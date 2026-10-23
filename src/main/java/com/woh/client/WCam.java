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
    // ------------------------------------------------------------------ his camera language
    // Still, symmetrical frames; slow cranes toward the sky; hard cuts on the beat. When the Stand is out striking,
    // the camera frames the Stand and the one it strikes, never him chasing them.

    /** The line from him to his target, flat. */
    private static Vec3 flat() {
        Vec3 d = target().subtract(caster(0f));
        d = new Vec3(d.x, 0, d.z);
        return d.lengthSqr() < 1.0e-4 ? live.c.f() : d.normalize();
    }

    private static Vec3 perp() {
        Vec3 f = flat();
        return new Vec3(-f.z, 0, f.x);
    }

    /** Where the Stand stands when it is out striking: in front of the target, on his side of it. */
    private static Vec3 standAt() {
        return target().subtract(flat().scale(1.35)).add(0, 0.4, 0);
    }

    /** Him, dead centre and level, the camera easing in from dist0 to dist1. */
    private static void icon(int t0, int t1, double dist0, double dist1, float fov) {
        shot(t0, t1, 0, t -> {
            Vec3 f = live.c.f();
            double d = dist0 + (dist1 - dist0) * ease(span(t, t0, t1));
            return look(caster(1.45f).add(f.scale(d)), caster(1.45f), 0, fov);
        });
    }

    /** A level profile two-shot of the Stand and the one it strikes, from one side of the line of action. */
    private static void twoShot(int t0, int t1, double side, double dist, float fov, double drift) {
        shot(t0, t1, 0, t -> {
            Vec3 mid = standAt().lerp(target(), 0.5);
            double d = dist - drift * span(t, t0, t1);
            return look(mid.add(perp().scale(side * d)).add(0, 0.2, 0), mid, 0, fov);
        });
    }

    /** Over the Stand's shoulder onto the one it strikes. */
    private static void overStand(int t0, int t1, float fov) {
        shot(t0, t1, 0, t -> look(standAt().subtract(flat().scale(1.5)).add(perp().scale(0.7)).add(0, 0.9, 0), target(), 0, fov));
    }

    /** From the ground just past them, looking up past them at the sky. */
    private static void worm(int t0, int t1, float roll, float fov) {
        shot(t0, t1, 0, t -> look(target().add(flat().scale(1.8)).add(perp().scale(0.8)).add(0, -0.7, 0), target().add(0, 1.2, 0), roll, fov));
    }

    /** A slow crane straight up behind him, looking down at him. */
    private static void crane(int t0, int t1, double h0, double h1, double behind, float fov) {
        shot(t0, t1, 0, t -> {
            double h = h0 + (h1 - h0) * ease(span(t, t0, t1));
            return look(caster(1.0f).subtract(live.c.f().scale(behind)).add(0, h, 0), caster(1.0f), 0, fov);
        });
    }

    /** Anything without its own staging: him in a still frame, then the Stand on its target (or heaven's view). */
    private static void fallback(WCasts.Live l) {
        int end = l.move.end;
        int first = end / 2;
        for (Beat b : l.beats) {
            if ((b.kind() == Beat.BF || b.kind() == Beat.ERASE) && (b.a0() == Beat.TARGET || b.a0() >= Beat.T0)) {
                first = Math.min(first, b.t());
            }
        }
        int lead = Math.max(3, first - 6);
        icon(0, lead, 4.4, 3.6, 50f);
        if (l.c.targetId() >= 0) {
            overStand(lead, first + 2, 68f);
            twoShot(first + 2, end, 1, 4.4, 58f, 0.8);
        } else {
            crane(lead, end, 3, 12, 3, 60f);
        }
    }

    private static void build(WCasts.Live l) {
        Vec3 F = l.c.f(), R = l.c.r();
        int end = l.move.end;
        switch (l.move) {
            case TIME_STOP -> {
                // The camera itself is the storytelling: while time runs it is alive, handheld, pushing in.
                // The instant he halts the world, the camera stops dead - locked off, no drift, no breath -
                // and holds there while he is the only thing in the frame still moving. When time resumes it
                // snaps back to life. The mark is where he stood when he called it, so the lock never follows him.
                final Vec3 mark = l.c.origin();
                final Vec3 eyeline = mark.add(0, 1.3, 0);
                // A. time still running: a quick breathing push-in onto the rising hand
                shot(0, 7, 0, t -> look(caster(1.25f).add(rot(F, -58).scale(3.4 - 0.6 * span(t, 0, 7))).add(0, 0.15, 0).add(handheld(t, 0.24)),
                        live.at(Beat.HAND, Vec3.ZERO), 0, 64));
                // B. the call: a hard cut to a tight profile, the camera still breathing
                shot(7, 15, 0, t -> look(caster(1.55f).add(rot(F, -104).scale(1.9)).add(0, 0.05, 0).add(handheld(t, 0.15)),
                        caster(1.5f), 0, 50));
                // C. TIME STOPS. The camera stops with it: a fixed frame, absolutely still, held for the
                //    whole of the call - nothing in it moves but him.
                shot(15, 29, 0, t -> look(mark.add(rot(F, 148).scale(5.4)).add(0, 2.1, 0), eyeline, 0, 56));
                // C2. still locked, but now on the frozen one: the Stand stands over them throwing every blow
                //     into a body that cannot fall yet. The frame does not move between blows.
                shot(29, 96, 0, t -> {
                    Vec3 mid = standAt().lerp(target(), 0.45).add(0, 0.25, 0);
                    return look(mid.add(perp().scale(3.9)).add(flat().scale(-0.9)).add(0, 0.55, 0), mid, 0, 54);
                });
                // D. still locked off. He walks through a world that cannot move; the lens only turns to keep him.
                shot(96, 114, 0, t -> look(mark.add(rot(F, 148).scale(5.4)).add(0, 2.1, 0),
                        eyeline.lerp(caster(1.3f), ease(span(t, 96, 114))), 0, 56));
                // E. time resumes: the camera comes back to life, recoiling as every held blow lands at once
                shot(114, end, 0, t -> {
                    float u = ease(span(t, 114, end));
                    Vec3 from = caster(1.45f).add(rot(F, 148 + u * 130).scale(5.4 - u * 2.4)).add(0, 2.1 - u * 0.8, 0);
                    return look(from.add(handheld(t, 0.55 * (1 - u))), target(), 0, 56 + u * 20);
                });
            }
            case OVERWRITE_BLOW -> {
                icon(0, 12, 4.6, 3.3, 50f);                                   // him, dead centre, gathering it
                shot(12, 22, 0, t -> look(live.at(Beat.HAND, Vec3.ZERO).add(R.scale(0.85)).add(F.scale(0.55)).add(0, 0.1, 0),
                        live.at(Beat.HAND, Vec3.ZERO), 0, 42));               // the hand, close
                overStand(22, 27, 70f);                                       // the Stand arriving on them
                twoShot(27, 34, 1, 4.2, 58f, 0.8);                            // the blow, level, in profile
                worm(34, 48, -8f, 80f);                                       // from the ground, as they go
                crane(48, end, 6, 22, 6, 62f);                                // heaven's view of the crater
            }
            case ERASURE_HOOK -> {
                icon(0, 5, 4.0, 3.6, 52f);
                overStand(5, 11, 66f);
                twoShot(11, 18, -1, 3.2, 56f, 0.5);
                worm(18, end, 6f, 76f);
            }
            case REALITY_OVERWRITE -> {
                icon(0, 10, 4.4, 3.4, 48f);
                // the condemned, close and airless
                shot(10, 17, 0, t -> look(target().subtract(flat().scale(2.2)).add(0, 0.3, 0), target().add(0, 0.4, 0), 0, 44));
                overStand(17, 20, 72f);                                       // the decree arrives
                twoShot(20, 32, 1, 5.0, 54f, 1.0);                            // and they are erased
                crane(32, end, 4, 14, 3, 60f);
            }
            case HEAVENLY_LIGHTNING -> {
                final Vec3 P = l.at(Beat.POINT, Vec3.ZERO);
                // under him, looking up past his raised hand into the sky
                shot(0, 12, 0, t -> look(caster(0.2f).add(F.scale(1.4)), caster(4.0f), 0, 70));
                // from where it will fall, straight up at it coming
                shot(12, 16, 0, t -> look(P.add(R.scale(1.2)).add(0, 1.0, 0), P.add(0, 30, 0), 0, 72));
                shot(16, 30, 0, t -> look(P.subtract(F.scale(12)).add(0, 4, 0), P.add(0, 1, 0), 0, 58));
                shot(30, end, 0, t -> look(P.add(F.scale(0.01)).add(0, 8 + 12 * ease(span(t, 30, end)), 0), P, 0, 60));
            }
            case FOURTEEN_PHRASES -> {
                final Vec3 P = l.at(Beat.POINT, Vec3.ZERO);
                // a slow walk round the ring as the sigils are set, one by one
                shot(0, 60, 0, t -> look(P.add(rot(F, 180 + 120 * span(t, 0, 60)).scale(13)).add(0, 5, 0), P, 0, 60));
                icon(60, 70, 3.6, 3.2, 48f);
                // from straight above: the ring detonating round on itself
                shot(70, 112, 0, t -> look(P.add(F.scale(0.01)).add(0, 18, 0), P, 25f * span(t, 70, 112), 64));
                shot(112, 125, 0, t -> look(P.add(R.scale(2.0)).add(0, -0.4, 0), P.add(0, 6, 0), 10, 80));
                shot(125, end, 0, t -> look(P.add(F.scale(0.01)).add(0, 10 + 16 * ease(span(t, 125, end)), 0), P, 0, 62));
            }
            case ROAD_TO_HEAVEN -> {
                shot(0, 6, 0, t -> look(target().add(perp().scale(3.5)).add(0, -0.6, 0), target(), 0, 64));
                // rising with them
                shot(6, 30, 0, t -> look(target().add(perp().scale(4.2)).add(0, -0.4, 0), target().add(0, 0.3, 0), 0, 62));
                // far below, looking up at them hanging in the air
                shot(30, end, 0, t -> look(ground().add(perp().scale(2.2)).add(0, 0.3, 0), target(), 0, 60));
            }
            case KNIFE_RAIN -> {
                final Vec3 P = l.at(Beat.POINT, Vec3.ZERO);
                final Vec3 lockFrom = P.subtract(F.scale(10)).add(0, 9, 0), lockAt = P.add(0, 3, 0);
                icon(0, 8, 4.4, 4.0, 52f);
                // time is held, and so is the camera: the knives hang in a frame where nothing moves
                shot(8, 70, 0, t -> look(lockFrom, lockAt, 0, 60));
                shot(70, 76, 0, t -> look(P.add(R.scale(1.6)).add(0, -0.3, 0), P.add(0, 9, 0), 12, 80));
                shot(76, end, 0, t -> look(P.add(F.scale(0.01)).add(0, 8 + 12 * ease(span(t, 76, end)), 0), P, 0, 62));
            }
            case WORLDS_TRUTH -> {
                final Vec3 c0 = l.c.origin();
                icon(0, 10, 5.0, 4.0, 50f);
                // locked off, low and far: the sky over him cracking like glass
                shot(10, 46, 0, t -> look(c0.add(F.scale(14)).add(0, 1.0, 0), c0.add(0, 14, 0), 0, 70));
                // the camera rises behind him as the Stand lifts its arms
                shot(46, 80, 0, t -> look(caster(1.5f + 5.5f * ease(span(t, 46, 80))).subtract(F.scale(3)), caster(2.0f), 0, 58));
                icon(80, 86, 1.6, 1.4, 38f);                                  // his face, close, before it lands
                // heaven's view: the world below being overwritten
                shot(86, 110, 0, t -> look(c0.add(F.scale(0.01)).add(0, 45, 0), c0, 0, 62));
                shot(110, end, 0, t -> {
                    float u = ease(span(t, 110, end));
                    return look(c0.subtract(F.scale(20 + 30 * u)).add(0, 20 + 20 * u, 0), c0, 0, 62);
                });
            }
            case HEAVEN_ASCENSION -> {
                shot(0, 14, 0, t -> look(caster(0.15f).add(F.scale(1.2)), caster(4.0f), 0, 70));   // from beneath him, up
                shot(14, 40, 0, t -> look(caster(2.0f + 4.0f * ease(span(t, 14, 40))).add(F.scale(5.0)), caster(1.6f), 0, 54));
                icon(40, end, 4.2, 4.0, 50f);
            }
            case REALITY_COLLAPSE -> {
                final Vec3 c0 = l.c.origin();
                final double Rd = Math.max(8, l.c.domainRadius());
                icon(0, 6, 2.6, 2.4, 44f);
                // straight up over him as the lattice spreads
                shot(6, 40, 0, t -> look(c0.add(F.scale(0.01)).add(0, 2 + 28 * ease(span(t, 6, 40)), 0), c0, 0, 66));
                // inside it: a slow circuit over the pulsing grid
                shot(40, 90, 0, t -> look(c0.add(rot(F, 200 + 60 * span(t, 40, 90)).scale(Rd * 0.8)).add(0, 6, 0), caster(1.2f), 0, 62));
                // low and still, looking up into the lattice sky
                shot(90, end, 0, t -> look(c0.add(F.scale(8)).add(0, 0.5, 0), c0.add(0, 12, 0), 0, 70));
            }
            case COLLAPSE_FINISHER -> {
                final Vec3 D = l.at(Beat.DOMAIN, Vec3.ZERO);
                icon(0, 6, 1.8, 1.6, 40f);
                shot(6, 30, 0, t -> look(D.add(F.scale(0.01)).add(0, 60, 0), D, 0, 64));
                shot(30, end, 0, t -> {
                    float u = ease(span(t, 30, end));
                    return look(D.add(rot(F, 40 * u).scale(0.01 + 18 * u)).add(0, 60 - 46 * u, 0), D, 0, 64);
                });
            }
            default -> fallback(l);
        }
        SHOTS.sort((a, b) -> Integer.compare(a.t0, b.t0));
        if (SHOTS.isEmpty()) fallback(l);
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
