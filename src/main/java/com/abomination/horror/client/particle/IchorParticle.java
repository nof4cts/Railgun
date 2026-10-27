package com.abomination.horror.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Black organic motes that drift, writhe and dissolve. */
public class IchorParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float spin;

    protected IchorParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd + (this.random.nextDouble() - 0.5D) * 0.02D;
        this.yd = yd;
        this.zd = zd + (this.random.nextDouble() - 0.5D) * 0.02D;
        this.gravity = -0.01F;
        this.friction = 0.92F;
        this.lifetime = 30 + this.random.nextInt(40);
        this.quadSize = 0.08F + this.random.nextFloat() * 0.14F;
        this.sprites = sprites;
        this.spin = (this.random.nextFloat() - 0.5F) * 0.2F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        this.oRoll = this.roll;
        this.roll += this.spin;
        this.alpha = 1.0F - (float) this.age / this.lifetime;
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
            return new IchorParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
