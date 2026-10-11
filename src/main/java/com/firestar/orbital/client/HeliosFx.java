package com.firestar.orbital.client;

import com.firestar.orbital.client.cinematic.CameraPose;
import com.firestar.orbital.client.cinematic.Film;
import com.firestar.orbital.client.cinematic.Shot;
import com.firestar.orbital.client.fx.Impact;
import com.firestar.orbital.client.fx.Light;
import com.firestar.orbital.client.fx.Ribbon;
import com.firestar.orbital.client.fx.ScreenFx;
import com.firestar.orbital.client.fx.Shapes;
import com.firestar.orbital.client.fx.Smoke;
import com.firestar.orbital.client.space.Space;
import com.firestar.orbital.common.Cannon;
import com.firestar.orbital.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * OA-01 Helios. The film: the mark from over the caller's shoulder, the sky above it, then orbit: twelve mirrors unfold
 * round the lens with the Earth turning below, the sunlight runs in along their struts until the lens burns white, and
 * the lance goes down; back on the ground it comes out of the sky in silence and lands, and the boom follows. Then the
 * caller has it: it follows the crosshair and cuts.
 */
final class HeliosFx {
    private HeliosFx() {}

    static final int GOLD = 0xFFC861, HOT = 0xFFF2D0, WHITE = 0xFFFFFF, EMBER = 0xFF7A20;
    static final int[][] LANDING = {{Impact.WHITEOUT, 2}, {Impact.INK, 1}, {Impact.INVERT, 1}, {Impact.LINES, 1}, {Impact.WHITEOUT, 1},
            {Impact.INK, 1}};
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Random R = new Random();

    // ------------------------------------------------------------------------------------------------ film

    static Film film(Strikes.Active a) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 P = mc.player.position();
        Vec3 T = a.target;
        Vec3 D = new Vec3(T.x - P.x, 0, T.z - P.z);
        D = D.lengthSqr() < 1.0E-3 ? new Vec3(0, 0, 1) : D.normalize();
        Vec3 S = new Vec3(-D.z, 0, D.x);
        Vec3 dir = D, side = S;
        Orbit o = new Orbit(a.seed);
        Vec3 arrayAt = o.over(0.065, 0, 0);
        Vec3 axis = o.mark.scale(-1);
        List<Shot> s = new ArrayList<>();
        int I = a.cannon.impact;

