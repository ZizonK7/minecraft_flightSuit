package com.pfkfks.flightsuit.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A lasting look on an entity switched on or off (M17): a Super Saiyan's golden hair (SwordArts), a mob turned
 * candy-small by the stolen candy beam (CandyShrink).
 */
public class EntityFxS2CPacket {
    public static final byte GOLDEN = 0;
    public static final byte CANDY = 1;

    public final int entityId;
    public final byte effect;
    public final boolean on;

    public EntityFxS2CPacket(int entityId, byte effect, boolean on) {
        this.entityId = entityId;
        this.effect = effect;
        this.on = on;
    }

    public static void encode(EntityFxS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.effect);
        buf.writeBoolean(packet.on);
    }

    public static EntityFxS2CPacket decode(FriendlyByteBuf buf) {
        return new EntityFxS2CPacket(buf.readVarInt(), buf.readByte(), buf.readBoolean());
    }

    public static void handle(EntityFxS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.pfkfks.flightsuit.client.ClientPacketHandler.handleEntityFx(packet)));
        ctx.setPacketHandled(true);
    }
}
