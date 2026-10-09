package com.pfkfks.flightsuit.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Briefly swings the camera to the front third-person view while the local player suits up - the
 * assembly is invisible from first person - then restores whatever view the player had.
 * Also freezes movement input for the duration (see ClientEvents).
 */
public final class CinematicCamera {
    private static CameraType previous;
    private static int remainingTicks;

    private CinematicCamera() {
    }

    public static void start(int ticks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (remainingTicks <= 0) {
            previous = minecraft.options.getCameraType();
        }
        remainingTicks = Math.max(remainingTicks, ticks);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }

    public static boolean isActive() {
        return remainingTicks > 0;
    }

    public static void tick() {
        if (remainingTicks <= 0) {
            return;
        }
        remainingTicks--;
        if (remainingTicks == 0 && previous != null) {
            Minecraft.getInstance().options.setCameraType(previous);
            previous = null;
        }
    }

    public static void reset() {
        if (remainingTicks > 0 && previous != null) {
            Minecraft.getInstance().options.setCameraType(previous);
        }
        remainingTicks = 0;
        previous = null;
    }
}
