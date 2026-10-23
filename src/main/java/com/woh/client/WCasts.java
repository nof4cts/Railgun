package com.woh.client;

import com.woh.W;
import com.woh.common.Anchors;
import com.woh.common.Beat;
import com.woh.common.Ctx;
import com.woh.common.Curve;
import com.woh.common.Script;
import com.woh.common.WMove;
import com.woh.net.WNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Every Woh move in progress on this client. Each one rebuilds the move's timeline from the Cast packet (the same
 * beats the server is running) and fires each beat on the exact rendered frame its tick is crossed, so a hit, its
 * sound, its impact frames and its shockwave all land on one frame. Hit-stop freezes the move's clock for a few
 * frames, and it then runs a little fast until it has caught up with the server again.
 */
public final class WCasts {
    // ---- rows of FxData (see woh.fsh)
    static final int B_SLASH = 0, B_FLASH = 48, B_RING = 56, B_DUST = 72, B_BOLT = 80, B_ORB = 208, B_SHELL = 224,
            B_CHARGE = 228, B_AURA = 232, B_MARK = 240, B_TRENCH = 256, B_SPHERE = 272, ROWS = 320;
    static final int K_SLASH = 0, K_FLASH = 1, K_RING = 2, K_DUST = 3, K_BOLT = 4, K_ORB = 5, K_SHELL = 6, K_CHARGE = 7,
            K_AURA = 8, K_MARK = 9, K_TRENCH = 10, K_SPHERE = 11;
    static final int[] BASE = {B_SLASH, B_FLASH, B_RING, B_DUST, B_BOLT, B_ORB, B_SHELL, B_CHARGE, B_AURA, B_MARK, B_TRENCH, B_SPHERE};
    static final int[] CAP = {48, 8, 16, 8, 128, 16, 4, 4, 8, 16, 16, 1};

    static final String[] SOUNDS = {"charge", "black_flash", "black_flash_big", "dash", "blood_form", "blood_fire", "blood_hit",
            "blood_rain", "dismantle", "cleave", "world_splitter", "crater", "eruption", "domain", "domain_hum", "overdrive",
            "wave", "world_ender", "rumble", "impact", "meteor", "card", "time_stop_voice"};
    private static final Map<String, SoundEvent> SOUND_CACHE = new HashMap<>();

    static final List<Live> LIVE = new ArrayList<>();
    static final List<Inst> INST = new ArrayList<>();
    private static final Map<Integer, Long> AURAS = new HashMap<>();
    private static WNet.Domain domain;
    private static double domainSeen = -1, domainEnded = -1;
    private static float domainSeed;
    private static double lastFrame = -1;

    private WCasts() {}

    static final class Live {
        final WNet.Cast m;
        final WMove move;
        final Ctx c;
        final List<Beat> beats;
        double start;
        double t, lag, last;
        int fired;
        float darkFrom, darkTo;
        double darkT0, darkDur = 1;

        Live(WNet.Cast m, double start) {
            this.m = m;
            this.move = m.moveEnum();
            this.c = new Ctx(move, m.seed(), m.origin(), m.dir(), m.point(), m.target(), m.targets(), m.domainCentre(),
                    m.domainRadius(), m.worldEnder(), m.splitter(), m.dismantle(), m.overdrive(), m.destructionScale());
            this.beats = Script.build(c);
            this.start = start;
            this.last = start;
        }

        float dark() {
            double u = Mth.clamp((t - darkT0) / Math.max(1, darkDur), 0, 1);
            u = u * u * (3 - 2 * u);
            return (float) (darkFrom + (darkTo - darkFrom) * u);
        }

        Vec3 at(int anchor, Vec3 off) {
            return Anchors.at(Minecraft.getInstance().level, c, m.caster(), anchor, off);
        }
    }

    static final class Inst {
        final Live l;
        final Beat b;
        final int kind;
        final double t0;
        final double life;     // ticks
        Vec3 p0, p1, d;
        float seed, a, w, s;   // free parameters

        Inst(Live l, Beat b, int kind, double life) {
            this.l = l;
            this.b = b;
            this.kind = kind;
            this.t0 = b.t();
            this.life = life;
            this.seed = (float) ((l.c.seed() * 31 + b.t() * 977L + b.kind() * 13L + INST.size()) % 1000) / 10f;
        }

        double age() {
            return l.t - t0;
        }

        double sec() {
            return age() / 20.0;
        }
    }

