package com.avatar.mod.client;

import com.avatar.mod.Net;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.Keys;
import com.jjk.mod.client.SelectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class Hud {
   static int charge = -1;
   static int lcharge = -1;
   static int blue;
   static int stance;
   static int[] cooldowns = new int[10];

   private Hud() {
   }

   public static void set(Net.State s) {
      charge = s.charge();
      lcharge = s.lcharge();
      blue = s.blue();
      stance = s.stance();
      cooldowns = s.cooldowns();
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      Minecraft mc = Minecraft.getInstance();
      Sorcerer s = ClientSorcerers.mine();
      if (mc.player != null && s.isAvatar() && !mc.options.hideGui && !FireFx.cutsceneActive() && !(mc.screen instanceof SelectorScreen)) {
         GuiGraphics g = e.getGuiGraphics();
         int w = g.guiWidth();
         int h = g.guiHeight();
         int y = h / 2 - s.moves.length * 11 / 2;

         for (int i = 0; i < s.moves.length; i++) {
            int cd = i < cooldowns.length ? cooldowns[i] : 0;
            String line = "[" + Keys.label(i) + "] " + s.moves[i] + (cd > 20 ? "  " + (cd + 19) / 20 + "s" : "");
            Keys.mark(g, mc.font, line, 6, y + i * 11, i);
            g.drawString(mc.font, line, 6, y + i * 11, cd > 0 ? -7701904 : -6448, true);
         }

         int ty = h - 50;
         if (blue > 0) {
            g.drawCenteredString(mc.font, "BLUE FIRE " + (blue + 19) / 20 + "s", w / 2, ty, -8406785);
            ty -= 10;
         }

         if (stance > 0) {
            g.drawCenteredString(mc.font, "REDIRECTION STANCE " + (stance + 19) / 20 + "s", w / 2, ty, -4663041);
         }

         if (lcharge >= 0) {
            float k = Mth.clamp((float)lcharge / 30.0F, 0.0F, 1.0F);
            boolean full = k >= 1.0F;
            int bw = 70;
            int bx = w / 2 - bw / 2;
            int by = h / 2 + 14;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, -1610612736);
            g.fill(bx, by, bx + (int)((float)bw * k), by + 3, full ? -1510145 : -11891457);
            String label = full ? (lcharge > 120 ? "TOO LONG - RELEASE NOW!" : "RELEASE TO STRIKE") : "SEPARATING THE ENERGIES...";
            g.drawCenteredString(mc.font, label, w / 2, by + 6, full ? -1510145 : -7358209);
         }

         if (charge >= 5) {
            float k = Mth.clamp((float)(charge - 5) / 35.0F, 0.0F, 1.0F);
            boolean full = k >= 1.0F;
            int bw = 70;
            int bx = w / 2 - bw / 2;
            int by = h / 2 + 14;
            float pulse = full ? 0.75F + 0.25F * Mth.sin(((float)mc.player.tickCount + e.getPartialTick()) * 0.8F) : 1.0F;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 4, -1610612736);
            int col = full ? -3640 : lerpColor(-4705782, -24282, k);
            g.fill(bx, by, bx + (int)((float)bw * k), by + 3, col);
            String label = full ? "FULL BREATH - RELEASE!" : "BREATHE IN...";
            g.drawCenteredString(mc.font, label, w / 2, by + 6, (int)(pulse * 255.0F) << 24 | (full ? 16773576 : 16756848));
         }
      }
   }

   private static int lerpColor(int a, int b, float k) {
      int r = (int)Mth.lerp(k, (float)(a >> 16 & 0xFF), (float)(b >> 16 & 0xFF));
      int gg = (int)Mth.lerp(k, (float)(a >> 8 & 0xFF), (float)(b >> 8 & 0xFF));
      int bl = (int)Mth.lerp(k, (float)(a & 0xFF), (float)(b & 0xFF));
      return 0xFF000000 | r << 16 | gg << 8 | bl;
   }
}
