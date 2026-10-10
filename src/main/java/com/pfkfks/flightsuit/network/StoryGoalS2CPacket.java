package com.pfkfks.flightsuit.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The story marker on the HUD (planet/PlanetStory): where to go next on this planet and what's there, or nothing. */
public class StoryGoalS2CPacket {
    public final boolean active;
    public final String dimension;
    public final BlockPos pos;
    public final Component label;

    public StoryGoalS2CPacket(boolean active, String dimension, BlockPos pos, Component label) {
        this.active = active;
        this.dimension = dimension;
        this.pos = pos;
        this.label = label;
    }

    public static StoryGoalS2CPacket none() {
        return new StoryGoalS2CPacket(false, "", BlockPos.ZERO, Component.empty());
    }

    public static void encode(StoryGoalS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeUtf(packet.dimension);
        buf.writeBlockPos(packet.pos);
        buf.writeComponent(packet.label);
    }

    public static StoryGoalS2CPacket decode(FriendlyByteBuf buf) {
        return new StoryGoalS2CPacket(buf.readBoolean(), buf.readUtf(), buf.readBlockPos(), buf.readComponent());
    }

    public static void handle(StoryGoalS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.StoryGoalOverlay.set(packet)));
        ctx.setPacketHandled(true);
    }
}
