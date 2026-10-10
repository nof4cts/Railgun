package com.geto.csm.server;

import com.geto.csm.entity.CurseEntity;
import com.geto.csm.entity.KuchisakeEntity;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.network.QuestionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The slit-mouthed woman's question, as a binding vow: neither side can strike until the
 * victim answers. Folklore rules — "yes" makes her pull down her mask and ask again,
 * "no" brings the shears, and calling her "average" confuses her long enough to escape.
 */
public final class KuchisakeGame {
    public static final int ANSWER_TICKS = 200;
    private static final RandomSource RNG = RandomSource.create();

    private static final class Game {
        KuchisakeEntity k;
        LivingEntity t;
        int stage;
        int ticks;
        Vec3 anchor;
    }

    private static final Map<Integer, Game> BY_TARGET = new HashMap<>();

    public static boolean busy(LivingEntity t) {
        return BY_TARGET.containsKey(t.getId());
    }

    /** True if this entity is a target currently bound by a vow (cannot hurt or be hurt). */
    public static boolean bound(Entity e) {
        return e != null && BY_TARGET.containsKey(e.getId());
    }

    public static void start(KuchisakeEntity k, LivingEntity t) {
        Game g = new Game();
        g.k = k; g.t = t; g.stage = 1; g.ticks = ANSWER_TICKS; g.anchor = t.position();
        BY_TARGET.put(t.getId(), g);
        k.vowTicks = ANSWER_TICKS * 2 + 40;
        k.setUnmasked(false);
        Vec3 mid = k.position().add(t.position()).scale(0.5);
        CsmNetwork.fx(k, FxType.KUCHI_DOMAIN, k, t, mid, 5f, ANSWER_TICKS);
        k.level().playSound(null, k.getX(), k.getY(), k.getZ(), SoundEvents.BELL_RESONATE, net.minecraft.sounds.SoundSource.HOSTILE, 2f, 0.6f);
        ask(g);
    }

    private static void ask(Game g) {
        g.ticks = ANSWER_TICKS;
        if (g.t instanceof ServerPlayer sp) CsmNetwork.toPlayer(sp, new QuestionPacket(g.k.getId(), g.stage, ANSWER_TICKS));
    }

    public static void answer(ServerPlayer sp, int entityId, int ans) {
        Game g = BY_TARGET.get(sp.getId());
        if (g == null || g.k.getId() != entityId) return;
        resolve(g, Math.floorMod(ans, 3));
    }

    private static void resolve(Game g, int ans) {
        if (g.stage == 1 && ans == 0) {
            // "Yes" — she lowers the mask: "...even like this?"
            g.stage = 2;
            g.k.setUnmasked(true);
            CsmNetwork.fx(g.k, FxType.ACTION, g.k, g.t, g.k.position(), 3, 0);
            g.k.level().playSound(null, g.k.getX(), g.k.getY(), g.k.getZ(), SoundEvents.WITCH_CELEBRATE, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.5f);
            ask(g);
            return;
        }
        finish(g);
        if (ans == 2) {
            escape(g);
        } else if (g.stage == 2 && ans == 0) {
            mouthSlash(g);
        } else {
            snip(g);
        }
    }

    private static void finish(Game g) {
        BY_TARGET.remove(g.t.getId());
        g.k.vowTicks = 0;
        g.k.askCooldown = 300;
        if (g.t instanceof ServerPlayer sp) CsmNetwork.toPlayer(sp, new QuestionPacket(g.k.getId(), 0, 0));
        KuchisakeEntity k = g.k;
        Scheduler.after(80, () -> k.setUnmasked(false));
    }

    private static void snip(Game g) {
        LivingEntity t = g.t;
        KuchisakeEntity k = g.k;
        CsmNetwork.fx(k, FxType.SHEARS, k, t, t.position().add(0, t.getBbHeight() * 0.5, 0), k.getYRot(), 1);
        Scheduler.after(9, () -> {
            if (!k.isAlive() || !t.isAlive()) return;
            k.strike(t, 18f);
            k.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.SHEEP_SHEAR, net.minecraft.sounds.SoundSource.HOSTILE, 3f, 0.4f);
        });
    }

    private static void mouthSlash(Game g) {
        LivingEntity t = g.t;
        KuchisakeEntity k = g.k;
        CsmNetwork.fx(k, FxType.MOUTH_SLASH, k, t, t.getEyePosition(), k.getYRot(), 0);
        Scheduler.after(6, () -> {
            if (!k.isAlive() || !t.isAlive()) return;
            k.strike(t, 12f);
            t.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
        });
    }

    private static void escape(Game g) {
        g.k.askCooldown = 400;
        g.k.vowTicks = 60; // she stands there, confused
        CsmNetwork.fx(g.k, FxType.ACTION, g.k, g.t, g.k.position(), 4, 0);
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, Game>> it = BY_TARGET.entrySet().iterator();
        java.util.List<Game> timedOut = new java.util.ArrayList<>();
        java.util.List<Game[]> auto = new java.util.ArrayList<>();
        while (it.hasNext()) {
            Game g = it.next().getValue();
            if (!g.k.isAlive() || !g.t.isAlive() || g.t.level() != g.k.level()) {
                it.remove();
                if (g.t instanceof ServerPlayer sp) CsmNetwork.toPlayer(sp, new QuestionPacket(g.k.getId(), 0, 0));
                g.k.vowTicks = 0;
                continue;
            }
            // the vow pins the victim in place
            if (g.t.position().distanceTo(g.anchor) > 0.3) g.t.teleportTo(g.anchor.x, g.anchor.y, g.anchor.z);
            g.t.setDeltaMovement(Vec3.ZERO);
            g.t.hurtMarked = true;
            g.ticks--;
            if (!(g.t instanceof ServerPlayer) && g.ticks == ANSWER_TICKS - 50) auto.add(new Game[]{g});
            else if (g.ticks <= 0) timedOut.add(g);
        }
        for (Game[] a : auto) resolve(a[0], RNG.nextInt(3));
        for (Game g : timedOut) if (BY_TARGET.get(g.t.getId()) == g) resolve(g, 1); // silence counts as "no"
    }

    public static void clear() {
        BY_TARGET.clear();
    }

    @SuppressWarnings("unused")
    private static boolean isCurse(Entity e) {
        return e instanceof CurseEntity;
    }

    private KuchisakeGame() {}
}
