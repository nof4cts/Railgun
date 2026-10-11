package com.firestar.silver.client;

import static com.firestar.silver.client.fx.ScreenFx.hit;
import static com.firestar.silver.client.fx.ScreenFx.window;

import com.firestar.silver.client.fx.Impact;
import com.firestar.silver.client.fx.Light;
import com.firestar.silver.client.fx.Ribbon;
import com.firestar.silver.client.fx.ScreenFx;
import com.firestar.silver.client.fx.Shapes;
import com.firestar.silver.client.render.Afterimages;
import com.firestar.silver.common.Beats;
import com.firestar.silver.common.Move;
import com.firestar.silver.config.ZConfig;
import com.firestar.silver.network.S2CFx;
import com.firestar.silver.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The client half of every move, on the beats in {@link Beats}: sounds and light per tick, the strokes of light drawn
 * into the world each frame (the aura, beams, the sphere, ki blasts, lines of a rush), and what every frame does to
 * the picture. Nothing here uses a vanilla particle: all light is the mod's own.
 */
public final class Choreo {
    static final int SILVER = 0xEAF4FF, BLUE = 0x6FB8FF, CYAN = 0x8FE6FF, VIOLET = 0xB49CFF, GOLD = 0xFFE6A8, WHITE = 0xFFFFFF;
    static final Vec3 UP = new Vec3(0, 1, 0);

    static final int[][] LIGHT = {{Impact.INK, 1}};
    static final int[][] KICK = {{Impact.WHITEOUT, 1}, {Impact.INVERT, 1}};
    static final int[][] HEAVY = {{Impact.WHITEOUT, 1}, {Impact.INK, 1}, {Impact.INVERT, 1}, {Impact.LINES, 1}};
    static final int[][] BEAM = {{Impact.WHITEOUT, 1}, {Impact.HALFTONE, 1}, {Impact.INVERT, 1}, {Impact.LINES, 2}};
    static final int[][] SURGE = {{Impact.WHITEOUT, 1}, {Impact.INVERT, 1}, {Impact.HALFTONE, 1}, {Impact.LINES, 1}};
    static final int[][] SUMMIT = {{Impact.WHITEOUT, 1}, {Impact.INK, 1}, {Impact.INVERT, 1}, {Impact.LINES, 1}, {Impact.HALFTONE, 1}};
    static final int[][] BOOM = {{Impact.WHITEOUT, 1}, {Impact.INVERT, 1}, {Impact.LINES, 1}, {Impact.HALFTONE, 1}};

    private Choreo() {}

    // ------------------------------------------------------------------------------------------------ helpers

