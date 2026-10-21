package com.yuji.common;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static com.yuji.common.Beat.*;

/**
 * The timelines of every move, as lists of {@link Beat}s. Built identically on the server and on every client
 * from the cast's {@link Ctx} (same seed, same numbers), so the hit you see and the hit that deals damage are
 * the same beat on the same tick. Ticks are 1/20 s.
 */
public final class Script {
    private Script() {}

    // sound indices (Snd.NAMES)
    public static final int S_CHARGE = 0, S_BF = 1, S_BF_BIG = 2, S_DASH = 3, S_BLOOD_FORM = 4, S_BLOOD_FIRE = 5,
            S_BLOOD_HIT = 6, S_BLOOD_RAIN = 7, S_SLASH = 8, S_CLEAVE = 9, S_SPLITTER = 10, S_CRATER = 11, S_ERUPT = 12,
            S_DOMAIN = 13, S_DOMAIN_HUM = 14, S_OVERDRIVE = 15, S_WAVE = 16, S_WORLD_ENDER = 17, S_RUMBLE = 18,
            S_METEOR = 20;

    private static final class B {
        final List<Beat> l = new ArrayList<>();
        final Ctx c;
        final Random r;
        final Vec3 F, R, U = new Vec3(0, 1, 0);

        B(Ctx c) {
            this.c = c;
            this.r = new Random(c.seed() * 31 + c.move().ordinal());
            this.F = c.f();
            this.R = c.r();
        }

        Beat add(Beat b) {
            l.add(b);
            return b;
        }

        void set(Beat old, Beat b) {
            l.set(l.indexOf(old), b);
        }

        // ---- helpers that read like a shot list
        void sound(int t, int anchor, int id, double vol, double pitch) {
            add(Beat.of(t, SOUND, anchor, Vec3.ZERO).p(vol, pitch, 0).n(id));
        }

        void dash(int t, int dur, int anchor, Vec3 off, double standoff) {
            add(Beat.of(t, DASH, CASTER, Vec3.ZERO).to(anchor, off).p(standoff, 0, 0).d(dur));
            sound(t, CASTER, S_DASH, 2.0, 0.9 + r.nextDouble() * 0.3);
        }

        void bf(int t, int anchor, Vec3 off, double scale, double mult, int variant, int tier) {
            add(Beat.of(t, BF, anchor, off).p(scale, mult, variant).n(tier));
            sound(t, anchor, scale >= 2.5 ? S_BF_BIG : S_BF, 3.0 + scale * 2, 1.0 / Math.max(0.6, Math.sqrt(scale * 0.7)));
        }

        void ring(int t, int anchor, Vec3 off, double maxR, double speed, double width) {
            add(Beat.of(t, RING, anchor, off).p(maxR * c.ds(), speed, width));
        }

        void blast(int t, int anchor, Vec3 off, double radius, double depth, int style, double dmgMult) {
            add(Beat.of(t, BLAST, anchor, off).p(radius * c.ds(), depth, dmgMult).n(style));
            sound(t, anchor, radius > 18 ? S_ERUPT : S_CRATER, 4 + radius * 0.4, 1.0 / Math.max(0.7, Math.sqrt(radius / 8)));
        }

        void trench(int t, int anchor, Vec3 off, Vec3 toward, double len, double width, double depth, int dur) {
            add(Beat.of(t, TRENCH, anchor, off).to(anchor, off.add(toward)).p(len * c.ds(), width, depth).d(dur));
        }

        void dust(int t, int anchor, Vec3 off, double radius, double height) {
            add(Beat.of(t, DUST, anchor, off).p(radius * c.ds(), height * c.ds(), 0));
        }

        void dark(int t, double amount, int dur) {
            add(Beat.of(t, DARK, CASTER, Vec3.ZERO).p(amount, 0, 0).d(dur));
        }

        void shell(int t, int anchor, Vec3 off, double maxR, int dur, int kind) {
            add(Beat.of(t, SHELL, anchor, off).p(maxR, 0, kind).d(dur));
        }

        void knock(int t, int anchor, double up, double push, double radius) {
            add(Beat.of(t, KNOCK, anchor, Vec3.ZERO).p(up, radius, push));
        }

        void dmg(int t, int anchor, Vec3 off, double radius, double mult, int tier) {
            add(Beat.of(t, DMG, anchor, off).p(radius, mult, 0).n(tier));
        }

        void charge(int t, int dur, int anchor, Vec3 off, double amount) {
            add(Beat.of(t, CHARGE, anchor, off).p(amount, 0, 0).d(dur));
            sound(t, anchor, S_CHARGE, 1.5 + amount, 0.8 + 20.0 / (dur + 20.0));
        }

