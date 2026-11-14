package com.aegis.command.client.fx;

import com.aegis.command.AegisCommand;
import com.aegis.command.client.render.WeaponRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-only 3D effects drawn after particles: energy beams, expanding shock rings and domes,
 * billboard flashes, streak sparks / glowing casings, and physical debris chunks that render the
 * actual block they were torn from, tumble, bounce and settle. Simulated per frame.
 */
@Mod.EventBusSubscriber(modid = AegisCommand.MODID, value = Dist.CLIENT)
public final class FxWorld {
    private static final RandomSource R = RandomSource.create();
    private static final ResourceLocation GLOW = WeaponRenderer.GLOW_TEX, RING = WeaponRenderer.RING,
            STREAK = WeaponRenderer.STREAK, STAR = WeaponRenderer.STAR;
    private static final int MAX_DEBRIS = 700, MAX_SPARKS = 1500;

    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final List<Debris> DEBRIS = new ArrayList<>();
    private static long lastNs = System.nanoTime();

    private FxWorld() {}

    // ------------------------------------------------------------------ element types

    private static final class Beam {
        Vec3 a, b; float width, life, age; float r, g, bl; boolean arcs;
    }

    private static final class Ring {
        Vec3 c; float r0, r1, width, life, age; float r, g, b; boolean flat, dome;
    }

    private static final class Flash {
        Vec3 c; float size, life, age; float r, g, b; boolean star;
    }

    private static final class Spark {
        Vec3 p, v; float life, age, len, size, grav; float r, g, b; boolean bounce;
    }

    private static final class Debris {
        Vec3 p, v; float rx, ry, rz, wx, wy, wz, scale, life, age; BlockState state; boolean resting;
    }

    // ------------------------------------------------------------------ spawning API

    public static void beam(Vec3 a, Vec3 b, float width, float life, int rgb, boolean arcs) {
        Beam x = new Beam();
        x.a = a; x.b = b; x.width = width; x.life = life; x.arcs = arcs;
        x.r = r(rgb); x.g = g(rgb); x.bl = b(rgb);
        BEAMS.add(x);
    }

    /** Flat ground ring (flat = true) or camera-facing ring. */
    public static void ring(Vec3 c, float r0, float r1, float life, int rgb, boolean flat) {
        Ring x = new Ring();
        x.c = c; x.r0 = r0; x.r1 = r1; x.life = life; x.flat = flat;
        x.r = r(rgb); x.g = g(rgb); x.b = b(rgb);
        RINGS.add(x);
    }

    /** Expanding translucent shock dome (hemisphere). */
    public static void dome(Vec3 c, float r1, float life, int rgb) {
        Ring x = new Ring();
        x.c = c; x.r0 = 0.5f; x.r1 = r1; x.life = life; x.dome = true;
        x.r = r(rgb); x.g = g(rgb); x.b = b(rgb);
        RINGS.add(x);
    }

    public static void flash(Vec3 c, float size, float life, int rgb, boolean star) {
        Flash x = new Flash();
        x.c = c; x.size = size; x.life = life; x.star = star;
        x.r = r(rgb); x.g = g(rgb); x.b = b(rgb);
        FLASHES.add(x);
    }

    public static void spark(Vec3 p, Vec3 v, float life, float len, float size, float grav, int rgb, boolean bounce) {
        if (SPARKS.size() >= MAX_SPARKS) return;
        Spark x = new Spark();
        x.p = p; x.v = v; x.life = life; x.len = len; x.size = size; x.grav = grav; x.bounce = bounce;
        x.r = r(rgb); x.g = g(rgb); x.b = b(rgb);
        SPARKS.add(x);
    }

    /** Burst of sparks in random directions. */
    public static void sparkBurst(Vec3 p, int n, float speed, float life, int rgb, float grav) {
        for (int i = 0; i < n; i++) {
            Vec3 d = randomDir().scale(speed * (0.35 + R.nextDouble() * 0.9));
            spark(p, d, life * (0.6f + R.nextFloat() * 0.8f), 0.06f + R.nextFloat() * 0.05f, 0.05f + R.nextFloat() * 0.05f, grav, rgb, true);
        }
    }

