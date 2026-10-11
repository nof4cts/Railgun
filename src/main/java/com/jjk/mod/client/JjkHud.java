package com.jjk.mod.client;

import com.choso.mod.client.CFx;
import com.geto.csm.entity.DragonEntity;
import com.hakari.idg.client.CameraDirector;
import com.jjk.mod.Dispatch;
import com.jjk.mod.JjkNet;
import com.jjk.mod.Sorcerer;
import com.kashimo.mod.client.KFx;
import com.ryu.mod.fx.client.RyuCine;
import com.shrine.client.cinematic.Cutscene;
import com.yuki.mod.client.YFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class JjkHud {
   private JjkHud() {
   }

   static boolean cutscene() {
      return KFx.cutsceneActive()
         || CFx.cutsceneActive()
         || Cutscene.active()
         || CameraDirector.active()
         || com.geto.csm.client.CameraDirector.active()
         || YFx.cutsceneActive()
         || RyuCine.active()
         || com.naoya.ps.client.cine.Director.active();
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.options.hideGui && !(mc.screen instanceof SelectorScreen) && !cutscene()) {
         Sorcerer s = ClientSorcerers.mine();
         GuiGraphics g = e.getGuiGraphics();
         int w = g.guiWidth();
         String name = s == Sorcerer.NONE ? "No technique  [" + Keys.MENU.getTranslatedKeyMessage().getString().toUpperCase() + "]" : s.displayName;
         int bw = mc.font.width(name) + 12;
         g.fill(w - bw - 4, 4, w - 4, 16, -1879048192);
         g.fill(w - bw - 4, 4, w - bw - 2, 16, 0xFF000000 | s.color);
         g.drawString(mc.font, name, w - bw + 3, 6, 0xFF000000 | s.color, false);
         if (s == Sorcerer.GOJO || s == Sorcerer.RYU || s == Sorcerer.HAKARI || s == Sorcerer.SUKUNA) {
            int ce = JjkNet.EnergySync.clientValue;
            int barW = 90;
            int bx = w - barW - 4;
            int by = 19;
            g.fill(bx - 1, by - 1, bx + barW + 1, by + 6, -1879048192);
            g.fill(bx, by, bx + barW * Math.min(ce, 1000) / 1000, by + 5, 0xFF000000 | s.color);
            String ceText = "CE " + ce + " / 1000";
            g.drawString(mc.font, ceText, bx - mc.font.width(ceText) - 4, by - 1, -2039576, true);
         }

         if (mc.player.getVehicle() instanceof DragonEntity dragon) {
            String[] m = dragon.riderMoves();
            int y = g.guiHeight() / 2 - (m.length + 1) * 11 / 2;
            int col = 0xFF000000 | dragon.kind().color;
            g.drawString(mc.font, dragon.kind().title.toUpperCase(), 6, y, col, true);
            y += 12;

            for (int i = 0; i < m.length; i++) {
               String line = "[" + Keys.label(i) + "] " + m[i] + (i == 0 ? "  (or right-click)" : "");
               g.drawString(mc.font, line, 6, y, -986891, true);
               g.fill(6, y + 9, 6 + mc.font.width(line), y + 10, col & -1862270977);
               y += 12;
            }
         } else if (s == Sorcerer.GOJO || s == Sorcerer.RYU || s == Sorcerer.HAKARI) {
            int y = g.guiHeight() / 2 - s.moves.length * 11 / 2;

            for (int i = 0; i < s.moves.length; i++) {
               Item item = Dispatch.cooldownItem(s, i);
               float cd = item == null ? 0.0F : mc.player.getCooldowns().getCooldownPercent(item, e.getPartialTick());
               String line = "[" + Keys.label(i) + "] " + s.moves[i];
               int col = cd > 0.0F ? -8749176 : -986891;
               g.drawString(mc.font, line, 6, y, col, true);
               int lw = mc.font.width(line);
               g.fill(6, y + 9, 6 + lw, y + 10, 1342177280);
               g.fill(6, y + 9, 6 + (int)((float)lw * (1.0F - cd)), y + 10, 0xFF000000 | s.color);
               y += 12;
            }
         }
      }
   }
}
