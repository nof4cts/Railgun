package com.kashimo.mod.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.Keys;
import com.kashimo.mod.Net;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.Arrays;
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
   static final String CAT = "key.categories.kashimo";
   static final KeyMapping[] KEYS = Arrays.copyOf(Keys.MOVES, 9);
   static final String[] NAMES = new String[]{
      "Lightning Strike",
      "Plant Nyoi Staff",
      "Sure-Hit Discharge",
      "Heavenly Thunderclap",
      "Hollow Wicker Basket",
      "Palace of the Storm God",
      "Mythical Beast Amber",
      "Thunder Repulsion",
      "Raijin's Fury"
   };
   static final String[] AMBER_NAMES = new String[]{
      "Sonic Wave",
      "Plant Nyoi Staff",
      "Atomic Discharge",
      "Magnetic Singularity",
      "Hollow Wicker Basket",
      "Palace of the Storm God",
      "FINAL: Heaven's Last Thunder",
      "Thunder Repulsion+",
      "Raijin's Fury+"
   };
   static int energy = 1000;
   static int amber;
   static int basket;
   static int[] cooldowns = new int[9];

   public static int energy() {
      return energy;
   }

   private Hud() {
   }

   private static KeyMapping key(String name, int code) {
      return new KeyMapping("key.kashimo." + name, KeyConflictContext.IN_GAME, Type.KEYSYM, code, "key.categories.kashimo");
   }

   public static void set(Net.State s) {
      energy = s.energy;
      amber = s.amber;
      basket = s.basket;
      cooldowns = s.cooldowns;
   }

   @SubscribeEvent
   public static void onKeys(RegisterKeyMappingsEvent e) {
      MinecraftForge.EVENT_BUS.addListener(Hud::onGui);
   }

   private static boolean carrying(Minecraft mc) {
      return ClientSorcerers.mine(Sorcerer.KASHIMO);
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
      if (carrying(mc) && !mc.options.hideGui && !KFx.cutsceneActive()) {
         GuiGraphics g = e.getGuiGraphics();
         int w = g.guiWidth();
         int h = g.guiHeight();
         int bx = w / 2 + 96;
         int by = h - 22;
         int bw = 90;
         if (amber > 0) {
            g.fill(bx, by + 7, bx + bw * amber / 1200, by + 9, -2512641);
            g.drawString(mc.font, "AMBER " + amber / 20 + "s", bx, by - 20, -3632897, true);
         }

         String[] names = amber > 0 ? AMBER_NAMES : NAMES;
         int y = h / 2 - 40;

         for (int i = 0; i < KEYS.length; i++) {
            String k = KEYS[i].getTranslatedKeyMessage().getString().toUpperCase();
            int cd = i < cooldowns.length ? cooldowns[i] : 0;
            int col = cd > 0 ? -8746343 : (amber > 0 ? -2246401 : -3148545);
            String line = "[" + k + "] " + names[i] + (cd > 0 ? "  " + (cd + 19) / 20 + "s" : "");
            Keys.mark(g, mc.font, line, 6, y + i * 11, i);
            g.drawString(mc.font, line, 6, y + i * 11, col, true);
         }

         if (basket > 0) {
            g.drawString(mc.font, "Hollow Wicker Basket " + (basket / 20 + 1) + "s", 6, y - 12, -6291488, true);
         }
      }
   }
}
