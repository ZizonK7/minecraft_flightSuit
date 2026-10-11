package com.pfkfks.flightsuit.town;

import com.pfkfks.flightsuit.hero.HeroData;
import com.pfkfks.flightsuit.thief.ThiefData;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import com.pfkfks.flightsuit.war.FortRecord;
import com.pfkfks.flightsuit.war.Kingdom;
import com.pfkfks.flightsuit.war.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * M18 what the innkeepers (and the vanilla villagers) have heard (DESIGN.md 5차 피드백 "갈 이유 - 여관 소문"): where the
 * other fortresses and Hero City lie, whether one has fallen, when the next raid on the player's village is due,
 * and whether Batman's crew is expected. One line a time, picked at random from what applies.
 */
public final class Rumours {
    private Rumours() {
    }

    /** Compass sector and rough distance from {@code from} to {@code x, z}, as the chat lines show them. */
    public static Component whereabouts(BlockPos from, int x, int z) {
        double dx = x - from.getX();
        double dz = z - from.getZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8);
        return Component.translatable("edith.flightsuit.where", Math.max(100, Math.round(distance / 100.0F) * 100),
                Component.translatable("edith.flightsuit.dir." + sector));
    }

    /**
     * One rumour for {@code player}, told at {@code at}. {@code town}: the fortress (its kingdom's ordinal) or
     * TownsfolkEntity.HERO_CITY the teller lives in, or -1 for a stranger (a vanilla villager) who tells of them all.
     */
    public static Component tell(ServerPlayer player, BlockPos at, int town) {
        ServerLevel level = player.serverLevel();
        WarData war = WarData.get(player.server);
        HeroData heroes = HeroData.get(player.server);
        long day = level.getDayTime() / 24000L;
        List<Component> lines = new ArrayList<>();
        // The other towns.
        for (Kingdom kingdom : Kingdom.values()) {
            FortRecord fort = war.fort(kingdom);
            if (fort == null || kingdom.ordinal() == town) {
                continue;
            }
            if (fort.built && fort.isFallen(day)) {
                lines.add(Component.translatable("rumour.flightsuit.fort_fallen", kingdom.displayName(), whereabouts(at, fort.x, fort.z)));
            } else {
                lines.add(Component.translatable("rumour.flightsuit.fort", kingdom.displayName(), whereabouts(at, fort.x, fort.z)));
            }
        }
        if (town != TownsfolkEntity.HERO_CITY && heroes.sited) {
            lines.add(Component.translatable(heroes.built && heroes.isFallen(day) ? "rumour.flightsuit.city_fallen" : "rumour.flightsuit.city",
                    whereabouts(at, heroes.x, heroes.z)));
        }
        // The player's own village: the next raid, the thieves.
        VillageHallBlockEntity home = nearestVillage(level, player);
        if (home != null) {
            WarData.VillageRecord record = war.village(Level.OVERWORLD, home.getBlockPos());
            if (record != null && record.nextRaidDay > day) {
                lines.add(Component.translatable("rumour.flightsuit.raid", record.nextRaidDay - day));
            } else if (record != null && record.nextRaidDay == day) {
                lines.add(Component.translatable("rumour.flightsuit.raid_tonight"));
            }
            ThiefData thieves = ThiefData.get(player.server);
            if (thieves.nextVisitDay >= 0 && thieves.nextVisitDay - day >= 0 && thieves.nextVisitDay - day <= 2) {
                lines.add(Component.translatable("rumour.flightsuit.thieves", thieves.nextVisitDay - day));
            }
        }
        if (lines.isEmpty()) {
            lines.add(Component.translatable("rumour.flightsuit.nothing"));
        }
        return lines.get(level.random.nextInt(lines.size())).copy().withStyle(ChatFormatting.YELLOW);
    }

    /** The village the player owns nearest to them (its hall must be loaded), or null. */
    private static VillageHallBlockEntity nearestVillage(ServerLevel level, ServerPlayer player) {
        VillageHallBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (WarData.VillageRecord record : WarData.get(player.server).villages().values()) {
            VillageHallBlockEntity hall = Villages.hallAt(level, record.hall);
            if (hall == null) {
                continue;
            }
            double distance = hall.getBlockPos().distSqr(player.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = hall;
            }
        }
        return best;
    }
}
