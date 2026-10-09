package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The local player's skill cooldowns and card gauge, for the suit HUD (times are level game times). */
public class WeaponStatusS2CPacket {
    public final long skill1Ready;
    public final long skill2Ready;
    public final int gauge;
    public final long spadeUntil;

    public WeaponStatusS2CPacket(long skill1Ready, long skill2Ready, int gauge, long spadeUntil) {
        this.skill1Ready = skill1Ready;
        this.skill2Ready = skill2Ready;
        this.gauge = gauge;
        this.spadeUntil = spadeUntil;
    }

    public static void encode(WeaponStatusS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.skill1Ready);
        buf.writeLong(packet.skill2Ready);
        buf.writeVarInt(packet.gauge);
        buf.writeLong(packet.spadeUntil);
    }

    public static WeaponStatusS2CPacket decode(FriendlyByteBuf buf) {
        return new WeaponStatusS2CPacket(buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readLong());
    }

    public static void handle(WeaponStatusS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientWeapons.handleStatus(packet)));
        ctx.setPacketHandled(true);
    }
}
