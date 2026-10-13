package com.geto.csm.server;

import com.geto.csm.CurseKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

public class CsmState {
   private static final Map<UUID, CsmState> MAP = new HashMap<>();
   public int selected;
   public int absorbed;
   public final List<List<Integer>> slots = new ArrayList<>();
   public final long[] readyAt = new long[CurseKind.values().length];
   public int lockTicks;

   public static CsmState get(Player p) {
      return MAP.computeIfAbsent(p.getUUID(), k -> new CsmState());
   }

   public static CsmState peek(Player p) {
      return MAP.get(p.getUUID());
   }

   public static void remove(Player p) {
      MAP.remove(p.getUUID());
   }
}
