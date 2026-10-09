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
 * Saiyan saga: land → Bulma at Capsule Corp → Goku at Kame House → Raditz at the crater (Goku fights beside
 * you; the scouter is yours) → a day later the Saiyans land there: Saibamen, Nappa, Vegeta → chapter clear.
 * "/planet" shows what's next and which way it is.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class PlanetStory {
    public enum DbzStage { ARRIVED, MET_BULMA, MET_GOKU, RADITZ_BEATEN, SAIYANS_BEATEN }

    private PlanetStory() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("planet").executes(ctx -> {
            objective(ctx.getSource().getPlayerOrException());
            return 1;
        }));
    }

    public static @Nullable DbzStage dbzStage(ServerPlayer player) {
        Integer stage = PlanetData.get(player.server).traveller(player.getUUID()).stage.get(Planet.DBZ_EARTH);
        return stage == null ? null : DbzStage.values()[Math.max(0, Math.min(DbzStage.values().length - 1, stage))];
    }

    private static void setStage(ServerPlayer player, DbzStage stage) {
        PlanetData data = PlanetData.get(player.server);
        data.traveller(player.getUUID()).stage.put(Planet.DBZ_EARTH, stage.ordinal());
        data.setDirty();
    }

    public static void onLanded(ServerPlayer player, Planet planet) {
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
        DbzStage stage = dbzStage(player);
        if (stage == null) {
            player.sendSystemMessage(Component.translatable("story.flightsuit.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        DbzLandmarks target = switch (stage) {
            case ARRIVED -> DbzLandmarks.CAPSULE_CORP;
            case MET_BULMA -> DbzLandmarks.KAME_HOUSE;
            case MET_GOKU, RADITZ_BEATEN -> DbzLandmarks.CRATER;
            case SAIYANS_BEATEN -> null;
        };
        Component where = Component.literal("");
        PlanetData data = PlanetData.get(player.server);
        BlockPos site = data.site(Planet.DBZ_EARTH);
        if (target != null && site != null) {
            if (player.level().dimension() == Planet.DBZ_EARTH.dimension()) {
                BlockPos built = DbzEarth.center(data.world(Planet.DBZ_EARTH), target);
                BlockPos goal = built != null ? built : target.around(site);
                double dx = goal.getX() - player.getX();
                double dz = goal.getZ() - player.getZ();
                int distance = (int) Math.sqrt(dx * dx + dz * dz);
                int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8);
                where = Component.translatable("edith.flightsuit.where", distance, Component.translatable("edith.flightsuit.dir." + sector));
            } else {
                where = Planet.DBZ_EARTH.displayName();
            }
        }
        long beat = data.traveller(player.getUUID()).nextBeatDay;
        player.sendSystemMessage(Component.translatable("story.flightsuit.dbz_earth.goal." + stage.name().toLowerCase(java.util.Locale.ROOT), where, beat)
                .withStyle(ChatFormatting.AQUA));
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
                } else {
                    player.sendSystemMessage(who.line(stage.name().toLowerCase(java.util.Locale.ROOT)));
                    if (!player.getInventory().contains(new ItemStack(ModItems.DRAGON_RADAR.get()))) {
                        give(player, new ItemStack(ModItems.DRAGON_RADAR.get()));
                        player.sendSystemMessage(who.line("radar"));
                    }
                }
            }
            case GOKU -> {
                if (stage == DbzStage.MET_BULMA) {
                    player.sendSystemMessage(who.line("raditz", player.getName()));
                    setStage(player, DbzStage.MET_GOKU);
                    objective(player);
                } else {
                    player.sendSystemMessage(who.line(stage.name().toLowerCase(java.util.Locale.ROOT)));
                }
            }
            default -> {
            }
        }
    }

    /** A Saiyan is beaten: everyone fighting at the crater at that point in the story moves on. */
    public static void onBossBeaten(ServerLevel level, DbzFighterEntity boss, @Nullable Entity killer) {
        DbzCharacter who = boss.getCharacter();
        long day = level.getDayTime() / 24000L;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(boss) > 96.0D * 96.0D) {
                continue;
            }
            DbzStage stage = dbzStage(player);
            if (who == DbzCharacter.RADITZ && stage == DbzStage.MET_GOKU) {
                setStage(player, DbzStage.RADITZ_BEATEN);
                PlanetData data = PlanetData.get(level.getServer());
                data.traveller(player.getUUID()).nextBeatDay = day + 1;
                data.setDirty();
                give(player, new ItemStack(ModItems.SCOUTER.get()));
                player.sendSystemMessage(Component.translatable("story.flightsuit.dbz_earth.raditz_done").withStyle(ChatFormatting.GOLD));
                objective(player);
            } else if (who == DbzCharacter.VEGETA && stage == DbzStage.RADITZ_BEATEN) {
                setStage(player, DbzStage.SAIYANS_BEATEN);
                PlanetData data = PlanetData.get(level.getServer());
                data.traveller(player.getUUID()).cleared.merge(Planet.DBZ_EARTH, 1, Integer::sum);
                data.setDirty();
                give(player, new ItemStack(ModItems.SENZU_BEAN.get(), 3));
                give(player, new ItemStack(Items.DIAMOND, 8));
                give(player, new ItemStack(ModItems.ARC_REACTOR.get(), 2));
                player.sendSystemMessage(Component.translatable("story.flightsuit.dbz_earth.chapter_clear").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                objective(player);
            }
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
