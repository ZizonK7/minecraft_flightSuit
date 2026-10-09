package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A player's nano shield went up or down (drives the shield visual and the arm pose on every client). */
public class ShieldStateS2CPacket {
    public final int entityId;
    public final boolean active;

    public ShieldStateS2CPacket(int entityId, boolean active) {
        this.entityId = entityId;
        this.active = active;
    }

    public static void encode(ShieldStateS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.active);
    }

    public static ShieldStateS2CPacket decode(FriendlyByteBuf buf) {
        return new ShieldStateS2CPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(ShieldStateS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientPacketHandler.handleShieldState(packet)));
        ctx.setPacketHandled(true);
    }
}
