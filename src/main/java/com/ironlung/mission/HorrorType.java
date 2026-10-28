package com.ironlung.mission;

/** Every unsettling thing the director can do. Ordinal is the network id. */
public enum HorrorType {
    CREAK(false),
    GROAN(false),
    KNOCK(false),
    IMPACT(true),
    SCRAPE(true),
    PRESSURE_SPIKE(false),
    FLICKER(false),
    BLACKOUT(true),
    CAMERA_GLITCH(false),
    SILHOUETTE(true),
    PROXIMITY_GHOST(false),
    SILENCE(false),
    WAIL(false),
    LEVIATHAN(true),
    EYES(true),
    HATCH_KNOCK(true),
    PHOTO_ANOMALY(false),
    COLLISION(false),
    JUMPSCARE_PRIME(false),
    JUMPSCARE(true),
    FINALE_CHAOS(true),
    GRABBED(true),
    GORE(true);

    /** Major events share a long cooldown so they never stack. */
    public final boolean major;

    HorrorType(boolean major) {
        this.major = major;
    }

    public static HorrorType byId(int id) {
        HorrorType[] v = values();
        return id >= 0 && id < v.length ? v[id] : CREAK;
    }
}
