package com.jjk.mod;

import com.avatar.mod.Bending;
import com.geto.csm.ModRegistry;
import com.gojo.mod.CursedEnergy;
import com.higuruma.mod.Higuruma;
import com.higuruma.mod.HigurumaMod;
import com.jjk.mod.client.ClientSorcerers;
import com.yuki.mod.Yuki;
import com.yuta.mod.Yuta;
import com.yuta.mod.YutaMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Sorcerers {
   private static final String TAG = "jjk_sorcerer";
   private static final Map<UUID, Long> LAST_SWITCH = new HashMap<>();

   private Sorcerers() {
   }

   public static Sorcerer get(Player p) {
      return Sorcerer.byId(p.getPersistentData().getInt("jjk_sorcerer"));
   }

   public static boolean is(Player p, Sorcerer s) {
      return p.level().isClientSide ? ClientSorcerers.of(p) == s : get(p) == s;
   }

   public static void choose(ServerPlayer p, Sorcerer s) {
      Sorcerer old = get(p);
      if (old != s) {
         if (!s.available()) {
            p.displayClientMessage(Component.literal(s.displayName + ": coming soon.").withStyle(ChatFormatting.GRAY), true);
         } else {
            LAST_SWITCH.put(p.getUUID(), p.level().getGameTime());
            leave(p, old);
            p.getPersistentData().putInt("jjk_sorcerer", s.ordinal());
            if (s == Sorcerer.HIGURUMA) {
               ItemStack gavel = new ItemStack((ItemLike)HigurumaMod.GAVEL.get());
               if (!p.getInventory().contains(gavel)) {
                  ItemStack held = p.getMainHandItem().copy();
                  p.setItemInHand(InteractionHand.MAIN_HAND, gavel);
                  if (!held.isEmpty() && !p.getInventory().add(held)) {
                     p.drop(held, false);
                  }
               }
            }

            if (s == Sorcerer.YUTA) {
               ItemStack katana = new ItemStack((ItemLike)YutaMod.KATANA.get());
               if (!p.getInventory().contains(katana)) {
                  ItemStack held = p.getMainHandItem().copy();
                  p.setItemInHand(InteractionHand.MAIN_HAND, katana);
                  if (!held.isEmpty() && !p.getInventory().add(held)) {
                     p.drop(held, false);
                  }
               }
            }

            if (s == Sorcerer.GETO) {
               ItemStack manip = new ItemStack((ItemLike)ModRegistry.MANIPULATION.get());
               if (!p.getInventory().contains(manip) && !p.getInventory().add(manip)) {
                  p.drop(manip, false);
               }
            }

            p.displayClientMessage(
               Component.literal(
                     s == Sorcerer.NONE
                        ? "Your cursed technique fades."
                        : (s.isAvatar() ? "You are a " + s.displayName + "." : "Cursed technique: " + s.displayName)
                  )
                  .withStyle(st -> st.withColor(s.color)),
               true
            );
            broadcast(p);
         }
      }
   }

   private static void leave(ServerPlayer p, Sorcerer old) {
      switch (old) {
         case GOJO:
            CursedEnergy.setInfinity(p, false);
            break;
         case RYU:
            com.ryu.mod.CursedEnergy.setReinforced(p, false);
            break;
         case YUKI:
            Yuki.leave(p);
            break;
         case FIRE:
         case WATER:
         case EARTH:
         case AIR:
            Bending.leave(p);
            break;
         case HIGURUMA:
            Higuruma.leave(p);
            break;
         case YUTA:
            Yuta.leave(p);
      }
   }

   static void broadcast(ServerPlayer p) {
      JjkNet.CHANNEL.send(PacketDistributor.ALL.noArg(), new JjkNet.Sync(p.getId(), get(p).ordinal()));
   }

   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            JjkNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new JjkNet.Sync(o.getId(), get(o).ordinal()));
         }

         broadcast(p);
         if (get(p) == Sorcerer.NONE && !p.getPersistentData().getBoolean("jjk_hinted")) {
            p.getPersistentData().putBoolean("jjk_hinted", true);
            p.displayClientMessage(Component.literal("Press M to choose your cursed technique.").withStyle(st -> st.withColor(11832575)), false);
         }
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerRespawnEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         broadcast(p);
      }
   }

   @SubscribeEvent
   public static void onDimension(PlayerChangedDimensionEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         broadcast(p);
      }
   }

   @SubscribeEvent
   public static void onClone(Clone e) {
      e.getEntity().getPersistentData().putInt("jjk_sorcerer", e.getOriginal().getPersistentData().getInt("jjk_sorcerer"));
      e.getEntity().getPersistentData().putBoolean("jjk_hinted", true);
   }
}
