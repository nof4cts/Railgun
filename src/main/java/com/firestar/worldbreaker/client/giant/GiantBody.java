package com.firestar.worldbreaker.client.giant;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The marching giants, sculpted from smooth forms in human proportion (feet at 0, crown at 1, facing +z):
 *
 * <ul>
 *   <li>the <b>colossi</b>: statues of living bronze gone green with age, athletic and upright, faces smooth masks with
 *   two burning slits, a crest running over the skull, a kilt of hanging bronze plates, bands at the wrists;</li>
 *   <li>the <b>sovereign</b> who walks behind them: pale marble, crowned and mantled in gold, a long crimson cloak
 *   behind, a staff in one hand crowned with light.</li>
 * </ul>
 *
 * Both are posed for each moment of their walk from the shared gait phase.
 */
final class GiantBody {
    private GiantBody() {}

    /** The surfaces a body is drawn in. */
    record Materials(VertexConsumer bronze, VertexConsumer marble, VertexConsumer gold, VertexConsumer cloth, VertexConsumer glow) {}

    /** Walk pose, shared by both bodies. */
    private record Walk(float bob, float hipRoll, float swingL, float swingR, float kneeL, float kneeR, float armL, float armR, float lag, float k) {
        static Walk of(double gait, float rising, float stride) {
            float a = (float) (gait * Math.PI * 2);
            float c = Mth.cos(a), sn = Mth.sin(a);
            float lag = (float) Math.cos(a - 0.5);
            return new Walk(-0.012F * (1 - Math.abs(c)), 0.04F * c, stride * c, -stride * c, Math.max(0, -sn) * 0.9F * stride / 0.4F,
                    Math.max(0, sn) * 0.9F * stride / 0.4F, -0.32F * lag, 0.32F * lag, lag, 1 - rising);
        }
    }

    // ------------------------------------------------------------------------------------------------ colossus

