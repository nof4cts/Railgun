package com.yuta.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.yuta.mod.RikaEntity;
import com.yuta.mod.YutaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

public class RikaRenderer extends EntityRenderer<RikaEntity> {
   public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("yuta", "rika"), "main");
   static final ResourceLocation TEX = new ResourceLocation("yuta", "textures/entity/rika.png");
   static final float SCALE = 1.2F;
   static final float CHEST_Y = 3.75F;
   private static RikaRenderer.Parts parts;

   public RikaRenderer(Context ctx) {
      super(ctx);
      parts = new RikaRenderer.Parts(ctx.bakeLayer(LAYER));
      this.shadowRadius = 1.1F;
   }

   public static LayerDefinition createLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      add(root, "skull", 0, 0, -8.0F, -12.0F, -12.0F, 16, 12, 22);
      add(root, "chest", 0, 34, -10.0F, 0.0F, -6.0F, 20, 12, 12);
      add(root, "jaw", 64, 34, -7.5F, 0.0F, -11.0F, 15, 4, 11);
      add(root, "fringe", 0, 58, -9.0F, 0.0F, -4.0F, 18, 6, 4);
      add(root, "mouth", 44, 58, -7.0F, 0.0F, -9.0F, 14, 6, 9);
      add(root, "teeth", 90, 58, -7.5F, 0.0F, -1.0F, 15, 4, 2);
      add(root, "belly", 0, 73, -7.0F, 0.0F, -5.0F, 14, 8, 10);
      add(root, "hips", 48, 73, -7.0F, 0.0F, -5.0F, 14, 5, 10);
      add(root, "shoulder", 0, 91, -4.5F, -4.0F, -4.5F, 9, 8, 9);
      add(root, "neck", 36, 91, -4.0F, -6.0F, -4.0F, 8, 6, 8);
      add(root, "hand", 68, 91, -5.0F, 0.0F, -5.0F, 10, 5, 10);
      add(root, "thigh", 0, 108, -4.0F, 0.0F, -4.0F, 8, 13, 8);
      add(root, "shin", 32, 108, -3.5F, 0.0F, -3.5F, 7, 13, 7);
      add(root, "foot", 60, 108, -4.5F, 0.0F, -8.0F, 9, 4, 12);
      add(root, "upper_arm", 102, 108, -3.0F, 0.0F, -3.0F, 6, 10, 6);
      add(root, "fore_arm", 0, 129, -2.5F, 0.0F, -2.5F, 5, 12, 5);
      add(root, "finger", 20, 129, -1.5F, -1.5F, 0.0F, 3, 3, 10);
      add(root, "thumb", 46, 129, -1.5F, -1.5F, 0.0F, 3, 3, 6);
      add(root, "strand", 64, 129, -2.0F, -1.0F, 0.0F, 4, 2, 12);
      add(root, "flap", 96, 129, -1.5F, 0.0F, -0.5F, 3, 7, 1);
      return LayerDefinition.create(mesh, 128, 384);
   }

   private static void add(PartDefinition root, String name, int u, int v, float x, float y, float z, int w, int h, int d) {
      root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, (float)w, (float)h, (float)d), PartPose.ZERO);
   }

   static RikaRenderer.Parts parts() {
      if (parts == null) {
         parts = new RikaRenderer.Parts(Minecraft.getInstance().getEntityModels().bakeLayer(LAYER));
      }

      return parts;
   }

   public ResourceLocation getTextureLocation(RikaEntity e) {
      return TEX;
   }

   public boolean shouldRender(RikaEntity e, Frustum frustum, double x, double y, double z) {
      return e.distanceToSqr(x, y, z) < 25600.0;
   }

   static RikaRenderer.Pose pose(RikaEntity e, float partial) {
      RikaRenderer.Pose p = new RikaRenderer.Pose();
      float t = (float)e.tickCount + partial;
      p.breath = Mth.sin(t * 0.08F);
      p.sway = Mth.sin(t * 0.05F);
      p.jaw = 0.18F + 0.05F * Mth.sin(t * 0.11F);
      p.headPitch = 0.05F * p.breath;
      p.lean = 0.6F;
      p.armRx = -0.15F + 0.05F * p.sway;
      p.armRz = 0.18F;
      p.foreR = -0.15F;
      p.armLx = -0.95F - 0.05F * p.sway;
      p.armLz = -0.2F;
      p.foreL = -0.75F;
      p.knee = 0.55F + 0.05F * p.breath;
      p.stride = 0.25F;
      float a = (float)(e.level().getGameTime() - e.animAt) + partial;
      switch (e.anim) {
         case 110: {
            float k = Mth.clamp(a / 6.0F, 0.0F, 1.0F);
            k = k * k * (3.0F - 2.0F * k);
            p.armRx = Mth.lerp(k, p.armRx, -2.8F);
            p.armRz = Mth.lerp(k, p.armRz, 0.12F);
            p.foreR = Mth.lerp(k, p.foreR, -0.2F);
            p.jaw = Mth.lerp(k, p.jaw, 0.85F);
            p.lean = Mth.lerp(k, p.lean, 0.2F);
            break;
         }
         case 111: {
            float k = Mth.clamp(a / 6.0F, 0.0F, 1.0F);
            k *= k;
            float back = Mth.clamp((a - 16.0F) / 10.0F, 0.0F, 1.0F);
            p.armRx = Mth.lerp(back, Mth.lerp(k, -2.8F, -1.05F), p.armRx);
            p.armRz = Mth.lerp(back, 0.1F, p.armRz);
            p.foreR = Mth.lerp(back, Mth.lerp(k, -0.2F, -0.05F), p.foreR);
            p.lean = Mth.lerp(back, Mth.lerp(k, 0.2F, 0.95F), p.lean);
            p.jaw = Mth.lerp(back, 0.95F, p.jaw);
            p.headPitch = Mth.lerp(back, 0.3F * k, p.headPitch);
            p.knee = Mth.lerp(back, Mth.lerp(k, 0.65F, 1.0F), p.knee);
            break;
         }
         case 112: {
            float k = a < 4.0F ? a / 4.0F : Mth.clamp(1.0F - (a - 16.0F) / 8.0F, 0.0F, 1.0F);
            if (a < 24.0F) {
               p.jaw = Mth.lerp(k, p.jaw, 1.0F + 0.05F * Mth.sin(a * 2.3F));
               p.headPitch = Mth.lerp(k, p.headPitch, -0.6F);
               p.armRz = Mth.lerp(k, p.armRz, 1.15F);
               p.armLz = Mth.lerp(k, p.armLz, -1.15F);
               p.armRx = Mth.lerp(k, p.armRx, -0.9F);
               p.armLx = Mth.lerp(k, p.armLx, -0.9F);
               p.foreL = Mth.lerp(k, p.foreL, -0.3F);
               p.lean = Mth.lerp(k, p.lean, 0.15F);
            }
            break;
         }
         case 113:
            if (a < 14.0F) {
               float kx = Mth.clamp(a / 5.0F, 0.0F, 1.0F);
               float out = Mth.clamp(1.0F - (a - 8.0F) / 6.0F, 0.0F, 1.0F);
               p.armRx = Mth.lerp(out, p.armRx, -1.5F);
               p.armRy = Mth.lerp(out, 0.0F, Mth.lerp(kx, 1.0F, -0.9F));
               p.foreR = Mth.lerp(out, p.foreR, -0.15F);
               p.jaw = Mth.lerp(out, p.jaw, 0.8F);
            }
      }

      return p;
   }

   public void render(RikaEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
      RikaRenderer.Parts m = parts();
      RikaRenderer.Pose p = pose(e, partial);
      VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
      int ov = OverlayTexture.pack(0, e.hurtTime > 0 ? 3 : 10);
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
      float t = (float)e.tickCount + partial;
      ps.pushPose();
      float big = e.full() ? 1.35F : 1.0F;
      ps.translate(0.0F, (3.75F + 0.05F * p.breath) * big, 0.0F);
      ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
      ps.scale(-1.2F * big, -1.2F * big, 1.2F * big);
      renderLegs(m, ps, vc, light, ov, p, t);
      ps.pushPose();
      ps.mulPose(Axis.XP.rotation(p.lean));
      m.chest.render(ps, vc, light, ov);
      ps.pushPose();
      px(ps, 0.0, 11.5, 0.5);
      ps.mulPose(Axis.XP.rotation(-p.lean * 0.55F));
      m.belly.render(ps, vc, light, ov);
      px(ps, 0.0, 7.5, 0.0);
      ps.mulPose(Axis.XP.rotation(-p.lean * 0.3F));
      m.hips.render(ps, vc, light, ov);

      for (int i = 0; i < 9; i++) {
         float fx = -6.0F + (float)i * 1.5F;
         ps.pushPose();
         px(ps, (double)fx, 4.5, i % 2 == 0 ? -5.3 : 5.3);
         ps.mulPose(Axis.XP.rotation((i % 2 == 0 ? -0.18F : 0.18F) + 0.05F * Mth.sin(t * 0.08F + (float)i)));
         ps.mulPose(Axis.ZP.rotation((float)(i - 4) * 0.04F));
         m.flap.render(ps, vc, light, ov);
         ps.popPose();
      }

      ps.popPose();
      ps.pushPose();
      px(ps, 0.0, 1.5, -4.5);
      ps.mulPose(Axis.XP.rotation(-0.75F));
      m.neck.render(ps, vc, light, ov);
      px(ps, 0.0, -5.5, -1.0);
      ps.mulPose(Axis.XP.rotation(0.75F - p.lean * 0.9F + p.headPitch));
      ps.scale(1.25F, 1.25F, 1.25F);
      renderHead(m, ps, vc, light, ov, p, t);
      ps.popPose();
      renderArm(m, ps, vc, light, ov, -1, p.armRx, p.armRy, p.armRz, p.foreR, false, t);
      renderArm(m, ps, vc, light, ov, 1, p.armLx, p.armLy, p.armLz, p.foreL, true, t);
      ps.popPose();
      ps.popPose();
      super.render(e, yaw, partial, ps, buf, light);
   }

   private static void renderLegs(RikaRenderer.Parts m, PoseStack ps, VertexConsumer vc, int light, int ov, RikaRenderer.Pose p, float t) {
      float down = 12.0F * Mth.cos(p.lean) + 7.5F + 4.5F;
      float back = 12.0F * Mth.sin(p.lean) + 2.5F;

      for (int side = -1; side <= 1; side += 2) {
         float step = (float)side * p.stride * Mth.sin(t * 0.04F + (side > 0 ? 0.0F : 1.5F)) * 0.3F;
         ps.pushPose();
         px(ps, (double)side * 4.0, (double)down, (double)back);
         ps.mulPose(Axis.XP.rotation(-p.knee * 0.7F + step + (side > 0 ? 0.15F : -0.1F)));
         ps.mulPose(Axis.ZP.rotation((float)side * 0.06F));
         m.thigh.render(ps, vc, light, ov);
         px(ps, 0.0, 12.5, 0.0);
         ps.mulPose(Axis.XP.rotation(p.knee * 1.25F));
         m.shin.render(ps, vc, light, ov);
         px(ps, 0.0, 12.5, 0.0);
         ps.mulPose(Axis.XP.rotation(-p.knee * 0.55F - step));
         m.foot.render(ps, vc, light, ov);
         ps.popPose();
      }
   }

   private static void renderHead(RikaRenderer.Parts m, PoseStack ps, VertexConsumer vc, int light, int ov, RikaRenderer.Pose p, float t) {
      m.skull.render(ps, vc, light, ov);
      ps.pushPose();
      px(ps, 0.0, -12.5, -11.5);
      ps.mulPose(Axis.XP.rotation(-0.15F));
      m.fringe.render(ps, vc, light, ov);
      ps.popPose();

      for (int i = 0; i < 5; i++) {
         ps.pushPose();
         px(ps, (double)(-6.5F + (float)i * 3.2F), -12.0, (double)(-10.0F + (float)(i % 2) * 2.0F));
         ps.mulPose(Axis.YP.rotation((float)(i - 2) * 0.12F));
         ps.mulPose(Axis.XP.rotation(0.55F + 0.15F * (float)(i % 2) + 0.04F * Mth.sin(t * 0.07F + (float)i)));
         m.strand.render(ps, vc, light, ov);
         ps.popPose();
      }

      ps.pushPose();
      px(ps, 0.0, 0.0, -3.0);
      m.mouth.render(ps, vc, light, ov);
      px(ps, 0.0, 0.0, -9.0);
      m.teeth.render(ps, vc, light, ov);
      ps.popPose();
      ps.pushPose();
      px(ps, 0.0, 0.5, -1.0);
      ps.mulPose(Axis.XP.rotation(p.jaw));
      m.jaw.render(ps, vc, light, ov);
      ps.popPose();
   }

   private static void renderArm(
      RikaRenderer.Parts m, PoseStack ps, VertexConsumer vc, int light, int ov, int side, float rx, float ry, float rz, float fore, boolean fist, float t
   ) {
      ps.pushPose();
      px(ps, (double)side * 11.5, 2.5, -1.5);
      m.shoulder.render(ps, vc, light, ov);
      px(ps, 0.0, 2.5, 0.0);
      ps.mulPose(Axis.YP.rotation(ry * (float)(-side)));
      ps.mulPose(Axis.XP.rotation(rx));
      ps.mulPose(Axis.ZP.rotation(rz * (float)side * -1.0F));
      m.upperArm.render(ps, vc, light, ov);
      px(ps, 0.0, 9.5, 0.0);
      ps.mulPose(Axis.XP.rotation(fore));
      m.foreArm.render(ps, vc, light, ov);
      px(ps, 0.0, 11.5, 0.0);
      ps.mulPose(Axis.XP.rotation(0.08F * Mth.sin(t * 0.09F + (float)side)));
      m.hand.render(ps, vc, light, ov);
      float curl = fist ? 2.0F : 0.12F + 0.05F * Mth.sin(t * 0.07F);

      for (int f = 0; f < 4; f++) {
         ps.pushPose();
         px(ps, (double)(-3.75F + (float)f * 2.5F), 4.5, -2.5);
         ps.mulPose(Axis.XP.rotation((float) (-Math.PI / 2) + curl));
         ps.mulPose(Axis.YP.rotation(((float)f - 1.5F) * (fist ? 0.0F : 0.06F)));
         m.finger.render(ps, vc, light, ov);
         ps.popPose();
      }

      ps.pushPose();
      px(ps, (double)((float)side * -5.0F), 3.0, -2.0);
      ps.mulPose(Axis.YP.rotation((float)side * 0.6F));
      ps.mulPose(Axis.XP.rotation((float) (-Math.PI / 2) + curl * 0.6F));
      m.thumb.render(ps, vc, light, ov);
      ps.popPose();
      ps.popPose();
   }

   private static void px(PoseStack ps, double x, double y, double z) {
      ps.translate(x / 16.0, y / 16.0, z / 16.0);
   }

   static Vec3 handPos(RikaEntity e, float partial, int side) {
      RikaRenderer.Pose p = pose(e, partial);
      float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * (float) (Math.PI / 180.0);
      double reach = 2.250000089406967;
      double fwd = -Math.sin((double)p.armRx) * reach;
      double down = Math.cos((double)p.armRx) * reach;
      Vec3 local = new Vec3((double)side * 11.5 / 16.0 * 1.2F, 3.75 - down, fwd + 0.4);
      Vec3 f = new Vec3((double)(-Mth.sin(bodyYaw)), 0.0, (double)Mth.cos(bodyYaw));
      Vec3 r = new Vec3(-f.z, 0.0, f.x);
      return e.getPosition(partial).add(f.scale(local.z)).add(r.scale(-local.x)).add(0.0, local.y, 0.0);
   }

   static final class Parts {
      final ModelPart chest;
      final ModelPart skull;
      final ModelPart fringe;
      final ModelPart jaw;
      final ModelPart mouth;
      final ModelPart teeth;
      final ModelPart belly;
      final ModelPart hips;
      final ModelPart flap;
      final ModelPart neck;
      final ModelPart shoulder;
      final ModelPart upperArm;
      final ModelPart foreArm;
      final ModelPart hand;
      final ModelPart finger;
      final ModelPart thumb;
      final ModelPart strand;
      final ModelPart thigh;
      final ModelPart shin;
      final ModelPart foot;

      Parts(ModelPart root) {
         this.chest = root.getChild("chest");
         this.skull = root.getChild("skull");
         this.fringe = root.getChild("fringe");
         this.flap = root.getChild("flap");
         this.jaw = root.getChild("jaw");
         this.mouth = root.getChild("mouth");
         this.teeth = root.getChild("teeth");
         this.belly = root.getChild("belly");
         this.hips = root.getChild("hips");
         this.neck = root.getChild("neck");
         this.shoulder = root.getChild("shoulder");
         this.upperArm = root.getChild("upper_arm");
         this.foreArm = root.getChild("fore_arm");
         this.hand = root.getChild("hand");
         this.finger = root.getChild("finger");
         this.thumb = root.getChild("thumb");
         this.strand = root.getChild("strand");
         this.thigh = root.getChild("thigh");
         this.shin = root.getChild("shin");
         this.foot = root.getChild("foot");
      }
   }

   static final class Pose {
      float jaw;
      float headPitch;
      float lean;
      float armRx;
      float armRy;
      float armRz;
      float foreR;
      float armLx;
      float armLy;
      float armLz;
      float foreL;
      float sway;
      float breath;
      float knee;
      float stride;
   }

   @EventBusSubscriber(
      modid = "jjk",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class Reg {
      @SubscribeEvent
      public static void onLayers(RegisterLayerDefinitions e) {
         e.registerLayerDefinition(RikaRenderer.LAYER, RikaRenderer::createLayer);
      }

      @SubscribeEvent
      public static void onRenderers(RegisterRenderers e) {
         e.registerEntityRenderer((EntityType)YutaMod.RIKA.get(), RikaRenderer::new);
      }
   }
}
