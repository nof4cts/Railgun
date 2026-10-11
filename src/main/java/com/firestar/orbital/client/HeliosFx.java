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
import com.firestar.orbital.client.render.WorldFx;
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
 * OA-01 Helios. The film, twenty seconds: the mark from over the caller's shoulder; the grass at the mark and the sky
 * above it; up into the blue until it goes black. Orbit: the Earth's limb and the sun rising over it, the folded array a
 * glint against the dark; round it as it begins to open; a single mirror swinging out with the sun sliding across its
 * facets; under the ring as the light runs in along the struts; the lens burning white. It fires. Down the lance through
 * the top of the sky, the clouds, to the point of fire on the ground. On the land: silence, the lance arriving out of the
 * sky, the ground going, the column standing over the molten earth. Then the caller has it.
 */
final class HeliosFx {
    private HeliosFx() {}

    static final int GOLD = 0xFFC861, HOT = 0xFFF2D0, WHITE = 0xFFFFFF, EMBER = 0xFF7A20, LASER = 0xFF5A2A;
    static final int[][] LANDING = {{Impact.WHITEOUT, 2}, {Impact.INK, 1}, {Impact.INVERT, 1}, {Impact.LINES, 1}, {Impact.WHITEOUT, 1},
            {Impact.INK, 1}, {Impact.HALFTONE, 1}};
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Random R = new Random();

    // ------------------------------------------------------------------------------------------------ film

    static Film film(Strikes.Active a) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 P = mc.player.position();
        Vec3 T = a.target;
        Vec3 D0 = new Vec3(T.x - P.x, 0, T.z - P.z);
        Vec3 dir = D0.lengthSqr() < 1.0E-3 ? new Vec3(0, 0, 1) : D0.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        float r = a.radius;
        Orbit o = new Orbit(a.seed);
        Vec3 arrayAt = o.over(0.065, 0, 0);
        Vec3 axis = o.mark.scale(-1);
        int I = a.cannon.impact;
        List<Shot> s = new ArrayList<>();

