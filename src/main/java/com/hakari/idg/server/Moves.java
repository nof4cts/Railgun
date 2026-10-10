package com.hakari.idg.server;

import com.hakari.idg.ModRegistry;
import com.hakari.idg.ReserveBallEntity;
import com.hakari.idg.network.CutType;
import com.hakari.idg.network.FxType;
import com.hakari.idg.network.HakariNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Every technique. Each method performs the move and returns the cooldown in ticks.
 * Timings are matched to the client choreography (doors close at 9 ticks, punch lands at 14, etc).
 */
public final class Moves {

    private static ServerLevel lvl(ServerPlayer sp) {
        return sp.serverLevel();
    }

    // ───────────────────────────── BASE KIT ─────────────────────────────

    public static int reserveBall(ServerPlayer sp, HakariState st) {
        ReserveBallEntity ball = new ReserveBallEntity(sp.level(), sp);
        Vec3 look = sp.getLookAngle();
        ball.setPos(sp.getX() + look.x * 0.6, sp.getEyeY() - 0.25 + look.y * 0.6, sp.getZ() + look.z * 0.6);
        ball.shoot(look.x, look.y, look.z, 2.4f, 0f);
        sp.level().addFreshEntity(ball);
        HakariNetwork.fx(sp, FxType.FLICK, sp, null, ball.position(), 0, 0);
        Combat.sound(sp, SoundEvents.ARROW_SHOOT, 1f, 1.9f);
        Combat.sound(sp, SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.5f);
        HakariLogic.onVisual(sp, st);
        return 30;
    }

