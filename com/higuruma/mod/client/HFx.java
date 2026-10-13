package com.higuruma.mod.client;

import com.avatar.mod.client.AShaders;
import com.avatar.mod.client.FireFx;
import com.avatar.mod.client.Gfx;
import com.avatar.mod.client.Impact;
import com.higuruma.mod.Net;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.CloudHide;
import com.jjk.mod.client.JjkPrefs;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
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
public final class HFx {
   static final float AIR = 0.81F;
   private static final List<HFx.Eff> EFFECTS = new ArrayList<>();
   private static final Map<Integer, HFx.Throw> THROWS = new HashMap<>();
   private static final List<HFx.Puff> PUFFS = new ArrayList<>();
   static final Map<Integer, float[]> GAVEL = new HashMap<>();
   static final Map<Integer, Long> SWORD = new HashMap<>();
   static final Map<Integer, Long> AMP = new HashMap<>();
   static final Map<Integer, Long> RCT = new HashMap<>();
   static HFx.Court court;
   private static HFx.Cut cut;
   private static HFx.CamEntity cam;
   private static CameraType prevType;
   private static float shake;
   private static float white;
   private static final List<double[]> BURSTS = new ArrayList<>();
   private static final Random RNG = new Random();
   private static Matrix4f lastView = new Matrix4f();
   private static Matrix4f lastProj = new Matrix4f();

   static void lightBurst(Vec3 at, float size, int life, int sparks) {
      BURSTS.add(new double[]{at.x, at.y, at.z, (double)now(), (double)life, (double)size});

      for (int i = 0; i < sparks; i++) {
         Vec3 v = rand(1.0).normalize().scale(0.15 + (double)RNG.nextFloat() * 0.35 * (double)size);
         HFx.Puff p = new HFx.Puff(at, v, 0.06F + RNG.nextFloat() * 0.06F, 12 + RNG.nextInt(14), 1.0F);
         p.white = true;
         PUFFS.add(p);
      }
   }

