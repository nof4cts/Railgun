package com.teengojo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Toji's dashing attacks (Heaven's Pierce): the dash carried him past anyone standing close before the hit was checked,
 * so close targets were never struck. Now the hit runs along everything he dashed through, from where the dash
 * began to where he is.
 */
public final class TojiFix {
    private static final Map<UUID, Object[]> START = new HashMap<>();

    private TojiFix() {}

    /** Called as a dashing attack begins. */
    public static void start(ServerPlayer p) {
        START.put(p.getUUID(), new Object[]{p.position(), p.level().getGameTime()});
    }

    /** Everyone within {@code reach} of the path he has dashed along this attack, nearest first. */
    public static List<LivingEntity> pathTargets(ServerPlayer p, double reach) {
        Vec3 now = p.position();
        Vec3 from = now;
        Object[] s = START.get(p.getUUID());
        if (s != null && p.level().getGameTime() - (Long) s[1] <= 40) {
            from = (Vec3) s[0];
        }
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
        Vec3 to = now.add(flat.scale(0.6));
        final Vec3 a = from, b = to;
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(reach + 2),
                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            Vec3 c = e.getBoundingBox().getCenter();
            // measured at his body's height, so the low point of the path doesn't miss a standing target
            Vec3 lift = new Vec3(0, Mth.clamp(c.y - a.y, 0, 1.2), 0);
            if (TeenGojo.segment(c, a.add(lift), b.add(lift)) - e.getBbWidth() * 0.5 <= reach) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return out;
    }
}