        // 1. over the shoulder: the mark unfolding on the land
        s.add(Shot.cut(0, 32, (k, t) -> pose(P.subtract(dir.scale(3.2 - 0.8 * k)).add(side.scale(1.3)).add(0, 1.8, 0), T.add(0, 2 + 3 * k, 0), 55 - 10 * k)));
        // 2. from beside the mark, up into the sky it will come out of
        s.add(Shot.cut(32, 62, (k, t) -> pose(T.add(side.scale(9)).add(dir.scale(-4)).add(0, 1.6, 0), T.add(side.scale(4)).add(0, 120, 0), 72 - 34 * k)));
        // 3. orbit: the array unfolds, the Earth turning far below
        s.add(Shot.cut(62, 100, (k, t) -> {
            Space.View v = o.view();
            Vec3 local = o.east.scale(6.5 - 1.5 * k).add(o.mark.scale(1.2 + 0.6 * k)).add(o.north.scale(2.2));
            space(v, arrayAt, local, Vec3.ZERO, o.mark, 52);
            v.helios = true;
            v.axis = axis;
            v.unfold = Film.smooth((t - 66) / 30.0F);
            v.fade = Mth.clamp((t - 62) / 6.0F, 0, 1);
            Space.show(v);
            return Orbit.hold();
        }));
        // 4. under the mirrors, the sun behind them: the light runs in, the lens burns
        s.add(Shot.cut(100, 130, (k, t) -> {
            Space.View v = o.view();
            Vec3 local = o.east.scale(2.4 - 0.4 * k).add(o.mark.scale(0.9 + 0.3 * k)).add(o.north.scale(-1.5 + 0.7 * k));
            space(v, arrayAt, local, Vec3.ZERO, o.mark, 64 - 10 * k);
            v.helios = true;
            v.axis = axis;
            v.unfold = 1;
            v.charge = Film.smooth((t - 102) / 24.0F);
            Space.show(v);
            return Orbit.hold();
        }));
        // 5. above the array, down the lance as it fires at the Earth
        s.add(Shot.cut(130, 148, (k, t) -> {
            Space.View v = o.view();
            // from off to one side, so the lance runs down across the frame to the curve of the Earth
            Vec3 local = o.mark.scale(1.6 - 0.4 * k).add(o.east.scale(5.5 - 1.0 * k)).add(o.north.scale(2.0));
            space(v, arrayAt, local, axis.scale(22), o.mark, 62 + 8 * k);
            v.helios = true;
            v.axis = axis;
            v.unfold = 1;
            v.charge = 1;
            v.lance = Mth.clamp((t - 132) / 8.0F, 0, 1);
            v.markGlow = Mth.clamp((t - 138) / 6.0F, 0, 1);
            Space.show(v);
            return Orbit.hold();
        }));
        // 6. wide on the land: the lance comes down out of the sky, silent
        s.add(Shot.cut(148, 164, (k, t) -> pose(T.subtract(dir.scale(80)).add(side.scale(30)).add(0, 22, 0), T.add(0, 40 + 40 * (1 - k), 0), 66)));
        // 7. closer, as the ground goes
        s.add(Shot.cut(164, 188, (k, t) -> pose(T.subtract(dir.scale(42 - 8 * k)).add(side.scale(16)).add(0, 16 + 4 * k, 0), T.add(0, 6, 0), 60 + 8 * k)));
        // 8. back to the caller, the lance in hand
        s.add(Shot.cut(188, a.cannon.film, (k, t) -> pose(P.subtract(dir.scale(5.5 - 1.2 * k)).add(side.scale(2.6)).add(0, 3.6, 0), a.beam(0).add(0, 4, 0), 66)));

