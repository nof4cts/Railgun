package com.firestar.rose.client;

import com.firestar.rose.client.cam.Lens;
import com.firestar.rose.common.RMove;
import com.firestar.rose.net.RoseNet;
import com.firestar.styles.Style;
import com.firestar.styles.client.ClientStyles;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * G ascends (sneak + G releases), the left mouse button reaps, E R T Y Z X H are the techniques. While ascended the
 * vanilla keys sharing them (inventory, chat, drop...) are held back. All of them can be rebound.
 */
public final class RoseKeys {
    private static final String CAT = "key.categories.requiem";
    public static final KeyMapping ASCEND = key("ascend", GLFW.GLFW_KEY_G);
    private static final Map<RMove, KeyMapping> MOVES = new EnumMap<>(RMove.class);

    static {
        MOVES.put(RMove.TETHER, key("tether", GLFW.GLFW_KEY_E));
        MOVES.put(RMove.CRESCENT, key("crescent", GLFW.GLFW_KEY_R));
        MOVES.put(RMove.STEP, key("step", GLFW.GLFW_KEY_T));
        MOVES.put(RMove.RAY, key("ray", GLFW.GLFW_KEY_Y));
        MOVES.put(RMove.REQUIEM, key("requiem", GLFW.GLFW_KEY_Z));
        MOVES.put(RMove.RIFT, key("rift", GLFW.GLFW_KEY_X));
        MOVES.put(RMove.LAST_BLOOM, key("last_bloom", GLFW.GLFW_KEY_H));
    }

    private RoseKeys() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.requiem." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CAT);
    }

    public static KeyMapping of(RMove m) {
        return MOVES.get(m);
    }

    public static String name(KeyMapping k) {
        if (k == null) {
            return "?";
        }
        String s = k.getTranslatedKeyMessage().getString().toUpperCase();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    public static void register(RegisterKeyMappingsEvent e) {
        e.register(ASCEND);
        for (KeyMapping k : MOVES.values()) {
            e.register(k);
        }
    }

    private static void swallow(KeyMapping ours) {
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping v : new KeyMapping[]{mc.options.keyInventory, mc.options.keyChat, mc.options.keyDrop, mc.options.keySwapOffhand,
                mc.options.keySaveHotbarActivator, mc.options.keyLoadHotbarActivator, mc.options.keyAdvancements}) {
            if (ours.getKey().equals(v.getKey())) {
                while (v.consumeClick()) {
                    // held back: while ascended this key is a technique
                }
            }
        }
    }

    public static void handle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        if (!ClientStyles.mine(Style.ROSE)) {
            while (ASCEND.consumeClick()) {
            }
            for (KeyMapping k : MOVES.values()) {
                while (k.consumeClick()) {
                }
            }
            return;
        }
        boolean up = RoseForms.ascended(mc.player);
        while (ASCEND.consumeClick()) {
            if (!Lens.active()) {
                RoseNet.toServer(new RoseNet.Act(RMove.ASCEND.ordinal()));
            }
        }
        if (up) {
            swallow(ASCEND);
        }
        for (Map.Entry<RMove, KeyMapping> e : MOVES.entrySet()) {
            KeyMapping k = e.getValue();
            while (k.consumeClick()) {
                if (!Lens.active()) {
                    if (up) {
                        RoseNet.toServer(new RoseNet.Act(e.getKey().ordinal()));
                    } else {
                        RoseHud.hint();
                    }
                }
            }
            if (up) {
                swallow(k);
            }
        }
    }

    /** Left click while ascended with the scythe in hand (an empty main hand): a cut of the Reap chain. */
    public static boolean reap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientStyles.mine(Style.ROSE) || !RoseForms.ascended(mc.player) || Lens.active()
                || !mc.player.getMainHandItem().isEmpty()) {
            return false;
        }
        RoseNet.toServer(new RoseNet.Act(RMove.REAP.ordinal()));
        return true;
    }
}
