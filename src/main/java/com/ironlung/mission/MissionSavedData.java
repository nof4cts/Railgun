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

    public final Map<UUID, Mission> missions = new HashMap<>();
    public int nextSlot = 0;

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
                d.missions.put(m.owner, m);
            } catch (Exception e) {
                com.ironlung.IronLungMod.LOGGER.warn("Discarding unreadable dive record", e);
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("nextSlot", nextSlot);
        ListTag list = new ListTag();
        for (Mission m : missions.values()) list.add(m.save());
        tag.put("missions", list);
        return tag;
    }
}
