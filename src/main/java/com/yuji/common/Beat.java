package com.yuji.common;

import net.minecraft.world.phys.Vec3;

/**
 * One event of a move's timeline, at tick {@code t} from the cast. Positions are anchors (the caster, the target,
 * the cast point...) plus a world-space offset, resolved on each side at the moment the beat fires, so a beat aimed
 * at a target follows it. The server reads beats for gameplay (damage, destruction, movement); the client reads the
 * same beats for everything you see and hear, firing them on the exact rendered frame they fall on.
 */
public record Beat(int t, int kind, int a0, Vec3 o0, int a1, Vec3 o1, double x, double y, double z, int dur, int i) {
    // ---- kinds
    public static final int BF = 1;          // Black Flash at p0: x scale, y damage multiplier, z variant; i = damage tier
    public static final int RING = 2;        // shockwave ring at p0 (ground): x max radius, y speed, z width
    public static final int BLAST = 3;       // destruction at p0: x radius, y depth ratio, i style (0 crater, 1 flatten, 2 sphere); z damage mult (0 = none)
    public static final int TRENCH = 4;      // directional trench from p0 toward p1: x length, y width, z depth; dur = ticks to run its length
    public static final int SLASH = 5;       // Dismantle from p0 toward p1: x length, y width, z roll degrees; i tier
    public static final int CLEAVE = 6;      // Cleave at p0: x size, y damage multiplier
    public static final int BOLT = 7;        // projectile p0 -> p1 over dur: x radius, y damage mult, z kind (0 blood, 1 energy), i curve seed (0 straight)
    public static final int DUST = 8;        // dust cloud at p0: x radius, y height
    public static final int DARK = 9;        // the world darkens to x over dur ticks
    public static final int SHELL = 10;      // energy shell at p0: x max radius over dur ticks, z kind (0 cursed, 1 domain gold)
    public static final int KNOCK = 11;      // launch whatever is at p0 (radius y): x up velocity, z push away
    public static final int DMG = 12;        // damage everything within x of p0: y multiplier, i tier (-1 = push only)
    public static final int ORB = 13;        // blood orb travelling p0 -> p1 over dur: x radius
    public static final int CHARGE = 14;     // energy compressing into p0 over dur: x amount
    public static final int DOMAIN_ON = 15;
    public static final int DOMAIN_OFF = 16;
    public static final int OVERDRIVE = 17;
    public static final int SPHERE = 18;     // the great blood formation at p0: x radius, for dur ticks
    public static final int BLACKOUT = 19;   // a black frame for dur ticks
    public static final int SMEAR = 20;      // smear frames along p0 -> p1
    public static final int DASH = 21;       // move the caster to p1 over dur ticks, stopping x short of it
    public static final int SOUND = 22;      // sound i at p0: x volume, y pitch
    public static final int STREAK = 23;     // a cursed-energy streak trailing the caster for dur ticks: x radius
    public static final int IMPACT = 24;     // a pure impact-frame beat at p0: i preset, x strength
    public static final int GRAB = 27;       // The End: the target held in a chokehold in front of the caster for dur ticks
    public static final int SINK = 28;       // The End: caster and held target sink through the ground into the void over dur ticks
    public static final int KILL = 29;       // The End: the target dies, creative or not
    public static final int RETURN = 30;     // The End: the caster comes back up to where he started
    public static final int GLYPH = 26;      // the World Ender's signature carved into the crater floor at p0: x = text width
    public static final int HEAL = 25;       // Reverse Cursed Technique x100: the caster's body rebuilt to x max health, fully healed

    // ---- anchors
    public static final int CASTER = 0, EYE = 1, TARGET = 2, POINT = 3, GROUND = 4, HAND = 5, START = 6, DOMAIN = 7, T0 = 10;

    public static Beat of(int t, int kind, int a0, Vec3 o0) {
        return new Beat(t, kind, a0, o0, -1, Vec3.ZERO, 0, 0, 0, 0, 0);
    }

    public Beat to(int a, Vec3 o) {
        return new Beat(t, kind, a0, o0, a, o, x, y, z, dur, i);
    }

    public Beat p(double x, double y, double z) {
        return new Beat(t, kind, a0, o0, a1, o1, x, y, z, dur, i);
    }

    public Beat d(int dur) {
        return new Beat(t, kind, a0, o0, a1, o1, x, y, z, dur, i);
    }

    public Beat n(int i) {
        return new Beat(t, kind, a0, o0, a1, o1, x, y, z, dur, i);
    }
}
