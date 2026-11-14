package com.aegis.command.mission;

import com.aegis.command.AegisCommand;
import com.aegis.command.AegisConfig;
import com.aegis.command.entity.AerialBomb;
import com.aegis.command.entity.StrikeJet;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import com.aegis.command.registry.ModBlocks;
import com.aegis.command.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Server-side flight director. Aircraft follow a fixed, time-based flight plan that this class
 * advances every server tick, so strikes keep moving even when the aircraft is outside the
 * simulation distance, and the client can reproduce the exact same path for the cinematic.
 */
@Mod.EventBusSubscriber(modid = AegisCommand.MODID)
public final class StrikeManager {
    public static final int LAUNCH_DELAY = 50;
    public static final double ALTITUDE = 36;
    public static final float BOMB_GRAVITY = 0.06f;

    private static final Map<UUID, Strike> STRIKES = new HashMap<>();

    private static final String[] ADJ = {"IRON", "SILENT", "CRIMSON", "HOLLOW", "NORTHERN", "GLASS", "EMBER", "BROKEN",
            "STEEL", "MIDNIGHT", "SHATTERED", "GILDED", "PALE", "THUNDER", "COLD", "SOVEREIGN", "ASHEN", "ARC"};
    private static final String[] NOUN = {"ANVIL", "VIGIL", "TEMPEST", "LANTERN", "SPEAR", "HARBOR", "MERIDIAN", "WARDEN",
            "CASCADE", "BASTION", "FALCON", "OBELISK", "RAMPART", "TIDE", "HALO", "CITADEL", "COMET", "LONGBOW"};

    private StrikeManager() {}

    public static boolean isActive(UUID player) {
        Strike s = STRIKES.get(player);
        return s != null && !s.done;
    }

    public static final class Strike {
        UUID owner;
        ResourceKey<Level> dim;
        int missionId;
        String codename;
        AircraftType aircraft;
        int heading;
        Vec3 target;
        Vec3 start;
        double dirX, dirZ;
        long acceptTime;
        long spawnTime = -1;
        int[] dropT;
        double[] lateral;
        int fallTicks;
        int lifeTicks;
        int dropped, detonated, kills;
        int jetId = -1;
        boolean done;

        Vec3 pathPos(double t) {
            return new Vec3(start.x + dirX * aircraft.speed * t, start.y, start.z + dirZ * aircraft.speed * t);
        }

        CompoundTag plan() {
            CompoundTag t = new CompoundTag();
            t.putInt("Jet", jetId);
            t.putInt("Ac", aircraft.ordinal());
            t.putString("Code", codename);
            t.putDouble("SX", start.x); t.putDouble("SY", start.y); t.putDouble("SZ", start.z);
            t.putDouble("DX", dirX); t.putDouble("DZ", dirZ);
            t.putDouble("TX", target.x); t.putDouble("TY", target.y); t.putDouble("TZ", target.z);
            t.putLong("Spawn", spawnTime);
            t.putInt("FirstDrop", dropT[0]);
            t.putInt("LastDrop", dropT[dropT.length - 1]);
            t.putInt("Fall", fallTicks);
            t.putInt("Life", lifeTicks);
            t.putInt("Bombs", dropT.length);
            return t;
        }
    }

