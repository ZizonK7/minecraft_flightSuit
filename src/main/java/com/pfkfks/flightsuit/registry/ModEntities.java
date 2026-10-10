package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.car.HoverCarEntity;
import com.pfkfks.flightsuit.cleaner.CleanerRobotEntity;
import com.pfkfks.flightsuit.entity.CardEntity;
import com.pfkfks.flightsuit.entity.MissileEntity;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.entity.SuitPartEntity;
import com.pfkfks.flightsuit.hero.CityHeroEntity;
import com.pfkfks.flightsuit.thief.ThiefEntity;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.war.GeneralEntity;
import com.pfkfks.flightsuit.war.KingdomSoldierEntity;
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

    public static final RegistryObject<EntityType<KingdomSoldierEntity>> KINGDOM_SOLDIER = ENTITY_TYPES.register("kingdom_soldier",
            () -> EntityType.Builder.<KingdomSoldierEntity>of(KingdomSoldierEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("kingdom_soldier"));

    public static final RegistryObject<EntityType<GeneralEntity>> GENERAL = ENTITY_TYPES.register("general",
            () -> EntityType.Builder.<GeneralEntity>of(GeneralEntity::new, MobCategory.MISC)
                    .sized(0.65F, 1.95F)
                    .clientTrackingRange(12)
                    .build("general"));

    public static final RegistryObject<EntityType<CityHeroEntity>> CITY_HERO = ENTITY_TYPES.register("city_hero",
            () -> EntityType.Builder.<CityHeroEntity>of(CityHeroEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("city_hero"));

    public static final RegistryObject<EntityType<com.pfkfks.flightsuit.planet.SpaceshipEntity>> SPACESHIP = ENTITY_TYPES.register("spaceship",
            () -> EntityType.Builder.<com.pfkfks.flightsuit.planet.SpaceshipEntity>of(com.pfkfks.flightsuit.planet.SpaceshipEntity::new, MobCategory.MISC)
                    .sized(2.4F, 2.4F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("spaceship"));

    public static final RegistryObject<EntityType<com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity>> DBZ_FIGHTER = ENTITY_TYPES.register("dbz_fighter",
            () -> EntityType.Builder.<com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity>of(com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(12)
                    // M17: cutscene actors are moved every tick - keep the moves smooth.
                    .updateInterval(2)
                    .build("dbz_fighter"));

    public static final RegistryObject<EntityType<com.pfkfks.flightsuit.thanos.ThanosForceEntity>> THANOS_FORCE = ENTITY_TYPES.register("thanos_force",
            () -> EntityType.Builder.<com.pfkfks.flightsuit.thanos.ThanosForceEntity>of(com.pfkfks.flightsuit.thanos.ThanosForceEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(12)
                    .build("thanos_force"));

    public static final RegistryObject<EntityType<ThiefEntity>> THIEF = ENTITY_TYPES.register("thief",
            () -> EntityType.Builder.<ThiefEntity>of(ThiefEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("thief"));

    /** An invisible seat (the fortress throne) - never saved. */
    /** M17: the Hulkbuster's delivery pod (Veronica), purely visual. */
    public static final RegistryObject<EntityType<com.pfkfks.flightsuit.entity.VeronicaPodEntity>> VERONICA_POD = ENTITY_TYPES.register("veronica_pod",
            () -> EntityType.Builder.<com.pfkfks.flightsuit.entity.VeronicaPodEntity>of(com.pfkfks.flightsuit.entity.VeronicaPodEntity::new, MobCategory.MISC)
                    .sized(1.8F, 3.0F)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("veronica_pod"));

    /** M17: the cutscene camera, only ever made on the client (cutscene.CameraEntity). */
    public static final RegistryObject<EntityType<com.pfkfks.flightsuit.cutscene.CameraEntity>> CUTSCENE_CAMERA = ENTITY_TYPES.register("cutscene_camera",
            () -> EntityType.Builder.<com.pfkfks.flightsuit.cutscene.CameraEntity>of(com.pfkfks.flightsuit.cutscene.CameraEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .noSave()
                    .noSummon()
                    .clientTrackingRange(1)
                    .build("cutscene_camera"));

    public static final RegistryObject<EntityType<com.pfkfks.flightsuit.entity.SeatEntity>> SEAT = ENTITY_TYPES.register("seat",
            () -> EntityType.Builder.<com.pfkfks.flightsuit.entity.SeatEntity>of(com.pfkfks.flightsuit.entity.SeatEntity::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F)
                    .noSave()
                    .noSummon()
                    .clientTrackingRange(10)
                    .build("seat"));

    /** The townsfolk of the fortresses and Hero City (after the M16 test) - never saved. */
    public static final RegistryObject<EntityType<com.pfkfks.flightsuit.town.TownsfolkEntity>> TOWNSFOLK = ENTITY_TYPES.register("townsfolk",
            () -> EntityType.Builder.<com.pfkfks.flightsuit.town.TownsfolkEntity>of(com.pfkfks.flightsuit.town.TownsfolkEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.9F)
                    .noSave()
                    .clientTrackingRange(10)
                    .build("townsfolk"));

    /** Shenron, summoned by the Dragon Balls (DragonBalls) - never saved; huge, so seen from far off. */
    public static final RegistryObject<EntityType<com.pfkfks.flightsuit.planet.dbz.ShenronEntity>> SHENRON = ENTITY_TYPES.register("shenron",
            () -> EntityType.Builder.<com.pfkfks.flightsuit.planet.dbz.ShenronEntity>of(com.pfkfks.flightsuit.planet.dbz.ShenronEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("shenron"));

    private ModEntities() {
    }
}
