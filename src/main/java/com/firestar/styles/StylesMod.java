package com.firestar.styles;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One mod, three fighting styles: Made in Heaven, Silver Instinct and Santoryu. Each style is its own package with its
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
        StyleNet.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
