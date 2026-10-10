package com.hakari.idg.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The pachinko riichi: three reels slam onto the screen, two lock on the same number,
 * the third crawls... then pays out or breaks.
 */
public final class RiichiOverlay {
    private static final String[] SCEN_JP = {"通過カード リーチ", "座席争奪 リーチ", "トイレ緊急 リーチ"};
    private static final String[] SCEN_EN = {"TRANSIT CARD RIICHI", "SEAT STRUGGLE RIICHI", "POTTY EMERGENCY RIICHI"};
    private static final String[] HYPE = {"CHANCE", "HOT!!", "GOLD RUSH", "RAINBOW — JACKPOT CONFIRMED"};
    private static final int[] TIER_COL = {0xFF39FF7A, 0xFFFF2B4A, 0xFFFFC93C, 0};

    private static int casterId = -1;
    private static long start = -1;
    private static long life;
    private static int tier, scenario, number, fin;
    private static boolean win, pity, jackpotMode;

    public static void start(int caster, int tier0, int scen, int num, int fin0, boolean win0, boolean pity0) {
        casterId = caster;
        start = Vfx.now();
        life = 3600;
        tier = tier0; scenario = scen; number = num; fin = fin0; win = win0; pity = pity0;
        jackpotMode = false;
        ReelSet.of(caster).spin(num, num, fin0, 800, 1300, 3200, true);

        for (int i = 0; i < 36; i++) {
            long at = i * 85L;
            if (at < 3150) Sfx.later(at, SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f + (i % 3) * 0.1f, 0.35f);
        }
        Sfx.later(800, () -> stopHit(0));
        Sfx.later(1300, () -> {
            stopHit(1);
            ScreenFx.impact(tier >= 2 ? ScreenFx.RED : ScreenFx.INVERT, 110, null);
            Sfx.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 1f);
            Sfx.play(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 0.8f);
        });
        Sfx.later(3200, () -> {
            stopHit(2);
            if (win) {
                ScreenFx.impact(ScreenFx.GOLD, 160, null);
                ScreenFx.flash(0xFFFFE08A, 300);
                Sfx.play(SoundEvents.PLAYER_LEVELUP, 1.5f, 1f);
            } else {
                Sfx.play(SoundEvents.GLASS_BREAK, 0.7f, 1f);
                Sfx.play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 1f);
            }
        });
    }

    /** Short reel slam used at the start of the jackpot cutscene. */
    public static void jackpotReels(int caster, int num) {
        casterId = caster;
        start = Vfx.now();
        life = 1000;
        number = fin = num;
        win = true;
        tier = 3;
        jackpotMode = true;
        ReelSet.of(caster).spin(num, num, num, 250, 500, 750, false);
    }

    private static void stopHit(int reel) {
        CameraDirector.shake(0.5f, 200);
        Sfx.play(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.9f, 1f);
    }

    public static boolean active() {
        return start >= 0 && Vfx.now() - start < life;
    }

    public static void clear() {
        start = -1;
    }

    private static int tierColor(long t) {
        if (pity || tier == 3) return Draw.hsv(t / 500f, 0.8f, 1f);
        return TIER_COL[tier];
    }

    public static void render(GuiGraphics g, float partial) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // only for the gambler and the people trapped with him
        if (mc.player.getId() != casterId) {
            var s = ClientState.get(casterId);
            if (s == null || ClientState.center(s).distanceTo(mc.player.position()) > s.radius + 2) {
                if (!jackpotMode) return;
            }
        }
        long t = Vfx.now();
        long el = t - start;
        Font font = mc.font;
        float w = g.guiWidth(), h = g.guiHeight();
        float alpha = el < 200 ? el / 200f : el > life - 300 ? Math.max(0, (life - el) / 300f) : 1f;
        float slide = Ease.outBack(Math.min(1f, el / 260f));
        float cy = h * (jackpotMode ? 0.33f : 0.36f) + (1 - slide) * -60;
        int col = tierColor(t);
        ReelSet reels = ReelSet.of(casterId);

        RenderSystem.disableDepthTest();
        float rw = 46, rh = 60, gap = 54;
        float panelW = gap * 2 + rw + 26, panelH = rh + 26;
        float px = w / 2 - panelW / 2, py = cy - panelH / 2;

        // panel + glowing tier frame
        g.fill((int) px - 3, (int) py - 3, (int) (px + panelW + 3), (int) (py + panelH + 3), Draw.alpha(col, alpha * 0.9f));
        g.fill((int) px, (int) py, (int) (px + panelW), (int) (py + panelH), Draw.alpha(0xFF14000E, alpha * 0.92f));

        for (int r = 0; r < 3; r++) {
            float rx = w / 2 + (r - 1) * gap - rw / 2, ry = cy - rh / 2;
            g.fill((int) rx - 2, (int) ry - 2, (int) (rx + rw + 2), (int) (ry + rh + 2), Draw.alpha(0xFFFFD34D, alpha));
            g.fill((int) rx, (int) ry, (int) (rx + rw), (int) (ry + rh), Draw.alpha(0xFFF4EEE6, alpha));
            g.enableScissor((int) rx, (int) ry, (int) (rx + rw), (int) (ry + rh));
            double pos = reels.position(r, t);
            int base = (int) Math.floor(pos);
            double frac = pos - base;
            for (int k = -1; k <= 2; k++) {
                int n = ReelSet.numberAt(base + k);
                float y = (float) (ry + rh / 2 + (k - frac) * 34);
                boolean blur = !reels.stopped(r, t);
                int nc = ReelSet.numberColor(n);
                if (blur) {
                    ScreenFx.bigText(g, font, String.valueOf(n), rx + rw / 2, y - 6, 4.2f, Draw.alpha(nc, alpha * 0.35f), false, 1f);
                    ScreenFx.bigText(g, font, String.valueOf(n), rx + rw / 2, y + 6, 4.2f, Draw.alpha(nc, alpha * 0.35f), false, 1f);
                }
                ScreenFx.bigText(g, font, String.valueOf(n), rx + rw / 2, y, 4.2f, Draw.alpha(Draw.scaleRgb(nc, 0.85f), alpha), false, 1f);
            }
            g.disableScissor();
            if (reels.stopped(r, t) && !reels.stopped(2, t) && r < 2) {
                float pulse = 0.5f + 0.5f * Mth.sin(el / 70f);
                g.fill((int) rx, (int) ry, (int) (rx + rw), (int) (ry + 3), Draw.alpha(col, alpha * pulse));
                g.fill((int) rx, (int) (ry + rh - 3), (int) (rx + rw), (int) (ry + rh), Draw.alpha(col, alpha * pulse));
            }
        }

        if (!jackpotMode) {
            if (el > 1300) {
                float k = Math.min(1f, (el - 1300) / 160f);
                float s = Mth.lerp(Ease.outExpo(k), 9f, 3.4f);
                ScreenFx.bigText(g, font, "リーチ!", w / 2, py - 26, s, col, true, alpha);
                ScreenFx.bigText(g, font, SCEN_JP[scenario], w / 2, py + panelH + 18, 2.0f, 0xFFFFFFFF, true, alpha * k);
                ScreenFx.bigText(g, font, SCEN_EN[scenario], w / 2, py + panelH + 36, 1.1f, 0xFFFFC0E0, false, alpha * k);
                String hype = pity ? "PITY — JACKPOT CONFIRMED" : HYPE[tier];
                ScreenFx.bigText(g, font, hype, w / 2, py + panelH + 52, 1.5f, col, false, alpha * k);
            }
            if (el > 3200) {
                float k = Math.min(1f, (el - 3200) / 140f);
                if (win) {
                    ScreenFx.bigText(g, font, "大当たり!!", w / 2, cy, Mth.lerp(Ease.outExpo(k), 12f, 5f), Draw.hsv(t / 300f, 0.6f, 1f), true, alpha);
                } else {
                    g.fill((int) px, (int) py, (int) (px + panelW), (int) (py + panelH), Draw.alpha(0xFF000000, 0.55f * k * alpha));
                    ScreenFx.bigText(g, font, "ハズレ", w / 2, cy, Mth.lerp(Ease.outExpo(k), 8f, 4f), 0xFF9A9A9A, false, alpha);
                }
            }
        }
        RenderSystem.enableDepthTest();
    }

    private RiichiOverlay() {}
}
