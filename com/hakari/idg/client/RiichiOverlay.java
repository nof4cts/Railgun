package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class RiichiOverlay {
   private static final String[] SCEN_JP = new String[]{"通過カード リーチ", "座席争奪 リーチ", "トイレ緊急 リーチ"};
   private static final String[] SCEN_EN = new String[]{"TRANSIT CARD RIICHI", "SEAT STRUGGLE RIICHI", "POTTY EMERGENCY RIICHI"};
   private static final String[] HYPE = new String[]{"CHANCE", "HOT!!", "GOLD RUSH", "RAINBOW — JACKPOT CONFIRMED"};
   private static final int[] TIER_COL = new int[]{-12976262, -54454, -14020, 0};
   private static int casterId = -1;
   private static long start = -1L;
   private static long life;
   private static int tier;
   private static int scenario;
   private static int number;
   private static int fin;
   private static boolean win;
   private static boolean pity;
   private static boolean jackpotMode;

   public static void start(int caster, int tier0, int scen, int num, int fin0, boolean win0, boolean pity0) {
      casterId = caster;
      start = Vfx.now();
      life = 3600L;
      tier = tier0;
      scenario = scen;
      number = num;
      fin = fin0;
      win = win0;
      pity = pity0;
      jackpotMode = false;
      ReelSet.of(caster).spin(num, num, fin0, 800L, 1300L, 3200L, true);

      for (int i = 0; i < 36; i++) {
         long at = (long)i * 85L;
         if (at < 3150L) {
            Sfx.later(at, (SoundEvent)SoundEvents.NOTE_BLOCK_HAT.value(), 1.2F + (float)(i % 3) * 0.1F, 0.35F);
         }
      }

      Sfx.later(800L, () -> stopHit(0));
      Sfx.later(1300L, () -> {
         stopHit(1);
         ScreenFx.impact(tier >= 2 ? 2 : 0, 110L, (Vec3)null);
         Sfx.play((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 1.0F);
         Sfx.play((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 2.0F, 0.8F);
      });
      Sfx.later(3200L, () -> {
         stopHit(2);
         if (win) {
            ScreenFx.impact(3, 160L, (Vec3)null);
            ScreenFx.flash(-8054, 300L);
            Sfx.play(SoundEvents.PLAYER_LEVELUP, 1.5F, 1.0F);
         } else {
            Sfx.play(SoundEvents.GLASS_BREAK, 0.7F, 1.0F);
            Sfx.play((SoundEvent)SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 1.0F);
         }
      });
   }

   public static void jackpotReels(int caster, int num) {
      casterId = caster;
      start = Vfx.now();
      life = 1000L;
      fin = num;
      number = num;
      win = true;
      tier = 3;
      jackpotMode = true;
      ReelSet.of(caster).spin(num, num, num, 250L, 500L, 750L, false);
   }

   private static void stopHit(int reel) {
      CameraDirector.shake(0.5F, 200L);
      Sfx.play((SoundEvent)SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.9F, 1.0F);
   }

   public static boolean active() {
      return start >= 0L && Vfx.now() - start < life;
   }

   public static void clear() {
      start = -1L;
   }

   private static int tierColor(long t) {
      return !pity && tier != 3 ? TIER_COL[tier] : Draw.hsv((float)t / 500.0F, 0.8F, 1.0F);
   }

   public static void render(GuiGraphics g, float partial) {
      if (active()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            if (mc.player.getId() != casterId) {
               StatePacket s = ClientState.get(casterId);
               if ((s == null || ClientState.center(s).distanceTo(mc.player.position()) > (double)(s.radius + 2.0F)) && !jackpotMode) {
                  return;
               }
            }

            long t = Vfx.now();
            long el = t - start;
            Font font = mc.font;
            float w = (float)g.guiWidth();
            float h = (float)g.guiHeight();
            float alpha = el < 200L ? (float)el / 200.0F : (el > life - 300L ? Math.max(0.0F, (float)(life - el) / 300.0F) : 1.0F);
            float slide = Ease.outBack(Math.min(1.0F, (float)el / 260.0F));
            float cy = h * (jackpotMode ? 0.33F : 0.36F) + (1.0F - slide) * -60.0F;
            int col = tierColor(t);
            ReelSet reels = ReelSet.of(casterId);
            RenderSystem.disableDepthTest();
            float rw = 46.0F;
            float rh = 60.0F;
            float gap = 54.0F;
            float panelW = gap * 2.0F + rw + 26.0F;
            float panelH = rh + 26.0F;
            float px = w / 2.0F - panelW / 2.0F;
            float py = cy - panelH / 2.0F;
            g.fill((int)px - 3, (int)py - 3, (int)(px + panelW + 3.0F), (int)(py + panelH + 3.0F), Draw.alpha(col, alpha * 0.9F));
            g.fill((int)px, (int)py, (int)(px + panelW), (int)(py + panelH), Draw.alpha(-15466482, alpha * 0.92F));

            for (int r = 0; r < 3; r++) {
               float rx = w / 2.0F + (float)(r - 1) * gap - rw / 2.0F;
               float ry = cy - rh / 2.0F;
               g.fill((int)rx - 2, (int)ry - 2, (int)(rx + rw + 2.0F), (int)(ry + rh + 2.0F), Draw.alpha(-11443, alpha));
               g.fill((int)rx, (int)ry, (int)(rx + rw), (int)(ry + rh), Draw.alpha(-725274, alpha));
               g.enableScissor((int)rx, (int)ry, (int)(rx + rw), (int)(ry + rh));
               double pos = reels.position(r, t);
               int base = (int)Math.floor(pos);
               double frac = pos - (double)base;

               for (int k = -1; k <= 2; k++) {
                  int n = ReelSet.numberAt((double)(base + k));
                  float y = (float)((double)(ry + rh / 2.0F) + ((double)k - frac) * 34.0);
                  boolean blur = !reels.stopped(r, t);
                  int nc = ReelSet.numberColor(n);
                  if (blur) {
                     ScreenFx.bigText(g, font, String.valueOf(n), rx + rw / 2.0F, y - 6.0F, 4.2F, Draw.alpha(nc, alpha * 0.35F), false, 1.0F);
                     ScreenFx.bigText(g, font, String.valueOf(n), rx + rw / 2.0F, y + 6.0F, 4.2F, Draw.alpha(nc, alpha * 0.35F), false, 1.0F);
                  }

                  ScreenFx.bigText(g, font, String.valueOf(n), rx + rw / 2.0F, y, 4.2F, Draw.alpha(Draw.scaleRgb(nc, 0.85F), alpha), false, 1.0F);
               }

               g.disableScissor();
               if (reels.stopped(r, t) && !reels.stopped(2, t) && r < 2) {
                  float pulse = 0.5F + 0.5F * Mth.sin((float)el / 70.0F);
                  g.fill((int)rx, (int)ry, (int)(rx + rw), (int)(ry + 3.0F), Draw.alpha(col, alpha * pulse));
                  g.fill((int)rx, (int)(ry + rh - 3.0F), (int)(rx + rw), (int)(ry + rh), Draw.alpha(col, alpha * pulse));
               }
            }

            if (!jackpotMode) {
               if (el > 1300L) {
                  float k = Math.min(1.0F, (float)(el - 1300L) / 160.0F);
                  float s = Mth.lerp(Ease.outExpo(k), 9.0F, 3.4F);
                  ScreenFx.bigText(g, font, "リーチ!", w / 2.0F, py - 26.0F, s, col, true, alpha);
                  ScreenFx.bigText(g, font, SCEN_JP[scenario], w / 2.0F, py + panelH + 18.0F, 2.0F, -1, true, alpha * k);
                  ScreenFx.bigText(g, font, SCEN_EN[scenario], w / 2.0F, py + panelH + 36.0F, 1.1F, -16160, false, alpha * k);
                  String hype = pity ? "PITY — JACKPOT CONFIRMED" : HYPE[tier];
                  ScreenFx.bigText(g, font, hype, w / 2.0F, py + panelH + 52.0F, 1.5F, col, false, alpha * k);
               }

               if (el > 3200L) {
                  float k = Math.min(1.0F, (float)(el - 3200L) / 140.0F);
                  if (win) {
                     ScreenFx.bigText(
                        g, font, "大当たり!!", w / 2.0F, cy, Mth.lerp(Ease.outExpo(k), 12.0F, 5.0F), Draw.hsv((float)t / 300.0F, 0.6F, 1.0F), true, alpha
                     );
                  } else {
                     g.fill((int)px, (int)py, (int)(px + panelW), (int)(py + panelH), Draw.alpha(-16777216, 0.55F * k * alpha));
                     ScreenFx.bigText(g, font, "ハズレ", w / 2.0F, cy, Mth.lerp(Ease.outExpo(k), 8.0F, 4.0F), -6645094, false, alpha);
                  }
               }
            }

            RenderSystem.enableDepthTest();
         }
      }
   }

   private RiichiOverlay() {
   }
}
