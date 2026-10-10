package com.pfkfks.flightsuit.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Opens the resident screen, with what the client can't see on the entity itself: mood, meal, bed, and the
 * talent stars per job (by ResidentJob ordinal).
 */
public class ResidentScreenS2CPacket {
    public final int entityId;
    public final int mood;
    public final boolean fed;
    public final boolean hasBed;
    public final int[] talents;
    /** For an architect: the village's suggestions, open blueprints and building queue (VillageWorks.screenData). */
    public final @Nullable CompoundTag works;
    /** What they're doing right now, and their home and workplace or farm (JSON components; after the M13 test). */
    public final @Nullable CompoundTag info;

    public ResidentScreenS2CPacket(int entityId, int mood, boolean fed, boolean hasBed, int[] talents, @Nullable CompoundTag works,
                                   @Nullable CompoundTag info) {
        this.entityId = entityId;
        this.mood = mood;
        this.fed = fed;
        this.hasBed = hasBed;
        this.talents = talents;
        this.works = works;
        this.info = info;
    }

    public static void encode(ResidentScreenS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.mood);
        buf.writeBoolean(packet.fed);
        buf.writeBoolean(packet.hasBed);
        buf.writeVarIntArray(packet.talents);
        buf.writeNbt(packet.works);
        buf.writeNbt(packet.info);
    }

    public static ResidentScreenS2CPacket decode(FriendlyByteBuf buf) {
        return new ResidentScreenS2CPacket(buf.readVarInt(), buf.readByte(), buf.readBoolean(), buf.readBoolean(), buf.readVarIntArray(), buf.readNbt(),
                buf.readNbt());
    }

    public static void handle(ResidentScreenS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ResidentScreen.open(packet)));
        ctx.setPacketHandled(true);
    }
}
