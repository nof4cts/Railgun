package com.firestar.rose.client;

import static com.firestar.rose.client.gfx.Look.hit;
import static com.firestar.rose.client.gfx.Look.window;

import com.firestar.rose.RoseMod;
import com.firestar.rose.client.cam.Lens;
import com.firestar.rose.client.gfx.Embers;
import com.firestar.rose.client.gfx.Frame;
import com.firestar.rose.client.gfx.Look;
import com.firestar.rose.common.RBeats;
import com.firestar.rose.common.RMove;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The client half of every Requiem Rose technique, on the beats in {@link RBeats}: what is heard each tick, what light
 * is in the world each frame ({@link Frame}), and what each frame does to the picture ({@link Look}).
 */
public final class RoseChoreo {
    static final Vec3 UP = new Vec3(0, 1, 0);
    static final int ROSE = 0xFF5FA2, BLUSH = 0xFFC8E0, CRIMSON = 0xB3124F, WHITE = 0xFFFFFF;
    static final int[] LIGHT = {Look.INK};
    static final int[] HEAVY = {Look.BLOWOUT, Look.INK, Look.NEGATIVE};
    static final int[] CUT = {Look.VOID, Look.INK, Look.VOID};
    static final int[] IGNITE = {Look.BLOWOUT, Look.NEGATIVE, Look.VOID, Look.INK, Look.NEGATIVE, Look.BLOWOUT};
    static final int[] RAY = {Look.BLOWOUT, Look.VOID, Look.NEGATIVE, Look.INK};
    static final int[] BURST = {Look.BLOWOUT, Look.INK, Look.VOID, Look.NEGATIVE, Look.INK, Look.VOID};
    static final int[] SNAP = {Look.VOID, Look.BLOWOUT, Look.NEGATIVE, Look.INK};
    static final int[] BLOOM = {Look.BLOWOUT, Look.TORN, Look.TORN, Look.VOID, Look.NEGATIVE, Look.TORN, Look.INK, Look.BLOWOUT, Look.VOID,
            Look.NEGATIVE, Look.INK};
    private static final Random R = new Random();
    private static final Map<Integer, AuraHum> HUMS = new HashMap<>();

    private RoseChoreo() {}

    // ------------------------------------------------------------------------------------------------ helpers