        void bolt(int t, int dur, int a0, Vec3 o0, int a1, Vec3 o1, double radius, double mult, int kind, int curve) {
            add(Beat.of(t, BOLT, a0, o0).to(a1, o1).p(radius, mult, kind).d(dur).n(curve));
        }

        void orb(int t, int dur, int a0, Vec3 o0, int a1, Vec3 o1, double radius) {
            add(Beat.of(t, ORB, a0, o0).to(a1, o1).p(radius, 0, 0).d(dur));
        }

        void slash(int t, int a0, Vec3 o0, int a1, Vec3 o1, double len, double width, double roll, int tier) {
            add(Beat.of(t, SLASH, a0, o0).to(a1, o1).p(len, width, roll).n(tier));
        }

        void blackout(int t, int dur) {
            add(Beat.of(t, BLACKOUT, CASTER, Vec3.ZERO).d(dur));
        }

        void smear(int t, int a0, int a1) {
            add(Beat.of(t, SMEAR, a0, Vec3.ZERO).to(a1, Vec3.ZERO).d(3));
        }

        void impact(int t, int anchor, int preset, double strength) {
            add(Beat.of(t, IMPACT, anchor, Vec3.ZERO).p(strength, 0, 0).n(preset));
        }

        Vec3 jitter(double s) {
            return new Vec3((r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * s, (r.nextDouble() - 0.5) * s);
        }

        Vec3 around(double radius) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * radius;
            return new Vec3(Math.cos(a) * d, 0, Math.sin(a) * d);
        }

        int tgt(int k) {
            int n = c.targetCount();
            return n == 0 ? TARGET : T0 + (k % n);
        }
    }

    public static List<Beat> build(Ctx c) {
        B b = new B(c);
        switch (c.move()) {
            case WORLD_BREAKER -> worldBreaker(b);
            case RCT -> rct(b);
            case THE_END -> theEnd(b);
            case RICOCHET -> ricochet(b);
            case RESONANCE -> resonance(b);
            case PIERCING_BLOOD -> piercingBlood(b);
            case HOMING_BLOOD -> homingBlood(b);
            case SUPERNOVA -> supernova(b);
            case BLOOD_STORM -> bloodStorm(b);
            case CRIMSON_EXECUTION -> crimsonExecution(b);
            case BLOOD_APOCALYPSE -> bloodApocalypse(b);
            case DISMANTLE -> dismantle(b);
            case THOUSAND_SLASHES -> thousandSlashes(b);
            case CLEAVE -> cleave(b);
            case WORLD_SPLITTER -> worldSplitter(b);
            case METEOR -> meteor(b);
            case DOMAIN -> domain(b);
            case DOMAIN_FINISHER -> domainFinisher(b);
            case DOMAIN_BARRAGE -> domainBarrage(b);
            case OVERDRIVE -> overdrive(b);
            case FINAL_JUDGMENT -> finalJudgment(b);
            case DOMAIN_APOCALYPSE -> domainApocalypse(b);
        }
        b.l.sort(Comparator.comparingInt(Beat::t));
        return b.l;
    }

    private static final Vec3 Z = Vec3.ZERO;

    // ================================================================== Black Flash

    /** Knock whatever is at the target along a direction (dx, dz flat), with some lift. */
    private static void shove(B b, int t, Vec3 dir, double up, double push) {
        Vec3 d = new Vec3(dir.x, 0, dir.z);
        d = d.lengthSqr() < 1e-6 ? b.F : d.normalize();
        b.add(Beat.of(t, KNOCK, TARGET, d.scale(-1.2)).p(up, 2.8, push));
    }

