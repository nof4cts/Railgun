package com.aegis.command.mission;

import com.aegis.command.AegisConfig;
import com.aegis.command.item.WeaponType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Per-player persistent data: mission history, lifetime stats and weapon customisation. */
public final class MissionData {
    public static final String KEY = "aegiscommand";
    public static final int MAX_HISTORY = 30;

    private MissionData() {}

    public static CompoundTag root(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(KEY, Tag.TAG_COMPOUND)) pd.put(KEY, new CompoundTag());
        return pd.getCompound(KEY);
    }

    public static int missions(Player p) {
        return root(p).getInt("Missions");
    }

    public static long lastStrike(Player p) {
        CompoundTag r = root(p);
        return r.contains("LastStrike") ? r.getLong("LastStrike") : Long.MIN_VALUE / 2;
    }

    public static void setLastStrike(Player p, long time) {
        root(p).putLong("LastStrike", time);
    }

    public static int nextMissionId(Player p) {
        CompoundTag r = root(p);
        int id = r.getInt("NextId") + 1;
        r.putInt("NextId", id);
        return id;
    }

    private static CompoundTag prefsFor(Player p, WeaponType t) {
        CompoundTag r = root(p);
        if (!r.contains("Prefs", Tag.TAG_COMPOUND)) r.put("Prefs", new CompoundTag());
        CompoundTag prefs = r.getCompound("Prefs");
        if (!prefs.contains(t.id, Tag.TAG_COMPOUND)) {
            CompoundTag w = new CompoundTag();
            w.putInt("F", t.defaultFinish);
            w.putInt("E", t.defaultEnergy);
            prefs.put(t.id, w);
        }
        return prefs.getCompound(t.id);
    }

    public static int finish(Player p, WeaponType t) { return prefsFor(p, t).getInt("F"); }
    public static int energy(Player p, WeaponType t) { return prefsFor(p, t).getInt("E"); }
    public static void setFinish(Player p, WeaponType t, int v) { prefsFor(p, t).putInt("F", v); }
    public static void setEnergy(Player p, WeaponType t, int v) { prefsFor(p, t).putInt("E", v); }

    public static void addRecord(Player p, CompoundTag rec) {
        CompoundTag r = root(p);
        ListTag list = r.getList("History", Tag.TAG_COMPOUND);
        list.add(rec);
        while (list.size() > MAX_HISTORY) list.remove(0);
        r.put("History", list);
        r.putInt("Missions", r.getInt("Missions") + 1);
        r.putInt("Ordnance", r.getInt("Ordnance") + rec.getInt("Bombs"));
        r.putInt("Kills", r.getInt("Kills") + rec.getInt("Kills"));
        r.putLong("FlightTicks", r.getLong("FlightTicks") + rec.getInt("Dur"));
    }

    public static void clearHistory(Player p) {
        root(p).put("History", new ListTag());
    }

    /** Everything the laptop UI needs, in one tag. */
    public static CompoundTag buildSync(ServerPlayer p) {
        CompoundTag r = root(p);
        CompoundTag out = new CompoundTag();
        out.put("History", r.getList("History", Tag.TAG_COMPOUND).copy());
        out.putInt("Missions", r.getInt("Missions"));
        out.putInt("Ordnance", r.getInt("Ordnance"));
        out.putInt("Kills", r.getInt("Kills"));
        out.putLong("FlightTicks", r.getLong("FlightTicks"));
        long now = p.level().getGameTime();
        long cdTicks = AegisConfig.COMMON.cooldownSeconds.get() * 20L;
        out.putInt("Cooldown", (int) Math.max(0, lastStrike(p) + cdTicks - now));
        out.putBoolean("Active", StrikeManager.isActive(p.getUUID()));
        out.putInt("MaxRange", AegisConfig.COMMON.maxRange.get());
        CompoundTag prefs = new CompoundTag();
        for (WeaponType t : WeaponType.values()) prefs.put(t.id, prefsFor(p, t).copy());
        out.put("Prefs", prefs);
        return out;
    }
}