    // ------------------------------------------------------------------ packets
    public static void add(WNet.Cast m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + mc.getFrameTime();
        double start = m.start();
        if (start > now + 2 || start < now - 3) start = now - 0.5;   // clocks disagree: start from arrival
        Live l = new Live(m, start);
        l.t = now - start;
        LIVE.add(l);
        Entity caster = mc.level.getEntity(m.caster());
        if (caster instanceof AbstractClientPlayer p) WAnims.play(p, l.move);
        WStand.surge(m.caster(), l.move);
        if (caster == mc.player && l.move.cutscene) WCam.start(l);
    }

    public static void domain(WNet.Domain m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.until() == 0) {
            if (domain != null && domain.owner() == m.owner()) {
                domainEnded = WFx.time();
            }
            return;
        }
        if (domain == null || domainEnded >= 0 || domain.owner() != m.owner()) {
            domainSeen = WFx.time();
            domainSeed = (float) (Math.random() * 50);
        }
        domainEnded = -1;
        domain = m;
    }

    public static void aura(WNet.Aura m) {
        if (m.until() <= 0) AURAS.remove(m.entity());
        else AURAS.put(m.entity(), m.until());
    }

    public static boolean overdrive(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        Long u = AURAS.get(e.getId());
        return u != null && mc.level != null && u > mc.level.getGameTime();
    }

    public static void clear() {
        LIVE.clear();
        INST.clear();
        AURAS.clear();
        domain = null;
        domainEnded = -1;
    }

    public static boolean any() {
        return !LIVE.isEmpty() || !INST.isEmpty() || !AURAS.isEmpty() || domain != null;
    }

    /** Darkness of the world right now (the quiet before a blow). */
    public static float dark() {
        float d = 0;
        for (Live l : LIVE) d = Math.max(d, l.dark());
        return d;
    }

    // ------------------------------------------------------------------ the frame
    /** Advance every move to this frame and fire the beats crossed since the last one. */
    public static void frame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        double now = mc.level.getGameTime() + partial;
        if (now == lastFrame) return;
        lastFrame = now;
        boolean stop = WFx.stopped();
        Iterator<Live> it = LIVE.iterator();
        List<Live> snapshot = new ArrayList<>(LIVE);
        for (Live l : snapshot) {
            double dt = now - l.last;
            l.last = now;
            if (dt < 0 || dt > 20) dt = 0;
            if (stop) {
                l.lag += dt;
            } else if (l.lag > 0) {
                l.lag = Math.max(0, l.lag - dt * 0.6);
            }
            l.t = Math.max(l.t, now - l.start - l.lag);
            while (l.fired < l.beats.size() && l.beats.get(l.fired).t() <= l.t) {
                Beat b = l.beats.get(l.fired++);
                try {
                    fire(l, b);
                } catch (Throwable e) {
                    W.LOG.error("[woh] beat {} of {} failed", b.kind(), l.move, e);
                }
            }
        }
        while (it.hasNext()) {
            Live l = it.next();
            if (l.fired >= l.beats.size() && l.t > l.move.end + 60) {
                boolean used = false;
                for (Inst in : INST) if (in.l == l) {
                    used = true;
                    break;
                }
                if (!used || l.t > l.move.end + 600) it.remove();
            }
        }
        INST.removeIf(in -> in.age() > in.life || !LIVE.contains(in.l));
    }

    private static void fire(Live l, Beat b) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 p0 = l.at(b.a0(), b.o0());
        double dist = p0.distanceTo(cam);
        float near = (float) Mth.clamp(1.0 - (dist - 8) / 90.0, 0.15, 1.0);
        switch (b.kind()) {
            case Beat.BF -> {
                Inst in = new Inst(l, b, K_FLASH, (0.9 + 0.25 * b.x()) * 20 + 2);
                in.p0 = p0;
                INST.add(in);
                Vec3 from = l.at(Beat.CASTER, new Vec3(0, 1.2, 0));
                float[] dir = WPost.screenDir(from, p0);
                WFx.blackFlash(p0, dir[0], dir[1], b.x(), dist);
                WAnims.freeze(l.m.caster(), Math.min(0.2, 0.06 + 0.03 * b.x()));
            }
            case Beat.RING -> {
                Inst in = new Inst(l, b, K_RING, 100);
                in.p0 = b.a0() == Beat.GROUND ? p0 : Anchors.ground(level, p0);
                INST.add(in);
            }
            case Beat.BLAST -> {
                float r = (float) b.x();
                WFx.shake(Math.min(0.9f, 0.15f + r / 40f) * near);
                if (r >= 12) {
                    WFx.shockwave(p0, Math.min(1.0f, r / 30f) * near, 0.9f);
                    WFx.flash(0.35f * near, 1f, 0.92f, 0.85f, 3f);
                }
            }
            case Beat.TRENCH -> {
                Vec3 p1 = l.at(b.a1(), b.o1());
                Vec3 d = p1.subtract(p0);
                d = new Vec3(d.x, 0, d.z);
                if (d.lengthSqr() < 1e-6) d = l.c.f();
                Inst in = new Inst(l, b, K_TRENCH, b.dur() + 9 * 20);
                in.p0 = Anchors.ground(level, p0);
                in.d = d.normalize();
                in.a = (float) b.x();
                in.w = (float) Math.max(1, b.y());
                in.s = (float) (b.x() / Math.max(1, b.dur()) * 20.0);
                INST.add(in);
                WFx.shake(0.25f * near);
            }
            case Beat.SLASH -> {
                Vec3 p1 = l.at(b.a1(), b.o1());
                Vec3 d = p1.subtract(p0);
                if (d.lengthSqr() < 1e-6) d = l.c.dir();
                d = d.normalize();
                double L = b.x();
                double speed = Math.max(260, L / 0.22);
                Inst in = new Inst(l, b, K_SLASH, (L / speed + 3.4) * 20);
                in.p0 = p0;
                in.d = d;
                in.p1 = Anchors.side(d, b.z());
                in.a = (float) L;
                in.w = (float) b.y();
                in.s = (float) speed;
                INST.add(in);
                if (b.i() >= 2) {
                    WFx.flash(0.6f, 1f, 1f, 1f, 2.5f);
                    WFx.shake(0.6f);
                    WFx.impact(p0.add(d.scale(8)), (float) d.x, (float) d.y, true, false, new float[][]{{0.04f, 1, 1}, {0.05f, 5, 1}, {0.06f, 1, 1}, {0.12f, 3, 0.5f}});
                } else {
                    WFx.shake(0.06f * near);
                }
            }
            case Beat.CLEAVE -> {
                java.util.Random r = new java.util.Random(l.c.seed() + b.t() * 31L);
                double len = 3 + b.x() * 1.6;
                for (int k = 0; k < 3; k++) {
                    Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.8, r.nextGaussian()).normalize();
                    Inst in = new Inst(l, b, K_SLASH, 24);
                    in.p0 = p0.subtract(d.scale(len * 0.5));
                    in.d = d;
                    in.p1 = Anchors.side(d, 0);
                    in.a = (float) len;
                    in.w = 0.1f;
                    in.s = -1;    // marks a Cleave cut
                    INST.add(in);
                }
                WFx.shake(0.25f * near);
                WFx.punch(0.25f * near);
            }
            case Beat.BOLT -> {
                Inst in = new Inst(l, b, K_BOLT, Math.max(1, b.dur()) + 3);
                in.p0 = p0;
                INST.add(in);
            }
            case Beat.DUST -> {
                Inst in = new Inst(l, b, K_DUST, 14 * 20);
                in.p0 = b.a0() == Beat.GROUND ? p0 : Anchors.ground(level, p0);
                INST.add(in);
            }
            case Beat.DARK -> {
                l.darkFrom = l.dark();
                l.darkTo = (float) b.x();
                l.darkT0 = l.t;
                l.darkDur = Math.max(1, b.dur());
            }
            case Beat.SHELL -> {
                Inst in = new Inst(l, b, K_SHELL, Math.max(1, b.dur()) + 12);
                in.p0 = p0;
                INST.add(in);
            }
            case Beat.ORB -> {
                Inst in = new Inst(l, b, K_ORB, Math.max(1, b.dur()) + 1);
                in.p0 = p0;
                INST.add(in);
            }
            case Beat.CHARGE -> INST.add(new Inst(l, b, K_CHARGE, Math.max(1, b.dur()) + 4));
            case Beat.SPHERE -> {
                Inst in = new Inst(l, b, K_SPHERE, Math.max(1, b.dur()));
                in.p0 = p0;
                INST.add(in);
            }
            case Beat.STREAK -> INST.add(new Inst(l, b, K_BOLT, Math.max(1, b.dur()) + 2));
            case Beat.DOMAIN_ON -> {
                WFx.flash(0.8f, 1f, 0.85f, 0.7f, 1.6f);
                WFx.shockwave(p0, 0.8f, 0.8f);
                WFx.shake(0.5f);
            }
            case Beat.HEAL -> {
                WFx.flash(0.5f, 0.85f, 1f, 0.9f, 2.2f);
                WFx.punch(0.25f);
            }
            case Beat.OVERDRIVE -> {
                WFx.flash(0.7f, 1f, 0.1f, 0.12f, 2.0f);
                WFx.shockwave(p0, 0.9f, 1.4f);
                WFx.shake(0.6f);
                WFx.punch(0.6f);
            }
            case Beat.BLACKOUT -> WFx.blackout(b.dur() / 20.0);
            case Beat.SMEAR -> {
                Vec3 p1 = l.at(b.a1(), b.o1());
                float[] dir = WPost.screenDir(p0, p1);
                WFx.smear(dir[0], dir[1], 1f);
            }
            case Beat.DASH -> {
                if (l.m.caster() == (mc.player == null ? -2 : mc.player.getId())) {
                    WFx.fovKick(8);
                    WFx.punch(0.2f);
                }
            }
            case Beat.IMPACT -> impactPreset(b.i(), p0, (float) b.x() * near, l);
            case Beat.SOUND -> sound(p0, b.i(), (float) b.x(), (float) b.y());
            case Beat.DMG -> {
                if (b.x() > 20) WFx.shake(0.5f * near);
            }
            default -> {
            }
        }
    }

    private static void impactPreset(int preset, Vec3 at, float strength, Live l) {
        Vec3 from = l.at(Beat.CASTER, new Vec3(0, 1.2, 0));
        float[] dir = WPost.screenDir(from, at);
        switch (preset) {
            case 1 -> { // Cleave: ink and speed lines
                WFx.accent(1f, 0.74f, 0.26f);
                WFx.impact(at, dir[0], dir[1], true, false, new float[][]{{0.04f, 1, 1}, {0.04f, 2, 1}, {0.06f, 1, 0.8f}});
                WFx.hitStop(0.05);
            }
            case 2 -> { // blood: negative, crimson
                WFx.accent(0.72f, 0.36f, 1f);
                WFx.impact(at, dir[0], dir[1], true, false, new float[][]{{0.04f, 2, 1}, {0.05f, 3, 1}, {0.05f, 2, 1}, {0.1f, 3, 0.5f}});
                WFx.punch(0.5f * strength);
                WFx.shake(0.5f * strength);
                WFx.hitStop(0.07);
            }
            case 3 -> { // a cataclysm: white-out, two-tone, cracks
                WFx.accent(1f, 0.76f, 0.3f);
                WFx.impact(at, dir[0], dir[1], false, true, new float[][]{{0.05f, 5, 1}, {0.05f, 2, 1}, {0.06f, 3, 1}, {0.06f, 6, 1},
                        {0.12f, 3, 0.7f}, {0.2f, 3, 0.3f}});
                WFx.shake(1.0f * strength);
                WFx.punch(0.8f * strength);
                WFx.afterimage(0.8f);
                WFx.shockwave(at, 1.0f, 0.7f);
                WFx.flash(0.6f, 1f, 0.95f, 0.9f, 1.2f);
            }
            case 4 -> { // the meteor: halftone, then white
                WFx.accent(1f, 0.72f, 0.22f);
                WFx.impact(at, 0, -1, true, true, new float[][]{{0.05f, 4, 1}, {0.05f, 5, 1}, {0.05f, 2, 1}, {0.1f, 4, 0.7f}, {0.15f, 3, 0.3f}});
                WFx.shake(1.1f * strength);
                WFx.punch(0.9f * strength);
                WFx.shockwave(at, 1.0f, 0.9f);
                WFx.hitStop(0.12);
            }
            case 5 -> { // the wave: a white-out washing over everything
                WFx.accent(0.7f, 0.34f, 1f);
                WFx.impact(at, 0, 1, false, false, new float[][]{{0.08f, 5, 1}, {0.06f, 2, 0.8f}, {0.25f, 3, 0.5f}});
                WFx.shake(0.9f);
                WFx.flash(0.8f, 1f, 0.9f, 0.9f, 0.8f);
            }
            case 6 -> { // a technique unleashed: one sharp two-tone beat
                WFx.accent(1f, 0.78f, 0.32f);
                WFx.impact(at, 0, 1, false, false, new float[][]{{0.05f, 2, 1}, {0.06f, 3, 1}, {0.1f, 3, 0.4f}});
                WFx.punch(0.4f);
            }
            default -> {
            }
        }
    }

    static void sound(Vec3 at, int index, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || index < 0 || index >= SOUNDS.length) return;
        SoundEvent ev = SOUND_CACHE.computeIfAbsent(SOUNDS[index], n -> SoundEvent.createVariableRangeEvent(W.id(n)));
        mc.level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, volume, pitch, false);
    }

    // ------------------------------------------------------------------ FxData
    /** Everything visible this frame, packed for the shader. */
    public static final class Frame {
        public final float[] data = new float[ROWS * 16];
        public final int[] n = new int[12];
        public float dark;
        public Vec3 domC;
        public float domR, domAge, domStrength, domSeed;
        public float overdrive;

        public boolean any() {
            for (int k : n) if (k > 0) return true;
            return dark > 0.001f || domStrength > 0.001f || overdrive > 0.001f;
        }

        float[] row(int kind) {
            if (n[kind] >= CAP[kind]) return null;
            int r = BASE[kind] + n[kind]++;
            return new float[]{r};
        }

        void put(int kind, Vec3 cam, float... cols) {
            if (n[kind] >= CAP[kind]) return;
            int r = BASE[kind] + n[kind]++;
            int o = r * 16;
            System.arraycopy(cols, 0, data, o, Math.min(16, cols.length));
        }
    }

    static float f(double v) {
        return (float) v;
    }

    public static Frame pack(Vec3 cam, float partial) {
        Frame fr = new Frame();
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return fr;
        for (Inst in : INST) {
            double age = in.age();
            if (age < 0) continue;
            double sec = age / 20.0;
            Beat b = in.b;
            switch (in.kind) {
                case K_FLASH -> {
                    Vec3 p = in.p0.subtract(cam);
                    fr.put(K_FLASH, cam, f(p.x), f(p.y), f(p.z), f(sec), f(b.x()), in.seed, f(b.z()), 0);
                }
                case K_RING -> {
                    Vec3 p = in.p0.subtract(cam);
                    fr.put(K_RING, cam, f(p.x), f(p.y), f(p.z), f(sec), f(b.y()), f(b.z()), f(b.x()), 1);
                }
                case K_DUST -> {
                    Vec3 p = in.p0.subtract(cam);
                    // huge clouds thin out, so the crater under them still reads
                    float thick = (float) Mth.clamp(Math.sqrt(25.0 / Math.max(1.0, b.x())), 0.3, 1.0);
                    fr.put(K_DUST, cam, f(p.x), f(p.y), f(p.z), f(sec), f(b.x()), f(b.y()), thick, 0);
                }
                case K_TRENCH -> {
                    Vec3 p = in.p0.subtract(cam);
                    fr.put(K_TRENCH, cam, f(p.x), f(p.y), f(p.z), f(sec), f(in.d.x), 0, f(in.d.z), in.a, in.w, in.s, 0, 0);
                }
                case K_SLASH -> {
                    Vec3 p = in.p0.subtract(cam);
                    boolean cleave = in.s < 0;
                    float vis = (float) (b.i() >= 2 ? 1.0 : W.slashVisibility);
                    fr.put(K_SLASH, cam, f(p.x), f(p.y), f(p.z), f(sec), f(in.d.x), f(in.d.y), f(in.d.z), in.a,
                            f(in.p1.x), f(in.p1.y), f(in.p1.z), in.w, cleave ? 1 : 0, cleave ? 0 : in.s, in.seed, vis);
                }
                case K_BOLT -> {
                    if (b.kind() == Beat.STREAK) {
                        Vec3 head = in.l.at(Beat.CASTER, new Vec3(0, 0.9, 0));
                        Vec3 tail = head.add(0, 10, 0);
                        float life = (float) Mth.clamp(1.0 - (age - b.dur()) / 3.0, 0, 1);
                        Vec3 h = head.subtract(cam), t = tail.subtract(cam);
                        fr.put(K_BOLT, cam, f(h.x), f(h.y), f(h.z), f(b.x()), f(t.x), f(t.y), f(t.z), 1.5f, 1, life, 0, 0);
                        break;
                    }
                    Vec3 to = in.l.at(b.a1(), b.o1());
                    double dur = Math.max(1, b.dur());
                    double u = Mth.clamp(age / dur, 0, 1);
                    double ut = Mth.clamp(u - 0.18 - 0.1 * (1 - u), 0, 1);
                    Vec3 head = Curve.at(in.p0, to, in.l.c.seed(), b.i(), u);
                    Vec3 tail = Curve.at(in.p0, to, in.l.c.seed(), b.i(), ut);
                    if (head.distanceToSqr(tail) < 1e-4) tail = head.subtract(to.subtract(in.p0).normalize().scale(0.3));
                    float life = u >= 1 ? (float) Mth.clamp(1.0 - (age - dur) / 3.0, 0, 1) : 1f;
                    Vec3 h = head.subtract(cam), t = tail.subtract(cam);
                    fr.put(K_BOLT, cam, f(h.x), f(h.y), f(h.z), f(b.x()), f(t.x), f(t.y), f(t.z), 1.0f, f(b.z()), life, 0, 0);
                }
                case K_ORB -> {
                    Vec3 to = in.l.at(b.a1(), b.o1());
                    Vec3 from = b.a0() == b.a1() && b.o0().equals(b.o1()) ? to : in.p0;
                    double dur = Math.max(1, b.dur());
                    double u = Mth.clamp(age / dur, 0, 1);
                    double e = u * u * (3 - 2 * u);
                    Vec3 p = from.lerp(to, e).subtract(cam);
                    float grow = (float) Mth.clamp(age / 5.0, 0, 1);
                    fr.put(K_ORB, cam, f(p.x), f(p.y), f(p.z), f(b.x() * grow), 1, 0, 0, 0);
                }
                case K_SHELL -> {
                    Vec3 p = in.p0.subtract(cam);
                    double dur = Math.max(1, b.dur());
                    double u = Mth.clamp(age / dur, 0, 1);
                    double R = b.x() * (1 - Math.pow(1 - u, 3));
                    float strength = (float) (1.0 - Mth.clamp((age - dur * 0.5) / (dur * 0.5 + 12), 0, 1));
                    fr.put(K_SHELL, cam, f(p.x), f(p.y), f(p.z), f(Math.max(0.2, R)), f(0.6 + R * 0.08), strength, f(b.z()), 0);
                }
                case K_CHARGE -> {
                    Vec3 p = in.l.at(b.a0(), b.o0()).subtract(cam);
                    double dur = Math.max(1, b.dur());
                    double amt = b.x() * Mth.clamp(age / dur, 0, 1) * (age > dur ? Mth.clamp(1 - (age - dur) / 4.0, 0, 1) : 1);
                    fr.put(K_CHARGE, cam, f(p.x), f(p.y), f(p.z), f(amt));
                }
                case K_SPHERE -> {
                    Vec3 p = in.p0.subtract(cam);
                    double dur = Math.max(1, b.dur());
                    float strength = (float) (Mth.clamp(age / 20.0, 0, 1) * Mth.clamp((dur - age) / 8.0, 0, 1));
                    fr.put(K_SPHERE, cam, f(p.x), f(p.y), f(p.z), f(b.x()), f(sec), strength, 0, 0);
                }
                default -> {
                }
            }
        }
        // Overdrive auras
        long gt = level.getGameTime();
        for (Map.Entry<Integer, Long> en : AURAS.entrySet()) {
            Entity e = level.getEntity(en.getKey());
            if (e == null || en.getValue() <= gt) continue;
            Vec3 base = e.getPosition(partial).subtract(cam);
            float amt = (float) Mth.clamp((en.getValue() - gt) / 40.0, 0, 1);
            fr.put(K_AURA, cam, f(base.x), f(base.y), f(base.z), e.getBbHeight() * 1.2f, amt, 1, 0, 0);
            if (e == mc.player) fr.overdrive = amt;
        }
        // the domain and who it has marked
        if (domain != null) {
            double t = WFx.time();
            float in = (float) Mth.clamp((t - domainSeen) / 1.2, 0, 1);
            float out = domainEnded < 0 ? 1f : (float) Mth.clamp(1 - (t - domainEnded) / 1.0, 0, 1);
            if (out <= 0 || domain.until() < gt - 40) {
                domain = null;
            } else {
                fr.domC = domain.centre();
                fr.domR = domain.radius();
                fr.domAge = (float) (t - domainSeen);
                fr.domStrength = in * out;
                fr.domSeed = domainSeed;
                for (int id : domain.marked()) {
                    Entity e = level.getEntity(id);
                    if (e == null || !e.isAlive()) continue;
                    Vec3 p = e.getPosition(partial).subtract(cam);
                    fr.put(K_MARK, cam, f(p.x), f(p.y), f(p.z), fr.domStrength);
                }
            }
        }
        fr.dark = dark();
        return fr;
    }

    /** The domain this client is showing, if any (for the camera and the HUD). */
    public static WNet.Domain domainNow() {
        return domain;
    }
}
