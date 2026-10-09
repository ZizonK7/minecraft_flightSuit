package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A player's primary weapon started or stopped (drives the beam visual and the aimed arm on every client). */
public class BeamStateS2CPacket {
    public final int entityId;
    /** SuitWeapons.FIRE_* (FIRE_NONE = stopped). */
    public final byte kind;

    public BeamStateS2CPacket(int entityId, byte kind) {
        this.entityId = entityId;
        this.kind = kind;
    }

    public static void encode(BeamStateS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.kind);
    }

    public static BeamStateS2CPacket decode(FriendlyByteBuf buf) {
        return new BeamStateS2CPacket(buf.readVarInt(), buf.readByte());
    }

    public static void handle(BeamStateS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientWeapons.handleBeam(packet)));
        ctx.setPacketHandled(true);
    }
}
