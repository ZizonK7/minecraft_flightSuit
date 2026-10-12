package com.pfkfks.flightsuit.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * 2026-10-12: a "Z" off a sleeper (Link's song, HeroArts) - it drifts up and to the side in a slow wobble, grows a
 * little and fades. Sent with count 0: x = the side it drifts to (-1..1).
 */
public class SleepZParticle extends TextureSheetParticle {
    private final double drift;

    protected SleepZParticle(ClientLevel level, double x, double y, double z, double side, SpriteSet sprites) {
        super(level, x, y, z);
        this.drift = side == 0.0D ? 0.5D : side;
        this.lifetime = 36;
        this.quadSize = 0.14F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.rCol = 0.75F;
        this.gCol = 0.85F;
        this.bCol = 1.0F;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) age / lifetime;
        this.xd = drift * 0.02D + Mth.sin(age * 0.35F) * 0.012D;
        this.yd = 0.035D;
        this.zd = Mth.cos(age * 0.35F) * 0.008D;
        this.quadSize = 0.14F + 0.12F * t;
        this.alpha = t > 0.6F ? 1.0F - (t - 0.6F) / 0.4F : 1.0F;
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
            return new SleepZParticle(level, x, y, z, dx, sprites);
        }
    }
}
