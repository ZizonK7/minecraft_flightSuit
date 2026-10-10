package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One drawn ki effect (after the M17 test: beams, charging balls, flying shots, blasts, pillars of light, auras,
 * the Solar Flare) - sent by fx.KiFx, drawn by client.KiFxClient. Which fields mean what depends on the kind (KiFx).
 */
public class KiFxS2CPacket {
    public final byte kind;
    public final byte style;
    public final int entity;
    public final int other;
    public final int id;
    public final int colour;
    public final float size;
    public final int ticks;
    public final Vec3 a;
    public final Vec3 b;

    public KiFxS2CPacket(byte kind, byte style, int entity, int other, int id, int colour, float size, int ticks, Vec3 a, Vec3 b) {
        this.kind = kind;
        this.style = style;
        this.entity = entity;
        this.other = other;
        this.id = id;
        this.colour = colour;
        this.size = size;
        this.ticks = ticks;
        this.a = a;
        this.b = b;
    }

    public static void encode(KiFxS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.kind);
        buf.writeByte(packet.style);
        buf.writeVarInt(packet.entity);
        buf.writeVarInt(packet.other);
        buf.writeVarInt(packet.id);
        buf.writeInt(packet.colour);
        buf.writeFloat(packet.size);
        buf.writeVarInt(packet.ticks);
        writeVec(buf, packet.a);
        writeVec(buf, packet.b);
    }

    public static KiFxS2CPacket decode(FriendlyByteBuf buf) {
        return new KiFxS2CPacket(buf.readByte(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(),
                buf.readFloat(), buf.readVarInt(), readVec(buf), readVec(buf));
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(KiFxS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.KiFxClient.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
