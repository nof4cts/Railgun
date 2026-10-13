package com.firestar.silver.client;

import com.firestar.silver.network.S2CState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Every fighter this client can see: the form, and the aura's power easing toward it. */
public final class ClientState {
    private static final Map<Integer, Info> INFO = new HashMap<>();

    private ClientState() {}

    public static final class Info {
        public int form;
        public long since;
        /** The aura: 0 none, ~0.55 Omen, 1 Zenith. Eased per tick. */
        public float power, powerO;
    }

    public static void accept(S2CState m) {
        Info i = INFO.computeIfAbsent(m.entityId(), k -> new Info());
        int before = i.form;
        i.form = m.form();
        i.since = m.since();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && m.entityId() == mc.player.getId()) {
            ZenithTheme.form(i.form);
        }
        if (before > 0 && i.form == 0 && mc.level != null && mc.level.getEntity(m.entityId()) != null) {
            Choreo.revert(mc.level.getEntity(m.entityId()));
        }
    }

    public static Info get(Entity e) {
        return e == null ? null : INFO.get(e.getId());
    }

    public static int form(Entity e) {
        Info i = get(e);
        return i == null ? 0 : i.form;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            INFO.clear();
            return;
        }
        for (Info i : INFO.values()) {
            i.powerO = i.power;
            float target = i.form == 2 ? 1.0F : i.form == 1 ? 0.55F : 0.0F;
            i.power += (target - i.power) * 0.12F;
            if (Math.abs(target - i.power) < 0.005F) {
                i.power = target;
            }
        }
    }

    public static void clear() {
        INFO.clear();
    }
}
