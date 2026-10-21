package com.yuji.common;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where a beat happens. The same rules on the server and on every client, read at the moment the beat fires,
 * so a blow aimed at a target lands on the target wherever it has been knocked to.
 */
public final class Anchors {
    private Anchors() {}

    public static Entity entity(Level level, int id) {
        return id < 0 || level == null ? null : level.getEntity(id);
    }

    public static Vec3 centre(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** The point of anchor {@code a} plus offset {@code off}. */
    public static Vec3 at(Level level, Ctx c, int casterId, int a, Vec3 off) {
        Entity caster = entity(level, casterId);
        switch (a) {
            case Beat.CASTER:
                return (caster != null ? caster.position() : c.origin()).add(off);
            case Beat.EYE:
                return (caster != null ? caster.getEyePosition() : c.origin().add(0, 1.6, 0)).add(off);
            case Beat.HAND: {
                Vec3 eye = caster != null ? caster.getEyePosition() : c.origin().add(0, 1.6, 0);
                Vec3 look = caster != null ? caster.getLookAngle() : c.dir();
                Vec3 flat = new Vec3(look.x, 0, look.z);
                flat = flat.lengthSqr() < 1e-6 ? c.f() : flat.normalize();
                Vec3 right = new Vec3(-flat.z, 0, flat.x);
                return eye.add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.4, 0).add(off);
            }
            case Beat.START:
                return c.origin().add(off);
            case Beat.DOMAIN:
                return c.domainCentre().add(off);
            case Beat.POINT:
                return c.point().add(off);
            case Beat.GROUND:
                return ground(level, target(level, c, c.targetId()).add(off));
            case Beat.TARGET:
                return target(level, c, c.targetId()).add(off);
            default:
                if (a >= Beat.T0) {
                    int k = a - Beat.T0;
                    int id = k < c.targets().length ? c.targets()[k] : -1;
                    return target(level, c, id >= 0 ? id : c.targetId()).add(off);
                }
                return c.point().add(off);
        }
    }

    private static Vec3 target(Level level, Ctx c, int id) {
        Entity t = entity(level, id);
        return t != null && t.isAlive() ? centre(t) : c.point();
    }

    /** The ground under (or around) p: the first solid surface within a few blocks above and many below. */
    public static Vec3 ground(Level level, Vec3 p) {
        if (level == null) return p;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos(Mth.floor(p.x), Mth.floor(p.y) + 2, Mth.floor(p.z));
        int min = Math.max(level.getMinBuildHeight(), q.getY() - 48);
        for (int y = q.getY(); y >= min; y--) {
            q.setY(y);
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
                return new Vec3(p.x, y + 1.0, p.z);
            }
        }
        return new Vec3(p.x, p.y - 1.0, p.z);
    }

    /** The side direction of a slash travelling along d, rolled by roll degrees (0 = flat, 90 = vertical). */
    public static Vec3 side(Vec3 d, double rollDeg) {
        Vec3 right = d.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(d).normalize();
        double r = Math.toRadians(rollDeg);
        return right.scale(Math.cos(r)).add(up.scale(Math.sin(r))).normalize();
    }
}
