package com.geto.csm.client;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.SelectPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class SelectorScreen extends Screen {
   private int hovered = -1;
   private final long opened = Vfx.now();

   public SelectorScreen() {
      super(Component.literal("Cursed Spirits"));
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static void v(BufferBuilder b, Matrix4f m, float x, float y, int c) {
      b.vertex(m, x, y, 0.0F).color(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, c >>> 24 & 0xFF).endVertex();
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      CurseKind[] kinds = CurseKind.values();
      int n = kinds.length;
      float cx = (float)this.width / 2.0F;
      float cy = (float)this.height / 2.0F;
      float open = Ease.outBack(Math.min(1.0F, (float)(Vfx.now() - this.opened) / 220.0F));
      float R = (float)Math.min(this.width, this.height) * 0.4F * open;
      float r0 = R * 0.38F;
      g.fill(0, 0, this.width, this.height, -1727725560);
      double ang = Math.atan2((double)((float)my - cy), (double)((float)mx - cx));
      double dist = Math.hypot((double)((float)mx - cx), (double)((float)my - cy));
      double step = (Math.PI * 2) / (double)n;
      this.hovered = dist > (double)r0 * 0.5 ? Math.floorMod((int)Math.floor((ang + (Math.PI / 2) + step / 2.0) / step), n) : -1;
      Matrix4f m = g.pose().last().pose();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableDepthTest();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      BufferBuilder b = Tesselator.getInstance().getBuilder();
      b.begin(Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

      for (int i = 0; i < n; i++) {
         double a0 = (-Math.PI / 2) + (double)i * step - step / 2.0 + 0.02;
         double a1 = a0 + step - 0.04;
         boolean hov = i == this.hovered;
         boolean sel = i == ClientState.selected;
         int inner = hov ? Draw.alpha(kinds[i].color, 0.55F) : -1072428516;
         int outer = hov ? Draw.alpha(kinds[i].color, 0.85F) : (sel ? Draw.alpha(kinds[i].color, 0.45F) : -803336152);
         float rr = hov ? R * 1.06F : R;

         for (int k = 0; k < 8; k++) {
            double s0 = a0 + (a1 - a0) * (double)k / 8.0;
            double s1 = a0 + (a1 - a0) * (double)(k + 1) / 8.0;
            float ix0 = cx + (float)Math.cos(s0) * r0;
            float iy0 = cy + (float)Math.sin(s0) * r0;
            float ix1 = cx + (float)Math.cos(s1) * r0;
            float iy1 = cy + (float)Math.sin(s1) * r0;
            float ox0 = cx + (float)Math.cos(s0) * rr;
            float oy0 = cy + (float)Math.sin(s0) * rr;
            float ox1 = cx + (float)Math.cos(s1) * rr;
            float oy1 = cy + (float)Math.sin(s1) * rr;
            v(b, m, ix0, iy0, inner);
            v(b, m, ox0, oy0, outer);
            v(b, m, ox1, oy1, outer);
            v(b, m, ix0, iy0, inner);
            v(b, m, ox1, oy1, outer);
            v(b, m, ix1, iy1, inner);
         }
      }

      BufferUploader.drawWithShader(b.end());

      for (int i = 0; i < n; i++) {
         double mid = (-Math.PI / 2) + (double)i * step;
         float lr = (r0 + R) / 2.0F;
         float lx = cx + (float)Math.cos(mid) * lr;
         float ly = cy + (float)Math.sin(mid) * lr;
         CurseKind k = kinds[i];
         boolean hov = i == this.hovered;
         ScreenFx.bigText(g, this.font, k.jp, lx, ly - 9.0F, hov ? 2.0F : 1.6F, hov ? -1 : k.color, hov, 1.0F);
         ScreenFx.bigText(g, this.font, k.title, lx, ly + 8.0F, 0.85F, -1515280, false, 1.0F);
         float cd = ClientState.cooldownSeconds(i);
         if (cd > 0.0F) {
            ScreenFx.bigText(g, this.font, String.format("%.0fs", cd), lx, ly + 18.0F, 0.8F, -6649176, false, 1.0F);
         }
      }

      CurseKind focus = this.hovered >= 0 ? kinds[this.hovered] : CurseKind.byId(ClientState.selected);
      ScreenFx.bigText(g, this.font, focus.jp, cx, cy - 10.0F, 2.4F, focus.color, true, 1.0F);
      ScreenFx.bigText(g, this.font, focus.grade, cx, cy + 10.0F, 0.8F, -1, false, 1.0F);
      ScreenFx.bigText(g, this.font, "◉ " + ClientState.absorbed + " stored", cx, cy + 22.0F, 0.8F, -4616961, false, 1.0F);
      RenderSystem.enableDepthTest();
      super.render(g, mx, my, partial);
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (this.hovered >= 0) {
         ClientState.selected = this.hovered;
         CsmNetwork.sendToServer(new SelectPacket(this.hovered));
         Sfx.play(SoundEvents.SCULK_CLICKING, 1.2F, 1.0F);
         this.onClose();
         return true;
      } else {
         return super.mouseClicked(mx, my, button);
      }
   }

   public boolean mouseScrolled(double mx, double my, double delta) {
      int next = Mth.positiveModulo(ClientState.selected + (delta < 0.0 ? 1 : -1), CurseKind.values().length);
      ClientState.selected = next;
      CsmNetwork.sendToServer(new SelectPacket(next));
      return true;
   }
}
