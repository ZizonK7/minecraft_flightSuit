package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.car.HoverCarCapsuleItem;
import com.pfkfks.flightsuit.cleaner.CleanerRobotItem;
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

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FlightSuitMod.MODID);

    /** Per suit type: its four pieces (by armor slot) and its capsule. */
    private static final Map<SuitType, Map<EquipmentSlot, RegistryObject<SuitArmorItem>>> PIECES = new EnumMap<>(SuitType.class);
    private static final Map<SuitType, RegistryObject<SuitCapsuleItem>> CAPSULES = new EnumMap<>(SuitType.class);

    static {
        for (SuitType type : SuitType.values()) {
            Rarity rarity = type == SuitType.RYAN_MK1 ? Rarity.UNCOMMON : Rarity.RARE;
            Map<EquipmentSlot, RegistryObject<SuitArmorItem>> pieces = new EnumMap<>(EquipmentSlot.class);
            pieces.put(EquipmentSlot.HEAD, piece(type, ArmorItem.Type.HELMET, rarity));
            pieces.put(EquipmentSlot.CHEST, piece(type, ArmorItem.Type.CHESTPLATE, rarity));
            pieces.put(EquipmentSlot.LEGS, piece(type, ArmorItem.Type.LEGGINGS, rarity));
            pieces.put(EquipmentSlot.FEET, piece(type, ArmorItem.Type.BOOTS, rarity));
            PIECES.put(type, pieces);
            CAPSULES.put(type, ITEMS.register(type.id() + "_capsule",
                    () -> new SuitCapsuleItem(type, new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));
        }
    }

    public static final RegistryObject<SuitArmorItem> RYAN_MK1_HELMET = PIECES.get(SuitType.RYAN_MK1).get(EquipmentSlot.HEAD);
    public static final RegistryObject<SuitArmorItem> RYAN_MK1_CHESTPLATE = PIECES.get(SuitType.RYAN_MK1).get(EquipmentSlot.CHEST);
    public static final RegistryObject<SuitArmorItem> RYAN_MK1_LEGGINGS = PIECES.get(SuitType.RYAN_MK1).get(EquipmentSlot.LEGS);
    public static final RegistryObject<SuitArmorItem> RYAN_MK1_BOOTS = PIECES.get(SuitType.RYAN_MK1).get(EquipmentSlot.FEET);
    public static final RegistryObject<SuitCapsuleItem> RYAN_MK1_CAPSULE = CAPSULES.get(SuitType.RYAN_MK1);

    public static final RegistryObject<Item> ARC_REACTOR = ITEMS.register("arc_reactor",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<EnergyCellItem> ENERGY_CELL = ITEMS.register("energy_cell",
            () -> new EnergyCellItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<EdithGlassesItem> EDITH_GLASSES = ITEMS.register("edith_glasses",
            () -> new EdithGlassesItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<CleanerRobotItem> CLEANER_ROBOT = ITEMS.register("cleaner_robot",
            () -> new CleanerRobotItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<HoverCarCapsuleItem> HOVER_CAR_CAPSULE = ITEMS.register("hover_car_capsule",
            () -> new HoverCarCapsuleItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private ModItems() {
    }

    private static RegistryObject<SuitArmorItem> piece(SuitType type, ArmorItem.Type slot, Rarity rarity) {
        return ITEMS.register(type.id() + "_" + slot.getName(),
                () -> new SuitArmorItem(type, slot, new Item.Properties().rarity(rarity)));
    }

    public static SuitArmorItem pieceFor(SuitType type, EquipmentSlot slot) {
        return PIECES.get(type).get(slot).get();
    }

    public static SuitCapsuleItem capsuleFor(SuitType type) {
        return CAPSULES.get(type).get();
    }
}
