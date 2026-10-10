package com.geto.csm.client;

import com.geto.csm.CurseKind;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Summon reveals and Maximum: Uzumaki, shot by shot. Coordinates: (forward, right, up) from the player's eyes. */
public final class Cutscenes {

    private static Vec3 eyeOf(int id) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        return e == null ? null : e.getEyePosition(1f);
    }

    // ═══════════════ SUMMON REVEAL (2.6 s) ═══════════════

    public static Cutscene summon(int player, CurseKind k) {
        double size = switch (k) {
            case WYRM, TAMAMO -> 1.7;
            case WORM, NAMAZU -> 1.4;
            default -> 1.0;
        };
        double lookUp = switch (k) {
            case WYRM -> 1.5;
            case TAMAMO -> 2.4;
            case WORM -> 3.5;
            case KUCHISAKE -> 0.9;
            default -> 0.2;
        };
        double at = k == CurseKind.WORM ? 6 : 3.5;
        Cutscene c = new Cutscene(player, 2.6, false);
        c.key(0.0, -1.6, -0.7, 0.25, at, 0, 0, 72, 0, Ease.LINEAR);
        c.key(0.85, -1.2, -0.6, 0.2, at, 0, lookUp * 0.4, 66, -3, Ease.IN_OUT);
        c.cut(0.9, at + 3.2 * size, 1.6 * size, -1.3, at, 0, lookUp, 52, 8);
        c.key(2.6, at + 2.6 * size, 1.1 * size, -1.15, at, 0, lookUp * 1.05, 44, 4, Ease.OUT_CUBIC);

        c.at(0.0, () -> {
            ScreenFx.letterbox(1);
            ScreenFx.vignette(0.5f);
            Sfx.play(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.5f);
        });
        c.at(0.35, () -> {
            ScreenFx.impact(ScreenFx.INVERT, 110, null);
            CameraDirector.shake(1.0f, 400);
        });
        c.at(0.9, () -> {
            ScreenFx.impact(ScreenFx.INK, 160, null);
            CameraDirector.shake(1.6f, 500);
            ScreenFx.card(new ScreenFx.Card(k.jp, k.title.toUpperCase(), k.grade, ScreenFx.Card.TITLE, k.color, 1500, false));
            Sfx.play(SoundEvents.RAVAGER_ROAR, 0.7f, 1f);
        });
        c.at(2.2, () -> {
            ScreenFx.letterbox(0);
            ScreenFx.vignette(0);
        });
        return c;
    }

    // ═══════════════ MAXIMUM: UZUMAKI (6.0 s) ═══════════════

    public static Cutscene uzumaki(int player, int n, boolean extracted) {
        Cutscene c = new Cutscene(player, 6.0, false);
        // close on the raised hand
        c.key(0.00, 1.2, 0.5, -0.15, 0, 0, -0.2, 46, 0, Ease.LINEAR);
        c.key(0.85, 1.0, 0.42, -0.18, 0, 0, -0.2, 40, -3, Ease.IN_OUT);
        // wide orbit while the curses stream in
        c.cut(0.90, 3.5, -4.2, 1.0, 3.5, 0, 0, 76, 0);
        c.key(2.20, 3.5, 4.2, 1.7, 3.5, 0, 0, 72, 4, Ease.IN_OUT);
        // macro on the condensing core
        c.cut(2.25, 1.4, 0.65, 0.12, 3.5, 0, 0, 40, -6);
        c.key(3.40, 2.25, 0.32, 0.02, 3.5, 0, 0, 30, -2, Ease.IN_EXPO);
        // title — low behind the player
        c.cut(3.45, -2.5, 0.65, -0.4, 6, 0, 0, 60, 3);
        c.key(4.25, -2.2, 0.55, -0.35, 6, 0, 0, 56, 3, Ease.LINEAR);
        // release
        c.cut(4.30, -3.0, 1.2, 0.5, 20, 0, 0, 86, 0);
        c.key(4.85, -3.6, 1.4, 0.7, 20, 0, 0, 92, 0, Ease.OUT_EXPO);
        // side view down the length of the beam
        c.cut(4.90, 10, 9, 2, 16, 0, 0, 70, 0);
        c.key(6.00, 14, 8, 2.6, 22, 0, 0, 66, 0, Ease.LINEAR);

        c.at(0.00, () -> {
            ScreenFx.letterbox(1);
            ScreenFx.vignette(0.8f);
            ScreenFx.card(new ScreenFx.Card("極ノ番", "MAXIMUM TECHNIQUE", "", ScreenFx.Card.SLAM, 0xFF9B5CFF, 1000, false));
            ScreenFx.impact(ScreenFx.INVERT, 120, null);
            Sfx.play(SoundEvents.WARDEN_HEARTBEAT, 0.7f, 1f);
        });
        c.at(0.90, () -> {
            CameraDirector.shake(0.8f, 1300);
            ScreenFx.speed(1300, 0.5f);
            Sfx.play(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 1f);
        });
        c.at(2.25, () -> {
            ScreenFx.speed(1200, 1f);
            CameraDirector.shake(1.2f, 1200);
            for (int i = 0; i < 10; i++) Sfx.later(i * 110L, SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f + i * 0.08f, 0.8f);
        });
        c.at(3.45, () -> {
            ScreenFx.split(-32, 700, 28);
            ScreenFx.impact(ScreenFx.INK, 300, eyeOf(player));
            CameraDirector.shake(2.4f, 700);
            ScreenFx.card(new ScreenFx.Card("うずまき", "MAXIMUM: UZUMAKI", n + (n == 1 ? " curse" : " curses") + " combined", ScreenFx.Card.TITLE, 0xFFB98CFF, 1100, false));
            Sfx.play(SoundEvents.BELL_BLOCK, 0.5f, 1f);
            Sfx.play(SoundEvents.GENERIC_EXPLODE, 0.6f, 0.8f);
        });
        c.at(4.30, () -> {
            ScreenFx.speed(800, 1f);
            ScreenFx.split(28, 450, 20);
        });
        if (extracted) {
            c.at(5.0, () -> ScreenFx.card(new ScreenFx.Card("術式抽出", "CURSED TECHNIQUE EXTRACTED", "", ScreenFx.Card.CORNER, 0xFFFF4FD8, 1500, true)));
        }
        c.at(5.6, () -> {
            ScreenFx.letterbox(0);
            ScreenFx.vignette(0);
        });
        return c;
    }

    private Cutscenes() {}
}
