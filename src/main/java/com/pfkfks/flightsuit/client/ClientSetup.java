package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.registry.ModBlockEntities;
import com.pfkfks.flightsuit.registry.ModEntities;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(SuitAnimator::init);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        SuitArmorModels.registerLayers(event);
        event.registerLayerDefinition(HoverCarModel.LAYER, HoverCarModel::create);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        SuitArmorModels.bake(event.getEntityModels());
        for (String skin : event.getSkins()) {
            LivingEntityRenderer<?, ?> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer player) {
                player.addLayer(new HeroRenderer.SwordLayer(player, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SUIT_PART.get(), SuitPartRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SUIT_STATION.get(), SuitStationRenderer::new);
        event.registerEntityRenderer(ModEntities.SUIT_COMPANION.get(), SuitCompanionRenderer::new);
        event.registerEntityRenderer(ModEntities.CLEANER_ROBOT.get(), CleanerRobotRenderer::new);
        event.registerEntityRenderer(ModEntities.HOVER_CAR.get(), HoverCarRenderer::new);
        event.registerEntityRenderer(ModEntities.REMOTE_BODY.get(), RemoteBodyRenderer::new);
        event.registerEntityRenderer(ModEntities.MISSILE.get(), MissileRenderer::new);
        event.registerEntityRenderer(ModEntities.CARD.get(), CardRenderer::new);
        event.registerEntityRenderer(ModEntities.RESIDENT.get(), ResidentRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VILLAGE_HALL.get(), VillageHallRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.SUIT_TOGGLE);
        event.register(ModKeys.COMMAND_ATTACK);
        event.register(ModKeys.COUNTER);
        event.register(ModKeys.SUIT_WHEEL);
        event.register(ModKeys.SKILL_1);
        event.register(ModKeys.SKILL_2);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("helmet_targets", HelmetTargetOverlay.INSTANCE);
        event.registerAboveAll("suit_hud", SuitHudOverlay.INSTANCE);
        event.registerAboveAll("hover_car_hud", HoverCarHudOverlay.INSTANCE);
        event.registerAboveAll("remote_link", RemoteLinkOverlay.INSTANCE);
    }
}
