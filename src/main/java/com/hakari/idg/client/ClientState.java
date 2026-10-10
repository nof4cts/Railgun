package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

public final class ClientState {
   private static final Map<Integer, StatePacket> STATES = new HashMap<>();

   public static void put(StatePacket p) {
      p.receivedAt = Vfx.now();
      STATES.put(p.entityId, p);
   }

   public static StatePacket get(int entityId) {
      return STATES.get(entityId);
   }

   public static Collection<StatePacket> all() {
      return STATES.values();
   }

   public static void clear() {
      STATES.clear();
   }

   public static float ticksSince(StatePacket p) {
      return (float)(Vfx.now() - p.receivedAt) / 50.0F;
   }

   public static float castElapsedSeconds(StatePacket p) {
      if (p.castTicks <= 0) {
         return p.domainActive ? 999.0F : 0.0F;
      } else {
         float elapsedTicks = (float)(180 - p.castTicks) + ticksSince(p);
         return Math.max(0.0F, elapsedTicks / 20.0F);
      }
   }

   public static float jackpotSecondsLeft(StatePacket p) {
      return Math.max(0.0F, ((float)p.jackpotTicks - ticksSince(p)) / 20.0F);
   }

   public static Vec3 center(StatePacket p) {
      return new Vec3(p.cx, p.cy, p.cz);
   }

   public static StatePacket domainContaining(Vec3 pos) {
      for (StatePacket p : STATES.values()) {
         if ((p.domainActive || p.castTicks > 0) && center(p).distanceTo(pos) <= (double)p.radius + 0.5) {
            return p;
         }
      }

      return null;
   }

   private ClientState() {
   }
}
