package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.block.BatteryBlock;
import com.pfkfks.flightsuit.block.GeneratorBlock;
import com.pfkfks.flightsuit.block.SolarPanelBlock;
import com.pfkfks.flightsuit.block.SuitStationBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FlightSuitMod.MODID);

    public static final RegistryObject<SuitStationBlock> SUIT_STATION = register("suit_station",
            () -> new SuitStationBlock(metal().noOcclusion()));
    public static final RegistryObject<SolarPanelBlock> SOLAR_PANEL = register("solar_panel",
            () -> new SolarPanelBlock(metal().noOcclusion()));
    public static final RegistryObject<GeneratorBlock> GENERATOR = register("generator",
            () -> new GeneratorBlock(metal().lightLevel(state -> state.getValue(GeneratorBlock.LIT) ? 13 : 0)));
    public static final RegistryObject<BatteryBlock> BATTERY = register("battery",
            () -> new BatteryBlock(metal()));

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
