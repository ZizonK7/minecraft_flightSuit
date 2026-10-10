package com.pfkfks.flightsuit.suit;

/** Balance numbers in one place so playtest tuning doesn't mean hunting through handlers. Energy is in FE. */
public final class SuitTuning {
    private SuitTuning() {
    }

    // Storage: the chestplate's arc reactor is the main battery, the other pieces only hold a small buffer.
    public static final int CHEST_CAPACITY = 20_000;
    public static final int PIECE_CAPACITY = 2_000;
    public static final int ENERGY_CELL_CHARGE = 10_000;

    // Per-tick drains.
    public static final int NIGHT_VISION_COST = 1;
    public static final int HOVER_COST = 8;
    public static final int BOOST_COST = 20;
    public static final int THRUST_COST = 12;

    // Companion suits' repulsor blast.
    public static final int REPULSOR_COST = 300;

    // Primary weapon (hold right click): a continuous stream.
    public static final double BEAM_RANGE = 40.0D;
    /** Ticks between damage ticks of a beam (hits bypass the target's hurt cooldown). */
    public static final int BEAM_HIT_INTERVAL = 4;
    public static final int BEAM_COST = 25;
    /** Mark 1 repulsor beam, per hit: 17.5 per second. */
    public static final float BEAM_DAMAGE = 3.5F;
    /** Mark 2 cryo beam: weaker, but slows, then freezes the target stock-still (it can still be hit). */
    public static final float CRYO_BEAM_DAMAGE = 1.5F;
    /** Unbroken cryo-beam exposure before the target freezes solid. */
    public static final int CRYO_FREEZE_TICKS = 30;
    public static final int FREEZE_HOLD_TICKS = 80;

    // Mark 1 micro-missiles (X): a salvo from the shoulder pods, homing on hostiles in front, else the aim point.
    public static final int MISSILE_COUNT = 6;
    public static final int MISSILE_COST = 1200;
    public static final int MISSILE_COOLDOWN_TICKS = 60;

    // Skills added after the M13 test (SuitSkills).
    /** Mark 1 C: the unibeam - charge, then a wide beam through everything in a line. */
    public static final int UNIBEAM_COST = 2_500;
    public static final int UNIBEAM_COOLDOWN_TICKS = 160;
    public static final int UNIBEAM_CHARGE_TICKS = 20;
    public static final float UNIBEAM_DAMAGE = 18.0F;
    public static final double UNIBEAM_RANGE = 32.0D;
    /** Mark 2 C: cryo nova - everything around frozen stock-still. */
    public static final int CRYO_NOVA_COST = 2_000;
    public static final int CRYO_NOVA_COOLDOWN_TICKS = 200;
    public static final double CRYO_NOVA_RADIUS = 7.0D;
    public static final float CRYO_NOVA_DAMAGE = 6.0F;
    public static final int CRYO_NOVA_HOLD_TICKS = 60;
    /** Mark 3 Z: shadow step - the phantom blinks instead of flying. */
    public static final int SHADOW_STEP_COST = 400;
    public static final int SHADOW_STEP_COOLDOWN_TICKS = 30;
    /** M17: 40 blocks (was 20); the cost grows with the distance (SHADOW_STEP_BASE_COST + per block). */
    public static final double SHADOW_STEP_RANGE = 40.0D;
    /** The card swirls turn this long before the phantom steps through. */
    public static final int SHADOW_STEP_CHANNEL_TICKS = 10;
    public static final float MISSILE_POWER = 1.5F;
    public static final double MISSILE_LOCK_RANGE = 48.0D;
    public static final double MISSILE_LOCK_ANGLE_DEG = 25.0D;

    // Mark 3 phantom cards.
    public static final int CARD_INTERVAL = 3;
    public static final int CARD_COST = 40;
    public static final float CARD_DAMAGE = 3.0F;
    public static final float NOIR_DAMAGE = 2.0F;
    public static final float NOIR_CHANCE = 0.35F;
    /** Card hits needed before Judgment Draw (X). */
    public static final int JUDGMENT_GAUGE = 40;
    public static final int SPADE_BUFF_TICKS = 200;
    public static final float SPADE_DAMAGE_MULTIPLIER = 1.5F;
    public static final int DIAMOND_ENERGY = 5_000;
    /** Card duel (C): a hand of blackjack against the aimed monster - win and it dies, lose and you're left at one heart. */
    public static final int DUEL_COST = 3_000;
    public static final int DUEL_COOLDOWN_TICKS = 600;
    public static final double DUEL_RANGE = 24.0D;
    /** No move for this long and you stand on what you have. */
    public static final int DUEL_TIMEOUT_TICKS = 600;
    /** Bosses don't just die: they lose this share of their max health. */
    public static final float DUEL_BOSS_DAMAGE = 0.3F;
    /** Losing also costs every worn suit piece this share of its durability. */
    public static final float DUEL_LOSS_WEAR = 0.3F;

