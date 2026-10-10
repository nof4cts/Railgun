package com.firestar.santoryu.client.fx;

import net.minecraft.world.phys.Vec3;

/**
 * A strip of light (or ink) along a spine. Width and alpha per point; {@code sides} null means the strip turns to face
 * the camera, otherwise it lies flat along the given direction at each point (a crescent seen from the side vanishes,
 * like a real flying slash).
 */
public final class Ribbon {
    public enum Style { GLOW, INK }

    public final Vec3[] spine;
    public final Vec3[] sides;
    public final float[] width;
    public final float[] alpha;
    public final int rgb;
    public final Style style;

    public Ribbon(Vec3[] spine, Vec3[] sides, float[] width, float[] alpha, int rgb, Style style) {
        this.spine = spine;
        this.sides = sides;
        this.width = width;
        this.alpha = alpha;
        this.rgb = rgb;
        this.style = style;
    }
}
