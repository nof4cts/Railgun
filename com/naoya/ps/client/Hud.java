package com.naoya.ps.client;

import com.jjk.mod.JjkNet;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.Keys;
import com.naoya.ps.Net;
import com.naoya.ps.client.cine.Director;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class Hud {
   private static long[] ready = new long[8];
   private static int[] total = new int[8];
   private static int accel;

   private Hud() {
   }

   public static void accept(Net.State s) {
      ready = s.ready();
      total = s.total();
      accel = s.accel();
   }

   public static void draw(GuiGraphics g, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && !mc.options.hideGui && !Director.active() && ClientSorcerers.mine() == Sorcerer.NAOYA) {
         Font font = mc.font;
         int w = g.guiWidth();
         int color = Sorcerer.NAOYA.color;
         long now = mc.level.getGameTime();
         int ce = JjkNet.EnergySync.clientValue;
         int barW = 90;
         int bx = w - barW - 4;
         int by = 19;
         g.fill(bx - 1, by - 1, bx + barW + 1, by + 6, -1879048192);
         g.fill(bx, by, bx + barW * Math.min(ce, 1000) / 1000, by + 5, 0xFF000000 | color);
         String ceText = "CE " + ce + " / 1000";
         g.drawString(font, ceText, bx - font.width(ceText) - 4, by - 1, -2039584, true);
         int fy = by + 10;
         String lab = "ACCEL";
         g.drawString(font, lab, w - 4 - 75 - font.width(lab) - 4, fy + 1, -2039584, true);

         for (int i = 0; i < 5; i++) {
            int x = w - 4 - (5 - i) * 15;
            boolean on = i < accel;
            g.fill(x, fy, x + 13, fy + 10, -16053490);
            g.fill(x + 1, fy + 2, x + 12, fy + 8, on ? 0xFF000000 | color : -14013906);
            g.fill(x + 1, fy, x + 3, fy + 1, -790556);
            g.fill(x + 10, fy, x + 12, fy + 1, -790556);
            g.fill(x + 1, fy + 9, x + 3, fy + 10, -790556);
            g.fill(x + 10, fy + 9, x + 12, fy + 10, -790556);
         }

         String[] moves = Sorcerer.NAOYA.moves;
         int y = g.guiHeight() / 2 - moves.length * 11 / 2;

         for (int i = 0; i < moves.length; i++) {
            long left = i < ready.length ? ready[i] - now : 0L;
            float cd = left > 0L && i < total.length ? Mth.clamp((float)left / (float)Math.max(1, total[i]), 0.0F, 1.0F) : 0.0F;
            String line = "[" + Keys.label(i) + "] " + moves[i];
            int col = cd > 0.0F ? -8750456 : -986891;
            Keys.mark(g, font, line, 6, y, i);
            g.drawString(font, line, 6, y, col, true);
            int lw = font.width(line);
            g.fill(6, y + 9, 6 + lw, y + 10, 1342177280);
            g.fill(6, y + 9, 6 + (int)((float)lw * (1.0F - cd)), y + 10, 0xFF000000 | color);
            y += 12;
         }
      }
   }
}
