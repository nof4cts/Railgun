package com.ironlung.mission;

import com.ironlung.ocean.OceanMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything about one pilot's dive. Persisted through {@link MissionSavedData}. */
public final class Mission {
    public enum Power { ONLINE, TRIPPED, RESTORING }
    public enum Phase { DIVING, ASCENDING, IMPLODING, DONE }

    /** Where a crew member goes back to when the dive ends. */
    public static final class ReturnPoint {
        public String dim = "minecraft:overworld";
        public double x, y, z;
        public float yaw, pitch;

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dim);
            t.putDouble("x", x);
            t.putDouble("y", y);
            t.putDouble("z", z);
            t.putFloat("yaw", yaw);
            t.putFloat("pitch", pitch);
            return t;
        }

        public static ReturnPoint load(CompoundTag t) {
            ReturnPoint r = new ReturnPoint();
            r.dim = t.getString("dim");
            r.x = t.getDouble("x");
            r.y = t.getDouble("y");
            r.z = t.getDouble("z");
            r.yaw = t.getFloat("yaw");
            r.pitch = t.getFloat("pitch");
            return r;
        }
    }

    public static final class CrewMember {
        public final UUID id;
        public String name;
        public ReturnPoint ret = new ReturnPoint();
        public transient float throttle, turn, ballast;

        public CrewMember(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public final UUID id;
    public UUID leader;
    public final Map<UUID, CrewMember> crew = new LinkedHashMap<>();
    public transient UUID camOperator;
    public final int slot;
    public final long seed;
    public final int mapSize, objectiveCount, hiddenCount;
    private transient OceanMap map;

    // submarine
    public double x, z, heading, depth, speed, turnRate, vertSpeed;
    public float hull = 1.0f;
    public Power power = Power.ONLINE;
    public int restoreTicks;
    public int flickerTicks;
    public float cameraCondition = 1.0f;
    public int sealant;
    public int weldTicks;
    public transient UUID welder;
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


    public Mission(UUID id, UUID leader, int slot, long seed, int mapSize, int objectiveCount, int hiddenCount) {
        this.id = id;
        this.leader = leader;
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

    public float throttle() {
        float v = 0;
        for (CrewMember c : crew.values()) v += c.throttle;
        return Math.max(-1, Math.min(1, v));
    }

    public float turn() {
        float v = 0;
        for (CrewMember c : crew.values()) v += c.turn;
        return Math.max(-1, Math.min(1, v));
    }

    public float ballast() {
        float v = 0;
        for (CrewMember c : crew.values()) v += c.ballast;
        return Math.max(-1, Math.min(1, v));
    }

    public void clearInputs() {
        for (CrewMember c : crew.values()) c.throttle = c.turn = c.ballast = 0;
    }

    public boolean powered() {
        return power == Power.ONLINE;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("leader", leader);
        ListTag cl = new ListTag();
        for (CrewMember c : crew.values()) {
            CompoundTag ct = c.ret.save();
            ct.putUUID("uuid", c.id);
            ct.putString("name", c.name);
            cl.add(ct);
        }
        t.put("crew", cl);
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
        return t;
    }

    public static Mission load(CompoundTag t) {
        Mission m = new Mission(t.getUUID("id"), t.getUUID("leader"), t.getInt("slot"), t.getLong("seed"),
                t.getInt("mapSize"), t.getInt("objCount"), t.getInt("hidden"));
        ListTag cl = t.getList("crew", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag ct = cl.getCompound(i);
            CrewMember c = new CrewMember(ct.getUUID("uuid"), ct.getString("name"));
            c.ret = ReturnPoint.load(ct);
            m.crew.put(c.id, c);
        }
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
        m.map();
        return m;
    }
}
