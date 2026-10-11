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
 * OA-02 Antipode. The film: the sky over the mark cracks; in orbit a breach tears open beside the Earth and a second
 * Earth slides out of it, red-seaed and dark, and drifts in until the two skies touch in lightning; on the ground the
 * other world's land hangs upside down overhead and comes down, its peaks reaching for ours, and touches. The land
 * there is theirs now.
 */
final class AntipodeFx {
    private AntipodeFx() {}

    static final int CRIMSON = 0xD0306A, MAGENTA = 0xFF4FB0, PALE = 0xFFD8EC, VIOLET = 0x8A3CFF;
    static final int[][] CONTACT = {{Impact.INVERT, 1}, {Impact.NEGATIVE, 2}, {Impact.HALFTONE, 1}, {Impact.CRIMSON, 1}, {Impact.INVERT, 1},
            {Impact.SLASHED, 1}, {Impact.INK, 1}};
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
        List<Shot> s = new ArrayList<>();
        int I = a.cannon.impact;

        // 1. over the shoulder, up at the sky over the mark as it cracks
        s.add(Shot.cut(0, 36, (k, t) -> pose(P.subtract(D.scale(3.0 - 0.6 * k)).add(S.scale(1.2)).add(0, 1.6, 0),
                T.add(0, 60 + 70 * k, 0), 60 - 8 * k)));
        // 2. orbit: the breach tears open beside the Earth and something comes through it
        s.add(Shot.cut(36, 84, (k, t) -> {
            Space.View v = o.view();
            v.fade = Mth.clamp((t - 36) / 6.0F, 0, 1);
            Vec3 eye = M.scale(1.7).add(o.east.scale(1.0 + 0.3 * k)).subtract(o.north.scale(4.6 - 0.4 * k));
            v.look(eye, M.scale(1.4).add(o.east.scale(1.4)), M);
            v.fov = 52;
            v.breach = breach;
            v.breachNormal = bn;
            v.breachRadius = 1.18F * Film.smooth((t - 40) / 14.0F);
            v.breachOpen = Film.smooth((t - 40) / 10.0F);
            float e = Film.smooth((t - 52) / 30.0F);
            v.earth2 = breach.subtract(bn.scale(1.4)).lerp(breach.add(bn.scale(0.4)), e);
            v.earth2Radius = 1;
            Space.show(v);
            return Orbit.hold();
        }));
        // 3. the second Earth drifts in until the skies touch
        s.add(Shot.cut(84, 122, (k, t) -> {
            Space.View v = o.view();
            Vec3 eye = M.scale(1.55).add(o.east.scale(-0.4 + 0.5 * k)).subtract(o.north.scale(2.1 - 0.3 * k));
            v.look(eye, M.scale(1.5), M);
            v.fov = 58;
            v.breach = breach;
            v.breachNormal = bn;
            v.breachRadius = 1.18F;
            v.breachOpen = 1 - 0.7F * k;
            float e = Film.smooth((t - 84) / 32.0F);
            v.earth2 = breach.add(bn.scale(0.4)).lerp(contactAt, e);
            v.earth2Radius = 1;
            v.contact = Film.smooth((t - 104) / 14.0F);
            v.markGlow = 0.5F * v.contact;
            Space.show(v);
            return Orbit.hold();
        }));
        // 4. on the ground, looking up: their land hangs over ours, coming down
        s.add(Shot.cut(122, 150, (k, t) -> pose(T.add(S.scale(R0 * 0.35)).add(D.scale(-R0 * 0.2)).add(0, 1.8, 0),
                T.add(D.scale(R0 * 0.3)).add(0, 90 - 30 * k, 0), 78 - 6 * k)));
        // 5. wide: the peaks reach the mark
        s.add(Shot.cut(150, 174, (k, t) -> pose(T.subtract(D.scale(R0 * 2.7)).add(S.scale(R0 * 0.9)).add(0, R0 * 0.22 + 3, 0),
                T.add(0, R0 * 0.45, 0), 66 + 6 * k)));
        // 6. from the air, circling: the exchange spreading out to the rim
        s.add(Shot.cut(174, 206, (k, t) -> {
            double ang = Math.toRadians(-30 + 70 * k);
            Vec3 round = D.scale(-Math.cos(ang)).add(S.scale(Math.sin(ang)));
            return pose(T.add(round.scale(R0 * 2.3)).add(0, R0 * 1.6, 0), T, 62);
        }));
        // 7. back to the caller
        s.add(Shot.cut(206, a.cannon.film, (k, t) -> pose(P.subtract(D.scale(4.5 - 1.0 * k)).add(S.scale(1.2)).add(0, 1.9, 0), T.add(0, 6, 0), 66)));

