package com.firestar.orbital.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The cannons answer The Shooting Star's Stellar Remote: hold it and press a cannon's key. Nothing of that mod is
 * touched; this only recognises its item by name, so it works with any version that keeps the name.
 */
public final class Remote {
    private Remote() {}

    public static final ResourceLocation STELLAR_REMOTE = new ResourceLocation("shooting_star_demo", "stellar_remote");

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && STELLAR_REMOTE.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    public static boolean holding(Player p) {
        return is(p.getMainHandItem()) || is(p.getOffhandItem());
    }
}
