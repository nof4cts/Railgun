package com.firestar.rose.common;

import net.minecraft.world.phys.Vec3;

/** Requiem Rose's timing sheet: every number is a tick from the cast, read by both server and client. */
public final class RBeats {
    private RBeats() {}

    // Reap: a four-cut chain; the fourth is the heavy one
    public static final int REAP_HIT = 4;
    public static final int REAP_CHAIN = 18;

    // Crescent Reaper: thrown, flies out, turns, comes home
    public static final int CRESCENT_THROW = 8;
    public static final int CRESCENT_TURN = 22;
    public static final int CRESCENT_HOME = 34;
    public static final double CRESCENT_RANGE = 26.0;

    // Rose Tether: the lash flies, binds, drags the target in; a rising cut ends it
    public static final int TETHER_FIRE = 5;
    public static final int TETHER_BIND = 10;
    public static final int TETHER_YANK = 15;
    public static final int TETHER_ARRIVE = 21;
    public static final int TETHER_CUT = 24;
    public static final double TETHER_RANGE = 24.0;

    // Dusk Step
    public static final int STEP_VANISH = 3;
    public static final int STEP_CUT = 6;

    // Calamity Ray: the charge is timed to the charge-up sound, which peaks 4.7 s in
    public static final int RAY_FIRE = 94;
    public static final int RAY_END = 130;
    public static final double RAY_RANGE = 48.0;

    // Thousand Requiem: eight vanishing cuts round the target, every cut hanging in the air, then all of them burst
    public static final int[] REQUIEM_CUTS = {12, 16, 20, 24, 28, 32, 36, 40};
    public static final int REQUIEM_GATHER = 50;
    public static final int REQUIEM_BURST = 58;
    public static final double REQUIEM_RADIUS = 2.6;

    // Severance Rift: one vertical cut that tears the air open; it drinks in what is near and snaps shut
    public static final int RIFT_CUT = 26;
    public static final int RIFT_OPEN = 32;
    public static final int RIFT_CLOSE = 92;
    public static final int RIFT_SNAP = 96;
    public static final double RIFT_DIST = 7.0;
    public static final double RIFT_HEIGHT = 14.0;

    // Last Bloom: rise, the sky turns, the blade grows past the clouds, one cut, the land answers
    public static final int BLOOM_RISE_END = 30;
    public static final int BLOOM_SKY = 34;
    public static final int BLOOM_GROW = 64;
    public static final int BLOOM_HALO = 90;
    public static final int BLOOM_CUT = 128;
    public static final int BLOOM_IMPACT = 134;
    public static final int BLOOM_WAVE_END = 164;
    public static final double BLOOM_HEIGHT = 9.0;
    public static final double BLOOM_REACH = 42.0;

    // Ascension
    public static final int ASCEND_IGNITE = 98;

    public static int cardStart(RMove m) {
        return switch (m) {
            case RAY -> 26;
            case REQUIEM -> 2;
            case RIFT -> 4;
            case LAST_BLOOM -> 40;
            case ASCEND -> ASCEND_IGNITE + 6;
            default -> -1;
        };
    }

    public static int cardLength(RMove m) {
        return switch (m) {
            case RAY, REQUIEM, RIFT -> 30;
            case LAST_BLOOM, ASCEND -> 40;
            default -> 0;
        };
    }

    /** Where Thousand Requiem's cut {@code i} begins: round the target, each across from the last. */
    public static Vec3 requiemFrom(Vec3 targetFeet, int seed, int i) {
        double th = (seed & 1023) / 1023.0 * Math.PI * 2.0 + i * Math.toRadians(180 + 47);
        double h = 0.4 + (i % 3) * 0.7;
        return targetFeet.add(Math.cos(th) * REQUIEM_RADIUS, h, Math.sin(th) * REQUIEM_RADIUS);
    }

    /** Where cut {@code i} ends: straight through the target and out the far side. */
    public static Vec3 requiemTo(Vec3 targetFeet, int seed, int i) {
        Vec3 from = requiemFrom(targetFeet, seed, i);
        Vec3 c = targetFeet.add(0, 1.0, 0);
        return c.add(c.subtract(from).multiply(1, 0.4, 1));
    }

    /** The crescent's position at time t (it flies out along the aim and arcs home). */
    public static Vec3 crescentAt(Vec3 hand, Vec3 dir, Vec3 right, double t, Vec3 homeHand) {
        if (t < CRESCENT_THROW) {
            return hand;
        }
        if (t <= CRESCENT_TURN) {
            double k = (t - CRESCENT_THROW) / (double) (CRESCENT_TURN - CRESCENT_THROW);
            double e = 1 - (1 - k) * (1 - k);
            return hand.add(dir.scale(CRESCENT_RANGE * e)).add(right.scale(Math.sin(Math.PI * k) * 2.2));
        }
        double k = Math.min(1.0, (t - CRESCENT_TURN) / (double) (CRESCENT_HOME - CRESCENT_TURN));
        double e = k * k;
        Vec3 far = hand.add(dir.scale(CRESCENT_RANGE));
        return far.lerp(homeHand, e).add(right.scale(-Math.sin(Math.PI * k) * 3.0));
    }

    /** Lines spoken on screen. */
    public static String[][] lines(RMove m) {
        return switch (m) {
            case ASCEND -> new String[][]{{"18", "Mortal grace has its limits."}, {"60", "Then let me cut past them."}};
            case RAY -> new String[][]{{"30", "Be still."}};
            case RIFT -> new String[][]{{"8", "Even the air bleeds."}};
            case LAST_BLOOM -> new String[][]{{"10", "Every rose falls."}, {"96", "Fall with it."}};
            default -> new String[0][];
        };
    }
}
