package com.firestar.sage.client;

import com.firestar.sage.common.SMove;
import com.firestar.sage.net.SageNet;
import com.firestar.styles.Style;
import com.firestar.styles.client.ClientStyles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** The sage's panel at the right of the screen: the form, then each rite with its key and the time until it returns. */
public final class SageHud {
    private static long[] readyAt = new long[SMove.values().length];
    private static int[] total = new int[SMove.values().length];

    private SageHud() {}

    public static void ready(SageNet.Ready m) {
        if (m.at().length == readyAt.length) {
            readyAt = m.at();
            total = m.total();
        }
    }

    public static void draw(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !ClientStyles.mine(Style.SAGE)
                || com.firestar.sage.client.cam.Director.active()) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        long now = mc.level.getGameTime();
        boolean up = SageForms.ascended(mc.player);
        int x = w - 136, y = h / 2 - SMove.KIT.length * 8 - 22;
        g.fill(x - 4, y - 4, w - 4, y + 22, 0x90050308);
        g.fill(x - 4, y - 4, x - 2, y + 22, 0xFFF2C14E);
        g.drawString(font, "天仙  " + (up ? "MANTLED" : "MORTAL"), x + 2, y, up ? 0xFFF2C14E : 0xFFB8B2C8, true);
        String sub = (up ? "[" + SageKeys.name(SageKeys.MANTLE) + "] let go" : "[" + SageKeys.name(SageKeys.MANTLE) + "] take up the Mantle");
        g.drawString(font, sub, x + 2, y + 11, 0xB0FFFFFF, false);
        y += 26;
        for (SMove m : SMove.KIT) {
            long left = readyAt[m.ordinal()] - now;
            boolean ready = left <= 0;
            int a = up ? 0xFF : 0x70;
            g.fill(x - 4, y - 2, w - 4, y + 13, (a / 2) << 24 | 0x0B0812);
            String key = SageKeys.name(SageKeys.of(m));
            g.fill(x - 2, y - 1, x + 12, y + 12, a << 24 | (ready ? 0xF2C14E : 0x5A4A30));
            g.drawString(font, key, x + 5 - font.width(key) / 2, y + 2, a << 24 | 0x0A0710, false);
            g.drawString(font, m.title, x + 17, y + 2, a << 24 | (ready ? 0xFFF1C8 : 0x9A93A8), true);
            if (!ready) {
                int t = Math.max(1, total[m.ordinal()]);
                float k = Mth.clamp(left / (float) t, 0, 1);
                g.fill(x + 17, y + 11, x + 17 + (int) (110 * (1 - k)), y + 12, a << 24 | 0xF2C14E);
                String s = String.valueOf((int) Math.ceil(left / 20.0));
                g.drawString(font, s, w - 8 - font.width(s), y + 2, a << 24 | 0xFFFFFF, true);
            }
            y += 16;
        }
    }
}
