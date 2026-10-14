package com.firestar.styles;

/** The fighting styles a player can switch between. Saved by ordinal: only ever append. */
public enum Style {
    NONE("No Style", "", "無", 0x9AA3B0, "Fight with your fists. Pick a style to take up a technique.", new String[0]),
    HEAVEN("Made in Heaven", "Stand", "天国", 0x7CFFD4,
            "A Stand that accelerates time itself: blinding speed, light-trail dashes, knife storms, and the Universe Reset.",
            new String[]{"Q  Summon the Stand", "LMB  Punch", "E R T Y G Z X C V H  Stand moves"}),
    INSTINCT("Silver Instinct", "Awakening", "銀の本能", 0xE8F4FF,
            "Instinct that moves before thought. Awaken to Omen, then Zenith: auto-dodge, counters, ki waves, the sphere, the final beam, and flight.",
            new String[]{"G  Awaken / Zenith (sneak+G revert)", "Double-jump  Fly while awakened", "LMB  Strike combo", "E R T Y Z X C V H  Techniques"}),
    SWORDS("Santoryu", "Three Swords", "三刀流", 0x5FD38A,
            "The way of the demon swordsman: three blades, one in the teeth. Cuts that carve the land itself.",
            new String[]{"Hold a katana", "Z / X  Choose technique", "R  Cut"}),
    ROSE("Requiem Rose", "Divine Reaper", "冥薔薇", 0xFF6FAE,
            "A reaper's grace in rose and black: ascend, call the energy scythe, and cut through flesh, air and sky.",
            new String[]{"G  Ascend (sneak+G release)", "LMB  Reap chain", "E R T Y Z X H  Techniques"}),
    SAGE("Celestial Sage", "Sage God", "天仙", 0xF2C14E,
            "A mortal who takes up the heavens: nine black orbs, a staff of night, falling stars, a giant of light, and the eclipse.",
            new String[]{"G  Heaven's Mantle (G again: let go)", "LMB  Staff rite", "E R T Y Z X H  Rites"});

    public final String title;
    public final String kind;
    public final String kanji;
    public final int color;
    public final String description;
    public final String[] keys;

    Style(String title, String kind, String kanji, int color, String description, String[] keys) {
        this.title = title;
        this.kind = kind;
        this.kanji = kanji;
        this.color = color;
        this.description = description;
        this.keys = keys;
    }

    public static Style byId(int id) {
        Style[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }
}
