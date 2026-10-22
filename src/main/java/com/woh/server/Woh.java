package com.woh.server;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.woh.W;
import com.woh.common.Anchors;
import com.woh.common.Beat;
import com.woh.common.Ctx;
import com.woh.common.Script;
import com.woh.common.WMove;
import com.woh.net.WNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
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
 * Modulo Woh on the server: his keys, the move runner that plays each move's timeline tick by tick (damage,
 * destruction, movement), his domain and Overdrive. What everyone sees is played by the clients from the same
 * timeline, sent in one Cast packet.
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class Woh {
    private static final Random RNG = new Random();
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, long[]> READY = new HashMap<>();
    private static final Map<UUID, int[]> TOTAL = new HashMap<>();
    private static final Map<UUID, Domain> DOMAINS = new HashMap<>();
    private static final Map<UUID, Long> OVERDRIVE = new HashMap<>();
    private static final Map<UUID, Long> SAFE_UNTIL = new HashMap<>();
    private static Run CURRENT;
    /** As God As My Witness: until when nothing can kill him. */
    private static final Map<UUID, Long> GOD = new HashMap<>();
    /** The extra max health from Reverse Cursed Technique x100 (one fixed id, so it never stacks). */
    static final UUID RCT_HP = UUID.fromString("6f3c7a1e-2b54-4d8e-9c1f-7a0d5e3b9c21");
    /** "Until" for permanent Overdrive (far in the future, still safe to add to). */
    static final long FOREVER = Long.MAX_VALUE / 4;
    static final String OD_TAG = "woh_overdrive";
    static final String GOD_TAG = "woh_asgod";
    /** While god holds: each attacker on him, and the recent ticks they struck (three within 30 s brings Judgement). */
    private static final Map<UUID, Map<UUID, java.util.ArrayDeque<Long>>> GOD_HITS = new HashMap<>();
    // ---- The World Over Heaven state
    private static final Map<UUID, long[]> STOPS = new HashMap<>();        // Time Stop: {untilTick, radius}
    private static final Map<UUID, Long> RETURN_READY = new HashMap<>();   // Return Over Heaven armed until this tick
    private static final Map<UUID, Double> RETURN_HP = new HashMap<>();    // health to restore to (0 = full)
    private static final Map<UUID, Long> NEGATE_UNTIL = new HashMap<>();   // Negation / Stillness reactive window

    private Woh() {}

    static final class Run {
        final ServerPlayer p;
        final WMove m;
        final Ctx c;
        final List<Beat> beats;
        final long start;
        final Destroy.Journal journal;
        int idx;
        Beat dash;
        long dashStart;
        Vec3 dashFrom;
        // The End: who is held, until when; the descent
        int grabbed = -1;
        long grabUntil, sinkStart;
        int sinkDur;
        double sinkFrom, sinkTo;
        final List<Object[]> bolts = new ArrayList<>();   // {impactTick(Long), Beat}
        final Map<Integer, Double> dealt = new HashMap<>();

        Run(ServerPlayer p, WMove m, Ctx c, List<Beat> beats, long start, Destroy.Journal j) {
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
        return Sorcerers.is(p, Sorcerer.WORLD_OVER_HEAVEN);
    }

    /** A move key (slot 0-15). True when Woh took it. */
    public static boolean key(ServerPlayer p, int slot) {
        if (!is(p)) return false;
        if (slot < 0 || slot >= Sorcerer.WORLD_OVER_HEAVEN.moves.length) return true;
        long now = p.serverLevel().getGameTime();
        WMove m = WMove.resolve(slot, domainOf(p) != null, overdrive(p));
        if (m == null) return true;
        // Heaven Ascension is a toggle: pressed while ascended, it lets the ascension go instead of recasting.
        if (m == WMove.HEAVEN_ASCENSION && overdrive(p)) {
            endOverdrive(p);
            tell(p, "Heaven Ascension ended");
            return true;
        }
        Run run = RUNS.get(p.getUUID());
        if (run != null) return true;   // busy: the move in progress finishes first
        long ready = ready(p)[m.ordinal()];
        if (now < ready) {
            tell(p, m.label() + " ready in " + String.format("%.1f", (ready - now) / 20.0) + "s");
            return true;
        }
        // infinite cursed energy: nothing is ever spent
        cast(p, m);
        return true;
    }

    /** The End, every tick: the chokehold keeps them pinned in front of him, and the two of them sink. */
    private static void theEndTick(Run r, ServerLevel level, long now) {
        ServerPlayer p = r.p;
        if (r.sinkDur > 0) {
            double u = Math.min(1.0, (now - r.sinkStart) / (double) r.sinkDur);
            double e = u * u * (3 - 2 * u);
            double y = r.sinkFrom + (r.sinkTo - r.sinkFrom) * e;
            p.teleportTo(level, p.getX(), y, p.getZ(), p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
        }
        if (r.grabbed >= 0 && now <= r.grabUntil) {
            Entity t = level.getEntity(r.grabbed);
            if (t == null || !t.isAlive()) {
                r.grabbed = -1;
                return;
            }
            Vec3 f = r.c.f();
            Vec3 at = p.position().add(f.scale(0.75));
            if (t instanceof ServerPlayer tp) {
                tp.teleportTo(level, at.x, at.y, at.z, p.getYRot(), tp.getXRot());
            } else {
                t.teleportTo(at.x, at.y, at.z);
            }
            t.setDeltaMovement(Vec3.ZERO);
            t.hurtMarked = true;
            t.fallDistance = 0;
            if (t instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 10, false, false, false));
                le.addEffect(new MobEffectInstance(MobEffects.JUMP, 10, 250, false, false, false));
            }
        }
    }

    private static void tell(ServerPlayer p, String s) {
        p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.RED), true);
    }

    private static long[] ready(ServerPlayer p) {
        return READY.computeIfAbsent(p.getUUID(), u -> new long[WMove.values().length]);
    }

    private static int[] total(ServerPlayer p) {
        return TOTAL.computeIfAbsent(p.getUUID(), u -> new int[WMove.values().length]);
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
    public static void cast(ServerPlayer p, WMove m) {
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
        double reach = dom != null ? dom.radius : (m == WMove.REALITY_KNIVES ? 18 : 40);
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(around, around).inflate(reach),
                e -> e != p && e.isAlive() && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) && e.position().distanceTo(around) <= reach);
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(around)));
        if (target != null) {
            near.remove(target);
            near.add(0, target);
        }
        int[] targets = new int[Math.min(m == WMove.REALITY_KNIVES || m == WMove.KNIFE_RAIN || m == WMove.FOURTEEN_PHRASES ? 16 : 8, near.size())];
        for (int i = 0; i < targets.length; i++) targets[i] = near.get(i).getId();

        boolean od = overdrive(p);
        Vec3 domainCentre = dom != null ? dom.centre : p.position();
        float domainRadius = dom != null ? dom.radius : W.domainRadius;
        float scale = (float) W.destructionScale;
        Ctx c = new Ctx(m, RNG.nextLong(), p.position(), look, point, target != null ? target.getId() : -1, targets,
                domainCentre, domainRadius, (float) Math.min(W.maxRadius, W.worldEnderRadius * scale),
                (float) (W.splitterLength * scale), (float) W.dismantleLength, od, scale);
        List<Beat> beats = Script.build(c);
        Run run = new Run(p, m, c, beats, now, Destroy.journal(level));
        RUNS.put(p.getUUID(), run);

        int cd = (int) Math.round(m.cooldown * W.cooldownScale * (od ? 0.5 : 1.0));
        ready(p)[m.ordinal()] = now + cd;
        total(p)[m.ordinal()] = cd;
        WNet.near(level, p.position(), 512, new WNet.Cast(m.ordinal(), p.getId(), now, c.origin(), c.dir(), c.point(), c.seed(),
                c.targetId(), c.targets(), c.domainCentre(), c.domainRadius(), c.worldEnder(), c.splitter(), c.dismantle(),
                c.overdrive(), c.destructionScale()));
        state(p);
        p.fallDistance = 0;
        W.LOG.info("[woh] {} cast {} (target {}, {} nearby)", p.getGameProfile().getName(), m, target == null ? "none" : target.getName().getString(), targets.length);
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
                W.LOG.error("[woh] move {} failed", r.m, t);
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
                WNet.near(d.level, d.centre, 512, new WNet.Domain(owner == null ? -1 : owner.getId(), d.centre, d.radius, 0, new int[0]));
                if (owner != null) state(owner);
                continue;
            }
            // the domain doesn't hurt: it holds. Everything inside is dragged to a crawl (walking, flying, jumping,
            // falling, swinging), so the Barrage and the Finisher can't miss
            List<LivingEntity> in = inside(owner, d);
            for (LivingEntity v : in) {
                Vec3 mv = v.getDeltaMovement();
                v.setDeltaMovement(mv.x * 0.35, mv.y > 0 ? mv.y * 0.35 : mv.y * 0.6, mv.z * 0.35);
                v.hurtMarked = v instanceof Player;   // players move themselves: tell their client
                if (now % 10 == 0) {
                    v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6, false, true, true));
                    v.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 3, false, true, true));
                    v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1, false, true, true));
                    v.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 250, false, false, false));   // no jumping out
                }
            }
            if (now % 10 == 0) {
                int[] ids = new int[Math.min(32, in.size())];
                for (int i = 0; i < ids.length; i++) ids[i] = in.get(i).getId();
                WNet.near(d.level, d.centre, 512, new WNet.Domain(owner.getId(), d.centre, d.radius, d.until, ids));
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
                flight(p, false);
                p.getPersistentData().remove(OD_TAG);
                WNet.near(p.serverLevel(), p.position(), 256, new WNet.Aura(p.getId(), 0));
                state(p);
                continue;
            }
            if (now % 10 == 0) purge(p);   // anything that slipped in some other way
            if (now % 40 == 0) {
                flight(p, true);
                WNet.near(p.serverLevel(), p.position(), 256, new WNet.Aura(p.getId(), en.getValue()));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2, false, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 1, false, false, false));
            }
        }
        // Time Stop: while a stop lasts, everything but its owner is held frozen in place
        Iterator<Map.Entry<UUID, long[]>> sit = STOPS.entrySet().iterator();
        while (sit.hasNext()) {
            Map.Entry<UUID, long[]> en = sit.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(en.getKey());
            long[] st = en.getValue();
            if (owner == null || !owner.isAlive() || !is(owner) || owner.serverLevel().getGameTime() >= st[0]) {
                sit.remove();
                continue;
            }
            ServerLevel lvl = owner.serverLevel();
            double radius = st[1];
            for (LivingEntity le : lvl.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(radius),
                    x -> x != owner && x.isAlive() && !(x instanceof Player pl && pl.isSpectator()))) {
                le.setDeltaMovement(Vec3.ZERO);
                le.hurtMarked = true;
                le.fallDistance = 0;
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 250, false, false, false));
                le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 4, 250, false, false, false));
                le.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 4, 250, false, false, false));
                if (le instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
            }
        }
        // a slow heartbeat of state, so the HUD is right after reconnects; his cursed energy never runs low
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!is(p)) {
                    // no longer Woh: the rebuilt body goes with the technique
                    AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
                    if (hp != null && hp.getModifier(RCT_HP) != null) {
                        hp.removeModifier(RCT_HP);
                        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
                    }
                    continue;
                }
                if (Energy.get(p) < Energy.MAX) Energy.set(p, Energy.MAX);
                Long gu = GOD.get(p.getUUID());
                if (gu != null && gu <= p.serverLevel().getGameTime()) {
                    GOD.remove(p.getUUID());
                    WNet.near(p.serverLevel(), p.position(), 256, new WNet.God(p.getId(), 0));
                }
                if (server.getTickCount() % 40 == 0) state(p);
            }
        }
    }

    private static void step(Run r, long now) {
        CURRENT = r;
        try {
            stepInner(r, now);
        } finally {
            CURRENT = null;
        }
    }

    private static void stepInner(Run r, long now) {
        int t = (int) (now - r.start);
        ServerLevel level = r.p.serverLevel();
        while (r.idx < r.beats.size() && r.beats.get(r.idx).t() <= t) {
            Beat b = r.beats.get(r.idx++);
            try {
                apply(r, b, now);
            } catch (Throwable e) {
                W.LOG.error("[woh] beat {} of {} failed (skipped)", b.kind(), r.m, e);
            }
        }
        theEndTick(r, level, now);
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
                // the blow lands on whatever is at the point of impact (the beat's own anchor already follows its target)
                LivingEntity primary = nearest(p, p0, Math.max(2.5, 2.0 * b.x()));
                if (primary == null) {
                    Entity tgt = Anchors.entity(level, r.c.targetId());
                    if (tgt instanceof LivingEntity le && le.isAlive() && Anchors.centre(le).distanceTo(p0) < 4 + b.x()) primary = le;
                }
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
                Destroy.slab(level, p, r.journal, p0, d, side, len, w * 0.8);
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
                    double k = W.knockback * (e.getType().is(Tags.EntityTypes.BOSSES) ? 0.2 : 1.0);
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
                float rad = W.domainRadius;
                Domain d = new Domain(level, c, rad, now + W.domainSeconds * 20L);
                DOMAINS.put(p.getUUID(), d);
                List<LivingEntity> in = inside(p, d);
                int[] ids = in.stream().limit(32).mapToInt(Entity::getId).toArray();
                WNet.near(level, c, 512, new WNet.Domain(p.getId(), c, rad, d.until, ids));
                state(p);
            }
            case Beat.DOMAIN_OFF -> {
                Domain d = DOMAINS.remove(p.getUUID());
                if (d != null) WNet.near(level, d.centre, 512, new WNet.Domain(p.getId(), d.centre, d.radius, 0, new int[0]));
                state(p);
            }
            case Beat.WALL -> {
                if (canBreakWall(p)) ServerShutdown.begin(p.getServer(), p.getGameProfile().getName(), (int) b.x());
            }
            case Beat.GOD -> {
                GOD.put(p.getUUID(), FOREVER);
                p.getPersistentData().putBoolean(GOD_TAG, true);
                p.setHealth(Math.max(p.getHealth(), 1f));
                WNet.near(level, p.position(), 256, new WNet.God(p.getId(), FOREVER));
                state(p);
            }
            case Beat.GRAB -> {
                Entity t = Anchors.entity(level, r.c.targetId());
                if (t instanceof LivingEntity && t.isAlive()) {
                    r.grabbed = t.getId();
                    r.grabUntil = now + Math.max(1, b.dur());
                }
            }
            case Beat.SINK -> {
                r.sinkStart = now;
                r.sinkDur = Math.max(1, b.dur());
                r.sinkFrom = p.getY();
                r.sinkTo = level.getMinBuildHeight() - b.x();
            }
            case Beat.KILL -> {
                Entity t = r.grabbed >= 0 ? level.getEntity(r.grabbed) : Anchors.entity(level, r.c.targetId());
                if (t instanceof LivingEntity le && le.isAlive() && !(le instanceof ServerPlayer sp && god(sp))) {
                    // the void takes them: it ignores creative mode, armour, totems and invulnerability
                    le.invulnerableTime = 0;
                    le.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                    if (le.isAlive()) le.kill();
                    if (le.isAlive()) le.setHealth(0);
                    W.LOG.info("[woh] The End: {} dead={}", le.getName().getString(), !le.isAlive());
                }
                r.grabbed = -1;
            }
            case Beat.RETURN -> {
                r.sinkDur = 0;
                Vec3 home = r.c.origin();
                p.teleportTo(level, home.x, home.y, home.z, p.getYRot(), p.getXRot());
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0;
                SAFE_UNTIL.put(p.getUUID(), now + 100);
            }
            case Beat.GLYPH -> Destroy.glyph(level, p, r.journal, p0, r.c.f(), r.c.r(), b.x());
            case Beat.HEAL -> {
                AttributeInstance hp = p.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) {
                    hp.removeModifier(RCT_HP);
                    double extra = Math.max(0, b.x() - hp.getBaseValue());
                    if (extra > 0) hp.addPermanentModifier(new AttributeModifier(RCT_HP, "Reverse Cursed Technique x100", extra, AttributeModifier.Operation.ADDITION));
                }
                p.setHealth(p.getMaxHealth());
                p.removeEffect(MobEffects.POISON);
                p.removeEffect(MobEffects.WITHER);
                p.removeEffect(MobEffects.HUNGER);
                p.removeEffect(MobEffects.WEAKNESS);
                p.removeEffect(MobEffects.CONFUSION);
                p.removeEffect(MobEffects.BLINDNESS);
                p.clearFire();
                p.getFoodData().setFoodLevel(20);
                if (b.y() == 1) {
                    // Grace Over Heaven: the same grace extended to nearby allies (every non-hostile player around him)
                    for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(10),
                            a -> a != null && a.isAlive() && !a.isSpectator())) {
                        ally.setHealth(ally.getMaxHealth());
                        ally.clearFire();
                        ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 2, false, true, true));
                        ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1, false, true, true));
                        ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1, false, true, true));
                        ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2, false, true, true));
                    }
                }
                W.LOG.info("[woh] {} restored to {} / {} health", p.getGameProfile().getName(), p.getHealth(), p.getMaxHealth());
            }
            case Beat.ERASE -> {
                double threshold = b.y();   // fraction of max HP at or below which a target is erased outright
                int tier = b.i();
                for (LivingEntity e : around(p, p0, b.x())) {
                    // The World Over Heaven overwrites existence itself: Modulo Yuji's As God As My Witness cannot refuse it
                    if (e instanceof ServerPlayer sp && isYujiGod(sp)) {
                        com.yuji.server.Yuji.realityOverwrite(sp);
                        continue;
                    }
                    boolean protectedPlayer = e instanceof Player pl && (pl.isCreative() || pl.isSpectator());
                    boolean weak = e.getHealth() <= e.getMaxHealth() * threshold;
                    if (weak && !protectedPlayer) {
                        // erased: past armour, totems, resistance and invulnerability
                        e.invulnerableTime = 0;
                        e.setInvulnerable(false);
                        e.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                        if (e.isAlive()) e.kill();
                        if (e.isAlive()) e.setHealth(0);
                        W.LOG.info("[woh] Reality Overwrite erased {}", e.getName().getString());
                    } else {
                        hurt(p, e, tierDamage(tier), e.getId() == r.c.targetId(), Set.of());
                    }
                }
            }
            case Beat.STRIP -> {
                for (LivingEntity e : around(p, p0, b.x())) {
                    for (MobEffectInstance eff : new java.util.ArrayList<>(e.getActiveEffects())) {
                        if (eff.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) {
                            e.removeEffect(eff.getEffect());
                        }
                    }
                    e.setAbsorptionAmount(0);
                }
            }
            case Beat.STOP -> {
                STOPS.put(p.getUUID(), new long[]{now + Math.max(1, b.dur()), (long) b.x()});
            }
            case Beat.RETURN_SET -> {
                RETURN_READY.put(p.getUUID(), now + Math.max(1, b.dur()));
                RETURN_HP.put(p.getUUID(), b.x());
                tell(p, "Return Over Heaven: your death is rewritten");
            }
            case Beat.NEGATE -> {
                NEGATE_UNTIL.put(p.getUUID(), now + Math.max(1, b.dur()));
            }
            case Beat.OVERDRIVE -> {
                long until = W.overdriveSeconds <= 0 ? FOREVER : now + W.overdriveSeconds * 20L;
                OVERDRIVE.put(p.getUUID(), until);
                purge(p);
                if (until == FOREVER) p.getPersistentData().putBoolean(OD_TAG, true);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2, false, false, false));
                flight(p, true);
                WNet.near(level, p.position(), 256, new WNet.Aura(p.getId(), until));
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
            case 0 -> W.dmgSignature;
            case 1 -> W.dmgHeavy;
            case 2 -> W.dmgUltimate;
            default -> W.dmgFinal;
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

    /**
     * Shrine cuts the space itself, so it goes straight through Gojo's Infinity (both the kit's Gojo and Teen Gojo):
     * against players, Dismantle, Cleave and everything built on them land as an unblockable, sourceless cut (the
     * kind of damage Infinity lets through). While his domain is open, every move does. Everything else is an
     * ordinary player attack.
     */
    private static DamageSource source(ServerPlayer p, LivingEntity victim, Run r) {
        // with his domain open, every move he has is a sure hit: nothing blocks it, Infinity included
        boolean domain = domainOf(p) != null;
        if (victim instanceof Player && (domain || (r != null && r.m.overwrites()))) {
            return new DamageSource(p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC_KILL));
        }
        return p.damageSources().playerAttack(p);
    }

    static void hurt(ServerPlayer p, LivingEntity e, double amount, boolean main, Set<Integer> skip) {
        if (amount <= 0 || e == p || !e.isAlive() || skip.contains(e.getId())) return;
        if (e instanceof Player && god(p)) return;   // As God As My Witness: he harms no other player
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return;
        double dmg = amount, boost = 1.0;
        if (overdrive(p)) boost *= 1.6;
        Domain d = domainOf(p);
        if (d != null && e.position().distanceTo(d.centre) <= d.radius) boost *= 1.5;
        dmg *= boost;
        Run r = CURRENT;
        if (r != null && r.m.basic()) {
            // a basic move deals at most this much to any one target over the whole cast, however many hits land
            double cap = W.dmgSignature * boost;
            double done = r.dealt.getOrDefault(e.getId(), 0.0);
            dmg = Math.min(dmg, cap - done);
            if (dmg <= 0.01) return;
            r.dealt.put(e.getId(), done + dmg);
        }
        if (e.getType().is(Tags.EntityTypes.BOSSES)) dmg *= W.bossMultiplier;
        if (W.damageCap > 0) dmg = Math.min(dmg, W.damageCap);
        if (W.resetInvulnerability) e.invulnerableTime = 0;
        e.hurt(source(p, e, r), (float) Math.min(dmg, Float.MAX_VALUE / 4));
        if (W.resetInvulnerability) e.invulnerableTime = 0;
    }

    // ------------------------------------------------------------------ safety, state, lifecycle
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Run r = RUNS.get(p.getUUID());
            if (r != null && r.m.cutscene && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ As God As My Witness
    /** End As God As My Witness: he can act again. */
    /** An operator, or the host of a single-player / LAN world (who may not hold op level 4). */
    /** A warning across the striker's screen. */
    private static void warn(ServerPlayer target, String text, int color, boolean bold) {
        var comp = net.minecraft.network.chat.Component.literal(text)
                .withStyle(st -> {
                    st = st.withColor(net.minecraft.network.chat.TextColor.fromRgb(color));
                    return bold ? st.withBold(true) : st;
                });
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(4, 26, 8));
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(comp));
    }

    public static boolean canBreakWall(ServerPlayer p) {
        var s = p.getServer();
        return p.hasPermissions(4) || (s != null && s.isSingleplayerOwner(p.getGameProfile()));
    }

    public static void endGod(ServerPlayer p) {
        GOD_HITS.remove(p.getUUID());
        if (GOD.remove(p.getUUID()) == null) return;
        p.getPersistentData().remove(GOD_TAG);
        WNet.near(p.serverLevel(), p.position(), 256, new WNet.God(p.getId(), 0));
        state(p);
    }

    public static boolean god(ServerPlayer p) {
        Long u = GOD.get(p.getUUID());
        return u != null && u > p.serverLevel().getGameTime();
    }

    /** Is this player a Modulo Yuji currently holding As God As My Witness? (Reality Overwrite overwrites it anyway.) */
    private static boolean isYujiGod(ServerPlayer sp) {
        try {
            return com.yuji.server.Yuji.is(sp) && com.yuji.server.Yuji.god(sp);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Return Over Heaven: an armed death is rewritten - he comes back at full instead of dying, once. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onReturnDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !is(p)) return;
        Long until = RETURN_READY.get(p.getUUID());
        if (until == null || until < p.serverLevel().getGameTime()) return;
        RETURN_READY.remove(p.getUUID());
        Double hp = RETURN_HP.remove(p.getUUID());
        e.setCanceled(true);
        p.deathTime = 0;
        double target = (hp == null || hp <= 0) ? p.getMaxHealth() : Math.min(hp, p.getMaxHealth());
        p.setHealth((float) target);
        p.removeAllEffects();
        p.clearFire();
        p.invulnerableTime = 60;
        SAFE_UNTIL.put(p.getUUID(), p.serverLevel().getGameTime() + 100);
        tell(p, "Return Over Heaven: your death is undone");
        W.LOG.info("[woh] Return Over Heaven saved {}", p.getGameProfile().getName());
    }

    /** Negation: while the window holds, the next technique thrown at him is nullified and thrown back. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onNegateIncoming(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !is(p)) return;
        Long until = NEGATE_UNTIL.get(p.getUUID());
        if (until == null || until < p.serverLevel().getGameTime()) return;
        Entity by = e.getSource().getEntity();
        if (by == p) return;
        e.setCanceled(true);
        NEGATE_UNTIL.remove(p.getUUID());
        if (by instanceof LivingEntity le && le.isAlive()) {
            le.invulnerableTime = 0;
            le.hurt(p.damageSources().magic(), (float) Math.max(8, e.getAmount() * 1.5));
        }
        tell(p, "Negation");
    }

    /** While As God As My Witness holds, he harms no one - his own punches and sword swings do nothing either. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onGodOutgoing(LivingAttackEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer atk && god(atk)) e.setCanceled(true);
    }

    /** Every attack on him, whatever it is (/kill, the void, other mods, bypass-everything damage) simply doesn't happen. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onGodAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !god(p)) return;
        e.setCanceled(true);
        // whoever struck him: three hits inside 30 seconds and the heavens answer
        Entity by = e.getSource().getEntity();
        if (by == null || by == p || !(by instanceof LivingEntity)) return;
        long now = p.serverLevel().getGameTime();
        var perAttacker = GOD_HITS.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
        var ticks = perAttacker.computeIfAbsent(by.getUUID(), u -> new java.util.ArrayDeque<>());
        while (!ticks.isEmpty() && now - ticks.peekFirst() > 600) ticks.pollFirst();   // keep only the last 30 s
        ticks.addLast(now);
        if (by instanceof ServerPlayer atk) {
            if (ticks.size() == 1) warn(atk, "Are you sure?", 0xAA0000, false);
            else if (ticks.size() == 2) warn(atk, "No More Chances.", 0xFF2222, true);
        }
        if (ticks.size() >= 3) {
            perAttacker.remove(by.getUUID());
            Judgement.judgeEntity(by, p);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onGodHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && god(p)) {
            e.setAmount(0);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onGodDamage(LivingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && god(p)) {
            e.setAmount(0);
            e.setCanceled(true);
        }
    }

    /** If anything gets him to death anyway (health set straight to zero), death is refused. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onGodDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && god(p)) {
            e.setCanceled(true);
            p.setHealth(Math.max(1f, p.getMaxHealth() * 0.25f));
            p.deathTime = 0;
        }
    }

    /** Before he ticks: undo anything that zeroed his health, and pull him out of the void. */
    @SubscribeEvent
    public static void onGodTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !(e.player instanceof ServerPlayer p) || !god(p)) return;
        if (p.getHealth() < 1f || p.isDeadOrDying()) {
            p.setHealth(Math.max(1f, p.getMaxHealth() * 0.25f));
            p.deathTime = 0;
        }
        if (p.getY() < p.serverLevel().getMinBuildHeight() - 32 && !RUNS.containsKey(p.getUUID())) {
            var spawn = p.serverLevel().getSharedSpawnPos();
            int y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
            if (y <= p.serverLevel().getMinBuildHeight()) {
                p.teleportTo(p.serverLevel(), spawn.getX() + 0.5, p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ()) + 1, spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
            } else {
                p.teleportTo(p.serverLevel(), p.getX(), y + 1, p.getZ(), p.getYRot(), p.getXRot());
            }
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
        }
        long now = p.serverLevel().getGameTime();
        if (now % 40 == 0) WNet.near(p.serverLevel(), p.position(), 256, new WNet.God(p.getId(), GOD.get(p.getUUID())));
    }

    /** In Overdrive no harmful effect can take hold of him (poison, wither, slowness, weakness, blindness...). */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable e) {
        if (e.getEntity() instanceof ServerPlayer p && overdrive(p) && e.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            e.setResult(Event.Result.DENY);
        }
    }

    static void purge(ServerPlayer p) {
        List<MobEffect> bad = new ArrayList<>();
        for (MobEffectInstance inst : p.getActiveEffects()) {
            if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) bad.add(inst.getEffect());
        }
        for (MobEffect m : bad) p.removeEffect(m);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && is(p)) {
            Long safe = SAFE_UNTIL.get(p.getUUID());
            if (RUNS.containsKey(p.getUUID()) || overdrive(p) || (safe != null && safe > p.serverLevel().getGameTime())) e.setCanceled(true);
        }
    }

    public static void state(ServerPlayer p) {
        Domain d = domainOf(p);
        Long od = OVERDRIVE.get(p.getUUID());
        WNet.to(p, new WNet.State(ready(p).clone(), total(p).clone(), d == null ? 0 : d.until, od == null ? 0 : od));
    }

    /** Self-test: no cooldowns, no move running. */
    public static void testReset(ServerPlayer p) {
        if (GOD.remove(p.getUUID()) != null) { p.getPersistentData().remove(GOD_TAG); WNet.near(p.serverLevel(), p.position(), 256, new WNet.God(p.getId(), 0)); }
        RUNS.remove(p.getUUID());
        READY.remove(p.getUUID());
        Energy.set(p, Energy.MAX);
    }

    /**
     * Overdrive flight: double-tap jump to take off, like creative flight but faster. Taken away when Overdrive
     * ends (he drifts down safely: no fall damage for a few seconds), never from creative or spectator players.
     */
    /** Overdrive off: flight taken back (with a safe landing), aura out, the permanent flag cleared. */
    public static void endOverdrive(ServerPlayer p) {
        if (OVERDRIVE.remove(p.getUUID()) == null) return;
        p.getPersistentData().remove(OD_TAG);
        p.removeEffect(MobEffects.MOVEMENT_SPEED);
        p.removeEffect(MobEffects.JUMP);
        flight(p, false);
        WNet.near(p.serverLevel(), p.position(), 256, new WNet.Aura(p.getId(), 0));
        state(p);
    }

    static void flight(ServerPlayer p, boolean on) {
        var ab = p.getAbilities();
        boolean native_ = p.isCreative() || p.isSpectator();
        if (on) {
            if (!ab.mayfly || ab.getFlyingSpeed() < 0.09f) {
                ab.mayfly = true;
                if (!native_) ab.setFlyingSpeed(0.1f);
                p.onUpdateAbilities();
            }
        } else if (!native_) {
            ab.mayfly = false;
            ab.flying = false;
            ab.setFlyingSpeed(0.05f);
            p.onUpdateAbilities();
            p.fallDistance = 0;
            SAFE_UNTIL.put(p.getUUID(), p.serverLevel().getGameTime() + 200);
        }
    }

    public static boolean busy(ServerPlayer p) {
        return RUNS.containsKey(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID u = e.getEntity().getUUID();
        RUNS.remove(u);
        // flight never gets saved onto the player; permanent Overdrive is remembered by a tag and comes back on login
        if (OVERDRIVE.remove(u) != null && e.getEntity() instanceof ServerPlayer p) flight(p, false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && is(p)) {
            if (p.getPersistentData().getBoolean(OD_TAG)) {
                OVERDRIVE.put(p.getUUID(), FOREVER);
                flight(p, true);
                WNet.near(p.serverLevel(), p.position(), 256, new WNet.Aura(p.getId(), FOREVER));
            }
            if (p.getPersistentData().getBoolean(GOD_TAG)) {
                GOD.put(p.getUUID(), FOREVER);
                WNet.near(p.serverLevel(), p.position(), 256, new WNet.God(p.getId(), FOREVER));
            }
            state(p);
        }
    }

    @SubscribeEvent
    public static void onStarting(ServerStartingEvent e) {
        W.load();
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
