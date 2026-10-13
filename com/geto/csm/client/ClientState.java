package com.geto.csm.client;

import com.geto.csm.network.StatePacket;

public final class ClientState {
   public static int selected;
   public static int absorbed;
   public static int slots;
   public static int[] cooldowns = new int[0];
   public static long receivedAt;

   public static void apply(StatePacket p) {
      selected = p.selected;
      absorbed = p.absorbed;
      slots = p.slots;
      cooldowns = p.cooldowns;
      receivedAt = Vfx.now();
   }

   public static float cooldownSeconds(int kind) {
      return kind >= 0 && kind < cooldowns.length ? Math.max(0.0F, (float)cooldowns[kind] / 20.0F - (float)(Vfx.now() - receivedAt) / 1000.0F) : 0.0F;
   }

   public static void clear() {
      selected = 0;
      absorbed = 0;
      slots = 0;
      cooldowns = new int[0];
   }

   private ClientState() {
   }
}
