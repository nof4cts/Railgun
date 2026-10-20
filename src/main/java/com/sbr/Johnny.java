package com.sbr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Johnny Joestar - Tusk. Spinning fingernails fired like bullets, growing stronger Act by Act:
 * ACT1 Nail Shot and Finger Cutter; ACT2 the Golden Rectangle nail, whose hole chases its mark; ACT3 the wormhole he
 * shoots himself into; ACT4, reached only by the Golden Spin (a galloping horse, or a long spin-up on foot), Infinite
 * Rotation and the ORA ORA rush. Ten nails, which grow back.
 */
public final class Johnny {
    public static final String NAME = "JOHNNY";
    public static final String[] MOVES = {"ACT1: Nail Shot", "ACT1: Finger Cutter", "ACT2: Golden Rectangle Nail", "ACT3: Wormhole",
            "ACT4: Infinite Rotation", "ACT4: ORA ORA Rush"};
    static final int[] COOLDOWN = {8, 30, 70, 160, 600, 240};
    static final int MAX_NAILS = 10, REGROW = 20;
    /** nails, the tick the next one grows back, ACT4 awake until, wormhole bonus until */
    private static final Map<UUID, long[]> STATE = new HashMap<>();

    private Johnny() {}

    static long[] st(ServerPlayer p) {
        return STATE.computeIfAbsent(p.getUUID(), k -> new long[]{MAX_NAILS, 0, 0, 0});
    }

    static void forget(UUID id) {
        STATE.remove(id);
    }

    /** Nails grow back one by one; the count goes to his HUD. */
    static int nails(ServerPlayer p) {
        long[] s = st(p);
        long now = Sbr.now(p);
        while (s[0] < MAX_NAILS && s[1] <= now) {
            s[0]++;
            s[1] = (s[1] == 0 ? now : s[1]) + REGROW;
        }
        if (s[0] >= MAX_NAILS) {
            s[1] = 0;
        }
        return (int) s[0];
    }

    static boolean spend(ServerPlayer p, int n) {
        if (p.getAbilities().instabuild) {
            return true;
        }
        long[] s = st(p);
        int have = nails(p);
        if (have < n) {
            Sbr.say(p, "Out of nails - they're growing back (" + have + "/" + MAX_NAILS + ")", ChatFormatting.GRAY);
            return false;
        }
        s[0] -= n;
        if (s[1] == 0) {
            s[1] = Sbr.now(p) + REGROW;
        }
        sync(p);
        return true;
    }

    static void sync(ServerPlayer p) {
        long[] s = st(p);
        SbrNet.to(p, SbrNet.Fx.of(SbrNet.AMMO, p.getId(), p.position(), Vec3.ZERO, nails(p), MAX_NAILS));
        SbrNet.to(p, SbrNet.Fx.of(SbrNet.GOLDEN, p.getId(), p.position(), Vec3.ZERO, Math.max(0, s[2] - Sbr.now(p)), 1));
    }

    static boolean act4(ServerPlayer p) {
        return st(p)[2] > Sbr.now(p);
    }

    static float bonus(ServerPlayer p) {
        return st(p)[3] > Sbr.now(p) ? 1.5F : 1.0F;
    }

    static Vec3 finger(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        return p.getEyePosition().add(look.scale(0.6)).add(new Vec3(-flat.z, 0, flat.x).scale(0.35)).add(0, -0.3, 0);
    }

