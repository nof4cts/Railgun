package com.aegis.command.client;

import com.aegis.command.item.WeaponType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Client mirror of the player's terminal data plus the live event feed. */
public final class ClientData {
    public enum Level { INFO, OK, WARN, ALERT }

    public record FeedEntry(String time, String text, Level level, long createdMs) {}

    public static final List<FeedEntry> FEED = new ArrayList<>();
    /** Recent impact positions for the tactical map: {x, z, timeMs}. */
    public static final List<double[]> IMPACTS = new ArrayList<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static CompoundTag data = new CompoundTag();
    public static BlockPos laptop = BlockPos.ZERO;
    public static long cooldownEndTick;
    /** Strike status shown on the dashboard. */
    public static String status = "STANDBY";
    public static String activeCodename = "";
    /** Most recent completed mission waiting to be shown on the debrief overlay. */
    public static CompoundTag pendingDebrief;

    // persisted UI choices for the session
    public static int selectedAircraft = 0;
    public static int heading = -1;
    public static Integer targetX, targetZ;

    private ClientData() {}

    public static void feed(String text, Level level) {
        FEED.add(new FeedEntry(LocalTime.now().format(TIME), text, level, System.currentTimeMillis()));
        while (FEED.size() > 40) FEED.remove(0);
    }

    public static void apply(CompoundTag tag) {
        data = tag;
        var mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        cooldownEndTick = now + tag.getInt("Cooldown");
        if (!tag.getBoolean("Active") && !"COMPLETE".equals(status) && !status.startsWith("ALERT")) {
            status = cooldownRemaining() > 0 ? "REARMING" : "STANDBY";
        }
    }

    public static int cooldownRemaining() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        return (int) Math.max(0, cooldownEndTick - mc.level.getGameTime());
    }

    public static boolean strikeActive() {
        return data.getBoolean("Active") || status.equals("ALERT") || status.equals("ACTIVE") || status.equals("INBOUND");
    }

    public static int missions() { return data.getInt("Missions"); }
    public static int ordnance() { return data.getInt("Ordnance"); }
    public static int kills() { return data.getInt("Kills"); }
    public static long flightTicks() { return data.getLong("FlightTicks"); }
    public static int maxRange() { return data.contains("MaxRange") ? data.getInt("MaxRange") : 96; }

    public static List<CompoundTag> history() {
        ListTag list = data.getList("History", Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) out.add(list.getCompound(i));
        return out;
    }

    public static boolean unlocked(WeaponType t) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) return true;
        return missions() >= t.unlockMissions;
    }

    private static CompoundTag prefs(WeaponType t) {
        return data.getCompound("Prefs").getCompound(t.id);
    }

    public static int finish(WeaponType t) {
        CompoundTag p = prefs(t);
        return p.contains("F") ? p.getInt("F") : t.defaultFinish;
    }

    public static int energy(WeaponType t) {
        CompoundTag p = prefs(t);
        return p.contains("E") ? p.getInt("E") : t.defaultEnergy;
    }

    /** Optimistic local update so the UI responds before the server echoes back. */
    public static void setPref(WeaponType t, String key, int v) {
        CompoundTag all = data.getCompound("Prefs");
        CompoundTag p = all.getCompound(t.id);
        p.putInt(key, v);
        all.put(t.id, p);
        data.put("Prefs", all);
    }
}
