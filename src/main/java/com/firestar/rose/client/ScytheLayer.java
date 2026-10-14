package com.firestar.rose.client;

import com.firestar.rose.RoseMod;
import com.firestar.rose.client.gfx.Frame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The reaper's scythe, in his right hand while ascended: an obsidian shaft banded in rose gold, its engraving glowing,
 * and a blade of pure rose energy. The shaft is drawn here with the player; the blade's placement is remembered and
 * drawn with the rest of the light (so it blooms) by the {@link com.firestar.rose.client.gfx.Canvas}.
 */
public final class ScytheLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    static final ResourceLocation TEX = RoseMod.id("textures/fx/scythe.png");
    static final ResourceLocation GLOW = RoseMod.id("textures/fx/scythe_glow.png");
    /** The shaft in the hand's frame: from the butt (z = +0.55) to the head (z = -1.8). */
    static final float BUTT = 0.55F, HEAD = -1.8F, RADIUS = 0.042F;
    private static final List<Matrix4f> HELD = new ArrayList<>();
    private static final List<float[]> HELD_K = new ArrayList<>();

    public ScytheLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    /** How much of the scythe is there (summoned with the ascension, gone when it ends; scaled up during the Last Bloom). */
    static float presence(AbstractClientPlayer p, float partial) {
        return RoseForms.scythe(p, partial);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt, float partial,
                       float age, float yaw, float pitch) {
        float k = presence(p, partial);
        if (k < 0.02F || !p.getMainHandItem().isEmpty() || p.isInvisible()) {
            return;
        }
        ps.pushPose();
        getParentModel().rightArm.translateAndRotate(ps);
        hand(ps, RoseChoreo.scytheGrow(p, partial));
        drawShaft(ps, buf, light, k);
        HELD.add(new Matrix4f(ps.last().pose()));
        // grown past the clouds, the scythe's own aura gives way to the light of the Last Bloom
        float grow = RoseChoreo.scytheGrow(p, partial);
        HELD_K.add(new float[]{k, RoseChoreo.bladeHeat(p, partial), k * Math.max(0, 1 - (grow - 1) / 0.6F)});
        ps.popPose();
    }

    /**
     * From the arm's pivot to the grip in the fist; the shaft runs along z (its head toward -z). Tipped 42 degrees up
     * the arm so that in the reaper's stance (arm raised 38 degrees) the shaft stands upright through the fist, butt
     * below the hand, the blade over the head reaching forward.
     */
    static void hand(PoseStack ps, float grow) {
        ps.translate(-0.0625F, 0.62F, -0.02F);
        // leaned in against the arm's outward roll so the shaft stands beside the body rather than off to the side
        ps.mulPose(Axis.ZP.rotationDegrees(20));
        ps.mulPose(Axis.XP.rotationDegrees(-42));
        if (grow != 1.0F) {
            ps.scale(grow, grow, grow);
        }
    }

    /** The shaft: an eight-sided rod, rose-gold caps at each end and a crown under the blade. */
    public static void drawShaft(PoseStack ps, MultiBufferSource buf, int light, float k) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        float len = BUTT - HEAD;
        float head = BUTT - len * k;
        rod(vc, m, n, light, RADIUS, BUTT, head, 0, 0.5F, 0, 1, 1);
        // caps and crown (rose gold, right half of the texture)
        rod(vc, m, n, light, RADIUS * 1.6F, BUTT + 0.03F, BUTT - 0.05F, 0.5F, 1, 0.0F, 0.1F, 1);
        if (k > 0.95F) {
            rod(vc, m, n, light, RADIUS * 1.5F, HEAD + 0.12F, HEAD - 0.02F, 0.5F, 1, 0.2F, 0.4F, 1);
            rod(vc, m, n, light, RADIUS * 2.3F, HEAD + 0.02F, HEAD - 0.06F, 0.5F, 1, 0.3F, 0.38F, 1);
            rod(vc, m, n, light, RADIUS * 1.3F, -0.42F, -0.48F, 0.5F, 1, 0.6F, 0.7F, 1);
        }
        VertexConsumer g = buf.getBuffer(RenderType.eyes(GLOW));
        rod(g, m, n, LightTexture.FULL_BRIGHT, RADIUS * 1.01F, BUTT, head, 0, 0.5F, 0, 1, k);
        if (k > 0.95F) {
            rod(g, m, n, LightTexture.FULL_BRIGHT, RADIUS * 2.31F, HEAD + 0.02F, HEAD - 0.06F, 0.5F, 1, 0.3F, 0.38F, 1);
        }
    }

    private static void rod(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float r, float z0, float z1, float u0, float u1, float v0,
                            float v1, float alpha) {
        int sides = 8;
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2 / sides, a1 = (i + 1) * Math.PI * 2 / sides;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            float nx = (float) Math.cos((a0 + a1) / 2), ny = (float) Math.sin((a0 + a1) / 2);
            float ua = u0 + (u1 - u0) * i / sides, ub = u0 + (u1 - u0) * (i + 1) / sides;
            vert(vc, m, n, x0, y0, z0, ua, v1, nx, ny, light, alpha);
            vert(vc, m, n, x1, y1, z0, ub, v1, nx, ny, light, alpha);
            vert(vc, m, n, x1, y1, z1, ub, v0, nx, ny, light, alpha);
            vert(vc, m, n, x0, y0, z1, ua, v0, nx, ny, light, alpha);
        }
        // end caps
        for (float z : new float[]{z0, z1}) {
            for (int i = 0; i < sides; i += 2) {
                double a0 = i * Math.PI * 2 / sides, a1 = (i + 1) * Math.PI * 2 / sides, a2 = (i + 2) * Math.PI * 2 / sides;
                float nz = z == z0 ? 1 : -1;
                vertN(vc, m, n, 0, 0, z, u0, v0, 0, 0, nz, light, alpha);
                vertN(vc, m, n, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, z, u0, v0, 0, 0, nz, light, alpha);
                vertN(vc, m, n, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, z, u0, v0, 0, 0, nz, light, alpha);
                vertN(vc, m, n, (float) Math.cos(a2) * r, (float) Math.sin(a2) * r, z, u0, v0, 0, 0, nz, light, alpha);
            }
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float nx, float ny, int light,
                             float a) {
        vertN(vc, m, n, x, y, z, u, v, nx, ny, 0, light, a);
    }

    private static void vertN(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float nx, float ny, float nz,
                              int light, float a) {
        vc.vertex(m, x, y, z).color(1F, 1F, 1F, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    // ------------------------------------------------------------------------------------------------ the blade

    /**
     * The blade, in the hand's frame: a crescent rising from the head of the shaft and sweeping back down beside it.
     * Points are mapped through {@code m} (hand -> camera view) and back into the world.
     */
    public static void blade(Frame f, Matrix4f m, Matrix4f invView, float k, float heat) {
        if (k < 0.6F) {
            return;
        }
        float grow = Math.min(1, (k - 0.6F) / 0.4F);
        Vec3 tip = map(m, invView, f.cam, 0, 0, HEAD);
        Vec3 s = map(m, invView, f.cam, 0, 0, HEAD - 1).subtract(tip).normalize();
        Vec3 b = map(m, invView, f.cam, 0, 1, HEAD).subtract(tip).normalize();
        double handScale = map(m, invView, f.cam, 0, 0, HEAD - 1).distanceTo(tip);
        // a long, nearly straight edge: it leaves the head square to the shaft and only sweeps back gently at the point
        double R = 1.55 * handScale;
        Vec3 c = tip.subtract(s.scale(R));
        f.bladeInk(c, s, b, R - 0.16 * handScale, -4, -4 + 62 * grow, 0.12 * handScale, 0.3F * grow);
        f.blade(c, s, b, R, -4, -4 + 62 * grow, 0.27 * handScale, grow, heat);
    }

    private static Vec3 map(Matrix4f m, Matrix4f invView, Vec3 cam, float x, float y, float z) {
        Vector4f v = new Vector4f(x, y, z, 1);
        m.transform(v);
        invView.transform(v);
        return new Vec3(v.x + cam.x, v.y + cam.y, v.z + cam.z);
    }

    /** Called by the canvas once a frame: every blade seen this frame, into the light. */
    public static void blades(Frame f, PoseStack view) {
        if (f != null && !HELD.isEmpty()) {
            Matrix4f inv = new Matrix4f(view.last().pose()).invert();
            for (int i = 0; i < HELD.size(); i++) {
                blade(f, HELD.get(i), inv, HELD_K.get(i)[0], HELD_K.get(i)[1]);
            }
        }
        // the scythe's own aura is drawn from the same placements
        AURA_M.clear();
        AURA_K.clear();
        AURA_M.addAll(HELD);
        for (float[] k : HELD_K) {
            AURA_K.add(k[2]);
        }
        HELD.clear();
        HELD_K.clear();
    }

    /** Every scythe seen this frame (hand -> view matrix) and how present it is, for its aura. */
    public static final List<Matrix4f> AURA_M = new ArrayList<>();
    public static final List<Float> AURA_K = new ArrayList<>();

    /** The world point of the blade's edge mid-way (for sparks off the edge), from the last frame, or null. */
    public static Vec3 vec(Vector3f v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