    // Mark 4 hero: Master Sword (hold right click), spin attack (X), clawshot (C), Hylian shield (passive).
    /** Ticks between slashes while the button is held. */
    public static final int SWORD_INTERVAL = 6;
    public static final int SWORD_COST = 30;
    public static final float SWORD_DAMAGE = 6.0F;
    public static final double SWORD_REACH = 4.0D;
    /** Full width of the slash in front of you. */
    public static final double SWORD_ARC_DEG = 110.0D;
    /** At full health every slash also looses a sword beam that flies straight ahead. */
    public static final float SWORD_BEAM_DAMAGE = 5.0F;
    public static final double SWORD_BEAM_SPEED = 1.6D;
    public static final double SWORD_BEAM_RANGE = 32.0D;
    public static final int SPIN_COST = 1_000;
    public static final int SPIN_COOLDOWN_TICKS = 80;
    public static final double SPIN_RADIUS = 4.5D;
    public static final float SPIN_DAMAGE = 10.0F;
    /** Great spin (at full health): wider and harder. */
    public static final double GREAT_SPIN_RADIUS = 7.0D;
    public static final float GREAT_SPIN_DAMAGE = 14.0F;
    public static final int CLAW_COST = 300;
    public static final int CLAW_COOLDOWN_TICKS = 20;
    public static final double CLAW_RANGE = 28.0D;
    /** Blocks per tick: the claw flying out, and you being reeled in. */
    public static final double CLAW_SPEED = 2.5D;
    public static final double CLAW_PULL_SPEED = 1.4D;
    public static final double CLAW_DRAG_SPEED = 1.1D;
    public static final float CLAW_DAMAGE = 2.0F;
    /** Reeled into a big monster: you land a slash on arrival. */
    public static final float CLAW_STRIKE_DAMAGE = 8.0F;
    /** Monsters bigger than this (width x width x height) can't be dragged - the clawshot pulls you to them. */
    public static final double CLAW_DRAG_MAX_VOLUME = 2.0D;
    public static final int CLAW_MAX_REEL_TICKS = 50;
    public static final int HYLIAN_BLOCK_COST = 40;
    /** Half-angle of the front the Hylian shield covers. */
    public static final double HYLIAN_BLOCK_ANGLE_DEG = 70.0D;

    // M17 Hulkbuster Mark 44 (HulkbusterArts): all fists - flurry (hold), piston punch (X), ground slam (C).
    /** Flurry: one punch every this many ticks, left and right in turn. */
    public static final int PUNCH_INTERVAL = 8;
    public static final int PUNCH_COST = 60;
    public static final float PUNCH_DAMAGE = 9.0F;
    public static final double PUNCH_REACH = 3.0D;
    /** Full width of the fan in front of the fists. */
    public static final double PUNCH_ARC_DEG = 90.0D;
    public static final double PUNCH_KNOCKBACK = 1.6D;
    /** Thrown into a wall within this many ticks of a punch: the extra hit. */
    public static final int WALL_SLAM_TICKS = 10;
    public static final float WALL_SLAM_DAMAGE = 6.0F;
    public static final int PISTON_COST = 1_500;
    public static final int PISTON_COOLDOWN_TICKS = 200;
    public static final double PISTON_RANGE = 8.0D;
    public static final int PISTON_HITS = 5;
    public static final int PISTON_HIT_INTERVAL = 4;
    public static final float PISTON_DAMAGE = 7.0F;
    public static final float PISTON_FINAL_DAMAGE = 12.0F;
    public static final int PISTON_STUN_TICKS = 40;
    /** What still reaches the wearer while the pistons pound. */
    public static final float PISTON_DAMAGE_TAKEN = 0.5F;
    public static final int SLAM_COST = 2_000;
    public static final int SLAM_COOLDOWN_TICKS = 160;
    public static final double SLAM_RADIUS = 6.0D;
    public static final float SLAM_DAMAGE = 10.0F;
    public static final double SLAM_LIFT = 0.8D;
    /** Heavy plating: arrows and other small projectiles do half. */
    public static final float HULKBUSTER_PROJECTILE_TAKEN = 0.5F;
    /** Flight speed against a Mark 1. */
    public static final float HULKBUSTER_FLIGHT_FACTOR = 0.7F;
    /** Small mobs (lower than this) it walks into are shoved aside. */
    public static final double HULKBUSTER_SHOVE_HEIGHT = 1.5D;

