package com.pfkfks.flightsuit.thief;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.Locale;

/**
 * Test helpers for Batman's crew (cheats / op only), standing in your village:
 * "/flightsuit thief now" - they come tonight (the clock jumps to nightfall);
 * "/flightsuit thief spawn <batman|catwoman|robin>" - one hidden thief right here;
 * "/flightsuit thief when" - the day of the next visit.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class ThiefCommands {
    private ThiefCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("flightsuit")
                // Every registration of the root carries the op check: Brigadier keeps whichever came first.
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("thief")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("now").executes(ThiefCommands::now))
                        .then(Commands.literal("when").executes(ctx -> {
                            long day = ThiefManager.nextVisitDay(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.thief_when", day), false);
                            return 1;
                        }))
                        .then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(ThiefType.values()).map(ThiefType::id), builder))
                                .executes(ThiefCommands::spawn)))));
    }

    private static VillageHallBlockEntity hallHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VillageHallBlockEntity hall = Villages.containing(player.level(), player.blockPosition());
        if (hall == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_village"));
        }
        return hall;
    }

    private static int now(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageHallBlockEntity hall = hallHere(ctx);
        if (hall == null) {
            return 0;
        }
        if (!ThiefManager.forceTonight(ctx.getSource().getPlayerOrException().serverLevel(), hall)) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.thief_no_village"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.thief_now"), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageHallBlockEntity hall = hallHere(ctx);
        if (hall == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "type").toLowerCase(Locale.ROOT);
        ThiefType type = Arrays.stream(ThiefType.values()).filter(t -> t.id().equals(name)).findFirst().orElse(null);
        if (type == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.thief_no_type"));
            return 0;
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ThiefEntity thief = ThiefManager.spawnHere(player.serverLevel(), hall, type, player.blockPosition().relative(player.getDirection(), 3));
        if (thief == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.thief_spawned", type.displayName()), false);
        return 1;
    }
}
