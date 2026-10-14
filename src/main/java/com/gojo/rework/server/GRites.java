package com.gojo.rework.server;

import com.gojo.mod.CursedEnergy;
import com.gojo.rework.Gojo2;
import com.gojo.rework.common.GBeats;
import com.gojo.rework.common.GMove;
import com.gojo.rework.net.GNet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gojo's reworked techniques on the server: each cast is a script run tick by tick against the timings in
 * {@link GBeats}, which the clients play from the same message. Damage, knockback, the domain's hold on its victims,
 * and the land: craters, trenches, furrows and the tunnels Purple erases (spread over ticks by the {@link Carver}).
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class GRites {
    private static final List<Cast> ACTIVE = new ArrayList<>();
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int BUDGET = 14000;

    private GRites() {}

    static final class Cast {
        final GMove move;
        final ServerPlayer p;
        final ServerLevel level;
        final long start;
        final Vec3 origin, dir, point, feet;
        final long seed;
        final Set<Integer> hit = new HashSet<>();
        Vec3 last;
        double lastD;
        boolean held;

        Cast(GMove move, ServerPlayer p, Vec3 origin, Vec3 dir, Vec3 point, long seed) {
            this.move = move;
            this.p = p;
            this.level = p.serverLevel();
            this.start = level.getGameTime();
            this.origin = origin;
            this.dir = dir;
            this.point = point;
            this.feet = p.position();
            this.seed = seed;
        }

        int t() {
            return (int) (level.getGameTime() - start);
        }

        Vec3 flat() {
            Vec3 f = new Vec3(dir.x, 0, dir.z);
            return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
        }

        Vec3 right() {
            Vec3 f = flat();
            return new Vec3(-f.z, 0, f.x);
        }

        int end() {
            return switch (move) {
                case DOMAIN -> GBeats.DV_HOLD;
                case DOMAIN_02 -> GBeats.D2_OPEN + GBeats.D2_STUN;
                default -> move.length;
            };
        }
    }

    // ------------------------------------------------------------------------------------------------ asking

    public static boolean busy(Player p) {
        for (Cast c : ACTIVE) {
            if (c.p == p && c.move.cinematic && c.t() < c.move.length) {
                return true;
            }
        }
        return false;
    }

    /** Begin a technique. False if it can't be done now (the item refunds the cost). */
    public static boolean ask(ServerPlayer p, GMove m) {
        if (!p.isAlive() || p.isSpectator() || busy(p)) {
            return false;
        }
        for (Cast c : ACTIVE) {
            if (c.p == p && c.move == m && c.t() < c.move.length) {
                return false;
            }
        }
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 point = switch (m) {
            case RED -> aim(p, GBeats.RED_RANGE);
            case NUKE -> ground(p.serverLevel(), aim(p, GBeats.NK_RANGE));
            case MAX_BLUE -> eye.add(dir.scale(3));
            // the Purples are fired from the air at whatever he was looking at when he began
            case PURPLE -> aim(p, GBeats.HP_RANGE);
            case IMAGINARY -> aim(p, GBeats.IM_RANGE);
            case PURPLE_200 -> aim(p, GBeats.P2_RANGE);
            default -> eye.add(dir.scale(4));
        };
        if (m == GMove.MAX_BLUE) {
            // Maximum Blue keeps low: it ploughs along the land
            dir = new Vec3(dir.x, Mth.clamp(dir.y, -0.35, 0.18), dir.z).normalize();
        }
        Cast c = new Cast(m, p, eye, dir, point, p.getRandom().nextLong());
        ACTIVE.add(c);
        double range = switch (m) {
            case NUKE -> 420;
            case PURPLE_200, IMAGINARY -> 320;
            default -> 200;
        };
        GNet.near(p, eye, range, new GNet.Cast(m.ordinal(), p.getId(), c.start, eye, dir, point, c.seed, m.cinematic));
        return true;
    }

    /** What the eye is on: the first living thing near the line of sight, else the first block, else the end of reach. */
    static Vec3 aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, stop, new AABB(eye, stop).inflate(2.0),
                e -> e instanceof LivingEntity && e.isAlive() && e != p, 1.5F);
        if (eh != null) {
            return eh.getEntity().getBoundingBox().getCenter();
        }
        return stop;
    }

    static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos m = BlockPos.containing(at).mutable();
        for (int i = 0; i < 64 && m.getY() > level.getMinBuildHeight(); i++) {
            if (!level.getBlockState(m.below()).isAir()) {
                return new Vec3(at.x, m.getY(), at.z);
            }
            m.move(0, -1, 0);
        }
        return at;
    }

    // ------------------------------------------------------------------------------------------------ Infinity

    public static void infinity(ServerPlayer p, boolean on) {
        GNet.near(p, p.position(), 160, new GNet.Inf(p.getId(), on, on));
        sound(p.serverLevel(), p.position(), Gojo2.INFINITY_ON.get(), 1.2F, on ? 1.0F : 0.7F);
    }

    public static void ripple(ServerPlayer p, Vec3 at, Vec3 normal) {
        GNet.near(p, at, 96, new GNet.Ripple(at, normal.lengthSqr() < 1e-6 ? UP : normal.normalize()));
        sound(p.serverLevel(), at, Gojo2.INFINITY_RIPPLE.get(), 0.7F, 0.9F + p.getRandom().nextFloat() * 0.3F);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
                if (CursedEnergy.isInfinity(o)) {
                    GNet.to(p, new GNet.Inf(o.getId(), true, false));
                }
            }
            if (CursedEnergy.isInfinity(p)) {
                GNet.near(p, p.position(), 160, new GNet.Inf(p.getId(), true, false));
            }
        }
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer o && CursedEnergy.isInfinity(o)) {
            GNet.to(viewer, new GNet.Inf(o.getId(), true, false));
        }
    }

    // ------------------------------------------------------------------------------------------------ the scripts

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Iterator<Cast> it = ACTIVE.iterator();
        List<Cast> run = new ArrayList<>();
        while (it.hasNext()) {
            Cast c = it.next();
            if (c.p.isRemoved() || c.p.level() != c.level || c.t() > c.end() + 2) {
                release(c);
                it.remove();
            } else {
                run.add(c);
            }
        }
        for (Cast c : run) {
            int t = c.t();
            try {
                switch (c.move) {
                    case RED -> red(c, t);
                    case MAX_BLUE -> maxBlue(c, t);
                    case MAX_RED -> maxRed(c, t);
                    case PURPLE -> {
                        rise(c, t, GBeats.HP_RISE, GBeats.HP_RISEN);
                        purple(c, t, GBeats.HP_FIRE, GBeats.HP_SPEED, GBeats.HP_RANGE, GBeats.HP_RADIUS, 90F);
                    }
                    case IMAGINARY -> {
                        rise(c, t, GBeats.IM_RISE, 20);
                        chant(c, t);
                        merge(c, t, GBeats.IM_MERGE, GBeats.IM_FIRE);
                        purple(c, t, GBeats.IM_FIRE, GBeats.IM_SPEED, GBeats.IM_RANGE, GBeats.IM_RADIUS, 160F);
                    }
                    case PURPLE_200 -> {
                        rise(c, t, GBeats.P2_RISE, 26);
                        chant(c, t - GBeats.P2_SHIFT);
                        merge(c, t, GBeats.P2_MERGE, GBeats.P2_FIRE);
                        purple(c, t, GBeats.P2_FIRE, GBeats.P2_SPEED, GBeats.P2_RANGE, GBeats.P2_RADIUS, 320F);
                    }
                    case NUKE -> nuke(c, t);
                    case DOMAIN -> domain(c, t);
                    case DOMAIN_02 -> domain02(c, t);
                }
            } catch (RuntimeException ex) {
                Gojo2.LOGGER.error("[gojo] {} failed at t={}", c.move, t, ex);
                c.held = false;
                release(c);
                ACTIVE.remove(c);
            }
        }
        Carver.tick(BUDGET);
    }

    /** Let go of the caster: gravity back, no fall damage from the float. */
    static void release(Cast c) {
        if (c.held) {
            c.held = false;
            c.p.setNoGravity(false);
            c.p.fallDistance = 0;
            // he drifts back down from the air rather than dropping
            c.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
        }
    }

    /** Keep the caster still through a cutscene, lifted {@code height} blocks over {@code ticks}. */
    static void rise(Cast c, int t, double height, int ticks) {
        if (t >= c.move.length - 4) {
            release(c);
            return;
        }
        c.held = true;
        c.p.setNoGravity(true);
        double k = Mth.clamp(t / (double) ticks, 0, 1);
        k = k * k * (3 - 2 * k);
        Vec3 at = c.feet.add(0, height * k + Math.sin(t * 0.12) * 0.08, 0);
        c.p.setDeltaMovement(Vec3.ZERO);
        c.p.connection.teleport(at.x, at.y, at.z, c.p.getYRot(), c.p.getXRot());
        c.p.fallDistance = 0;
    }

    static void chant(Cast c, int t) {
        for (int i = 0; i < GBeats.CHANT.length; i++) {
            if (t == GBeats.CHANT[i]) {
                sound(c.level, c.p.position(), Gojo2.CHANT.get(), 1.4F, 0.9F + i * 0.06F);
            }
        }
    }

    static void merge(Cast c, int t, int merge, int fire) {
        if (t == merge) {
            sound(c.level, c.p.position(), Gojo2.PURPLE_MERGE.get(), 3.0F, 1.0F);
        }
        if (t == fire) {
            sound(c.level, c.p.position(), Gojo2.PURPLE_BIG.get(), 8.0F, 1.0F);
        }
    }

    // ---- Reversal: Red

    static void red(Cast c, int t) {
        if (t == 0) {
            sound(c.level, c.origin, Gojo2.RED_CHARGE.get(), 1.4F, 1.0F);
        }
        if (t == GBeats.RED_FIRE) {
            sound(c.level, c.origin, Gojo2.RED_FIRE.get(), 2.0F, 1.0F);
            // the recoil throws everything right in front of him back
            for (LivingEntity e : around(c, c.origin.add(c.dir.scale(1.5)), 2.5)) {
                hurt(c, e, 6, c.dir.scale(1.2).add(0, 0.3, 0));
            }
        }
        double dist = c.origin.distanceTo(c.point);
        int at = GBeats.arrive(GBeats.RED_FIRE, GBeats.RED_SPEED, dist);
        if (t > GBeats.RED_FIRE && t < at) {
            // anything the red passes through is hit
            Vec3 a = c.origin.add(c.dir.scale(Math.min(dist, (t - 1 - GBeats.RED_FIRE) * GBeats.RED_SPEED)));
            Vec3 b = c.origin.add(c.dir.scale(Math.min(dist, (t - GBeats.RED_FIRE) * GBeats.RED_SPEED)));
            for (LivingEntity e : nearSegment(c, a, b, 1.6)) {
                hurt(c, e, 10, c.dir.scale(2.2).add(0, 0.5, 0));
            }
        }
        if (t == at) {
            Vec3 o = c.point;
            sound(c.level, o, Gojo2.RED_IMPACT.get(), 5.0F, 1.0F);
            crater(c, o, GBeats.RED_CRATER, 1.3);
            for (LivingEntity e : around(c, o, GBeats.RED_REACH)) {
                double d = e.position().distanceTo(o);
                Vec3 away = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(o);
                away = away.lengthSqr() < 1e-4 ? c.dir : away.normalize();
                float k = (float) (1 - d / (GBeats.RED_REACH + 1));
                hurt(c, e, 14 + 26 * k, away.scale(1.6 + 2.4 * k).add(0, 0.6 + 0.6 * k, 0));
            }
        }
    }

    // ---- Maximum Output: Blue

    static Vec3 maxBlueAt(Cast c, float t) {
        Vec3 from = c.origin.add(c.dir.scale(3)).add(0, 0.5, 0);
        if (t < GBeats.MB_LAUNCH) {
            return from;
        }
        return from.add(c.dir.scale(Math.min(t, GBeats.MB_END) - GBeats.MB_LAUNCH).scale(GBeats.MB_SPEED));
    }

    public static Vec3 maxBlueAt(Vec3 origin, Vec3 dir, float t) {
        Vec3 from = origin.add(dir.scale(3)).add(0, 0.5, 0);
        if (t < GBeats.MB_LAUNCH) {
            return from;
        }
        return from.add(dir.scale((Math.min(t, GBeats.MB_END) - GBeats.MB_LAUNCH) * GBeats.MB_SPEED));
    }

    static void maxBlue(Cast c, int t) {
        if (t == 0) {
            sound(c.level, c.origin, Gojo2.BLUE_CHARGE.get(), 2.0F, 0.8F);
        }
        Vec3 o = maxBlueAt(c.origin, c.dir, t);
        if (t == GBeats.MB_LAUNCH) {
            sound(c.level, o, Gojo2.BLUE_DRAG.get(), 4.0F, 1.0F);
        }
        if (t >= GBeats.MB_LAUNCH && t < GBeats.MB_END) {
            erase(c.level, o, GBeats.MB_RADIUS + 0.4);
            for (LivingEntity e : around(c, o, GBeats.MB_PULL)) {
                Vec3 to = o.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
                double d = to.length();
                if (d > 0.6) {
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.3).add(to.normalize().scale(Math.min(1.6, 0.35 + 6 / (d + 2)))));
                    e.hurtMarked = true;
                }
                if (d < GBeats.MB_RADIUS + 1.2 && t % 3 == 0) {
                    hurtAgain(c, e, 5);
                }
            }
            // loose things are drawn in and swallowed
            for (Entity e : c.level.getEntities((Entity) null, new AABB(o, o).inflate(GBeats.MB_PULL), x -> x instanceof ItemEntity)) {
                if (e.position().distanceTo(o) < GBeats.MB_RADIUS) {
                    e.discard();
                } else {
                    e.setDeltaMovement(o.subtract(e.position()).normalize().scale(0.8));
                }
            }
        }
        if (t == GBeats.MB_END) {
            sound(c.level, o, Gojo2.BLUE_CRUSH.get(), 5.0F, 1.0F);
            erase(c.level, o, GBeats.MB_RADIUS + 2.4);
            for (LivingEntity e : around(c, o, 8)) {
                hurtAgain(c, e, 30);
            }
        }
    }

    // ---- Maximum Output: Red

    static void maxRed(Cast c, int t) {
        if (t == 0) {
            sound(c.level, c.origin, Gojo2.RED_CHARGE.get(), 2.0F, 0.75F);
        }
        if (t == GBeats.MR_FIRE) {
            sound(c.level, c.origin, Gojo2.MAXRED_FIRE.get(), 8.0F, 1.0F);
            c.lastD = 1.5;
        }
        if (t > GBeats.MR_FIRE && t <= GBeats.MR_FIRE + GBeats.MR_TRAVEL) {
            double k = (t - GBeats.MR_FIRE) / (double) GBeats.MR_TRAVEL;
            double d = GBeats.MR_LENGTH * k;
            Carver.Batch b = Carver.batch(c.level);
            for (double s = c.lastD; s <= d; s += 1.0) {
                double r = GBeats.MR_R0 + (GBeats.MR_R1 - GBeats.MR_R0) * (s / GBeats.MR_LENGTH);
                sphere(b, c.origin.add(c.dir.scale(s)).add(0, -0.8, 0), r);
            }
            b.go();
            Vec3 a = c.origin.add(c.dir.scale(c.lastD)), z = c.origin.add(c.dir.scale(d));
            for (LivingEntity e : nearSegment(c, a, z, GBeats.MR_R1 + 1)) {
                hurt(c, e, 46, c.dir.scale(3.2).add(0, 0.9, 0));
            }
            c.lastD = d;
        }
    }

    // ---- the Purples

    static void purple(Cast c, int t, int fire, double speed, double range, double radius, float dmg) {
        if (c.move == GMove.PURPLE) {
            if (t == GBeats.HP_BLUE) {
                sound(c.level, c.origin, Gojo2.BLUE_CHARGE.get(), 1.0F, 1.3F);
            }
            if (t == GBeats.HP_RED) {
                sound(c.level, c.origin, Gojo2.RED_CHARGE.get(), 1.0F, 1.3F);
            }
            if (t == GBeats.HP_MERGE) {
                sound(c.level, c.origin, Gojo2.PURPLE_MERGE.get(), 2.0F, 1.1F);
            }
            if (t == fire) {
                sound(c.level, c.origin, Gojo2.PURPLE_FIRE.get(), 6.0F, 1.0F);
            }
        }
        if (t < fire) {
            if (c.move.cinematic) {
                c.p.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        Vec3 from = start(c);
        Vec3 fd = GBeats.purpleDir(c.move, c.origin, c.dir, c.point);
        double d0 = Math.min(range, (t - 1 - fire) * speed), d1 = Math.min(range, (t - fire) * speed);
        if (d0 >= range) {
            return;
        }
        Carver.Batch b = Carver.batch(c.level);
        for (double s = Math.max(0, d0); s <= d1; s += 1.0) {
            sphere(b, from.add(fd.scale(s)), radius);
        }
        b.go();
        Vec3 a = from.add(fd.scale(Math.max(0, d0))), z = from.add(fd.scale(d1));
        for (LivingEntity e : nearSegment(c, a, z, radius + 1)) {
            hurt(c, e, dmg, fd.scale(1.5));
        }
        for (Entity e : c.level.getEntities((Entity) null, new AABB(a, z).inflate(radius + 1),
                x -> x instanceof ItemEntity || (x instanceof Projectile pr && pr.getOwner() != c.p))) {
            e.discard();
        }
    }

    /** Where the Purple leaves from: in front of his hands. */
    static Vec3 start(Cast c) {
        return GBeats.purpleStart(c.move, c.origin, c.dir);
    }

    // ---- Hollow Nuke

    public static Vec3 nukeTarget(Vec3 point) {
        return point;
    }

    static void nuke(Cast c, int t) {
        if (t < GBeats.NK_BLAST + 20) {
            rise(c, t, GBeats.NK_FLY, GBeats.NK_RISE);
        } else {
            release(c);
        }
        Vec3 sky = GBeats.nukeRedPoint(c.origin, c.dir);
        if (t == GBeats.NK_BLUE) {
            sound(c.level, c.p.position(), Gojo2.BLUE_CHARGE.get(), 6.0F, 0.7F);
        }
        if (t == GBeats.NK_RED) {
            sound(c.level, c.p.position(), Gojo2.RED_FIRE.get(), 6.0F, 0.7F);
        }
        if (t == GBeats.NK_JOIN) {
            sound(c.level, sky, Gojo2.NUKE_CHARGE.get(), 16.0F, 1.0F);
        }
        if (t == GBeats.NK_MERGE) {
            sound(c.level, sky, Gojo2.PURPLE_MERGE.get(), 16.0F, 0.6F);
        }
        if (t == GBeats.NK_LAUNCH) {
            sound(c.level, sky, Gojo2.PURPLE_BIG.get(), 16.0F, 0.8F);
        }
        if (t == GBeats.NK_BLAST) {
            Vec3 g = c.point;
            sound(c.level, g, Gojo2.NUKE_BLAST.get(), 40.0F, 1.0F);
            crater(c, g, GBeats.NK_CRATER, 3.0);
            for (LivingEntity e : around(c, g.add(0, 6, 0), GBeats.NK_REACH)) {
                double d = e.position().distanceTo(g);
                Vec3 away = e.position().subtract(g);
                away = away.lengthSqr() < 1e-4 ? UP : away.normalize();
                float k = (float) Mth.clamp(1 - d / GBeats.NK_REACH, 0, 1);
                hurt(c, e, 120 + 380 * k, away.scale(2 + 4 * k).add(0, 1.2 + k, 0));
            }
        }
    }

    // ---- Unlimited Void

    static void domain(Cast c, int t) {
        if (t < GBeats.DV_CUT) {
            c.held = true;
            c.p.setDeltaMovement(Vec3.ZERO);
            c.p.hurtMarked = true;
        } else if (c.held) {
            c.held = false;
        }
        if (t == 0) {
            sound(c.level, c.p.position(), Gojo2.DOMAIN_SIGN.get(), 2.0F, 1.0F);
        }
        if (t == GBeats.DV_BANNER) {
            sound(c.level, c.p.position(), Gojo2.DOMAIN_OPEN.get(), 6.0F, 1.0F);
        }
        if (t == GBeats.DV_VOID) {
            sound(c.level, c.p.position(), Gojo2.DOMAIN_VOID.get(), 8.0F, 1.0F);
        }
        if (t == GBeats.DV_FLASH1) {
            // whoever is inside now is shut in with him
            for (LivingEntity e : around(c, c.feet, GBeats.DV_RADIUS)) {
                if (e.position().distanceTo(c.feet) <= GBeats.DV_RADIUS) {
                    c.hit.add(e.getId());
                }
            }
        }
        if (t >= GBeats.DV_FLASH1 && t < GBeats.DV_HOLD) {
            barrier(c, c.feet, GBeats.DV_RADIUS);
            hold(c, c.feet, GBeats.DV_RADIUS, t % 10 == 0, t >= GBeats.DV_VOID && t % 40 == 0 ? 2 : 0);
            if (t % 20 == 0) {
                c.p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, 2, false, false));
                c.p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false));
            }
        }
    }

    /**
     * The domain is closed: nobody shut in can leave, nobody outside can enter (both are thrown back from the
     * boundary), and nothing thrown passes through it. Only Gojo moves freely.
     */
    static void barrier(Cast c, Vec3 centre, double radius) {
        for (Entity e : c.level.getEntities(c.p, new AABB(centre, centre).inflate(radius + 4))) {
            if (e == c.p) {
                continue;
            }
            Vec3 off = e.position().subtract(centre);
            double d = off.length();
            Vec3 dir = d < 1e-4 ? c.flat() : off.scale(1 / d);
            if (e instanceof LivingEntity) {
                boolean in = c.hit.contains(e.getId());
                if (in && d > radius - 1.0) {
                    Vec3 back = centre.add(dir.scale(radius - 1.6));
                    e.teleportTo(back.x, Math.max(back.y, centre.y), back.z);
                    e.setDeltaMovement(dir.scale(-0.4));
                    e.hurtMarked = true;
                } else if (!in && d < radius + 0.6) {
                    Vec3 out = centre.add(dir.scale(radius + 1.2));
                    e.teleportTo(out.x, Math.max(out.y, e.getY()), out.z);
                    e.setDeltaMovement(dir.scale(0.8).add(0, 0.2, 0));
                    e.hurtMarked = true;
                }
            } else if (e instanceof Projectile && Math.abs(d - radius) < 1.5) {
                e.discard();
            }
        }
    }

    /** Everyone inside is flooded with the infinite: they can't move, can't act. */
    static void hold(Cast c, Vec3 centre, double radius, boolean refresh, float dmg) {
        for (LivingEntity e : around(c, centre, radius)) {
            if (e.position().distanceTo(centre) > radius) {
                continue;
            }
            e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
            e.hurtMarked = true;
            if (e instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
            if (refresh) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 9, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 9, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 9, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 128, false, false));
            }
            if (dmg > 0) {
                hurtAgain(c, e, dmg);
            }
        }
    }

    static void domain02(Cast c, int t) {
        if (t == 0) {
            sound(c.level, c.p.position(), Gojo2.DOMAIN_SIGN.get(), 2.0F, 1.2F);
        }
        if (t == GBeats.D2_OPEN) {
            sound(c.level, c.p.position(), Gojo2.DOMAIN_VOID.get(), 6.0F, 1.4F);
            for (LivingEntity e : around(c, c.feet, GBeats.D2_RADIUS)) {
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, false, false));
                hurtAgain(c, e, 6);
            }
        }
        if (t >= GBeats.D2_OPEN && t < GBeats.D2_OPEN + GBeats.D2_STUN) {
            hold(c, c.feet, GBeats.D2_RADIUS, t % 10 == GBeats.D2_OPEN % 10, 0);
        }
    }

    // ------------------------------------------------------------------------------------------------ the land

    static void sphere(Carver.Batch b, Vec3 c, double r) {
        int R = (int) Math.ceil(r);
        BlockPos o = BlockPos.containing(c);
        double r2 = r * r;
        for (int x = -R; x <= R; x++) {
            for (int y = -R; y <= R; y++) {
                for (int z = -R; z <= R; z++) {
                    if (x * x + y * y + z * z <= r2) {
                        b.erase(o.offset(x, y, z));
                    }
                }
            }
        }
    }

    static void erase(ServerLevel level, Vec3 c, double r) {
        Carver.Batch b = Carver.batch(level);
        sphere(b, c, r);
        b.go();
    }

    /** A crater: a bowl erased under the point, its floor and lip left scorched. */
    static void crater(Cast c, Vec3 o, double r, double scorch) {
        Carver.Batch b = Carver.batch(c.level);
        BlockPos centre = BlockPos.containing(o);
        int R = (int) Math.ceil(r + scorch);
        java.util.Random rnd = new java.util.Random(c.seed);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                for (int y = R; y >= -R; y--) {
                    // a bowl: flatter than a sphere, deeper at the middle
                    double d = Math.sqrt(x * x + z * z + (y < 0 ? y * y * 2.2 : y * y * 0.6));
                    BlockPos p = centre.offset(x, y, z);
                    if (d <= r) {
                        b.erase(p);
                    } else if (d <= r + scorch && rnd.nextFloat() < 0.85F) {
                        int pick = rnd.nextInt(10);
                        BlockState burnt = pick < 4 ? Blocks.COARSE_DIRT.defaultBlockState() : pick < 7 ? Blocks.TUFF.defaultBlockState()
                                : pick < 9 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
                        b.set(p, (l, s) -> s.isAir() || !s.isSolidRender(l, p) ? s : burnt);
                    }
                }
            }
        }
        b.go();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    static List<LivingEntity> around(Cast c, Vec3 at, double r) {
        return c.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                e -> e != c.p && e.isAlive() && e.getBoundingBox().getCenter().distanceTo(at) <= r + e.getBbWidth());
    }

    static List<LivingEntity> nearSegment(Cast c, Vec3 a, Vec3 b, double r) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(1e-6, ab.lengthSqr());
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(r + 2), x -> x != c.p && x.isAlive())) {
            Vec3 p = e.getBoundingBox().getCenter();
            double k = Mth.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
            if (p.distanceTo(a.add(ab.scale(k))) <= r + e.getBbWidth() * 0.5) {
                out.add(e);
            }
        }
        return out;
    }

    static DamageSource src(Cast c) {
        return c.level.damageSources().indirectMagic(c.p, c.p);
    }

    /** Hit once per cast. */
    static void hurt(Cast c, LivingEntity e, float amount, Vec3 push) {
        if (!c.hit.add(e.getId())) {
            return;
        }
        hurtAgain(c, e, amount);
        if (push != null) {
            e.setDeltaMovement(e.getDeltaMovement().add(push));
            e.hurtMarked = true;
        }
    }

    static void hurtAgain(Cast c, LivingEntity e, float amount) {
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
            return;
        }
        e.invulnerableTime = 0;
        e.hurt(src(c), amount);
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** No harm comes to Gojo while a cutscene plays. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && busy(p)) {
            e.setCanceled(true);
        }
    }

    /** The build server's self-test: forget every cast. */
    public static void testReset(ServerPlayer p) {
        ACTIVE.removeIf(c -> {
            if (c.p == p) {
                release(c);
                return true;
            }
            return false;
        });
    }

    public static void clearAll() {
        ACTIVE.clear();
        Carver.clear();
    }

    static MinecraftServer server(Cast c) {
        return c.level.getServer();
    }
}
