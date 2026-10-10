package com.pfkfks.flightsuit.registry;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, FlightSuitMod.MODID);

    /**
     * A phantom card (client/CardSwirlParticle) turning round a point. Sent with count 0 so the "speed" carries its
     * orbit: x = start angle, y = radius (negative = a burst flying outwards), z = lifetime in ticks.
     */
    public static final RegistryObject<SimpleParticleType> CARD_SWIRL =
            PARTICLE_TYPES.register("card_swirl", () -> new SimpleParticleType(true));

    /**
     * A phantom card flying straight (client/CardFlightParticle, M17): the Tempest's rain, the steal's cards out to
     * the target. Sent with count 0: x/y/z = its velocity per tick; it lives CARD_FLIGHT_LIFE ticks.
     */
    public static final RegistryObject<SimpleParticleType> CARD_FLIGHT =
            PARTICLE_TYPES.register("card_flight", () -> new SimpleParticleType(true));

    /** How long a flying card lives: senders scale its velocity to the distance it should cover. */
    public static final int CARD_FLIGHT_LIFE = 9;

    private ModParticles() {
    }
}
