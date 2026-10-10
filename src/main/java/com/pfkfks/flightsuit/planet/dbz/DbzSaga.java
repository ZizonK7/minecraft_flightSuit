package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.cutscene.CutsceneRunner;
import com.pfkfks.flightsuit.planet.Planet;
import com.pfkfks.flightsuit.planet.PlanetData;
import com.pfkfks.flightsuit.planet.PlanetStory;
import com.pfkfks.flightsuit.planet.PlanetStory.DbzStage;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.Companions;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.war.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Dragon Ball story's fights, scene by scene (M15/M16; reworked in M17 around cutscenes): each scene is a fight in
 * waves at a sight that's built when someone on that part of the story comes near, with cutscenes before waves and at
 * points in the fight (a boss's health falling to a share - DbzFighterEntity.atHealth), the story's famous moments:
 * - chapter 1 at the crater (DbzEarth's): Raditz with Goku and Piccolo (Gohan's headbutt, the Special Beam Cannon);
 *   a day later the Saibamen and Nappa (Yamcha and Piccolo fall), then Vegeta (Goku's Kaioken, the Great Ape,
 *   Yajirobe cutting the tail, Vegeta spared);
 * - chapter 2 on Namek: the village raid, the Ginyu Force (Goku lands), Frieza (Krillin, Goku's Super Saiyan, the
 *   planet's last minutes, the escape);
 * - chapter 3 back on Earth: Trunks arrives; the androids (Cell absorbs 17 and 18); the Cell Games (Gohan awakens,
 *   Cell's self-destruct, the father-son Kamehameha);
 * - chapter 4: Majin Buu at Babidi's ship (Mr. Satan, Super Buu, the absorption, Kid Buu) and the Spirit Bomb.
 * After a scene its fighters fly along with you a while (escorts). Never saved: a fight left (nobody within 96 blocks)
 * or too long (20 minutes) starts over next time; Namek's last minutes running out does too.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DbzSaga {
    /**
     * A sight of the story: on which planet, where (from the landing site), from which point in the story. CRATER is
     * chapter 1's crater, built by DbzEarth as a landmark (its fights happen on the crater floor).
     */
    public enum Site {
        CRATER(Planet.DBZ_EARTH, 0, 0, DbzStage.MET_GOKU),
        NAMEK_VILLAGE(Planet.NAMEK, 40, 24, DbzStage.NAMEK_OPEN),
        FRIEZA_SHIP(Planet.NAMEK, 190, -150, DbzStage.MET_DENDE),
        GINYU_FIELD(Planet.NAMEK, -170, 120, DbzStage.ZARBON_BEATEN),
        ANDROID_ROAD(Planet.DBZ_EARTH, -70, -220, DbzStage.MET_TRUNKS),
        CELL_RING(Planet.DBZ_EARTH, 240, 70, DbzStage.ANDROIDS_BEATEN),
        BABIDI_SHIP(Planet.DBZ_EARTH, -230, -40, DbzStage.CELL_BEATEN),
        WASTELAND(Planet.DBZ_EARTH, 40, 320, DbzStage.BUU_BEATEN);

        final Planet planet;
        final int dx;
        final int dz;
        final DbzStage from;

        Site(Planet planet, int dx, int dz, DbzStage from) {
            this.planet = planet;
            this.dx = dx;
            this.dz = dz;
            this.from = from;
        }

        public String key() {
            return "saga_" + name().toLowerCase(java.util.Locale.ROOT);
        }

        public BlockPos around(BlockPos site) {
            return site.offset(dx, 0, dz);
        }

        public Planet planet() {
            return planet;
        }
    }

    /** One wave: who comes (and who fights beside you), what's said, and a cutscene that plays before it (may bring its foes). */
    private record Wave(DbzCharacter[] foes, DbzCharacter[] allies, String[] lines, @Nullable String intro) {
    }

    /** A scene: its sight, the point in the story it starts at and moves on to, whether it waits for the next day. */
    private enum Scene {
        RADITZ(Site.CRATER, DbzStage.MET_GOKU, DbzStage.RADITZ_BEATEN, false),
        SAIBAMEN_NAPPA(Site.CRATER, DbzStage.RADITZ_BEATEN, DbzStage.NAPPA_BEATEN, true),
        VEGETA(Site.CRATER, DbzStage.NAPPA_BEATEN, DbzStage.SAIYANS_BEATEN, false),
        ZARBON(Site.NAMEK_VILLAGE, DbzStage.MET_DENDE, DbzStage.ZARBON_BEATEN, false),
        GINYU(Site.GINYU_FIELD, DbzStage.ZARBON_BEATEN, DbzStage.GINYU_BEATEN, false),
        FRIEZA(Site.FRIEZA_SHIP, DbzStage.GINYU_BEATEN, DbzStage.FRIEZA_BEATEN, false),
        ANDROIDS(Site.ANDROID_ROAD, DbzStage.MET_TRUNKS, DbzStage.ANDROIDS_BEATEN, false),
        CELL(Site.CELL_RING, DbzStage.ANDROIDS_BEATEN, DbzStage.CELL_BEATEN, true),
        BUU(Site.BABIDI_SHIP, DbzStage.CELL_BEATEN, DbzStage.BUU_BEATEN, false),
        KID_BUU(Site.WASTELAND, DbzStage.BUU_BEATEN, DbzStage.KID_BUU_BEATEN, false);

        final Site site;
        final DbzStage from;
        final DbzStage to;
        /** Starts only on the day set when the scene before ended ("tomorrow"). */
        final boolean nextDay;

        Scene(Site site, DbzStage from, DbzStage to, boolean nextDay) {
            this.site = site;
            this.from = from;
            this.to = to;
            this.nextDay = nextDay;
        }

        Wave[] waves() {
            return switch (this) {
                case RADITZ -> new Wave[]{wave(none(), none(), lines(), "dbz.raditz_arrives")};
                case SAIBAMEN_NAPPA -> new Wave[]{
                        wave(of(DbzCharacter.SAIBAMAN, 6), new DbzCharacter[]{DbzCharacter.PICCOLO, DbzCharacter.KRILLIN, DbzCharacter.YAMCHA,
                                DbzCharacter.TIEN, DbzCharacter.GOHAN_KID}, lines("nappa.arrive", "vegeta.arrive"), "dbz.saiyans_land"),
                        wave(of(DbzCharacter.NAPPA, 1), none(), lines("nappa.go"), null)};
                case VEGETA -> new Wave[]{wave(of(DbzCharacter.VEGETA, 1),
                        new DbzCharacter[]{DbzCharacter.KRILLIN, DbzCharacter.TIEN, DbzCharacter.GOHAN_KID}, lines("vegeta.go"), null)};
                case ZARBON -> new Wave[]{
                        wave(of(DbzCharacter.FRIEZA_SOLDIER, 6), new DbzCharacter[]{DbzCharacter.KRILLIN, DbzCharacter.GOHAN_KID},
                                lines("frieza_soldier.arrive", "krillin.namek"), null),
                        wave(of(DbzCharacter.DODORIA, 1), none(), lines("dodoria.arrive"), null),
                        wave(of(DbzCharacter.ZARBON, 1), none(), lines("zarbon.arrive"), null)};
                case GINYU -> new Wave[]{
                        wave(of(DbzCharacter.GULDO, 1), new DbzCharacter[]{DbzCharacter.KRILLIN, DbzCharacter.GOHAN_KID},
                                lines("guldo.arrive"), "dbz.ginyu_pose"),
                        wave(of(DbzCharacter.RECOOME, 1), none(), lines("recoome.arrive"), null),
                        wave(new DbzCharacter[]{DbzCharacter.BURTER, DbzCharacter.JEICE}, none(), lines("burter.arrive", "jeice.arrive"), null),
                        wave(of(DbzCharacter.GINYU, 1), none(), lines("ginyu.arrive"), null)};
                case FRIEZA -> new Wave[]{
                        wave(of(DbzCharacter.FRIEZA_SOLDIER, 5), new DbzCharacter[]{DbzCharacter.GOKU, DbzCharacter.PICCOLO, DbzCharacter.KRILLIN,
                                DbzCharacter.GOHAN_KID}, lines("frieza.arrive", "piccolo.back"), null),
                        wave(of(DbzCharacter.FRIEZA, 1), none(), lines("frieza.go"), null)};
                case ANDROIDS -> new Wave[]{wave(new DbzCharacter[]{DbzCharacter.ANDROID_17, DbzCharacter.ANDROID_18},
                        new DbzCharacter[]{DbzCharacter.TRUNKS, DbzCharacter.ANDROID_16, DbzCharacter.KRILLIN},
                        lines("android_18.arrive", "android_17.arrive", "trunks.fight"), null)};
                case CELL -> new Wave[]{
                        wave(of(DbzCharacter.CELL_JR, 6), new DbzCharacter[]{DbzCharacter.GOKU_SSJ, DbzCharacter.TRUNKS, DbzCharacter.GOHAN_TEEN,
                                DbzCharacter.PICCOLO, DbzCharacter.KRILLIN, DbzCharacter.ANDROID_16}, lines("cell.games", "cell_jr.arrive"), null),
                        wave(none(), none(), lines("cell.go"), "dbz.gohan_awakens")};
                case BUU -> new Wave[]{wave(of(DbzCharacter.MAJIN_BUU, 1), of(DbzCharacter.GOKU_SSJ, 1), lines("majin_buu.arrive", "goku.buu"), null)};
                case KID_BUU -> new Wave[]{wave(of(DbzCharacter.KID_BUU, 1), of(DbzCharacter.GOKU_SSJ, 1),
                        lines("kid_buu.arrive", "goku.spirit_bomb"), null)};
            };
        }
    }

    private static Wave wave(DbzCharacter[] foes, DbzCharacter[] allies, String[] lines, @Nullable String intro) {
        return new Wave(foes, allies, lines, intro);
    }

    private static DbzCharacter[] of(DbzCharacter who, int count) {
        DbzCharacter[] out = new DbzCharacter[count];
        java.util.Arrays.fill(out, who);
        return out;
    }

    private static DbzCharacter[] none() {
        return new DbzCharacter[0];
    }

    private static String[] lines(String... keys) {
        return keys;
    }

    /** A cutscene waiting for the one playing to end. */
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
        /** Fallen and posed (Yamcha after the Saibaman, kneeling androids): cleared away at the end. */
        final List<UUID> props = new ArrayList<>();
        /** Spectators and the like, cleared away at the end. */
        final List<UUID> extras = new ArrayList<>();
        /** A cutscene (or a scripted pause) holds the waves. */
        boolean busy;
        final List<Queued> queue = new ArrayList<>();
        /** Seconds until {@code then} runs (Cell coming back). */
        int delay;
        @Nullable Runnable then;
        boolean over;
        /** Saibamen: Yamcha's moment has come. */
        boolean yamchaDone;
        /** Cell: the first one has been armed (the one who comes back is the second). */
        boolean firstCellArmed;
        /** Namek's last minutes: seconds left, the bar, the blocks the planet's breaking put in place (put back at the end). */
        int doom = -1;
        @Nullable ServerBossEvent doomBar;
        final Map<BlockPos, BlockState> broken = new LinkedHashMap<>();
        /** Kid Buu: the Spirit Bomb's state. */
        int bombCooldown = 100;
        int bombTicks;

        Fight(Scene scene, BlockPos center, long startedAt) {
            this.scene = scene;
            this.center = center;
            this.anchor = new Vec3(center.getX() + 0.5D, scene.site == Site.CRATER ? center.getY() : center.getY() + 1.0D, center.getZ() + 0.5D);
            this.startedAt = startedAt;
        }
    }

    private static final Map<Planet, Fight> FIGHTS = new EnumMap<>(Planet.class);
    private static final int SPIRIT_BOMB_TICKS = 600;
    /** Namek's last minutes, in seconds. */
    private static final int DOOM_SECONDS = 180;
    private static final String ESCORT = "flightsuit_escort";

    private DbzSaga() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        FIGHTS.clear();
    }

    /** Whether a story fight is on, on this planet (DbzEarth keeps the night's Saibamen away then). */
    public static boolean isFighting(Planet planet) {
        return FIGHTS.containsKey(planet);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % 20 != 11) {
            return;
        }
        Planet planet = Planet.of(level.dimension());
        if (planet != Planet.DBZ_EARTH && planet != Planet.NAMEK) {
            return;
        }
        PlanetData data = PlanetData.get(level.getServer());
        BlockPos landing = data.site(planet);
        if (landing == null) {
            return;
        }
        CompoundTag world = data.world(planet);
        for (Site site : Site.values()) {
            if (site.planet != planet || site == Site.CRATER) {
                continue;
            }
            if (world.contains(site.key())) {
                // M17: the Namekian village grew - one built before is built again (when no fight is on there).
                BlockPos built = BlockPos.of(world.getLong(site.key()));
                if (site == Site.NAMEK_VILLAGE && world.getInt(site.key() + "_layout") < NAMEK_LAYOUT && !FIGHTS.containsKey(planet)
                        && level.isLoaded(built.offset(24, 0, 24)) && level.isLoaded(built.offset(-24, 0, -24))
                        && someoneAt(level, built, 96.0D, site.from)) {
                    namekVillage(level, built);
                    world.putInt(site.key() + "_layout", NAMEK_LAYOUT);
                    data.setDirty();
                }
                continue;
            }
            BlockPos column = site.around(landing);
            if (level.isLoaded(column) && someoneAt(level, column, 96.0D, site.from)) {
                world.putLong(site.key(), build(level, site, column).asLong());
                if (site == Site.NAMEK_VILLAGE) {
                    world.putInt(site.key() + "_layout", NAMEK_LAYOUT);
                }
                data.setDirty();
            }
        }
        keepPeople(level, planet, world);
        tickFight(level, planet, world);
    }

    public static @Nullable BlockPos center(CompoundTag world, Site site) {
        if (site == Site.CRATER) {
            BlockPos crater = DbzEarth.center(world, DbzLandmarks.CRATER);
            return crater == null ? null : DbzLandmarks.CRATER.post(crater);
        }
        return world.contains(site.key()) ? BlockPos.of(world.getLong(site.key())) : null;
    }

    /** A player within {@code range} of {@code pos} who has got at least as far as {@code stage}. */
    private static boolean someoneAt(ServerLevel level, BlockPos pos, double range, DbzStage stage) {
        for (ServerPlayer player : level.players()) {
            DbzStage theirs = PlanetStory.dbzStage(player);
            if (theirs != null && theirs.atLeast(stage) && near(player, pos, range)) {
                return true;
            }
        }
        return false;
    }

    private static boolean near(ServerPlayer player, BlockPos pos, double range) {
        double dx = player.getX() - pos.getX();
        double dz = player.getZ() - pos.getZ();
        return dx * dx + dz * dz < range * range;
    }

    // ---------------------------------------------------------------- people at home

    /** Dende at the Namekian village; Trunks at Capsule Corp once he's come back in time (he arrives in a cutscene). */
    private static void keepPeople(ServerLevel level, Planet planet, CompoundTag world) {
        if (planet == Planet.NAMEK) {
            BlockPos village = center(world, Site.NAMEK_VILLAGE);
            if (village != null) {
                keep(level, DbzCharacter.DENDE, village.offset(0, 1, 3), DbzStage.NAMEK_OPEN);
            }
            return;
        }
        BlockPos capsule = DbzEarth.center(world, DbzLandmarks.CAPSULE_CORP);
        if (capsule == null) {
            return;
        }
        BlockPos post = DbzLandmarks.CAPSULE_CORP.post(capsule).offset(3, 0, 0);
        if (CutsceneRunner.isPlayingNear(level, Vec3.atCenterOf(post))) {
            return;
        }
        // Someone back from Namek who hasn't seen him arrive: he isn't there yet - he comes in the scene, up close.
        PlanetData data = PlanetData.get(level.getServer());
        for (ServerPlayer player : level.players()) {
            if (PlanetStory.dbzStage(player) == DbzStage.FRIEZA_BEATEN && near(player, post, 48.0D)
                    && !data.traveller(player.getUUID()).seenCutscenes.contains("dbz.trunks_arrives")) {
                if (!FIGHTS.containsKey(planet) && near(player, post, 24.0D) && level.isPositionEntityTicking(post)) {
                    // Out in front of the dome, its door behind the time machine.
                    CutsceneRunner.play(level, "dbz.trunks_arrives", new Vec3(capsule.getX() + 0.5D, post.getY(), capsule.getZ() - 15.5D), 0.0F,
                            Map.of(), actors -> {
                            }, false, null);
                }
                return;
            }
        }
        keep(level, DbzCharacter.TRUNKS, post, DbzStage.FRIEZA_BEATEN);
    }

    private static void keep(ServerLevel level, DbzCharacter who, BlockPos post, DbzStage from) {
        if (!level.isPositionEntityTicking(post) || !someoneAt(level, post, 48.0D, from)) {
            return;
        }
        List<DbzFighterEntity> there = level.getEntitiesOfClass(DbzFighterEntity.class, new AABB(post).inflate(24.0D),
                fighter -> fighter.getCharacter() == who && !fighter.isFighting() && !fighter.isActing() && !fighter.isEscort());
        for (int i = 1; i < there.size(); i++) {
            there.get(i).discard();
        }
        if (there.isEmpty()) {
            DbzFighterEntity person = DbzFighterEntity.create(level, who, post, false);
            person.moveTo(post.getX() + 0.5D, post.getY(), post.getZ() + 0.5D, 180.0F, 0.0F);
            level.addFreshEntity(person);
        }
    }

    // ---------------------------------------------------------------- the fights

    private static void tickFight(ServerLevel level, Planet planet, CompoundTag world) {
        Fight fight = FIGHTS.get(planet);
        long now = level.getGameTime();
        if (fight == null) {
            fight = start(level, planet, world);
            if (fight == null) {
                return;
            }
            FIGHTS.put(planet, fight);
            begin(level, fight);
            return;
        }
        if (!anyoneNear(level, fight.center, 96.0D) || now - fight.startedAt > 20L * 60 * 20) {
            end(level, planet, fight);
            return;
        }
        if (!level.isPositionEntityTicking(fight.center)) {
            return;
        }
        if (fight.doom >= 0) {
            tickDoom(level, planet, fight);
            if (fight.over) {
                return;
            }
        }
        if (fight.delay > 0 && --fight.delay == 0 && fight.then != null) {
            Runnable then = fight.then;
            fight.then = null;
            then.run();
        }
        if (fight.busy || fight.delay > 0) {
            return;
        }
        int up = 0;
        int saibamen = 0;
        for (Map.Entry<UUID, DbzCharacter> entry : fight.foes.entrySet()) {
            if (level.getEntity(entry.getKey()) instanceof DbzFighterEntity foe && foe.isAlive() && !foe.isActing()) {
                up++;
                if (entry.getValue() == DbzCharacter.SAIBAMAN) {
                    saibamen++;
                }
            }
        }
        if (fight.scene == Scene.SAIBAMEN_NAPPA && fight.wave == 0 && saibamen == 1 && !fight.yamchaDone) {
            fight.yamchaDone = true;
            yamchaDies(level, fight);
            return;
        }
        if (fight.scene == Scene.KID_BUU) {
            DbzFighterEntity kidBuu = find(level, fight, DbzCharacter.KID_BUU);
            if (kidBuu != null) {
                spiritBomb(level, fight, kidBuu);
            }
        }
        if (up > 0) {
            return;
        }
        Wave[] waves = fight.scene.waves();
        if (fight.wave + 1 < waves.length) {
            nextWave(level, fight);
        } else {
            finish(level, planet, fight);
        }
    }

    private static boolean anyoneNear(ServerLevel level, BlockPos pos, double range) {
        for (ServerPlayer player : level.players()) {
            if (near(player, pos, range) && !player.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Fight start(ServerLevel level, Planet planet, CompoundTag world) {
        long day = level.getDayTime() / 24000L;
        for (Scene scene : Scene.values()) {
            if (scene.site.planet != planet) {
                continue;
            }
            BlockPos center = center(world, scene.site);
            if (center == null || !level.isPositionEntityTicking(center)) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (PlanetStory.dbzStage(player) != scene.from || !near(player, center, 40.0D)) {
                    continue;
                }
                if (scene.nextDay && day < PlanetData.get(level.getServer()).traveller(player.getUUID()).nextBeatDay) {
                    continue;
                }
                return new Fight(scene, center, level.getGameTime());
            }
        }
        return null;
    }

    /** A new fight: the last scene's escorts go (this one brings its own), the stage is set, the first wave comes. */
    private static void begin(ServerLevel level, Fight fight) {
        for (DbzFighterEntity escort : level.getEntitiesOfClass(DbzFighterEntity.class, new AABB(fight.center).inflate(160.0D),
                DbzFighterEntity::isEscort)) {
            escort.discard();
        }
        if (fight.scene == Scene.CELL) {
            cellGames(level, fight);
        }
        nextWave(level, fight);
    }

    private static void nextWave(ServerLevel level, Fight fight) {
        fight.wave++;
        Wave wave = fight.scene.waves()[fight.wave];
        Runnable spawnWave = () -> {
            for (int i = 0; i < wave.foes().length; i++) {
                double angle = i * Math.PI * 2.0D / Math.max(1, wave.foes().length);
                int r = wave.foes().length > 1 ? 6 : 0;
                spawn(level, fight, wave.foes()[i], Mth.floor(Math.cos(angle) * r), Mth.floor(Math.sin(angle) * r) + 3, false);
            }
            for (int i = 0; i < wave.allies().length; i++) {
                if (find(level, fight, wave.allies()[i]) == null) {
                    spawn(level, fight, wave.allies()[i], -6 + i * 2, -6, true);
                }
            }
            for (String key : wave.lines()) {
                int dot = key.indexOf('.');
                DbzCharacter who = DbzCharacter.valueOf(key.substring(0, dot).toUpperCase(java.util.Locale.ROOT));
                announce(level, fight.center, who.line(key.substring(dot + 1)));
            }
            level.playSound(null, fight.center, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5F, 1.4F);
        };
        if (wave.intro() == null) {
            spawnWave.run();
            return;
        }
        // Allies already there (or the wave's own) act in the intro; its fighters who end fighting join the wave.
        for (DbzCharacter ally : wave.allies()) {
            if (find(level, fight, ally) == null) {
                spawn(level, fight, ally, -6, -6, true);
            }
        }
        play(level, fight, wave.intro(), () -> bindAll(level, fight), actors -> {
            DbzFighterEntity gohan = actors.get("gohan");
            if ("dbz.gohan_awakens".equals(wave.intro()) && gohan != null) {
                // Super Saiyan 2: twice the damage from here on.
                gohan.buff(2.0F, 1.0F, 0);
                gohan.setAura(DbzFighterEntity.AURA_GOLD, 0);
            }
            for (int i = 0; i < wave.foes().length; i++) {
                double angle = i * Math.PI * 2.0D / Math.max(1, wave.foes().length);
                int r = wave.foes().length > 1 ? 6 : 0;
                spawn(level, fight, wave.foes()[i], Mth.floor(Math.cos(angle) * r), Mth.floor(Math.sin(angle) * r) + 3, false);
            }
            for (String key : wave.lines()) {
                int dot = key.indexOf('.');
                DbzCharacter who = DbzCharacter.valueOf(key.substring(0, dot).toUpperCase(java.util.Locale.ROOT));
                announce(level, fight.center, who.line(key.substring(dot + 1)));
            }
        });
    }

    private static @Nullable DbzFighterEntity spawn(ServerLevel level, Fight fight, DbzCharacter who, int dx, int dz, boolean ally) {
        int x = fight.center.getX() + dx;
        int z = fight.center.getZ() + dz;
        int y = groundAt(level, fight, x, z);
        DbzFighterEntity fighter = DbzFighterEntity.create(level, who, new BlockPos(x, y, z), true);
        fighter.restrictTo(fight.center, 40);
        fighter.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        if (!level.addFreshEntity(fighter)) {
            return null;
        }
        (ally ? fight.allies : fight.foes).put(fighter.getUUID(), who);
        arm(level, fight, fighter);
        return fighter;
    }

    /** Standing height at x/z near the arena (the crater floor slopes). */
    private static int groundAt(ServerLevel level, Fight fight, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.max((int) Math.floor(fight.anchor.y), Math.min(top, (int) Math.floor(fight.anchor.y) + 6));
    }

    /** The fighter of {@code who} in this fight (foe or ally, standing or posed), or null. */
    private static @Nullable DbzFighterEntity find(ServerLevel level, Fight fight, DbzCharacter who) {
        for (Map<UUID, DbzCharacter> side : List.of(fight.foes, fight.allies)) {
            for (Map.Entry<UUID, DbzCharacter> entry : side.entrySet()) {
                if (level.getEntity(entry.getKey()) instanceof DbzFighterEntity fighter && fighter.isAlive() && fighter.getCharacter() == who) {
                    return fighter;
                }
            }
        }
        for (UUID id : fight.props) {
            if (level.getEntity(id) instanceof DbzFighterEntity fighter && fighter.isAlive() && fighter.getCharacter() == who) {
                return fighter;
            }
        }
        return null;
    }

    /** Everyone in the fight, by cutscene cast id (the character's id: "goku", "raditz"...; Gohan is "gohan", buu forms "buu"). */
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

    /** The cast id the cutscene scripts use for a character. */
    static String castId(DbzCharacter who) {
        return switch (who) {
            case GOKU, GOKU_SSJ -> "goku";
            case GOHAN_KID, GOHAN_TEEN, GOHAN_TEEN_SSJ2 -> "gohan";
            case VEGETA, OOZARU_VEGETA -> "vegeta";
            case FRIEZA, FRIEZA_FINAL -> "frieza";
            case CELL, CELL_IMPERFECT, CELL_SEMI -> "cell";
            case MAJIN_BUU -> "buu";
            case SUPER_BUU, SUPER_BUU_ABSORBED -> "super_buu";
            case ANDROID_16 -> "a16";
            case ANDROID_17 -> "a17";
            case ANDROID_18 -> "a18";
            case MR_SATAN -> "satan";
            default -> who.id();
        };
    }

    /**
     * Plays a cutscene in the fight: the waves wait for it; its fighters who end up fighting join the fight, those
     * left lying are cleared away at the end; then {@code after}. While one plays, the next waits its turn.
     */
    private static void play(ServerLevel level, Fight fight, String id, Supplier<Map<String, DbzFighterEntity>> bound,
                             Consumer<Map<String, DbzFighterEntity>> after) {
        if (fight.busy) {
            fight.queue.add(new Queued(id, bound, after));
            return;
        }
        fight.busy = true;
        CutsceneRunner.play(level, id, fight.anchor, 0.0F, bound.get(), actors -> {
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

    /** After a cutscene: fighters who fight on are in the fight (foe or ally); posed ones are props; the gone are forgotten. */
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
            actor.restrictTo(fight.center, 40);
            (actor.getCharacter().isFoe() ? fight.foes : fight.allies).put(id, actor.getCharacter());
            arm(level, fight, actor);
        }
    }

    // ---------------------------------------------------------------- the story's moments, by who's fighting

    /** Hangs a fighter's story moments on it (health shares where cutscenes play). */
    private static void arm(ServerLevel level, Fight fight, DbzFighterEntity fighter) {
        if (fighter.isArmed()) {
            return;
        }
        fighter.setArmed();
        DbzCharacter who = fighter.getCharacter();
        switch (fight.scene) {
            case RADITZ -> {
                if (who == DbzCharacter.RADITZ) {
                    fighter.atHealth(0.5F, () -> play(level, fight, "dbz.gohan_headbutt", () -> bindAll(level, fight), actors -> {
                        DbzFighterEntity raditz = actors.get("raditz");
                        if (raditz != null) {
                            // Shaken: he takes more from here on.
                            raditz.buff(1.0F, 1.3F, 0);
                        }
                    }));
                    fighter.atHealth(0.1F, () -> play(level, fight, "dbz.special_beam_cannon", () -> bindAll(level, fight),
                            actors -> finish(level, fight.scene.site.planet, fight)));
                }
            }
            case SAIBAMEN_NAPPA -> {
                if (who == DbzCharacter.NAPPA) {
                    fighter.atHealth(0.3F, () -> play(level, fight, "dbz.piccolo_dies", () -> bindAll(level, fight), actors -> {
                        DbzFighterEntity gohan = actors.get("gohan");
                        if (gohan != null) {
                            // "Piccolo-san!": Gohan's rage for ten seconds.
                            gohan.buff(1.5F, 1.0F, 200);
                            gohan.setAura(DbzFighterEntity.AURA_WHITE, 200);
                        }
                    }));
                }
            }
            case VEGETA -> {
                if (who == DbzCharacter.VEGETA) {
                    fighter.atHealth(0.7F, () -> play(level, fight, "dbz.kaioken", () -> bindAll(level, fight), actors -> {
                        DbzFighterEntity goku = actors.get("goku");
                        if (goku != null) {
                            goku.buff(1.5F, 1.0F, 0);
                            goku.setAura(DbzFighterEntity.AURA_RED, 0);
                        }
                    }));
                    fighter.atHealth(0.4F, () -> play(level, fight, "dbz.oozaru", () -> bindAll(level, fight), actors -> {
                        DbzFighterEntity ape = actors.get("vegeta");
                        if (ape == null) {
                            return;
                        }
                        ape.setHealth(ape.getMaxHealth());
                        ape.atHealth(0.3F, () -> play(level, fight, "dbz.tail_cut", () -> bindAll(level, fight), after -> {
                            DbzFighterEntity vegeta = after.get("vegeta");
                            if (vegeta == null) {
                                return;
                            }
                            vegeta.setHealth(Math.min(vegeta.getMaxHealth(), 150.0F));
                            vegeta.atHealth(0.0F, () -> play(level, fight, "dbz.vegeta_spared", () -> bindAll(level, fight),
                                    spared -> finish(level, fight.scene.site.planet, fight)));
                        }));
                    }));
                }
            }
            case FRIEZA -> {
                if (who == DbzCharacter.FRIEZA) {
                    fighter.atHealth(0.5F, () -> {
                        // His final form, then Krillin - and Goku's anger.
                        fighter.setCharacter(DbzCharacter.FRIEZA_FINAL);
                        DbzFighterEntity.transformBurst(level, fighter);
                        play(level, fight, "dbz.krillin_dies_ssj", () -> bindAll(level, fight), actors -> {
                            DbzFighterEntity goku = actors.get("goku");
                            if (goku != null) {
                                goku.buff(2.0F, 1.0F, 0);
                            }
                        });
                    });
                    fighter.atHealth(0.15F, () -> play(level, fight, "dbz.namek_dying", () -> bindAll(level, fight), actors -> startDoom(level, fight)));
                    fighter.atHealth(0.0F, () -> play(level, fight, "dbz.namek_escape", () -> bindAll(level, fight),
                            actors -> finish(level, fight.scene.site.planet, fight)));
                }
            }
            case ANDROIDS -> {
                if (who == DbzCharacter.ANDROID_17 || who == DbzCharacter.ANDROID_18) {
                    fighter.atHealth(0.0F, () -> kneel(level, fight, fighter));
                }
            }
            case CELL -> {
                if (who == DbzCharacter.CELL) {
                    if (!fight.firstCellArmed) {
                        // The first Cell: at a fifth of his health he swells to blow up the Earth.
                        fight.firstCellArmed = true;
                        fighter.atHealth(0.2F, () -> play(level, fight, "dbz.cell_self_destruct", () -> bindAll(level, fight), actors -> {
                            // Gone with Goku... and back, ten seconds later.
                            fight.busy = true;
                            fight.delay = 10;
                            fight.then = () -> {
                                DbzFighterEntity cell = spawn(level, fight, DbzCharacter.CELL, 0, 4, false);
                                if (cell != null) {
                                    cell.setHealth(cell.getMaxHealth() * 0.3F);
                                    DbzFighterEntity.transformBurst(level, cell);
                                    announce(level, fight.center, DbzCharacter.CELL.line("back"));
                                }
                                fight.busy = false;
                            };
                        }));
                    } else {
                        fighter.atHealth(0.0F, () -> play(level, fight, "dbz.father_son_kamehameha", () -> bindAll(level, fight),
                                actors -> finish(level, fight.scene.site.planet, fight)));
                    }
                }
            }
            case BUU -> {
                if (who == DbzCharacter.MAJIN_BUU) {
                    fighter.atHealth(0.3F, () -> {
                        play(level, fight, "dbz.buu_befriends", () -> bindAll(level, fight), actors -> {
                            DbzFighterEntity buu = actors.get("buu");
                            if (buu != null) {
                                buu.setHealth(buu.getMaxHealth());
                            }
                        });
                        play(level, fight, "dbz.super_buu", () -> bindAll(level, fight), actors -> {
                            for (DbzCharacter ally : new DbzCharacter[]{DbzCharacter.GOHAN_TEEN, DbzCharacter.PICCOLO, DbzCharacter.TRUNKS}) {
                                if (find(level, fight, ally) == null) {
                                    spawn(level, fight, ally, -4, -6, true);
                                }
                            }
                        });
                    });
                } else if (who == DbzCharacter.SUPER_BUU) {
                    fighter.atHealth(0.5F, () -> play(level, fight, "dbz.buu_absorbs", () -> bindAll(level, fight), actors -> {
                    }));
                    fighter.atHealth(0.0F, () -> play(level, fight, "dbz.kid_buu", () -> bindAll(level, fight),
                            actors -> finish(level, fight.scene.site.planet, fight)));
                }
            }
            case KID_BUU -> {
                if (who == DbzCharacter.KID_BUU) {
                    fighter.atHealth(0.0F, () -> play(level, fight, "dbz.spirit_bomb_end", () -> bindAll(level, fight),
                            actors -> finish(level, fight.scene.site.planet, fight)));
                }
            }
            default -> {
            }
        }
    }

    /** The last Saibaman lies still; Yamcha walks up to it... */
    private static void yamchaDies(ServerLevel level, Fight fight) {
        play(level, fight, "dbz.yamcha_dies", () -> {
            Map<String, DbzFighterEntity> bound = bindAll(level, fight);
            for (Map.Entry<UUID, DbzCharacter> entry : fight.foes.entrySet()) {
                if (entry.getValue() == DbzCharacter.SAIBAMAN && level.getEntity(entry.getKey()) instanceof DbzFighterEntity last && last.isAlive()) {
                    bound.put("saibaman", last);
                }
            }
            return bound;
        }, actors -> {
        });
    }

    /** An android at the end of its strength kneels (a prop); when both are down, Cell comes for them. */
    private static void kneel(ServerLevel level, Fight fight, DbzFighterEntity android) {
        android.setActing(true);
        android.setAction(DbzAction.DOWN);
        fight.foes.remove(android.getUUID());
        if (!fight.props.contains(android.getUUID())) {
            fight.props.add(android.getUUID());
        }
        announce(level, fight.center, android.getCharacter().line("beaten"));
        boolean bothDown = find(level, fight, DbzCharacter.ANDROID_17) != null && find(level, fight, DbzCharacter.ANDROID_18) != null
                && fight.foes.isEmpty();
        if (bothDown) {
            play(level, fight, "dbz.cell_absorbs_17", () -> bindAll(level, fight), actors -> {
            });
            play(level, fight, "dbz.cell_absorbs_18", () -> bindAll(level, fight), actors -> finish(level, fight.scene.site.planet, fight));
        }
    }

    // ---------------------------------------------------------------- Namek's last minutes

    private static void startDoom(ServerLevel level, Fight fight) {
        fight.doom = DOOM_SECONDS;
        fight.doomBar = new ServerBossEvent(Component.translatable("story.flightsuit.namek_doom", DOOM_SECONDS), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        announce(level, fight.center, Component.translatable("story.flightsuit.namek_doom_start").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Every second: lightning, the bar running down, and every ten seconds the ground breaking open into lava. */
    private static void tickDoom(ServerLevel level, Planet planet, Fight fight) {
        fight.doom--;
        ServerBossEvent bar = fight.doomBar;
        if (bar != null) {
            bar.setName(Component.translatable("story.flightsuit.namek_doom", Math.max(0, fight.doom)));
            bar.setProgress(Math.max(0.0F, fight.doom / (float) DOOM_SECONDS));
            for (ServerPlayer player : level.players()) {
                if (near(player, fight.center, 96.0D)) {
                    bar.addPlayer(player);
                } else {
                    bar.removePlayer(player);
                }
            }
        }
        if (level.random.nextInt(2) == 0) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double r = 6.0D + level.random.nextDouble() * 20.0D;
            CutsceneRunner.burst(level, fight.anchor.add(Math.cos(angle) * r, 0.0D, Math.sin(angle) * r), com.pfkfks.flightsuit.cutscene.Cutscene.BURST_LIGHTNING);
        }
        if (fight.doom % 10 == 0 && fight.doom > 0) {
            breakGround(level, fight);
        }
        if (fight.doom <= 0) {
            // Out of time: Namek goes - and you're pulled out to try again.
            announce(level, fight.center, Component.translatable("story.flightsuit.namek_doom_fail").withStyle(ChatFormatting.RED));
            CutsceneRunner.shake(level, fight.anchor, 96.0D, 40);
            end(level, planet, fight);
        }
    }

    /** A crack in the ground: magma round a pit of lava (all put back when the fight's over). */
    private static void breakGround(ServerLevel level, Fight fight) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double r = 3.0D + level.random.nextDouble() * 10.0D;
        int x = Mth.floor(fight.anchor.x + Math.cos(angle) * r);
        int z = Mth.floor(fight.anchor.z + Math.sin(angle) * r);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                fight.broken.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, dx == 0 && dz == 0 ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            }
        }
        CutsceneRunner.burst(level, new Vec3(x + 0.5D, y + 1.0D, z + 0.5D), com.pfkfks.flightsuit.cutscene.Cutscene.BURST_LAVA);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    // ---------------------------------------------------------------- the Cell Games

    /** The stands round the ring (built once), the crowd in them, and Mr. Satan at the ringside. */
    private static void cellGames(ServerLevel level, Fight fight) {
        BlockPos c = fight.center;
        if (!level.getBlockState(c.offset(0, 1, 12)).is(Blocks.QUARTZ_STAIRS)) {
            for (int side = -1; side <= 1; side += 2) {
                for (int dx = -7; dx <= 7; dx++) {
                    for (int row = 0; row < 3; row++) {
                        BlockPos seat = c.offset(dx, 1 + row, side * (12 + row));
                        level.setBlock(seat, Blocks.QUARTZ_STAIRS.defaultBlockState()
                                .setValue(StairBlock.FACING, side < 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH), 3);
                        for (int under = 1; under <= row; under++) {
                            level.setBlock(seat.below(under), Blocks.SMOOTH_QUARTZ.defaultBlockState(), 3);
                        }
                    }
                }
            }
            // Flags and torches at the corners of the ring.
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    BlockPos post = c.offset(sx * 10, 1, sz * 10);
                    for (int dy = 0; dy < 4; dy++) {
                        level.setBlock(post.above(dy), Blocks.OAK_FENCE.defaultBlockState(), 3);
                    }
                    level.setBlock(post.above(4), Blocks.TORCH.defaultBlockState(), 3);
                    level.setBlock(post.above(3).offset(sx, 0, 0), sx * sz > 0 ? Blocks.RED_WOOL.defaultBlockState() : Blocks.YELLOW_WOOL.defaultBlockState(), 3);
                    level.setBlock(post.above(2).offset(sx, 0, 0), Blocks.WHITE_WOOL.defaultBlockState(), 3);
                }
            }
        }
        com.pfkfks.flightsuit.town.TownPlan plan = com.pfkfks.flightsuit.town.TownPlan.westCity(c);
        com.pfkfks.flightsuit.town.TownRole[] crowd = {com.pfkfks.flightsuit.town.TownRole.DBZ_CITIZEN,
                com.pfkfks.flightsuit.town.TownRole.DBZ_CITIZEN, com.pfkfks.flightsuit.town.TownRole.DBZ_CHILD};
        for (int i = 0; i < 10; i++) {
            int side = i % 2 == 0 ? -1 : 1;
            int row = (i / 2) % 3;
            BlockPos seat = c.offset(-6 + (i / 2) * 3, 1 + row, side * (12 + row));
            com.pfkfks.flightsuit.town.TownsfolkEntity fan = com.pfkfks.flightsuit.town.TownsfolkEntity.create(level,
                    com.pfkfks.flightsuit.town.TownsfolkEntity.WEST_CITY, plan, crowd[i % crowd.length], i);
            fan.moveTo(seat.getX() + 0.5D, seat.getY() + 0.5D, seat.getZ() + 0.5D, side < 0 ? 0.0F : 180.0F, 0.0F);
            fan.setNoAi(true);
            fan.setYHeadRot(side < 0 ? 0.0F : 180.0F);
            if (level.addFreshEntity(fan)) {
                fight.extras.add(fan.getUUID());
            }
        }
        DbzFighterEntity satan = DbzFighterEntity.create(level, DbzCharacter.MR_SATAN, c.offset(9, 1, 0), false);
        satan.moveTo(c.getX() + 9.5D, c.getY() + 1.0D, c.getZ() + 0.5D, 90.0F, 0.0F);
        if (level.addFreshEntity(satan)) {
            fight.extras.add(satan.getUUID());
        }
        announce(level, c, Component.translatable("story.flightsuit.cell_games.announcer_1").withStyle(ChatFormatting.YELLOW));
        announce(level, c, Component.translatable("story.flightsuit.cell_games.announcer_2").withStyle(ChatFormatting.YELLOW));
        announce(level, c, DbzCharacter.MR_SATAN.line("games"));
        level.playSound(null, c, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 2.0F, 1.0F);
    }

    // ---------------------------------------------------------------- the Spirit Bomb

    /**
     * Goku gathers the Spirit Bomb (half a minute, hands up), then throws it at Kid Buu: the more people live in
     * the players' villages and the more of their suits fight there, the harder it hits. Then again, until Buu falls.
     */
    private static void spiritBomb(ServerLevel level, Fight fight, DbzFighterEntity kidBuu) {
        DbzFighterEntity goku = find(level, fight, DbzCharacter.GOKU_SSJ);
        if (goku == null) {
            goku = find(level, fight, DbzCharacter.GOKU);
        }
        if (goku == null || goku.isActing()) {
            return;
        }
        if (fight.bombTicks > 0) {
            fight.bombTicks -= 20;
            if (fight.bombTicks > 0) {
                return;
            }
            int energy = energy(level, fight.center);
            float damage = 200.0F + 60.0F * energy;
            for (int k = 0; k < 20; k++) {
                level.sendParticles(ParticleTypes.END_ROD, kidBuu.getX(), kidBuu.getY() + 20.0D - k, kidBuu.getZ(), 20, 1.6D, 1.6D, 1.6D, 0.0D);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, kidBuu.getX(), kidBuu.getY() + 1.0D, kidBuu.getZ(), 3, 1.0D, 1.0D, 1.0D, 0.0D);
            level.playSound(null, kidBuu.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.5F);
            kidBuu.invulnerableTime = 0;
            kidBuu.hurt(goku.damageSources().mobAttack(goku), damage);
            announce(level, fight.center, Component.translatable("story.flightsuit.spirit_bomb_hit", energy, (int) damage).withStyle(ChatFormatting.GOLD));
            fight.bombCooldown = 400;
            return;
        }
        if (fight.bombCooldown > 0) {
            fight.bombCooldown -= 20;
            return;
        }
        goku.channel(SPIRIT_BOMB_TICKS);
        fight.bombTicks = SPIRIT_BOMB_TICKS;
        announce(level, fight.center, DbzCharacter.GOKU.line("gather", energy(level, fight.center)));
    }

    /** The Spirit Bomb's strength: everyone in the players' home villages, every suit fighting beside them, and them. */
    private static int energy(ServerLevel level, BlockPos center) {
        int energy = 0;
        WarData war = WarData.get(level.getServer());
        for (ServerPlayer player : level.players()) {
            if (!near(player, center, 96.0D)) {
                continue;
            }
            energy++;
            WarData.VillageRecord village = war.homeVillage(player.getUUID());
            if (village != null) {
                energy += village.population;
            }
            energy += Companions.owned(player, 96.0D).size();
        }
        return energy;
    }

    // ---------------------------------------------------------------- the end of a scene

    /** The scene is won: everyone on that part of the story nearby moves on, with the scene's rewards. */
    private static void finish(ServerLevel level, Planet planet, Fight fight) {
        if (fight.over) {
            return;
        }
        PlanetData data = PlanetData.get(level.getServer());
        long day = level.getDayTime() / 24000L;
        ServerPlayer lead = null;
        for (ServerPlayer player : level.players()) {
            if (!near(player, fight.center, 96.0D) || PlanetStory.dbzStage(player) != fight.scene.from) {
                continue;
            }
            lead = lead == null ? player : lead;
            PlanetStory.setStage(player, fight.scene.to);
            data.traveller(player.getUUID()).nextBeatDay = day + 1;
            switch (fight.scene) {
                case RADITZ -> reward(player, new ItemStack(ModItems.SCOUTER.get()));
                case SAIBAMEN_NAPPA -> reward(player, new ItemStack(ModItems.SENZU_BEAN.get(), 1));
                case VEGETA -> {
                    data.traveller(player.getUUID()).cleared.merge(Planet.DBZ_EARTH, 1, Integer::sum);
                    reward(player, new ItemStack(ModItems.SENZU_BEAN.get(), 4), new ItemStack(Items.DIAMOND, 8),
                            new ItemStack(ModItems.ARC_REACTOR.get(), 2));
                }
                case ZARBON -> reward(player, new ItemStack(ModItems.SENZU_BEAN.get(), 2), new ItemStack(Items.GOLD_INGOT, 8));
                case GINYU -> reward(player, new ItemStack(ModItems.SENZU_BEAN.get(), 2), new ItemStack(Items.DIAMOND, 4));
                case FRIEZA -> {
                    data.traveller(player.getUUID()).cleared.merge(Planet.NAMEK, 1, Integer::sum);
                    reward(player, new ItemStack(ModItems.SENZU_BEAN.get(), 4), new ItemStack(Items.DIAMOND, 12),
                            new ItemStack(Items.NETHERITE_INGOT), new ItemStack(ModItems.ARC_REACTOR.get(), 2));
                }
                case ANDROIDS -> reward(player, new ItemStack(ModItems.SENZU_BEAN.get(), 2), new ItemStack(Items.DIAMOND, 4),
                        new ItemStack(Items.REDSTONE_BLOCK, 4));
                case CELL -> reward(player, ModItems.capsuleFor(SuitType.TRUNKS_MK5).createFilledCapsule(),
                        new ItemStack(ModItems.TRUNKS_SWORD.get()), new ItemStack(ModItems.SENZU_BEAN.get(), 4), new ItemStack(Items.DIAMOND, 12));
                case BUU -> reward(player, new ItemStack(ModItems.SENZU_BEAN.get(), 2), new ItemStack(Items.DIAMOND, 6));
                case KID_BUU -> reward(player, new ItemStack(ModItems.SENZU_BEAN.get(), 6), new ItemStack(Items.DIAMOND, 16),
                        new ItemStack(Items.NETHER_STAR), new ItemStack(Items.TOTEM_OF_UNDYING, 2));
            }
            player.sendSystemMessage(Component.translatable("story.flightsuit.dbz.done." + fight.scene.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            PlanetStory.objective(player);
        }
        data.setDirty();
        // Our side flies along with the lead player for a while.
        if (lead != null) {
            for (UUID id : fight.allies.keySet()) {
                if (level.getEntity(id) instanceof DbzFighterEntity ally && ally.isAlive() && !ally.isActing()) {
                    ally.escort(lead.getUUID());
                }
            }
            fight.allies.clear();
        }
        end(level, planet, fight);
    }

    private static void reward(ServerPlayer player, ItemStack... stacks) {
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static void end(ServerLevel level, Planet planet, Fight fight) {
        fight.over = true;
        List<UUID> all = new ArrayList<>(fight.foes.keySet());
        all.addAll(fight.allies.keySet());
        all.addAll(fight.props);
        all.addAll(fight.extras);
        for (UUID id : all) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        if (fight.doomBar != null) {
            fight.doomBar.removeAllPlayers();
        }
        // Namek's broken ground mends.
        fight.broken.forEach((pos, state) -> level.setBlock(pos, state, 3));
        FIGHTS.remove(planet);
    }

    private static void announce(ServerLevel level, BlockPos at, Component line) {
        for (ServerPlayer player : level.players()) {
            if (near(player, at, 128.0D)) {
                player.sendSystemMessage(line);
            }
        }
    }

    // ---------------------------------------------------------------- the sights

    /** Builds a sight on the ground at {@code column}; returns its centre at ground level. */
    private static BlockPos build(ServerLevel level, Site site, BlockPos column) {
        BlockPos ground = DragonBalls.firmGround(level, column.getX(), column.getZ());
        int y = ground != null ? ground.getY() - 1 : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
        BlockPos center = new BlockPos(column.getX(), y, column.getZ());
        switch (site) {
            case NAMEK_VILLAGE -> namekVillage(level, center);
            case FRIEZA_SHIP -> friezaShip(level, center);
            case GINYU_FIELD -> rockyField(level, center, Blocks.STONE.defaultBlockState(), 14);
            case ANDROID_ROAD -> ruinedRoad(level, center);
            case CELL_RING -> cellRing(level, center);
            case BABIDI_SHIP -> babidiShip(level, center);
            case WASTELAND -> rockyField(level, center, Blocks.COARSE_DIRT.defaultBlockState(), 18);
            default -> {
            }
        }
        return center;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** A flat round of ground (the arena), the air above it cleared. */
    private static void clearRound(ServerLevel level, BlockPos center, int radius, BlockState floor) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                set(level, center.offset(dx, 0, dz), floor);
                for (int dy = 1; dy <= 8; dy++) {
                    set(level, center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** A Namekian dome: a half sphere of white with round green windows, a doorway on {@code door}'s side. */
    static void dome(ServerLevel level, BlockPos base, int radius, net.minecraft.core.Direction door) {
        BlockState wall = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState window = Blocks.LIME_STAINED_GLASS.defaultBlockState();
        for (int dy = 0; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    BlockPos pos = base.offset(dx, dy + 1, dz);
                    if (d <= radius + 0.5D && d > radius - 0.6D) {
                        set(level, pos, dy == 2 && (dx == 0 || dz == 0) ? window : wall);
                    } else if (d <= radius - 0.6D) {
                        set(level, pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        BlockPos doorway = base.relative(door, radius);
        set(level, doorway.above(), Blocks.AIR.defaultBlockState());
        set(level, doorway.above(2), Blocks.AIR.defaultBlockState());
        set(level, base.offset(0, radius, 0), Blocks.SEA_LANTERN.defaultBlockState());
    }

    /** The Namekian village's layout (M17 bundle D: ten domes round the green); older villages are rebuilt once. */
    public static final int NAMEK_LAYOUT = 2;
    /** The domes (x, z from the village centre), their doors facing the green - the villagers' homes (town.TownPlan.namek). */
    public static final int[][] NAMEK_DOMES = namekDomes();

    private static int[][] namekDomes() {
        int[][] out = new int[10][];
        for (int i = 0; i < out.length; i++) {
            double angle = Math.toRadians(i * 36.0D + 18.0D);
            out[i] = new int[]{(int) Math.round(Math.cos(angle) * 17.0D), (int) Math.round(Math.sin(angle) * 17.0D)};
        }
        return out;
    }

    /** Which way a dome at (x, z) opens: towards the green in the middle. */
    public static net.minecraft.core.Direction namekDoor(int[] at) {
        if (Math.abs(at[0]) >= Math.abs(at[1])) {
            return at[0] > 0 ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST;
        }
        return at[1] > 0 ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
    }

    private static void namekVillage(ServerLevel level, BlockPos center) {
        clearRound(level, center, 22, Blocks.GRASS_BLOCK.defaultBlockState());
        for (int[] at : NAMEK_DOMES) {
            dome(level, center.offset(at[0], 0, at[1]), 3, namekDoor(at));
        }
        // Ajisa trees between the green and the domes: a tall trunk under a round blue-green crown.
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(60.0D + i * 120.0D);
            BlockPos tree = center.offset((int) Math.round(Math.cos(angle) * 11.0D), 0, (int) Math.round(Math.sin(angle) * 11.0D));
            for (int dy = 1; dy <= 7; dy++) {
                set(level, tree.above(dy), Blocks.OAK_LOG.defaultBlockState());
            }
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        if (dx * dx + dy * dy + dz * dz <= 5) {
                            set(level, tree.offset(dx, 9 + dy, dz), Blocks.WARPED_WART_BLOCK.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    /** Frieza's ship: a broad white disk on three legs, a ring of windows, lights round the rim, a ramp down. */
    private static void friezaShip(ServerLevel level, BlockPos center) {
        clearRound(level, center, 16, Blocks.GRASS_BLOCK.defaultBlockState());
        int r = 9;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5D) {
                    continue;
                }
                set(level, center.offset(dx, 4, dz), Blocks.WHITE_CONCRETE.defaultBlockState());
                if (d <= r - 1.5D) {
                    set(level, center.offset(dx, 8, dz), Blocks.WHITE_CONCRETE.defaultBlockState());
                }
                if (d > r - 1.5D) {
                    BlockState rim = (dx + dz) % 3 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                    for (int dy = 5; dy <= 7; dy++) {
                        set(level, center.offset(dx, dy, dz), dy == 6 ? rim : Blocks.WHITE_CONCRETE.defaultBlockState());
                    }
                    if ((dx * 7 + dz * 3) % 5 == 0) {
                        set(level, center.offset(dx, 3, dz), Blocks.SEA_LANTERN.defaultBlockState());
                    }
                }
            }
        }
        for (int[] leg : new int[][]{{0, -6}, {5, 4}, {-5, 4}}) {
            for (int dy = 1; dy <= 3; dy++) {
                set(level, center.offset(leg[0], dy, leg[1]), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
            }
        }
        for (int i = 0; i < 4; i++) {
            set(level, center.offset(0, 1 + i, r + 1 + 3 - i), Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, net.minecraft.core.Direction.NORTH));
        }
        set(level, center.offset(0, 9, 0), Blocks.BEACON.defaultBlockState());
    }

    /** An open rocky arena: a round of {@code floor}, spires of stone round its edge. */
    private static void rockyField(ServerLevel level, BlockPos center, BlockState floor, int radius) {
        clearRound(level, center, radius, floor);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0D + 0.3D;
            int x = Mth.floor(Math.cos(angle) * (radius - 2));
            int z = Mth.floor(Math.sin(angle) * (radius - 2));
            int height = 4 + (i * 5) % 6;
            for (int dy = 1; dy <= height; dy++) {
                set(level, center.offset(x, dy, z), dy % 3 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
                if (dy < height - 1) {
                    set(level, center.offset(x + 1, dy, z), Blocks.STONE.defaultBlockState());
                }
            }
        }
    }

    /** The ruined road where the androids struck: a cracked highway, burnt-out cars, rubble. */
    private static void ruinedRoad(ServerLevel level, BlockPos center) {
        clearRound(level, center, 16, Blocks.COARSE_DIRT.defaultBlockState());
        for (int dx = -16; dx <= 16; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean broken = (dx * 7 + dz * 13) % 11 == 0;
                set(level, center.offset(dx, 0, dz), broken ? Blocks.GRAVEL.defaultBlockState()
                        : dz == 0 && dx % 4 != 0 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : Blocks.GRAY_CONCRETE.defaultBlockState());
            }
        }
        for (int[] car : new int[][]{{-8, -1}, {5, 1}, {11, -1}}) {
            set(level, center.offset(car[0], 1, car[1]), Blocks.IRON_BLOCK.defaultBlockState());
            set(level, center.offset(car[0] + 1, 1, car[1]), Blocks.IRON_BLOCK.defaultBlockState());
            set(level, center.offset(car[0], 2, car[1]), Blocks.BLACK_STAINED_GLASS.defaultBlockState());
            set(level, center.offset(car[0] + 1, 1, car[1] + (car[1] > 0 ? 1 : -1)), Blocks.FIRE.defaultBlockState());
        }
    }

    /** The Cell Games ring: a raised square of white tiles, a post at each corner (the stands go up for the games). */
    private static void cellRing(ServerLevel level, BlockPos center) {
        clearRound(level, center, 18, Blocks.COARSE_DIRT.defaultBlockState());
        int r = 8;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                boolean grid = Math.floorMod(dx, 3) == 0 || Math.floorMod(dz, 3) == 0;
                set(level, center.offset(dx, 1, dz), grid ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState());
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int dy = 2; dy <= 5; dy++) {
                    set(level, center.offset(sx * r, dy, sz * r), Blocks.QUARTZ_PILLAR.defaultBlockState());
                }
            }
        }
    }

    /** Babidi's ship: a great brown dome half sunk in the ground, a dark doorway. */
    private static void babidiShip(ServerLevel level, BlockPos center) {
        clearRound(level, center, 14, Blocks.COARSE_DIRT.defaultBlockState());
        int r = 7;
        for (int dy = 0; dy <= r - 2; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    double d = Math.sqrt(dx * dx + (dy + 2) * (dy + 2) + dz * dz);
                    if (d <= r + 0.5D && d > r - 0.6D) {
                        set(level, center.offset(dx, dy + 1, dz), (dx + dy) % 4 == 0 ? Blocks.BROWN_TERRACOTTA.defaultBlockState()
                                : Blocks.TERRACOTTA.defaultBlockState());
                    }
                }
            }
        }
        set(level, center.offset(0, 1, r - 1), Blocks.AIR.defaultBlockState());
        set(level, center.offset(0, 2, r - 1), Blocks.AIR.defaultBlockState());
        set(level, center.offset(0, 3, r - 2), Blocks.REDSTONE_LAMP.defaultBlockState());
    }

    /** Where the next scene is, for "/planet" (null if its sight isn't up yet). */
    public static @Nullable BlockPos target(PlanetData data, Site site) {
        BlockPos landing = data.site(site.planet);
        if (landing == null) {
            return null;
        }
        BlockPos built = center(data.world(site.planet), site);
        if (site == Site.CRATER) {
            return built != null ? built : DbzLandmarks.CRATER.around(landing);
        }
        return built != null ? built : site.around(landing);
    }

    /** For the test command: the fight on this planet ends now (nothing is won). */
    public static void stopFight(ServerLevel level, Planet planet) {
        Fight fight = FIGHTS.get(planet);
        if (fight != null) {
            end(level, planet, fight);
        }
    }
}
