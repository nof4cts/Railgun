package com.yuji.client;

import com.jjk.mod.client.JjkPrefs;
import com.yuji.Y;
import com.yuji.common.Beat;
import com.yuji.common.YMove;
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
public final class YCam {
    public record Pose(Vec3 pos, float yaw, float pitch, float roll, float fov) {}

    private record Shot(int t0, int t1, int blend, Function<Float, Pose> at) {}

    private static YCasts.Live live;
    private static Marker camera;
    private static CameraType saved;
    private static float lockYaw, lockPitch;
    private static int skipHeld;
    private static Pose pose;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static double titleStart;

    private YCam() {}

    public static boolean running() {
        return live != null;
    }

    public static Pose pose() {
        return live == null ? null : pose;
    }

    public static YMove move() {
        return live == null ? null : live.move;
    }

    public static void start(YCasts.Live l) {
        if (!Y.cutscenes || JjkPrefs.skipCutscenes()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // Immersive Portals renders the world from its own camera; a borrowed camera entity can crash it
        if (net.minecraftforge.fml.ModList.get().isLoaded("immersive_portals") || net.minecraftforge.fml.ModList.get().isLoaded("imm_ptl_core")) return;
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
        titleStart = YFx.time();
        SHOTS.clear();
        try {
            build(l);
        } catch (Throwable t) {
            Y.LOG.error("[yuji] couldn't direct {}", l.move, t);
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
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || !YCasts.LIVE.contains(live)) {
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
            pose = evaluate(t);
        } catch (Throwable e) {
            Y.LOG.error("[yuji] camera failed", e);
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
                Math.sin(t * 0.061 + 2.1) * 0.6).scale(a * Y.motion);
    }

    private static Vec3 rot(Vec3 f, double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return new Vec3(f.x * c - f.z * s, 0, f.x * s + f.z * c);
    }

    private static void shot(int t0, int t1, int blend, Function<Float, Pose> at) {
        SHOTS.add(new Shot(t0, t1, blend, at));
    }

    // ------------------------------------------------------------------ shot lists
    private static void build(YCasts.Live l) {
        Vec3 F = l.c.f(), R = l.c.r();
        int end = l.move.end;
        switch (l.move) {
            case DOMAIN -> {
                shot(0, 48, 0, t -> look(caster(1.5f).add(rot(F, 30 + t * 0.9).scale(3.2 + t * 0.02)).add(0, -0.2, 0), caster(1.5f), 0, 60));
                shot(48, 62, 0, t -> look(caster(1.55f).add(F.scale(1.5 - span(t, 48, 62) * 0.3)), caster(1.55f), 0, 55));
                shot(62, end, 0, t -> {
                    float u = ease(span(t, 62, end));
                    double r = l.c.domainRadius();
                    Vec3 from = caster(1.4f).add(rot(F, 160 + u * 50).scale(4 + u * r * 1.1)).add(0, 1 + u * r * 0.55, 0);
                    return look(from.add(handheld(t, 0.2)), caster(1.2f + u * 4), 0, 70 + u * 8);
                });
            }
            case OVERDRIVE -> {
                shot(0, 40, 0, t -> look(caster(1.3f).add(rot(F, 15).scale(5 - span(t, 0, 40) * 2.6)).add(0, -0.5, 0), caster(1.4f), 0, 62));
                shot(40, end, 0, t -> {
                    float u = 1 - (float) Math.pow(1 - span(t, 40, 52), 3);
                    return look(caster(0.4f).add(rot(F, -20).scale(2.4 + u * 6)).add(handheld(t, 0.3)), caster(1.5f), -4, 75);
                });
            }
            case METEOR -> {
                Vec3 base = l.c.origin();
                shot(0, 28, 0, t -> look(base.add(F.scale(9)).add(R.scale(3)).add(0, 1.5, 0), caster(1f), 0, 70));
                shot(28, 36, 0, t -> look(base.add(F.scale(4)).add(0, 1.0, 0), caster(1f), 8, 80));
                shot(36, 46, 0, t -> {
                    Vec3 g = ground();
                    return look(g.add(R.scale(26)).add(F.scale(-8)).add(0, 4, 0), caster(0.5f).lerp(g, 0.35), 0, 72);
                });
                wide(46, end, 0, 20 * l.c.ds(), 1f);
            }
            case WORLD_SPLITTER -> {
                shot(0, 22, 0, t -> look(caster(1.6f).add(F.scale(-2.6)).add(R.scale(1.1)).add(0, 0.3, 0), caster(1.4f).add(F.scale(20)), 0, 66));
                shot(22, end, 0, t -> {
                    float u = ease(span(t, 22, end));
                    Vec3 from = caster(1f).add(F.scale(-6 - u * 10)).add(R.scale(3)).add(0, 4 + u * 14, 0);
                    return look(from.add(handheld(t, 0.4)), caster(0f).add(F.scale(40 + u * 60)), 0, 72);
                });
            }
            case FINAL_JUDGMENT -> {
                double W = l.c.worldEnder();
                shot(0, 40, 0, t -> look(caster(0.3f).add(rot(F, 140 + t * 0.6).scale(12)).add(0, 0.6, 0), caster(1.6f), 0, 58));
                shot(40, 112, 12, t -> look(caster(1.3f).add(rot(F, 40 + (t - 40) * 1.6).scale(3.2)).add(0, -0.3, 0).add(handheld(t, 0.08)), caster(1.3f), 0, 60));
                shot(112, 120, 0, t -> look(caster(1.6f).add(F.scale(-2.4)).add(R.scale(1.0)), target(), 0, 78));
                hit(120, 136, l, 1);
                shot(136, end, 0, t -> {
                    float u = ease(span(t, 136, end));
                    Vec3 g = ground();
                    Vec3 from = g.add(F.scale(-W * (0.3 + 0.6 * u))).add(R.scale(W * 0.2)).add(0, 12 + W * (0.25 + 0.6 * u), 0);
                    return look(from.add(handheld(t, 0.6)), g, 0, 70);
                });
            }
            case DOMAIN_APOCALYPSE -> {
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
            default -> generic(l);
        }
        SHOTS.sort((a, b) -> Integer.compare(a.t0, b.t0));
        if (SHOTS.isEmpty()) generic(l);
    }

    /** A side-on shot of a blow landing: cut in on the frame of the hit. */
    private static void hit(int t0, int t1, YCasts.Live l, int side) {
        shot(t0 - 2, t1, 0, t -> {
            Vec3 c = caster(1.2f), tg = target();
            Vec3 d = tg.subtract(c);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            if (flat.lengthSqr() < 1e-4) flat = l.c.f();
            flat = flat.normalize();
            Vec3 perp = new Vec3(-flat.z, 0, flat.x).scale(side);
            Vec3 mid = c.lerp(tg, 0.55);
            double dist = 4.5 + d.length() * 0.4;
            float u = span(t, t0, t1);
            Vec3 from = mid.add(perp.scale(dist * (1 - 0.12 * u))).add(flat.scale(-1.2)).add(0, 0.5, 0);
            return look(from.add(handheld(t, 0.15)), mid.add(flat.scale(0.6)), side * 3, 74);
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
    private static void generic(YCasts.Live l) {
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
            hit(h, Math.max(h + 4, next), l, i % 2 == 0 ? 1 : -1);
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
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        double since = YFx.time() - titleStart;
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
            g.fill(18, h - bar + 20, 18 + (int) ((x - 26) * a), h - bar + 21, 0xD9213A | alpha);
            if (!live.move.canon) g.drawString(mc.font, "* original technique (not canon)", 18, h - bar + 24, 0xB0B0B0 | alpha, false);
        }
        String hint = "Hold " + mc.options.keyShift.getTranslatedKeyMessage().getString() + " to skip";
        g.drawString(mc.font, hint, w - mc.font.width(hint) - 10, h - bar / 2 - 4, 0x707070, false);
    }
}
