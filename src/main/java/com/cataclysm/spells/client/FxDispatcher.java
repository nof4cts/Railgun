package com.cataclysm.spells.client;

import com.cataclysm.spells.network.FxPacket;
import com.cataclysm.spells.network.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/** Server FX events → spell effects, impact frames and shake. */
public final class FxDispatcher {

    static boolean feels(Vec3 pos, double range) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.position().distanceTo(pos) < range;
    }

    public static void handle(FxPacket p) {
        Vec3 pos = new Vec3(p.x, p.y, p.z);
        switch (FxType.byId(p.type)) {
            case STAR_FALL -> Vfx.add(new SpellFx.FallingStar(pos));
            case STAR_IMPACT -> {
                Vfx.add(new SpellFx.Fireball(pos.add(0, 0.5, 0), 15, 3200));
                Vfx.add(new SpellFx.Mushroom(pos, 9500));
                Vfx.add(new SpellFx.ShockDome(pos, 42, 0xFFFFE0B0, 1400));
                Vfx.add(Vfx.Shockwave.ground(pos, 48, 0xFFFFFFFF, 1300));
                Vfx.add(Vfx.Shockwave.ground(pos, 32, 0xFFFF8A2A, 1700));
                Vfx.add(new Vfx.Debris(pos, 150, 2.6, 3500));
                Vfx.add(new Vfx.Spikes(pos.add(0, 1, 0), 90, 26, 0xFFFFC870, 700, new Vec3(0, 1, 0), 0.5));
                Vfx.add(new Vfx.Pillar(pos, 120, 9, 0xFFFFC870, 1400));
                if (feels(pos, 140)) cataclysmFrames(pos, 0xFFFFF4E0, 38, 4.2f);
            }
            case HOLE_FORM -> {
                Vfx.add(new SpellFx.BlackHole(pos));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, -3.4, 0), 10, 0xFFFFC070, 900));
            }
            case HOLE_COLLAPSE -> {
                Vfx.add(new SpellFx.ShockDome(pos, 26, 0xFFBFE6FF, 1100));
                Vfx.add(new Vfx.Flash(pos, 8, 0xFFBFE6FF, 600));
                Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0.25, 1, 0.15), 30, 0xFFFFFFFF, 1000));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, -3.4, 0), 34, 0xFFFFC070, 1300));
                Vfx.add(new Vfx.Spikes(pos, 100, 22, 0xFFBFE6FF, 600));
                Vfx.add(new Vfx.Debris(pos.add(0, -3.4, 0), 90, 2.0, 3000));
                if (feels(pos, 110)) cataclysmFrames(pos, 0xFFDDEEFF, -40, 3.6f);
            }
            case RIP_BLOCK -> {
                Vfx.add(new Vfx.Flash(pos, 0.9, 0xFFFFC070, 200));
                Vfx.add(new Vfx.Debris(pos, 4, 0.5, 900));
            }
            case LANCE_SIGIL -> {
                Vec3 dir = Draw.forward(p.a, 0);
                double len = p.b;
                Vfx.add(new SpellFx.Sigil(pos.add(dir.scale(len / 2)), dir, len / 2 + 2, 4000));
            }
            case LANCE_STRIKE -> {
                Vec3 dir = Draw.forward(p.a, 0);
                double len = p.b;
                Vfx.add(new SpellFx.LanceBeam(pos, dir, len, 2600));
                if (feels(pos, 120)) {
                    cataclysmFrames(pos, 0xFFFFF4D0, 26, 2.6f);
                    ScreenFx.speed(1600, 0.8f);
                }
                for (int i = 0; i <= 10; i++) {
                    final double k = i / 10.0;
                    Sfx.later(100 + (long) (1550 * k), () -> {
                        Vec3 c = pos.add(dir.scale(len * k));
                        Vec3 g = new Vec3(c.x, SpellFx.groundY(c.x, c.z, c.y), c.z);
                        Vfx.add(Vfx.Shockwave.ground(g, 7, 0xFFFFD86A, 600));
                        Vfx.add(new Vfx.Debris(g, 14, 1.3, 1600));
                        Vfx.add(new Vfx.Flash(g.add(0, 1, 0), 3, 0xFFFFF4D0, 260));
                        if (feels(g, 50)) CameraDirector.shake(1.4f, 260);
                    });
                }
            }
        }
    }

    /** The big one: a stuttering sequence of eye-searing impact frames, a world split and a long quake. */
    static void cataclysmFrames(Vec3 pos, int flashCol, float splitAngle, float shake) {
        ScreenFx.flash(flashCol, 900);
        ScreenFx.impact(ScreenFx.INVERT, 160, pos);
        Sfx.later(160, () -> ScreenFx.impact(ScreenFx.GOLD, 140, pos));
        Sfx.later(300, () -> ScreenFx.impact(ScreenFx.INK, 150, pos));
        Sfx.later(450, () -> ScreenFx.impact(ScreenFx.RED, 120, pos));
        Sfx.later(570, () -> ScreenFx.impact(ScreenFx.INVERT, 80, pos));
        ScreenFx.split(splitAngle, 900, 40);
        CameraDirector.shake(shake, 1800);
        ScreenFx.speed(1200, 1f);
        Sfx.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1f);
        Sfx.play(SoundEvents.GENERIC_EXPLODE, 0.5f, 1f);
    }

    private FxDispatcher() {}
}
