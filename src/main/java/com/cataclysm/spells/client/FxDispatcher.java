package com.cataclysm.spells.client;

import com.cataclysm.spells.network.FxPacket;
import com.cataclysm.spells.network.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/** Server FX events → layered spell effects, shader impact frames and camera work. */
public final class FxDispatcher {

    static boolean feels(Vec3 pos, double range) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.position().distanceTo(pos) < range;
    }

    private static Vec3 ground(Vec3 around, double r, long seed) {
        double a = Draw.hash(seed) * Math.PI * 2, rr = r * (0.4 + 0.6 * Draw.hash(seed + 1));
        double x = around.x + Math.cos(a) * rr, z = around.z + Math.sin(a) * rr;
        return new Vec3(x, SpellFx.groundY(x, z, around.y), z);
    }

    public static void handle(FxPacket p) {
        Vec3 pos = new Vec3(p.x, p.y, p.z);
        long seed = Vfx.now();
        switch (FxType.byId(p.type)) {
            case STAR_FALL -> {
                Vfx.add(new SpellFx.FallingStar(pos));
                if (feels(pos, 160)) {
                    SpaceSky.open(0.92f, 1400, 3000, 5000, 0xFFFFE6D0);
                    ScreenFx.grade(0xFFFFB070, 0.45f, 2500, 1600, 2500);
                    ScreenFx.heat(0.6f, 4300);
                }
                // fragments break off the star and rain down around ground zero ahead of the main hit
                for (int i = 0; i < 7; i++) {
                    final long k = i;
                    long when = 2300 + i * 190L;
                    Sfx.later(when, () -> {
                        Vec3 from = SpellFx.starPos(pos, when).add(Draw.randDir(seed + k * 13).scale(2));
                        Vec3 to = ground(pos, 24, seed + k * 101);
                        Vfx.add(new SpellFx.Fragment(from, to, 4100 - when - 60));
                    });
                }
            }
            case STAR_IMPACT -> {
                Vfx.add(new SpellFx.Fireball(pos.add(0, 0.5, 0), 16, 3400));
                Vfx.add(new SpellFx.Mushroom(pos, 11000));
                Vfx.add(new SpellFx.ShockDome(pos, 46, 0xFFFFE0B0, 1500));
                Vfx.add(new SpellFx.ShockDome(pos, 24, 0xFFFF8A2A, 900));
                Vfx.add(Vfx.Shockwave.ground(pos, 52, 0xFFFFFFFF, 1300));
                Vfx.add(Vfx.Shockwave.ground(pos, 34, 0xFFFF8A2A, 1700));
                Sfx.later(180, () -> Vfx.add(Vfx.Shockwave.ground(pos, 70, 0xFFFFD0A0, 2200)));
                Vfx.add(new SpellFx.DustWall(pos, 42, 0xFF6A5A50, 4200));
                Vfx.add(new SpellFx.GroundCracks(pos, 26, 16, 9000));
                Vfx.add(new SpellFx.EmberRain(pos, 30, 260, 9000));
                Vfx.add(new Vfx.Debris(pos, 170, 2.8, 3800));
                Vfx.add(new Vfx.Spikes(pos.add(0, 1, 0), 110, 30, 0xFFFFC870, 750, new Vec3(0, 1, 0), 0.5));
                Vfx.add(new Vfx.Pillar(pos, 140, 10, 0xFFFFC870, 1500));
                for (int i = 0; i < 8; i++) {
                    final long k = i;
                    Sfx.later(300 + i * 140L, () -> {
                        Vec3 g = ground(pos, 16, seed + k * 77);
                        Vfx.add(new SpellFx.Fireball(g.add(0, 0.4, 0), 3 + Draw.hash(k) * 2, 1400));
                        Vfx.add(new Vfx.Debris(g, 12, 1.4, 1600));
                    });
                }
                if (feels(pos, 160)) cataclysm(pos, 0xFFFFC850, 38, 4.4f);
            }
            case HOLE_FORM -> {
                Vfx.add(new SpellFx.BlackHole(pos));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, -3.4, 0), 12, 0xFFFFC070, 900));
                if (feels(pos, 120)) {
                    SpaceSky.open(1f, 900, 3300, 1600, 0xFFB0B8FF);
                    ScreenFx.grade(0xFF8A90C0, 0.5f, 900, 2600, 600);
                    ScreenFx.cineHit(pos, 0xFFFFC070, 0.4f);
                }
                for (int i = 0; i < 6; i++) Sfx.later(500 + i * 600L, () -> {
                    Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0.25, 1, 0.15), 14, 0xFFFFC070, 900));
                    if (feels(pos, 80)) ScreenFx.ring(pos, 900);
                });
            }
            case HOLE_COLLAPSE -> {
                Vfx.add(new SpellFx.ShockDome(pos, 30, 0xFFBFE6FF, 1200));
                Vfx.add(new SpellFx.ShockDome(pos, 14, 0xFFFFFFFF, 600));
                Vfx.add(new Vfx.Flash(pos, 9, 0xFFBFE6FF, 700));
                Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0.25, 1, 0.15), 34, 0xFFFFFFFF, 1100));
                Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0.9, 0.2, 0.3), 26, 0xFFBFE6FF, 1000));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, -3.4, 0), 38, 0xFFFFC070, 1400));
                Vfx.add(new Vfx.Spikes(pos, 140, 26, 0xFFBFE6FF, 650));
                Vfx.add(new Vfx.Debris(pos.add(0, -3.4, 0), 110, 2.2, 3200));
                Vfx.add(new SpellFx.DustWall(pos.add(0, -3.4, 0), 30, 0xFF5A5468, 3600));
                Vfx.add(new SpellFx.EmberRain(pos.add(0, -3.4, 0), 18, 120, 5000));
                for (int i = 0; i < 6; i++) {
                    Vec3 b = pos.add(Draw.randDir(seed + i).scale(10));
                    Vfx.add(new Vfx.Bolt(pos, b, 0xFFBFE6FF, 500));
                }
                if (feels(pos, 140)) cataclysm(pos, 0xFFBFE6FF, -40, 3.8f);
            }
            case RIP_BLOCK -> {
                Vfx.add(new Vfx.Flash(pos, 1.1, 0xFFFFC070, 220));
                Vfx.add(new Vfx.Debris(pos, 5, 0.6, 900));
                Vfx.add(new Vfx.Spikes(pos, 6, 1.6, 0xFFFFC070, 200));
            }
            case LANCE_SIGIL -> {
                Vec3 dir = Draw.forward(p.a, 0);
                double len = p.b;
                Vfx.add(new SpellFx.Sigil(pos.add(dir.scale(len / 2)), dir, len / 2 + 2, 4200));
                Vfx.add(new SpellFx.GuideBeam(new Vec3(pos.x, SpellFx.groundY(pos.x, pos.z, pos.y), pos.z), 2000));
                if (feels(pos, 120)) {
                    ScreenFx.grade(0xFFFFE0A0, 0.35f, 1500, 1500, 1500);
                    SpaceSky.open(0.65f, 900, 2600, 2600, 0xFFFFF0C0);
                }
            }
            case LANCE_STRIKE -> {
                Vec3 dir = Draw.forward(p.a, 0);
                double len = p.b;
                Vfx.add(new SpellFx.LanceBeam(pos, dir, len, 2600));
                Vfx.add(new SpellFx.BeamArcs(pos, dir, len, 1550, 2100));
                Vfx.add(new SpellFx.SmokeColumns(pos, dir, len, 9000));
                Vfx.add(new SpellFx.EmberRain(pos.add(dir.scale(len / 2)), len / 2, 180, 7000));
                if (feels(pos, 140)) {
                    cataclysm(pos, 0xFFFFD86A, 26, 2.8f);
                    ScreenFx.speed(1700, 0.8f);
                    ScreenFx.heat(0.8f, 3500);
                }
                for (int i = 0; i <= 12; i++) {
                    final double k = i / 12.0;
                    Sfx.later(100 + (long) (1550 * k), () -> {
                        Vec3 c = pos.add(dir.scale(len * k));
                        Vec3 g = new Vec3(c.x, SpellFx.groundY(c.x, c.z, c.y), c.z);
                        Vfx.add(Vfx.Shockwave.ground(g, 8, 0xFFFFD86A, 600));
                        Vfx.add(new SpellFx.Fireball(g.add(0, 0.5, 0), 3.2, 1300));
                        Vfx.add(new Vfx.Debris(g, 16, 1.4, 1700));
                        Vfx.add(new Vfx.Spikes(g.add(0, 0.5, 0), 22, 7, 0xFFFFF4D0, 350, new Vec3(0, 1, 0), 0.9));
                        if (feels(g, 50)) {
                            CameraDirector.shake(1.4f, 260);
                            ScreenFx.ring(g, 420);
                        }
                    });
                }
                Sfx.later(1700, () -> Vfx.add(new SpellFx.GroundCracks(pos.add(dir.scale(len / 2)), len * 0.45, 12, 8000)));
            }
        }
    }

    /** Signature cataclysm hit: shader impact sequence, a world split, a long quake, and a second echo frame. */
    static void cataclysm(Vec3 pos, int tint, float splitAngle, float shake) {
        ScreenFx.cineHit(pos, tint, 1.2f);
        ScreenFx.flash(0xFFFFFFFF, 260);
        ScreenFx.split(splitAngle, 900, 42);
        CameraDirector.shake(shake, 2000);
        ScreenFx.speed(1300, 1f);
        Sfx.later(650, () -> ScreenFx.frames(pos, tint, 0, new int[]{ScreenFx.S_INK, ScreenFx.S_DUO}, new long[]{40, 50}));
        Sfx.later(1000, () -> ScreenFx.post(pos, 0.7f, 900));
        Sfx.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1f);
        Sfx.play(SoundEvents.GENERIC_EXPLODE, 0.5f, 1f);
        Sfx.later(120, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1f);
    }

    private FxDispatcher() {}
}
