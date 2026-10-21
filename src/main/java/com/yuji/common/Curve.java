package com.yuji.common;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Paths of curving projectiles (Homing Piercing Blood): a quadratic arc that swings wide, then curls in. */
public final class Curve {
    private Curve() {}

    public static Vec3 at(Vec3 a, Vec3 b, long seed, int curve, double u) {
        if (curve <= 0) return a.lerp(b, u);
        Random r = new Random(seed * 977 + curve * 7919L);
        Vec3 ab = b.subtract(a);
        Vec3 side = ab.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize().scale((r.nextBoolean() ? 1 : -1) * (3 + r.nextDouble() * 7));
        Vec3 ctrl = a.add(ab.scale(0.45)).add(side).add(0, 2 + r.nextDouble() * 6, 0);
        double v = 1 - u;
        return a.scale(v * v).add(ctrl.scale(2 * v * u)).add(b.scale(u * u));
    }
}
