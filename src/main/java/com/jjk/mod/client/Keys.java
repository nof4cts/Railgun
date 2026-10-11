package com.jjk.mod.client;

import com.geto.csm.entity.DragonEntity;
import com.jjk.mod.JjkNet;
import com.jjk.mod.Sorcerer;
import com.kashimo.mod.Net;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

public final class Keys {
   static final String CAT = "key.categories.jjk";
   public static final KeyMapping MENU = new KeyMapping("key.jjk.menu", KeyConflictContext.IN_GAME, Type.KEYSYM, 77, "key.categories.jjk");
   private static final int[] DEFAULTS = new int[]{71, 72, 74, 75, 86, 78, 66, 82, 67, 90};
   public static final KeyMapping[] MOVES = new KeyMapping[10];

   private Keys() {
   }

   public static String label(int slot) {
      return slot < MOVES.length ? MOVES[slot].getTranslatedKeyMessage().getString().toUpperCase() : "?";
   }

   static {
      for (int i = 0; i < MOVES.length; i++) {
         MOVES[i] = new KeyMapping("key.jjk.move" + (i + 1), KeyConflictContext.IN_GAME, Type.KEYSYM, DEFAULTS[i], "key.categories.jjk");
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class Reg {
      @SubscribeEvent
      public static void onKeys(RegisterKeyMappingsEvent e) {
         e.register(Keys.MENU);

         for (KeyMapping k : Keys.MOVES) {
            e.register(k);
         }
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      value = {Dist.CLIENT}
   )
   public static final class RideClick {
      @SubscribeEvent
      public static void onUse(InteractionKeyMappingTriggered e) {
         Minecraft mc = Minecraft.getInstance();
         if (e.isUseItem() && mc.player != null && mc.player.getVehicle() instanceof DragonEntity d && d.ownerId() == mc.player.getId()) {
            JjkNet.CHANNEL.sendToServer(new JjkNet.Key(0));
            e.setSwingHand(false);
            e.setCanceled(true);
         }
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      value = {Dist.CLIENT}
   )
   public static final class Tick {
      @SubscribeEvent
      public static void onTick(ClientTickEvent e) {
         if (e.phase == Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               while (Keys.MENU.consumeClick()) {
                  if (mc.screen == null) {
                     mc.setScreen(new SelectorScreen());
                  }
               }

               if (mc.player.getVehicle() instanceof DragonEntity dragon) {
                  for (int i = 0; i < Keys.MOVES.length; i++) {
                     while (Keys.MOVES[i].consumeClick()) {
                        if (mc.screen == null && i < dragon.riderMoves().length) {
                           JjkNet.CHANNEL.sendToServer(new JjkNet.Key(i));
                        }
                     }
                  }
               } else {
                  Sorcerer s = ClientSorcerers.mine();
                  if (s != Sorcerer.SUKUNA) {
                     for (int i = 0; i < Keys.MOVES.length; i++) {
                        while (Keys.MOVES[i].consumeClick()) {
                           if (mc.screen == null && s.usesKeys() && i < s.moves.length) {
                              switch (s) {
                                 case KASHIMO:
                                    Net.CHANNEL.sendToServer(new Net.Move(i));
                                    break;
                                 case CHOSO:
                                    com.choso.mod.Net.CHANNEL.sendToServer(new com.choso.mod.Net.Move(i));
                                    break;
                                 case YUKI:
                                    com.yuki.mod.Net.CHANNEL.sendToServer(new com.yuki.mod.Net.Move(i));
                                    break;
                                 case NAOYA:
                                    com.naoya.ps.Net.CHANNEL.sendToServer(new com.naoya.ps.Net.Move(i));
                                    break;
                                 default:
                                    JjkNet.CHANNEL.sendToServer(new JjkNet.Key(i));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
