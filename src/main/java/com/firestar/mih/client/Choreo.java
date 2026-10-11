package com.firestar.mih.client;

import static com.firestar.mih.client.fx.ScreenFx.hit;
import static com.firestar.mih.client.fx.ScreenFx.window;

import com.firestar.mih.client.fx.Impact;
import com.firestar.mih.client.fx.Light;
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
import net.minecraft.sounds.SoundEvent;
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
    static final Vec3 UP = new Vec3(0, 1, 0);

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

    // ------------------------------------------------------------------------------------------------ per tick

    public static void begin(ClientCast c) {
        if (c.move == Move.UNIVERSE_RESET) {
            Galaxy.capture(c);
        }
    }

    public static void tick(ClientCast c, int t) {
        Random r = new Random(c.seed * 31L + t);
        Vec3 user = c.casterFeet(0).add(0, 1.0, 0);
        Vec3 tp = c.targetCentre(0);
        Entity caster = c.caster();
        Vec3 look = caster != null ? caster.getLookAngle() : c.dir;
        switch (c.move) {
            case SUMMON -> {
                if (t == 0) {
                    play(user, ModSounds.STAND_SUMMON.get(), 1.0F, 1.0F);
                    Vec3 stand = c.local(0.85, 1.6, -0.95);
                    Light.flare(stand, 2.4F, MINT, 0.5F);
                    Light.flare(stand, 0.9F, WHITE, 0.3F);
                    Light.ring(c.feet.add(0, 0.05, 0), UP, 0.3F, 3.0F, 0.35F, MINT, 0.5F);
                    Light.spray(stand, UP, 0.8F, 26, 6.0F, MINT, 0.6F, 0.05F);
                }
            }
            case PUNCH -> {
                if (t == 1 && c.exit.distanceTo(c.feet) > 1.0) {
                    // a blink of light to the target
                    play(user, ModSounds.DASH.get(), 1.0F, 1.0F + r.nextFloat() * 0.2F);
                    play(user, ModSounds.BASS.get(), 0.9F, 1.1F);
                    Light.flare(c.feet.add(0, 1.0, 0), 1.6F, SKY, 0.25F);
                }
                if (t == Beats.PUNCH_HIT && c.hasTarget()) {
                    play(tp, ModSounds.FIST.get(), 1.0F, 0.95F + r.nextFloat() * 0.2F);
                    Light.hit(tp, look, WHITE, 0.8F);
                }
            }
            case BARRAGE -> {
                if (t == Beats.BARRAGE_START) {
                    play(user, ModSounds.BARRAGE_RUSH.get(), 1.0F, c.hasTarget() ? 1.0F : 1.15F);
                    play(user, ModSounds.BASS.get(), 0.5F, 1.3F);
                }
                if (t >= Beats.BARRAGE_START && t <= Beats.BARRAGE_END) {
                    Vec3 front = user.add(look.scale(2.4));
                    Vec3 at = (c.hasTarget() ? tp : front).add((r.nextDouble() - 0.5) * 0.9, (r.nextDouble() - 0.5) * 0.9, (r.nextDouble() - 0.5) * 0.9);
                    if (c.hasTarget()) {
                        Light.hit(at, look, t % 3 == 0 ? MINT : WHITE, 0.45F);
                    } else if (t % 2 == 0) {
                        Light.spray(at, look, 0.4F, 3, 6.0F, WHITE, 0.2F, 0.04F);
                    }
                }
            }
            case FINISHER -> {
                if (t == Beats.FINISH_HIT - 4) {
                    play(user, ModSounds.TOCK.get(), 0.8F, 0.7F);
                    Light.flare(user.add(look.scale(0.8)).add(0, 0.3, 0), 1.2F, MINT, 0.3F);
                }
                if (t == Beats.FINISH_HIT) {
                    Vec3 at = c.hasTarget() ? tp : user.add(look.scale(3.0));
                    play(at, ModSounds.FINISHER.get(), 1.2F, 1.0F);
                    play(at, ModSounds.BASS.get(), 1.0F, 1.0F);
                    Light.hit(at, look, WHITE, 1.8F);
                    Light.ring(at, look, 0.3F, 3.6F, 0.35F, MINT, 0.4F);
                }
            }
            case KNIVES -> {
                if (t == Beats.KNIFE_THROW) {
                    Vec3 hand = user.add(0, 0.4, 0).add(c.right.scale(0.55)).add(c.fwd.scale(0.9));
                    play(hand, ModSounds.KNIFE_THROW.get(), 1.0F, 1.0F);
                    Light.flare(hand, 1.0F, SKY, 0.2F);
                    Light.spray(hand, look, 0.25F, 10, 14.0F, WHITE, 0.25F, 0.04F);
                }
            }
            case SPEED_SLICE -> {
                if (t == 2) {
                    play(user, ModSounds.TOCK.get(), 1.0F, 0.6F);
                }
                if (t == 8 || t == 12 || t == 15) {
                    play(user, ModSounds.TICK.get(), 1.0F, 0.9F + t * 0.02F);
                }
                if (t == Beats.SLICE_VANISH) {
                    play(user, ModSounds.BASS.get(), 1.2F, 1.0F);
                    play(user, ModSounds.DASH.get(), 1.0F, 0.9F);
                    Light.flare(c.feet.add(0, 1.0, 0), 2.2F, WHITE, 0.3F);
                    Light.flare(c.exit.add(0, 1.0, 0), 2.2F, WHITE, 0.3F);
                }
                if (t == Beats.SLICE_HIT) {
                    Vec3 neck = tp.add(0, 0.45, 0);
                    play(neck, ModSounds.SLICE.get(), 1.3F, 1.0F);
                    play(neck, ModSounds.JUDGE_CUT.get(), 1.0F, 0.9F);
                    Light.hit(neck, c.exit.subtract(c.feet), WHITE, 1.4F);
                    Light.spray(neck, UP.add(c.exit.subtract(c.feet).normalize()), 0.6F, 40, 7.0F, BLOOD, 0.7F, 0.07F);
                }
                if (t > Beats.SLICE_HIT && t < Beats.SLICE_HIT + 60 && t % 10 == 0 && c.hasTarget()) {
                    Light.spray(tp.add(0, 0.4, 0), UP, 0.5F, 6, 2.5F, BLOOD, 0.5F, 0.05F);
                }
            }
            case JUDGEMENT -> {
                if (t == 2) {
                    play(user, ModSounds.TOCK.get(), 1.0F, 0.5F);
                }
                if (t > 2 && t < Beats.JUDGE_LUNGE && t % 3 == 0) {
                    play(user, ModSounds.TICK.get(), 0.9F, 0.8F + t * 0.05F);
                }
                if (t == Beats.JUDGE_LUNGE - 2) {
                    play(user, ModSounds.ACCEL_BASS.get(), 1.0F, 1.2F);
                }
                for (int i = 0; i < Beats.JUDGE_DASHES.length; i++) {
                    if (t == Beats.JUDGE_DASHES[i]) {
                        Vec3 from = (i == 0 ? c.feet : Beats.judgeDash(c.exit, c.seed, i - 1)).add(0, 1.0, 0);
                        Vec3 to = Beats.judgeDash(c.exit, c.seed, i).add(0, 1.0, 0);
                        play(tp, ModSounds.JUDGE_CUT.get(), 1.1F, 0.9F + i * 0.05F);
                        play(tp, ModSounds.DASH.get(), 0.8F, 1.0F + i * 0.04F);
                        if (i % 2 == 0) {
                            play(tp, ModSounds.BASS.get(), 0.8F, 1.0F + i * 0.05F);
                        }
                        Light.flare(to, 1.4F, GOLD, 0.25F);
                        Light.hit(tp, to.subtract(from), i % 2 == 0 ? GOLD : WHITE, 0.9F);
                    }
                }
                if (t == Beats.JUDGE_FINAL) {
                    play(tp, ModSounds.JUDGEMENT.get(), 1.5F, 1.0F);
                    play(tp, ModSounds.DETONATE.get(), 1.2F, 1.1F);
                    play(tp, ModSounds.BASS.get(), 1.3F, 0.85F);
                    Light.flare(tp, 5.0F, GOLD, 0.6F);
                    Light.flare(tp, 2.0F, WHITE, 0.35F);
                    Light.burst(tp, 70, 16.0F, GOLD, 0.8F, 0.08F);
                    Light.burst(tp, 30, 10.0F, WHITE, 0.5F, 0.05F);
                    Light.ring(tp, UP, 0.5F, 9.0F, 0.6F, GOLD, 0.7F);
                    // every cut that was left hanging in the air bursts into light
                    for (int i = 0; i < Beats.JUDGE_DASHES.length; i++) {
                        Vec3 from = (i == 0 ? c.feet : Beats.judgeDash(c.exit, c.seed, i - 1)).add(0, 1.0, 0);
                        Vec3 to = Beats.judgeDash(c.exit, c.seed, i).add(0, 1.0, 0);
                        for (int k = 0; k < 6; k++) {
                            Vec3 at = from.lerp(to, k / 5.0);
                            Light.burst(at, 3, 4.0F, i % 2 == 0 ? GOLD : WHITE, 0.5F, 0.05F);
                        }
                    }
                }
            }
            case DOUBLE_ACCEL -> {
                if (t == 1) {
                    play(user, ModSounds.DOUBLE_ACCEL.get(), 1.1F, 1.0F);
                }
                if (t < Beats.DA_BURST && t % Math.max(1, 6 - t / 5) == 0) {
                    play(user, ModSounds.TICK.get(), 0.6F, 1.0F + t * 0.03F);
                }
                if (t == Beats.DA_BURST) {
                    play(user, ModSounds.ACCEL_BASS.get(), 1.2F, 1.0F);
                    Light.flare(user.add(0, 0.6, 0), 4.0F, MINT, 0.5F);
                    Light.ring(c.feet.add(0, 0.05, 0), UP, 0.5F, 8.0F, 0.5F, MINT, 0.6F);
                    Light.burst(user, 50, 12.0F, MINT, 0.7F, 0.06F);
                }
            }
            case INFINITE_SPEED -> {
                if (t == Beats.INF_ON - 5) {
                    play(user, ModSounds.INFINITE_START.get(), 1.3F, 1.0F);
                }
                if (t == Beats.INF_ON) {
                    play(user, ModSounds.BASS.get(), 1.3F, 0.8F);
                    Light.flare(user, 4.0F, SKY, 0.45F);
                    Light.ring(c.feet.add(0, 0.08, 0), UP, 0.5F, (float) Beats.INF_RADIUS, 0.9F, SKY, 0.9F);
                    Light.ring(user, c.fwd, 0.3F, 5.0F, 0.3F, WHITE, 0.4F);
                    Light.burst(user, 40, 10.0F, SKY, 0.6F, 0.05F);
                }
            }
            case LIGHTSPEED_RUSH -> {
                if (t == Beats.RUSH_GO) {
                    play(user, ModSounds.RUSH.get(), 1.3F, 1.0F);
                    play(user, ModSounds.BASS.get(), 1.1F, 1.15F);
                    Light.flare(c.feet.add(0, 1.0, 0), 2.6F, WHITE, 0.35F);
                    Light.ring(c.feet.add(0, 1.0, 0), c.fwd, 0.3F, 2.8F, 0.3F, WHITE, 0.35F);
                }
                if (t == Beats.RUSH_GO + Beats.RUSH_STEPS - 1) {
                    Vec3 a = c.feet.add(0, 1.0, 0), b = c.exit.add(0, 1.0, 0);
                    for (int k = 0; k <= 12; k++) {
                        Light.spray(a.lerp(b, k / 12.0), c.fwd, 0.5F, 3, 8.0F, k % 2 == 0 ? WHITE : SKY, 0.4F, 0.05F);
                    }
                    Light.flare(b, 2.0F, WHITE, 0.3F);
                    if (c.hasTarget()) {
                        play(tp, ModSounds.LIGHT_HIT.get(), 1.2F, 1.0F);
                        Light.hit(tp, c.fwd, SKY, 1.3F);
                    }
                }
            }
            case KNIFE_STORM -> {
                int i = t - Beats.STORM_SPAWN;
                if (i >= 0 && i < Beats.STORM_KNIVES) {
                    play(user, ModSounds.KNIFE_HANG.get(), 0.7F, 0.9F + i * 0.04F);
                }
                if (t == Beats.STORM_AIM) {
                    play(user, ModSounds.TOCK.get(), 1.0F, 0.6F);
                }
                if (t == Beats.STORM_FIRE) {
                    play(user, ModSounds.KNIFE_VOLLEY.get(), 1.3F, 1.0F);
                    play(user, ModSounds.BASS.get(), 1.0F, 1.2F);
                    Light.ring(user.add(0, 0.9, 0), c.fwd, 0.5F, 4.0F, 0.4F, SKY, 0.4F);
                }
            }
            case TIME_ACCEL -> {
                if (t == 1) {
                    play(user, ModSounds.TIME_ACCEL.get(), 1.4F, 1.0F);
                    play(user, ModSounds.MENACING.get(), 1.0F, 1.0F);
                }
                if (t < Beats.TA_START && t % Math.max(1, 10 - t / 5) == 0) {
                    play(user, t % 2 == 0 ? ModSounds.TICK.get() : ModSounds.TOCK.get(), 0.9F, 0.8F + t * 0.01F);
                }
                if (t == Beats.TA_START) {
                    play(user, ModSounds.ACCEL_BASS.get(), 1.4F, 0.9F);
                    play(user, ModSounds.CRACK.get(), 0.8F, 1.3F);
                    Light.flare(user.add(0, 1.0, 0), 6.0F, GOLD, 0.7F);
                    Light.ring(c.feet.add(0, 0.08, 0), UP, 0.8F, 46.0F, 1.4F, GOLD, 1.5F);
                    Light.burst(user.add(0, 1.0, 0), 80, 14.0F, GOLD, 0.9F, 0.06F);
                }
            }
            case UNIVERSE_RESET -> {
                if (t < Beats.RESET_WHITE && t % 2 == 0) {
                    play(user, ModSounds.TICK.get(), 0.7F, 1.4F + t * 0.02F);
                }
                if (t == Beats.RESET_WHITE) {
                    play(user, ModSounds.CRACK.get(), 1.4F, 0.8F);
                    play(user, ModSounds.MENACING.get(), 1.2F, 0.7F);
                }
                if (t == Beats.RESET_GALAXY) {
                    play(user, ModSounds.GALAXY.get(), 1.4F, 1.0F);
                }
                if (t == Beats.RESET_PLUNGE + 14) {
                    play(user, ModSounds.PLUNGE.get(), 1.4F, 1.0F);
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
            case JUDGEMENT -> {
                // the web of light: every dash leaves its line hanging in the air until the final cut
                for (int i = 0; i < Beats.JUDGE_DASHES.length; i++) {
                    float at = Beats.JUDGE_DASHES[i];
                    if (t < at - 0.5F) {
                        break;
                    }
                    Vec3 from = (i == 0 ? c.feet : Beats.judgeDash(c.exit, c.seed, i - 1)).add(0, 1.0, 0);
                    Vec3 to = Beats.judgeDash(c.exit, c.seed, i).add(0, 1.0, 0);
                    float flash = ScreenFx.hit(t, at, 3.0F);
                    float hang = window(t, at - 0.5F, at, Beats.JUDGE_FINAL, Beats.JUDGE_FINAL + 6);
                    int col = i % 2 == 0 ? GOLD : WHITE;
                    Shapes.streak(out, from, to, 0.18F + 0.5F * flash, hang * (0.55F + 0.45F * flash), col, Ribbon.Style.GLOW);
                    Shapes.streak(out, from, to, 0.06F, hang, WHITE, Ribbon.Style.GLOW);
                    Shapes.streak(out, from, to, 1.2F, hang * 0.18F + flash * 0.3F, col, Ribbon.Style.GLOW);
                }
                if (t >= Beats.JUDGE_FINAL) {
                    float k = Mth.clamp((t - Beats.JUDGE_FINAL) / 14.0F, 0, 1);
                    Vec3 tp = c.targetCentre(partial);
                    Shapes.ring(out, new Vec3(tp.x, c.exit.y + 0.05, tp.z), new Vec3(0, 1, 0), 0.5 + k * 9.0, 0.6F, (1 - k), GOLD, Ribbon.Style.GLOW);
                    for (int i = 0; i < 16; i++) {
                        double a = i / 16.0 * Math.PI * 2;
                        Vec3 d = new Vec3(Math.cos(a), (i % 3 - 1) * 0.3, Math.sin(a)).normalize();
                        Shapes.streak(out, tp.add(d.scale(0.5 + k * 2.0)), tp.add(d.scale(1.5 + k * 7.0)), 0.25F, (1 - k) * 0.85F, i % 2 == 0 ? GOLD : WHITE, Ribbon.Style.GLOW);
                    }
                }
            }
            case LIGHTSPEED_RUSH -> {
                float a = window(t, Beats.RUSH_GO - 0.5F, Beats.RUSH_GO + 1, Beats.RUSH_GO + Beats.RUSH_STEPS, Beats.RUSH_GO + Beats.RUSH_STEPS + 10);
                Vec3 s0 = c.feet.add(0, 1.0, 0), s1 = c.exit.add(0, 1.0, 0);
                Shapes.streak(out, s0, s1, 0.5F, a, WHITE, Ribbon.Style.GLOW);
                Shapes.streak(out, s0, s1, 1.8F, a * 0.35F, SKY, Ribbon.Style.GLOW);
                for (int i = 0; i < 6; i++) {
                    Vec3 off = new Vec3(0, (i - 2.5) * 0.25, 0).add(c.right.scale((r.nextDouble() - 0.5) * 1.2));
                    Shapes.streak(out, s0.add(off), s1.add(off), 0.06F, a * 0.6F, WHITE, Ribbon.Style.GLOW);
                }
            }
            case KNIFE_STORM -> {
                // a halo of clock-light behind him while the knives hang
                float a = window(t, Beats.STORM_SPAWN, Beats.STORM_SPAWN + 6, Beats.STORM_FIRE, Beats.STORM_FIRE + 6);
                Vec3 centre = c.feet.add(0, 1.9, 0).subtract(c.fwd.scale(0.4));
                Shapes.ring(out, centre, c.fwd, 2.3, 0.08F, a * 0.7F, SKY, Ribbon.Style.GLOW);
                Shapes.ring(out, centre, c.fwd, 2.9, 0.05F, a * 0.5F, WHITE, Ribbon.Style.GLOW);
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
                fx.impact(ACCEL, t - Beats.INF_ON, c.casterFeet(0).add(0, 1.4, 0), SKY);
                fx.flash(0xD8F4FF, 0.9F * hit(t, Beats.INF_ON, 4));
                fx.punch(1.0F * hit(t, Beats.INF_ON, 4));
                fx.aberration(1.3F * hit(t, Beats.INF_ON, 6));
                fx.shake(0.7F * hit(t, Beats.INF_ON, 6));
            }
            case LIGHTSPEED_RUSH -> {
                int end = Beats.RUSH_GO + Beats.RUSH_STEPS - 1;
                fx.punch(1.1F * hit(t, Beats.RUSH_GO, 4));
                fx.aberration(1.4F * hit(t, Beats.RUSH_GO, 6));
                fx.flash(0xEAF6FF, 0.5F * hit(t, Beats.RUSH_GO, 2));
                fx.shake(0.6F * hit(t, end, 5));
                if (c.hasTarget()) {
                    fx.impact(CHOP, t - end, tp, SKY);
                }
            }
            case KNIFE_STORM -> {
                float hush = window(t, 2, 10, Beats.STORM_FIRE, Beats.STORM_FIRE + 2);
                fx.desaturate(0.5F * hush);
                fx.vignette(0x001020, 0.5F * hush);
                fx.grain(0.35F * hush);
                fx.punch(0.9F * hit(t, Beats.STORM_FIRE, 4));
                fx.aberration(1.0F * hit(t, Beats.STORM_FIRE, 6));
                fx.shake(0.5F * hit(t, Beats.STORM_FIRE, 8));
                fx.impact(new int[][]{{Impact.INVERT, 1}, {Impact.LINES, 1}}, t - Beats.STORM_FIRE, tp, SKY);
            }
            case JUDGEMENT -> {
                float rear = window(t, 2, 12, Beats.JUDGE_LUNGE, Beats.JUDGE_LUNGE + 2);
                fx.vignette(0x302000, 0.45F * rear);
                fx.bloom(0.4F * rear);
                fx.contrast(0.25F * rear);
                // the dashes: the world goes grey and still while he moves through it
                float still = window(t, Beats.JUDGE_DASHES[0] - 1, Beats.JUDGE_DASHES[0], Beats.JUDGE_FINAL - 1, Beats.JUDGE_FINAL);
                fx.desaturate(0.55F * still);
                fx.vignette(0x000000, 0.4F * still);
                fx.grain(0.3F * still);
                fx.bloom(0.35F * still);
                for (int i = 0; i < Beats.JUDGE_DASHES.length; i++) {
                    int h = Beats.JUDGE_DASHES[i];
                    fx.shake(0.4F * hit(t, h, 3));
                    fx.aberration(0.9F * hit(t, h, 3));
                    fx.punch(0.35F * hit(t, h, 2));
                    fx.impact(i % 2 == 0 ? new int[][]{{Impact.INK, 1}} : new int[][]{{Impact.SLASHED, 1}}, t - h, tp, GOLD);
                }
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
                fx.bloom(0.25F * window(t, Beats.TA_START, Beats.TA_START + 2, 80, 92));
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
            fx.bloom(0.05F + 0.15F * k);
            fx.vignette(0x2A1A00, 0.12F + 0.25F * k);
            fx.contrast(0.12F * k);
            fx.aberration(0.25F * k * k);
        }
        // infinite speed: everything else nearly still, the colour drained, a cold edge to the frame
        for (Player pl : mc.level.players()) {
            ClientState.Info i = ClientState.get(pl);
            if (i != null && i.infUntil > now && pl.distanceTo(mc.player) < Beats.INF_RADIUS + 8) {
                float left = Mth.clamp((i.infUntil - now - partial) / 8.0F, 0, 1);
                fx.desaturate(0.55F * left);
                fx.vignette(0x0A2A40, 0.45F * left);
                fx.aberration(0.3F * left);
                fx.grain(0.25F * left);
                fx.contrast(0.15F * left);
            }
        }
        float t = now + partial;
        PULSES.removeIf(pu -> t - pu.start > 40);
        for (Pulse pu : PULSES) {
            fx.impact(pu.seq, t - pu.start, pu.at, pu.rgb);
            fx.flash(0xFFFFFF, 1.0F * hit(t, pu.start, 4));
            fx.shake(1.0F * hit(t, pu.start, 10));
            fx.punch(0.8F * hit(t, pu.start, 4));
        }
    }

    // ------------------------------------------------------------------------------------------------ outside the casts

    private record Pulse(float start, int[][] seq, Vec3 at, int rgb) {}

    private static final java.util.List<Pulse> PULSES = new java.util.ArrayList<>();
    static final int[][] DETONATE = {{Impact.WHITEOUT, 1}, {Impact.INVERT, 1}, {Impact.SLASHED, 1}, {Impact.INK, 1}, {Impact.LINES, 2},
            {Impact.NEGATIVE, 1}, {Impact.HALFTONE, 1}};

    /** A one-off from the server: Infinite Speed snapping back, every remembered blow landing at once. */
    public static void serverFx(com.firestar.mih.network.S2CFx m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (m.kind == com.firestar.mih.network.S2CFx.DETONATE) {
            Entity user = mc.level.getEntity(m.entityId);
            Vec3 u = user != null ? user.position().add(0, 1.0, 0) : (m.points.isEmpty() ? Vec3.ZERO : m.points.get(0));
            play(u, ModSounds.BASS.get(), 1.3F, 0.8F);
            play(u, ModSounds.CRACK.get(), 0.7F, 1.6F);
            if (!m.points.isEmpty()) {
                play(m.points.get(0), ModSounds.DETONATE.get(), 1.5F, 1.0F);
            }
            for (Vec3 p : m.points) {
                Light.flare(p, 3.4F, SKY, 0.5F);
                Light.flare(p, 1.4F, WHITE, 0.3F);
                Light.burst(p, 40, 13.0F, SKY, 0.7F, 0.07F);
                Light.ring(p, UP, 0.3F, 4.0F, 0.4F, WHITE, 0.45F);
                for (int k = 0; k < 4; k++) {
                    Vec3 d = new Vec3(Math.cos(k * 1.57 + p.x), 0.3 * (k - 1.5), Math.sin(k * 1.57 + p.z)).normalize();
                    Light.spray(p, d, 0.15F, 8, 18.0F, WHITE, 0.3F, 0.05F);
                }
            }
            if (user != null && user.distanceTo(mc.player) < 40 && !m.points.isEmpty()) {
                PULSES.add(new Pulse(mc.level.getGameTime() + mc.getFrameTime(), DETONATE, m.points.get(0), SKY));
            }
        }
    }

    /** A knife strikes (seen by every client from its own copy of the knife). */
    public static void knifeHit(Vec3 at, Vec3 dir, boolean flesh) {
        play(at, flesh ? ModSounds.KNIFE_FLESH.get() : ModSounds.KNIFE_HIT.get(), 0.9F, 0.9F + (float) Math.random() * 0.3F);
        if (flesh) {
            Light.hit(at, dir.scale(-1), SKY, 0.6F);
            Light.spray(at, dir, 0.5F, 10, 4.0F, BLOOD, 0.4F, 0.04F);
        } else {
            Light.flare(at, 0.6F, WHITE, 0.15F);
            Light.spray(at, dir.scale(-1), 0.6F, 8, 7.0F, 0xFFE9B0, 0.3F, 0.03F);
        }
    }

    /** The Stand put away: its light folds up. */
    public static void dismiss(Entity e) {
        Vec3 at = e.position().add(0, 1.4, 0);
        play(at, ModSounds.STAND_DISMISS.get(), 0.9F, 1.0F);
        Light.flare(at, 1.6F, MINT, 0.35F);
        Light.burst(at, 14, 3.0F, MINT, 0.4F, 0.04F);
    }

    /**
     * Every rendered frame: feed the light trails. The user streaks whenever he moves faster than he should (Double
     * Acceleration, accelerated time, Infinite Speed, the dashes); the Stand's fists streak when they strike; knives streak.
     */
    public static void frame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (Player p : mc.level.players()) {
            ClientState.Info i = ClientState.get(p);
            ClientCast c = Casts.of(p);
            float t = c == null ? -1 : c.time(partial);
            boolean dash = c != null && switch (c.move) {
                case SPEED_SLICE -> t > Beats.SLICE_VANISH - 1 && t < Beats.SLICE_VANISH + 3;
                case JUDGEMENT -> t > Beats.JUDGE_LUNGE && t < Beats.JUDGE_FINAL;
                case LIGHTSPEED_RUSH -> t > Beats.RUSH_GO - 1 && t < Beats.RUSH_GO + Beats.RUSH_STEPS + 2;
                case PUNCH -> c.exit.distanceTo(c.feet) > 1.0 && t < 4;
                default -> false;
            };
            boolean inf = i != null && i.infUntil > now;
            boolean fast = i != null && (i.daUntil > now || ClientState.accelerating(i, now));
            Vec3 chest = p.getPosition(partial).add(0, 1.0, 0);
            if (dash || inf || fast) {
                int col = dash ? (c.move == Move.JUDGEMENT ? GOLD : WHITE) : inf ? SKY : MINT;
                Light.trail("u" + p.getId(), chest, col, dash ? 1.3F : 0.9F, dash ? 0.45F : 0.3F);
                Light.trail("uh" + p.getId(), chest.add(0, 0.6, 0), WHITE, 0.35F, 0.22F);
            }
            if (c != null && t <= c.move.length && (c.move == Move.PUNCH || c.move == Move.BARRAGE || c.move == Move.FINISHER
                    || c.move == Move.KNIVES || c.move == Move.JUDGEMENT) && p instanceof net.minecraft.client.player.AbstractClientPlayer ap) {
                com.firestar.mih.client.stand.StandRenderer.Placement pl = com.firestar.mih.client.stand.StandRenderer.place(ap, partial);
                if (pl != null) {
                    Vec3[] f = com.firestar.mih.client.stand.StandRenderer.fists(pl);
                    float w = c.move == Move.BARRAGE ? 0.26F : 0.38F;
                    Light.trail("fr" + p.getId(), f[0], 0xD8FFF4, w, 0.14F);
                    Light.trail("fl" + p.getId(), f[1], 0xD8FFF4, w, 0.14F);
                }
            }
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof com.firestar.mih.server.KnifeEntity k && k.getDeltaMovement().lengthSqr() > 0.01) {
                Light.trail("k" + k.getId(), k.getPosition(partial), 0xCFE8FF, 0.14F, 0.12F);
            }
        }
    }
}
