package com.pfkfks.flightsuit.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** An EDITH alert for the HUD (suit/EdithAlert): what, details, and where (dimension id + position, if any). */
public class EdithAlertS2CPacket {
    public final Component title;
    public final Component detail;
    public final String dimension;
    public final BlockPos pos;
    public final boolean hasPos;
    public final int color;

    public EdithAlertS2CPacket(Component title, Component detail, String dimension, BlockPos pos, boolean hasPos, int color) {
        this.title = title;
        this.detail = detail;
        this.dimension = dimension;
        this.pos = pos;
        this.hasPos = hasPos;
        this.color = color;
    }

    public static void encode(EdithAlertS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeComponent(packet.title);
        buf.writeComponent(packet.detail);
        buf.writeUtf(packet.dimension);
        buf.writeBlockPos(packet.pos);
        buf.writeBoolean(packet.hasPos);
        buf.writeInt(packet.color);
    }

    public static EdithAlertS2CPacket decode(FriendlyByteBuf buf) {
        return new EdithAlertS2CPacket(buf.readComponent(), buf.readComponent(), buf.readUtf(), buf.readBlockPos(), buf.readBoolean(), buf.readInt());
    }

    public static void handle(EdithAlertS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.EdithAlertOverlay.show(packet)));
        ctx.setPacketHandled(true);
    }
}
