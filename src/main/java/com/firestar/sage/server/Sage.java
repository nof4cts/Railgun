package com.firestar.sage.server;

import com.firestar.sage.SageConfig;
import com.firestar.sage.net.SageNet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** Who wears the Mantle: the form (mortal / ascended), since when, and the flight it lends. */
public final class Sage {
    public static final int MORTAL = 0, ASCENDED = 1;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private Sage() {}

    static final class State {
        int form;
        long since;
        boolean flightGiven;
        int staffStep;
        long lastStaff;
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

    /** The staff's next blow: thrust, sweep, overhead, then round again; a pause starts over. */
    static int nextStaff(ServerPlayer p) {
        State s = state(p);
        long now = p.serverLevel().getGameTime();
        s.staffStep = now - s.lastStaff > 20 ? 0 : (s.staffStep + 1) % 3;
        s.lastStaff = now;
        return s.staffStep;
    }

    public static SageNet.Form packet(ServerPlayer p) {
        State s = state(p);
        return new SageNet.Form(p.getId(), s.form, s.since);
    }

    public static void sync(ServerPlayer p) {
        SageNet.around(p, packet(p));
    }

    static void flight(ServerPlayer p, State s) {
        var ab = p.getAbilities();
        if (p.isCreative() || p.isSpectator()) {
            s.flightGiven = false;
            return;
        }
        boolean want = s.form == ASCENDED && SageConfig.SERVER.flight.get();
        boolean changed = false;
        if (want && !ab.mayfly) {
            ab.mayfly = true;
            s.flightGiven = true;
            p.getPersistentData().putBoolean("celestial_flight", true);
            changed = true;
        }
        if (!want && s.flightGiven) {
            ab.mayfly = false;
            ab.flying = false;
            s.flightGiven = false;
            p.getPersistentData().remove("celestial_flight");
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
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 2, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, false, true));
        }
    }

    public static void cleanFlight(ServerPlayer p) {
        if (p.getPersistentData().getBoolean("celestial_flight")) {
            p.getPersistentData().remove("celestial_flight");
            if (!p.isCreative() && !p.isSpectator()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
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
