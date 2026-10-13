package com.geto.csm.client;

import com.geto.csm.entity.AtroxiiaEntity;
import com.geto.csm.entity.CindervaneEntity;
import com.geto.csm.entity.DragonEntity;
import com.geto.csm.entity.RaevyxEntity;
import com.geto.csm.entity.WyrmEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

public final class DragonRenderers {
   static final Vector3f WYRM_SEAT = new Vector3f(0.0F, 0.8F, 0.0F);

   private DragonRenderers() {
   }

   static ResourceLocation rl(String path) {
      return new ResourceLocation("cursemanip", path);
   }

   static Vector3f v(float x, float y, float z) {
      return new Vector3f(x, y, z);
   }

   static <T extends DragonEntity> GeoEntityRenderer<T> base(GeoEntityRenderer<T> r, float scale, float shadow, boolean glow, String saddle) {
      r.withScale(scale);
      if (glow) {
         r.addRenderLayer(new DragonRenderers.Glow(r));
      }

      if (saddle != null) {
         r.addRenderLayer(new DragonRenderers.SaddleLayer(r, rl("textures/entity/" + saddle + ".png")));
      }

      return r;
   }

   public static final class Atroxiia extends DragonRenderers.DragonGeoRenderer<AtroxiiaEntity> {
      public Atroxiia(Context ctx) {
         super(
            ctx,
            new DragonRenderers.DragonModel<>(
               "atroxiia",
               new String[]{"neck1Controller", "neck2Controller", "headController"},
               new float[]{0.15F, 0.3F, 0.45F},
               new String[]{"tail1", "tail2", "tail3", "tail4", "tail5", "tail6"},
               new float[]{0.3F, 0.35F, 0.4F, 0.75F, 0.8F, 1.0F}
            ),
            "passengerBone",
            DragonRenderers.v(0.0F, -0.66875F, -0.2625F)
         );
         DragonRenderers.base(this, 1.0F, 2.0F, true, "atroxiia_saddle");
         this.shadowRadius = 2.0F;
      }
   }

   public static final class Cindervane extends DragonRenderers.DragonGeoRenderer<CindervaneEntity> {
      public Cindervane(Context ctx) {
         super(
            ctx,
            new DragonRenderers.DragonModel<>(
               "cindervane",
               new String[]{"neck1Controller", "neck2Controller", "headController"},
               new float[]{0.2F, 0.35F, 0.45F},
               new String[]{"tail1", "tail2", "tail3", "tail4"},
               new float[]{0.3F, 0.5F, 0.8F, 1.0F}
            ),
            "passengerBone1",
            DragonRenderers.v(0.0F, -0.52F, -0.46F)
         );
         DragonRenderers.base(this, 1.0F, 1.4F, true, "cindervane_saddle");
         this.shadowRadius = 1.4F;
      }
   }

   static class DragonGeoRenderer<T extends DragonEntity> extends GeoEntityRenderer<T> {
      private static final float MAX_TILT = 0.45F;
      private final String seatBone;
      private final Vector3f seatOffset;
      boolean drawSaddle;
      private static final ResourceLocation LEATHER = new ResourceLocation("minecraft", "textures/block/brown_terracotta.png");
      private static final ResourceLocation STRAP = new ResourceLocation("minecraft", "textures/block/black_terracotta.png");

      DragonGeoRenderer(Context ctx, GeoModel<T> model, String seatBone, Vector3f seatOffset) {
         super(ctx, model);
         this.seatBone = seatBone;
         this.seatOffset = seatOffset;
      }

