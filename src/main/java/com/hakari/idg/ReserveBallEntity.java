package com.hakari.idg;

import com.hakari.idg.network.FxType;
import com.hakari.idg.network.HakariNetwork;
import com.hakari.idg.server.Combat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A pachinko reserve ball flicked by the thumb. Rendered with custom geometry only. */
public class ReserveBallEntity extends ThrowableProjectile {

    public ReserveBallEntity(EntityType<? extends ReserveBallEntity> type, Level level) {
        super(type, level);
    }

    public ReserveBallEntity(Level level, LivingEntity owner) {
        super(ModRegistry.RESERVE_BALL_ENTITY.get(), owner, level);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected float getGravity() {
        return 0.004f;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 60) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        Entity owner = getOwner();
        if (result.getEntity() instanceof LivingEntity target && owner instanceof ServerPlayer sp && target != sp) {
            double dist = sp.distanceTo(target);
            boolean close = dist < 4.5;
            Combat.damage(sp, target, 5f, false);
            if (close) {
                Vec3 dir = getDeltaMovement().normalize();
                Combat.launch(target, dir.scale(1.6).add(0, 0.6, 0));
            } else {
                Combat.stun(target, 20);
            }
            HakariNetwork.fx((ServerLevel) level(), FxType.BALL_IMPACT, sp.getId(), target.getId(), position(), close ? 1 : 0, 0);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 2f);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 1.8f);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide) {
            Entity owner = getOwner();
            HakariNetwork.fx((ServerLevel) level(), FxType.BALL_IMPACT, owner == null ? -1 : owner.getId(), -1, position(), 0, 0);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.6f, 1.7f);
            discard();
        }
    }
}
