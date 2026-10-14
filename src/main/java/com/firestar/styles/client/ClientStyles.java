package com.firestar.styles.client;

import com.firestar.styles.Style;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Every player's style as this client knows it. */
public final class ClientStyles {
    private static final Map<Integer, Style> STYLES = new HashMap<>();

    private ClientStyles() {}

    public static void set(int entity, Style s) {
        STYLES.put(entity, s);
    }

    public static Style of(Entity e) {
        return e == null ? Style.NONE : STYLES.getOrDefault(e.getId(), Style.NONE);
    }

    public static Style mine() {
        return of(Minecraft.getInstance().player);
    }

    public static boolean mine(Style s) {
        return mine() == s;
    }

    /** Whether the big techniques of every style play their cutscenes (the style menu's switch). */
    public static boolean cutscenes() {
        return com.firestar.styles.StylesPrefs.cutscenes();
    }

    public static void clear() {
        STYLES.clear();
    }
}
