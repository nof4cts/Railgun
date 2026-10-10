package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Latest technique snapshot for every Hakari user this client can see. */
public final class ClientState {
    private static final Map<Integer, StatePacket> STATES = new HashMap<>();

    public static void put(StatePacket p) {
        p.receivedAt = Vfx.now();
        STATES.put(p.entityId, p);
    }

    public static StatePacket get(int entityId) {
        return STATES.get(entityId);
    }

    public static Collection<StatePacket> all() {
        return STATES.values();
    }

    public static void clear() {
        STATES.clear();
    }

    /** Ticks elapsed since the snapshot arrived, as a smooth float. */
    public static float ticksSince(StatePacket p) {
        return (Vfx.now() - p.receivedAt) / 50f;
    }

    public static float castElapsedSeconds(StatePacket p) {
        if (p.castTicks <= 0) return p.domainActive ? 999f : 0f;
        float elapsedTicks = (180 - p.castTicks) + ticksSince(p);
        return Math.max(0, elapsedTicks / 20f);
    }

    public static float jackpotSecondsLeft(StatePacket p) {
        return Math.max(0, (p.jackpotTicks - ticksSince(p)) / 20f);
    }

    public static Vec3 center(StatePacket p) {
        return new Vec3(p.cx, p.cy, p.cz);
    }

    /** Is the given point inside any live domain? Returns that domain's state or null. */
    public static StatePacket domainContaining(Vec3 pos) {
        for (StatePacket p : STATES.values()) {
            if ((p.domainActive || p.castTicks > 0) && center(p).distanceTo(pos) <= p.radius + 0.5) return p;
        }
        return null;
    }

    private ClientState() {}
}
