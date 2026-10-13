package com.ryu.mod.fx.client;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;

public class EnergyParticle extends TextureSheetParticle {
   public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
      public void begin(BufferBuilder builder, TextureManager textureManager) {
         RenderSystem.depthMask(false);
         RenderSystem.setShader(GameRenderer::getParticleShader);
         RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         builder.begin(Mode.QUADS, DefaultVertexFormat.PARTICLE);
      }

      public void end(Tesselator tesselator) {
         tesselator.end();
         RenderSystem.defaultBlendFunc();
         RenderSystem.depthMask(true);
      }

      @Override
      public String toString() {
         return "ADDITIVE_ENERGY";
      }
   };
   private final SpriteSet sprites;
   private float r0 = 1.0F;
   private float g0 = 1.0F;
   private float b0 = 1.0F;
   private float r1 = 1.0F;
   private float g1 = 1.0F;
   private float b1 = 1.0F;
   private float size0 = 0.2F;
   private float size1 = 0.0F;
   private float peakAlpha = 1.0F;
   private final float spin;
   private boolean attract;
   private double attractX;
   private double attractY;
   private double attractZ;
   private float swirl;

   public EnergyParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, int lifetime) {
      super(level, x, y, z);
      this.xd = vx;
      this.yd = vy;
      this.zd = vz;
      this.sprites = sprites;
      this.lifetime = Math.max(1, lifetime);
      this.hasPhysics = false;
      this.gravity = 0.0F;
      this.friction = 0.9F;
      this.quadSize = this.size0;
      this.alpha = 0.0F;
      this.roll = this.oRoll = this.random.nextFloat() * (float) (Math.PI * 2);
      this.spin = (this.random.nextFloat() - 0.5F) * 0.25F;
      this.setSpriteFromAge(sprites);
   }

   public EnergyParticle colors(int from, int to) {
      this.r0 = FxDraw.red(from);
      this.g0 = FxDraw.green(from);
      this.b0 = FxDraw.blue(from);
      this.r1 = FxDraw.red(to);
      this.g1 = FxDraw.green(to);
      this.b1 = FxDraw.blue(to);
      this.setColor(this.r0, this.g0, this.b0);
      return this;
   }

   public EnergyParticle size(float from, float to) {
      this.size0 = from;
      this.size1 = to;
      this.quadSize = from;
      return this;
   }

   public EnergyParticle peakAlpha(float alpha) {
      this.peakAlpha = alpha;
      return this;
   }

   public EnergyParticle gravity(float gravity) {
      this.gravity = gravity;
      return this;
   }

   public EnergyParticle drag(float friction) {
      this.friction = friction;
      return this;
   }

   public EnergyParticle attract(double x, double y, double z, float swirl) {
      this.attract = true;
      this.attractX = x;
      this.attractY = y;
      this.attractZ = z;
      this.swirl = swirl;
      this.friction = 0.86F;
      return this;
   }

   public void tick() {
      if (this.attract && !this.removed) {
         double dx = this.attractX - this.x;
         double dy = this.attractY - this.y;
         double dz = this.attractZ - this.z;
         double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (d < 0.18) {
            this.remove();
            return;
         }

         double pull = 0.035 + 0.04 / (d + 0.3);
         this.xd = this.xd + (dx / d * pull - dz / d * (double)this.swirl * 0.06);
         this.yd += dy / d * pull;
         this.zd = this.zd + dz / d * pull + dx / d * (double)this.swirl * 0.06;
      }

      super.tick();
      if (!this.removed) {
         float t = Math.min(1.0F, (float)this.age / (float)this.lifetime);
         float fadeIn = Math.min(1.0F, t / 0.12F);
         float fadeOut = t > 0.55F ? 1.0F - (t - 0.55F) / 0.45F : 1.0F;
         this.alpha = this.peakAlpha * fadeIn * fadeOut;
         this.quadSize = Mth.lerp(t, this.size0, this.size1);
         this.rCol = Mth.lerp(t, this.r0, this.r1);
         this.gCol = Mth.lerp(t, this.g0, this.g1);
         this.bCol = Mth.lerp(t, this.b0, this.b1);
         this.oRoll = this.roll;
         this.roll = this.roll + this.spin;
         this.setSpriteFromAge(this.sprites);
      }
   }

   public ParticleRenderType getRenderType() {
      return ADDITIVE;
   }

   protected int getLightColor(float partialTick) {
      return 15728880;
   }
}
