package com.geto.csm.client;

import com.geto.csm.entity.CurseEntity;
import com.geto.csm.entity.KuchisakeEntity;
import com.geto.csm.entity.PyreEntity;
import com.geto.csm.entity.TamamoEntity;
import com.geto.csm.entity.WyrmEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class CurseModels {
   static final Vec3 UP = Draw.Y;

   static double time() {
      return (double)Vfx.now() / 1000.0;
   }

   static float yaw(LivingEntity e, float partial) {
      return Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
   }

   static Vec3 tangent(Vec3[] p, int i) {
      Vec3 a = p[Math.max(0, i - 1)];
      Vec3 b = p[Math.min(p.length - 1, i + 1)];
      Vec3 t = b.subtract(a);
      return t.lengthSqr() < 1.0E-10 ? Draw.Z : t.normalize();
   }

   static Vec3 upOf(Vec3 tan) {
      Vec3 u = UP.subtract(tan.scale(UP.dot(tan)));
      return u.lengthSqr() < 1.0E-6 ? Draw.perp(tan) : u.normalize();
   }

   public static double inkWidth(CurseEntity e) {
      return switch (e.kind()) {
         case WYRM, TAMAMO -> 0.05;
         case PYRE -> 0.035;
         case KUCHISAKE -> 0.022;
         default -> throw new IncompatibleClassChangeError();
      };
   }

   public static void draw(CurseEntity e, Vec3 origin, float partial) {
      switch (e.kind()) {
         case WYRM:
            wyrm((WyrmEntity)e, origin, partial);
            break;
         case TAMAMO:
            tamamo((TamamoEntity)e, partial);
            break;
         case PYRE:
            pyre((PyreEntity)e, partial);
            break;
         case KUCHISAKE:
            kuchisake((KuchisakeEntity)e, partial);
      }
   }

   static double wyrmRadius(double u) {
      return u < 0.22 ? Mth.lerp(u / 0.22, 0.62, 0.95) : 0.06 + 0.89 * Math.pow(1.0 - (u - 0.22) / 0.78, 0.85);
   }

   static Vec3[] wyrmSpine(WyrmEntity e, Vec3 origin, float partial) {
      Mesh.Frame f = Mesh.Frame.yaw(yaw(e, partial));
      Vec3 headW = origin.add(0.0, 1.0, 0.0);
      int n = 44;
      Vec3[] raw = SpineTrail.sample(e.getId(), headW.subtract(f.fwd.scale(1.1)), f.fwd.scale(-1.0), n, 0.55);
      double t = time();
      float sweep = CurseAnim.pulse(e.getId(), 2, 800L);
      Vec3[] out = new Vec3[n + 1];
      out[0] = new Vec3(0.0, 1.0, 0.0);

      for (int i = 0; i < n; i++) {
         Vec3 tan = raw[Math.min(n - 1, i + 1)].subtract(raw[Math.max(0, i - 1)]);
         Vec3 side = UP.cross(tan);
         side = side.lengthSqr() < 1.0E-8 ? f.side : side.normalize();
         double amp = 0.12 + (double)i * 0.016;
         double w = Math.sin(t * 2.4 - (double)i * 0.42) * amp + (double)sweep * Math.sin((double)i * 0.22) * 3.0 * ((double)i / (double)n);
         double v = Math.cos(t * 1.9 - (double)i * 0.37) * amp * 0.5;
         out[i + 1] = raw[i].subtract(origin).add(side.scale(w)).add(0.0, v, 0.0);
      }

      return out;
   }

   static void wyrm(WyrmEntity e, Vec3 origin, float partial) {
      Vec3[] p = wyrmSpine(e, origin, partial);
      int n = p.length;
      double[] r = new double[n];

      for (int i = 0; i < n; i++) {
         r[i] = wyrmRadius((double)i / (double)(n - 1));
      }

      Mesh.tube(p, r, 14, (u, nn) -> {
         int band = (int)(u * 90.0) % 2;
         if (nn.y > 0.12) {
            return band == 0 ? -14936269 : -14147510;
         } else if (nn.y < -0.3) {
            return band == 0 ? -2569042 : -4411762;
         } else {
            return -13686967;
         }
      });

      for (int i = 3; i < n - 6; i += 2) {
         double u = (double)i / (double)(n - 1);
         Vec3 tan = tangent(p, i);
         Vec3 up = upOf(tan);
         Vec3 base = p[i].add(up.scale(r[i] * 0.82));
         Vec3 tip = base.add(up.scale(0.12 + 0.6 * (1.0 - u))).add(tan.scale(0.32));
         Mesh.spike(base, tip, 0.13 * (1.1 - u), -1647418);
      }

      int[] legAt = new int[]{8, 25};
      double t = time();

      for (int li = 0; li < legAt.length; li++) {
         int i = legAt[li];
         Vec3 tan = tangent(p, i);
         Vec3 up = upOf(tan);
         Vec3 side = up.cross(tan).normalize();

         for (int s = -1; s <= 1; s += 2) {
            double paddle = Math.sin(t * 2.2 + (double)li * 1.7 + (double)s) * 0.15;
            Vec3 sh = p[i].add(side.scale((double)s * r[i] * 0.75)).add(up.scale(-r[i] * 0.2));
            Vec3 el = sh.add(side.scale((double)s * 0.65)).add(up.scale(-0.35)).add(tan.scale(-0.25 + paddle));
            Vec3 ft = el.add(up.scale(-0.55)).add(tan.scale(-0.35 + paddle));
            Mesh.limb(new Vec3[]{sh, el, ft}, 0.24, 0.11, 9, -14015936);

            for (int c = -1; c <= 1; c++) {
               Vec3 cb = ft.add(side.scale((double)(s * c) * 0.08));
               Mesh.spike(cb, cb.add(tan.scale(-0.3)).add(up.scale(-0.12)).add(side.scale((double)(s * c) * 0.1)), 0.045, -1647418);
            }
         }
      }

      wyrmHead(e, p);
   }

   static void wyrmHead(WyrmEntity e, Vec3[] p) {
      Vec3 H = p[0];
      Vec3 fwdH = H.subtract(p[2]);
      Mesh.Frame hf = Mesh.Frame.of(fwdH.lengthSqr() < 1.0E-6 ? Draw.Z : fwdH, UP);
      double t = time();
      double jaw = 0.1 + 0.05 * Math.sin(t * 2.0) + 0.7 * (double)Math.max(CurseAnim.pulse(e.getId(), 1, 420L), CurseAnim.pulse(e.getId(), 3, 1100L));
      Mesh.blob(H.add(hf.l(0.0, 0.12, 0.2)), hf, 0.62, 0.48, 0.95, (u, n) -> n.y > 0.0 ? -14542024 : -12964016);
      Mesh.blob(H.add(hf.l(0.0, 0.02, 1.12)), hf, 0.42, 0.3, 0.78, (u, n) -> n.y > 0.0 ? -14410436 : -12832686);

      for (int s = -1; s <= 1; s += 2) {
         Mesh.ball(H.add(hf.l((double)s * 0.18, 0.22, 1.75)), 0.06, -15988716);
      }

      Vec3 pivot = H.add(hf.l(0.0, -0.22, 0.1));
      Vec3 jd = hf.fwd.scale(Math.cos(jaw)).subtract(hf.up.scale(Math.sin(jaw)));
      Mesh.Frame jf = Mesh.Frame.of(jd, hf.up);
      Mesh.blob(pivot.add(jd.scale(0.85)), jf, 0.36, 0.17, 0.85, (u, n) -> n.dot(jf.up) > 0.0 ? -10873818 : -3161436);

      for (int s = -1; s <= 1; s += 2) {
         for (int k = 0; k < 6; k++) {
            double z = 0.55 + (double)k * 0.22;
            Vec3 ub = H.add(hf.l((double)s * (0.34 - (double)k * 0.03), -0.18, z));
            Mesh.spike(ub, ub.add(hf.up.scale(-0.22 + (double)k * 0.015)), 0.045, -725284);
            Vec3 lb = pivot.add(jd.scale(0.35 + (double)k * 0.2)).add(jf.side.scale((double)s * (0.28 - (double)k * 0.025))).add(jf.up.scale(0.12));
            Mesh.spike(lb, lb.add(jf.up.scale(0.18)), 0.04, -725284);
         }
      }

      for (int s = -1; s <= 1; s += 2) {
         Mesh.blob(H.add(hf.l((double)s * 0.42, 0.3, 0.6)), hf, 0.1, 0.07, 0.16, Mesh.emissive(16757051));
         Mesh.spike(H.add(hf.l((double)s * 0.4, 0.42, 0.55)), H.add(hf.l((double)s * 0.62, 0.62, 0.1)), 0.08, -1647418);
         Mesh.curve(
            H.add(hf.l((double)s * 0.3, 0.38, -0.1)),
            H.add(hf.l((double)s * 0.45, 0.95, -1.0)),
            H.add(hf.l((double)s * 0.75, 0.45, -2.3)),
            10,
            0.17,
            0.015,
            8,
            (u, n) -> u > 0.75 ? -2848 : -2042692
         );
         double sway = Math.sin(t * 2.3 + (double)s) * 0.35;
         Mesh.curve(
            H.add(hf.l((double)s * 0.38, -0.02, 1.55)),
            H.add(hf.l((double)s * 1.3, 0.2 + sway, 0.9)),
            H.add(hf.l((double)s * 1.2, -0.3 - sway, -1.6)),
            14,
            0.06,
            0.012,
            6,
            Mesh.emissive(16742954)
         );
      }
   }

   static float heatK(PyreEntity e) {
      return (PyreEntity.heat(e.tickCount) - 120.0F) / 2880.0F;
   }

   static int molten(float k) {
      return Draw.lerp(-46576, -3904, k);
   }

   static void pyre(PyreEntity e, float partial) {
      Mesh.Frame f = Mesh.Frame.yaw(yaw(e, partial));
      float k = heatK(e);
      int mc = molten(k);
      double pos = (double)e.walkAnimation.position(partial);
      double spd = (double)Math.min(1.0F, e.walkAnimation.speed(partial));
      double swing = Math.sin(pos * 0.66) * spd * 0.6;
      float flare = CurseAnim.pulse(e.getId(), 1, 650L);
      Mesh.Paint ash = (u, n) -> {
         double v = Math.sin(n.x * 11.0 + n.y * 7.3 + u * 9.0) * Math.sin(n.z * 9.1 - n.y * 5.7 + u * 4.0);
         if (Math.abs(v) < 0.06 + (double)k * 0.1) {
            return Mesh.emissive(mc);
         } else {
            return n.y > 0.4 ? -14015456 : -14804456;
         }
      };

      for (int s = -1; s <= 1; s += 2) {
         double ph = (double)s * swing;
         Vec3 hip = f.l((double)s * 0.24, 1.15, -0.05);
         Vec3 knee = f.l((double)s * 0.3, 0.68, 0.28 + ph * 0.4);
         Vec3 ankle = f.l((double)s * 0.28, 0.24, -0.12 + ph * 0.2);
         Vec3 toe = f.l((double)s * 0.28, 0.03, 0.28 + ph * 0.35);
         Mesh.limb(new Vec3[]{hip, knee, ankle, toe}, 0.16, 0.07, 9, ash);
      }

      Mesh.blob(f.l(0.0, 1.15, 0.0), f, 0.32, 0.2, 0.26, ash);
      Mesh.Frame tf = f.pitch(-0.45 - (double)flare * 0.25);
      Vec3 torso = f.l(0.0, 1.62, 0.18);
      Mesh.blob(torso, tf, 0.46, 0.62, 0.36, ash);

      for (int j = 0; j < 4; j++) {
         Mesh.blob(torso.add(tf.l(0.0, 0.3 - (double)j * 0.18, 0.3)), tf, 0.36 - (double)j * 0.03, 0.045, 0.08, -13357012);
      }

      Vec3 neckTop = f.l(0.0, 2.22, 0.58 + (double)flare * 0.1);
      Mesh.limb(new Vec3[]{torso.add(tf.l(0.0, 0.5, 0.05)), neckTop}, 0.15, 0.12, 8, ash);
      Mesh.Frame hf = f.pitch(-0.2 + (double)flare * 0.4);
      Vec3 H = neckTop.add(hf.l(0.0, 0.12, 0.06));
      Mesh.blob(H, hf, 0.26, 0.3, 0.3, ash);
      Mesh.blob(H.add(hf.l(0.0, 0.02, 0.28)), hf, 0.035, 0.17, 0.025, Mesh.emissive(mc));

      for (int s = -1; s <= 1; s += 2) {
         double sw = -swing * (double)s;
         Vec3 sh = f.l((double)s * 0.5, 1.98, 0.22);
         Vec3 el = f.l((double)s * 0.66, 1.4 + (double)flare * 0.6, 0.42 + sw * 0.3 + (double)flare * 0.5);
         Vec3 wr = f.l((double)s * 0.56, 0.86 + (double)flare * 1.0, 0.52 + sw * 0.2 + (double)flare * 0.9);
         Mesh.limb(new Vec3[]{sh, el, wr}, 0.14, 0.07, 9, ash);

         for (int c = -1; c <= 1; c++) {
            Vec3 cb = wr.add(f.side.scale((double)(s * c) * 0.06));
            Mesh.spike(cb, cb.add(f.l((double)(s * c) * 0.07, -0.32 + (double)flare * 0.3, 0.16 + (double)flare * 0.2)), 0.035, Mesh.emissive(mc));
         }
      }
   }

   static void kuchisake(KuchisakeEntity e, float partial) {
      Mesh.Frame f = Mesh.Frame.yaw(yaw(e, partial));
      double t = time();
      double pos = (double)e.walkAnimation.position(partial);
      double spd = (double)Math.min(1.0F, e.walkAnimation.speed(partial));
      double swing = Math.sin(pos * 0.66) * spd * 0.5;
      float snip = CurseAnim.pulse(e.getId(), 1, 520L);
      double confused = (double)CurseAnim.pulse(e.getId(), 4, 3000L) * 0.45;
      int skin = -1055526;
      int coat = -5238230;

      for (int s = -1; s <= 1; s += 2) {
         double ph = (double)s * swing;
         Mesh.blob(f.l((double)s * 0.13, 0.06, 0.08 + ph * 0.25), f, 0.08, 0.06, 0.15, -15593452);
         Mesh.limb(new Vec3[]{f.l((double)s * 0.12, 0.08, 0.02 + ph * 0.2), f.l((double)s * 0.12, 0.48, 0.0)}, 0.06, 0.065, 7, skin);
      }

      double[] ys = new double[]{0.35, 0.75, 1.2, 1.6, 1.95, 2.25, 2.42};
      double[] rs = new double[]{0.66, 0.52, 0.38, 0.31, 0.33, 0.37, 0.2};
      Vec3[] coatPts = new Vec3[ys.length];

      for (int j = 0; j < ys.length; j++) {
         double hem = 1.0 - (double)j / 6.0;
         coatPts[j] = f.l(Math.sin(t * 1.7) * 0.06 * hem, ys[j], -swing * 0.12 * hem);
      }

      Mesh.tube(coatPts, rs, 18, (u, n) -> Draw.scaleRgb(coat, 0.84F + 0.16F * (float)Math.sin(Math.atan2(n.z, n.x) * 9.0 + u * 2.0)));
      Mesh.torus(f.l(0.0, 1.22, 0.0), f.side, f.fwd, 0.385, 0.045, 22, -11925998);

      for (int j = 0; j < 3; j++) {
         Mesh.ball(f.l(0.0, 1.5 + (double)j * 0.2, 0.33 - (double)j * 0.005), 0.03, -15462382);
      }

      Mesh.blob(f.l(0.0, 2.37, 0.0), f, 0.3, 0.08, 0.26, -7730144);
      Mesh.limb(new Vec3[]{f.l(0.0, 2.38, 0.0), f.l(0.0, 2.53, 0.02)}, 0.07, 0.065, 7, skin);
      Mesh.Frame hf = Mesh.Frame.of(f.fwd, f.up.scale(Math.cos(confused)).add(f.side.scale(Math.sin(confused))));
      Vec3 H = f.l(0.0, 2.7, 0.03);
      Mesh.blob(H, hf, 0.2, 0.26, 0.22, skin);

      for (int s = -1; s <= 1; s += 2) {
         Mesh.blob(H.add(hf.l((double)s * 0.075, 0.04, 0.19)), hf, 0.045, 0.022, 0.02, -15463922);
         Mesh.ball(H.add(hf.l((double)s * 0.075, 0.04, 0.208)), 0.012, Mesh.emissive(16722490));
      }

      if (!e.unmasked()) {
         Mesh.blob(H.add(hf.l(0.0, -0.08, 0.13)), hf, 0.17, 0.11, 0.1, -657934);

         for (int s = -1; s <= 1; s += 2) {
            Mesh.curve(
               H.add(hf.l((double)s * 0.16, -0.06, 0.1)),
               H.add(hf.l((double)s * 0.23, 0.0, 0.02)),
               H.add(hf.l((double)s * 0.2, 0.03, -0.04)),
               6,
               0.01,
               0.01,
               4,
               -657934
            );
         }
      } else {
         Vec3[] slit = Mesh.bezier(H.add(hf.l(-0.19, -0.05, 0.08)), H.add(hf.l(0.0, -0.13, 0.33)), H.add(hf.l(0.19, -0.05, 0.08)), 12);
         Mesh.limb(slit, 0.022, 0.022, 6, Mesh.emissive(6946840));

         for (int k = 2; k <= 10; k++) {
            Mesh.ball(slit[k].add(hf.up.scale(0.018)), 0.012, -725276);
         }
      }

      for (int k = 0; k < 28; k++) {
         double a = -2.3 + 4.6 * (double)k / 27.0;
         double sway = Math.sin(t * 1.4 + (double)k) * 0.05;
         Vec3 root = H.add(hf.l(Math.sin(a) * 0.19, 0.17 - Math.abs(a) * 0.02, -Math.cos(a) * 0.17 + 0.02));
         Vec3 ctrl = H.add(hf.l(Math.sin(a) * 0.3, -0.2, -Math.cos(a) * 0.32));
         Vec3 end = H.add(hf.l(Math.sin(a) * 0.36 + sway, -1.15 - Draw.hash((long)k) * 0.3, -Math.cos(a) * 0.32 - 0.1 - swing * 0.08));
         Mesh.curve(root, ctrl, end, 8, 0.045, 0.008, 5, -16053742);
      }

      for (int k = 0; k < 7; k++) {
         double x = -0.15 + (double)k * 0.05;
         Mesh.curve(H.add(hf.l(x, 0.22, 0.1)), H.add(hf.l(x * 1.05, 0.18, 0.22)), H.add(hf.l(x * 1.1, 0.05, 0.215)), 5, 0.03, 0.008, 4, -16053742);
      }

      Mesh.limb(new Vec3[]{f.l(0.36, 2.22, 0.0), f.l(0.44, 1.7, 0.04 - swing * 0.2), f.l(0.38, 1.26, 0.12)}, 0.1, 0.08, 8, coat);
      Mesh.ball(f.l(0.38, 1.18, 0.14), 0.06, skin);
      Vec3 wr = f.l(-0.4, 1.55 + (double)snip * 0.4, 0.62 + (double)snip * 0.25);
      Mesh.limb(new Vec3[]{f.l(-0.36, 2.22, 0.0), f.l(-0.46, 1.82 + (double)snip * 0.25, 0.3), wr}, 0.1, 0.08, 8, coat);
      Mesh.ball(wr, 0.06, skin);
      double oa = 0.14 + 0.5 * (double)snip + 0.04 * Math.sin(t * 3.0);
      shears(wr, f.fwd.scale(0.85).subtract(f.up.scale(0.3 - (double)snip * 0.4)).normalize(), f.side, oa, 1.0);
   }

   public static void shears(Vec3 hand, Vec3 dir, Vec3 perpHint, double open, double scale) {
      Vec3 sd = dir.normalize();
      Vec3 perp = perpHint.subtract(sd.scale(perpHint.dot(sd))).normalize();
      Vec3 pivot = hand.add(sd.scale(0.25 * scale));

      for (int s = -1; s <= 1; s += 2) {
         Vec3 b = sd.scale(Math.cos(open)).add(perp.scale((double)s * Math.sin(open))).normalize();
         Vec3 thick = b.cross(perp).normalize();
         Vec3 wdir = thick.cross(b).normalize();
         Mesh.ellipsoid(
            pivot.add(b.scale(0.78 * scale)),
            b.scale(0.8 * scale),
            wdir.scale(0.085 * scale),
            thick.scale(0.022 * scale),
            6,
            12,
            (u, n) -> Math.abs(n.dot(wdir)) > 0.6 ? -854792 : -5327938
         );
         Vec3 hc = pivot.subtract(b.scale(0.32 * scale));
         Mesh.torus(hc, b, wdir, 0.12 * scale, 0.032 * scale, 14, -15330278);
      }

      Mesh.ball(pivot, 0.045 * scale, -14014416);
   }

   static void tamamo(TamamoEntity e, float partial) {
      Mesh.Frame f = Mesh.Frame.yaw(yaw(e, partial));
      double t = time();
      float sweep = CurseAnim.pulse(e.getId(), 2, 700L);
      float stone = CurseAnim.pulse(e.getId(), 3, 1100L);
      float cast = CurseAnim.pulse(e.getId(), 1, 500L);
      Vec3 o = new Vec3(0.0, Math.sin(t * 1.3) * 0.25, 0.0);
      int gold = -868018;
      int ivory = -790554;
      int shadow = -2436376;
      int verm = -2080726;
      Mesh.Paint fur = (ux, n) -> n.y < -0.35 ? shadow : ivory;
      Mesh.blob(o.add(f.l(0.0, 1.4, 0.0)), f, 0.66, 0.62, 1.35, fur);
      Mesh.blob(o.add(f.l(0.0, 1.55, 1.0)), f, 0.6, 0.72, 0.7, fur);
      Mesh.blob(o.add(f.l(0.0, 1.45, -1.0)), f, 0.62, 0.66, 0.72, fur);

      for (int k = 0; k < 14; k++) {
         double a = (double)k / 14.0 * Math.PI * 2.0;
         Vec3 dir = f.side.scale(Math.cos(a)).add(f.up.scale(Math.sin(a)));
         Vec3 base = o.add(f.l(0.0, 1.75, 1.25)).add(dir.scale(0.55));
         Mesh.spike(base, base.add(dir.scale(0.45)).subtract(f.fwd.scale(0.35)), 0.13, ivory);
      }

      Vec3 H = o.add(f.l(0.0, 2.6 + (double)stone * 0.25, 1.92));
      Mesh.limb(new Vec3[]{o.add(f.l(0.0, 1.85, 1.25)), o.add(f.l(0.0, 2.2, 1.55)), H.add(f.l(0.0, -0.15, -0.14))}, 0.38, 0.3, 12, fur);
      Mesh.Frame hf = f.pitch((double)stone * 0.35 + (double)cast * 0.15);
      Mesh.blob(H, hf, 0.42, 0.38, 0.48, fur);
      Mesh.blob(H.add(hf.l(0.0, -0.1, 0.5)), hf, 0.2, 0.17, 0.4, fur);
      Mesh.ball(H.add(hf.l(0.0, -0.06, 0.9)), 0.07, -15068136);
      Mesh.blob(H.add(hf.l(0.0, -0.2 - (double)cast * 0.05, 0.55)), hf, 0.12, 0.015 + (double)cast * 0.04, 0.25, -14018016);

      for (int s = -1; s <= 1; s += 2) {
         Mesh.blob(H.add(hf.l((double)s * 0.2, 0.08, 0.38)), hf, 0.09, 0.028, 0.05, Mesh.emissive(gold));
         Mesh.blob(H.add(hf.l((double)s * 0.22, 0.17, 0.33)), hf, 0.13, 0.02, 0.035, verm);
         Mesh.blob(H.add(hf.l((double)s * 0.3, -0.05, 0.25)), hf, 0.1, 0.02, 0.045, verm);
         Mesh.limb(
            new Vec3[]{H.add(hf.l((double)s * 0.24, 0.28, 0.05)), H.add(hf.l((double)s * 0.31, 0.6, 0.0)), H.add(hf.l((double)s * 0.35, 0.86, -0.08))},
            0.17,
            0.012,
            8,
            (ux, n) -> ux > 0.7 ? gold : ivory
         );
      }

      Mesh.ball(H.add(hf.l(0.0, 0.3, 0.36)), 0.06, Mesh.emissive(gold));
      int idx = 0;

      for (int s = -1; s <= 1; s += 2) {
         for (int fr = -1; fr <= 1; fr += 2) {
            double ph = t * 2.0 + (double)(idx++);
            Vec3 hip = o.add(f.l((double)s * 0.4, 1.22, (double)fr * 1.05));
            Vec3 knee = hip.add(f.l((double)s * 0.05, -0.55, 0.15 * Math.sin(ph)));
            Vec3 paw = knee.add(f.l(0.0, -0.55, 0.25 + 0.15 * Math.sin(ph + 1.0)));
            Mesh.limb(new Vec3[]{hip, knee, paw}, 0.2, 0.11, 10, fur);
            Mesh.ball(paw, 0.13, ivory);
         }
      }

      Vec3 R = o.add(f.l(0.0, 1.65, -1.75));

      for (int i = 0; i < 9; i++) {
         double a = (-1.0 + (double)(2 * i) / 8.0) * 1.25;
         Vec3 sdir = f.side.scale(Math.sin(a)).add(f.up.scale(Math.cos(a)));
         double sway = Math.sin(t * 1.4 + (double)i * 0.7) * 0.4 + (double)sweep * 2.0 * Math.sin((double)i * 1.3 + t * 8.0);
         Vec3 p1 = R.add(f.fwd.scale(-1.3)).add(sdir.scale(0.9)).add(f.side.scale(sway * 0.3));
         Vec3 p2 = R.add(f.fwd.scale(-2.2)).add(sdir.scale(2.0)).add(f.side.scale(sway * 0.8)).add(f.up.scale(0.3));
         Vec3 tip = R.add(f.fwd.scale(-2.6)).add(sdir.scale(3.3)).add(f.side.scale(sway * 1.3)).add(f.up.scale(0.6));
         Vec3[] pts = Mesh.bezier3(R, p1, p2, tip, 16);
         double[] rr = new double[pts.length];

         for (int k = 0; k < pts.length; k++) {
            double u = (double)k / (double)(pts.length - 1);
            double body = 0.2 + 0.3 * Math.sin(Math.PI * Math.min(0.999, u * 0.95 + 0.05));
            rr[k] = body * (u > 0.8 ? (1.0 - u) / 0.2 : 1.0) + 0.02;
         }

         Mesh.tube(pts, rr, 12, (ux, n) -> ux > 0.88 ? Mesh.emissive(gold) : (ux > 0.74 ? gold : (n.y < -0.35 ? shadow : ivory)));
      }
   }

   public static void glow(CurseEntity e, Vec3 origin, float partial, VertexConsumer vc, Matrix4f m, Vec3 camRel, Vfx.Ctx c) {
      double t = time();
      switch (e.kind()) {
         case WYRM:
            Vec3[] p = wyrmSpine((WyrmEntity)e, origin, partial);

            for (int i = 4; i < p.length - 4; i += 3) {
               Vec3 tan = tangent(p, i);
               Vec3 up = upOf(tan);
               Vec3 side = up.cross(tan).normalize();
               double r = wyrmRadius((double)i / (double)(p.length - 1)) + 0.03;
               float a = 0.45F + 0.25F * (float)Math.sin(t * 3.0 + (double)i);
               Draw.ring(vc, m, p[i], side, up, r - 0.04, r + 0.03, Draw.alpha(-34262, a), Draw.alpha(-34262, 0.0F), 20);
            }

            Vec3 H = p[0];
            Mesh.Frame hf = Mesh.Frame.of(H.subtract(p[2]), UP);

            for (int s = -1; s <= 1; s += 2) {
               Draw.glowOrb(
                  vc,
                  m,
                  H.add(hf.l((double)s * 0.45, 0.3, 0.65)).x,
                  H.add(hf.l((double)s * 0.45, 0.3, 0.65)).y,
                  H.add(hf.l((double)s * 0.45, 0.3, 0.65)).z,
                  0.22,
                  -20165,
                  0.7F
               );
            }

            float roar = CurseAnim.pulse(e.getId(), 3, 1100L);
            if (roar > 0.0F) {
               Vec3 mouth = H.add(hf.l(0.0, -0.2, 1.3));
               Draw.glowOrb(vc, m, mouth.x, mouth.y, mouth.z, 0.5 * (double)roar, -6595329, roar);
            }
            break;
         case TAMAMO:
            Mesh.Frame f = Mesh.Frame.yaw(yaw(e, partial));

            for (int k = 0; k < 4; k++) {
               double ang = t * 1.6 + (double)k * Math.PI / 2.0;
               Vec3 fp = f.l(Math.cos(ang) * 2.8, 2.0 + Math.sin(t * 2.0 + (double)k) * 0.4, Math.sin(ang) * 2.8);
               Vec3 trailEnd = f.l(Math.cos(ang - 0.5) * 2.8, 2.0 + Math.sin(t * 2.0 + (double)k - 0.3) * 0.4, Math.sin(ang - 0.5) * 2.8);
               Draw.glowOrb(vc, m, fp.x, fp.y, fp.z, 0.22, -6297857, 0.8F);
               Draw.ribbon(vc, m, trailEnd, fp, 0.0, 0.15, 0, Draw.alpha(-6297857, 0.6F), camRel);
            }

            Vec3 H = f.l(0.0, 2.6, 1.92).add(0.0, Math.sin(t * 1.3) * 0.25, 0.0);

            for (int s = -1; s <= 1; s += 2) {
               Vec3 eye = H.add(f.l((double)s * 0.2, 0.08, 0.38));
               Draw.glowOrb(vc, m, eye.x, eye.y, eye.z, 0.12, -868018, 0.6F);
            }
            break;
         case PYRE:
            pyreFlames((PyreEntity)e, partial, vc, m, camRel, c);
            break;
         case KUCHISAKE:
            KuchisakeEntity k = (KuchisakeEntity)e;
            Mesh.Frame f = Mesh.Frame.yaw(yaw(e, partial));
            Vec3 H = f.l(0.0, 2.72, 0.2);
            float a = k.unmasked() ? 0.6F : 0.3F;

            for (int s = -1; s <= 1; s += 2) {
               Draw.glowOrb(vc, m, H.x + f.side.x * (double)s * 0.075, H.y + 0.02, H.z + f.side.z * (double)s * 0.075, 0.05, -54726, a);
            }
      }
   }

   static void pyreFlames(PyreEntity e, float partial, VertexConsumer vc, Matrix4f m, Vec3 camRel, Vfx.Ctx c) {
      float k = heatK(e);
      int mc = molten(k);
      Mesh.Frame f = Mesh.Frame.yaw(yaw(e, partial));
      double t = time();
      int tongues = 10 + (int)(k * 16.0F);
      long frame = Vfx.now() / 45L;

      for (int i = 0; i < tongues; i++) {
         double a = (double)i * 2.399;
         double rr = 0.25 + Draw.hash((long)i) * 0.35;
         Vec3 base = f.l(Math.cos(a) * rr, 1.5 + Draw.hash((long)(i * 3)) * 0.7, Math.sin(a) * rr * 0.7 + 0.1);
         double h = (0.45 + 2.6 * (double)k) * (0.6 + 0.4 * Draw.hash(frame + (long)(i * 13)));
         Vec3 tip = base.add(Math.sin(t * 5.0 + (double)i) * 0.15, h, Math.cos(t * 4.0 + (double)i) * 0.15);
         Draw.ribbon(vc, m, base, tip, 0.12 + 0.12 * (double)k, 0.0, Draw.alpha(-1, 0.55F), Draw.alpha(mc, 0.0F), camRel);
         Draw.ribbon(vc, m, base, tip.add(0.0, h * 0.2, 0.0), 0.25 + 0.2 * (double)k, 0.0, Draw.alpha(mc, 0.35F), Draw.alpha(-54784, 0.0F), camRel);
      }

      Draw.glowOrb(vc, m, 0.0, 1.7, 0.2, 0.6 + (double)k * 0.9, mc, 0.25F + 0.25F * k);
      double aura = 2.0 + (double)PyreEntity.heat(e.tickCount) / 600.0;
      Draw.ringXZ(vc, m, new Vec3(0.0, 0.06, 0.0), aura - 0.3, aura, 0, Draw.alpha(mc, 0.35F), 48);
      Draw.ringXZ(vc, m, new Vec3(0.0, 0.05, 0.0), 0.0, aura, Draw.alpha(mc, 0.12F), 0, 48);
   }

   private CurseModels() {
   }
}
