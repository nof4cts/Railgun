package com.firestar.mih.client;

import com.firestar.mih.client.cinematic.Director;
import com.firestar.mih.common.Beats;
import com.firestar.mih.common.Move;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The Stand's move list, YBA-style: each move with its key and a cooldown bar, at the right of the screen while the
 * Stand is out; and, while time accelerates, a gauge across the top counting the speed of the day.
 */
public final class Hud implements IGuiOverlay {
    public static final Hud INSTANCE = new Hud();
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

    public static long readyAt(Move m) {
        return readyAt[m.ordinal()];
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || Director.active()) {
            return;
        }
        Font font = mc.font;
        long now = mc.level.getGameTime();
        accelGauge(g, font, partial, width);
        ClientState.Info info = ClientState.get(mc.player);
        if (info == null || !info.has) {
            return;
        }
        float shown = Mth.lerp(partial, info.shownO, info.shown);
        int x = width - 128;
        int y = height / 2 - Move.KIT.length * 7 - 10;
        int head = (int) (0x90 + 0x6F * shown);
        g.drawString(font, "「MADE IN HEAVEN」", x, y, head << 24 | 0xC8FFF0, true);
        if (shown < 0.05F) {
            g.drawString(font, "[" + Keys.name(Keys.SUMMON) + "] summon", x, y + 12, 0x90FFFFFF, true);
            return;
        }
        y += 14;
        boolean da = info.daUntil > now;
        for (Move m : Move.KIT) {
            long left = readyAt[m.ordinal()] - now;
            int tot = Math.max(1, total[m.ordinal()]);
            boolean ready = left <= 0;
            String key = Keys.name(Keys.of(m));
            int alpha = (int) (255 * shown);
            if (alpha < 8) {
                continue;
            }
            g.fill(x - 2, y - 2, x + 124, y + 11, (int) (alpha * 0.35F) << 24);
            g.drawString(font, key, x, y, alpha << 24 | 0xFFE9A8, true);
            String name = m.title;
            boolean activeAccel = (m == Move.TIME_ACCEL && ClientState.accelerating(info, now)) || (m == Move.INFINITE_SPEED && info.infUntil > now);
            if (activeAccel) {
                name = m == Move.TIME_ACCEL ? "Cancel Acceleration" : "Release Infinite Speed";
            }
            g.drawString(font, name, x + 14, y, alpha << 24 | (ready || activeAccel ? (m.color & 0xFFFFFF) : 0x888888), true);
            if (!ready && !activeAccel) {
                float frac = Mth.clamp(left / (float) tot, 0, 1);
                g.fill(x + 14, y + 9, x + 14 + (int) (108 * (1 - frac)), y + 10, alpha << 24 | 0x7CFFD4);
                String s = String.format("%.0f", Math.ceil(left / 20.0));
                g.drawString(font, s, x + 122 - font.width(s), y, alpha << 24 | 0xAAAAAA, true);
            }
            y += 13;
        }
        if (da) {
            float k = Mth.clamp((info.daUntil - now) / (float) Beats.DA_DURATION, 0, 1);
            g.drawString(font, "DOUBLE ACCELERATION", x, y + 2, 0xFF7CFFD4, true);
            g.fill(x, y + 12, x + (int) (122 * k), y + 13, 0xFF7CFFD4);
        }
    }

    private static void accelGauge(GuiGraphics g, Font font, float partial, int width) {
        double progress = ClientState.accelProgress(partial);
        if (progress < 0) {
            return;
        }
        double rate = ClientState.accelRate(partial);
        int w = 180;
        int x = width / 2 - w / 2;
        int y = 14;
        String title = "時 は 加 速 す る";
        g.drawString(font, title, width / 2 - font.width(title) / 2, y - 11, 0xFFFFE9A8, true);
        g.fill(x - 1, y - 1, x + w + 1, y + 4, 0x90000000);
        g.fill(x, y, x + (int) (w * progress), y + 3, 0xFFFFD86B);
        String speed = String.format("×%,.0f", rate);
        g.drawString(font, speed, width / 2 - font.width(speed) / 2, y + 7, 0xFFFFFFFF, true);
    }
}