      public void render(T e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         super.render(e, yaw, partial, ps, buf, light);
         if (e.isSaddled() && e.clientSeatMat != null && !(e.fade(partial) < 0.95F)) {
            float by = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * (float) (Math.PI / 180.0);
            if (this.drawSaddle) {
               ps.pushPose();
               ps.mulPoseMatrix(e.clientSeatMat);
               ps.mulPose(Axis.YP.rotation(-by));
               VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(LEATHER));
               box(ps, vc, light, -0.42F, 0.28F, -0.55F, 0.42F, 0.48F, 0.55F);
               box(ps, vc, light, -0.3F, 0.48F, 0.38F, 0.3F, 0.72F, 0.55F);
               box(ps, vc, light, -0.36F, 0.48F, -0.55F, 0.36F, 0.66F, -0.4F);
               VertexConsumer st = buf.getBuffer(RenderType.entityCutoutNoCull(STRAP));
               box(ps, st, light, -0.5F, -0.6F, -0.08F, -0.42F, 0.46F, 0.08F);
               box(ps, st, light, 0.42F, -0.6F, -0.08F, 0.5F, 0.46F, 0.08F);
               box(ps, st, light, -0.62F, -0.72F, -0.14F, -0.42F, -0.6F, 0.14F);
               box(ps, st, light, 0.42F, -0.72F, -0.14F, 0.62F, -0.6F, 0.14F);
               ps.popPose();
            }

            if (e.isVehicle() && e.clientHead != null) {
               Vector3f seat = e.clientSeatMat.transformPosition(new Vector3f());
               Vector3f up = e.clientSeatMat.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F));
               Vec3 f = new Vec3((double)(-Mth.sin(by)), 0.0, (double)Mth.cos(by));
               Vec3 s = new Vec3(-f.z, 0.0, f.x);
               Vec3 hands = new Vec3((double)(seat.x + up.x), (double)(seat.y + up.y), (double)(seat.z + up.z)).add(f.scale(0.55));
               Vec3 jaw = e.clientHead.add(f.scale(0.4));
               VertexConsumer lv = buf.getBuffer(RenderType.entityCutoutNoCull(LEATHER));

