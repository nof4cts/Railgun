package com.firestar.mih.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Made in Heaven's own light, in place of every vanilla particle: sparks that streak along their velocity, soft round
 * flares, expanding shock rings, and YBA-style light trails that follow whatever moves too fast (the user, the Stand's
 * fists). All additive, all smooth gradients, simulated per rendered frame so they never stutter at tick rate.
 */
public final class Light {
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final List<Flare> FLARES = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final Map<String, Trail> TRAILS = new HashMap<>();
    private static final Random R = new Random();
    private static long last = System.nanoTime();

    private Light() {}

    // ------------------------------------------------------------------------------------------------ kinds

    static final class Spark {
        Vec3 pos, vel;
        float life, age, width, stretch, drag;
        int rgb;
    }

    static final class Flare {
        Vec3 pos;
        float size, grow, life, age;
        int rgb;
    }

    static final class Ring {
        Vec3 pos, normal;
        float r0, r1, width, life, age;
        int rgb;
    }

    static final class Trail {
        final Deque<double[]> pts = new ArrayDeque<>();
        int rgb;
        float width;
        float keep;
        long touched;
    }

    // ------------------------------------------------------------------------------------------------ emitters

    public static void spark(Vec3 pos, Vec3 vel, float life, int rgb, float width) {
        Spark s = new Spark();
        s.pos = pos;
        s.vel = vel;
        s.life = life;
        s.rgb = rgb;
        s.width = width;
        s.stretch = 0.045F;
        s.drag = 3.5F;
        SPARKS.add(s);
    }