    /**
     * Black Flash: Ricochet. No charge, no crater: the target becomes a pinball. Five Black Flashes from five
     * directions, Yuji blinking to wherever it is flying before it gets there: sideways, back the other way, up,
     * spiked down, and the last one an uppercut that throws it into the sky, where the black sparks catch up with
     * it a second later.
     */
    private static void ricochet(B b) {
        Vec3 F = b.F, R = b.R;
        b.dash(0, 6, TARGET, Z, 1.3);
        b.bf(8, TARGET, Z, 1.3, 0.6, 22, 1);
        shove(b, 8, R, 0.35, 3.2);
        b.ring(8, GROUND, Z, 14, 70, 1.0);
        // 2: already there, from the other side
        b.dash(11, 3, TARGET, R.scale(2.2), 0.1);
        b.smear(15, CASTER, TARGET);
        b.bf(16, TARGET, Z, 1.4, 0.6, 23, 1);
        shove(b, 16, R.scale(-1), 0.4, 3.6);
        // 3: under it, kicked up
        b.dash(19, 3, TARGET, R.scale(-2.0).add(0, -0.6, 0), 0.1);
        b.bf(24, TARGET, Z, 1.5, 0.6, 24, 1);
        shove(b, 24, F, 1.9, 0.8);
        b.ring(24, GROUND, Z, 18, 70, 1.2);
        // 4: above it, spiked back down
        b.dash(28, 4, TARGET, new Vec3(0, 1.8, 0).add(F.scale(-0.6)), 0.1);
        b.bf(34, TARGET, Z, 1.6, 0.6, 25, 1);
        shove(b, 34, F, -3.2, 0.3);
        b.blast(38, GROUND, Z, 5, 0.45, 0, 0);
        b.dust(39, GROUND, Z, 7, 7);
        // 5: as it bounces off the ground, the uppercut
        b.dash(40, 6, TARGET, F.scale(-1.0).add(0, -0.4, 0), 0.2);
        b.charge(40, 8, HAND, Z, 1.2);
        b.blackout(47, 1);
        b.bf(48, TARGET, Z, 2.4, 1.2, 26, 2);
        shove(b, 48, F, 3.6, 0.5);
        b.ring(48, GROUND, Z, 30, 80, 1.6);
        b.add(Beat.of(48, BOLT, GROUND, Z).to(GROUND, new Vec3(0, 34, 0)).p(0.6, 0.0, 1).d(6));   // a black streak up after it
        // the echo: the sparks catch it at the top
        b.impact(62, TARGET, 2, 0.8);
        b.bf(62, TARGET, Z, 2.0, 0.8, 27, 2);
        b.shell(62, TARGET, Z, 8, 10, 0);
        b.sound(62, TARGET, S_BF_BIG, 6.0, 1.2);
    }

    /**
     * Black Flash: Resonance. One short jab, and the black sparks it leaves don't stop: they arc from enemy to
     * enemy as black lightning, a Black Flash on each one they reach. Then everything they touched goes off again
     * at once, in resonance with the first.
     */
    private static void resonance(B b) {
        int n = Math.max(1, b.c.targetCount());
        b.dark(0, 0.55, 10);
        b.charge(0, 14, HAND, Z, 0.9);
        b.dash(6, 6, TARGET, Z, 1.2);
        b.bf(14, b.tgt(0), Z, 1.4, 0.8, 30, 1);
        int t = 14;
        for (int k = 1; k < Math.max(n, 5); k++) {
            int from = b.tgt(k - 1), to = b.tgt(k);
            // a jagged arc of black lightning to the next one
            b.add(Beat.of(t, BOLT, from, Z).to(to, Z).p(0.35, 0.0, 1).d(3).n(1 + k));
            t += 4;
            b.bf(t, to, Z, 1.1 + 0.08 * k, 0.6, 30 + k, 1);
            b.sound(t, to, S_BF, 3.0, 1.2 + 0.05 * k);
        }
        b.dark(t + 2, 0.75, 8);
        int res = t + 14;
        // resonance: all of them, at once
        b.blackout(res - 1, 1);
        for (int k = 0; k < Math.max(n, 5); k++) {
            int who = b.tgt(k);
            b.bf(res, who, Z, 1.8, 1.0, 40 + k, 2);
            b.shell(res, who, Z, 5, 10, 0);
            b.knock(res, who, 0.9, 2.0, 2.5);
        }
        b.impact(res, TARGET, 3, 0.9);
        b.sound(res, TARGET, S_BF_BIG, 8.0, 0.9);
        b.ring(res, GROUND, Z, 36, 80, 1.6);
        b.dark(res + 2, 0.0, 14);
    }

    /**
     * Reverse Cursed Technique x100: negative energy multiplied against itself, a hundred times over. His body is
     * rebuilt sturdier than it was: an iron golem's 100 health (50 hearts), healed to full, poisons and withering
     * burned out.
     */
    /**
     * The End. He is behind them before they see him move: an arm locked around the throat, and the ground under
     * them gives way to nothing. He drags them down through the earth, past the bedrock, into the void, and holds on
     * until it takes them. Then he walks back out where he started.
     */
    private static void theEnd(B b) {
        b.dark(0, 0.6, 6);
        b.dash(0, 5, TARGET, b.F.scale(0.8), 0.0);               // around behind them
        b.add(Beat.of(6, GRAB, TARGET, Z).d(64));
        b.impact(6, TARGET, 2, 0.6);
        b.sound(6, TARGET, S_BLOOD_HIT, 2.5, 0.6);
        b.dark(8, 0.85, 14);
        b.sound(10, CASTER, S_RUMBLE, 5.0, 0.5);
        // the ground opens into black
        b.ring(22, START, Z, 6, 12, 1.2);
        b.shell(22, START, Z, 3, 20, 0);
        b.sound(22, START, S_WAVE, 6.0, 0.5);
        b.add(Beat.of(24, SINK, CASTER, Z).p(24, 0, 0).d(38));      // x: how far below the world's floor they go
        b.blackout(63, 3);
        b.add(Beat.of(64, KILL, TARGET, Z));
        b.sound(64, START, S_BF_BIG, 6.0, 0.5);
        b.add(Beat.of(66, RETURN, CASTER, Z));
        b.impact(67, CASTER, 6, 0.6);
        b.dark(68, 0.0, 20);
    }

