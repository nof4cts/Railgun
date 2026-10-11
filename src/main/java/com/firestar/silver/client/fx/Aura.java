package com.firestar.silver.client.fx;

import java.util.List;
import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The anime aura, drawn as a flame silhouette standing in the plane behind the fighter (as the camera sees him), so his
 * body cuts it out the way a cel aura sits behind a character: a soft filled teardrop, flickering tongues licking up its
 * edges, a crisp bright outline, wisps tearing off the tip, and a pressure ring on the ground.
 */
public final class Aura {
    private Aura() {}

    /** Shape and colours of one awakening. */
    public record Style(float height, float width, int fill, int tongue, int core, int outline, int wisp, int shade, float flicker) {}

    public static final Style OMEN = new Style(2.75F, 1.05F, 0x4F7FE0, 0x7FB4FF, 0xD6E8FF, 0xA9CCFF, 0xB49CFF, 0x3D5FC4, 1.0F);
    public static final Style ZENITH = new Style(3.3F, 1.25F, 0x6E9CF0, 0xA8D4FF, 0xFFFFFF, 0xF2F8FF, 0xC9B8FF, 0x5A7FD8, 0.0F);

    /** Half-width of the silhouette at fraction u of its height: broad at the shoulders, a point at the top. */
    static double profile(double u) {
        u = Mth.clamp(u, 0, 1);
        return (0.62 + 0.38 * Math.sin(Math.PI * Math.min(1.0, u * 1.25))) * Math.pow(1.0 - u, 0.75);
    }

    /**
     * @param feet    the fighter's feet
     * @param away    horizontal unit vector from the camera toward him
     * @param power   0..1 strength
     * @param time    seconds, for motion
     * @param seed    per-fighter phase
     * @param detail  how many tongues per side
     */
    public static void draw(List<Ribbon> out, Vec3 feet, Vec3 away, float power, float time, int seed, int detail, Style s) {
        if (power < 0.01F) {
            return;
        }
        Vec3 side = new Vec3(-away.z, 0, away.x);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 base = feet.add(away.scale(0.42)).subtract(0, 0.05, 0);
        float grow = Mth.clamp(power * 1.8F, 0, 1);
        double H = s.height * (0.55 + 0.45 * grow);
        double W = s.width * (0.7 + 0.3 * grow);
        // Omen sputters: the light drops out and comes back as the instinct does
        float flick = 1.0F;
        if (s.flicker > 0) {
            float n = (float) (0.5 + 0.3 * Math.sin(time * 7.3 + seed) + 0.2 * Math.sin(time * 17.9 + seed * 3));
            flick = Mth.clamp(0.45F + n, 0.4F, 1.0F);
        }
        float a = Math.min(1.0F, power * 1.6F) * flick;

        // the body of the flame: overlapping vertical strokes across the silhouette, faint
        int strips = 7;
        for (int i = 0; i < strips; i++) {
            double f = -1 + 2.0 * i / (strips - 1);
            double top = H * (1 - 0.5 * f * f) * (0.94 + 0.06 * Math.sin(time * 9 + i * 1.7 + seed));
            int n = 8;
            Vec3[] spine = new Vec3[n];
            float[] w = new float[n];
            float[] al = new float[n];
            for (int k = 0; k < n; k++) {
                double u = k / (n - 1.0);
                double y = u * top;
                double x = f * profile(y / H) * W * 0.85 + 0.05 * Math.sin(time * 6 + u * 5 + i);
                spine[k] = base.add(side.scale(x)).add(up.scale(y));
                w[k] = (float) (2.0 * W / strips * 1.9 * (1 - 0.6 * u));
                al[k] = a * 0.11F * (float) (Math.sin(Math.PI * Math.pow(u, 0.6)) * 0.8 + 0.2 * (1 - u));
            }
            out.add(new Ribbon(spine, null, w, al, s.fill, Ribbon.Style.GLOW));
            float[] ta = al.clone();
            for (int k = 0; k < n; k++) {
                ta[k] *= 1.5F;
            }
            out.add(new Ribbon(spine, null, w, ta, s.tongue, Ribbon.Style.TINT));
        }

        // tongues licking up both edges, born low, rising and thinning as they go
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            for (int j = 0; j < detail; j++) {
                double ph = frac(time * (1.3 + 0.17 * (j % 3)) + j * 0.37 + (sgn > 0 ? 0.5 : 0) + seed * 0.13);
                double u0 = (j / (double) detail) * 0.75 + ph * 0.18;
                double len = 0.22 + 0.16 * Math.sin(j * 2.3 + seed);
                float life = (float) Math.sin(Math.PI * ph);
                int n = 7;
                Vec3[] spine = new Vec3[n];
                float[] w = new float[n];
                float[] al = new float[n];
                for (int k = 0; k < n; k++) {
                    double v = k / (n - 1.0);
                    double u = Math.min(1.0, u0 + v * len);
                    double bulge = 0.10 * Math.sin(Math.PI * v) * (1 + 0.5 * Math.sin(time * 14 + j));
                    double x = sgn * (profile(u) * W + bulge) + sgn * 0.06 * Math.sin(time * 11 + j * 1.9 + v * 4);
                    spine[k] = base.add(side.scale(x)).add(up.scale(u * H));
                    w[k] = (float) ((0.26 + 0.1 * (1 - u0)) * (1 - 0.85 * v) * (0.7 + 0.3 * power));
                    al[k] = a * life * 0.5F * (float) Math.sin(Math.PI * Math.pow(v, 0.55));
                }
                float[] tal2 = al.clone();
                for (int k = 0; k < n; k++) {
                    tal2[k] *= 0.35F;
                }
                out.add(new Ribbon(spine, null, w, tal2, s.tongue, Ribbon.Style.TINT));
                out.add(new Ribbon(spine, null, w, al, s.tongue, Ribbon.Style.GLOW));
                float[] w2 = w.clone();
                float[] al2 = al.clone();
                for (int k = 0; k < n; k++) {
                    w2[k] *= 0.35F;
                    al2[k] = Math.min(1.0F, al2[k] * 1.4F);
                }
                out.add(new Ribbon(spine, null, w2, al2, s.core, Ribbon.Style.GLOW));
            }
        }

