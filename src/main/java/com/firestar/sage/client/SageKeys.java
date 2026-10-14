package com.firestar.sage.client;

import com.firestar.sage.common.SMove;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** G takes up (or lets go of) the Mantle, the left mouse button strikes with the staff, the rest are rites. */
public final class SageKeys {
    private static final String CAT = "key.categories.celestial";
    public static final KeyMapping MANTLE = key("mantle", GLFW.GLFW_KEY_G);
    private static final Map<SMove, KeyMapping> MOVES = new EnumMap<>(SMove.class);

    static {
        MOVES.put(SMove.PULL, key("pull", GLFW.GLFW_KEY_E));
        MOVES.put(SMove.ORBS, key("orbs", GLFW.GLFW_KEY_R));
        MOVES.put(SMove.MIRROR, key("mirror", GLFW.GLFW_KEY_T));
        MOVES.put(SMove.STARFALL, key("starfall", GLFW.GLFW_KEY_Y));
        MOVES.put(SMove.THORNWOOD, key("thornwood", GLFW.GLFW_KEY_Z));
        MOVES.put(SMove.COLOSSUS, key("colossus", GLFW.GLFW_KEY_X));
        MOVES.put(SMove.ECLIPSE, key("eclipse", GLFW.GLFW_KEY_H));
    }

    private SageKeys() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.celestial." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CAT);
    }

    public static KeyMapping of(SMove m) {
        return MOVES.get(m);
    }

    public static Map<SMove, KeyMapping> all() {
        return MOVES;
    }

    public static String name(KeyMapping k) {
        if (k == null) {
            return "?";
        }
        String s = k.getTranslatedKeyMessage().getString().toUpperCase();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    public static void register(RegisterKeyMappingsEvent e) {
        e.register(MANTLE);
        for (KeyMapping k : MOVES.values()) {
            e.register(k);
        }
    }
}
