package com.pfkfks.flightsuit.village;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Test helper (cheats / op only): "/flightsuit village wanderer" brings a wanderer to the village you are
 * standing in right now, instead of waiting for the next morning (a free bed is not required).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class VillageCommands {
    private VillageCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("flightsuit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("village")
                        .then(Commands.literal("wanderer").executes(VillageCommands::wanderer))));
    }

    private static int wanderer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VillageHallBlockEntity hall = Villages.containing(player.level(), player.blockPosition());
        if (hall == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_village"));
            return 0;
        }
        ResidentEntity wanderer = hall.spawnWanderer();
        if (wanderer == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.wanderer", wanderer.getName()), false);
        return 1;
    }
}
