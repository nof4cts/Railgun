package com.ironlung.mission;

import com.ironlung.block.ControlType;
import com.ironlung.config.IronLungConfig;
import com.ironlung.network.ModNetwork;
import com.ironlung.network.Packets;
import com.ironlung.ocean.Poi;
import com.ironlung.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Paces the dread. Tension rises with progress, time, depth, damage and proximity to survey sites.
 * Events are rare, varied, and chosen by context: things in the camera only happen while you are
 * watching it, power trouble prefers moments when you need power, and big events never stack.
 */
public final class HorrorDirector {
    private HorrorDirector() {}

    public static void tick(Mission m, ServerPlayer p, ServerLevel lvl) {
        RandomSource r = lvl.getRandom();
        updateTension(m);

        if (m.silenceTicks > 0) m.silenceTicks--;
        if (m.scrapeTicks > 0) {
            if (m.scrapeTicks % 20 == 0) {
                int i = (80 - m.scrapeTicks) / 20; // walks from nose to tail along the port side
                BlockPos[] path = {new BlockPos(-1, 1, -1), new BlockPos(-1, 1, 1), new BlockPos(-1, 1, 3), new BlockPos(-1, 1, 5)};
                BlockPos at = m.base().offset(path[Math.min(path.length - 1, Math.max(0, i))]);
                lvl.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ModSounds.HULL_SCRAPE.get(), SoundSource.AMBIENT, 1.1f, 0.8f + r.nextFloat() * 0.15f);
            }
            m.scrapeTicks--;
        }

        // background machinery and hull settling: not "events", just the boat being a boat
        if (m.silenceTicks == 0 && m.ticks >= m.ambientCreakTick) {
            double depthF = Math.max(0, (m.depth - 120) / 100.0);
            playHull(m, lvl, r, r.nextBoolean() ? ModSounds.HULL_CREAK.get() : ModSounds.DRIP.get(), 0.35f + r.nextFloat() * 0.25f, 0.8f + r.nextFloat() * 0.3f);
            m.ambientCreakTick = m.ticks + (long) ((300 + r.nextInt(700)) / (1 + depthF * 1.5));
        }

        if (m.chainedEvent >= 0 && m.ticks >= m.chainedEventTick) {
            int t = m.chainedEvent;
            m.chainedEvent = -1;
            trigger(m, p, lvl, HorrorType.byId(t));
        }

