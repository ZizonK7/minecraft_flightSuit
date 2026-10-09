package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.SuitUpManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** G key: tap (suit up / step out / board a companion) or hold (everything home). */
public class SuitToggleC2SPacket {
    private final boolean hold;

    public SuitToggleC2SPacket(boolean hold) {
        this.hold = hold;
    }

    public static void encode(SuitToggleC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.hold);
    }

    public static SuitToggleC2SPacket decode(FriendlyByteBuf buf) {
        return new SuitToggleC2SPacket(buf.readBoolean());
    }

    public static void handle(SuitToggleC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                SuitUpManager.toggle(player, packet.hold);
            }
        });
        ctx.setPacketHandled(true);
    }
}
