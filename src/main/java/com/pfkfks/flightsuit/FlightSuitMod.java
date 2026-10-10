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
import com.pfkfks.flightsuit.village.ResidentEntity;
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
                        output.accept(ModItems.GUIDE_BOOK.get());
                        output.accept(ModItems.EDITH_GLASSES.get());
                        output.accept(ModBlocks.SUIT_STATION.get());
                        output.accept(ModBlocks.SOLAR_PANEL.get());
                        output.accept(ModBlocks.GENERATOR.get());
                        output.accept(ModBlocks.BATTERY.get());
                        output.accept(ModBlocks.STATION_STORAGE.get());
                        output.accept(ModBlocks.SECURITY_SENSOR.get());
                        output.accept(ModBlocks.LAUNCH_PAD.get());
                        output.accept(ModBlocks.CLEANER_DOCK.get());
                        output.accept(ModItems.CLEANER_ROBOT.get());
                        output.accept(ModItems.HOVER_CAR_CAPSULE.get());
                        output.accept(ModBlocks.VILLAGE_HALL.get());
                        output.accept(ModItems.RESIDENT_SPAWN_EGG.get());
                        output.accept(ModItems.KINGDOM_SOLDIER_SPAWN_EGG.get());
                        output.accept(ModItems.GENERAL_SPAWN_EGG.get());
                        output.accept(ModItems.BAT_MARK.get());
                        output.accept(ModItems.BATARANG.get());
                        output.accept(ModItems.GRAPPLE.get());
                        output.accept(ModItems.SMOKE_BOMB.get());
                        output.accept(ModItems.SCOUTER.get());
                        output.accept(ModItems.SENZU_BEAN.get());
                        output.accept(ModItems.WUZHU_COIN.get());
                        output.accept(ModItems.DOLLAR.get());
                        output.accept(ModItems.DRAGON_RADAR.get());
                        output.accept(ModItems.INFINITY_GAUNTLET.get());
                        for (com.pfkfks.flightsuit.thanos.InfinityStone stone : com.pfkfks.flightsuit.thanos.InfinityStone.values()) {
                            output.accept(ModItems.stone(stone));
                        }
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
        com.pfkfks.flightsuit.registry.ModParticles.PARTICLE_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SUIT_COMPANION.get(), SuitCompanionEntity.createAttributes().build());
        event.put(ModEntities.CLEANER_ROBOT.get(), CleanerRobotEntity.createAttributes().build());
        event.put(ModEntities.REMOTE_BODY.get(), RemoteBodyEntity.createAttributes().build());
        event.put(ModEntities.RESIDENT.get(), ResidentEntity.createAttributes().build());
        event.put(ModEntities.KINGDOM_SOLDIER.get(), com.pfkfks.flightsuit.war.KingdomSoldierEntity.createAttributes().build());
        event.put(ModEntities.GENERAL.get(), com.pfkfks.flightsuit.war.GeneralEntity.createAttributes().build());
        event.put(ModEntities.CITY_HERO.get(), com.pfkfks.flightsuit.hero.CityHeroEntity.createAttributes().build());
        event.put(ModEntities.TOWNSFOLK.get(), com.pfkfks.flightsuit.town.TownsfolkEntity.createAttributes().build());
        event.put(ModEntities.THIEF.get(), com.pfkfks.flightsuit.thief.ThiefEntity.createAttributes().build());
        event.put(ModEntities.DBZ_FIGHTER.get(), com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity.createAttributes().build());
        event.put(ModEntities.THANOS_FORCE.get(), com.pfkfks.flightsuit.thanos.ThanosForceEntity.createAttributes().build());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
