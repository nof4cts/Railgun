package com.hakari.idg.client.post;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.world.phys.Vec3;

/**
 * What Hakari asks of the final picture: anime impact frames held on a timing sheet, flashes, punch-ins, aberration
 * kicks and the cursed-energy grade. Everything is scheduled on the real-time clock the rest of Hakari's effects use,
 * and rebuilt into one {@link Frame} each rendered frame.
 */
public final class Post {
    // impact frame looks (match composite.fsh)
    public static final int INK = 1, INVERT = 2, CRIMSON = 3, NEGATIVE = 4, WHITEOUT = 5, HALFTONE = 6, GRADE = 7, SLASHED = 8,
            LINES = 9, RAINBOW = 10, JACKPOT = 11;

    /** Timing sheets: (look, frames held), one frame = 50 ms like a game tick. */
    public static final int[][] LIGHT = {{INK, 1}, {INVERT, 1}};
    public static final int[][] HEAVY = {{WHITEOUT, 1}, {INK, 1}, {INVERT, 1}, {JACKPOT, 2}, {LINES, 1}};
    public static final int[][] DOMAIN = {{WHITEOUT, 1}, {INK, 1}, {INVERT, 1}, {HALFTONE, 1}, {LINES, 2}, {NEGATIVE, 1}, {INVERT, 1}};
    public static final int[][] TITLE = {{WHITEOUT, 1}, {RAINBOW, 2}, {INVERT, 1}, {RAINBOW, 1}, {SLASHED, 1}, {RAINBOW, 2}, {LINES, 1}};
    public static final int[][] CONFIRM = {{RAINBOW, 2}, {WHITEOUT, 1}, {RAINBOW, 2}, {INVERT, 1}, {RAINBOW, 2}};
    public static final int[][] JACKPOT_HIT = {{WHITEOUT, 1}, {JACKPOT, 1}, {INVERT, 1}, {RAINBOW, 1}, {JACKPOT, 1}, {SLASHED, 1},
            {RAINBOW, 2}, {NEGATIVE, 1}, {JACKPOT, 2}, {LINES, 1}, {WHITEOUT, 1}, {RAINBOW, 2}};
    public static final int[][] FINISHER = {{WHITEOUT, 1}, {JACKPOT, 1}, {INVERT, 1}, {SLASHED, 1}, {JACKPOT, 2}, {LINES, 1}};

    public static final int GREEN = 0x39FF7A;
    public static final int PINK = 0xFF4FB8;
    public static final int GOLD = 0xFFD34D;

    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<Pulse> PULSES = new ArrayList<>();
    private static float gradeTarget, gradeNow;
    private static float bloomTarget, bloomNow;
    private static float vignetteTarget, vignetteNow;
    private static int vignetteRgb;

    private Post() {}

    private record Impact(long start, int[][] seq, Vec3 at, int accent) {}

    /** A short-lived kick: flash, punch, aberration or shake that peaks at once and decays. */
    private record Pulse(long start, long life, int kind, float amount, int rgb) {}

    private static final int FLASH = 0, PUNCH = 1, ABERRATION = 2, DESAT = 3;

    static long now() {
        return Util.getMillis();
    }

    public static void impact(int[][] seq, Vec3 at, int accent) {
        IMPACTS.add(new Impact(now(), seq, at, accent));
    }

    public static void flash(int rgb, float amount, long life) {
        PULSES.add(new Pulse(now(), life, FLASH, amount, rgb));
    }

    public static void punch(float amount, long life) {
        PULSES.add(new Pulse(now(), life, PUNCH, amount, 0));
    }

    public static void aberration(float amount, long life) {
        PULSES.add(new Pulse(now(), life, ABERRATION, amount, 0));
    }

    public static void desaturate(float amount, long life) {
        PULSES.add(new Pulse(now(), life, DESAT, amount, 0));
    }

    /** Standing looks, eased toward their targets: the jackpot's green grade, neon bloom, a coloured vignette. */
    public static void grade(float target) {
        gradeTarget = target;
    }

