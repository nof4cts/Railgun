package com.aegis.command.client.ui;

import com.aegis.command.AegisConfig;

/** Terminal palette: charcoal surfaces, neon accent, amber warnings, red alerts, cyan arsenal tech. */
public final class Theme {
    public static final int[] ACCENTS = {0x3CFF8E, 0x3AE6FF, 0xFFB23A};
    public static final String[] ACCENT_NAMES = {"TACTICAL GREEN", "SIGNAL CYAN", "AMBER"};

    public static final int BG = 0x070A09;
    public static final int SURFACE = 0x0C1210;
    public static final int SURFACE_HI = 0x121A17;
    public static final int AMBER = 0xFFB020;
    public static final int RED = 0xFF3B3B;
    public static final int WHITE = 0xE8F4EE;
    public static final int MUTED = 0x6E827A;
    public static final int TECH = 0x37D7FF;
    public static final int TECH_DEEP = 0x0A3A66;

    private Theme() {}

    public static int accent() {
        return ACCENTS[Math.floorMod(AegisConfig.CLIENT.accent.get(), ACCENTS.length)];
    }

    /** rgb + alpha (0..1) -> argb */
    public static int a(int rgb, float alpha) {
        int al = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    public static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * k));
        int b = Math.min(255, (int) ((rgb & 255) * k));
        return (r << 16) | (g << 8) | b;
    }
}