    static void key(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= MOVES.length) {
            return;
        }
        if (!Sbr.ready(p, slot, MOVES[slot])) {
            return;
        }
        switch (slot) {
            case 0 -> nailShot(p);
            case 1 -> fingerCutter(p);
            case 2 -> goldenNail(p);
            case 3 -> wormhole(p);
            case 4 -> infiniteRotation(p);
            case 5 -> ora(p);
            default -> {
            }
        }
        sync(p);
    }

    /** ACT1: a spinning nail, fired from the fingertip. */
    static void nailShot(ServerPlayer p) {
        if (!spend(p, 1)) {
            return;
        }
        Sbr.cooldown(p, 0, COOLDOWN[0]);
        ServerLevel l = p.serverLevel();
        Vec3 from = finger(p), dir = p.getLookAngle().normalize();
        float dmg = 4.5F * bonus(p);
        Sbr.play(l, from, SoundEvents.ARROW_SHOOT, 0.8F, 1.9F);
        double reach = Sbr.shoot(p, from, dir, 3.5, 16, 0.25, false, e -> {
            Sbr.hurt(p, e, dmg);
            Sbr.push(e, dir.scale(0.25));
        }, at -> {
            Sbr.play(l, at, SoundEvents.ARROW_HIT, 0.7F, 1.6F);
            SbrNet.around(l, at, SbrNet.Fx.of(SbrNet.HIT, p.getId(), at, dir, 0, 0));
        });
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.NAIL1, p.getId(), from, dir, 3.5F, (float) reach));
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.SPIN_UP, p.getId(), from, dir, 0, 0));
    }

    /** ACT1: Finger Cutter - the spinning nails as short blades, swept across in front of him. */
    static void fingerCutter(ServerPlayer p) {
        Sbr.cooldown(p, 1, COOLDOWN[1]);
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle().normalize();
        for (LivingEntity e : Sbr.around(p, eye, 4.4)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double ang = Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1, 1)));
            if (ang <= 70) {
                Sbr.hurt(p, e, 5.0F * bonus(p));
                Sbr.push(e, look.scale(0.35).add(0, 0.12, 0));
            }
        }
        Sbr.play(l, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.5F);
        SbrNet.around(l, eye, SbrNet.Fx.of(SbrNet.CUTTER, p.getId(), eye, look, 0, 0));
    }

    /** ACT2: the Golden Rectangle nail - it drills, and the hole it leaves chases its mark. */
    static void goldenNail(ServerPlayer p) {
        if (!spend(p, 3)) {
            return;
        }
        Sbr.cooldown(p, 2, COOLDOWN[2]);
        ServerLevel l = p.serverLevel();
        Vec3 from = finger(p), dir = p.getLookAngle().normalize();
        float dmg = 7.0F * bonus(p);
        LivingEntity[] marked = {null};
        Sbr.play(l, from, SoundEvents.ARROW_SHOOT, 1.0F, 1.4F);
        double reach = Sbr.shoot(p, from, dir, 4.0, 24, 0.3, false, e -> {
            Sbr.hurt(p, e, dmg);
            marked[0] = e;
        }, at -> {
            Sbr.play(l, at, SoundEvents.ANVIL_LAND, 0.4F, 1.8F);
            SbrNet.around(l, at, SbrNet.Fx.of(SbrNet.HIT, p.getId(), at, dir, 1, 0));
            hole(p, at, marked[0]);
        });
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.NAIL2, p.getId(), from, dir, 4.0F, (float) reach));
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.SPIN_UP, p.getId(), from, dir, 0, 0));
    }

    /** The hole: it slides after its mark (or the nearest thing, if the nail hit a wall) for eight seconds. */
    static void hole(ServerPlayer p, Vec3 at, LivingEntity mark) {
        ServerLevel l = p.serverLevel();
        if (mark == null) {
            List<LivingEntity> near = Sbr.around(p, at, 10);
            near.sort((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at)));
            mark = near.isEmpty() ? null : near.get(0);
        }
        if (mark == null) {
            return;
        }
        LivingEntity target = mark;
        long start = Sbr.now(p);
        int id = (int) (start * 31 + p.getId()) & 0x7FFFFFFF;
        Vec3[] pos = {at};
        int[] bites = {0};
        long[] lastBite = {-100};
        Sbr.JOBS.add(new Sbr.Job(p, start, start + 160) {
            @Override
            void tick(long t) {
                if (!target.isAlive() || target.level() != p.level()) {
                    until = t;
                    return;
                }
                Vec3 goal = target.position().add(0, Math.min(0.9, target.getBbHeight() * 0.5), 0);
                Vec3 d = goal.subtract(pos[0]);
                double step = Math.min(d.length(), 0.34);
                if (d.length() > 1e-4) {
                    pos[0] = pos[0].add(d.normalize().scale(step));
                }
                if (t % 2 == 0) {
                    SbrNet.around(l, pos[0], SbrNet.Fx.of(SbrNet.HOLE, id, pos[0], Vec3.ZERO, 4, 0));
                }
                if (pos[0].distanceTo(goal) < 1.1 && t - lastBite[0] >= 15) {
                    lastBite[0] = t;
                    Sbr.hurt(p, target, 3.0F);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, true));
                    Sbr.play(l, pos[0], SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 0.6F);
                    if (++bites[0] >= 3) {
                        until = t;
                    }
                }
            }

            @Override
            void end() {
                SbrNet.around(l, pos[0], SbrNet.Fx.of(SbrNet.HOLE_END, id, pos[0], Vec3.ZERO, 0, 0));
            }
        });
    }

    /** ACT3: he fires into himself and slips into the hole - and comes out where he's looking. */
    static void wormhole(ServerPlayer p) {
        if (!spend(p, 1)) {
            return;
        }
        ServerLevel l = p.serverLevel();
        Vec3 from = p.position();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 hit = Sbr.sight(p, 18);
        double reach = Math.max(0, eye.distanceTo(hit) - 0.7);
        Vec3 to = null;
        for (double d = reach; d >= 0; d -= 0.5) {
            Vec3 c = eye.add(dir.scale(d)).subtract(0, p.getEyeHeight(), 0);
            if (l.noCollision(p, p.getBoundingBox().move(c.subtract(from)))) {
                to = c;
                break;
            }
        }
        if (to == null || to.distanceTo(from) < 2) {
            Sbr.say(p, "No room to come out there", ChatFormatting.GRAY);
            return;
        }
        Sbr.cooldown(p, 3, COOLDOWN[3]);
        Vec3 out = to;
        long start = Sbr.now(p);
        Sbr.BUSY.put(p.getUUID(), start + 10);
        p.setInvisible(true);
        p.setInvulnerable(true);
        Sbr.play(l, from, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 0.7F);
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.WORM_IN, p.getId(), from, out.subtract(from), 10, 0));
        Sbr.JOBS.add(new Sbr.Job(p, start, start + 10) {
            @Override
            void tick(long t) {
                p.setDeltaMovement(Vec3.ZERO);
                p.hurtMarked = true;
            }

            @Override
            void end() {
                p.setInvisible(false);
                p.setInvulnerable(false);
                p.connection.teleport(out.x, out.y, out.z, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
                st(p)[3] = Sbr.now(p) + 60;
                Sbr.play(l, out, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 1.2F);
                SbrNet.around(l, out, SbrNet.Fx.of(SbrNet.WORM_OUT, p.getId(), out, Vec3.ZERO, 0, 0));
            }
        });
    }

    /** ACT4 needs the Golden Spin: on a galloping horse it comes at once; on foot he has to spin it up first. */
    static void infiniteRotation(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (Sbr.galloping(p) || act4(p)) {
            fireRotation(p);
            return;
        }
        long start = Sbr.now(p);
        int charge = 30;
        Sbr.BUSY.put(p.getUUID(), start + charge);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, charge, 4, false, false, false));
        Sbr.say(p, "Golden Spin... (a galloping horse makes it instant)", ChatFormatting.GOLD);
        Sbr.play(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.4F);
        SbrNet.around(l, p.position(), SbrNet.Fx.of(SbrNet.GOLDEN, p.getId(), p.position(), Vec3.ZERO, charge, 0));
        Sbr.JOBS.add(new Sbr.Job(p, start, start + charge) {
            @Override
            void tick(long t) {
            }

            @Override
            void end() {
                fireRotation(p);
            }
        });
    }

    static void fireRotation(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Sbr.cooldown(p, 4, COOLDOWN[4]);
        long[] s = st(p);
        s[2] = Sbr.now(p) + 300;
        Vec3 from = finger(p), dir = p.getLookAngle().normalize();
        Sbr.play(l, from, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.6F);
        Sbr.say(p, "TUSK ACT4", ChatFormatting.GOLD);
        double reach = Sbr.shoot(p, from, dir, 4.0, 30, 0.35, false, e -> {
            Sbr.hurt(p, e, 7.0F);
            Sbr.spin(p, e, e.position(), 120);
        }, at -> SbrNet.around(l, at, SbrNet.Fx.of(SbrNet.HIT, p.getId(), at, dir, 2, 0)));
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.NAIL4, p.getId(), from, dir, 4.0F, (float) reach));
        SbrNet.around(l, from, SbrNet.Fx.of(SbrNet.SPIN_UP, p.getId(), from, dir, 1, 0));
        sync(p);
    }

    /** ACT4: ORA ORA - a storm of blows in front of him, each laced with the infinite spin. Only while ACT4 is awake. */
    static void ora(ServerPlayer p) {
        if (!act4(p) && !Sbr.galloping(p)) {
            Sbr.say(p, "ACT4 isn't awake - use Infinite Rotation first (or ride at a gallop)", ChatFormatting.GRAY);
            return;
        }
        Sbr.cooldown(p, 5, COOLDOWN[5]);
        ServerLevel l = p.serverLevel();
        long start = Sbr.now(p);
        int len = 30;
        Sbr.BUSY.put(p.getUUID(), start + len);
        SbrNet.around(l, p.position(), SbrNet.Fx.of(SbrNet.ORA, p.getId(), p.position(), Vec3.ZERO, len, 0));
        Set<Integer> spun = new HashSet<>();
        Sbr.JOBS.add(new Sbr.Job(p, start, start + len) {
            @Override
            void tick(long t) {
                if ((t - start) % 3 != 0) {
                    return;
                }
                Vec3 look = p.getLookAngle().normalize();
                Vec3 front = p.getEyePosition().add(look.scale(1.8));
                for (LivingEntity e : Sbr.around(p, front, 2.0)) {
                    Sbr.hurt(p, e, 1.2F);
                    e.setDeltaMovement(look.scale(0.08));
                    e.hurtMarked = true;
                    if (spun.add(e.getId())) {
                        Sbr.spin(p, e, e.position(), 30);
                    }
                }
                Sbr.play(l, front, SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 1.3F + (t % 7) * 0.05F);
            }

            @Override
            void end() {
                Vec3 look = p.getLookAngle().normalize();
                for (LivingEntity e : Sbr.around(p, p.getEyePosition().add(look.scale(1.8)), 2.4)) {
                    Sbr.hurt(p, e, 3.0F);
                    Sbr.push(e, look.scale(1.3).add(0, 0.35, 0));
                }
                Sbr.play(l, p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
            }
        });
    }
}
