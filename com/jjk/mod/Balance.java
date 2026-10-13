package com.jjk.mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Balance {
   public static final float CAP = 7.0F;
   private static final int TOP_WINDOW = 600;
   private static final int MOVE_WINDOW = 600;
   private static final Map<UUID, long[]> STATE = new HashMap<>();
   private static final Map<UUID, Long> CONFISCATED = new HashMap<>();
   private static final Map<UUID, long[]> LAST = new HashMap<>();
   private static final String DRAIN_KEY = "jjk_drained_until";

   private Balance() {
   }

   public static int[] top(Sorcerer s) {
      return switch (s) {
         case GOJO, SUKUNA, RYU, KASHIMO -> new int[]{5, 6};
         case CHOSO -> new int[]{5, 8};
         case HAKARI -> new int[]{3, 5};
         case YUKI -> new int[]{5, 8};
         case GETO -> new int[]{1, 3};
         case NAOYA -> new int[]{6, 7};
         case FIRE -> new int[]{3, 4};
         case HIGURUMA -> new int[]{5, 6};
         case TEN_SHADOWS -> new int[]{0};
         case YUTA -> new int[]{1, 8};
         case WATER -> new int[]{5, 6};
         case EARTH -> new int[]{2, 6};
         case AIR -> new int[]{1, 4};
         default -> new int[0];
      };
   }

   public static int[] lastMove(ServerPlayer p, int within) {
      long[] l = LAST.get(p.getUUID());
      return l != null && p.serverLevel().getGameTime() - l[2] <= (long)within ? new int[]{(int)l[0], (int)l[1]} : null;
   }

   public static boolean confiscated(Player p) {
      Long c = CONFISCATED.get(p.getUUID());
      return c != null && c > p.level().getGameTime();
   }

   @SubscribeEvent
   public static void onUseItem(RightClickItem e) {
      if (e.getEntity() instanceof ServerPlayer p && confiscated(p)) {
         String ns = ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem()).getNamespace();
         if (!ns.equals("minecraft")) {
            e.setCanceled(true);
            p.displayClientMessage(Component.literal("Your technique is confiscated.").withStyle(ChatFormatting.RED), true);
         }
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && CONFISCATED.remove(p.getUUID()) != null) {
         p.displayClientMessage(Component.literal("Your confiscated technique returns to you.").withStyle(ChatFormatting.GOLD), false);
      }
   }

   public static void drain(ServerPlayer p, int ticks) {
      p.getPersistentData().putLong("jjk_drained_until", p.serverLevel().getGameTime() + (long)ticks);
   }

   public static long drainedFor(Player p) {
      long until = p.getPersistentData().getLong("jjk_drained_until");
      return Math.max(0L, until - p.level().getGameTime());
   }

   @SubscribeEvent
   public static void onClone(Clone e) {
      long until = e.getOriginal().getPersistentData().getLong("jjk_drained_until");
      if (until > 0L) {
         e.getEntity().getPersistentData().putLong("jjk_drained_until", until);
      }
   }

   public static void confiscate(ServerPlayer p, int ticks) {
      CONFISCATED.put(p.getUUID(), p.serverLevel().getGameTime() + (long)ticks);
   }

   public static boolean used(ServerPlayer p, int slot) {
      long now = p.serverLevel().getGameTime();
      Long c = CONFISCATED.get(p.getUUID());
      if (c != null && c > now) {
         p.displayClientMessage(Component.literal("Your cursed technique is confiscated (" + (c - now + 19L) / 20L + "s).").withStyle(ChatFormatting.RED), true);
         return false;
      } else {
         long drained = Sorcerers.get(p) == Sorcerer.TEN_SHADOWS ? drainedFor(p) : 0L;
         if (drained > 0L) {
            long s = (drained + 19L) / 20L;
            p.displayClientMessage(
               Component.literal("Ten Shadows is sealed (the price of summoning Mahoraga): " + s / 60L + ":" + String.format("%02d", s % 60L) + " left.")
                  .withStyle(ChatFormatting.RED),
               true
            );
            return false;
         } else {
            long[] st = STATE.computeIfAbsent(p.getUUID(), k -> new long[]{-100000L, -1L});
            st[0] = now;
            LAST.put(p.getUUID(), new long[]{(long)Sorcerers.get(p).ordinal(), (long)slot, now});

            for (int t : top(Sorcerers.get(p))) {
               if (t == slot) {
                  st[1] = now + 600L;
               }
            }

            return true;
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onHurt(LivingHurtEvent e) {
      if (e.getSource().getEntity() instanceof ServerPlayer p && !(e.getAmount() <= 7.0F) && Sorcerers.get(p) != Sorcerer.NONE) {
         long[] st = STATE.get(p.getUUID());
         long now = p.serverLevel().getGameTime();
         if (st != null && now - st[0] <= 600L && st[1] < now) {
            e.setAmount(7.0F);
            return;
         }

         return;
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent e) {
      STATE.remove(e.getEntity().getUUID());
   }
}
