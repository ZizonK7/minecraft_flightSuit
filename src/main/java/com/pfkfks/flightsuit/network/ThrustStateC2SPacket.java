package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.SuitServerEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Boots-only thrust start/stop. Movement itself is client-authoritative (like all player movement);
 * the server only needs to know so it can drain energy, cancel fall distance and broadcast the pose.
 */
public class ThrustStateC2SPacket {
    private final boolean thrusting;

    public ThrustStateC2SPacket(boolean thrusting) {
        this.thrusting = thrusting;
    }

    public static void encode(ThrustStateC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.thrusting);
    }

    public static ThrustStateC2SPacket decode(FriendlyByteBuf buf) {
        return new ThrustStateC2SPacket(buf.readBoolean());
    }

    public static void handle(ThrustStateC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                SuitServerEvents.setThrusting(player, packet.thrusting);
            }
        });
        ctx.setPacketHandled(true);
    }
}
