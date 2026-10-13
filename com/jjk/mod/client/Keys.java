package com.jjk.mod.client;

import com.avatar.mod.client.BendKeys;
import com.geto.csm.entity.DragonEntity;
import com.jjk.mod.JjkNet;
import com.jjk.mod.Sorcerer;
import com.kashimo.mod.Net;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.client.event.InputEvent.Key;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

public final class Keys {
   static final String CAT = "key.categories.jjk";
   public static final KeyMapping MENU = new KeyMapping("key.jjk.menu", KeyConflictContext.IN_GAME, Type.KEYSYM, 77, "key.categories.jjk");
   public static final KeyMapping[] PHYS = new KeyMapping[4];
   public static final KeyMapping PAGE = new KeyMapping("key.jjk.nextpage", KeyConflictContext.IN_GAME, Type.KEYSYM, 82, "key.categories.jjk");
   public static final Keys.VKey[] MOVES = new Keys.VKey[10];
   private static int page;

   private Keys() {
   }

   public static int page() {
      return page;
   }

   public static int pages() {
      Sorcerer s = ClientSorcerers.mine();
      int n = s.moves.length;
      if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof DragonEntity d) {
         n = d.riderMoves().length;
      }

      return Math.max(1, (n + 3) / 4);
   }

   public static String label(int slot) {
      return slot / 4 == page ? String.valueOf(slot % 4 + 1) : "·";
   }

   public static void mark(GuiGraphics g, Font font, String line, int x, int y, int slot) {
      if (slot / 4 == page && slot < MOVES.length) {
         int w = font.width(line);
         g.fill(x - 3, y - 2, x + w + 3, y + 10, -1073741824);
         g.fill(x - 3, y - 2, x - 1, y + 10, -1);
      }
   }

   public static String labelFull(int slot) {
      return "R".repeat(slot / 4) + (slot % 4 + 1);
   }

   static {
      int[] defaults = new int[]{49, 50, 51, 52};

      for (int i = 0; i < 4; i++) {
         PHYS[i] = new KeyMapping("key.jjk.num" + (i + 1), KeyConflictContext.IN_GAME, Type.KEYSYM, defaults[i], "key.categories.jjk");
      }

      for (int i = 0; i < MOVES.length; i++) {
         MOVES[i] = new Keys.VKey(i);
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      value = {Dist.CLIENT}
   )
   public static final class Pump {
      private static final int[] PRESSES = new int[4];
      private static int pagePresses;
      private static boolean touched;
      private static int slotBefore = -1;

      @SubscribeEvent
      public static void onKey(Key e) {
         Minecraft mc = Minecraft.getInstance();
         if (e.getAction() == 1 && mc.screen == null && mc.player != null) {
            for (int i = 0; i < 4; i++) {
               com.mojang.blaze3d.platform.InputConstants.Key key = Keys.PHYS[i].getKey();
               if (key.getType() == Type.KEYSYM && key.getValue() == e.getKey()) {
                  PRESSES[i]++;
               }
            }

            com.mojang.blaze3d.platform.InputConstants.Key pk = Keys.PAGE.getKey();
            if (pk.getType() == Type.KEYSYM && pk.getValue() == e.getKey()) {
               pagePresses++;
            }
         }
      }

      @SubscribeEvent(
         priority = EventPriority.LOWEST
      )
      public static void onTickEnd(ClientTickEvent e) {
         Minecraft mc = Minecraft.getInstance();
         if (e.phase == Phase.END && mc.player != null) {
            if (touched && slotBefore >= 0 && mc.player.getInventory().selected != slotBefore) {
               mc.player.getInventory().selected = slotBefore;
            }

            touched = false;
         }
      }

      @SubscribeEvent(
         priority = EventPriority.HIGHEST
      )
      public static void onTick(ClientTickEvent e) {
         if (e.phase == Phase.START) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               slotBefore = mc.player.getInventory().selected;
               Sorcerer s = ClientSorcerers.mine();
               boolean riding = mc.player.getVehicle() instanceof DragonEntity;
               boolean active = (s.usesKeys() || riding) && mc.screen == null;
               if (Keys.page >= Keys.pages()) {
                  Keys.page = 0;
               }

               while (Keys.PAGE.consumeClick()) {
               }

               int flips = pagePresses;
               pagePresses = 0;

               for (int n = 0; n < flips; n++) {
                  if (active) {
                     Keys.page = (Keys.page + 1) % Keys.pages();

                     for (Keys.VKey v : Keys.MOVES) {
                        v.down = false;
                     }

                     mc.player
                        .displayClientMessage(
                           Component.literal(
                                 "Moves "
                                    + (Keys.page * 4 + 1)
                                    + "-"
                                    + Math.min(Keys.page * 4 + 4, riding ? 10 : s.moves.length)
                                    + "  (page "
                                    + (Keys.page + 1)
                                    + "/"
                                    + Keys.pages()
                                    + ")"
                              )
                              .withStyle(st -> st.withColor(s.color)),
                           true
                        );
                     mc.getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.UI_BUTTON_CLICK.get(), 1.6F, 0.3F));
                  }
               }

               for (int i = 0; i < 4; i++) {
                  if (active) {
                     for (KeyMapping hb : mc.options.keyHotbarSlots) {
                        if (hb.same(Keys.PHYS[i])) {
                           while (hb.consumeClick()) {
                           }
                        }
                     }
                  }

                  int slot = Keys.page * 4 + i;

                  while (Keys.PHYS[i].consumeClick()) {
                  }

                  int c = PRESSES[i];
                  PRESSES[i] = 0;
                  com.mojang.blaze3d.platform.InputConstants.Key key = Keys.PHYS[i].getKey();
                  boolean raw = mc.screen == null
                     && key.getType() == Type.KEYSYM
                     && key.getValue() > 0
                     && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
                  if (active && (raw || c > 0)) {
                     touched = true;
                  }

                  for (Keys.VKey v : Keys.MOVES) {
                     if (v.slot % 4 == i) {
                        boolean mine = v.slot == slot && active;
                        v.down = mine && (Keys.PHYS[i].isDown() || raw);
                        if (mine) {
                           v.clicks += c;
                        }
                     }
                  }
               }
            }
         }
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

         for (KeyMapping k : Keys.PHYS) {
            e.register(k);
         }

         e.register(Keys.PAGE);
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
                  if (s.isAvatar()) {
                     BendKeys.tick(mc, s);
                  } else if (s != Sorcerer.SUKUNA) {
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
                                 case HIGURUMA:
                                    com.higuruma.mod.Net.CHANNEL.sendToServer(new com.higuruma.mod.Net.Move(i));
                                    break;
                                 case TEN_SHADOWS:
                                    com.mahoraga.mod.Net.CHANNEL.sendToServer(new com.mahoraga.mod.Net.Move(i));
                                    break;
                                 case YUTA:
                                    com.yuta.mod.Net.CHANNEL.sendToServer(new com.yuta.mod.Net.Move(i));
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

   public static final class VKey extends KeyMapping {
      final int slot;
      int clicks;
      boolean down;

      VKey(int slot) {
         super("key.jjk.vmove" + (slot + 1), InputConstants.UNKNOWN.getValue(), "key.categories.jjk");
         this.slot = slot;
      }

      public boolean consumeClick() {
         if (this.clicks == 0) {
            return false;
         } else {
            this.clicks--;
            return true;
         }
      }

      public boolean isDown() {
         return this.down;
      }

      public Component getTranslatedKeyMessage() {
         return Component.literal(Keys.label(this.slot));
      }
   }
}
