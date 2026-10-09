package com.pfkfks.flightsuit.hero;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Hero City's chat buttons run these (anyone may type them; only the player the request is for is listened to):
 * "/village hero accept|decline|send <id>", "/village ironman <repair|upgrade|reactor|gift>".
 *
 * Test helpers (cheats / op only): "/flightsuit hero tp" - to the city's gate; "/flightsuit hero trust <value>";
 * "/flightsuit hero request <defend|reinforce>" - a request now, due today.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class HeroCommands {
    private HeroCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("village")
                .then(Commands.literal("hero")
                        .then(Commands.literal("accept").then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(ctx -> HeroCity.accept(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                        .then(Commands.literal("decline").then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(ctx -> HeroCity.decline(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                        .then(Commands.literal("send").then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(ctx -> HeroCity.send(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                        .then(Commands.literal("timestone").executes(ctx ->
                                com.pfkfks.flightsuit.thanos.ThanosSaga.timeStone(ctx.getSource().getPlayerOrException()) ? 1 : 0)))
                .then(Commands.literal("ironman").then(Commands.argument("service", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"repair", "upgrade", "reactor", "gift"}, builder))
                        .executes(ctx -> HeroCity.ironManService(ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "service")) ? 1 : 0))));
        event.getDispatcher().register(Commands.literal("flightsuit")
                .then(Commands.literal("hero")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("tp").executes(HeroCommands::tp))
                        .then(Commands.literal("trust").then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                .executes(ctx -> trust(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("request").then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[]{"defend", "reinforce"}, builder))
                                .executes(HeroCommands::request)))));
    }

    private static int tp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getServer().overworld();
        BlockPos site = HeroCity.site(level);
        int z = site.getZ() + HeroCityBuilder.EDGE + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getX(), z);
        player.teleportTo(level, site.getX() + 0.5D, y, z + 0.5D, 180.0F, 0.0F);
        return 1;
    }

    private static int trust(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        HeroData data = HeroData.get(ctx.getSource().getServer());
        data.addTrust(player.getUUID(), value - data.trust(player.getUUID()));
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.trust_set", Component.translatable("hero.flightsuit.city"), value), false);
        return 1;
    }

    private static int request(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        HeroData.Request.Type type;
        try {
            type = HeroData.Request.Type.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_request_type"));
            return 0;
        }
        return HeroCity.forceOffer(player, type) ? 1 : 0;
    }
}
