package com.naoya.ps.client.fx;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Light {
   private static final List<Light.Spark> SPARKS = new ArrayList<>();
   private static final List<Light.Flare> FLARES = new ArrayList<>();
   private static final List<Light.Ring> RINGS = new ArrayList<>();
   private static final Map<String, Light.Trail> TRAILS = new HashMap<>();
   private static final Random R = new Random();
   private static long last = System.nanoTime();
   private static Vec3 forward = new Vec3(0.0, 0.0, 1.0);
   private static final double NEAR = 0.8;

   private static Vec3[] clip(Vec3 a, Vec3 b) {
      double da = a.dot(forward);
      double db = b.dot(forward);
      if (da < 0.8 && db < 0.8) {
         return null;
      } else {
         if (da < 0.8) {
            a = b.add(a.subtract(b).scale((db - 0.8) / (db - da)));
         } else if (db < 0.8) {
            b = a.add(b.subtract(a).scale((da - 0.8) / (da - db)));
         }

         return new Vec3[]{a, b};
      }
   }

   private Light() {
   }

   public static void spark(Vec3 pos, Vec3 vel, float life, int rgb, float width) {
      Light.Spark s = new Light.Spark();
      s.pos = pos;
      s.vel = vel;
      s.life = life;
      s.rgb = rgb;
      s.width = width;
      s.stretch = 0.045F;
      s.drag = 3.5F;
      SPARKS.add(s);
   }

   public static void stream(Vec3 from, Vec3 to, float seconds, int rgb, float width) {
      Light.Spark s = new Light.Spark();
      s.pos = from;
      s.vel = to.subtract(from).scale(1.0 / (double)seconds);
      s.life = seconds;
      s.rgb = rgb;
      s.width = width;
      s.stretch = 0.06F;
      s.drag = 0.0F;
      s.gravity = 0.0F;
      SPARKS.add(s);
   }

   public static void mote(Vec3 at, Vec3 vel, float life, int rgb, float width) {
      Light.Spark s = new Light.Spark();
      s.pos = at;
      s.vel = vel;
      s.life = life;
      s.rgb = rgb;
      s.width = width;
      s.stretch = 0.05F;
      s.drag = 0.5F;
      s.gravity = -0.5F;
      SPARKS.add(s);
   }

   public static void burst(Vec3 at, int n, float speed, int rgb, float life, float width) {
      for (int i = 0; i < n; i++) {
         Vec3 d = new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).normalize();
         spark(at, d.scale((double)(speed * (0.35F + R.nextFloat() * 0.9F))), life * (0.6F + R.nextFloat() * 0.6F), rgb, width * (0.6F + R.nextFloat() * 0.7F));
      }
   }

   public static void spray(Vec3 at, Vec3 dir, float cone, int n, float speed, int rgb, float life, float width) {
      Vec3 d0 = dir.normalize();

      for (int i = 0; i < n; i++) {
         Vec3 d = d0.add(new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).scale((double)cone)).normalize();
         spark(at, d.scale((double)(speed * (0.4F + R.nextFloat() * 0.8F))), life * (0.6F + R.nextFloat() * 0.6F), rgb, width);
      }
   }

   public static void flare(Vec3 at, float size, int rgb, float life) {
      Light.Flare f = new Light.Flare();
      f.pos = at;
      f.size = size;
      f.grow = 0.6F;
      f.rgb = rgb;
      f.life = life;
      FLARES.add(f);
   }

   public static void ring(Vec3 at, Vec3 normal, float r0, float r1, float width, int rgb, float life) {
      Light.Ring r = new Light.Ring();
      r.pos = at;
      r.normal = normal.normalize();
      r.r0 = r0;
      r.r1 = r1;
      r.width = width;
      r.rgb = rgb;
      r.life = life;
      RINGS.add(r);
   }

   public static void hit(Vec3 at, Vec3 dir, int rgb, float power) {
      flare(at, 0.9F * power, 16777215, 0.18F);
      flare(at, 2.0F * power, rgb, 0.3F);
      ring(at, dir, 0.2F, 1.6F * power, 0.18F * power, rgb, 0.3F);
      spray(at, dir, 0.7F, (int)(14.0F * power), 9.0F * power, rgb, 0.35F, 0.05F);
      burst(at, (int)(6.0F * power), 5.0F * power, 16777215, 0.25F, 0.04F);
   }

   public static void trail(String key, Vec3 at, int rgb, float width, float keepSeconds) {
      Light.Trail t = TRAILS.computeIfAbsent(key, k -> new Light.Trail());
      t.rgb = rgb;
      t.width = width;
      t.keep = keepSeconds;
      long now = System.nanoTime();
      t.touched = now;
      double[] lastPt = t.pts.peekLast();
      if (lastPt == null
         || (at.x - lastPt[0]) * (at.x - lastPt[0]) + (at.y - lastPt[1]) * (at.y - lastPt[1]) + (at.z - lastPt[2]) * (at.z - lastPt[2]) > 4.0E-4) {
         t.pts.addLast(new double[]{at.x, at.y, at.z, (double)now});
      }
   }

   public static void clear() {
      SPARKS.clear();
      FLARES.clear();
      RINGS.clear();
      TRAILS.clear();
   }

   private static void step(float dt) {
      Iterator<Light.Spark> si = SPARKS.iterator();

      while (si.hasNext()) {
         Light.Spark s = si.next();
         s.age += dt;
         if (s.age >= s.life) {
            si.remove();
         } else {
            s.pos = s.pos.add(s.vel.scale((double)dt));
            s.vel = s.vel.scale(Math.exp((double)(-s.drag * dt))).add(0.0, (double)(-s.gravity * dt), 0.0);
         }
      }

      FLARES.removeIf(f -> (f.age += dt) >= f.life);
      RINGS.removeIf(r -> (r.age += dt) >= r.life);
      long now = System.nanoTime();
      Iterator<Entry<String, Light.Trail>> ti = TRAILS.entrySet().iterator();

      while (ti.hasNext()) {
         Light.Trail t = ti.next().getValue();
         long keepNs = (long)((double)t.keep * 1.0E9);

         while (!t.pts.isEmpty() && now - (long)((double[])t.pts.peekFirst())[3] > keepNs) {
            t.pts.removeFirst();
         }

         if (t.pts.isEmpty() && now - t.touched > keepNs) {
            ti.remove();
         }
      }
   }

   public static boolean idle() {
      return SPARKS.isEmpty() && FLARES.isEmpty() && RINGS.isEmpty() && TRAILS.isEmpty();
   }

   public static void render(PoseStack view, Vec3 cam) {
      long nowNs = System.nanoTime();
      float dt = Math.min(0.1F, (float)(nowNs - last) / 1.0E9F);
      last = nowNs;
      if (Minecraft.getInstance().isPaused()) {
         dt = 0.0F;
      }

      step(dt);
      if (!idle()) {
         Matrix4f m = view.last().pose();
         Vector3f look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
         forward = new Vec3((double)look.x, (double)look.y, (double)look.z);
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         RenderSystem.depthMask(false);
         RenderSystem.enableDepthTest();
         RenderSystem.disableCull();
         BufferBuilder bb = Tesselator.getInstance().getBuilder();
         bb.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

         for (Light.Trail t : TRAILS.values()) {
            drawTrail(bb, m, cam, t, nowNs);
         }

         for (Light.Spark s : SPARKS) {
            float k = 1.0F - s.age / s.life;
            Vec3 head = s.pos.subtract(cam);
            Vec3 tail = head.subtract(s.vel.scale((double)s.stretch + 0.01));
            streak(bb, m, tail, head, s.width * (0.4F + 0.6F * k), s.rgb, k);
            streak(bb, m, tail, head, s.width * 0.35F, 16777215, k * 0.8F);
         }

         for (Light.Flare f : FLARES) {
            float k = f.age / f.life;
            float a = (1.0F - k) * (1.0F - k);
            float size = f.size * (1.0F + f.grow * k);
            glow(bb, m, f.pos.subtract(cam), size, f.rgb, a);
         }

         for (Light.Ring r : RINGS) {
            float k = r.age / r.life;
            float e = 1.0F - (float)Math.pow((double)(1.0F - k), 3.0);
            ring(bb, m, r.pos.subtract(cam), r.normal, r.r0 + (r.r1 - r.r0) * e, r.width * (1.0F - 0.5F * k), r.rgb, 1.0F - k);
         }

         BufferUploader.drawWithShader(bb.end());
         RenderSystem.depthMask(true);
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableBlend();
         RenderSystem.enableCull();
      }
   }

   private static void v(BufferBuilder bb, Matrix4f m, Vec3 p, int rgb, float a) {
      float r = (float)(rgb >> 16 & 0xFF) / 255.0F;
      float g = (float)(rgb >> 8 & 0xFF) / 255.0F;
      float b = (float)(rgb & 0xFF) / 255.0F;
      bb.vertex(m, (float)p.x, (float)p.y, (float)p.z).color(r, g, b, Math.max(0.0F, Math.min(1.0F, a))).endVertex();
   }

   private static void streak(BufferBuilder bb, Matrix4f m, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
      Vec3[] cl = clip(a, b);
      if (cl != null) {
         a = cl[0];
         b = cl[1];
         Vec3 d = b.subtract(a);
         if (!(d.lengthSqr() < 1.0E-8)) {
            Vec3 side = d.cross(a.add(b).scale(0.5)).normalize().scale((double)width * 0.5);
            Vec3 mid = a.lerp(b, 0.7);
            tri(bb, m, a, mid.add(side), mid, rgb, 0.0F, 0.0F, alpha);
            tri(bb, m, a, mid, mid.subtract(side), rgb, 0.0F, alpha, 0.0F);
            tri(bb, m, mid, mid.add(side), b, rgb, alpha, 0.0F, alpha * 0.6F);
            tri(bb, m, mid, b, mid.subtract(side), rgb, alpha, alpha * 0.6F, 0.0F);
         }
      }
   }

   private static void tri(BufferBuilder bb, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, int rgb, float a0, float a1, float a2) {
      v(bb, m, p0, rgb, a0);
      v(bb, m, p1, rgb, a1);
      v(bb, m, p2, rgb, a2);
   }

   private static void glow(BufferBuilder bb, Matrix4f m, Vec3 c, float size, int rgb, float a) {
      if (!(a <= 0.003F) && !(c.dot(forward) < 0.8 + (double)size * 0.5)) {
         Vec3 view = c.normalize();
         Vec3 u = Math.abs(view.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
         Vec3 right = view.cross(u).normalize();
         Vec3 up = right.cross(view).normalize();
         int n = 20;
         float[] radii = new float[]{0.0F, 0.18F, 0.45F, 1.0F};
         float[] alphas = new float[]{1.0F, 0.75F, 0.25F, 0.0F};

         for (int band = 0; band < 3; band++) {
            for (int i = 0; i < n; i++) {
               double a0 = (double)i * Math.PI * 2.0 / (double)n;
               double a1 = (double)(i + 1) * Math.PI * 2.0 / (double)n;
               Vec3 d0 = right.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
               Vec3 d1 = right.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
               Vec3 i0 = c.add(d0.scale((double)(size * radii[band])));
               Vec3 i1 = c.add(d1.scale((double)(size * radii[band])));
               Vec3 o0 = c.add(d0.scale((double)(size * radii[band + 1])));
               Vec3 o1 = c.add(d1.scale((double)(size * radii[band + 1])));
               tri(bb, m, i0, o0, o1, rgb, a * alphas[band], a * alphas[band + 1], a * alphas[band + 1]);
               tri(bb, m, i0, o1, i1, rgb, a * alphas[band], a * alphas[band + 1], a * alphas[band]);
            }
         }
      }
   }

   private static void ring(BufferBuilder bb, Matrix4f m, Vec3 c, Vec3 n, float radius, float width, int rgb, float a) {
      if (!(a <= 0.003F) && !(radius <= 0.0F)) {
         Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0).cross(n).normalize() : new Vec3(0.0, 1.0, 0.0).cross(n).normalize();
         Vec3 w = n.cross(u);
         int segs = 56;

         for (int i = 0; i < segs; i++) {
            double a0 = (double)i * Math.PI * 2.0 / (double)segs;
            double a1 = (double)(i + 1) * Math.PI * 2.0 / (double)segs;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0)));
            Vec3 d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            Vec3 in0 = c.add(d0.scale((double)Math.max(0.0F, radius - width)));
            Vec3 in1 = c.add(d1.scale((double)Math.max(0.0F, radius - width)));
            Vec3 mid0 = c.add(d0.scale((double)radius));
            Vec3 mid1 = c.add(d1.scale((double)radius));
            Vec3 out0 = c.add(d0.scale((double)radius + (double)width * 0.4));
            Vec3 out1 = c.add(d1.scale((double)radius + (double)width * 0.4));
            tri(bb, m, in0, mid0, mid1, rgb, 0.0F, a, a);
            tri(bb, m, in0, mid1, in1, rgb, 0.0F, a, 0.0F);
            tri(bb, m, mid0, out0, out1, rgb, a, 0.0F, 0.0F);
            tri(bb, m, mid0, out1, mid1, rgb, a, 0.0F, a);
         }
      }
   }

   private static void drawTrail(BufferBuilder bb, Matrix4f m, Vec3 cam, Light.Trail t, long now) {
      int n = t.pts.size();
      if (n >= 2) {
         double[][] p = t.pts.toArray(new double[0][]);
         double keepNs = (double)t.keep * 1.0E9;

         for (int pass = 0; pass < 2; pass++) {
            int rgb = pass == 0 ? t.rgb : 16777215;
            float wscale = pass == 0 ? 1.0F : 0.32F;

            for (int i = 0; i < n - 1; i++) {
               Vec3[] cl = clip(new Vec3(p[i][0], p[i][1], p[i][2]).subtract(cam), new Vec3(p[i + 1][0], p[i + 1][1], p[i + 1][2]).subtract(cam));
               if (cl != null) {
                  Vec3 a = cl[0];
                  Vec3 b = cl[1];
                  Vec3 d = b.subtract(a);
                  if (!(d.lengthSqr() < 1.0E-8)) {
                     float ka = (float)Math.max(0.0, 1.0 - ((double)now - p[i][3]) / keepNs);
                     float kb = (float)Math.max(0.0, 1.0 - ((double)now - p[i + 1][3]) / keepNs);
                     Vec3 side = d.cross(a.add(b).scale(0.5)).normalize();
                     Vec3 sa = side.scale((double)(t.width * wscale) * 0.5 * (0.2 + 0.8 * (double)ka));
                     Vec3 sb = side.scale((double)(t.width * wscale) * 0.5 * (0.2 + 0.8 * (double)kb));
                     float aa = ka * ka * (pass == 0 ? 0.75F : 1.0F);
                     float ab = kb * kb * (pass == 0 ? 0.75F : 1.0F);
                     tri(bb, m, a.add(sa), a, b, rgb, 0.0F, aa, ab);
                     tri(bb, m, a.add(sa), b, b.add(sb), rgb, 0.0F, ab, 0.0F);
                     tri(bb, m, a, a.subtract(sa), b.subtract(sb), rgb, aa, 0.0F, 0.0F);
                     tri(bb, m, a, b.subtract(sb), b, rgb, aa, 0.0F, ab);
                  }
               }
            }
         }
      }
   }

   static final class Flare {
      Vec3 pos;
      float size;
      float grow;
      float life;
      float age;
      int rgb;
   }

   static final class Ring {
      Vec3 pos;
      Vec3 normal;
      float r0;
      float r1;
      float width;
      float life;
      float age;
      int rgb;
   }

   static final class Spark {
      Vec3 pos;
      Vec3 vel;
      float life;
      float age;
      float width;
      float stretch;
      float drag;
      float gravity = 2.0F;
      int rgb;
   }

   static final class Trail {
      final Deque<double[]> pts = new ArrayDeque<>();
      int rgb;
      float width;
      float keep;
      long touched;
   }
}
