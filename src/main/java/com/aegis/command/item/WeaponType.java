package com.aegis.command.item;

/**
 * The three arsenal weapons. Stats are fictional 0-10 game attributes used by the UI;
 * the gameplay numbers (damage, rate, capacity) live alongside them.
 */
public enum WeaponType {
    PHANTOM_9("phantom_9", "PHANTOM-9", "PRECISION ENERGY RIFLE", "RARE", 0xFF4FA3FF, 0,
            0, 0,
            new int[]{7, 6, 8, 10, 6},
            24, 34, 4, 9f, 0.55f,
            "A whisper-quiet coherent-beam rifle. A cyan resonance core charges each pulse; the shot lands the instant you fire.",
            new float[]{8f, 8.5f, -14.1f}, new float[]{8f, 8.8f, 7f}),
    TITAN_X("titan_x", "TITAN-X", "HEAVY ARC CANNON", "LEGENDARY", 0xFFFF9A2E, 3,
            1, 1,
            new int[]{10, 2, 4, 7, 2},
            4, 60, 0, 22f, 1.6f,
            "An armoured siege cannon. Hold to build charge across three plasma chambers, release to throw an arc shell that ruptures on impact.",
            new float[]{8f, 8.5f, -14.1f}, new float[]{8f, 8.5f, 5.5f}),
    SPECTRE_7("spectre_7", "SPECTRE-7", "COMPACT PLASMA SMG", "EPIC", 0xFFB46CFF, 1,
            2, 2,
            new int[]{4, 10, 6, 5, 9},
            40, 28, 2, 3.5f, 0.25f,
            "A lightweight plasma repeater with adaptive digital circuitry. Hold to stream violet bolts at a blistering cycle rate.",
            new float[]{8f, 8.3f, -9f}, new float[]{8f, 8.5f, 5f});

    public static final String[] STAT_NAMES = {"POWER", "FIRE RATE", "STABILITY", "RANGE", "CAPACITY"};

    public static final String[] FINISH_NAMES = {"OBSIDIAN", "GUNMETAL", "ARCTIC", "SOLAR GOLD"};
    public static final int[] FINISH_COLORS = {0x3A3E46, 0x8A8F98, 0xDCE1E8, 0xD2A64C};

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

    WeaponType(String id, String displayName, String classification, String rarity, int rarityColor, int unlockMissions,
               int defaultFinish, int defaultEnergy, int[] stats, int capacity, int reloadTicks, int fireDelay,
               float damage, float recoil, String description, float[] muzzle, float[] core) {
        this.id = id;
        this.displayName = displayName;
        this.classification = classification;
        this.rarity = rarity;
        this.rarityColor = rarityColor;
        this.unlockMissions = unlockMissions;
        this.defaultFinish = defaultFinish;
        this.defaultEnergy = defaultEnergy;
        this.stats = stats;
        this.capacity = capacity;
        this.reloadTicks = reloadTicks;
        this.fireDelay = fireDelay;
        this.damage = damage;
        this.recoil = recoil;
        this.description = description;
        this.muzzle = muzzle;
        this.core = core;
    }

    public static WeaponType byIndex(int i) {
        WeaponType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
