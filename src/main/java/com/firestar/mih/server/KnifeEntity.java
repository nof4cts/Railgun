package com.firestar.mih.server;

import com.firestar.mih.config.ZConfig;
import com.firestar.mih.registry.ModEntities;
import com.firestar.mih.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One of Pucci's knives, thrown faster than the eye: no gravity, a straight line, a small stun on what it hits. */
public final class KnifeEntity extends Projectile {
    private int life;

    public KnifeEntity(EntityType<? extends KnifeEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static KnifeEntity thrown(Level level, Entity owner, Vec3 from, Vec3 velocity) {
        KnifeEntity k = new KnifeEntity(ModEntities.KNIFE.get(), level);
        k.setOwner(owner);
        k.setPos(from.x, from.y, from.z);
        k.setDeltaMovement(velocity);
        double flat = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        k.setYRot((float) Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
        k.setXRot((float) Math.toDegrees(Math.atan2(velocity.y, flat)));
        k.yRotO = k.getYRot();
        k.xRotO = k.getXRot();
        return k;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (++life > 50) {
            discard();
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !level().isClientSide) {
            onHit(hit);
            if (isRemoved()) {
                return;
            }
        }
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        updateRotation();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwner();
        if (target instanceof LivingEntity living) {
            living.invulnerableTime = 0;
            living.hurt(damageSources().thrown(this, owner), 4.5F * ZConfig.SERVER.damage.get().floatValue());
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 2, false, false));
        }
        level().playSound(null, getX(), getY(), getZ(), ModSounds.KNIFE_HIT.get(), SoundSource.PLAYERS, 0.9F, 1.0F + random.nextFloat() * 0.2F);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        Vec3 p = result.getLocation();
        level().playSound(null, p.x, p.y, p.z, ModSounds.KNIFE_HIT.get(), SoundSource.PLAYERS, 0.6F, 1.4F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 6, 0.05, 0.05, 0.05, 0.25);
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
