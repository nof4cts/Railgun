package com.teengojo;

import com.jjk.mod.Balance;
import com.jjk.mod.Energy;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Teen Gojo: Gojo before the awakening. Six moves on keys 1-6 - Lapse: Blue, Reversal: Red, Blink, Infinity (five
 * seconds only), Hollow Purple: Unrefined and the 0.2-Second Domain - each a weaker, shorter version of the full
 * Gojo's, paid for in cursed energy and on its own cooldown. Strong for a regular player, not a one-shot machine.
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class TeenGojo {
    static final Logger LOG = LogUtils.getLogger();
    public static final String NAME = "TEEN_GOJO";

    public static final int BLUE = 0, RED = 1, BLINK = 2, INFINITY = 3, PURPLE = 4, DOMAIN = 5;
    public static final String[] MOVES = {"Lapse: Blue", "Reversal: Red", "Blink", "Infinity (5s)", "Hollow Purple: Unrefined", "0.2-Second Domain"};
    /** Cursed energy (of 1000) and cooldown (ticks) per move. */
    static final int[] COST = {60, 70, 40, 120, 260, 300};
    public static final int[] COOLDOWN = {140, 120, 80, 500, 800, 1500};

    // tuning
    static final double BLUE_AHEAD = 7, BLUE_PULL_R = 6, BLUE_HIT_R = 2.5;
    static final int BLUE_TICKS = 40;
    static final int RED_CHARGE = 8;
    static final double RED_SPEED = 2.5, RED_RANGE = 28, RED_R = 3.5;
    static final float RED_DMG = 9;
    static final double BLINK_RANGE = 12;
    static final int INFINITY_TICKS = 100;
    static final int PURPLE_CHARGE = 24;
    static final double PURPLE_SPEED = 1.8, PURPLE_RANGE = 40, PURPLE_R = 1.6;
    static final float PURPLE_DMG = 16;
    static final double DOMAIN_R = 10;
    static final int DOMAIN_STUN = 60;

    private static Sorcerer teen;
    private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> INFINITY_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> BUSY_UNTIL = new HashMap<>();
    private static final Map<Entity, Long> FROZEN = new HashMap<>();
    private static final Map<UUID, Long> RIPPLED = new HashMap<>();
    private static final List<Job> JOBS = new ArrayList<>();

    private TeenGojo() {}

    /** The TEEN_GOJO sorcerer, if this jar's list has one. */
    static Sorcerer teen() {
        if (teen == null) {
            try {
                teen = Sorcerer.valueOf(NAME);
            } catch (Throwable t) {
                return null;
            }
        }
        return teen;
    }

    public static boolean is(Player p) {
        Sorcerer t = teen();
        return t != null && p != null && Sorcerers.get(p) == t;
    }

    static long now(ServerLevel l) {
        return l.getGameTime();
    }

    // ------------------------------------------------------------------------------------------------ keys

    static void key(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= MOVES.length || !p.isAlive() || p.isSpectator()) {
            return;
        }
        ServerLevel l = p.serverLevel();
        long now = now(l);
        if (Balance.confiscated(p)) {
            p.displayClientMessage(Component.literal("Your cursed technique is confiscated.").withStyle(ChatFormatting.RED), true);
            return;
        }
        Long busy = BUSY_UNTIL.get(p.getUUID());
        if (busy != null && busy > now) {
            return;
        }
        boolean creative = p.getAbilities().instabuild;
        long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[MOVES.length]);
        if (!creative && cd[slot] > now) {
            p.displayClientMessage(Component.literal(MOVES[slot] + " - ready in " + (cd[slot] - now + 19) / 20 + "s").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!Energy.has(p, COST[slot])) {
            return;
        }
        Energy.spend(p, COST[slot]);
        if (!creative) {
            cd[slot] = now + COOLDOWN[slot];
        }
        TeenNet.to(p, TeenNet.Fx.of(TeenNet.COOLDOWN, p.getId(), p.position(), Vec3.ZERO, slot, creative ? 0 : COOLDOWN[slot]));
        switch (slot) {
            case BLUE -> blue(p);
            case RED -> red(p);
            case BLINK -> blink(p);
            case INFINITY -> infinity(p);
            case PURPLE -> purple(p);
            case DOMAIN -> domain(p);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the moves

    static SoundEvent sound(String name) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation("gojo", name));
    }

    static void play(ServerLevel l, Vec3 at, String name, float vol, float pitch) {
        l.playSound(null, at.x, at.y, at.z, sound(name), SoundSource.PLAYERS, vol, pitch);
    }

    /** Where he is looking, stopped by the first block, at most {@code range} away. */
    static Vec3 sight(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    static List<LivingEntity> around(ServerPlayer p, Vec3 at, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
            if (e.getBoundingBox().getCenter().distanceTo(at) <= r + e.getBbWidth() * 0.5) {
                out.add(e);
            }
        }
        return out;
    }

    static void hurt(ServerPlayer p, LivingEntity e, float dmg) {
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().indirectMagic(p, p), dmg);
    }

    static void push(LivingEntity e, Vec3 v) {
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hurtMarked = true;
    }

    /** Lapse: Blue - a point of attraction a few blocks ahead; everything near is dragged into it, then it caves in. */
    static void blue(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 hit = sight(p, BLUE_AHEAD);
        Vec3 at = eye.add(hit.subtract(eye).scale(0.9));
        play(l, at, "voice_blue", 2.0F, 1.0F);
        play(l, at, "blue_charge", 1.6F, 1.1F);
        TeenNet.around(l, at, TeenNet.Fx.of(TeenNet.BLUE, p.getId(), at, Vec3.ZERO, BLUE_TICKS, 0));
        TeenNet.around(l, at, TeenNet.Fx.of(TeenNet.RED_CHARGE, p.getId(), at, Vec3.ZERO, -1, 0));
        long start = now(l);
        JOBS.add(new Job(p, start, start + BLUE_TICKS) {
            @Override
            void tick(long t) {
                int age = (int) (t - start);
                for (LivingEntity e : around(p, at, BLUE_PULL_R)) {
                    Vec3 d = at.subtract(e.getBoundingBox().getCenter());
                    double dist = d.length();
                    if (dist > 0.6) {
                        Vec3 v = d.normalize().scale(Math.min(0.32, 0.12 + 0.6 / dist));
                        e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(v));
                        e.hurtMarked = true;
                        e.fallDistance = 0;
                    }
                    if (age % 10 == 0 && dist <= BLUE_HIT_R) {
                        hurt(p, e, 1.5F);
                    }
                }
                if (age % 12 == 0) {
                    play(l, at, "blue_drag", 0.8F, 1.3F);
                }
            }

            @Override
            void end() {
                for (LivingEntity e : around(p, at, 3.0)) {
                    hurt(p, e, 5.0F);
                    push(e, new Vec3(0, 0.35, 0));
                }
                play(l, at, "blue_crush", 1.8F, 1.2F);
                TeenNet.around(l, at, TeenNet.Fx.of(TeenNet.BLUE_END, p.getId(), at, Vec3.ZERO, 0, 0));
            }
        });
    }

    /** Reversal: Red - a point of red at the fingertip, flicked; it bursts on whatever it meets. */
    static void red(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        play(l, p.getEyePosition(), "voice_red", 2.0F, 1.0F);
        play(l, p.getEyePosition(), "red_charge", 1.2F, 1.2F);
        TeenNet.around(l, p.position(), TeenNet.Fx.of(TeenNet.RED_CHARGE, p.getId(), p.position(), Vec3.ZERO, RED_CHARGE, 0));
        long start = now(l);
        BUSY_UNTIL.put(p.getUUID(), start + RED_CHARGE);
        JOBS.add(new Job(p, start, start + RED_CHARGE) {
            @Override
            void tick(long t) {
            }

            @Override
            void end() {
                Vec3 eye = p.getEyePosition();
                Vec3 dir = p.getLookAngle().normalize();
                Vec3 from = eye.add(dir.scale(0.8));
                // what it meets first: a block, or a living thing near the line
                Vec3 stop = sight(p, RED_RANGE);
                double dist = from.distanceTo(stop);
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(from, stop).inflate(1.0),
                        e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
                    var clip = e.getBoundingBox().inflate(0.5).clip(from, stop);
                    if (clip.isPresent()) {
                        dist = Math.min(dist, from.distanceTo(clip.get()));
                    }
                }
                Vec3 at = from.add(dir.scale(dist));
                play(l, from, "red_fire", 1.6F, 1.25F);
                TeenNet.around(l, from, TeenNet.Fx.of(TeenNet.RED_BOLT, p.getId(), from, dir, (float) RED_SPEED, (float) dist));
                long land = now(l) + Math.max(1, (long) Math.ceil(dist / RED_SPEED));
                JOBS.add(new Job(p, now(l), land) {
                    @Override
                    void tick(long t) {
                    }

                    @Override
                    void end() {
                        for (LivingEntity e : around(p, at, RED_R)) {
                            double d = e.getBoundingBox().getCenter().distanceTo(at);
                            float k = (float) Mth.clamp(1 - d / RED_R * 0.55, 0.45, 1);
                            hurt(p, e, RED_DMG * k);
                            Vec3 away = e.position().subtract(at);
                            away = new Vec3(away.x, 0, away.z);
                            away = away.lengthSqr() < 1e-4 ? dir : away.normalize();
                            push(e, away.scale(1.4 * k).add(0, 0.35, 0));
                        }
                        play(l, at, "red_impact", 1.6F, 1.2F);
                        TeenNet.around(l, at, TeenNet.Fx.of(TeenNet.RED_BLAST, p.getId(), at, dir, (float) RED_R, 0));
                    }
                });
            }
        });
    }

    /** Blink - the Teleport, shorter: to where he looks, as far as twelve blocks. */
    static void blink(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 from = p.position();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 hit = sight(p, BLINK_RANGE);
        double reach = Math.max(0, eye.distanceTo(hit) - 0.7);
        Vec3 to = null;
        for (double d = reach; d >= 0; d -= 0.5) {
            Vec3 c = eye.add(dir.scale(d)).subtract(0, p.getEyeHeight(), 0);
            if (l.noCollision(p, p.getBoundingBox().move(c.subtract(from)))) {
                to = c;
                break;
            }
        }
        if (to == null || to.distanceTo(from) < 1.0) {
            return;
        }
        p.connection.teleport(to.x, to.y, to.z, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.hurtMarked = true;
        play(l, from, "infinity_ripple", 1.0F, 1.6F);
        l.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.5F);
        TeenNet.around(l, to, TeenNet.Fx.of(TeenNet.BLINK, p.getId(), from, to.subtract(from), 0, 0));
    }

    /** Infinity - five seconds of it: hits stop short of him, arrows hang in the air and drop. */
    static void infinity(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        INFINITY_UNTIL.put(p.getUUID(), now(l) + INFINITY_TICKS);
        play(l, p.position(), "infinity_on", 1.4F, 1.15F);
        TeenNet.around(l, p.position(), TeenNet.Fx.of(TeenNet.INFINITY, p.getId(), p.position(), Vec3.ZERO, INFINITY_TICKS, 0));
    }

    public static boolean infinite(Entity e) {
        if (!(e instanceof ServerPlayer p)) {
            return false;
        }
        Long until = INFINITY_UNTIL.get(p.getUUID());
        return until != null && until > now(p.serverLevel()) && is(p);
    }

    /** Hollow Purple: Unrefined - Red in one hand, Blue in the other, pressed together, and let go: a small Purple. */
    static void purple(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        long start = now(l);
        BUSY_UNTIL.put(p.getUUID(), start + PURPLE_CHARGE);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PURPLE_CHARGE, 3, false, false, false));
        play(l, p.position(), "red_charge", 1.2F, 1.0F);
        play(l, p.position(), "blue_charge", 1.2F, 1.0F);
        TeenNet.around(l, p.position(), TeenNet.Fx.of(TeenNet.PURPLE_CHARGE, p.getId(), p.position(), Vec3.ZERO, PURPLE_CHARGE, 0));
        JOBS.add(new Job(p, start, start + PURPLE_CHARGE) {
            @Override
            void tick(long t) {
                int age = (int) (t - start);
                if (age == 4) {
                    play(l, p.position(), "voice_kyoshiki", 2.0F, 1.0F);
                }
                if (age == PURPLE_CHARGE - 8) {
                    play(l, p.position(), "purple_merge", 1.4F, 1.2F);
                }
                if (age == PURPLE_CHARGE - 3) {
                    play(l, p.position(), "voice_murasaki", 2.0F, 1.0F);
                }
            }

            @Override
            void end() {
                Vec3 dir = p.getLookAngle().normalize();
                Vec3 from = p.getEyePosition().add(dir.scale(1.6));
                // it stops at the first solid block
                Vec3 stop = from.add(dir.scale(PURPLE_RANGE));
                BlockHitResult bh = l.clip(new ClipContext(from, stop, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                double range = bh.getType() == HitResult.Type.MISS ? PURPLE_RANGE : from.distanceTo(bh.getLocation());
                play(l, from, "purple_fire", 2.0F, 1.2F);
                TeenNet.around(l, from, TeenNet.Fx.of(TeenNet.PURPLE_SHOT, p.getId(), from, dir, (float) PURPLE_SPEED, (float) range));
                long fired = now(l);
                Set<Integer> hit = new HashSet<>();
                JOBS.add(new Job(p, fired, fired + (long) Math.ceil(range / PURPLE_SPEED)) {
                    @Override
                    void tick(long t) {
                        double d0 = Math.min(range, (t - 1 - fired) * PURPLE_SPEED), d1 = Math.min(range, (t - fired) * PURPLE_SPEED);
                        Vec3 a = from.add(dir.scale(Math.max(0, d0))), b = from.add(dir.scale(d1));
                        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(PURPLE_R + 1),
                                e -> e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
                            if (segment(e.getBoundingBox().getCenter(), a, b) <= PURPLE_R + e.getBbWidth() * 0.5 && hit.add(e.getId())) {
                                hurt(p, e, PURPLE_DMG);
                                push(e, dir.scale(1.1).add(0, 0.3, 0));
                            }
                        }
                    }

                    @Override
                    void end() {
                        Vec3 at = from.add(dir.scale(range));
                        play(l, at, "red_impact", 1.4F, 0.8F);
                        TeenNet.around(l, at, TeenNet.Fx.of(TeenNet.PURPLE_END, p.getId(), at, dir, 0, 0));
                    }
                });
            }
        });
    }

    /** 0.2-Second Domain - Unlimited Void opened and closed in a blink: everyone near is left frozen, overloaded. */
    static void domain(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 at = p.position();
        play(l, at, "domain_open", 2.0F, 1.3F);
        play(l, at, "voice_muryokusho", 2.2F, 1.0F);
        TeenNet.around(l, at, TeenNet.Fx.of(TeenNet.DOMAIN, p.getId(), at, Vec3.ZERO, (float) DOMAIN_R, 0));
        long until = now(l) + DOMAIN_STUN;
        for (LivingEntity e : around(p, at.add(0, 1, 0), DOMAIN_R)) {
            FROZEN.put(e, until);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DOMAIN_STUN, 7, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DOMAIN_STUN, 2, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, DOMAIN_STUN, 2, false, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.JUMP, DOMAIN_STUN, 250, false, false, false));
            hurt(p, e, 3.0F);
            if (e instanceof ServerPlayer sp) {
                TeenNet.to(sp, TeenNet.Fx.of(TeenNet.FROZEN, p.getId(), at, Vec3.ZERO, DOMAIN_STUN, 0));
                sp.displayClientMessage(Component.literal("0.2-Second Domain - your mind is overloaded").withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    static double segment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-9) {
            return p.distanceTo(a);
        }
        double k = Mth.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
        return p.distanceTo(a.add(ab.scale(k)));
    }

    // ------------------------------------------------------------------------------------------------ the world

    abstract static class Job {
        final ServerPlayer p;
        final long start, until;

        Job(ServerPlayer p, long start, long until) {
            this.p = p;
            this.start = start;
            this.until = until;
        }

        abstract void tick(long t);

        abstract void end();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        // moves in progress
        List<Job> run = new ArrayList<>(JOBS);
        for (Job j : run) {
            try {
                if (j.p.isRemoved() || !j.p.isAlive()) {
                    JOBS.remove(j);
                    continue;
                }
                long t = now(j.p.serverLevel());
                if (t >= j.until) {
                    JOBS.remove(j);
                    j.end();
                } else {
                    j.tick(t);
                }
            } catch (Throwable ex) {
                JOBS.remove(j);
                LOG.error("[teen gojo] move failed", ex);
            }
        }
        // Infinity: anything thrown at him slows to nothing on the way in and drops
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (!infinite(p)) {
                continue;
            }
            Vec3 c = p.getBoundingBox().getCenter();
            for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.5), x -> x.getOwner() != p)) {
                double d = pr.position().distanceTo(c);
                Vec3 v = pr.getDeltaMovement();
                if (v.dot(c.subtract(pr.position())) > 0) {
                    pr.setDeltaMovement(d < 1.8 ? Vec3.ZERO : v.scale(0.25));
                    pr.hurtMarked = true;
                    ripple(p, pr.position());
                }
            }
        }
        // the domain's frozen
        Iterator<Map.Entry<Entity, Long>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Long> f = it.next();
            Entity x = f.getKey();
            if (x.isRemoved() || !x.isAlive() || now((ServerLevel) x.level()) >= f.getValue()) {
                it.remove();
                continue;
            }
            Vec3 v = x.getDeltaMovement();
            x.setDeltaMovement(0, Math.min(v.y, 0), 0);
            if (x instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
        }
    }

    static void ripple(ServerPlayer p, Vec3 at) {
        long now = now(p.serverLevel());
        Long last = RIPPLED.get(p.getUUID());
        if (last != null && now - last < 3) {
            return;
        }
        RIPPLED.put(p.getUUID(), now);
        play(p.serverLevel(), at, "infinity_ripple", 0.8F, 1.3F);
        TeenNet.around(p.serverLevel(), at, TeenNet.Fx.of(TeenNet.RIPPLE, p.getId(), at, at.subtract(p.getBoundingBox().getCenter()), 0, 0));
    }

    /** Infinity stops every hit that comes from something; the frozen can't strike back. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        Entity src = e.getSource().getEntity(), direct = e.getSource().getDirectEntity();
        Entity by = direct != null ? direct : src;
        if (by != null && by != e.getEntity() && infinite(e.getEntity())) {
            e.setCanceled(true);
            ServerPlayer p = (ServerPlayer) e.getEntity();
            Vec3 c = p.getBoundingBox().getCenter();
            Vec3 from = by.getBoundingBox().getCenter().subtract(c);
            ripple(p, c.add(from.lengthSqr() < 1e-4 ? p.getLookAngle() : from.normalize().scale(0.9)));
            return;
        }
        if (src != null && FROZEN.containsKey(src)) {
            Long until = FROZEN.get(src);
            if (until != null && until > src.level().getGameTime()) {
                e.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        COOLDOWNS.remove(id);
        INFINITY_UNTIL.remove(id);
        BUSY_UNTIL.remove(id);
        RIPPLED.remove(id);
        FROZEN.remove(e.getEntity());
        JOBS.removeIf(j -> j.p.getUUID().equals(id));
    }
}
