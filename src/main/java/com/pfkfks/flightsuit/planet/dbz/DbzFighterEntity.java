package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.planet.PlanetStory;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.suit.KiGuard;
import com.pfkfks.flightsuit.suit.KiShots;
import com.pfkfks.flightsuit.suit.StolenSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.Set;

/**
 * A Dragon Ball character (DESIGN.md 4-16, M15; chapters 2-4 after the M16 test; M17 motions). NPCs only talk; allies
 * fight beside you; bosses fight with their signature moves; minions swarm. Bosses and allies don't die - beaten,
 * they withdraw (the story moves on). Never saved: the story puts people back while someone's near.
 *
 * Since M17 the body moves for real (DbzAction, posed by the client's DbzFighterModel): three-blow combos with a
 * moment's hit-stop on each blow, charging and firing poses, being thrown back; bosses may guard or dodge a player's
 * beam and now and then clash with an ally in the air. Ki blasts fly (KiShots) and can be cut out of the air.
 * A fighter can also be an actor (ACTING): no AI, can't be hurt, the cutscene (cutscene.CutsceneRunner) moves it.
 */
public class DbzFighterEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(DbzFighterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIGHTING = SynchedEntityData.defineId(DbzFighterEntity.class, EntityDataSerializers.BOOLEAN);
    /** M17: the body's action (DbzAction ordinal) and the game tick it started, for the client's poses. */
    private static final EntityDataAccessor<Byte> ACTION = SynchedEntityData.defineId(DbzFighterEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> ACTION_START = SynchedEntityData.defineId(DbzFighterEntity.class, EntityDataSerializers.INT);
    /** M17: in a cutscene - no AI, untouchable, moved by the script. */
    private static final EntityDataAccessor<Boolean> ACTING = SynchedEntityData.defineId(DbzFighterEntity.class, EntityDataSerializers.BOOLEAN);

    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.2F), 2.0F);
    /** Ticks a blow freezes both sides for. */
    private static final int HIT_STOP = 3;
    /** Between the blows of a combo; a breather after the third. */
    private static final int COMBO_GAP = 6;
    private static final int COMBO_REST = 20;
    private static final int CLASH_TICKS = 30;
    private static final int CLASH_EVERY = 200;

    private final ServerBossEvent bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private @Nullable BlockPos home;
    private int blastCooldown = 40;
    private int specialCooldown = 160;
    private int charging;
    private boolean gone;
    /** Frieza's final form, Cell's regeneration, Zarbon's monster form: once each (after the M16 test). */
    private boolean transformed;
    /** Android 17's barrier is up (halves what reaches him). */
    private int barrier;
    /** Goku gathering the Spirit Bomb: he stands, hands up, and does nothing else (DbzSaga). */
    private int channeling;
    /** M17: the last skill it used that the phantom could steal, and when (PhantomArts). */
    private @Nullable StolenSkill lastSkill;
    private long lastSkillAt;
    /** M17: skills already stolen from this boss in this fight (once each). */
    private final Set<StolenSkill> stolenFrom = EnumSet.noneOf(StolenSkill.class);
    /** M17 motions: hit-stop, guarding until, the clash (and its cooldown), damage dealt and taken multipliers. */
    private int hitStop;
    private int guardUntil;
    private int clashTicks;
    private int clashCooldown = CLASH_EVERY;
    private @Nullable DbzFighterEntity clashWith;
    private boolean clashLeader;
    private float damageDealt = 1.0F;
    private float damageTaken = 1.0F;
    private int buffUntil;
    /** Whether the NoAi flag was set before acting began (to put it back). */
    private boolean hadNoAi;
    /** M17 story moments at points in a fight: run once when health falls to the share (0 = instead of withdrawing). */
    private final java.util.List<Milestone> milestones = new java.util.ArrayList<>();

    private record Milestone(float share, Runnable action) {
    }

    /** Aura colours round a powered-up fighter (synced; drawn by the server as particles). */
    public static final int AURA_NONE = 0;
    public static final int AURA_GOLD = 1;
    public static final int AURA_RED = 2;
    public static final int AURA_WHITE = 3;
    private int aura;
    private int auraUntil;
    /** The story has hung its moments on it (DbzSaga.arm) - once. */
    private boolean armed;
    /** M17: flying along with this player after a scene (DbzSaga), not fighting. */
    private @Nullable java.util.UUID escortOf;

    public DbzFighterEntity(EntityType<? extends DbzFighterEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        bossBar.setVisible(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    /** @param fighting allies: fighting beside you (else at home to talk) */
    public static DbzFighterEntity create(ServerLevel level, DbzCharacter type, BlockPos home, boolean fighting) {
        DbzFighterEntity fighter = new DbzFighterEntity(ModEntities.DBZ_FIGHTER.get(), level);
        fighter.entityData.set(FIGHTING, type.isFoe() || fighting);
        fighter.home = home.immutable();
        fighter.setCharacter(type);
        fighter.setHealth(fighter.getMaxHealth());
        if (type.role() == DbzCharacter.Role.NPC || !fighter.isFighting()) {
            fighter.restrictTo(home, 6);
        }
        return fighter;
    }

    /**
     * Who it is now - also mid-fight (M17: Goku going Super Saiyan, Vegeta becoming a Great Ape, Cell absorbing):
     * stats, name, boss bar and size follow; health keeps its share.
     */
    public void setCharacter(DbzCharacter type) {
        float share = getMaxHealth() > 0.0F ? getHealth() / getMaxHealth() : 1.0F;
        entityData.set(TYPE, type.ordinal());
        set(this, Attributes.MAX_HEALTH, type.health());
        set(this, Attributes.ATTACK_DAMAGE, Math.max(1.0D, type.damage()));
        set(this, Attributes.MOVEMENT_SPEED, type.speed());
        setHealth(Math.max(1.0F, getMaxHealth() * share));
        setCustomName(type.displayName());
        setCustomNameVisible(type.role() != DbzCharacter.Role.MINION);
        setItemSlot(EquipmentSlot.MAINHAND, type == DbzCharacter.TRUNKS ? new ItemStack(com.pfkfks.flightsuit.registry.ModItems.TRUNKS_SWORD.get())
                : ItemStack.EMPTY);
        bossBar.setName(type.displayName());
        bossBar.setVisible(type.role() == DbzCharacter.Role.BOSS && !isActing());
        transformed = false;
        refreshDimensions();
    }

    private static void set(DbzFighterEntity fighter, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance instance = fighter.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(TYPE, 0);
        entityData.define(FIGHTING, false);
        entityData.define(ACTION, (byte) 0);
        entityData.define(ACTION_START, 0);
        entityData.define(ACTING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new ComboGoal());
        goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8D));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, DbzFighterEntity.class) {
            @Override
            public boolean canUse() {
                return isFighting() && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::isEnemy) {
            @Override
            public boolean canUse() {
                return isFighting() && super.canUse();
            }
        });
    }

    public DbzCharacter getCharacter() {
        return DbzCharacter.byId(entityData.get(TYPE));
    }

    public boolean isFighting() {
        return entityData.get(FIGHTING);
    }

    public void setFighting(boolean fighting) {
        entityData.set(FIGHTING, fighting);
        if (!fighting) {
            setTarget(null);
        }
    }

    public @Nullable BlockPos getHome() {
        return home;
    }

    /** Saiyans and Saibamen go for players (and their suits) and our side's fighters; our side goes for them. */
    public boolean isEnemy(LivingEntity other) {
        if (!other.isAlive() || other == this || isActing()) {
            return false;
        }
        DbzCharacter me = getCharacter();
        if (other instanceof DbzFighterEntity fighter) {
            return me.isFoe() != fighter.getCharacter().isFoe() && fighter.isFighting() && !fighter.isActing();
        }
        if (!me.isFoe()) {
            return false;
        }
        if (other instanceof Player player) {
            return !player.isCreative() && !player.isSpectator()
                    && !(player instanceof ServerPlayer watcher && com.pfkfks.flightsuit.cutscene.CutsceneRunner.isWatching(watcher));
        }
        return other instanceof SuitCompanionEntity;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (TYPE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getCharacter().scale());
    }

    // ---------------------------------------------------------------- actions (M17)

    /** Starts a body action (synced; the client poses it). */
    public void setAction(DbzAction action) {
        entityData.set(ACTION, (byte) action.ordinal());
        entityData.set(ACTION_START, (int) level().getGameTime());
    }

    public DbzAction getAction() {
        return DbzAction.byId(entityData.get(ACTION));
    }

    /** Ticks since the current action started (client or server). */
    public float actionAge(float partialTick) {
        return Math.max(0.0F, level().getGameTime() - entityData.get(ACTION_START) + partialTick);
    }

    /** In a cutscene: no AI, untouchable, moved by the script. */
    public boolean isActing() {
        return entityData.get(ACTING);
    }

    public void setActing(boolean acting) {
        if (acting == isActing()) {
            return;
        }
        entityData.set(ACTING, acting);
        if (acting) {
            hadNoAi = isNoAi();
            setNoAi(true);
            setTarget(null);
            getNavigation().stop();
            charging = 0;
            endClash();
            bossBar.setVisible(false);
        } else {
            setNoAi(hadNoAi);
            setAction(DbzAction.IDLE);
            bossBar.setVisible(getCharacter().role() == DbzCharacter.Role.BOSS);
        }
    }

    /**
     * A story moment at a point in this fight (M17, the saga's cutscenes): {@code action} runs once when its health
     * falls to {@code share} of the maximum; at 0 it runs instead of the withdrawal (it holds on at a sliver).
     */
    public void atHealth(float share, Runnable action) {
        milestones.add(new Milestone(share, action));
    }

    /** Runs (and forgets) every moment due at this share of health; true if any ran. */
    boolean runMilestones(float share) {
        boolean ran = false;
        java.util.List<Milestone> due = new java.util.ArrayList<>();
        for (java.util.Iterator<Milestone> it = milestones.iterator(); it.hasNext(); ) {
            Milestone milestone = it.next();
            if (share <= milestone.share()) {
                due.add(milestone);
                it.remove();
            }
        }
        for (Milestone milestone : due) {
            milestone.action().run();
            ran = true;
        }
        return ran;
    }

    public boolean isArmed() {
        return armed;
    }

    public void setArmed() {
        armed = true;
    }

    /** An aura round it (AURA_*) for {@code ticks} (0 = for good). */
    public void setAura(int colour, int ticks) {
        aura = colour;
        auraUntil = ticks > 0 ? tickCount + ticks : 0;
    }

    /** After a scene: flies along with {@code player} (follows within 20 blocks, flies when farther, gone past 96). */
    public void escort(java.util.UUID player) {
        escortOf = player;
        setFighting(false);
        setActing(false);
        clearRestriction();
        setTarget(null);
        bossBar.setVisible(false);
        addTag("flightsuit_escort");
    }

    public boolean isEscort() {
        return escortOf != null;
    }

    /** Story buffs (Gohan's rage, Goku's Kaioken): damage dealt and taken, for {@code ticks} (0 = for good). */
    public void buff(float dealt, float taken, int ticks) {
        damageDealt = dealt;
        damageTaken = taken;
        buffUntil = ticks > 0 ? tickCount + ticks : 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            DbzAction action = getAction();
            if (action.ticks() > 0 && actionAge(0.0F) >= action.ticks()) {
                setAction(onGround() || isActing() ? DbzAction.IDLE : DbzAction.FLY);
            }
            if (buffUntil > 0 && tickCount >= buffUntil) {
                damageDealt = 1.0F;
                damageTaken = 1.0F;
                buffUntil = 0;
            }
            if (aura != AURA_NONE && level() instanceof ServerLevel server) {
                if (auraUntil > 0 && tickCount >= auraUntil) {
                    aura = AURA_NONE;
                } else if (tickCount % 2 == 0) {
                    drawAura(server);
                }
            }
            if (escortOf != null) {
                tickEscort();
            }
        }
    }

    // ---------------------------------------------------------------- moves

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel server) || !isFighting() || isActing()) {
            return;
        }
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (hitStop > 0) {
            hitStop--;
            setDeltaMovement(0.0D, Math.min(0.0D, getDeltaMovement().y), 0.0D);
            getNavigation().stop();
            return;
        }
        if (clashTicks > 0) {
            if (clashLeader) {
                clashTick(server);
            }
            return;
        }
        LivingEntity target = getTarget();
        if (blastCooldown > 0) {
            blastCooldown--;
        }
        if (specialCooldown > 0) {
            specialCooldown--;
        }
        if (charging > 0) {
            chargeTick(server, target);
            return;
        }
        if (channeling > 0) {
            channelTick(server);
            return;
        }
        if (barrier > 0) {
            barrier--;
            if (barrier % 4 == 0) {
                server.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.0D, getZ(), 6, 0.8D, 1.0D, 0.8D, 0.2D);
            }
        }
        sagaTransform(server);
        if (getCharacter() == DbzCharacter.MAJIN_BUU || getCharacter() == DbzCharacter.KID_BUU) {
            // Buu pulls himself back together.
            if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) {
                heal(getCharacter() == DbzCharacter.KID_BUU ? 3.0F : 2.0F);
            }
        }
        if (getCharacter().role() == DbzCharacter.Role.BOSS) {
            tryClash(server);
        }
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = distanceTo(target);
        boolean sees = getSensing().hasLineOfSight(target);
        DbzMoves.use(this, server, target, distance, sees);
    }

    // Small handles for DbzMoves (the move table per character).

    boolean blastReady() {
        return blastCooldown <= 0;
    }

    boolean specialReady() {
        return specialCooldown <= 0;
    }

    void blastCooldown(int ticks) {
        blastCooldown = ticks;
    }

    void specialCooldown(int ticks) {
        specialCooldown = ticks;
    }

    boolean isTransformedNow() {
        return transformed;
    }

    void raiseBarrier(int ticks) {
        barrier = ticks;
    }

    /** Begins a charged beam: {@code ticks} of gathering, crouched, hands at one side (chargeTick fires it). */
    void startCharge(int ticks) {
        charging = ticks;
        setAction(DbzAction.CHARGE);
    }

    public boolean isCharging() {
        return charging > 0;
    }

    /**
     * A charged beam: Vegeta's Galick Gun, Goku's (and Cell's) Kamehameha, Recoome's Eraser Gun, Trunks' Burning
     * Attack, Frieza's Supernova, Piccolo's Special Beam Cannon - gathering energy, then a wide beam.
     */
    private void chargeTick(ServerLevel server, @Nullable LivingEntity target) {
        getNavigation().stop();
        DbzCharacter me = getCharacter();
        StolenSkill shared = DbzMoves.beamOf(me);
        Vec3 hands = com.pfkfks.flightsuit.suit.SwordArts.hands(this);
        if (shared != null) {
            shared.chargeEffect(server, this, Math.max(0, DbzMoves.chargeTicks(me) - charging));
        } else {
            server.sendParticles(ParticleTypes.WITCH, hands.x, hands.y, hands.z, 6, 0.4D, 0.4D, 0.4D, 0.05D);
        }
        if (target != null) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (--charging > 0) {
            return;
        }
        specialCooldown = DbzMoves.beamCooldown(me);
        setAction(DbzAction.FIRE);
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 from = me == DbzCharacter.PICCOLO ? getEyePosition() : hands;
        Vec3 dir = target.getBoundingBox().getCenter().subtract(from).normalize();
        float damage = DbzMoves.beamDamage(me) * damageDealt;
        if (shared != null) {
            shared.cast(server, this, from, dir, target, damage, this::isEnemy);
            usedSkill(shared);
            return;
        }
        // Recoome's Eraser Gun, Frieza's Supernova: a purple beam of their own.
        com.pfkfks.flightsuit.suit.SuitSkills.beam(server, this, from, dir, 30.0D, 1.8D, damage, 1.0D,
                new DustParticleOptions(new Vector3f(0.75F, 0.3F, 0.9F), 2.8F), new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 1.3F),
                this::isEnemy);
        playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 0.6F);
    }

    /**
     * Once each, at half health (after the M16 test): Frieza goes to his final form (heals, hits harder - the saga
     * brings Goku in then), Cell regenerates, Zarbon turns into his monster form. M17: a pillar of gold, the ground
     * cracking, the screen shaking.
     */
    private void sagaTransform(ServerLevel server) {
        DbzCharacter me = getCharacter();
        if (transformed || getHealth() > getMaxHealth() * 0.5F
                || me != DbzCharacter.FRIEZA && me != DbzCharacter.CELL && me != DbzCharacter.ZARBON) {
            return;
        }
        if (me == DbzCharacter.FRIEZA) {
            // M17: his final form is a skin (and stats) of its own.
            setCharacter(DbzCharacter.FRIEZA_FINAL);
        }
        transformed = true;
        heal(getMaxHealth() * (me == DbzCharacter.ZARBON ? 0.1F : 0.3F));
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && me != DbzCharacter.FRIEZA) {
            damage.setBaseValue(damage.getBaseValue() * 1.4D);
        }
        transformBurst(server, this);
        say(server, me.line("transform"));
    }

    /** Power rising: the transform pose, a pillar of gold, the ground cracking, and the screen shaking for those near. */
    public static void transformBurst(ServerLevel server, DbzFighterEntity who) {
        who.setAction(DbzAction.TRANSFORM);
        com.pfkfks.flightsuit.suit.SwordArts.transformBurst(server, who);
        BlockState ground = server.getBlockState(who.blockPosition().below());
        if (ground.isAir()) {
            ground = Blocks.STONE.defaultBlockState();
        }
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12.0D;
            for (int r = 1; r <= 4; r++) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), who.getX() + Math.cos(angle) * r * 0.9D,
                        who.getY() + 0.1D, who.getZ() + Math.sin(angle) * r * 0.9D, 1, 0.1D, 0.1D, 0.1D, 0.1D);
            }
        }
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, who.getX(), who.getY() + 1.0D, who.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        server.playSound(null, who.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2F, 1.3F);
        com.pfkfks.flightsuit.cutscene.CutsceneRunner.shake(server, who.position(), 48.0D, 20);
    }

    public boolean isTransformed() {
        return transformed || getCharacter() == DbzCharacter.FRIEZA_FINAL;
    }

    /** Goku: hands up, gathering the Spirit Bomb for {@code ticks} (DbzSaga lets it fly). */
    public void channel(int ticks) {
        channeling = ticks;
        getNavigation().stop();
        setTarget(null);
        setAction(DbzAction.HANDS_UP);
    }

    public boolean isChanneling() {
        return channeling > 0;
    }

    /** The Spirit Bomb growing over his head, light drawn in from all around. */
    private void channelTick(ServerLevel server) {
        channeling--;
        getNavigation().stop();
        setXRot(-60.0F);
        if (getAction() != DbzAction.HANDS_UP) {
            setAction(DbzAction.HANDS_UP);
        }
        float grown = 1.0F - channeling / 600.0F;
        double size = 0.5D + grown * 3.0D;
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 4.0D + size, getZ(), 6, size * 0.5D, size * 0.5D, size * 0.5D, 0.0D);
        if (tickCount % 4 == 0) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            server.sendParticles(ParticleTypes.GLOW, getX() + Math.cos(angle) * 12.0D, getY() + 2.0D, getZ() + Math.sin(angle) * 12.0D,
                    0, -Math.cos(angle), 0.3D, -Math.sin(angle), 0.6D);
        }
        if (channeling == 0) {
            setAction(DbzAction.FIRE);
        }
    }

    /** Ki blasts (M17: they fly, with a tail, and can be dodged or cut): the first at the target, the rest at others near. */
    void kiBlast(ServerLevel server, LivingEntity target, float damage, int count) {
        swing(InteractionHand.MAIN_HAND);
        setAction(DbzAction.FIRE);
        DbzCharacter me = getCharacter();
        KiShots.Style style = me.isFoe() && me != DbzCharacter.CELL_JR && me != DbzCharacter.ANDROID_17 && me != DbzCharacter.ANDROID_18
                ? KiShots.Style.KI_DARK : KiShots.Style.KI;
        for (int n = 0; n < count; n++) {
            LivingEntity victim = n == 0 ? target : nearestOtherEnemy(server, target);
            if (victim == null) {
                victim = target;
            }
            Vec3 from = com.pfkfks.flightsuit.suit.SwordArts.hands(this).add(0.0D, n * 0.2D, 0.0D);
            Vec3 aim = victim.getBoundingBox().getCenter().add(victim.getDeltaMovement().scale(4.0D));
            KiShots.fire(server, this, style, from, aim.subtract(from), 1.5D, 32.0D, damage * damageDealt, 0.0D, this::isEnemy);
        }
        if (me == DbzCharacter.FRIEZA) {
            usedSkill(StolenSkill.DEATH_BEAM);
        }
    }

    private @Nullable LivingEntity nearestOtherEnemy(ServerLevel server, LivingEntity not) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20.0D), this::isEnemy)) {
            double d = other.distanceToSqr(this);
            if (other != not && d < bestDist) {
                best = other;
                bestDist = d;
            }
        }
        return best;
    }

    /** Saiyans close in at speed: a blur, and they're beside you. */
    void dash(ServerLevel server, LivingEntity target) {
        Vec3 to = target.position().subtract(position()).normalize().scale(Math.min(distanceTo(target) - 2.0D, 10.0D)).add(position());
        afterimage(server);
        if (randomTeleport(to.x, target.getY(), to.z, false)) {
            playSound(SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.6F);
            setAction(DbzAction.PUNCH_R);
        }
    }

    /** A fading shape where it just was. */
    private void afterimage(ServerLevel server) {
        server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() / 2.0D, getZ(), 10, 0.3D, getBbHeight() / 3.0D, 0.3D, 0.02D);
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() / 2.0D, getZ(), 4, 0.2D, getBbHeight() / 3.0D, 0.2D, 0.0D);
    }

    /** Nappa: a blast wave from where he stands (no blocks broken). */
    void blastWave(ServerLevel server, double radius, float damage) {
        setAction(DbzAction.TRANSFORM);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::isEnemy)) {
            hit.hurt(damageSources().mobAttack(this), damage * damageDealt);
            Vec3 push = hit.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() > 1.0E-4D) {
                push = push.normalize().scale(1.4D);
                hit.push(push.x, 0.6D, push.z);
                hit.hurtMarked = true;
            }
        }
        playSound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);
        say(server, getCharacter().line("blast"));
    }

    /** A Saibaman (or Cell Jr.) blowing itself up (M17: the very move the phantom steals). */
    void selfDestruct(ServerLevel server) {
        usedSkill(StolenSkill.SELF_DESTRUCT);
        StolenSkill.SELF_DESTRUCT.cast(server, this, position(), getLookAngle(), null, 8.0F, this::isEnemy);
        gone = true;
        discard();
    }

    void say(ServerLevel server, Component line) {
        for (Player player : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(40.0D))) {
            player.sendSystemMessage(line);
        }
    }

    // ---------------------------------------------------------------- melee: the three-blow combo (M17)

    /**
     * Runs the target down and lays into it: left, right, kick, COMBO_GAP ticks apart, the kick throwing twice as far;
     * every blow stops both sides for a moment (hit-stop) and the one hit reels (HURT). Then a breather.
     */
    private final class ComboGoal extends Goal {
        private int step;
        private int wait;
        private int path;

        ComboGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return isFighting() && !isActing() && target != null && target.isAlive() && charging <= 0 && channeling <= 0 && clashTicks <= 0;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            step = 0;
            wait = 0;
            path = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null || hitStop > 0) {
                return;
            }
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double reach = getBbWidth() / 2.0D + target.getBbWidth() / 2.0D + 1.6D;
            double distance = distanceTo(target);
            if (wait > 0) {
                wait--;
            }
            if (distance > reach || Math.abs(target.getY() - getY()) > 2.5D) {
                if (--path <= 0) {
                    getNavigation().moveTo(target, 1.25D);
                    path = 10;
                }
                return;
            }
            getNavigation().stop();
            if (wait > 0) {
                return;
            }
            blow(target, step);
            step = (step + 1) % 3;
            wait = step == 0 ? COMBO_REST : COMBO_GAP;
        }
    }

    /** One blow of the combo: 0 left, 1 right, 2 the kick. */
    private void blow(LivingEntity target, int step) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        setAction(step == 0 ? DbzAction.PUNCH_L : step == 1 ? DbzAction.PUNCH_R : DbzAction.KICK);
        swing(step == 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * damageDealt * (step == 2 ? 1.3F : 1.0F);
        target.invulnerableTime = 0;
        if (!target.hurt(damageSources().mobAttack(this), damage)) {
            return;
        }
        Vec3 push = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        if (push.lengthSqr() > 1.0E-4D) {
            target.knockback(step == 2 ? 1.2D : 0.4D, -push.x, -push.z);
        }
        hitStop = HIT_STOP;
        if (target instanceof DbzFighterEntity other) {
            other.hitStop = HIT_STOP;
            other.setAction(DbzAction.HURT);
        }
        Vec3 at = target.getBoundingBox().getCenter();
        server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2D, 0.2D, 0.2D, 0.4D);
        server.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, step == 2 ? 0.7F : 0.9F);
    }

    // ---------------------------------------------------------------- the clash (M17)

    /**
     * Now and then a boss and one of our fighters close together fly up and trade blows in the air for a moment -
     * a flurry of flashes. A player's hit on the boss meanwhile ends it: the boss is knocked out of the air.
     */
    private void tryClash(ServerLevel server) {
        if (--clashCooldown > 0 || !onGround()) {
            return;
        }
        clashCooldown = CLASH_EVERY;
        for (DbzFighterEntity ally : server.getEntitiesOfClass(DbzFighterEntity.class, getBoundingBox().inflate(6.0D),
                f -> f.isFighting() && !f.isActing() && !f.getCharacter().isFoe() && f.clashTicks <= 0 && f.charging <= 0 && f.channeling <= 0)) {
            startClash(ally);
            return;
        }
    }

    private void startClash(DbzFighterEntity ally) {
        clashWith = ally;
        clashLeader = true;
        clashTicks = CLASH_TICKS;
        ally.clashWith = this;
        ally.clashLeader = false;
        ally.clashTicks = CLASH_TICKS;
        for (DbzFighterEntity f : new DbzFighterEntity[]{this, ally}) {
            f.setNoGravity(true);
            f.getNavigation().stop();
            f.setAction(DbzAction.FLY);
        }
        playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.5F, 1.4F);
    }

    /** The leader (the boss) moves both: up, then back and forth, a flash at each meeting. */
    private void clashTick(ServerLevel server) {
        DbzFighterEntity other = clashWith;
        if (other == null || !other.isAlive() || other.isActing() || other.level() != level()) {
            endClash();
            return;
        }
        int age = CLASH_TICKS - clashTicks;
        Vec3 mid = position().add(other.position()).scale(0.5D);
        Vec3 axis = other.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        axis = axis.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : axis.normalize();
        double lift = age < 6 ? 0.5D : 0.0D;
        double swing = (age / 3) % 2 == 0 ? 0.9D : 1.6D;
        Vec3 base = new Vec3(mid.x, getY() + lift, mid.z);
        teleportTo(base.x - axis.x * swing, base.y, base.z - axis.z * swing);
        other.teleportTo(base.x + axis.x * swing, base.y, base.z + axis.z * swing);
        lookAt(other);
        other.lookAt(this);
        if (age >= 6 && age % 3 == 0) {
            setAction((age / 3) % 2 == 0 ? DbzAction.PUNCH_L : DbzAction.KICK);
            other.setAction((age / 3) % 2 == 0 ? DbzAction.GUARD : DbzAction.PUNCH_R);
            server.sendParticles(ParticleTypes.FLASH, base.x, base.y + getBbHeight() / 2.0D, base.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            server.sendParticles(ParticleTypes.CRIT, base.x, base.y + getBbHeight() / 2.0D, base.z, 10, 0.3D, 0.3D, 0.3D, 0.4D);
            server.playSound(null, base.x, base.y, base.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.2F, 0.8F + random.nextFloat() * 0.4F);
        }
        if (--clashTicks <= 0) {
            endClash();
        }
    }

    private void lookAt(DbzFighterEntity other) {
        Vec3 to = other.position().subtract(position());
        float yaw = (float) (Math.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    private void endClash() {
        DbzFighterEntity other = clashWith;
        clashWith = null;
        clashTicks = 0;
        setNoGravity(false);
        if (other != null && other.clashWith == this) {
            other.clashWith = null;
            other.clashTicks = 0;
            other.setNoGravity(false);
        }
    }

    public boolean isClashing() {
        return clashTicks > 0;
    }

    // ---------------------------------------------------------------- the phantom's steal (M17)

    /** Records a skill it just used (the phantom can steal it for STEAL_MEMORY_TICKS). */
    public void usedSkill(StolenSkill skill) {
        lastSkill = skill;
        lastSkillAt = level().getGameTime();
    }

    /** What the phantom could steal from it now: what it used lately, or what its kind always does (Saibamen, Cell Jr.). */
    public @Nullable StolenSkill recentSkill(long now) {
        if (lastSkill != null && now - lastSkillAt <= com.pfkfks.flightsuit.suit.SuitTuning.STEAL_MEMORY_TICKS) {
            return lastSkill;
        }
        return DbzMoves.signature(getCharacter());
    }

    /** A boss gives up each skill once a fight; anyone else, any number of times. */
    public boolean allowSteal(StolenSkill skill) {
        return getCharacter().role() != DbzCharacter.Role.BOSS || stolenFrom.add(skill);
    }

    /**
     * A player's beam coming at it (KiGuard): a boss dodges one in seven - a burst sideways, an afterimage left behind
     * - and guards three in ten (arms crossed: half damage). True if it's dodged.
     */
    public boolean dodgesBeam(LivingEntity shooter) {
        if (!(shooter instanceof Player) || getCharacter().role() != DbzCharacter.Role.BOSS || isActing() || charging > 0
                || !(level() instanceof ServerLevel server)) {
            return false;
        }
        float roll = random.nextFloat();
        if (roll < 0.15F) {
            Vec3 shot = position().subtract(shooter.position()).multiply(1.0D, 0.0D, 1.0D);
            Vec3 side = shot.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(-shot.z, 0.0D, shot.x).normalize();
            if (random.nextBoolean()) {
                side = side.scale(-1.0D);
            }
            Vec3 spot = com.pfkfks.flightsuit.suit.SuitSkills.standable(server, this, position().add(side.scale(6.0D)));
            if (spot != null) {
                afterimage(server);
                teleportTo(spot.x, spot.y, spot.z);
                afterimage(server);
                server.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 1.0F, 1.8F);
                return true;
            }
        }
        if (roll < 0.45F) {
            guardUntil = tickCount + 12;
            setAction(DbzAction.GUARD);
        }
        return false;
    }

    // ---------------------------------------------------------------- damage, defeat, talk

    @Override
    public boolean hurt(DamageSource source, float amount) {
        DbzCharacter me = getCharacter();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (isActing() || !isFighting() || source.getEntity() instanceof DbzFighterEntity other && other.getCharacter().isFoe() == me.isFoe()) {
            return false;
        }
        // Our side only takes hits from the other side.
        if (!me.isFoe() && !(source.getEntity() instanceof DbzFighterEntity)) {
            return false;
        }
        if (barrier > 0) {
            amount *= 0.5F;
        }
        amount *= damageTaken;
        boolean byPlayer = source.getEntity() instanceof Player;
        if (byPlayer && guardUntil > tickCount) {
            amount *= 0.5F;
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.6D, getZ(), 8, 0.3D, 0.3D, 0.3D, 0.2D);
            }
        }
        if (byPlayer && clashTicks > 0 && level() instanceof ServerLevel server) {
            // Caught mid-clash: knocked out of the air.
            endClash();
            setAction(DbzAction.HURT);
            setDeltaMovement(0.0D, -1.2D, 0.0D);
            hurtMarked = true;
            server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0D, getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            amount *= 1.5F;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && isAlive() && !milestones.isEmpty()) {
            runMilestones(getHealth() / getMaxHealth());
        }
        if (hurt && charging <= 0 && channeling <= 0 && getAction() != DbzAction.TRANSFORM && getAction() != DbzAction.GUARD) {
            setAction(DbzAction.HURT);
        }
        return hurt;
    }

    /** Bosses and our side don't die: beaten, they withdraw - the Saiyans' defeat moves the story on. */
    @Override
    public void die(DamageSource source) {
        DbzCharacter me = getCharacter();
        if (level().isClientSide || me.role() == DbzCharacter.Role.MINION || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(level() instanceof ServerLevel server)) {
            super.die(source);
            return;
        }
        if (gone) {
            return;
        }
        if (me.role() == DbzCharacter.Role.BOSS && DbzMoves.holdsOn(this, server)) {
            // The story steps in first (a cutscene at a point in the fight): back up at a sliver, untouchable for now.
            setHealth(1.0F);
            return;
        }
        gone = true;
        setHealth(1.0F);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0D, getZ(), 20, 0.4D, 0.8D, 0.4D, 0.03D);
        say(server, me.line("beaten"));
        if (me.role() == DbzCharacter.Role.BOSS) {
            PlanetStory.onBossBeaten(server, this, source.getEntity());
        }
        discard();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || isFighting() || isActing()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PlanetStory.talk(serverPlayer, getCharacter());
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        endClash();
        super.remove(reason);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        // They fly, clash in the air, get thrown about: the ground doesn't hurt them.
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // M17: by name (the list grows in the middle now).
        tag.putString("Character", getCharacter().name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        DbzCharacter character = tag.contains("Character", net.minecraft.nbt.Tag.TAG_STRING)
                ? DbzCharacter.byName(tag.getString("Character")) : DbzCharacter.byLegacyId(tag.getInt("Character"));
        entityData.set(TYPE, character.ordinal());
    }

    /** For the cutscenes: puts it at a spot and facing, no questions asked. */
    public void place(Vec3 at, float yaw) {
        teleportTo(at.x, at.y, at.z);
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        yRotO = yaw;
    }

    /** Gold particles round it (a Super Saiyan, a powered-up ally) - DbzMoves and the cutscenes call it. */
    void goldAura(ServerLevel server) {
        server.sendParticles(GOLD, getX(), getY() + getBbHeight() / 2.0D, getZ(), 3, getBbWidth() * 0.7D, getBbHeight() * 0.45D,
                getBbWidth() * 0.7D, 0.0D);
    }

    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.15F), 2.0F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(0.95F, 0.95F, 1.0F), 1.6F);

    private void drawAura(ServerLevel server) {
        DustParticleOptions dust = aura == AURA_RED ? RED : aura == AURA_WHITE ? WHITE : GOLD;
        server.sendParticles(dust, getX(), getY() + getBbHeight() / 2.0D, getZ(), 3, getBbWidth() * 0.7D, getBbHeight() * 0.45D,
                getBbWidth() * 0.7D, 0.0D);
    }

    /** Escorting: walks after its player when close, flies after them when they're far, leaves when they're gone. */
    private void tickEscort() {
        Player player = escortOf == null ? null : level().getPlayerByUUID(escortOf);
        if (player == null || player.isSpectator() || distanceToSqr(player) > 96.0D * 96.0D) {
            discard();
            return;
        }
        double distance = distanceTo(player);
        getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (distance > 12.0D || !player.onGround() && distance > 5.0D) {
            // Fly: straight at a spot beside and above the player.
            setNoGravity(true);
            Vec3 spot = player.position().add(-Math.sin(Math.toRadians(player.getYRot() + 60.0F)) * 3.0D, 1.5D,
                    Math.cos(Math.toRadians(player.getYRot() + 60.0F)) * 3.0D);
            Vec3 to = spot.subtract(position());
            double speed = Math.min(2.2D, Math.max(0.4D, to.length() * 0.15D));
            setDeltaMovement(to.normalize().scale(speed));
            float yaw = (float) (Math.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            if (getAction() != DbzAction.FLY) {
                setAction(DbzAction.FLY);
            }
            getNavigation().stop();
        } else {
            if (isNoGravity()) {
                setNoGravity(false);
                setAction(DbzAction.IDLE);
            }
            if (distance > 4.0D && tickCount % 10 == 0) {
                getNavigation().moveTo(player, 1.2D);
            }
        }
    }

    /** Short blindness etc. for what it hits - for DbzMoves. */
    static void blind(LivingEntity victim, int ticks) {
        victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
    }
}
