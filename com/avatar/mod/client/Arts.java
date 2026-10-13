package com.avatar.mod.client;

import com.avatar.mod.Net;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

final class Arts {
   private static final List<Arts.Whip> WHIPS = new ArrayList<>();
   private static final List<Arts.Breath> BREATHS = new ArrayList<>();
   private static final List<Arts.Bolt> BOLTS = new ArrayList<>();
   private static final Map<Integer, Long> BLUE = new HashMap<>();
   private static final Map<Integer, Long> LCHARGE = new HashMap<>();
   private static final Map<Integer, Long> LFULL = new HashMap<>();
   private static final Map<Integer, Long> STANCE = new HashMap<>();
   private static final Map<Integer, Arts.Redirect> REDIRECTS = new HashMap<>();
   private static long distortUntil;
   private static Matrix4f lastView = new Matrix4f();
   private static Matrix4f lastProj = new Matrix4f();

   private Arts() {
   }

   static boolean isBlue(Entity e) {
      Long t = BLUE.get(e.getId());
      return t != null && t > FireFx.gameTime();
   }

   static boolean active() {
      return !WHIPS.isEmpty() || !BREATHS.isEmpty() || !BOLTS.isEmpty() || !LCHARGE.isEmpty() || !REDIRECTS.isEmpty() || !STANCE.isEmpty();
   }

   static void clear() {
      WHIPS.clear();
      BREATHS.clear();
      BOLTS.clear();
      BLUE.clear();
      LCHARGE.clear();
      LFULL.clear();
      STANCE.clear();
      REDIRECTS.clear();
   }

   static float fovScale(float partial) {
      float left = (float)(distortUntil - FireFx.gameTime()) - partial;
      return left > 0.0F ? 1.0F + 0.28F * Mth.clamp(left / 6.0F, 0.0F, 1.0F) : 1.0F;
   }

   static void receive(Net.Fx f, Entity caster) {
      Minecraft mc = Minecraft.getInstance();
      long now = FireFx.gameTime();
      boolean blue = (f.flags & 1) != 0;
      boolean self = caster == mc.player;
      switch (f.kind) {
         case 8:
            WHIPS.add(new Arts.Whip(f, now, blue));
            break;
         case 9:
            BREATHS.add(new Arts.Breath(f, now, blue));
            FireFx.FLASHES.add(new FireFx.Flash(f.a, 2.4F, 1.0F, 6).blue(blue));
            FireFx.shake(f.a, 1.0F);
            break;
         case 10:
            if (f.f > 0.5F) {
               LCHARGE.put(f.caster, now);
            } else {
               LCHARGE.remove(f.caster);
               LFULL.remove(f.caster);
            }
            break;
         case 11:
            LFULL.put(f.caster, now);
            break;
         case 12:
            LCHARGE.remove(f.caster);
            LFULL.remove(f.caster);
            bolt(f, now);
            break;
         case 13:
            LCHARGE.put(f.caster, now - 30L);
            LFULL.put(f.caster, now);
            if (self) {
               FireFx.startCut(new Arts.LightningCut(f));
            }
            break;
         case 14:
            if (self) {
               FireFx.startCut(new Arts.BreathCut(f));
            }
            break;
         case 15:
            if (f.dur > 0) {
               BLUE.put(f.caster, now + (long)f.dur);
            } else {
               BLUE.remove(f.caster);
            }
            break;
         case 16:
            STANCE.remove(f.caster);
            REDIRECTS.put(f.caster, new Arts.Redirect(now, f.dur, f.a, f.b));
            if (self) {
               FireFx.startCut(new Arts.RedirectCut(f));
            }
            break;
         case 17:
            STANCE.put(f.caster, now + (long)f.dur);
            break;
         case 18:
            if (caster != null) {
               Vec3 p = caster.position();

               for (int i = 0; i < 26; i++) {
                  double a = (double)i / 26.0 * Math.PI * 2.0;
                  Vec3 at = p.add(Math.cos(a) * 0.9, 0.1, Math.sin(a) * 0.9);
                  FireFx.PUFFS
                     .add(
                        new FireFx.Puff(
                              at,
                              new Vec3(Math.cos(a) * 0.04, 0.16 + (double)FireFx.RNG.nextFloat() * 0.08, Math.sin(a) * 0.04),
                              0.45F + FireFx.RNG.nextFloat() * 0.3F,
                              1.03F,
                              1.0F,
                              0.05F,
                              0.5F,
                              18 + FireFx.RNG.nextInt(8),
                              10
                           )
                           .blue(true)
                           .drag(0.93F)
                     );
               }

               FireFx.FLASHES.add(new FireFx.Flash(p.add(0.0, 1.0, 0.0), 3.5F, 1.0F, 10).blue(true));
            }

            if (self) {
               FireFx.startCut(new Arts.BlueCut(f));
            }
            break;
         default:
            Elems.receive(f, caster);
      }
   }

