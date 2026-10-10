package com.sixpaths.entity;

import com.sixpaths.network.FxType;
import com.sixpaths.network.PathsNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Great Centipede — a forty-segment armoured centipede that coils around its prey. */
public class CentipedeEntity extends BeastEntity {
    private LivingEntity coiled;
    private int coilTicks;

    public CentipedeEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setMaxUpStep(1.5f);
    }

    @Override
    protected void ai(ServerPlayer o) {
        if (coiled != null) {
            if (!coiled.isAlive() || --coilTicks <= 0) {
                if (coiled.isAlive()) launch(coiled, new Vec3(0, 1.0, 0));
                coiled = null;
                return;
            }
            coiled.setDeltaMovement(Vec3.ZERO);
            coiled.hurtMarked = true;
            getNavigation().stop();
            setDeltaMovement(Vec3.ZERO);
            if (coilTicks % 15 == 0) {
                strike(coiled, 5f);
                sound(SoundEvents.SPIDER_HURT, 1.4f, 0.5f);
                PathsNetwork.fx(this, FxType.BEAST_HIT, this, coiled, coiled.position().add(0, 1, 0), 0, 0);
            }
            return;
        }
        LivingEntity t = findTarget(o, 32);
        if (t == null) {
            followOwner(o, 5, 1.1);
            return;
        }
        getNavigation().moveTo(t, 1.4);
        faceToward(t.position());
        double d = distanceTo(t);
        if (d < 2.8 && specialCd <= 0) {
            specialCd = 200;
            coiled = t;
            coilTicks = 70;
            action(2);
            sound(SoundEvents.SPIDER_AMBIENT, 2f, 0.4f);
            return;
        }
        if (d < 2.8 && attackCd <= 0) {
            attackCd = 22;
            action(1);
            strike(t, 8f);
            t.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            PathsNetwork.fx(this, FxType.BEAST_HIT, this, t, t.position().add(0, 0.8, 0), 0, 0);
            sound(SoundEvents.SPIDER_HURT, 1f, 0.7f);
        }
    }

    public int coiledId() {
        return coiled == null ? -1 : coiled.getId();
    }
}
