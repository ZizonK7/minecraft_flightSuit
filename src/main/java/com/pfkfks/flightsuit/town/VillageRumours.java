package com.pfkfks.flightsuit.town;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.hero.HeroData;
import com.pfkfks.flightsuit.war.FortRecord;
import com.pfkfks.flightsuit.war.Kingdom;
import com.pfkfks.flightsuit.war.WarData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * M18 finding the towns the way a vanilla village is found (the user's call: "봉화는 OK, 바닐라 마을에서 소문이나
 * 표식"): the first time a player stands among a vanilla village's villagers, one of them tells of the nearest
 * fortress and of Hero City (a line in chat with direction and distance, once a day), and every cartographer's
 * first trades include maps to each fortress and to Hero City (a red X, like the explorer maps).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class VillageRumours {
    private static final String HEARD_DAY = "FlightSuitVillageRumourDay";
    private static final int MAP_PRICE = 12;

    private VillageRumours() {
    }

    // ---------------------------------------------------------------- cartographers' maps

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CARTOGRAPHER) {
            return;
        }
        List<net.minecraft.world.entity.npc.VillagerTrades.ItemListing> level1 = event.getTrades().get(1);
        for (Kingdom kingdom : Kingdom.values()) {
            level1.add((trader, random) -> fortMap(trader, kingdom));
        }
        level1.add(VillageRumours::cityMap);
    }

    private static MerchantOffer fortMap(Entity trader, Kingdom kingdom) {
        if (!(trader.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        FortRecord fort = WarData.get(level.getServer()).fort(kingdom);
        if (fort == null) {
            return null;
        }
        ItemStack map = map(level, fort.x, fort.z, "fort_" + kingdom.id());
        map.setHoverName(Component.translatable("item.flightsuit.fort_map", kingdom.displayName()));
        return new MerchantOffer(new ItemStack(Items.EMERALD, MAP_PRICE), new ItemStack(Items.COMPASS), map, 3, 10, 0.2F);
    }

    private static MerchantOffer cityMap(Entity trader, net.minecraft.util.RandomSource random) {
        if (!(trader.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        HeroData heroes = HeroData.get(level.getServer());
        if (!heroes.sited) {
            return null;
        }
        ItemStack map = map(level, heroes.x, heroes.z, "hero_city");
        map.setHoverName(Component.translatable("item.flightsuit.city_map"));
        return new MerchantOffer(new ItemStack(Items.EMERALD, MAP_PRICE + 2), new ItemStack(Items.COMPASS), map, 3, 10, 0.2F);
    }

    /** A map centred on the town with a red X on it, drawn once so it isn't blank in the trade window. */
    private static ItemStack map(ServerLevel level, int x, int z, String mark) {
        ItemStack map = MapItem.create(level, x, z, (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(level, map);
        MapItemSavedData.addTargetDecoration(map, new BlockPos(x, 64, z), mark, MapDecoration.Type.RED_X);
        return map;
    }

    // ---------------------------------------------------------------- the villagers' word

    /** Every ten seconds: a player among vanilla villagers hears of the towns, once a day. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 200 != 17
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long day = level.getDayTime() / 24000L;
        CompoundTag data = player.getPersistentData();
        if (data.contains(HEARD_DAY) && data.getLong(HEARD_DAY) == day) {
            return;
        }
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(12.0D), Villager::isAlive);
        if (villagers.size() < 2) {
            return;
        }
        data.putLong(HEARD_DAY, day);
        Villager teller = villagers.get(0);
        BlockPos at = player.blockPosition();
        WarData war = WarData.get(player.server);
        FortRecord nearest = null;
        double best = Double.MAX_VALUE;
        for (Kingdom kingdom : Kingdom.values()) {
            FortRecord fort = war.fort(kingdom);
            if (fort != null && at.distSqr(new BlockPos(fort.x, at.getY(), fort.z)) < best) {
                best = at.distSqr(new BlockPos(fort.x, at.getY(), fort.z));
                nearest = fort;
            }
        }
        if (nearest != null) {
            player.sendSystemMessage(Component.translatable("rumour.flightsuit.villager", teller.getDisplayName(),
                    Component.translatable("rumour.flightsuit.fort", nearest.kingdom.displayName(), Rumours.whereabouts(at, nearest.x, nearest.z)))
                    .withStyle(ChatFormatting.YELLOW));
        }
        HeroData heroes = HeroData.get(player.server);
        if (heroes.sited) {
            player.sendSystemMessage(Component.translatable("rumour.flightsuit.villager", teller.getDisplayName(),
                    Component.translatable("rumour.flightsuit.city", Rumours.whereabouts(at, heroes.x, heroes.z))).withStyle(ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(Component.translatable("rumour.flightsuit.villager_maps").withStyle(ChatFormatting.GRAY));
    }
}
