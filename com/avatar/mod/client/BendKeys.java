package com.avatar.mod.client;

import com.avatar.mod.Net;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.Keys;
import net.minecraft.client.Minecraft;

public final class BendKeys {
   private static final boolean[] HELD = new boolean[Keys.MOVES.length];

   private BendKeys() {
   }

   public static void tick(Minecraft mc, Sorcerer s) {
      for (int i = 0; i < Keys.MOVES.length; i++) {
         int clicks = 0;

         while (Keys.MOVES[i].consumeClick()) {
            clicks++;
         }

         boolean usable = mc.screen == null && i < s.moves.length;
         boolean down = usable && Keys.MOVES[i].isDown();
         if (down != HELD[i]) {
            Net.CHANNEL.sendToServer(new Net.Key(i, down));
            HELD[i] = down;
         } else if (!down && clicks > 0 && usable) {
            Net.CHANNEL.sendToServer(new Net.Key(i, true));
            Net.CHANNEL.sendToServer(new Net.Key(i, false));
         }
      }
   }
}
