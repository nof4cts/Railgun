package com.jjk.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Energy {
   public static final int MAX = 1000;
   public static final int REGEN_EVERY = 8;
   public static final int BASIC = 10;
   public static final int STRONG = 40;
   public static final int ULTIMATE = 50;
   private static final String KEY = "jjk_cursed_energy";

   private Energy() {
   }

   public static int get(Player p) {
      return p.getPersistentData().contains("jjk_cursed_energy") ? p.getPersistentData().getInt("jjk_cursed_energy") : 1000;
   }

   public static void set(Player p, int value) {
      p.getPersistentData().putInt("jjk_cursed_energy", Mth.clamp(value, 0, 1000));
      if (p instanceof ServerPlayer sp) {
         sync(sp);
      }
   }

   public static boolean has(ServerPlayer p, int cost) {
      if (!p.getAbilities().instabuild && get(p) < cost) {
         p.displayClientMessage(Component.literal("Not enough cursed energy (" + get(p) + " / " + cost + ")").withStyle(ChatFormatting.RED), true);
         p.playNotifySound((SoundEvent)SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7F, 0.5F);
         return false;
      } else {
         return true;
      }
   }

   public static void spend(ServerPlayer p, int cost) {
      if (!p.getAbilities().instabuild) {
         set(p, get(p) - cost);
      }
   }

   public static void sync(ServerPlayer p) {
      JjkNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new JjkNet.EnergySync(get(p)));
   }

   @SubscribeEvent
   public static void onTick(PlayerTickEvent e) {
      if (e.phase == Phase.END && e.player instanceof ServerPlayer p) {
         if (p.tickCount % 8 == 0 && get(p) < 1000) {
            p.getPersistentData().putInt("jjk_cursed_energy", get(p) + 1);
         }

         if (p.tickCount % 20 == 0) {
            sync(p);
         }
      }
   }
}
