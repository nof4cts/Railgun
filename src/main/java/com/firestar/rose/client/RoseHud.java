package com.firestar.rose.client;

import com.firestar.rose.client.cam.Lens;
import com.firestar.rose.common.RBeats;
import com.firestar.rose.common.RMove;
import com.firestar.styles.Style;
import com.firestar.styles.client.ClientStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Requiem Rose on screen: the kit down the right edge (each technique, its key, its cooldown draining in rose), the
 * title card that sweeps in when a great technique begins, and the reaper's words at the foot of the frame.
 */
public final class RoseHud implements IGuiOverlay {
    public static final RoseHud INSTANCE = new RoseHud();
    private static long[] readyAt = new long[RMove.values().length];
    private static int[] total = new int[RMove.values().length];
    private static long hintUntil;

    private RoseHud() {}

    public static void cooldowns(long[] ready, int[] totals) {
        if (ready.length == readyAt.length) {
            readyAt = ready;
        }
        if (totals.length == total.length) {
            total = totals;
        }
    }

    public static void hint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            hintUntil = mc.level.getGameTime() + 50;
        }
    }

    private static int a(int alpha, int rgb) {
        return Mth.clamp(alpha, 0, 255) << 24 | (rgb & 0xFFFFFF);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || Lens.active() || !ClientStyles.mine(Style.ROSE)) {
            return;
        }
        Font font = mc.font;
        long now = mc.level.getGameTime();
        boolean up = RoseForms.ascended(mc.player);
        RoseForms.Info info = RoseForms.get(mc.player);
        float aura = info == null ? 0 : Mth.lerp(partial, info.auraO, info.aura);
        int x = width - 136;
        int y = height / 2 - RMove.KIT.length * 8 - 22;

        if (hintUntil > now && !up) {
            String s = "Ascend first  [" + RoseKeys.name(RoseKeys.ASCEND) + "]";
            int al = (int) (255 * Mth.clamp((hintUntil - now) / 10.0F, 0, 1));
            g.drawString(font, s, width / 2 - font.width(s) / 2, height / 2 + 24, a(al, 0xFFB0D4), true);
        }

        // the header: the form, and the aura's strength as a hairline of rose
        g.fill(x - 4, y - 4, x + 132, y + 15, 0x70000000);
        g.fill(x - 4, y - 4, x - 2, y + 15, up ? 0xFFFF5FA2 : 0xFF6A5060);
        String head = up ? "冥薔薇  ASCENDED" : "冥薔薇  MORTAL";
        g.drawString(font, head, x + 2, y, up ? 0xFFFFD0E4 : 0xFFB8A8B0, true);
        g.fill(x + 2, y + 11, x + 128, y + 12, 0x50FFFFFF);
        int w = (int) (126 * aura);
        for (int i = 0; i < w; i++) {
            float k = i / 126F;
            int c = lerp(0xB3124F, 0xFFC8E0, k);
            g.fill(x + 2 + i, y + 11, x + 3 + i, y + 12, a(255, c));
        }
        y += 19;
        String sub = up ? "[SHIFT+" + RoseKeys.name(RoseKeys.ASCEND) + "] release" : "[" + RoseKeys.name(RoseKeys.ASCEND) + "] ascend";
        g.drawString(font, sub, x + 2, y, 0xA0FFFFFF, true);
        y += 13;
        int dim = up ? 255 : 120;
        for (RMove m : RMove.KIT) {
            long left = readyAt[m.ordinal()] - now;
            int tot = Math.max(1, total[m.ordinal()]);
            boolean ready = left <= 0;
            g.fill(x - 4, y - 3, x + 132, y + 11, a((int) (dim * 0.4F), 0x14000A));
            // the key, in a small rose-gold frame
            String k = RoseKeys.name(RoseKeys.of(m));
            g.fill(x - 2, y - 2, x + 11, y + 10, a(dim, ready && up ? 0xE89A90 : 0x5A4048));
            g.fill(x - 1, y - 1, x + 10, y + 9, a(dim, 0x16060E));
            g.drawString(font, k, x + 5 - font.width(k) / 2, y, a(dim, ready && up ? 0xFFD8E8 : 0x9A8A92), false);
            g.drawString(font, m.title, x + 15, y, a(dim, ready ? (m == RMove.LAST_BLOOM ? 0xFF8FC0 : 0xF4E4EC) : 0x8A7E84), true);
            if (!ready) {
                float frac = Mth.clamp(left / (float) tot, 0, 1);
                int bw = (int) (112 * (1 - frac));
                for (int i = 0; i < bw; i++) {
                    g.fill(x + 15 + i, y + 9, x + 16 + i, y + 10, a(dim, lerp(0x6A0A30, 0xFF5FA2, i / 112F)));
                }
                String s = String.format("%.0f", Math.ceil(left / 20.0));
                g.drawString(font, s, x + 128 - font.width(s), y, a(dim, 0xB0A0A8), true);
            }
            y += 15;
        }
    }

    private static int lerp(int c0, int c1, float k) {
        int r = (int) Mth.lerp(k, c0 >> 16 & 255, c1 >> 16 & 255), gg = (int) Mth.lerp(k, c0 >> 8 & 255, c1 >> 8 & 255),
                b = (int) Mth.lerp(k, c0 & 255, c1 & 255);
        return r << 16 | gg << 8 | b;
    }

    // ------------------------------------------------------------------------------------------------ cards and words

    /** Drawn over everything (also during cutscenes): the title card and the spoken lines. */
    public static void cinema(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        RoseCast c = RoseCasts.of(mc.player);
        if (c == null) {
            return;
        }
        Font font = mc.font;
        int width = g.guiWidth(), height = g.guiHeight();
        float t = c.time(partial);
        int start = RBeats.cardStart(c.move), len = RBeats.cardLength(c.move);
        if (start >= 0 && t >= start && t < start + len) {
            float k = (t - start) / len;
            float in = Mth.clamp(k * 6, 0, 1), out = Mth.clamp((1 - k) * 5, 0, 1);
            float alpha = Math.min(in, out);
            float slide = (1 - easeOut(in)) * -60;
            int cy = height / 2 - 30;
            int band = (int) (width * 0.42F * easeOut(in));
            // a black slash of a band with a rose edge
            g.fill(0, cy - 4, band, cy + 54, a((int) (200 * alpha), 0x0A0006));
            g.fill(0, cy + 54, band, cy + 55, a((int) (255 * alpha), 0xFF5FA2));
            g.fill(0, cy - 5, (int) (band * 0.7F), cy - 4, a((int) (180 * alpha), 0xE89A90));
            g.pose().pushPose();
            g.pose().translate(18 + slide, cy, 0);
            g.pose().scale(3.2F, 3.2F, 1);
            g.drawString(font, c.move.kanji, 0, 0, a((int) (255 * alpha), 0xFFD0E4), false);
            g.pose().popPose();
            String title = spaced(c.move.title.toUpperCase());
            g.drawString(font, title, (int) (20 + slide * 0.5F), cy + 38, a((int) (255 * alpha), 0xFFFFFF), false);
        }
        for (String[] line : RBeats.lines(c.move)) {
            float at = Float.parseFloat(line[0]);
            float k = (t - at) / 60F;
            if (k >= 0 && k < 1) {
                float alpha = Math.min(Mth.clamp(k * 10, 0, 1), Mth.clamp((1 - k) * 6, 0, 1));
                String s = "“" + line[1] + "”";
                int sw = font.width(s);
                int yy = height - (Lens.active() ? (int) (height * 0.115F) + 18 : 64);
                g.drawString(font, s, width / 2 - sw / 2, yy, a((int) (255 * alpha), 0xFFE6F0), true);
            }
        }
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            b.append(ch).append(' ');
        }
        return b.toString().trim();
    }

    private static float easeOut(float x) {
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }
}
