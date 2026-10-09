package com.pfkfks.flightsuit.thanos;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Infinity Gauntlet (DESIGN.md 4-16). Right-click: set the stones you carry into it. With all six, right-click
 * snaps: Thanos and his forces within 96 blocks turn to dust, monsters within 48 fall - and it costs the wearer
 * dearly (down to one heart, starving, weak), and the stones burn out of the gauntlet.
 */
public class InfinityGauntletItem extends Item {
    public static final String STONES = "Stones";

    public InfinityGauntletItem(Properties properties) {
        super(properties);
    }

    public static int stones(ItemStack stack) {
        return stack.getTag() == null ? 0 : stack.getTag().getInt(STONES) & InfinityStone.ALL;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        int mask = stones(stack);
        if (mask != InfinityStone.ALL) {
            int added = 0;
            for (InfinityStone stone : InfinityStone.values()) {
                if ((mask & stone.bit()) != 0) {
                    continue;
                }
                for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
                    ItemStack held = serverPlayer.getInventory().getItem(i);
                    if (held.is(stone.item())) {
                        held.shrink(1);
                        mask |= stone.bit();
                        added++;
                        break;
                    }
                }
            }
            stack.getOrCreateTag().putInt(STONES, mask);
            if (added > 0) {
                server.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            serverPlayer.displayClientMessage(Component.translatable(added > 0 ? "thanos.flightsuit.gauntlet_set" : "thanos.flightsuit.gauntlet_none",
                    Integer.bitCount(mask)), true);
            return InteractionResultHolder.success(stack);
        }
        snap(serverPlayer, server);
        stack.getOrCreateTag().putInt(STONES, 0);
        player.getCooldowns().addCooldown(this, 200);
        return InteractionResultHolder.success(stack);
    }

    private static void snap(ServerPlayer player, ServerLevel level) {
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 1.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0D, player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 80, 1.5D, 1.5D, 1.5D, 0.3D);
        for (ThanosForceEntity force : level.getEntitiesOfClass(ThanosForceEntity.class, player.getBoundingBox().inflate(96.0D),
                force -> force.getForce().role() != ThanosForce.Role.NPC)) {
            force.dust(level);
        }
        for (Monster monster : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(48.0D),
                monster -> !(monster instanceof ThanosForceEntity))) {
            level.sendParticles(ParticleTypes.ASH, monster.getX(), monster.getY() + 1.0D, monster.getZ(), 20, 0.3D, 0.6D, 0.3D, 0.02D);
            monster.kill();
        }
        // The price.
        player.setHealth(2.0F);
        player.getFoodData().setFoodLevel(0);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 120, 1));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 30, 1));
        player.sendSystemMessage(Component.translatable("thanos.flightsuit.snap_self").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stones(stack) == InfinityStone.ALL;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.infinity_gauntlet", Integer.bitCount(stones(stack))).withStyle(ChatFormatting.GRAY));
        tooltip.add(InfinityStone.list(stones(stack)));
    }
}
