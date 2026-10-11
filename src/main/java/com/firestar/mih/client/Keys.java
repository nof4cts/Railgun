package com.firestar.mih.client;

import com.firestar.mih.client.cinematic.Director;
import com.firestar.mih.common.Move;
import com.firestar.mih.network.C2SMove;
import com.firestar.mih.network.Net;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * YBA's layout: Q summons, the mouse punches, E R T Y G Z X H are the moves. While the Stand is out, the vanilla keys
 * that share them (inventory, chat, drop) are held back so a move never opens a menu. Everything can be rebound.
 */
public final class Keys {
    private static final String CATEGORY = "key.categories.madeinheaven";
    public static final KeyMapping SUMMON = key("summon", GLFW.GLFW_KEY_Q);
    private static final Map<Move, KeyMapping> MOVES = new EnumMap<>(Move.class);

    static {
        MOVES.put(Move.BARRAGE, key("barrage", GLFW.GLFW_KEY_E));
        MOVES.put(Move.FINISHER, key("finisher", GLFW.GLFW_KEY_R));
        MOVES.put(Move.KNIVES, key("knives", GLFW.GLFW_KEY_T));
        MOVES.put(Move.SPEED_SLICE, key("speed_slice", GLFW.GLFW_KEY_Y));
        MOVES.put(Move.JUDGEMENT, key("judgement", GLFW.GLFW_KEY_G));
        MOVES.put(Move.DOUBLE_ACCEL, key("double_accel", GLFW.GLFW_KEY_Z));
        MOVES.put(Move.INFINITE_SPEED, key("infinite_speed", GLFW.GLFW_KEY_X));
        MOVES.put(Move.LIGHTSPEED_RUSH, key("lightspeed_rush", GLFW.GLFW_KEY_C));
        MOVES.put(Move.KNIFE_STORM, key("knife_storm", GLFW.GLFW_KEY_V));
        MOVES.put(Move.TIME_ACCEL, key("time_accel", GLFW.GLFW_KEY_H));
    }

    private Keys() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.madeinheaven." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
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
        event.register(SUMMON);
        for (KeyMapping k : MOVES.values()) {
            event.register(k);
        }
    }

    private static boolean shares(KeyMapping ours, KeyMapping vanilla) {
        return ours.getKey().equals(vanilla.getKey());
    }

    /** Start of the client tick, before vanilla reads its keys. */
    public static void handle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        ClientState.Info info = ClientState.get(mc.player);
        boolean has = info != null && info.has;
        boolean out = has && info.summoned;
        while (SUMMON.consumeClick()) {
            if (has && !Director.active()) {
                Net.toServer(new C2SMove(Move.SUMMON.ordinal()));
                if (shares(SUMMON, mc.options.keyDrop)) {
                    while (mc.options.keyDrop.consumeClick()) {
                        // swallowed: Q summoned the Stand, it must not also drop the held item
                    }
                }
            }
        }
        for (Map.Entry<Move, KeyMapping> e : MOVES.entrySet()) {
            KeyMapping k = e.getValue();
            while (k.consumeClick()) {
                if (out && !Director.active()) {
                    Net.toServer(new C2SMove(e.getKey().ordinal()));
                }
            }
            if (out) {
                for (KeyMapping v : new KeyMapping[]{mc.options.keyInventory, mc.options.keyChat, mc.options.keyDrop, mc.options.keySwapOffhand,
                        mc.options.keySaveHotbarActivator, mc.options.keyLoadHotbarActivator}) {
                    if (shares(k, v)) {
                        while (v.consumeClick()) {
                            // swallowed: this key is a Stand move while the Stand is out
                        }
                    }
                }
            }
        }
    }

    /** Left click with the Stand out: Made in Heaven punches instead of the hand. */
    public static boolean punch() {
        Minecraft mc = Minecraft.getInstance();
        ClientState.Info info = mc.player == null ? null : ClientState.get(mc.player);
        if (info == null || !info.has || !info.summoned || Director.active()) {
            return false;
        }
        Net.toServer(new C2SMove(Move.PUNCH.ordinal()));
        return true;
    }
}
