package com.geto.csm.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

public final class SpineTrail {
   private static final Map<Integer, SpineTrail> TRAILS = new HashMap<>();
   private final List<Vec3> pts = new ArrayList<>();

   public static Vec3[] sample(int entityId, Vec3 head, Vec3 backDir, int count, double spacing) {
      SpineTrail tr = TRAILS.computeIfAbsent(entityId, k -> new SpineTrail());
      List<Vec3> p = tr.pts;
      if (p.isEmpty()) {
         p.add(head);
      } else {
         p.set(0, head);
         if (p.size() == 1 || p.get(0).distanceTo(p.get(1)) > spacing * 0.5) {
            p.add(0, head);
         }
      }

      int max = count * 3;

      while (p.size() > max) {
         p.remove(p.size() - 1);
      }

      Vec3[] out = new Vec3[count];
      out[0] = head;
      int seg = 0;
      double carried = 0.0;
      Vec3 cur = head;

      for (int i = 1; i < count; i++) {
         double need = spacing;

         Vec3 next;
         for (next = null; seg < p.size() - 1; seg++) {
            Vec3 b = p.get(seg + 1);
            double len = cur.distanceTo(b);
            if (len >= need) {
               next = cur.add(b.subtract(cur).scale(need / len));
               break;
            }

            need -= len;
            cur = b;
         }

         if (next == null) {
            Vec3 dir = backDir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, -1.0) : backDir.normalize();
            next = cur.add(dir.scale(need));
         }

         out[i] = next;
         cur = next;
      }

      return out;
   }

   public static void forget(int entityId) {
      TRAILS.remove(entityId);
   }

   public static void clear() {
      TRAILS.clear();
   }

   public static void prune(Set<Integer> alive) {
      TRAILS.keySet().retainAll(alive);
   }

   private SpineTrail() {
   }
}
