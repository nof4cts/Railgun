package com.shrine.client;

import com.shrine.config.ShrineConfig;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.Keys;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.shrine.client.cinematic.Cutscene;
import com.shrine.common.Move;
import com.shrine.network.C2SCast;
import com.shrine.network.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;

public final class Keybinds {
   private static final String CATEGORY = "key.categories.shrine";
   public static final KeyMapping DISMANTLE = Keys.MOVES[0];
   public static final KeyMapping CLEAVE = Keys.MOVES[1];
   public static final KeyMapping SPIDERWEB = Keys.MOVES[2];
   public static final KeyMapping BLACK_FLASH = Keys.MOVES[3];
   public static final KeyMapping FUGA = Keys.MOVES[4];
   public static final KeyMapping DOMAIN = Keys.MOVES[5];
   public static final KeyMapping WORLD_SLASH = Keys.MOVES[6];
   private static int dismantleHeld = -1;

   private Keybinds() {
   }

   private static KeyMapping key(String name, int code) {
      return new KeyMapping("key.shrine." + name, KeyConflictContext.IN_GAME, Type.KEYSYM, code, "key.categories.shrine");
   }

   public static void register(RegisterKeyMappingsEvent event) {
   }

   public static KeyMapping keyFor(Move m) {
      return switch (m) {
         case DISMANTLE, BARRAGE -> DISMANTLE;
         case CLEAVE -> CLEAVE;
         case SPIDERWEB -> SPIDERWEB;
         case BLACK_FLASH -> BLACK_FLASH;
         case FUGA -> FUGA;
         case DOMAIN -> DOMAIN;
         case WORLD_SLASH -> WORLD_SLASH;
      };
   }

   private static void send(Move m) {
      Net.CHANNEL.sendToServer(new C2SCast(m));
   }

   public static void handle() {
      Minecraft mc = Minecraft.getInstance();
      if (!ClientSorcerers.mine(Sorcerer.SUKUNA)) {
         dismantleHeld = -1;
      } else {
         if (mc.player != null && !Cutscene.active() && mc.screen == null) {
            if (DISMANTLE.isDown()) {
               if (dismantleHeld >= 0) {
                  dismantleHeld++;
               }

               if (dismantleHeld == 6) {
                  send(Move.BARRAGE);
               }
            } else if (dismantleHeld >= 0) {
               if (dismantleHeld < 6) {
                  send(Move.DISMANTLE);
               }

               dismantleHeld = -1;
            }

            while (DISMANTLE.consumeClick()) {
               if (dismantleHeld < 0) {
                  dismantleHeld = 0;
               }
            }

            while (CLEAVE.consumeClick()) {
               send(Move.CLEAVE);
            }

            while (SPIDERWEB.consumeClick()) {
               send(Move.SPIDERWEB);
            }

            while (BLACK_FLASH.consumeClick()) {
               send(Move.BLACK_FLASH);
            }

            while (FUGA.consumeClick()) {
               // Open: the film cutscene, or (sneaking, or with the film turned off) the move as it happens in the film
               Net.CHANNEL.sendToServer(new C2SCast(Move.FUGA, ShrineConfig.CLIENT.openFilm.get() && !mc.player.isShiftKeyDown()));
            }

            while (DOMAIN.consumeClick()) {
               send(Move.DOMAIN);
            }

            while (WORLD_SLASH.consumeClick()) {
               send(Move.WORLD_SLASH);
            }
         } else {
            dismantleHeld = -1;
         }
      }
   }
}
