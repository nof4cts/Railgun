package com.ironlung.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class IronLungConfig {
    private IronLungConfig() {}

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // ---- common / gameplay ----
    public static ForgeConfigSpec.IntValue OBJECTIVES;
    public static ForgeConfigSpec.IntValue HIDDEN_ANOMALIES;
    public static ForgeConfigSpec.IntValue MAP_SIZE;
    public static ForgeConfigSpec.DoubleValue EVENT_FREQUENCY;
    public static ForgeConfigSpec.DoubleValue HULL_DAMAGE;
    public static ForgeConfigSpec.DoubleValue MAX_SPEED;
    public static ForgeConfigSpec.IntValue SEALANT_CHARGES;
    public static ForgeConfigSpec.BooleanValue ENABLE_LEVIATHAN;
    public static ForgeConfigSpec.BooleanValue LETHAL_IMPLOSION;

    // ---- client / presentation ----
    public static ForgeConfigSpec.IntValue FEED_WIDTH;
    public static ForgeConfigSpec.IntValue FEED_FPS;
    public static ForgeConfigSpec.IntValue PHOTO_WIDTH;
    public static ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    public static ForgeConfigSpec.DoubleValue SHAKE_INTENSITY;
    public static ForgeConfigSpec.BooleanValue SCREEN_DISTORTION;
    public static ForgeConfigSpec.BooleanValue HUD_READOUT;
    public static ForgeConfigSpec.DoubleValue AMBIENT_VOLUME;

    static {
        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("mission");
        OBJECTIVES = c.comment("Number of survey sites to photograph per dive.").defineInRange("objectives", 4, 1, 8);
        HIDDEN_ANOMALIES = c.comment("Uncatalogued things placed in the ocean that are not on the chart.").defineInRange("hiddenAnomalies", 4, 0, 10);
        MAP_SIZE = c.comment("Width of the survey area in metres.").defineInRange("mapSize", 1100, 600, 3000);
        MAX_SPEED = c.comment("Maximum submarine speed in metres per second.").defineInRange("maxSpeed", 2.2, 0.5, 6.0);
        SEALANT_CHARGES = c.comment("Hull sealant charges available per dive.").defineInRange("sealantCharges", 3, 0, 10);
        c.pop();
        c.push("horror");
        EVENT_FREQUENCY = c.comment("Multiplier for how often unsettling events occur. 1.0 = default pacing.").defineInRange("eventFrequency", 1.0, 0.1, 4.0);
        HULL_DAMAGE = c.comment("Multiplier for hull damage from collisions, pressure and impacts.").defineInRange("hullDamage", 1.0, 0.0, 4.0);
        ENABLE_LEVIATHAN = c.comment("Allow the rare encounter with something very large.").define("enableLeviathan", true);
        LETHAL_IMPLOSION = c.comment("If the hull fails, the pilot dies. If false, the dive is simply lost.").define("lethalImplosion", true);
        c.pop();
        COMMON_SPEC = c.build();

        ForgeConfigSpec.Builder k = new ForgeConfigSpec.Builder();
        k.push("camera");
        FEED_WIDTH = k.comment("Horizontal resolution of the live camera feed (height is 3/4 of this). Lower is faster.").defineInRange("feedWidth", 160, 96, 320);
        FEED_FPS = k.comment("Target refresh rate of the live feed. The real thing is not smooth.").defineInRange("feedFps", 8, 2, 30);
        PHOTO_WIDTH = k.comment("Horizontal resolution of developed photographs.").defineInRange("photoWidth", 320, 160, 640);
        k.pop();
        k.push("effects");
        CAMERA_SHAKE = k.comment("Shake the view on hull impacts.").define("cameraShake", true);
        SHAKE_INTENSITY = k.defineInRange("shakeIntensity", 1.0, 0.0, 3.0);
        SCREEN_DISTORTION = k.comment("Static and darkness overlays during failures.").define("screenDistortion", true);
        HUD_READOUT = k.comment("Small status readout while inside the submarine.").define("hudReadout", true);
        AMBIENT_VOLUME = k.comment("Volume of the ambient drone and machinery loops.").defineInRange("ambientVolume", 1.0, 0.0, 2.0);
        k.pop();
        CLIENT_SPEC = k.build();
    }
}
