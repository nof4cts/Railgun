package com.naoya.ps.client;

import static com.naoya.ps.client.fx.ScreenFx.hit;

import com.naoya.ps.Net.Fx;
import com.naoya.ps.client.cine.Director;
import com.naoya.ps.client.fx.Impact;
import com.naoya.ps.client.fx.Light;
import com.naoya.ps.client.fx.Ribbon;
import com.naoya.ps.client.fx.ScreenFx;
import com.naoya.ps.client.fx.Shapes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What Projection Sorcery looks like. Every movement leaves its frames behind: copies of Naoya held at each of the 24
 * positions he planned, flicking out one after another. Whoever fails to keep up is flattened into a single drawing,
 * held inside a stark white panel with the black edge of an animation cel, until the panel cracks and breaks apart.
 */
public final class NFx {
    static final int PAPER = 0xF3EFE4, INK = 0x0B0B0E, PALE = 0xE9E3D0, SKY = 0xBFD8FF, GOLD = 0xF2D27A, WHITE = 0xFFFFFF;

    /** A body held in a frame, as the client knows it. */
    public record Frozen(int id, long start, long until, float tilt) {}

    /** A strike's moment, for stars and lines. */
    private record Mark(int kind, Vec3 a, Vec3 b, float f, long time, long seed) {}

    /** A shard of a broken frame. */
    private static final class Shard {
        Vec3 pos, vel, u, v;
        float size, life, age;
        boolean dark;
    }

    static final Map<Integer, Frozen> FROZEN = new HashMap<>();
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final Random R = new Random();

    /** Impact pulses on the local picture: start time (game ticks + partial), sequence, power. */
    private record Pulse(float start, int[][] seq, Vec3 at, float power) {}

    private static final List<Pulse> PULSES = new ArrayList<>();

    private NFx() {}

