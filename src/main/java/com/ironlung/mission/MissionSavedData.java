package com.ironlung.mission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MissionSavedData extends SavedData {
    private static final String NAME = "ironlung_missions";

    /** Missions by mission id. */
    public final Map<UUID, Mission> missions = new HashMap<>();
    /** Player -> mission id, rebuilt from crews. */
    public final transient Map<UUID, UUID> crewIndex = new HashMap<>();
    /** Crew who were offline when their dive ended: sent home on next login. */
    public final Map<UUID, Mission.ReturnPoint> pendingReturns = new HashMap<>();
    public int nextSlot = 0;

    public Mission missionOf(UUID player) {
        UUID mid = crewIndex.get(player);
        return mid == null ? null : missions.get(mid);
    }

    public void reindex() {
        crewIndex.clear();
        for (Mission m : missions.values()) for (UUID u : m.crew.keySet()) crewIndex.put(u, m.id);
    }

    public static MissionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MissionSavedData::load, MissionSavedData::new, NAME);
    }

    public static MissionSavedData load(CompoundTag tag) {
        MissionSavedData d = new MissionSavedData();
        d.nextSlot = tag.getInt("nextSlot");
        ListTag list = tag.getList("missions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                Mission m = Mission.load(list.getCompound(i));
                d.missions.put(m.id, m);
            } catch (Exception e) {
                com.ironlung.IronLungMod.LOGGER.warn("Discarding unreadable dive record", e);
            }
        }
        ListTag pr = tag.getList("pendingReturns", Tag.TAG_COMPOUND);
        for (int i = 0; i < pr.size(); i++) {
            CompoundTag t = pr.getCompound(i);
            d.pendingReturns.put(t.getUUID("uuid"), Mission.ReturnPoint.load(t));
        }
        d.reindex();
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextSlot", nextSlot);
        ListTag list = new ListTag();
        for (Mission m : missions.values()) list.add(m.save());
        tag.put("missions", list);
        ListTag pr = new ListTag();
        for (Map.Entry<UUID, Mission.ReturnPoint> e : pendingReturns.entrySet()) {
            CompoundTag t = e.getValue().save();
            t.putUUID("uuid", e.getKey());
            pr.add(t);
        }
        tag.put("pendingReturns", pr);
        return tag;
    }
}
