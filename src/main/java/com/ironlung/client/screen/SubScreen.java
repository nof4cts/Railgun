package com.ironlung.client.screen;

import com.ironlung.client.ClientMissionState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared look for the submarine's instruments: dark steel, dull red phosphor, no pause. */
public abstract class SubScreen extends Screen {
    public static final int TEXT = 0xFFB0352A;
    public static final int TEXT_DIM = 0xFF5A1A16;
    public static final int TEXT_HOT = 0xFFE0563F;
    public static final int AMBER = 0xFFC08A2A;
    public static final int STEEL = 0xFF1B1716;
    public static final int STEEL_LIGHT = 0xFF2C2522;
    public static final int STEEL_DARK = 0xFF0C0A0A;

    protected int ticks;

    protected SubScreen(Component title) {
        super(title);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
        if (!ClientMissionState.inSub()) onClose();
    }

    /** A bolted steel panel. */
    protected void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, STEEL_DARK);
        g.fill(x, y, x + w, y + h, STEEL);
        g.fill(x, y, x + w, y + 1, STEEL_LIGHT);
        g.fill(x, y, x + 1, y + h, STEEL_LIGHT);
        int[][] bolts = {{x + 4, y + 4}, {x + w - 6, y + 4}, {x + 4, y + h - 6}, {x + w - 6, y + h - 6}};
        for (int[] b : bolts) {
            g.fill(b[0], b[1], b[0] + 2, b[1] + 2, 0xFF3D3330);
            g.fill(b[0] + 1, b[1] + 1, b[0] + 2, b[1] + 2, 0xFF080606);
        }
    }

    /** Inset screen area. */
    protected void bezel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF050404);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2A0C0A);
        g.fill(x, y, x + w, y + h, 0xFF030202);
    }

    protected void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    protected void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    protected void lamp(GuiGraphics g, int x, int y, boolean on, int onColor) {
        g.fill(x - 1, y - 1, x + 7, y + 7, 0xFF050404);
        g.fill(x, y, x + 6, y + 6, on ? onColor : 0xFF1E0A08);
        if (on) g.fill(x + 1, y + 1, x + 3, y + 3, 0x80FFFFFF);
    }

    protected boolean blink(int period) {
        return (ticks / period) % 2 == 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xC0000000);
        renderContents(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
    }

    protected abstract void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick);
}
