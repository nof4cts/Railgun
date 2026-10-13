package com.jjk.mod.client;

import com.jjk.mod.Sorcerer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class FovFix {
   private FovFix() {
   }

   @SubscribeEvent
   public static void onFov(ComputeFovModifierEvent e) {
      Sorcerer s = ClientSorcerers.mine();
      if (e.getPlayer() == Minecraft.getInstance().player && s != Sorcerer.NONE) {
         e.setNewFovModifier(Math.min(e.getNewFovModifier(), 1.08F));
      }
   }
}
