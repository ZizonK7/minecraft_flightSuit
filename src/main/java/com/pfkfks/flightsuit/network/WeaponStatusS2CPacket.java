package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The local player's skill cooldowns and card gauge, for the suit HUD (times are level game times). Since M17 also
 * the ultimate's cooldown, whether Super Saiyan is on (and how long it's locked out after running dry), and the
 * phantom's stolen skill (its name, "" for none) with its cooldown. M18: the third skill's (B) cooldown, the end of
 * whatever timed buff is on (overdrive, the Triforce, x-ray, Heaven's Door) and the Hulkbuster's armour mode; after the
 * first M18 test, the hero's chain and whether the Hulkbuster holds a chunk of ground ready to throw.
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
    public final long skill3Ready;
    public final long buffUntil;
    public final boolean armourMode;
    public final byte chain;
    public final boolean holding;

    public WeaponStatusS2CPacket(long skill1Ready, long skill2Ready, int gauge, long spadeUntil, long ultReady, boolean superSaiyan,
                                 long ssjLockedUntil, String stolen, long stolenReady, long skill3Ready, long buffUntil, boolean armourMode,
                                 byte chain, boolean holding) {
        this.skill1Ready = skill1Ready;
        this.skill2Ready = skill2Ready;
        this.gauge = gauge;
        this.spadeUntil = spadeUntil;
        this.ultReady = ultReady;
        this.superSaiyan = superSaiyan;
        this.ssjLockedUntil = ssjLockedUntil;
        this.stolen = stolen;
        this.stolenReady = stolenReady;
        this.skill3Ready = skill3Ready;
        this.buffUntil = buffUntil;
        this.armourMode = armourMode;
        this.chain = chain;
        this.holding = holding;
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
        buf.writeLong(packet.skill3Ready);
        buf.writeLong(packet.buffUntil);
        buf.writeBoolean(packet.armourMode);
        buf.writeByte(packet.chain);
        buf.writeBoolean(packet.holding);
    }

    public static WeaponStatusS2CPacket decode(FriendlyByteBuf buf) {
        return new WeaponStatusS2CPacket(buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readBoolean(),
                buf.readLong(), buf.readUtf(64), buf.readLong(), buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readByte(),
                buf.readBoolean());
    }

    public static void handle(WeaponStatusS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientWeapons.handleStatus(packet)));
        ctx.setPacketHandled(true);
    }
}
