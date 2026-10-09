package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Weapon input: primary trigger down / up (hold right click), or a skill key (X / C). See SuitWeapons. */
public class WeaponC2SPacket {
    private final byte action;

    public WeaponC2SPacket(byte action) {
        this.action = action;
    }

    public static void encode(WeaponC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.action);
    }

    public static WeaponC2SPacket decode(FriendlyByteBuf buf) {
        return new WeaponC2SPacket(buf.readByte());
    }

    public static void handle(WeaponC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                SuitWeapons.handle(player, packet.action);
            }
        });
        ctx.setPacketHandled(true);
    }
}
