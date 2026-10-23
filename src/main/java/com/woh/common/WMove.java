package com.woh.common;

/**
 * The World Over Heaven's moves, one per key slot: five pages of 1-4 (R turns the page). No Sneak combinations.
 * Two keys change with the situation (the "sneak" flag marks these alternates): while Reality Collapse (his domain)
 * is open, the domain key becomes the Collapse Finisher. Heaven Ascension is a toggle handled in the runner.
 *
 * tier = which damage setting the move's main blow uses (0 signature, 1 heavy, 2 ultimate, 3 final).
 */
public enum WMove {
    // ---- page 1: the fists
    ASCENDED_BARRAGE(0, false, "Ascended Barrage", 40, 70, 90, false, 1),
    OVERWRITE_BLOW(1, false, "Overwrite Blow", 90, 150, 110, true, 2),
    ERASURE_HOOK(2, false, "Erasure Hook", 70, 130, 72, true, 1),
    REWRITE_RUSH(3, false, "Rewrite Rush", 60, 100, 84, false, 1),
    // ---- page 2: reality overwrite
    REALITY_OVERWRITE(4, false, "Reality Overwrite", 120, 220, 96, true, 2),
    WOUNDS_UNDONE(5, false, "Wounds Undone", 0, 240, 56, false, 0),
    GRACE_OVER_HEAVEN(6, false, "Grace Over Heaven", 0, 260, 60, false, 0),
    NEGATION(7, false, "Negation", 0, 180, 40, false, 0),
    // ---- page 3: heaven's judgment
    HEAVENLY_LIGHTNING(8, false, "Heavenly Lightning", 80, 120, 70, true, 2),
    FOURTEEN_PHRASES(9, false, "Fourteen Phrases", 160, 320, 150, true, 2),
    REALITY_KNIVES(10, false, "Reality-Warping Knives", 50, 80, 48, false, 1),
    ROAD_TO_HEAVEN(11, false, "Road to Heaven", 70, 130, 72, true, 1),
    // ---- page 4: the world (time)
    TIME_STOP(12, false, "Time Stop", 200, 420, 150, true, 1),
    KNIFE_RAIN(13, false, "Time Stop: Knife Rain", 260, 520, 150, true, 2),
    STILLNESS(14, false, "Stillness", 60, 160, 56, false, 0),
    // ---- page 5: over heaven (ultimates)
    WORLDS_TRUTH(15, false, "This Is the World's Truth", 900, 1200, 260, true, 3),
    RETURN_OVER_HEAVEN(16, false, "Return Over Heaven", 0, 1200, 56, false, 0),
    HEAVEN_ASCENSION(17, false, "Heaven Ascension", 200, 400, 64, true, 0),
    REALITY_COLLAPSE(18, false, "Reality Collapse", 500, 2400, 130, true, 1),
    // ---- situational alternate
    COLLAPSE_FINISHER(18, true, "Reality Collapse: Overwrite", 350, 0, 110, true, 3);

    public final int slot;
    public final boolean sneak;
    public final String title;
    public final int cost;
    public final int cooldown;
    public final int end;
    public final boolean cutscene;
    public final int tier;

    WMove(int slot, boolean sneak, String title, int cost, int cooldown, int end, boolean cutscene, int tier) {
        this.slot = slot;
        this.sneak = sneak;
        this.title = title;
        this.cost = cost;
        this.cooldown = cooldown;
        this.end = end;
        this.cutscene = cutscene;
        this.tier = tier;
    }

    public static WMove byId(int id) {
        WMove[] v = values();
        return id >= 0 && id < v.length ? v[id] : ASCENDED_BARRAGE;
    }

    public static WMove of(int slot, boolean sneak) {
        WMove plain = null;
        for (WMove m : values()) {
            if (m.slot != slot) continue;
            if (m.sneak == sneak) return m;
            if (!m.sneak) plain = m;
        }
        return plain;
    }

    /** The move a key casts right now. The domain key gains its finisher while Reality Collapse is open. */
    public static WMove resolve(int slot, boolean inDomain, boolean ascended) {
        WMove m = of(slot, false);
        if (m == REALITY_COLLAPSE && inDomain) return COLLAPSE_FINISHER;
        return m;
    }

    /** The everyday moves: each one does at most damage.signature per target. */
    public boolean basic() {
        return this == ASCENDED_BARRAGE || this == REWRITE_RUSH || this == REALITY_KNIVES
                || this == ROAD_TO_HEAVEN || this == STILLNESS;
    }

    /** Reality-overwriting strikes: these are sure hits that cut through Infinity and the like. */
    public boolean overwrites() {
        return this == REALITY_OVERWRITE || this == OVERWRITE_BLOW || this == ERASURE_HOOK
                || this == WORLDS_TRUTH || this == COLLAPSE_FINISHER || this == KNIFE_RAIN;
    }

    /** The execute: erases a weakened target outright. */
    public boolean execute() {
        return this == REALITY_OVERWRITE || this == WORLDS_TRUTH || this == COLLAPSE_FINISHER;
    }

    public String label() {
        return title;
    }
}
