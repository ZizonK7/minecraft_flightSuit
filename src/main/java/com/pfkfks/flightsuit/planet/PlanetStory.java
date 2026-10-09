package com.pfkfks.flightsuit.planet;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Each planet's main story (DESIGN.md 4-16). For now: the welcome on landing. */
public final class PlanetStory {
    private PlanetStory() {
    }

    public static void onLanded(ServerPlayer player, Planet planet) {
        player.sendSystemMessage(Component.translatable("story.flightsuit." + planet.id() + ".welcome").withStyle(ChatFormatting.YELLOW));
    }
}
