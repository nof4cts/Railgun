package com.ryu.mod.fx.client;

import com.jjk.mod.client.JjkPrefs;
import com.ryu.mod.fx.CinePacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class RyuCine {
   private static RyuCine.CamEntity cam;
   private static int kind = -1;
   private static int age;
   private static int casterId;
   private static CameraType prevType;

   private RyuCine() {
   }

   public static boolean active() {
      return kind >= 0;
   }

   static int length() {
      return kind == 0 ? 44 : 66;
   }

   public static void start(CinePacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null && !JjkPrefs.skipCutscenes() && kind < 0) {
         Entity caster = mc.level.getEntity(p.caster);
         if (caster != null && !(caster.distanceTo(mc.player) > 48.0F)) {
            kind = p.kind;
            casterId = p.caster;
            age = 0;
            prevType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cam = new RyuCine.CamEntity(mc.level);
            place(0.0F);
            mc.setCameraEntity(cam);
         }
      }
   }

   private static void stop() {
      Minecraft mc = Minecraft.getInstance();
      kind = -1;
      if (mc.player != null) {
         mc.setCameraEntity(mc.player);
      }

      if (prevType != null) {
         mc.options.setCameraType(prevType);
      }

      cam = null;
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END && kind >= 0) {
         Minecraft mc = Minecraft.getInstance();
         Entity caster = mc.level == null ? null : mc.level.getEntity(casterId);
         if (caster != null && ++age <= length()) {
            if (kind == 0 && age == 30) {
               ScreenFx.flash(-520093697, 1.0F);
               ScreenFx.shake(1.6F, 14);
            }

            if (kind == 1) {
               if (age == 12 || age == 26) {
                  ScreenFx.flash(-1878989313, 0.5F);
               }

               if (age == 40) {
                  ScreenFx.flash(-253689857, 1.0F);
                  ScreenFx.shake(1.4F, 16);
               }
            }
         } else {
            stop();
         }
      }
   }

   @SubscribeEvent
   public static void onRender(RenderTickEvent e) {
      if (e.phase == Phase.START && kind >= 0 && cam != null) {
         place(e.renderTickTime);
      }
   }

   private static void place(float partial) {
      Minecraft mc = Minecraft.getInstance();
      Entity c = mc.level == null ? null : mc.level.getEntity(casterId);
      if (c != null && cam != null) {
         float t = (float)age + partial;
         Vec3 base = c.getPosition(partial);
         float yaw = c.getViewYRot(partial) * (float) (Math.PI / 180.0);
         Vec3 fwd = new Vec3((double)(-Mth.sin(yaw)), 0.0, (double)Mth.cos(yaw));
         Vec3 side = new Vec3(-fwd.z, 0.0, fwd.x);
         Vec3 up = new Vec3(0.0, 1.0, 0.0);
         Vec3 head = base.add(0.0, (double)c.getBbHeight() + 0.05, 0.0);
         Vec3 eye;
         Vec3 look;
         if (kind == 0) {
            if (t < 14.0F) {
               float k = t / 14.0F;
               eye = head.add(fwd.scale(2.4 - (double)k * 0.5)).add(side.scale(0.7)).add(up.scale(-0.9));
               look = head.add(0.0, 0.2, 0.0);
            } else if (t < 30.0F) {
               float k = (t - 14.0F) / 16.0F;
               eye = base.add(side.scale(7.0 - (double)k * 1.5)).add(fwd.scale(1.5)).add(up.scale(1.4));
               look = head.add(fwd.scale(1.5));
            } else {
               eye = head.add(fwd.scale(-3.5)).add(side.scale(1.3)).add(up.scale(0.6));
               look = head.add(fwd.scale(25.0));
            }
         } else if (t < 12.0F) {
            float k = t / 12.0F;
            eye = head.add(fwd.scale(1.9 - (double)k * 0.6)).add(side.scale(-0.35)).add(up.scale(-0.45));
            look = head.add(0.0, -0.1, 0.0);
         } else if (t < 26.0F) {
            float k = (t - 12.0F) / 14.0F;
            eye = base.add(fwd.scale(-2.2)).add(side.scale(0.9)).add(up.scale(0.3));
            look = head.add(up.scale((double)(2.0F + k * 30.0F))).add(fwd.scale(0.5));
         } else if (t < 40.0F) {
            double a = (double)yaw + (double)(t - 26.0F) * 0.03;
            eye = base.add(Math.sin(a) * 30.0, 24.0 + (double)(t - 26.0F) * 0.2, -Math.cos(a) * 30.0);
            look = base.add(0.0, 2.0, 0.0);
         } else {
            float k = (t - 40.0F) / 26.0F;
            eye = head.add(fwd.scale(-3.0 + (double)k * 0.6)).add(side.scale(1.4)).add(up.scale(-1.2 + (double)k * 1.6));
            look = head.add(fwd.scale(12.0)).add(up.scale((double)(3.0F - k * 2.0F)));
         }

         HitResult hit = mc.level.clip(new ClipContext(head, eye, Block.VISUAL, Fluid.NONE, c));
         if (hit.getType() != Type.MISS) {
            eye = hit.getLocation().add(head.subtract(eye).normalize().scale(0.3));
         }

         Vec3 d = look.subtract(eye);
         float cy = (float)(Mth.atan2(d.z, d.x) * 180.0F / (float)Math.PI) - 90.0F;
         float cp = (float)(-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0F / (float)Math.PI));
         cam.setPos(eye.x, eye.y, eye.z);
         cam.xo = cam.xOld = eye.x;
         cam.yo = cam.yOld = eye.y;
         cam.zo = cam.zOld = eye.z;
         cam.setYRot(cy);
         cam.yRotO = cy;
         cam.setXRot(cp);
         cam.xRotO = cp;
         if (mc.getCameraEntity() != cam) {
            mc.setCameraEntity(cam);
         }
      }
   }

   @SubscribeEvent
   public static void onHand(RenderHandEvent e) {
      if (kind >= 0) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onOverlay(Pre e) {
      if (kind >= 0) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      if (kind >= 0) {
         Minecraft mc = Minecraft.getInstance();
         GuiGraphics g = e.getGuiGraphics();
         int w = g.guiWidth();
         int h = g.guiHeight();
         float in = Math.min(1.0F, ((float)age + e.getPartialTick()) / 6.0F);
         float out = Math.min(1.0F, (float)(length() - age) / 6.0F);
         int bar = (int)((float)h * 0.11F * Math.min(in, out));
         g.fill(0, 0, w, bar, -16777216);
         g.fill(0, h - bar, w, h, -16777216);
         String jp = kind == 0 ? "高出力" : "領域展開";
         String en = kind == 0 ? "GRANITE BLAST" : "DOMAIN EXPANSION";
         int start = kind == 0 ? 12 : 42;
         if (age >= start) {
            float k = Math.min(1.0F, ((float)(age - start) + e.getPartialTick()) / 5.0F) * out;
            int a = (int)(255.0F * k) << 24;
            float s = 3.2F + (1.0F - k) * 2.0F;
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)h * 0.36F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.drawCenteredString(mc.font, jp, 0, 0, a | 8386303);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)h * 0.36F + 34.0F, 0.0F);
            g.pose().scale(1.8F, 1.8F, 1.0F);
            g.drawCenteredString(mc.font, en, 0, 0, a | 16777215);
            g.pose().popPose();
            g.fill(
               (int)((float)w / 2.0F - 90.0F * k),
               (int)((float)h * 0.36F + 28.0F),
               (int)((float)w / 2.0F + 90.0F * k),
               (int)((float)h * 0.36F + 29.0F),
               a | 58879
            );
         }
      }
   }

   static final class CamEntity extends Entity {
      CamEntity(Level level) {
         super(EntityType.MARKER, level);
         this.noPhysics = true;
      }

      protected void defineSynchedData() {
      }

      protected void readAdditionalSaveData(CompoundTag tag) {
      }

      protected void addAdditionalSaveData(CompoundTag tag) {
      }

      public Packet<ClientGamePacketListener> getAddEntityPacket() {
         throw new UnsupportedOperationException("client-only camera");
      }
   }
}
