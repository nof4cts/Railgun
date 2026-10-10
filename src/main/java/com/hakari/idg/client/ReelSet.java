package com.hakari.idg.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Three slot reels shared by the riichi HUD and the giant machine floating in the domain sky,
 * so both always show the same numbers. Positions are in "faces"; face i shows number i+1.
 */
public final class ReelSet {
    private static final Map<Integer, ReelSet> BY_CASTER = new HashMap<>();

    public static ReelSet of(int casterId) {
        return BY_CASTER.computeIfAbsent(casterId, k -> new ReelSet());
    }

    public static void clear() {
        BY_CASTER.clear();
    }

    private final long[] stopAt = new long[3];
    private final int[] target = {6, 6, 6};
    private final boolean[] slowLast = new boolean[3];
    private long spinStart = -1;

    /** Spin all reels now, stopping at the given millisecond offsets on the given numbers (1-7). */
    public void spin(int n1, int n2, int n3, long s1, long s2, long s3, boolean dramaticLast) {
        long now = Vfx.now();
        spinStart = now;
        target[0] = n1 - 1; target[1] = n2 - 1; target[2] = n3 - 1;
        stopAt[0] = now + s1; stopAt[1] = now + s2; stopAt[2] = now + s3;
        slowLast[2] = dramaticLast;
    }

    public boolean spinning(long t) {
        return spinStart >= 0 && t < stopAt[2];
    }

    public boolean stopped(int reel, long t) {
        return spinStart < 0 || t >= stopAt[reel];
    }

    /** Continuous reel position in faces (fractional while spinning, with a bounce on stop). */
    public double position(int reel, long t) {
        if (spinStart < 0) return target[reel];
        long d = stopAt[reel] - t;
        if (d > 0) {
            double faces = d / 85.0;
            if (slowLast[reel]) faces *= 0.22 + 0.78 * Math.min(1.0, d / 1700.0);
            return target[reel] - faces;
        }
        double k = -d / 260.0;
        return target[reel] + 0.18 * Math.exp(-k * 4) * Math.sin(k * 16);
    }

    public static int numberAt(double pos) {
        int idx = (int) Math.floor(pos + 0.5);
        return ((idx % 7) + 7) % 7 + 1;
    }

    public int finalNumber(int reel) {
        return target[reel] + 1;
    }

    public static int numberColor(int n) {
        return switch (n) {
            case 7 -> 0xFFFF2A3A;
            case 1 -> 0xFF4FC3FF;
            case 3 -> 0xFFFFD34D;
            case 5 -> 0xFF7CFF6B;
            case 2 -> 0xFFFF8AD8;
            case 4 -> 0xFFB48CFF;
            default -> 0xFFFFFFFF;
        };
    }
}
