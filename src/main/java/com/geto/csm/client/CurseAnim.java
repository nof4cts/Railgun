package com.geto.csm.client;

import java.util.HashMap;
import java.util.Map;

/** When each curse last performed each action (server-triggered), for procedural animation. */
public final class CurseAnim {
    private static final Map<Long, Long> STARTS = new HashMap<>();

    private static long key(int entity, int action) {
        return ((long) entity << 8) | (action & 0xFF);
    }

    public static void trigger(int entity, int action) {
        STARTS.put(key(entity, action), Vfx.now());
    }

    /** Milliseconds since the action started, or -1 if never / long ago. */
    public static long age(int entity, int action) {
        Long s = STARTS.get(key(entity, action));
        if (s == null) return -1;
        long a = Vfx.now() - s;
        return a > 20000 ? -1 : a;
    }

    /** 0 → 1 → 0 envelope over the given duration (sin hump), 0 when inactive. */
    public static float pulse(int entity, int action, long durationMs) {
        long a = age(entity, action);
        if (a < 0 || a > durationMs) return 0f;
        return (float) Math.sin(Math.PI * a / durationMs);
    }

    public static void clear() {
        STARTS.clear();
    }

    private CurseAnim() {}
}
