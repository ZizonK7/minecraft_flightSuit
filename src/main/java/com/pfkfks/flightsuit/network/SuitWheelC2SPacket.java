package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.SuitWheel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Suit wheel: open (ask for the roster) or act on an entry. */
public class SuitWheelC2SPacket {
    private static final byte OPEN = -1;

    private final byte action;
    private final byte kind;
    private final long key;

    private SuitWheelC2SPacket(byte action, byte kind, long key) {
        this.action = action;
        this.kind = kind;
        this.key = key;
    }

    public static SuitWheelC2SPacket open() {
        return new SuitWheelC2SPacket(OPEN, (byte) 0, 0L);
    }

    public static SuitWheelC2SPacket act(byte action, byte kind, long key) {
        return new SuitWheelC2SPacket(action, kind, key);
    }

    public static void encode(SuitWheelC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.action);
        buf.writeByte(packet.kind);
        buf.writeLong(packet.key);
    }

    public static SuitWheelC2SPacket decode(FriendlyByteBuf buf) {
        return new SuitWheelC2SPacket(buf.readByte(), buf.readByte(), buf.readLong());
    }

    public static void handle(SuitWheelC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (packet.action == OPEN) {
                SuitWheel.open(player);
            } else {
                SuitWheel.act(player, packet.kind, packet.key, packet.action);
            }
        });
        ctx.setPacketHandled(true);
    }
}
