package com.firestar.santoryu.common;

/** How many blades a technique asks for, and whether one of them must be Enma. */
public enum Need {
    ONE(1, false), TWO(2, false), THREE(3, false), ENMA(3, true);

    public final int blades;
    public final boolean enma;

    Need(int blades, boolean enma) {
        this.blades = blades;
        this.enma = enma;
    }
}
