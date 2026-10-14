package com.gojo.rework.common;

/** Gojo's reworked techniques: whether each plays a cutscene, and how long it runs (ticks). Sent by ordinal. */
public enum GMove {
    RED("red", "Reversal: Red", "", false, 60),
    MAX_BLUE("max_blue", "Maximum Output: Blue", "", false, 112),
    MAX_RED("max_red", "Maximum Output: Red", "", false, 84),
    PURPLE("purple", "Hollow Purple", "虚式「茈」", false, 72),
    IMAGINARY("imaginary", "Imaginary Technique: Purple", "虚式「茈」", true, 200),
    PURPLE_200("purple_200", "200% Hollow Purple", "虚式「茈」", true, 236),
    NUKE("nuke", "Hollow Nuke", "", true, 250),
    DOMAIN("domain", "Domain Expansion: Unlimited Void", "無量空処", true, 156),
    DOMAIN_02("domain_02", "0.2-Second Domain", "無量空処", false, 44);

    public final String key;
    public final String title;
    public final String kanji;
    public final boolean cinematic;
    public final int length;

    GMove(String key, String title, String kanji, boolean cinematic, int length) {
        this.key = key;
        this.title = title;
        this.kanji = kanji;
        this.cinematic = cinematic;
        this.length = length;
    }

    public static GMove byId(int id) {
        GMove[] v = values();
        return id >= 0 && id < v.length ? v[id] : RED;
    }
}
