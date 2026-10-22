package com.woh.client;

import com.jjk.mod.client.JjkPrefs;
import com.woh.W;
import com.woh.common.Beat;
import com.woh.common.WMove;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The cutscene director. Every move with a cutscene gets a shot list: built from the move's own timeline (where its
 * blows land, how big its destruction is), with hand-made shot lists for the domain, Overdrive, the Meteor, the World
 * Splitter and both final ultimates. Cuts land exactly on the hits; between them the camera eases. Hold Sneak to skip;
 * control always comes back at the end, on death, on logout or if anything goes wrong.
 */
public final class WCam {
    public record Pose(Vec3 pos, float yaw, float pitch, float roll, float fov) {}

    private record Shot(int t0, int t1, int blend, Function<Float, Pose> at) {}

    private static WCasts.Live live;
    private static Marker camera;
    private static CameraType saved;
    private static float lockYaw, lockPitch;
    private static int skipHeld;
    private static Pose pose;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static double titleStart;
    private static Shot lastShot;
    private static boolean shotChanged, cutIsHard;
    private static long lastNanos;

    private WCam() {}

    public static boolean running() {
        return live != null;
    }

    public static Pose pose() {
        return live == null ? null : pose;
    }

    public static WMove move() {
        return live == null ? null : live.move;
    }

