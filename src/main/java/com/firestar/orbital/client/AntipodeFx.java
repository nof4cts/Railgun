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
 * OA-02 Antipode. The film, twenty-two seconds: the sky over the mark cracks, and from the ground the web of light spreads
 * across it as the air turns red. Up through it, into orbit: beside the Earth, space tears; a breach opens round a vortex
 * that bends the starlight, and something pushes through it: another Earth, crimson-seaed and dark. It drifts in until the
 * two skies touch in lightning. On the ground the other world's land fills the sky, upside down, coming down; its peaks
 * reach for ours; they touch. The land there is theirs now.
 */
final class AntipodeFx {
    private AntipodeFx() {}

    static final int CRIMSON = 0xD0306A, MAGENTA = 0xFF4FB0, PALE = 0xFFD8EC, VIOLET = 0x8A3CFF;
    static final int[][] CONTACT = {{Impact.INVERT, 1}, {Impact.NEGATIVE, 2}, {Impact.HALFTONE, 1}, {Impact.CRIMSON, 1}, {Impact.INVERT, 1},
            {Impact.SLASHED, 1}, {Impact.NEGATIVE, 1}, {Impact.INK, 1}};
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Random R = new Random();

    // ------------------------------------------------------------------------------------------------ film

    static Film film(Strikes.Active a) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 P = mc.player.position();
        Vec3 T = a.target;
        Vec3 D0 = new Vec3(T.x - P.x, 0, T.z - P.z);
        Vec3 D = D0.lengthSqr() < 1.0E-3 ? new Vec3(0, 0, 1) : D0.normalize();
        Vec3 S = new Vec3(-D.z, 0, D.x);
        float R0 = a.radius;
        Orbit o = new Orbit(a.seed);
        Vec3 M = o.mark;
        Vec3 breach = M.scale(1.9).add(o.east.scale(2.7));
        Vec3 contactAt = M.scale(2.03);
        Vec3 bn = contactAt.subtract(breach).normalize();
        int I = a.cannon.impact;
        List<Shot> s = new ArrayList<>();

