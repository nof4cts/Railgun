package com.firestar.styles.client;

import com.firestar.styles.Style;
import com.firestar.styles.StyleNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Pick a style: a tall card for each style and "none", each with its kanji, what it does and its keys. Click or press 1-6. Under
 * the cards, the switch that turns every style's cutscenes on or off (or press C).
 */
public final class StyleScreen extends Screen {
    private static final Style[] ORDER = {Style.HEAVEN, Style.INSTINCT, Style.SWORDS, Style.ROSE, Style.SAGE, Style.NONE};
    private final long opened = System.currentTimeMillis();
    private final float[] hover = new float[ORDER.length];

    public StyleScreen() {
        super(Component.literal("Fighting Style"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final int GAP = 8;
    private net.minecraft.client.gui.components.Button cutsceneButton;

    /** All the cards in one row when the window is wide enough, otherwise two rows of three. */
    private int cols() {
        int n = ORDER.length;
        return (width - 24 - GAP * (n - 1)) / n >= 92 ? n : (n + 1) / 2;
    }

    private int rows() {
        return (ORDER.length + cols() - 1) / cols();
    }

    private int cardH() {
        int r = rows();
        return Mth.clamp((height - 72 - GAP * (r - 1)) / r, 80, 230);
    }

    private int top() {
        int block = rows() * cardH() + (rows() - 1) * GAP;
        return Math.max(30, height / 2 - block / 2 - 6);
    }

    private int bottom() {
        return top() + rows() * cardH() + (rows() - 1) * GAP;
    }

    private int[] card(int i) {
        int c = cols();
        int cw = Mth.clamp((width - 24 - GAP * (c - 1)) / c, 70, 150);
        int ch = cardH();
        int row = i / c, col = i % c;
        int inRow = Math.min(c, ORDER.length - row * c);
        int total = cw * inRow + GAP * (inRow - 1);
        int x0 = width / 2 - total / 2;
        return new int[]{x0 + col * (cw + GAP), top() + row * (ch + GAP), cw, ch};
    }

    private static Component cutsceneLabel() {
        boolean on = com.firestar.styles.StylesPrefs.cutscenes();
        return Component.literal("Cutscenes: ").append(Component.literal(on ? "ON" : "OFF")
                .withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
    }

    @Override
    protected void init() {
        int bw = 130;
        cutsceneButton = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(cutsceneLabel(), b -> toggleCutscenes())
                .bounds(width / 2 - bw / 2, bottom() + 8, bw, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Turn off to skip every style's cutscenes: the big techniques still happen in full, seen from your own camera.")))
                .build());
    }

    private void toggleCutscenes() {
        com.firestar.styles.StylesPrefs.setCutscenes(!com.firestar.styles.StylesPrefs.cutscenes());
        if (cutsceneButton != null) {
            cutsceneButton.setMessage(cutsceneLabel());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        float in = Mth.clamp((System.currentTimeMillis() - opened) / 220.0F, 0, 1);
        g.fill(0, 0, width, height, (int) (0xB0 * in) << 24);
        Font font = this.font;
        String title = "CHOOSE YOUR STYLE";
        g.drawString(font, title, width / 2 - font.width(title) / 2, top() - 20, 0xFFF0F0F0, true);
        Style mine = ClientStyles.mine();
        for (int i = 0; i < ORDER.length; i++) {
            Style s = ORDER[i];
            int[] c = card(i);
            boolean over = mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3];
            hover[i] = Mth.lerp(0.25F, hover[i], over ? 1 : 0);
            int lift = (int) (-6 * hover[i]) + (int) ((1 - in) * 20 * (i + 1));
            int x = c[0], y = c[1] + lift, w = c[2], h = c[3];
            int col = s.color;
            g.fill(x, y, x + w, y + h, 0xE0101014);
            g.fill(x, y, x + w, y + 3, 0xFF000000 | col);
            if (s == mine) {
                g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFF000000 | col);
            }
            g.fill(x, y + h - 2, x + (int) (w * hover[i]), y + h, 0xFF000000 | col);
            // the kanji, big
            g.pose().pushPose();
            float ks = w > 120 ? 3.0F : 2.4F;
            g.pose().translate(x + w / 2.0F - font.width(s.kanji) * ks / 2.0F, y + 14, 0);
            g.pose().scale(ks, ks, 1);
            g.drawString(font, s.kanji, 0, 0, 0xFF000000 | col, true);
            g.pose().popPose();
            int ty = y + 14 + (int) (9 * ks) + 8;
            g.drawString(font, s.title, x + w / 2 - font.width(s.title) / 2, ty, 0xFFFFFFFF, true);
            if (!s.kind.isEmpty()) {
                g.drawString(font, s.kind.toUpperCase(), x + w / 2 - font.width(s.kind.toUpperCase()) / 2, ty + 11, 0xFF9AA3B0, false);
            }
            // keys wrap onto as many lines as they need, never cut off; the description fills the space above them
            java.util.List<FormattedCharSequence> keyLines = new java.util.ArrayList<>();
            for (String k : s.keys) {
                keyLines.addAll(font.split(Component.literal(k), w - 12));
            }
            int ky = y + h - 8 - keyLines.size() * 10;
            int dy = ty + 26;
            for (FormattedCharSequence line : font.split(Component.literal(s.description), w - 12)) {
                if (dy > ky - 16) {
                    break;
                }
                g.drawString(font, line, x + 6, dy, 0xFFCCCCD4, false);
                dy += 10;
            }
            for (FormattedCharSequence k : keyLines) {
                g.drawString(font, k, x + 6, ky, 0xFF000000 | col, false);
                ky += 10;
            }
            String num = "[" + (i + 1) + "]";
            g.drawString(font, num, x + w - font.width(num) - 4, y + 5, 0xFF707080, false);
            if (s == mine && s != Style.NONE) {
                g.drawString(font, "●", x + 4, y + 5, 0xFF000000 | col, false);
            }
        }
        String hint = "Click a card, or press 1-" + ORDER.length + ".   C: cutscenes.   Esc to close.";
        g.drawString(font, hint, width / 2 - font.width(hint) / 2, bottom() + 28, 0xFF9AA3B0, false);
        super.render(g, mx, my, partial);
    }

    private void pick(Style s) {
        StyleNet.CHANNEL.sendToServer(new StyleNet.Choose(s.ordinal()));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < ORDER.length; i++) {
            int[] c = card(i);
            if (mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3]) {
                pick(ORDER[i]);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
            toggleCutscenes();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key < org.lwjgl.glfw.GLFW.GLFW_KEY_1 + ORDER.length) {
            pick(ORDER[key - org.lwjgl.glfw.GLFW.GLFW_KEY_1]);
            return true;
        }
        if (StyleClient.SWITCH.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
