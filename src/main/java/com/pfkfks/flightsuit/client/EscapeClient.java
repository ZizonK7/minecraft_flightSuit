package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.EscapeS2CPacket;
import com.pfkfks.flightsuit.suit.SuitTuning;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

/**
 * Flies Mark 2's emergency escape (suit/EscapeFlight) - player movement is the client's, so the route is flown here:
 * up (straight up while there's rock overhead, the server cutting it), across at cruising height (over whatever stands
 * in the way), then down onto home. The look stays free; the server says when it's over.
 */
public final class EscapeClient {
    private static Vec3 target;
    private static int cruise;
    private static int ticks;

    private EscapeClient() {
    }

    public static void handle(EscapeS2CPacket packet) {
        target = packet.on ? packet.to : null;
        cruise = packet.cruise;
        ticks = 0;
    }

    public static boolean active() {
        return target != null;
    }

    /** After SuitFlightClient (this has the last word on the speed). */
    public static void tick(LocalPlayer player) {
        if (target == null) {
            return;
        }
        if (!player.isAlive()) {
            target = null;
            return;
        }
        ticks++;
        Abilities abilities = player.getAbilities();
        if (abilities.mayfly && !abilities.flying) {
            abilities.flying = true;
            player.onUpdateAbilities();
        }
        Vec3 pos = player.position();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Vec3 velocity;
        if (horizontal > SuitTuning.ESCAPE_DESCENT_RADIUS) {
            Vec3 flat = new Vec3(dx / horizontal, 0.0D, dz / horizontal);
            if (pos.y < cruise - 3) {
                // Under rock: straight up the shaft being cut; in the open, up and away.
                boolean open = player.level().canSeeSky(BlockPos.containing(player.getEyePosition()));
                velocity = open ? flat.scale(0.5D).add(0.0D, SuitTuning.ESCAPE_CLIMB_SPEED, 0.0D)
                        : new Vec3(0.0D, SuitTuning.ESCAPE_DRILL_SPEED, 0.0D);
            } else {
                double speed = Math.min(SuitTuning.ESCAPE_CRUISE_SPEED, 0.4D + ticks * 0.05D);
                // Easing off before the descent.
                speed = Math.min(speed, (horizontal - SuitTuning.ESCAPE_DESCENT_RADIUS) * 0.1D + 0.4D);
                double rise = player.horizontalCollision ? 1.0D : Mth.clamp((cruise - pos.y) * 0.1D, -0.6D, 0.6D);
                velocity = flat.scale(speed).add(0.0D, rise, 0.0D);
            }
        } else {
            Vec3 down = target.subtract(pos);
            double distance = down.length();
            velocity = distance < 1.0E-3D ? Vec3.ZERO : down.scale(Math.min(1.2D, distance * 0.25D + 0.1D) / distance);
        }
        player.setDeltaMovement(velocity);
        player.fallDistance = 0.0F;
    }

    public static void reset() {
        target = null;
        ticks = 0;
    }
}
