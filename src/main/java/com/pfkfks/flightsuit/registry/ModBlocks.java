package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.block.BatteryBlock;
import com.pfkfks.flightsuit.cleaner.CleanerDockBlock;
import com.pfkfks.flightsuit.block.GeneratorBlock;
import com.pfkfks.flightsuit.block.SecuritySensorBlock;
import com.pfkfks.flightsuit.block.SolarPanelBlock;
import com.pfkfks.flightsuit.block.StationFrameBlock;
import com.pfkfks.flightsuit.block.StationStorageBlock;
import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.village.VillageHallBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FlightSuitMod.MODID);

    public static final RegistryObject<SuitStationBlock> SUIT_STATION = register("suit_station",
            () -> new SuitStationBlock(metal().noOcclusion().pushReaction(PushReaction.BLOCK)));
    /** The station rig around the core; no item of its own, it comes and goes with the station. */
    public static final RegistryObject<StationFrameBlock> STATION_FRAME = BLOCKS.register("station_frame",
            () -> new StationFrameBlock(metal().noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<SolarPanelBlock> SOLAR_PANEL = register("solar_panel",
            () -> new SolarPanelBlock(metal().noOcclusion()));
    public static final RegistryObject<GeneratorBlock> GENERATOR = register("generator",
            () -> new GeneratorBlock(metal().lightLevel(state -> state.getValue(GeneratorBlock.LIT) ? 13 : 0)));
    public static final RegistryObject<BatteryBlock> BATTERY = register("battery",
            () -> new BatteryBlock(metal()));
    public static final RegistryObject<StationStorageBlock> STATION_STORAGE = register("station_storage",
            () -> new StationStorageBlock(metal()));
    public static final RegistryObject<SecuritySensorBlock> SECURITY_SENSOR = register("security_sensor",
            () -> new SecuritySensorBlock(metal()));
    public static final RegistryObject<com.pfkfks.flightsuit.planet.LaunchPadBlock> LAUNCH_PAD = register("launch_pad",
            () -> new com.pfkfks.flightsuit.planet.LaunchPadBlock(metal().lightLevel(state -> 7)));
    /** Not in the creative tab: the balls are placed by the planet (DragonBalls). */
    public static final RegistryObject<com.pfkfks.flightsuit.planet.dbz.DragonBallBlock> DRAGON_BALL = BLOCKS.register("dragon_ball",
            () -> new com.pfkfks.flightsuit.planet.dbz.DragonBallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
                    .strength(-1.0F, 3_600_000.0F).noLootTable().noOcclusion().lightLevel(state -> 9).sound(SoundType.GLASS)));
    public static final RegistryObject<CleanerDockBlock> CLEANER_DOCK = register("cleaner_dock",
            () -> new CleanerDockBlock(metal().noOcclusion()));
    /** Blast-proof, so a creeper can't take the whole village down with it. */
    public static final RegistryObject<VillageHallBlock> VILLAGE_HALL = register("village_hall",
            () -> new VillageHallBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 1200.0F)
                    .sound(SoundType.WOOD)));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F)
                .sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static <T extends Block> RegistryObject<T> register(String name, Supplier<T> block) {
        RegistryObject<T> registered = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }
}
