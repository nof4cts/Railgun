package com.firestar.rose.client;

import com.firestar.rose.common.RBeats;
import com.firestar.rose.common.RMove;
import com.firestar.rose.net.RoseNet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Every reaper this client can see: the form, the aura's growth (0..1), and the scythe's presence (0..1). */
public final class RoseForms {
    private static final Map<Integer, Info> INFO = new HashMap<>();

    private RoseForms() {}

    public static final class Info {
        public int form;
        public long since;
        public float aura, auraO, scythe, scytheO;
    }

    public static void accept(RoseNet.Form m) {
        Info i = INFO.computeIfAbsent(m.entity(), k -> new Info());
        int before = i.form;
        i.form = m.form();
        i.since = m.since();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && before != i.form) {
            Entity e = mc.level.getEntity(m.entity());
            if (e != null) {
                RoseChoreo.formChanged(e, i.form);
            }
        }
    }

    public static Info get(Entity e) {
        return e == null ? null : INFO.get(e.getId());
    }

    public static int form(Entity e) {
        Info i = get(e);
        return i == null ? 0 : i.form;
    }

    public static boolean ascended(Entity e) {
        return form(e) == 1;
    }

    /** The aura's auravar for this frame, including the ignition inside Ascension. */
    public static float aura(Player p, float partial) {
        Info i = get(p);
        float a = i == null ? 0 : Mth.lerp(partial, i.auraO, i.aura);
        RoseCast c = RoseCasts.of(p);
        if (c != null && c.move == RMove.ASCEND) {
            float t = c.time(partial);
            if (t >= RBeats.ASCEND_IGNITE) {
                // ignition: the shell blooms out of nothing in a breath
                a = Math.max(a, Mth.clamp((t - RBeats.ASCEND_IGNITE) / 10.0F, 0, 1));
            } else {
                a = 0;
            }
        }
        if (c != null && c.move == RMove.RELEASE) {
            a = Math.min(a, 1 - Mth.clamp(c.time(partial) / 10.0F, 0, 1));
        }
        return a;
    }

    public static float scythe(Player p, float partial) {
        Info i = get(p);
        float k = i == null ? 0 : Mth.lerp(partial, i.scytheO, i.scythe);
        RoseCast c = RoseCasts.of(p);
        if (c != null && c.move == RMove.CRESCENT) {
            // thrown: the hand is empty until it comes home
            float t = c.time(partial);
            if (t >= RBeats.CRESCENT_THROW && t < RBeats.CRESCENT_HOME) {
                return 0;
            }
        }
        if (c != null && c.move == RMove.ASCEND && c.time(partial) < RBeats.ASCEND_IGNITE + 6) {
            return 0;
        }
        return k;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            INFO.clear();
            return;
        }
        for (Map.Entry<Integer, Info> e : INFO.entrySet()) {
            Info i = e.getValue();
            i.auraO = i.aura;
            i.scytheO = i.scythe;
            float want = i.form == 1 ? 1 : 0;
            i.aura += (want - i.aura) * (want > i.aura ? 0.10F : 0.18F);
            if (Math.abs(want - i.aura) < 0.004F) {
                i.aura = want;
            }
            i.scythe += (want - i.scythe) * 0.16F;
            if (Math.abs(want - i.scythe) < 0.004F) {
                i.scythe = want;
            }
        }
    }

    public static void clear() {
        INFO.clear();
    }
}
