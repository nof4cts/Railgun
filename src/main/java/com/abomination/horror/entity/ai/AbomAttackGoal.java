package com.abomination.horror.entity.ai;

import com.abomination.horror.entity.AbominationEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Anticipation -> strike frame -> recovery. Swipe or (later stages) a two-handed ground slam. */
public class AbomAttackGoal extends Goal {
    private final AbominationEntity mob;
    private int ticks = -1;

    public AbomAttackGoal(AbominationEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() && !mob.isLocked() && mob.attackCooldown <= 0
                && (mob.getPhase() >= AbominationEntity.PHASE_HUNT || mob.isAwakened())
                && mob.distanceToSqr(t) <= 3.8D * 3.8D;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        return ticks >= 0 && ticks < (mob.currentAttack == AbominationEntity.ATTACK_SLAM ? 28 : 18) && t != null && t.isAlive() && !mob.isLocked();
    }

    @Override
    public void start() {
        ticks = 0;
        mob.getNavigation().stop();
        mob.setDashing(false);
        mob.chooseAttack();
    }

    @Override
    public void stop() {
        ticks = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) return;
        ticks++;
        mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (ticks < mob.strikeTick()) {
            // lunge forward during anticipation
            mob.getNavigation().moveTo(t, 0.6D);
        } else {
            mob.getNavigation().stop();
        }
        if (ticks == mob.strikeTick()) mob.performStrike(t);
    }
}
