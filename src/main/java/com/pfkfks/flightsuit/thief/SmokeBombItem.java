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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Smoke bomb (DESIGN.md 4-14, Robin's drop): a thick cloud where you stand - mobs within 8 blocks lose sight of
 * you and are blinded, and you turn invisible for 5 seconds. Used up.
 */
public class SmokeBombItem extends Item {
    public SmokeBombItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(this, 40);
        if (level instanceof ServerLevel server) {
            cloud(server, player.getX(), player.getY(), player.getZ());
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0D))) {
                if (mob.getTarget() == player) {
                    mob.setTarget(null);
                }
                mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            }
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** The cloud itself (the thieves use it too). */
    public static void cloud(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.8D, z, 40, 1.6D, 0.8D, 1.6D, 0.01D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0D, z, 60, 1.4D, 1.0D, 1.4D, 0.02D);
        level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.smoke_bomb").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
