package com.choso.mod.client;

import com.choso.mod.Net;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.jjk.mod.client.JjkPrefs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import net.minecraft.world.entity.player.Player;
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
public final class CFx {
   static final float[] BLOOD = new float[]{0.62F, 0.02F, 0.05F};
   static final float[] BRIGHT = new float[]{0.95F, 0.08F, 0.09F};
   static final float[] DARK = new float[]{0.32F, 0.0F, 0.02F};
   static final float[] GLOW = new float[]{1.0F, 0.12F, 0.08F};
   static final float[] STEAM = new float[]{0.85F, 0.35F, 0.35F};
   private static final List<CFx.Eff> EFFECTS = new ArrayList<>();
   private static final List<CFx.Splat> SPLATS = new ArrayList<>();
   public static final Map<Integer, long[]> POSES = new HashMap<>();
   static final Map<Integer, long[]> SCALE = new HashMap<>();
   static final Map<Integer, Long> CONVERGED = new HashMap<>();
   static final Set<Integer> KNOWN = new HashSet<>();
   private static CFx.Cut cut;
   private static CFx.CamEntity cam;
   private static CameraType prevType;
   private static float shake;
   private static Matrix4f lastView = new Matrix4f();
   private static Matrix4f lastProj = new Matrix4f();

   private CFx() {
   }

   public static boolean cutsceneActive() {
      return cut != null;
   }

