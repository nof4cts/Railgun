package com.firestar.styles;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One mod, five fighting styles: Made in Heaven, Silver Instinct, Santoryu, Requiem Rose and Celestial Sage. Each style is its own package with its
 * own assets; this class brings them up together and owns the switch between them.
 */
@Mod(StylesMod.MODID)
public final class StylesMod {
    public static final String MODID = "fightingstyles";
    public static final Logger LOGGER = LoggerFactory.getLogger("FightingStyles");

    public StylesMod() {
        new com.firestar.mih.MihMod();
        new com.firestar.silver.SilverMod();
        new com.firestar.santoryu.SantoryuMod();
        new com.firestar.rose.RoseMod();
        new com.firestar.sage.SageMod();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, StylesPrefs.SPEC,
                "fightingstyles-client.toml");
        StyleNet.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
