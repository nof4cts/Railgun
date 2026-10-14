package com.firestar.sage.client;

import com.firestar.sage.SageConfig;
import com.firestar.sage.common.SMove;
import com.firestar.sage.net.SageNet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The rites playing on this client: the server's message, and everything worked out from its timings. */
public final class SageCasts {
    private static final List<Play> ACTIVE = new ArrayList<>();

    private SageCasts() {}

    public static final class Play {
        public final SageNet.Rite msg;
        public final boolean mine;
        int lastBeat = -1;
        /** Null Orbs: each orb's flight as the client sees it (position now and a tick ago, velocity, tick it landed). */
        final List<Vec3> orbs = new ArrayList<>();
        final List<Vec3> orbsO = new ArrayList<>();
        final List<Vec3> orbVel = new ArrayList<>();
        final List<Integer> orbLanded = new ArrayList<>();

        Play(SageNet.Rite msg) {
            this.msg = msg;
            Minecraft mc = Minecraft.getInstance();
            this.mine = mc.player != null && mc.player.getId() == msg.caster();
        }

        public SMove rite() {
            return msg.rite();
        }

        public float time(float partial) {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? 0 : (mc.level.getGameTime() - msg.start()) + partial;
        }

        @Nullable
        public Entity caster() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : mc.level.getEntity(msg.caster());
        }

        @Nullable
        public LivingEntity target() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || msg.target() < 0) {
                return null;
            }
            return mc.level.getEntity(msg.target()) instanceof LivingEntity l ? l : null;
        }

        public Vec3 flat() {
            Vec3 d = msg.dir();
            Vec3 f = new Vec3(d.x, 0, d.z);
            return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
        }

        public Vec3 right() {
            Vec3 f = flat();
            return new Vec3(-f.z, 0, f.x);
        }

        public Vec3 feet(float partial) {
            Entity c = caster();
            return c != null ? c.getPosition(partial) : msg.origin().subtract(0, 1.62, 0);
        }
    }

    public static void begin(SageNet.Rite m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ACTIVE.removeIf(p -> p.msg.caster() == m.caster() && p.rite() != SMove.STAFF && p.rite() == m.rite());
        Play p = new Play(m);
        ACTIVE.add(p);
        SageFx.begin(p);
        if (p.mine && m.rite().cinematic && SageConfig.CLIENT.cutscenes.get()) {
            com.firestar.sage.client.cam.Director.play(p);
        }
    }

    @Nullable
    public static Play of(Entity e) {
        if (e == null) {
            return null;
        }
        Play best = null;
        for (Play p : ACTIVE) {
            if (p.msg.caster() == e.getId() && p.time(0) < p.rite().length + 2) {
                if (best == null || p.msg.start() > best.msg.start()) {
                    best = p;
                }
            }
        }
        return best;
    }

    public static List<Play> all() {
        return ACTIVE;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Play> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Play p = it.next();
            int t = (int) p.time(0);
            for (int k = p.lastBeat + 1; k <= t; k++) {
                SageFx.beat(p, k);
            }
            p.lastBeat = t;
            SageFx.tick(p, t);
            if (t > p.rite().length + 160) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
