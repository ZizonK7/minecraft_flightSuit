package com.pfkfks.flightsuit.thief;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Batarang (DESIGN.md 4-14, Batman's drop): right-click throws it at whatever you look at up to 24 blocks
 * away - 6 damage and a short stun - and it comes straight back to your hand. 1 second between throws.
 */
public class BatarangItem extends Item {
    private static final double RANGE = 24.0D;

    public BatarangItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(this, 20);
        player.swing(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0D),
                entity -> entity instanceof LivingEntity && entity.isPickable() && !entity.isSpectator() && entity != player);
        Vec3 stop = hit != null ? hit.getLocation() : end;
        int points = Math.max(4, (int) (stop.distanceTo(eye) * 2.0D));
        for (int i = 1; i <= points; i++) {
            Vec3 at = eye.add(stop.subtract(eye).scale(i / (double) points));
            server.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.8F, 1.6F);
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            target.hurt(player.damageSources().playerAttack(player), 6.0F);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6D), target.getZ(), 8, 0.2D, 0.3D, 0.2D, 0.1D);
            stack.hurtAndBreak(1, player, owner -> owner.broadcastBreakEvent(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.batarang").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
