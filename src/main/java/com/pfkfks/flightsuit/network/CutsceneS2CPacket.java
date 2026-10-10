package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A cutscene for this player (M17, cutscene.CutsceneRunner -> client CutsceneClient): START (which script, where, which
 * way), LINE (the subtitle: who says it and what), SHAKE (the screen shakes - also outside cutscenes, for a
 * transformation), END.
 */
public class CutsceneS2CPacket {
    public static final byte START = 0;
    public static final byte LINE = 1;
    public static final byte SHAKE = 2;
    public static final byte END = 3;

    public final byte kind;
    public final String id;
    public final Vec3 anchor;
    public final float yaw;
    public final Component speaker;
    public final Component text;
    public final int ticks;

    private CutsceneS2CPacket(byte kind, String id, Vec3 anchor, float yaw, Component speaker, Component text, int ticks) {
        this.kind = kind;
        this.id = id;
        this.anchor = anchor;
        this.yaw = yaw;
        this.speaker = speaker;
        this.text = text;
        this.ticks = ticks;
    }

    public static CutsceneS2CPacket start(String id, Vec3 anchor, float yaw) {
        return new CutsceneS2CPacket(START, id, anchor, yaw, Component.empty(), Component.empty(), 0);
    }

    /** A subtitle; an empty speaker = narration. */
    public static CutsceneS2CPacket line(Component speaker, Component text) {
        return new CutsceneS2CPacket(LINE, "", Vec3.ZERO, 0.0F, speaker, text, 0);
    }

    public static CutsceneS2CPacket shake(int ticks) {
        return new CutsceneS2CPacket(SHAKE, "", Vec3.ZERO, 0.0F, Component.empty(), Component.empty(), ticks);
    }

    public static CutsceneS2CPacket end() {
        return new CutsceneS2CPacket(END, "", Vec3.ZERO, 0.0F, Component.empty(), Component.empty(), 0);
    }

    public static void encode(CutsceneS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.kind);
        switch (packet.kind) {
            case START -> {
                buf.writeUtf(packet.id, 128);
                buf.writeDouble(packet.anchor.x);
                buf.writeDouble(packet.anchor.y);
                buf.writeDouble(packet.anchor.z);
                buf.writeFloat(packet.yaw);
            }
            case LINE -> {
                buf.writeComponent(packet.speaker);
                buf.writeComponent(packet.text);
            }
            case SHAKE -> buf.writeVarInt(packet.ticks);
            default -> {
            }
        }
    }

    public static CutsceneS2CPacket decode(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        return switch (kind) {
            case START -> new CutsceneS2CPacket(START, buf.readUtf(128), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readFloat(), Component.empty(), Component.empty(), 0);
            case LINE -> new CutsceneS2CPacket(LINE, "", Vec3.ZERO, 0.0F, buf.readComponent(), buf.readComponent(), 0);
            case SHAKE -> shake(buf.readVarInt());
            default -> end();
        };
    }

    public static void handle(CutsceneS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.CutsceneClient.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
