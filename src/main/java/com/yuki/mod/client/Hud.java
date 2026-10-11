package com.yuki.mod.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.Keys;
import com.yuki.mod.Net;
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
   static final String[] NAMES = Sorcerer.YUKI.moves;
   static int energy = 1000;
   static int mass;
   static int garuda;
   static int simple;
   static int armed;
   static int[] cooldowns = new int[10];

   private Hud() {
   }

   public static void set(Net.State s) {
      energy = s.energy;
      mass = s.mass;
      garuda = s.garuda;
      simple = s.simple;
      armed = s.armed;
      cooldowns = s.cooldowns;
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (ClientSorcerers.mine(Sorcerer.YUKI) && !mc.options.hideGui && !YFx.cutsceneActive()) {
         GuiGraphics g = e.getGuiGraphics();
         int w = g.guiWidth();
         int h = g.guiHeight();
         int bx = w / 2 + 96;
         int by = h - 22;
         int bw = 90;
         g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, -1073741824);
         g.fill(bx, by, bx + bw * Math.min(energy, 1000) / 1000, by + 5, mass > 0 ? -2982913 : -6595329);
         g.drawString(mc.font, "CE " + energy, bx, by - 10, -1913601, true);
         int y2 = by - 20;
         if (mass > 0) {
            g.fill(bx, by + 7, bx + bw * Math.min(mass, 1200) / 1200, by + 9, -3703297);
            g.drawString(mc.font, "STAR RAGE: MASS " + mass / 20 + "s", bx, y2, -2513153, true);
            y2 -= 10;
         }

         if (garuda > 0) {
            g.drawString(mc.font, "GARUDA " + garuda / 20 + "s", bx, y2, -1514256, true);
            y2 -= 10;
         }

         if (simple > 0) {
            g.drawString(mc.font, "SIMPLE DOMAIN " + (simple / 20 + 1) + "s", bx, y2, -4656897, true);
         }

         if (armed > 0) {
            String warn = "Press " + Keys.label(8) + " again: BLACK HOLE (costs your life)";
            g.drawCenteredString(mc.font, warn, w / 2, h / 2 + 24, -2068225);
         }

         int y = h / 2 - 55;

         for (int i = 0; i < NAMES.length; i++) {
            int cd = i < cooldowns.length ? cooldowns[i] : 0;
            int col = cd > 0 ? -8753528 : -1319425;
            String line = "[" + Keys.label(i) + "] " + NAMES[i] + (cd > 0 ? "  " + (cd + 19) / 20 + "s" : "");
            g.drawString(mc.font, line, 6, y + i * 11, col, true);
         }
      }
   }
}
