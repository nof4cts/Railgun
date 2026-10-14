package com.firestar.sage.common;

/** Every rite's timing, in ticks from the cast. Server and client both read these. */
public final class SBeats {
    private SBeats() {}

    // Staff Rite: thrust, sweep, overhead
    public static final int STAFF_HIT = 4;

    // Null Orbs: one orb every few ticks, nine of them
    public static final int ORBS_FIRST = 8;
    public static final int ORBS_EVERY = 4;
    public static final int ORBS_COUNT = 9;
    public static final double ORBS_SPEED = 2.4;
    public static final double ORBS_RANGE = 64.0;
    public static final double ORBS_ERASE = 2.3;

    // Heaven's Pull
    public static final int PULL_BIND = 5;
    public static final int PULL_ARRIVE = 17;
    public static final int PULL_BURST = 20;
    public static final double PULL_RANGE = 34.0;

    // Mirror Step
    public static final int MIRROR_VANISH = 3;
    public static final int MIRROR_ECHO = 8;
    public static final int MIRROR_STRIKE = 11;
    public static final double MIRROR_RANGE = 30.0;

    // Starfall
    public static final int STAR_CALL = 16;
    public static final int STAR_APPEAR = 34;
    public static final int STAR_IMPACT = 96;
    public static final int STAR_SECOND = 116;
    public static final double STAR_HEIGHT = 95.0;
    public static final double STAR_RANGE = 90.0;
    public static final int STAR_CRATER = 14;

    // Thornwood
    public static final int THORN_PLANT = 8;
    public static final int THORN_ERUPT = 14;
    public static final int THORN_SPREAD = 22;
    public static final double THORN_LENGTH = 32.0;
    public static final int THORN_FADE = 50;

    // Colossus
    public static final int COL_RISE = 12;
    public static final int COL_FORMED = 58;
    public static final int COL_RAISE = 70;
    public static final int COL_CUT = 98;
    public static final double COL_LENGTH = 80.0;

    // Eternal Eclipse
    public static final int ECL_RISE = 10;
    public static final int ECL_COVER = 40;
    public static final int ECL_TOTAL = 104;
    public static final int ECL_RING = 140;
    public static final int ECL_RING_END = 176;
    public static final int ECL_DAWN = 200;
    public static final double ECL_RADIUS = 72.0;

    // Heaven's Mantle
    public static final int MANTLE_GATHER = 16;
    public static final int MANTLE_HALO = 70;
    public static final int MANTLE_IGNITE = 96;
}
