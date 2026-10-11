package com.firestar.mih.server;

import com.firestar.mih.network.Net;
import com.firestar.mih.network.S2CState;
import com.firestar.mih.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Who has Made in Heaven (kept on the player, survives death and relogs) and each user's live state: Stand out or not,
 * Double Acceleration, and the Alternate Universe marks a reset leaves (a name suffix and a lost limb, for a while).
 */
public final class StandUsers {
    private static final String TAG = "madeinheaven";
    private static final Map<UUID, State> STATES = new HashMap<>();

    public static final byte LIMB_NONE = 0, LIMB_ARM = 1, LIMB_LEG = 2;

    public static final class State {
        public boolean summoned;
        public long daUntil;
        public long auUntil;
        public byte limb;
    }

    private StandUsers() {}

    public static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static boolean has(Player p) {
        return p.getPersistentData().getCompound(TAG).getBoolean("stand");
    }

    public static void grant(ServerPlayer p, boolean has) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        t.putBoolean("stand", has);
        p.getPersistentData().put(TAG, t);
        if (!has) {
            state(p).summoned = false;
        }
        sync(p);
    }

    /** Give the Stand back up: the disc comes out. */
    public static void relinquish(ServerPlayer p) {
        TimeAccel.cancel(p);
        grant(p, false);
        ItemStack disc = new ItemStack(ModItems.DISC.get());
        if (!p.getInventory().add(disc)) {
            p.drop(disc, false);
        }
    }

    /** Carry the Stand across death. */
    public static void copy(Player from, Player to) {
        if (from.getPersistentData().contains(TAG)) {
            to.getPersistentData().put(TAG, from.getPersistentData().getCompound(TAG).copy());
        }
    }

    public static boolean summoned(Player p) {
        return has(p) && state(p).summoned;
    }

    public static boolean doubleAccel(ServerPlayer p) {
        return state(p).daUntil > p.serverLevel().getGameTime();
    }

    public static boolean alternate(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.auUntil > p.level().getGameTime();
    }

    public static byte limb(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.auUntil > p.level().getGameTime() ? s.limb : LIMB_NONE;
    }

    public static S2CState packet(ServerPlayer p) {
        State s = state(p);
        TimeAccel.Run run = TimeAccel.of(p);
        return new S2CState(p.getId(), has(p), s.summoned && has(p), s.daUntil,
                run == null ? -1L : run.start, run == null ? 0 : run.length,
                run == null ? 0 : run.center.x, run == null ? 0 : run.center.y, run == null ? 0 : run.center.z,
                run == null ? 0 : run.radius, s.auUntil, s.limb);
    }

    public static void sync(ServerPlayer p) {
        Net.toTrackingAndSelf(p, packet(p));
    }

    /** Per tick: the lost limb's handicaps, and the marks wearing off. */
    public static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        if (s.auUntil > 0 && s.auUntil <= now) {
            s.auUntil = 0;
            s.limb = LIMB_NONE;
            p.refreshDisplayName();
            sync(p);
            return;
        }
        if (s.auUntil > now && s.limb == LIMB_LEG) {
            p.setSprinting(false);
            if (now % 20 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, false, true));
            }
        }
        if (s.auUntil > now && s.limb == LIMB_ARM && now % 20 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0, false, false, true));
        }
    }

    public static void forget(Player p) {
        STATES.remove(p.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
    }
}
