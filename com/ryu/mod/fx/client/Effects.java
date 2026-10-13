package com.ryu.mod.fx.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class Effects {
   static final int PASS_DARK = 0;
   static final int PASS_COLOR = 1;
   static final int PASS_GLOW = 2;
   static final int PASS_BEAM = 3;
   static final int PASS_DARK_OVERLAY = 4;
   static final int PASS_GLOW_OVERLAY = 5;
   static final int PASS_BEAM_OVERLAY = 6;
   private static final int WHITE = 16777215;
   private static final int BLUE = 4033535;
   private static final int RED = 16722731;
   private static int nextId;

   private Effects() {
   }

   static float easeOut(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
   }

   static float smooth(float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   static Vec3 randomDir(RandomSource rnd) {
      double u = rnd.nextDouble() * 2.0 - 1.0;
      double theta = rnd.nextDouble() * Math.PI * 2.0;
      double s = Math.sqrt(1.0 - u * u);
      return new Vec3(s * Math.cos(theta), u, s * Math.sin(theta));
   }

   static final class Arcs extends Effects.Effect {
      private final float length;
      private final int color;
      private final int count;
      private final float[] arcPoints = new float[33];

      Arcs(double x, double y, double z, float length, int color, int count, int duration) {
         super(x, y, z, duration);
         this.length = length;
         this.color = color;
         this.count = Math.max(1, count);
      }

      @Override
      void render(int pass, VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float partial, float time) {
         float a = 1.0F - this.progress(partial);
         float px = (float)(this.x - camX);
         float py = (float)(this.y - camY);
         float pz = (float)(this.z - camZ);
         if (pass == 3) {
            long frame = ((long)this.age + (long)(partial * 2.0F)) / 2L;

            for (int i = 0; i < this.count; i++) {
               long seed = (long)this.id * 104729L + frame * 31L + (long)i * 7L;
               float dx = FxDraw.hash(seed) - 0.5F;
               float dy = FxDraw.hash(seed + 1L) - 0.5F;
               float dz = FxDraw.hash(seed + 2L) - 0.5F;
               float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F;
               float len = this.length * (0.6F + 0.4F * FxDraw.hash(seed + 3L)) / dl;
               int n = FxDraw.arc(seed, px, py, pz, px + dx * len, py + dy * len, pz + dz * len, 10, this.length * 0.25F, this.arcPoints);
               FxDraw.polyline(vc, m, this.arcPoints, n, 0.14F, this.color, 0.85F * a);
               FxDraw.polyline(vc, m, this.arcPoints, n, 0.045F, 16777215, a);
            }
         } else if (pass == 2) {
            FxDraw.billboard(vc, m, px, py, pz, this.length * 1.4F, this.color, 0.5F * a, 0.0F);
         }
      }
   }

   static final class Beam extends Effects.Effect {
      private final double bx;
      private final double by;
      private final double bz;
      private final float width;
      private final int color;
      private final int core;
      private final float[] arcPoints = new float[99];

      Beam(double x, double y, double z, double bx, double by, double bz, float width, int color, int core, int duration) {
         super(x, y, z, duration);
         this.bx = bx;
         this.by = by;
         this.bz = bz;
         this.width = width;
         this.color = color;
         this.core = core;
      }

      @Override
      boolean tick(ClientLevel level, RandomSource rnd) {
         if (this.age < 5) {
            double dx = this.bx - this.x;
            double dy = this.by - this.y;
            double dz = this.bz - this.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int count = ClientFx.scaled(Math.min(48, 4 + (int)(len * (double)this.width * 0.5)));
            double tMin = len < 0.001 ? 0.0 : Math.min(0.5, 3.0 / len);

            for (int i = 0; i < count; i++) {
               double t = tMin + rnd.nextDouble() * (1.0 - tMin);
               Vec3 d = Effects.randomDir(rnd);
               double off = (double)this.width * 0.6 * rnd.nextDouble();
               EnergyParticle p = ClientFx.spawn(
                  rnd.nextInt(3) == 0,
                  this.x + dx * t + d.x * off,
                  this.y + dy * t + d.y * off,
                  this.z + dz * t + d.z * off,
                  d.x * 0.06 * (double)this.width,
                  d.y * 0.06 * (double)this.width,
                  d.z * 0.06 * (double)this.width,
                  12 + rnd.nextInt(10)
               );
               if (p != null) {
                  p.colors(this.core, this.color).size(0.2F + this.width * 0.08F, 0.0F).drag(0.9F);
               }
            }
         }

         return super.tick(level, rnd);
      }

      @Override
      void render(int pass, VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float partial, float time) {
         float t = this.progress(partial);
         float fadeIn = Mth.clamp(((float)this.age + partial) / 2.0F, 0.0F, 1.0F);
         float fadeOut = t < 0.3F ? 1.0F : (float)Math.pow((double)(1.0F - (t - 0.3F) / 0.7F), 1.3);
         float a = fadeIn * fadeOut;
         if (!(a <= 0.003F)) {
            float w = this.width * (0.55F + 0.45F * (1.0F - t)) * (1.0F + 0.06F * Mth.sin(time * 2.3F));
            float ax = (float)(this.x - camX);
            float ay = (float)(this.y - camY);
            float az = (float)(this.z - camZ);
            float ex = (float)(this.bx - camX);
            float ey = (float)(this.by - camY);
            float ez = (float)(this.bz - camZ);
            if (pass == 3) {
               float len = Mth.sqrt((ex - ax) * (ex - ax) + (ey - ay) * (ey - ay) + (ez - az) * (ez - az));
               float startDist = Mth.sqrt(ax * ax + ay * ay + az * az);
               float haloOffset = Math.max(w * 1.5F, 3.0F - startDist);
               float k = len < 0.001F ? 0.0F : Math.min(0.3F, haloOffset / len);
               float hx = ax + (ex - ax) * k;
               float hy = ay + (ey - ay) * k;
               float hz = az + (ez - az) * k;
               float k2 = k * 0.5F;
               float mx = ax + (ex - ax) * k2;
               float my = ay + (ey - ay) * k2;
               float mz = az + (ez - az) * k2;
               FxDraw.beam(vc, m, hx, hy, hz, ex, ey, ez, w * 2.8F, this.color, 0.22F * a);
               FxDraw.beam(vc, m, mx, my, mz, ex, ey, ez, w * 1.3F, this.color, 0.65F * a);
               FxDraw.beam(vc, m, ax, ay, az, ex, ey, ez, w * 0.45F, this.core, a);
               int segments = Mth.clamp((int)(len / 1.5F), 4, 32);
               long frame = ((long)this.age + (long)(partial * 2.0F)) / 2L;

               for (int i = 0; i < 2; i++) {
                  int n = FxDraw.arc((long)this.id * 31L + frame * 977L + (long)i, ax, ay, az, ex, ey, ez, segments, w * 0.9F, this.arcPoints);
                  FxDraw.polyline(vc, m, this.arcPoints, n, Math.max(0.05F, w * 0.12F), this.color, 0.8F * a);
                  FxDraw.polyline(vc, m, this.arcPoints, n, Math.max(0.02F, w * 0.04F), 16777215, a);
               }
            } else if (pass == 2) {
               float len = Mth.sqrt((ex - ax) * (ex - ax) + (ey - ay) * (ey - ay) + (ez - az) * (ez - az));
               int discs = Mth.clamp((int)(len / 2.5F), 1, 32);

               for (int i = 1; i <= discs; i++) {
                  float f = (float)i / (float)(discs + 1);
                  float dx = ax + (ex - ax) * f;
                  float dy = ay + (ey - ay) * f;
                  float dz = az + (ez - az) * f;
                  FxDraw.billboard(vc, m, dx, dy, dz, w * 2.4F, this.color, 0.3F * a, time * 0.03F + (float)i);
                  FxDraw.billboard(vc, m, dx, dy, dz, w * 0.9F, this.core, 0.35F * a, 0.0F);
               }

               float muzzleFade = Mth.clamp(Mth.sqrt(ax * ax + ay * ay + az * az) / 3.0F, 0.1F, 1.0F);
               FxDraw.billboard(vc, m, ax, ay, az, w * 5.0F, this.color, 0.6F * a * muzzleFade, time * 0.05F);
               FxDraw.billboard(vc, m, ex, ey, ez, w * 7.0F, this.color, 0.7F * a, -time * 0.04F);
               FxDraw.billboard(vc, m, ex, ey, ez, w * 2.5F, this.core, 0.95F * a, 0.0F);
            }
         }
      }

      @Override
      double distanceSqTo(Vec3 cam) {
         double dx = this.bx - this.x;
         double dy = this.by - this.y;
         double dz = this.bz - this.z;
         double lenSq = dx * dx + dy * dy + dz * dz;
         double t = lenSq < 1.0E-6 ? 0.0 : Mth.clamp(((cam.x - this.x) * dx + (cam.y - this.y) * dy + (cam.z - this.z) * dz) / lenSq, 0.0, 1.0);
         double px = this.x + dx * t - cam.x;
         double py = this.y + dy * t - cam.y;
         double pz = this.z + dz * t - cam.z;
         return px * px + py * py + pz * pz;
      }
   }

   static final class Burst extends Effects.Effect {
      private final float speed;
      private final int color;
      private final int fadeTo;
      private final int count;
      private final byte mode;

      Burst(double x, double y, double z, float speed, int color, int fadeTo, int count, byte mode) {
         super(x, y, z, 1);
         this.speed = speed;
         this.color = color;
         this.fadeTo = fadeTo;
         this.count = count;
         this.mode = mode;
      }

      @Override
      boolean tick(ClientLevel level, RandomSource rnd) {
         for (int i = ClientFx.scaled(this.count); i > 0; i--) {
            Vec3 d = Effects.randomDir(rnd);
            switch (this.mode) {
               case 1:
                  double sx = (double)this.speed * (0.1 + rnd.nextDouble() * 0.3);
                  EnergyParticle pxx = ClientFx.spawn(
                     false, this.x + d.x * 0.5, this.y + d.y * 0.5, this.z + d.z * 0.5, d.x * sx, Math.abs(d.y) * sx + 0.02, d.z * sx, 40 + rnd.nextInt(40)
                  );
                  if (pxx != null) {
                     pxx.colors(this.color, this.fadeTo).size(0.14F, 0.0F).gravity(-0.015F).drag(0.95F);
                  }
                  break;
               case 2:
                  EnergyParticle p = ClientFx.spawn(
                     false,
                     this.x + d.x * 0.6,
                     this.y + d.y * 0.4,
                     this.z + d.z * 0.6,
                     0.0,
                     (double)this.speed * (0.4 + rnd.nextDouble() * 0.6),
                     0.0,
                     25 + rnd.nextInt(15)
                  );
                  if (p != null) {
                     p.colors(this.color, this.fadeTo).size(0.2F, 0.02F).drag(0.94F);
                  }
                  break;
               default:
                  double s = (double)this.speed * (0.4 + rnd.nextDouble() * 0.6);
                  EnergyParticle px = ClientFx.spawn(rnd.nextBoolean(), this.x, this.y, this.z, d.x * s, d.y * s, d.z * s, 10 + rnd.nextInt(9));
                  if (px != null) {
                     px.colors(this.color, this.fadeTo).size(0.32F, 0.02F).drag(0.87F);
                  }
            }
         }

         return false;
      }
   }

   static final class Domain extends Effects.Effect {
      private static final int STARS = 480;
      private static final int NEBULAE = 18;
      private static final int CIRCLES = 6;
      private static final int LINKS = 40;
      private final float radius;
      private final byte mode;
      private final int color;
      private final float[] stars = new float[1920];
      private final float[] nebulae = new float[72];
      private final float[] circles = new float[18];
      private final int[] palette;

      Domain(double x, double y, double z, float radius, int duration, byte mode, int color) {
         super(x, y, z, duration);
         this.radius = radius;
         this.mode = mode;
         this.color = color;
         this.palette = mode == 1 ? new int[]{58879, 30634, 4259792} : new int[]{6958079, 2845695, 12602367};
         RandomSource rnd = RandomSource.create((long)this.id * 31L + 7L);

         for (int i = 0; i < 480; i++) {
            Vec3 d = Effects.randomDir(rnd);
            this.stars[i * 4] = (float)d.x;
            this.stars[i * 4 + 1] = (float)d.y;
            this.stars[i * 4 + 2] = (float)d.z;
            this.stars[i * 4 + 3] = 0.15F + rnd.nextFloat() * rnd.nextFloat() * 0.6F;
         }

         for (int i = 0; i < 18; i++) {
            Vec3 d = Effects.randomDir(rnd);
            this.nebulae[i * 4] = (float)d.x;
            this.nebulae[i * 4 + 1] = (float)d.y;
            this.nebulae[i * 4 + 2] = (float)d.z;
            this.nebulae[i * 4 + 3] = 0.5F + rnd.nextFloat();
         }

         for (int i = 0; i < 6; i++) {
            Vec3 d = Effects.randomDir(rnd);
            this.circles[i * 3] = (float)d.x;
            this.circles[i * 3 + 1] = (float)d.y;
            this.circles[i * 3 + 2] = (float)d.z;
         }
      }

      private float fade(float partial) {
         float t = (float)this.age + partial;
         return Math.min(Effects.smooth(t / 20.0F), Effects.smooth(((float)this.duration - t) / 20.0F));
      }

      private float currentRadius(float partial) {
         return this.radius * (0.35F + 0.65F * Effects.easeOut(((float)this.age + partial) / 18.0F));
      }

      @Override
      boolean tick(ClientLevel level, RandomSource rnd) {
         Minecraft mc = Minecraft.getInstance();
         Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
         float r = this.currentRadius(1.0F);
         if (this.distanceSqTo(cam) < (double)(r * r)) {
            ScreenFx.inDomain(this.fade(1.0F), this.mode == 1 ? 6176 : 393242);

            for (int i = ClientFx.scaled(3); i > 0; i--) {
               Vec3 d = Effects.randomDir(rnd);
               double dist = 2.0 + rnd.nextDouble() * 8.0;
               EnergyParticle p = ClientFx.spawn(
                  false,
                  cam.x + d.x * dist,
                  cam.y + d.y * dist,
                  cam.z + d.z * dist,
                  rnd.nextGaussian() * 0.01,
                  0.01,
                  rnd.nextGaussian() * 0.01,
                  40 + rnd.nextInt(30)
               );
               if (p != null) {
                  p.colors(16777215, this.palette[i % this.palette.length]).size(0.08F, 0.02F).peakAlpha(0.8F).drag(0.98F);
               }
            }
         }

         return super.tick(level, rnd);
      }

      @Override
      void render(int pass, VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float partial, float time) {
         float f = this.fade(partial);
         if (!(f <= 0.003F)) {
            float r = this.currentRadius(partial);
            float px = (float)(this.x - camX);
            float py = (float)(this.y - camY);
            float pz = (float)(this.z - camZ);
            boolean inside = px * px + py * py + pz * pz < r * r;
            int darkPass = inside ? 4 : 0;
            int glowPass = inside ? 5 : 2;
            int beamPass = inside ? 6 : 3;
            int shell = this.mode == 1 ? 5148 : 262415;
            if (pass == darkPass) {
               FxDraw.sphere(vc, m, px, py, pz, r, shell, (inside ? 0.6F : 0.9F) * f, 24, 40);
            } else if (pass == glowPass) {
               for (int i = 0; i < 18; i++) {
                  int j = i * 4;
                  float d = r * 0.94F;
                  FxDraw.billboard(
                     vc,
                     m,
                     px + this.nebulae[j] * d,
                     py + this.nebulae[j + 1] * d,
                     pz + this.nebulae[j + 2] * d,
                     r * 0.7F * this.nebulae[j + 3],
                     this.palette[i % this.palette.length],
                     0.3F * f,
                     time * 0.003F * (float)(i + 1)
                  );
               }

               for (int i = 0; i < 480; i++) {
                  int j = i * 4;
                  float d = r * 0.97F;
                  float twinkle = 0.55F + 0.45F * Mth.sin(time * (0.08F + (float)(i % 7) * 0.03F) + (float)i);
                  FxDraw.billboard(
                     vc,
                     m,
                     px + this.stars[j] * d,
                     py + this.stars[j + 1] * d,
                     pz + this.stars[j + 2] * d,
                     this.stars[j + 3] * (r / 7.0F),
                     i % 4 == 0 ? this.color : 16777215,
                     f * twinkle,
                     0.0F
                  );
               }

               if (!inside) {
                  FxDraw.billboard(vc, m, px, py, pz, 4.0F, this.color, 0.35F * f, 0.0F);
               }
            } else if (pass == beamPass) {
               float spin = time * 0.004F;

               for (int i = 0; i < 6; i++) {
                  int j = i * 3;
                  float flicker = 0.5F + 0.5F * Mth.sin(time * 1.7F + (float)i * 2.1F);
                  if (FxDraw.hash((long)(this.age / 2) * 31L + (long)i) < 0.12F) {
                     flicker *= 0.2F;
                  }

                  float cs = Mth.cos(spin * (float)(i + 1));
                  float sn = Mth.sin(spin * (float)(i + 1));
                  float nx = this.circles[j] * cs - this.circles[j + 2] * sn;
                  float nz = this.circles[j] * sn + this.circles[j + 2] * cs;
                  FxDraw.ring(
                     vc, m, px, py, pz, nx, this.circles[j + 1], nz, r * 0.9F, 0.35F, i % 2 == 0 ? 16777215 : this.color, (0.3F + 0.5F * flicker) * f, 72
                  );
               }

               for (int i = 0; i < 40; i++) {
                  int a = i * 7 % 480 * 4;
                  int b = (i * 7 + 1) % 480 * 4;
                  float d = r * 0.97F;
                  float flicker = 0.5F + 0.5F * Mth.sin(time * 0.6F + (float)i);
                  FxDraw.beam(
                     vc,
                     m,
                     px + this.stars[a] * d,
                     py + this.stars[a + 1] * d,
                     pz + this.stars[a + 2] * d,
                     px + this.stars[b] * d,
                     py + this.stars[b + 1] * d,
                     pz + this.stars[b + 2] * d,
                     0.12F,
                     this.color,
                     0.55F * f * flicker
                  );
               }
            }
         }
      }

      @Override
      double cullDistance() {
         return 160.0 + (double)this.radius;
      }
   }

   abstract static class Effect {
      final int id = Effects.nextId++;
      final double x;
      final double y;
      final double z;
      int duration;
      int age;

      Effect(double x, double y, double z, int duration) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.duration = Math.max(1, duration);
      }

      boolean tick(ClientLevel level, RandomSource rnd) {
         return ++this.age < this.duration;
      }

      void render(int pass, VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float partial, float time) {
      }

      double cullDistance() {
         return 160.0;
      }

      double distanceSqTo(Vec3 cam) {
         double dx = this.x - cam.x;
         double dy = this.y - cam.y;
         double dz = this.z - cam.z;
         return dx * dx + dy * dy + dz * dz;
      }

      float progress(float partial) {
         return Mth.clamp(((float)this.age + partial) / (float)this.duration, 0.0F, 1.0F);
      }
   }

   static final class Orb extends Effects.Effect {
      private final float radius;
      private final int color;
      private final int core;
      private final byte mode;
      private final int followId;
      private final float[] arcPoints = new float[27];
      private double cx;
      private double cy;
      private double cz;

      Orb(double x, double y, double z, float radius, int color, int core, int duration, byte mode, int followId) {
         super(x, y, z, duration);
         this.radius = radius;
         this.color = color;
         this.core = core;
         this.mode = mode;
         this.followId = followId;
         this.cx = x;
         this.cy = y;
         this.cz = z;
      }

      private void updateCenter(ClientLevel level, float partial) {
         if (this.followId >= 0 && level != null) {
            Entity entity = level.getEntity(this.followId);
            if (entity != null) {
               Vec3 eye = entity.getEyePosition(partial);
               Vec3 look = entity.getViewVector(partial);
               this.cx = eye.x + look.x * 2.4;
               this.cy = eye.y + look.y * 2.4 - 0.2;
               this.cz = eye.z + look.z * 2.4;
               return;
            }
         }

         this.cx = this.x;
         this.cy = this.y;
         this.cz = this.z;
      }

      private float charge(float partial) {
         return this.mode == 2 ? Effects.easeOut(this.progress(partial)) : 1.0F;
      }

      private float scale(float partial, float time) {
         float t = (float)this.age + partial;
         float grow = this.mode == 2 ? 0.25F + 0.75F * this.charge(partial) : Effects.easeOut(t / 5.0F);
         float end = Mth.clamp(((float)this.duration - t) / 4.0F, 0.0F, 1.0F);
         float pulse = 1.0F + (this.mode == 2 ? 0.12F : 0.08F) * Mth.sin(time * 0.9F);
         return this.radius * grow * end * pulse;
      }

      @Override
      boolean tick(ClientLevel level, RandomSource rnd) {
         this.updateCenter(level, 1.0F);
         float c = this.charge(1.0F);
         switch (this.mode) {
            case 0:
               for (int ixx = ClientFx.scaled(7); ixx > 0; ixx--) {
                  Vec3 d = Effects.randomDir(rnd);
                  double dist = (double)this.radius * (3.5 + rnd.nextDouble() * 3.0);
                  EnergyParticle p = ClientFx.spawn(false, this.cx + d.x * dist, this.cy + d.y * dist, this.cz + d.z * dist, -d.z * 0.05, 0.0, d.x * 0.05, 40);
                  if (p != null) {
                     p.colors(this.color, 16777215).size(0.24F, 0.04F).attract(this.cx, this.cy, this.cz, 1.0F);
                  }
               }

               this.sparkAround(rnd, 2, this.color);
               break;
            case 1:
               for (int ix = ClientFx.scaled(10); ix > 0; ix--) {
                  Vec3 d = Effects.randomDir(rnd);
                  double speed = 0.3 + rnd.nextDouble() * 0.5;
                  EnergyParticle p = ClientFx.spawn(rnd.nextBoolean(), this.cx, this.cy, this.cz, d.x * speed, d.y * speed, d.z * speed, 10 + rnd.nextInt(8));
                  if (p != null) {
                     p.colors(this.core, this.color).size(0.28F, 0.02F).drag(0.86F);
                  }
               }
               break;
            case 2:
               float angle = (float)((long)this.age + level.getGameTime() % 1000L) * 0.2F;
               double sx = (double)Mth.cos(angle);
               double sz = (double)Mth.sin(angle);
               int count = ClientFx.scaled(3 + (int)(10.0F * c));

               for (int i = 0; i < count; i++) {
                  boolean blue = (i & 1) == 0;
                  double side = blue ? 1.0 : -1.0;
                  double dist = (double)this.radius * (3.0 + rnd.nextDouble() * 2.0);
                  EnergyParticle p = ClientFx.spawn(
                     false,
                     this.cx + sx * side * dist + rnd.nextGaussian() * 0.3,
                     this.cy + rnd.nextGaussian() * 0.4,
                     this.cz + sz * side * dist + rnd.nextGaussian() * 0.3,
                     0.0,
                     0.0,
                     0.0,
                     40
                  );
                  if (p != null) {
                     p.colors(blue ? 4033535 : 16722731, 16777215).size(0.26F, 0.05F).attract(this.cx, this.cy, this.cz, 1.4F);
                  }
               }

               this.sparkAround(rnd, 1 + (int)(4.0F * c), 14264575);
         }

         return super.tick(level, rnd);
      }

      private void sparkAround(RandomSource rnd, int count, int tint) {
         for (int i = ClientFx.scaled(count); i > 0; i--) {
            Vec3 d = Effects.randomDir(rnd);
            double r = (double)this.radius * (0.9 + rnd.nextDouble() * 0.6);
            EnergyParticle p = ClientFx.spawn(
               true, this.cx + d.x * r, this.cy + d.y * r, this.cz + d.z * r, d.x * 0.04, d.y * 0.04, d.z * 0.04, 6 + rnd.nextInt(5)
            );
            if (p != null) {
               p.colors(16777215, tint).size(0.35F, 0.05F);
            }
         }
      }

      @Override
      void render(int pass, VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float partial, float time) {
         this.updateCenter(Minecraft.getInstance().level, partial);
         float r = this.scale(partial, time);
         if (!(r <= 0.01F)) {
            float px = (float)(this.cx - camX);
            float py = (float)(this.cy - camY);
            float pz = (float)(this.cz - camZ);
            boolean held = this.followId >= 0;
            float glow = this.mode == 2 ? 0.45F + 0.55F * this.charge(partial) : 1.0F;
            float halo = held ? 0.6F : 1.0F;
            switch (pass) {
               case 1:
                  FxDraw.sphere(vc, m, px, py, pz, r * 0.85F, this.color, 0.22F * glow, 10, 16);
                  FxDraw.sphere(vc, m, px, py, pz, r * 0.6F, this.color, 0.55F * glow, 10, 16);
                  FxDraw.sphere(vc, m, px, py, pz, r * 0.24F, this.core, 0.9F, 8, 12);
                  break;
               case 2:
                  FxDraw.billboard(vc, m, px, py, pz, r * 7.5F * halo, this.color, 0.24F * glow, 0.0F);
                  FxDraw.billboard(vc, m, px, py, pz, r * 3.6F * halo, this.color, 0.65F * glow, time * 0.02F);
                  FxDraw.billboard(vc, m, px, py, pz, r * 1.3F, this.core, 0.8F, 0.0F);
                  break;
               case 3:
                  int arcs = this.mode == 2 ? 3 + (int)(4.0F * glow) : 4;
                  long frame = ((long)this.age + (long)(partial * 2.0F)) / 2L;

                  for (int i = 0; i < arcs; i++) {
                     long seed = (long)this.id * 7919L + frame * 131L + (long)i * 17L;
                     float dx = FxDraw.hash(seed) - 0.5F;
                     float dy = FxDraw.hash(seed + 1L) - 0.5F;
                     float dz = FxDraw.hash(seed + 2L) - 0.5F;
                     float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-4F;
                     float len = r * (1.5F + FxDraw.hash(seed + 3L) * 1.2F) / dl;
                     int n = FxDraw.arc(seed, px, py, pz, px + dx * len, py + dy * len, pz + dz * len, 8, r * 0.35F, this.arcPoints);
                     FxDraw.polyline(vc, m, this.arcPoints, n, 0.09F * Math.max(1.0F, r), this.color, 0.75F * glow);
                     FxDraw.polyline(vc, m, this.arcPoints, n, 0.03F * Math.max(1.0F, r), 16777215, 0.9F * glow);
                  }

                  if (this.mode == 2) {
                     float a = time * 0.15F;
                     FxDraw.ring(vc, m, px, py, pz, Mth.sin(a), 1.0F, Mth.cos(a), r * 1.6F, r * 0.18F, 4033535, 0.6F * glow, 40);
                     FxDraw.ring(vc, m, px, py, pz, Mth.cos(a), -0.6F, Mth.sin(a), r * 1.8F, r * 0.18F, 16722731, 0.6F * glow, 40);
                  }
            }
         }
      }

      @Override
      double distanceSqTo(Vec3 cam) {
         double dx = this.cx - cam.x;
         double dy = this.cy - cam.y;
         double dz = this.cz - cam.z;
         return dx * dx + dy * dy + dz * dz;
      }
   }

   static final class Shimmer extends Effects.Effect {
      private final float nx;
      private final float ny;
      private final float nz;
      private final float size;
      final int color;
      final byte mode;
      final int followId;
      private double cx;
      private double cy;
      private double cz;
      private boolean hiddenFirstPerson;

      Shimmer(double x, double y, double z, float nx, float ny, float nz, float size, int color, int duration, byte mode, int followId) {
         super(x, y, z, duration);
         this.nx = nx;
         this.ny = ny;
         this.nz = nz;
         this.size = size;
         this.color = color;
         this.mode = mode;
         this.followId = followId;
         this.cx = x;
         this.cy = y;
         this.cz = z;
      }

      void refresh(int newDuration) {
         this.duration = this.age + newDuration;
      }

      private void follow(float partial) {
         Minecraft mc = Minecraft.getInstance();
         this.hiddenFirstPerson = false;
         if (this.mode == 0 && mc.level != null) {
            Entity entity = mc.level.getEntity(this.followId);
            if (entity != null) {
               Vec3 pos = entity.getPosition(partial);
               this.cx = pos.x;
               this.cy = pos.y + (double)entity.getBbHeight() * 0.5;
               this.cz = pos.z;
               this.hiddenFirstPerson = entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            }
         }
      }

      @Override
      void render(int pass, VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float partial, float time) {
         this.follow(partial);
         if (!this.hiddenFirstPerson) {
            float px = (float)(this.cx - camX);
            float py = (float)(this.cy - camY);
            float pz = (float)(this.cz - camZ);
            if (this.mode == 0) {
               float t = (float)this.age + partial;
               float f = Math.min(Effects.smooth(t / 6.0F), Effects.smooth(((float)this.duration - t) / 6.0F));
               if (pass == 1) {
                  FxDraw.sphere(vc, m, px, py, pz, this.size, this.color, 0.035F * f, 12, 20);
               } else if (pass == 3) {
                  for (int i = 0; i < 3; i++) {
                     float a = time * (0.05F + (float)i * 0.02F) + (float)i * 2.1F;
                     float pulse = 0.6F + 0.4F * Mth.sin(time * 0.3F + (float)i);
                     FxDraw.ring(
                        vc, m, px, py, pz, Mth.sin(a), 1.0F, Mth.cos(a), this.size * (1.0F + 0.03F * (float)i), 0.06F, this.color, 0.3F * f * pulse, 40
                     );
                  }
               }
            } else {
               float t = this.progress(partial);
               if (pass == 3) {
                  for (int i = 0; i < 3; i++) {
                     float ti = Mth.clamp(t * 1.3F - (float)i * 0.15F, 0.0F, 1.0F);
                     if (!(ti <= 0.0F)) {
                        float rad = this.size * Effects.easeOut(ti);
                        FxDraw.ring(vc, m, px, py, pz, this.nx, this.ny, this.nz, rad, 0.12F, this.color, 0.8F * (1.0F - ti), 32);
                        FxDraw.ring(vc, m, px, py, pz, this.nx, this.ny, this.nz, rad * 0.9F, 0.04F, 16777215, 0.6F * (1.0F - ti), 32);
                     }
                  }
               } else if (pass == 2) {
                  FxDraw.billboard(vc, m, px, py, pz, this.size * 1.4F, this.color, 0.5F * (1.0F - t), 0.0F);
               }
            }
         }
      }

      @Override
      double distanceSqTo(Vec3 cam) {
         double dx = this.cx - cam.x;
         double dy = this.cy - cam.y;
         double dz = this.cz - cam.z;
         return dx * dx + dy * dy + dz * dz;
      }
   }

   static final class Shockwave extends Effects.Effect {
      private final float nx;
      private final float ny;
      private final float nz;
      private final float radius;
      private final int color;

      Shockwave(double x, double y, double z, float nx, float ny, float nz, float radius, int color, int duration) {
         super(x, y, z, duration);
         this.nx = nx;
         this.ny = ny;
         this.nz = nz;
         this.radius = radius;
         this.color = color;
      }

      @Override
      void render(int pass, VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float partial, float time) {
         float t = this.progress(partial);
         float a = (float)Math.pow((double)(1.0F - t), 1.6);
         float rad = this.radius * Effects.easeOut(t);
         float px = (float)(this.x - camX);
         float py = (float)(this.y - camY);
         float pz = (float)(this.z - camZ);
         switch (pass) {
            case 1:
               FxDraw.sphere(vc, m, px, py, pz, rad * 0.92F, this.color, 0.05F * a, 10, 20);
               break;
            case 2:
               float flashT = ((float)this.age + partial) / 5.0F;
               if (flashT < 1.0F) {
                  FxDraw.billboard(vc, m, px, py, pz, this.radius * 1.3F * (0.5F + flashT), 16777215, 0.85F * (1.0F - flashT), 0.0F);
                  FxDraw.billboard(vc, m, px, py, pz, this.radius * 2.2F, this.color, 0.5F * (1.0F - flashT), 0.0F);
               }
               break;
            case 3:
               float thick = 0.35F + this.radius * 0.14F * (1.0F - t);
               FxDraw.ring(vc, m, px, py, pz, this.nx, this.ny, this.nz, rad, thick, this.color, 0.9F * a, 56);
               FxDraw.ring(vc, m, px, py, pz, this.nx, this.ny, this.nz, rad * 0.86F, thick * 0.45F, 16777215, 0.55F * a, 56);
               float t2 = Mth.clamp(((float)this.age + partial - 3.0F) / Math.max(1.0F, (float)this.duration - 3.0F), 0.0F, 1.0F);
               if (t2 > 0.0F) {
                  FxDraw.ring(
                     vc, m, px, py, pz, this.nx, this.ny, this.nz, this.radius * 0.7F * Effects.easeOut(t2), thick * 0.6F, this.color, 0.5F * (1.0F - t2), 48
                  );
               }
         }
      }

      @Override
      double cullDistance() {
         return 160.0 + (double)this.radius;
      }
   }

   static final class Trail extends Effects.Effect {
      private final double bx;
      private final double by;
      private final double bz;
      private final int color;

      Trail(double x, double y, double z, double bx, double by, double bz, int color) {
         super(x, y, z, 1);
         this.bx = bx;
         this.by = by;
         this.bz = bz;
         this.color = color;
      }

      @Override
      boolean tick(ClientLevel level, RandomSource rnd) {
         double dx = this.bx - this.x;
         double dy = this.by - this.y;
         double dz = this.bz - this.z;
         double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (len < 0.01) {
            return false;
         } else {
            int count = ClientFx.scaled(Mth.clamp((int)(len * 3.0), 4, 60));

            for (int i = 0; i < count; i++) {
               double t = rnd.nextDouble();
               EnergyParticle p = ClientFx.spawn(
                  rnd.nextInt(4) == 0,
                  this.x + dx * t + rnd.nextGaussian() * 0.15,
                  this.y + dy * t + rnd.nextGaussian() * 0.15,
                  this.z + dz * t + rnd.nextGaussian() * 0.15,
                  dx / len * 0.18,
                  dy / len * 0.18,
                  dz / len * 0.18,
                  10 + rnd.nextInt(5)
               );
               if (p != null) {
                  p.colors(this.color, 16777215).size(0.18F, 0.02F).drag(0.92F);
               }
            }

            return false;
         }
      }
   }
}
