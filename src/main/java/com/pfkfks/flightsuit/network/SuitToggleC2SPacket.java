package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.SuitUpManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** G key: suit up from a capsule, or pack the worn suit away. */
public class SuitToggleC2SPacket {
    public static void encode(SuitToggleC2SPacket packet, FriendlyByteBuf buf) {
    }

    public static SuitToggleC2SPacket decode(FriendlyByteBuf buf) {
        return new SuitToggleC2SPacket();
    }

    public static void handle(SuitToggleC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                SuitUpManager.toggle(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
