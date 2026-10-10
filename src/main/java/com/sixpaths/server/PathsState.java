package com.sixpaths.server;

import com.sixpaths.Ability;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player Rinnegan state: selection, cooldowns, active windows, cutscene lock. */
public class PathsState {
    private static final Map<UUID, PathsState> MAP = new HashMap<>();

    public static PathsState get(Player p) {
        return MAP.computeIfAbsent(p.getUUID(), k -> new PathsState());
    }

    public static PathsState peek(Player p) {
        return MAP.get(p.getUUID());
    }

    public static void remove(Player p) {
        MAP.remove(p.getUUID());
    }

    public int selected;
    public final long[] readyAt = new long[Ability.values().length];
    public long pretaUntil, ascendUntil, noFallUntil;
    public int lockTicks;
    public boolean ascended;
    public int syncIn;

    public boolean preta(long now) { return now < pretaUntil; }
    public boolean ascending(long now) { return now < ascendUntil; }
}
