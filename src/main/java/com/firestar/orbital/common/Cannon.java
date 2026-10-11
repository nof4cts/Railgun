package com.firestar.orbital.common;

/**
 * The two cannons and the timeline each strike plays on, in ticks from the moment the button is pressed. Client and
 * server read the same numbers, so the film, the world effects and the destruction land on the same tick.
 */
public enum Cannon {
    /**
     * OA-01 Helios: a ring of orbital mirrors unfolds round a focusing lens and pours the sun down a single white-gold
     * lance; for a few seconds after it lands, the lance follows the caller's crosshair and cuts a molten trench.
     */
    HELIOS("Helios", "OA-01", 0xFFD27A, 392, 332, 332 + 170, 332 + 170 + 30),
    /**
     * OA-02 Antipode: the sky over the mark cracks, a breach opens beside the Earth and a mirror Earth slides out of it;
     * where the two touch, the other world's land comes down upside down onto the mark and the ground there is
     * exchanged for its own, every hill a pit and every pit a hill.
     */
    ANTIPODE("Antipode", "OA-02", 0xD0306A, 440, 364, 364 + 100, 364 + 150);

    public final String title, code;
    public final int color;
    /** Length of the caller's film. */
    public final int film;
    /** When the strike lands. */
    public final int impact;
    /** Helios: the lance stops following the crosshair. Antipode: the exchange of land is finished. */
    public final int hold;
    /** The strike is over. */
    public final int end;

    Cannon(String title, String code, int color, int film, int impact, int hold, int end) {
        this.title = title;
        this.code = code;
        this.color = color;
        this.film = film;
        this.impact = impact;
        this.hold = hold;
        this.end = end;
    }

    public Cannon next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Cannon of(int i) {
        return values()[Math.floorMod(i, values().length)];
    }
}