    static void play(Vec3 at, SoundEvent s, float vol, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && s != null) {
            level.playLocalSound(at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch, false);
        }
    }

    static void voice(RoseCast c, SoundEvent s) {
        play(c.casterFeet(0).add(0, 1.6, 0), s, 2.2F, 1.0F);
    }

    /** The plane of a cut, rolled round the reaper's forward axis: u across, v forward. */
    static Vec3[] plane(Vec3 fwd, Vec3 right, double rollDeg) {
        double r = Math.toRadians(rollDeg);
        Vec3 u = right.scale(Math.cos(r)).add(UP.scale(Math.sin(r)));
        return new Vec3[]{u, fwd};
    }

    static Vec3 reach(Vec3 from, Vec3 dir, double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return from.add(dir.scale(range));
        }
        BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : from.add(dir.scale(range));
    }

    static Vec3 ground(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return at;
        }
        BlockHitResult hit = mc.level.clip(new ClipContext(at.add(0, 2, 0), at.subtract(0, 30, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : at;
    }

    /**
     * A straight cut from a to b drawn as a very shallow crescent (so it still has the taper and the white edge), bowed
     * a little toward {@code bow}.
     */
    static void straight(Frame f, Vec3 a, Vec3 b, Vec3 bow, double width, float reveal, float fade, float heat, float seed, boolean ink) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.05) {
            return;
        }
        Vec3 dn = d.normalize();
        Vec3 n = bow.subtract(dn.scale(bow.dot(dn)));
        n = n.lengthSqr() < 1.0E-4 ? UP.subtract(dn.scale(dn.y)) : n;
        n = n.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : n.normalize();
        double R = len * 1.4;
        Vec3 mid = a.add(d.scale(0.5));
        double sag = R - Math.sqrt(Math.max(0, R * R - len * len / 4));
        Vec3 c = mid.subtract(n.scale(R - sag));
        double half = Math.toDegrees(Math.asin(Math.min(1, len / 2 / R)));
        // u along n, v along d: angle 90 - half .. 90 + half sweeps from a to b... a is at -d side
        Vec3 u = dn, v = n;
        double a0 = 90 + half, a1 = 90 - half;
        // with u = dn: point at angle th = c + R(dn cos th + n sin th); th = 90+half gives -dn side (a)
        if (ink) {
            f.slash(c, u, v, R, a0, a1, width, reveal, fade, heat, seed);
        } else {
            f.cut(c, u, v, R, a0, a1, width, reveal, fade, heat, seed);
        }
    }

    /** An anime impact star: spikes of light thrown out of the point of contact, a ring blown open round them. */
    static void star(Frame f, Vec3 p, float t, float at, float size, long seed) {
        float life = 4 + size * 1.5F;
        float k = (t - at) / life;
        if (k < 0 || k > 1) {
            return;
        }
        Random r = new Random(seed);
        float fade = (1 - k) * (1 - k);
        int spikes = 8 + (int) (size * 3);
        for (int i = 0; i < spikes; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.8, r.nextGaussian()).normalize();
            double len = size * (0.6 + r.nextDouble() * 0.8);
            double in = len * (0.1 + 0.6 * Math.sqrt(k)), out = len * (0.5 + 0.8 * Math.sqrt(k));
            f.line(p.add(d.scale(in)), p.add(d.scale(out)), (0.05 + 0.05 * size) * (1 - k), fade, 0.9F);
        }
        f.flare(p, size * (1.4 + k), fade, 1.0F);
        Vec3 n = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
        f.ring(p, n, size * (0.3 + 1.2 * Math.sqrt(k)), 0.25 * size * (1 - k) + 0.05, fade, 0.8F, r.nextFloat(), 1);
        f.shock(p, n.cross(UP).lengthSqr() < 1e-4 ? UP : n.cross(UP).normalize(), size * (0.5 + 2.5 * k), 0.6 * size, fade);
    }

    /** Seconds, for things that move on their own. */
    static float clock() {
        return (float) ((System.nanoTime() / 1.0E9) % 3600.0);
    }

    // ------------------------------------------------------------------------------------------------ the scythe

    /** How big the scythe is: life-size, except in the Last Bloom where it grows past the clouds. */
    public static float scytheGrow(Player p, float partial) {
        RoseCast c = RoseCasts.of(p);
        if (c == null || c.move != RMove.LAST_BLOOM) {
            return 1.0F;
        }
        float t = c.time(partial);
        float up = Look.smooth((t - RBeats.BLOOM_GROW) / 40.0F);
        float down = Look.smooth((t - RBeats.BLOOM_WAVE_END) / 30.0F);
        return 1.0F + 13.0F * up * (1 - down);
    }

    /** How hot the blade burns: brighter when it cuts. */
    public static float bladeHeat(Player p, float partial) {
        RoseCast c = RoseCasts.of(p);
        if (c == null) {
            return 0.55F;
        }
        float t = c.time(partial);
        return switch (c.move) {
            case REAP -> 0.6F + 0.4F * window(t, 1, 3, 6, 10);
            case CRESCENT -> 0.6F + 0.4F * window(t, 0, 6, 8, 10);
            case RIFT -> 0.6F + 0.4F * window(t, 6, 22, 28, 40);
            case LAST_BLOOM -> 0.6F + 0.4F * window(t, RBeats.BLOOM_GROW, RBeats.BLOOM_HALO, RBeats.BLOOM_IMPACT, RBeats.BLOOM_WAVE_END);
            default -> 0.7F;
        };
    }

    // ------------------------------------------------------------------------------------------------ per tick: sound and loose light

    static void begin(RoseCast c) {
    }

    static void tick(RoseCast c, int t) {
        Vec3 me = c.casterFeet(0).add(0, 1.0, 0);
        Vec3 tp = c.targetCentre(0);
        Random r = new Random(c.seed * 31L + t);
        switch (c.move) {
            case REAP -> {
                int step = c.seed & 3;
                if (t == 1) {
                    play(me, step == 3 ? RoseMod.SWING_HEAVY.get() : RoseMod.SWING.get(), 1.0F, 0.9F + step * 0.08F + r.nextFloat() * 0.08F);
                }
                if (t == RBeats.REAP_HIT) {
                    Vec3 at = c.hasTarget() ? tp : me.add(c.fwd.scale(2.4));
                    if (c.hasTarget()) {
                        play(at, step == 3 ? RoseMod.CUT_HEAVY.get() : RoseMod.CUT.get(), 1.1F, 0.9F + r.nextFloat() * 0.2F);
                        Embers.spray(at, at.subtract(me), 0.7F, step == 3 ? 46 : 22, step == 3 ? 16 : 10, 0.45F, 0.05F, 0.9F);
                        Embers.soot(at, step == 3 ? 10 : 4, 2.5F, 0.9F, 0.7F);
                    }
                    if (step == 3) {
                        Vec3 g = ground(c.casterFeet(0).add(c.fwd.scale(2.6)));
                        play(g, RoseMod.SUB.get(), 0.9F, 1.1F);
                        Embers.spray(g, UP, 0.8F, 40, 11, 0.6F, 0.05F, 0.8F);
                        Embers.soot(g, 12, 3, 1.2F, 1.0F);
                    }
                }
            }
            case CRESCENT -> {
                if (t == 2) {
                    play(me, RoseMod.SWING.get(), 0.7F, 0.7F);
                }
                if (t == RBeats.CRESCENT_THROW) {
                    play(me, RoseMod.CRESCENT_THROW.get(), 1.2F, 1.0F);
                    play(me, RoseMod.CRESCENT_SPIN.get(), 1.0F, 1.0F);
                }
                if (t > RBeats.CRESCENT_THROW && t < RBeats.CRESCENT_HOME) {
                    Vec3 at = crescent(c, t);
                    if (t % 2 == 0) {
                        Embers.spray(at, UP, 1.2F, 3, 3, 0.5F, 0.04F, 0.7F);
                    }
                    if (c.hasTarget() && at.distanceTo(tp) < 2.2) {
                        play(tp, RoseMod.CUT.get(), 1.0F, 1.1F + r.nextFloat() * 0.2F);
                        Embers.burst(tp, 18, 9, 0.4F, 0.05F, 0.9F);
                    }
                }
                if (t == RBeats.CRESCENT_HOME) {
                    play(me, RoseMod.CRESCENT_CATCH.get(), 1.0F, 1.0F);
                    Embers.burst(c.hand(0), 20, 5, 0.35F, 0.04F, 1.0F);
                }
            }
            case TETHER -> {
                if (t == RBeats.TETHER_FIRE) {
                    play(me, RoseMod.TETHER_FIRE.get(), 1.1F, 1.0F);
                }
                if (t == RBeats.TETHER_BIND) {
                    play(tp, RoseMod.TETHER_BIND.get(), 1.2F, 1.0F);
                    Embers.burst(tp, 26, 7, 0.5F, 0.05F, 0.9F);
                    Embers.soot(tp, 8, 1.5F, 1.0F, 0.6F);
                }
                if (t == RBeats.TETHER_YANK) {
                    play(tp, RoseMod.TETHER_YANK.get(), 1.2F, 1.0F);
                }
                if (t == RBeats.TETHER_CUT - 2) {
                    play(me, RoseMod.SWING_HEAVY.get(), 1.0F, 1.05F);
                }
                if (t == RBeats.TETHER_CUT) {
                    play(tp, RoseMod.CUT_HEAVY.get(), 1.3F, 1.0F);
                    play(tp, RoseMod.SUB.get(), 0.8F, 1.2F);
                    Embers.spray(tp, UP, 0.6F, 50, 15, 0.6F, 0.05F, 0.9F);
                }
            }
            case STEP -> {
                if (t == RBeats.STEP_VANISH) {
                    play(c.feet.add(0, 1, 0), RoseMod.VANISH.get(), 1.0F, 1.0F);
                    Embers.soot(c.feet.add(0, 1, 0), 14, 2.5F, 0.9F, 0.8F);
                    Embers.burst(c.feet.add(0, 1, 0), 18, 6, 0.4F, 0.04F, 0.8F);
                    Embers.soot(c.exit.add(0, 1, 0), 10, 2.0F, 0.8F, 0.7F);
                }
                if (t == RBeats.STEP_CUT && c.hasTarget()) {
                    play(tp, RoseMod.CUT_HEAVY.get(), 1.2F, 1.05F);
                    Embers.spray(tp, tp.subtract(c.exit.add(0, 1, 0)), 0.6F, 36, 13, 0.5F, 0.05F, 0.9F);
                }
            }
            case RAY -> {
                Vec3 o = rayOrigin(c, 0);
                if (t == 0) {
                    play(o, RoseMod.RAY_CHARGE.get(), 2.0F, 1.0F);
                }
                if (t == 30) {
                    voice(c, RoseMod.VOICE_RAY.get());
                }
                if (t >= 5 && t <= 20 && t % 3 == 0) {
                    Embers.burst(o, 10, 7, 0.45F, 0.04F, 0.9F);
                }
                if (t > 20 && t < RBeats.RAY_FIRE) {
                    // energy pulled in from all round him
                    int n = 2 + t / 20;
                    for (int i = 0; i < n; i++) {
                        Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7, r.nextGaussian()).normalize();
                        double dist = 5 + r.nextDouble() * 6;
                        float life = 0.5F + r.nextFloat() * 0.3F;
                        Embers.spark(o.add(d.scale(dist)), d.scale(-dist / life), life, 0.05F, 0.8F);
                    }
                }
                if (t == RBeats.RAY_FIRE) {
                    play(o, RoseMod.RAY_FIRE.get(), 2.2F, 1.0F);
                    play(o, RoseMod.SUB.get(), 1.5F, 0.8F);
                    Embers.burst(o, 70, 18, 0.7F, 0.06F, 1.0F);
                    Embers.soot(c.feet, 24, 5, 1.4F, 1.2F);
                }
                if (t > RBeats.RAY_FIRE && t < RBeats.RAY_END) {
                    Vec3 end = reach(o, aim(c), RBeats.RAY_RANGE);
                    Embers.spray(end, aim(c).scale(-1), 0.9F, 8, 14, 0.5F, 0.06F, 0.9F);
                    if (t % 4 == 0) {
                        Embers.soot(end, 3, 3, 1.0F, 1.0F);
                    }
                }
                if (t == RBeats.RAY_END) {
                    Vec3 end = reach(o, aim(c), RBeats.RAY_RANGE);
                    play(end, RoseMod.RAY_END.get(), 1.8F, 1.0F);
                    Embers.burst(end, 80, 16, 0.9F, 0.07F, 0.9F);
                }
            }
            case REQUIEM -> {
                Vec3 tf = c.targetFeet(0);
                for (int i = 0; i < RBeats.REQUIEM_CUTS.length; i++) {
                    if (t == RBeats.REQUIEM_CUTS[i]) {
                        Vec3 a = RBeats.requiemFrom(tf, c.seed, i), b = RBeats.requiemTo(tf, c.seed, i);
                        play(tp, RoseMod.VANISH.get(), 0.8F, 1.0F + i * 0.04F);
                        play(tp, RoseMod.CUT.get(), 1.1F, 0.9F + i * 0.05F);
                        Embers.soot(a, 6, 1.5F, 0.8F, 0.6F);
                        Embers.spray(tp, b.subtract(a), 0.5F, 18, 10, 0.4F, 0.04F, 0.9F);
                    }
                }
                if (t == RBeats.REQUIEM_GATHER) {
                    play(tp, RoseMod.REQUIEM_GATHER.get(), 1.4F, 1.0F);
                }
                if (t == RBeats.REQUIEM_BURST) {
                    play(tp, RoseMod.REQUIEM_BURST.get(), 2.0F, 1.0F);
                    play(tp, RoseMod.SUB.get(), 1.4F, 0.85F);
                    Embers.burst(tp, 140, 22, 1.0F, 0.07F, 1.0F);
                    Embers.soot(tp, 30, 5, 1.6F, 1.3F);
                    for (int i = 0; i < RBeats.REQUIEM_CUTS.length; i++) {
                        Vec3 a = RBeats.requiemFrom(tf, c.seed, i), b = RBeats.requiemTo(tf, c.seed, i);
                        for (int k = 0; k < 5; k++) {
                            Embers.spark(a.lerp(b, k / 4.0), new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(4), 0.6F, 0.05F, 0.9F);
                        }
                    }
                }
            }
            case RIFT -> {
                Vec3 rc = riftCentre(c);
                if (t == 8) {
                    voice(c, RoseMod.VOICE_RIFT.get());
                }
                if (t == 4) {
                    play(me, RoseMod.SUMMON.get(), 1.0F, 0.8F);
                }
                if (t > 4 && t < RBeats.RIFT_CUT && t % 2 == 0) {
                    Embers.smoke(c.casterFeet(0).add(r.nextGaussian() * 0.6, 2.2 + r.nextDouble(), r.nextGaussian() * 0.6), new Vec3(0, 1.2, 0), 1.0F, 0.6F);
                }
                if (t == RBeats.RIFT_CUT - 3) {
                    play(me, RoseMod.SWING_HEAVY.get(), 1.4F, 0.7F);
                }
                if (t == RBeats.RIFT_CUT) {
                    play(rc, RoseMod.RIFT_TEAR.get(), 2.0F, 1.0F);
                    play(rc, RoseMod.SUB.get(), 1.4F, 0.9F);
                    Embers.burst(rc, 90, 18, 0.8F, 0.06F, 1.0F);
                }
                if (t == RBeats.RIFT_OPEN || t == RBeats.RIFT_OPEN + 40) {
                    play(rc, RoseMod.RIFT_HUM.get(), 1.5F, 1.0F);
                }
                if (t > RBeats.RIFT_OPEN && t < RBeats.RIFT_CLOSE) {
                    // the world being drunk into the tear
                    for (int i = 0; i < 3; i++) {
                        Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5, r.nextGaussian()).normalize();
                        double dist = 5 + r.nextDouble() * 7;
                        Vec3 from = rc.add(d.scale(dist));
                        float life = 0.7F;
                        if (r.nextBoolean()) {
                            Embers.spark(from, rc.subtract(from).scale(1 / life), life, 0.05F, 0.7F);
                        } else {
                            Embers.smoke(from, rc.subtract(from).scale(0.8 / life), life, 0.5F);
                        }
                    }
                }
                if (t == RBeats.RIFT_SNAP) {
                    play(rc, RoseMod.RIFT_SNAP.get(), 2.0F, 1.0F);
                    play(rc, RoseMod.SUB.get(), 1.6F, 0.8F);
                    Embers.burst(rc, 160, 24, 1.0F, 0.07F, 1.0F);
                    Embers.soot(rc, 30, 6, 1.6F, 1.4F);
                }
            }
            case LAST_BLOOM -> {
                Vec3 g = c.feet;
                if (t == 0) {
                    play(me, RoseMod.BLOOM_RISE.get(), 1.8F, 1.0F);
                }
                if (t == 10) {
                    voice(c, RoseMod.VOICE_BLOOM_1.get());
                }
                if (t < RBeats.BLOOM_RISE_END + 10 && t % 2 == 0) {
                    Embers.smoke(g.add(r.nextGaussian() * 1.4, 0.2, r.nextGaussian() * 1.4), new Vec3(r.nextGaussian(), 0.4, r.nextGaussian()), 1.6F, 1.0F);
                }
                if (t == RBeats.BLOOM_SKY) {
                    play(me, RoseMod.BLOOM_SKY.get(), 2.0F, 1.0F);
                }
                if (t == RBeats.BLOOM_GROW) {
                    play(me, RoseMod.BLOOM_GROW.get(), 2.0F, 1.0F);
                }
                if (t == 96) {
                    voice(c, RoseMod.VOICE_BLOOM_2.get());
                }
                if (t == RBeats.BLOOM_CUT) {
                    play(me, RoseMod.BLOOM_CUT.get(), 2.4F, 1.0F);
                    play(me, RoseMod.SUB.get(), 2.0F, 0.7F);
                }
                if (t == RBeats.BLOOM_IMPACT) {
                    play(g.add(c.fwd.scale(12)), RoseMod.BLOOM_IMPACT.get(), 3.0F, 1.0F);
                    for (int i = 0; i < 12; i++) {
                        Vec3 p = ground(g.add(c.fwd.scale(3 + i * 3.4)));
                        Embers.spray(p, UP, 0.5F, 26, 22, 1.2F, 0.08F, 1.0F);
                        Embers.soot(p, 8, 6, 2.2F, 2.0F);
                    }
                }
                if (t > RBeats.BLOOM_IMPACT && t < RBeats.BLOOM_WAVE_END && t % 2 == 0) {
                    double k = (t - RBeats.BLOOM_IMPACT) / (double) (RBeats.BLOOM_WAVE_END - RBeats.BLOOM_IMPACT);
                    double rad = RBeats.BLOOM_REACH * (1 - Math.pow(1 - k, 2.2));
                    for (int i = 0; i < 10; i++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        Vec3 p = ground(g.add(Math.cos(a) * rad, 0, Math.sin(a) * rad));
                        Embers.spray(p, UP, 0.6F, 4, 12, 0.8F, 0.06F, 0.9F);
                        if (i % 3 == 0) {
                            Embers.smoke(p, new Vec3(Math.cos(a), 0.6, Math.sin(a)).scale(3), 1.6F, 1.5F);
                        }
                    }
                }
            }
            case ASCEND -> {
                Vec3 f0 = c.feet;
                if (t == 4 || t == 30 || t == 58 || t == 80 || t == 90 || t == 94) {
                    play(me, RoseMod.HEARTBEAT.get(), 1.4F, 1.0F);
                }
                if (t == 14) {
                    voice(c, RoseMod.VOICE_ASCEND_1.get());
                }
                if (t == 66) {
                    voice(c, RoseMod.VOICE_ASCEND_2.get());
                }
                if (t == 40) {
                    play(me, RoseMod.SUMMON.get(), 1.2F, 0.7F);
                }
                if (t < RBeats.ASCEND_IGNITE && t % 2 == 0) {
                    double rr = 1.0 + r.nextDouble() * 2.5;
                    double a = r.nextDouble() * Math.PI * 2;
                    Embers.smoke(f0.add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), new Vec3(-Math.sin(a) * 0.8, 0.9 + t / 60.0, Math.cos(a) * 0.8), 1.5F,
                            0.6F + t / 120F);
                }
                if (t == RBeats.ASCEND_IGNITE) {
                    play(me, RoseMod.AURA_IGNITE.get(), 2.0F, 1.0F);
                    play(me, RoseMod.SUB.get(), 1.6F, 0.75F);
                    Embers.burst(me, 160, 20, 1.0F, 0.06F, 1.0F);
                    Embers.soot(me, 40, 7, 1.4F, 1.4F);
                }
            }
            case RELEASE -> {
                if (t == 0) {
                    play(me, RoseMod.AURA_RELEASE.get(), 1.4F, 1.0F);
                    Embers.soot(me, 16, 2, 1.0F, 0.7F);
                    Embers.burst(me, 30, 4, 0.6F, 0.04F, 0.8F);
                }
            }
            default -> {
            }
        }
    }

    /** A reaper's form changed (seen by everyone): the scythe and aura answer through RoseForms; this is the sound. */
    static void formChanged(Entity e, int form) {
        if (form == 1) {
            Embers.burst(e.position().add(0, 1, 0), 40, 6, 0.6F, 0.05F, 1.0F);
        }
    }

    /** Every client tick: the aura's hum follows each ascended reaper; embers drift up off him. */
    public static void ambientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            HUMS.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            boolean up = RoseForms.ascended(p);
            AuraHum hum = HUMS.get(p.getId());
            if (up && (hum == null || hum.isStopped())) {
                hum = new AuraHum(p);
                HUMS.put(p.getId(), hum);
                mc.getSoundManager().play(hum);
            }
            if (up && R.nextFloat() < 0.6F) {
                Vec3 at = p.position().add(R.nextGaussian() * 0.5, R.nextDouble() * 1.8, R.nextGaussian() * 0.5);
                Embers.mote(at, new Vec3(R.nextGaussian() * 0.2, 1.2 + R.nextDouble(), R.nextGaussian() * 0.2), 0.9F, 0.06F + R.nextFloat() * 0.06F, 0.8F);
            }
            if (up && R.nextFloat() < 0.15F) {
                Embers.smoke(p.position().add(R.nextGaussian() * 0.4, 0.1, R.nextGaussian() * 0.4), new Vec3(0, 0.5, 0), 1.2F, 0.5F);
            }
        }
        HUMS.values().removeIf(h -> h.isStopped());
    }

    /** The aura's sound: the author's recording, looping, following its reaper, gone when the aura is. */
    static final class AuraHum extends AbstractTickableSoundInstance {
        private final Player who;

        AuraHum(Player p) {
            super(RoseMod.AURA_LOOP.get(), SoundSource.PLAYERS, RandomSource.create());
            this.who = p;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.x = p.getX();
            this.y = p.getY() + 1;
            this.z = p.getZ();
        }

        @Override
        public void tick() {
            if (who.isRemoved() || !RoseForms.ascended(who)) {
                volume -= 0.08F;
                if (volume <= 0.01F) {
                    stop();
                }
                return;
            }
            volume = Math.min(0.85F, volume + 0.05F);
            x = who.getX();
            y = who.getY() + 1;
            z = who.getZ();
        }
    }

    // ------------------------------------------------------------------------------------------------ positions

    static Vec3 aim(RoseCast c) {
        Vec3 d = c.point.subtract(c.eye);
        return d.lengthSqr() < 1.0E-4 ? c.dir : d.normalize();
    }

    static Vec3 rayOrigin(RoseCast c, float partial) {
        return c.eye.subtract(0, 0.35, 0).add(aim(c).scale(1.7));
    }

    static Vec3 crescent(RoseCast c, float t) {
        Entity e = c.caster();
        Vec3 home = e != null ? e.getEyePosition(0).subtract(0, 0.3, 0) : c.eye;
        return RBeats.crescentAt(c.eye.subtract(0, 0.3, 0).add(c.dir.scale(0.8)), c.dir, c.right, t, home);
    }

    static Vec3 riftCentre(RoseCast c) {
        return c.eye.add(c.fwd.scale(RBeats.RIFT_DIST)).add(0, 1.0, 0);
    }

    // ------------------------------------------------------------------------------------------------ per frame: light in the world

    public static void frame(Frame f, float partial) {
        for (RoseCast c : RoseCasts.all()) {
            float t = c.time(partial);
            if (t >= 0) {
                draw(f, c, t, partial);
            }
        }
        // the ignition halo of every freshly ascended reaper, and the dark ring at an ascended reaper's feet
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Player p : mc.level.players()) {
                float a = RoseForms.aura(p, partial);
                if (a > 0.05F) {
                    Vec3 feet = p.getPosition(partial).add(0, 0.05, 0);
                    f.ring(feet, UP, 1.3 + 0.08 * Math.sin(f.time * 5), 0.5, 0.35F * a, 0.4F, 0.3F, 0.5F);
                }
            }
        }
    }

    private static void draw(Frame f, RoseCast c, float t, float partial) {
        Vec3 feet = c.casterFeet(partial);
        Vec3 chest = feet.add(0, 1.2, 0);
        Vec3 tp = c.targetCentre(partial);
        float s = (c.seed & 1023) / 1023F;
        switch (c.move) {
            case REAP -> {
                int step = c.seed & 3;
                float at = RBeats.REAP_HIT;
                float reveal = Mth.clamp((t - (at - 2.2F)) / 2.6F, 0, 1);
                float fade = 1 - Look.smooth((t - at - 2) / 7.0F);
                Vec3 fwd = c.facing(partial), right = new Vec3(-fwd.z, 0, fwd.x);
                switch (step) {
                    case 0 -> {
                        Vec3[] pl = plane(fwd, right, 38);
                        f.slash(chest.add(fwd.scale(0.3)), pl[0], pl[1], 2.6, -25, 205, 0.75, reveal, fade, 0.9F, s);
                    }
                    case 1 -> {
                        Vec3[] pl = plane(fwd, right, -32);
                        f.slash(chest.add(fwd.scale(0.3)).subtract(0, 0.2, 0), pl[0], pl[1], 2.6, 205, -25, 0.75, reveal, fade, 0.9F, s);
                    }
                    case 2 -> {
                        f.slash(feet.add(0, 0.95, 0), right, fwd, 3.0, -40, 330, 0.8, reveal, fade, 1.0F, s);
                        f.ring(feet.add(0, 0.95, 0), UP, 0.8 + 3.2 * Mth.clamp((t - at) / 6, 0, 1), 0.4, fade * window(t, at - 1, at, at + 2, at + 8),
                                0.6F, s, 1);
                    }
                    default -> {
                        // overhead cleave: down through the target into the ground
                        f.slash(chest.add(0, 0.6, 0), UP, fwd, 3.4, -35, 165, 1.15, reveal, fade, 1.0F, s);
                        Vec3 g = ground(feet.add(fwd.scale(3.0)));
                        float k = Mth.clamp((t - at) / 9, 0, 1);
                        f.ring(g.add(0, 0.06, 0), UP, 0.5 + 5.5 * Math.sqrt(k), 0.8 * (1 - k) + 0.1, (1 - k), 0.85F, s, 1);
                        f.shock(g.add(0, 0.5, 0), UP, 1 + 7 * k, 1.5, 1 - k);
                        straight(f, g.add(fwd.scale(-1.0)).add(0, 0.05, 0), g.add(fwd.scale(5.0)).add(0, 0.05, 0), UP, 0.35,
                                Mth.clamp((t - at) / 2, 0, 1), 1 - Look.smooth((t - at - 3) / 9), 1.0F, s, true);
                    }
                }
                if (c.hasTarget()) {
                    star(f, tp, t, at, step == 3 ? 2.6F : 1.4F, c.seed * 13L);
                }
            }
            case CRESCENT -> {
                // the wind-up: the blade drawn back, burning brighter
                if (t > RBeats.CRESCENT_THROW && t < RBeats.CRESCENT_HOME + 1) {
                    for (int g = 0; g < 4; g++) {
                        float tt = t - g * 0.45F;
                        if (tt < RBeats.CRESCENT_THROW) {
                            continue;
                        }
                        Vec3 at = crescent(c, tt);
                        float spin = tt * 62;
                        float fade = g == 0 ? 1 : 0.5F / g;
                        Vec3 tilt = UP.add(c.right.scale(0.25)).normalize();
                        Vec3 u = c.right.subtract(tilt.scale(c.right.dot(tilt))).normalize();
                        Vec3 v = tilt.cross(u);
                        if (g == 0) {
                            f.slash(at, u, v, 1.5, spin, spin + 150, 0.55, 1, fade, 1.0F, s);
                            f.slash(at, u, v, 1.5, spin + 180, spin + 330, 0.55, 1, fade, 1.0F, s + 0.5F);
                            f.flare(at, 1.6, 0.7F, 0.9F);
                            f.shock(at, tilt, 1.8, 0.8, 0.8F);
                        } else {
                            f.cut(at, u, v, 1.5, spin, spin + 150, 0.5, 1, fade, 0.8F, s);
                            f.cut(at, u, v, 1.5, spin + 180, spin + 330, 0.5, 1, fade, 0.8F, s + 0.5F);
                        }
                    }
                }
                if (c.hasTarget()) {
                    float near = -1;
                    for (int k = RBeats.CRESCENT_THROW; k <= RBeats.CRESCENT_HOME; k++) {
                        if (crescent(c, k).distanceTo(tp) < 2.2) {
                            near = k;
                            break;
                        }
                    }
                    if (near > 0) {
                        star(f, tp, t, near, 1.8F, c.seed * 17L);
                    }
                }
            }
            case TETHER -> {
                Vec3 hand = c.hand(partial);
                if (t >= RBeats.TETHER_FIRE && t < RBeats.TETHER_CUT + 2) {
                    float k = Mth.clamp((t - RBeats.TETHER_FIRE) / (RBeats.TETHER_BIND - RBeats.TETHER_FIRE), 0, 1);
                    Vec3 head = hand.lerp(tp, Look.smooth(k));
                    float fade = 1 - Look.smooth((t - RBeats.TETHER_ARRIVE) / 4);
                    chain(f, hand, head, t, fade, k < 1 ? 1 - k : 0, c.right);
                    if (k < 1) {
                        f.flare(head, 1.0, 0.9F, 1.0F);
                    }
                }
                if (t >= RBeats.TETHER_BIND && t < RBeats.TETHER_CUT + 4) {
                    float k = Mth.clamp((t - RBeats.TETHER_BIND) / 4, 0, 1);
                    float fade = 1 - Look.smooth((t - RBeats.TETHER_CUT) / 4);
                    for (int i = 0; i < 3; i++) {
                        double rad = (1.3 - 0.75 * Look.smooth(k)) * (i == 1 ? 1.1 : 0.9);
                        Vec3 n = UP.add(c.right.scale(0.25 * (i - 1))).normalize();
                        f.ring(tp.add(0, (i - 1) * 0.55, 0), n, rad, 0.16, fade, 0.9F, s + i * 0.2F, 1);
                    }
                    star(f, tp, t, RBeats.TETHER_BIND, 1.2F, c.seed * 19L);
                }
                if (t >= RBeats.TETHER_CUT - 2) {
                    float at = RBeats.TETHER_CUT;
                    float reveal = Mth.clamp((t - (at - 2)) / 2.5F, 0, 1);
                    float fade = 1 - Look.smooth((t - at - 2) / 8);
                    Vec3 fwd = c.facing(partial);
                    f.slash(feet.add(0, 1.0, 0).add(fwd.scale(0.6)), UP, fwd, 2.8, 200, 20, 0.95, reveal, fade, 1.0F, s);
                    star(f, tp, t, at, 2.4F, c.seed * 23L);
                }
            }
            case STEP -> {
                float a1 = window(t, RBeats.STEP_VANISH - 1, RBeats.STEP_VANISH, RBeats.STEP_VANISH + 1, RBeats.STEP_VANISH + 6);
                if (a1 > 0) {
                    float open = Look.smooth((t - RBeats.STEP_VANISH + 1) / 2) * (1 - Look.smooth((t - RBeats.STEP_VANISH - 1) / 4));
                    Vec3 side = c.right;
                    f.rift(c.feet.add(0, 1.0, 0), UP, side, 2.6, 0.45, open, a1, s);
                    f.rift(c.exit.add(0, 1.0, 0), UP, side, 2.6, 0.45, open, a1, s + 0.3F);
                }
                if (c.hasTarget() && t >= RBeats.STEP_CUT - 2) {
                    float at = RBeats.STEP_CUT;
                    Vec3 fwd = tp.subtract(c.exit.add(0, 1, 0)).multiply(1, 0, 1);
                    fwd = fwd.lengthSqr() < 1e-4 ? c.fwd : fwd.normalize();
                    Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
                    Vec3[] pl = plane(fwd, right, 12);
                    f.slash(c.exit.add(0, 1.2, 0), pl[0], pl[1], 3.0, -30, 210, 0.85, Mth.clamp((t - at + 2) / 2.2F, 0, 1),
                            1 - Look.smooth((t - at - 2) / 8), 1.0F, s);
                    star(f, tp, t, at, 2.0F, c.seed * 29L);
                }
            }
            case RAY -> drawRay(f, c, t, partial, s);
            case REQUIEM -> {
                Vec3 tf = c.targetFeet(partial);
                float gather = window(t, RBeats.REQUIEM_GATHER - 2, RBeats.REQUIEM_GATHER + 4, RBeats.REQUIEM_BURST - 1, RBeats.REQUIEM_BURST);
                for (int i = 0; i < RBeats.REQUIEM_CUTS.length; i++) {
                    float at = RBeats.REQUIEM_CUTS[i];
                    if (t < at - 1) {
                        break;
                    }
                    Vec3 a = RBeats.requiemFrom(tf, c.seed, i), b = RBeats.requiemTo(tf, c.seed, i);
                    float reveal = Mth.clamp((t - at + 1) / 1.5F, 0, 1);
                    float fade = t < RBeats.REQUIEM_BURST ? 0.75F + 0.25F * (float) hit(t, at, 3) + 0.4F * gather : 1 - Look.smooth((t - RBeats.REQUIEM_BURST) / 6);
                    Vec3 bow = UP.add(new Vec3(Math.cos(i * 2.1), 0, Math.sin(i * 2.1)).scale(0.6));
                    straight(f, a, b, bow, 0.42 + 0.2 * gather, reveal, Math.min(1, fade), 0.8F + 0.2F * gather, s + i * 0.11F, true);
                    star(f, tp, t, at, 1.2F, c.seed * 31L + i);
                    // a sliver of tear where he vanished and where he came out
                    float sl = window(t, at - 1, at, at + 1, at + 4);
                    if (sl > 0) {
                        f.rift(a, UP, b.subtract(a).cross(UP).lengthSqr() < 1e-4 ? c.right : b.subtract(a).cross(UP).normalize(), 2.2, 0.3, sl, sl, s + i);
                    }
                }
                if (gather > 0) {
                    f.flare(tp, 2.5 + 2 * gather, 0.6F * gather, 1.0F);
                    for (int i = 0; i < 3; i++) {
                        Vec3 n = new Vec3(Math.sin(f.time * 3 + i * 2), Math.cos(f.time * 2.3 + i), Math.sin(f.time * 1.7 + i * 4)).normalize();
                        f.ring(tp, n, 2.6 - 1.5 * gather, 0.2, gather, 0.9F, s + i * 0.3F, 1);
                    }
                }
                star(f, tp, t, RBeats.REQUIEM_BURST, 4.5F, c.seed * 37L);
                float b = window(t, RBeats.REQUIEM_BURST, RBeats.REQUIEM_BURST + 1, RBeats.REQUIEM_BURST + 3, RBeats.REQUIEM_BURST + 16);
                if (b > 0) {
                    float k = Mth.clamp((t - RBeats.REQUIEM_BURST) / 16, 0, 1);
                    f.ring(tp, UP, 1 + 9 * Math.sqrt(k), 1.2 * (1 - k) + 0.2, b, 1.0F, s, 1);
                    f.ring(tp, c.fwd, 0.5 + 6 * Math.sqrt(k), 0.8 * (1 - k) + 0.1, b, 0.9F, s + 0.3F, 1);
                    f.shock(tp, UP, 2 + 12 * k, 3, b);
                }
            }
            case RIFT -> drawRift(f, c, t, partial, s);
            case LAST_BLOOM -> drawBloom(f, c, t, partial, s);
            case ASCEND -> drawAscend(f, c, t, partial, s);
            case RELEASE -> {
                float a = window(t, 0, 1, 2, 10);
                f.ring(feet.add(0, 1, 0), UP, 0.6 + 2 * Mth.clamp(t / 10, 0, 1), 0.3, a, 0.6F, s, 1);
            }
            default -> {
            }
        }
    }

    /** The tether: a lash of light with links of rose rings strung along it, whipping as it flies. */
    static void chain(Frame f, Vec3 a, Vec3 b, float t, float fade, float whip, Vec3 right) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1 || fade <= 0) {
            return;
        }
        Vec3 dn = d.normalize();
        Vec3 side = dn.cross(UP).lengthSqr() < 1e-4 ? right : dn.cross(UP).normalize();
        int n = Math.max(4, (int) (len / 0.32));
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double k = i / (double) n;
            double wave = Math.sin(k * Math.PI * 3 - t * 1.4) * Math.sin(k * Math.PI) * 0.6 * whip;
            Vec3 p = a.add(d.scale(k)).add(side.scale(wave)).add(0, Math.sin(k * Math.PI) * 0.15 * whip, 0);
            f.line(prev, p, 0.07, fade, 0.9F);
            Vec3 ln = (i % 2 == 0) ? dn.cross(side) : side;
            f.ring(p, dn.add(ln.scale(0.3)).normalize(), 0.12, 0.05, fade * 0.9F, 0.8F, (float) k, 1);
            prev = p;
        }
    }

    private static void drawRay(Frame f, RoseCast c, float t, float partial, float s) {
        Vec3 o = rayOrigin(c, partial);
        Vec3 d = aim(c);
        Vec3 right = d.cross(UP).lengthSqr() < 1e-4 ? c.right : d.cross(UP).normalize();
        Vec3 upv = right.cross(d);
        // the gathering: an orb of rose light swelling at the blade, rings turning round it, the early burst
        float charge = window(t, 2, 20, RBeats.RAY_FIRE, RBeats.RAY_FIRE + 2);
        if (charge > 0) {
            float k = Mth.clamp(t / RBeats.RAY_FIRE, 0, 1);
            double orb = 0.25 + 1.1 * k * k;
            f.flare(o, orb * 2.2 * (1 + 0.08 * Math.sin(f.time * 30)), charge, 1.0F);
            f.flare(o, orb * 5.0, 0.35F * charge, 0.6F);
            for (int i = 0; i < 4; i++) {
                double a1 = f.time * (1.4 + i * 0.5) + i * 1.3, a2 = f.time * (0.9 + i * 0.3) + i;
                Vec3 n = new Vec3(Math.cos(a1) * Math.sin(a2), Math.cos(a2), Math.sin(a1) * Math.sin(a2));
                f.ring(o, n, orb * (1.3 + 0.2 * i), 0.12 + 0.05 * k, charge * 0.85F, 0.8F + 0.2F * k, s + i * 0.2F, 1);
            }
            // living current crackling over the orb
            Random fr = new Random((long) (f.time * 20) * 7 + c.seed);
            for (int i = 0; i < 2 + (int) (k * 4); i++) {
                Vec3 p1 = o.add(new Vec3(fr.nextGaussian(), fr.nextGaussian(), fr.nextGaussian()).normalize().scale(orb * 1.4));
                Vec3 p2 = p1.add(new Vec3(fr.nextGaussian(), fr.nextGaussian(), fr.nextGaussian()).scale(orb * 0.9));
                f.line(p1, p2, 0.04, charge, 1.0F);
            }
            // pressure on the ground beneath him
            f.ring(c.feet.add(0, 0.06, 0), UP, 2.0 + 3 * k + 0.3 * Math.sin(f.time * 4), 0.9, 0.5F * charge * k, 0.5F, s, 0.3F);
            // the early burst of the charge
            float b = window(t, 4, 6, 12, 22);
            if (b > 0) {
                f.ring(o, d, 1 + 4 * Mth.clamp((t - 4) / 18, 0, 1), 0.5, b, 0.9F, s, 1);
            }
        }
        star(f, o, t, RBeats.RAY_FIRE, 2.2F, c.seed * 41L);
        float on = window(t, RBeats.RAY_FIRE - 0.5F, RBeats.RAY_FIRE + 1.5F, RBeats.RAY_END - 3, RBeats.RAY_END + 10);
        if (on > 0) {
            Vec3 end = reach(o, d, RBeats.RAY_RANGE);
            float thin = t > RBeats.RAY_END - 3 ? 1 - Look.smooth((t - RBeats.RAY_END + 3) / 13) * 0.85F : 1;
            float swell = 1 + 0.35F * (float) hit(t, RBeats.RAY_FIRE, 5);
            double w = 1.0 * thin * swell;
            f.beam(o, end, 2.6 * w, 0.35F * on, 0.45F, s);
            f.beam(o, end, 1.35 * w, 0.8F * on, 0.8F, s + 0.3F);
            f.beam(o, end, 0.55 * w, on, 1.0F, s + 0.6F);
            double len = o.distanceTo(end);
            // rings of pressure riding down it, and the dark twist wound round it
            for (int i = 0; i < 7; i++) {
                double k = (f.time * 1.6 + i / 7.0) % 1.0;
                f.ring(o.add(d.scale(len * k)), d, 2.4 * w * (1 - 0.3 * k), 0.25 * w, on * 0.6F * (float) (1 - k), 0.7F, s + i * 0.1F, 1);
            }
            for (int strand = 0; strand < 2; strand++) {
                int n = Math.max(8, (int) (len * 1.5));
                Vec3 prev = null;
                for (int i = 0; i <= n; i++) {
                    double k = i / (double) n;
                    double ang = k * len * 0.9 - f.time * 14 + strand * Math.PI;
                    Vec3 p = o.add(d.scale(len * k)).add(right.scale(Math.cos(ang) * 1.6 * w)).add(upv.scale(Math.sin(ang) * 1.6 * w));
                    if (prev != null) {
                        f.line(prev, p, 0.08 * w, on * 0.7F, 0.95F);
                    }
                    prev = p;
                }
            }
            // where it bites
            f.flare(end, 6 * w, on, 1.0F);
            for (int i = 0; i < 3; i++) {
                Vec3 n = new Vec3(Math.sin(f.time * 4 + i * 1.7), Math.cos(f.time * 3.1 + i), Math.sin(f.time * 2.2 + i * 2.9)).normalize();
                f.ring(end, n, 2.6 * w * (1 + 0.1 * Math.sin(f.time * 25)), 0.4 * w, on * 0.8F, 0.9F, s + i * 0.25F, 1);
            }
            star(f, end, t, (float) Math.floor(t / 3) * 3, 2.8F, c.seed * 43L + (long) Math.floor(t / 3));
            f.ring(o, d, 1.6 * w * (1 + 0.12 * Math.sin(f.time * 35)), 0.35 * w, on, 1.0F, s, 1);
            f.shock(o.add(d.scale(2)), d, 3.5 * w, 2 * w, on);
            Vec3 g = ground(end);
            if (g.distanceTo(end) < 3) {
                f.ring(g.add(0, 0.08, 0), UP, 3 + 1.5 * Math.sin(f.time * 3), 2.5, on * 0.7F, 0.6F, s, 0.4F);
            }
        }
    }

    private static void drawRift(Frame f, RoseCast c, float t, float partial, float s) {
        Vec3 rc = riftCentre(c);
        Vec3 feet = c.casterFeet(partial);
        // dark energy gathering along the raised blade
        float wind = window(t, 4, 14, RBeats.RIFT_CUT - 1, RBeats.RIFT_CUT + 1);
        if (wind > 0) {
            Vec3 above = feet.add(0, 3.4, 0).subtract(c.fwd.scale(0.4));
            for (int i = 0; i < 3; i++) {
                double a = f.time * (3 + i) + i * 2.1;
                Vec3 u = new Vec3(Math.cos(a), 0, Math.sin(a));
                f.slash(above, u, UP.add(c.fwd.scale(0.3)).normalize(), 1.4 + 0.3 * i, 0, 140, 0.3, 1, wind * 0.8F, 0.6F, s + i * 0.3F);
            }
            f.ring(feet.add(0, 0.06, 0), UP, 2.5 + 2 * wind, 1.2, 0.6F * wind, 0.5F, s, 0.3F);
        }
        // the cut: a colossal crescent falling from above him through the air ahead
        float at = RBeats.RIFT_CUT;
        if (t >= at - 2) {
            float reveal = Mth.clamp((t - at + 2) / 2.5F, 0, 1);
            float fade = 1 - Look.smooth((t - at - 4) / 12);
            f.slash(feet.add(0, 1.5, 0), UP, c.fwd, RBeats.RIFT_DIST + 1.2, -20, 175, 2.4, reveal, fade, 1.0F, s);
            star(f, rc, t, at, 5.0F, c.seed * 47L);
        }
        // the tear, held open: jaws of rose round the void
        float open;
        if (t < RBeats.RIFT_OPEN - 4) {
            open = 0;
        } else if (t < RBeats.RIFT_CLOSE) {
            float k = Mth.clamp((t - (RBeats.RIFT_OPEN - 4)) / 10, 0, 1);
            open = (float) (1 - Math.exp(-k * 5) * Math.cos(k * 9)) * 0.98F;
            open = Math.min(1.1F, Math.max(0, open)) * (1 + 0.04F * (float) Math.sin(f.time * 7));
        } else {
            open = Math.max(0, 1 - Look.smooth((t - RBeats.RIFT_CLOSE) / (RBeats.RIFT_SNAP - RBeats.RIFT_CLOSE)));
        }
        if (open > 0.01F) {
            f.rift(rc, UP, c.right, RBeats.RIFT_HEIGHT, 2.6, open, 1, s);
            // lightning crawling round its rim
            Random fr = new Random((long) (f.time * 18) * 13 + c.seed);
            for (int i = 0; i < 4; i++) {
                double y = (fr.nextDouble() - 0.5) * RBeats.RIFT_HEIGHT * 0.8;
                double half = 2.6 * 0.3 / 0.3 * open * Math.pow(Math.max(0, 1 - Math.pow(2 * y / RBeats.RIFT_HEIGHT, 2)), 0.8) * 0.3;
                Vec3 p1 = rc.add(0, y, 0).add(c.right.scale((fr.nextBoolean() ? 1 : -1) * half));
                Vec3 p2 = p1.add(new Vec3(fr.nextGaussian(), fr.nextGaussian(), fr.nextGaussian()).scale(1.2));
                f.line(p1, p2, 0.05, 0.9F, 1.0F);
            }
            f.flare(rc.add(0, RBeats.RIFT_HEIGHT * 0.5, 0), 2.5, 0.6F * open, 0.8F);
            f.flare(rc.subtract(0, RBeats.RIFT_HEIGHT * 0.5, 0), 2.5, 0.6F * open, 0.8F);
        }
        float snap = window(t, RBeats.RIFT_SNAP, RBeats.RIFT_SNAP + 1, RBeats.RIFT_SNAP + 3, RBeats.RIFT_SNAP + 16);
        if (snap > 0) {
            float k = Mth.clamp((t - RBeats.RIFT_SNAP) / 16, 0, 1);
            f.ring(rc, c.fwd, 1 + 12 * Math.sqrt(k), 1.5 * (1 - k) + 0.2, snap, 1.0F, s, 1);
            f.ring(rc.subtract(0, RBeats.RIFT_HEIGHT * 0.4, 0), UP, 1 + 10 * Math.sqrt(k), 1.0 * (1 - k) + 0.2, snap, 0.8F, s + 0.4F, 1);
            f.shock(rc, c.fwd, 2 + 16 * k, 4, snap);
            straight(f, rc.add(0, RBeats.RIFT_HEIGHT * 0.5, 0), rc.subtract(0, RBeats.RIFT_HEIGHT * 0.5, 0), c.right, 0.5, 1, snap, 1.0F, s, false);
        }
        star(f, rc, t, RBeats.RIFT_SNAP, 6.0F, c.seed * 53L);
    }

    private static void drawBloom(Frame f, RoseCast c, float t, float partial, float s) {
        Vec3 feet = c.casterFeet(partial);
        Vec3 g = c.feet;
        // rising: a column of black smoke and rose light under him
        float rise = window(t, 0, 6, RBeats.BLOOM_RISE_END, RBeats.BLOOM_RISE_END + 20);
        if (rise > 0) {
            f.beam(g, feet.add(0, 0.5, 0), 0.9, 0.35F * rise, 0.5F, s);
            f.ring(g.add(0, 0.06, 0), UP, 2 + Mth.clamp(t / 30, 0, 1) * 4, 1.6, 0.6F * rise, 0.5F, s, 0.3F);
        }
        // the halo behind him: great crescents turning
        float halo = window(t, RBeats.BLOOM_HALO, RBeats.BLOOM_HALO + 20, RBeats.BLOOM_CUT, RBeats.BLOOM_CUT + 8);
        if (halo > 0) {
            Vec3 back = feet.add(0, 2.2, 0).subtract(c.fwd.scale(2.5));
            for (int i = 0; i < 6; i++) {
                double a0 = f.time * 22 + i * 60;
                f.slash(back, c.right, UP, 7.5 + 0.4 * Math.sin(f.time + i), a0, a0 + 38, 1.4, 1, halo * 0.9F, 0.9F, s + i * 0.15F);
            }
            f.ring(back, c.fwd, 7.8, 0.4, halo * 0.6F, 0.9F, s, 1);
            f.ring(back, c.fwd, 3.2 + 0.3 * Math.sin(f.time * 2), 0.25, halo * 0.5F, 0.8F, s + 0.5F, 1);
        }
        // the cut: a blade's arc the size of the sky, falling the length of the land ahead
        float at = RBeats.BLOOM_CUT;
        if (t >= at - 3) {
            float reveal = Mth.clamp((t - at + 3) / 6, 0, 1);
            float fade = 1 - Look.smooth((t - RBeats.BLOOM_IMPACT - 8) / 26);
            double R = RBeats.BLOOM_REACH * 0.62;
            f.slash(feet.add(0, 2.0, 0), UP, c.fwd, R, -25, 168, 6.5, reveal, fade, 1.0F, s);
            f.cut(feet.add(0, 2.0, 0), UP, c.fwd, R * 0.92, -20, 160, 3.0, reveal, fade * 0.7F, 0.8F, s + 0.4F);
        }
        // the land answers: a burning seam along the cut, a wave of light rolling out to the horizon
        float imp = RBeats.BLOOM_IMPACT;
        if (t >= imp) {
            float k = Mth.clamp((t - imp) / (RBeats.BLOOM_WAVE_END - imp), 0, 1);
            float fade = 1 - Look.smooth((t - RBeats.BLOOM_WAVE_END) / 30);
            Vec3 a = ground(g.add(c.fwd.scale(1.5)));
            Vec3 b = ground(g.add(c.fwd.scale(RBeats.BLOOM_REACH)));
            float seam = Math.min(1, (t - imp) / 3) * fade;
            straight(f, a.add(0, 0.1, 0), b.add(0, 0.1, 0), UP, 1.6, Math.min(1, (t - imp) / 3), seam, 1.0F, s, true);
            // pillars of light along it
            for (int i = 0; i < 9; i++) {
                double kk = (i + 0.5) / 9;
                Vec3 p = a.lerp(b, kk);
                float pa = window(t, imp + i * 0.6F, imp + i * 0.6F + 1, imp + 10 + i, imp + 28 + i);
                f.beam(p, p.add(0, 14 + 10 * Math.sin(i * 1.7), 0), 0.9 + 0.3 * Math.sin(i), pa * 0.8F, 0.9F, s + i * 0.1F);
            }
            double rad = RBeats.BLOOM_REACH * (1 - Math.pow(1 - k, 2.2));
            f.ring(g.add(0, 0.3, 0), UP, rad + 0.5, 3.5 * (1 - k) + 0.6, fade * (1 - k * 0.6F), 1.0F, s, 1);
            f.ring(g.add(0, 1.2, 0), UP, rad * 0.8 + 0.5, 1.6 * (1 - k) + 0.3, fade * (1 - k) * 0.7F, 0.8F, s + 0.3F, 1);
            f.shock(g.add(0, 1.5, 0), UP, rad + 1, 6, fade * (1 - k));
            star(f, a.lerp(b, 0.35).add(0, 1, 0), t, imp, 9.0F, c.seed * 59L);
        }
    }

    private static void drawAscend(Frame f, RoseCast c, float t, float partial, float s) {
        Vec3 feet = c.casterFeet(partial);
        Vec3 chest = feet.add(0, 1.1, 0);
        // the dark: black crescents circling him, faster and wider as it builds, then dragged inward
        float dark = window(t, 6, 40, RBeats.ASCEND_IGNITE - 2, RBeats.ASCEND_IGNITE + 1);
        if (dark > 0) {
            float build = Mth.clamp((t - 6) / 84, 0, 1);
            float pull = Look.smooth((t - 88) / 10);
            int n = 3 + (int) (build * 5);
            for (int i = 0; i < n; i++) {
                double speed = 70 + 260 * build;
                double a0 = f.time * speed + i * (360.0 / n);
                double rr = (1.4 + 1.6 * build + (i % 3) * 0.35) * (1 - 0.7 * pull);
                Vec3 ctr = feet.add(0, 0.3 + (i % 4) * 0.55 + build * 0.6, 0);
                Vec3 tilt = UP.add(new Vec3(Math.cos(i * 1.9), 0, Math.sin(i * 1.9)).scale(0.25)).normalize();
                Vec3 u = new Vec3(1, 0, 0).subtract(tilt.scale(tilt.x)).normalize();
                Vec3 v = tilt.cross(u);
                // ink crescents, and a hairline of rose light riding each
                f.slash(ctr, u, v, rr, a0, a0 + 70 + 40 * build, 0.25 + 0.25 * build, 1, dark * (0.6F + 0.4F * build), 0.35F + 0.4F * build,
                        s + i * 0.13F);
            }
            // cracks of rose light across the ground under him
            for (int i = 0; i < 8; i++) {
                double a = i / 8.0 * Math.PI * 2 + s * 6;
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                float ck = Mth.clamp((t - 40 - i * 3) / 20, 0, 1) * dark;
                if (ck > 0) {
                    Vec3 a1 = c.feet.add(dir.scale(0.6)).add(0, 0.05, 0);
                    Vec3 b1 = c.feet.add(dir.scale(0.6 + 4.5 * ck)).add(0, 0.05, 0);
                    straight(f, a1, b1, UP.add(new Vec3(-dir.z, 0, dir.x)), 0.12, 1, ck, 0.8F, s + i * 0.07F, false);
                }
            }
            f.ring(c.feet.add(0, 0.05, 0), UP, 3.2 * (1 - pull) + 0.6, 1.5, 0.5F * dark, 0.4F, s, 0.3F);
        }
        // the last breath: everything sucked into him
        float suck = window(t, 86, 92, RBeats.ASCEND_IGNITE - 1, RBeats.ASCEND_IGNITE);
        if (suck > 0) {
            float k = Mth.clamp((t - 86) / 12, 0, 1);
            for (int i = 0; i < 3; i++) {
                Vec3 n = new Vec3(Math.sin(i * 2.1 + t * 0.1), Math.cos(i * 1.3), Math.sin(i * 3.7)).normalize();
                f.ring(chest, n, 5.0 * (1 - k) + 1.1, 0.22, suck * (1 - 0.8F * k), 0.8F, s + i * 0.2F, 1);
            }
        }
        // ignition
        float ig = RBeats.ASCEND_IGNITE;
        star(f, chest, t, ig, 7.0F, c.seed * 61L);
        float b = window(t, ig, ig + 1, ig + 4, ig + 30);
        if (b > 0) {
            float k = Mth.clamp((t - ig) / 30, 0, 1);
            f.ring(c.feet.add(0, 0.1, 0), UP, 1 + 16 * Math.sqrt(k), 2.4 * (1 - k) + 0.3, b, 1.0F, s, 1);
            f.ring(chest, c.fwd, 1 + 9 * Math.sqrt(k), 1.2 * (1 - k) + 0.2, b, 0.9F, s + 0.4F, 1);
            f.shock(chest, UP, 2 + 20 * k, 5, b);
            f.beam(c.feet, c.feet.add(0, 26 * Math.sqrt(k) + 2, 0), 2.6 * (1 - k) + 0.3, b * 0.8F, 1.0F, s);
        }
        // the scythe called out of a tear in the air beside his hand
        float call = window(t, ig + 2, ig + 5, ig + 10, ig + 18);
        if (call > 0) {
            Vec3 at = feet.add(c.right.scale(0.55)).add(0, 1.4, 0).add(c.fwd.scale(0.5));
            f.rift(at, UP.add(c.fwd.scale(0.4)).normalize(), c.right, 3.2, 0.6, call, call, s);
        }
    }

    // ------------------------------------------------------------------------------------------------ the picture

    public static void screen(Look l, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (RoseCast c : RoseCasts.all()) {
            float t = c.time(partial);
            if (t < 0) {
                continue;
            }
            boolean near = c.mine || mc.player.position().distanceTo(c.point) < 48 || mc.player.position().distanceTo(c.feet) < 48;
            if (!near) {
                continue;
            }
            screen(l, c, t, partial);
        }
        if (RoseForms.ascended(mc.player)) {
            l.vignette(0x1A0010, 0.22F);
            l.tint(0.04F);
        }
    }

    private static float[] at(Vec3 world) {
        Frame f = com.firestar.rose.client.gfx.Canvas.lastFrame();
        return f == null ? new float[]{0.5F, 0.5F} : f.screen(world);
    }

    private static void screen(Look l, RoseCast c, float t, float partial) {
        Vec3 tp = c.targetCentre(partial);
        float cine = c.mine && Lens.active() ? Lens.weight() : 0;
        switch (c.move) {
            case REAP -> {
                int step = c.seed & 3;
                float at = RBeats.REAP_HIT;
                if (c.hasTarget()) {
                    l.shake((step == 3 ? 0.7F : 0.25F) * hit(t, at, 4));
                    l.chroma((step == 3 ? 1.2F : 0.5F) * hit(t, at, 3));
                    l.punch((step == 3 ? 0.6F : 0.25F) * hit(t, at, 3));
                    l.impact(step == 3 ? HEAVY : LIGHT, t - at, at(tp), ROSE);
                } else if (step == 3) {
                    l.shake(0.5F * hit(t, at, 4));
                }
            }
            case CRESCENT -> {
                l.punch(0.25F * hit(t, RBeats.CRESCENT_THROW, 3));
                l.chroma(0.4F * hit(t, RBeats.CRESCENT_THROW, 4));
            }
            case TETHER -> {
                l.punch(0.3F * hit(t, RBeats.TETHER_BIND, 3));
                l.chroma(0.6F * window(t, RBeats.TETHER_YANK, RBeats.TETHER_YANK + 1, RBeats.TETHER_ARRIVE, RBeats.TETHER_ARRIVE + 2));
                l.lines(at(tp), 0.4F * window(t, RBeats.TETHER_YANK, RBeats.TETHER_YANK + 1, RBeats.TETHER_ARRIVE, RBeats.TETHER_ARRIVE + 2));
                l.impact(HEAVY, t - RBeats.TETHER_CUT, at(tp), ROSE);
                l.shake(0.6F * hit(t, RBeats.TETHER_CUT, 5));
            }
            case STEP -> {
                l.chroma(1.4F * hit(t, RBeats.STEP_VANISH, 3));
                l.punch(0.4F * hit(t, RBeats.STEP_VANISH, 3));
                if (c.hasTarget()) {
                    l.impact(CUT, t - RBeats.STEP_CUT, at(tp), ROSE);
                    l.shake(0.4F * hit(t, RBeats.STEP_CUT, 4));
                }
            }
            case RAY -> {
                Vec3 o = rayOrigin(c, partial);
                float charge = window(t, 2, 30, RBeats.RAY_FIRE - 2, RBeats.RAY_FIRE + 1);
                float k = Mth.clamp(t / RBeats.RAY_FIRE, 0, 1);
                l.vignette(0x12000A, 0.6F * charge);
                l.desat(0.45F * charge * k);
                l.tint(0.3F * charge);
                l.shake(0.35F * charge * k * k);
                l.bars(Math.max(cine, 0.6F * charge));
                l.zoom(at(o), 0.25F * charge * k);
                l.flash(0xFFE0F0, 0.7F * window(t, 5, 6, 7, 12));
                l.impact(RAY, t - RBeats.RAY_FIRE, at(o), ROSE);
                l.flash(0xFFFFFF, 1.0F * hit(t, RBeats.RAY_FIRE, 4));
                l.punch(1.0F * hit(t, RBeats.RAY_FIRE, 5));
                float on = window(t, RBeats.RAY_FIRE, RBeats.RAY_FIRE + 2, RBeats.RAY_END - 3, RBeats.RAY_END + 4);
                l.shake(0.55F * on);
                l.chroma(0.9F * on);
                l.tint(0.25F * on);
                l.lines(at(reach(o, aim(c), RBeats.RAY_RANGE)), 0.6F * on);
                l.bars(on * 0.8F);
                l.flash(0xFFD8EA, 0.6F * hit(t, RBeats.RAY_END, 5));
            }
            case REQUIEM -> {
                float still = window(t, RBeats.REQUIEM_CUTS[0] - 1, RBeats.REQUIEM_CUTS[0], RBeats.REQUIEM_BURST - 1, RBeats.REQUIEM_BURST);
                l.desat(0.5F * still);
                l.vignette(0x0A0006, 0.5F * still);
                l.tint(0.35F * still);
                l.bars(Math.max(cine, 0.8F * still));
                for (int i = 0; i < RBeats.REQUIEM_CUTS.length; i++) {
                    int h = RBeats.REQUIEM_CUTS[i];
                    l.shake(0.3F * hit(t, h, 3));
                    l.chroma(0.7F * hit(t, h, 2));
                    // a single held frame on every third cut: the rhythm reads, the picture still breathes
                    if (i % 3 == 0) {
                        l.impact(LIGHT, t - h, at(tp), ROSE);
                    }
                }
                float gather = window(t, RBeats.REQUIEM_GATHER - 2, RBeats.REQUIEM_GATHER + 4, RBeats.REQUIEM_BURST - 1, RBeats.REQUIEM_BURST);
                l.desat(0.85F * gather);
                l.grain(0.5F * gather);
                l.impact(BURST, t - RBeats.REQUIEM_BURST, at(tp), ROSE);
                l.flash(0xFFFFFF, 1.1F * hit(t, RBeats.REQUIEM_BURST, 5));
                l.shake(1.1F * hit(t, RBeats.REQUIEM_BURST, 12));
                l.zoom(at(tp), 0.6F * hit(t, RBeats.REQUIEM_BURST, 6));
            }
            case RIFT -> {
                Vec3 rc = riftCentre(c);
                float wind = window(t, 4, 14, RBeats.RIFT_CUT - 1, RBeats.RIFT_CUT + 1);
                l.vignette(0x0A0006, 0.5F * wind);
                l.desat(0.4F * wind);
                l.bars(Math.max(cine, wind));
                l.impact(CUT, t - RBeats.RIFT_CUT, at(rc), ROSE);
                l.flash(0xFFE6F2, 0.9F * hit(t, RBeats.RIFT_CUT, 4));
                l.split(1, 0, 0, 1.0F * hit(t, RBeats.RIFT_CUT, 6));
                l.shake(0.9F * hit(t, RBeats.RIFT_CUT, 8));
                float open = window(t, RBeats.RIFT_OPEN, RBeats.RIFT_OPEN + 6, RBeats.RIFT_CLOSE, RBeats.RIFT_SNAP);
                l.shake(0.25F * open);
                l.chroma(0.6F * open);
                l.tint(0.3F * open);
                l.vignette(0x0C0008, 0.4F * open);
                l.impact(SNAP, t - RBeats.RIFT_SNAP, at(rc), ROSE);
                l.flash(0xFFFFFF, 1.0F * hit(t, RBeats.RIFT_SNAP, 5));
                l.shake(1.1F * hit(t, RBeats.RIFT_SNAP, 10));
                l.zoom(at(rc), 0.7F * hit(t, RBeats.RIFT_SNAP, 5));
            }
            case LAST_BLOOM -> {
                float sky = Look.smooth((t - RBeats.BLOOM_SKY) / 26) * (1 - Look.smooth((t - (RBeats.BLOOM_WAVE_END + 10)) / 34));
                l.sky(sky, 0.6F * sky);
                l.bars(Math.max(cine, 0.9F * sky));
                l.vignette(0x0A0006, 0.45F * sky);
                l.shake(0.15F * window(t, 0, 4, RBeats.BLOOM_RISE_END, RBeats.BLOOM_RISE_END + 10));
                float grow = window(t, RBeats.BLOOM_GROW, RBeats.BLOOM_GROW + 20, RBeats.BLOOM_CUT - 8, RBeats.BLOOM_CUT);
                l.shake(0.3F * grow);
                l.tint(0.3F * grow);
                float hush = window(t, RBeats.BLOOM_CUT - 10, RBeats.BLOOM_CUT - 4, RBeats.BLOOM_CUT, RBeats.BLOOM_CUT + 1);
                l.desat(0.9F * hush);
                l.grain(0.6F * hush);
                Vec3 seamMid = ground(c.feet.add(c.fwd.scale(RBeats.BLOOM_REACH * 0.4)));
                l.flash(0xFFE8F2, 0.7F * hit(t, RBeats.BLOOM_CUT, 2));
                l.lines(at(seamMid), 0.55F * window(t, RBeats.BLOOM_CUT, RBeats.BLOOM_CUT + 1, RBeats.BLOOM_IMPACT + 2, RBeats.BLOOM_IMPACT + 8));
                l.impact(BLOOM, t - RBeats.BLOOM_IMPACT, at(seamMid), ROSE);
                // the screen torn along the cut
                float[] sa = at(c.feet.add(c.fwd.scale(2))), sb = at(c.feet.add(c.fwd.scale(RBeats.BLOOM_REACH)));
                if (sa != null && sb != null) {
                    float dx = sb[0] - sa[0], dy = sb[1] - sa[1];
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 1e-3) {
                        float nx = -dy / len, ny = dx / len;
                        float aspect = 16 / 9F;
                        float off = (sa[0] - 0.5F) * aspect * nx + (sa[1] - 0.5F) * ny;
                        l.split(nx, ny, off, 1.6F * hit(t, RBeats.BLOOM_IMPACT, 10));
                    }
                }
                l.flash(0xFFF0F6, 0.9F * hit(t, RBeats.BLOOM_IMPACT, 4));
                l.shake(1.6F * hit(t, RBeats.BLOOM_IMPACT, 22));
                l.chroma(1.6F * hit(t, RBeats.BLOOM_IMPACT, 14));
                l.zoom(at(seamMid), 0.8F * hit(t, RBeats.BLOOM_IMPACT, 8));
                l.punch(1.2F * hit(t, RBeats.BLOOM_CUT, 6));
            }
            case ASCEND -> {
                float hush = window(t, 0, 10, RBeats.ASCEND_IGNITE, RBeats.ASCEND_IGNITE + 2);
                l.desat(0.75F * hush);
                l.vignette(0x000000, 0.65F * hush);
                l.grain(0.45F * hush);
                l.tint(0.25F * hush * Mth.clamp((t - 40) / 50, 0, 1));
                l.bars(Math.max(cine, hush));
                for (int b : new int[]{4, 30, 58, 80, 90, 94}) {
                    l.punch(0.35F * hit(t, b, 3));
                }
                l.zoom(at(c.feet.add(0, 1.2, 0)), 0.35F * window(t, 86, 94, RBeats.ASCEND_IGNITE, RBeats.ASCEND_IGNITE + 1));
                l.impact(IGNITE, t - RBeats.ASCEND_IGNITE, at(c.feet.add(0, 1.3, 0)), ROSE);
                l.flash(0xFFE6F2, 1.0F * hit(t, RBeats.ASCEND_IGNITE, 4));
                l.shake(1.3F * hit(t, RBeats.ASCEND_IGNITE, 14));
                l.chroma(1.4F * hit(t, RBeats.ASCEND_IGNITE, 10));
                l.tint(0.35F * window(t, RBeats.ASCEND_IGNITE, RBeats.ASCEND_IGNITE + 4, 130, 150));
            }
            default -> {
            }
        }
    }
}
