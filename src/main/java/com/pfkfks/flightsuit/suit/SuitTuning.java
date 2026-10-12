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
    /** Losing also costs every worn suit piece this share of its durability. */
    public static final float DUEL_LOSS_WEAR = 0.3F;

    // Mark 4 hero: Master Sword (hold right click), spin attack (X), clawshot (C), Hylian shield (passive).
    /** Hero Mode (the hard suit, M18 3rd feedback): every blow that lands on the hero counts double, as in Zelda's Hero Mode. */
    public static final float HERO_DAMAGE_TAKEN = 2.0F;
    /** Ticks between slashes while the button is held. */
    public static final int SWORD_INTERVAL = 6;
    public static final int SWORD_COST = 30;
    public static final float SWORD_DAMAGE = 4.5F;
    public static final double SWORD_REACH = 4.0D;
    /** Full width of the slash in front of you. */
    public static final double SWORD_ARC_DEG = 110.0D;
    /** At full health every slash also looses a sword beam that flies straight ahead. */
    public static final float SWORD_BEAM_DAMAGE = 4.0F;
    public static final double SWORD_BEAM_SPEED = 1.6D;
    public static final double SWORD_BEAM_RANGE = 32.0D;
    public static final int SPIN_COST = 1_000;
    public static final int SPIN_COOLDOWN_TICKS = 40;
    public static final double SPIN_RADIUS = 4.5D;
    public static final float SPIN_DAMAGE = 7.0F;
    /** Great spin (at full health): wider and harder. */
    public static final double GREAT_SPIN_RADIUS = 7.0D;
    public static final float GREAT_SPIN_DAMAGE = 10.0F;
    public static final int CLAW_COST = 300;
    public static final int CLAW_COOLDOWN_TICKS = 20;
    public static final double CLAW_RANGE = 28.0D;
    /** Blocks per tick: the claw flying out, and you being reeled in. */
    public static final double CLAW_SPEED = 2.5D;
    public static final double CLAW_PULL_SPEED = 1.4D;
    public static final double CLAW_DRAG_SPEED = 1.1D;
    public static final float CLAW_DAMAGE = 2.0F;
    /** Reeled into a big monster: you land a slash on arrival. */
    public static final float CLAW_STRIKE_DAMAGE = 6.0F;
    /** Monsters bigger than this (width x width x height) can't be dragged - the clawshot pulls you to them. */
    public static final double CLAW_DRAG_MAX_VOLUME = 2.0D;
    public static final int CLAW_MAX_REEL_TICKS = 50;
    public static final int HYLIAN_BLOCK_COST = 40;
    /** M18 feedback: Z is the shield, right click held charges the spin - this long for a spin, this long for the great one (X spins at once). */
    public static final int SPIN_CHARGE_TICKS = 10;
    public static final int GREAT_SPIN_CHARGE_TICKS = 30;
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

    // ---------------------------------------------------------------- M18: the full line-up (DESIGN.md 5차 피드백)

    // Scrap Mark 0 (ScrapArts): a coal boiler for a battery, the flamethrower (right click held - M18 feedback: no beam
    // on a scrap suit), the scrap shot (X), a rocket jump (C), overdrive (V), a flare (B).
    public static final int SCRAP_CHEST_CAPACITY = 8_000;
    /** FE a lump of coal (or charcoal) is worth in the boiler; a coal block is nine. */
    public static final int SCRAP_COAL_ENERGY = 2_000;
    public static final float SCRAP_FLIGHT_FACTOR = 0.55F;
    /** The flamethrower: FE a tick while it burns, a hit on everything in the cone every FLAME_HIT_INTERVAL ticks. */
    public static final int FLAME_DRAIN = 25;
    public static final int FLAME_HIT_INTERVAL = 4;
    public static final double FLAME_REACH = 5.5D;
    public static final double FLAME_ARC_DEG = 40.0D;
    public static final float FLAME_DAMAGE = 2.0F;
    public static final int FLAME_BURN_SECONDS = 4;
    /** Each hit of the flame: the chance fire catches where it lands (M18 feedback: it has to burn). */
    public static final float FLAME_IGNITE_CHANCE = 0.4F;
    /** The scrap shot (X): a fan of hot iron scraps, each pellet a hit of its own, close up. */
    public static final int SCRAP_SHOT_COST = 500;
    public static final int SCRAP_SHOT_COOLDOWN_TICKS = 50;
    public static final int SCRAP_SHOT_PELLETS = 7;
    public static final double SCRAP_SHOT_RANGE = 9.0D;
    public static final double SCRAP_SHOT_SPREAD = 0.11D;
    public static final float SCRAP_SHOT_DAMAGE = 2.5F;
    public static final int ROCKET_JUMP_COST = 500;
    public static final int ROCKET_JUMP_COOLDOWN_TICKS = 80;
    public static final double ROCKET_JUMP_UP = 1.1D;
    public static final double ROCKET_JUMP_FORWARD = 0.45D;
    public static final double ROCKET_LAND_RADIUS = 3.0D;
    public static final float ROCKET_LAND_DAMAGE = 4.0F;
    public static final int OVERDRIVE_COST = 4_000;
    public static final int OVERDRIVE_TICKS = 160;
    public static final int OVERDRIVE_COOLDOWN_TICKS = 600;
    public static final float OVERDRIVE_FLIGHT = 1.5F;
    public static final float OVERDRIVE_DAMAGE = 2.0F;
    public static final int FLARE_COST = 300;
    public static final int FLARE_COOLDOWN_TICKS = 600;
    public static final int FLARE_TICKS = 1_200;
    public static final double FLARE_RADIUS = 24.0D;
    public static final int FLARE_HEIGHT = 12;

    // Mark 1 (MarkArts): the full burst (V) and the EDITH scan (B).
    public static final int FULL_BURST_COST = 5_000;
    public static final int FULL_BURST_COOLDOWN_TICKS = 900;
    public static final double FULL_BURST_RANGE = 24.0D;
    public static final int FULL_BURST_TARGETS = 12;
    /** Ticks between the repulsor shots at the next target. */
    public static final int FULL_BURST_INTERVAL = 3;
    public static final float FULL_BURST_DAMAGE = 9.0F;
    /** The shoulder guns (B): for SHOULDER_TICKS, a shot every SHOULDER_INTERVAL at the nearest foe in the cone ahead. */
    public static final int SHOULDER_COST = 900;
    public static final int SHOULDER_COOLDOWN_TICKS = 300;
    public static final int SHOULDER_TICKS = 60;
    public static final int SHOULDER_INTERVAL = 3;
    public static final double SHOULDER_RANGE = 20.0D;
    public static final double SHOULDER_ARC_DEG = 80.0D;
    public static final float SHOULDER_DAMAGE = 2.5F;

    // Mark 2 (MarkArts): the mining laser (X), x-ray (V), the light orb (B).
    public static final int DRILL_COST_PER_BLOCK = 40;
    public static final int DRILL_COOLDOWN_TICKS = 30;
    public static final int DRILL_LENGTH = 6;
    /** Blocks harder than this (stone 1.5, obsidian 50) the drill leaves alone. */
    public static final float DRILL_MAX_HARDNESS = 30.0F;
    public static final int XRAY_COST = 2_500;
    public static final int XRAY_COOLDOWN_TICKS = 600;
    public static final int XRAY_TICKS = 300;
    public static final int XRAY_RADIUS = 32;
    /**
     * The flash (2026-10-12: no longer thrown - it goes off where the emergency escape takes off): everything with
     * eyes on it is blinded and loses you for a while.
     */
    public static final double FLASHBANG_RADIUS = 7.0D;
    public static final int FLASHBANG_BLIND_TICKS = 100;
    public static final int FLASHBANG_STUN_TICKS = 20;
    /**
     * Mark 2 emergency escape (B, 2026-10-12, EscapeFlight): a flash, then the suit flies the wearer home - to the
     * suit's station, or on a planet to the landed ship. Speeds are blocks per tick (the client flies it).
     */
    public static final int ESCAPE_COST = 3_000;
    public static final int ESCAPE_COOLDOWN_TICKS = 1_800;
    /** Pressed again, or cut short (blocked, out of the suit): only this long before it can be tried again. */
    public static final int ESCAPE_FAILED_COOLDOWN_TICKS = 100;
    public static final double ESCAPE_CLIMB_SPEED = 1.0D;
    /** Straight up through rock (the laser cutting the way ahead). */
    public static final double ESCAPE_DRILL_SPEED = 0.6D;
    public static final double ESCAPE_CRUISE_SPEED = 2.0D;
    /** Cruising height: this far above the higher of the take-off and landing ground, at least the floor. */
    public static final int ESCAPE_CRUISE_ABOVE = 40;
    public static final int ESCAPE_CRUISE_FLOOR = 120;
    /** Blocks the laser clears above the head while climbing (ahead of the client, which is a few ticks ahead). */
    public static final int ESCAPE_DRILL_AHEAD = 7;
    /** Farther than this at cruising height: the suit jumps ahead to ESCAPE_JUMP_TO short of home (sonic boom). */
    public static final double ESCAPE_JUMP_OVER = 1_600.0D;
    public static final double ESCAPE_JUMP_TO = 600.0D;
    /** Within this (horizontally) of home: the descent. */
    public static final double ESCAPE_DESCENT_RADIUS = 8.0D;
    /** No closer for this long: the last stretch (a roof over the station, a cliff in the way) is done in one go. */
    public static final int ESCAPE_STUCK_TICKS = 40;
    public static final int ESCAPE_MAX_TICKS = 1_200;

    // Mark 4 (HeroArts, M18): shield raised (right click held), the Triforce (V), the gale boomerang (B). After the
    // first test ("어렵고 쎄야 하는데 그냥 쎈데"): the chain - every move that differs from the last within CHAIN_WINDOW
    // adds a link, each link CHAIN_STEP more damage; a hit taken breaks it - and the perfect guard.
    public static final int HYLIAN_HOLD_DRAIN = 4;
    public static final int CHAIN_MAX = 5;
    public static final float CHAIN_STEP = 0.3F;
    public static final int CHAIN_WINDOW_TICKS = 40;
    /** The shield raised within this many ticks of the blow: a perfect guard (free, stuns, two links). */
    public static final int PERFECT_GUARD_TICKS = 6;
    public static final int PERFECT_GUARD_STUN_TICKS = 25;
    /** The shield raised but late: this share of a melee blow still lands. */
    public static final float HYLIAN_LATE_TAKEN = 0.5F;
    public static final int TRIFORCE_COST = 4_000;
    public static final int TRIFORCE_TICKS = 200;
    public static final int TRIFORCE_COOLDOWN_TICKS = 1_200;
    public static final float TRIFORCE_MULTIPLIER = 1.5F;
    public static final int BOOMERANG_COST = 700;
    public static final int BOOMERANG_COOLDOWN_TICKS = 100;
    public static final double BOOMERANG_RANGE = 16.0D;
    public static final double BOOMERANG_SPEED = 1.2D;
    public static final float BOOMERANG_DAMAGE = 4.0F;
    public static final int BOOMERANG_STUN_TICKS = 25;

    // Mark 5 (SwordArts, M18): the charged ki slash (right click held) and the ki volley (B).
    public static final int CHARGE_SLASH_TICKS = 16;
    public static final int CHARGE_SLASH_COST = 1_500;
    public static final float CHARGE_SLASH_MULTIPLIER = 2.5F;
    public static final float CHARGE_WAVE_DAMAGE = 12.0F;
    public static final double CHARGE_WAVE_RANGE = 24.0D;
    /** The Dragon Fist (B, M18 feedback in place of the ki volley): RISE ticks of powering up to Super Saiyan 3, then
     * DASH ticks flying forward at SPEED in a golden dragon, everything within RADIUS of the way taking DAMAGE once. */
    public static final int DRAGON_FIST_COST = 4_000;
    public static final int DRAGON_FIST_COOLDOWN_TICKS = 1_200;
    public static final int DRAGON_FIST_RISE_TICKS = 50;
    public static final int DRAGON_FIST_DASH_TICKS = 22;
    public static final int DRAGON_FIST_TOTAL_TICKS = 100;
    public static final double DRAGON_FIST_SPEED = 1.3D;
    public static final double DRAGON_FIST_RADIUS = 3.0D;
    public static final float DRAGON_FIST_DAMAGE = 36.0F;
    public static final float DRAGON_FIST_BOSS_MULTIPLIER = 1.5F;

    // Mark 44 (HulkbusterArts, M18): armour mode (Z held), the jackhammer (V), block throwing (B).
    public static final int ARMOUR_MODE_DRAIN = 30;
    public static final float ARMOUR_MODE_TAKEN = 0.3F;
    public static final double ARMOUR_MODE_WAVE_RADIUS = 5.0D;
    public static final float ARMOUR_MODE_WAVE_DAMAGE = 8.0F;
    public static final int JACKHAMMER_COST = 5_000;
    public static final int JACKHAMMER_COOLDOWN_TICKS = 900;
    public static final double JACKHAMMER_RANGE = 6.0D;
    public static final int JACKHAMMER_HITS = 10;
    public static final int JACKHAMMER_INTERVAL = 3;
    public static final float JACKHAMMER_DAMAGE = 9.0F;
    public static final float JACKHAMMER_BOSS_MULTIPLIER = 1.5F;
    public static final int THROW_COST = 1_200;
    public static final int THROW_COOLDOWN_TICKS = 240;
    public static final double THROW_RANGE = 6.0D;
    public static final double THROW_SPEED = 1.3D;
    public static final float THROW_DAMAGE = 6.0F;
    /** How long the ripped-up chunk floats over the shoulder before it's thrown on its own. */
    public static final int THROW_HOLD_TICKS = 200;

    // Mark 6 Cannon Shooter (CannonArts): cannonballs (right click), recoil jump (Z), monkey militia (X), the rolling
    // cannon (C), the big huge gigantic cannonball (V), the monkey rush (B).
    public static final int CANNON_INTERVAL = 12;
    public static final int CANNON_COST = 250;
    public static final double CANNON_SPEED = 1.6D;
    public static final double CANNON_RADIUS = 3.0D;
    public static final float CANNON_DAMAGE = 8.0F;
    public static final double CANNON_RECOIL = 0.25D;
    /**
     * Where a ball bursts, blocks within this radius and up to this hardness go (the giant one: twice the radius, harder).
     * M18 3rd feedback ("더 시원시원"): wider, and cobblestone, deepslate and ores go too (hardness 3).
     */
    public static final double CANNON_BREAK_RADIUS = 2.3D;
    public static final float CANNON_BREAK_HARDNESS = 3.0F;
    public static final float BHGC_BREAK_HARDNESS = 6.0F;
    public static final int RECOIL_JUMP_COST = 500;
    public static final int RECOIL_JUMP_COOLDOWN_TICKS = 40;
    public static final double RECOIL_JUMP_POWER = 1.1D;
    /**
     * 2026-10-12 Mark 6 on the move (MapleStory's Cannon Jump + Monkey Boost; the user: no flight, more dynamic):
     * cannon jumps in a row before landing (the cooldown only counts from the ground) and the chained one's power;
     * the monkey's kick at the top of a cannon jump. The leap is straight away from the aim (the user: "the opposite
     * of where you aim" - intuitive, and hard to steer on purpose).
     */
    public static final int CANNON_JUMP_CHAIN = 2;
    public static final double CANNON_JUMP_CHAIN_POWER = 0.85D;
    public static final double MONKEY_KICK_FORWARD = 0.85D;
    public static final double MONKEY_KICK_UP = 0.3D;
    /**
     * The landing slam (the user: falling is just falling - Z pressed as you touch down makes the shockwave and
     * throws you off again, away from the aim like a cannon jump): from
     * at least MIN_FALL blocks, Z within WINDOW blocks of the ground on the way down or LATE ticks after landing.
     * Radius and damage grow with the fall, up to the caps.
     */
    public static final double CANNON_SLAM_MIN_FALL = 2.0D;
    public static final double CANNON_SLAM_WINDOW = 2.5D;
    public static final int CANNON_SLAM_LATE_TICKS = 4;
    public static final int CANNON_SLAM_COST = 400;
    public static final double CANNON_SLAM_RADIUS = 3.0D;
    public static final double CANNON_SLAM_RADIUS_PER_BLOCK = 0.2D;
    public static final double CANNON_SLAM_RADIUS_MAX = 7.0D;
    public static final float CANNON_SLAM_DAMAGE = 6.0F;
    public static final float CANNON_SLAM_DAMAGE_PER_BLOCK = 0.8F;
    public static final float CANNON_SLAM_DAMAGE_MAX = 22.0F;
    public static final int MILITIA_COST = 1_500;
    public static final int MILITIA_COOLDOWN_TICKS = 400;
    public static final int MILITIA_COUNT = 3;
    public static final int MILITIA_TICKS = 200;
    public static final int MILITIA_INTERVAL = 15;
    public static final float MILITIA_DAMAGE = 3.0F;
    public static final double MILITIA_RANGE = 16.0D;
    public static final int ROLLING_COST = 1_200;
    public static final int ROLLING_COOLDOWN_TICKS = 160;
    public static final int ROLLING_SHOTS = 3;
    public static final double ROLLING_SPREAD_DEG = 15.0D;
    public static final int BHGC_COST = 6_000;
    public static final int BHGC_COOLDOWN_TICKS = 1_200;
    public static final double BHGC_SPEED = 0.6D;
    public static final double BHGC_RANGE = 32.0D;
    public static final double BHGC_RADIUS = 4.0D;
    public static final float BHGC_DAMAGE = 20.0F;
    public static final double BHGC_BLAST_RADIUS = 8.0D;
    public static final float BHGC_BLAST_DAMAGE = 24.0F;
    public static final int RUSH_COST = 1_000;
    public static final int RUSH_COOLDOWN_TICKS = 240;
    public static final int RUSH_TICKS = 12;
    public static final double RUSH_WIDTH = 1.6D;
    public static final float RUSH_DAMAGE = 6.0F;

    // Mark 7 Bishop (BishopArts): heal beam / holy arrow (right click), Holy Magic Shell (Z), Angel Ray (X), chains
    // of light (C), Genesis (V), Heaven's Door (B).
    public static final int HOLY_COST = 30;
    public static final float HEAL_PER_HIT = 2.0F;
    public static final float HOLY_ARROW_DAMAGE = 3.0F;
    public static final int SHELL_COST = 600;
    public static final int SHELL_COOLDOWN_TICKS = 120;
    public static final int SHELL_TICKS = 40;
    /** Within this many ticks of raising the shell a blocked shot goes back where it came from. */
    public static final int SHELL_REFLECT_TICKS = 8;
    /** Z then a click (2026-10-12): how long the picking lasts, how far an ally can be, how long their shell holds. */
    public static final int SHELL_AIM_TICKS = 60;
    public static final double SHELL_AIM_RANGE = 24.0D;
    public static final int SHELL_ALLY_TICKS = 60;
    public static final int ANGEL_RAY_COST = 1_200;
    public static final int ANGEL_RAY_COOLDOWN_TICKS = 100;
    public static final double ANGEL_RAY_RANGE = 24.0D;
    public static final double ANGEL_RAY_WIDTH = 1.2D;
    public static final float ANGEL_RAY_DAMAGE = 10.0F;
    public static final float ANGEL_RAY_HEAL = 6.0F;
    public static final int CHAINS_COST = 1_500;
    public static final int CHAINS_COOLDOWN_TICKS = 240;
    public static final double CHAINS_RANGE = 20.0D;
    public static final int CHAINS_TICKS = 60;
    public static final int CHAINS_BOSS_TICKS = 20;
    public static final int GENESIS_COST = 7_000;
    public static final int GENESIS_COOLDOWN_TICKS = 1_800;
    public static final double GENESIS_RADIUS = 16.0D;
    public static final int GENESIS_PILLARS = 10;
    public static final int GENESIS_INTERVAL = 4;
    public static final float GENESIS_DAMAGE = 14.0F;
    public static final int DOOR_COST = 2_500;
    public static final int DOOR_COOLDOWN_TICKS = 800;
    public static final double DOOR_RADIUS = 12.0D;
    public static final int DOOR_TICKS = 300;
    /** The health it hands back: the sample from this many ticks before the killing blow. */
    public static final int DOOR_REWIND_TICKS = 60;

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