        // 1. over the shoulder: the mark forming on the land, the laser coming down onto it
        s.add(Shot.cut(0, 40, (k, t) -> pose(P.subtract(dir.scale(3.4 - 1.0 * k)).add(side.scale(1.3)).add(0, 1.8, 0), T.add(0, 2 + 4 * k, 0), 56 - 10 * k)));
        // 2. down in the grass at the mark, looking up past it at the sky
        s.add(Shot.cut(40, 70, (k, t) -> pose(T.add(side.scale(8 + 2 * k)).subtract(dir.scale(5)).add(0, 0.5, 0),
                T.add(dir.scale(16)).add(0, 140, 0), 72 - 10 * k)));
        // 3. straight up into the blue, closing in on a star that should not be there
        s.add(Shot.cut(70, 98, (k, t) -> pose(T.add(side.scale(4)).add(0, 1.6, 0), T.add(side.scale(4.2)).add(0, 300, 0.4), 70 - 52 * k * k)));
        // 4. orbit: the limb of the Earth, the sun coming over it, the folded array a glint in the dark
        s.add(Shot.cut(98, 140, (k, t) -> {
            Space.View v = o.view();
            Vec3 local = o.east.scale(46 - 10 * k).add(o.mark.scale(6 + 2 * k)).add(o.north.scale(16));
            space(v, arrayAt, local, o.mark.scale(-3), o.mark, 42);
            helios(v, axis, 0, 0, 0);
            v.fade = Mth.clamp((t - 98) / 8.0F, 0, 1);
            Space.show(v);
            return Orbit.hold();
        }));
        // 5. round the array as it begins to open
        s.add(Shot.cut(140, 180, (k, t) -> {
            Space.View v = o.view();
            double ang = Math.toRadians(-40 + 70 * k);
            Vec3 round = o.east.scale(Math.cos(ang)).add(o.north.scale(Math.sin(ang)));
            Vec3 local = round.scale(8.5 - 2 * k).add(o.mark.scale(1.8));
            space(v, arrayAt, local, Vec3.ZERO, o.mark, 46);
            helios(v, axis, Film.smooth((t - 146) / 34.0F) * 0.45F, 0, 0);
            Space.show(v);
            return Orbit.hold();
        }));
        // 6. one mirror swinging out, the sun sliding across its facets
        s.add(Shot.cut(180, 215, (k, t) -> {
            Space.View v = o.view();
            Vec3 local = o.east.scale(3.4 - 0.5 * k).add(o.mark.scale(0.7 + 0.2 * k)).add(o.north.scale(1.1));
            space(v, arrayAt, local, o.east.scale(1.6).add(o.north.scale(-0.3)), o.mark, 44 - 6 * k);
            helios(v, axis, 0.45F + 0.55F * Film.smooth((t - 180) / 32.0F), 0, 0);
            Space.show(v);
            return Orbit.hold();
        }));
        // 7. under the ring: the light runs in along the struts
        s.add(Shot.cut(215, 250, (k, t) -> {
            Space.View v = o.view();
            Vec3 local = o.east.scale(2.6 - 0.4 * k).add(o.mark.scale(1.0 + 0.3 * k)).add(o.north.scale(-1.6 + 0.6 * k));
            space(v, arrayAt, local, Vec3.ZERO, o.mark, 62 - 8 * k);
            helios(v, axis, 1, 0.8F * Film.smooth((t - 217) / 30.0F), 0);
            Space.show(v);
            return Orbit.hold();
        }));
        // 8. the lens, burning white
        s.add(Shot.cut(250, 268, (k, t) -> {
            Space.View v = o.view();
            Vec3 local = o.mark.scale(1.1 - 0.2 * k).add(o.east.scale(0.8 - 0.2 * k)).add(o.north.scale(0.35));
            space(v, arrayAt, local, Vec3.ZERO, o.north, 38 - 8 * k);
            helios(v, axis, 1, 0.8F + 0.2F * k, 0);
            Space.show(v);
            return Orbit.hold();
        }));
        // 9. it fires: from off to one side, the lance runs down across the frame to the curve of the Earth
        s.add(Shot.cut(268, 290, (k, t) -> {
            Space.View v = o.view();
            Vec3 local = o.mark.scale(1.8 - 0.4 * k).add(o.east.scale(5.8 - 1.0 * k)).add(o.north.scale(2.0));
            space(v, arrayAt, local, axis.scale(22), o.mark, 62 + 10 * k);
            helios(v, axis, 1, 1, Mth.clamp((t - 270) / 8.0F, 0, 1));
            lance(v, arrayAt, o, Mth.clamp((t - 272) / 10.0F, 0, 1));
            v.markGlow = Mth.clamp((t - 282) / 8.0F, 0, 1);
            Space.show(v);
            return Orbit.hold();
        }));
        // 10. down with it: through the top of the sky, the clouds, to the point of fire on the ground
        s.add(Shot.cut(290, 316, (k, t) -> {
            Space.View v = o.view();
            double alt = Mth.lerp(Math.pow(k, 0.7), 0.03, 0.0035);
            Vec3 eye = o.mark.scale(1 + alt).add(o.east.scale(alt * 0.6)).add(o.north.scale(alt * 0.25));
            v.look(eye, o.mark.scale(1.0).add(o.east.scale(-alt * 0.15)), o.north);
            v.fov = 64 + 6 * k;
            lance(v, arrayAt, o, 1);
            v.markGlow = 1;
            Space.show(v);
            return Orbit.hold();
        }));
        // 11. the land, from far off and high: silence, and the lance coming out of the sky
        s.add(Shot.cut(316, I, (k, t) -> pose(T.subtract(dir.scale(110)).add(side.scale(40)).add(0, 34, 0), T.add(0, 60 + 70 * (1 - k), 0), 64)));
        // 12. the ground goes
        s.add(Shot.cut(I, I + 22, (k, t) -> pose(T.subtract(dir.scale(64 - 10 * k)).add(side.scale(24)).add(0, 22 + 6 * k, 0), T.add(0, 10, 0), 60 + 8 * k)));
        // 13. low, close: the column standing over the molten earth, the air shaking round it
        s.add(Shot.cut(I + 22, I + 42, (k, t) -> pose(T.add(side.scale(r * 4.5)).subtract(dir.scale(r * 3.5 - k * r)).add(0, 1.2, 0),
                T.add(0, 30 + 40 * k, 0), 82)));
        // 14. back to the caller, the lance in hand
        s.add(Shot.cut(I + 42, a.cannon.film, (k, t) -> pose(P.subtract(dir.scale(5.5 - 1.2 * k)).add(side.scale(2.6)).add(0, 3.6, 0),
                a.beam(0).add(0, 4, 0), 66)));

        String[][] lines = {{"6", "Uplink established. Channel one."}, {"44", "Target confirmed."}, {"100", "Helios, wake up."},
                {"146", "Open the mirrors."}, {"220", "Gather the sun."}, {"254", "Focus..."}, {"270", "Fire."}, {"320", "..."},
                {"376", "It's yours. Cut."}};
        Film.Card card = new Film.Card("ORBITAL ARSENAL", "陽光の槍", "OA-01  HELIOS", Cannon.HELIOS.color, 104, 60);
        return new Film(partial -> Strikes.time(a, partial), s, a.cannon.film, card, lines, 8, 14);
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

