package com.yuta.mod.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.Keys;
import com.yuta.mod.Net;
import com.yuta.mod.Yuta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class Hud {
   static int[] cooldowns = new int[9];
   private static long stamp;

   private Hud() {
   }

   public static void set(Net.State s) {
      cooldowns = s.cooldowns();
      stamp = System.currentTimeMillis();
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.options.hideGui && !YuFx.cutsceneActive() && YuFx.mine()) {
         GuiGraphics g = e.getGuiGraphics();
         int h = g.guiHeight();
         boolean fresh = System.currentTimeMillis() - stamp < 600L;
         String[] moves = Sorcerer.YUTA.moves;
         int y = h / 2 - moves.length * 11 / 2;

         for (int i = 0; i < moves.length; i++) {
            int cd = fresh && i < cooldowns.length ? cooldowns[i] : 0;
            boolean needsKatana = i == 0 && !Yuta.hasKatana(mc.player);
            String line = "[" + Keys.label(i) + "] " + moves[i] + (cd > 20 ? "  " + (cd + 19) / 20 + "s" : "");
            Keys.mark(g, mc.font, line, 6, y + i * 11, i);
            g.drawString(mc.font, line, 6, y + i * 11, !needsKatana && cd <= 0 ? -2300161 : -9538424, true);
         }

         int full = fresh && cooldowns.length > 9 ? cooldowns[9] : 0;
         if (full > 0) {
            int sec = (full + 19) / 20;
            g.drawCenteredString(
               mc.font, "RIKA: FULL MANIFESTATION  " + sec / 60 + ":" + String.format("%02d", sec % 60), g.guiWidth() / 2, g.guiHeight() - 50, -2702593
            );
         }
      }
   }
}
