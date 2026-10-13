package com.geto.csm.client;

import com.geto.csm.CurseKind;
import com.geto.csm.network.FxPacket;
import com.geto.csm.network.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class FxDispatcher {
   private static final int VIOLET = -6595329;
   private static final int WHITE = -1;
   private static final int EMBER = -34262;
   private static final int RED = -2086852;
   private static final int CYAN = -11540768;
   private static final int GOLD = -868018;

   static boolean feels(Vec3 pos, double range) {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null && mc.player.position().distanceTo(pos) < range;
   }

   private static boolean involves(FxPacket p) {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null && (p.src == mc.player.getId() || p.tgt == mc.player.getId());
   }

   private static boolean feels(FxPacket p, Vec3 pos, double range) {
      return involves(p) || feels(pos, range);
   }

   static Vec3 groundBelow(Vec3 pos, int depth) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      } else {
         MutableBlockPos bp = BlockPos.containing(pos).mutable();
         int i = 0;

         while (i <= depth) {
            if (!mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) {
               return new Vec3(pos.x, (double)bp.getY() + 1.0, pos.z);
            }

            i++;
            bp.move(0, -1, 0);
         }

         return null;
      }
   }

   public static void handle(FxPacket p) {
      Vec3 pos = new Vec3(p.x, p.y, p.z);
      FxType type = FxType.byId(p.type);
      switch (type) {
         case RIFT:
            CurseKind k = CurseKind.byId((int)p.a);

            double var24 = switch (k) {
               case WYRM, ATROXIIA, RAEVYX, TAMAMO -> 3.4;
               case CINDERVANE -> 2.6;
               default -> 1.8;
            };
            Vec3 groundx = k != CurseKind.WYRM && k != CurseKind.TAMAMO && k != CurseKind.CINDERVANE && k != CurseKind.RAEVYX ? pos : pos.add(0.0, -0.4, 0.0);
            Vfx.add(new CsmFx.Rift(groundx, k.color, var24, k.special ? 2400L : 1500L));
            Vfx.add(new Vfx.Spikes(pos.add(0.0, 0.5, 0.0), 18, var24 * 1.4, -6595329, 380L, new Vec3(0.0, 1.0, 0.0), 0.6));
            if (p.b < 0.5F) {
               Vfx.add(Vfx.Shockwave.ground(groundx, var24 * 2.2, -6595329, 600L));
               if (feels(p, pos, 20.0)) {
                  CameraDirector.shake(k.special ? 1.4F : 0.5F, 400L);
               }
            }
            break;
         case VANISH:
            Vfx.add(new Vfx.Flash(pos, 1.6, -6595329, 350L));
            Vfx.add(new Vfx.Spikes(pos, 16, 2.4, -6595329, 320L));
            SpineTrail.forget(p.src);
            break;
         case ORB_FORM:
            Vfx.add(new CsmFx.OrbForm(pos, 900L));
            Vfx.add(Vfx.Shockwave.ground(pos.add(0.0, -0.4, 0.0), 2.0, -6595329, 500L));
            break;
         case ABSORB:
            Vfx.add(new Vfx.Flash(pos.add(0.0, -0.3, 0.0), 0.6, -6595329, 300L));
            if (involves(p)) {
               ScreenFx.card(new ScreenFx.Card("吸収", "CURSE ABSORBED · " + (int)p.a + " STORED", "", 2, -6595329, 900L, false));
            }
            break;
         case ACTION:
            CurseAnim.trigger(p.src, (int)p.a);
            break;
         case BITE: {
            boolean big = p.b > 0.5F;
            Vfx.add(new Vfx.Flash(pos, big ? 2.0 : 1.2, -1, 260L));
            Vfx.add(new Vfx.Spikes(pos, big ? 30 : 16, big ? 4.0 : 2.4, -34262, 300L));
            Vec3 ground = groundBelow(pos, 3);
            if (ground != null) {
               Vfx.add(new Vfx.Debris(ground, big ? 14 : 6, 0.8, 1300L));
            }

            if (feels(p, pos, 18.0)) {
               CameraDirector.shake(big ? 0.8F : 0.35F, 300L);
            }
            break;
         }
         case TAIL_SWEEP:
            double r = (double)Math.max(3.0F, p.b);
            Vfx.add(Vfx.Shockwave.ground(pos.add(0.0, 0.3, 0.0), r, -1, 450L));
            Vfx.add(Vfx.Shockwave.ground(pos.add(0.0, 0.8, 0.0), r * 1.3, -34262, 600L));
            Vfx.add(new Vfx.Debris(pos, 20, 1.2, 1500L));
            if (feels(p, pos, r + 10.0)) {
               CameraDirector.shake(0.9F, 400L);
            }
            break;
         case ROAR_BEAM: {
            Vec3 dir = Draw.forward(p.a, p.b);
            Vfx.add(new CsmFx.BreathCone(pos, dir, 24.0, -6595329, -34262, 1300L));
            Vfx.add(Vfx.Shockwave.facing(pos.add(dir.scale(2.0)), dir, 4.0, -1, 400L));
            if (feels(p, pos, 30.0)) {
               CameraDirector.shake(1.8F, 1100L);
               ScreenFx.speed(900L, 0.7F);
            }
            break;
         }
         case WORM_EMERGE:
            Vfx.add(Vfx.Shockwave.ground(pos, 6.0, -5215296, 700L));
            Vfx.add(new Vfx.Debris(pos, 34, 1.6, 2000L));
            Vfx.add(new Vfx.Spikes(pos.add(0.0, 0.5, 0.0), 26, 5.0, -1, 400L, new Vec3(0.0, 1.0, 0.0), 1.2));
            if (feels(p, pos, 24.0)) {
               CameraDirector.shake(2.2F, 800L);
            }
            break;
         case WORM_SWALLOW: {
            Vfx.add(new Vfx.Flash(pos, 2.2, -54704, 300L));
            if (feels(p, pos, 20.0)) {
               CameraDirector.shake(1.4F, 500L);
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && p.tgt == mc.player.getId()) {
               ScreenFx.vignette(1.0F);
               ScreenFx.card(new ScreenFx.Card("SWALLOWED", "", "", 0, -2086852, 1400L, false));
               Sfx.later(3400L, () -> ScreenFx.vignette(0.0F));
            }
            break;
         }
         case WORM_SPIT:
            Vfx.add(new Vfx.Flash(pos, 1.6, -5215296, 260L));
            Vfx.add(new Vfx.Spikes(pos, 20, 3.0, -5215296, 300L));
            break;
         case GUST:
            Vfx.add(Vfx.Shockwave.ground(pos, 6.0, -11540768, 500L));
            Vfx.add(Vfx.Shockwave.ground(pos.add(0.0, 0.6, 0.0), 4.0, -1, 400L));
            if (feels(p, pos, 14.0)) {
               CameraDirector.shake(1.2F, 400L);
            }
            break;
         case CENTI_BITE:
            boolean venom = p.b > 0.5F;
            Vfx.add(new Vfx.Flash(pos, venom ? 2.0 : 0.5, -6488262, venom ? 320L : 160L));
            Vfx.add(new Vfx.Spikes(pos, venom ? 24 : 6, venom ? 3.0 : 0.9, -6488262, 220L));
            if (venom) {
               Vfx.add(Vfx.Shockwave.ground(pos, 3.5, -6488262, 450L));
               if (feels(p, pos, 14.0)) {
                  CameraDirector.shake(1.0F, 350L);
               }
            }
            break;
         case PYRE_PULSE: {
            float k = (p.a - 120.0F) / 2880.0F;
            int mc = Draw.lerp(-46576, -3904, k);
            Vfx.add(Vfx.Shockwave.ground(pos, (double)p.b, mc, 700L));
            break;
         }
         case PYRE_FLARE: {
            float k = (p.b - 120.0F) / 2880.0F;
            int mc = Draw.lerp(-46576, -3904, k);
            Entity t = Vfx.entity(p.tgt);
            Vec3 dir = t != null ? t.position().add(0.0, 1.0, 0.0).subtract(pos) : Draw.forward(p.a, 0.0F);
            Vfx.add(new CsmFx.BreathCone(pos, dir, 10.0, mc, -54784, 900L));
            Sfx.later(400L, () -> {
               Vec3 hit = pos.add(dir.normalize().scale(5.0));
               Vfx.add(new Vfx.Flash(hit, 2.5, mc, 350L));
               Vfx.add(Vfx.Shockwave.ground(hit.add(0.0, -1.0, 0.0), 6.0, mc, 600L));
               Vfx.add(new Vfx.Pillar(hit.add(0.0, -1.2, 0.0), (double)(6.0F + k * 10.0F), 1.2, mc, 700L));
               if (feels(hit, 20.0)) {
                  CameraDirector.shake(1.5F, 450L);
               }
            });
            break;
         }
         case KUCHI_DOMAIN: {
            Vfx.add(new CsmFx.VowDome(pos, (double)p.a, (long)(p.b * 50.0F)));
            if (feels(p, pos, 16.0)) {
               ScreenFx.impact(2, 160L, pos);
               CameraDirector.shake(0.8F, 400L);
               Sfx.play(SoundEvents.BELL_RESONATE, 0.5F, 0.8F);
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && p.tgt == mc.player.getId()) {
               ScreenFx.rules(2200L);
            }
            break;
         }
         case SHEARS: {
            boolean big = p.b > 0.5F;
            Vfx.add(new CsmFx.ShearsFx(p.tgt, pos, big, big ? 900L : 380L));
            break;
         }
         case MOUTH_SLASH:
            Vfx.add(new Vfx.Flash(pos, 1.0, -2086852, 300L));
            Vfx.add(new Vfx.Spikes(pos, 22, 2.4, -2086852, 300L));
            if (feels(p, pos, 16.0)) {
               CameraDirector.shake(1.4F, 400L);
            }
            break;
         case FOXFIRE:
            Vfx.add(new CsmFx.Foxfire(pos, p.tgt, (int)p.a, 900L));
            break;
         case KILLING_STONE:
            Vfx.add(new CsmFx.KillingStone(pos, 7000L));
            if (feels(p, pos, 30.0)) {
               ScreenFx.card(new ScreenFx.Card("殺生石", "KILLING STONE", "", 3, -6595329, 1500L, false));
            }
            break;
         case MIASMA:
            Vfx.add(new CsmFx.Miasma(pos, (double)Math.max(3.0F, p.a), 6000L));
            break;
         case QUAKE_WAVE:
            Vfx.add(Vfx.Shockwave.ground(pos, (double)p.a, -3558806, 600L));
            Vfx.add(new Vfx.Debris(pos, (int)(p.a * 3.0F), 1.0, 1400L));
            if (feels(pos, (double)(p.a + 8.0F))) {
               CameraDirector.shake(2.0F, 500L);
            }
            break;
         case UZUMAKI_CORE:
            Vfx.add(new CsmFx.UzuCore(pos, (int)p.a, 4400L));
            break;
         case UZUMAKI_GATHER: {
            Entity src = Vfx.entity(p.src);
            Vec3 from = src != null ? src.position().add(0.0, (double)src.getBbHeight() * 0.5, 0.0) : pos.add(5.0, 2.0, 0.0);
            CurseKind k = CurseKind.byId((int)p.a);
            Vfx.add(new CsmFx.UzuStream(from, pos, k.color, 1700L));
            Vfx.add(new Vfx.Flash(from, 2.5, k.color, 400L));
            break;
         }
         case UZUMAKI_BEAM: {
            Vec3 dir = Draw.forward(p.a, p.b);
            Vfx.add(new CsmFx.UzuBeam(pos, dir, 1700L));

            for (int i = 0; i < 5; i++) {
               Sfx.later(
                  (long)i * 60L,
                  () -> Vfx.add(Vfx.Shockwave.facing(pos.add(dir.scale((double)(3 + i * 7))), dir, 5.0 + (double)i * 1.5, i % 2 == 0 ? -1 : -6595329, 600L))
               );
            }

            Vfx.add(new Vfx.Spikes(pos, 60, 10.0, -6595329, 500L, dir, 1.6));
            if (feels(pos, 60.0)) {
               ScreenFx.flash(-1517313, 500L);
               CameraDirector.shake(3.4F, 1200L);
            }
            break;
         }
         case SMALL_HIT:
            Vfx.add(new Vfx.Flash(pos, 0.8, -1, 200L));
            Vfx.add(new Vfx.Spikes(pos, 10, 1.6, -6595329, 220L));
      }
   }

   private FxDispatcher() {
   }
}
