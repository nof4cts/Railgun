package com.firestar.rose.server;

import com.firestar.rose.RoseConfig;
import com.firestar.rose.net.RoseNet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** Each reaper's form (mortal or ascended), the gifts of the ascended form, and the flight it lends. */
public final class Reaper {
    public static final int MORTAL = 0, ASCENDED = 1;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private Reaper() {}

    static final class State {
        int form;
        long since;
        int combo;
        long lastReap;
        boolean flightGiven;
    }

    static State state(Player p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    public static int form(Player p) {
        State s = STATES.get(p.getUUID());
        return s == null ? MORTAL : s.form;
    }

    public static void setForm(ServerPlayer p, int form) {
        State s = state(p);
        s.form = form;
        s.since = p.serverLevel().getGameTime();
        flight(p, s);
        if (form == MORTAL) {
            p.removeEffect(MobEffects.MOVEMENT_SPEED);
            p.removeEffect(MobEffects.DAMAGE_BOOST);
            p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }
        sync(p);
    }

    /** The next cut of the Reap chain: 0..3 then round; a pause starts it again. */
    static int nextReap(ServerPlayer p) {
        State s = state(p);
        long now = p.serverLevel().getGameTime();
        s.combo = now - s.lastReap > 18 ? 0 : (s.combo + 1) % 4;
        s.lastReap = now;
        return s.combo;
    }

    public static RoseNet.Form packet(ServerPlayer p) {
        State s = state(p);
        return new RoseNet.Form(p.getId(), s.form, s.since);
    }

    public static void sync(ServerPlayer p) {
        RoseNet.around(p, packet(p));
    }

    static void flight(ServerPlayer p, State s) {
        var ab = p.getAbilities();
        if (p.isCreative() || p.isSpectator()) {
            s.flightGiven = false;
            return;
        }
        boolean want = s.form == ASCENDED && RoseConfig.SERVER.flight.get();
        boolean changed = false;
        if (want && !ab.mayfly) {
            ab.mayfly = true;
            s.flightGiven = true;
            p.getPersistentData().putBoolean("requiem_flight", true);
            changed = true;
        }
        if (want && s.flightGiven && Math.abs(ab.getFlyingSpeed() - 0.09F) > 1.0E-4) {
            ab.setFlyingSpeed(0.09F);
            changed = true;
        }
        if (!want && s.flightGiven) {
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05F);
            s.flightGiven = false;
            p.getPersistentData().remove("requiem_flight");
            changed = true;
        }
        if (changed) {
            p.onUpdateAbilities();
        }
    }

    public static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) {
            return;
        }
        flight(p, s);
        if (s.form == ASCENDED && p.serverLevel().getGameTime() % 40 == 0) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, false, true));
        }
    }

    /** Flight lent by an ascension that ended with the session is taken back on login. */
    public static void cleanFlight(ServerPlayer p) {
        if (p.getPersistentData().getBoolean("requiem_flight")) {
            p.getPersistentData().remove("requiem_flight");
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.getAbilities().setFlyingSpeed(0.05F);
                p.onUpdateAbilities();
            }
        }
    }

    public static void forget(Player p) {
        STATES.remove(p.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
    }
}
