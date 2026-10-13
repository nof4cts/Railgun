package com.gojo.mod.cine.client;

import com.gojo.mod.cine.Cine;
import com.jjk.mod.client.JjkPrefs;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.client.event.ViewportEvent.ComputeFov;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class CineClient {
   private static final List<CineClient.Scene> SCENES = new ArrayList<>();
   private static CineClient.Scene directing;
   private static CineClient.CamEntity cam;
   private static CameraType prevType;
   private static float fov = 70.0F;
   private static float roll;
   private static float shake;
   private static Matrix4f lastView = new Matrix4f();
   private static Matrix4f lastProj = new Matrix4f();
   private static CloudStatus savedClouds;
   static final float[] BLUE_A = new float[]{0.22F, 0.5F, 1.0F};
   static final float[] BLUE_B = new float[]{0.85F, 0.95F, 1.0F};
   static final float[] RED_A = new float[]{1.0F, 0.12F, 0.08F};
   static final float[] RED_B = new float[]{1.0F, 0.85F, 0.6F};
   static final float[] PUR_A = new float[]{0.55F, 0.18F, 1.0F};
   static final float[] PUR_B = new float[]{0.95F, 0.85F, 1.0F};

   private CineClient() {
   }

   public static void start(Cine.Packet p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         CineClient.Scene s = new CineClient.Scene(p);
         SCENES.add(s);
         boolean cinematic = p.kind == 2 || p.kind == 3 || p.kind == 4;
         if (cinematic && !JjkPrefs.skipCutscenes() && directing == null && mc.player.position().distanceTo(p.pos) < 48.0) {
            directing = s;
            prevType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cam = new CineClient.CamEntity(mc.level);
         }

         if (p.kind == 0) {
            s.title(0, 26 + s.travel, "術式順転「蒼」", "CURSED TECHNIQUE LAPSE: BLUE", 5939455, true);
            shake = Math.max(shake, 0.3F);
         } else if (p.kind == 1) {
            s.title(0, 26 + s.travel, "術式反転「赫」", "CURSED TECHNIQUE REVERSAL: RED", 16728112, true);
            shake = Math.max(shake, 0.4F);
         }

         if (p.kind == 4 && savedClouds == null) {
            savedClouds = (CloudStatus)mc.options.cloudStatus().get();
            mc.options.cloudStatus().set(CloudStatus.OFF);
         }
      }
   }

   private static void endCut() {
      Minecraft mc = Minecraft.getInstance();
      directing = null;
      cam = null;
      if (mc.player != null) {
         mc.setCameraEntity(mc.player);
      }

      if (prevType != null) {
         mc.options.setCameraType(prevType);
      }

      roll = 0.0F;
   }

   static float[] screen(Vec3 p) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
      Vector4f v = new Vector4f((float)(p.x - c.x), (float)(p.y - c.y), (float)(p.z - c.z), 1.0F);
      lastView.transform(v);
      lastProj.transform(v);
      return v.w <= 0.01F
         ? new float[]{0.5F, 0.5F}
         : new float[]{Mth.clamp(v.x / v.w * 0.5F + 0.5F, 0.05F, 0.95F), Mth.clamp(v.y / v.w * 0.5F + 0.5F, 0.05F, 0.95F)};
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            SCENES.clear();
            Impact.clear();
            if (directing != null) {
               endCut();
            }
         } else if (!mc.isPaused()) {
            Iterator<CineClient.Scene> it = SCENES.iterator();

            while (it.hasNext()) {
               CineClient.Scene s = it.next();
               s.tick();
               if (s.done()) {
                  it.remove();
               }
            }

            Impact.tick();
            if (savedClouds != null) {
               boolean voidOpen = false;

               for (CineClient.Scene s : SCENES) {
                  voidOpen |= s.kind == 4;
               }

               if (!voidOpen) {
                  mc.options.cloudStatus().set(savedClouds);
                  savedClouds = null;
               }
            }

            shake = Math.max(0.0F, shake * 0.88F - 0.01F);
            if (directing != null) {
               boolean skip = mc.options.keyShift.isDown() && directing.age > 10 && mc.screen == null;
               if (skip || directing.age >= directing.cutEnd() || !SCENES.contains(directing)) {
                  endCut();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderTick(RenderTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.START && directing != null && cam != null && mc.level != null) {
         double[] shot = directing.shot(e.renderTickTime);
         Vec3 eye = new Vec3(shot[0], shot[1], shot[2]);
         Vec3 subject = directing.o.add(0.0, 1.62, 0.0);
         BlockHitResult hit = mc.level.clip(new ClipContext(subject, eye, Block.VISUAL, Fluid.NONE, mc.player));
         if (hit.getType() != Type.MISS) {
            eye = hit.getLocation().add(subject.subtract(eye).normalize().scale(0.35));
         }

         Vec3 d = new Vec3(shot[3], shot[4], shot[5]).subtract(eye);
         fov = (float)shot[6];
         roll = (float)shot[7];
         float yaw = (float)(Mth.atan2(d.z, d.x) * 180.0F / (float)Math.PI) - 90.0F;
         float pitch = (float)(-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0F / (float)Math.PI));
         cam.setPos(eye.x, eye.y, eye.z);
         cam.xo = cam.xOld = eye.x;
         cam.yo = cam.yOld = eye.y;
         cam.zo = cam.zOld = eye.z;
         cam.setYRot(yaw);
         cam.yRotO = yaw;
         cam.setXRot(pitch);
         cam.xRotO = pitch;
         if (mc.getCameraEntity() != cam) {
            mc.setCameraEntity(cam);
         }
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent e) {
      if (CineShaders.ready()) {
         Matrix4f view = e.getPoseStack().last().pose();
         Matrix4f proj = e.getProjectionMatrix();
         float partial = e.getPartialTick();
         Vec3 camPos = e.getCamera().getPosition();
         Stage stage = e.getStage();
         if (stage == Stage.AFTER_CUTOUT_BLOCKS) {
            for (CineClient.Scene s : SCENES) {
               float f = s.voidFadeFor(camPos, partial);
               if (f > 0.0F) {
                  Gfx.setCamera(view, 1.0E9F);
                  ShaderInstance sh = CineShaders.voidsky;
                  Gfx.f(sh, "Time", (float)s.age + partial);
                  Gfx.f(sh, "Fade", f);
                  Gfx.v3(sh, "IrisDir", (float)s.irisDir.x, (float)s.irisDir.y, (float)s.irisDir.z);
                  Gfx.begin();
                  Gfx.skyBox(40.0F);
                  Gfx.flush(sh, view, proj, false);
                  if (f > 0.98F) {
                     RenderSystem.clear(256, Minecraft.ON_OSX);
                  }
                  break;
               }
            }
         } else if (stage == Stage.AFTER_PARTICLES) {
            lastView = new Matrix4f(view);
            lastProj = new Matrix4f(proj);

            for (CineClient.Scene sx : SCENES) {
               sx.render(camPos, view, proj, partial);
            }
         } else if (stage == Stage.AFTER_LEVEL) {
            Impact.render(partial);
         }
      }
   }

   @SubscribeEvent
   public static void onAngles(ComputeCameraAngles e) {
      float r = directing != null ? roll : 0.0F;
      if (shake > 0.001F) {
         float t = (float)((double)System.nanoTime() / 4.0E7 % 10000.0);
         e.setYaw(e.getYaw() + (Mth.sin(t * 2.7F) + Mth.sin(t * 5.9F) * 0.5F) * shake);
         e.setPitch(e.getPitch() + (Mth.cos(t * 3.3F) + Mth.sin(t * 7.1F) * 0.5F) * shake);
         r += Mth.sin(t * 4.1F) * shake * 0.8F;
      }

      if (r != 0.0F) {
         e.setRoll(e.getRoll() + r);
      }
   }

   @SubscribeEvent
   public static void onFov(ComputeFov e) {
      if (directing != null) {
         e.setFOV((double)fov);
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onOverlay(Pre e) {
      if (directing != null) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onHand(RenderHandEvent e) {
      if (directing != null) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      GuiGraphics g = e.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      float partial = e.getPartialTick();
      Minecraft mc = Minecraft.getInstance();
      if (directing != null) {
         float t = (float)directing.age + partial;
         float k = smooth(t / 10.0F) * smooth(((float)directing.cutEnd() - t) / 10.0F);
         int bar = (int)((float)h * 0.12F * k);
         g.fill(0, 0, w, bar, -16777216);
         g.fill(0, h - bar, w, h, -16777216);
         float black = Math.max(1.0F - t / 8.0F, Mth.clamp((t - (float)(directing.cutEnd() - 8)) / 8.0F, 0.0F, 1.0F));
         if (black > 0.01F) {
            g.fill(0, 0, w, h, argb(black, 0));
         }
      }

      for (CineClient.Scene s : SCENES) {
         s.gui(g, w, h, partial, mc);
      }
   }

   static int argb(float a, int rgb) {
      return Mth.clamp((int)(a * 255.0F), 0, 255) << 24 | rgb & 16777215;
   }

   static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   static float hash(float x) {
      float s = Mth.sin(x * 12.9898F) * 43758.547F;
      return s - (float)Mth.floor(s);
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

   static final class Scene {
      final Cine.Packet p;
      final byte kind;
      final Vec3 o;
      final Vec3 fwd;
      final Vec3 right;
      final Vec3 up = new Vec3(0.0, 1.0, 0.0);
      final Vec3 irisDir;
      final List<CineClient.Title> titles = new ArrayList<>();
      final Random rnd;
      int age;
      boolean victim;
      final int travel;

      Scene(Cine.Packet p) {
         this.p = p;
         this.kind = p.kind;
         this.o = p.pos;
         Vec3 f = new Vec3(p.dir.x, 0.0, p.dir.z);
         this.fwd = f.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : f.normalize();
         this.right = this.fwd.cross(this.up).normalize();
         this.irisDir = this.fwd.add(0.0, 0.45, 0.0).normalize();
         this.rnd = new Random(p.seed);
         this.travel = this.kind != 0 && this.kind != 1 ? 0 : Cine.travel(p.from, p.pos);
         switch (this.kind) {
            case 2:
               this.title(6, 30, "術式順転「蒼」", "LAPSE: BLUE", 5939455, true);
               this.title(32, 56, "術式反転「赫」", "REVERSAL: RED", 16728112, true);
               this.title(84, 114, "虚式「茈」", "HOLLOW TECHNIQUE: PURPLE", 11563263, false);
               break;
            case 3:
               this.title(8, 40, "術式順転「蒼」", "MAXIMUM OUTPUT: BLUE", 5939455, true);
               this.title(44, 74, "術式反転「赫」", "MAXIMUM OUTPUT: RED", 16728112, true);
               this.title(126, 166, "虚式「茈」200%", "HOLLOW PURPLE — 200%", 12615935, false);
               break;
            case 4:
               this.title(10, 46, "領域展開", "DOMAIN EXPANSION", 14215423, false);
               this.title(76, 116, "無量空処", "INFINITE VOID", 10475775, false);
         }
      }

      void title(int s, int e, String jp, String en, int color, boolean small) {
         this.titles.add(new CineClient.Title(s, e, jp, en, color, small));
      }

      int cutEnd() {
         return switch (this.kind) {
            case 2 -> 150;
            case 3 -> 215;
            case 4 -> 120;
            default -> 0;
         };
      }

      boolean done() {
         return switch (this.kind) {
            case 0 -> this.age > 40 + this.travel;
            case 1 -> this.age > 30 + this.travel;
            case 2 -> this.age > 160;
            case 3 -> this.age > 225;
            default -> this.age > 304;
         };
      }

      boolean big() {
         return this.kind == 3;
      }

      int merge() {
         return this.big() ? 120 : 76;
      }

      int fire() {
         return this.big() ? 168 : 112;
      }

      void tick() {
         this.age++;
         Minecraft mc = Minecraft.getInstance();
         if ((this.kind == 0 || this.kind == 1) && this.age == this.travel) {
            float[] sc = CineClient.screen(this.p.pos);
            if (this.kind == 0) {
               Impact.hit(3, 2, 5, sc[0], sc[1]);
               CineClient.shake = Math.max(CineClient.shake, 0.8F);
            } else {
               Impact.hit(2, 2, 3, sc[0], sc[1]);
               Impact.hit(0, 1, 4, sc[0], sc[1]);
               CineClient.shake = Math.max(CineClient.shake, 1.6F);
            }
         }

         if (this.kind == 2 || this.kind == 3) {
            if (this.age == this.merge()) {
               float[] sc = CineClient.screen(this.mergePos());
               Impact.hit(1, 3, 2, sc[0], sc[1]);
               CineClient.shake = Math.max(CineClient.shake, this.big() ? 2.5F : 1.6F);
            }

            if (this.age == this.merge() + 5) {
               float[] sc = CineClient.screen(this.mergePos());
               Impact.hit(0, 2, 6, sc[0], sc[1]);
            }

            if (this.big() && this.age == this.merge() + 9) {
               float[] sc = CineClient.screen(this.mergePos());
               Impact.hit(1, 2, 8, sc[0], sc[1]);
            }

            if (this.age == this.fire()) {
               float[] sc = CineClient.screen(this.mergePos());
               Impact.hit(0, 2, 1, sc[0], sc[1]);
               CineClient.shake = Math.max(CineClient.shake, this.big() ? 3.5F : 2.4F);
            }

            if (this.age == this.fire() + 3) {
               Impact.hit(1, 3, 10, 0.5F, 0.5F);
            }

            if (this.big() && this.age == this.fire() + 16) {
               Impact.hit(0, 1, 6, 0.5F, 0.5F);
               CineClient.shake = Math.max(CineClient.shake, 3.0F);
            }
         } else if (this.kind == 4) {
            if (this.age == 62) {
               Impact.hit(0, 3, 1, 0.5F, 0.5F);
            }

            if (this.age == 67) {
               Impact.hit(4, 5, 4, 0.5F, 0.5F);
               CineClient.shake = Math.max(CineClient.shake, 1.6F);
            }

            if (this.age == 72 && mc.player != null && mc.player.getId() != this.p.caster && mc.player.position().distanceTo(this.o) < 22.0) {
               this.victim = true;
            }
         }
      }

      Vec3 bluePos(double t) {
         double s = this.big() ? 2.2 : 1.0;
         Vec3 hand = this.big()
            ? this.o.add(0.0, 3.4, 0.0).add(this.right.scale(-2.6)).add(this.fwd.scale(0.5))
            : this.o.add(0.0, 1.35, 0.0).add(this.right.scale(-0.75)).add(this.fwd.scale(0.55));
         return this.converge(hand, t, -1);
      }

      Vec3 redPos(double t) {
         Vec3 hand = this.big()
            ? this.o.add(0.0, 3.4, 0.0).add(this.right.scale(2.6)).add(this.fwd.scale(0.5))
            : this.o.add(0.0, 1.35, 0.0).add(this.right.scale(0.75)).add(this.fwd.scale(0.55));
         return this.converge(hand, t, 1);
      }

      private Vec3 converge(Vec3 hand, double t, int side) {
         int startC = this.big() ? 74 : 54;
         if (t < (double)startC) {
            return hand;
         } else {
            double k = Mth.clamp((t - (double)startC) / (double)(this.merge() - startC), 0.0, 1.0);
            double e = k * k * (3.0 - 2.0 * k);
            Vec3 m = this.mergePos();
            Vec3 mid = hand.add(m.subtract(hand).scale(e));
            double ang = e * Math.PI * 3.0 + (side < 0 ? 0.0 : Math.PI);
            double rad = (1.0 - e) * (this.big() ? 1.6 : 0.5);
            return mid.add(this.right.scale(Math.cos(ang) * rad)).add(0.0, Math.sin(ang) * rad * 0.6, 0.0);
         }
      }

      Vec3 mergePos() {
         return this.big() ? this.o.add(0.0, 3.8, 0.0).add(this.fwd.scale(3.2)) : this.o.add(0.0, 1.5, 0.0).add(this.fwd.scale(1.9));
      }

      Vec3 shotPos(double t) {
         double speed = this.big() ? 4.0 : 3.0;
         Vec3 start = this.o.add(0.0, 1.62, 0.0).add(this.p.dir.scale(2.5));
         double d = Math.min((t - (double)this.fire()) * speed, this.big() ? 150.0 : 64.0);
         return start.add(this.p.dir.scale(d));
      }

      double[] shot(float partial) {
         double t = (double)((float)this.age + partial);
         double fv = 60.0;
         double rl = 0.0;
         Vec3 head = this.o.add(0.0, 1.62, 0.0);
         if (this.kind == 4) {
            Vec3 eye;
            Vec3 look;
            if (t < 26.0) {
               double k = t / 26.0;
               eye = head.add(this.fwd.scale(1.5 - 0.3 * k)).add(this.right.scale(0.25)).add(0.0, 0.05, 0.0);
               look = head.add(0.0, 0.05, 0.0);
               fv = 42.0;
            } else if (t < 50.0) {
               double k = (t - 26.0) / 24.0;
               eye = this.o.add(this.fwd.scale(2.8)).add(this.right.scale(-1.6 + 0.6 * k)).add(0.0, 0.35, 0.0);
               look = head.add(0.0, 0.25, 0.0);
               fv = 55.0;
               rl = -6.0 + 4.0 * k;
            } else if (t < 72.0) {
               double k = (double)CineClient.smooth((float)((t - 50.0) / 22.0));
               eye = head.add(this.fwd.scale(2.0 + 10.0 * k)).add(0.0, 0.5 + 3.0 * k, 0.0).add(this.right.scale(1.0 * k));
               look = head;
               fv = 50.0 + 25.0 * k;
            } else {
               double k = t - 72.0;
               double a = k * 0.025;
               Vec3 rad = this.fwd.scale(Math.cos(a)).add(this.right.scale(Math.sin(a)));
               eye = head.add(rad.scale(5.5 - k * 0.02)).add(0.0, 0.8, 0.0);
               look = head.add(this.irisDir.scale(1.5)).add(0.0, 0.2, 0.0);
               fv = 66.0;
            }

            return new double[]{eye.x, eye.y, eye.z, look.x, look.y, look.z, fv, rl};
         } else {
            boolean big = this.big();
            int merge = this.merge();
            int fire = this.fire();
            int redStart = big ? 40 : 28;
            int convStart = big ? 74 : 54;
            double sc = big ? 2.6 : 1.0;
            Vec3 eye;
            Vec3 look;
            if (t < (double)redStart) {
               Vec3 b = this.bluePos(t);
               double k = t / (double)redStart;
               eye = b.add(this.right.scale(-1.6 * sc)).add(this.fwd.scale(1.4 * sc)).add(0.0, 0.2 * sc, 0.0).add(this.fwd.scale(-0.3 * k));
               look = b.add(this.right.scale(0.4 * sc));
               fv = 48.0;
               rl = 4.0;
            } else if (t < (double)convStart) {
               Vec3 r = this.redPos(t);
               double k = (t - (double)redStart) / (double)(convStart - redStart);
               eye = r.add(this.right.scale(1.6 * sc)).add(this.fwd.scale(1.4 * sc)).add(0.0, 0.2 * sc, 0.0).add(this.fwd.scale(-0.3 * k));
               look = r.add(this.right.scale(-0.4 * sc));
               fv = 48.0;
               rl = -4.0;
            } else if (t < (double)(merge + 6)) {
               double k = (t - (double)convStart) / (double)(merge + 6 - convStart);
               double a = k * 0.5 - 0.25;
               Vec3 dirv = this.fwd.scale(Math.cos(a)).add(this.right.scale(Math.sin(a)));
               eye = this.mergePos().add(dirv.scale((big ? 9.0 : 3.6) - k * (big ? 2.0 : 0.8))).add(0.0, big ? -1.5 : -0.4, 0.0);
               look = this.mergePos().add(0.0, big ? -0.6 : -0.1, 0.0);
               fv = 55.0;
            } else if (t < (double)fire) {
               double k = (t - (double)merge - 6.0) / (double)(fire - merge - 6);
               eye = this.mergePos()
                  .add(this.fwd.scale((big ? 11.0 : 4.2) - k * (big ? 3.0 : 1.2)))
                  .add(this.right.scale(big ? 2.5 : 0.8))
                  .add(0.0, big ? -2.4 : -0.3, 0.0);
               look = big ? this.mergePos().add(0.0, -1.2, 0.0) : head;
               fv = 50.0 - 8.0 * k;
               rl = 3.0 * k;
            } else {
               double k = (t - (double)fire) / 20.0;
               eye = head.add(this.fwd.scale(-3.2 - (double)(big ? 3 : 0))).add(this.right.scale(1.1)).add(0.0, big ? 2.4 : 0.55, 0.0);
               look = head.add(this.p.dir.scale(20.0));
               fv = 62.0 + Math.min(14.0, k * 14.0);
            }

            return new double[]{eye.x, eye.y, eye.z, look.x, look.y, look.z, fv, rl};
         }
      }

      float voidFadeFor(Vec3 camPos, float partial) {
         if (this.kind != 4) {
            return 0.0F;
         } else {
            double t = (double)((float)this.age + partial);
            if (!(t < 70.0) && !(t > 302.0)) {
               boolean inside = camPos.distanceTo(this.o) < 23.0 || CineClient.directing == this;
               if (!inside) {
                  return 0.0F;
               } else {
                  float in = (float)Mth.clamp((t - 70.0) / 4.0, 0.0, 1.0);
                  float out = (float)Mth.clamp((t - 292.0) / 10.0, 0.0, 1.0);
                  return in * (1.0F - out);
               }
            } else {
               return 0.0F;
            }
         }
      }

      void render(Vec3 cam, Matrix4f view, Matrix4f proj, float partial) {
         double t = (double)((float)this.age + partial);
         Gfx.setCamera(view, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.9F);
         ShaderInstance orbSh = CineShaders.orb;
         ShaderInstance en = CineShaders.energy;
         Gfx.f(orbSh, "Time", (float)t);
         Gfx.f(en, "Time", (float)t);
         if (this.kind == 0 || this.kind == 1) {
            this.renderFlick(cam, view, proj, t);
         }

         if (this.kind == 0 && t >= (double)this.travel) {
            double u = t - (double)this.travel;
            float k = (float)Mth.clamp(1.0 - Math.abs(u - 15.0) / 15.0, 0.0, 1.0);
            this.orb(cam, view, proj, this.p.pos, 1.25F * CineClient.smooth((float)u / 4.0F) * (0.6F + 0.4F * k), 0, CineClient.BLUE_A, CineClient.BLUE_B, 1.3F);
            this.spiral(cam, view, proj, this.p.pos, 5.0F, u, CineClient.BLUE_A, 18, -1, k);
         } else if (this.kind == 1 && t >= (double)this.travel) {
            double u = t - (double)this.travel;
            float k = (float)Math.max(0.0, 1.0 - u / 14.0);
            this.orb(cam, view, proj, this.p.pos, 1.0F + (float)u * 0.12F, 1, CineClient.RED_A, CineClient.RED_B, k * 1.6F);
            this.burstLines(cam, view, proj, this.p.pos, (float)u, CineClient.RED_A, 26, 1.0F);
            this.ring(cam, view, proj, this.p.pos, (float)(1.0 + u * 0.9), CineClient.RED_B, (float)Math.max(0.0, 1.0 - u / 18.0));
         } else if (this.kind == 2 || this.kind == 3) {
            this.renderPurple(cam, view, proj, t);
         } else if (this.kind == 4) {
            this.renderVoid(cam, view, proj, t);
         }
      }

      private void renderFlick(Vec3 cam, Matrix4f view, Matrix4f proj, double t) {
         if (!(t > (double)(this.travel + 1))) {
            boolean blue = this.kind == 0;
            float[] a = blue ? CineClient.BLUE_A : CineClient.RED_A;
            float[] b = blue ? CineClient.BLUE_B : CineClient.RED_B;
            double k = Math.min(1.0, t / (double)this.travel);
            Vec3 at = this.p.from.add(this.p.pos.subtract(this.p.from).scale(k));
            if (t < 2.0) {
               this.glow(cam, view, proj, this.p.from, 1.4F * (float)(1.0 - t / 2.0), b, 0.9F, 4);
            }

            this.orb(cam, view, proj, at, 0.28F, blue ? 0 : 1, a, b, 1.3F);
            Vec3 tail = this.p.from.add(this.p.pos.subtract(this.p.from).scale(Math.max(0.0, k - 0.35)));
            Vec3 x0 = at.subtract(cam);
            Vec3 x1 = tail.subtract(cam);
            Gfx.begin();
            Gfx.ribbon(
               new float[]{(float)x0.x, (float)x0.y, (float)x0.z, (float)x1.x, (float)x1.y, (float)x1.z},
               new float[]{0.24F, 0.04F},
               new float[]{0.05F, 1.0F},
               a[0],
               a[1],
               a[2],
               1.0F
            );
            draw(CineShaders.energy, 1, view, proj);
            if (blue) {
               this.spiral(cam, view, proj, at, 1.2F, t, a, 6, -1, 0.8F);
            } else {
               this.burstLines(cam, view, proj, at, (float)(t % 6.0), a, 6, 0.5F);
            }
         }
      }

      private void renderPurple(Vec3 cam, Matrix4f view, Matrix4f proj, double t) {
         boolean big = this.big();
         float sc = big ? 2.4F : 1.0F;
         int blueStart = big ? 8 : 6;
         int redStart = big ? 40 : 28;
         int merge = this.merge();
         int fire = this.fire();
         if (t >= (double)blueStart && t < (double)merge) {
            float g = CineClient.smooth((float)(t - (double)blueStart) / 18.0F) * sc;
            Vec3 b = this.bluePos(t);
            this.orb(cam, view, proj, b, 0.42F * g, 0, CineClient.BLUE_A, CineClient.BLUE_B, 1.4F);
            this.spiral(cam, view, proj, b, 2.4F * g, t, CineClient.BLUE_A, 12, -1, 1.0F);
         }

         if (t >= (double)redStart && t < (double)merge) {
            float g = CineClient.smooth((float)(t - (double)redStart) / 18.0F) * sc;
            Vec3 r = this.redPos(t);
            this.orb(cam, view, proj, r, 0.38F * g, 1, CineClient.RED_A, CineClient.RED_B, 1.5F);
            this.burstLines(cam, view, proj, r, (float)t, CineClient.RED_A, 10, g * 0.5F);
         }

         if (t >= (double)(merge - 3) && t < (double)(merge + 6)) {
            float k = (float)(1.0 - Math.abs(t - (double)merge) / 6.0);
            this.glow(cam, view, proj, this.mergePos(), 6.0F * sc * k + 0.5F, new float[]{1.0F, 0.95F, 1.0F}, k * 0.8F, 4);
            this.glow(cam, view, proj, this.mergePos(), 10.0F * sc * k, CineClient.PUR_A, k * 0.45F, 0);
         }

         if (t >= (double)merge && t < (double)(fire + 2)) {
            float g = CineClient.smooth((float)(t - (double)merge) / 10.0F) * (big ? 4.2F : 1.05F) * (1.0F + 0.04F * Mth.sin((float)t * 1.7F));
            Vec3 m = this.mergePos();
            this.orb(cam, view, proj, m, g, 2, CineClient.PUR_A, CineClient.PUR_B, 0.9F);
            this.arcs(cam, view, proj, m, g, t, 8 + (big ? 6 : 0));
            this.ringsAround(cam, view, proj, m, g * 1.7F, t, 3);
            this.spiral(cam, view, proj, m, g * 3.2F, t, CineClient.PUR_A, 10, -1, 0.6F);
         }

         if (t >= (double)fire && t < (double)(fire + (big ? 40 : 24))) {
            Vec3 s = this.shotPos(t);
            float rad = (big ? 6.2F : 3.0F) * CineClient.smooth((float)(t - (double)fire) / 4.0F);
            this.orb(cam, view, proj, s, rad, 2, CineClient.PUR_A, CineClient.PUR_B, 1.0F);
            this.arcs(cam, view, proj, s, rad, t, 10);
            Vec3 back = s.subtract(this.p.dir.scale(Math.min((t - (double)fire) * (double)(big ? 4 : 3), 40.0)));
            Gfx.begin();
            Vec3 a = s.subtract(cam);
            Vec3 b2 = back.subtract(cam);
            Gfx.ribbon(
               new float[]{(float)a.x, (float)a.y, (float)a.z, (float)b2.x, (float)b2.y, (float)b2.z},
               new float[]{rad * 1.2F, rad * 0.3F},
               new float[]{0.05F, 1.0F},
               CineClient.PUR_A[0],
               CineClient.PUR_A[1],
               CineClient.PUR_A[2],
               1.0F
            );
            draw(CineShaders.energy, 1, view, proj);

            for (int i = 0; i < 4; i++) {
               double tt = t - (double)fire - (double)(i * 5);
               if (tt > 0.0) {
                  Vec3 rp = this.shotPos((double)fire + tt * 0.3);
                  this.ring(cam, view, proj, rp, (float)((double)rad * (1.2 + tt * 0.25)), CineClient.PUR_B, (float)Math.max(0.0, 1.0 - tt / 20.0));
               }
            }
         }
      }

      private void renderVoid(Vec3 cam, Matrix4f view, Matrix4f proj, double t) {
         Vec3 head = this.o.add(0.0, 1.62, 0.0);
         if (t > 30.0 && t < 76.0) {
            float k = CineClient.smooth((float)(t - 30.0) / 40.0F);
            this.spiral(cam, view, proj, head, 4.0F * k + 1.0F, t, new float[]{0.6F, 0.8F, 1.0F}, 16, -1, k);
            this.glow(cam, view, proj, head, 2.5F * k, new float[]{0.7F, 0.85F, 1.0F}, 0.5F * k, 0);
         }

         double open = t - 72.0;
         if (open >= 0.0 && open < 230.0) {
            float life = (float)(Mth.clamp(open / 6.0, 0.0, 1.0) * Mth.clamp((230.0 - open) / 10.0, 0.0, 1.0));
            boolean inside = cam.distanceTo(this.o) < 23.0 || CineClient.directing == this;
            if (!inside) {
               ShaderInstance sh = CineShaders.voidsky;
               Gfx.f(sh, "Time", (float)t);
               Gfx.f(sh, "Fade", 0.94F * life);
               Gfx.v3(sh, "IrisDir", (float)this.irisDir.x, (float)this.irisDir.y, (float)this.irisDir.z);
               Matrix4f mv = new Matrix4f(view)
                  .translate((float)(this.o.x - cam.x), (float)(this.o.y - cam.y), (float)(this.o.z - cam.z))
                  .scale(22.0F * Mth.clamp((float)open / 5.0F, 0.05F, 1.0F));
               Gfx.meshBlend(CineShaders.sphere(), sh, mv, proj);
            } else {
               Gfx.begin();

               for (int i = 0; i < 60; i++) {
                  float h1 = CineClient.hash((float)i * 1.3F + 0.2F);
                  float h2 = CineClient.hash((float)i * 2.7F + 5.0F);
                  float h3 = CineClient.hash((float)i * 0.7F + 9.0F);
                  Vec3 dirv = new Vec3((double)(h1 * 2.0F - 1.0F), (double)(h2 * 2.0F - 1.0F), (double)(h3 * 2.0F - 1.0F)).normalize();
                  double ph = (open * (0.8 + (double)h1) * 0.06 + (double)h3) % 1.0;
                  Vec3 from = head.add(dirv.scale(26.0 - ph * 24.0));
                  Vec3 to = from.add(head.add(this.irisDir.scale(60.0)).subtract(from).normalize().scale((double)(4.0F + h2 * 6.0F)));
                  Vec3 a = from.subtract(cam);
                  Vec3 b = to.subtract(cam);
                  float al = (float)(Math.sin(ph * Math.PI) * 0.8 * (double)life);
                  Gfx.ribbon(
                     new float[]{(float)a.x, (float)a.y, (float)a.z, (float)b.x, (float)b.y, (float)b.z},
                     new float[]{0.04F, 0.08F},
                     new float[]{0.0F, 1.0F},
                     0.7F,
                     0.88F,
                     1.0F,
                     al
                  );
               }

               draw(CineShaders.energy, 1, view, proj);
               this.glow(cam, view, proj, head.add(0.0, -0.4, 0.0), 2.2F, new float[]{0.6F, 0.8F, 1.0F}, 0.35F * life, 0);
            }
         }
      }

      private void orb(Vec3 cam, Matrix4f view, Matrix4f proj, Vec3 at, float radius, int mode, float[] a, float[] b, float intensity) {
         if (!(radius < 0.01F)) {
            ShaderInstance sh = CineShaders.orb;
            Gfx.f(sh, "Mode", (float)mode);
            Gfx.f(sh, "Intensity", intensity);
            Gfx.f(sh, "Seed", (float)(this.p.seed & 63L));
            Gfx.v3(sh, "ColorA", a[0], a[1], a[2]);
            Gfx.v3(sh, "ColorB", b[0], b[1], b[2]);
            Matrix4f mv = new Matrix4f(view)
               .translate((float)(at.x - cam.x), (float)(at.y - cam.y), (float)(at.z - cam.z))
               .rotateY((float)this.age * 0.05F)
               .scale(radius);
            Gfx.meshBlend(CineShaders.sphere(), sh, mv, proj);
            float gk = mode == 2 ? 0.35F : 1.0F;
            this.glow(cam, view, proj, at, radius * 2.6F, a, 0.5F * gk * intensity / 1.5F, 0);
            this.glow(cam, view, proj, at, radius * 6.0F, a, 0.18F * gk * intensity / 1.5F, 0);
         }
      }

      private void glow(Vec3 cam, Matrix4f view, Matrix4f proj, Vec3 at, float half, float[] c, float a, int mode) {
         Gfx.begin();
         Gfx.billboard(at.x - cam.x, at.y - cam.y, at.z - cam.z, half, 0.0F, c[0], c[1], c[2], a);
         draw(CineShaders.energy, mode, view, proj);
      }

      private void ring(Vec3 cam, Matrix4f view, Matrix4f proj, Vec3 at, float half, float[] c, float a) {
         if (!(a <= 0.0F)) {
            this.glow(cam, view, proj, at, half, c, a, 2);
         }
      }

      private void spiral(Vec3 cam, Matrix4f view, Matrix4f proj, Vec3 at, float reach, double t, float[] c, int n, int dir, float a) {
         Gfx.begin();

         for (int i = 0; i < n; i++) {
            float h = CineClient.hash((float)i * 3.17F + 1.0F);
            double ph = (t * 0.045 * (double)(1.0F + h) + (double)h) % 1.0;
            double r0 = (double)reach * (1.0 - ph);
            double r1 = Math.max(0.05, r0 - (double)reach * 0.25);
            double a0 = (double)i * 2.39996 + ph * 5.0;
            double a1 = a0 + 0.7;
            double tilt = ((double)CineClient.hash((float)i * 7.1F) - 0.5) * 1.4;
            Vec3 p0 = at.add(this.right.scale(Math.cos(a0) * r0)).add(this.fwd.scale(Math.sin(a0) * r0)).add(0.0, Math.sin(a0 + tilt) * r0 * 0.6, 0.0);
            Vec3 p1 = at.add(this.right.scale(Math.cos(a1) * r1)).add(this.fwd.scale(Math.sin(a1) * r1)).add(0.0, Math.sin(a1 + tilt) * r1 * 0.6, 0.0);
            Vec3 x0 = p0.subtract(cam);
            Vec3 x1 = p1.subtract(cam);
            float al = (float)(Math.sin(ph * Math.PI) * (double)a);
            Gfx.ribbon(
               new float[]{(float)x0.x, (float)x0.y, (float)x0.z, (float)x1.x, (float)x1.y, (float)x1.z},
               new float[]{reach * 0.012F + 0.02F, reach * 0.03F + 0.03F},
               new float[]{0.0F, 1.0F},
               c[0],
               c[1],
               c[2],
               al
            );
         }

         draw(CineShaders.energy, 1, view, proj);
      }

      private void burstLines(Vec3 cam, Matrix4f view, Matrix4f proj, Vec3 at, float t, float[] c, int n, float a) {
         Gfx.begin();

         for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(
                  (double)(CineClient.hash((float)i * 1.1F) * 2.0F - 1.0F),
                  (double)(CineClient.hash((float)i * 2.3F) * 2.0F - 1.0F),
                  (double)(CineClient.hash((float)i * 3.7F) * 2.0F - 1.0F)
               )
               .normalize();
            double r0 = 0.3 + (double)t * 0.6 * (0.6 + (double)CineClient.hash((float)i));
            double r1 = r0 + 1.2 + (double)(CineClient.hash((float)i * 5.0F) * 2.0F);
            Vec3 x0 = at.add(d.scale(r0)).subtract(cam);
            Vec3 x1 = at.add(d.scale(r1)).subtract(cam);
            Gfx.ribbon(
               new float[]{(float)x0.x, (float)x0.y, (float)x0.z, (float)x1.x, (float)x1.y, (float)x1.z},
               new float[]{0.06F, 0.02F},
               new float[]{1.0F, 0.0F},
               c[0],
               c[1],
               c[2],
               a * Math.max(0.0F, 1.0F - t / 25.0F) * 1.2F
            );
         }

         draw(CineShaders.energy, 1, view, proj);
      }

      private void arcs(Vec3 cam, Matrix4f view, Matrix4f proj, Vec3 at, float rad, double t, int n) {
         Gfx.begin();
         int frame = (int)(t * 0.7);

         for (int i = 0; i < n; i++) {
            Random r = new Random((long)frame * 131L + (long)i * 7919L + this.p.seed);
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            Vec3 side = d.cross(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian())).normalize();
            int seg = 7;
            float[] pts = new float[seg * 3];
            float[] w = new float[seg];
            float[] u = new float[seg];

            for (int s = 0; s < seg; s++) {
               double k = (double)s / (double)(seg - 1);
               Vec3 q = at.add(d.scale((double)rad * (1.0 + k * 0.9)))
                  .add(side.scale(Math.sin(k * Math.PI) * (double)rad * 0.5))
                  .add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale((double)rad * 0.08 * k));
               Vec3 x = q.subtract(cam);
               pts[s * 3] = (float)x.x;
               pts[s * 3 + 1] = (float)x.y;
               pts[s * 3 + 2] = (float)x.z;
               w[s] = rad * 0.05F * (1.0F - (float)k * 0.7F) + 0.02F;
               u[s] = (float)k;
            }

            Gfx.ribbon(pts, w, u, 0.8F, 0.6F, 1.0F, 1.0F);
         }

         draw(CineShaders.energy, 3, view, proj);
      }

      private void ringsAround(Vec3 cam, Matrix4f view, Matrix4f proj, Vec3 at, float half, double t, int n) {
         for (int i = 0; i < n; i++) {
            float a = (float)(0.4 + 0.3 * Math.sin(t * 0.2 + (double)i));
            this.ring(cam, view, proj, at, half * (1.0F + 0.15F * (float)i), CineClient.PUR_B, a * 0.35F);
         }
      }

      private static void draw(ShaderInstance sh, int mode, Matrix4f view, Matrix4f proj) {
         Gfx.f(sh, "Mode", (float)mode);
         Gfx.flush(sh, view, proj, true);
      }

      void gui(GuiGraphics g, int w, int h, float partial, Minecraft mc) {
         double t = (double)((float)this.age + partial);

         for (CineClient.Title ti : this.titles) {
            if (!(t < (double)ti.start) && !(t > (double)ti.end)) {
               float in = CineClient.smooth((float)(t - (double)ti.start) / 5.0F);
               float out = CineClient.smooth((float)((double)ti.end - t) / 5.0F);
               float a = in * out;
               int slide = (int)((1.0F - in) * -60.0F + (1.0F - out) * 40.0F);
               float s = ti.small ? 2.2F : 3.6F;
               int cy = ti.small ? (int)((float)h * 0.2F) : (int)((float)h * 0.42F);
               int bandH = (int)(s * 14.0F + 18.0F);
               g.fill(0, cy - bandH / 2, w, cy + bandH / 2, CineClient.argb(0.62F * a, 0));
               g.fill(0, cy + bandH / 2 - 2, w, cy + bandH / 2, CineClient.argb(0.9F * a, ti.color));
               g.fill(0, cy - bandH / 2, w, cy - bandH / 2 + 1, CineClient.argb(0.6F * a, ti.color));
               g.pose().pushPose();
               g.pose().translate((float)w / 2.0F + (float)slide, (float)cy - s * 7.0F, 0.0F);
               g.pose().scale(s, s, 1.0F);
               int col = CineClient.argb(a, 16777215);
               g.drawCenteredString(mc.font, Component.literal(ti.jp), 0, 0, col);
               g.pose().popPose();
               g.pose().pushPose();
               g.pose().translate((float)w / 2.0F - (float)slide * 0.5F, (float)cy + s * 3.5F, 0.0F);
               g.pose().scale(1.3F, 1.3F, 1.0F);
               g.drawCenteredString(mc.font, Component.literal(ti.en), 0, 0, CineClient.argb(a, ti.color));
               g.pose().popPose();
            }
         }

         if (this.victim && this.kind == 4 && mc.player != null && mc.player.isAlive()) {
            double open = t - 72.0;
            if (open > 0.0 && open < 220.0) {
               float k = (float)Math.min(1.0, open / 20.0) * (float)Mth.clamp((220.0 - open) / 10.0, 0.0, 1.0);
               g.fill(0, 0, w, h, CineClient.argb(0.35F * k, 14544639));
               Random r = new Random((long)(t * 3.0));
               String glyphs = "01アイウエオカキクケコ∞※#%&@$§¥ΔΩΣ";

               for (int line = 0; line < h / 9; line++) {
                  StringBuilder sb = new StringBuilder();
                  int len = 20 + r.nextInt(60);

                  for (int i = 0; i < len; i++) {
                     sb.append(glyphs.charAt(r.nextInt(glyphs.length())));
                  }

                  int x = (int)((double)(r.nextFloat() * (float)w) * 1.4 - (double)w * 0.2 + t * (double)(5 + r.nextInt(20)) % (double)w) - w / 3;
                  g.drawString(
                     mc.font, sb.toString(), x, line * 9, CineClient.argb((0.25F + r.nextFloat() * 0.6F) * k, r.nextBoolean() ? 16777215 : 10475775), false
                  );
               }

               if (r.nextFloat() < 0.08F) {
                  g.fill(0, 0, w, h, CineClient.argb(0.6F * k, 16777215));
               }
            }
         }
      }
   }

   private static final class Title {
      final int start;
      final int end;
      final int color;
      final String jp;
      final String en;
      final boolean small;

      Title(int start, int end, String jp, String en, int color, boolean small) {
         this.start = start;
         this.end = end;
         this.jp = jp;
         this.en = en;
         this.color = color;
         this.small = small;
      }
   }
}
