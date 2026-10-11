package com.firestar.worldbreaker.client.giant;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Smooth sculpted forms: textured ellipsoids with true normals. The material (the buffer the surface goes to) can be
 * switched between forms, so one body can be bronze here and gold there. Near the camera each form also gets an
 * inverted-hull ink outline. Tessellation is chosen per giant by distance: a near giant is round, a far one costs
 * almost nothing.
 */
final class Sculpt {
    /** Units of the body covered by one repeat of a material texture. */
    private static final float TILE = 0.2F;
    private static final Map<Integer, float[][]> UNITS = new HashMap<>();
    private final VertexConsumer ink;
    private final int light;
    private final int lat, lon;
    private final float[][] unit;
    /** The surface the next forms are drawn in. */
    VertexConsumer surface;
    float inkWidth;
    float r = 1, g = 1, b = 1;
    /** Below this many blocks a form is not worth drawing (fingers on a far giant). */
    final boolean detail;
    private final Vector3f p = new Vector3f(), nn = new Vector3f();

    Sculpt(VertexConsumer surface, VertexConsumer ink, int light, int lat, int lon, boolean detail) {
        this.surface = surface;
        this.ink = ink;
        this.light = light;
        this.lat = lat;
        this.lon = lon;
        this.detail = detail;
        this.unit = UNITS.computeIfAbsent(lat * 1000 + lon, k -> {
            float[][] u = new float[(lat + 1) * (lon + 1)][];
            for (int i = 0; i <= lat; i++) {
                double th = Math.PI * i / lat - Math.PI / 2;
                for (int j = 0; j <= lon; j++) {
                    double ph = Math.PI * 2 * j / lon;
                    u[i * (lon + 1) + j] = new float[]{(float) (Math.cos(th) * Math.cos(ph)), (float) Math.sin(th), (float) (Math.cos(th) * Math.sin(ph))};
                }
            }
            return u;
        });
    }

    Sculpt material(VertexConsumer surface, float r, float g, float b) {
        this.surface = surface;
        this.r = r;
        this.g = g;
        this.b = b;
        return this;
    }

    /** A smooth form centred at c with radii (rx, ry, rz) in the current pose; {@code seed} shifts where the texture falls. */
    void ellipsoid(PoseStack ps, float cx, float cy, float cz, float rx, float ry, float rz, int seed) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float su = (float) (Math.PI * (rx + rz)) / TILE, sv = (float) (Math.PI * ry) / TILE;
        float ou = (seed * 0.137F) % 1, ov = (seed * 0.291F) % 1;
        int row = lon + 1;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int k0 = i * row + j, k1 = k0 + row, k2 = k1 + 1, k3 = k0 + 1;
                vert(m, n, k0, cx, cy, cz, rx, ry, rz, ou, ov, su, sv);
                vert(m, n, k1, cx, cy, cz, rx, ry, rz, ou, ov, su, sv);
                vert(m, n, k2, cx, cy, cz, rx, ry, rz, ou, ov, su, sv);
                vert(m, n, k3, cx, cy, cz, rx, ry, rz, ou, ov, su, sv);
                if (ink != null && inkWidth > 0) {
                    // the outline: the same form a little larger, wound inside out so only its far side shows
                    hull(m, n, k3, cx, cy, cz, rx, ry, rz);
                    hull(m, n, k2, cx, cy, cz, rx, ry, rz);
                    hull(m, n, k1, cx, cy, cz, rx, ry, rz);
                    hull(m, n, k0, cx, cy, cz, rx, ry, rz);
                }
            }
        }
    }

    private void vert(Matrix4f m, Matrix3f n, int k, float cx, float cy, float cz, float rx, float ry, float rz, float ou, float ov, float su, float sv) {
        float[] u = unit[k];
        int ii = k / (lon + 1), jj = k % (lon + 1);
        float nx = u[0] / rx, ny = u[1] / ry, nz = u[2] / rz;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        surface.vertex(m, cx + u[0] * rx, cy + u[1] * ry, cz + u[2] * rz).color(r, g, b, 1).uv(ou + su * jj / lon, ov + sv * ii / lat)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx / l, ny / l, nz / l).endVertex();
    }

    private void hull(Matrix4f m, Matrix3f n, int k, float cx, float cy, float cz, float rx, float ry, float rz) {
        float[] u = unit[k];
        m.transformPosition(cx + u[0] * (rx + inkWidth), cy + u[1] * (ry + inkWidth), cz + u[2] * (rz + inkWidth), p);
        n.transform(u[0], u[1], u[2], nn).normalize();
        ink.vertex(p.x, p.y, p.z).color(0.03F, 0.025F, 0.02F, 1).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(nn.x, nn.y, nn.z).endVertex();
    }

    /** A tapering limb from a to b with end radii ra and rb: an ellipsoid stretched along the bone. */
    void limb(PoseStack ps, float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int seed) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F) {
            return;
        }
        ps.pushPose();
        ps.translate((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2);
        ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(dx, dy, dz).normalize()));
        if (detail) {
            // two overlapping forms: the muscle belly near the root, the slimmer end toward the joint
            float h = len / 2;
            ellipsoid(ps, 0, -h + len * 0.36F, 0, ra, len * 0.38F + ra * 0.3F, ra * 0.9F, seed);
            ellipsoid(ps, 0, -h + len * 0.72F, 0, rb, len * 0.3F + rb * 0.4F, rb * 0.92F, seed + 1);
        } else {
            float r = (ra + rb) / 2;
            ellipsoid(ps, 0, 0, 0, r, len / 2 + r * 0.45F, r * 0.92F, seed);
        }
        ps.popPose();
    }
}
