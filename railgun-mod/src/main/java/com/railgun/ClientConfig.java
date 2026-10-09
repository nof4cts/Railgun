package com.railgun;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client options. Turn off impactFrames if you are sensitive to flashing light. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue IMPACT_FRAMES;
    public static final ForgeConfigSpec.BooleanValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.BooleanValue HEAVY_PARTICLES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        IMPACT_FRAMES = b.comment("Invert/black-and-white strobing impact frames. WARNING: rapid flashing. Disable for photosensitivity.")
                .define("impactFrames", true);
        SCREEN_SHAKE = b.comment("Camera shake and FOV punch.").define("screenShake", true);
        HEAVY_PARTICLES = b.comment("Spawn lots of particles along the beam and at impact.").define("heavyParticles", true);
        SPEC = b.build();
    }
    private ClientConfig() {}
}
