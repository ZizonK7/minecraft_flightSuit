package com.pfkfks.flightsuit.planet.dbz;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.Planet;
import com.pfkfks.flightsuit.planet.PlanetData;
import com.pfkfks.flightsuit.registry.ModBlocks;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitEnergy;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The seven Dragon Balls of Dragon Ball Earth (DESIGN.md 4-16: 수집 → 소원), the planet's big side quest.
 * They lie 200-900 blocks out from the landing site and appear on the ground when someone comes near; Bulma's
 * radar points to the nearest. Set all seven down together on the planet (within a few blocks of each other):
 * they glow and the ground rumbles, then Shenron rises from them and grants one wish - strength (+2 hearts,
 * up to +6), a better suit battery (one upgrade past Iron Man's), treasure, or senzu beans. Then the balls
 * fly apart and are stone for three days.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DragonBalls {
    private static final String BALLS = "DragonBalls";
    private static final String STONE_UNTIL = "DragonBallsStone";
    private static final int LYING = 0;
    private static final int PLACED = 1;
    private static final int TAKEN = 2;
    private static final int STONE_DAYS = 3;
    private static final double SCATTER_MIN = 80.0D;
    private static final double SCATTER_MAX = 320.0D;
    /** How near someone must come for a ball to settle on the ground (and its light to show). */
    private static final double APPEAR_RANGE = 96.0D;
    /** How far off a ball's light column can be seen. */
    private static final double BEACON_RANGE = 200.0D;
    private static final long WISH_TICKS = 20L * 120;
    private static final UUID STRENGTH = UUID.fromString("5c1b7d0e-3a52-4c11-9d7e-4f6a2b8e9a71");
    private static final double MAX_STRENGTH = 12.0D;

    /** A summoned Shenron: where, the dragon himself, and how long he's been up. */
    private static final class Show {
        final ServerLevel level;
        final Vec3 at;
        final ShenronEntity dragon;
        int age;

        Show(ServerLevel level, Vec3 at, ShenronEntity dragon) {
            this.level = level;
            this.at = at;
            this.dragon = dragon;
        }
    }

    /** Seven balls set down together: glowing for a few seconds before Shenron comes. */
    private static final class Gathering {
        final ServerLevel level;
        final Vec3 at;
        final List<BlockPos> balls;
        final UUID summoner;
        int age;

        Gathering(ServerLevel level, Vec3 at, List<BlockPos> balls, UUID summoner) {
            this.level = level;
            this.at = at;
            this.balls = balls;
            this.summoner = summoner;
        }
    }

    /** How close together the seven must be set down. */
    private static final int GATHER_RADIUS = 4;
    private static final int GATHER_TICKS = 60;

    private static final List<Show> SHOWS = new ArrayList<>();
    private static final List<Gathering> GATHERINGS = new ArrayList<>();
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    private DragonBalls() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SHOWS.clear();
        GATHERINGS.clear();
        PENDING.clear();
    }

    // ---------------------------------------------------------------- where they are

    private static ListTag balls(CompoundTag world) {
        return world.getList(BALLS, Tag.TAG_COMPOUND);
    }

    private static void scatter(CompoundTag world, BlockPos site, RandomSource random) {
        ListTag list = new ListTag();
        for (int stars = 1; stars <= 7; stars++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            // 80-320 out (after the M16 test: 200-900 made them far too hard to find).
            double distance = SCATTER_MIN + random.nextDouble() * (SCATTER_MAX - SCATTER_MIN);
            CompoundTag ball = new CompoundTag();
            ball.putInt("Stars", stars);
            ball.putInt("X", site.getX() + Mth.floor(Math.cos(angle) * distance));
            ball.putInt("Z", site.getZ() + Mth.floor(Math.sin(angle) * distance));
            ball.putInt("Y", Integer.MIN_VALUE);
            ball.putInt("State", LYING);
            list.add(ball);
        }
        world.put(BALLS, list);
    }

    /** From DbzEarth every second: scatter them once, and set them on the ground where someone comes near. */
    static void tick(ServerLevel level, PlanetData data, BlockPos site) {
        CompoundTag world = data.world(Planet.DBZ_EARTH);
        if (!world.contains(BALLS)) {
            scatter(world, site, level.random);
            data.setDirty();
        }
        if (level.getDayTime() / 24000L < world.getLong(STONE_UNTIL)) {
            return;
        }
        for (Tag raw : balls(world)) {
            CompoundTag ball = (CompoundTag) raw;
            if (ball.getInt("State") == PLACED) {
                // Its block is gone (creative break, a Wither...): it lies out there again.
                BlockPos at = new BlockPos(ball.getInt("X"), ball.getInt("Y"), ball.getInt("Z"));
                if (level.isLoaded(at) && !level.getBlockState(at).is(ModBlocks.DRAGON_BALL.get())) {
                    ball.putInt("State", LYING);
                    data.setDirty();
                } else {
                    beacon(level, at);
                }
                continue;
            }
            if (ball.getInt("State") != LYING) {
                continue;
            }
            int x = ball.getInt("X");
            int z = ball.getInt("Z");
            double out = Math.sqrt((double) (x - site.getX()) * (x - site.getX()) + (double) (z - site.getZ()) * (z - site.getZ()));
            if (out > SCATTER_MAX + 40.0D) {
                // Scattered before the search was made easier: it rolls in closer.
                double angle = level.random.nextDouble() * Math.PI * 2.0D;
                double distance = SCATTER_MIN + level.random.nextDouble() * (SCATTER_MAX - SCATTER_MIN);
                ball.putInt("X", site.getX() + Mth.floor(Math.cos(angle) * distance));
                ball.putInt("Z", site.getZ() + Mth.floor(Math.sin(angle) * distance));
                data.setDirty();
                continue;
            }
            BlockPos column = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.isLoaded(column) || !someoneNear(level, x, z)) {
                continue;
            }
            BlockPos pos = firmGround(level, x, z);
            if (pos == null) {
                // Water or lava there: it rolls on a little.
                ball.putInt("X", x + 12);
                data.setDirty();
                continue;
            }
            int y = pos.getY();
            BlockState state = ModBlocks.DRAGON_BALL.get().defaultBlockState().setValue(DragonBallBlock.STARS, ball.getInt("Stars"));
            level.setBlock(pos, state, Block.UPDATE_ALL);
            ball.putInt("Y", y);
            ball.putInt("State", PLACED);
            data.setDirty();
        }
    }

    /**
     * A ball on the ground glows: an orange column of light over it, seen from far off (after the M16 test -
     * finding them was too hard). Sent to each player within BEACON_RANGE, past the usual particle distance.
     */
    private static void beacon(ServerLevel level, BlockPos at) {
        net.minecraft.core.particles.DustParticleOptions glow = new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(1.0F, 0.6F, 0.1F), 2.5F);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.getX() + 0.5D, player.getY(), at.getZ() + 0.5D) < BEACON_RANGE * BEACON_RANGE) {
                for (int k = 0; k < 12; k++) {
                    level.sendParticles(player, glow, true, at.getX() + 0.5D, at.getY() + 1.0D + k * 2.5D, at.getZ() + 0.5D,
                            2, 0.08D, 1.0D, 0.08D, 0.0D);
                }
            }
        }
    }

    /** The first spot above solid ground (through trees and plants); null over water or lava. */
    static @org.jetbrains.annotations.Nullable BlockPos firmGround(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        while (pos.getY() > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                return null;
            }
            if (!state.is(net.minecraft.tags.BlockTags.LOGS) && !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.canBeReplaced() && !state.isAir()) {
                return pos.above().immutable();
            }
            pos.move(0, -1, 0);
        }
        return null;
    }

    private static boolean someoneNear(ServerLevel level, int x, int z) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz < APPEAR_RANGE * APPEAR_RANGE) {
                return true;
            }
        }
        return false;
    }

    static void pickUp(ServerPlayer player, BlockPos pos, int stars) {
        ServerLevel level = player.serverLevel();
        level.removeBlock(pos, false);
        PlanetData data = PlanetData.get(player.server);
        int left = 0;
        boolean real = false;
        for (Tag raw : balls(data.world(Planet.DBZ_EARTH))) {
            CompoundTag ball = (CompoundTag) raw;
            if (ball.getInt("Stars") == stars && ball.getInt("State") == PLACED && ball.getInt("X") == pos.getX() && ball.getInt("Z") == pos.getZ()) {
                ball.putInt("State", TAKEN);
                data.setDirty();
                real = true;
            }
            if (ball.getInt("State") != TAKEN) {
                left++;
            }
        }
        if (!real) {
            // A ball from before a scatter: it crumbles - only the balls on record are real.
            player.displayClientMessage(Component.translatable("dragonball.flightsuit.crumbled"), true);
            return;
        }
        give(player, DragonBallItem.of(ModItems.DRAGON_BALL.get(), stars));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
        player.sendSystemMessage(Component.translatable("dragonball.flightsuit.got", stars, 7 - left).withStyle(ChatFormatting.GOLD));
    }

    /** What the radar shows. */
    static Component radar(ServerPlayer player) {
        if (player.level().dimension() != Planet.DBZ_EARTH.dimension()) {
            return Component.translatable("dragonball.flightsuit.radar_none").withStyle(ChatFormatting.GRAY);
        }
        CompoundTag world = PlanetData.get(player.server).world(Planet.DBZ_EARTH);
        long day = player.level().getDayTime() / 24000L;
        long stone = world.getLong(STONE_UNTIL);
        if (day < stone) {
            return Component.translatable("dragonball.flightsuit.radar_stone", stone - day).withStyle(ChatFormatting.GRAY);
        }
        CompoundTag nearest = null;
        double best = Double.MAX_VALUE;
        int out = 0;
        for (Tag raw : balls(world)) {
            CompoundTag ball = (CompoundTag) raw;
            if (ball.getInt("State") == TAKEN) {
                continue;
            }
            out++;
            double dx = ball.getInt("X") - player.getX();
            double dz = ball.getInt("Z") - player.getZ();
            double d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                nearest = ball;
            }
        }
        if (nearest == null) {
            return Component.translatable("dragonball.flightsuit.radar_all").withStyle(ChatFormatting.GOLD);
        }
        double dx = nearest.getInt("X") - player.getX();
        double dz = nearest.getInt("Z") - player.getZ();
        int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8);
        return Component.translatable("dragonball.flightsuit.radar", nearest.getInt("Stars"), (int) Math.sqrt(best),
                Component.translatable("edith.flightsuit.dir." + sector), out).withStyle(ChatFormatting.YELLOW);
    }

    // ---------------------------------------------------------------- Shenron

    /**
     * A ball in hand set down on the planet (DragonBallItem.useOn): it's placed where it was put (on firm ground),
     * on record, and if all seven now lie together, they start to glow.
     */
    static InteractionResult place(ServerPlayer player, InteractionHand hand, ItemStack stack, BlockPos clicked, Direction face) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Planet.DBZ_EARTH.dimension()) {
            player.displayClientMessage(Component.translatable("dragonball.flightsuit.place_planet"), true);
            return InteractionResult.FAIL;
        }
        BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
        BlockPos below = target.below();
        if (!level.getBlockState(target).canBeReplaced() || !level.getFluidState(target).isEmpty()
                || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            player.displayClientMessage(Component.translatable("dragonball.flightsuit.place_flat"), true);
            return InteractionResult.FAIL;
        }
        int stars = DragonBallItem.stars(stack);
        PlanetData data = PlanetData.get(player.server);
        CompoundTag record = null;
        for (Tag raw : balls(data.world(Planet.DBZ_EARTH))) {
            CompoundTag ball = (CompoundTag) raw;
            if (ball.getInt("Stars") == stars && ball.getInt("State") == TAKEN) {
                record = ball;
            }
        }
        // Take it out of the hand by replacing the stack (creative mode puts the count of the old one back).
        ItemStack rest = stack.copy();
        rest.shrink(1);
        player.setItemInHand(hand, rest);
        if (record == null) {
            // A ball from before a scatter: it crumbles - only the balls on record are real.
            player.displayClientMessage(Component.translatable("dragonball.flightsuit.crumbled"), true);
            return InteractionResult.SUCCESS;
        }
        level.setBlock(target, ModBlocks.DRAGON_BALL.get().defaultBlockState().setValue(DragonBallBlock.STARS, stars), Block.UPDATE_ALL);
        record.putInt("X", target.getX());
        record.putInt("Y", target.getY());
        record.putInt("Z", target.getZ());
        record.putInt("State", PLACED);
        data.setDirty();
        level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.GLOW, target.getX() + 0.5D, target.getY() + 0.3D, target.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.0D);
        List<BlockPos> together = gathered(level, data, target);
        player.displayClientMessage(Component.translatable("dragonball.flightsuit.placed", stars, together.size()).withStyle(ChatFormatting.GOLD), true);
        Vec3 here = Vec3.atCenterOf(target);
        if (together.size() == 7 && GATHERINGS.stream().noneMatch(g -> g.level == level && g.at.distanceToSqr(here) < 100.0D)) {
            Vec3 center = Vec3.ZERO;
            for (BlockPos pos : together) {
                center = center.add(Vec3.atBottomCenterOf(pos));
            }
            center = center.scale(1.0D / together.size());
            GATHERINGS.add(new Gathering(level, center, together, player.getUUID()));
            for (ServerPlayer near : level.players()) {
                if (near.distanceToSqr(center) < 64.0D * 64.0D) {
                    near.sendSystemMessage(Component.translatable("dragonball.flightsuit.gathering").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The placed balls (on record, still there) lying within reach of {@code around} - one per star. */
    private static List<BlockPos> gathered(ServerLevel level, PlanetData data, BlockPos around) {
        BlockPos[] byStars = new BlockPos[8];
        for (Tag raw : balls(data.world(Planet.DBZ_EARTH))) {
            CompoundTag ball = (CompoundTag) raw;
            if (ball.getInt("State") != PLACED) {
                continue;
            }
            BlockPos pos = new BlockPos(ball.getInt("X"), ball.getInt("Y"), ball.getInt("Z"));
            if (Math.abs(pos.getX() - around.getX()) <= GATHER_RADIUS && Math.abs(pos.getZ() - around.getZ()) <= GATHER_RADIUS
                    && Math.abs(pos.getY() - around.getY()) <= 3 && level.getBlockState(pos).is(ModBlocks.DRAGON_BALL.get())) {
                byStars[ball.getInt("Stars")] = pos;
            }
        }
        List<BlockPos> found = new ArrayList<>();
        for (int stars = 1; stars <= 7; stars++) {
            if (byStars[stars] != null) {
                found.add(byStars[stars]);
            }
        }
        return found;
    }

    /** The glow before the dragon: light rising from each ball, a deepening rumble; it fails if one is taken away. */
    private static void tickGatherings() {
        Iterator<Gathering> it = GATHERINGS.iterator();
        while (it.hasNext()) {
            Gathering gathering = it.next();
            ServerLevel level = gathering.level;
            boolean intact = true;
            for (BlockPos pos : gathering.balls) {
                if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.DRAGON_BALL.get())) {
                    intact = false;
                }
            }
            if (!intact) {
                it.remove();
                continue;
            }
            gathering.age++;
            if (gathering.age % 4 == 0) {
                for (BlockPos pos : gathering.balls) {
                    level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 0.5D,
                            1 + gathering.age / 20, 0.1D, 0.1D, 0.1D, 0.02D + gathering.age * 0.001D);
                }
            }
            if (gathering.age == 1) {
                level.playSound(null, gathering.at.x, gathering.at.y, gathering.at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 2.0F, 0.6F);
            }
            if (gathering.age % 20 == 10) {
                level.playSound(null, gathering.at.x, gathering.at.y, gathering.at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                        0.4F + gathering.age / 100.0F, 0.5F);
            }
            if (gathering.age >= GATHER_TICKS) {
                it.remove();
                summon(level, gathering.at, level.getServer().getPlayerList().getPlayer(gathering.summoner));
            }
        }
    }

    /**
     * Shenron rises from the gathered balls at {@code at}; whoever set the last one down has two minutes to
     * wish. The balls fly apart into the sky (and the records scatter) and are stone for three days.
     */
    private static void summon(ServerLevel level, Vec3 at, @org.jetbrains.annotations.Nullable ServerPlayer summoner) {
        // He faces whoever set the last ball down.
        float yaw = summoner == null ? level.random.nextFloat() * 360.0F
                : (float) (Math.toDegrees(Math.atan2(summoner.getZ() - at.z, summoner.getX() - at.x)) - 90.0D);
        SHOWS.add(new Show(level, at, ShenronEntity.summon(level, at, yaw)));
        for (int i = 0; i < 3; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(at.x + level.random.nextInt(11) - 5, at.y, at.z + level.random.nextInt(11) - 5);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 3.0F, 0.7F);
        for (ServerPlayer near : level.players()) {
            if (near.distanceToSqr(at) < 96.0D * 96.0D) {
                near.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                near.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("dragonball.flightsuit.shenron").withStyle(ChatFormatting.GREEN)));
                near.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("dragonball.flightsuit.speak")));
            }
        }
        if (summoner != null) {
            PENDING.put(summoner.getUUID(), level.getGameTime() + WISH_TICKS);
            MutableComponent wishes = Component.empty();
            for (String wish : new String[]{"strength", "suit", "treasure", "senzu"}) {
                wishes.append(Component.literal("[").append(Component.translatable("dragonball.flightsuit.wish." + wish)).append("]")
                        .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/shenron " + wish))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable("dragonball.flightsuit.wish." + wish + ".hint"))))).append(" ");
            }
            summoner.sendSystemMessage(Component.translatable("dragonball.flightsuit.ask").withStyle(ChatFormatting.GREEN));
            summoner.sendSystemMessage(wishes);
        }
        // They fly apart into the sky and turn to stone.
        PlanetData data = PlanetData.get(level.getServer());
        BlockPos site = data.site(Planet.DBZ_EARTH);
        CompoundTag world = data.world(Planet.DBZ_EARTH);
        for (Tag raw : balls(world)) {
            CompoundTag ball = (CompoundTag) raw;
            if (ball.getInt("State") == PLACED) {
                BlockPos pos = new BlockPos(ball.getInt("X"), ball.getInt("Y"), ball.getInt("Z"));
                if (level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.DRAGON_BALL.get())) {
                    level.removeBlock(pos, false);
                    for (int k = 0; k < 12; k++) {
                        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 0.5D + k * 1.5D, pos.getZ() + 0.5D,
                                2, 0.05D, 0.2D, 0.05D, 0.0D);
                    }
                }
            }
        }
        if (site != null) {
            scatter(world, site, level.random);
        }
        world.putLong(STONE_UNTIL, level.getDayTime() / 24000L + STONE_DAYS);
        data.setDirty();
    }

    /** A ball that fell out of the world is lost - so it turns up out there again. */
    @SubscribeEvent
    public static void onLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item) || event.getLevel().isClientSide()
                || !item.getItem().is(ModItems.DRAGON_BALL.get()) || item.getY() > event.getLevel().getMinBuildHeight() - 32) {
            return;
        }
        PlanetData data = PlanetData.get(item.getServer());
        int stars = DragonBallItem.stars(item.getItem());
        for (Tag raw : balls(data.world(Planet.DBZ_EARTH))) {
            CompoundTag ball = (CompoundTag) raw;
            if (ball.getInt("Stars") == stars && ball.getInt("State") == TAKEN) {
                ball.putInt("State", LYING);
                data.setDirty();
                return;
            }
        }
    }

    /** Shenron's strength wish stays with you through death (attribute modifiers aren't copied to the new body). */
    @SubscribeEvent
    public static void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        AttributeInstance before = event.getOriginal().getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance after = event.getEntity().getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier wish = before == null ? null : before.getModifier(STRENGTH);
        if (wish != null && after != null && after.getModifier(STRENGTH) == null) {
            after.addPermanentModifier(new AttributeModifier(STRENGTH, "Shenron's wish", wish.getAmount(), AttributeModifier.Operation.ADDITION));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!GATHERINGS.isEmpty()) {
            tickGatherings();
        }
        if (SHOWS.isEmpty()) {
            return;
        }
        // Shenron himself is an entity (ShenronEntity); he stays until the wish, or until the time to wish runs out.
        Iterator<Show> it = SHOWS.iterator();
        while (it.hasNext()) {
            Show show = it.next();
            if (show.dragon.isRemoved()) {
                it.remove();
                continue;
            }
            if (++show.age > WISH_TICKS) {
                show.dragon.depart();
            }
        }
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("shenron").then(Commands.argument("wish", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"strength", "suit", "treasure", "senzu"}, builder))
                .executes(ctx -> wish(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "wish")) ? 1 : 0)));
        event.getDispatcher().register(Commands.literal("flightsuit")
                // Every registration of the root carries the op check: Brigadier keeps whichever came first.
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dragonballs")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("give").executes(ctx -> {
                            // The real seven: lifted from wherever they are (and out of stone), on record as taken.
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            PlanetData data = PlanetData.get(ctx.getSource().getServer());
                            CompoundTag world = data.world(Planet.DBZ_EARTH);
                            BlockPos site = data.site(Planet.DBZ_EARTH);
                            if (!world.contains(BALLS)) {
                                scatter(world, site != null ? site : BlockPos.ZERO, player.getRandom());
                            }
                            ServerLevel planet = ctx.getSource().getServer().getLevel(Planet.DBZ_EARTH.dimension());
                            for (Tag raw : balls(world)) {
                                CompoundTag ball = (CompoundTag) raw;
                                BlockPos at = new BlockPos(ball.getInt("X"), ball.getInt("Y"), ball.getInt("Z"));
                                if (planet != null && ball.getInt("State") == PLACED && planet.isLoaded(at)
                                        && planet.getBlockState(at).is(ModBlocks.DRAGON_BALL.get())) {
                                    planet.removeBlock(at, false);
                                }
                                if (ball.getInt("State") != TAKEN) {
                                    ball.putInt("State", TAKEN);
                                    give(player, DragonBallItem.of(ModItems.DRAGON_BALL.get(), ball.getInt("Stars")));
                                }
                            }
                            world.remove(STONE_UNTIL);
                            data.setDirty();
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            PlanetData data = PlanetData.get(ctx.getSource().getServer());
                            CompoundTag world = data.world(Planet.DBZ_EARTH);
                            ServerLevel planet = ctx.getSource().getServer().getLevel(Planet.DBZ_EARTH.dimension());
                            for (Tag raw : balls(world)) {
                                CompoundTag ball = (CompoundTag) raw;
                                BlockPos at = new BlockPos(ball.getInt("X"), ball.getInt("Y"), ball.getInt("Z"));
                                if (planet != null && ball.getInt("State") == PLACED && planet.isLoaded(at)
                                        && planet.getBlockState(at).is(ModBlocks.DRAGON_BALL.get())) {
                                    planet.removeBlock(at, false);
                                }
                            }
                            world.remove(BALLS);
                            world.remove(STONE_UNTIL);
                            data.setDirty();
                            ctx.getSource().sendSuccess(() -> Component.translatable("dragonball.flightsuit.reset"), false);
                            return 1;
                        }))));
    }

    private static boolean wish(ServerPlayer player, String wish) {
        Long until = PENDING.get(player.getUUID());
        if (until == null || player.level().getGameTime() > until) {
            PENDING.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable("dragonball.flightsuit.no_wish").withStyle(ChatFormatting.GRAY));
            return false;
        }
        switch (wish) {
            case "strength" -> {
                AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
                if (health == null) {
                    return false;
                }
                AttributeModifier old = health.getModifier(STRENGTH);
                double amount = (old == null ? 0.0D : old.getAmount()) + 4.0D;
                if (amount > MAX_STRENGTH) {
                    player.sendSystemMessage(Component.translatable("dragonball.flightsuit.wish_too_much").withStyle(ChatFormatting.YELLOW));
                    return false;
                }
                health.removeModifier(STRENGTH);
                health.addPermanentModifier(new AttributeModifier(STRENGTH, "Shenron's wish", amount, AttributeModifier.Operation.ADDITION));
                player.setHealth(player.getMaxHealth());
            }
            case "suit" -> {
                ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
                if (!(chest.getItem() instanceof SuitArmorItem)) {
                    player.sendSystemMessage(Component.translatable("dragonball.flightsuit.wish_no_suit").withStyle(ChatFormatting.YELLOW));
                    return false;
                }
                int level = SuitEnergy.upgradeLevel(chest);
                if (level >= SuitEnergy.MAX_UPGRADE) {
                    player.sendSystemMessage(Component.translatable("dragonball.flightsuit.wish_too_much").withStyle(ChatFormatting.YELLOW));
                    return false;
                }
                SuitEnergy.setUpgradeLevel(chest, level + 1);
                SuitEnergy.set(chest, SuitEnergy.capacity(chest));
            }
            case "treasure" -> {
                give(player, new ItemStack(Items.DIAMOND, 16));
                give(player, new ItemStack(Items.NETHERITE_INGOT, 2));
                give(player, new ItemStack(ModItems.ARC_REACTOR.get(), 3));
            }
            case "senzu" -> give(player, new ItemStack(ModItems.SENZU_BEAN.get(), 6));
            default -> {
                return false;
            }
        }
        PENDING.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable("dragonball.flightsuit.granted").withStyle(ChatFormatting.GREEN));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
        for (Show show : SHOWS) {
            if (show.level == player.level() && show.at.distanceToSqr(player.position()) < 64.0D * 64.0D) {
                show.dragon.depart();
            }
        }
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