    public static void bloom(float target) {
        bloomTarget = target;
    }

    public static void vignette(int rgb, float target) {
        vignetteRgb = rgb;
        vignetteTarget = target;
    }

    public static void clear() {
        IMPACTS.clear();
        PULSES.clear();
        gradeTarget = gradeNow = bloomTarget = bloomNow = vignetteTarget = vignetteNow = 0.0F;
    }

    /** The picture this frame. */
    public static final class Frame {
        public float flash, flashR = 1, flashG = 1, flashB = 1;
        public float punch, aberration, desaturate, grade, bloom, vignette, vr, vg, vb;
        public int impactMode;
        public float impactSeed;
        public Vec3 impactAt;
        public float ar = 0.22F, ag = 1.0F, ab = 0.48F;

        public boolean idle() {
            return flash < 1e-3F && punch < 1e-3F && aberration < 1e-3F && desaturate < 1e-3F && grade < 1e-3F && bloom < 1e-3F
                    && vignette < 1e-3F && impactMode == 0;
        }
    }

    private static final Frame FRAME = new Frame();
    private static long last = now();

    static Frame frame() {
        long t = now();
        float dt = Math.min(0.1F, (t - last) / 1000.0F);
        last = t;
        float k = 1.0F - (float) Math.exp(-dt * 4.0F);
        gradeNow += (gradeTarget - gradeNow) * k;
        bloomNow += (bloomTarget - bloomNow) * k;
        vignetteNow += (vignetteTarget - vignetteNow) * k;

        Frame f = FRAME;
        f.flash = f.punch = f.aberration = f.desaturate = 0.0F;
        f.flashR = f.flashG = f.flashB = 1.0F;
        f.impactMode = 0;
        f.impactAt = null;
        f.grade = gradeNow;
        f.bloom = bloomNow;
        f.vignette = vignetteNow;
        f.vr = (vignetteRgb >> 16 & 255) / 255.0F;
        f.vg = (vignetteRgb >> 8 & 255) / 255.0F;
        f.vb = (vignetteRgb & 255) / 255.0F;

        Iterator<Pulse> pi = PULSES.iterator();
        while (pi.hasNext()) {
            Pulse p = pi.next();
            float age = (t - p.start) / (float) Math.max(1L, p.life);
            if (age >= 1.0F) {
                pi.remove();
                continue;
            }
            float v = p.amount * (float) Math.pow(1.0F - age, 2.2);
            switch (p.kind) {
                case FLASH -> {
                    if (v > f.flash) {
                        f.flash = v;
                        f.flashR = (p.rgb >> 16 & 255) / 255.0F;
                        f.flashG = (p.rgb >> 8 & 255) / 255.0F;
                        f.flashB = (p.rgb & 255) / 255.0F;
                    }
                }
                case PUNCH -> f.punch = Math.max(f.punch, v);
                case ABERRATION -> f.aberration = Math.max(f.aberration, v);
                default -> f.desaturate = Math.max(f.desaturate, v);
            }
        }

        Iterator<Impact> ii = IMPACTS.iterator();
        while (ii.hasNext()) {
            Impact im = ii.next();
            int tick = (int) ((t - im.start) / 50L);
            int acc = 0;
            boolean live = false;
            for (int i = 0; i < im.seq.length; i++) {
                if (tick < acc + im.seq[i][1]) {
                    f.impactMode = im.seq[i][0];
                    f.impactSeed = i * 17.31F + acc + (im.start % 97);
                    f.impactAt = im.at;
                    f.ar = (im.accent >> 16 & 255) / 255.0F;
                    f.ag = (im.accent >> 8 & 255) / 255.0F;
                    f.ab = (im.accent & 255) / 255.0F;
                    live = true;
                    break;
                }
                acc += im.seq[i][1];
            }
            if (!live) {
                ii.remove();
            }
        }
        return f;
    }
}
