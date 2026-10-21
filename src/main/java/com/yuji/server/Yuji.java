package com.yuji.server;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.yuji.Y;
import com.yuji.common.Anchors;
import com.yuji.common.Beat;
import com.yuji.common.Ctx;
import com.yuji.common.Script;
import com.yuji.common.YMove;
import com.yuji.net.YNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Modulo Yuji on the server: his keys, the move runner that plays each move's timeline tick by tick (damage,
 * destruction, movement), his domain and Overdrive. What everyone sees is played by the clients from the same
 * timeline, sent in one Cast packet.
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class Yuji {
    private static final Random RNG = new Random();
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, long[]> READY = new HashMap<>();
    private static final Map<UUID, int[]> TOTAL = new HashMap<>();
    private static final Map<UUID, Domain> DOMAINS = new HashMap<>();
    private static final Map<UUID, Long> OVERDRIVE = new HashMap<>();
    private static final Map<UUID, Long> SAFE_UNTIL = new HashMap<>();

    private Yuji() {}

    static final class Run {
        final ServerPlayer p;
        final YMove m;
        final Ctx c;
        final List<Beat> beats;
        final long start;
        final Destroy.Journal journal;
        int idx;
        Beat dash;
        long dashStart;
        Vec3 dashFrom;
        final List<Object[]> bolts = new ArrayList<>();   // {impactTick(Long), Beat}

        Run(ServerPlayer p, YMove m, Ctx c, List<Beat> beats, long start, Destroy.Journal j) {
            this.p = p;
            this.m = m;
            this.c = c;
            this.beats = beats;
            this.start = start;
            this.journal = j;
        }
    }

    static final class Domain {
        final ServerLevel level;
        final Vec3 centre;
        final float radius;
        final long until;

        Domain(ServerLevel level, Vec3 centre, float radius, long until) {
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.until = until;
        }
    }

    // ------------------------------------------------------------------ keys
    public static boolean is(Player p) {
        return Sorcerers.is(p, Sorcerer.YUJI);
    }

    /** A move key (slot 0-15). True when Yuji took it. */
    public static boolean key(ServerPlayer p, int slot) {
        if (!is(p)) return false;
        if (slot < 0 || slot > 15) return true;
        boolean sneak = p.isShiftKeyDown();
        long now = p.serverLevel().getGameTime();
        YMove m = YMove.of(slot, sneak);
        if (m == null) return true;
        boolean inDomain = domainOf(p) != null;
        if (slot == 12 && inDomain) m = YMove.DOMAIN_FINISHER;
        else if (m == YMove.DOMAIN_FINISHER) m = YMove.DOMAIN;
        if (m == YMove.DOMAIN_BARRAGE && !inDomain) {
            tell(p, "Domain Barrage only works inside your domain");
            return true;
        }
        if (m == YMove.DOMAIN_APOCALYPSE && !overdrive(p)) {
            tell(p, "Domain Apocalypse needs Modulo Overdrive");
            return true;
        }
        if (m == YMove.OVERDRIVE && overdrive(p)) {
            // toggle off
            OVERDRIVE.remove(p.getUUID());
            p.removeEffect(MobEffects.MOVEMENT_SPEED);
            YNet.near(p.serverLevel(), p.position(), 256, new YNet.Aura(p.getId(), 0));
            state(p);
            return true;
        }
        Run run = RUNS.get(p.getUUID());
        if (run != null) return true;   // busy: the move in progress finishes first
        long ready = ready(p)[m.ordinal()];
        if (now < ready) {
            tell(p, m.label() + " ready in " + String.format("%.1f", (ready - now) / 20.0) + "s");
            return true;
        }
        int cost = (int) Math.round(m.cost * Y.energyScale);
        if (cost > 0 && !Energy.has(p, cost)) return true;
        if (cost > 0) Energy.spend(p, cost);
        cast(p, m);
        return true;
    }

    private static void tell(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.RED), true);
    }

    private static long[] ready(ServerPlayer p) {
        return READY.computeIfAbsent(p.getUUID(), u -> new long[YMove.values().length]);
    }

    private static int[] total(ServerPlayer p) {
        return TOTAL.computeIfAbsent(p.getUUID(), u -> new int[YMove.values().length]);
    }

    public static boolean overdrive(ServerPlayer p) {
        Long u = OVERDRIVE.get(p.getUUID());
        return u != null && u > p.serverLevel().getGameTime();
    }

    static Domain domainOf(ServerPlayer p) {
        Domain d = DOMAINS.get(p.getUUID());
        if (d == null) return null;
        if (d.level != p.serverLevel() || d.until <= p.serverLevel().getGameTime()) return null;
        return d;
    }

    // ------------------------------------------------------------------ casting
    public static void cast(ServerPlayer p, YMove m) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Domain dom = domainOf(p);
        LivingEntity target = pick(p, eye, look, dom);
        Vec3 point;
        if (target != null) {
            point = Anchors.centre(target);
        } else {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(60)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            point = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(24)) : hit.getLocation();
        }
        Vec3 around = dom != null ? dom.centre : point;
        double reach = dom != null ? dom.radius : 40;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(around, around).inflate(reach),
                e -> e != p && e.isAlive() && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) && e.position().distanceTo(around) <= reach);
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(around)));
        if (target != null) {
            near.remove(target);
            near.add(0, target);
        }
        int[] targets = new int[Math.min(8, near.size())];
        for (int i = 0; i < targets.length; i++) targets[i] = near.get(i).getId();

        boolean od = overdrive(p);
        Vec3 domainCentre = dom != null ? dom.centre : p.position();
        float domainRadius = dom != null ? dom.radius : Y.domainRadius;
        float scale = (float) Y.destructionScale;
        Ctx c = new Ctx(m, RNG.nextLong(), p.position(), look, point, target != null ? target.getId() : -1, targets,
                domainCentre, domainRadius, (float) Math.min(Y.maxRadius, Y.worldEnderRadius * scale),
                (float) (Y.splitterLength * scale), (float) Y.dismantleLength, od, scale);
        List<Beat> beats = Script.build(c);
        Run run = new Run(p, m, c, beats, now, Destroy.journal(level));
        RUNS.put(p.getUUID(), run);

        int cd = (int) Math.round(m.cooldown * Y.cooldownScale * (od ? 0.5 : 1.0));
        ready(p)[m.ordinal()] = now + cd;
        total(p)[m.ordinal()] = cd;
        YNet.near(level, p.position(), 512, new YNet.Cast(m.ordinal(), p.getId(), now, c.origin(), c.dir(), c.point(), c.seed(),
                c.targetId(), c.targets(), c.domainCentre(), c.domainRadius(), c.worldEnder(), c.splitter(), c.dismantle(),
                c.overdrive(), c.destructionScale()));
        state(p);
        p.fallDistance = 0;
        Y.LOG.info("[yuji] {} cast {} (target {}, {} nearby)", p.getGameProfile().getName(), m, target == null ? "none" : target.getName().getString(), targets.length);
        step(run, now);   // beats on tick 0 happen now
    }

    /** What he is looking at: the closest living thing near his line of sight, out to 60 blocks. */
    private static LivingEntity pick(ServerPlayer p, Vec3 eye, Vec3 look, Domain dom) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(60),
                e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3 to = Anchors.centre(e).subtract(eye);
            double d = to.length();
            if (d > 60 || d < 0.01) continue;
            double cos = to.dot(look) / d;
            double tol = Math.max(0.9, 1.0 - 2.0 / d);   // wider cone close by
            if (cos < Math.min(0.97, tol) && d > 4) continue;
            if (cos < 0.3) continue;
            double score = d * (2.0 - cos);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best == null && dom != null) {
            // inside his domain everything is in reach
            for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(dom.centre, dom.centre).inflate(dom.radius),
                    e -> e != p && e.isAlive() && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
                double d = e.distanceToSqr(p);
                if (d < bestScore) {
                    bestScore = d;
                    best = e;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the runner
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator();
        while (it.hasNext()) {
            Run r = it.next().getValue();
            ServerPlayer p = r.p;
            if (p.isRemoved() || !p.isAlive() || !is(p)) {
                it.remove();
                continue;
            }
            long now = p.serverLevel().getGameTime();
            try {
                step(r, now);
            } catch (Throwable t) {
                Y.LOG.error("[yuji] move {} failed", r.m, t);
                it.remove();
                continue;
            }
            p.fallDistance = 0;
            if (now - r.start >= r.m.end && r.bolts.isEmpty() && r.idx >= r.beats.size()) {
                it.remove();
                SAFE_UNTIL.put(p.getUUID(), now + 60);
            }
        }
        // domains
        Iterator<Map.Entry<UUID, Domain>> dit = DOMAINS.entrySet().iterator();
        while (dit.hasNext()) {
            Map.Entry<UUID, Domain> en = dit.next();
            Domain d = en.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(en.getKey());
            long now = d.level.getGameTime();
            if (owner == null || !owner.isAlive() || owner.serverLevel() != d.level || now >= d.until || !is(owner)) {
                dit.remove();
                YNet.near(d.level, d.centre, 512, new YNet.Domain(owner == null ? -1 : owner.getId(), d.centre, d.radius, 0, new int[0]));
                if (owner != null) state(owner);
                continue;
            }
            if (now % 10 == 0) {
                List<LivingEntity> in = inside(owner, d);
                int[] ids = new int[Math.min(32, in.size())];
                for (int i = 0; i < ids.length; i++) ids[i] = in.get(i).getId();
                YNet.near(d.level, d.centre, 512, new YNet.Domain(owner.getId(), d.centre, d.radius, d.until, ids));
                if (now % 20 == 0) {
                    // the sure-hit: everything inside burns
                    for (LivingEntity v : in) hurt(owner, v, Y.dmgSignature * 0.08, false, Set.of());
                }
            }
        }
        // overdrive
        Iterator<Map.Entry<UUID, Long>> oit = OVERDRIVE.entrySet().iterator();
        while (oit.hasNext()) {
            Map.Entry<UUID, Long> en = oit.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                oit.remove();
                continue;
            }
            long now = p.serverLevel().getGameTime();
            if (now >= en.getValue() || !is(p) || !p.isAlive()) {
                oit.remove();
                YNet.near(p.serverLevel(), p.position(), 256, new YNet.Aura(p.getId(), 0));
                state(p);
            } else if (now % 40 == 0) {
                YNet.near(p.serverLevel(), p.position(), 256, new YNet.Aura(p.getId(), en.getValue()));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2, false, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 1, false, false, false));
            }
        }
        // a slow heartbeat of state, so the HUD is right after reconnects
        if (server.getTickCount() % 40 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) if (is(p)) state(p);
        }
    }

    private static void step(Run r, long now) {
        int t = (int) (now - r.start);
        ServerLevel level = r.p.serverLevel();
        while (r.idx < r.beats.size() && r.beats.get(r.idx).t() <= t) {
            apply(r, r.beats.get(r.idx), now);
            r.idx++;
        }
        // dash
        if (r.dash != null) {
            Beat b = r.dash;
            double u = Math.min(1.0, (now - r.dashStart + 1) / (double) Math.max(1, b.dur()));
            double e = 1 - Math.pow(1 - u, 3);
            Vec3 to = Anchors.at(level, r.c, r.p.getId(), b.a1(), b.o1());
            Vec3 from = r.dashFrom;
            Vec3 d = to.subtract(from);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            if (b.x() > 0 && flat.lengthSqr() > 1e-4) {
                to = to.subtract(flat.normalize().scale(b.x()));
                if (b.a1() == Beat.TARGET || b.a1() >= Beat.T0) to = new Vec3(to.x, to.y - r.p.getBbHeight() * 0.5, to.z);
            }
            Vec3 at = from.lerp(to, e);
            r.p.teleportTo(level, at.x, at.y, at.z, r.p.getYRot(), r.p.getXRot());
            r.p.setDeltaMovement(Vec3.ZERO);
            r.p.hurtMarked = true;
            if (u >= 1.0) r.dash = null;
        }
        // blood in flight
        Iterator<Object[]> bi = r.bolts.iterator();
        while (bi.hasNext()) {
            Object[] o = bi.next();
            if ((Long) o[0] > now) continue;
            bi.remove();
            Beat b = (Beat) o[1];
            Vec3 at = Anchors.at(level, r.c, r.p.getId(), b.a1(), b.o1());
            double rad = Math.max(1.2, b.x() * 4 + 0.8);
            damageAround(r, at, rad, b.y(), Math.min(r.m.tier, 2));
        }
    }

    private static void apply(Run r, Beat b, long now) {
        ServerPlayer p = r.p;
        ServerLevel level = p.serverLevel();
        Vec3 p0 = Anchors.at(level, r.c, p.getId(), b.a0(), b.o0());
        switch (b.kind()) {
            case Beat.BF -> {
                // the primary blow lands on the target (or whatever is right there), then the distortion tears outwards
                Set<Integer> hit = new HashSet<>();
                LivingEntity primary = null;
                Entity tgt = Anchors.entity(level, r.c.targetId());
                if (tgt instanceof LivingEntity le && le.isAlive() && Anchors.centre(le).distanceTo(p0) < 4 + b.x()) primary = le;
                if (primary == null) primary = nearest(p, p0, 2.5 * b.x());
                if (primary != null) {
                    hurt(p, primary, tierDamage(b.i()) * b.y(), true, hit);
                    hit.add(primary.getId());
                }
                for (LivingEntity e : around(p, p0, 3.0 * b.x())) {
                    if (hit.add(e.getId())) hurt(p, e, tierDamage(b.i()) * 0.3, false, Set.of());
                }
            }
            case Beat.BLAST -> {
                int style = b.i();
                double depth = style == 2 ? 1 : b.y();
                Vec3 c = style == 2 ? p0 : p0.add(0, -0.5, 0);
                Destroy.blast(level, p, r.journal, c, b.x(), depth, style);
                if (b.z() > 0) damageAround(r, p0, b.x(), b.z(), r.m.tier);
            }
            case Beat.TRENCH -> {
                Vec3 p1 = Anchors.at(level, r.c, p.getId(), b.a1(), b.o1());
                Vec3 dir = p1.subtract(p0);
                Destroy.trench(level, p, r.journal, p0, dir, b.x(), b.y(), b.z(), b.dur());
                Vec3 f = new Vec3(dir.x, 0, dir.z);
                if (f.lengthSqr() > 1e-6) damageLine(r, p0, f.normalize(), b.x(), b.y() * 0.5 + 1.5, 6, 0.5);
            }
            case Beat.SLASH -> {
                Vec3 p1 = Anchors.at(level, r.c, p.getId(), b.a1(), b.o1());
                Vec3 d = p1.subtract(p0);
                if (d.lengthSqr() < 1e-6) d = r.c.dir();
                d = d.normalize();
                Vec3 side = Anchors.side(d, b.z());
                Vec3 n = d.cross(side).normalize();
                double len = b.x(), w = b.y();
                Set<Integer> hit = new HashSet<>();
                for (LivingEntity e : around(p, p0.add(d.scale(len * 0.5)), len * 0.5 + w)) {
                    Vec3 q = Anchors.centre(e).subtract(p0);
                    double a = q.dot(d), s = q.dot(side), nn = q.dot(n);
                    if (a < 0 || a > len || Math.abs(s) > w * 0.5 || Math.abs(nn) > 1.2 + e.getBbWidth()) continue;
                    if (hit.add(e.getId())) hurt(p, e, tierDamage(b.i()), false, Set.of());
                }
                Destroy.slab(level, p, r.journal, p0, d, side, len, w * 0.6);
            }
            case Beat.CLEAVE -> {
                LivingEntity e = nearest(p, p0, Math.max(3.0, b.x() + 1.5));
                if (e != null) {
                    // adaptive: the tougher the target, the deeper the cut
                    double dmg = tierDamage(r.m.tier) * b.y() + e.getMaxHealth() * 0.5 * b.y() + e.getArmorValue() * 40;
                    hurt(p, e, dmg, true, Set.of());
                }
            }
            case Beat.BOLT -> r.bolts.add(new Object[]{now + Math.max(1, b.dur()), b});
            case Beat.KNOCK -> {
                for (LivingEntity e : around(p, p0, b.y())) {
                    Vec3 away = new Vec3(e.getX() - p0.x, 0, e.getZ() - p0.z);
                    if (away.lengthSqr() < 1e-4) away = r.c.f();
                    away = away.normalize();
                    double k = Y.knockback * (e.getType().is(Tags.EntityTypes.BOSSES) ? 0.2 : 1.0);
                    e.setDeltaMovement(away.scale(b.z() * 0.55 * k).add(0, b.x() * 0.7 * k, 0));
                    e.hurtMarked = true;
                    e.hasImpulse = true;
                }
            }
            case Beat.DMG -> {
                if (b.i() >= 0) damageAround(r, p0, b.x(), b.y(), b.i());
            }
            case Beat.DOMAIN_ON -> {
                Vec3 c = p.position();
                float rad = Y.domainRadius;
                Domain d = new Domain(level, c, rad, now + Y.domainSeconds * 20L);
                DOMAINS.put(p.getUUID(), d);
                List<LivingEntity> in = inside(p, d);
                int[] ids = in.stream().limit(32).mapToInt(Entity::getId).toArray();
                YNet.near(level, c, 512, new YNet.Domain(p.getId(), c, rad, d.until, ids));
                state(p);
            }
            case Beat.DOMAIN_OFF -> {
                Domain d = DOMAINS.remove(p.getUUID());
                if (d != null) YNet.near(level, d.centre, 512, new YNet.Domain(p.getId(), d.centre, d.radius, 0, new int[0]));
                state(p);
            }
            case Beat.OVERDRIVE -> {
                long until = now + Y.overdriveSeconds * 20L;
                OVERDRIVE.put(p.getUUID(), until);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2, false, false, false));
                YNet.near(level, p.position(), 256, new YNet.Aura(p.getId(), until));
                state(p);
            }
            case Beat.DASH -> {
                r.dash = b;
                r.dashStart = now;
                r.dashFrom = p.position();
            }
            default -> {
                // everything else is only seen and heard (played by the clients)
            }
        }
    }

    // ------------------------------------------------------------------ damage
    static double tierDamage(int tier) {
        return switch (tier) {
            case 0 -> Y.dmgSignature;
            case 1 -> Y.dmgHeavy;
            case 2 -> Y.dmgUltimate;
            default -> Y.dmgFinal;
        };
    }

    private static List<LivingEntity> around(ServerPlayer p, Vec3 c, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r + 2),
                e -> e != p && e.isAlive() && Anchors.centre(e).distanceTo(c) <= r + e.getBbWidth() * 0.5);
    }

    private static LivingEntity nearest(ServerPlayer p, Vec3 c, double r) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : around(p, c, r)) {
            double d = Anchors.centre(e).distanceToSqr(c);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static List<LivingEntity> inside(ServerPlayer owner, Domain d) {
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(d.centre, d.centre).inflate(d.radius),
                e -> e != owner && e.isAlive() && e.position().distanceTo(d.centre) <= d.radius
                        && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())));
    }

    private static void damageAround(Run r, Vec3 c, double radius, double mult, int tier) {
        for (LivingEntity e : around(r.p, c, radius)) hurt(r.p, e, tierDamage(tier) * mult, false, Set.of());
    }

    private static void damageLine(Run r, Vec3 o, Vec3 d, double len, double halfWidth, double height, double mult) {
        Vec3 mid = o.add(d.scale(len * 0.5));
        for (LivingEntity e : around(r.p, mid, len * 0.5 + halfWidth)) {
            Vec3 q = e.position().subtract(o);
            double a = q.dot(d);
            if (a < 0 || a > len) continue;
            Vec3 perp = q.subtract(d.scale(a));
            if (Math.hypot(perp.x, perp.z) > halfWidth || Math.abs(perp.y) > height) continue;
            hurt(r.p, e, tierDamage(r.m.tier) * mult, false, Set.of());
        }
    }

    static void hurt(ServerPlayer p, LivingEntity e, double amount, boolean main, Set<Integer> skip) {
        if (e == p || !e.isAlive() || skip.contains(e.getId())) return;
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return;
        double dmg = amount;
        if (overdrive(p)) dmg *= 1.6;
        Domain d = domainOf(p);
        if (d != null && e.position().distanceTo(d.centre) <= d.radius) dmg *= 1.5;
        if (e.getType().is(Tags.EntityTypes.BOSSES)) dmg *= Y.bossMultiplier;
        if (Y.damageCap > 0) dmg = Math.min(dmg, Y.damageCap);
        if (Y.resetInvulnerability) e.invulnerableTime = 0;
        e.hurt(p.damageSources().playerAttack(p), (float) Math.min(dmg, Float.MAX_VALUE / 4));
        if (Y.resetInvulnerability) e.invulnerableTime = 0;
    }

    // ------------------------------------------------------------------ safety, state, lifecycle
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Run r = RUNS.get(p.getUUID());
            if (r != null && r.m.cutscene && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && is(p)) {
            Long safe = SAFE_UNTIL.get(p.getUUID());
            if (RUNS.containsKey(p.getUUID()) || (safe != null && safe > p.serverLevel().getGameTime())) e.setCanceled(true);
        }
    }

    public static void state(ServerPlayer p) {
        Domain d = domainOf(p);
        Long od = OVERDRIVE.get(p.getUUID());
        YNet.to(p, new YNet.State(ready(p).clone(), total(p).clone(), d == null ? 0 : d.until, od == null ? 0 : od));
    }

    /** Self-test: no cooldowns, no move running. */
    public static void testReset(ServerPlayer p) {
        RUNS.remove(p.getUUID());
        READY.remove(p.getUUID());
        Energy.set(p, Energy.MAX);
    }

    public static boolean busy(ServerPlayer p) {
        return RUNS.containsKey(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID u = e.getEntity().getUUID();
        RUNS.remove(u);
        OVERDRIVE.remove(u);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && is(p)) state(p);
    }

    @SubscribeEvent
    public static void onStarting(ServerStartingEvent e) {
        Y.load();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        RUNS.clear();
        READY.clear();
        TOTAL.clear();
        DOMAINS.clear();
        OVERDRIVE.clear();
        SAFE_UNTIL.clear();
    }
}