        String[][] lines = {{"4", "Uplink established. Channel one."}, {"36", "Helios, wake up."}, {"66", "Open the mirrors."},
                {"104", "Gather the sun."}, {"132", "Fire."}, {"190", "It's yours. Cut."}};
        Film.Card card = new Film.Card("ORBITAL ARSENAL", "陽光の槍", "OA-01  HELIOS", Cannon.HELIOS.color, 64, 50);
        return new Film(partial -> Strikes.time(a, partial), s, a.cannon.film, card, lines, 8, 12);
    }

    static CameraPose pose(Vec3 pos, Vec3 look, double fov) {
        return new CameraPose(pos, look, (float) fov, 0);
    }

    /** Place a space camera: {@code local} and {@code at} in kilometres round the array at {@code arrayAt}. */
    static void space(Space.View v, Vec3 arrayAt, Vec3 local, Vec3 at, Vec3 up, double fov) {
        v.localEye = local;
        v.eye = arrayAt.add(local.scale(1 / 6371.0));
        v.forward = at.subtract(local).normalize();
        v.up = up;
        v.fov = (float) fov;
    }

    // ------------------------------------------------------------------------------------------------ the world

    static void tick(Strikes.Active a, int t) {
        Cannon c = a.cannon;
        Vec3 T = a.target;
        if (a.mine) {
            if (t == 2) Strikes.sound(a, T, ModSounds.COUNTDOWN.get(), 1.0F, 1.0F);
            if (t == 52) Strikes.sound(a, T, ModSounds.ASCENT.get(), 1.0F, 1.0F);
            if (t == 64) Strikes.sound(a, T, ModSounds.HELIOS_UNFOLD.get(), 1.0F, 1.0F);
            if (t == 100) Strikes.sound(a, T, ModSounds.HELIOS_CHARGE.get(), 1.0F, 1.0F);
            if (t == 131) Strikes.sound(a, T, ModSounds.HELIOS_FIRE.get(), 1.0F, 1.0F);
        }
        if (t == c.impact - 18) Strikes.sound(a, T, ModSounds.HELIOS_FIRE.get(), 0.7F, 0.8F);
        if (t == c.impact) Strikes.sound(a, T, ModSounds.HELIOS_IMPACT.get(), 1.0F, 1.0F);
        if (t == c.impact + Math.max(6, Strikes.soundDelay(T))) Strikes.sound(a, T, ModSounds.BOOM.get(), 1.0F, 0.9F);
        if (t > c.impact && t < c.hold && (t - c.impact) % 76 == 1) Strikes.sound(a, a.beam, ModSounds.HELIOS_BEAM.get(), 0.9F, 1.0F);

        // a star that should not be there, high over the mark
        if (t > 90 && t < c.impact) {
            float k = (t - 90) / (float) (c.impact - 90);
            Light.flare(T.add(0, 260, 0), 3 + 10 * k * k, GOLD, 0.12F);
        }
        if (t == c.impact) {
            Light.ring(T.add(0, 0.4, 0), UP, a.radius, a.radius * 16, a.radius * 0.6F, HOT, 1.1F);
            Light.ring(T.add(0, 0.6, 0), UP, a.radius * 0.5F, a.radius * 9, a.radius * 0.3F, GOLD, 1.6F);
            Light.burst(T.add(0, 1, 0), 90, 2.2F, GOLD, 1.6F, 0.35F);
            for (int i = 0; i < 40; i++) {
                double ang = i / 40.0 * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Smoke.puff(T.add(out.scale(a.radius * 1.5)).add(0, 1, 0), out.scale(1.2).add(0, 0.25, 0), a.radius * 0.8F, a.radius * 2.6F, 3.5F,
                        0x3A2E26, 0.55F, 0.9F, 0.05F);
            }
        }
        if (t > c.impact && t < c.hold + 20) {
            Vec3 b = a.beam;
            float r = a.radius;
            if (t % 5 == 0) {
                Light.ring(b.add(0, 0.5, 0), UP, r, r * 4.5F, r * 0.25F, GOLD, 0.6F);
            }
            for (int i = 0; i < 6; i++) {
                double ang = R.nextDouble() * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Light.spark(b.add(out.scale(r)).add(0, 0.5, 0), out.scale(0.4 + R.nextDouble() * 0.6).add(0, 0.6 + R.nextDouble() * 0.9, 0), 0.9F,
                        R.nextBoolean() ? GOLD : EMBER, 0.12F);
            }
            if (t % 2 == 0) {
                double ang = R.nextDouble() * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Smoke.puff(b.add(out.scale(r * 1.1)).add(0, 1, 0), out.scale(0.2).add(0, 0.35, 0), r * 0.6F, r * 2.0F, 2.6F,
                        R.nextBoolean() ? 0xD8D0C8 : 0x2C2420, 0.35F, 0.95F, 0.12F);
            }
        }
    }

    static void frame(Strikes.Active a, float t, float partial) {
    }

    static void ribbons(List<Ribbon> out, Strikes.Active a, float t, float partial) {
        Cannon c = a.cannon;
        Vec3 T = a.target;
        float r = a.radius;
        // the mark: rings of gold drawing in on the ground, ticking round
        if (t < c.impact + 4) {
            float on = ScreenFx.window(t, 0, 10, c.impact - 2, c.impact + 4);
            float pulse = 0.75F + 0.25F * Mth.sin(t * 0.5F);
            Vec3 g = T.add(0, 0.15, 0);
            float k = Mth.clamp(t / c.impact, 0, 1);
            Shapes.ring(out, g, UP, r * (4.0 - 2.4 * k), 0.35F, on * pulse, GOLD, Ribbon.Style.GLOW);
            Shapes.ring(out, g, UP, r, 0.2F, on, HOT, Ribbon.Style.GLOW);
            for (int i = 0; i < 4; i++) {
                double ang = Math.toRadians(i * 90 + t * 2.0);
                Vec3 u = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Shapes.streak(out, g.add(u.scale(r * 1.4)), g.add(u.scale(r * (4.6 - 2.4 * k))), 0.35F, on, GOLD, Ribbon.Style.GLOW);
            }
        }
        // the lance
        float head;
        if (t < c.impact - 12) {
            return;
        } else if (t < c.impact) {
            head = (float) Mth.lerp((t - (c.impact - 12)) / 12.0, 420, 0);
        } else {
            head = 0;
        }
        float width = t < c.hold ? 1 : Math.max(0, 1 - (t - c.hold) / (float) (c.end - c.hold));
        if (width <= 0) {
            return;
        }
        Vec3 b = t < c.impact ? T : a.beam(partial);
        float flick = 0.92F + 0.08F * Mth.sin(t * 3.7F) * Mth.sin(t * 1.3F);
        Vec3 top = b.add(0, 440, 0), bottom = b.add(0, head, 0);
        column(out, top, bottom, r * 5.0F * width, 0.16F, GOLD);
        column(out, top, bottom, r * 2.4F * width * flick, 0.55F, GOLD);
        column(out, top, bottom, r * 1.3F * width * flick, 1.0F, HOT);
        column(out, top, bottom, r * 0.55F * width, 1.0F, WHITE);
        if (t >= c.impact) {
            Shapes.ring(out, b.add(0, 0.3, 0), UP, r * 1.05, r * 0.5F * width, 0.9F * width, HOT, Ribbon.Style.GLOW);
        }
    }

    /** A straight, even, camera-facing column. */
    static void column(List<Ribbon> out, Vec3 a, Vec3 b, float width, float alpha, int rgb) {
        int n = 6;
        Vec3[] spine = new Vec3[n];
        float[] w = new float[n];
        float[] al = new float[n];
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1);
            spine[i] = a.lerp(b, k);
            w[i] = width;
            al[i] = alpha * (i == 0 ? 0.0F : 1.0F);
        }
        out.add(new Ribbon(spine, null, w, al, rgb, Ribbon.Style.GLOW));
    }

    static void screen(ScreenFx fx, Strikes.Active a, float t, double dist, boolean film) {
        Cannon c = a.cannon;
        int I = c.impact;
        if (film) {
            if (t > 62 && t < 148) {
                fx.grain(0.35F);
                fx.vignette(0x000000, 0.35F);
                fx.bloom(0.6F);
            }
            if (t > 100 && t < 148) {
                fx.aberration(0.2F * ScreenFx.window(t, 110, 128, 140, 148));
            }
            if (t > 128 && t < 136) {
                fx.flash(HOT, ScreenFx.hit(t, 131, 3.0F) * 1.2F);
            }
            if (t >= I - 12 && t < I) {
                // the hush before the hit: the world drains of colour
                fx.desaturate(0.5F * (t - (I - 12)) / 12.0F);
            }
            if (t >= I && t < I + 30) {
                fx.impact(LANDING, t - I, a.target.add(0, 2, 0), GOLD);
                fx.shake(1.2F * ScreenFx.hit(t, I, 14.0F));
                fx.flash(HOT, ScreenFx.hit(t, I, 4.0F) * 1.3F);
                fx.punch(0.9F * ScreenFx.hit(t, I, 6.0F));
                fx.aberration(0.8F * ScreenFx.hit(t, I, 10.0F));
            }
            if (t > I + 20 && t < c.film) {
                fx.bloom(0.5F);
            }
            return;
        }
        // everyone else: felt from where they stand
        float near = (float) Mth.clamp(1.0 - dist / 320.0, 0, 1);
        if (near <= 0) {
            return;
        }
        if (t >= I && t < I + 30) {
            if (dist < 140) {
                fx.impact(Impact.LIGHT, t - I, a.target.add(0, 2, 0), GOLD);
            }
            fx.flash(HOT, ScreenFx.hit(t, I, 5.0F) * near);
        }
        int boom = I + Math.max(6, (int) (dist / 17.0));
        if (t >= boom && t < boom + 30) {
            fx.shake(1.1F * near * ScreenFx.hit(t, boom, 14.0F));
        }
        if (t > I && t < c.hold) {
            Minecraft mc = Minecraft.getInstance();
            double d = mc.player == null ? 999 : mc.player.position().distanceTo(a.beam);
            float heat = (float) Mth.clamp(1.0 - d / 70.0, 0, 1);
            fx.shake(0.18F * heat);
            fx.aberration(0.25F * heat);
            fx.bloom(0.4F * near);
        }
    }
}
