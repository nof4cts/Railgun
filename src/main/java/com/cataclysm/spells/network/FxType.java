package com.cataclysm.spells.network;

public enum FxType {
    STAR_FALL, STAR_IMPACT, HOLE_FORM, HOLE_COLLAPSE, RIP_BLOCK, LANCE_SIGIL, LANCE_STRIKE;

    private static final FxType[] VALUES = values();

    public static FxType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : RIP_BLOCK;
    }
}