        // 1. over the shoulder, up at the sky over the mark as the first crack runs across it
        s.add(Shot.cut(0, 40, (k, t) -> pose(P.subtract(D.scale(3.0 - 0.8 * k)).add(S.scale(1.2)).add(0, 1.6, 0), T.add(0, 70 + 90 * k, 0), 62 - 8 * k)));
        // 2. on the ground at the mark, straight up into the web of light as it spreads and the air turns
        s.add(Shot.cut(40, 78, (k, t) -> {
            double ang = Math.toRadians(20 * k);
            Vec3 look = T.add(S.scale(Math.sin(ang) * 30)).add(0, 260, 0).add(D.scale(10));
            return pose(T.add(S.scale(3)).add(0, 1.2, 0), look, 84 - 16 * k);
        }));
        // 3. up through it
        s.add(Shot.cut(78, 100, (k, t) -> pose(T.add(S.scale(6)).add(0, 4 + 240 * k * k, 0), T.add(0, 400, 0), 70 + 20 * k)));
        // 4. orbit: beside the Earth, space tears
        s.add(Shot.cut(100, 142, (k, t) -> {
            Space.View v = o.view();
            v.fade = Mth.clamp((t - 100) / 8.0F, 0, 1);
            Vec3 eye = M.scale(1.8).add(o.east.scale(1.2 + 0.4 * k)).subtract(o.north.scale(5.2 - 0.6 * k));
            v.look(eye, M.scale(1.4).add(o.east.scale(1.5)), M);
            v.fov = 50;
            v.breach = breach;
            v.breachNormal = bn;
            float open = Film.smooth((t - 106) / 30.0F);
            v.breachRadius = 1.2F * open;
            v.breachOpen = open;
            Space.show(v);
            return Orbit.hold();
        }));
        // 5. close on the breach: the vortex, and a limb of another world pushing through it
        s.add(Shot.cut(142, 186, (k, t) -> {
            Space.View v = o.view();
            Vec3 eye = breach.add(bn.scale(2.6 - 0.6 * k)).add(o.north.scale(-1.2)).add(M.scale(0.4));
            v.look(eye, breach, M);
            v.fov = 58;
            v.breach = breach;
            v.breachNormal = bn;
            v.breachRadius = 1.2F;
            v.breachOpen = 1;
            v.earth2 = breach.subtract(bn.scale(1.5)).lerp(breach.subtract(bn.scale(0.3)), Film.smooth((t - 150) / 36.0F));
            v.earth2Radius = 1;
            Space.show(v);
            return Orbit.hold();
        }));
        // 6. wide: it is out, a second Earth beside ours
        s.add(Shot.cut(186, 226, (k, t) -> {
            Space.View v = o.view();
            Vec3 eye = M.scale(1.9).add(o.east.scale(1.0)).subtract(o.north.scale(6.0 - 0.8 * k));
            v.look(eye, M.scale(1.3).add(o.east.scale(1.6)), M);
            v.fov = 52;
            v.breach = breach;
            v.breachNormal = bn;
            v.breachRadius = 1.2F;
            v.breachOpen = 1 - 0.5F * k;
            v.earth2 = breach.subtract(bn.scale(0.3)).lerp(breach.add(bn.scale(0.6)), Film.smooth(k));
            v.earth2Radius = 1;
            Space.show(v);
            return Orbit.hold();
        }));
        // 7. between them as they drift together; the skies touch
        s.add(Shot.cut(226, 266, (k, t) -> {
            Space.View v = o.view();
            Vec3 eye = M.scale(1.55).add(o.east.scale(-0.6 + 0.6 * k)).subtract(o.north.scale(2.3 - 0.4 * k));
            v.look(eye, M.scale(1.5), M);
            v.fov = 60;
            v.breach = breach;
            v.breachNormal = bn;
            v.breachRadius = 1.2F;
            v.breachOpen = 0.5F - 0.4F * k;
            v.earth2 = breach.add(bn.scale(0.6)).lerp(contactAt, Film.smooth(k));
            v.earth2Radius = 1;
            v.contact = Film.smooth((t - 246) / 18.0F);
            v.markGlow = 0.5F * v.contact;
            Space.show(v);
            return Orbit.hold();
        }));
        // 8. on the seam where they touch: lightning the size of continents
        s.add(Shot.cut(266, 290, (k, t) -> {
            Space.View v = o.view();
            Vec3 eye = M.scale(1.08 + 0.02 * k).add(o.east.scale(0.55 - 0.15 * k)).subtract(o.north.scale(0.5));
            v.look(eye, M.scale(1.015), M);
            v.fov = 66;
            v.earth2 = contactAt;
            v.earth2Radius = 1;
            v.contact = 1;
            v.markGlow = 0.6F;
            Space.show(v);
            return Orbit.hold();
        }));
        // 9. on the ground, looking up: their land filling the sky upside down, coming down
        s.add(Shot.cut(290, 322, (k, t) -> pose(T.add(S.scale(R0 * 0.4)).add(D.scale(-R0 * 0.3)).add(0, 1.8, 0),
                T.add(D.scale(R0 * 0.5)).add(0, 120 - 40 * k, 0), 84 - 6 * k)));
        // 10. from far off: the land descending onto the mark
        s.add(Shot.cut(322, 344, (k, t) -> pose(T.subtract(D.scale(R0 * 4.2)).add(S.scale(R0 * 1.4)).add(0, R0 * 0.35 + 4, 0),
                T.add(0, R0 * 0.9, 0), 70)));
        // 11. low and close: a peak of their world right over us, reaching down
        s.add(Shot.cut(344, I, (k, t) -> pose(T.add(S.scale(R0 * 0.9)).subtract(D.scale(R0 * 0.6)).add(0, 2, 0), T.add(0, R0 * 0.8, 0), 88)));
        // 12. they touch
        s.add(Shot.cut(I, I + 22, (k, t) -> pose(T.subtract(D.scale(R0 * 2.8)).add(S.scale(R0 * 1.0)).add(0, R0 * 0.5 + 4, 0), T.add(0, R0 * 0.3, 0),
                68 + 6 * k)));
        // 13. from the air, circling: the exchange spreading to the rim, their land lifting away
        s.add(Shot.cut(I + 22, I + 56, (k, t) -> {
            double ang = Math.toRadians(-40 + 80 * k);
            Vec3 round = D.scale(-Math.cos(ang)).add(S.scale(Math.sin(ang)));
            return pose(T.add(round.scale(R0 * 2.4)).add(0, R0 * 1.6, 0), T, 62);
        }));
        // 14. back to the caller
        s.add(Shot.cut(I + 56, a.cannon.film, (k, t) -> pose(P.subtract(D.scale(5.0 - 1.0 * k)).add(S.scale(2.0)).add(0, 3.2, 0), T.add(0, 6, 0), 68)));

