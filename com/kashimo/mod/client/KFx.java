package com.kashimo.mod.client;

import com.jjk.mod.client.JjkPrefs;
import com.kashimo.mod.Net;
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
public final class KFx {
   static final float[] ELEC = new float[]{0.45F, 0.85F, 1.0F};
   static final float[] ELEC_CORE = new float[]{0.92F, 0.98F, 1.0F};
   static final float[] AMBER = new float[]{1.0F, 0.72F, 0.25F};
   static final float[] GAS = new float[]{0.65F, 0.95F, 0.25F};
   static final float[] PURPLE = new float[]{0.7F, 0.3F, 1.0F};
   static final float[] PURPLE_CORE = new float[]{0.96F, 0.86F, 1.0F};
   private static final List<KFx.Eff> EFFECTS = new ArrayList<>();
   static final Map<Integer, Integer> CHARGES = new HashMap<>();
   static final Map<Integer, Long> AMBER_UNTIL = new HashMap<>();
   private static final Map<Integer, Vec3> STAFFS = new HashMap<>();
   private static KFx.Cut cut;
   private static KFx.CamEntity cam;
   private static CameraType prevType;
   private static float shake;
   private static Matrix4f lastView = new Matrix4f();
   private static Matrix4f lastProj = new Matrix4f();

   private KFx() {
   }

   public static boolean cutsceneActive() {
      return cut != null;
   }