    // M17 Trunks Mark 5 (SwordArts): a flying swordsman - three-step combo (hold), Burning Attack (X), flash slash (C),
    // Super Saiyan (V).
    public static final int SLASH_INTERVAL = 6;
    public static final int SLASH_COST = 40;
    public static final double SLASH_REACH = 4.0D;
    public static final double THRUST_REACH = 5.0D;
    public static final float SLASH_DAMAGE = 7.0F;
    public static final float THRUST_DAMAGE = 10.0F;
    /** All three steps of one combo on the same foe: it's stunned this long. */
    public static final int COMBO_STUN_TICKS = 20;
    /** The sword goes back on his back this long after the last cut. */
    public static final int SWORD_SHEATHE_TICKS = 20;
    public static final int BURNING_COST = 1_800;
    public static final int BURNING_COOLDOWN_TICKS = 160;
    public static final int BURNING_WINDUP_TICKS = 20;
    public static final double BURNING_SPEED = 1.5D;
    public static final double BURNING_RANGE = 40.0D;
    public static final double BURNING_RADIUS = 3.0D;
    public static final float BURNING_DAMAGE = 16.0F;
    public static final int FLASH_COST = 2_000;
    public static final int FLASH_COOLDOWN_TICKS = 200;
    public static final double FLASH_DISTANCE = 12.0D;
    public static final int FLASH_TICKS = 6;
    public static final double FLASH_WIDTH = 1.5D;
    public static final float FLASH_DAMAGE = 14.0F;
    /** Parried with the sword: the next cut lands this much harder. */
    public static final float PARRY_BONUS = 2.0F;
    public static final int SSJ_COST = 2_000;
    /** 400 FE a second while it lasts. */
    public static final int SSJ_DRAIN = 20;
    public static final float SSJ_MULTIPLIER = 1.5F;
    /** Below this share of the battery it drops by itself, and can't come back for a while. */
    public static final float SSJ_MIN_CHARGE = 0.1F;
    public static final int SSJ_LOCK_TICKS = 100;
    public static final int SSJ_PARRY_WINDOW_TICKS = 10;

    // M17 Phantom Mark 3 (PhantomArts): stealing skills (Z held), using them (B), Tempest (V).
    public static final int STEAL_COST = 600;
    public static final int STEAL_COOLDOWN_TICKS = 100;
    public static final double STEAL_RANGE = 16.0D;
    /** The cards' trip round the target and back. */
    public static final int STEAL_TICKS = 12;
    /** A skill seen this recently can be stolen. */
    public static final int STEAL_MEMORY_TICKS = 160;
    /** Client: how long Z must be held to steal instead of aiming the step. */
    public static final int STEAL_HOLD_TICKS = 10;
    public static final int TEMPEST_COST = 6_000;
    public static final int TEMPEST_COOLDOWN_TICKS = 1_200;
    public static final double TEMPEST_RANGE = 40.0D;
    public static final double TEMPEST_RADIUS = 10.0D;
    public static final int TEMPEST_TICKS = 80;
    public static final int TEMPEST_HIT_INTERVAL = 5;
    public static final float TEMPEST_DAMAGE = 3.0F;
    /** Throwing the card up before the sky turns. */
    public static final int TEMPEST_WINDUP_TICKS = 10;
    /** Shadow step after the M17 rework: farther, onto ledges and into the open air. */
    public static final double SHADOW_STEP_BASE_COST = 100.0D;
    public static final double SHADOW_STEP_COST_PER_BLOCK = 7.5D;
    public static final double SHADOW_STEP_MIN_RANGE = 4.0D;
    /** Stepping into the air: a card to stand on for a moment, then a slow fall. */
    public static final int CARD_PLATFORM_TICKS = 20;
    public static final int CARD_PLATFORM_FALL_TICKS = 40;
    /** Looking at the sky: how far down from the end of the look to search for ground. */
    public static final int SHADOW_STEP_GROUND_SEARCH = 8;

    // Stealth active camouflage (full set, while sneaking).
    public static final int CLOAK_COST = 6;

    // Boots-only thrust: limited climb per airtime, then the boots can only slow the fall.
    public static final int BOOTS_THRUST_TICKS = 30;
    public static final double BOOTS_THRUST_ACCEL = 0.16D;
    public static final double BOOTS_THRUST_MAX_RISE = 0.5D;
    public static final double BOOTS_GLIDE_MAX_FALL = -0.15D;

    // Full-set boost flight (added on top of vanilla creative-style flying).
    public static final double BOOST_ACCEL_HORIZONTAL = 0.12D;
    public static final double BOOST_ACCEL_VERTICAL = 0.45D;
    public static final double BOOST_MAX_SPEED = 1.8D;
    public static final float SUIT_FLYING_SPEED = 0.08F;
    public static final float VANILLA_FLYING_SPEED = 0.05F;

    // Counter key: tap = parry window, hold = nano shield.
    public static final int PARRY_WINDOW_TICKS = 6;
    public static final int PARRY_COOLDOWN_TICKS = 16;
    public static final int PARRY_COST = 100;
    /** Fraction of the hit that still lands on a successful parry. */
    public static final float PARRY_DAMAGE_TAKEN = 0.2F;
    public static final float COUNTER_DAMAGE = 6.0F;
    public static final int COUNTER_STUN_TICKS = 40;
    public static final int SHIELD_DRAIN = 15;
    public static final int SHIELD_HIT_COST = 60;
    /** Client: how long the key must be held before the parry turns into the shield. */
    public static final int SHIELD_HOLD_TICKS = 6;

    // Leggings power assist.
    public static final double LEGS_SPEED_BONUS = 0.2D;
    public static final double LEGS_STEP_BONUS = 0.5D;
    public static final float LEGS_FALL_MULTIPLIER = 0.5F;
}
