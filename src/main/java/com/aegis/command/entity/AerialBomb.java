package com.aegis.command.entity;

import com.aegis.command.AegisConfig;
import com.aegis.command.mission.StrikeManager;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/** Fictional ordnance dropped by a strike aircraft. Visual-first: blocks are untouched unless the config says otherwise. */
public class AerialBomb extends ThrowableProjectile {
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(AerialBomb.class, EntityDataSerializers.FLOAT);
    private UUID ownerId;
    private boolean detonated;

    public AerialBomb(EntityType<? extends AerialBomb> type, Level level) {
        super(type, level);
    }

    public void setOwnerId(UUID id) { this.ownerId = id; }
    public void setPower(float p) { entityData.set(POWER, p); }
    public float power() { return entityData.get(POWER); }

    @Override
    protected void defineSynchedData() {
        entityData.define(POWER, 4f);
    }

    @Override
    protected float getGravity() {
        return StrikeManager.BOMB_GRAVITY;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return !(e instanceof StrikeJet) && !(e instanceof AerialBomb) && super.canHitEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, xo, yo + 0.2, zo, 0, 0.02, 0);
            if (tickCount % 2 == 0) {
                level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, xo, yo + 0.3, zo, 0, 0, 0);
            }
        } else if (tickCount > 600 || getY() < level().getMinBuildHeight() - 8) {
            if (ownerId != null && !detonated) StrikeManager.onBombDetonated(ownerId, 0);
            detonated = true;
            discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide && !detonated) {
            detonated = true;
            detonate();
            discard();
        }
    }

    private void detonate() {
        float power = power();
        double x = getX(), y = getY(), z = getZ();
        AABB area = new AABB(x - power * 2, y - power * 2, z - power * 2, x + power * 2, y + power * 2, z + power * 2);
        List<LivingEntity> before = level().getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);

        Explosion.BlockInteraction interaction = AegisConfig.COMMON.destroyBlocks.get()
                ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.KEEP;
        Explosion explosion = new Explosion(level(), this, level().damageSources().explosion(this, getOwner()), null,
                x, y, z, power, false, interaction);
        if (!ForgeEventFactory.onExplosionStart(level(), explosion)) {
            explosion.explode();
            explosion.finalizeExplosion(false);
        }

        int kills = 0;
        for (LivingEntity e : before) if (!e.isAlive()) kills++;
        if (ownerId != null) StrikeManager.onBombDetonated(ownerId, kills);

        AegisNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, 320, level().dimension())),
                new Packets.ImpactFx(x, y, z, power, (byte) 0));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 320 * 320;
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Power", power());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPower(tag.getFloat("Power"));
    }
}
