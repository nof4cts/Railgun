package com.abomination.horror.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Heavy droplets that arc, fall, and splat on the ground where they linger. */
public class BloodParticle extends TextureSheetParticle {
    private boolean landed;

    protected BloodParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.95F;
        this.friction = 0.97F;
        this.lifetime = 50 + this.random.nextInt(50);
        this.quadSize = 0.05F + this.random.nextFloat() * 0.09F;
        this.hasPhysics = true;
        float c = 0.65F + this.random.nextFloat() * 0.35F;
        this.rCol = c;
        this.gCol = c * 0.9F;
        this.bCol = c * 0.9F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.onGround && !this.landed) {
            this.landed = true;
            this.xd = 0.0D;
            this.zd = 0.0D;
            this.quadSize *= 1.7F;
            this.lifetime = this.age + 80 + this.random.nextInt(120);
        }
        int left = this.lifetime - this.age;
        if (left < 20) this.alpha = left / 20.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new BloodParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
