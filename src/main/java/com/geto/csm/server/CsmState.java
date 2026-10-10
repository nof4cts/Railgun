package com.geto.csm.server;

import com.geto.csm.CurseKind;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player manipulator state (server side). */
public class CsmState {
    private static final Map<UUID, CsmState> MAP = new HashMap<>();

    public static CsmState get(Player p) {
        return MAP.computeIfAbsent(p.getUUID(), k -> new CsmState());
    }

    public static CsmState peek(Player p) {
        return MAP.get(p.getUUID());
    }

    public static void remove(Player p) {
        MAP.remove(p.getUUID());
    }

    public int selected;
    /** Curses swallowed as orbs and held in reserve (fuel for Uzumaki). */
    public int absorbed;
    /** Each summon is one slot holding the entity ids it created (centipedes make five). */
    public final List<List<Integer>> slots = new ArrayList<>();
    public final long[] readyAt = new long[CurseKind.values().length];
    /** While > 0 the player is inside the Uzumaki cutscene: can't act, can't be hurt. */
    public int lockTicks;
}
