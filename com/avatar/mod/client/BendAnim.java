package com.avatar.mod.client;

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
public final class BendAnim implements IAnimation {
   private static final Set<AbstractClientPlayer> ADDED = Collections.newSetFromMap(new WeakHashMap<>());
   private final AbstractClientPlayer player;
   private float partial;

   private BendAnim(AbstractClientPlayer player) {
      this.player = player;
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.END && mc.level != null) {
         for (AbstractClientPlayer p : mc.level.players()) {
            if (ADDED.add(p)) {
               try {
                  PlayerAnimationAccess.getPlayerAnimLayer(p).addAnimLayer(1400, new BendAnim(p));
               } catch (RuntimeException var5) {
                  LogUtils.getLogger().warn("Could not add bending animation layer", var5);
               }
            }
         }
      }
   }

   private long[] state() {
      return FireFx.POSES.get(this.player.getId());
   }

   public boolean isActive() {
      long[] s = this.state();
      return s != null && FireFx.gameTime() < s[1] + s[2] + 6L && !this.player.isSleeping() && !this.player.isPassenger();
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
         float t = (float)(FireFx.gameTime() - s[1]) + this.partial;
         float dur = (float)s[2];
         boolean stance = pose == 4
            || pose == 9
            || pose == 7
            || pose == 8
            || pose == 11
            || pose == 10
            || pose == 12
            || pose == 15
            || pose == 16
            || pose == 17
            || pose == 18
            || pose == 19;
         float w = stance ? smooth(0.0F, 4.0F, t) * (1.0F - smooth(dur, dur + 5.0F, t)) : smooth(0.0F, 1.6F, t) * (1.0F - smooth(dur - 3.0F, dur + 3.0F, t));
         if (w <= 0.001F) {
            return v;
         } else {
            float pitch = Mth.clamp(Mth.lerp(this.partial, this.player.xRotO, this.player.getXRot()), -70.0F, 70.0F) * (float) (Math.PI / 180.0);
            float yaw = Mth.wrapDegrees(
                  Mth.rotLerp(this.partial, this.player.yHeadRotO, this.player.yHeadRot)
                     - Mth.rotLerp(this.partial, this.player.yBodyRotO, this.player.yBodyRot)
               )
               * (float) (Math.PI / 180.0);

            Vec3f target = switch (pose) {
               case 1 -> this.punch(part, 1, pitch, yaw, v);
               case 2 -> this.punch(part, -1, pitch, yaw, v);
               case 3 -> this.kick(part, v);
               case 4 -> this.stance(part, t, v);
               case 5 -> this.thrust(part, pitch, yaw, v);
               case 6 -> this.whip(part, t, dur, v);
               case 7 -> this.inhale(part, v);
               case 8 -> this.exhale(part, v);
               case 9 -> this.generate(part, t, v);
               case 10 -> this.aim(part, pitch, yaw, v);
               case 11 -> this.redirect(part, pitch, yaw, v);
               case 12 -> this.blue(part, t, v);
               case 13 -> this.lash(part, t, pitch, yaw, v);
               case 14 -> this.stomp(part, t, v);
               case 15 -> this.heal(part, v);
               case 16 -> this.bloodPose(part, t, v);
               case 17 -> this.spin(part, t, v);
               case 18 -> this.scooter(part, v);
               case 19 -> this.glide(part, v);
               case 20 -> this.lift(part, t, v);
               case 21 -> this.hammer(part, t, v);
               default -> v;
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

   private Vec3f punch(String part, int side, float pitch, float yaw, Vec3f v) {
      String strike = side > 0 ? "rightArm" : "leftArm";
      String back = side > 0 ? "leftArm" : "rightArm";
      String front = side > 0 ? "leftLeg" : "rightLeg";
      String rear = side > 0 ? "rightLeg" : "leftLeg";
      if (part.equals(strike)) {
         return new Vec3f((float) (-Math.PI / 2) + pitch, yaw + 0.12F * (float)side, 0.0F);
      } else if (part.equals(back)) {
         return new Vec3f(0.55F, 0.0F, -0.1F * (float)side);
      } else if (part.equals(front)) {
         return new Vec3f(-0.42F, 0.0F, 0.0F);
      } else if (part.equals(rear)) {
         return new Vec3f(0.38F, 0.0F, 0.0F);
      } else {
         return part.equals("body") ? new Vec3f(0.06F, 0.3F * (float)side, 0.0F) : v;
      }
   }

   private Vec3f kick(String part, Vec3f v) {
      return switch (part) {
         case "rightLeg" -> new Vec3f(-1.55F, 0.0F, 0.05F);
         case "leftLeg" -> new Vec3f(0.12F, 0.0F, -0.05F);
         case "rightArm" -> new Vec3f(-0.75F, 0.0F, 0.45F);
         case "leftArm" -> new Vec3f(-1.0F, 0.35F, -0.2F);
         case "body" -> new Vec3f(-0.12F, -0.12F, 0.0F);
         default -> v;
      };
   }

   private Vec3f stance(String part, float t, Vec3f v) {
      float breath = 0.5F + 0.5F * Mth.sin(t * 0.18F);

      return switch (part) {
         case "rightLeg" -> new Vec3f(-0.1F, 0.0F, 0.32F);
         case "leftLeg" -> new Vec3f(-0.1F, 0.0F, -0.32F);
         case "rightArm" -> new Vec3f(0.42F, 0.0F, 0.22F + 0.04F * breath);
         case "leftArm" -> new Vec3f(0.42F, 0.0F, -0.22F - 0.04F * breath);
         case "body" -> new Vec3f(-0.05F - 0.06F * breath, 0.0F, 0.0F);
         case "head" -> new Vec3f((Float)v.getX() - 0.12F * breath, (Float)v.getY(), (Float)v.getZ());
         default -> v;
      };
   }

   private Vec3f whip(String part, float t, float dur, Vec3f v) {
      float k = smooth(0.0F, dur * 0.7F, t);
      float spin = Mth.lerp(k, 0.9F, -0.9F);

      return switch (part) {
         case "rightLeg" -> new Vec3f(-0.7F, spin * 0.4F, 1.25F);
         case "leftLeg" -> new Vec3f(0.1F, 0.0F, -0.12F);
         case "rightArm" -> new Vec3f(-0.4F, 0.0F, 0.9F);
         case "leftArm" -> new Vec3f(-0.6F, 0.0F, -1.0F);
         case "body" -> new Vec3f(-0.05F, spin, -0.18F);
         default -> v;
      };
   }

   private Vec3f inhale(String part, Vec3f v) {
      return switch (part) {
         case "rightArm" -> new Vec3f(0.6F, 0.0F, 0.55F);
         case "leftArm" -> new Vec3f(0.6F, 0.0F, -0.55F);
         case "rightLeg" -> new Vec3f(0.05F, 0.0F, 0.25F);
         case "leftLeg" -> new Vec3f(0.05F, 0.0F, -0.25F);
         case "body" -> new Vec3f(-0.18F, 0.0F, 0.0F);
         case "head" -> new Vec3f(-0.45F, (Float)v.getY(), 0.0F);
         default -> v;
      };
   }

   private Vec3f exhale(String part, Vec3f v) {
      return switch (part) {
         case "rightArm" -> new Vec3f(0.5F, 0.0F, 1.05F);
         case "leftArm" -> new Vec3f(0.5F, 0.0F, -1.05F);
         case "rightLeg" -> new Vec3f(0.45F, 0.0F, 0.2F);
         case "leftLeg" -> new Vec3f(-0.45F, 0.0F, -0.2F);
         case "body" -> new Vec3f(0.22F, 0.0F, 0.0F);
         case "head" -> new Vec3f(0.1F, (Float)v.getY(), 0.0F);
         default -> v;
      };
   }

   private Vec3f generate(String part, float t, Vec3f v) {
      float a = t * 0.35F;

      return switch (part) {
         case "rightArm" -> new Vec3f(-1.15F + 0.45F * Mth.sin(a), 0.2F, 0.35F + 0.3F * Mth.cos(a));
         case "leftArm" -> new Vec3f(-1.15F + 0.45F * Mth.sin(a + (float) Math.PI), -0.2F, -0.35F - 0.3F * Mth.cos(a + (float) Math.PI));
         case "rightLeg" -> new Vec3f(-0.05F, 0.0F, 0.28F);
         case "leftLeg" -> new Vec3f(-0.05F, 0.0F, -0.28F);
         case "body" -> new Vec3f(0.05F, 0.08F * Mth.sin(a), 0.0F);
         default -> v;
      };
   }

   private Vec3f aim(String part, float pitch, float yaw, Vec3f v) {
      return switch (part) {
         case "rightArm" -> new Vec3f((float) (-Math.PI / 2) + pitch, yaw + 0.05F, 0.0F);
         case "leftArm" -> new Vec3f(0.65F, 0.0F, -0.35F);
         case "rightLeg" -> new Vec3f(-0.38F, 0.0F, 0.1F);
         case "leftLeg" -> new Vec3f(0.42F, 0.0F, -0.1F);
         case "body" -> new Vec3f(0.05F, -0.35F, 0.0F);
         default -> v;
      };
   }

   private Vec3f redirect(String part, float pitch, float yaw, Vec3f v) {
      return switch (part) {
         case "leftArm" -> new Vec3f((float) (-Math.PI / 2) + pitch * 0.5F, yaw + 0.25F, 0.0F);
         case "rightArm" -> new Vec3f(-1.3F, 0.0F, 1.25F);
         case "rightLeg" -> new Vec3f(0.3F, 0.0F, 0.22F);
         case "leftLeg" -> new Vec3f(-0.35F, 0.0F, -0.18F);
         case "body" -> new Vec3f(0.04F, 0.2F, 0.0F);
         default -> v;
      };
   }

   private Vec3f blue(String part, float t, Vec3f v) {
      float k = smooth(4.0F, 14.0F, t);

      return switch (part) {
         case "rightArm" -> new Vec3f(Mth.lerp(k, 0.4F, -2.7F), 0.0F, Mth.lerp(k, 0.3F, 0.55F));
         case "leftArm" -> new Vec3f(Mth.lerp(k, 0.4F, -2.7F), 0.0F, Mth.lerp(k, -0.3F, -0.55F));
         case "rightLeg" -> new Vec3f(0.0F, 0.0F, 0.18F);
         case "leftLeg" -> new Vec3f(0.0F, 0.0F, -0.18F);
         case "head" -> new Vec3f(-0.35F * k, (Float)v.getY(), 0.0F);
         case "body" -> new Vec3f(-0.1F * k, 0.0F, 0.0F);
         default -> v;
      };
   }

   private Vec3f lash(String part, float t, float pitch, float yaw, Vec3f v) {
      float k = smooth(0.0F, 3.0F, t);

      return switch (part) {
         case "rightArm" -> new Vec3f((float) (-Math.PI / 2) + pitch, yaw + Mth.lerp(k, 1.2F, -0.2F), 0.0F);
         case "leftArm" -> new Vec3f(-0.5F, 0.0F, -0.5F);
         case "body" -> new Vec3f(0.05F, Mth.lerp(k, 0.4F, -0.3F), 0.0F);
         case "rightLeg" -> new Vec3f(0.3F, 0.0F, 0.1F);
         case "leftLeg" -> new Vec3f(-0.35F, 0.0F, -0.1F);
         default -> v;
      };
   }

   private Vec3f stomp(String part, float t, Vec3f v) {
      float up = smooth(0.0F, 3.0F, t) * (1.0F - smooth(3.0F, 5.0F, t));

      return switch (part) {
         case "rightLeg" -> new Vec3f(-1.3F * up, 0.0F, 0.3F);
         case "leftLeg" -> new Vec3f(0.05F, 0.0F, -0.3F);
         case "rightArm" -> new Vec3f(0.3F - 0.6F * up, 0.0F, 0.35F);
         case "leftArm" -> new Vec3f(0.3F - 0.6F * up, 0.0F, -0.35F);
         case "body" -> new Vec3f(0.18F * (1.0F - up), 0.0F, 0.0F);
         default -> v;
      };
   }

   private Vec3f heal(String part, Vec3f v) {
      return switch (part) {
         case "rightArm" -> new Vec3f(-1.05F, -0.45F, 0.0F);
         case "leftArm" -> new Vec3f(-1.05F, 0.45F, 0.0F);
         case "head" -> new Vec3f(0.35F, (Float)v.getY(), 0.0F);
         default -> v;
      };
   }

   private Vec3f bloodPose(String part, float t, Vec3f v) {
      float jerk = (int)(t / 3.0F) % 2 == 0 ? 0.08F : -0.08F;

      return switch (part) {
         case "rightArm" -> new Vec3f(-2.0F + jerk, -0.3F, 0.35F);
         case "leftArm" -> new Vec3f(-1.6F - jerk, 0.3F, -0.35F);
         case "head" -> new Vec3f(0.15F, (Float)v.getY() + jerk, 0.0F);
         case "body" -> new Vec3f(0.08F, 0.0F, 0.0F);
         default -> v;
      };
   }

   private Vec3f spin(String part, float t, Vec3f v) {
      float a = t * 0.9F;

      return switch (part) {
         case "rightArm" -> new Vec3f(0.0F, 0.0F, 1.45F);
         case "leftArm" -> new Vec3f(0.0F, 0.0F, -1.45F);
         case "body" -> new Vec3f(0.0F, a, 0.0F);
         case "head" -> new Vec3f((Float)v.getX(), a, 0.0F);
         default -> v;
      };
   }

   private Vec3f scooter(String part, Vec3f v) {
      return switch (part) {
         case "rightArm" -> new Vec3f(-0.35F, 0.0F, 0.95F);
         case "leftArm" -> new Vec3f(-0.35F, 0.0F, -0.95F);
         case "rightLeg" -> new Vec3f(-1.4F, 0.15F, 0.1F);
         case "leftLeg" -> new Vec3f(-1.4F, -0.15F, -0.1F);
         case "body" -> new Vec3f(0.08F, 0.0F, 0.0F);
         default -> v;
      };
   }

   private Vec3f glide(String part, Vec3f v) {
      return switch (part) {
         case "rightArm" -> new Vec3f(-2.9F, 0.0F, 0.35F);
         case "leftArm" -> new Vec3f(-2.9F, 0.0F, -0.35F);
         case "rightLeg" -> new Vec3f(0.2F, 0.0F, 0.05F);
         case "leftLeg" -> new Vec3f(0.1F, 0.0F, -0.05F);
         default -> v;
      };
   }

   private Vec3f lift(String part, float t, Vec3f v) {
      float k = smooth(0.0F, 5.0F, t);

      return switch (part) {
         case "rightArm" -> new Vec3f(Mth.lerp(k, 0.3F, -1.7F), 0.0F, 0.3F);
         case "leftArm" -> new Vec3f(Mth.lerp(k, 0.3F, -1.7F), 0.0F, -0.3F);
         case "rightLeg" -> new Vec3f(-0.05F, 0.0F, 0.3F);
         case "leftLeg" -> new Vec3f(-0.05F, 0.0F, -0.3F);
         default -> v;
      };
   }

   private Vec3f hammer(String part, float t, Vec3f v) {
      float up = smooth(0.0F, 9.0F, t);
      float down = smooth(9.0F, 12.0F, t);
      float arm = Mth.lerp(down, Mth.lerp(up, -0.6F, -3.0F), -0.55F);
      float bend = Mth.lerp(down, Mth.lerp(up, 0.0F, -0.18F), 0.55F);

      return switch (part) {
         case "rightArm" -> new Vec3f(arm, -0.2F, 0.05F);
         case "leftArm" -> new Vec3f(arm, 0.2F, -0.05F);
         case "body" -> new Vec3f(bend, 0.0F, 0.0F);
         case "head" -> new Vec3f((Float)v.getX() + bend * 0.4F, (Float)v.getY(), (Float)v.getZ());
         case "rightLeg" -> new Vec3f(-0.45F * down, 0.0F, 0.12F);
         case "leftLeg" -> new Vec3f(0.35F * down, 0.0F, -0.12F);
         default -> v;
      };
   }

   private Vec3f thrust(String part, float pitch, float yaw, Vec3f v) {
      return switch (part) {
         case "rightArm" -> new Vec3f((float) (-Math.PI / 2) + pitch, yaw + 0.16F, 0.0F);
         case "leftArm" -> new Vec3f((float) (-Math.PI / 2) + pitch, yaw - 0.16F, 0.0F);
         case "rightLeg" -> new Vec3f(0.5F, 0.0F, 0.12F);
         case "leftLeg" -> new Vec3f(-0.48F, 0.0F, -0.12F);
         case "body" -> new Vec3f(0.14F, 0.0F, 0.0F);
         default -> v;
      };
   }
}
