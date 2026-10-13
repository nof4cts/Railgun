package com.jjk.mod.client;

import com.jjk.mod.JjkNet;
import com.jjk.mod.Sorcerer;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class SelectorScreen extends Screen {
   private static final Sorcerer[] JJK = new Sorcerer[]{
      Sorcerer.GOJO,
      Sorcerer.SUKUNA,
      Sorcerer.RYU,
      Sorcerer.KASHIMO,
      Sorcerer.CHOSO,
      Sorcerer.HAKARI,
      Sorcerer.YUKI,
      Sorcerer.GETO,
      Sorcerer.NAOYA,
      Sorcerer.HIGURUMA,
      Sorcerer.TEN_SHADOWS,
      Sorcerer.YUTA,
      Sorcerer.NONE
   };
   private static final Sorcerer[] AVATAR = new Sorcerer[]{Sorcerer.FIRE, Sorcerer.WATER, Sorcerer.EARTH, Sorcerer.AIR};
   private Sorcerer[] order;
   private final long opened = System.currentTimeMillis();
   private Sorcerer focus;
   private float tintR;
   private float tintG;
   private float tintB;
   private final float[] hover = new float[Math.max(JJK.length, AVATAR.length)];
   private long chosenAt = -1L;
   private Sorcerer chosen;
   private float buttonHover;

   public SelectorScreen() {
      super(Component.literal("Cursed Technique"));
      Sorcerer cur = ClientSorcerers.mine();
      this.focus = cur == Sorcerer.NONE ? Sorcerer.GOJO : cur;
      this.order = this.focus.isAvatar() ? AVATAR : JJK;
      this.tintR = r(this.focus.color);
      this.tintG = g(this.focus.color);
      this.tintB = b(this.focus.color);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private int cardsX() {
      return Math.max(10, this.width / 18);
   }

   private int cardW() {
      return Mth.clamp(this.width / 3, 130, 190);
   }

   private int cardH() {
      return Mth.clamp((this.height - 70) / ((this.order.length + 1) / 2) - 6, 22, 46);
   }

   private int cardsY() {
      return 44;
   }

   private int cols() {
      return 2;
   }

   private int[] card(int i) {
      int cw = Math.min(this.cardW(), (int)((float)this.width * 0.52F - (float)this.cardsX() - 6.0F) / 2);
      int col = i % this.cols();
      int row = i / this.cols();
      return new int[]{this.cardsX() + col * (cw + 6), this.cardsY() + row * (this.cardH() + 6), cw, this.cardH()};
   }

   private int panelX() {
      int[] c = this.card(1);
      return c[0] + c[2] + 14;
   }

   private int[] button() {
      int px = this.panelX();
      int pw = this.width - px - this.cardsX();
      int bw = Math.min(150, pw - 20);
      int bh = 20;
      return new int[]{px + (pw - bw) / 2, this.height - 34, bw, bh};
   }

   private float time() {
      return (float)(System.currentTimeMillis() - this.opened) / 1000.0F;
   }

   public void render(GuiGraphics gg, int mx, int my, float partial) {
      float t = this.time();
      float open = ease(Math.min(1.0F, t / 0.35F));
      Sorcerer show = this.hovered(mx, my) != null ? this.hovered(mx, my) : this.focus;
      this.tintR = Mth.lerp(0.12F, this.tintR, r(show.color));
      this.tintG = Mth.lerp(0.12F, this.tintG, g(show.color));
      this.tintB = Mth.lerp(0.12F, this.tintB, b(show.color));
      int tint = rgb(this.tintR, this.tintG, this.tintB);
      gg.fill(0, 0, this.width, this.height, argb(0.78F * open, 328970));
      gg.fillGradient(0, this.height / 3, this.width, this.height, 0, argb(0.35F * open, tint));
      gg.fillGradient(0, 0, this.width, 40, argb(0.6F * open, 0), 0);
      this.specks(gg, t, open, tint);
      Font f = this.font;
      int hx = this.cardsX();
      float slide = (1.0F - open) * -30.0F;
      gg.pose().pushPose();
      gg.pose().translate((float)hx + slide, 12.0F, 0.0F);
      boolean av = this.order == AVATAR;
      gg.drawString(f, av ? "四大元素" : "術式選択", 0, 0, argb(0.8F * open, tint), false);
      gg.pose().translate(0.0F, 10.0F, 0.0F);
      gg.pose().scale(1.5F, 1.5F, 1.0F);
      gg.drawString(f, av ? "CHOOSE YOUR ELEMENT" : "CHOOSE YOUR CURSED TECHNIQUE", 0, 0, argb(open, 16777215), true);
      gg.pose().popPose();
      gg.fill(hx, 37, hx + (int)((float)(this.width - hx * 2) * open), 38, argb(0.8F * open, tint));
      this.drawTabs(gg, mx, my, open, tint);
      String hint = "Tab: switch series · moves: 1 2 3 4, R = next page · keys: Options › Controls › Jujutsu Kaisen";
      float hs = Math.min(
         1.0F, ((float)(this.width - hx * 2) - (float)this.font.width("CHOOSE YOUR CURSED TECHNIQUE") * 1.5F - 10.0F) / (float)this.font.width(hint)
      );
      if (hs > 0.45F) {
         gg.pose().pushPose();
         gg.pose().translate((float)(this.width - hx) - (float)this.font.width(hint) * hs, 28.0F, 0.0F);
         gg.pose().scale(hs, hs, 1.0F);
         gg.drawString(this.font, hint, 0, 0, argb(0.45F * open, 16777215), false);
         gg.pose().popPose();
      }

      Sorcerer active = ClientSorcerers.mine();

      for (int i = 0; i < this.order.length; i++) {
         Sorcerer s = this.order[i];
         int[] c = this.card(i);
         boolean over = inside(mx, my, c);
         this.hover[i] = Mth.lerp(0.25F, this.hover[i], over ? 1.0F : 0.0F);
         float delay = Mth.clamp((t - 0.05F * (float)i) / 0.3F, 0.0F, 1.0F);
         float a = ease(delay);
         this.drawCard(gg, s, c, this.hover[i], a, s == this.focus, s == active, t);
      }

      this.drawPanel(gg, this.focus, mx, my, t, open);
      int[] sb = this.skipButton();
      boolean overSkip = inside(mx, my, sb);
      boolean skipping = JjkPrefs.skipCutscenes();
      gg.fill(sb[0], sb[1], sb[0] + sb[2], sb[1] + sb[3], argb((overSkip ? 0.55F : 0.35F) * open, skipping ? 8400960 : 2111528));
      gg.fill(sb[0], sb[1] + sb[3] - 1, sb[0] + sb[2], sb[1] + sb[3], argb(open, tint));
      String sl = skipping ? "Cutscenes: SKIPPED" : "Cutscenes: ON";
      gg.drawString(this.font, sl, sb[0] + (sb[2] - this.font.width(sl)) / 2, sb[1] + (sb[3] - 8) / 2, argb(open, 16777215), true);
      if (this.chosenAt >= 0L) {
         float k = (float)(System.currentTimeMillis() - this.chosenAt) / 450.0F;
         gg.fill(0, 0, this.width, this.height, argb(Math.max(0.0F, 0.7F - k) * 0.9F, this.chosen.color));
         if (k > 1.0F) {
            this.onClose();
         }
      }

      super.render(gg, mx, my, partial);
   }

   private void drawCard(GuiGraphics gg, Sorcerer s, int[] c, float h, float a, boolean focused, boolean active, float t) {
      int x = c[0] + (int)(h * 4.0F) + (int)((1.0F - a) * -40.0F);
      int y = c[1];
      int w = c[2];
      int hh = c[3];
      int col = s.color;
      float bright = 0.55F + 0.45F * Math.max(h, focused ? 1.0F : 0.0F);
      gg.fill(x, y, x + w, y + hh, argb(0.82F * a, 723730));
      gg.fillGradient(x, y, x + w, y + hh, argb(0.1F * a * bright, col), argb(0.28F * a * bright, col));
      gg.enableScissor(x, y, x + w, y + hh);
      gg.pose().pushPose();
      float ks = (float)hh / 11.0F;
      int kw = this.font.width(s.kanji);
      gg.pose().translate((float)(x + w) - (float)kw * ks * 0.55F - 2.0F + h * 4.0F, (float)y + (float)hh / 2.0F - 4.5F * ks, 0.0F);
      gg.pose().scale(ks, ks, 1.0F);
      gg.drawString(this.font, s.kanji, 0, 0, argb((0.16F + 0.18F * h) * a, col), false);
      gg.pose().popPose();
      gg.disableScissor();
      gg.fill(x, y, x + 3, y + hh, argb(a, col));
      int border = argb((focused ? 0.95F : 0.25F + 0.5F * h) * a, focused ? col : 16777215);
      gg.fill(x, y, x + w, y + 1, border);
      gg.fill(x, y + hh - 1, x + w, y + hh, border);
      gg.fill(x + w - 1, y, x + w, y + hh, border);
      int nameRoom = w - 12 - (active ? 10 : 0);
      gg.drawString(this.font, this.fit(s.displayName, nameRoom), x + 8, y + hh / 2 - 9, argb(a, 16777215), true);
      gg.drawString(
         this.font,
         this.fit(s.available() ? s.title : s.title + " · coming soon", w - 12),
         x + 8,
         y + hh / 2 + 2,
         argb((s.available() ? 0.9F : 0.5F) * a, col),
         false
      );
      if (active) {
         gg.fill(x + w - 10, y + 4, x + w - 4, y + 10, argb(a, col));
         gg.fill(x + w - 9, y + 5, x + w - 5, y + 9, argb(a, 16777215));
      }
   }

   private String fit(String text, int room) {
      return this.font.width(text) <= room ? text : this.font.plainSubstrByWidth(text, Math.max(0, room - this.font.width("…"))) + "…";
   }

   private void drawPanel(GuiGraphics gg, Sorcerer s, int mx, int my, float t, float open) {
      int px = this.panelX();
      int py = 44;
      int pw = this.width - px - this.cardsX();
      int ph = this.height - py - 10;
      if (pw >= 90) {
         int col = s.color;
         gg.fill(px, py, px + pw, py + ph, argb(0.72F * open, 526350));
         gg.fill(px, py, px + pw, py + 2, argb(open, col));
         gg.enableScissor(px, py, px + pw, py + ph);
         float ks = Math.min((float)ph / 9.0F * 0.9F, (float)pw / Math.max(1.0F, (float)this.font.width(s.kanji)) * 0.95F);
         float breathe = 1.0F + 0.02F * Mth.sin(t * 2.0F);
         gg.pose().pushPose();
         gg.pose().translate((float)px + (float)pw / 2.0F, (float)py + (float)ph * 0.55F, 0.0F);
         gg.pose().scale(ks * breathe, ks * breathe, 1.0F);
         gg.drawString(this.font, s.kanji, -this.font.width(s.kanji) / 2, -4, argb(0.09F * open, col), false);
         gg.pose().popPose();
         gg.disableScissor();
         gg.enableScissor(px, py, px + pw, py + ph);
         int x = px + 10;
         int y = py + 10;
         String big = s.displayName.toUpperCase();
         float ns = Mth.clamp((float)(pw - 20) / (float)Math.max(1, this.font.width(big)), 1.0F, 2.0F);
         gg.pose().pushPose();
         gg.pose().translate((float)x, (float)y, 0.0F);
         gg.pose().scale(ns, ns, 1.0F);
         gg.drawString(this.font, big, 0, 0, argb(open, 16777215), true);
         gg.pose().popPose();
         y += (int)(10.0F * ns);
         gg.drawString(this.font, this.fit(s.kanji + "  ·  " + s.title, pw - 20), x, y, argb(open, col), false);
         y += 14;
         List<FormattedCharSequence> lines = this.font.split(Component.literal(s.description), pw - 20);
         int maxLines = Math.max(2, (this.button()[1] - y - 30 - Math.min(s.moves.length, 6) * 8) / 10);

         for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            gg.drawString(this.font, lines.get(i), x, y, argb(0.85F * open, 14211296), false);
            y += 10;
         }

         y += 6;
         if (s.moves.length > 0) {
            gg.drawString(this.font, s.usesKeys() ? "MOVES" : "TECHNIQUE", x, y, argb(open, col), false);
            gg.fill(x, y + 10, x + pw - 20, y + 11, argb(0.4F * open, col));
            y += 14;
            int avail = this.button()[1] - 6 - y;
            int n = s.moves.length;
            float sc = Mth.clamp((float)avail / ((float)n * 11.0F), 0.6F, 1.0F);
            boolean two = sc < 0.72F;
            if (two) {
               sc = Mth.clamp((float)avail / ((float)((n + 1) / 2) * 11.0F), 0.6F, 0.85F);
            }

            int perCol = two ? (n + 1) / 2 : n;
            float colW = two ? (float)(pw - 20) / 2.0F / sc : (float)(pw - 20) / sc;
            gg.pose().pushPose();
            gg.pose().translate((float)x, (float)y, 0.0F);
            gg.pose().scale(sc, sc, 1.0F);

            for (int i = 0; i < n; i++) {
               int cx = (int)(i >= perCol ? colW : 0.0F);
               int cy = i % perCol * 11;
               String key = s.usesKeys() ? Keys.labelFull(i) : "•";
               int kw = Math.max(12, this.font.width(key) + 6);
               gg.fill(cx, cy - 1, cx + kw, cy + 9, argb(0.9F * open, col));
               gg.drawCenteredString(this.font, key, cx + kw / 2, cy, argb(open, 723730));
               String name = s.moves[i];
               int room = (int)colW - kw - 8;
               if (this.font.width(name) > room) {
                  name = this.font.plainSubstrByWidth(name, room - 6) + "…";
               }

               gg.drawString(this.font, name, cx + kw + 4, cy, argb(open, 15790325), false);
            }

            gg.pose().popPose();
         }

         gg.disableScissor();
         int[] b = this.button();
         boolean active = s == ClientSorcerers.mine();
         boolean over = inside(mx, my, b) && !active && s.available();
         this.buttonHover = Mth.lerp(0.3F, this.buttonHover, over ? 1.0F : 0.0F);
         int bx = b[0] - (int)(this.buttonHover * 3.0F);
         int bw = b[2] + (int)(this.buttonHover * 6.0F);
         gg.fill(bx, b[1], bx + bw, b[1] + b[3], argb(open * (!active && s.available() ? 0.75F + 0.25F * this.buttonHover : 0.35F), col));
         gg.fill(bx, b[1] + b[3] - 2, bx + bw, b[1] + b[3], argb(open * 0.6F, 0));
         String label = active
            ? "ACTIVE"
            : (!s.available() ? "COMING SOON" : (s == Sorcerer.NONE ? "RELINQUISH TECHNIQUE" : (s.isAvatar() ? "BEGIN BENDING" : "AWAKEN")));
         gg.drawCenteredString(this.font, label, bx + bw / 2, b[1] + 6, argb(open, !active && s.available() ? 723730 : 16777215));
      }
   }

   private void specks(GuiGraphics gg, float t, float open, int tint) {
      for (int i = 0; i < 70; i++) {
         float seed = (float)i * 12.9898F;
         float sx = frac(Mth.sin(seed) * 43758.55F);
         float speed = 0.04F + 0.08F * frac(Mth.sin(seed * 1.7F) * 9631.3F);
         float sy = 1.0F - frac(frac(Mth.sin(seed * 2.3F) * 2337.1F) + t * speed);
         float size = 1.0F + 2.0F * frac(Mth.sin(seed * 3.1F) * 517.7F);
         float wob = Mth.sin(t * 1.3F + (float)i) * 6.0F;
         int x = (int)(sx * (float)this.width + wob);
         int y = (int)(sy * (float)this.height);
         float a = open * (0.15F + 0.35F * sy) * (0.5F + 0.5F * Mth.sin(t * 3.0F + (float)i));
         gg.fill(x, y, x + (int)size, y + (int)size, argb(a, tint));
      }
   }

   private Sorcerer hovered(int mx, int my) {
      for (int i = 0; i < this.order.length; i++) {
         if (inside(mx, my, this.card(i))) {
            return this.order[i];
         }
      }

      return null;
   }

   private int[] skipButton() {
      int w = Math.max(this.font.width("Cutscenes: SKIPPED") + 16, 120);
      return new int[]{this.cardsX(), this.height - 24, w, 16};
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (this.chosenAt >= 0L) {
         return true;
      } else if (inside((int)mx, (int)my, this.skipButton())) {
         JjkPrefs.setSkipCutscenes(!JjkPrefs.skipCutscenes());
         this.click(1.0F);
         return true;
      } else {
         for (int i = 0; i < 2; i++) {
            if (inside((int)mx, (int)my, this.tab(i))) {
               this.setTab(i == 1);
               return true;
            }
         }

         Sorcerer h = this.hovered((int)mx, (int)my);
         if (h != null) {
            if (h == this.focus) {
               this.pick(h);
            } else {
               this.focus = h;
               this.click(1.4F);
            }

            return true;
         } else if (inside((int)mx, (int)my, this.button()) && this.focus != ClientSorcerers.mine()) {
            this.pick(this.focus);
            return true;
         } else {
            return super.mouseClicked(mx, my, button);
         }
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (Keys.MENU.matches(key, scan)) {
         this.onClose();
         return true;
      } else if (key == 258) {
         this.setTab(this.order != AVATAR);
         return true;
      } else {
         int idx = this.indexOf(this.focus);
         if (key != 262 && key != 264 && key != 263 && key != 265) {
            if (key != 257 && key != 335) {
               return super.keyPressed(key, scan, mods);
            } else {
               if (this.focus != ClientSorcerers.mine()) {
                  this.pick(this.focus);
               }

               return true;
            }
         } else {
            int d = key == 262 ? 1 : (key == 263 ? -1 : (key == 264 ? 2 : -2));
            this.focus = this.order[Math.floorMod(idx + d, this.order.length)];
            this.click(1.4F);
            return true;
         }
      }
   }

   private void pick(Sorcerer s) {
      if (s != ClientSorcerers.mine() && s.available()) {
         JjkNet.CHANNEL.sendToServer(new JjkNet.Choose(s.ordinal()));
         this.chosen = s;
         this.chosenAt = System.currentTimeMillis();
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.2F));
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F));
      }
   }

   private void click(float pitch) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.UI_BUTTON_CLICK.get(), pitch, 0.4F));
   }

   private int[] tab(int i) {
      int w = 84;
      int h = 14;
      int gap = 4;
      int x = this.width - this.cardsX() - (2 - i) * w - (1 - i) * gap;
      return new int[]{x, 8, w, h};
   }

   private void drawTabs(GuiGraphics gg, int mx, int my, float open, int tint) {
      String[] names = new String[]{"JUJUTSU KAISEN", "AVATAR"};
      int[] cols = new int[]{11832575, 16738847};

      for (int i = 0; i < 2; i++) {
         int[] t = this.tab(i);
         boolean sel = this.order == AVATAR == (i == 1);
         boolean over = inside(mx, my, t);
         gg.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], argb(open * (sel ? 0.85F : (over ? 0.5F : 0.3F)), sel ? cols[i] : 1710628));
         gg.fill(t[0], t[1] + t[3] - 1, t[0] + t[2], t[1] + t[3], argb(open, cols[i]));
         gg.drawCenteredString(this.font, names[i], t[0] + t[2] / 2, t[1] + 3, argb(open, sel ? 723730 : 16777215));
      }
   }

   private void setTab(boolean avatar) {
      Sorcerer[] want = avatar ? AVATAR : JJK;
      if (this.order != want) {
         this.order = want;
         Sorcerer cur = ClientSorcerers.mine();
         this.focus = this.indexOf(cur) >= 0 && this.order[this.indexOf(cur)] == cur ? cur : this.order[0];
         Arrays.fill(this.hover, 0.0F);
         this.click(1.2F);
      }
   }

   private int indexOf(Sorcerer s) {
      for (int i = 0; i < this.order.length; i++) {
         if (this.order[i] == s) {
            return i;
         }
      }

      return 0;
   }

   private static boolean inside(int mx, int my, int[] r) {
      return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
   }

   private static float ease(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
   }

   private static float frac(float x) {
      return x - (float)Mth.floor(x);
   }

   private static float r(int c) {
      return (float)(c >> 16 & 0xFF) / 255.0F;
   }

   private static float g(int c) {
      return (float)(c >> 8 & 0xFF) / 255.0F;
   }

   private static float b(int c) {
      return (float)(c & 0xFF) / 255.0F;
   }

   private static int rgb(float r, float g, float b) {
      return (int)(r * 255.0F) << 16 | (int)(g * 255.0F) << 8 | (int)(b * 255.0F);
   }

   static int argb(float a, int rgb) {
      return Mth.clamp((int)(a * 255.0F), 0, 255) << 24 | rgb & 16777215;
   }
}
