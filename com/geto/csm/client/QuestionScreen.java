package com.geto.csm.client;

import com.geto.csm.network.AnswerPacket;
import com.geto.csm.network.CsmNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class QuestionScreen extends Screen {
   public final int entityId;
   private final int stage;
   private final long start = Vfx.now();
   private final long timeoutMs;
   private boolean sent;

   public QuestionScreen(int entityId, int stage, int timeoutTicks) {
      super(Component.literal("Am I pretty?"));
      this.entityId = entityId;
      this.stage = stage;
      this.timeoutMs = (long)timeoutTicks * 50L;
   }

   public boolean isPauseScreen() {
      return false;
   }

   protected void init() {
      int bw = 120;
      int gap = 10;
      int y = (int)((float)this.height * 0.72F);
      int x0 = this.width / 2 - (bw * 3 + gap * 2) / 2;
      this.addRenderableWidget(Button.builder(Component.literal("Yes, you're pretty"), b -> this.answer(0)).bounds(x0, y, bw, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("No"), b -> this.answer(1)).bounds(x0 + bw + gap, y, bw, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("You're... average"), b -> this.answer(2)).bounds(x0 + (bw + gap) * 2, y, bw, 20).build());
   }

   private void answer(int a) {
      if (!this.sent) {
         this.sent = true;
         CsmNetwork.sendToServer(new AnswerPacket(this.entityId, a));
      }

      this.onClose();
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      long el = Vfx.now() - this.start;
      g.fill(0, 0, this.width, this.height, -1340080122);
      float jit = el / 60L % 9L == 0L ? 2.0F : 0.0F;
      String jp = this.stage == 2 ? "これでも…？" : "私、きれい？";
      String en = this.stage == 2 ? "...EVEN LIKE THIS?" : "AM I PRETTY?";
      float s = el < 220L ? Mth.lerp(Ease.outExpo((float)el / 220.0F), 12.0F, 5.0F) : 5.0F;
      ScreenFx.bigText(g, this.font, jp, (float)this.width / 2.0F + jit, (float)this.height * 0.32F, s, -5908, true, 1.0F);
      ScreenFx.bigText(
         g, this.font, en, (float)this.width / 2.0F, (float)this.height * 0.32F + 40.0F, 1.8F, -2086852, false, Math.min(1.0F, (float)el / 400.0F)
      );
      ScreenFx.bigText(
         g,
         this.font,
         "Binding vow: no one can strike until you answer.",
         (float)this.width / 2.0F,
         (float)this.height * 0.32F + 60.0F,
         0.9F,
         -3624776,
         false,
         1.0F
      );
      float left = Math.max(0.0F, 1.0F - (float)el / (float)this.timeoutMs);
      int bw = 240;
      g.fill(this.width / 2 - bw / 2, (int)((float)this.height * 0.66F), this.width / 2 + bw / 2, (int)((float)this.height * 0.66F) + 3, 1610612736);
      g.fill(
         this.width / 2 - bw / 2,
         (int)((float)this.height * 0.66F),
         (int)((float)(this.width / 2 - bw / 2) + (float)bw * left),
         (int)((float)this.height * 0.66F) + 3,
         -2086852
      );
      super.render(g, mx, my, partial);
      if (left <= 0.0F) {
         this.onClose();
      }
   }
}
