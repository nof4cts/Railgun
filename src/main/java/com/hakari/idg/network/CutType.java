package com.hakari.idg.network;

public enum CutType {
    DOMAIN, RIICHI, JACKPOT, MICRO_ROUGH, MICRO_OVERWHELM, MICRO_SURGE, MICRO_RUSH;

    private static final CutType[] VALUES = values();

    public static CutType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : MICRO_ROUGH;
    }
}