               for (int side = -1; side <= 1; side += 2) {
                  rein(ps, lv, hands.add(s.scale(0.28 * (double)side)), jaw.add(s.scale(0.35 * (double)side)), light);
               }
            }
         }
      }

      private static void box(PoseStack ps, VertexConsumer vc, int light, float x0, float y0, float z0, float x1, float y1, float z1) {
         Matrix4f m = ps.last().pose();
         Matrix3f n = ps.last().normal();
         float[][] faces = new float[][]{
            {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0.0F, 1.0F, 0.0F},
            {x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0.0F, -1.0F, 0.0F},
            {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0.0F, 0.0F, -1.0F},
            {x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0.0F, 0.0F, 1.0F},
            {x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1.0F, 0.0F, 0.0F},
            {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1.0F, 0.0F, 0.0F}
         };
         float[][] uv = new float[][]{{0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}, {0.0F, 0.0F}};

         for (float[] q : faces) {
            for (int i = 0; i < 4; i++) {
               vc.vertex(m, q[i * 3], q[i * 3 + 1], q[i * 3 + 2])
                  .color(255, 255, 255, 255)
                  .uv(uv[i][0], uv[i][1])
                  .overlayCoords(OverlayTexture.NO_OVERLAY)
                  .uv2(light)
                  .normal(n, q[12], q[13], q[14])
                  .endVertex();
            }
         }
      }

      private static void rein(PoseStack ps, VertexConsumer vc, Vec3 a, Vec3 b, int light) {
         Matrix4f m = ps.last().pose();
         Matrix3f n = ps.last().normal();
         int seg = 16;
         float w = 0.04F;
         Vec3 prev = a;

         for (int i = 1; i <= seg; i++) {
            float k = (float)i / (float)seg;
            Vec3 p = a.lerp(b, (double)k).add(0.0, -Math.sin((double)k * Math.PI) * 0.35, 0.0);
            float[][] q = new float[][]{
               {(float)prev.x, (float)prev.y + w, (float)prev.z},
               {(float)p.x, (float)p.y + w, (float)p.z},
               {(float)p.x, (float)p.y - w, (float)p.z},
               {(float)prev.x, (float)prev.y - w, (float)prev.z}
            };

            for (int j = 0; j < 4; j++) {
               vc.vertex(m, q[j][0], q[j][1], q[j][2])
                  .color(150, 110, 80, 255)
                  .uv(j != 1 && j != 2 ? 0.0F : 0.1F, j >= 2 ? 1.0F : 0.0F)
                  .overlayCoords(OverlayTexture.NO_OVERLAY)
                  .uv2(light)
                  .normal(n, 0.0F, 1.0F, 0.0F)
                  .endVertex();
            }

            prev = p;
         }
      }

      public RenderType getRenderType(T e, ResourceLocation tex, MultiBufferSource buf, float partial) {
         return e.fade(partial) < 0.999F ? RenderType.entityTranslucent(tex) : super.getRenderType(e, tex, buf, partial);
      }

      public void actuallyRender(
         PoseStack ps,
         T e,
         BakedGeoModel model,
         RenderType type,
         MultiBufferSource buf,
         VertexConsumer vc,
         boolean reRender,
         float partial,
         int light,
         int overlay,
         float r,
         float g,
         float b,
         float a
      ) {
         float f = reRender ? 1.0F : e.fade(partial);
         super.actuallyRender(
            ps,
            e,
            model,
            type,
            buf,
            vc,
            reRender,
            partial,
            light,
            overlay,
            r * (0.15F + 0.85F * f * f),
            g * (0.05F + 0.95F * f * f),
            b * (0.3F + 0.7F * f * f),
            a * f
         );
      }

      private Matrix4f boneMatrix(PoseStack ps, GeoBone bone) {
         ps.pushPose();

         Matrix4f var3;
         try {
            RenderUtils.translateMatrixToBone(ps, bone);
            RenderUtils.translateToPivotPoint(ps, bone);
            RenderUtils.rotateMatrixAroundBone(ps, bone);
            RenderUtils.scaleMatrixForBone(ps, bone);
            var3 = RenderUtils.invertAndMultiplyMatrices(ps.last().pose(), this.entityRenderTranslations);
         } finally {
            ps.popPose();
         }

         return var3;
      }

      public void renderRecursively(
         PoseStack ps,
         T e,
         GeoBone bone,
         RenderType type,
         MultiBufferSource buf,
         VertexConsumer vc,
         boolean reRender,
         float partial,
         int light,
         int overlay,
         float r,
         float g,
         float b,
         float a
      ) {
         super.renderRecursively(ps, e, bone, type, buf, vc, reRender, partial, light, overlay, r, g, b, a);
         if (!reRender) {
            if (bone.getName().equals("headController")) {
               Vector3f h = this.boneMatrix(ps, bone).transformPosition(new Vector3f());
               e.clientHead = new Vec3((double)h.x, (double)h.y, (double)h.z);
            }

            if (bone.getName().equals(this.seatBone) && (e.isVehicle() || e.isSaddled())) {
               Matrix4f bm = this.boneMatrix(ps, bone);
               Vector3f pivot = bm.transformPosition(new Vector3f());
               Vector3f up = bm.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
               if (up.y < 0.0F) {
                  up.negate();
               }

               float tilt = (float)Math.acos((double)Mth.clamp(up.y, -1.0F, 1.0F));
               if (tilt > 0.45F) {
                  Vector3f h = new Vector3f(up.x, 0.0F, up.z).normalize();
                  up.set(h.x * Mth.sin(0.45F), Mth.cos(0.45F), h.z * Mth.sin(0.45F));
               }

               float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot) * (float) (Math.PI / 180.0);
               Vector3f fwd = new Vector3f(-Mth.sin(bodyYaw), 0.0F, Mth.cos(bodyYaw));
               Vector3f p = new Vector3f(pivot).add(new Vector3f(up).mul(this.seatOffset.y)).add(fwd.mul(this.seatOffset.z));
               Matrix4f seat = new Matrix4f().translation(p).rotate(new Quaternionf().rotateTo(0.0F, 1.0F, 0.0F, up.x, up.y, up.z));
               e.clientSeatMat = seat;
               e.clientSeatNanos = System.nanoTime();
               e.clientSeat = new Vec3((double)p.x, (double)p.y, (double)p.z);
               e.clientSeatTime = e.level().getGameTime();
            }
         }
      }
   }

   static class DragonModel<T extends DragonEntity> extends GeoModel<T> {
      private final String name;
      private final String[] neck;
      private final String[] tail;
      private final float[] neckW;
      private final float[] tailW;

      DragonModel(String name, String[] neck, float[] neckW, String[] tail, float[] tailW) {
         this.name = name;
         this.neck = neck;
         this.tail = tail;
         this.tailW = tailW;
         float sum = 0.0F;

         for (float w : neckW) {
            sum += w;
         }

         this.neckW = new float[neckW.length];

         for (int i = 0; i < neckW.length; i++) {
            this.neckW[i] = neckW[i] / sum;
         }
      }

      public ResourceLocation getModelResource(T e) {
         return DragonRenderers.rl("geo/entity/" + this.name + ".geo.json");
      }

      public ResourceLocation getTextureResource(T e) {
         return DragonRenderers.rl("textures/entity/" + this.name + ".png");
      }

      public ResourceLocation getAnimationResource(T e) {
         return DragonRenderers.rl("animations/entity/" + this.name + ".animation.json");
      }

      public void setCustomAnimations(T e, long id, AnimationState<T> state) {
         super.setCustomAnimations(e, id, state);
         EntityModelData data = (EntityModelData)state.getData(DataTickets.ENTITY_MODEL_DATA);
         if (data != null && e.isAlive() && !e.isVehicle()) {
            float yaw = Mth.clamp(Mth.wrapDegrees(data.netHeadYaw()), -50.0F, 50.0F) * (float) (Math.PI / 180.0);
            float pitch = Mth.clamp(data.headPitch(), -35.0F, 35.0F) * (float) (Math.PI / 180.0) * 0.5F;

            for (int i = 0; i < this.neck.length; i++) {
               float w = this.neckW[i];
               this.getBone(this.neck[i]).ifPresent(b -> {
                  b.setRotY(b.getRotY() + yaw * w);
                  b.setRotX(b.getRotX() + pitch * w);
               });
            }

            float turn = Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO) * (float) (Math.PI / 180.0);

            for (int i = 0; i < this.tail.length; i++) {
               float w = this.tailW[i];
               this.getBone(this.tail[i]).ifPresent(b -> b.setRotY(b.getRotY() - Mth.clamp(turn * 5.0F, -0.4F, 0.4F) * w));
            }
         }
      }
   }

   static final class Glow<T extends DragonEntity> extends AutoGlowingGeoLayer<T> {
      Glow(GeoEntityRenderer<T> r) {
         super(r);
      }

      public void render(
         PoseStack ps, T e, BakedGeoModel model, RenderType type, MultiBufferSource buf, VertexConsumer vc, float partial, int light, int overlay
      ) {
         if (e.fade(partial) > 0.9F) {
            super.render(ps, e, model, type, buf, vc, partial, light, overlay);
         }
      }
   }

   public static final class Raevyx extends DragonRenderers.DragonGeoRenderer<RaevyxEntity> {
      public Raevyx(Context ctx) {
         super(
            ctx,
            new DragonRenderers.DragonModel<>(
               "raevyx",
               new String[]{"neck1Controller", "neck2Controller", "headController"},
               new float[]{0.2F, 0.35F, 0.45F},
               new String[]{"tail1", "tail2", "tail3", "tail4", "tail5"},
               new float[]{0.3F, 0.45F, 0.6F, 0.8F, 1.0F}
            ),
            "passengerBone",
            DragonRenderers.v(0.0F, -0.6F, -0.59F)
         );
         DragonRenderers.base(this, 1.0F, 2.2F, true, null);
         this.drawSaddle = true;
         this.shadowRadius = 2.2F;
      }
   }

   static final class SaddleLayer<T extends DragonEntity> extends GeoRenderLayer<T> {
      private final ResourceLocation tex;

      SaddleLayer(GeoEntityRenderer<T> r, ResourceLocation tex) {
         super(r);
         this.tex = tex;
      }

      public void render(
         PoseStack ps, T e, BakedGeoModel model, RenderType type, MultiBufferSource buf, VertexConsumer vc, float partial, int light, int overlay
      ) {
         if (e.isSaddled()) {
            float f = e.fade(partial);
            RenderType rt = f < 0.999F ? RenderType.entityTranslucent(this.tex) : RenderType.entityCutoutNoCull(this.tex);
            this.getRenderer().reRender(model, ps, buf, e, rt, buf.getBuffer(rt), partial, light, overlay, 1.0F, 1.0F, 1.0F, f);
         }
      }
   }

   public static final class Wyrm extends DragonRenderers.DragonGeoRenderer<WyrmEntity> {
      public Wyrm(Context ctx) {
         super(
            ctx,
            new DragonRenderers.DragonModel<>(
               "nulljaw",
               new String[]{"neck1Controller", "neck2Controller", "neck3Controller", "headController"},
               new float[]{0.25F, 0.45F, 0.65F, 0.85F},
               new String[]{"tail1", "tail2", "tail3", "tail4", "tail5"},
               new float[]{0.45F, 0.7F, 0.95F, 1.2F, 1.45F}
            ),
            "neck1",
            DragonRenderers.WYRM_SEAT
         );
         DragonRenderers.base(this, 3.0F, 3.9F, false, null);
         this.drawSaddle = true;
         this.shadowRadius = 3.9F;
      }
   }
}
