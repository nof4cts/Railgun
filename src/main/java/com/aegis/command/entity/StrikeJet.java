package com.aegis.command.entity;

import com.aegis.command.mission.AircraftType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/**
 * A strike aircraft on a fixed straight flight plan. Position is a pure function of world time,
 * so the server, every client and the cinematic camera all agree on where it is, frame-perfect.
 */
public class StrikeJet extends Entity implements IEntityAdditionalSpawnData {
    private AircraftType aircraft = AircraftType.KESTREL;
    private Vec3 start = Vec3.ZERO;
    private double dirX = 0, dirZ = 1;
    private long spawnTime;
    private int lifeTicks = 200;

    public StrikeJet(EntityType<? extends StrikeJet> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setFlightPlan(AircraftType aircraft, Vec3 start, double dirX, double dirZ, long spawnTime, int lifeTicks) {
        this.aircraft = aircraft;
        this.start = start;
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.spawnTime = spawnTime;
        this.lifeTicks = lifeTicks;
        followPlan();
        setOldPosAndRot();
    }

    public AircraftType aircraft() { return aircraft; }
    public double dirX() { return dirX; }
    public double dirZ() { return dirZ; }
    public long spawnTime() { return spawnTime; }
    public int lifeTicks() { return lifeTicks; }

    public Vec3 pathPos(double t) {
        return new Vec3(start.x + dirX * aircraft.speed * t, start.y, start.z + dirZ * aircraft.speed * t);
    }

    public double flightTime(float partialTick) {
        return level().getGameTime() - spawnTime + partialTick;
    }

    /** Snap to the planned position for the current world time. */
    public void followPlan() {
        double t = level().getGameTime() - spawnTime;
        Vec3 p = pathPos(t);
        float yaw = (float) (Mth.atan2(dirZ, dirX) * Mth.RAD_TO_DEG) - 90f;
        setYRot(yaw);
        setXRot(0);
        setPos(p.x, p.y, p.z);
    }

    @Override
    public void tick() {
        followPlan();
        double t = level().getGameTime() - spawnTime;
        if (!level().isClientSide) {
            if (t > lifeTicks + 40) discard();
            return;
        }
        // contrails from the wingtips and engine heat haze
        if (tickCount % 1 == 0 && t > 3) {
            double span = 2.1 * aircraft.scale;
            double px = -dirZ, pz = dirX;
            double bx = getX() - dirX * 3.5 * aircraft.scale / 4;
            double bz = getZ() - dirZ * 3.5 * aircraft.scale / 4;
            level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, bx + px * span, getY() + 0.3, bz + pz * span, 0, 0, 0);
            level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, bx - px * span, getY() + 0.3, bz - pz * span, 0, 0, 0);
            double ex = getX() - dirX * 6.2 * aircraft.scale / 4, ez = getZ() - dirZ * 6.2 * aircraft.scale / 4;
            level().addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, ex, getY() + 0.4, ez,
                    -dirX * 0.2, 0.01, -dirZ * 0.2);
            if (random.nextInt(2) == 0) {
                level().addAlwaysVisibleParticle(ParticleTypes.FLAME, true, ex, getY() + 0.4, ez,
                        -dirX * 0.3, 0, -dirZ * 0.3);
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        // flight path is deterministic; ignore server interpolation updates
    }

    @Override
    public void lerpMotion(double x, double y, double z) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 512 * 512;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(12);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeVarInt(aircraft.ordinal());
        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);
        buf.writeDouble(dirX);
        buf.writeDouble(dirZ);
        buf.writeLong(spawnTime);
        buf.writeVarInt(lifeTicks);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buf) {
        aircraft = AircraftType.byIndex(buf.readVarInt());
        start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        dirX = buf.readDouble();
        dirZ = buf.readDouble();
        spawnTime = buf.readLong();
        lifeTicks = buf.readVarInt();
        followPlan();
        setOldPosAndRot();
    }
}
