package com.ironlung.block;

public enum ControlType {
    HELM(true, 3),
    CAMERA(true, 4),
    CHART(false, 0),
    BREAKER(true, 1),
    BALLAST(true, 1),
    PRESSURE_GAUGE(true, 2),
    DEPTH_GAUGE(true, 2),
    PROXIMITY(true, 2),
    SEALANT(false, 0),
    HATCH(false, 0);

    /** Whether this control has a powered/unpowered look. */
    public final boolean powerable;
    /** Faint glow given off while powered. */
    public final int glow;

    ControlType(boolean powerable, int glow) {
        this.powerable = powerable;
        this.glow = glow;
    }

    /** Controls that open a screen on the client. */
    public boolean opensScreen() {
        return this == HELM || this == CAMERA || this == CHART || this == BALLAST;
    }
}
