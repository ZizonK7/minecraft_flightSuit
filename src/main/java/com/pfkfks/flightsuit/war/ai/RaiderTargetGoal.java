package com.pfkfks.flightsuit.war.ai;

import com.pfkfks.flightsuit.war.RaidMember;
import com.pfkfks.flightsuit.war.WarTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Picks the closest enemy in sight for a Three Kingdoms soldier or general, by the war's rules (WarTargets):
 * a raider goes for the village's defenders, a garrison for intruders, the player's side for their foes.
 */
public class RaiderTargetGoal extends TargetGoal {
    private static final double RANGE = 20.0D;

    private final Mob raider;
    private final RaidMember member;
    private final BooleanSupplier off;
    private LivingEntity candidate;

    public RaiderTargetGoal(Mob raider, RaidMember member, BooleanSupplier off) {
        super(raider, true);
        this.raider = raider;
        this.member = member;
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
        List<LivingEntity> around = raider.level().getEntitiesOfClass(LivingEntity.class, raider.getBoundingBox().inflate(RANGE),
                entity -> WarTargets.isEnemy(member, raider, entity));
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

    @Override
    public void start() {
        raider.setTarget(candidate);
        super.start();
    }
}