    /** Sparks flung out from a point in every direction (a hit, a burst). Speed in blocks per second. */
    public static void burst(Vec3 at, int n, float speed, int rgb, float life, float width) {
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).normalize();
            spark(at, d.scale(speed * (0.35F + R.nextFloat() * 0.9F)), life * (0.6F + R.nextFloat() * 0.6F), rgb, width * (0.6F + R.nextFloat() * 0.7F));
        }
    }

    /** Sparks thrown mostly one way, in a cone round {@code dir}. */
    public static void spray(Vec3 at, Vec3 dir, float cone, int n, float speed, int rgb, float life, float width) {
        Vec3 d0 = dir.normalize();
        for (int i = 0; i < n; i++) {
            Vec3 d = d0.add(new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).scale(cone)).normalize();
            spark(at, d.scale(speed * (0.4F + R.nextFloat() * 0.8F)), life * (0.6F + R.nextFloat() * 0.6F), rgb, width);
        }
    }

    public static void flare(Vec3 at, float size, int rgb, float life) {
        Flare f = new Flare();
        f.pos = at;
        f.size = size;
        f.grow = 0.6F;
        f.rgb = rgb;
        f.life = life;
        FLARES.add(f);
    }

    public static void ring(Vec3 at, Vec3 normal, float r0, float r1, float width, int rgb, float life) {
        Ring r = new Ring();
        r.pos = at;
        r.normal = normal.normalize();
        r.r0 = r0;
        r.r1 = r1;
        r.width = width;
        r.rgb = rgb;
        r.life = life;
        RINGS.add(r);
    }

    /** The standard hit: a white flare, a ring, and a spray of sparks in the colour given. */
    public static void hit(Vec3 at, Vec3 dir, int rgb, float power) {
        flare(at, 0.9F * power, 0xFFFFFF, 0.18F);
        flare(at, 2.0F * power, rgb, 0.3F);
        ring(at, dir, 0.2F, 1.6F * power, 0.18F * power, rgb, 0.3F);
        spray(at, dir, 0.7F, (int) (14 * power), 9.0F * power, rgb, 0.35F, 0.05F);
        burst(at, (int) (6 * power), 5.0F * power, 0xFFFFFF, 0.25F, 0.04F);
    }

    /** Feed one point of a light trail; call every frame while the thing is moving too fast. */
    public static void trail(String key, Vec3 at, int rgb, float width, float keepSeconds) {
        Trail t = TRAILS.computeIfAbsent(key, k -> new Trail());
        t.rgb = rgb;
        t.width = width;
        t.keep = keepSeconds;
        long now = System.nanoTime();
        t.touched = now;
        double[] lastPt = t.pts.peekLast();
        if (lastPt == null || (at.x - lastPt[0]) * (at.x - lastPt[0]) + (at.y - lastPt[1]) * (at.y - lastPt[1]) + (at.z - lastPt[2]) * (at.z - lastPt[2]) > 0.0004) {
            t.pts.addLast(new double[]{at.x, at.y, at.z, now});
        }
    }

    public static void clear() {
        SPARKS.clear();
        FLARES.clear();
        RINGS.clear();
        TRAILS.clear();
    }

    // ------------------------------------------------------------------------------------------------ simulate + draw

    private static void step(float dt) {
        Iterator<Spark> si = SPARKS.iterator();
        while (si.hasNext()) {
            Spark s = si.next();
            s.age += dt;
            if (s.age >= s.life) {
                si.remove();
                continue;
            }
            s.pos = s.pos.add(s.vel.scale(dt));
            s.vel = s.vel.scale(Math.exp(-s.drag * dt)).add(0, -2.0 * dt, 0);
        }
        FLARES.removeIf(f -> (f.age += dt) >= f.life);
        RINGS.removeIf(r -> (r.age += dt) >= r.life);
        long now = System.nanoTime();
        Iterator<Map.Entry<String, Trail>> ti = TRAILS.entrySet().iterator();
        while (ti.hasNext()) {
            Trail t = ti.next().getValue();
            long keepNs = (long) (t.keep * 1.0E9);
            while (!t.pts.isEmpty() && now - (long) t.pts.peekFirst()[3] > keepNs) {
                t.pts.removeFirst();
            }
            if (t.pts.isEmpty() && now - t.touched > keepNs) {
                ti.remove();
            }
        }
    }

    public static boolean idle() {
        return SPARKS.isEmpty() && FLARES.isEmpty() && RINGS.isEmpty() && TRAILS.isEmpty();
    }

    /** Draws everything with the given camera view (rotation only) and camera position. */
    public static void render(PoseStack view, Vec3 cam) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1F, (nowNs - last) / 1.0E9F);
        last = nowNs;
        if (Minecraft.getInstance().isPaused()) {
            dt = 0.0F;
        }
        step(dt);
        if (idle()) {
            return;
        }
        Matrix4f m = view.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (Trail t : TRAILS.values()) {
            drawTrail(bb, m, cam, t, nowNs);
        }
        for (Spark s : SPARKS) {
            float k = 1.0F - s.age / s.life;
            Vec3 head = s.pos.subtract(cam);
            Vec3 tail = head.subtract(s.vel.scale(s.stretch + 0.01));
            streak(bb, m, tail, head, s.width * (0.4F + 0.6F * k), s.rgb, k);
            streak(bb, m, tail, head, s.width * 0.35F, 0xFFFFFF, k * 0.8F);
        }
        for (Flare f : FLARES) {
            float k = f.age / f.life;
            float a = (1.0F - k) * (1.0F - k);
            float size = f.size * (1.0F + f.grow * k);
            glow(bb, m, f.pos.subtract(cam), size, f.rgb, a);
        }
        for (Ring r : RINGS) {
            float k = r.age / r.life;
            float e = 1.0F - (float) Math.pow(1.0F - k, 3.0);
            ring(bb, m, r.pos.subtract(cam), r.normal, r.r0 + (r.r1 - r.r0) * e, r.width * (1.0F - 0.5F * k), r.rgb, (1.0F - k));
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static void v(BufferBuilder bb, Matrix4f m, Vec3 p, int rgb, float a) {
        float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
        bb.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Math.max(0.0F, Math.min(1.0F, a))).endVertex();
    }

    /** A soft line: bright along its spine, fading to nothing at both edges and both ends. */
    private static void streak(BufferBuilder bb, Matrix4f m, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-8) {
            return;
        }
        Vec3 side = d.cross(a.add(b).scale(0.5)).normalize().scale(width * 0.5);
        Vec3 mid = a.lerp(b, 0.7);
        // tail -> mid -> head, each side a triangle fan fading out
        tri(bb, m, a, mid.add(side), mid, rgb, 0.0F, 0.0F, alpha);
        tri(bb, m, a, mid, mid.subtract(side), rgb, 0.0F, alpha, 0.0F);
        tri(bb, m, mid, mid.add(side), b, rgb, alpha, 0.0F, alpha * 0.6F);
        tri(bb, m, mid, b, mid.subtract(side), rgb, alpha, alpha * 0.6F, 0.0F);
    }

    private static void tri(BufferBuilder bb, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, int rgb, float a0, float a1, float a2) {
        v(bb, m, p0, rgb, a0);
        v(bb, m, p1, rgb, a1);
        v(bb, m, p2, rgb, a2);
    }

    /** A round soft glow facing the camera: a bright core and a long falloff. */
    private static void glow(BufferBuilder bb, Matrix4f m, Vec3 c, float size, int rgb, float a) {
        if (a <= 0.003F) {
            return;
        }
        Vec3 view = c.normalize();
        Vec3 u = Math.abs(view.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = view.cross(u).normalize();
        Vec3 up = right.cross(view).normalize();
        int n = 20;
        float[] radii = {0.0F, 0.18F, 0.45F, 1.0F};
        float[] alphas = {1.0F, 0.75F, 0.25F, 0.0F};
        for (int band = 0; band < 3; band++) {
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                Vec3 d0 = right.scale(Math.cos(a0)).add(up.scale(Math.sin(a0)));
                Vec3 d1 = right.scale(Math.cos(a1)).add(up.scale(Math.sin(a1)));
                Vec3 i0 = c.add(d0.scale(size * radii[band])), i1 = c.add(d1.scale(size * radii[band]));
                Vec3 o0 = c.add(d0.scale(size * radii[band + 1])), o1 = c.add(d1.scale(size * radii[band + 1]));
                tri(bb, m, i0, o0, o1, rgb, a * alphas[band], a * alphas[band + 1], a * alphas[band + 1]);
                tri(bb, m, i0, o1, i1, rgb, a * alphas[band], a * alphas[band + 1], a * alphas[band]);
            }
        }
    }

    /** A shock ring in the plane with normal n, soft on both edges. */
    private static void ring(BufferBuilder bb, Matrix4f m, Vec3 c, Vec3 n, float radius, float width, int rgb, float a) {
        if (a <= 0.003F || radius <= 0) {
            return;
        }
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0).cross(n).normalize() : new Vec3(0, 1, 0).cross(n).normalize();
        Vec3 w = n.cross(u);
        int segs = 56;
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2 / segs, a1 = (i + 1) * Math.PI * 2 / segs;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0)));
            Vec3 d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            Vec3 in0 = c.add(d0.scale(Math.max(0, radius - width))), in1 = c.add(d1.scale(Math.max(0, radius - width)));
            Vec3 mid0 = c.add(d0.scale(radius)), mid1 = c.add(d1.scale(radius));
            Vec3 out0 = c.add(d0.scale(radius + width * 0.4)), out1 = c.add(d1.scale(radius + width * 0.4));
            tri(bb, m, in0, mid0, mid1, rgb, 0.0F, a, a);
            tri(bb, m, in0, mid1, in1, rgb, 0.0F, a, 0.0F);
            tri(bb, m, mid0, out0, out1, rgb, a, 0.0F, 0.0F);
            tri(bb, m, mid0, out1, mid1, rgb, a, 0.0F, a);
        }
    }

    /** A trail: a camera-facing ribbon through the recent points, wide and bright at the head, gone at the tail. */
    private static void drawTrail(BufferBuilder bb, Matrix4f m, Vec3 cam, Trail t, long now) {
        int n = t.pts.size();
        if (n < 2) {
            return;
        }
        double[][] p = t.pts.toArray(new double[0][]);
        double keepNs = t.keep * 1.0E9;
        for (int pass = 0; pass < 2; pass++) {
            int rgb = pass == 0 ? t.rgb : 0xFFFFFF;
            float wscale = pass == 0 ? 1.0F : 0.32F;
            for (int i = 0; i < n - 1; i++) {
                Vec3 a = new Vec3(p[i][0], p[i][1], p[i][2]).subtract(cam);
                Vec3 b = new Vec3(p[i + 1][0], p[i + 1][1], p[i + 1][2]).subtract(cam);
                Vec3 d = b.subtract(a);
                if (d.lengthSqr() < 1.0E-8) {
                    continue;
                }
                float ka = (float) Math.max(0, 1.0 - (now - p[i][3]) / keepNs);
                float kb = (float) Math.max(0, 1.0 - (now - p[i + 1][3]) / keepNs);
                Vec3 side = d.cross(a.add(b).scale(0.5)).normalize();
                Vec3 sa = side.scale(t.width * wscale * 0.5 * (0.2 + 0.8 * ka));
                Vec3 sb = side.scale(t.width * wscale * 0.5 * (0.2 + 0.8 * kb));
                float aa = ka * ka * (pass == 0 ? 0.75F : 1.0F), ab = kb * kb * (pass == 0 ? 0.75F : 1.0F);
                tri(bb, m, a.add(sa), a, b, rgb, 0.0F, aa, ab);
                tri(bb, m, a.add(sa), b, b.add(sb), rgb, 0.0F, ab, 0.0F);
                tri(bb, m, a, a.subtract(sa), b.subtract(sb), rgb, aa, 0.0F, 0.0F);
                tri(bb, m, a, b.subtract(sb), b, rgb, aa, 0.0F, ab);
            }
        }
    }
}
