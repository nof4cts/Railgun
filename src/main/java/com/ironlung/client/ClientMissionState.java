package com.ironlung.client;

import com.ironlung.IronLungMod;
import com.ironlung.feed.FeedRenderer;
import com.ironlung.mission.CameraMount;
import com.ironlung.mission.HorrorType;
import com.ironlung.mission.Mission;
import com.ironlung.mission.PhotoRecord;
import com.ironlung.mission.SubInterior;
import com.ironlung.network.Packets;
import com.ironlung.ocean.OceanMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** Everything the client knows about the current dive. Only touched on the render/client thread. */
public final class ClientMissionState {
    private ClientMissionState() {}

    public static boolean active;
    public static long seed;
    public static int mapSize, objectiveCount, hiddenCount, slot;
    public static OceanMap map;
    public static boolean[] done = new boolean[0];
    public static final List<PhotoRecord> photos = new ArrayList<>();
    public static final List<Integer> newlyLogged = new ArrayList<>();
    public static final List<String> crew = new ArrayList<>();
    public static boolean leader = true;
    /** Last time the local player steered the camera; while recent, local aim wins over the server's. */
    public static long lastCamInputNanos;

    // interpolated submarine state
    public static Packets.SubState prev, cur;
    public static long curArrival;

    // camera (client authoritative for aiming; reported to server)
    public static int camMount;
    public static float camPan, camTilt;

    // effects
    public static float shake;
    public static int shakeLongTicks;
    public static int glitchTicks;
    public static int silenceTicks;
    public static int flashTicks;
    public static int spikeTicks;
    public static float spikeAmount;
    public static int introTicks;
    public static int jumpscareTicks, jumpscareTotal = 1;
    /** Ticks to show the external camera inset to crew who are not at the monitor. */
    public static int feedInsetTicks;
    public static int chaosTicks, grabbedTicks, goreFlash;
    public static boolean finaleBlack;
    public static int lastPhotoId = -1;
    public static long developStart;
    public static String toast;
    public static int toastTicks;
    public static final List<FeedRenderer.Transient> transients = new CopyOnWriteArrayList<>();
    private static final long T0 = System.nanoTime();

    public static Packets.MissionEnd pendingSummary;

    public static double now() {
        return (System.nanoTime() - T0) / 1e9;
    }

    public static void init(Packets.MissionInit m) {
        if (!m.active) {
            clear();
            return;
        }
        boolean fresh = !active || seed != m.seed;
        active = true;
        seed = m.seed;
        mapSize = m.mapSize;
        objectiveCount = m.objectiveCount;
        hiddenCount = m.hiddenCount;
        slot = m.slot;
        if (fresh || map == null) {
            map = new OceanMap(seed, mapSize, objectiveCount, hiddenCount);
            FeedSystem.reset(map);
            camMount = 0;
            camPan = 0;
            camTilt = 0;
        }
        done = m.done;
        crew.clear();
        crew.addAll(m.crew);
        leader = m.leader;
        photos.clear();
        photos.addAll(m.photos);
        if (fresh) {
            if (com.ironlung.config.IronLungConfig.OPENING_CUTSCENE.get()) {
                introTicks = 0;
                Cutscene.start();
            } else {
                introTicks = 160;
            }
        }
        prev = cur = null;
    }

    public static void clear() {
        active = false;
        map = null;
        prev = cur = null;
        photos.clear();
        crew.clear();
        transients.clear();
        done = new boolean[0];
        glitchTicks = silenceTicks = flashTicks = spikeTicks = shakeLongTicks = introTicks = jumpscareTicks = feedInsetTicks = chaosTicks = grabbedTicks = goreFlash = 0;
        finaleBlack = false;
        shake = 0;
        FeedSystem.release();
        Cutscene.stop();
    }

    public static void onState(Packets.SubState s) {
        prev = cur == null ? s : cur;
        cur = s;
        curArrival = System.nanoTime();
        boolean steeringRecently = System.nanoTime() - lastCamInputNanos < 600_000_000L;
        if (!s.youOperate && !steeringRecently) {
            camMount = s.camMount;
            camPan = s.camPan;
            camTilt = s.camTilt;
        }
        for (int i = 0; i < done.length; i++) {
            boolean d = (s.doneMask & (1 << i)) != 0;
            done[i] = d;
        }
    }

