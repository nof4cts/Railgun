package com.cataclysm.spells.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A keyframed camera rig. Positions and look-targets are in the anchor's local frame
 * (f = forward, r = right, u = up, metres, from the anchor's eyes) so a shot reads the
 * same wherever and whichever way the caster is facing. A key flagged {@code cut}
 * is a hard cut — the camera jumps instead of gliding.
 */
public final class Cutscene {
    public record Key(double t, Vec3 pos, Vec3 look, double fov, double roll, Ease ease, boolean cut) {}

    private static final class Ev {
        final double t;
        final Runnable run;
        boolean fired;

        Ev(double t, Runnable run) {
            this.t = t;
            this.run = run;
        }
    }

    public final int anchorId;
    public final double duration;
    public final boolean follow;
    private final List<Key> keys = new ArrayList<>();
    private final List<Ev> events = new ArrayList<>();

    long start;
    Vec3 anchorStart = Vec3.ZERO;
    float yawStart;

    // evaluated output
    Vec3 outPos = Vec3.ZERO;
    float outYaw, outPitch, outRoll;
    double outFov = 70;

    public Cutscene(int anchorId, double duration, boolean follow) {
        this.anchorId = anchorId;
        this.duration = duration;
        this.follow = follow;
    }

    public Cutscene key(double t, double f, double r, double u, double lf, double lr, double lu, double fov, double roll, Ease ease) {
        keys.add(new Key(t, new Vec3(f, r, u), new Vec3(lf, lr, lu), fov, roll, ease, false));
        return this;
    }

    public Cutscene cut(double t, double f, double r, double u, double lf, double lr, double lu, double fov, double roll) {
        keys.add(new Key(t, new Vec3(f, r, u), new Vec3(lf, lr, lu), fov, roll, Ease.LINEAR, true));
        return this;
    }

    public Cutscene at(double t, Runnable r) {
        events.add(new Ev(t, r));
        return this;
    }

    private Vec3 fixedOrigin;
    private float fixedYaw;

    /** Anchor the rig to a fixed world point and heading instead of an entity's eyes. */
    public Cutscene fixed(Vec3 origin, float yaw) {
        fixedOrigin = origin;
        fixedYaw = yaw;
        return this;
    }

    void begin() {
        start = Vfx.now();
        Entity a = anchor();
        if (fixedOrigin != null) {
            anchorStart = fixedOrigin;
            yawStart = fixedYaw;
        } else if (a != null) {
            anchorStart = a.getEyePosition(1f);
            yawStart = a.getYRot();
        }
    }

    Entity anchor() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getEntity(anchorId);
    }

    double time() {
        return (Vfx.now() - start) / 1000.0;
    }

    boolean done() {
        return time() >= duration;
    }

    private Vec3 local(Vec3 origin, Vec3 frl) {
        float yaw = yawStart * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 r = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return origin.add(f.scale(frl.x)).add(r.scale(frl.y)).add(0, frl.z, 0);
    }

    /** Evaluate the rig at the current wall-clock time and fire any due events. */
    void update(float partial) {
        double t = time();
        for (Ev e : events) {
            if (!e.fired && t >= e.t) {
                e.fired = true;
                e.run.run();
            }
        }
        Vec3 origin = anchorStart;
        if (follow) {
            Entity a = anchor();
            if (a != null) origin = a.getEyePosition(partial);
        }
        if (keys.isEmpty()) return;
        Key a = keys.get(0), b = a;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).t <= t) a = keys.get(i);
            if (keys.get(i).t > t) {
                b = keys.get(i);
                break;
            }
            b = keys.get(i);
        }
        Vec3 pos, look;
        double fov, roll;
        if (a == b || b.cut) {
            pos = a.pos; look = a.look; fov = a.fov; roll = a.roll;
        } else {
            float k = b.ease.apply((float) ((t - a.t) / Math.max(1e-6, b.t - a.t)));
            pos = a.pos.lerp(b.pos, k);
            look = a.look.lerp(b.look, k);
            fov = Mth.lerp(k, a.fov, b.fov);
            roll = Mth.lerp(k, a.roll, b.roll);
        }
        outPos = local(origin, pos);
        Vec3 target = local(origin, look);
        Vec3 d = target.subtract(outPos);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        outYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        outPitch = (float) (-(Mth.atan2(d.y, horiz) * Mth.RAD_TO_DEG));
        outFov = fov;
        outRoll = (float) roll;
    }
}
