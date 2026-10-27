package com.abomination.horror.entity.ai;

import com.abomination.horror.entity.AbominationEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Phase one. It keeps its distance (24-44 blocks), half-hidden behind trees and walls, and simply watches.
 * Look straight at it and it is gone - leaving only a ripple of distortion.
 */
public class ObserveGoal extends Goal {
    private final AbominationEntity mob;
    private int repath;
    private int observedTicks;
    private int stillTicks;

    public ObserveGoal(AbominationEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.getPhase() == AbominationEntity.PHASE_OBSERVE && mob.targetPlayer() != null && !mob.isLocked();
    }

    @Override
    public void start() {
        repath = 0;
        observedTicks = 0;
        mob.setDashing(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Player t = mob.targetPlayer();
        if (t == null) return;
        mob.getLookControl().setLookAt(t, 8.0F, 8.0F);
        double d = mob.distanceTo(t);

        if (mob.isObservedBy(t)) {
            if (++observedTicks > 4) {
                mob.vanish(t);
                observedTicks = 0;
                repath = 40;
            }
        } else {
            observedTicks = 0;
        }

        if (--repath <= 0) {
            repath = 60 + mob.getRandom().nextInt(60);
            if (d < 18.0D || d > 44.0D || mob.getRandom().nextFloat() < 0.25F) {
                Vec3 spot = mob.findHidingSpot(t, 24.0D, 36.0D, false);
                if (spot != null) mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9D);
            }
        }

        if (mob.getNavigation().isDone()) {
            if (++stillTicks > 200) {
                mob.corruptAround(mob.blockPosition(), 4, 3 + mob.getRandom().nextInt(4));
                stillTicks = 0;
            }
        } else {
            stillTicks = 0;
        }
    }
}
