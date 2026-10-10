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
                .then(Commands.literal("list").executes(ctx -> {
                    Diplomacy.list(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("accept").then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(ctx -> Diplomacy.accept(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                .then(Commands.literal("decline").then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(ctx -> Diplomacy.decline(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                .then(Commands.literal("send").then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .executes(ctx -> Diplomacy.send(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id")) ? 1 : 0)))
                .then(Commands.literal("muster").then(kingdomArgument()
                        .executes(ctx -> withKingdom(ctx, kingdom -> Diplomacy.muster(ctx.getSource().getPlayerOrException(), kingdom)))))
                .then(Commands.literal("lead").then(kingdomArgument()
                        .executes(ctx -> withKingdom(ctx, kingdom -> Diplomacy.lead(ctx.getSource().getPlayerOrException(), kingdom)))))
                .then(Commands.literal("war").then(kingdomArgument()
                        .executes(ctx -> withKingdom(ctx, kingdom -> Diplomacy.war(ctx.getSource().getPlayerOrException(), kingdom)))))
                .then(Commands.literal("army")
                        .then(Commands.literal("follow").executes(ctx -> Army.follow(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("home").executes(ctx -> Army.home(ctx.getSource().getPlayerOrException()))))
                .then(Commands.literal("fort")
                        .then(Commands.literal("recruit").then(kingdomArgument().executes(ctx -> withKingdom(ctx, kingdom ->
                                FortressManager.decide(ctx.getSource().getServer(), kingdom, ctx.getSource().getPlayerOrException(), true)))))
                        .then(Commands.literal("release").then(kingdomArgument().executes(ctx -> withKingdom(ctx, kingdom ->
                                FortressManager.decide(ctx.getSource().getServer(), kingdom, ctx.getSource().getPlayerOrException(), false))))))
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
                                        .executes(WarCommands::general))))
                .then(Commands.literal("fort")
                        .then(Commands.literal("tp").then(kingdomArgument().executes(ctx -> withKingdom(ctx, kingdom -> tpFort(ctx, kingdom)))))
                        .then(Commands.literal("rebuild").then(kingdomArgument().executes(ctx -> withKingdom(ctx, kingdom -> {
                            boolean done = FortressManager.rebuild(ctx.getSource().getServer().overworld(), kingdom);
                            if (done) {
                                ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.rebuild", kingdom.displayName()), false);
                            }
                            return done;
                        }))))
                        .then(Commands.literal("trust").then(kingdomArgument().then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                .executes(ctx -> withKingdom(ctx, kingdom -> setTrust(ctx, kingdom, IntegerArgumentType.getInteger(ctx, "value")))))))
                        .then(Commands.literal("done").then(kingdomArgument().then(Commands.argument("count", IntegerArgumentType.integer(0, 99))
                                .executes(ctx -> withKingdom(ctx, kingdom -> setDone(ctx, kingdom, IntegerArgumentType.getInteger(ctx, "count")))))))
                        .then(Commands.literal("request").then(kingdomArgument().then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Request.Type.values()).map(type -> type.name().toLowerCase(java.util.Locale.ROOT)), builder))
                                .executes(ctx -> withKingdom(ctx, kingdom -> forceRequest(ctx, kingdom, StringArgumentType.getString(ctx, "type")))))))));
    }

    private interface KingdomAction {
        boolean run(Kingdom kingdom) throws CommandSyntaxException;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> kingdomArgument() {
        return Commands.argument("kingdom", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Kingdom.values()).map(Kingdom::id), builder));
    }

    private static int withKingdom(CommandContext<CommandSourceStack> ctx, KingdomAction action) throws CommandSyntaxException {
        Kingdom kingdom = Kingdom.byName(StringArgumentType.getString(ctx, "kingdom"));
        if (kingdom == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_kingdom"));
            return 0;
        }
        return action.run(kingdom) ? 1 : 0;
    }

    private static boolean tpFort(CommandContext<CommandSourceStack> ctx, Kingdom kingdom) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FortRecord fort = WarData.get(ctx.getSource().getServer()).fort(kingdom);
        if (fort == null) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_fort"));
            return false;
        }
        ServerLevel level = ctx.getSource().getServer().overworld();
        int z = fort.z + FortressBuilder.CLEAR + 12;
        // Generate the column first: an unloaded chunk's heightmap reads as the bottom of the world.
        level.getChunk(fort.x >> 4, z >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, fort.x, z);
        player.teleportTo(level, fort.x + 0.5D, y, z + 0.5D, 180.0F, 0.0F);
        return true;
    }

    private static boolean setTrust(CommandContext<CommandSourceStack> ctx, Kingdom kingdom, int value) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData data = WarData.get(ctx.getSource().getServer());
        data.addTrust(player.getUUID(), kingdom, value - data.trust(player.getUUID(), kingdom));
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.trust_set", kingdom.displayName(), value), false);
        return true;
    }

    private static boolean setDone(CommandContext<CommandSourceStack> ctx, Kingdom kingdom, int count) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        WarData data = WarData.get(ctx.getSource().getServer());
        while (data.standing(player.getUUID()).done(kingdom) < count) {
            data.markDone(player.getUUID(), kingdom);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.done_set", kingdom.displayName(), count), false);
        return true;
    }

    private static boolean forceRequest(CommandContext<CommandSourceStack> ctx, Kingdom kingdom, String type) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Request.Type kind;
        try {
            kind = Request.Type.valueOf(type.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("command.flightsuit.no_request_type"));
            return false;
        }
        return Diplomacy.forceOffer(player, kingdom, kind);
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
