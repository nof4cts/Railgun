package com.yuji.common;

/**
 * Every move. Sixteen key slots (four pages of 1-4); Sneak + key gives the slot's second move. The Domain slot
 * becomes the Domain Finisher while the domain is open.
 *
 * canon = established in the manga (the technique itself; the cinematic staging is always our own).
 * tier  = which damage setting the move's main blow uses (0 signature, 1 heavy, 2 ultimate, 3 final).
 */
public enum YMove {
    PERFECT_IMPACT(0, false, "Black Flash: Perfect Impact", true, 140, 140, 52, true, 1),
    RELENTLESS_CHAIN(1, false, "Black Flash: Relentless Chain", false, 260, 320, 112, true, 2),
    VOID_SHATTER(2, false, "Black Flash: Void Shatter", false, 300, 400, 86, true, 2),
    WORLD_BREAKER(3, false, "Black Flash: World Breaker", false, 420, 900, 142, true, 2),
    PIERCING_BLOOD(4, false, "Piercing Blood", true, 50, 40, 30, false, 0),
    HOMING_BLOOD(4, true, "Piercing Blood: Homing", true, 110, 120, 40, false, 1),
    SUPERNOVA(5, false, "Supernova", true, 140, 200, 62, true, 1),
    BLOOD_STORM(6, false, "Blood Storm", false, 220, 400, 112, true, 1),
    CRIMSON_EXECUTION(7, false, "Crimson Execution", false, 200, 300, 64, true, 2),
    BLOOD_APOCALYPSE(7, true, "Apocalypse", false, 420, 900, 172, true, 2),
    DISMANTLE(8, false, "Dismantle", true, 40, 30, 30, false, 0),
    THOUSAND_SLASHES(8, true, "Dismantle: Thousand Slashes", false, 240, 400, 92, true, 1),
    CLEAVE(9, false, "Cleave", true, 70, 60, 30, false, 1),
    WORLD_SPLITTER(9, true, "Cleave: World Splitter", false, 360, 700, 102, true, 2),
    METEOR(10, false, "Meteoric Cursed-Energy Crash", false, 260, 500, 94, true, 2),
    CE_APOCALYPSE(11, false, "Cursed-Energy Apocalypse", false, 380, 900, 112, true, 2),
    DOMAIN(12, false, "Domain Expansion", true, 500, 2400, 122, true, 1),
    DOMAIN_FINISHER(12, true, "Domain Finisher", false, 300, 0, 92, true, 3),
    DOMAIN_BARRAGE(13, false, "Domain Barrage", false, 260, 600, 102, true, 2),
    OVERDRIVE(14, false, "Modulo Overdrive: Unrestricted", false, 300, 1800, 62, true, 0),
    FINAL_JUDGMENT(15, false, "The Final Judgment: World Ender", false, 800, 3600, 234, true, 3),
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

    YMove(int slot, boolean sneak, String title, boolean canon, int cost, int cooldown, int end, boolean cutscene, int tier) {
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

    public static YMove byId(int id) {
        YMove[] v = values();
        return id >= 0 && id < v.length ? v[id] : DISMANTLE;
    }

    public static YMove of(int slot, boolean sneak) {
        YMove plain = null;
        for (YMove m : values()) {
            if (m.slot != slot) continue;
            if (m.sneak == sneak) return m;
            if (!m.sneak) plain = m;
        }
        return plain;
    }

    public String label() {
        return (canon ? "" : "*") + title;
    }
}
