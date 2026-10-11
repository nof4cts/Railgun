package com.naoya.ps.server;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.naoya.ps.Net;
import com.naoya.ps.Net.Fx;
import com.naoya.ps.Sounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Projection Sorcery. One second becomes twenty-four frames: Naoya decides every frame of a movement in advance, then
 * his body traces it at a speed nothing else can follow. Anyone he touches must move in twenty-four frames too, and
 * whoever can't is stopped dead, flattened into a single frame, helpless until it breaks.
 *
 * <p>Moves (slot order): Projection Dash, Frame Freeze, 24-Frame Barrage, Afterimage Step, Sound Barrier Break, Frame
 * Trap, Frame Shatter, Mach 3 Strike. Every move chained within three seconds of the last builds acceleration (up to
 * five stacks): faster, harder hitting, longer dashes.
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class Projection {
    public static final int SLOTS = 8;
    private static final int[] COOLDOWN = {14, 140, 200, 40, 180, 360, 160, 1400};
    private static final int[] COST = {Energy.BASIC, 30, Energy.STRONG, Energy.BASIC, Energy.STRONG, 60, 30, 150};

    private static final Map<UUID, long[]> READY = new HashMap<>();
    private static final Map<UUID, Accel> ACCEL = new HashMap<>();
    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();
    private static final List<Script> SCRIPTS = new ArrayList<>();

    private Projection() {}

    private static final class Accel {
        int stacks;
        long last;
    }

    private record Frozen(LivingEntity e, Vec3 at, float yaw, float pitch, long until, boolean hadNoAi, int by) {}

    /** A move that plays out over several ticks. */
    private static final class Script {
        final ServerPlayer p;
        final int slot;
        final LivingEntity target;
        final long start;
        final Vec3 origin;
        final Vec3 dir;
        int last = -1;

        Script(ServerPlayer p, int slot, LivingEntity target, Vec3 dir) {
            this.p = p;
            this.slot = slot;
            this.target = target;
            this.start = p.serverLevel().getGameTime();
            this.origin = p.position();
            this.dir = dir;
        }
    }

    // ------------------------------------------------------------------------------------------------ entry

    public static void useKey(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= SLOTS || !p.isAlive() || p.isSpectator() || Sorcerers.get(p) != Sorcerer.NAOYA) {
            return;
        }
        if (busy(p)) {
            return;
        }
        long now = p.serverLevel().getGameTime();
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[SLOTS]);
        if (now < ready[slot] && !p.getAbilities().instabuild) {
            return;
        }
        if (!Energy.has(p, COST[slot])) {
            return;
        }
        boolean used = switch (slot) {
            case 0 -> dash(p);
            case 1 -> palm(p);
            case 2 -> barrage(p);
            case 3 -> step(p);
            case 4 -> soundBarrier(p);
            case 5 -> trap(p);
            case 6 -> shatterAll(p);
            case 7 -> ult(p);
            default -> false;
        };
        if (!used) {
            return;
        }
        Energy.spend(p, COST[slot]);
        ready[slot] = now + COOLDOWN[slot];
        accelerate(p, now);
        sync(p);
    }

    public static int[] cooldowns() {
        return COOLDOWN.clone();
    }

    private static boolean busy(ServerPlayer p) {
        for (Script s : SCRIPTS) {
            if (s.p == p) {
                return true;
            }
        }
        return false;
    }

    private static void accelerate(ServerPlayer p, long now) {
        Accel a = ACCEL.computeIfAbsent(p.getUUID(), k -> new Accel());
        a.stacks = now - a.last <= 60 ? Math.min(5, a.stacks + 1) : 1;
        a.last = now;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 70, a.stacks - 1, false, false, true));
    }

    public static int stacks(Player p) {
        Accel a = ACCEL.get(p.getUUID());
        return a == null ? 0 : a.stacks;
    }

    private static float power(ServerPlayer p) {
        return 1.0F + 0.1F * stacks(p);
    }

    private static void sync(ServerPlayer p) {
        long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[SLOTS]);
        Net.state(p, new Net.State(ready.clone(), COOLDOWN.clone(), stacks(p)));
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
        l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    private static void fx(ServerPlayer p, int kind, Vec3 a, Vec3 b, float f, int other, int dur) {
        Net.fx(p.serverLevel(), new Fx(kind, a, b, f, p.getId(), other, dur));
    }

    private static Vec3 flatLook(ServerPlayer p) {
        Vec3 l = p.getLookAngle();
        Vec3 f = new Vec3(l.x, 0, l.z);
        return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** The living thing nearest the crosshair inside a cone. */
    private static LivingEntity aim(ServerPlayer p, double range, double cone) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.35 + 1);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> valid(p, e))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range + e.getBbWidth()) {
                continue;
            }
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.normalize().dot(look)))));
            if (ang > cone) {
                continue;
            }
            double score = ang * 2 + d;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private static boolean valid(ServerPlayer p, LivingEntity e) {
        return e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !(e instanceof Player pl && pl.isCreative());
    }

    private static Vec3 ray(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
        BlockHitResult hit = p.level().clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(dir.scale(0.7)) : from.add(dir.scale(range));
    }

    /** A standing spot near {@code want}: the spot itself, a little above, or else {@code fallback}. */
    private static Vec3 safe(ServerPlayer p, Vec3 want, Vec3 fallback) {
        for (double up : new double[]{0, 0.6, 1.2, 2.0}) {
            Vec3 at = want.add(0, up, 0);
            if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) {
                return at;
            }
        }
        return fallback;
    }

    private static void teleport(ServerPlayer p, Vec3 to, Vec3 face) {
        Vec3 d = face.subtract(to);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.connection.teleport(to.x, to.y, to.z, yaw, pitch);
        p.fallDistance = 0;
    }

    private static void hit(ServerPlayer p, LivingEntity e, float amount, Vec3 knock) {
        if (e == null || !e.isAlive()) {
            return;
        }
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().playerAttack(p), amount * power(p));
        e.invulnerableTime = 0;
        if (knock != null && !FROZEN.containsKey(e.getId())) {
            e.setDeltaMovement(knock);
            e.hurtMarked = true;
        }
        fx(p, Fx.HIT, e.getBoundingBox().getCenter(), e.getBoundingBox().getCenter().subtract(p.getEyePosition()), amount, e.getId(), 0);
    }

    /** Everything along a line, within {@code r} of it. */
    private static List<LivingEntity> along(ServerPlayer p, Vec3 a, Vec3 b, double r) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(a, b).inflate(r + 1);
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(1.0E-6, ab.lengthSqr());
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> valid(p, e))) {
            Vec3 c = e.getBoundingBox().getCenter();
            double k = Math.max(0, Math.min(1, c.subtract(a).dot(ab) / len2));
            if (c.distanceTo(a.add(ab.scale(k))) <= r + e.getBbWidth() * 0.5) {
                out.add(e);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ the frame

    /** Stop a body dead: it failed to move in twenty-four frames and is stuck in one. */
    public static void freeze(ServerPlayer by, LivingEntity e, int ticks) {
        if (e == null || !e.isAlive() || e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
            return;
        }
        long now = by.serverLevel().getGameTime();
        Frozen old = FROZEN.get(e.getId());
        boolean hadNoAi = old != null ? old.hadNoAi : e instanceof Mob m && m.isNoAi();
        if (e instanceof Mob m) {
            m.setNoAi(true);
        }
        e.setDeltaMovement(Vec3.ZERO);
        FROZEN.put(e.getId(), new Frozen(e, e.position(), e.getYRot(), e.getXRot(), now + ticks, hadNoAi, by.getId()));
        fx(by, Fx.FREEZE, e.position(), e.getBoundingBox().getCenter(), e.getBbHeight(), e.getId(), ticks);
        play(by.serverLevel(), e.position(), Sounds.FREEZE.get(), 1.2F, 1.0F);
    }

    public static boolean frozen(Entity e) {
        return FROZEN.containsKey(e.getId());
    }

    private static void release(Frozen f, ServerLevel level, boolean shattered) {
        if (f.e instanceof Mob m) {
            m.setNoAi(f.hadNoAi);
        }
        Net.fx(level, new Fx(shattered ? Fx.SHATTER : Fx.UNFREEZE, f.e.position(), f.e.getBoundingBox().getCenter(), f.e.getBbHeight(), f.by,
                f.e.getId(), 0));
    }

    private static void unfreeze(LivingEntity e, ServerLevel level, boolean shattered) {
        Frozen f = FROZEN.remove(e.getId());
        if (f != null) {
            release(f, level, shattered);
        }
    }

    // ------------------------------------------------------------------------------------------------ moves

    /** Projection Dash: twenty-four frames planned along a line, traced in an instant. */
    private static boolean dash(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double range = 18 + 2.5 * stacks(p);
        LivingEntity t = aim(p, range, 14);
        Vec3 from = p.position();
        Vec3 to;
        if (t != null) {
            Vec3 d = t.position().subtract(from).multiply(1, 0, 1);
            d = d.lengthSqr() < 1.0E-4 ? flatLook(p) : d.normalize();
            to = safe(p, t.position().subtract(d.scale(t.getBbWidth() * 0.5 + 0.9)), from);
        } else {
            Vec3 end = ray(p, eye, new Vec3(look.x, Math.max(-0.4, Math.min(0.4, look.y)), look.z).normalize(), range);
            to = safe(p, end.subtract(0, p.getEyeHeight(), 0), from);
        }
        for (LivingEntity e : along(p, from.add(0, 1, 0), to.add(0, 1, 0), 1.3)) {
            hit(p, e, e == t ? 6 : 4, to.subtract(from).normalize().scale(0.6).add(0, 0.25, 0));
        }
        teleport(p, to, t != null ? t.getEyePosition() : to.add(look.scale(5)).add(0, p.getEyeHeight(), 0));
        p.setDeltaMovement(to.subtract(from).normalize().scale(0.35));
        p.hurtMarked = true;
        fx(p, Fx.DASH, from, to, stacks(p), t == null ? -1 : t.getId(), 24);
        play(p.serverLevel(), from, Sounds.DASH.get(), 1.0F, 1.0F + 0.05F * stacks(p));
        play(p.serverLevel(), to, Sounds.SHUTTER.get(), 0.8F, 1.2F);
        return true;
    }

    /** Frame Freeze: a palm on someone who can't keep up. */
    private static boolean palm(ServerPlayer p) {
        LivingEntity t = aim(p, 11, 32);
        if (t == null) {
            p.displayClientMessage(Component.literal("Nothing within reach to touch.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Vec3 from = p.position();
        Vec3 d = t.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1.0E-4 ? flatLook(p) : d.normalize();
        Vec3 to = safe(p, t.position().subtract(d.scale(t.getBbWidth() * 0.5 + 0.8)), from);
        teleport(p, to, t.getEyePosition());
        fx(p, Fx.DASH, from, to, stacks(p), t.getId(), 12);
        hit(p, t, 4, null);
        freeze(p, t, 70);
        fx(p, Fx.PALM, to.add(0, 1.2, 0).add(d.scale(0.6)), t.getBoundingBox().getCenter(), 1, t.getId(), 0);
        play(p.serverLevel(), to, Sounds.HEAVY.get(), 1.0F, 1.3F);
        return true;
    }

    /** 24-Frame Barrage: one blow a frame, from wherever the frame puts him. */
    private static boolean barrage(ServerPlayer p) {
        LivingEntity t = aim(p, 9, 35);
        if (t == null) {
            p.displayClientMessage(Component.literal("No one close enough.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        SCRIPTS.add(new Script(p, 2, t, flatLook(p)));
        fx(p, Fx.BARRAGE, p.position(), t.position(), 0, t.getId(), 26);
        return true;
    }

    /** Afterimage Step: behind the target in a frame, or a frame away from danger. */
    private static boolean step(ServerPlayer p) {
        LivingEntity t = aim(p, 16, 30);
        Vec3 from = p.position();
        Vec3 to;
        Vec3 face;
        if (t != null) {
            Vec3 tl = t.getLookAngle().multiply(1, 0, 1);
            tl = tl.lengthSqr() < 1.0E-4 ? flatLook(p) : tl.normalize();
            to = safe(p, t.position().subtract(tl.scale(t.getBbWidth() * 0.5 + 1.1)), from);
            face = t.getEyePosition();
        } else {
            Vec3 back = flatLook(p).scale(-1);
            to = safe(p, ray(p, from.add(0, 0.5, 0), back, 8).subtract(0, 0.5, 0), from);
            face = to.add(flatLook(p).scale(5)).add(0, p.getEyeHeight(), 0);
        }
        teleport(p, to, face);
        p.invulnerableTime = 12;
        fx(p, Fx.STEP, from, to, stacks(p), t == null ? -1 : t.getId(), 30);
        play(p.serverLevel(), from, Sounds.BLINK.get(), 1.0F, 1.0F);
        return true;
    }

    /** Sound Barrier Break: a straight run through the wall of air. */
    private static boolean soundBarrier(ServerPlayer p) {
        Vec3 dir = flatLook(p);
        Vec3 from = p.position();
        double range = 26 + 3 * stacks(p);
        Vec3 end = ray(p, from.add(0, 0.6, 0), dir, range).subtract(0, 0.6, 0);
        Vec3 to = safe(p, end, from);
        for (LivingEntity e : along(p, from.add(0, 1, 0), to.add(0, 1, 0), 2.6)) {
            hit(p, e, 11, dir.scale(2.2).add(0, 0.8, 0));
        }
        teleport(p, to, to.add(dir.scale(6)).add(0, p.getEyeHeight(), 0));
        p.setDeltaMovement(dir.scale(0.6));
        p.hurtMarked = true;
        fx(p, Fx.BOOM, from, to, stacks(p), -1, 30);
        play(p.serverLevel(), from, Sounds.BOOM.get(), 2.5F, 1.0F);
        play(p.serverLevel(), to, Sounds.DASH.get(), 1.0F, 0.8F);
        return true;
    }

    /** Frame Trap: everyone around him is held to twenty-four frames at once, and fails. */
    private static boolean trap(ServerPlayer p) {
        List<LivingEntity> near = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9), e -> valid(p, e)
                && e.distanceTo(p) <= 9);
        if (near.isEmpty()) {
            p.displayClientMessage(Component.literal("No one to catch.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        fx(p, Fx.TRAP, p.position(), p.position(), 9, -1, 20);
        play(p.serverLevel(), p.position(), Sounds.FILM.get(), 1.4F, 1.0F);
        for (LivingEntity e : near) {
            freeze(p, e, 50);
        }
        return true;
    }

    /** Frame Shatter: every frame he made breaks at once, and the body inside it with it. */
    private static boolean shatterAll(ServerPlayer p) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Frozen f : FROZEN.values()) {
            if (f.e.isAlive() && f.e.level() == p.level() && f.e.distanceTo(p) < 20) {
                targets.add(f.e);
            }
        }
        if (targets.isEmpty()) {
            p.displayClientMessage(Component.literal("No frames to break.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        play(p.serverLevel(), p.position(), Sounds.SHATTER.get(), 1.6F, 0.9F);
        for (LivingEntity e : targets) {
            unfreeze(e, p.serverLevel(), true);
            Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? flatLook(p) : away.normalize();
            hit(p, e, 14, away.scale(1.1).add(0, 0.6, 0));
        }
        return true;
    }

    /** Mach 3: the whole plan in one frame-perfect line, through the sound barrier three times. */
    private static boolean ult(ServerPlayer p) {
        LivingEntity t = aim(p, 32, 26);
        if (t == null) {
            p.displayClientMessage(Component.literal("Look at a target.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        freeze(p, t, 64);
        SCRIPTS.add(new Script(p, 7, t, flatLook(p)));
        fx(p, Fx.ULT, p.position(), t.position(), 0, t.getId(), ULT_LENGTH);
        play(p.serverLevel(), p.position(), Sounds.FILM.get(), 1.6F, 0.85F);
        return true;
    }

    public static final int ULT_LENGTH = 104;
    public static final int ULT_HIT = 60;
    public static final int[] ULT_BLINKS = {46, 51, 56};

    // ------------------------------------------------------------------------------------------------ ticking

    private static void runScripts() {
        Iterator<Script> it = SCRIPTS.iterator();
        while (it.hasNext()) {
            Script s = it.next();
            ServerPlayer p = s.p;
            int age = (int) (p.serverLevel().getGameTime() - s.start);
            if (!p.isAlive() || p.isRemoved()) {
                it.remove();
                continue;
            }
            if (age == s.last) {
                continue;
            }
            s.last = age;
            boolean done = switch (s.slot) {
                case 2 -> barrageTick(s, age);
                case 7 -> ultTick(s, age);
                default -> true;
            };
            if (done) {
                it.remove();
            }
        }
    }

    private static boolean barrageTick(Script s, int age) {
        ServerPlayer p = s.p;
        LivingEntity t = s.target;
        if (t == null || !t.isAlive() || t.level() != p.level()) {
            return true;
        }
        if (age < 24) {
            if (age % 3 == 0) {
                // the frame puts him somewhere new round the target
                double ang = age * 2.399 + s.start;
                Vec3 around = new Vec3(Math.cos(ang), 0, Math.sin(ang)).scale(t.getBbWidth() * 0.5 + 1.0);
                Vec3 to = safe(p, t.position().add(around), p.position());
                Vec3 from = p.position();
                teleport(p, to, t.getEyePosition());
                fx(p, Fx.DASH, from, to, stacks(p), t.getId(), 4);
            }
            hit(p, t, 1.4F, null);
            if (age % 2 == 0) {
                play(p.serverLevel(), t.position(), Sounds.HIT.get(), 0.7F, 0.9F + 0.03F * (age % 8));
            }
            return false;
        }
        Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? s.dir : away.normalize();
        boolean wasFrozen = frozen(t);
        unfreeze(t, p.serverLevel(), wasFrozen);
        hit(p, t, wasFrozen ? 12 : 7, away.scale(1.6).add(0, 0.55, 0));
        play(p.serverLevel(), t.position(), Sounds.HEAVY.get(), 1.4F, 0.8F);
        return true;
    }

    private static boolean ultTick(Script s, int age) {
        ServerPlayer p = s.p;
        LivingEntity t = s.target;
        ServerLevel level = p.serverLevel();
        if (t == null || !t.isAlive() || t.level() != p.level()) {
            return true;
        }
        if (age < ULT_BLINKS[0]) {
            // planning: he stands, still as a held frame
            teleport(p, s.origin, t.getEyePosition());
            p.setDeltaMovement(Vec3.ZERO);
            return false;
        }
        for (int i = 0; i < ULT_BLINKS.length; i++) {
            if (age == ULT_BLINKS[i]) {
                Vec3 tp = t.position();
                Vec3 d = tp.subtract(s.origin).multiply(1, 0, 1);
                d = d.lengthSqr() < 1.0E-4 ? s.dir : d.normalize();
                double k = (i + 1) / (double) (ULT_BLINKS.length + 1);
                Vec3 from = p.position();
                Vec3 to = safe(p, s.origin.lerp(tp.subtract(d.scale(1.6)), k), from);
                teleport(p, to, t.getEyePosition());
                Net.fx(level, new Fx(Fx.ULT_BLINK, from, to, i + 1, p.getId(), t.getId(), 0));
                play(level, to, Sounds.BOOM.get(), 2.0F, 1.0F + 0.15F * i);
            }
        }
        if (age > ULT_BLINKS[ULT_BLINKS.length - 1] && age < ULT_HIT) {
            p.setDeltaMovement(Vec3.ZERO);
        }
        if (age == ULT_HIT) {
            Vec3 tp = t.position();
            Vec3 d = tp.subtract(s.origin).multiply(1, 0, 1);
            d = d.lengthSqr() < 1.0E-4 ? s.dir : d.normalize();
            Vec3 at = safe(p, tp.subtract(d.scale(t.getBbWidth() * 0.5 + 0.9)), p.position());
            teleport(p, at, t.getEyePosition());
            unfreeze(t, level, true);
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), 42 * power(p));
            t.setDeltaMovement(d.scale(4.2).add(0, 1.1, 0));
            t.hurtMarked = true;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(6), e -> valid(p, e) && e != t)) {
                Vec3 away = e.position().subtract(tp).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? d : away.normalize();
                e.invulnerableTime = 0;
                e.hurt(p.damageSources().playerAttack(p), 9);
                e.setDeltaMovement(away.scale(1.6).add(0, 0.6, 0));
                e.hurtMarked = true;
            }
            Net.fx(level, new Fx(Fx.ULT_HIT, t.getBoundingBox().getCenter(), d, 1, p.getId(), t.getId(), 0));
            play(level, tp, Sounds.ULT_HIT.get(), 3.0F, 1.0F);
        }
        return age >= ULT_LENGTH;
    }

    private static void holdFrozen(net.minecraft.server.MinecraftServer server) {
        Iterator<Map.Entry<Integer, Frozen>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Frozen f = it.next().getValue();
            LivingEntity e = f.e;
            if (!e.isAlive() || e.isRemoved()) {
                if (e instanceof Mob m) {
                    m.setNoAi(f.hadNoAi);
                }
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) e.level();
            if (level.getGameTime() >= f.until) {
                it.remove();
                release(f, level, false);
                continue;
            }
            e.setDeltaMovement(Vec3.ZERO);
            e.fallDistance = 0;
            if (e.position().distanceToSqr(f.at) > 0.0025) {
                if (e instanceof ServerPlayer sp) {
                    sp.connection.teleport(f.at.x, f.at.y, f.at.z, f.yaw, f.pitch);
                } else {
                    e.teleportTo(f.at.x, f.at.y, f.at.z);
                }
            }
            if (e instanceof Player) {
                e.setYRot(f.yaw);
                e.setXRot(f.pitch);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        runScripts();
        holdFrozen(e.getServer());
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (Sorcerers.get(p) == Sorcerer.NAOYA && p.tickCount % 10 == 0) {
                Accel a = ACCEL.get(p.getUUID());
                if (a != null && a.stacks > 0 && p.serverLevel().getGameTime() - a.last > 60) {
                    a.stacks = 0;
                }
                sync(p);
            }
        }
    }

    /** A frozen body can't act; Naoya can't be touched mid-ult. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity attacker && frozen(attacker)) {
            e.setCanceled(true);
            return;
        }
        if (e.getEntity() instanceof ServerPlayer p) {
            for (Script s : SCRIPTS) {
                if (s.p == p && s.slot == 7) {
                    e.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SCRIPTS.removeIf(s -> s.p == e.getEntity());
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        for (Frozen f : FROZEN.values()) {
            if (f.e instanceof Mob m) {
                m.setNoAi(f.hadNoAi);
            }
        }
        FROZEN.clear();
        SCRIPTS.clear();
        READY.clear();
        ACCEL.clear();
    }
}
