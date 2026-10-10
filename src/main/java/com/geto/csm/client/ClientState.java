package com.geto.csm.client;

import com.geto.csm.network.StatePacket;

/** The local player's manipulator state, mirrored from the server. */
public final class ClientState {
    public static int selected;
    public static int absorbed;
    public static int slots;
    public static int[] cooldowns = new int[0];
    public static long receivedAt;

    public static void apply(StatePacket p) {
        selected = p.selected;
        absorbed = p.absorbed;
        slots = p.slots;
        cooldowns = p.cooldowns;
        receivedAt = Vfx.now();
    }

    public static float cooldownSeconds(int kind) {
        if (kind < 0 || kind >= cooldowns.length) return 0;
        return Math.max(0, cooldowns[kind] / 20f - (Vfx.now() - receivedAt) / 1000f);
    }

    public static void clear() {
        selected = 0; absorbed = 0; slots = 0; cooldowns = new int[0];
    }

    private ClientState() {}
}
