package com.firestar.worldbreaker.client;

import com.firestar.worldbreaker.client.cinematic.Director;
import com.firestar.worldbreaker.network.C2SCall;
import com.firestar.worldbreaker.network.Net;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** One key: call the march toward where you are looking. Rebindable. */
public final class Keys {
    public static final KeyMapping CALL = new KeyMapping("key.worldbreaker.call", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.worldbreaker");

    private Keys() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CALL);
    }

    public static void handle() {
        Minecraft mc = Minecraft.getInstance();
        while (CALL.consumeClick()) {
            if (mc.player != null && mc.screen == null && !Director.active()) {
                Net.toServer(new C2SCall());
            }
        }
    }
}
