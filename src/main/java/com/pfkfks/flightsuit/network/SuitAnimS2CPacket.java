package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.FlightPose;
import com.pfkfks.flightsuit.suit.SuitAnim;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells clients to play a one-shot suit animation or switch a player's continuous flight pose. */
public class SuitAnimS2CPacket {
    public static final byte KIND_ONE_SHOT = 0;
    public static final byte KIND_POSE = 1;

    public final int entityId;
    public final byte kind;
    public final byte value;
    /** One-shot length in ticks where it matters (suit-up drives the cinematic camera), else 0. */
    public final int durationTicks;

    private SuitAnimS2CPacket(int entityId, byte kind, byte value, int durationTicks) {
        this.entityId = entityId;
        this.kind = kind;
        this.value = value;
        this.durationTicks = durationTicks;
    }

    public static SuitAnimS2CPacket oneShot(Player player, SuitAnim anim, int durationTicks) {
        return new SuitAnimS2CPacket(player.getId(), KIND_ONE_SHOT, (byte) anim.ordinal(), durationTicks);
    }

    public static SuitAnimS2CPacket pose(Player player, FlightPose pose) {
        return new SuitAnimS2CPacket(player.getId(), KIND_POSE, (byte) pose.ordinal(), 0);
    }

    public static void encode(SuitAnimS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.kind);
        buf.writeByte(packet.value);
        buf.writeVarInt(packet.durationTicks);
    }

    public static SuitAnimS2CPacket decode(FriendlyByteBuf buf) {
        return new SuitAnimS2CPacket(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readVarInt());
    }

    public static void handle(SuitAnimS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientPacketHandler.handleSuitAnim(packet)));
        ctx.setPacketHandled(true);
    }
}