        // the crisp outline round the whole silhouette: the cel edge
        int n = 34;
        Vec3[] edge = new Vec3[n];
        float[] ew = new float[n];
        float[] ea = new float[n];
        for (int k = 0; k < n; k++) {
            double v = k / (n - 1.0);
            // left foot -> tip -> right foot
            double u = v < 0.5 ? v * 2 : (1 - v) * 2;
            int sgn = v < 0.5 ? -1 : 1;
            double wob = 0.07 * Math.sin(time * 12 + u * 14 + sgn * 1.3 + seed) + 0.04 * Math.sin(time * 23 + u * 31);
            double x = sgn * (profile(u) * W + wob * (0.4 + u));
            double y = u * H + (u > 0.97 ? 0.08 * Math.sin(time * 9) : 0);
            edge[k] = base.add(side.scale(x)).add(up.scale(y));
            ew[k] = (float) (0.075 * (1 - 0.5 * u));
            ea[k] = a * 0.85F * (float) Mth.clamp(u * 6, 0, 1);
        }
        // a deeper blue band under the bright edge: the cel shadow that keeps it legible on a bright sky
        float[] tw = ew.clone();
        float[] tal = ea.clone();
        for (int k = 0; k < n; k++) {
            tw[k] *= 2.4F;
            tal[k] *= 0.45F;
        }
        out.add(new Ribbon(edge, null, tw, tal, s.shade, Ribbon.Style.TINT));
        out.add(new Ribbon(edge, null, ew, ea, s.outline, Ribbon.Style.GLOW));
        float[] ew2 = ew.clone();
        float[] ea2 = ea.clone();
        for (int k = 0; k < n; k++) {
            ew2[k] *= 3.2F;
            ea2[k] *= 0.22F;
        }
        out.add(new Ribbon(edge, null, ew2, ea2, s.tongue, Ribbon.Style.GLOW));

        // wisps tearing off the tip and rising away
        Vec3 tip = base.add(up.scale(H * 0.92));
        for (int j = 0; j < 4; j++) {
            double ph = frac(time * 0.9 + j * 0.25 + seed * 0.07);
            Vec3 c = tip.add(up.scale(ph * 1.4)).add(side.scale(0.35 * Math.sin(j * 2.1 + ph * 3)));
            double len = 0.45 * (1 - ph) + 0.1;
            float al = a * 0.5F * (float) Math.sin(Math.PI * ph);
            Shapes.streak(out, c.subtract(0, len * 0.5, 0), c.add(0, len * 0.5, 0), (float) (0.18 * (1 - ph) + 0.04), al, s.wisp, Ribbon.Style.GLOW);
            Shapes.streak(out, c.subtract(0, len * 0.3, 0), c.add(0, len * 0.3, 0), 0.05F, Math.min(1.0F, al * 1.3F), s.core, Ribbon.Style.GLOW);
        }

        // pressure on the ground: a ring, and dust lines pushed outward
        Shapes.ring(out, feet.add(0, 0.04, 0), up, W * 1.05 + 0.06 * Math.sin(time * 6), 0.1F, 0.4F * a, s.outline, Ribbon.Style.GLOW);
        Shapes.ring(out, feet.add(0, 0.03, 0), up, W * 1.05, 0.45F, 0.12F * a, s.tongue, Ribbon.Style.GLOW);
        Random r = new Random(seed * 131L);
        for (int j = 0; j < 10; j++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double ph = frac(time * (0.8 + r.nextDouble() * 0.5) + r.nextDouble());
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 p0 = feet.add(0, 0.05, 0).add(dir.scale(W * 0.9 + ph * 1.6));
            Shapes.streak(out, p0, p0.add(dir.scale(0.35 + 0.3 * (1 - ph))), 0.06F, a * 0.45F * (float) (1 - ph), s.core, Ribbon.Style.GLOW);
        }
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }
}
