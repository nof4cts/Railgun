package com.hakari.idg.network;

public enum FxType {
    FLICK, BALL_IMPACT, DOORS_SLAM, ROUGH_CHARGE, ROUGH_HIT, FEVER_KICK1, FEVER_KICK2,
    COUNTER_READY, COUNTER_SLAM, VISUAL_TICK,
    VOLLEY_FIST, VOLLEY_FINISH, RUSH_DASH, RUSH_SLAM, OVERWHELM_HIT, OVERWHELM_THROW,
    SURGE_LEAP, SURGE_SLAM, SURGE_KICK, RHYTHM,
    DOMAIN_COLLAPSE, DOMAIN_SHATTER, JACKPOT_END, SMALL_HIT;

    private static final FxType[] VALUES = values();

    public static FxType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SMALL_HIT;
    }
}