    private static void rct(B b) {
        b.charge(0, 16, EYE, new Vec3(0, -0.5, 0), 0.8);
        b.sound(0, CASTER, S_CHARGE, 2.0, 1.4);
        b.add(Beat.of(18, HEAL, CASTER, Z).p(100, 0, 0));
        b.sound(18, CASTER, S_OVERDRIVE, 3.0, 1.6);
        b.shell(18, CASTER, new Vec3(0, 1, 0), 4, 12, 1);
        b.ring(18, CASTER, Z, 10, 30, 0.8);
        b.impact(18, CASTER, 6, 0.5);
    }

    private static void worldBreaker(B b) {
        b.dark(0, 0.85, 30);
        b.sound(0, CASTER, S_RUMBLE, 4, 0.7);
        b.ring(10, CASTER, Z, 8, 20, 0.8);
        b.ring(24, CASTER, Z, 10, 20, 0.8);
        b.charge(28, 36, HAND, Z, 2.0);
        b.dash(58, 7, TARGET, Z, 1.4);
        b.smear(63, CASTER, TARGET);
        b.blackout(64, 2);
        b.bf(66, TARGET, Z, 3.5, 3.0, 16, 2);
        b.knock(66, TARGET, 1.0, 6.0, 6.0);
        b.shell(66, GROUND, Z, 22, 20, 0);
        b.ring(66, GROUND, Z, 60, 90, 2.0);
        b.ring(70, GROUND, Z, 80, 70, 2.5);
        b.ring(76, GROUND, Z, 100, 60, 3.0);
        b.blast(66, GROUND, Z, 24, 0.6, 0, 0.6);
        b.dust(70, GROUND, Z, 30, 34);
        // the delayed second eruption
        b.blast(96, GROUND, b.F.scale(6), 34, 0.45, 0, 0.6);
        b.shell(96, GROUND, b.F.scale(6), 30, 18, 0);
        b.ring(96, GROUND, Z, 90, 80, 2.5);
        b.dust(98, GROUND, b.F.scale(6), 40, 40);
        b.dark(70, 0.0, 30);
    }

    // ================================================================== Blood Manipulation
    /**
     * Piercing Blood. The compressed blood is fired point-blank; where it hits, it bursts: the beam splits into
     * thinner beams, one for every enemy around the one it hit (the more of them, the more it splits), and each of
     * those splits again as it passes through, spraying out beyond them.
     */
    private static void piercingBlood(B b) {
        int n = b.c.targetCount();
        b.sound(0, HAND, S_BLOOD_FORM, 1.5, 1.2);
        b.orb(0, 8, HAND, Z, HAND, Z, 0.22);
        b.sound(8, HAND, S_BLOOD_FIRE, 3.0, 1.0);
        int first = n > 0 ? T0 : POINT;
        b.bolt(8, 4, HAND, Z, first, Z, 0.16, 1.0, 0, 0);
        b.blast(12, first, Z, 2.0, 0.6, 2, 0.2);
        b.ring(12, first, Z, 6, 40, 0.6);
        if (n <= 1) {
            // nothing else to split toward: the beam still bursts out behind its target
            for (int k = 0; k < 4; k++) {
                Vec3 d = b.F.add(b.jitter(0.9)).normalize().scale(6 + b.r.nextDouble() * 6);
                b.bolt(12, 3, first, Z, first, d, 0.07, 0.0, 0, 0);
            }
            return;
        }
        // the split: a beam from the struck enemy to every other one around it
        b.sound(12, first, S_BLOOD_FIRE, 3.0, 1.4);
        for (int k = 1; k < n; k++) {
            int to = T0 + k;
            int dur = 3 + k % 3;
            b.bolt(12 + k / 4, dur, first, Z, to, Z, 0.1, 1.0, 0, 0);
            int hit = 12 + k / 4 + dur;
            b.ring(hit, to, Z, 3, 30, 0.4);
            // and through each of them it splits again, spraying out the far side
            for (int s2 = 0; s2 < 2; s2++) {
                Vec3 d = b.jitter(2.0).add(0, 0.3, 0).normalize().scale(4 + b.r.nextDouble() * 5);
                b.bolt(hit, 3, to, Z, to, d, 0.06, 0.0, 0, 0);
            }
            if (k % 3 == 0) b.sound(hit, to, S_BLOOD_HIT, 2.0, 1.2 + 0.05 * k);
        }
    }