    private static void play(Vec3 at, SoundEvent s, float vol, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.playLocalSound(at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch, false);
        }
    }

    /** Where the hands are: a little below the eyes, out in front, along the aim. */
    static Vec3 hands(ClientCast c, float partial) {
        Entity e = c.caster();
        Vec3 eye = e != null ? e.getEyePosition(partial) : c.eye;
        return eye.subtract(0, 0.35, 0).add(aim(c).scale(0.75));
    }

    static Vec3 aim(ClientCast c) {
        Vec3 d = c.point.subtract(c.eye);
        return d.lengthSqr() < 1.0E-4 ? c.dir : d.normalize();
    }

    /** Where a beam fired from {@code from} along {@code dir} stops: the first block, or its full reach. */
    static Vec3 reach(ClientCast c, Vec3 from, Vec3 dir, double range) {
        Minecraft mc = Minecraft.getInstance();
        Entity who = c.caster() != null ? c.caster() : mc.player;
        if (mc.level == null || who == null) {
            return from.add(dir.scale(range));
        }
        BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : from.add(dir.scale(range));
    }

    /** A straight beam of constant width (a camera-facing ribbon), its edge breathing. */
    static void beam(List<Ribbon> out, Vec3 a, Vec3 b, float width, float alpha, int rgb, float time) {
        int n = 16;
        Vec3[] spine = new Vec3[n];
        float[] w = new float[n];
        float[] al = new float[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            spine[i] = a.lerp(b, k);
            w[i] = width * (0.92F + 0.08F * (float) Math.sin(time * 40 + i * 1.3)) * (k < 0.04F ? 0.6F + 10 * k : 1.0F);
            al[i] = alpha * (k > 0.97F ? (1 - k) / 0.03F : 1.0F);
        }
        out.add(new Ribbon(spine, null, w, al, rgb, Ribbon.Style.GLOW));
    }

    /** A spiral of light wound round the axis a-b. */
    static void spiral(List<Ribbon> out, Vec3 a, Vec3 b, double radius, double turns, double phase, float width, float alpha, int rgb) {
        Vec3 d = b.subtract(a);
        Vec3 dn = d.normalize();
        Vec3 u = dn.cross(Math.abs(dn.y) > 0.9 ? new Vec3(1, 0, 0) : UP).normalize();
        Vec3 v = dn.cross(u);
        int n = Math.max(16, (int) (turns * 18));
        Vec3[] spine = new Vec3[n];
        float[] w = new float[n];
        float[] al = new float[n];
        for (int i = 0; i < n; i++) {
            double k = i / (double) (n - 1);
            double ang = phase + k * turns * Math.PI * 2;
            spine[i] = a.add(d.scale(k)).add(u.scale(Math.cos(ang) * radius)).add(v.scale(Math.sin(ang) * radius));
            w[i] = width;
            al[i] = alpha * (float) Math.min(1.0, Math.min(k * 8, (1 - k) * 4));
        }
        out.add(new Ribbon(spine, null, w, al, rgb, Ribbon.Style.GLOW));
    }

    /** The aura's strength on a fighter this frame, including the build-up inside a transformation. */
    static float auraPower(Player p, float partial) {
        ClientState.Info i = ClientState.get(p);
        float power = i == null ? 0.0F : Mth.lerp(partial, i.powerO, i.power);
        ClientCast c = Casts.of(p);
        if (c != null && c.move == Move.AWAKEN_OMEN) {
            float t = c.time(partial);
            power = Math.max(power, 0.45F * Mth.clamp((t - 36) / 28.0F, 0, 1) * (0.5F + 0.5F * (float) Math.sin(t * 2.3F)));
        }
        if (c != null && c.move == Move.AWAKEN_ZENITH) {
            float t = c.time(partial);
            power = Math.max(power, 0.55F + 0.35F * Mth.clamp((t - 56) / 40.0F, 0, 1));
        }
        return power;
    }

    /** Position of volley blast {@code i} at time t, or null if not in flight. */
    static Vec3[] blast(ClientCast c, int i, float t) {
        int fire = Beats.VOLLEY_FIRST + i * Beats.VOLLEY_GAP;
        if (t < fire) {
            return null;
        }
        Vec3 d = Beats.blastDir(c.hasTarget() ? c.point.subtract(c.eye).normalize() : c.dir, c.seed, i);
        Vec3 from = c.eye.add(d.scale(0.8)).subtract(0, 0.25, 0).add(c.right.scale(i % 2 == 0 ? 0.35 : -0.35));
        Vec3 end = reach(c, c.eye.add(d.scale(0.8)), d, Beats.BLAST_RANGE);
        double dist = end.distanceTo(from);
        double travelled = (t - fire) * Beats.BLAST_SPEED;
        if (travelled > dist) {
            return null;
        }
        return new Vec3[]{from.add(end.subtract(from).normalize().scale(travelled)), end.subtract(from).normalize(), end};
    }

    static Vec3 spherePos(ClientCast c, float t) {
        Vec3 home = c.casterFeet(0).add(0, Beats.SPHERE_HEIGHT, 0);
        if (t < Beats.SPHERE_THROW) {
            return home;
        }
        float k = Mth.clamp((t - Beats.SPHERE_THROW) / Beats.SPHERE_FLIGHT, 0, 1);
        Vec3 tgt = c.hasTarget() ? c.targetCentre(0) : c.point.add(0, 1, 0);
        return home.lerp(tgt, k * k);
    }

    static float sphereRadius(ClientCast c, float t, boolean zenith) {
        float max = zenith ? 4.6F : 3.4F;
        if (t >= Beats.SPHERE_BOOM) {
            return 0.0F;
        }
        return max * Mth.clamp((t - 6) / (Beats.SPHERE_THROW - 10.0F), 0.05F, 1.0F);
    }

    // ------------------------------------------------------------------------------------------------ per tick

    public static void begin(ClientCast c) {
    }

    public static void tick(ClientCast c, int t) {
        Random r = new Random(c.seed * 31L + t);
        Vec3 user = c.casterFeet(0).add(0, 1.0, 0);
        Vec3 tp = c.targetCentre(0);
        boolean zenith = c.caster() instanceof Player p && ClientState.form(p) == 2;
        switch (c.move) {
            case STRIKE -> {
                int step = c.seed & 3;
                if (t == 0) {
                    play(user, ModSounds.SWING.get(), 0.8F, 1.0F + step * 0.1F);
                }
                if (t == Beats.STRIKE_HIT && c.hasTarget()) {
                    play(tp, ModSounds.STRIKE.get(), 1.0F, 0.95F + r.nextFloat() * 0.2F);
                    Light.hit(tp, c.dir, zenith ? CYAN : SILVER, step == 2 ? 1.2F : 0.7F);
                }
            }
            case HEAVY -> {
                if (t == 3) {
                    play(user, ModSounds.SWING.get(), 1.0F, 0.75F);
                }
                if (t == Beats.HEAVY_HIT) {
                    Vec3 at = c.hasTarget() ? tp : user.add(c.dir.scale(2.6));
                    play(at, ModSounds.HEAVY.get(), 1.2F, 1.0F);
                    Light.hit(at, c.dir, BLUE, 1.6F);
                    Light.ring(at, c.dir, 0.3F, 3.2F, 0.35F, SILVER, 0.4F);
                }
            }
            case VOLLEY -> {
                for (int i = 0; i < Beats.VOLLEY_SHOTS; i++) {
                    int fire = Beats.VOLLEY_FIRST + i * Beats.VOLLEY_GAP;
                    if (t == fire) {
                        Vec3 hand = hands(c, 0).add(c.right.scale(i % 2 == 0 ? 0.35 : -0.35));
                        play(hand, ModSounds.BLAST.get(), 0.9F, 0.9F + r.nextFloat() * 0.25F);
                        Light.flare(hand, 1.1F, BLUE, 0.18F);
                    }
                    Vec3[] now = blast(c, i, t), next = blast(c, i, t + 1);
                    if (now != null && next == null) {
                        play(now[2], ModSounds.BLAST_HIT.get(), 0.8F, 0.9F + r.nextFloat() * 0.3F);
                        Light.hit(now[2], now[1].scale(-1), BLUE, 0.8F);
                    }
                }
            }
            case VANISH -> {
                if (t == Beats.VANISH_AT) {
                    play(user, ModSounds.VANISH.get(), 1.0F, 1.0F);
                    play(user, ModSounds.BASS.get(), 0.7F, 1.2F);
                    Afterimages.ghost(c.casterId, c.feet, 0.6F);
                    Light.flare(c.feet.add(0, 1.0, 0), 1.6F, SILVER, 0.25F);
                    Light.flare(c.exit.add(0, 1.0, 0), 1.6F, SILVER, 0.25F);
                }
            }
            case WAVE -> {
                if (t == 2) {
                    play(user, ModSounds.CHARGE.get(), 1.1F, 1.0F);
                }
                if (t == Beats.WAVE_FIRE) {
                    play(user, ModSounds.WAVE_FIRE.get(), 1.4F, zenith ? 0.9F : 1.0F);
                    play(user, ModSounds.BASS.get(), 1.2F, 1.0F);
                    Light.ring(hands(c, 0), aim(c), 0.3F, 4.0F, 0.4F, SILVER, 0.4F);
                }
                if (t == Beats.WAVE_END) {
                    Vec3 end = reach(c, hands(c, 0), aim(c), Beats.WAVE_RANGE);
                    play(end, ModSounds.BLAST_HIT.get(), 1.4F, 0.7F);
                    Light.burst(end, 40, 12.0F, CYAN, 0.6F, 0.06F);
                }
            }
            case FLURRY -> {
                if (t == Beats.FLURRY_DASH) {
                    play(user, ModSounds.VANISH.get(), 1.0F, 1.1F);
                    play(user, ModSounds.BASS.get(), 0.9F, 1.2F);
                    Afterimages.ghost(c.casterId, c.feet, 0.5F);
                }
                if (t == Beats.FLURRY_FIRST) {
                    play(tp, ModSounds.FLURRY.get(), 1.2F, 1.0F);
                }
                if (t >= Beats.FLURRY_FIRST && t <= Beats.FLURRY_LAST) {
                    Vec3 at = tp.add((r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.9, (r.nextDouble() - 0.5) * 0.8);
                    Light.hit(at, at.subtract(user), t % 2 == 0 ? SILVER : CYAN, 0.45F);
                }
                if (t == Beats.FLURRY_FINAL) {
                    play(tp, ModSounds.HEAVY.get(), 1.3F, 0.9F);
                    play(tp, ModSounds.BASS.get(), 1.1F, 1.0F);
                    Light.hit(tp, tp.subtract(user), WHITE, 2.0F);
                    Light.ring(tp, tp.subtract(user), 0.3F, 4.5F, 0.4F, CYAN, 0.45F);
                }
            }
            case METEOR -> {
                if (t == 3 || t == Beats.METEOR_UP) {
                    play(user, ModSounds.VANISH.get(), 0.9F, 1.1F + t * 0.01F);
                }
                if (t == Beats.METEOR_LAUNCH) {
                    play(tp, ModSounds.LAUNCH.get(), 1.2F, 1.0F);
                    Light.hit(tp, UP, GOLD, 1.2F);
                    Light.ring(tp.subtract(0, 0.8, 0), UP, 0.3F, 3.0F, 0.3F, GOLD, 0.4F);
                }
                for (int h : Beats.METEOR_HITS) {
                    if (t == h) {
                        play(tp, ModSounds.STRIKE.get(), 1.0F, 0.9F + h * 0.01F);
                        Light.hit(tp, c.right, h % 2 == 0 ? GOLD : SILVER, 0.8F);
                    }
                }
                if (t == Beats.METEOR_SPIKE) {
                    play(tp, ModSounds.HEAVY.get(), 1.3F, 0.8F);
                    play(tp, ModSounds.BASS.get(), 1.0F, 1.1F);
                    Light.hit(tp, UP.scale(-1), WHITE, 1.6F);
                }
                if (t == Beats.METEOR_SLAM) {
                    Vec3 g = new Vec3(tp.x, c.feet.y + 0.05, tp.z);
                    play(g, ModSounds.SLAM.get(), 1.5F, 1.0F);
                    play(g, ModSounds.BASS.get(), 1.3F, 0.85F);
                    Light.flare(g.add(0, 0.5, 0), 4.0F, GOLD, 0.5F);
                    Light.ring(g, UP, 0.5F, 7.0F, 0.7F, GOLD, 0.7F);
                    Light.ring(g, UP, 0.3F, 4.0F, 0.4F, WHITE, 0.4F);
                    Light.spray(g, UP, 0.9F, 60, 12.0F, GOLD, 0.8F, 0.06F);
                }
            }
            case RUSH -> {
                for (int i = 0; i < Beats.RUSH_DASHES.length; i++) {
                    if (t == Beats.RUSH_DASHES[i]) {
                        Vec3 to = Beats.rushDash(c.exit, c.seed, i).add(0, 1.0, 0);
                        play(tp, ModSounds.VANISH.get(), 0.8F, 1.0F + i * 0.05F);
                        play(tp, ModSounds.STRIKE.get(), 1.0F, 0.9F + i * 0.04F);
                        if (i % 3 == 0) {
                            play(tp, ModSounds.BASS.get(), 0.8F, 1.0F + i * 0.04F);
                        }
                        Afterimages.ghost(c.casterId, to.subtract(0, 1.0, 0), 0.5F);
                        Light.hit(tp, tp.subtract(to), i % 2 == 0 ? CYAN : VIOLET, 0.9F);
                    }
                }
                if (t == Beats.RUSH_FINAL) {
                    play(tp, ModSounds.HEAVY.get(), 1.4F, 0.85F);
                    play(tp, ModSounds.SLAM.get(), 1.0F, 1.2F);
                    play(tp, ModSounds.BASS.get(), 1.3F, 0.9F);
                    Light.flare(tp, 4.5F, CYAN, 0.5F);
                    Light.burst(tp, 70, 15.0F, CYAN, 0.8F, 0.07F);
                    Light.ring(tp, UP, 0.5F, 8.0F, 0.6F, SILVER, 0.6F);
                }
            }
            case SPHERE -> {
                if (t == 4) {
                    play(user, ModSounds.SPHERE_GATHER.get(), 1.4F, 1.0F);
                }
                if (t == Beats.SPHERE_THROW) {
                    play(user, ModSounds.SPHERE_THROW.get(), 1.4F, 1.0F);
                    play(user, ModSounds.BASS.get(), 1.1F, 0.9F);
                }
                if (t == Beats.SPHERE_BOOM) {
                    Vec3 at = spherePos(c, t - 0.01F);
                    play(at, ModSounds.SPHERE_BOOM.get(), 2.0F, 1.0F);
                    play(at, ModSounds.BASS.get(), 1.5F, 0.8F);
                    float s = zenith ? 1.4F : 1.0F;
                    Light.flare(at, 12.0F * s, CYAN, 1.0F);
                    Light.flare(at, 5.0F * s, WHITE, 0.6F);
                    Light.ring(at.subtract(0, 0.8, 0), UP, 1.0F, 16.0F * s, 1.6F, CYAN, 1.1F);
                    Light.ring(at, c.fwd, 1.0F, 11.0F * s, 1.0F, SILVER, 0.9F);
                    Light.burst(at, 140, 24.0F, CYAN, 1.2F, 0.09F);
                    Light.burst(at, 60, 14.0F, WHITE, 0.8F, 0.06F);
                }
            }
            case FINAL -> {
                if (t == 2) {
                    play(user, ModSounds.FINAL_CHARGE.get(), 1.4F, 1.0F);
                }
                if (t == 22) {
                    play(user, ModSounds.HEARTBEAT.get(), 1.2F, 0.9F);
                }
                if (t == Beats.FINAL_FIRE) {
                    play(user, ModSounds.FINAL_FIRE.get(), 1.8F, 1.0F);
                    play(user, ModSounds.BASS.get(), 1.5F, 0.8F);
                    Light.ring(hands(c, 0), aim(c), 0.5F, 7.0F, 0.6F, WHITE, 0.6F);
                }
                if (t == Beats.FINAL_END) {
                    Vec3 end = reach(c, hands(c, 0), aim(c), Beats.FINAL_RANGE);
                    play(end, ModSounds.FINAL_BOOM.get(), 2.0F, 1.0F);
                    play(end, ModSounds.BASS.get(), 1.5F, 0.75F);
                    Light.flare(end, 10.0F, CYAN, 0.9F);
                    Light.burst(end, 120, 20.0F, CYAN, 1.0F, 0.08F);
                    Light.ring(end, UP, 1.0F, 14.0F, 1.2F, SILVER, 0.9F);
                }
            }
            case AWAKEN_OMEN -> {
                if (t == 4 || t == 22 || t == 40 || t == 52 || t == 58 || t == 62) {
                    play(user, ModSounds.HEARTBEAT.get(), 1.2F, 1.0F);
                }
                if (t == Beats.OMEN_SURGE) {
                    play(user, ModSounds.AWAKEN_OMEN.get(), 1.6F, 1.0F);
                    play(user, ModSounds.BASS.get(), 1.3F, 0.9F);
                    Light.flare(user, 5.0F, BLUE, 0.6F);
                    Light.ring(c.feet.add(0, 0.05, 0), UP, 0.5F, 9.0F, 0.8F, BLUE, 0.8F);
                    Light.burst(user, 60, 10.0F, SILVER, 0.8F, 0.05F);
                }
            }
            case AWAKEN_ZENITH -> {
                if (t == 6 || t == 30 || t == 60 || t == 78 || t == 86 || t == 91 || t == 94) {
                    play(user, ModSounds.HEARTBEAT.get(), 1.3F, 1.0F);
                }
                if (t == 56) {
                    play(user, ModSounds.AURA_HUM.get(), 1.0F, 0.8F);
                }
                if (t == Beats.ZENITH_SURGE) {
                    play(user, ModSounds.AWAKEN_ZENITH.get(), 1.8F, 1.0F);
                    play(user, ModSounds.BASS.get(), 1.5F, 0.8F);
                    play(user, ModSounds.SLAM.get(), 1.0F, 1.3F);
                    Light.flare(user, 9.0F, SILVER, 0.8F);
                    Light.flare(user, 4.0F, WHITE, 0.4F);
                    Light.ring(c.feet.add(0, 0.05, 0), UP, 0.5F, 14.0F, 1.2F, SILVER, 1.0F);
                    Light.ring(c.feet.add(0, 0.6, 0), UP, 0.5F, 9.0F, 0.6F, VIOLET, 0.8F);
                    Light.burst(user, 120, 16.0F, SILVER, 1.0F, 0.06F);
                }
            }
            default -> {
            }
        }
    }

    /** Every client tick: the aura hums under an awakened fighter. */
    public static void ambientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (Player p : mc.level.players()) {
            int form = ClientState.form(p);
            if (form > 0 && (now + p.getId()) % 60 == 0) {
                play(p.position().add(0, 1, 0), ModSounds.AURA_HUM.get(), form == 2 ? 0.35F : 0.2F, form == 2 ? 1.0F : 1.15F);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ strokes of light

    public static void ribbons(ClientCast c, float t, float partial, List<Ribbon> out) {
        float time = (System.nanoTime() % 100_000_000_000L) / 1.0E9F;
        boolean zenith = c.caster() instanceof Player p && ClientState.form(p) == 2;
        Vec3 tp = c.targetCentre(partial);
        Random r = new Random(c.seed * 7L + (long) Math.floor(t * 2));
        switch (c.move) {
            case STRIKE, HEAVY -> {
                int at = c.move == Move.STRIKE ? Beats.STRIKE_HIT : Beats.HEAVY_HIT;
                float a = window(t, at - 1.5F, at, at + 1, at + 5);
                Vec3 from = c.casterFeet(partial).add(0, (c.seed & 3) == 2 || c.move == Move.HEAVY ? 0.7 : 1.3, 0).add(c.right.scale(0.4));
                Vec3 to = c.hasTarget() ? tp : from.add(c.dir.scale(2.8));
                Shapes.crescent(out, from.lerp(to, 0.5), c.right, UP, from.distanceTo(to) * 0.6, -70, 70, c.move == Move.HEAVY ? 0.5F : 0.25F,
                        Mth.clamp((t - at + 1.5F) / 1.5F, 0, 1), a, zenith ? CYAN : SILVER, Ribbon.Style.GLOW);
            }
            case VOLLEY -> {
                for (int i = 0; i < Beats.VOLLEY_SHOTS; i++) {
                    Vec3[] b = blast(c, i, t);
                    if (b != null) {
                        Shapes.streak(out, b[0].subtract(b[1].scale(1.8)), b[0], 0.55F, 0.9F, BLUE, Ribbon.Style.GLOW);
                        Shapes.streak(out, b[0].subtract(b[1].scale(1.2)), b[0], 0.2F, 1.0F, WHITE, Ribbon.Style.GLOW);
                    }
                }
            }
            case WAVE, FINAL -> {
                boolean fin = c.move == Move.FINAL;
                int fire = fin ? Beats.FINAL_FIRE : Beats.WAVE_FIRE;
                int end = fin ? Beats.FINAL_END : Beats.WAVE_END;
                Vec3 h = hands(c, partial);
                Vec3 d = aim(c);
                // the gathering between the palms
                float charge = window(t, 4, fire - 4, fire, fire + 2);
                if (charge > 0) {
                    float k = Mth.clamp((t - 4) / (fire - 8.0F), 0, 1);
                    for (int i = 0; i < 3; i++) {
                        Vec3 n = new Vec3(Math.sin(time * 3 + i * 2.1), Math.cos(time * 2.3 + i), Math.sin(time * 1.7 + i * 4)).normalize();
                        Shapes.ring(out, h, n, 0.2 + 0.35 * k * (fin ? 1.6 : 1.0), 0.05F, charge * 0.8F, i == 1 ? VIOLET : CYAN, Ribbon.Style.GLOW);
                    }
                }
                float on = window(t, fire - 0.5F, fire + 1, end - 4, end + 2);
                if (on > 0) {
                    Vec3 b = reach(c, h, d, fin ? Beats.FINAL_RANGE : Beats.WAVE_RANGE);
                    float scale = (fin ? 2.2F : zenith ? 1.4F : 1.0F) * (1.0F + 0.25F * hit(t, fire, 4));
                    beam(out, h, b, 4.8F * scale, 0.22F * on, fin ? VIOLET : BLUE, time);
                    beam(out, h, b, 2.4F * scale, 0.55F * on, CYAN, time + 3);
                    beam(out, h, b, 1.0F * scale, 1.0F * on, WHITE, time + 7);
                    for (int i = 0; i < 3; i++) {
                        spiral(out, h, b, 1.1 * scale, b.distanceTo(h) / 6.0, -time * 9 + i * 2.09, 0.14F * scale, 0.55F * on, i == 0 ? SILVER : CYAN);
                    }
                    // rings riding down the beam
                    double len = b.distanceTo(h);
                    for (int i = 0; i < 6; i++) {
                        double k = ((time * 1.8 + i / 6.0) % 1.0);
                        Shapes.ring(out, h.add(d.scale(len * k)), d, 1.4 * scale * (1 - 0.3 * k), 0.12F * scale, on * 0.5F * (float) (1 - k), SILVER, Ribbon.Style.GLOW);
                    }
                }
            }
            case FLURRY -> {
                if (t > Beats.FLURRY_DASH && t < Beats.FLURRY_FINAL + 2) {
                    Vec3 from = c.casterFeet(partial).add(0, 1.3, 0);
                    for (int i = 0; i < 8; i++) {
                        Vec3 s = from.add(c.right.scale((r.nextDouble() - 0.5) * 1.0)).add(0, (r.nextDouble() - 0.5) * 0.8, 0);
                        Vec3 e = tp.add((r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6);
                        Shapes.streak(out, s, e, 0.18F, 0.7F, i % 3 == 0 ? CYAN : SILVER, Ribbon.Style.GLOW);
                    }
                    // the serpent: a long coil of light winding round the target
                    spiral(out, tp.subtract(0, 1.2, 0), tp.add(0, 1.6, 0), 1.3, 3.0, time * 6, 0.22F, 0.7F, SILVER);
                    spiral(out, tp.subtract(0, 1.2, 0), tp.add(0, 1.6, 0), 1.0, 3.0, time * 6 + 3.14, 0.14F, 0.5F, CYAN);
                }
            }
            case METEOR -> {
                if (t >= Beats.METEOR_SPIKE - 1 && t < Beats.METEOR_SLAM + 4) {
                    float a = window(t, Beats.METEOR_SPIKE - 1, Beats.METEOR_SPIKE, Beats.METEOR_SLAM, Beats.METEOR_SLAM + 4);
                    Shapes.streak(out, tp.add(0, 6, 0), tp, 0.8F, a, GOLD, Ribbon.Style.GLOW);
                    Shapes.streak(out, tp.add(0, 5, 0), tp, 0.3F, a, WHITE, Ribbon.Style.GLOW);
                }
            }
            case RUSH -> {
                for (int i = 1; i < Beats.RUSH_DASHES.length; i++) {
                    float at = Beats.RUSH_DASHES[i];
                    if (t < at - 0.5F) {
                        break;
                    }
                    Vec3 a = Beats.rushDash(c.exit, c.seed, i - 1).add(0, 1.0, 0);
                    Vec3 b = Beats.rushDash(c.exit, c.seed, i).add(0, 1.0, 0);
                    float hang = window(t, at - 0.5F, at, Beats.RUSH_FINAL, Beats.RUSH_FINAL + 6);
                    float flash = hit(t, at, 3);
                    Shapes.streak(out, a, b, 0.2F + 0.5F * flash, hang * (0.5F + 0.5F * flash), i % 2 == 0 ? CYAN : VIOLET, Ribbon.Style.GLOW);
                    Shapes.streak(out, a, b, 0.06F, hang, WHITE, Ribbon.Style.GLOW);
                }
            }
            case SPHERE -> {
                float rad = sphereRadius(c, t, zenith);
                if (rad > 0.05F) {
                    Vec3 s = spherePos(c, t);
                    // a sphere of light: rings at every angle, turning
                    for (int i = 0; i < 12; i++) {
                        double a1 = i * 0.52 + time * (0.6 + i * 0.07), a2 = i * 1.3 + time * 0.4;
                        Vec3 n = new Vec3(Math.cos(a1) * Math.sin(a2), Math.cos(a2), Math.sin(a1) * Math.sin(a2));
                        Shapes.ring(out, s, n, rad * (0.92 + 0.08 * Math.sin(time * 5 + i)), 0.14F * rad, 0.55F, i % 3 == 0 ? WHITE : i % 3 == 1 ? CYAN : BLUE,
                                Ribbon.Style.GLOW);
                    }
                    // arms raised: a column of light from his hands to it
                    if (t < Beats.SPHERE_THROW) {
                        Vec3 hand = c.casterFeet(partial).add(0, 2.1, 0);
                        Shapes.streak(out, hand, s.subtract(0, rad, 0), 0.35F, 0.6F * window(t, 6, 20, Beats.SPHERE_THROW - 2, Beats.SPHERE_THROW), CYAN,
                                Ribbon.Style.GLOW);
                    }
                }
            }
            case AWAKEN_ZENITH -> {
                // light rising out of the ground round him before the summit
                float a = window(t, 56, 70, Beats.ZENITH_SURGE, Beats.ZENITH_SURGE + 2);
                for (int i = 0; i < 14; i++) {
                    double ang = i / 14.0 * Math.PI * 2 + t * 0.03;
                    double rr = 2.0 + (i % 4) * 1.4;
                    Vec3 base = c.feet.add(Math.cos(ang) * rr, 0.05, Math.sin(ang) * rr);
                    Shapes.streak(out, base, base.add(0, 3 + (i % 3) * 2.5, 0), 0.18F, a * 0.6F, i % 2 == 0 ? SILVER : VIOLET, Ribbon.Style.GLOW);
                }
            }
            default -> {
            }
        }
    }

    /** The aura round every awakened fighter: flame tongues of silver and blue, violet at the tips, lightning in Zenith. */
    public static void ambientRibbons(List<Ribbon> out, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float time = (System.nanoTime() % 100_000_000_000L) / 1.0E9F;
        int tongues = ZConfig.CLIENT.auraDetail.get();
        for (Player p : mc.level.players()) {
            float power = auraPower(p, partial);
            if (power < 0.01F) {
                continue;
            }
            boolean zenith = power > 0.7F;
            Vec3 feet = p.getPosition(partial);
            float hgt = 2.2F + 0.8F * power;
            for (int i = 0; i < tongues; i++) {
                float phase = i * 1.7F + p.getId();
                float flick = zenith ? 0.75F + 0.25F * (float) Math.sin(time * 9 + phase) : Mth.clamp(0.4F + 0.9F * (float) Math.sin(time * 6 + phase * 2.3F), 0, 1);
                if (flick < 0.05F) {
                    continue;
                }
                float h = hgt * (0.7F + 0.3F * (float) Math.sin(time * 4 + phase));
                int n = 9;
                Vec3[] spine = new Vec3[n];
                float[] w = new float[n];
                float[] al = new float[n];
                double base = i * Math.PI * 2 / tongues + time * 0.5;
                for (int k = 0; k < n; k++) {
                    float u = k / (n - 1.0F);
                    double ang = base + 0.5 * u * Math.sin(time * 2.5 + phase);
                    double rr = (0.62 - 0.45 * u) + 0.07 * Math.sin(time * 11 + phase + u * 6);
                    spine[k] = feet.add(Math.cos(ang) * rr, u * h, Math.sin(ang) * rr);
                    w[k] = (0.42F * (1 - u) + 0.05F) * (0.7F + 0.5F * power);
                    al[k] = power * flick * 0.45F * (float) Math.sin(Math.PI * Math.pow(u, 0.65));
                }
                out.add(new Ribbon(spine, null, w, al, BLUE, Ribbon.Style.GLOW));
                float[] w2 = w.clone();
                float[] al2 = al.clone();
                for (int k = 0; k < n; k++) {
                    w2[k] *= 0.4F;
                    al2[k] *= 1.3F;
                }
                out.add(new Ribbon(spine, null, w2, al2, SILVER, Ribbon.Style.GLOW));
                if (zenith && i % 3 == 0) {
                    Vec3 tip = spine[n - 1];
                    Shapes.streak(out, spine[n - 3], tip.add(0, 0.5, 0), 0.16F, al[n - 3] * 1.5F, VIOLET, Ribbon.Style.GLOW);
                }
            }
            Shapes.ring(out, feet.add(0, 0.06, 0), UP, 1.0 + 0.08 * Math.sin(time * 6), 0.14F, 0.35F * power, BLUE, Ribbon.Style.GLOW);
            if (zenith) {
                long frame = (long) (time * 14);
                Random r = new Random(frame * 977L + p.getId());
                for (int b = 0; b < 2; b++) {
                    if (r.nextFloat() < 0.6F) {
                        Vec3 a = feet.add((r.nextDouble() - 0.5) * 1.2, 0.3 + r.nextDouble() * 1.6, (r.nextDouble() - 0.5) * 1.2);
                        Vec3 e = a.add((r.nextDouble() - 0.5) * 1.4, (r.nextDouble() - 0.3) * 1.2, (r.nextDouble() - 0.5) * 1.4);
                        Shapes.bolt(out, a, e, frame * 31 + b, 0.35, 0.05F, 0.85F, b == 0 ? 0xCFE4FF : VIOLET, Ribbon.Style.GLOW);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ per frame light

    /** Motes off the aura, trails off fast fists and bodies, the glow of blasts, beams and the sphere, energy pouring in. */
    public static void frame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        Random r = new Random();
        for (Player p : mc.level.players()) {
            float power = auraPower(p, partial);
            Vec3 feet = p.getPosition(partial);
            if (power > 0.01F && r.nextFloat() < power * 0.9F) {
                Vec3 at = feet.add((r.nextDouble() - 0.5) * 1.4, r.nextDouble() * 1.6, (r.nextDouble() - 0.5) * 1.4);
                Light.mote(at, new Vec3(0, 1.5 + r.nextDouble() * 2.5, 0), 0.7F, r.nextBoolean() ? SILVER : BLUE, 0.05F);
            }
            Vec3 move = p.getDeltaMovement();
            if (power > 0.7F && move.horizontalDistanceSqr() > 0.02) {
                Light.trail("b" + p.getId(), feet.add(0, 1.0, 0), CYAN, 0.9F, 0.25F);
            }
            ClientCast c = Casts.of(p);
            if (c == null) {
                continue;
            }
            float t = c.time(partial);
            Vec3 look = p.getViewVector(partial);
            Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
            boolean striking = switch (c.move) {
                case STRIKE, HEAVY -> t < 7;
                case FLURRY -> t > Beats.FLURRY_DASH && t < Beats.FLURRY_FINAL + 2;
                case METEOR -> t < Beats.METEOR_SLAM + 2;
                case RUSH -> t > Beats.RUSH_DASHES[0] - 1 && t < Beats.RUSH_FINAL + 2;
                case VANISH -> t > Beats.VANISH_AT - 1 && t < Beats.VANISH_AT + 3;
                default -> false;
            };
            if (striking) {
                Vec3 chest = feet.add(0, 1.2, 0);
                Light.trail("h" + p.getId(), chest.add(look.scale(0.7)).add(right.scale(0.35 * Math.sin(t * 2.4))), SILVER, 0.35F, 0.18F);
                Light.trail("c" + p.getId(), feet.add(0, 1.0, 0), CYAN, 1.0F, 0.3F);
            }
            switch (c.move) {
                case VOLLEY -> {
                    for (int i = 0; i < Beats.VOLLEY_SHOTS; i++) {
                        Vec3[] b = blast(c, i, t);
                        if (b != null) {
                            Light.flare(b[0], 0.9F, BLUE, 0.05F);
                            Light.flare(b[0], 0.35F, WHITE, 0.05F);
                        }
                    }
                }
                case WAVE, FINAL -> {
                    boolean fin = c.move == Move.FINAL;
                    int fire = fin ? Beats.FINAL_FIRE : Beats.WAVE_FIRE;
                    int end = fin ? Beats.FINAL_END : Beats.WAVE_END;
                    Vec3 h = hands(c, partial);
                    if (t > 4 && t < fire) {
                        float k = Mth.clamp((t - 4) / (fire - 8.0F), 0, 1);
                        Light.flare(h, (0.6F + 1.6F * k) * (fin ? 1.6F : 1.0F), CYAN, 0.05F);
                        Light.flare(h, 0.4F + 0.6F * k, WHITE, 0.05F);
                        if (r.nextFloat() < 0.8F) {
                            Vec3 from = h.add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(fin ? 6 + r.nextDouble() * 6 : 3));
                            Light.stream(from, h, 0.35F, r.nextBoolean() ? CYAN : SILVER, 0.05F);
                        }
                    }
                    if (t >= fire && t <= end) {
                        Vec3 b = reach(c, h, aim(c), fin ? Beats.FINAL_RANGE : Beats.WAVE_RANGE);
                        float s = fin ? 2.0F : 1.0F;
                        Light.flare(h, 2.8F * s, CYAN, 0.05F);
                        Light.flare(b, 4.0F * s, CYAN, 0.05F);
                        Light.flare(b, 1.8F * s, WHITE, 0.05F);
                        if (r.nextFloat() < 0.7F) {
                            Light.spray(b, aim(c).scale(-1), 0.9F, 4, 10.0F * s, r.nextBoolean() ? CYAN : WHITE, 0.4F, 0.05F);
                        }
                    }
                }
                case SPHERE -> {
                    boolean zenith = ClientState.form(p) == 2;
                    float rad = sphereRadius(c, t, zenith);
                    if (rad > 0.05F) {
                        Vec3 s = spherePos(c, t);
                        Light.flare(s, rad * 1.5F, BLUE, 0.05F);
                        Light.flare(s, rad * 0.75F, SILVER, 0.05F);
                        if (t < Beats.SPHERE_THROW) {
                            if (r.nextFloat() < 0.6F) {
                                // light drawn in from the land around, never from straight overhead
                                Vec3 d = new Vec3(r.nextGaussian(), -0.2 - r.nextDouble() * 0.8, r.nextGaussian()).normalize();
                                Light.stream(s.add(d.scale(10 + r.nextDouble() * 6)), s, 0.6F, r.nextInt(3) == 0 ? WHITE : CYAN, 0.045F);
                            }
                        }
                    }
                }
                default -> {
                }
            }
            if (c.move == Move.FINAL && t > 20 && t < Beats.FINAL_FIRE && r.nextFloat() < 0.6F) {
                // the storm: everything around him being pulled toward his hands
                Vec3 h = hands(c, partial);
                Vec3 from = h.add(new Vec3(r.nextGaussian(), r.nextGaussian() * 0.5, r.nextGaussian()).normalize().scale(10 + r.nextDouble() * 10));
                Light.stream(from, h, 0.5F, r.nextBoolean() ? VIOLET : SILVER, 0.06F);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the picture

    public static void screen(ClientCast c, float t, ScreenFx fx) {
        Vec3 tp = c.targetCentre(Minecraft.getInstance().getFrameTime());
        switch (c.move) {
            case STRIKE -> {
                if (c.hasTarget()) {
                    fx.shake(0.25F * hit(t, Beats.STRIKE_HIT, 3));
                    fx.aberration(0.5F * hit(t, Beats.STRIKE_HIT, 3));
                    if ((c.seed & 3) == 2) {
                        fx.impact(KICK, t - Beats.STRIKE_HIT, tp, CYAN);
                        fx.punch(0.4F * hit(t, Beats.STRIKE_HIT, 3));
                    }
                }
            }
            case HEAVY -> {
                fx.punch(0.9F * hit(t, Beats.HEAVY_HIT, 3));
                fx.shake(0.7F * hit(t, Beats.HEAVY_HIT, 5));
                fx.aberration(1.0F * hit(t, Beats.HEAVY_HIT, 5));
                if (c.hasTarget()) {
                    fx.impact(HEAVY, t - Beats.HEAVY_HIT, tp, BLUE);
                }
            }
            case VOLLEY -> {
                fx.shake(0.15F * window(t, Beats.VOLLEY_FIRST, Beats.VOLLEY_FIRST + 2, 32, 36));
                fx.bloom(0.25F * window(t, Beats.VOLLEY_FIRST, Beats.VOLLEY_FIRST + 2, 34, 40));
            }
            case VANISH -> {
                fx.aberration(1.2F * hit(t, Beats.VANISH_AT, 4));
                fx.punch(0.5F * hit(t, Beats.VANISH_AT, 3));
            }
            case WAVE, FINAL -> {
                boolean fin = c.move == Move.FINAL;
                int fire = fin ? Beats.FINAL_FIRE : Beats.WAVE_FIRE;
                int end = fin ? Beats.FINAL_END : Beats.WAVE_END;
                float charge = window(t, 4, fire - 6, fire, fire + 1);
                fx.vignette(fin ? 0x0A0820 : 0x061830, (fin ? 0.65F : 0.4F) * charge);
                fx.desaturate((fin ? 0.5F : 0.2F) * charge);
                fx.shake((fin ? 0.35F : 0.12F) * charge * Mth.clamp((t - 4) / (fire - 4.0F), 0, 1));
                fx.bloom(0.3F * charge);
                fx.impact(fin ? SUMMIT : BEAM, t - fire, hands(c, 0), CYAN);
                fx.flash(0xE6F4FF, (fin ? 1.3F : 0.8F) * hit(t, fire, 4));
                fx.punch((fin ? 1.2F : 0.8F) * hit(t, fire, 5));
                float on = window(t, fire, fire + 2, end - 4, end);
                fx.shake((fin ? 0.65F : 0.35F) * on);
                fx.aberration((fin ? 0.7F : 0.4F) * on);
                fx.bloom((fin ? 0.9F : 0.6F) * on);
                if (fin) {
                    fx.impact(BOOM, t - end, tp, CYAN);
                    fx.flash(0xFFFFFF, 1.2F * hit(t, end, 6));
                }
            }
            case FLURRY -> {
                float on = window(t, Beats.FLURRY_FIRST, Beats.FLURRY_FIRST + 2, Beats.FLURRY_LAST, Beats.FLURRY_FINAL);
                fx.shake(0.3F * on);
                fx.aberration(0.5F * on);
                fx.bloom(0.3F * on);
                fx.punch(0.6F * hit(t, Beats.FLURRY_DASH, 3));
                if (((int) t - Beats.FLURRY_FIRST) % 6 == 0 && on > 0.5F) {
                    fx.impact(((int) t / 6) % 2 == 0 ? LIGHT : new int[][]{{Impact.INVERT, 1}}, t - (int) t, tp, CYAN);
                }
                fx.impact(HEAVY, t - Beats.FLURRY_FINAL, tp, CYAN);
                fx.shake(0.9F * hit(t, Beats.FLURRY_FINAL, 6));
            }
            case METEOR -> {
                fx.impact(KICK, t - Beats.METEOR_LAUNCH, tp, GOLD);
                fx.shake(0.5F * hit(t, Beats.METEOR_LAUNCH, 4));
                for (int h : Beats.METEOR_HITS) {
                    fx.shake(0.3F * hit(t, h, 3));
                    fx.aberration(0.5F * hit(t, h, 3));
                }
                fx.impact(HEAVY, t - Beats.METEOR_SPIKE, tp, GOLD);
                fx.impact(BOOM, t - Beats.METEOR_SLAM, tp, GOLD);
                fx.flash(0xFFF2D8, 0.9F * hit(t, Beats.METEOR_SLAM, 4));
                fx.shake(1.1F * hit(t, Beats.METEOR_SLAM, 10));
            }
            case RUSH -> {
                float still = window(t, Beats.RUSH_DASHES[0] - 1, Beats.RUSH_DASHES[0], Beats.RUSH_FINAL - 1, Beats.RUSH_FINAL);
                fx.desaturate(0.45F * still);
                fx.vignette(0x000814, 0.4F * still);
                fx.bloom(0.4F * still);
                for (int i = 0; i < Beats.RUSH_DASHES.length; i++) {
                    int h = Beats.RUSH_DASHES[i];
                    fx.shake(0.35F * hit(t, h, 3));
                    fx.aberration(0.8F * hit(t, h, 3));
                    fx.impact(i % 2 == 0 ? LIGHT : new int[][]{{Impact.SLASHED, 1}}, t - h, tp, CYAN);
                }
                fx.impact(SUMMIT, t - Beats.RUSH_FINAL, tp, CYAN);
                fx.flash(0xE6F4FF, 1.0F * hit(t, Beats.RUSH_FINAL, 4));
                fx.shake(1.0F * hit(t, Beats.RUSH_FINAL, 10));
            }
            case SPHERE -> {
                float gather = window(t, 6, 30, Beats.SPHERE_THROW, Beats.SPHERE_THROW + 2);
                // the world dims as its light is borrowed
                fx.vignette(0x020A1C, 0.75F * gather);
                fx.desaturate(0.45F * gather);
                fx.contrast(0.25F * gather);
                fx.bloom(0.12F * gather);
                fx.shake(0.15F * gather);
                fx.punch(0.8F * hit(t, Beats.SPHERE_THROW, 4));
                fx.impact(BOOM, t - Beats.SPHERE_BOOM, spherePos(c, Beats.SPHERE_BOOM - 0.01F), CYAN);
                fx.flash(0xFFFFFF, 1.0F * hit(t, Beats.SPHERE_BOOM, 5));
                fx.shake(1.2F * hit(t, Beats.SPHERE_BOOM, 14));
                fx.bloom(0.8F * hit(t, Beats.SPHERE_BOOM, 20));
            }
            case AWAKEN_OMEN -> {
                float hush = window(t, 0, 8, Beats.OMEN_SURGE, Beats.OMEN_SURGE + 2);
                fx.desaturate(0.7F * hush);
                fx.vignette(0x000000, 0.55F * hush);
                fx.grain(0.4F * hush);
                for (int b : new int[]{4, 22, 40, 52, 58, 62}) {
                    fx.punch(0.35F * hit(t, b, 3));
                }
                fx.impact(SURGE, t - Beats.OMEN_SURGE, c.casterFeet(0).add(0, 1.4, 0), BLUE);
                fx.flash(0xD8ECFF, 1.0F * hit(t, Beats.OMEN_SURGE, 5));
                fx.shake(0.8F * hit(t, Beats.OMEN_SURGE, 8));
                fx.bloom(0.5F * window(t, Beats.OMEN_SURGE, Beats.OMEN_SURGE + 2, 100, 110));
            }
            case AWAKEN_ZENITH -> {
                float hush = window(t, 0, 8, Beats.ZENITH_SURGE, Beats.ZENITH_SURGE + 2);
                fx.desaturate(0.85F * hush);
                fx.vignette(0x000000, 0.65F * hush);
                fx.grain(0.5F * hush);
                fx.flash(0xFFFFFF, 1.2F * Mth.clamp((t - 88) / 8.0F, 0, 1) * (t < Beats.ZENITH_SURGE ? 1 : 0));
                for (int b : new int[]{6, 30, 60, 78, 86, 91, 94}) {
                    fx.punch(0.35F * hit(t, b, 3));
                }
                fx.impact(SUMMIT, t - Beats.ZENITH_SURGE, c.casterFeet(0).add(0, 1.4, 0), SILVER);
                fx.flash(0xFFFFFF, 1.6F * hit(t, Beats.ZENITH_SURGE, 6));
                fx.shake(1.2F * hit(t, Beats.ZENITH_SURGE, 12));
                fx.bloom(0.7F * window(t, Beats.ZENITH_SURGE, Beats.ZENITH_SURGE + 2, 136, 150));
            }
            default -> {
            }
        }
    }

    /** Standing looks: the cool edge of Zenith, and the flash of a slipped blow or a counter. */
    public static void ambientScreen(ScreenFx fx, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (ClientState.form(mc.player) == 2) {
            fx.vignette(0x0A1830, 0.14F);
            fx.bloom(0.1F);
        }
        float t = mc.level.getGameTime() + partial;
        PULSES.removeIf(pu -> t - pu.start > 30);
        for (Pulse pu : PULSES) {
            fx.impact(pu.seq, t - pu.start, pu.at, pu.rgb);
            fx.aberration(pu.power * hit(t, pu.start, 4));
            fx.punch(0.4F * pu.power * hit(t, pu.start, 3));
            fx.shake(0.4F * pu.power * hit(t, pu.start, 5));
        }
    }

    // ------------------------------------------------------------------------------------------------ outside the casts

    private record Pulse(float start, int[][] seq, Vec3 at, int rgb, float power) {}

    private static final List<Pulse> PULSES = new ArrayList<>();

    public static void serverFx(S2CFx m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float now = mc.level.getGameTime() + mc.getFrameTime();
        if (m.kind == S2CFx.DODGE && m.points.size() == 2) {
            Vec3 from = m.points.get(0), to = m.points.get(1);
            Afterimages.ghost(m.entityId, from, 0.45F);
            play(from.add(0, 1, 0), ModSounds.DODGE.get(), 1.0F, 0.9F + (float) Math.random() * 0.3F);
            Light.flare(from.add(0, 1.0, 0), 1.4F, SILVER, 0.2F);
            Light.spray(from.add(0, 1.0, 0), to.subtract(from), 0.5F, 10, 6.0F, SILVER, 0.3F, 0.04F);
            if (m.entityId == mc.player.getId()) {
                PULSES.add(new Pulse(now, new int[0][], from, SILVER, 0.6F));
            }
        }
        if (m.kind == S2CFx.COUNTER && !m.points.isEmpty()) {
            Vec3 at = m.points.get(0);
            play(at, ModSounds.COUNTER.get(), 1.2F, 1.0F);
            play(at, ModSounds.BASS.get(), 0.8F, 1.2F);
            Light.hit(at, UP, CYAN, 1.5F);
            Entity user = mc.level.getEntity(m.entityId);
            if (user != null && user.distanceTo(mc.player) < 24) {
                PULSES.add(new Pulse(now, HEAVY, at, CYAN, 1.0F));
            }
        }
    }

    /** The awakening let go: the light folds away. */
    public static void revert(Entity e) {
        Vec3 at = e.position().add(0, 1.0, 0);
        play(at, ModSounds.REVERT.get(), 1.0F, 1.0F);
        Light.flare(at, 2.4F, BLUE, 0.4F);
        Light.burst(at, 30, 4.0F, SILVER, 0.5F, 0.04F);
    }
}
