package com.gojo.rework.client;

import com.gojo.rework.client.fx.Ink;
import com.gojo.rework.common.GBeats;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Unlimited Void as it stands in the world: a globe of night around him, stars and great white bursts of light thrown
 * across it like spilled paint, a black hole hanging in it with a ring of light round its rim, and under the feet a
 * floor of nothing that mirrors the dark.
 */
public final class GVoid {
    private GVoid() {}

    static Vec3 hole(GCasts.Play c) {
        return c.msg.origin().add(c.flat().scale(GBeats.DV_RADIUS * 0.82)).add(0, GBeats.DV_RADIUS * 0.28, 0);
    }

    static void drawDomain(Ink ink, GCasts.Play c, float t, float partial) {
        if (t < 8) {
            // the hand sign: a cold light between his fingers
            Vec3 h = GFx.hand(c, partial, true).add(0, 0.35, 0);
            ink.glow(h, 0.5 + 0.3 * Math.sin(t), GFx.a(GFx.BLUE_CORE, 0.8F));
        }
        if (t < GBeats.DV_HOLE || t > GBeats.DV_HOLD) {
            return;
        }
        float in = GFx.smooth(GFx.span(t, GBeats.DV_HOLE, GBeats.DV_VOID));
        float out = 1 - GFx.span(t, GBeats.DV_HOLD - 24, GBeats.DV_HOLD);
        float k = Math.min(in, out);
        if (k <= 0.01F) {
            return;
        }
        globe(ink, c.msg.origin().subtract(0, 1.62, 0), GBeats.DV_RADIUS * (0.25 + 0.75 * in), k, t, c.msg.seed(), hole(c), 1.0);
    }

    static void drawFlash(Ink ink, GCasts.Play c, float t, float partial) {
        if (t < 4) {
            ink.glow(GFx.hand(c, partial, true).add(0, 0.35, 0), 0.5, GFx.a(GFx.BLUE_CORE, 0.8F));
        }
        // a fifth of a second: the void opens and closes
        if (t >= GBeats.D2_OPEN && t < GBeats.D2_CLOSE) {
            globe(ink, c.msg.origin().subtract(0, 1.62, 0), GBeats.D2_RADIUS, 1, t, c.msg.seed(), hole(c), 0.6);
        }
    }

