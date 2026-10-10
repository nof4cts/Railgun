package com.firestar.santoryu.common;

/** The shared beat sheet, in ticks from the cast (20 per second). */
public final class Beats {
    public static final int BLEND_IN = 8;
    public static final int BLEND_OUT = 16;

    // Oni Giri: cross the arms, crouch, vanish through, the cut lands as he straightens up behind them
    public static final int ONI_DASH = 7;
    public static final int ONI_HIT = 9;
    public static final double ONI_RANGE = 11.0;

    // Tatsu Maki: one turn of the body, the column spins up, three pulses, it lets go
    public static final int TATSU_FIRST = 6;
    public static final int TATSU_EVERY = 6;
    public static final int TATSU_PULSES = 5;

    // Sanjuroku Pound Ho: two swords drawn back, the air cannon fired
    public static final int POUND_FIRE = 9;
    public static final int POUND_FLIGHT = 18;
    public static final double POUND_SPEED = 2.4;

    // Shishi Sonson: the stance and the silence, the draw nobody sees, the sheath clicks home, then it cuts
    public static final int SHISHI_VANISH = 26;
    public static final int SHISHI_CLICK = 48;
    public static final double SHISHI_RANGE = 14.0;

    // Hyakuhachi Pound Ho: three swords wound back in a full turn, the 108 cannon
    public static final int HYAKU_FIRE = 48;
    public static final int HYAKU_FLIGHT = 22;
    public static final double HYAKU_SPEED = 3.0;

    // Kokujo O Tatsu Maki: haki blackens the blades, the black dragon tornado
    public static final int KOKUJO_FORM = 36;
    public static final int KOKUJO_EVERY = 4;
    public static final int KOKUJO_LAST = 82;
    public static final int KOKUJO_BURST = 86;

    // Sanzen Sekai: blades spun like windmills, the charge, the three thousand worlds
    public static final int SANZEN_DASH = 92;
    public static final int SANZEN_HIT = 102;
    public static final double SANZEN_RANGE = 16.0;

    // Asura: the aura, the three faces and nine swords, the vanish, Ichibugin
    public static final int ASURA_AURA = 20;
    public static final int ASURA_MANIFEST = 66;
    public static final int ASURA_DASH = 152;
    public static final int ASURA_HIT = 164;
    public static final double ASURA_RANGE = 18.0;

    // Rengoku Oni Giri: Enma drains, the conqueror's wave, black lightning, the cross
    public static final int RENGOKU_DRAIN = 10;
    public static final int RENGOKU_CONQUER = 46;
    public static final int RENGOKU_DASH = 118;
    public static final int RENGOKU_HIT = 126;
    public static final double RENGOKU_RANGE = 16.0;

    // 103 Mercies Dragon Damnation: the king's haki, the climb, the serpent, the fall
    public static final int DRAGON_CONQUER = 52;
    public static final int DRAGON_LEAP = 182;
    public static final int DRAGON_HIT = 202;
    public static final double DRAGON_RANGE = 24.0;

    private Beats() {}

    /** The ticks on which a beat lands for anyone following along: card in, card out. */
    public static int cardStart(Move m) {
        return switch (m) {
            case SHISHI_SONSON -> 6;
            case HYAKUHACHI -> 14;
            case KOKUJO -> 12;
            case SANZEN_SEKAI -> 40;
            case ASURA -> ASURA_MANIFEST + 6;
            case RENGOKU -> RENGOKU_CONQUER + 14;
            case DRAGON -> 112;
            default -> -1;
        };
    }

    public static int cardLength(Move m) {
        return switch (m) {
            case SHISHI_SONSON -> 18;
            case HYAKUHACHI, KOKUJO -> 22;
            case SANZEN_SEKAI -> 32;
            case ASURA, RENGOKU -> 40;
            case DRAGON -> 46;
            default -> 0;
        };
    }
}
