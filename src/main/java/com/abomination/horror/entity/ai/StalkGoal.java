package com.abomination.horror.entity.ai;

import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.network.ModNetwork;
import com.abomination.horror.registry.ModSounds;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Phase two. It follows at 9-16 blocks, freezing completely the instant you look at it.
 * Footsteps come from the wrong direction, ambient sound drops out, something whispers by your ear,
 * and it occasionally appears at the edge of your vision.
 */
public class StalkGoal extends Goal {
    private final AbominationEntity mob;
    private int fakeStep;
    private int silence;
    private int peripheral;
    private int repath;

    public StalkGoal(AbominationEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.getPhase() == AbominationEntity.PHASE_STALK && mob.targetPlayer() != null && !mob.isLocked();
    }

    @Override
    public void start() {
        fakeStep = 40;
        silence = 200;
        peripheral = 300 + mob.getRandom().nextInt(200);
        mob.setDashing(false);
    }

    @Override
    public void stop() {
        mob.setFrozenByGaze(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Player t = mob.targetPlayer();
        if (t == null) return;
        mob.getLookControl().setLookAt(t, 10.0F, 10.0F);

        boolean seen = mob.isObservedBy(t);
        mob.setFrozenByGaze(seen);
        if (seen) {
            mob.getNavigation().stop();
            mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, 0.0D);
            return;
        }

        double d = mob.distanceTo(t);
        if (--repath <= 0) {
            repath = 10;
            if (d > 16.0D) mob.getNavigation().moveTo(t, 0.7D);
            else if (d > 10.0D) mob.getNavigation().moveTo(t, 0.4D);
            else mob.getNavigation().stop();
        }

        if (!(t instanceof ServerPlayer sp)) return;

        // false footsteps behind the player, never where it actually is
        if (--fakeStep <= 0) {
            fakeStep = 50 + mob.getRandom().nextInt(90);
            Vec3 behind = sp.getViewVector(1.0F).multiply(-1, 0, -1).normalize().scale(4.0D + mob.getRandom().nextDouble() * 5.0D);
            Vec3 pos = sp.position().add(behind).add((mob.getRandom().nextDouble() - 0.5D) * 4.0D, 0.0D, (mob.getRandom().nextDouble() - 0.5D) * 4.0D);
            AbominationEntity.playTo(sp, mob.getRandom().nextInt(4) == 0 ? ModSounds.SCRAPE.get() : ModSounds.STEP.get(), pos, 0.7F, 0.8F + mob.getRandom().nextFloat() * 0.2F);
        }

        // the world goes quiet, then something whispers right beside you
        if (--silence <= 0) {
            silence = 300 + mob.getRandom().nextInt(300);
            sp.connection.send(new ClientboundStopSoundPacket(null, SoundSource.AMBIENT));
            sp.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
            Vec3 ear = sp.getEyePosition().add(sp.getViewVector(1.0F).yRot(mob.getRandom().nextBoolean() ? 1.5F : -1.5F).scale(1.5D));
            AbominationEntity.playTo(sp, ModSounds.WHISPER.get(), ear, 0.5F, 0.7F + mob.getRandom().nextFloat() * 0.4F);
        }

        // brief appearances in peripheral vision
        if (--peripheral <= 0) {
            peripheral = 400 + mob.getRandom().nextInt(400);
            Vec3 spot = mob.peripheralSpot(sp);
            if (spot != null && mob.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1.8D, mob.getZ(), 20, 0.4D, 1.0D, 0.4D, 0.03D);
                mob.teleportTo(spot.x, spot.y, spot.z);
                ModNetwork.fx(sp, 0.0F, 0.25F, 0.0F, 0);
            }
        }

        if (mob.getNavigation().isDone() && mob.getRandom().nextInt(400) == 0) {
            mob.corruptAround(mob.blockPosition(), 3, 2 + mob.getRandom().nextInt(3));
        }
    }
}
