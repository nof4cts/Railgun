package com.firestar.silver.common;

import net.minecraft.world.phys.Vec3;

/** The timing sheet. Every number is a tick from the cast; server and client both read it. */
public final class Beats {
    private Beats() {}

    public static final int BLEND_IN = 6;
    public static final int BLEND_OUT = 12;

    public static final int STRIKE_HIT = 3;
    public static final int HEAVY_HIT = 9;

    public static final int VOLLEY_FIRST = 5;
    public static final int VOLLEY_SHOTS = 14;
    public static final int VOLLEY_GAP = 2;
    public static final double BLAST_SPEED = 2.6;
    public static final double BLAST_RANGE = 40.0;

    public static final int VANISH_AT = 4;

    public static final int WAVE_FIRE = 34;
    public static final int WAVE_END = 66;
    public static final double WAVE_RANGE = 42.0;

    public static final int FLURRY_DASH = 10;
    public static final int FLURRY_FIRST = 13;
    public static final int FLURRY_LAST = 40;
    public static final int FLURRY_FINAL = 44;

    public static final int METEOR_LAUNCH = 8;
    public static final int METEOR_UP = 13;
    public static final int[] METEOR_HITS = {15, 18, 21, 24, 27};
    public static final int METEOR_SPIKE = 34;
    public static final int METEOR_SLAM = 42;

    public static final int[] RUSH_DASHES = {12, 15, 18, 21, 24, 27, 30, 33, 36, 39};
    public static final int RUSH_FINAL = 48;
    public static final double RUSH_RADIUS = 2.4;

    public static final int SPHERE_THROW = 92;
    public static final int SPHERE_FLIGHT = 14;
    public static final int SPHERE_BOOM = SPHERE_THROW + SPHERE_FLIGHT;
    public static final double SPHERE_HEIGHT = 9.0;

    public static final int FINAL_FIRE = 70;
    public static final int FINAL_END = 112;
    public static final double FINAL_RANGE = 60.0;

    public static final int OMEN_SURGE = 64;
    public static final int ZENITH_SURGE = 96;

    public static int cardStart(Move m) {
        return switch (m) {
            case WAVE, FLURRY, METEOR, RUSH -> 2;
            case SPHERE -> 10;
            case FINAL -> 14;
            case AWAKEN_OMEN -> OMEN_SURGE + 4;
            case AWAKEN_ZENITH -> ZENITH_SURGE + 6;
            default -> -1;
        };
    }

    public static int cardLength(Move m) {
        return switch (m) {
            case WAVE, FLURRY, METEOR, RUSH -> 18;
            case SPHERE, FINAL -> 34;
            case AWAKEN_OMEN, AWAKEN_ZENITH -> 34;
            default -> 0;
        };
    }

    /** Where rush dash {@code i} lands: round the target, each across from the last. */
    public static Vec3 rushDash(Vec3 targetFeet, int seed, int i) {
        double th0 = (seed & 1023) / 1023.0 * Math.PI * 2.0;
        double th = th0 + i * Math.toRadians(180 + 61);
        return targetFeet.add(Math.cos(th) * RUSH_RADIUS, (i % 3) * 0.6, Math.sin(th) * RUSH_RADIUS);
    }

    /** The direction of blast {@code i} of a volley: the aim, spread a little by the seed. */
    public static Vec3 blastDir(Vec3 dir, int seed, int i) {
        java.util.Random r = new java.util.Random(seed * 131L + i * 7L);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = dir.cross(up).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : dir.cross(up).normalize();
        Vec3 vert = side.cross(dir).normalize();
        double a = (r.nextDouble() - 0.5) * 0.12, b = (r.nextDouble() - 0.5) * 0.08;
        return dir.add(side.scale(a)).add(vert.scale(b)).normalize();
    }

    /** Lines spoken on screen during the cutscenes (start tick, line). */
    public static String[][] lines(Move m) {
        return switch (m) {
            case AWAKEN_OMEN -> new String[][]{{"20", "...Something is moving."}, {"44", "Before I even think."}};
            case AWAKEN_ZENITH -> new String[][]{{"24", "No more thinking."}, {"56", "My body already knows."}, {"118", "This is what lies past the limit."}};
            case SPHERE -> new String[][]{{"30", "Everything around me... lend me your energy."}};
            case FINAL -> new String[][]{{"30", "All of it. Into one strike."}, {"118", "It's over."}};
            case WAVE -> new String[][]{{"10", "Ha..."}};
            default -> new String[0][];
        };
    }
}
