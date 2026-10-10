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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Dragon Ball Earth's open world (DESIGN.md 4-16, M15): builds its sights as people come near, keeps Bulma and
 * Goku at home and lets Saibamen roam at night. The fights at the crater (chapter 1) are DbzSaga's scenes since M17.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DbzEarth {
    private static final String WILD = "flightsuit_wild";

    private DbzEarth() {
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
        // Goku's at home - except while he's gone (from Raditz until he comes back for Vegeta).
        if (!gokuAway(level)) {
            keep(level, world, DbzLandmarks.KAME_HOUSE, DbzCharacter.GOKU);
        }
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

    /** Goku died with Raditz: from then until Vegeta's fight he's not at home for anyone near. */
    private static boolean gokuAway(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            PlanetStory.DbzStage stage = PlanetStory.dbzStage(player);
            if (stage == PlanetStory.DbzStage.RADITZ_BEATEN || stage == PlanetStory.DbzStage.NAPPA_BEATEN) {
                return true;
            }
        }
        return false;
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
            if (player.isSpectator() || nearHome(world, player) || DbzSaga.isFighting(Planet.DBZ_EARTH)
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
