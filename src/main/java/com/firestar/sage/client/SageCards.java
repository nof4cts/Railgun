package com.firestar.sage.client;

import com.firestar.sage.common.SMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The name of a great rite, written on the picture as it begins: the kanji stacked top to bottom down the right third of
 * the screen, a gold rule drawn down beside them, and the English name in spaced capitals at its foot. Only the caster
 * sees it, and only for the rites that have a name worth writing (those with kanji).
 */
public final class SageCards {
    private static final float IN = 6, HOLD = 64, OUT = 16;

    private SageCards() {}

    public static void draw(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        SageCasts.Play best = null;
        for (SageCasts.Play p : SageCasts.all()) {
            if (p.mine && !p.rite().kanji.isEmpty() && (p.rite().cinematic || p.rite() == SMove.THORNWOOD)) {
                float t = p.time(partial) - start(p.rite());
                if (t >= 0 && t < IN + HOLD + OUT && (best == null || p.msg.start() > best.msg.start())) {
                    best = p;
                }
            }
        }
        if (best == null) {
            return;
        }
        SMove m = best.rite();
        float t = best.time(partial) - start(m);
        float a = t < IN ? t / IN : t > IN + HOLD ? 1 - (t - IN - HOLD) / OUT : 1;
        a = Mth.clamp(a, 0, 1);
        if (a < 0.02F) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean cine = com.firestar.sage.client.cam.Director.active();
        float scale = cine ? 3.2F : 2.2F;
        int n = m.kanji.length();
        float lineH = 10 * scale;
        float block = n * lineH;
        float cx = w * (cine ? 0.80F : 0.86F);
        float top = h * 0.5F - block * 0.5F - 10;
        int alpha = (int) (a * 255) << 24;
        // the rule: drawn down from the top as the card comes in
        float draw = Mth.clamp(t / (IN + 10), 0, 1);
        int rx = (int) (cx + 7 * scale);
        g.fill(rx, (int) top - 4, rx + 1, (int) (top - 4 + (block + 8) * draw), alpha | 0xF2C14E);
        g.fill(rx + 3, (int) top + 6, rx + 4, (int) (top + 6 + (block - 12) * draw), (int) (a * 120) << 24 | 0xFFF1C8);
        // each character slides a little down into place, one after another
        for (int i = 0; i < n; i++) {
            float ci = Mth.clamp((t - i * 2.5F) / IN, 0, 1);
            if (ci <= 0) {
                continue;
            }
            float drop = (1 - ci) * (1 - ci) * 8;
            String ch = m.kanji.substring(i, i + 1);
            g.pose().pushPose();
            g.pose().translate(cx - font.width(ch) * scale / 2, top + i * lineH - drop, 0);
            g.pose().scale(scale, scale, 1);
            int ca = (int) (a * ci * 255) << 24;
            g.drawString(font, ch, 1, 1, (int) (a * ci * 160) << 24 | 0x3A2400, false);
            g.drawString(font, ch, 0, 0, ca | 0xF2C14E, false);
            g.pose().popPose();
        }
        // the name, in spaced capitals
        StringBuilder sb = new StringBuilder();
        for (char c : m.title.toUpperCase().toCharArray()) {
            sb.append(c).append(' ');
        }
        String name = sb.toString().trim();
        float na = Mth.clamp((t - IN - 4) / 8, 0, 1) * a;
        if (na > 0.02F) {
            int ny = (int) (top + block + 8);
            g.drawString(font, name, (int) cx - font.width(name) / 2, ny, (int) (na * 255) << 24 | 0xFFF6E2, true);
        }
    }

    /** When the card comes up: after the rite has gathered itself. */
    private static float start(SMove m) {
        return switch (m) {
            case MANTLE -> 30;
            case STARFALL -> 8;
            case COLOSSUS -> 10;
            case ECLIPSE -> 14;
            default -> 2;
        };
    }
}
