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
                        .then(Commands.literal("wanderer").executes(VillageCommands::wanderer))
                        .then(Commands.literal("birth").executes(VillageCommands::birth))
                        .then(Commands.literal("grow").executes(VillageCommands::grow))));
    }

    /** A baby right now, whatever the beds and moods say (the two first residents are the parents). */
    private static int birth(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VillageHallBlockEntity hall = Villages.containing(player.level(), player.blockPosition());
        if (hall == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_village"));
            return 0;
        }
        java.util.List<ResidentEntity> adults = hall.residents().stream().filter(r -> !r.isBaby()).toList();
        Component first = adults.size() >= 2 ? adults.get(0).getName() : Component.literal("-");
        Component second = adults.size() >= 2 ? adults.get(1).getName() : Component.literal("-");
        String parents = adults.size() >= 2 ? first.getString() + " · " + second.getString() : "";
        ResidentEntity child = ResidentEntity.spawnChild(player.serverLevel(), hall, player.position(), parents);
        hall.addNews(Component.translatable("news.flightsuit.born", child.getName(), first, second));
        hall.refreshStats();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.child", child.getName()), false);
        return 1;
    }

    /** Every child in this village grows up now (with whatever school they've had). */
    private static int grow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VillageHallBlockEntity hall = Villages.containing(player.level(), player.blockPosition());
        if (hall == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_village"));
            return 0;
        }
        int count = 0;
        for (ResidentEntity resident : hall.residents()) {
            if (resident.isBaby()) {
                resident.growUpNow();
                count++;
            }
        }
        int grown = count;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.grown", grown), false);
        return count;
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
