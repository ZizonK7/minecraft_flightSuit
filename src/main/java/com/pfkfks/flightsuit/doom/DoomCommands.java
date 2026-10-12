package com.pfkfks.flightsuit.doom;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * The Doom saga's commands (DESIGN.md 4-17).
 * Anyone: "/doom" - what's next and which way; "/doom key" - a new bridge key if yours is lost.
 * Test helpers (op): "/flightsuit doom start" (the incursion now), "stage <name>", "cores <0-7>", "tp <site>"
 * (Battleworld's sights), "stop" (call off the fight on in Battleworld).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class DoomCommands {
    private DoomCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("doom")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!DoomSaga.objective(player)) {
                        player.sendSystemMessage(Component.translatable("doom.flightsuit.goal.none").withStyle(ChatFormatting.GRAY));
                    }
                    return 1;
                })
                .then(Commands.literal("key").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (!DoomSaga.reached(player, DoomSaga.Stage.GATE) || player.getInventory().contains(new ItemStack(ModItems.BRIDGE_KEY.get()))) {
                        ctx.getSource().sendFailure(Component.translatable("doom.flightsuit.no_key"));
                        return 0;
                    }
                    ItemStack key = new ItemStack(ModItems.BRIDGE_KEY.get());
                    if (!player.getInventory().add(key)) {
                        player.drop(key, false);
                    }
                    return 1;
                })));
        event.getDispatcher().register(Commands.literal("flightsuit")
                // Every registration of the root carries the op check: Brigadier keeps whichever came first.
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("doom")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DoomSaga.setStage(player, null);
                            return DoomSaga.startIncursion(player) ? 1 : 0;
                        }))
                        .then(Commands.literal("stage")
                                .then(Commands.argument("stage", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(DoomSaga.Stage.values()).map(s -> s.name().toLowerCase(java.util.Locale.ROOT)), builder))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String name = StringArgumentType.getString(ctx, "stage").toUpperCase(java.util.Locale.ROOT);
                                            try {
                                                DoomSaga.setStage(player, DoomSaga.Stage.valueOf(name));
                                            } catch (IllegalArgumentException e) {
                                                ctx.getSource().sendFailure(Component.literal(name));
                                                return 0;
                                            }
                                            if (DoomSaga.reached(player, DoomSaga.Stage.GATE)
                                                    && !player.getInventory().contains(new ItemStack(ModItems.BRIDGE_KEY.get()))) {
                                                player.getInventory().add(new ItemStack(ModItems.BRIDGE_KEY.get()));
                                            }
                                            DoomSaga.objective(player);
                                            return 1;
                                        })))
                        .then(Commands.literal("cores")
                                .then(Commands.argument("cores", IntegerArgumentType.integer(0, 7)).executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    DoomData data = DoomData.get(player.server);
                                    data.walker(player.getUUID()).cores = IntegerArgumentType.getInteger(ctx, "cores");
                                    data.setDirty();
                                    DoomSaga.objective(player);
                                    return 1;
                                })))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("site", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(DoomSites.values()).map(DoomSites::id), builder))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerLevel level = player.server.getLevel(Multiverse.BATTLEWORLD);
                                            DoomSites site = null;
                                            for (DoomSites s : DoomSites.values()) {
                                                if (s.id().equals(StringArgumentType.getString(ctx, "site"))) {
                                                    site = s;
                                                }
                                            }
                                            if (level == null || site == null) {
                                                ctx.getSource().sendFailure(Component.translatable("doom.flightsuit.gate.no_world"));
                                                return 0;
                                            }
                                            BlockPos built = site.center(DoomData.get(player.server));
                                            BlockPos at = built != null ? built : site.around(DoomSites.hub(level));
                                            level.getChunkAt(at);
                                            int y = built != null ? built.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                                            player.teleportTo(level, at.getX() + 0.5D, y + 1.0D, at.getZ() + 8.5D, 180.0F, 0.0F);
                                            return 1;
                                        })))
                        .then(Commands.literal("stop").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getServer().getLevel(Multiverse.BATTLEWORLD);
                            return level != null && DoomSaga.stopFight(level) ? 1 : 0;
                        }))));
    }
}
