package com.mahoraga.mod.client;

import com.jjk.mod.client.CloudHide;
import com.jjk.mod.client.JjkPrefs;
import com.mahoraga.mod.Net;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent.ComputeFogColor;
import net.minecraftforge.client.event.ViewportEvent.RenderFog;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class RitualFx {
   private static final int VOID_IN = 40;
   private static final int HEAD = 150;
   private static final int BURST = 186;
   private static final int WORLD = 194;
   private static boolean on;
   private static int summoner;
   private static int length;
   private static Vec3 spot;
   private static Vec3 from;
   private static float age;
   private static int ticks;
   private static float fov = 55.0F;
   private static BufferBuilder bb;
   private static Matrix4f mat;
   private static final float[] MUMMY = new float[]{
      -0.13F,
      0.0F,
      0.13F,
      0.0F,
      0.15F,
      0.25F,
      0.17F,
      0.5F,
      0.21F,
      0.62F,
      0.235F,
      0.72F,
      0.2F,
      0.8F,
      0.13F,
      0.86F,
      0.11F,
      0.95F,
      0.03F,
      1.0F,
      -0.07F,
      0.98F,
      -0.13F,
      0.9F,
      -0.19F,
      0.82F,
      -0.235F,
      0.73F,
      -0.21F,
      0.62F,
      -0.17F,
      0.5F,
      -0.15F,
      0.25F
   };
   private static final float[] WOLF = new float[]{
      0.0F,
      0.0F,
      0.3F,
      0.0F,
      0.36F,
      0.06F,
      0.46F,
      0.0F,
      0.66F,
      0.0F,
      0.64F,
      0.05F,
      0.56F,
      0.08F,
      0.58F,
      0.4F,
      0.64F,
      0.52F,
      0.6F,
      0.6F,
      0.67F,
      0.69F,
      0.71F,
      0.86F,
      0.8F,
      1.02F,
      0.93F,
      1.2F,
      0.88F,
      1.22F,
      0.83F,
      1.17F,
      0.9F,
      1.3F,
      0.84F,
      1.33F,
      0.7F,
      1.16F,
      0.66F,
      1.2F,
      0.57F,
      1.35F,
      0.55F,
      1.14F,
      0.46F,
      1.02F,
      0.4F,
      0.86F,
      0.34F,
      0.7F,
      0.2F,
      0.52F,
      0.1F,
      0.4F,
      0.02F,
      0.3F,
      -0.1F,
      0.22F,
      -0.22F,
      0.1F,
      -0.16F,
      0.04F,
      -0.04F,
      0.1F
   };
   private static final float[] MAN = new float[]{
      -0.04F,
      0.0F,
      -0.018F,
      0.0F,
      -0.004F,
      0.045F,
      0.01F,
      0.0F,
      0.034F,
      0.0F,
      0.014F,
      0.055F,
      0.016F,
      0.085F,
      0.062F,
      0.09F,
      0.064F,
      0.102F,
      0.016F,
      0.104F,
      0.012F,
      0.122F,
      -0.012F,
      0.127F,
      -0.022F,
      0.11F,
      -0.028F,
      0.07F
   };

   private RitualFx() {
   }

   static boolean active() {
      return on;
   }

   static float fov() {
      return fov;
   }

   static float age() {
      return age;
   }

   static void start(Net.Fx f) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !(mc.player.position().distanceTo(f.v()) > 40.0) && !JjkPrefs.skipCutscenes()) {
         if (MahoFx.cutsceneActive()) {
            MahoFx.endCut();
         }

         on = true;
         summoner = f.entity();
         spot = f.at();
         from = f.v();
         length = (int)f.f();
         ticks = 0;
         MahoFx.prevType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         RitualStage.start(mc, spot, from, summoner);
         CloudHide.set("ritual", true);
      }
   }

   static void tick(Minecraft mc) {
      if (on) {
         ticks++;
         RitualStage.tick(ticks);
         if (ticks >= length || mc.player == null || !mc.player.isAlive()) {
            on = false;
            CloudHide.set("ritual", false);
            MahoFx.endCut();
         }
      }
   }

   private static float smooth(float a, float b, float x) {
      float k = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
      return k * k * (3.0F - 2.0F * k);
   }

   private static float hash(int i, int salt) {
      float s = Mth.sin((float)i * 12.9898F + (float)salt * 78.233F) * 43758.547F;
      return s - (float)Mth.floor(s);
   }

   static void camera(Minecraft mc, float partial) {
      float t = (float)ticks + partial;
      age = t;
      Entity s = mc.level.getEntity(summoner);
      Vec3 sp = s != null ? s.getPosition(partial) : from;
      Vec3 dir = spot.subtract(from).multiply(1.0, 0.0, 1.0);
      dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
      Vec3 right = new Vec3(-dir.z, 0.0, dir.x);
      if (s instanceof LivingEntity le && t < 214.0F) {
         float yaw = (float)(Mth.atan2(dir.z, dir.x) * 180.0F / (float)Math.PI) - 90.0F;
         le.setYRot(yaw);
         le.yRotO = yaw;
         le.setYBodyRot(yaw);
         le.yBodyRotO = yaw;
         le.setYHeadRot(yaw);
         le.yHeadRotO = yaw;
         le.setXRot(10.0F);
         le.xRotO = 10.0F;
      }

      if (t < 44.0F) {
         float k = smooth(0.0F, 40.0F, t);
         fov = 55.0F - 12.0F * k;
         MahoFx.place(
            mc, sp.add(dir.scale(2.6 - 0.7 * (double)k)).add(right.scale(0.9 - 0.4 * (double)k)).add(0.0, 1.8, 0.0), sp.add(dir.scale(0.3)).add(0.0, 1.45, 0.0)
         );
      } else if (t >= 194.0F) {
         float k = smooth(194.0F, (float)length, t);
         fov = 72.0F - 10.0F * k;
         MahoFx.place(mc, sp.subtract(dir.scale(2.0 + 0.6 * (double)k)).add(right.scale(1.7)).add(0.0, 0.5, 0.0), spot.add(0.0, 2.9, 0.0));
      } else {
         Vec3[] c = RitualStage.camera(t);
         fov = (float)c[2].x;
         MahoFx.place(mc, c[0], c[1], false);
      }
   }

   @SubscribeEvent
   public static void onFog(RenderFog e) {
      if (on) {
         float t = (float)ticks + (float)e.getPartialTick();
         if (t >= 44.0F && t < 194.0F) {
            e.setNearPlaneDistance(500.0F);
            e.setFarPlaneDistance(600.0F);
            e.setCanceled(true);
         } else {
            float dark = Math.max(smooth(4.0F, 40.0F, t) * (1.0F - smooth(196.0F, 224.0F, t)), t > 40.0F && t < 194.0F ? 1.0F : 0.0F);
            if (!(dark <= 0.001F)) {
               float far = Mth.lerp(dark, e.getFarPlaneDistance(), 5.0F);
               e.setFarPlaneDistance(far);
               e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * 0.05F));
               e.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onFogColor(ComputeFogColor e) {
      if (on) {
         float t = (float)ticks + (float)e.getPartialTick();
         float dark = Math.max(smooth(4.0F, 40.0F, t) * (1.0F - smooth(196.0F, 224.0F, t)), t > 40.0F && t < 194.0F ? 1.0F : 0.0F);
         e.setRed(e.getRed() * (1.0F - dark));
         e.setGreen(e.getGreen() * (1.0F - dark));
         e.setBlue(e.getBlue() * (1.0F - dark));
      }
   }

   private static void begin(GuiGraphics g, boolean additive) {
      g.flush();
      RenderSystem.enableBlend();
      if (additive) {
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      } else {
         RenderSystem.defaultBlendFunc();
      }

      RenderSystem.disableCull();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      bb = Tesselator.getInstance().getBuilder();
      bb.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      mat = g.pose().last().pose();
   }

   private static void end() {
      BufferUploader.drawWithShader(bb.end());
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
   }

   private static void v(float x, float y, int argb) {
      bb.vertex(mat, x, y, 0.0F).color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24).endVertex();
   }

   private static int argb(float a, int rgb) {
      return (int)(Mth.clamp(a, 0.0F, 1.0F) * 255.0F) << 24 | rgb & 16777215;
   }

   private static void tri(float x1, float y1, float x2, float y2, float x3, float y3, int c) {
      v(x1, y1, c);
      v(x2, y2, c);
      v(x3, y3, c);
   }

   private static void line(float x1, float y1, float x2, float y2, float w, int c1, int c2) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float l = Mth.sqrt(dx * dx + dy * dy);
      if (!(l < 0.001F)) {
         float nx = -dy / l * w * 0.5F;
         float ny = dx / l * w * 0.5F;
         v(x1 + nx, y1 + ny, c1);
         v(x1 - nx, y1 - ny, c1);
         v(x2 - nx, y2 - ny, c2);
         v(x1 + nx, y1 + ny, c1);
         v(x2 - nx, y2 - ny, c2);
         v(x2 + nx, y2 + ny, c2);
      }
   }

   private static void ellipse(float cx, float cy, float rx, float ry, int center, int edge) {
      int seg = 28;

      for (int i = 0; i < seg; i++) {
         float a0 = (float)i * (float) (Math.PI * 2) / (float)seg;
         float a1 = (float)(i + 1) * (float) (Math.PI * 2) / (float)seg;
         v(cx, cy, center);
         v(cx + Mth.cos(a0) * rx, cy + Mth.sin(a0) * ry, edge);
         v(cx + Mth.cos(a1) * rx, cy + Mth.sin(a1) * ry, edge);
      }
   }

   private static void poly(float[] pts, int c) {
      float cx = 0.0F;
      float cy = 0.0F;
      int n = pts.length / 2;

      for (int i = 0; i < n; i++) {
         cx += pts[2 * i];
         cy += pts[2 * i + 1];
      }

      cx /= (float)n;
      cy /= (float)n;

      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         v(cx, cy, c);
         v(pts[2 * i], pts[2 * i + 1], c);
         v(pts[2 * j], pts[2 * j + 1], c);
      }
   }

   private static void shape(float[] pts, int c) {
      int n = pts.length / 2;
      List<Integer> idx = new ArrayList<>();
      float area = 0.0F;

      for (int i = 0; i < n; i++) {
         idx.add(i);
         int j = (i + 1) % n;
         area += pts[2 * i] * pts[2 * j + 1] - pts[2 * j] * pts[2 * i + 1];
      }

      float sign = Math.signum(area);
      int guard = 0;

      label68:
      while (idx.size() > 3 && guard++ < 4 * n * n) {
         boolean clipped = false;

         for (int k = 0; k < idx.size(); k++) {
            int a = idx.get((k + idx.size() - 1) % idx.size());
            int b = idx.get(k);
            int d = idx.get((k + 1) % idx.size());
            float ax = pts[2 * a];
            float ay = pts[2 * a + 1];
            float bx = pts[2 * b];
            float by = pts[2 * b + 1];
            float dx = pts[2 * d];
            float dy = pts[2 * d + 1];
            float cross = (bx - ax) * (dy - ay) - (by - ay) * (dx - ax);
            if (!(cross * sign <= 0.0F)) {
               boolean inside = false;

               for (int m : idx) {
                  if (m != a && m != b && m != d && inTri(pts[2 * m], pts[2 * m + 1], ax, ay, bx, by, dx, dy)) {
                     inside = true;
                     break;
                  }
               }

               if (!inside) {
                  tri(ax, ay, bx, by, dx, dy, c);
                  idx.remove(k);
                  clipped = true;
                  if (!clipped) {
                     break label68;
                  }
                  continue label68;
               }
            }
         }
         break;
      }

      if (idx.size() >= 3) {
         for (int kx = 1; kx + 1 < idx.size(); kx++) {
            int a = idx.get(0);
            int b = idx.get(kx);
            int d = idx.get(kx + 1);
            tri(pts[2 * a], pts[2 * a + 1], pts[2 * b], pts[2 * b + 1], pts[2 * d], pts[2 * d + 1], c);
         }
      }
   }

   private static boolean inTri(float px, float py, float ax, float ay, float bx, float by, float cx, float cy) {
      float d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
      float d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
      float d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
      boolean neg = d1 < 0.0F || d2 < 0.0F || d3 < 0.0F;
      boolean pos = d1 > 0.0F || d2 > 0.0F || d3 > 0.0F;
      return !neg || !pos;
   }

   private static void outline(float[] pts, float w, int c) {
      int n = pts.length / 2;

      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         line(pts[2 * i], pts[2 * i + 1], pts[2 * j], pts[2 * j + 1], w, c, c);
      }
   }

   private static float[] place(float[] unit, float ox, float oy, float s, boolean mirror) {
      float[] out = new float[unit.length];

      for (int i = 0; i < unit.length; i += 2) {
         out[i] = ox + (mirror ? -unit[i] : unit[i]) * s;
         out[i + 1] = oy - unit[i + 1] * s;
      }

      return out;
   }

   private static float halfWidth(float y) {
      float[][] r = new float[][]{
         {0.0F, 0.13F}, {0.25F, 0.15F}, {0.5F, 0.17F}, {0.62F, 0.21F}, {0.72F, 0.235F}, {0.8F, 0.2F}, {0.86F, 0.13F}, {0.95F, 0.11F}, {1.0F, 0.03F}
      };

      for (int i = 0; i < r.length - 1; i++) {
         if (y <= r[i + 1][0]) {
            float k = (y - r[i][0]) / (r[i + 1][0] - r[i][0]);
            return Mth.lerp(k, r[i][1], r[i + 1][1]);
         }
      }

      return 0.02F;
   }

   static void drawGui(GuiGraphics g, Minecraft mc, float partial) {
      int w = g.guiWidth();
      int h = g.guiHeight();
      float t = (float)ticks + partial;
      if (t < 46.0F) {
         float d = 0.55F * smooth(8.0F, 36.0F, t) + 0.45F * smooth(36.0F, 44.0F, t);
         g.fill(0, 0, w, h, argb(d, 0));
      }

      if (t >= 184.0F && t < 220.0F) {
         drawBurst(g, w, h, t - 186.0F);
      }

      int bar = (int)((float)h * 0.11F);
      g.fill(0, 0, w, bar, -16777216);
      g.fill(0, h - bar, w, h, -16777216);
      String sub = null;
      float sa = 0.0F;
      if (t >= 8.0F && t < 66.0F) {
         sub = "\"With this treasure, I summon...\"";
         sa = smooth(8.0F, 14.0F, t) * (1.0F - smooth(60.0F, 66.0F, t));
      } else if (t >= 70.0F && t < 152.0F) {
         String[] words = "Eight-Handled Sword Divergent Sila Divine General Mahoraga".split(" ");
         int shown = Math.min(words.length, 1 + (int)((t - 70.0F) / 8.0F));
         sub = "\"" + String.join(" ", Arrays.copyOf(words, shown)) + (shown == words.length ? "\"" : "");
         sa = smooth(70.0F, 74.0F, t) * (1.0F - smooth(146.0F, 152.0F, t));
      }

      if (sub != null) {
         g.drawCenteredString(mc.font, sub, w / 2, h - bar / 2 - 4, argb(sa, 15921906));
      }

      if (t >= 198.0F && t < (float)length) {
         float a = smooth(198.0F, 206.0F, t) * (1.0F - smooth((float)(length - 10), (float)length, t));
         g.pose().pushPose();
         g.pose().translate((float)w * 0.5F, (float)(bar + 10), 0.0F);
         g.pose().scale(1.2F, 1.2F, 1.0F);
         g.drawCenteredString(mc.font, "八握剣異戒神将魔虚羅", 0, 0, argb(a, 16777215));
         g.pose().popPose();
         g.pose().pushPose();
         g.pose().translate((float)w * 0.5F, (float)(bar + 24), 0.0F);
         g.pose().scale(0.7F, 0.7F, 1.0F);
         g.drawCenteredString(mc.font, "Eight-Handled Sword Divergent Sila Divine General Mahoraga", 0, 0, argb(a, 14208944));
         g.pose().popPose();
      }
   }

   private static void drawVoid(GuiGraphics g, int w, int h, float tt) {
      float cx = (float)w / 2.0F;
      float base = (float)h * 0.86F;
      float zoom = 1.0F + 0.14F * smooth(50.0F, 110.0F, tt);
      float H = (float)h * 0.64F * zoom;
      base += (zoom - 1.0F) * (float)h * 0.25F;
      float fig = smooth(0.0F, 28.0F, tt);
      float figY = base - 0.55F * H;
      begin(g, true);

      for (int i = 0; i < 70; i++) {
         float x = hash(i, 1) * (float)w;
         float y = hash(i, 2) * (float)h;
         float a = 0.15F + 0.25F * hash(i, 3) * (0.6F + 0.4F * Mth.sin(tt * 0.2F + (float)i));
         ellipse(x, y, 0.9F, 0.9F, argb(a * smooth(0.0F, 10.0F, tt), 10135200), argb(0.0F, 10135200));
      }

      for (int i = 0; i < 18; i++) {
         float ang = (hash(i, 4) < 0.5F ? (float) Math.PI : 0.0F) + (hash(i, 5) - 0.5F) * 1.3F + (hash(i, 6) - 0.5F) * 0.3F;
         float len = (0.25F + 0.9F * smooth((float)i * 1.5F, 30.0F + (float)i * 1.5F, tt)) * (float)w * (0.6F + 0.4F * hash(i, 7));
         float sx = cx + (hash(i, 8) - 0.5F) * 0.2F * H;
         float sy = figY + (hash(i, 9) - 0.2F) * 0.4F * H;
         line(sx, sy, sx + Mth.cos(ang) * len, sy + Mth.sin(ang) * len * 0.45F, 1.1F, argb(0.45F * fig, 15267054), argb(0.0F, 15267054));
      }

      ellipse(cx, figY, 0.42F * H, 0.62F * H, argb(0.42F * fig, 10479816), argb(0.0F, 10479816));
      ellipse(cx, figY, 0.22F * H, 0.45F * H, argb(0.35F * fig, 14221296), argb(0.0F, 14221296));
      end();
      begin(g, false);
      float[] body = place(MUMMY, cx, base, H, false);
      shape(body, argb(fig, 14545638));

      for (int i = 0; i < 24; i++) {
         float y0 = (float)i / 24.0F * 0.97F;
         float y1 = (float)(i + 1) / 24.0F * 0.97F;
         float o0 = halfWidth(y0) * H;
         float o1 = halfWidth(y1) * H;
         int c = argb(fig * 0.5F, 9417378);
         tri(cx + 0.4F * o0, base - y0 * H, cx + o0, base - y0 * H, cx + o1, base - y1 * H, c);
         tri(cx + 0.4F * o0, base - y0 * H, cx + o1, base - y1 * H, cx + 0.4F * o1, base - y1 * H, c);
      }

      int arm = argb(fig, 15661812);
      int ink = argb(fig * 0.85F, 3029557);
      line(cx - 0.21F * H, base - 0.66F * H, cx + 0.19F * H, base - 0.77F * H, 0.075F * H, arm, arm);
      line(cx + 0.21F * H, base - 0.64F * H, cx - 0.18F * H, base - 0.775F * H, 0.075F * H, arm, arm);
      line(cx - 0.21F * H, base - 0.63F * H, cx + 0.19F * H, base - 0.735F * H, 1.2F, ink, ink);
      line(cx + 0.21F * H, base - 0.605F * H, cx - 0.18F * H, base - 0.74F * H, 1.2F, ink, ink);

      for (int i = 0; i < 30; i++) {
         float y0 = 0.02F + (float)i * 0.032F;
         float y1 = Math.min(0.98F, y0 + 0.055F);
         float wob = Mth.sin(tt * 0.3F + (float)i) * 0.004F;
         line(
            cx - halfWidth(y0) * H, base - (y0 + wob) * H, cx + halfWidth(y1) * H, base - y1 * H, 1.1F, argb(fig * 0.75F, 3424828), argb(fig * 0.75F, 3424828)
         );
      }

      for (int i = 0; i < 12; i++) {
         float y0 = 0.05F + (float)i * 0.075F;
         float y1 = Math.min(0.97F, y0 + 0.09F);
         line(cx + halfWidth(y0) * H, base - y0 * H, cx - halfWidth(y1) * H, base - y1 * H, 0.9F, argb(fig * 0.45F, 3424828), argb(fig * 0.45F, 3424828));
      }

      outline(body, 1.4F, argb(fig, 2240554));
      float[] man = place(MAN, cx - 0.05F * H, base + 0.004F * H, H, false);
      shape(man, argb(smooth(4.0F, 16.0F, tt), 922134));
      outline(man, 1.0F, argb(0.6F * smooth(4.0F, 16.0F, tt), 9418920));
      float wolves = smooth(6.0F, 30.0F, tt);

      for (int side = -1; side <= 1; side += 2) {
         for (int k = 0; k < 2; k++) {
            float s = H * (k == 0 ? 0.4F : 0.33F);
            float x = cx + (float)side * H * (k == 0 ? 0.42F : 0.78F);
            float y = base + (k == 0 ? 0.0F : 0.02F * H);
            float[] wolf = place(WOLF, x, y, s, side > 0);
            shape(wolf, argb(wolves, 1316892));
            outline(wolf, 1.0F, argb(wolves * 0.6F, 3818572));
            float mx0 = side > 0 ? -1.0F : 1.0F;

            for (int f = 0; f < 6; f++) {
               float fx = 0.12F + (float)f * 0.07F;
               float fy = 0.42F + (float)f * 0.11F;
               line(x + mx0 * fx * s, y - fy * s, x + mx0 * (fx - 0.06F) * s, y - (fy + 0.12F) * s, 1.4F, argb(wolves * 0.8F, 3818572), argb(0.0F, 3818572));
            }
         }
      }

      float toads = smooth(26.0F, 46.0F, tt);

      for (int side = -1; side <= 1; side += 2) {
         float x = cx + (float)side * H * 0.27F;
         float y = base + 0.05F * H;
         float s = H * 0.18F;
         ellipse(x, y - 0.3F * s, 0.58F * s, 0.35F * s, argb(toads, 5661790), argb(toads, 5661790));
         ellipse(x - 0.22F * s, y - 0.58F * s, 0.18F * s, 0.16F * s, argb(toads, 5661790), argb(toads, 5661790));
         ellipse(x + 0.22F * s, y - 0.58F * s, 0.18F * s, 0.16F * s, argb(toads, 5661790), argb(toads, 5661790));
         ellipse(x, y - 0.3F * s, 0.55F * s, 0.32F * s, argb(toads, 1251098), argb(toads, 1251098));
         ellipse(x - 0.22F * s, y - 0.58F * s, 0.15F * s, 0.13F * s, argb(toads, 1251098), argb(toads, 1251098));
         ellipse(x + 0.22F * s, y - 0.58F * s, 0.15F * s, 0.13F * s, argb(toads, 1251098), argb(toads, 1251098));
         tri(x - 0.55F * s, y, x - 0.75F * s, y, x - 0.42F * s, y - 0.25F * s, argb(toads, 1251098));
         tri(x + 0.55F * s, y, x + 0.75F * s, y, x + 0.42F * s, y - 0.25F * s, argb(toads, 1251098));
      }

      end();
      begin(g, true);

      for (int side = -1; side <= 1; side += 2) {
         for (int k = 0; k < 2; k++) {
            float s = H * (k == 0 ? 0.4F : 0.33F);
            float x = cx + (float)side * H * (k == 0 ? 0.42F : 0.78F);
            float y = base + (k == 0 ? 0.0F : 0.02F * H);
            float fl = 0.85F + 0.15F * Mth.sin(tt * 0.5F + (float)k + (float)side);
            float sx = side > 0 ? -1.0F : 1.0F;
            ellipse(x + sx * 0.58F * s, y - 0.52F * s, 0.16F * s, 0.14F * s, argb(wolves * fl, 16777215), argb(0.0F, 16777215));
            ellipse(x + sx * 0.74F * s, y - 1.12F * s, 0.1F * s, 0.08F * s, argb(wolves * fl, 16777215), argb(0.0F, 16777215));
            ellipse(x + sx * 0.42F * s, y - 0.8F * s, 0.08F * s, 0.11F * s, argb(wolves * fl * 0.8F, 16777215), argb(0.0F, 16777215));
            ellipse(x + sx * 0.12F * s, y - 0.3F * s, 0.1F * s, 0.07F * s, argb(wolves * fl * 0.6F, 16777215), argb(0.0F, 16777215));

            for (float howl : new float[]{-6.0F, 10.0F, 64.0F, 80.0F}) {
               float ht = tt - howl - (float)(k * 3);
               if (ht > 0.0F && ht < 24.0F) {
                  float r = (0.1F + ht * 0.03F) * s;
                  float a = (1.0F - ht / 24.0F) * 0.5F * wolves;
                  float mx = x + sx * 0.92F * s;
                  float my = y - 1.32F * s;

                  for (int q = 0; q < 10; q++) {
                     float a0 = -2.670354F + (float)q * 0.07F * (float) Math.PI;
                     float a1 = a0 + 0.21991149F;
                     line(mx + Mth.cos(a0) * r, my + Mth.sin(a0) * r, mx + Mth.cos(a1) * r, my + Mth.sin(a1) * r, 1.2F, argb(a, 15134975), argb(a, 15134975));
                  }
               }
            }
         }

         float x = cx + (float)side * H * 0.27F;
         float y = base + 0.05F * H;
         float s = H * 0.18F;
         ellipse(x - 0.22F * s, y - 0.62F * s, 0.07F * s, 0.07F * s, argb(toads, 16777215), argb(0.0F, 16777215));
         ellipse(x + 0.22F * s, y - 0.62F * s, 0.07F * s, 0.07F * s, argb(toads, 16777215), argb(0.0F, 16777215));

         for (float croak : new float[]{36.0F, 52.0F, 90.0F}) {
            float ct = tt - croak - (float)(side > 0 ? 4 : 0);
            if (ct > 0.0F && ct < 14.0F) {
               float k = Mth.sin(ct / 14.0F * (float) Math.PI);
               ellipse(x, y - 0.2F * s, 0.25F * s * (0.6F + k), 0.18F * s * (0.6F + k), argb(0.5F * k * toads, 13688024), argb(0.0F, 13688024));
            }
         }
      }

      for (int i = 0; i < 55; i++) {
         float x = cx + (hash(i, 11) * 2.0F - 1.0F) * 0.7F * H;
         float ph = (hash(i, 12) + tt * (0.004F + 0.006F * hash(i, 13))) % 1.0F;
         float y = base - ph * 1.25F * H;
         float a = Mth.sin(ph * (float) Math.PI) * (0.5F + 0.5F * Mth.sin(tt * 0.7F + (float)i)) * fig;
         ellipse(x, y, 1.6F, 1.6F, argb(a, 16777215), argb(0.0F, 16777215));
      }

      end();
   }

   private static void drawHead(GuiGraphics g, int w, int h, float tt) {
      float roar = tt - 8.0F;
      float shake = roar > 0.0F ? Math.max(0.0F, 1.0F - roar / 20.0F) * 6.0F : 0.0F;
      float cx = (float)w * 0.53F + Mth.sin(tt * 3.1F) * shake;
      float cy = (float)h * 0.42F + Mth.cos(tt * 2.7F) * shake;
      float S = (float)h * 0.52F * (1.0F + 0.12F * smooth(0.0F, 36.0F, tt));
      begin(g, true);

      for (int i = 0; i < 26; i++) {
         float ang = hash(i, 21) * (float) (Math.PI * 2);
         float len = (float)w * (0.4F + 0.6F * hash(i, 22));
         line(cx, cy, cx + Mth.cos(ang) * len, cy + Mth.sin(ang) * len, 1.0F, argb(0.0F, 16777215), argb(0.4F, 16777215));
      }

      for (int i = 0; i < 60; i++) {
         ellipse(hash(i, 23) * (float)w, hash(i, 24) * (float)h, 1.0F, 1.0F, argb(0.3F * hash(i, 25), 13160664), argb(0.0F, 13160664));
      }

      end();
      begin(g, false);
      float[] torso = new float[]{-0.32F, 0.15F, 0.32F, 0.2F, 0.75F, 0.55F, 0.95F, 1.3F, -1.4F, 1.3F, -1.05F, 0.6F, -0.6F, 0.35F};
      float[] tp = new float[torso.length];

      for (int i = 0; i < torso.length; i += 2) {
         tp[i] = cx + torso[i] * S;
         tp[i + 1] = cy + torso[i + 1] * S;
      }

      shape(tp, -1645617);

      for (int i = 0; i < 9; i++) {
         float y = 0.3F + (float)i * 0.1F;
         line(cx - 1.2F * S + (float)i * 0.05F * S, cy + y * S, cx + 0.8F * S, cy + (y - 0.18F) * S, 0.035F * S, argb(0.35F, 11118480), argb(0.1F, 11118480));
      }

      ellipse(cx, cy, 0.3F * S, 0.38F * S, -1184808, -2435136);

      for (int i = 0; i < 16; i++) {
         float y = -0.36F + (float)i * 0.048F;
         float rx = 0.3F * Mth.sqrt(Math.max(0.0F, 1.0F - y / 0.38F * (y / 0.38F)));
         float tilt = (hash(i, 26) - 0.5F) * 0.25F;
         float strain = roar > 0.0F ? Math.min(1.0F, roar / 10.0F) * 0.02F * Mth.sin(tt * 2.0F + (float)i) : 0.0F;
         int c = argb(0.85F, 3881520);
         float px = cx - rx * S;
         float py = cy + (y - tilt * 0.3F) * S;

         for (int k = 1; k <= 12; k++) {
            float a = (float) Math.PI - (float)k * (float) Math.PI / 12.0F;
            float x = cx + Mth.cos(a) * rx * S;
            float yy = cy + (y + tilt * Mth.cos(a) * 0.3F + 0.03F * Mth.sin(a) + strain) * S;
            line(px, py, x, yy, 1.3F, c, c);
            px = x;
            py = yy;
         }
      }

      for (int i = 0; i < 8; i++) {
         float y = 0.2F + (float)i * 0.12F;
         line(cx - 0.9F * S, cy + y * S, cx + 0.6F * S, cy + (y - 0.25F) * S, 1.2F, argb(0.7F, 3881520), argb(0.7F, 3881520));
      }

      float mo = smooth(6.0F, 14.0F, tt);
      if (mo > 0.01F) {
         float mx = cx + 0.01F * S;
         float my = cy + 0.02F * S;
         float rx = 0.13F * S;
         float ry = 0.17F * S * mo;
         ellipse(mx, my, rx * 1.08F, ry * 1.08F + 1.0F, -14670290, -14670290);
         ellipse(mx, my, rx, ry, -14930344, -16051674);
         int fang = -3151617;

         for (int k = 0; k < 7; k++) {
            float a = (float) Math.PI + ((float)k + 0.5F) * (float) Math.PI / 7.0F;
            float x = mx + Mth.cos(a) * rx;
            float y = my + Mth.sin(a) * ry;
            float len = (0.045F + 0.03F * hash(k, 27)) * S * mo;
            tri(x - 0.018F * S, y, x + 0.018F * S, y, x + (mx - x) * 0.1F, y + len, fang);
         }

         for (int k = 0; k < 6; k++) {
            float a = ((float)k + 0.5F) * (float) Math.PI / 6.0F;
            float x = mx + Mth.cos(a) * rx;
            float y = my + Mth.sin(a) * ry;
            float len = (0.04F + 0.03F * hash(k, 28)) * S * mo;
            tri(x - 0.017F * S, y, x + 0.017F * S, y, x + (mx - x) * 0.1F, y - len, fang);
         }
      }

      for (int i = 0; i < 40; i++) {
         float ph = (hash(i, 29) + tt * 0.02F) % 1.0F;
         float ang = hash(i, 30) * (float) (Math.PI * 2);
         float r = (0.2F + ph * 0.9F) * S;
         ellipse(cx + Mth.cos(ang) * r, cy + Mth.sin(ang) * r * 0.8F, 1.6F, 1.6F, argb(0.8F * (1.0F - ph), 328965), argb(0.0F, 328965));
      }

      end();
   }

   private static void drawBurst(GuiGraphics g, int w, int h, float tt) {
      float cx = (float)w * 0.5F;
      float cy = (float)h * 0.45F;
      begin(g, false);
      end();
      float flash = tt < 0.0F ? smooth(-2.0F, 0.0F, tt) : 1.0F - smooth(2.0F, 22.0F, tt);
      if (flash > 0.001F) {
         g.fill(0, 0, w, h, argb(flash, 16777215));
      }
   }
}
