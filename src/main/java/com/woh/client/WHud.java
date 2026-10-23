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
 * His move list on the left edge, in the kit's key convention ("[key] move", the current page highlighted), set in
 * gold: a hairline between each page of four, cooling moves greyed with a gold line that refills as they return.
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
     * The Stand's plate, top right, over the kit's energy readout (a Stand has no cursed energy): a small gold dial
     * whose hand turns, and what the Stand is doing right now - at his side, striking, ascended, holding the field.
     */
    public static void infinite(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || WCam.running() || JjkHud.cutscene()) return;
        if (ClientSorcerers.mine() != Sorcerer.WORLD_OVER_HEAVEN || mc.screen instanceof com.jjk.mod.client.SelectorScreen) return;
        Font font = mc.font;
        int w = g.guiWidth();
        int pw = 150, px = w - pw - 4, py = 14;
        float t = (System.nanoTime() / 1.0e9f) % 1000f;
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        // the plate (covers the kit's energy numbers and bar)
        g.fill(px, py, px + pw, py + 14, 0xE00C0904);
        g.fill(px, py + 13, px + pw, py + 14, 0xFFE6C867);
        // a tiny dial: twelve gold ticks round a dark face, one hand turning
        int cx = px + 8, cy = py + 7;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = cx + (int) Math.round(Math.cos(a) * 5), y = cy + (int) Math.round(Math.sin(a) * 5);
            g.fill(x, y, x + 1, y + 1, 0xFFE6C867);
        }
        double ha = t * 1.4;
        for (int k = 1; k <= 4; k++) {
            int x = cx + (int) Math.round(Math.cos(ha) * k), y = cy + (int) Math.round(Math.sin(ha) * k);
            g.fill(x, y, x + 1, y + 1, 0xFFFFF4D6);
        }
        String state;
        if (domainOpen()) state = "REALITY COLLAPSE";
        else if (overdrive()) state = "ASCENDED";
        else if (WStand.active(mc.player.getId()) != null) state = "STAND: STRIKING";
        else state = "STAND: AT HIS SIDE";
        g.drawString(font, state, px + 18, py + 3, 0xFFF2DFA6, true);
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
            int col = cd > 0.0f ? 0xFF6E6658 : 0xFFF2E6C8;
            if (i > 0 && i % 4 == 0) g.fill(6, y - 2, 120, y - 1, 0x60E6C867);
            Keys.mark(g, font, line, 6, y, i);
            g.drawString(font, line, 6, y, col, true);
            int lw = font.width(line);
            g.fill(6, y + 9, 6 + lw, y + 10, 0x50000000);
            g.fill(6, y + 9, 6 + (int) (lw * (1.0f - cd)), y + 10, 0xFF000000 | s.color);
            y += 12;
        }
    }
}
