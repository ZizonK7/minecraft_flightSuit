package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The card duel table as the phantom sees it (see CardDuel for the card encoding and results). */
public class CardDuelS2CPacket {
    public final int opponentId;
    public final String opponentName;
    public final boolean boss;
    public final List<Byte> mine;
    /** The house's hand; CardDuel.FACE_DOWN for its hole card while the hand is being played. */
    public final List<Byte> theirs;
    public final byte result;
    public final boolean sleightUsed;
    /** Dealing from the bottom: the cards on offer (FACE_DOWN when not peeking). */
    public final byte peekTop;
    public final byte peekBottom;

    public CardDuelS2CPacket(int opponentId, String opponentName, boolean boss, List<Byte> mine, List<Byte> theirs, byte result,
                             boolean sleightUsed, byte peekTop, byte peekBottom) {
        this.opponentId = opponentId;
        this.opponentName = opponentName;
        this.boss = boss;
        this.mine = mine;
        this.theirs = theirs;
        this.result = result;
        this.sleightUsed = sleightUsed;
        this.peekTop = peekTop;
        this.peekBottom = peekBottom;
    }

    public static void encode(CardDuelS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.opponentId);
        buf.writeUtf(packet.opponentName);
        buf.writeBoolean(packet.boss);
        writeHand(buf, packet.mine);
        writeHand(buf, packet.theirs);
        buf.writeByte(packet.result);
        buf.writeBoolean(packet.sleightUsed);
        buf.writeByte(packet.peekTop);
        buf.writeByte(packet.peekBottom);
    }

    public static CardDuelS2CPacket decode(FriendlyByteBuf buf) {
        return new CardDuelS2CPacket(buf.readVarInt(), buf.readUtf(), buf.readBoolean(), readHand(buf), readHand(buf),
                buf.readByte(), buf.readBoolean(), buf.readByte(), buf.readByte());
    }

    private static void writeHand(FriendlyByteBuf buf, List<Byte> hand) {
        buf.writeVarInt(hand.size());
        for (byte card : hand) {
            buf.writeByte(card);
        }
    }

    private static List<Byte> readHand(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Byte> hand = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            hand.add(buf.readByte());
        }
        return hand;
    }

    public static void handle(CardDuelS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.CardDuelScreen.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
