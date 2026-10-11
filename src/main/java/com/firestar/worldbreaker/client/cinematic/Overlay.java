package com.firestar.worldbreaker.client.cinematic;

import com.firestar.worldbreaker.config.WConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Letterbox bars (2.39:1), the technique's name card, and the skip hint. */
public final class Overlay {
    private Overlay() {}

    public static void draw(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Film film = Director.film();
        if (film == null) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float weight = Director.weight();
        int target = Math.max(0, (int) ((h - w / 2.39F) / 2.0F));
        int bar = (int) (target * Film.smooth(weight * 1.4F));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);

        float t = film.clock.time(partial);
        Film.Card cd = film.card;
        if (cd != null && WConfig.CLIENT.titleCards.get() && t >= cd.start() && t < cd.start() + cd.length() && !Director.skipped()) {
            card(g, font, cd.style(), cd.kanji(), cd.title(), cd.color(), true, t - cd.start(), cd.length(), w, h, bar);
        }
        subtitles(g, font, film.lines, t, w, h, bar);
        if (t > 10 && weight > 0.5F && !Director.skipped()) {
            String hint = "HOLD " + mc.options.keyShift.getTranslatedKeyMessage().getString().toUpperCase() + " TO SKIP";
            int a = (int) (Mth.clamp((t - 10) / 10.0F, 0, 1) * 110);
            g.drawString(font, hint, w - font.width(hint) - 10, h - Math.max(bar, 14) + (bar > 14 ? (bar - 8) / 2 : -2), a << 24 | 0xFFFFFF, false);
        }
    }

    private static void card(GuiGraphics g, Font font, String styleText, String kanji, String title, int color, boolean king, float age, int length,
                             int w, int h, int bar) {
        float in = 1.0F - (float) Math.pow(1.0F - Mth.clamp(age / 5.0F, 0, 1), 3.0);
        float out = Mth.clamp((length - age) / 4.0F, 0, 1);
        float a = Math.min(in, out);
        if (a <= 0.02F) {
            return;
        }
        int alpha = (int) (255 * a);
        int x = (int) (w * 0.07F - 40 * (1.0F - in));
        int y = h - bar - (king ? 92 : 78);

        String style = spaced(styleText);
        g.drawString(font, style, x, y, (int) (alpha * 0.8F) << 24 | 0xD0D0D0, true);

        float scale = king ? 4.2F + 0.2F * Mth.clamp(age / length, 0, 1) : 3.4F;
        g.pose().pushPose();
        g.pose().translate(x, y + 12, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, kanji, 0, 0, alpha << 24 | 0xFFFFFF, true);
        g.pose().popPose();

        int ny = y + 12 + (int) (9 * scale) + 4;
        int stroke = (int) (Math.max(160, font.width(title.toUpperCase()) * 1.6F + 20) * Mth.clamp(age / 6.0F, 0, 1));
        int col = color & 0xFFFFFF;
        g.fill(x, ny, x + stroke, ny + 2, alpha << 24 | col);
        g.pose().pushPose();
        g.pose().translate(x, ny + 6, 0);
        g.pose().scale(1.6F, 1.6F, 1.0F);
        g.drawString(font, title.toUpperCase(), 0, 0, alpha << 24 | 0xFFFFFF, true);
        g.pose().popPose();
    }

    /** What he says, in place of a voice: centred low, fading in and out, each line until the next. */
    private static void subtitles(GuiGraphics g, Font font, String[][] lines, float t, int w, int h, int bar) {
        if (lines == null) {
            return;
        }
        for (int i = 0; i < lines.length; i++) {
            float start = Float.parseFloat(lines[i][0]);
            float end = i + 1 < lines.length ? Math.min(start + 40, Float.parseFloat(lines[i + 1][0])) : start + 40;
            if (t < start || t > end) {
                continue;
            }
            float a = Math.min(Mth.clamp((t - start) / 4.0F, 0, 1), Mth.clamp((end - t) / 5.0F, 0, 1));
            if (a <= 0.02F) {
                continue;
            }
            String line = "「" + lines[i][1] + "」";
            float scale = bar > 18 ? 1.25F : 1.5F;
            g.pose().pushPose();
            g.pose().translate(w / 2.0F - font.width(line) * scale / 2.0F, bar > 18 ? h - bar + (bar - 9 * scale) / 2.0F : h - bar - 34, 0);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, line, 0, 0, ((int) (a * 255)) << 24 | 0xF2F8FF, true);
            g.pose().popPose();
        }
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && s.charAt(i) != ' ' && s.charAt(i - 1) != ' ') {
                b.append(' ');
            }
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    /** True when any HUD should be hidden for the film. */
    public static boolean hideHud() {
        return Director.active() && Director.weight() > 0.3F;
    }

}
