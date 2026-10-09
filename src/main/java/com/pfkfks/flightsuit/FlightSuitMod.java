package com.pfkfks.flightsuit;

import com.mojang.logging.LogUtils;
import com.pfkfks.flightsuit.cleaner.CleanerRobotEntity;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import com.pfkfks.flightsuit.registry.ModBlocks;
import com.pfkfks.flightsuit.registry.ModEntities;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
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
                        output.accept(ModBlocks.CLEANER_DOCK.get());
                        output.accept(ModItems.CLEANER_ROBOT.get());
                        output.accept(ModItems.HOVER_CAR_CAPSULE.get());
                        for (SuitType type : SuitType.values()) {
                            output.accept(ModItems.capsuleFor(type).createFilledCapsule());
                            output.accept(ModItems.capsuleFor(type));
                            for (EquipmentSlot slot : WornSuit.SLOTS) {
                                output.accept(ModItems.pieceFor(type, slot));
                            }
                        }
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
        modEventBus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SUIT_COMPANION.get(), SuitCompanionEntity.createAttributes().build());
        event.put(ModEntities.CLEANER_ROBOT.get(), CleanerRobotEntity.createAttributes().build());
        event.put(ModEntities.REMOTE_BODY.get(), RemoteBodyEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
