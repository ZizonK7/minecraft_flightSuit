package com.pfkfks.flightsuit.village.ai;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.war.RaidMember;
import com.pfkfks.flightsuit.war.WarTargets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;

import java.util.EnumSet;
import java.util.List;

/**
 * A soldier marching with the player (M12 원정) fights what the player's side fights: raiders, the garrison of a
 * kingdom the player is at war with, and monsters close by.
 */
public class ArmyTargetGoal extends TargetGoal {
    private final ResidentEntity soldier;
    private LivingEntity candidate;

    public ArmyTargetGoal(ResidentEntity soldier) {
        super(soldier, true);
        this.soldier = soldier;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (soldier.getCommander() == null || !soldier.getJob().isFighter() || soldier.isDowned() || soldier.getTarget() != null
                || soldier.getRandom().nextInt(reducedTickDelay(10)) != 0) {
            return false;
        }
        List<LivingEntity> around = soldier.level().getEntitiesOfClass(LivingEntity.class, soldier.getBoundingBox().inflate(16.0D),
                this::isFoe);
        candidate = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity entity : around) {
            double dist = entity.distanceToSqr(soldier);
            if (dist < best && soldier.getSensing().hasLineOfSight(entity)) {
                candidate = entity;
                best = dist;
            }
        }
        return candidate != null;
    }

    private boolean isFoe(LivingEntity entity) {
        if (!entity.isAlive() || entity == soldier) {
            return false;
        }
        if (entity instanceof RaidMember member) {
            return WarTargets.isPlayerSideFoe(soldier, soldier.getCommander(), null, member);
        }
        return entity instanceof Enemy;
    }

    @Override
    public void start() {
        soldier.setTarget(candidate);
        super.start();
    }
}
