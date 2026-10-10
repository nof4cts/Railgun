package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Persistent HUD: domain status, spin pips, pity counter, the 4:11 jackpot clock, rhythm beat. */
public final class Hud {

    public static void render(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || CameraDirector.active()) return;
        Font font = mc.font;
        float w = g.guiWidth(), h = g.guiHeight();
        long t = Vfx.now();

        StatePacket self = ClientState.get(mc.player.getId());
        StatePacket dom = ClientState.domainContaining(mc.player.position());

        if (dom != null && dom.domainActive) {
            boolean mine = self != null && dom == self;
            int y = 6;
            g.fill((int) (w / 2 - 110), y - 2, (int) (w / 2 + 110), y + (mine ? 40 : 22), 0x9A14000E);
            g.fill((int) (w / 2 - 110), y - 2, (int) (w / 2 + 110), y - 1, 0xFFFF4FB8);
            ScreenFx.bigText(g, font, "坐殺博徒 · IDLE DEATH GAMBLE", w / 2, y + 5, 1.0f, 0xFFFF8AD8, false, 1f);
            float secs = Math.max(0, (dom.domainTicks - ClientState.ticksSince(dom)) / 20f);
            ScreenFx.bigText(g, font, String.format("%d:%02d", (int) secs / 60, (int) secs % 60), w / 2, y + 15, 0.9f, 0xFFFFFFFF, false, 1f);
            if (mine) {
                // visual pips
                for (int i = 0; i < 2; i++) {
                    int x = (int) (w / 2 - 60 + i * 14);
                    int c = i < self.visuals ? 0xFFFFD34D : 0xFF4A3040;
                    g.fill(x, y + 25, x + 10, y + 35, c);
                }
                g.drawString(font, "VISUALS", (int) (w / 2 - 30), y + 26, 0xFFFFFFFF, false);
                String miss = self.fails >= 3 ? "PITY READY" : "MISSES " + self.fails + "/3";
                int mc2 = self.fails >= 3 ? Draw.hsv(t / 400f, 0.6f, 1f) : 0xFFBBBBBB;
                g.drawString(font, miss, (int) (w / 2 + 22), y + 26, mc2, false);
                if (self.boosted) ScreenFx.bigText(g, font, "確変 PROBABILITY UP", w / 2, y + 48, 1f, Draw.hsv(t / 600f, 0.7f, 1f), false, 1f);
            }
        }

        if (self != null && self.jackpotTicks > 0) {
            float left = ClientState.jackpotSecondsLeft(self);
            String clock = String.format("%d:%02d", (int) left / 60, (int) left % 60);
            float pulse = 1f + 0.05f * Mth.sin(t / 120f);
            int gold = left < 10 ? (((t / 250) % 2 == 0) ? 0xFFFF3A3A : 0xFFFFD34D) : 0xFFFFD34D;
            ScreenFx.bigText(g, font, clock, w / 2, 26, 3.2f * pulse, gold, true, 1f);
            ScreenFx.bigText(g, font, "JACKPOT  ·  ∞ CURSED ENERGY", w / 2, 46, 0.9f, 0xFFFFF0B0, false, 1f);
            float frac = self.jackpotMax > 0 ? Mth.clamp(left * 20f / self.jackpotMax, 0, 1) : 0;
            int bw = 160;
            g.fill((int) (w / 2 - bw / 2f), 54, (int) (w / 2 + bw / 2f), 57, 0x80000000);
            g.fill((int) (w / 2 - bw / 2f), 54, (int) (w / 2 - bw / 2f + bw * frac), 57, gold);
        }

        if (self != null && self.rhythmTicks > 0) {
            float beat = (t % 500) / 500f;
            int r = (int) (6 + 10 * Ease.outExpo(beat));
            int cx = (int) (w / 2), cy = (int) (h - 58);
            int col = Draw.alpha(0xFF5CF2FF, 1 - beat);
            g.fill(cx - r, cy - 1, cx + r, cy + 1, col);
            g.fill(cx - 1, cy - r, cx + 1, cy + r, col);
            g.drawString(font, "RHYTHM", cx - font.width("RHYTHM") / 2, cy + 12, 0xFF5CF2FF, false);
        }
    }

    private Hud() {}
}
