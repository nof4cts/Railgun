package com.sixpaths;

/** Every technique, grouped by path. Cooldowns in ticks. */
public enum Ability {
    ALMIGHTY_PUSH("Almighty Push", "神羅天征", "DEVA PATH", 0xFFE8E4FF, 100, false),
    UNIVERSAL_PULL("Universal Pull", "万象天引", "DEVA PATH", 0xFFB9A8FF, 120, false),
    CHAKRA_RODS("Chakra Receivers", "黒い棒", "ALL PATHS", 0xFF8A8A96, 70, false),
    PLANETARY_DEVASTATION("Planetary Devastation", "地爆天星", "DEVA PATH · ULTIMATE", 0xFF9B6CFF, 2400, true),
    HEAVENLY_DESCENT("Almighty Push: Heavenly Descent", "神羅天征・天降", "DEVA PATH · ULTIMATE", 0xFFFFFFFF, 2400, true),
    ASURA_BARRAGE("Asura Barrage", "阿修羅道・弾幕", "ASURA PATH", 0xFFFF8A3A, 200, false),
    ASURA_CANNON("Asura Cannon", "阿修羅道・砲", "ASURA PATH", 0xFF5CE1FF, 300, false),
    SOUL_EXTRACTION("Soul Extraction", "人間道・魂抜き", "HUMAN PATH", 0xFF9FFFE8, 400, false),
    SUMMON_RHINO("Summon: Horned Behemoth", "畜生道・角獣", "ANIMAL PATH", 0xFFC8A070, 600, false),
    SUMMON_CENTIPEDE("Summon: Great Centipede", "畜生道・大百足", "ANIMAL PATH", 0xFFB04A3A, 600, false),
    SUMMON_HOUND("Summon: Three-Headed Hound", "畜生道・三頭犬", "ANIMAL PATH", 0xFF8C7AA8, 600, false),
    SUMMON_BIRD("Summon: Sky Roc", "畜生道・巨鳥", "ANIMAL PATH · MOUNT", 0xFF7AB8FF, 400, false),
    PRETA_ABSORPTION("Preta Absorption", "餓鬼道・封術吸印", "PRETA PATH", 0xFF7AFFB0, 400, false),
    KING_OF_HELL("King of Hell", "地獄道・閻魔", "NARAKA PATH", 0xFFFF5A3A, 1200, false),
    SAMSARA("Samsara of Heavenly Life", "外道・輪廻天生の術", "OUTER PATH · ULTIMATE", 0xFF8CFFB4, 6000, true),
    ASCENSION("Deva Ascension", "天道・昇天", "TRANSFORMATION", 0xFFFFF0C0, 3000, true),
    PATH_SIGHT("Shared Sight", "視界共有", "ALL PATHS", 0xFFB48CFF, 300, false);

    public final String title, jp, path;
    public final int color, cooldown;
    public final boolean ultimate;

    Ability(String title, String jp, String path, int color, int cooldown, boolean ultimate) {
        this.title = title; this.jp = jp; this.path = path; this.color = color; this.cooldown = cooldown; this.ultimate = ultimate;
    }

    private static final Ability[] VALUES = values();

    public static Ability byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }
}
