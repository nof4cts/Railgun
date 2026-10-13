package com.yuki.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yuki.mod.GarudaEntity;
import com.yuki.mod.YukiMod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class GarudaRenderer extends EntityRenderer<GarudaEntity> {
   public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("yuki", "garuda"), "main");
   static final ResourceLocation TEX = new ResourceLocation("yuki", "textures/entity/garuda.png");
   static final float SCALE = 1.35F;
   static final float SPACING = 0.42F;
   static final int SEGMENTS = 18;
   private static GarudaRenderer.Parts parts;
   static float finScale = 1.0F;

   public GarudaRenderer(Context ctx) {
      super(ctx);
      parts = new GarudaRenderer.Parts(ctx.bakeLayer(LAYER));
      this.shadowRadius = 0.6F;
   }

   public static LayerDefinition createLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      root.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -3.5F, -6.0F, 8.0F, 7.0F, 9.0F), PartPose.ZERO);
      root.addOrReplaceChild(
         "upper_beak", CubeListBuilder.create().texOffs(0, 17).addBox(-1.0F, -0.5F, -12.0F, 2.0F, 1.0F, 12.0F), PartPose.offset(0.0F, -0.5F, -5.5F)
      );
      root.addOrReplaceChild(
         "lower_beak", CubeListBuilder.create().texOffs(0, 31).addBox(-1.0F, -0.5F, -11.0F, 2.0F, 1.0F, 11.0F), PartPose.offset(0.0F, 1.0F, -5.5F)
      );
      root.addOrReplaceChild(
         "crest",
         CubeListBuilder.create().texOffs(36, 12).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F),
         PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.35F, 0.0F, 0.0F)
      );
      root.addOrReplaceChild("vertebra", CubeListBuilder.create().texOffs(36, 0).addBox(-2.5F, -2.5F, -2.0F, 5.0F, 5.0F, 4.0F), PartPose.ZERO);
      root.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(0, 44).addBox(0.0F, -0.5F, -1.5F, 14.0F, 1.0F, 3.0F), PartPose.ZERO);
      root.addOrReplaceChild("prong", CubeListBuilder.create().texOffs(36, 26).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 7.0F), PartPose.ZERO);
      return LayerDefinition.create(mesh, 64, 64);
   }

   private static GarudaRenderer.Parts parts() {
      if (parts == null) {
         parts = new GarudaRenderer.Parts(Minecraft.getInstance().getEntityModels().bakeLayer(LAYER));
      }

      return parts;
   }

   public ResourceLocation getTextureLocation(GarudaEntity e) {
      return TEX;
   }

   public boolean shouldRender(GarudaEntity e, Frustum frustum, double x, double y, double z) {
      return e.distanceToSqr(x, y, z) < 16384.0;
   }

   public void render(GarudaEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      if (!e.hidden()) {
         Vec3 base = e.getPosition(partial);
         Vec3 head = base.add(0.0, (double)e.getBbHeight() * 0.5, 0.0);
         List<Vec3> spine = new ArrayList<>();
         spine.add(head);
         Vec3 prev = head;
         double need = 0.42F;
         Iterator<Vec3> it = e.hist.iterator();

         while (spine.size() < 18 && it.hasNext()) {
            Vec3 p = it.next().add(0.0, (double)e.getBbHeight() * 0.5, 0.0);

            double seg;
            for (seg = p.distanceTo(prev); seg >= need && spine.size() < 18; need = 0.42F) {
               prev = prev.lerp(p, need / seg);
               spine.add(prev);
               seg = p.distanceTo(prev);
            }

            need -= seg;
            prev = p;
         }

         Vec3 back = Vec3.directionFromRotation(e.getXRot(), e.getYRot()).scale(-0.42F);

         while (spine.size() < 18) {
            spine.add(spine.get(spine.size() - 1).add(back));
         }

         float jaw = e.attackAnim > 0
            ? Mth.sin(((float)(10 - e.attackAnim) + partial) / 10.0F * (float) Math.PI) * 0.7F
            : 0.08F + 0.05F * Mth.sin(((float)e.tickCount + partial) * 0.1F);
         int overlay = LivingEntityRenderer.getOverlayCoords(e, 0.0F);
         ps.pushPose();
         ps.translate(-base.x, -base.y, -base.z);
         drawSpine(ps, buf, spine, light, 1.0F, jaw, overlay);
         ps.popPose();
         super.render(e, yaw, partial, ps, buf, light);
      }
   }

   public static void drawSpine(PoseStack ps, MultiBufferSource buf, List<Vec3> spine, int light, float alpha, float jaw, int overlay) {
      GarudaRenderer.Parts p = parts();
      int n = spine.size();
      if (n >= 2) {
         VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TEX));

         for (int i = n - 1; i >= 1; i--) {
            Vec3 at = spine.get(i);
            Vec3 fwd = spine.get(i - 1).subtract(at);
            float k = (float)i / (float)(n - 1);
            float s = 1.35F * Mth.lerp(k, 1.05F, 0.55F);
            ps.pushPose();
            orient(ps, at, fwd, s, (float)i * 0.12F);
            p.vertebra.render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, alpha);
            if (i % 2 == 0 && i < n - 2) {
               float len = Mth.lerp(k, 1.25F, 0.7F) * finScale;

               for (int side = -1; side <= 1; side += 2) {
                  ps.pushPose();
                  ps.scale((float)side, 1.0F, 1.0F);
                  ps.mulPose(new Quaternionf().rotationYXZ(-0.6F, 0.0F, -0.45F));
                  ps.scale(len, 1.0F, 1.0F);
                  p.blade.render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, alpha * 0.92F);
                  ps.popPose();
               }
            }

            if (i == n - 1) {
               for (int side = -1; side <= 1; side += 2) {
                  ps.pushPose();
                  ps.mulPose(new Quaternionf().rotationYXZ((float)side * 0.35F, 0.0F, 0.0F));
                  p.prong.render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, alpha);
                  ps.popPose();
               }
            }

            ps.popPose();
         }

         Vec3 head = spine.get(0);
         Vec3 fwdx = head.subtract(spine.get(1));
         ps.pushPose();
         orient(ps, head, fwdx, 1.35F, 0.0F);
         p.skull.render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, alpha);
         p.crest.render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, alpha);
         p.upper.xRot = -jaw * 0.25F;
         p.upper.render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, alpha);
         p.lower.xRot = jaw;
         p.lower.render(ps, vc, light, overlay, 1.0F, 1.0F, 1.0F, alpha);
         ps.popPose();
      }
   }

   private static void orient(PoseStack ps, Vec3 at, Vec3 fwd, float scale, float roll) {
      ps.translate(at.x, at.y, at.z);
      Vector3f f = fwd.lengthSqr() < 1.0E-8 ? new Vector3f(0.0F, 0.0F, 1.0F) : new Vector3f((float)fwd.x, (float)fwd.y, (float)fwd.z).normalize();
      float yaw = (float)Math.atan2((double)(-f.x), (double)(-f.z));
      float pitch = (float)Math.asin((double)Mth.clamp(f.y, -1.0F, 1.0F));
      ps.mulPose(new Quaternionf().rotationYXZ(yaw, pitch, roll * 0.3F));
      ps.scale(-scale, -scale, scale);
   }

   static void drawWorld(List<Vec3> spine, float alpha, float jaw, Vec3 cam, Matrix4f view) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && spine.size() >= 2 && !(alpha <= 0.01F)) {
         BufferSource bs = mc.renderBuffers().bufferSource();
         PoseStack ps = new PoseStack();
         ps.last().pose().set(view);
         ps.last().normal().set(new Matrix3f(view));
         ps.translate(-cam.x, -cam.y, -cam.z);
         int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing((Position)spine.get(0)));
         drawSpine(ps, bs, spine, light, alpha, jaw, OverlayTexture.NO_OVERLAY);
         bs.endBatch(RenderType.entityTranslucent(TEX));
      }
   }

   static final class Parts {
      final ModelPart skull;
      final ModelPart upper;
      final ModelPart lower;
      final ModelPart crest;
      final ModelPart vertebra;
      final ModelPart blade;
      final ModelPart prong;

      Parts(ModelPart root) {
         this.skull = root.getChild("skull");
         this.upper = root.getChild("upper_beak");
         this.lower = root.getChild("lower_beak");
         this.crest = root.getChild("crest");
         this.vertebra = root.getChild("vertebra");
         this.blade = root.getChild("blade");
         this.prong = root.getChild("prong");
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class Reg {
      @SubscribeEvent
      public static void onLayers(RegisterLayerDefinitions e) {
         e.registerLayerDefinition(GarudaRenderer.LAYER, GarudaRenderer::createLayer);
      }

      @SubscribeEvent
      public static void onRenderers(RegisterRenderers e) {
         e.registerEntityRenderer((EntityType)YukiMod.GARUDA.get(), GarudaRenderer::new);
      }
   }
}
