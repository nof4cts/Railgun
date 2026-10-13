package com.geto.csm.client;

import com.geto.csm.entity.DragonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent.Pre;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class DragonRiderPose implements IAnimation {
   private static final Set<AbstractClientPlayer> ADDED = Collections.newSetFromMap(new WeakHashMap<>());
   private final AbstractClientPlayer player;
   private float partial;

   private DragonRiderPose(AbstractClientPlayer player) {
      this.player = player;
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.END && mc.level != null) {
         for (AbstractClientPlayer p : mc.level.players()) {
            if (ADDED.add(p)) {
               try {
                  PlayerAnimationAccess.getPlayerAnimLayer(p).addAnimLayer(1400, new DragonRiderPose(p));
               } catch (RuntimeException var5) {
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderPlayer(Pre e) {
      Player p = e.getEntity();
      if (p.getVehicle() instanceof DragonEntity d && d.clientSeatMat != null && System.nanoTime() - d.clientSeatNanos <= 250000000L) {
         float pt = e.getPartialTick();
         Vec3 dragonPos = d.getPosition(pt);
         Vec3 riderPos = p.getPosition(pt);
         Vec3 off = e.getRenderer().getRenderOffset((AbstractClientPlayer)p, pt);
         Vec3 delta = dragonPos.subtract(riderPos).subtract(off);
         PoseStack ps = e.getPoseStack();
         ps.translate(delta.x, delta.y, delta.z);
         Matrix4f m = new Matrix4f(d.clientSeatMat);
         ps.mulPoseMatrix(m);
         ps.last().normal().mul(new Matrix3f(m).invert().transpose());
         return;
      }
   }

   public boolean isActive() {
      return this.player.getVehicle() instanceof DragonEntity;
   }

   public void setupAnim(float tickDelta) {
      this.partial = tickDelta;
   }

   public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f v) {
      if (type != TransformType.ROTATION) {
         return v;
      } else {
         float t = ((float)this.player.tickCount + this.partial) * 0.15F;
         float sway = Mth.sin(t) * 0.06F;

         return switch (part) {
            case "body" -> new Vec3f(0.45F + sway, (Float)v.getY(), (Float)v.getZ());
            case "head" -> new Vec3f((Float)v.getX() - 0.35F, (Float)v.getY(), (Float)v.getZ());
            case "rightArm" -> new Vec3f(-1.15F + sway, -0.25F, 0.0F);
            case "leftArm" -> new Vec3f(-1.15F - sway, 0.25F, 0.0F);
            case "rightLeg" -> new Vec3f(-1.2F, 0.35F, 0.45F);
            case "leftLeg" -> new Vec3f(-1.2F, -0.35F, -0.45F);
            default -> v;
         };
      }
   }
}
