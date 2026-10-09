package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.village.ResidentEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A choice on the resident screen: take a wanderer in, send them away, or give a resident a job. */
public class ResidentActionC2SPacket {
    private final int entityId;
    private final int action;
    private final int arg;

    public ResidentActionC2SPacket(int entityId, int action, int arg) {
        this.entityId = entityId;
        this.action = action;
        this.arg = arg;
    }

    public static void encode(ResidentActionC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.action);
        buf.writeByte(packet.arg);
    }

    public static ResidentActionC2SPacket decode(FriendlyByteBuf buf) {
        return new ResidentActionC2SPacket(buf.readVarInt(), buf.readByte(), buf.readByte());
    }

    public static void handle(ResidentActionC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && player.level().getEntity(packet.entityId) instanceof ResidentEntity resident) {
                resident.handleAction(player, packet.action, packet.arg);
            }
        });
        ctx.setPacketHandled(true);
    }
}
