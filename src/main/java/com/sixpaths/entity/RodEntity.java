package com.sixpaths.entity;

import com.sixpaths.ModRegistry;
import com.sixpaths.network.FxType;
import com.sixpaths.network.PathsNetwork;
import com.sixpaths.server.Combat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A black chakra receiver. Pins and disrupts whatever it pierces. */
public class RodEntity extends ThrowableProjectile {
    private int stuck = -1;

    public RodEntity(EntityType<? extends RodEntity> type, Level level) {
        super(type, level);
    }

    public RodEntity(Level level, LivingEntity owner) {
        super(ModRegistry.ROD.get(), owner, level);
    }

    @Override protected void defineSynchedData() {}
    @Override protected float getGravity() { return stuck >= 0 ? 0f : 0.006f; }

    @Override
    public void tick() {
        if (stuck >= 0) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && ++stuck > 120) discard();
            return;
        }
        super.tick();
        if (!level().isClientSide && tickCount > 80) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        if (level().isClientSide) return;
        if (r.getEntity() instanceof LivingEntity t && getOwner() instanceof ServerPlayer sp && t != sp) {
            Combat.damage(sp, t, 6f, false);
            Combat.stun(t, 60);
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            PathsNetwork.fx((ServerLevel) level(), FxType.ROD_HIT, sp.getId(), t.getId(), position(), 1, 0);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.2f, 0.6f);
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        super.onHitBlock(r);
        if (level().isClientSide || stuck >= 0) return;
        stuck = 0;
        setPos(r.getLocation());
        PathsNetwork.fx((ServerLevel) level(), FxType.ROD_HIT, -1, -1, r.getLocation(), 0, 0);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1f, 0.6f);
    }
}
