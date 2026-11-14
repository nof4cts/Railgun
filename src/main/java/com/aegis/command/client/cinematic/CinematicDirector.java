package com.aegis.command.client.cinematic;

import com.aegis.command.AegisConfig;
import com.aegis.command.client.ClientData;
import com.aegis.command.client.Sfx;
import com.aegis.command.client.fx.ImpactFrames;
import com.aegis.command.client.fx.ScreenFx;
import com.aegis.command.client.screen.LaptopScreen;
import com.aegis.command.client.ui.Ease;
import com.aegis.command.entity.AerialBomb;
import com.aegis.command.entity.CinematicCamera;
import com.aegis.command.mission.AircraftType;
import com.aegis.command.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Directs the strike as four cutscenes:
 * A (activation) plays on the laptop itself — alert state and a push toward the display;
 * B (deployment) — aircraft emerges from cloud, low flyby then chase camera;
 * C (strike) — wide ground establishing shot, release, impacts, camera slow-push with time dilation;
 * D (complete) — overhead orbit of the smoke column, fade back to the laptop debrief.
 * The flight plan is deterministic, so every shot is computed from world time with no lag.
 */
public final class CinematicDirector {
    public enum Phase { NONE, ALERT, TRANSITION, CINEMATIC }

    private static final RandomSource R = RandomSource.create();

    private static Phase phase = Phase.NONE;
    private static CompoundTag plan;
    private static long alertStartMs;
    private static int alertDelayTicks = 50;
    private static long transitionMs;
    private static long cineStartMs;
    private static CinematicCamera cam;
    private static boolean prevHideGui;
    private static boolean awaitingDebrief;
    private static boolean bombsAway;
    private static int impacts;
    private static long firstImpactMs = -1;
    private static double impactClock = -1;
    private static double cineClock;
    private static long lastFrameNs;
    private static int shot = -1;

    private static float camYaw, camPitch, camRoll;

    private record Line(double t, String text) {}

    private static final List<Line> script = new ArrayList<>();
    private static int nextLine;
    public static String subtitle = "";
    public static long subtitleMs;

    private CinematicDirector() {}

    // ------------------------------------------------------------------ queries

    public static Phase phase() { return phase; }
    public static CompoundTag plan() { return plan; }
    public static boolean awaitingDebrief() { return awaitingDebrief && ClientData.pendingDebrief == null; }
    public static int shot() { return shot; }

    public static float alertProgress() {
        if (phase == Phase.TRANSITION) return 1;
        return Mth.clamp((System.currentTimeMillis() - alertStartMs) / (alertDelayTicks * 50f), 0, 1);
    }

    public static float alertSecondsLeft() {
        return Math.max(0, alertDelayTicks / 20f - (System.currentTimeMillis() - alertStartMs) / 1000f);
    }

    public static float transitionProgress() {
        return Ease.inCubic((System.currentTimeMillis() - transitionMs) / 600f);
    }

    private static AircraftType aircraft() { return AircraftType.byIndex(plan.getInt("Ac")); }

    private static Vec3 planPos(double t) {
        double sp = aircraft().speed;
        return new Vec3(plan.getDouble("SX") + plan.getDouble("DX") * sp * t, plan.getDouble("SY"),
                plan.getDouble("SZ") + plan.getDouble("DZ") * sp * t);
    }

    public static double flightT(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (plan == null || mc.level == null) return -1;
        return mc.level.getGameTime() + partial - plan.getLong("Spawn");
    }

    /** Aircraft position from the flight plan, or null when no aircraft is flying. */
    public static Vec3 jetPos(float partial) {
        if (plan == null) return null;
        double t = flightT(partial);
        if (t < 0 || t > plan.getInt("Life") + 40) return null;
        return planPos(t);
    }

    private static double endT() {
        return plan.getInt("LastDrop") + plan.getInt("Fall") + 115;
    }

    /** 0..1 black overlay for the cinematic fade in/out. */
    public static float fade() {
        if (phase != Phase.CINEMATIC || plan == null) return 0;
        float in = 1 - Ease.outCubic((System.currentTimeMillis() - cineStartMs) / 800f);
        double t = flightT(Minecraft.getInstance().getFrameTime());
        float out = (float) Mth.clamp((t - (endT() - 18)) / 18.0, 0, 1);
        return Math.max(in, out);
    }

