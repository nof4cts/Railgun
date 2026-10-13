package com.naoya.ps.client;

import com.naoya.ps.Net;
import com.naoya.ps.client.cine.CameraPose;
import com.naoya.ps.client.cine.Director;
import com.naoya.ps.client.cine.Film;
import com.naoya.ps.client.cine.Shot;
import com.naoya.ps.client.fx.ScreenFx;
import com.naoya.ps.server.Projection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Cine {
   public static final int[][] HIT = new int[][]{{5, 1}, {1, 1}, {2, 1}, {9, 1}, {6, 1}, {1, 1}, {2, 1}};
   private static Film film;
   private static long start;

   private Cine() {
   }

   static Film ult(Net.Fx m, long now) {
      start = now;
      Vec3 P = m.a();
      Vec3 T = m.b();
      Vec3 D0 = T.subtract(P).multiply(1.0, 0.0, 1.0);
      Vec3 D = D0.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : D0.normalize();
      Vec3 S = new Vec3(-D.z, 0.0, D.x);
      int target = m.other();
      int hitAt = 60;
      List<Shot> s = new ArrayList<>();
      s.add(
         Shot.cut(
            0,
            22,
            (k, t) -> pose(P.add(D.scale(1.9 - 0.3 * (double)k)).add(S.scale(0.25)).add(0.0, 1.62, 0.0), P.add(0.0, 1.55, 0.0), (double)(38.0F - 4.0F * k), 0.0)
         )
      );
      s.add(Shot.cut(22, 44, (k, t) -> pose(T.subtract(D.scale(3.6 - 0.8 * (double)k)).add(S.scale(1.4)).add(0.0, 1.3, 0.0), T.add(0.0, 1.0, 0.0), 44.0, -3.0)));
      s.add(Shot.cut(44, hitAt, (k, t) -> {
         Vec3 mid = P.lerp(T, 0.25 + 0.6 * (double)k);
         return pose(mid.add(S.scale((double)(10.0F - 2.0F * k))).add(0.0, 1.8, 0.0), mid.add(0.0, 1.1, 0.0), 50.0, 0.0);
      }));
      s.add(
         Shot.cut(
            hitAt,
            hitAt + 12,
            (k, t) -> pose(T.add(S.scale(2.8)).subtract(D.scale(1.0)).add(0.0, 1.2, 0.0), T.add(0.0, 1.0, 0.0), (double)(56.0F + 8.0F * k), 4.0)
         )
      );
      s.add(Shot.cut(hitAt + 12, 104, (k, t) -> {
         Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(target);
         Vec3 at = e != null ? e.position().add(0.0, 1.0, 0.0) : T.add(D.scale((double)(12.0F * k))).add(0.0, 1.0, 0.0);
         return pose(T.subtract(D.scale(3.0)).add(S.scale(2.0)).add(0.0, 2.4 + (double)(2.0F * k), 0.0), at, 62.0, 0.0);
      }));
      String[][] lines = new String[][]{{"6", "One second. Twenty-four frames."}, {"26", "Too slow. You're stuck in one."}, {"70", "Keep up, or stay still."}};
      Film.Card card = new Film.Card("ZEN'IN CLAN", "投射呪法", "Projection Sorcery", 15913594, 30, 44);
      film = new Film(partial -> NFx.now(partial) - (float)start, s, 104, card, lines, 6, 12);
      return film;
   }

   private static CameraPose pose(Vec3 pos, Vec3 look, double fov, double roll) {
      return new CameraPose(pos, look, (float)fov, (float)roll);
   }

   static boolean playing() {
      return film != null && Director.film() == film;
   }

   static void screen(ScreenFx fx, float partial) {
      if (playing()) {
         float t = film.clock.time(partial);
         float still = ScreenFx.window(t, 0.0F, 4.0F, 40.0F, 46.0F);
         fx.desaturate(0.55F * still);
         fx.vignette(0, 0.45F * still);
         fx.grain(0.4F);

         for (int b : Projection.ULT_BLINKS) {
            fx.punch(0.6F * ScreenFx.hit(t, (float)b, 3.0F));
            fx.aberration(0.7F * ScreenFx.hit(t, (float)b, 3.0F));
            fx.shake(0.5F * ScreenFx.hit(t, (float)b, 5.0F));
            if (Prefs.impactFrames) {
               fx.impact(new int[][]{{1, 1}}, t - (float)b, (Vec3)null, 15913594);
            }
         }

         int h = 60;
         if (Prefs.impactFrames) {
            fx.impact(HIT, t - (float)h, (Vec3)null, 15913594);
         }

         fx.flash(16777215, 0.9F * ScreenFx.hit(t, (float)h, 4.0F));
         fx.shake(1.3F * ScreenFx.hit(t, (float)h, 10.0F));
         fx.aberration(1.0F * ScreenFx.hit(t, (float)h, 6.0F));
      }
   }

   public static void filmstrip(GuiGraphics g, float partial) {
      if (playing()) {
         float t = film.clock.time(partial);
         float a = Mth.clamp(t / 4.0F, 0.0F, 1.0F) * Mth.clamp((64.0F - t) / 6.0F, 0.0F, 1.0F);
         if (!(a <= 0.02F)) {
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            int w = g.guiWidth();
            int bar = Math.max(0, (int)(((float)g.guiHeight() - (float)w / 2.39F) / 2.0F));
            int cell = Math.max(10, Math.min(18, (w - 40) / 24));
            int x0 = w / 2 - cell * 12;
            int y0 = bar + 8;
            int lit = (int)Mth.clamp(t / 0.9F, 0.0F, 24.0F);
            int al = (int)(a * 255.0F) << 24;
            g.fill(x0 - 2, y0 - 4, x0 + cell * 24 + 2, y0 + cell + 4, al | 723726);

            for (int i = 0; i < 24; i++) {
               int x = x0 + i * cell;
               boolean on = i < lit;
               g.fill(x + 1, y0, x + cell - 1, y0 + cell, al | (on ? 15986660 : 2763310));
               g.fill(x + cell / 2 - 1, y0 - 3, x + cell / 2 + 1, y0 - 1, al | 15986660);
               g.fill(x + cell / 2 - 1, y0 + cell + 1, x + cell / 2 + 1, y0 + cell + 3, al | 15986660);
               if (on && cell >= 12) {
                  String n = Integer.toString(i + 1);
                  g.drawString(font, n, x + cell / 2 - font.width(n) / 2, y0 + cell / 2 - 4, al | 723726, false);
               }
            }
         }
      }
   }
}
