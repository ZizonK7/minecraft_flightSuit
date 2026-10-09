package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.network.EdithAlertS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * EDITH speaking up (DESIGN.md 4-15): something is happening to the village while you're away - a raid, the
 * alarm, a surrender. Wearing or carrying the glasses (or a suit helmet) it flashes across the HUD with an
 * alarm tone and where it is; the chat line comes either way, so nothing is missed.
 */
public final class EdithAlert {
    public static final int RED = 0xFF4D3A;
    public static final int AMBER = 0xFFB347;
    public static final int CYAN = 0x5FE3FF;

    private EdithAlert() {
    }

    public static void send(@Nullable ServerPlayer player, Component title, Component detail, @Nullable ResourceKey<Level> dimension,
                            @Nullable BlockPos pos, int color, boolean chat) {
        if (player == null) {
            return;
        }
        if (chat) {
            player.sendSystemMessage(Component.translatable("edith.flightsuit.chat", title, detail));
        }
        if (canHear(player)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EdithAlertS2CPacket(title, detail,
                    dimension == null ? "" : dimension.location().toString(), pos == null ? BlockPos.ZERO : pos, pos != null, color));
        }
    }

    /** EDITH is with you: the glasses, worn or carried, or a suit helmet. */
    public static boolean canHear(ServerPlayer player) {
        return EdithGlassesItem.has(player) || player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SuitArmorItem;
    }

    /** Far from the spot (or in another dimension): the HUD alert is worth it. */
    public static boolean isAway(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos, double range) {
        return player.level().dimension() != dimension || player.blockPosition().distSqr(pos) > range * range;
    }
}
