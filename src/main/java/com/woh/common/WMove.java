package com.woh.common;

/**
 * Every move, one per key slot: five pages of 1-4 (R turns the page). No Sneak combinations. Three keys change with
 * the situation (the "sneak" flag marks these alternates): inside his domain, Domain Expansion becomes the Domain
 * Finisher and Dismantle becomes the Domain Barrage; in Overdrive, the Overdrive key becomes Domain Apocalypse.
 *
 * canon = established in the manga (the technique itself; the cinematic staging is always our own).
 * tier  = which damage setting the move's main blow uses (0 signature, 1 heavy, 2 ultimate, 3 final).
 */
public enum WMove {
    WORLD_BREAKER(0, false, "Black Flash: World Breaker", false, 420, 900, 142, true, 2),
    RICOCHET(1, false, "Black Flash: Ricochet", false, 240, 360, 80, true, 2),
    RESONANCE(2, false, "Black Flash: Resonance", false, 260, 400, 96, true, 2),
    PIERCING_BLOOD(3, false, "Piercing Blood", true, 50, 40, 30, false, 0),
    HOMING_BLOOD(4, false, "Piercing Blood: Homing", true, 110, 120, 40, false, 1),
    SUPERNOVA(5, false, "Supernova", true, 140, 200, 62, true, 1),
    BLOOD_STORM(6, false, "Blood Storm", false, 220, 400, 112, true, 1),
    CRIMSON_EXECUTION(7, false, "Crimson Execution", false, 200, 300, 64, true, 2),
    BLOOD_APOCALYPSE(8, false, "Apocalypse", false, 420, 900, 172, true, 2),
    DISMANTLE(9, false, "Dismantle", true, 40, 30, 30, false, 0),
    THOUSAND_SLASHES(10, false, "Dismantle: Thousand Slashes", false, 240, 400, 92, true, 1),
    CLEAVE(11, false, "Cleave", true, 70, 60, 30, false, 1),
    WORLD_SPLITTER(12, false, "Cleave: World Splitter", false, 360, 700, 102, true, 2),
    METEOR(13, false, "Meteoric Crash", false, 260, 500, 94, true, 2),
    DOMAIN(14, false, "Domain Expansion", true, 500, 2400, 122, true, 1),
    DOMAIN_FINISHER(14, true, "Domain Finisher", false, 300, 0, 92, true, 3),
    DOMAIN_BARRAGE(9, true, "Domain Barrage", false, 260, 600, 102, true, 2),
    OVERDRIVE(15, false, "Modulo Overdrive: Unrestricted", false, 300, 1800, 62, true, 0),
    FINAL_JUDGMENT(16, false, "The Final Judgment: World Ender", false, 800, 3600, 234, true, 3),
    RCT(17, false, "Reverse Cursed Technique x100", false, 0, 600, 44, false, 0),
    THE_END(18, false, "The End", false, 0, 1200, 96, true, 3),
    AS_GOD(19, false, "As God As My Witness", false, 0, 1200, 40, false, 0),
    FOURTH_WALL(20, false, "4th Wall Breaker", false, 0, 0, 80, true, 0),
    DOMAIN_APOCALYPSE(15, true, "Domain Apocalypse", false, 950, 4800, 286, true, 3);

    public final int slot;
    public final boolean sneak;
    public final String title;
    public final boolean canon;
    public final int cost;
    public final int cooldown;
    public final int end;
    public final boolean cutscene;
    public final int tier;

    WMove(int slot, boolean sneak, String title, boolean canon, int cost, int cooldown, int end, boolean cutscene, int tier) {
        this.slot = slot;
        this.sneak = sneak;
        this.title = title;
        this.canon = canon;
        this.cost = cost;
        this.cooldown = cooldown;
        this.end = end;
        this.cutscene = cutscene;
        this.tier = tier;
    }

    public static WMove byId(int id) {
        WMove[] v = values();
        return id >= 0 && id < v.length ? v[id] : DISMANTLE;
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

    /** The move a key casts right now: one move per key, three of which change with the situation (no Sneak). */
    public static WMove resolve(int slot, boolean inDomain, boolean overdrive) {
        WMove m = of(slot, false);
        if (m == DOMAIN && inDomain) return DOMAIN_FINISHER;
        if (m == DISMANTLE && inDomain) return DOMAIN_BARRAGE;
        if (m == OVERDRIVE && overdrive) return DOMAIN_APOCALYPSE;
        return m;
    }

    /** The everyday moves: each one does at most damage.signature (default 70, most of an iron golem) per target. */
    public boolean basic() {
        return this == PIERCING_BLOOD || this == HOMING_BLOOD || this == SUPERNOVA || this == DISMANTLE || this == CLEAVE;
    }

    /** Sukuna's technique (Shrine): these cut through Gojo's Infinity. */
    public boolean shrine() {
        return this == DISMANTLE || this == THOUSAND_SLASHES || this == CLEAVE || this == WORLD_SPLITTER || this == DOMAIN_BARRAGE;
    }

    public String label() {
        return (canon ? "" : "*") + title;
    }
}
