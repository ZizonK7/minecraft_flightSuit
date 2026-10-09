package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.Companions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** H key: companions attack what the player is aiming at (the server does the aiming raycast). */
public class CommandAttackC2SPacket {
    public static void encode(CommandAttackC2SPacket packet, FriendlyByteBuf buf) {
    }

    public static CommandAttackC2SPacket decode(FriendlyByteBuf buf) {
        return new CommandAttackC2SPacket();
    }

    public static void handle(CommandAttackC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                Companions.commandAttack(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
