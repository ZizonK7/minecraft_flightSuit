package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Crossing space (planet/SpaceTravel): the pilot's screen goes to stars for a moment, with where they're headed. */
public class SpaceTravelS2CPacket {
    public final Component destination;
    public final int ticks;

    public SpaceTravelS2CPacket(Component destination, int ticks) {
        this.destination = destination;
        this.ticks = ticks;
    }

    public static void encode(SpaceTravelS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeComponent(packet.destination);
        buf.writeVarInt(packet.ticks);
    }

    public static SpaceTravelS2CPacket decode(FriendlyByteBuf buf) {
        return new SpaceTravelS2CPacket(buf.readComponent(), buf.readVarInt());
    }

    public static void handle(SpaceTravelS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.SpaceTravelOverlay.show(packet.destination, packet.ticks)));
        ctx.setPacketHandled(true);
    }
}
