package com.firestar.mih.client;

import com.firestar.mih.common.Beats;
import com.firestar.mih.network.S2CState;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** What this client knows about every Stand user it can see, plus the Stand's fade in and out. */
public final class ClientState {
    private static final Map<Integer, Info> INFO = new HashMap<>();

    private ClientState() {}

    public static final class Info {
        public boolean has;
        public boolean summoned;
        public long daUntil;
        public long taStart = -1;
        public int taLength;
        public Vec3 center = Vec3.ZERO;
        public float radius;
        public long auUntil;
        public byte limb;
        /** 0 hidden … 1 fully out, eased per tick. */
        public float shown, shownO;
    }

    public static void accept(S2CState m) {
        Info i = INFO.computeIfAbsent(m.entityId, k -> new Info());
        boolean au = i.auUntil != m.auUntil;
        i.has = m.has;
        i.summoned = m.summoned;
        i.daUntil = m.daUntil;
        i.taStart = m.taStart;
        i.taLength = m.taLength;
        i.center = new Vec3(m.cx, m.cy, m.cz);
        i.radius = m.radius;
        i.auUntil = m.auUntil;
        i.limb = m.limb;
        Minecraft mc = Minecraft.getInstance();
        if (au && mc.level != null && mc.level.getEntity(m.entityId) instanceof Player p) {
            p.refreshDisplayName();
        }
    }

    public static Info get(Entity e) {
        return e == null ? null : INFO.get(e.getId());
    }

    public static Map<Integer, Info> all() {
        return INFO;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            INFO.clear();
            return;
        }
        for (Info i : INFO.values()) {
            i.shownO = i.shown;
            float target = i.summoned ? 1.0F : 0.0F;
            i.shown += (target - i.shown) * 0.3F;
            if (Math.abs(target - i.shown) < 0.01F) {
                i.shown = target;
            }
        }
    }

    /** True while this user's time is accelerating. */
    public static boolean accelerating(Info i, long now) {
        return i != null && i.taStart >= 0 && now >= i.taStart && now < i.taStart + i.taLength;
    }

    /** The current day-ticks per tick of any acceleration running in this world, or 0. */
    public static double accelRate(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0.0;
        }
        double now = mc.level.getGameTime() + partial;
        double best = 0.0;
        for (Info i : INFO.values()) {
            if (i.taStart >= 0 && i.taLength > 0 && now >= i.taStart && now < i.taStart + i.taLength) {
                best = Math.max(best, Beats.accelRate((now - i.taStart) / i.taLength));
            }
        }
        return best;
    }

    /** 0..1 progress of the strongest acceleration running, or -1. */
    public static double accelProgress(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return -1;
        }
        double now = mc.level.getGameTime() + partial;
        double best = -1;
        for (Info i : INFO.values()) {
            if (i.taStart >= 0 && i.taLength > 0 && now >= i.taStart && now < i.taStart + i.taLength) {
                best = Math.max(best, (now - i.taStart) / i.taLength);
            }
        }
        return best;
    }

    public static void clear() {
        INFO.clear();
    }
}