    public static void start(WCasts.Live l) {
        if (!W.cutscenes || JjkPrefs.skipCutscenes()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        stop();
        live = l;
        skipHeld = 0;
        lockYaw = mc.player.getYRot();
        lockPitch = mc.player.getXRot();
        camera = new Marker(EntityType.MARKER, mc.level);
        camera.setPos(mc.player.getEyePosition());
        saved = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
        titleStart = WFx.time();
        lastShot = null;
        shotChanged = cutIsHard = false;
        lastNanos = 0;
        pose = null;
        SHOTS.clear();
        try {
            build(l);
        } catch (Throwable t) {
            W.LOG.error("[woh] couldn't direct {}", l.move, t);
            stop();
        }
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (live == null) return;
        live = null;
        pose = null;
        SHOTS.clear();
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (saved != null) mc.options.setCameraType(saved);
        saved = null;
        camera = null;
    }

    public static void tick() {
        if (live == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || !WCasts.LIVE.contains(live)) {
            stop();
            return;
        }
        skipHeld = mc.options.keyShift.isDown() ? skipHeld + 1 : 0;
        if (live.t >= live.move.end + 4 || skipHeld >= 12) stop();
    }

    public static void frame(float partial) {
        if (live == null || camera == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stop();
            return;
        }
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
        // his body keeps facing where he cast (mouse movement doesn't swing his aim mid-move)
        mc.player.setYRot(lockYaw);
        mc.player.yRotO = lockYaw;
        mc.player.setXRot(lockPitch);
        mc.player.xRotO = lockPitch;
        float t = (float) Math.max(0, live.t);
        try {
            Pose target = evaluate(t);
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
            lastNanos = now;
            if (pose == null || shotChanged) {
                pose = target;                      // a cut: land on the new framing exactly
                if (shotChanged && cutIsHard) {
                    WFx.punch(0.18f);
                }
                shotChanged = false;
            } else {
                // follow the shot through a short spring: anything jerky (a knocked-back target, a teleport step)
                // is ironed into a smooth camera move, and nothing lags more than a few frames
                float k = 1f - (float) Math.exp(-dt / 0.055f);
                float kr = 1f - (float) Math.exp(-dt / 0.04f);
                pose = new Pose(pose.pos().lerp(target.pos(), k), Mth.rotLerp(kr, pose.yaw(), target.yaw()),
                        Mth.lerp(kr, pose.pitch(), target.pitch()), Mth.lerp(kr, pose.roll(), target.roll()), Mth.lerp(k, pose.fov(), target.fov()));
            }
        } catch (Throwable e) {
            W.LOG.error("[woh] camera failed", e);
            stop();
            return;
        }
        if (pose == null) return;
        Vec3 p = pose.pos();
        camera.setPos(p.x, p.y, p.z);
        camera.xo = camera.xOld = p.x;
        camera.yo = camera.yOld = p.y;
        camera.zo = camera.zOld = p.z;
        camera.setYRot(pose.yaw());
        camera.yRotO = pose.yaw();
        camera.setXRot(pose.pitch());
        camera.xRotO = pose.pitch();
    }

    private static Pose evaluate(float t) {
        Shot cur = null, prev = null;
        for (Shot s : SHOTS) {
            if (t >= s.t0) {
                if (cur != null && s.t0 > cur.t0) prev = cur;
                if (cur == null || s.t0 >= cur.t0) cur = s;
            }
        }
        if (cur == null) cur = SHOTS.get(0);
        if (cur != lastShot) {
            shotChanged = lastShot != null && cur.blend == 0;
            cutIsHard = shotChanged;
            lastShot = cur;
        }
        Pose a = cur.at.apply(t);
        if (prev != null && cur.blend > 0 && t < cur.t0 + cur.blend) {
            float u = ease((t - cur.t0) / cur.blend);
            Pose b = prev.at.apply(t);
            a = mix(b, a, u);
        }
        return a;
    }

    private static Pose mix(Pose a, Pose b, float u) {
        return new Pose(a.pos().lerp(b.pos(), u), Mth.rotLerp(u, a.yaw(), b.yaw()), Mth.lerp(u, a.pitch(), b.pitch()),
                Mth.lerp(u, a.roll(), b.roll()), Mth.lerp(u, a.fov(), b.fov()));
    }

    static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0, 1);
    }

    // ------------------------------------------------------------------ helpers
    private static Pose look(Vec3 from, Vec3 at, float roll, float fov) {
        from = clear(from);
        Vec3 d = at.subtract(from);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
        return new Pose(from, yaw, pitch, roll, fov);
    }

    /** Keep the camera out of the ground. */
    private static Vec3 clear(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return p;
        int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
        if (h > mc.level.getMinBuildHeight() && p.y < h + 0.6 && p.y > h - 6) return new Vec3(p.x, h + 0.6, p.z);
        return p;
    }

    private static Vec3 caster(float yOff) {
        return live.at(Beat.CASTER, new Vec3(0, yOff, 0));
    }

    private static Vec3 target() {
        return live.at(Beat.TARGET, Vec3.ZERO);
    }

    private static Vec3 ground() {
        return live.at(Beat.GROUND, Vec3.ZERO);
    }

    private static Vec3 handheld(float t, double a) {
        return new Vec3(Math.sin(t * 0.071) * 0.6 + Math.sin(t * 0.23) * 0.25, Math.sin(t * 0.053 + 1.3) * 0.4 + Math.sin(t * 0.19) * 0.2,
                Math.sin(t * 0.061 + 2.1) * 0.6).scale(a * W.motion);
    }

    private static Vec3 rot(Vec3 f, double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return new Vec3(f.x * c - f.z * s, 0, f.x * s + f.z * c);
    }

    private static void shot(int t0, int t1, int blend, Function<Float, Pose> at) {
        SHOTS.add(new Shot(t0, t1, blend, at));
    }

    // ------------------------------------------------------------------ shot lists
    private static void build(WCasts.Live l) {
        Vec3 F = l.c.f(), R = l.c.r();
        int end = l.move.end;
        switch (l.move) {
            case TIME_STOP -> {
                shot(0, 48, 0, t -> look(caster(1.5f).add(rot(F, 30 + t * 0.9).scale(3.2 + t * 0.02)).add(0, -0.2, 0), caster(1.5f), 0, 60));
                shot(48, 62, 0, t -> look(caster(1.55f).add(F.scale(1.5 - span(t, 48, 62) * 0.3)), caster(1.55f), 0, 55));
                shot(62, end, 0, t -> {
                    float u = ease(span(t, 62, end));
                    double r = l.c.domainRadius();
                    Vec3 from = caster(1.4f).add(rot(F, 160 + u * 50).scale(4 + u * r * 1.1)).add(0, 1 + u * r * 0.55, 0);
                    return look(from.add(handheld(t, 0.2)), caster(1.2f + u * 4), 0, 70 + u * 8);
                });
            }
            case HEAVEN_ASCENSION -> {
                shot(0, 40, 0, t -> look(caster(1.3f).add(rot(F, 15).scale(5 - span(t, 0, 40) * 2.6)).add(0, -0.5, 0), caster(1.4f), 0, 62));
                shot(40, end, 0, t -> {
                    float u = 1 - (float) Math.pow(1 - span(t, 40, 52), 3);
                    return look(caster(0.4f).add(rot(F, -20).scale(2.4 + u * 6)).add(handheld(t, 0.3)), caster(1.5f), -4, 75);
                });
            }
            case HEAVENLY_LIGHTNING -> {
                Vec3 base = l.c.origin();
                shot(0, 28, 0, t -> look(base.add(F.scale(9)).add(R.scale(3)).add(0, 1.5, 0), caster(1f), 0, 70));
                shot(28, 36, 0, t -> look(base.add(F.scale(4)).add(0, 1.0, 0), caster(1f), 8, 80));
                shot(36, 46, 0, t -> {
                    Vec3 g = ground();
                    return look(g.add(R.scale(26)).add(F.scale(-8)).add(0, 4, 0), caster(0.5f).lerp(g, 0.35), 0, 72);
                });
                wide(46, end, 0, 20 * l.c.ds(), 1f);
            }
            case ROAD_TO_HEAVEN -> {
                shot(0, 22, 0, t -> look(caster(1.6f).add(F.scale(-2.6)).add(R.scale(1.1)).add(0, 0.3, 0), caster(1.4f).add(F.scale(20)), 0, 66));
                shot(22, end, 0, t -> {
                    float u = ease(span(t, 22, end));
                    Vec3 from = caster(1f).add(F.scale(-6 - u * 10)).add(R.scale(3)).add(0, 4 + u * 14, 0);
                    return look(from.add(handheld(t, 0.4)), caster(0f).add(F.scale(40 + u * 60)), 0, 72);
                });
            }
            case WORLDS_TRUTH -> {
                double W = l.c.worldEnder();
                shot(0, 40, 0, t -> look(caster(0.3f).add(rot(F, 140 + t * 0.6).scale(12)).add(0, 0.6, 0), caster(1.6f), 0, 58));
                shot(40, 112, 12, t -> look(caster(1.3f).add(rot(F, 40 + (t - 40) * 1.6).scale(3.2)).add(0, -0.3, 0).add(handheld(t, 0.08)), caster(1.3f), 0, 60));
                shot(112, 120, 0, t -> look(caster(1.6f).add(F.scale(-2.4)).add(R.scale(1.0)), target(), 0, 78));
                hit(120, 126, l, 3);
                hit(126, 136, l, 2);
                shot(136, end, 0, t -> {
                    float u = ease(span(t, 136, end));
                    Vec3 g = ground();
                    Vec3 from = g.add(F.scale(-W * (0.3 + 0.6 * u))).add(R.scale(W * 0.2)).add(0, 12 + W * (0.25 + 0.6 * u), 0);
                    return look(from.add(handheld(t, 0.6)), g, 0, 70);
                });
            }
            case REALITY_COLLAPSE -> {
                double r = l.c.domainRadius(), W = l.c.worldEnder() * 0.8;
                shot(0, 40, 0, t -> look(caster(1.5f).add(rot(F, 20 + t)).scale(1).add(rot(F, 20 + t).scale(2.2)), caster(1.5f), 0, 60));
                shot(40, 60, 0, t -> {
                    float u = ease(span(t, 40, 60));
                    return look(caster(1f).add(rot(F, 170).scale(4 + u * r)).add(0, 2 + u * r * 0.5, 0), caster(1.5f), 0, 72);
                });
                shot(60, 110, 10, t -> look(live.at(Beat.DOMAIN, Vec3.ZERO).add(rot(F, 120 + (t - 60) * 0.8).scale(r * 0.9)).add(0, r * 0.5, 0),
                        live.at(Beat.DOMAIN, Vec3.ZERO), 0, 75));
                shot(110, 146, 0, t -> look(live.at(Beat.DOMAIN, Vec3.ZERO).add(rot(F, -60 - (t - 110) * 1.2).scale(r * 0.6)).add(0, 6, 0),
                        live.at(Beat.DOMAIN, new Vec3(0, 2, 0)), 0, 72));
                shot(146, 152, 0, t -> look(caster(1.6f).add(F.scale(-2.4)).add(R.scale(1.0)), target(), 0, 74));
                hit(152, 160, l, -1);
                shot(160, 176, 4, t -> look(caster(1.3f).add(rot(F, 60 + (t - 160) * 2).scale(3.0)), caster(1.3f), 0, 62));
                hit(176, 190, l, 1);
                shot(190, end, 0, t -> {
                    float u = ease(span(t, 190, end));
                    Vec3 g = live.at(Beat.DOMAIN, Vec3.ZERO);
                    Vec3 from = g.add(F.scale(-W * (0.4 + 0.5 * u))).add(0, 14 + W * (0.3 + 0.5 * u), 0);
                    return look(from.add(handheld(t, 0.6)), g, 0, 72);
                });
            }
            case ASCENDED_BARRAGE -> {
                // behind them in a blink; the hold, close; the ground swallowing them, seen from above; black; and him, back
                shot(0, 6, 0, t -> look(caster(1.6f).add(F.scale(-2.4)).add(R.scale(0.9)), target(), 0, 80));
                shot(6, 22, 0, t -> {
                    float u = span(t, 6, 22);
                    return look(caster(1.2f).add(rot(F, 70 + 20 * u).scale(2.2 - 0.4 * u)).add(handheld(t, 0.06)), target().add(0, 0.5, 0), -4, 58);
                });
                shot(22, 63, 0, t -> {
                    Vec3 hole = live.at(Beat.START, Vec3.ZERO);
                    float u = ease(span(t, 22, 63));
                    return look(hole.add(F.scale(-3.5)).add(R.scale(1.5)).add(0, 7 + 3 * u, 0).add(handheld(t, 0.1)), hole.add(0, -4 * u, 0), 0, 70);
                });
                shot(66, end, 0, t -> {
                    float u = ease(span(t, 66, end));
                    return look(live.at(Beat.START, Vec3.ZERO).add(F.scale(3.2 + 1.5 * u)).add(R.scale(-1.0)).add(0, 1.2, 0), caster(1.5f), 0, 62);
                });
            }
            case OVERWRITE_BLOW -> {
                // the energy wells up: low, slow orbit; then coiled, a tight push-in on the fist; the step; the hit from
                // three angles in a row; the second eruption seen from far above
                shot(0, 28, 0, t -> look(caster(0.4f).add(rot(F, 150 - t * 1.8).scale(4.5)).add(0, -0.2, 0).add(handheld(t, 0.05)), caster(1.7f), 0, 60));
                shot(28, 56, 0, t -> {
                    float u = ease(span(t, 28, 56));
                    Vec3 fist = live.at(Beat.HAND, Vec3.ZERO);
                    return look(caster(1.1f).add(rot(F, 75).scale(2.6 - 1.2 * u)).add(handheld(t, 0.05 + 0.1 * u)), fist, 0, 62 - 10 * u);
                });
                shot(56, 64, 0, t -> look(caster(1.7f).add(F.scale(-2.6)).add(R.scale(1.1)), target(), 0, 80));
                hit(66, 72, l, 3);
                hit(72, 80, l, 2);
                hit(80, 94, l, 1);
                orbitImpact(94, 106, 26 * l.c.ds(), 200, 70);
                shot(106, end, 0, t -> {
                    float u = ease(span(t, 106, end));
                    Vec3 g = ground();
                    return look(g.add(F.scale(-30 - 30 * u)).add(R.scale(18)).add(0, 26 + 30 * u, 0).add(handheld(t, 0.5)), g.add(F.scale(6)), 0, 72);
                });
            }
            case ERASURE_HOOK -> {
                shot(0, 12, 0, t -> look(caster(1.4f).add(rot(F, 30 + t * 2).scale(2.8)), caster(1.4f), 0, 60));
                shot(12, 20, 0, t -> look(caster(1.0f).add(F.scale(-3.5)).add(0, 1.4, 0), caster(1.8f).add(F.scale(2)), 0, 82));   // the five orbs around him
                shot(20, 32, 0, t -> {
                    float u = span(t, 20, 32);
                    Vec3 tg = target();
                    Vec3 orb = caster(1.6f).lerp(tg, u);
                    return look(orb.add(R.scale(3)).add(0, 1, 0), orb.add(F.scale(2)), 0, 76);     // riding with the orbs
                });
                hit(32, 44, l, 2);
                orbitImpact(44, end, 8, 120, 60);
            }
            case FOURTEEN_PHRASES -> {
                shot(0, 20, 0, t -> look(caster(1.2f).add(rot(F, 20 + t * 1.5).scale(3.0)).add(0, -0.4, 0), caster(1.9f), 0, 64));
                shot(20, 60, 0, t -> {
                    float u = ease(span(t, 20, 60));
                    Vec3 p0 = live.at(Beat.POINT, Vec3.ZERO);
                    return look(p0.add(rot(F, 180 + 40 * u).scale(26)).add(0, 5 + 10 * u, 0).add(handheld(t, 0.3)), p0.add(0, 6, 0), 0, 74);
                });
                shot(60, 84, 0, t -> look(live.at(Beat.POINT, new Vec3(0, 1.0, 0)).add(R.scale(6)).add(F.scale(-4)), live.at(Beat.POINT, new Vec3(0, 18, 0)), -6, 84));  // looking up into the rain
                shot(84, end, 0, t -> {
                    float u = ease(span(t, 84, end));
                    Vec3 p0 = live.at(Beat.POINT, Vec3.ZERO);
                    return look(p0.add(rot(F, 250 + 30 * u).scale(30 + 10 * u)).add(0, 22 + 10 * u, 0), p0, 0, 70);
                });
            }
            case REALITY_OVERWRITE -> {
                shot(0, 20, 0, t -> look(live.at(Beat.HAND, Vec3.ZERO).add(rot(F, 120 + t * 4).scale(1.8)), live.at(Beat.HAND, Vec3.ZERO), 0, 56));  // the blood spinning in his hand
                shot(20, 27, 0, t -> look(caster(1.6f).add(F.scale(-2.4)).add(R.scale(0.9)), target(), 0, 84));
                hit(28, 38, l, 3);
                hit(38, 50, l, 4);
                orbitImpact(50, end, 6, 60, 50);
            }
            case REWRITE_RUSH -> {
                shot(0, 40, 0, t -> look(caster(0.2f).add(rot(F, 200 - t)).scale(1).add(rot(F, 200 - t).scale(3.0)).add(0, -0.6, 0), caster(2.2f).add(0, 3 * span(t, 0, 40), 0), 0, 66));
                shot(40, 100, 0, t -> {
                    float u = ease(span(t, 40, 100));
                    Vec3 sky = live.at(Beat.POINT, new Vec3(0, 40, 0));
                    return look(caster(0.5f).add(F.scale(-12 - 20 * u)).add(R.scale(10)).add(0, 3 + 8 * u, 0), sky.lerp(live.at(Beat.POINT, Vec3.ZERO), 0.3), 0, 76);
                });
                shot(100, 116, 0, t -> {
                    float u = span(t, 100, 116);
                    Vec3 sky = live.at(Beat.POINT, new Vec3(0, 40, 0));
                    Vec3 g = ground();
                    Vec3 drop = sky.lerp(g, u * u);
                    return look(g.add(R.scale(34)).add(F.scale(-10)).add(0, 6, 0), drop, 0, 70);   // following it down
                });
                hit(116, 126, l, 4);
                orbitImpact(126, end, 30 * l.c.ds(), 160, 80);
            }
            case KNIFE_RAIN -> {
                shot(0, 8, 0, t -> look(caster(1.6f).add(F.scale(2.4)).add(R.scale(-0.8)), caster(1.5f), 0, 60));
                shot(8, 30, 0, t -> look(caster(1.7f).add(F.scale(-2.6)).add(R.scale(1.2)).add(handheld(t, 0.15)), live.at(Beat.POINT, Vec3.ZERO), 0, 78));
                shot(30, 60, 0, t -> {
                    float u = ease(span(t, 30, 60));
                    Vec3 p0 = live.at(Beat.POINT, Vec3.ZERO);
                    return look(p0.add(rot(F, 90 + 120 * u).scale(14)).add(0, 3, 0).add(handheld(t, 0.25)), p0, 0, 74);   // circling the storm of cuts
                });
                shot(60, end, 0, t -> {
                    float u = ease(span(t, 60, end));
                    Vec3 p0 = live.at(Beat.POINT, Vec3.ZERO);
                    return look(p0.add(F.scale(-20 - 14 * u)).add(0, 14 + 12 * u, 0), p0, 0, 70);
                });
            }
            case COLLAPSE_FINISHER -> {
                shot(0, 24, 0, t -> look(caster(1.0f).add(rot(F, 60).scale(2.6 - 0.8 * span(t, 0, 24))).add(handheld(t, 0.06)), live.at(Beat.HAND, Vec3.ZERO), 0, 58));
                shot(24, 36, 0, t -> look(caster(1.7f).add(F.scale(-2.6)).add(R.scale(1.1)), target(), 0, 82));
                hit(38, 46, l, 3);
                hit(46, 56, l, 2);
                shot(56, end, 0, t -> {
                    float u = ease(span(t, 56, end));
                    Vec3 dc = live.at(Beat.DOMAIN, Vec3.ZERO);
                    double r = l.c.domainRadius();
                    return look(dc.add(rot(F, 200 + 50 * u).scale(r * (0.5 + 0.6 * u))).add(0, 4 + r * 0.5 * u, 0), dc, 0, 74);
                });
            }
            case STILLNESS -> {
                double r = Math.max(8, l.c.domainRadius());
                shot(0, 40, 0, t -> {
                    Vec3 dc = live.at(Beat.DOMAIN, Vec3.ZERO);
                    return look(dc.add(rot(F, 40 + t * 3).scale(r * 0.55)).add(0, 5, 0).add(handheld(t, 0.2)), dc.add(0, 2, 0), 0, 76);
                });
                shot(40, 70, 0, t -> look(caster(1.6f).add(F.scale(-2.4)).add(R.scale(1.0)).add(handheld(t, 0.2)), target(), 0, 80));
                hit(74, 84, l, 4);
                shot(84, end, 0, t -> {
                    float u = ease(span(t, 84, end));
                    Vec3 dc = live.at(Beat.DOMAIN, Vec3.ZERO);
                    return look(dc.add(rot(F, 300 + 40 * u).scale(r * (0.6 + 0.4 * u))).add(0, r * (0.3 + 0.4 * u), 0), dc, 0, 72);
                });
            }
            default -> generic(l);
        }
        SHOTS.sort((a, b) -> Integer.compare(a.t0, b.t0));
        if (SHOTS.isEmpty()) generic(l);
    }

    /**
     * A blow landing, cut in on the frame of the hit. Framings rotate so no two hits look alike:
     * side (+1 / -1): profile of the exchange, drifting in;
     * 2: low hero angle from behind the target, looking up past it into his face;
     * 3: over his shoulder, tight, rolling into the punch;
     * 4: a high crash-zoom down onto the contact point.
     */
    private static void hit(int t0, int t1, WCasts.Live l, int side) {
        shot(t0 - 2, t1, 0, t -> {
            Vec3 c = caster(1.2f), tg = target();
            Vec3 d = tg.subtract(c);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            if (flat.lengthSqr() < 1e-4) flat = l.c.f();
            flat = flat.normalize();
            Vec3 perp = new Vec3(-flat.z, 0, flat.x);
            Vec3 mid = c.lerp(tg, 0.55);
            float u = span(t, t0, t1);
            float e = 1 - (float) Math.pow(1 - u, 3);
            switch (side) {
                case 2 -> {
                    Vec3 from = tg.add(flat.scale(2.6 + 1.2 * e)).add(perp.scale(1.1)).add(0, -0.9, 0);
                    return look(from.add(handheld(t, 0.12)), c.add(0, 0.35, 0), -4, 82 - 6 * e);
                }
                case 3 -> {
                    Vec3 from = c.add(flat.scale(-1.9 + 0.5 * e)).add(perp.scale(0.9)).add(0, 0.55, 0);
                    return look(from.add(handheld(t, 0.1)), tg, 6 * (1 - e), 78 + 8 * e);
                }
                case 4 -> {
                    double h = 9 - 4 * e;
                    Vec3 from = mid.add(flat.scale(-2.0)).add(perp.scale(2.5)).add(0, h, 0);
                    return look(from, tg.add(0, -0.5, 0), 0, 70 - 10 * e);
                }
                default -> {
                    double dist = 4.5 + d.length() * 0.4;
                    Vec3 from = mid.add(perp.scale(side * dist * (1 - 0.14 * e))).add(flat.scale(-1.2)).add(0, 0.5, 0);
                    return look(from.add(handheld(t, 0.15)), mid.add(flat.scale(0.6)), side * 3, 74);
                }
            }
        });
    }

    /** After a blow: the camera circles the impact as the dust rises, pulling out to show the damage. */
    private static void orbitImpact(int t0, int t1, double radius, double startDeg, double sweep) {
        shot(t0, t1, 0, t -> {
            float u = ease(span(t, t0, t1));
            Vec3 g = ground();
            Vec3 F = live.c.f();
            double d = 6 + radius * (0.9 + 0.9 * u);
            Vec3 from = g.add(rot(F, startDeg + sweep * u).scale(d)).add(0, 2.5 + radius * (0.35 + 0.45 * u), 0);
            return look(from.add(handheld(t, 0.3)), g.add(0, 1.0 + radius * 0.1, 0), 0, 72);
        });
    }

    private static void wide(int t0, int t1, int blend, double radius, float side) {
        shot(t0, t1, blend, t -> {
            float u = ease(span(t, t0, t1));
            Vec3 g = ground();
            Vec3 F = live.c.f(), R = live.c.r();
            double d = 8 + radius * (1.1 + 0.8 * u);
            Vec3 from = g.add(F.scale(-d * 0.7)).add(R.scale(side * d * 0.7)).add(0, 4 + radius * (0.5 + 0.5 * u), 0);
            return look(from.add(handheld(t, 0.4)), g.add(0, 1, 0), 0, 70);
        });
    }

    /** Any other move: build-up on him, a cut in on every blow, and a pull-back over what is left. */
    private static void generic(WCasts.Live l) {
        Vec3 F = l.c.f();
        List<Integer> hits = new ArrayList<>();
        int firstDash = -1;
        double radius = 6;
        for (Beat b : l.beats) {
            if (b.kind() == Beat.BF || b.kind() == Beat.CLEAVE || b.kind() == Beat.IMPACT) {
                if (hits.isEmpty() || b.t() - hits.get(hits.size() - 1) >= 6) hits.add(b.t());
            }
            if (b.kind() == Beat.DASH && firstDash < 0) firstDash = b.t();
            if (b.kind() == Beat.BLAST || b.kind() == Beat.SHELL) radius = Math.max(radius, b.x());
            if (b.kind() == Beat.RING) radius = Math.max(radius, b.x() * 0.35);
        }
        int end = l.move.end;
        int first = hits.isEmpty() ? (int) (end * 0.5) : hits.get(0);
        int lead = Math.max(2, first - 8);
        // build-up
        shot(0, lead, 0, t -> look(caster(1.4f).add(rot(F, 55 - t * 0.8).scale(3.8)).add(0, -0.1, 0).add(handheld(t, 0.08)), caster(1.3f), 0, 62));
        if (firstDash >= 0 && firstDash < first - 4) {
            shot(Math.max(firstDash, 1), first - 2, 3, t -> look(caster(1.7f).add(F.scale(-2.8)).add(l.c.r().scale(1.0)), target(), 0, 76));
        }
        // the blows
        for (int i = 0; i < hits.size(); i++) {
            int h = hits.get(i);
            int next = i + 1 < hits.size() ? hits.get(i + 1) - 2 : Math.min(end, h + 16);
            int[] styles = {3, 1, 2, -1, 4};
            hit(h, Math.max(h + 4, next), l, styles[i % styles.length]);
        }
        // what is left
        int last = hits.isEmpty() ? first : hits.get(hits.size() - 1) + 12;
        if (last < end) wide(Math.min(last, end - 4), end, 4, radius, hits.size() % 2 == 0 ? 1 : -1);
    }

    // ------------------------------------------------------------------ letterbox and title card
    public static void overlay(GuiGraphics g) {
        if (live == null) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = Math.max(28, (int) (h * 0.11));
        // drawn high in the GUI's depth so HUDs that draw after us (the kit's name tag and energy bar) stay hidden
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        double since = WFx.time() - titleStart;
        if (since < 3.2) {
            float a = (float) (Mth.clamp(since / 0.25, 0, 1) * Mth.clamp((3.2 - since) / 0.5, 0, 1));
            int alpha = (int) (a * 255) << 24;
            String title = live.move.title.toUpperCase();
            g.pose().pushPose();
            g.pose().translate(18, h - bar + 6, 0);
            g.pose().scale(1.5f, 1.5f, 1);
            g.drawString(mc.font, title, 0, 0, 0xFFFFFF | alpha, false);
            g.pose().popPose();
            int x = 18 + (int) (mc.font.width(title) * 1.5f) + 8;
            g.fill(18, h - bar + 20, 18 + (int) ((x - 26) * a), h - bar + 21, 0xE6C867 | alpha);
        }
        String hint = "Hold " + mc.options.keyShift.getTranslatedKeyMessage().getString() + " to skip";
        g.drawString(mc.font, hint, w - mc.font.width(hint) - 10, h - bar / 2 - 4, 0x707070, false);
        g.pose().popPose();
    }
}
