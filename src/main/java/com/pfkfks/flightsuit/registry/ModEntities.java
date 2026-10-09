package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.car.HoverCarEntity;
import com.pfkfks.flightsuit.cleaner.CleanerRobotEntity;
import com.pfkfks.flightsuit.entity.CardEntity;
import com.pfkfks.flightsuit.entity.MissileEntity;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.entity.SuitPartEntity;
import com.pfkfks.flightsuit.village.ResidentEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, FlightSuitMod.MODID);

    public static final RegistryObject<EntityType<SuitPartEntity>> SUIT_PART = ENTITY_TYPES.register("suit_part",
            () -> EntityType.Builder.<SuitPartEntity>of(SuitPartEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("suit_part"));

    public static final RegistryObject<EntityType<SuitCompanionEntity>> SUIT_COMPANION = ENTITY_TYPES.register("suit_companion",
            () -> EntityType.Builder.<SuitCompanionEntity>of(SuitCompanionEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("suit_companion"));

    public static final RegistryObject<EntityType<CleanerRobotEntity>> CLEANER_ROBOT = ENTITY_TYPES.register("cleaner_robot",
            () -> EntityType.Builder.<CleanerRobotEntity>of(CleanerRobotEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.3F)
                    .clientTrackingRange(8)
                    .build("cleaner_robot"));

    public static final RegistryObject<EntityType<HoverCarEntity>> HOVER_CAR = ENTITY_TYPES.register("hover_car",
            () -> EntityType.Builder.<HoverCarEntity>of(HoverCarEntity::new, MobCategory.MISC)
                    .sized(1.6F, 0.8F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("hover_car"));

    public static final RegistryObject<EntityType<RemoteBodyEntity>> REMOTE_BODY = ENTITY_TYPES.register("remote_body",
            () -> EntityType.Builder.<RemoteBodyEntity>of(RemoteBodyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .noSave()
                    .clientTrackingRange(10)
                    .build("remote_body"));

    public static final RegistryObject<EntityType<MissileEntity>> MISSILE = ENTITY_TYPES.register("missile",
            () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .noSave()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("missile"));

    public static final RegistryObject<EntityType<CardEntity>> CARD = ENTITY_TYPES.register("card",
            () -> EntityType.Builder.<CardEntity>of(CardEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .noSave()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("card"));

    public static final RegistryObject<EntityType<ResidentEntity>> RESIDENT = ENTITY_TYPES.register("resident",
            () -> EntityType.Builder.<ResidentEntity>of(ResidentEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("resident"));

    private ModEntities() {
    }
}
