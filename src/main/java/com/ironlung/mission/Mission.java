package com.ironlung.mission;

import com.ironlung.ocean.OceanMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything about one pilot's dive. Persisted through {@link MissionSavedData}. */
public final class Mission {
    public enum Power { ONLINE, TRIPPED, RESTORING }
    public enum Phase { DIVING, ASCENDING, IMPLODING, DONE }

    public final UUID owner;
    public final int slot;
    public final long seed;
    public final int mapSize, objectiveCount, hiddenCount;
    private transient OceanMap map;

    // submarine
    public double x, z, heading, depth, speed, turnRate, vertSpeed;
    public float throttleIn, turnIn, ballastIn;
    public float hull = 1.0f;
    public Power power = Power.ONLINE;
    public int restoreTicks;
    public int flickerTicks;
    public float cameraCondition = 1.0f;
    public int sealant;
    public int weldTicks;
    public int photoCooldown;
    public int glitchTicks;
    public int collisionCooldown;

    // camera as last reported by the client
    public int camMount;
    public float camPan, camTilt;
    public long cameraActiveUntil;

    // progress
    public boolean[] objectiveDone;
    public final List<PhotoRecord> photos = new ArrayList<>();
    public final List<Integer> anomaliesFound = new ArrayList<>();
    public Phase phase = Phase.DIVING;
    public int phaseTicks;
    public long ticks;

    // stats
    public int collisions, powerFailures, sightings, eventsTotal;

    // horror director state
    public long nextEventTick = 900;
    public long lastMajorTick = -100000;
    public boolean leviathanDone;
    public boolean photoAnomalyArmed;
    public long chainedEventTick = -1;
    public int chainedEvent = -1;
    public long lastPowerFailTick = -100000;
    public float tension;
    public int ghostTicks, ghostSide;
    public int silenceTicks;
    public int scrapeTicks;
    public long ambientCreakTick = 400;

    // runtime only
    public transient Boolean appliedLamps, appliedConsoles;
    public transient final float[] prox = {99, 99, 99, 99};

    // where to put the pilot back
    public String returnDim = "minecraft:overworld";
    public double returnX, returnY, returnZ;
    public float returnYaw, returnPitch;

    public Mission(UUID owner, int slot, long seed, int mapSize, int objectiveCount, int hiddenCount) {
        this.owner = owner;
        this.slot = slot;
        this.seed = seed;
        this.mapSize = mapSize;
        this.objectiveCount = objectiveCount;
        this.hiddenCount = hiddenCount;
    }

    public OceanMap map() {
        if (map == null) {
            map = new OceanMap(seed, mapSize, objectiveCount, hiddenCount);
            if (objectiveDone == null || objectiveDone.length != map.objectives.size()) {
                objectiveDone = new boolean[map.objectives.size()];
            }
        }
        return map;
    }

    public BlockPos base() {
        return SubInterior.base(slot);
    }

    public int objectivesComplete() {
        int n = 0;
        if (objectiveDone != null) for (boolean b : objectiveDone) if (b) n++;
        return n;
    }

    public boolean allObjectivesDone() {
        map();
        return objectivesComplete() >= objectiveDone.length;
    }

    public boolean powered() {
        return power == Power.ONLINE;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("owner", owner);
        t.putInt("slot", slot);
        t.putLong("seed", seed);
        t.putInt("mapSize", mapSize);
        t.putInt("objCount", objectiveCount);
        t.putInt("hidden", hiddenCount);
        t.putDouble("x", x);
        t.putDouble("z", z);
        t.putDouble("heading", heading);
        t.putDouble("depth", depth);
        t.putFloat("hull", hull);
        t.putInt("power", power.ordinal());
        t.putFloat("camCond", cameraCondition);
        t.putInt("sealant", sealant);
        t.putInt("phase", phase.ordinal());
        t.putInt("phaseTicks", phaseTicks);
        t.putLong("ticks", ticks);
        t.putInt("collisions", collisions);
        t.putInt("powerFailures", powerFailures);
        t.putInt("sightings", sightings);
        t.putInt("eventsTotal", eventsTotal);
        t.putLong("nextEvent", nextEventTick);
        t.putLong("lastMajor", lastMajorTick);
        t.putBoolean("leviathan", leviathanDone);
        t.putBoolean("anomalyArmed", photoAnomalyArmed);
        t.putLong("lastPowerFail", lastPowerFailTick);
        byte[] done = new byte[objectiveDone == null ? 0 : objectiveDone.length];
        for (int i = 0; i < done.length; i++) done[i] = (byte) (objectiveDone[i] ? 1 : 0);
        t.putByteArray("done", done);
        ListTag ph = new ListTag();
        for (PhotoRecord p : photos) ph.add(p.save());
        t.put("photos", ph);
        int[] an = new int[anomaliesFound.size()];
        for (int i = 0; i < an.length; i++) an[i] = anomaliesFound.get(i);
        t.putIntArray("anomalies", an);
        t.putString("retDim", returnDim);
        t.putDouble("retX", returnX);
        t.putDouble("retY", returnY);
        t.putDouble("retZ", returnZ);
        t.putFloat("retYaw", returnYaw);
        t.putFloat("retPitch", returnPitch);
        return t;
    }

    public static Mission load(CompoundTag t) {
        Mission m = new Mission(t.getUUID("owner"), t.getInt("slot"), t.getLong("seed"),
                t.getInt("mapSize"), t.getInt("objCount"), t.getInt("hidden"));
        m.x = t.getDouble("x");
        m.z = t.getDouble("z");
        m.heading = t.getDouble("heading");
        m.depth = t.getDouble("depth");
        m.hull = t.getFloat("hull");
        m.power = Power.values()[Math.min(Power.values().length - 1, t.getInt("power"))];
        if (m.power == Power.RESTORING) m.power = Power.TRIPPED;
        m.cameraCondition = t.getFloat("camCond");
        m.sealant = t.getInt("sealant");
        m.phase = Phase.values()[Math.min(Phase.values().length - 1, t.getInt("phase"))];
        m.phaseTicks = t.getInt("phaseTicks");
        m.ticks = t.getLong("ticks");
        m.collisions = t.getInt("collisions");
        m.powerFailures = t.getInt("powerFailures");
        m.sightings = t.getInt("sightings");
        m.eventsTotal = t.getInt("eventsTotal");
        m.nextEventTick = t.getLong("nextEvent");
        m.lastMajorTick = t.getLong("lastMajor");
        m.leviathanDone = t.getBoolean("leviathan");
        m.photoAnomalyArmed = t.getBoolean("anomalyArmed");
        m.lastPowerFailTick = t.getLong("lastPowerFail");
        byte[] done = t.getByteArray("done");
        m.objectiveDone = new boolean[done.length];
        for (int i = 0; i < done.length; i++) m.objectiveDone[i] = done[i] != 0;
        ListTag ph = t.getList("photos", Tag.TAG_COMPOUND);
        for (int i = 0; i < ph.size(); i++) m.photos.add(PhotoRecord.load(ph.getCompound(i)));
        for (int a : t.getIntArray("anomalies")) m.anomaliesFound.add(a);
        m.returnDim = t.getString("retDim");
        m.returnX = t.getDouble("retX");
        m.returnY = t.getDouble("retY");
        m.returnZ = t.getDouble("retZ");
        m.returnYaw = t.getFloat("retYaw");
        m.returnPitch = t.getFloat("retPitch");
        m.map();
        return m;
    }
}
