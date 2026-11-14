package com.aegis.command.item;

/**
 * The arsenal weapons. Stats are fictional 0-10 game attributes used by the UI; the gameplay
 * numbers (damage, rate, capacity) live alongside them. {@code heavy} marks the slow, cannon-class
 * weapons that get full impact frames (freeze, inverted flash, speed lines, shockwave).
 */
public enum WeaponType {
    PHANTOM_9("phantom_9", "PHANTOM-9", "PRECISION ENERGY RIFLE", "RARE", 0xFF4FA3FF, 0,
            0, 0, 0xE8ECF2, false,
            new int[]{7, 6, 8, 10, 6},
            24, 34, 4, 9f, 0.55f,
            "A whisper-quiet coherent-beam rifle. A cyan resonance core charges each pulse; the shot lands the instant you fire.",
            new float[]{8f, 8.6f, -14.6f}, new float[]{8f, 8.6f, 6.8f}, 8.6f),
    TITAN_X("titan_x", "TITAN-X", "HEAVY ARC CANNON", "LEGENDARY", 0xFFFF9A2E, 3,
            1, 1, 0xB3261E, true,
            new int[]{10, 2, 4, 7, 2},
            4, 60, 0, 22f, 1.6f,
            "An armoured siege cannon. Hold to build charge across three plasma chambers, release to throw an arc shell that ruptures on impact.",
            new float[]{8f, 8.5f, -14.4f}, new float[]{8f, 8.5f, 5.5f}, 8.5f),
    SPECTRE_7("spectre_7", "SPECTRE-7", "COMPACT PLASMA SMG", "EPIC", 0xFFB46CFF, 1,
            2, 2, 0x1C1E24, false,
            new int[]{4, 10, 6, 5, 9},
            40, 28, 2, 3.5f, 0.25f,
            "A lightweight plasma repeater with adaptive digital circuitry. Hold to stream violet bolts at a blistering cycle rate.",
            new float[]{8f, 8.4f, -10.6f}, new float[]{8f, 10.9f, 2.5f}, 10.9f),
    NOVA_RAIL("nova_rail", "NOVA-RAIL", "MAGNETIC LANCE RAILGUN", "LEGENDARY", 0xFFFF9A2E, 2,
            0, 3, 0xC9CED6, true,
            new int[]{10, 1, 7, 10, 1},
            3, 54, 34, 30f, 1.9f,
            "Twin superconducting rails throw a slug of bound plasma at a fraction of light speed. One shot. It goes through everything in a line.",
            new float[]{8f, 8.6f, -15.0f}, new float[]{8f, 8.6f, 5.0f}, 8.6f),
    HELIX_4("helix_4", "HELIX-4", "SCATTER PLASMA SHOTGUN", "EPIC", 0xFFB46CFF, 1,
            1, 4, 0xE2B93B, false,
            new int[]{8, 4, 5, 3, 4},
            8, 40, 9, 3.2f, 0.95f,
            "A helical accelerator coils a fistful of plasma into a seven-bolt spread. Point-blank, nothing walks away.",
            new float[]{8f, 8.6f, -13.8f}, new float[]{8f, 5.6f, 3.0f}, 8.6f),
    OBLIVION("oblivion", "OBLIVION", "SINGULARITY PROJECTOR", "MYTHIC", 0xFFFF3D6E, 4,
            3, 2, 0x16171C, true,
            new int[]{10, 1, 3, 6, 1},
            2, 70, 40, 26f, 2.2f,
            "A caged micro-singularity, fed and released. It drags the world inward for a heartbeat, then lets it all go at once.",
            new float[]{8f, 9.0f, -13.6f}, new float[]{8f, 10.0f, 5.0f}, 10.0f);

    public static final String[] STAT_NAMES = {"POWER", "FIRE RATE", "STABILITY", "RANGE", "CAPACITY"};

    public static final String[] FINISH_NAMES = {"OBSIDIAN", "GUNMETAL", "ARCTIC", "SOLAR GOLD"};
    public static final int[] FINISH_COLORS = {0x2E323A, 0x6E747E, 0xDCE1E8, 0xD2A64C};

    public static final String[] ENERGY_NAMES = {"CYAN", "EMBER", "VIOLET", "CRIMSON", "VERDANT"};
    public static final int[] ENERGY_COLORS = {0x2BE8FF, 0xFF7A1A, 0xB04BFF, 0xFF2E4D, 0x3DFF7A};

    public final String id;
    public final String displayName;
    public final String classification;
    public final String rarity;
    public final int rarityColor;
    /** Completed missions needed before the weapon can be equipped. */
    public final int unlockMissions;
    public final int defaultFinish;
    public final int defaultEnergy;
    /** Secondary armour colour painted on accent plates. */
    public final int accentColor;
    /** Slow, cannon-class weapon: gets impact frames. */
    public final boolean heavy;
    public final int[] stats;
    public final int capacity;
    public final int reloadTicks;
    public final int fireDelay;
    public final float damage;
    public final float recoil;
    public final String description;
    /** Muzzle and energy-core positions in model space (pixels), used for flashes and close-ups. */
    public final float[] muzzle;
    public final float[] core;
    /** Height of the axis the SPIN part group turns around (it runs along Z at x = 8). */
    public final float spinY;

    WeaponType(String id, String displayName, String classification, String rarity, int rarityColor, int unlockMissions,
               int defaultFinish, int defaultEnergy, int accentColor, boolean heavy, int[] stats, int capacity, int reloadTicks,
               int fireDelay, float damage, float recoil, String description, float[] muzzle, float[] core, float spinY) {
        this.id = id;
        this.displayName = displayName;
        this.classification = classification;
        this.rarity = rarity;
        this.rarityColor = rarityColor;
        this.unlockMissions = unlockMissions;
        this.defaultFinish = defaultFinish;
        this.defaultEnergy = defaultEnergy;
        this.accentColor = accentColor;
        this.heavy = heavy;
        this.stats = stats;
        this.capacity = capacity;
        this.reloadTicks = reloadTicks;
        this.fireDelay = fireDelay;
        this.damage = damage;
        this.recoil = recoil;
        this.description = description;
        this.muzzle = muzzle;
        this.core = core;
        this.spinY = spinY;
    }

    public static WeaponType byIndex(int i) {
        WeaponType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
