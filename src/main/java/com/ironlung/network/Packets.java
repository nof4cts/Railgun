package com.ironlung.network;

import com.ironlung.mission.MissionManager;
import com.ironlung.mission.PhotoRecord;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** All packets. S2C handlers bounce to client-only code through DistExecutor. */
public final class Packets {
    private Packets() {}

    // ------------------------------------------------------------------ S2C

    public static final class MissionInit {
        public final boolean active;
        public final long seed;
        public final int mapSize, objectiveCount, hiddenCount, slot;
        public final boolean[] done;
        public final List<PhotoRecord> photos;
        public final long ticks;
        public final List<String> crew;
        public final boolean leader;

        public MissionInit(boolean active, long seed, int mapSize, int objectiveCount, int hiddenCount, int slot,
                           boolean[] done, List<PhotoRecord> photos, long ticks, List<String> crew, boolean leader) {
            this.crew = crew;
            this.leader = leader;
            this.active = active;
            this.seed = seed;
            this.mapSize = mapSize;
            this.objectiveCount = objectiveCount;
            this.hiddenCount = hiddenCount;
            this.slot = slot;
            this.done = done;
            this.photos = photos;
            this.ticks = ticks;
        }

        public static MissionInit inactive() {
            return new MissionInit(false, 0, 0, 0, 0, 0, new boolean[0], new ArrayList<>(), 0, new ArrayList<>(), false);
        }

        public void encode(FriendlyByteBuf b) {
            b.writeBoolean(active);
            b.writeLong(seed);
            b.writeVarInt(mapSize);
            b.writeVarInt(objectiveCount);
            b.writeVarInt(hiddenCount);
            b.writeVarInt(slot);
            b.writeVarInt(done.length);
            for (boolean d : done) b.writeBoolean(d);
            b.writeVarInt(photos.size());
            for (PhotoRecord p : photos) p.write(b);
            b.writeLong(ticks);
            b.writeVarInt(crew.size());
            for (String c : crew) b.writeUtf(c, 64);
            b.writeBoolean(leader);
        }

        public static MissionInit decode(FriendlyByteBuf b) {
            boolean active = b.readBoolean();
            long seed = b.readLong();
            int ms = b.readVarInt(), oc = b.readVarInt(), hc = b.readVarInt(), slot = b.readVarInt();
            boolean[] done = new boolean[b.readVarInt()];
            for (int i = 0; i < done.length; i++) done[i] = b.readBoolean();
            int n = b.readVarInt();
            List<PhotoRecord> photos = new ArrayList<>();
            for (int i = 0; i < n; i++) photos.add(PhotoRecord.read(b));
            long ticks = b.readLong();
            int cn = b.readVarInt();
            List<String> crew = new ArrayList<>();
            for (int i = 0; i < cn; i++) crew.add(b.readUtf(64));
            return new MissionInit(active, seed, ms, oc, hc, slot, done, photos, ticks, crew, b.readBoolean());
        }

