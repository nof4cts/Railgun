package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class DomainFloor {
   private DomainFloor() {
   }

   @SubscribeEvent
   public static void onTick(PlayerTickEvent e) {
      if (e.phase == Phase.END && e.player.level().isClientSide && e.player == Minecraft.getInstance().player) {
         Player p = e.player;
         StatePacket d = ClientState.domainContaining(p.position().add(0.0, 1.0, 0.0));
         if (d != null && !p.getAbilities().flying && !p.isPassenger()) {
            if (p.getY() < d.cy - 0.7 && p.getDeltaMovement().y < 0.0 && !p.onGround()) {
               p.setPos(p.getX(), d.cy, p.getZ());
               p.setDeltaMovement(p.getDeltaMovement().x, Math.max(0.0, p.getDeltaMovement().y), p.getDeltaMovement().z);
               p.setOnGround(true);
               p.fallDistance = 0.0F;
            }
         }
      }
   }
}
