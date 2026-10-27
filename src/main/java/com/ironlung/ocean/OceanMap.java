package com.ironlung.ocean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The blood ocean: a deterministic seabed plus the things resting on it.
 * Depth values are positive metres below the surface; world y = -depth.
 * Server and client build identical maps from the same seed.
 */
public final class OceanMap {
    public static final double MIN_SUB_DEPTH = 60.0;
    public static final double WARN_DEPTH = 195.0;
    public static final double CRUSH_DEPTH = 212.0;
    public static final double MAX_SUB_DEPTH = 240.0;

    public final long seed;
    public final int size;
    private final Noise base, trench, rock, detail;
    public final List<Poi> pois = new ArrayList<>();
    public final List<Poi> objectives = new ArrayList<>();
    public double startX, startZ;
    private boolean suppressRocks;

    public OceanMap(long seed, int size, int objectiveCount, int hiddenCount) {
        this.seed = seed;
        this.size = Math.max(500, size);
        this.base = new Noise(seed * 3 + 1);
        this.trench = new Noise(seed * 5 + 2);
        this.rock = new Noise(seed * 7 + 3);
        this.detail = new Noise(seed * 11 + 4);
        Random rnd = new Random(seed ^ 0x5DEECE66DL);
        findStart(rnd);
        placePois(rnd, Math.max(1, objectiveCount), Math.max(0, hiddenCount));
        suppressRocks = true;
        for (Poi p : pois) p.baseY = floorY(p.x, p.z) + 0.4;
    }

    private void findStart(Random rnd) {
        double cx = size / 2.0, cz = size / 2.0;
        for (int i = 0; i < 400; i++) {
            double a = i * 0.7, r = i * 1.5;
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
            if (rockAmount(x, z, 14) < 0.01 && floorDepth(x, z) > 150) {
                startX = x;
                startZ = z;
                return;
            }
        }
        startX = cx;
        startZ = cz;
    }

    private double rockAmount(double x, double z, double radius) {
        double m = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            m = Math.max(m, rockRaw(x + Math.cos(a) * radius, z + Math.sin(a) * radius));
        }
        return Math.max(m, rockRaw(x, z));
    }

    private double rockRaw(double x, double z) {
        double n = rock.fbm(x * 0.028, z * 0.028, 3);
        return Math.max(0, n - 0.30);
    }

    private void placePois(Random rnd, int objectiveCount, int hiddenCount) {
        List<PoiType> types = new ArrayList<>();
        while (types.size() < objectiveCount) {
            List<PoiType> batch = new ArrayList<>(List.of(PoiType.CATALOGUED));
            Collections.shuffle(batch, rnd);
            types.addAll(batch);
        }
        int idx = 0;
        for (int i = 0; i < objectiveCount; i++) {
            Poi p = tryPlace(rnd, idx, types.get(i), true, 130);
            if (p != null) {
                pois.add(p);
                objectives.add(p);
                idx++;
            }
        }
        for (int i = 0; i < hiddenCount; i++) {
            PoiType t = PoiType.HIDDEN[rnd.nextInt(PoiType.HIDDEN.length)];
            Poi p = tryPlace(rnd, idx, t, false, 90);
            if (p != null) {
                pois.add(p);
                idx++;
            }
        }
    }

    private Poi tryPlace(Random rnd, int idx, PoiType type, boolean catalogued, double spacing) {
        double margin = 120;
        for (int attempt = 0; attempt < 300; attempt++) {
            double x = margin + rnd.nextDouble() * (size - margin * 2);
            double z = margin + rnd.nextDouble() * (size - margin * 2);
            double fd = floorDepth(x, z);
            if (fd < 140 || fd > 206) continue;
            if (rockRaw(x, z) > 0.01) continue;
            if (dist(x, z, startX, startZ) < 110) continue;
            boolean ok = true;
            for (Poi o : pois) {
                if (dist(x, z, o.x, o.z) < spacing) {
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;
            double scale = 0.9 + rnd.nextDouble() * 0.5;
            if (type == PoiType.CARCASS) scale += 0.4;
            Poi p = new Poi(idx, type, x, z, rnd.nextDouble() * Math.PI * 2, scale, rnd.nextLong(), catalogued);
            p.baseY = -fd + 0.4;
            return p;
        }
        return null;
    }

    private static double dist(double ax, double az, double bx, double bz) {
        double dx = ax - bx, dz = az - bz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Depth of the sea floor (positive metres) at a survey coordinate. */
    public double floorDepth(double x, double z) {
        double d = 158 + 24 * base.fbm(x * 0.0035, z * 0.0035, 4);
        double tr = trench.ridged(x * 0.0022, z * 0.0022, 2);
        d += Math.pow(Math.max(0, tr - 0.55) / 0.45, 2.0) * 48.0;
        double r = rockRaw(x, z);
        if (r > 0) {
            double rockH = Math.min(1.0, r / 0.35);
            rockH = rockH * rockH * (3 - 2 * rockH);
            double amount = rockH * 95;
            if (suppressRocks) {
                for (Poi p : objectives) {
                    double dd = dist(x, z, p.x, p.z);
                    if (dd < 40) amount *= Sdf.smoothstep(18, 40, dd);
                }
                double ds = dist(x, z, startX, startZ);
                if (ds < 30) amount *= Sdf.smoothstep(12, 30, ds);
            }
            d -= amount;
        }
        d += 1.6 * detail.noise(x * 0.35, z * 0.35) + 0.6 * detail.noise(x * 1.3, z * 1.3);
        double edge = Math.min(Math.min(x, z), Math.min(size - x, size - z));
        if (edge < 90) {
            double t = Sdf.smoothstep(0, 90, edge);
            d = 30 + (d - 30) * t;
        }
        return d;
    }

    public double floorY(double x, double z) {
        return -floorDepth(x, z);
    }

    /** Sediment variation in [0,1] used for floor shading. */
    public double sediment(double x, double z) {
        return 0.5 + 0.5 * detail.fbm(x * 0.08 + 100, z * 0.08 - 50, 2);
    }

    public List<Poi> poisNear(double x, double z, double range) {
        List<Poi> out = new ArrayList<>();
        for (Poi p : pois) {
            if (dist(x, z, p.x, p.z) < range + p.radius) out.add(p);
        }
        return out;
    }

    /** Line of sight check using the floor only (objects ignored). y up. */
    public boolean lineOfSight(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.max(2, len / 1.5);
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            double x = ax + dx * t, y = ay + dy * t, z = az + dz * t;
            if (y < floorY(x, z) - 0.5) return false;
        }
        return true;
    }
}
