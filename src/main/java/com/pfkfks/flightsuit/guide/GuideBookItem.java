package com.pfkfks.flightsuit.guide;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * The FlightSuit guide (after the 4th test round: "a mod that teaches the controls, or make one" - so survival can
 * be played without the README). Right click reads it (client/GuideBook); its pages name the keys as they're bound
 * now. Everyone gets one the first time they join; a book and a redstone make another.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public class GuideBookItem extends Item {
    private static final String GIVEN = "flightsuit_guide_given";

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.pfkfks.flightsuit.client.GuideBook.open());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Kept across deaths: the persisted part of the player's data.
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN)) {
            return;
        }
        persisted.putBoolean(GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(ModItems.GUIDE_BOOK.get());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        player.sendSystemMessage(Component.translatable("message.flightsuit.guide_given").withStyle(ChatFormatting.GOLD));
    }
}
