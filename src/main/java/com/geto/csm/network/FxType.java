package com.geto.csm.network;

public enum FxType {
    RIFT, VANISH, ORB_FORM, ACTION, BITE, TAIL_SWEEP, ROAR_BEAM,
    WORM_EMERGE, WORM_SWALLOW, WORM_SPIT, GUST, CENTI_BITE, PYRE_FLARE, PYRE_PULSE,
    KUCHI_DOMAIN, SHEARS, MOUTH_SLASH, FOXFIRE, KILLING_STONE, MIASMA, QUAKE_WAVE,
    UZUMAKI_GATHER, UZUMAKI_CORE, UZUMAKI_BEAM, SMALL_HIT, ABSORB;

    private static final FxType[] VALUES = values();

    public static FxType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SMALL_HIT;
    }
}