   private static void bolt(Net.Fx f, long now) {
      Minecraft mc = Minecraft.getInstance();
      Arts.Bolt b = new Arts.Bolt(f.a, f.b, f.f, f.dur, FireFx.RNG.nextLong());
      BOLTS.add(b);
      FireFx.FLASHES.add(new FireFx.Flash(f.b, 2.6F * f.f, 1.0F, 7).blue(true));
      FireFx.FLASHES.add(new FireFx.Flash(f.a, 1.4F * f.f, 1.0F, 5).blue(true));

      for (int i = 0; i < 18; i++) {
         Vec3 v = FireFx.rand(1.0).normalize().scale(0.25 + (double)FireFx.RNG.nextFloat() * 0.4).add(0.0, 0.1, 0.0);
         FireFx.EMBERS.add(new FireFx.Ember(f.b, v, 8 + FireFx.RNG.nextInt(10), 1.0F, 0.03F).blue(true));
      }

      FireFx.shake(f.b, 1.4F * f.f);
      if ((f.flags & 2) != 0 && mc.player != null && mc.player.position().distanceTo(f.b) < 64.0) {
         float[] c = screen(f.b);
         Impact.hit(0, 0, 2, 0, c[0], c[1], true);
         Impact.hit(8, 2, 3, 0, c[0], c[1], true);
         Impact.hit(3, 5, 2, 0, c[0], c[1], false);
         Impact.hit(5, 7, 3, 6, c[0], c[1], true);
         distortUntil = now + 8L;
         FireFx.flash = 0.6F;
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

   static Vec3 fwd(Entity e, float partial) {
      float yaw = e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getYRot();
      return Vec3.directionFromRotation(0.0F, yaw);
   }

   static Vec3 hand(Entity e, int side, float partial) {
      if (FireFx.firstPersonSelf(e)) {
         return FireFx.fist(e, side, partial);
      } else {
         Vec3 f = fwd(e, partial);
         Vec3 r = new Vec3(-f.z, 0.0, f.x);
         Vec3 base = e.getPosition(partial);
         Vec3 look = e.getViewVector(partial);
         int pose = FireFx.pose(e);
         if (pose == 10) {
            return side > 0
               ? e.getEyePosition(partial).add(look.scale(0.85)).add(r.scale(0.22)).add(0.0, -0.28, 0.0)
               : base.add(0.0, 1.0, 0.0).add(f.scale(-0.35)).add(r.scale(-0.4));
         } else if (pose == 11) {
            return side < 0
               ? e.getEyePosition(partial).add(f.scale(0.75)).add(r.scale(-0.3)).add(0.0, -0.3, 0.0)
               : base.add(0.0, 1.25, 0.0).add(f.scale(0.35)).add(r.scale(0.8));
         } else {
            float t = ((float)FireFx.gameTime() + partial) * 0.35F + (side > 0 ? 0.0F : (float) Math.PI);
            return base.add(0.0, 1.15 + 0.18 * (double)Mth.sin(t), 0.0).add(f.scale(0.4 + 0.12 * (double)Mth.cos(t))).add(r.scale(0.36 * (double)side));
         }
      }
   }

   static List<Vec3> jag(Vec3 a, Vec3 b, Random r, int depth, double rough) {
      List<Vec3> pts = new ArrayList<>();
      pts.add(a);
      pts.add(b);
      double disp = a.distanceTo(b) * rough;

      for (int d = 0; d < depth; d++) {
         List<Vec3> next = new ArrayList<>();

         for (int i = 0; i < pts.size() - 1; i++) {
            Vec3 p = pts.get(i);
            Vec3 q = pts.get(i + 1);
            next.add(p);
            Vec3 mid = p.add(q).scale(0.5).add(r.nextGaussian() * disp, r.nextGaussian() * disp, r.nextGaussian() * disp);
            next.add(mid);
         }

         next.add(pts.get(pts.size() - 1));
         pts = next;
         disp *= 0.55;
      }

      return pts;
   }

   private static void ribbon(Vec3 cam, List<Vec3> pts, float width, float intensity, float seed) {
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

         Gfx.ribbon(p, w, u, seed, Mth.clamp(intensity, 0.0F, 1.0F), 1.0F, 1.0F);
      }
   }

   static void tick(Minecraft mc, long now) {
      Iterator<Arts.Whip> it = WHIPS.iterator();

      while (it.hasNext()) {
         Arts.Whip w = it.next();
         float t = (float)(now - w.start - 3L);
         if (t > (float)(w.sweep + 8)) {
            it.remove();
         } else if (t >= 0.0F && t <= (float)w.sweep) {
            double ang = Math.toRadians((double)(75.0F - 150.0F * t / (float)w.sweep));

            for (int i = 0; i < 5; i++) {
               double rr = (double)w.radius * (0.35 + (double)FireFx.RNG.nextFloat() * 0.65);
               Vec3 dir = w.fwd.scale(Math.cos(ang)).add(w.right.scale(Math.sin(ang)));
               Vec3 p = w.c.add(dir.scale(rr)).add(0.0, FireFx.RNG.nextGaussian() * 0.15, 0.0);
               FireFx.PUFFS
                  .add(
                     new FireFx.Puff(
                           p,
                           dir.scale(0.05).add(w.right.scale(-0.08)).add(0.0, 0.02, 0.0),
                           0.35F + FireFx.RNG.nextFloat() * 0.3F,
                           1.04F,
                           1.0F,
                           0.09F,
                           0.5F,
                           9 + FireFx.RNG.nextInt(6),
                           10
                        )
                        .blue(w.blue)
                  );
            }

            FireFx.EMBERS
               .add(
                  new FireFx.Ember(
                        w.c.add(w.fwd.scale(Math.cos(ang) * (double)w.radius)).add(w.right.scale(Math.sin(ang) * (double)w.radius)),
                        FireFx.rand(0.15).add(0.0, 0.1, 0.0),
                        10 + FireFx.RNG.nextInt(8),
                        0.9F,
                        0.03F
                     )
                     .blue(w.blue)
               );
         }
      }

      it = BREATHS.iterator();

      while (it.hasNext()) {
         Arts.Breath b = (Arts.Breath)it.next();
         long age = now - b.start;
         if (age >= (long)b.life) {
            it.remove();
         } else {
            Vec3 side = b.dir.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            Vec3 up = side.cross(b.dir).normalize();

            for (int i = 0; i < 8; i++) {
               double spread = 0.07 + (double)FireFx.RNG.nextFloat() * 0.05;
               Vec3 v = b.dir
                  .scale(0.85 + (double)FireFx.RNG.nextFloat() * 0.4)
                  .add(side.scale(FireFx.RNG.nextGaussian() * spread))
                  .add(up.scale(FireFx.RNG.nextGaussian() * spread));
               FireFx.PUFFS
                  .add(
                     new FireFx.Puff(
                           b.mouth.add(b.dir.scale(0.3 + (double)FireFx.RNG.nextFloat() * 0.6)),
                           v,
                           0.22F + FireFx.RNG.nextFloat() * 0.15F,
                           1.1F,
                           1.0F,
                           0.04F + FireFx.RNG.nextFloat() * 0.02F,
                           0.5F,
                           15 + FireFx.RNG.nextInt(5),
                           10
                        )
                        .blue(b.blue)
                        .drag(0.965F)
                  );
            }

            for (int i = 0; i < 2; i++) {
               FireFx.EMBERS
                  .add(
                     new FireFx.Ember(b.mouth.add(b.dir.scale(2.0)), b.dir.scale(0.8).add(FireFx.rand(0.12)), 12 + FireFx.RNG.nextInt(8), 1.0F, 0.01F)
                        .blue(b.blue)
                  );
            }

            if (age % 4L == 0L) {
               FireFx.shake(b.mouth, 0.35F);
            }
         }
      }

      BOLTS.removeIf(b -> ++b.age > b.life);
      it = REDIRECTS.entrySet().iterator();

      while (it.hasNext()) {
         Arts.Redirect r = (Arts.Redirect)((Entry)it.next()).getValue();
         if (now - r.start > (long)(r.dur + 2)) {
            it.remove();
         }
      }

      STANCE.values().removeIf(t -> t < now);
      BLUE.values().removeIf(t -> t < now);

      for (Entry<Integer, Long> c : LCHARGE.entrySet()) {
         Entity en = mc.level.getEntity(c.getKey());
         if (en != null && FireFx.RNG.nextFloat() < 0.6F) {
            Vec3 h = hand(en, FireFx.RNG.nextBoolean() ? 1 : -1, 0.0F);
            FireFx.EMBERS.add(new FireFx.Ember(h, FireFx.rand(0.12), 4 + FireFx.RNG.nextInt(4), 1.0F, 0.02F).blue(true));
         }
      }
   }

   static void render(Minecraft mc, Vec3 cam, float partial, float time, Matrix4f view, Matrix4f proj) {
      lastView = new Matrix4f(view);
      lastProj = new Matrix4f(proj);
      long now = FireFx.gameTime();
      if (!WHIPS.isEmpty() || !BREATHS.isEmpty()) {
         Gfx.begin();

         for (Arts.Whip w : WHIPS) {
            w.draw(cam, now, partial);
         }

         for (Arts.Breath b : BREATHS) {
            b.draw(cam, now, partial, time);
         }

         FireFx.draw(AShaders.flame, 11, view, proj);
      }

      Gfx.begin();

      for (Arts.Breath b : BREATHS) {
         float k = Mth.clamp(((float)((long)b.life - (now - b.start)) - partial) / 6.0F, 0.0F, 1.0F);
         FireFx.billboard(cam, b.mouth.add(b.dir.scale(1.2)), 2.2F, 0.0F, 0.9F, b.blue ? 1.0F : 0.0F, 0.6F * k);

         for (int i = 1; i <= 3; i++) {
            FireFx.hazeAdd(b.mouth.add(b.dir.scale((double)i * 3.5)).add(0.0, 0.8, 0.0), 1.6F + (float)i * 0.6F, 1.0F);
         }
      }

      for (Arts.Bolt b : BOLTS) {
         float k = b.intensity(partial);
         FireFx.billboard(cam, b.to, 3.5F * b.thick, 0.0F, 1.0F, 1.0F, 0.7F * k);
      }

      for (Entry<Integer, Long> c : LCHARGE.entrySet()) {
         Entity en = mc.level.getEntity(c.getKey());
         if (en != null) {
            boolean full = LFULL.containsKey(c.getKey());

            for (int side = -1; side <= 1; side += 2) {
               FireFx.billboard(cam, hand(en, side, partial), full ? 0.7F : 0.45F, 0.0F, 1.0F, 1.0F, 0.5F + 0.2F * Mth.sin(time * 2.3F + (float)side));
            }
         }
      }

      for (Entry<Integer, Long> s : STANCE.entrySet()) {
         Entity en = mc.level.getEntity(s.getKey());
         if (en != null) {
            for (int side = -1; side <= 1; side += 2) {
               FireFx.billboard(cam, hand(en, side, partial), 0.4F, 0.0F, 0.8F, 1.0F, 0.25F + 0.1F * Mth.sin(time * 0.8F));
            }
         }
      }

      FireFx.draw(AShaders.flame, 4, view, proj);
      Gfx.begin();
      int drawn = 0;

      for (Arts.Bolt b : BOLTS) {
         float k = b.intensity(partial);
         if (!(k <= 0.01F)) {
            Random r = new Random(b.seed * 31L + (long)(b.age / 2));
            List<Vec3> main = jag(b.from, b.to, r, 6, 0.09);
            ribbon(cam, main, 0.32F * b.thick, k, 0.3F);

            for (int i = 0; i < 5; i++) {
               Vec3 sx = main.get(r.nextInt(main.size() - 2) + 1);
               Vec3 d = b.to.subtract(b.from).normalize().add(FireFx.rand(0.7)).normalize();
               ribbon(cam, jag(sx, sx.add(d.scale(1.5 + (double)r.nextFloat() * b.from.distanceTo(b.to) * 0.12)), r, 3, 0.18), 0.14F * b.thick, k * 0.75F, 0.6F);
            }

            drawn++;
         }
      }

      for (Entry<Integer, Long> cx : LCHARGE.entrySet()) {
         Entity en = mc.level.getEntity(cx.getKey());
         if (en != null) {
            boolean full = LFULL.containsKey(cx.getKey());
            Random r = new Random((long)cx.getKey().intValue() * 977L + now);
            Vec3 l = hand(en, -1, partial);
            Vec3 rh = hand(en, 1, partial);

            for (Vec3 h : new Vec3[]{l, rh}) {
               for (int i = 0; i < (full ? 4 : 2); i++) {
                  ribbon(cam, jag(h, h.add(FireFx.rand(0.5)), r, 3, 0.2), 0.05F, 0.9F, 0.2F);
               }
            }

            if (full) {
               ribbon(cam, jag(l, rh, r, 4, 0.18), 0.07F, 1.0F, 0.4F);
               if (r.nextFloat() < 0.4F) {
                  ribbon(cam, jag(rh, rh.add(FireFx.rand(1.2)), r, 4, 0.2), 0.06F, 0.9F, 0.5F);
               }
            }

            drawn++;
         }
      }

      for (Entry<Integer, Arts.Redirect> e : REDIRECTS.entrySet()) {
         Entity en = mc.level.getEntity(e.getKey());
         if (en != null) {
            Arts.Redirect rd = e.getValue();
            float t = (float)(now - rd.start) + partial;
            Vec3 f = fwd(en, partial);
            Vec3 rt = new Vec3(-f.z, 0.0, f.x);
            Vec3 base = en.getPosition(partial);
            Vec3[] path = new Vec3[]{
               hand(en, -1, partial),
               base.add(0.0, 1.42, 0.0).add(rt.scale(-0.36)),
               base.add(0.0, 0.9, 0.0).add(f.scale(0.18)),
               base.add(0.0, 1.42, 0.0).add(rt.scale(0.36)),
               hand(en, 1, partial)
            };
            float k = Mth.clamp(t / 20.0F, 0.0F, 1.0F);
            List<Vec3> pts = new ArrayList<>();
            float total = (float)(path.length - 1) * k;

            for (int i = 0; i < path.length - 1 && (float)i < total; i++) {
               float seg = Math.min(1.0F, total - (float)i);
               pts.add(path[i]);
               if (seg < 1.0F) {
                  pts.add(path[i].lerp(path[i + 1], (double)seg));
               }
            }

            if (k >= 1.0F) {
               pts.add(path[path.length - 1]);
            }

            if (pts.size() >= 2) {
               Random r = new Random((long)e.getKey().intValue() * 131L + now);
               List<Vec3> all = new ArrayList<>();

               for (int ix = 0; ix < pts.size() - 1; ix++) {
                  List<Vec3> seg = jag(pts.get(ix), pts.get(ix + 1), r, 2, 0.08);
                  all.addAll(ix == 0 ? seg : seg.subList(1, seg.size()));
               }

               ribbon(cam, all, 0.08F, 1.0F, 0.7F);
               Vec3 head = all.get(all.size() - 1);
               ribbon(cam, jag(head, head.add(FireFx.rand(0.35)), r, 2, 0.2), 0.05F, 0.9F, 0.1F);
            }

            drawn++;
         }
      }

      if (drawn > 0) {
         FireFx.draw(AShaders.flame, 13, view, proj);
      } else {
         Gfx.flushEmpty();
      }
   }

   private static void card(GuiGraphics g, Minecraft mc, int w, int h, float t, float t0, float t1, String glyph, String name, int col) {
      if (!(t <= t0) && !(t >= t1)) {
         float a = Mth.clamp((t - t0) / 4.0F, 0.0F, 1.0F) * Mth.clamp((t1 - t) / 4.0F, 0.0F, 1.0F);
         g.pose().pushPose();
         g.pose().translate((float)w * 0.18F, (float)h * 0.36F, 0.0F);
         float s = 4.0F + (t - t0) * 0.02F;
         g.pose().scale(s, s, 1.0F);
         int gw = mc.font.width(glyph);
         g.drawString(mc.font, glyph, -gw / 2 + 1, 1, FireFx.argb(a, 1052696), false);
         g.drawString(mc.font, glyph, -gw / 2, 0, FireFx.argb(a, col), false);
         g.pose().popPose();
         g.pose().pushPose();
         g.pose().translate((float)w * 0.18F, (float)h * 0.36F + 40.0F, 0.0F);
         g.pose().scale(1.6F, 1.6F, 1.0F);
         g.drawCenteredString(mc.font, name, 0, 0, FireFx.argb(a, 16777215));
         g.pose().popPose();
      }
   }

   private static void line(GuiGraphics g, Minecraft mc, int w, int h, float t, float t0, float t1, String text, int col) {
      if (!(t <= t0) && !(t >= t1)) {
         float a = Mth.clamp((t - t0) / 3.0F, 0.0F, 1.0F) * Mth.clamp((t1 - t) / 3.0F, 0.0F, 1.0F);
         g.drawCenteredString(mc.font, text, w / 2, h - (int)((float)h * 0.12F) - 14, FireFx.argb(a, col));
      }
   }

   static final class BlueCut extends FireFx.Cine {
      BlueCut(Net.Fx f) {
         super(f);
      }

      @Override
      int length() {
         return 32;
      }

      @Override
      void onAge() {
         if (this.age == 14) {
            FireFx.flash = 0.35F;
         }
      }

      @Override
      float warm(float t) {
         return Mth.clamp(t / 6.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.length() - t) / 6.0F, 0.0F, 1.0F);
      }

      @Override
      int warmColor() {
         return 2780159;
      }

      @Override
      Vec3[] shot(float partial) {
         Vec3 base = this.base(partial);
         Vec3 chest = base.add(0.0, 1.2, 0.0);
         float t = (float)this.age + partial;
         float k = Mth.clamp(t / (float)this.length(), 0.0F, 1.0F);
         double a = Math.toRadians((double)(-35.0F + 80.0F * k));
         this.fov = 50.0F - 8.0F * k;
         this.roll = 0.0F;
         Vec3 eye = chest.add(this.fwd.scale(Math.cos(a) * (3.2 - (double)k)))
            .add(this.right.scale(Math.sin(a) * (3.2 - (double)k)))
            .add(0.0, -0.7 + 1.0 * (double)k, 0.0);
         return new Vec3[]{eye, chest.add(0.0, 0.25, 0.0), chest};
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Arts.card(g, mc, w, h, t, 4.0F, (float)(this.length() - 2), "蓝火", "B L U E   F I R E", 8370431);
         Arts.line(g, mc, w, h, t, 6.0F, (float)(this.length() - 4), "\"Hotter. Faster. Precise.\"", 14543103);
      }
   }

