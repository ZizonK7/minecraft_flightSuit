package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The local player's remote link opened or closed (drives the link transition and the remote HUD). */
public class RemoteLinkS2CPacket {
    public final boolean active;
    public final Vec3 bodyPos;
    public final String suitName;

    public RemoteLinkS2CPacket(boolean active, Vec3 bodyPos, String suitName) {
        this.active = active;
        this.bodyPos = bodyPos;
        this.suitName = suitName;
    }

    public static void encode(RemoteLinkS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeDouble(packet.bodyPos.x);
        buf.writeDouble(packet.bodyPos.y);
        buf.writeDouble(packet.bodyPos.z);
        buf.writeUtf(packet.suitName);
    }

    public static RemoteLinkS2CPacket decode(FriendlyByteBuf buf) {
        return new RemoteLinkS2CPacket(buf.readBoolean(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readUtf());
    }

    public static void handle(RemoteLinkS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.RemoteLinkClient.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
