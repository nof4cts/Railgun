package com.ryu.mod;

import com.jjk.mod.Energy;
import net.minecraft.world.entity.player.Player;

public final class CursedEnergy {
   public static final int MAX = 1500;
   private static final int REGEN_PER_TICK = 2;
   private static final String ENERGY_KEY = "ryu_cursed_energy";
   private static final String REINFORCEMENT_KEY = "ryu_reinforcement";
   private static final String DOMAIN_KEY = "ryu_domain_ticks";

   private CursedEnergy() {
   }

   public static int get(Player player) {
      return Energy.get(player);
   }

   public static void set(Player player, int value) {
      Energy.set(player, value);
   }

   public static boolean consume(Player player, int cost) {
      if (cost > 0 && !player.getAbilities().instabuild && getDomainTicks(player) <= 0) {
         int current = get(player);
         if (current < cost) {
            return false;
         } else {
            set(player, current - cost);
            return true;
         }
      } else {
         return true;
      }
   }

   public static void refund(Player player, int cost) {
      if (cost > 0 && !player.getAbilities().instabuild && getDomainTicks(player) <= 0) {
         set(player, get(player) + cost);
      }
   }

   public static void regen(Player player) {
   }

   public static boolean isReinforced(Player player) {
      return player.getPersistentData().getBoolean("ryu_reinforcement");
   }

   public static void setReinforced(Player player, boolean active) {
      player.getPersistentData().putBoolean("ryu_reinforcement", active);
   }

   public static int getDomainTicks(Player player) {
      return player.getPersistentData().getInt("ryu_domain_ticks");
   }

   public static void setDomainTicks(Player player, int ticks) {
      player.getPersistentData().putInt("ryu_domain_ticks", Math.max(0, ticks));
   }
}
