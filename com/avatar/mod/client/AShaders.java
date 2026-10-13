package com.avatar.mod.client;

import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
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
public final class AShaders {
   public static ShaderInstance flame;
   public static ShaderInstance haze;
   public static ShaderInstance impact;

   private AShaders() {
   }

   static boolean ready() {
      return flame != null && haze != null && impact != null;
   }

   @SubscribeEvent
   public static void onShaders(RegisterShadersEvent e) throws IOException {
      e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation("avatar", "flame"), Gfx.FORMAT), s -> flame = s);
      e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation("avatar", "haze"), Gfx.FORMAT), s -> haze = s);
      e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation("avatar", "impact"), Gfx.FORMAT), s -> impact = s);
   }
}
