package com.firestar.santoryu.client;

import com.firestar.santoryu.client.cinematic.Director;
import com.firestar.santoryu.common.Move;
import com.firestar.santoryu.network.C2SCast;
import com.firestar.santoryu.network.Net;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    private static final String CATEGORY = "key.categories.santoryu";
    public static final KeyMapping CAST = new KeyMapping("key.santoryu.cast", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping NEXT = new KeyMapping("key.santoryu.next", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping PREV = new KeyMapping("key.santoryu.prev", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    private Keys() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CAST);
        event.register(NEXT);
        event.register(PREV);
    }

    public static void handle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (!com.firestar.styles.client.ClientStyles.mine(com.firestar.styles.Style.SWORDS)) {
            while (NEXT.consumeClick()) {
            }
            while (PREV.consumeClick()) {
            }
            while (CAST.consumeClick()) {
            }
            return;
        }
        while (NEXT.consumeClick()) {
            Hud.select(1);
        }
        while (PREV.consumeClick()) {
            Hud.select(-1);
        }
        while (CAST.consumeClick()) {
            if (!Director.active()) {
                Move m = Hud.selected();
                Net.toServer(new C2SCast(m.ordinal()));
            }
        }
    }
}
