package com.sixpaths.network;

public enum CutType {
    CHIBAKU, DESCENT, SAMSARA, KING, CANNON, SOUL, ASCEND, SUMMON;

    private static final CutType[] VALUES = values();

    public static CutType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SUMMON;
    }
}
