package com.pfkfks.flightsuit.planet;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import com.pfkfks.flightsuit.planet.dbz.DbzEarth;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import com.pfkfks.flightsuit.planet.dbz.DbzLandmarks;
import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Each planet's main story (DESIGN.md 4-16: 오픈 월드 + 메인 스토리). Dragon Ball Earth, chapter 1 - the
 * Saiyan saga: land → Bulma at Capsule Corp → Goku at Kame House → Raditz at the crater (Goku and Piccolo fight
 * beside you; the scouter is yours) → a day later the Saibamen and Nappa → Vegeta → chapter clear. Chapters 2-4
 * (Namek, the androids and Cell, Majin Buu) follow on from there. Every fight, with its cutscenes, runs in DbzSaga.
 * "/planet" shows what's next and which way it is.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class PlanetStory {
    /**
     * The Dragon Ball story's points, in the order they come (declaration order = progress). Since M17 saved by name,
     * so new points can go in the middle (NAPPA_BEATEN did); an ordinal saved before is read in the old order
     * ({@link #LEGACY}).
     */
    public enum DbzStage {
        ARRIVED, MET_BULMA, MET_GOKU, RADITZ_BEATEN,
        /** M17: the Saibamen and Nappa are beaten (Yamcha and Piccolo fell); Vegeta comes at once. */
        NAPPA_BEATEN,
        SAIYANS_BEATEN,
        // Chapter 2 (Namek), 3 (androids, Cell), 4 (Majin Buu).
        NAMEK_OPEN, MET_DENDE, ZARBON_BEATEN, GINYU_BEATEN, FRIEZA_BEATEN,
        MET_TRUNKS, ANDROIDS_BEATEN, CELL_BEATEN,
        BUU_BEATEN, KID_BUU_BEATEN;

        /** Whether this is at least as far as {@code other}. */
        public boolean atLeast(DbzStage other) {
            return ordinal() >= other.ordinal();
        }
    }

    /** The order the stages were saved in (by ordinal) before M17. */
    private static final DbzStage[] LEGACY = {DbzStage.ARRIVED, DbzStage.MET_BULMA, DbzStage.MET_GOKU, DbzStage.RADITZ_BEATEN,
            DbzStage.SAIYANS_BEATEN, DbzStage.NAMEK_OPEN, DbzStage.MET_DENDE, DbzStage.ZARBON_BEATEN, DbzStage.GINYU_BEATEN,
            DbzStage.FRIEZA_BEATEN, DbzStage.MET_TRUNKS, DbzStage.ANDROIDS_BEATEN, DbzStage.CELL_BEATEN, DbzStage.BUU_BEATEN,
            DbzStage.KID_BUU_BEATEN};

    /** Whether the player has got as far as {@code stage}. */
    public static boolean reached(ServerPlayer player, DbzStage stage) {
        DbzStage theirs = dbzStage(player);
        return theirs != null && theirs.atLeast(stage);
    }

    /** Namek opens on the launch pad once Bulma has the coordinates (chapter 1 done, then a word with her). */
    public static boolean canFlyTo(ServerPlayer player, Planet planet) {
        return planet != Planet.NAMEK || reached(player, DbzStage.NAMEK_OPEN);
    }

    private PlanetStory() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("planet").executes(ctx -> {
            objective(ctx.getSource().getPlayerOrException());
            return 1;
        }).then(Commands.literal("replay")
                // M17: watch a cutscene again, on the spot (actors of its own, the story untouched).
                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                PlanetData.get(ctx.getSource().getServer()).traveller(ctx.getSource().getPlayerOrException().getUUID()).seenCutscenes, builder))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                            boolean seen = PlanetData.get(player.server).traveller(player.getUUID()).seenCutscenes.contains(id);
                            if (!seen && !player.hasPermissions(2)) {
                                player.sendSystemMessage(Component.translatable("story.flightsuit.replay_unseen", id).withStyle(ChatFormatting.GRAY));
                                return 0;
                            }
                            if (!com.pfkfks.flightsuit.cutscene.CutsceneRunner.replay(player, id)) {
                                player.sendSystemMessage(Component.translatable("story.flightsuit.replay_none", id).withStyle(ChatFormatting.GRAY));
                                return 0;
                            }
                            return 1;
                        }))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    java.util.Set<String> seen = PlanetData.get(player.server).traveller(player.getUUID()).seenCutscenes;
                    player.sendSystemMessage(Component.translatable("story.flightsuit.replay_list", seen.isEmpty() ? "-" : String.join(", ", seen))
                            .withStyle(ChatFormatting.AQUA));
                    return 1;
                })));
    }

    public static @Nullable DbzStage dbzStage(ServerPlayer player) {
        PlanetData data = PlanetData.get(player.server);
        PlanetData.Traveller traveller = data.traveller(player.getUUID());
        String name = traveller.stageName.get(Planet.DBZ_EARTH);
        if (name != null) {
            for (DbzStage stage : DbzStage.values()) {
                if (stage.name().equals(name)) {
                    return stage;
                }
            }
        }
        Integer legacy = traveller.stage.get(Planet.DBZ_EARTH);
        if (legacy == null) {
            return null;
        }
        // Saved by ordinal before M17: read it in the old order, and keep it by name from now on.
        DbzStage stage = LEGACY[Math.max(0, Math.min(LEGACY.length - 1, legacy))];
        traveller.stageName.put(Planet.DBZ_EARTH, stage.name());
        traveller.stage.remove(Planet.DBZ_EARTH);
        data.setDirty();
        return stage;
    }

    public static void setStage(ServerPlayer player, DbzStage stage) {
        PlanetData data = PlanetData.get(player.server);
        data.traveller(player.getUUID()).stageName.put(Planet.DBZ_EARTH, stage.name());
        data.traveller(player.getUUID()).stage.remove(Planet.DBZ_EARTH);
        data.setDirty();
    }

    public static void onLanded(ServerPlayer player, Planet planet) {
        if (planet == Planet.TITAN) {
            player.sendSystemMessage(Component.translatable("story.flightsuit.titan.welcome").withStyle(ChatFormatting.LIGHT_PURPLE));
            com.pfkfks.flightsuit.thanos.ThanosSaga.objective(player);
            return;
        }
        if (planet == Planet.NAMEK) {
            player.sendSystemMessage(Component.translatable("story.flightsuit.namek.welcome").withStyle(ChatFormatting.GREEN));
            objective(player);
            return;
        }
        if (planet != Planet.DBZ_EARTH) {
            return;
        }
        if (dbzStage(player) == null) {
            setStage(player, DbzStage.ARRIVED);
            player.sendSystemMessage(Component.translatable("story.flightsuit.dbz_earth.welcome").withStyle(ChatFormatting.YELLOW));
        }
        objective(player);
    }

    /** What to do next, and which way. */
    public static void objective(ServerPlayer player) {
        int stones = com.pfkfks.flightsuit.thanos.ThanosSaga.stones(player.server, player.getUUID());
        if (player.level().dimension() == Planet.TITAN.dimension() || stones != 0) {
            com.pfkfks.flightsuit.thanos.ThanosSaga.objective(player);
            if (player.level().dimension() == Planet.TITAN.dimension()) {
                return;
            }
        }
        DbzStage stage = dbzStage(player);
        if (stage == null) {
            player.sendSystemMessage(Component.translatable("story.flightsuit.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        Goal goal = goal(player);
        Component where = Component.literal("");
        if (goal != null) {
            if (goal.pos() != null && player.level().dimension() == goal.planet().dimension()) {
                double dx = goal.pos().getX() - player.getX();
                double dz = goal.pos().getZ() - player.getZ();
                int distance = (int) Math.sqrt(dx * dx + dz * dz);
                int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8);
                where = Component.translatable("edith.flightsuit.where", distance, Component.translatable("edith.flightsuit.dir." + sector));
            } else {
                where = goal.planet().displayName();
            }
        }
        long beat = PlanetData.get(player.server).traveller(player.getUUID()).nextBeatDay;
        player.sendSystemMessage(Component.translatable("story.flightsuit.dbz_earth.goal." + stage.name().toLowerCase(java.util.Locale.ROOT), where, beat)
                .withStyle(ChatFormatting.AQUA));
    }

    /** Where the Dragon Ball story goes next: on which planet, the spot (null before that planet's been visited), what's there. */
    public record Goal(Planet planet, @Nullable BlockPos pos, Component label) {
    }

    /** The next sight of the Dragon Ball story for this player; null before it starts and once it's over. */
    public static @Nullable Goal goal(ServerPlayer player) {
        DbzStage stage = dbzStage(player);
        if (stage == null) {
            return null;
        }
        DbzLandmarks target = switch (stage) {
            case ARRIVED, SAIYANS_BEATEN, FRIEZA_BEATEN -> DbzLandmarks.CAPSULE_CORP;
            case MET_BULMA -> DbzLandmarks.KAME_HOUSE;
            case MET_GOKU, RADITZ_BEATEN, NAPPA_BEATEN -> DbzLandmarks.CRATER;
            default -> null;
        };
        // Chapters 2-4: the scene's sight (DbzSaga), on Namek or back on Earth.
        com.pfkfks.flightsuit.planet.dbz.DbzSaga.Site scene = switch (stage) {
            case NAMEK_OPEN, MET_DENDE -> com.pfkfks.flightsuit.planet.dbz.DbzSaga.Site.NAMEK_VILLAGE;
            case ZARBON_BEATEN -> com.pfkfks.flightsuit.planet.dbz.DbzSaga.Site.GINYU_FIELD;
            case GINYU_BEATEN -> com.pfkfks.flightsuit.planet.dbz.DbzSaga.Site.FRIEZA_SHIP;
            case MET_TRUNKS -> com.pfkfks.flightsuit.planet.dbz.DbzSaga.Site.ANDROID_ROAD;
            case ANDROIDS_BEATEN -> com.pfkfks.flightsuit.planet.dbz.DbzSaga.Site.CELL_RING;
            case CELL_BEATEN -> com.pfkfks.flightsuit.planet.dbz.DbzSaga.Site.BABIDI_SHIP;
            case BUU_BEATEN -> com.pfkfks.flightsuit.planet.dbz.DbzSaga.Site.WASTELAND;
            default -> null;
        };
        if (target == null && scene == null) {
            return null;
        }
        PlanetData data = PlanetData.get(player.server);
        Planet planet = scene != null ? scene.planet() : Planet.DBZ_EARTH;
        BlockPos site = data.site(planet);
        BlockPos pos = null;
        if (site != null) {
            if (scene != null) {
                pos = com.pfkfks.flightsuit.planet.dbz.DbzSaga.target(data, scene);
            } else {
                BlockPos built = DbzEarth.center(data.world(Planet.DBZ_EARTH), target);
                pos = built != null ? built : target.around(site);
            }
        }
        return new Goal(planet, pos, Component.translatable("story.flightsuit.marker." + stage.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** The marker last sent to each player (so it's only sent when it changes). */
    private static final java.util.Map<java.util.UUID, String> MARKERS = new java.util.HashMap<>();

    /**
     * Once a second on the Dragon Ball planets (after the 4th test round - finding the way was hard): the HUD
     * marker points at the next sight, or at your ship when the story's on another planet, and a gold light column
     * stands over it so it's seen from far off.
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 7) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Planet here = Planet.of(level.dimension());
        BlockPos pos = null;
        Component label = null;
        String key = "";
        if (here == Planet.DBZ_EARTH || here == Planet.NAMEK) {
            Goal goal = goal(player);
            if (goal != null && goal.planet() == here && goal.pos() != null) {
                pos = goal.pos();
                label = goal.label();
                key = label.getString();
            } else if (goal != null && PlanetData.get(player.server).site(here) != null) {
                pos = PlanetData.get(player.server).site(here);
                label = Component.translatable("story.flightsuit.marker.ship", goal.planet().displayName());
                key = "ship:" + goal.planet().id();
            }
        }
        if (pos != null && pos.getY() == 0 && level.hasChunkAt(pos)) {
            // A sight not built yet: its ground.
            pos = new BlockPos(pos.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()), pos.getZ());
        }
        if (pos != null) {
            key = level.dimension().location() + "|" + pos.asLong() + "|" + key;
        }
        if (!key.equals(MARKERS.get(player.getUUID()))) {
            MARKERS.put(player.getUUID(), key);
            com.pfkfks.flightsuit.network.ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    pos == null ? com.pfkfks.flightsuit.network.StoryGoalS2CPacket.none()
                            : new com.pfkfks.flightsuit.network.StoryGoalS2CPacket(true, level.dimension().location().toString(), pos, label));
        }
        if (pos != null && level.hasChunkAt(pos) && player.distanceToSqr(pos.getX(), player.getY(), pos.getZ()) > 24.0D * 24.0D) {
            lightColumn(level, player, pos);
        }
    }

    /** A gold column of light, 80 blocks tall, seen only by this player (forced, so it shows from far away). */
    private static void lightColumn(ServerLevel level, ServerPlayer player, BlockPos pos) {
        net.minecraft.core.particles.DustParticleOptions gold =
                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0F, 0.82F, 0.25F), 4.0F);
        for (int k = 0; k < 40; k++) {
            level.sendParticles(player, gold, true, pos.getX() + 0.5D, pos.getY() + 1.0D + k * 2.0D, pos.getZ() + 0.5D,
                    2, 0.25D, 1.0D, 0.25D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        MARKERS.remove(event.getEntity().getUUID());
    }

    /** Talking to Bulma or Goku moves the story along. */
    public static void talk(ServerPlayer player, DbzCharacter who) {
        DbzStage stage = dbzStage(player);
        if (stage == null) {
            stage = DbzStage.ARRIVED;
            setStage(player, stage);
        }
        switch (who) {
            case BULMA -> {
                if (stage == DbzStage.ARRIVED) {
                    player.sendSystemMessage(who.line("welcome", player.getName()));
                    give(player, new ItemStack(ModItems.SENZU_BEAN.get(), 2));
                    give(player, new ItemStack(ModItems.DRAGON_RADAR.get()));
                    player.sendSystemMessage(who.line("radar"));
                    setStage(player, DbzStage.MET_BULMA);
                    objective(player);
                } else if (stage == DbzStage.SAIYANS_BEATEN) {
                    // Chapter 2: the Namekian dragon balls - she sets the ship's course.
                    player.sendSystemMessage(who.line("namek", player.getName()));
                    player.sendSystemMessage(who.line("namek_2"));
                    player.sendSystemMessage(who.line("namek_3"));
                    setStage(player, DbzStage.NAMEK_OPEN);
                    objective(player);
                } else {
                    player.sendSystemMessage(who.line(stage.atLeast(DbzStage.NAMEK_OPEN) ? "later"
                            : stage.name().toLowerCase(java.util.Locale.ROOT)));
                    if (!player.getInventory().contains(new ItemStack(ModItems.DRAGON_RADAR.get()))) {
                        give(player, new ItemStack(ModItems.DRAGON_RADAR.get()));
                        player.sendSystemMessage(who.line("radar"));
                    }
                }
            }
            case GOKU -> {
                if (stage == DbzStage.MET_BULMA) {
                    player.sendSystemMessage(who.line("raditz", player.getName()));
                    player.sendSystemMessage(who.line("raditz_2"));
                    setStage(player, DbzStage.MET_GOKU);
                    objective(player);
                } else {
                    player.sendSystemMessage(who.line(stage.atLeast(DbzStage.NAMEK_OPEN) ? "later"
                            : stage.name().toLowerCase(java.util.Locale.ROOT)));
                }
            }
            case DENDE -> {
                if (stage == DbzStage.NAMEK_OPEN) {
                    player.sendSystemMessage(who.line("help", player.getName()));
                    player.sendSystemMessage(who.line("help_2"));
                    player.sendSystemMessage(who.line("help_3"));
                    setStage(player, DbzStage.MET_DENDE);
                    objective(player);
                } else {
                    player.sendSystemMessage(who.line(stage.atLeast(DbzStage.ZARBON_BEATEN) ? "thanks" : "hello"));
                }
            }
            case TRUNKS -> {
                if (stage == DbzStage.FRIEZA_BEATEN) {
                    player.sendSystemMessage(who.line("future", player.getName()));
                    player.sendSystemMessage(who.line("future_2"));
                    player.sendSystemMessage(who.line("future_3"));
                    setStage(player, DbzStage.MET_TRUNKS);
                    objective(player);
                } else {
                    player.sendSystemMessage(who.line("hello"));
                }
            }
            default -> {
            }
        }
    }

    /**
     * A boss withdrew. Since M17 every story fight moves on through DbzSaga's scenes (cutscenes at their moments, the
     * scene's end), so there's nothing more to do here.
     */
    public static void onBossBeaten(ServerLevel level, DbzFighterEntity boss, @Nullable Entity killer) {
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
