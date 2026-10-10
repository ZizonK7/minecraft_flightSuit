package com.pfkfks.flightsuit.planet.dbz;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.cutscene.CutsceneRunner;
import com.pfkfks.flightsuit.cutscene.Cutscenes;
import com.pfkfks.flightsuit.planet.Planet;
import com.pfkfks.flightsuit.planet.PlanetData;
import com.pfkfks.flightsuit.planet.PlanetStory;
import com.pfkfks.flightsuit.planet.PlanetStory.DbzStage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Locale;

/**
 * Test commands for the Dragon Ball story (M17, cheats): "/flightsuit dbz stage <stage>" puts you at a point in the
 * story (and lets "tomorrow" come at once), "/flightsuit dbz cutscene <id>" plays a scene for you on the spot (like
 * "/planet replay", seen or not), "/flightsuit dbz stop" ends the story fight on the planet you're on.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DbzCommands {
    private DbzCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("flightsuit")
                // Every registration of the root carries the op check: Brigadier keeps whichever came first.
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dbz")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("stage").then(Commands.argument("stage", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(DbzStage.values()).map(s -> s.name().toLowerCase(Locale.ROOT)), builder))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String name = StringArgumentType.getString(ctx, "stage").toUpperCase(Locale.ROOT);
                                    DbzStage stage;
                                    try {
                                        stage = DbzStage.valueOf(name);
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendFailure(Component.literal(name));
                                        return 0;
                                    }
                                    PlanetStory.setStage(player, stage);
                                    PlanetData data = PlanetData.get(player.server);
                                    data.traveller(player.getUUID()).nextBeatDay = 0L;
                                    data.setDirty();
                                    PlanetStory.objective(player);
                                    return 1;
                                })))
                        .then(Commands.literal("cutscene").then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Cutscenes.ids(), builder))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    if (!CutsceneRunner.replay(player, id)) {
                                        player.sendSystemMessage(Component.translatable("story.flightsuit.replay_none", id).withStyle(ChatFormatting.GRAY));
                                        return 0;
                                    }
                                    return 1;
                                })))
                        .then(Commands.literal("stop").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Planet planet = Planet.of(player.level().dimension());
                            if (planet != null && player.level() instanceof ServerLevel level) {
                                DbzSaga.stopFight(level, planet);
                            }
                            return 1;
                        }))));
    }
}