    /** Interpolation factor between the last two server states (sent every 2 ticks). */
    private static double alpha() {
        double dt = (System.nanoTime() - curArrival) / 1e9;
        return Math.max(0, Math.min(1, dt / 0.1));
    }

    public static double x() {
        return cur == null ? (map == null ? 0 : map.startX) : lerp(prev.x, cur.x, alpha());
    }

    public static double z() {
        return cur == null ? (map == null ? 0 : map.startZ) : lerp(prev.z, cur.z, alpha());
    }

    public static double depth() {
        return cur == null ? 100 : lerp(prev.depth, cur.depth, alpha());
    }

    public static double heading() {
        if (cur == null) return 0;
        double a = prev.heading, b = cur.heading;
        double d = b - a;
        while (d > Math.PI) d -= Math.PI * 2;
        while (d < -Math.PI) d += Math.PI * 2;
        return a + d * alpha();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static boolean powered() {
        return cur != null && cur.power == Mission.Power.ONLINE.ordinal();
    }

    public static Mission.Power power() {
        return cur == null ? Mission.Power.ONLINE : Mission.Power.values()[cur.power];
    }

    public static float hull() {
        return cur == null ? 1f : cur.hull;
    }

    public static Mission.Phase phase() {
        return cur == null ? Mission.Phase.DIVING : Mission.Phase.values()[cur.phase];
    }

    public static boolean inSub() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.level != null && mc.level.dimension().equals(IronLungMod.DIMENSION);
    }

    public static BlockPos base() {
        return SubInterior.base(slot);
    }

    public static double[] cameraPose() {
        return CameraMount.byId(camMount).pose(x(), z(), heading(), depth(), camPan, camTilt);
    }

    public static boolean allDone() {
        if (done.length == 0) return false;
        for (boolean b : done) if (!b) return false;
        return true;
    }

    public static void toast(String text, int ticks) {
        toast = text;
        toastTicks = ticks;
    }

    // ------------------------------------------------------------------ events

