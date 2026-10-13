package com.gojo.mod;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import net.minecraft.world.entity.player.Player;

public final class CursedEnergy {
   public static final int MAX = 1000;
   private static final String ENERGY_KEY = "gojo_cursed_energy";
   private static final String INFINITY_KEY = "gojo_infinity";
   private static final String PURPLE_CHARGE_KEY = "gojo_purple_charge";
   private static final String RIPPLE_KEY = "gojo_last_ripple";

   private CursedEnergy() {
   }

   public static int get(Player player) {
      return Energy.get(player);
   }

   public static void set(Player player, int value) {
      Energy.set(player, value);
   }

   public static boolean consume(Player player, int cost) {
      if (cost > 0 && !player.getAbilities().instabuild) {
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
      if (cost > 0 && !player.getAbilities().instabuild) {
         set(player, get(player) + cost);
      }
   }

   public static void regen(Player player) {
   }

   public static boolean hasSixEyes(Player player) {
      return Sorcerers.is(player, Sorcerer.GOJO);
   }

   public static boolean isInfinity(Player player) {
      return player.getPersistentData().getBoolean("gojo_infinity");
   }

   public static void setInfinity(Player player, boolean active) {
      player.getPersistentData().putBoolean("gojo_infinity", active);
   }

   public static int getPurpleCharge(Player player) {
      return player.getPersistentData().getInt("gojo_purple_charge");
   }

   public static void setPurpleCharge(Player player, int ticks) {
      player.getPersistentData().putInt("gojo_purple_charge", Math.max(0, ticks));
   }

   public static boolean rippleReady(Player player) {
      long now = player.level().getGameTime();
      if (now - player.getPersistentData().getLong("gojo_last_ripple") < 4L) {
         return false;
      } else {
         player.getPersistentData().putLong("gojo_last_ripple", now);
         return true;
      }
   }
}