    public static int shutterDoors(ServerPlayer sp, HakariState st) {
        LivingEntity t = Combat.nearest(sp, 14, 0.75);
        Vec3 pos = t != null ? t.position() : sp.position().add(Combat.flatLook(sp).scale(5));
        HakariNetwork.fx(sp, FxType.DOORS_SLAM, sp, t, pos, sp.getYRot(), 0);
        Combat.sound(lvl(sp), pos, SoundEvents.IRON_DOOR_OPEN, 1.2f, 0.5f);
        if (t != null) Combat.stun(t, 10);
        Scheduler.after(9, () -> {
            if (!sp.isAlive()) return;
            Combat.sound(lvl(sp), pos, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
            Combat.sound(lvl(sp), pos, SoundEvents.IRON_DOOR_CLOSE, 1.5f, 0.7f);
            for (LivingEntity e : Combat.sphere(lvl(sp), pos.add(0, 1, 0), 2.8, sp)) {
                Combat.damage(sp, e, 8f, false);
                Combat.stun(e, 35);
                HakariNetwork.fx(sp, FxType.SMALL_HIT, sp, e, e.position().add(0, 1, 0), 0, 0);
            }
        });
        HakariLogic.onVisual(sp, st);
        return 100;
    }

    public static int roughEnergy(ServerPlayer sp, HakariState st) {
        st.pose = 2;
        st.windupTicks = 16;
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 3, false, false));
        HakariNetwork.fx(sp, FxType.ROUGH_CHARGE, sp, null, sp.position(), 0, 0);
        HakariNetwork.cutscene(sp, CutType.MICRO_ROUGH, sp.getId());
        Combat.sound(sp, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 1.6f);
        Combat.sound(sp, SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.6f);
        Scheduler.after(14, () -> {
            if (!sp.isAlive()) return;
            Vec3 look = Combat.flatLook(sp);
            List<LivingEntity> hits = Combat.cone(sp, 5.5, 0.55);
            Vec3 fist = sp.getEyePosition().add(sp.getLookAngle().scale(1.2));
            HakariNetwork.fx(sp, FxType.ROUGH_HIT, sp, hits.isEmpty() ? null : hits.get(0), fist, sp.getYRot(), sp.getXRot());
            Combat.sound(sp, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.7f);
            Combat.sound(sp, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.4f);
            for (LivingEntity e : hits) {
                Combat.damage(sp, e, 14f, true);
                Combat.stun(e, 3);
                // Hit-stop: freeze for 3 ticks, THEN send them flying.
                Scheduler.after(3, () -> Combat.launch(e, look.scale(2.6).add(0, 0.55, 0)));
            }
        });
        return 160;
    }

    public static int feverBreaker(ServerPlayer sp, HakariState st) {
        Vec3 look = Combat.flatLook(sp);
        Combat.launch(sp, look.scale(1.5).add(0, 0.15, 0));
        HakariNetwork.fx(sp, FxType.FEVER_KICK1, sp, null, sp.position(), sp.getYRot(), 0);
        Combat.sound(sp, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        final boolean[] landed = {false};
        for (int i = 1; i <= 7; i++) {
            Scheduler.after(i, () -> {
                if (landed[0] || !sp.isAlive()) return;
                List<LivingEntity> h = Combat.sphere(lvl(sp), sp.position().add(look.scale(0.8)).add(0, 1, 0), 1.9, sp);
                if (h.isEmpty()) return;
                landed[0] = true;
                LivingEntity t = h.get(0);
                sp.setDeltaMovement(Vec3.ZERO);
                sp.hurtMarked = true;
                Combat.damage(sp, t, 6f, false);
                Combat.stun(t, 12);
                HakariNetwork.fx(sp, FxType.DOORS_SLAM, sp, t, t.position(), sp.getYRot(), 1);
                Combat.sound(t, SoundEvents.IRON_DOOR_CLOSE, 1.2f, 1.2f);
                HakariLogic.onVisual(sp, HakariState.get(sp));
                Scheduler.after(8, () -> {
                    if (!t.isAlive()) return;
                    Combat.damage(sp, t, 8f, false);
                    Combat.launch(t, look.scale(1.9).add(0, 0.9, 0));
                    HakariNetwork.fx(sp, FxType.FEVER_KICK2, sp, t, t.position().add(0, 1, 0), sp.getYRot(), 0);
                    Combat.sound(t, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.3f);
                    Combat.sound(t, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
                });
            });
        }
        return 140;
    }

    public static int counter(ServerPlayer sp, HakariState st) {
        st.counterTicks = 14;
        st.pose = 3;
        HakariNetwork.fx(sp, FxType.COUNTER_READY, sp, null, sp.position(), sp.getYRot(), 0);
        Combat.sound(sp, SoundEvents.IRON_TRAPDOOR_OPEN, 1f, 0.6f);
        return 120;
    }

    public static void counterTrigger(ServerPlayer sp, HakariState st, LivingEntity attacker) {
        st.counterTicks = 0;
        st.pose = 0;
        Vec3 pos = attacker.position();
        HakariNetwork.fx(sp, FxType.COUNTER_SLAM, sp, attacker, pos, sp.getYRot(), 0);
        Combat.sound(attacker, SoundEvents.ANVIL_LAND, 1f, 0.7f);
        Combat.sound(attacker, SoundEvents.IRON_DOOR_CLOSE, 1.5f, 0.6f);
        Combat.stun(attacker, 6);
        Scheduler.after(5, () -> {
            Combat.damage(sp, attacker, 9f, true);
            Combat.stun(attacker, 40);
        });
        HakariLogic.onVisual(sp, st);
        HakariLogic.sync(sp, st);
    }

    // ───────────────────────────── JACKPOT KIT ─────────────────────────────

    public static int luckyVolley(ServerPlayer sp, HakariState st) {
        LivingEntity t = Combat.nearest(sp, 4.5, 0.5);
        st.pose = 2;
        st.windupTicks = 28;
        HakariNetwork.fx(sp, FxType.VOLLEY_FIST, sp, t, sp.position(), sp.getYRot(), 28);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 28, 2, false, false));
        for (int i = 0; i < 12; i++) {
            final int k = i;
            Scheduler.after(2 + i * 2, () -> {
                if (!sp.isAlive()) return;
                Combat.sound(sp, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 1.2f + k * 0.04f);
                if (t != null && t.isAlive()) {
                    Vec3 hold = sp.position().add(Combat.flatLook(sp).scale(1.9));
                    t.teleportTo(hold.x, t.getY(), hold.z);
                    Combat.damage(sp, t, 1.5f, true);
                    Combat.stun(t, 4);
                }
            });
        }
        Scheduler.after(28, () -> {
            if (!sp.isAlive()) return;
            Vec3 look = Combat.flatLook(sp);
            Vec3 at = t != null ? t.position().add(0, 1, 0) : sp.getEyePosition().add(look.scale(2));
            HakariNetwork.fx(sp, FxType.VOLLEY_FINISH, sp, t, at, sp.getYRot(), 0);
            Combat.sound(sp, SoundEvents.GENERIC_EXPLODE, 1f, 1.3f);
            if (t != null && t.isAlive()) {
                Combat.damage(sp, t, 7f, true);
                Combat.launch(t, look.scale(2.6).add(0, 0.7, 0));
            }
        });
        return 50;
    }

    public static int luckyRushdown(ServerPlayer sp, HakariState st) {
        Vec3 look = Combat.flatLook(sp);
        HakariNetwork.fx(sp, FxType.RUSH_DASH, sp, null, sp.position(), sp.getYRot(), 0);
        HakariNetwork.cutscene(sp, CutType.MICRO_RUSH, sp.getId());
        Combat.sound(sp, SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.6f);
        final LivingEntity[] grabbed = {null};
        for (int i = 0; i < 9; i++) {
            Scheduler.after(i, () -> {
                if (grabbed[0] != null || !sp.isAlive()) return;
                sp.setDeltaMovement(look.x * 1.5, Math.min(sp.getDeltaMovement().y, 0.1), look.z * 1.5);
                sp.hurtMarked = true;
                List<LivingEntity> h = Combat.sphere(lvl(sp), sp.position().add(0, 1, 0).add(look), 1.8, sp);
                if (h.isEmpty()) return;
                LivingEntity t = h.get(0);
                grabbed[0] = t;
                sp.setDeltaMovement(Vec3.ZERO);
                sp.hurtMarked = true;
                Combat.damage(sp, t, 6f, true);
                Combat.launch(t, new Vec3(0, 1.45, 0));
                Combat.sound(t, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
                HakariNetwork.fx(sp, FxType.SMALL_HIT, sp, t, t.position().add(0, 1, 0), 0, 0);
                Scheduler.after(11, () -> {
                    if (!t.isAlive() || !sp.isAlive()) return;
                    sp.teleportTo(t.getX() - look.x * 0.6, t.getY() + 1.6, t.getZ() - look.z * 0.6);
                    Combat.damage(sp, t, 14f, true);
                    Combat.launch(t, new Vec3(look.x * 0.4, -2.8, look.z * 0.4));
                    sp.setDeltaMovement(0, 0.2, 0);
                    sp.hurtMarked = true;
                    sp.fallDistance = 0;
                    HakariNetwork.fx(sp, FxType.RUSH_SLAM, sp, t, t.position().add(0, 1, 0), sp.getYRot(), 0);
                    Combat.sound(t, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.8f);
                    Scheduler.after(4, () -> HakariNetwork.fx(sp, FxType.SURGE_SLAM, sp, null, t.position(), 0, 3.5f));
                });
            });
        }
        return 80;
    }

    public static int overwhelmingLuck(ServerPlayer sp, HakariState st) {
        HakariNetwork.cutscene(sp, CutType.MICRO_OVERWHELM, sp.getId());
        HakariNetwork.fx(sp, FxType.RUSH_DASH, sp, null, sp.position(), sp.getYRot(), 1);
        Combat.sound(sp, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 1.2f);
        final LivingEntity[] grabbed = {null};
        for (int i = 0; i < 50; i++) {
            final int k = i;
            Scheduler.after(i, () -> {
                if (!sp.isAlive()) return;
                Vec3 look = Combat.flatLook(sp);
                sp.setDeltaMovement(look.x * 0.95, sp.getDeltaMovement().y, look.z * 0.95);
                sp.hurtMarked = true;
                if (grabbed[0] == null) {
                    List<LivingEntity> h = Combat.sphere(lvl(sp), sp.position().add(0, 1, 0).add(look.scale(1.2)), 1.8, sp);
                    if (!h.isEmpty()) {
                        grabbed[0] = h.get(0);
                        Combat.damage(sp, grabbed[0], 4f, true);
                        HakariNetwork.fx(sp, FxType.SMALL_HIT, sp, grabbed[0], grabbed[0].position().add(0, 1, 0), 0, 0);
                        Combat.sound(sp, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
                    }
                } else {
                    LivingEntity t = grabbed[0];
                    if (!t.isAlive()) return;
                    Vec3 hold = sp.position().add(look.scale(1.4));
                    t.teleportTo(hold.x, sp.getY() + 0.3, hold.z);
                    Combat.stun(t, 4);
                    if (k % 8 == 0) {
                        Combat.damage(sp, t, 4f, true);
                        HakariNetwork.fx(sp, FxType.OVERWHELM_HIT, sp, t, t.position().add(0, 1, 0), sp.getYRot(), 0);
                        Combat.sound(sp, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.9f);
                    }
                }
            });
        }
        Scheduler.after(51, () -> {
            if (!sp.isAlive()) return;
            Vec3 look = Combat.flatLook(sp);
            sp.setDeltaMovement(Vec3.ZERO);
            sp.hurtMarked = true;
            LivingEntity t = grabbed[0];
            Vec3 at = t != null ? t.position().add(0, 1, 0) : sp.getEyePosition().add(look.scale(2));
            HakariNetwork.fx(sp, FxType.OVERWHELM_THROW, sp, t, at, sp.getYRot(), 0);
            Combat.sound(sp, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.6f);
            Combat.sound(sp, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.1f);
            if (t != null && t.isAlive()) {
                Combat.damage(sp, t, 12f, true);
                Combat.launch(t, look.scale(3.2).add(0, 1.0, 0));
            }
        });
        return 120;
    }

    public static int energySurge(ServerPlayer sp, HakariState st) {
        Vec3 look = Combat.flatLook(sp);
        Combat.launch(sp, look.scale(0.7).add(0, 1.35, 0));
        HakariNetwork.fx(sp, FxType.SURGE_LEAP, sp, null, sp.position(), 0, 0);
        HakariNetwork.cutscene(sp, CutType.MICRO_SURGE, sp.getId());
        Combat.sound(sp, SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.8f);
        Scheduler.after(12, () -> {
            sp.setDeltaMovement(look.x * 0.3, -2.6, look.z * 0.3);
            sp.hurtMarked = true;
        });
        final boolean[] slammed = {false};
        for (int i = 13; i <= 40; i++) {
            final boolean last = i == 40;
            Scheduler.after(i, () -> {
                if (slammed[0] || !sp.isAlive()) return;
                if (!sp.onGround() && !last) return;
                slammed[0] = true;
                sp.fallDistance = 0;
                Vec3 c = sp.position();
                HakariNetwork.fx(sp, FxType.SURGE_SLAM, sp, null, c, 0, 6f);
                Combat.sound(sp, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.6f);
                Combat.sound(sp, SoundEvents.ANVIL_LAND, 1f, 0.5f);
                List<LivingEntity> h = Combat.sphere(lvl(sp), c, 6, sp);
                for (LivingEntity e : h) {
                    Combat.damage(sp, e, 10f, true);
                    Combat.launch(e, new Vec3(0, 1.1, 0));
                }
                final LivingEntity t = h.isEmpty() ? null : h.get(0);
                Scheduler.after(7, () -> {
                    if (t == null || !t.isAlive() || !sp.isAlive()) return;
                    sp.teleportTo(t.getX() - look.x, t.getY(), t.getZ() - look.z);
                    Combat.damage(sp, t, 8f, true);
                    Combat.launch(t, look.scale(2.3).add(0, 0.6, 0));
                    HakariNetwork.fx(sp, FxType.SURGE_KICK, sp, t, t.position().add(0, 1, 0), sp.getYRot(), 0);
                    Combat.sound(t, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
                });
            });
        }
        return 90;
    }

    public static int rhythm(ServerPlayer sp, HakariState st) {
        st.rhythmTicks = 200;
        for (RegistryObject<Item> ro : ModRegistry.MOVE_SCROLLS) sp.getCooldowns().removeCooldown(ro.get());
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 2, false, false));
        HakariNetwork.fx(sp, FxType.RHYTHM, sp, null, sp.position(), 0, 0);
        Combat.sound(sp, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.5f, 0.8f);
        Combat.sound(sp, SoundEvents.BEACON_POWER_SELECT, 1f, 1.6f);
        return 300;
    }

    private Moves() {}
}
