package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.ThrustStateC2SPacket;
import com.pfkfks.flightsuit.suit.SuitEnergy;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side movement for the suit (player movement is client-authoritative in Minecraft):
 * - full set + vanilla flying + sprint-forward = Iron Man boost along the look direction;
 * - boots only = press jump again mid-air to fire the thrusters (short climb, then a slow glide).
 */
public final class SuitFlightClient {
    private static boolean jumpWasDown;
    private static boolean thrustArmed;
    private static int airThrustTicks;
    private static boolean sentThrusting;

    private SuitFlightClient() {
    }

    public static void tick(LocalPlayer player) {
        WornSuit worn = WornSuit.of(player);
        tickBoost(player, worn);
        tickBootsThrust(player, worn);
    }

    private static void tickBoost(LocalPlayer player, WornSuit worn) {
        if (!worn.canFly() || !player.getAbilities().flying || !player.isSprinting() || player.input.forwardImpulse <= 0.0F) {
            return;
        }
        Vec3 look = player.getLookAngle();
        // M17: the Hulkbuster boosts at 70% of a Mark 1, a Super Saiyan half again as fast.
        double factor = worn.fullSet() && WornSuit.primaryType(player).suitClass() == com.pfkfks.flightsuit.suit.SuitClass.HULKBUSTER
                ? SuitTuning.HULKBUSTER_FLIGHT_FACTOR : 1.0D;
        if (ClientWeapons.superSaiyan) {
            factor *= SuitTuning.SSJ_MULTIPLIER;
        }
        Vec3 velocity = player.getDeltaMovement().add(
                look.x * SuitTuning.BOOST_ACCEL_HORIZONTAL * factor,
                look.y * SuitTuning.BOOST_ACCEL_VERTICAL * factor,
                look.z * SuitTuning.BOOST_ACCEL_HORIZONTAL * factor);
        if (velocity.length() > SuitTuning.BOOST_MAX_SPEED * factor) {
            velocity = velocity.normalize().scale(SuitTuning.BOOST_MAX_SPEED * factor);
        }
        player.setDeltaMovement(velocity);
    }

    private static void tickBootsThrust(LocalPlayer player, WornSuit worn) {
        boolean jump = player.input.jumping;
        if (player.onGround()) {
            airThrustTicks = 0;
            thrustArmed = false;
        }
        boolean canThrust = WornSuit.hasThrusterBoots(player) && !worn.fullSet()
                && !player.getAbilities().flying
                && !player.onGround() && !player.isInWater() && !player.isPassenger()
                && SuitEnergy.available(player, EquipmentSlot.FEET) > 0;
        // Only a fresh press while already airborne fires the boots, so normal jumps stay normal jumps.
        if (canThrust && jump && !jumpWasDown) {
            thrustArmed = true;
        }
        if (!jump) {
            thrustArmed = false;
        }
        boolean thrusting = canThrust && thrustArmed;
        if (thrusting) {
            Vec3 v = player.getDeltaMovement();
            if (airThrustTicks < SuitTuning.BOOTS_THRUST_TICKS) {
                v = new Vec3(v.x, Math.min(v.y + SuitTuning.BOOTS_THRUST_ACCEL, SuitTuning.BOOTS_THRUST_MAX_RISE), v.z);
                airThrustTicks++;
            } else if (v.y < SuitTuning.BOOTS_GLIDE_MAX_FALL) {
                v = new Vec3(v.x, SuitTuning.BOOTS_GLIDE_MAX_FALL, v.z);
            }
            player.setDeltaMovement(v);
            player.fallDistance = 0.0F;
        }
        if (thrusting != sentThrusting) {
            sentThrusting = thrusting;
            ModNetwork.sendToServer(new ThrustStateC2SPacket(thrusting));
        }
        jumpWasDown = jump;
    }

    public static void reset() {
        jumpWasDown = false;
        thrustArmed = false;
        airThrustTicks = 0;
        sentThrusting = false;
    }
}
