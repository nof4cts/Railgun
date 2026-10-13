package com.avatar.mod.client;

import com.avatar.mod.FxKind;
import com.avatar.mod.Net;
import com.jjk.mod.client.JjkPrefs;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class FireFx {
   static final List<FireFx.Puff> PUFFS = new ArrayList<>();
   static final List<FireFx.Ember> EMBERS = new ArrayList<>();
   static final List<FireFx.Flash> FLASHES = new ArrayList<>();
   static final List<FireFx.Ring> RINGS = new ArrayList<>();
   static final List<FireFx.Shot> SHOTS = new ArrayList<>();
   public static final Map<Integer, long[]> POSES = new HashMap<>();
   static final Map<Integer, Long> CHARGING = new HashMap<>();
   static final Map<Integer, Long> FULL = new HashMap<>();
   static final Random RNG = new Random();
   private static final float[] HAZE = new float[1024];
   private static int hazeCount;
   private static TextureTarget copy;
   static FireFx.Cine cut;
   private static FireFx.CamEntity cam;
   private static CameraType prevType;
   static float shake;
   static float fovKick;
   static float flash;

   private FireFx() {
   }

   public static boolean cutsceneActive() {
      return cut != null;
   }

   static void startCut(FireFx.Cine c) {
      Minecraft mc = Minecraft.getInstance();
      if (cut == null && !JjkPrefs.skipCutscenes() && mc.level != null) {
         cut = c;
         prevType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         cam = new FireFx.CamEntity(mc.level);
      }
   }

   static long gameTime() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0L : mc.level.getGameTime();
   }

   static int pose(Entity e) {
      long[] p = POSES.get(e.getId());
      return p != null && gameTime() < p[1] + p[2] ? (int)p[0] : 0;
   }

   static float breath(Entity e, float partial) {
      Long s = CHARGING.get(e.getId());
      return s == null ? 0.0F : Mth.clamp(((float)(gameTime() - s) + partial) / 35.0F, 0.0F, 1.0F);
   }

   static boolean firstPersonSelf(Entity e) {
      Minecraft mc = Minecraft.getInstance();
      return e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
   }

   static Vec3 fist(Entity e, int side, float partial) {
      Vec3 look = e.getViewVector(partial);
      if (firstPersonSelf(e)) {
         Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
         right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
         Vec3 up = right.cross(look).normalize();
         return e.getEyePosition(partial).add(look.scale(0.9)).add(right.scale(0.42 * (double)side)).add(up.scale(-0.42));
      } else {
         float yaw = e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getYRot();
         Vec3 fwd = Vec3.directionFromRotation(0.0F, yaw);
         Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
         Vec3 base = e.getPosition(partial);
         return !CHARGING.containsKey(e.getId()) && pose(e) != 4
            ? e.getEyePosition(partial).add(look.scale(0.7)).add(right.scale(0.3 * (double)side)).add(0.0, -0.35, 0.0)
            : base.add(0.0, 0.78, 0.0).add(right.scale(0.42 * (double)side)).add(fwd.scale(0.05));
      }
   }

   public static void receive(Net.Fx f) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         long now = gameTime();
         Entity caster = mc.level.getEntity(f.caster);
         switch (f.kind) {
            case 0:
               POSES.put(f.caster, new long[]{(long)f.f, now, (long)f.dur});
               if (caster == mc.player && f.f != 4.0F) {
                  mc.player.swing(InteractionHand.MAIN_HAND);
                  fovKick = Math.max(fovKick, f.f == 5.0F ? 0.07F : 0.03F);
               }

               if (f.f == 5.0F && caster != null) {
                  exhale(caster, 1.0F);
               }
               break;
            case 1:
               if (f.f > 0.5F) {
                  CHARGING.put(f.caster, now);
               } else {
                  CHARGING.remove(f.caster);
                  FULL.remove(f.caster);
                  long[] p = POSES.get(f.caster);
                  if (p != null && p[0] == 4L && (cut == null || cut.caster != f.caster)) {
                     POSES.remove(f.caster);
                  }
               }
               break;
            case 2:
               FULL.put(f.caster, now);
               if (caster != null) {
                  for (int side = -1; side <= 1; side += 2) {
                     Vec3 p = fist(caster, side, 0.0F);
                     FLASHES.add(new FireFx.Flash(p, 1.4F, 1.0F, 6).blue(Arts.isBlue(caster)));
                     burst(p, 0.25F, 6, 0.06F, 0.9F, Arts.isBlue(caster));
                  }

                  if (caster == mc.player) {
                     fovKick = Math.max(fovKick, 0.04F);
                  }
               }
               break;
            case 3:
               SHOTS.add(new FireFx.Shot(f));
               if (FxKind.elem(f.flags) != 0) {
                  return;
               }

               if (cut != null && f.caster == cut.caster && cut.serial < 0) {
                  cut.serial = f.other;
                  cut.launchAge = cut.age;
               }

               Vec3 dir = f.b.normalize();
               float s = f.f;
               boolean blue = (f.flags & 1) != 0;
               FLASHES.add(new FireFx.Flash(f.a, s * 3.2F, 1.0F, 4).blue(blue));
               burst(f.a.add(dir.scale((double)s * 0.5)), s * 0.8F, 4 + (int)(s * 6.0F), 0.05F + s * 0.05F, 1.0F, blue);

               for (int i = 0; i < 3 + (int)(s * 4.0F); i++) {
                  Vec3 v = dir.scale(0.25 + (double)RNG.nextFloat() * 0.35).add(rand(0.05 + (double)s * 0.04));
                  PUFFS.add(new FireFx.Puff(f.a, v, s * (0.5F + RNG.nextFloat() * 0.4F), 1.06F, 1.0F, 0.1F, 0.45F, 9 + RNG.nextInt(5), 10).blue(blue));
               }

               if (caster != null && f.f > 1.0F) {
                  shake(f.a, 0.6F);
               }
               break;
            case 4:
               endShot(f.other);
               cut(f.other);
               if (FxKind.elem(f.flags) != 0) {
                  Elems.impact(f.a, f.b, f.f, FxKind.elem(f.flags));
                  return;
               }

               explode(f.a, f.b, f.f, (f.flags & 1) != 0);
               break;
            case 5:
               endShot(f.other);
               cut(f.other);
               if (FxKind.elem(f.flags) != 0) {
                  Elems.impact(f.a, f.b, f.f * 0.6F, FxKind.elem(f.flags));
                  return;
               }

               for (int i = 0; i < 5; i++) {
                  PUFFS.add(
                     new FireFx.Puff(
                           f.a.add(rand(0.3)),
                           f.b.scale(0.06).add(rand(0.03)),
                           f.f * (0.6F + RNG.nextFloat() * 0.5F),
                           1.05F,
                           0.6F,
                           0.07F,
                           0.4F,
                           10 + RNG.nextInt(6),
                           10
                        )
                        .blue((f.flags & 1) != 0)
                  );
               }
               break;
            case 6:
               endShot(f.other);
               cut(f.other);

               for (int i = 0; i < 10 + (int)(f.f * 10.0F); i++) {
                  Vec3 v = new Vec3(RNG.nextGaussian() * 0.05, 0.06 + (double)RNG.nextFloat() * 0.08, RNG.nextGaussian() * 0.05);
                  FireFx.Puff p = new FireFx.Puff(
                     f.a.add(rand(0.3)), v, 0.35F + f.f * 0.6F + RNG.nextFloat() * 0.3F, 1.05F, 0.1F, 0.05F, 0.45F, 24 + RNG.nextInt(16), 7
                  );
                  PUFFS.add(p);
               }
               break;
            case 7:
               if (caster == mc.player) {
                  startCut(new FireFx.Cine(f));
               }
               break;
            default:
               Arts.receive(f, caster);
         }
      }
   }

   private static void cut(int serial) {
      if (cut != null && cut.serial == serial && cut.endAge < 0) {
         cut.endAge = cut.age;
      }
   }

   private static FireFx.Shot endShot(int serial) {
      Iterator<FireFx.Shot> it = SHOTS.iterator();

      while (it.hasNext()) {
         FireFx.Shot s = it.next();
         if (s.serial == serial) {
            it.remove();
            return s;
         }
      }

      return null;
   }

   static Vec3 rand(double s) {
      return new Vec3(RNG.nextGaussian() * s, RNG.nextGaussian() * s, RNG.nextGaussian() * s);
   }

   static void burst(Vec3 at, float size, int n, float speed, float heat, boolean blue) {
      for (int i = 0; i < n; i++) {
         Vec3 v = rand(1.0).normalize().scale((double)speed * (0.4 + (double)RNG.nextFloat()));
         PUFFS.add(
            new FireFx.Puff(at, v, size * (0.6F + RNG.nextFloat() * 0.6F), 1.07F, heat, 0.08F + RNG.nextFloat() * 0.05F, 0.5F, 10 + RNG.nextInt(8), 10)
               .blue(blue)
         );
      }
   }

   private static void exhale(Entity e, float k) {
      if (!firstPersonSelf(e)) {
         Vec3 look = e.getLookAngle();
         Vec3 mouth = e.getEyePosition().add(look.scale(0.3)).add(0.0, -0.15, 0.0);

         for (int i = 0; i < 4; i++) {
            PUFFS.add(
               new FireFx.Puff(
                  mouth,
                  look.scale(0.06 + (double)RNG.nextFloat() * 0.04).add(0.0, 0.01, 0.0),
                  0.12F + RNG.nextFloat() * 0.08F,
                  1.07F,
                  0.35F * k,
                  0.06F,
                  0.35F,
                  14 + RNG.nextInt(8),
                  0
               )
            );
         }
      }
   }

   static void explode(Vec3 at, Vec3 dir, float size, boolean blue) {
      Minecraft mc = Minecraft.getInstance();
      boolean big = size > 2.0F;
      FLASHES.add(new FireFx.Flash(at, size * 4.5F, 1.0F, big ? 9 : 5).blue(blue));
      int n = 10 + (int)(size * (float)(big ? 14 : 22));

      for (int i = 0; i < n; i++) {
         Vec3 v = rand(1.0).normalize().scale((0.05 + (double)RNG.nextFloat() * 0.18) * (0.6 + (double)size * 0.45)).add(dir.scale(-0.05));
         PUFFS.add(
            new FireFx.Puff(
                  at.add(rand((double)size * 0.15)),
                  v,
                  size * (0.35F + RNG.nextFloat() * 0.45F),
                  1.04F + RNG.nextFloat() * 0.03F,
                  1.0F,
                  (big ? 0.035F : 0.06F) + RNG.nextFloat() * 0.04F,
                  0.55F,
                  (big ? 26 : 14) + RNG.nextInt(big ? 20 : 10),
                  10
               )
               .blue(blue)
         );
      }

      int embers = 8 + (int)(size * 12.0F);

      for (int i = 0; i < embers; i++) {
         Vec3 v = rand(1.0).normalize().scale(0.25 + (double)RNG.nextFloat() * 0.45 * (big ? 1.6 : 1.0)).add(0.0, 0.12, 0.0);
         EMBERS.add(new FireFx.Ember(at, v, 14 + RNG.nextInt(16), 0.75F + RNG.nextFloat() * 0.25F, 0.035F).blue(blue));
      }

      BlockHitResult down = mc.level.clip(new ClipContext(at, at.add(0.0, -3.0, 0.0), Block.COLLIDER, Fluid.NONE, null));
      if (down.getType() != Type.MISS) {
         RINGS.add(new FireFx.Ring(down.getLocation().add(0.0, 0.06, 0.0), size * (big ? 2.4F : 1.8F), big ? 16 : 9));
      }

      if (big) {
         for (int i = 0; i < 14; i++) {
            Vec3 v = new Vec3(RNG.nextGaussian() * 0.02, 0.04 + (double)RNG.nextFloat() * 0.05, RNG.nextGaussian() * 0.02);
            PUFFS.add(
               new FireFx.Puff(
                  at.add(rand((double)size * 0.25)), v, size * (0.4F + RNG.nextFloat() * 0.3F), 1.015F, 0.35F, 0.02F, 0.7F, 60 + RNG.nextInt(40), 0
               )
            );
         }
      }

      shake(at, big ? 2.2F : 0.35F);
   }

   static void shake(Vec3 at, float amount) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         double d = mc.player.position().distanceTo(at);
         shake = Math.max(shake, (float)((double)amount * Mth.clamp(1.0 - d / (double)(12.0F + amount * 10.0F), 0.0, 1.0)));
      }
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            PUFFS.clear();
            EMBERS.clear();
            FLASHES.clear();
            RINGS.clear();
            SHOTS.clear();
            POSES.clear();
            CHARGING.clear();
            FULL.clear();
            Arts.clear();
            Elems.clear();
            Impact.clear();
            if (cut != null) {
               endCut();
            }
         } else if (!mc.isPaused()) {
            long now = gameTime();
            Iterator<FireFx.Puff> it = PUFFS.iterator();

            while (it.hasNext()) {
               if (!it.next().tick()) {
                  it.remove();
               }
            }

            while (PUFFS.size() > 2500) {
               PUFFS.remove(0);
            }

            EMBERS.removeIf(m -> !m.tick());
            FLASHES.removeIf(f -> ++f.age > f.life);
            RINGS.removeIf(r -> ++r.age > r.life);
            it = SHOTS.iterator();

            while (it.hasNext()) {
               FireFx.Shot s = (FireFx.Shot)it.next();
               Vec3 prev = s.pos(0.0F);
               s.age++;
               if (s.age > s.life + 4) {
                  it.remove();
               } else if (s.elem != 0) {
                  Elems.trail(s);
               } else {
                  Vec3 cur = s.pos(0.0F);
                  int n = s.big ? 8 : 5;

                  for (int i = 0; i < n; i++) {
                     Vec3 p = prev.lerp(cur, (double)((float)i / (float)n)).add(rand((double)s.size * 0.18));
                     Vec3 v = s.vel.scale(0.04).add(rand(0.02)).add(0.0, 0.012, 0.0);
                     PUFFS.add(
                        new FireFx.Puff(
                              p,
                              v,
                              s.size * (0.9F + RNG.nextFloat() * 0.5F),
                              1.03F,
                              1.0F,
                              0.1F + RNG.nextFloat() * 0.05F,
                              0.45F,
                              (s.big ? 22 : 13) + RNG.nextInt(8),
                              10
                           )
                           .blue(s.blue)
                     );
                  }

                  if (RNG.nextFloat() < (s.big ? 0.9F : 0.35F)) {
                     EMBERS.add(new FireFx.Ember(cur, s.vel.scale(0.2).add(rand(0.08)), 8 + RNG.nextInt(8), 0.9F, 0.02F).blue(s.blue));
                  }
               }
            }

            for (Entry<Integer, Long> c : CHARGING.entrySet()) {
               Entity en = mc.level.getEntity(c.getKey());
               if (en != null) {
                  boolean full = FULL.containsKey(c.getKey());

                  for (int i = 0; i < (full ? 3 : 2); i++) {
                     Vec3 target = fist(en, RNG.nextBoolean() ? 1 : -1, 0.0F);
                     Vec3 from = target.add(rand(1.0).normalize().scale(1.2 + (double)RNG.nextFloat() * 1.0));
                     int life = 7 + RNG.nextInt(3);
                     EMBERS.add(
                        new FireFx.Ember(from, target.subtract(from).scale(1.0 / (double)life), life, 0.65F + (full ? 0.3F : 0.0F), 0.0F).blue(Arts.isBlue(en))
                     );
                  }

                  if (full && now % 3L == 0L) {
                     for (int side = -1; side <= 1; side += 2) {
                        PUFFS.add(
                           new FireFx.Puff(fist(en, side, 0.0F), new Vec3(0.0, 0.04, 0.0).add(rand(0.015)), 0.16F, 1.06F, 0.9F, 0.1F, 0.3F, 9, 10)
                              .blue(Arts.isBlue(en))
                        );
                     }
                  }
               }
            }

            POSES.values().removeIf(p -> now > p[1] + p[2] + 40L);
            Arts.tick(mc, now);
            Elems.tick(mc, now);
            Impact.tick();
            shake = Math.max(0.0F, shake * 0.85F - 0.01F);
            fovKick *= 0.75F;
            flash *= 0.6F;
            if (cut != null) {
               cut.age++;
               boolean skip = mc.options.keyShift.isDown() && cut.age > 8 && mc.screen == null;
               cut.onAge();
               if (skip || cut.age >= cut.length()) {
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
         Vec3 subject = s[2];
         BlockHitResult hit = mc.level.clip(new ClipContext(subject, eye, Block.VISUAL, Fluid.NONE, mc.player));
         if (hit.getType() != Type.MISS) {
            eye = hit.getLocation().add(subject.subtract(eye).normalize().scale(0.35));
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
      if (AShaders.ready()) {
         if (e.getStage() == Stage.AFTER_LEVEL) {
            Impact.render(e.getPartialTick());
         } else if (e.getStage() == Stage.AFTER_PARTICLES) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
               boolean anything = !PUFFS.isEmpty()
                  || !EMBERS.isEmpty()
                  || !FLASHES.isEmpty()
                  || !SHOTS.isEmpty()
                  || !CHARGING.isEmpty()
                  || !RINGS.isEmpty()
                  || Arts.active()
                  || Elems.active();
               if (anything) {
                  Matrix4f view = e.getPoseStack().last().pose();
                  Matrix4f proj = e.getProjectionMatrix();
                  float partial = e.getPartialTick();
                  Vec3 cam = e.getCamera().getPosition();
                  Gfx.setCamera(view, mc.gameRenderer.getDepthFar() * 0.9F);
                  float time = (float)(mc.level.getGameTime() % 100000L) + partial;
                  ShaderInstance fl = AShaders.flame;
                  Gfx.f(fl, "Time", time);
                  Gfx.f(fl, "Bright", 1.0F);
                  Gfx.f(fl, "Length", 1.0F);
                  float sky = mc.level.getSkyDarken(partial);
                  Gfx.v3(fl, "SmokeLight", 0.28F * sky + 0.03F, 0.26F * sky + 0.025F, 0.25F * sky + 0.02F);
                  hazeCount = 0;
                  if (!RINGS.isEmpty()) {
                     Gfx.begin();

                     for (FireFx.Ring r : RINGS) {
                        float k = ((float)r.age + partial) / (float)r.life;
                        float a = 1.0F - k;
                        Gfx.ground(r.pos.x - cam.x, r.pos.y - cam.y, r.pos.z - cam.z, r.radius, 0.3F, Mth.clamp(0.15F + k * 0.85F, 0.0F, 1.0F), 0.8F, a);
                     }

                     draw(fl, 3, view, proj);
                  }

                  List<FireFx.Puff> fire = new ArrayList<>();
                  List<FireFx.Puff> steam = new ArrayList<>();
                  List<FireFx.Puff> smoke = new ArrayList<>();
                  List<FireFx.Puff> blobs = new ArrayList<>();

                  for (FireFx.Puff p : PUFFS) {
                     if (p.mode != 14 || p.palette != 0.885F) {
                        (p.mode == 7 ? steam : (p.mode == 0 ? smoke : (p.mode == 14 ? blobs : fire))).add(p);
                     }
                  }

                  if (!blobs.isEmpty()) {
                     sortBackToFront(blobs, cam);
                     Gfx.begin();

                     for (FireFx.Puff px : blobs) {
                        px.draw(cam, partial);
                     }

                     draw(fl, 14, view, proj);
                  }

                  if (!smoke.isEmpty()) {
                     sortBackToFront(smoke, cam);
                     Gfx.begin();

                     for (FireFx.Puff px : smoke) {
                        px.draw(cam, partial);
                     }

                     draw(fl, 0, view, proj);
                  }

                  List<FireFx.Puff> heads = new ArrayList<>();

                  for (FireFx.Shot s : SHOTS) {
                     if (s.elem == 0) {
                        Vec3 px = s.pos(partial);
                        float flick = 0.9F + 0.1F * Mth.sin(time * 1.7F + (float)s.serial);

                        for (int i = 0; i < (s.big ? 5 : 3); i++) {
                           FireFx.Puff h = new FireFx.Puff(
                                 px.add(
                                    (double)(Mth.sin(time * 0.9F + (float)i * 2.1F) * s.size) * 0.18,
                                    (double)(Mth.cos(time * 1.1F + (float)i) * s.size) * 0.18,
                                    (double)(Mth.sin(time * 1.3F + (float)i * 1.3F) * s.size) * 0.18
                                 ),
                                 Vec3.ZERO,
                                 s.size * (1.05F + 0.25F * (float)(i % 2)) * flick,
                                 1.0F,
                                 1.0F,
                                 0.0F,
                                 0.3F,
                                 100,
                                 10
                              )
                              .blue(s.blue);
                           h.seed = (float)((s.serial * 13 + i * 7) % 97) / 97.0F;
                           h.age = 3;
                           heads.add(h);
                        }

                        hazeAdd(px.add(0.0, (double)s.size * 0.6, 0.0), s.size * 2.2F, 1.0F);
                     }
                  }

                  if (!SHOTS.isEmpty()) {
                     Gfx.begin();

                     for (FireFx.Shot sx : SHOTS) {
                        if (sx.elem == 0) {
                           tongues(sx, cam, partial, time);
                        }
                     }

                     draw(fl, 11, view, proj);
                  }

                  fire.addAll(heads);
                  sortBackToFront(fire, cam);
                  if (!fire.isEmpty()) {
                     Gfx.begin();

                     for (FireFx.Puff px : fire) {
                        px.draw(cam, partial);
                     }

                     draw(fl, 10, view, proj);
                  }

                  if (!steam.isEmpty()) {
                     sortBackToFront(steam, cam);
                     Gfx.begin();

                     for (FireFx.Puff px : steam) {
                        px.draw(cam, partial);
                     }

                     draw(fl, 7, view, proj);
                  }

                  if (!CHARGING.isEmpty()) {
                     Gfx.begin();

                     for (Entry<Integer, Long> c : CHARGING.entrySet()) {
                        Entity en = mc.level.getEntity(c.getKey());
                        if (en != null) {
                           float k = breath(en, partial);
                           boolean full = FULL.containsKey(c.getKey());
                           boolean fp = firstPersonSelf(en);

                           for (int side = -1; side <= 1; side += 2) {
                              Vec3 px = fist(en, side, partial);
                              float hw = (fp ? 0.07F : 0.12F) + k * (fp ? 0.06F : 0.12F) + (full ? 0.05F : 0.0F);
                              float ht = (fp ? 0.22F : 0.35F) + k * (fp ? 0.2F : 0.45F) + (full ? 0.15F : 0.0F);

                              for (int i = 0; i < 2; i++) {
                                 float seed = (float)(c.getKey() * 7 + side * 3 + i * 11 & 63) / 63.0F;
                                 Gfx.upright(
                                    px.x - cam.x,
                                    px.y - cam.y - (double)ht * 0.15,
                                    px.z - cam.z,
                                    hw * (1.0F - (float)i * 0.35F),
                                    ht * (1.0F - (float)i * 0.2F),
                                    seed,
                                    Mth.clamp(0.55F + k * 0.35F, 0.0F, 1.0F),
                                    Arts.isBlue(en) ? 1.0F : 0.35F,
                                    1.0F
                                 );
                              }

                              if (!fp) {
                                 hazeAdd(px.add(0.0, (double)ht * 0.8, 0.0), 0.5F + k * 0.6F, 0.8F);
                              }
                           }
                        }
                     }

                     draw(fl, 12, view, proj);
                  }

                  if (!EMBERS.isEmpty()) {
                     Gfx.begin();

                     for (FireFx.Ember m : EMBERS) {
                        m.draw(cam, partial);
                     }

                     draw(fl, 5, view, proj);
                  }

                  Gfx.begin();

                  for (FireFx.Flash f : FLASHES) {
                     float k = ((float)f.age + partial) / (float)f.life;
                     float a = (1.0F - k) * (1.0F - k);
                     billboard(cam, f.pos, f.size * (0.8F + 0.4F * k), 0.0F, 0.5F, f.blue ? 1.0F : 0.0F, a * f.heat);
                  }

                  for (FireFx.Shot sxx : SHOTS) {
                     if (sxx.elem == 0) {
                        billboard(cam, sxx.pos(partial), sxx.size * (sxx.big ? 4.5F : 3.2F), 0.0F, 0.75F, sxx.blue ? 1.0F : 0.0F, 0.55F);
                     }
                  }

                  for (FireFx.Ember m : EMBERS) {
                     float k = ((float)m.age + partial) / (float)m.life;
                     billboard(cam, m.prev.lerp(m.pos, (double)partial), 0.07F + 0.05F * (1.0F - k), 0.0F, m.heat, m.pal(), Mth.clamp(1.3F - k, 0.0F, 1.0F));
                  }

                  for (Entry<Integer, Long> cx : CHARGING.entrySet()) {
                     Entity en = mc.level.getEntity(cx.getKey());
                     if (en != null) {
                        float k = breath(en, partial);
                        float g = (firstPersonSelf(en) ? 0.3F : 0.5F) + k * 0.5F + (FULL.containsKey(cx.getKey()) ? 0.3F : 0.0F);

                        for (int side = -1; side <= 1; side += 2) {
                           billboard(cam, fist(en, side, partial), g, 0.0F, 0.5F + 0.25F * k, Arts.isBlue(en) ? 1.0F : 0.0F, 0.18F + 0.14F * k);
                        }
                     }
                  }

                  draw(fl, 4, view, proj);
                  Arts.render(mc, cam, partial, time, view, proj);
                  Elems.render(mc, cam, partial, time, view, proj, e.getPoseStack());
                  haze(view, proj, cam, time);
               }
            }
         }
      }
   }

   private static void tongues(FireFx.Shot s, Vec3 cam, float partial, float time) {
      Vec3 head = s.pos(partial);
      Vec3 dir = s.vel.normalize();
      Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
      side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
      Vec3 up = side.cross(dir).normalize();
      float travelled = (float)Math.min(head.distanceTo(s.start), 1000.0);
      int n = 9;

      for (int k = 0; k < 3; k++) {
         float len = Math.min(travelled + s.size, s.size * (s.big ? 5.5F : (s.size < 0.3F ? 11.0F : 6.5F)) * (1.0F - 0.18F * (float)k));
         float ph = time * 0.35F + (float)k * 2.1F + (float)s.serial;
         float[] pts = new float[n * 3];
         float[] w = new float[n];
         float[] u = new float[n];

         for (int i = 0; i < n; i++) {
            float x = (float)i / ((float)n - 1.0F);
            double sway = (double)(Mth.sin(ph + x * 5.0F) * s.size) * 0.55 * (double)x;
            double lift = (double)(Mth.cos(ph * 1.3F + x * 4.0F + (float)k) * s.size) * 0.45 * (double)x + (double)(x * x * s.size) * 0.5;
            Vec3 p = head.subtract(dir.scale((double)(len * x)))
               .add(side.scale(sway + (double)((float)(k - 1) * s.size) * 0.3 * (double)x))
               .add(up.scale(lift));
            if (p.distanceTo(cam) < 0.6) {
               p = p.add(dir.scale(0.8));
            }

            pts[i * 3] = (float)(p.x - cam.x);
            pts[i * 3 + 1] = (float)(p.y - cam.y);
            pts[i * 3 + 2] = (float)(p.z - cam.z);
            w[i] = s.size * (0.95F - 0.2F * (float)k);
            u[i] = x;
         }

         Gfx.ribbon(pts, w, u, (float)((s.serial * 7 + k * 13) % 97) / 97.0F, 1.0F, s.blue ? 1.0F : 0.5F, 1.0F);
      }
   }

   static void draw(ShaderInstance sh, int mode, Matrix4f view, Matrix4f proj) {
      Gfx.f(sh, "Mode", (float)mode);
      Gfx.flush(sh, view, proj, true);
   }

   static void billboard(Vec3 cam, Vec3 p, float half, float seed, float heat, float dens, float alpha) {
      double d = p.distanceTo(cam);
      float near = (float)Mth.clamp((d - (double)half * 0.6 - 0.25) / 1.5, 0.0, 1.0);
      if (!(near <= 0.01F) && !(alpha <= 0.003F)) {
         Gfx.billboard(
            p.x - cam.x,
            p.y - cam.y,
            p.z - cam.z,
            half,
            seed * 6.283F,
            seed,
            Mth.clamp(heat, 0.0F, 1.0F),
            Mth.clamp(dens, 0.0F, 1.0F),
            Mth.clamp(alpha * near, 0.0F, 1.0F)
         );
      }
   }

   static void sortBackToFront(List<FireFx.Puff> l, Vec3 cam) {
      for (FireFx.Puff p : l) {
         p.dist = p.pos.distanceToSqr(cam);
      }

      l.sort((a, b) -> Double.compare(b.dist, a.dist));
   }

   static void hazeAdd(Vec3 p, float size, float strength) {
      if (hazeCount * 4 < HAZE.length) {
         int i = hazeCount++ * 4;
         HAZE[i] = (float)p.x;
         HAZE[i + 1] = (float)p.y;
         HAZE[i + 2] = (float)p.z;
         HAZE[i + 3] = size * strength;
      }
   }

   private static void haze(Matrix4f view, Matrix4f proj, Vec3 cam, float time) {
      if (hazeCount != 0) {
         Minecraft mc = Minecraft.getInstance();
         RenderTarget main = mc.getMainRenderTarget();
         if (copy == null || copy.width != main.width || copy.height != main.height) {
            if (copy != null) {
               copy.destroyBuffers();
            }

            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
         }

         GlStateManager._glBindFramebuffer(36008, main.frameBufferId);
         GlStateManager._glBindFramebuffer(36009, copy.frameBufferId);
         GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, 16384, 9728);
         main.bindWrite(true);
         ShaderInstance sh = AShaders.haze;
         sh.setSampler("Scene", copy.getColorTextureId());
         sh.safeGetUniform("ScreenSize").set((float)main.width, (float)main.height);
         sh.safeGetUniform("Time").set(time);
         Gfx.begin();

         for (int i = 0; i < hazeCount; i++) {
            int j = i * 4;
            Vec3 p = new Vec3((double)HAZE[j], (double)HAZE[j + 1], (double)HAZE[j + 2]);
            double d = p.distanceTo(cam);
            if (!(d < 0.8)) {
               Gfx.billboard(p.x - cam.x, p.y - cam.y, p.z - cam.z, HAZE[j + 3], 0.0F, (float)i * 0.137F % 1.0F, 0.6F, 0.0F, 1.0F);
            }
         }

         Gfx.flush(sh, view, proj, true);
      }
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
         float k = mc.player != null ? breath(mc.player, (float)e.getPartialTick()) : 0.0F;
         e.setFOV(e.getFOV() * (double)(1.0F - 0.07F * k + fovKick) * (double)Arts.fovScale((float)e.getPartialTick()));
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
   public static void onNameTag(RenderNameTagEvent e) {
      if (cut != null && e.getEntity().getId() == cut.caster) {
         e.setResult(Result.DENY);
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
      Elems.gui(e.getGuiGraphics(), e.getGuiGraphics().guiWidth(), e.getGuiGraphics().guiHeight(), e.getPartialTick());
      if (cut != null) {
         GuiGraphics g = e.getGuiGraphics();
         int w = g.guiWidth();
         int h = g.guiHeight();
         float t = (float)cut.age + e.getPartialTick();
         float k = smooth(t / 4.0F) * smooth(((float)cut.length() - t) / 5.0F);
         int bar = (int)((float)h * 0.12F * k);
         g.fill(0, 0, w, bar, -16777216);
         g.fill(0, h - bar, w, h, -16777216);
         if (flash > 0.02F) {
            g.fill(0, 0, w, h, argb(flash * 0.45F, 16774368));
         }

         float warm = cut.warm(t);
         if (warm > 0.0F) {
            g.fillGradient(0, h - h / 3, w, h, 0, argb(0.22F * warm, cut.warmColor()));
         }

         cut.titles(g, w, h, t, Minecraft.getInstance());
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

   static class Cine {
      final int caster;
      final Vec3 eye0;
      final Vec3 dir;
      final Vec3 fwd;
      final Vec3 right;
      int age;
      int serial = -1;
      int launchAge = -1;
      int endAge = -1;
      Vec3 last;
      float fov = 50.0F;
      float roll;

      Cine(Net.Fx f) {
         this.caster = f.caster;
         this.eye0 = f.a;
         this.dir = f.b.normalize();
         Vec3 flat = new Vec3(this.dir.x, 0.0, this.dir.z);
         this.fwd = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
         this.right = new Vec3(-this.fwd.z, 0.0, this.fwd.x);
      }

      int length() {
         return 38;
      }

      void onAge() {
         if (this.age == 22) {
            FireFx.flash = 1.0F;
         }
      }

      float warm(float t) {
         return Mth.clamp(t / 10.0F, 0.0F, 1.0F) * Mth.clamp((22.0F - t) / 4.0F, 0.0F, 1.0F);
      }

      int warmColor() {
         return 16730624;
      }

      Vec3 base(float partial) {
         Entity c = Minecraft.getInstance().level.getEntity(this.caster);
         return c != null ? c.getPosition(partial) : this.eye0.add(0.0, -1.62, 0.0);
      }

      Vec3[] shot(float partial) {
         Minecraft mc = Minecraft.getInstance();
         Entity c = mc.level.getEntity(this.caster);
         Vec3 base = c != null ? c.getPosition(partial) : this.eye0.add(0.0, -1.62, 0.0);
         Vec3 head = base.add(0.0, 1.55, 0.0);
         float t = (float)this.age + partial;
         if (t < 12.0F) {
            float k = t / 12.0F;
            this.fov = 40.0F;
            this.roll = 0.0F;
            Vec3 eye = head.add(this.fwd.scale(1.75 - 0.45 * (double)k)).add(this.right.scale(0.5)).add(0.0, -0.2, 0.0);
            return new Vec3[]{eye, head.add(0.0, -0.05, 0.0), head};
         } else if (t < 22.0F) {
            float k = (t - 12.0F) / 10.0F;
            this.fov = 58.0F - 6.0F * k;
            this.roll = -4.0F;
            Vec3 eye = base.add(this.right.scale(3.7)).add(this.fwd.scale(1.0 + 0.6 * (double)k)).add(0.0, 0.5, 0.0);
            return new Vec3[]{eye, base.add(0.0, 1.15, 0.0).add(this.fwd.scale(1.6)), base.add(0.0, 1.0, 0.0)};
         } else {
            float since = t - 22.0F;
            Vec3 hands = base.add(0.0, 1.3, 0.0).add(this.fwd.scale(0.6));
            Vec3 bp = this.last != null ? this.last : hands;
            if (this.endAge < 0) {
               for (FireFx.Shot s : FireFx.SHOTS) {
                  if (s.serial == this.serial) {
                     bp = s.pos(partial);
                  }
               }

               this.last = bp;
            }

            float back = this.endAge < 0 ? 0.0F : FireFx.smooth((t - (float)this.endAge) / 6.0F);
            this.fov = since < 2.0F ? 50.0F : Mth.lerp(Mth.clamp((since - 2.0F) / 6.0F, 0.0F, 1.0F), 76.0F, 64.0F) - 6.0F * back;
            this.roll = since < 2.0F ? 6.0F : 2.0F - 2.0F * back;
            Vec3 eye = bp.add(this.dir.scale(-4.2 - (double)(7.0F * back)))
               .add(this.right.scale(1.0 + 1.5 * (double)back))
               .add(0.0, 1.0 + 1.6 * (double)back, 0.0);
            return new Vec3[]{eye, bp.add(this.dir.scale((double)(3.0F * (1.0F - back)))), bp};
         }
      }

      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         if (t > 2.0F && t < 21.0F) {
            float a = Mth.clamp((t - 2.0F) / 4.0F, 0.0F, 1.0F) * Mth.clamp((21.0F - t) / 4.0F, 0.0F, 1.0F);
            g.pose().pushPose();
            g.pose().translate((float)w * 0.18F, (float)h * 0.36F, 0.0F);
            float s = 4.0F + t * 0.02F;
            g.pose().scale(s, s, 1.0F);
            g.drawString(mc.font, "火", -mc.font.width("火") / 2 + 1, 1, FireFx.argb(a, 5903360), false);
            g.drawString(mc.font, "火", -mc.font.width("火") / 2, 0, FireFx.argb(a, 16757575), false);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((float)w * 0.18F, (float)h * 0.36F + 40.0F, 0.0F);
            g.pose().scale(1.6F, 1.6F, 1.0F);
            g.drawCenteredString(mc.font, "F I R E   B L A S T", 0, 0, FireFx.argb(a, 16777215));
            g.pose().popPose();
         }

         int bar = (int)((float)h * 0.12F);
         if (t > 3.0F && t < 19.0F) {
            float a = Mth.clamp((t - 3.0F) / 3.0F, 0.0F, 1.0F) * Mth.clamp((19.0F - t) / 3.0F, 0.0F, 1.0F);
            g.drawCenteredString(mc.font, "\"Breathe in... and let the fire rise with your breath.\"", w / 2, h - bar - 14, FireFx.argb(a, 16770760));
         }

         if (t > 22.0F && t < 34.0F) {
            float a = Mth.clamp((34.0F - t) / 5.0F, 0.0F, 1.0F);
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)(h - bar - 18), 0.0F);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawCenteredString(mc.font, "HAAH!", 0, 0, FireFx.argb(a, 16765562));
            g.pose().popPose();
         }
      }
   }

   static final class Ember {
      Vec3 pos;
      Vec3 prev;
      Vec3 vel;
      final int life;
      final float heat;
      final float gravity;
      int age;
      boolean blue;
      float palette = -1.0F;

      FireFx.Ember blue(boolean b) {
         this.blue = b;
         return this;
      }

      FireFx.Ember palette(float p) {
         this.palette = p;
         return this;
      }

      float pal() {
         return this.palette > 0.0F ? this.palette : (this.blue ? 1.0F : 0.0F);
      }

      Ember(Vec3 pos, Vec3 vel, int life, float heat, float gravity) {
         this.pos = this.prev = pos;
         this.vel = vel;
         this.life = life;
         this.heat = heat;
         this.gravity = gravity;
      }

      boolean tick() {
         this.prev = this.pos;
         this.pos = this.pos.add(this.vel);
         this.vel = this.vel.scale(0.94).add(0.0, (double)(-this.gravity), 0.0);
         return ++this.age < this.life;
      }

      void draw(Vec3 cam, float partial) {
         Vec3 p = this.prev.lerp(this.pos, (double)partial);
         Vec3 tail = p.subtract(this.vel.scale(1.6));
         if (!(p.distanceTo(cam) < 0.5)) {
            float k = ((float)this.age + partial) / (float)this.life;
            float h = this.heat * (1.0F - k * 0.6F);
            float[] pts = new float[]{
               (float)(p.x - cam.x), (float)(p.y - cam.y), (float)(p.z - cam.z), (float)(tail.x - cam.x), (float)(tail.y - cam.y), (float)(tail.z - cam.z)
            };
            Gfx.ribbon(pts, new float[]{0.035F, 0.02F}, new float[]{0.0F, 1.0F}, 0.0F, Mth.clamp(h, 0.0F, 1.0F), this.pal(), Mth.clamp(1.2F - k, 0.0F, 1.0F));
         }
      }
   }

   static final class Flash {
      final Vec3 pos;
      final float size;
      final float heat;
      final int life;
      int age;
      boolean blue;

      FireFx.Flash blue(boolean b) {
         this.blue = b;
         return this;
      }

      Flash(Vec3 pos, float size, float heat, int life) {
         this.pos = pos;
         this.size = size;
         this.heat = heat;
         this.life = life;
      }
   }

   static final class Puff {
      Vec3 pos;
      Vec3 prev;
      Vec3 vel;
      float size;
      float grow;
      float heat;
      float cool;
      float dens;
      float seed;
      float drag = 0.88F;
      float palette = -1.0F;
      float gravity;
      final int life;
      final int mode;
      boolean blue;
      int age;
      double dist;

      Puff(Vec3 pos, Vec3 vel, float size, float grow, float heat, float cool, float dens, int life, int mode) {
         this.pos = this.prev = pos;
         this.vel = vel;
         this.size = size;
         this.grow = grow;
         this.heat = heat;
         this.cool = cool;
         this.dens = dens;
         this.life = life;
         this.mode = mode;
         this.seed = FireFx.RNG.nextFloat();
      }

      FireFx.Puff blue(boolean b) {
         this.blue = b;
         return this;
      }

      FireFx.Puff drag(float d) {
         this.drag = d;
         return this;
      }

      boolean tick() {
         this.prev = this.pos;
         this.pos = this.pos.add(this.vel);
         this.vel = this.vel.scale((double)this.drag).add(0.0, this.gravity > 0.0F ? (double)(-this.gravity) : 0.004 + (double)this.heat * 0.006, 0.0);
         this.size = this.size * this.grow;
         this.heat = Math.max(0.0F, this.heat - this.cool);
         return ++this.age < this.life;
      }

      void draw(Vec3 cam, float partial) {
         Vec3 p = this.prev.lerp(this.pos, (double)partial);
         float k = ((float)this.age + partial) / (float)this.life;
         float a = Mth.clamp(((float)this.age + partial) / 2.0F, 0.0F, 1.0F) * (k > 0.55F ? 1.0F - (k - 0.55F) / 0.45F : 1.0F);
         float h = Math.max(0.0F, this.heat - this.cool * partial);
         float spin = this.mode == 10
            ? Mth.sin(((float)this.age + partial) * 0.3F + this.seed * 6.0F) * 0.18F
            : (this.seed - 0.5F) * 0.05F * ((float)this.age + partial);
         double d = p.distanceTo(cam);
         float near = (float)Mth.clamp((d - (double)this.size * 0.5 - 0.2) / 1.2, 0.0, 1.0);
         if (!(near <= 0.01F) && !(a <= 0.003F)) {
            Gfx.billboard(
               p.x - cam.x,
               p.y - cam.y,
               p.z - cam.z,
               this.size,
               (this.mode == 10 ? 0.0F : this.seed * 6.283F) + spin,
               this.seed,
               Mth.clamp(h, 0.0F, 1.0F),
               this.palette > 0.0F ? this.palette : (this.blue ? 1.0F : Math.min(this.dens, 0.7F)),
               Mth.clamp(a * near, 0.0F, 1.0F)
            );
         }
      }
   }

   static final class Ring {
      final Vec3 pos;
      final float radius;
      final int life;
      int age;

      Ring(Vec3 pos, float radius, int life) {
         this.pos = pos;
         this.radius = radius;
         this.life = life;
      }
   }

   static final class Shot {
      final int serial;
      final int life;
      final int caster;
      final Vec3 start;
      final Vec3 vel;
      final float size;
      final boolean big;
      final boolean blue;
      final int elem;
      int age;

      Shot(Net.Fx f) {
         this.elem = FxKind.elem(f.flags);
         this.blue = (f.flags & 1) != 0;
         this.serial = f.other;
         this.start = f.a;
         this.vel = f.b;
         this.size = f.f;
         this.life = f.dur;
         this.caster = f.caster;
         this.big = f.f > 1.0F;
      }

      Vec3 pos(float partial) {
         float t = (float)this.age + partial;
         double drop = this.elem == 2 ? 0.035 * (double)t * (double)(t - 1.0F) / 2.0 : 0.0;
         return this.start.add(this.vel.scale((double)t)).add(0.0, -drop, 0.0);
      }
   }
}
