package com.geto.csm.client;

import java.util.HashMap;
import java.util.Map;

public final class CurseAnim {
   private static final Map<Long, Long> STARTS = new HashMap<>();

   private static long key(int entity, int action) {
      return (long)entity << 8 | (long)(action & 0xFF);
   }

   public static void trigger(int entity, int action) {
      STARTS.put(key(entity, action), Vfx.now());
   }

   public static long age(int entity, int action) {
      Long s = STARTS.get(key(entity, action));
      if (s == null) {
         return -1L;
      } else {
         long a = Vfx.now() - s;
         return a > 20000L ? -1L : a;
      }
   }

   public static float pulse(int entity, int action, long durationMs) {
      long a = age(entity, action);
      return a >= 0L && a <= durationMs ? (float)Math.sin(Math.PI * (double)a / (double)durationMs) : 0.0F;
   }

   public static void clear() {
      STARTS.clear();
   }

   private CurseAnim() {
   }
}
