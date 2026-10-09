package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.suit.EdithGlassesItem;
import com.pfkfks.flightsuit.suit.EnergyCellItem;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitCapsuleItem;
import com.pfkfks.flightsuit.suit.SuitType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FlightSuitMod.MODID);

    public static final RegistryObject<SuitArmorItem> RYAN_MK1_HELMET = ITEMS.register("ryan_mk1_helmet",
            () -> new SuitArmorItem(SuitType.RYAN_MK1, ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<SuitArmorItem> RYAN_MK1_CHESTPLATE = ITEMS.register("ryan_mk1_chestplate",
            () -> new SuitArmorItem(SuitType.RYAN_MK1, ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<SuitArmorItem> RYAN_MK1_LEGGINGS = ITEMS.register("ryan_mk1_leggings",
            () -> new SuitArmorItem(SuitType.RYAN_MK1, ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<SuitArmorItem> RYAN_MK1_BOOTS = ITEMS.register("ryan_mk1_boots",
            () -> new SuitArmorItem(SuitType.RYAN_MK1, ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<SuitCapsuleItem> RYAN_MK1_CAPSULE = ITEMS.register("ryan_mk1_capsule",
            () -> new SuitCapsuleItem(SuitType.RYAN_MK1, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> ARC_REACTOR = ITEMS.register("arc_reactor",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<EnergyCellItem> ENERGY_CELL = ITEMS.register("energy_cell",
            () -> new EnergyCellItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<EdithGlassesItem> EDITH_GLASSES = ITEMS.register("edith_glasses",
            () -> new EdithGlassesItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    private ModItems() {
    }

    public static SuitArmorItem pieceFor(SuitType type, EquipmentSlot slot) {
        // Only one suit so far; this becomes a per-type table once a second suit exists.
        return switch (slot) {
            case HEAD -> RYAN_MK1_HELMET.get();
            case CHEST -> RYAN_MK1_CHESTPLATE.get();
            case LEGS -> RYAN_MK1_LEGGINGS.get();
            default -> RYAN_MK1_BOOTS.get();
        };
    }

    public static SuitCapsuleItem capsuleFor(SuitType type) {
        return RYAN_MK1_CAPSULE.get();
    }
}
