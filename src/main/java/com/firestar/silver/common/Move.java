package com.firestar.silver.common;

/**
 * The whole kit, with the beat sheet both sides play from (see {@link Beats}). {@code form} is the lowest awakening the
 * move needs: 0 any, 1 Omen, 2 Zenith.
 */
public enum Move {
    STRIKE("strike", "Strike", "", "", 'M', 6, false, 9, 0xFFFFFF, 0),
    HEAVY("heavy", "Crescent Kick", "", "", 'R', 80, false, 20, 0xDDEBFF, 0),
    VOLLEY("volley", "Starfall Volley", "", "", 'E', 160, false, 40, 0x8FD8FF, 0),
    VANISH("vanish", "Afterimage Step", "", "", 'T', 60, false, 10, 0xE8F4FF, 0),
    WAVE("wave", "Gathered Wave", "集気波", "SILVER INSTINCT", 'Y', 300, true, 84, 0x7FD0FF, 0),
    FLURRY("flurry", "Silver Serpent Flurry", "銀龍乱舞", "SILVER INSTINCT", 'Z', 240, true, 60, 0xC8E6FF, 1),
    METEOR("meteor", "Meteor Combination", "流星連撃", "SILVER INSTINCT", 'X', 300, true, 74, 0xFFE6A8, 1),
    RUSH("rush", "Instinct Rush", "本能乱撃", "SILVER INSTINCT", 'C', 360, true, 66, 0xB8C8FF, 2),
    SPHERE("sphere", "Gathered Sphere", "万象の気玉", "SILVER INSTINCT", 'V', 900, true, 150, 0x9FE0FF, 0),
    FINAL("final", "Final Radiance", "終焉の光", "SILVER ZENITH", 'H', 1200, true, 140, 0xE0F2FF, 2),
    AWAKEN_OMEN("awaken_omen", "Instinct Omen", "本能の兆し", "AWAKENING", 'G', 60, true, 110, 0x9CC8FF, 0),
    AWAKEN_ZENITH("awaken_zenith", "Silver Zenith", "銀の極み", "AWAKENING", 'G', 60, true, 150, 0xE8F4FF, 1),
    REVERT("revert", "Revert", "", "", 'G', 40, false, 16, 0xAAAAAA, 0);

    public final String key;
    public final String title;
    public final String kanji;
    public final String style;
    public final char bind;
    public final int cooldown;
    public final boolean cinematic;
    public final int length;
    public final int color;
    public final int form;

    Move(String key, String title, String kanji, String style, char bind, int cooldown, boolean cinematic, int length, int color, int form) {
        this.key = key;
        this.title = title;
        this.kanji = kanji;
        this.style = style;
        this.bind = bind;
        this.cooldown = cooldown;
        this.cinematic = cinematic;
        this.length = length;
        this.color = color;
        this.form = form;
    }

    public static Move byId(int id) {
        Move[] all = values();
        return id >= 0 && id < all.length ? all[id] : STRIKE;
    }

    public static final Move[] KIT = {HEAVY, VOLLEY, VANISH, WAVE, FLURRY, METEOR, RUSH, SPHERE, FINAL};

    /** Ticks the fighter is held in place for the cinematic. */
    public int anchorUntil() {
        return switch (this) {
            case WAVE -> length;
            case SPHERE -> Beats.SPHERE_THROW;
            case FINAL -> length;
            case AWAKEN_OMEN, AWAKEN_ZENITH -> length;
            default -> 0;
        };
    }
}
