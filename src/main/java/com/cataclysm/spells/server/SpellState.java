package com.cataclysm.spells.server;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player cast lock (while > 0 the caster is in a cutscene: can't act, can't be hurt). */
public class SpellState {
    private static final Map<UUID, SpellState> MAP = new HashMap<>();

    public static SpellState get(Player p) {
        return MAP.computeIfAbsent(p.getUUID(), k -> new SpellState());
    }

    public static SpellState peek(Player p) {
        return MAP.get(p.getUUID());
    }

    public static void remove(Player p) {
        MAP.remove(p.getUUID());
    }

    public int lockTicks;
}
