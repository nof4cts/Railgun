package com.hakari.idg.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class HakariState {
   private static final Map<UUID, HakariState> MAP = new HashMap<>();
   public int castTicks;
   public boolean domainActive;
   public int domainTicks;
   public Vec3 domainCenter = Vec3.ZERO;
   public float radius = 20.0F;
   public final Set<Integer> trapped = new HashSet<>();
   public int visuals;
   public int fails;
   public float chanceBonus;
   public boolean boosted;
   public int riichiTicks;
   public int riichiTier;
   public int riichiScenario;
   public int riichiNumber;
   public int riichiFinal;
   public boolean riichiWin;
   public boolean riichiPity;
   public int jackpotCutTicks;
   public int jackpotTicks;
   public int jackpotMax;
   public int jackpotNumber;
   public int counterTicks;
   public int rhythmTicks;
   public int windupTicks;
   public int pose;
   public List<BlockPos> clearQueue = new ArrayList<>();
   public int clearIndex;

   public static HakariState get(Player p) {
      return MAP.computeIfAbsent(p.getUUID(), k -> new HakariState());
   }

   public static HakariState peek(Player p) {
      return MAP.get(p.getUUID());
   }

   public static void remove(Player p) {
      MAP.remove(p.getUUID());
   }

   public boolean locked() {
      return this.castTicks > 0 || this.jackpotCutTicks > 0;
   }

   public boolean immortal() {
      return this.locked() || this.jackpotTicks > 0;
   }
}
