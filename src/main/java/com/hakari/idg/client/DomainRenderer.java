package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class DomainRenderer {
   private static final Map<Integer, DomainRenderer.Visual> VISUALS = new HashMap<>();
   private static final double TRACK_Z = 8.0;
   private static final long TRAIN_MS = 2200L;

   private static DomainRenderer.Visual visual(int caster) {
      return VISUALS.computeIfAbsent(caster, k -> new DomainRenderer.Visual());
   }

   public static void train(int caster) {
      DomainRenderer.Visual v = visual(caster);
      v.trainStart = Vfx.now();
      v.lastAutoTrain = v.trainStart;
   }

   public static void clear() {
      VISUALS.clear();
   }

   public static void solid(Vfx.Ctx c) {
      for (StatePacket s : ClientState.all()) {
         if (s.domainActive || s.castTicks > 0) {
            DomainRenderer.Visual v = visual(s.entityId);
            captureYaw(s, v);
            float e = ClientState.castElapsedSeconds(s);
            Vec3 center = ClientState.center(s);
            double R = (double)s.radius;
            c.at(center);
            Matrix4f m = c.m();
            Vec3 camRel = c.camRel(center);
            if (e >= 2.45F && e < 3.95F) {
               float f = Mth.clamp((e - 2.45F) / 1.45F, 0.0F, 1.0F);
               double r = R * (double)Ease.outExpo(f);
               fresnelSphere(c.vc, m, r, camRel, -16449531, -45128);
            } else if (e >= 3.95F) {
               if (v.openedAt < 0L) {
                  v.openedAt = c.t;
               }

               autoTrain(s, v, c.t, center);
               interiorSolid(c, s, v, R, camRel);
            }

            c.pop();
         }
      }
   }

   public static void glow(Vfx.Ctx c) {
      for (StatePacket s : ClientState.all()) {
         if (s.domainActive || s.castTicks > 0) {
            DomainRenderer.Visual v = visual(s.entityId);
            float e = ClientState.castElapsedSeconds(s);
            Vec3 center = ClientState.center(s);
            double R = (double)s.radius;
            c.at(center);
            if (e >= 2.45F && e < 3.95F) {
               float f = Mth.clamp((e - 2.45F) / 1.45F, 0.0F, 1.0F);
               double r = R * (double)Ease.outExpo(f);
               Draw.sphere(c.vc, c.m(), 0.0, 0.0, 0.0, r * 1.03, 16, 24, Draw.alpha(-53894, 0.18F * (1.0F - f * 0.5F)));
               Draw.ringXZ(c.vc, c.m(), new Vec3(0.0, 0.1, 0.0), r * 0.98, r * 1.15, Draw.alpha(-45128, 0.9F), 0, 64);
            } else if (e >= 3.95F) {
               interiorGlow(c, s, v, R);
            }

            c.pop();
         }

         if (s.jackpotTicks > 0 || s.jackpotCutTicks > 0) {
            aura(c, s);
         }
      }
   }

   public static void text(Vfx.Ctx c, BufferSource buffers) {
      Font font = Minecraft.getInstance().font;

      for (StatePacket s : ClientState.all()) {
         if ((s.domainActive || s.castTicks > 0) && !(ClientState.castElapsedSeconds(s) < 3.95F)) {
            Vec3 center = ClientState.center(s);
            double R = (double)s.radius;
            double spin = (double)c.t / 9000.0;
            String[] glyphs = new String[]{"坐", "殺", "博", "徒"};

            for (int i = 0; i < 4; i++) {
               double a = spin + (double)i * Math.PI / 2.0;
               Vec3 p = center.add(Math.cos(a) * R * 0.86, R * 0.42, Math.sin(a) * R * 0.86);
               billboardText(c, font, buffers, glyphs[i], p, 0.55F, -45128);
            }

            DomainRenderer.Visual v = visual(s.entityId);
            Vec3 mpos = machinePos(center, v);
            billboardText(c, font, buffers, "IDLE DEATH GAMBLE", mpos.add(0.0, 4.4, 0.0), 0.09F, -11443);
            billboardText(c, font, buffers, "CR 私鉄純愛列車", mpos.add(0.0, 3.7, 0.0), 0.07F, -1);

            for (int side = -1; side <= 1; side += 2) {
               Vec3 sign = center.add((double)(side * 9), 3.9, 4.8);
               billboardText(c, font, buffers, "純愛駅", sign, 0.06F, -15073264);
               billboardText(c, font, buffers, "PURE LOVE STA.", sign.add(0.0, -0.45, 0.0), 0.025F, -15073264);
            }
         }
      }

      buffers.endBatch();
   }

   private static void captureYaw(StatePacket s, DomainRenderer.Visual v) {
      if (Float.isNaN(v.yaw)) {
         Entity e = Vfx.entity(s.entityId);
         v.yaw = e != null ? e.getYRot() : 0.0F;
      }
   }

   private static Vec3 machineLocal(DomainRenderer.Visual v) {
      float y = (Float.isNaN(v.yaw) ? 0.0F : v.yaw) * (float) (Math.PI / 180.0);
      return new Vec3((double)(-Mth.sin(y) * 9.0F), 14.6, (double)(Mth.cos(y) * 9.0F));
   }

   private static Vec3 machinePos(Vec3 center, DomainRenderer.Visual v) {
      return center.add(machineLocal(v));
   }

   private static void autoTrain(StatePacket s, DomainRenderer.Visual v, long t, Vec3 center) {
      if (s.domainActive && t - v.lastAutoTrain > 14000L) {
         v.trainStart = t;
         v.lastAutoTrain = t;
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.player.position().distanceTo(center) < (double)(s.radius + 4.0F)) {
            Sfx.play((SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 0.6F);
            Sfx.later(160L, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 0.6F);
            Sfx.later(900L, SoundEvents.ENDER_DRAGON_FLAP, 0.5F, 0.8F);
         }
      }
   }

   private static void fresnelSphere(VertexConsumer vc, Matrix4f m, double r, Vec3 camRel, int core, int rim) {
      Draw.sphere(vc, m, 0.0, 0.0, 0.0, r, 24, 36, (nx, ny, nz) -> {
         Vec3 p = new Vec3(nx * r, ny * r, nz * r);
         Vec3 view = p.subtract(camRel).normalize();
         double facing = Math.abs(nx * view.x + ny * view.y + nz * view.z);
         float edge = (float)Math.pow(1.0 - facing, 3.0);
         return Draw.lerp(core, rim, edge);
      });
   }

   private static void interiorSolid(Vfx.Ctx c, StatePacket s, DomainRenderer.Visual v, double R, Vec3 camRel) {
      VertexConsumer vc = c.vc;
      Matrix4f m = c.m();
      boolean inside = camRel.length() < R;
      if (!inside) {
         fresnelSphere(vc, m, R, camRel, -16449531, -45128);
      } else {
         Draw.sphere(
            vc,
            m,
            0.0,
            0.0,
            0.0,
            R,
            24,
            40,
            (nx, ny, nz) -> ny > 0.0 ? Draw.lerp(-10876872, -15859702, (float)Math.pow(ny, 0.6)) : Draw.lerp(-10876872, -16252924, (float)(-ny) * 3.0F)
         );
         Draw.ringXZ(vc, m, new Vec3(0.0, 0.02, 0.0), 0.0, R, -15070698, -15989750, 64);
         platform(vc, m, R, 3.0, 6.2, true);
         platform(vc, m, R, 9.8, 13.0, false);
         double half = Math.sqrt(Math.max(0.0, R * R - 64.0));

         for (double ml = -half; ml < half; ml += 0.8) {
            Draw.box(vc, m, ml, 0.02, 6.7, ml + 0.3, 0.14, 9.3, -12966368);
         }

         Draw.box(vc, m, -half, 0.14, 7.2, half, 0.3, 7.3, -4670266);
         Draw.box(vc, m, -half, 0.14, 8.7, half, 0.3, 8.8, -4670266);

         for (int side = -1; side <= 1; side += 2) {
            double sx = (double)(side * 9);
            Draw.box(vc, m, sx - 0.12, 0.5, 4.7, sx + 0.12, 3.5, 4.9, -12831680);
            Draw.box(vc, m, sx - 1.6, 3.5, 4.75, sx + 1.6, 4.3, 4.85, -855820);
            Draw.box(vc, m, sx - 1.6, 3.5, 4.73, sx + 1.6, 3.6, 4.87, -45128);
         }

         for (double x = -half + 3.0; x < half - 2.0; x += 6.0) {
            Draw.box(vc, m, x - 0.25, 0.5, 11.5, x + 0.25, 6.0, 12.0, -14015952);
            Draw.box(vc, m, x - 0.25, 0.5, 4.0, x + 0.25, 6.0, 4.5, -14015952);
         }

         Draw.box(vc, m, -half + 1.0, 6.0, 3.5999999999999996, half - 1.0, 6.3, 12.4, -14937056);
         if (v.trainStart >= 0L) {
            train(vc, m, c.t - v.trainStart, R);
         }

         Vec3 ml = machineLocal(v);
         c.ps.pushPose();
         c.ps.translate(ml.x, ml.y, ml.z);
         c.ps.mulPose(Axis.YP.rotationDegrees(-v.yaw));
         machine(c, ReelSet.of(s.entityId));
         c.ps.popPose();
         balls(c, R, true);
      }
   }

   private static void platform(VertexConsumer vc, Matrix4f m, double R, double z0, double z1, boolean edgeHigh) {
      double zz = Math.max(Math.abs(z0), Math.abs(z1));
      double half = Math.sqrt(Math.max(0.0, R * R - zz * zz));
      Draw.box(vc, m, -half, 0.0, z0, half, 0.5, z1, -4606786);
      double ez0 = edgeHigh ? z1 - 0.5 : z0 + 0.1;
      double ez1 = edgeHigh ? z1 - 0.1 : z0 + 0.5;
      Draw.box(vc, m, -half, 0.5, ez0, half, 0.52, ez1, -867792);
   }

   private static void train(VertexConsumer vc, Matrix4f m, long age, double R) {
      if (age <= 2200L) {
         double span = 2.0 * R + 80.0;
         double head = -R - 40.0 + span * ((double)age / 2200.0);

         for (int car = 0; car < 4; car++) {
            double x1 = head - (double)car * 8.4;
            double x0 = x1 - 8.0;
            double z0 = 6.55;
            double z1 = 9.45;
            Draw.box(vc, m, x0, 0.4, z0, x1, 3.4, z1, -724750);
            Draw.box(vc, m, x0, 1.0, z0 - 0.02, x1, 1.3, z1 + 0.02, -45128);
            Draw.box(vc, m, x0, 3.4, z0 + 0.2, x1, 3.6, z1 - 0.2, -5199692);

            for (double wx = x0 + 0.6; wx < x1 - 1.0; wx++) {
               Draw.box(vc, m, wx, 1.6, z0 - 0.03, wx + 1.0, 2.7, z1 + 0.03, -14016986);
            }

            Draw.box(vc, m, x0, 0.3, z0 + 0.3, x1, 0.45, z1 - 0.3, -14671836);
         }

         Draw.box(vc, m, head, 0.4, 6.7, head + 0.6, 3.1, 9.3, -1514778);
      }
   }

   private static void machine(Vfx.Ctx c, ReelSet reels) {
      VertexConsumer vc = c.vc;
      Matrix4f m = c.m();
      Draw.box(vc, m, -5.6, -3.4, 0.0, 5.6, 3.4, 1.4, -14022624);
      Draw.box(vc, m, -4.6, -2.0, -0.25, 4.6, -1.8, 0.05, -11443);
      Draw.box(vc, m, -4.6, 1.8, -0.25, 4.6, 2.0, 0.05, -11443);
      Draw.box(vc, m, -4.6, -2.0, -0.25, -4.4, 2.0, 0.05, -11443);
      Draw.box(vc, m, 4.4, -2.0, -0.25, 4.6, 2.0, 0.05, -11443);
      Draw.box(vc, m, -5.6, 3.4, -0.1, 5.6, 4.0, 1.2, -45128);
      Draw.box(vc, m, -2.0, -3.4, -0.6, 2.0, -2.6, 0.2, -3563472);
      long t = c.t;

      for (int r = 0; r < 3; r++) {
         double rx = (double)(r - 1) * 2.9;
         double pos = reels.position(r, t);
         int segs = 21;
         double rad = 1.55;

         for (int i = 0; i < segs; i++) {
            double a0 = (double)i / (double)segs * Math.PI * 2.0 - pos * Math.PI * 2.0 / 7.0;
            double a1 = (double)(i + 1) / (double)segs * Math.PI * 2.0 - pos * Math.PI * 2.0 / 7.0;
            float shade = (float)Math.max(0.25, -Math.cos((a0 + a1) / 2.0) * -1.0);
            int col = Draw.scaleRgb(i / 3 % 2 == 0 ? -593692 : -1647413, 0.35F + 0.65F * (float)Math.max(0.0, Math.cos((a0 + a1) / 2.0)));
            Vec3 p0 = new Vec3(rx - 1.1, Math.sin(a0) * rad, 0.4 - Math.cos(a0) * rad);
            Vec3 p1 = new Vec3(rx + 1.1, Math.sin(a0) * rad, 0.4 - Math.cos(a0) * rad);
            Vec3 p2 = new Vec3(rx + 1.1, Math.sin(a1) * rad, 0.4 - Math.cos(a1) * rad);
            Vec3 p3 = new Vec3(rx - 1.1, Math.sin(a1) * rad, 0.4 - Math.cos(a1) * rad);
            Draw.quad(vc, m, p0, p1, p2, p3, col);
         }
      }
   }

   private static void seg7(VertexConsumer vc, Matrix4f m, int digit, double size, int col) {
      int[] masks = new int[]{63, 6, 91, 79, 102, 109, 125, 7, 127, 111};
      int mask = masks[Mth.clamp(digit, 0, 9)];
      double w = size * 0.5;
      double t = size * 0.13;
      double[][] seg = new double[][]{
         {-w, size - t, w, size},
         {w - t, 0.0, w, size},
         {w - t, -size, w, 0.0},
         {-w, -size, w, -size + t},
         {-w, -size, -w + t, 0.0},
         {-w, 0.0, -w + t, size},
         {-w, -t / 2.0, w, t / 2.0}
      };

      for (int i = 0; i < 7; i++) {
         if ((mask & 1 << i) != 0) {
            double[] s = seg[i];
            Draw.quad(vc, m, new Vec3(-s[0], s[1], 0.0), new Vec3(-s[2], s[1], 0.0), new Vec3(-s[2], s[3], 0.0), new Vec3(-s[0], s[3], 0.0), col);
         }
      }
   }

   private static void interiorGlow(Vfx.Ctx c, StatePacket s, DomainRenderer.Visual v, double R) {
      VertexConsumer vc = c.vc;
      Matrix4f m = c.m();
      long t = c.t;
      float pulse = 0.65F + 0.35F * Mth.sin((float)t / 300.0F);

      for (double ny : new double[]{0.15, 0.4, 0.65, 0.85}) {
         double rr = R * Math.sqrt(1.0 - ny * ny) * 0.995;
         Draw.ring(vc, m, new Vec3(0.0, R * ny * 0.995, 0.0), Draw.X, Draw.Z, rr - 0.12, rr, 0, Draw.alpha(-45128, 0.22F * pulse), 64);
      }

      for (double x = -R + 2.0; x < R; x += 2.0) {
         double h = Math.sqrt(Math.max(0.0, R * R - x * x));
         Draw.quad(
            vc,
            m,
            new Vec3(x - 0.03, 0.05, -h),
            new Vec3(x + 0.03, 0.05, -h),
            new Vec3(x + 0.03, 0.05, h),
            new Vec3(x - 0.03, 0.05, h),
            Draw.alpha(-45128, 0.16F)
         );
         Draw.quad(
            vc,
            m,
            new Vec3(-h, 0.05, x - 0.03),
            new Vec3(-h, 0.05, x + 0.03),
            new Vec3(h, 0.05, x + 0.03),
            new Vec3(h, 0.05, x - 0.03),
            Draw.alpha(-45128, 0.16F)
         );
      }

      float wave = (float)(t % 2400L) / 2400.0F;
      Draw.ringXZ(vc, m, new Vec3(0.0, 0.06, 0.0), R * (double)wave - 0.6, R * (double)wave, 0, Draw.alpha(-29992, 0.5F * (1.0F - wave)), 72);

      for (int i = 0; i < 10; i++) {
         double a = (double)i * Math.PI * 2.0 / 10.0 + (double)t / 20000.0;
         Vec3 b = new Vec3(Math.cos(a) * R * 0.8, 0.0, Math.sin(a) * R * 0.8);
         int col = i % 2 == 0 ? -45128 : -11443;
         float k = 0.5F + 0.5F * Mth.sin((float)t / 250.0F + (float)i);
         Draw.beam(vc, m, b, R * 1.1, 0.35, Draw.alpha(-1, 0.5F * k), Draw.alpha(col, 0.0F), 12);
         Draw.beam(vc, m, b, R * 0.9, 0.9, Draw.alpha(col, 0.22F * k), Draw.alpha(col, 0.0F), 12);
      }

      Vec3 apex = new Vec3(0.0, R * 0.9, 0.0);

      for (int i = 0; i < 4; i++) {
         double a = (double)t / 1400.0 + (double)i * Math.PI / 2.0;
         Vec3 floor = new Vec3(Math.cos(a) * R * 0.5, 0.0, Math.sin(a * 1.3) * R * 0.5);
         Vec3 dir = floor.subtract(apex);
         Draw.cone(vc, m, apex, dir, dir.length(), 3.2, Draw.alpha(-1, 0.25F), Draw.alpha(i % 2 == 0 ? -45128 : -10685697, 0.0F), 24);
         Draw.ringXZ(vc, m, floor.add(0.0, 0.07, 0.0), 0.0, 3.2, Draw.alpha(-1, 0.18F), 0, 24);
      }

      if (v.trainStart >= 0L && t - v.trainStart < 2200L) {
         double span = 2.0 * R + 80.0;
         double head = -R - 40.0 + span * ((double)(t - v.trainStart) / 2200.0);
         Vec3 cam = c.camRel(ClientState.center(s));

         for (int car = 0; car < 4; car++) {
            double x1 = head - (double)car * 8.4;
            double x0 = x1 - 8.0;

            for (double wx = x0 + 0.6; wx < x1 - 1.0; wx++) {
               for (int side = -1; side <= 1; side += 2) {
                  double z = 8.0 + (double)side * 1.5;
                  Draw.quad(
                     vc, m, new Vec3(wx, 1.65, z), new Vec3(wx + 1.0, 1.65, z), new Vec3(wx + 1.0, 2.65, z), new Vec3(wx, 2.65, z), Draw.alpha(-6232, 0.85F)
                  );
               }
            }
         }

         for (int k = 0; k < 10; k++) {
            double y = 0.6 + Draw.hash((long)(k * 7)) * 2.6;
            double z = 8.0 + (Draw.hash((long)(k * 13)) - 0.5) * 3.2;
            Draw.ribbon(vc, m, new Vec3(head - 34.0 - Draw.hash((long)k) * 10.0, y, z), new Vec3(head, y, z), 0.0, 0.05, 0, Draw.alpha(-1, 0.6F), cam);
         }

         Draw.glowOrb(vc, m, head + 0.7, 2.6, 7.2, 0.35, -2880, 1.0F);
         Draw.glowOrb(vc, m, head + 0.7, 2.6, 8.8, 0.35, -2880, 1.0F);
      }

      Vec3 ml = machineLocal(v);
      c.ps.pushPose();
      c.ps.translate(ml.x, ml.y, ml.z);
      c.ps.mulPose(Axis.YP.rotationDegrees(-v.yaw));
      ReelSet reels = ReelSet.of(s.entityId);

      for (int r = 0; r < 3; r++) {
         double rx = (double)(r - 1) * 2.9;
         double pos = reels.position(r, t);
         int base = (int)Math.floor(pos);

         for (int k = -1; k <= 1; k++) {
            double faceAng = ((double)(base + k) - pos) * Math.PI * 2.0 / 7.0;
            double cs = Math.cos(faceAng);
            if (!(cs <= 0.15)) {
               int n = ReelSet.numberAt((double)(base + k));
               c.ps.pushPose();
               c.ps.translate(rx, 0.0, 0.4);
               c.ps.mulPose(Axis.XP.rotation((float)faceAng));
               c.ps.translate(0.0, 0.0, -1.58);
               seg7(vc, c.m(), n, 0.62, Draw.alpha(ReelSet.numberColor(n), (float)cs));
               c.ps.popPose();
            }
         }

         if (reels.stopped(r, t) && reels.spinning(t)) {
            Draw.glowOrb(vc, c.m(), rx, 0.0, -1.3, 0.8, -11443, 0.35F + 0.25F * Mth.sin((float)t / 60.0F));
         }
      }

      Matrix4f mm = c.m();
      int bulbs = 44;

      for (int i = 0; i < bulbs; i++) {
         double u = (double)i / (double)bulbs;
         double x;
         double y;
         if (u < 0.25) {
            x = -5.4 + u / 0.25 * 10.8;
            y = 3.25;
         } else if (u < 0.5) {
            x = 5.4;
            y = 3.25 - (u - 0.25) / 0.25 * 6.5;
         } else if (u < 0.75) {
            x = 5.4 - (u - 0.5) / 0.25 * 10.8;
            y = -3.25;
         } else {
            x = -5.4;
            y = -3.25 + (u - 0.75) / 0.25 * 6.5;
         }

         boolean lit = ((long)i + t / 70L) % 4L == 0L;
         Draw.glowOrb(vc, mm, x, y, -0.15, lit ? 0.18 : 0.1, -11443, lit ? 1.0F : 0.35F);
      }

      c.ps.popPose();
      balls(c, R, false);
   }

   private static void balls(Vfx.Ctx c, double R, boolean solid) {
      VertexConsumer vc = c.vc;
      Matrix4f m = c.m();
      double time = (double)c.t / 1000.0;
      double top = R * 0.72;
      double g = 9.8;
      double tf = Math.sqrt(2.0 * top / g);

      for (int i = 0; i < 72; i++) {
         double period = tf + 1.2 + Draw.hash((long)(i * 3)) * 1.4;
         double ph = (time + Draw.hash((long)(i * 5)) * period) % period;
         long cycle = (long)((time + Draw.hash((long)(i * 5)) * period) / period);
         double ang = Draw.hash((long)(i * 11) + cycle * 101L) * Math.PI * 2.0;
         double rad = Math.sqrt(Draw.hash((long)(i * 13) + cycle * 37L)) * R * 0.82;
         double x = Math.cos(ang) * rad;
         double z = Math.sin(ang) * rad;
         double y;
         if (ph < tf) {
            y = top - 0.5 * g * ph * ph;
         } else {
            double b = ph - tf;
            y = 0.14 + Math.abs(Math.sin(b * 7.0)) * 1.1 * Math.exp(-b * 2.2);
            x += Math.cos(ang + 1.0) * b * 0.9;
            z += Math.sin(ang + 1.0) * b * 0.9;
         }

         if (solid) {
            Draw.sphere(vc, m, x, y, z, 0.13, 5, 8, (nx, ny, nz) -> Draw.lerp(-9671048, -723201, (float)(ny * 0.5 + 0.5)));
         } else {
            Draw.sphere(vc, m, x, y, z, 0.22, 4, 6, Draw.alpha(-29992, 0.25F));
         }
      }
   }

   private static void aura(Vfx.Ctx c, StatePacket s) {
      Entity e = Vfx.entity(s.entityId);
      if (e != null) {
         Vec3 base = e.getPosition(c.partial);
         long t = c.t;
         c.at(base);
         VertexConsumer vc = c.vc;
         Matrix4f m = c.m();
         Vec3 cam = c.camRel(base);
         double time = (double)t / 1000.0;

         for (int h = 0; h < 3; h++) {
            Vec3 prev = null;

            for (int k = 0; k <= 26; k++) {
               double y = (double)k * 0.085;
               double a = time * 4.0 + (double)h * 2.094 + (double)k * 0.33;
               double r = 0.72 + 0.12 * Math.sin(time * 3.0 + (double)k * 0.4);
               Vec3 p = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
               if (prev != null) {
                  float al = (float)Math.sin(Math.PI * (double)k / 26.0);
                  Draw.ribbon(vc, m, prev, p, 0.05, 0.05, Draw.alpha(-11443, al), Draw.alpha(-2880, al), cam);
               }

               prev = p;
            }
         }

         float beat = (float)(t % 700L) / 700.0F;
         Draw.ringXZ(vc, m, new Vec3(0.0, 0.05, 0.0), 0.6 + (double)beat * 1.6, 0.8 + (double)beat * 1.6, Draw.alpha(-11443, 1.0F - beat), 0, 40);
         Draw.ringXZ(vc, m, new Vec3(0.0, 0.05, 0.0), 0.0, 1.0, Draw.alpha(-11443, 0.25F), 0, 32);

         for (int i = 0; i < 16; i++) {
            double ph = (time * 0.8 + Draw.hash((long)i)) % 1.0;
            double a = Draw.hash((long)(i * 7)) * Math.PI * 2.0;
            Vec3 p = new Vec3(Math.cos(a) * 0.6, ph * 2.6, Math.sin(a) * 0.6);
            Draw.billboard(vc, m, p, 0.06 + 0.06 * (1.0 - ph), Draw.alpha(-2880, (float)(1.0 - ph)), c.cam.getLeftVector(), c.cam.getUpVector());
         }

         for (int i = 0; i < 8; i++) {
            double ph = (time * 0.5 + Draw.hash((long)(i * 19))) % 1.0;
            Vec3 p = new Vec3((Draw.hash((long)(i * 3)) - 0.5) * 0.7, 1.2 + ph * 1.6, (Draw.hash((long)(i * 5)) - 0.5) * 0.7);
            Draw.billboard(vc, m, p, 0.3 + ph * 0.5, Draw.alpha(-1, (float)(0.12 * (1.0 - ph))), c.cam.getLeftVector(), c.cam.getUpVector());
         }

         if (t / 90L % 5L == 0L) {
            Vec3 a = Draw.randDir(t / 90L).scale(0.5).add(0.0, 1.1, 0.0);
            Vec3 b = a.add(Draw.randDir(t / 90L + 3L).scale(0.6));
            Draw.ribbon(vc, m, a, b, 0.03, 0.01, -1, -45128, cam);
         }

         c.pop();
      }
   }

   private static void billboardText(Vfx.Ctx c, Font font, MultiBufferSource buffers, String s, Vec3 world, float scale, int color) {
      PoseStack ps = c.ps;
      ps.pushPose();
      ps.translate(world.x - c.camPos.x, world.y - c.camPos.y, world.z - c.camPos.z);
      ps.mulPose(c.cam.rotation());
      ps.scale(-scale, -scale, scale);
      float x = (float)(-font.width(s)) / 2.0F;
      font.drawInBatch(s, x, -4.0F, color, false, ps.last().pose(), buffers, DisplayMode.NORMAL, 0, 15728880);
      ps.popPose();
   }

   private DomainRenderer() {
   }

   private static final class Visual {
      float yaw = Float.NaN;
      long trainStart = -1L;
      long lastAutoTrain;
      long openedAt = -1L;
   }
}
