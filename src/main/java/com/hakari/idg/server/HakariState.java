package com.hakari.idg.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side technique state per player. */
public class HakariState {
    private static final Map<UUID, HakariState> MAP = new HashMap<>();

    public static HakariState get(Player p) {
        return MAP.computeIfAbsent(p.getUUID(), k -> new HakariState());
    }

    public static HakariState peek(Player p) {
        return MAP.get(p.getUUID());
    }

    public static void remove(Player p) {
        MAP.remove(p.getUUID());
    }

    // Domain
    public int castTicks;
    public boolean domainActive;
    public int domainTicks;
    public Vec3 domainCenter = Vec3.ZERO;
    public float radius = HakariLogic.DOMAIN_RADIUS;
    public final Set<Integer> trapped = new HashSet<>();
    public int visuals;
    public int fails;
    public float chanceBonus;
    /** Set by an odd-numbered jackpot: the next domain has doubled odds. */
    public boolean boosted;

    // Riichi
    public int riichiTicks;
    public int riichiTier, riichiScenario, riichiNumber, riichiFinal;
    public boolean riichiWin, riichiPity;

    // Jackpot
    public int jackpotCutTicks;
    public int jackpotTicks;
    public int jackpotMax;
    public int jackpotNumber;

    // Moves
    public int counterTicks;
    public int rhythmTicks;
    public int windupTicks;
    /** 0 none, 1 hand sign, 2 punch windup, 3 guard. */
    public int pose;

    /** True while a cutscene owns this player (can't act, can't be hurt). */
    public boolean locked() {
        return castTicks > 0 || jackpotCutTicks > 0;
    }

    public boolean immortal() {
        return locked() || jackpotTicks > 0;
    }
}