    static void helios(Space.View v, Vec3 axis, float unfold, float charge, float lance) {
        v.helios = true;
        v.axis = axis;
        v.unfold = unfold;
        v.charge = charge;
        v.lance = lance;
    }

    /** The lance in Earth radii, reaching from the array toward the ground ({@code reach} 0..1). */
    static void lance(Space.View v, Vec3 arrayAt, Orbit o, float reach) {
        if (reach <= 0) {
            return;
        }
        v.lanceFrom = arrayAt;
        v.lanceTo = arrayAt.lerp(o.mark, reach);
        v.lanceGlow = 1;
    }

    // ------------------------------------------------------------------------------------------------ the world

    static void tick(Strikes.Active a, int t) {
        Cannon c = a.cannon;
        Vec3 T = a.target;
        int I = c.impact;
        if (a.mine) {
            if (t == 2) Strikes.sound(a, T, ModSounds.COUNTDOWN.get(), 1.0F, 1.0F);
            if (t == 74) Strikes.sound(a, T, ModSounds.ASCENT.get(), 1.0F, 1.0F);
            if (t == 142) Strikes.sound(a, T, ModSounds.HELIOS_UNFOLD.get(), 1.0F, 0.9F);
            if (t == 182) Strikes.sound(a, T, ModSounds.HELIOS_UNFOLD.get(), 0.8F, 1.1F);
            if (t == 216) Strikes.sound(a, T, ModSounds.HELIOS_CHARGE.get(), 1.0F, 0.85F);
            if (t == 250) Strikes.sound(a, T, ModSounds.HELIOS_CHARGE.get(), 1.0F, 1.15F);
            if (t == 270) Strikes.sound(a, T, ModSounds.HELIOS_FIRE.get(), 1.0F, 1.0F);
            if (t == 292) Strikes.sound(a, T, ModSounds.ASCENT.get(), 0.7F, 1.4F);
        }
        if (t == I - 16) Strikes.sound(a, T, ModSounds.HELIOS_FIRE.get(), 0.7F, 0.8F);
        if (t == I) Strikes.sound(a, T, ModSounds.HELIOS_IMPACT.get(), 1.0F, 1.0F);
        if (t == I + Math.max(6, Strikes.soundDelay(T))) Strikes.sound(a, T, ModSounds.BOOM.get(), 1.0F, 0.9F);
        if (t > I && t < c.hold && (t - I) % 76 == 1) Strikes.sound(a, a.beam, ModSounds.HELIOS_BEAM.get(), 0.9F, 1.0F);

        // a star that should not be there, high over the mark
        if (t > 60 && t < I - 14) {
            float k = (t - 60) / (float) (I - 74);
            Light.flare(T.add(0, 300, 0), 3 + 12 * k * k, GOLD, 0.12F);
        }
        float r = a.radius;
        if (t == I) {
            Light.burst(T.add(0, 1, 0), 140, 2.6F, GOLD, 1.8F, 0.3F);
            Light.burst(T.add(0, 1, 0), 60, 4.0F, HOT, 1.0F, 0.2F);
            for (int i = 0; i < 36; i++) {
                double ang = i / 36.0 * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Smoke.puff(T.add(out.scale(r * 1.5)).add(0, 1, 0), out.scale(1.6).add(0, 0.3, 0), r * 0.6F, r * 2.4F, 3.5F, 0x4A3A30, 0.45F, 0.9F,
                        0.05F);
            }
        }
        if (t > I && t < c.hold + 20) {
            Vec3 b = a.beam;
            for (int i = 0; i < 8; i++) {
                double ang = R.nextDouble() * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Light.spark(b.add(out.scale(r * (0.8 + R.nextDouble() * 0.5))).add(0, 0.5, 0),
                        out.scale(0.3 + R.nextDouble() * 0.8).add(0, 0.7 + R.nextDouble() * 1.2, 0), 1.1F, R.nextBoolean() ? GOLD : EMBER, 0.09F);
            }
            if (t % 2 == 0) {
                double ang = R.nextDouble() * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Smoke.puff(b.add(out.scale(r * 1.2)).add(0, 1, 0), out.scale(0.25).add(0, 0.45, 0), r * 0.5F, r * 2.2F, 3.0F,
                        R.nextInt(3) == 0 ? 0xE8E0D8 : 0x2C2420, 0.3F, 0.95F, 0.14F);
            }
        }
    }

    static void frame(Strikes.Active a, float t, float partial) {
    }

