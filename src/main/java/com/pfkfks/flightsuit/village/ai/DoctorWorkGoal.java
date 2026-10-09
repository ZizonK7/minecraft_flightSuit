package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.VillageTuning;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The doctor (DESIGN.md 4-12 의사): runs to anyone downed in the village - even through an alarm - and gets
 * them back up after a short treatment (faster with more talent), so a downed resident doesn't have to wait
 * for the player's first aid. Otherwise patches up the walking wounded nearby now and then.
 */
public class DoctorWorkGoal extends Goal {
    private final ResidentEntity doctor;
    private ResidentEntity patient;
    private int treating;
    private int ticks;
    private long nextHeal;

    public DoctorWorkGoal(ResidentEntity doctor) {
        this.doctor = doctor;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private ResidentEntity findPatient() {
        VillageHallBlockEntity hall = doctor.hall();
        if (hall == null) {
            return null;
        }
        ResidentEntity best = null;
        boolean bestDowned = false;
        boolean canHeal = doctor.level().getGameTime() >= nextHeal;
        for (ResidentEntity person : hall.people()) {
            if (person == doctor || !person.isAlive()) {
                continue;
            }
            boolean downed = person.isDowned();
            boolean hurt = !downed && canHeal && person.getHealth() < person.getMaxHealth() - 4.0F && person.distanceToSqr(doctor) < 24.0D * 24.0D;
            if (!downed && !hurt) {
                continue;
            }
            if (best == null || downed && !bestDowned || downed == bestDowned && person.distanceToSqr(doctor) < best.distanceToSqr(doctor)) {
                best = person;
                bestDowned = downed;
            }
        }
        return best;
    }

    @Override
    public boolean canUse() {
        if (doctor.getJob() != ResidentJob.DOCTOR || doctor.isWanderer() || doctor.isDowned() || doctor.tickCount % 20 != 0) {
            return false;
        }
        patient = findPatient();
        return patient != null;
    }

    @Override
    public boolean canContinueToUse() {
        return patient != null && patient.isAlive() && !doctor.isDowned() && ticks < 600
                && (patient.isDowned() || patient.getHealth() < patient.getMaxHealth());
    }

    @Override
    public void start() {
        treating = 0;
        ticks = 0;
    }

    @Override
    public void tick() {
        ticks++;
        doctor.getLookControl().setLookAt(patient);
        if (doctor.distanceToSqr(patient) > 4.0D) {
            treating = 0;
            if (ticks % 10 == 1) {
                doctor.getNavigation().moveTo(patient, 1.0D);
            }
            return;
        }
        doctor.getNavigation().stop();
        treating++;
        if (treating % 10 == 0) {
            doctor.swing(InteractionHand.MAIN_HAND);
        }
        int needed = Math.round(VillageTuning.TREAT_TICKS / VillageTuning.talentSpeed(doctor.talent(ResidentJob.DOCTOR)));
        if (treating < needed) {
            return;
        }
        if (patient.isDowned()) {
            patient.revive();
            VillageHallBlockEntity hall = doctor.hall();
            if (hall != null) {
                hall.announceTreated(doctor, patient);
            }
        } else {
            patient.heal(VillageTuning.HEAL_AMOUNT);
            patient.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
            if (doctor.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + 1.2D, patient.getZ(), 3, 0.3D, 0.3D, 0.3D, 0.0D);
            }
            nextHeal = doctor.level().getGameTime() + VillageTuning.HEAL_COOLDOWN;
        }
        patient = null;
    }

    @Override
    public void stop() {
        doctor.getNavigation().stop();
        patient = null;
    }
}
