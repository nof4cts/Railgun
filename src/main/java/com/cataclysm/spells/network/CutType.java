package com.cataclysm.spells.network;

public enum CutType {
    STAR, HOLE, LANCE;

    private static final CutType[] VALUES = values();

    public static CutType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : STAR;
    }
}