    private static void homingBlood(B b) {
        b.sound(0, HAND, S_BLOOD_FORM, 1.5, 1.1);
        b.orb(0, 8, HAND, Z, HAND, Z, 0.3);
        b.sound(8, HAND, S_BLOOD_FIRE, 3.0, 0.9);
        int n = Math.max(1, b.c.targetCount());
        for (int k = 0; k < Math.max(8, n); k++) {
            int to = b.tgt(k);
            // each beam splits off, swings wide and curls into its mark
            b.bolt(8 + k % 4, 8 + k % 3, HAND, Z, to, Z, 0.12, 1.0, 0, 1 + k);
        }
    }

    private static void supernova(B b) {
        b.sound(0, HAND, S_BLOOD_FORM, 2.0, 0.9);
        for (int k = 0; k < 5; k++) {
            double a = k / 5.0 * Math.PI * 2;
            Vec3 ring = b.R.scale(Math.cos(a) * 2.4).add(0, 1.2 + Math.sin(a) * 0.9, 0).add(b.F.scale(0.8));
            b.orb(0, 18, HAND, Z, EYE, ring, 0.35);
            b.orb(18, 14, EYE, ring, TARGET, b.jitter(0.6), 0.35);
        }
        b.sound(32, TARGET, S_BLOOD_HIT, 4.0, 0.9);
        b.shell(32, TARGET, Z, 5, 8, 0);
        b.dmg(32, TARGET, Z, 8, 1.0, 1);
        b.knock(32, TARGET, 0.6, 2.2, 8);
        for (int k = 0; k < 24; k++) {
            double a = k / 24.0 * Math.PI * 2 + b.r.nextDouble() * 0.2;
            double up = (b.r.nextDouble() - 0.3) * 0.8;
            Vec3 d = new Vec3(Math.cos(a), up, Math.sin(a)).normalize().scale(10 + b.r.nextDouble() * 4);
            b.bolt(32, 7, TARGET, Z, TARGET, d, 0.12, 0.5, 0, 0);
        }
        b.blast(33, GROUND, Z, 3.5, 0.5, 0, 0);
        b.ring(32, GROUND, Z, 20, 60, 1.0);
    }

    private static void bloodStorm(B b) {
        b.dark(0, 0.4, 20);
        b.charge(0, 24, HAND, Z, 0.8);
        b.sound(20, POINT, S_BLOOD_RAIN, 6.0, 1.0);
        for (int w = 0; w < 4; w++) {
            int t0 = 24 + w * 20;
            for (int k = 0; k < 16; k++) {
                Vec3 g = b.around(22);
                int t = t0 + b.r.nextInt(10);
                Vec3 sky = g.add(b.jitter(4)).add(0, 36, 0);
                b.bolt(t, 10, POINT, sky, GROUND, g, 0.22, 0.6, 0, 0);
                b.blast(t + 10, GROUND, g, 2.5, 0.6, 0, 0.4);
            }
            b.ring(t0 + 10, POINT, Z, 30, 50, 1.2);
            b.dmg(t0 + 10, POINT, Z, 24, 0.5, 1);
        }
        b.dark(100, 0.0, 12);
    }

    private static void crimsonExecution(B b) {
        b.sound(0, HAND, S_BLOOD_FORM, 2.5, 0.8);
        for (int k = 0; k < 6; k++) {
            double a = k / 6.0 * Math.PI * 2;
            Vec3 o = b.R.scale(Math.cos(a) * 1.6).add(0, Math.sin(a) * 1.6, 0);
            b.orb(0, 22, HAND, o, HAND, Z, 0.25);
        }
        b.charge(4, 18, HAND, Z, 1.0);
        b.dash(22, 6, TARGET, Z, 1.3);
        b.smear(27, CASTER, TARGET);
        b.sound(28, TARGET, S_BLOOD_FIRE, 4.0, 0.7);
        b.bolt(28, 2, HAND, Z, TARGET, b.F.scale(12), 0.45, 4.0, 0, 0);
        b.impact(30, TARGET, 2, 1.0);
        b.shell(30, TARGET, Z, 6, 10, 0);
        b.blast(30, GROUND, Z, 4, 0.5, 0, 0);
        b.dmg(30, TARGET, Z, 3.0, 4.0, 2);
        b.knock(30, TARGET, 0.8, 5.0, 2.5);
        b.ring(30, GROUND, Z, 22, 70, 1.4);
        b.sound(30, TARGET, S_BLOOD_HIT, 4.0, 0.8);
    }