   static void whiteOut(Vec3 at, float k, double range) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.position().distanceTo(at) < range) {
         white = Math.max(white, k);
      }
   }

   private HFx() {
   }

   public static boolean cutsceneActive() {
      return cut != null;
   }

   static long now() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0L : mc.level.getGameTime();
   }

   static boolean on(Map<Integer, Long> m, Entity e) {
      Long t = m.get(e.getId());
      return t != null && t > now();
   }

   static float gavelSize(Entity e, float partial) {
      for (HFx.Throw t : THROWS.values()) {
         if (t.owner == e.getId()) {
            return 0.0F;
         }
      }

      float[] g = GAVEL.get(e.getId());
      if (g == null) {
         return 1.0F;
      } else {
         float tx = (float)now() - g[1] + partial;
         if (tx > g[2]) {
            return 1.0F;
         } else {
            float grow = g[0] > 3.0F ? Math.max(4.0F, g[2] - 10.0F) : 2.0F;
            float in = Mth.clamp(tx / grow, 0.0F, 1.0F);
            float out = Mth.clamp((g[2] - tx) / 3.0F, 0.0F, 1.0F);
            in = in * in * (3.0F - 2.0F * in);
            return Mth.lerp(Math.min(in, out), 1.0F, g[0]);
         }
      }
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

   public static void receive(Net.Fx f) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         long now = now();
         boolean me = f.caster == mc.player.getId();
         boolean meDef = f.other == mc.player.getId();
         switch (f.kind) {
            case 0:
               GAVEL.put(f.caster, new float[]{f.f, (float)now, (float)f.dur});
               EFFECTS.add(new HFx.Eff(f, now, f.dur));
               break;
            case 1:
               if (f.dur == 0) {
                  THROWS.remove(f.other);
               } else {
                  HFx.Throw t = THROWS.computeIfAbsent(f.other, k -> new HFx.Throw(f.caster, f.a));
                  t.prev = t.pos;
                  t.pos = f.a;
                  t.vel = f.b;
                  t.stamp = now;
               }
               break;
            case 2:
               GAVEL.put(f.caster, new float[]{4.5F, (float)now, (float)(f.dur + 6)});
               EFFECTS.add(new HFx.Eff(f, now, f.dur + 6));
               break;
            case 3:
               EFFECTS.add(new HFx.Eff(f, now, 24));

               for (int i = 0; i < 30; i++) {
                  Vec3 v = rand(1.0).multiply(1.0, 0.3, 1.0).normalize().scale(0.15 + (double)RNG.nextFloat() * 0.3).add(0.0, 0.05, 0.0);
                  PUFFS.add(new HFx.Puff(f.a.add(0.0, 0.3, 0.0), v, 0.6F + RNG.nextFloat() * 0.6F, 30 + RNG.nextInt(20), 0.0F));
               }

               double d = mc.player.position().distanceTo(f.a);
               shake = Math.max(shake, (float)(2.5 * Mth.clamp(1.0 - d / 24.0, 0.0, 1.0)));
               if (me || d < 14.0) {
                  float[] var8 = screen(f.a);
               }
               break;
            case 4:
               if (f.f > 0.5F) {
                  EFFECTS.add(new HFx.Eff(f, now, 8));
               } else if (f.dur > 0) {
                  AMP.put(f.caster, now + (long)f.dur);
               } else {
                  AMP.remove(f.caster);
               }
               break;
            case 5:
               RCT.put(f.caster, now + (long)f.dur);
               break;
            case 6:
               court = new HFx.Court(f, now);
               Entity c = mc.level.getEntity(f.caster);
               Vec3 at = c != null ? c.position().add(0.0, 1.2, 0.0) : f.a;
               lightBurst(at, 6.0F, 30, 90);
               EFFECTS.add(new HFx.Eff(new Net.Fx((byte)6, at, Vec3.ZERO, 1.0F, f.caster, -1, 34), now, 34));
               whiteOut(at, 1.0F, 40.0);
               if ((me || meDef) && !JjkPrefs.skipCutscenes() && cut == null) {
                  cut = new HFx.Cut(court);
                  prevType = mc.options.getCameraType();
                  mc.options.setCameraType(CameraType.FIRST_PERSON);
                  cam = new HFx.CamEntity(mc.level);
               }
               break;
            case 7:
               if (court != null) {
                  court.phase = (int)f.f;
               }
               break;
            case 8:
               if (court != null) {
                  court.verdict = (int)f.f;
                  court.verdictAt = now;
                  if (court.verdict > 0 && mc.player.position().distanceTo(court.center) < 40.0) {
                     float[] c = screen(court.judge(0.0F));
                     Impact.hit(0, 0, 2, 0, c[0], c[1], true);
                     Impact.hit(court.verdict == 2 ? 6 : 5, 2, 4, 8, c[0], c[1], true);
                     shake = Math.max(shake, court.verdict == 2 ? 2.5F : 1.4F);
                  }
               }
               break;
            case 9:
               if (court != null) {
                  court.endAt = now;
               }
               break;
            case 10:
               if (f.dur > 0) {
                  SWORD.put(f.caster, now + (long)f.dur);
                  Entity c = mc.level.getEntity(f.caster);
                  Vec3 at = c != null ? c.position() : f.a;
                  EFFECTS.add(new HFx.Eff(new Net.Fx((byte)10, at, Vec3.ZERO, 1.0F, f.caster, -1, 40), now, 40));
                  lightBurst(at.add(0.0, 1.2, 0.0), 3.0F, 20, 40);
                  whiteOut(at, me ? 0.95F : 0.6F, 30.0);
               } else {
                  SWORD.remove(f.caster);
               }
               break;
            case 11:
               EFFECTS.add(new HFx.Eff(f, now, 16));
               lightBurst(f.a, f.f > 0.5F ? 3.5F : 1.6F, f.f > 0.5F ? 18 : 10, f.f > 0.5F ? 60 : 24);
               whiteOut(f.a, f.f > 0.5F ? 0.9F : 0.35F, 20.0);
               if (f.other >= 0 && f.f > 0.5F && (me || meDef || mc.player.position().distanceTo(f.a) < 30.0)) {
                  float[] cx = screen(f.a);
                  Impact.hit(0, 0, 2, 0, cx[0], cx[1], true);
                  Impact.hit(6, 2, 3, 0, cx[0], cx[1], true);
                  Impact.hit(5, 5, 4, 8, cx[0], cx[1], true);
                  shake = Math.max(shake, 3.0F);
               }
               break;
            case 12:
               EFFECTS.add(new HFx.Eff(f, now, 8));

               for (int i = 0; i < 6; i++) {
                  PUFFS.add(new HFx.Puff(f.a, rand(0.12).add(f.b.scale(0.08)), 0.25F, 10, 0.6F));
               }

               if (me || meDef) {
                  shake = Math.max(shake, 0.5F * f.f);
               }
               break;
            case 13:
               FireFx.POSES.put(f.caster, new long[]{(long)f.f, now, (long)f.dur});
         }
      }
   }

   static Vec3 rand(double s) {
      return new Vec3(RNG.nextGaussian() * s, RNG.nextGaussian() * s, RNG.nextGaussian() * s);
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            EFFECTS.clear();
            THROWS.clear();
            PUFFS.clear();
            GAVEL.clear();
            SWORD.clear();
            AMP.clear();
            RCT.clear();
            court = null;
            if (cut != null) {
               endCut();
            }
         } else {
            long now = now();
            EFFECTS.removeIf(x -> now - x.start > (long)(x.life + 2));
            THROWS.values().removeIf(t -> now - t.stamp > 10L);
            PUFFS.removeIf(px -> !px.tick());
            if (court != null && court.endAt >= 0L && now - court.endAt > 20L) {
               court = null;
            }

            shake = Math.max(0.0F, shake * 0.85F - 0.01F);
            white = Math.max(0.0F, white * 0.88F - 0.01F);
            BURSTS.removeIf(b -> (double)now - b[3] > b[4]);
            if (cut != null) {
               cut.age++;
               boolean skip = mc.options.keyShift.isDown() && cut.age > 10 && mc.screen == null;
               if (skip || cut.age >= 70 || court == null) {
                  endCut();
               }
            }

            if (court != null && court.alpha(0.0F) > 0.3F && now % 2L == 0L) {
               Vec3 p = court.center.add(((double)RNG.nextFloat() - 0.5) * 18.0, (double)(RNG.nextFloat() * 2.0F), ((double)RNG.nextFloat() - 0.5) * 18.0);
               PUFFS.add(new HFx.Puff(p, new Vec3(0.0, 0.02, 0.0), 0.05F, 60, 1.0F));
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
            boolean inCourt = court != null && court.alpha(partial) > 0.55F && mc.player != null && mc.player.position().distanceTo(court.center) < 24.0;
            CloudHide.set("court", inCourt);
            if (inCourt) {
               RenderTarget main = mc.getMainRenderTarget();
               main.bindWrite(false);
               RenderSystem.clearColor(0.012F, 0.009F, 0.006F, 1.0F);
               RenderSystem.clear(16640, Minecraft.ON_OSX);
               EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
               BufferSource pb = mc.renderBuffers().bufferSource();

               for (Entity en : mc.level
                  .getEntities((Entity)null, new AABB(court.center, court.center).inflate(20.0), xx -> xx instanceof LivingEntity && xx.isAlive())) {
                  if (en != mc.getCameraEntity() || !mc.options.getCameraType().isFirstPerson()) {
                     Vec3 ep = en.getPosition(partial);
                     erd.render(
                        en,
                        ep.x - cam.x,
                        ep.y - cam.y,
                        ep.z - cam.z,
                        en.getViewYRot(partial),
                        partial,
                        e.getPoseStack(),
                        pb,
                        erd.getPackedLightCoords(en, partial)
                     );
                  }
               }

               pb.endBatch();
            }

            ShaderInstance fl = AShaders.flame;
            Gfx.setCamera(view, mc.gameRenderer.getDepthFar() * 0.9F);
            Gfx.f(fl, "Time", time);
            Gfx.f(fl, "Bright", 1.0F);
            Gfx.f(fl, "Length", 1.0F);
            float sky = mc.level.getSkyDarken(partial);
            Gfx.v3(fl, "SmokeLight", 0.25F * sky + 0.04F, 0.23F * sky + 0.03F, 0.2F * sky + 0.02F);
            BufferSource buf = mc.renderBuffers().bufferSource();
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            VertexConsumer solid = buf.getBuffer(Cubes.SOLID);
            boolean any = false;

            for (HFx.Throw t : THROWS.values()) {
               Vec3 p = t.prev.lerp(t.pos, (double)Mth.clamp((float)(now - t.stamp) + partial, 0.0F, 1.0F));
               ps.pushPose();
               ps.translate(p.x, p.y, p.z);
               float yaw = (float)Math.atan2(t.vel.x, t.vel.z);
               ps.mulPose(Axis.YP.rotation(yaw));
               ps.mulPose(Axis.XP.rotationDegrees(time * 55.0F));
               ps.translate(0.0, -0.35, 0.0);
               Cubes.gavel(ps, solid, 1.4F, light(mc, p));
               ps.popPose();
               any = true;
            }

            if (mc.options.getCameraType().isFirstPerson() && cut == null && ClientSorcerers.mine() == Sorcerer.HIGURUMA && mc.getCameraEntity() == mc.player) {
               float sz = gavelSize(mc.player, partial);
               if (sz > 1.05F && !giant(mc.player) || on(SWORD, mc.player)) {
                  Vec3 look = mc.player.getViewVector(partial);
                  Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                  Vec3 up = right.cross(look).normalize();
                  float swing = swingK(mc.player, partial);
                  Vec3 p = mc.player.getEyePosition(partial).add(look.scale(0.75)).add(right.scale(0.42)).add(up.scale(-0.5));
                  ps.pushPose();
                  ps.translate(p.x, p.y, p.z);
                  ps.mulPose(Axis.YP.rotationDegrees(-mc.player.getViewYRot(partial)));
                  ps.mulPose(Axis.XP.rotationDegrees(mc.player.getViewXRot(partial)));
                  ps.mulPose(Axis.XP.rotationDegrees(25.0F + 80.0F * swing));
                  ps.mulPose(Axis.ZP.rotationDegrees(-15.0F));
                  ps.translate(0.0, -0.15, 0.0);
                  if (on(SWORD, mc.player)) {
                     Cubes.sword(ps, solid, buf.getBuffer(Cubes.GLOW), 0.85F, 15728880);
                  } else {
                     Cubes.gavel(ps, solid, 0.75F * Math.min(sz, 1.8F), 15728880);
                  }

                  ps.popPose();
                  any = true;
               }
            }

            for (HFx.Eff x : EFFECTS) {
               if (x.f.kind == 2) {
                  Entity c = mc.level.getEntity(x.f.caster);
                  float t = (float)(now - x.start) + partial;
                  float k = Mth.clamp(t / (float)x.f.dur, 0.0F, 1.0F);
                  if (c != null && c == mc.player && mc.options.getCameraType().isFirstPerson()) {
                     Vec3 fwd = new Vec3(x.f.b.x, 0.0, x.f.b.z).normalize();
                     Vec3 right = fwd.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                     Vec3 base = c.getPosition(partial).add(0.0, 1.15, 0.0).add(right.scale(0.75)).add(fwd.scale(0.35));
                     ps.pushPose();
                     ps.translate(base.x, base.y, base.z);
                     ps.mulPose(Axis.YP.rotation((float)Math.atan2(x.f.b.x, x.f.b.z)));
                     float lift = Mth.clamp(k / 0.7F, 0.0F, 1.0F);
                     float smash = Mth.clamp((k - 0.7F) / 0.3F, 0.0F, 1.0F);
                     float ang = smash > 0.0F ? Mth.lerp(smash * smash, -55.0F, 100.0F) : Mth.lerp(lift * (2.0F - lift), 25.0F, -55.0F);
                     ps.mulPose(Axis.XP.rotationDegrees(ang));
                     Cubes.gavel(ps, solid, Math.max(1.0F, gavelSize(c, partial)), light(mc, base));
                     ps.popPose();
                     any = true;
                  }
               }
            }

            if (court != null) {
               any |= court.renderSolid(mc, ps, buf, partial, time);
            }

            ps.popPose();
            if (any) {
               buf.endBatch(Cubes.SOLID);
               buf.endBatch(Cubes.GLOW);
            }

            Gfx.begin();
            int n = 0;

            for (HFx.Puff p : PUFFS) {
               if (p.gold < 0.5F) {
                  n += p.draw(cam, partial);
               }
            }

            flushOr(fl, 0, view, proj, n);
            Gfx.begin();
            n = 0;

            for (HFx.Eff xx : EFFECTS) {
               n += xx.ribbons(mc, cam, partial, now);
            }

            for (Entry<Integer, Long> a : AMP.entrySet()) {
               Entity c = mc.level.getEntity(a.getKey());
               if (c != null && a.getValue() > now && (c != mc.player || !mc.options.getCameraType().isFirstPerson() || cut != null)) {
                  Vec3 o = c.getPosition(partial);

                  for (int k = 0; k < 4; k++) {
                     ring(
                        cam,
                        o.add(0.0, 0.3 + (double)k * 0.45, 0.0),
                        0.75F,
                        time * (k % 2 == 0 ? 0.3F : -0.25F) + (float)k,
                        0.25F * ((float)k - 1.5F),
                        0.09F,
                        0.81F,
                        0.55F
                     );
                  }

                  n++;
               }
            }

            flushOr(fl, 11, view, proj, n);
            Gfx.begin();

            for (HFx.Puff px : PUFFS) {
               if (px.gold >= 0.5F) {
                  px.draw(cam, partial);
               }
            }

            for (HFx.Eff xx : EFFECTS) {
               float t = (float)(now - xx.start) + partial;
               float ax = Mth.clamp(1.0F - t / (float)xx.life, 0.0F, 1.0F);
               if (xx.f.kind == 12) {
                  glow(cam, xx.f.a, 0.7F * xx.f.f, 0.75F, 0.0F, ax);
               } else if (xx.f.kind == 3 && t < 8.0F) {
                  glow(cam, xx.f.a.add(0.0, 0.5, 0.0), xx.f.f * 1.4F, 0.6F, 0.0F, ax);
                  Gfx.ground(xx.f.a.x - cam.x, xx.f.a.y + 0.05 - cam.y, xx.f.a.z - cam.z, xx.f.f * (0.6F + t * 0.18F), 0.0F, 0.5F, 0.3F, ax);
               } else if (xx.f.kind == 4) {
                  Entity c = mc.level.getEntity(xx.f.caster);
                  if (c != null) {
                     glow(cam, c.getPosition(partial).add(0.0, 1.0, 0.0), 1.6F, 0.55F, 0.0F, ax * 0.8F);
                  }
               } else if (xx.f.kind == 11 && t < 6.0F) {
                  glow(cam, xx.f.a, 2.2F, 0.4F, 0.0F, ax);
               }
            }

            for (Entry<Integer, Long> r : RCT.entrySet()) {
               Entity c = mc.level.getEntity(r.getKey());
               if (c != null && r.getValue() > now) {
                  glow(cam, c.getPosition(partial).add(0.0, 1.0, 0.0), 1.4F + 0.2F * Mth.sin(time * 0.4F), 0.9F, 0.81F, 0.45F);
               }
            }

            for (Entry<Integer, Long> ax : AMP.entrySet()) {
               Entity c = mc.level.getEntity(ax.getKey());
               if (c != null && ax.getValue() > now && (c != mc.player || !mc.options.getCameraType().isFirstPerson() || cut != null)) {
                  glow(cam, c.getPosition(partial).add(0.0, 1.0, 0.0), 1.3F, 0.5F, 0.0F, 0.3F);
               }
            }

            if (court != null) {
               court.renderGlow(cam, partial, time);
            }

            for (double[] b : BURSTS) {
               float t = (float)((double)now - b[3]) + partial;
               float k = Mth.clamp(t / (float)b[4], 0.0F, 1.0F);
               Vec3 at = new Vec3(b[0], b[1], b[2]);
               float sz = (float)b[5];
               glow(cam, at, sz * (0.6F + 1.4F * k), 1.0F, 0.81F, (1.0F - k) * (1.0F - k));
               glow(cam, at, sz * 0.4F * (1.0F - k), 1.0F, 0.81F, 1.0F - k);
            }

            for (Entry<Integer, Long> sw : SWORD.entrySet()) {
               Entity c = mc.level.getEntity(sw.getKey());
               if (c != null && sw.getValue() > now) {
                  Vec3 look = c.getViewVector(partial);
                  Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
                  side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
                  boolean fp = c == mc.player && mc.options.getCameraType().isFirstPerson() && cut == null;
                  Vec3 hand = fp
                     ? c.getEyePosition(partial).add(look.scale(0.75)).add(side.scale(0.42)).add(0.0, -0.45, 0.0)
                     : c.getPosition(partial).add(0.0, 0.95, 0.0).add(side.scale(0.38)).add(look.scale(0.3));
                  Vec3 bladeDir = fp ? look.add(0.0, 0.6, 0.0).normalize() : look.add(0.0, 0.35, 0.0).normalize();
                  float pulse = 0.8F + 0.2F * Mth.sin(time * 0.4F);

                  for (int i = 2; i <= 6; i++) {
                     Vec3 pxx = hand.add(bladeDir.scale((double)i * (fp ? 0.12 : 0.28)));
                     glow(cam, pxx, (fp ? 0.08F : 0.18F) * pulse, 1.0F, 0.81F, 0.12F);
                  }
               }
            }

            for (HFx.Eff xxx : EFFECTS) {
               float t = (float)(now - xxx.start) + partial;
               float k = Mth.clamp(t / (float)xxx.life, 0.0F, 1.0F);
               if (xxx.f.kind == 10 && xxx.f.f > 0.5F) {
                  Vec3 base = xxx.f.a;

                  for (int i = 0; i < 12; i++) {
                     glow(cam, base.add(0.0, (double)i * 1.6, 0.0), 1.6F, 1.0F, 0.81F, 0.55F * (1.0F - k));
                  }
               } else if (xxx.f.kind == 6 && xxx.f.f > 0.5F) {
                  glow(cam, xxx.f.a, 2.0F + 22.0F * k, 1.0F, 0.81F, 0.8F * (1.0F - k));
                  glow(cam, xxx.f.a, 3.0F * (1.0F - k), 1.0F, 0.81F, 1.0F - k);
               }
            }

            Gfx.f(fl, "Mode", 4.0F);
            Gfx.flush(fl, view, proj, true);
         }
      }
   }

   private static void flushOr(ShaderInstance sh, int mode, Matrix4f view, Matrix4f proj, int n) {
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

   static void ribbon(Vec3 cam, List<Vec3> pts, float width, float pal, float alpha, float seed) {
      int n = pts.size();
      if (n >= 2) {
         float[] p = new float[n * 3];
         float[] w = new float[n];
         float[] u = new float[n];

         for (int i = 0; i < n; i++) {
            Vec3 v = pts.get(i);
            p[i * 3] = (float)(v.x - cam.x);
            p[i * 3 + 1] = (float)(v.y - cam.y);
            p[i * 3 + 2] = (float)(v.z - cam.z);
            w[i] = width;
            u[i] = (float)i / ((float)n - 1.0F);
         }

         Gfx.ribbon(p, w, u, seed, 1.0F, pal, Mth.clamp(alpha, 0.0F, 1.0F));
      }
   }

   static void ring(Vec3 cam, Vec3 c, float radius, float spin, float tilt, float width, float pal, float alpha) {
      List<Vec3> pts = new ArrayList<>();

      for (int i = 0; i < 16; i++) {
         float a = spin + (float)i / 15.0F * (float) (Math.PI * 2) * 0.8F;
         double x = (double)(Mth.cos(a) * radius);
         double z = (double)(Mth.sin(a) * radius);
         pts.add(c.add(x, z * (double)Mth.sin(tilt), z * (double)Mth.cos(tilt)));
      }

      Collections.reverse(pts);
      ribbon(cam, pts, width, pal, alpha, tilt * 0.17F % 1.0F + 0.5F);
   }

   static int light(Minecraft mc, Vec3 p) {
      return LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
   }

   static boolean giant(Entity e) {
      for (HFx.Eff x : EFFECTS) {
         if (x.f.kind == 2 && x.f.caster == e.getId()) {
            return true;
         }
      }

      return false;
   }

   static float swingK(Entity e, float partial) {
      for (HFx.Eff x : EFFECTS) {
         if (x.f.kind == 0 && x.f.caster == e.getId() || x.f.kind == 11 && x.f.caster == e.getId()) {
            float t = ((float)(now() - x.start) + partial) / (float)Math.max(1, x.life);
            return t < 1.0F ? Mth.sin(t * (float) Math.PI) : 0.0F;
         }
      }

      return 0.0F;
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

   @SubscribeEvent
   public static void onFogColor(ComputeFogColor e) {
      float k = courtView((float)e.getPartialTick());
      if (k > 0.0F) {
         e.setRed(Mth.lerp(k, e.getRed(), 0.09F));
         e.setGreen(Mth.lerp(k, e.getGreen(), 0.06F));
         e.setBlue(Mth.lerp(k, e.getBlue(), 0.02F));
      }
   }

   @SubscribeEvent
   public static void onFog(RenderFog e) {
      float k = courtView((float)e.getPartialTick());
      if (k > 0.0F) {
         e.setNearPlaneDistance(Mth.lerp(k, e.getNearPlaneDistance(), 2.0F));
         e.setFarPlaneDistance(Mth.lerp(k, e.getFarPlaneDistance(), 26.0F));
         e.setCanceled(true);
      }
   }

   static float courtView(float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (court != null && mc.level != null) {
         return mc.gameRenderer.getMainCamera().getPosition().distanceTo(court.center) < 22.0 ? court.alpha(partial) : 0.0F;
      } else {
         return 0.0F;
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

      Minecraft mc = Minecraft.getInstance();
      if (e.getHand() == InteractionHand.MAIN_HAND && mc.player != null && on(SWORD, mc.player)) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      GuiGraphics g = e.getGuiGraphics();
      int w = g.guiWidth();
      int h = g.guiHeight();
      Minecraft mc = Minecraft.getInstance();
      float partial = e.getPartialTick();
      if (white > 0.01F) {
         g.fill(0, 0, w, h, argb(Math.min(1.0F, white), 16777215));
      }

      float k = courtView(partial);
      if (k > 0.0F) {
         g.fillGradient(0, 0, w, h / 3, argb(0.45F * k, 656898), 0);
         g.fillGradient(0, h - h / 3, w, h, 0, argb(0.35F * k, 2759174));
      }

      if (cut != null) {
         float t = (float)cut.age + partial;
         float bk = smooth(t / 5.0F) * smooth((70.0F - t) / 6.0F);
         int bar = (int)((float)h * 0.12F * bk);
         g.fill(0, 0, w, bar, -16777216);
         g.fill(0, h - bar, w, h, -16777216);
         title(g, mc, w, h, t, 4.0F, 24.0F, "領域展開", "DOMAIN EXPANSION", 15254634, 3.5F);
         title(g, mc, w, h, t, 46.0F, 70.0F, "誅伏賜死", "DEADLY SENTENCING", 15915402, 4.5F);
         if (t > 26.0F && t < 44.0F) {
            float a = Mth.clamp((t - 26.0F) / 3.0F, 0.0F, 1.0F) * Mth.clamp((44.0F - t) / 3.0F, 0.0F, 1.0F);
            g.drawCenteredString(mc.font, "\"In this court, violence is forbidden.\"", w / 2, h - bar - 14, argb(a, 16049864));
         }
      }

      if (court != null && court.verdict >= 0 && cut == null && courtView(partial) > 0.0F) {
         float t = (float)(now() - court.verdictAt) + partial;
         if (t < 60.0F) {
            float a = Mth.clamp(t / 3.0F, 0.0F, 1.0F) * Mth.clamp((60.0F - t) / 8.0F, 0.0F, 1.0F);
            String kanji = court.verdict == 0 ? "無罪" : (court.verdict == 1 ? "有罪" : "死刑");
            String en = court.verdict == 0 ? "NOT GUILTY" : (court.verdict == 1 ? "GUILTY  -  CONFISCATION" : "DEATH PENALTY");
            int col = court.verdict == 0 ? 15921384 : (court.verdict == 1 ? 15254634 : 14688288);
            float s = 6.0F - Math.min(t, 6.0F) * 0.25F;
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)h * 0.3F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.drawString(mc.font, kanji, -mc.font.width(kanji) / 2 + 1, 1, argb(a, 1050628), false);
            g.drawString(mc.font, kanji, -mc.font.width(kanji) / 2, 0, argb(a, col), false);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)h * 0.3F + 48.0F, 0.0F);
            g.pose().scale(1.8F, 1.8F, 1.0F);
            g.drawCenteredString(mc.font, en, 0, 0, argb(a, 16777215));
            g.pose().popPose();
         }
      }

      if (court != null && court.phase == 2 && court.verdict < 0 && courtView(partial) > 0.0F) {
         g.drawCenteredString(mc.font, "The court is deliberating...", w / 2, h / 3, argb(0.8F, 15254634));
      }
   }

   private static void title(GuiGraphics g, Minecraft mc, int w, int h, float t, float t0, float t1, String kanji, String en, int col, float scale) {
      if (!(t <= t0) && !(t >= t1)) {
         float a = Mth.clamp((t - t0) / 4.0F, 0.0F, 1.0F) * Mth.clamp((t1 - t) / 4.0F, 0.0F, 1.0F);
         g.pose().pushPose();
         g.pose().translate((float)w * 0.22F, (float)h * 0.36F, 0.0F);
         float s = scale + (t - t0) * 0.02F;
         g.pose().scale(s, s, 1.0F);
         g.drawString(mc.font, kanji, -mc.font.width(kanji) / 2 + 1, 1, argb(a, 1050628), false);
         g.drawString(mc.font, kanji, -mc.font.width(kanji) / 2, 0, argb(a, col), false);
         g.pose().popPose();
         g.pose().pushPose();
         g.pose().translate((float)w * 0.22F, (float)h * 0.36F + scale * 10.0F + 4.0F, 0.0F);
         g.pose().scale(1.5F, 1.5F, 1.0F);
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

   static final class Court {
      final Vec3 center;
      final Vec3 dir;
      final Vec3 right;
      final int pros;
      final int def;
      final long start;
      int phase;
      int verdict = -1;
      long verdictAt;
      long endAt = -1L;

      Court(Net.Fx f, long now) {
         this.center = f.a;
         this.dir = f.b.normalize();
         this.right = this.dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
         this.pros = f.caster;
         this.def = f.other;
         this.start = now;
      }

      float alpha(float partial) {
         long now = HFx.now();
         float t = (float)(now - this.start) + partial;
         float in = Mth.clamp((t - 14.0F) / 24.0F, 0.0F, 1.0F);
         float out = this.endAt >= 0L ? Mth.clamp(1.0F - ((float)(now - this.endAt) + partial) / 18.0F, 0.0F, 1.0F) : 1.0F;
         return in * out;
      }

      Vec3 stand(boolean defendant) {
         return this.center.add(this.dir.scale(defendant ? 4.5 : -4.5));
      }

      Vec3 judge(float partial) {
         float a = this.alpha(partial);
         return this.center.add(this.right.scale(8.5)).add(0.0, 1.0 + 6.5 * (double)a, 0.0);
      }

      private float[] floorCol(Vec3 p, float a) {
         float lit = 0.0F;

         for (int s = 0; s < 2; s++) {
            double d = p.distanceTo(this.stand(s == 1));
            lit += (float)Math.max(0.0, 1.0 - d / 2.6);
         }

         double dj = p.distanceTo(this.center.add(this.right.scale(7.5)));
         lit += (float)Math.max(0.0, 1.0 - dj / 5.0) * 0.35F;
         lit = Math.min(1.0F, lit);
         return new float[]{0.07F + 0.75F * lit, 0.08F + 0.52F * lit, 0.11F + 0.18F * lit};
      }

      boolean renderSolid(Minecraft mc, PoseStack ps, MultiBufferSource buf, float partial, float time) {
         float a = this.alpha(partial);
         if (a <= 0.02F) {
            return false;
         } else {
            VertexConsumer vc = buf.getBuffer(Cubes.SOLID);
            int lit = 15728880;
            int dim = LightTexture.pack(8, 8);
            float rise = (1.0F - a) * 1.5F;
            Vec3 c0 = this.center.add(0.0, 0.02 - (double)rise * 0.2, 0.0);
            int seg = 56;
            double A = 11.5;
            double B = 9.5;
            float[] rings = new float[]{0.0F, 0.55F, 0.6F, 0.86F, 0.9F, 1.0F};

            for (int r = 0; r < rings.length - 1; r++) {
               for (int k = 0; k < seg; k++) {
                  Vec3[] q = new Vec3[4];
                  double[][] uv = new double[][]{
                     {(double)rings[r], (double)k},
                     {(double)rings[r + 1], (double)k},
                     {(double)rings[r + 1], (double)(k + 1)},
                     {(double)rings[r], (double)(k + 1)}
                  };

                  for (int m = 0; m < 4; m++) {
                     double ang = uv[m][1] / (double)seg * Math.PI * 2.0;
                     q[m] = c0.add(this.dir.scale(Math.cos(ang) * A * uv[m][0])).add(this.right.scale(Math.sin(ang) * B * uv[m][0]));
                  }

                  boolean trim = r == 1 || r == 3;
                  float[] col = trim ? new float[]{0.55F, 0.4F, 0.16F} : this.floorCol(q[0].lerp(q[2], 0.5), a);
                  if (r == 4) {
                     col = new float[]{0.05F, 0.05F, 0.07F};
                  }

                  Cubes.quad(ps, vc, q, col, trim ? lit : lit, 0.0F, 1.0F, 0.0F);
               }
            }

            for (int k = 0; k < seg; k++) {
               double ang = ((double)k + 0.5) / (double)seg * Math.PI * 2.0;
               Vec3 p = c0.add(this.dir.scale(Math.cos(ang) * A)).add(this.right.scale(Math.sin(ang) * B));
               Vec3 out = p.subtract(c0).normalize();
               float yaw = (float)Math.atan2(out.x, out.z);
               ps.pushPose();
               ps.translate(p.x, p.y, p.z);
               ps.mulPose(Axis.YP.rotation(yaw));
               Cubes.box(ps, vc, -0.65F, -0.2F, -0.1F, 0.65F, 0.25F, 0.35F, 0.12F, 0.1F, 0.1F, dim);
               ps.popPose();
               Vec3 w = c0.add(this.dir.scale(Math.cos(ang) * (A + 4.5))).add(this.right.scale(Math.sin(ang) * (B + 4.5)));
               ps.pushPose();
               ps.translate(w.x, w.y, w.z);
               ps.mulPose(Axis.YP.rotation(yaw));
               float h = 11.0F * a;
               Cubes.box(ps, vc, -0.95F, -0.5F, 0.0F, 0.95F, h, 0.6F, 0.09F, 0.07F, 0.06F, dim);
               if (k % 4 == 0) {
                  Cubes.box(ps, vc, -0.6F, -0.5F, -0.5F, 0.6F, h + 0.6F, 0.1F, 0.16F, 0.12F, 0.09F, dim);
                  Cubes.box(ps, vc, -0.75F, h - 0.4F, -0.65F, 0.75F, h, 0.15F, 0.5F, 0.37F, 0.15F, dim);
               }

               ps.popPose();
            }

            for (int st = 0; st < 2; st++) {
               Vec3 at = this.stand(st == 1).add(0.0, (double)(-rise), 0.0);
               Vec3 face = st == 1 ? this.dir.scale(-1.0) : this.dir;
               float yaw = (float)Math.atan2(face.x, face.z);
               ps.pushPose();
               ps.translate(at.x, at.y, at.z);
               ps.mulPose(Axis.YP.rotation(yaw));
               float R = 1.05F;
               int posts = 14;

               for (int i = 0; i <= posts; i++) {
                  float ang = Mth.lerp((float)i / (float)posts, -2.35F, 2.35F);
                  float x = Mth.sin(ang) * R;
                  float z = Mth.cos(ang) * R;
                  Cubes.cbox(ps, vc, x, 0.08F, z, 0.07F, 0.08F, 0.07F, 0.55F, 0.36F, 0.16F, lit);
                  Cubes.cbox(ps, vc, x, 0.42F, z, 0.035F, 0.28F, 0.035F, 0.62F, 0.42F, 0.19F, lit);
                  Cubes.cbox(ps, vc, x, 0.62F, z, 0.06F, 0.07F, 0.06F, 0.66F, 0.45F, 0.2F, lit);
                  Cubes.cbox(ps, vc, x, 0.82F, z, 0.04F, 0.12F, 0.04F, 0.62F, 0.42F, 0.19F, lit);
               }

               for (int i = 0; i < posts; i++) {
                  float a1 = Mth.lerp((float)i / (float)posts, -2.35F, 2.35F);
                  float a2 = Mth.lerp((float)(i + 1) / (float)posts, -2.35F, 2.35F);
                  float am = (a1 + a2) / 2.0F;
                  float half = R * Mth.sin((a2 - a1) / 2.0F) + 0.03F;
                  ps.pushPose();
                  ps.translate(Mth.sin(am) * R, 0.98F, Mth.cos(am) * R);
                  ps.mulPose(Axis.YP.rotation(am));
                  Cubes.cbox(ps, vc, 0.0F, 0.0F, 0.0F, half, 0.055F, 0.09F, 0.7F, 0.48F, 0.22F, lit);
                  Cubes.cbox(ps, vc, 0.0F, -0.92F, 0.0F, half, 0.04F, 0.1F, 0.45F, 0.3F, 0.14F, lit);
                  ps.popPose();
               }

               Cubes.cbox(ps, vc, 0.0F, 0.01F, 0.0F, 1.2F, 0.03F, 1.2F, 0.3F, 0.2F, 0.1F, lit);
               ps.popPose();
            }

            Vec3 sc = this.center.add(this.right.scale(3.2)).add(0.0, (double)(-rise), 0.0);
            float yaw = (float)Math.atan2(this.dir.x, this.dir.z);
            ps.pushPose();
            ps.translate(sc.x, sc.y, sc.z);
            ps.mulPose(Axis.YP.rotation(yaw));
            float gr = 0.85F;
            float gg = 0.66F;
            float gb = 0.25F;
            Cubes.cbox(ps, vc, 0.0F, 0.1F, 0.0F, 0.45F, 0.1F, 0.45F, gr * 0.7F, gg * 0.7F, gb * 0.7F, lit);
            Cubes.cbox(ps, vc, 0.0F, 1.4F, 0.0F, 0.07F, 1.25F, 0.07F, gr, gg, gb, lit);
            Cubes.cbox(ps, vc, 0.0F, 2.75F, 0.0F, 0.14F, 0.14F, 0.14F, gr, gg, gb, lit);
            float tilt = this.verdict != 1 && this.verdict != 2 ? (this.verdict == 0 ? -0.25F : 0.08F * Mth.sin(time * 0.05F)) : 0.25F;
            ps.pushPose();
            ps.translate(0.0F, 2.65F, 0.0F);
            ps.mulPose(Axis.XP.rotation(tilt));
            Cubes.cbox(ps, vc, 0.0F, 0.0F, 0.0F, 0.04F, 0.04F, 1.5F, gr, gg, gb, lit);

            for (int side = -1; side <= 1; side += 2) {
               float z = (float)side * 1.45F;

               for (int ch = -1; ch <= 1; ch += 2) {
                  Cubes.cbox(ps, vc, (float)ch * 0.18F, -0.55F, z, 0.012F, 0.55F, 0.012F, gr * 0.8F, gg * 0.8F, gb * 0.8F, lit);
               }

               ps.pushPose();
               ps.translate(0.0F, -1.1F, z);
               ps.mulPose(Axis.XP.rotation(-tilt));
               Cubes.cbox(ps, vc, 0.0F, 0.0F, 0.0F, 0.36F, 0.04F, 0.36F, gr, gg, gb, lit);
               ps.popPose();
            }

            ps.popPose();
            ps.popPose();
            sc = this.center.add(this.right.scale(7.6)).add(0.0, (double)(-rise), 0.0);
            yaw = (float)Math.atan2(-this.right.x, -this.right.z);
            ps.pushPose();
            ps.translate(sc.x, sc.y, sc.z);
            ps.mulPose(Axis.YP.rotation(yaw));
            Cubes.box(ps, vc, -3.2F, 0.0F, -0.9F, 3.2F, 2.4F, 0.9F, 0.24F, 0.14F, 0.07F, dim);
            Cubes.box(ps, vc, -3.4F, 2.4F, -1.1F, 3.4F, 2.6F, 1.1F, 0.42F, 0.26F, 0.12F, dim);

            for (int k = -2; k <= 2; k++) {
               Cubes.box(ps, vc, (float)k * 1.25F - 0.45F, 0.3F, 0.88F, (float)k * 1.25F + 0.45F, 2.1F, 0.95F, 0.3F, 0.18F, 0.09F, dim);
            }

            Cubes.box(ps, vc, -3.25F, 0.05F, 0.9F, 3.25F, 0.12F, 1.0F, 0.55F, 0.42F, 0.17F, lit);
            ps.popPose();
            double wallTop = 11.0 * (double)a;
            int roofRings = 7;

            for (int r = 0; r < roofRings; r++) {
               double k0 = (double)r / (double)roofRings;
               double k1 = (double)(r + 1) / (double)roofRings;

               for (int k = 0; k < seg; k++) {
                  double ang0 = (double)k / (double)seg * Math.PI * 2.0;
                  double ang1 = (double)(k + 1) / (double)seg * Math.PI * 2.0;
                  Vec3[] q = new Vec3[4];
                  double[][] kk = new double[][]{{k0, ang0}, {k1, ang0}, {k1, ang1}, {k0, ang1}};

                  for (int m = 0; m < 4; m++) {
                     double kr = kk[m][0];
                     double rad = Mth.lerp(kr, 1.0, 0.22);
                     double y = wallTop + Math.sin(kr * Math.PI / 2.0) * 7.5 * (double)a;
                     q[m] = c0.add(this.dir.scale(Math.cos(kk[m][1]) * (A + 4.5) * rad))
                        .add(this.right.scale(Math.sin(kk[m][1]) * (B + 4.5) * rad))
                        .add(0.0, y, 0.0);
                  }

                  boolean rib = k % 7 == 0;
                  float shade = 0.07F + 0.03F * (float)k1;
                  float[] col = rib ? new float[]{0.45F, 0.33F, 0.12F} : new float[]{shade * 1.2F, shade, shade * 0.8F};
                  Cubes.quad(ps, vc, q, col, rib ? lit : dim, 0.0F, -1.0F, 0.0F);
               }
            }

            this.judgeman(mc, ps, buf, partial, time, a);
            VertexConsumer glow = buf.getBuffer(Cubes.GLOW);

            for (int st = 0; st < 2; st++) {
               Cubes.cone(ps, glow, this.stand(st == 1), 0.25F, 1.5F, 8.0F, 0.2F * a, 0.15F * a, 0.06F * a);
            }

            for (int k = 0; k < 5; k++) {
               Vec3 base = this.center.add(this.right.scale(9.0 + (double)k * 0.4)).add(this.dir.scale((double)(k - 2) * 1.6));
               Cubes.cone(ps, glow, base, 0.4F, 1.4F, 16.0F, 0.1F * a, 0.07F * a, 0.025F * a);
            }

            return true;
         }
      }

      void judgeman(Minecraft mc, PoseStack ps, MultiBufferSource buf, float partial, float time, float a) {
         VertexConsumer vc = buf.getBuffer(Cubes.SOLID);
         Vec3 face = this.judge(partial);
         Vec3 toCenter = this.center.subtract(face);
         float yaw = (float)Math.atan2(toCenter.x, toCenter.z);
         int lit = LightTexture.pack(13, 13);
         int dim = LightTexture.pack(5, 5);
         int full = 15728880;
         ps.pushPose();
         ps.translate(face.x, face.y + (double)(Mth.sin(time * 0.05F) * 0.08F), face.z);
         ps.mulPose(Axis.YP.rotation(yaw));
         ps.mulPose(Axis.XP.rotationDegrees(8.0F));
         ps.scale(2.1F, 2.1F, 2.1F);
         Random r = new Random(11L);

         for (int layer = 0; layer < 4; layer++) {
            float z = -0.25F - (float)layer * 0.18F;
            int strands = 14 + layer * 4;

            for (int k = 0; k < strands; k++) {
               float x = ((float)k / ((float)strands - 1.0F) * 2.0F - 1.0F) * (1.0F + (float)layer * 0.45F);
               float top = 0.95F - Math.abs(x) * 0.25F;
               float len = 1.8F + (float)layer * 0.55F + r.nextFloat() * 0.9F;
               float w = 0.09F + r.nextFloat() * 0.07F;
               float c = 0.16F + 0.06F * r.nextFloat() + 0.03F * (float)(3 - layer);
               Cubes.box(ps, vc, x - w, top - len, z - 0.12F, x + w, top, z + 0.12F, c * 1.2F, c * 0.82F, c * 0.45F, dim);
            }
         }

         for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 4; k++) {
               float x = (float)side * (0.48F + (float)k * 0.12F);
               Cubes.box(ps, vc, x - 0.1F, -1.6F - (float)k * 0.25F, -0.05F + (float)k * 0.03F, x + 0.1F, 0.35F, 0.2F, 0.22F, 0.15F, 0.08F, dim);
            }
         }

         Cubes.box(ps, vc, -0.38F, 0.42F, -0.15F, 0.38F, 0.82F, 0.3F, 0.98F, 0.97F, 0.93F, full);
         Cubes.box(ps, vc, -0.3F, 0.82F, -0.1F, 0.3F, 0.95F, 0.25F, 1.0F, 1.0F, 0.96F, full);
         Cubes.box(ps, vc, -0.4F, -0.55F, -0.12F, 0.4F, 0.42F, 0.3F, 0.84F, 0.78F, 0.72F, lit);
         Cubes.box(ps, vc, -0.3F, -0.78F, -0.1F, 0.3F, -0.55F, 0.27F, 0.82F, 0.76F, 0.7F, lit);

         for (int side = -1; side <= 1; side += 2) {
            float ex = (float)side * 0.18F;
            Cubes.box(ps, vc, ex - 0.12F, 0.05F, 0.3F, ex + 0.12F, 0.08F, 0.32F, 0.25F, 0.15F, 0.15F, lit);

            for (int st = -1; st <= 1; st++) {
               Cubes.box(ps, vc, ex + (float)st * 0.07F - 0.008F, 0.0F, 0.3F, ex + (float)st * 0.07F + 0.008F, 0.13F, 0.325F, 0.25F, 0.15F, 0.15F, lit);
            }
         }

         Cubes.box(ps, vc, -0.13F, -0.4F, 0.3F, 0.13F, -0.34F, 0.32F, 0.32F, 0.08F, 0.12F, lit);
         Cubes.box(ps, vc, -0.09F, -0.46F, 0.3F, 0.09F, -0.4F, 0.32F, 0.26F, 0.06F, 0.1F, lit);
         if (this.verdict == 2) {
            float t = Mth.clamp(((float)(HFx.now() - this.verdictAt) + partial) / 30.0F, 0.0F, 1.0F);
            VertexConsumer g = buf.getBuffer(Cubes.GLOW);

            for (int side = -1; side <= 1; side += 2) {
               float len = 0.75F * t;
               Cubes.box(ps, g, (float)side * 0.18F - 0.025F, 0.04F - len, 0.32F, (float)side * 0.18F + 0.025F, 0.04F, 0.34F, 0.8F, 0.02F, 0.02F, full);
            }
         }

         ps.popPose();
      }

      void renderGlow(Vec3 cam, float partial, float time) {
         float a = this.alpha(partial);
         if (!(a <= 0.02F)) {
            for (int s = 0; s < 2; s++) {
               Vec3 at = this.stand(s == 1);
               HFx.glow(cam, at.add(0.0, 7.5, 0.0), 1.2F, 0.7F, 0.0F, 0.35F * a);
            }

            Vec3 j = this.judge(partial);
            HFx.glow(cam, j.add(0.0, 2.5, 0.0).add(this.right.scale(1.5)), 12.0F, 0.62F, 0.0F, (0.3F + 0.05F * Mth.sin(time * 0.1F)) * a);
            HFx.glow(cam, j.add(0.0, 1.6, 0.0), 2.6F, 0.85F, 0.81F, 0.35F * a);
            if (this.verdict == 2) {
               HFx.glow(cam, j, 2.5F, 0.3F, 0.0F, 0.5F * a);
            }
         }
      }
   }

   static final class Cut {
      final HFx.Court c;
      int age;
      float fov = 50.0F;
      float roll;

      Cut(HFx.Court c) {
         this.c = c;
      }

      Vec3[] shot(float partial) {
         Minecraft mc = Minecraft.getInstance();
         float t = (float)this.age + partial;
         Entity p = mc.level.getEntity(this.c.pros);
         Vec3 pp = p != null ? p.getPosition(partial) : this.c.stand(false);
         Vec3 head = pp.add(0.0, 1.6, 0.0);
         Vec3 jb = this.c.judge(partial);
         Vec3 away = new Vec3(pp.x - jb.x, 0.0, pp.z - jb.z).normalize();
         if (t < 22.0F) {
            float k = t / 22.0F;
            this.fov = 46.0F;
            this.roll = 0.0F;
            Vec3 eye = head.add(away.scale(2.2 - 0.5 * (double)k)).add(0.0, -0.6, 0.0);
            return new Vec3[]{eye, head.add(0.0, 0.5 + (double)k * 0.6, 0.0), head};
         } else if (t < 46.0F) {
            float k = (t - 22.0F) / 24.0F;
            this.fov = 58.0F;
            this.roll = 0.0F;
            Vec3 d = this.c.stand(true);
            Vec3 eye = d.add(this.c.dir.scale(2.2)).add(this.c.right.scale(1.2)).add(0.0, 2.3 + 0.5 * (double)k, 0.0);
            return new Vec3[]{eye, this.c.stand(false).add(0.0, 1.3, 0.0), d.add(0.0, 1.0, 0.0)};
         } else {
            float k = (t - 46.0F) / 24.0F;
            this.fov = 62.0F - 6.0F * k;
            this.roll = 0.0F;
            Vec3 eye = pp.add(away.scale(7.5 - 1.5 * (double)k)).add(0.0, 0.9, 0.0);
            return new Vec3[]{eye, jb.add(0.0, -2.2, 0.0), pp.add(0.0, 1.0, 0.0)};
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

      int ribbons(Minecraft mc, Vec3 cam, float partial, long now) {
         float t = (float)(now - this.start) + partial;
         float k = Mth.clamp(t / (float)this.life, 0.0F, 1.0F);
         Entity c = mc.level.getEntity(this.f.caster);
         if (this.f.kind == 0 && c != null) {
            Vec3 eye = c.getEyePosition(partial).add(0.0, -0.3, 0.0);
            Vec3 fwd = this.f.b.normalize();
            Vec3 right = fwd.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            Vec3 up = right.cross(fwd).normalize();
            float reach = this.f.other == 2 ? 4.2F : 2.6F;
            int dir = this.f.other == 1 ? -1 : 1;
            float lead = Mth.clamp(t / ((float)this.life * 0.45F), 0.0F, 1.0F);
            List<Vec3> pts = new ArrayList<>();

            for (int i = 0; i <= 10; i++) {
               float a = Mth.lerp((float)i / 10.0F, -1.1F, 1.1F) * (float)dir;
               if ((a * (float)dir + 1.1F) / 2.2F > lead) {
                  break;
               }

               double tilt = this.f.other == 2 ? 0.6 : 0.25;
               pts.add(
                  eye.add(fwd.scale(Math.cos((double)a) * (double)reach))
                     .add(right.scale(Math.sin((double)a) * (double)reach))
                     .add(up.scale(-Math.sin((double)a) * tilt * (double)dir))
               );
            }

            Collections.reverse(pts);
            HFx.ribbon(cam, pts, 0.18F + 0.08F * this.f.f, 0.81F, 1.0F - k, 0.3F);
            return 1;
         } else if (this.f.kind == 11) {
            Vec3 fwd = this.f.b.normalize();
            Vec3 right = fwd.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            Vec3 diag = right.add(0.0, 0.8, 0.0).normalize();
            float grow = Mth.clamp(t / 3.0F, 0.0F, 1.0F);
            List<Vec3> pts = new ArrayList<>();
            pts.add(this.f.a.add(diag.scale(-3.2 * (double)grow)));
            pts.add(this.f.a);
            pts.add(this.f.a.add(diag.scale(3.2 * (double)grow)));
            HFx.ribbon(cam, pts, this.f.f > 0.5F ? 0.5F : 0.3F, 0.81F, 1.0F - k, 0.7F);
            return 1;
         } else if (this.f.kind == 6 && this.f.f > 0.5F) {
            for (int r = 0; r < 3; r++) {
               float kr = Mth.clamp((t - (float)(r * 4)) / (float)this.life, 0.0F, 1.0F);
               HFx.ring(cam, this.f.a.add(0.0, -1.0 + (double)r * 0.6, 0.0), 1.0F + 20.0F * kr, t * 0.05F + (float)r, 0.0F, 0.35F, 0.81F, 1.0F - kr);
            }

            return 1;
         } else if (this.f.kind == 10 && this.f.f > 0.5F) {
            List<Vec3> col = new ArrayList<>();

            for (int i = 0; i < 6; i++) {
               col.add(this.f.a.add(0.0, 18.0 - (double)i * 3.5, 0.0));
            }

            HFx.ribbon(cam, col, 1.4F * (1.0F - k), 0.81F, 0.9F * (1.0F - k), 0.5F);
            return 1;
         } else if (this.f.kind == 4 && c != null) {
            HFx.ring(cam, c.getPosition(partial).add(0.0, 1.0, 0.0), 0.6F + k * 1.4F, t, 0.2F, 0.12F, 0.81F, 1.0F - k);
            return 1;
         } else {
            return 0;
         }
      }
   }

   static final class Puff {
      Vec3 pos;
      Vec3 prev;
      Vec3 vel;
      final float size;
      final float gold;
      boolean white;
      final int life;
      int age;

      Puff(Vec3 pos, Vec3 vel, float size, int life, float gold) {
         this.pos = this.prev = pos;
         this.vel = vel;
         this.size = size;
         this.life = life;
         this.gold = gold;
      }

      boolean tick() {
         this.prev = this.pos;
         this.pos = this.pos.add(this.vel);
         this.vel = this.vel.scale(this.gold > 0.5F ? 0.99 : 0.9).add(0.0, this.gold > 0.5F ? 0.0 : 0.003, 0.0);
         return ++this.age < this.life;
      }

      int draw(Vec3 cam, float partial) {
         Vec3 p = this.prev.lerp(this.pos, (double)partial);
         float k = ((float)this.age + partial) / (float)this.life;
         float a = Mth.clamp(((float)this.age + partial) / 3.0F, 0.0F, 1.0F) * (1.0F - k);
         if (this.gold >= 0.5F) {
            HFx.glow(cam, p, this.size * 3.0F, this.white ? 1.0F : 0.75F, this.white ? 0.81F : 0.0F, a * 0.9F);
         } else {
            float s = this.size * (1.0F + k * 1.5F);
            if (p.distanceTo(cam) < (double)s + 0.3) {
               return 0;
            }

            Gfx.billboard(p.x - cam.x, p.y - cam.y, p.z - cam.z, s, (float)this.age * 0.03F, (float)(this.age * 7 % 97) / 97.0F, this.gold, 0.5F, a * 0.8F);
         }

         return 1;
      }
   }

   static final class Throw {
      final int owner;
      Vec3 pos;
      Vec3 prev;
      Vec3 vel = Vec3.ZERO;
      long stamp;

      Throw(int owner, Vec3 pos) {
         this.owner = owner;
         this.pos = this.prev = pos;
      }
   }
}
