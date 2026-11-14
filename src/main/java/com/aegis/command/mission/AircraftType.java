package com.aegis.command.mission;

/** Fictional strike aircraft. Speeds are blocks per tick. */
public enum AircraftType {
    KESTREL("KESTREL A-9", "VIPER-1", "MULTIROLE STRIKE", 1.4, 3, 6.0, 0.0, 4.2f, 0xFF8FA3B8, 4.0f,
            new int[]{7, 4, 3}),
    GOLIATH("GOLIATH B-4", "ANVIL-2", "HEAVY CARPET BOMBER", 1.0, 8, 4.5, 0.0, 4.6f, 0xFF7A7F68, 4.6f,
            new int[]{4, 10, 8}),
    WRAITH("WRAITH S-2", "SHADE-3", "STEALTH CLUSTER STRIKE", 2.0, 6, 1.4, 0.13, 3.6f, 0xFF3A3D45, 4.2f,
            new int[]{10, 6, 6});

    public static final String[] STAT_NAMES = {"SPEED", "PAYLOAD", "SPREAD"};

    public final String displayName;
    public final String callsign;
    public final String role;
    public final double speed;
    public final int bombs;
    /** Along-track spacing between impacts, in blocks. */
    public final double spacing;
    /** Cross-track release velocity spread (cluster pattern). */
    public final double lateral;
    public final float power;
    public final int tint;
    public final float scale;
    public final int[] stats;

    AircraftType(String displayName, String callsign, String role, double speed, int bombs, double spacing,
                 double lateral, float power, int tint, float scale, int[] stats) {
        this.displayName = displayName;
        this.callsign = callsign;
        this.role = role;
        this.speed = speed;
        this.bombs = bombs;
        this.spacing = spacing;
        this.lateral = lateral;
        this.power = power;
        this.tint = tint;
        this.scale = scale;
        this.stats = stats;
    }

    public static AircraftType byIndex(int i) {
        AircraftType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
