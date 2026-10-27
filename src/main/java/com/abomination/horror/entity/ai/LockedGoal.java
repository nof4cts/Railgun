package com.abomination.horror.entity.ai;

import com.abomination.horror.entity.AbominationEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Roots the creature during cinematic animations (detection freeze, roar, emergence, finisher). */
public class LockedGoal extends Goal {
    private final AbominationEntity mob;

    public LockedGoal(AbominationEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return mob.isLocked();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        mob.getNavigation().stop();
        mob.setDashing(false);
        if (mob.getAnim() == AbominationEntity.ANIM_DETECT && mob.getTarget() != null) {
            // the body stays frozen; only the head slowly turns (handled by the model), but we track the target
            mob.getLookControl().setLookAt(mob.getTarget(), 4.0F, 4.0F);
        } else if (mob.getAnim() == AbominationEntity.ANIM_ROAR && mob.getTarget() != null) {
            mob.getLookControl().setLookAt(mob.getTarget(), 30.0F, 30.0F);
        }
    }
}
