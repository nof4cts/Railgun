package com.firestar.mih.client;

import static com.firestar.mih.client.fx.ScreenFx.hit;
import static com.firestar.mih.client.fx.ScreenFx.window;

import com.firestar.mih.client.fx.Impact;
import com.firestar.mih.client.fx.Ribbon;
import com.firestar.mih.client.fx.ScreenFx;
import com.firestar.mih.client.fx.Shapes;
import com.firestar.mih.client.render.Galaxy;
import com.firestar.mih.common.Beats;
import com.firestar.mih.common.Move;
import com.firestar.mih.registry.ModSounds;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The client half of every move, on the beats in {@link Beats}: the sounds and particles per tick, the strokes of
 * light drawn into the world, and what each frame does to the picture (shake, flash, punch-in, aberration, grade and
 * the anime impact frames).
 */
public final class Choreo {
    static final int MINT = 0x7CFFD4, GOLD = 0xFFD86B, WHITE = 0xFFFFFF, SKY = 0x9FD8FF, BLOOD = 0xFF2A2A;

    // impact sheets of its own
    static final int[][] JAB = {{Impact.INVERT, 1}};
    static final int[][] SLICE = {{Impact.WHITEOUT, 1}, {Impact.SLASHED, 2}, {Impact.CRIMSON, 1}, {Impact.INVERT, 1}, {Impact.SLASHED, 1},
            {Impact.CRIMSON, 2}, {Impact.INK, 1}};
    static final int[][] CHOP = {{Impact.WHITEOUT, 1}, {Impact.INK, 1}, {Impact.LINES, 2}, {Impact.INVERT, 1}, {Impact.HALFTONE, 1}};
    static final int[][] ACCEL = {{Impact.WHITEOUT, 1}, {Impact.HALFTONE, 2}, {Impact.INVERT, 1}, {Impact.LINES, 1}, {Impact.HALFTONE, 1}};
    static final int[][] JUDGE_FINAL = {{Impact.WHITEOUT, 1}, {Impact.INK, 1}, {Impact.INVERT, 1}, {Impact.HALFTONE, 1}, {Impact.LINES, 2},
            {Impact.NEGATIVE, 1}, {Impact.INVERT, 1}, {Impact.SLASHED, 1}, {Impact.WHITEOUT, 1}, {Impact.INK, 1}, {Impact.HALFTONE, 2}};
    static final int[][] RESET_CRACK = {{Impact.INK, 1}, {Impact.INVERT, 1}, {Impact.NEGATIVE, 1}, {Impact.HALFTONE, 1}, {Impact.LINES, 1},
            {Impact.INVERT, 1}, {Impact.WHITEOUT, 2}};
    static final int[][] SINGULARITY = {{Impact.WHITEOUT, 1}, {Impact.INVERT, 1}, {Impact.INK, 1}, {Impact.NEGATIVE, 1}, {Impact.HALFTONE, 1},
            {Impact.INVERT, 1}, {Impact.LINES, 2}, {Impact.WHITEOUT, 3}};

    private Choreo() {}

    // ------------------------------------------------------------------------------------------------ sounds

