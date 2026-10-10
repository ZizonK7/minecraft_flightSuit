package com.pfkfks.flightsuit.entity.ai;

import com.pfkfks.flightsuit.entity.CardEntity;
import com.pfkfks.flightsuit.entity.MissileEntity;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.suit.RepulsorHandler;
import com.pfkfks.flightsuit.suit.Stasis;
import com.pfkfks.flightsuit.suit.SuitClass;
import com.pfkfks.flightsuit.suit.SuitSkills;
import com.pfkfks.flightsuit.suit.SuitTuning;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Iron Man style engagement: hold a firing position a few blocks off the target (above it, so the shot
 * isn't blocked by the ground), fire the class's primary on a cooldown, punch when it closes in - and, since
 * the M13 test, use the suit's skills like its wearer would:
 * - Mark 1 / Mark 50: repulsor; a missile salvo, the unibeam;
 * - Mark 2: cryo beam (three hits on the same target freeze it); cryo nova when they crowd it;
 * - Mark 3 (on foot): throws cards from range and shadow-steps around the target, a fan of cards now and then;
 * - Mark 4 (on foot): the sword and clawshot; the spin attack when surrounded;
 * - M17 Mark 44: lands and walks up to punch (the flurry); slams the ground when three or more crowd it;
 * - M17 Mark 5: flies in cutting (the three-step combo), Burning Attack at a target that's far, Super Saiyan below
 *   half health;
 * - M17 Mark 3: the Tempest when five or more foes stand within its radius.
 */
public class CompanionCombatGoal extends Goal {
    private static final double STANDOFF = 7.0D;
    private static final double FIRE_RANGE = 24.0D;
    private static final int FIRE_COOLDOWN = 20;
    private static final int MELEE_COOLDOWN = 15;
    private static final float COMPANION_REPULSOR_DAMAGE = 6.0F;
    private static final float COMPANION_CRYO_DAMAGE = 4.0F;
    /** Mark 4: beyond this it clawshots onto the target; slashes this often up close. */
    private static final double CLAW_DISTANCE = 9.0D;
    private static final int SWORD_COOLDOWN = 12;
    /** Skills: how often (ticks) once it's in a fight. */
    private static final int MISSILE_EVERY = 200;
    private static final int UNIBEAM_EVERY = 260;
    private static final int NOVA_EVERY = 240;
    private static final int SPIN_EVERY = 140;
    private static final int CARD_EVERY = 12;
    private static final int FAN_EVERY = 200;
    private static final float COMPANION_CARD_DAMAGE = 4.0F;
    /** Mark 44: punches this often; the ground slam at most this often. */
    private static final int PUNCH_EVERY = 10;
    private static final int SLAM_EVERY = 200;
    /** Mark 5: a cut this often; Burning Attack at most this often, at targets farther than BURNING_DISTANCE. */
    private static final int CUT_EVERY = 8;
    private static final int BURNING_EVERY = 180;
    private static final double BURNING_DISTANCE = 8.0D;
    private static final int TEMPEST_EVERY = 1_200;

    private final SuitCompanionEntity suit;
    private int fireCooldown;
    private int meleeCooldown;
    private int skillCooldown;
    private int skill2Cooldown;
    private int blinkIn;
    /** Mark 2: hits on one target in a row (the third freezes it). */
    private LivingEntity chilled;
    private int chillHits;
    /** Mark 44 / Mark 5: which punch or cut comes next. */
    private int combo;
    /** Mark 3: the Tempest's own (long) cooldown. */
    private int tempestCooldown = 200;

    public CompanionCombatGoal(SuitCompanionEntity suit) {
        this.suit = suit;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = suit.getTarget();
        return target != null && target.isAlive() && !suit.isBusy();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        fireCooldown = 5;
        // The first skill comes a little into the fight, not on the first tick.
        skillCooldown = 60;
        skill2Cooldown = 100;
        blinkIn = 40;
    }

    @Override
    public void tick() {
        LivingEntity target = suit.getTarget();
        if (target == null) {
            return;
        }
        suit.getLookControl().setLookAt(target, 60.0F, 60.0F);
        double distance = suit.distanceTo(target);
        if (skillCooldown > 0) {
            skillCooldown--;
        }
        if (skill2Cooldown > 0) {
            skill2Cooldown--;
        }
        SuitClass suitClass = suit.suitClass();
        if (suitClass == SuitClass.PHANTOM) {
            phantomFight(target, distance);
            return;
        }
        if (suitClass == SuitClass.HULKBUSTER) {
            hulkbusterFight(target, distance);
            return;
        }
        if (suitClass == SuitClass.SWORDSMAN) {
            swordsmanFight(target, distance);
            return;
        }
        if (suit.isGrounded()) {
            swordFight(target, distance);
            return;
        }

        // Firing position: on the line from the target to the suit, STANDOFF away and a bit above.
        Vec3 away = suit.position().subtract(target.position());
        if (away.horizontalDistanceSqr() < 0.01D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 spot = target.position().add(new Vec3(away.x, 0.0D, away.z).normalize().scale(STANDOFF)).add(0.0D, 2.5D, 0.0D);
        if (distance > 2.0D) {
            suit.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, distance > 16.0D ? 3.0D : 1.6D);
        }

        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        if (distance < 2.4D && meleeCooldown <= 0) {
            suit.swing(InteractionHand.MAIN_HAND);
            suit.doHurtTarget(target);
            meleeCooldown = MELEE_COOLDOWN;
            return;
        }
        boolean sees = suit.getSensing().hasLineOfSight(target);
        if (suitClass == SuitClass.STEALTH ? stealthSkills(target, distance)
                : suitClass == SuitClass.STANDARD && standardSkills(target, distance, sees)) {
            return;
        }
        if (fireCooldown <= 0 && distance < FIRE_RANGE && sees && suit.drain(SuitTuning.REPULSOR_COST)) {
            if (suitClass == SuitClass.STEALTH) {
                cryoShot(target);
            } else {
                Vec3 palm = suit.palmPosition();
                Vec3 aim = target.getBoundingBox().getCenter().subtract(palm).normalize();
                RepulsorHandler.blast((ServerLevel) suit.level(), suit, palm, aim, FIRE_RANGE, COMPANION_REPULSOR_DAMAGE,
                        entity -> entity != suit && !(entity instanceof SuitCompanionEntity) && !(entity instanceof RemoteBodyEntity)
                                && entity != suit.getOwner());
            }
            suit.markAiming();
            fireCooldown = FIRE_COOLDOWN;
        }
    }

    private ServerLevel level() {
        return (ServerLevel) suit.level();
    }

    private List<LivingEntity> foesWithin(double radius) {
        return suit.level().getEntitiesOfClass(LivingEntity.class, suit.getBoundingBox().inflate(radius),
                entity -> suit.isFoe(entity) && entity.distanceTo(suit) <= radius);
    }

    // ---------------------------------------------------------------- Mark 1 / Mark 50

    /** Missiles at a target that's a way off; the unibeam when it has a clear line. True if it used one. */
    private boolean standardSkills(LivingEntity target, double distance, boolean sees) {
        if (skillCooldown <= 0 && distance > 6.0D && sees && suit.getOwner() instanceof ServerPlayer owner
                && suit.drain(SuitTuning.MISSILE_COST)) {
            // Fired in the owner's name: their blasts spare the owner's side (SuitWeapons.isFriendly).
            Vec3 pod = suit.position().add(0.0D, suit.getBbHeight() * 0.85D, 0.0D);
            for (int i = 0; i < 4; i++) {
                Vec3 launch = new Vec3((suit.getRandom().nextDouble() - 0.5D) * 0.4D, 0.5D, (suit.getRandom().nextDouble() - 0.5D) * 0.4D);
                MissileEntity.launch(level(), owner, pod, launch, target, target.getBoundingBox().getCenter());
            }
            level().playSound(null, suit.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 0.7F, 1.6F);
            skillCooldown = MISSILE_EVERY;
            return true;
        }
        if (skill2Cooldown <= 0 && distance < SuitTuning.UNIBEAM_RANGE - 4.0D && sees && suit.drain(SuitTuning.UNIBEAM_COST)) {
            Vec3 chest = SuitSkills.chest(suit);
            SuitSkills.unibeam(level(), suit, chest, target.getBoundingBox().getCenter().subtract(chest),
                    SuitTuning.UNIBEAM_DAMAGE * 0.75F, suit::isFoe);
            suit.markAiming();
            skill2Cooldown = UNIBEAM_EVERY;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- Mark 2

    /** Cryo nova when the target is close or two or more crowd it. */
    private boolean stealthSkills(LivingEntity target, double distance) {
        if (skill2Cooldown <= 0 && (distance < 5.0D || foesWithin(SuitTuning.CRYO_NOVA_RADIUS).size() >= 2)
                && suit.drain(SuitTuning.CRYO_NOVA_COST)) {
            SuitSkills.cryoNova(level(), suit, suit::isFoe);
            skill2Cooldown = NOVA_EVERY;
            return true;
        }
        return false;
    }

    /** The cryo beam's shot: a cold hit that slows; the third in a row on one target freezes it. */
    private void cryoShot(LivingEntity target) {
        Vec3 palm = suit.palmPosition();
        Vec3 to = target.getBoundingBox().getCenter();
        Vec3 step = to.subtract(palm);
        int points = Math.max(4, (int) (step.length() * 2.0D));
        for (int i = 0; i <= points; i++) {
            Vec3 at = palm.add(step.scale(i / (double) points));
            level().sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        target.invulnerableTime = 0;
        target.hurt(suit.damageSources().mobAttack(suit), COMPANION_CRYO_DAMAGE);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + 20));
        chillHits = target == chilled ? chillHits + 1 : 1;
        chilled = target;
        if (chillHits >= 3 && Stasis.hold(level(), target, SuitTuning.FREEZE_HOLD_TICKS, true)) {
            chillHits = 0;
        }
        level().playSound(null, suit.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.NEUTRAL, 0.8F, 1.6F);
    }

    // ---------------------------------------------------------------- Mark 3

    /**
     * On foot like Reaper: keeps some distance and throws cards; every couple of seconds it shadow-steps to
     * another side of the target (behind it, often); now and then a fan of cards at everything around.
     */
    private void phantomFight(LivingEntity target, double distance) {
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        if (tempestCooldown > 0) {
            tempestCooldown--;
        }
        if (tempestCooldown <= 0 && !com.pfkfks.flightsuit.suit.TempestRain.isRaining(suit)) {
            List<LivingEntity> crowd = level().getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(SuitTuning.TEMPEST_RADIUS, 4.0D, SuitTuning.TEMPEST_RADIUS), suit::isFoe);
            if (crowd.size() >= 5 && suit.drain(SuitTuning.TEMPEST_COST)) {
                com.pfkfks.flightsuit.suit.PhantomArts.companionTempest(level(), suit, target.position(), suit::isFoe);
                suit.swing(InteractionHand.MAIN_HAND);
                tempestCooldown = TEMPEST_EVERY;
                return;
            }
        }
        if (blinkIn > 0) {
            blinkIn--;
        }
        boolean sees = suit.getSensing().hasLineOfSight(target);
        if (blinkIn <= 0 || distance > 20.0D) {
            double angle = suit.getRandom().nextDouble() * Math.PI * 2.0D;
            double reach = 3.0D + suit.getRandom().nextDouble() * 4.0D;
            Vec3 near = target.position().add(Math.cos(angle) * reach, 0.0D, Math.sin(angle) * reach);
            if (suit.blinkTo(near)) {
                blinkIn = 50 + suit.getRandom().nextInt(30);
                return;
            }
        }
        if (distance > 10.0D || !sees) {
            if (suit.tickCount % 5 == 0) {
                suit.getNavigation().moveTo(target, 1.4D);
            }
        } else {
            suit.getNavigation().stop();
        }
        if (!(suit.getOwner() instanceof ServerPlayer owner)) {
            return;
        }
        if (skillCooldown <= 0 && sees && suit.drain(SuitTuning.CARD_COST * 8)) {
            // A fan: one card at each foe around, the rest at the target.
            List<LivingEntity> foes = foesWithin(12.0D);
            for (int i = 0; i < 8; i++) {
                LivingEntity at = foes.isEmpty() ? target : foes.get(i % foes.size());
                throwCard(owner, at, i % 2 == 0 ? CardEntity.NOIR : CardEntity.BLANCHE);
            }
            skillCooldown = FAN_EVERY;
            return;
        }
        if (fireCooldown <= 0 && sees && distance < 24.0D && suit.drain(SuitTuning.CARD_COST)) {
            throwCard(owner, target, CardEntity.BLANCHE);
            fireCooldown = CARD_EVERY;
        }
    }

    private void throwCard(ServerPlayer owner, LivingEntity target, byte style) {
        Vec3 hand = suit.palmPosition();
        Vec3 velocity = target.getBoundingBox().getCenter().subtract(hand).normalize().scale(1.7D);
        // Thrown in the owner's name, like the missiles (their card hits fill the owner's gauge too).
        CardEntity.throwCard(level(), owner, hand, velocity, target, COMPANION_CARD_DAMAGE, style);
        suit.swing(InteractionHand.MAIN_HAND);
        suit.markAiming();
        level().playSound(null, suit.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.7F, 1.6F);
    }

    // ---------------------------------------------------------------- Mark 4

    /**
     * Mark 4 fights on foot with the sword: clawshot onto a target that's far off and in sight (arriving with
     * a slash), otherwise run it down and cut - and spin when two or more are around it.
     */
    private void swordFight(LivingEntity target, double distance) {
        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        if (skillCooldown <= 0 && foesWithin(4.5D).size() >= 2 && suit.drain(SuitTuning.MISSILE_COST)) {
            suit.swing(InteractionHand.MAIN_HAND);
            SuitSkills.spin(level(), suit, 4.5D, 10.0F, suit::isFoe);
            skillCooldown = SPIN_EVERY;
            return;
        }
        if (distance > CLAW_DISTANCE && suit.getSensing().hasLineOfSight(target)
                && suit.clawTo(target.getBoundingBox().getCenter(), target)) {
            return;
        }
        if (distance > 2.6D) {
            if (suit.tickCount % 5 == 0) {
                suit.getNavigation().moveTo(target, 1.5D);
            }
            return;
        }
        suit.getNavigation().stop();
        if (meleeCooldown <= 0) {
            suit.swing(InteractionHand.MAIN_HAND);
            if (suit.doHurtTarget(target)) {
                Vec3 at = target.getBoundingBox().getCenter();
                level().sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            suit.level().playSound(null, suit.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.8F, 1.1F);
            meleeCooldown = SWORD_COOLDOWN;
        }
    }

    // ---------------------------------------------------------------- Mark 44

    /** Lands (SuitCompanionEntity.isGrounded), walks up and punches; slams the ground when three or more crowd it. */
    private void hulkbusterFight(LivingEntity target, double distance) {
        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        if (skillCooldown <= 0 && suit.onGround() && foesWithin(SuitTuning.SLAM_RADIUS).size() >= 3 && suit.drain(SuitTuning.SLAM_COST)) {
            suit.swing(InteractionHand.MAIN_HAND);
            com.pfkfks.flightsuit.suit.HulkbusterArts.slam(level(), suit, SuitTuning.SLAM_RADIUS, SuitTuning.SLAM_DAMAGE * 0.8F, suit::isFoe);
            skillCooldown = SLAM_EVERY;
            return;
        }
        double reach = suit.getBbWidth() / 2.0D + target.getBbWidth() / 2.0D + 1.8D;
        if (distance > reach) {
            if (suit.tickCount % 5 == 0) {
                suit.getNavigation().moveTo(target, 1.3D);
            }
            return;
        }
        suit.getNavigation().stop();
        if (meleeCooldown <= 0 && suit.drain(SuitTuning.PUNCH_COST)) {
            boolean left = combo++ % 2 == 1;
            suit.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            com.pfkfks.flightsuit.suit.HulkbusterArts.punchFan(level(), suit, SuitTuning.PUNCH_DAMAGE * 0.8F, suit::isFoe);
            level().playSound(null, suit.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.NEUTRAL, 0.8F, 0.9F);
            meleeCooldown = PUNCH_EVERY;
        }
    }

    // ---------------------------------------------------------------- Mark 5

    /**
     * Flies in to cutting distance and runs the three-step combo; a Burning Attack at a target that's far off;
     * Super Saiyan when it's below half health (the battery pays).
     */
    private void swordsmanFight(LivingEntity target, double distance) {
        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        boolean sees = suit.getSensing().hasLineOfSight(target);
        if (!suit.isUltimate() && suit.getHealth() < suit.getMaxHealth() * 0.5F && suit.drain(SuitTuning.SSJ_COST)) {
            suit.setUltimate(true);
        }
        float power = suit.isUltimate() ? SuitTuning.SSJ_MULTIPLIER : 1.0F;
        if (skillCooldown <= 0 && distance > BURNING_DISTANCE && sees && suit.drain(SuitTuning.BURNING_COST)) {
            Vec3 hands = com.pfkfks.flightsuit.suit.SwordArts.hands(suit);
            com.pfkfks.flightsuit.suit.KiShots.fire(level(), suit, com.pfkfks.flightsuit.suit.KiShots.Style.BURNING, hands,
                    target.getBoundingBox().getCenter().subtract(hands), SuitTuning.BURNING_SPEED, SuitTuning.BURNING_RANGE,
                    SuitTuning.BURNING_DAMAGE * 0.75F * power, SuitTuning.BURNING_RADIUS, suit::isFoe);
            suit.markAiming();
            skillCooldown = BURNING_EVERY;
            return;
        }
        // In close, a little above its middle, cutting.
        Vec3 away = suit.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        away = away.lengthSqr() < 0.01D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
        Vec3 spot = target.position().add(away.scale(target.getBbWidth() / 2.0D + 1.8D)).add(0.0D, target.getBbHeight() * 0.3D, 0.0D);
        suit.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, distance > 12.0D ? 2.6D : 1.6D);
        if (distance <= SuitTuning.SLASH_REACH + target.getBbWidth() / 2.0D && meleeCooldown <= 0 && suit.drain(SuitTuning.SLASH_COST)) {
            int step = combo++ % 3;
            suit.swing(InteractionHand.MAIN_HAND);
            Vec3 look = target.getBoundingBox().getCenter().subtract(suit.getEyePosition());
            com.pfkfks.flightsuit.suit.SwordArts.cut(level(), suit, look.normalize(), step, 0.8F * power, suit::isFoe);
            meleeCooldown = CUT_EVERY;
        }
    }
}