        String[][] lines = {{"6", "Uplink established. Channel two."}, {"44", "Look at the sky."}, {"104", "Antipode. Open the breach."},
                {"150", "Something's coming through."}, {"190", "...Another Earth."}, {"230", "Bring it closer."}, {"294", "Look up."},
                {"370", "Their land, for ours."}};
        Film.Card card = new Film.Card("ORBITAL ARSENAL", "対蹠の世界", "OA-02  ANTIPODE", Cannon.ANTIPODE.color, 106, 60);
        return new Film(partial -> Strikes.time(a, partial), s, a.cannon.film, card, lines, 8, 14);
    }

    static CameraPose pose(Vec3 pos, Vec3 look, double fov) {
        return new CameraPose(pos, look, (float) fov, 0);
    }

    // ------------------------------------------------------------------------------------------------ the world

    /** The hanging land's base above the mark at time t (its deepest point hangs 0.9 zone radii below that). */
    static float base(Strikes.Active a, float t) {
        int I = a.cannon.impact;
        float dmax = a.radius * 0.9F;
        if (t < I) {
            float k = Mth.clamp((t - 280) / (I - 280.0F), 0, 1);
            return Mth.lerp((float) Math.pow(k, 1.8), 320, dmax);
        }
        float k = (t - I) / 80.0F;
        return dmax + 380 * k * k;
    }

    static void world(WorldFx.State st, Strikes.Active a, float t, float partial) {
        Cannon c = a.cannon;
        int I = c.impact;
        if (t < 2 || t > c.end + 40) {
            return;
        }
        st.mirror = a.target;
        st.mirrorRadius = a.radius;
        st.mirrorBase = base(a, t);
        st.mirrorAlpha = Mth.clamp((t - 280) / 16.0F, 0, 1) * Mth.clamp(1 - (t - I - 30) / 50.0F, 0, 1);
        st.mirrorFlash = t >= I ? Math.max(0, 0.9F - (t - I) / 8.0F) : 0;
        st.crackGrowth = Film.smooth((t - 4) / 96.0F);
        st.crackAlpha = Mth.clamp(1 - (t - I) / 60.0F, 0, 1);
        st.crackHeight = 280;
        st.crackSeed = (a.seed & 255) * 0.37F;
        st.crimson = Math.max(st.crimson, ScreenFx.window(t, 20, 120, c.hold, c.end + 30));
        st.lightning = Math.max(st.lightning, ScreenFx.window(t, 296, 316, I + 4, I + 14));
        if (t >= I && t < I + 50) {
            float k = (t - I) / 50.0F;
            st.shock = a.target;
            st.shockRadius = a.radius * (0.2F + 3.5F * (1 - (1 - k) * (1 - k)));
            st.shockThickness = a.radius * 0.25F;
            st.shockStrength = (1 - k) * 1.3F;
            st.shockColour = MAGENTA;
        }
    }

    static void tick(Strikes.Active a, int t) {
        Cannon c = a.cannon;
        Vec3 T = a.target;
        Vec3 sky = T.add(0, 200, 0);
        int I = c.impact;
        if (a.mine) {
            if (t == 2) Strikes.sound(a, T, ModSounds.COUNTDOWN.get(), 1.0F, 0.85F);
            if (t == 80) Strikes.sound(a, T, ModSounds.ASCENT.get(), 1.0F, 0.9F);
            if (t == 104) Strikes.sound(a, T, ModSounds.ANTIPODE_BREACH.get(), 1.0F, 1.0F);
            if (t == 150) Strikes.sound(a, T, ModSounds.ANTIPODE_BREACH.get(), 0.9F, 0.75F);
            if (t == 190) Strikes.sound(a, T, ModSounds.ANTIPODE_APPROACH.get(), 1.0F, 1.0F);
            if (t == 250) Strikes.sound(a, T, ModSounds.ANTIPODE_CRACK.get(), 1.0F, 0.6F);
        }
        if (t == 6) Strikes.sound(a, sky, ModSounds.ANTIPODE_CRACK.get(), 1.0F, 1.0F);
        if (t == 54) Strikes.sound(a, sky, ModSounds.ANTIPODE_CRACK.get(), 0.9F, 0.8F);
        if (t == 292) Strikes.sound(a, sky, ModSounds.ANTIPODE_APPROACH.get(), 1.0F, 0.8F);
        if (t == I) Strikes.sound(a, T, ModSounds.ANTIPODE_CONTACT.get(), 1.0F, 1.0F);
        if (t == I + Math.max(8, Strikes.soundDelay(T))) Strikes.sound(a, T, ModSounds.BOOM.get(), 1.0F, 0.7F);
        if (t == I + 12) Strikes.sound(a, T, ModSounds.ANTIPODE_AFTER.get(), 1.0F, 1.0F);

        float r = a.radius;
        if (t == I) {
            Light.burst(T.add(0, 2, 0), 160, 2.8F, MAGENTA, 1.8F, 0.25F);
            Light.burst(T.add(0, 2, 0), 60, 4.0F, PALE, 1.0F, 0.18F);
            for (int i = 0; i < 24; i++) {
                double ang = i / 24.0 * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Smoke.puff(T.add(out.scale(r * 0.3)).add(0, 1, 0), out.scale(1.6).add(0, 0.15, 0), r * 0.1F, r * 0.35F, 2.5F, 0x6A3A5C, 0.3F, 0.92F, 0.04F);
            }
        }
        if (t > I && t < c.hold) {
            float k = (t - I) / (float) (c.hold - I);
            float front = r * k;
            for (int i = 0; i < 4; i++) {
                double ang = R.nextDouble() * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Vec3 at = T.add(out.scale(front)).add(0, 1 + R.nextDouble() * 3, 0);
                if (i == 0) {
                    Smoke.puff(at, out.scale(0.3).add(0, 0.25, 0), 1.5F, 4.5F, 2.0F, 0x7A4A6C, 0.22F, 0.95F, 0.06F);
                }
                Light.spark(at, out.scale(0.3).add(0, 0.6 + R.nextDouble(), 0), 1.0F, R.nextBoolean() ? MAGENTA : VIOLET, 0.1F);
            }
        }
    }

    static void frame(Strikes.Active a, float t, float partial) {
    }

    static void ribbons(List<Ribbon> out, Strikes.Active a, float t, float partial) {
        Cannon c = a.cannon;
        int I = c.impact;
        Vec3 T = a.target;
        float r = a.radius;
        if (t < I + 2) {
            float on = ScreenFx.window(t, 0, 12, I - 2, I + 2);
            float pulse = 0.7F + 0.3F * Mth.sin(t * 0.4F);
            Shapes.ring(out, T.add(0, 0.2, 0), UP, r, 0.25F, on * pulse, CRIMSON, Ribbon.Style.GLOW);
        }
        if (WorldFx.available()) {
            return;
        }
        // without the world shader: a plain web of lightning in the sky
        float grow = Mth.clamp((t - 4) / 90.0F, 0, 1);
        float fade = 1 - Mth.clamp((t - I) / 50.0F, 0, 1);
        if (grow > 0 && fade > 0) {
            Vec3 centre = T.add(0, 200, 0);
            Random rr = new Random(a.seed);
            for (int i = 0; i < 11; i++) {
                double ang = i / 11.0 * Math.PI * 2 + rr.nextDouble() * 0.5;
                double len = r * (3.5 + rr.nextDouble() * 2.5) * Math.min(1, grow * (1.2 + rr.nextDouble()));
                Vec3 end = centre.add(new Vec3(Math.cos(ang), 0, Math.sin(ang)).scale(len));
                Shapes.bolt(out, centre, end, a.seed + i * 977L, len * 0.18, 2.4F, 0.9F * fade, PALE, Ribbon.Style.GLOW);
            }
        }
    }

    static void screen(ScreenFx fx, Strikes.Active a, float t, double dist, boolean film) {
        Cannon c = a.cannon;
        int I = c.impact;
        float near = (float) Mth.clamp(1.0 - (dist - a.radius) / (a.radius * 10), 0, 1);
        if (film) {
            if (t > 90 && t < 102) {
                fx.flash(0xFFE0F0, ScreenFx.window(t, 92, 99, 100, 103) * 1.1F);
            }
            if (t > 100 && t < 290) {
                fx.grain(0.35F);
                fx.vignette(0x200008, 0.35F);
                fx.bloom(0.6F);
            }
            if (t > 280 && t < 292) {
                fx.flash(PALE, ScreenFx.hit(t, 288, 3.0F) * 1.2F);
            }
            if (t >= 290 && t < I) {
                fx.vignette(0x400014, 0.5F);
                fx.shake(0.35F * (t - 290) / (I - 290.0F));
                fx.aberration(0.35F * (t - 290) / (I - 290.0F));
            }
            if (t >= I && t < I + 40) {
                fx.impact(CONTACT, t - I, a.target.add(0, 2, 0), MAGENTA);
                fx.shake(1.5F * ScreenFx.hit(t, I, 20.0F));
                fx.flash(PALE, ScreenFx.hit(t, I, 4.0F) * 1.2F);
                fx.punch(1.0F * ScreenFx.hit(t, I, 6.0F));
                fx.aberration(1.0F * ScreenFx.hit(t, I, 12.0F));
            }
            return;
        }
        if (near <= 0) {
            return;
        }
        if (t > 290 && t < I) {
            fx.shake(0.3F * near * (t - 290) / (I - 290.0F));
            fx.vignette(0x400014, 0.4F * near);
        }
        if (t >= I && t < I + 30) {
            if (dist < a.radius * 4) {
                fx.impact(Impact.LIGHT, t - I, a.target.add(0, 2, 0), MAGENTA);
            }
            fx.flash(PALE, ScreenFx.hit(t, I, 5.0F) * near);
            fx.shake(1.2F * near * ScreenFx.hit(t, I, 16.0F));
        }
    }
}
