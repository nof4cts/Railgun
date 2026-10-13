package com.firestar.rose.common;

/**
 * Requiem Rose: every technique, with its key, cooldown (ticks), whether it plays a cutscene, and its length (ticks).
 * Saved and sent by ordinal: only ever append.
 */
public enum RMove {
    REAP("reap", "Reap", "", 'M', 5, false, 14),
    CRESCENT("crescent", "Crescent Reaper", "月鎌", 'R', 100, false, 40),
    TETHER("tether", "Rose Tether", "薔薇縛", 'E', 160, false, 36),
    STEP("step", "Dusk Step", "黄昏歩", 'T', 70, false, 16),
    RAY("ray", "Calamity Ray", "災禍光", 'Y', 320, true, 148),
    REQUIEM("requiem", "Thousand Requiem", "千鎮魂", 'Z', 380, true, 80),
    RIFT("rift", "Severance Rift", "断界", 'X', 440, true, 112),
    LAST_BLOOM("last_bloom", "Last Bloom", "終ノ薔薇", 'H', 1400, true, 210),
    ASCEND("ascend", "Ascension", "昇華", 'G', 60, true, 150),
    RELEASE("release", "Release", "", 'G', 40, false, 20);

    public final String key;
    public final String title;
    public final String kanji;
    public final char bind;
    public final int cooldown;
    public final boolean cinematic;
    public final int length;

    RMove(String key, String title, String kanji, char bind, int cooldown, boolean cinematic, int length) {
        this.key = key;
        this.title = title;
        this.kanji = kanji;
        this.bind = bind;
        this.cooldown = cooldown;
        this.cinematic = cinematic;
        this.length = length;
    }

    public static RMove byId(int id) {
        RMove[] all = values();
        return id >= 0 && id < all.length ? all[id] : REAP;
    }

    /** The techniques on the HUD, in key order. */
    public static final RMove[] KIT = {TETHER, CRESCENT, STEP, RAY, REQUIEM, RIFT, LAST_BLOOM};

    /** Until which tick the reaper is held in place by the move. */
    public int anchorUntil() {
        return switch (this) {
            case RAY -> RBeats.RAY_END;
            case RIFT -> RBeats.RIFT_CUT + 4;
            case ASCEND -> length;
            default -> 0;
        };
    }
}
