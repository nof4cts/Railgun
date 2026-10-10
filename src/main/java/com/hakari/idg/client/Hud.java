package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class Hud {
   public static void render(GuiGraphics g, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !CameraDirector.active()) {
         Font font = mc.font;
         float w = (float)g.guiWidth();
         float h = (float)g.guiHeight();
         long t = Vfx.now();
         StatePacket self = ClientState.get(mc.player.getId());
         StatePacket dom = ClientState.domainContaining(mc.player.position());
         if (dom != null && dom.domainActive) {
            boolean mine = self != null && dom == self;
            int y = 6;
            g.fill((int)(w / 2.0F - 110.0F), y - 2, (int)(w / 2.0F + 110.0F), y + (mine ? 40 : 22), -1709965298);
            g.fill((int)(w / 2.0F - 110.0F), y - 2, (int)(w / 2.0F + 110.0F), y - 1, -45128);
            ScreenFx.bigText(g, font, "坐殺博徒 · IDLE DEATH GAMBLE", w / 2.0F, (float)(y + 5), 1.0F, -29992, false, 1.0F);
            float secs = Math.max(0.0F, ((float)dom.domainTicks - ClientState.ticksSince(dom)) / 20.0F);
            ScreenFx.bigText(g, font, String.format("%d:%02d", (int)secs / 60, (int)secs % 60), w / 2.0F, (float)(y + 15), 0.9F, -1, false, 1.0F);
            if (mine) {
               for (int i = 0; i < 2; i++) {
                  int x = (int)(w / 2.0F - 60.0F + (float)(i * 14));
                  int c = i < self.visuals ? -11443 : -11915200;
                  g.fill(x, y + 25, x + 10, y + 35, c);
               }

               g.drawString(font, "VISUALS", (int)(w / 2.0F - 30.0F), y + 26, -1, false);
               String miss = self.fails >= 3 ? "PITY READY" : "MISSES " + self.fails + "/3";
               int mc2 = self.fails >= 3 ? Draw.hsv((float)t / 400.0F, 0.6F, 1.0F) : -4473925;
               g.drawString(font, miss, (int)(w / 2.0F + 22.0F), y + 26, mc2, false);
               if (self.boosted) {
                  ScreenFx.bigText(g, font, "確変 PROBABILITY UP", w / 2.0F, (float)(y + 48), 1.0F, Draw.hsv((float)t / 600.0F, 0.7F, 1.0F), false, 1.0F);
               }
            }
         }

         if (self != null && self.jackpotTicks > 0) {
            float left = ClientState.jackpotSecondsLeft(self);
            String clock = String.format("%d:%02d", (int)left / 60, (int)left % 60);
            float pulse = 1.0F + 0.05F * Mth.sin((float)t / 120.0F);
            int gold = left < 10.0F ? (t / 250L % 2L == 0L ? -50630 : -11443) : -11443;
            ScreenFx.bigText(g, font, clock, w / 2.0F, 26.0F, 3.2F * pulse, gold, true, 1.0F);
            ScreenFx.bigText(g, font, "JACKPOT  ·  ∞ CURSED ENERGY", w / 2.0F, 46.0F, 0.9F, -3920, false, 1.0F);
            float frac = self.jackpotMax > 0 ? Mth.clamp(left * 20.0F / (float)self.jackpotMax, 0.0F, 1.0F) : 0.0F;
            int bw = 160;
            g.fill((int)(w / 2.0F - (float)bw / 2.0F), 54, (int)(w / 2.0F + (float)bw / 2.0F), 57, Integer.MIN_VALUE);
            g.fill((int)(w / 2.0F - (float)bw / 2.0F), 54, (int)(w / 2.0F - (float)bw / 2.0F + (float)bw * frac), 57, gold);
         }

         if (self != null && self.rhythmTicks > 0) {
            float beat = (float)(t % 500L) / 500.0F;
            int r = (int)(6.0F + 10.0F * Ease.outExpo(beat));
            int cx = (int)(w / 2.0F);
            int cy = (int)(h - 58.0F);
            int col = Draw.alpha(-10685697, 1.0F - beat);
            g.fill(cx - r, cy - 1, cx + r, cy + 1, col);
            g.fill(cx - 1, cy - r, cx + 1, cy + r, col);
            g.drawString(font, "RHYTHM", cx - font.width("RHYTHM") / 2, cy + 12, -10685697, false);
         }
      }
   }

   private Hud() {
   }
}