    private static void bloodApocalypse(B b) {
        Vec3 sky = new Vec3(0, 40, 0);
        b.dark(0, 0.6, 30);
        b.add(Beat.of(0, SPHERE, POINT, sky).p(12, 0, 0).d(118));
        b.sound(0, POINT, S_BLOOD_FORM, 8.0, 0.5);
        b.sound(50, POINT, S_BLOOD_RAIN, 8.0, 0.8);
        for (int k = 0; k < 50; k++) {
            int t = 50 + k;
            Vec3 g = b.around(30);
            Vec3 from = sky.add(b.jitter(18));
            b.bolt(t, 8, POINT, from, GROUND, g, 0.3, 0.6, 0, 0);
            b.blast(t + 8, GROUND, g, 2.0, 0.6, 0, 0.3);
        }
        b.sound(104, POINT, S_METEOR, 8.0, 0.8);
        b.bolt(104, 12, POINT, sky, GROUND, Z, 3.0, 1.0, 0, 0);
        b.blackout(115, 2);
        b.impact(116, GROUND, 3, 1.0);
        b.blast(116, GROUND, Z, 30, 0.5, 0, 0);
        b.dmg(116, GROUND, Z, 40, 1.0, 2);
        b.shell(116, GROUND, Z, 30, 20, 0);
        b.ring(116, GROUND, Z, 80, 90, 2.5);
        b.ring(122, GROUND, Z, 110, 70, 3.0);
        b.dust(118, GROUND, Z, 35, 36);
        b.dark(120, 0.0, 30);
    }

    // ================================================================== Shrine
    private static void dismantle(B b) {
        b.sound(3, EYE, S_SLASH, 3.0, 0.95 + b.r.nextDouble() * 0.1);
        double roll = b.r.nextBoolean() ? 20 + b.r.nextDouble() * 40 : 120 + b.r.nextDouble() * 40;
        b.slash(3, EYE, b.c.dir().scale(-0.5), EYE, b.c.dir().scale(10), b.c.dismantle(), 18, roll, 1);
    }

    private static void thousandSlashes(B b) {
        b.dark(0, 0.35, 8);
        b.sound(6, POINT, S_SLASH, 6.0, 0.8);
        for (int k = 0; k < 26; k++) {
            int t = 8 + k * 2;
            Vec3 aim = b.around(28).add(0, b.r.nextDouble() * 2, 0);
            double roll = b.r.nextDouble() * 180;
            b.slash(t, EYE, b.jitter(1.5), POINT, aim, 70, 14, roll, 0);
            if (k % 4 == 0) b.sound(t, POINT, S_SLASH, 4.0, 0.8 + b.r.nextDouble() * 0.5);
        }
        b.dark(60, 0.0, 12);
    }

    private static void cleave(B b) {
        b.dash(0, 5, TARGET, Z, 1.0);
        b.sound(6, TARGET, S_CLEAVE, 3.0, 1.0);
        b.add(Beat.of(6, CLEAVE, TARGET, Z).p(2.0, 1.0, 0));
        b.impact(6, TARGET, 1, 0.8);
        b.ring(6, GROUND, Z, 12, 60, 1.0);
        for (int k = 0; k < 6; k++) {
            double a = k / 6.0 * Math.PI * 2 + b.r.nextDouble() * 0.4;
            b.trench(6, GROUND, Z, new Vec3(Math.cos(a), 0, Math.sin(a)), 9, 1, 3, 2);
        }
    }

    private static void worldSplitter(B b) {
        b.charge(0, 20, HAND, Z, 1.2);
        b.dark(0, 0.5, 16);
        b.sound(22, EYE, S_SPLITTER, 10.0, 1.0);
        double L = b.c.splitter();
        // the cut: a vertical plane of slashing energy racing to the horizon
        b.slash(22, EYE, Z, EYE, b.F.scale(10), L, 40, 90, 2);
        b.trench(22, CASTER, b.F.scale(2), b.F, L, 3, 30, (int) Math.max(4, L / 30));
        b.dark(24, 0.0, 16);
        // the delayed shockwave following the cut
        b.trench(34, CASTER, b.F.scale(2), b.F, L, 9, 4, (int) Math.max(4, L / 24));
        b.sound(34, CASTER, S_RUMBLE, 8.0, 0.8);
    }

