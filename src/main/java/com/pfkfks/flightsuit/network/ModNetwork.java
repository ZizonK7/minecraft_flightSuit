package com.pfkfks.flightsuit.network;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FlightSuitMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SuitToggleC2SPacket.class, SuitToggleC2SPacket::encode, SuitToggleC2SPacket::decode,
                SuitToggleC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, RepulsorC2SPacket.class, RepulsorC2SPacket::encode, RepulsorC2SPacket::decode,
                RepulsorC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ThrustStateC2SPacket.class, ThrustStateC2SPacket::encode, ThrustStateC2SPacket::decode,
                ThrustStateC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SuitAnimS2CPacket.class, SuitAnimS2CPacket::encode, SuitAnimS2CPacket::decode,
                SuitAnimS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, CommandAttackC2SPacket.class, CommandAttackC2SPacket::encode, CommandAttackC2SPacket::decode,
                CommandAttackC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CounterC2SPacket.class, CounterC2SPacket::encode, CounterC2SPacket::decode,
                CounterC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ShieldStateS2CPacket.class, ShieldStateS2CPacket::encode, ShieldStateS2CPacket::decode,
                ShieldStateS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SuitWheelC2SPacket.class, SuitWheelC2SPacket::encode, SuitWheelC2SPacket::decode,
                SuitWheelC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SuitRosterS2CPacket.class, SuitRosterS2CPacket::encode, SuitRosterS2CPacket::decode,
                SuitRosterS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, EdithStatusS2CPacket.class, EdithStatusS2CPacket::encode, EdithStatusS2CPacket::decode,
                EdithStatusS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RemoteLinkS2CPacket.class, RemoteLinkS2CPacket::encode, RemoteLinkS2CPacket::decode,
                RemoteLinkS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToTrackingAndSelf(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }
}
