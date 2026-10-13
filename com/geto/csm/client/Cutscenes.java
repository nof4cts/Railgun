package com.geto.csm.client;

import com.geto.csm.CurseKind;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Cutscenes {
   private static Vec3 eyeOf(int id) {
      Minecraft mc = Minecraft.getInstance();
      Entity e = mc.level == null ? null : mc.level.getEntity(id);
      return e == null ? null : e.getEyePosition(1.0F);
   }

   public static Cutscene summon(int player, CurseKind k) {
      double var10000 = switch (k) {
         case WYRM -> 2.4;
         case ATROXIIA, RAEVYX, TAMAMO -> 1.7;
         case CINDERVANE -> 1.3;
         default -> 1.0;
      };

      double var11 = switch (k) {
         case WYRM, ATROXIIA, RAEVYX, CINDERVANE -> 1.5;
         case TAMAMO -> 2.4;
         case KUCHISAKE -> 0.9;
         default -> 0.2;
      };
      double at = k == CurseKind.WYRM ? 5.0 : 3.5;
      Cutscene c = new Cutscene(player, 2.6, false);
      c.key(0.0, -1.6, -0.7, 0.25, at, 0.0, 0.0, 72.0, 0.0, Ease.LINEAR);
      c.key(0.85, -1.2, -0.6, 0.2, at, 0.0, var11 * 0.4, 66.0, -3.0, Ease.IN_OUT);
      c.cut(0.9, at + 3.2 * var10000, 1.6 * var10000, -1.3, at, 0.0, var11, 52.0, 8.0);
      c.key(2.6, at + 2.6 * var10000, 1.1 * var10000, -1.15, at, 0.0, var11 * 1.05, 44.0, 4.0, Ease.OUT_CUBIC);
      c.at(0.0, () -> {
         ScreenFx.letterbox(1.0F);
         ScreenFx.vignette(0.5F);
         Sfx.play(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6F, 0.5F);
      });
      c.at(0.35, () -> {
         ScreenFx.impact(0, 110L, (Vec3)null);
         CameraDirector.shake(1.0F, 400L);
      });
      c.at(0.9, () -> {
         ScreenFx.impact(1, 160L, (Vec3)null);
         CameraDirector.shake(1.6F, 500L);
         ScreenFx.card(new ScreenFx.Card(k.jp, k.title.toUpperCase(), k.grade, 1, k.color, 1500L, false));
         Sfx.play(SoundEvents.RAVAGER_ROAR, 0.7F, 1.0F);
      });
      c.at(2.2, () -> {
         ScreenFx.letterbox(0.0F);
         ScreenFx.vignette(0.0F);
      });
      return c;
   }

   public static Cutscene uzumaki(int player, int n, boolean extracted) {
      Cutscene c = new Cutscene(player, 6.0, false);
      c.key(0.0, 1.2, 0.5, -0.15, 0.0, 0.0, -0.2, 46.0, 0.0, Ease.LINEAR);
      c.key(0.85, 1.0, 0.42, -0.18, 0.0, 0.0, -0.2, 40.0, -3.0, Ease.IN_OUT);
      c.cut(0.9, 3.5, -4.2, 1.0, 3.5, 0.0, 0.0, 76.0, 0.0);
      c.key(2.2, 3.5, 4.2, 1.7, 3.5, 0.0, 0.0, 72.0, 4.0, Ease.IN_OUT);
      c.cut(2.25, 1.4, 0.65, 0.12, 3.5, 0.0, 0.0, 40.0, -6.0);
      c.key(3.4, 2.25, 0.32, 0.02, 3.5, 0.0, 0.0, 30.0, -2.0, Ease.IN_EXPO);
      c.cut(3.45, -2.5, 0.65, -0.4, 6.0, 0.0, 0.0, 60.0, 3.0);
      c.key(4.25, -2.2, 0.55, -0.35, 6.0, 0.0, 0.0, 56.0, 3.0, Ease.LINEAR);
      c.cut(4.3, -3.0, 1.2, 0.5, 20.0, 0.0, 0.0, 86.0, 0.0);
      c.key(4.85, -3.6, 1.4, 0.7, 20.0, 0.0, 0.0, 92.0, 0.0, Ease.OUT_EXPO);
      c.cut(4.9, 10.0, 9.0, 2.0, 16.0, 0.0, 0.0, 70.0, 0.0);
      c.key(6.0, 14.0, 8.0, 2.6, 22.0, 0.0, 0.0, 66.0, 0.0, Ease.LINEAR);
      c.at(0.0, () -> {
         ScreenFx.letterbox(1.0F);
         ScreenFx.vignette(0.8F);
         ScreenFx.card(new ScreenFx.Card("極ノ番", "MAXIMUM TECHNIQUE", "", 0, -6595329, 1000L, false));
         ScreenFx.impact(0, 120L, (Vec3)null);
         Sfx.play(SoundEvents.WARDEN_HEARTBEAT, 0.7F, 1.0F);
      });
      c.at(0.9, () -> {
         CameraDirector.shake(0.8F, 1300L);
         ScreenFx.speed(1300L, 0.5F);
         Sfx.play(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F, 1.0F);
      });
      c.at(2.25, () -> {
         ScreenFx.speed(1200L, 1.0F);
         CameraDirector.shake(1.2F, 1200L);

         for (int i = 0; i < 10; i++) {
            Sfx.later((long)i * 110L, (SoundEvent)SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F + (float)i * 0.08F, 0.8F);
         }
      });
      c.at(3.45, () -> {
         ScreenFx.split(-32.0F, 700L, 28.0F);
         ScreenFx.impact(1, 300L, eyeOf(player));
         CameraDirector.shake(2.4F, 700L);
         ScreenFx.card(new ScreenFx.Card("うずまき", "MAXIMUM: UZUMAKI", n + (n == 1 ? " curse" : " curses") + " combined", 1, -4616961, 1100L, false));
         Sfx.play(SoundEvents.BELL_BLOCK, 0.5F, 1.0F);
         Sfx.play(SoundEvents.GENERIC_EXPLODE, 0.6F, 0.8F);
      });
      c.at(4.3, () -> {
         ScreenFx.speed(800L, 1.0F);
         ScreenFx.split(28.0F, 450L, 20.0F);
      });
      if (extracted) {
         c.at(5.0, () -> ScreenFx.card(new ScreenFx.Card("術式抽出", "CURSED TECHNIQUE EXTRACTED", "", 3, -45096, 1500L, true)));
      }

      c.at(5.6, () -> {
         ScreenFx.letterbox(0.0F);
         ScreenFx.vignette(0.0F);
      });
      return c;
   }

   private Cutscenes() {
   }
}
