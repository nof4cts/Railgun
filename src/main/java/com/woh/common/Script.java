package com.woh.common;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static com.woh.common.Beat.*;

/**
 * The timelines of every move of The World Over Heaven, as lists of {@link Beat}s. Built identically on the server
 * and on every client from the cast's {@link Ctx} (same seed, same numbers), so the hit you see and the hit that
 * deals damage are the same beat on the same tick. Ticks are 1/20 s.
 */
public final class Script {
    private Script() {}

    // sound indices (Snd.NAMES) - reused from the shared sound set
    public static final int S_GATHER = 0, S_OVERWRITE = 1, S_OVERWRITE_BIG = 2, S_BLINK = 3, S_CRYSTALLIZE = 4, S_SHARD_THROW = 5,
            S_SHARD_HIT = 6, S_SHARD_RAIN = 7, S_REND = 8, S_SEAL = 9, S_FISSURE = 10, S_SLAM = 11, S_RUPTURE = 12,
            S_COLLAPSE = 13, S_LATTICE = 14, S_ASCEND = 15, S_CRACK = 16, S_TRUTH = 17, S_HALT = 18,
            S_IMPACT = 19, S_JUDGMENT = 20, S_CHIME = 21, S_VOICE = 22;

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

        // ---- helpers that read like a shot list
        void sound(int t, int anchor, int id, double vol, double pitch) {
            add(Beat.of(t, SOUND, anchor, Vec3.ZERO).p(vol, pitch, 0).n(id));
        }

        void dash(int t, int dur, int anchor, Vec3 off, double standoff) {
            add(Beat.of(t, DASH, CASTER, Vec3.ZERO).to(anchor, off).p(standoff, 0, 0).d(dur));
            sound(t, CASTER, S_BLINK, 2.0, 0.9 + r.nextDouble() * 0.3);
        }

        void bf(int t, int anchor, Vec3 off, double scale, double mult, int variant, int tier) {
            add(Beat.of(t, BF, anchor, off).p(scale, mult, variant).n(tier));
            sound(t, anchor, scale >= 2.5 ? S_OVERWRITE_BIG : S_OVERWRITE, 3.0 + scale * 2, 1.0 / Math.max(0.6, Math.sqrt(scale * 0.7)));
        }

        void ring(int t, int anchor, Vec3 off, double maxR, double speed, double width) {
            add(Beat.of(t, RING, anchor, off).p(maxR * c.ds(), speed, width));
        }

        void blast(int t, int anchor, Vec3 off, double radius, double depth, int style, double dmgMult) {
            add(Beat.of(t, BLAST, anchor, off).p(radius * c.ds(), depth, dmgMult).n(style));
            sound(t, anchor, radius > 18 ? S_RUPTURE : S_SLAM, 4 + radius * 0.4, 1.0 / Math.max(0.7, Math.sqrt(radius / 8)));
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
            sound(t, anchor, S_GATHER, 1.5 + amount, 0.8 + 20.0 / (dur + 20.0));
        }

        void bolt(int t, int dur, int a0, Vec3 o0, int a1, Vec3 o1, double radius, double mult, int kind, int curve) {
            add(Beat.of(t, BOLT, a0, o0).to(a1, o1).p(radius, mult, kind).d(dur).n(curve));
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

        // ---- The World Over Heaven
        void erase(int t, int anchor, Vec3 off, double radius, double threshold, int tier) {
            add(Beat.of(t, ERASE, anchor, off).p(radius, threshold, 0).n(tier));
            sound(t, anchor, S_TRUTH, 5.0, 0.7);
        }

        void stop(int t, int dur, double radius) {
            add(Beat.of(t, STOP, CASTER, Vec3.ZERO).p(radius, 0, 0).d(dur));
            sound(t, CASTER, S_HALT, 4.0, 0.55);
        }

        void strip(int t, int anchor, Vec3 off, double radius) {
            add(Beat.of(t, STRIP, anchor, off).p(radius, 0, 0));
        }

        void shard(int t, int anchor, Vec3 off, double radius, double count) {
            add(Beat.of(t, SHARD, anchor, off).p(radius, count, 0));
            sound(t, anchor, S_CRACK, 2.5, 1.2);
        }

        void returnSet(int t, int dur, double hp) {
            add(Beat.of(t, RETURN_SET, CASTER, Vec3.ZERO).p(hp, 0, 0).d(dur));
        }

        void negate(int t, int dur) {
            add(Beat.of(t, NEGATE, CASTER, Vec3.ZERO).d(dur));
        }

        void heal(int t, double maxHp, boolean allies) {
            add(Beat.of(t, HEAL, CASTER, Vec3.ZERO).p(maxHp, allies ? 1 : 0, 0));
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
            case ASCENDED_BARRAGE -> ascendedBarrage(b);
            case OVERWRITE_BLOW -> overwriteBlow(b);
            case ERASURE_HOOK -> erasureHook(b);
            case REWRITE_RUSH -> rewriteRush(b);
            case REALITY_OVERWRITE -> realityOverwrite(b);
            case WOUNDS_UNDONE -> woundsUndone(b);
            case GRACE_OVER_HEAVEN -> graceOverHeaven(b);
            case NEGATION -> negation(b);
            case HEAVENLY_LIGHTNING -> heavenlyLightning(b);
            case FOURTEEN_PHRASES -> fourteenPhrases(b);
            case REALITY_KNIVES -> realityKnives(b);
            case ROAD_TO_HEAVEN -> roadToHeaven(b);
            case TIME_STOP -> timeStop(b);
            case KNIFE_RAIN -> knifeRain(b);
            case STILLNESS -> stillness(b);
            case WORLDS_TRUTH -> worldsTruth(b);
            case RETURN_OVER_HEAVEN -> returnOverHeaven(b);
            case HEAVEN_ASCENSION -> heavenAscension(b);
            case REALITY_COLLAPSE -> realityCollapse(b);
            case COLLAPSE_FINISHER -> collapseFinisher(b);
        }
        b.l.sort(Comparator.comparingInt(Beat::t));
        return b.l;
    }

