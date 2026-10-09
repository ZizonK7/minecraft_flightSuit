package com.pfkfks.flightsuit.suit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Test helpers (cheats / op only): set the durability or energy of the worn suit and of your companion
 * suits within 16 blocks, as a percentage. "/flightsuit durability 0" leaves every piece at its last
 * point, so the next hit triggers the forced ejection.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class SuitCommands {
    private SuitCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("flightsuit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("durability")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> apply(ctx, "durability", SuitCommands::setDurability))))
                .then(Commands.literal("energy")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> apply(ctx, "energy", SuitCommands::setEnergy)))));
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, String what, BiConsumer<ItemStack, Integer> setter)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int percent = IntegerArgumentType.getInteger(ctx, "percent");
        List<ItemStack> pieces = new ArrayList<>();
        for (EquipmentSlot slot : WornSuit.SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof SuitArmorItem) {
                pieces.add(stack);
            }
        }
        for (SuitCompanionEntity suit : Companions.owned(player, 16.0D)) {
            for (EquipmentSlot slot : WornSuit.SLOTS) {
                ItemStack stack = suit.getItemBySlot(slot);
                if (stack.getItem() instanceof SuitArmorItem) {
                    pieces.add(stack);
                }
            }
        }
        for (ItemStack piece : pieces) {
            setter.accept(piece, percent);
        }
        int count = pieces.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.flightsuit.set", what, percent, count), false);
        return count;
    }

    private static void setDurability(ItemStack stack, int percent) {
        int max = stack.getMaxDamage();
        int left = Math.max(1, Math.round(max * percent / 100.0F));
        stack.setDamageValue(max - left);
    }

    private static void setEnergy(ItemStack stack, int percent) {
        SuitEnergy.set(stack, Math.round(SuitEnergy.capacity(stack) * percent / 100.0F));
    }
}