    private static void play(Vec3 at, SoundEvent s, float vol, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            level.playLocalSound(at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch, false);
        }
    }

    private static void particles(ParticleOptions type, Vec3 at, int n, double spread, double speed, Random r) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (int i = 0; i < n; i++) {
            level.addParticle(type, at.x + (r.nextDouble() - 0.5) * spread, at.y + (r.nextDouble() - 0.5) * spread, at.z + (r.nextDouble() - 0.5) * spread,
                    (r.nextDouble() - 0.5) * speed, (r.nextDouble() - 0.5) * speed, (r.nextDouble() - 0.5) * speed);
        }
    }

    // ------------------------------------------------------------------------------------------------ per tick

    public static void begin(ClientCast c) {
        if (c.move == Move.UNIVERSE_RESET) {
            Galaxy.capture(c);
        }
    }

    public static void tick(ClientCast c, int t) {
        Random r = new Random(c.seed * 31L + t);
        Vec3 user = c.casterFeet(0).add(0, 1.0, 0);
        switch (c.move) {
            case SUMMON -> {
                if (t == 1) {
                    particles(ParticleTypes.END_ROD, user, 24, 1.4, 0.12, r);
                }
            }
            case PUNCH -> {
                if (t == 1) {
                    play(user, SoundEvents.PLAYER_ATTACK_SWEEP, 0.35F, 1.6F + r.nextFloat() * 0.3F);
                }
            }
            case BARRAGE -> {
                if (t >= Beats.BARRAGE_START && t <= Beats.BARRAGE_END) {
                    if (t % 3 == 0) {
                        play(user, SoundEvents.PLAYER_ATTACK_WEAK, 0.25F, 1.4F + r.nextFloat() * 0.5F);
                    }
                    if (c.hasTarget() && t % 2 == 0) {
                        particles(ParticleTypes.CRIT, c.targetCentre(0), 3, 0.8, 0.5, r);
                    }
                }
            }
            case FINISHER -> {
                if (t == Beats.FINISH_HIT - 3) {
                    play(user, ModSounds.TOCK.get(), 0.7F, 0.8F);
                }
                if (t == Beats.FINISH_HIT && c.hasTarget()) {
                    particles(ParticleTypes.EXPLOSION, c.targetCentre(0), 1, 0.1, 0.0, r);
                    particles(ParticleTypes.END_ROD, c.targetCentre(0), 14, 0.6, 0.5, r);
                }
            }
            case SPEED_SLICE -> {
                if (t == 8 || t == 12 || t == 15) {
                    play(user, ModSounds.TICK.get(), 1.0F, 0.9F + t * 0.02F);
                }
                if (t == Beats.SLICE_VANISH) {
                    particles(ParticleTypes.CLOUD, user, 10, 0.8, 0.08, r);
                }
            }
            case INFINITE_SPEED -> {
                if (t == 4 || t == 8 || t == 11) {
                    play(user, ModSounds.TICK.get(), 1.0F, 1.0F + t * 0.04F);
                }
                if (t == Beats.INF_VANISH) {
                    particles(ParticleTypes.CLOUD, user, 10, 0.8, 0.08, r);
                }
                if (t == Beats.INF_HIT && c.hasTarget()) {
                    particles(ParticleTypes.SWEEP_ATTACK, c.targetCentre(0), 3, 0.6, 0.0, r);
                }
            }
            case JUDGEMENT -> {
                if (t > 2 && t < Beats.JUDGE_LUNGE && t % 3 == 0) {
                    play(user, ModSounds.TICK.get(), 0.9F, 0.8F + t * 0.05F);
                }
                for (int h : Beats.JUDGE_HITS) {
                    if (t == h) {
                        particles(ParticleTypes.CRIT, c.targetCentre(0), 10, 1.0, 0.8, r);
                        particles(ParticleTypes.END_ROD, c.targetCentre(0), 4, 0.8, 0.3, r);
                    }
                }
                if (t == Beats.JUDGE_FINAL) {
                    particles(ParticleTypes.END_ROD, c.targetCentre(0), 40, 1.0, 1.0, r);
                    particles(ParticleTypes.FLASH, c.targetCentre(0), 1, 0.0, 0.0, r);
                }
            }
            case DOUBLE_ACCEL -> {
                if (t < Beats.DA_BURST && t % Math.max(1, 6 - t / 5) == 0) {
                    play(user, ModSounds.TICK.get(), 0.6F, 1.0F + t * 0.03F);
                }
                if (t == Beats.DA_BURST) {
                    particles(ParticleTypes.END_ROD, user, 50, 2.0, 0.6, r);
                    particles(ParticleTypes.FLASH, user, 1, 0.0, 0.0, r);
                }
            }
            case TIME_ACCEL -> {
                if (t < Beats.TA_START && t % Math.max(1, 10 - t / 5) == 0) {
                    play(user, t % 2 == 0 ? ModSounds.TICK.get() : ModSounds.TOCK.get(), 0.9F, 0.8F + t * 0.01F);
                }
                if (t == Beats.TA_START) {
                    particles(ParticleTypes.END_ROD, user.add(0, 1, 0), 80, 3.0, 0.8, r);
                    particles(ParticleTypes.FLASH, user, 1, 0.0, 0.0, r);
                }
            }
            case UNIVERSE_RESET -> {
                if (t < Beats.RESET_WHITE && t % 2 == 0) {
                    play(user, ModSounds.TICK.get(), 0.7F, 1.4F + t * 0.02F);
                }
                if (t == Beats.RESET_WHITE) {
                    play(user, ModSounds.MENACING.get(), 1.2F, 0.7F);
                }
                if (t == Beats.RESET_GALAXY) {
                    play(user, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.5F);
                }
                if (t == Beats.RESET_PLUNGE) {
                    play(user, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.5F);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ strokes of light

    public static void ribbons(ClientCast c, float t, float partial, List<Ribbon> out) {
        Minecraft mc = Minecraft.getInstance();
        Entity caster = c.caster();
        Vec3 feet = c.casterFeet(partial);
        Vec3 chest = feet.add(0, 1.2, 0);
        Vec3 look = caster != null ? caster.getViewVector(partial) : c.dir;
        Vec3 flat = new Vec3(look.x, 0, look.z).lengthSqr() < 1e-4 ? c.fwd : new Vec3(look.x, 0, look.z).normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        Random r = new Random(c.seed * 7L + (long) Math.floor(t));
        switch (c.move) {
            case PUNCH -> {
                float a = window(t, 1, 2.5F, 4, 7);
                Vec3 from = chest.add(flat.scale(0.8)).add(right.scale(((c.seed & 1) == 0 ? 0.35 : -0.35)));
                Shapes.streak(out, from, from.add(look.scale(2.6)), 0.22F, a * 0.7F, WHITE, Ribbon.Style.GLOW);
            }
            case BARRAGE -> {
                float a = window(t, Beats.BARRAGE_START - 1, Beats.BARRAGE_START + 1, Beats.BARRAGE_END, Beats.BARRAGE_END + 4);
                if (a > 0) {
                    for (int i = 0; i < 9; i++) {
                        Vec3 s = chest.add(flat.scale(0.9 + r.nextDouble() * 0.4)).add(right.scale((r.nextDouble() - 0.5) * 1.6))
                                .add(0, (r.nextDouble() - 0.4) * 1.2, 0);
                        Shapes.streak(out, s, s.add(look.scale(1.6 + r.nextDouble() * 1.4)), 0.12F + r.nextFloat() * 0.12F, a * 0.65F,
                                i % 3 == 0 ? MINT : WHITE, Ribbon.Style.GLOW);
                    }
                }
            }
            case FINISHER -> {
                float a = window(t, Beats.FINISH_HIT - 1, Beats.FINISH_HIT, Beats.FINISH_HIT + 2, Beats.FINISH_HIT + 8);
                Vec3 from = chest.add(flat.scale(0.6));
                Shapes.streak(out, from, from.add(look.scale(4.2)), 0.5F, a * 0.9F, WHITE, Ribbon.Style.GLOW);
                if (c.hasTarget()) {
                    float k = Mth.clamp((t - Beats.FINISH_HIT) / 8.0F, 0, 1);
                    Shapes.ring(out, c.targetCentre(partial), look, 0.4 + k * 2.6, 0.25F, (1 - k) * 0.9F * (t >= Beats.FINISH_HIT ? 1 : 0), WHITE, Ribbon.Style.GLOW);
                }
            }
            case KNIVES -> {
                float k = Mth.clamp((t - Beats.KNIFE_THROW) / 5.0F, 0, 1);
                if (t >= Beats.KNIFE_THROW) {
                    Vec3 hand = feet.add(0, 1.4, 0).add(right.scale(0.55)).add(flat.scale(0.9));
                    Shapes.ring(out, hand, look, 0.15 + k * 0.9, 0.1F, (1 - k) * 0.8F, SKY, Ribbon.Style.GLOW);
                }
            }
            case SPEED_SLICE -> {
                // the vanish: one white line from where he stood to where he stands
                float v = window(t, Beats.SLICE_VANISH - 0.5F, Beats.SLICE_VANISH, Beats.SLICE_VANISH + 2, Beats.SLICE_VANISH + 9);
                Shapes.streak(out, c.feet.add(0, 1.0, 0), c.exit.add(0, 1.0, 0), 0.7F, v, WHITE, Ribbon.Style.GLOW);
                Shapes.streak(out, c.feet.add(0, 1.0, 0), c.exit.add(0, 1.0, 0), 1.8F, v * 0.3F, SKY, Ribbon.Style.GLOW);
                // the cut through the neck, drawn on as it lands
                if (c.hasTarget()) {
                    Vec3 neck = c.targetCentre(partial).add(0, 0.45, 0);
                    Vec3 across = c.exit.subtract(c.feet).multiply(1, 0, 1);
                    across = across.lengthSqr() < 1e-4 ? c.right : new Vec3(-across.z, 0, across.x).normalize();
                    float reveal = Mth.clamp((t - Beats.SLICE_HIT + 1) / 2.0F, 0, 1);
                    float fade = window(t, Beats.SLICE_HIT - 1, Beats.SLICE_HIT, Beats.SLICE_HIT + 8, Beats.SLICE_HIT + 20);
                    Shapes.crescent(out, neck, across, new Vec3(0, 1, 0).cross(across).normalize(), 0.9, -60, 60, 0.12F, reveal, fade, WHITE, Ribbon.Style.GLOW);
                    Shapes.crescent(out, neck, across, new Vec3(0, 1, 0).cross(across).normalize(), 0.9, -60, 60, 0.35F, reveal, fade * 0.6F, BLOOD, Ribbon.Style.GLOW);
                }
            }
            case INFINITE_SPEED -> {
                float v = window(t, Beats.INF_VANISH - 0.5F, Beats.INF_VANISH, Beats.INF_VANISH + 2, Beats.INF_VANISH + 8);
                Shapes.streak(out, c.feet.add(0, 1.0, 0), c.exit.add(0, 1.0, 0), 0.6F, v, WHITE, Ribbon.Style.GLOW);
                Shapes.streak(out, c.feet.add(0, 1.0, 0), c.exit.add(0, 1.0, 0), 1.6F, v * 0.3F, MINT, Ribbon.Style.GLOW);
                if (c.hasTarget() && t >= Beats.INF_HIT - 1) {
                    Vec3 p = c.targetCentre(partial);
                    Vec3 to = p.subtract(c.exit).multiply(1, 0, 1);
                    Vec3 f = to.lengthSqr() < 1e-4 ? c.fwd : to.normalize();
                    float reveal = Mth.clamp((t - Beats.INF_HIT + 1) / 2.0F, 0, 1);
                    float fade = window(t, Beats.INF_HIT - 1, Beats.INF_HIT, Beats.INF_HIT + 5, Beats.INF_HIT + 14);
                    Shapes.blade(out, p.add(0, 0.6, 0).subtract(f.scale(0.6)), f, new Vec3(0, 1, 0), 1.3, 100, -10, 0.3F, reveal, fade, MINT);
                    float k = Mth.clamp((t - Beats.INF_HIT) / 10.0F, 0, 1);
                    Shapes.ring(out, new Vec3(p.x, c.exit.y + 0.05, p.z), new Vec3(0, 1, 0), 0.5 + k * 4.0, 0.3F, (1 - k) * 0.8F, WHITE, Ribbon.Style.GLOW);
                }
            }
            case JUDGEMENT -> {
                float lunge = window(t, Beats.JUDGE_LUNGE, Beats.JUDGE_LUNGE + 1, Beats.JUDGE_ARRIVE, Beats.JUDGE_ARRIVE + 6);
                Shapes.streak(out, c.feet.add(0, 1.0, 0), c.exit.add(0, 1.0, 0), 1.1F, lunge, GOLD, Ribbon.Style.GLOW);
                Vec3 tp = c.targetCentre(partial);
                // lightspeed: fists from every side at once
                if (t > Beats.JUDGE_ARRIVE && t < Beats.JUDGE_FINAL - 4) {
                    for (int i = 0; i < 7; i++) {
                        double yaw = r.nextDouble() * Math.PI * 2;
                        double pitch = (r.nextDouble() - 0.4) * 1.2;
                        Vec3 d = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
                        Shapes.streak(out, tp.add(d.scale(2.6 + r.nextDouble())), tp.add(d.scale(0.4)), 0.2F + r.nextFloat() * 0.2F, 0.75F,
                                i % 2 == 0 ? GOLD : WHITE, Ribbon.Style.GLOW);
                    }
                }
                for (int h : Beats.JUDGE_HITS) {
                    float k = Mth.clamp((t - h) / 5.0F, 0, 1);
                    if (t >= h && k < 1) {
                        Shapes.ring(out, tp, new Vec3(r.nextDouble() - 0.5, 1, r.nextDouble() - 0.5), 0.3 + k * 1.6, 0.12F, (1 - k) * 0.8F, GOLD, Ribbon.Style.GLOW);
                    }
                }
                if (t >= Beats.JUDGE_FINAL) {
                    float k = Mth.clamp((t - Beats.JUDGE_FINAL) / 14.0F, 0, 1);
                    Vec3 g = new Vec3(tp.x, c.exit.y + 0.05, tp.z);
                    Shapes.ring(out, g, new Vec3(0, 1, 0), 0.5 + k * 9.0, 0.6F, (1 - k), GOLD, Ribbon.Style.GLOW);
                    Shapes.ring(out, tp, flat, 0.4 + k * 5.0, 0.35F, (1 - k) * 0.9F, WHITE, Ribbon.Style.GLOW);
                    for (int i = 0; i < 16; i++) {
                        double a = i / 16.0 * Math.PI * 2;
                        Vec3 d = new Vec3(Math.cos(a), (i % 3 - 1) * 0.3, Math.sin(a)).normalize();
                        Shapes.streak(out, tp.add(d.scale(0.5 + k * 2.0)), tp.add(d.scale(1.5 + k * 7.0)), 0.25F, (1 - k) * 0.85F, i % 2 == 0 ? GOLD : WHITE, Ribbon.Style.GLOW);
                    }
                }
            }
            case DOUBLE_ACCEL -> {
                float a = window(t, 2, 10, Beats.DA_BURST, Beats.DA_BURST + 8);
                for (int i = 0; i < 3; i++) {
                    Shapes.helix(out, feet.add(0, 0.05, 0), 1.6 - i * 0.3, 0.3, 2.6, 2.0, t * (0.3 + i * 0.1) + i * 2.1, 0.12F, a * 0.8F, i == 1 ? GOLD : MINT, Ribbon.Style.GLOW);
                }
                if (t >= Beats.DA_BURST) {
                    float k = Mth.clamp((t - Beats.DA_BURST) / 12.0F, 0, 1);
                    Shapes.ring(out, feet.add(0, 0.05, 0), new Vec3(0, 1, 0), 0.6 + k * 8.0, 0.5F, (1 - k), MINT, Ribbon.Style.GLOW);
                    Shapes.ring(out, feet.add(0, 1.0, 0), new Vec3(0, 1, 0), 0.4 + k * 5.0, 0.25F, (1 - k) * 0.8F, WHITE, Ribbon.Style.GLOW);
                }
            }
            case TIME_ACCEL -> {
                float a = window(t, 6, 20, Beats.TA_START + 10, c.move.length);
                for (int i = 0; i < 4; i++) {
                    Shapes.helix(out, feet.add(0, 0.05, 0), 2.4 - i * 0.4, 0.4, 4.5, 2.5, t * (0.18 + i * 0.07) + i * 1.7, 0.16F, a * 0.75F,
                            i % 2 == 0 ? GOLD : WHITE, Ribbon.Style.GLOW);
                }
                if (t >= Beats.TA_START) {
                    float k = Mth.clamp((t - Beats.TA_START) / 30.0F, 0, 1);
                    Shapes.ring(out, feet.add(0, 0.08, 0), new Vec3(0, 1, 0), 0.8 + k * 46.0, 1.4F, (1 - k * k), GOLD, Ribbon.Style.GLOW);
                    Shapes.ring(out, feet.add(0, 0.1, 0), new Vec3(0, 1, 0), 0.5 + k * 30.0, 0.5F, (1 - k), WHITE, Ribbon.Style.GLOW);
                }
            }
            case UNIVERSE_RESET -> {
                // the world's last moments: light pouring upward from the ground round him
                float a = window(t, 2, 16, Beats.RESET_WHITE, Beats.RESET_LIFT);
                for (int i = 0; i < 18; i++) {
                    double ang = i / 18.0 * Math.PI * 2 + t * 0.02;
                    double rr = 3 + (i % 5) * 2.5;
                    Vec3 base = c.point.add(Math.cos(ang) * rr, 0.05, Math.sin(ang) * rr);
                    Shapes.streak(out, base, base.add(0, 8 + (i % 4) * 4, 0), 0.25F, a * 0.6F, i % 3 == 0 ? GOLD : WHITE, Ribbon.Style.GLOW);
                }
            }
            default -> {
            }
        }
    }

    /** Standing effects: Double Acceleration's afterglow at the feet, the edge of an accelerated zone. */
    public static void ambientRibbons(List<Ribbon> out, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        float time = now + partial;
        for (Player p : mc.level.players()) {
            ClientState.Info i = ClientState.get(p);
            if (i == null) {
                continue;
            }
            Vec3 feet = p.getPosition(partial);
            if (i.daUntil > now) {
                float left = Mth.clamp((i.daUntil - now) / 20.0F, 0, 1);
                Shapes.ring(out, feet.add(0, 0.06, 0), new Vec3(0, 1, 0), 0.9 + 0.1 * Math.sin(time * 0.4), 0.08F, 0.5F * left, MINT, Ribbon.Style.GLOW);
                Shapes.helix(out, feet.add(0, 0.05, 0), 0.8, 0.5, 1.8, 1.0, time * 0.5, 0.06F, 0.35F * left, MINT, Ribbon.Style.GLOW);
            }
            if (ClientState.accelerating(i, now)) {
                // the boundary of the acceleration: a ring of light round its edge, and clocks wheeling round him
                Shapes.ring(out, i.center.add(0, 0.1, 0), new Vec3(0, 1, 0), i.radius, 0.6F, 0.35F, GOLD, Ribbon.Style.GLOW);
                Shapes.helix(out, feet.add(0, 0.05, 0), 1.3, 0.6, 2.4, 1.5, time * 0.9, 0.08F, 0.45F, GOLD, Ribbon.Style.GLOW);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the picture

    public static void screen(ClientCast c, float t, ScreenFx fx) {
        Vec3 tp = c.targetCentre(Minecraft.getInstance().getFrameTime());
        switch (c.move) {
            case PUNCH -> {
                if (c.hasTarget()) {
                    fx.shake(0.22F * hit(t, Beats.PUNCH_HIT, 3));
                    fx.aberration(0.5F * hit(t, Beats.PUNCH_HIT, 3));
                }
            }
            case BARRAGE -> {
                float on = window(t, Beats.BARRAGE_START, Beats.BARRAGE_START + 2, Beats.BARRAGE_END, Beats.BARRAGE_END + 3);
                fx.shake(0.18F * on);
                fx.aberration(0.35F * on);
                fx.bloom(0.25F * on);
                if (c.hasTarget() && on > 0.5F && ((int) t) % 9 == 0) {
                    fx.impact(JAB, t - (int) t, tp, MINT);
                }
            }
            case FINISHER -> {
                fx.punch(0.35F * window(t, 4, Beats.FINISH_HIT - 1, Beats.FINISH_HIT, Beats.FINISH_HIT + 1));
                fx.shake(0.8F * hit(t, Beats.FINISH_HIT, 5));
                fx.punch(0.9F * hit(t, Beats.FINISH_HIT, 3));
                fx.aberration(1.0F * hit(t, Beats.FINISH_HIT, 5));
                if (c.hasTarget()) {
                    fx.impact(Impact.HEAVY, t - Beats.FINISH_HIT, tp, WHITE);
                }
            }
            case KNIVES -> {
                fx.punch(0.25F * hit(t, Beats.KNIFE_THROW, 3));
                fx.aberration(0.4F * hit(t, Beats.KNIFE_THROW, 4));
            }
            case SPEED_SLICE -> {
                // the world goes still and grey; he is gone; the cut lands in red
                float hush = window(t, 2, 12, Beats.SLICE_HIT, Beats.SLICE_HIT + 1);
                fx.desaturate(0.75F * hush);
                fx.vignette(0x000000, 0.6F * hush);
                fx.grain(0.5F * hush);
                fx.contrast(0.3F * hush);
                fx.flash(WHITE, 0.6F * hit(t, Beats.SLICE_VANISH, 2));
                fx.aberration(1.3F * hit(t, Beats.SLICE_VANISH, 4));
                fx.punch(0.6F * hit(t, Beats.SLICE_VANISH, 3));
                fx.impact(SLICE, t - Beats.SLICE_HIT, tp, BLOOD);
                fx.shake(0.9F * hit(t, Beats.SLICE_HIT, 6));
                fx.vignette(0x5A0000, 0.55F * window(t, Beats.SLICE_HIT, Beats.SLICE_HIT + 2, 40, 56));
                fx.bloom(0.5F * hit(t, Beats.SLICE_HIT, 10));
            }
            case INFINITE_SPEED -> {
                float wind = window(t, 2, 10, Beats.INF_VANISH, Beats.INF_VANISH + 1);
                fx.desaturate(0.4F * wind);
                fx.vignette(0x002018, 0.5F * wind);
                fx.flash(0xCFFFF0, 0.7F * hit(t, Beats.INF_VANISH, 2));
                fx.aberration(1.4F * hit(t, Beats.INF_VANISH, 4));
                fx.punch(0.7F * hit(t, Beats.INF_VANISH, 3));
                fx.impact(CHOP, t - Beats.INF_HIT, tp, MINT);
                fx.shake(1.0F * hit(t, Beats.INF_HIT, 6));
                fx.bloom(0.5F * hit(t, Beats.INF_HIT, 10));
            }
            case JUDGEMENT -> {
                float rear = window(t, 2, 12, Beats.JUDGE_LUNGE, Beats.JUDGE_LUNGE + 2);
                fx.vignette(0x302000, 0.45F * rear);
                fx.bloom(0.4F * rear);
                fx.contrast(0.25F * rear);
                fx.punch(1.1F * hit(t, Beats.JUDGE_LUNGE, 4));
                fx.aberration(1.4F * hit(t, Beats.JUDGE_LUNGE, 6));
                for (int h : Beats.JUDGE_HITS) {
                    fx.shake(0.45F * hit(t, h, 3));
                    fx.aberration(0.6F * hit(t, h, 3));
                    fx.impact((h / 4) % 2 == 0 ? new int[][]{{Impact.INK, 1}} : new int[][]{{Impact.INVERT, 1}}, t - h, tp, GOLD);
                }
                fx.punch(0.5F * window(t, Beats.JUDGE_FINAL - 6, Beats.JUDGE_FINAL - 1, Beats.JUDGE_FINAL, Beats.JUDGE_FINAL + 1));
                fx.impact(JUDGE_FINAL, t - Beats.JUDGE_FINAL, tp, GOLD);
                fx.flash(0xFFF2D0, 1.1F * hit(t, Beats.JUDGE_FINAL, 4));
                fx.shake(1.1F * hit(t, Beats.JUDGE_FINAL, 10));
                fx.bloom(0.8F * hit(t, Beats.JUDGE_FINAL, 14));
            }
            case DOUBLE_ACCEL -> {
                float build = window(t, 2, Beats.DA_BURST, Beats.DA_BURST, Beats.DA_BURST + 2);
                fx.aberration(0.9F * build);
                fx.vignette(0x00382A, 0.55F * build);
                fx.desaturate(0.3F * build);
                fx.impact(ACCEL, t - Beats.DA_BURST, c.casterFeet(0).add(0, 1.6, 0), MINT);
                fx.flash(0xC8FFF0, 0.9F * hit(t, Beats.DA_BURST, 4));
                fx.punch(1.0F * hit(t, Beats.DA_BURST, 4));
                fx.shake(0.6F * hit(t, Beats.DA_BURST, 6));
                fx.bloom(0.6F * hit(t, Beats.DA_BURST, 12));
            }
            case TIME_ACCEL -> {
                float menace = window(t, 2, 20, Beats.TA_START, Beats.TA_START + 2);
                fx.desaturate(0.55F * menace);
                fx.vignette(0x1A1000, 0.6F * menace);
                fx.grain(0.4F * menace);
                fx.shake(0.15F * menace);
                fx.impact(ACCEL, t - Beats.TA_START, c.casterFeet(0).add(0, 1.6, 0), GOLD);
                fx.flash(0xFFE9A8, 1.3F * hit(t, Beats.TA_START, 6));
                fx.punch(1.1F * hit(t, Beats.TA_START, 5));
                fx.shake(0.9F * hit(t, Beats.TA_START, 10));
                fx.bloom(0.7F * window(t, Beats.TA_START, Beats.TA_START + 2, 80, 92));
            }
            case UNIVERSE_RESET -> reset(c, t, fx);
            default -> {
            }
        }
    }

    private static void reset(ClientCast c, float t, ScreenFx fx) {
        // the world brightening to nothing
        float white = Mth.clamp((t - 6) / (Beats.RESET_WHITE - 6), 0, 1);
        fx.flash(0xFFFFFF, white * white * 1.15F * (t < Beats.RESET_LIFT ? 1 : 0));
        fx.desaturate(0.6F * white * (t < Beats.RESET_LIFT ? 1 : 0));
        fx.shake(0.35F * window(t, 10, 30, Beats.RESET_WHITE, Beats.RESET_LIFT));
        fx.aberration(0.8F * window(t, 10, 30, Beats.RESET_WHITE, Beats.RESET_LIFT));
        fx.impact(RESET_CRACK, t - Beats.RESET_WHITE, c.eye, 0xFFE9A8);
        // hold white while the world is swapped for space, then let the galaxy through
        float hold = window(t, Beats.RESET_LIFT - 0.5F, Beats.RESET_LIFT, Beats.RESET_GALAXY, Beats.RESET_GALAXY + 12);
        fx.flash(0xFFFFFF, 1.6F * hold);
        float space = window(t, Beats.RESET_GALAXY, Beats.RESET_GALAXY + 12, Beats.RESET_SINGULARITY, Beats.RESET_SINGULARITY + 1);
        fx.bloom(0.6F * space);
        fx.grain(0.25F * space);
        fx.vignette(0x02031A, 0.45F * space);
        float plunge = window(t, Beats.RESET_PLUNGE, Beats.RESET_SINGULARITY - 2, Beats.RESET_SINGULARITY, Beats.RESET_SINGULARITY + 1);
        fx.punch(0.9F * plunge);
        fx.aberration(1.4F * plunge);
        fx.shake(0.5F * plunge);
        // the singularity: everything at once, then white
        fx.impact(SINGULARITY, t - Beats.RESET_SINGULARITY, c.eye.add(0, Galaxy.LIFT, 0), 0xFFFFFF);
        float burn = window(t, Beats.RESET_SINGULARITY + 5, Beats.RESET_SINGULARITY + 7, Beats.RESET_NEW, Beats.RESET_NEW + 22);
        fx.flash(0xFFFFFF, 1.7F * burn);
        fx.shake(1.2F * hit(t, Beats.RESET_SINGULARITY, 8));
        // the new universe: a soft, bright dawn
        float dawn = window(t, Beats.RESET_NEW, Beats.RESET_NEW + 10, 270, 300);
        fx.bloom(0.45F * dawn);
        fx.desaturate(0.25F * window(t, Beats.RESET_NEW, Beats.RESET_NEW + 2, 240, 290));
        fx.vignette(0xFFF4E0, 0.25F * dawn);
    }

    /** Standing looks: the clock-green fringe of Double Acceleration, the gold of accelerated time. */
    public static void ambientScreen(ScreenFx fx, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = mc.level.getGameTime();
        ClientState.Info mine = ClientState.get(mc.player);
        if (mine != null && mine.daUntil > now) {
            float left = Mth.clamp((mine.daUntil - now) / 20.0F, 0, 1);
            fx.aberration(0.18F * left);
            fx.vignette(0x00382A, 0.2F * left);
        }
        double accel = ClientState.accelProgress(partial);
        if (accel >= 0) {
            float k = (float) accel;
            fx.bloom(0.15F + 0.35F * k);
            fx.vignette(0x2A1A00, 0.12F + 0.25F * k);
            fx.contrast(0.12F * k);
            fx.aberration(0.25F * k * k);
        }
    }
}
