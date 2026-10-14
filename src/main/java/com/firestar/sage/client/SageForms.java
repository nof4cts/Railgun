package com.firestar.sage.client;

import com.firestar.sage.common.SBeats;
import com.firestar.sage.common.SMove;
import com.firestar.sage.net.SageNet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Every sage the client knows of: form, since when, and how present the orbs, halo and staff are (eased). */
public final class SageForms {
    static final class Info {
        int form;
        long since;
        float mantle, mantleO;
    }

    private static final Map<Integer, Info> INFO = new HashMap<>();

    private SageForms() {}

    public static void accept(SageNet.Form m) {
        Info i = INFO.computeIfAbsent(m.entity(), k -> new Info());
        int before = i.form;
        i.form = m.form();
        i.since = m.since();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && before != i.form) {
            Entity e = mc.level.getEntity(m.entity());
            if (e != null) {
                SageFx.formChanged(e, i.form);
            }
        }
    }

    public static boolean ascended(Entity e) {
        Info i = e == null ? null : INFO.get(e.getId());
        return i != null && i.form == 1;
    }

    /** 0..1: how much of the Mantle is on him (orbs, halo, staff), with the ascension's own build-up. */
    public static float mantle(Entity e, float partial) {
        Info i = e == null ? null : INFO.get(e.getId());
        float m = i == null ? 0 : Mth.lerp(partial, i.mantleO, i.mantle);
        SageCasts.Play c = SageCasts.of(e);
        if (c != null && c.rite() == SMove.MANTLE) {
            // during the ascension the orbs gather before the form is his
            float t = c.time(partial);
            m = Math.max(m, Mth.clamp((t - SBeats.MANTLE_GATHER) / 50.0F, 0, 1));
        }
        if (c != null && c.rite() == SMove.UNMANTLE) {
            m = Math.min(m, 1 - Mth.clamp(c.time(partial) / 10.0F, 0, 1));
        }
        return m;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            INFO.clear();
            return;
        }
        for (Info i : INFO.values()) {
            i.mantleO = i.mantle;
            float want = i.form == 1 ? 1 : 0;
            i.mantle += (want - i.mantle) * (want > i.mantle ? 0.25F : 0.12F);
        }
    }

    public static void forget() {
        INFO.clear();
    }
}
