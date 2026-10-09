package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.RepulsorHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Empty-hand right click while wearing a suit chestplate. Cooldown/energy are checked server-side. */
public class RepulsorC2SPacket {
    public static void encode(RepulsorC2SPacket packet, FriendlyByteBuf buf) {
    }

    public static RepulsorC2SPacket decode(FriendlyByteBuf buf) {
        return new RepulsorC2SPacket();
    }

    public static void handle(RepulsorC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                RepulsorHandler.fire(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