    public static float letterbox() {
        if (phase != Phase.CINEMATIC) return 0;
        return Ease.outCubic((System.currentTimeMillis() - cineStartMs) / 900f);
    }

    // ------------------------------------------------------------------ events

    public static void onAccepted(String codename, int delayTicks) {
        phase = Phase.ALERT;
        plan = null;
        alertStartMs = System.currentTimeMillis();
        alertDelayTicks = Math.max(1, delayTicks);
        bombsAway = false;
        impacts = 0;
        firstImpactMs = -1;
        awaitingDebrief = false;
        ClientData.status = "ALERT";
        ClientData.activeCodename = codename;
        ClientData.pendingDebrief = null;
        ClientData.feed("STRIKE AUTHORIZED: " + codename, ClientData.Level.ALERT);
        ClientData.feed("WEAPONS FREE // AIRCRAFT LAUNCH IN " + String.format("%.1f", delayTicks / 20f) + "s", ClientData.Level.WARN);
        Sfx.alarm(Math.max(2, delayTicks / 10));
        Sfx.raw(SoundEvents.BEACON_ACTIVATE, 0.7f, 0.6f);
        ScreenFx.addTrauma(0.15f);
    }

    public static void onLaunched(CompoundTag p) {
        Minecraft mc = Minecraft.getInstance();
        plan = p;
        AircraftType ac = aircraft();
        ClientData.status = "INBOUND";
        ClientData.activeCodename = p.getString("Code");
        double tot = (p.getInt("FirstDrop") + p.getInt("Fall")) / 20.0;
        ClientData.feed(ac.callsign + " AIRBORNE // " + ac.displayName + " // TIME ON TARGET " + String.format("%.1fs", tot), ClientData.Level.WARN);
        buildScript(ac);

        mc.getSoundManager().play(new JetSound(SoundEvents.ELYTRA_FLYING, ac == AircraftType.GOLIATH ? 0.45f : 0.6f, 1.0f, 170));
        mc.getSoundManager().play(new JetSound(SoundEvents.ELYTRA_FLYING, ac == AircraftType.WRAITH ? 1.3f : 0.95f, 0.55f, 110));

        // cloud bank the aircraft bursts out of
        if (mc.level != null) {
            Vec3 fwd = new Vec3(p.getDouble("DX"), 0, p.getDouble("DZ"));
            for (int i = 0; i < 220; i++) {
                Vec3 c = planPos(R.nextDouble() * 18 / ac.speed).add(fwd.scale(-6 + R.nextDouble() * 12))
                        .add(R.nextGaussian() * 7, R.nextGaussian() * 3, R.nextGaussian() * 7);
                mc.level.addAlwaysVisibleParticle(i % 3 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD, true, c.x, c.y, c.z,
                        R.nextGaussian() * 0.02, 0.005, R.nextGaussian() * 0.02);
            }
        }

        boolean cine = AegisConfig.CLIENT.cinematicCamera.get();
        if (cine && mc.screen instanceof LaptopScreen) {
            phase = Phase.TRANSITION;
            transitionMs = System.currentTimeMillis();
            Sfx.raw(SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 0.35f);
        } else if (cine) {
            beginCinematic();
        } else {
            phase = Phase.NONE;
        }
    }

    private static void buildScript(AircraftType ac) {
        script.clear();
        nextLine = 0;
        String cs = ac.callsign;
        int fd = plan.getInt("FirstDrop"), fall = plan.getInt("Fall"), ld = plan.getInt("LastDrop");
        script.add(new Line(2, cs + ": Aegis, " + cs + " on station. Package is hot."));
        script.add(new Line(Math.max(14, fd - 28), "AEGIS: " + cs + ", target confirmed. You are cleared hot."));
        script.add(new Line(fd, cs + ": Weapons away. Breaking off."));
        script.add(new Line(fd + fall + 10, "AEGIS: Splash. Good effect on target."));
        script.add(new Line(ld + fall + 55, cs + ": " + cs + " returning to base. Aegis out."));
    }

