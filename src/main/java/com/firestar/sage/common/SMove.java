package com.firestar.sage.common;

/**
 * The Celestial Sage's rites: key, cooldown (ticks), whether it plays a cutscene, and how long it runs (ticks).
 * Sent and saved by ordinal: only ever append.
 */
public enum SMove {
    STAFF("staff", "Staff Rite", "", 'M', 6, false, 16),
    ORBS("orbs", "Null Orbs", "虚珠", 'R', 140, false, 70),
    PULL("pull", "Heaven's Pull", "天引", 'E', 160, false, 40),
    MIRROR("mirror", "Mirror Step", "鏡歩", 'T', 90, false, 22),
    STARFALL("starfall", "Starfall", "星墜", 'Y', 600, true, 150),
    THORNWOOD("thornwood", "Thornwood", "棘樹界", 'Z', 420, false, 70),
    COLOSSUS("colossus", "Colossus", "巨神", 'X', 900, true, 150),
    ECLIPSE("eclipse", "Eternal Eclipse", "永劫日蝕", 'H', 2400, true, 240),
    MANTLE("mantle", "Heaven's Mantle", "天衣", 'G', 60, true, 130),
    UNMANTLE("unmantle", "Let Go", "", 'G', 40, false, 20);

    public final String key;
    public final String title;
    public final String kanji;
    public final char bind;
    public final int cooldown;
    public final boolean cinematic;
    public final int length;

    SMove(String key, String title, String kanji, char bind, int cooldown, boolean cinematic, int length) {
        this.key = key;
        this.title = title;
        this.kanji = kanji;
        this.bind = bind;
        this.cooldown = cooldown;
        this.cinematic = cinematic;
        this.length = length;
    }

    public static SMove byId(int id) {
        SMove[] v = values();
        return id >= 0 && id < v.length ? v[id] : STAFF;
    }

    /** The rites on the HUD, in key order. */
    public static final SMove[] KIT = {PULL, ORBS, MIRROR, STARFALL, THORNWOOD, COLOSSUS, ECLIPSE};
}
