package com.firestar.orbital.client.fx;

/**
 * Anime impact frames. Each sequence is a list of (mode, ticks held): the picture is redrawn in a new two-tone look
 * and held for a whole tick or two, "on ones" and "on twos", never flickered per render frame. Mode numbers match
 * composite.fsh.
 */
public final class Impact {
    public static final int INK = 1;        // black ink on white paper
    public static final int INVERT = 2;     // white on black
    public static final int CRIMSON = 3;    // black and blood red, white where it burns
    public static final int NEGATIVE = 4;   // colour negative, pushed
    public static final int WHITEOUT = 5;   // the flash: nearly all white, only the hardest edges inked
    public static final int HALFTONE = 6;   // big screentone dots in the accent colour on black
    public static final int HAKI = 7;       // black, edges lit in the accent, black lightning crawling from the hit
    public static final int SLASHED = 8;    // split along the cut: ink one side, inverted the other, a white tear between
    public static final int LINES = 9;      // ink with heavy focus lines converging on the hit

    public static final int[][] LIGHT = {{INK, 1}, {INVERT, 1}};
    public static final int[][] HEAVY = {{WHITEOUT, 1}, {INK, 1}, {INVERT, 1}, {CRIMSON, 2}, {LINES, 1}};
    public static final int[][] IAI = {{WHITEOUT, 1}, {SLASHED, 2}, {INVERT, 1}, {INK, 1}, {SLASHED, 1}, {INVERT, 2}};
    public static final int[][] ULTIMATE = {{WHITEOUT, 1}, {INK, 1}, {INVERT, 1}, {SLASHED, 1}, {CRIMSON, 1}, {INVERT, 1},
            {LINES, 2}, {NEGATIVE, 1}, {INK, 1}, {HALFTONE, 1}, {WHITEOUT, 1}, {CRIMSON, 2}};
    public static final int[][] KING = {{WHITEOUT, 1}, {HAKI, 1}, {INVERT, 1}, {SLASHED, 1}, {HAKI, 1}, {CRIMSON, 1},
            {INVERT, 1}, {HAKI, 2}, {NEGATIVE, 1}, {LINES, 1}, {HALFTONE, 1}, {WHITEOUT, 1}, {HAKI, 2}, {INK, 1}};

    private Impact() {}

    public static int length(int[][] seq) {
        int n = 0;
        for (int[] s : seq) {
            n += s[1];
        }
        return n;
    }
}