    /** The void: everything at {@code feet}'s radius {@code R}, faded by {@code k}. */
    static void globe(Ink ink, Vec3 feet, double R, float k, float t, long seed, Vec3 hole, double holeScale) {
        Vec3 cam = ink.cam();
        boolean inside = cam.distanceTo(feet) < R;
        // the globe of night
        ink.sphere(false, feet, R, GFx.a(GFx.SPACE, 0.97F * k), GFx.a(GFx.NIGHT, 0.99F * k), 0.8);
        // the floor: black glass
        ink.disc(false, feet.add(0, 0.03, 0), GFx.UP, R * 0.999, GFx.a(0xFF050814, 0.93F * k), GFx.a(GFx.NIGHT, 0.97F * k));
        // the black hole, and the light bent round it
        Vec3 toCam = cam.subtract(hole).normalize();
        double hr = 7.5 * holeScale * k;
        ink.disc(false, hole.add(toCam.scale(0.2)), toCam, hr, GFx.a(0xFF000000, k), GFx.a(0xFF000000, k));
        ink.corona(hole, hr, hr * 1.18, GFx.a(GFx.WHITE, 0.95F * k));
        ink.corona(hole, hr, hr * 2.4, GFx.a(0xFF9FC4FF, 0.45F * k));
        ink.corona(hole, hr * 1.4, hr * 4.5, GFx.a(0xFF4060C0, 0.25F * k));
        // the lensed ring: a bright band crossing below it, as in a photograph of one
        Vec3 tilt = new Vec3(0, 1, 0).add(toCam.scale(-0.35)).normalize();
        ink.arc(hole.add(0, -hr * 0.15, 0), tilt, hr * 1.55, hr * 0.06, Math.PI * 0.05, Math.PI * 0.95, GFx.a(GFx.WHITE, 0.9F * k), 48, true);
        ink.arc(hole.add(0, -hr * 0.15, 0), tilt, hr * 1.55, hr * 0.22, Math.PI * 0.05, Math.PI * 0.95, GFx.a(0xFFB8D4FF, 0.35F * k), 48, true);
        // the swirl round it
        for (int s = 0; s < 5; s++) {
            int n = 14;
            Vec3[] pts = new Vec3[n];
            double[] w = new double[n];
            int[] cols = new int[n];
            Vec3[] b = Ink.basis(toCam);
            for (int i = 0; i < n; i++) {
                double u = i / (double) (n - 1);
                double ang = t * 0.03 + s * 1.256 + u * 3.2;
                double rad = hr * (3.4 - 2.0 * u);
                pts[i] = hole.add(b[0].scale(Math.cos(ang) * rad)).add(b[1].scale(Math.sin(ang) * rad));
                w[i] = hr * 0.12 * Math.sin(Math.PI * u);
                cols[i] = GFx.a(i % 2 == 0 ? 0xFFDDE8FF : 0xFF7FA0E0, 0.45F * k * (float) Math.sin(Math.PI * u));
            }
            ink.ribbon(pts, w, cols);
        }
        Random r = new Random(seed);
        // the stars
        for (int i = 0; i < 220; i++) {
            Vec3 d = dirOnUpper(r);
            Vec3 p = feet.add(d.scale(R * 0.96));
            if (p.subtract(hole).length() < hr * 2.6) {
                continue;
            }
            float tw = 0.5F + 0.5F * (float) Math.sin(t * 0.2 + i);
            ink.glow(p, 0.12 + r.nextDouble() * 0.22, GFx.a(GFx.WHITE, (0.4F + 0.6F * tw) * k));
        }
        // the bursts of white: clusters of light like paint thrown across the dark
        for (int cl = 0; cl < 26; cl++) {
            Vec3 d = dirOnUpper(r);
            Vec3 centre = feet.add(d.scale(R * (0.8 + 0.15 * r.nextDouble())));
            if (centre.subtract(hole).length() < hr * 3.0) {
                continue;
            }
            double size = 1.5 + r.nextDouble() * 4.5;
            int blobs = 5 + r.nextInt(8);
            for (int b = 0; b < blobs; b++) {
                Vec3 off = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).scale(size * 0.45);
                double br = size * (0.25 + r.nextDouble() * 0.55);
                float drift = (float) Math.sin(t * 0.04 + cl + b) * 0.1F;
                ink.glow(centre.add(off).add(0, drift, 0), br, GFx.a(b % 4 == 0 ? 0xFFCFE0FF : GFx.WHITE, (0.45F + 0.35F * r.nextFloat()) * k));
            }
            // a ragged edge of small specks thrown off it
            for (int s = 0; s < 8; s++) {
                Vec3 off = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).scale(size * 1.1);
                ink.glow(centre.add(off), 0.15 + r.nextDouble() * 0.3, GFx.a(GFx.WHITE, 0.7F * k));
            }
        }
        // light streaming across: long faint streaks
        for (int i = 0; i < 10; i++) {
            Vec3 a = feet.add(dirOnUpper(r).scale(R * 0.9));
            Vec3 b = feet.add(dirOnUpper(r).scale(R * 0.9));
            float f = 0.5F + 0.5F * (float) Math.sin(t * 0.05 + i * 1.7);
            ink.beam(a, a.lerp(b, 0.35), 0.15, GFx.a(0xFFB8D4FF, 0.18F * f * k));
        }
        // the floor reflects the hole and the bursts faintly
        if (inside) {
            Vec3 mirror = new Vec3(hole.x, feet.y * 2 - hole.y, hole.z);
            ink.glow(mirror.add(0, 0.05, 0), hr * 2.2, GFx.a(0xFF6E8FD8, 0.18F * k));
        }
    }

    static Vec3 dirOnUpper(Random r) {
        double y = r.nextDouble() * 1.1 - 0.15;
        double a = r.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(Math.max(0, 1 - y * y));
        return new Vec3(Math.cos(a) * s, y, Math.sin(a) * s);
    }

    /** Whether the local player stands inside an open void (for the hud and the sky). */
    public static float insideVoid() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        float best = 0;
        for (GCasts.Play c : GCasts.all()) {
            if (c.move() == com.gojo.rework.common.GMove.DOMAIN) {
                float t = c.time(0);
                double d = mc.player.position().distanceTo(c.msg.origin().subtract(0, 1.62, 0));
                if (d < GBeats.DV_RADIUS) {
                    best = Math.max(best, Math.min(GFx.span(t, GBeats.DV_HOLE, GBeats.DV_VOID), 1 - GFx.span(t, GBeats.DV_HOLD - 24, GBeats.DV_HOLD)));
                }
            }
        }
        return Mth.clamp(best, 0, 1);
    }
}
