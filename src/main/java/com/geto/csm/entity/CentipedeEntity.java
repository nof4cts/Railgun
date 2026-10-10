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
import net.minecraft.world.level.Level;

/** Cinder Centipedes — a swarm of fast, venomous crawlers. Summoned five at a time. */
public class CentipedeEntity extends CurseEntity {

    public CentipedeEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setMaxUpStep(1.0f);
        signatureCd = 160 + random.nextInt(600);
    }

    @Override
    public CurseKind kind() {
        return CurseKind.CENTIPEDE;
    }

    @Override
    protected void serverAi(ServerPlayer o) {
        LivingEntity t = findTarget(o, 30);
        if (t == null) {
            followOwner(o, 3 + (getId() % 3), 1.2, false);
            return;
        }
        walkToward(t, 1.5);
        if (distanceTo(t) < 2.2 && signatureCd <= 0) {
            signatureCd = 1200;
            action(1);
            for (LivingEntity e : enemiesAround(position(), 3.0)) {
                strike(e, 6f);
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1));
            }
            CsmNetwork.fx(this, FxType.CENTI_BITE, this, t, position().add(0, 0.4, 0), getYRot(), 1);
            sound(SoundEvents.SPIDER_AMBIENT, 1.4f, 0.6f);
            return;
        }
        if (distanceTo(t) < 1.8 && attackCd <= 0) {
            attackCd = 14 + random.nextInt(8);
            action(1);
            strike(t, 3f);
            t.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            CsmNetwork.fx(this, FxType.CENTI_BITE, this, t, t.position().add(0, 0.5, 0), getYRot(), 0);
            sound(SoundEvents.SPIDER_HURT, 0.6f, 1.6f);
        }
    }
}
