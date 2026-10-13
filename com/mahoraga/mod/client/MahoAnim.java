package com.mahoraga.mod.client;

import com.mojang.logging.LogUtils;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
public final class MahoAnim implements IAnimation {
   private static final Set<AbstractClientPlayer> ADDED = Collections.newSetFromMap(new WeakHashMap<>());
   static final Map<Integer, long[]> POSES = new HashMap<>();
   private final AbstractClientPlayer player;
   private float partial;

   private MahoAnim(AbstractClientPlayer player) {
      this.player = player;
   }

   static void pose(int playerId, int pose, int duration) {
      POSES.put(playerId, new long[]{(long)pose, MahoFx.now(), (long)duration});
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.END && mc.level != null) {
         for (AbstractClientPlayer p : mc.level.players()) {
            if (ADDED.add(p)) {
               try {
                  PlayerAnimationAccess.getPlayerAnimLayer(p).addAnimLayer(1500, new MahoAnim(p));
               } catch (RuntimeException var5) {
                  LogUtils.getLogger().warn("Could not add Mahoraga animation layer", var5);
               }
            }
         }

         long now = MahoFx.now();
         POSES.values().removeIf(s -> now > s[1] + s[2] + 10L);
      } else {
         if (mc.level == null) {
            POSES.clear();
         }
      }
   }

   private long[] state() {
      return POSES.get(this.player.getId());
   }

   public boolean isActive() {
      long[] s = this.state();
      return s != null && MahoFx.now() < s[1] + s[2] + 8L;
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
         float t = (float)(MahoFx.now() - s[1]) + this.partial;
         float dur = (float)s[2];
         float w = smooth(0.0F, 6.0F, t) * (1.0F - smooth(dur, dur + 8.0F, t));
         if (w <= 0.001F) {
            return v;
         } else {
            Vec3f target = switch ((int)s[0]) {
               case 1 -> summon(part, t, dur, v);
               case 2 -> crushed(part, t, v);
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

   private static Vec3f summon(String part, float t, float dur, Vec3f v) {
      float rise = smooth(dur - 40.0F, dur - 20.0F, t);
      float tremble = Mth.sin(t * 1.7F) * 0.02F;

      return switch (part) {
         case "rightArm" -> new Vec3f((float) (-Math.PI / 2) - 0.9F * rise + tremble, -0.08F, 0.0F);
         case "leftArm" -> new Vec3f(-1.35F - 0.9F * rise, 0.62F - 0.15F * rise, 0.0F);
         case "rightLeg" -> new Vec3f(0.12F, 0.0F, 0.16F);
         case "leftLeg" -> new Vec3f(-0.12F, 0.0F, -0.16F);
         case "body" -> new Vec3f(0.12F - 0.1F * rise, 0.0F, 0.0F);
         case "head" -> new Vec3f(0.3F - 0.55F * rise, 0.0F, 0.0F);
         default -> v;
      };
   }

   private static Vec3f crushed(String part, float t, Vec3f v) {
      float s1 = Mth.sin(t * 1.9F);
      float s2 = Mth.sin(t * 2.7F + 1.1F);

      return switch (part) {
         case "rightArm" -> new Vec3f(0.05F + 0.06F * s1, 0.0F, 0.04F);
         case "leftArm" -> new Vec3f(0.05F - 0.06F * s1, 0.0F, -0.04F);
         case "rightLeg" -> new Vec3f(0.18F + 0.25F * s2, 0.0F, 0.02F);
         case "leftLeg" -> new Vec3f(0.18F - 0.25F * s2, 0.0F, -0.02F);
         case "body" -> new Vec3f(-0.08F, 0.0F, 0.05F * s1);
         case "head" -> new Vec3f(-0.55F + 0.12F * s2, 0.15F * s1, 0.0F);
         default -> v;
      };
   }
}
