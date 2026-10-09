package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.EdithStatusS2CPacket;
import com.pfkfks.flightsuit.network.ShieldStateS2CPacket;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import com.pfkfks.flightsuit.suit.FlightPose;
import com.pfkfks.flightsuit.suit.SuitAnim;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

public final class ClientPacketHandler {
    /** Latest main-station summary for the EDITH HUD (null until the first one arrives). */
    public static EdithStatusS2CPacket edithStatus;

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
        if (player == minecraft.player) {
            // Ground: watch the pieces arrive from the front. Fall: from behind, to see the suit dive onto your back.
            if (anim == SuitAnim.SUIT_UP_GROUND || anim == SuitAnim.SUIT_UP_STATION) {
                CinematicCamera.start(packet.durationTicks, CameraType.THIRD_PERSON_FRONT);
            } else if (anim == SuitAnim.SUIT_UP_FALL) {
                CinematicCamera.start(packet.durationTicks, CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    public static void handleShieldState(ShieldStateS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(packet.entityId) instanceof AbstractClientPlayer player) {
            SuitAnimator.setShield(player, packet.active);
        }
    }

    public static void handleEdithStatus(EdithStatusS2CPacket packet) {
        edithStatus = packet;
    }
}
