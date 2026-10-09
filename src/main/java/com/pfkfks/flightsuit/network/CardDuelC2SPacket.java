package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.CardDuel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A move at the card duel table: hit, stand, deal from the bottom, take the top / bottom card. */
public class CardDuelC2SPacket {
    private final byte action;

    public CardDuelC2SPacket(byte action) {
        this.action = action;
    }

    public static void encode(CardDuelC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.action);
    }

    public static CardDuelC2SPacket decode(FriendlyByteBuf buf) {
        return new CardDuelC2SPacket(buf.readByte());
    }

    public static void handle(CardDuelC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                CardDuel.act(player, packet.action);
            }
        });
        ctx.setPacketHandled(true);
    }
}
