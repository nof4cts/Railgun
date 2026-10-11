package com.firestar.santoryu.client;

import com.firestar.santoryu.client.cinematic.Director;
import com.firestar.santoryu.common.Move;
import com.firestar.santoryu.registry.KatanaItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** The technique list beside the hotbar: the selected one large, its neighbours dim, a cooldown bar under each. */
public final class Hud implements IGuiOverlay {
    public static final Hud INSTANCE = new Hud();
    private static int selected;
    private static long[] readyAt = new long[Move.values().length];
    private static int[] total = new int[Move.values().length];

    private Hud() {}

    public static void cooldowns(long[] ready, int[] totals) {
        if (ready.length == readyAt.length) {
            readyAt = ready;
        }
        if (totals.length == total.length) {
            total = totals;
        }
    }

    public static void select(int delta) {
        int n = Move.values().length;
        selected = ((selected + delta) % n + n) % n;
    }

    public static Move selected() {
        return Move.byId(selected);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || Director.active()) {
            return;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof KatanaItem)
                || !com.firestar.styles.client.ClientStyles.mine(com.firestar.styles.Style.SWORDS)) {
            return;
        }
        Font font = mc.font;
        long now = mc.level.getGameTime();
        int x = width / 2 + 98;
        int y = height - 46;
        for (int off = -1; off <= 1; off++) {
            int n = Move.values().length;
            Move m = Move.byId(((selected + off) % n + n) % n);
            boolean sel = off == 0;
            int row = y + off * 12 + (off > 0 ? 6 : 0);
            long left = readyAt[m.ordinal()] - now;
            float frac = total[m.ordinal()] <= 0 ? 0 : Math.max(0, Math.min(1, left / (float) total[m.ordinal()]));
            int alpha = sel ? 0xFF : 0x80;
            String name = m.title + (sel ? "  " + m.kanji : "");
            g.drawString(font, name, x, row, alpha << 24 | (left > 0 ? 0x888888 : (m.color & 0xFFFFFF)), true);
            if (sel) {
                int bw = Math.max(60, font.width(name));
                g.fill(x, row + 10, x + bw, row + 12, 0x60000000);
                if (left > 0) {
                    g.fill(x, row + 10, x + (int) (bw * (1 - frac)), row + 12, 0xFFD02020);
                    String s = String.format("%.1fs", left / 20.0F);
                    g.drawString(font, s, x + bw + 4, row + 6, 0xFFAAAAAA, true);
                } else {
                    g.fill(x, row + 10, x + bw, row + 12, 0xFF000000 | (m.color & 0xFFFFFF));
                }
            }
        }
    }
}
