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
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every dive. A dive has a crew of 1..maxCrew players sharing one submarine, one ocean,
 * one camera and one hull. Each crew member keeps their own return point.
 */
public final class MissionManager {
    private MissionManager() {}

    public static final ResourceKey<DamageType> CRUSHED = ResourceKey.create(Registries.DAMAGE_TYPE, IronLungMod.id("crushed"));
    public static final ResourceKey<DamageType> TAKEN = ResourceKey.create(Registries.DAMAGE_TYPE, IronLungMod.id("taken"));
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.35f, 0.0f, 0.0f), 0.9f);
    private static final Map<UUID, Packets.MissionEnd> PENDING_SUMMARIES = new HashMap<>();

    /** Spawn spots inside the 3x5 interior, one per crew slot. */
    private static final double[][] SPAWNS = {{1.5, 2.5}, {0.6, 3.6}, {2.4, 3.6}, {1.5, 4.4}};

    private static MissionSavedData data(MinecraftServer server) {
        return MissionSavedData.get(server);
    }

    public static Mission get(ServerPlayer p) {
        return data(p.server).missionOf(p.getUUID());
    }

    public static boolean hasMission(ServerPlayer p) {
        Mission m = get(p);
        return m != null && m.phase != Mission.Phase.DONE;
    }

    public static boolean isLeader(ServerPlayer p, Mission m) {
        return m.leader.equals(p.getUUID());
    }

    // ------------------------------------------------------------------ crew helpers

    /** Crew members who are online and inside the submarine dimension. */
    public static List<ServerPlayer> aboard(Mission m) {
        List<ServerPlayer> out = new ArrayList<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return out;
        for (UUID u : m.crew.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && p.level().dimension().equals(IronLungMod.DIMENSION)) out.add(p);
        }
        return out;
    }

    public static void broadcast(Mission m, Object msg) {
        for (ServerPlayer p : aboard(m)) ModNetwork.toPlayer(p, msg);
    }

    public static void tellCrew(Mission m, Component c, boolean actionBar) {
        for (ServerPlayer p : aboard(m)) p.displayClientMessage(c, actionBar);
    }

    private static Mission.ReturnPoint returnPointOf(ServerPlayer p) {
        Mission.ReturnPoint r = new Mission.ReturnPoint();
        r.dim = p.level().dimension().location().toString();
        r.x = p.getX();
        r.y = p.getY();
        r.z = p.getZ();
        r.yaw = p.getYRot();
        r.pitch = p.getXRot();
        return r;
    }

    private static void sendHome(ServerPlayer p, Mission.ReturnPoint r) {
        MinecraftServer server = p.server;
        ServerLevel target = r == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(r.dim)));
        if (target == null || target.dimension().equals(IronLungMod.DIMENSION)) {
            ServerLevel ow = server.overworld();
            BlockPos s = ow.getSharedSpawnPos();
            p.teleportTo(ow, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0f, 0f);
        } else {
            p.teleportTo(target, r.x, r.y, r.z, r.yaw, r.pitch);
        }
    }

    private static void boardAt(ServerPlayer p, Mission m, ServerLevel sub, int index) {
        BlockPos base = m.base();
        double[] s = SPAWNS[Math.min(SPAWNS.length - 1, index)];
        p.teleportTo(sub, base.getX() + s[0], base.getY() + SubInterior.SPAWN_Y, base.getZ() + s[1], 180f, 0f);
    }

    private static int indexOf(Mission m, UUID u) {
        int i = 0;
        for (UUID k : m.crew.keySet()) {
            if (k.equals(u)) return i;
            i++;
        }
        return 0;
    }

    private static List<String> crewNames(Mission m) {
        List<String> out = new ArrayList<>();
        for (Mission.CrewMember c : m.crew.values()) out.add(c.name);
        return out;
    }

    public static void sendInit(ServerPlayer p, Mission m) {
        m.map();
        ModNetwork.toPlayer(p, new Packets.MissionInit(true, m.seed, m.mapSize, m.objectiveCount, m.hiddenCount, m.slot,
                m.objectiveDone.clone(), new ArrayList<>(m.photos), m.ticks, crewNames(m), isLeader(p, m)));
    }

    private static void resyncCrew(Mission m) {
        for (ServerPlayer p : aboard(m)) sendInit(p, m);
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
        if (d.missionOf(p.getUUID()) != null) return false;
        int slot = d.nextSlot++;
        Mission m = new Mission(UUID.randomUUID(), p.getUUID(), slot, seed, IronLungConfig.MAP_SIZE.get(),
                IronLungConfig.OBJECTIVES.get(), IronLungConfig.HIDDEN_ANOMALIES.get());
        OceanMap map = m.map();
        m.x = map.startX;
        m.z = map.startZ;
        m.heading = 0;
        m.depth = Math.max(OceanMap.MIN_SUB_DEPTH + 5, map.floorDepth(m.x, m.z) - 12);
        m.sealant = IronLungConfig.SEALANT_CHARGES.get();
        m.nextEventTick = 1000 + p.getRandom().nextInt(600); // quiet while the opening plays
        Mission.CrewMember c = new Mission.CrewMember(p.getUUID(), p.getGameProfile().getName());
        c.ret = returnPointOf(p);
        m.crew.put(c.id, c);

        BlockPos base = m.base();
        SubInterior.build(sub, base);
        d.missions.put(m.id, m);
        d.reindex();
        d.setDirty();

        boardAt(p, m, sub, 0);
        sendInit(p, m);
        sub.playSound(null, base.getX() + 1.5, base.getY() + 1.5, base.getZ() + 5.5, ModSounds.BREAKER_CLUNK.get(), SoundSource.BLOCKS, 1.5f, 0.6f);
        sub.playSound(null, base.getX() + 1.5, base.getY() + 1.5, base.getZ() + 2.5, ModSounds.HULL_GROAN.get(), SoundSource.AMBIENT, 1.0f, 0.7f);
        IronLungMod.LOGGER.info("{} began a dive (seed {}, slot {})", c.name, seed, slot);
        return true;
    }

    /** Bring another player aboard a dive already under way. */
    public static boolean joinMission(ServerPlayer p, Mission m) {
        MinecraftServer server = p.server;
        ServerLevel sub = server.getLevel(IronLungMod.DIMENSION);
        if (sub == null || m.phase != Mission.Phase.DIVING) {
            p.sendSystemMessage(Component.translatable("ironlung.msg.join_closed").withStyle(ChatFormatting.DARK_RED));
            return false;
        }
        if (m.crew.size() >= IronLungConfig.MAX_CREW.get()) {
            p.sendSystemMessage(Component.translatable("ironlung.msg.join_full").withStyle(ChatFormatting.DARK_RED));
            return false;
        }
        MissionSavedData d = data(server);
        if (d.missionOf(p.getUUID()) != null) return false;
        Mission.CrewMember c = new Mission.CrewMember(p.getUUID(), p.getGameProfile().getName());
        c.ret = returnPointOf(p);
        m.crew.put(c.id, c);
        d.reindex();
        d.setDirty();
        boardAt(p, m, sub, m.crew.size() - 1);
        BlockPos b = m.base();
        sub.playSound(null, b.getX() + 1.5, b.getY() + 1.5, b.getZ() + 5.5, ModSounds.BREAKER_CLUNK.get(), SoundSource.BLOCKS, 1.2f, 0.7f);
        tellCrew(m, Component.translatable("ironlung.msg.crew_joined", c.name).withStyle(ChatFormatting.GRAY), false);
        resyncCrew(m);
        sendInit(p, m);
        return true;
    }

    /** One crew member leaves; the dive continues for the rest. */
    public static void leaveMission(ServerPlayer p, Mission m, boolean teleportBack) {
        Mission.CrewMember c = m.crew.remove(p.getUUID());
        if (c == null) return;
        MissionSavedData d = data(p.server);
        d.reindex();
        d.setDirty();
        if (p.getUUID().equals(m.camOperator)) m.camOperator = null;
        if (p.getUUID().equals(m.welder)) m.weldTicks = 0;
        Packets.MissionEnd summary = summary(m, Packets.MissionEnd.ABORTED);
        if (teleportBack) sendHome(p, c.ret);
        ModNetwork.toPlayer(p, Packets.MissionInit.inactive());
        if (p.isAlive()) ModNetwork.toPlayer(p, summary);
        else PENDING_SUMMARIES.put(p.getUUID(), summary);
        if (m.crew.isEmpty()) {
            finish(p.server, m, Packets.MissionEnd.ABORTED, true);
            return;
        }
        if (m.leader.equals(p.getUUID())) m.leader = m.crew.keySet().iterator().next();
        tellCrew(m, Component.translatable("ironlung.msg.crew_left", c.name).withStyle(ChatFormatting.GRAY), false);
        resyncCrew(m);
    }

    private static Packets.MissionEnd summary(Mission m, int outcome) {
        return new Packets.MissionEnd(outcome, m.ticks, m.objectivesComplete(), m.objectiveDone.length,
                m.photos.size(), m.anomaliesFound.size(), Math.max(0, m.hull), m.collisions, m.powerFailures, m.sightings);
    }

    /** End the dive for everyone aboard. Offline crew are sent home when they next log in. */
    public static void finish(MinecraftServer server, Mission m, int outcome, boolean teleportBack) {
        MissionSavedData d = data(server);
        m.phase = Mission.Phase.DONE;
        Packets.MissionEnd summary = summary(m, outcome);
        for (Mission.CrewMember c : new ArrayList<>(m.crew.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(c.id);
            if (p == null) {
                d.pendingReturns.put(c.id, c.ret);
                continue;
            }
            if (p.isAlive()) {
                if (teleportBack || p.level().dimension().equals(IronLungMod.DIMENSION)) sendHome(p, c.ret);
                ModNetwork.toPlayer(p, Packets.MissionInit.inactive());
                ModNetwork.toPlayer(p, summary);
            } else {
                ModNetwork.toPlayer(p, Packets.MissionInit.inactive());
                PENDING_SUMMARIES.put(p.getUUID(), summary);
            }
        }
        m.crew.clear();
        d.missions.remove(m.id);
        d.reindex();
        d.setDirty();
        ServerLevel sub = server.getLevel(IronLungMod.DIMENSION);
        if (sub != null) SubInterior.clear(sub, m.base());
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
        if (m == null || m.phase == Mission.Phase.DONE) return;
        if (m.phase == Mission.Phase.IMPLODING || m.phase == Mission.Phase.ASCENDING) return; // the ending handles everyone
        if (m.crew.size() <= 1) {
            finish(p.server, m, Packets.MissionEnd.LOST, false);
        } else {
            tellCrew(m, Component.translatable("ironlung.msg.crew_died", p.getGameProfile().getName()).withStyle(ChatFormatting.DARK_RED), false);
            leaveMission(p, m, false);
        }
    }

    public static void onLogin(ServerPlayer p) {
        MissionSavedData d = data(p.server);
        Mission.ReturnPoint pending = d.pendingReturns.remove(p.getUUID());
        if (pending != null) {
            d.setDirty();
            if (p.level().dimension().equals(IronLungMod.DIMENSION)) sendHome(p, pending);
            ModNetwork.toPlayer(p, Packets.MissionInit.inactive());
            p.sendSystemMessage(Component.translatable("ironlung.msg.dive_ended_offline").withStyle(ChatFormatting.GRAY));
            return;
        }
        Mission m = d.missionOf(p.getUUID());
        if (m == null) {
            if (p.level().dimension().equals(IronLungMod.DIMENSION)) {
                sendHome(p, null);
                ModNetwork.toPlayer(p, Packets.MissionInit.inactive());
            }
            return;
        }
        if (!p.level().dimension().equals(IronLungMod.DIMENSION)) {
            leaveMission(p, m, false);
            return;
        }
        m.crew.get(p.getUUID()).throttle = 0;
        m.appliedLamps = null;
        m.appliedConsoles = null;
        sendInit(p, m);
    }

    public static void onLogout(ServerPlayer p) {
        Mission m = get(p);
        if (m == null) return;
        Mission.CrewMember c = m.crew.get(p.getUUID());
        if (c != null) c.throttle = c.turn = c.ballast = 0;
        if (p.getUUID().equals(m.camOperator)) m.camOperator = null;
        if (p.getUUID().equals(m.welder)) m.weldTicks = 0;
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(MinecraftServer server) {
        MissionSavedData d = data(server);
        if (d.missions.isEmpty()) return;
        ServerLevel sub = server.getLevel(IronLungMod.DIMENSION);
        if (sub == null) return;
        for (Mission m : new ArrayList<>(d.missions.values())) {
            // anyone who walked out of the dimension by other means has left the crew
            for (UUID u : new ArrayList<>(m.crew.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null && p.isAlive() && !p.level().dimension().equals(IronLungMod.DIMENSION)) leaveMission(p, m, false);
            }
            if (m.phase == Mission.Phase.DONE) continue;
            List<ServerPlayer> crew = aboard(m);
            crew.removeIf(p -> !p.isAlive());
            if (crew.isEmpty()) continue; // paused while nobody is aboard
            try {
                tickMission(m, crew, sub);
            } catch (Exception e) {
                IronLungMod.LOGGER.error("Dive tick failed; ending dive safely", e);
                finish(server, m, Packets.MissionEnd.ABORTED, true);
                continue;
            }
            if (m.ticks % 200 == 0) d.setDirty();
        }
    }

    private static void tickMission(Mission m, List<ServerPlayer> crew, ServerLevel lvl) {
        if (m.phase == Mission.Phase.DONE) return;
        m.ticks++;
        m.phaseTicks++;
        RandomSource r = lvl.getRandom();
        switch (m.phase) {
            case DIVING -> {
                physics(m, lvl, r);
                systems(m, lvl, r);
                if (m.phase == Mission.Phase.DIVING) HorrorDirector.tick(m, lvl);
            }
            case ASCENDING -> ascend(m, lvl, r);
            case IMPLODING -> implode(m, crew, lvl, r);
            default -> {}
        }
        if (m.phase == Mission.Phase.DONE) return;
        applyPowerVisuals(m, lvl, r);
        if (m.ticks % 4 == 0) updateProximity(m);
        if (m.ticks % 2 == 0) for (ServerPlayer p : crew) sendState(p, m);
        for (ServerPlayer p : crew) keepInside(p, m);
    }

    private static void keepInside(ServerPlayer p, Mission m) {
        BlockPos b = m.base();
        double rx = p.getX() - b.getX(), ry = p.getY() - b.getY(), rz = p.getZ() - b.getZ();
        if (rx < -0.5 || rx > 3.5 || ry < -1.5 || ry > 3.5 || rz < -0.5 || rz > 5.5) {
            double[] s = SPAWNS[Math.min(SPAWNS.length - 1, indexOf(m, p.getUUID()))];
            p.teleportTo(b.getX() + s[0], b.getY() + SubInterior.SPAWN_Y, b.getZ() + s[1]);
        }
    }

    private static void physics(Mission m, ServerLevel lvl, RandomSource r) {
        OceanMap map = m.map();
        boolean pw = m.powered();
        double thr = pw ? m.throttle() : 0, turn = pw ? m.turn() : 0, bal = pw ? m.ballast() : 0;
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
        double floorHere = map.floorDepth(nx, nz);
        if (floorHere < nd + 1.8) {
            severity += Math.abs(m.vertSpeed) * 1.2 + 0.05;
            nd = floorHere - 1.8;
            m.vertSpeed = Math.min(0, m.vertSpeed) * -0.2;
            hitAt = new BlockPos(1, -1, 2);
        }
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
            broadcast(m, new Packets.HorrorEvent(HorrorType.COLLISION.ordinal(), (float) Math.min(1.2, severity * 0.8), r.nextLong(), 0, 0));
            if (severity > 1.0 && m.powered() && r.nextFloat() < 0.3f) HorrorDirector.tripPower(m, lvl);
            else if (severity > 0.5 && m.powered() && r.nextFloat() < 0.4f) m.flickerTicks = 20 + r.nextInt(20);
        }
    }

    private static void systems(Mission m, ServerLevel lvl, RandomSource r) {
        BlockPos base = m.base();
        double dmgMul = IronLungConfig.HULL_DAMAGE.get();
        if (m.depth > OceanMap.CRUSH_DEPTH) {
            m.hull -= (float) ((m.depth - OceanMap.CRUSH_DEPTH) * 0.00004 * dmgMul);
            if (m.ticks % 50 == 0) {
                HorrorDirector.playHull(m, lvl, r, ModSounds.HULL_GROAN.get(), 1.1f, 0.6f + r.nextFloat() * 0.2f);
                HorrorDirector.playAtControl(m, lvl, ControlType.DEPTH_GAUGE, ModSounds.ALARM.get(), 0.6f, 1.0f);
            }
        } else if (m.depth > OceanMap.WARN_DEPTH && m.ticks % 220 == 0) {
            HorrorDirector.playHull(m, lvl, r, ModSounds.HULL_CREAK.get(), 0.9f, 0.6f);
        }
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
        if (m.weldTicks > 0) {
            BlockPos st = base.offset(SubInterior.CONTROL_POS.get(ControlType.SEALANT));
            ServerPlayer welder = m.welder == null ? null : lvl.getServer().getPlayerList().getPlayer(m.welder);
            if (welder == null || welder.distanceToSqr(st.getX() + 0.5, st.getY() + 0.5, st.getZ() + 0.5) > 3.2 * 3.2) {
                m.weldTicks = 0;
                if (welder != null) welder.displayClientMessage(Component.translatable("ironlung.msg.weld_abandoned").withStyle(ChatFormatting.GRAY), true);
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
                    tellCrew(m, Component.translatable("ironlung.msg.weld_done", m.sealant).withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
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
        double[] angles = {0, Math.PI / 2, Math.PI, -Math.PI / 2};
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
                m.cameraCondition, m.prox.clone(), mask, m.phase.ordinal(), m.sealant, m.weldTicks > 0, m.glitchTicks, m.photoCooldown,
                m.camMount, m.camPan, m.camTilt, p.getUUID().equals(m.camOperator)));
    }

    // ------------------------------------------------------------------ endings

    private static void ascend(Mission m, ServerLevel lvl, RandomSource r) {
        int t = m.phaseTicks;
        m.depth = Math.max(OceanMap.MIN_SUB_DEPTH, m.depth - 0.06);
        m.speed *= 0.95;
        switch (t) {
            case 1 -> {
                HorrorDirector.playAtControl(m, lvl, ControlType.BALLAST, ModSounds.PRESSURE_HISS.get(), 1.4f, 0.7f);
                tellCrew(m, Component.translatable("ironlung.msg.ascending").withStyle(ChatFormatting.DARK_RED), true);
            }
            case 90 -> HorrorDirector.trigger(m, lvl, HorrorType.GROAN);
            case 170 -> m.flickerTicks = 50;
            case 240 -> {
                HorrorDirector.tripPower(m, lvl);
                HorrorDirector.trigger(m, lvl, HorrorType.SILENCE);
                m.chainedEvent = -1;
            }
            case 330 -> {
                BlockPos at = m.base().offset(1, 1, -2);
                lvl.playSound(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, ModSounds.LEVIATHAN.get(), SoundSource.AMBIENT, 3.0f, 0.75f);
                broadcast(m, new Packets.HorrorEvent(HorrorType.LEVIATHAN.ordinal(), 1.0f, r.nextLong(), 7f, 1f));
            }
            case 400 -> {
                HorrorDirector.playHull(m, lvl, r, ModSounds.HULL_IMPACT.get(), 2.0f, 0.6f);
                broadcast(m, new Packets.HorrorEvent(HorrorType.IMPACT.ordinal(), 1.3f, r.nextLong(), 0, 0));
            }
            case 430 -> HorrorDirector.trigger(m, lvl, HorrorType.SILENCE);
            case 520 -> HorrorDirector.playAtControl(m, lvl, ControlType.HATCH, ModSounds.HATCH_KNOCK.get(), 1.2f, 0.8f);
            case 600 -> HorrorDirector.playAtControl(m, lvl, ControlType.HATCH, ModSounds.HATCH_KNOCK.get(), 1.6f, 0.65f);
            case 660 -> {
                // the hatch gives
                HorrorDirector.playAtControl(m, lvl, ControlType.HATCH, ModSounds.HATCH_BREACH.get(), 2.5f, 0.9f);
                broadcast(m, new Packets.HorrorEvent(HorrorType.IMPACT.ordinal(), 1.6f, r.nextLong(), 0, 0));
                BlockPos h = m.base().offset(SubInterior.CONTROL_POS.get(ControlType.HATCH));
                lvl.sendParticles(BLOOD, h.getX() + 0.5, h.getY() + 0.5, h.getZ() - 0.2, 60, 0.6, 0.8, 0.2, 0.05);
            }
            case 680, 715, 745 -> {
                // something wet breathing, coming in from the hatch toward you
                double zz = t == 680 ? 4.6 : t == 715 ? 3.4 : 2.4;
                BlockPos b = m.base();
                lvl.playSound(null, b.getX() + 1.5, b.getY() + 1.5, b.getZ() + zz, ModSounds.BREATHING.get(), SoundSource.HOSTILE, 1.2f + (t - 680) * 0.01f, 0.85f);
            }
            case 785 -> broadcast(m, new Packets.HorrorEvent(HorrorType.JUMPSCARE.ordinal(), 0.4f, r.nextLong(), 1.9f, 0));
            case 797 -> {
                BlockPos h = m.base().offset(1, 1, 2);
                lvl.playSound(null, h.getX() + 0.5, h.getY() + 0.5, h.getZ() + 0.5, ModSounds.JUMPSCARE.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
                broadcast(m, new Packets.HorrorEvent(HorrorType.COLLISION.ordinal(), 2.5f, r.nextLong(), 0, 0));
            }
            case 826 -> {
                if (IronLungConfig.FINALE_KILLS.get()) {
                    DamageSource src = new DamageSource(lvl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TAKEN));
                    for (ServerPlayer p : aboard(m)) {
                        if (!p.isCreative() && !p.isSpectator()) p.hurt(src, Float.MAX_VALUE);
                    }
                }
            }
            default -> {}
        }
        if (t >= 856) {
            finish(lvl.getServer(), m, IronLungConfig.FINALE_KILLS.get() ? Packets.MissionEnd.TAKEN : Packets.MissionEnd.RECOVERED, true);
        }
    }

    private static void implode(Mission m, List<ServerPlayer> crew, ServerLevel lvl, RandomSource r) {
        int t = m.phaseTicks;
        if (t == 1) {
            if (m.powered()) HorrorDirector.tripPower(m, lvl);
            HorrorDirector.playAtControl(m, lvl, ControlType.PRESSURE_GAUGE, ModSounds.ALARM.get(), 1.5f, 0.7f);
            HorrorDirector.playHull(m, lvl, r, ModSounds.HULL_GROAN.get(), 2.0f, 0.5f);
            broadcast(m, new Packets.HorrorEvent(HorrorType.IMPACT.ordinal(), 0.6f, r.nextLong(), 0, 0));
        } else if (t == 35) {
            BlockPos b = m.base();
            lvl.playSound(null, b.getX() + 1.5, b.getY() + 1.5, b.getZ() + 2.5, ModSounds.IMPLOSION.get(), SoundSource.AMBIENT, 3.0f, 1.0f);
            broadcast(m, new Packets.HorrorEvent(HorrorType.IMPACT.ordinal(), 2.0f, r.nextLong(), 1, 0));
        } else if (t == 50) {
            if (IronLungConfig.LETHAL_IMPLOSION.get()) {
                DamageSource src = new DamageSource(lvl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CRUSHED));
                for (ServerPlayer p : crew) {
                    if (!p.isCreative() && !p.isSpectator()) p.hurt(src, Float.MAX_VALUE);
                }
            }
            finish(lvl.getServer(), m, Packets.MissionEnd.LOST, true);
        }
    }

    // ------------------------------------------------------------------ input

    public static void onHelmInput(ServerPlayer p, float throttle, float turn, float ballast) {
        Mission m = get(p);
        if (m == null || m.phase != Mission.Phase.DIVING) return;
        Mission.CrewMember c = m.crew.get(p.getUUID());
        if (c == null) return;
        c.throttle = (float) clamp(throttle, -1, 1);
        c.turn = (float) clamp(turn, -1, 1);
        c.ballast = (float) clamp(ballast, -1, 1);
    }

    /**
     * There is one camera. Whoever is actively steering it (or just took a photo) is its operator;
     * everyone else's monitor follows what the operator is looking at.
     */
    public static void onCamera(ServerPlayer p, int mount, float pan, float tilt, boolean capture, boolean steering) {
        Mission m = get(p);
        if (m == null) return;
        m.cameraActiveUntil = m.ticks + 50;
        boolean operatorAway = m.camOperator == null || p.server.getPlayerList().getPlayer(m.camOperator) == null;
        if (steering || operatorAway || p.getUUID().equals(m.camOperator)) {
            m.camOperator = p.getUUID();
            m.camMount = Math.floorMod(mount, CameraMount.values().length);
            m.camPan = (float) clamp(pan, -CameraMount.MAX_PAN, CameraMount.MAX_PAN);
            m.camTilt = (float) clamp(tilt, -CameraMount.MAX_TILT, CameraMount.MAX_TILT);
        }
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
                if (dist > 52 + poi.radius * 0.4) continue;
                double cos = (vx * dx + vy * dy + vz * dz) / Math.max(1e-6, dist);
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
        broadcast(m, new Packets.PhotoResult(rec, newly.stream().mapToInt(Integer::intValue).toArray(), anomaly));
        data(p.server).setDirty();

        double nearTarget = 0;
        for (Poi o : map.objectives) nearTarget = Math.max(nearTarget, 1 - Math.hypot(o.x - m.x, o.z - m.z) / 60.0);
        double failChance = 0.03 + m.tension * 0.10 + Math.max(0, nearTarget) * 0.06;
        if (r.nextDouble() < failChance) {
            HorrorDirector.chain(m, r.nextFloat() < 0.35f && m.ticks - m.lastPowerFailTick > 3000 ? HorrorType.BLACKOUT : HorrorType.FLICKER, 12 + r.nextInt(25));
        }
        if (!newly.isEmpty() && m.allObjectivesDone()) {
            tellCrew(m, Component.translatable("ironlung.msg.all_logged").withStyle(ChatFormatting.DARK_RED), true);
            m.nextEventTick = Math.min(m.nextEventTick, m.ticks + 300);
        }
    }

    /** Debug/testing: mark every survey site as photographed. */
    public static void completeAll(Mission m) {
        m.map();
        java.util.Arrays.fill(m.objectiveDone, true);
        tellCrew(m, Component.translatable("ironlung.msg.all_logged").withStyle(ChatFormatting.DARK_RED), true);
        data(ServerLifecycleHooks.getCurrentServer()).setDirty();
    }

    /** Surface (anyone, once the survey is complete) or abort (leader ends it for all; crew just leave). */
    public static void onSurface(ServerPlayer p, boolean abort) {
        Mission m = get(p);
        if (m == null || m.phase != Mission.Phase.DIVING) return;
        if (abort) {
            if (isLeader(p, m)) finish(p.server, m, Packets.MissionEnd.ABORTED, true);
            else leaveMission(p, m, true);
            return;
        }
        if (!m.allObjectivesDone()) {
            p.displayClientMessage(Component.translatable("ironlung.msg.incomplete").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        m.phase = Mission.Phase.ASCENDING;
        m.phaseTicks = 0;
        m.clearInputs();
        broadcast(m, new Packets.HorrorEvent(HorrorType.JUMPSCARE_PRIME.ordinal(), 0f, p.getRandom().nextLong(), 0, 0));
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
                    tellCrew(m, Component.translatable("ironlung.msg.breaker_reset").withStyle(ChatFormatting.GRAY), true);
                } else if (m.power == Mission.Power.RESTORING) {
                    say(p, "ironlung.msg.breaker_busy", ChatFormatting.GRAY);
                } else {
                    say(p, "ironlung.msg.breaker_ok", ChatFormatting.GRAY);
                }
            }
            case PRESSURE_GAUGE -> {
                double atm = 1 + m.depth / 10.0;
                ChatFormatting c = m.depth > OceanMap.CRUSH_DEPTH ? ChatFormatting.RED : m.depth > OceanMap.WARN_DEPTH ? ChatFormatting.GOLD : ChatFormatting.GRAY;
                if (!m.powered()) say(p, "ironlung.msg.gauge_dead", ChatFormatting.DARK_GRAY);
                else p.displayClientMessage(Component.translatable("ironlung.msg.pressure", String.format("%.1f", atm), Math.round(m.hull * 100)).withStyle(c), true);
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
                if (!m.powered()) say(p, "ironlung.msg.gauge_dead", ChatFormatting.DARK_GRAY);
                else p.displayClientMessage(Component.translatable("ironlung.msg.proximity", fmt(m.prox[0]), fmt(m.prox[1]), fmt(m.prox[2]), fmt(m.prox[3])).withStyle(ChatFormatting.GRAY), true);
            }
            case SEALANT -> {
                if (m.weldTicks > 0) say(p, "ironlung.msg.weld_busy", ChatFormatting.GRAY);
                else if (m.sealant <= 0) say(p, "ironlung.msg.weld_empty", ChatFormatting.DARK_RED);
                else if (m.hull >= 0.97f) say(p, "ironlung.msg.weld_none", ChatFormatting.GRAY);
                else {
                    m.weldTicks = 100;
                    m.welder = p.getUUID();
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