        if (m.ticks < m.nextEventTick) return;
        HorrorType type = pick(m, r);
        if (type != null) trigger(m, p, lvl, type);
        double freq = IronLungConfig.EVENT_FREQUENCY.get();
        double interval = (800 + r.nextInt(1500)) * (1.0 - 0.45 * m.tension) / freq;
        m.nextEventTick = m.ticks + (long) Math.max(160, interval);
    }

    private static void updateTension(Mission m) {
        int total = Math.max(1, m.objectiveDone == null ? 1 : m.objectiveDone.length);
        double progress = m.objectivesComplete() / (double) total;
        double minutes = m.ticks / 1200.0;
        double depthF = clamp((m.depth - 150) / 60.0, 0, 1);
        double near = 0;
        for (int i = 0; i < m.map().objectives.size(); i++) {
            if (m.objectiveDone[i]) continue;
            Poi poi = m.map().objectives.get(i);
            double d = Math.hypot(poi.x - m.x, poi.z - m.z);
            near = Math.max(near, 1 - d / 70.0);
        }
        double t = 0.42 * progress + Math.min(0.25, minutes / 40.0) + 0.13 * depthF + 0.15 * clamp(near, 0, 1)
                + (m.hull < 0.5f ? 0.1 : 0);
        m.tension = (float) clamp(t, 0, 1);
    }

    private static HorrorType pick(Mission m, RandomSource r) {
        boolean cam = m.ticks < m.cameraActiveUntil;
        boolean powered = m.powered();
        double t = m.tension;
        double depthF = clamp((m.depth - 150) / 60.0, 0, 1);
        double freq = IronLungConfig.EVENT_FREQUENCY.get();
        boolean majorReady = m.ticks - m.lastMajorTick > Math.max(1800, 4200 * (1 - 0.4 * t) / freq);

        double[] w = new double[HorrorType.values().length];
        w[HorrorType.GROAN.ordinal()] = 3;
        w[HorrorType.CREAK.ordinal()] = 1.5;
        w[HorrorType.KNOCK.ordinal()] = 2.2;
        w[HorrorType.PRESSURE_SPIKE.ordinal()] = 0.5 + depthF * 2.0;
        w[HorrorType.FLICKER.ordinal()] = powered ? 1.4 : 0;
        w[HorrorType.CAMERA_GLITCH.ordinal()] = powered ? (cam ? 2.2 : 0.3) : 0;
        w[HorrorType.PROXIMITY_GHOST.ordinal()] = t > 0.2 ? 1.1 : 0;
        w[HorrorType.SILENCE.ordinal()] = t > 0.25 ? 1.2 : 0;
        w[HorrorType.WAIL.ordinal()] = 0.4 + t * 1.4;
        w[HorrorType.PHOTO_ANOMALY.ordinal()] = (!m.photoAnomalyArmed && t > 0.3) ? 1.0 : 0;
        if (majorReady) {
            w[HorrorType.IMPACT.ordinal()] = t > 0.45 ? 1.0 : 0;
            w[HorrorType.SCRAPE.ordinal()] = t > 0.35 ? 1.2 : 0;
            w[HorrorType.BLACKOUT.ordinal()] = (t > 0.3 && powered && m.ticks - m.lastPowerFailTick > 6000) ? 0.9 : 0;
            w[HorrorType.SILHOUETTE.ordinal()] = (cam && powered && t > 0.12) ? 2.4 : 0;
            w[HorrorType.EYES.ordinal()] = (cam && powered && t > 0.5) ? 0.9 : 0;
            w[HorrorType.HATCH_KNOCK.ordinal()] = t > 0.6 ? 0.5 : 0;
            w[HorrorType.LEVIATHAN.ordinal()] = (IronLungConfig.ENABLE_LEVIATHAN.get() && !m.leviathanDone && cam && powered && t > 0.62) ? 1.8 : 0;
        }
        double sum = 0;
        for (double v : w) sum += v;
        if (sum <= 0) return null;
        double roll = r.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll <= 0 && w[i] > 0) return HorrorType.byId(i);
        }
        return HorrorType.GROAN;
    }

    public static void chain(Mission m, HorrorType type, int delayTicks) {
        if (m.chainedEvent >= 0) return;
        m.chainedEvent = type.ordinal();
        m.chainedEventTick = m.ticks + delayTicks;
    }

    public static void trigger(Mission m, ServerPlayer p, ServerLevel lvl, HorrorType type) {
        RandomSource r = lvl.getRandom();
        m.eventsTotal++;
        if (type.major) m.lastMajorTick = m.ticks;
        boolean cam = m.ticks < m.cameraActiveUntil;
        float shake = 0, a = 0, b = 0;
        double dmgMul = IronLungConfig.HULL_DAMAGE.get();
        switch (type) {
            case CREAK -> playHull(m, lvl, r, ModSounds.HULL_CREAK.get(), 0.8f, 0.75f + r.nextFloat() * 0.3f);
            case GROAN -> {
                playHull(m, lvl, r, ModSounds.HULL_GROAN.get(), 1.0f, 0.65f + r.nextFloat() * 0.3f);
                shake = 0.12f;
            }
            case KNOCK -> {
                playHull(m, lvl, r, ModSounds.HULL_KNOCK.get(), 0.55f + r.nextFloat() * 0.35f, 0.65f + r.nextFloat() * 0.2f);
                shake = 0.18f;
                if (r.nextFloat() < 0.3f) chain(m, HorrorType.KNOCK, 25 + r.nextInt(30));
            }
            case IMPACT -> {
                playHull(m, lvl, r, ModSounds.HULL_IMPACT.get(), 1.6f, 0.8f + r.nextFloat() * 0.2f);
                m.hull -= (float) ((0.04 + r.nextFloat() * 0.05) * dmgMul);
                m.cameraCondition = Math.max(0.2f, m.cameraCondition - 0.05f);
                shake = 1.0f;
                if (r.nextFloat() < 0.35f) chain(m, HorrorType.FLICKER, 6);
            }
            case SCRAPE -> {
                m.scrapeTicks = 80;
                shake = 0.3f;
                a = 4.0f;
            }
            case PRESSURE_SPIKE -> {
                BlockPos at = m.base().offset(2, 2, 1 + r.nextInt(3));
                lvl.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ModSounds.PRESSURE_HISS.get(), SoundSource.AMBIENT, 1.0f, 0.8f + r.nextFloat() * 0.3f);
                playHull(m, lvl, r, ModSounds.HULL_GROAN.get(), 0.7f, 0.6f);
                m.hull -= (float) (0.01 * dmgMul);
                shake = 0.25f;
                a = 6 + r.nextFloat() * 10; // apparent extra metres on the gauge
            }
            case FLICKER -> {
                if (!m.powered()) return;
                m.flickerTicks = 30 + r.nextInt(40);
                playAtControl(m, lvl, ControlType.BREAKER, ModSounds.ELECTRIC_BUZZ.get(), 0.9f, 0.9f + r.nextFloat() * 0.2f);
                a = m.flickerTicks / 20f;
            }
            case BLACKOUT -> {
                if (!m.powered()) return;
                tripPower(m, lvl);
                a = 1;
            }
            case CAMERA_GLITCH -> {
                if (!m.powered()) return;
                m.glitchTicks = 60 + r.nextInt(80);
                if (cam) playAtControl(m, lvl, ControlType.CAMERA, ModSounds.STATIC_BURST.get(), 0.8f, 1.0f);
                a = m.glitchTicks / 20f;
            }
            case SILHOUETTE -> {
                if (cam) m.sightings++;
                a = 3.5f + r.nextFloat() * 2.5f; // duration
                b = r.nextBoolean() ? 1 : -1; // crossing direction
                if (r.nextFloat() < 0.4f) chain(m, HorrorType.KNOCK, 80 + r.nextInt(80));
            }
            case PROXIMITY_GHOST -> {
                m.ghostTicks = 60 + r.nextInt(60);
                m.ghostSide = r.nextInt(4);
            }
            case SILENCE -> {
                m.silenceTicks = 160 + r.nextInt(140);
                a = m.silenceTicks / 20f;
                if (r.nextFloat() < 0.55f) {
                    chain(m, m.tension > 0.6 && r.nextBoolean() ? HorrorType.HATCH_KNOCK : HorrorType.KNOCK, m.silenceTicks - 10);
                }
            }
            case WAIL -> playHull(m, lvl, r, ModSounds.DISTANT_WAIL.get(), 0.5f + r.nextFloat() * 0.3f, 0.55f + r.nextFloat() * 0.3f);
            case LEVIATHAN -> {
                m.leviathanDone = true;
                m.sightings++;
                BlockPos at = m.base().offset(1, 1, -2);
                lvl.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ModSounds.LEVIATHAN.get(), SoundSource.AMBIENT, 2.5f, 0.9f);
                m.flickerTicks = 70;
                shake = 0.7f;
                a = 6.5f;
                chain(m, HorrorType.GROAN, 140);
            }
            case EYES -> {
                m.sightings++;
                a = 2.5f;
                chain(m, HorrorType.CAMERA_GLITCH, 50);
            }
            case HATCH_KNOCK -> playAtControl(m, lvl, ControlType.HATCH, ModSounds.HATCH_KNOCK.get(), 1.0f, 0.9f + r.nextFloat() * 0.1f);
            case PHOTO_ANOMALY -> m.photoAnomalyArmed = true;
            case COLLISION -> {}
        }
        ModNetwork.toPlayer(p, new Packets.HorrorEvent(type.ordinal(), shake, r.nextLong(), a, b));
    }

    public static void tripPower(Mission m, ServerLevel lvl) {
        m.power = Mission.Power.TRIPPED;
        m.powerFailures++;
        m.lastPowerFailTick = m.ticks;
        m.flickerTicks = 0;
        m.throttleIn = m.turnIn = m.ballastIn = 0;
        playAtControl(m, lvl, ControlType.BREAKER, ModSounds.POWER_DOWN.get(), 1.2f, 1.0f);
    }

    static void playHull(Mission m, ServerLevel lvl, RandomSource r, SoundEvent s, float vol, float pitch) {
        BlockPos rel = SubInterior.HULL_SOUND_POINTS.get(r.nextInt(SubInterior.HULL_SOUND_POINTS.size()));
        BlockPos at = m.base().offset(rel);
        lvl.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, s, SoundSource.AMBIENT, vol, pitch);
    }

    static void playAtControl(Mission m, ServerLevel lvl, ControlType t, SoundEvent s, float vol, float pitch) {
        BlockPos at = m.base().offset(SubInterior.CONTROL_POS.get(t));
        lvl.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, s, SoundSource.BLOCKS, vol, pitch);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
