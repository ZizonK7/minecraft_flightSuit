package com.pfkfks.flightsuit.doom;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.cutscene.CutsceneRunner;
import com.pfkfks.flightsuit.fx.KiFx;
import com.pfkfks.flightsuit.planet.PlanetData;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import com.pfkfks.flightsuit.planet.dbz.DbzEarth;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.EdithAlert;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Doom saga, "Doomsday" (DESIGN.md 4-17, 2026-10-12): the multiverse is dying - Earths collide (incursions) - and
 * Victor von Doom has stitched what was left of the dead worlds into Battleworld, with himself on its throne.
 * - Prologue, the incursion: only after Thanos falls - the stones' time-meddling in that war tangled the timelines,
 *   and through the knot the worlds start colliding (the user: "타노스 사가의 시간여행 때문에 시간선들이 엉켜서 둠스데이가
 *   오는 것"). The first night after, another Earth fills the sky over you, a rift
 *   tears open and Doombots step out. Beat them and the Fantastic Four of Earth-828 come through after them; Reed
 *   hands you the bridge key (an amethyst frame, lit - a gate, like the Nether's).
 * - Chapter 1, Battleworld: every gate opens onto the Four's refuge. Reed tells you how it holds together: three
 *   Barons each keep a world core that feeds Doom's power.
 * - Chapter 2, the Barons, in any order, each with one of the Four beside you: Genosha (Doombots, Sentinels rising
 *   out of the ground, Magneto), Talokan (its warriors, Namor), the Deadlands beyond the Shield (the horde, the
 *   Zombie Captain). With the third core Doom shows himself and calls you to his castle.
 * - Chapter 3, Castle Doom: the gates (Doombots, all Four with you), the Latverian witches, then Doom himself -
 *   "Hell answers to me"; the Doombot trick; God Emperor Doom; Reed's question ("could you have done better?");
 *   the mask comes off. After that Battleworld stays open and Doom, being Doom, is back on his throne each day.
 * - And a last scene back home, the night after: a cloaked figure, the mask in his hands.
 * The fights work like the Dragon Ball story's (DbzSaga): waves at a sight built when you come near, cutscenes
 * before waves and at points in a boss's health; never saved - leave (96 blocks) and it starts over.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DoomSaga {
    /** The story's steps, in order (saved by name). */
    public enum Stage {
        /** The incursion is on its way (the prologue fight, a night in the overworld). */
        INCURSION,
        /** Reed gave the bridge key: build a gate, go through. */
        GATE,
        /** In Battleworld: find Reed at the refuge. */
        ARRIVED,
        /** Free the three world cores. */
        DOMAINS,
        /** Storm Castle Doom. */
        CASTLE,
        DONE;

        public boolean atLeast(Stage other) {
            return ordinal() >= other.ordinal();
        }
    }

    /** The three Barons' domains: the sight, the Baron, and which of the Four goes with you. */
    public enum Domain {
        GENOSHA(DoomSites.GENOSHA, DbzCharacter.MAGNETO, DbzCharacter.INVISIBLE_WOMAN),
        TALOKAN(DoomSites.TALOKAN, DbzCharacter.NAMOR, DbzCharacter.HUMAN_TORCH),
        DEADLANDS(DoomSites.DEADLANDS, DbzCharacter.ZOMBIE_CAPTAIN, DbzCharacter.THE_THING);

        final DoomSites site;
        final DbzCharacter baron;
        final DbzCharacter ally;

        Domain(DoomSites site, DbzCharacter baron, DbzCharacter ally) {
            this.site = site;
            this.baron = baron;
            this.ally = ally;
        }

        public int bit() {
            return 1 << ordinal();
        }

        public Component displayName() {
            return Component.translatable("doom.flightsuit.domain." + name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GREEN);
        }
    }

    private static final int ALL_CORES = 7;
    private static final String ESCORT = "flightsuit_escort";

    private DoomSaga() {
    }

    // ---------------------------------------------------------------- the story's state

    public static @Nullable Stage stage(ServerPlayer player) {
        String name = DoomData.get(player.server).walker(player.getUUID()).stage;
        if (name.isEmpty()) {
            return null;
        }
        try {
            return Stage.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean reached(ServerPlayer player, Stage stage) {
        Stage theirs = stage(player);
        return theirs != null && theirs.atLeast(stage);
    }

    public static void setStage(ServerPlayer player, @Nullable Stage stage) {
        DoomData data = DoomData.get(player.server);
        data.walker(player.getUUID()).stage = stage == null ? "" : stage.name();
        data.setDirty();
    }

    private static int cores(ServerPlayer player) {
        return DoomData.get(player.server).walker(player.getUUID()).cores;
    }

    private static void give(ServerPlayer player, ItemStack... stacks) {
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static boolean near(Entity who, BlockPos pos, double range) {
        double dx = who.getX() - pos.getX() - 0.5D;
        double dz = who.getZ() - pos.getZ() - 0.5D;
        return dx * dx + dz * dz < range * range;
    }

    private static void announce(ServerLevel level, BlockPos at, Component line) {
        for (ServerPlayer player : level.players()) {
            if (near(player, at, 128.0D)) {
                player.sendSystemMessage(line);
            }
        }
    }

    /** The cutscene scripts' cast id for a character. */
    static String castId(DbzCharacter who) {
        return switch (who) {
            case DOCTOR_DOOM, DOOM_GOD_EMPEROR -> "doom";
            case MISTER_FANTASTIC -> "reed";
            case INVISIBLE_WOMAN -> "sue";
            case HUMAN_TORCH -> "johnny";
            case THE_THING -> "ben";
            default -> who.id();
        };
    }

    // ================================================================ prologue: the incursion

    /** One player's incursion night: the other Earth overhead, the rift, its Doombots, then the Four. */
    private static final class Incursion {
        final UUID player;
        final Vec3 rift;
        final Vec3 earth;
        final List<UUID> bots = new ArrayList<>();
        int seconds;
        boolean opened;
        boolean scene;

        Incursion(UUID player, Vec3 rift, Vec3 earth) {
            this.player = player;
            this.rift = rift;
            this.earth = earth;
        }
    }

    private static final Map<UUID, Incursion> INCURSIONS = new HashMap<>();

    /**
     * Who's due an incursion: not started (or started and not finished), and Thanos beaten in this world - by anyone:
     * the tangled timelines are the world's, not one player's (so a friend who joins later gets the story too).
     */
    private static boolean due(ServerPlayer player) {
        Stage stage = stage(player);
        if (stage != null && stage != Stage.INCURSION) {
            return false;
        }
        return stage == Stage.INCURSION || !com.pfkfks.flightsuit.thanos.ThanosData.get(player.server).defeated.isEmpty();
    }

    /** Starts this player's incursion now (the test command, and the night it's due). */
    public static boolean startIncursion(ServerPlayer player) {
        if (INCURSIONS.containsKey(player.getUUID()) || player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            player.sendSystemMessage(Component.translatable("doom.flightsuit.incursion.overworld").withStyle(ChatFormatting.GRAY));
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 spot = player.position().add(look.scale(14.0D));
        Vec3 standable = com.pfkfks.flightsuit.suit.SuitSkills.standable(level, player, spot);
        Vec3 rift = standable != null ? standable : player.position().add(look.scale(6.0D));
        // The other Earth: far out ahead and high up, still within everyone's drawing range.
        Vec3 earth = player.position().add(look.scale(70.0D)).add(0.0D, 75.0D, 0.0D);
        INCURSIONS.put(player.getUUID(), new Incursion(player.getUUID(), rift, earth));
        setStage(player, Stage.INCURSION);
        player.sendSystemMessage(Component.translatable("doom.flightsuit.incursion.start").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));
        EdithAlert.send(player, Component.translatable("doom.flightsuit.incursion.edith_title").withStyle(ChatFormatting.RED),
                Component.translatable("doom.flightsuit.incursion.edith"), level.dimension(), BlockPos.containing(rift), EdithAlert.RED, false);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.6F, 0.5F);
        CutsceneRunner.shake(level, player.position(), 64.0D, 40);
        return true;
    }

    private static void tickIncursions(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long time = overworld.getDayTime() % 24000L;
        boolean night = time > 13500L && time < 22000L;
        if (night) {
            for (ServerPlayer player : overworld.players()) {
                if (!INCURSIONS.containsKey(player.getUUID()) && !player.isSpectator() && due(player)
                        && !CutsceneRunner.isWatching(player) && overworld.random.nextInt(30) == 0) {
                    startIncursion(player);
                }
            }
        }
        List<UUID> over = new ArrayList<>();
        // A copy: a scene can end at once (already seen) and take its incursion off the map mid-loop.
        for (Incursion inc : new ArrayList<>(INCURSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(inc.player);
            if (player == null || player.level() != overworld && !inc.scene || !inc.scene && player.position().distanceTo(inc.rift) > 96.0D) {
                over.add(inc.player);
                clearBots(overworld, inc);
                continue;
            }
            if (!inc.scene) {
                tickIncursion(overworld, player, inc);
            }
        }
        over.forEach(INCURSIONS::remove);
    }

    private static void clearBots(ServerLevel level, Incursion inc) {
        for (UUID id : inc.bots) {
            Entity bot = level.getEntity(id);
            if (bot != null) {
                bot.discard();
            }
        }
    }

    private static void tickIncursion(ServerLevel level, ServerPlayer player, Incursion inc) {
        inc.seconds++;
        if (inc.seconds % 15 == 1) {
            // The other Earth, blue and swelling, hanging over everything (KiFx: an orb of light).
            float size = Math.min(34.0F, 14.0F + inc.seconds * 0.8F);
            KiFx.burst(level, inc.earth, 0x3C7DFF, size, 340, KiFx.ORB);
            KiFx.burst(level, inc.earth, 0x9FE07A, size * 0.55F, 340, KiFx.ORB);
            level.playSound(null, player.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0F, 0.5F);
        }
        if (inc.seconds == 8 && !inc.opened) {
            inc.opened = true;
            KiFx.pillar(level, null, inc.rift, DoomMoves.GREEN, 18.0F, 160);
            KiFx.burst(level, inc.rift.add(0.0D, 0.2D, 0.0D), DoomMoves.GREEN, 5.0F, 30, KiFx.RING);
            level.playSound(null, BlockPos.containing(inc.rift), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.0F, 0.7F);
            player.sendSystemMessage(Component.translatable("doom.flightsuit.incursion.rift").withStyle(ChatFormatting.GREEN));
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI / 2.0D;
                Vec3 at = inc.rift.add(Math.cos(angle) * 2.0D, 0.0D, Math.sin(angle) * 2.0D);
                DbzFighterEntity bot = DbzFighterEntity.create(level, DbzCharacter.DOOMBOT, BlockPos.containing(at), true);
                bot.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
                bot.restrictTo(BlockPos.containing(inc.rift), 32);
                if (level.addFreshEntity(bot)) {
                    inc.bots.add(bot.getUUID());
                }
            }
            announce(level, BlockPos.containing(inc.rift), DbzCharacter.DOOMBOT.line("arrive"));
            return;
        }
        if (!inc.opened) {
            return;
        }
        inc.bots.removeIf(id -> !(level.getEntity(id) instanceof DbzFighterEntity bot) || !bot.isAlive());
        if (!inc.bots.isEmpty() && inc.seconds < 240) {
            return;
        }
        clearBots(level, inc);
        inc.scene = true;
        float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - inc.rift.x), player.getZ() - inc.rift.z));
        CutsceneRunner.play(level, "doom.ff_arrive", inc.rift, yaw, Map.of(), actors -> {
            INCURSIONS.remove(inc.player);
            ServerPlayer who = level.getServer().getPlayerList().getPlayer(inc.player);
            if (who != null && stage(who) == Stage.INCURSION) {
                setStage(who, Stage.GATE);
                give(who, new ItemStack(ModItems.BRIDGE_KEY.get()));
                who.sendSystemMessage(DbzCharacter.MISTER_FANTASTIC.line("key", who.getName()));
                who.sendSystemMessage(DbzCharacter.MISTER_FANTASTIC.line("key_2"));
                who.sendSystemMessage(Component.translatable("doom.flightsuit.incursion.passed").withStyle(ChatFormatting.GRAY));
                objective(who);
            }
        }, false, null);
    }

    // ================================================================ Battleworld

    /** Called by the gate when a player arrives. */
    static void onArrived(ServerPlayer player) {
        Stage stage = stage(player);
        if (stage == null || stage == Stage.INCURSION || stage == Stage.GATE) {
            setStage(player, Stage.ARRIVED);
            player.sendSystemMessage(Component.translatable("doom.flightsuit.arrived").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            ServerLevel level = player.serverLevel();
            BlockPos refuge = DoomSites.REFUGE.center(DoomData.get(player.server));
            if (refuge != null) {
                CutsceneRunner.play(level, "doom.battleworld", Vec3.atBottomCenterOf(refuge), 0.0F, Map.of(), actors -> objective(player),
                        false, List.of(player));
            }
        } else {
            objective(player);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.getGameTime() % 20 == 13) {
            tickIncursions(level.getServer());
            postCredits(level);
            return;
        }
        if (!Multiverse.inBattleworld(level) || level.getGameTime() % 20 != 13 || level.players().isEmpty()) {
            return;
        }
        DoomData data = DoomData.get(level.getServer());
        BlockPos hub = DoomSites.hub(level);
        for (DoomSites site : DoomSites.values()) {
            if (site.center(data) != null) {
                continue;
            }
            BlockPos column = site.around(hub);
            if (anyoneWithin(level, column, DbzEarth.SIGHT_RANGE)) {
                DbzEarth.load(level, column, site == DoomSites.CASTLE ? 3 : 2);
                data.setSite(site.id(), site.build(level, column));
            }
        }
        keepPeople(level, data);
        tickFight(level, data);
        patrol(level);
    }

    private static boolean anyoneWithin(ServerLevel level, BlockPos pos, double range) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && near(player, pos, range)) {
                return true;
            }
        }
        return false;
    }

    /** The Four at home in the refuge while no fight has them (Reed by his tower; the others about the plaza). */
    private static void keepPeople(ServerLevel level, DoomData data) {
        BlockPos refuge = DoomSites.REFUGE.center(data);
        if (refuge == null || !level.isPositionEntityTicking(refuge) || !anyoneWithin(level, refuge, 48.0D)
                || CutsceneRunner.isPlayingNear(level, Vec3.atCenterOf(refuge))) {
            return;
        }
        keep(level, DbzCharacter.MISTER_FANTASTIC, refuge.offset(4, 0, -2));
        keep(level, DbzCharacter.INVISIBLE_WOMAN, refuge.offset(-4, 0, -1));
        keep(level, DbzCharacter.HUMAN_TORCH, refuge.offset(-6, 0, 4));
        keep(level, DbzCharacter.THE_THING, refuge.offset(2, 0, 6));
    }

    private static void keep(ServerLevel level, DbzCharacter who, BlockPos post) {
        List<DbzFighterEntity> there = level.getEntitiesOfClass(DbzFighterEntity.class, new AABB(post).inflate(24.0D),
                f -> f.getCharacter() == who && !f.isFighting() && !f.isActing() && !f.isEscort());
        for (int i = 1; i < there.size(); i++) {
            there.get(i).discard();
        }
        // The one flying along with a player counts as home (no second copy while they're here).
        boolean escorting = !level.getEntitiesOfClass(DbzFighterEntity.class, new AABB(post).inflate(48.0D),
                f -> f.getCharacter() == who && f.isEscort()).isEmpty();
        if (there.isEmpty() && !escorting) {
            DbzFighterEntity person = DbzFighterEntity.create(level, who, post, false);
            person.moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 180.0F, 0.0F);
            level.addFreshEntity(person);
        }
    }

    /** Battleworld at night: a pair of Doombots on patrol now and then finds whoever is out (not near a fight). */
    private static void patrol(ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        if (time < 13000L || time > 23000L || level.random.nextInt(25) != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || FIGHT != null && near(player, FIGHT.center, 120.0D)) {
                continue;
            }
            DoomData data = DoomData.get(level.getServer());
            BlockPos refuge = DoomSites.REFUGE.center(data);
            if (refuge != null && near(player, refuge, 32.0D)) {
                continue;
            }
            int bots = level.getEntitiesOfClass(DbzFighterEntity.class, player.getBoundingBox().inflate(48.0D),
                    f -> f.getCharacter() == DbzCharacter.DOOMBOT).size();
            if (bots >= 3) {
                continue;
            }
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < 2; i++) {
                int x = Mth.floor(player.getX() + Math.cos(angle) * 22.0D) + i * 2;
                int z = Mth.floor(player.getZ() + Math.sin(angle) * 22.0D);
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                DbzFighterEntity bot = DbzFighterEntity.create(level, DbzCharacter.DOOMBOT, new BlockPos(x, y, z), true);
                bot.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                bot.restrictTo(new BlockPos(x, y, z), 40);
                level.addFreshEntity(bot);
            }
        }
    }

    // ================================================================ the fights

    /** One wave: who comes, who fights beside you, what's said, and the cutscene before it. */
    private record Wave(DbzCharacter[] foes, DbzCharacter[] allies, String[] lines, @Nullable String intro) {
    }

    private static Wave wave(DbzCharacter[] foes, DbzCharacter[] allies, String[] lines, @Nullable String intro) {
        return new Wave(foes, allies, lines, intro);
    }

    private static DbzCharacter[] of(DbzCharacter who, int count) {
        DbzCharacter[] out = new DbzCharacter[count];
        java.util.Arrays.fill(out, who);
        return out;
    }

    private static DbzCharacter[] list(DbzCharacter... who) {
        return who;
    }

    private static String[] lines(String... keys) {
        return keys;
    }

    private static final DbzCharacter[] FOUR = {DbzCharacter.MISTER_FANTASTIC, DbzCharacter.INVISIBLE_WOMAN, DbzCharacter.HUMAN_TORCH,
            DbzCharacter.THE_THING};

    /** A story fight: where, who it's for, its waves. REMATCH is Doom back on his throne (after the story, once a day). */
    private enum Scene {
        GENOSHA(DoomSites.GENOSHA, Domain.GENOSHA),
        TALOKAN(DoomSites.TALOKAN, Domain.TALOKAN),
        DEADLANDS(DoomSites.DEADLANDS, Domain.DEADLANDS),
        CASTLE(DoomSites.CASTLE, null),
        REMATCH(DoomSites.CASTLE, null);

        final DoomSites site;
        final @Nullable Domain domain;

        Scene(DoomSites site, @Nullable Domain domain) {
            this.site = site;
            this.domain = domain;
        }

        Wave[] waves() {
            DbzCharacter[] none = new DbzCharacter[0];
            return switch (this) {
                case GENOSHA -> new Wave[]{
                        wave(of(DbzCharacter.DOOMBOT, 5), list(DbzCharacter.INVISIBLE_WOMAN), lines("magneto.watch"), "doom.genosha"),
                        wave(of(DbzCharacter.SENTINEL, 2), none, lines(), "doom.sentinels"),
                        wave(list(DbzCharacter.MAGNETO), none, lines("magneto.arrive"), null)};
                case TALOKAN -> new Wave[]{
                        wave(of(DbzCharacter.TALOKAN_WARRIOR, 6), list(DbzCharacter.HUMAN_TORCH), lines(), "doom.talokan"),
                        wave(list(DbzCharacter.NAMOR, DbzCharacter.TALOKAN_WARRIOR, DbzCharacter.TALOKAN_WARRIOR), none, lines("namor.arrive"), null)};
                case DEADLANDS -> new Wave[]{
                        wave(of(DbzCharacter.MARVEL_ZOMBIE, 7), list(DbzCharacter.THE_THING), lines(), "doom.deadlands"),
                        wave(of(DbzCharacter.MARVEL_ZOMBIE, 9), none, lines("ben.more"), null),
                        wave(list(DbzCharacter.ZOMBIE_CAPTAIN, DbzCharacter.MARVEL_ZOMBIE, DbzCharacter.MARVEL_ZOMBIE, DbzCharacter.MARVEL_ZOMBIE), none,
                                lines("zombie_captain.arrive"), null)};
                case CASTLE -> new Wave[]{
                        wave(of(DbzCharacter.DOOMBOT, 7), FOUR, lines(), "doom.castle_gates"),
                        wave(list(DbzCharacter.LATVERIAN_WITCH, DbzCharacter.LATVERIAN_WITCH, DbzCharacter.LATVERIAN_WITCH, DbzCharacter.DOOMBOT,
                                DbzCharacter.DOOMBOT), none, lines("latverian_witch.arrive"), null),
                        wave(none, none, lines(), "doom.throne")};
                case REMATCH -> new Wave[]{
                        wave(of(DbzCharacter.DOOMBOT, 4), none, lines("doom.again"), null),
                        wave(list(DbzCharacter.DOCTOR_DOOM, DbzCharacter.LATVERIAN_WITCH), none, lines("doom.rematch"), null)};
            };
        }
    }

    private record Queued(String id, Supplier<Map<String, DbzFighterEntity>> bound, Consumer<Map<String, DbzFighterEntity>> after) {
    }

    private static final class Fight {
        final Scene scene;
        final BlockPos center;
        final Vec3 anchor;
        final long startedAt;
        int wave = -1;
        final Map<UUID, DbzCharacter> foes = new LinkedHashMap<>();
        final Map<UUID, DbzCharacter> allies = new LinkedHashMap<>();
        final List<UUID> props = new ArrayList<>();
        boolean busy;
        final List<Queued> queue = new ArrayList<>();
        boolean over;

        Fight(Scene scene, BlockPos center, long startedAt) {
            this.scene = scene;
            this.center = center;
            this.anchor = Vec3.atBottomCenterOf(center);
            this.startedAt = startedAt;
        }
    }

    /** Battleworld has one story fight on at a time. */
    private static @Nullable Fight FIGHT;

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        FIGHT = null;
        INCURSIONS.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Incursion inc = INCURSIONS.remove(event.getEntity().getUUID());
        if (inc != null && event.getEntity().getServer() != null) {
            clearBots(event.getEntity().getServer().overworld(), inc);
        }
    }

    private static void tickFight(ServerLevel level, DoomData data) {
        Fight fight = FIGHT;
        if (fight == null) {
            fight = start(level, data);
            if (fight != null) {
                FIGHT = fight;
                begin(level, fight);
            }
            return;
        }
        if (!anyoneWithin(level, fight.center, 96.0D) || level.getGameTime() - fight.startedAt > 20L * 60 * 25) {
            end(level, fight);
            return;
        }
        if (fight.busy || !level.isPositionEntityTicking(fight.center)) {
            return;
        }
        int up = 0;
        for (UUID id : fight.foes.keySet()) {
            if (level.getEntity(id) instanceof DbzFighterEntity foe && foe.isAlive() && !foe.isActing()) {
                up++;
            }
        }
        if (up > 0) {
            return;
        }
        if (fight.wave + 1 < fight.scene.waves().length) {
            nextWave(level, fight);
        } else {
            finish(level, fight);
        }
    }

    /** Who's ready for which fight: on the right step of the story, within 40 blocks of its sight. */
    private static @Nullable Fight start(ServerLevel level, DoomData data) {
        long day = level.getServer().overworld().getDayTime() / 24000L;
        for (Scene scene : Scene.values()) {
            BlockPos center = scene.site.center(data);
            if (center == null || !level.isPositionEntityTicking(center)) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || !near(player, center, 40.0D)) {
                    continue;
                }
                Stage stage = stage(player);
                boolean ready = switch (scene) {
                    case GENOSHA, TALOKAN, DEADLANDS -> stage == Stage.DOMAINS && (cores(player) & scene.domain.bit()) == 0;
                    case CASTLE -> stage == Stage.CASTLE;
                    case REMATCH -> stage == Stage.DONE && data.world().getLong("rematch_" + player.getUUID()) < day;
                };
                if (ready) {
                    return new Fight(scene, center, level.getGameTime());
                }
            }
        }
        return null;
    }

    private static void begin(ServerLevel level, Fight fight) {
        for (DbzFighterEntity escort : level.getEntitiesOfClass(DbzFighterEntity.class, new AABB(fight.center).inflate(160.0D),
                DbzFighterEntity::isEscort)) {
            escort.discard();
        }
        if (fight.scene == Scene.REMATCH) {
            for (ServerPlayer player : level.players()) {
                if (near(player, fight.center, 40.0D) && stage(player) == Stage.DONE) {
                    DoomData data = DoomData.get(level.getServer());
                    data.world().putLong("rematch_" + player.getUUID(), level.getServer().overworld().getDayTime() / 24000L);
                    data.setDirty();
                }
            }
        }
        nextWave(level, fight);
    }

    private static void nextWave(ServerLevel level, Fight fight) {
        fight.wave++;
        Wave wave = fight.scene.waves()[fight.wave];
        Runnable spawnFoes = () -> {
            for (int i = 0; i < wave.foes().length; i++) {
                double angle = i * Math.PI * 2.0D / Math.max(1, wave.foes().length);
                int r = wave.foes().length > 1 ? 7 : 0;
                spawn(level, fight, wave.foes()[i], Mth.floor(Math.cos(angle) * r), Mth.floor(Math.sin(angle) * r) - 4, false);
            }
            for (String key : wave.lines()) {
                int dot = key.indexOf('.');
                announce(level, fight.center, speaker(key.substring(0, dot)).line(key.substring(dot + 1)));
            }
            level.playSound(null, fight.center, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.2F, 1.5F);
        };
        for (int i = 0; i < wave.allies().length; i++) {
            if (find(level, fight, wave.allies()[i]) == null) {
                spawn(level, fight, wave.allies()[i], -4 + i * 3, 8, true);
            }
        }
        if (wave.intro() == null) {
            spawnFoes.run();
            return;
        }
        play(level, fight, wave.intro(), () -> bindAll(level, fight), actors -> spawnFoes.run());
    }

    /** "magneto.watch" → MAGNETO; the voices of the Four by their short names. */
    private static DbzCharacter speaker(String voice) {
        return switch (voice) {
            case "reed" -> DbzCharacter.MISTER_FANTASTIC;
            case "sue" -> DbzCharacter.INVISIBLE_WOMAN;
            case "johnny" -> DbzCharacter.HUMAN_TORCH;
            case "ben" -> DbzCharacter.THE_THING;
            case "doom" -> DbzCharacter.DOCTOR_DOOM;
            default -> DbzCharacter.byName(voice);
        };
    }

    private static @Nullable DbzFighterEntity spawn(ServerLevel level, Fight fight, DbzCharacter who, int dx, int dz, boolean ally) {
        if (fight.scene == Scene.DEADLANDS) {
            // Played turned round (yaw 180): the dead come from the Shield's breach, to the south.
            dx = -dx;
            dz = -dz;
        }
        int x = fight.center.getX() + dx;
        int z = fight.center.getZ() + dz;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y = Math.max(fight.center.getY(), Math.min(top, fight.center.getY() + 4));
        DbzFighterEntity fighter = DbzFighterEntity.create(level, who, new BlockPos(x, y, z), true);
        fighter.restrictTo(fight.center, 36);
        fighter.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        if (!level.addFreshEntity(fighter)) {
            return null;
        }
        (ally ? fight.allies : fight.foes).put(fighter.getUUID(), who);
        arm(level, fight, fighter);
        return fighter;
    }

    /** A fighter called in mid-fight (DoomMoves: Doombots, Talokan, the horde): joins {@code caller}'s fight if it's in one. */
    static @Nullable DbzFighterEntity reinforce(ServerLevel level, DbzFighterEntity caller, DbzCharacter who, Vec3 at) {
        DbzFighterEntity fighter = DbzFighterEntity.create(level, who, BlockPos.containing(at), true);
        fighter.moveTo(at.x, at.y, at.z, caller.getYRot(), 0.0F);
        Fight fight = FIGHT;
        boolean inFight = fight != null && !fight.over && (fight.foes.containsKey(caller.getUUID()) || fight.props.contains(caller.getUUID()));
        fighter.restrictTo(inFight ? fight.center : caller.blockPosition(), 36);
        if (!level.addFreshEntity(fighter)) {
            return null;
        }
        if (inFight) {
            fight.foes.put(fighter.getUUID(), who);
        }
        return fighter;
    }

    private static @Nullable DbzFighterEntity find(ServerLevel level, Fight fight, DbzCharacter who) {
        List<UUID> all = new ArrayList<>(fight.foes.keySet());
        all.addAll(fight.allies.keySet());
        all.addAll(fight.props);
        for (UUID id : all) {
            if (level.getEntity(id) instanceof DbzFighterEntity fighter && fighter.isAlive() && fighter.getCharacter() == who) {
                return fighter;
            }
        }
        return null;
    }

    private static Map<String, DbzFighterEntity> bindAll(ServerLevel level, Fight fight) {
        Map<String, DbzFighterEntity> bound = new HashMap<>();
        List<UUID> all = new ArrayList<>(fight.foes.keySet());
        all.addAll(fight.allies.keySet());
        all.addAll(fight.props);
        for (UUID id : all) {
            if (level.getEntity(id) instanceof DbzFighterEntity fighter && fighter.isAlive()) {
                bound.putIfAbsent(castId(fighter.getCharacter()), fighter);
            }
        }
        return bound;
    }

    /** A cutscene in the fight: the waves wait for it, its fighters who end up fighting join in, then {@code after}. */
    private static void play(ServerLevel level, Fight fight, String id, Supplier<Map<String, DbzFighterEntity>> bound,
                             Consumer<Map<String, DbzFighterEntity>> after) {
        if (fight.busy) {
            fight.queue.add(new Queued(id, bound, after));
            return;
        }
        fight.busy = true;
        float yaw = fight.scene.site == DoomSites.DEADLANDS ? 180.0F : 0.0F;
        CutsceneRunner.play(level, id, fight.anchor, yaw, bound.get(), actors -> {
            if (fight.over) {
                actors.values().forEach(Entity::discard);
                return;
            }
            adopt(level, fight, actors);
            fight.busy = false;
            after.accept(actors);
            if (!fight.busy && !fight.queue.isEmpty() && !fight.over) {
                Queued next = fight.queue.remove(0);
                play(level, fight, next.id(), next.bound(), next.after());
            }
        }, false, null);
    }

    private static void adopt(ServerLevel level, Fight fight, Map<String, DbzFighterEntity> actors) {
        fight.foes.keySet().removeIf(id -> !(level.getEntity(id) instanceof DbzFighterEntity f) || !f.isAlive());
        fight.allies.keySet().removeIf(id -> !(level.getEntity(id) instanceof DbzFighterEntity f) || !f.isAlive());
        for (DbzFighterEntity actor : actors.values()) {
            if (!actor.isAlive()) {
                continue;
            }
            UUID id = actor.getUUID();
            fight.foes.remove(id);
            fight.allies.remove(id);
            if (actor.isActing()) {
                if (!fight.props.contains(id)) {
                    fight.props.add(id);
                }
                continue;
            }
            fight.props.remove(id);
            actor.restrictTo(fight.center, 36);
            (actor.getCharacter().isFoe() ? fight.foes : fight.allies).put(id, actor.getCharacter());
            arm(level, fight, actor);
        }
    }

    /** The story's moments in a boss fight: cutscenes at shares of its health. */
    private static void arm(ServerLevel level, Fight fight, DbzFighterEntity fighter) {
        if (fighter.isArmed()) {
            return;
        }
        fighter.setArmed();
        DbzCharacter who = fighter.getCharacter();
        switch (fight.scene) {
            case GENOSHA -> {
                if (who == DbzCharacter.MAGNETO) {
                    fighter.atHealth(0.0F, () -> play(level, fight, "doom.magneto_yields", () -> bindAll(level, fight), actors -> finish(level, fight)));
                }
            }
            case TALOKAN -> {
                if (who == DbzCharacter.NAMOR) {
                    fighter.atHealth(0.0F, () -> play(level, fight, "doom.namor_yields", () -> bindAll(level, fight), actors -> finish(level, fight)));
                }
            }
            case CASTLE -> {
                if (who == DbzCharacter.DOCTOR_DOOM) {
                    fighter.atHealth(0.75F, () -> play(level, fight, "doom.doombot_reveal", () -> bindAll(level, fight), actors -> {
                        DbzFighterEntity doom = actors.get("doom");
                        if (doom != null) {
                            // That was a Doombot. The real one is fresh.
                            doom.heal(doom.getMaxHealth() * 0.25F);
                        }
                    }));
                    fighter.atHealth(0.5F, () -> play(level, fight, "doom.god_emperor", () -> bindAll(level, fight), actors -> {
                        DbzFighterEntity doom = actors.get("doom");
                        if (doom != null) {
                            doom.setAura(DbzFighterEntity.AURA_GOLD, 0);
                            doom.heal(doom.getMaxHealth() * 0.15F);
                        }
                    }));
                    fighter.atHealth(0.2F, () -> play(level, fight, "doom.could_you", () -> bindAll(level, fight), actors -> {
                        DbzFighterEntity doom = actors.get("doom");
                        if (doom != null) {
                            // The power's doubt is his: it lets go of him.
                            doom.setAura(DbzFighterEntity.AURA_NONE, 0);
                            doom.buff(0.8F, 1.6F, 0);
                        }
                    }));
                    fighter.atHealth(0.0F, () -> play(level, fight, "doom.unmasked", () -> bindAll(level, fight), actors -> finish(level, fight)));
                }
            }
            default -> {
            }
        }
    }

    /** A boss withdrew (DbzFighterEntity.die): nothing more to do - the fight moves on when no foe is left standing. */
    public static void onBossBeaten(ServerLevel level, DbzFighterEntity boss, @Nullable Entity killer) {
        if (boss.getCharacter() == DbzCharacter.DOCTOR_DOOM || boss.getCharacter() == DbzCharacter.DOOM_GOD_EMPEROR) {
            DoomMoves.sparks(level, boss.getBoundingBox().getCenter());
        }
    }

    /** The fight is won: the scene's step of the story, its rewards, the allies along with the lead player. */
    private static void finish(ServerLevel level, Fight fight) {
        if (fight.over) {
            return;
        }
        DoomData data = DoomData.get(level.getServer());
        ServerPlayer lead = null;
        for (ServerPlayer player : level.players()) {
            if (!near(player, fight.center, 96.0D) || player.isSpectator()) {
                continue;
            }
            Stage stage = stage(player);
            DoomData.Walker walker = data.walker(player.getUUID());
            switch (fight.scene) {
                case GENOSHA, TALOKAN, DEADLANDS -> {
                    Domain domain = fight.scene.domain;
                    if (stage != Stage.DOMAINS || (walker.cores & domain.bit()) != 0) {
                        continue;
                    }
                    walker.cores |= domain.bit();
                    give(player, new ItemStack(ModItems.WORLD_CORE.get()));
                    switch (domain) {
                        case GENOSHA -> give(player, new ItemStack(Items.IRON_BLOCK, 8), new ItemStack(ModItems.ARC_REACTOR.get()));
                        case TALOKAN -> give(player, new ItemStack(Items.HEART_OF_THE_SEA), new ItemStack(Items.DIAMOND, 4),
                                new ItemStack(Items.PRISMARINE_SHARD, 16));
                        case DEADLANDS -> give(player, new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.DIAMOND, 4));
                    }
                    int count = Integer.bitCount(walker.cores);
                    player.sendSystemMessage(Component.translatable("doom.flightsuit.core_won", domain.displayName(), count)
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                    if (walker.cores == ALL_CORES) {
                        setStage(player, Stage.CASTLE);
                    }
                }
                case CASTLE -> {
                    if (stage != Stage.CASTLE) {
                        continue;
                    }
                    setStage(player, Stage.DONE);
                    data.world().putLong("rematch_" + player.getUUID(), level.getServer().overworld().getDayTime() / 24000L);
                    give(player, new ItemStack(ModItems.DOOM_MASK.get()), new ItemStack(Items.NETHERITE_INGOT, 2),
                            new ItemStack(ModItems.ARC_REACTOR.get(), 3), new ItemStack(Items.DIAMOND, 16));
                    upgradeSuit(player);
                    player.sendSystemMessage(Component.translatable("doom.flightsuit.done").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                    EdithAlert.send(player, Component.translatable("doom.flightsuit.done_title").withStyle(ChatFormatting.GOLD),
                            Component.translatable("doom.flightsuit.done_edith"), null, null, EdithAlert.CYAN, false);
                }
                case REMATCH -> {
                    if (stage != Stage.DONE) {
                        continue;
                    }
                    walker.rematches++;
                    give(player, new ItemStack(Items.DIAMOND, 6), new ItemStack(Items.NETHERITE_SCRAP), new ItemStack(ModItems.ARC_REACTOR.get()));
                    player.sendSystemMessage(Component.translatable("doom.flightsuit.rematch_won", walker.rematches).withStyle(ChatFormatting.GOLD));
                }
            }
            lead = lead == null ? player : lead;
            data.setDirty();
            objective(player);
        }
        if (lead != null && fight.scene.domain != null && reached(lead, Stage.CASTLE)) {
            // The third core: Doom shows himself - and calls them to his castle.
            ServerPlayer caller = lead;
            List<UUID> keep = new ArrayList<>(fight.allies.keySet());
            fight.allies.clear();
            end(level, fight);
            CutsceneRunner.play(level, "doom.summons", fight.anchor, 0.0F, Map.of(), actors -> objective(caller), false, null);
            escort(level, keep, caller);
            return;
        }
        if (lead != null) {
            escort(level, new ArrayList<>(fight.allies.keySet()), lead);
            fight.allies.clear();
        }
        end(level, fight);
    }

    private static void escort(ServerLevel level, List<UUID> allies, ServerPlayer lead) {
        for (UUID id : allies) {
            if (level.getEntity(id) instanceof DbzFighterEntity ally && ally.isAlive() && !ally.isActing()) {
                ally.setInvisible(false);
                ally.escort(lead.getUUID());
            }
        }
    }

    /** Doom's science, taken apart: the worn suit chestplate's battery goes up a level (else two more arc reactors). */
    private static void upgradeSuit(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof com.pfkfks.flightsuit.suit.SuitArmorItem)) {
            chest = ItemStack.EMPTY;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.getItem() instanceof com.pfkfks.flightsuit.suit.SuitArmorItem armor && armor.getEquipmentSlot() == EquipmentSlot.CHEST) {
                    chest = stack;
                    break;
                }
            }
        }
        int level = chest.isEmpty() ? com.pfkfks.flightsuit.suit.SuitEnergy.MAX_UPGRADE : com.pfkfks.flightsuit.suit.SuitEnergy.upgradeLevel(chest);
        if (level < com.pfkfks.flightsuit.suit.SuitEnergy.MAX_UPGRADE) {
            com.pfkfks.flightsuit.suit.SuitEnergy.setUpgradeLevel(chest, level + 1);
            player.sendSystemMessage(Component.translatable("doom.flightsuit.reward_battery").withStyle(ChatFormatting.AQUA));
        } else {
            give(player, new ItemStack(ModItems.ARC_REACTOR.get(), 2));
            player.sendSystemMessage(Component.translatable("doom.flightsuit.reward_reactors").withStyle(ChatFormatting.AQUA));
        }
    }

    private static void end(ServerLevel level, Fight fight) {
        fight.over = true;
        List<UUID> all = new ArrayList<>(fight.foes.keySet());
        all.addAll(fight.allies.keySet());
        all.addAll(fight.props);
        for (UUID id : all) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        if (FIGHT == fight) {
            FIGHT = null;
        }
    }

    /** "/flightsuit doom stop": the fight on in Battleworld is called off (it starts over when someone comes back). */
    public static boolean stopFight(ServerLevel level) {
        Fight fight = FIGHT;
        if (fight == null) {
            return false;
        }
        end(level, fight);
        return true;
    }

    // ================================================================ the epilogue: back home

    /** The night after the castle, at home: a cloaked figure, the mask in his hands (once). */
    private static void postCredits(ServerLevel overworld) {
        long time = overworld.getDayTime() % 24000L;
        if (time < 14000L || time > 20000L) {
            return;
        }
        PlanetData planets = PlanetData.get(overworld.getServer());
        for (ServerPlayer player : overworld.players()) {
            if (stage(player) != Stage.DONE || planets.traveller(player.getUUID()).seenCutscenes.contains("doom.post_credits")
                    || CutsceneRunner.isWatching(player) || player.isSpectator() || overworld.random.nextInt(20) != 0) {
                continue;
            }
            Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(5.0D);
            Vec3 anchor = player.position().add(ahead.x, 0.0D, ahead.z);
            CutsceneRunner.play(overworld, "doom.post_credits", anchor, player.getYRot() + 180.0F, Map.of(), actors -> {
            }, false, List.of(player));
        }
    }

    // ================================================================ talking

    /** Talking to the Four at the refuge moves the story along. */
    public static void talk(ServerPlayer player, DbzCharacter who) {
        Stage stage = stage(player);
        String key = stage == null ? "none" : stage.name().toLowerCase(java.util.Locale.ROOT);
        switch (who) {
            case MISTER_FANTASTIC -> {
                if (stage == Stage.ARRIVED) {
                    player.sendSystemMessage(who.line("explain", player.getName()));
                    player.sendSystemMessage(who.line("explain_2"));
                    player.sendSystemMessage(who.line("explain_3"));
                    player.sendSystemMessage(who.line("explain_4"));
                    setStage(player, Stage.DOMAINS);
                    objective(player);
                } else if (stage == Stage.DOMAINS) {
                    player.sendSystemMessage(who.line("domains", Integer.bitCount(cores(player))));
                    objective(player);
                } else {
                    player.sendSystemMessage(who.line(key));
                }
                if (reached(player, Stage.GATE) && !player.getInventory().contains(new ItemStack(ModItems.BRIDGE_KEY.get()))) {
                    give(player, new ItemStack(ModItems.BRIDGE_KEY.get()));
                    player.sendSystemMessage(who.line("spare_key"));
                }
            }
            case INVISIBLE_WOMAN, HUMAN_TORCH, THE_THING -> player.sendSystemMessage(who.line(key));
            default -> {
            }
        }
    }

    // ================================================================ where to go

    /** Where the story goes next in Battleworld: a spot (y 0 = not built yet, its ground) and what's there. */
    public record Goal(BlockPos pos, Component label) {
    }

    public static @Nullable Goal goal(ServerPlayer player) {
        Stage stage = stage(player);
        if (stage == null || !Multiverse.inBattleworld(player.level())) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        DoomData data = DoomData.get(player.server);
        BlockPos hub = data.site(DoomSites.HUB);
        if (hub == null) {
            return null;
        }
        switch (stage) {
            case ARRIVED, GATE, INCURSION -> {
                BlockPos refuge = DoomSites.REFUGE.center(data);
                return refuge == null ? null : new Goal(refuge.offset(4, 0, -2), Component.translatable("doom.flightsuit.marker.reed"));
            }
            case DOMAINS -> {
                Domain best = null;
                BlockPos bestPos = null;
                double bestDist = Double.MAX_VALUE;
                for (Domain domain : Domain.values()) {
                    if ((cores(player) & domain.bit()) != 0) {
                        continue;
                    }
                    BlockPos built = domain.site.center(data);
                    BlockPos at = built != null ? built : flat(domain.site.around(hub));
                    double d = player.distanceToSqr(at.getX(), player.getY(), at.getZ());
                    if (d < bestDist) {
                        bestDist = d;
                        best = domain;
                        bestPos = at;
                    }
                }
                return best == null ? null : new Goal(bestPos, Component.translatable("doom.flightsuit.marker.domain", best.displayName()));
            }
            case CASTLE -> {
                BlockPos built = DoomSites.CASTLE.center(data);
                return new Goal(built != null ? built : flat(DoomSites.CASTLE.around(hub)), Component.translatable("doom.flightsuit.marker.castle"));
            }
            default -> {
                return null;
            }
        }
    }

    private static BlockPos flat(BlockPos pos) {
        return new BlockPos(pos.getX(), 0, pos.getZ());
    }

    /** "/planet" and "/doom": what to do next, and which way. True if this player is in the story at all. */
    public static boolean objective(ServerPlayer player) {
        Stage stage = stage(player);
        if (stage == null) {
            return false;
        }
        Goal goal = goal(player);
        Component where = Component.literal("");
        if (goal != null) {
            double dx = goal.pos().getX() - player.getX();
            double dz = goal.pos().getZ() - player.getZ();
            int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8);
            where = Component.translatable("edith.flightsuit.where", (int) Math.sqrt(dx * dx + dz * dz),
                    Component.translatable("edith.flightsuit.dir." + sector));
        }
        player.sendSystemMessage(Component.translatable("doom.flightsuit.goal." + stage.name().toLowerCase(java.util.Locale.ROOT), where,
                Integer.bitCount(cores(player))).withStyle(ChatFormatting.GREEN));
        return true;
    }
}
