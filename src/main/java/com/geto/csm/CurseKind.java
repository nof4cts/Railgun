package com.geto.csm;

/** The roster. Grade, lifetime and summon cooldown per curse. */
public enum CurseKind {
    WYRM("Gloomscale Wyrm", "冥鱗竜", "SPECIAL GRADE", 0xFFFF7A2A, 20 * 150, 20 * 25, true),
    WORM("Maw Burrower", "喰穴蟲", "GRADE 1", 0xFFB06BC0, 20 * 30, 20 * 12, false),
    RAY("Veil Ray", "帳鱝", "GRADE 2 · MOUNT", 0xFF4FE6E0, 20 * 300, 20 * 8, false),
    CENTIPEDE("Cinder Centipedes", "煤百足", "GRADE 3 · SWARM", 0xFF9CFF3A, 20 * 40, 20 * 10, false),
    PYRE("Pyre Wraith", "焚骸", "GRADE 1 · 120°C → 3000°C", 0xFFFF5A1A, 20 * 45, 20 * 20, false),
    KUCHISAKE("Kuchisake-onna", "口裂け女", "SPECIAL GRADE · FOLKLORE", 0xFFE0283C, 20 * 60, 20 * 30, true),
    TAMAMO("Tamamo-no-Mae", "玉藻前", "SPECIAL GRADE · FOLKLORE", 0xFFF2C14E, 20 * 60, 20 * 45, true),
    NAMAZU("Ōnamazu", "大鯰", "GRADE 1 · FOLKLORE", 0xFFC9B26A, 20 * 40, 20 * 25, false);

    public final String title, jp, grade;
    public final int color, lifetime, cooldown;
    public final boolean special;

    CurseKind(String title, String jp, String grade, int color, int lifetime, int cooldown, boolean special) {
        this.title = title; this.jp = jp; this.grade = grade; this.color = color;
        this.lifetime = lifetime; this.cooldown = cooldown; this.special = special;
    }

    private static final CurseKind[] VALUES = values();

    public static CurseKind byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }
}
