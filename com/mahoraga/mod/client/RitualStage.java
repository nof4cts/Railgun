package com.mahoraga.mod.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class RitualStage {
   static final int VOID_IN = 44;
   static final int FACE = 150;
   static final int BURST = 186;
   static final int VOID_OUT = 194;
   private static final float WU = 0.072265625F;
   private static final float WV = 0.005859375F;
   private static Vec3 spot = Vec3.ZERO;
   private static Vec3 from = Vec3.ZERO;
   private static Vec3 dir = new Vec3(0.0, 0.0, 1.0);
   private static Vec3 right = new Vec3(1.0, 0.0, 0.0);
   private static float figYaw;
   private static final List<Wolf> WOLVES = new ArrayList<>();
   private static final List<Vec3> WOLF_AT = new ArrayList<>();
   private static final List<Frog> FROGS = new ArrayList<>();
   private static final List<Vec3> FROG_AT = new ArrayList<>();
   private static int summoner;
   static Vec3 face = Vec3.ZERO;
   static Vec3 faceFwd = new Vec3(0.0, 0.0, 1.0);

   private RitualStage() {
   }

   private static float smooth(float a, float b, float x) {
      float k = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
      return k * k * (3.0F - 2.0F * k);
   }

   static void start(Minecraft mc, Vec3 spotAt, Vec3 fromAt, int summonerId) {
      spot = spotAt;
      from = fromAt;
      summoner = summonerId;
      Vec3 d = spot.subtract(from).multiply(1.0, 0.0, 1.0);
      dir = d.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : d.normalize();
      right = new Vec3(-dir.z, 0.0, dir.x);
      figYaw = (float)(Mth.atan2(-dir.z, -dir.x) * 180.0F / (float)Math.PI) - 90.0F;
      face = spot.add(0.0, 3.3, 0.0);
      faceFwd = dir.reverse();
      WOLVES.clear();
      WOLF_AT.clear();
      FROGS.clear();
      FROG_AT.clear();
      double[][] wolves = new double[][]{{-3.4, -0.6}, {3.4, -0.6}, {-5.6, 0.9}, {5.6, 0.9}};

      for (double[] w : wolves) {
         Wolf wolf = (Wolf)EntityType.WOLF.create(mc.level);
         if (wolf != null) {
            Vec3 at = spot.add(right.scale(w[0])).add(dir.scale(w[1]));
            float yaw = (float)(Mth.atan2(from.z - at.z, from.x - at.x) * 180.0F / (float)Math.PI) - 90.0F + (w[0] < 0.0 ? -35.0F : 35.0F);
            setup(wolf, at, yaw, -60.0F);
            wolf.setInSittingPose(true);
            WOLVES.add(wolf);
            WOLF_AT.add(at);
         }
      }

      for (int side = -1; side <= 1; side += 2) {
         Frog frog = (Frog)EntityType.FROG.create(mc.level);
         if (frog != null) {
            Vec3 at = from.add(dir.scale(1.4)).add(right.scale((double)side * 1.7));
            float yaw = (float)(Mth.atan2(from.z - at.z, from.x - at.x) * 180.0F / (float)Math.PI) - 90.0F;
            setup(frog, at, yaw, 0.0F);
            FROGS.add(frog);
            FROG_AT.add(at);
         }
      }
   }

   private static void setup(LivingEntity e, Vec3 at, float yaw, float pitch) {
      e.moveTo(at.x, at.y, at.z, yaw, pitch);
      e.xo = e.xOld = at.x;
      e.yo = e.yOld = at.y;
      e.zo = e.zOld = at.z;
      e.setYBodyRot(yaw);
      e.yBodyRotO = yaw;
      e.setYHeadRot(yaw);
      e.yHeadRotO = yaw;
      e.xRotO = pitch;
   }

   static void tick(int t) {
      for (int i = 0; i < WOLVES.size(); i++) {
         Wolf w = WOLVES.get(i);
         w.tickCount++;
         float howl = 0.0F;

         for (int h : new int[]{34, 50, 104, 120}) {
            float k = (float)(t - h - i * 2);
            if (k > 0.0F && k < 30.0F) {
               howl = Math.max(howl, Mth.sin(k / 30.0F * (float) Math.PI));
            }
         }

         w.xRotO = w.getXRot();
         w.setXRot(-45.0F - 35.0F * howl);
      }

      for (int i = 0; i < FROGS.size(); i++) {
         Frog f = FROGS.get(i);
         f.tickCount++;
         if (t == 76 + i * 4 || t == 92 + i * 4 || t == 130 + i * 6) {
            f.croakAnimationState.start(f.tickCount);
         }
      }
   }

   static Vec3[] camera(float t) {
      if (t < 100.0F) {
         float k = smooth(44.0F, 100.0F, t);
         return new Vec3[]{
            from.subtract(dir.scale(4.0 - (double)k)).add(right.scale(3.0)).add(0.0, 0.9 + 0.4 * (double)k, 0.0),
            spot.add(0.0, 2.2 + 0.3 * (double)k, 0.0),
            new Vec3(62.0, 0.0, 0.0)
         };
      } else if (t < 150.0F) {
         double a = (double)(t - 100.0F) * 0.016 - 0.9;
         Vec3 off = right.scale(Math.sin(a) * 8.0).add(dir.reverse().scale(Math.cos(a) * 8.0));
         return new Vec3[]{spot.add(off).add(0.0, 1.0 + (double)(t - 100.0F) * 0.01, 0.0), spot.add(0.0, 2.6, 0.0), new Vec3(58.0, 0.0, 0.0)};
      } else {
         float k = smooth(150.0F, 186.0F, t);
         Vec3 f = face.add(faceFwd.scale(2.2 - 0.9 * (double)k)).add(0.0, -0.15, 0.0);
         return new Vec3[]{f, face, new Vec3((double)(48.0F - 8.0F * k), 0.0, 0.0)};
      }
   }

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() == Stage.AFTER_PARTICLES && RitualFx.active()) {
         float t = RitualFx.age();
         if (!(t < 44.0F) && !(t >= 194.0F)) {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            main.bindWrite(false);
            RenderSystem.clearColor(0.004F, 0.006F, 0.008F, 1.0F);
            RenderSystem.clear(16640, Minecraft.ON_OSX);
            float partial = e.getPartialTick();
            Vec3 cam = e.getCamera().getPosition();
            PoseStack ps = e.getPoseStack();
            BufferSource buf = mc.renderBuffers().bufferSource();
            EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
            float fig = smooth(44.0F, 74.0F, t);
            float burst = smooth(184.0F, 194.0F, t);
            MahoModel m = MahoModel.get();
            Quaternionf[] q = wrappedPose(m, t);
            Matrix4f[] jm = m.joints(q);
            ps.pushPose();
            ps.translate(spot.x - cam.x, spot.y - cam.y, spot.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(-figYaw));
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(MahoRenderer.TEX));
            float b = 0.15F + 0.85F * fig;
            m.draw(ps, vc, jm, 0.0F, 15728880, OverlayTexture.NO_OVERLAY, 0.86F * b, 1.0F * b, 0.92F * b);
            threads(ps, vc, t, fig, burst);
            Vector4f fp = new Vector4f(0.0F, 3.56F, 0.9F, 1.0F);
            jm[3].transform(fp);
            double yr = Math.toRadians((double)(-figYaw));
            face = spot.add(
               (double)fp.x * Math.cos(yr) + (double)fp.z * Math.sin(yr), (double)fp.y, (double)(-fp.x) * Math.sin(yr) + (double)fp.z * Math.cos(yr)
            );
            if (t > 150.0F) {
               mouth(ps, vc, jm[3], smooth(156.0F, 164.0F, t));
            }

            ps.popPose();
            buf.endBatch();
            int wl = (int)(9.0F * smooth(50.0F, 80.0F, t));
            int light = LightTexture.pack(Math.max(wl, 0), Math.max(wl, 0));

            for (int i = 0; i < WOLVES.size(); i++) {
               drawEntity(erd, ps, buf, (Entity)WOLVES.get(i), WOLF_AT.get(i), cam, partial, 2.4F, light);
            }

            int fl = (int)(9.0F * smooth(70.0F, 94.0F, t));

            for (int i = 0; i < FROGS.size(); i++) {
               drawEntity(erd, ps, buf, (Entity)FROGS.get(i), FROG_AT.get(i), cam, partial, 1.7F, LightTexture.pack(fl, fl));
            }

            Entity s = mc.level.getEntity(summoner);
            if (s != null) {
               Vec3 sp = s.getPosition(partial);
               ps.pushPose();
               erd.render(s, sp.x - cam.x, sp.y - cam.y, sp.z - cam.z, s.getViewYRot(partial), partial, ps, buf, LightTexture.pack(10, 10));
               ps.popPose();
            }

            buf.endBatch();
            VertexConsumer glow = buf.getBuffer(RenderType.lightning());
            Matrix4f pose = ps.last().pose();

            for (int i = 0; i < 18; i++) {
               double ang = (hash(i, 4) < 0.5 ? Math.PI : 0.0) + (hash(i, 5) - 0.5) * 1.4;
               double len = (double)(6.0F + 30.0F * smooth(44.0F + (float)i * 1.5F, 74.0F + (float)i * 1.5F, t)) * (0.6 + 0.4 * hash(i, 7));
               Vec3 a = spot.add(0.0, 1.0 + hash(i, 9) * 2.5, 0.0).add(right.scale((hash(i, 8) - 0.5) * 1.2));
               Vec3 d = right.scale(Math.cos(ang)).add(dir.scale(Math.sin(ang) * 0.6)).add(0.0, (hash(i, 6) - 0.6) * 0.5, 0.0).normalize();
               ribbon(glow, pose, cam, a, a.add(d.scale(len)), 0.04F, 0.85F, 0.95F, 0.9F, 0.35F * fig * (1.0F - burst));
            }

            for (int i = 0; i < 50; i++) {
               float ph = (float)((hash(i, 12) + (double)t * (0.004 + 0.006 * hash(i, 13))) % 1.0);
               Vec3 p = spot.add(right.scale((hash(i, 11) * 2.0 - 1.0) * 6.0))
                  .add(dir.scale((hash(i, 14) * 2.0 - 1.0) * 4.0))
                  .add(0.0, (double)(ph * 7.0F), 0.0);
               float a = Mth.sin(ph * (float) Math.PI) * fig;
               ribbon(glow, pose, cam, p, p.add(0.0, 0.09, 0.0), 0.07F, 1.0F, 1.0F, 1.0F, a * 0.8F);
            }

            buf.endBatch(RenderType.lightning());
         }
      }
   }

   private static double hash(int i, int salt) {
      double s = Math.sin((double)i * 12.9898 + (double)salt * 78.233) * 43758.5453;
      return s - Math.floor(s);
   }

   private static void drawEntity(
      EntityRenderDispatcher erd, PoseStack ps, MultiBufferSource buf, Entity e, Vec3 at, Vec3 cam, float partial, float scale, int light
   ) {
      ps.pushPose();
      ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
      ps.scale(scale, scale, scale);
      erd.render(e, 0.0, 0.0, 0.0, e.getYRot(), partial, ps, buf, light);
      ps.popPose();
   }

   private static Quaternionf[] wrappedPose(MahoModel m, float t) {
      Quaternionf[] q = new Quaternionf[m.parent.length];

      for (int i = 0; i < q.length; i++) {
         q[i] = new Quaternionf();
      }

      float strain = smooth(156.0F, 186.0F, t);
      float breathe = 0.03F * Mth.sin(t * 0.12F);
      q[8].rotateY(0.95F).rotateX(-1.25F);
      q[5].rotateY(-0.95F).rotateX(-1.15F);
      q[9].rotateZ(1.5F);
      q[6].rotateZ(-1.5F);
      q[3].rotateX(0.4F - 0.35F * strain);
      q[2].rotateX(0.08F + breathe - 0.1F * strain);
      q[11].rotateZ(0.05F);
      q[13].rotateZ(-0.05F);
      return q;
   }

   private static void threads(PoseStack ps, VertexConsumer vc, float t, float fig, float burst) {
      Matrix4f pose = ps.last().pose();
      Matrix3f nrm = ps.last().normal();
      float c = 0.16F * fig;
      int bands = 46;

      for (int i = 0; i < bands; i++) {
         float y = 0.15F + (float)i * 0.088F;
         float rx = y > 3.25F ? 0.6F : (y > 2.35F ? 1.3F : (y > 1.75F ? 0.95F : 0.78F));
         float rz = y > 3.25F ? 0.72F : (y > 2.35F ? 0.95F : 0.7F);
         float tilt = (float)((hash(i, 3) - 0.5) * 0.5);
         float spread = 1.0F + burst * (2.5F + (float)hash(i, 5) * 3.0F);
         float drop = burst * burst * (float)hash(i, 6) * 2.0F;
         float w = 0.04F * (1.0F - burst);
         if (w <= 0.001F) {
            return;
         }

         int seg = 28;

         for (int k = 0; k < seg; k++) {
            float a0 = (float)k * (float) (Math.PI * 2) / (float)seg;
            float a1 = (float)(k + 1) * (float) (Math.PI * 2) / (float)seg;
            float x0 = Mth.cos(a0) * rx * spread;
            float z0 = Mth.sin(a0) * rz * spread + 0.05F;
            float x1 = Mth.cos(a1) * rx * spread;
            float z1 = Mth.sin(a1) * rz * spread + 0.05F;
            float y0 = y + tilt * Mth.sin(a0) - drop;
            float y1 = y + tilt * Mth.sin(a1) - drop;
            quad(vc, pose, nrm, x0, y0 - w, z0, x1, y1 - w, z1, x1, y1 + w, z1, x0, y0 + w, z0, c, c * 1.2F, c * 1.1F);
         }
      }
   }

   private static void mouth(PoseStack ps, VertexConsumer vc, Matrix4f head, float open) {
      if (!(open < 0.01F)) {
         Pose last = ps.last();
         Matrix4f pose = new Matrix4f(last.pose()).mul(head);
         Matrix3f nrm = new Matrix3f(last.normal());
         float cx = 0.0F;
         float cy = 3.56F;
         float z = 0.97F;
         float rx = 0.17F;
         float ry = 0.22F * open;
         int seg = 20;

         for (int k = 0; k < seg; k++) {
            float a0 = (float)k * (float) (Math.PI * 2) / (float)seg;
            float a1 = (float)(k + 1) * (float) (Math.PI * 2) / (float)seg;
            quad(
               vc,
               pose,
               nrm,
               cx,
               cy,
               z,
               cx + Mth.cos(a0) * rx,
               cy + Mth.sin(a0) * ry,
               z,
               cx + Mth.cos(a1) * rx,
               cy + Mth.sin(a1) * ry,
               z,
               cx,
               cy,
               z,
               0.05F,
               0.08F,
               0.18F
            );
         }

         for (int k = 0; k < 7; k++) {
            float a = (float) Math.PI * ((float)k + 0.5F) / 7.0F;
            float fx = cx + Mth.cos(a) * rx * 0.95F;
            float top = cy + Mth.sin(a) * ry * 0.95F;
            float bot = cy - Mth.sin(a) * ry * 0.95F;
            float len = (0.06F + 0.03F * (float)(k % 2)) * open;
            float half = 0.022F;
            quad(vc, pose, nrm, fx - half, top, z + 0.005F, fx + half, top, z + 0.005F, fx, top - len, z + 0.005F, fx, top - len, z + 0.005F, 0.82F, 0.9F, 1.0F);
            quad(vc, pose, nrm, fx - half, bot, z + 0.005F, fx + half, bot, z + 0.005F, fx, bot + len, z + 0.005F, fx, bot + len, z + 0.005F, 0.82F, 0.9F, 1.0F);
         }
      }
   }

   private static void quad(
      VertexConsumer vc,
      Matrix4f pose,
      Matrix3f nrm,
      float x0,
      float y0,
      float z0,
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3,
      float r,
      float g,
      float b
   ) {
      Vector3f n = nrm.transform(new Vector3f(0.0F, 0.0F, 1.0F));
      float[][] pts = new float[][]{{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
      Vector4f v = new Vector4f();

      for (int[] o : new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}}) {
         for (int i : o) {
            pose.transform(v.set(pts[i][0], pts[i][1], pts[i][2], 1.0F));
            vc.vertex(v.x, v.y, v.z, r, g, b, 1.0F, 0.072265625F, 0.005859375F, OverlayTexture.NO_OVERLAY, 15728880, n.x, n.y, n.z);
         }
      }
   }

   private static void ribbon(VertexConsumer vc, Matrix4f pose, Vec3 cam, Vec3 a, Vec3 b, float w, float r, float g, float bl, float alpha) {
      if (!(alpha <= 0.003F)) {
         Vec3 d = b.subtract(a);
         Vec3 mid = a.add(b).scale(0.5);
         Vec3 side = d.cross(mid.subtract(cam));
         if (!(side.lengthSqr() < 1.0E-8)) {
            side = side.normalize().scale((double)w);
            Vec3[] c = new Vec3[]{a.add(side), a.subtract(side), b.subtract(side), b.add(side)};

            for (Vec3 p : c) {
               vc.vertex(pose, (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z)).color(r, g, bl, alpha).endVertex();
            }
         }
      }
   }
}
