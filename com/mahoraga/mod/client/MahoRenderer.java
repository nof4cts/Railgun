package com.mahoraga.mod.client;

import com.mahoraga.mod.MahoragaEntity;
import com.mahoraga.mod.MahoragaMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class MahoRenderer extends EntityRenderer<MahoragaEntity> {
   static final ResourceLocation TEX = new ResourceLocation("mahoraga", "textures/entity/mahoraga.png");
   private static final float WU = 0.072265625F;
   private static final float WV = 0.005859375F;
   private static final Map<Integer, float[]> WHEEL = new HashMap<>();

   MahoRenderer(Context ctx) {
      super(ctx);
      this.shadowRadius = 1.3F;
   }

   public ResourceLocation getTextureLocation(MahoragaEntity e) {
      return TEX;
   }

   static float wheel(MahoragaEntity e, float partial) {
      float[] w = WHEEL.computeIfAbsent(e.getId(), k -> new float[]{(float)e.spins() * 45.0F, 0.0F, (float)e.tickCount});
      float target = (float)e.spins() * 45.0F;
      float now = (float)e.tickCount + partial;
      float dt = Math.min(1.0F, now - w[2]);
      if (dt > 0.0F) {
         w[1] += (target - w[0]) * 0.25F * dt;
         w[1] *= (float)Math.pow(0.6, (double)dt);
         w[0] += w[1] * dt;
         w[2] = now;
      }

      return w[0];
   }

   private static Vec3 world(MahoragaEntity e, float partial, double bodyYaw, Vec3 local, Vec3 off, float sc) {
      local = local.add(off).scale((double)sc);
      double r = Math.toRadians(-bodyYaw);
      double x = local.x * Math.cos(r) + local.z * Math.sin(r);
      double z = -local.x * Math.sin(r) + local.z * Math.cos(r);
      return e.getPosition(partial).add(x, local.y, z);
   }

   public void render(MahoragaEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      MahoModel m = MahoModel.get();
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      Quaternionf[] q = m.pose(e, partial);
      Matrix4f[] jm = m.joints(q);
      float sc = e.scale();
      Vec3 off = m.offset(e, partial);
      this.shadowRadius = 1.3F * sc;
      ps.pushPose();
      ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
      ps.translate(off.x * (double)sc, off.y * (double)sc, off.z * (double)sc);
      ps.scale(sc, sc, sc);
      if (e.deathTime > 0) {
         float k = Math.min(1.0F, ((float)e.deathTime + partial) / 20.0F);
         ps.mulPose(Axis.XP.rotationDegrees(-80.0F * k));
      }

      int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
      float glow = MahoFx.wheelGlow(e.getId(), partial);
      float[] wheelRgb = new float[]{1.0F, Mth.lerp(glow, 1.0F, 0.84F), Mth.lerp(glow, 1.0F, 0.32F)};
      int wheelLight = glow > 0.02F ? 15728880 : light;
      VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
      m.draw(ps, vc, jm, wheel(e, partial), light, overlay, 1.0F, 1.0F, 1.0F, wheelRgb, wheelLight);
      if (e.gills()) {
         gills(ps, vc, jm[3], light, overlay);
      }

      if (e.action() == 8) {
         float t = e.actionAge(partial);
         if (t > 8.5F && t < 16.5F) {
            heldChunk(e, ps, buf, m.joint(jm, 7), light);
         }
      }

      ps.popPose();
      e.clientBlade = world(e, partial, (double)bodyYaw, m.bladeTip(jm), off, sc);
      e.clientHandL = world(e, partial, (double)bodyYaw, m.joint(jm, 7), off, sc);
      Vec3 l = m.joint(jm, 7);
      Vec3 r = m.joint(jm, 10);
      e.clientHands = world(e, partial, (double)bodyYaw, l.add(r).scale(0.5), off, sc);
      MahoFx.WHEELS.put(e.getId(), world(e, partial, (double)bodyYaw, m.wheel(jm), off, sc));
      super.render(e, yaw, partial, ps, buf, light);
   }

   private static void gills(PoseStack ps, VertexConsumer vc, Matrix4f head, int light, int overlay) {
      Matrix4f pose = new Matrix4f(ps.last().pose()).mul(head);
      Matrix3f nrm = new Matrix3f(ps.last().normal()).mul(new Matrix3f(head));

      for (int side = -1; side <= 1; side += 2) {
         float x = (float)side * 0.262F;

         for (int i = 0; i < 3; i++) {
            float z = 0.47F + (float)i * 0.09F;
            float y0 = 3.31F;
            float y1 = 3.62F - (float)i * 0.04F;
            quad(
               vc,
               pose,
               nrm,
               overlay,
               light,
               side,
               new float[]{x, y0, z - 0.03F},
               new float[]{x, y0, z + 0.03F},
               new float[]{x, y1, z + 0.03F},
               new float[]{x, y1, z - 0.03F}
            );
         }
      }
   }

   private static void quad(VertexConsumer vc, Matrix4f pose, Matrix3f nrm, int overlay, int light, int side, float[]... c) {
      Vector3f n = nrm.transform(new Vector3f((float)side, 0.0F, 0.0F)).normalize();
      Vector4f v = new Vector4f();
      int[][] orders = new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}};

      for (int[] o : orders) {
         for (int k : o) {
            pose.transform(v.set(c[k][0], c[k][1], c[k][2], 1.0F));
            vc.vertex(v.x, v.y, v.z, 0.62F, 0.06F, 0.1F, 1.0F, 0.072265625F, 0.005859375F, overlay, light, n.x, n.y, n.z);
         }
      }
   }

   private static void heldChunk(MahoragaEntity e, PoseStack ps, MultiBufferSource buf, Vec3 hand, int light) {
      BlockState st = chunkState(e);
      float grow = Mth.clamp((e.actionAge(0.0F) - 8.0F) / 4.0F, 0.3F, 1.0F);
      ps.pushPose();
      ps.translate(hand.x, hand.y + 0.6 * (double)grow, hand.z);
      MahoFx.drawBall(ps, buf, st, light, (float)e.tickCount * 9.0F, 0.75F * grow);
      ps.popPose();
   }

   static BlockState chunkState(MahoragaEntity e) {
      Vec3 fwd = Vec3.directionFromRotation(0.0F, e.getYRot());
      BlockPos p = BlockPos.containing(e.position().add(fwd.scale(1.6)).add(0.0, -0.5, 0.0));
      BlockState st = e.level().getBlockState(p);
      return !st.isAir() && !st.hasBlockEntity() && st.isCollisionShapeFullBlock(e.level(), p) && !st.is(Blocks.GRASS_BLOCK)
         ? st
         : Blocks.DIRT.defaultBlockState();
   }

   @EventBusSubscriber(
      modid = "jjk",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class Reg {
      @SubscribeEvent
      public static void onRenderers(RegisterRenderers e) {
         e.registerEntityRenderer((EntityType)MahoragaMod.MAHORAGA.get(), MahoRenderer::new);
      }
   }
}
