package com.yuta.mod.client;

import com.mojang.logging.LogUtils;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class YutaAnim implements IAnimation {
   private static final Set<AbstractClientPlayer> ADDED = Collections.newSetFromMap(new WeakHashMap<>());
   private final AbstractClientPlayer player;
   private float partial;

   private YutaAnim(AbstractClientPlayer player) {
      this.player = player;
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.END && mc.level != null) {
         for (AbstractClientPlayer p : mc.level.players()) {
            if (ADDED.add(p)) {
               try {
                  PlayerAnimationAccess.getPlayerAnimLayer(p).addAnimLayer(1450, new YutaAnim(p));
               } catch (RuntimeException var5) {
                  LogUtils.getLogger().warn("Could not add Yuta animation layer", var5);
               }
            }
         }
      }
   }

   private long[] state() {
      return YuFx.POSES.get(this.player.getId());
   }

   public boolean isActive() {
      long[] s = this.state();
      return s != null && YuFx.now() < s[1] + s[2] + 6L && !this.player.isSleeping() && !this.player.isPassenger();
   }

   public void setupAnim(float tickDelta) {
      this.partial = tickDelta;
   }

   private static float smooth(float a, float b, float x) {
      float k = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
      return k * k * (3.0F - 2.0F * k);
   }

   public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f v) {
      long[] s = this.state();
      if (s != null && type == TransformType.ROTATION) {
         int pose = (int)s[0];
         float t = (float)(YuFx.now() - s[1]) + this.partial;
         float dur = (float)s[2];
         boolean stance = pose == 4 || pose == 8 || pose == 9 || pose == 6;
         float w = stance ? smooth(0.0F, 3.0F, t) * (1.0F - smooth(dur, dur + 5.0F, t)) : smooth(0.0F, 1.2F, t) * (1.0F - smooth(dur - 2.0F, dur + 4.0F, t));
         if (w <= 0.001F) {
            return v;
         } else {
            float pitch = Mth.clamp(Mth.lerp(this.partial, this.player.xRotO, this.player.getXRot()), -70.0F, 70.0F) * (float) (Math.PI / 180.0);

            Vec3f target = switch (pose) {
               case 1 -> this.cut(part, t, dur, 1, pitch, v);
               case 2 -> this.cut(part, t, dur, -1, pitch, v);
               case 3 -> this.overhead(part, t, dur, pitch, v);
               case 4 -> this.call(part, t, v);
               default -> v;
               case 6 -> this.speak(part, v);
               case 7 -> this.palm(part, t, pitch, v);
               case 8 -> this.ring(part, t, v);
               case 9 -> this.focus(part, v);
            };
            return new Vec3f(
               Mth.lerp(w, (Float)v.getX(), (Float)target.getX()),
               Mth.lerp(w, (Float)v.getY(), (Float)target.getY()),
               Mth.lerp(w, (Float)v.getZ(), (Float)target.getZ())
            );
         }
      } else {
         return v;
      }
   }

   private Vec3f cut(String part, float t, float dur, int dir, float pitch, Vec3f v) {
      float k = smooth(0.5F, dur * 0.55F, t);
      float sweep = (float)dir * Mth.lerp(k, 1.1F, -1.0F);
      float twist = (float)dir * Mth.lerp(k, 0.55F, -0.45F);

      return switch (part) {
         case "rightArm" -> new Vec3f(-1.45F + pitch * 0.6F, sweep, 0.1F);
         case "leftArm" -> new Vec3f(-1.3F + pitch * 0.6F, sweep - 0.55F * (float)dir, -0.05F);
         case "body" -> new Vec3f(0.05F, twist, 0.0F);
         case "rightLeg" -> new Vec3f(dir > 0 ? 0.3F : -0.35F, 0.0F, 0.05F);
         case "leftLeg" -> new Vec3f(dir > 0 ? -0.35F : 0.3F, 0.0F, -0.05F);
         default -> v;
      };
   }

   private Vec3f overhead(String part, float t, float dur, float pitch, Vec3f v) {
      float k = smooth(1.5F, dur * 0.5F, t);
      float arm = Mth.lerp(k, -2.9F, -0.7F + pitch * 0.4F);

      return switch (part) {
         case "rightArm" -> new Vec3f(arm, -0.25F, 0.05F);
         case "leftArm" -> new Vec3f(arm, 0.25F, -0.05F);
         case "body" -> new Vec3f(Mth.lerp(k, -0.1F, 0.32F), 0.0F, 0.0F);
         case "rightLeg" -> new Vec3f(-0.5F * k, 0.0F, 0.05F);
         case "leftLeg" -> new Vec3f(0.35F * k, 0.0F, -0.05F);
         case "head" -> new Vec3f((Float)v.getX() + 0.15F * k, (Float)v.getY(), (Float)v.getZ());
         default -> v;
      };
   }

   private Vec3f speak(String part, Vec3f v) {
      return switch (part) {
         case "leftArm" -> new Vec3f(-2.2F, 0.6F, 0.1F);
         case "rightArm" -> new Vec3f(0.3F, 0.0F, 0.25F);
         case "head" -> new Vec3f((Float)v.getX() + 0.15F, (Float)v.getY(), (Float)v.getZ());
         case "body" -> new Vec3f(0.12F, 0.0F, 0.0F);
         default -> v;
      };
   }

   private Vec3f palm(String part, float t, float pitch, Vec3f v) {
      float k = smooth(0.0F, 3.0F, t);

      return switch (part) {
         case "rightArm" -> new Vec3f(Mth.lerp(k, 0.4F, -1.55F + pitch), -0.1F, 0.0F);
         case "leftArm" -> new Vec3f(0.45F, 0.0F, -0.2F);
         case "body" -> new Vec3f(0.1F * k, -0.3F * k, 0.0F);
         case "rightLeg" -> new Vec3f(0.35F, 0.0F, 0.05F);
         case "leftLeg" -> new Vec3f(-0.4F, 0.0F, -0.05F);
         default -> v;
      };
   }

   private Vec3f ring(String part, float t, Vec3f v) {
      return switch (part) {
         case "leftArm" -> new Vec3f(-1.9F, 0.75F, 0.15F);
         case "rightArm" -> new Vec3f(0.25F, 0.0F, 0.2F);
         case "head" -> new Vec3f((Float)v.getX() + 0.35F, (Float)v.getY(), (Float)v.getZ());
         default -> v;
      };
   }

   private Vec3f focus(String part, Vec3f v) {
      return switch (part) {
         case "rightArm" -> new Vec3f(-2.6F, -0.55F, 0.2F);
         case "leftArm" -> new Vec3f(-0.3F, 0.0F, -0.15F);
         default -> v;
      };
   }

   private Vec3f call(String part, float t, Vec3f v) {
      float shake = Mth.sin(t * 1.7F) * 0.03F;

      return switch (part) {
         case "leftArm" -> new Vec3f(-2.6F + shake, 0.15F, -0.35F);
         case "rightArm" -> new Vec3f(0.35F, 0.0F, 0.3F);
         case "body" -> new Vec3f(-0.08F, 0.0F, 0.0F);
         case "head" -> new Vec3f((Float)v.getX() - 0.3F, (Float)v.getY(), (Float)v.getZ());
         case "rightLeg" -> new Vec3f(0.2F, 0.0F, 0.12F);
         case "leftLeg" -> new Vec3f(-0.3F, 0.0F, -0.12F);
         default -> v;
      };
   }
}