    /** What the world shader draws for this strike this frame. */
    static void world(WorldFx.State st, Strikes.Active a, float t, float partial) {
        Cannon c = a.cannon;
        int I = c.impact;
        float r = a.radius;
        if (t >= I - 14 && t < c.end) {
            Vec3 b = t < I ? a.target : a.beam(partial);
            float top = (float) a.target.y + 700;
            st.beam = b;
            st.beamRadius = r;
            st.beamTop = top;
            st.beamHead = t < I ? (float) Mth.lerp(Math.pow((t - (I - 14)) / 14.0, 1.6), top, b.y) : (float) b.y - 4;
            float fade = t < c.hold ? 1 : 1 - (t - c.hold) / (float) (c.end - c.hold);
            st.beamIntensity = Math.max(0, fade) * (0.92F + 0.08F * Mth.sin(t * 2.3F));
            st.molten = Mth.clamp((t - I) / 10.0F, 0, 1) * Math.max(0.3F, fade);
        }
        if (t >= I && t < I + 44) {
            float k = (t - I) / 44.0F;
            st.shock = a.target;
            st.shockRadius = r * 40 * (1 - (1 - k) * (1 - k));
            st.shockThickness = r * 1.2F;
            st.shockStrength = (1 - k) * 1.2F;
            st.shockColour = HOT;
        }
    }

    static void ribbons(List<Ribbon> out, Strikes.Active a, float t, float partial) {
        Cannon c = a.cannon;
        Vec3 T = a.target;
        float r = a.radius;
        int I = c.impact;
        if (t < I) {
            // the designator: a thin laser from the sky onto the mark, and a fine ring tightening round it
            float on = ScreenFx.window(t, 0, 12, I - 16, I - 4);
            float k = Mth.clamp(t / I, 0, 1);
            Vec3 g = T.add(0, 0.12, 0);
            HeliosFx.column(out, T.add(0, 320, 0), g, 0.18F, 0.9F * on, LASER);
            HeliosFx.column(out, T.add(0, 320, 0), g, 0.7F, 0.25F * on, LASER);
            Shapes.ring(out, g, UP, r * (3.0 - 1.8 * k), 0.12F, on * 0.8F, GOLD, Ribbon.Style.GLOW);
            Shapes.ring(out, g, UP, r * 0.35, 0.1F, on, HOT, Ribbon.Style.GLOW);
        }
        if (!com.firestar.orbital.client.render.WorldFx.available() && t >= I - 12 && t < c.end) {
            // without the world shader: a plain column, so the strike still reads
            Vec3 b = t < I ? T : a.beam(partial);
            float head = t < I ? (float) Mth.lerp((t - (I - 12)) / 12.0, 420, 0) : 0;
            column(out, b.add(0, 440, 0), b.add(0, head, 0), r * 2.4F, 0.55F, GOLD);
            column(out, b.add(0, 440, 0), b.add(0, head, 0), r * 0.9F, 1.0F, HOT);
        }
    }

    /** A straight, even, camera-facing column. */
    static void column(List<Ribbon> out, Vec3 a, Vec3 b, float width, float alpha, int rgb) {
        if (alpha <= 0) {
            return;
        }
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
            if (t > 86 && t < 100) {
                fx.flash(0xFFFFFF, ScreenFx.window(t, 88, 97, 98, 102) * 1.1F);
            }
            if (t > 98 && t < 316) {
                fx.grain(0.3F);
                fx.vignette(0x000000, 0.3F);
                fx.bloom(0.7F);
            }
            if (t > 215 && t < 270) {
                fx.aberration(0.25F * ScreenFx.window(t, 230, 262, 266, 270));
            }
            if (t > 262 && t < 278) {
                fx.flash(HOT, ScreenFx.hit(t, 268, 3.0F) * 0.8F);
                fx.shake(0.8F * ScreenFx.hit(t, 270, 10.0F));
                fx.punch(0.6F * ScreenFx.hit(t, 270, 5.0F));
            }
            if (t > 290 && t < 316) {
                fx.shake(0.3F);
                fx.aberration(0.4F);
            }
            if (t >= 316 && t < I) {
                fx.desaturate(0.7F * (t - 316) / (I - 316.0F));
                fx.contrast(0.3F);
            }
            if (t >= I && t < I + 36) {
                fx.impact(LANDING, t - I, a.target.add(0, 2, 0), GOLD);
                fx.shake(1.3F * ScreenFx.hit(t, I, 16.0F));
                fx.flash(HOT, ScreenFx.hit(t, I, 4.0F) * 1.4F);
                fx.punch(1.0F * ScreenFx.hit(t, I, 6.0F));
                fx.aberration(0.9F * ScreenFx.hit(t, I, 12.0F));
            }
            if (t > I + 20 && t < c.film) {
                fx.bloom(0.6F);
                fx.shake(0.2F);
            }
            return;
        }
        float near = (float) Mth.clamp(1.0 - dist / 360.0, 0, 1);
        if (near <= 0) {
            return;
        }
        if (t >= I && t < I + 30) {
            if (dist < 160) {
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
            fx.bloom(0.5F * near);
        }
    }
}