   static boolean amber(Entity e) {
      Long u = AMBER_UNTIL.get(e.getId());
      return u != null && e.level().getGameTime() < u;
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
         int me = mc.player.getId();
         boolean involved = f.caster == me || f.other == me;
         float[] sc = screen(f.kind == 1 ? f.b : f.a);
         switch (f.kind) {
            case 0:
               EFFECTS.add(new KFx.Eff(f, 10));
               boolean repelled = EFFECTS.stream().anyMatch(x -> x.f.kind == 14 && x.f.caster == f.caster && x.age < 3);
               if (involved && !repelled) {
                  if (f.f >= 2.0F) {
                     shake = Math.max(shake, 2.2F);
                  } else if (f.f >= 1.2F) {
                     shake = Math.max(shake, 1.2F);
                  } else if (f.other >= 0) {
                     shake = Math.max(shake, 0.7F);
                  }
               }
               break;
            case 1:
            case 16:
               EFFECTS.add(new KFx.Eff(f, Math.max(4, f.dur)));
               break;
            case 2:
               STAFFS.put(f.caster, f.a);
               break;
            case 3:
            case 7:
            case 12:
               EFFECTS.add(new KFx.Eff(f, f.dur));
               break;
            case 4:
               EFFECTS.add(new KFx.Eff(f, f.dur));
               if (f.caster == me || mc.player.position().distanceTo(f.a) < 40.0) {
                  shake = Math.max(shake, 2.5F);
               }
               break;
            case 5:
               EFFECTS.add(new KFx.Eff(f, f.dur + 16));
               EFFECTS.add(new KFx.Eff(f, f.dur, () -> {
                  if (Minecraft.getInstance().player.position().distanceTo(f.a) < 36.0) {
                     float[] s2 = screen(f.a);
                     shake = Math.max(shake, 2.0F);
                  }
               }));
               break;
            case 6:
               EFFECTS.add(new KFx.Eff(f, f.dur));
               if (involved) {
                  shake = Math.max(shake, 1.5F);
               }
               break;
            case 8:
            case 9:
            case 10:
               KFx.Cut c = new KFx.Cut(f);
               EFFECTS.add(c);
               if (cut == null && !JjkPrefs.skipCutscenes() && mc.player.position().distanceTo(f.a) < 40.0) {
                  cut = c;
                  prevType = mc.options.getCameraType();
                  mc.options.setCameraType(CameraType.FIRST_PERSON);
                  cam = new KFx.CamEntity(mc.level);
               }
               break;
            case 11:
               if (f.dur <= 0) {
                  CHARGES.remove(f.other);
               } else {
                  CHARGES.put(f.other, f.dur);
               }
               break;
            case 13:
               AMBER_UNTIL.remove(f.caster);
               break;
            case 14:
               EFFECTS.add(new KFx.Eff(f, f.dur));
               if (f.caster == me || mc.player.position().distanceTo(f.a) < (double)(f.f + 2.0F)) {
                  shake = Math.max(shake, f.caster == me ? 1.4F : 2.4F);
               }
               break;
            case 15:
               EFFECTS.add(new KFx.Eff(f, f.dur));
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
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            EFFECTS.clear();
            CHARGES.clear();
            STAFFS.clear();
            AMBER_UNTIL.clear();
            Impact.clear();
            if (cut != null) {
               endCut();
            }
         } else if (!mc.isPaused()) {
            Iterator<KFx.Eff> it = EFFECTS.iterator();

            while (it.hasNext()) {
               KFx.Eff f = it.next();
               f.age++;
               if (f instanceof KFx.Cut c) {
                  c.onAge();
               }

               if (f.callback != null && f.age == f.life) {
                  f.callback.run();
               }

               if (f.age >= f.life) {
                  it.remove();
               }
            }

            for (KFx.Eff fx : EFFECTS) {
               if (fx.f.kind == 15
                  && fx.callback == null
                  && mc.player != null
                  && mc.player.position().distanceTo(fx.f.a) < (double)(fx.f.f + 10.0F)
                  && fx.age < fx.life - 6) {
                  shake = Math.max(shake, 0.35F);
                  Random r = new Random((long)fx.age * 977L + (long)fx.f.caster);
                  if (fx.age % 9 == 4 && r.nextFloat() < 0.8F) {
                     shake = Math.max(shake, 1.3F);
                  }
               }
            }

            Impact.tick();
            shake = Math.max(0.0F, shake * 0.86F - 0.01F);
            if (cut != null) {
               boolean skip = mc.options.keyShift.isDown() && cut.age > 10 && mc.screen == null;
               if (skip || cut.age >= cut.cutLength() || !EFFECTS.contains(cut)) {
                  endCut();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRenderTick(RenderTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.START && cut != null && cam != null && mc.level != null) {
         double[] s = cut.shot(e.renderTickTime);
         Vec3 eye = new Vec3(s[0], s[1], s[2]);
         Vec3 subject = cut.f.a.add(0.0, 1.4, 0.0);
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
      if (KShaders.ready()) {
         if (e.getStage() == Stage.AFTER_LEVEL) {
            Impact.render(e.getPartialTick());
         } else if (e.getStage() == Stage.AFTER_PARTICLES) {
            Minecraft mc = Minecraft.getInstance();
            Matrix4f view = e.getPoseStack().last().pose();
            Matrix4f proj = e.getProjectionMatrix();
            lastView = new Matrix4f(view);
            lastProj = new Matrix4f(proj);
            float partial = e.getPartialTick();
            Vec3 cam = e.getCamera().getPosition();
            Gfx.setCamera(view, mc.gameRenderer.getDepthFar() * 0.9F);
            float time = (float)(mc.level.getGameTime() % 100000L) + partial;
            Gfx.f(KShaders.energy, "Time", time);
            Gfx.f(KShaders.orb, "Time", time);
            KFx.Draw d = new KFx.Draw(cam, view, proj, time);

            for (KFx.Eff f : EFFECTS) {
               f.render(d, partial);
            }

            for (Entry<Integer, Vec3> s : STAFFS.entrySet()) {
               Vec3 base = s.getValue();
               d.ribbon(base, base.add(0.0, 2.4, 0.0), 0.07F, 0.07F, ELEC, 0.7F, 1);
               d.arc(
                  base.add(0.0, 2.2, 0.0),
                  base.add(0.0, 2.4, 0.0).add(Math.sin((double)time * 0.7) * 0.6, 0.3, Math.cos((double)time * 0.9) * 0.6),
                  0.05F,
                  ELEC_CORE,
                  1.0F,
                  (int)((double)time * 0.5)
               );
               d.glow(base.add(0.0, 2.35, 0.0), 0.6F + 0.15F * Mth.sin(time * 0.6F), ELEC, 0.7F, 0);
            }

            for (Entity en : mc.level.entitiesForRendering()) {
               Integer c = CHARGES.get(en.getId());
               Vec3 pos = en.getPosition(partial);
               if (c != null && c > 0) {
                  for (int i = 0; i < c; i++) {
                     float a = time * 0.15F + (float)i * (float) (Math.PI * 2) / (float)c.intValue();
                     Vec3 p = pos.add(
                        (double)Mth.cos(a) * 0.55, (double)en.getBbHeight() + 0.35 + 0.08 * (double)Mth.sin(time * 0.3F + (float)i), (double)Mth.sin(a) * 0.55
                     );
                     d.glow(p, 0.22F, c >= 3 ? AMBER : ELEC, 0.95F, 4);
                  }
               }

               if (amber(en)) {
                  Random r = new Random((long)((double)time * 0.5) * 31L + (long)en.getId());

                  for (int i = 0; i < 4; i++) {
                     Vec3 a = pos.add(r.nextGaussian() * 0.35, (double)(r.nextFloat() * en.getBbHeight()), r.nextGaussian() * 0.35);
                     Vec3 b = a.add(r.nextGaussian() * 0.9, r.nextGaussian() * 0.6, r.nextGaussian() * 0.9);
                     d.arc(a, b, 0.035F, i % 2 == 0 ? PURPLE : PURPLE_CORE, 0.9F, r.nextInt());
                  }

                  d.glow(pos.add(0.0, (double)en.getBbHeight() * 0.55, 0.0), 1.7F, PURPLE, 0.32F, 0);
                  d.glow(pos.add(0.0, (double)en.getBbHeight() * 0.55, 0.0), 0.9F + 0.15F * Mth.sin(time * 0.8F), PURPLE_CORE, 0.18F, 0);
               }
            }
         }
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

      for (KFx.Eff f : EFFECTS) {
         if (f instanceof KFx.Cut) {
            KFx.Cut c = (KFx.Cut)f;
            if (c.f.kind == 8 && c.age > 50 && mc.player != null && mc.player.position().distanceTo(c.f.a) < (double)c.f.f) {
               float k = Mth.clamp((float)(c.age - 50) / 10.0F, 0.0F, 1.0F) * Mth.clamp((float)(c.life - c.age) / 10.0F, 0.0F, 1.0F);
               g.fillGradient(0, 0, w, h / 3, argb(0.55F * k, 1180958), 0);
               g.fillGradient(0, h - h / 3, w, h, 0, argb(0.55F * k, 1180958));
               if ((mc.level.getGameTime() + (long)c.f.caster) % 23L == 0L) {
                  g.fill(0, 0, w, h, argb(0.35F * k, 14862591));
               }
            }
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

   static Vec3 ground(Minecraft mc, Vec3 p) {
      if (mc.level == null) {
         return p;
      } else {
         int y = mc.level.getHeight(Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
         return new Vec3(p.x, (double)y + 0.05, p.z);
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

   static final class Cut extends KFx.Eff {
      float fov;
      float roll;
      final Vec3 fwd;
      final Vec3 right;

      Cut(Net.Fx f) {
         super(f, switch (f.kind) {
            case 8 -> f.dur;
            case 9 -> 80;
            default -> 130;
         });
         this.fov = 60.0F;
         Minecraft mc = Minecraft.getInstance();
         Entity c = mc.level.getEntity(f.caster);
         Vec3 l = c != null ? c.getLookAngle() : new Vec3(0.0, 0.0, 1.0);
         Vec3 flat = new Vec3(l.x, 0.0, l.z);
         this.fwd = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
         this.right = this.fwd.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      }

      int cutLength() {
         return switch (this.f.kind) {
            case 8 -> 70;
            case 9 -> 78;
            default -> 110;
         };
      }

      double[] shot(float partial) {
         double t = (double)((float)this.age + partial);
         Vec3 o = this.f.a;
         Vec3 head = o.add(0.0, 1.6, 0.0);
         double fv = 55.0;
         double rl = 0.0;
         Vec3 eye;
         Vec3 look;
         if (this.f.kind == 9) {
            if (t < 24.0) {
               double k = t / 24.0;
               eye = head.add(this.fwd.scale(1.4 - 0.2 * k)).add(0.0, 0.05, 0.0);
               look = head;
               fv = 40.0;
            } else if (t < 50.0) {
               double a = (t - 24.0) * 0.09;
               Vec3 rad = this.fwd.scale(Math.cos(a)).add(this.right.scale(Math.sin(a)));
               eye = o.add(rad.scale(3.2)).add(0.0, 0.4, 0.0);
               look = head.add(0.0, -0.3, 0.0);
               fv = 60.0;
               rl = 8.0 * Math.sin(a);
            } else {
               double k = (double)KFx.smooth((float)((t - 50.0) / 20.0));
               eye = o.add(this.fwd.scale(2.2 + 2.5 * k)).add(0.0, 0.2 + 0.4 * k, 0.0).add(this.right.scale(-0.8 * k));
               look = head.add(0.0, 0.1, 0.0);
               fv = 46.0 + 14.0 * k;
               rl = -5.0 * k;
            }
         } else if (this.f.kind == 10) {
            Vec3 target = this.f.b;
            if (t < 40.0) {
               eye = o.add(this.fwd.scale(-1.5)).add(this.right.scale(1.6)).add(0.0, 0.4, 0.0);
               look = head.add(0.0, 6.0 + t * 0.2, 0.0).add(this.fwd.scale(4.0));
               fv = 65.0;
            } else if (t < 78.0) {
               double k = (t - 40.0) / 38.0;
               eye = head.add(this.fwd.scale(1.6)).add(this.right.scale(-0.4)).add(0.0, -0.1, 0.0);
               look = head;
               fv = 50.0 - 12.0 * k;
               rl = 4.0 * k;
            } else {
               Vec3 side = target.subtract(o).normalize().cross(new Vec3(0.0, 1.0, 0.0)).normalize();
               eye = o.add(target.subtract(o).scale(0.45)).add(side.scale(18.0)).add(0.0, 9.0, 0.0);
               look = target.add(0.0, 6.0, 0.0);
               fv = 70.0;
            }
         } else if (t < 25.0) {
            eye = head.add(this.fwd.scale(1.5)).add(this.right.scale(0.3));
            look = head;
            fv = 42.0;
         } else {
            double a = (t - 25.0) * 0.04;
            Vec3 rad = this.fwd.scale(Math.cos(a)).add(this.right.scale(Math.sin(a)));
            eye = o.add(rad.scale(9.0)).add(0.0, 4.0 + (t - 25.0) * 0.05, 0.0);
            look = head.add(0.0, 3.0, 0.0);
            fv = 70.0;
         }

         return new double[]{eye.x, eye.y, eye.z, look.x, look.y, look.z, fv, rl};
      }

      void onAge() {
         Minecraft mc = Minecraft.getInstance();
         boolean me = KFx.cut == this || mc.player != null && mc.player.position().distanceTo(this.f.a) < 40.0;
         if (this.f.kind == 9) {
            if (this.age == 46 && me) {
               Impact.hit(5, 3, 0, 0.5F, 0.55F);
            }

            if (this.age == 52 && me) {
               Impact.hit(3, 2, 0, 0.5F, 0.55F);
            }

            if (this.age == 70 && me) {
               Impact.hit(0, 3, 8, 0.5F, 0.55F);
               KFx.shake = Math.max(KFx.shake, 2.2F);
            }

            if (this.age == 70 && mc.level != null) {
               KFx.AMBER_UNTIL.put(this.f.caster, mc.level.getGameTime() + (long)this.f.dur);
            }
         } else if (this.f.kind == 10) {
            if (this.age == 80 && me) {
               Impact.hit(0, 4, 0, 0.5F, 0.5F);
            }

            if (this.age == 84 && me) {
               Impact.hit(5, 3, 0, 0.5F, 0.5F);
            }

            if (this.age == 87 && me) {
               Impact.hit(4, 3, 12, 0.5F, 0.5F);
               KFx.shake = Math.max(KFx.shake, 3.5F);
            }
         } else if (this.f.kind == 8) {
            if (this.age == 46 && me) {
               Impact.hit(5, 3, 0, 0.5F, 0.5F);
            }

            if (this.age == 50 && me) {
               Impact.hit(3, 2, 8, 0.5F, 0.5F);
               KFx.shake = Math.max(KFx.shake, 1.8F);
            }
         }
      }

      @Override
      void render(KFx.Draw d, float partial) {
         double t = (double)((float)this.age + partial);
         Vec3 o = this.f.a;
         Minecraft mc = Minecraft.getInstance();
         if (this.f.kind == 9) {
            if (t < 74.0) {
               float k = (float)Math.min(1.0, t / 70.0);
               Random r = new Random((long)(t / 2.0) * 31L);

               for (int i = 0; i < 4 + (int)(k * 10.0F); i++) {
                  Vec3 a = o.add(r.nextGaussian() * 0.5, (double)r.nextFloat() * 2.0, r.nextGaussian() * 0.5);
                  d.arc(
                     a,
                     a.add(r.nextGaussian() * 1.6, r.nextGaussian() * 1.2, r.nextGaussian() * 1.6),
                     0.05F,
                     i % 3 == 0 ? KFx.PURPLE_CORE : KFx.PURPLE,
                     0.9F,
                     r.nextInt()
                  );
               }

               if (t > 30.0) {
                  d.bolt(o.add(r.nextGaussian() * 3.0, 30.0, r.nextGaussian() * 3.0), o.add(0.0, 1.0, 0.0), 0.12F, KFx.PURPLE_CORE, 0.8F, (int)(t / 2.0));
               }

               d.glow(o.add(0.0, 1.0, 0.0), 1.0F + k * 3.0F, KFx.PURPLE, 0.5F, 0);
            }

            if (t > 68.0 && t < 80.0) {
               float k = (float)((t - 70.0 + 2.0) / 12.0);
               d.glow(o.add(0.0, 1.0, 0.0), 2.0F + k * 16.0F, KFx.PURPLE, 1.0F - k, 2);
               d.glow(o.add(0.0, 1.0, 0.0), 3.0F + k * 10.0F, KFx.ELEC_CORE, (1.0F - k) * 0.8F, 4);
            }
         } else if (this.f.kind == 10) {
            Vec3 target = this.f.b;
            float charge = (float)Math.min(1.0, t / 78.0);
            if (t < 82.0) {
               Vec3 sky = o.add(0.0, 14.0, 0.0);
               d.orb(sky, 1.0F + charge * 4.0F, 0, KFx.ELEC, KFx.ELEC_CORE, 1.1F);
               d.glow(sky, 8.0F + charge * 14.0F, KFx.AMBER, 0.35F * charge, 0);
               Random r = new Random((long)(t / 2.0) * 7L);

               for (int i = 0; i < 6; i++) {
                  Vec3 a = o.add(r.nextGaussian() * 0.4, (double)(1.0F + r.nextFloat()), r.nextGaussian() * 0.4);
                  d.arc(a, sky, 0.06F, i % 2 == 0 ? KFx.AMBER : KFx.ELEC_CORE, 0.7F * charge, r.nextInt());
               }
            }

            if (t >= 80.0 && t < 108.0) {
               float k = (float)((t - 80.0) / 28.0);
               d.bolt(target.add(0.0, 120.0, 0.0), target, 3.2F * (1.0F - k * 0.6F), KFx.ELEC_CORE, 1.0F - k, (int)(t / 2.0));
               d.bolt(target.add(5.0, 120.0, -3.0), target, 1.4F, KFx.ELEC, (1.0F - k) * 0.8F, (int)(t / 2.0) + 9);
               d.glow(target, 12.0F + k * 40.0F, KFx.ELEC_CORE, 1.0F - k, 2);
               d.glow(target, 20.0F + k * 20.0F, KFx.ELEC, (1.0F - k) * 0.8F, 0);
            }
         } else if (this.f.kind == 8) {
            float r = this.f.f;
            if (t > 40.0) {
               float al = (float)(Mth.clamp((t - 50.0 + 10.0) / 10.0, 0.0, 1.0) * Mth.clamp(((double)this.life - t) / 10.0, 0.0, 1.0));
               Random rr = new Random((long)(t / 3.0) * 13L);

               for (int i = 0; i < 16; i++) {
                  float a = (float)i * (float) (Math.PI * 2) / 16.0F + rr.nextFloat() * 0.3F;
                  Vec3 base = o.add((double)(Mth.cos(a) * r), 0.0, (double)(Mth.sin(a) * r));
                  d.arc(
                     base,
                     o.add((double)(Mth.cos(a) * r) * 0.3, (double)r * 0.9, (double)(Mth.sin(a) * r) * 0.3),
                     0.12F,
                     i % 2 == 0 ? KFx.PURPLE : KFx.PURPLE_CORE,
                     0.6F * al,
                     rr.nextInt()
                  );
               }

               d.glow(o.add(0.0, (double)r * 0.9, 0.0), 6.0F, KFx.PURPLE_CORE, 0.6F * al, 4);
               d.glow(o.add(0.0, (double)r * 0.9, 0.0), r * 0.8F, KFx.PURPLE, 0.25F * al, 0);
            }
         }
      }

      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         String jp = null;
         String en = null;
         int col = 10479871;
         float s0 = 0.0F;
         float s1 = 0.0F;
         if (this.f.kind == 9) {
            if (t > 52.0F && t < 76.0F) {
               jp = "幻獣琥珀";
               en = "MYTHICAL BEAST AMBER";
               col = 16761402;
               s0 = 52.0F;
               s1 = 76.0F;
            }
         } else if (this.f.kind == 10) {
            if (t > 44.0F && t < 80.0F) {
               jp = "最終奥義";
               en = "FINAL TECHNIQUE: HEAVEN'S LAST THUNDER";
               col = 16767082;
               s0 = 44.0F;
               s1 = 80.0F;
            }
         } else if (t > 8.0F && t < 68.0F) {
            col = 12946687;
            boolean second = t > 30.0F;
            jp = second ? "雷神宮" : "領域展開";
            en = second ? "PALACE OF THE STORM GOD" : "DOMAIN EXPANSION";
            s0 = second ? 30.0F : 8.0F;
            s1 = second ? 68.0F : 30.0F;
         }

         if (jp != null) {
            float in = KFx.smooth((t - s0) / 5.0F);
            float out = KFx.smooth((s1 - t) / 5.0F);
            float a = in * out;
            int slide = (int)((1.0F - in) * -60.0F + (1.0F - out) * 40.0F);
            float s = 3.6F;
            int cy = (int)((float)h * 0.42F);
            int bandH = (int)(s * 14.0F + 18.0F);
            g.fill(0, cy - bandH / 2, w, cy + bandH / 2, KFx.argb(0.62F * a, 0));
            g.fill(0, cy + bandH / 2 - 2, w, cy + bandH / 2, KFx.argb(0.9F * a, col));
            g.fill(0, cy - bandH / 2, w, cy - bandH / 2 + 1, KFx.argb(0.6F * a, col));
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F + (float)slide, (float)cy - s * 7.0F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.drawCenteredString(mc.font, Component.literal(jp), 0, 0, KFx.argb(a, 16777215));
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F - (float)slide * 0.5F, (float)cy + s * 3.5F, 0.0F);
            g.pose().scale(1.3F, 1.3F, 1.0F);
            g.drawCenteredString(mc.font, Component.literal(en), 0, 0, KFx.argb(a, col));
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

      private void flush(int mode) {
         Gfx.f(KShaders.energy, "Mode", (float)mode);
         Gfx.flush(KShaders.energy, this.view, this.proj, true);
      }

      void glow(Vec3 at, float half, float[] c, float a, int mode) {
         Gfx.begin();
         Gfx.billboard(at.x - this.cam.x, at.y - this.cam.y, at.z - this.cam.z, half, 0.0F, c[0], c[1], c[2], a);
         this.flush(mode);
      }

      void ribbon(Vec3 a, Vec3 b, float wa, float wb, float[] c, float al, int mode) {
         Vec3 x0 = a.subtract(this.cam);
         Vec3 x1 = b.subtract(this.cam);
         Gfx.begin();
         Gfx.ribbon(
            new float[]{(float)x0.x, (float)x0.y, (float)x0.z, (float)x1.x, (float)x1.y, (float)x1.z},
            new float[]{wa, wb},
            new float[]{0.05F, 0.95F},
            c[0],
            c[1],
            c[2],
            al
         );
         this.flush(mode);
      }

      void arc(Vec3 a, Vec3 b, float width, float[] c, float al, int seed) {
         Random r = new Random((long)seed);
         Vec3 d = b.subtract(a);
         double len = d.length();
         if (!(len < 0.001)) {
            Vec3 dir = d.scale(1.0 / len);
            Vec3 s1 = dir.cross(Math.abs(dir.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0)).normalize();
            Vec3 s2 = dir.cross(s1);
            int n = Math.max(4, Math.min(28, (int)(len * 1.6)));
            float[] pts = new float[(n + 1) * 3];
            float[] w = new float[n + 1];
            float[] u = new float[n + 1];

            for (int i = 0; i <= n; i++) {
               double k = (double)i / (double)n;
               double amp = Math.sin(k * Math.PI) * len * 0.09;
               Vec3 p = a.add(d.scale(k)).add(s1.scale(r.nextGaussian() * amp)).add(s2.scale(r.nextGaussian() * amp)).subtract(this.cam);
               pts[i * 3] = (float)p.x;
               pts[i * 3 + 1] = (float)p.y;
               pts[i * 3 + 2] = (float)p.z;
               w[i] = width;
               u[i] = (float)k;
            }

            Gfx.begin();
            Gfx.ribbon(pts, w, u, c[0], c[1], c[2], al);
            this.flush(3);
         }
      }

      void bolt(Vec3 a, Vec3 b, float thick, float[] c, float al, int seed) {
         this.arc(a, b, thick, c, al, seed);
         this.arc(a, b, thick * 4.0F, c, al * 0.25F, seed);
         Random r = new Random((long)seed * 7L + 3L);
         Vec3 d = b.subtract(a);

         for (int i = 0; i < 3; i++) {
            double k = 0.2 + r.nextDouble() * 0.6;
            Vec3 from = a.add(d.scale(k));
            Vec3 to = from.add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(d.length() * (0.1 + r.nextDouble() * 0.2)));
            this.arc(from, to, thick * 0.45F, c, al * 0.8F, seed + i * 31);
         }

         this.glow(b, thick * 9.0F + 0.6F, c, al * 0.8F, 0);
         this.glow(a, thick * 5.0F + 0.3F, c, al * 0.5F, 0);
      }

      void orb(Vec3 at, float radius, int mode, float[] a, float[] b, float intensity) {
         ShaderInstance sh = KShaders.orb;
         Gfx.f(sh, "Mode", (float)mode);
         Gfx.f(sh, "Intensity", intensity);
         Gfx.f(sh, "Seed", 7.0F);
         Gfx.v3(sh, "ColorA", a[0], a[1], a[2]);
         Gfx.v3(sh, "ColorB", b[0], b[1], b[2]);
         Matrix4f mv = new Matrix4f(this.view)
            .translate((float)(at.x - this.cam.x), (float)(at.y - this.cam.y), (float)(at.z - this.cam.z))
            .rotateY(this.time * 0.05F)
            .scale(radius);
         Gfx.meshBlend(KShaders.sphere(), sh, mv, this.proj);
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

      void render(KFx.Draw d, float partial) {
         if (this.callback == null) {
            float t = (float)this.age + partial;
            float fade = Mth.clamp(1.0F - t / (float)this.life, 0.0F, 1.0F);
            Minecraft mc = Minecraft.getInstance();
            switch (this.f.kind) {
               case 0:
                  float s = this.f.f;
                  d.glow(this.f.a, (0.8F + s) * (1.0F + t * 0.2F), KFx.ELEC, fade, 0);
                  d.glow(this.f.a, (0.6F + s * 0.8F) * fade, KFx.ELEC_CORE, fade, 4);
                  Random rx = new Random((long)this.f.a.hashCode());

                  for (int ix = 0; ix < 6 + (int)(s * 4.0F); ix++) {
                     Vec3 dir = new Vec3(rx.nextGaussian(), rx.nextGaussian(), rx.nextGaussian()).normalize().add(this.f.b.scale(0.6)).normalize();
                     Vec3 to = this.f.a.add(dir.scale((0.6 + (double)s) * (0.6 + (double)t * 0.15)));
                     d.arc(this.f.a, to, 0.03F + s * 0.015F, ix % 2 == 0 ? KFx.ELEC : KFx.ELEC_CORE, fade, rx.nextInt() + (int)(t / 2.0F));
                  }
                  break;
               case 1:
               case 16:
                  boolean pu = this.f.kind == 16;
                  Vec3 b = this.f.b;
                  if (this.f.other >= 0 && mc.level != null) {
                     Entity ex = mc.level.getEntity(this.f.other);
                     if (ex != null) {
                        b = ex.getPosition(partial).add(0.0, (double)ex.getBbHeight() * 0.5, 0.0);
                     }
                  }

                  float flick = 0.7F + 0.3F * (float)(this.age % 2 == 0 ? 1 : 0);
                  d.bolt(this.f.a, b, this.f.f * 0.18F + 0.02F, pu ? KFx.PURPLE_CORE : KFx.ELEC_CORE, fade * flick, (int)(t / 2.0F) * 13 + this.f.a.hashCode());
                  d.bolt(this.f.a, b, this.f.f * 0.08F + 0.01F, pu ? KFx.PURPLE : KFx.ELEC, fade * 0.8F, (int)(t / 2.0F) * 29 + 5);
                  if (pu) {
                     d.glow(b, this.f.f * 3.0F + 0.8F, KFx.PURPLE, fade * 0.6F, 2);
                  }
               case 2:
               case 8:
               case 9:
               case 10:
               case 11:
               case 13:
               default:
                  break;
               case 3:
                  Entity e = mc.level == null ? null : mc.level.getEntity(this.f.other);
                  if (e != null) {
                     Vec3 p = e.getPosition(partial).add(0.0, (double)e.getBbHeight() * 0.5, 0.0);
                     float pulse = 0.5F + 0.5F * Mth.sin(t * 1.4F);
                     d.glow(p, 1.4F + pulse * 0.6F, KFx.ELEC, 0.5F, 2);
                     d.arc(p.add(-0.6, 0.6, 0.0), p.add(0.6, -0.6, 0.2), 0.04F, KFx.ELEC_CORE, 0.9F, (int)t);
                  }
                  break;
               case 4:
                  float w = this.f.f * (t < 3.0F ? t / 3.0F : 1.0F) * (0.9F + 0.1F * Mth.sin(t * 3.0F));
                  d.ribbon(this.f.a, this.f.b, w * 1.6F, w * 1.2F, KFx.ELEC, fade, 1);
                  d.ribbon(this.f.a, this.f.b, w * 0.6F, w * 0.5F, KFx.ELEC_CORE, fade, 1);
                  Vec3 dir = this.f.b.subtract(this.f.a);

                  for (int ix = 0; ix < 8; ix++) {
                     Vec3 s0 = this.f.a.add(dir.scale((double)ix / 8.0));
                     Vec3 s1 = this.f.a.add(dir.scale((double)(ix + 1) / 8.0));
                     d.arc(s0, s1, 0.06F, KFx.ELEC_CORE, fade, ix * 17 + (int)(t / 2.0F));
                  }

                  d.glow(this.f.a, 2.5F * fade, KFx.ELEC_CORE, fade, 4);
                  break;
               case 5:
                  float pull = Math.min(1.0F, t / (float)this.f.dur);
                  if (t < (float)this.f.dur) {
                     d.orb(this.f.a, 1.4F + pull * 2.4F, 0, KFx.ELEC, KFx.ELEC_CORE, 1.2F);

                     for (int ix = 0; ix < 14; ix++) {
                        float a = (float)ix * 2.39996F + t * 0.12F;
                        float rr = 9.0F * (1.0F - (t * 0.05F + (float)ix * 0.071F) % 1.0F);
                        Vec3 p0 = this.f.a.add((double)(Mth.cos(a) * rr), (double)(Mth.sin(a * 1.3F) * rr) * 0.4, (double)(Mth.sin(a) * rr));
                        d.ribbon(p0, p0.add(this.f.a.subtract(p0).normalize().scale(2.5)), 0.16F, 0.05F, KFx.ELEC, 0.9F, 1);
                     }

                     d.glow(this.f.a, 4.0F + pull * 3.0F, KFx.ELEC, 0.35F, 0);
                     d.glow(this.f.a, 10.0F * pull, KFx.ELEC, 0.6F * (0.5F + 0.5F * Mth.sin(t)), 2);
                  } else {
                     float k = (t - (float)this.f.dur) / 16.0F;
                     d.glow(this.f.a, 3.0F + k * 14.0F, KFx.ELEC_CORE, 1.0F - k, 4);
                     d.glow(this.f.a, 4.0F + k * 18.0F, KFx.ELEC, (1.0F - k) * 0.8F, 2);
                  }
                  break;
               case 6:
                  for (int i = 0; i < 4; i++) {
                     float k = (t - (float)i * 1.5F) / 10.0F;
                     if (k > 0.0F && k < 1.0F) {
                        Vec3 p = this.f.a.add(this.f.b.scale((double)(k * this.f.f)));
                        d.glow(p, 0.8F + k * 5.0F, KFx.ELEC, (1.0F - k) * 0.9F, 2);
                     }
                  }
                  break;
               case 7:
                  Entity ex = mc.level == null ? null : mc.level.getEntity(this.f.caster);
                  boolean ownx = ex == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                  if (ex != null && !ownx) {
                     Vec3 c = ex.getPosition(partial).add(0.0, 1.0, 0.0);
                     float al = Math.min(1.0F, t / 5.0F) * Math.min(1.0F, ((float)this.life - t) / 10.0F);

                     for (int ring = 0; ring < 3; ring++) {
                        int seg = 10;

                        for (int i = 0; i < seg; i++) {
                           float a0 = t * 0.08F * (float)(ring % 2 == 0 ? 1 : -1) + (float)i * (float) (Math.PI * 2) / (float)seg;
                           float a1 = a0 + (float) (Math.PI * 2) / (float)seg;
                           float y0 = -0.6F + (float)ring * 0.6F + 0.25F * Mth.sin(a0 * 2.0F);
                           float y1 = -0.6F + (float)ring * 0.6F + 0.25F * Mth.sin(a1 * 2.0F);
                           d.ribbon(
                              c.add((double)Mth.cos(a0) * 1.2, (double)y0, (double)Mth.sin(a0) * 1.2),
                              c.add((double)Mth.cos(a1) * 1.2, (double)y1, (double)Mth.sin(a1) * 1.2),
                              0.04F,
                              0.04F,
                              new float[]{0.6F, 1.0F, 0.9F},
                              0.7F * al,
                              1
                           );
                        }
                     }
                  }
                  break;
               case 12:
                  Random rx = new Random(17L);
                  float al = Math.min(1.0F, t / 10.0F) * Math.min(1.0F, ((float)this.life - t) / 30.0F);

                  for (int i = 0; i < 18; i++) {
                     Vec3 p = this.f
                        .a
                        .add(
                           rx.nextGaussian() * 2.5,
                           0.4 + (double)(rx.nextFloat() * 2.0F) + (double)Mth.sin(t * 0.05F + (float)i) * 0.3,
                           rx.nextGaussian() * 2.5
                        );
                     d.glow(p, 1.5F + rx.nextFloat() * 1.5F, KFx.GAS, 0.18F * al, 0);
                  }
                  break;
               case 14:
                  float k = Mth.clamp(t / 7.0F, 0.0F, 1.0F);
                  float ease = 1.0F - (1.0F - k) * (1.0F - k);
                  float radx = this.f.f * ease;
                  float al = fade;
                  Entity casterx = mc.level == null ? null : mc.level.getEntity(this.f.caster);
                  Vec3 c = casterx != null ? casterx.getPosition(partial).add(0.0, 1.0, 0.0) : this.f.a;
                  boolean ownxx = casterx == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                  if (!ownxx) {
                     d.orb(c, Math.max(0.3F, radx), 2, KFx.PURPLE, KFx.PURPLE_CORE, 1.0F * fade);
                  }

                  d.glow(c, 1.5F + radx * 1.4F, KFx.PURPLE, 0.55F * fade, 2);
                  d.glow(c, 2.5F * (1.0F - k) + 0.5F, KFx.PURPLE_CORE, fade, 4);
                  Random rx = new Random((long)this.f.a.hashCode() + (long)((int)(t / 2.0F)) * 31L);

                  for (int ix = 0; ix < 18; ix++) {
                     float a = (float)ix * (float) (Math.PI * 2) / 18.0F + rx.nextFloat() * 0.3F;
                     float up = ix % 3 == 0 ? 0.6F + rx.nextFloat() * 0.6F : rx.nextFloat() * 0.25F - 0.4F;
                     Vec3 dir = new Vec3((double)Mth.cos(a), (double)up, (double)Mth.sin(a)).normalize();
                     Vec3 from = c.add(dir.scale(ownxx ? 2.5 : 0.4));
                     Vec3 to = c.add(dir.scale(Math.max(ownxx ? 3.0 : 0.8, (double)radx)));
                     d.arc(from, to, 0.09F + 0.06F * (1.0F - k), ix % 2 == 0 ? KFx.PURPLE : KFx.PURPLE_CORE, al, rx.nextInt());
                  }

                  float[] heights = new float[]{-0.9F, 0.2F, 1.2F};

                  for (int h = 0; h < 3; h++) {
                     float rr = Math.max(0.8F, radx * (1.0F - (float)h * 0.12F));
                     int seg = 20;

                     for (int ix = 0; ix < seg; ix++) {
                        float a0 = (float)ix * (float) (Math.PI * 2) / (float)seg + (float)h * 0.4F;
                        float a1 = a0 + (float) (Math.PI * 2) / (float)seg;
                        Vec3 p0 = c.add((double)(Mth.cos(a0) * rr), (double)heights[h], (double)(Mth.sin(a0) * rr));
                        Vec3 p1 = c.add((double)(Mth.cos(a1) * rr), (double)heights[h], (double)(Mth.sin(a1) * rr));
                        d.arc(p0, p1, h == 0 ? 0.16F : 0.09F, h == 1 ? KFx.PURPLE_CORE : KFx.PURPLE, al, rx.nextInt());
                     }
                  }

                  for (int ix = 0; ix < 10; ix++) {
                     float a = (float)ix * (float) (Math.PI * 2) / 10.0F;
                     d.glow(c.add((double)(Mth.cos(a) * radx), -0.9, (double)(Mth.sin(a) * radx)), 1.6F, KFx.PURPLE, 0.7F * al, 2);
                  }
                  break;
               case 15:
                  Entity caster = mc.level == null ? null : mc.level.getEntity(this.f.caster);
                  Vec3 o = caster != null ? caster.getPosition(partial) : this.f.a;
                  float in = Mth.clamp(t / 6.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.life - t) / 8.0F, 0.0F, 1.0F);
                  boolean own = caster == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
                  Random r = new Random((long)(t / 1.5F) * 7919L + (long)this.f.caster);
                  float rad = this.f.f;
                  d.glow(o.add(0.0, 18.0, 0.0), rad * 1.3F, KFx.PURPLE, 0.3F * in, 0);
                  d.glow(o.add(0.0, 18.0, 0.0), rad * 0.5F, KFx.PURPLE_CORE, 0.25F * in * (0.5F + 0.5F * Mth.sin(t * 1.7F)), 2);
                  if (!own) {
                     for (int i = 0; i < 7; i++) {
                        Vec3 a = o.add(r.nextGaussian() * 0.35, (double)r.nextFloat() * 1.9, r.nextGaussian() * 0.35);
                        d.arc(
                           a,
                           a.add(r.nextGaussian() * 1.4, r.nextGaussian() * 1.0, r.nextGaussian() * 1.4),
                           0.045F,
                           i % 2 == 0 ? KFx.PURPLE : KFx.PURPLE_CORE,
                           in,
                           r.nextInt()
                        );
                     }

                     d.glow(o.add(0.0, 1.0, 0.0), 2.2F, KFx.PURPLE, 0.45F * in, 0);
                  }

                  for (int i = 0; i < 8; i++) {
                     float a0 = r.nextFloat() * (float) (Math.PI * 2);
                     float d0 = 2.0F + r.nextFloat() * rad;
                     float a1 = a0 + (r.nextFloat() - 0.5F) * 1.4F;
                     float d1 = 2.0F + r.nextFloat() * rad;
                     Vec3 p0 = KFx.ground(mc, o.add((double)(Mth.cos(a0) * d0), 0.0, (double)(Mth.sin(a0) * d0)));
                     Vec3 p1 = KFx.ground(mc, o.add((double)(Mth.cos(a1) * d1), 0.0, (double)(Mth.sin(a1) * d1)));
                     d.arc(p0, p1, 0.07F, i % 3 == 0 ? KFx.PURPLE_CORE : KFx.PURPLE, 0.9F * in, r.nextInt());
                     d.glow(p1, 0.9F, KFx.PURPLE, 0.6F * in, 0);
                  }

                  for (int i = 0; i < 3; i++) {
                     float a = r.nextFloat() * (float) (Math.PI * 2);
                     float dd = 4.0F + r.nextFloat() * (rad + 6.0F);
                     Vec3 at = KFx.ground(mc, o.add((double)(Mth.cos(a) * dd), 0.0, (double)(Mth.sin(a) * dd)));
                     d.bolt(
                        at.add(r.nextGaussian() * 5.0, (double)(26.0F + r.nextFloat() * 8.0F), r.nextGaussian() * 5.0),
                        at,
                        0.12F + r.nextFloat() * 0.18F,
                        i == 0 ? KFx.PURPLE_CORE : KFx.PURPLE,
                        0.85F * in,
                        r.nextInt()
                     );
                     d.glow(at, 2.5F, KFx.PURPLE, 0.5F * in, 2);
                  }

                  if (r.nextFloat() < 0.25F) {
                     d.glow(o.add(r.nextGaussian() * 12.0, 24.0, r.nextGaussian() * 12.0), 22.0F, KFx.PURPLE_CORE, 0.35F * in, 0);
                  }
            }
         }
      }
   }
}