    static void colossus(PoseStack ps, Sculpt s, Materials m, double gait, float time, int seed, float rising, float tint, float warm) {
        Walk w = Walk.of(gait, rising, 0.4F);
        float br = 0.95F * tint, bg = 0.95F * tint, bb = 0.95F * tint;
        s.material(m.bronze(), br * (1 + warm), bg, bb * (1 - warm));
        ps.pushPose();
        ps.translate(0, w.bob, 0);

        legs(ps, s, w, seed, 0.5F, 0.06F, 0.043F);

        ps.pushPose();
        ps.translate(0, 0.5F, 0);
        ps.mulPose(Axis.ZP.rotation(w.hipRoll));
        ps.mulPose(Axis.YP.rotation(0.07F * (w.swingL)));
        // pelvis
        s.ellipsoid(ps, 0, 0.015F, 0, 0.095F, 0.06F, 0.065F, seed + 21);
        // kilt: bronze plates hanging round the hips, swinging with the legs
        if (s.detail) {
            for (int i = 0; i < 8; i++) {
                float ang = (float) (i / 8.0 * Math.PI * 2);
                float x = Mth.sin(ang) * 0.1F, z = Mth.cos(ang) * 0.075F;
                ps.pushPose();
                ps.translate(x, 0.0F, z);
                ps.mulPose(Axis.YP.rotation(ang));
                float swing = (x < 0 ? w.swingR : w.swingL) * 0.35F;
                ps.mulPose(Axis.XP.rotation(-0.12F - Math.max(0, -swing)));
                s.ellipsoid(ps, 0, -0.06F, 0.008F, 0.036F, 0.07F, 0.01F, seed + 80 + i);
                ps.popPose();
            }
            // the belt
            s.ellipsoid(ps, 0, 0.05F, 0, 0.1F, 0.016F, 0.072F, seed + 90);
        }
        // abdomen, leaning very slightly into the walk
        ps.mulPose(Axis.XP.rotation(0.06F - 0.35F * w.k));
        s.ellipsoid(ps, 0, 0.11F, 0.006F, 0.083F, 0.08F, 0.058F, seed + 22);
        ps.translate(0, 0.17F, 0);
        ps.mulPose(Axis.YP.rotation(-0.1F * w.swingL));
        // chest: broad, the pectorals and the back muscles over it
        s.ellipsoid(ps, 0, 0.07F, 0.0F, 0.125F, 0.095F, 0.072F, seed + 23);
        s.ellipsoid(ps, -0.052F, 0.085F, 0.04F, 0.058F, 0.045F, 0.035F, seed + 24);
        s.ellipsoid(ps, 0.052F, 0.085F, 0.04F, 0.058F, 0.045F, 0.035F, seed + 25);
        if (s.detail) {
            s.ellipsoid(ps, -0.065F, 0.05F, -0.03F, 0.06F, 0.075F, 0.04F, seed + 26);
            s.ellipsoid(ps, 0.065F, 0.05F, -0.03F, 0.06F, 0.075F, 0.04F, seed + 27);
            // trapezius, sloping from neck to shoulder
            s.ellipsoid(ps, 0, 0.15F, -0.012F, 0.09F, 0.03F, 0.045F, seed + 28);
        }

        arms(ps, s, w, seed, 0.15F, 0.038F, 0.03F, 0.026F, false, m, time);

        // neck and head
        s.limb(ps, 0, 0.14F, 0.0F, 0, 0.205F, 0.012F, 0.036F, 0.032F, seed + 70);
        ps.translate(0, 0.205F, 0.012F);
        ps.mulPose(Axis.YP.rotation(0.22F * Mth.sin(time * 0.3F + seed * 1.7F)));
        ps.mulPose(Axis.XP.rotation(0.05F * Mth.sin(time * 0.45F + seed) - 0.5F * w.k));
        // skull and the smooth mask of a face
        s.ellipsoid(ps, 0, 0.06F, -0.004F, 0.048F, 0.064F, 0.056F, seed + 71);
        s.ellipsoid(ps, 0, 0.035F, 0.022F, 0.041F, 0.052F, 0.04F, seed + 72);
        // the jaw and chin
        s.ellipsoid(ps, 0, 0.004F, 0.026F, 0.033F, 0.022F, 0.033F, seed + 73);
        // the crest: a fin of bronze from brow to nape
        s.ellipsoid(ps, 0, 0.115F, -0.01F, 0.009F, 0.03F, 0.062F, seed + 74);
        // the eyes: two narrow burning slits
        for (int side = -1; side <= 1; side += 2) {
            burn(ps, m.glow(), side * 0.018F, 0.05F, 0.059F, 0.012F, 0.0035F, 0.004F, 1.0F, 0.78F, 0.4F);
        }
        ps.popPose();
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------------ sovereign

    static void sovereign(PoseStack ps, Sculpt s, Materials m, double gait, float time, int seed, float rising) {
        Walk w = Walk.of(gait, rising, 0.3F);
        ps.pushPose();
        ps.translate(0, w.bob, 0);
        s.material(m.marble(), 1, 1, 1);
        legs(ps, s, w, seed, 0.5F, 0.056F, 0.04F);
        ps.pushPose();
        ps.translate(0, 0.5F, 0);
        ps.mulPose(Axis.ZP.rotation(w.hipRoll * 0.6F));
        s.ellipsoid(ps, 0, 0.015F, 0, 0.09F, 0.06F, 0.062F, seed + 21);
        // a long skirt of gold-hemmed crimson to the knees
        s.material(m.cloth(), 1, 1, 1);
        for (int i = 0; i < (s.detail ? 10 : 5); i++) {
            float ang = (float) (i / (s.detail ? 10.0 : 5.0) * Math.PI * 2);
            float x = Mth.sin(ang) * 0.085F, z = Mth.cos(ang) * 0.068F;
            ps.pushPose();
            ps.translate(x, 0.02F, z);
            ps.mulPose(Axis.YP.rotation(ang));
            float swing = (x < 0 ? w.swingR : w.swingL) * 0.5F;
            ps.mulPose(Axis.XP.rotation(-0.1F - Math.max(0, -swing)));
            s.ellipsoid(ps, 0, -0.12F, 0.01F, 0.05F, 0.13F, 0.012F, seed + 80 + i);
            ps.popPose();
        }
        s.material(m.gold(), 1, 1, 1);
        s.ellipsoid(ps, 0, 0.05F, 0, 0.096F, 0.02F, 0.07F, seed + 90);
        ps.mulPose(Axis.XP.rotation(-0.03F - 0.3F * w.k));
        s.material(m.marble(), 1, 1, 1);
        s.ellipsoid(ps, 0, 0.11F, 0.004F, 0.08F, 0.08F, 0.056F, seed + 22);
        ps.translate(0, 0.17F, 0);
        // a breastplate of gold over the marble chest
        s.material(m.gold(), 1, 1, 1);
        s.ellipsoid(ps, 0, 0.07F, 0.004F, 0.122F, 0.098F, 0.074F, seed + 23);
        if (s.detail) {
            s.ellipsoid(ps, 0, 0.09F, 0.05F, 0.07F, 0.05F, 0.03F, seed + 24);
        }

        // the cloak: panels hung from the shoulders down the back, nearly to the ground, rippling as he walks
        s.material(m.cloth(), 1, 1, 1);
        int panels = s.detail ? 7 : 4;
        for (int i = 0; i < panels; i++) {
            float f = (i - (panels - 1) / 2.0F) / ((panels - 1) / 2.0F);
            ps.pushPose();
            ps.translate(f * 0.12F, 0.15F, -0.07F - (1 - f * f) * 0.015F);
            float ripple = 0.05F * Mth.sin(time * 1.6F + i * 0.9F + (float) gait * 6.28F);
            ps.mulPose(Axis.XP.rotation(0.12F + ripple + 0.1F * Math.abs(w.swingL)));
            ps.mulPose(Axis.ZP.rotation(f * 0.08F));
            s.ellipsoid(ps, 0, -0.36F, -0.01F, 0.05F, 0.38F, 0.014F, seed + 100 + i);
            ps.popPose();
        }

        // pauldrons, then the arms; the right hand holds the staff
        s.material(m.gold(), 1, 1, 1);
        for (int side = -1; side <= 1; side += 2) {
            s.ellipsoid(ps, side * 0.13F, 0.155F, -0.004F, 0.06F, 0.038F, 0.058F, seed + 30 + side);
        }
        s.material(m.marble(), 1, 1, 1);
        arms(ps, s, w, seed, 0.15F, 0.036F, 0.029F, 0.025F, true, m, time);

        // neck, head, crown
        s.limb(ps, 0, 0.15F, 0.0F, 0, 0.21F, 0.01F, 0.034F, 0.031F, seed + 70);
        ps.translate(0, 0.21F, 0.01F);
        ps.mulPose(Axis.XP.rotation(-0.06F - 0.4F * w.k));
        s.ellipsoid(ps, 0, 0.058F, -0.004F, 0.046F, 0.062F, 0.054F, seed + 71);
        s.ellipsoid(ps, 0, 0.034F, 0.021F, 0.039F, 0.05F, 0.038F, seed + 72);
        s.ellipsoid(ps, 0, 0.004F, 0.024F, 0.031F, 0.022F, 0.031F, seed + 73);
        s.material(m.gold(), 1, 1, 1);
        // the band of the crown and its tall points, the front one tallest
        s.ellipsoid(ps, 0, 0.098F, -0.002F, 0.052F, 0.014F, 0.058F, seed + 110);
        int points = s.detail ? 9 : 5;
        for (int i = 0; i < points; i++) {
            float ang = (float) (i / (double) points * Math.PI * 2);
            float x = Mth.sin(ang) * 0.048F, z = Mth.cos(ang) * 0.054F - 0.002F;
            float tall = 0.05F + 0.035F * Math.max(0, Mth.cos(ang));
            s.limb(ps, x, 0.1F, z, x * 1.25F, 0.1F + tall, z * 1.25F, 0.011F, 0.003F, seed + 120 + i);
        }
        // a jewel at the front of the crown, and the eyes: white-gold
        burn(ps, m.glow(), 0, 0.105F, 0.062F, 0.009F, 0.009F, 0.006F, 1.0F, 0.95F, 0.75F);
        for (int side = -1; side <= 1; side += 2) {
            burn(ps, m.glow(), side * 0.017F, 0.05F, 0.057F, 0.011F, 0.004F, 0.004F, 1.0F, 0.95F, 0.8F);
        }
        ps.popPose();
        ps.popPose();
    }

    // ------------------------------------------------------------------------------------------------ shared parts

    private static void legs(PoseStack ps, Sculpt s, Walk w, int seed, float hip, float thigh, float calf) {
        for (int side = -1; side <= 1; side += 2) {
            float swing = side < 0 ? w.swingR : w.swingL;
            float knee = side < 0 ? w.kneeR : w.kneeL;
            ps.pushPose();
            ps.translate(side * 0.058F, hip, 0);
            ps.mulPose(Axis.XP.rotation(-swing));
            ps.mulPose(Axis.ZP.rotation(side * 0.025F));
            // thigh: heavy at the hip, slim at the knee
            s.limb(ps, 0, 0.0F, 0, 0, -0.235F, 0.004F, thigh, thigh * 0.62F, seed + side * 3);
            ps.translate(0, -0.235F, 0.004F);
            if (s.detail) {
                s.ellipsoid(ps, 0, 0.004F, 0.022F, thigh * 0.45F, thigh * 0.5F, thigh * 0.4F, seed + 12);
            }
            ps.mulPose(Axis.XP.rotation(knee));
            // calf: the muscle high at the back, then the slim shin to the ankle
            s.limb(ps, 0, 0, 0, 0, -0.225F, -0.004F, calf, calf * 0.55F, seed + side * 5);
            if (s.detail) {
                s.ellipsoid(ps, 0, -0.07F, -0.014F, calf * 0.85F, calf * 1.5F, calf * 0.8F, seed + 14);
            }
            ps.translate(0, -0.232F, -0.004F);
            ps.mulPose(Axis.XP.rotation(-knee * 0.55F + swing * 0.35F));
            // the foot
            s.ellipsoid(ps, 0, -0.014F, 0.03F, 0.03F, 0.02F, 0.068F, seed + side * 7);
            ps.popPose();
        }
    }

    private static void arms(PoseStack ps, Sculpt s, Walk w, int seed, float shoulderY, float upper, float fore, float hand, boolean staff, Materials m,
            float time) {
        for (int side = -1; side <= 1; side += 2) {
            boolean holds = staff && side > 0;
            float swing = (side < 0 ? w.armR : w.armL) * (holds ? 0.25F : 1);
            ps.pushPose();
            ps.translate(side * 0.135F, shoulderY, -0.004F);
            // the deltoid
            s.ellipsoid(ps, side * 0.008F, 0.004F, 0, upper * 1.25F, upper * 1.15F, upper * 1.2F, seed + 50 + side);
            float raise = w.k * 2.4F;
            ps.mulPose(Axis.XP.rotation(-swing - raise - (holds ? 0.35F : 0)));
            ps.mulPose(Axis.ZP.rotation(side * (0.1F + 0.22F * w.k)));
            s.limb(ps, 0, 0, 0, 0, -0.17F, 0.004F, upper, upper * 0.72F, seed + 52 + side);
            ps.translate(0, -0.17F, 0.004F);
            float bend = 0.2F + 0.16F * Math.max(0, side < 0 ? -w.lag : w.lag) + 0.5F * w.k + (holds ? 0.9F : 0);
            ps.mulPose(Axis.XP.rotation(-bend));
            s.limb(ps, 0, 0, 0, 0, -0.155F, 0.006F, fore, fore * 0.7F, seed + 56 + side);
            ps.translate(0, -0.162F, 0.006F);
            if (s.detail && !staff) {
                // a bronze band at the wrist
                s.ellipsoid(ps, 0, 0.022F, 0, fore * 0.95F, 0.014F, fore * 0.92F, seed + 57);
            }
            // the hand: palm, fingers together, thumb
            s.ellipsoid(ps, 0, -0.03F, 0.003F, hand * 0.9F, hand * 1.35F, hand * 0.55F, seed + 60 + side);
            if (s.detail) {
                s.ellipsoid(ps, side * -0.012F, -0.025F, 0.016F, hand * 0.32F, hand * 0.8F, hand * 0.32F, seed + 62 + side);
            }
            if (holds) {
                // the staff: a long gold shaft through the fist, a ring and a sphere of light at its head
                VertexConsumer was = s.surface;
                float r0 = s.r, g0 = s.g, b0 = s.b;
                s.material(m.gold(), 1, 1, 1);
                ps.pushPose();
                ps.translate(0, -0.035F, 0.004F);
                // undo the arm's own bend so the staff stands upright beside him
                float upright = bend + swing + raise + 0.35F;
                ps.mulPose(Axis.XP.rotation(upright));
                ps.mulPose(Axis.ZP.rotation(-side * (0.1F + 0.22F * w.k)));
                s.limb(ps, 0, -0.42F, 0, 0, 0.5F, 0, 0.011F, 0.011F, seed + 130);
                // a ring of gold cradling the light
                s.ellipsoid(ps, 0, 0.53F, 0, 0.04F, 0.012F, 0.04F, seed + 131);
                ps.popPose();
                burnAt(ps, m.glow(), upright, -side * (0.1F + 0.22F * w.k), time);
                s.material(was, r0, g0, b0);
            }
            ps.popPose();
        }
    }

    /** The staff's orb, burning: drawn in the staff's own frame. */
    private static void burnAt(PoseStack ps, VertexConsumer glow, float tilt, float roll, float time) {
        ps.pushPose();
        ps.translate(0, -0.035F, 0.004F);
        ps.mulPose(Axis.XP.rotation(tilt));
        ps.mulPose(Axis.ZP.rotation(roll));
        float pulse = 0.85F + 0.15F * Mth.sin(time * 3.0F);
        burn(ps, glow, 0, 0.56F, 0, 0.032F * pulse, 0.032F * pulse, 0.032F * pulse, 1.0F, 0.95F, 0.78F);
        ps.popPose();
    }

    /** A shape that only burns, in the additive glow pass. */
    private static void burn(PoseStack ps, VertexConsumer glow, float cx, float cy, float cz, float rx, float ry, float rz, float r, float g, float b) {
        if (glow == null) {
            return;
        }
        Matrix4f mat = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int lat = 5, lon = 8;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int[][] q = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int[] v : q) {
                    double th = Math.PI * v[0] / lat - Math.PI / 2, ph = Math.PI * 2 * v[1] / lon;
                    float ux = (float) (Math.cos(th) * Math.cos(ph)), uy = (float) Math.sin(th), uz = (float) (Math.cos(th) * Math.sin(ph));
                    glow.vertex(mat, cx + ux * rx, cy + uy * ry, cz + uz * rz).color(r, g, b, 1).uv(0.5F, 0.5F)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, ux, uy, uz).endVertex();
                }
            }
        }
    }
}
