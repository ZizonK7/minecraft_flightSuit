package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.FlightSuitMod;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Dragon Ball story after chapter 1 (after the M16 test: "finish the story after the Dragon Balls"; DESIGN.md
 * 4-16's table). Chapter 1 (the Saiyans) stays in DbzEarth; this runs the rest, scene by scene, each a fight
 * in waves at a sight that's built when someone on that part of the story comes near:
 * - chapter 2, Namek: Dende at the Namekian village → Frieza's men, Dodoria and Zarbon raid it → the Ginyu Force
 *   on the rocky field (Goku lands for Ginyu) → Frieza at his ship (Goku comes when Frieza takes his final form);
 * - chapter 3, back on Earth: Trunks at Capsule Corp warns of the androids → 17 and 18 on the ruined road (Trunks
 *   with you) → a day later the Cell Games: Cell Juniors, then Cell (Goku and Trunks with you);
 * - chapter 4: Majin Buu at Babidi's ship (with Goku) → Kid Buu in the wastes, where Goku gathers a Spirit Bomb
 *   while you hold Buu off - it's stronger the more people live in your village and the more suits fight with you.
 * Never saved: a fight left (nobody within 96 blocks) or too long (20 minutes) starts over next time.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DbzSaga {
    /** A sight of chapters 2-4: on which planet, where (from the landing site), from which point in the story. */
    public enum Site {
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

        String key() {
            return "saga_" + name().toLowerCase(java.util.Locale.ROOT);
        }

        public BlockPos around(BlockPos site) {
            return site.offset(dx, 0, dz);
        }

        public Planet planet() {
            return planet;
        }
    }

    /** One wave: who comes, who fights beside you, what's said. */
    private record Wave(DbzCharacter[] foes, DbzCharacter[] allies, String[] lines) {
    }

    /** A scene: its sight, the point in the story it starts at and moves on to, its waves. */
    private enum Scene {
        ZARBON(Site.NAMEK_VILLAGE, DbzStage.MET_DENDE, DbzStage.ZARBON_BEATEN, false,
                new Wave(of(DbzCharacter.FRIEZA_SOLDIER, 6), none(), lines("frieza_soldier.arrive")),
                new Wave(of(DbzCharacter.DODORIA, 1), none(), lines("dodoria.arrive")),
                new Wave(of(DbzCharacter.ZARBON, 1), none(), lines("zarbon.arrive"))),
        GINYU(Site.GINYU_FIELD, DbzStage.ZARBON_BEATEN, DbzStage.GINYU_BEATEN, false,
                new Wave(of(DbzCharacter.GULDO, 1), none(), lines("ginyu.pose", "guldo.arrive")),
                new Wave(of(DbzCharacter.RECOOME, 1), none(), lines("recoome.arrive")),
                new Wave(new DbzCharacter[]{DbzCharacter.BURTER, DbzCharacter.JEICE}, none(), lines("burter.arrive", "jeice.arrive")),
                new Wave(of(DbzCharacter.GINYU, 1), of(DbzCharacter.GOKU, 1), lines("ginyu.arrive", "goku.namek"))),
        FRIEZA(Site.FRIEZA_SHIP, DbzStage.GINYU_BEATEN, DbzStage.FRIEZA_BEATEN, false,
                new Wave(of(DbzCharacter.FRIEZA_SOLDIER, 5), none(), lines("frieza.arrive")),
                new Wave(of(DbzCharacter.FRIEZA, 1), none(), lines("frieza.go"))),
        ANDROIDS(Site.ANDROID_ROAD, DbzStage.MET_TRUNKS, DbzStage.ANDROIDS_BEATEN, false,
                new Wave(new DbzCharacter[]{DbzCharacter.ANDROID_17, DbzCharacter.ANDROID_18}, of(DbzCharacter.TRUNKS, 1),
                        lines("android_18.arrive", "android_17.arrive", "trunks.fight"))),
        CELL(Site.CELL_RING, DbzStage.ANDROIDS_BEATEN, DbzStage.CELL_BEATEN, true,
                new Wave(of(DbzCharacter.CELL_JR, 6), none(), lines("cell.games", "cell_jr.arrive")),
                new Wave(of(DbzCharacter.CELL, 1), new DbzCharacter[]{DbzCharacter.GOKU, DbzCharacter.TRUNKS}, lines("cell.go", "goku.cell"))),
        BUU(Site.BABIDI_SHIP, DbzStage.CELL_BEATEN, DbzStage.BUU_BEATEN, false,
                new Wave(of(DbzCharacter.MAJIN_BUU, 1), of(DbzCharacter.GOKU, 1), lines("majin_buu.arrive", "goku.buu"))),
        KID_BUU(Site.WASTELAND, DbzStage.BUU_BEATEN, DbzStage.KID_BUU_BEATEN, false,
                new Wave(of(DbzCharacter.KID_BUU, 1), of(DbzCharacter.GOKU, 1), lines("kid_buu.arrive", "goku.spirit_bomb")));

        final Site site;
        final DbzStage from;
        final DbzStage to;
        /** Starts only on the day set when the scene before ended (the Cell Games are "tomorrow"). */
        final boolean nextDay;
        final Wave[] waves;

        Scene(Site site, DbzStage from, DbzStage to, boolean nextDay, Wave... waves) {
            this.site = site;
            this.from = from;
            this.to = to;
            this.nextDay = nextDay;
            this.waves = waves;
        }
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

    private static final class Fight {
        final Scene scene;
        final BlockPos center;
        final long startedAt;
        int wave = -1;
        final Map<UUID, DbzCharacter> foes = new HashMap<>();
        final List<UUID> allies = new ArrayList<>();
        /** Frieza: Goku has come. Kid Buu: the Spirit Bomb's state. */
        boolean gokuCame;
        int bombCooldown = 100;
        int bombTicks;

        Fight(Scene scene, BlockPos center, long startedAt) {
            this.scene = scene;
            this.center = center;
            this.startedAt = startedAt;
        }
    }

    private static final Map<Planet, Fight> FIGHTS = new EnumMap<>(Planet.class);
    private static final int SPIRIT_BOMB_TICKS = 600;

    private DbzSaga() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        FIGHTS.clear();
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
            if (site.planet != planet || world.contains(site.key())) {
                continue;
            }
            BlockPos column = site.around(landing);
            if (level.isLoaded(column) && someoneAt(level, column, 96.0D, site.from)) {
                world.putLong(site.key(), build(level, site, column).asLong());
                data.setDirty();
            }
        }
        keepPeople(level, planet, world);
        tickFight(level, planet, world);
    }

    public static @Nullable BlockPos center(CompoundTag world, Site site) {
        return world.contains(site.key()) ? BlockPos.of(world.getLong(site.key())) : null;
    }

    /** A player within {@code range} of {@code pos} who has got at least as far as {@code stage}. */
    private static boolean someoneAt(ServerLevel level, BlockPos pos, double range, DbzStage stage) {
        for (ServerPlayer player : level.players()) {
            DbzStage theirs = PlanetStory.dbzStage(player);
            if (theirs != null && theirs.ordinal() >= stage.ordinal() && near(player, pos, range)) {
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

    /** Dende at the Namekian village; Trunks at Capsule Corp once he's come back in time. */
    private static void keepPeople(ServerLevel level, Planet planet, CompoundTag world) {
        if (planet == Planet.NAMEK) {
            BlockPos village = center(world, Site.NAMEK_VILLAGE);
            if (village != null) {
                keep(level, DbzCharacter.DENDE, village.offset(0, 1, 3), DbzStage.NAMEK_OPEN);
            }
        } else {
            BlockPos capsule = DbzEarth.center(world, DbzLandmarks.CAPSULE_CORP);
            if (capsule != null) {
                keep(level, DbzCharacter.TRUNKS, DbzLandmarks.CAPSULE_CORP.post(capsule).offset(3, 0, 0), DbzStage.FRIEZA_BEATEN);
            }
        }
    }

    private static void keep(ServerLevel level, DbzCharacter who, BlockPos post, DbzStage from) {
        if (!level.isPositionEntityTicking(post) || !someoneAt(level, post, 48.0D, from)) {
            return;
        }
        List<DbzFighterEntity> there = level.getEntitiesOfClass(DbzFighterEntity.class, new AABB(post).inflate(24.0D),
                fighter -> fighter.getCharacter() == who && !fighter.isFighting());
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
        }
        if (!anyoneNear(level, fight.center, 96.0D) || now - fight.startedAt > 20L * 60 * 20) {
            end(level, planet, fight);
            return;
        }
        if (!level.isPositionEntityTicking(fight.center)) {
            return;
        }
        int up = 0;
        DbzFighterEntity frieza = null;
        DbzFighterEntity kidBuu = null;
        for (Map.Entry<UUID, DbzCharacter> entry : fight.foes.entrySet()) {
            if (level.getEntity(entry.getKey()) instanceof DbzFighterEntity foe && foe.isAlive()) {
                up++;
                if (entry.getValue() == DbzCharacter.FRIEZA) {
                    frieza = foe;
                } else if (entry.getValue() == DbzCharacter.KID_BUU) {
                    kidBuu = foe;
                }
            }
        }
        if (fight.scene == Scene.FRIEZA && frieza != null && frieza.isTransformed() && !fight.gokuCame) {
            fight.gokuCame = true;
            spawn(level, fight, DbzCharacter.GOKU, -6, -6, true);
            announce(level, fight.center, DbzCharacter.GOKU.line("super_saiyan"));
        }
        if (fight.scene == Scene.KID_BUU && kidBuu != null) {
            spiritBomb(level, fight, kidBuu);
        }
        if (up > 0) {
            return;
        }
        if (fight.wave + 1 < fight.scene.waves.length) {
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

    private static void nextWave(ServerLevel level, Fight fight) {
        fight.wave++;
        Wave wave = fight.scene.waves[fight.wave];
        for (int i = 0; i < wave.foes().length; i++) {
            double angle = i * Math.PI * 2.0D / Math.max(1, wave.foes().length);
            int r = wave.foes().length > 1 ? 6 : 0;
            spawn(level, fight, wave.foes()[i], Mth.floor(Math.cos(angle) * r), Mth.floor(Math.sin(angle) * r), false);
        }
        for (int i = 0; i < wave.allies().length; i++) {
            spawn(level, fight, wave.allies()[i], -6 + i * 3, -6, true);
        }
        for (String key : wave.lines()) {
            int dot = key.indexOf('.');
            DbzCharacter who = DbzCharacter.valueOf(key.substring(0, dot).toUpperCase(java.util.Locale.ROOT));
            announce(level, fight.center, who.line(key.substring(dot + 1)));
        }
        level.playSound(null, fight.center, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5F, 1.4F);
    }

    private static void spawn(ServerLevel level, Fight fight, DbzCharacter who, int dx, int dz, boolean ally) {
        int x = fight.center.getX() + dx;
        int z = fight.center.getZ() + dz;
        int y = Math.max(fight.center.getY() + 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        DbzFighterEntity fighter = DbzFighterEntity.create(level, who, new BlockPos(x, y, z), true);
        fighter.restrictTo(fight.center, 40);
        fighter.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        if (level.addFreshEntity(fighter)) {
            if (ally) {
                fight.allies.add(fighter.getUUID());
            } else {
                fight.foes.put(fighter.getUUID(), who);
            }
        }
    }

    /**
     * Goku gathers the Spirit Bomb (half a minute, hands up), then throws it at Kid Buu: the more people live in
     * the players' villages and the more of their suits fight there, the harder it hits. Then again, until Buu falls.
     */
    private static void spiritBomb(ServerLevel level, Fight fight, DbzFighterEntity kidBuu) {
        DbzFighterEntity goku = null;
        for (UUID id : fight.allies) {
            if (level.getEntity(id) instanceof DbzFighterEntity ally && ally.getCharacter() == DbzCharacter.GOKU && ally.isAlive()) {
                goku = ally;
            }
        }
        if (goku == null) {
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

    /** Every wave beaten: everyone on that part of the story nearby moves on, with the scene's rewards. */
    private static void finish(ServerLevel level, Planet planet, Fight fight) {
        PlanetData data = PlanetData.get(level.getServer());
        long day = level.getDayTime() / 24000L;
        for (ServerPlayer player : level.players()) {
            if (!near(player, fight.center, 96.0D) || PlanetStory.dbzStage(player) != fight.scene.from) {
                continue;
            }
            PlanetStory.setStage(player, fight.scene.to);
            data.traveller(player.getUUID()).nextBeatDay = day + 1;
            switch (fight.scene) {
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
        List<UUID> all = new ArrayList<>(fight.foes.keySet());
        all.addAll(fight.allies);
        for (UUID id : all) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
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

    /** A Namekian dome: a half sphere of white with round green windows, a doorway to the south. */
    private static void dome(ServerLevel level, BlockPos base, int radius) {
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
        set(level, base.offset(0, 1, radius), Blocks.AIR.defaultBlockState());
        set(level, base.offset(0, 2, radius), Blocks.AIR.defaultBlockState());
        set(level, base.offset(0, radius, 0), Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void namekVillage(ServerLevel level, BlockPos center) {
        clearRound(level, center, 14, Blocks.GRASS_BLOCK.defaultBlockState());
        dome(level, center.offset(-8, 0, -6), 3);
        dome(level, center.offset(8, 0, -6), 3);
        dome(level, center.offset(0, 0, -10), 4);
        // An Ajisa tree: a tall pale trunk under a round blue-green crown.
        for (int dy = 1; dy <= 7; dy++) {
            set(level, center.offset(6, dy, 7), Blocks.BIRCH_LOG.defaultBlockState());
        }
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (dx * dx + dy * dy + dz * dz <= 5) {
                        set(level, center.offset(6 + dx, 9 + dy, 7 + dz), Blocks.CYAN_WOOL.defaultBlockState());
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
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH));
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

    /** The Cell Games ring: a raised square of white tiles, a post at each corner. */
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
        return built != null ? built : site.around(landing);
    }
}