    // ================================================================== power moves
    private static void meteor(B b) {
        b.sound(8, CASTER, S_DASH, 4.0, 0.6);
        b.dash(8, 20, START, new Vec3(0, 55, 0), 0);
        b.dash(28, 8, START, new Vec3(0, 56, 0), 0);
        b.charge(26, 12, CASTER, new Vec3(0, 1, 0), 1.6);
        b.sound(36, CASTER, S_METEOR, 8.0, 1.0);
        b.dash(36, 10, GROUND, new Vec3(0, 0.2, 0), 0);
        b.add(Beat.of(36, STREAK, CASTER, Z).p(0.9, 0, 0).d(10));
        b.blackout(45, 1);
        b.impact(46, GROUND, 4, 1.0);
        b.blast(46, GROUND, Z, 20, 0.6, 0, 0);
        b.dmg(46, GROUND, Z, 24, 1.0, 2);
        b.knock(46, GROUND, 1.0, 5.0, 26);
        b.shell(46, GROUND, Z, 16, 14, 0);
        b.ring(46, GROUND, Z, 50, 90, 2.0);
        b.ring(50, GROUND, Z, 70, 70, 2.5);
        b.dust(48, GROUND, Z, 26, 28);
    }

    // ================================================================== Domain
    private static void domain(B b) {
        b.charge(34, 16, EYE, new Vec3(0, -0.5, 0), 1.0);
        b.dark(48, 0.7, 12);
        b.sound(50, CASTER, S_DOMAIN, 10.0, 1.0);
        b.add(Beat.of(62, DOMAIN_ON, CASTER, Z));
        b.impact(62, CASTER, 6, 1.0);
        b.shell(62, CASTER, Z, b.c.domainRadius(), 24, 1);
        b.ring(62, CASTER, Z, b.c.domainRadius(), 90, 2.0);
        b.dark(88, 0.0, 20);
        b.sound(90, CASTER, S_DOMAIN_HUM, 6.0, 1.0);
    }

    private static void domainFinisher(B b) {
        double R = Math.max(8, b.c.domainRadius());
        b.charge(0, 36, HAND, Z, 1.8);
        b.dark(0, 0.6, 20);
        b.dash(24, 10, TARGET, Z, 1.3);
        b.blackout(36, 2);
        b.bf(38, TARGET, Z, 3.0, 3.0, 17, 3);
        b.shell(38, DOMAIN, Z, R, 24, 0);
        b.dmg(40, DOMAIN, Z, R, 1.0, 2);
        b.blast(38, GROUND, Z, 18, 0.55, 0, 0);
        for (int k = 0; k < 8; k++) {
            double a = k / 8.0 * Math.PI * 2;
            b.trench(40, GROUND, Z, new Vec3(Math.cos(a), 0, Math.sin(a)), R, 3, 4, 14);
        }
        b.ring(38, DOMAIN, Z, R * 1.2, 80, 2.5);
        b.dust(42, GROUND, Z, 24, 26);
        b.dark(44, 0.0, 20);
        b.add(Beat.of(80, DOMAIN_OFF, CASTER, Z));
    }

    private static void domainBarrage(B b) {
        double R = Math.max(8, b.c.domainRadius());
        b.dark(0, 0.4, 8);
        for (int k = 0; k < 22; k++) {
            int t = 6 + k * 3;
            if (k % 2 == 0) {
                Vec3 from = b.around(R * 0.9).add(0, 8 + b.r.nextDouble() * 6, 0);
                Vec3 to = b.around(R * 0.7);
                b.slash(t, DOMAIN, from, DOMAIN, to, R * 1.6, 14, b.r.nextDouble() * 180, 1);
                b.sound(t, DOMAIN, S_SLASH, 5.0, 0.8 + b.r.nextDouble() * 0.5);
            } else {
                int to = b.tgt(k / 2);
                b.add(Beat.of(t, CLEAVE, to, Z).p(2.0, 0.6, 0));
                b.sound(t, to, S_CLEAVE, 4.0, 0.9 + b.r.nextDouble() * 0.3);
            }
        }
        b.blackout(73, 1);
        b.impact(74, DOMAIN, 3, 1.0);
        b.dmg(74, DOMAIN, Z, R, 1.0, 2);
        b.shell(74, DOMAIN, Z, R, 16, 0);
        b.ring(74, DOMAIN, Z, R * 1.1, 80, 2.5);
        for (int k = 0; k < Math.max(1, b.c.targetCount()); k++) b.blast(74, b.tgt(k), Z, 4, 0.5, 2, 0);
        b.dark(76, 0.0, 16);
    }

    private static void overdrive(B b) {
        b.charge(0, 40, EYE, new Vec3(0, -0.6, 0), 1.5);
        b.dark(10, 0.5, 20);
        b.sound(0, CASTER, S_RUMBLE, 5.0, 0.8);
        b.sound(40, CASTER, S_OVERDRIVE, 8.0, 1.0);
        b.add(Beat.of(42, OVERDRIVE, CASTER, Z));
        b.impact(42, CASTER, 6, 1.0);
        b.shell(42, CASTER, Z, 14, 14, 0);
        b.ring(42, CASTER, Z, 40, 70, 2.0);
        b.dmg(42, CASTER, Z, 10, 0, -1);
        b.knock(42, CASTER, 0.6, 2.5, 10);
        b.dark(46, 0.0, 14);
    }

