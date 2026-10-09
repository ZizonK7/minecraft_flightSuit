package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.entity.SuitPartEntity;
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

    private ModEntities() {
    }
}
