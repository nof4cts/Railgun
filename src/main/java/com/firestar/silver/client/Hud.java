package com.firestar.silver.client;

import com.firestar.silver.client.cinematic.Director;
import com.firestar.silver.common.Move;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The kit at the right of the screen: the current form, then each technique with its key and a cooldown bar. Moves the
 * current form can't reach yet are shown dimmed with the form they need.
 */
public final class Hud implements IGuiOverlay {
    public static final Hud INSTANCE = new Hud();
    private static final String[] FORMS = {"BASE", "INSTINCT OMEN", "SILVER ZENITH"};
    private static final int[] FORM_COLORS = {0xBBBBBB, 0x9CC8FF, 0xF2F8FF};
    private static long[] readyAt = new long[Move.values().length];
    private static int[] total = new int[Move.values().length];
    private static long hintUntil;

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

    /** A technique key was pressed before awakening. */
    public static void hint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            hintUntil = mc.level.getGameTime() + 50;
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || Director.active()) {
            return;
        }
        Font font = mc.font;
        long now = mc.level.getGameTime();
        if (!com.firestar.styles.client.ClientStyles.mine(com.firestar.styles.Style.INSTINCT)) {
            return;
        }
        ClientState.Info info = ClientState.get(mc.player);
        int form = info == null ? 0 : info.form;
        float power = info == null ? 0 : Mth.lerp(partial, info.powerO, info.power);
        int x = width - 132;
        int y = height / 2 - Move.KIT.length * 7 - 18;

        if (hintUntil > now && form == 0) {
            String s = "Awaken first  [" + Keys.name(Keys.AWAKEN) + "]";
            int a = (int) (255 * Mth.clamp((hintUntil - now) / 10.0F, 0, 1));
            if (a > 8) {
                g.drawString(font, s, width / 2 - font.width(s) / 2, height / 2 + 24, a << 24 | 0xCFE6FF, true);
            }
        }

        // form banner with a little aura bar
        String head = "「" + FORMS[form] + "」";
        g.drawString(font, head, x, y, 0xFF000000 | FORM_COLORS[form], true);
        g.fill(x, y + 11, x + 128, y + 12, 0x60000000);
        g.fill(x, y + 11, x + (int) (128 * power), y + 12, 0xFF000000 | (form == 2 ? 0xF2F8FF : 0x8FC4FF));
        y += 16;
        long zenithRest = readyAt(Move.AWAKEN_ZENITH) - now;
        if (form == 1 && zenithRest > 0 && !mc.player.isCreative()) {
            g.drawString(font, "Zenith returns in " + clock(zenithRest), x, y, 0x90B8C8DC, true);
            y += 11;
        } else {
            String next = form == 0 ? "awaken" : form == 1 ? "reach Zenith" : "";
            if (!next.isEmpty()) {
                g.drawString(font, "[" + Keys.name(Keys.AWAKEN) + "] " + next, x, y, 0xB0FFFFFF, true);
                y += 11;
            }
        }
        if (form == 2 && info.until > 0) {
            long left = Math.max(0, info.until - now);
            // the last ten seconds pulse
            int col = left < 200 && (now / 5) % 2 == 0 ? 0xFFFF8A8A : 0xFFF2F8FF;
            g.drawString(font, "Zenith fades in " + clock(left), x, y, col, true);
            g.fill(x, y + 10, x + 128, y + 11, 0x60000000);
            g.fill(x, y + 10, x + (int) (128 * Mth.clamp(left / (float) Math.max(1, info.until - info.since), 0, 1)), y + 11, 0xFFF2F8FF);
            y += 14;
        }
        if (form > 0) {
            g.drawString(font, "[SHIFT+" + Keys.name(Keys.AWAKEN) + "] revert", x, y, 0x80FFFFFF, true);
            y += 11;
        }
        y += 3;
        int alphaAll = form == 0 ? 0x90 : 0xFF;
        for (Move m : Move.KIT) {
            long left = readyAt[m.ordinal()] - now;
            int tot = Math.max(1, total[m.ordinal()]);
            boolean ready = left <= 0;
            boolean locked = form < Math.max(1, m.form);
            int alpha = alphaAll;
            g.fill(x - 2, y - 2, x + 128, y + 11, (int) (alpha * 0.35F) << 24);
            g.drawString(font, Keys.name(Keys.of(m)), x, y, alpha << 24 | 0xCFE6FF, true);
            int color = locked ? 0x5A6470 : ready ? (m.color & 0xFFFFFF) : 0x888888;
            g.drawString(font, m.title, x + 14, y, alpha << 24 | color, true);
            if (locked && m.form > 0) {
                String need = m.form == 2 ? "Z" : "O";
                g.drawString(font, need, x + 124 - font.width(need), y, alpha << 24 | FORM_COLORS[m.form], true);
            } else if (!ready) {
                float frac = Mth.clamp(left / (float) tot, 0, 1);
                g.fill(x + 14, y + 9, x + 14 + (int) (110 * (1 - frac)), y + 10, alpha << 24 | 0x8FC4FF);
                String s = String.format("%.0f", Math.ceil(left / 20.0));
                g.drawString(font, s, x + 124 - font.width(s), y, alpha << 24 | 0xAAAAAA, true);
            }
            y += 13;
        }
    }

    private static String clock(long ticks) {
        int secs = (int) Math.ceil(ticks / 20.0);
        return (secs / 60) + ":" + String.format("%02d", secs % 60);
    }
}
