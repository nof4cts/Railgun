package com.yuki.mod.client;

import com.avatar.mod.client.FireFx;
import com.choso.mod.client.CFx;
import com.choso.mod.client.Gfx;
import com.choso.mod.client.Impact;
import com.jjk.mod.client.JjkPrefs;
import com.yuki.mod.Net;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.CameraType;
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
import net.minecraft.world.level.levelgen.Heightmap.Types;
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
import net.minecraftforge.client.event.ViewportEvent.ComputeFogColor;
import net.minecraftforge.client.event.ViewportEvent.ComputeFov;
import net.minecraftforge.client.event.ViewportEvent.RenderFog;
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
public final class YFx {
   static final float[] PURPLE = new float[]{0.68F, 0.3F, 1.0F};
   static final float[] VIOLET = new float[]{0.45F, 0.12F, 0.85F};
   static final float[] PALE = new float[]{0.92F, 0.82F, 1.0F};
   static final float[] BONE = new float[]{0.93F, 0.92F, 0.88F};
   static final float[] BLADE = new float[]{0.6F, 0.64F, 1.0F};
   static final float[] EYE = new float[]{1.0F, 0.15F, 0.12F};
   static final float[] SIMPLE_C = new float[]{0.75F, 0.95F, 1.0F};
   static final float[] HEAL = new float[]{1.0F, 0.95F, 0.8F};
   private static final List<YFx.Eff> EFFECTS = new ArrayList<>();
   static final Map<Integer, Long> MASS = new HashMap<>();
   static final Map<Integer, Long> SIMPLE = new HashMap<>();
   private static YFx.Cut cut;
   private static YFx.CamEntity cam;
   private static CameraType prevType;
   private static float shake;
   private static Matrix4f lastView = new Matrix4f();
   private static Matrix4f lastProj = new Matrix4f();

   private YFx() {
   }

   public static boolean cutsceneActive() {
      return cut != null;
   }

   static long gameTime() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0L : mc.level.getGameTime();
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

