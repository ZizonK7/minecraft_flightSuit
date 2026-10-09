package com.pfkfks.flightsuit.entity;

import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.suit.RemoteLink;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The pilot's own body, left standing where they opened a remote link (DESIGN.md 4-7: "내 몸은 그 자리에
 * 무방비로 남는다"). Looks like them - their skin and whatever they had on, EDITH glasses included - and
 * monsters nearby go for it. It never takes damage itself: the first hit ends the link and lands on the player.
 *
 * Not saved with the world (the link is rebuilt from the player's own data after a crash), and it removes
 * itself if it ever outlives its link.
 */
public class RemoteBodyEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(RemoteBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final double LURE_RANGE = 16.0D;

    public RemoteBodyEntity(EntityType<? extends RemoteBodyEntity> type, Level level) {
        super(type, level);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            // The gear on it is only a copy for looks - never drop it.
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    public static RemoteBodyEntity spawn(ServerPlayer owner) {
        RemoteBodyEntity body = new RemoteBodyEntity(ModEntities.REMOTE_BODY.get(), owner.level());
        body.entityData.set(OWNER, Optional.of(owner.getUUID()));
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            body.setItemSlot(slot, owner.getItemBySlot(slot).copy());
        }
        body.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
        body.setYHeadRot(owner.getYRot());
        body.setYBodyRot(owner.getYRot());
        owner.level().addFreshEntity(body);
        return body;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
    }

    public UUID getOwnerId() {
        return entityData.get(OWNER).orElse(null);
    }

    public Player getOwner() {
        UUID id = getOwnerId();
        if (id == null) {
            return null;
        }
        // The owner may be piloting a suit in another dimension.
        return level().getServer() != null ? level().getServer().getPlayerList().getPlayer(id) : level().getPlayerByUUID(id);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (tickCount % 20 == 0 && !RemoteLink.isBodyOf(this, getOwnerId())) {
            discard();
            return;
        }
        if (tickCount % 10 == 0) {
            lureMonsters();
        }
    }

    /** Defenceless and in plain sight: idle monsters nearby notice it. */
    private void lureMonsters() {
        for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(LURE_RANGE),
                mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() == null && mob.hasLineOfSight(this))) {
            mob.setTarget(this);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // The owner's own suits standing guard never count as an attack on it.
        if (level().isClientSide || isInvulnerableTo(source) || isRemoved() || source.getEntity() instanceof SuitCompanionEntity) {
            return false;
        }
        RemoteLink.onBodyHit(this, source, amount);
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }
}
