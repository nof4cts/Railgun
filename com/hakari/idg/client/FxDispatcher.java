package com.hakari.idg.client;

import com.hakari.idg.client.post.Post;
import com.hakari.idg.network.FxPacket;
import com.hakari.idg.network.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class FxDispatcher {
   private static final int PINK = -45128;
   private static final int HOT = -53894;
   private static final int GOLD = -11443;
   private static final int WHITE = -1;
   private static final int CYAN = -10685697;
   private static final int GREEN = -12976262;
   private static final int MINT = -3604520;

   private static boolean feels(FxPacket p, Vec3 pos, double range) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      } else {
         int me = mc.player.getId();
         return p.src == me || p.tgt == me || mc.player.position().distanceTo(pos) < range;
      }
   }

   private static boolean isMe(int id) {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null && mc.player.getId() == id;
   }

   public static void handle(FxPacket p) {
      Vec3 pos = new Vec3(p.x, p.y, p.z);
      FxType type = FxType.byId(p.type);
      switch (type) {
         case FLICK:
            Vfx.add(new Vfx.Flash(pos, 0.35, -45128, 160L));
            Vfx.add(new Vfx.Spikes(pos, 7, 0.7, -1, 180L));
            break;
         case BALL_IMPACT:
            boolean close = p.a > 0.5F;
            Vfx.add(new Vfx.Flash(pos, close ? 1.4 : 0.8, -45128, 260L));
            Vfx.add(new Vfx.Spikes(pos, close ? 22 : 12, close ? 2.6 : 1.4, -45128, 260L));
            Vfx.add(Vfx.Shockwave.ground(pos.add(0.0, -0.3, 0.0), close ? 3.0 : 1.6, -45128, 380L));
            Vfx.add(new Vfx.Debris(pos, close ? 10 : 5, 0.6, 1100L));
            if (close && feels(p, pos, 14.0)) {
               CameraDirector.shake(0.9F, 300L);
            }
            break;
         case DOORS_SLAM:
            boolean fast = p.b > 0.5F;
            long closeAt = fast ? 200L : 450L;
            Vfx.add(new Vfx.Doors(pos, p.a, fast, false, fast ? 900L : 1300L));
            Sfx.later(closeAt, () -> {
               Vec3 mid = pos.add(0.0, 1.6, 0.0);
               Vfx.add(new Vfx.Flash(mid, 1.6, -45128, 300L));
               Vfx.add(new Vfx.Spikes(mid, 26, 3.2, -45128, 320L));
               Vfx.add(Vfx.Shockwave.ground(pos, 4.5, -45128, 450L));
               Vfx.add(new Vfx.Debris(pos, 12, 0.8, 1300L));
               if (feels(p, pos, 18.0)) {
                  Post.punch(0.5F, 300L);
                  CameraDirector.shake(1.3F, 350L);
               }
            });
            break;
         case ROUGH_CHARGE:
            Vfx.add(new Vfx.Charge(p.src, 720L));
            if (isMe(p.src)) {
               ScreenFx.speed(700L, 0.5F);
            }
            break;
         case ROUGH_HIT:
            Vec3 dirRough = Draw.forward(p.a, p.b);
            Vfx.add(new Vfx.Flash(pos, 2.6, -12976262, 380L));

            for (int i = 0; i < 3; i++) {
               int ii = i;
               Sfx.later(
                  (long)i * 45L,
                  () -> Vfx.add(
                        Vfx.Shockwave.facing(pos.add(dirRough.scale(0.8 + (double)ii * 1.3)), dirRough, 1.6 + (double)ii * 1.1, ii == 0 ? -1 : -12976262, 420L)
                     )
               );
            }

            Vfx.add(new Vfx.Spikes(pos, 40, 5.5, -12976262, 420L, dirRough, 1.4));
            Vfx.add(new Vfx.Debris(pos.add(0.0, -1.3, 0.0), 22, 1.1, 1500L));
            Vfx.add(Vfx.Shockwave.ground(pos.add(0.0, -1.4, 0.0), 6.0, -12976262, 600L));

            for (int i = 0; i < 4; i++) {
               Vfx.add(new Vfx.Bolt(pos, pos.add(dirRough.scale(2.5)).add(Draw.randDir((long)i + Vfx.now()).scale(1.2)), -12976262, 260L));
            }

            if (feels(p, pos, 22.0)) {
               Post.punch(1.0F, 420L);
               Post.aberration(1.2F, 600L);
               Post.flash(13172696, 0.6F, 260L);
               CameraDirector.shake(2.2F, 650L);
               ScreenFx.speed(400L, 1.0F);
            }
            break;
         case FEVER_KICK1:
            Vfx.add(new Vfx.Trail(p.src, -12976262, 450L));
            Vfx.add(Vfx.Shockwave.ground(pos, 2.0, -1, 300L));
            break;
         case FEVER_KICK2:
            Vec3 dirxx = Draw.forward(p.a, -20.0F);
            Vfx.add(new Vfx.Flash(pos, 1.8, -12976262, 300L));
            Vfx.add(new Vfx.Spikes(pos, 26, 3.4, -12976262, 320L, dirxx, 1.0));
            Vfx.add(Vfx.Shockwave.facing(pos, dirxx, 2.6, -1, 350L));
            if (feels(p, pos, 18.0)) {
               Post.punch(0.6F, 300L);
               CameraDirector.shake(1.4F, 380L);
            }
            break;
         case COUNTER_READY:
            Vfx.add(new Vfx.Doors(pos, p.a, false, true, 700L));
            break;
         case COUNTER_SLAM:
            Vfx.add(new Vfx.Doors(pos, p.a, true, false, 900L));
            Sfx.later(200L, () -> {
               Vec3 mid = pos.add(0.0, 1.4, 0.0);
               Vfx.add(new Vfx.Flash(mid, 2.0, -12976262, 320L));
               Vfx.add(new Vfx.Spikes(mid, 30, 3.6, -1, 340L));
               Vfx.add(Vfx.Shockwave.ground(pos, 5.0, -12976262, 500L));
               if (feels(p, pos, 18.0)) {
                  Post.punch(0.8F, 350L);
                  CameraDirector.shake(1.6F, 400L);
                  ScreenFx.card(new ScreenFx.Card("COUNTER", "", "", 2, -45128, 700L, false));
               }
            });
            break;
         case VISUAL_TICK:
            Vfx.add(Vfx.Shockwave.ground(pos, 2.4, -11443, 500L));
            if (feels(p, pos, 24.0)) {
               Sfx.play((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F + p.a * 0.3F, 0.8F);
               ScreenFx.card(new ScreenFx.Card(p.a >= 2.0F ? "SPIN!" : "VISUAL " + (int)p.a + "/2", "", "", 2, -11443, 650L, p.a >= 2.0F));
            }
            break;
         case VOLLEY_FIST:
            Vfx.add(new Vfx.Fists(p.src, -12976262, (long)(p.b * 50.0F)));
            if (isMe(p.src)) {
               ScreenFx.speed((long)(p.b * 50.0F), 0.8F);
            }
            break;
         case VOLLEY_FINISH:
            Vec3 dirx = Draw.forward(p.a, 0.0F);
            Vfx.add(new Vfx.Flash(pos, 2.4, -12976262, 360L));
            Vfx.add(new Vfx.Spikes(pos, 40, 5.0, -12976262, 400L, dirx, 1.2));
            Vfx.add(Vfx.Shockwave.facing(pos, dirx, 3.4, -1, 400L));
            Vfx.add(Vfx.Shockwave.facing(pos.add(dirx), dirx, 4.4, -11443, 460L));
            if (feels(p, pos, 22.0)) {
               Post.punch(1.0F, 450L);
               Post.aberration(1.0F, 600L);
               CameraDirector.shake(2.0F, 500L);
            }
            break;
         case RUSH_DASH:
            long life = p.b > 0.5F ? 2700L : 600L;
            Vfx.add(new Vfx.Trail(p.src, -12976262, life));
            Vfx.add(Vfx.Shockwave.ground(pos, 3.0, -11443, 400L));
            Vfx.add(new Vfx.Debris(pos, 8, 0.6, 1000L));
            break;
         case RUSH_SLAM:
            Vfx.add(new Vfx.Flash(pos, 2.4, -12976262, 360L));
            Vfx.add(new Vfx.Spikes(pos, 36, 5.0, -12976262, 380L, new Vec3(0.0, -1.0, 0.0), 1.3));
            if (feels(p, pos, 22.0)) {
               Post.punch(0.9F, 400L);
               CameraDirector.shake(2.2F, 550L);
            }
            break;
         case OVERWHELM_HIT:
            Vfx.add(new Vfx.Flash(pos, 1.3, -12976262, 220L));
            Vfx.add(new Vfx.Spikes(pos, 14, 2.4, -12976262, 240L));
            if (feels(p, pos, 16.0)) {
               CameraDirector.shake(0.7F, 220L);
            }
            break;
         case OVERWHELM_THROW:
            Vec3 dirThrow = Draw.forward(p.a, 0.0F);
            Vfx.add(new Vfx.Flash(pos, 3.4, -12976262, 450L));

            for (int i = 0; i < 4; i++) {
               int ii = i;
               Sfx.later(
                  (long)i * 40L,
                  () -> Vfx.add(
                        Vfx.Shockwave.facing(pos.add(dirThrow.scale((double)ii * 1.5)), dirThrow, 2.0 + (double)ii * 1.3, ii % 2 == 0 ? -1 : -11443, 480L)
                     )
               );
            }

            Vfx.add(new Vfx.Spikes(pos, 48, 7.0, -12976262, 480L, dirThrow, 1.5));
            Vfx.add(new Vfx.Debris(pos.add(0.0, -1.0, 0.0), 26, 1.3, 1700L));
            Vfx.add(Vfx.Shockwave.ground(pos.add(0.0, -1.0, 0.0), 8.0, -11443, 700L));
            if (feels(p, pos, 26.0)) {
               Post.punch(1.2F, 600L);
               Post.aberration(1.4F, 800L);
               Post.flash(16777215, 1.2F, 300L);
               float ang = (float)(Math.random() * 50.0 - 25.0);
               ScreenFx.split(ang, 480L, 22.0F);
               CameraDirector.shake(3.0F, 800L);
            }
            break;
         case SURGE_LEAP:
            Vfx.add(new Vfx.Trail(p.src, -12976262, 900L));
            Vfx.add(Vfx.Shockwave.ground(pos, 3.5, -1, 450L));
            Vfx.add(new Vfx.Debris(pos, 14, 0.9, 1200L));
            break;
         case SURGE_SLAM:
            double r = (double)Math.max(2.0F, p.b);
            Vfx.add(Vfx.Shockwave.ground(pos, r * 1.4, -11443, 650L));
            Sfx.later(70L, () -> Vfx.add(Vfx.Shockwave.ground(pos, r * 2.2, -1, 800L)));
            Vfx.add(new Vfx.Debris(pos, (int)(r * 5.0), 1.4, 1800L));
            Vfx.add(new Vfx.Flash(pos.add(0.0, 0.5, 0.0), r * 0.5, -12976262, 380L));
            Vfx.add(new Vfx.Spikes(pos.add(0.0, 0.3, 0.0), 30, r, -1, 400L, new Vec3(0.0, 1.0, 0.0), 0.8));
            Vfx.add(new Vfx.Pillar(pos, 8.0, 1.2, -12976262, 500L));
            if (feels(p, pos, 26.0)) {
               Post.punch(1.0F, 500L);
               Post.aberration(1.0F, 700L);
               CameraDirector.shake(2.6F, 700L);
            }
            break;
         case SURGE_KICK:
            Vec3 dir = Draw.forward(p.a, -10.0F);
            Vfx.add(new Vfx.Flash(pos, 2.0, -12976262, 300L));
            Vfx.add(Vfx.Shockwave.facing(pos, dir, 3.0, -1, 360L));
            Vfx.add(new Vfx.Spikes(pos, 28, 4.0, -12976262, 340L, dir, 1.2));
            if (feels(p, pos, 20.0)) {
               CameraDirector.shake(1.5F, 400L);
            }
            break;
         case RHYTHM:
            Vfx.add(new Vfx.RhythmPulse(p.src, 10000L));
            Vfx.add(new Vfx.Flash(pos.add(0.0, 1.0, 0.0), 1.6, -12976262, 300L));
            if (isMe(p.src)) {
               ScreenFx.card(new ScreenFx.Card("RHYTHM", "cooldowns reset · moves accelerate", "", 2, -10685697, 1100L, false));
            }
            break;
         case DOMAIN_COLLAPSE:
         case DOMAIN_SHATTER:
            boolean gold = type == FxType.DOMAIN_SHATTER;
            double R = (double)Math.max(4.0F, p.a);
            Vfx.add(new Vfx.Shatter(pos, R, gold ? -11443 : -7705984, 2200L));
            Vfx.add(new Vfx.Flash(pos.add(0.0, 1.0, 0.0), R * 0.4, gold ? -11443 : -45128, 500L));
            if (feels(p, pos, R + 10.0)) {
               CameraDirector.shake(gold ? 1.8F : 1.0F, 700L);
               if (!gold) {
                  ScreenFx.card(new ScreenFx.Card("BURNOUT", "the domain collapses", "", 2, -5592406, 1200L, false));
               }
            }
            break;
         case JACKPOT_END:
            Vfx.add(Vfx.Shockwave.ground(pos, 5.0, -11443, 900L));
            if (isMe(p.src)) {
               ScreenFx.card(new ScreenFx.Card("JACKPOT OVER", "the domain is ready — spin again", "", 3, -11443, 2200L, false));
            }
            break;
         case SMALL_HIT:
            Vfx.add(new Vfx.Flash(pos, 0.9, -1, 200L));
            Vfx.add(new Vfx.Spikes(pos, 10, 1.8, -12976262, 220L));
            if (feels(p, pos, 8.0)) {
               CameraDirector.shake(0.5F, 200L);
            }
      }
   }

   static Entity entity(int id) {
      return Vfx.entity(id);
   }

   private FxDispatcher() {
   }
}
