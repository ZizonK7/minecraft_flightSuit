package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.Planet;
import com.pfkfks.flightsuit.planet.PlanetData;
import com.pfkfks.flightsuit.planet.PlanetStory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dragon Ball Earth's open world (DESIGN.md 4-16, M15): builds its sights as people come near, keeps Bulma and
 * Goku at home, lets Saibamen roam at night, and runs the fights at the crater - Raditz (with Goku beside you),
 * then a day later the Saiyans in waves: Saibamen, Nappa, and Vegeta (Goku arrives for that one).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DbzEarth {
    private enum Kind { RADITZ, SAIYANS }

    /** The fight at the crater (one at a time; it starts over if everyone leaves). */
    private static final class Fight {
        final Kind kind;
        int wave;
        final long startedAt;
        /** Everyone this fight brought in - counted and cleared by id, wherever they've wandered. */
        final java.util.Map<java.util.UUID, DbzCharacter> spawned = new java.util.HashMap<>();

        Fight(Kind kind, long startedAt) {
            this.kind = kind;
            this.startedAt = startedAt;
        }
    }

    private static @Nullable Fight fight;
    private static final String WILD = "flightsuit_wild";

    private DbzEarth() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        fight = null;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != Planet.DBZ_EARTH.dimension() || level.getGameTime() % 20 != 7) {
            return;
        }
        PlanetData data = PlanetData.get(level.getServer());
        BlockPos site = data.site(Planet.DBZ_EARTH);
        if (site == null) {
            return;
        }
        CompoundTag world = data.world(Planet.DBZ_EARTH);
        for (DbzLandmarks mark : DbzLandmarks.values()) {
            BlockPos column = mark.around(site);
            if (!world.contains(mark.id()) && anyoneWithin(level, column, 96.0D) && level.isLoaded(column)) {
                BlockPos center = mark.build(level, site);
                world.putLong(mark.id(), center.asLong());
                data.setDirty();
            }
        }
        // After the sights: a ball set down this tick isn't then built over.
        DragonBalls.tick(level, data, site);
        keep(level, world, DbzLandmarks.CAPSULE_CORP, DbzCharacter.BULMA);
        keep(level, world, DbzLandmarks.KAME_HOUSE, DbzCharacter.GOKU);
        tickFight(level, world);
        if (level.getGameTime() % 300 == 7) {
            roam(level, world);
        }
    }

    public static @Nullable BlockPos center(CompoundTag world, DbzLandmarks mark) {
        return world.contains(mark.id()) ? BlockPos.of(world.getLong(mark.id())) : null;
    }

    private static boolean anyoneWithin(ServerLevel level, BlockPos pos, double range) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - pos.getX();
            double dz = player.getZ() - pos.getZ();
            if (dx * dx + dz * dz < range * range) {
                return true;
            }
        }
        return false;
    }

    /** One of {@code who} at home while someone is close enough to see. */
    private static void keep(ServerLevel level, CompoundTag world, DbzLandmarks mark, DbzCharacter who) {
        BlockPos center = center(world, mark);
        if (center == null) {
            return;
        }
        BlockPos post = mark.post(center);
        if (!anyoneWithin(level, post, 48.0D) || !level.isPositionEntityTicking(post)) {
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

    // ---------------------------------------------------------------- the crater

    private static void tickFight(ServerLevel level, CompoundTag world) {
        BlockPos crater = center(world, DbzLandmarks.CRATER);
        if (crater == null) {
            return;
        }
        BlockPos post = DbzLandmarks.CRATER.post(crater);
        long day = level.getDayTime() / 24000L;
        if (fight == null) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(crater.getX(), player.getY(), crater.getZ()) > 40.0D * 40.0D || !level.isPositionEntityTicking(post)) {
                    continue;
                }
                PlanetStory.DbzStage stage = PlanetStory.dbzStage(player);
                if (stage == PlanetStory.DbzStage.MET_GOKU) {
                    fight = new Fight(Kind.RADITZ, level.getGameTime());
                } else if (stage == PlanetStory.DbzStage.RADITZ_BEATEN
                        && day >= PlanetData.get(level.getServer()).traveller(player.getUUID()).nextBeatDay) {
                    fight = new Fight(Kind.SAIYANS, level.getGameTime());
                }
                if (fight != null) {
                    break;
                }
            }
            if (fight == null) {
                return;
            }
        }
        Fight current = fight;
        if (!anyoneWithin(level, crater, 96.0D) || level.getGameTime() - current.startedAt > 20L * 60 * 20) {
            end(level, current);
            return;
        }
        if (!level.isPositionEntityTicking(post)) {
            return;
        }
        int raditz = alive(level, current, DbzCharacter.RADITZ);
        int nappa = alive(level, current, DbzCharacter.NAPPA);
        int vegeta = alive(level, current, DbzCharacter.VEGETA);
        int saibamen = alive(level, current, DbzCharacter.SAIBAMAN);
        switch (current.kind) {
            case RADITZ -> {
                if (current.wave == 0) {
                    spawn(level, current, DbzCharacter.RADITZ, post, 0, 0);
                    spawn(level, current, DbzCharacter.GOKU, post, -6, -6);
                    announce(level, crater, DbzCharacter.RADITZ.line("arrive"));
                    current.wave = 1;
                } else if (raditz == 0) {
                    end(level, current);
                }
            }
            case SAIYANS -> {
                if (current.wave == 0) {
                    RandomSource random = level.random;
                    for (int i = 0; i < 6; i++) {
                        double angle = i * Math.PI / 3.0D;
                        spawn(level, current, DbzCharacter.SAIBAMAN, post, Mth.floor(Math.cos(angle) * 6) + random.nextInt(3) - 1,
                                Mth.floor(Math.sin(angle) * 6));
                    }
                    announce(level, crater, DbzCharacter.NAPPA.line("arrive"));
                    announce(level, crater, DbzCharacter.VEGETA.line("arrive"));
                    current.wave = 1;
                } else if (current.wave == 1 && saibamen <= 1) {
                    spawn(level, current, DbzCharacter.NAPPA, post, 0, 0);
                    announce(level, crater, DbzCharacter.NAPPA.line("go"));
                    current.wave = 2;
                } else if (current.wave == 2 && nappa == 0) {
                    spawn(level, current, DbzCharacter.VEGETA, post, 0, 0);
                    spawn(level, current, DbzCharacter.GOKU, post, -6, -6);
                    announce(level, crater, DbzCharacter.GOKU.line("arrive"));
                    announce(level, crater, DbzCharacter.VEGETA.line("go"));
                    current.wave = 3;
                } else if (current.wave == 3 && vegeta == 0) {
                    end(level, current);
                }
            }
        }
    }

    private static void end(ServerLevel level, Fight over) {
        for (java.util.UUID id : over.spawned.keySet()) {
            net.minecraft.world.entity.Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        fight = null;
    }

    /** How many of this fight's {@code who} are still up (a boss that withdrew, or was cleared, is gone). */
    private static int alive(ServerLevel level, Fight current, DbzCharacter who) {
        int count = 0;
        for (java.util.Map.Entry<java.util.UUID, DbzCharacter> entry : current.spawned.entrySet()) {
            if (entry.getValue() == who && level.getEntity(entry.getKey()) instanceof DbzFighterEntity fighter && fighter.isAlive()) {
                count++;
            }
        }
        return count;
    }

    private static void spawn(ServerLevel level, Fight current, DbzCharacter who, BlockPos at, int dx, int dz) {
        int x = at.getX() + dx;
        int z = at.getZ() + dz;
        int y = Math.max(at.getY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        DbzFighterEntity fighter = DbzFighterEntity.create(level, who, new BlockPos(x, y, z), true);
        // Tethered to the crater, so nobody wanders off and leaves the fight hanging.
        fighter.restrictTo(at, 40);
        fighter.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        if (level.addFreshEntity(fighter)) {
            current.spawned.put(fighter.getUUID(), who);
        }
    }

    private static void announce(ServerLevel level, BlockPos at, Component line) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.getX(), player.getY(), at.getZ()) < 128.0D * 128.0D) {
                player.sendSystemMessage(line);
            }
        }
    }

    // ---------------------------------------------------------------- the wilds

    /** At night Saibamen roam the wastes (not near Capsule Corp or Kame House). */
    private static void roam(ServerLevel level, CompoundTag world) {
        long tod = level.getDayTime() % 24000L;
        if (tod < 13000L || tod > 23000L) {
            // Daybreak: the night's Saibamen are gone.
            List<net.minecraft.world.entity.Entity> wild = new java.util.ArrayList<>();
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof DbzFighterEntity && entity.getTags().contains(WILD)) {
                    wild.add(entity);
                }
            }
            wild.forEach(net.minecraft.world.entity.Entity::discard);
            return;
        }
        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            BlockPos crater = center(world, DbzLandmarks.CRATER);
            if (player.isSpectator() || nearHome(world, player) || fight != null
                    || crater != null && player.distanceToSqr(crater.getX(), player.getY(), crater.getZ()) < 96.0D * 96.0D) {
                continue;
            }
            int around = level.getEntitiesOfClass(DbzFighterEntity.class, player.getBoundingBox().inflate(48.0D),
                    fighter -> fighter.getCharacter() == DbzCharacter.SAIBAMAN).size();
            if (around >= 3) {
                continue;
            }
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = 24.0D + random.nextDouble() * 16.0D;
                int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
                int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
                BlockPos column = new BlockPos(x, player.getBlockY(), z);
                if (!level.isPositionEntityTicking(column)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                DbzFighterEntity saibaman = DbzFighterEntity.create(level, DbzCharacter.SAIBAMAN, new BlockPos(x, y, z), true);
                saibaman.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                saibaman.addTag(WILD);
                level.addFreshEntity(saibaman);
            }
        }
    }

    private static boolean nearHome(CompoundTag world, ServerPlayer player) {
        for (DbzLandmarks mark : new DbzLandmarks[]{DbzLandmarks.CAPSULE_CORP, DbzLandmarks.KAME_HOUSE}) {
            BlockPos center = center(world, mark);
            if (center != null && player.distanceToSqr(center.getX(), player.getY(), center.getZ()) < 64.0D * 64.0D) {
                return true;
            }
        }
        return false;
    }
}
