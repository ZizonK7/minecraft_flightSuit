package com.pfkfks.flightsuit.entity.ai;

import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

/**
 * Bodyguard targeting, in priority order:
 * 1. an explicit order (H key);
 * 2. whatever just hurt the owner;
 * 3. whatever the owner just attacked;
 * 4. a hostile mob closing in on the owner (or on their body, while they're remote-piloting).
 * Never targets the owner, other players, or other suits, nor anything out of reach.
 */
public class CompanionTargetGoal extends TargetGoal {
    private static final double GUARD_RADIUS = 12.0D;
    /** Fights the owner picks far away (e.g. while remote-piloting) are not this suit's to chase. */
    private static final double REACH = 48.0D;

    private final SuitCompanionEntity suit;
    private LivingEntity candidate;
    private int lastHurtByTimestamp;
    private int lastHurtTimestamp;

    public CompanionTargetGoal(SuitCompanionEntity suit) {
        super(suit, false);
        this.suit = suit;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (suit.isBusy()) {
            return false;
        }
        candidate = pick();
        return candidate != null && candidate != suit.getTarget();
    }

    private LivingEntity pick() {
        LivingEntity ordered = suit.getCommandTarget();
        if (ordered != null) {
            return ordered;
        }
        Player owner = suit.getOwner();
        if (owner == null) {
            return null;
        }
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && owner.getLastHurtByMobTimestamp() != lastHurtByTimestamp && valid(attacker, owner)) {
            lastHurtByTimestamp = owner.getLastHurtByMobTimestamp();
            return attacker;
        }
        LivingEntity victim = owner.getLastHurtMob();
        if (victim != null && owner.getLastHurtMobTimestamp() != lastHurtTimestamp && valid(victim, owner)) {
            lastHurtTimestamp = owner.getLastHurtMobTimestamp();
            return victim;
        }
        if (suit.getTarget() != null || suit.tickCount % 10 != 0) {
            return null;
        }
        LivingEntity ward = suit.getAnchor();
        List<Mob> threats = suit.level().getEntitiesOfClass(Mob.class, ward.getBoundingBox().inflate(GUARD_RADIUS),
                mob -> mob instanceof Enemy && mob.isAlive() && valid(mob, owner)
                        && (mob.getTarget() == ward || mob.distanceToSqr(ward) < 8.0D * 8.0D));
        Mob closest = null;
        for (Mob mob : threats) {
            if (closest == null || mob.distanceToSqr(ward) < closest.distanceToSqr(ward)) {
                closest = mob;
            }
        }
        return closest;
    }

    private boolean valid(LivingEntity entity, Player owner) {
        return entity.isAlive() && entity != owner && entity != suit && entity.distanceToSqr(suit) < REACH * REACH
                && !(entity instanceof Player) && !(entity instanceof SuitCompanionEntity) && !(entity instanceof RemoteBodyEntity);
    }

    @Override
    public void start() {
        suit.setTarget(candidate);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = suit.getTarget();
        return target != null && target.isAlive() && !suit.isBusy();
    }
}
