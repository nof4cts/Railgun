package com.jjk.mod.client;

import com.jjk.mod.Sorcerer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class ClientSorcerers {
   private static final Map<Integer, Sorcerer> MAP = new HashMap<>();

   private ClientSorcerers() {
   }

   public static void set(int entity, Sorcerer s) {
      MAP.put(entity, s);
   }

   public static Sorcerer of(Entity e) {
      return e == null ? Sorcerer.NONE : MAP.getOrDefault(e.getId(), Sorcerer.NONE);
   }

   public static Sorcerer mine() {
      return of(Minecraft.getInstance().player);
   }

   public static boolean mine(Sorcerer s) {
      return mine() == s;
   }

   @SubscribeEvent
   public static void onLogout(LoggingOut e) {
      MAP.clear();
   }
}
