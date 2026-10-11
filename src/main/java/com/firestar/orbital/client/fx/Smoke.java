package com.firestar.orbital.client.fx;

import com.firestar.orbital.config.OConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Steam and dust: soft round clouds that billow, drift, grow and thin away, drawn back to front with ordinary alpha
 * blending (so they can darken a bright sky, which light cannot). Each cloud is three overlapping lobes so it never
 * reads as a disc. Simulated per rendered frame.
 */
public final class Smoke {
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final Random R = new Random();
    private static long last = System.nanoTime();

    private Smoke() {}

    static final class Puff {
        Vec3 pos, vel;
        float size0, size1, life, age, alpha, drag, rise, spin;
        int rgb;
        float[] lobes;
        double dist;
    }

    /**
     * @param rise upward acceleration (steam rises, dust settles: negative)
     */
    public static void puff(Vec3 pos, Vec3 vel, float size0, float size1, float life, int rgb, float alpha, float drag, float rise) {
        if (PUFFS.size() >= OConfig.CLIENT.smoke.get()) {
            // full: replace a random cloud rather than shifting the whole list
            PUFFS.set(R.nextInt(PUFFS.size()), make(pos, vel, size0, size1, life, rgb, alpha, drag, rise));
            return;
        }
        PUFFS.add(make(pos, vel, size0, size1, life, rgb, alpha, drag, rise));
    }

    private static Puff make(Vec3 pos, Vec3 vel, float size0, float size1, float life, int rgb, float alpha, float drag, float rise) {
        Puff p = new Puff();
        p.pos = pos;
        p.vel = vel;
        p.size0 = size0;
        p.size1 = size1;
        p.life = life;
        p.rgb = rgb;
        p.alpha = alpha;
        p.drag = drag;
        p.rise = rise;
        p.spin = R.nextFloat() * 6.28F;
        p.lobes = new float[]{R.nextFloat() - 0.5F, R.nextFloat() - 0.5F, R.nextFloat() - 0.5F, R.nextFloat() - 0.5F, 0.7F + R.nextFloat() * 0.5F,
                0.6F + R.nextFloat() * 0.5F};
        return p;
    }

    public static void clear() {
        PUFFS.clear();
    }

    public static int count() {
        return PUFFS.size();
    }

    private static void step(float dt) {
        for (int i = PUFFS.size() - 1; i >= 0; i--) {
            Puff p = PUFFS.get(i);
            p.age += dt;
            if (p.age >= p.life) {
                PUFFS.remove(i);
                continue;
            }
            float d = (float) Math.exp(-p.drag * dt);
            p.vel = p.vel.scale(d).add(0, p.rise * dt, 0);
            p.pos = p.pos.add(p.vel.scale(dt));
        }
    }

    public static void render(PoseStack view, Vec3 cam, float brightness) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - last) / 1.0E9F);
        last = now;
        if (Minecraft.getInstance().isPaused()) {
            dt = 0;
        }
        step(dt);
        if (PUFFS.isEmpty()) {
            return;
        }
        for (Puff p : PUFFS) {
            p.dist = p.pos.distanceToSqr(cam);
        }
        PUFFS.sort(Comparator.comparingDouble((Puff p) -> -p.dist));
        Matrix4f m = view.last().pose();
        org.joml.Vector3f lk = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        Vec3 fwd = new Vec3(lk.x, lk.y, lk.z);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int drawn = 0;
        for (Puff p : PUFFS) {
            Vec3 c = p.pos.subtract(cam);
            float k = p.age / p.life;
            float size = Mth.lerp(1 - (1 - k) * (1 - k), p.size0, p.size1);
            if (c.dot(fwd) < -size) {
                continue;
            }
            // fade in quickly, thin out slowly
            float a = p.alpha * Mth.clamp(k * 8, 0, 1) * (1 - k) * (1 - k * 0.3F);
            if (a < 0.004F) {
                continue;
            }
            // a near cloud must not swallow the lens
            double dist = Math.sqrt(p.dist);
            if (dist < size * 1.2) {
                a *= (float) Mth.clamp((dist - size * 0.3) / (size * 0.9), 0, 1);
            }
            // too small on screen to matter
            if (size / Math.max(1.0, dist) < 0.012) {
                continue;
            }
            int lobes = dist < 60 ? 3 : dist < 160 ? 2 : 1;
            int rgb = shade(p.rgb, brightness);
            Vec3 view0 = c.normalize();
            Vec3 upHint = Math.abs(view0.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = view0.cross(upHint).normalize();
            Vec3 up = right.cross(view0).normalize();
            for (int l = 0; l < lobes; l++) {
                float ox = l == 0 ? 0 : p.lobes[(l - 1) * 2] * size * 0.7F;
                float oy = l == 0 ? 0 : p.lobes[(l - 1) * 2 + 1] * size * 0.5F;
                float ls = size * (l == 0 ? 1.0F : p.lobes[3 + l]);
                Vec3 lc = c.add(right.scale(ox)).add(up.scale(oy));
                blob(bb, m, lc, right, up, ls, rgb, a * (l == 0 ? (lobes == 1 ? 0.9F : 0.75F) : 0.5F), p.spin + l, dist < 80 ? 12 : 8);
            }
            if (++drawn > 2500) {
                break;
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static int shade(int rgb, float b) {
        int r = (int) ((rgb >> 16 & 255) * b), g = (int) ((rgb >> 8 & 255) * b), bl = (int) ((rgb & 255) * b);
        return r << 16 | g << 8 | bl;
    }

    /** A soft disc: opaque-ish core, irregular edge (the radius wobbles round the rim), alpha to nothing outside. */
    private static void blob(BufferBuilder bb, Matrix4f m, Vec3 c, Vec3 right, Vec3 up, float size, int rgb, float a, float spin, int n) {
        float r = (rgb >> 16 & 255) / 255.0F, g = (rgb >> 8 & 255) / 255.0F, b = (rgb & 255) / 255.0F;
        Vec3 prevIn = null, prevOut = null;
        Vec3 firstIn = null, firstOut = null;
        for (int i = 0; i <= n; i++) {
            double ang = i * Math.PI * 2 / n + spin;
            double wob = 1.0 + 0.16 * Math.sin(ang * 3 + spin * 2) + 0.08 * Math.sin(ang * 5 - spin);
            Vec3 d = right.scale(Math.cos(ang)).add(up.scale(Math.sin(ang)));
            Vec3 in = c.add(d.scale(size * 0.42 * wob));
            Vec3 out = c.add(d.scale(size * wob));
            if (i == n) {
                in = firstIn;
                out = firstOut;
            }
            if (prevIn != null) {
                // core fan
                v(bb, m, c, r, g, b, a);
                v(bb, m, prevIn, r, g, b, a * 0.8F);
                v(bb, m, in, r, g, b, a * 0.8F);
                // soft rim
                v(bb, m, prevIn, r, g, b, a * 0.8F);
                v(bb, m, prevOut, r, g, b, 0);
                v(bb, m, out, r, g, b, 0);
                v(bb, m, prevIn, r, g, b, a * 0.8F);
                v(bb, m, out, r, g, b, 0);
                v(bb, m, in, r, g, b, a * 0.8F);
            } else {
                firstIn = in;
                firstOut = out;
            }
            prevIn = in;
            prevOut = out;
        }
    }

    private static void v(BufferBuilder bb, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        bb.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }
}
