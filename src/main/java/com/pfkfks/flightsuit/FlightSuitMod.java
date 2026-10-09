package com.pfkfks.flightsuit;

import com.mojang.logging.LogUtils;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import com.pfkfks.flightsuit.registry.ModBlocks;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(FlightSuitMod.MODID)
public class FlightSuitMod {
    public static final String MODID = "flightsuit";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> SUIT_TAB = CREATIVE_MODE_TABS.register("suits",
            () -> CreativeModeTab.builder()
                    // Without .title() the tab name silently stays blank.
                    .title(Component.translatable("itemGroup.flightsuit.suits"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.RYAN_MK1_HELMET.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.EDITH_GLASSES.get());
                        output.accept(ModBlocks.SUIT_STATION.get());
                        output.accept(ModBlocks.SOLAR_PANEL.get());
                        output.accept(ModBlocks.GENERATOR.get());
                        output.accept(ModBlocks.BATTERY.get());
                        output.accept(ModItems.RYAN_MK1_CAPSULE.get().createFilledCapsule());
                        output.accept(ModItems.RYAN_MK1_CAPSULE.get());
                        output.accept(ModItems.RYAN_MK1_HELMET.get());
                        output.accept(ModItems.RYAN_MK1_CHESTPLATE.get());
                        output.accept(ModItems.RYAN_MK1_LEGGINGS.get());
                        output.accept(ModItems.RYAN_MK1_BOOTS.get());
                        output.accept(ModItems.ARC_REACTOR.get());
                        output.accept(ModItems.ENERGY_CELL.get());
                    })
                    .build());

    public FlightSuitMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        // ModBlocks first: loading it also queues the block items onto ModItems.ITEMS.
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
