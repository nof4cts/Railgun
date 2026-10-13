package com.geto.csm.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class CsmFx {
   static void glowTube(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double r, int ca, int cb, int segs) {
      Vec3 d = b.subtract(a);
      if (!(d.lengthSqr() < 1.0E-8)) {
         Vec3 u = Draw.perp(d.normalize());
         Vec3 v = d.normalize().cross(u);

         for (int i = 0; i < segs; i++) {
            double a0 = (Math.PI * 2) * (double)i / (double)segs;
            double a1 = (Math.PI * 2) * (double)(i + 1) / (double)segs;
            Vec3 o0 = u.scale(Math.cos(a0) * r).add(v.scale(Math.sin(a0) * r));
            Vec3 o1 = u.scale(Math.cos(a1) * r).add(v.scale(Math.sin(a1) * r));
            Draw.quad(vc, m, a.add(o0), a.add(o1), b.add(o1), b.add(o0), ca, ca, cb, cb);
         }
      }
   }

   private CsmFx() {
   }

   public static final class BreathCone extends Vfx.Effect {
      final Vec3 from;
      final Vec3 dir;
      final double len;
      final int core;
      final int edge;

      public BreathCone(Vec3 from, Vec3 dir, double len, int core, int edge, long life) {
         super(life);
         this.from = from;
         this.dir = dir.normalize();
         this.len = len;
         this.core = core;
         this.edge = edge;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         double s = (double)this.age(c.t) / 1000.0;
         c.at(this.from);
         Vec3 cam = c.camRel(this.from);
         Vec3 u = Draw.perp(this.dir);
         Vec3 v = this.dir.cross(u);
         long frame = c.t / 40L;

         for (int i = 0; i < 46; i++) {
            double ph = (s * 2.4 + Draw.hash((long)i * 3L)) % 1.0;
            double d = this.len * ph * Math.min(1.0, s * 4.0);
            double spread = 0.4 + d * 0.16;
            double a = Draw.hash((long)i * 7L + frame) * Math.PI * 2.0;
            Vec3 p0 = this.dir.scale(d).add(u.scale(Math.cos(a) * spread * Draw.hash((long)i))).add(v.scale(Math.sin(a) * spread * Draw.hash((long)i)));
            Vec3 p1 = p0.add(this.dir.scale(1.2 + d * 0.06));
            float al = (float)((1.0 - ph) * (double)(1.0F - p));
            Draw.ribbon(c.vc, c.m(), p0, p1, 0.2 + d * 0.05, 0.05, Draw.alpha(this.core, al), Draw.alpha(this.edge, al * 0.5F), cam);
         }

         Draw.glowOrb(c.vc, c.m(), 0.0, 0.0, 0.0, 0.8, this.core, 0.6F * (1.0F - p));
         c.pop();
      }
   }

   public static final class Foxfire extends Vfx.Effect {
      final Vec3 from;
      final int targetId;
      final Vec3 bend;
      boolean landed;

      public Foxfire(Vec3 from, int targetId, int index, long life) {
         super(life);
         this.from = from;
         this.targetId = targetId;
         this.bend = Draw.randDir(this.start + (long)index * 101L).add(0.0, 1.2, 0.0).scale(3.5);
      }

      Vec3 target(float partial) {
         Entity t = Vfx.entity(this.targetId);
         return t == null ? this.from : t.getPosition(partial).add(0.0, (double)t.getBbHeight() * 0.55, 0.0);
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = Math.min(1.0F, (float)this.age(c.t) / 700.0F);
         Vec3 to = this.target(c.partial);
         Vec3 ctrl = this.from.add(to).scale(0.5).add(this.bend);
         c.at(this.from);
         Vec3 cam = c.camRel(this.from);
         Vec3 prev = null;

         for (int k = 0; k <= 10; k++) {
            double u = Math.max(0.0, (double)p - 0.25 + 0.25 * (double)k / 10.0);
            double iu = 1.0 - u;
            Vec3 pt = this.from.scale(iu * iu).add(ctrl.scale(2.0 * iu * u)).add(to.scale(u * u)).subtract(this.from);
            if (prev != null) {
               Draw.ribbon(
                  c.vc,
                  c.m(),
                  prev,
                  pt,
                  0.02 + 0.02 * (double)k,
                  0.04 + 0.02 * (double)k,
                  Draw.alpha(-6297857, (float)k / 10.0F * 0.5F),
                  Draw.alpha(-2098945, (float)k / 10.0F),
                  cam
               );
            }

            prev = pt;
         }

         if (prev != null && p < 1.0F) {
            Draw.glowOrb(c.vc, c.m(), prev.x, prev.y, prev.z, 0.3, -6297857, 1.0F);
         }

         c.pop();
         if (p >= 1.0F && !this.landed) {
            this.landed = true;
            Sfx.later(0L, () -> {
               Vfx.add(new Vfx.Flash(to, 1.2, -6297857, 300L));
               Vfx.add(new Vfx.Spikes(to, 14, 2.2, -6297857, 280L));
            });
         }
      }
   }

   public static final class KillingStone extends Vfx.Effect {
      final Vec3 at;
      boolean impacted;

      public KillingStone(Vec3 at, long life) {
         super(life);
         this.at = at;
      }

      double height(long t) {
         long a = this.age(t);
         if (a < 1000L) {
            return (double)(18.0F * (1.0F - Ease.inExpo((float)a / 1000.0F)));
         } else {
            float p = this.p(t);
            return p > 0.8F ? -3.5 * (double)Ease.inOut((p - 0.8F) / 0.2F) : 0.0;
         }
      }

      @Override
      public void solid(Vfx.Ctx c) {
         Vec3 pos = this.at.add(0.0, this.height(c.t) + 1.4, 0.0);
         c.at(pos);
         Mesh.begin(1, c.vc, c.m(), c.camRel(pos), 0.9F, 0.0F);
         Mesh.Frame f = Mesh.Frame.of(new Vec3(0.3, 0.0, 1.0), Draw.Y);
         Mesh.blob(Vec3.ZERO, f, 1.4, 2.0, 1.2, (u, n) -> (int)((n.x * 3.0 + n.y * 5.0 + n.z * 4.0) * 2.0) % 2 == 0 ? -12962752 : -14015440);
         Mesh.blob(new Vec3(0.6, 1.2, 0.2), f, 0.8, 1.1, 0.7, -13423302);
         Mesh.blob(new Vec3(-0.5, -0.9, -0.3), f, 1.0, 0.8, 0.9, -13752268);
         Mesh.torus(new Vec3(0.0, 0.8, 0.0), Draw.X, Draw.Z, 1.25, 0.08, 24, -855832);
         c.pop();
         if (this.age(c.t) >= 1000L && !this.impacted) {
            this.impacted = true;
            Vec3 g = this.at;
            Sfx.later(0L, () -> {
               Vfx.add(Vfx.Shockwave.ground(g, 9.0, -6595329, 700L));
               Vfx.add(new Vfx.Debris(g, 30, 1.4, 1800L));
               Vfx.add(new Vfx.Flash(g.add(0.0, 1.0, 0.0), 3.0, -6595329, 400L));
               if (FxDispatcher.feels(g, 26.0)) {
                  CameraDirector.shake(2.4F, 700L);
               }
            });
         }
      }

      @Override
      public void glow(Vfx.Ctx c) {
         if (this.age(c.t) <= 1000L) {
            Vec3 pos = this.at.add(0.0, this.height(c.t) + 1.4, 0.0);
            c.at(pos);
            Draw.glowOrb(c.vc, c.m(), 0.0, 0.0, 0.0, 2.2, -8766209, 0.35F);
            Draw.beam(c.vc, c.m(), Vec3.ZERO, 8.0, 0.8, Draw.alpha(-6595329, 0.4F), 0, 16);
            c.pop();
         }
      }
   }

   public static final class Miasma extends Vfx.Effect {
      final Vec3 at;
      final double r;

      public Miasma(Vec3 at, double r, long life) {
         super(life);
         this.at = at;
         this.r = r;
      }

      @Override
      public void solid(Vfx.Ctx c) {
         float p = this.p(c.t);
         float a = (p < 0.1F ? p / 0.1F : (p > 0.8F ? (1.0F - p) / 0.2F : 1.0F)) * 0.28F;
         double s = (double)this.age(c.t) / 1000.0;
         c.at(this.at);

         for (int i = 0; i < 9; i++) {
            double ang = (double)i * 0.7 + s * (0.3 + (double)i * 0.05);
            double rr = this.r * (0.3 + 0.6 * Draw.hash((long)i * 7L));
            double y = 0.4 + Draw.hash((long)i * 3L) * 1.4 + Math.sin(s + (double)i) * 0.2;
            Draw.sphere(c.vc, c.m(), Math.cos(ang) * rr, y, Math.sin(ang) * rr, 0.9 + Draw.hash((long)i) * 0.9, 8, 12, Draw.alpha(-15071708, a));
         }

         c.pop();
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         c.at(this.at.add(0.0, 0.06, 0.0));
         Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, this.r - 0.4, this.r, 0, Draw.alpha(-6595329, 0.5F * (1.0F - p)), 48);
         c.pop();
      }
   }

   public static final class OrbForm extends Vfx.Effect {
      final Vec3 pos;

      public OrbForm(Vec3 pos, long life) {
         super(life);
         this.pos = pos;
      }

      @Override
      public void solid(Vfx.Ctx c) {
         float p = this.p(c.t);
         double r = 0.28 * (double)Ease.outBack(Math.min(1.0F, p * 2.0F)) * (p > 0.85F ? (double)(1.0F - p) / 0.15 : 1.0);
         c.at(this.pos);
         Draw.sphere(c.vc, c.m(), 0.0, 0.0, 0.0, r, 10, 14, (x, y, z) -> Draw.lerp(-16449528, -12970920, (float)Math.max(0.0, y)));
         c.pop();
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         c.at(this.pos);
         Vec3 cam = c.camRel(this.pos);

         for (int i = 0; i < 16; i++) {
            Vec3 d = Draw.randDir(this.start + (long)i * 31L);
            double k = 1.6 * (double)(1.0F - Ease.outCubic(Math.min(1.0F, p * 1.6F)));
            Draw.ribbon(c.vc, c.m(), d.scale(k + 0.6), d.scale(k), 0.0, 0.04, 0, Draw.alpha(-6595329, 1.0F - p), cam);
         }

         Draw.glowOrb(c.vc, c.m(), 0.0, 0.0, 0.0, 0.35, -8766209, 0.5F * (1.0F - p));
         c.pop();
      }
   }

   public static final class Rift extends Vfx.Effect {
      final Vec3 pos;
      final int col;
      final double R;

      public Rift(Vec3 pos, int col, double R, long life) {
         super(life);
         this.pos = pos;
         this.col = col;
         this.R = R;
      }

      double radius(long t) {
         float p = this.p(t);
         if (p < 0.2F) {
            return this.R * (double)Ease.outBack(p / 0.2F);
         } else {
            return p > 0.7F ? this.R * (double)(1.0F - Ease.inExpo((p - 0.7F) / 0.3F)) : this.R;
         }
      }

      @Override
      public void solid(Vfx.Ctx c) {
         double r = this.radius(c.t);
         c.at(this.pos.add(0.0, 0.05, 0.0));
         Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, 0.0, r, -268107768, -536215534, 40);
         Vec3 cam = c.camRel(this.pos);
         double time = (double)this.age(c.t) / 1000.0;

         for (int i = 0; i < 9; i++) {
            double a = (double)i * 0.7 + time * 1.5;
            Vec3 b = new Vec3(Math.cos(a) * r * 0.7, 0.0, Math.sin(a) * r * 0.7);
            Vec3 tip = b.add(Math.sin(time * 4.0 + (double)i) * 0.4, r * (0.6 + 0.4 * Math.sin(time * 3.0 + (double)i)), Math.cos(time * 3.0 + (double)i) * 0.4);
            Draw.ribbon(c.vc, c.m(), b, tip, 0.18, 0.0, -536543224, 327688, cam);
         }

         c.pop();
      }

      @Override
      public void glow(Vfx.Ctx c) {
         double r = this.radius(c.t);
         double time = (double)this.age(c.t) / 1000.0;
         c.at(this.pos.add(0.0, 0.07, 0.0));
         Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, r * 0.92, r * 1.08, Draw.alpha(this.col, 0.9F), Draw.alpha(this.col, 0.0F), 48);
         Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, r * 0.97, r * 1.0, -1, -1, 48);
         Vec3 cam = c.camRel(this.pos);

         for (int i = 0; i < 10; i++) {
            double a0 = (double)i * Math.PI / 5.0 + time * 3.0;
            Vec3 out = new Vec3(Math.cos(a0) * r, 0.02, Math.sin(a0) * r);
            Vec3 in = new Vec3(Math.cos(a0 + 1.2) * r * 0.2, 0.02, Math.sin(a0 + 1.2) * r * 0.2);
            Draw.ribbon(c.vc, c.m(), out, in, 0.06, 0.0, Draw.alpha(-6595329, 0.7F), 0, cam);
         }

         Draw.beam(c.vc, c.m(), Vec3.ZERO, (double)(5.0F * (1.0F - this.p(c.t))), r * 0.5, Draw.alpha(this.col, 0.35F), 0, 20);
         c.pop();
      }
   }

   public static final class ShearsFx extends Vfx.Effect {
      final int targetId;
      final boolean big;
      final long snapAt;
      final Vec3 fallback;
      boolean snapped;

      public ShearsFx(int targetId, Vec3 fallback, boolean big, long life) {
         super(life);
         this.targetId = targetId;
         this.big = big;
         this.fallback = fallback;
         this.snapAt = big ? 450L : 120L;
      }

      Vec3 center(float partial) {
         Entity t = Vfx.entity(this.targetId);
         return t == null ? this.fallback : t.getPosition(partial).add(0.0, (double)t.getBbHeight() * 0.5, 0.0);
      }

      double open(long t) {
         long a = this.age(t);
         return a < this.snapAt
            ? 0.75 * (double)Ease.outCubic(Math.min(1.0F, (float)a / 150.0F))
            : 0.75 * Math.max(0.0, 1.0 - (double)(a - this.snapAt) / 60.0);
      }

      @Override
      public void solid(Vfx.Ctx c) {
         float p = this.p(c.t);
         if (!(p > 0.85F)) {
            Vec3 cen = this.center(c.partial);
            c.at(cen);
            Mesh.begin(1, c.vc, c.m(), c.camRel(cen), 1.0F, 0.0F);
            double op = this.open(c.t);
            int pairs = this.big ? 3 : 1;

            for (int i = 0; i < pairs; i++) {
               double ang = (double)i * 2.1 + 0.4;
               Vec3 radial = new Vec3(Math.cos(ang), (double)(i - 1) * 0.35, Math.sin(ang)).normalize();
               Vec3 hand = radial.scale(1.9);
               CurseModels.shears(hand, radial.scale(-1.0), Draw.Y.cross(radial).normalize(), op, this.big ? 1.4 : 1.0);
            }

            c.pop();
            if (this.age(c.t) >= this.snapAt && !this.snapped) {
               this.snapped = true;
               Sfx.later(0L, () -> {
                  Vfx.add(new Vfx.Flash(cen, 1.4, -2086852, 260L));
                  Vfx.add(new Vfx.Spikes(cen, 20, 3.0, -1, 260L));
                  if (this.big && FxDispatcher.feels(cen, 18.0)) {
                     CameraDirector.shake(1.8F, 400L);
                  }
               });
            }
         }
      }
   }

   public static final class UzuBeam extends Vfx.Effect {
      final Vec3 origin;
      final Vec3 dir;
      final double length = 52.0;

      public UzuBeam(Vec3 origin, Vec3 dir, long life) {
         super(life);
         this.origin = origin;
         this.dir = dir.normalize();
      }

      double reach(long t) {
         return 52.0 * (double)Ease.outExpo(Math.min(1.0F, (float)this.age(t) / 260.0F));
      }

      double width(long t) {
         float p = this.p(t);
         return 2.6 * (double)(p < 0.08F ? Ease.outBack(p / 0.08F) : 1.0F - Ease.inOut(Math.max(0.0F, (p - 0.45F) / 0.55F)));
      }

      @Override
      public void solid(Vfx.Ctx c) {
         double L = this.reach(c.t);
         double w = this.width(c.t);
         if (!(w < 0.02)) {
            c.at(this.origin);
            CsmFx.glowTube(c.vc, c.m(), Vec3.ZERO, this.dir.scale(L), w * 0.45, -16580602, -16449520, 18);
            c.pop();
         }
      }

      @Override
      public void glow(Vfx.Ctx c) {
         double L = this.reach(c.t);
         double w = this.width(c.t);
         if (!(w < 0.02)) {
            double s = (double)this.age(c.t) / 1000.0;
            c.at(this.origin);
            Vec3 cam = c.camRel(this.origin);
            Vec3 u = Draw.perp(this.dir);
            Vec3 v = this.dir.cross(u);
            int[] cols = new int[]{-6595329, -45096, -10867457};

            for (int strand = 0; strand < 3; strand++) {
               Vec3 prev = null;

               for (int k = 0; k <= 90; k++) {
                  double d = L * (double)k / 90.0;
                  double a = d * 0.9 - s * 22.0 + (double)strand * 2.094;
                  double rad = w * (0.7 + 0.15 * Math.sin(d * 0.5 + s * 10.0));
                  Vec3 p = this.dir.scale(d).add(u.scale(Math.cos(a) * rad)).add(v.scale(Math.sin(a) * rad));
                  if (prev != null) {
                     Draw.ribbon(c.vc, c.m(), prev, p, 0.18, 0.18, Draw.alpha(cols[strand], 0.85F), Draw.alpha(cols[strand], 0.85F), cam);
                  }

                  prev = p;
               }
            }

            CsmFx.glowTube(c.vc, c.m(), Vec3.ZERO, this.dir.scale(L), w * 1.1, Draw.alpha(-8766209, 0.35F), Draw.alpha(-8766209, 0.1F), 20);
            CsmFx.glowTube(c.vc, c.m(), Vec3.ZERO, this.dir.scale(L), w * 0.55, Draw.alpha(-1, 0.5F), Draw.alpha(-45096, 0.2F), 16);

            for (int i = 0; i < 6; i++) {
               double d = (s * 40.0 + (double)(i * 9)) % L;
               Draw.ring(c.vc, c.m(), this.dir.scale(d), u, v, w * 1.2, w * 2.0, Draw.alpha(-1, 0.5F), 0, 32);
            }

            Vec3 tip = this.dir.scale(L);
            Draw.glowOrb(c.vc, c.m(), tip.x, tip.y, tip.z, w * 2.2, -45096, 0.6F);
            c.pop();
         }
      }
   }

   public static final class UzuCore extends Vfx.Effect {
      final Vec3 pos;
      final int n;

      public UzuCore(Vec3 pos, int n, long life) {
         super(life);
         this.pos = pos;
         this.n = n;
      }

      double radius(long t) {
         double s = (double)this.age(t) / 1000.0;
         double grow = Math.min(1.0, (double)this.n / 12.0) * 0.8 + 0.6;
         if (s < 3.3) {
            return grow * (double)Ease.outCubic((float)Math.min(1.0, s / 3.0));
         } else {
            return s < 4.2 ? Mth.lerp((s - 3.3) / 0.9, grow, grow * 0.35) : grow * 0.35 * Math.max(0.0, 1.0 - (s - 4.2) / 0.2);
         }
      }

      @Override
      public void solid(Vfx.Ctx c) {
         double r = this.radius(c.t);
         if (!(r <= 0.01)) {
            c.at(this.pos);
            Draw.sphere(c.vc, c.m(), 0.0, 0.0, 0.0, r, 16, 22, (x, y, z) -> Draw.lerp(-16646140, -15071184, (float)Math.abs(y) * 0.5F));
            c.pop();
         }
      }

      @Override
      public void glow(Vfx.Ctx c) {
         double r = this.radius(c.t);
         double s = (double)this.age(c.t) / 1000.0;
         c.at(this.pos);
         Vec3 cam = c.camRel(this.pos);
         int[] cols = new int[]{-10867457, -6595329, -45096, -12968768};
         double spin = s * (6.0 + s * 4.0);

         for (int band = 0; band < 8; band++) {
            Vec3 axis = Draw.randDir((long)band * 977L + 5L);
            Vec3 u = Draw.perp(axis);
            Vec3 v = axis.cross(u);
            Vec3 prev = null;
            double rr = r * (1.35 + (double)band * 0.08);

            for (int k = 0; k <= 30; k++) {
               double a = spin * (double)(band % 2 == 0 ? 1 : -1) + (double)k * 0.2;
               double wob = Math.sin((double)k * 0.4 + s * 3.0) * 0.12 * r;
               Vec3 p = u.scale(Math.cos(a) * (rr + wob)).add(v.scale(Math.sin(a) * (rr + wob)));
               if (prev != null) {
                  float al = (float)((double)k / 30.0);
                  Draw.ribbon(
                     c.vc, c.m(), prev, p, 0.04 + r * 0.04, 0.04 + r * 0.04, Draw.alpha(cols[band % 4], al * 0.2F), Draw.alpha(cols[band % 4], al * 0.9F), cam
                  );
               }

               prev = p;
            }
         }

         Vec3 tilt = new Vec3(0.3, 1.0, 0.2).normalize();
         Vec3 tu = Draw.perp(tilt);
         Vec3 tv = tilt.cross(tu);
         Draw.ring(c.vc, c.m(), Vec3.ZERO, tu, tv, r * 1.6, r * 3.2, Draw.alpha(-6595329, 0.55F), 0, 64);
         Draw.ring(c.vc, c.m(), Vec3.ZERO, tu, tv, r * 1.2, r * 1.6, -1, Draw.alpha(-45096, 0.6F), 64);
         if (s > 2.2) {
            long frame = c.t / 50L;

            for (int i = 0; i < 6; i++) {
               Vec3 a = Draw.randDir(frame * 7L + (long)i).scale(r * 1.05);
               Vec3 b = a.add(Draw.randDir(frame * 13L + (long)i).scale(r * 1.6));
               Draw.ribbon(c.vc, c.m(), a, b, 0.05, 0.01, -1, Draw.alpha(-6595329, 0.6F), cam);
            }
         }

         Draw.glowOrb(c.vc, c.m(), 0.0, 0.0, 0.0, r * 2.2, -8766209, 0.25F);
         c.pop();
      }
   }

   public static final class UzuStream extends Vfx.Effect {
      final Vec3 from;
      final Vec3 to;
      final int col;

      public UzuStream(Vec3 from, Vec3 to, int col, long life) {
         super(life);
         this.from = from;
         this.to = to;
         this.col = col;
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         c.at(this.to);
         Vec3 a = this.from.subtract(this.to);
         Vec3 mid = a.scale(0.5).add(0.0, a.length() * 0.35 + 1.0, 0.0);
         Vec3 cam = c.camRel(this.to);
         double head = (double)Ease.inOut(Math.min(1.0F, p * 1.3F));
         double tail = (double)Ease.inOut(Math.max(0.0F, p * 1.3F - 0.35F));
         Vec3 prev = null;

         for (int k = 0; k <= 40; k++) {
            double u = tail + (head - tail) * (double)k / 40.0;
            double iu = 1.0 - u;
            Vec3 base = a.scale(iu * iu).add(mid.scale(2.0 * iu * u));
            Vec3 tan = a.scale(-2.0 * iu).add(mid.scale(2.0 * (iu - u))).normalize();
            Vec3 pu = Draw.perp(tan);
            Vec3 pv = tan.cross(pu);
            double ang = u * 18.0 + (double)this.age(c.t) / 80.0;
            double rad = 0.5 * (1.0 - u) + 0.05;
            Vec3 pt = base.add(pu.scale(Math.cos(ang) * rad)).add(pv.scale(Math.sin(ang) * rad));
            if (prev != null) {
               Draw.ribbon(c.vc, c.m(), prev, pt, 0.1, 0.1, Draw.alpha(this.col, 0.7F), Draw.alpha(-6595329, 0.9F), cam);
            }

            prev = pt;
         }

         if (p < 0.35F) {
            Draw.glowOrb(c.vc, c.m(), a.x, a.y, a.z, 1.6 * (double)(1.0F - p / 0.35F), this.col, 0.6F * (1.0F - p / 0.35F));
         }

         c.pop();
      }
   }

   public static final class VowDome extends Vfx.Effect {
      final Vec3 at;
      final double r;

      public VowDome(Vec3 at, double r, long life) {
         super(life);
         this.at = at;
         this.r = r;
      }

      @Override
      public void solid(Vfx.Ctx c) {
         float p = this.p(c.t);
         float a = p < 0.05F ? p / 0.05F : (p > 0.9F ? (1.0F - p) / 0.1F : 1.0F);
         c.at(this.at);
         Draw.sphere(
            c.vc,
            c.m(),
            0.0,
            0.0,
            0.0,
            this.r * (double)Ease.outBack(Math.min(1.0F, p * 12.0F)),
            14,
            24,
            (x, y, z) -> y < 0.0 ? 0 : Draw.alpha(-12976112, 0.22F * a)
         );
         c.pop();
      }

      @Override
      public void glow(Vfx.Ctx c) {
         float p = this.p(c.t);
         float a = p > 0.9F ? (1.0F - p) / 0.1F : 1.0F;
         double rr = this.r * (double)Ease.outBack(Math.min(1.0F, p * 12.0F));
         c.at(this.at);

         for (double ny : new double[]{0.05, 0.35, 0.65}) {
            double ring = rr * Math.sqrt(1.0 - ny * ny);
            Draw.ring(c.vc, c.m(), new Vec3(0.0, rr * ny, 0.0), Draw.X, Draw.Z, ring - 0.06, ring, 0, Draw.alpha(-2086852, 0.7F * a), 48);
         }

         for (int k = 0; k < 8; k++) {
            double ang = (double)k * Math.PI / 4.0;
            Vec3 prev = null;

            for (int i = 0; i <= 10; i++) {
               double th = (Math.PI / 2) * (double)i / 10.0;
               Vec3 pt = new Vec3(Math.cos(ang) * Math.cos(th) * rr, Math.sin(th) * rr, Math.sin(ang) * Math.cos(th) * rr);
               if (prev != null) {
                  Draw.ribbon(c.vc, c.m(), prev, pt, 0.03, 0.03, Draw.alpha(-2086852, 0.5F * a), Draw.alpha(-2086852, 0.5F * a), c.camRel(this.at));
               }

               prev = pt;
            }
         }

         c.pop();
      }
   }
}
