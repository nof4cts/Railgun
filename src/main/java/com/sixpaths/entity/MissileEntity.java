package com.sixpaths.entity;

import com.sixpaths.ModRegistry;
import com.sixpaths.network.FxType;
import com.sixpaths.network.PathsNetwork;
import com.sixpaths.server.Combat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Asura Path homing missile: arcs out, then curves onto its target. */
public class MissileEntity extends ThrowableProjectile {
    private int targetId = -1;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
    }

    public MissileEntity(Level level, LivingEntity owner, int targetId) {
        super(ModRegistry.MISSILE.get(), owner, level);
        this.targetId = targetId;
    }

    @Override protected void defineSynchedData() {}
    @Override protected float getGravity() { return 0f; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount > 100) {
            explode();
            return;
        }
        if (tickCount > 6) {
            Entity t = targetId >= 0 ? level().getEntity(targetId) : null;
            if (t != null && t.isAlive()) {
                Vec3 want = t.getBoundingBox().getCenter().subtract(position()).normalize().scale(1.4);
                setDeltaMovement(getDeltaMovement().lerp(want, 0.18));
            } else {
                setDeltaMovement(getDeltaMovement().scale(1.03));
            }
        }
    }

    @Override
    protected void onHit(HitResult r) {
        if (!level().isClientSide) explode();
    }

    private void explode() {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 p = position();
        if (getOwner() instanceof ServerPlayer sp) {
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(3), x -> x != sp && x.isAlive())) {
                if (e instanceof BeastEntity b && b.ownerId() == sp.getId()) continue;
                Combat.damage(sp, e, 7f, false);
                Combat.launch(e, e.position().subtract(p).normalize().scale(0.9).add(0, 0.5, 0));
            }
        }
        PathsNetwork.fx(sl, FxType.MISSILE_BOOM, getOwner() == null ? -1 : getOwner().getId(), -1, p, 0, 0);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.3f);
        discard();
    }
}
