package com.firestar.mih.client.cinematic;

import com.firestar.mih.client.Casts;
import com.firestar.mih.client.ClientCast;
import com.firestar.mih.common.Beats;
import com.firestar.mih.common.Move;
import com.firestar.mih.config.ZConfig;
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

        ClientCast c = film.cast;
        Move m = c.move;
        float t = c.time(partial);
        int cs = Beats.cardStart(m);
        int cl = Beats.cardLength(m);
        if (ZConfig.CLIENT.titleCards.get() && cs >= 0 && t >= cs && t < cs + cl && !Director.skipped()) {
            card(g, font, m, t - cs, cl, w, h, bar);
        }
        if (m == Move.UNIVERSE_RESET && ZConfig.CLIENT.titleCards.get() && t >= Beats.RESET_NEW + 10 && t < Beats.RESET_NEW + 52) {
            card(g, font, "MADE IN HEAVEN", "一巡後の世界", "THE NEW UNIVERSE", 0xFFE9A8, true, t - Beats.RESET_NEW - 10, 42, w, h, bar);
        }
        if (t > 10 && weight > 0.5F && !Director.skipped() && m != Move.UNIVERSE_RESET) {
            String hint = "HOLD " + mc.options.keyShift.getTranslatedKeyMessage().getString().toUpperCase() + " TO SKIP";
            int a = (int) (Mth.clamp((t - 10) / 10.0F, 0, 1) * 110);
            g.drawString(font, hint, w - font.width(hint) - 10, h - Math.max(bar, 14) + (bar > 14 ? (bar - 8) / 2 : -2), a << 24 | 0xFFFFFF, false);
        }
    }

    /** Big kanji, the Stand's name above it, the move under a stroke in its colour that wipes in. Slides in, holds, wipes out. */
    private static void card(GuiGraphics g, Font font, Move m, float age, int length, int w, int h, int bar) {
        boolean king = m == Move.TIME_ACCEL || m == Move.UNIVERSE_RESET || m == Move.JUDGEMENT;
        card(g, font, m.style, m.kanji, m.title, m.color, king, age, length, w, h, bar);
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

    @SuppressWarnings("unused")
    private static boolean any() {
        return Casts.any();
    }
}
