package com.cataclysm.spells.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Cutscenes for the cataclysm spells. Each rig is anchored on the target point, facing away
 * from the caster, so (forward, right, up) are relative to the blast.
 */
public final class SpellCutscenes {

    private static Vec3 eye(int id) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        return e == null ? null : e.getEyePosition(1f);
    }

    private static float yawFrom(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        if (d.horizontalDistanceSqr() < 1.0e-4) return 0;
        return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
    }

    /** World offset → (forward, right, up) in the rig's frame. */
    private static Vec3 local(Vec3 w, float yawDeg) {
        float y = yawDeg * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
        Vec3 r = new Vec3(-Mth.cos(y), 0, -Mth.sin(y));
        return new Vec3(w.dot(f), w.dot(r), w.y);
    }

    // ═══════════════ FALLING STAR (7.0 s) ═══════════════

    public static Cutscene star(int player, Vec3 target) {
        Vec3 e = eye(player);
        if (e == null) e = target.add(0, 2, -10);
        float yaw = yawFrom(e, target);
        Vec3 P = local(e.subtract(target), yaw);
        Vec3 sky2 = local(SpellFx.starPos(target, 2000).subtract(target), yaw);
        Vec3 sky3 = local(SpellFx.starPos(target, 3000).subtract(target), yaw);
        Vec3 sA = local(SpellFx.starPos(target, 1500).subtract(target), yaw);
        Vec3 sB = local(SpellFx.starPos(target, 2300).subtract(target), yaw);
        Cutscene c = new Cutscene(player, 7.0, false).fixed(target, yaw);
        c.key(0.00, P.x - 2.2, P.y - 0.9, P.z + 0.35, 0, 0, 2, 72, 0, Ease.LINEAR);
        c.key(0.65, P.x - 1.8, P.y - 0.8, P.z + 0.3, 0, 0, 6, 66, -2, Ease.IN_OUT);
        c.cut(0.70, 6, -5, 1.5, sky2.x, sky2.y, sky2.z, 82, 4);
        c.key(1.45, 5.7, -4.8, 1.45, sky2.x, sky2.y, sky2.z, 74, 3, Ease.IN_OUT);
        // chase cam riding alongside the star as it burns through the atmosphere
        c.cut(1.50, sA.x + 9, sA.y - 7, sA.z + 4, sA.x, sA.y, sA.z, 64, -10);
        c.key(2.00, sB.x + 9, sB.y - 7, sB.z + 4, sB.x, sB.y, sB.z, 70, -14, Ease.LINEAR);
        c.cut(2.05, -34, 26, 22, 0, 0, 6, 62, 0);
        c.key(3.55, -32, 24, 20, 0, 0, 8, 60, 0, Ease.LINEAR);
        c.cut(3.60, -9, 3.5, 1.2, 0, 0, 5, 88, -6);
        c.key(4.10, -8.5, 3.3, 1.1, 0, 0, 4, 92, -8, Ease.LINEAR);
        c.cut(4.15, -46, -30, 14, 0, 0, 10, 66, 0);
        c.key(5.45, -50, -33, 16, 0, 0, 14, 63, 0, Ease.OUT_CUBIC);
        // ground level as the dust wall rolls over the camera
        c.cut(5.50, -30, 8, 0.9, 0, 0, 9, 84, 3);
        c.key(7.00, -26, 7, 1.0, 0, 0, 15, 80, 1, Ease.LINEAR);

        c.at(0.0, () -> {
            ScreenFx.letterbox(1);
            ScreenFx.vignette(0.7f);
            ScreenFx.impact(ScreenFx.INVERT, 110, null);
            ScreenFx.card(new ScreenFx.Card("流星", "FALLING STAR", "", ScreenFx.Card.SLAM, 0xFFFFC870, 1100, false));
            Sfx.play(SoundEvents.BEACON_ACTIVATE, 0.5f, 1f);
        });
        c.at(0.70, () -> {
            ScreenFx.speed(1300, 0.4f);
            CameraDirector.shake(0.5f, 1300);
            Sfx.play(SoundEvents.ELYTRA_FLYING, 0.6f, 1f);
        });
        c.at(1.50, () -> {
            ScreenFx.speed(550, 1f);
            ScreenFx.heat(1f, 600);
            CameraDirector.shake(1.2f, 550);
        });
        c.at(2.05, () -> ScreenFx.card(new ScreenFx.Card("着弾まで 2秒", "IMPACT IN 2 SECONDS", "", ScreenFx.Card.CORNER, 0xFFFFC870, 1500, false)));
        c.at(3.60, () -> {
            ScreenFx.speed(500, 1f);
            CameraDirector.shake(1.6f, 500);
        });
        c.at(6.6, () -> {
            ScreenFx.letterbox(0);
            ScreenFx.vignette(0);
        });
        return c;
    }

    // ═══════════════ EVENT HORIZON (6.6 s) ═══════════════

    public static Cutscene hole(int player, Vec3 center) {
        Vec3 e = eye(player);
        if (e == null) e = center.add(0, -2, -10);
        float yaw = yawFrom(e, center);
        Vec3 P = local(e.subtract(center), yaw);
        Cutscene c = new Cutscene(player, 6.6, false).fixed(center, yaw);
        c.key(0.00, P.x - 1.6, P.y - 0.8, P.z + 0.3, 0, 0, 0, 70, 0, Ease.LINEAR);
        c.key(0.75, P.x - 1.2, P.y - 0.7, P.z + 0.25, 0, 0, 0, 62, 0, Ease.IN_OUT);
        c.cut(0.80, -7, -6, 1.5, 0, 0, 0, 60, -4);
        c.key(1.60, -7, 0, 2.2, 0, 0, 0, 58, 0, Ease.IN_OUT);
        // looking straight up into it from the ground as terrain is torn upward
        c.cut(1.65, -4.5, -2.0, -3.1, 0, 0, 0.5, 78, 10);
        c.key(2.40, -4.0, 2.0, -3.0, 0, 0, 0.5, 74, -10, Ease.IN_OUT);
        c.cut(2.45, -3.4, 0.7, 0.4, 0, 0, 0, 40, 0);
        c.key(4.00, -2.5, 0.4, 0.3, 0, 0, 0, 33, -3, Ease.IN_EXPO);
        c.cut(4.05, -24, 11, 6, 0, 0, 0, 72, 0);
        c.key(6.60, -28, 13, 8, 0, 0, 0, 68, 0, Ease.OUT_CUBIC);

        c.at(0.0, () -> {
            ScreenFx.letterbox(1);
            ScreenFx.vignette(0.8f);
            ScreenFx.impact(ScreenFx.INVERT, 110, null);
            ScreenFx.card(new ScreenFx.Card("事象の地平線", "EVENT HORIZON", "", ScreenFx.Card.SLAM, 0xFFFFC070, 1100, false));
            Sfx.play(SoundEvents.WARDEN_SONIC_CHARGE, 0.4f, 1f);
        });
        c.at(0.80, () -> {
            CameraDirector.shake(1.0f, 1600);
            ScreenFx.speed(1600, 0.5f);
        });
        c.at(1.65, () -> ScreenFx.post(center, 0.9f, 800));
        c.at(2.45, () -> {
            ScreenFx.speed(1600, 1f);
            CameraDirector.shake(1.4f, 1600);
            for (int i = 0; i < 12; i++) Sfx.later(i * 120L, SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f + i * 0.04f, 0.8f);
        });
        c.at(6.2, () -> {
            ScreenFx.letterbox(0);
            ScreenFx.vignette(0);
        });
        return c;
    }

    // ═══════════════ SKYFALL LANCE (5.5 s) ═══════════════

    public static Cutscene lance(int player, Vec3 start, float yaw) {
        Cutscene c = new Cutscene(player, 5.5, false).fixed(start, yaw);
        c.key(0.00, -7.5, -1.2, 2.4, 0, 0, 1, 70, 0, Ease.LINEAR);
        c.key(0.55, -7.0, -1.1, 2.3, 4, 0, 1, 66, 0, Ease.IN_OUT);
        c.cut(0.60, 18, -6, 58, 18, 0, 0, 62, 0);
        c.key(1.95, 18, -5, 50, 18, 0, 0, 60, 0, Ease.IN_OUT);
        c.cut(2.00, 8, -15, 2.5, 12, 0, 4, 76, 3);
        c.key(2.60, 14, -15, 2.7, 18, 0, 4, 76, 3, Ease.LINEAR);
        // high above, looking down the length of the beam as it carves the ground
        c.cut(2.65, 12, 3, 38, 14, 0, 0, 70, 0);
        c.key(3.30, 28, 3, 34, 30, 0, 0, 70, 0, Ease.LINEAR);
        c.cut(3.35, 24, -14, 2.8, 30, 0, 3, 78, 4);
        c.key(3.60, 27, -14, 3.0, 32, 0, 3, 78, 4, Ease.LINEAR);
        c.cut(3.65, -6, 0, 7, 30, 0, 0, 70, 0);
        c.key(5.50, -8, 0, 8, 30, 0, 0, 68, 0, Ease.OUT_CUBIC);

        c.at(0.0, () -> {
            ScreenFx.letterbox(1);
            ScreenFx.vignette(0.6f);
            ScreenFx.impact(ScreenFx.INVERT, 110, null);
            ScreenFx.card(new ScreenFx.Card("天墜槍", "SKYFALL LANCE", "", ScreenFx.Card.SLAM, 0xFFFFD86A, 1000, false));
            Sfx.play(SoundEvents.BELL_RESONATE, 0.7f, 1f);
        });
        c.at(0.60, () -> {
            CameraDirector.shake(0.6f, 1300);
            for (int i = 0; i < 6; i++) Sfx.later(i * 200L, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f + i * 0.15f, 0.8f);
        });
        c.at(5.1, () -> {
            ScreenFx.letterbox(0);
            ScreenFx.vignette(0);
        });
        return c;
    }

    private SpellCutscenes() {}
}