    private static final Vec3 Z = Vec3.ZERO;

    /** Knock whatever is at the target along a direction (flat), with some lift. */
    private static void shove(B b, int t, Vec3 dir, double up, double push) {
        Vec3 d = new Vec3(dir.x, 0, dir.z);
        d = d.lengthSqr() < 1e-6 ? b.F : d.normalize();
        b.add(Beat.of(t, KNOCK, TARGET, d.scale(-1.2)).p(up, 2.8, push));
    }

    // ============================================================ page 1: the fists

    /** A flurry of reality-rewriting fists: he blinks onto the target and lands a frame-accurate barrage, each punch
     *  a small white-gold crack in the air, the last one a launching blow. */
    private static void ascendedBarrage(B b) {
        b.sound(0, CASTER, S_BLINK, 2.0, 1.1);   // the Stand goes; he stays where he is
        b.smear(5, CASTER, TARGET);
        int[] at = {8, 11, 14, 17, 20, 23, 26, 29, 33, 37};
        for (int i = 0; i < at.length; i++) {
            Vec3 off = b.jitter(0.5);
            boolean big = i == at.length - 1;
            b.bf(at[i], TARGET, off, big ? 1.6 : 0.8, big ? 0.9 : 0.35, 20 + (i % 4), 1);
            b.dmg(at[i], TARGET, off, 2.6, big ? 1.0 : 0.34, 1);
            if (big || i % 4 == 0) b.impact(at[i], TARGET, big ? 2 : 0, big ? 1.1 : 0.5);   // the frame breaks on the beats that matter
        }
        b.ring(37, GROUND, Z, 10, 60, 0.9);
        shove(b, 37, b.F, 0.7, 4.2);
    }

    /** A single charged overwrite-fist: reality folds into his hand, then one colossal blow that craters the ground. */
    private static void overwriteBlow(B b) {
        b.charge(0, 24, HAND, Z, 1.4);
        b.dark(10, 0.3, 14);
        b.dark(30, 0.0, 8);
        b.sound(22, CASTER, S_BLINK, 2.0, 0.9);
        b.smear(26, CASTER, TARGET);
        b.bf(28, TARGET, Z, 3.0, 1.0, 40, 2);
        b.blast(29, GROUND, Z, 9, 0.5, 0, 1.0);
        b.ring(29, GROUND, Z, 22, 80, 1.4);
        b.dmg(29, TARGET, Z, 4.5, 1.0, 2);
        b.impact(29, TARGET, 4, 1.6);
        b.blackout(28, 1);
        shove(b, 30, b.F, 0.6, 6.5);
        b.dust(31, GROUND, Z, 10, 5);
    }

    /** Erasure Hook: reality strips the target of every ward - buffs, shields, resistances - and then a heavy hook lands
     *  on what is left. */
    private static void erasureHook(B b) {
        b.sound(0, CASTER, S_BLINK, 2.0, 1.0);
        b.shard(4, TARGET, Z, 2.0, 10);
        b.strip(6, TARGET, Z, 3.0);
        b.sound(6, TARGET, S_CRACK, 3.0, 0.8);
        b.bf(12, TARGET, Z, 1.8, 1.0, 24, 1);
        b.dmg(12, TARGET, Z, 3.0, 1.0, 1);
        b.impact(12, TARGET, 2, 1.1);
        shove(b, 12, b.R, 0.4, 4.0);
    }