    public static void requestStrike(ServerPlayer p, Packets.LaunchStrike m) {
        ServerLevel level = p.serverLevel();
        BlockPos laptop = m.laptop();
        if (!level.getBlockState(laptop).is(ModBlocks.LAPTOP.get()) || p.distanceToSqr(Vec3.atCenterOf(laptop)) > 64) {
            deny(p, "TERMINAL LINK LOST");
            return;
        }
        if (isActive(p.getUUID())) {
            deny(p, "STRIKE ALREADY IN PROGRESS");
            return;
        }
        long now = level.getGameTime();
        long cd = AegisConfig.COMMON.cooldownSeconds.get() * 20L;
        long remaining = MissionData.lastStrike(p) + cd - now;
        if (remaining > 0) {
            deny(p, "AIRFRAME REARMING - " + (remaining / 20 + 1) + "s");
            return;
        }
        double dx = m.targetX() + 0.5 - (laptop.getX() + 0.5);
        double dz = m.targetZ() + 0.5 - (laptop.getZ() + 0.5);
        int range = AegisConfig.COMMON.maxRange.get();
        if (dx * dx + dz * dz > (range + 0.5) * (range + 0.5)) {
            deny(p, "TARGET OUTSIDE STRIKE RADIUS");
            return;
        }
        if (!level.hasChunk(m.targetX() >> 4, m.targetZ() >> 4)) {
            deny(p, "TARGET SECTOR NOT LOADED");
            return;
        }

        Strike s = new Strike();
        s.owner = p.getUUID();
        s.dim = level.dimension();
        s.aircraft = AircraftType.byIndex(m.aircraft());
        s.heading = m.heading();
        s.missionId = MissionData.nextMissionId(p);
        s.codename = codename(s.missionId, p.getUUID());
        int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING, m.targetX(), m.targetZ());
        s.target = new Vec3(m.targetX() + 0.5, ty, m.targetZ() + 0.5);

