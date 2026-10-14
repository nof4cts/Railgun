package com.firestar.styles;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Choices that hold for every style at once, kept in fightingstyles-client.toml and set from the style menu (N). For now:
 * whether the big techniques play their cutscenes. With cutscenes off every technique still happens in full, seen from
 * the player's own camera.
 */
public final class StylesPrefs {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue CUTSCENES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        CUTSCENES = b.comment("Play the cutscenes of every style's big techniques. Toggle it from the style menu (N).").define("cutscenes", true);
        SPEC = b.build();
    }

    private StylesPrefs() {}

    public static boolean cutscenes() {
        try {
            return CUTSCENES.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static void setCutscenes(boolean on) {
        CUTSCENES.set(on);
        CUTSCENES.save();
    }
}
