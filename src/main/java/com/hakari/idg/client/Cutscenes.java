package com.hakari.idg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Cutscenes {
   private static Vec3 anchorPos(int id) {
      Minecraft mc = Minecraft.getInstance();
      Entity e = mc.level == null ? null : mc.level.getEntity(id);
      return e == null ? null : e.position().add(0.0, 1.2, 0.0);
   }

   /**
    * Domain Expansion: Idle Death Gamble. Nine seconds (the server holds everyone for exactly that long):
    *  0.00 the hand sign, the world going quiet           1.20 eyes, "DOMAIN EXPANSION", the first frames
    *  1.95 green cursed energy erupts off him              2.45 the barrier blooms and swallows the land, crane out
    *  3.95 white-out: inside the parlour                   4.30 the express tears through the station
    *  5.55 the title, rainbow "confirmed" frames           6.95 the reels spin           8.15 hand back
    */
   public static Cutscene domain(int caster, boolean victim, boolean boosted) {
      Cutscene c = new Cutscene(caster, 9.0, false);
      // the hand sign: low, slow push on his hands and face
      c.key(0.0, 2.1, 0.7, -0.35, 0.0, 0.0, -0.15, 44.0, 0.0, Ease.LINEAR);
      c.key(1.15, 1.25, 0.35, -0.2, 0.0, 0.0, -0.1, 33.0, -3.0, Ease.IN_OUT);
      // his eyes as he names it
      c.cut(1.2, 0.95, 0.12, 0.02, 0.0, 0.0, 0.0, 28.0, 0.0);
      c.key(1.9, 0.78, 0.08, 0.02, 0.0, 0.0, 0.0, 24.0, 2.0, Ease.IN_OUT);
      // low behind him, looking up, as the energy erupts
      c.cut(1.95, -2.6, 1.6, -1.2, 1.5, 0.0, 0.8, 72.0, 9.0);
      c.key(2.4, -3.0, 1.9, -1.0, 1.5, 0.0, 1.0, 76.0, 6.0, Ease.OUT_CUBIC);
      // the barrier: crane up and out as it blooms and eats the world
      c.cut(2.45, -4.0, 3.2, 0.6, 0.0, 0.0, -0.4, 64.0, -4.0);
      c.key(3.9, -15.0, 11.0, 10.0, 0.0, 0.0, 0.0, 84.0, 0.0, Ease.OUT_EXPO);
      // inside: high corner reveal, sweeping across the station
      c.cut(3.95, 10.0, -9.0, 0.8, 0.0, 0.0, -0.6, 72.0, 0.0);
      c.key(5.5, 10.0, 9.0, 4.6, 0.0, 0.0, -0.6, 72.0, 3.0, Ease.IN_OUT);
      // the title: close and low on him
      c.cut(5.55, 2.6, 0.0, -0.45, 0.0, 0.0, 0.1, 30.0, 0.0);
      c.key(6.9, 2.05, 0.0, -0.5, 0.0, 0.0, 0.1, 25.0, 0.0, Ease.LINEAR);
      // the machine
      c.cut(6.95, -1.5, 0.0, 0.6, 9.0, 0.0, 13.0, 66.0, 0.0);
      c.key(8.1, -0.8, 0.0, 0.3, 9.0, 0.0, 12.5, 50.0, 0.0, Ease.IN_OUT);
      c.cut(8.15, -4.2, 0.9, 0.9, 3.0, 0.0, 0.2, 76.0, 0.0);
      c.key(9.0, -3.7, 0.7, 0.75, 3.0, 0.0, 0.2, 72.0, 0.0, Ease.OUT_CUBIC);

      c.at(0.0, () -> {
         ScreenFx.letterbox(1.0F);
         ScreenFx.vignette(0.85F);
         ScreenFx.speed(1100L, 0.35F);
         com.hakari.idg.client.post.Post.desaturate(0.7F, 1300L);
         com.hakari.idg.client.post.Post.vignette(0x000000, 0.6F);
         Sfx.play(SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.0F);
      });
      c.at(0.55, () -> Sfx.play(SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.0F));
      c.at(1.2, () -> {
         ScreenFx.impact(1, 260L, anchorPos(caster));
         com.hakari.idg.client.post.Post.impact(com.hakari.idg.client.post.Post.DOMAIN, anchorPos(caster), com.hakari.idg.client.post.Post.PINK);
         com.hakari.idg.client.post.Post.flash(0xFF4FB8, 0.8F, 450L);
         com.hakari.idg.client.post.Post.punch(1.0F, 380L);
         CameraDirector.shake(1.4F, 500L);
         ScreenFx.card(new ScreenFx.Card("領域展開", "DOMAIN EXPANSION", "", 0, -1, 1200L, false));
         Sfx.play(SoundEvents.ANVIL_LAND, 0.5F, 0.8F);
         Sfx.play(SoundEvents.BEACON_ACTIVATE, 0.6F, 1.0F);
      });
      c.at(1.95, () -> {
         // the eruption: his cursed energy, green, before the parlour's neon takes over
         Vec3 p = anchorPos(caster);
         if (p != null) {
            Vec3 feet = p.add(0.0, -1.2, 0.0);
            Vfx.add(new Vfx.Pillar(feet, 26.0, 1.6, 0xFF39FF7A, 900L));
            Vfx.add(Vfx.Shockwave.ground(feet, 9.0, 0xFF39FF7A, 700L));
            Vfx.add(new Vfx.Flash(p, 2.6, 0xFF39FF7A, 450L));
            Vfx.add(new Vfx.Spikes(p, 44, 5.0, 0xFF39FF7A, 500L));
         }
         com.hakari.idg.client.post.Post.impact(com.hakari.idg.client.post.Post.LIGHT, p, com.hakari.idg.client.post.Post.GREEN);
         com.hakari.idg.client.post.Post.aberration(1.0F, 700L);
         com.hakari.idg.client.post.Post.bloom(0.6F);
         CameraDirector.shake(1.0F, 450L);
         Sfx.play(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.2F);
      });
      c.at(2.45, () -> {
         Sfx.play(SoundEvents.CONDUIT_ACTIVATE, 0.7F, 0.6F);
         ScreenFx.speed(1400L, 0.8F);
         CameraDirector.shake(0.8F, 1400L);
         com.hakari.idg.client.post.Post.punch(0.7F, 900L);
         com.hakari.idg.client.post.Post.aberration(0.8F, 1400L);
      });
      c.at(3.95, () -> {
         ScreenFx.flash(-1, 650L);
         com.hakari.idg.client.post.Post.impact(com.hakari.idg.client.post.Post.DOMAIN, anchorPos(caster), com.hakari.idg.client.post.Post.GOLD);
         com.hakari.idg.client.post.Post.flash(0xFFFFFF, 1.6F, 650L);
         com.hakari.idg.client.post.Post.vignette(0x3A0030, 0.35F);
         CameraDirector.shake(1.2F, 400L);
         Sfx.play(SoundEvents.BELL_BLOCK, 0.6F, 1.0F);
         Sfx.play(SoundEvents.GENERIC_EXPLODE, 0.5F, 0.7F);
      });
      c.at(4.3, () -> {
         DomainRenderer.train(caster);
         com.hakari.idg.client.post.Post.punch(0.5F, 600L);
         Sfx.play((SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 0.8F);
         Sfx.later(180L, (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 0.8F);
      });
      c.at(5.55, () -> {
         ScreenFx.split(-28.0F, 650L, 26.0F);
         com.hakari.idg.client.post.Post.impact(com.hakari.idg.client.post.Post.TITLE, anchorPos(caster), com.hakari.idg.client.post.Post.GOLD);
         com.hakari.idg.client.post.Post.flash(0xFFD34D, 0.9F, 500L);
         com.hakari.idg.client.post.Post.aberration(1.4F, 900L);
         CameraDirector.shake(2.2F, 700L);
         ScreenFx.card(new ScreenFx.Card("坐殺博徒", "IDLE DEATH GAMBLE", "CR 私鉄純愛列車  —  ver. 1/239", 1, -45128, 1450L, false));
         Sfx.play(SoundEvents.END_PORTAL_SPAWN, 0.7F, 0.9F);
         Sfx.play(SoundEvents.BEACON_ACTIVATE, 0.5F, 1.0F);
         Sfx.later(250L, SoundEvents.BEACON_ACTIVATE, 0.75F, 1.0F);
      });
      c.at(6.95, () -> {
         int a = 1 + (int)(Math.random() * 7.0);
         int b = 1 + a % 7;
         int d = 1 + (a + 2) % 7;
         ReelSet.of(caster).spin(a, b, d, 600L, 850L, 1100L, false);
         for (int i = 0; i < 12; i++) {
            Sfx.later((long)i * 85L, (SoundEvent)SoundEvents.NOTE_BLOCK_HAT.value(), 1.4F, 0.5F);
         }
         Sfx.later(600L, (SoundEvent)SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0F, 1.0F);
         Sfx.later(850L, (SoundEvent)SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0F, 1.0F);
         Sfx.later(1100L, (SoundEvent)SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0F, 1.0F);
         if (victim) {
            ScreenFx.rules(1900L);
         }
      });
      if (boosted) {
         c.at(7.6, () -> {
            ScreenFx.card(new ScreenFx.Card("確変", "PROBABILITY UP — odds doubled", "", 3, -45128, 1300L, true));
            com.hakari.idg.client.post.Post.impact(com.hakari.idg.client.post.Post.CONFIRM, null, com.hakari.idg.client.post.Post.PINK);
         });
      }
      c.at(8.15, () -> {
         ScreenFx.letterbox(0.0F);
         ScreenFx.vignette(0.25F);
         com.hakari.idg.client.post.Post.punch(0.4F, 300L);
      });
      return c;
   }

   public static Cutscene jackpot(int caster, int number, boolean pity) {
      Cutscene c = new Cutscene(caster, 5.5, false);
      c.key(0.0, 1.3, 0.3, 0.0, 0.0, 0.0, 0.0, 45.0, 0.0, Ease.LINEAR);
      c.key(0.95, 1.0, 0.2, 0.0, 0.0, 0.0, 0.0, 37.0, 0.0, Ease.IN_OUT);
      c.cut(1.0, 3.5, -2.0, -0.9, 0.0, 0.0, 0.0, 70.0, 12.0);
      c.key(1.6, 3.0, -1.6, -0.8, 0.0, 0.0, 0.0, 66.0, 15.0, Ease.LINEAR);

      for (int k = 0; k <= 8; k++) {
         double ang = Math.toRadians((double)(-90 + k * 45));
         double rad = 4.2 - (double)k * 0.15;
         double t = 1.65 + (double)k * 0.17;
         if (k == 0) {
            c.cut(t, Math.cos(ang) * rad, Math.sin(ang) * rad, 0.6, 0.0, 0.0, 0.0, 80.0, 0.0);
         } else {
            c.key(t, Math.cos(ang) * rad, Math.sin(ang) * rad, 0.6 + (double)k * 0.45, 0.0, 0.0, 0.0, 80.0, 0.0, Ease.LINEAR);
         }
      }

      c.cut(3.05, 2.2, 0.0, -1.3, 0.0, 0.0, 0.3, 50.0, -6.0);
      c.key(4.5, 1.8, 0.0, -1.2, 0.0, 0.0, 0.3, 44.0, -3.0, Ease.IN_OUT);
      c.cut(4.55, -3.5, 0.8, 1.0, 3.0, 0.0, 0.3, 74.0, 0.0);
      c.key(5.5, -3.3, 0.7, 0.9, 3.0, 0.0, 0.3, 72.0, 0.0, Ease.OUT_CUBIC);
      c.at(0.0, () -> {
         ScreenFx.letterbox(1.0F);
         ScreenFx.vignette(0.6F);
         RiichiOverlay.jackpotReels(caster, number);
      });

      for (int i = 0; i < 3; i++) {
         int k = i;
         c.at(0.25 + (double)i * 0.25, () -> {
            ScreenFx.impact(0, 90L, (Vec3)null);
            CameraDirector.shake(0.6F + (float)k * 0.3F, 250L);
            Sfx.play((SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F + (float)k * 0.25F, 1.0F);
            Sfx.play(SoundEvents.ANVIL_LAND, 1.4F, 0.4F);
         });
      }

      c.at(1.0, () -> {
         ScreenFx.split(35.0F, 750L, 34.0F);
         com.hakari.idg.client.post.Post.impact(com.hakari.idg.client.post.Post.JACKPOT_HIT, anchorPos(caster), com.hakari.idg.client.post.Post.GREEN);
         com.hakari.idg.client.post.Post.flash(0xFFFFFF, 1.5F, 500L);
         com.hakari.idg.client.post.Post.punch(1.2F, 600L);
         com.hakari.idg.client.post.Post.aberration(1.6F, 1000L);
         com.hakari.idg.client.post.Post.bloom(0.7F);
         ScreenFx.flash(-8054, 500L);
         CameraDirector.shake(2.8F, 900L);
         Sfx.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 0.8F);
         Sfx.play(SoundEvents.GENERIC_EXPLODE, 0.8F, 1.0F);
      });
      c.at(1.65, () -> {
         Vec3 p = anchorPos(caster);
         if (p != null) {
            Vec3 feet = p.add(0.0, -1.2, 0.0);
            Vfx.add(new Vfx.Coins(feet, 90, 2600L));
            Vfx.add(new Vfx.Pillar(feet, 60.0, 2.4, 0xFF39FF7A, 2400L));
            Vfx.add(new Vfx.Pillar(feet, 60.0, 1.1, -11443, 2000L));
            Vfx.add(Vfx.Shockwave.ground(feet, 14.0, 0xFF39FF7A, 1200L));
            Vfx.add(Vfx.Shockwave.ground(feet, 9.0, -11443, 900L));
            Vfx.add(new Vfx.Flash(p, 3.5, 0xFF39FF7A, 700L));
         }

         ScreenFx.card(new ScreenFx.Card("大当たり", "JACKPOT", "", 1, -11443, 1350L, true));
         com.hakari.idg.client.post.Post.impact(com.hakari.idg.client.post.Post.CONFIRM, anchorPos(caster), com.hakari.idg.client.post.Post.GREEN);
         Sfx.play(SoundEvents.TOTEM_USE, 1.0F, 0.9F);
         Sfx.play(SoundEvents.PLAYER_LEVELUP, 0.7F, 1.0F);
         float[] arp = new float[]{0.5F, 0.63F, 0.75F, 1.0F, 1.26F, 1.5F, 2.0F};

         for (int ix = 0; ix < arp.length; ix++) {
            Sfx.later((long)ix * 70L, (SoundEvent)SoundEvents.NOTE_BLOCK_CHIME.value(), arp[ix], 0.9F);
         }
      });
      c.at(3.05, () -> {
         ScreenFx.impact(0, 140L, (Vec3)null);
         String time = pity ? "2:05" : "4:11";
         ScreenFx.card(new ScreenFx.Card(time, "INFINITE CURSED ENERGY", "AUTOMATIC REVERSE CURSED TECHNIQUE", 4, -11443, 1450L, false));
         CameraDirector.shake(1.0F, 400L);
         Sfx.play(SoundEvents.BELL_BLOCK, 1.2F, 1.0F);
      });
      c.at(
         4.0,
         () -> {
            boolean odd = number % 2 == 1;
            ScreenFx.card(
               new ScreenFx.Card(
                  odd ? "確変" : "通常",
                  odd ? number + " — odd jackpot: next domain PROBABILITY UP" : number + " — even jackpot: odds reset",
                  "",
                  3,
                  odd ? -45128 : -5592406,
                  1400L,
                  odd
               )
            );
            if (pity) {
               ScreenFx.card(new ScreenFx.Card("PITY JACKPOT", "half duration", "", 2, -29992, 1300L, false));
            }
         }
      );
      c.at(4.55, () -> {
         ScreenFx.letterbox(0.0F);
         ScreenFx.vignette(0.0F);
         com.hakari.idg.client.post.Post.bloom(0.0F);
      });
      return c;
   }

   public static Cutscene microRough(int caster) {
      Cutscene c = new Cutscene(caster, 0.7, true);
      c.key(0.0, 1.8, 1.4, 0.0, 0.3, 0.0, -0.3, 52.0, 8.0, Ease.LINEAR);
      c.key(0.7, 1.45, 1.05, -0.1, 0.3, 0.0, -0.3, 42.0, 11.0, Ease.IN_EXPO);
      c.at(0.0, () -> {
         ScreenFx.letterbox(0.6F);
         ScreenFx.speed(700L, 0.6F);
         CameraDirector.shake(0.4F, 700L);
      });
      return c;
   }

   public static Cutscene microOverwhelm(int caster) {
      Cutscene c = new Cutscene(caster, 2.6, true);
      c.key(0.0, 0.2, 3.4, 0.3, 1.5, 0.0, -0.2, 72.0, 0.0, Ease.LINEAR);
      c.key(1.3, 1.2, 3.0, 0.6, 1.5, 0.0, -0.2, 76.0, -4.0, Ease.IN_OUT);
      c.cut(1.35, 4.5, -0.5, 0.2, 0.0, 0.0, -0.2, 62.0, 6.0);
      c.key(2.6, 5.0, -0.6, 0.0, 0.0, 0.0, -0.2, 60.0, 3.0, Ease.LINEAR);
      c.at(0.0, () -> {
         ScreenFx.letterbox(0.6F);
         ScreenFx.speed(2600L, 0.9F);
      });
      c.at(1.35, () -> ScreenFx.impact(0, 80L, (Vec3)null));
      return c;
   }

   public static Cutscene microSurge(int caster) {
      Cutscene c = new Cutscene(caster, 1.1, true);
      c.key(0.0, -2.5, 0.0, -0.6, 0.0, 0.0, 1.0, 70.0, 0.0, Ease.LINEAR);
      c.key(0.6, -3.0, 0.0, 3.5, 0.0, 0.0, -1.2, 82.0, 0.0, Ease.OUT_CUBIC);
      c.key(1.1, -3.2, 0.0, 4.0, 0.0, 0.0, -1.5, 86.0, 0.0, Ease.LINEAR);
      c.at(0.0, () -> {
         ScreenFx.letterbox(0.5F);
         ScreenFx.speed(1100L, 0.7F);
      });
      return c;
   }

   public static Cutscene microRush(int caster) {
      Cutscene c = new Cutscene(caster, 0.6, true);
      c.key(0.0, -2.2, -1.2, 0.2, 2.0, 0.0, 0.0, 88.0, 0.0, Ease.LINEAR);
      c.key(0.6, -2.6, -1.0, 0.3, 2.0, 0.0, 0.0, 96.0, -5.0, Ease.LINEAR);
      c.at(0.0, () -> {
         ScreenFx.letterbox(0.5F);
         ScreenFx.speed(600L, 1.0F);
      });
      return c;
   }

   private Cutscenes() {
   }
}
