package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import com.pfkfks.flightsuit.suit.FlightPose;
import com.pfkfks.flightsuit.suit.SuitAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void handleSuitAnim(SuitAnimS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(packet.entityId) instanceof AbstractClientPlayer player)) {
            return;
        }
        if (packet.kind == SuitAnimS2CPacket.KIND_POSE) {
            SuitAnimator.setPose(player, FlightPose.byId(packet.value));
            return;
        }
        SuitAnim anim = SuitAnim.byId(packet.value);
        SuitAnimator.playOneShot(player, anim);
        if (anim == SuitAnim.SUIT_UP_GROUND && player == minecraft.player) {
            CinematicCamera.start(packet.durationTicks);
        }
    }
}
