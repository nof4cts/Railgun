package com.sixpaths.client;

import com.sixpaths.Ability;
import com.sixpaths.network.FxPacket;
import com.sixpaths.network.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server FX events → layered effects, shader impact frames, manga panels and camera work. */
public final class FxDispatcher {

    static boolean feels(Vec3 pos, double range) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.position().distanceTo(pos) < range;
    }

    static boolean mine(int src) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == src;
    }

    static Vec3 ground(Vec3 p) {
        return new Vec3(p.x, SpellFx.groundY(p.x, p.z, p.y), p.z);
    }

    static int summonColor(int kind) {
        return Ability.values()[Ability.SUMMON_RHINO.ordinal() + Math.max(0, Math.min(3, kind))].color;
    }

    public static void handle(FxPacket p) {
        Vec3 pos = new Vec3(p.x, p.y, p.z);
        switch (FxType.byId(p.type)) {
            case PUSH -> push(p, pos);
            case PULL -> {
                Vfx.add(new PainFx.PullStream(p.src, p.tgt, 520));
                Vfx.add(Vfx.Shockwave.facing(pos.add(0, 1, 0), new Vec3(0, 1, 0), 2.5, 0xFFB9A8FF, 400));
                Vfx.add(new Vfx.Flash(pos.add(0, 1, 0), 1.2, 0xFFB9A8FF, 260));
                if (mine(p.src)) {
                    ScreenFx.post(pos, 0.5f, 450);
                    ScreenFx.speed(450, 0.6f);
                }
            }
            case ROD_THROW -> {
                Vec3 dir = Draw.forward(p.a, p.b);
                Vfx.add(new Vfx.Flash(pos.add(dir.scale(0.8)), 0.6, 0xFFB48CFF, 180));
                Vfx.add(new Vfx.Spikes(pos.add(dir.scale(0.8)), 8, 2.5, 0xFFE0D8FF, 180, dir, 0.9));
            }
            case ROD_HIT -> {
                if (p.a >= 2) {
                    Vfx.add(new Vfx.Flash(pos, 2.2, 0xFFB48CFF, 420));
                    Vfx.add(new Vfx.Spikes(pos, 30, 5, 0xFFFFFFFF, 300));
                    Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0, 0, 1), 4, 0xFFFFFFFF, 400));
                    if (feels(pos, 40)) {
                        ScreenFx.cineHit(pos, 0xFFB48CFF, 0.4f);
                        ScreenFx.panels(520, 2, 0xFFFFFFFF);
                        CameraDirector.shake(1.0f, 350);
                    }
                    Sfx.play(SoundEvents.TRIDENT_HIT, 0.5f, 1f);
                } else if (p.a >= 1) {
                    Vfx.add(new Vfx.Flash(pos, 0.9, 0xFFB48CFF, 220));
                    Vfx.add(new Vfx.Spikes(pos, 12, 1.8, 0xFFE0D8FF, 200));
                } else {
                    Vfx.add(new Vfx.Debris(pos, 6, 0.5, 900));
                    Vfx.add(new Vfx.Spikes(pos, 6, 1.0, 0xFFB48CFF, 160));
                }
            }
            case CORE_THROW -> {
                Vfx.add(new PainFx.CoreThrow(p.src, pos, 1000));
                Vec3 g = ground(pos.subtract(0, 14, 0));
                Sfx.later(1000, () -> {
                    Vfx.add(new PainFx.GravityCore(pos, 5250));
                    Vfx.add(new PainFx.RockSpiral(pos, g, 110, 5250));
                    Vfx.add(new Vfx.Flash(pos, 4, 0xFF9B6CFF, 500));
                    Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0.2, 1, 0.1), 16, 0xFFB9A8FF, 900));
                    Vfx.add(Vfx.Shockwave.ground(g, 22, 0xFF9B6CFF, 1400));
                    Vfx.add(new SpellFx.GroundCracks(g, 18, 12, 6000));
                    if (feels(pos, 120)) {
                        ScreenFx.grade(0xFF9C88D8, 0.45f, 800, 4200, 1500);
                        ScreenFx.frames(pos, 0xFF9B6CFF, 0, new int[]{ScreenFx.S_INK, ScreenFx.S_NEG}, new long[]{55, 55});
                        ScreenFx.ring(pos, 800);
                        CameraDirector.shake(0.8f, 600);
                    }
                    Sfx.play(SoundEvents.WARDEN_SONIC_BOOM, 0.4f, 0.8f);
                });
                for (int i = 0; i < 8; i++) {
                    Sfx.later(1600 + i * 520L, () -> {
                        if (feels(pos, 80)) CameraDirector.shake(0.5f, 400);
                        Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0.2, 1, 0.1), 10, 0xFFE0D8FF, 600));
                    });
                }
            }
            case RIP_BLOCK -> {
                Vfx.add(new Vfx.Flash(pos, 1.0, 0xFF9B6CFF, 220));
                Vfx.add(new Vfx.Debris(pos, 5, 0.7, 1000));
                Vfx.add(new Vfx.Spikes(pos, 5, 1.6, 0xFFB9A8FF, 200, new Vec3(0, 1, 0), 0.8));
            }
            case SPHERE_SEAL -> {
                Vfx.add(new PainFx.Moon(pos, p.a + 0.6, 3000));
                Vfx.add(new SpellFx.ShockDome(pos, 34, 0xFFB9A8FF, 1400));
                Vfx.add(new SpellFx.ShockDome(pos, 16, 0xFFFFFFFF, 700));
                Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0.2, 1, 0.1), 40, 0xFFFFFFFF, 1200));
                Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(1, 0.2, 0.3), 30, 0xFF9B6CFF, 1100));
                Vfx.add(new Vfx.Spikes(pos, 120, 26, 0xFFE0D8FF, 650));
                Vfx.add(new Vfx.Debris(pos, 140, 2.4, 3600));
                Vec3 g = ground(pos.subtract(0, 14, 0));
                Vfx.add(new SpellFx.DustWall(g, 30, Vfx.groundColor(g), 4200));
                Vfx.add(Vfx.Shockwave.ground(g, 34, 0xFFB9A8FF, 1500));
                Sfx.later(2500, () -> {
                    Vfx.add(new Vfx.Debris(pos, 160, 1.6, 3000));
                    Vfx.add(new PainFx.Smoke(pos, 7, 2200));
                });
                if (feels(pos, 160)) {
                    cataclysm(pos, 0xFF9B6CFF, -35, 4.0f);
                    ScreenFx.panels(1000, 3, 0xFFFFFFFF);
                }
            }
            case DESCENT_RISE -> Vfx.add(new PainFx.RiseHalo(p.src, 3700));
            case DESCENT_BLAST -> descentBlast(p, pos);
            case MISSILE_LAUNCH -> {
                Vfx.add(new Vfx.Flash(pos, 0.7, 0xFFFF9A40, 160));
                Vfx.add(new PainFx.Puff(pos, 0.6, 0xFFB0AEB8, 900));
            }
            case MISSILE_BOOM -> {
                Vfx.add(new SpellFx.Fireball(pos, 2.0, 1100));
                Vfx.add(Vfx.Shockwave.ground(pos.subtract(0, 0.4, 0), 5, 0xFFFF8A2A, 500));
                Vfx.add(new Vfx.Debris(pos, 14, 1.0, 1300));
                Vfx.add(new Vfx.Spikes(pos, 16, 3, 0xFFFFC870, 250));
                Vfx.add(new PainFx.Smoke(pos, 1.6, 1500));
                if (feels(pos, 30)) CameraDirector.shake(0.5f, 250);
            }
            case CANNON_CHARGE -> Vfx.add(new PainFx.CannonCharge(p.src, 900));
            case CANNON_BEAM -> {
                Vfx.add(new PainFx.CannonBeam(p.src, pos, 1000));
                Vfx.add(new Vfx.Flash(pos, 5, 0xFF5CE1FF, 650));
                Vfx.add(new SpellFx.ShockDome(pos, 9, 0xFF5CE1FF, 600));
                Vfx.add(new Vfx.Debris(pos, 50, 1.8, 2200));
                Vfx.add(new Vfx.Spikes(pos, 40, 7, 0xFFBFF4FF, 400));
                Vfx.add(new PainFx.Smoke(pos, 3, 2000));
                Entity s = Vfx.entity(p.src);
                if (s != null) Vfx.add(Vfx.Shockwave.facing(PainFx.hand(s, 1f), s.getLookAngle(), 3.5, 0xFFFFFFFF, 400));
                if (feels(pos, 70) || mine(p.src)) {
                    ScreenFx.cineHit(pos, 0xFF5CE1FF, 0.75f);
                    ScreenFx.flash(0xFFBFF4FF, 200);
                    CameraDirector.shake(1.6f, 700);
                    ScreenFx.speed(700, 1f);
                }
            }
            case SOUL_GRAB -> Vfx.add(new PainFx.SoulPull(p.src, p.tgt, false, 2250));
            case SOUL_TORN -> {
                Vfx.add(new PainFx.SoulPull(p.src, p.tgt, true, 1100));
                Vfx.add(new Vfx.Flash(pos, 1.8, 0xFF9FFFE8, 420));
                Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0, 1, 0), 3, 0xFF9FFFE8, 500));
                boolean kill = p.a >= 1, judge = p.b >= 1;
                if (feels(pos, 40)) {
                    int tint = judge ? 0xFFFF5A3A : 0xFF9FFFE8;
                    if (kill || judge) {
                        ScreenFx.frames(pos, tint, 0, new int[]{ScreenFx.S_BLEACH, ScreenFx.S_INK, ScreenFx.S_NEG, ScreenFx.S_INK},
                                new long[]{50, 70, 60, 50});
                        ScreenFx.panels(700, kill ? 3 : 2, 0xFFFFFFFF);
                        ScreenFx.post(pos, 0.9f, 700);
                    } else {
                        ScreenFx.frames(pos, tint, 0, new int[]{ScreenFx.S_INK}, new long[]{60});
                    }
                    CameraDirector.shake(0.8f, 400);
                }
                Sfx.play(SoundEvents.SOUL_ESCAPE, 0.6f, 1f);
            }
            case SUMMON_SEAL -> {
                int kind = (int) p.a;
                double size = p.b;
                int col = summonColor(kind);
                Vfx.add(new PainFx.SummonCircle(pos, size * 1.3, col, 1500));
                Sfx.later(600, () -> {
                    Vfx.add(new PainFx.Smoke(pos, size, 1900));
                    Vfx.add(new Vfx.Flash(pos.add(0, size * 0.6, 0), size * 0.8, 0xFFFFFFFF, 300));
                    Vfx.add(Vfx.Shockwave.ground(pos, size * 3, 0xFFFFFFFF, 700));
                    Vfx.add(new Vfx.Debris(pos, 20, 1.2, 1500));
                    if (feels(pos, 30)) CameraDirector.shake(0.7f, 350);
                    Sfx.play(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.6f, 0.8f);
                });
            }
            case BEAST_ACTION -> {
                BeastAnim.trigger(p.src, (int) p.a);
                Entity e = Vfx.entity(p.src);
                if (e instanceof com.sixpaths.entity.RhinoEntity && p.a == 1) {
                    Vfx.add(new Vfx.Trail(p.src, 0xFFE8DCC0, 1200));
                    Vfx.add(Vfx.Shockwave.ground(pos, 4, 0xFFE8DCC0, 500));
                }
                if (e instanceof com.sixpaths.entity.BirdEntity && p.a == 1) Vfx.add(new Vfx.Trail(p.src, 0xFF8AE0FF, 1500));
            }
            case BEAST_HIT -> {
                Vfx.add(new Vfx.Flash(pos, 1.2, 0xFFFFFFFF, 200));
                Vfx.add(new Vfx.Spikes(pos, 14, 2.2, 0xFFFFFFFF, 220));
                Vfx.add(new Vfx.Debris(pos, 6, 0.6, 900));
                if (p.a >= 1) {
                    // a beast's signature move — the only beast attack that gets an impact frame
                    Vfx.add(Vfx.Shockwave.facing(pos, new Vec3(0, 1, 0), 5, 0xFFFFFFFF, 450));
                    Vfx.add(new Vfx.Debris(pos, 24, 1.4, 1500));
                    if (feels(pos, 28)) {
                        ScreenFx.cineHit(pos, 0xFFFFE0B0, 0.35f);
                        CameraDirector.shake(1.1f, 400);
                    }
                } else if (feels(pos, 16)) CameraDirector.shake(0.25f, 150);
            }
            case HOUND_SPLIT -> {
                Vfx.add(new PainFx.Smoke(pos, 1.4, 900));
                Vfx.add(new Vfx.Flash(pos, 1.0, 0xFF8C7AA8, 220));
            }
            case BEAST_VANISH -> Vfx.add(new PainFx.Smoke(pos, Math.max(1.2, p.a * 0.9), 1200));
            case PRETA_ON -> {
                Vfx.add(new PainFx.PretaShell(p.src, (long) (p.a * 50)));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, 0.05, 0), 4, 0xFF7AFFB0, 500));
                if (mine(p.src)) ScreenFx.grade(0xFFB8FFD8, 0.2f, 150, (long) (p.a * 50) - 400, 250);
            }
            case PRETA_DRINK -> {
                Vfx.add(new PainFx.Drain(pos, p.src, 0xFF7AFFB0, 480));
                Vfx.add(new Vfx.Flash(pos, 0.8, 0xFF7AFFB0, 220));
                Vfx.add(new Vfx.Spikes(pos, 8, 1.4, 0xFF7AFFB0, 180));
            }
            case KING_RISE -> {
                Vfx.add(new PainFx.King(pos, p.a, 1.0, 0xFF6A1E28, 0xFFB08A3A, 0xFFFF6A2A, 0xFFB040FF, 4400, 1100, 1900, 3500));
                Vfx.add(new Vfx.Debris(pos, 60, 1.6, 2500));
                Vfx.add(Vfx.Shockwave.ground(pos, 7, 0xFFB040FF, 900));
                Vfx.add(new SpellFx.DustWall(pos, 6, Vfx.groundColor(pos), 2400));
                if (feels(pos, 30)) CameraDirector.shake(0.9f, 1200);
                Sfx.later(2400, () -> {
                    Entity s = Vfx.entity(p.src);
                    if (s != null) {
                        Vfx.add(new PainFx.Drain(pos.add(0, 1.6, 0), p.src, 0xFF9CFF7A, 900));
                        Vfx.add(new Vfx.Flash(s.position().add(0, 1, 0), 2.2, 0xFF9CFF7A, 600));
                        Vfx.add(Vfx.Shockwave.ground(s.position().add(0, 0.05, 0), 5, 0xFF9CFF7A, 700));
                    }
                });
            }
            case SAMSARA -> {
                Entity s = Vfx.entity(p.src);
                float yaw = s == null ? 0 : s.getYRot();
                Vec3 back = Draw.forward(yaw, 0).scale(-7);
                Vec3 headAt = ground(pos.add(back));
                Vfx.add(new PainFx.King(headAt, yaw, 2.6, 0xFF3E4A44, 0xFF8CA090, 0xFF8CFFB4, 0xFF8CFFB4, 10500, 2200, 3000, 9200));
                Vfx.add(new Vfx.Debris(headAt, 120, 2.0, 3500));
                Vfx.add(new SpellFx.DustWall(headAt, 14, Vfx.groundColor(headAt), 3500));
                Vfx.add(new PainFx.SummonCircle(pos, 9, 0xFF8CFFB4, 10000));
                Sfx.later(3000, () -> Vfx.add(new PainFx.SoulRiver(headAt.add(0, 4, 0), p.a, 7000)));
                if (feels(pos, 140)) {
                    SpaceSky.open(0.55f, 2000, 6000, 2500, 0xFF8CFFB4);
                    ScreenFx.grade(0xFFA8E8C0, 0.35f, 2000, 6000, 2500);
                    CameraDirector.shake(0.6f, 2500);
                }
                Sfx.later(8500, () -> {
                    if (feels(pos, 140)) {
                        ScreenFx.frames(pos.add(0, 12, 0), 0xFF8CFFB4, 0, new int[]{ScreenFx.S_BLEACH, ScreenFx.S_DUO, ScreenFx.S_BLEACH}, new long[]{80, 90, 120});
                        ScreenFx.flash(0xFFE8FFF0, 900);
                        ScreenFx.panels(900, 3, 0xFF8CFFB4);
                    }
                    for (net.minecraft.world.entity.player.Player pl : Minecraft.getInstance().level.players()) {
                        if (pl.position().distanceTo(pos) > p.a) continue;
                        Vfx.add(new Vfx.Pillar(pl.position(), 30, 1.2, 0xFF8CFFB4, 1600));
                        Vfx.add(Vfx.Shockwave.ground(pl.position().add(0, 0.05, 0), 4, 0xFF8CFFB4, 900));
                    }
                });
            }
            case ASCEND -> {
                Vfx.add(new PainFx.Halo(p.src, (long) (p.a * 50)));
                Vfx.add(new Vfx.Flash(pos.add(0, 1, 0), 2.5, 0xFFFFF0C0, 500));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, 0.05, 0), 9, 0xFFFFF0C0, 900));
                Vfx.add(new Vfx.Spikes(pos.add(0, 1, 0), 40, 9, 0xFFFFF0C0, 500, new Vec3(0, 1, 0), 0.7));
                Vfx.add(new Vfx.Pillar(pos, 40, 2, 0xFFFFF0C0, 1200));
                if (feels(pos, 40)) {
                    ScreenFx.cineHit(pos.add(0, 1, 0), 0xFFFFF0C0, 0.4f);
                    CameraDirector.shake(0.6f, 500);
                }
            }
            case SIGHT -> {
                Vfx.add(new PainFx.Scan(pos, p.a, 1600));
                if (mine(p.src)) {
                    ScreenFx.grade(0xFFC8B0FF, 0.35f, 120, 400, 700);
                    ScreenFx.ring(pos.add(Minecraft.getInstance().player.getLookAngle().scale(5)), 700);
                    Sfx.play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 0.8f);
                }
            }
        }
    }

    private static void push(FxPacket p, Vec3 pos) {
        double r = p.a;
        boolean big = p.b >= 1;
        Vec3 g = ground(pos.subtract(0, 1, 0));
        Vfx.add(new PainFx.ForcePush(pos, r, big, 950));
        Vfx.add(Vfx.Shockwave.ground(g, r * 1.5, 0xFFFFFFFF, 900));
        Vfx.add(Vfx.Shockwave.ground(g, r * 1.0, 0xFFD8D0FF, 1100));
        Vfx.add(new SpellFx.DustWall(g, r * 1.2, Vfx.groundColor(g), 1700));
        Vfx.add(new Vfx.Debris(g, big ? 50 : 30, 1.5, 1600));
        Vfx.add(new Vfx.Spikes(pos, 46, r * 0.8, 0xFFFFFFFF, 330, new Vec3(0, 0.2, 0), 0.2));
        if (feels(pos, 48)) {
            ScreenFx.frames(pos, 0xFFE8E4FF, 0, new int[]{ScreenFx.S_BLEACH, ScreenFx.S_INK, ScreenFx.S_NEG}, new long[]{40, 60, 50});
            ScreenFx.post(pos, 0.9f, 650);
            ScreenFx.ring(pos, 700);
            CameraDirector.shake(big ? 1.6f : 1.1f, 450);
            if (mine(p.src)) {
                ScreenFx.card(new ScreenFx.Card(Ability.ALMIGHTY_PUSH.jp, "ALMIGHTY PUSH", "", ScreenFx.Card.CORNER, Ability.ALMIGHTY_PUSH.color, 900, false));
                if (big) ScreenFx.panels(500, 2, 0xFFFFFFFF);
            }
        }
        Sfx.play(SoundEvents.TRIDENT_RIPTIDE_3, 0.6f, 0.9f);
    }

    private static void descentBlast(FxPacket p, Vec3 g) {
        double R = p.a, h = p.b;
        Vfx.add(new PainFx.PressureColumn(g, h, 1000));
        Vfx.add(new SpellFx.ShockDome(g, R + 4, 0xFFFFFFFF, 1500));
        Vfx.add(new SpellFx.ShockDome(g, R * 0.6, 0xFFD8D0FF, 900));
        Vfx.add(new PainFx.ForcePush(g.add(0, 2, 0), R, true, 1300));
        Vfx.add(Vfx.Shockwave.ground(g, R * 1.6, 0xFFFFFFFF, 1500));
        Vfx.add(Vfx.Shockwave.ground(g, R, 0xFFD8D0FF, 1900));
        Sfx.later(200, () -> Vfx.add(Vfx.Shockwave.ground(g, R * 2.3, 0xFFE8E4FF, 2400)));
        int col = Vfx.groundColor(g);
        Vfx.add(new SpellFx.DustWall(g, R * 1.15, col, 4800));
        Sfx.later(500, () -> Vfx.add(new SpellFx.DustWall(g, R * 0.7, col, 4000)));
        Vfx.add(new Vfx.Debris(g, 280, 3.0, 4200));
        Vfx.add(new Vfx.Spikes(g.add(0, 1, 0), 130, R * 0.9, 0xFFFFFFFF, 700, new Vec3(0, 0.15, 0), 0.4));
        for (int i = 0; i < 10; i++) {
            final long k = i;
            Sfx.later(120 + i * 110L, () -> {
                double a = Draw.hash(k * 13) * Math.PI * 2, rr = R * (0.3 + 0.6 * Draw.hash(k * 7));
                Vec3 q = ground(g.add(Math.cos(a) * rr, 0, Math.sin(a) * rr));
                Vfx.add(new PainFx.Smoke(q, 3 + Draw.hash(k) * 2, 2600));
                Vfx.add(new Vfx.Debris(q, 18, 1.6, 2000));
            });
        }
        if (feels(g, 180)) {
            cataclysm(g, 0xFFE8E4FF, 30, 4.4f);
            ScreenFx.panels(1100, 3, 0xFFFFFFFF);
            ScreenFx.grade(0xFFF0F0FF, 0.4f, 0, 1200, 1800);
        }
    }

    /** Signature hit: shader impact sequence, a world split, a long quake, and a second echo frame. */
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
