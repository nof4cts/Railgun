package com.railgun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The railgun, built entirely in code. Canonical frame: grip at origin, barrel toward -Z, up = +Y.
 * Length is roughly 1.4 blocks; RailgunRenderer scales it per display context.
 */
public final class GunModel {
    private static final float[] DARK = {.10f, .11f, .14f, 1f}, MID = {.19f, .21f, .26f, 1f},
            WHITE = {.90f, .92f, .96f, 1f}, CHROME = {.78f, .83f, .90f, 1f},
            CYAN = {.25f, .85f, 1f, 1f}, ICE = {.85f, 1f, 1f, 1f}, ORANGE = {1f, .42f, .08f, 1f};
    private static final float[] COIL_Z = {-.40f, -.47f, -.52f, -.68f, -.78f, -.88f};

    private static final Mesh BODY = new Mesh();
    private static final Mesh COIL = new Mesh();
    private static final Mesh GLOW = buildGlow(CYAN);
    private static final Mesh GLOW_HOT = buildGlow(ICE);
    private static final Mesh COIL_GLOW = new Mesh();
    private static final Mesh HEAT = new Mesh();
    private static final Mesh SPHERE = new Mesh();

    static {
        // ---- receiver + rear housing + stock pad
        BODY.tube(.12f, -.34f, .082f, .092f, .082f, .092f, 4f, 36, MID, true, true);
        BODY.tube(.40f, .12f, .050f, .060f, .082f, .092f, 4f, 36, DARK, true, false);
        BODY.tube(.44f, .40f, .056f, .068f, .056f, .068f, 4f, 28, WHITE, true, true);
        // ---- spine rail + optic
        BODY.push().translate(0, .100f, -.05f).tube(.30f, -.55f, .028f, .016f, .028f, .016f, 4f, 20, CHROME, true, true).pop();
        BODY.push().translate(0, .134f, -.12f).tube(.09f, -.09f, .025f, .025f, .025f, .025f, 3f, 24, DARK, true, true).pop();
        BODY.push().translate(0, .134f, -.12f).tube(-.09f, -.14f, .032f, .032f, .027f, .027f, 2f, 24, MID, false, false).pop();
        // ---- grip + trigger
        BODY.push().translate(0, -.080f, .075f).rotX(78f).tube(0f, .25f, .034f, .040f, .030f, .036f, 3f, 24, DARK, true, true).pop();
        BODY.push().translate(0, -.095f, -.03f).rotX(70f).tube(0f, .06f, .007f, .010f, .006f, .008f, 2f, 10, CHROME, true, true).pop();
        // ---- energy cell
        BODY.push().translate(0, -.10f, -.13f).tube(.16f, -.16f, .052f, .036f, .052f, .036f, 4f, 28, WHITE, true, true).pop();
        // ---- handguard
        BODY.tube(-.36f, -.56f, .078f, .084f, .064f, .072f, 4f, 36, WHITE, false, true);
        BODY.tube(-.56f, -.585f, .074f, .080f, .074f, .080f, 4f, 36, DARK, true, true);
        // ---- twin conductor rails + cross bars + yoke
        for (int s = -1; s <= 1; s += 2) {
            BODY.push().translate(s * .046f, 0, 0).tube(-.34f, -.93f, .017f, .017f, .017f, .017f, 2f, 20, CHROME, false, true).pop();
        }
        for (float z : new float[]{-.64f, -.74f, -.84f}) {
            BODY.tube(z, z - .03f, .066f, .016f, .066f, .016f, 4f, 20, DARK, true, true);
        }
        BODY.tube(-.90f, -.95f, .072f, .030f, .072f, .030f, 4f, 24, MID, true, true);

        // ---- spinning coil ring (drawn at several Z positions, rotated each frame)
        COIL.tube(.009f, -.009f, .091f, .091f, .091f, .091f, 2f, 40, MID, false, false);
        for (int k = 0; k < 8; k++) {
            COIL.push().rotZ(k * 45f).translate(.091f, 0, 0).tube(.020f, -.020f, .0125f, .0125f, .0125f, .0125f, 2f, 10, CHROME, true, true).pop();
            COIL_GLOW.push().rotZ(k * 45f).translate(.091f, 0, 0).tube(.022f, -.022f, .0075f, .0075f, .0075f, .0075f, 2f, 8, CYAN, true, true).pop();
        }

        // ---- rails heat (orange overlay after firing)
        for (int s = -1; s <= 1; s += 2) {
            HEAT.push().translate(s * .046f, 0, 0).tube(-.585f, -.93f, .0185f, .0185f, .0185f, .0185f, 2f, 16, ORANGE, false, true).pop();
        }
        SPHERE.sphere(0, 0, 0, 1f, 24, 14, new float[]{1f, 1f, 1f, 1f});
    }

