package com.yuji.client;

import com.yuji.net.YNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Heavenly Judgement, on the screen of whoever raised /kill against Yuji under "As God As My Witness": the world
 * goes dark, their eyes are dragged up to the sky, a red halo opens over everything, HEAVENLY JUDGEMENT is written
 * across it - and then the white. (The server kills them as it ends.)
 */
public final class YJudge {
    private static double start = -1, length;
    private static String by = "";
    private static boolean flashed, struck;

    private YJudge() {}

    public static void start(YNet.Judge m) {
        start = YFx.time();
        length = m.ticks() / 20.0;
        by = m.by();
        flashed = struck = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            YCasts.sound(mc.player.position(), 18, 3.0f, 0.5f);    // the earth rumbling
            YCasts.sound(mc.player.position(), 13, 3.0f, 0.6f);    // a bell, very low
        }
        YFx.shake(0.2f);
    }

    public static boolean running() {
        return start >= 0 && YFx.time() - start < length + 0.6;
    }

    private static double t() {
        return YFx.time() - start;
    }

    /** Their eyes are pulled up to the sky. */
    public static void angles(ViewportEvent.ComputeCameraAngles e) {
        if (!running()) return;
        double u = Mth.clamp(t() / 1.4, 0, 1);
        u = u * u * (3 - 2 * u);
        e.setPitch((float) Mth.lerp(u, e.getPitch(), -72.0));
        e.setRoll(e.getRoll() + (float) (Math.sin(t() * 1.7) * 2.0 * u));
    }

    public static void overlay(GuiGraphics g) {
        if (!running()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        double t = t();
        int w = g.guiWidth(), h = g.guiHeight();
        g.pose().pushPose();
        g.pose().translate(0, 0, 480);
        // the dark
        float dark = (float) Mth.clamp(t / 0.6, 0, 1) * 0.82f;
        g.fill(0, 0, w, h, ((int) (dark * 255) << 24) | 0x080000);
        // crimson vignette, pulsing
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 6);
        for (int i = 0; i < 12; i++) {
            int a = (int) ((40 + 25 * pulse) * (1 - i / 12.0) * Mth.clamp(t / 0.8, 0, 1));
            int c = (a << 24) | 0x8A0010;
            g.fill(0, i * 3, w, i * 3 + 3, c);
            g.fill(0, h - i * 3 - 3, w, h - i * 3, c);
            g.fill(i * 3, 0, i * 3 + 3, h, c);
            g.fill(w - i * 3 - 3, 0, w - i * 3, h, c);
        }
        // the halo opening over everything
        double ho = Mth.clamp((t - 0.4) / 1.2, 0, 1);
        if (ho > 0) {
            int cx = w / 2, cy = (int) (h * 0.36);
            double r = 18 + ho * Math.min(w, h) * 0.22;
            int segs = 90;
            for (int i = 0; i < segs; i++) {
                double a = i * Math.PI * 2 / segs + t * 0.4;
                int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r * 0.32);
                int alpha = (int) (230 * ho);
                g.fill(x - 2, y - 1, x + 2, y + 2, (alpha << 24) | 0xFF1A1A);
                g.fill(x - 5, y - 2, x + 5, y + 3, ((alpha / 5) << 24) | 0xFF0000);
            }
            // light pouring down from it
            for (int k = -3; k <= 3; k++) {
                int x0 = cx + k * (int) (r / 3.5);
                int a = (int) (40 * ho * (1 - Math.abs(k) / 4.0));
                g.fill(x0 - 3, cy, x0 + 3, h, (a << 24) | 0xFF3030);
            }
        }
        // HEAVENLY JUDGEMENT, letter by letter
        String title = "HEAVENLY JUDGEMENT";
        int shown = (int) Mth.clamp((t - 0.9) / 0.05, 0, title.length());
        if (shown > 0) {
            String s = title.substring(0, shown);
            float scale = 3.2f;
            int tw = (int) (font.width(title) * scale);
            int x = (w - tw) / 2, y = (int) (h * 0.52);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, s, 1, 1, 0xFF6A0008, false);
            g.drawString(font, s, 0, 0, 0xFFFFF4F0, false);
            g.pose().popPose();
            int line = (int) (tw * Mth.clamp((t - 1.2) / 0.6, 0, 1));
            g.fill(w / 2 - line / 2, y + (int) (10 * scale) + 4, w / 2 + line / 2, y + (int) (10 * scale) + 5, 0xFFD9213A);
        }
        double sub = Mth.clamp((t - 1.9) / 0.5, 0, 1);
        if (sub > 0) {
            String s = "you raised your hand against " + by + ", and God was his witness";
            int a = (int) (sub * 220);
            g.drawString(font, s, (w - font.width(s)) / 2, (int) (h * 0.52) + 48, (a << 24) | 0xC8B8B8, false);
        }
        // and then the white
        if (t > length - 0.5) {
            if (!flashed) {
                flashed = true;
                YFx.shake(1.0f);
                YFx.impact(null, 0, 1, false, true, new float[][]{{0.06f, 5, 1}, {0.05f, 2, 1}, {0.08f, 5, 1}, {0.3f, 3, 0.5f}});
                if (mc.player != null) YCasts.sound(mc.player.position(), 17, 4.0f, 1.0f);
            }
            float white = (float) Mth.clamp((t - (length - 0.5)) / 0.25, 0, 1);
            g.fill(0, 0, w, h, ((int) (white * 255) << 24) | 0xFFFFFF);
        }
        g.pose().popPose();
    }
}
