package com.woh.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.JjkHud;
import com.jjk.mod.client.Keys;
import com.woh.common.WMove;
import com.woh.net.WNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * His move list, drawn exactly the way the kit draws every sorcerer's: the left edge, vertically centred,
 * "[key] move" with the key highlight, and a cooldown underline in his colour. (The name tag and the cursed-energy
 * bar on the right are the kit's own.)
 */
public final class WHud {
    private static long[] readyAt = new long[WMove.values().length];
    private static int[] total = new int[WMove.values().length];
    private static long domainUntil, overdriveUntil;

    private WHud() {}

    public static void state(WNet.State m) {
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
    private static float cooldown(WMove m, long now) {
        int i = m.ordinal();
        long left = readyAt[i] - now;
        if (left <= 0 || total[i] <= 0) return 0f;
        return Math.min(1f, left / (float) total[i]);
    }

    /**
     * His cursed energy is infinite: the kit's bar (top right) is covered with a full, slowly shimmering bar and
     * "CE INFINITE". Drawn high in the GUI's depth so it stays on top whichever HUD draws last.
     */
    public static void infinite(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || WCam.running() || JjkHud.cutscene()) return;
        if (ClientSorcerers.mine() != Sorcerer.WORLD_OVER_HEAVEN || mc.screen instanceof com.jjk.mod.client.SelectorScreen) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int barW = 90, bx = w - barW - 4, by = 19;
        int color = Sorcerer.WORLD_OVER_HEAVEN.color;
        float t = (System.nanoTime() / 1.0e9f) % 1000f;
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        // cover the kit's numbers
        String old = "CE 1000 / 1000";
        int ow = font.width(old) + 8;
        g.fill(bx - ow - 2, by - 3, bx - 2, by + 9, 0xFF0C0608);
        // the bar: always full, a highlight sweeping along it
        g.fill(bx - 1, by - 1, bx + barW + 1, by + 6, 0xFF000000);
        g.fill(bx, by, bx + barW, by + 5, 0xFF000000 | color);
        int sweep = (int) ((t * 45) % (barW + 30)) - 15;
        for (int i = 0; i < 12; i++) {
            int x = bx + sweep + i;
            if (x < bx || x >= bx + barW) continue;
            int a = (int) (110 * Math.sin(Math.PI * i / 12.0));
            g.fill(x, by, x + 1, by + 5, (a << 24) | 0xFFFFFF);
        }
        String text = "CE INFINITE";
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 3.0);
        int tc = 0xFF000000 | ((int) (255 * pulse) << 16) | ((int) (60 * pulse) << 8) | (int) (70 * pulse);
        g.drawString(font, text, bx - font.width(text) - 4, by - 1, tc, true);
        g.pose().popPose();
    }

    public static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || WCam.running() || JjkHud.cutscene()) return;
        if (ClientSorcerers.mine() != Sorcerer.WORLD_OVER_HEAVEN) return;
        Sorcerer s = Sorcerer.WORLD_OVER_HEAVEN;
        Font font = mc.font;
        long now = mc.level.getGameTime();
        boolean dom = domainOpen(), od = overdrive();
        int y = g.guiHeight() / 2 - s.moves.length * 11 / 2;
        for (int i = 0; i < s.moves.length; ++i) {
            WMove m = WMove.resolve(i, dom, od);
            float cd = m == null ? 0f : cooldown(m, now);
            String text = m != null && m.sneak ? m.label() + "  [now]" : s.moves[i];
            if (m == WMove.HEAVEN_ASCENSION && od) text = m.label() + "  [ON]";
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
