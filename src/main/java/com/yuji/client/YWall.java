package com.yuji.client;

import com.yuji.net.YNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * 4th Wall Breaker, on every screen on the server: slashes tear across the view one after another, each one a bright
 * cut with the picture shoved apart along it, until the whole screen is cracked and goes black with the title. It is
 * the last thing anyone sees before the server goes down (the server handles the actual shutdown).
 */
public final class YWall {
    private static double start = -1, length;
    private static String by = "";
    private static long seed;
    private static int lastSlash = -1;

    private YWall() {}

    public static void start(YNet.Wall m) {
        start = YFx.time();
        length = m.ticks() / 20.0;
        by = m.by();
        seed = System.nanoTime();
        lastSlash = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) YCasts.sound(mc.player.position(), 10, 4.0f, 0.7f);   // the first cut
        YFx.shake(0.4f);
    }

    public static boolean running() {
        return start >= 0 && YFx.time() - start < length + 0.4;
    }

    private static double t() {
        return YFx.time() - start;
    }

    /** The eight cuts and when each one lands (seconds). */
    private static final double[] AT = {0.15, 0.45, 0.70, 1.0, 1.25, 1.5, 1.75, 2.0};

    public static void overlay(GuiGraphics g) {
        if (!running()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        double t = t();
        int w = g.guiWidth(), h = g.guiHeight();
        RandomSource r = RandomSource.create(seed);
        g.pose().pushPose();
        g.pose().translate(0, 0, 500);

        int landed = 0;
        for (double a : AT) if (t >= a) landed++;
        if (landed != lastSlash) {
            lastSlash = landed;
            YFx.shake(0.35f);
            if (mc.player != null && landed > 0) YCasts.sound(mc.player.position(), 8, 3.0f, 0.8f + 0.1f * landed);
        }

        // each cut: a bright diagonal line, the two sides of it shoved apart, darkness bleeding from the gap
        for (int i = 0; i < AT.length; i++) {
            if (t < AT[i]) continue;
            double age = t - AT[i];
            float ang = (r.nextFloat() - 0.5f) * 2.4f + (i % 2 == 0 ? 0.6f : -0.6f);
            float cx = w * (0.2f + 0.6f * r.nextFloat());
            float cy = h * (0.2f + 0.6f * r.nextFloat());
            float dx = Mth.cos(ang), dy = Mth.sin(ang);
            float nx = -dy, ny = dx;
            float open = (float) Math.min(1.0, age / 0.25) * (8 + 14 * r.nextFloat());
            float grow = (float) Math.min(1.0, age / 0.12);
            float len = (float) Math.hypot(w, h);
            // the dark gap
            for (float o = -open; o <= open; o += 1f) {
                int a1 = (int) (190 * (1 - Math.abs(o) / open) * grow);
                line(g, cx + nx * o - dx * len, cy + ny * o - dy * len, cx + nx * o + dx * len, cy + ny * o + dy * len, 2f, (a1 << 24));
            }
            // the bright edge of the cut
            float flash = (float) Math.max(0, 1 - age / 0.18);
            int edge = ((int) (255 * (0.5f + 0.5f * flash)) << 24) | 0xFFF0F0;
            line(g, cx - dx * len, cy - dy * len, cx + dx * len, cy + dy * len, 2f + 3f * flash, edge);
            int red = ((int) (150 * grow) << 24) | 0xD9213A;
            line(g, cx - dx * len + nx * (open + 2), cy - dy * len + ny * (open + 2), cx + dx * len + nx * (open + 2), cy + dy * len + ny * (open + 2), 1.5f, red);
            line(g, cx - dx * len - nx * (open + 2), cy - dy * len - ny * (open + 2), cx + dx * len - nx * (open + 2), cy + dy * len - ny * (open + 2), 1.5f, red);
        }

        // the screen darkening as it falls apart, then black
        float dark = (float) Mth.clamp((t - 1.8) / 0.9, 0, 1);
        if (dark > 0) g.fill(0, 0, w, h, ((int) (dark * 255) << 24) | 0x000000);

        // the title, once the screen is mostly gone
        double tt = Mth.clamp((t - 2.2) / 0.3, 0, 1);
        if (tt > 0) {
            String title = "4TH WALL BREAKER";
            float scale = 3.4f;
            int tw = (int) (font.width(title) * scale);
            g.pose().pushPose();
            g.pose().translate((w - tw) / 2.0, h * 0.44, 0);
            g.pose().scale(scale, scale, 1);
            int a = (int) (tt * 255);
            g.drawString(font, title, 1, 1, (a << 24) | 0x6A0008, false);
            g.drawString(font, title, 0, 0, (a << 24) | 0xFFF4F0, false);
            g.pose().popPose();
            String sub = by + " broke the 4th wall";
            g.drawString(font, sub, (w - font.width(sub)) / 2, (int) (h * 0.44) + 44, ((int) (tt * 200) << 24) | 0xC8B8B8, false);
            if (t > length - 0.4) {
                String bye = "the server is going down…";
                g.drawString(font, bye, (w - font.width(bye)) / 2, (int) (h * 0.44) + 60, 0xFF808080, false);
            }
        }
        g.pose().popPose();
    }

    /** A thick line, any angle, drawn as a quad of filled rows. */
    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 1e-3) return;
        int steps = (int) len;
        float nx = -dy / len, ny = dx / len;
        float hw = width * 0.5f;
        for (int i = 0; i <= steps; i += 2) {
            float px = x0 + dx * i / steps, py = y0 + dy * i / steps;
            g.fill((int) (px - nx * hw), (int) (py - ny * hw), (int) (px + nx * hw) + 1, (int) (py + ny * hw) + 1, color);
        }
    }
}
