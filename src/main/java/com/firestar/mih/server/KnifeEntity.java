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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** One of Pucci's knives, thrown faster than the eye: no gravity, a straight line, a small stun on what it hits. */
public final class KnifeEntity extends Projectile {
    private int life;
    private boolean held;
    private boolean shownHit;
    /** Damage per knife (Knife Storm throws lighter ones). */
    public float damage = 4.5F;

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

    /** A knife hanging still in the air, pointed at {@code aim}, until {@link #launch}. */
    public static KnifeEntity held(Level level, Entity owner, Vec3 at, Vec3 aim) {
        KnifeEntity k = thrown(level, owner, at, Vec3.ZERO);
        k.held = true;
        k.face(aim);
        return k;
    }

    public void face(Vec3 aim) {
        Vec3 d = aim.subtract(position());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        setYRot((float) Math.toDegrees(Math.atan2(d.x, d.z)));
        setXRot((float) Math.toDegrees(Math.atan2(d.y, flat)));
    }

    public void launch(Vec3 velocity) {
        held = false;
        life = 0;
        setDeltaMovement(velocity);
        hasImpulse = true;
    }

    public boolean isHeld() {
        return held;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (held || getDeltaMovement().lengthSqr() < 1.0E-6) {
            if (++life > 120) {
                discard();
            }
            return;
        }
        if (++life > 50) {
            discard();
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && level().isClientSide && !shownHit) {
            shownHit = true;
            Vec3 at = hit.getLocation();
            Vec3 dir = getDeltaMovement().normalize();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.mih.client.Choreo.knifeHit(at, dir, hit.getType() == HitResult.Type.ENTITY));
        }
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
            living.hurt(damageSources().thrown(this, owner), damage * ZConfig.SERVER.damage.get().floatValue());
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 2, false, false));
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
