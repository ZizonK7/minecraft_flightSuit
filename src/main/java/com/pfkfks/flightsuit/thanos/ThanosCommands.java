package com.pfkfks.flightsuit.thanos;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.PlanetData;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Test helpers (cheats / op only): "/flightsuit thanos now" - he comes for you right away;
 * "/flightsuit thanos stones" - all six stones in hand.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class ThanosCommands {
    private ThanosCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("flightsuit")
                // Every registration of the root carries the op check: Brigadier keeps whichever came first.
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("thanos")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("now").executes(ctx -> {
                            boolean started = ThanosRaid.forceNow(ctx.getSource().getPlayerOrException());
                            if (!started) {
                                ctx.getSource().sendFailure(Component.translatable("command.flightsuit.thanos_cant"));
                            }
                            return started ? 1 : 0;
                        }))
                        .then(Commands.literal("stones").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            for (InfinityStone stone : InfinityStone.values()) {
                                ItemStack stack = new ItemStack(stone.item());
                                if (!player.getInventory().add(stack)) {
                                    player.drop(stack, false);
                                }
                            }
                            PlanetData.get(player.server).setDirty();
                            return 1;
                        }))));
    }
}
