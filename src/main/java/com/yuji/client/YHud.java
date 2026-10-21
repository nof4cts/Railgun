package com.yuji.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.JjkHud;
import com.jjk.mod.client.Keys;
import com.yuji.common.YMove;
import com.yuji.net.YNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * His move list, drawn exactly the way the kit draws every sorcerer's: the left edge, vertically centred,
 * "[key] move" with the key highlight, and a cooldown underline in his colour. (The name tag and the cursed-energy
 * bar on the right are the kit's own.)
 */
public final class YHud {
    private static long[] readyAt = new long[YMove.values().length];
    private static int[] total = new int[YMove.values().length];
    private static long domainUntil, overdriveUntil;

    private YHud() {}

    public static void state(YNet.State m) {
        if (m.readyAt().length == readyAt.length) {
            readyAt = m.readyAt();
            total = m.total();
        }
        domainUntil = m.domainUntil();
        overdriveUntil = m.overdriveUntil();
    }

    public static boolean domainOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && domainUntil > mc.level.getGameTime();
    }

    public static boolean overdrive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && overdriveUntil > mc.level.getGameTime();
    }

    /** 0 = ready, 1 = just used. */
    private static float cooldown(YMove m, long now) {
        int i = m.ordinal();
        long left = readyAt[i] - now;
        if (left <= 0 || total[i] <= 0) return 0f;
        return Math.min(1f, left / (float) total[i]);
    }

    public static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || YCam.running() || JjkHud.cutscene()) return;
        if (ClientSorcerers.mine() != Sorcerer.YUJI) return;
        Sorcerer s = Sorcerer.YUJI;
        Font font = mc.font;
        long now = mc.level.getGameTime();
        boolean dom = domainOpen(), od = overdrive();
        int y = g.guiHeight() / 2 - s.moves.length * 11 / 2;
        for (int i = 0; i < s.moves.length; ++i) {
            YMove m = YMove.resolve(i, dom, od);
            float cd = m == null ? 0f : cooldown(m, now);
            String text = m != null && m.sneak ? m.label() + "  [now]" : s.moves[i];
            String line = "[" + Keys.label(i) + "] " + text;
            int col = cd > 0.0f ? -8749176 : -986891;
            Keys.mark(g, font, line, 6, y, i);
            g.drawString(font, line, 6, y, col, true);
            int lw = font.width(line);
            g.fill(6, y + 9, 6 + lw, y + 10, 0x50000000);
            g.fill(6, y + 9, 6 + (int) (lw * (1.0f - cd)), y + 10, 0xFF000000 | s.color);
            y += 12;
        }
    }
}
