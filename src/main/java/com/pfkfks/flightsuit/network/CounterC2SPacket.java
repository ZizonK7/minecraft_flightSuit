package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.CounterHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Counter key edges: press (parry window), held long enough (shield on), release (shield off); Mark 3's shadow step
 * with the range the client showed (M17: the mouse wheel sets it).
 */
public class CounterC2SPacket {
    private final byte action;
    private final float range;

    public CounterC2SPacket(byte action) {
        this(action, 0.0F);
    }

    public CounterC2SPacket(byte action, float range) {
        this.action = action;
        this.range = range;
    }

    public static void encode(CounterC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.action);
        buf.writeFloat(packet.range);
    }

    public static CounterC2SPacket decode(FriendlyByteBuf buf) {
        return new CounterC2SPacket(buf.readByte(), buf.readFloat());
    }

    public static void handle(CounterC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                CounterHandler.handleInput(player, packet.action, packet.range);
            }
        });
        ctx.setPacketHandled(true);
    }
}