    /** Rewrite Rush: five reality-steps - short blinks around the target - each leaving a smear and a strike, reality
     *  closing the gaps he tears open. */
    private static void rewriteRush(B b) {
        Vec3[] offs = {b.R.scale(1.8), b.R.scale(-1.8), b.F.scale(-1.6), new Vec3(0, 0, 0), b.F.scale(1.2)};
        int t = 0;
        for (int i = 0; i < offs.length; i++) {
            b.sound(t, TARGET, S_BLINK, 1.6, 1.0 + 0.08 * i);   // the Stand steps through reality to another side
            b.smear(t + 2, CASTER, TARGET);
            b.bf(t + 4, TARGET, b.jitter(0.4), i == offs.length - 1 ? 1.5 : 0.7, i == offs.length - 1 ? 0.9 : 0.3, 21 + i, 1);
            b.dmg(t + 4, TARGET, Z, 2.6, i == offs.length - 1 ? 0.9 : 0.3, 1);
            b.impact(t + 4, TARGET, i % 3, 0.7);
            t += 7;
        }
        b.ring(t, GROUND, Z, 9, 55, 0.9);
    }

    // ============================================================ page 2: reality overwrite

    /** Reality Overwrite - the execute. He declares the target's existence void: if it is weakened enough, it is
     *  simply erased, past armour, totems and resistance alike (and past As God As My Witness). Otherwise a heavy
     *  overwrite-blow lands. */
    private static void realityOverwrite(B b) {
        b.charge(0, 18, HAND, Z, 1.2);
        b.dark(6, 0.45, 10);
        b.dark(24, 0.0, 10);
        b.shard(16, TARGET, Z, 3.0, 18);
        b.blackout(18, 2);
        b.erase(20, TARGET, Z, 3.2, 0.5, 2);     // threshold: under 50% max HP is erased outright
        b.impact(20, TARGET, 4, 1.8);
        b.ring(21, GROUND, Z, 16, 70, 1.2);
        b.dmg(21, TARGET, Z, 3.2, 1.0, 2);       // fallback blow if not erased
        b.dust(22, GROUND, Z, 6, 4);
    }

    /** Wounds Undone: reality is told he was never hurt. Full heal, every ailment cleansed, a gold shell blooming out. */
    private static void woundsUndone(B b) {
        b.sound(0, CASTER, S_GATHER, 2.0, 1.1);
        b.shell(2, CASTER, Z, 3.0, 24, 1);
        b.heal(6, 0, false);
        b.ring(6, GROUND, Z, 6, 40, 0.8);
    }

    /** Grace Over Heaven: the same grace extended outward - nearby allies healed and strengthened. */
    private static void graceOverHeaven(B b) {
        b.sound(0, CASTER, S_LATTICE, 2.5, 1.0);
        b.shell(2, CASTER, Z, 10.0, 30, 1);
        b.heal(8, 0, true);
        b.ring(8, GROUND, Z, 12, 45, 1.0);
    }

    /** Negation: a thin pane of overwritten reality held before him - the next technique thrown at him is nullified
     *  and thrown back. */
    private static void negation(B b) {
        b.sound(0, CASTER, S_CRACK, 2.5, 0.9);
        b.shell(1, CASTER, Z, 3.5, 36, 1);
        b.negate(2, 40);
    }

    // ============================================================ page 3: heaven's judgment

    /** Heavenly Lightning: judgment falls on the aimed point - a bolt out of a clear sky, a ring and a launch. */
    private static void heavenlyLightning(B b) {
        b.charge(0, 12, EYE, Z, 0.8);
        b.bolt(12, 4, POINT, new Vec3(0, 40, 0), POINT, Z, 1.0, 1.0, 1, 0);
        b.sound(16, POINT, S_JUDGMENT, 6, 1.0);
        b.dmg(16, POINT, Z, 5.0, 1.0, 2);
        b.knock(16, POINT, 0.9, 3.5, 5.0);
        b.ring(16, POINT, Z, 16, 80, 1.3);
        b.impact(16, POINT, 3, 1.3);
        b.dust(17, POINT, Z, 6, 4);
    }

