package com.pfkfks.flightsuit.war;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * "/village recruit|release <raid>": the answer to a surrender (the chat buttons run these; anyone may type
 * them, only the raided village's owner is listened to).
 *
 * Test helpers (cheats / op only), standing in your village:
 * "/flightsuit raid start [kingdom]" - the army arrives now; "/flightsuit raid stop" - calls it off;
 * "/flightsuit raid general <name>" - a hostile general right here.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class WarCommands {
    private WarCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("village")
                .then(Commands.literal("recruit")
                        .then(Commands.argument("raid", IntegerArgumentType.integer(0))
                                .executes(ctx -> RaidManager.recruit(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "raid"),
                                        ctx.getSource().getPlayerOrException()) ? 1 : 0)))
                .then(Commands.literal("release")
                        .then(Commands.argument("raid", IntegerArgumentType.integer(0))
                                .executes(ctx -> RaidManager.release(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "raid"),
                                        ctx.getSource().getPlayerOrException()) ? 1 : 0))));
        event.getDispatcher().register(Commands.literal("flightsuit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("raid")
                        .then(Commands.literal("start")
                                .executes(ctx -> start(ctx, null))
                                .then(Commands.argument("kingdom", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Kingdom.values()).map(Kingdom::id), builder))
                                        .executes(ctx -> start(ctx, Kingdom.byName(StringArgumentType.getString(ctx, "kingdom"))))))
                        .then(Commands.literal("stop").executes(WarCommands::stop))
                        .then(Commands.literal("general")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(General.values()).map(General::id), builder))
                                        .executes(WarCommands::general)))));
    }

    private static VillageHallBlockEntity hallHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VillageHallBlockEntity hall = Villages.containing(player.level(), player.blockPosition());
        if (hall == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_village"));
        }
        return hall;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, Kingdom kingdom) throws CommandSyntaxException {
        VillageHallBlockEntity hall = hallHere(ctx);
        if (hall == null || !(hall.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        WarData data = WarData.get(level.getServer());
        WarData.VillageRecord village = data.village(level.dimension(), hall.getBlockPos());
        village.owner = hall.getOwner();
        village.population = hall.getPopulation();
        if (data.raidOf(village.key()) != null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.raid_running"));
            return 0;
        }
        Kingdom pick = kingdom != null ? kingdom : Kingdom.byId(level.random.nextInt(Kingdom.values().length));
        RaidManager.startRaid(level.getServer(), data, village, pick);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.raid_started", pick.displayName()), false);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        VillageHallBlockEntity hall = hallHere(ctx);
        if (hall == null || !(hall.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        WarData data = WarData.get(level.getServer());
        RaidState raid = data.raidOf(WarData.keyOf(level.dimension(), hall.getBlockPos()));
        if (raid == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_raid"));
            return 0;
        }
        RaidManager.stop(level.getServer(), raid);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.raid_stopped"), false);
        return 1;
    }

    private static int general(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        General general = General.byName(StringArgumentType.getString(ctx, "name"));
        if (general == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_general"));
            return 0;
        }
        VillageHallBlockEntity hall = Villages.containing(player.level(), player.blockPosition());
        GeneralEntity entity = GeneralEntity.create(player.serverLevel(), general, -1, hall == null ? null : hall.getBlockPos());
        entity.moveTo(player.getX() + player.getLookAngle().x * 6.0D, player.getY(), player.getZ() + player.getLookAngle().z * 6.0D,
                player.getYRot() + 180.0F, 0.0F);
        player.serverLevel().addFreshEntity(entity);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.general_spawned", general.displayName()), false);
        return 1;
    }
}
