package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Maw Burrower — erupts from the ground under its prey and swallows it whole,
 * grinding it for a few seconds before spitting it out.
 */
public class WormEntity extends CurseEntity {
    private LivingEntity swallowed;
    private int swallowTicks;
    private int emerge = 24;

    public WormEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        signatureCd = 0;
    }

    @Override
    public CurseKind kind() {
        return CurseKind.WORM;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void serverAi(ServerPlayer o) {
        setDeltaMovement(Vec3.ZERO);
        if (emerge > 0) {
            if (emerge == 24) {
                CsmNetwork.fx(this, FxType.WORM_EMERGE, this, null, position(), 0, 0);
                sound(SoundEvents.RAVAGER_ROAR, 1.6f, 0.5f);
            }
            emerge--;
            if (emerge == 8) {
                for (LivingEntity e : enemiesAround(position().add(0, 1, 0), 3.2)) {
                    if (swallowed == null && signatureCd <= 0) swallow(e);
                    else launch(e, e.position().subtract(position()).normalize().scale(1.2).add(0, 1.0, 0));
                }
            }
            return;
        }
        if (swallowed != null) {
            holdSwallowed();
            return;
        }
        LivingEntity t = findTarget(o, 30);
        if (t != null) faceToward(t.position());
        if (t != null && distanceTo(t) < 6.5 && attackCd <= 0) {
            attackCd = 30;
            action(1);
            sound(SoundEvents.EVOKER_FANGS_ATTACK, 1.5f, 0.5f);
            if (distanceTo(t) < 4.0 && signatureCd <= 0) swallow(t);
            else {
                strike(t, 8f);
                CsmNetwork.fx(this, FxType.BITE, this, t, t.position().add(0, 1, 0), getYRot(), 0);
            }
        }
    }

    private void swallow(LivingEntity t) {
        signatureCd = 1200;
        swallowed = t;
        swallowTicks = 70;
        action(2);
        CsmNetwork.fx(this, FxType.WORM_SWALLOW, this, t, position().add(0, 4.5, 0), 0, 0);
        sound(SoundEvents.GENERIC_EAT, 2f, 0.4f);
        t.setInvisible(true);
        if (t instanceof Player p) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
    }

    private void holdSwallowed() {
        LivingEntity t = swallowed;
        if (!t.isAlive()) {
            swallowed = null;
            return;
        }
        t.teleportTo(getX(), getY() + 3.2, getZ());
        t.setDeltaMovement(Vec3.ZERO);
        t.hurtMarked = true;
        if (swallowTicks % 20 == 0) {
            strike(t, 4f);
            sound(SoundEvents.SLIME_SQUISH, 1.5f, 0.5f);
        }
        if (--swallowTicks <= 0) {
            t.setInvisible(false);
            swallowed = null;
            action(3);
            launch(t, new Vec3((random.nextDouble() - 0.5) * 1.5, 1.4, (random.nextDouble() - 0.5) * 1.5));
            CsmNetwork.fx(this, FxType.WORM_SPIT, this, t, position().add(0, 4.5, 0), 0, 0);
            sound(SoundEvents.LLAMA_SPIT, 2f, 0.4f);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (swallowed != null) {
            swallowed.setInvisible(false);
            swallowed = null;
        }
        super.remove(reason);
    }
}