    public static void debris(Vec3 p, Vec3 v, BlockState state, float scale, float life) {
        if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) return;
        if (DEBRIS.size() >= MAX_DEBRIS) DEBRIS.remove(0);
        Debris d = new Debris();
        d.p = p; d.v = v; d.state = state; d.scale = scale; d.life = life;
        d.rx = R.nextFloat() * 360; d.ry = R.nextFloat() * 360; d.rz = R.nextFloat() * 360;
        d.wx = (R.nextFloat() - 0.5f) * 900; d.wy = (R.nextFloat() - 0.5f) * 900; d.wz = (R.nextFloat() - 0.5f) * 900;
        DEBRIS.add(d);
    }

    public static void clear() {
        BEAMS.clear(); RINGS.clear(); FLASHES.clear(); SPARKS.clear(); DEBRIS.clear();
    }

    // ------------------------------------------------------------------ simulation + rendering

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        if (level == null) { clear(); return; }
        if (BEAMS.isEmpty() && RINGS.isEmpty() && FLASHES.isEmpty() && SPARKS.isEmpty() && DEBRIS.isEmpty()) return;
        if (mc.isPaused()) dt = 0;

        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Quaternionf camRot = cam.rotation();

        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);

        // debris: real block models, tumbling
        Iterator<Debris> di = DEBRIS.iterator();
        while (di.hasNext()) {
            Debris d = di.next();
            d.age += dt;
            if (d.age > d.life) { di.remove(); continue; }
            if (!d.resting) {
                d.v = d.v.add(0, -24 * dt, 0).scale(Math.pow(0.6, dt));
                Vec3 np = d.p.add(d.v.scale(dt));
                BlockPos bp = BlockPos.containing(np);
                if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                    // bounce off the surface we hit, losing most of the energy
                    BlockPos vp = BlockPos.containing(d.p.x, np.y, d.p.z);
                    if (d.v.y < 0 && !level.getBlockState(vp).getCollisionShape(level, vp).isEmpty()) {
                        d.v = new Vec3(d.v.x * 0.45, -d.v.y * 0.3, d.v.z * 0.45);
                        if (Math.abs(d.v.y) < 0.8) { d.resting = true; d.v = Vec3.ZERO; }
                    } else {
                        d.v = new Vec3(-d.v.x * 0.3, d.v.y, -d.v.z * 0.3);
                    }
                    d.wx *= 0.5f; d.wy *= 0.5f; d.wz *= 0.5f;
                } else {
                    d.p = np;
                }
                d.rx += d.wx * dt; d.ry += d.wy * dt; d.rz += d.wz * dt;
            }
            float shrink = Mth.clamp((d.life - d.age) / 1.2f, 0, 1);
            ps.pushPose();
            ps.translate(d.p.x, d.p.y, d.p.z);
            ps.mulPose(Axis.XP.rotationDegrees(d.rx));
            ps.mulPose(Axis.YP.rotationDegrees(d.ry));
            ps.mulPose(Axis.ZP.rotationDegrees(d.rz));
            float s = d.scale * shrink;
            ps.scale(s, s, s);
            ps.translate(-0.5, -0.5, -0.5);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(d.p));
            mc.getBlockRenderer().renderSingleBlock(d.state, ps, buf, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        buf.endBatch();

        // sparks and casings: velocity-aligned streaks
        VertexConsumer sv = buf.getBuffer(RenderType.eyes(STREAK));
        Iterator<Spark> si = SPARKS.iterator();
        while (si.hasNext()) {
            Spark s = si.next();
            s.age += dt;
            if (s.age > s.life) { si.remove(); continue; }
            s.v = s.v.add(0, -s.grav * dt, 0).scale(Math.pow(0.35, dt));
            Vec3 np = s.p.add(s.v.scale(dt));
            if (s.bounce) {
                BlockPos bp = BlockPos.containing(np);
                if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                    s.v = new Vec3(s.v.x * 0.5, -s.v.y * 0.35, s.v.z * 0.5);
                    np = s.p;
                }
            }
            s.p = np;
            float k = 1 - s.age / s.life;
            Vec3 tail = s.p.subtract(s.v.scale(s.len));
            streak(ps, sv, cp, tail, s.p, s.size * (0.5f + 0.5f * k), s.r * k, s.g * k, s.b * k);
        }

        // beams
        VertexConsumer bv = buf.getBuffer(RenderType.eyes(STREAK));
        Iterator<Beam> bi = BEAMS.iterator();
        while (bi.hasNext()) {
            Beam b = bi.next();
            b.age += dt;
            if (b.age > b.life) { bi.remove(); continue; }
            float k = 1 - b.age / b.life;
            float w = b.width * (0.3f + 0.7f * k) * (1 + 0.6f * (float) Math.exp(-b.age * 30));
            streak(ps, bv, cp, b.a, b.b, w, b.r * k, b.g * k, b.bl * k);
            streak(ps, bv, cp, b.a, b.b, w * 0.35f, k, k, k);
            if (b.arcs) {
                Vec3 d = b.b.subtract(b.a);
                double len = d.length();
                Vec3 dir = d.normalize();
                Vec3 side = dir.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
                side = side.normalize();
                Vec3 up = side.cross(dir).normalize();
                Vec3 prev = b.a;
                double twist = b.age * 40;
                for (double t = 0.6; t < len; t += 0.6) {
                    double a = t * 1.7 + twist;
                    double rad = w * 1.4 * (0.6 + 0.4 * Math.sin(t * 3 + b.age * 50));
                    Vec3 p = b.a.add(dir.scale(t)).add(side.scale(Math.cos(a) * rad)).add(up.scale(Math.sin(a) * rad));
                    streak(ps, bv, cp, prev, p, w * 0.12f, b.r * k, b.g * k, b.bl * k);
                    prev = p;
                }
            }
        }

        // rings and domes
        VertexConsumer rv = buf.getBuffer(RenderType.eyes(RING));
        VertexConsumer dv = buf.getBuffer(RenderType.eyes(GLOW));
        Iterator<Ring> ri = RINGS.iterator();
        while (ri.hasNext()) {
            Ring r = ri.next();
            r.age += dt;
            if (r.age > r.life) { ri.remove(); continue; }
            float p = r.age / r.life;
            float rad = r.r0 + (r.r1 - r.r0) * (1 - (float) Math.pow(1 - p, 3));
            float k = (1 - p) * (1 - p);
            if (r.dome) {
                dome(ps, dv, r.c, rad, r.r * k * 0.55f, r.g * k * 0.55f, r.b * k * 0.55f);
            } else {
                ps.pushPose();
                ps.translate(r.c.x, r.c.y, r.c.z);
                if (r.flat) ps.mulPose(Axis.XP.rotationDegrees(90));
                else ps.mulPose(camRot);
                billboard(ps, rv, rad, r.r * k, r.g * k, r.b * k);
                ps.popPose();
            }
        }

        // flashes
        VertexConsumer fv = buf.getBuffer(RenderType.eyes(GLOW));
        VertexConsumer stv = buf.getBuffer(RenderType.eyes(STAR));
        Iterator<Flash> fi = FLASHES.iterator();
        while (fi.hasNext()) {
            Flash f = fi.next();
            f.age += dt;
            if (f.age > f.life) { fi.remove(); continue; }
            float p = f.age / f.life;
            float k = (1 - p);
            float size = f.size * (0.6f + 0.6f * (float) Math.sqrt(p));
            ps.pushPose();
            ps.translate(f.c.x, f.c.y, f.c.z);
            ps.mulPose(camRot);
            billboard(ps, fv, size, f.r * k, f.g * k, f.b * k);
            billboard(ps, fv, size * 0.4f, k, k, k);
            if (f.star) {
                ps.mulPose(Axis.ZP.rotationDegrees(p * 40));
                billboard(ps, stv, size * 1.8f, f.r * k, f.g * k, f.b * k);
            }
            ps.popPose();
        }
        buf.endBatch();
        ps.popPose();
    }

    // ------------------------------------------------------------------ primitives

    /** Camera-facing ribbon from a to b. */
    private static void streak(PoseStack ps, VertexConsumer vc, Vec3 cam, Vec3 a, Vec3 b, float w, float r, float g, float bl) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-8) return;
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(mid.subtract(cam));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(w);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        v(vc, m, n, a.subtract(side), 0, 0, r, g, bl);
        v(vc, m, n, b.subtract(side), 1, 0, r, g, bl);
        v(vc, m, n, b.add(side), 1, 1, r, g, bl);
        v(vc, m, n, a.add(side), 0, 1, r, g, bl);
        v(vc, m, n, a.add(side), 0, 1, r, g, bl);
        v(vc, m, n, b.add(side), 1, 1, r, g, bl);
        v(vc, m, n, b.subtract(side), 1, 0, r, g, bl);
        v(vc, m, n, a.subtract(side), 0, 0, r, g, bl);
    }

    private static void billboard(PoseStack ps, VertexConsumer vc, float s, float r, float g, float b) {
        WeaponRenderer.quad(ps, vc, s, s, r, g, b);
    }

    /** Latitude bands of a hemisphere, brighter toward the rim (fresnel-like shell). */
    private static void dome(PoseStack ps, VertexConsumer vc, Vec3 c, float rad, float r, float g, float b) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int lat = 7, lon = 22;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI / 2 * i / lat, t1 = Math.PI / 2 * (i + 1) / lat;
            float k0 = (float) Math.pow(Math.cos(t0), 2.5), k1 = (float) Math.pow(Math.cos(t1), 2.5);
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                Vec3 a = c.add(Math.cos(t0) * Math.cos(p0) * rad, Math.sin(t0) * rad, Math.cos(t0) * Math.sin(p0) * rad);
                Vec3 bb = c.add(Math.cos(t0) * Math.cos(p1) * rad, Math.sin(t0) * rad, Math.cos(t0) * Math.sin(p1) * rad);
                Vec3 cc = c.add(Math.cos(t1) * Math.cos(p1) * rad, Math.sin(t1) * rad, Math.cos(t1) * Math.sin(p1) * rad);
                Vec3 dd = c.add(Math.cos(t1) * Math.cos(p0) * rad, Math.sin(t1) * rad, Math.cos(t1) * Math.sin(p0) * rad);
                v(vc, m, n, a, 0.5f, 0.5f, r * k0, g * k0, b * k0);
                v(vc, m, n, bb, 0.5f, 0.5f, r * k0, g * k0, b * k0);
                v(vc, m, n, cc, 0.5f, 0.5f, r * k1, g * k1, b * k1);
                v(vc, m, n, dd, 0.5f, 0.5f, r * k1, g * k1, b * k1);
                v(vc, m, n, dd, 0.5f, 0.5f, r * k1, g * k1, b * k1);
                v(vc, m, n, cc, 0.5f, 0.5f, r * k1, g * k1, b * k1);
                v(vc, m, n, bb, 0.5f, 0.5f, r * k0, g * k0, b * k0);
                v(vc, m, n, a, 0.5f, 0.5f, r * k0, g * k0, b * k0);
            }
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float vv, float r, float g, float b) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1), 1f)
                .uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(WeaponRenderer.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    public static Vec3 randomDir() {
        double u = R.nextDouble() * 2 - 1, a = R.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(a), u, s * Math.sin(a));
    }

    private static float r(int c) { return ((c >> 16) & 255) / 255f; }
    private static float g(int c) { return ((c >> 8) & 255) / 255f; }
    private static float b(int c) { return (c & 255) / 255f; }

    @SuppressWarnings("unused")
    private static Vector3f unused() { return new Vector3f(); }
}
