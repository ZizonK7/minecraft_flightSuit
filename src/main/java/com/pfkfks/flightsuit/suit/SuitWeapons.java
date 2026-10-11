package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.CardEntity;
import com.pfkfks.flightsuit.entity.MissileEntity;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.BeamStateS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitAnimS2CPacket;
import com.pfkfks.flightsuit.network.WeaponStatusS2CPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a suit fires, by class (M8 weapons, the full six-key line-up since M18 - DESIGN.md 5차 피드백 역할표):
 * - primary, held right click: a continuous stream from an empty hand (Mark 0/1 repulsor, Mark 2 cryo beam, Mark 3
 *   cards, Mark 44 fists) or the weapon's stance from the weapon in hand (Mark 4 shield up, Mark 5 charging a ki
 *   slash, Mark 6 cannon fire, Mark 7 heal beam / holy arrows);
 * - left click with a weapon in hand (SWING): Mark 4 and Mark 5 cut;
 * - X / C: skills 1 and 2, V the ultimate, B the third skill (the phantom's stolen skill) - one each per class, see
 *   ScrapArts, MarkArts, PhantomCards/PhantomArts, HeroArts, HulkbusterArts, SwordArts, CannonArts, BishopArts.
 * The client only says when the trigger goes down and up and which key was pressed; energy, cooldowns, aiming and
 * damage all live here and in the arts.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class SuitWeapons {
    public static final byte PRIMARY_START = 0;
    public static final byte PRIMARY_STOP = 1;
    public static final byte SKILL_1 = 2;
    public static final byte SKILL_2 = 3;
    /** M17: the ultimate key (V). */
    public static final byte ULTIMATE = 4;
    /** M17 the phantom's stolen skill (B); M18 every suit's third skill. */
    public static final byte SKILL_3 = 5;
    /** M17: the phantom stealing (Z held, PhantomAimClient). */
    public static final byte STEAL = 6;
    /** M18: a left click with the suit's weapon in hand (WeaponInput) - the sword suits cut. */
    public static final byte SWING = 7;

    /** What a player's primary is firing (synced for the beam visual and the aimed arm). */
    public static final byte FIRE_NONE = 0;
    public static final byte FIRE_REPULSOR = 1;
    public static final byte FIRE_CRYO = 2;
    public static final byte FIRE_CARDS = 3;
    /** M17: the Hulkbuster's flurry of punches. */
    public static final byte FIRE_PUNCH = 5;
    /** M18 Mark 4: the Hylian shield raised (right click held with the Master Sword in hand). */
    public static final byte FIRE_SHIELD = 7;
    /** M18 Mark 5: charging a ki slash. */
    public static final byte FIRE_CHARGE = 8;
    /** M18 Mark 7: the staff's beam - healing an ally or shooting holy arrows at a foe. */
    public static final byte FIRE_HOLY = 9;
    /** M18 Mark 6: the cannon firing. */
    public static final byte FIRE_CANNON = 10;

    /** Per-player weapon state (package-private: the arts keep their gauges and cooldowns here). */
    static final class State {
        byte firing = FIRE_NONE;
        int firingTicks;
        long skill1Ready;
        long skill2Ready;
        /** M18: the third skill's (B) cooldown. */
        long skill3Ready;
        // Mark 2: unbroken exposure of one target to the cryo beam.
        LivingEntity frostTarget;
        int frostTicks;
        // Mark 1: missiles still in the pods this salvo.
        final Deque<LivingEntity> salvo = new ArrayDeque<>();
        Vec3 salvoAim = Vec3.ZERO;
        int salvoFired;
        // Mark 3.
        int gauge;
        long spadeUntil;
        // Mark 4 (HeroArts).
        int swings;
        long lastSwing;
        final List<HeroArts.SwordBeam> swordBeams = new ArrayList<>();
        int spinTicks;
        double spinRadius;
        byte hookPhase;
        Vec3 hookPos = Vec3.ZERO;
        Vec3 hookDir = Vec3.ZERO;
        double hookTravelled;
        LivingEntity hookMob;
        int hookTicks;
        /** M18: the Triforce's power until this tick; the gale boomerang in flight. */
        long triforceUntil;
        final List<HeroArts.Boomerang> boomerangs = new ArrayList<>();
        // Mark 1 unibeam charging; Mark 3 shadow step (SuitSkills): cooldown, and the step under way.
        int unibeamCharge;
        long stepReady;
        int stepTicks;
        Vec3 stepFrom = Vec3.ZERO;
        Vec3 stepTo = Vec3.ZERO;
        boolean stepAir;
        float stepYaw;
        /** Mark 3 stepping into the open air: standing on a card until this tick, then a slow fall. */
        long cardPlatformUntil;
        Vec3 cardPlatformAt = Vec3.ZERO;
        // M17 ultimate (V): its cooldown.
        long ultReady;
        // M17 Mark 44 (HulkbusterArts).
        int punches;
        /** Punched foes still flying back, until when a wall counts. */
        final Map<LivingEntity, Long> wallWatch = new java.util.WeakHashMap<>();
        LivingEntity pistonTarget;
        int pistonHits;
        int pistonTicks;
        byte slamPhase;
        int slamTicks;
        /** M18 Mark 44: armour mode (Z held); the jackhammer's victim and count. */
        boolean armourMode;
        LivingEntity hammerTarget;
        int hammerHits;
        int hammerTicks;
        // M17 Mark 5 (SwordArts).
        int comboStep;
        long lastCut;
        final java.util.Set<LivingEntity> comboHit = new java.util.HashSet<>();
        boolean parryBonus;
        int burningWindup;
        int flashTicks;
        Vec3 flashDir = Vec3.ZERO;
        Vec3 flashFrom = Vec3.ZERO;
        Vec3 flashLast = Vec3.ZERO;
        final java.util.Set<LivingEntity> flashHit = new java.util.HashSet<>();
        boolean superSaiyan;
        long ssjLockedUntil;
        /** M18 Mark 5: the ki slash charging (right click held); the ki volley's shots left. */
        int chargeTicks;
        int volleyLeft;
        // M17 Mark 3 (PhantomArts): stealing, and the stolen skill's cooldown and charge.
        long stealReady;
        int stealTicks;
        LivingEntity stealTarget;
        long stolenReady;
        StolenSkill chargingSkill;
        int skillCharge;
        // M18 Mark 0 (ScrapArts): the flamethrower burning, the rocket jump waiting to land, overdrive until.
        int flameTicks;
        boolean rocketArmed;
        long overdriveUntil;
        // M18 Mark 1 (MarkArts): the full burst's targets still to shoot.
        final Deque<LivingEntity> burst = new ArrayDeque<>();
        int burstTicks;
        /** M18 Mark 2: x-ray mode until this tick. */
        long xrayUntil;
        // M18 Mark 6 (CannonArts): the monkey rush sweeping forward.
        int rushTicks;
        Vec3 rushPos = Vec3.ZERO;
        Vec3 rushDir = Vec3.ZERO;
        final java.util.Set<LivingEntity> rushHit = new java.util.HashSet<>();
        // M18 Mark 7 (BishopArts): the Holy Magic Shell's window, Genesis falling, Heaven's Door on allies until.
        long shellUntil;
        long shellRaised;
        int genesisLeft;
        int genesisTicks;
        long doorUntil;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private SuitWeapons() {
    }

    static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    /** The player's weapon state if they have one yet (null otherwise - never creates it). */
    static State existing(ServerPlayer player) {
        return STATES.get(player.getUUID());
    }

    static void cooldownMessage(ServerPlayer player, long ticksLeft) {
        player.displayClientMessage(Component.translatable("message.flightsuit.skill_cooldown",
                String.format("%.1f", Math.max(0L, ticksLeft) / 20.0F)), true);
    }

    /** The class of the suit whose chestplate (arms, reactor) the player wears, or null. */
    public static SuitClass armedClass(LivingEntity wearer) {
        return wearer.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitArmorItem armor ? armor.getSuitType().suitClass() : null;
    }

    /** Never hit by the wearer's own weapons: themselves, their companion suits, their body, their projectiles. */
    public static boolean isFriendly(ServerPlayer owner, Entity entity) {
        return entity == owner
                || entity instanceof SuitCompanionEntity companion && companion.isOwnedBy(owner)
                || entity instanceof RemoteBodyEntity body && owner.getUUID().equals(body.getOwnerId())
                || entity instanceof CardEntity || entity instanceof MissileEntity
                || entity instanceof com.pfkfks.flightsuit.entity.MonkeyEntity monkey && monkey.isOwnedBy(owner);
    }

    /** Beams (repulsor, cryo, the bishop's staff) hum while they fire and are drawn as a ribbon by the clients. */
    public static boolean isBeam(byte kind) {
        return kind == FIRE_REPULSOR || kind == FIRE_CRYO || kind == FIRE_HOLY;
    }

    /** Firing that holds the right arm out aimed (beams, cards, the staff, the cannon) - not the fists. */
    public static boolean aimsArm(byte kind) {
        return kind == FIRE_REPULSOR || kind == FIRE_CRYO || kind == FIRE_CARDS || kind == FIRE_HOLY || kind == FIRE_CANNON;
    }

    /** The primary's energy and damage multiplier while the scrap suit's overdrive is on. */
    static float overdrive(State state, ServerPlayer player) {
        return state.overdriveUntil > player.level().getGameTime() ? SuitTuning.OVERDRIVE_DAMAGE : 1.0F;
    }

    // ---------------------------------------------------------------- input

    public static void handle(ServerPlayer player, byte action) {
        State state = state(player);
        if (action == PRIMARY_STOP) {
            stop(player, state);
            return;
        }
        SuitClass suitClass = armedClass(player);
        if (suitClass == null || SuitUpManager.isSuitingUp(player) || !player.isAlive()) {
            return;
        }
        switch (action) {
            case PRIMARY_START -> start(player, state, suitClass);
            case SWING -> {
                if (!SuitWeaponItem.holding(player)) {
                    return;
                }
                if (suitClass == SuitClass.HERO) {
                    HeroArts.swing(player, state);
                } else if (suitClass == SuitClass.SWORDSMAN) {
                    SwordArts.swing(player, state);
                }
            }
            case SKILL_1 -> {
                switch (suitClass) {
                    case SCRAP -> ScrapArts.flamethrower(player, state);
                    case STANDARD -> fireMissiles(player, state);
                    case STEALTH -> MarkArts.drill(player, state);
                    case PHANTOM -> PhantomCards.judgmentDraw(player, state);
                    case HERO -> HeroArts.spinAttack(player, state);
                    case HULKBUSTER -> HulkbusterArts.pistonPunch(player, state);
                    case SWORDSMAN -> SwordArts.burningAttack(player, state);
                    case CANNONEER -> CannonArts.militia(player, state);
                    case BISHOP -> BishopArts.angelRay(player, state);
                }
            }
            case SKILL_2 -> {
                switch (suitClass) {
                    case SCRAP -> ScrapArts.rocketJump(player, state);
                    case STANDARD -> SuitSkills.startUnibeam(player, state);
                    case STEALTH -> SuitSkills.cryoNova(player, state);
                    case PHANTOM -> CardDuel.challenge(player, state);
                    case HERO -> HeroArts.clawshot(player, state);
                    case HULKBUSTER -> HulkbusterArts.groundSlam(player, state);
                    case SWORDSMAN -> SwordArts.flashSlash(player, state);
                    case CANNONEER -> CannonArts.rollingCannon(player, state);
                    case BISHOP -> BishopArts.chains(player, state);
                }
            }
            case ULTIMATE -> {
                switch (suitClass) {
                    case SCRAP -> ScrapArts.overdrive(player, state);
                    case STANDARD -> MarkArts.fullBurst(player, state);
                    case STEALTH -> MarkArts.xray(player, state);
                    case PHANTOM -> PhantomArts.tempest(player, state);
                    case HERO -> HeroArts.triforce(player, state);
                    case HULKBUSTER -> HulkbusterArts.jackhammer(player, state);
                    case SWORDSMAN -> SwordArts.toggleSuperSaiyan(player, state);
                    case CANNONEER -> CannonArts.giantCannonball(player, state);
                    case BISHOP -> BishopArts.genesis(player, state);
                }
            }
            case SKILL_3 -> {
                switch (suitClass) {
                    case SCRAP -> ScrapArts.flare(player, state);
                    case STANDARD -> MarkArts.scan(player, state);
                    case STEALTH -> MarkArts.lightOrb(player, state);
                    case PHANTOM -> PhantomArts.useStolen(player, state);
                    case HERO -> HeroArts.boomerang(player, state);
                    case HULKBUSTER -> HulkbusterArts.throwBlocks(player, state);
                    case SWORDSMAN -> SwordArts.volley(player, state);
                    case CANNONEER -> CannonArts.monkeyRush(player, state);
                    case BISHOP -> BishopArts.heavensDoor(player, state);
                }
            }
            case STEAL -> {
                if (suitClass == SuitClass.PHANTOM) {
                    PhantomArts.steal(player, state);
                }
            }
            default -> {
            }
        }
    }

    private static void start(ServerPlayer player, State state, SuitClass suitClass) {
        if (state.firing != FIRE_NONE) {
            return;
        }
        // The weapon suits fire from the weapon, the others from an empty hand (the client checks too).
        if (!suitClass.handsFree() && !SuitWeaponItem.holding(player)) {
            return;
        }
        if (SuitEnergy.available(player, EquipmentSlot.CHEST) <= 0 && !player.getAbilities().instabuild) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.firing = switch (suitClass) {
            case STEALTH -> FIRE_CRYO;
            case PHANTOM -> FIRE_CARDS;
            case HERO -> FIRE_SHIELD;
            case HULKBUSTER -> FIRE_PUNCH;
            case SWORDSMAN -> FIRE_CHARGE;
            case CANNONEER -> FIRE_CANNON;
            case BISHOP -> FIRE_HOLY;
            default -> FIRE_REPULSOR;
        };
        state.firingTicks = 0;
        state.frostTarget = null;
        state.frostTicks = 0;
        state.chargeTicks = 0;
        ModNetwork.sendToTrackingAndSelf(player, new BeamStateS2CPacket(player.getId(), state.firing));
        if (isBeam(state.firing)) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F,
                    state.firing == FIRE_CRYO ? 1.9F : state.firing == FIRE_HOLY ? 1.4F : 1.6F);
        } else if (state.firing == FIRE_SHIELD) {
            HeroArts.raiseShield(player);
        } else if (state.firing == FIRE_CHARGE) {
            SwordArts.startCharge(player, state);
        }
    }

    private static void stop(ServerPlayer player, State state) {
        if (state.firing == FIRE_NONE) {
            return;
        }
        byte was = state.firing;
        state.firing = FIRE_NONE;
        state.frostTarget = null;
        ModNetwork.sendToTrackingAndSelf(player, new BeamStateS2CPacket(player.getId(), FIRE_NONE));
        if (isBeam(was)) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4F, 1.8F);
        } else if (was == FIRE_SHIELD) {
            HeroArts.lowerShield(player);
        } else if (was == FIRE_CHARGE) {
            SwordArts.releaseCharge(player, state);
        }
    }

    // ---------------------------------------------------------------- ticking

    public static void tick(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null) {
            return;
        }
        SuitClass suitClass = armedClass(player);
        if (state.firing != FIRE_NONE) {
            if (suitClass == null || SuitUpManager.isSuitingUp(player) || !player.isAlive()
                    || !suitClass.handsFree() && !SuitWeaponItem.holding(player)) {
                stop(player, state);
            } else {
                state.firingTicks++;
                switch (state.firing) {
                    case FIRE_CARDS -> PhantomCards.tickStream(player, state);
                    case FIRE_PUNCH -> HulkbusterArts.tickFlurry(player, state);
                    case FIRE_SHIELD -> HeroArts.tickShield(player, state);
                    case FIRE_CHARGE -> SwordArts.tickCharge(player, state);
                    case FIRE_HOLY -> BishopArts.tickHoly(player, state);
                    case FIRE_CANNON -> CannonArts.tickCannon(player, state);
                    default -> tickBeam(player, state, state.firing == FIRE_CRYO);
                }
            }
        }
        tickSalvo(player, state, suitClass);
        SuitSkills.tickShadowStep(player, state);
        SuitSkills.tickCardPlatform(player, state);
        if (suitClass == SuitClass.STANDARD) {
            SuitSkills.tickUnibeam(player, state);
        } else {
            state.unibeamCharge = 0;
        }
        HeroArts.tick(player, state, suitClass);
        HulkbusterArts.tick(player, state, suitClass);
        SwordArts.tick(player, state, suitClass);
        PhantomArts.tick(player, state, suitClass);
        ScrapArts.tick(player, state, suitClass);
        MarkArts.tick(player, state, suitClass);
        CannonArts.tick(player, state, suitClass);
        BishopArts.tick(player, state, suitClass);
        CardDuel.tick(player);
    }

    public record Aim(Vec3 end, LivingEntity target) {
    }

    /** Where the wearer's crosshair lands (blocks and living entities, own side excluded). */
    public static Aim aim(ServerPlayer player, double range) {
        return aim(player, range, false);
    }

    /**
     * Where the wearer's crosshair lands.
     * @param allies true to land on the wearer's own side too (the bishop heals whoever it looks at)
     */
    public static Aim aim(ServerPlayer player, double range, boolean allies) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(player.getLookAngle().scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0D),
                entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator() && entity.isPickable()
                        && entity != player && (allies || !isFriendly(player, entity)));
        return hit == null ? new Aim(end, null) : new Aim(hit.getLocation(), (LivingEntity) hit.getEntity());
    }

    /** Mark 0 / 1 / 2 beam: drains every tick, hits every few ticks (straight through the hurt cooldown). */
    private static void tickBeam(ServerPlayer player, State state, boolean cryo) {
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.BEAM_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            stop(player, state);
            return;
        }
        Aim aim = aim(player, SuitTuning.BEAM_RANGE);
        if (cryo && aim.target() != state.frostTarget) {
            // The ice only closes on a target held in the beam without a break.
            state.frostTarget = aim.target();
            state.frostTicks = 0;
        }
        ServerLevel level = player.serverLevel();
        if (state.firingTicks % SuitTuning.BEAM_HIT_INTERVAL == 0 && aim.target() != null) {
            LivingEntity target = aim.target();
            target.invulnerableTime = 0;
            float damage = cryo ? SuitTuning.CRYO_BEAM_DAMAGE
                    : armedClass(player) == SuitClass.SCRAP ? SuitTuning.SCRAP_BEAM_DAMAGE * overdrive(state, player)
                    : SuitTuning.BEAM_DAMAGE;
            target.hurt(player.damageSources().playerAttack(player), damage);
            if (cryo) {
                chill(level, target, state);
            } else {
                Vec3 look = player.getLookAngle();
                target.knockback(0.15D, -look.x, -look.z);
            }
        }
        if (state.firingTicks % 10 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, cryo ? 2.0F : 1.7F);
            Vec3 end = aim.end();
            level.playSound(null, end.x, end.y, end.z, cryo ? SoundEvents.POWDER_SNOW_STEP : SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    /** Cryo beam on a target: slower and slower, then frozen stock-still for a while. */
    private static void chill(ServerLevel level, LivingEntity target, State state) {
        state.frostTicks += SuitTuning.BEAM_HIT_INTERVAL;
        int slow = Math.min(4, state.frostTicks / 8);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, slow));
        target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + 8));
        if (state.frostTicks >= SuitTuning.CRYO_FREEZE_TICKS && Stasis.hold(level, target, SuitTuning.FREEZE_HOLD_TICKS, true)) {
            state.frostTicks = 0;
        }
    }

    // ---------------------------------------------------------------- Mark 1 missiles

    /** X on Mark 1: lock up to six hostiles in front, then the pods fire one missile every other tick. */
    static void fireMissiles(ServerPlayer player, State state) {
        fireMissiles(player, state, false);
    }

    /** @param free true for the full burst's salvo (no cooldown, no cost - the burst paid) */
    static void fireMissiles(ServerPlayer player, State state, boolean free) {
        long now = player.level().getGameTime();
        if (!free && (now < state.skill1Ready || !state.salvo.isEmpty())) {
            player.displayClientMessage(Component.translatable("message.flightsuit.skill_cooldown",
                    String.format("%.1f", (state.skill1Ready - now) / 20.0F)), true);
            return;
        }
        if (!free && !SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.MISSILE_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        if (!free) {
            state.skill1Ready = now + SuitTuning.MISSILE_COOLDOWN_TICKS;
            sendStatus(player, state);
        }
        List<LivingEntity> targets = lockTargets(player);
        state.salvoAim = aim(player, 64.0D).end();
        state.salvoFired = 0;
        state.salvo.clear();
        for (int i = 0; i < SuitTuning.MISSILE_COUNT; i++) {
            // Null = no lock: that missile goes for the aim point (and whatever blocks are there).
            state.salvo.add(targets.isEmpty() ? null : targets.get(i % targets.size()));
        }
        ModNetwork.sendToTrackingAndSelf(player, SuitAnimS2CPacket.oneShot(player, SuitAnim.MISSILE_LAUNCH, 0));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.7F, 1.6F);
        player.displayClientMessage(targets.isEmpty()
                ? Component.translatable("message.flightsuit.missiles_point")
                : Component.translatable("message.flightsuit.missiles_locked", targets.size()), true);
    }

    static List<LivingEntity> lockTargets(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double cos = Math.cos(Math.toRadians(SuitTuning.MISSILE_LOCK_ANGLE_DEG));
        double range = SuitTuning.MISSILE_LOCK_RANGE;
        List<LivingEntity> found = new ArrayList<>(player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(range), mob -> {
                    if (!(mob instanceof Enemy) || !mob.isAlive() || isFriendly(player, mob)) {
                        return false;
                    }
                    Vec3 to = mob.getBoundingBox().getCenter().subtract(eye);
                    return to.length() <= range && to.normalize().dot(look) >= cos && player.hasLineOfSight(mob);
                }));
        found.sort(Comparator.comparingDouble(mob -> -mob.getBoundingBox().getCenter().subtract(eye).normalize().dot(look)));
        return found.size() > SuitTuning.MISSILE_COUNT ? new ArrayList<>(found.subList(0, SuitTuning.MISSILE_COUNT)) : found;
    }

    private static void tickSalvo(ServerPlayer player, State state, SuitClass suitClass) {
        if (state.salvo.isEmpty() || player.tickCount % 2 != 0) {
            return;
        }
        if (suitClass != SuitClass.STANDARD) {
            state.salvo.clear();
            return;
        }
        LivingEntity target = state.salvo.poll();
        // Alternate shoulders: right pod, left pod, ...
        int side = state.salvoFired++ % 2 == 0 ? 1 : -1;
        float yaw = player.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 pod = player.position().add(0.0D, 1.5D, 0.0D).add(right.scale(0.3D * side)).subtract(forward.scale(0.1D));
        Vec3 launch = new Vec3(0.0D, 0.5D, 0.0D).add(right.scale(0.18D * side)).add(player.getLookAngle().scale(0.3D));
        MissileEntity.launch(player.serverLevel(), player, pod, launch, target, state.salvoAim);
    }

    // ---------------------------------------------------------------- status

    /** The buff the HUD shows a timer for (overdrive, the Triforce, x-ray, Heaven's Door), whichever is on. */
    private static long buffUntil(State state) {
        return Math.max(Math.max(state.overdriveUntil, state.triforceUntil), Math.max(state.xrayUntil, state.doorUntil));
    }

    static void sendStatus(ServerPlayer player, State state) {
        StolenSkill stolen = PhantomArts.stolen(player);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new WeaponStatusS2CPacket(state.skill1Ready, state.skill2Ready, state.gauge, state.spadeUntil, state.ultReady,
                        state.superSaiyan, state.ssjLockedUntil, stolen == null ? "" : stolen.name(), state.stolenReady,
                        state.skill3Ready, buffUntil(state), state.armourMode));
    }

    /** The HUD status straight after joining (the stolen card is kept across sessions). */
    public static void sendStatusNow(ServerPlayer player) {
        sendStatus(player, state(player));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
            State state = STATES.get(target.getUUID());
            if (state != null && state.firing != FIRE_NONE) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), new BeamStateS2CPacket(target.getId(), state.firing));
            }
            if (state != null && state.superSaiyan) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher),
                        new com.pfkfks.flightsuit.network.EntityFxS2CPacket(target.getId(), com.pfkfks.flightsuit.network.EntityFxS2CPacket.GOLDEN, true));
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendStatusNow(player);
            // Never left floating by a card platform cut short (players have no gravity switch of their own).
            if (player.isNoGravity()) {
                player.setNoGravity(false);
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        PhantomArts.onClone(event);
    }

    // ---------------------------------------------------------------- damage hooks

    /** Your own missiles' blasts spare you, your companion suits and your body. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        HeroArts.hylianShield(event);
        SwordArts.onAttack(event);
        BishopArts.onAttack(event);
        if (event.getSource().getDirectEntity() instanceof MissileEntity missile && missile.getOwner() instanceof ServerPlayer owner
                && isFriendly(owner, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Carte Noir: a phantom's own hits sometimes send a black card after the target. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        HulkbusterArts.onHurt(event);
        BishopArts.onHurt(event);
        if (event.getSource().getEntity() instanceof ServerPlayer player && !(event.getSource().getDirectEntity() instanceof CardEntity)
                && armedClass(player) == SuitClass.PHANTOM && !isFriendly(player, event.getEntity())) {
            PhantomCards.carteNoir(player, state(player), event.getEntity());
        }
    }

    public static void forget(UUID playerId) {
        STATES.remove(playerId);
    }

    /** Dropping states that are still on (logging out): Super Saiyan's speed boost, the card platform, armour mode. */
    public static void endUltimates(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state != null && state.superSaiyan) {
            SwordArts.setSuperSaiyan(player, state, false);
        }
        if (state != null) {
            SuitSkills.endCardPlatform(player, state);
            HulkbusterArts.setArmourMode(player, state, false);
        }
    }
}
