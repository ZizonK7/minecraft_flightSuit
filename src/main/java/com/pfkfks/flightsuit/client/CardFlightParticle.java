package com.pfkfks.flightsuit.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A phantom card flying straight and spinning (M17): the Tempest's rain falling out of the purple sky, the steal's
 * cards on their way to the target. Velocity per tick from the packet; no gravity, no collisions; ModParticles.CARD_FLIGHT_LIFE ticks.
 */
public class CardFlightParticle extends TextureSheetParticle {
    protected CardFlightParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.lifetime = com.pfkfks.flightsuit.registry.ModParticles.CARD_FLIGHT_LIFE;
        this.quadSize = 0.24F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.roll = level.random.nextFloat() * 6.28F;
        this.oRoll = this.roll;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        this.roll += 0.7F;
        super.tick();
        if (this.lifetime - this.age < 2) {
            this.alpha = 0.5F;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new CardFlightParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
