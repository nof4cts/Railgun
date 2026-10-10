package com.hakari.idg;

/** The six scrolls. Inside a Jackpot, each base scroll turns into its jackpot technique. */
public enum Technique {
    RESERVE_BALL("Reserve Ball", "Lucky Volley"),
    SHUTTER_DOORS("Shutter Doors", "Lucky Rushdown"),
    ROUGH_ENERGY("Rough Energy", "Overwhelming Luck"),
    FEVER_BREAKER("Fever Breaker", "Energy Surge"),
    COUNTER("Door Counter", "Rhythm"),
    DOMAIN("Domain Expansion: Idle Death Gamble", "Idle Death Gamble");

    public final String baseName;
    public final String jackpotName;

    Technique(String baseName, String jackpotName) {
        this.baseName = baseName;
        this.jackpotName = jackpotName;
    }
}
