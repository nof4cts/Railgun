package com.geto.csm.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.client.event.ViewportEvent.ComputeFov;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "jjk",
   bus = Bus.FORGE,
   value = {Dist.CLIENT}
)
public final class ClientEvents {
   @SubscribeEvent
   public static void onRenderTick(RenderTickEvent e) {
      if (e.phase == Phase.START) {
         Sfx.run();
         CameraDirector.frame(e.renderTickTime);
      }
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         CameraDirector.clientTick();
         Vfx.prune();
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent e) {
      Stage stage = e.getStage();
      boolean solid = stage == Stage.AFTER_ENTITIES;
      boolean glow = stage == Stage.AFTER_PARTICLES;
      if (solid || glow) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            PoseStack ps = e.getPoseStack();
            Vec3 camPos = e.getCamera().getPosition();
            Vfx.Ctx ctx = new Vfx.Ctx();
            ctx.ps = ps;
            ctx.cam = e.getCamera();
            ctx.camPos = camPos;
            ctx.t = Vfx.now();
            ctx.partial = e.getPartialTick();
            if (solid) {
               CurseRenderer.solid(ctx);
               Draw.worldPassBegin(false);
               BufferBuilder b = Draw.begin(Mode.QUADS);
               ctx.vc = b;
               Vfx.renderSolid(ctx);
               Draw.end(b);
               Draw.worldPassEnd();
            } else {
               ScreenFx.captureMatrices(new Matrix4f(ps.last().pose()), new Matrix4f(e.getProjectionMatrix()), camPos);
               Draw.worldPassBegin(true);
               BufferBuilder b = Draw.begin(Mode.QUADS);
               ctx.vc = b;
               CurseRenderer.glow(ctx);
               Vfx.renderGlow(ctx);
               Draw.end(b);
               Draw.worldPassEnd();
               CurseRenderer.text(ctx, mc.renderBuffers().bufferSource());
            }
         }
      }
   }

   @SubscribeEvent
   public static void onCameraAngles(ComputeCameraAngles e) {
      float[] s = CameraDirector.shakeOffsets();
      e.setYaw(e.getYaw() + s[0]);
      e.setPitch(e.getPitch() + s[1]);
      e.setRoll(e.getRoll() + s[2] + CameraDirector.roll());
   }

   @SubscribeEvent
   public static void onFov(ComputeFov e) {
      if (e.usedConfiguredFov() && CameraDirector.active()) {
         e.setFOV(CameraDirector.fov(e.getFOV()));
      }
   }

   @SubscribeEvent
   public static void onRenderHand(RenderHandEvent e) {
      if (CameraDirector.active()) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onOverlay(Pre e) {
      if (CameraDirector.active()) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onGuiPre(net.minecraftforge.client.event.RenderGuiEvent.Pre e) {
      ScreenFx.preGui(e.getGuiGraphics());
   }

   @SubscribeEvent
   public static void onGuiPost(Post e) {
      Hud.render(e.getGuiGraphics());
      ScreenFx.postGui(e.getGuiGraphics(), e.getPartialTick());
   }

   @SubscribeEvent
   public static void onLogout(LoggingOut e) {
      CameraDirector.reset();
      Vfx.clear();
      ScreenFx.clear();
      ClientState.clear();
      SpineTrail.clear();
      CurseAnim.clear();
      Sfx.clear();
   }

   private ClientEvents() {
   }
}
