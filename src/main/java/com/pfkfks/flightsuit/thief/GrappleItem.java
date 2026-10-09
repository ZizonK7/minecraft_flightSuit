package com.pfkfks.flightsuit.thief;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Grapple gun (DESIGN.md 4-14, Catwoman's drop): right-click fires a line at the block you look at, up to 32
 * blocks, and yanks you towards it (falling damage forgiven). 1.5 seconds between shots.
 */
public class GrappleItem extends Item {
    private static final double RANGE = 32.0D;

    public GrappleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, 30);
        Vec3 pull = hit.getLocation().subtract(player.position());
        double distance = pull.length();
        Vec3 motion = pull.normalize().scale(Math.min(2.4D, 0.6D + distance * 0.12D)).add(0.0D, 0.35D, 0.0D);
        player.setDeltaMovement(motion);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        if (level instanceof ServerLevel server) {
            int points = Math.max(4, (int) distance);
            for (int i = 1; i <= points; i++) {
                Vec3 at = eye.add(hit.getLocation().subtract(eye).scale(i / (double) points));
                server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.9F, 1.3F);
            stack.hurtAndBreak(1, player, owner -> owner.broadcastBreakEvent(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.grapple").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
