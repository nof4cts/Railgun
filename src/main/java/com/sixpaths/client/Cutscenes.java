package com.sixpaths.client;

import com.sixpaths.Ability;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Camera choreography for the Six Paths. Rig coordinates are (forward, right, up) relative to
 * the anchor; timings are matched to the server's tick schedule (1 tick = 50 ms).
 */
public final class Cutscenes {

    static Entity entity(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : mc.level.getEntity(id);
    }

    static float yawFrom(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        if (d.horizontalDistanceSqr() < 1.0e-4) return 0;
        return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
    }

    static Vec3 local(Vec3 w, float yawDeg) {
        float y = yawDeg * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
        Vec3 r = new Vec3(-Mth.cos(y), 0, -Mth.sin(y));
        return new Vec3(w.dot(f), w.dot(r), w.y);
    }

    static ScreenFx.Card title(Ability a, long life) {
        return new ScreenFx.Card(a.jp, a.title.toUpperCase(), a.path, ScreenFx.Card.SLAM, a.color, life, false);
    }

    static void open() {
        ScreenFx.letterbox(1);
        ScreenFx.vignette(0.6f);
    }

    // ═══════════════ PLANETARY DEVASTATION (7.5 s) ═══════════════

    public static Cutscene chibaku(int player, Vec3 core) {
        Entity p = entity(player);
        Vec3 eye = p == null ? core.add(0, -12, -14) : p.getEyePosition(1f);
        float yaw = yawFrom(eye, core);
        Vec3 P = local(eye.subtract(core), yaw);
        Cutscene c = new Cutscene(player, 7.5, false).fixed(core, yaw);
        // low behind the caster as the core leaves the hand
        c.key(0.00, P.x - 2.6, P.y + 1.4, P.z - 0.9, 0, 0, 0, 62, 2, Ease.LINEAR);
        c.key(1.00, P.x - 3.2, P.y + 1.8, P.z - 1.2, 0, 0, 0, 70, 4, Ease.IN_OUT);
        // ground level, far out, looking up at the core as the land starts to lift
        c.cut(1.05, 4, 30, -13, 0, 0, -3, 66, -3);
        c.key(2.60, 2, 26, -12.5, 0, 0, -1, 62, -1, Ease.LINEAR);
        // orbit tight around the gravity core through the debris storm
        c.cut(2.65, 9, 8, 2, 0, 0, 0, 84, 8);
        c.key(4.20, -5, 10, 4, 0, 0, 0, 78, -6, Ease.IN_OUT);
        // high wide: the whole valley rising into the sky
        c.cut(4.25, -26, -10, 24, 0, 0, -6, 64, 0);
        c.key(6.20, -30, -14, 28, 0, 0, -4, 60, 0, Ease.OUT_CUBIC);
        // the seal: punch in on the finished moon
        c.cut(6.25, -17, 8, 2, 0, 0, 0, 92, -4);
        c.key(7.50, -21, 10, 3, 0, 0, 0, 68, 0, Ease.OUT_EXPO);

        c.at(0.0, () -> {
            open();
            ScreenFx.impact(ScreenFx.INK, 120, core);
            ScreenFx.card(title(Ability.PLANETARY_DEVASTATION, 1500));
        });
        c.at(1.05, () -> {
            CameraDirector.shake(0.6f, 1500);
            ScreenFx.speed(900, 0.4f);
        });
        c.at(2.65, () -> {
            ScreenFx.speed(1500, 0.8f);
            CameraDirector.shake(0.9f, 1500);
            Sfx.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.5f, 0.7f);
        });
        c.at(4.25, () -> CameraDirector.shake(1.1f, 1900));
        c.at(6.05, () -> Sfx.play(SoundEvents.WARDEN_HEARTBEAT, 0.5f, 1f));
        return c;
    }

    // ═══════════════ HEAVENLY DESCENT (8 s; blast at 3.6 s) ═══════════════

    public static Cutscene descent(int player, Vec3 origin) {
        Entity p = entity(player);
        float yaw = p == null ? 0 : p.getYRot();
        Cutscene c = new Cutscene(player, 8.0, false).fixed(origin, yaw);
        // worm's-eye: the caster lifting off
        c.key(0.00, 5, 3, 0.4, 0, 0, 4, 70, 3, Ease.LINEAR);
        c.key(1.40, 5.5, 3.5, 0.5, 0, 0, 12, 74, 5, Ease.IN_OUT);
        // god's-eye: tiny figure above the land
        c.cut(1.45, -4, 2, 36, 0, 0, 0, 66, 0);
        c.key(3.00, -3, 1.5, 32, 0, 0, 0, 62, 0, Ease.IN_OUT);
        // close on the caster, a breath before
        c.cut(3.05, 3.4, 0.6, 20.8, 0, 0, 21.6, 52, -4);
        c.key(3.55, 3.0, 0.5, 20.9, 0, 0, 21.6, 46, -6, Ease.IN_EXPO);
        // the blast from far away
        c.cut(3.60, -48, 26, 6, 0, 0, 4, 68, 0);
        c.key(5.20, -52, 29, 8, 0, 0, 6, 64, 0, Ease.OUT_CUBIC);
        // crater reveal from the air
        c.cut(5.25, -18, -10, 36, 0, 0, -2, 70, 0);
        c.key(8.00, -26, -15, 44, 0, 0, -4, 64, 0, Ease.OUT_CUBIC);

        c.at(0.0, () -> {
            open();
            ScreenFx.grade(0xFFE8E8FF, 0.25f, 600, 2600, 800);
        });
        c.at(1.45, () -> ScreenFx.speed(1500, 0.3f));
        c.at(3.05, () -> {
            ScreenFx.card(title(Ability.HEAVENLY_DESCENT, 1200));
            ScreenFx.frames(origin.add(0, 21, 0), 0xFFFFFFFF, 0, new int[]{ScreenFx.S_INK}, new long[]{70});
            Sfx.play(SoundEvents.BELL_RESONATE, 0.6f, 1f);
        });
        c.at(5.25, () -> CameraDirector.shake(0.6f, 1500));
        return c;
    }

    // ═══════════════ SAMSARA (11 s; restoration at 8.5 s) ═══════════════

    public static Cutscene samsara(int player, Vec3 at) {
        Entity p = entity(player);
        float yaw = p == null ? 0 : p.getYRot();
        Cutscene c = new Cutscene(player, 11.0, false).fixed(at, yaw);
        c.key(0.00, -3.2, 1.4, 1.1, 4, 0, 2.0, 66, 0, Ease.LINEAR);
        c.key(2.50, -2.6, 1.7, 1.3, 4, 0, 2.4, 62, 1, Ease.IN_OUT);
        c.cut(2.55, 15, 3, 3, -4, 0, 7, 72, 0);
        c.key(5.50, 17, 4, 2, -5, 0, 10, 66, -2, Ease.IN_OUT);
        c.cut(5.55, 10, -10, 8, 0, 0, 18, 76, 4);
        c.key(8.45, 6, -6, 30, 0, 0, 22, 70, 0, Ease.IN_OUT);
        c.cut(8.50, -30, 22, 40, 0, 0, 0, 72, 0);
        c.key(11.00, -36, 26, 48, 0, 0, 0, 66, 0, Ease.OUT_CUBIC);
        c.at(0.0, () -> {
            open();
            ScreenFx.card(title(Ability.SAMSARA, 2400));
            Sfx.play(SoundEvents.BEACON_AMBIENT, 0.5f, 1f);
        });
        c.at(2.55, () -> CameraDirector.shake(0.7f, 2500));
        c.at(8.50, () -> ScreenFx.speed(800, 0.3f));
        return c;
    }

    // ═══════════════ KING OF HELL (4.5 s) ═══════════════

    public static Cutscene king(int player, Vec3 king, float casterYaw) {
        Cutscene c = new Cutscene(player, 4.5, false);
        c.key(0.00, 1.5, 4.2, -1.1, 3.2, 0, -1.2, 70, 3, Ease.LINEAR);
        c.key(1.20, 1.7, 4.0, -0.9, 3.2, 0, 0.5, 74, 2, Ease.IN_OUT);
        c.cut(1.25, 0.6, 2.6, 0.4, 3.2, 0, 1.2, 86, -5);
        c.key(2.40, 0.9, 2.2, 0.6, 3.2, 0, 1.6, 80, -3, Ease.IN_OUT);
        c.cut(2.45, 6.2, -1.8, 2.6, 0, 0, -0.4, 68, 0);
        c.key(4.50, 6.6, -2.2, 3.0, 0, 0, -0.2, 64, 0, Ease.OUT_CUBIC);
        c.at(0.0, () -> {
            open();
            ScreenFx.card(title(Ability.KING_OF_HELL, 1300));
        });
        c.at(1.25, () -> {
            CameraDirector.shake(0.9f, 700);
            ScreenFx.frames(king.add(0, 2.4, 0), 0xFFFF5A3A, 0, new int[]{ScreenFx.S_INK, ScreenFx.S_DUO}, new long[]{60, 70});
        });
        return c;
    }

    // ═══════════════ ASURA CANNON (2 s; fires at 0.9 s) ═══════════════

    public static Cutscene cannon(int player, Vec3 end) {
        Cutscene c = new Cutscene(player, 2.0, false);
        c.key(0.00, 1.6, -3.6, 0.1, 1.6, 0, -0.2, 58, 0, Ease.LINEAR);
        c.key(0.85, 1.9, -3.2, 0.0, 1.6, 0, -0.2, 52, -2, Ease.IN_EXPO);
        c.cut(0.90, -2.4, -0.95, 0.35, 12, 0, 0, 92, 0);
        c.key(2.00, -2.9, -1.1, 0.45, 12, 0, 0, 76, 0, Ease.OUT_EXPO);
        c.at(0.0, () -> {
            open();
            ScreenFx.card(new ScreenFx.Card(Ability.ASURA_CANNON.jp, "ASURA CANNON", "", ScreenFx.Card.CORNER, Ability.ASURA_CANNON.color, 1500, false));
        });
        return c;
    }

    // ═══════════════ SOUL EXTRACTION (2.9 s; tears at 2.2 s) ═══════════════

    public static Cutscene soul(int player, int target) {
        Cutscene c = new Cutscene(player, 2.9, false);
        c.key(0.00, 0.8, -3.2, 0.0, 0.9, 0, -0.2, 62, 0, Ease.LINEAR);
        c.key(1.15, 0.9, -2.8, 0.0, 0.9, 0, -0.2, 58, 0, Ease.IN_OUT);
        c.cut(1.20, 2.8, 0.7, 0.05, 0, 0, -0.05, 50, 3);
        c.key(2.15, 2.6, 0.6, 0.05, 0, 0, -0.05, 44, 5, Ease.IN_EXPO);
        c.cut(2.20, -2.3, 1.1, 0.35, 2.2, 0, -0.1, 82, 0);
        c.key(2.90, -2.6, 1.3, 0.45, 2.2, 0, -0.1, 74, 0, Ease.OUT_CUBIC);
        c.at(0.0, () -> {
            open();
            ScreenFx.card(new ScreenFx.Card(Ability.SOUL_EXTRACTION.jp, "SOUL EXTRACTION", "HUMAN PATH", ScreenFx.Card.CORNER, Ability.SOUL_EXTRACTION.color, 1800, false));
            ScreenFx.grade(0xFF9FD8E8, 0.35f, 300, 1800, 500);
        });
        return c;
    }

    // ═══════════════ ASCENSION (2 s) ═══════════════

    public static Cutscene ascend(int player) {
        Cutscene c = new Cutscene(player, 2.0, false);
        c.key(0.00, 3.0, 0.0, -0.3, 0, 0, -0.1, 66, 0, Ease.LINEAR);
        c.key(1.00, 0.4, 3.0, 0.3, 0, 0, 0.0, 70, -4, Ease.IN_OUT);
        c.key(2.00, -3.2, 0.4, 1.4, 0, 0, 0.2, 74, 0, Ease.IN_OUT);
        c.at(0.0, () -> {
            open();
            ScreenFx.card(title(Ability.ASCENSION, 1500));
        });
        return c;
    }

    // ═══════════════ SUMMON (1.3 s) ═══════════════

    public static Cutscene summon(int player, Vec3 spot, int kind) {
        Entity p = entity(player);
        float yaw = p == null ? 0 : p.getYRot();
        Ability a = Ability.values()[Ability.SUMMON_RHINO.ordinal() + Mth.clamp(kind, 0, 3)];
        double sz = kind == 3 ? 1.6 : 1.0;
        Cutscene c = new Cutscene(player, 1.3, false).fixed(spot, yaw);
        c.key(0.00, 5.5 * sz, 3 * sz, 0.5, 0, 0, 1.0, 72, 3, Ease.LINEAR);
        c.key(0.55, 5.0 * sz, 2.7 * sz, 0.5, 0, 0, 1.2, 76, 4, Ease.LINEAR);
        c.cut(0.60, 6.5 * sz, -2.2 * sz, 1.0, 0, 0, 1.6 * sz, 80, -3);
        c.key(1.30, 7.5 * sz, -2.8 * sz, 1.4, 0, 0, 1.8 * sz, 72, -1, Ease.OUT_EXPO);
        c.at(0.0, () -> {
            ScreenFx.letterbox(0.6f);
            ScreenFx.card(new ScreenFx.Card(a.jp, a.title.toUpperCase(), "", ScreenFx.Card.CORNER, a.color, 1300, false));
        });
        return c;
    }

    private Cutscenes() {}
}
