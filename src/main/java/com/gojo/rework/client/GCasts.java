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
        /** The cutscene was skipped: it does not come back to follow the shot. */
        public boolean skipped;
        boolean let;
        /** A held Purple once let go: when (ticks into the cast), from where, which way, onto what. */
        public int fireAt = -1;
        public Vec3 fireFrom, fireDir, target;
        /** Maximum Blue, steered: its last two positions from the server. */
        Vec3 posPrev, posNow;

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

        /** Which way a Purple points: its line once fired; before that, his gaze (once he can aim), else ahead. */
        public Vec3 aimDir(float partial) {
            if (fireAt >= 0) {
                return fireDir;
            }
            Entity c = caster();
            if (c != null && time(partial) >= GBeats.ready(move())) {
                return c.getViewVector(partial);
            }
            return flat();
        }

        public boolean aiming(float partial) {
            return GBeats.aims(move()) && fireAt < 0 && time(partial) >= GBeats.ready(move());
        }

        /** Where a steered technique is, between the server's last two positions. */
        public Vec3 pos(float partial) {
            if (posNow == null) {
                return null;
            }
            return posPrev == null ? posNow : posPrev.lerp(posNow, partial);
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
            if (GBeats.aims(move())) {
                return (fireAt < 0 ? GBeats.ready(move()) + GBeats.AIM_MAX + GBeats.KYO : fireAt) + GBeats.after(move());
            }
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
            if (p.msg.caster() == e.getId() && p.time(0) < p.end() + 2) {
                if (best == null || p.msg.start() > best.msg.start()) {
                    best = p;
                }
            }
        }
        return best;
    }

    /** A held Purple has been let go. */
    public static void fired(GNet.Fire m) {
        for (Play p : ACTIVE) {
            if (p.msg.caster() == m.caster() && p.move().ordinal() == m.move() && p.fireAt < 0) {
                p.fireAt = m.at();
                p.fireFrom = m.from();
                p.fireDir = m.dir();
                p.target = m.target();
                GFx.fired(p);
                if (p.caster() instanceof AbstractClientPlayer pl) {
                    GAnims.fire(pl, p.move());
                }
                if (p.mine && !p.skipped && !com.jjk.mod.client.JjkPrefs.skipCutscenes()) {
                    // the cutscene picks up again to follow the shot
                    com.gojo.rework.client.cam.GDirector.play(p);
                }
                return;
            }
        }
    }

    public static void pos(GNet.Pos m) {
        for (Play p : ACTIVE) {
            if (p.msg.caster() == m.caster() && p.move().ordinal() == m.move()) {
                p.posPrev = p.posNow == null ? m.pos() : p.posNow;
                p.posNow = m.pos();
                return;
            }
        }
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
            if (p.mine && !p.let && p.aiming(0) && t >= GBeats.ready(p.move()) + GBeats.AIM_MAX && mc.player != null) {
                // held as long as it can be: it goes where he is looking
                p.let = true;
                com.gojo.rework.net.GNet.CHANNEL.sendToServer(new com.gojo.rework.net.GNet.Shoot(mc.player.getViewVector(1.0F)));
            }
            if (t > p.end() + 200) {
                it.remove();
            }
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
