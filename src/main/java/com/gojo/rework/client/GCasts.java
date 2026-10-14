package com.gojo.rework.client;

import com.gojo.rework.common.GBeats;
import com.gojo.rework.common.GMove;
import com.gojo.rework.net.GNet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** The techniques playing on this client: the server's message, and everything worked out from its timings. */
public final class GCasts {
    private static final List<Play> ACTIVE = new ArrayList<>();

    private GCasts() {}

    public static final class Play {
        public final GNet.Cast msg;
        public final boolean mine;
        int lastBeat = -1;

        Play(GNet.Cast msg) {
            this.msg = msg;
            Minecraft mc = Minecraft.getInstance();
            this.mine = mc.player != null && mc.player.getId() == msg.caster();
        }

        public GMove move() {
            return msg.gmove();
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

        public Vec3 dir() {
            return msg.dir();
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

        /** His eye, as it is now (falls back to where he cast from). */
        public Vec3 eye(float partial) {
            Entity c = caster();
            return c != null ? c.getEyePosition(partial) : msg.origin();
        }

        public Vec3 feet(float partial) {
            Entity c = caster();
            return c != null ? c.getPosition(partial) : msg.origin().subtract(0, 1.62, 0);
        }

        public int end() {
            return switch (move()) {
                case DOMAIN -> GBeats.DV_HOLD;
                case DOMAIN_02 -> GBeats.D2_OPEN + GBeats.D2_STUN;
                default -> move().length;
            };
        }
    }

    public static void begin(GNet.Cast m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ACTIVE.removeIf(p -> p.msg.caster() == m.caster() && p.move() == m.gmove());
        Play p = new Play(m);
        ACTIVE.add(p);
        if (p.caster() instanceof AbstractClientPlayer pl) {
            GAnims.play(pl, m.gmove());
        }
        GFx.begin(p);
        boolean watch = p.mine;
        if (m.gmove() == GMove.DOMAIN && mc.player != null && mc.player.position().distanceTo(m.origin()) < GBeats.DV_RADIUS + 4) {
            // everyone the domain swallows sees it open
            watch = true;
        }
        if (watch && m.film() && !com.jjk.mod.client.JjkPrefs.skipCutscenes()) {
            com.gojo.rework.client.cam.GDirector.play(p);
        }
    }

    @Nullable
    public static Play of(Entity e) {
        if (e == null) {
            return null;
        }
        Play best = null;
        for (Play p : ACTIVE) {
            if (p.msg.caster() == e.getId() && p.time(0) < p.move().length + 2) {
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
                GFx.beat(p, k);
            }
            p.lastBeat = t;
            if (t > p.end() + 200) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
