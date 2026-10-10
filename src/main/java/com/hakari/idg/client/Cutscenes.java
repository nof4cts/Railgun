package com.hakari.idg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Every cutscene, shot by shot.
 * Coordinates: (forward, right, up) metres from the anchor's eyes, camera then look-at.
 */
public final class Cutscenes {

    private static Vec3 anchorPos(int id) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        return e == null ? null : e.position().add(0, 1.2, 0);
    }

    // ═══════════════ DOMAIN EXPANSION: IDLE DEATH GAMBLE (9.0 s) ═══════════════

    public static Cutscene domain(int caster, boolean victim, boolean boosted) {
        Cutscene c = new Cutscene(caster, 9.0, false);

        // 0.00 — extreme close-up on the eyes; heartbeat; the world holds its breath
        c.key(0.00, 1.60, 0.45, 0.05, 0, 0, 0, 55, 0, Ease.LINEAR);
        c.key(1.15, 1.02, 0.22, 0.00, 0, 0, 0, 40, -4, Ease.IN_OUT);
        // 1.20 — CUT: macro shot of the hand sign
        c.cut(1.20, 0.75, -0.95, -0.55, 0.35, 0, -0.5, 36, 7);
        c.key(2.40, 0.95, -0.62, -0.70, 0.35, 0, -0.5, 32, 2, Ease.IN_OUT);
        // 2.45 — CUT: dutch low angle as the black barrier swallows everything
        c.cut(2.45, -2.8, 2.4, -1.4, 0, 0, 0, 62, -11);
        c.key(3.90, 6.5, 6.0, 3.2, 0, 0, -0.5, 88, 0, Ease.OUT_EXPO);
        // 3.95 — white-out → inside the domain: crane sweep across the station
        c.cut(3.95, 10, -9, 0.8, 0, 0, -0.6, 72, 0);
        c.key(5.50, 10, 9, 4.6, 0, 0, -0.6, 72, 3, Ease.IN_OUT);
        // 5.55 — CUT: title card hero shot, slow push
        c.cut(5.55, 2.6, 0, -0.45, 0, 0, 0.1, 30, 0);
        c.key(6.90, 2.05, 0, -0.5, 0, 0, 0.1, 25, 0, Ease.LINEAR);
        // 6.95 — CUT: whip up to the slot machine in the sky
        c.cut(6.95, -1.5, 0, 0.6, 9, 0, 13, 66, 0);
        c.key(8.10, -0.8, 0, 0.3, 9, 0, 12.5, 50, 0, Ease.IN_OUT);
        // 8.15 — CUT: over-the-shoulder, back to the fight
        c.cut(8.15, -4.2, 0.9, 0.9, 3, 0, 0.2, 76, 0);
        c.key(9.00, -3.7, 0.7, 0.75, 3, 0, 0.2, 72, 0, Ease.OUT_CUBIC);

        c.at(0.00, () -> {
            ScreenFx.letterbox(1);
            ScreenFx.vignette(0.85f);
            ScreenFx.speed(1100, 0.35f);
            Sfx.play(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1f);
        });
        c.at(0.55, () -> Sfx.play(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1f));
        c.at(1.20, () -> {
            ScreenFx.impact(ScreenFx.INVERT, 260, anchorPos(caster));
            CameraDirector.shake(1.4f, 500);
            ScreenFx.card(new ScreenFx.Card("領域展開", "DOMAIN EXPANSION", "", ScreenFx.Card.SLAM, 0xFFFFFFFF, 1200, false));
            Sfx.play(SoundEvents.ANVIL_LAND, 0.5f, 0.8f);
            Sfx.play(SoundEvents.BELL_BLOCK, 0.6f, 1f);
        });
        c.at(2.45, () -> {
            Sfx.play(SoundEvents.END_PORTAL_SPAWN, 0.7f, 0.6f);
            ScreenFx.speed(1400, 0.8f);
            CameraDirector.shake(0.8f, 1400);
        });
        c.at(3.95, () -> {
            ScreenFx.flash(0xFFFFFFFF, 650);
            CameraDirector.shake(1.2f, 400);
            Sfx.play(SoundEvents.BEACON_ACTIVATE, 0.6f, 1f);
        });
        c.at(4.30, () -> {
            DomainRenderer.train(caster);
            Sfx.play(SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, 0.8f);
            Sfx.later(180, SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 0.8f);
        });
        c.at(5.55, () -> {
            ScreenFx.split(-28, 650, 26);
            ScreenFx.impact(ScreenFx.INK, 300, anchorPos(caster));
            CameraDirector.shake(2.2f, 700);
            ScreenFx.card(new ScreenFx.Card("坐殺博徒", "IDLE DEATH GAMBLE", "CR 私鉄純愛列車  —  ver. 1/239", ScreenFx.Card.TITLE, 0xFFFF4FB8, 1450, false));
            Sfx.play(SoundEvents.GENERIC_EXPLODE, 0.7f, 0.9f);
            Sfx.play(SoundEvents.BELL_BLOCK, 0.5f, 1f);
            Sfx.later(250, SoundEvents.BELL_BLOCK, 0.75f, 1f);
        });
        c.at(6.95, () -> {
            int a = 1 + (int) (Math.random() * 7), b = 1 + (a % 7), d = 1 + ((a + 2) % 7);
            ReelSet.of(caster).spin(a, b, d, 600, 850, 1100, false);
            for (int i = 0; i < 12; i++) Sfx.later(i * 85L, SoundEvents.NOTE_BLOCK_HAT.value(), 1.4f, 0.5f);
            Sfx.later(600, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1f, 1f);
            Sfx.later(850, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1f, 1f);
            Sfx.later(1100, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1f, 1f);
            if (victim) ScreenFx.rules(1900);
        });
        if (boosted) {
            c.at(7.6, () -> ScreenFx.card(new ScreenFx.Card("確変", "PROBABILITY UP — odds doubled", "", ScreenFx.Card.CORNER, 0xFFFF4FB8, 1300, true)));
        }
        c.at(8.15, () -> {
            ScreenFx.letterbox(0);
            ScreenFx.vignette(0.25f);
            ScreenFx.impact(ScreenFx.INVERT, 90, null);
        });
        return c;
    }

    // ═══════════════ JACKPOT (5.5 s) ═══════════════

    public static Cutscene jackpot(int caster, int number, boolean pity) {
        Cutscene c = new Cutscene(caster, 5.5, false);
        c.key(0.00, 1.30, 0.3, 0.0, 0, 0, 0, 45, 0, Ease.LINEAR);
        c.key(0.95, 1.00, 0.2, 0.0, 0, 0, 0, 37, 0, Ease.IN_OUT);
        c.cut(1.00, 3.5, -2.0, -0.9, 0, 0, 0, 70, 12);
        c.key(1.60, 3.0, -1.6, -0.8, 0, 0, 0, 66, 15, Ease.LINEAR);
        // spiral crane around the caster as the payout erupts
        for (int k = 0; k <= 8; k++) {
            double ang = Math.toRadians(-90 + k * 45);
            double rad = 4.2 - k * 0.15;
            double t = 1.65 + k * 0.17;
            if (k == 0) c.cut(t, Math.cos(ang) * rad, Math.sin(ang) * rad, 0.6, 0, 0, 0, 80, 0);
            else c.key(t, Math.cos(ang) * rad, Math.sin(ang) * rad, 0.6 + k * 0.45, 0, 0, 0, 80, 0, Ease.LINEAR);
        }
        c.cut(3.05, 2.2, 0, -1.3, 0, 0, 0.3, 50, -6);
        c.key(4.50, 1.8, 0, -1.2, 0, 0, 0.3, 44, -3, Ease.IN_OUT);
        c.cut(4.55, -3.5, 0.8, 1.0, 3, 0, 0.3, 74, 0);
        c.key(5.50, -3.3, 0.7, 0.9, 3, 0, 0.3, 72, 0, Ease.OUT_CUBIC);

        c.at(0.00, () -> {
            ScreenFx.letterbox(1);
            ScreenFx.vignette(0.6f);
            RiichiOverlay.jackpotReels(caster, number);
        });
        for (int i = 0; i < 3; i++) {
            final int k = i;
            c.at(0.25 + i * 0.25, () -> {
                ScreenFx.impact(ScreenFx.INVERT, 90, null);
                CameraDirector.shake(0.6f + k * 0.3f, 250);
                Sfx.play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f + k * 0.25f, 1f);
                Sfx.play(SoundEvents.ANVIL_LAND, 1.4f, 0.4f);
            });
        }
        c.at(1.00, () -> {
            ScreenFx.split(35, 750, 34);
            ScreenFx.impact(ScreenFx.GOLD, 260, anchorPos(caster));
            ScreenFx.flash(0xFFFFE08A, 500);
            CameraDirector.shake(2.8f, 900);
            Sfx.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
            Sfx.play(SoundEvents.GENERIC_EXPLODE, 0.8f, 1f);
        });
        c.at(1.65, () -> {
            Vec3 p = anchorPos(caster);
            if (p != null) {
                Vec3 feet = p.add(0, -1.2, 0);
                Vfx.add(new Vfx.Coins(feet, 90, 2600));
                Vfx.add(new Vfx.Pillar(feet, 60, 2.2, 0xFFFFD34D, 2400));
                Vfx.add(Vfx.Shockwave.ground(feet, 14, 0xFFFFD34D, 1200));
                Vfx.add(new Vfx.Flash(p, 3.5, 0xFFFFD34D, 700));
            }
            ScreenFx.card(new ScreenFx.Card("大当たり", "JACKPOT", "", ScreenFx.Card.TITLE, 0xFFFFD34D, 1350, true));
            Sfx.play(SoundEvents.TOTEM_USE, 1f, 0.9f);
            Sfx.play(SoundEvents.PLAYER_LEVELUP, 0.7f, 1f);
            float[] arp = {0.5f, 0.63f, 0.75f, 1.0f, 1.26f, 1.5f, 2.0f};
            for (int i = 0; i < arp.length; i++) Sfx.later(i * 70L, SoundEvents.NOTE_BLOCK_CHIME.value(), arp[i], 0.9f);
        });
        c.at(3.05, () -> {
            ScreenFx.impact(ScreenFx.INVERT, 140, null);
            String time = pity ? "2:05" : "4:11";
            ScreenFx.card(new ScreenFx.Card(time, "INFINITE CURSED ENERGY", "AUTOMATIC REVERSE CURSED TECHNIQUE", ScreenFx.Card.TIMER, 0xFFFFD34D, 1450, false));
            CameraDirector.shake(1f, 400);
            Sfx.play(SoundEvents.BELL_BLOCK, 1.2f, 1f);
        });
        c.at(4.0, () -> {
            boolean odd = number % 2 == 1;
            ScreenFx.card(new ScreenFx.Card(odd ? "確変" : "通常",
                    odd ? number + " — odd jackpot: next domain PROBABILITY UP" : number + " — even jackpot: odds reset",
                    "", ScreenFx.Card.CORNER, odd ? 0xFFFF4FB8 : 0xFFAAAAAA, 1400, odd));
            if (pity) ScreenFx.card(new ScreenFx.Card("PITY JACKPOT", "half duration", "", ScreenFx.Card.POP, 0xFFFF8AD8, 1300, false));
        });
        c.at(4.55, () -> {
            ScreenFx.letterbox(0);
            ScreenFx.vignette(0);
        });
        return c;
    }

    // ═══════════════ MOVE MICRO-CUTS ═══════════════

    public static Cutscene microRough(int caster) {
        Cutscene c = new Cutscene(caster, 0.70, true);
        c.key(0.00, 1.8, 1.4, 0.0, 0.3, 0, -0.3, 52, 8, Ease.LINEAR);
        c.key(0.70, 1.45, 1.05, -0.1, 0.3, 0, -0.3, 42, 11, Ease.IN_EXPO);
        c.at(0, () -> {
            ScreenFx.letterbox(0.6f);
            ScreenFx.speed(700, 0.6f);
            CameraDirector.shake(0.4f, 700);
        });
        return c;
    }

    public static Cutscene microOverwhelm(int caster) {
        Cutscene c = new Cutscene(caster, 2.6, true);
        c.key(0.0, 0.2, 3.4, 0.3, 1.5, 0, -0.2, 72, 0, Ease.LINEAR);
        c.key(1.3, 1.2, 3.0, 0.6, 1.5, 0, -0.2, 76, -4, Ease.IN_OUT);
        c.cut(1.35, 4.5, -0.5, 0.2, 0, 0, -0.2, 62, 6);
        c.key(2.6, 5.0, -0.6, 0.0, 0, 0, -0.2, 60, 3, Ease.LINEAR);
        c.at(0, () -> {
            ScreenFx.letterbox(0.6f);
            ScreenFx.speed(2600, 0.9f);
        });
        c.at(1.35, () -> ScreenFx.impact(ScreenFx.INVERT, 80, null));
        return c;
    }

    public static Cutscene microSurge(int caster) {
        Cutscene c = new Cutscene(caster, 1.1, true);
        c.key(0.0, -2.5, 0, -0.6, 0, 0, 1.0, 70, 0, Ease.LINEAR);
        c.key(0.6, -3.0, 0, 3.5, 0, 0, -1.2, 82, 0, Ease.OUT_CUBIC);
        c.key(1.1, -3.2, 0, 4.0, 0, 0, -1.5, 86, 0, Ease.LINEAR);
        c.at(0, () -> {
            ScreenFx.letterbox(0.5f);
            ScreenFx.speed(1100, 0.7f);
        });
        return c;
    }

    public static Cutscene microRush(int caster) {
        Cutscene c = new Cutscene(caster, 0.6, true);
        c.key(0.0, -2.2, -1.2, 0.2, 2, 0, 0, 88, 0, Ease.LINEAR);
        c.key(0.6, -2.6, -1.0, 0.3, 2, 0, 0, 96, -5, Ease.LINEAR);
        c.at(0, () -> {
            ScreenFx.letterbox(0.5f);
            ScreenFx.speed(600, 1f);
        });
        return c;
    }

    private Cutscenes() {}
}
