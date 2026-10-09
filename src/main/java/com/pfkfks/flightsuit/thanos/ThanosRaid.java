package com.pfkfks.flightsuit.thanos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Thanos coming for the stones (DESIGN.md 4-16 최종전). For now: the warning when a third stone is won. */
public final class ThanosRaid {
    public static final int STONES_TO_RAID = 3;

    private ThanosRaid() {
    }

    static void onStoneWon(ServerPlayer player, int count) {
        if (count == STONES_TO_RAID) {
            player.sendSystemMessage(Component.translatable("thanos.flightsuit.he_senses").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    static void onBeaten(ServerLevel level, ThanosForceEntity entity) {
    }
}
