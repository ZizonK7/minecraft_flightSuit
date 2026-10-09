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
        CHANNEL.registerMessage(id++, WeaponC2SPacket.class, WeaponC2SPacket::encode, WeaponC2SPacket::decode,
                WeaponC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
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
        CHANNEL.registerMessage(id++, BeamStateS2CPacket.class, BeamStateS2CPacket::encode, BeamStateS2CPacket::decode,
                BeamStateS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WeaponStatusS2CPacket.class, WeaponStatusS2CPacket::encode, WeaponStatusS2CPacket::decode,
                WeaponStatusS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, CardDuelS2CPacket.class, CardDuelS2CPacket::encode, CardDuelS2CPacket::decode,
                CardDuelS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, CardDuelC2SPacket.class, CardDuelC2SPacket::encode, CardDuelC2SPacket::decode,
                CardDuelC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ClawshotS2CPacket.class, ClawshotS2CPacket::encode, ClawshotS2CPacket::decode,
                ClawshotS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ResidentScreenS2CPacket.class, ResidentScreenS2CPacket::encode, ResidentScreenS2CPacket::decode,
                ResidentScreenS2CPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ResidentActionC2SPacket.class, ResidentActionC2SPacket::encode, ResidentActionC2SPacket::decode,
                ResidentActionC2SPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToTrackingAndSelf(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }
}
