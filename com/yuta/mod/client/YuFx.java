package com.yuta.mod.client;

import com.avatar.mod.client.AShaders;
import com.avatar.mod.client.Gfx;
import com.avatar.mod.client.Impact;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.JjkPrefs;
import com.yuta.mod.DhruvPath;
import com.yuta.mod.Net;
import com.yuta.mod.RikaEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
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
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class YuFx {
   static final float YUTA = 0.74F;
   static final float RIKA = 0.66F;
   static final float BLUE_GLOW = 1.0F;
   private static final Random RNG = new Random();
   static final Map<Integer, long[]> POSES = new HashMap<>();
   static final Map<Integer, Long> AURA = new HashMap<>();
   private static final List<YuFx.Eff> EFFECTS = new ArrayList<>();
   private static final List<YuFx.Puff> PUFFS = new ArrayList<>();
   static final Map<Integer, Long> FROZEN = new HashMap<>();
   static final Map<Integer, Long> CLAIR = new HashMap<>();
   private static long speechAt = -1000L;
   private static YuFx.Cut cut;
   private static YuFx.CamEntity cam;
   private static CameraType prevType;
   private static float shake;
   private static Matrix4f lastView = new Matrix4f();
   private static Matrix4f lastProj = new Matrix4f();
   static final float WHITE_CUT = 0.81F;

   private YuFx() {
   }

   public static boolean cutsceneActive() {
      return cut != null;
   }

   static long now() {
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

   static Vec3 rand(double s) {
      return new Vec3(RNG.nextGaussian() * s, RNG.nextGaussian() * s, RNG.nextGaussian() * s);
   }

   public static void receive(Net.Fx f) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         long now = now();
         boolean me = f.caster == mc.player.getId();
         switch (f.kind) {
            case 0:
               EFFECTS.add(new YuFx.Eff(f, now, f.dur + 4));
               break;
            case 1:
               EFFECTS.add(new YuFx.Eff(f, now, 9));

               for (int i = 0; i < 10; i++) {
                  Vec3 v = f.b.scale(0.25 + (double)RNG.nextFloat() * 0.25).add(rand(0.12));
                  PUFFS.add(new YuFx.Puff(f.a, v, 0.12F + RNG.nextFloat() * 0.1F, 8 + RNG.nextInt(6), 0.74F));
               }

               if (me || f.other == mc.player.getId()) {
                  shake = Math.max(shake, 0.5F * f.f);
               }

               if (me && f.f >= 1.5F) {
                  float[] var19 = screen(f.a);
               }
               break;
            case 2:
               POSES.put(f.caster, new long[]{(long)f.f, now, (long)f.dur});
               break;
            case 3:
               EFFECTS.add(new YuFx.Eff(f, now, f.dur));
               boolean meTarget = (int)f.f == mc.player.getId();
               if ((me || meTarget) && !JjkPrefs.skipCutscenes() && cut == null) {
                  cut = new YuFx.Cut(f);
                  prevType = mc.options.getCameraType();
                  mc.options.setCameraType(CameraType.FIRST_PERSON);
                  cam = new YuFx.CamEntity(mc.level);
               }
               break;
            case 4:
               EFFECTS.add(new YuFx.Eff(f, now, f.dur));

               for (int i = 0; i < 34; i++) {
                  double a = RNG.nextDouble() * Math.PI * 2.0;
                  Vec3 v = new Vec3(Math.cos(a), 0.08 + (double)RNG.nextFloat() * 0.25, Math.sin(a)).scale(0.18 + (double)RNG.nextFloat() * 0.28);
                  PUFFS.add(new YuFx.Puff(f.a.add(0.0, 0.4, 0.0), v, 0.7F + RNG.nextFloat() * 0.8F, 26 + RNG.nextInt(18), 0.66F));
               }

               for (int i = 0; i < 18; i++) {
                  Vec3 v = rand(0.25).add(0.0, 0.35 + (double)RNG.nextFloat() * 0.3, 0.0);
                  PUFFS.add(new YuFx.Puff(f.a.add(0.0, 0.5, 0.0), v, 0.15F + RNG.nextFloat() * 0.15F, 14 + RNG.nextInt(8), 0.74F));
               }

               double d = mc.player.position().distanceTo(f.a);
               shake = Math.max(shake, (float)(3.2 * Mth.clamp(1.0 - d / 30.0, 0.0, 1.0)));
               if (me || f.other == mc.player.getId() || d < 28.0) {
                  float[] c = screen(f.a);
                  Impact.hit(0, 0, 2, 0, c[0], c[1], true);
                  Impact.hit(6, 2, 3, 0, c[0], c[1], true);
                  Impact.hit(5, 5, 4, 8, c[0], c[1], true);
               }
            case 5:
            default:
               break;
            case 6:
               for (int i = 0; i < 40; i++) {
                  PUFFS.add(new YuFx.Puff(f.a.add(rand(1.0)), rand(0.06).add(0.0, 0.05, 0.0), 0.5F + RNG.nextFloat() * 0.7F, 30 + RNG.nextInt(20), 0.66F));
               }
               break;
            case 7:
               AURA.put(f.caster, now + (long)f.dur);
               if (f.other >= 0) {
                  Entity r = mc.level.getEntity(f.other);
                  Vec3 at = r != null ? r.position().add(0.0, 3.75, 0.0) : f.a;

                  for (int i = 0; i < 28; i++) {
                     PUFFS.add(new YuFx.Puff(at.add(rand(0.8)), rand(0.09).add(0.0, 0.03, 0.0), 0.6F + RNG.nextFloat() * 0.6F, 24 + RNG.nextInt(16), 0.66F));
                  }
               }
               break;
            case 8:
               EFFECTS.add(new YuFx.Eff(f, now, f.dur));
               if (mc.player.position().distanceTo(f.a) < 32.0) {
                  speechAt = now;
                  shake = Math.max(shake, 0.8F);
               }
               break;
            case 9:
               FROZEN.put(f.other, now + (long)f.dur);
               break;
            case 10:
            case 11:
               EFFECTS.add(new YuFx.Eff(f, now, f.dur));
               break;
            case 12:
               EFFECTS.add(new YuFx.Eff(f, now, f.dur));
               if (f.f > 0.5F) {
                  for (int i = 0; i < 22; i++) {
                     Vec3 v = rand(0.25).add(f.b.scale(0.3));
                     PUFFS.add(new YuFx.Puff(f.a, v, 0.12F + RNG.nextFloat() * 0.12F, 10 + RNG.nextInt(8), 0.74F));
                  }

                  double d = mc.player.position().distanceTo(f.a);
                  shake = Math.max(shake, (float)(1.6 * Mth.clamp(1.0 - d / 20.0, 0.0, 1.0)));
               }
               break;
            case 13:
               if (f.dur > 0) {
                  CLAIR.put(f.caster, now + (long)f.dur);
               } else {
                  CLAIR.remove(f.caster);
               }
               break;
            case 14:
               for (int i = 0; i < 10; i++) {
                  Vec3 at = f.a.lerp(f.b, (double)i / 9.0).add(0.0, 0.3 + (double)RNG.nextFloat() * 1.4, 0.0);
                  PUFFS.add(new YuFx.Puff(at, rand(0.02), 0.22F, 10 + i, 0.74F));
               }
               break;
            case 15:
               EFFECTS.add(new YuFx.Eff(f, now, f.dur));
               break;
            case 16:
               EFFECTS.add(new YuFx.Eff(f, now, f.dur + 10));
               if (me && !JjkPrefs.skipCutscenes() && cut == null) {
                  cut = new YuFx.Cut(f);
                  prevType = mc.options.getCameraType();
                  mc.options.setCameraType(CameraType.FIRST_PERSON);
                  cam = new YuFx.CamEntity(mc.level);
               }
               break;
            case 17:
               for (int i = 0; i < 40; i++) {
                  PUFFS.add(
                     new YuFx.Puff(
                        f.a.add(rand(1.2)).add(0.0, 3.0, 0.0), rand(0.07).add(0.0, 0.05, 0.0), 0.6F + RNG.nextFloat() * 0.6F, 30 + RNG.nextInt(20), 0.66F
                     )
                  );
               }
         }
      }
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            EFFECTS.clear();
            PUFFS.clear();
            POSES.clear();
            AURA.clear();
            if (cut != null) {
               endCut();
            }
         } else {
            long now = now();
            EFFECTS.removeIf(xx -> now - xx.start > (long)(xx.life + 2));
            PUFFS.removeIf(p -> !p.tick());
            AURA.values().removeIf(t -> t < now);
            FROZEN.values().removeIf(t -> t < now);
            CLAIR.values().removeIf(t -> t < now);

            for (YuFx.Eff x : EFFECTS) {
               if (x.f.kind == 16 && now - x.start == 40L) {
                  Entity r = mc.level.getEntity(x.f.other);
                  Vec3 at = r != null ? r.position().add(0.0, 3.0, 0.0) : x.f.a.add(0.0, 3.0, 0.0);

                  for (int i = 0; i < 60; i++) {
                     PUFFS.add(new YuFx.Puff(at.add(rand(1.0)), rand(0.18).add(0.0, 0.06, 0.0), 0.7F + RNG.nextFloat() * 0.9F, 30 + RNG.nextInt(24), 0.66F));
                  }

                  double d = mc.player.position().distanceTo(at);
                  if (d < 40.0) {
                     float[] c = screen(at);
                     Impact.hit(0, 0, 2, 0, c[0], c[1], true);
                     Impact.hit(6, 2, 3, 0, c[0], c[1], true);
                     Impact.hit(5, 5, 4, 8, c[0], c[1], true);
                     shake = Math.max(shake, 2.6F);
                  }
               }
            }

            shake = Math.max(0.0F, shake * 0.85F - 0.01F);
            if (cut != null) {
               cut.age++;
               boolean skip = mc.options.keyShift.isDown() && cut.age > 8 && mc.screen == null;
               if (skip || cut.age >= cut.f.dur) {
                  endCut();
               }
            }
         }
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
   public static void onRenderTick(RenderTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.START && cut != null && cam != null && mc.level != null) {
         Vec3[] s = cut.shot(e.renderTickTime);
         Vec3 eye = s[0];
         Vec3 look = s[1];
         BlockHitResult hit = mc.level.clip(new ClipContext(s[2], eye, Block.VISUAL, Fluid.NONE, mc.player));
         if (hit.getType() != Type.MISS) {
            eye = hit.getLocation().add(s[2].subtract(eye).normalize().scale(0.35));
         }

         Vec3 d = look.subtract(eye);
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
      if (e.getStage() == Stage.AFTER_PARTICLES && AShaders.flame != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            Matrix4f view = e.getPoseStack().last().pose();
            Matrix4f proj = e.getProjectionMatrix();
            lastView = new Matrix4f(view);
            lastProj = new Matrix4f(proj);
            float partial = e.getPartialTick();
            Vec3 cam = e.getCamera().getPosition();
            long now = now();
            float time = (float)(now % 100000L) + partial;
            List<RikaEntity> rikas = mc.level.getEntitiesOfClass(RikaEntity.class, new AABB(cam, cam).inflate(96.0));
            if (!EFFECTS.isEmpty() || !PUFFS.isEmpty() || !AURA.isEmpty() || !rikas.isEmpty() || !FROZEN.isEmpty()) {
               ShaderInstance fl = AShaders.flame;
               Gfx.setCamera(view, mc.gameRenderer.getDepthFar() * 0.9F);
               Gfx.f(fl, "Time", time);
               Gfx.f(fl, "Bright", 1.0F);
               Gfx.f(fl, "Length", 1.0F);
               float sky = mc.level.getSkyDarken(partial);
               Gfx.v3(fl, "SmokeLight", 0.25F * sky + 0.04F, 0.23F * sky + 0.03F, 0.2F * sky + 0.02F);
               Gfx.begin();
               int n = 0;

               for (RikaEntity r : rikas) {
                  n += rikaSmoke(cam, r, partial, time);
               }

               for (YuFx.Puff p : PUFFS) {
                  n += p.draw(cam, partial);
               }

               flush(fl, 14, view, proj, n);
               Gfx.begin();
               n = 0;

               for (YuFx.Eff x : EFFECTS) {
                  if (x.f.kind == 0) {
                     n += slashArc(mc, cam, x, partial, now);
                  }
               }

               flush(fl, 11, view, proj, n);
               Gfx.begin();
               n = 0;

               for (YuFx.Eff xx : EFFECTS) {
                  float t = (float)(now - xx.start) + partial;
                  switch (xx.f.kind) {
                     case 8:
                        n += speechWaves(cam, xx, t);
                     case 9:
                     default:
                        break;
                     case 10:
                        n += dismantleLine(cam, xx, t);
                        break;
                     case 11:
                        n += cleaveGrid(cam, xx, t);
                        break;
                     case 12:
                        n += iceFold(cam, xx, t);
                  }
               }

               for (Entry<Integer, Long> fz : FROZEN.entrySet()) {
                  Entity en = mc.level.getEntity(fz.getKey());
                  if (en != null) {
                     float left = Mth.clamp(((float)(fz.getValue() - now) - partial) / 6.0F, 0.0F, 1.0F);
                     Vec3 o = en.getPosition(partial);
                     float rad = en.getBbWidth() * 0.8F + 0.3F;

                     for (int k = 0; k < 3; k++) {
                        ringH(
                           cam,
                           o.add(0.0, 0.25 + (double)((float)k * en.getBbHeight()) * 0.35, 0.0),
                           rad,
                           time * (k % 2 == 0 ? 0.25F : -0.2F) + (float)k,
                           0.05F,
                           0.74F,
                           0.85F * left
                        );
                        n++;
                     }
                  }
               }

               flush(fl, 11, view, proj, n);
               Gfx.begin();
               n = 0;

               for (YuFx.Eff xx : EFFECTS) {
                  if (xx.f.kind == 15) {
                     n += dhruvTrails(cam, xx, (float)(now - xx.start) + partial);
                  }
               }

               flush(fl, 11, view, proj, n);
               Gfx.begin();
               int var30 = 0;

               for (YuFx.Eff xxx : EFFECTS) {
                  if (xxx.f.kind == 15) {
                     float t = (float)(now - xxx.start) + partial;
                     float fade = Mth.clamp(((float)xxx.f.dur - t) / 10.0F, 0.0F, 1.0F) * Mth.clamp(t / 6.0F, 0.0F, 1.0F);

                     for (int i = 0; i < 2; i++) {
                        Vec3 h = DhruvPath.pos(xxx.f.a, xxx.f.f, xxx.f.other, i, t);
                        Vec3 back = DhruvPath.pos(xxx.f.a, xxx.f.f, xxx.f.other, i, Math.max(0.0F, t - 1.5F));
                        blob(cam, h, 0.42F, 1.0F, 0.74F, 0.95F * fade, 0.3F + (float)i * 0.4F);
                        blob(cam, back, 0.3F, 0.8F, 0.74F, 0.8F * fade, 0.6F + (float)i * 0.4F);
                        var30 += 2;
                     }
                  }
               }

               flush(fl, 14, view, proj, var30);
               Gfx.begin();
               var30 = 0;

               for (Entry<Integer, Long> a : AURA.entrySet()) {
                  Entity c = mc.level.getEntity(a.getKey());
                  if (c != null && (c != mc.player || !mc.options.getCameraType().isFirstPerson() || cut != null)) {
                     float left = Mth.clamp(((float)(a.getValue() - now) - partial) / 8.0F, 0.0F, 1.0F);
                     Vec3 o = c.getPosition(partial);

                     for (int i = 0; i < 9; i++) {
                        float ang = (float)i * 0.7F + time * 0.05F;
                        double rr = 0.45 + 0.08 * (double)Mth.sin(time * 0.3F + (float)i);
                        double xxxx = o.x + (double)Mth.cos(ang) * rr - cam.x;
                        double z = o.z + (double)Mth.sin(ang) * rr - cam.z;
                        float h = 1.3F + 0.5F * Mth.sin(time * 0.4F + (float)i * 1.7F);
                        Gfx.upright(xxxx, o.y - 0.05 - cam.y, z, 0.32F, h, (float)i * 0.137F % 1.0F, 0.85F, 0.74F, 0.75F * left);
                        var30++;
                     }
                  }
               }

               flush(fl, 12, view, proj, var30);
               Gfx.begin();
               var30 = 0;

               for (YuFx.Eff xxxx : EFFECTS) {
                  float t = (float)(now - xxxx.start) + partial;
                  float ax = Mth.clamp(1.0F - t / (float)xxxx.life, 0.0F, 1.0F);
                  if (xxxx.f.kind == 1) {
                     glow(cam, xxxx.f.a, 0.55F * xxxx.f.f, 0.8F, 1.0F, ax);
                     var30++;
                  } else if (xxxx.f.kind == 4 && t < 8.0F) {
                     glow(cam, xxxx.f.a.add(0.0, 0.6, 0.0), xxxx.f.f * 1.3F, 0.7F, 0.0F, ax * (1.0F - t / 8.0F));
                     var30++;
                  } else if (xxxx.f.kind == 12) {
                     glow(cam, xxxx.f.a, xxxx.f.f > 0.5F ? 2.2F * ax : 1.1F + 0.3F * Mth.sin(t), 0.6F, 1.0F, xxxx.f.f > 0.5F ? ax : 0.35F);
                     var30++;
                  } else if (xxxx.f.kind == 8 && t < 8.0F) {
                     glow(cam, xxxx.f.a, 0.5F, 0.7F, 1.0F, 1.0F - t / 8.0F);
                     var30++;
                  } else if (xxxx.f.kind == 15) {
                     float fade = Mth.clamp(((float)xxxx.f.dur - t) / 10.0F, 0.0F, 1.0F);

                     for (int i = 0; i < 2; i++) {
                        glow(cam, DhruvPath.pos(xxxx.f.a, xxxx.f.f, xxxx.f.other, i, t), 0.9F, 0.7F, 1.0F, 0.45F * fade);
                        var30++;
                     }
                  }
               }

               flush(fl, 4, view, proj, var30);
               Gfx.begin();
               var30 = 0;

               for (YuFx.Eff xxxxx : EFFECTS) {
                  if (xxxxx.f.kind == 4) {
                     float t = (float)(now - xxxxx.start) + partial;
                     float ax = Mth.clamp(1.0F - t / (float)xxxxx.life, 0.0F, 1.0F);
                     Gfx.ground(xxxxx.f.a.x - cam.x, xxxxx.f.a.y + 0.06 - cam.y, xxxxx.f.a.z - cam.z, xxxxx.f.f * (0.5F + t * 0.12F), 0.0F, 0.55F, 0.2F, ax);
                     var30++;
                  }
               }

               flush(fl, 3, view, proj, var30);
            }
         }
      }
   }

   private static void flush(ShaderInstance sh, int mode, Matrix4f view, Matrix4f proj, int n) {
      if (n > 0) {
         Gfx.f(sh, "Mode", (float)mode);
         Gfx.flush(sh, view, proj, true);
      } else {
         Gfx.flushEmpty();
      }
   }

   static void glow(Vec3 cam, Vec3 p, float half, float heat, float pal, float a) {
      double d = p.distanceTo(cam);
      float near = (float)Mth.clamp((d - (double)half * 0.6 - 0.25) / 1.5, 0.0, 1.0);
      if (near > 0.01F && a > 0.003F) {
         Gfx.billboard(p.x - cam.x, p.y - cam.y, p.z - cam.z, half, 0.0F, 0.0F, Mth.clamp(heat, 0.0F, 1.0F), pal, Mth.clamp(a * near, 0.0F, 1.0F));
      }
   }

   static void blob(Vec3 cam, Vec3 p, float half, float heat, float pal, float a, float seed) {
      double d = p.distanceTo(cam);
      float near = (float)Mth.clamp((d - (double)half - 0.3) / 1.5, 0.0, 1.0);
      if (near > 0.01F && a > 0.003F) {
         Gfx.billboard(p.x - cam.x, p.y - cam.y, p.z - cam.z, half, seed * 6.28F, seed, Mth.clamp(heat, 0.0F, 1.0F), pal, Mth.clamp(a * near, 0.0F, 1.0F));
      }
   }

   static void ribbon(Vec3 cam, List<Vec3> pts, float[] widths, float pal, float heat, float alpha, float seed) {
      int n = pts.size();
      if (n >= 2) {
         float[] p = new float[n * 3];
         float[] u = new float[n];

         for (int i = 0; i < n; i++) {
            Vec3 v = pts.get(i);
            p[i * 3] = (float)(v.x - cam.x);
            p[i * 3 + 1] = (float)(v.y - cam.y);
            p[i * 3 + 2] = (float)(v.z - cam.z);
            u[i] = (float)i / ((float)n - 1.0F);
         }

         Gfx.ribbon(p, widths, u, seed, heat, pal, Mth.clamp(alpha, 0.0F, 1.0F));
      }
   }

   private static int slashArc(Minecraft mc, Vec3 cam, YuFx.Eff x, float partial, long now) {
      Entity c = mc.level.getEntity(x.f.caster);
      Vec3 look = x.f.b;
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
      Vec3 right = flat.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 up = new Vec3(0.0, 1.0, 0.0);
      Vec3 centre = (c != null ? c.getPosition(partial) : x.f.a).add(0.0, 1.15, 0.0).add(flat.scale(0.4));
      float t = (float)(now - x.start) + partial;
      float life = (float)(x.life - 4);
      float lead = Mth.clamp(t / (life * 0.55F), 0.0F, 1.0F);
      float fade = Mth.clamp(1.0F - (t - life * 0.55F) / (life * 0.6F), 0.0F, 1.0F);
      if (fade <= 0.0F) {
         return 0;
      } else {
         int step = x.f.other;
         float size = x.f.f;
         double radius = 2.0 * (double)size;
         float a0;
         float a1;
         if (step == 2) {
            a0 = 1.5F;
            a1 = -1.0F;
         } else {
            a0 = step == 0 ? 1.25F : -1.25F;
            a1 = -a0;
         }

         float head = Mth.lerp(lead, a0, a1);
         float tailLen = 1.5F;
         List<Vec3> pts = new ArrayList<>();
         int segs = 14;
         float[] w = new float[segs + 1];

         for (int i = 0; i <= segs; i++) {
            float k = (float)i / (float)segs;
            float ang = head - Math.signum(a1 - a0) * tailLen * k;
            if (a1 > a0 && ang < a0 || a1 < a0 && ang > a0) {
               ang = a0;
            }

            Vec3 p;
            if (step == 2) {
               p = centre.add(flat.scale(Math.cos((double)ang) * radius * 0.8)).add(up.scale(Math.sin((double)ang) * radius * 0.75));
            } else {
               double tilt = (double)(step == 0 ? -1 : 1) * 0.18 * Math.sin((double)ang);
               p = centre.add(flat.scale(Math.cos((double)ang) * radius)).add(right.scale(Math.sin((double)ang) * radius)).add(up.scale(tilt));
            }

            pts.add(p);
            w[i] = (float)(0.34 * (double)size * (double)(1.0F - k) * (0.6 + 0.4 * (double)fade));
         }

         ribbon(cam, pts, w, 0.74F, 0.95F * fade, 0.95F * fade, (float)(x.start % 7L) / 7.0F);
         return 1;
      }
   }

   static void ringH(Vec3 cam, Vec3 c, float radius, float spin, float width, float pal, float alpha) {
      List<Vec3> pts = new ArrayList<>();
      int seg = 18;
      float[] w = new float[seg + 1];

      for (int i = 0; i <= seg; i++) {
         float a = spin + (float)i / (float)seg * (float) (Math.PI * 2) * 0.85F;
         pts.add(c.add((double)(Mth.cos(a) * radius), 0.0, (double)(Mth.sin(a) * radius)));
         w[i] = width;
      }

      ribbon(cam, pts, w, pal, 0.9F, alpha, spin * 0.1F % 1.0F + 0.5F);
   }

   private static int dismantleLine(Vec3 cam, YuFx.Eff x, float t) {
      float k = Mth.clamp(t / 5.0F, 0.0F, 1.0F);
      float fade = Mth.clamp(1.0F - (t - 5.0F) / 4.0F, 0.0F, 1.0F);
      if (fade <= 0.0F) {
         return 0;
      } else {
         Vec3 dir = x.f.b.normalize();
         Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
         side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
         Vec3 up = side.cross(dir).normalize();
         float tilt = (float)(x.f.other - 1) * 0.6F;
         Vec3 across = side.scale((double)Mth.cos(tilt)).add(up.scale((double)Mth.sin(tilt)));
         Vec3 c = x.f.a.add(dir.scale((double)(x.f.f * k)));
         List<Vec3> pts = new ArrayList<>();
         float[] w = new float[9];

         for (int i = 0; i <= 8; i++) {
            float u = (float)i / 8.0F - 0.5F;
            pts.add(c.add(across.scale((double)u * 2.6)).add(dir.scale((double)(-Math.abs(u)) * 0.6)));
            w[i] = 0.07F * (1.0F - Math.abs(u) * 1.6F);
         }

         ribbon(cam, pts, w, 0.81F, 1.0F, fade, 0.2F + (float)x.f.other * 0.3F);
         return 1;
      }
   }

   private static int cleaveGrid(Vec3 cam, YuFx.Eff x, float t) {
      int n = 0;
      Vec3 look = x.f.b.normalize();
      Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
      side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
      Vec3 up = side.cross(look).normalize();
      Vec3 face = x.f.a.subtract(look.scale(0.4));
      float size = Math.max(1.0F, x.f.f) * 0.75F;

      for (int k = 0; k < 4; k++) {
         float born = (float)(2 + k * 2);
         float age = t - born;
         if (!(age < 0.0F)) {
            float fade = Mth.clamp(1.0F - (age - 4.0F) / 6.0F, 0.0F, 1.0F);
            float ang = new float[]{0.15F, 1.7F, 0.85F, 2.4F}[k];
            Vec3 across = side.scale((double)Mth.cos(ang)).add(up.scale((double)Mth.sin(ang)));
            Vec3 off = up.scale((k % 2 == 0 ? 0.25 : -0.25) * (double)size);
            List<Vec3> pts = new ArrayList<>();
            float[] w = new float[7];
            float grow = Mth.clamp(age / 2.0F, 0.0F, 1.0F);

            for (int i = 0; i <= 6; i++) {
               float u = ((float)i / 6.0F - 0.5F) * grow;
               pts.add(face.add(off).add(across.scale((double)(u * size * 2.0F))));
               w[i] = 0.05F * (1.0F - Math.abs(u) * 1.4F);
            }

            ribbon(cam, pts, w, 0.81F, 1.0F, fade, 0.1F + (float)k * 0.2F);
            n++;
         }
      }

      return n;
   }

   private static int iceFold(Vec3 cam, YuFx.Eff x, float t) {
      int n = 0;
      Vec3 look = x.f.b.normalize();
      Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
      side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
      Vec3 up = side.cross(look).normalize();
      boolean strike = x.f.f > 0.5F;
      float fade = Mth.clamp(1.0F - t / (float)x.life, 0.0F, 1.0F);

      for (int k = 0; k < 4; k++) {
         float r = strike ? 0.4F + t * 0.35F + (float)k * 0.5F : 1.6F - (t * 0.08F + (float)k * 0.3F) % 1.4F;
         List<Vec3> pts = new ArrayList<>();
         int seg = 16;
         float[] w = new float[seg + 1];

         for (int i = 0; i <= seg; i++) {
            float a = (float)i / (float)seg * (float) (Math.PI * 2);
            double warp = strike ? 0.0 : 0.12 * (double)Mth.sin(a * 3.0F + t * 0.6F + (float)k);
            pts.add(x.f.a.add(side.scale((double)Mth.cos(a) * ((double)r + warp))).add(up.scale((double)Mth.sin(a) * ((double)r + warp))));
            w[i] = 0.05F;
         }

         ribbon(cam, pts, w, strike ? 0.81F : 0.74F, 1.0F, (strike ? fade : 0.7F) * (1.0F - (float)k * 0.18F), 0.3F + (float)k * 0.2F);
         n++;
      }

      return n;
   }

   private static int speechWaves(Vec3 cam, YuFx.Eff x, float t) {
      int n = 0;
      Vec3 look = x.f.b.normalize();
      Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
      side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();

      for (int k = 0; k < 4; k++) {
         float d = t * 0.9F - (float)k * 2.5F;
         if (!(d <= 0.3F) && !(d > x.f.f)) {
            float fade = Mth.clamp(1.0F - d / x.f.f, 0.0F, 1.0F);
            List<Vec3> pts = new ArrayList<>();
            int seg = 12;
            float[] w = new float[seg + 1];
            float half = (float)Math.toRadians(35.0);

            for (int i = 0; i <= seg; i++) {
               float a = -half + 2.0F * half * (float)i / (float)seg;
               Vec3 dir = look.scale((double)Mth.cos(a)).add(side.scale((double)Mth.sin(a)));
               pts.add(x.f.a.add(dir.scale((double)d)));
               w[i] = 0.08F + d * 0.012F;
            }

            ribbon(cam, pts, w, 0.74F, 1.0F, 0.85F * fade, 0.4F + (float)k * 0.15F);
            n++;
         }
      }

      return n;
   }

   private static int dhruvTrails(Vec3 cam, YuFx.Eff x, float t) {
      float fade = Mth.clamp(((float)x.f.dur - t) / 10.0F, 0.0F, 1.0F) * Mth.clamp(t / 6.0F, 0.0F, 1.0F);
      int n = 0;

      for (int i = 0; i < 2; i++) {
         List<Vec3> pts = new ArrayList<>();
         int seg = 26;
         float[] w = new float[seg + 1];

         for (int k = 0; k <= seg; k++) {
            pts.add(DhruvPath.pos(x.f.a, x.f.f, x.f.other, i, Math.max(0.0F, t - (float)k)));
            w[k] = 0.16F * (1.0F - (float)k / (float)seg) + 0.02F;
         }

         ribbon(cam, pts, w, 0.74F, 1.0F, 0.9F * fade, 0.25F + (float)i * 0.5F);
         n++;
      }

      return n;
   }

   private static int rikaSmoke(Vec3 cam, RikaEntity r, float partial, float time) {
      Vec3 base = r.getPosition(partial).add(0.0, 0.12, 0.0);
      int n = 0;

      for (int i = 0; i < 10; i++) {
         float ang = (float)i * 0.628F + time * 0.01F;
         float rad = 0.55F + 0.45F * (float)(i * 37 % 10) / 10.0F;
         Vec3 p = base.add((double)(Mth.cos(ang) * rad), 0.05 * (double)Mth.sin(time * 0.1F + (float)i), (double)(Mth.sin(ang) * rad));
         blob(cam, p, 0.42F + 0.08F * Mth.sin(time * 0.12F + (float)i), 0.02F, 0.66F, 0.85F, ((float)i * 0.31F + (float)r.getId() * 0.07F) % 1.0F);
         n++;
      }

      return n;
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
   public static void onNameTag(RenderNameTagEvent e) {
      if (cut != null && (e.getEntity().getId() == cut.f.caster || e.getEntity().getId() == (int)cut.f.f)) {
         e.setResult(Result.DENY);
      }
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      GuiGraphics g = e.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      float partial = e.getPartialTick();
      long now = now();
      float st = (float)(now - speechAt) + partial;
      if (st >= 0.0F && st < 30.0F && cut == null) {
         float a = Mth.clamp(st / 2.0F, 0.0F, 1.0F) * Mth.clamp((30.0F - st) / 6.0F, 0.0F, 1.0F);
         g.pose().pushPose();
         g.pose().translate((float)w / 2.0F, (float)h * 0.3F, 0.0F);
         g.pose().scale(4.0F, 4.0F, 1.0F);
         g.drawCenteredString(mc.font, "動くな", 0, 0, argb(a, 10467583));
         g.pose().popPose();
         g.pose().pushPose();
         g.pose().translate((float)w / 2.0F, (float)h * 0.3F + 44.0F, 0.0F);
         g.pose().scale(2.0F, 2.0F, 1.0F);
         g.drawCenteredString(mc.font, "DON'T MOVE", 0, 0, argb(a, 16777215));
         g.pose().popPose();
      }

      Long cl = mc.player == null ? null : CLAIR.get(mc.player.getId());
      if (cl != null && cl > now && cut == null) {
         float left = Mth.clamp(((float)(cl - now) - partial) / 10.0F, 0.0F, 1.0F);
         int col = argb(0.85F * left, 328965);
         int b = 6;
         g.fill(0, 0, w, b, col);
         g.fill(0, h - b, w, h, col);
         g.fill(0, 0, b, h, col);
         g.fill(w - b, 0, w, h, col);
         g.fill(0, h / 3, w / 5, h / 3 + 3, col);
         g.fill(w - w / 5, h - h / 3, w, h - h / 3 + 3, col);
         g.fill(w / 4, 0, w / 4 + 3, h / 6, col);
         g.fill(w - w / 4, h - h / 6, w - w / 4 + 3, h, col);
         g.drawString(mc.font, "未来視  CLAIRVOYANCE", b + 6, b + 6, argb(left, 15263976), true);
      }

      if (cut != null) {
         float t = (float)cut.age + partial;
         float bk = smooth(t / 4.0F) * smooth(((float)cut.f.dur - t) / 5.0F);
         int bar = (int)((float)h * 0.12F * bk);
         g.fill(0, 0, w, bar, -16777216);
         g.fill(0, h - bar, w, h, -16777216);
         if (cut.f.kind == 16) {
            title(g, mc, w, h, t, 18.0F, 38.0F, "完全顕現", "FULL MANIFESTATION", 14074623);
            title(g, mc, w, h, t, 41.0F, 58.0F, "里香", "RIKA", 15922431);
         } else {
            if (t > 1.0F && t < 15.0F) {
               float a = Mth.clamp((t - 1.0F) / 2.0F, 0.0F, 1.0F) * Mth.clamp((15.0F - t) / 3.0F, 0.0F, 1.0F);
               float jx = (RNG.nextFloat() - 0.5F) * 2.0F;
               float jy = (RNG.nextFloat() - 0.5F) * 2.0F;
               String kanji = "里香";
               float s = 5.5F + (t - 1.0F) * 0.06F;
               g.pose().pushPose();
               g.pose().translate((float)w * 0.24F + jx, (float)h * 0.36F + jy, 0.0F);
               g.pose().scale(s, s, 1.0F);
               g.drawString(mc.font, kanji, -mc.font.width(kanji) / 2 + 1, 1, argb(a, 328458), false);
               g.drawString(mc.font, kanji, -mc.font.width(kanji) / 2, 0, argb(a, 15922431), false);
               g.pose().popPose();
               g.pose().pushPose();
               g.pose().translate((float)w * 0.24F, (float)h * 0.36F + s * 10.0F + 6.0F, 0.0F);
               g.pose().scale(2.0F, 2.0F, 1.0F);
               g.drawCenteredString(mc.font, "RIKA!", 0, 0, argb(a, 9417983));
               g.pose().popPose();
            }
         }
      }
   }

   private static void title(GuiGraphics g, Minecraft mc, int w, int h, float t, float t0, float t1, String kanji, String en, int col) {
      if (!(t <= t0) && !(t >= t1)) {
         float a = Mth.clamp((t - t0) / 3.0F, 0.0F, 1.0F) * Mth.clamp((t1 - t) / 3.0F, 0.0F, 1.0F);
         g.pose().pushPose();
         g.pose().translate((float)w * 0.25F, (float)h * 0.36F, 0.0F);
         g.pose().scale(5.0F, 5.0F, 1.0F);
         g.drawString(mc.font, kanji, -mc.font.width(kanji) / 2 + 1, 1, argb(a, 328458), false);
         g.drawString(mc.font, kanji, -mc.font.width(kanji) / 2, 0, argb(a, col), false);
         g.pose().popPose();
         g.pose().pushPose();
         g.pose().translate((float)w * 0.25F, (float)h * 0.36F + 56.0F, 0.0F);
         g.pose().scale(2.0F, 2.0F, 1.0F);
         g.drawCenteredString(mc.font, en, 0, 0, argb(a, 16777215));
         g.pose().popPose();
      }
   }

   static int argb(float a, int rgb) {
      return Mth.clamp((int)(a * 255.0F), 0, 255) << 24 | rgb & 16777215;
   }

   static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   private static Vec3[] fullShot(float t, Vec3 yp, Vec3 head, Vec3 dir, Vec3 side, Entity r, float partial) {
      YuFx.Cut c = cut;
      float big = 1.35F;
      Vec3 rHead = r != null ? r.getPosition(partial).add(0.0, 4.75 * (double)big, 0.0) : head.add(0.0, 3.0, 0.0).subtract(dir.scale(3.0));
      if (t < 20.0F) {
         float k = t / 20.0F;
         c.fov = 44.0F - 6.0F * k;
         c.roll = 0.0F;
         Vec3 eye = head.add(dir.scale(1.25 - 0.25 * (double)k)).add(side.scale(0.35)).add(0.0, -0.15, 0.0);
         return new Vec3[]{eye, head.add(0.0, -0.1, 0.0), head};
      } else if (t < 40.0F) {
         float k = (t - 20.0F) / 20.0F;
         c.fov = 64.0F;
         c.roll = -2.0F * k;
         Vec3 eye = yp.add(dir.scale(5.5)).add(side.scale(2.5)).add(0.0, 0.6, 0.0);
         return new Vec3[]{eye, head.lerp(rHead, 0.35 + 0.5 * (double)k), head};
      } else {
         float k = (t - 40.0F) / 20.0F;
         c.fov = 70.0F - 12.0F * k;
         c.roll = 0.0F;
         Vec3 rFwd = r != null ? Vec3.directionFromRotation(0.0F, r.getYRot()) : dir;
         Vec3 eye = rHead.add(rFwd.scale(4.2 - (double)k)).add(0.0, -0.8, 0.0);
         return new Vec3[]{eye, rHead, rHead};
      }
   }

   static boolean mine() {
      return ClientSorcerers.mine() == Sorcerer.YUTA;
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

   static final class Cut {
      final Net.Fx f;
      int age;
      float fov = 55.0F;
      float roll;

      Cut(Net.Fx f) {
         this.f = f;
      }

      Vec3[] shot(float partial) {
         Minecraft mc = Minecraft.getInstance();
         float t = (float)this.age + partial;
         Entity y = mc.level.getEntity(this.f.caster);
         Entity r = mc.level.getEntity(this.f.other);
         Vec3 yp = y != null ? y.getPosition(partial) : this.f.a.subtract(this.f.b.scale(8.0));
         Vec3 head = yp.add(0.0, 1.6, 0.0);
         Vec3 dir = new Vec3(this.f.b.x, 0.0, this.f.b.z).normalize();
         Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
         Vec3 rp = r != null ? r.getPosition(partial).add(0.0, 4.35, 0.0) : head.add(0.0, 2.5, 0.0).subtract(dir.scale(3.0));
         Vec3 smash = this.f.a;
         if (this.f.kind == 16) {
            return YuFx.fullShot(t, yp, head, dir, side, r, partial);
         } else if (t < 12.0F) {
            float k = t / 12.0F;
            this.fov = 56.0F - 6.0F * k;
            this.roll = -2.0F;
            Vec3 eye = head.add(dir.scale(2.2 - 0.4 * (double)k)).add(side.scale(0.7)).add(0.0, -0.55, 0.0);
            return new Vec3[]{eye, head.add(0.0, 0.45 + 0.3 * (double)k, 0.0), head};
         } else if (t < 21.0F) {
            float k = (t - 12.0F) / 9.0F;
            this.fov = 62.0F;
            this.roll = 0.0F;
            Vec3 mid = yp.lerp(smash, 0.5);
            double dist = yp.distanceTo(smash);
            Vec3 eye = mid.add(side.scale(dist * 0.55 + 5.5)).add(0.0, 2.4 + (double)k * 0.6, 0.0);
            return new Vec3[]{eye, rp.lerp(smash.add(0.0, 1.0, 0.0), 0.3), mid.add(0.0, 1.0, 0.0)};
         } else if (t < 32.0F) {
            float k = (t - 21.0F) / 11.0F;
            this.fov = 72.0F - 14.0F * YuFx.smooth(k);
            this.roll = 3.0F * (1.0F - k);
            Vec3 eye = smash.add(side.scale(-3.4)).subtract(dir.scale(1.6)).add(0.0, 0.55, 0.0);
            return new Vec3[]{eye, smash.add(0.0, 1.2 + 0.4 * (double)(1.0F - k), 0.0), smash.add(0.0, 1.0, 0.0)};
         } else {
            float k = (t - 32.0F) / Math.max(1.0F, (float)this.f.dur - 32.0F);
            this.fov = 60.0F + 4.0F * k;
            this.roll = 0.0F;
            Vec3 eye = yp.subtract(dir.scale(3.6 + (double)k)).add(side.scale(1.3)).add(0.0, 2.9, 0.0);
            return new Vec3[]{eye, smash.add(0.0, 1.2, 0.0), head};
         }
      }
   }

   static final class Eff {
      final Net.Fx f;
      final long start;
      final int life;

      Eff(Net.Fx f, long start, int life) {
         this.f = f;
         this.start = start;
         this.life = Math.max(1, life);
      }
   }

   static final class Puff {
      Vec3 pos;
      Vec3 prev;
      Vec3 vel;
      final float size;
      final float pal;
      final float seed;
      final int life;
      int age;

      Puff(Vec3 pos, Vec3 vel, float size, int life, float pal) {
         this.pos = this.prev = pos;
         this.vel = vel;
         this.size = size;
         this.life = life;
         this.pal = pal;
         this.seed = YuFx.RNG.nextFloat();
      }

      boolean tick() {
         this.prev = this.pos;
         this.pos = this.pos.add(this.vel);
         this.vel = this.vel.scale(this.pal == 0.66F ? 0.9 : 0.82).add(0.0, this.pal == 0.66F ? 0.004 : -0.01, 0.0);
         return ++this.age < this.life;
      }

      int draw(Vec3 cam, float partial) {
         float k = ((float)this.age + partial) / (float)this.life;
         float s = this.pal == 0.66F ? this.size * (0.7F + 0.6F * k) : this.size * (1.0F - k);
         YuFx.blob(
            cam,
            this.prev.lerp(this.pos, (double)partial),
            s,
            this.pal == 0.66F ? 0.4F * (1.0F - k) : 1.0F - k,
            this.pal,
            (1.0F - k) * (this.pal == 0.66F ? 0.85F : 1.0F),
            this.seed
         );
         return 1;
      }
   }
}
