package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The local player's skill cooldowns and card gauge, for the suit HUD (times are level game times). Since M17 also
 * the ultimate's cooldown, whether Super Saiyan is on (and how long it's locked out after running dry), and the
 * phantom's stolen skill (its name, "" for none) with its cooldown.
 */
public class WeaponStatusS2CPacket {
    public final long skill1Ready;
    public final long skill2Ready;
    public final int gauge;
    public final long spadeUntil;
    public final long ultReady;
    public final boolean superSaiyan;
    public final long ssjLockedUntil;
    public final String stolen;
    public final long stolenReady;

    public WeaponStatusS2CPacket(long skill1Ready, long skill2Ready, int gauge, long spadeUntil, long ultReady, boolean superSaiyan,
                                 long ssjLockedUntil, String stolen, long stolenReady) {
        this.skill1Ready = skill1Ready;
        this.skill2Ready = skill2Ready;
        this.gauge = gauge;
        this.spadeUntil = spadeUntil;
        this.ultReady = ultReady;
        this.superSaiyan = superSaiyan;
        this.ssjLockedUntil = ssjLockedUntil;
        this.stolen = stolen;
        this.stolenReady = stolenReady;
    }

    public static void encode(WeaponStatusS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.skill1Ready);
        buf.writeLong(packet.skill2Ready);
        buf.writeVarInt(packet.gauge);
        buf.writeLong(packet.spadeUntil);
        buf.writeLong(packet.ultReady);
        buf.writeBoolean(packet.superSaiyan);
        buf.writeLong(packet.ssjLockedUntil);
        buf.writeUtf(packet.stolen, 64);
        buf.writeLong(packet.stolenReady);
    }

    public static WeaponStatusS2CPacket decode(FriendlyByteBuf buf) {
        return new WeaponStatusS2CPacket(buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readBoolean(),
                buf.readLong(), buf.readUtf(64), buf.readLong());
    }

    public static void handle(WeaponStatusS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientWeapons.handleStatus(packet)));
        ctx.setPacketHandled(true);
    }
}
