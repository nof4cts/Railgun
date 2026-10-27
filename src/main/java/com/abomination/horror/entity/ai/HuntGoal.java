package com.abomination.horror.entity.ai;

import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.registry.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Phase three/four. Drops onto all fours and runs you down. Feints sideways, throws its voice,
 * leaps gaps, and tears through walls when it can't path to you.
 */
public class HuntGoal extends Goal {
    private final AbominationEntity mob;
    private int repath;
    private int feintCooldown = 60;
    private int feintTicks;
    private Vec3 feintPos = Vec3.ZERO;
    private int leapCooldown = 60;
    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTicks;

    public HuntGoal(AbominationEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.getPhase() >= AbominationEntity.PHASE_HUNT && mob.targetPlayer() != null && !mob.isLocked();
    }

    @Override
    public void start() {
        repath = 0;
        stuckTicks = 0;
        lastPos = mob.position();
    }

    @Override
    public void stop() {
        mob.setDashing(false);
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Player t = mob.targetPlayer();
        if (t == null) return;
        mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        double d2 = mob.distanceToSqr(t);
        boolean nightmare = mob.getPhase() == AbominationEntity.PHASE_NIGHTMARE;
        double speed = (nightmare ? 1.75D : 1.55D) * (mob.getBossStage() == 3 ? 1.15D : 1.0D);
        mob.setDashing(d2 > 12.0D);

        if (feintTicks > 0) {
            feintTicks--;
            mob.getNavigation().moveTo(feintPos.x, feintPos.y, feintPos.z, speed);
        } else if (--repath <= 0) {
            repath = 4 + mob.getRandom().nextInt(4);
            mob.getNavigation().moveTo(t, speed);
        }

        // feints and sudden direction changes
        if (--feintCooldown <= 0) {
            feintCooldown = 80 + mob.getRandom().nextInt(80);
            if (d2 > 36.0D && d2 < 400.0D && mob.getRandom().nextBoolean()) {
                Vec3 to = t.position().subtract(mob.position()).normalize();
                Vec3 side = new Vec3(-to.z, 0, to.x).scale(mob.getRandom().nextBoolean() ? 6.0D : -6.0D);
                feintPos = mob.position().add(to.scale(3.0D)).add(side);
                feintTicks = 10 + mob.getRandom().nextInt(6);
                if (t instanceof ServerPlayer sp) {
                    // deceptive sound from the opposite side
                    AbominationEntity.playTo(sp, ModSounds.STEP.get(), t.position().subtract(side.scale(1.2D)), 1.2F, 0.7F);
                }
            }
        }

        // leap across gaps / onto ledges
        if (--leapCooldown <= 0 && mob.onGround() && d2 > 25.0D && d2 < 144.0D && mob.hasLineOfSight(t)
                && (nightmare || mob.getBossStage() == 3 || mob.getRandom().nextFloat() < 0.3F)) {
            leapCooldown = 70 + mob.getRandom().nextInt(60);
            mob.leapAt(t);
        } else if (leapCooldown <= 0) {
            leapCooldown = 20;
        }

        // stuck behind something? go through it
        if (mob.tickCount % 20 == 0) {
            if (mob.position().distanceToSqr(lastPos) < 0.5D && d2 > 6.0D) stuckTicks += 20;
            else stuckTicks = 0;
            lastPos = mob.position();
        }
        if (stuckTicks >= 40) {
            if (mob.breakTowards(t)) stuckTicks = 0;
            else stuckTicks = 20;
        }
    }
}