        String[][] lines = {{"4", "Uplink established. Channel two."}, {"40", "Antipode. Open the breach."}, {"62", "...There. Another Earth."},
                {"88", "Bring it closer."}, {"126", "Look up."}, {"178", "Their land, for ours."}};
        Film.Card card = new Film.Card("ORBITAL ARSENAL", "対蹠の世界", "OA-02  ANTIPODE", Cannon.ANTIPODE.color, 40, 50);
        return new Film(partial -> Strikes.time(a, partial), s, a.cannon.film, card, lines, 8, 12);
    }

    static CameraPose pose(Vec3 pos, Vec3 look, double fov) {
        return new CameraPose(pos, look, (float) fov, 0);
    }

    // ------------------------------------------------------------------------------------------------ the world

    static void tick(Strikes.Active a, int t) {
        Cannon c = a.cannon;
        Vec3 T = a.target;
        Vec3 sky = T.add(0, 170, 0);
        int I = c.impact;
        if (a.mine) {
            if (t == 2) Strikes.sound(a, T, ModSounds.COUNTDOWN.get(), 1.0F, 0.85F);
            if (t == 38) Strikes.sound(a, T, ModSounds.ANTIPODE_BREACH.get(), 1.0F, 1.0F);
            if (t == 84) Strikes.sound(a, T, ModSounds.ANTIPODE_APPROACH.get(), 1.0F, 1.0F);
        }
        if (t == 6) Strikes.sound(a, sky, ModSounds.ANTIPODE_CRACK.get(), 1.0F, 1.0F);
        if (t == 60) Strikes.sound(a, sky, ModSounds.ANTIPODE_CRACK.get(), 0.8F, 0.8F);
        if (t == 124) Strikes.sound(a, sky, ModSounds.ANTIPODE_APPROACH.get(), 1.0F, 0.85F);
        if (t == I) Strikes.sound(a, T, ModSounds.ANTIPODE_CONTACT.get(), 1.0F, 1.0F);
        if (t == I + Math.max(8, Strikes.soundDelay(T))) Strikes.sound(a, T, ModSounds.BOOM.get(), 1.0F, 0.7F);
        if (t == I + 12) Strikes.sound(a, T, ModSounds.ANTIPODE_AFTER.get(), 1.0F, 1.0F);

        float r = a.radius;
        if (t == I) {
            Light.ring(T.add(0, 0.6, 0), UP, r * 0.2F, r * 3.5F, r * 0.12F, PALE, 1.2F);
            Light.ring(T.add(0, 1.0, 0), UP, r * 0.1F, r * 2.2F, r * 0.08F, MAGENTA, 1.8F);
            Light.burst(T.add(0, 2, 0), 120, 2.6F, MAGENTA, 1.8F, 0.3F);
            for (int i = 0; i < 24; i++) {
                double ang = i / 24.0 * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Smoke.puff(T.add(out.scale(r * 0.3)).add(0, 1, 0), out.scale(1.6).add(0, 0.15, 0), r * 0.1F, r * 0.35F, 2.5F, 0x6A3A5C, 0.35F, 0.92F,
                        0.04F);
            }
        }
        // the front of the exchange, racing out to the rim in dust and violet sparks
        if (t > I && t < c.hold) {
            float k = (t - I) / (float) (c.hold - I);
            float front = r * k;
            for (int i = 0; i < 4; i++) {
                double ang = R.nextDouble() * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Vec3 at = T.add(out.scale(front)).add(0, 1 + R.nextDouble() * 3, 0);
                if (i == 0) {
                    Smoke.puff(at, out.scale(0.3).add(0, 0.25, 0), 1.5F, 4.5F, 2.0F, 0x7A4A6C, 0.25F, 0.95F, 0.06F);
                }
                Light.spark(at, out.scale(0.3).add(0, 0.6 + R.nextDouble(), 0), 1.0F, R.nextBoolean() ? MAGENTA : VIOLET, 0.12F);
            }
            if (t % 6 == 0) {
                Light.ring(T.add(0, 0.6, 0), UP, front, front + 3, 0.6F, MAGENTA, 0.5F);
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
        // the mark on the ground
        if (t < I + 2) {
            float on = ScreenFx.window(t, 0, 12, I - 2, I + 2);
            float pulse = 0.7F + 0.3F * Mth.sin(t * 0.4F);
            Shapes.ring(out, T.add(0, 0.2, 0), UP, r, 0.6F, on * pulse, CRIMSON, Ribbon.Style.GLOW);
            Shapes.ring(out, T.add(0, 0.2, 0), UP, r * 0.5, 0.3F, on * 0.6F, MAGENTA, Ribbon.Style.GLOW);
        }
        // the sky cracking over the mark: a web of light spreading from one point
        float grow = Mth.clamp((t - 4) / 70.0F, 0, 1);
        float fade = 1 - Mth.clamp((t - I) / 50.0F, 0, 1);
        if (grow > 0 && fade > 0) {
            Vec3 centre = T.add(0, 170, 0);
            Random rr = new Random(a.seed);
            int arms = 13;
            for (int i = 0; i < arms; i++) {
                double ang = i / (double) arms * Math.PI * 2 + rr.nextDouble() * 0.5;
                double len = r * (3.5 + rr.nextDouble() * 2.5) * Math.min(1, grow * (1.2 + rr.nextDouble()));
                Vec3 dir = new Vec3(Math.cos(ang), (rr.nextDouble() - 0.5) * 0.15, Math.sin(ang));
                Vec3 end = centre.add(dir.scale(len));
                long seed = a.seed + i * 977L + (long) (t / 3);
                Shapes.bolt(out, centre, end, a.seed + i * 977L, len * 0.18, 9.0F, 0.35F * fade, MAGENTA, Ribbon.Style.GLOW);
                Shapes.bolt(out, centre, end, a.seed + i * 977L, len * 0.18, 2.6F, 0.95F * fade, PALE, Ribbon.Style.GLOW);
                // a branch
                Vec3 mid = centre.add(dir.scale(len * 0.55));
                Vec3 branch = mid.add(Shapes.rotateY(dir, 35 * (rr.nextBoolean() ? 1 : -1)).scale(len * 0.4));
                Shapes.bolt(out, mid, branch, seed, len * 0.1, 1.8F, 0.7F * fade, PALE, Ribbon.Style.GLOW);
            }
        }
        // lightning between the two lands as they close
        if (t > 118 && t < I + 6) {
            float base = MirrorWorld.base(a, t);
            for (int i = 0; i < 3; i++) {
                Random rr = new Random(a.seed + (long) (t / 2) * 31 + i);
                if (rr.nextFloat() < 0.5F) {
                    continue;
                }
                double ang = rr.nextDouble() * Math.PI * 2, d = rr.nextDouble() * r * 0.9;
                Vec3 g = T.add(Math.cos(ang) * d, 0.2, Math.sin(ang) * d);
                Vec3 top = g.add(0, Math.max(4, base - r * MirrorWorld.HANG * 0.5), 0);
                Shapes.bolt(out, top, g, rr.nextLong(), (top.y - g.y) * 0.12, 1.4F, 0.9F, PALE, Ribbon.Style.GLOW);
                Shapes.bolt(out, top, g, rr.nextLong(), (top.y - g.y) * 0.12, 4.0F, 0.3F, MAGENTA, Ribbon.Style.GLOW);
            }
        }
    }

    static void screen(ScreenFx fx, Strikes.Active a, float t, double dist, boolean film) {
        Cannon c = a.cannon;
        int I = c.impact;
        float near = (float) Mth.clamp(1.0 - (dist - a.radius) / (a.radius * 9), 0, 1);
        if (film) {
            if (t > 36 && t < 122) {
                fx.grain(0.4F);
                fx.vignette(0x200008, 0.4F);
                fx.bloom(0.5F);
            }
            if (t >= 122 && t < I) {
                fx.vignette(0x400014, 0.55F);
                fx.shake(0.25F * (t - 122) / (I - 122.0F));
                fx.aberration(0.3F * (t - 122) / (I - 122.0F));
            }
            if (t >= I && t < I + 34) {
                fx.impact(CONTACT, t - I, a.target.add(0, 2, 0), MAGENTA);
                fx.shake(1.4F * ScreenFx.hit(t, I, 18.0F));
                fx.flash(PALE, ScreenFx.hit(t, I, 4.0F) * 1.2F);
                fx.punch(1.0F * ScreenFx.hit(t, I, 6.0F));
                fx.aberration(1.0F * ScreenFx.hit(t, I, 12.0F));
            }
            return;
        }
        if (near <= 0) {
            return;
        }
        if (t > 110 && t < I) {
            fx.shake(0.3F * near * (t - 110) / (I - 110.0F));
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
