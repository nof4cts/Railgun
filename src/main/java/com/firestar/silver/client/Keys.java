package com.firestar.silver.client;

import com.firestar.silver.client.cinematic.Director;
import com.firestar.silver.common.Move;
import com.firestar.silver.network.C2SMove;
import com.firestar.silver.network.Net;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * G awakens (and climbs to Zenith; sneak + G reverts), the mouse strikes, E R T Y Z X C V H are the moves. While
 * awakened, the vanilla keys sharing them (inventory, chat, drop...) are held back. Everything can be rebound.
 */
public final class Keys {
    private static final String CATEGORY = "key.categories.silverinstinct";
    public static final KeyMapping AWAKEN = key("awaken", GLFW.GLFW_KEY_G);
    private static final Map<Move, KeyMapping> MOVES = new EnumMap<>(Move.class);

    static {
        MOVES.put(Move.VOLLEY, key("volley", GLFW.GLFW_KEY_E));
        MOVES.put(Move.HEAVY, key("heavy", GLFW.GLFW_KEY_R));
        MOVES.put(Move.VANISH, key("vanish", GLFW.GLFW_KEY_T));
        MOVES.put(Move.WAVE, key("wave", GLFW.GLFW_KEY_Y));
        MOVES.put(Move.FLURRY, key("flurry", GLFW.GLFW_KEY_Z));
        MOVES.put(Move.METEOR, key("meteor", GLFW.GLFW_KEY_X));
        MOVES.put(Move.RUSH, key("rush", GLFW.GLFW_KEY_C));
        MOVES.put(Move.SPHERE, key("sphere", GLFW.GLFW_KEY_V));
        MOVES.put(Move.FINAL, key("final", GLFW.GLFW_KEY_H));
    }

    private Keys() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.silverinstinct." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static KeyMapping of(Move m) {
        return MOVES.get(m);
    }

    public static String name(KeyMapping k) {
        if (k == null) {
            return "?";
        }
        String s = k.getTranslatedKeyMessage().getString().toUpperCase();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(AWAKEN);
        for (KeyMapping k : MOVES.values()) {
            event.register(k);
        }
    }

    private static boolean shares(KeyMapping ours, KeyMapping vanilla) {
        return ours.getKey().equals(vanilla.getKey());
    }

    private static void swallow(KeyMapping ours) {
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping v : new KeyMapping[]{mc.options.keyInventory, mc.options.keyChat, mc.options.keyDrop, mc.options.keySwapOffhand,
                mc.options.keySaveHotbarActivator, mc.options.keyLoadHotbarActivator, mc.options.keyAdvancements}) {
            if (shares(ours, v)) {
                while (v.consumeClick()) {
                    // swallowed: while awakened this key is a technique
                }
            }
        }
    }

    /** Start of the client tick, before vanilla reads its keys. */
    public static void handle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        int form = ClientState.form(mc.player);
        boolean awake = form > 0;
        while (AWAKEN.consumeClick()) {
            if (!Director.active()) {
                // the server decides: Omen from nothing, Zenith from Omen, revert when sneaking
                Net.toServer(new C2SMove(Move.AWAKEN_OMEN.ordinal()));
            }
        }
        if (awake) {
            swallow(AWAKEN);
        }
        for (Map.Entry<Move, KeyMapping> e : MOVES.entrySet()) {
            KeyMapping k = e.getValue();
            while (k.consumeClick()) {
                if (!Director.active()) {
                    if (awake) {
                        Net.toServer(new C2SMove(e.getKey().ordinal()));
                    } else {
                        Hud.hint();
                    }
                }
            }
            if (awake) {
                swallow(k);
            }
        }
    }

    /** Left click while awakened with an empty hand: a strike of the combo instead of the vanilla punch. */
    public static boolean strike() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientState.form(mc.player) == 0 || Director.active() || !mc.player.getMainHandItem().isEmpty()) {
            return false;
        }
        Net.toServer(new C2SMove(Move.STRIKE.ordinal()));
        return true;
    }
}
