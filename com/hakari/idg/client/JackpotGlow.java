package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent.Post;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class JackpotGlow {
   private static boolean drawing;

   private JackpotGlow() {
   }

   @SubscribeEvent
   public static void after(Post event) {
      if (!drawing && event.getEntity() instanceof AbstractClientPlayer player) {
         StatePacket var16 = null;

         for (StatePacket p : ClientState.all()) {
            if (p.entityId == player.getId() && (p.jackpotTicks > 0 || p.jackpotCutTicks > 0)) {
               var16 = p;
            }
         }

         if (var16 != null) {
            float build = var16.jackpotCutTicks > 0 ? Mth.clamp(1.0F - (float)var16.jackpotCutTicks / 110.0F, 0.0F, 1.0F) : 1.0F;
            float time = (float)(System.currentTimeMillis() % 100000L) / 1000.0F;
            float breathe = 0.75F + 0.25F * Mth.sin(time * 4.2F);
            float a = 0.26F * build * breathe;
            if (!(a <= 0.01F)) {
               PoseStack pose = event.getPoseStack();
               MultiBufferSource base = event.getMultiBufferSource();
               MultiBufferSource glow = type -> new JackpotGlow.Tint(base.getBuffer(RenderType.lightning()), 0.22F, 1.0F, 0.48F, a);
               drawing = true;

               try {
                  for (int layer = 0; layer < 2; layer++) {
                     float scale = layer == 0 ? 1.06F : 1.14F;
                     pose.pushPose();
                     pose.translate(0.0F, -0.02F * (float)layer, 0.0F);
                     pose.scale(scale, scale, scale);
                     MultiBufferSource src = layer == 0
                        ? glow
                        : type -> new JackpotGlow.Tint(base.getBuffer(RenderType.lightning()), 0.12F, 0.85F, 0.35F, a * 0.35F);
                     event.getRenderer()
                        .render(player, Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot()), event.getPartialTick(), pose, src, 15728880);
                     pose.popPose();
                  }
               } finally {
                  drawing = false;
               }
            }
         }
      }
   }

   private static record Tint(VertexConsumer to, float r, float g, float b, float a) implements VertexConsumer {
      public VertexConsumer vertex(double x, double y, double z) {
         this.to.vertex(x, y, z);
         this.to.color(this.r, this.g, this.b, this.a);
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
