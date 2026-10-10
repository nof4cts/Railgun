package com.hakari.idg.client;

import com.hakari.idg.network.FxPacket;
import com.hakari.idg.network.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Turns server FX events into choreographed world effects, impact frames and camera shake. */
public final class FxDispatcher {
    private static final int PINK = 0xFFFF4FB8, HOT = 0xFFFF2D7A, GOLD = 0xFFFFD34D, WHITE = 0xFFFFFFFF, CYAN = 0xFF5CF2FF;

    /** Is the local player involved in, or close enough to feel, this hit? */
    private static boolean feels(FxPacket p, Vec3 pos, double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        int me = mc.player.getId();
        return p.src == me || p.tgt == me || mc.player.position().distanceTo(pos) < range;
    }

    private static boolean isMe(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == id;
    }

    public static void handle(FxPacket p) {
        Vec3 pos = new Vec3(p.x, p.y, p.z);
        FxType type = FxType.byId(p.type);
        switch (type) {
            case FLICK -> {
                Vfx.add(new Vfx.Flash(pos, 0.35, PINK, 160));
                Vfx.add(new Vfx.Spikes(pos, 7, 0.7, WHITE, 180));
            }
            case BALL_IMPACT -> {
                boolean close = p.a > 0.5f;
                Vfx.add(new Vfx.Flash(pos, close ? 1.4 : 0.8, PINK, 260));
                Vfx.add(new Vfx.Spikes(pos, close ? 22 : 12, close ? 2.6 : 1.4, PINK, 260));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, -0.3, 0), close ? 3.0 : 1.6, PINK, 380));
                Vfx.add(new Vfx.Debris(pos, close ? 10 : 5, 0.6, 1100));
                if (close && feels(p, pos, 14)) {
                    ScreenFx.impact(ScreenFx.INVERT, 120, pos);
                    CameraDirector.shake(0.9f, 300);
                }
            }
            case DOORS_SLAM -> {
                boolean fast = p.b > 0.5f;
                long close = fast ? 200 : 450;
                Vfx.add(new Vfx.Doors(pos, p.a, fast, false, fast ? 900 : 1300));
                Sfx.later(close, () -> {
                    Vec3 mid = pos.add(0, 1.6, 0);
                    Vfx.add(new Vfx.Flash(mid, 1.6, PINK, 300));
                    Vfx.add(new Vfx.Spikes(mid, 26, 3.2, PINK, 320));
                    Vfx.add(Vfx.Shockwave.ground(pos, 4.5, PINK, 450));
                    Vfx.add(new Vfx.Debris(pos, 12, 0.8, 1300));
                    if (feels(p, pos, 18)) {
                        ScreenFx.impact(ScreenFx.INK, 140, mid);
                        CameraDirector.shake(1.3f, 350);
                    }
                });
            }
            case ROUGH_CHARGE -> {
                Vfx.add(new Vfx.Charge(p.src, 720));
                if (isMe(p.src)) ScreenFx.speed(700, 0.5f);
            }
            case ROUGH_HIT -> {
                Vec3 dir = Draw.forward(p.a, p.b);
                Vfx.add(new Vfx.Flash(pos, 2.6, HOT, 380));
                for (int i = 0; i < 3; i++) {
                    final int k = i;
                    Sfx.later(i * 45L, () -> Vfx.add(Vfx.Shockwave.facing(pos.add(dir.scale(0.8 + k * 1.3)), dir, 1.6 + k * 1.1, k == 0 ? WHITE : HOT, 420)));
                }
                Vfx.add(new Vfx.Spikes(pos, 40, 5.5, HOT, 420, dir, 1.4));
                Vfx.add(new Vfx.Debris(pos.add(0, -1.3, 0), 22, 1.1, 1500));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, -1.4, 0), 6, HOT, 600));
                for (int i = 0; i < 4; i++) Vfx.add(new Vfx.Bolt(pos, pos.add(dir.scale(2.5)).add(Draw.randDir(i + Vfx.now()).scale(1.2)), PINK, 260));
                if (feels(p, pos, 22)) {
                    ScreenFx.impact(ScreenFx.INVERT, 230, pos);
                    CameraDirector.shake(2.2f, 650);
                    ScreenFx.speed(400, 1f);
                }
            }
            case FEVER_KICK1 -> {
                Vfx.add(new Vfx.Trail(p.src, PINK, 450));
                Vfx.add(Vfx.Shockwave.ground(pos, 2.0, WHITE, 300));
            }
            case FEVER_KICK2 -> {
                Vec3 dir = Draw.forward(p.a, -20);
                Vfx.add(new Vfx.Flash(pos, 1.8, PINK, 300));
                Vfx.add(new Vfx.Spikes(pos, 26, 3.4, PINK, 320, dir, 1.0));
                Vfx.add(Vfx.Shockwave.facing(pos, dir, 2.6, WHITE, 350));
                if (feels(p, pos, 18)) {
                    ScreenFx.impact(ScreenFx.INK, 150, pos);
                    CameraDirector.shake(1.4f, 380);
                }
            }
            case COUNTER_READY -> Vfx.add(new Vfx.Doors(pos, p.a, false, true, 700));
            case COUNTER_SLAM -> {
                Vfx.add(new Vfx.Doors(pos, p.a, true, false, 900));
                Sfx.later(200, () -> {
                    Vec3 mid = pos.add(0, 1.4, 0);
                    Vfx.add(new Vfx.Flash(mid, 2.0, PINK, 320));
                    Vfx.add(new Vfx.Spikes(mid, 30, 3.6, WHITE, 340));
                    Vfx.add(Vfx.Shockwave.ground(pos, 5, PINK, 500));
                    if (feels(p, pos, 18)) {
                        ScreenFx.impact(ScreenFx.INVERT, 200, mid);
                        CameraDirector.shake(1.6f, 400);
                        ScreenFx.card(new ScreenFx.Card("COUNTER", "", "", ScreenFx.Card.POP, PINK, 700, false));
                    }
                });
            }
            case VISUAL_TICK -> {
                Vfx.add(Vfx.Shockwave.ground(pos, 2.4, GOLD, 500));
                if (feels(p, pos, 24)) {
                    Sfx.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f + p.a * 0.3f, 0.8f);
                    ScreenFx.card(new ScreenFx.Card(p.a >= 2 ? "SPIN!" : "VISUAL " + (int) p.a + "/2", "", "", ScreenFx.Card.POP, GOLD, 650, p.a >= 2));
                }
            }
            case VOLLEY_FIST -> {
                Vfx.add(new Vfx.Fists(p.src, GOLD, (long) (p.b * 50)));
                if (isMe(p.src)) ScreenFx.speed((long) (p.b * 50), 0.8f);
            }
            case VOLLEY_FINISH -> {
                Vec3 dir = Draw.forward(p.a, 0);
                Vfx.add(new Vfx.Flash(pos, 2.4, GOLD, 360));
                Vfx.add(new Vfx.Spikes(pos, 40, 5, GOLD, 400, dir, 1.2));
                Vfx.add(Vfx.Shockwave.facing(pos, dir, 3.4, WHITE, 400));
                Vfx.add(Vfx.Shockwave.facing(pos.add(dir), dir, 4.4, GOLD, 460));
                if (feels(p, pos, 22)) {
                    ScreenFx.impact(ScreenFx.GOLD, 220, pos);
                    CameraDirector.shake(2f, 500);
                }
            }
            case RUSH_DASH -> {
                long life = p.b > 0.5f ? 2700 : 600;
                Vfx.add(new Vfx.Trail(p.src, GOLD, life));
                Vfx.add(Vfx.Shockwave.ground(pos, 3, GOLD, 400));
                Vfx.add(new Vfx.Debris(pos, 8, 0.6, 1000));
            }
            case RUSH_SLAM -> {
                Vfx.add(new Vfx.Flash(pos, 2.4, GOLD, 360));
                Vfx.add(new Vfx.Spikes(pos, 36, 5, GOLD, 380, new Vec3(0, -1, 0), 1.3));
                if (feels(p, pos, 22)) {
                    ScreenFx.impact(ScreenFx.INK, 210, pos);
                    CameraDirector.shake(2.2f, 550);
                }
            }
            case OVERWHELM_HIT -> {
                Vfx.add(new Vfx.Flash(pos, 1.3, GOLD, 220));
                Vfx.add(new Vfx.Spikes(pos, 14, 2.4, GOLD, 240));
                if (feels(p, pos, 16)) CameraDirector.shake(0.7f, 220);
            }
            case OVERWHELM_THROW -> {
                Vec3 dir = Draw.forward(p.a, 0);
                Vfx.add(new Vfx.Flash(pos, 3.4, GOLD, 450));
                for (int i = 0; i < 4; i++) {
                    final int k = i;
                    Sfx.later(i * 40L, () -> Vfx.add(Vfx.Shockwave.facing(pos.add(dir.scale(k * 1.5)), dir, 2 + k * 1.3, k % 2 == 0 ? WHITE : GOLD, 480)));
                }
                Vfx.add(new Vfx.Spikes(pos, 48, 7, GOLD, 480, dir, 1.5));
                Vfx.add(new Vfx.Debris(pos.add(0, -1, 0), 26, 1.3, 1700));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, -1, 0), 8, GOLD, 700));
                if (feels(p, pos, 26)) {
                    float ang = (float) (Math.random() * 50 - 25);
                    ScreenFx.split(ang, 480, 22);
                    ScreenFx.impact(ScreenFx.INVERT, 260, pos);
                    CameraDirector.shake(3f, 800);
                }
            }
            case SURGE_LEAP -> {
                Vfx.add(new Vfx.Trail(p.src, GOLD, 900));
                Vfx.add(Vfx.Shockwave.ground(pos, 3.5, WHITE, 450));
                Vfx.add(new Vfx.Debris(pos, 14, 0.9, 1200));
            }
            case SURGE_SLAM -> {
                double r = Math.max(2, p.b);
                Vfx.add(Vfx.Shockwave.ground(pos, r * 1.4, GOLD, 650));
                Sfx.later(70, () -> Vfx.add(Vfx.Shockwave.ground(pos, r * 2.2, WHITE, 800)));
                Vfx.add(new Vfx.Debris(pos, (int) (r * 5), 1.4, 1800));
                Vfx.add(new Vfx.Flash(pos.add(0, 0.5, 0), r * 0.5, GOLD, 380));
                Vfx.add(new Vfx.Spikes(pos.add(0, 0.3, 0), 30, r, WHITE, 400, new Vec3(0, 1, 0), 0.8));
                Vfx.add(new Vfx.Pillar(pos, 8, 1.2, GOLD, 500));
                if (feels(p, pos, 26)) {
                    ScreenFx.impact(ScreenFx.INK, 200, pos);
                    CameraDirector.shake(2.6f, 700);
                }
            }
            case SURGE_KICK -> {
                Vec3 dir = Draw.forward(p.a, -10);
                Vfx.add(new Vfx.Flash(pos, 2, GOLD, 300));
                Vfx.add(Vfx.Shockwave.facing(pos, dir, 3, WHITE, 360));
                Vfx.add(new Vfx.Spikes(pos, 28, 4, GOLD, 340, dir, 1.2));
                if (feels(p, pos, 20)) {
                    ScreenFx.impact(ScreenFx.INVERT, 150, pos);
                    CameraDirector.shake(1.5f, 400);
                }
            }
            case RHYTHM -> {
                Vfx.add(new Vfx.RhythmPulse(p.src, 10000));
                Vfx.add(new Vfx.Flash(pos.add(0, 1, 0), 1.6, CYAN, 300));
                if (isMe(p.src)) ScreenFx.card(new ScreenFx.Card("RHYTHM", "cooldowns reset · moves accelerate", "", ScreenFx.Card.POP, CYAN, 1100, false));
            }
            case DOMAIN_COLLAPSE, DOMAIN_SHATTER -> {
                boolean gold = type == FxType.DOMAIN_SHATTER;
                double R = Math.max(4, p.a);
                Vfx.add(new Vfx.Shatter(pos, R, gold ? GOLD : 0xFF8A6A80, 2200));
                Vfx.add(new Vfx.Flash(pos.add(0, 1, 0), R * 0.4, gold ? GOLD : PINK, 500));
                if (feels(p, pos, R + 10)) {
                    CameraDirector.shake(gold ? 1.8f : 1.0f, 700);
                    if (!gold) ScreenFx.card(new ScreenFx.Card("BURNOUT", "the domain collapses", "", ScreenFx.Card.POP, 0xFFAAAAAA, 1200, false));
                }
            }
            case JACKPOT_END -> {
                Vfx.add(Vfx.Shockwave.ground(pos, 5, GOLD, 900));
                if (isMe(p.src)) ScreenFx.card(new ScreenFx.Card("JACKPOT OVER", "the domain is ready — spin again", "", ScreenFx.Card.CORNER, GOLD, 2200, false));
            }
            case SMALL_HIT -> {
                Vfx.add(new Vfx.Flash(pos, 0.9, WHITE, 200));
                Vfx.add(new Vfx.Spikes(pos, 10, 1.8, PINK, 220));
                if (feels(p, pos, 8)) CameraDirector.shake(0.5f, 200);
            }
        }
    }

    static Entity entity(int id) {
        return Vfx.entity(id);
    }

    private FxDispatcher() {}
}
