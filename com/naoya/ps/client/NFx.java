package com.naoya.ps.client;

import com.naoya.ps.Net;
import com.naoya.ps.client.cine.Director;
import com.naoya.ps.client.fx.Light;
import com.naoya.ps.client.fx.Ribbon;
import com.naoya.ps.client.fx.ScreenFx;
import com.naoya.ps.client.fx.Shapes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class NFx {
   static final int PAPER = 15986660;
   static final int INK = 723726;
   static final int PALE = 15328208;
   static final int SKY = 12572927;
   static final int GOLD = 15913594;
   static final int WHITE = 16777215;
   static final Map<Integer, NFx.Frozen> FROZEN = new HashMap<>();
   private static final List<NFx.Mark> MARKS = new ArrayList<>();
   private static final List<NFx.Shard> SHARDS = new ArrayList<>();
   private static final Random R = new Random();
   private static final List<NFx.Pulse> PULSES = new ArrayList<>();

   private NFx() {
   }

   static float now(float partial) {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0.0F : (float)mc.level.getGameTime() + partial;
   }

   public static boolean frozen(Entity e) {
      NFx.Frozen f = FROZEN.get(e.getId());
      return f != null && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() < f.until;
   }

   public static void accept(Net.Fx m) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         long now = mc.level.getGameTime();
         Entity caster = mc.level.getEntity(m.caster());
         boolean mine = mc.player != null && m.caster() == mc.player.getId();
         MARKS.add(new NFx.Mark(m.kind(), m.a(), m.b(), m.f(), now, R.nextLong()));
         switch (m.kind()) {
            case 0:
               frames(m.caster(), m.a(), m.b(), m.dur());
               anim(caster, "dash");
               Light.ring(m.a().add(0.0, 1.0, 0.0), m.b().subtract(m.a()).normalize(), 0.4F, 2.6F, 0.2F, 16777215, 0.35F);
               if (mine) {
                  PULSES.add(new NFx.Pulse((float)now, (int[][])null, m.b(), 0.25F + 0.06F * m.f()));
               }
               break;
            case 1:
               anim(caster, "palm");
               Light.hit(m.b(), m.b().subtract(m.a()), 16777215, 1.4F);
               Light.ring(m.b(), m.b().subtract(m.a()).normalize(), 0.2F, 3.0F, 0.25F, 15986660, 0.4F);
               if (mine) {
                  PULSES.add(new NFx.Pulse((float)now, new int[][]{{5, 1}, {1, 1}}, m.b(), 0.6F));
               }
               break;
            case 2:
               FROZEN.put(m.other(), new NFx.Frozen(m.other(), now, now + (long)m.dur(), (R.nextFloat() - 0.5F) * 9.0F));
               Light.flare(m.b(), 2.2F, 16777215, 0.25F);
               break;
            case 3:
               FROZEN.remove(m.other());
               break;
            case 4:
               anim(caster, "barrage");
               break;
            case 5:
               Light.hit(m.a(), m.b(), 16777215, 0.5F + m.f() * 0.06F);
               break;
            case 6:
               frames(m.caster(), m.a(), m.b(), 10);
               Ghosts.add(m.caster(), m.a(), yawOf(caster), 1.4F, 0.75F);
               anim(caster, "step");
               break;
            case 7:
               frames(m.caster(), m.a(), m.b(), 24);
               anim(caster, "boom");
               Vec3 d = m.b().subtract(m.a()).normalize();

               for (int i = 0; i < 4; i++) {
                  Light.ring(
                     m.a().add(0.0, 1.0, 0.0).add(d.scale((double)i * 1.4)),
                     d,
                     0.5F + (float)i * 0.6F,
                     4.5F + (float)i * 1.5F,
                     0.3F,
                     16777215,
                     0.45F + (float)i * 0.08F
                  );
               }

               Light.spray(m.a().add(0.0, 0.3, 0.0), d.scale(-1.0).add(0.0, 0.4, 0.0), 0.9F, 30, 10.0F, 15328208, 0.8F, 0.06F);
               if (mine) {
                  PULSES.add(new NFx.Pulse((float)now, new int[][]{{5, 1}, {9, 1}, {1, 1}}, m.b(), 1.0F));
               }
               break;
            case 8:
               anim(caster, "trap");
               if (mine) {
                  PULSES.add(new NFx.Pulse((float)now, new int[][]{{1, 1}, {2, 1}}, m.a(), 0.7F));
               }
               break;
            case 9:
               FROZEN.remove(m.other());
               shatter(m.b(), m.f(), caster);
               if (mine || mc.player != null && mc.player.getId() == m.other()) {
                  PULSES.add(new NFx.Pulse((float)now, new int[][]{{2, 1}, {1, 1}, {9, 1}}, m.b(), 0.9F));
               }
               break;
            case 10:
               anim(caster, "ult");
               if (mine && Prefs.cutscenes) {
                  Director.play(Cine.ult(m, now));
               }
               break;
            case 11:
               frames(m.caster(), m.a(), m.b(), 24);
               Vec3 d11 = m.b().subtract(m.a()).normalize();
               Light.ring(m.b().add(0.0, 1.0, 0.0), d11, 0.6F, 6.0F + 2.0F * m.f(), 0.35F, 16777215, 0.5F);
               Light.ring(m.a().add(0.0, 1.0, 0.0), d11, 0.4F, 3.5F + m.f(), 0.25F, 12572927, 0.4F);
               break;
            case 12:
               Vec3 d12 = m.b();
               Light.hit(m.a(), d12, 16777215, 3.0F);

               for (int i = 0; i < 3; i++) {
                  Light.ring(m.a(), d12, 0.5F, (float)(9 + i * 4), 0.45F - (float)i * 0.1F, i == 1 ? 12572927 : 16777215, 0.6F + (float)i * 0.15F);
               }

               Light.spray(m.a(), d12, 0.6F, 60, 18.0F, 16777215, 0.9F, 0.08F);
               shatter(m.a(), 2.0F, caster);
               if (mc.player != null && mc.player.position().distanceTo(m.a()) < 40.0) {
                  PULSES.add(new NFx.Pulse((float)now, Cine.HIT, m.a(), 1.6F));
               }
         }
      }
   }

   private static float yawOf(Entity e) {
      return e == null ? 0.0F : e.getYRot();
   }

   private static void anim(Entity e, String name) {
      if (e instanceof AbstractClientPlayer p) {
         Anims.play(p, name);
      }
   }

   private static void frames(int who, Vec3 a, Vec3 b, int n) {
      Minecraft mc = Minecraft.getInstance();
      Entity e = mc.level == null ? null : mc.level.getEntity(who);
      float yaw = yawOf(e);
      Vec3 d12 = b.subtract(a);
      if (d12.horizontalDistanceSqr() > 0.001) {
         yaw = (float)Math.toDegrees(Math.atan2(-d12.x, d12.z));
      }

      int count = Math.max(2, Math.min(24, n));

      for (int i = 0; i < count; i++) {
         double k = (double)i / (double)(count - 1);
         float life = 0.25F + 0.5F * (float)k;
         float alpha = 0.32F + 0.4F * (float)k;
         Ghosts.add(who, a.lerp(b, k), yaw, life, alpha);
      }
   }

   private static void shatter(Vec3 c, float h, Entity by) {
      Vec3 n = c.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()).normalize();
      Vec3 right = n.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 up = right.cross(n).normalize();

      for (int i = 0; i < 46; i++) {
         NFx.Shard s = new NFx.Shard();
         double x = (R.nextDouble() - 0.5) * (double)h * 0.9;
         double y = (R.nextDouble() - 0.5) * (double)h * 1.25;
         s.pos = c.add(right.scale(x)).add(up.scale(y));
         Vec3 out = right.scale(x).add(up.scale(y)).normalize();
         s.vel = out.scale(3.0 + R.nextDouble() * 6.0).add(n.scale(-(1.0 + R.nextDouble() * 3.0))).add(0.0, 2.0 + R.nextDouble() * 3.0, 0.0);
         double ang = R.nextDouble() * Math.PI;
         s.u = right.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
         s.v = right.scale(-Math.sin(ang)).add(up.scale(Math.cos(ang)));
         s.size = 0.12F + R.nextFloat() * 0.3F;
         s.life = 0.7F + R.nextFloat() * 0.7F;
         s.dark = R.nextInt(4) == 0;
         SHARDS.add(s);
      }

      Light.flare(c, 3.5F, 16777215, 0.25F);
      Light.ring(c, n, 0.3F, 4.0F, 0.25F, 16777215, 0.35F);
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         FROZEN.clear();
         MARKS.clear();
         SHARDS.clear();
         PULSES.clear();
      } else {
         long now = mc.level.getGameTime();
         FROZEN.values().removeIf(f -> now > f.until + 2L || mc.level.getEntity(f.id) == null);
         MARKS.removeIf(m -> now - m.time > 40L);
         PULSES.removeIf(p -> (float)now - p.start > 30.0F);
         Ghosts.tick();
      }
   }

   public static void ribbons(List<Ribbon> out, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         float now = now(partial);
         Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

         for (NFx.Frozen f : FROZEN.values()) {
            Entity e = mc.level.getEntity(f.id);
            if (e != null && !(now > (float)f.until)) {
               panel(out, e.getBoundingBox(), cam, now - (float)f.start, (float)f.until - now, f.tilt);
            }
         }

         for (NFx.Mark m : MARKS) {
            float age = now - (float)m.time;
            switch (m.kind) {
               case 0:
               case 7:
               case 11:
                  speedLines(out, m, age, m.kind == 0 ? 1.0F : 1.8F);
                  break;
               case 1:
                  star(out, m.b, age, 1.8F, m.seed);
               case 2:
               case 3:
               case 4:
               case 6:
               case 10:
               default:
                  break;
               case 5:
                  if (age < 4.0F) {
                     star(out, m.a, age, 0.9F + m.f * 0.05F, m.seed);
                  }
                  break;
               case 8:
                  filmRing(out, m.a, m.f, age);
                  break;
               case 9:
                  star(out, m.b, age, 2.4F, m.seed);
                  break;
               case 12:
                  star(out, m.a, age, 6.0F, m.seed);
            }
         }

         float dt = mc.isPaused() ? 0.0F : mc.getDeltaFrameTime() / 20.0F;
         Iterator<NFx.Shard> it = SHARDS.iterator();

         while (it.hasNext()) {
            NFx.Shard s = it.next();
            s.age += dt;
            if (s.age > s.life) {
               it.remove();
            } else {
               s.vel = s.vel.scale(Math.exp(-1.4 * (double)dt)).add(0.0, (double)(-14.0F * dt), 0.0);
               s.pos = s.pos.add(s.vel.scale((double)dt));
               float a = 1.0F - s.age / s.life;
               Vec3[] sp = new Vec3[]{s.pos.subtract(s.v.scale((double)s.size)), s.pos.add(s.v.scale((double)s.size))};
               Vec3[] sides = new Vec3[]{s.u, s.u};
               out.add(
                  new Ribbon(
                     sp, sides, new float[]{s.size * 1.6F, 0.02F}, new float[]{a, a}, s.dark ? 723726 : 15986660, s.dark ? Ribbon.Style.INK : Ribbon.Style.TINT
                  )
               );
            }
         }
      }
   }

   private static void panel(List<Ribbon> out, AABB box, Vec3 cam, float age, float left, float tilt) {
      Vec3 c = box.getCenter();
      Vec3 n = c.subtract(cam);
      n = new Vec3(n.x, 0.0, n.z).lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : new Vec3(n.x, 0.0, n.z).normalize();
      Vec3 right = n.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 up = new Vec3(0.0, 1.0, 0.0);
      double t = Math.toRadians((double)tilt);
      Vec3 r2 = right.scale(Math.cos(t)).add(up.scale(Math.sin(t)));
      Vec3 u2 = up.scale(Math.cos(t)).subtract(right.scale(Math.sin(t)));
      double h = box.getYsize() * 0.68 + 0.25;
      double w = Math.max(box.getXsize() * 0.9 + 0.25, h * 0.72);
      float pop = 1.0F + 0.35F * (float)Math.exp((double)(-age) * 0.9);
      float shiver = left < 12.0F ? (float)Math.sin((double)age * 3.1) * 0.04F * (1.0F - left / 12.0F) : 0.0F;
      w *= (double)pop;
      h *= (double)pop;
      Vec3 o = c.subtract(n.scale(0.05)).add(r2.scale((double)shiver));
      float a = Mth.clamp(age / 2.0F, 0.0F, 1.0F) * Mth.clamp(left / 3.0F, 0.0F, 1.0F);
      Vec3 back = o.add(n.scale(0.12));
      out.add(
         new Ribbon(
            new Vec3[]{back.subtract(u2.scale(h)), back.add(u2.scale(h))},
            new Vec3[]{r2, r2},
            new float[]{(float)(w * 2.0), (float)(w * 2.0)},
            new float[]{0.3F * a, 0.3F * a},
            15986660,
            Ribbon.Style.TINT
         )
      );
      edge(out, o, r2, u2, w, h, 0.11F, 723726, a, Ribbon.Style.INK);
      edge(out, o, r2, u2, w - 0.1, h - 0.1, 0.05F, 16777215, a, Ribbon.Style.INK);

      for (int s = -1; s <= 1; s += 2) {
         Vec3 col = o.add(r2.scale((double)s * (w + 0.18)));

         for (int i = -3; i <= 3; i++) {
            Vec3 hole = col.add(u2.scale((double)i * h / 3.4));
            out.add(
               new Ribbon(
                  new Vec3[]{hole.subtract(u2.scale(0.07)), hole.add(u2.scale(0.07))},
                  new Vec3[]{r2, r2},
                  new float[]{0.13F, 0.13F},
                  new float[]{a, a},
                  723726,
                  Ribbon.Style.INK
               )
            );
         }

         out.add(
            new Ribbon(
               new Vec3[]{col.subtract(u2.scale(h + 0.1)), col.add(u2.scale(h + 0.1))},
               new Vec3[]{r2, r2},
               new float[]{0.3F, 0.3F},
               new float[]{0.65F * a, 0.65F * a},
               15986660,
               Ribbon.Style.TINT
            )
         );
      }
   }

   private static void edge(List<Ribbon> out, Vec3 o, Vec3 r, Vec3 u, double w, double h, float width, int rgb, float a, Ribbon.Style st) {
      Vec3[] corners = new Vec3[]{
         o.add(r.scale(-w)).add(u.scale(-h)), o.add(r.scale(w)).add(u.scale(-h)), o.add(r.scale(w)).add(u.scale(h)), o.add(r.scale(-w)).add(u.scale(h))
      };

      for (int i = 0; i < 4; i++) {
         Vec3 p0 = corners[i];
         Vec3 p1 = corners[(i + 1) % 4];
         Vec3 along = p1.subtract(p0).normalize();
         Vec3 side = i % 2 == 0 ? u : r;
         Vec3 e0 = p0.subtract(along.scale((double)width * 0.5));
         Vec3 e1 = p1.add(along.scale((double)width * 0.5));
         out.add(new Ribbon(new Vec3[]{e0, e1}, new Vec3[]{side, side}, new float[]{width, width}, new float[]{a, a}, rgb, st));
      }
   }

   private static void speedLines(List<Ribbon> out, NFx.Mark m, float age, float scale) {
      if (!(age > 8.0F)) {
         float a = 1.0F - age / 8.0F;
         Vec3 d = m.b.subtract(m.a);
         double len = d.length();
         if (!(len < 0.5)) {
            Vec3 dn = d.normalize();
            Vec3 u = dn.cross(Math.abs(dn.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0)).normalize();
            Vec3 v = dn.cross(u);
            Random r = new Random(m.seed);

            for (int i = 0; (float)i < 14.0F * scale; i++) {
               double ang = r.nextDouble() * Math.PI * 2.0;
               double rad = (0.5 + r.nextDouble() * 1.4) * (double)scale;
               Vec3 off = u.scale(Math.cos(ang) * rad).add(v.scale(Math.sin(ang) * rad)).add(0.0, 1.0, 0.0);
               double k0 = r.nextDouble() * 0.6;
               double k1 = Math.min(1.0, k0 + 0.25 + r.nextDouble() * 0.4);
               Vec3 p0 = m.a.add(d.scale(k0)).add(off);
               Vec3 p1 = m.a.add(d.scale(k1)).add(off);
               boolean ink = r.nextInt(5) == 0;
               Shapes.streak(out, p0, p1, ink ? 0.05F : 0.08F, a * (ink ? 0.8F : 0.9F), ink ? 723726 : 16777215, ink ? Ribbon.Style.INK : Ribbon.Style.GLOW);
            }
         }
      }
   }

   private static void filmRing(List<Ribbon> out, Vec3 c, float radius, float age) {
      if (!(age > 22.0F)) {
         float k = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
         float a = Mth.clamp((22.0F - age) / 8.0F, 0.0F, 1.0F);
         double r = (double)radius * (0.3 + 0.7 * (1.0 - Math.pow((double)(1.0F - k), 3.0)));
         Vec3 at = c.add(0.0, 1.0, 0.0);
         int n = 48;
         Vec3[] spine = new Vec3[n + 1];
         Vec3[] sides = new Vec3[n + 1];
         float[] w = new float[n + 1];
         float[] al = new float[n + 1];

         for (int i = 0; i <= n; i++) {
            double ang = (double)i * Math.PI * 2.0 / (double)n + (double)age * 0.05;
            spine[i] = at.add(Math.cos(ang) * r, 0.0, Math.sin(ang) * r);
            sides[i] = new Vec3(0.0, 1.0, 0.0);
            w[i] = 1.4F;
            al[i] = a * 0.85F;
         }

         out.add(new Ribbon(spine, sides, w, al, 723726, Ribbon.Style.INK));
         float[] w2 = (float[])w.clone();

         for (int i = 0; i <= n; i++) {
            w2[i] = 1.0F;
         }

         Vec3[] inner = new Vec3[n + 1];

         for (int i = 0; i <= n; i++) {
            Vec3 rad = spine[i].subtract(at).normalize();
            inner[i] = spine[i].subtract(rad.scale(0.02));
         }

         out.add(new Ribbon(inner, sides, w2, al, 15986660, Ribbon.Style.TINT));

         for (int i = 0; i < n; i += 2) {
            for (int s = -1; s <= 1; s += 2) {
               Vec3 hole = spine[i].add(0.0, (double)s * 0.56, 0.0);
               Vec3 tan = spine[i + 1].subtract(spine[i]).normalize();
               Shapes.streak(out, hole.subtract(tan.scale(0.07)), hole.add(tan.scale(0.07)), 0.1F, a, 15986660, Ribbon.Style.INK);
            }
         }
      }
   }

   private static void star(List<Ribbon> out, Vec3 p, float age, float size, long seed) {
      float life = 4.0F + size;
      float k = age / life;
      if (!(k < 0.0F) && !(k > 1.0F)) {
         Random r = new Random(seed);
         float fade = (1.0F - k) * (1.0F - k);
         int spikes = 8 + (int)(size * 3.0F);

         for (int i = 0; i < spikes; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.8, r.nextGaussian()).normalize();
            double len = (double)size * (0.6 + r.nextDouble() * 0.7);
            double in = len * (0.15 + 0.7 * Math.sqrt((double)k));
            double outR = len * (0.45 + 0.75 * Math.sqrt((double)k));
            float w = (float)(0.05 + 0.06 * (double)size) * (1.0F - k);
            boolean ink = i % 4 == 0;
            Shapes.streak(
               out,
               p.add(d.scale(in)),
               p.add(d.scale(outR)),
               w * (ink ? 1.4F : 1.0F),
               fade,
               ink ? 723726 : 16777215,
               ink ? Ribbon.Style.INK : Ribbon.Style.GLOW
            );
         }

         Shapes.ring(
            out,
            p,
            new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize(),
            (double)size * (0.3 + 1.1 * Math.sqrt((double)k)),
            (float)(0.04 + 0.06 * (double)size) * (1.0F - k),
            0.8F * fade,
            16777215,
            Ribbon.Style.GLOW
         );
      }
   }

   public static void screen(ScreenFx fx, float partial) {
      float now = now(partial);
      Minecraft mc = Minecraft.getInstance();

      for (NFx.Pulse p : PULSES) {
         if (p.seq != null && Prefs.impactFrames) {
            fx.impact(p.seq, now - p.start, p.at, 15913594);
         }

         fx.shake(p.power * ScreenFx.hit(now, p.start, 6.0F));
         fx.punch(0.5F * p.power * ScreenFx.hit(now, p.start, 3.0F));
         fx.aberration(0.6F * p.power * ScreenFx.hit(now, p.start, 4.0F));
      }

      if (mc.player != null && frozen(mc.player)) {
         fx.desaturate(0.85F);
         fx.vignette(0, 0.5F);
         fx.grain(0.6F);
      }

      Cine.screen(fx, partial);
   }

   public static record Frozen(int id, long start, long until, float tilt) {
   }

   private static record Mark(int kind, Vec3 a, Vec3 b, float f, long time, long seed) {
   }

   private static record Pulse(float start, int[][] seq, Vec3 at, float power) {
   }

   private static final class Shard {
      Vec3 pos;
      Vec3 vel;
      Vec3 u;
      Vec3 v;
      float size;
      float life;
      float age;
      boolean dark;
   }
}
