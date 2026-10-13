package com.naoya.ps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.naoya.ps.client.cine.CameraPose;
import com.naoya.ps.client.cine.Director;
import com.naoya.ps.client.cine.Overlay;
import com.naoya.ps.client.fx.Light;
import com.naoya.ps.client.fx.ScreenFx;
import com.naoya.ps.client.render.PostFx;
import com.naoya.ps.client.render.RibbonRenderer;
import com.naoya.ps.client.render.Views;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.client.event.RenderLivingEvent.Post;
import net.minecraftforge.client.event.RenderLivingEvent.Pre;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.client.event.ViewportEvent.ComputeFov;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;

public final class ClientEvents {
   private ClientEvents() {
   }

   @EventBusSubscriber(
      modid = "jjk",
      value = {Dist.CLIENT}
   )
   public static final class ForgeBus {
      private static final Set<Integer> FLATTENED = new HashSet<>();

      private ForgeBus() {
      }

      @SubscribeEvent
      public static void onRenderTick(RenderTickEvent event) {
         if (event.phase == Phase.START) {
            Director.frame(event.renderTickTime);
         }
      }

      @SubscribeEvent
      public static void onClientTick(ClientTickEvent event) {
         if (event.phase == Phase.END) {
            NFx.tick();
            Director.tick();
         }
      }

      @SubscribeEvent
      public static void onRenderLevel(RenderLevelStageEvent event) {
         Ghosts.render(event);
         RibbonRenderer.render(event);
         if (event.getStage() == Stage.AFTER_PARTICLES) {
            Light.render(Views.view(event), event.getCamera().getPosition());
         }

         PostFx.render(event);
      }

      @SubscribeEvent
      public static void onLivingPre(Pre<?, ?> event) {
         LivingEntity e = event.getEntity();
         if (!Ghosts.drawing && NFx.frozen(e)) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 n = e.position().subtract(cam);
            n = new Vec3(n.x, 0.0, n.z).lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(n.x, 0.0, n.z).normalize();
            float s = 0.05F;
            float k = 1.0F - s;
            Matrix4f m = new Matrix4f(
               (float)(1.0 - (double)k * n.x * n.x),
               0.0F,
               (float)((double)(-k) * n.z * n.x),
               0.0F,
               0.0F,
               1.0F,
               0.0F,
               0.0F,
               (float)((double)(-k) * n.x * n.z),
               0.0F,
               (float)(1.0 - (double)k * n.z * n.z),
               0.0F,
               0.0F,
               0.0F,
               0.0F,
               1.0F
            );
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.last().pose().mul(m);
            FLATTENED.add(e.getId());
         }
      }

      @SubscribeEvent
      public static void onLivingPost(Post<?, ?> event) {
         if (FLATTENED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
         }
      }

      @SubscribeEvent(
         priority = EventPriority.HIGH
      )
      public static void onAngles(ComputeCameraAngles event) {
         CameraPose pose = Director.pose();
         if (pose != null) {
            event.setRoll(pose.roll() * Director.weight());
         }

         ScreenFx fx = PostFx.current();
         float s = Math.min(1.2F, fx.shake) * (float)Prefs.shake;
         if (s > 0.001F) {
            s *= s;
            double t = (double)System.nanoTime() / 1.0E9 * 26.0;
            event.setYaw(event.getYaw() + s * 3.0F * noise(t, 1));
            event.setPitch(event.getPitch() + s * 2.6F * noise(t, 2));
            event.setRoll(event.getRoll() + s * 3.4F * noise(t * 0.7, 3));
         }
      }

      private static float noise(double t, int seed) {
         return (float)(
            Math.sin(t + (double)seed * 12.9898) * 0.55 + Math.sin(t * 2.31 + (double)seed * 7.233) * 0.3 + Math.sin(t * 5.17 + (double)seed * 3.719) * 0.15
         );
      }

      @SubscribeEvent
      public static void onFov(ComputeFov event) {
         CameraPose pose = Director.pose();
         if (pose != null && event.usedConfiguredFov()) {
            event.setFOV((double)pose.fov());
         }
      }

      @SubscribeEvent
      public static void onMovement(MovementInputUpdateEvent event) {
         Minecraft mc = Minecraft.getInstance();
         if (Director.active() || mc.player != null && NFx.frozen(mc.player)) {
            Input in = event.getInput();
            in.forwardImpulse = 0.0F;
            in.leftImpulse = 0.0F;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
         }
      }

      @SubscribeEvent
      public static void onInteract(InteractionKeyMappingTriggered event) {
         Minecraft mc = Minecraft.getInstance();
         if (Director.active() || mc.player != null && NFx.frozen(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
         }
      }

      @SubscribeEvent
      public static void onHand(RenderHandEvent event) {
         if (Director.active()) {
            event.setCanceled(true);
         }
      }

      @SubscribeEvent
      public static void onOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
         if (Overlay.hideHud()) {
            event.setCanceled(true);
         }
      }

      @SubscribeEvent
      public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
         Hud.draw(event.getGuiGraphics(), event.getPartialTick());
         Overlay.draw(event.getGuiGraphics(), event.getPartialTick());
         Cine.filmstrip(event.getGuiGraphics(), event.getPartialTick());
      }

      @SubscribeEvent
      public static void onNameTag(RenderNameTagEvent event) {
         Minecraft mc = Minecraft.getInstance();
         if (Ghosts.drawing || Director.active() && event.getEntity() == mc.player) {
            event.setResult(Result.DENY);
         }
      }

      @SubscribeEvent
      public static void onLogout(LoggingOut event) {
         Director.stop();
         Light.clear();
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class ModBus {
      private ModBus() {
      }

      @SubscribeEvent
      public static void onSetup(FMLClientSetupEvent event) {
         event.enqueueWork(Anims::init);
      }

      @SubscribeEvent
      public static void onReload(RegisterClientReloadListenersEvent event) {
         event.registerReloadListener(PostFx::reload);
      }
   }
}
