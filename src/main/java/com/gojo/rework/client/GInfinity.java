package com.gojo.rework.client;

import com.gojo.rework.client.fx.Ink;
import com.gojo.rework.net.GNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Infinity, seen: a barely-there boundary round him that shimmers as it turns, slow rings spreading over the ground
 * at his feet, rings stacked up his body when it's raised, and ripples over the boundary wherever something is
 * stopped by it.
 */
public final class GInfinity {
    private static final Map<Integer, Long> ON = new HashMap<>();
    private static final List<Object[]> RIPPLES = new ArrayList<>();

    private GInfinity() {}

    public static void state(GNet.Inf m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (m.on()) {
            ON.put(m.entity(), m.burst() ? mc.level.getGameTime() : mc.level.getGameTime() - 1000);
        } else {
            ON.remove(m.entity());
        }
    }

    public static void ripple(GNet.Ripple m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            RIPPLES.add(new Object[]{m.at(), m.normal(), mc.level.getGameTime()});
            if (RIPPLES.size() > 40) {
                RIPPLES.remove(0);
            }
        }
    }

    public static boolean on(Entity e) {
        return e != null && ON.containsKey(e.getId());
    }

    static void clear() {
        ON.clear();
        RIPPLES.clear();
    }

    static void draw(Ink ink, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = mc.level.getGameTime() + partial;
        for (Map.Entry<Integer, Long> en : ON.entrySet()) {
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || e.isInvisible()) {
                continue;
            }
            Vec3 feet = e.getPosition(partial);
            Vec3 mid = feet.add(0, e.getBbHeight() * 0.5, 0);
            float age = (float) (now - en.getValue());
            boolean me = e == mc.player && mc.options.getCameraType().isFirstPerson();
            // raised: rings stacked up the body, flickering out, and one wide wave over the ground
            if (age < 24) {
                float k = age / 24F;
                for (int i = 0; i < 6; i++) {
                    float flick = ((int) (age * 1.5F) + i) % 3 == 0 ? 0.4F : 1F;
                    double y = 0.15 + i * 0.36;
                    ink.ring(feet.add(0, y, 0), GFx.UP, 0.7 + 1.6 * k + i * 0.05, 0.07, GFx.a(i % 2 == 0 ? GFx.BLUE : GFx.BLUE_CORE, 0.85F * (1 - k) * flick), 48);
                }
                ink.ring(feet.add(0, 0.1, 0), GFx.UP, 1 + 9 * k, 0.25 * (1 - k) + 0.05, GFx.a(GFx.BLUE_CORE, 0.8F * (1 - k)), 96);
                ink.glow(mid, 2.5 * (1 - k), GFx.a(GFx.BLUE_CORE, 0.5F * (1 - k)));
            }
            if (!me) {
                // the boundary: three thin great circles, turning, almost invisible
                for (int i = 0; i < 3; i++) {
                    double a = now * 0.02 + i * 2.1;
                    Vec3 n = new Vec3(Math.cos(a), 0.6 + 0.3 * Math.sin(a * 0.7 + i), Math.sin(a)).normalize();
                    ink.ring(mid, n, 1.45, 0.025, GFx.a(GFx.BLUE_CORE, 0.22F), 48);
                }
                ink.ring(mid, ink.cam().subtract(mid), 1.5, 0.04, GFx.a(0xFF9FD0FF, 0.18F + 0.06F * (float) Math.sin(now * 0.15)), 64);
            }
            // slow rings spreading over the ground at his feet
            for (int i = 0; i < 2; i++) {
                float u = (float) (((now / 40.0) + i * 0.5) % 1.0);
                ink.ring(feet.add(0, 0.06, 0), GFx.UP, 0.5 + 2.8 * u, 0.04, GFx.a(GFx.BLUE_CORE, 0.35F * (1 - u)), 48);
            }
        }
        Iterator<Object[]> it = RIPPLES.iterator();
        while (it.hasNext()) {
            Object[] r = it.next();
            Vec3 at = (Vec3) r[0], n = (Vec3) r[1];
            float age = (float) (now - (Long) r[2]);
            if (age > 16) {
                it.remove();
                continue;
            }
            for (int i = 0; i < 3; i++) {
                float u = (age - i * 2.5F) / 12F;
                if (u < 0 || u > 1) {
                    continue;
                }
                ink.ring(at, n, 0.15 + 1.1 * u, 0.05, GFx.a(i == 0 ? GFx.WHITE : GFx.BLUE_CORE, 0.8F * (1 - u)), 40);
            }
            ink.glow(at, 0.5 * (1 - age / 16), GFx.a(GFx.WHITE, 0.6F * (1 - age / 16)));
        }
    }
}
