package com.choso.mod.client;

import com.choso.mod.Net;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.Keys;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "jjk",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public final class Hud {
   static final String CAT = "key.categories.choso";
   static final KeyMapping[] KEYS = Keys.MOVES;
   static final String[] NAMES = new String[]{
      "Blood Edge",
      "Blood Meteorite",
      "Piercing Blood",
      "Slicing Exorcism",
      "Crimson Binding",
      "Domain: Kusozu Blood Realm",
      "Flowing Red Scale",
      "Convergence",
      "ULT: Blood Rain",
      "Supernova"
   };
   static int blood = 1000;
   static int scale;
   static int stack;
   static int converged;
   static int[] cooldowns = new int[10];

   public static int blood() {
      return blood;
   }

   private Hud() {
   }

   private static KeyMapping key(String name, int code) {
      return new KeyMapping("key.choso." + name, KeyConflictContext.IN_GAME, Type.KEYSYM, code, "key.categories.choso");
   }

   public static void set(Net.State s) {
      blood = s.blood;
      scale = s.scale;
      stack = s.stack;
      converged = s.converged;
      cooldowns = s.cooldowns;
   }

   @SubscribeEvent
   public static void onKeys(RegisterKeyMappingsEvent e) {
      MinecraftForge.EVENT_BUS.addListener(Hud::onGui);
   }

   static boolean carrying(Minecraft mc) {
      return ClientSorcerers.mine(Sorcerer.CHOSO);
   }

   private static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();

         for (int i = 0; i < KEYS.length; i++) {
            while (KEYS[i].consumeClick()) {
               if (mc.screen == null && carrying(mc)) {
                  Net.CHANNEL.sendToServer(new Net.Move(i));
               }
            }
         }
      }
   }

   private static void onGui(Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (carrying(mc) && !mc.options.hideGui && !CFx.cutsceneActive()) {
         GuiGraphics g = e.getGuiGraphics();
         int w = g.guiWidth();
         int h = g.guiHeight();
         int bx = w / 2 + 96;
         int by = h - 22;
         int bw = 90;
         int y2 = by - 20;
         if (scale > 0) {
            int max = stack >= 2 ? 600 : 900;
            g.fill(bx, by + 7, bx + bw * Math.min(scale, max) / max, by + 9, stack >= 2 ? -54742 : -4181958);
            g.drawString(mc.font, (stack >= 2 ? "RED SCALE: STACK " : "RED SCALE ") + scale / 20 + "s", bx, y2, -46518, true);
            y2 -= 10;
         }

         if (converged > 0) {
            g.drawString(mc.font, "CONVERGED " + (converged / 20 + 1) + "s", bx, y2, -20304, true);
         }

         int y = h / 2 - 50;

         for (int i = 0; i < KEYS.length; i++) {
            String k = KEYS[i].getTranslatedKeyMessage().getString().toUpperCase();
            int cd = i < cooldowns.length ? cooldowns[i] : 0;
            String name = NAMES[i];
            if (i == 6 && scale > 0 && stack < 2) {
               name = "Red Scale: STACK";
            }

            boolean powered = converged > 0 && (i == 2 || i == 9);
            int col = cd > 0 ? -8754582 : (powered ? -40864 : -862506);
            String line = "[" + k + "] " + name + (powered ? " +" : "") + (cd > 0 ? "  " + (cd + 19) / 20 + "s" : "");
            Keys.mark(g, mc.font, line, 6, y + i * 11, i);
            g.drawString(mc.font, line, 6, y + i * 11, col, true);
         }
      }
   }
}
