package com.firestar.sage.client;

import com.firestar.sage.SageMod;
import com.firestar.sage.common.SBeats;
import com.firestar.sage.common.SMove;
import com.firestar.sage.client.fx.Ink;
import com.firestar.sage.client.fx.Look;
import com.firestar.sage.server.Rites;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the Celestial Sage puts into the world on this client: the Mantle (nine orbs on their wheel, the halo),
 * every rite's light and shadow, its sounds and its effect on the picture. Painted each frame with {@link Ink}.
 */
public final class SageFx {
    static final Vec3 UP = new Vec3(0, 1, 0);
    private SageFx() {}

    // ------------------------------------------------------------------------------------------------ helpers

    static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0, 1);
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** 0 before a, rising to 1 by b, holding, falling to 0 between c and d. */
    static float window(float t, float a, float b, float c, float d) {
        return Math.min(span(t, a, b), 1 - span(t, c, d));
    }

    /** A hit that flashes in at {@code at} and dies away over {@code len} ticks. */
    static float hit(float t, float at, float len) {
        if (t < at) {
            return 0;
        }
        return (float) Math.exp(-(t - at) / Math.max(0.5F, len) * 2.2F);
    }

    static void play(Vec3 at, SoundEvent s, float vol, float pitch) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l != null) {
            l.playLocalSound(at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch, false);
        }
    }

    /** The orb's home on the wheel behind the sage's back (same as the server's). */
    static Vec3 wheel(Entity p, int i, float partial, float spread) {
        Minecraft mc = Minecraft.getInstance();
        double gt = mc.level == null ? 0 : mc.level.getGameTime() + partial;
        double a = Math.PI * 2 * i / SBeats.ORBS_COUNT + gt * 0.03;
        float yaw = p instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : p.getYRot();
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 r = new Vec3(-f.z, 0, f.x);
        double rad = 1.15 * spread;
        return p.getPosition(partial).add(0, 1.35, 0).subtract(f.scale(0.55)).add(r.scale(Math.cos(a) * rad)).add(0, Math.sin(a) * rad, 0);
    }

    static Vec3 hand(Entity p, float partial) {
        float yaw = p instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : p.getYRot();
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 r = new Vec3(-f.z, 0, f.x);
        return p.getPosition(partial).add(0, 1.15, 0).add(f.scale(0.45)).add(r.scale(0.38));
    }

    static Vec3 ground(Vec3 at) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null) {
            return at;
        }
        BlockHitResult h = l.clip(new ClipContext(at.add(0, 3, 0), at.add(0, -40, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                Minecraft.getInstance().player));
        return h.getType() == HitResult.Type.BLOCK ? h.getLocation() : at;
    }

    // ------------------------------------------------------------------------------------------------ beats

    /** A rite began on this client. */
    static void begin(SageCasts.Play c) {
        Entity e = c.caster();
        if (e instanceof net.minecraft.client.player.AbstractClientPlayer p) {
            SageAnims.play(p, c.rite(), c.msg.size());
        }
    }

    static void formChanged(Entity e, int form) {
        if (form == 0) {
            play(e.position().add(0, 1, 0), SageMod.MANTLE_RELEASE.get(), 1.0F, 1.0F);
        }
    }

    /** Sounds and one-off particles at their beats (the server plays the big ones for everyone). */
    static void beat(SageCasts.Play c, int t) {
        Entity e = c.caster();
        if (e == null) {
            return;
        }
        Vec3 at = e.position().add(0, 1, 0);
        ClientLevel l = Minecraft.getInstance().level;
        switch (c.rite()) {
            case STAFF -> {
                if (t == 1) {
                    play(at, SageMod.STAFF_SWING.get(), 0.8F, 1.1F + c.msg.size() * 0.08F);
                }
            }
            case MIRROR -> {
                if (t == SBeats.MIRROR_VANISH && l != null) {
                    for (int i = 0; i < 24; i++) {
                        l.addParticle(ParticleTypes.END_ROD, at.x + (Math.random() - 0.5) * 0.8, at.y + Math.random() * 1.6 - 0.6,
                                at.z + (Math.random() - 0.5) * 0.8, 0, 0.02, 0);
                    }
                }
            }
            case STARFALL -> {
                if (t == SBeats.STAR_IMPACT || t == SBeats.STAR_SECOND) {
                    Vec3 p = Rites.starPoint(c.msg.point(), c.flat(), t == SBeats.STAR_IMPACT ? 0 : 1);
                    if (l != null) {
                        for (int i = 0; i < 60; i++) {
                            double a = Math.random() * Math.PI * 2, sp = 0.4 + Math.random() * 0.9;
                            l.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y + 1, p.z, Math.cos(a) * sp, 0.2 + Math.random() * 0.6, Math.sin(a) * sp);
                            l.addParticle(ParticleTypes.LAVA, p.x, p.y + 1, p.z, 0, 0, 0);
                        }
                    }
                }
            }
            case THORNWOOD -> {
                if (l != null) {
                    List<Vec3> spots = Rites.thornSpots(c.msg.point(), c.flat(), c.msg.seed());
                    for (int i = 0; i < spots.size(); i++) {
                        if (t == Rites.thornTime(i, spots.size())) {
                            Vec3 s = ground(spots.get(i));
                            for (int k = 0; k < 6; k++) {
                                l.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, s.x, s.y + 0.2, s.z, (Math.random() - 0.5) * 0.05, 0.05, (Math.random() - 0.5) * 0.05);
                            }
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Each tick: the client's own flight of the null orbs (to draw them where the server flies them). */
    static void tick(SageCasts.Play c, int t) {
        if (c.rite() != SMove.ORBS) {
            return;
        }
        Entity e = c.caster();
        ClientLevel l = Minecraft.getInstance().level;
        if (e == null || l == null) {
            return;
        }
        int k = t - SBeats.ORBS_FIRST;
        if (k >= 0 && k % SBeats.ORBS_EVERY == 0 && k / SBeats.ORBS_EVERY < SBeats.ORBS_COUNT && c.orbs.size() == k / SBeats.ORBS_EVERY) {
            int i = k / SBeats.ORBS_EVERY;
            Vec3 from = wheel(e, i, 0, 1);
            LivingEntity tg = c.target();
            Vec3 aim = tg != null ? tg.getBoundingBox().getCenter() : c.msg.point();
            c.orbs.add(from);
            c.orbsO.add(from);
            c.orbVel.add(aim.subtract(from).normalize().scale(SBeats.ORBS_SPEED));
            c.orbLanded.add(-1);
        }
        for (int i = 0; i < c.orbs.size(); i++) {
            Vec3 pos = c.orbs.get(i);
            c.orbsO.set(i, pos);
            if (c.orbLanded.get(i) >= 0) {
                continue;
            }
            Vec3 vel = c.orbVel.get(i);
            LivingEntity tg = c.target();
            if (tg != null && tg.isAlive()) {
                vel = vel.lerp(tg.getBoundingBox().getCenter().subtract(pos).normalize().scale(SBeats.ORBS_SPEED), 0.18).normalize().scale(SBeats.ORBS_SPEED);
                c.orbVel.set(i, vel);
            }
            Vec3 next = pos.add(vel);
            BlockHitResult bh = l.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            boolean done = bh.getType() == HitResult.Type.BLOCK;
            if (done) {
                next = bh.getLocation();
            }
            if (!done && tg != null && tg.getBoundingBox().inflate(0.8).clip(pos, next).isPresent()) {
                done = true;
                next = tg.getBoundingBox().getCenter();
            }
            if (pos.distanceTo(c.msg.origin()) > SBeats.ORBS_RANGE + 10) {
                done = true;
            }
            c.orbs.set(i, next);
            if (done) {
                c.orbLanded.set(i, t);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the picture

    /** What the rites do to the screen (the sage's own cutscenes add their bars). */
    static void screen(Look look, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (SageCasts.Play c : SageCasts.all()) {
            float t = c.time(partial);
            double d = mc.player.position().distanceTo(c.msg.point());
            boolean near = c.mine || d < 120;
            if (!near) {
                continue;
            }
            switch (c.rite()) {
                case MANTLE -> {
                    impactFrames(look, t, SBeats.MANTLE_IGNITE, 1);
                    look.flash(0.85F * hit(t, SBeats.MANTLE_IGNITE + 2, 6), 0xFFF4D0);
                    look.shake(0.7F * hit(t, SBeats.MANTLE_IGNITE, 10));
                    look.gold(0.5F * hit(t, SBeats.MANTLE_IGNITE, 20));
                }
                case PULL -> look.shake(0.5F * hit(t, SBeats.PULL_BURST, 5));
                case MIRROR -> {
                    look.flash(0.35F * hit(t, SBeats.MIRROR_STRIKE, 3), 0xFFFFFF);
                    look.shake(0.35F * hit(t, SBeats.MIRROR_STRIKE, 4));
                }
                case STARFALL -> {
                    float k = (float) Math.max(0, 1 - d / 140);
                    impactFrames(look, t, SBeats.STAR_IMPACT, k);
                    look.flash(k * 1.0F * hit(t, SBeats.STAR_IMPACT + 2, 8), 0xFFE9B0);
                    look.flash(k * 0.8F * hit(t, SBeats.STAR_SECOND, 7), 0xFFE9B0);
                    look.shake(k * 1.6F * hit(t, SBeats.STAR_IMPACT, 30));
                    look.shake(k * 1.1F * hit(t, SBeats.STAR_SECOND, 22));
                    look.shake(k * 0.25F * span(t, SBeats.STAR_APPEAR, SBeats.STAR_IMPACT));
                }
                case THORNWOOD -> look.shake(0.45F * window(t, SBeats.THORN_ERUPT, SBeats.THORN_ERUPT + 2, SBeats.THORN_ERUPT + SBeats.THORN_SPREAD,
                        SBeats.THORN_ERUPT + SBeats.THORN_SPREAD + 8));
                case COLOSSUS -> {
                    impactFrames(look, t, SBeats.COL_CUT, 1);
                    look.flash(0.9F * hit(t, SBeats.COL_CUT + 2, 6), 0xFFF1C8);
                    look.shake(1.5F * hit(t, SBeats.COL_CUT, 26));
                    look.shake(0.3F * span(t, SBeats.COL_RISE, SBeats.COL_FORMED) * (1 - span(t, SBeats.COL_FORMED, SBeats.COL_FORMED + 10)));
                }
                case ECLIPSE -> {
                    float cover = window(t, SBeats.ECL_RISE, SBeats.ECL_TOTAL, SBeats.ECL_RING_END, SBeats.ECL_DAWN + 20);
                    look.vignette(0.8F * cover);
                    impactFrames(look, t, SBeats.ECL_RING, 1);
                    look.flash(1.0F * hit(t, SBeats.ECL_RING + 2, 10), 0xFFF6DA);
                    look.shake(1.2F * hit(t, SBeats.ECL_RING, 30));
                    look.gold(0.35F * window(t, SBeats.ECL_RING, SBeats.ECL_RING + 4, SBeats.ECL_RING_END, SBeats.ECL_DAWN));
                }
                default -> {
                }
            }
        }
    }

    /** The two frames of an impact: the picture blacked out for a tick, then burnt white for a tick. */
    static void impactFrames(Look look, float t, int at, float k) {
        if (k < 0.2F) {
            return;
        }
        if (t >= at && t < at + 1) {
            look.flash(0.92F * k, 0x000000);
        } else if (t >= at + 1 && t < at + 2) {
            look.flash(1.0F * k, 0xFFFFFF);
        }
    }

    /** How dark the world is made right now (the eclipse, the gathering of the heavens): 0..1. */
    static float veil(float partial) {
        Minecraft mc = Minecraft.getInstance();
        float v = 0;
        if (mc.player == null) {
            return 0;
        }
        for (SageCasts.Play c : SageCasts.all()) {
            float t = c.time(partial);
            double d = mc.player.position().distanceTo(c.msg.point());
            if (!c.mine && d > 120) {
                continue;
            }
            v = Math.max(v, switch (c.rite()) {
                case MANTLE -> 0.5F * window(t, 4, 30, SBeats.MANTLE_IGNITE - 1, SBeats.MANTLE_IGNITE + 3);
                case STARFALL -> 0.6F * window(t, SBeats.STAR_CALL, SBeats.STAR_APPEAR, SBeats.STAR_IMPACT, SBeats.STAR_IMPACT + 30);
                case COLOSSUS -> 0.45F * window(t, SBeats.COL_RISE, SBeats.COL_FORMED, SBeats.COL_CUT + 10, SBeats.COL_CUT + 40);
                case ECLIPSE -> 0.88F * window(t, SBeats.ECL_RISE, SBeats.ECL_TOTAL, SBeats.ECL_RING, SBeats.ECL_DAWN + 20)
                        + 0.0F;
                default -> 0F;
            });
        }
        return Mth.clamp(v, 0, 0.92F);
    }

    // ------------------------------------------------------------------------------------------------ the world

    /** Every frame, after the world: paint every sage and every rite. */
    static void draw(Ink ink, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float veil = veil(partial);
        if (veil > 0.01F) {
            ink.veil(Ink.alpha(0xFF05030A, veil));
        }
        for (Player p : mc.level.players()) {
            float m = SageForms.mantle(p, partial);
            if (m > 0.01F && !p.isInvisible()) {
                boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson() && !com.firestar.sage.client.cam.Director.active();
                mantle(ink, p, m, partial, firstPerson);
            }
        }
        for (SageCasts.Play c : SageCasts.all()) {
            float t = c.time(partial);
            switch (c.rite()) {
                case MANTLE -> drawMantle(ink, c, t, partial);
                case STAFF -> drawStaff(ink, c, t, partial);
                case ORBS -> drawOrbs(ink, c, t, partial);
                case PULL -> drawPull(ink, c, t, partial);
                case MIRROR -> drawMirror(ink, c, t, partial);
                case STARFALL -> drawStarfall(ink, c, t, partial);
                case THORNWOOD -> drawThornwood(ink, c, t, partial);
                case COLOSSUS -> drawColossus(ink, c, t, partial);
                case ECLIPSE -> drawEclipse(ink, c, t, partial);
                default -> {
                }
            }
        }
    }

    /** The Mantle on a sage: the nine orbs on their slow wheel, the halo over the head. */
    static void mantle(Ink ink, Entity p, float m, float partial, boolean firstPerson) {
        SageCasts.Play c = SageCasts.of(p);
        boolean firing = c != null && c.rite() == SMove.ORBS;
        int fired = 0;
        if (firing) {
            float t = c.time(partial);
            fired = Mth.clamp((int) ((t - SBeats.ORBS_FIRST) / SBeats.ORBS_EVERY) + 1, 0, SBeats.ORBS_COUNT);
            // they come home one by one after the volley
            int back = Mth.clamp((int) ((t - 50) / 2), 0, SBeats.ORBS_COUNT);
            fired = Math.max(0, fired - back);
        }
        if (!firstPerson) {
            for (int i = 0; i < SBeats.ORBS_COUNT; i++) {
                if (firing && i < fired) {
                    continue;
                }
                Vec3 o = wheel(p, i, partial, 0.6F + 0.4F * m);
                double r = 0.21 * m;
                ink.sphere(false, o, r, Ink.alpha(Ink.VOID, 0.97F), Ink.alpha(Ink.GOLD, 0.95F), 3.0);
                ink.glow(o, r * 2.6, Ink.alpha(Ink.GOLD, 0.16F * m));
            }
        }
        // the halo: a thin gold ring tilted behind the head, broken in four
        Vec3 head = p.getPosition(partial).add(0, p.getBbHeight() + 0.28, 0);
        float yaw = p instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : p.getYRot();
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 n = UP.scale(0.86).add(f.scale(-0.5)).normalize();
        Vec3 hc = head.subtract(f.scale(firstPerson ? 0.0 : 0.22));
        if (!firstPerson) {
            for (int k = 0; k < 4; k++) {
                double a0 = k * Math.PI / 2 + 0.12, a1 = (k + 1) * Math.PI / 2 - 0.12;
                ink.arc(hc, n, 0.42, 0.035 * m, a0, a1, Ink.alpha(Ink.GOLD, 0.95F * m), 10, true);
            }
            ink.ring(hc, n, 0.42, 0.11 * m, Ink.alpha(Ink.GOLD, 0.12F * m), 40);
        }
    }

    static void drawMantle(Ink ink, SageCasts.Play c, float t, float partial) {
        Entity e = c.caster();
        if (e == null) {
            return;
        }
        Vec3 feet = e.getPosition(partial);
        // the orbs gather: nine motes of night spiral in from far around him
        float g = span(t, SBeats.MANTLE_GATHER, SBeats.MANTLE_HALO);
        if (g > 0 && g < 1) {
            for (int i = 0; i < SBeats.ORBS_COUNT; i++) {
                double a = Math.PI * 2 * i / SBeats.ORBS_COUNT + t * 0.06;
                double r = 14 * (1 - smooth(g)) + 1.2;
                Vec3 from = feet.add(Math.cos(a) * r, 1.3 + (1 - g) * 6 * Math.sin(i * 1.7), Math.sin(a) * r);
                Vec3 to = wheel(e, i, partial, 1);
                Vec3 o = from.lerp(to, smooth(g) * smooth(g));
                ink.sphere(false, o, 0.12 + 0.1 * g, Ink.alpha(Ink.VOID, 0.95F), Ink.alpha(Ink.GOLD, 0.9F), 3);
                Vec3 tail = o.add(o.subtract(feet.add(0, 1.3, 0)).normalize().scale(1.4 * (1 - g)));
                ink.beam(o, tail, 0.05, Ink.alpha(Ink.GOLD, 0.5F * (1 - g)));
            }
        }
        // the ground ring: a gold seal drawn around him as he gathers
        float seal = window(t, 6, 40, SBeats.MANTLE_IGNITE, SBeats.MANTLE_IGNITE + 20);
        if (seal > 0) {
            Vec3 gc = feet.add(0, 0.05, 0);
            ink.ring(gc, UP, 3.2, 0.06, Ink.alpha(Ink.GOLD, 0.8F * seal), 64);
            ink.ring(gc, UP, 2.4, 0.03, Ink.alpha(Ink.PALE, 0.6F * seal), 48);
            for (int k = 0; k < 9; k++) {
                double a = Math.PI * 2 * k / 9 + t * 0.02;
                Vec3 p = gc.add(Math.cos(a) * 2.8, 0, Math.sin(a) * 2.8);
                ink.ring(p, UP, 0.28, 0.025, Ink.alpha(Ink.GOLD, 0.7F * seal), 18);
            }
        }
        // ignition: the halo closes and a pulse throws the light out
        float ign = hit(t, SBeats.MANTLE_IGNITE, 14);
        if (t >= SBeats.MANTLE_IGNITE && ign > 0.01F) {
            float k = span(t, SBeats.MANTLE_IGNITE, SBeats.MANTLE_IGNITE + 18);
            Vec3 cc = feet.add(0, 1.0, 0);
            ink.ring(feet.add(0, 0.1, 0), UP, 1 + 14 * k, 0.5 * (1 - k) + 0.05, Ink.alpha(Ink.PALE, 0.9F * (1 - k)), 80);
            ink.glow(cc, 4 + 6 * k, Ink.alpha(Ink.PALE, 0.6F * ign));
            ink.flare(cc.add(0, 0.8, 0), 9 * ign, 0.08, Ink.alpha(Ink.WHITE, ign), t * 0.01);
            ink.beam(feet, feet.add(0, 60 * ign, 0), 0.6 * ign, Ink.alpha(Ink.GOLD, 0.8F * ign));
        }
    }

    static void drawStaff(Ink ink, SageCasts.Play c, float t, float partial) {
        Entity e = c.caster();
        if (e == null || t > 10) {
            return;
        }
        int step = (int) c.msg.size();
        Vec3 eye = e.getEyePosition(partial);
        Vec3 look = e.getViewVector(partial);
        Vec3 side = look.cross(UP).normalize();
        float k = smooth(span(t, 1, SBeats.STAFF_HIT + 1));
        float fade = 1 - span(t, SBeats.STAFF_HIT + 1, 10);
        int n = 16;
        Vec3[] pts = new Vec3[n];
        double[] w = new double[n];
        int[] col = new int[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1) * k;
            Vec3 p;
            if (step == 0) {
                p = eye.subtract(0, 0.3, 0).add(look.scale(0.6 + 3.8 * u));
            } else if (step == 1) {
                double a = Math.PI * (-0.6 + 1.2 * u);
                p = eye.subtract(0, 0.35, 0).add(look.scale(2.6 * Math.cos(a))).add(side.scale(2.6 * Math.sin(a)));
            } else {
                double a = Math.PI * (0.55 - 0.8 * u);
                p = eye.add(look.scale(2.8 * Math.cos(a))).add(UP.scale(2.8 * Math.sin(a) - 0.4));
            }
            pts[i] = p;
            w[i] = 0.16 * Math.sin(Math.PI * i / (n - 1)) + 0.02;
            col[i] = Ink.alpha(i > n * 0.7 ? Ink.PALE : Ink.GOLD, fade * (0.3F + 0.7F * i / n));
        }
        ink.ribbon(pts, w, col);
    }

    static void drawOrbs(Ink ink, SageCasts.Play c, float t, float partial) {
        for (int i = 0; i < c.orbs.size(); i++) {
            int landed = c.orbLanded.get(i);
            Vec3 p = c.orbsO.get(i).lerp(c.orbs.get(i), partial);
            if (landed < 0) {
                ink.sphere(false, p, 0.24, Ink.alpha(Ink.VOID, 0.98F), Ink.alpha(Ink.GOLD, 0.95F), 3);
                ink.glow(p, 0.8, Ink.alpha(Ink.GOLD, 0.25F));
                Vec3 back = p.subtract(c.orbVel.get(i).normalize().scale(2.6));
                ink.ribbon(new Vec3[]{back, p.lerp(back, 0.5), p}, new double[]{0.0, 0.08, 0.16},
                        new int[]{Ink.alpha(Ink.GOLD, 0), Ink.alpha(Ink.GOLD, 0.4F), Ink.alpha(Ink.PALE, 0.8F)});
            } else {
                // erasure: a sphere of nothing swells and is gone, a thin gold ring where it was
                float k = span(t, landed, landed + 12);
                if (k < 1) {
                    double r = SBeats.ORBS_ERASE * (0.4 + 0.9 * Math.sin(Math.PI * Math.min(1, k * 1.3)));
                    ink.sphere(false, p, r, Ink.alpha(Ink.VOID, 0.92F * (1 - k * k)), Ink.alpha(Ink.GOLD, 0.6F * (1 - k)), 4);
                    ink.ring(p, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(p), SBeats.ORBS_ERASE * (0.8 + 1.8 * k),
                            0.12 * (1 - k), Ink.alpha(Ink.PALE, 0.9F * (1 - k)), 40);
                }
            }
        }
    }

    static void drawPull(Ink ink, SageCasts.Play c, float t, float partial) {
        Entity e = c.caster();
        LivingEntity tg = c.target();
        if (e == null || tg == null) {
            return;
        }
        Vec3 a = hand(e, partial);
        Vec3 b = tg.getPosition(partial).add(0, tg.getBbHeight() * 0.55, 0);
        float on = window(t, 2, SBeats.PULL_BIND, SBeats.PULL_ARRIVE, SBeats.PULL_BURST);
        if (on > 0.01F) {
            int n = 24;
            Vec3[] pts = new Vec3[n];
            double[] w = new double[n];
            int[] col = new int[n];
            Vec3 d = b.subtract(a);
            Vec3 s = d.cross(UP).lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : d.cross(UP).normalize();
            float reach = smooth(span(t, 2, SBeats.PULL_BIND));
            for (int i = 0; i < n; i++) {
                float u = i / (float) (n - 1) * reach;
                double wave = Math.sin(u * Math.PI * 4 - t * 1.2) * Math.sin(u * Math.PI) * 0.35 * (1 - span(t, SBeats.PULL_BIND, SBeats.PULL_ARRIVE));
                pts[i] = a.add(d.scale(u)).add(s.scale(wave)).add(UP.scale(wave * 0.4));
                w[i] = 0.06;
                col[i] = Ink.alpha(Ink.GOLD, on * 0.95F);
            }
            ink.ribbon(pts, w, col);
            if (t >= SBeats.PULL_BIND) {
                ink.ring(b, d, 0.7 + 0.1 * Math.sin(t), 0.05, Ink.alpha(Ink.PALE, on), 24);
                ink.ring(b, UP, 0.9, 0.04, Ink.alpha(Ink.GOLD, on), 24);
            }
        }
        float burst = hit(t, SBeats.PULL_BURST, 8);
        if (t >= SBeats.PULL_BURST && burst > 0.01F) {
            Vec3 at = e.getPosition(partial).add(c.flat().scale(2.6)).add(0, 1, 0);
            ink.glow(at, 3.5, Ink.alpha(Ink.PALE, 0.8F * burst));
            ink.flare(at, 5 * burst, 0.06, Ink.alpha(Ink.WHITE, burst), 0.3);
            ink.ring(at, c.flat(), 1 + 4 * (1 - burst), 0.2 * burst, Ink.alpha(Ink.GOLD, burst), 40);
        }
    }

    static void drawMirror(Ink ink, SageCasts.Play c, float t, float partial) {
        Vec3 from = c.msg.origin().subtract(0, 1.62, 0);
        float fade = window(t, SBeats.MIRROR_VANISH, SBeats.MIRROR_VANISH + 1, SBeats.MIRROR_STRIKE, SBeats.MIRROR_STRIKE + 8);
        if (fade > 0.01F) {
            // the outline he leaves behind: a column of gold motes and a thin silhouette
            for (int i = 0; i < 9; i++) {
                double a = i * 0.7 + t * 0.2;
                Vec3 p = from.add(Math.cos(a) * 0.35, 0.2 + i * 0.2, Math.sin(a) * 0.35);
                ink.glow(p, 0.14, Ink.alpha(Ink.GOLD, 0.8F * fade));
            }
            ink.beam(from, from.add(0, 2.0, 0), 0.3, Ink.alpha(Ink.GOLD, 0.25F * fade));
        }
        LivingEntity tg = c.target();
        Vec3 dest = tg != null ? tg.getPosition(partial) : c.msg.point();
        float echo = window(t, SBeats.MIRROR_ECHO - 2, SBeats.MIRROR_ECHO, SBeats.MIRROR_STRIKE, SBeats.MIRROR_STRIKE + 2);
        if (echo > 0.01F) {
            ink.beam(dest.add(0, 0.1, 0), dest.add(0, 2.2, 0), 0.5, Ink.alpha(Ink.PALE, 0.5F * echo));
            ink.ring(dest.add(0, 0.05, 0), UP, 1.0, 0.05, Ink.alpha(Ink.GOLD, echo), 32);
        }
        float s = hit(t, SBeats.MIRROR_STRIKE, 6);
        if (t >= SBeats.MIRROR_STRIKE && s > 0.01F) {
            Vec3 at = dest.add(0, 1.0, 0);
            ink.flare(at, 4 * s, 0.05, Ink.alpha(Ink.WHITE, s), 0.8);
            ink.glow(at, 2.2, Ink.alpha(Ink.GOLD, 0.6F * s));
            ink.beam(from.add(0, 1, 0), at, 0.12 * s, Ink.alpha(Ink.PALE, 0.7F * s));
        }
    }

    static void drawStarfall(Ink ink, SageCasts.Play c, float t, float partial) {
        Vec3 flat = c.flat();
        // the call: a pillar of gold from his raised hand into the sky
        Entity e = c.caster();
        float call = window(t, SBeats.STAR_CALL - 4, SBeats.STAR_CALL, SBeats.STAR_APPEAR, SBeats.STAR_APPEAR + 20);
        if (e != null && call > 0.01F) {
            Vec3 h = e.getPosition(partial).add(0, 2.2, 0);
            ink.beam(h, h.add(0, 160, 0), 0.25 * call, Ink.alpha(Ink.GOLD, 0.9F * call));
            ink.glow(h, 1.4, Ink.alpha(Ink.PALE, call));
        }
        for (int which = 0; which < 2; which++) {
            int appear = which == 0 ? SBeats.STAR_APPEAR : SBeats.STAR_APPEAR + 22;
            int impact = which == 0 ? SBeats.STAR_IMPACT : SBeats.STAR_SECOND;
            float R = which == 0 ? 7.0F : 4.5F;
            Vec3 from = Rites.starFrom(c.msg.point(), flat, which);
            Vec3 to = Rites.starPoint(c.msg.point(), flat, which).add(0, R * 0.6, 0);
            if (t >= appear && t < impact + 1) {
                float k = span(t, appear, impact);
                float fall = k * k;
                Vec3 p = from.lerp(to, fall);
                Vec3 dir = to.subtract(from).normalize();
                // the star: a burning ball with a corona and a long torn tail
                ink.sphere(true, p, R, Ink.alpha(Ink.PALE, 0.95F), Ink.alpha(Ink.EMBER, 0.8F), 1.6);
                ink.glow(p, R * 2.6, Ink.alpha(Ink.GOLD, 0.45F));
                ink.glow(p, R * 5.0, Ink.alpha(Ink.EMBER, 0.18F));
                for (int s = 0; s < 6; s++) {
                    double a = s * 1.047 + t * 0.05;
                    Vec3 off = Ink.basis(dir)[0].scale(Math.cos(a) * R * 0.6).add(Ink.basis(dir)[1].scale(Math.sin(a) * R * 0.6));
                    Vec3 t0 = p.add(off), t1 = p.subtract(dir.scale(R * (8 + 6 * fall))).add(off.scale(1.8));
                    ink.ribbon(new Vec3[]{t0, t0.lerp(t1, 0.5), t1}, new double[]{R * 0.5, R * 0.35, 0.05},
                            new int[]{Ink.alpha(Ink.PALE, 0.8F), Ink.alpha(Ink.EMBER, 0.5F), Ink.alpha(Ink.EMBER, 0)});
                }
            }
            if (t >= impact && t < impact + 90) {
                Vec3 g = Rites.starPoint(c.msg.point(), flat, which);
                float k = span(t, impact, impact + 40);
                double big = (which == 0 ? SBeats.STAR_CRATER : 8) * 2.6;
                blast(ink, g, big, t - impact, c.msg.seed() + which * 977L);
            }
        }
    }

    /**
     * A great impact, {@code age} ticks old: a white-hot core that blinks out, a churning fireball of billows that
     * swells, rolls and climbs into a column, a shock ring racing over the ground, burning debris thrown out on long
     * arcs, and the dark smoke that is left.
     */
    static void blast(Ink ink, Vec3 g, double big, float age, long seed) {
        if (age < 0 || age > 90) {
            return;
        }
        float k = age / 90.0F;
        // the core: blinding for a few ticks, then gone
        float core = (float) Math.exp(-age / 3.0);
        if (core > 0.02F) {
            ink.sphere(true, g.add(0, big * 0.2, 0), big * (0.35 + 0.5 * (1 - core)), Ink.alpha(Ink.WHITE, core), Ink.alpha(Ink.PALE, 0.8F * core), 1.2);
            ink.glow(g.add(0, big * 0.3, 0), big * 3.0, Ink.alpha(Ink.PALE, 0.7F * core));
        }
        // the shock ring over the ground, fast, and a second, slower one of dust
        float sk = Mth.clamp(age / 14.0F, 0, 1);
        if (sk < 1) {
            ink.ring(g.add(0, 0.4, 0), UP, big * (0.5 + 4.5 * Math.sqrt(sk)), big * 0.05 + 0.4, Ink.alpha(Ink.WHITE, 0.95F * (1 - sk)), 128);
            ink.ring(g.add(0, 0.6, 0), UP, big * (0.4 + 3.6 * Math.sqrt(sk)), big * 0.16, Ink.alpha(Ink.GOLD, 0.5F * (1 - sk)), 128);
        }
        float dk = Mth.clamp(age / 50.0F, 0, 1);
        ink.ring(g.add(0, 1.2, 0), UP, big * (0.6 + 3.0 * Math.sqrt(dk)), big * 0.5 * (1 - dk) + 0.2, Ink.alpha(Ink.DUSK, 0.45F * (1 - dk)), 96);
        // the fireball: billows on a rolling, rising, swelling body
        java.util.Random r = new java.util.Random(seed);
        int n = 46;
        float grow = (float) (1 - Math.exp(-age / 7.0));
        for (int i = 0; i < n; i++) {
            double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(r.nextDouble() * 1.6 - 0.6);
            double speed = 0.6 + r.nextDouble() * 0.6;
            double rad = big * (0.4 + 1.1 * grow * speed);
            // the column: a share of the billows climbs, the rest roll outward and settle
            boolean climb = i % 3 == 0;
            double lift = climb ? big * (0.5 + 4.2 * Math.pow(k, 0.7) * speed) : big * 0.6 * grow;
            double roll = age * 0.03 * (r.nextBoolean() ? 1 : -1);
            Vec3 dir = new Vec3(Math.cos(th + roll) * Math.sin(ph), Math.cos(ph), Math.sin(th + roll) * Math.sin(ph));
            Vec3 p = g.add(dir.x * rad * (climb ? 0.35 : 1), Math.max(0, dir.y) * rad * 0.6 + lift, dir.z * rad * (climb ? 0.35 : 1));
            double size = big * (0.45 + 0.35 * r.nextDouble()) * (0.5 + grow) * (1 + 0.6 * k);
            float heat = (float) Math.exp(-age / (10.0 + 14 * r.nextDouble()));
            float life = 1 - Mth.clamp((age - 30 - r.nextFloat() * 30) / 30.0F, 0, 1);
            if (life <= 0.01F) {
                continue;
            }
            int hot = heat > 0.6F ? Ink.WHITE : heat > 0.3F ? Ink.PALE : heat > 0.12F ? Ink.GOLD : Ink.EMBER;
            ink.glow(p, size * 1.6, Ink.alpha(hot, (0.25F + 0.55F * heat) * life));
            ink.glow(p, size * 0.8, Ink.alpha(Ink.PALE, 0.5F * heat * life));
            // smoke behind the fire as it cools
            float smoke = (1 - heat) * life * Mth.clamp((age - 6) / 12.0F, 0, 1);
            if (smoke > 0.02F) {
                ink.sphere(false, p.add(0, size * 0.4, 0), size * 1.15, Ink.alpha(Ink.DUSK, 0.55F * smoke), Ink.alpha(Ink.VOID, 0.15F * smoke), 0.7);
            }
        }
        // burning debris on long arcs
        for (int i = 0; i < 28; i++) {
            double th = r.nextDouble() * Math.PI * 2;
            double v = big * (0.25 + 0.35 * r.nextDouble()), up = big * (0.35 + 0.4 * r.nextDouble());
            double tt = age / 20.0;
            Vec3 p = g.add(Math.cos(th) * v * tt * 4, up * tt * 4 - 9.8 * tt * tt * 1.4, Math.sin(th) * v * tt * 4);
            Vec3 pv = g.add(Math.cos(th) * v * (tt - 0.08) * 4, up * (tt - 0.08) * 4 - 9.8 * (tt - 0.08) * (tt - 0.08) * 1.4,
                    Math.sin(th) * v * (tt - 0.08) * 4);
            if (p.y < g.y - 1 || age < 1) {
                continue;
            }
            float f = 1 - k;
            ink.ribbon(new Vec3[]{pv, p.lerp(pv, 0.4), p}, new double[]{0.05, 0.25, 0.45}, new int[]{Ink.alpha(Ink.EMBER, 0), Ink.alpha(Ink.GOLD, 0.6F * f),
                    Ink.alpha(Ink.PALE, 0.95F * f)});
        }
    }

    static void drawThornwood(Ink ink, SageCasts.Play c, float t, float partial) {
        List<Vec3> spots = Rites.thornSpots(c.msg.point(), c.flat(), c.msg.seed());
        float fade = 1 - span(t, SBeats.THORN_ERUPT + SBeats.THORN_SPREAD + SBeats.THORN_FADE - 20, c.rite().length + 30);
        // the bell's line: a gold crack runs ahead along the ground first
        float crack = window(t, SBeats.THORN_PLANT, SBeats.THORN_ERUPT, SBeats.THORN_ERUPT + SBeats.THORN_SPREAD, SBeats.THORN_ERUPT + SBeats.THORN_SPREAD + 10);
        if (crack > 0.01F) {
            Vec3 a = ground(c.msg.point()).add(0, 0.06, 0);
            Vec3 b = a.add(c.flat().scale(SBeats.THORN_LENGTH * smooth(span(t, SBeats.THORN_PLANT, SBeats.THORN_ERUPT + SBeats.THORN_SPREAD))));
            ink.beam(a, b, 0.25, Ink.alpha(Ink.GOLD, crack));
        }
        for (int i = 0; i < spots.size(); i++) {
            int at = Rites.thornTime(i, spots.size());
            if (t < at) {
                continue;
            }
            Vec3 base = ground(spots.get(i));
            float grow = smooth(span(t, at, at + 6));
            java.util.Random r = new java.util.Random(c.msg.seed() * 31 + i);
            double h = (3.5 + r.nextDouble() * 4.5) * grow;
            double lean = (r.nextDouble() - 0.5) * 0.5;
            Vec3 lv = c.flat().scale(lean * 0.6).add(c.right().scale((r.nextDouble() - 0.5) * 0.5));
            int seg = 6;
            Vec3[] trunk = new Vec3[seg + 1];
            double[] tw = new double[seg + 1];
            int[] tc = new int[seg + 1];
            for (int k = 0; k <= seg; k++) {
                double u = k / (double) seg;
                trunk[k] = base.add(lv.scale(u * h * 0.4)).add(0, u * h, 0).add(Math.sin(u * 5 + i) * 0.2, 0, Math.cos(u * 4 + i) * 0.2);
                tw[k] = (0.55 * (1 - u) + 0.08) * grow;
                tc[k] = Ink.alpha(Ink.mix(Ink.GOLD, Ink.PALE, (float) u), fade * 0.9F);
            }
            // the bark is dark: a shadow trunk under the gold light
            for (int k = 0; k < seg; k++) {
                Vec3 ax = trunk[k + 1].subtract(trunk[k]);
                Vec3 mid = trunk[k].add(ax.scale(0.5));
                Vec3[] bb = Ink.basis(ax);
                ink.box(false, mid, bb[0].scale(tw[k] * 0.9), ax.scale(0.5), bb[1].scale(tw[k] * 0.9), Ink.alpha(Ink.DUSK, 0.95F * fade),
                        Ink.alpha(Ink.VOID, 0.95F * fade));
            }
            ink.ribbon(trunk, java.util.Arrays.stream(tw).map(x -> x * 0.45).toArray(), tc);
            // thorned branches
            for (int b = 0; b < 5; b++) {
                double bu = 0.35 + b * 0.13;
                Vec3 from = trunk[(int) Math.min(seg, Math.round(bu * seg))];
                double ba = r.nextDouble() * Math.PI * 2;
                Vec3 dir = new Vec3(Math.cos(ba), 0.55 + r.nextDouble() * 0.5, Math.sin(ba)).normalize();
                double bl = (1.2 + r.nextDouble() * 1.8) * grow;
                Vec3 to = from.add(dir.scale(bl));
                ink.ribbon(new Vec3[]{from, from.lerp(to, 0.5), to}, new double[]{0.12 * grow, 0.07 * grow, 0.0},
                        new int[]{Ink.alpha(Ink.GOLD, fade), Ink.alpha(Ink.GOLD, 0.8F * fade), Ink.alpha(Ink.PALE, 0.6F * fade)});
                ink.glow(to, 0.18 * grow, Ink.alpha(Ink.PALE, 0.8F * fade));
            }
            // the burst where it broke through
            float b = hit(t, at, 6);
            if (b > 0.02F) {
                ink.ring(base.add(0, 0.1, 0), UP, 0.6 + 2.2 * (1 - b), 0.15 * b, Ink.alpha(Ink.PALE, b), 24);
            }
        }
    }

    /** The colossus: a giant of gold light behind the sage, built of glowing slabs over a dark body. */
    static void drawColossus(Ink ink, SageCasts.Play c, float t, float partial) {
        Entity e = c.caster();
        Vec3 feet = c.msg.point();
        Vec3 f = c.flat(), r = c.right();
        float rise = smooth(span(t, SBeats.COL_RISE, SBeats.COL_FORMED));
        float fade = 1 - span(t, SBeats.COL_CUT + 24, c.rite().length + 20);
        if (rise <= 0.01F || fade <= 0.01F) {
            return;
        }
        double S = 2.4; // one unit of the giant's body = 2.4 blocks; it stands ~30 tall
        Vec3 base = feet.subtract(f.scale(6)).add(0, -12 * (1 - rise), 0);
        float raise = smooth(span(t, SBeats.COL_FORMED, SBeats.COL_RAISE));
        float swing = smooth(span(t, SBeats.COL_RAISE + 14, SBeats.COL_CUT));
        int lightCol = Ink.alpha(Ink.GOLD, 0.55F * fade * rise), core = Ink.alpha(Ink.DUSK, 0.35F * fade * rise);
        // legs, hips, chest, head
        part(ink, base.add(r.scale(1.6 * S)).add(0, 3 * S, 0), r, f, 0.8 * S, 3 * S, 0.8 * S, lightCol, core);
        part(ink, base.add(r.scale(-1.6 * S)).add(0, 3 * S, 0), r, f, 0.8 * S, 3 * S, 0.8 * S, lightCol, core);
        part(ink, base.add(0, 6.6 * S, 0), r, f, 2.6 * S, 0.8 * S, 1.2 * S, lightCol, core);
        part(ink, base.add(0, 9.4 * S, 0), r, f, 3.0 * S, 2.2 * S, 1.6 * S, lightCol, core);
        part(ink, base.add(0, 12.6 * S, 0), r, f, 1.0 * S, 1.1 * S, 1.0 * S, lightCol, core);
        // the face: two slits of white
        Vec3 face = base.add(0, 12.8 * S, 0).add(f.scale(1.02 * S));
        ink.beam(face.add(r.scale(0.55 * S)), face.add(r.scale(0.15 * S)), 0.18 * S, Ink.alpha(Ink.WHITE, fade * rise));
        ink.beam(face.add(r.scale(-0.55 * S)), face.add(r.scale(-0.15 * S)), 0.18 * S, Ink.alpha(Ink.WHITE, fade * rise));
        // the wheel behind its shoulders: a great ring with twelve spokes, turning slowly
        Vec3 hub = base.add(0, 11.4 * S, 0).subtract(f.scale(1.4 * S));
        double turn = t * 0.012;
        ink.ring(hub, f, 6.2 * S, 0.1 * S, Ink.alpha(Ink.GOLD, 0.85F * fade * rise), 96);
        ink.ring(hub, f, 5.4 * S, 0.04 * S, Ink.alpha(Ink.PALE, 0.7F * fade * rise), 96);
        for (int k = 0; k < 12; k++) {
            double a = turn + Math.PI * 2 * k / 12;
            Vec3 d = r.scale(Math.cos(a)).add(UP.scale(Math.sin(a)));
            ink.beam(hub.add(d.scale(1.6 * S)), hub.add(d.scale(5.4 * S)), 0.05 * S, Ink.alpha(Ink.PALE, 0.55F * fade * rise));
            ink.glow(hub.add(d.scale(6.2 * S)), 0.5 * S, Ink.alpha(Ink.WHITE, 0.8F * fade * rise));
        }
        // light rising off the giant like heat: motes climbing from its body into the sky
        java.util.Random mr = new java.util.Random(c.msg.seed() ^ 0x5A5A);
        for (int k = 0; k < 70; k++) {
            double ox = (mr.nextDouble() - 0.5) * 7 * S, oz = (mr.nextDouble() - 0.5) * 3 * S;
            double speed = 0.15 + mr.nextDouble() * 0.25, phase = mr.nextDouble() * 16 * S;
            double y = (phase + t * speed) % (16 * S);
            float a = (float) (1 - y / (16 * S));
            ink.glow(base.add(r.scale(ox)).add(f.scale(oz)).add(0, y, 0), 0.25 * S * (0.5 + a), Ink.alpha(Ink.PALE, 0.7F * a * fade * rise));
        }
        // the crown of light
        ink.arc(base.add(0, 14.2 * S, 0).subtract(f.scale(0.6 * S)), UP.scale(0.8).add(f.scale(-0.6)).normalize(), 2.4 * S, 0.12 * S, 0, Math.PI * 2,
                Ink.alpha(Ink.GOLD, 0.8F * fade * rise), 48, true);
        // arms: both raise the great blade overhead, then bring it down along his line of sight
        Vec3 shoulderL = base.add(0, 10.6 * S, 0).add(r.scale(-3.4 * S)), shoulderR = base.add(0, 10.6 * S, 0).add(r.scale(3.4 * S));
        double armAng = Math.PI * (0.05 + 0.85 * raise - 1.15 * swing);
        Vec3 grip = base.add(0, 10.6 * S, 0).add(f.scale(Math.cos(armAng) * 5 * S)).add(UP.scale(Math.sin(armAng) * 5 * S));
        limb(ink, shoulderL, grip, 0.7 * S, lightCol, core);
        limb(ink, shoulderR, grip, 0.7 * S, lightCol, core);
        // the blade: a long tapering edge of white-gold light
        Vec3 bladeDir = grip.subtract(base.add(0, 10.6 * S, 0)).normalize();
        Vec3 tip = grip.add(bladeDir.scale(14 * S));
        ink.ribbon(new Vec3[]{grip, grip.lerp(tip, 0.5), tip}, new double[]{0.9 * S, 0.6 * S, 0.02},
                new int[]{Ink.alpha(Ink.PALE, 0.95F * fade * rise), Ink.alpha(Ink.WHITE, 0.95F * fade * rise), Ink.alpha(Ink.PALE, 0.5F * fade * rise)});
        // the cut: a white line racing along the land, the trench glowing behind it
        float cut = span(t, SBeats.COL_CUT, SBeats.COL_CUT + 8);
        if (t >= SBeats.COL_CUT) {
            float glow = 1 - span(t, SBeats.COL_CUT + 10, SBeats.COL_CUT + 60);
            Vec3 a = ground(feet.add(f.scale(3)));
            Vec3 b = a.add(f.scale(SBeats.COL_LENGTH * cut));
            ink.beam(a.add(0, 0.3, 0), b.add(0, 0.3, 0), 3.5 * glow, Ink.alpha(Ink.PALE, 0.9F * glow));
            ink.beam(a.add(0, 0.3, 0), b.add(0, 0.3, 0), 9 * glow, Ink.alpha(Ink.GOLD, 0.35F * glow));
            // where the edge leaves the land, it bursts
            if (t >= SBeats.COL_CUT + 8) {
                blast(ink, a.add(f.scale(SBeats.COL_LENGTH)), 16, t - SBeats.COL_CUT - 8, c.msg.seed());
            }
            float wall = hit(t, SBeats.COL_CUT, 14);
            for (int k = 0; k < 12; k++) {
                Vec3 p = a.add(f.scale(SBeats.COL_LENGTH * cut * k / 12.0));
                ink.beam(p, p.add(0, 18 * wall * (1 - k / 14.0), 0), 2.2 * wall, Ink.alpha(Ink.PALE, 0.6F * wall));
            }
        }
    }

    private static void part(Ink ink, Vec3 c, Vec3 r, Vec3 f, double hx, double hy, double hz, int light, int core) {
        ink.box(false, c, r.scale(hx * 0.92), UP.scale(hy * 0.92), f.scale(hz * 0.92), core, core);
        ink.box(true, c, r.scale(hx), UP.scale(hy), f.scale(hz), Ink.alpha(light, 0.35F), Ink.alpha(light, 0.2F));
        // glowing edges along the vertical corners
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                Vec3 e0 = c.add(r.scale(hx * sx)).add(f.scale(hz * sz)).add(UP.scale(-hy)), e1 = e0.add(UP.scale(2 * hy));
                ink.beam(e0, e1, Math.max(0.12, hx * 0.08), light);
            }
        }
    }

    private static void limb(Ink ink, Vec3 a, Vec3 b, double w, int light, int core) {
        Vec3 ax = b.subtract(a);
        Vec3[] bb = Ink.basis(ax);
        ink.box(false, a.add(ax.scale(0.5)), bb[0].scale(w * 0.9), ax.scale(0.5), bb[1].scale(w * 0.9), core, core);
        ink.beam(a, b, w * 0.9, light);
    }

    static void drawEclipse(Ink ink, SageCasts.Play c, float t, float partial) {
        Entity e = c.caster();
        Vec3 o = c.msg.point();
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float cover = window(t, SBeats.ECL_RISE, SBeats.ECL_TOTAL, SBeats.ECL_RING_END, SBeats.ECL_DAWN + 20);
        if (cover > 0.01F) {
            // the black sun: a disc of nothing high over him, its corona streaming gold, always facing the eye
            Vec3 sun = o.add(c.flat().scale(30)).add(0, 95, 0);
            Vec3 toCam = cam.subtract(sun).normalize();
            float total = smooth(span(t, SBeats.ECL_COVER, SBeats.ECL_TOTAL));
            double R = 26;
            double rs = R * (0.2 + 0.8 * total);
            ink.corona(sun, rs, rs * 1.25, Ink.alpha(Ink.WHITE, 0.9F * cover));
            ink.corona(sun, rs, rs * (1.9 + 0.5 * total), Ink.alpha(Ink.PALE, 0.5F * cover));
            ink.corona(sun, rs, rs * (3.4 + 0.8 * total), Ink.alpha(Ink.GOLD, 0.28F * cover));
            for (int k = 0; k < 24; k++) {
                double a = Math.PI * 2 * k / 24 + t * 0.004;
                Vec3[] bb = Ink.basis(toCam);
                Vec3 d = bb[0].scale(Math.cos(a)).add(bb[1].scale(Math.sin(a)));
                double len = R * (1.6 + 1.2 * Ink.noise(k * 1.7, t * 0.03, 0)) * (0.6 + 0.4 * total);
                ink.ribbon(new Vec3[]{sun.add(d.scale(rs * 0.98)), sun.add(d.scale(rs + len * 0.5)), sun.add(d.scale(rs + len))},
                        new double[]{R * 0.08, R * 0.05, 0}, new int[]{Ink.alpha(Ink.PALE, 0.8F * cover), Ink.alpha(Ink.GOLD, 0.45F * cover), 0});
            }
            ink.disc(false, sun.add(toCam.scale(0.5)), toCam, R * (0.2 + 0.8 * total), Ink.alpha(Ink.VOID, 0.99F * cover), Ink.alpha(Ink.VOID, 0.99F * cover));
            ink.ring(sun.add(toCam.scale(0.6)), toCam, R * (0.2 + 0.8 * total), R * 0.03, Ink.alpha(Ink.WHITE, 0.95F * cover), 96);
        }
        // the sage: a silhouette ringed in gold, hanging under it
        if (e != null && cover > 0.01F) {
            Vec3 h = e.getPosition(partial).add(0, 1, 0);
            ink.ring(h, UP, 2.2, 0.05, Ink.alpha(Ink.GOLD, cover), 48);
            ink.ring(h, c.flat(), 2.6, 0.04, Ink.alpha(Ink.PALE, 0.8F * cover), 48);
        }
        // the ring of light that seals everything in reach
        if (t >= SBeats.ECL_RING - 2 && t < SBeats.ECL_DAWN + 10) {
            float k = span(t, SBeats.ECL_RING, SBeats.ECL_RING_END);
            float fade = 1 - span(t, SBeats.ECL_RING_END, SBeats.ECL_DAWN + 10);
            double R = SBeats.ECL_RADIUS * (1 - Math.pow(1 - k, 1.8));
            Vec3 g = ground(o).add(0, 0.2, 0);
            ink.ring(g, UP, R, 1.6 * fade + 0.2, Ink.alpha(Ink.WHITE, 0.95F * fade), 160);
            ink.ring(g, UP, R, 5 * fade, Ink.alpha(Ink.GOLD, 0.4F * fade), 160);
            // the wall of light rising off the ring
            int n = 96;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3 p0 = g.add(Math.cos(a0) * R, 0, Math.sin(a0) * R), p1 = g.add(Math.cos(a1) * R, 0, Math.sin(a1) * R);
                double hgt = 30 * fade * (0.7 + 0.3 * Ink.noise(i * 0.4, t * 0.1, 2));
                ink.quad(true, p0, Ink.alpha(Ink.PALE, 0.7F * fade), p1, Ink.alpha(Ink.PALE, 0.7F * fade), p1.add(0, hgt, 0), 0, p0.add(0, hgt, 0), 0);
            }
            // seal lines etched across the ground behind the ring
            for (int ring = 1; ring <= 7; ring++) {
                double rr = ring * 9.0;
                if (rr < R) {
                    ink.ring(g.add(0, 0.05, 0), UP, rr, 0.15, Ink.alpha(Ink.GOLD, 0.7F * fade), 96);
                }
            }
        }
    }
}
