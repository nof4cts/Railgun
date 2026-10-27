package com.ironlung.mission;

import com.ironlung.IronLungMod;
import com.ironlung.block.ControlType;
import com.ironlung.config.IronLungConfig;
import com.ironlung.network.ModNetwork;
import com.ironlung.network.Packets;
import com.ironlung.ocean.OceanMap;
import com.ironlung.ocean.Poi;
import com.ironlung.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MissionManager {
    private MissionManager() {}

    public static final ResourceKey<DamageType> CRUSHED = ResourceKey.create(Registries.DAMAGE_TYPE, IronLungMod.id("crushed"));
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.35f, 0.0f, 0.0f), 0.9f);
    private static final Map<UUID, Packets.MissionEnd> PENDING_SUMMARIES = new HashMap<>();

    private static MissionSavedData data(MinecraftServer server) {
        return MissionSavedData.get(server);
    }

    public static Mission get(ServerPlayer p) {
        return data(p.server).missions.get(p.getUUID());
    }

    public static boolean hasMission(ServerPlayer p) {
        Mission m = get(p);
        return m != null && m.phase != Mission.Phase.DONE;
    }

    // ------------------------------------------------------------------ lifecycle

    public static boolean startMission(ServerPlayer p, long seed) {
        MinecraftServer server = p.server;
        ServerLevel sub = server.getLevel(IronLungMod.DIMENSION);
        if (sub == null) {
            p.sendSystemMessage(Component.literal("Iron Lung dimension is missing (datapack not loaded?).").withStyle(ChatFormatting.RED));
            return false;
        }
        MissionSavedData d = data(server);
        if (d.missions.containsKey(p.getUUID())) return false;
        int slot = d.nextSlot++;
        Mission m = new Mission(p.getUUID(), slot, seed, IronLungConfig.MAP_SIZE.get(),
                IronLungConfig.OBJECTIVES.get(), IronLungConfig.HIDDEN_ANOMALIES.get());
        OceanMap map = m.map();
        m.x = map.startX;
        m.z = map.startZ;
        m.heading = 0;
        m.depth = Math.max(OceanMap.MIN_SUB_DEPTH + 5, map.floorDepth(m.x, m.z) - 22);
        m.sealant = IronLungConfig.SEALANT_CHARGES.get();
        m.nextEventTick = 700 + p.getRandom().nextInt(700);
        m.returnDim = p.level().dimension().location().toString();
        m.returnX = p.getX();
        m.returnY = p.getY();
        m.returnZ = p.getZ();
        m.returnYaw = p.getYRot();
        m.returnPitch = p.getXRot();

        BlockPos base = m.base();
        SubInterior.build(sub, base);
        d.missions.put(p.getUUID(), m);
        d.setDirty();

        p.teleportTo(sub, base.getX() + SubInterior.SPAWN_X, base.getY() + SubInterior.SPAWN_Y, base.getZ() + SubInterior.SPAWN_Z, 180f, 0f);
        sendInit(p, m);
        sub.playSound(null, base.getX() + 1.5, base.getY() + 1.5, base.getZ() + 5.5, ModSounds.BREAKER_CLUNK.get(), SoundSource.BLOCKS, 1.5f, 0.6f);
        sub.playSound(null, base.getX() + 1.5, base.getY() + 1.5, base.getZ() + 2.5, ModSounds.HULL_GROAN.get(), SoundSource.AMBIENT, 1.0f, 0.7f);
        IronLungMod.LOGGER.info("{} began a dive (seed {}, slot {})", p.getGameProfile().getName(), seed, slot);
        return true;
    }

    public static void sendInit(ServerPlayer p, Mission m) {
        m.map();
        ModNetwork.toPlayer(p, new Packets.MissionInit(true, m.seed, m.mapSize, m.objectiveCount, m.hiddenCount, m.slot,
                m.objectiveDone.clone(), new ArrayList<>(m.photos), m.ticks));
    }

    public static void endMission(ServerPlayer p, Mission m, int outcome, boolean teleportBack) {
        MinecraftServer server = p.server;
        MissionSavedData d = data(server);
        m.phase = Mission.Phase.DONE;
        d.missions.remove(m.owner);
        d.setDirty();
        Packets.MissionEnd summary = new Packets.MissionEnd(outcome, m.ticks, m.objectivesComplete(), m.objectiveDone.length,
                m.photos.size(), m.anomaliesFound.size(), Math.max(0, m.hull), m.collisions, m.powerFailures, m.sightings);
        ServerLevel sub = server.getLevel(IronLungMod.DIMENSION);
        if (teleportBack) {
            ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(m.returnDim)));
            if (target == null) target = server.overworld();
            p.teleportTo(target, m.returnX, m.returnY, m.returnZ, m.returnYaw, m.returnPitch);
        }
        if (sub != null) SubInterior.clear(sub, m.base());
        ModNetwork.toPlayer(p, Packets.MissionInit.inactive());
        if (p.isAlive()) {
            ModNetwork.toPlayer(p, summary);
        } else {
            PENDING_SUMMARIES.put(p.getUUID(), summary);
        }
    }

    public static void onRespawn(ServerPlayer p) {
        Packets.MissionEnd s = PENDING_SUMMARIES.remove(p.getUUID());
        if (s != null) {
            ModNetwork.toPlayer(p, Packets.MissionInit.inactive());
            ModNetwork.toPlayer(p, s);
        }
    }

    public static void onDeath(ServerPlayer p) {
        Mission m = get(p);
        if (m != null && m.phase != Mission.Phase.DONE) endMission(p, m, Packets.MissionEnd.LOST, false);
    }

    public static void onLogin(ServerPlayer p) {
        Mission m = get(p);
        if (m == null) return;
        if (!p.level().dimension().equals(IronLungMod.DIMENSION)) {
            // logged in somewhere else: the dive is forfeit
            endMission(p, m, Packets.MissionEnd.ABORTED, false);
            return;
        }
        m.appliedLamps = null;
        m.appliedConsoles = null;
        m.throttleIn = m.turnIn = m.ballastIn = 0;
        sendInit(p, m);
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(MinecraftServer server) {
        MissionSavedData d = data(server);
        if (d.missions.isEmpty()) return;
        ServerLevel sub = server.getLevel(IronLungMod.DIMENSION);
        if (sub == null) return;
        for (Mission m : new ArrayList<>(d.missions.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(m.owner);
            if (p == null || !p.isAlive()) continue; // paused while the pilot is away
            if (!p.level().dimension().equals(IronLungMod.DIMENSION)) {
                endMission(p, m, Packets.MissionEnd.ABORTED, false);
                continue;
            }
            try {
                tickMission(p, m, sub);
            } catch (Exception e) {
                IronLungMod.LOGGER.error("Dive tick failed; ending dive safely", e);
                endMission(p, m, Packets.MissionEnd.ABORTED, true);
                continue;
            }
            if (m.ticks % 200 == 0) d.setDirty();
        }
    }

    private static void tickMission(ServerPlayer p, Mission m, ServerLevel lvl) {
        if (m.phase == Mission.Phase.DONE) return;
        m.ticks++;
        m.phaseTicks++;
        RandomSource r = lvl.getRandom();
        switch (m.phase) {
            case DIVING -> {
                physics(p, m, lvl, r);
                systems(p, m, lvl, r);
                if (m.phase == Mission.Phase.DIVING) HorrorDirector.tick(m, p, lvl);
            }
            case ASCENDING -> ascend(p, m, lvl, r);
            case IMPLODING -> implode(p, m, lvl, r);
            default -> {}
        }
        if (m.phase == Mission.Phase.DONE) return;
        applyPowerVisuals(m, lvl, r);
        if (m.ticks % 4 == 0) updateProximity(m);
        if (m.ticks % 2 == 0) sendState(p, m);
        keepPilotInside(p, m);
    }

    private static void keepPilotInside(ServerPlayer p, Mission m) {
        BlockPos b = m.base();
        double rx = p.getX() - b.getX(), ry = p.getY() - b.getY(), rz = p.getZ() - b.getZ();
        if (rx < -0.5 || rx > 3.5 || ry < -1.5 || ry > 3.5 || rz < -0.5 || rz > 5.5) {
            p.teleportTo(b.getX() + SubInterior.SPAWN_X, b.getY() + SubInterior.SPAWN_Y, b.getZ() + SubInterior.SPAWN_Z);
        }
    }

    private static void physics(ServerPlayer p, Mission m, ServerLevel lvl, RandomSource r) {
        OceanMap map = m.map();
        boolean pw = m.powered();
        double thr = pw ? m.throttleIn : 0, turn = pw ? m.turnIn : 0, bal = pw ? m.ballastIn : 0;
        double maxSpd = IronLungConfig.MAX_SPEED.get();
        double target = thr >= 0 ? thr * maxSpd : thr * maxSpd * 0.5;
        m.speed += (target - m.speed) * 0.015;
        double targetTurn = turn * Math.toRadians(10) / 20.0;
        m.turnRate += (targetTurn - m.turnRate) * 0.06;
        m.heading = wrap(m.heading + m.turnRate);
        double targetV = bal * 0.9;
        m.vertSpeed += (targetV - m.vertSpeed) * 0.03;

        double nd = clamp(m.depth + m.vertSpeed / 20.0, OceanMap.MIN_SUB_DEPTH, OceanMap.MAX_SUB_DEPTH);
        double fx = Math.sin(m.heading), fz = -Math.cos(m.heading);
        double nx = m.x + fx * m.speed / 20.0, nz = m.z + fz * m.speed / 20.0;

        double severity = 0;
        BlockPos hitAt = null;
        // keel against the floor directly below
        double floorHere = map.floorDepth(nx, nz);
        if (floorHere < nd + 1.8) {
            severity += Math.abs(m.vertSpeed) * 1.2 + 0.05;
            nd = floorHere - 1.8;
            m.vertSpeed = Math.min(0, m.vertSpeed) * -0.2;
            hitAt = new BlockPos(1, -1, 2);
        }
        // nose / tail against rock
        double dir = m.speed >= 0 ? 1 : -1;
        double px = nx + fx * 3.6 * dir, pz = nz + fz * 3.6 * dir;
        if (map.floorDepth(px, pz) < nd + 1.6) {
            severity += Math.abs(m.speed);
            nx = m.x;
            nz = m.z;
            m.speed = -m.speed * 0.25;
            hitAt = dir > 0 ? new BlockPos(1, 1, -1) : new BlockPos(1, 1, 5);
        }
        m.x = clamp(nx, 0, map.size);
        m.z = clamp(nz, 0, map.size);
        m.depth = Math.max(OceanMap.MIN_SUB_DEPTH, nd);

        if (m.collisionCooldown > 0) m.collisionCooldown--;
        if (severity > 0.12 && m.collisionCooldown == 0 && hitAt != null) {
            m.collisionCooldown = 30;
            m.collisions++;
            float dmg = (float) (Math.min(1.5, severity) * 0.045 * IronLungConfig.HULL_DAMAGE.get());
            m.hull -= dmg;
            m.cameraCondition = Math.max(0.2f, m.cameraCondition - 0.03f);
            BlockPos at = m.base().offset(hitAt);
            lvl.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ModSounds.HULL_IMPACT.get(), SoundSource.AMBIENT,
                    (float) Math.min(1.8, 0.6 + severity), 0.9f + r.nextFloat() * 0.2f);
            ModNetwork.toPlayer(p, new Packets.HorrorEvent(HorrorType.COLLISION.ordinal(), (float) Math.min(1.2, severity * 0.8), r.nextLong(), 0, 0));
            if (severity > 1.0 && m.powered() && r.nextFloat() < 0.3f) HorrorDirector.tripPower(m, lvl);
            else if (severity > 0.5 && m.powered() && r.nextFloat() < 0.4f) m.flickerTicks = 20 + r.nextInt(20);
        }
    }

    private static void systems(ServerPlayer p, Mission m, ServerLevel lvl, RandomSource r) {
        BlockPos base = m.base();
        double dmgMul = IronLungConfig.HULL_DAMAGE.get();
        // pressure
        if (m.depth > OceanMap.CRUSH_DEPTH) {
            m.hull -= (float) ((m.depth - OceanMap.CRUSH_DEPTH) * 0.00004 * dmgMul);
            if (m.ticks % 50 == 0) {
                HorrorDirector.playHull(m, lvl, r, ModSounds.HULL_GROAN.get(), 1.1f, 0.6f + r.nextFloat() * 0.2f);
                HorrorDirector.playAtControl(m, lvl, ControlType.DEPTH_GAUGE, ModSounds.ALARM.get(), 0.6f, 1.0f);
            }
        } else if (m.depth > OceanMap.WARN_DEPTH && m.ticks % 220 == 0) {
            HorrorDirector.playHull(m, lvl, r, ModSounds.HULL_CREAK.get(), 0.9f, 0.6f);
        }
        // power
        if (m.power == Mission.Power.RESTORING) {
            if (--m.restoreTicks <= 0) {
                m.power = Mission.Power.ONLINE;
                m.flickerTicks = 25;
                HorrorDirector.playAtControl(m, lvl, ControlType.BREAKER, ModSounds.POWER_UP.get(), 1.0f, 1.0f);
            }
        }
        if (m.flickerTicks > 0) m.flickerTicks--;
        if (m.photoCooldown > 0) m.photoCooldown--;
        if (m.glitchTicks > 0) m.glitchTicks--;
        if (m.ghostTicks > 0) m.ghostTicks--;
        if (m.cameraCondition < 0.6f && m.powered() && m.glitchTicks == 0 && r.nextFloat() < (0.6f - m.cameraCondition) * 0.004f) {
            m.glitchTicks = 20 + r.nextInt(40);
        }
        // welding a breach
        if (m.weldTicks > 0) {
            BlockPos st = base.offset(SubInterior.CONTROL_POS.get(ControlType.SEALANT));
            if (p.distanceToSqr(st.getX() + 0.5, st.getY() + 0.5, st.getZ() + 0.5) > 3.2 * 3.2) {
                m.weldTicks = 0;
                p.displayClientMessage(Component.translatable("ironlung.msg.weld_abandoned").withStyle(ChatFormatting.GRAY), true);
            } else {
                m.weldTicks--;
                if (m.weldTicks % 8 == 0) {
                    lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, st.getX() + 1.1, st.getY() + 0.6, st.getZ() + 0.5, 6, 0.05, 0.2, 0.2, 0.05);
                }
                if (m.weldTicks % 20 == 0) {
                    lvl.playSound(null, st.getX() + 0.5, st.getY() + 0.5, st.getZ() + 0.5, ModSounds.WELD.get(), SoundSource.BLOCKS, 0.9f, 0.9f + r.nextFloat() * 0.2f);
                }
                if (m.weldTicks == 0) {
                    m.sealant--;
                    m.hull = Math.min(1.0f, m.hull + 0.22f);
                    p.displayClientMessage(Component.translatable("ironlung.msg.weld_done", m.sealant).withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
        // leaks
        if (m.hull < 0.6f && r.nextFloat() < (0.6f - m.hull) * 0.12f) {
            double lx = base.getX() + 0.2 + r.nextDouble() * 2.6, lz = base.getZ() + 0.2 + r.nextDouble() * 4.6;
            lvl.sendParticles(BLOOD, lx, base.getY() + 2.9, lz, 1, 0, 0, 0, 0);
            lvl.sendParticles(ParticleTypes.DRIPPING_DRIPSTONE_LAVA, lx, base.getY() + 2.95, lz, 1, 0, 0, 0, 0);
            if (r.nextFloat() < 0.15f) {
                lvl.playSound(null, lx, base.getY() + 2.5, lz, ModSounds.DRIP.get(), SoundSource.AMBIENT, 0.5f, 0.8f + r.nextFloat() * 0.4f);
            }
        }
        if (m.hull < 0.3f && m.ticks % 120 == 0) {
            HorrorDirector.playAtControl(m, lvl, ControlType.PRESSURE_GAUGE, ModSounds.ALARM.get(), 0.9f, 0.8f);
        }
        if (m.hull <= 0) {
            m.hull = 0;
            m.phase = Mission.Phase.IMPLODING;
            m.phaseTicks = 0;
        }
    }

    private static void applyPowerVisuals(Mission m, ServerLevel lvl, RandomSource r) {
        boolean lamps, consoles;
        if (m.power == Mission.Power.ONLINE) {
            consoles = true;
            lamps = m.flickerTicks <= 0 || r.nextFloat() < 0.55f;
        } else if (m.power == Mission.Power.RESTORING) {
            consoles = r.nextFloat() < 0.3f;
            lamps = false;
        } else {
            lamps = false;
            consoles = false;
        }
        if (m.appliedLamps == null || m.appliedLamps != lamps || m.appliedConsoles == null || m.appliedConsoles != consoles) {
            SubInterior.applyPower(lvl, m.base(), lamps, consoles);
            m.appliedLamps = lamps;
            m.appliedConsoles = consoles;
        }
    }

    private static void updateProximity(Mission m) {
        OceanMap map = m.map();
        double[] angles = {0, Math.PI / 2, Math.PI, -Math.PI / 2}; // fore, starboard, aft, port
        for (int i = 0; i < 4; i++) {
            double a = m.heading + angles[i];
            double fx = Math.sin(a), fz = -Math.cos(a);
            float clear = 99;
            for (int d = 2; d <= 16; d++) {
                if (map.floorDepth(m.x + fx * d, m.z + fz * d) < m.depth + 1.6) {
                    clear = d;
                    break;
                }
            }
            m.prox[i] = clear;
        }
        if (m.ghostTicks > 0) m.prox[m.ghostSide] = Math.min(m.prox[m.ghostSide], 1.5f + (m.ticks % 7) * 0.1f);
    }

    private static void sendState(ServerPlayer p, Mission m) {
        int mask = 0;
        for (int i = 0; i < m.objectiveDone.length; i++) if (m.objectiveDone[i]) mask |= 1 << i;
        float restore = m.power == Mission.Power.RESTORING ? 1f - m.restoreTicks / 70f : 0f;
        ModNetwork.toPlayer(p, new Packets.SubState(m.x, m.z, m.heading, m.depth, (float) m.speed, m.hull, m.power.ordinal(), restore,
                m.cameraCondition, m.prox.clone(), mask, m.phase.ordinal(), m.sealant, m.weldTicks > 0, m.glitchTicks, m.photoCooldown));
    }

    // ------------------------------------------------------------------ endings

    private static void ascend(ServerPlayer p, Mission m, ServerLevel lvl, RandomSource r) {
        int t = m.phaseTicks;
        m.depth = Math.max(OceanMap.MIN_SUB_DEPTH, m.depth - 0.06);
        m.speed *= 0.95;
        switch (t) {
            case 1 -> {
                HorrorDirector.playAtControl(m, lvl, ControlType.BALLAST, ModSounds.PRESSURE_HISS.get(), 1.4f, 0.7f);
                p.displayClientMessage(Component.translatable("ironlung.msg.ascending").withStyle(ChatFormatting.DARK_RED), true);
            }
            case 90 -> HorrorDirector.trigger(m, p, lvl, HorrorType.GROAN);
            case 170 -> m.flickerTicks = 50;
            case 240 -> {
                HorrorDirector.tripPower(m, lvl);
                HorrorDirector.trigger(m, p, lvl, HorrorType.SILENCE);
                m.chainedEvent = -1;
            }
            case 330 -> {
                BlockPos at = m.base().offset(1, 1, -2);
                lvl.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ModSounds.LEVIATHAN.get(), SoundSource.AMBIENT, 3.0f, 0.75f);
                ModNetwork.toPlayer(p, new Packets.HorrorEvent(HorrorType.LEVIATHAN.ordinal(), 1.0f, r.nextLong(), 7f, 1f));
            }
            case 400 -> {
                HorrorDirector.playHull(m, lvl, r, ModSounds.HULL_IMPACT.get(), 2.0f, 0.6f);
                ModNetwork.toPlayer(p, new Packets.HorrorEvent(HorrorType.IMPACT.ordinal(), 1.3f, r.nextLong(), 0, 0));
            }
            case 430 -> HorrorDirector.trigger(m, p, lvl, HorrorType.SILENCE);
            case 520 -> HorrorDirector.playAtControl(m, lvl, ControlType.HATCH, ModSounds.HATCH_KNOCK.get(), 1.2f, 0.8f);
            case 600 -> {
                m.power = Mission.Power.RESTORING;
                m.restoreTicks = 30;
            }
            default -> {}
        }
        if (m.power == Mission.Power.RESTORING && --m.restoreTicks <= 0) {
            m.power = Mission.Power.ONLINE;
            m.flickerTicks = 40;
        }
        if (t >= 680) endMission(p, m, Packets.MissionEnd.RECOVERED, true);
    }

    private static void implode(ServerPlayer p, Mission m, ServerLevel lvl, RandomSource r) {
        int t = m.phaseTicks;
        if (t == 1) {
            if (m.powered()) HorrorDirector.tripPower(m, lvl);
            HorrorDirector.playAtControl(m, lvl, ControlType.PRESSURE_GAUGE, ModSounds.ALARM.get(), 1.5f, 0.7f);
            HorrorDirector.playHull(m, lvl, r, ModSounds.HULL_GROAN.get(), 2.0f, 0.5f);
            ModNetwork.toPlayer(p, new Packets.HorrorEvent(HorrorType.IMPACT.ordinal(), 0.6f, r.nextLong(), 0, 0));
        } else if (t == 35) {
            BlockPos b = m.base();
            lvl.playSound(null, b.getX() + 1.5, b.getY() + 1.5, b.getZ() + 2.5, ModSounds.IMPLOSION.get(), SoundSource.AMBIENT, 3.0f, 1.0f);
            ModNetwork.toPlayer(p, new Packets.HorrorEvent(HorrorType.IMPACT.ordinal(), 2.0f, r.nextLong(), 1, 0));
        } else if (t == 50) {
            boolean lethal = IronLungConfig.LETHAL_IMPLOSION.get() && !p.isCreative() && !p.isSpectator();
            if (lethal) {
                DamageSource src = new DamageSource(lvl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CRUSHED));
                p.hurt(src, Float.MAX_VALUE);
            }
            if (p.isAlive()) endMission(p, m, Packets.MissionEnd.LOST, true);
        }
    }

    // ------------------------------------------------------------------ input

    public static void onHelmInput(ServerPlayer p, float throttle, float turn, float ballast) {
        Mission m = get(p);
        if (m == null || m.phase != Mission.Phase.DIVING) return;
        m.throttleIn = (float) clamp(throttle, -1, 1);
        m.turnIn = (float) clamp(turn, -1, 1);
        m.ballastIn = (float) clamp(ballast, -1, 1);
    }

    public static void onCamera(ServerPlayer p, int mount, float pan, float tilt, boolean capture) {
        Mission m = get(p);
        if (m == null) return;
        m.camMount = Math.floorMod(mount, CameraMount.values().length);
        m.camPan = (float) clamp(pan, -CameraMount.MAX_PAN, CameraMount.MAX_PAN);
        m.camTilt = (float) clamp(tilt, -CameraMount.MAX_TILT, CameraMount.MAX_TILT);
        m.cameraActiveUntil = m.ticks + 50;
        if (capture) capture(p, m);
    }

    private static void capture(ServerPlayer p, Mission m) {
        if (m.phase != Mission.Phase.DIVING) return;
        ServerLevel lvl = (ServerLevel) p.level();
        RandomSource r = lvl.getRandom();
        if (!m.powered()) {
            p.displayClientMessage(Component.translatable("ironlung.msg.no_power").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        if (m.photoCooldown > 0) {
            p.displayClientMessage(Component.translatable("ironlung.msg.recharging").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        m.photoCooldown = 70;
        OceanMap map = m.map();
        CameraMount mount = CameraMount.byId(m.camMount);
        double[] pose = mount.pose(m.x, m.z, m.heading, m.depth, m.camPan, m.camTilt);
        int flags = 0;
        boolean ruined = m.glitchTicks > 0 || r.nextFloat() < (1 - m.cameraCondition) * 0.25f;
        if (ruined) flags |= PhotoRecord.FLAG_RUINED;
        if (m.photoAnomalyArmed && !ruined) {
            flags |= PhotoRecord.FLAG_ANOMALY;
            m.photoAnomalyArmed = false;
            m.sightings++;
        }
        List<Integer> hits = new ArrayList<>();
        if (!ruined) {
            double cp = Math.cos(pose[4]);
            double dx = Math.sin(pose[3]) * cp, dy = Math.sin(pose[4]), dz = -Math.cos(pose[3]) * cp;
            double cosLimit = Math.cos(Math.toRadians(26 + 7));
            for (Poi poi : map.pois) {
                double vx = poi.x - pose[0], vy = poi.centerY() - pose[1], vz = poi.z - pose[2];
                double dist = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (dist > 40 + poi.radius * 0.4) continue;
                double cos = (vx * dx + vy * dy + vz * dz) / Math.max(1e-6, dist);
                // large objects count even when only partly in frame
                double slack = Math.atan2(poi.radius * 0.5, Math.max(1, dist));
                if (Math.acos(Math.max(-1, Math.min(1, cos))) > Math.acos(cosLimit) + slack) continue;
                if (!map.lineOfSight(pose[0], pose[1], pose[2], poi.x, poi.centerY(), poi.z)) continue;
                hits.add(poi.index);
            }
        }
        List<Integer> newly = new ArrayList<>();
        boolean anomaly = false;
        for (int idx : hits) {
            Poi poi = map.pois.get(idx);
            int oi = map.objectives.indexOf(poi);
            if (oi >= 0) {
                if (!m.objectiveDone[oi]) {
                    m.objectiveDone[oi] = true;
                    newly.add(oi);
                }
            } else if (!m.anomaliesFound.contains(idx)) {
                m.anomaliesFound.add(idx);
                anomaly = true;
            }
        }
        int[] hitArr = hits.stream().mapToInt(Integer::intValue).toArray();
        PhotoRecord rec = new PhotoRecord(m.photos.size() + 1, pose[0], pose[1], pose[2], pose[3], pose[4], m.ticks, flags, r.nextLong(), hitArr);
        m.photos.add(rec);
        HorrorDirector.playAtControl(m, lvl, ControlType.CAMERA, ModSounds.CAMERA_SHUTTER.get(), 1.0f, 1.0f);
        ModNetwork.toPlayer(p, new Packets.PhotoResult(rec, newly.stream().mapToInt(Integer::intValue).toArray(), anomaly));
        data(p.server).setDirty();

        // the worst possible moment
        double nearTarget = 0;
        for (Poi o : map.objectives) nearTarget = Math.max(nearTarget, 1 - Math.hypot(o.x - m.x, o.z - m.z) / 60.0);
        double failChance = 0.03 + m.tension * 0.10 + Math.max(0, nearTarget) * 0.06;
        if (r.nextDouble() < failChance) {
            HorrorDirector.chain(m, r.nextFloat() < 0.35f && m.ticks - m.lastPowerFailTick > 3000 ? HorrorType.BLACKOUT : HorrorType.FLICKER, 12 + r.nextInt(25));
        }
        if (!newly.isEmpty() && m.allObjectivesDone()) {
            p.displayClientMessage(Component.translatable("ironlung.msg.all_logged").withStyle(ChatFormatting.DARK_RED), true);
            m.nextEventTick = Math.min(m.nextEventTick, m.ticks + 300);
        }
    }

    public static void onSurface(ServerPlayer p, boolean abort) {
        Mission m = get(p);
        if (m == null || m.phase != Mission.Phase.DIVING) return;
        if (abort) {
            endMission(p, m, Packets.MissionEnd.ABORTED, true);
            return;
        }
        if (!m.allObjectivesDone()) {
            p.displayClientMessage(Component.translatable("ironlung.msg.incomplete").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        m.phase = Mission.Phase.ASCENDING;
        m.phaseTicks = 0;
        m.throttleIn = m.turnIn = m.ballastIn = 0;
    }

    public static void onControlUsed(ServerPlayer p, ControlType type, BlockPos pos) {
        Mission m = get(p);
        if (m == null) return;
        ServerLevel lvl = (ServerLevel) p.level();
        RandomSource r = lvl.getRandom();
        switch (type) {
            case BREAKER -> {
                lvl.playSound(null, pos, ModSounds.BREAKER_CLUNK.get(), SoundSource.BLOCKS, 1.0f, 0.9f + r.nextFloat() * 0.2f);
                if (m.phase != Mission.Phase.DIVING) {
                    say(p, "ironlung.msg.breaker_stuck", ChatFormatting.DARK_GRAY);
                } else if (m.power == Mission.Power.TRIPPED) {
                    m.power = Mission.Power.RESTORING;
                    m.restoreTicks = 70;
                    say(p, "ironlung.msg.breaker_reset", ChatFormatting.GRAY);
                } else if (m.power == Mission.Power.RESTORING) {
                    say(p, "ironlung.msg.breaker_busy", ChatFormatting.GRAY);
                } else {
                    say(p, "ironlung.msg.breaker_ok", ChatFormatting.GRAY);
                }
            }
            case PRESSURE_GAUGE -> {
                double atm = 1 + m.depth / 10.0;
                ChatFormatting c = m.depth > OceanMap.CRUSH_DEPTH ? ChatFormatting.RED : m.depth > OceanMap.WARN_DEPTH ? ChatFormatting.GOLD : ChatFormatting.GRAY;
                if (!m.powered()) {
                    say(p, "ironlung.msg.gauge_dead", ChatFormatting.DARK_GRAY);
                } else {
                    p.displayClientMessage(Component.translatable("ironlung.msg.pressure", String.format("%.1f", atm), Math.round(m.hull * 100)).withStyle(c), true);
                }
            }
            case DEPTH_GAUGE -> {
                if (!m.powered()) {
                    say(p, "ironlung.msg.gauge_dead", ChatFormatting.DARK_GRAY);
                } else {
                    double floor = m.map().floorDepth(m.x, m.z);
                    p.displayClientMessage(Component.translatable("ironlung.msg.depth", String.format("%.1f", m.depth),
                            String.format("%.1f", Math.max(0, floor - m.depth - 1.8)), (int) OceanMap.CRUSH_DEPTH).withStyle(ChatFormatting.GRAY), true);
                }
            }
            case PROXIMITY -> {
                if (!m.powered()) {
                    say(p, "ironlung.msg.gauge_dead", ChatFormatting.DARK_GRAY);
                } else {
                    p.displayClientMessage(Component.translatable("ironlung.msg.proximity", fmt(m.prox[0]), fmt(m.prox[1]), fmt(m.prox[2]), fmt(m.prox[3])).withStyle(ChatFormatting.GRAY), true);
                }
            }
            case SEALANT -> {
                if (m.weldTicks > 0) say(p, "ironlung.msg.weld_busy", ChatFormatting.GRAY);
                else if (m.sealant <= 0) say(p, "ironlung.msg.weld_empty", ChatFormatting.DARK_RED);
                else if (m.hull >= 0.97f) say(p, "ironlung.msg.weld_none", ChatFormatting.GRAY);
                else {
                    m.weldTicks = 100;
                    say(p, "ironlung.msg.weld_start", ChatFormatting.GRAY);
                }
            }
            case HATCH -> {
                lvl.playSound(null, pos, ModSounds.HELM_CLICK.get(), SoundSource.BLOCKS, 0.8f, 0.5f);
                say(p, "ironlung.msg.hatch" + r.nextInt(4), ChatFormatting.DARK_GRAY);
            }
            default -> {}
        }
    }

    private static String fmt(float v) {
        return v >= 99 ? "--" : String.format("%.0fm", v);
    }

    private static void say(ServerPlayer p, String key, ChatFormatting c) {
        p.displayClientMessage(Component.translatable(key).withStyle(c), true);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double wrap(double a) {
        double twoPi = Math.PI * 2;
        a %= twoPi;
        if (a < 0) a += twoPi;
        return a;
    }

    public static boolean isSubLevel(Level level) {
        return level.dimension().equals(IronLungMod.DIMENSION);
    }
}
