package com.gojo.mod.fx.client;

import com.gojo.mod.fx.FxConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.client.event.ViewportEvent.ComputeFov;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class ScreenFx {
   private static float shake;
   private static float shakeDecay;
   private static float flash;
   private static int flashColor;
   private static float fovKick;
   private static float domainTarget;
   private static int domainColor;
   private static float domain;
   private static float prevDomain;

   private ScreenFx() {
   }

   public static void shake(float intensity, int durationTicks) {
      if (FxConfig.shake() && !(intensity <= shake)) {
         shake = Math.min(intensity, 4.0F);
         shakeDecay = shake / (float)Math.max(1, durationTicks);
      }
   }

   public static void flash(int argb, float strength) {
      float a = (float)(argb >>> 24 & 0xFF) / 255.0F * strength;
      if (FxConfig.flash() && !(a <= flash)) {
         flash = a;
         flashColor = argb & 16777215;
      }
   }

   public static void fovKick(float amount) {
      if (FxConfig.fovKick()) {
         fovKick = Math.max(fovKick, Math.min(amount, 0.2F));
      }
   }

   public static void inDomain(float amount, int color) {
      if (amount > domainTarget) {
         domainTarget = amount;
         domainColor = color;
      }
   }

   static void tick() {
      shake = Math.max(0.0F, shake - shakeDecay);
      flash *= 0.78F;
      if (flash < 0.004F) {
         flash = 0.0F;
      }

      fovKick *= 0.8F;
      prevDomain = domain;
      domain = domain + (domainTarget - domain) * 0.15F;
      domainTarget = 0.0F;
   }

   static void reset() {
      domainTarget = 0.0F;
      prevDomain = 0.0F;
      domain = 0.0F;
      fovKick = 0.0F;
      flash = 0.0F;
      shake = 0.0F;
   }

   @SubscribeEvent
   public static void onCameraAngles(ComputeCameraAngles event) {
      if (!(shake <= 0.001F)) {
         float t = (float)((double)(Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L) + event.getPartialTick());
         float s = shake * 1.6F;
         event.setYaw(event.getYaw() + (Mth.sin(t * 2.7F) + Mth.sin(t * 5.3F) * 0.5F) * s);
         event.setPitch(event.getPitch() + (Mth.cos(t * 3.1F) + Mth.sin(t * 6.7F) * 0.5F) * s);
         event.setRoll(event.getRoll() + Mth.sin(t * 4.3F) * s * 0.6F);
      }
   }

   @SubscribeEvent
   public static void onFov(ComputeFov event) {
      if (fovKick > 0.001F) {
         event.setFOV(event.getFOV() * (double)(1.0F + fovKick));
      }
   }

   @SubscribeEvent
   public static void onGui(Post event) {
      GuiGraphics g = event.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      float d = Mth.lerp(event.getPartialTick(), prevDomain, domain);
      if (d > 0.004F && FxConfig.flash()) {
         g.fill(0, 0, w, h, argb(d * 0.2F, domainColor));
         int edge = argb(d * 0.65F, 0);
         g.fillGradient(0, 0, w, h / 3, edge, 0);
         g.fillGradient(0, h - h / 3, w, h, 0, edge);
      }

      if (flash > 0.004F) {
         g.fill(0, 0, w, h, argb(flash, flashColor));
      }
   }

   private static int argb(float alpha, int rgb) {
      int a = Mth.clamp((int)(alpha * 255.0F), 0, 255);
      return a << 24 | rgb & 16777215;
   }
}
