package com.naoya.ps.client;

import com.jjk.mod.JjkNet;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.Keys;
import com.naoya.ps.Net;
import com.naoya.ps.client.cine.Director;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** Naoya's move list with cooldowns, his cursed energy, and acceleration as a strip of lit film frames. */
public final class Hud {
    private static long[] ready = new long[8];
    private static int[] total = new int[8];
    private static int accel;

    private Hud() {}

    public static void accept(Net.State s) {
        ready = s.ready();
        total = s.total();
        accel = s.accel();
    }

    public static void draw(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || Director.active() || ClientSorcerers.mine() != Sorcerer.NAOYA) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int color = Sorcerer.NAOYA.color;
        long now = mc.level.getGameTime();
        // cursed energy, under the core's name tag
        int ce = JjkNet.EnergySync.clientValue;
        int barW = 90, bx = w - barW - 4, by = 19;
        g.fill(bx - 1, by - 1, bx + barW + 1, by + 6, 0x90000000);
        g.fill(bx, by, bx + barW * ce / 500, by + 5, 0xFF000000 | color);
        String ceText = "CE " + ce + " / 500";
        g.drawString(font, ceText, bx - font.width(ceText) - 4, by - 1, 0xFFE0E0E0, true);
        // acceleration: five frames of film, lit one by one
        int fy = by + 10;
        String lab = "ACCEL";
        g.drawString(font, lab, w - 4 - 5 * 15 - font.width(lab) - 4, fy + 1, 0xFFE0E0E0, true);
        for (int i = 0; i < 5; i++) {
            int x = w - 4 - (5 - i) * 15;
            boolean on = i < accel;
            g.fill(x, fy, x + 13, fy + 10, 0xFF0B0B0E);
            g.fill(x + 1, fy + 2, x + 12, fy + 8, on ? 0xFF000000 | color : 0xFF2A2A2E);
            g.fill(x + 1, fy, x + 3, fy + 1, 0xFFF3EFE4);
            g.fill(x + 10, fy, x + 12, fy + 1, 0xFFF3EFE4);
            g.fill(x + 1, fy + 9, x + 3, fy + 10, 0xFFF3EFE4);
            g.fill(x + 10, fy + 9, x + 12, fy + 10, 0xFFF3EFE4);
        }
        // moves
        String[] moves = Sorcerer.NAOYA.moves;
        int y = g.guiHeight() / 2 - moves.length * 11 / 2;
        for (int i = 0; i < moves.length; i++) {
            long left = i < ready.length ? ready[i] - now : 0;
            float cd = left <= 0 || i >= total.length ? 0 : Mth.clamp(left / (float) Math.max(1, total[i]), 0, 1);
            String line = "[" + Keys.label(i) + "] " + moves[i];
            int col = cd > 0 ? 0xFF7A7A88 : 0xFFF0F0F5;
            g.drawString(font, line, 6, y, col, true);
            int lw = font.width(line);
            g.fill(6, y + 9, 6 + lw, y + 10, 0x50000000);
            g.fill(6, y + 9, 6 + (int) (lw * (1 - cd)), y + 10, 0xFF000000 | color);
            y += 12;
        }
    }
}
