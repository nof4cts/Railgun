package com.shrine.client;

import com.shrine.client.anim.PlayerAnims;
import com.shrine.client.cinematic.Cutscene;
import com.shrine.common.Slash;
import com.shrine.network.S2CCast;
import com.shrine.registry.ModSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Casts {
   private static final List<Casts.Active> ACTIVE = new ArrayList<>();
   private static final float[] CRIMSON = new float[]{0.95F, 0.08F, 0.06F};

   private Casts() {
   }

   public static void add(S2CCast msg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         Casts.Active a = new Casts.Active(msg);
         ACTIVE.add(a);
         Entity caster = mc.level.getEntity(msg.casterId);
         if (caster instanceof AbstractClientPlayer p) {
            PlayerAnims.play(p, msg.move);
         }

         if (msg.move.cutscene) {
            boolean mine = caster == mc.player;
            boolean near = mc.player.getEyePosition().distanceTo(msg.origin) < (double)msg.cutsceneRadius;
            if (mine || near) {
               Cutscene.start(msg);
            }
         }
      }
   }

   public static void clear() {
      ACTIVE.clear();
   }

   static double now(float partial) {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0.0 : (double)((float)mc.level.getGameTime() + partial);
   }

   private static void ui(SoundEvent e, float pitch) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, 1.0F));
   }

   private static boolean near(Casts.Active a) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      } else if (Cutscene.isFor(a.msg)) {
         return true;
      } else {
         double var10000 = switch (a.msg.move) {
            case FUGA -> (double)(a.msg.size * 6.0F + 40.0F);
            case DOMAIN -> (double)(a.msg.size + 40.0F);
            case WORLD_SLASH -> 600.0;
            default -> 48.0;
         };
         return mc.gameRenderer.getMainCamera().getPosition().distanceTo(a.msg.origin) < var10000
            || mc.gameRenderer.getMainCamera().getPosition().distanceTo(a.msg.point) < var10000;
      }
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         ACTIVE.clear();
      } else {
         long now = mc.level.getGameTime();
         Iterator<Casts.Active> it = ACTIVE.iterator();

         while (it.hasNext()) {
            Casts.Active a = it.next();
            int t = (int)(now - a.msg.startTick);

            for (int k = a.lastTick + 1; k <= t; k++) {
               beat(a, k);
            }

            a.lastTick = t;
            if (now > a.endTick) {
               it.remove();
            }
         }
      }
   }

   private static void hit(ScreenFx.Hit h, Vec3 at, float[] c) {
      ScreenFx.hit(h, at, 0.5F, 0.5F, c[0], c[1], c[2]);
      ui((SoundEvent)ModSounds.IMPACT.get(), h == ScreenFx.Hit.LIGHT ? 1.3F : 0.95F);
   }

   private static void screenHit(ScreenFx.Hit h, float[] c) {
      ScreenFx.hit(h, (Vec3)null, 0.5F, 0.5F, c[0], c[1], c[2]);
      ui((SoundEvent)ModSounds.IMPACT.get(), h == ScreenFx.Hit.LIGHT ? 1.3F : 0.9F);
   }

   private static void beat(Casts.Active a, int t) {
      Minecraft mc = Minecraft.getInstance();
      S2CCast m = a.msg;
      boolean close = near(a);
      Entity caster = mc.level.getEntity(m.casterId);
      switch (m.move) {
         case FUGA:
            if (t == 8 && Cutscene.isFor(m)) {
               ui((SoundEvent)ModSounds.CARD.get(), 1.0F);
            }

            if (t == 48 && close) {
               ui((SoundEvent)ModSounds.FUGA_DRAW.get(), 1.0F);
            }

            if (t == 80) {
               a.fugaFrom = fugaBow(caster, m, 0.0F);
               a.fugaAim = m.point.subtract(a.fugaFrom).normalize();
               if (close) {
                  hit(ScreenFx.Hit.LIGHT, a.fugaFrom, new float[]{1.0F, 0.45F, 0.05F});
               }
            }

            if (t == 89 && close) {
               ScreenFx.impact(m.point, m.size, 1.0F, 0.3F, 0.02F);
            }
            break;
         case DOMAIN:
            if (Cutscene.isFor(m)) {
               if (t == 26) {
                  ui((SoundEvent)ModSounds.CARD.get(), 0.8F);
                  screenHit(ScreenFx.Hit.HEAVY, CRIMSON);
               }

               if (t == 40) {
                  ui((SoundEvent)ModSounds.CARD.get(), 0.6F);
               }
            }

            if (t == 52 && close) {
               screenHit(ScreenFx.Hit.ULTIMATE, CRIMSON);
               ScreenFx.flashAfterHit(0.7F);
            }
            break;
         case WORLD_SLASH:
            if ((t == 0 || t == 18 || t == 36) && Cutscene.isFor(m)) {
               screenHit(ScreenFx.Hit.LIGHT, new float[]{0.85F, 0.85F, 1.0F});
            }

            if (t == 78 && close) {
               screenHit(ScreenFx.Hit.ULTIMATE, new float[]{0.9F, 0.9F, 1.0F});
               ScreenFx.flashAfterHit(0.9F);
            }

            if (t == 92 && close) {
               screenHit(ScreenFx.Hit.HEAVY, new float[]{0.55F, 0.1F, 0.9F});
            }
            break;
         case DISMANTLE:
            if (t == 2) {
               a.strokes
                  .add(
                     new Casts.Stroke(
                        Slash.of(m.origin.subtract(m.dir.scale(0.5)), m.dir, Slash.roll(m.seed, 0), (double)m.size, 16.0),
                        (double)(m.startTick + (long)t),
                        0,
                        380.0,
                        1.0F,
                        1.0F
                     )
                  );
               if (close) {
                  hit(ScreenFx.Hit.LIGHT, m.origin.add(m.dir.scale(5.0)), CRIMSON);
               }
            }
            break;
         case BARRAGE:
            int k = t - 3;
            if (k >= 0 && k % 2 == 0 && k / 2 < 16) {
               int i = k / 2;
               Vec3 eye = caster != null ? caster.getEyePosition() : m.origin;
               Vec3 look = caster != null ? caster.getLookAngle() : m.dir;
               Vec3 d = Slash.jitter(m.seed, i, look, 0.35);
               a.strokes
                  .add(
                     new Casts.Stroke(
                        Slash.of(eye.subtract(d.scale(0.5)), d, Slash.roll(m.seed, i), (double)m.size * 0.75, 11.0),
                        (double)(m.startTick + (long)t),
                        0,
                        420.0,
                        (float)i,
                        0.85F
                     )
                  );
               if (close && (i == 0 || i == 15)) {
                  hit(i == 0 ? ScreenFx.Hit.LIGHT : ScreenFx.Hit.HEAVY, eye.add(d.scale(6.0)), CRIMSON);
               } else if (close) {
                  ScreenFx.cutFlash(0.08F);
               }
            }
            break;
         case CLEAVE:
            if (t == 3) {
               Entity target = mc.level.getEntity(m.targetId);
               Vec3 c = target != null ? target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0) : m.point;
               double size = target != null ? (double)Math.max(target.getBbWidth(), target.getBbHeight()) : (double)Math.max(1.0F, m.size);
               RandomSource r = RandomSource.create(m.seed);

               for (int i = 0; i < 16; i++) {
                  Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
                  Vec3 off = new Vec3(r.nextGaussian() * 0.25, r.nextGaussian() * 0.4, r.nextGaussian() * 0.25).scale(size);
                  double len = size * (1.1 + r.nextDouble() * 0.6);
                  Vec3 o = c.add(off).subtract(dir.scale(len * 0.5));
                  a.strokes.add(new Casts.Stroke(Slash.of(o, dir, 0.0, len, 0.0), (double)(m.startTick + (long)t) + (double)i * 0.25, 1, 0.0, (float)i, 1.0F));
               }

               if (close) {
                  hit(ScreenFx.Hit.HEAVY, c, CRIMSON);
               }
            }
            break;
         case BLACK_FLASH:
            if (t == 4 && close) {
               hit(ScreenFx.Hit.HEAVY, m.point, new float[]{1.0F, 0.0F, 0.12F});
               ScreenFx.shock(m.point, 0.8F);
            }
            break;
         case SPIDERWEB:
            if (t == 7 && close) {
               hit(ScreenFx.Hit.HEAVY, m.point, CRIMSON);
            }
      }
   }

   static Vec3[] basis(Vec3 aim) {
      Vec3 right = aim.cross(new Vec3(0.0, 1.0, 0.0));
      if (right.lengthSqr() < 1.0E-6) {
         right = new Vec3(1.0, 0.0, 0.0);
      }

      right = right.normalize();
      Vec3 up = right.cross(aim).normalize();
      return new Vec3[]{aim, right, up};
   }

   static Vec3 eyeOf(Entity caster, S2CCast m, float partial) {
      return caster != null ? caster.getEyePosition(partial) : m.origin;
   }

   static Vec3 aimOf(Entity caster, S2CCast m, float partial) {
      return caster != null ? caster.getViewVector(partial) : m.dir;
   }

   public static Vec3 fugaHand(Entity caster, S2CCast m, float partial) {
      Vec3[] b = basis(aimOf(caster, m, partial));
      return eyeOf(caster, m, partial).add(b[0].scale(0.55)).add(b[1].scale(0.32)).subtract(b[2].scale(0.3));
   }

   public static Vec3 fugaBow(Entity caster, S2CCast m, float partial) {
      Vec3[] b = basis(aimOf(caster, m, partial));
      return eyeOf(caster, m, partial).add(b[0].scale(0.8)).subtract(b[2].scale(0.18)).add(b[1].scale(0.04));
   }

   private static float smooth(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   public static Casts.Frame frame(float partial) {
      Casts.Frame f = new Casts.Frame();
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return f;
      } else {
         double now = now(partial);

         for (Casts.Active a : ACTIVE) {
            S2CCast m = a.msg;
            float t = (float)(now - (double)m.startTick);
            Entity caster = mc.level.getEntity(m.casterId);

            for (Casts.Stroke s : a.strokes) {
               float age = (float)((now - s.startTick) / 20.0);
               if (!(age > 3.0F) && f.slashes.size() < 48) {
                  Slash sl = s.slash;
                  f.slashes
                     .add(
                        new float[]{
                           (float)sl.origin().x,
                           (float)sl.origin().y,
                           (float)sl.origin().z,
                           age,
                           (float)sl.dir().x,
                           (float)sl.dir().y,
                           (float)sl.dir().z,
                           (float)sl.length(),
                           (float)sl.side().x,
                           (float)sl.side().y,
                           (float)sl.side().z,
                           (float)sl.width(),
                           (float)s.kind,
                           (float)s.speed,
                           s.seed,
                           s.intensity
                        }
                     );
               }
            }

            switch (m.move) {
               case FUGA:
                  fugaFrame(f, a, caster, t, partial);
                  break;
               case DOMAIN:
                  float open = t - 52.0F;
                  if (open >= 0.0F) {
                     float g = Mth.clamp(open / 40.0F, 0.0F, 1.0F);
                     f.domain = true;
                     f.domC = m.point;
                     f.domFull = m.size;
                     f.domR = (float)((double)m.size * (1.0 - Math.pow((double)(1.0F - g), 2.5)));
                     f.domAge = open / 20.0F;
                     float left = ((float)(a.endTick - 20L) - (float)now) / 20.0F;
                     f.domStrength = smooth(open / 6.0F) * smooth(left);
                  } else if (t >= 26.0F) {
                     f.domain = true;
                     f.domC = m.point;
                     f.domFull = m.size;
                     f.domR = 1.5F;
                     f.domAge = 0.0F;
                     f.domStrength = 0.35F * smooth((t - 26.0F) / 20.0F);
                  }
                  break;
               case WORLD_SLASH:
                  if (t >= 78.0F) {
                     f.ws = true;
                     f.wsO = m.point;
                     f.wsD = m.dir;
                     f.wsLen = m.size;
                     f.wsAge = (t - 78.0F) / 20.0F;
                     float split = smooth((t - 92.0F) / 16.0F) * (1.0F - smooth((t - 200.0F) / 30.0F));
                     f.wsSplit = 0.028F * split;
                     f.wsSky = 1.0F - smooth((t - 200.0F - 40.0F) / 80.0F);
                  }
               case DISMANTLE:
               case BARRAGE:
               case CLEAVE:
               default:
                  break;
               case BLACK_FLASH:
                  float agex = (t - 4.0F) / 20.0F;
                  if (agex >= 0.0F && agex < 0.7F) {
                     f.bfC = m.point;
                     f.bfAge = agex;
                     f.bfSeed = (float)(m.seed & 1023L) / 11.0F;
                  }
                  break;
               case SPIDERWEB:
                  float age = (t - 7.0F) / 20.0F;
                  if (age >= 0.0F && age < 3.5F) {
                     f.webC = m.point;
                     f.webR = m.size;
                     f.webAge = age;
                     f.webSeed = (float)(m.seed & 1023L) / 7.0F;
                  }
            }
         }

         return f;
      }
   }

   private static void fugaFrame(Casts.Frame f, Casts.Active a, Entity caster, float t, float partial) {
      S2CCast m = a.msg;
      if (!(t > 249.0F)) {
         f.fuga = true;
         f.fRadius = m.size;
         f.fTarget = m.point;
         Vec3 aim = a.fugaAim != null ? a.fugaAim : aimOf(caster, m, partial);
         Vec3[] b = basis(aim);
         f.fDir = b[0];
         f.fUp = b[2];
         f.fHand = fugaHand(caster, m, partial);
         f.fBow = a.fugaFrom != null ? a.fugaFrom : fugaBow(caster, m, partial);
         f.fFrom = a.fugaFrom != null ? a.fugaFrom : f.fBow;
         float released = smooth((t - 80.0F) / 5.0F);
         f.fHandAmt = smooth(t / 14.0F) * (1.0F - 0.75F * smooth((t - 30.0F) / 14.0F)) * (1.0F - released);
         f.fBowAmt = smooth((t - 30.0F) / 12.0F) * (1.0F - released);
         float d = Mth.clamp((t - 48.0F) / 28.0F, 0.0F, 1.0F);
         f.fDraw = (1.0F - (1.0F - d) * (1.0F - d) * (1.0F - d)) * (1.0F - released);
         if (t >= 80.0F && t <= 89.0F) {
            f.fFlight = (t - 80.0F) / 9.0F;
            f.fFlight = f.fFlight * f.fFlight * (0.4F + 0.6F * f.fFlight);
         }

         if (t >= 89.0F) {
            f.fBlast = (t - 89.0F) / 20.0F;
         }
      }
   }

   public static boolean any() {
      return !ACTIVE.isEmpty();
   }

   static final class Active {
      final S2CCast msg;
      final List<Casts.Stroke> strokes = new ArrayList<>();
      int lastTick = -1;
      Vec3 fugaFrom;
      Vec3 fugaAim;
      long endTick;

      Active(S2CCast msg) {
         this.msg = msg;
         long var10001 = msg.startTick;

         this.endTick = var10001 + switch (msg.move) {
            case FUGA -> 269L;
            case DOMAIN -> 52L + (long)msg.targetId * 20L + 20L;
            case WORLD_SLASH -> 358L;
            case DISMANTLE -> 80L;
            case BARRAGE -> 115L;
            case CLEAVE, BLACK_FLASH -> 40L;
            case SPIDERWEB -> 80L;
         };
      }
   }

   public static final class Frame {
      public final List<float[]> slashes = new ArrayList<>();
      public Vec3 webC;
      public float webR;
      public float webAge = -1.0F;
      public float webSeed;
      public Vec3 bfC;
      public float bfAge = -1.0F;
      public float bfSeed;
      public boolean fuga;
      public Vec3 fHand;
      public Vec3 fBow;
      public Vec3 fDir;
      public Vec3 fUp;
      public Vec3 fFrom;
      public Vec3 fTarget;
      public float fHandAmt;
      public float fBowAmt;
      public float fDraw;
      public float fFlight = -1.0F;
      public float fBlast = -1.0F;
      public float fRadius;
      public Vec3 domC;
      public float domR;
      public float domAge;
      public float domStrength;
      public float domFull;
      public boolean domain;
      public Vec3 wsO;
      public Vec3 wsD;
      public float wsAge = -1.0F;
      public float wsLen;
      public float wsSplit;
      public float wsSky;
      public boolean ws;
      public Vec3 shake;

      public boolean any() {
         return !this.slashes.isEmpty() || this.webAge >= 0.0F || this.bfAge >= 0.0F || this.fuga || this.domain || this.ws;
      }
   }

   static record Stroke(Slash slash, double startTick, int kind, double speed, float seed, float intensity) {
   }
}
