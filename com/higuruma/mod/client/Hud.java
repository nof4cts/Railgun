package com.higuruma.mod.client;

import com.higuruma.mod.Net;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.Keys;
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
   static int amp;
   static int sword;
   static int plea = -1;
   static int[] cooldowns = new int[7];
   private static long stamp;

   private Hud() {
   }

   public static void set(Net.State s) {
      amp = s.amp();
      sword = s.sword();
      plea = s.plea();
      cooldowns = s.cooldowns();
      stamp = System.currentTimeMillis();
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.options.hideGui && !HFx.cutsceneActive()) {
         GuiGraphics g = e.getGuiGraphics();
         int w = g.guiWidth();
         int h = g.guiHeight();
         boolean fresh = System.currentTimeMillis() - stamp < 600L;
         if (fresh && plea >= 0) {
            float pulse = 0.75F + 0.25F * Mth.sin((float)System.currentTimeMillis() / 180.0F);
            g.drawCenteredString(mc.font, "PLEAD YOUR CASE  -  " + (plea + 19) / 20 + "s", w / 2, h / 2 + 28, (int)(pulse * 255.0F) << 24 | 15254634);
            g.drawCenteredString(mc.font, "Type your defence in chat", w / 2, h / 2 + 40, -2568000);
         }

         if (ClientSorcerers.mine() == Sorcerer.HIGURUMA) {
            String[] moves = Sorcerer.HIGURUMA.moves;
            int y = h / 2 - moves.length * 11 / 2;

            for (int i = 0; i < moves.length; i++) {
               int cd = fresh && i < cooldowns.length ? cooldowns[i] : 0;
               boolean locked = i == 6 && sword <= 0;
               String line = "[" + Keys.label(i) + "] " + moves[i] + (cd > 20 ? "  " + (cd + 19) / 20 + "s" : "");
               Keys.mark(g, mc.font, line, 6, y + i * 11, i);
               g.drawString(mc.font, line, 6, y + i * 11, !locked && cd <= 0 ? -728400 : -8489370, true);
            }

            int ty = h - 50;
            if (fresh && sword > 0) {
               g.drawCenteredString(mc.font, "SWORD OF LIGHT  " + (sword + 19) / 20 + "s  -  5 cuts kill", w / 2, ty, -2078648);
               ty -= 10;
            }

            if (fresh && amp > 0) {
               g.drawCenteredString(mc.font, "DOMAIN AMPLIFICATION  " + (amp + 19) / 20 + "s", w / 2, ty, -1522582);
            }
         }
      }
   }
}
