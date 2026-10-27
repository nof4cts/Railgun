package com.abomination.horror.finisher;

import com.abomination.horror.config.AbomConfig;
import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.network.FinisherPacket;
import com.abomination.horror.network.ModNetwork;
import com.abomination.horror.registry.ModDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Server authority for the spine-rip finisher.
 * <p>
 * When The Abomination lands a killing blow on a player, {@link FinisherEvents} cancels the death and calls
 * {@link #tryStart}. The victim is held at 1 HP, invulnerable to everything else, and pinned in front of the
 * creature while every tracking client plays the cinematic. At {@link #KILL_TICK} the victim is killed with the
 * custom {@code abomination:spine_rip} damage type, so the vanilla death screen, drops and respawn all run normally.
 * <p>
 * Timeline in ticks (shared with the client):
 * <pre>
 *  0   grab - victim yanked into its shadow
 *  4   close-up on the face, jaw unhinges
 *  22  sudden silence
 *  26  IMPACT: claws close around the skull, camera freezes
 *  30  RIP: head torn upward, spine drawn out vertebra by vertebra, blood fountains from the neck
 *  56  head + dangling spine held aloft
 *  88  cut to black, final sound
 *  96  victim dies -> death screen
 *  150 creature releases its trophy and resumes hunting
 * </pre>
 */
public final class FinisherManager {
    public static final int IMPACT_TICK = 26;
    public static final int RIP_START = 30;
    public static final int RIP_END = 56;
    public static final int BLACKOUT_TICK = 88;
    public static final int KILL_TICK = 96;
    public static final int CREATURE_RELEASE_TICK = 150;
    private static final int PULL_TICKS = 6;

    private static final Map<UUID, Instance> ACTIVE = new HashMap<>();

    private static final class Instance {
        final ServerPlayer victim;
        final AbominationEntity creature;
        final long start;
        final float yaw;
        final Vec3 startPos;

        Instance(ServerPlayer victim, AbominationEntity creature, long start, float yaw) {
            this.victim = victim;
            this.creature = creature;
            this.start = start;
            this.yaw = yaw;
            this.startPos = victim.position();
        }
    }

    private FinisherManager() {}

    public static boolean isInFinisher(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static boolean tryStart(AbominationEntity creature, ServerPlayer victim) {
        if (!AbomConfig.COMMON.finisherEnabled.get()) return false;
        if (!creature.isAlive() || creature.inFinisher() || isInFinisher(victim)) return false;
        if (creature.level() != victim.level()) return false;

        float yaw = (float) (Mth.atan2(victim.getZ() - creature.getZ(), victim.getX() - creature.getX()) * (180.0F / (float) Math.PI)) - 90.0F;
        Instance inst = new Instance(victim, creature, victim.level().getGameTime(), yaw);
        ACTIVE.put(victim.getUUID(), inst);

        victim.setHealth(1.0F);
        victim.stopUsingItem();
        victim.closeContainer();
        victim.setDeltaMovement(Vec3.ZERO);
        creature.startFinisher(victim, yaw);

        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> victim),
                new FinisherPacket(FinisherPacket.START, creature.getId(), victim.getId(), inst.start));
        return true;
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        for (Instance inst : new ArrayList<>(ACTIVE.values())) {
            ServerPlayer v = inst.victim;
            AbominationEntity a = inst.creature;
            long t = v.level().getGameTime() - inst.start;

            if (v.isRemoved() || v.hasDisconnected() || !v.isAlive()) {
                ACTIVE.remove(v.getUUID());
                a.endFinisher();
                continue;
            }
            boolean creatureGone = a.isRemoved() || !a.isAlive() || a.level() != v.level();
            if (creatureGone && t < RIP_START) {
                abort(inst);  // killed before it could finish: the victim escapes
                continue;
            }
            if (t >= KILL_TICK) {
                finish(inst, creatureGone);
                continue;
            }
            if (!creatureGone) hold(inst, t);
        }
    }

    private static void hold(Instance inst, long t) {
        ServerPlayer v = inst.victim;
        AbominationEntity a = inst.creature;
        Vec3 fwd = AbominationEntity.forward(inst.yaw);
        Vec3 hold = a.position().add(fwd.scale(1.35D));
        if (!v.level().noCollision(v, v.getBoundingBox().move(hold.subtract(v.position())))) {
            hold = new Vec3(inst.startPos.x, v.getY(), inst.startPos.z);
        }
        Vec3 target = t < PULL_TICKS ? inst.startPos.lerp(hold, (t + 1) / (double) PULL_TICKS) : hold;
        if (t < PULL_TICKS || t % 3 == 0) {
            v.connection.teleport(target.x, target.y, target.z, 0.0F, 0.0F, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
        }
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0.0F;
    }

    private static void finish(Instance inst, boolean creatureGone) {
        ServerPlayer v = inst.victim;
        ACTIVE.remove(v.getUUID());
        v.invulnerableTime = 0;
        v.hurt(ModDamage.spineRip(v.level(), inst.creature), Float.MAX_VALUE);
    }

    private static void abort(Instance inst) {
        ServerPlayer v = inst.victim;
        ACTIVE.remove(v.getUUID());
        inst.creature.endFinisher();
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
        v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> v),
                new FinisherPacket(FinisherPacket.ABORT, inst.creature.getId(), v.getId(), inst.start));
    }

    public static void onLogout(ServerPlayer player) {
        Instance inst = ACTIVE.remove(player.getUUID());
        if (inst != null) inst.creature.endFinisher();
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
