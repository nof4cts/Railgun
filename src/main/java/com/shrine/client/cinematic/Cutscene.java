package com.shrine.client.cinematic;

import com.jjk.mod.client.JjkPrefs;
import com.mahoraga.mod.client.MahoFx;
import com.shrine.client.Casts;
import com.shrine.common.Move;
import com.shrine.network.S2CCast;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class Cutscene {
   private static S2CCast active;
   private static Marker camera;
   private static CameraType savedCamera;
   private static int skipHeld;
   @Nullable
   private static Pose pose;
   private static float groundY;
   private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

   private Cutscene() {
   }

   /** Any cutscene is playing: Sukuna's, or Gojo's (other HUDs ask here to hide themselves). */
   public static boolean active() {
      return active != null || com.gojo.rework.client.cam.GDirector.active();
   }

   /** Sukuna's own cutscene is playing. */
   public static boolean running() {
      return active != null;
   }

   public static boolean isFor(S2CCast m) {
      return active == m;
   }

   @Nullable
   public static S2CCast cast() {
      return active;
   }

   @Nullable
   public static Pose pose() {
      return active == null ? null : pose;
   }

   public static void start(S2CCast msg) {
      if (!MahoFx.cutsceneActive()) {
         if (!JjkPrefs.skipCutscenes()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
               stop();
               active = msg;
               skipHeld = 0;
               groundY = (float)ground(msg.point.x, msg.point.z, msg.point.y);
               camera = new Marker(EntityType.MARKER, mc.level);
               camera.setPos(mc.player.getEyePosition());
               savedCamera = mc.options.getCameraType();
               mc.options.setCameraType(CameraType.FIRST_PERSON);
               mc.setCameraEntity(camera);
               if (msg.film) {
                  com.shrine.client.film.Film.start(msg);
               }
            }
         }
      }
   }

   public static void stop() {
      Minecraft mc = Minecraft.getInstance();
      com.shrine.client.film.Film.stop();
      if (active != null) {
         active = null;
         pose = null;
         if (mc.player != null) {
            mc.setCameraEntity(mc.player);
         }

         if (savedCamera != null) {
            mc.options.setCameraType(savedCamera);
         }

         savedCamera = null;
         camera = null;
      }
   }

   private static double ground(double x, double z, double fallback) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return fallback;
      } else {
         int h = mc.level.getHeight(Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
         return h <= mc.level.getMinBuildHeight() ? fallback : (double)h;
      }
   }

   private static Vec3 aboveGround(Vec3 p, double clearance) {
      double g = ground(p.x, p.z, (double)groundY);
      return p.y < g + clearance ? new Vec3(p.x, g + clearance, p.z) : p;
   }

   public static float time(float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || active == null) {
         return 0.0F;
      }
      // the film version's in-world shots begin where the film ends
      long base = active.startTick + (active.film && active.move == com.shrine.common.Move.FUGA ? (long)com.shrine.common.Timing.FUGA_FILM_SHIFT : 0L);
      return (float)mc.level.getGameTime() + partial - (float)base;
   }

   private static int end() {
      return switch (active.move) {
         case FUGA -> 175;
         case DOMAIN -> 150;
         case WORLD_SLASH -> 200;
         default -> 1;
      };
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      if (active != null) {
         if (mc.level != null && mc.player != null) {
            skipHeld = mc.options.keyShift.isDown() ? skipHeld + 1 : 0;
            if (time(0.0F) >= (float)end() || skipHeld >= 20) {
               stop();
            }
         } else {
            stop();
         }
      }
   }

   public static void frame(float partial) {
      if (active != null && camera != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
         }

         float t = Math.max(0.0F, time(partial));

         pose = switch (active.move) {
            case FUGA -> fuga(t, partial);
            case DOMAIN -> domain(t, partial);
            default -> worldSlash(t, partial);
         };
         Vec3 p = pose.pos();
         camera.setPos(p.x, p.y, p.z);
         camera.xo = p.x;
         camera.yo = p.y;
         camera.zo = p.z;
         camera.xOld = p.x;
         camera.yOld = p.y;
         camera.zOld = p.z;
         camera.setYRot(pose.yaw());
         camera.yRotO = pose.yaw();
         camera.setXRot(pose.pitch());
         camera.xRotO = pose.pitch();
      }
   }

   static float ease(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
   }

   static float easeOut(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      float u = 1.0F - x;
      return 1.0F - u * u * u;
   }

   static float span(float t, int a, int b) {
      return Mth.clamp((t - (float)a) / (float)(b - a), 0.0F, 1.0F);
   }

   private static Vec3 handheld(float t, double amount) {
      return new Vec3(
            (double)Mth.sin(t * 0.071F) * 0.6 + (double)Mth.sin(t * 0.23F) * 0.25,
            (double)Mth.sin(t * 0.053F + 1.3F) * 0.4 + (double)Mth.sin(t * 0.19F) * 0.2,
            (double)Mth.sin(t * 0.061F + 2.1F) * 0.6
         )
         .scale(amount);
   }

   private static Entity caster() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? null : mc.level.getEntity(active.casterId);
   }

   private static Vec3[] basis(Vec3 d) {
      Vec3 r = d.cross(new Vec3(0.0, 1.0, 0.0));
      if (r.lengthSqr() < 1.0E-6) {
         r = new Vec3(1.0, 0.0, 0.0);
      }

      r = r.normalize();
      return new Vec3[]{d.normalize(), r, r.cross(d).normalize()};
   }

   private static Pose fuga(float t, float partial) {
      Entity c = caster();
      Vec3 E = c != null ? c.getEyePosition(partial) : active.origin;
      Vec3 aim = c != null ? c.getViewVector(partial) : active.dir;
      Vec3[] b = basis(aim);
      Vec3 D = b[0];
      Vec3 R = b[1];
      Vec3 U = b[2];
      if (t < 30.0F) {
         float u = ease(span(t, 0, 30));
         Vec3 hand = Casts.fugaHand(c, active, partial);
         Vec3 pos = E.add(D.scale(1.25)).add(R.scale(0.95 - 0.15 * (double)u)).subtract(U.scale(0.15));
         return Pose.look(pos, hand, -4.0F + 2.0F * u, 42.0F - 7.0F * u);
      } else if (t < 48.0F) {
         float u = ease(span(t, 30, 48));
         Vec3 pos = E.add(R.scale(3.0 - 0.4 * (double)u)).add(D.scale(0.9 - 0.7 * (double)u)).add(U.scale(0.15));
         return Pose.look(pos, E.add(D.scale(0.9)).subtract(U.scale(0.12)), 0.0F, 48.0F);
      } else if (t < 80.0F) {
         float u = ease(span(t, 48, 80));
         float tense = span(t, 62, 80);
         Vec3 shake = new Vec3((double)Mth.sin(t * 2.3F), (double)Mth.sin(t * 3.1F + 1.0F), 0.0).scale(0.012 * (double)tense);
         Vec3 pos = E.subtract(D.scale(2.2 - 0.7 * (double)u)).subtract(R.scale(0.75)).add(U.scale(0.25)).add(shake);
         return Pose.look(pos, E.add(D.scale(25.0)), 3.0F * u, 54.0F - 10.0F * u);
      } else {
         Vec3 target = active.point;
         Vec3 from = Casts.fugaBow(c, active, partial);
         Vec3 path = target.subtract(from);
         Vec3 D2 = path.lengthSqr() < 1.0E-4 ? D : path.normalize();
         Vec3[] b2 = basis(D2);
         if (t < 89.0F) {
            float f = (t - 80.0F) / 9.0F;
            f = f * f * (0.4F + 0.6F * f);
            Vec3 head = from.add(path.scale((double)Mth.clamp(f, 0.0F, 1.0F)));
            Vec3 pos = head.subtract(D2.scale(7.0)).add(b2[2].scale(1.6)).add(b2[1].scale(1.2));
            return Pose.look(pos, head.add(D2.scale(12.0)), 0.0F, 80.0F);
         } else {
            float u = ease(span(t, 89, 175));
            double Rb = (double)active.size;
            Vec3 flatD = new Vec3(D2.x, 0.0, D2.z);
            flatD = flatD.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flatD.normalize();
            Vec3 flatR = flatD.cross(UP).normalize();
            Vec3 pos = target.subtract(flatD.scale(Rb * 3.3))
               .add(flatR.scale(Rb * 1.3))
               .add(0.0, Rb * (0.35 + 0.5 * (double)u), 0.0)
               .add(handheld(t, 0.6 + 2.5 * (double)(1.0F - u)));
            return Pose.look(aboveGround(pos, 4.0), target.add(0.0, Rb * (1.1 + 0.9 * (double)u), 0.0), 0.0F, 72.0F);
         }
      }
   }

   private static Pose domain(float t, float partial) {
      Entity c = caster();
      Vec3 E = c != null ? c.getEyePosition(partial) : active.origin;
      Vec3 aim = c != null ? c.getViewVector(partial) : active.dir;
      Vec3 flat = new Vec3(aim.x, 0.0, aim.z);
      Vec3 D = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
      Vec3 R = D.cross(UP).normalize();
      Vec3 P = active.point;
      double Rd = (double)active.size;
      if (t < 26.0F) {
         float u = ease(span(t, 0, 26));
         Vec3 pos = E.add(D.scale(1.35 - 0.2 * (double)u)).subtract(UP.scale(0.2)).add(R.scale(0.15));
         return Pose.look(pos, E.subtract(UP.scale(0.42)), 0.0F, 40.0F - 6.0F * u);
      } else if (t < 52.0F) {
         Vec3 pos = E.add(D.scale(0.9));
         return Pose.look(pos, E, 0.0F, 30.0F);
      } else if (t < 110.0F) {
         float u = ease(span(t, 52, 110));
         Vec3 a = E.subtract(D.scale(3.0)).add(UP.scale(1.2));
         Vec3 bpos = P.subtract(D.scale(Rd * 0.35)).add(UP.scale(Rd * 0.3)).add(R.scale(Rd * 0.1));
         Vec3 look = E.lerp(P.add(D.scale(Rd * 0.3)), (double)u);
         return Pose.look(aboveGround(a.lerp(bpos, (double)u), 2.0), look, -3.0F * u, 60.0F + 18.0F * u);
      } else {
         float u = ease(span(t, 110, 150));
         Vec3 pos = P.add(R.scale((double)(14.0F - 3.0F * u))).add(D.scale((double)(18.0F - 4.0F * u))).add(0.0, 1.2, 0.0).add(handheld(t, 0.35));
         return Pose.look(aboveGround(pos, 1.2), P.add(0.0, 1.6, 0.0), 0.0F, 55.0F - 6.0F * u);
      }
   }

   private static Pose worldSlash(float t, float partial) {
      Entity c = caster();
      Vec3 E = c != null ? c.getEyePosition(partial) : active.origin;
      Vec3 D = active.dir;
      Vec3 R = D.cross(UP).normalize();
      Vec3 U = UP;
      Vec3 P = active.point;
      if (t < 18.0F) {
         float u = ease(span(t, 0, 18));
         return Pose.look(E.add(D.scale(1.3 - 0.15 * (double)u)).subtract(U.scale(0.3)).add(R.scale(0.35)), E.subtract(U.scale(0.45)), 5.0F, 36.0F);
      } else if (t < 36.0F) {
         float u = ease(span(t, 18, 36));
         return Pose.look(E.add(D.scale(1.1 - 0.1 * (double)u)).subtract(R.scale(0.8)).subtract(U.scale(0.05)), E.subtract(U.scale(0.35)), -7.0F, 34.0F);
      } else if (t < 54.0F) {
         float u = ease(span(t, 36, 54));
         return Pose.look(E.add(D.scale(1.7)).subtract(U.scale(1.1 - 0.2 * (double)u)).add(R.scale(0.2)), E.add(U.scale(0.1)), 3.0F, 44.0F - 4.0F * u);
      } else if (t < 78.0F) {
         float u = ease(span(t, 54, 78));
         Vec3 pos = E.subtract(D.scale(3.2 - 1.0 * (double)u)).add(U.scale(1.0)).add(R.scale(0.5));
         return Pose.look(pos, E.add(D.scale(60.0)).add(0.0, 4.0, 0.0), 0.0F, 62.0F - 6.0F * u);
      } else if (t < 140.0F) {
         float u = easeOut(span(t, 78, 140));
         Vec3 pos = P.subtract(D.scale((double)(8.0F + 4.0F * u))).add(0.0, 3.0 + 2.5 * (double)u, 0.0).add(R.scale(0.02));
         float kick = t < 88.0F ? (float)Math.exp((double)(-(t - 78.0F)) / 3.0) * Mth.sin((t - 78.0F) * 2.2F) * 2.0F : 0.0F;
         return Pose.look(aboveGround(pos, 2.0), P.add(D.scale(220.0)).add(0.0, 12.0, 0.0), kick, 76.0F);
      } else {
         float u = ease(span(t, 140, 200));
         double L = (double)Math.min(active.size, 420.0F);
         Vec3 pos = P.add(D.scale(L * 0.06)).add(R.scale(L * 0.18)).add(0.0, L * 0.09 + (double)(30.0F * u), 0.0);
         return Pose.look(aboveGround(pos, 10.0), P.add(D.scale(L * (0.35 + 0.15 * (double)u))), 0.0F, 72.0F);
      }
   }

   @Nullable
   public static Object[] card(float partial) {
      if (active == null) {
         return null;
      } else {
         float t = time(partial);
         Move m = active.move;
         if (m == Move.FUGA && t >= 8.0F && t < 36.0F) {
            return new Object[]{"■ 開", "OPEN", 0, t - 8.0F};
         } else {
            if (m == Move.DOMAIN) {
               if (t >= 26.0F && t < 40.0F) {
                  return new Object[]{"領域展開", "DOMAIN EXPANSION", 1, t - 26.0F};
               }

               if (t >= 40.0F && t < 52.0F) {
                  return new Object[]{"伏魔御厨子", "MALEVOLENT SHRINE", 1, t - 40.0F};
               }
            }

            return m == Move.WORLD_SLASH && t >= 54.0F && t < 78.0F ? new Object[]{"世界を断つ斬撃", "WORLD-CUTTING SLASH", 0, t - 54.0F} : null;
         }
      }
   }
}
