package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.block.BatteryBlockEntity;
import com.pfkfks.flightsuit.block.GeneratorBlockEntity;
import com.pfkfks.flightsuit.block.SecuritySensorBlockEntity;
import com.pfkfks.flightsuit.block.SolarPanelBlockEntity;
import com.pfkfks.flightsuit.block.StationStorageBlockEntity;
import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.cleaner.CleanerDockBlockEntity;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FlightSuitMod.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SuitStationBlockEntity>> SUIT_STATION = BLOCK_ENTITY_TYPES.register("suit_station",
            () -> BlockEntityType.Builder.of(SuitStationBlockEntity::new, ModBlocks.SUIT_STATION.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL = BLOCK_ENTITY_TYPES.register("solar_panel",
            () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<GeneratorBlockEntity>> GENERATOR = BLOCK_ENTITY_TYPES.register("generator",
            () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, ModBlocks.GENERATOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<BatteryBlockEntity>> BATTERY = BLOCK_ENTITY_TYPES.register("battery",
            () -> BlockEntityType.Builder.of(BatteryBlockEntity::new, ModBlocks.BATTERY.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CleanerDockBlockEntity>> CLEANER_DOCK = BLOCK_ENTITY_TYPES.register("cleaner_dock",
            () -> BlockEntityType.Builder.of(CleanerDockBlockEntity::new, ModBlocks.CLEANER_DOCK.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<VillageHallBlockEntity>> VILLAGE_HALL = BLOCK_ENTITY_TYPES.register("village_hall",
            () -> BlockEntityType.Builder.of(VillageHallBlockEntity::new, ModBlocks.VILLAGE_HALL.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<StationStorageBlockEntity>> STATION_STORAGE = BLOCK_ENTITY_TYPES.register("station_storage",
            () -> BlockEntityType.Builder.of(StationStorageBlockEntity::new, ModBlocks.STATION_STORAGE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SecuritySensorBlockEntity>> SECURITY_SENSOR = BLOCK_ENTITY_TYPES.register("security_sensor",
            () -> BlockEntityType.Builder.of(SecuritySensorBlockEntity::new, ModBlocks.SECURITY_SENSOR.get()).build(null));

    private ModBlockEntities() {
    }
}
