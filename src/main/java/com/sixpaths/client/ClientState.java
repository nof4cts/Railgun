package com.sixpaths.client;

import com.sixpaths.network.StatePacket;

/** The local player's Rinnegan state, mirrored from the server. */
public final class ClientState {
    public static int selected;
    public static int pretaTicks, ascendTicks;
    public static int[] cooldowns = new int[0];
    public static long receivedAt;

    public static void apply(StatePacket p) {
        selected = p.selected;
        pretaTicks = p.pretaTicks;
        ascendTicks = p.ascendTicks;
        cooldowns = p.cooldowns;
        receivedAt = Vfx.now();
    }

    public static float cooldownSeconds(int i) {
        if (i < 0 || i >= cooldowns.length) return 0;
        return Math.max(0, cooldowns[i] / 20f - (Vfx.now() - receivedAt) / 1000f);
    }

    public static float ascendSeconds() {
        return Math.max(0, ascendTicks / 20f - (Vfx.now() - receivedAt) / 1000f);
    }

    public static void clear() {
        selected = 0; pretaTicks = 0; ascendTicks = 0; cooldowns = new int[0];
    }

    private ClientState() {}
}
