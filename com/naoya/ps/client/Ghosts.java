package com.naoya.ps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.naoya.ps.client.render.FxTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;

public final class Ghosts {
   private static final List<Ghosts.Ghost> GHOSTS = new ArrayList<>();
   static boolean drawing;

   private Ghosts() {
   }

   public static void add(int id, Vec3 pos, float yaw, float life, float alpha) {
      if (GHOSTS.size() > 240) {
         GHOSTS.remove(0);
      }

      GHOSTS.add(new Ghosts.Ghost(id, pos, yaw, System.nanoTime(), life, alpha));
   }

   static void tick() {
      long now = System.nanoTime();
      GHOSTS.removeIf(g -> (float)(now - g.start) / 1.0E9F > g.life);
   }

   public static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_ENTITIES && !GHOSTS.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            BufferSource buffers = mc.renderBuffers().bufferSource();
            float partial = event.getPartialTick();
            long now = System.nanoTime();
            drawing = true;

            try {
               for (Ghosts.Ghost g : GHOSTS) {
                  Entity e = mc.level.getEntity(g.id);
                  if (e instanceof LivingEntity && !(g.pos.distanceTo(e.position()) < 0.6)) {
                     double camDist = g.pos.add(0.0, 1.0, 0.0).distanceTo(cam);
                     if (!(camDist < 2.5)) {
                        float near = (float)Math.min(1.0, (camDist - 2.5) / 4.0);
                        float k = (float)(now - g.start) / 1.0E9F / g.life;
                        float a = near * g.alpha * (k < 0.7F ? 1.0F : (1.0F - k) / 0.3F);
                        if (!(a <= 0.02F)) {
                           EntityRenderer<Entity> r = mc.getEntityRenderDispatcher().getRenderer(e);
                           float yaw0 = e.getYRot();
                           float body0 = ((LivingEntity)e).yBodyRot;
                           float bodyO = ((LivingEntity)e).yBodyRotO;
                           float head0 = ((LivingEntity)e).yHeadRot;
                           float headO = ((LivingEntity)e).yHeadRotO;
                           LivingEntity le = (LivingEntity)e;
                           le.yBodyRot = le.yBodyRotO = g.yaw;
                           le.yHeadRot = le.yHeadRotO = g.yaw;
                           pose.pushPose();
                           pose.translate(g.pos.x - cam.x, g.pos.y - cam.y, g.pos.z - cam.z);
                           MultiBufferSource fill = type -> new Ghosts.Tint(buffers.getBuffer(FxTypes.TINT), 0.93F, 0.91F, 0.85F, a * 0.38F, false);
                           r.render(e, g.yaw, partial, pose, fill, 15728880);
                           MultiBufferSource rim = type -> new Ghosts.Tint(buffers.getBuffer(RenderType.lightning()), 0.75F, 0.85F, 1.0F, a * 0.25F, true);
                           pose.scale(1.03F, 1.02F, 1.03F);
                           r.render(e, g.yaw, partial, pose, rim, 15728880);
                           pose.popPose();
                           le.yBodyRot = body0;
                           le.yBodyRotO = bodyO;
                           le.yHeadRot = head0;
                           le.yHeadRotO = headO;
                           e.setYRot(yaw0);
                        }
                     }
                  }
               }
            } finally {
               drawing = false;
            }

            buffers.endBatch(FxTypes.TINT);
            buffers.endBatch(RenderType.lightning());
         }
      }
   }

   private static record Ghost(int id, Vec3 pos, float yaw, long start, float life, float alpha) {
   }

   static record Tint(VertexConsumer to, float r, float g, float b, float a, boolean premultiply) implements VertexConsumer {
      public VertexConsumer vertex(double x, double y, double z) {
         this.to.vertex(x, y, z);
         float m = this.premultiply ? this.a : 1.0F;
         this.to.color(this.r * m, this.g * m, this.b * m, this.a);
         return this;
      }

      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this;
      }

      public VertexConsumer uv(float u, float v) {
         return this;
      }

      public VertexConsumer overlayCoords(int u, int v) {
         return this;
      }

      public VertexConsumer uv2(int u, int v) {
         return this;
      }

      public VertexConsumer normal(float x, float y, float z) {
         return this;
      }

      public void endVertex() {
         this.to.endVertex();
      }

      public void defaultColor(int red, int green, int blue, int alpha) {
      }

      public void unsetDefaultColor() {
      }
   }
}
