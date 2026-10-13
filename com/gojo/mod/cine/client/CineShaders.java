package com.gojo.mod.cine.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexBuffer.Usage;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "jjk",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public final class CineShaders {
   public static ShaderInstance orb;
   public static ShaderInstance energy;
   public static ShaderInstance voidsky;
   public static ShaderInstance impact;
   private static VertexBuffer sphere;

   private CineShaders() {
   }

   static boolean ready() {
      return orb != null && energy != null && voidsky != null && impact != null;
   }

   @SubscribeEvent
   public static void onShaders(RegisterShadersEvent e) throws IOException {
      e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation("gojo", "orb"), Gfx.FORMAT), s -> orb = s);
      e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation("gojo", "energy"), Gfx.FORMAT), s -> energy = s);
      e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation("gojo", "voidsky"), Gfx.FORMAT), s -> voidsky = s);
      e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation("gojo", "impact"), Gfx.FORMAT), s -> impact = s);
   }

   static VertexBuffer sphere() {
      if (sphere == null) {
         BufferBuilder b = Tesselator.getInstance().getBuilder();
         b.begin(Mode.TRIANGLES, Gfx.FORMAT);
         int nt = 48;
         int np = 28;

         for (int i = 0; i < nt; i++) {
            for (int j = 0; j < np; j++) {
               float[][] q = new float[4][];
               int[][] ij = new int[][]{{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};

               for (int c = 0; c < 4; c++) {
                  float th = (float) (Math.PI * 2) * (float)ij[c][0] / (float)nt;
                  float ph = (float) Math.PI * (float)ij[c][1] / (float)np;
                  q[c] = new float[]{Mth.sin(ph) * Mth.cos(th), Mth.cos(ph), Mth.sin(ph) * Mth.sin(th)};
               }

               for (int o : new int[]{0, 1, 2, 0, 2, 3}) {
                  float[] p = q[o];
                  b.vertex((double)p[0], (double)p[1], (double)p[2]).uv(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F).normal(p[0], p[1], p[2]).endVertex();
               }
            }
         }

         sphere = new VertexBuffer(Usage.STATIC);
         sphere.bind();
         sphere.upload(b.end());
         VertexBuffer.unbind();
      }

      return sphere;
   }
}
