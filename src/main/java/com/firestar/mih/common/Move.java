package com.firestar.mih.common;

/**
 * Made in Heaven's whole kit, YBA's keys and all, with the beat sheet both sides play from (see {@link Beats}). Ticks
 * are counted from the cast; the server lands damage on these ticks, the client lands the fist, sound, camera cut and
 * impact frames on the same ones.
 */
public enum Move {
    SUMMON("summon", "Summon", "", "", 'Q', 10, false, 22, 0xC8FFF0),
    PUNCH("punch", "Punch", "", "", 'M', 7, false, 9, 0xFFFFFF),
    BARRAGE("barrage", "Stand Barrage", "", "", 'E', 110, false, 52, 0x9CFFE0),
    FINISHER("finisher", "Barrage Finisher", "", "", 'R', 100, false, 24, 0xFFFFFF),
    KNIVES("knives", "Knife Throw", "", "", 'T', 120, false, 18, 0xDDE8FF),
    SPEED_SLICE("speed_slice", "Speed Slice", "光速の斬首", "MADE IN HEAVEN", 'Y', 240, true, 58, 0xFF5050),
    JUDGEMENT("judgement", "Heaven's Judgement", "天国の裁き", "MADE IN HEAVEN", 'G', 320, true, 100, 0xFFD86B),
    DOUBLE_ACCEL("double_accel", "Double Acceleration", "二重加速", "MADE IN HEAVEN", 'Z', 700, true, 50, 0x7CFFD4),
    INFINITE_SPEED("infinite_speed", "Infinite Speed", "無限の速さ", "MADE IN HEAVEN", 'X', 500, false, 14, 0xBFF6FF),
    LIGHTSPEED_RUSH("lightspeed_rush", "Lightspeed Rush", "光速突進", "", 'C', 140, false, 20, 0xFFFFFF),
    KNIFE_STORM("knife_storm", "Knife Storm", "ナイフの嵐", "MADE IN HEAVEN", 'V', 280, true, 46, 0xDDE8FF),
    TIME_ACCEL("time_accel", "Time Acceleration", "時は加速する", "MADE IN HEAVEN", 'H', 2400, true, 92, 0xFFE9A8),
    UNIVERSE_RESET("universe_reset", "Universe Reset", "一巡", "MADE IN HEAVEN", ' ', 0, true, 300, 0xFFFFFF);

    public final String key;
    public final String title;
    public final String kanji;
    public final String style;
    public final char bind;
    /** Cooldown in ticks. */
    public final int cooldown;
    public final boolean cinematic;
    public final int length;
    public final int color;

    Move(String key, String title, String kanji, String style, char bind, int cooldown, boolean cinematic, int length, int color) {
        this.key = key;
        this.title = title;
        this.kanji = kanji;
        this.style = style;
        this.bind = bind;
        this.cooldown = cooldown;
        this.cinematic = cinematic;
        this.length = length;
        this.color = color;
    }

    public static Move byId(int id) {
        Move[] all = values();
        return id >= 0 && id < all.length ? all[id] : PUNCH;
    }

    /** The moves shown on the HUD, in YBA's order. */
    public static final Move[] KIT = {BARRAGE, FINISHER, KNIVES, SPEED_SLICE, JUDGEMENT, DOUBLE_ACCEL, INFINITE_SPEED, LIGHTSPEED_RUSH, KNIFE_STORM,
            TIME_ACCEL};

    /** Ticks during which the user is held in place for the cinematic. */
    public int anchorUntil() {
        return switch (this) {
            case SPEED_SLICE -> Beats.SLICE_VANISH;
            case JUDGEMENT -> Beats.JUDGE_LUNGE;
            case DOUBLE_ACCEL, TIME_ACCEL, UNIVERSE_RESET, KNIFE_STORM -> length;
            default -> 0;
        };
    }
}