        public static void handle(MissionInit m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironlung.client.ClientPacketHandler.onInit(m));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class SubState {
        public final double x, z, heading, depth;
        public final float speed, hull, restore, cameraCondition;
        public final int power, phase, sealant, doneMask, glitch, photoCooldown;
        public final boolean welding;
        public final float[] prox;
        public final int camMount;
        public final float camPan, camTilt;
        public final boolean youOperate;

        public SubState(double x, double z, double heading, double depth, float speed, float hull, int power, float restore,
                        float cameraCondition, float[] prox, int doneMask, int phase, int sealant, boolean welding, int glitch, int photoCooldown,
                        int camMount, float camPan, float camTilt, boolean youOperate) {
            this.camMount = camMount;
            this.camPan = camPan;
            this.camTilt = camTilt;
            this.youOperate = youOperate;
            this.x = x;
            this.z = z;
            this.heading = heading;
            this.depth = depth;
            this.speed = speed;
            this.hull = hull;
            this.power = power;
            this.restore = restore;
            this.cameraCondition = cameraCondition;
            this.prox = prox;
            this.doneMask = doneMask;
            this.phase = phase;
            this.sealant = sealant;
            this.welding = welding;
            this.glitch = glitch;
            this.photoCooldown = photoCooldown;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeDouble(x);
            b.writeDouble(z);
            b.writeDouble(heading);
            b.writeDouble(depth);
            b.writeFloat(speed);
            b.writeFloat(hull);
            b.writeVarInt(power);
            b.writeFloat(restore);
            b.writeFloat(cameraCondition);
            for (int i = 0; i < 4; i++) b.writeFloat(prox[i]);
            b.writeVarInt(doneMask);
            b.writeVarInt(phase);
            b.writeVarInt(sealant);
            b.writeBoolean(welding);
            b.writeVarInt(glitch);
            b.writeVarInt(photoCooldown);
            b.writeVarInt(camMount);
            b.writeFloat(camPan);
            b.writeFloat(camTilt);
            b.writeBoolean(youOperate);
        }

        public static SubState decode(FriendlyByteBuf b) {
            double x = b.readDouble(), z = b.readDouble(), h = b.readDouble(), d = b.readDouble();
            float sp = b.readFloat(), hull = b.readFloat();
            int pw = b.readVarInt();
            float rs = b.readFloat(), cc = b.readFloat();
            float[] prox = new float[4];
            for (int i = 0; i < 4; i++) prox[i] = b.readFloat();
            int mask = b.readVarInt(), phase = b.readVarInt(), seal = b.readVarInt();
            boolean weld = b.readBoolean();
            int glitch = b.readVarInt(), cd = b.readVarInt();
            int cm = b.readVarInt();
            float cp = b.readFloat(), ct = b.readFloat();
            return new SubState(x, z, h, d, sp, hull, pw, rs, cc, prox, mask, phase, seal, weld, glitch, cd, cm, cp, ct, b.readBoolean());
        }

        public static void handle(SubState m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironlung.client.ClientPacketHandler.onState(m));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class HorrorEvent {
        public final int type;
        public final float intensity;
        public final long seed;
        public final float a, b;

        public HorrorEvent(int type, float intensity, long seed, float a, float b) {
            this.type = type;
            this.intensity = intensity;
            this.seed = seed;
            this.a = a;
            this.b = b;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(type);
            buf.writeFloat(intensity);
            buf.writeLong(seed);
            buf.writeFloat(a);
            buf.writeFloat(b);
        }

        public static HorrorEvent decode(FriendlyByteBuf buf) {
            return new HorrorEvent(buf.readVarInt(), buf.readFloat(), buf.readLong(), buf.readFloat(), buf.readFloat());
        }

        public static void handle(HorrorEvent m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironlung.client.ClientPacketHandler.onHorror(m));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class PhotoResult {
        public final PhotoRecord photo;
        public final int[] completed;
        public final boolean anomalyFound;

        public PhotoResult(PhotoRecord photo, int[] completed, boolean anomalyFound) {
            this.photo = photo;
            this.completed = completed;
            this.anomalyFound = anomalyFound;
        }

        public void encode(FriendlyByteBuf b) {
            photo.write(b);
            b.writeVarIntArray(completed);
            b.writeBoolean(anomalyFound);
        }

        public static PhotoResult decode(FriendlyByteBuf b) {
            return new PhotoResult(PhotoRecord.read(b), b.readVarIntArray(), b.readBoolean());
        }

        public static void handle(PhotoResult m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironlung.client.ClientPacketHandler.onPhoto(m));
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class MissionEnd {
        public static final int RECOVERED = 0, ABORTED = 1, LOST = 2, TAKEN = 3;
        public final int outcome;
        public final long ticks;
        public final int objectivesDone, objectivesTotal, photos, anomalies, collisions, powerFailures, sightings;
        public final float hull;

        public MissionEnd(int outcome, long ticks, int objectivesDone, int objectivesTotal, int photos, int anomalies,
                          float hull, int collisions, int powerFailures, int sightings) {
            this.outcome = outcome;
            this.ticks = ticks;
            this.objectivesDone = objectivesDone;
            this.objectivesTotal = objectivesTotal;
            this.photos = photos;
            this.anomalies = anomalies;
            this.hull = hull;
            this.collisions = collisions;
            this.powerFailures = powerFailures;
            this.sightings = sightings;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(outcome);
            b.writeLong(ticks);
            b.writeVarInt(objectivesDone);
            b.writeVarInt(objectivesTotal);
            b.writeVarInt(photos);
            b.writeVarInt(anomalies);
            b.writeFloat(hull);
            b.writeVarInt(collisions);
            b.writeVarInt(powerFailures);
            b.writeVarInt(sightings);
        }

        public static MissionEnd decode(FriendlyByteBuf b) {
            return new MissionEnd(b.readVarInt(), b.readLong(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }

        public static void handle(MissionEnd m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironlung.client.ClientPacketHandler.onEnd(m));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C2S

    public static final class HelmInput {
        public final float throttle, turn, ballast;

        public HelmInput(float throttle, float turn, float ballast) {
            this.throttle = throttle;
            this.turn = turn;
            this.ballast = ballast;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeFloat(throttle);
            b.writeFloat(turn);
            b.writeFloat(ballast);
        }

        public static HelmInput decode(FriendlyByteBuf b) {
            return new HelmInput(b.readFloat(), b.readFloat(), b.readFloat());
        }

        public static void handle(HelmInput m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) MissionManager.onHelmInput(sp, m.throttle, m.turn, m.ballast);
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class CameraState {
        public final int mount;
        public final float pan, tilt;
        public final boolean capture;
        public final boolean steering;

        public CameraState(int mount, float pan, float tilt, boolean capture, boolean steering) {
            this.steering = steering;
            this.mount = mount;
            this.pan = pan;
            this.tilt = tilt;
            this.capture = capture;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(mount);
            b.writeFloat(pan);
            b.writeFloat(tilt);
            b.writeBoolean(capture);
            b.writeBoolean(steering);
        }

        public static CameraState decode(FriendlyByteBuf b) {
            return new CameraState(b.readVarInt(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean());
        }

        public static void handle(CameraState m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) MissionManager.onCamera(sp, m.mount, m.pan, m.tilt, m.capture, m.steering);
            ctx.get().setPacketHandled(true);
        }
    }

    public static final class Surface {
        public final boolean abort;

        public Surface(boolean abort) {
            this.abort = abort;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeBoolean(abort);
        }

        public static Surface decode(FriendlyByteBuf b) {
            return new Surface(b.readBoolean());
        }

        public static void handle(Surface m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) MissionManager.onSurface(sp, m.abort);
            ctx.get().setPacketHandled(true);
        }
    }
}