   static final class Bolt {
      final Vec3 from;
      final Vec3 to;
      final float thick;
      final int life;
      final long seed;
      int age;

      Bolt(Vec3 from, Vec3 to, float thick, int life, long seed) {
         this.from = from;
         this.to = to;
         this.thick = thick;
         this.life = Math.max(4, life);
         this.seed = seed;
      }

      float intensity(float partial) {
         float t = (float)this.age + partial;
         if (t < 2.0F) {
            return 1.0F;
         } else if (t < 3.0F) {
            return 0.3F;
         } else {
            return t < 5.0F ? 1.0F : Math.max(0.0F, 1.0F - (t - 5.0F) / (float)(this.life - 5));
         }
      }
   }

   static final class Breath {
      final Vec3 mouth;
      final Vec3 dir;
      final long start;
      final int life;
      final boolean blue;

      Breath(Net.Fx f, long now, boolean blue) {
         this.mouth = f.a;
         this.dir = f.b.normalize();
         this.start = now;
         this.life = f.dur;
         this.blue = blue;
      }

      void draw(Vec3 cam, long now, float partial, float time) {
         float age = (float)(now - this.start) + partial;
         float k = Mth.clamp(age / 5.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.life - age) / 6.0F, 0.0F, 1.0F);
         if (!(k <= 0.0F)) {
            Vec3 side = this.dir.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            Vec3 up = side.cross(this.dir).normalize();
            float len = Math.min(14.0F, age * 1.1F);

            for (int c = 0; c < 3; c++) {
               int n = 16;
               float[] p = new float[n * 3];
               float[] w = new float[n];
               float[] u = new float[n];

               for (int i = 0; i < n; i++) {
                  float x = (float)i / ((float)n - 1.0F);
                  float along = len * (1.0F - x);
                  float ph = time * 0.45F + along * 0.55F + (float)c * 2.09F;
                  float rad = 0.12F + along * 0.11F;
                  Vec3 q = this.mouth
                     .add(this.dir.scale((double)along))
                     .add(side.scale((double)(Mth.cos(ph) * rad)))
                     .add(up.scale((double)(Mth.sin(ph) * rad)));
                  p[i * 3] = (float)(q.x - cam.x);
                  p[i * 3 + 1] = (float)(q.y - cam.y);
                  p[i * 3 + 2] = (float)(q.z - cam.z);
                  w[i] = 0.25F + along * 0.09F;
                  u[i] = x;
               }

               Gfx.ribbon(p, w, u, (float)c * 0.31F, k, this.blue ? 1.0F : 0.5F, k);
            }
         }
      }
   }

   static final class BreathCut extends FireFx.Cine {
      BreathCut(Net.Fx f) {
         super(f);
      }

      @Override
      int length() {
         return 72;
      }

      @Override
      void onAge() {
         if (this.age == 17) {
            Impact.hit(7, 0, 7, 3, 0.5F, 0.5F, true);
         }
      }

      @Override
      float warm(float t) {
         return Mth.clamp(t / 6.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.length() - t) / 6.0F, 0.0F, 1.0F);
      }

      @Override
      Vec3[] shot(float partial) {
         Vec3 base = this.base(partial);
         Vec3 head = base.add(0.0, 1.55, 0.0);
         float t = (float)this.age + partial;
         if (t < 17.0F) {
            float k = t / 17.0F;
            this.fov = 38.0F - 4.0F * k;
            this.roll = 0.0F;
            Vec3 eye = head.add(this.fwd.scale(1.6 - 0.3 * (double)k)).add(this.right.scale(-0.45)).add(0.0, -0.45, 0.0);
            return new Vec3[]{eye, head.add(0.0, 0.05, 0.0), head};
         } else if (t < 40.0F) {
            float k = (t - 14.0F - 3.0F) / 23.0F;
            this.fov = 64.0F;
            this.roll = -3.0F;
            Vec3 eye = base.add(this.right.scale(5.5 + (double)k)).add(this.fwd.scale(3.0 + (double)(2.0F * k))).add(0.0, 1.0, 0.0);
            return new Vec3[]{eye, base.add(0.0, 1.3, 0.0).add(this.fwd.scale(6.0)), base.add(0.0, 1.3, 0.0)};
         } else {
            float k = Mth.clamp((t - 14.0F - 26.0F) / 30.0F, 0.0F, 1.0F);
            this.fov = 70.0F;
            this.roll = 2.0F;
            Vec3 eye = head.add(this.fwd.scale(-2.6 - (double)k)).add(this.right.scale(0.9)).add(0.0, 0.45 + 0.4 * (double)k, 0.0);
            return new Vec3[]{eye, head.add(this.dir.scale(10.0)), head};
         }
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Arts.card(g, mc, w, h, t, 1.0F, 16.0F, "龍", "D R A G O N ' S   B R E A T H", 16757575);
         Arts.line(g, mc, w, h, t, 2.0F, 14.0F, "\"Draw the fire deep inside... and let the dragon out.\"", 16770760);
         Arts.line(g, mc, w, h, t, 24.0F, 42.0F, "RRRAAAAAHHH!", 16765562);
      }
   }

   static final class LightningCut extends FireFx.Cine {
      LightningCut(Net.Fx f) {
         super(f);
      }

      @Override
      int length() {
         return 30;
      }

      @Override
      void onAge() {
      }

      @Override
      float warm(float t) {
         return Mth.clamp(t / 6.0F, 0.0F, 1.0F) * Mth.clamp((16.0F - t) / 3.0F, 0.0F, 1.0F);
      }

      @Override
      int warmColor() {
         return 2780159;
      }

      @Override
      Vec3[] shot(float partial) {
         Vec3 base = this.base(partial);
         Vec3 head = base.add(0.0, 1.55, 0.0);
         Vec3 tip = head.add(this.dir.scale(0.95)).add(this.right.scale(0.22)).add(0.0, -0.3, 0.0);
         float t = (float)this.age + partial;
         if (t < 9.0F) {
            this.fov = 32.0F;
            this.roll = 0.0F;
            Vec3 eye = tip.add(this.right.scale(1.0)).add(this.fwd.scale(0.25)).add(0.0, 0.12, 0.0);
            return new Vec3[]{eye, tip, tip};
         } else if (t < 16.0F) {
            float k = (t - 9.0F) / 7.0F;
            this.fov = 46.0F - 4.0F * k;
            this.roll = 0.0F;
            Vec3 eye = base.add(this.right.scale(3.2)).add(this.fwd.scale(0.6)).add(0.0, 1.45, 0.0);
            return new Vec3[]{eye, head.add(this.fwd.scale(1.0)), head};
         } else {
            float k = Mth.clamp((t - 16.0F) / 10.0F, 0.0F, 1.0F);
            this.fov = 66.0F + 6.0F * k;
            this.roll = 3.0F;
            Vec3 eye = head.add(this.fwd.scale(-2.3 - 1.2 * (double)k)).add(this.right.scale(0.8)).add(0.0, 0.35 + 0.3 * (double)k, 0.0);
            return new Vec3[]{eye, head.add(this.dir.scale(18.0)), head};
         }
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Arts.card(g, mc, w, h, t, 1.0F, 13.0F, "雷", "L I G H T N I N G", 10275071);
         Arts.line(g, mc, w, h, t, 9.0F, 16.0F, "...", 14543103);
      }
   }

   static record Redirect(long start, int dur, Vec3 in, Vec3 target) {
   }

   static final class RedirectCut extends FireFx.Cine {
      RedirectCut(Net.Fx f) {
         super(new Net.Fx(f.kind, f.a, f.b.subtract(f.a), 0.0F, f.caster, f.other, f.dur));
      }

      @Override
      int length() {
         return 30;
      }

      @Override
      void onAge() {
         if (this.age == 20) {
            FireFx.flash = 0.5F;
         }
      }

      @Override
      float warm(float t) {
         return 0.0F;
      }

      @Override
      Vec3[] shot(float partial) {
         Vec3 base = this.base(partial);
         Vec3 chest = base.add(0.0, 1.15, 0.0);
         float t = (float)this.age + partial;
         if (t < 20.0F) {
            double a = Math.toRadians((double)(-60.0F + 120.0F * t / 20.0F));
            this.fov = 44.0F;
            this.roll = 0.0F;
            Vec3 eye = chest.add(this.fwd.scale(Math.cos(a) * 3.0)).add(this.right.scale(Math.sin(a) * 3.0)).add(0.0, 0.2, 0.0);
            return new Vec3[]{eye, chest, chest};
         } else {
            this.fov = 64.0F;
            this.roll = 2.0F;
            Vec3 eye = chest.add(this.right.scale(-1.2)).add(this.dir.scale(-2.2)).add(0.0, 0.5, 0.0);
            return new Vec3[]{eye, chest.add(this.dir.scale(12.0)), chest};
         }
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Arts.card(g, mc, w, h, t, 1.0F, 18.0F, "导", "R E D I R E C T I O N", 10275071);
         Arts.line(g, mc, w, h, t, 2.0F, 20.0F, "\"In through the fingertips... down to the stomach... and out.\"", 14543103);
      }
   }

   static final class Whip {
      final Vec3 c;
      final Vec3 fwd;
      final Vec3 right;
      final float radius;
      final int sweep;
      final long start;
      final boolean blue;
      final float seed;

      Whip(Net.Fx f, long now, boolean blue) {
         this.c = f.a;
         this.fwd = f.b.normalize();
         this.right = new Vec3(-this.fwd.z, 0.0, this.fwd.x);
         this.radius = f.f;
         this.sweep = Math.max(1, f.dur);
         this.start = now;
         this.blue = blue;
         this.seed = FireFx.RNG.nextFloat();
      }

      void draw(Vec3 cam, long now, float partial) {
         float t = (float)(now - this.start - 3L) + partial;
         if (!(t < 0.0F)) {
            float u = Mth.clamp(t / (float)this.sweep, 0.0F, 1.0F);
            float alpha = 1.0F - Mth.clamp((t - (float)this.sweep) / 7.0F, 0.0F, 1.0F);
            float lead = 75.0F - 150.0F * u;
            float tail = Math.min(95.0F, 75.0F - lead + 15.0F);

            for (int layer = 0; layer < 2; layer++) {
               int n = 14;
               float[] p = new float[n * 3];
               float[] w = new float[n];
               float[] uu = new float[n];

               for (int i = 0; i < n; i++) {
                  float x = (float)i / ((float)n - 1.0F);
                  double a = Math.toRadians((double)(lead + x * tail));
                  double rr = (double)this.radius * (0.98 - 0.22 * (double)x) * (layer == 0 ? 1.0 : 0.8);
                  Vec3 d = this.fwd.scale(Math.cos(a)).add(this.right.scale(Math.sin(a)));
                  Vec3 q = this.c.add(d.scale(rr)).add(0.0, 0.3 * Math.sin((double)x * Math.PI) - 0.25 * (double)u + (double)layer * 0.15, 0.0);
                  p[i * 3] = (float)(q.x - cam.x);
                  p[i * 3 + 1] = (float)(q.y - cam.y);
                  p[i * 3 + 2] = (float)(q.z - cam.z);
                  w[i] = (layer == 0 ? 0.75F : 0.45F) * (0.6F + 0.4F * (1.0F - u * 0.5F));
                  uu[i] = x;
               }

               Gfx.ribbon(p, w, uu, (this.seed + (float)layer * 0.37F) % 1.0F, Mth.clamp(alpha, 0.0F, 1.0F), this.blue ? 1.0F : 0.5F, alpha);
            }
         }
      }
   }
}
