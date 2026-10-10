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
 * One of the phantom's cards in the shadow step (after the 4th test round: "it's just smoke, not cards round me").
 * Swirl: it opens out round the centre, turns and rises, then closes in on it as the step happens. Burst (negative
 * radius): flung outwards from the centre, spinning, falling a little. The card faces (blanche, noir) from the
 * thrown cards' textures.
 */
public class CardSwirlParticle extends TextureSheetParticle {
    private final double cx;
    private final double cy;
    private final double cz;
    private final double radius;
    private final boolean burst;
    private double angle;

    protected CardSwirlParticle(ClientLevel level, double x, double y, double z, double angle, double radius, double life, SpriteSet sprites) {
        super(level, x, y, z);
        this.cx = x;
        this.cy = y;
        this.cz = z;
        this.angle = angle;
        this.burst = radius < 0.0D;
        this.radius = Math.abs(radius);
        this.lifetime = Math.max(4, (int) life);
        this.quadSize = 0.22F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.roll = (float) (angle * 2.0D);
        this.oRoll = this.roll;
        pickSprite(sprites);
        place();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            remove();
            return;
        }
        this.angle += burst ? 0.25D : 0.55D;
        this.roll += burst ? 0.6F : 0.25F;
        place();
        if (this.lifetime - this.age < 3) {
            this.alpha = (this.lifetime - this.age) / 3.0F;
        }
    }

    /** Where it is on its path at this age. */
    private void place() {
        float t = this.age / (float) this.lifetime;
        double r;
        double y;
        if (burst) {
            r = 0.3D + this.radius * Math.sqrt(t);
            y = this.cy + 0.6D * t - 1.2D * t * t;
        } else {
            // Opens out over the first quarter, holds while turning, closes in on the body over the last quarter.
            float open = Mth.clamp(t / 0.25F, 0.0F, 1.0F);
            float close = Mth.clamp((1.0F - t) / 0.25F, 0.0F, 1.0F);
            r = this.radius * (0.35D + 0.65D * Math.min(open, close));
            y = this.cy + 0.5D * t;
        }
        setPos(this.cx + Math.cos(this.angle) * r, y, this.cz + Math.sin(this.angle) * r);
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
                                       double angle, double radius, double life) {
            return new CardSwirlParticle(level, x, y, z, angle, radius, life, sprites);
        }
    }
}
