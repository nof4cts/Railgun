package com.sbr;

import com.jjk.mod.Balance;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Steel Ball Run's two styles in the Stand tab: Johnny Joestar (Tusk ACT1-4) and Gyro Zeppeli (the Spin). This class
 * holds what they share: key dispatch, cooldowns, projectiles flown tick by tick, and the lasting effects (Tusk's
 * chasing holes, Infinite Rotation).
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class Sbr {
    static final Logger LOG = LogUtils.getLogger();
    private static final Map<String, Sorcerer> STYLES = new HashMap<>();
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
    static final Map<UUID, Long> BUSY = new HashMap<>();
    static final List<Job> JOBS = new ArrayList<>();
    /** Infinite Rotation: victim -> [until, anchor x, y, z, by entity id]. */
    static final Map<Entity, double[]> SPINNING = new HashMap<>();

    private Sbr() {}

    static Sorcerer style(String name) {
        return STYLES.computeIfAbsent(name, n -> {
            try {
                return Sorcerer.valueOf(n);
            } catch (Throwable t) {
                return null;
            }
        });
    }

    static boolean is(Player p, String name) {
        Sorcerer s = style(name);
        return s != null && p != null && Sorcerers.get(p) == s;
    }

    /** A key press: true if this is Johnny or Gyro (and so handled here). */
    public static boolean key(ServerPlayer p, int slot) {
        boolean johnny = is(p, Johnny.NAME), gyro = !johnny && is(p, Gyro.NAME);
        if (!johnny && !gyro) {
            return false;
        }
        try {
            if (!p.isAlive() || p.isSpectator()) {
                return true;
            }
            if (Balance.confiscated(p)) {
                p.displayClientMessage(Component.literal("Your Stand is suppressed.").withStyle(ChatFormatting.RED), true);
                return true;
            }
            Long busy = BUSY.get(p.getUUID());
            if (busy != null && busy > now(p)) {
                return true;
            }
            if (johnny) {
                Johnny.key(p, slot);
            } else {
                Gyro.key(p, slot);
            }
        } catch (Throwable t) {
            LOG.error("[sbr] key failed", t);
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    static long now(Entity e) {
        return e.level().getGameTime();
    }

    /** Ready? If not, says how long. Creative players have no cooldowns. */
    static boolean ready(ServerPlayer p, int slot, String name) {
        if (p.getAbilities().instabuild) {
            return true;
        }
        long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[8]);
        long now = now(p);
        if (cd[slot] > now) {
            p.displayClientMessage(Component.literal(name + " - ready in " + (cd[slot] - now + 19) / 20 + "s").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        return true;
    }

    static void cooldown(ServerPlayer p, int slot, int ticks) {
        if (p.getAbilities().instabuild) {
            ticks = 0;
        }
        long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[8]);
        cd[slot] = now(p) + ticks;
        SbrNet.to(p, SbrNet.Fx.of(SbrNet.COOLDOWN, p.getId(), p.position(), Vec3.ZERO, slot, ticks));
    }

    static void say(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }

    static void play(ServerLevel l, Vec3 at, String ns, String name, float vol, float pitch) {
        SoundEvent s = SoundEvent.createVariableRangeEvent(new ResourceLocation(ns, name));
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static void play(ServerLevel l, Vec3 at, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static Vec3 sight(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    static boolean target(Entity e, Entity owner) {
        return e instanceof LivingEntity && e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != owner.getVehicle();
    }

    static List<LivingEntity> around(ServerPlayer p, Vec3 at, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), e -> target(e, p))) {
            if (e.getBoundingBox().getCenter().distanceTo(at) <= r + e.getBbWidth() * 0.5) {
                out.add(e);
            }
        }
        return out;
    }

    static void hurt(ServerPlayer p, LivingEntity e, float dmg) {
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().playerAttack(p), dmg);
    }

    static void push(LivingEntity e, Vec3 v) {
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hurtMarked = true;
    }

    static double segment(Vec3 q, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-9) {
            return q.distanceTo(a);
        }
        double k = Mth.clamp(q.subtract(a).dot(ab) / len2, 0, 1);
        return q.distanceTo(a.add(ab.scale(k)));
    }

    /** On a horse at a gallop: the Golden Spin's natural pace. */
    static boolean galloping(ServerPlayer p) {
        if (p.getVehicle() instanceof AbstractHorse h) {
            double v = Math.hypot(h.getX() - h.xo, h.getZ() - h.zo);
            return v > 0.22;
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ projectiles

    /**
     * A shot flown tick by tick from {@code from} along {@code dir}: it hits the first living thing within
     * {@code radius} of its path (or every one, if {@code pierce}) and stops at the first block. {@code onHit} gets
     * each target; {@code onEnd} gets where it stopped.
     */
    static double shoot(ServerPlayer p, Vec3 from, Vec3 dir, double speed, double range, double radius, boolean pierce,
            Consumer<LivingEntity> onHit, Consumer<Vec3> onEnd) {
        ServerLevel l = p.serverLevel();
        Vec3 stop = from.add(dir.scale(range));
        BlockHitResult bh = l.clip(new ClipContext(from, stop, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = bh.getType() == HitResult.Type.MISS ? range : from.distanceTo(bh.getLocation());
        long start = now(p);
        Set<Integer> hit = new HashSet<>();
        double[] end = {reach};
        JOBS.add(new Job(p, start, start + (long) Math.ceil(reach / speed) + 1) {
            @Override
            void tick(long t) {
                double d0 = Math.min(end[0], (t - 1 - start) * speed), d1 = Math.min(end[0], (t - start) * speed);
                Vec3 a = from.add(dir.scale(Math.max(0, d0))), b = from.add(dir.scale(d1));
                LivingEntity first = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(radius + 1), e -> target(e, p))) {
                    if (hit.contains(e.getId())) {
                        continue;
                    }
                    Vec3 c = e.getBoundingBox().getCenter();
                    if (segment(c, a, b) <= radius + e.getBbWidth() * 0.5 + 0.3) {
                        if (pierce) {
                            hit.add(e.getId());
                            onHit.accept(e);
                        } else if (c.distanceTo(a) < best) {
                            best = c.distanceTo(a);
                            first = e;
                        }
                    }
                }
                if (first != null) {
                    hit.add(first.getId());
                    onHit.accept(first);
                    // it stops in them: the effect ends there
                    end[0] = Math.max(0, from.distanceTo(first.getBoundingBox().getCenter()));
                    until = t;
                }
            }

            @Override
            void end() {
                onEnd.accept(from.add(dir.scale(end[0])));
            }
        });
        return reach;
    }

    // ------------------------------------------------------------------------------------------------ the world

    abstract static class Job {
        final ServerPlayer p;
        final long start;
        long until;

        Job(ServerPlayer p, long start, long until) {
            this.p = p;
            this.start = start;
            this.until = until;
        }

        abstract void tick(long t);

        abstract void end();
    }

    /** Infinite Rotation on {@code e}: it spins where it was struck, dragged back to that spot, torn at, for {@code ticks}. */
    static void spin(ServerPlayer by, LivingEntity e, Vec3 anchor, int ticks) {
        SPINNING.put(e, new double[]{now(e) + ticks, anchor.x, anchor.y, anchor.z, by.getId()});
        SbrNet.around(by.serverLevel(), anchor, SbrNet.Fx.of(SbrNet.ROTATION, e.getId(), anchor, Vec3.ZERO, ticks, 0));
        if (e instanceof ServerPlayer sp) {
            say(sp, "Infinite Rotation - you are spinning in place", ChatFormatting.GOLD);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        for (Job j : new ArrayList<>(JOBS)) {
            try {
                if (j.p.isRemoved() || !j.p.isAlive()) {
                    JOBS.remove(j);
                    continue;
                }
                long t = now(j.p);
                if (t >= j.until) {
                    JOBS.remove(j);
                    j.end();
                } else {
                    j.tick(t);
                    if (t >= j.until) {
                        JOBS.remove(j);
                        j.end();
                    }
                }
            } catch (Throwable ex) {
                JOBS.remove(j);
                LOG.error("[sbr] move failed", ex);
            }
        }
        // nails grow back: keep Johnny's count on his screen
        if (e.getServer().getTickCount() % 10 == 0) {
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
                if (is(p, Johnny.NAME)) {
                    Johnny.sync(p);
                }
            }
        }
        Iterator<Map.Entry<Entity, double[]>> it = SPINNING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, double[]> s = it.next();
            Entity x = s.getKey();
            double[] v = s.getValue();
            if (x.isRemoved() || !x.isAlive() || now(x) >= v[0]) {
                it.remove();
                continue;
            }
            Vec3 anchor = new Vec3(v[1], v[2], v[3]);
            Vec3 d = anchor.subtract(x.position());
            // drawn back to where it was struck; turned round and round
            x.setDeltaMovement(d.x * 0.35, Math.max(-0.2, Math.min(0.2, d.y * 0.35)), d.z * 0.35);
            x.hurtMarked = true;
            x.setYRot(x.getYRot() + 40);
            x.setYHeadRot(x.getYRot());
            long left = (long) v[0] - now(x);
            if (left % 10 == 0 && x instanceof LivingEntity le) {
                Entity by = x.level().getEntity((int) v[4]);
                le.invulnerableTime = 0;
                if (by instanceof ServerPlayer bp) {
                    le.hurt(bp.damageSources().playerAttack(bp), 2.0F);
                } else {
                    le.hurt(le.damageSources().magic(), 2.0F);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        COOLDOWNS.remove(id);
        BUSY.remove(id);
        SPINNING.remove(e.getEntity());
        JOBS.removeIf(j -> j.p.getUUID().equals(id));
        Johnny.forget(id);
        Gyro.forget(id);
    }
}
