package com.pfkfks.flightsuit.suit;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * The station rig's choreography (Iron Man 2 / Avengers catwalk), shared by the server - when pieces really
 * go on and come off - and the renderer - where the arms and the pieces they carry are at any moment. All
 * times are ticks since the run started; everything here is a pure function of that, so both sides agree.
 *
 * Suit up: the arms reach into the suit standing on the platform and lift it apart (helmet up, chest and legs
 * back; the boots stay open on the floor), you walk in, and they put it back together around you: boots,
 * legs, chest, helmet, eyes on.
 * Suit off: you walk in, the arms take the suit off you top-down, you step out forward, and the arms stand
 * the empty suit back up on the platform.
 *
 * Station frame (+x = the suit's right, +y up, +z = toward the back; origin = platform top, center): the
 * ceiling arm carries the helmet, the upper pair (on the back pillars) the chest, the lower pair the legs.
 */
public final class StationRigTimeline {
    public static final byte NONE = 0;
    public static final byte SUIT_UP = 1;
    public static final byte SUIT_OFF = 2;

    /** How long one arm move (lift, retract) takes. */
    public static final int MOVE = 10;

    public static final int UP_REACH = 8;
    public static final int UP_LIFT_END = UP_REACH + MOVE;
    public static final int UP_WALK_START = 10;
    public static final int UP_WALK_END = 26;
    public static final int UP_EYES = 60;
    public static final int UP_END = 70;

    public static final int OFF_REACH_START = 4;
    public static final int OFF_WALK_END = 14;
    public static final int OFF_STEP_OUT_START = 38;
    public static final int OFF_STEP_OUT_END = 50;
    public static final int OFF_REASSEMBLE_START = 50;
    public static final int OFF_DOCK = 62;
    public static final int OFF_END = 72;

    private StationRigTimeline() {
    }

    public static int end(byte mode) {
        return mode == SUIT_UP ? UP_END : mode == SUIT_OFF ? OFF_END : 0;
    }

    /** Suit up: when the arms start pressing this piece on. */
    public static int attachStart(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 26;
            case LEGS -> 28;
            case CHEST -> 38;
            default -> 48;
        };
    }

    /** Suit up: when this piece is on (the server equips it this tick). */
    public static int attachEnd(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 28;
            case LEGS -> 38;
            case CHEST -> 48;
            default -> 58;
        };
    }

    /** Suit off: when this piece comes off the wearer (top-down). */
    public static int detach(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 14;
            case CHEST -> 20;
            case LEGS -> 26;
            default -> 32;
        };
    }

    /** Where an arm parks a piece it took off, relative to where the piece sits when worn. */
    public static Vec3 held(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> new Vec3(0.0D, 0.8D, 0.0D);
            case CHEST -> new Vec3(0.0D, 0.15D, 0.6D);
            case LEGS -> new Vec3(0.0D, 0.05D, 0.55D);
            default -> Vec3.ZERO;
        };
    }

    /**
     * Where the piece is relative to its worn position, or null while it's on the wearer (the player's own
     * armor layer draws it then).
     */
    public static Vec3 pieceOffset(byte mode, EquipmentSlot slot, float t) {
        Vec3 held = held(slot);
        if (mode == SUIT_UP) {
            if (t >= attachEnd(slot)) {
                return null;
            }
            if (t >= attachStart(slot)) {
                // A machine press: slides in slowly, snaps shut at the end.
                float p = ramp(t, attachStart(slot), attachEnd(slot));
                return held.scale(1.0F - p * p * p);
            }
            return held.scale(ease(ramp(t, UP_REACH, UP_LIFT_END)));
        }
        if (t < detach(slot)) {
            return null;
        }
        if (t >= OFF_REASSEMBLE_START) {
            return held.scale(1.0F - ease(ramp(t, OFF_REASSEMBLE_START, OFF_DOCK)));
        }
        return held.scale(ease(ramp(t, detach(slot), detach(slot) + MOVE)));
    }

    /** 0 = the docked suit's at-ease pose, 1 = arms out like the wearer on the rig (see SuitUpPose). */
    public static float poseBlend(byte mode, float t) {
        return mode == SUIT_UP ? ramp(t, UP_REACH, UP_LIFT_END) : 1.0F - ramp(t, OFF_REASSEMBLE_START, OFF_DOCK);
    }

    /** How far the arm for this slot is out of its parked pose: 0 parked, 1 gripping its piece. */
    public static float armReach(byte mode, EquipmentSlot slot, float t) {
        if (mode == SUIT_UP) {
            int release = attachEnd(slot);
            return t < release ? ease(ramp(t, 0, UP_REACH)) : 1.0F - ease(ramp(t, release, release + MOVE));
        }
        if (mode == SUIT_OFF) {
            return t < OFF_DOCK ? ease(ramp(t, OFF_REACH_START, OFF_WALK_END)) : 1.0F - ease(ramp(t, OFF_DOCK, OFF_DOCK + MOVE));
        }
        return 0.0F;
    }

    public static float ramp(float t, float from, float to) {
        return Mth.clamp((t - from) / (to - from), 0.0F, 1.0F);
    }

    public static float ease(float p) {
        return p < 0.5F ? 2.0F * p * p : 1.0F - (float) Math.pow(-2.0F * p + 2.0F, 2) / 2.0F;
    }
}