    static float now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partial;
    }

    public static boolean frozen(Entity e) {
        Frozen f = FROZEN.get(e.getId());
        return f != null && Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() < f.until;
    }

    // ------------------------------------------------------------------------------------------------ packets

    public static void accept(Fx m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        Entity caster = mc.level.getEntity(m.caster());
        boolean mine = mc.player != null && m.caster() == mc.player.getId();
        MARKS.add(new Mark(m.kind(), m.a(), m.b(), m.f(), now, R.nextLong()));
        switch (m.kind()) {
            case Fx.DASH -> {
                frames(m.caster(), m.a(), m.b(), m.dur());
                anim(caster, "dash");
                Light.ring(m.a().add(0, 1, 0), m.b().subtract(m.a()).normalize(), 0.4F, 2.6F, 0.2F, WHITE, 0.35F);
                if (mine) {
                    PULSES.add(new Pulse(now, null, m.b(), 0.25F + 0.06F * m.f()));
                }
            }
            case Fx.PALM -> {
                anim(caster, "palm");
                Light.hit(m.b(), m.b().subtract(m.a()), WHITE, 1.4F);
                Light.ring(m.b(), m.b().subtract(m.a()).normalize(), 0.2F, 3.0F, 0.25F, PAPER, 0.4F);
                if (mine) {
                    PULSES.add(new Pulse(now, new int[][]{{Impact.WHITEOUT, 1}, {Impact.INK, 1}}, m.b(), 0.6F));
                }
            }
            case Fx.FREEZE -> {
                FROZEN.put(m.other(), new Frozen(m.other(), now, now + m.dur(), (R.nextFloat() - 0.5F) * 9));
                Light.flare(m.b(), 2.2F, WHITE, 0.25F);
            }
            case Fx.UNFREEZE -> FROZEN.remove(m.other());
            case Fx.SHATTER -> {
                FROZEN.remove(m.other());
                shatter(m.b(), m.f(), caster);
                if (mine || (mc.player != null && mc.player.getId() == m.other())) {
                    PULSES.add(new Pulse(now, new int[][]{{Impact.INVERT, 1}, {Impact.INK, 1}, {Impact.LINES, 1}}, m.b(), 0.9F));
                }
            }
            case Fx.BARRAGE -> anim(caster, "barrage");
            case Fx.HIT -> {
                Light.hit(m.a(), m.b(), WHITE, 0.5F + m.f() * 0.06F);
            }
            case Fx.STEP -> {
                frames(m.caster(), m.a(), m.b(), 10);
                Ghosts.add(m.caster(), m.a(), yawOf(caster), 1.4F, 0.75F);
                anim(caster, "step");
            }
            case Fx.BOOM -> {
                frames(m.caster(), m.a(), m.b(), 24);
                anim(caster, "boom");
                Vec3 d = m.b().subtract(m.a()).normalize();
                for (int i = 0; i < 4; i++) {
                    Light.ring(m.a().add(0, 1, 0).add(d.scale(i * 1.4)), d, 0.5F + i * 0.6F, 4.5F + i * 1.5F, 0.3F, WHITE, 0.45F + i * 0.08F);
                }
                Light.spray(m.a().add(0, 0.3, 0), d.scale(-1).add(0, 0.4, 0), 0.9F, 30, 10, PALE, 0.8F, 0.06F);
                if (mine) {
                    PULSES.add(new Pulse(now, new int[][]{{Impact.WHITEOUT, 1}, {Impact.LINES, 1}, {Impact.INK, 1}}, m.b(), 1.0F));
                }
            }
            case Fx.TRAP -> {
                anim(caster, "trap");
                if (mine) {
                    PULSES.add(new Pulse(now, new int[][]{{Impact.INK, 1}, {Impact.INVERT, 1}}, m.a(), 0.7F));
                }
            }
            case Fx.ULT -> {
                anim(caster, "ult");
                if (mine && Prefs.cutscenes) {
                    Director.play(Cine.ult(m, now));
                }
            }
            case Fx.ULT_BLINK -> {
                frames(m.caster(), m.a(), m.b(), 24);
                Vec3 d = m.b().subtract(m.a()).normalize();
                Light.ring(m.b().add(0, 1, 0), d, 0.6F, 6.0F + 2 * m.f(), 0.35F, WHITE, 0.5F);
                Light.ring(m.a().add(0, 1, 0), d, 0.4F, 3.5F + m.f(), 0.25F, SKY, 0.4F);
            }
            case Fx.ULT_HIT -> {
                Vec3 d = m.b();
                Light.hit(m.a(), d, WHITE, 3.0F);
                for (int i = 0; i < 3; i++) {
                    Light.ring(m.a(), d, 0.5F, 9 + i * 4, 0.45F - i * 0.1F, i == 1 ? SKY : WHITE, 0.6F + i * 0.15F);
                }
                Light.spray(m.a(), d, 0.6F, 60, 18, WHITE, 0.9F, 0.08F);
                shatter(m.a(), 2.0F, caster);
                if (mc.player != null && mc.player.position().distanceTo(m.a()) < 40) {
                    PULSES.add(new Pulse(now, Cine.HIT, m.a(), 1.6F));
                }
            }
            default -> {
            }
        }
    }

    private static float yawOf(Entity e) {
        return e == null ? 0 : e.getYRot();
    }

    private static void anim(Entity e, String name) {
        if (e instanceof AbstractClientPlayer p) {
            Anims.play(p, name);
        }
    }

    /** Leave the frames of a movement: copies of him at every planned position, flicking out in order. */
    private static void frames(int who, Vec3 a, Vec3 b, int n) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(who);
        float yaw = yawOf(e);
        Vec3 d = b.subtract(a);
        if (d.horizontalDistanceSqr() > 1.0E-3) {
            yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        }
        int count = Math.max(2, Math.min(24, n));
        for (int i = 0; i < count; i++) {
            double k = i / (double) (count - 1);
            // frames are held, not smeared: each stays a beat, the earliest go first
            float life = 0.25F + 0.5F * (float) k;
            float alpha = 0.32F + 0.4F * (float) k;
            Ghosts.add(who, a.lerp(b, k), yaw, life, alpha);
        }
    }

    /** Break a frame: shards of white paper and black ink flung out from the body. */
    private static void shatter(Vec3 c, float h, Entity by) {
        Vec3 n = c.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()).normalize();
        Vec3 right = n.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(n).normalize();
        for (int i = 0; i < 46; i++) {
            Shard s = new Shard();
            double x = (R.nextDouble() - 0.5) * h * 0.9, y = (R.nextDouble() - 0.5) * h * 1.25;
            s.pos = c.add(right.scale(x)).add(up.scale(y));
            Vec3 out = right.scale(x).add(up.scale(y)).normalize();
            s.vel = out.scale(3 + R.nextDouble() * 6).add(n.scale(-(1 + R.nextDouble() * 3))).add(0, 2 + R.nextDouble() * 3, 0);
            double ang = R.nextDouble() * Math.PI;
            s.u = right.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
            s.v = right.scale(-Math.sin(ang)).add(up.scale(Math.cos(ang)));
            s.size = 0.12F + R.nextFloat() * 0.3F;
            s.life = 0.7F + R.nextFloat() * 0.7F;
            s.dark = R.nextInt(4) == 0;
            SHARDS.add(s);
        }
        Light.flare(c, 3.5F, WHITE, 0.25F);
        Light.ring(c, n, 0.3F, 4.0F, 0.25F, WHITE, 0.35F);
    }

    // ------------------------------------------------------------------------------------------------ ticking

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FROZEN.clear();
            MARKS.clear();
            SHARDS.clear();
            PULSES.clear();
            return;
        }
        long now = mc.level.getGameTime();
        FROZEN.values().removeIf(f -> now > f.until + 2 || mc.level.getEntity(f.id) == null);
        MARKS.removeIf(m -> now - m.time > 40);
        PULSES.removeIf(p -> now - p.start > 30);
        Ghosts.tick();
    }

    // ------------------------------------------------------------------------------------------------ strokes

    public static void ribbons(List<Ribbon> out, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float now = now(partial);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Frozen f : FROZEN.values()) {
            Entity e = mc.level.getEntity(f.id);
            if (e == null || now > f.until) {
                continue;
            }
            panel(out, e.getBoundingBox(), cam, now - f.start, f.until - now, f.tilt);
        }
        for (Mark m : MARKS) {
            float age = now - m.time;
            switch (m.kind) {
                case Fx.DASH, Fx.BOOM, Fx.ULT_BLINK -> speedLines(out, m, age, m.kind == Fx.DASH ? 1.0F : 1.8F);
                case Fx.TRAP -> filmRing(out, m.a, m.f, age);
                case Fx.HIT -> {
                    if (age < 4) {
                        star(out, m.a, age, 0.9F + m.f * 0.05F, m.seed);
                    }
                }
                case Fx.PALM -> star(out, m.b, age, 1.8F, m.seed);
                case Fx.SHATTER -> star(out, m.b, age, 2.4F, m.seed);
                case Fx.ULT_HIT -> star(out, m.a, age, 6.0F, m.seed);
                default -> {
                }
            }
        }
        // shards in flight
        float dt = mc.isPaused() ? 0 : mc.getDeltaFrameTime() / 20.0F;
        Iterator<Shard> it = SHARDS.iterator();
        while (it.hasNext()) {
            Shard s = it.next();
            s.age += dt;
            if (s.age > s.life) {
                it.remove();
                continue;
            }
            s.vel = s.vel.scale(Math.exp(-1.4 * dt)).add(0, -14 * dt, 0);
            s.pos = s.pos.add(s.vel.scale(dt));
            float a = 1 - s.age / s.life;
            Vec3[] sp = {s.pos.subtract(s.v.scale(s.size)), s.pos.add(s.v.scale(s.size))};
            Vec3[] sides = {s.u, s.u};
            out.add(new Ribbon(sp, sides, new float[]{s.size * 1.6F, 0.02F}, new float[]{a, a}, s.dark ? INK : PAPER,
                    s.dark ? Ribbon.Style.INK : Ribbon.Style.TINT));
        }
    }

    /** The frame a body is stuck in: paper fill, a hard black edge, a white inner line and film sprockets down the sides. */
    private static void panel(List<Ribbon> out, AABB box, Vec3 cam, float age, float left, float tilt) {
        Vec3 c = box.getCenter();
        Vec3 n = c.subtract(cam);
        n = new Vec3(n.x, 0, n.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(n.x, 0, n.z).normalize();
        Vec3 right = n.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        double t = Math.toRadians(tilt);
        Vec3 r2 = right.scale(Math.cos(t)).add(up.scale(Math.sin(t)));
        Vec3 u2 = up.scale(Math.cos(t)).subtract(right.scale(Math.sin(t)));
        double h = box.getYsize() * 0.68 + 0.25;
        double w = Math.max(box.getXsize() * 0.9 + 0.25, h * 0.72);
        // the panel snaps in a little large, and shudders as it nears its end
        float pop = 1 + 0.35F * (float) Math.exp(-age * 0.9);
        float shiver = left < 12 ? (float) Math.sin(age * 3.1) * 0.04F * (1 - left / 12) : 0;
        w *= pop;
        h *= pop;
        Vec3 o = c.subtract(n.scale(0.05)).add(r2.scale(shiver));
        float a = Mth.clamp(age / 2.0F, 0, 1) * Mth.clamp(left / 3.0F, 0, 1);
        // fill: behind the flattened body
        Vec3 back = o.add(n.scale(0.12));
        out.add(new Ribbon(new Vec3[]{back.subtract(u2.scale(h)), back.add(u2.scale(h))}, new Vec3[]{r2, r2},
                new float[]{(float) (w * 2), (float) (w * 2)}, new float[]{0.3F * a, 0.3F * a}, PAPER, Ribbon.Style.TINT));
        // black edge and white inner line
        edge(out, o, r2, u2, w, h, 0.11F, INK, a, Ribbon.Style.INK);
        edge(out, o, r2, u2, w - 0.1, h - 0.1, 0.05F, WHITE, a, Ribbon.Style.INK);
        // sprocket holes down both sides, like a strip of film
        for (int s = -1; s <= 1; s += 2) {
            Vec3 col = o.add(r2.scale(s * (w + 0.18)));
            for (int i = -3; i <= 3; i++) {
                Vec3 hole = col.add(u2.scale(i * h / 3.4));
                out.add(new Ribbon(new Vec3[]{hole.subtract(u2.scale(0.07)), hole.add(u2.scale(0.07))}, new Vec3[]{r2, r2},
                        new float[]{0.13F, 0.13F}, new float[]{a, a}, INK, Ribbon.Style.INK));
            }
            out.add(new Ribbon(new Vec3[]{col.subtract(u2.scale(h + 0.1)), col.add(u2.scale(h + 0.1))}, new Vec3[]{r2, r2},
                    new float[]{0.3F, 0.3F}, new float[]{0.65F * a, 0.65F * a}, PAPER, Ribbon.Style.TINT));
        }
    }

    private static void edge(List<Ribbon> out, Vec3 o, Vec3 r, Vec3 u, double w, double h, float width, int rgb, float a, Ribbon.Style st) {
        Vec3[] corners = {o.add(r.scale(-w)).add(u.scale(-h)), o.add(r.scale(w)).add(u.scale(-h)), o.add(r.scale(w)).add(u.scale(h)),
                o.add(r.scale(-w)).add(u.scale(h))};
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = corners[i], p1 = corners[(i + 1) % 4];
            Vec3 along = p1.subtract(p0).normalize();
            Vec3 side = i % 2 == 0 ? u : r;
            Vec3 e0 = p0.subtract(along.scale(width * 0.5)), e1 = p1.add(along.scale(width * 0.5));
            out.add(new Ribbon(new Vec3[]{e0, e1}, new Vec3[]{side, side}, new float[]{width, width}, new float[]{a, a}, rgb, st));
        }
    }

    /** Wind torn open along a path: thin white lines running with it, and a black one or two for the ink. */
    private static void speedLines(List<Ribbon> out, Mark m, float age, float scale) {
        if (age > 8) {
            return;
        }
        float a = 1 - age / 8;
        Vec3 d = m.b.subtract(m.a);
        double len = d.length();
        if (len < 0.5) {
            return;
        }
        Vec3 dn = d.normalize();
        Vec3 u = dn.cross(Math.abs(dn.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = dn.cross(u);
        Random r = new Random(m.seed);
        for (int i = 0; i < 14 * scale; i++) {
            double ang = r.nextDouble() * Math.PI * 2, rad = (0.5 + r.nextDouble() * 1.4) * scale;
            Vec3 off = u.scale(Math.cos(ang) * rad).add(v.scale(Math.sin(ang) * rad)).add(0, 1.0, 0);
            double k0 = r.nextDouble() * 0.6, k1 = Math.min(1, k0 + 0.25 + r.nextDouble() * 0.4);
            Vec3 p0 = m.a.add(d.scale(k0)).add(off), p1 = m.a.add(d.scale(k1)).add(off);
            boolean ink = r.nextInt(5) == 0;
            Shapes.streak(out, p0, p1, ink ? 0.05F : 0.08F, a * (ink ? 0.8F : 0.9F), ink ? INK : WHITE, ink ? Ribbon.Style.INK : Ribbon.Style.GLOW);
        }
    }

    /** Frame Trap: a strip of film snaps out round him in a ring. */
    private static void filmRing(List<Ribbon> out, Vec3 c, float radius, float age) {
        if (age > 22) {
            return;
        }
        float k = Mth.clamp(age / 5.0F, 0, 1);
        float a = Mth.clamp((22 - age) / 8.0F, 0, 1);
        double r = radius * (0.3 + 0.7 * (1 - Math.pow(1 - k, 3)));
        Vec3 at = c.add(0, 1.0, 0);
        int n = 48;
        Vec3[] spine = new Vec3[n + 1];
        Vec3[] sides = new Vec3[n + 1];
        float[] w = new float[n + 1];
        float[] al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double ang = i * Math.PI * 2 / n + age * 0.05;
            spine[i] = at.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            sides[i] = new Vec3(0, 1, 0);
            w[i] = 1.4F;
            al[i] = a * 0.85F;
        }
        out.add(new Ribbon(spine, sides, w, al, INK, Ribbon.Style.INK));
        float[] w2 = w.clone();
        for (int i = 0; i <= n; i++) {
            w2[i] = 1.0F;
        }
        Vec3[] inner = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            Vec3 rad = spine[i].subtract(at).normalize();
            inner[i] = spine[i].subtract(rad.scale(0.02));
        }
        out.add(new Ribbon(inner, sides, w2, al, PAPER, Ribbon.Style.TINT));
        for (int i = 0; i < n; i += 2) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 hole = spine[i].add(0, s * 0.56, 0);
                Vec3 tan = spine[i + 1].subtract(spine[i]).normalize();
                Shapes.streak(out, hole.subtract(tan.scale(0.07)), hole.add(tan.scale(0.07)), 0.1F, a, PAPER, Ribbon.Style.INK);
            }
        }
    }

    /** The anime impact star: spikes thrown out from the point of contact. */
    private static void star(List<Ribbon> out, Vec3 p, float age, float size, long seed) {
        float life = 4 + size;
        float k = age / life;
        if (k < 0 || k > 1) {
            return;
        }
        Random r = new Random(seed);
        float fade = (1 - k) * (1 - k);
        int spikes = 8 + (int) (size * 3);
        for (int i = 0; i < spikes; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.8, r.nextGaussian()).normalize();
            double len = size * (0.6 + r.nextDouble() * 0.7);
            double in = len * (0.15 + 0.7 * Math.sqrt(k)), outR = len * (0.45 + 0.75 * Math.sqrt(k));
            float w = (float) (0.05 + 0.06 * size) * (1 - k);
            boolean ink = i % 4 == 0;
            Shapes.streak(out, p.add(d.scale(in)), p.add(d.scale(outR)), w * (ink ? 1.4F : 1.0F), fade, ink ? INK : WHITE,
                    ink ? Ribbon.Style.INK : Ribbon.Style.GLOW);
        }
        Shapes.ring(out, p, new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize(), size * (0.3 + 1.1 * Math.sqrt(k)),
                (float) (0.04 + 0.06 * size) * (1 - k), 0.8F * fade, WHITE, Ribbon.Style.GLOW);
    }

    // ------------------------------------------------------------------------------------------------ the picture

    public static void screen(ScreenFx fx, float partial) {
        float now = now(partial);
        Minecraft mc = Minecraft.getInstance();
        for (Pulse p : PULSES) {
            if (p.seq != null && Prefs.impactFrames) {
                fx.impact(p.seq, now - p.start, p.at, GOLD);
            }
            fx.shake(p.power * hit(now, p.start, 6));
            fx.punch(0.5F * p.power * hit(now, p.start, 3));
            fx.aberration(0.6F * p.power * hit(now, p.start, 4));
        }
        // stuck in a frame yourself: the world goes flat and grey
        if (mc.player != null && frozen(mc.player)) {
            fx.desaturate(0.85F);
            fx.vignette(0x000000, 0.5F);
            fx.grain(0.6F);
        }
        Cine.screen(fx, partial);
    }
}
