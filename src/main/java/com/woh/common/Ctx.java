package com.woh.common;

import net.minecraft.world.phys.Vec3;

/** Everything a move's timeline is built from: identical on the server and every client. */
public record Ctx(WMove move, long seed, Vec3 origin, Vec3 dir, Vec3 point, int targetId, int[] targets,
                  Vec3 domainCentre, float domainRadius, float worldEnder, float splitter, float dismantle,
                  boolean overdrive, float destructionScale) {

    /** Flat forward. */
    public Vec3 f() {
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
    }

    /** Flat right. */
    public Vec3 r() {
        Vec3 f = f();
        return new Vec3(-f.z, 0, f.x);
    }

    public int targetCount() {
        int n = 0;
        for (int t : targets) if (t >= 0) n++;
        return n;
    }

    /** Destruction grows with Overdrive. */
    public double ds() {
        return destructionScale * (overdrive ? 1.35 : 1.0);
    }
}
