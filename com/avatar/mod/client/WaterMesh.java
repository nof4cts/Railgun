package com.avatar.mod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class WaterMesh {
   private static final List<WaterMesh.Tube> TUBES = new ArrayList<>();
   private static final List<float[]> SPHERES = new ArrayList<>();
   private static final List<List<List<Vec3>>> SHEETS = new ArrayList<>();
   private static final ResourceLocation STILL = new ResourceLocation("minecraft", "block/water_still");
   private static final ResourceLocation FLOW = new ResourceLocation("minecraft", "block/water_flow");
   static final float[] WATER = new float[]{0.42F, 0.68F, 1.0F, 0.72F};
   static final float[] FOAM = new float[]{0.95F, 0.98F, 1.0F, 0.9F};

   private WaterMesh() {
   }

   static boolean empty() {
      return TUBES.isEmpty() && SPHERES.isEmpty() && SHEETS.isEmpty();
   }

   static void tube(List<Vec3> pts, float r, float[] col, float alpha) {
      if (pts.size() >= 2 && alpha > 0.02F) {
         TUBES.add(new WaterMesh.Tube(new ArrayList<>(pts), r, col, alpha));
      }
   }

   static void sphere(Vec3 c, float r, float[] col, float alpha) {
      if (alpha > 0.02F) {
         SPHERES.add(new float[]{(float)c.x, (float)c.y, (float)c.z, r, col[0], col[1], col[2], col[3] * alpha});
      }
   }

   static void sheet(List<List<Vec3>> rows) {
      if (rows.size() >= 2) {
         SHEETS.add(rows);
      }
   }

   static void flush(Minecraft mc, PoseStack ps, BufferSource buf, Vec3 cam, float time) {
      if (!empty()) {
         TextureAtlasSprite still = (TextureAtlasSprite)mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(STILL);
         TextureAtlasSprite flow = (TextureAtlasSprite)mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(FLOW);
         VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
         Matrix4f m = ps.last().pose();
         Matrix3f nm = ps.last().normal();

         for (WaterMesh.Tube t : TUBES) {
            drawTube(mc, vc, m, nm, cam, t, flow, time);
         }

         for (float[] s : SPHERES) {
            drawSphere(mc, vc, m, nm, cam, s, still);
         }

         for (List<List<Vec3>> rows : SHEETS) {
            drawSheet(mc, vc, m, nm, cam, rows, flow, time);
         }

         buf.endBatch(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
         TUBES.clear();
         SPHERES.clear();
         SHEETS.clear();
      }
   }

   private static int light(Minecraft mc, Vec3 p) {
      int l = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
      int block = Math.max((l & 65535) >> 4, 5);
      int sky = l >> 20 & 15;
      return block << 4 | sky << 20;
   }

   private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, Vec3 p, float u, float v, float[] c, float a, int light, Vec3 n) {
      vc.vertex(m, (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z))
         .color(c[0], c[1], c[2], a)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(light)
         .normal(nm, (float)n.x, (float)n.y, (float)n.z)
         .endVertex();
   }

   private static void drawTube(Minecraft mc, VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, WaterMesh.Tube t, TextureAtlasSprite sp, float time) {
      int n = t.pts.size();
      int sides = 8;
      boolean closed = n > 3 && t.pts.get(0).distanceToSqr(t.pts.get(n - 1)) < 1.0E-6;
      Vec3 centre = Vec3.ZERO;
      if (closed) {
         for (int i = 0; i < n - 1; i++) {
            centre = centre.add(t.pts.get(i));
         }

         centre = centre.scale(1.0 / (double)(n - 1));
      }

      Vec3[][] ring = new Vec3[n][sides + 1];
      Vec3[][] norm = new Vec3[n][sides + 1];
      Vec3 prevN = null;

      for (int i = 0; i < n; i++) {
         Vec3 a = closed ? t.pts.get(i == 0 ? n - 2 : i - 1) : t.pts.get(Math.max(0, i - 1));
         Vec3 b = closed ? t.pts.get(i == n - 1 ? 1 : i + 1) : t.pts.get(Math.min(n - 1, i + 1));
         Vec3 tan = b.subtract(a);
         tan = tan.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : tan.normalize();
         Vec3 nrm = closed
            ? t.pts.get(i).subtract(centre)
            : (prevN == null ? (Math.abs(tan.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0)) : prevN);
         nrm = nrm.subtract(tan.scale(nrm.dot(tan)));
         nrm = nrm.lengthSqr() < 1.0E-8 ? new Vec3(1.0, 0.0, 0.0) : nrm.normalize();
         prevN = nrm;
         Vec3 bin = tan.cross(nrm).normalize();
         float x = (float)i / ((float)n - 1.0F);
         float r = closed ? t.r : t.r * (0.35F + 0.65F * Mth.sqrt(Mth.sin((float) Math.PI * Mth.clamp(x * 0.92F + 0.04F, 0.0F, 1.0F))));

         for (int j = 0; j <= sides; j++) {
            double ang = (double)j / (double)sides * Math.PI * 2.0;
            Vec3 d = nrm.scale(Math.cos(ang)).add(bin.scale(Math.sin(ang)));
            norm[i][j] = d;
            ring[i][j] = t.pts.get(i).add(d.scale((double)r));
         }
      }

      int light = light(mc, t.pts.get(n / 2));
      float a = t.col[3] * t.alpha;
      float scroll = time * 0.05F % 1.0F;

      for (int i = 0; i < n - 1; i++) {
         for (int j = 0; j < sides; j++) {
            float u0 = sp.getU((double)((float)j * 16.0F / (float)sides));
            float u1 = sp.getU((double)((float)(j + 1) * 16.0F / (float)sides));
            float v0 = sp.getV((double)(((float)i * 0.25F + scroll) % 1.0F * 8.0F));
            float v1 = sp.getV((double)(((float)i * 0.25F + scroll) % 1.0F * 8.0F + 2.0F));
            vert(vc, m, nm, cam, ring[i][j], u0, v0, t.col, a, light, norm[i][j]);
            vert(vc, m, nm, cam, ring[i + 1][j], u0, v1, t.col, a, light, norm[i + 1][j]);
            vert(vc, m, nm, cam, ring[i + 1][j + 1], u1, v1, t.col, a, light, norm[i + 1][j + 1]);
            vert(vc, m, nm, cam, ring[i][j + 1], u1, v0, t.col, a, light, norm[i][j + 1]);
         }
      }
   }

   private static void drawSphere(Minecraft mc, VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, float[] s, TextureAtlasSprite sp) {
      Vec3 c = new Vec3((double)s[0], (double)s[1], (double)s[2]);
      float r = s[3];
      float[] col = new float[]{s[4], s[5], s[6]};
      int light = light(mc, c);
      int lat = 6;
      int lon = 8;

      for (int i = 0; i < lat; i++) {
         for (int j = 0; j < lon; j++) {
            Vec3[] q = new Vec3[4];
            int[][] ij = new int[][]{{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};

            for (int k = 0; k < 4; k++) {
               double ph = Math.PI * (double)ij[k][0] / (double)lat;
               double th = (Math.PI * 2) * (double)ij[k][1] / (double)lon;
               q[k] = new Vec3(Math.sin(ph) * Math.cos(th), Math.cos(ph), Math.sin(ph) * Math.sin(th));
            }

            float u0 = sp.getU((double)((float)j * 16.0F / (float)lon));
            float u1 = sp.getU((double)((float)(j + 1) * 16.0F / (float)lon));
            float v0 = sp.getV((double)((float)i * 16.0F / (float)lat));
            float v1 = sp.getV((double)((float)(i + 1) * 16.0F / (float)lat));
            float[][] uv = new float[][]{{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};

            for (int k = 0; k < 4; k++) {
               vert(vc, m, nm, cam, c.add(q[k].scale((double)r)), uv[k][0], uv[k][1], col, s[7], light, q[k]);
            }
         }
      }
   }

   private static void drawSheet(Minecraft mc, VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, List<List<Vec3>> rows, TextureAtlasSprite sp, float time) {
      int light = light(mc, rows.get(rows.size() / 2).get(0));
      float scroll = time * 0.08F % 1.0F;

      for (int i = 0; i < rows.size() - 1; i++) {
         List<Vec3> a = rows.get(i);
         List<Vec3> b = rows.get(i + 1);
         int n = Math.min(a.size(), b.size());

         for (int j = 0; j < n - 1; j++) {
            Vec3 p0 = a.get(j);
            Vec3 p1 = b.get(j);
            Vec3 p2 = b.get(j + 1);
            Vec3 p3 = a.get(j + 1);
            Vec3 nrm = p1.subtract(p0).cross(p3.subtract(p0));
            nrm = nrm.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : nrm.normalize();
            float v0 = sp.getV((double)(((float)j * 0.2F + scroll) % 1.0F * 8.0F));
            float v1 = sp.getV((double)(((float)j * 0.2F + scroll) % 1.0F * 8.0F + 1.6F));
            float u0 = sp.getU((double)((float)(i % 4) * 4.0F));
            float u1 = sp.getU((double)((float)(i % 4) * 4.0F + 4.0F));
            float k = (float)j / ((float)n - 1.0F);
            float[] col = k > 0.85F ? FOAM : WATER;
            vert(vc, m, nm, cam, p0, u0, v0, col, col[3], light, nrm);
            vert(vc, m, nm, cam, p1, u1, v0, col, col[3], light, nrm);
            vert(vc, m, nm, cam, p2, u1, v1, col, col[3], light, nrm);
            vert(vc, m, nm, cam, p3, u0, v1, col, col[3], light, nrm);
         }
      }
   }

   private static record Tube(List<Vec3> pts, float r, float[] col, float alpha) {
   }
}
