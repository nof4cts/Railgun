package com.gojo.mod.fx.client;

import com.gojo.mod.fx.FxConfig;
import com.gojo.mod.fx.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class ClientFx {
   private static final int MAX_EFFECTS = 96;
   private static final int PARTICLES_PER_TICK = 260;
   private static final double SHAKE_RANGE = 48.0;
   static SpriteSet energySprites;
   static SpriteSet sparkSprites;
   private static final List<Effects.Effect> EFFECTS = new ArrayList<>();
   private static final RandomSource RANDOM = RandomSource.create();
   private static int particleBudget;
   private static float density = 1.0F;

   private ClientFx() {
   }

   public static void handle(FxPacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         switch (p.kind) {
            case 0:
               add(new Effects.Orb(p.x, p.y, p.z, p.size, p.colorA, p.colorB, p.duration, p.mode, p.entityId));
               break;
            case 1:
               add(new Effects.Beam(p.x, p.y, p.z, p.bx, p.by, p.bz, p.size, p.colorA, p.colorB, p.duration));
               break;
            case 2:
               add(new Effects.Shockwave(p.x, p.y, p.z, (float)p.bx, (float)p.by, (float)p.bz, p.size, p.colorA, p.duration));
               break;
            case 3:
               add(new Effects.Burst(p.x, p.y, p.z, p.size, p.colorA, p.colorB, p.duration, p.mode));
               break;
            case 4:
               add(new Effects.Trail(p.x, p.y, p.z, p.bx, p.by, p.bz, p.colorA));
               break;
            case 5:
               add(new Effects.Domain(p.x, p.y, p.z, p.size, p.duration, p.mode, p.colorA));
               break;
            case 6:
               if (p.mode == 0) {
                  for (Effects.Effect e : EFFECTS) {
                     if (e instanceof Effects.Shimmer s && s.mode == 0 && s.followId == p.entityId && s.color == p.colorA) {
                        s.refresh(p.duration);
                        return;
                     }
                  }
               }

               add(new Effects.Shimmer(p.x, p.y, p.z, (float)p.bx, (float)p.by, (float)p.bz, p.size, p.colorA, p.duration, p.mode, p.entityId));
               break;
            case 7:
               Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
               double dist = Math.sqrt(cam.distanceToSqr(p.x, p.y, p.z));
               float falloff = (float)Mth.clamp(1.0 - dist / 48.0, 0.0, 1.0);
               if (falloff > 0.0F) {
                  ScreenFx.shake(p.size * falloff, p.duration);
                  ScreenFx.flash(p.colorA, falloff);
                  ScreenFx.fovKick(0.035F * p.size * falloff);
               }
               break;
            case 8:
               add(new Effects.Arcs(p.x, p.y, p.z, p.size, p.colorA, p.mode, p.duration));
         }
      }
   }

   private static void add(Effects.Effect effect) {
      if (EFFECTS.size() >= 96) {
         EFFECTS.remove(0);
      }

      EFFECTS.add(effect);
   }

   static int scaled(int count) {
      return Math.round((float)count * density);
   }

   static EnergyParticle spawn(boolean spark, double x, double y, double z, double vx, double vy, double vz, int lifetime) {
      Minecraft mc = Minecraft.getInstance();
      SpriteSet sprites = spark ? sparkSprites : energySprites;
      if (particleBudget > 0 && sprites != null && mc.level != null) {
         particleBudget--;
         EnergyParticle particle = new EnergyParticle(mc.level, x, y, z, vx, vy, vz, sprites, lifetime);
         mc.particleEngine.add(particle);
         return particle;
      } else {
         return null;
      }
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         ClientLevel level = mc.level;
         if (level == null) {
            EFFECTS.clear();
            ScreenFx.reset();
         } else if (!mc.isPaused()) {
            density = FxConfig.density();
            particleBudget = Math.round(260.0F * Math.min(1.0F, Math.max(density, 0.25F)));

            for (int i = EFFECTS.size() - 1; i >= 0; i--) {
               if (!EFFECTS.get(i).tick(level, RANDOM)) {
                  EFFECTS.remove(i);
               }
            }

            ScreenFx.tick();
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES && !EFFECTS.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            Camera camera = event.getCamera();
            Vec3 cam = camera.getPosition();
            FxDraw.setCamera(camera);
            Matrix4f pose = event.getPoseStack().last().pose();
            float partial = event.getPartialTick();
            float time = (float)(mc.level.getGameTime() % 24000L) + partial;
            BufferSource buffers = mc.renderBuffers().bufferSource();
            renderPass(buffers, FxRenderTypes.DARK_OVERLAY, 4, pose, cam, partial, time);
            renderPass(buffers, FxRenderTypes.DARK, 0, pose, cam, partial, time);
            renderPass(buffers, FxRenderTypes.ADDITIVE_COLOR, 1, pose, cam, partial, time);
            renderPass(buffers, FxRenderTypes.GLOW, 2, pose, cam, partial, time);
            renderPass(buffers, FxRenderTypes.BEAM, 3, pose, cam, partial, time);
            renderPass(buffers, FxRenderTypes.GLOW_OVERLAY, 5, pose, cam, partial, time);
            renderPass(buffers, FxRenderTypes.BEAM_OVERLAY, 6, pose, cam, partial, time);
         }
      }
   }

   private static void renderPass(BufferSource buffers, RenderType type, int pass, Matrix4f pose, Vec3 cam, float partial, float time) {
      VertexConsumer vc = buffers.getBuffer(type);

      for (Effects.Effect effect : EFFECTS) {
         double cull = effect.cullDistance();
         if (effect.distanceSqTo(cam) <= cull * cull) {
            effect.render(pass, vc, pose, cam.x, cam.y, cam.z, partial, time);
         }
      }

      buffers.endBatch(type);
   }
}
