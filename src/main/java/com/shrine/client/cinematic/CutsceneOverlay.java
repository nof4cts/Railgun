package com.shrine.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class CutsceneOverlay {
   private CutsceneOverlay() {
   }

   public static void draw(GuiGraphics g, float partial) {
      if (Cutscene.active()) {
         Minecraft mc = Minecraft.getInstance();
         Font font = mc.font;
         int w = g.guiWidth();
         int h = g.guiHeight();
         float t = Cutscene.time(partial);
         int target = Math.max(0, (int)(((float)h - (float)w / 2.39F) / 2.0F));
         int bar = (int)((float)target * Mth.clamp(t / 8.0F, 0.0F, 1.0F));
         g.fill(0, 0, w, bar, -16777216);
         g.fill(0, h - bar, w, h, -16777216);
         Object[] card = Cutscene.card(partial);
         if (card != null) {
            String big = (String)card[0];
            String small = (String)card[1];
            int style = (Integer)card[2];
            float age = (Float)card[3];
            if (style == 1) {
               g.fill(0, 0, w, h, -16777216);
               float scale = 4.0F + 0.25F * Math.min(age, 14.0F) / 14.0F;
               g.pose().pushPose();
               g.pose().translate((float)w / 2.0F, (float)h / 2.0F - 18.0F, 0.0F);
               g.pose().scale(scale, scale, 1.0F);
               g.drawCenteredString(font, big, 0, -4, -1);
               g.pose().popPose();
               int a = (int)(255.0F * Mth.clamp(age / 4.0F, 0.0F, 1.0F));
               g.pose().pushPose();
               g.pose().translate((float)w / 2.0F, (float)h / 2.0F + 22.0F, 0.0F);
               g.pose().scale(1.5F, 1.5F, 1.0F);
               g.drawCenteredString(font, spaced(small), 0, 0, a << 24 | 14211288);
               g.pose().popPose();
            } else {
               float in = 1.0F - (float)Math.pow((double)(1.0F - Mth.clamp(age / 5.0F, 0.0F, 1.0F)), 3.0);
               int x = (int)((float)w * 0.08F - 30.0F * (1.0F - in));
               int y = h - bar - 58;
               int a = (int)(255.0F * in);
               g.pose().pushPose();
               g.pose().translate((float)x, (float)y, 0.0F);
               g.pose().scale(3.2F, 3.2F, 1.0F);
               g.drawString(font, big, 0, 0, a << 24 | 16777215, true);
               g.pose().popPose();
               g.fill(x, y + 32, x + (int)(180.0F * in), y + 33, a << 24 | 12589080);
               g.drawString(font, spaced(small), x, y + 38, a << 24 | 14737632, true);
            }
         }

         if (t > 10.0F) {
            String hint = "HOLD SHIFT TO SKIP";
            g.drawString(font, hint, w - font.width(hint) - 8, h - bar - 12, 1728053247, false);
         }
      }
   }

   private static String spaced(String s) {
      StringBuilder b = new StringBuilder();

      for (int i = 0; i < s.length(); i++) {
         if (i > 0) {
            b.append(' ');
         }

         b.append(s.charAt(i));
      }

      return b.toString();
   }
}
