package com.gojo.rework.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * What the rites do to the picture itself: flashes, a darkened world (the eclipse), letterbox bars during cutscenes, a
 * vignette, and camera shake. Each frame the rites ask for what they want; the strongest ask wins.
 */
public final class Look {
    public float flash, flashR = 1, flashG = 1, flashB = 1;
    public float dark;
    public float bars;
    public float vignette;
    public float shake;
    public float gold;

    public void reset() {
        flash = dark = bars = vignette = shake = gold = 0;
        flashR = flashG = flashB = 1;
    }

    public void flash(float amount, int rgb) {
        
        if (amount > flash) {
            flash = amount;
            flashR = ((rgb >> 16) & 255) / 255.0F;
            flashG = ((rgb >> 8) & 255) / 255.0F;
            flashB = (rgb & 255) / 255.0F;
        }
    }

    public void dark(float a) {
        dark = Math.max(dark, a);
    }

    public void bars(float a) {
        bars = Math.max(bars, a);
    }

    public void vignette(float a) {
        vignette = Math.max(vignette, a);
    }

    public void shake(float a) {
        shake = Math.max(shake, a);
    }

    public void gold(float a) {
        gold = Math.max(gold, a);
    }

    /** Drawn over the whole screen, under the HUD. */
    public void draw(GuiGraphics g) {
        int w = g.guiWidth(), h = g.guiHeight();
        if (dark > 0.01F) {
            g.fill(0, 0, w, h, (int) (Mth.clamp(dark, 0, 1) * 170) << 24 | 0x05030A);
        }
        if (gold > 0.01F) {
            g.fill(0, 0, w, h, (int) (Mth.clamp(gold, 0, 1) * 60) << 24 | 0xF2C14E);
        }
        if (vignette > 0.01F) {
            int a = (int) (Mth.clamp(vignette, 0, 1) * 200);
            int edge = Math.max(8, w / 6);
            for (int i = 0; i < edge; i += 2) {
                int al = (int) (a * Math.pow(1 - i / (float) edge, 2.2));
                int c = al << 24;
                g.fill(i, i, i + 2, h - i, c);
                g.fill(w - i - 2, i, w - i, h - i, c);
                g.fill(i, i, w - i, i + 2, c);
                g.fill(i, h - i - 2, w - i, h - i, c);
            }
        }
        if (bars > 0.01F) {
            int bh = (int) (h * 0.11F * Mth.clamp(bars, 0, 1));
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        if (flash > 0.01F) {
            int a = (int) (Mth.clamp(flash, 0, 1) * 255);
            g.fill(0, 0, w, h, a << 24 | (int) (flashR * 255) << 16 | (int) (flashG * 255) << 8 | (int) (flashB * 255));
        }
    }

    /** The camera's shake this frame: yaw, pitch and roll offsets in degrees. */
    public float[] shakeAngles(float time) {
        if (shake < 0.01F) {
            return new float[]{0, 0, 0};
        }
        float s = shake;
        return new float[]{(Ink.noise(time * 7.1, 1.3, 0) - 0.5F) * 3.0F * s, (Ink.noise(time * 6.3, 4.1, 0) - 0.5F) * 3.0F * s,
                (Ink.noise(time * 5.7, 8.9, 0) - 0.5F) * 2.0F * s};
    }

    public static boolean cinematic() {
        return Minecraft.getInstance().options.hideGui;
    }
}
