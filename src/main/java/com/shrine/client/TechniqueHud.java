package com.shrine.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.shrine.client.cinematic.Cutscene;
import com.shrine.common.Move;
import com.shrine.network.S2CCooldowns;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class TechniqueHud implements IGuiOverlay {
   public static final TechniqueHud INSTANCE = new TechniqueHud();
   private static long[] readyAt = new long[Move.values().length];
   private static int[] total = new int[Move.values().length];

   private TechniqueHud() {
   }

   public static void cooldowns(S2CCooldowns msg) {
      readyAt = msg.readyAt;
      total = msg.total;
   }

   public void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && !mc.options.hideGui && !Cutscene.active() && ClientSorcerers.mine(Sorcerer.SUKUNA)) {
         Font font = mc.font;
         long now = mc.level.getGameTime();
         int x = 6;
         int y = h - 6 - Move.values().length * 11;

         for (Move m : Move.values()) {
            int i = m.ordinal();
            long left = readyAt[i] - now;
            float frac = total[i] <= 0 ? 1.0F : 1.0F - Math.max(0.0F, (float)left / (float)total[i]);
            boolean ready = left <= 0L;
            String key = m == Move.BARRAGE
               ? "hold " + Keybinds.keyFor(m).getTranslatedKeyMessage().getString()
               : Keybinds.keyFor(m).getTranslatedKeyMessage().getString();
            String name = Component.translatable("move.shrine." + m.key).getString().toUpperCase(Locale.ROOT);
            g.fill(x, y + 9, x + 70, y + 10, 1426063360);
            g.fill(x, y + 9, x + (int)(70.0F * frac), y + 10, ready ? -2088928 : -9428960);
            g.drawString(font, "[" + key + "] " + name, x, y, ready ? -1 : -8355712, true);
            y += 11;
         }
      }
   }
}
