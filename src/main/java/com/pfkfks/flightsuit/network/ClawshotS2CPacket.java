package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Where a hero's clawshot claw is this tick (sent every tick while it's out), or that it's back in the hand. */
public class ClawshotS2CPacket {
    public final int entityId;
    public final boolean active;
    public final Vec3 tip;

    public ClawshotS2CPacket(int entityId, boolean active, Vec3 tip) {
        this.entityId = entityId;
        this.active = active;
        this.tip = tip;
    }

    public static void encode(ClawshotS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeBoolean(packet.active);
        buf.writeDouble(packet.tip.x);
        buf.writeDouble(packet.tip.y);
        buf.writeDouble(packet.tip.z);
    }

    public static ClawshotS2CPacket decode(FriendlyByteBuf buf) {
        return new ClawshotS2CPacket(buf.readVarInt(), buf.readBoolean(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static void handle(ClawshotS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.HeroRenderer.handleClawshot(packet)));
        ctx.setPacketHandled(true);
    }
}
