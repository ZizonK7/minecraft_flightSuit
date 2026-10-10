package com.pfkfks.flightsuit.town;

import com.pfkfks.flightsuit.hero.HeroData;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.war.Kingdom;
import com.pfkfks.flightsuit.war.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Someone who lives in a Three Kingdoms fortress town or in Hero City (after the M16 test: "make them real towns,
 * so I can go in and do things there"). Not saved - like the garrison, the town's people are put back each visit
 * (TownLife). Their day: work by day (fields, a shop, the smithy, the office, the lab, the beat), the square in
 * the evening, home at night - and indoors whenever the town is under attack.
 *
 * What the player can do with them: trade with the merchants and shopkeepers (the vanilla trading screen), and
 * help with small requests - bring them what they ask for, for emeralds and the town's goodwill. Hitting them
 * costs goodwill.
 */
public class TownsfolkEntity extends PathfinderMob implements Merchant {
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(TownsfolkEntity.class, EntityDataSerializers.INT);
    /** Which town: 0..2 a kingdom's fortress (Kingdom ordinal), 3 Hero City; M17: 4 West City, 5 the Namekian village. */
    public static final int HERO_CITY = 3;
    public static final int WEST_CITY = 4;
    public static final int NAMEK = 5;
    private static final long DAY_START = 1000L;
    private static final long EVENING = 11000L;
    private static final long NIGHT = 13000L;

    private int town = HERO_CITY;
    private int index;
    private @Nullable TownPlan plan;
    private @Nullable BlockPos home;
    private @Nullable Request request;
    private @Nullable Player trader;
    private @Nullable MerchantOffers offers;
    private long lastPenaltyAt;

    /** A small favour asked of the player: bring {@code count} of {@code item} for {@code pay} of the town's money. */
    public record Request(Item item, int count, int pay) {
    }

    public TownsfolkEntity(EntityType<? extends TownsfolkEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    public static TownsfolkEntity create(ServerLevel level, int town, TownPlan plan, TownRole role, int index) {
        TownsfolkEntity person = new TownsfolkEntity(ModEntities.TOWNSFOLK.get(), level);
        person.entityData.set(ROLE, role.ordinal());
        person.town = town;
        person.plan = plan;
        person.index = index;
        person.home = plan.home(index);
        person.setItemSlot(EquipmentSlot.MAINHAND, role.tool());
        person.setCustomName(person.title());
        person.request = role.trades() ? null : TownRequests.roll(role, level.random, town >= WEST_CITY || role.isCity() ? 5 : 4);
        person.refreshDimensions();
        return person;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ROLE, TownRole.FORT_ELDER.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.4D));
        goalSelector.addGoal(3, new DayGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public TownRole getRole() {
        return TownRole.byId(entityData.get(ROLE));
    }

    public int town() {
        return town;
    }

    public boolean isCity() {
        return town == HERO_CITY;
    }

    private Component title() {
        TownRole role = getRole();
        if (role.trades()) {
            return Component.translatable("town.flightsuit.shop." + role.group() + "_" + Math.floorMod(index, shopKinds()))
                    .withStyle(ChatFormatting.GREEN);
        }
        return role.displayName();
    }

    private int shopKinds() {
        return getRole().isCity() ? 4 : 3;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ROLE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return getRole().isChild() ? super.getDimensions(pose).scale(0.6F) : super.getDimensions(pose);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    // ---------------------------------------------------------------- the town's goodwill

    /** The Dragon Ball towns (M17) keep no goodwill score: they're friendly to everyone. */
    private boolean keepsTrust() {
        return town < WEST_CITY;
    }

    private int trust(Player player) {
        if (!(level() instanceof ServerLevel server) || !keepsTrust()) {
            return 0;
        }
        return isCity() ? HeroData.get(server.getServer()).trust(player.getUUID())
                : WarData.get(server.getServer()).trust(player.getUUID(), Kingdom.byId(town));
    }

    private void addTrust(Player player, int delta) {
        if (!(level() instanceof ServerLevel server) || !keepsTrust()) {
            return;
        }
        if (isCity()) {
            HeroData.get(server.getServer()).addTrust(player.getUUID(), delta);
        } else {
            WarData.get(server.getServer()).addTrust(player.getUUID(), Kingdom.byId(town), delta);
        }
    }

    /** Whether the town is under attack right now (a fortress battle, Hero City's invasion): everyone indoors. */
    private boolean underAttack() {
        if (!(level() instanceof ServerLevel server)) {
            return false;
        }
        if (town == NAMEK) {
            // Frieza's men at the village: everyone into the domes.
            return com.pfkfks.flightsuit.planet.dbz.DbzSaga.isFighting(com.pfkfks.flightsuit.planet.Planet.NAMEK);
        }
        if (town == WEST_CITY) {
            return false;
        }
        return isCity() ? HeroData.get(server.getServer()).storm != null
                : WarData.get(server.getServer()).battleAt(Kingdom.byId(town)) != null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof ServerPlayer player && !level().isClientSide
                && level().getGameTime() - lastPenaltyAt > 40L) {
            lastPenaltyAt = level().getGameTime();
            addTrust(player, isDeadOrDying() ? -15 : -5);
            player.displayClientMessage(Component.translatable("town.flightsuit.hurt", title()).withStyle(ChatFormatting.RED), true);
        }
        return hurt;
    }

    // ---------------------------------------------------------------- talking, requests, trading

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !isAlive()) {
            return InteractionResult.PASS;
        }
        TownRole role = getRole();
        if (trust(player) <= -50) {
            say(serverPlayer, Component.translatable("town.flightsuit.hostile"));
            return InteractionResult.CONSUME;
        }
        if (role.trades()) {
            if (trader != null && trader != player) {
                say(serverPlayer, Component.translatable("town.flightsuit.busy"));
                return InteractionResult.CONSUME;
            }
            getNavigation().stop();
            setTradingPlayer(player);
            openTradingScreen(player, title(), 1);
            return InteractionResult.CONSUME;
        }
        if (request != null) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(request.item()) && held.getCount() >= request.count()) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(request.count());
                }
                ItemStack reward = new ItemStack(TownTrades.money(town), request.pay());
                if (!player.getInventory().add(reward)) {
                    player.drop(reward, false);
                }
                addTrust(player, 2);
                say(serverPlayer, Component.translatable("town.flightsuit.thanks", reward.getHoverName(), request.pay()).withStyle(ChatFormatting.GREEN));
                ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.5D, getZ(), 8, 0.4D, 0.4D, 0.4D, 0.0D);
                playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
                request = null;
                return InteractionResult.CONSUME;
            }
            say(serverPlayer, Component.translatable("town.flightsuit.request", new ItemStack(request.item()).getHoverName(),
                    request.count(), new ItemStack(TownTrades.money(town)).getHoverName(), request.pay()).withStyle(ChatFormatting.YELLOW));
            return InteractionResult.CONSUME;
        }
        say(serverPlayer, Component.translatable("town.flightsuit.line." + role.id() + "." + level().random.nextInt(3)));
        return InteractionResult.CONSUME;
    }

    private void say(ServerPlayer player, Component line) {
        player.sendSystemMessage(Component.translatable("town.flightsuit.says", title(), line));
        getLookControl().setLookAt(player);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.trader = player;
    }

    @Override
    public @Nullable Player getTradingPlayer() {
        return trader;
    }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) {
            offers = TownTrades.offers(getRole(), Math.floorMod(index, shopKinds()), town);
        }
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        if (trader != null && level() instanceof ServerLevel && level().random.nextInt(4) == 0) {
            // Good business makes friends.
            addTrust(trader, 1);
        }
        playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }

    // ---------------------------------------------------------------- the day

    @Override
    public void tick() {
        super.tick();
        if (trader != null && (!trader.isAlive() || trader.distanceToSqr(this) > 64.0D || trader.containerMenu == trader.inventoryMenu)) {
            trader = null;
        }
    }

    /**
     * Where they should be now: home at night and when the town's attacked; their place of work by day; the
     * square (or the park, for children) in the evening. The police walk their beat at any hour.
     */
    private @Nullable BlockPos destination() {
        if (plan == null || home == null) {
            return null;
        }
        TownRole role = getRole();
        long time = level().getDayTime() % 24000L;
        if (underAttack() && role != TownRole.CITY_POLICE) {
            return home;
        }
        if (role == TownRole.CITY_POLICE) {
            return null;
        }
        if (time >= NIGHT || time < DAY_START) {
            return home;
        }
        if (time >= EVENING) {
            return plan.spot(role.isChild() ? TownRole.Place.PLAY : TownRole.Place.SQUARE, index);
        }
        return plan.spot(role.place(), index);
    }

    /** Goes where the hour says, then stays near it doing their work (and the police keep walking). */
    private final class DayGoal extends Goal {
        private @Nullable BlockPos target;
        private int ticks;
        private int wander;

        DayGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return plan != null && trader == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            ticks++;
            TownRole role = getRole();
            if (ticks % 20 == 1) {
                BlockPos want = destination();
                if (role == TownRole.CITY_POLICE && (target == null || closeTo(target, 3.0D))) {
                    want = plan.randomSpot(TownRole.Place.PATROL, getRandom());
                } else if (role == TownRole.CITY_POLICE) {
                    want = target;
                }
                if (want != null && !want.equals(target)) {
                    target = want;
                    wander = 0;
                }
                if (target != null && !closeTo(target, role.isChild() ? 4.0D : 2.0D)) {
                    getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, role.isChild() ? 0.9D : 0.6D);
                } else if (target != null) {
                    atPlace(role);
                }
            }
        }

        private boolean closeTo(BlockPos pos, double reach) {
            return position().distanceToSqr(Vec3.atBottomCenterOf(pos)) < reach * reach;
        }

        /** There: work (a swing, the trade's sound), and amble about a little - the children run around. */
        private void atPlace(TownRole role) {
            long time = level().getDayTime() % 24000L;
            boolean working = time >= DAY_START && time < EVENING && !underAttack();
            if (working && ticks % 80 == 1 && level() instanceof ServerLevel server) {
                swing(InteractionHand.MAIN_HAND);
                SoundEvent sound = switch (role) {
                    case FORT_FARMER -> SoundEvents.HOE_TILL;
                    case FORT_SMITH -> SoundEvents.ANVIL_USE;
                    case FORT_COOK -> SoundEvents.SMOKER_SMOKE;
                    case CITY_SCIENTIST -> SoundEvents.BEACON_AMBIENT;
                    default -> null;
                };
                if (sound != null) {
                    server.playSound(null, blockPosition(), sound, net.minecraft.sounds.SoundSource.NEUTRAL, 0.4F, 1.0F);
                }
            }
            boolean roams = role.isChild() || role.place() == TownRole.Place.FIELD || role.place() == TownRole.Place.SQUARE
                    || time >= EVENING && time < NIGHT;
            if (roams && ++wander % 4 == 0 && getNavigation().isDone()) {
                int reach = role.isChild() ? 6 : 3;
                BlockPos near = target.offset(getRandom().nextInt(reach * 2 + 1) - reach, 0, getRandom().nextInt(reach * 2 + 1) - reach);
                getNavigation().moveTo(near.getX() + 0.5D, near.getY(), near.getZ() + 0.5D, role.isChild() ? 1.0D : 0.5D);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** For TownLife: a fresh person stands where the hour puts them. */
    public @Nullable BlockPos startSpot() {
        BlockPos at = destination();
        return at != null ? at : plan != null ? plan.randomSpot(TownRole.Place.PATROL, getRandom()) : null;
    }

    /** Which roster slot this is (TownLife keeps one of each). */
    public int index() {
        return index;
    }

    public static List<TownRole> rosterOf(TownPlan plan) {
        return plan.roster();
    }
}
