package com.sixpaths.network;

public enum FxType {
    PUSH, PULL, ROD_THROW, ROD_HIT, CORE_THROW, RIP_BLOCK, SPHERE_SEAL, DESCENT_BLAST, DESCENT_RISE,
    MISSILE_LAUNCH, MISSILE_BOOM, CANNON_CHARGE, CANNON_BEAM, SOUL_GRAB, SOUL_TORN,
    SUMMON_SEAL, BEAST_ACTION, BEAST_HIT, HOUND_SPLIT, BEAST_VANISH,
    PRETA_ON, PRETA_DRINK, KING_RISE, SAMSARA, ASCEND, SIGHT;

    private static final FxType[] VALUES = values();

    public static FxType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : BEAST_HIT;
    }
}
