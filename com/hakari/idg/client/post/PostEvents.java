package com.hakari.idg.client.post;

import com.hakari.idg.client.CameraDirector;
import com.hakari.idg.client.ClientState;
import com.hakari.idg.network.StatePacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

public final class PostEvents {
   private PostEvents() {
   }

   @EventBusSubscriber(
      modid = "jjk",
      value = {Dist.CLIENT}
   )
   public static final class Game {
      private Game() {
      }

      @SubscribeEvent
      public static void onRenderLevel(RenderLevelStageEvent event) {
         PostRenderer.render(event);
      }

      @SubscribeEvent
      public static void onClientTick(ClientTickEvent event) {
         if (event.phase == Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !CameraDirector.active()) {
               boolean inside = false;
               boolean jackpot = false;

               for (StatePacket s : ClientState.all()) {
                  if (s.domainActive && ClientState.center(s).distanceTo(mc.player.position()) < (double)(s.radius + 1.0F)) {
                     inside = true;
                  }

                  if (s.entityId == mc.player.getId() && s.jackpotTicks > 0) {
                     jackpot = true;
                  }
               }

               Post.grade(jackpot ? 0.16F : 0.0F);
               Post.bloom(inside ? 0.45F : (jackpot ? 0.18F : 0.0F));
               Post.vignette(inside ? 3801136 : 136714, inside ? 0.3F : (jackpot ? 0.22F : 0.0F));
            }
         }
      }

      @SubscribeEvent
      public static void onLogout(LoggingOut event) {
         Post.clear();
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void onReload(RegisterClientReloadListenersEvent event) {
         event.registerReloadListener(PostRenderer::reload);
      }
   }
}
