package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The weapon a suit puts in its wearer's hand (M18, after the user's "holding right click is a poor feel for a
 * sword"): Trunks' sword, the Master Sword, the bishop's staff, the cannon. It appears in the hotbar while the
 * suit's chestplate is on and goes away with it - it can't be dropped, stored or crafted, and a stray copy
 * vanishes the moment it lands in anyone's bag without the suit on. Left click swings it (SuitWeapons.SWING, sent
 * by the client's attack key), right click is the weapon's own stance (SuitWeapons' primary). Damage, energy and
 * cooldowns are the suit's (the arts classes), never the item's.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public class SuitWeaponItem extends Item {
    public enum Kind {
        MASTER_SWORD("master_sword"),
        TRUNKS_SWORD("trunks_sword"),
        BISHOP_STAFF("bishop_staff"),
        CANNON("cannon");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private final Kind kind;

    public SuitWeaponItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** The weapon item for a suit class's weapon, or null for the bare-handed classes. */
    public static @Nullable Item of(@Nullable SuitWeaponItem.Kind kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case MASTER_SWORD -> ModItems.MASTER_SWORD.get();
            case TRUNKS_SWORD -> ModItems.TRUNKS_SWORD.get();
            case BISHOP_STAFF -> ModItems.BISHOP_STAFF.get();
            case CANNON -> ModItems.CANNON.get();
        };
    }

    /** The weapon the player's suit wants in hand right now (by the chestplate), or null. */
    public static @Nullable Kind wanted(Player player) {
        SuitClass suitClass = SuitWeapons.armedClass(player);
        return suitClass == null ? null : suitClass.weapon();
    }

    /** True while the player holds the weapon their suit gave them (the arts only answer to that). */
    public static boolean holding(Player player) {
        Kind wanted = wanted(player);
        return wanted != null && player.getMainHandItem().getItem() instanceof SuitWeaponItem weapon && weapon.kind == wanted;
    }

    /**
     * Every half second (SuitServerEvents): the suit's weapon is in the bag exactly once while its chestplate is on
     * - put into the selected slot if that's free, else the first free hotbar slot, else anywhere - and not at all
     * otherwise. Companion suits carry their own copies (SuitCompanionEntity) and never go through here.
     */
    public static void sync(ServerPlayer player) {
        Kind wanted = wanted(player);
        boolean found = false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof SuitWeaponItem weapon)) {
                continue;
            }
            if (wanted == null || weapon.kind != wanted || found) {
                inventory.setItem(i, ItemStack.EMPTY);
            } else {
                found = true;
            }
        }
        if (wanted == null || found) {
            return;
        }
        ItemStack weapon = new ItemStack(of(wanted));
        int slot = inventory.selected;
        if (!inventory.getItem(slot).isEmpty()) {
            slot = -1;
            for (int i = 0; i < 9; i++) {
                if (inventory.getItem(i).isEmpty()) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                slot = inventory.getFreeSlot();
            }
        }
        if (slot >= 0) {
            inventory.setItem(slot, weapon);
            player.inventoryMenu.broadcastChanges();
        }
    }

    /** Found in a bag whose owner doesn't wear the suit: gone. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof Player player && wanted(player) != kind) {
            stack.setCount(0);
        }
    }

    /** Never swung at blocks: the suit's weapons don't dig. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.suit_weapon").withStyle(ChatFormatting.GRAY));
    }

    /** The weapon stays in the bag: a throw puts it straight back. */
    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() instanceof SuitWeaponItem) {
            event.setCanceled(true);
        }
    }

    /**
     * A left click on a monster with the weapon never does vanilla sword damage: the client's attack key sends
     * SuitWeapons.SWING and the arts land the hit (cancelled on both sides so the client doesn't play the vanilla
     * hit either).
     */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof SuitWeaponItem) {
            event.setCanceled(true);
        }
    }

    /** For the renderers: the chest piece the weapon belongs to is on (otherwise the item is about to vanish anyway). */
    public static boolean worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitArmorItem;
    }
}
