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
import net.minecraft.core.particles.DustParticleOptions;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The seven Dragon Balls of Dragon Ball Earth (DESIGN.md 4-16: 수집 → 소원), the planet's big side quest.
 * They lie 200-900 blocks out from the landing site and appear on the ground when someone comes near; Bulma's
 * radar points to the nearest. With all seven, right-click one: Shenron rises and grants one wish -
 * strength (+2 hearts, up to +6), a better suit battery (one upgrade past Iron Man's), treasure, or senzu beans.
 * Then the balls scatter again and are stone for three days.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DragonBalls {
    private static final String BALLS = "DragonBalls";
    private static final String STONE_UNTIL = "DragonBallsStone";
    private static final int LYING = 0;
    private static final int PLACED = 1;
    private static final int TAKEN = 2;
    private static final int STONE_DAYS = 3;
    private static final long WISH_TICKS = 20L * 120;
    private static final UUID STRENGTH = UUID.fromString("5c1b7d0e-3a52-4c11-9d7e-4f6a2b8e9a71");
    private static final double MAX_STRENGTH = 12.0D;

    /** A summoned Shenron: where, and how long he's been up. */
    private static final class Show {
        final ServerLevel level;
        final Vec3 at;
        int age;

        Show(ServerLevel level, Vec3 at) {
            this.level = level;
            this.at = at;
        }
    }

    private static final List<Show> SHOWS = new ArrayList<>();
    private static final Map<UUID, Long> PENDING = new HashMap<>();

    private DragonBalls() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SHOWS.clear();
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
            double distance = 200.0D + random.nextDouble() * 700.0D;
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
            if (ball.getInt("State") != LYING) {
                continue;
            }
            int x = ball.getInt("X");
            int z = ball.getInt("Z");
            BlockPos column = new BlockPos(x, level.getSeaLevel(), z);
            if (!level.isLoaded(column) || !someoneNear(level, x, z)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = ModBlocks.DRAGON_BALL.get().defaultBlockState().setValue(DragonBallBlock.STARS, ball.getInt("Stars"));
            level.setBlock(pos, state, Block.UPDATE_ALL);
            ball.putInt("Y", y);
            ball.putInt("State", PLACED);
            data.setDirty();
        }
    }

    private static boolean someoneNear(ServerLevel level, int x, int z) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz < 64.0D * 64.0D) {
                return true;
            }
        }
        return false;
    }

    static void pickUp(ServerPlayer player, BlockPos pos, int stars) {
        ServerLevel level = player.serverLevel();
        level.removeBlock(pos, false);
        give(player, DragonBallItem.of(ModItems.DRAGON_BALL.get(), stars));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
        PlanetData data = PlanetData.get(player.server);
        int left = 0;
        for (Tag raw : balls(data.world(Planet.DBZ_EARTH))) {
            CompoundTag ball = (CompoundTag) raw;
            if (ball.getInt("Stars") == stars && ball.getInt("State") == PLACED && ball.getInt("X") == pos.getX() && ball.getInt("Z") == pos.getZ()) {
                ball.putInt("State", TAKEN);
                data.setDirty();
            }
            if (ball.getInt("State") != TAKEN) {
                left++;
            }
        }
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

    /** All seven in the inventory: they're used up, Shenron rises, and the summoner has two minutes to wish. */
    static void summon(ServerPlayer player) {
        int[] slots = new int[8];
        java.util.Arrays.fill(slots, -1);
        int have = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.DRAGON_BALL.get())) {
                int stars = DragonBallItem.stars(stack);
                if (slots[stars] < 0) {
                    slots[stars] = i;
                    have++;
                }
            }
        }
        if (have < 7) {
            player.displayClientMessage(Component.translatable("dragonball.flightsuit.need_all", have), true);
            return;
        }
        for (int stars = 1; stars <= 7; stars++) {
            player.getInventory().getItem(slots[stars]).shrink(1);
        }
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position().add(player.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D).normalize().scale(6.0D));
        SHOWS.add(new Show(level, at));
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
        PENDING.put(player.getUUID(), level.getGameTime() + WISH_TICKS);
        MutableComponent wishes = Component.empty();
        for (String wish : new String[]{"strength", "suit", "treasure", "senzu"}) {
            wishes.append(Component.literal("[").append(Component.translatable("dragonball.flightsuit.wish." + wish)).append("]")
                    .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/shenron " + wish))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("dragonball.flightsuit.wish." + wish + ".hint"))))).append(" ");
        }
        player.sendSystemMessage(Component.translatable("dragonball.flightsuit.ask").withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(wishes);
        // They fly apart and turn to stone.
        ServerLevel planet = player.server.getLevel(Planet.DBZ_EARTH.dimension());
        PlanetData data = PlanetData.get(player.server);
        BlockPos site = data.site(Planet.DBZ_EARTH);
        CompoundTag world = data.world(Planet.DBZ_EARTH);
        if (planet != null && site != null) {
            for (Tag raw : balls(world)) {
                CompoundTag ball = (CompoundTag) raw;
                if (ball.getInt("State") == PLACED) {
                    BlockPos pos = new BlockPos(ball.getInt("X"), ball.getInt("Y"), ball.getInt("Z"));
                    if (planet.isLoaded(pos) && planet.getBlockState(pos).is(ModBlocks.DRAGON_BALL.get())) {
                        planet.removeBlock(pos, false);
                    }
                }
            }
            scatter(world, site, level.random);
        }
        world.putLong(STONE_UNTIL, level.getDayTime() / 24000L + STONE_DAYS);
        data.setDirty();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SHOWS.isEmpty()) {
            return;
        }
        Iterator<Show> it = SHOWS.iterator();
        while (it.hasNext()) {
            Show show = it.next();
            if (++show.age > 200) {
                it.remove();
                continue;
            }
            if (show.age % 4 != 0) {
                continue;
            }
            // A long green body winding up into the sky, a head with red eyes at the top.
            float rise = Math.min(1.0F, show.age / 60.0F);
            for (int k = 0; k < 48; k++) {
                double y = k * 0.7D * rise;
                double angle = k * 0.32D + show.age * 0.05D;
                double radius = 2.5D + Math.sin(k * 0.2D) * 1.2D;
                double x = show.at.x + Math.cos(angle) * radius;
                double z = show.at.z + Math.sin(angle) * radius;
                show.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, show.at.y + y, z, 2, 0.25D, 0.25D, 0.25D, 0.0D);
                if (k % 4 == 0) {
                    show.level.sendParticles(ParticleTypes.GLOW, x, show.at.y + y, z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
                }
            }
            double top = 48 * 0.7D * rise;
            double angle = 47 * 0.32D + show.age * 0.05D;
            double hx = show.at.x + Math.cos(angle) * 2.5D;
            double hz = show.at.z + Math.sin(angle) * 2.5D;
            show.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, hx, show.at.y + top, hz, 12, 1.0D, 0.8D, 1.0D, 0.05D);
            show.level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 2.0F), hx + 0.6D, show.at.y + top + 0.4D, hz, 2, 0.05D, 0.05D, 0.05D, 0.0D);
            show.level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 2.0F), hx - 0.6D, show.at.y + top + 0.4D, hz, 2, 0.05D, 0.05D, 0.05D, 0.0D);
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
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            for (int stars = 1; stars <= 7; stars++) {
                                give(player, DragonBallItem.of(ModItems.DRAGON_BALL.get(), stars));
                            }
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            PlanetData data = PlanetData.get(ctx.getSource().getServer());
                            CompoundTag world = data.world(Planet.DBZ_EARTH);
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
        SHOWS.removeIf(show -> show.level == player.level() && show.at.distanceToSqr(player.position()) < 64.0D * 64.0D);
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
