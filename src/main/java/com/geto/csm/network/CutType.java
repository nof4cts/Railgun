package com.geto.csm.network;

public enum CutType {
    SUMMON, UZUMAKI;

    private static final CutType[] VALUES = values();

    public static CutType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SUMMON;
    }
}
