package com.firestar.rose.client;

import com.firestar.rose.common.RMove;
import com.firestar.rose.net.RoseNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** One technique as this client plays it: where it was cast, at what, and its clock (server game time). */
public final class RoseCast {
    public final RMove move;
    public final int casterId;
    public final long start;
    public final int seed;
    public final Vec3 eye, feet, dir, fwd, right, point, exit;
    public final int targetId;
    public final boolean mine;
    int lastTick = -1;

    RoseCast(RoseNet.Play m) {
        move = m.move();
        casterId = m.caster();
        start = m.start();
        seed = m.seed();
        eye = m.eye();
        feet = eye.subtract(0, 1.62, 0);
        dir = m.dir();
        Vec3 f = new Vec3(dir.x, 0, dir.z);
        fwd = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
        right = new Vec3(-fwd.z, 0, fwd.x);
        point = m.point();
        exit = m.exit();
        targetId = m.target();
        Minecraft mc = Minecraft.getInstance();
        mine = mc.player != null && mc.player.getId() == casterId;
    }

    public float time(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : (float) (level.getGameTime() - start) + partial;
    }

    public boolean over() {
        return time(0) > move.length + 2;
    }

    public Entity caster() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(casterId);
    }

    public LivingEntity target() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null || targetId < 0 || !(level.getEntity(targetId) instanceof LivingEntity l) ? null : l;
    }

    public boolean hasTarget() {
        return targetId >= 0;
    }

    public Vec3 targetCentre(float partial) {
        LivingEntity t = target();
        return t == null ? point : t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
    }

    public Vec3 targetFeet(float partial) {
        LivingEntity t = target();
        return t == null ? exit : t.getPosition(partial);
    }

    public Vec3 casterFeet(float partial) {
        Entity e = caster();
        return e != null ? e.getPosition(partial) : feet;
    }

    /** The reaper's facing now (it can change mid-technique when he is carried round a target). */
    public Vec3 facing(float partial) {
        Entity e = caster();
        if (e == null) {
            return fwd;
        }
        Vec3 v = e.getViewVector(partial);
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-4 ? fwd : f.normalize();
    }

    public boolean isCaster(Player p) {
        return p.getId() == casterId;
    }

    /** A point in the reaper's frame at the cast: right, up, forward from his feet. */
    public Vec3 local(double r, double u, double f) {
        return feet.add(right.scale(r)).add(0, u, 0).add(fwd.scale(f));
    }

    /** The hand that throws: a little below the eyes, out along the aim. */
    public Vec3 hand(float partial) {
        Entity e = caster();
        Vec3 eyeNow = e != null ? e.getEyePosition(partial) : eye;
        return eyeNow.subtract(0, 0.3, 0).add(dir.scale(0.8));
    }
}