   static long gameTime() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0L : mc.level.getGameTime();
   }

   static int scaleLevel(Entity e) {
      long[] s = SCALE.get(e.getId());
      return s != null && gameTime() < s[1] ? (int)s[0] : 0;
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
         return new Vec3(p.x, Math.min((double)y, p.y + 0.5) + 0.03, p.z);
      }
   }

   static Vec3 hands(Entity e, float partial) {
      Vec3 look = e.getViewVector(partial);
      return e.getEyePosition(partial).add(look.scale(0.9)).add(0.0, -0.35, 0.0);
   }

   static void stain(Vec3 at, float size) {
      if (SPLATS.size() > 160) {
         SPLATS.remove(0);
      }

      Vec3 g = ground(at);
      if (at.y - g.y < 6.0) {
         SPLATS.add(new CFx.Splat(g, size, new Random().nextInt(1000)));
      }
   }

   public static void receive(Net.Fx f) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         int me = mc.player.getId();
         boolean involved = f.caster == me || f.other == me;
         if (f.caster >= 0) {
            KNOWN.add(f.caster);
         }

         float[] sc = screen(f.a);
         long now = gameTime();
         switch (f.kind) {
            case 0:
               EFFECTS.add(new CFx.Eff(f, 12));
               stain(f.a, 0.6F + f.f * 0.5F);
               if (involved) {
                  if (f.f >= 2.0F) {
                     shake = Math.max(shake, 2.6F);
                  } else if (f.other >= 0) {
                     shake = Math.max(shake, f.f >= 1.2F ? 0.9F : 0.5F);
                  }
               }
               break;
            case 1:
            case 13:
               EFFECTS.add(new CFx.Eff(f, f.dur));
               break;
            case 2:
            case 4:
            case 5:
            case 16:
            case 17:
               EFFECTS.add(new CFx.Eff(f, f.dur));
               break;
            case 3:
               EFFECTS.add(new CFx.Eff(f, f.dur + 12));
               CONVERGED.remove(f.caster);
               if (f.f > 0.25F && f.caster == me) {
                  boolean big = true;
                  float[] s2 = screen(f.b);
                  shake = Math.max(shake, big ? 3.0F : 1.6F);
               }
               break;
            case 6:
            case 12:
               if (f.other >= 0) {
                  EFFECTS.removeIf(x -> (x.f.kind == 2 || x.f.kind == 4 || x.f.kind == 16) && x.f.other == f.other && x.f.caster == f.caster);
               }

               EFFECTS.add(new CFx.Eff(f, f.kind == 12 ? 18 : 12));
               stain(f.a, f.f * (f.kind == 12 ? 1.4F : 1.0F));
               if (f.kind == 12 && mc.player.position().distanceTo(f.a) < 14.0) {
                  shake = Math.max(shake, 0.8F);
               }
               break;
            case 7:
               EFFECTS.add(new CFx.Eff(f, f.dur + 2));
               if (f.f == 0.0F) {
                  later(f, f.dur, () -> {
                     CONVERGED.put(f.caster, gameTime() + 240L);
                     if (f.caster == Minecraft.getInstance().player.getId()) {
                        shake = Math.max(shake, 0.6F);
                     }
                  });
               }
               break;
            case 8:
               if (f.f >= 2.0F) {
                  startCut(new CFx.Cut(f));
                  later(f, 50, () -> SCALE.put(f.caster, new long[]{2L, gameTime() + (long)f.dur}));
               } else {
                  SCALE.put(f.caster, new long[]{1L, now + (long)f.dur});
                  EFFECTS.add(new CFx.Eff(f, 16));
                  if (f.caster == me) {
                  }
               }
               break;
            case 9:
            case 10:
               startCut(new CFx.Cut(f));
               break;
            case 11:
               EFFECTS.add(new CFx.Eff(f, f.dur));
               CONVERGED.remove(f.caster);
               if (f.caster == me) {
                  later(f, f.dur, () -> shake = Math.max(shake, 1.0F));
               }
               break;
            case 14:
               SCALE.remove(f.caster);
               break;
            case 15:
               POSES.put(f.caster, new long[]{(long)f.f, now, (long)f.dur});
               break;
            case 18:
               for (CFx.Eff e : EFFECTS) {
                  if (e.f.kind == 3 && e.f.other == f.other && e.f.caster == f.caster) {
                     e.prevTip = e.tip == null ? f.a : e.tip;
                     e.tip = f.a;
                     e.prevDir = e.tipDir == null ? f.b : e.tipDir;
                     e.tipDir = f.b;
                     e.tipAge = e.age;
                     if (f.f > 0.0F && e.stopT < 0.0F) {
                        e.stopT = (float)e.age;
                     }
                  }
               }
         }
      }
   }

   private static void later(Net.Fx f, int ticks, Runnable r) {
      EFFECTS.add(new CFx.Eff(f, ticks, r));
   }

   private static void startCut(CFx.Cut c) {
      Minecraft mc = Minecraft.getInstance();
      EFFECTS.add(c);
      if (cut == null && !JjkPrefs.skipCutscenes() && mc.player.position().distanceTo(c.f.a) < 48.0) {
         cut = c;
         prevType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         cam = new CFx.CamEntity(mc.level);
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
            SPLATS.clear();
            POSES.clear();
            SCALE.clear();
            CONVERGED.clear();
            KNOWN.clear();
            Impact.clear();
            if (cut != null) {
               endCut();
            }
         } else if (!mc.isPaused()) {
            for (CFx.Eff f : new ArrayList<>(EFFECTS)) {
               f.age++;
               if (f instanceof CFx.Cut c) {
                  c.onAge();
               }

               if (f.callback != null && f.age == f.life) {
                  f.callback.run();
               }

               if (f.f.kind == 17 && f.callback == null && f.age == f.life) {
                  stain(f.f.b, 2.2F);
               }

               if (f.age >= f.life) {
                  EFFECTS.remove(f);
               }
            }

            Iterator<CFx.Splat> it = SPLATS.iterator();

            while (it.hasNext()) {
               if (++it.next().age > 500) {
                  it.remove();
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
         Vec3 subject = cut.subject(e.renderTickTime);
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
      if (CShaders.ready()) {
         if (e.getStage() == Stage.AFTER_LEVEL) {
            Impact.render(e.getPartialTick());
         } else if (e.getStage() == Stage.AFTER_PARTICLES) {
            Minecraft mc = Minecraft.getInstance();
            Matrix4f view = e.getPoseStack().last().pose();
            Matrix4f proj = e.getProjectionMatrix();
            lastView = new Matrix4f(view);
            lastProj = new Matrix4f(proj);
            float partial = e.getPartialTick();
            Vec3 camPos = e.getCamera().getPosition();
            Gfx.setCamera(view, mc.gameRenderer.getDepthFar() * 0.9F);
            float time = (float)(mc.level.getGameTime() % 100000L) + partial;
            Gfx.f(CShaders.blood, "Time", time);
            Gfx.f(CShaders.energy, "Time", time);
            CFx.Draw d = new CFx.Draw(camPos, view, proj, time);

            for (CFx.Eff f : EFFECTS) {
               if (f instanceof CFx.Cut c) {
                  c.renderWorld(d, partial);
               }
            }

            if (!SPLATS.isEmpty()) {
               d.begin();

               for (CFx.Splat s : SPLATS) {
                  float a = Mth.clamp((float)(500 - s.age) / 80.0F, 0.0F, 1.0F) * Mth.clamp(((float)s.age + partial) / 3.0F, 0.0F, 1.0F);
                  float grow = Math.min(1.0F, ((float)s.age + partial) / 4.0F);
                  d.flatQuad(s.pos, s.size * (0.5F + 0.5F * grow), (float)s.seed * 0.7F, DARK, 0.92F * a);
               }

               d.end(3, 3.0F);
            }

            for (CFx.Eff fx : EFFECTS) {
               if (!(fx instanceof CFx.Cut)) {
                  fx.render(d, partial);
               }
            }

            for (Entity en : mc.level.entitiesForRendering()) {
               boolean own = en == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
               Vec3 pos = en.getPosition(partial);
               int lvl = scaleLevel(en);
               if (lvl > 0 && !own) {
                  Random r = new Random((long)(time / 3.0F) * 31L + (long)en.getId());
                  int puffs = lvl >= 2 ? 6 : 2;

                  for (int i = 0; i < puffs; i++) {
                     float ph = (time * 0.04F + (float)i / (float)puffs) % 1.0F;
                     Vec3 p = pos.add((double)Mth.sin((float)i * 2.1F) * 0.35, 0.6 + (double)ph * 2.2, (double)Mth.cos((float)i * 2.1F) * 0.35);
                     d.steam(p, 0.35F + ph * (lvl >= 2 ? 0.9F : 0.5F), (1.0F - ph) * (lvl >= 2 ? 0.55F : 0.3F), (float)i);
                  }

                  d.glow(pos.add(0.0, 1.0, 0.0), lvl >= 2 ? 1.8F : 1.1F, GLOW, lvl >= 2 ? 0.35F : 0.18F);

                  for (int i = 0; i < (lvl >= 2 ? 3 : 1); i++) {
                     Vec3 a = pos.add(r.nextGaussian() * 0.3, 0.3 + (double)r.nextFloat() * 1.4, r.nextGaussian() * 0.3);
                     d.tube(a, a.add(0.0, 0.5 + (double)r.nextFloat() * 0.6, 0.0), 0.03F, BRIGHT, 0.7F, 1);
                  }
               }

               Long conv = CONVERGED.get(en.getId());
               if (conv != null && gameTime() < conv && !own) {
                  Vec3 side = en.getViewVector(partial).cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                  Vec3 p = pos.add(0.0, 1.05, 0.0).add(side.scale(0.45));
                  d.sphere(p, 0.16F, 10, BLOOD, 1.0F);
                  d.glow(p, 0.6F + 0.1F * Mth.sin(time * 0.5F), GLOW, 0.5F);
               } else if (conv != null && gameTime() < conv && own) {
                  Vec3 look = en.getViewVector(partial);
                  Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                  Vec3 p = en.getEyePosition(partial).add(look.scale(2.4)).add(side.scale(0.9)).add(0.0, -0.9, 0.0);
                  d.sphere(p, 0.2F, 10, BLOOD, 1.0F);
                  d.glow(p, 0.7F, GLOW, 0.35F);
               }
            }
         }
      }
   }

   static CFx.Cut activeWorld(int kind) {
      Minecraft mc = Minecraft.getInstance();

      for (CFx.Eff f : EFFECTS) {
         if (f instanceof CFx.Cut c
            && c.f.kind == kind
            && c.worldAlpha(0.0F) > 0.01F
            && mc.gameRenderer.getMainCamera().getPosition().distanceTo(c.f.a) < (double)(c.f.f + 6.0F)) {
            return c;
         }
      }

      return null;
   }

   @SubscribeEvent
   public static void onFogColor(ComputeFogColor e) {
      CFx.Cut c = activeWorld(9);
      float k = c != null ? c.worldAlpha((float)e.getPartialTick()) : 0.0F;
      CFx.Cut r = activeWorld(10);
      float kr = r != null ? r.worldAlpha((float)e.getPartialTick()) * 0.8F : 0.0F;
      float m = Math.max(k, kr);
      if (m > 0.0F) {
         float tr = k >= kr ? 0.2F : 0.24F;
         float tg = 0.0F;
         float tb = k >= kr ? 0.02F : 0.03F;
         e.setRed(Mth.lerp(m, e.getRed(), tr));
         e.setGreen(Mth.lerp(m, e.getGreen(), tg));
         e.setBlue(Mth.lerp(m, e.getBlue(), tb));
      }
   }

   @SubscribeEvent
   public static void onFog(RenderFog e) {
      CFx.Cut c = activeWorld(9);
      CFx.Cut r = activeWorld(10);
      if (c != null || r != null) {
         float k = c != null ? c.worldAlpha((float)e.getPartialTick()) : r.worldAlpha((float)e.getPartialTick()) * 0.7F;
         float far = c != null ? 14.0F : 60.0F;
         e.setNearPlaneDistance(Mth.lerp(k, e.getNearPlaneDistance(), c != null ? 0.0F : 4.0F));
         e.setFarPlaneDistance(Mth.lerp(k, e.getFarPlaneDistance(), far));
         e.setCanceled(true);
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
      CFx.Cut rain = activeWorld(10);
      if (rain != null) {
         float k = rain.worldAlpha(partial);
         g.fill(0, 0, w, h, argb(0.16F * k, 4194310));
      }

      CFx.Cut dom = activeWorld(9);
      if (dom != null) {
         float k = dom.worldAlpha(partial);
         g.fillGradient(0, 0, w, h / 3, argb(0.5F * k, 655362), 0);
         g.fillGradient(0, h - h / 3, w, h, 0, argb(0.45F * k, 3801094));
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

   static boolean isChoso(Player p) {
      return ClientSorcerers.of(p) == Sorcerer.CHOSO || scaleLevel(p) > 0;
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

   static final class Cut extends CFx.Eff {
      float fov;
      float roll;
      final Vec3 fwd;
      final Vec3 right;

      Cut(Net.Fx f) {
         super(f, switch (f.kind) {
            case 9, 10 -> f.dur;
            default -> 62;
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
            case 9 -> 80;
            case 10 -> 106;
            default -> 60;
         };
      }

      Vec3 subject(float partial) {
         Entity c = Minecraft.getInstance().level.getEntity(this.f.caster);
         return (c != null ? c.getPosition(partial) : this.f.a).add(0.0, 1.4, 0.0);
      }

      float worldAlpha(float partial) {
         float t = (float)this.age + partial;
         if (this.f.kind == 9) {
            return Mth.clamp((t - 34.0F) / 30.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.life - t) / 20.0F, 0.0F, 1.0F);
         } else {
            return this.f.kind == 10 ? Mth.clamp((t - 20.0F) / 40.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.life - t) / 30.0F, 0.0F, 1.0F) : 0.0F;
         }
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
            if (t < 26.0) {
               double k = t / 26.0;
               eye = head.add(this.fwd.scale(1.3 - 0.3 * k)).add(this.right.scale(0.15));
               look = head;
               fv = 38.0;
            } else {
               double k = (double)CFx.smooth((float)((t - 26.0) / 40.0));
               eye = o.add(this.fwd.scale(-5.5 - 2.0 * k)).add(this.right.scale(0.8)).add(0.0, 0.6 + 0.8 * k, 0.0);
               look = head.add(this.fwd.scale(4.0)).add(0.0, 1.2, 0.0);
               fv = 62.0 + 8.0 * k;
               rl = -3.0 * k;
            }
         } else if (this.f.kind == 10) {
            if (t < 30.0) {
               eye = o.add(this.fwd.scale(3.2)).add(this.right.scale(-1.2)).add(0.0, 0.3, 0.0);
               look = head.add(0.0, 1.0 + t * 0.03, 0.0);
               fv = 58.0;
               rl = 6.0;
            } else if (t < 62.0) {
               double k = (double)CFx.smooth((float)((t - 30.0) / 32.0));
               eye = o.add(this.fwd.scale(6.0 + 4.0 * k)).add(this.right.scale(3.0)).add(0.0, 1.0 + 18.0 * k, 0.0);
               look = o.add(0.0, 6.0 + 24.0 * k, 0.0);
               fv = 70.0;
            } else {
               double k = (double)CFx.smooth((float)((t - 62.0) / 22.0));
               eye = head.add(this.fwd.scale(2.2 + k)).add(this.right.scale(0.5)).add(0.0, -0.5, 0.0);
               look = head.add(0.0, 0.6 + 0.6 * k, 0.0);
               fv = 48.0 + 10.0 * k;
               rl = -4.0 * k;
            }
         } else {
            double a = t * 0.06;
            Vec3 rad = this.fwd.scale(Math.cos(a)).add(this.right.scale(Math.sin(a)));
            if (t < 36.0) {
               eye = head.add(rad.scale(1.4)).add(0.0, -0.1, 0.0);
               look = head;
               fv = 40.0;
               rl = 4.0 * Math.sin(a);
            } else {
               double k = (double)CFx.smooth((float)((t - 36.0) / 18.0));
               eye = o.add(this.fwd.scale(2.2 + 1.5 * k)).add(0.0, 0.5, 0.0);
               look = head.add(0.0, -0.2, 0.0);
               fv = 45.0 + 15.0 * k;
            }
         }

         return new double[]{eye.x, eye.y, eye.z, look.x, look.y, look.z, fv, rl};
      }

      void onAge() {
         Minecraft mc = Minecraft.getInstance();
         boolean me = CFx.cut == this || mc.player != null && mc.player.position().distanceTo(this.f.a) < (double)(this.f.f + 6.0F);
         if (me) {
            if (this.f.kind == 9) {
               if (this.age == 56) {
                  Impact.hit(5, 3, 0, 0.5F, 0.5F);
               }

               if (this.age == 59) {
                  Impact.hit(6, 2, 0, 0.5F, 0.5F);
               }

               if (this.age == 61) {
                  Impact.hit(2, 2, 10, 0.5F, 0.5F);
                  CFx.shake = Math.max(CFx.shake, 2.0F);
               }
            } else if (this.f.kind == 10) {
               if (this.age == 76) {
                  Impact.hit(0, 3, 0, 0.5F, 0.3F);
               }

               if (this.age == 79) {
                  Impact.hit(6, 2, 0, 0.5F, 0.3F);
               }

               if (this.age == 81) {
                  Impact.hit(2, 2, 12, 0.5F, 0.3F);
                  CFx.shake = Math.max(CFx.shake, 2.4F);
               }

               if (this.age > 84 && this.age % 30 == 0) {
                  CFx.shake = Math.max(CFx.shake, 0.8F);
               }
            } else {
               if (this.age == 48) {
                  Impact.hit(0, 3, 0, 0.5F, 0.55F);
               }

               if (this.age == 51) {
                  Impact.hit(2, 2, 8, 0.5F, 0.55F);
                  CFx.shake = Math.max(CFx.shake, 1.8F);
               }
            }
         }
      }

      @Override
      void render(CFx.Draw d, float partial) {
      }

      void renderWorld(CFx.Draw d, float partial) {
         float t = (float)this.age + partial;
         Minecraft mc = Minecraft.getInstance();
         Entity caster = mc.level.getEntity(this.f.caster);
         Vec3 o = this.f.a;
         if (this.f.kind == 9) {
            float wa = this.worldAlpha(partial);
            float r = this.f.f;
            if (wa > 0.01F) {
               d.sphere(o, r + 3.0F, 11, CFx.BLOOD, wa);
               Random rr = new Random(91L);
               d.begin();

               for (int i = 0; i < 90; i++) {
                  float a = rr.nextFloat() * (float) (Math.PI * 2);
                  float dist = 5.0F + rr.nextFloat() * (r - 3.0F);
                  Vec3 base = CFx.ground(o.add((double)(Mth.cos(a) * dist), 0.0, (double)(Mth.sin(a) * dist))).add(0.0, -0.5, 0.0);
                  d.curtain(
                     base,
                     0.6F + rr.nextFloat() * 1.6F,
                     18.0F + rr.nextFloat() * 16.0F,
                     i % 4 == 0 ? CFx.BRIGHT : CFx.BLOOD,
                     wa * (0.5F + rr.nextFloat() * 0.5F)
                  );
               }

               d.end(7, 4.0F);

               for (int i = 0; i < 14; i++) {
                  float a = (float)i * 0.45F + t * 0.004F;
                  float dist = 3.0F + (float)(i * 7 % 13) * 1.1F;
                  d.steam(CFx.ground(o.add((double)(Mth.cos(a) * dist), 0.0, (double)(Mth.sin(a) * dist))).add(0.0, 0.6, 0.0), 3.5F, 0.35F * wa, (float)i);
               }

               Random sp = new Random(7L);

               for (int i = 0; i < 40; i++) {
                  Vec3 p = o.add(
                     ((double)sp.nextFloat() - 0.5) * (double)r * 1.6,
                     (double)((sp.nextFloat() * 14.0F + t * 0.03F) % 14.0F),
                     ((double)sp.nextFloat() - 0.5) * (double)r * 1.6
                  );
                  d.glow(p, 0.08F, new float[]{1.0F, 0.8F, 0.8F}, 0.8F * wa);
               }
            }

            if (caster != null && t > 16.0F) {
               float al = Mth.clamp((t - 16.0F) / 10.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.life - t) / 20.0F, 0.0F, 1.0F);
               boolean own = caster == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
               if (!own) {
                  Vec3 cp = caster.getPosition(partial);
                  float yaw = caster.getViewYRot(partial) * (float) (Math.PI / 180.0);
                  Vec3 side = new Vec3((double)Mth.cos(yaw), 0.0, (double)Mth.sin(yaw));
                  Vec3 front = new Vec3((double)(-Mth.sin(yaw)), 0.0, (double)Mth.cos(yaw));
                  Vec3 lh = cp.add(side.scale(1.25)).add(0.0, 1.38, 0.0);
                  Vec3 rh = cp.add(side.scale(-1.25)).add(0.0, 1.38, 0.0);

                  for (int loop = 0; loop < 2; loop++) {
                     List<Vec3> pts = new ArrayList<>();

                     for (int i = 0; i <= 24; i++) {
                        float s = (float)i / 24.0F;
                        float sag = Mth.sin((float) Math.PI * s);
                        pts.add(
                           lh.lerp(rh, (double)s)
                              .add(front.scale((double)sag * (loop == 0 ? 0.5 : -0.35)))
                              .add(0.0, (double)(-sag) * (loop == 0 ? 1.15 : 0.8) + (double)Mth.sin(t * 0.15F + s * 6.0F) * 0.04, 0.0)
                        );
                     }

                     d.path(pts, 0.06F, 0.06F, CFx.BLOOD, al, 1, (float)(loop + 20));
                  }
               }
            }
         } else if (this.f.kind == 10) {
            float wax = this.worldAlpha(partial);
            if (t < 94.0F) {
               float rise = Mth.clamp(t / 40.0F, 0.0F, 1.0F);
               float al = Mth.clamp((94.0F - t) / 12.0F, 0.0F, 1.0F);

               for (int i = 0; i < 12; i++) {
                  float a = (float)i * (float) (Math.PI * 2) / 12.0F + t * 0.01F;
                  Vec3 base = CFx.ground(o.add((double)Mth.cos(a) * 3.5, 0.0, (double)Mth.sin(a) * 3.5));
                  List<Vec3> pts = new ArrayList<>();

                  for (int j = 0; j <= 10; j++) {
                     float s = (float)j / 10.0F * rise;
                     float tw = s * 4.0F + t * 0.08F;
                     pts.add(base.add((double)(Mth.cos(a + tw) * s) * 2.5, (double)(s * 32.0F), (double)(Mth.sin(a + tw) * s) * 2.5));
                  }

                  d.path(pts, 0.3F, 0.12F, CFx.BLOOD, al, 1, (float)i);
               }
            }

            float cloud = Mth.clamp((t - 30.0F) / 40.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.life - t) / 30.0F, 0.0F, 1.0F);
            if (cloud > 0.01F) {
               Random cr = new Random(5L);

               for (int i = 0; i < 26; i++) {
                  float a = cr.nextFloat() * (float) (Math.PI * 2);
                  float dist = (float)Math.sqrt((double)cr.nextFloat()) * this.f.f;
                  Vec3 p = o.add((double)(Mth.cos(a) * dist), 32.0 + cr.nextGaussian() * 1.5, (double)(Mth.sin(a) * dist));
                  d.blob(p, (5.0F + cr.nextFloat() * 5.0F) * (0.5F + 0.5F * cloud), CFx.DARK, 0.85F * cloud, (float)i);
               }

               d.glow(o.add(0.0, 30.0, 0.0), this.f.f * 0.8F, CFx.GLOW, 0.2F * cloud);
            }

            float rain = Mth.clamp((t - 84.0F + 6.0F) / 8.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.life - t) / 25.0F, 0.0F, 1.0F);
            if (rain > 0.01F) {
               Random rr = new Random(1234L);
               d.begin();

               for (int i = 0; i < 2600; i++) {
                  float a = rr.nextFloat() * (float) (Math.PI * 2);
                  float dist = (float)Math.sqrt((double)rr.nextFloat()) * this.f.f;
                  float ph = rr.nextFloat();
                  double x = o.x + (double)(Mth.cos(a) * dist);
                  double z = o.z + (double)(Mth.sin(a) * dist);
                  if (!((x - d.cam.x) * (x - d.cam.x) + (z - d.cam.z) * (z - d.cam.z) > 2500.0)) {
                     float fall = (t * (0.07F + (float)(i % 7) * 0.004F) + ph) % 1.0F;
                     double y = o.y + 32.0 - (double)(fall * 40.0F);
                     Vec3 p = new Vec3(x, y, z);
                     d.seg(p.add(0.0, 2.2, 0.0), p, 0.045F, 0.065F, i % 4 == 0 ? CFx.BRIGHT : CFx.BLOOD, rain);
                  }
               }

               d.end(1, 9.0F);
               Random sr = new Random((long)(t / 2.0F));

               for (int ix = 0; ix < 60; ix++) {
                  Vec3 g = CFx.ground(d.cam.add(sr.nextGaussian() * 9.0, 0.0, sr.nextGaussian() * 9.0));
                  d.blob(g.add(0.0, 0.1, 0.0), 0.12F + sr.nextFloat() * 0.1F, CFx.BLOOD, rain * 0.8F, (float)ix);
               }
            }
         } else {
            if (caster == null) {
               return;
            }

            Vec3 cp = caster.getPosition(partial);
            float k = Mth.clamp(t / 56.0F, 0.0F, 1.0F);

            for (int ix = 0; ix < 10; ix++) {
               float ph = (t * 0.05F + (float)ix / 10.0F) % 1.0F;
               float a = (float)ix * 2.4F;
               d.steam(
                  cp.add((double)Mth.cos(a) * (0.3 + (double)ph), 0.5 + (double)(ph * 3.0F), (double)Mth.sin(a) * (0.3 + (double)ph)),
                  0.4F + ph * 1.4F,
                  (1.0F - ph) * 0.6F * k,
                  (float)ix
               );
            }

            d.glow(cp.add(0.0, 1.0, 0.0), 1.5F + k * 2.0F, CFx.GLOW, 0.3F + 0.3F * k);
            if (t > 48.0F && t < 60.0F) {
               float b = (t - 56.0F + 8.0F) / 12.0F;
               d.glow(cp.add(0.0, 1.0, 0.0), 2.0F + b * 12.0F, CFx.GLOW, (1.0F - b) * 0.9F);
            }
         }
      }

      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         String jp = null;
         String en = null;
         int col = 16726596;
         float s0 = 0.0F;
         float s1 = 0.0F;
         if (this.f.kind == 9) {
            boolean second = t > 30.0F;
            if (t > 6.0F && t < 76.0F) {
               jp = second ? "九相図血界" : "領域展開";
               en = second ? "KUSOZU BLOOD REALM" : "DOMAIN EXPANSION";
               s0 = second ? 30.0F : 6.0F;
               s1 = second ? 76.0F : 30.0F;
            }
         } else if (this.f.kind == 10) {
            if (t > 46.0F && t < 100.0F) {
               jp = "血雨";
               en = "BLOOD RAIN";
               s0 = 46.0F;
               s1 = 100.0F;
            }
         } else if (t > 14.0F && t < 58.0F) {
            jp = "赤鱗躍動・載";
            en = "FLOWING RED SCALE: STACK";
            s0 = 14.0F;
            s1 = 58.0F;
         }

         if (jp != null) {
            float in = CFx.smooth((t - s0) / 5.0F);
            float out = CFx.smooth((s1 - t) / 5.0F);
            float a = in * out;
            int slide = (int)((1.0F - in) * -60.0F + (1.0F - out) * 40.0F);
            float s = 3.6F;
            int cy = (int)((float)h * 0.42F);
            int bandH = (int)(s * 14.0F + 18.0F);
            g.fill(0, cy - bandH / 2, w, cy + bandH / 2, CFx.argb(0.66F * a, 524288));
            g.fill(0, cy + bandH / 2 - 2, w, cy + bandH / 2, CFx.argb(0.9F * a, col));
            g.fill(0, cy - bandH / 2, w, cy - bandH / 2 + 1, CFx.argb(0.6F * a, col));
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F + (float)slide, (float)cy - s * 7.0F, 0.0F);
            g.pose().scale(s, s, 1.0F);
            g.drawCenteredString(mc.font, Component.literal(jp), 0, 0, CFx.argb(a, 16777215));
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F - (float)slide * 0.5F, (float)cy + s * 3.5F, 0.0F);
            g.pose().scale(1.3F, 1.3F, 1.0F);
            g.drawCenteredString(mc.font, Component.literal(en), 0, 0, CFx.argb(a, col));
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
         ShaderInstance sh = CShaders.blood;
         Gfx.f(sh, "Mode", (float)mode);
         Gfx.f(sh, "Seed", seed);
         Gfx.f(sh, "Alpha", 1.0F);
         Gfx.flush(sh, this.view, this.proj, true);
      }

      void blob(Vec3 at, float half, float[] c, float a, float seed) {
         this.begin();
         Gfx.billboard(at.x - this.cam.x, at.y - this.cam.y, at.z - this.cam.z, half, seed, c[0], c[1], c[2], a);
         this.end(0, seed);
      }

      void glow(Vec3 at, float half, float[] c, float a) {
         this.begin();
         Gfx.billboard(at.x - this.cam.x, at.y - this.cam.y, at.z - this.cam.z, half, 0.0F, c[0], c[1], c[2], a);
         this.end(4, 0.0F);
      }

      void steam(Vec3 at, float half, float a, float seed) {
         this.begin();
         Gfx.billboard(at.x - this.cam.x, at.y - this.cam.y, at.z - this.cam.z, half, seed, CFx.STEAM[0], CFx.STEAM[1], CFx.STEAM[2], a);
         this.end(5, seed);
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

      void tube(Vec3 a, Vec3 b, float w, float[] c, float al, int mode) {
         this.begin();
         this.seg(a, b, w, w * 0.8F, c, al);
         this.end(mode, 1.0F);
      }

      void path(List<Vec3> pts, float w0, float w1, float[] c, float al, int mode, float seed) {
         if (pts.size() >= 2) {
            int n = pts.size();
            float[] p = new float[n * 3];
            float[] w = new float[n];
            float[] u = new float[n];

            for (int i = 0; i < n; i++) {
               Vec3 v = pts.get(i).subtract(this.cam);
               p[i * 3] = (float)v.x;
               p[i * 3 + 1] = (float)v.y;
               p[i * 3 + 2] = (float)v.z;
               float k = (float)i / (float)(n - 1);
               w[i] = Mth.lerp(k, w0, w1);
               u[i] = k;
            }

            this.begin();
            Gfx.ribbon(p, w, u, c[0], c[1], c[2], al);
            this.end(mode, seed);
         }
      }

      void quad(Vec3 c, Vec3 ax, Vec3 ay, float[] col, float al) {
         Vec3 o = c.subtract(this.cam);
         float[][] cs = new float[][]{{-1.0F, -1.0F, 0.0F, 0.0F}, {1.0F, -1.0F, 1.0F, 0.0F}, {1.0F, 1.0F, 1.0F, 1.0F}, {-1.0F, 1.0F, 0.0F, 1.0F}};

         for (float[] k : cs) {
            Vec3 p = o.add(ax.scale((double)k[0])).add(ay.scale((double)k[1]));
            Gfx.v((float)p.x, (float)p.y, (float)p.z, k[2], k[3], col[0], col[1], col[2], al);
         }
      }

      void flatQuad(Vec3 at, float half, float rot, float[] c, float al) {
         Vec3 ax = new Vec3((double)Mth.cos(rot), 0.0, (double)Mth.sin(rot)).scale((double)half);
         Vec3 ay = new Vec3((double)(-Mth.sin(rot)), 0.0, (double)Mth.cos(rot)).scale((double)half * 0.8);
         this.quad(at, ax, ay, c, al);
      }

      void sphere(Vec3 at, float radius, int mode, float[] c, float a) {
         ShaderInstance sh = CShaders.blood;
         Gfx.f(sh, "Mode", (float)mode);
         Gfx.f(sh, "Seed", 5.0F);
         Gfx.f(sh, "Alpha", a);
         Matrix4f mv = new Matrix4f(this.view)
            .translate((float)(at.x - this.cam.x), (float)(at.y - this.cam.y), (float)(at.z - this.cam.z))
            .rotateY(this.time * 0.05F)
            .scale(radius);
         Gfx.meshBlend(CShaders.sphere(), sh, mv, this.proj);
      }

      void curtain(Vec3 base, float halfW, float height, float[] c, float al) {
         Vec3 o = base.subtract(this.cam);
         Gfx.upright(o.x, o.y, o.z, halfW, height, c[0], c[1], c[2], al);
      }
   }

   static class Eff {
      final Net.Fx f;
      final int life;
      final Runnable callback;
      int age;
      Vec3 tip;
      Vec3 prevTip;
      Vec3 tipDir;
      Vec3 prevDir;
      int tipAge;
      float stopT = -1.0F;

      Eff(Net.Fx f, int life) {
         this(f, life, null);
      }

      Eff(Net.Fx f, int life, Runnable callback) {
         this.f = f;
         this.life = life;
         this.callback = callback;
      }

      void render(CFx.Draw d, float partial) {
         if (this.callback == null) {
            float t = (float)this.age + partial;
            float fade = Mth.clamp(1.0F - t / (float)this.life, 0.0F, 1.0F);
            Minecraft mc = Minecraft.getInstance();
            Entity caster = mc.level == null ? null : mc.level.getEntity(this.f.caster);
            boolean own = caster != null && caster == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            switch (this.f.kind) {
               case 0:
               case 6:
               case 12:
                  float s = this.f.kind == 12 ? this.f.f * 0.8F : (this.f.kind == 6 ? this.f.f * 0.6F : 0.6F + this.f.f * 0.5F);
                  Random r = new Random((long)(this.f.a.x * 977.0 + this.f.a.z * 131.0 + this.f.a.y * 7.0));
                  int n = this.f.kind == 12 ? 22 : 10 + (int)(this.f.f * 4.0F);

                  for (int i = 0; i < n; i++) {
                     Vec3 dirx = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.8 + 0.3, r.nextGaussian())
                        .normalize()
                        .add(this.f.b.scale(this.f.kind == 0 ? 0.7 : 0.3))
                        .normalize();
                     float sp = (0.15F + r.nextFloat() * 0.25F) * s;
                     Vec3 px = this.f.a.add(dirx.scale((double)(sp * t))).add(0.0, -0.02 * (double)t * (double)t, 0.0);
                     float size = (0.06F + r.nextFloat() * 0.12F) * (0.7F + s * 0.4F) * (0.4F + 0.6F * fade);
                     d.blob(px, size, i % 3 == 0 ? CFx.BRIGHT : CFx.BLOOD, Math.min(1.0F, fade * 2.0F), (float)i);
                     if (i % 2 == 0) {
                        d.tube(px.subtract(dirx.scale((double)sp * 1.6)), px, size * 0.5F, CFx.BLOOD, fade, 1);
                     }
                  }

                  if (this.f.kind == 12) {
                     float kx = Mth.clamp(t / 6.0F, 0.0F, 1.0F);
                     d.blob(this.f.a, s * (0.6F + kx * 1.6F), CFx.DARK, (1.0F - kx) * 0.9F, 3.0F);
                     d.glow(this.f.a, s * (1.5F + kx * 3.0F), CFx.GLOW, (1.0F - kx) * 0.8F);
                  } else {
                     d.blob(this.f.a, 0.25F * s * (1.0F + t * 0.1F), CFx.BRIGHT, fade * 0.8F, 1.0F);
                  }
                  break;
               case 1: {
                  int step = (int)this.f.f;
                  Vec3 look = this.f.b.normalize();
                  Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                  Vec3 up = right.cross(look).normalize();
                  float tilt = step == 0 ? 0.25F : (step == 1 ? -0.35F : 1.45F);
                  Vec3 ax = right.scale(Math.cos((double)tilt)).add(up.scale(Math.sin((double)tilt)));
                  float sweep = Mth.clamp(t / 3.0F, 0.0F, 1.0F);
                  float fromx = step == 1 ? 1.3F : -1.3F;
                  float to = -fromx;
                  List<Vec3> pts = new ArrayList<>();

                  for (int ixx = 0; ixx <= 14; ixx++) {
                     float kx = (float)ixx / 14.0F;
                     float ang = Mth.lerp(kx * sweep, fromx, to);
                     Vec3 pp = this.f.a.add(look.scale(Math.cos((double)ang) * 1.0 - 0.6)).add(ax.scale(Math.sin((double)ang) * 1.7));
                     pts.add(pp);
                  }

                  float a = fade * (own ? 0.8F : 1.0F);
                  d.path(pts, 0.02F, 0.22F * (step == 2 ? 1.4F : 1.0F), CFx.BLOOD, a, 1, (float)step);
                  d.path(pts, 0.01F, 0.07F, CFx.BRIGHT, a, 1, (float)(step + 3));
                  d.glow(pts.get(pts.size() / 2), 1.6F, CFx.GLOW, 0.3F * a);
                  Vec3 tip = pts.get(pts.size() - 1);

                  for (int ixx = 0; ixx < 5; ixx++) {
                     Vec3 px = tip.add(ax.scale(-0.2 * (double)ixx)).add(look.scale(0.15 * (double)t)).add(0.0, -0.03 * (double)t * (double)t, 0.0);
                     d.blob(px, 0.06F * fade + 0.02F, CFx.BLOOD, fade, (float)ixx);
                  }
                  break;
               }
               case 2:
               case 16: {
                  Vec3 p = this.f.a.add(this.f.b.scale((double)t));
                  Vec3 back = this.f.b.normalize().scale(-1.0);
                  boolean nova = this.f.kind == 16;
                  float size = nova ? 0.26F * this.f.f : 0.34F;
                  float a = Math.min(1.0F, t / 2.0F);
                  if (own && t < 2.5F) {
                     a *= t / 2.5F;
                  }

                  d.glow(p, size * 3.2F, CFx.GLOW, 0.3F * a);
                  d.blob(p, size, nova ? CFx.BLOOD : CFx.DARK, a, (float)this.f.other);
                  d.blob(p.add(back.scale((double)size * 0.3)), size * 0.55F, CFx.BRIGHT, a * 0.5F, (float)(this.f.other + 1));
                  d.tube(p.add(back.scale((double)Math.min(t, 4.0F) * this.f.b.length() * 0.7)), p, size * 0.55F, CFx.BLOOD, a * 0.8F, 1);
                  if (nova) {
                     d.glow(p, size * 4.0F, CFx.GLOW, 0.45F * a);
                  }

                  for (int ixx = 0; ixx < 3; ixx++) {
                     float dt = (t + (float)ixx * 1.3F) % 4.0F;
                     Vec3 q = this.f.a.add(this.f.b.scale((double)(t - dt))).add(0.0, -0.04 * (double)dt * (double)dt, 0.0);
                     d.blob(q, 0.05F, CFx.BLOOD, a * (1.0F - dt / 4.0F), (float)ixx);
                  }
                  break;
               }
               case 3: {
                  Vec3 a;
                  if (caster != null) {
                     Vec3 look = caster.getViewVector(partial);
                     Vec3 sidex = look.cross(new Vec3(0.0, 1.0, 0.0));
                     if (sidex.lengthSqr() < 1.0E-4) {
                        new Vec3(1.0, 0.0, 0.0);
                     } else {
                        sidex.normalize();
                     }

                     a = caster.getEyePosition(partial).add(look.scale(0.9)).add(0.0, -0.35, 0.0);
                  } else {
                     a = this.f.a;
                  }

                  Vec3 head;
                  if (this.tip == null) {
                     head = a.lerp(this.f.b, Mth.clamp((double)(t / (float)Math.max(1, this.f.dur)), 0.0, 0.02));
                  } else {
                     head = this.prevTip.lerp(this.tip, (double)Mth.clamp(t - (float)this.tipAge, 0.0F, 1.0F));
                     if (this.tipDir != null) {
                        Vec3 dd = this.prevDir.lerp(this.tipDir, (double)Mth.clamp(t - (float)this.tipAge, 0.0F, 1.0F)).normalize();
                        head = a.add(dd.scale(head.distanceTo(a)));
                     }
                  }

                  float end = this.stopT >= 0.0F ? this.stopT : (float)this.life;
                  float al = Mth.clamp(1.0F - (t - end) / 10.0F, 0.0F, 1.0F) * Mth.clamp(t / 2.0F, 0.0F, 1.0F);
                  float w = this.f.f;
                  d.tube(a, head, w, CFx.DARK, al, 1);
                  d.tube(a, head, w * 0.55F, CFx.BLOOD, al, 1);
                  Gfx.f(CShaders.energy, "Mode", 1.0F);
                  Gfx.begin();
                  Vec3 g0 = a.subtract(d.cam);
                  Vec3 g1 = head.subtract(d.cam);
                  Gfx.ribbon(
                     new float[]{(float)g0.x, (float)g0.y, (float)g0.z, (float)g1.x, (float)g1.y, (float)g1.z},
                     new float[]{w * 2.6F, w * 2.6F},
                     new float[]{0.0F, 1.0F},
                     CFx.GLOW[0],
                     CFx.GLOW[1],
                     CFx.GLOW[2],
                     al * 0.8F
                  );
                  Gfx.flush(CShaders.energy, d.view, d.proj, true);
                  d.tube(a, head, w * 0.18F, new float[]{1.0F, 0.45F, 0.45F}, al, 1);
                  d.glow(head, 0.6F + w * 5.0F, CFx.GLOW, al * 0.9F);
                  d.blob(head, w * 1.6F + 0.06F, CFx.BRIGHT, al, 3.0F);
                  d.glow(a, 0.35F + w * 2.0F, CFx.GLOW, al * 0.6F);
                  break;
               }
               case 4: {
                  Vec3 p = this.f.a.add(this.f.b.scale((double)t));
                  Vec3 fwd = this.f.b.normalize();
                  Vec3 side = fwd.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                  Vec3 up = side.cross(fwd).normalize();
                  Vec3 ay = up.scale(Math.cos((double)this.f.f)).add(side.scale(Math.sin((double)this.f.f)));
                  float half = 1.15F;
                  float a = Math.min(1.0F, t / 2.0F);
                  d.begin();
                  d.quad(p, fwd.scale((double)half), ay.scale((double)half), CFx.BLOOD, a);
                  d.end(2, (float)this.f.other);
                  d.begin();
                  d.quad(p.subtract(fwd.scale(0.6)), fwd.scale((double)half * 0.95), ay.scale((double)half * 0.95), CFx.DARK, a * 0.4F);
                  d.end(2, (float)(this.f.other + 3));
                  d.glow(p, 1.3F, CFx.GLOW, 0.2F * a);
                  break;
               }
               case 5:
                  Entity v = mc.level.getEntity(this.f.other);
                  if (v == null) {
                     return;
                  }

                  Vec3 vp = v.getPosition(partial);
                  float hgt = v.getBbHeight();
                  float wid = v.getBbWidth() * 0.7F + 0.15F;
                  float shoot = Mth.clamp(t / 4.0F, 0.0F, 1.0F);
                  float al = Math.min(1.0F, ((float)this.life - t) / 8.0F);
                  Vec3 from = caster != null ? CFx.hands(caster, partial) : this.f.a;
                  if (t < 16.0F) {
                     for (int c = 0; c < 3; c++) {
                        List<Vec3> pts = new ArrayList<>();
                        Vec3 to = vp.add(0.0, (double)hgt * (0.3 + (double)c * 0.25), 0.0);

                        for (int ixx = 0; ixx <= 12; ixx++) {
                           float kx = (float)ixx / 12.0F * shoot;
                           Vec3 q = from.lerp(to, (double)kx)
                              .add(
                                 (double)Mth.sin(kx * 9.0F + (float)(c * 2)) * 0.25 * (double)(1.0F - kx),
                                 (double)Mth.cos(kx * 7.0F + (float)c) * 0.2 * (double)(1.0F - kx),
                                 0.0
                              );
                           pts.add(q);
                        }

                        d.path(pts, 0.09F, 0.09F, CFx.BLOOD, al * Mth.clamp((16.0F - t) / 6.0F, 0.0F, 1.0F), 6, (float)c);
                     }
                  }

                  if (t > 3.0F) {
                     List<Vec3> helix = new ArrayList<>();
                     float wrap = Mth.clamp((t - 3.0F) / 6.0F, 0.0F, 1.0F);

                     for (int ixx = 0; ixx <= 40; ixx++) {
                        float kx = (float)ixx / 40.0F * wrap;
                        float ang = kx * 18.0F;
                        helix.add(vp.add((double)(Mth.cos(ang) * wid), 0.1 + (double)(kx * hgt) * 0.95, (double)(Mth.sin(ang) * wid)));
                     }

                     d.path(helix, 0.1F, 0.1F, CFx.BLOOD, al, 6, 7.0F);
                     d.glow(vp.add(0.0, (double)hgt * 0.5, 0.0), hgt * 1.1F, CFx.GLOW, 0.25F * al);

                     for (int c = 0; c < 4; c++) {
                        float ang = (float)c * (float) (Math.PI / 2) + 0.6F;
                        Vec3 anchor = CFx.ground(vp.add((double)Mth.cos(ang) * 2.4, 0.0, (double)Mth.sin(ang) * 2.4));
                        Vec3 top = vp.add((double)(Mth.cos(ang) * wid), (double)hgt * 0.55, (double)(Mth.sin(ang) * wid));
                        d.path(List.of(anchor, anchor.lerp(top, 0.5).add(0.0, 0.2, 0.0), top), 0.09F, 0.09F, CFx.BLOOD, al * wrap, 6, (float)(c + 11));
                        d.blob(anchor, 0.25F, CFx.DARK, al * wrap, (float)c);
                     }
                  }
                  break;
               case 7: {
                  Vec3 c = caster != null ? CFx.hands(caster, partial) : this.f.a;
                  if (own && caster != null) {
                     Vec3 look = caster.getViewVector(partial);
                     c = caster.getEyePosition(partial).add(look.scale(2.4)).add(0.0, -0.5, 0.0);
                  }

                  float k = Mth.clamp(t / (float)Math.max(1, this.f.dur), 0.0F, 1.0F);
                  float big = this.f.f == 2.0F ? 1.4F : 1.0F;
                  float r = (0.55F - 0.38F * k) * big;
                  d.sphere(c, r, 10, CFx.BLOOD, 1.0F);
                  d.glow(c, (0.8F + 1.6F * k) * big, CFx.GLOW, 0.4F + 0.4F * k);

                  for (int ixx = 0; ixx < 7; ixx++) {
                     List<Vec3> pts = new ArrayList<>();
                     float base = (float)ixx * (float) (Math.PI * 2) / 7.0F + t * 0.35F;

                     for (int j = 0; j <= 10; j++) {
                        float s = (float)j / 10.0F;
                        float rad = (2.4F - s * 2.2F) * (1.0F - k * 0.5F);
                        float ang = base + s * 2.4F;
                        pts.add(c.add((double)(Mth.cos(ang) * rad), (double)(Mth.sin(ang * 1.3F + (float)ixx) * rad) * 0.5, (double)(Mth.sin(ang) * rad)));
                     }

                     d.path(pts, 0.03F, 0.08F, ixx % 2 == 0 ? CFx.BLOOD : CFx.BRIGHT, Math.min(1.0F, (1.0F - k) * 1.5F + 0.2F), 1, (float)ixx);
                  }
                  break;
               }
               case 8: {
                  Vec3 o = caster != null ? caster.getPosition(partial) : this.f.a;
                  float k = t / (float)this.life;
                  if (!own) {
                     d.glow(o.add(0.0, 1.0, 0.0), 1.5F + k * 3.0F, CFx.GLOW, (1.0F - k) * 0.7F);
                  }

                  for (int ixx = 0; ixx < 6; ixx++) {
                     float ax = (float)ixx * (float) (Math.PI * 2) / 6.0F;
                     d.steam(
                        o.add((double)Mth.cos(ax) * (0.5 + (double)k * 1.5), 0.4 + (double)k * 1.5, (double)Mth.sin(ax) * (0.5 + (double)k * 1.5)),
                        0.5F + k,
                        (1.0F - k) * 0.5F,
                        (float)ixx
                     );
                  }
               }
               case 9:
               case 10:
               case 14:
               case 15:
               default:
                  break;
               case 11:
                  Vec3 o = caster != null ? caster.getPosition(partial) : this.f.a;
                  int n = (int)this.f.f;
                  float form = Mth.clamp(t / 6.0F, 0.0F, 1.0F);

                  for (int ix = 0; ix < n; ix++) {
                     float ang = (float)ix * (float) (Math.PI * 2) / (float)n + t * 0.22F;
                     float rad = 1.9F * form;
                     Vec3 px = o.add(
                        (double)(Mth.cos(ang) * rad),
                        1.2 + (double)Mth.sin(t * 0.3F + (float)ix) * 0.25 + (double)(ix % 2) * 0.35,
                        (double)(Mth.sin(ang) * rad)
                     );
                     if (!own || !(px.distanceTo(d.cam) < 1.2)) {
                        d.blob(px, 0.22F * form, CFx.BLOOD, 1.0F, (float)ix);
                        d.glow(px, 0.9F * form, CFx.GLOW, 0.35F);
                     }
                  }

                  if (!own) {
                     d.glow(o.add(0.0, 1.2, 0.0), 2.5F * form, CFx.GLOW, 0.25F);
                  }
                  break;
               case 13: {
                  float up = Mth.clamp(t / 2.5F, 0.0F, 1.0F);
                  float sink = Mth.clamp(((float)this.life - t) / 6.0F, 0.0F, 1.0F);
                  float hgt = this.f.f * up * sink;
                  Random r = new Random((long)this.f.other * 31L + (long)this.f.a.x);
                  Vec3 g = CFx.ground(this.f.a);

                  for (int ixx = 0; ixx < 6; ixx++) {
                     Vec3 dirx = new Vec3(r.nextGaussian() * 0.35, 1.0, r.nextGaussian() * 0.35).normalize();
                     Vec3 base = g.add(r.nextGaussian() * 0.35, 0.0, r.nextGaussian() * 0.35);
                     d.path(
                        List.of(base, base.add(dirx.scale((double)hgt * 0.5)), base.add(dirx.scale((double)hgt * (0.8 + (double)r.nextFloat() * 0.4)))),
                        0.16F,
                        0.01F,
                        ixx % 2 == 0 ? CFx.BLOOD : CFx.DARK,
                        1.0F,
                        1,
                        (float)ixx
                     );
                  }

                  d.begin();
                  d.flatQuad(g, 1.3F, (float)this.f.other, CFx.DARK, 0.9F * sink);
                  d.end(3, (float)this.f.other);
                  break;
               }
               case 17: {
                  float k = Mth.clamp(t / (float)this.f.dur, 0.0F, 1.0F);
                  Vec3 head = this.f.a.lerp(this.f.b, (double)k);
                  Vec3 dir = this.f.b.subtract(this.f.a).normalize();
                  d.tube(head.subtract(dir.scale(4.0)), head, 0.18F, CFx.DARK, 1.0F, 1);
                  d.tube(head.subtract(dir.scale(3.5)), head, 0.07F, CFx.BRIGHT, 0.9F, 1);
                  d.tube(head.subtract(dir.scale(9.0)), head.subtract(dir.scale(3.0)), 0.08F, CFx.BLOOD, 0.6F, 1);
               }
            }
         }
      }
   }

   static final class Splat {
      final Vec3 pos;
      final float size;
      final int seed;
      int age;

      Splat(Vec3 pos, float size, int seed) {
         this.pos = pos;
         this.size = size;
         this.seed = seed;
      }
   }
}
