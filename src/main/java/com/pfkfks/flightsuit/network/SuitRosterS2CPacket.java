package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.suit.SuitWheel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The player's reachable suits, for the suit wheel (sent when the wheel is opened). */
public class SuitRosterS2CPacket {
    public final List<SuitWheel.Entry> entries;

    public SuitRosterS2CPacket(List<SuitWheel.Entry> entries) {
        this.entries = entries;
    }

    public static void encode(SuitRosterS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entries.size());
        for (SuitWheel.Entry entry : packet.entries) {
            buf.writeByte(entry.kind());
            buf.writeLong(entry.key());
            buf.writeUtf(entry.name(), 64);
            buf.writeVarInt(entry.charge());
            buf.writeVarInt(entry.durability());
            buf.writeVarInt(entry.distance());
            buf.writeBoolean(entry.main());
            buf.writeBoolean(entry.broken());
        }
    }

    public static SuitRosterS2CPacket decode(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 64);
        List<SuitWheel.Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new SuitWheel.Entry(buf.readByte(), buf.readLong(), buf.readUtf(64), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        }
        return new SuitRosterS2CPacket(entries);
    }

    public static void handle(SuitRosterS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientPacketHandler.handleRoster(packet)));
        ctx.setPacketHandled(true);
    }
}
