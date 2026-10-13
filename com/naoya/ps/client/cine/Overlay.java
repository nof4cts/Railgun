package com.naoya.ps.client.cine;

import com.naoya.ps.client.Prefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class Overlay {
   private Overlay() {
   }

   public static void draw(GuiGraphics g, float partial) {
      Minecraft mc = Minecraft.getInstance();
      Film film = Director.film();
      if (film != null) {
         Font font = mc.font;
         int w = g.guiWidth();
         int h = g.guiHeight();
         float weight = Director.weight();
         int target = Math.max(0, (int)(((float)h - (float)w / 2.39F) / 2.0F));
         int bar = (int)((float)target * Film.smooth(weight * 1.4F));
         g.fill(0, 0, w, bar, -16777216);
         g.fill(0, h - bar, w, h, -16777216);
         float t = film.clock.time(partial);
         Film.Card cd = film.card;
         if (cd != null && Prefs.titleCards && t >= (float)cd.start() && t < (float)(cd.start() + cd.length()) && !Director.skipped()) {
            card(g, font, cd.style(), cd.kanji(), cd.title(), cd.color(), true, t - (float)cd.start(), cd.length(), w, h, bar);
         }

         subtitles(g, font, film.lines, t, w, h, bar);
         if (t > 10.0F && weight > 0.5F && !Director.skipped()) {
            String hint = "HOLD " + mc.options.keyShift.getTranslatedKeyMessage().getString().toUpperCase() + " TO SKIP";
            int a = (int)(Mth.clamp((t - 10.0F) / 10.0F, 0.0F, 1.0F) * 110.0F);
            g.drawString(font, hint, w - font.width(hint) - 10, h - Math.max(bar, 14) + (bar > 14 ? (bar - 8) / 2 : -2), a << 24 | 16777215, false);
         }
      }
   }

   private static void card(
      GuiGraphics g, Font font, String styleText, String kanji, String title, int color, boolean king, float age, int length, int w, int h, int bar
   ) {
      float in = 1.0F - (float)Math.pow((double)(1.0F - Mth.clamp(age / 5.0F, 0.0F, 1.0F)), 3.0);
      float out = Mth.clamp(((float)length - age) / 4.0F, 0.0F, 1.0F);
      float a = Math.min(in, out);
      if (!(a <= 0.02F)) {
         int alpha = (int)(255.0F * a);
         int x = (int)((float)w * 0.07F - 40.0F * (1.0F - in));
         int y = h - bar - (king ? 92 : 78);
         String style = spaced(styleText);
         g.drawString(font, style, x, y, (int)((float)alpha * 0.8F) << 24 | 13684944, true);
         float scale = king ? 4.2F + 0.2F * Mth.clamp(age / (float)length, 0.0F, 1.0F) : 3.4F;
         g.pose().pushPose();
         g.pose().translate((float)x, (float)(y + 12), 0.0F);
         g.pose().scale(scale, scale, 1.0F);
         g.drawString(font, kanji, 0, 0, alpha << 24 | 16777215, true);
         g.pose().popPose();
         int ny = y + 12 + (int)(9.0F * scale) + 4;
         int stroke = (int)(Math.max(160.0F, (float)font.width(title.toUpperCase()) * 1.6F + 20.0F) * Mth.clamp(age / 6.0F, 0.0F, 1.0F));
         int col = color & 16777215;
         g.fill(x, ny, x + stroke, ny + 2, alpha << 24 | col);
         g.pose().pushPose();
         g.pose().translate((float)x, (float)(ny + 6), 0.0F);
         g.pose().scale(1.6F, 1.6F, 1.0F);
         g.drawString(font, title.toUpperCase(), 0, 0, alpha << 24 | 16777215, true);
         g.pose().popPose();
      }
   }

   private static void subtitles(GuiGraphics g, Font font, String[][] lines, float t, int w, int h, int bar) {
      if (lines != null) {
         for (int i = 0; i < lines.length; i++) {
            float start = Float.parseFloat(lines[i][0]);
            float end = i + 1 < lines.length ? Math.min(start + 40.0F, Float.parseFloat(lines[i + 1][0])) : start + 40.0F;
            if (!(t < start) && !(t > end)) {
               float a = Math.min(Mth.clamp((t - start) / 4.0F, 0.0F, 1.0F), Mth.clamp((end - t) / 5.0F, 0.0F, 1.0F));
               if (!(a <= 0.02F)) {
                  String line = "「" + lines[i][1] + "」";
                  float scale = bar > 18 ? 1.25F : 1.5F;
                  g.pose().pushPose();
                  g.pose()
                     .translate(
                        (float)w / 2.0F - (float)font.width(line) * scale / 2.0F,
                        bar > 18 ? (float)(h - bar) + ((float)bar - 9.0F * scale) / 2.0F : (float)(h - bar - 34),
                        0.0F
                     );
                  g.pose().scale(scale, scale, 1.0F);
                  g.drawString(font, line, 0, 0, (int)(a * 255.0F) << 24 | 15923455, true);
                  g.pose().popPose();
               }
            }
         }
      }
   }

   private static String spaced(String s) {
      StringBuilder b = new StringBuilder();

      for (int i = 0; i < s.length(); i++) {
         if (i > 0 && s.charAt(i) != ' ' && s.charAt(i - 1) != ' ') {
            b.append(' ');
         }

         b.append(s.charAt(i));
      }

      return b.toString();
   }

   public static boolean hideHud() {
      return Director.active() && Director.weight() > 0.3F;
   }
}
