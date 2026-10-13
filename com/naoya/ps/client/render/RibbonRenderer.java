package com.naoya.ps.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.naoya.ps.client.NFx;
import com.naoya.ps.client.fx.Ribbon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;

public final class RibbonRenderer {
   private static final List<Ribbon> RIBBONS = new ArrayList<>();

   private RibbonRenderer() {
   }

   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES) {
         RIBBONS.clear();
         NFx.ribbons(RIBBONS, event.getPartialTick());
         if (!RIBBONS.isEmpty()) {
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            Matrix4f m = pose.last().pose();
            BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            VertexConsumer ink = buffers.getBuffer(RenderType.debugQuads());

            for (Ribbon r : RIBBONS) {
               if (r.style == Ribbon.Style.INK) {
                  emit(ink, m, r, cam, false);
               }
            }

            buffers.endBatch(RenderType.debugQuads());
            VertexConsumer tint = buffers.getBuffer(FxTypes.TINT);

            for (Ribbon rx : RIBBONS) {
               if (rx.style == Ribbon.Style.TINT) {
                  emit(tint, m, rx, cam, false);
               }
            }

            buffers.endBatch(FxTypes.TINT);
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());

            for (Ribbon rxx : RIBBONS) {
               if (rxx.style == Ribbon.Style.GLOW) {
                  emit(glow, m, rxx, cam, true);
               }
            }

            buffers.endBatch(RenderType.lightning());
         }
      }
   }

   private static void emit(VertexConsumer vc, Matrix4f m, Ribbon r, Vec3 cam, boolean twoSided) {
      int n = r.spine.length;
      if (n >= 2) {
         float cr = (float)(r.rgb >> 16 & 0xFF) / 255.0F;
         float cg = (float)(r.rgb >> 8 & 0xFF) / 255.0F;
         float cb = (float)(r.rgb & 0xFF) / 255.0F;
         Vec3[] left = new Vec3[n];
         Vec3[] right = new Vec3[n];

         for (int i = 0; i < n; i++) {
            Vec3 p = r.spine[i].subtract(cam);
            Vec3 side;
            if (r.sides != null) {
               side = r.sides[i];
            } else {
               Vec3 tangent = r.spine[Math.min(n - 1, i + 1)].subtract(r.spine[Math.max(0, i - 1)]);
               side = tangent.cross(p);
               if (side.lengthSqr() < 1.0E-8) {
                  side = new Vec3(0.0, 1.0, 0.0);
               }
            }

            side = side.normalize().scale((double)r.width[i] * 0.5);
            left[i] = p.subtract(side);
            right[i] = p.add(side);
         }

         for (int i = 0; i < n - 1; i++) {
            float a0 = Math.max(0.0F, Math.min(1.0F, r.alpha[i]));
            float a1 = Math.max(0.0F, Math.min(1.0F, r.alpha[i + 1]));
            if (!(a0 < 0.003F) || !(a1 < 0.003F)) {
               float m0 = twoSided ? a0 : 1.0F;
               float m1 = twoSided ? a1 : 1.0F;
               v(vc, m, left[i], cr * m0, cg * m0, cb * m0, a0);
               v(vc, m, right[i], cr * m0, cg * m0, cb * m0, a0);
               v(vc, m, right[i + 1], cr * m1, cg * m1, cb * m1, a1);
               v(vc, m, left[i + 1], cr * m1, cg * m1, cb * m1, a1);
               if (twoSided) {
                  v(vc, m, left[i + 1], cr * m1, cg * m1, cb * m1, a1);
                  v(vc, m, right[i + 1], cr * m1, cg * m1, cb * m1, a1);
                  v(vc, m, right[i], cr * m0, cg * m0, cb * m0, a0);
                  v(vc, m, left[i], cr * m0, cg * m0, cb * m0, a0);
               }
            }
         }
      }
   }

   private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
      vc.vertex(m, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, a).endVertex();
   }
}