    // ================================================================== ultimates
    private static void finalJudgment(B b) {
        double W = b.c.worldEnder();
        b.dark(30, 0.9, 40);
        b.sound(30, CASTER, S_RUMBLE, 8.0, 0.5);
        for (int t : new int[]{50, 70, 90}) b.ring(t, CASTER, Z, 8, 20, 0.8);
        b.charge(40, 72, HAND, Z, 2.4);
        b.dash(112, 7, TARGET, Z, 1.4);
        b.smear(116, CASTER, TARGET);
        b.blackout(118, 2);
        b.bf(120, TARGET, Z, 5.0, 5.0, 20, 3);
        b.sound(120, TARGET, S_WORLD_ENDER, 16.0, 1.0);
        b.dmg(120, GROUND, Z, W, 1.0, 3);
        b.knock(121, GROUND, 1.5, 6.0, W);
        b.shell(120, GROUND, Z, 40, 30, 0);
        b.ring(120, GROUND, Z, W * 1.2, 120, 3.0);
        b.ring(126, GROUND, Z, W * 1.6, 100, 4.0);
        b.blast(120, GROUND, Z, W, 0.35, 1, 0);
        b.dust(125, GROUND, Z, W * 0.6, W * 0.5);
        // his name, left in the floor of what remains
        b.add(Beat.of(140, GLYPH, GROUND, Z).p(Math.min(W * 1.05, 240), 0, 0));
        b.dark(170, 0.0, 50);
    }

    private static void domainApocalypse(B b) {
        double R = Math.max(8, b.c.domainRadius());
        double W = b.c.worldEnder() * 0.8;
        // I: the domain
        b.charge(0, 30, EYE, new Vec3(0, -0.5, 0), 1.2);
        b.dark(10, 0.7, 20);
        b.sound(28, CASTER, S_DOMAIN, 10.0, 0.9);
        b.add(Beat.of(40, DOMAIN_ON, CASTER, Z));
        b.impact(40, CASTER, 6, 1.0);
        b.shell(40, CASTER, Z, R, 20, 1);
        b.dark(60, 0.3, 10);
        // II: blood storm over the marked
        b.sound(60, DOMAIN, S_BLOOD_RAIN, 8.0, 0.9);
        for (int k = 0; k < 30; k++) {
            int t = 60 + k + k / 2;
            Vec3 g = b.around(R * 0.6);
            int to = k % 3 == 0 ? b.tgt(k) : GROUND;
            b.bolt(t, 10, DOMAIN, g.add(b.jitter(6)).add(0, 34, 0), to, to == GROUND ? g : Z, 0.25, 0.5, 0, 0);
            if (to == GROUND) b.blast(t + 10, GROUND, g, 2.0, 0.5, 0, 0.3);
        }
        // III: Dismantle across the domain
        for (int k = 0; k < 12; k++) {
            int t = 110 + k * 3;
            Vec3 from = b.around(R * 0.9).add(0, 10, 0);
            b.slash(t, DOMAIN, from, DOMAIN, b.around(R * 0.6), R * 1.6, 14, b.r.nextDouble() * 180, 1);
            b.sound(t, DOMAIN, S_SLASH, 5.0, 0.8 + b.r.nextDouble() * 0.4);
        }
        // IV: Cleave on the primary target
        b.dash(146, 6, TARGET, Z, 1.0);
        b.add(Beat.of(152, CLEAVE, TARGET, Z).p(2.5, 2.0, 0));
        b.sound(152, TARGET, S_CLEAVE, 6.0, 0.8);
        // V: Black Flash
        b.charge(154, 20, HAND, Z, 2.0);
        b.dark(156, 0.8, 16);
        b.blackout(174, 2);
        b.bf(176, TARGET, Z, 4.0, 4.0, 21, 3);
        b.shell(176, TARGET, Z, 20, 16, 0);
        b.ring(176, DOMAIN, Z, R * 1.5, 100, 3.0);
        // VI: total destruction
        b.impact(190, DOMAIN, 3, 1.0);
        b.sound(190, DOMAIN, S_WORLD_ENDER, 16.0, 0.9);
        b.blast(190, DOMAIN, Z, W, 0.35, 1, 0);
        b.dmg(190, DOMAIN, Z, W, 1.0, 3);
        b.shell(190, DOMAIN, Z, R * 1.5, 30, 0);
        b.ring(190, DOMAIN, Z, W * 1.4, 110, 3.5);
        b.dust(195, DOMAIN, Z, W * 0.6, W * 0.5);
        b.add(Beat.of(200, DOMAIN_OFF, CASTER, Z));
        b.dark(205, 0.0, 40);
    }
}