    private static void beginCinematic() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || plan == null) { phase = Phase.NONE; return; }
        cam = new CinematicCamera(ModEntities.CINEMATIC_CAMERA.get(), mc.level);
        Vec3 j = jetPos(0);
        if (j != null) cam.place(j.x, j.y, j.z, 0, 0);
        prevHideGui = mc.options.hideGui;
        mc.options.hideGui = true;
        mc.setCameraEntity(cam);
        phase = Phase.CINEMATIC;
        cineStartMs = System.currentTimeMillis();
        lastFrameNs = System.nanoTime();
        cineClock = 0;
        impactClock = -1;
        shot = -1;
        riderId = -1;
        awaitingDebrief = true;
        mc.setScreen(new CinematicScreen());
    }

    public static void end(boolean skipped) {
        if (phase != Phase.CINEMATIC) return;
        Minecraft mc = Minecraft.getInstance();
        phase = Phase.NONE;
        mc.options.hideGui = prevHideGui;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        cam = null;
        shot = -1;
        if (skipped) ClientData.feed("CINEMATIC FEED CLOSED // TRACKING ON GRID", ClientData.Level.INFO);
        if (mc.screen != null && !(mc.screen instanceof CinematicScreen)) {
            // another screen (e.g. death screen) took over; leave it alone
            if (ClientData.pendingDebrief == null) awaitingDebrief = false;
            return;
        }
        if (mc.player != null && mc.player.isAlive() && mc.player.distanceToSqr(Vec3.atCenterOf(ClientData.laptop)) < 100) {
            mc.setScreen(new LaptopScreen(ClientData.laptop, true));
        } else {
            mc.setScreen(null);
            if (ClientData.pendingDebrief == null) awaitingDebrief = false;
        }
    }

    public static void onImpact(double x, double y, double z) {
        ClientData.IMPACTS.add(new double[]{x, z, System.currentTimeMillis()});
        if (plan == null) return;
        double dx = x - plan.getDouble("TX"), dz = z - plan.getDouble("TZ");
        if (dx * dx + dz * dz > 50 * 50) return;
        impacts++;
        if (impacts == 1) {
            firstImpactMs = System.currentTimeMillis();
            impactClock = cineClock;
            if (phase == Phase.CINEMATIC) ImpactFrames.trigger(1.15f, 0xFFC27A);
            ClientData.status = "ACTIVE";
            ClientData.feed("IMPACT CONFIRMED // BDA IN PROGRESS", ClientData.Level.ALERT);
        }
    }

    public static void onComplete(CompoundTag rec) {
        Minecraft mc = Minecraft.getInstance();
        ClientData.pendingDebrief = rec;
        ClientData.status = "COMPLETE";
        ClientData.activeCodename = rec.getString("Code");
        ClientData.feed(rec.getString("Code") + " COMPLETE // " + rec.getInt("Kills") + " HOSTILES NEUTRALIZED", ClientData.Level.OK);
        awaitingDebrief = false;
        if (phase == Phase.ALERT || phase == Phase.TRANSITION) phase = Phase.NONE;
        if (phase != Phase.CINEMATIC && !(mc.screen instanceof LaptopScreen) && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("aegiscommand.msg.complete", rec.getString("Code")), false);
            Sfx.missionComplete();
        }
    }

    public static void reset() {
        if (phase == Phase.CINEMATIC) {
            Minecraft mc = Minecraft.getInstance();
            mc.options.hideGui = prevHideGui;
            if (mc.player != null) mc.setCameraEntity(mc.player);
        }
        phase = Phase.NONE;
        plan = null;
        cam = null;
        awaitingDebrief = false;
        ClientData.status = "STANDBY";
    }

    // ------------------------------------------------------------------ ticking

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (phase != Phase.NONE || plan != null) reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (phase == Phase.TRANSITION && now - transitionMs > 600) beginCinematic();
        if (phase == Phase.ALERT && plan == null && now - alertStartMs > alertDelayTicks * 50L + 6000) {
            phase = Phase.NONE;
            ClientData.status = "STANDBY";
            ClientData.feed("UPLINK TIMEOUT // STRIKE ABORTED", ClientData.Level.ALERT);
        }
        if (phase == Phase.CINEMATIC && (mc.player == null || !mc.player.isAlive() || cam == null || cam.level() != mc.level)) {
            end(true);
        }
        if (plan == null) return;

        double t = flightT(0);
        while (nextLine < script.size() && t >= script.get(nextLine).t) {
            Line l = script.get(nextLine++);
            subtitle = l.text;
            subtitleMs = now;
            Sfx.radio();
            ClientData.feed(l.text.toUpperCase(), ClientData.Level.INFO);
        }
        if (!bombsAway && t >= plan.getInt("FirstDrop")) {
            bombsAway = true;
            ClientData.status = "ACTIVE";
            ClientData.feed(aircraft().callsign + " // ORDNANCE RELEASED x" + plan.getInt("Bombs"), ClientData.Level.ALERT);
            Vec3 j = planPos(t);
            Sfx.at(SoundEvents.FIREWORK_ROCKET_LAUNCH, j.x, j.y, j.z, 0.5f, 4f);
            Sfx.at(SoundEvents.PHANTOM_SWOOP, j.x, j.y, j.z, 0.6f, 4f);
        }
        if (phase == Phase.CINEMATIC && t > endT()) end(false);
        if (phase != Phase.CINEMATIC && t > plan.getInt("Life") + 60 && !awaitingDebrief()) plan = null;
    }

    private static double groundY(double x, double z) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 64;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    /** Position and aim the camera rig for this frame. */
    public static void renderTick(float partial) {
        if (phase != Phase.CINEMATIC || cam == null || plan == null) return;
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1f, (nowNs - lastFrameNs) / 1e9f);
        lastFrameNs = nowNs;
        long nowMs = System.currentTimeMillis();
        // brief time dilation on the camera at the first impact
        float rate = firstImpactMs > 0 && nowMs - firstImpactMs < 1500 ? 0.25f + 0.75f * Ease.inCubic((nowMs - firstImpactMs) / 1500f) : 1f;
        cineClock += dt * rate;

        AircraftType ac = aircraft();
        double t = flightT(partial);
        Vec3 fwd = new Vec3(plan.getDouble("DX"), 0, plan.getDouble("DZ"));
        Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 target = new Vec3(plan.getDouble("TX"), plan.getDouble("TY"), plan.getDouble("TZ"));
        Vec3 jet = planPos(t);
        double fd = plan.getInt("FirstDrop"), fall = plan.getInt("Fall"), ld = plan.getInt("LastDrop");
        double flyby = Math.max(12, Math.min(40, fd * 0.45));

        Vec3 pos, look;
        float roll = 0;
        int s;
        double uplink = Math.min(30, flyby * 0.7);
        double impactT = fd + fall;
        AerialBomb rider = (t >= fd && t < impactT + 2 && impactClock < 0) ? ordnanceToFollow(target) : null;
        if (t < uplink) {
            // A2: orbital uplink - straight down from high altitude, rotating and pushing in as the lock resolves
            s = 4;
            float p = Ease.inOutCubic((float) Mth.clamp(t / uplink, 0, 1));
            double alt = 115 - 55 * p;
            pos = target.add(0, alt, 0);
            look = target.add(fwd.scale(0.05));
            roll = 30 * p - 15;
        } else if (t < flyby) {
            // B1: low flyby - fixed camera beside the flight path, aircraft roars past
            s = 0;
            Vec3 pass = planPos(flyby * 0.95);
            pos = pass.add(side.scale(10 + 4 * ac.scale)).add(0, -5, 0);
            look = jet;
            roll = -4;
        } else if (t < fd - 4) {
            // B2: chase camera, tightening as the release point approaches
            s = 1;
            double sway = Math.sin(t * 0.05) * 4;
            double tight = Mth.clamp((t - (fd - 30)) / 26.0, 0, 1);
            pos = jet.subtract(fwd.scale(16 + 3 * ac.scale - 6 * tight)).add(0, 3 + ac.scale - 2 * tight, 0).add(side.scale(sway * (1 - tight)));
            look = jet.add(fwd.scale(14)).add(0, -2 - 4 * tight, 0);
            roll = (float) (Math.sin(t * 0.06) * 4);
        } else if (rider != null) {
            // C1: ordnance cam - ride the first bomb down onto the target
            s = 5;
            Vec3 bp = rider.getPosition(partial);
            Vec3 v = rider.getDeltaMovement();
            Vec3 back = v.lengthSqr() > 1e-4 ? v.normalize() : new Vec3(0, -1, 0);
            pos = bp.subtract(back.scale(4.5)).add(side.scale(1.2)).add(0, 1.4, 0);
            look = bp.add(back.scale(6));
            roll = (float) (Math.sin(t * 0.4) * 3);
        } else if (t < fd + 2 && impactClock < 0) {
            // C0: release - under the aircraft, looking up at the bay as the weapons fall away
            s = 1;
            pos = jet.subtract(fwd.scale(10)).add(side.scale(6)).add(0, -7, 0);
            look = jet.add(0, -3, 0);
        } else if (t < ld + fall + 40) {
            // C2: wide ground shot for the impacts, then a slow push toward the blast
            s = 2;
            Vec3 anchor = target.add(side.scale(32)).subtract(fwd.scale(14));
            double gy = groundY(anchor.x, anchor.z);
            anchor = new Vec3(anchor.x, Math.max(target.y + 9, gy + 3), anchor.z);
            if (impactClock >= 0) {
                float push = Ease.inOutCubic((float) Mth.clamp((cineClock - impactClock) / 4.5, 0, 1));
                Vec3 close = target.add(side.scale(18)).subtract(fwd.scale(8)).add(0, 7, 0);
                anchor = anchor.lerp(close, push * 0.6);
            }
            pos = anchor;
            double tl = Ease.inOutCubic((float) Mth.clamp((t - (fd + 4)) / 16.0, 0, 1));
            look = impactClock >= 0 ? target.add(0, 4, 0) : jet.lerp(target.add(0, 3, 0), tl);
        } else {
            // D: aftermath - orbit the smoke column while craning up and away
            s = 3;
            double since = t - (ld + fall + 40);
            double base = Math.atan2(side.z, side.x);
            double a = base + since * 0.012;
            double crane = Ease.inOutCubic((float) Mth.clamp(since / 70.0, 0, 1));
            pos = target.add(Math.cos(a) * (30 + 14 * crane), 18 + 22 * crane, Math.sin(a) * (30 + 14 * crane));
            look = target.add(0, 5 + 3 * crane, 0);
        }
        shot = s;
        Vec3 d = look.subtract(pos);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        camYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        camPitch = (float) -(Mth.atan2(d.y, h) * Mth.RAD_TO_DEG);
        camRoll = roll;
        cam.place(pos.x, pos.y, pos.z, camYaw, camPitch);
    }

    /** First aerial bomb of this strike still in the air near the target (client entity). */
    private static AerialBomb ordnanceToFollow(Vec3 target) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        AerialBomb best = null;
        if (riderId >= 0 && level.getEntity(riderId) instanceof AerialBomb b && b.isAlive()) return b;
        for (var e : level.entitiesForRendering()) {
            if (e instanceof AerialBomb b && b.isAlive() && b.position().distanceToSqr(target) < 160 * 160) {
                if (best == null || b.getId() < best.getId()) best = b;
            }
        }
        riderId = best == null ? -1 : best.getId();
        return best;
    }

    private static int riderId = -1;

    /** 0..1 progress of the orbital lock-on (for the HUD). */
    public static float uplinkProgress(float partial) {
        if (plan == null) return 0;
        double fd = plan.getInt("FirstDrop");
        double uplink = Math.min(30, Math.max(12, Math.min(40, fd * 0.45)) * 0.7);
        return (float) Mth.clamp(flightT(partial) / uplink, 0, 1);
    }

    /** Height of the followed bomb above the target (for the ordnance-cam HUD). */
    public static double riderAltitude() {
        var level = Minecraft.getInstance().level;
        if (level == null || plan == null || riderId < 0) return -1;
        return level.getEntity(riderId) instanceof AerialBomb b ? b.getY() - plan.getDouble("TY") : -1;
    }

    public static boolean active() { return phase == Phase.CINEMATIC && cam != null; }
    public static float yaw() { return camYaw; }
    public static float pitch() { return camPitch; }
    public static float roll() { return camRoll; }

    public static float fovScale() {
        if (!active()) return 1f;
        float punch = firstImpactMs > 0 ? Math.max(0, 1 - (System.currentTimeMillis() - firstImpactMs) / 900f) : 0;
        float base = switch (shot) {
            case 4 -> 0.55f;
            case 5 -> 1.15f;
            case 0 -> 0.85f;
            case 1 -> 1.05f;
            case 3 -> 0.95f;
            default -> 0.9f;
        };
        return base * (1 - 0.12f * punch);
    }
}