    private static Mesh buildGlow(float[] c) {
        Mesh g = new Mesh();
        g.tube(-.34f, -.95f, .010f, .010f, .010f, .010f, 2f, 14, c, false, false);                    // bore
        g.tube(-.34f, -.36f, .070f, .078f, .070f, .078f, 4f, 32, c, false, false);                    // reactor gap
        for (int s = -1; s <= 1; s += 2) {
            g.push().translate(s * .0835f, .025f, 0).tube(.10f, -.30f, .0045f, .0045f, .0045f, .0045f, 2f, 8, c, true, true).pop();
        }
        g.push().translate(0, -.10f, -.13f).tube(-.02f, .02f, .0535f, .0375f, .0535f, .0375f, 4f, 28, c, false, false).pop(); // cell band
        g.push().translate(0, .134f, -.12f).tube(-.09f, -.093f, .019f, .019f, .019f, .019f, 2f, 18, c, true, true).pop();    // optic lens
        g.tube(-.945f, -.955f, .030f, .0f + .014f, .030f, .014f, 4f, 20, c, true, true);                                      // yoke slit
        return g;
    }

    public static void render(PoseStack ps, MultiBufferSource buf, int light, int overlay, float charge, float heat, float time) {
        // ---------- solid, lit geometry
        VertexConsumer lit = buf.getBuffer(RenderType.entityCutoutNoCull(FxRenderTypes.WHITE));
        BODY.drawLit(lit, ps.last(), light, overlay);
        float spin = time * (3f + charge * 55f);
        for (int i = 0; i < COIL_Z.length; i++) {
            ps.pushPose();
            ps.translate(0, 0, COIL_Z[i]);
            ps.mulPose(Axis.ZP.rotationDegrees((i % 2 == 0 ? spin : -spin * 1.3f) + i * 17f));
            COIL.drawLit(lit, ps.last(), light, overlay);
            ps.popPose();
        }

        // ---------- additive energy
        VertexConsumer glow = buf.getBuffer(FxRenderTypes.GLOW);
        float pulse = 0.82f + 0.18f * Mth.sin(time * 0.55f + charge * 6f);
        float k = (0.30f + charge * 0.95f) * pulse;
        GLOW.drawGlow(glow, ps.last(), k, 0f, 1, 1, 1);
        GLOW.drawGlow(glow, ps.last(), k * 0.45f, 0.010f, 1, 1, 1);
        GLOW.drawGlow(glow, ps.last(), k * 0.18f, 0.026f, 1, 1, 1);
        float hot = charge * charge * charge;
        if (hot > 0.01f) {
            GLOW_HOT.drawGlow(glow, ps.last(), hot * 0.8f, 0f, 1, 1, 1);
            GLOW_HOT.drawGlow(glow, ps.last(), hot * 0.35f, 0.012f, 1, 1, 1);
        }
        for (int i = 0; i < COIL_Z.length; i++) {
            ps.pushPose();
            ps.translate(0, 0, COIL_Z[i]);
            ps.mulPose(Axis.ZP.rotationDegrees((i % 2 == 0 ? spin : -spin * 1.3f) + i * 17f));
            COIL_GLOW.drawGlow(glow, ps.last(), k * 0.9f, 0f, 1, 1, 1);
            COIL_GLOW.drawGlow(glow, ps.last(), k * 0.3f, 0.012f, 1, 1, 1);
            ps.popPose();
        }
        if (heat > 0.01f) {
            HEAT.drawGlow(glow, ps.last(), heat, 0f, 1, 1, 1);
            HEAT.drawGlow(glow, ps.last(), heat * 0.45f, 0.012f, 1, 1, 1);
            HEAT.drawGlow(glow, ps.last(), heat * 0.18f, 0.03f, 1, 1, 1);
        }

        // ---------- plasma orb forming at the muzzle + arcing between the rails
        if (charge > 0.04f) {
            float r = (0.012f + 0.05f * charge) * pulse;
            ps.pushPose();
            ps.translate(0, 0, -0.96f);
            layer(ps, glow, r, 1f, 1f, 1f, 1f);
            layer(ps, glow, r * 1.8f, 0.45f, 0.5f, 0.9f, 1f);
            layer(ps, glow, r * 3.2f, 0.18f, 0.2f, 0.5f, 1f);
            ps.popPose();

            int arcs = 1 + (int) (charge * 5f);
            for (int a = 0; a < arcs; a++) {
                Random rnd = new Random((long) (time * 0.9f) * 31L + a * 977L);
                float z = -0.40f - rnd.nextFloat() * 0.5f;
                float[] pts = new float[3 * 7];
                for (int j = 0; j < 7; j++) {
                    float f = j / 6f;
                    pts[j * 3] = Mth.lerp(f, -.046f, .046f);
                    pts[j * 3 + 1] = (j == 0 || j == 6) ? 0f : (rnd.nextFloat() - .5f) * .05f;
                    pts[j * 3 + 2] = z + ((j == 0 || j == 6) ? 0f : (rnd.nextFloat() - .5f) * .05f);
                }
                Draw.ribbon(glow, ps.last().pose(), pts, 7, .018f, .018f, .5f, .9f, 1f, .55f * charge, true);
                Draw.ribbon(glow, ps.last().pose(), pts, 7, .006f, .006f, 1f, 1f, 1f, .95f * charge, true);
            }
        }
    }

    private static void layer(PoseStack ps, VertexConsumer vc, float s, float a, float r, float g, float b) {
        ps.pushPose();
        ps.scale(s, s, s);
        SPHERE.drawGlow(vc, ps.last(), a, 0f, r, g, b);
        ps.popPose();
    }

    private GunModel() {}
}