   static Vec3 ground(Vec3 p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return p;
      } else {
         int y = mc.level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
         return new Vec3(p.x, Math.min((double)y, p.y + 0.5) + 0.04, p.z);
      }
   }

   static Vec3 hand(Entity e, float partial) {
      Vec3 look = e.getViewVector(partial);
      Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
      side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
      return e.getEyePosition(partial).add(look.scale(0.6)).add(side.scale(0.4)).add(0.0, -0.45, 0.0);
   }

   static boolean firstPersonOf(Entity e) {
      Minecraft mc = Minecraft.getInstance();
      return e != null && e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
   }

   public static void receive(Net.Fx f) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         int me = mc.player.getId();
         long now = gameTime();
         switch (f.kind) {
            case 0:
            case 1:
            case 2:
            case 5:
            case 10:
            case 16:
            case 17:
               EFFECTS.add(new YFx.Eff(f, Math.max(10, f.dur)));
               if ((f.caster == me || f.other == me) && f.kind != 10) {
                  shake = Math.max(shake, f.kind == 1 ? 0.5F + f.f * 0.3F : 0.4F);
               }
               break;
            case 3:
               EFFECTS.add(new YFx.Eff(f, f.dur + 2));
               break;
            case 4:
               EFFECTS.removeIf(x -> x.f.kind == 3 && x.f.other == f.other && x.f.caster == f.caster);
               EFFECTS.add(new YFx.Eff(f, 26));
               double d = mc.player.position().distanceTo(f.a);
               if (d < (double)(f.f + 10.0F)) {
                  float[] sc = screen(f.a);
                  shake = Math.max(shake, (float)(3.0 * (1.0 - d / (double)(f.f + 10.0F)) + 0.6));
               }
               break;
            case 6:
               SIMPLE.put(f.caster, now + (long)f.dur);
               EFFECTS.add(new YFx.Eff(f, 14));
               break;
            case 7:
            case 11:
               startCut(new YFx.Cut(f));
               break;
            case 8:
               MASS.put(f.caster, now + (long)f.dur);
               EFFECTS.add(new YFx.Eff(f, 22));
               if (f.caster == me) {
                  shake = Math.max(shake, 1.6F);
               }
               break;
            case 9:
               MASS.remove(f.caster);
               if (f.f > 0.0F) {
                  SIMPLE.remove(f.caster);
               }
               break;
            case 12:
            case 14:
               EFFECTS.add(new YFx.Eff(f, 16));
            case 13:
            default:
               break;
            case 15:
               if (f.f == 6.0F) {
                  FireFx.POSES.put(f.caster, new long[]{3L, now, (long)f.dur});
               } else {
                  CFx.POSES.put(f.caster, new long[]{(long)f.f, now, (long)f.dur});
               }
         }
      }
   }

   private static void later(Net.Fx f, int ticks, Runnable r) {
      EFFECTS.add(new YFx.Eff(f, ticks, r));
   }

   private static void startCut(YFx.Cut c) {
      Minecraft mc = Minecraft.getInstance();
      EFFECTS.add(c);
      if (cut == null && !JjkPrefs.skipCutscenes() && mc.player.position().distanceTo(c.f.a) < 60.0) {
         cut = c;
         prevType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         cam = new YFx.CamEntity(mc.level);
      }
   }

   private static void endCut() {
      Minecraft mc = Minecraft.getInstance();
      cut = null;
      cam = null;
      if (mc.player != null) {
         mc.setCameraEntity(mc.player);
      }

      if (prevType != null) {
         mc.options.setCameraType(prevType);
      }
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            EFFECTS.clear();
            MASS.clear();
            SIMPLE.clear();
            if (cut != null) {
               endCut();
            }
         } else if (!mc.isPaused()) {
            for (YFx.Eff f : new ArrayList<>(EFFECTS)) {
               f.age++;
               if (f instanceof YFx.Cut c) {
                  c.onAge();
               }

               if (f.callback != null && f.age == f.life) {
                  f.callback.run();
               }

               if (f.age >= f.life) {
                  EFFECTS.remove(f);
               }
            }

            long now = gameTime();
            shake = Math.max(0.0F, shake * 0.86F - 0.01F);
            YFx.Cut hole = nearestHole();
            if (hole != null) {
               float k = holeCloseness(hole, mc.player.position());
               shake = Math.max(shake, 0.15F + 1.6F * k * k);
            }

            if (cut != null) {
               boolean skip = mc.options.keyShift.isDown() && cut.age > 10 && mc.screen == null;
               if (skip || cut.age >= cut.cutLength() || !EFFECTS.contains(cut)) {
                  endCut();
               }
            }
         }
      }
   }

   static YFx.Cut nearestHole() {
      for (YFx.Eff f : EFFECTS) {
         if (f instanceof YFx.Cut c && c.f.kind == 11 && c.age >= 96) {
            return c;
         }
      }

      return null;
   }

   static float holeCloseness(YFx.Cut c, Vec3 p) {
      return (float)Mth.clamp(1.0 - p.distanceTo(c.f.a) / 48.0, 0.0, 1.0);
   }

   @SubscribeEvent
   public static void onRenderTick(RenderTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.START && cut != null && cam != null && mc.level != null) {
         double[] s = cut.shot(e.renderTickTime);
         Vec3 eye = new Vec3(s[0], s[1], s[2]);
         Vec3 subject = cut.f.a.add(0.0, cut.f.kind == 11 ? -1.0 : 1.4, 0.0);
         BlockHitResult hit = mc.level.clip(new ClipContext(subject, eye, Block.VISUAL, Fluid.NONE, mc.player));
         if (hit.getType() != Type.MISS) {
            eye = hit.getLocation().add(subject.subtract(eye).normalize().scale(0.35));
         }

         Vec3 d = new Vec3(s[3], s[4], s[5]).subtract(eye);
         cut.fov = (float)s[6];
         cut.roll = (float)s[7];
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
      if (YShaders.ready()) {
         Minecraft mc = Minecraft.getInstance();
         Matrix4f view = e.getPoseStack().last().pose();
         Matrix4f proj = e.getProjectionMatrix();
         float partial = e.getPartialTick();
         Vec3 camPos = e.getCamera().getPosition();
         float time = (float)(mc.level.getGameTime() % 100000L) + partial;
         if (e.getStage() == Stage.AFTER_SKY) {
            for (YFx.Eff f : EFFECTS) {
               if (f instanceof YFx.Cut) {
                  YFx.Cut c = (YFx.Cut)f;
                  if (c.f.kind == 7 && c.worldAlpha(partial) > 0.01F && camPos.distanceTo(c.f.a) < (double)(c.f.f + 4.0F)) {
                     Gfx.f(YShaders.star, "Time", time);
                     Gfx.f(YShaders.star, "Mode", 7.0F);
                     Gfx.f(YShaders.star, "Alpha", c.worldAlpha(partial));
                     Gfx.f(YShaders.star, "Seed", 3.0F);
                     Matrix4f mv = new Matrix4f(view).scale(60.0F);
                     Gfx.meshBlend(YShaders.sphere(), YShaders.star, mv, proj);
                  }
               }
            }
         } else if (e.getStage() == Stage.AFTER_PARTICLES) {
            lastView = new Matrix4f(view);
            lastProj = new Matrix4f(proj);
            Gfx.setCamera(view, mc.gameRenderer.getDepthFar() * 0.9F);
            Gfx.f(YShaders.star, "Time", time);
            YFx.Draw d = new YFx.Draw(camPos, view, proj, time);

            for (YFx.Eff fx : EFFECTS) {
               fx.render(d, partial);
            }

            long now = mc.level.getGameTime();

            for (Entity en : mc.level.entitiesForRendering()) {
               Vec3 pos = en.getPosition(partial);
               boolean own = firstPersonOf(en);
               Long m = MASS.get(en.getId());
               if (m != null && now < m) {
                  float pulse = 0.5F + 0.5F * Mth.sin(time * 0.35F + (float)en.getId());
                  if (!own) {
                     d.halo(pos.add(0.0, 1.0, 0.0), 1.6F, 0.35F);
                     d.glow(pos.add(0.0, 1.0, 0.0), 1.5F + 0.2F * pulse, PURPLE, 0.35F);

                     for (int i = 0; i < 3; i++) {
                        float a = time * 0.08F + (float)i * (float) (Math.PI * 2) / 3.0F;
                        Vec3 p = pos.add((double)Mth.cos(a) * 0.8, 0.4 + (double)i * 0.55, (double)Mth.sin(a) * 0.8);
                        d.glow(p, 0.16F, PALE, 0.9F);
                     }

                     Random r = new Random((long)(time / 3.0F) * 17L + (long)en.getId());
                     if (r.nextFloat() < 0.6F) {
                        Vec3 a = pos.add(r.nextGaussian() * 1.4, (double)r.nextFloat() * 2.2, r.nextGaussian() * 1.4);
                        d.arc(a, pos.add(0.0, 1.0, 0.0), 0.03F, PURPLE, 0.8F, r.nextInt());
                     }
                  }

                  d.groundRing(ground(pos), 1.2F + 0.25F * pulse, VIOLET, 0.55F, (float)en.getId());
               }

               Long s = SIMPLE.get(en.getId());
               if (s != null && now < s) {
                  float al = Mth.clamp((float)(s - now) / 20.0F, 0.0F, 1.0F);
                  Vec3 g = ground(pos);
                  d.groundRing(g, 3.0F, SIMPLE_C, 0.7F * al, 11.0F);
                  d.groundRing(g, 2.85F, SIMPLE_C, 0.35F * al, 12.0F);
                  d.begin();

                  for (int i = 0; i < 24; i++) {
                     float a = (float)i * (float) (Math.PI * 2) / 24.0F + time * 0.01F;
                     Vec3 p0 = g.add((double)Mth.cos(a) * 2.75, 0.03, (double)Mth.sin(a) * 2.75);
                     Vec3 p1 = g.add((double)Mth.cos(a) * 3.1, 0.03, (double)Mth.sin(a) * 3.1);
                     d.seg(p0, p1, 0.04F, 0.04F, SIMPLE_C, 0.8F * al);
                  }

                  d.end(1, 2.0F);
               }
            }
         }
      }
   }

   static void garuda(YFx.Draw d, List<Vec3> spine, float a, int seed) {
      GarudaRenderer.drawWorld(spine, a, 0.15F + 0.1F * Mth.sin(d.time * 0.2F), d.cam, d.view);
   }

   @SubscribeEvent
   public static void onAngles(ComputeCameraAngles e) {
      float r = cut != null ? cut.roll : 0.0F;
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
      if (cut != null) {
         e.setFOV((double)cut.fov);
      } else {
         Minecraft mc = Minecraft.getInstance();
         YFx.Cut hole = nearestHole();
         if (hole != null && mc.player != null) {
            float k = holeCloseness(hole, mc.player.position());
            e.setFOV(e.getFOV() * (double)(1.0F + 0.25F * k * k));
         }
      }
   }

   @SubscribeEvent
   public static void onFogColor(ComputeFogColor e) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 cp = e.getCamera().getPosition();
      float m = 0.0F;

      for (YFx.Eff f : EFFECTS) {
         if (f instanceof YFx.Cut) {
            YFx.Cut c = (YFx.Cut)f;
            if (c.f.kind == 7 && cp.distanceTo(c.f.a) < (double)(c.f.f + 4.0F)) {
               m = Math.max(m, c.worldAlpha((float)e.getPartialTick()));
            }
         }
      }

      YFx.Cut hole = nearestHole();
      if (hole != null) {
         m = Math.max(m, holeCloseness(hole, cp) * 0.8F);
      }

      if (m > 0.0F) {
         e.setRed(Mth.lerp(m, e.getRed(), 0.07F));
         e.setGreen(Mth.lerp(m, e.getGreen(), 0.0F));
         e.setBlue(Mth.lerp(m, e.getBlue(), 0.14F));
      }
   }

   @SubscribeEvent
   public static void onFog(RenderFog e) {
      Vec3 cp = e.getCamera().getPosition();

      for (YFx.Eff f : EFFECTS) {
         if (f instanceof YFx.Cut) {
            YFx.Cut c = (YFx.Cut)f;
            if (c.f.kind == 7 && cp.distanceTo(c.f.a) < (double)(c.f.f + 4.0F)) {
               float k = c.worldAlpha((float)e.getPartialTick());
               if (k > 0.01F) {
                  e.setNearPlaneDistance(Mth.lerp(k, e.getNearPlaneDistance(), 4.0F));
                  e.setFarPlaneDistance(Mth.lerp(k, e.getFarPlaneDistance(), c.f.f + 8.0F));
                  e.setCanceled(true);
               }
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onOverlay(Pre e) {
      if (cut != null) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onHand(RenderHandEvent e) {
      if (cut != null) {
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
      YFx.Cut hole = nearestHole();
      if (hole != null && mc.player != null) {
         float k = holeCloseness(hole, mc.player.position());
         int band = (int)((float)Math.min(w, h) * (0.12F + 0.35F * k));
         int col = argb(0.25F + 0.65F * k, 655380);
         g.fillGradient(0, 0, w, band, col, 0);
         g.fillGradient(0, h - band, w, h, 0, col);

         for (int i = 0; i < band; i += 2) {
            float a = (1.0F - (float)i / (float)band) * (0.25F + 0.65F * k);
            g.fill(i, 0, i + 2, h, argb(a, 655380));
            g.fill(w - i - 2, 0, w - i, h, argb(a, 655380));
         }

         if (k > 0.75F && mc.level.getGameTime() % 9L < 2L) {
            g.fill(0, 0, w, h, argb((k - 0.75F) * 1.2F, 2752592));
         }
      }

      if (cut != null) {
         float t = (float)cut.age + partial;
         float k = smooth(t / 10.0F) * smooth(((float)cut.cutLength() - t) / 10.0F);
         int bar = (int)((float)h * 0.12F * k);
         g.fill(0, 0, w, bar, -16777216);
         g.fill(0, h - bar, w, h, -16777216);
         float black = Math.max(1.0F - t / 8.0F, Mth.clamp((t - (float)(cut.cutLength() - 8)) / 8.0F, 0.0F, 1.0F));
         if (black > 0.01F) {
            g.fill(0, 0, w, h, argb(black, 0));
         }

         cut.titles(g, w, h, t, mc);
      }
   }

   static int argb(float a, int rgb) {
      return Mth.clamp((int)(a * 255.0F), 0, 255) << 24 | rgb & 16777215;
   }

   static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
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

   static final class Cut extends YFx.Eff {
      float fov = 60.0F;
      float roll;
      final Vec3 fwd;
      final Vec3 right;

      Cut(Net.Fx f) {
         super(f, f.kind == 7 ? f.dur : 96 + f.dur);
         Minecraft mc = Minecraft.getInstance();
         Entity c = mc.level.getEntity(f.caster);
         Vec3 l = c != null ? c.getLookAngle() : new Vec3(0.0, 0.0, 1.0);
         Vec3 flat = new Vec3(l.x, 0.0, l.z);
         this.fwd = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
         this.right = this.fwd.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      }

      int cutLength() {
         return this.f.kind == 7 ? 74 : 140;
      }

      float worldAlpha(float partial) {
         float t = (float)this.age + partial;
         return Mth.clamp((t - 32.0F) / 28.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.life - t) / 20.0F, 0.0F, 1.0F);
      }

      double[] shot(float partial) {
         double t = (double)((float)this.age + partial);
         Vec3 o = this.f.kind == 11 ? this.f.a.add(0.0, -2.5, 0.0) : this.f.a;
         Vec3 head = o.add(0.0, 1.6, 0.0);
         double fv = 55.0;
         double rl = 0.0;
         Vec3 eye;
         Vec3 look;
         if (this.f.kind == 7) {
            if (t < 24.0) {
               double k = t / 24.0;
               eye = head.add(this.fwd.scale(1.4 - 0.3 * k)).add(this.right.scale(0.2));
               look = head;
               fv = 40.0;
            } else {
               double a = (t - 24.0) * 0.05;
               Vec3 rad = this.fwd.scale(Math.cos(a)).add(this.right.scale(Math.sin(a)));
               eye = o.add(rad.scale(7.0)).add(0.0, 0.6, 0.0);
               look = head.add(0.0, 2.5, 0.0);
               fv = 72.0;
               rl = 4.0 * Math.sin(a);
            }
         } else {
            int cutAt = 96;
            if (t < 30.0) {
               double k = t / 30.0;
               eye = head.add(this.fwd.scale(1.5 - 0.4 * k)).add(this.right.scale(0.15)).add(0.0, -0.05, 0.0);
               look = head;
               fv = 38.0;
               rl = 2.0 * k;
            } else if (t < 62.0) {
               double a = (t - 30.0) * 0.07;
               Vec3 rad = this.fwd.scale(Math.cos(a)).add(this.right.scale(Math.sin(a)));
               eye = o.add(rad.scale(4.2)).add(0.0, 0.5, 0.0);
               look = head.add(0.0, -0.2, 0.0);
               fv = 58.0;
               rl = 6.0 * Math.sin(a);
            } else if (t < (double)cutAt) {
               double k = (t - 62.0) / (double)(cutAt - 62);
               eye = head.add(this.fwd.scale(0.95 - 0.35 * k)).add(0.0, 0.02, 0.0);
               look = head;
               fv = 34.0 - 10.0 * k;
               rl = -8.0 * k;
            } else {
               double k = (double)YFx.smooth((float)((t - (double)cutAt) / 40.0));
               Vec3 c = this.f.a;
               eye = c.add(this.fwd.scale(7.0 + 15.0 * k)).add(this.right.scale(4.0 + 6.0 * k)).add(0.0, -1.5 + 3.0 * k, 0.0);
               look = c;
               fv = 60.0 + 12.0 * k;
               rl = 5.0 * (1.0 - k);
            }
         }

         return new double[]{eye.x, eye.y, eye.z, look.x, look.y, look.z, fv, rl};
      }

      void onAge() {
         Minecraft mc = Minecraft.getInstance();
         boolean me = YFx.cut == this || mc.player != null && mc.player.position().distanceTo(this.f.a) < 50.0;
         if (me) {
            if (this.f.kind == 7) {
               if (this.age == 54) {
                  Impact.hit(5, 2, 0, 0.5F, 0.5F);
               }

               if (this.age == 56) {
                  Impact.hit(6, 2, 0, 0.5F, 0.5F);
               }

               if (this.age == 58) {
                  Impact.hit(1, 2, 10, 0.5F, 0.5F);
                  YFx.shake = Math.max(YFx.shake, 2.0F);
               }
            } else {
               int c = 96;
               if (this.age == 60) {
                  Impact.hit(5, 2, 0, 0.5F, 0.55F);
               }

               if (this.age == c - 12) {
                  Impact.hit(5, 3, 0, 0.5F, 0.5F);
               }

               if (this.age == c - 9) {
                  Impact.hit(0, 4, 0, 0.5F, 0.5F);
               }

               if (this.age == c - 5) {
                  Impact.hit(6, 3, 0, 0.5F, 0.5F);
               }

               if (this.age == c - 2) {
                  Impact.hit(4, 2, 0, 0.5F, 0.5F);
               }

               if (this.age == c) {
                  Impact.hit(1, 3, 16, 0.5F, 0.5F);
                  YFx.shake = Math.max(YFx.shake, 3.5F);
               }
            }
         }
      }

      @Override
      void render(YFx.Draw d, float partial) {
         float t = (float)this.age + partial;
         Minecraft mc = Minecraft.getInstance();
         if (this.f.kind == 7) {
            float wa = this.worldAlpha(partial);
            if (wa > 0.01F) {
               for (int i = 0; i < 4; i++) {
                  float k = 1.0F - (t * 0.012F + (float)i * 0.25F) % 1.0F;
                  d.groundRing(YFx.ground(this.f.a), this.f.f * k, YFx.PURPLE, wa * 0.7F * (1.0F - k * 0.5F), (float)i);
               }

               Random r = new Random(7L);
               d.begin();

               for (int i = 0; i < 160; i++) {
                  float a = r.nextFloat() * (float) (Math.PI * 2);
                  float dist = (float)Math.sqrt((double)r.nextFloat()) * this.f.f;
                  float ph = (t * 0.05F + r.nextFloat()) % 1.0F;
                  Vec3 p = this.f.a.add((double)(Mth.cos(a) * dist), (double)(14.0F - ph * 16.0F), (double)(Mth.sin(a) * dist));
                  d.seg(p.add(0.0, 2.2, 0.0), p, 0.03F, 0.05F, i % 3 == 0 ? YFx.PALE : YFx.PURPLE, wa * 0.8F);
               }

               d.end(1, 6.0F);
            }
         } else {
            int cutAt = 96;
            Vec3 c = this.f.a;
            Entity caster = mc.level.getEntity(this.f.caster);
            if (t < (float)(cutAt + 2)) {
               float k = Mth.clamp(t / (float)cutAt, 0.0F, 1.0F);
               Vec3 body = caster != null && caster.isAlive() ? caster.getPosition(partial).add(0.0, 1.0, 0.0) : c.add(0.0, -1.5, 0.0);
               d.halo(body, 1.0F + k * 4.0F, 0.3F + 0.5F * k);
               d.glow(body, 1.0F + k * 3.0F, YFx.PURPLE, 0.3F + 0.5F * k);

               for (int i = 0; i < 3; i++) {
                  float rk = 1.0F - (t * 0.03F + (float)i / 3.0F) % 1.0F;
                  d.groundRing(YFx.ground(body), 9.0F * rk, YFx.PURPLE, 0.8F * (1.0F - rk), (float)(i + 20));
               }

               Random r = new Random((long)(t / 2.0F) * 13L);

               for (int i = 0; i < 3 + (int)(k * 9.0F); i++) {
                  Vec3 a = body.add(r.nextGaussian() * 5.0, r.nextGaussian() * 2.5, r.nextGaussian() * 5.0);
                  d.arc(a, body, 0.04F, i % 2 == 0 ? YFx.PURPLE : YFx.PALE, 0.8F, r.nextInt());
               }

               for (int i = 0; i < 20; i++) {
                  Random rr = new Random((long)i * 31L);
                  float ph = (t * 0.02F + rr.nextFloat()) % 1.0F;
                  Vec3 from = YFx.ground(body.add(rr.nextGaussian() * 7.0, 0.0, rr.nextGaussian() * 7.0));
                  d.glow(from.lerp(body, (double)(ph * ph)), 0.1F, YFx.VIOLET, 0.8F * k);
               }

               if (t > (float)(cutAt - 10)) {
                  float b = (t - (float)cutAt + 10.0F) / 12.0F;
                  d.glow(body, 2.0F + b * 14.0F, YFx.PALE, Mth.clamp(1.0F - b, 0.0F, 1.0F));
               }

               if (t < (float)cutAt) {
                  return;
               }
            }

            float ht = t - (float)cutAt;
            float holeLife = (float)(this.life - cutAt);
            float core = Math.min(this.f.f, 1.2F + ht / 30.0F);
            float endK = Mth.clamp((holeLife - ht) / 20.0F, 0.0F, 1.0F);
            core *= 0.4F + 0.6F * endK;
            float spin = d.time * 0.004F;
            Vec3 n = new Vec3(0.22, 1.0, 0.12);
            d.halo(c, core * 5.5F, 0.85F * endK);
            d.glow(c, core * 7.0F, YFx.VIOLET, 0.35F * endK);
            d.disk(c, n, core * 1.35F, core * 4.6F, endK, true, spin);
            d.sphere(c, core, 8, endK);
            d.ring(c, core * 1.22F, YFx.PALE, 0.9F * endK);
            d.ring(c, core * 1.5F, YFx.PURPLE, 0.6F * endK);
            d.disk(c, n, core * 1.35F, core * 4.6F, endK, false, spin);
            Random r = new Random(99L);

            for (int i = 0; i < 26; i++) {
               float a0 = r.nextFloat() * (float) (Math.PI * 2);
               float ph = (d.time * 0.012F * (0.6F + r.nextFloat()) + r.nextFloat()) % 1.0F;
               float rad = core * (1.3F + 9.0F * (1.0F - ph));
               float ang = a0 + ph * 9.0F;
               float yy = (r.nextFloat() - 0.5F) * 3.0F * (1.0F - ph);
               Vec3 p = c.add((double)(Mth.cos(ang) * rad), (double)yy, (double)(Mth.sin(ang) * rad));
               float ang2 = ang - 0.25F;
               float rad2 = rad + core * 0.7F;
               Vec3 q = c.add((double)(Mth.cos(ang2) * rad2), (double)(yy * 1.05F), (double)(Mth.sin(ang2) * rad2));
               d.streak(q, p, 0.05F + 0.08F * ph, i % 3 == 0 ? YFx.PALE : YFx.PURPLE, 0.85F * endK);
            }

            for (int i = 0; i < 18; i++) {
               Random rr = new Random((long)i * 53L + 7L);
               float ph = (d.time * 0.008F + rr.nextFloat()) % 1.0F;
               float a = rr.nextFloat() * (float) (Math.PI * 2) + ph * 6.0F;
               float rad = core * (1.2F + 12.0F * (1.0F - ph));
               Vec3 p = c.add((double)(Mth.cos(a) * rad), (double)(-3.0F * (1.0F - ph)) + rr.nextGaussian(), (double)(Mth.sin(a) * rad));
               d.begin();
               d.sprite(p, 0.14F + rr.nextFloat() * 0.18F, d.time * 0.1F + (float)i, new float[]{0.12F, 0.1F, 0.14F}, endK);
               d.end(5, (float)i);
            }

            if (ht > holeLife - 20.0F) {
               float b = (ht - holeLife + 20.0F) / 20.0F;
               d.ring(c, core + b * 30.0F, YFx.PALE, 1.0F - b);
               d.glow(c, 2.0F + b * 20.0F, YFx.PURPLE, (1.0F - b) * 0.8F);
            }
         }
      }

      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         String jp = null;
         String en = null;
         int col = 12946687;
         float s0 = 0.0F;
         float s1 = 0.0F;
         if (this.f.kind == 7) {
            boolean second = t > 28.0F;
            if (t > 6.0F && t < 70.0F) {
               jp = second ? "星の怒り" : "領域展開";
               en = second ? "STAR RAGE: CRUSHING MASS" : "DOMAIN EXPANSION";
               s0 = second ? 28.0F : 6.0F;
               s1 = second ? 70.0F : 28.0F;
            }
         } else if (t > 32.0F && t < 76.0F) {
            jp = "星の怒り";
            en = "STAR RAGE: LIMIT BREAK";
            s0 = 32.0F;
            s1 = 76.0F;
         } else if (t > 102.0F && t < 136.0F) {
            jp = "ブラックホール";
            en = "BLACK HOLE";
            col = 15127295;
            s0 = 102.0F;
            s1 = 136.0F;
         }

         if (jp != null) {
            float in = YFx.smooth((t - s0) / 5.0F);
            float out = YFx.smooth((s1 - t) / 5.0F);
            float a = in * out;
            int slide = (int)((1.0F - in) * -60.0F + (1.0F - out) * 40.0F);
            float s = 3.6F;
            int cy = (int)((float)h * 0.42F);
            int bandH = (int)(s * 14.0F + 18.0F);
            g.fill(0, cy - bandH / 2, w, cy + bandH / 2, YFx.argb(0.62F * a, 393228));
            g.fill(0, cy + bandH / 2 - 2, w, cy + bandH / 2, YFx.argb(0.9F * a, col));
            g.fill(0, cy - bandH / 2, w, cy - bandH / 2 + 1, YFx.argb(0.6F * a, col));
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F + (float)slide, (float)cy - s * 7.0F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.drawCenteredString(mc.font, Component.literal(jp), 0, 0, YFx.argb(a, 16777215));
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F - (float)slide * 0.5F, (float)cy + s * 3.5F, 0.0F);
            g.pose().scale(1.3F, 1.3F, 1.0F);
            g.drawCenteredString(mc.font, Component.literal(en), 0, 0, YFx.argb(a, col));
            g.pose().popPose();
         }
      }
   }

   static final class Draw {
      final Vec3 cam;
      final Matrix4f view;
      final Matrix4f proj;
      final float time;

      Draw(Vec3 cam, Matrix4f view, Matrix4f proj, float time) {
         this.cam = cam;
         this.view = view;
         this.proj = proj;
         this.time = time;
      }

      void begin() {
         Gfx.begin();
      }

      void end(int mode, float seed) {
         ShaderInstance sh = YShaders.star;
         Gfx.f(sh, "Mode", (float)mode);
         Gfx.f(sh, "Seed", seed);
         Gfx.f(sh, "Alpha", 1.0F);
         Gfx.flush(sh, this.view, this.proj, true);
      }

      void sprite(Vec3 at, float half, float rot, float[] c, float a) {
         Gfx.billboard(at.x - this.cam.x, at.y - this.cam.y, at.z - this.cam.z, half, rot, c[0], c[1], c[2], a);
      }

      void glow(Vec3 at, float half, float[] c, float a) {
         this.begin();
         this.sprite(at, half, 0.0F, c, a);
         this.end(0, 0.0F);
      }

      void ring(Vec3 at, float half, float[] c, float a) {
         this.begin();
         this.sprite(at, half, 0.0F, c, a);
         this.end(2, 0.0F);
      }

      void halo(Vec3 at, float half, float a) {
         this.begin();
         this.sprite(at, half, 0.0F, YFx.PALE, a);
         this.end(3, 0.0F);
      }

      void seg(Vec3 a, Vec3 b, float wa, float wb, float[] c, float al) {
         Vec3 x0 = a.subtract(this.cam);
         Vec3 x1 = b.subtract(this.cam);
         Gfx.ribbon(
            new float[]{(float)x0.x, (float)x0.y, (float)x0.z, (float)x1.x, (float)x1.y, (float)x1.z},
            new float[]{wa, wb},
            new float[]{0.0F, 1.0F},
            c[0],
            c[1],
            c[2],
            al
         );
      }

      void streak(Vec3 a, Vec3 b, float w, float[] c, float al) {
         this.begin();
         this.seg(a, b, w, w, c, al);
         this.end(1, 0.0F);
      }

      void quad(Vec3 c, Vec3 ax, Vec3 ay, float[] col, float al) {
         Vec3 o = c.subtract(this.cam);
         float[][] cs = new float[][]{{-1.0F, -1.0F, 0.0F, 0.0F}, {1.0F, -1.0F, 1.0F, 0.0F}, {1.0F, 1.0F, 1.0F, 1.0F}, {-1.0F, 1.0F, 0.0F, 1.0F}};

         for (float[] k : cs) {
            Vec3 p = o.add(ax.scale((double)k[0])).add(ay.scale((double)k[1]));
            Gfx.v((float)p.x, (float)p.y, (float)p.z, k[2], k[3], col[0], col[1], col[2], al);
         }
      }

      void groundRing(Vec3 at, float half, float[] c, float a, float seed) {
         this.begin();
         this.quad(at, new Vec3((double)half, 0.0, 0.0), new Vec3(0.0, 0.0, (double)half), c, a);
         this.end(10, seed);
      }

      void wallRing(Vec3 at, Vec3 facing, float half, float[] c, float a, float seed) {
         Vec3 f = facing.normalize();
         Vec3 s = f.cross(new Vec3(0.0, 1.0, 0.0));
         s = s.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : s.normalize();
         Vec3 u = s.cross(f).normalize();
         this.begin();
         this.quad(at, s.scale((double)half), u.scale((double)half), c, a);
         this.end(10, seed);
      }

      void arc(Vec3 a, Vec3 b, float width, float[] c, float al, int seed) {
         Random r = new Random((long)seed);
         Vec3 d = b.subtract(a);
         double len = d.length();
         if (!(len < 0.001)) {
            Vec3 dir = d.scale(1.0 / len);
            Vec3 s1 = dir.cross(Math.abs(dir.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0)).normalize();
            Vec3 s2 = dir.cross(s1);
            int n = Math.max(4, Math.min(24, (int)(len * 1.6)));
            float[] pts = new float[(n + 1) * 3];
            float[] w = new float[n + 1];
            float[] u = new float[n + 1];

            for (int i = 0; i <= n; i++) {
               double k = (double)i / (double)n;
               double amp = Math.sin(k * Math.PI) * len * 0.08;
               Vec3 p = a.add(d.scale(k)).add(s1.scale(r.nextGaussian() * amp)).add(s2.scale(r.nextGaussian() * amp)).subtract(this.cam);
               pts[i * 3] = (float)p.x;
               pts[i * 3 + 1] = (float)p.y;
               pts[i * 3 + 2] = (float)p.z;
               w[i] = width;
               u[i] = (float)k;
            }

            this.begin();
            Gfx.ribbon(pts, w, u, c[0], c[1], c[2], al);
            this.end(9, (float)seed);
         }
      }

      void sphere(Vec3 at, float radius, int mode, float alpha) {
         ShaderInstance sh = YShaders.star;
         Gfx.f(sh, "Mode", (float)mode);
         Gfx.f(sh, "Seed", 5.0F);
         Gfx.f(sh, "Alpha", alpha);
         Matrix4f mv = new Matrix4f(this.view).translate((float)(at.x - this.cam.x), (float)(at.y - this.cam.y), (float)(at.z - this.cam.z)).scale(radius);
         Gfx.meshBlend(YShaders.sphere(), sh, mv, this.proj);
      }

      void disk(Vec3 c, Vec3 normal, float rIn, float rOut, float a, boolean farHalf, float spin) {
         Vec3 n = normal.normalize();
         Vec3 e1 = n.cross(new Vec3(0.0, 0.0, 1.0));
         e1 = e1.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : e1.normalize();
         Vec3 e2 = n.cross(e1).normalize();
         Vec3 toCam = this.cam.subtract(c);
         this.begin();
         int seg = 56;

         for (int i = 0; i < seg; i++) {
            float a0 = (float)i * (float) (Math.PI * 2) / (float)seg;
            float a1 = (float)(i + 1) * (float) (Math.PI * 2) / (float)seg;
            Vec3 d0 = e1.scale((double)Mth.cos(a0)).add(e2.scale((double)Mth.sin(a0)));
            Vec3 d1 = e1.scale((double)Mth.cos(a1)).add(e2.scale((double)Mth.sin(a1)));
            Vec3 mid = d0.add(d1);
            boolean far = mid.dot(toCam) < 0.0;
            if (far == farHalf) {
               Vec3 p00 = c.add(d0.scale((double)rIn)).subtract(this.cam);
               Vec3 p01 = c.add(d1.scale((double)rIn)).subtract(this.cam);
               Vec3 p11 = c.add(d1.scale((double)rOut)).subtract(this.cam);
               Vec3 p10 = c.add(d0.scale((double)rOut)).subtract(this.cam);
               float u0 = (float)i / (float)seg + spin;
               float u1 = (float)(i + 1) / (float)seg + spin;
               Gfx.v((float)p00.x, (float)p00.y, (float)p00.z, u0, 0.0F, 1.0F, 1.0F, 1.0F, a);
               Gfx.v((float)p01.x, (float)p01.y, (float)p01.z, u1, 0.0F, 1.0F, 1.0F, 1.0F, a);
               Gfx.v((float)p11.x, (float)p11.y, (float)p11.z, u1, 1.0F, 1.0F, 1.0F, 1.0F, a);
               Gfx.v((float)p10.x, (float)p10.y, (float)p10.z, u0, 1.0F, 1.0F, 1.0F, 1.0F, a);
            }
         }

         this.end(4, 0.0F);
      }
   }

   static class Eff {
      final Net.Fx f;
      final int life;
      final Runnable callback;
      int age;

      Eff(Net.Fx f, int life) {
         this(f, life, null);
      }

      Eff(Net.Fx f, int life, Runnable callback) {
         this.f = f;
         this.life = life;
         this.callback = callback;
      }

      void render(YFx.Draw d, float partial) {
         if (this.callback == null) {
            float t = (float)this.age + partial;
            float fade = Mth.clamp(1.0F - t / (float)this.life, 0.0F, 1.0F);
            Minecraft mc = Minecraft.getInstance();
            Entity caster = mc.level == null ? null : mc.level.getEntity(this.f.caster);
            switch (this.f.kind) {
               case 0:
                  float sx = this.f.f;
                  d.glow(this.f.a, (0.6F + sx * 0.5F) * (1.0F + t * 0.15F), YFx.PURPLE, fade);
                  d.glow(this.f.a, 0.35F + sx * 0.2F, YFx.PALE, fade);
                  Random r = new Random((long)(this.f.a.x * 977.0 + this.f.a.z * 131.0));

                  for (int i = 0; i < 5; i++) {
                     Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize().add(this.f.b.scale(0.8)).normalize();
                     d.streak(
                        this.f.a.add(dir.scale(0.2 + (double)t * 0.12)), this.f.a.add(dir.scale(0.6 + (double)t * 0.3 * (double)sx)), 0.05F, YFx.PURPLE, fade
                     );
                  }
                  break;
               case 1: {
                  float s = this.f.f;
                  float k = Mth.clamp(t / 8.0F, 0.0F, 1.0F);
                  d.wallRing(this.f.a, this.f.b, 0.6F + k * 2.6F * s, YFx.PURPLE, (1.0F - k) * 0.95F, 3.0F);
                  d.wallRing(this.f.a.add(this.f.b.scale(0.8 * (double)k)), this.f.b, 0.4F + k * 1.6F * s, YFx.PALE, (1.0F - k) * 0.6F, 4.0F);
                  d.groundRing(YFx.ground(this.f.a), 0.8F + k * 3.2F * s, YFx.VIOLET, (1.0F - k) * 0.8F, 5.0F);
                  d.glow(this.f.a, 1.0F + 1.2F * s * (1.0F - k), YFx.PURPLE, 1.0F - k);
                  if (s > 1.2F) {
                     d.halo(this.f.a, 1.2F * s * (1.0F - k * 0.5F), (1.0F - k) * 0.45F);
                  }
                  break;
               }
               case 2:
                  if (caster == null) {
                     return;
                  }

                  Vec3 h0 = YFx.hand(caster, partial);
                  Vec3 fl = this.f.b;
                  float sweep = YFx.smooth(t / 7.0F);
                  float theta = Mth.lerp(sweep, 1.9F, -1.9F);
                  List<Vec3> pts = new ArrayList<>();

                  for (int i = 0; i <= 24; i++) {
                     float ang = theta + (float)i * 0.055F * (1.0F - sweep * 0.6F);
                     Vec3 dir = new Vec3(
                           fl.x * (double)Mth.cos(ang) - fl.z * (double)Mth.sin(ang), -0.05, fl.x * (double)Mth.sin(ang) + fl.z * (double)Mth.cos(ang)
                        )
                        .normalize();
                     pts.add(h0.add(dir.scale(0.5 + (double)i * 0.42)).add(0.0, (double)Mth.sin((float)i * 0.4F + t) * 0.12, 0.0));
                  }

                  Collections.reverse(pts);
                  float al = Mth.clamp(((float)this.life - t) / 5.0F, 0.0F, 1.0F);
                  YFx.garuda(d, pts, al, this.f.caster);
                  if (t < 7.0F) {
                     d.streak(pts.get(0), pts.get(3), 0.35F, YFx.PURPLE, 0.5F * (1.0F - t / 7.0F));
                  }
                  break;
               case 3:
                  Vec3 p = this.f.a.add(this.f.b.scale((double)t)).add(0.0, -0.015 * (double)t * (double)(t - 1.0F) / 2.0, 0.0);
                  float big = this.f.f > 1.0F ? 1.3F : 1.0F;
                  float form = this.f.b.lengthSqr() > 1.0E-4 ? 1.0F : Mth.clamp(t / 3.0F, 0.0F, 1.0F);
                  form = form * form * (3.0F - 2.0F * form);
                  float rad = Mth.lerp(form, 2.2F, 0.62F) * big;
                  d.halo(p, rad * 2.0F, 0.45F + 0.2F * form);
                  d.glow(p, rad * 2.6F, YFx.PURPLE, 0.5F);
                  float spin = d.time * 1.1F;
                  List<Vec3> coil = new ArrayList<>();

                  for (int i = 0; i < 44; i++) {
                     float kx = (float)i / 43.0F;
                     float phi = Mth.lerp(kx, 0.15F, 2.9915926F);
                     float th = (float)i * 1.35F * form + spin;
                     float rr = rad * (0.9F + 0.1F * Mth.sin((float)i * 0.7F));
                     coil.add(p.add((double)(Mth.sin(phi) * Mth.cos(th) * rr), (double)(Mth.cos(phi) * rr), (double)(Mth.sin(phi) * Mth.sin(th) * rr)));
                  }

                  GarudaRenderer.finScale = Mth.lerp(form, 1.0F, 0.3F);
                  YFx.garuda(d, coil, 1.0F, this.f.caster);
                  GarudaRenderer.finScale = 1.0F;
                  d.glow(p, rad * 0.9F, YFx.PALE, 0.5F);
                  d.streak(p.subtract(this.f.b.normalize().scale(4.0)), p, rad * 0.8F, YFx.PURPLE, 0.5F);
                  break;
               case 4: {
                  float k = Mth.clamp(t / 14.0F, 0.0F, 1.0F);
                  float r = this.f.f;
                  if (t < 4.0F) {
                     d.halo(this.f.a, r * (1.4F - t * 0.25F), 0.9F);
                     d.glow(this.f.a, r * 0.8F, YFx.PALE, 1.0F);
                  }

                  d.glow(this.f.a, r * (0.6F + k * 2.4F), YFx.PURPLE, 1.0F - k);
                  d.groundRing(YFx.ground(this.f.a), r * (0.4F + k * 2.2F), YFx.PURPLE, 1.0F - k, 7.0F);
                  d.groundRing(YFx.ground(this.f.a), r * (0.2F + k * 1.4F), YFx.PALE, (1.0F - k) * 0.7F, 8.0F);
                  d.ring(this.f.a, r * (0.3F + k * 2.4F), YFx.PURPLE, (1.0F - k) * 0.9F);
                  Random rr = new Random((long)this.f.other * 31L);

                  for (int i = 0; i < 14; i++) {
                     Vec3 dir = new Vec3(rr.nextGaussian(), Math.abs(rr.nextGaussian()) * 0.8 + 0.2, rr.nextGaussian()).normalize();
                     d.streak(
                        this.f.a.add(dir.scale((double)(r * k) * 0.6)),
                        this.f.a.add(dir.scale((double)r * (0.4 + (double)k * 1.6))),
                        0.12F,
                        i % 2 == 0 ? YFx.PURPLE : YFx.PALE,
                        1.0F - k
                     );
                  }

                  for (int i = 0; i < 4; i++) {
                     Vec3 a = this.f.a.add(rr.nextGaussian() * (double)r, (double)(rr.nextFloat() * r), rr.nextGaussian() * (double)r);
                     d.arc(a, this.f.a, 0.05F, YFx.PURPLE, 1.0F - k, rr.nextInt() + (int)(t / 2.0F));
                  }
                  break;
               }
               case 5:
                  Entity v = mc.level == null ? null : mc.level.getEntity(this.f.other);
                  if (v == null) {
                     return;
                  }

                  Vec3 vp = v.getPosition(partial);
                  float hgt = v.getBbHeight();
                  float wid = v.getBbWidth() * 0.6F + 0.3F;
                  float wrap = Mth.clamp((t - 4.0F) / 8.0F, 0.0F, 1.0F);
                  float al = Mth.clamp(((float)this.life - t) / 8.0F, 0.0F, 1.0F);
                  List<Vec3> pts = new ArrayList<>();
                  if (t < 4.0F && caster != null) {
                     Vec3 from = YFx.hand(caster, partial);
                     Vec3 to = vp.add(0.0, (double)hgt, 0.0);

                     for (int i = 0; i <= 18; i++) {
                        float kx = (float)i / 18.0F;
                        pts.add(from.lerp(to, (double)Mth.clamp(t / 4.0F - kx * 0.35F, 0.0F, 1.0F)).add(0.0, (double)Mth.sin(kx * 6.0F) * 0.25, 0.0));
                     }
                  } else {
                     for (int i = 0; i <= 30; i++) {
                        float kx = (float)i / 30.0F * Math.max(0.2F, wrap);
                        float ang = kx * 22.0F + d.time * 0.03F;
                        float squeeze = 1.0F - 0.08F * Mth.sin(d.time * 0.6F);
                        pts.add(
                           vp.add(
                              (double)(Mth.cos(ang) * wid * squeeze), (double)hgt * 1.05 - (double)(kx * hgt) * 0.95, (double)(Mth.sin(ang) * wid * squeeze)
                           )
                        );
                     }
                  }

                  YFx.garuda(d, pts, al, this.f.caster);
                  break;
               case 6:
                  if (caster != null) {
                     float kx = t / (float)this.life;
                     d.groundRing(YFx.ground(caster.getPosition(partial)), 0.5F + kx * 3.0F, YFx.SIMPLE_C, 1.0F - kx, 13.0F);
                  }
               case 7:
               case 9:
               case 11:
               case 13:
               case 15:
               default:
                  break;
               case 8:
                  if (caster != null) {
                     Vec3 cx = caster.getPosition(partial).add(0.0, 1.0, 0.0);
                     float kx = t / (float)this.life;
                     if (!YFx.firstPersonOf(caster)) {
                        d.halo(cx, 2.5F * (1.0F - kx) + 0.5F, (1.0F - kx) * 0.8F);
                     }

                     d.ring(cx, 1.0F + kx * 7.0F, YFx.PURPLE, 1.0F - kx);
                     d.groundRing(YFx.ground(caster.getPosition(partial)), 1.0F + kx * 6.0F, YFx.VIOLET, 1.0F - kx, 14.0F);
                  }
                  break;
               case 10:
                  if (caster != null) {
                     Vec3 cx = caster.getPosition(partial);
                     new Random((long)caster.getId() * 7L + (long)(t / 3.0F));
                     if (!YFx.firstPersonOf(caster)) {
                        d.glow(cx.add(0.0, 1.0, 0.0), 1.4F + 0.2F * Mth.sin(t), YFx.HEAL, 0.35F * fade + 0.1F);
                     }

                     for (int i = 0; i < 6; i++) {
                        float ph = (t * 0.04F + (float)i / 6.0F) % 1.0F;
                        Vec3 p = cx.add((double)Mth.cos((float)i * 2.1F + t * 0.1F) * 0.6, (double)ph * 2.2, (double)Mth.sin((float)i * 2.1F + t * 0.1F) * 0.6);
                        d.glow(p, 0.12F, YFx.HEAL, (1.0F - ph) * fade);
                     }
                  }
                  break;
               case 12:
               case 14: {
                  float k = t / (float)this.life;
                  Vec3 c = this.f.a.add(0.0, 0.5, 0.0);
                  d.ring(c, 0.5F + k * 3.0F, YFx.PALE, 1.0F - k);
                  d.glow(c, 1.5F * (1.0F - k), YFx.PURPLE, 1.0F - k);
                  break;
               }
               case 16: {
                  float k = Mth.clamp(t / 10.0F, 0.0F, 1.0F);
                  Vec3 g = YFx.ground(this.f.a);
                  d.groundRing(g, 0.5F + k * this.f.f * 1.3F, YFx.PURPLE, 1.0F - k, 9.0F);
                  d.groundRing(g, 0.3F + k * this.f.f * 0.8F, YFx.PALE, (1.0F - k) * 0.6F, 10.0F);
                  d.glow(g.add(0.0, 0.3, 0.0), this.f.f * (1.0F - k), YFx.PURPLE, (1.0F - k) * 0.5F);
                  break;
               }
               case 17:
                  Vec3 side = caster == null ? new Vec3(1.0, 0.0, 0.0) : this.f.a.subtract(caster.position()).cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                  d.streak(this.f.a.add(side.scale(-1.3)).add(0.0, 0.5, 0.0), this.f.a.add(side.scale(1.3)).add(0.0, -0.4, 0.0), 0.12F, YFx.SIMPLE_C, fade);
                  d.glow(this.f.a, 0.8F * fade, YFx.SIMPLE_C, fade);
            }
         }
      }
   }
}
