package com.pfkfks.flightsuit.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * M18: the ores the suit's sensors found (Mark 1's EDITH scan, Mark 2's x-ray - MarkArts), for the client to draw
 * through the walls for {@code ticks} (client/OreSightClient).
 */
public class OreSightS2CPacket {
    public static final int MAX = 400;

    public final List<BlockPos> ores;
    public final int ticks;

    public OreSightS2CPacket(List<BlockPos> ores, int ticks) {
        this.ores = ores;
        this.ticks = ticks;
    }

    public static void encode(OreSightS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.ticks);
        buf.writeVarInt(packet.ores.size());
        for (BlockPos pos : packet.ores) {
            buf.writeBlockPos(pos);
        }
    }

    public static OreSightS2CPacket decode(FriendlyByteBuf buf) {
        int ticks = buf.readVarInt();
        int count = Math.min(MAX, buf.readVarInt());
        List<BlockPos> ores = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ores.add(buf.readBlockPos());
        }
        return new OreSightS2CPacket(ores, ticks);
    }

    public static void handle(OreSightS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.OreSightClient.handle(packet)));
        ctx.setPacketHandled(true);
    }
}
