package com.firestar.orbital.client;

import com.firestar.orbital.common.Cannon;
import com.firestar.orbital.common.Remote;
import com.firestar.orbital.network.C2SFire;
import com.firestar.orbital.network.Net;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** One key per cannon; they only fire while the Stellar Remote is in hand. Rebind them in Controls. */
public final class Keys {
    private Keys() {}

    public static final String CATEGORY = "key.categories.orbital_arsenal";
    public static final KeyMapping HELIOS = new KeyMapping("key.orbital_arsenal.helios", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping ANTIPODE = new KeyMapping("key.orbital_arsenal.antipode", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(HELIOS);
        event.register(ANTIPODE);
    }

    public static void handle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        while (HELIOS.consumeClick()) {
            fire(mc, Cannon.HELIOS);
        }
        while (ANTIPODE.consumeClick()) {
            fire(mc, Cannon.ANTIPODE);
        }
    }

    private static void fire(Minecraft mc, Cannon c) {
        if (!Remote.holding(mc.player)) {
            mc.player.displayClientMessage(Component.literal("Hold the Stellar Remote to call " + c.code + " " + c.title + ".")
                    .withStyle(st -> st.withColor(0x9AA3B0)), true);
            return;
        }
        Net.toServer(new C2SFire(c.ordinal()));
    }
}
