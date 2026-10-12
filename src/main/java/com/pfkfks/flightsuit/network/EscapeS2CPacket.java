package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 2026-10-12: Mark 2's emergency escape (suit/EscapeFlight) for the local player - where home is and the cruising
 * height, which client/EscapeClient flies (player movement is the client's); {@code on} false: the flight is over.
 */
public class EscapeS2CPacket {
    public final boolean on;
    public final Vec3 to;
    public final int cruise;

    public EscapeS2CPacket(boolean on, Vec3 to, int cruise) {
        this.on = on;
        this.to = to;
        this.cruise = cruise;
    }

    public static EscapeS2CPacket off() {
        return new EscapeS2CPacket(false, Vec3.ZERO, 0);
    }

    public static void encode(EscapeS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.on);
        buf.writeDouble(packet.to.x);
        buf.writeDouble(packet.to.y);
        buf.writeDouble(packet.to.z);
        buf.writeVarInt(packet.cruise);
    }

    public static EscapeS2CPacket decode(FriendlyByteBuf buf) {
        return new EscapeS2CPacket(buf.readBoolean(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt());
    }

    public static void handle(EscapeS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.EscapeClient.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