        double fx, fz;
        switch (m.heading()) {
            case 0 -> { fx = 0; fz = 1; }   // from the north, flying south
            case 1 -> { fx = -1; fz = 0; }  // from the east
            case 2 -> { fx = 0; fz = -1; }  // from the south
            case 3 -> { fx = 1; fz = 0; }   // from the west
            default -> {
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 4) {
                    Vec3 look = p.getLookAngle();
                    fx = look.x; fz = look.z;
                    double l2 = Math.sqrt(fx * fx + fz * fz);
                    if (l2 < 1e-3) { fx = 0; fz = 1; } else { fx /= l2; fz /= l2; }
                } else {
                    fx = dx / len; fz = dz / len;
                }
            }
        }
        s.dirX = fx;
        s.dirZ = fz;

        double alt = Math.min(s.target.y + ALTITUDE, level.getMaxBuildHeight() - 6);
        double drop = alt - s.target.y - 1.2;
        double distance = AegisConfig.COMMON.approachDistance.get();
        s.start = new Vec3(s.target.x - fx * distance, alt, s.target.z - fz * distance);

        // simulate one bomb to find how far ahead of the target it must be released
        double vy = -0.05, y = 0, vh = s.aircraft.speed, lead = 0;
        int ticks = 0;
        while (y > -drop && ticks < 400) {
            y += vy;
            lead += vh;
            vy = vy * 0.99 - BOMB_GRAVITY;
            vh *= 0.99;
            ticks++;
        }
        s.fallTicks = ticks;
        int n = s.aircraft.bombs;
        s.dropT = new int[n];
        s.lateral = new double[n];
        for (int i = 0; i < n; i++) {
            double offset = (i - (n - 1) / 2.0) * s.aircraft.spacing;
            s.dropT[i] = (int) Math.round((distance + offset - lead) / s.aircraft.speed);
            s.lateral[i] = (i - (n - 1) / 2.0) * s.aircraft.lateral;
        }
        s.lifeTicks = (int) (distance * 2.2 / s.aircraft.speed) + 20;
        s.acceptTime = now;

        STRIKES.put(p.getUUID(), s);
        MissionData.setLastStrike(p, now);
        AegisNetwork.toPlayer(p, new Packets.StrikeResponse(true, "STRIKE AUTHORIZED", s.codename, LAUNCH_DELAY));
    }

    private static void deny(ServerPlayer p, String reason) {
        AegisNetwork.toPlayer(p, new Packets.StrikeResponse(false, reason, "", 0));
    }

    private static String codename(int id, UUID owner) {
        Random r = new Random(owner.getLeastSignificantBits() * 31 + id * 7919L);
        return "OPERATION " + ADJ[r.nextInt(ADJ.length)] + " " + NOUN[r.nextInt(NOUN.length)];
    }

    public static void onBombDetonated(UUID owner, int kills) {
        Strike s = STRIKES.get(owner);
        if (s == null) return;
        s.detonated++;
        s.kills += kills;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || STRIKES.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Iterator<Map.Entry<UUID, Strike>> it = STRIKES.entrySet().iterator();
        while (it.hasNext()) {
            Strike s = it.next().getValue();
            ServerLevel level = server.getLevel(s.dim);
            if (level == null) { it.remove(); continue; }
            if (tick(server, level, s)) it.remove();
        }
    }

    /** @return true when the strike can be forgotten */
    private static boolean tick(MinecraftServer server, ServerLevel level, Strike s) {
        long now = level.getGameTime();
        ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);

        if (s.spawnTime < 0) {
            if (now < s.acceptTime + LAUNCH_DELAY) return false;
            StrikeJet jet = new StrikeJet(ModEntities.STRIKE_JET.get(), level);
            s.spawnTime = now;
            jet.setFlightPlan(s.aircraft, s.start, s.dirX, s.dirZ, s.spawnTime, s.lifeTicks);
            level.addFreshEntity(jet);
            s.jetId = jet.getId();
            if (owner != null) AegisNetwork.toPlayer(owner, new Packets.StrikeLaunched(s.plan()));
            return false;
        }

        long t = now - s.spawnTime;
        Entity e = level.getEntity(s.jetId);
        StrikeJet jet = e instanceof StrikeJet j ? j : null;
        if (jet != null) jet.followPlan();

        while (s.dropped < s.dropT.length && t >= s.dropT[s.dropped]) {
            int i = s.dropped++;
            Vec3 pos = s.pathPos(t).add(0, -1.2, 0);
            AerialBomb bomb = new AerialBomb(ModEntities.AERIAL_BOMB.get(), level);
            bomb.setPos(pos.x, pos.y, pos.z);
            if (owner != null) bomb.setOwner(owner);
            bomb.setOwnerId(s.owner);
            bomb.setPower(s.aircraft.power);
            double px = -s.dirZ, pz = s.dirX; // perpendicular
            bomb.setDeltaMovement(s.dirX * s.aircraft.speed + px * s.lateral[i], -0.05,
                    s.dirZ * s.aircraft.speed + pz * s.lateral[i]);
            level.addFreshEntity(bomb);
        }

        boolean allDropped = s.dropped >= s.dropT.length;
        if (!s.done && allDropped && (s.detonated >= s.dropT.length || t > s.dropT[s.dropT.length - 1] + 200)) {
            s.done = true;
            finish(level, s, owner, t);
        }

        if (t > s.lifeTicks) {
            if (jet != null) jet.discard();
            if (s.done) return true;
        }
        return s.done && t > s.lifeTicks;
    }

    private static void finish(ServerLevel level, Strike s, ServerPlayer owner, long t) {
        CompoundTag rec = new CompoundTag();
        rec.putInt("Id", s.missionId);
        rec.putString("Code", s.codename);
        rec.putInt("Ac", s.aircraft.ordinal());
        rec.putInt("X", (int) Math.floor(s.target.x));
        rec.putInt("Y", (int) Math.floor(s.target.y));
        rec.putInt("Z", (int) Math.floor(s.target.z));
        rec.putInt("Bombs", s.dropped);
        rec.putInt("Hits", s.detonated);
        rec.putInt("Kills", s.kills);
        rec.putInt("Dur", (int) t + LAUNCH_DELAY);
        rec.putLong("Time", System.currentTimeMillis());
        rec.putInt("Heading", s.heading);
        rec.putDouble("DX", s.dirX);
        rec.putDouble("DZ", s.dirZ);
        rec.putString("Dim", level.dimension().location().toString());
        if (owner != null) {
            MissionData.addRecord(owner, rec);
            AegisNetwork.toPlayer(owner, new Packets.MissionComplete(rec, MissionData.buildSync(owner)));
        } else {
            ServerPlayer later = level.getServer().getPlayerList().getPlayer(s.owner);
            if (later != null) MissionData.addRecord(later, rec);
        }
    }

    /** Called on logout so a player's record is not lost if they leave mid-strike. */
    public static void forget(UUID player) {
        STRIKES.remove(player);
    }

    @SuppressWarnings("unused")
    private static Component msg(String s) {
        return Component.literal(s);
    }
}
