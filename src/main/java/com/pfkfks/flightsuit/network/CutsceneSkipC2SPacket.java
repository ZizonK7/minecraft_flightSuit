package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The jump key during a cutscene (M17): skip to its end. */
public class CutsceneSkipC2SPacket {
    public CutsceneSkipC2SPacket() {
    }

    public static void encode(CutsceneSkipC2SPacket packet, FriendlyByteBuf buf) {
    }

    public static CutsceneSkipC2SPacket decode(FriendlyByteBuf buf) {
        return new CutsceneSkipC2SPacket();
    }

    public static void handle(CutsceneSkipC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                com.pfkfks.flightsuit.cutscene.CutsceneRunner.skip(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
