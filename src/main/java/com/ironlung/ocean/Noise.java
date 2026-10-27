package com.ironlung.ocean;

/** Deterministic 2D gradient noise. Pure Java, shared by server and client so both see the same ocean. */
public final class Noise {
    private final long seed;

    public Noise(long seed) {
        this.seed = seed;
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private long hash(int x, int z) {
        return mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL));
    }

    /** Random value in [-1,1] for an integer lattice point. */
    public double rand(int x, int z) {
        return ((hash(x, z) >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private double grad(int ix, int iz, double dx, double dz) {
        int h = (int) (hash(ix, iz) & 7);
        switch (h) {
            case 0: return dx + dz;
            case 1: return dx - dz;
            case 2: return -dx + dz;
            case 3: return -dx - dz;
            case 4: return dx;
            case 5: return -dx;
            case 6: return dz;
            default: return -dz;
        }
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** Gradient noise, roughly in [-1,1]. */
    public double noise(double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        double u = fade(fx), v = fade(fz);
        double a = grad(ix, iz, fx, fz);
        double b = grad(ix + 1, iz, fx - 1, fz);
        double c = grad(ix, iz + 1, fx, fz - 1);
        double d = grad(ix + 1, iz + 1, fx - 1, fz - 1);
        double ab = a + u * (b - a);
        double cd = c + u * (d - c);
        return (ab + v * (cd - ab)) * 0.9;
    }

    public double fbm(double x, double z, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x * f + i * 17.31, z * f - i * 9.17) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return sum / norm;
    }

    /** Ridged noise in [0,1], peaks along "valleys" of the base noise. */
    public double ridged(double x, double z, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(noise(x * f + i * 3.7, z * f + i * 5.1));
            sum += n * n * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.1;
        }
        return sum / norm;
    }
}
