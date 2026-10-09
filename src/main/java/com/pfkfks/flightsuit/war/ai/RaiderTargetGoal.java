package com.pfkfks.flightsuit.war.ai;

import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.war.GeneralEntity;
import com.pfkfks.flightsuit.war.RaidManager;
import com.pfkfks.flightsuit.war.RaidMember;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Who a raider goes for: the closest thing defending the village that it can see - a resident still on their
 * feet, a player, a suit, a recruited general, an iron golem. A player Guan Yu has called out to a duel is
 * left to him.
 */
public class RaiderTargetGoal extends TargetGoal {
    private static final double RANGE = 20.0D;

    private final Mob raider;
    private final BooleanSupplier off;
    private LivingEntity candidate;

    public RaiderTargetGoal(Mob raider, BooleanSupplier off) {
        super(raider, true);
        this.raider = raider;
        this.off = off;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // Random like NearestAttackableTargetGoal: canUse only runs every other tick, so a fixed tickCount % 10 would starve half the mobs.
        if (off.getAsBoolean() || raider.getTarget() != null || raider.getRandom().nextInt(reducedTickDelay(10)) != 0) {
            return false;
        }
        candidate = pick();
        return candidate != null;
    }

    private LivingEntity pick() {
        int raidId = raider instanceof RaidMember member ? member.raidId() : -1;
        List<LivingEntity> around = raider.level().getEntitiesOfClass(LivingEntity.class, raider.getBoundingBox().inflate(RANGE),
                entity -> entity.isAlive() && entity != raider && isDefender(entity, raidId));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : around) {
            double dist = entity.distanceToSqr(raider);
            if (dist < bestDist && raider.getSensing().hasLineOfSight(entity)) {
                best = entity;
                bestDist = dist;
            }
        }
        return best;
    }

    private boolean isDefender(LivingEntity entity, int raidId) {
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator() && !RaidManager.isDueling(raidId, player, raider);
        }
        if (entity instanceof ResidentEntity resident) {
            return !resident.isDowned();
        }
        if (entity instanceof GeneralEntity general) {
            return general.isRecruited() && general.canFight();
        }
        return entity instanceof SuitCompanionEntity || entity instanceof IronGolem || entity instanceof RemoteBodyEntity;
    }

    @Override
    public void start() {
        raider.setTarget(candidate);
        super.start();
    }
}
