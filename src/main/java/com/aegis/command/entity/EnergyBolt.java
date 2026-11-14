package com.aegis.command.entity;

import com.aegis.command.AegisConfig;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import com.aegis.command.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/** Plasma bolt fired by SPECTRE-7 (kind 0) or the charged TITAN-X arc shell (kind 1). */
public class EnergyBolt extends ThrowableProjectile {
    public static final byte SPECTRE = 0, TITAN = 1;
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(EnergyBolt.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(EnergyBolt.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(EnergyBolt.class, EntityDataSerializers.FLOAT);
    private float damage = 4f;

    public EnergyBolt(EntityType<? extends EnergyBolt> type, Level level) {
        super(type, level);
    }

    public EnergyBolt(Level level, LivingEntity owner, byte kind, int color, float damage, float size) {
        super(ModEntities.ENERGY_BOLT.get(), owner, level);
        entityData.set(KIND, kind);
        entityData.set(COLOR, color);
        entityData.set(SIZE, size);
        this.damage = damage;
    }

    public int color() { return entityData.get(COLOR); }
    public byte kind() { return entityData.get(KIND); }
    public float size() { return entityData.get(SIZE); }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLOR, 0xB04BFF);
        entityData.define(KIND, SPECTRE);
        entityData.define(SIZE, 1f);
    }

    @Override
    protected float getGravity() {
        return 0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            int c = color();
            Vector3f col = new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
            Vec3 v = getDeltaMovement();
            for (int i = 0; i < (kind() == TITAN ? 4 : 2); i++) {
                double f = i / 4.0;
                level().addParticle(new DustParticleOptions(col, kind() == TITAN ? 1.6f : 0.8f),
                        getX() - v.x * f, getY() - v.y * f, getZ() - v.z * f, 0, 0, 0);
            }
            if (kind() == TITAN && random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(),
                        random.nextGaussian() * 0.1, random.nextGaussian() * 0.1, random.nextGaussian() * 0.1);
            }
        } else if (tickCount > (kind() == TITAN ? 80 : 40)) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return !(e instanceof EnergyBolt) && super.canHitEntity(e);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        Entity target = result.getEntity();
        target.invulnerableTime = 0;
        target.hurt(damageSources().thrown(this, getOwner()), damage);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide) return;
        Vec3 p = result.getLocation();
        if (kind() == TITAN) {
            Explosion.BlockInteraction bi = AegisConfig.COMMON.weaponBlockDamage.get()
                    ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.KEEP;
            Explosion ex = new Explosion(level(), this, damageSources().explosion(this, getOwner()), null,
                    p.x, p.y, p.z, 2.6f * size(), false, bi);
            if (!ForgeEventFactory.onExplosionStart(level(), ex)) {
                ex.explode();
                ex.finalizeExplosion(false);
            }
            AegisNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(p.x, p.y, p.z, 128, level().dimension())),
                    new Packets.ImpactFx(p.x, p.y, p.z, 2.6f * size(), (byte) 1));
        } else if (level() instanceof ServerLevel sl) {
            int c = color();
            Vector3f col = new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
            sl.sendParticles(new DustParticleOptions(col, 1.2f), p.x, p.y, p.z, 8, 0.15, 0.15, 0.15, 0.05);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.2);
            level().playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.5f, 1.8f);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
    }
}