    public static void onHorror(Packets.HorrorEvent e) {
        HorrorType type = HorrorType.byId(e.type);
        Random rnd = new Random(e.seed);
        addShake(e.intensity);
        double t = now();
        double[] cam = cameraPose();
        double cx = cam[0], cy = cam[1], cz = cam[2], yaw = cam[3];
        double fx = Math.sin(yaw), fz = -Math.cos(yaw);
        double rx = Math.cos(yaw), rz = Math.sin(yaw);
        switch (type) {
            case SCRAPE -> shakeLongTicks = (int) (e.a * 20);
            case FLICKER -> glitchTicks = Math.max(glitchTicks, 10);
            case BLACKOUT -> glitchTicks = Math.max(glitchTicks, 15);
            case CAMERA_GLITCH -> glitchTicks = Math.max(glitchTicks, (int) (e.a * 20));
            case SILENCE -> silenceTicks = (int) (e.a * 20);
            case PRESSURE_SPIKE -> {
                spikeTicks = 50;
                spikeAmount = e.a;
            }
            case SILHOUETTE -> {
                double dist = 16 + rnd.nextDouble() * 12;
                double side = e.b;
                double dur = e.a;
                double span = dist * 1.1;
                double sx = cx + fx * dist - rx * span * side;
                double sz = cz + fz * dist - rz * span * side;
                double vx = rx * side * span * 2 / dur, vz = rz * side * span * 2 / dur;
                double sy = cy - 1.5 + rnd.nextDouble() * 3;
                double oy = Math.atan2(-vx, vz);
                transients.add(new FeedRenderer.Transient(FeedRenderer.Transient.SILHOUETTE, sx, sy, sz, vx, (rnd.nextDouble() - 0.5) * 0.3, vz,
                        0.8 + rnd.nextDouble() * 0.7, oy, t, dur));
                feedInsetTicks = (int) (dur * 20) + 20;
            }
            case LEVIATHAN -> {
                boolean finale = e.b > 0.5f;
                double dist = finale ? 36 : 52;
                double size = finale ? 22 : 28;
                double px = cx + fx * (dist + size), pz = cz + fz * (dist + size);
                double py = cy + (finale ? 0 : -4);
                double dx = cx - px, dz = cz - pz;
                double oy = Math.atan2(dx, -dz);
                transients.add(new FeedRenderer.Transient(FeedRenderer.Transient.LEVIATHAN, px, py, pz, rx * 1.2, 0.1, rz * 1.2, size, oy, t, e.a));
                glitchTicks = Math.max(glitchTicks, 40);
                shakeLongTicks = (int) (e.a * 20);
                feedInsetTicks = (int) (e.a * 20) + 20;
            }
            case EYES -> {
                double dist = 13 + rnd.nextDouble() * 6;
                double off = (rnd.nextDouble() - 0.5) * dist * 0.6;
                double px = cx + fx * dist + rx * off, pz = cz + fz * dist + rz * off;
                transients.add(new FeedRenderer.Transient(FeedRenderer.Transient.EYES, px, cy + (rnd.nextDouble() - 0.6) * 2, pz, 0, 0, 0,
                        1.0, yaw, t, e.a));
                feedInsetTicks = (int) (e.a * 20) + 20;
            }
            case IMPACT -> {
                glitchTicks = Math.max(glitchTicks, 12);
                if (e.a > 0.5f) flashTicks = 0;
            }
            case FINALE_CHAOS -> {
                chaosTicks = (int) (e.a * 20);
                shake = 2.5f;
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen != null) mc.setScreen(null);
            }
            case GRABBED -> {
                grabbedTicks = (int) (e.a * 20) + 12;
                shake = 2.2f;
                shakeLongTicks = grabbedTicks;
            }
            case GORE -> {
                goreFlash = 10;
                addShake(1.5f);
            }
            case JUMPSCARE_PRIME -> FeedSystem.prepareJumpscare();
            case JUMPSCARE -> {
                jumpscareTicks = (int) (e.a * 20);
                jumpscareTotal = Math.max(1, jumpscareTicks);
                finaleBlack = true;
                shake = 2.5f;
                shakeLongTicks = jumpscareTicks;
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen != null) mc.setScreen(null);
            }
            default -> {}
        }
    }

    public static void addShake(float amount) {
        shake = Math.min(2.5f, shake + amount);
    }

    public static void onPhoto(Packets.PhotoResult r) {
        photos.add(r.photo);
        lastPhotoId = r.photo.id;
        developStart = System.nanoTime();
        flashTicks = 6;
        FeedSystem.requestPhoto(r.photo);
        StringBuilder sb = new StringBuilder();
        for (int i : r.completed) {
            if (sb.length() > 0) sb.append("  ");
            sb.append("SITE ").append(String.format("%02d", i + 1)).append(" LOGGED");
            if (i < done.length) done[i] = true;
        }
        if (r.anomalyFound) {
            if (sb.length() > 0) sb.append("  ");
            sb.append("UNCATALOGUED SUBJECT RECORDED");
        }
        if (r.photo.has(PhotoRecord.FLAG_RUINED)) sb.append(sb.length() > 0 ? "  " : "").append("EXPOSURE LOST");
        if (sb.length() > 0) toast(sb.toString(), 120);
    }

    public static void tick() {
        if (shake > 0) shake = Math.max(0, shake * 0.86f - 0.004f);
        if (shakeLongTicks > 0) {
            shakeLongTicks--;
            shake = Math.max(shake, 0.18f);
        }
        if (glitchTicks > 0) glitchTicks--;
        if (cur != null && cur.glitch > 0) glitchTicks = Math.max(glitchTicks, 2);
        if (silenceTicks > 0) silenceTicks--;
        if (flashTicks > 0) flashTicks--;
        if (spikeTicks > 0) spikeTicks--;
        if (introTicks > 0) introTicks--;
        if (jumpscareTicks > 0) jumpscareTicks--;
        if (feedInsetTicks > 0) feedInsetTicks--;
        if (chaosTicks > 0) {
            chaosTicks--;
            shake = Math.max(shake, 0.35f);
        }
        if (grabbedTicks > 0) grabbedTicks--;
        if (goreFlash > 0) goreFlash--;
        if (toastTicks > 0) toastTicks--;
        double t = now();
        transients.removeIf(tr -> t > tr.start + tr.duration + 1);
    }
}
