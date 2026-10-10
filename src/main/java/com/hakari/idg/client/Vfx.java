package com.hakari.idg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Vfx {
   public static final List<Vfx.Effect> EFFECTS = new ArrayList<>();

   public static long now() {
      return Util.getMillis();
   }

   public static void add(Vfx.Effect e) {
      EFFECTS.add(e);
   }

   public static void prune() {
      long t = now();
      EFFECTS.removeIf(e -> e.dead(t));
   }

   public static void clear() {
      EFFECTS.clear();
   }

   public static void renderSolid(Vfx.Ctx c) {
      for (Vfx.Effect e : EFFECTS) {
         if (!e.dead(c.t) && c.t >= e.start) {
            e.solid(c);
         }
      }
   }

   public static void renderGlow(Vfx.Ctx c) {
      for (Vfx.Effect e : EFFECTS) {
         if (!e.dead(c.t) && c.t >= e.start) {
            e.glow(c);
         }
      }
   }

   static Vec3 v3(Vector3f f) {
      return new Vec3((double)f.x(), (double)f.y(), (double)f.z());
   }

   static Entity entity(int id) {
      Minecraft mc = Minecraft.getInstance();
      return mc.level != null && id >= 0 ? mc.level.getEntity(id) : null;
   }

   static int groundColor(Vec3 p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return -9741750;
      } else {
         BlockPos bp = BlockPos.containing(p.x, p.y - 0.5, p.z);

         for (int i = 0; i < 4; i++) {
            BlockState s = mc.level.getBlockState(bp);
            if (!s.isAir()) {
               int col = s.getMapColor(mc.level, bp).col;
               return col == 0 ? -9741750 : 0xFF000000 | col;
            }

            bp = bp.below();
         }

         return -9741750;
      }
   }

   private Vfx() {
   }

   public static final class Bolt extends Vfx.Effect {
      final Vec3 a;
      final Vec3 b;
      final int col;

      public Bolt(Vec3 a, Vec3 b, int col, long life) {
         super(life);
         this.a = a;
         this.b = b;
         this.col = col;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float fade = 1.0F - this.p(c.t);
         long frame = c.t / 50L;
         c.at(this.a);
         Vec3 cam = c.camRel(this.a);
         Vec3 d = this.b.subtract(this.a);
         Vec3 prev = Vec3.ZERO;

         for (int i = 1; i <= 8; i++) {
            Vec3 next = d.scale((double)i / 8.0);
            if (i < 8) {
               next = next.add(Draw.randDir(frame * 37L + (long)i).scale(d.length() * 0.08));
            }

            Draw.ribbon(c.vc, c.m(), prev, next, 0.05, 0.05, Draw.alpha(-1, fade), Draw.alpha(this.col, fade), cam);
            prev = next;
         }

         c.pop();
      }
   }

   public static final class Charge extends Vfx.Effect {
      final int entityId;

      public Charge(int entityId, long life) {
         super(life);
         this.entityId = entityId;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         Entity e = Vfx.entity(this.entityId);
         if (e != null) {
            float p = this.p(c.t);
            Vec3 base = e.getPosition(c.partial);
            Vec3 fwd = Draw.forward(e.getYRot(), 0.0F);
            Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
            Vec3 fist = base.add(0.0, 1.25, 0.0).add(right.scale(-0.45)).add(fwd.scale(-0.25));
            c.at(fist);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(fist);
            double time = (double)this.age(c.t) / 1000.0;

            for (int i = 0; i < 22; i++) {
               double ph = (time * 1.9 + (double)i * 0.137) % 1.0;
               double rad = 2.6 * (1.0 - ph);
               double th = (double)i * 2.399 + time * 7.0;
               double y = (Draw.hash((long)i) - 0.5) * 2.4 * (1.0 - ph);
               Vec3 a = new Vec3(Math.cos(th) * rad, y, Math.sin(th) * rad);
               Vec3 b = new Vec3(Math.cos(th - 0.5) * rad * 0.82, y * 0.82, Math.sin(th - 0.5) * rad * 0.82);
               int col = i % 3 == 0 ? -1 : 0xFF39FF7A;
               Draw.ribbon(c.vc, m, a, b, 0.0, 0.05, Draw.alpha(col, 0.0F), Draw.alpha(col, (float)ph * 0.9F), cam);
            }

            long frame = c.t / 40L;

            for (int k = 0; k < 6; k++) {
               Vec3 prev = Vec3.ZERO;
               Vec3 dir = Draw.randDir(frame * 13L + (long)(k * 101));

               for (int s = 1; s <= 5; s++) {
                  Vec3 next = dir.scale((double)s * 0.22 * (0.4 + (double)p)).add(Draw.randDir(frame * 7L + (long)(k * 31) + (long)s).scale(0.12));
                  Draw.ribbon(c.vc, m, prev, next, 0.025, 0.02, 0xFFC8FFD8, 0xAA39FF7A, cam);
                  prev = next;
               }
            }

            Draw.glowOrb(c.vc, m, 0.0, 0.0, 0.0, 0.18 + 0.35 * (double)p, 0xFF39FF7A, 0.8F);
            c.pop();
         }
      }
   }

   public static final class Coins extends Vfx.Effect {
      final Vec3 pos;
      final int n;
      final long seed;

      public Coins(Vec3 pos, int n, long life) {
         super(life);
         this.pos = pos;
         this.n = n;
         this.seed = this.start;
      }

      @Override
      public void solid(Vfx.Ctx c) {
         double t = (double)this.age(c.t) / 1000.0;
         float fade = 1.0F - Math.max(0.0F, (this.p(c.t) - 0.75F) / 0.25F);

         for (int i = 0; i < this.n; i++) {
            long s = this.seed + (long)i * 53L;
            double ang = Draw.hash(s) * Math.PI * 2.0;
            double out = 2.0 + Draw.hash(s + 1L) * 6.0;
            double up = 6.0 + Draw.hash(s + 2L) * 9.0;
            double y = up * t - 9.8 * t * t;
            if (y < -0.9) {
               y = -0.9;
            }

            c.at(this.pos.add(Math.cos(ang) * out * t, 1.0 + y, Math.sin(ang) * out * t));
            c.ps.mulPose(Axis.YP.rotation((float)(t * 12.0 + (double)i)));
            c.ps.mulPose(Axis.XP.rotation((float)(t * 17.0 + (double)i * 0.7)));
            Draw.ring(c.vc, c.m(), Vec3.ZERO, Draw.X, Draw.Y, 0.0, 0.17, Draw.alpha(-6518, fade), Draw.alpha(-2513920, fade), 10);
            c.pop();
         }
      }

      @Override
      public void glow(Vfx.Ctx c) {
         double t = (double)this.age(c.t) / 1000.0;
         float fade = 1.0F - this.p(c.t);

         for (int i = 0; i < this.n; i += 3) {
            long s = this.seed + (long)i * 53L;
            double ang = Draw.hash(s) * Math.PI * 2.0;
            double out = 2.0 + Draw.hash(s + 1L) * 6.0;
            double up = 6.0 + Draw.hash(s + 2L) * 9.0;
            double y = Math.max(-0.9, up * t - 9.8 * t * t);
            Vec3 p = this.pos.add(Math.cos(ang) * out * t, 1.0 + y, Math.sin(ang) * out * t);
            c.at(p);
            if ((c.t / 60L + (long)i) % 4L == 0L) {
               Draw.billboard(c.vc, c.m(), Vec3.ZERO, 0.35, Draw.alpha(-2880, fade), c.cam.getLeftVector(), c.cam.getUpVector());
            }

            c.pop();
         }
      }
   }

   public static final class Ctx {
      public VertexConsumer vc;
      public PoseStack ps;
      public Camera cam;
      public Vec3 camPos;
      public long t;
      public float partial;

      public Matrix4f m() {
         return this.ps.last().pose();
      }

      public void at(Vec3 world) {
         this.ps.pushPose();
         this.ps.translate(world.x - this.camPos.x, world.y - this.camPos.y, world.z - this.camPos.z);
      }

      public void pop() {
         this.ps.popPose();
      }

      public Vec3 camRel(Vec3 origin) {
         return this.camPos.subtract(origin);
      }
   }

   public static final class Debris extends Vfx.Effect {
      final Vec3 pos;
      final int n;
      final int col;
      final long seed;
      final double power;

      public Debris(Vec3 pos, int n, double power, long life) {
         super(life);
         this.pos = pos;
         this.n = n;
         this.power = power;
         this.col = Vfx.groundColor(pos);
         this.seed = (long)(pos.x * 911.0 + pos.z * 577.0 + (double)this.start);
      }

      @Override
      public void solid(Vfx.Ctx c) {
         float p = this.p(c.t);
         double t = (double)this.age(c.t) / 1000.0;
         float fade = p > 0.7F ? 1.0F - (p - 0.7F) / 0.3F : 1.0F;

         for (int i = 0; i < this.n; i++) {
            long s = this.seed + (long)i * 97L;
            double ang = Draw.hash(s) * Math.PI * 2.0;
            double out = (2.0 + Draw.hash(s + 1L) * 5.0) * this.power;
            double up = (4.0 + Draw.hash(s + 2L) * 6.0) * this.power;
            double x = Math.cos(ang) * out * t;
            double z = Math.sin(ang) * out * t;
            double y = up * t - 9.8 * t * t;
            if (y < 0.0) {
               y = 0.0;
            }

            double size = 0.07 + Draw.hash(s + 3L) * 0.2;
            int col = Draw.scaleRgb(this.col, 0.6F + (float)Draw.hash(s + 4L) * 0.6F);
            c.at(this.pos.add(x, y + size, z));
            c.ps.mulPose(Axis.XP.rotation((float)(t * 9.0 * (Draw.hash(s + 5L) - 0.5) * 2.0)));
            c.ps.mulPose(Axis.ZP.rotation((float)(t * 7.0 * (Draw.hash(s + 6L) - 0.5) * 2.0)));
            Draw.cube(c.vc, c.m(), size, Draw.alpha(col, fade));
            c.pop();
         }
      }
   }

   public static final class Doors extends Vfx.Effect {
      final Vec3 pos;
      final float yaw;
      final long closeMs;
      final boolean ghost;

      public Doors(Vec3 pos, float yaw, boolean fast, boolean ghost, long life) {
         super(life);
         this.pos = pos;
         this.yaw = yaw;
         this.closeMs = fast ? 200L : 450L;
         this.ghost = ghost;
      }

      double offset(long t) {
         long a = this.age(t);
         if (this.ghost) {
            return 1.25 + 0.15 * Math.sin((double)a / 60.0);
         } else if (a < this.closeMs) {
            return 1.0 + 3.4 * (double)(1.0F - Ease.inExpo((float)a / (float)this.closeMs));
         } else {
            double k = (double)(a - this.closeMs) / 200.0;
            return 1.0 + 0.12 * Math.exp(-k * 3.0) * Math.cos(k * 14.0);
         }
      }

      float fade(long t) {
         float p = this.p(t);
         if (this.ghost) {
            return t / 50L % 3L == 0L ? 0.35F : 0.75F;
         } else {
            return p > 0.72F ? 1.0F - (p - 0.72F) / 0.28F : 1.0F;
         }
      }

      void panel(Vfx.Ctx c, double sign, long t, boolean glow) {
         double off = this.offset(t);
         float f = this.fade(t);
         c.ps.pushPose();
         c.ps.translate(sign * off, 0.0, 0.0);
         Matrix4f m = c.m();
         double h = 3.4;
         double d = 0.07;
         double x0 = -1.0;
         double x1 = 1.0;
         if (!glow) {
            int steel = Draw.alpha(this.ghost ? -9545088 : -7433570, f * (this.ghost ? 0.55F : 1.0F));
            Draw.box(c.vc, m, x0, 0.0, -d, x1, h, d, steel);

            for (int i = 0; i < 12; i++) {
               double y = 0.2 + (double)i * 0.26;
               int slat = Draw.alpha(i % 2 == 0 ? -4933436 : -9802118, f);
               Draw.box(c.vc, m, x0 + 0.06, y, -d - 0.035, x1 - 0.06, y + 0.12, d + 0.035, slat);
            }

            int frame = Draw.alpha(-14014928, f);
            Draw.box(c.vc, m, x0, 0.0, -d - 0.05, x0 + 0.1, h, d + 0.05, frame);
            Draw.box(c.vc, m, x1 - 0.1, 0.0, -d - 0.05, x1, h, d + 0.05, frame);
            Draw.box(c.vc, m, x0, h - 0.12, -d - 0.06, x1, h + 0.05, d + 0.06, frame);
            Draw.box(c.vc, m, x0, 0.0, -d - 0.06, x1, 0.1, d + 0.06, frame);
            double hx = sign > 0.0 ? x0 + 0.25 : x1 - 0.25;
            Draw.box(c.vc, m, hx - 0.04, 1.4, -d - 0.12, hx + 0.04, 2.0, d + 0.12, Draw.alpha(-1518982, f));
         } else {
            int edge = Draw.alpha(-45128, f * 0.8F);
            Vec3 cam = c.camRel(this.pos);
            double ex = sign > 0.0 ? x0 : x1;
            Draw.ribbon(c.vc, m, new Vec3(ex, 0.0, 0.0), new Vec3(ex, h, 0.0), 0.06, 0.06, edge, edge, cam);
            Draw.ribbon(c.vc, m, new Vec3(x0, h, 0.0), new Vec3(x1, h, 0.0), 0.05, 0.05, edge, edge, cam);
         }

         c.ps.popPose();
      }

      @Override
      public void solid(Vfx.Ctx c) {
         c.at(this.pos);
         c.ps.mulPose(Axis.YP.rotationDegrees(-this.yaw));
         this.panel(c, -1.0, c.t, false);
         this.panel(c, 1.0, c.t, false);
         c.pop();
      }

      @Override
      public void glow(Vfx.Ctx c) {
         c.at(this.pos);
         c.ps.mulPose(Axis.YP.rotationDegrees(-this.yaw));
         this.panel(c, -1.0, c.t, true);
         this.panel(c, 1.0, c.t, true);
         long a = this.age(c.t);
         if (!this.ghost && a > this.closeMs && a < this.closeMs + 180L) {
            float k = 1.0F - (float)(a - this.closeMs) / 180.0F;
            Vec3 cam = c.camRel(this.pos);
            Draw.ribbon(
               c.vc, c.m(), new Vec3(0.0, -0.4, 0.0), new Vec3(0.0, 3.9, 0.0), 0.25 * (double)k, 0.25 * (double)k, Draw.alpha(-1, k), Draw.alpha(-1, k), cam
            );
            Draw.ribbon(
               c.vc,
               c.m(),
               new Vec3(0.0, -0.4, 0.0),
               new Vec3(0.0, 3.9, 0.0),
               0.9 * (double)k,
               0.9 * (double)k,
               Draw.alpha(-45128, k * 0.4F),
               Draw.alpha(-45128, k * 0.4F),
               cam
            );
         }

         c.pop();
      }
   }

   public abstract static class Effect {
      public final long start = Vfx.now();
      public final long life;

      protected Effect(long life) {
         this.life = life;
      }

      public float p(long t) {
         return Mth.clamp((float)(t - this.start) / (float)this.life, 0.0F, 1.0F);
      }

      public long age(long t) {
         return t - this.start;
      }

      public boolean dead(long t) {
         return t - this.start > this.life;
      }

      public void solid(Vfx.Ctx c) {
      }

      public void glow(Vfx.Ctx c) {
      }
   }

   public static final class Fists extends Vfx.Effect {
      final int entityId;
      final int col;

      public Fists(int entityId, int col, long life) {
         super(life);
         this.entityId = entityId;
         this.col = col;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         Entity e = Vfx.entity(this.entityId);
         if (e != null) {
            Vec3 base = e.getPosition(c.partial).add(0.0, 1.3, 0.0);
            Vec3 fwd = Draw.forward(e.getYRot(), 0.0F);
            Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
            c.at(base);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(base);
            double time = (double)this.age(c.t) / 1000.0;

            for (int j = 0; j < 14; j++) {
               double ph = (time * 13.0 + (double)j * 0.37) % 1.0;
               long cycle = (long)(time * 13.0 + (double)j * 0.37);
               double lx = (Draw.hash(cycle * 31L + (long)j) - 0.5) * 1.5;
               double ly = (Draw.hash(cycle * 17L + (long)j) - 0.5) * 0.9;
               Vec3 origin = right.scale(lx).add(0.0, ly, 0.0).add(fwd.scale(0.3));
               Vec3 tip = origin.add(fwd.scale(0.4 + ph * 2.0));
               float a = (float)(1.0 - ph);
               Draw.ribbon(c.vc, m, origin, tip, 0.02, 0.17, Draw.alpha(this.col, 0.0F), Draw.alpha(this.col, a * 0.7F), cam);
               Draw.glowOrb(c.vc, m, tip.x, tip.y, tip.z, 0.14, this.col, a * 0.8F);
            }

            c.pop();
         }
      }
   }

   public static final class Flash extends Vfx.Effect {
      final Vec3 pos;
      final double r;
      final int col;

      public Flash(Vec3 pos, double r, int col, long life) {
         super(life);
         this.pos = pos;
         this.r = r;
         this.col = col;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         float a = (1.0F - p) * (1.0F - p);
         double s = this.r * (0.35 + 0.65 * (double)Ease.outExpo(p));
         c.at(this.pos);
         Matrix4f m = c.m();
         Draw.sphere(c.vc, m, 0.0, 0.0, 0.0, s * 0.4, 8, 12, Draw.alpha(-1, a));
         Draw.sphere(c.vc, m, 0.0, 0.0, 0.0, s, 10, 14, Draw.alpha(this.col, a * 0.5F));
         Draw.sphere(c.vc, m, 0.0, 0.0, 0.0, s * 1.8, 10, 14, Draw.alpha(this.col, a * 0.15F));
         Vec3 cam = c.camRel(this.pos);
         Vec3 l = Vfx.v3(c.cam.getLeftVector());
         Vec3 u = Vfx.v3(c.cam.getUpVector());
         double fl = s * 4.5 * (1.0 - (double)p * 0.5);
         Draw.ribbon(c.vc, m, l.scale(-fl), l.scale(fl), 0.0, 0.0, 0, 0, cam);
         Draw.ribbon(c.vc, m, Vec3.ZERO, l.scale(fl), s * 0.12, 0.0, Draw.alpha(-1, a), Draw.alpha(this.col, 0.0F), cam);
         Draw.ribbon(c.vc, m, Vec3.ZERO, l.scale(-fl), s * 0.12, 0.0, Draw.alpha(-1, a), Draw.alpha(this.col, 0.0F), cam);
         Draw.ribbon(c.vc, m, Vec3.ZERO, u.scale(fl * 0.6), s * 0.08, 0.0, Draw.alpha(-1, a), Draw.alpha(this.col, 0.0F), cam);
         Draw.ribbon(c.vc, m, Vec3.ZERO, u.scale(-fl * 0.6), s * 0.08, 0.0, Draw.alpha(-1, a), Draw.alpha(this.col, 0.0F), cam);
         c.pop();
      }
   }

   public static final class Pillar extends Vfx.Effect {
      final Vec3 pos;
      final double h;
      final double r;
      final int col;

      public Pillar(Vec3 pos, double h, double r, int col, long life) {
         super(life);
         this.pos = pos;
         this.h = h;
         this.r = r;
         this.col = col;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         double rr = this.r * (p < 0.15F ? (double)Ease.outExpo(p / 0.15F) : 1.0 - 0.85 * (double)Ease.inOut((p - 0.15F) / 0.85F));
         float a = 1.0F - p * 0.6F;
         c.at(this.pos);
         Draw.beam(c.vc, c.m(), Vec3.ZERO, this.h, rr, Draw.alpha(-1, a), Draw.alpha(this.col, 0.0F), 20);
         Draw.beam(c.vc, c.m(), Vec3.ZERO, this.h * 0.8, rr * 2.2, Draw.alpha(this.col, a * 0.35F), Draw.alpha(this.col, 0.0F), 20);
         c.pop();
      }
   }

   public static final class RhythmPulse extends Vfx.Effect {
      final int entityId;

      public RhythmPulse(int entityId, long life) {
         super(life);
         this.entityId = entityId;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         Entity e = Vfx.entity(this.entityId);
         if (e != null) {
            Vec3 base = e.getPosition(c.partial).add(0.0, 0.06, 0.0);
            float beat = (float)(this.age(c.t) % 500L) / 500.0F;
            c.at(base);
            double r = 0.5 + 3.5 * (double)Ease.outExpo(beat);
            Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, r - 0.25, r, Draw.alpha(-10685697, 0.0F), Draw.alpha(-10685697, (1.0F - beat) * 0.8F), 40);
            Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, 0.7, 0.85, Draw.alpha(-11443, 0.6F), Draw.alpha(-11443, 0.6F), 40);
            c.pop();
         }
      }
   }

   public static final class Shatter extends Vfx.Effect {
      final Vec3 c0;
      final double R;
      final int edge;
      final long seed;

      public Shatter(Vec3 center, double R, int edge, long life) {
         super(life);
         this.c0 = center;
         this.R = R;
         this.edge = edge;
         this.seed = this.start;
      }

      @Override
      public void solid(Vfx.Ctx c) {
         double t = (double)this.age(c.t) / 1000.0;
         float fade = 1.0F - this.p(c.t);

         for (int i = 0; i < 170; i++) {
            long s = this.seed + (long)i * 41L;
            Vec3 d = Draw.randDir(s);
            double sp = 4.0 + Draw.hash(s + 3L) * 9.0;
            Vec3 p = this.c0.add(d.scale(this.R + sp * t)).add(0.0, -4.9 * t * t, 0.0);
            double size = 1.2 + Draw.hash(s + 4L) * 2.4;
            c.at(p);
            float yaw = (float)Math.atan2(d.x, d.z);
            c.ps.mulPose(Axis.YP.rotation(yaw));
            c.ps.mulPose(Axis.XP.rotation((float)(Math.asin(-d.y) + t * 3.0 * (Draw.hash(s + 5L) - 0.5))));
            c.ps.mulPose(Axis.ZP.rotation((float)(t * 4.0 * (Draw.hash(s + 6L) - 0.5))));
            Draw.box(c.vc, c.m(), -size / 2.0, -size / 2.0, -0.04, size / 2.0, size / 2.0, 0.04, Draw.alpha(-15859696, fade));
            c.pop();
         }
      }

      @Override
      public void glow(Vfx.Ctx c) {
         double t = (double)this.age(c.t) / 1000.0;
         float fade = 1.0F - this.p(c.t);

         for (int i = 0; i < 170; i += 2) {
            long s = this.seed + (long)i * 41L;
            Vec3 d = Draw.randDir(s);
            double sp = 4.0 + Draw.hash(s + 3L) * 9.0;
            Vec3 p = this.c0.add(d.scale(this.R + sp * t)).add(0.0, -4.9 * t * t, 0.0);
            c.at(p);
            Draw.billboard(c.vc, c.m(), Vec3.ZERO, 0.6, Draw.alpha(this.edge, fade * 0.7F), c.cam.getLeftVector(), c.cam.getUpVector());
            c.pop();
         }
      }
   }

   public static final class Shockwave extends Vfx.Effect {
      final Vec3 pos;
      final Vec3 u;
      final Vec3 v;
      final double maxR;
      final double width;
      final int col;

      public Shockwave(Vec3 pos, Vec3 u, Vec3 v, double maxR, double width, int col, long life) {
         super(life);
         this.pos = pos;
         this.u = u;
         this.v = v;
         this.maxR = maxR;
         this.width = width;
         this.col = col;
      }

      public static Vfx.Shockwave ground(Vec3 pos, double r, int col, long life) {
         return new Vfx.Shockwave(pos.add(0.0, 0.08, 0.0), Draw.X, Draw.Z, r, Math.max(0.4, r * 0.18), col, life);
      }

      public static Vfx.Shockwave facing(Vec3 pos, Vec3 dir, double r, int col, long life) {
         Vec3 d = dir.normalize();
         Vec3 a = Draw.perp(d);
         return new Vfx.Shockwave(pos, a, d.cross(a), r, Math.max(0.25, r * 0.22), col, life);
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         double r = this.maxR * (double)Ease.outExpo(p);
         double w = this.width * (double)(1.0F - p) + 0.04;
         float a = 1.0F - p;
         c.at(this.pos);
         Matrix4f m = c.m();
         Draw.ring(c.vc, m, Vec3.ZERO, this.u, this.v, Math.max(0.0, r - w), r, Draw.alpha(this.col, 0.0F), Draw.alpha(this.col, a * 0.9F), 48);
         Draw.ring(c.vc, m, Vec3.ZERO, this.u, this.v, r, r + w * 0.25, Draw.alpha(-1, a), Draw.alpha(-1, 0.0F), 48);
         c.pop();
      }
   }

   public static final class Spikes extends Vfx.Effect {
      final Vec3 pos;
      final int n;
      final double len;
      final int col;
      final long seed;
      final Vec3 bias;
      final double biasAmt;

      public Spikes(Vec3 pos, int n, double len, int col, long life, Vec3 bias, double biasAmt) {
         super(life);
         this.pos = pos;
         this.n = n;
         this.len = len;
         this.col = col;
         this.seed = (long)(pos.x * 7349.0 + pos.z * 1931.0 + (double)this.start);
         this.bias = bias;
         this.biasAmt = biasAmt;
      }

      public Spikes(Vec3 pos, int n, double len, int col, long life) {
         this(pos, n, len, col, life, Vec3.ZERO, 0.0);
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         c.at(this.pos);
         Matrix4f m = c.m();
         Vec3 cam = c.camRel(this.pos);

         for (int i = 0; i < this.n; i++) {
            Vec3 d = Draw.randDir(this.seed + (long)i * 31L).add(this.bias.scale(this.biasAmt)).normalize();
            double l = this.len * (0.5 + Draw.hash(this.seed + (long)i * 17L));
            double outer = l * (double)Ease.outExpo(Math.min(1.0F, p * 1.7F));
            double inner = l * (double)Ease.outCubic(p) * 0.85;
            double w = 0.07 * (double)(1.0F - p) * (0.6 + Draw.hash(this.seed + (long)i));
            Draw.ribbon(c.vc, m, d.scale(inner), d.scale(outer), w, 0.0, Draw.alpha(-1, 1.0F - p), Draw.alpha(this.col, (1.0F - p) * 0.6F), cam);
         }

         c.pop();
      }
   }

   public static final class Trail extends Vfx.Effect {
      final int entityId;
      final int col;
      final Deque<Vec3> points = new ArrayDeque<>();

      public Trail(int entityId, int col, long life) {
         super(life);
         this.entityId = entityId;
         this.col = col;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         Entity e = Vfx.entity(this.entityId);
         if (e != null && this.p(c.t) < 0.85F) {
            Vec3 now = e.getPosition(c.partial);
            if (this.points.isEmpty() || this.points.peekLast().distanceToSqr(now) > 0.04) {
               this.points.addLast(now);
            }
         } else if (!this.points.isEmpty()) {
            this.points.pollFirst();
         }

         while (this.points.size() > 28) {
            this.points.pollFirst();
         }

         if (this.points.size() >= 2) {
            Vec3 origin = this.points.peekLast();
            c.at(origin);
            Matrix4f m = c.m();
            Vec3[] arr = this.points.toArray(new Vec3[0]);

            for (int i = 1; i < arr.length; i++) {
               float a0 = (float)(i - 1) / (float)arr.length;
               float a1 = (float)i / (float)arr.length;
               Vec3 p0 = arr[i - 1].subtract(origin);
               Vec3 p1 = arr[i].subtract(origin);
               Draw.quad(
                  c.vc,
                  m,
                  p0.add(0.0, 0.1, 0.0),
                  p1.add(0.0, 0.1, 0.0),
                  p1.add(0.0, 1.9, 0.0),
                  p0.add(0.0, 1.9, 0.0),
                  Draw.alpha(this.col, a0 * 0.18F),
                  Draw.alpha(this.col, a1 * 0.18F),
                  Draw.alpha(this.col, a1 * 0.4F),
                  Draw.alpha(this.col, a0 * 0.4F)
               );
            }

            Vec3 cam = c.camRel(origin);

            for (int k = 0; k < 6; k++) {
               double y = 0.2 + Draw.hash((long)(k * 13) + this.start) * 1.6;
               Vec3 from = arr[0].subtract(origin).add(0.0, y, 0.0);
               Draw.ribbon(c.vc, m, from, new Vec3(0.0, y, 0.0), 0.0, 0.03, 0, Draw.alpha(-1, 0.7F), cam);
            }

            c.pop();
         }
      }
   }
}
