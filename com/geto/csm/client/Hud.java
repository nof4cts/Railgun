package com.geto.csm.client;

import com.geto.csm.CurseKind;
import com.geto.csm.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;

public final class Hud {
   public static void render(GuiGraphics g) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !CameraDirector.active()) {
         boolean holding = mc.player.getMainHandItem().is((Item)ModRegistry.MANIPULATION.get())
            || mc.player.getOffhandItem().is((Item)ModRegistry.MANIPULATION.get())
            || mc.player.getMainHandItem().is((Item)ModRegistry.UZUMAKI.get());
         if (holding) {
            CurseKind k = CurseKind.byId(ClientState.selected);
            int x = 10;
            int y = g.guiHeight() - 74;
            g.fill(x - 4, y - 4, x + 170, y + 46, -1610087408);
            g.fill(x - 4, y - 4, x - 2, y + 46, k.color);
            g.pose().pushPose();
            g.pose().translate((float)x, (float)y, 0.0F);
            g.pose().scale(1.8F, 1.8F, 1.0F);
            g.drawString(mc.font, k.jp, 0, 0, k.color, false);
            g.pose().popPose();
            g.drawString(mc.font, k.title, x + 4 + (int)((float)mc.font.width(k.jp) * 1.8F), y + 4, -1, false);
            g.drawString(mc.font, k.grade, x, y + 19, -5201728, false);
            float cd = ClientState.cooldownSeconds(k.ordinal());
            String status = cd > 0.0F ? String.format("recovering %.1fs", cd) : "ready — right-click";
            g.drawString(mc.font, status, x, y + 30, cd > 0.0F ? -6649176 : -6488262, false);
            String right = "◉ " + ClientState.absorbed + "   " + ClientState.slots + "/3";
            g.drawString(mc.font, right, x + 166 - mc.font.width(right), y + 30, -4616961, false);
         }
      }
   }

   private Hud() {
   }
}
