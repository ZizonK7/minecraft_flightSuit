package com.pfkfks.flightsuit.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * M18: a signal fire's column of smoke (the fortress towers' beacons - FortressManager), drawn by the client for
 * {@code ticks} from {@code pos} up {@code height} blocks (client/SignalSmokeClient). Sent to players much farther
 * out than particles reach, so a fortress can be found by its smoke.
 */
public class SignalSmokeS2CPacket {
    public final BlockPos pos;
    public final int height;
    public final int ticks;

    public SignalSmokeS2CPacket(BlockPos pos, int height, int ticks) {
        this.pos = pos;
        this.height = height;
        this.ticks = ticks;
    }

    public static void encode(SignalSmokeS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.height);
        buf.writeVarInt(packet.ticks);
    }

    public static SignalSmokeS2CPacket decode(FriendlyByteBuf buf) {
        return new SignalSmokeS2CPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SignalSmokeS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.SignalSmokeClient.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
