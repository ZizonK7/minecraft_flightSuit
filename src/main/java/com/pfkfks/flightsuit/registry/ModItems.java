package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.car.HoverCarCapsuleItem;
import com.pfkfks.flightsuit.cleaner.CleanerRobotItem;
import com.pfkfks.flightsuit.suit.EdithGlassesItem;
import com.pfkfks.flightsuit.suit.EnergyCellItem;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitCapsuleItem;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.thief.BatarangItem;
import com.pfkfks.flightsuit.thief.GrappleItem;
import com.pfkfks.flightsuit.thief.SmokeBombItem;
import com.pfkfks.flightsuit.village.BlueprintItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
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
    /** Display only: what the Mark 4 hero swings, drawn in the empty hand (not in the creative tab, no recipe). */
    public static final RegistryObject<Item> MASTER_SWORD = ITEMS.register("master_sword",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    /** Handed out by the architects; which building is in its NBT (not in the creative tab). */
    public static final RegistryObject<BlueprintItem> BLUEPRINT = ITEMS.register("blueprint",
            () -> new BlueprintItem(new Item.Properties().stacksTo(1)));
    /** Batman's crew (DESIGN.md 4-14): the mark they leave in a robbed chest, and what each drops when beaten. */
    public static final RegistryObject<Item> BAT_MARK = ITEMS.register("bat_mark",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BatarangItem> BATARANG = ITEMS.register("batarang",
            () -> new BatarangItem(new Item.Properties().durability(250).rarity(Rarity.RARE)));
    public static final RegistryObject<GrappleItem> GRAPPLE = ITEMS.register("grapple",
            () -> new GrappleItem(new Item.Properties().durability(200).rarity(Rarity.RARE)));
    public static final RegistryObject<SmokeBombItem> SMOKE_BOMB = ITEMS.register("smoke_bomb",
            () -> new SmokeBombItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    /** Dragon Ball Earth (DESIGN.md 4-16): Raditz's scouter, Korin's senzu beans. */
    public static final RegistryObject<com.pfkfks.flightsuit.planet.dbz.ScouterItem> SCOUTER = ITEMS.register("scouter",
            () -> new com.pfkfks.flightsuit.planet.dbz.ScouterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<com.pfkfks.flightsuit.planet.dbz.SenzuBeanItem> SENZU_BEAN = ITEMS.register("senzu_bean",
            () -> new com.pfkfks.flightsuit.planet.dbz.SenzuBeanItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(20).saturationMod(1.0F).alwaysEat().fast().build())));
    public static final RegistryObject<com.pfkfks.flightsuit.planet.dbz.DragonBallItem> DRAGON_BALL = ITEMS.register("dragon_ball",
            () -> new com.pfkfks.flightsuit.planet.dbz.DragonBallItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<com.pfkfks.flightsuit.planet.dbz.DragonRadarItem> DRAGON_RADAR = ITEMS.register("dragon_radar",
            () -> new com.pfkfks.flightsuit.planet.dbz.DragonRadarItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    /** The six Infinity Stones (DESIGN.md 4-16, M16), by InfinityStone. */
    private static final Map<com.pfkfks.flightsuit.thanos.InfinityStone, RegistryObject<com.pfkfks.flightsuit.thanos.InfinityStoneItem>> STONES =
            new EnumMap<>(com.pfkfks.flightsuit.thanos.InfinityStone.class);

    static {
        for (com.pfkfks.flightsuit.thanos.InfinityStone stone : com.pfkfks.flightsuit.thanos.InfinityStone.values()) {
            STONES.put(stone, ITEMS.register(stone.id() + "_stone", () -> new com.pfkfks.flightsuit.thanos.InfinityStoneItem(stone,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())));
        }
    }

    public static Item stone(com.pfkfks.flightsuit.thanos.InfinityStone stone) {
        return STONES.get(stone).get();
    }

    /** Test helper: drops a wanderer heading for the village it is used in. */
    public static final RegistryObject<ForgeSpawnEggItem> RESIDENT_SPAWN_EGG = ITEMS.register("resident_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.RESIDENT, 0xC99A62, 0x2F5D8C, new Item.Properties()));
    /** Test helpers: a random Three Kingdoms soldier / general marching on the village it is used in. */
    public static final RegistryObject<ForgeSpawnEggItem> KINGDOM_SOLDIER_SPAWN_EGG = ITEMS.register("kingdom_soldier_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.KINGDOM_SOLDIER, 0x8E2A20, 0xD9B54A, new Item.Properties()));
    public static final RegistryObject<ForgeSpawnEggItem> GENERAL_SPAWN_EGG = ITEMS.register("general_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.GENERAL, 0x2E5D3A, 0xE0AE3A, new Item.Properties()));

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