    /** Fourteen Phrases: fourteen crystalline sigils set in a ring around the point, lit one by one, then detonated
     *  together in a cascade. */
    private static void fourteenPhrases(B b) {
        b.dark(0, 0.35, 20);
        b.dark(125, 0.0, 20);
        int t = 6;
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            Vec3 off = new Vec3(Math.cos(a) * 6, 0, Math.sin(a) * 6);
            b.shard(t, POINT, off, 1.4, 3);
            b.sound(t, POINT, S_CRACK, 1.6, 1.3 + i * 0.03);
            t += 4;
        }
        // the cascade
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14;
            Vec3 off = new Vec3(Math.cos(a) * 6, 0, Math.sin(a) * 6);
            int tt = 70 + i * 3;
            b.dmg(tt, POINT, off, 3.0, 0.5, 2);
            b.ring(tt, POINT, off, 5, 50, 0.6);
            if (i % 4 == 0) b.impact(tt, POINT, 0, 0.6);
        }
        b.erase(118, POINT, Z, 7.0, 0.35, 2);
        b.blast(118, POINT, Z, 8, 0.4, 0, 1.0);
        b.ring(118, POINT, Z, 24, 90, 1.5);
        b.impact(118, POINT, 4, 1.6);
    }

    /** Reality-Warping Knives: a fan of crystal shards that ignore the target's dodges and bite harder the weaker it
     *  is (the server scales each hit by the target's missing health). */
    private static void realityKnives(B b) {
        b.sound(0, CASTER, S_REND, 2.5, 1.3);
        for (int i = 0; i < 7; i++) {
            double spread = (i - 3) * 0.14;
            Vec3 aim = b.F.scale(Math.cos(spread)).add(b.R.scale(Math.sin(spread)));
            b.bolt(2 + i, 10, HAND, Z, TARGET, aim.scale(0.0), 0.5, 0.5, 2, 1 + i);
            b.shard(2 + i, HAND, Z, 0.8, 2);
        }
        b.dmg(14, TARGET, Z, 2.4, 0.7, 1);
        b.impact(14, TARGET, 1, 0.8);
    }

    /** Road to Heaven: a pillar of overwritten reality rises under the target, hurling it up into a brief hover. */
    private static void roadToHeaven(B b) {
        b.sound(0, GROUND, S_FISSURE, 3.0, 1.0);
        b.trench(2, GROUND, Z, b.U.scale(0), 2.5, 2.5, 6, 6);
        b.shell(2, TARGET, Z, 2.5, 20, 1);
        b.knock(6, TARGET, 1.4, 0.0, 3.0);
        b.dmg(6, TARGET, Z, 3.0, 0.8, 1);
        b.ring(6, GROUND, Z, 10, 60, 1.0);
        b.impact(6, TARGET, 3, 1.0);
    }

    // ============================================================ page 4: the world (time)

    /** Time Stop: everything but him is frozen for a few seconds. The world desaturates and goes silent; the blows he
     *  lands inside the stop are held, and all land at once when time resumes. */
    private static void timeStop(B b) {
        b.sound(0, CASTER, S_TRUTH, 4.0, 0.8);
        b.dark(0, 0.45, 5);       // the world drains the instant he calls it
        b.dark(112, 0.0, 6);      // and the colour comes back when time does
        // the halt is held long enough for the call to finish: the world stays stopped for the whole line
        b.stop(2, 112, 40);
        b.sound(15, CASTER, S_VOICE, 4.0, 1.0);   // lands on the exact frame the camera locks off
        // he walks through the stopped world and marks his blows; none of them land until time runs again
        for (int i = 0; i < 4; i++) {
            int tt = 34 + i * 18;
            b.bf(tt, TARGET, b.jitter(0.6), 1.2, 0.0, 20 + i, 1);
            b.dmg(114, TARGET, b.jitter(0.6), 3.0, 0.6, 1);   // all of them arrive at once as time resumes
        }
        b.impact(114, TARGET, 4, 1.4);
        b.ring(114, GROUND, Z, 14, 70, 1.1);
    }

    /** Time Stop: Knife Rain - time halts and the air fills with suspended crystal knives; when it resumes, every one
     *  strikes in the same instant. */
    private static void knifeRain(B b) {
        b.sound(0, CASTER, S_TRUTH, 4.5, 0.75);
        b.dark(0, 0.45, 5);
        b.dark(72, 0.0, 6);
        b.stop(2, 70, 44);
        for (int i = 0; i < 24; i++) {
            Vec3 off = b.around(10).add(0, 4 + b.r.nextDouble() * 6, 0);
            b.shard(10 + i, POINT, off, 1.0, 1);
        }
        // resume: they all drive down together
        for (int i = 0; i < 24; i++) {
            Vec3 off = b.around(10);
            b.bolt(72, 3, POINT, off.add(0, 8, 0), POINT, off, 0.5, 0.4, 2, 0);
            b.dmg(75, POINT, off, 2.6, 0.45, 2);
        }
        b.impact(75, POINT, 4, 1.6);
        b.ring(75, POINT, Z, 20, 85, 1.4);
    }

    /** Stillness: a short, defensive halt - he stops time just long enough to step out of harm. */
    private static void stillness(B b) {
        b.sound(0, CASTER, S_HALT, 3.0, 0.9);
        b.dark(0, 0.35, 3);
        b.dark(19, 0.0, 4);
        b.stop(1, 18, 24);
        b.negate(1, 20);
    }

    // ============================================================ page 5: over heaven (ultimates)

    /** This Is the World's Truth: time halts, reality cracks like glass across the whole sky, and he overwrites the
     *  world - a catastrophic erase-and-crater that nothing weakened survives. */
    private static void worldsTruth(B b) {
        b.sound(0, CASTER, S_TRUTH, 6.0, 0.6);
        b.dark(2, 0.6, 12);
        b.dark(90, 0.0, 16);
        b.stop(4, 80, 64);
        b.blackout(6, 2);
        // the sky cracks in waves
        for (int i = 0; i < 6; i++) {
            b.shard(10 + i * 6, EYE, b.jitter(14).add(0, 10, 0), 6.0, 8);
        }
        b.charge(40, 36, CASTER, new Vec3(0, 2, 0), 2.0);
        b.blackout(84, 2);
        b.erase(86, CASTER, Z, 22.0, 0.75, 3);     // inside the field almost nothing is above the line
        b.blast(86, GROUND, Z, 26, 0.6, 0, 1.0);
        b.dmg(86, CASTER, Z, 22.0, 1.0, 3);
        b.ring(86, GROUND, Z, 48, 110, 2.2);
        b.impact(86, CASTER, 4, 2.0);
        b.dust(88, GROUND, Z, 24, 10);
        b.shell(86, CASTER, Z, 26, 40, 1);
    }

    /** Return Over Heaven: he rewrites his own death in advance - the next lethal blow for a long while is undone,
     *  his body back to full. */
    private static void returnOverHeaven(B b) {
        b.sound(0, CASTER, S_ASCEND, 3.0, 1.0);
        b.shell(2, CASTER, Z, 3.5, 30, 1);
        b.returnSet(4, 2400, 0);     // armed for two minutes, restores to full (0 = max health)
        b.ring(4, GROUND, Z, 6, 40, 0.8);
    }

    /** Heaven Ascension (toggle): he ascends - crystalline reality wreathes him, his blows carry rewrite, and he flies.
     *  Pressing it again lets the ascension go. */
    private static void heavenAscension(B b) {
        b.sound(0, CASTER, S_ASCEND, 4.0, 0.9);
        b.add(Beat.of(2, OVERDRIVE, CASTER, Z));   // reuse the Overdrive state machine (aura, flight, boosts)
        b.shell(2, CASTER, Z, 4.5, 40, 1);
        b.ring(4, GROUND, Z, 10, 60, 1.2);
        b.dark(2, 0.2, 10);
        b.dark(40, 0.0, 15);
    }

    /** Reality Collapse (domain): a field of overwritten reality - inside it his moves cannot miss and everything else
     *  is weakened and slowed. */
    private static void realityCollapse(B b) {
        b.sound(0, CASTER, S_COLLAPSE, 6.0, 0.8);
        b.dark(2, 0.4, 8);
        b.dark(40, 0.0, 30);
        b.add(Beat.of(6, DOMAIN_ON, CASTER, Z));
        b.shell(6, CASTER, Z, 20, 40, 1);
        b.ring(6, GROUND, Z, 40, 100, 2.0);
    }

    /** Reality Collapse: Overwrite - the finisher, only inside the field: the whole domain is overwritten at once. */
    private static void collapseFinisher(B b) {
        b.sound(0, CASTER, S_TRUTH, 6.0, 0.6);
        b.blackout(4, 2);
        b.erase(6, DOMAIN, Z, 40.0, 0.9, 3);
        b.dmg(6, DOMAIN, Z, 40.0, 1.0, 3);
        b.ring(6, DOMAIN, Z, 48, 120, 2.4);
        b.impact(6, CASTER, 4, 2.0);
        b.add(Beat.of(10, DOMAIN_OFF, CASTER, Z));
    }
}
