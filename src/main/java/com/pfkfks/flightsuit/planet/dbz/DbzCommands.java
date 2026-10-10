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
 * "/planet replay", seen or not), "/flightsuit dbz stop" ends the story fight on the planet you're on,
 * "/flightsuit dbz fx <effect>" shows one of the drawn ki effects in front of you (looks only, nothing hurt).
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
                        .then(Commands.literal("fx").then(Commands.argument("effect", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FX, builder))
                                .executes(ctx -> showFx(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "effect")))))
                        .then(Commands.literal("stop").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            Planet planet = Planet.of(player.level().dimension());
                            if (planet != null && player.level() instanceof ServerLevel level) {
                                DbzSaga.stopFight(level, planet);
                            }
                            return 1;
                        }))));
    }

    private static final java.util.List<String> FX = java.util.List.of("kamehameha", "galick", "special_beam", "death_beam", "candy",
            "unibeam", "charge", "disc", "ki", "burning", "fireball", "blast", "ring", "spirit_bomb", "pillar", "aura", "kaioken", "flare");

    /** One effect, a few blocks in front of the player, for looking at (KiFx). */
    private static int showFx(ServerPlayer player, String name) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(look.x, 0.0D, look.z).normalize();
        net.minecraft.world.phys.Vec3 from = player.getEyePosition().add(flat.scale(2.0D)).add(0.0D, -0.4D, 0.0D);
        net.minecraft.world.phys.Vec3 to = from.add(flat.scale(18.0D));
        net.minecraft.world.phys.Vec3 ahead = player.position().add(flat.scale(6.0D));
        switch (name) {
            case "kamehameha" -> com.pfkfks.flightsuit.fx.KiFx.beam(level, from, to, com.pfkfks.flightsuit.fx.KiFx.KAME, 1.35F,
                    com.pfkfks.flightsuit.fx.KiFx.BEAM_PLAIN);
            case "galick" -> com.pfkfks.flightsuit.fx.KiFx.beam(level, from, to, com.pfkfks.flightsuit.fx.KiFx.GALICK, 1.35F,
                    com.pfkfks.flightsuit.fx.KiFx.BEAM_PLAIN);
            case "special_beam" -> com.pfkfks.flightsuit.fx.KiFx.beam(level, from, to, com.pfkfks.flightsuit.fx.KiFx.SBC, 0.75F,
                    com.pfkfks.flightsuit.fx.KiFx.BEAM_SPIRAL);
            case "death_beam" -> com.pfkfks.flightsuit.fx.KiFx.beam(level, from, to, com.pfkfks.flightsuit.fx.KiFx.DEATH, 0.45F,
                    com.pfkfks.flightsuit.fx.KiFx.BEAM_THIN);
            case "candy" -> com.pfkfks.flightsuit.fx.KiFx.beam(level, from, to, com.pfkfks.flightsuit.fx.KiFx.CANDY, 0.45F,
                    com.pfkfks.flightsuit.fx.KiFx.BEAM_THIN);
            case "unibeam" -> com.pfkfks.flightsuit.fx.KiFx.beam(level, from, to, com.pfkfks.flightsuit.fx.KiFx.UNIBEAM, 1.2F,
                    com.pfkfks.flightsuit.fx.KiFx.BEAM_PLAIN);
            case "charge" -> com.pfkfks.flightsuit.suit.StolenSkill.KAMEHAMEHA.chargeBall(level, player, 40);
            case "disc" -> com.pfkfks.flightsuit.fx.KiFx.shot(level, (byte) com.pfkfks.flightsuit.suit.KiShots.Style.DISC.ordinal(),
                    com.pfkfks.flightsuit.fx.KiFx.BURNING, 1.7F, from, flat, 0.6D, 24.0D);
            case "ki" -> com.pfkfks.flightsuit.fx.KiFx.shot(level, (byte) com.pfkfks.flightsuit.suit.KiShots.Style.KI.ordinal(),
                    com.pfkfks.flightsuit.fx.KiFx.KI, 0.6F, from, flat, 0.8D, 24.0D);
            case "burning" -> com.pfkfks.flightsuit.fx.KiFx.shot(level, (byte) com.pfkfks.flightsuit.suit.KiShots.Style.BURNING.ordinal(),
                    com.pfkfks.flightsuit.fx.KiFx.BURNING, 1.5F, from, flat, 0.6D, 24.0D);
            case "fireball" -> com.pfkfks.flightsuit.fx.KiFx.shot(level, (byte) com.pfkfks.flightsuit.suit.KiShots.Style.FIRE.ordinal(),
                    com.pfkfks.flightsuit.fx.KiFx.FIRE, 0.5F, from, flat, 0.7D, 24.0D);
            case "blast" -> com.pfkfks.flightsuit.fx.KiFx.burst(level, ahead.add(0.0D, 1.5D, 0.0D), 0xFFC870, 3.0F, 24,
                    com.pfkfks.flightsuit.fx.KiFx.BLAST);
            case "ring" -> com.pfkfks.flightsuit.fx.KiFx.burst(level, ahead, 0xFFE0A0, 7.0F, 18, com.pfkfks.flightsuit.fx.KiFx.RING);
            case "spirit_bomb" -> com.pfkfks.flightsuit.fx.KiFx.shot(level, com.pfkfks.flightsuit.fx.KiFx.SHOT_SPIRIT,
                    com.pfkfks.flightsuit.fx.KiFx.SPIRIT, 6.0F, ahead.add(flat.scale(6.0D)).add(0.0D, 22.0D, 0.0D),
                    new net.minecraft.world.phys.Vec3(0.0D, -1.0D, 0.0D), 1.8D, 24.0D);
            case "pillar" -> com.pfkfks.flightsuit.fx.KiFx.pillar(level, null, ahead, com.pfkfks.flightsuit.fx.KiFx.GOLD, 26.0F, 40);
            case "aura" -> com.pfkfks.flightsuit.fx.KiFx.aura(level, player, com.pfkfks.flightsuit.fx.KiFx.GOLD, 200);
            case "kaioken" -> {
                com.pfkfks.flightsuit.fx.KiFx.pillar(level, player, player.position(), com.pfkfks.flightsuit.fx.KiFx.RED, 6.0F, 16);
                com.pfkfks.flightsuit.fx.KiFx.aura(level, player, com.pfkfks.flightsuit.fx.KiFx.RED, 200);
            }
            case "flare" -> com.pfkfks.flightsuit.fx.KiFx.flare(level, player, ahead.add(0.0D, 1.6D, 0.0D), 0.0F);
            default -> {
                player.sendSystemMessage(Component.literal(String.join(", ", FX)).withStyle(ChatFormatting.GRAY));
                return 0;
            }
        }
        return 1;
    }
}
