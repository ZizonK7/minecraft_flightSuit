package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.suit.MainStation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Periodic main-station summary for the EDITH HUD (sent ~once a second to players who have the glasses). */
public class EdithStatusS2CPacket {
    public static final byte NO_STATION = 0;
    public static final byte OTHER_DIMENSION = 1;
    /** Linked, same dimension, but its chunk isn't loaded - no live readings. */
    public static final byte NO_SIGNAL = 2;
    public static final byte ONLINE = 3;

    public final byte state;
    public final int distance;
    public final String suitName;
    public final int suitChargePercent;
    public final int stationEnergy;
    public final int stationCapacity;

    public EdithStatusS2CPacket(byte state, int distance, String suitName, int suitChargePercent, int stationEnergy, int stationCapacity) {
        this.state = state;
        this.distance = distance;
        this.suitName = suitName;
        this.suitChargePercent = suitChargePercent;
        this.stationEnergy = stationEnergy;
        this.stationCapacity = stationCapacity;
    }

    public static EdithStatusS2CPacket of(ServerPlayer player) {
        MainStation.Link link = MainStation.get(player);
        if (link == null) {
            return new EdithStatusS2CPacket(NO_STATION, 0, "", -1, 0, 0);
        }
        if (!MainStation.isInPlayerDimension(player, link)) {
            return new EdithStatusS2CPacket(OTHER_DIMENSION, 0, "", -1, 0, 0);
        }
        int distance = (int) Math.sqrt(link.pos().distToCenterSqr(player.position()));
        SuitStationBlockEntity station = MainStation.peek(player);
        if (station == null) {
            return new EdithStatusS2CPacket(NO_SIGNAL, distance, "", -1, 0, 0);
        }
        String name = station.getSuitType() == null ? "" : station.getSuitType().hudName();
        return new EdithStatusS2CPacket(ONLINE, distance, name, station.suitChargePercent(),
                station.energy().getEnergyStored(), station.energy().getMaxEnergyStored());
    }

    public static void encode(EdithStatusS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.state);
        buf.writeVarInt(packet.distance);
        buf.writeUtf(packet.suitName, 64);
        buf.writeInt(packet.suitChargePercent);
        buf.writeVarInt(packet.stationEnergy);
        buf.writeVarInt(packet.stationCapacity);
    }

    public static EdithStatusS2CPacket decode(FriendlyByteBuf buf) {
        return new EdithStatusS2CPacket(buf.readByte(), buf.readVarInt(), buf.readUtf(64), buf.readInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(EdithStatusS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientPacketHandler.handleEdithStatus(packet)));
        ctx.setPacketHandled(true);
    }
}
