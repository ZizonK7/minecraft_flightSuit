package com.pfkfks.flightsuit.town;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.hero.HeroCityBuilder;
import com.pfkfks.flightsuit.hero.HeroData;
import com.pfkfks.flightsuit.war.FortRecord;
import com.pfkfks.flightsuit.war.FortressBuilder;
import com.pfkfks.flightsuit.war.WarData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the fortress towns and Hero City peopled while a player is near (after the M16 test: "make them real
 * towns"). The townsfolk aren't saved - like the garrisons, everyone in the town's roster (TownPlan) is put
 * back where the hour has them each visit, and they're gone again once nobody's around.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class TownLife {
    private static final double FORT_NEAR = 144.0D;
    private static final double CITY_NEAR = 160.0D;

    private TownLife() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % 100L != 37L) {
            return;
        }
        WarData war = WarData.get(level.getServer());
        for (FortRecord fort : war.forts().values()) {
            if (fort.built && anyoneNear(level, fort.center(), FORT_NEAR)) {
                BlockPos center = fort.center();
                keep(level, fort.kingdom.ordinal(), TownPlan.fortress(center),
                        new AABB(center).inflate(FortressBuilder.CLEAR + 16, 24.0D, FortressBuilder.CLEAR + 16));
            }
        }
        HeroData heroes = HeroData.get(level.getServer());
        if (heroes.built && anyoneNear(level, heroes.center(), CITY_NEAR)) {
            BlockPos center = heroes.center();
            keep(level, TownsfolkEntity.HERO_CITY, TownPlan.city(center),
                    new AABB(center).inflate(HeroCityBuilder.EDGE + 12, 64.0D, HeroCityBuilder.EDGE + 12));
        }
    }

    private static boolean anyoneNear(ServerLevel level, BlockPos center, double reach) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - center.getX();
            double dz = player.getZ() - center.getZ();
            if (dx * dx + dz * dz < reach * reach && !player.isSpectator()) {
                return true;
            }
        }
        return false;
    }

    /** Everyone on the roster there once (extras sent off), the missing ones back where the hour has them. */
    private static void keep(ServerLevel level, int town, TownPlan plan, AABB area) {
        BlockPos center = BlockPos.containing(area.getCenter());
        if (!level.isPositionEntityTicking(center)) {
            return;
        }
        List<TownRole> roster = plan.roster();
        boolean[] present = new boolean[roster.size()];
        List<TownsfolkEntity> extras = new ArrayList<>();
        for (TownsfolkEntity person : level.getEntitiesOfClass(TownsfolkEntity.class, area, person -> person.town() == town)) {
            int index = person.index();
            if (index >= 0 && index < present.length && !present[index] && person.getRole() == roster.get(index)) {
                present[index] = true;
            } else {
                extras.add(person);
            }
        }
        extras.forEach(TownsfolkEntity::discard);
        for (int i = 0; i < roster.size(); i++) {
            if (present[i]) {
                continue;
            }
            TownsfolkEntity person = TownsfolkEntity.create(level, town, plan, roster.get(i), i);
            BlockPos at = person.startSpot();
            if (at == null || !level.isLoaded(at)) {
                continue;
            }
            person.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(person);
        }
    }
}
