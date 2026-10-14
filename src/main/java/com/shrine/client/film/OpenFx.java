package com.shrine.client.film;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.GlStateManager;
import com.shrine.network.S2CCast;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Open without its cutscene: the move exactly as it happens in the author's film, drawn in the world from the fire
 * baked out of fire_arrow_open.blend (tools/bake_open.py). Every part's own geometry, its placement each tick, how
 * much of each flame ribbon is revealed, and the film's "dough flow" wobble and noise-driven colour ramps redone here.
 *
 * <p>Timeline (ticks after the cast; the server's beats are the same): 0 the arm catches fire, 30 the fists burst,
 * 48 the arrow is drawn out of the flame, 80 it flies, 89 it lands and the blast blooms, then the bonfires burn down.
 */
public final class OpenFx {
    public static final ResourceLocation DATA = new ResourceLocation("shrine", "film/open_fx.bin");
    /** The film's hero is two blocks tall; a player is 1.8. */
    private static final float HERO = 0.9F;
    /** The film's blast reaches about seven blocks; the real one is scaled to the blast radius. */
    private static final float BLAST_R = 7.0F;

    private static Seq ARMFIRE, FISTS, DRAW, ARROW, BLAST;
    private static boolean loaded, failed;

    private OpenFx() {}

    // ------------------------------------------------------------------------------------------------ the baked data

    static final class Part {
        boolean curve;
        int mat;
        float amp, swell, scale, speed, rise, fx, fy, fz;
        float[] pos, nrm, u;
        int[] tri;
    }

    static final class Seq {
        String name;
        int frames;
        int start;
        Part[] parts;
        /** [frame][part]: 12 floats (3x4 matrix, rows) + reveal start/end, or null when hidden. */
        float[][][] place;
    }

    private static void load() {
        if (loaded || failed) {
            return;
        }
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(DATA);
            if (res.isEmpty()) {
                failed = true;
                return;
            }
            byte[] raw;
            try (InputStream in = new GZIPInputStream(res.get().open())) {
                raw = in.readAllBytes();
            }
            ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            if (b.get() != 'O' || b.get() != 'P' || b.get() != 'N' || b.get() != '2') {
                throw new IllegalStateException("not an Open fx file");
            }
            int ns = b.getInt();
            int[] starts = {433, 1441, 1675, 2100, 2323};
            for (int s = 0; s < ns; s++) {
                Seq q = new Seq();
                byte[] nm = new byte[b.getInt()];
                b.get(nm);
                q.name = new String(nm, java.nio.charset.StandardCharsets.UTF_8);
                q.start = s < starts.length ? starts[s] : 0;
                q.frames = b.getInt();
                int np = b.getInt();
                q.parts = new Part[np];
                for (int i = 0; i < np; i++) {
                    Part p = new Part();
                    p.curve = b.get() != 0;
                    p.mat = b.get();
                    p.amp = b.getFloat();
                    p.swell = b.getFloat();
                    p.scale = b.getFloat();
                    p.speed = b.getFloat();
                    p.rise = b.getFloat();
                    b.getFloat(); // subdivision: baked into the geometry
                    p.fx = b.getFloat();
                    p.fy = b.getFloat();
                    p.fz = b.getFloat();
                    int nv = b.getInt(), nt = b.getInt();
                    p.pos = new float[nv * 3];
                    p.nrm = new float[nv * 3];
                    p.u = new float[nv];
                    for (int v = 0; v < nv; v++) {
                        p.pos[v * 3] = b.getFloat();
                        p.pos[v * 3 + 1] = b.getFloat();
                        p.pos[v * 3 + 2] = b.getFloat();
                        p.nrm[v * 3] = b.get() / 127.0F;
                        p.nrm[v * 3 + 1] = b.get() / 127.0F;
                        p.nrm[v * 3 + 2] = b.get() / 127.0F;
                        p.u[v] = b.getFloat();
                    }
                    p.tri = new int[nt * 3];
                    for (int k = 0; k < nt * 3; k++) {
                        p.tri[k] = b.getInt();
                    }
                    q.parts[i] = p;
                }
                q.place = new float[q.frames][np][];
                for (int f = 0; f < q.frames; f++) {
                    for (int i = 0; i < np; i++) {
                        if (b.get() == 0) {
                            continue;
                        }
                        float[] m = new float[14];
                        for (int k = 0; k < 14; k++) {
                            m[k] = b.getFloat();
                        }
                        q.place[f][i] = m;
                    }
                }
                switch (q.name) {
                    case "armfire" -> ARMFIRE = q;
                    case "fists" -> FISTS = q;
                    case "draw" -> DRAW = q;
                    case "arrow" -> ARROW = q;
                    case "blast" -> BLAST = q;
                    default -> {
                    }
                }
            }
            loaded = true;
        } catch (Throwable t) {
            failed = true;
            com.shrine.ShrineMod.LOGGER.error("[open] could not load the film's fire", t);
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    /** A frame to draw: which sequence, at which (fractional) frame, placed by {@code M} (blend space to world). */
    private record Shot(Seq seq, float frame, Matrix4f M) {}

    /** Called at the end of the level render with the camera's pose. */
    public static void render(PoseStack pose, Camera camera, float partial, List<S2CCast> quick, List<Float> times, List<S2CCast> burning,
                              List<Float> burnTimes) {
        if (quick.isEmpty() && burning.isEmpty()) {
            return;
        }
        load();
        if (!loaded) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int drawn = 0;
        for (int i = 0; i < burning.size(); i++) {
            drawn += burn(buf, burning.get(i), cam, burnTimes.get(i));
        }
        for (int i = 0; i < quick.size(); i++) {
            S2CCast m = quick.get(i);
            float t = times.get(i);
            Entity caster = mc.level == null ? null : mc.level.getEntity(m.casterId);
            for (Shot s : shots(m, caster, t, partial)) {
                drawn += emit(buf, s, cam);
                if (s.seq == ARROW || (s.seq == DRAW && t > 62.0F)) {
                    // flame streaming off the arrow
                    drawn += trail(buf, s, cam, t + partial, s.seq == ARROW);
                }
            }
            if (t >= 89.0F && t < 89.0F + PILLAR) {
                drawn += pillar(buf, m, cam, t - 89.0F, camera);
            }
        }
        RenderSystem.enableBlend();
        // blended over the world (not added) so the film's oranges and golds stay their colour against a bright sky
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        BufferUploader.drawWithShader(buf.end());
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static List<Shot> shots(S2CCast m, @Nullable Entity caster, float t, float partial) {
        java.util.ArrayList<Shot> out = new java.util.ArrayList<>();
        Vec3 flat = new Vec3(m.dir.x, 0.0, m.dir.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 feet = caster != null ? caster.getPosition(partial) : m.origin.subtract(0, 1.62, 0);
        Matrix4f hero = basis(feet, flat, new Vec3(0, 1, 0), HERO);
        // 0-30: the arm catches fire (the film's "Fire aspect?!" shot, frames 433-523)
        if (t < 32.0F) {
            out.add(new Shot(ARMFIRE, Math.min(t, ARMFIRE.frames - 1.001F), hero));
        }
        // 30-48: the fists burst (frames 1441-1549, at twice the speed)
        if (t >= 30.0F && t < 49.0F) {
            out.add(new Shot(FISTS, Math.min((t - 30.0F) * 2.0F, FISTS.frames - 1.001F), hero));
        }
        // 48-80: the arrow drawn out of the flame (frames 1675-1771). The film's hero turns side-on to draw, so in
        // his own frame the arrow lies along +x: that axis is turned to face the target.
        if (t >= 48.0F && t < 80.5F) {
            out.add(new Shot(DRAW, Math.min(t - 48.0F, DRAW.frames - 1.001F), alongX(feet, flat, HERO)));
        }
        // 80-89: it flies, growing as it goes (the film's arrow, 1.05 at the bow, 2.8 at the target)
        if (t >= 80.0F && t < 89.5F) {
            float k = Mth.clamp((t - 80.0F) / 9.0F, 0.0F, 1.0F);
            float kk = k * k * (0.4F + 0.6F * k);
            Vec3 bow = feet.add(right(flat).scale(-0.3 * HERO)).add(flat.scale(1.03 * HERO)).add(0, 1.61 * HERO, 0);
            Vec3 aim = m.point.subtract(bow);
            Vec3 dir = aim.lengthSqr() < 1.0E-6 ? flat : aim.normalize();
            Vec3 at = bow.lerp(m.point, kk);
            Vec3 up = Math.abs(dir.y) > 0.98 ? flat : new Vec3(0, 1, 0);
            float scale = HERO * Mth.lerp(k, 1.05F, 2.8F);
            float f = ((t - 80.0F) * 1.0F) % (ARROW.frames - 1.001F);
            out.add(new Shot(ARROW, f, alongX(at, dir, scale)));
        }
        return out;
    }

    /** The film's arrow frame: its +x (the tip) along {@code fwd}, +z up. */
    private static Matrix4f alongX(Vec3 origin, Vec3 fwd, float scale) {
        Vec3 f = fwd.normalize();
        Vec3 up = Math.abs(f.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = up.cross(f).normalize();
        Vec3 u = f.cross(side).normalize();
        return new Matrix4f(
                (float) f.x * scale, (float) f.y * scale, (float) f.z * scale, 0,
                (float) side.x * scale, (float) side.y * scale, (float) side.z * scale, 0,
                (float) u.x * scale, (float) u.y * scale, (float) u.z * scale, 0,
                (float) origin.x, (float) origin.y, (float) origin.z, 1);
    }

    // ------------------------------------------------------------------------------------------------ the burning crater

    /**
     * Open's crater burns: flames stand all over its floor (they hurt whoever walks into them, server side), taller
     * near the middle, licking and flickering, dying down over the last few seconds.
     */
    private static int burn(BufferBuilder buf, S2CCast m, Vec3 cam, float t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        float R = Math.max(6.0F, m.size * 10.0F) * 1.02F;
        float rise = Mth.clamp((t - 8.0F) / 24.0F, 0, 1);
        float die = 1 - Mth.clamp((t - (com.shrine.common.Timing.FUGA_BURN - 120)) / 120.0F, 0, 1);
        float life = rise * die;
        if (life <= 0.01F || cam.distanceTo(m.point) > R + 160) {
            return 0;
        }
        int tris = 0;
        int n = Mth.clamp((int) (R * R * 0.24F), 90, 620);
        java.util.Random rnd = new java.util.Random(m.seed * 31 + 7);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = R * Math.sqrt(rnd.nextDouble());
            double x = m.point.x + Math.cos(a) * d, z = m.point.z + Math.sin(a) * d;
            float phase = rnd.nextFloat() * 50;
            float hScale = 0.6F + rnd.nextFloat() * 0.8F;
            // skip flames far from the camera to keep the frame light
            double dist = Math.sqrt((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z));
            if (dist > 90 && (i & 3) != 0) {
                continue;
            }
            int floor = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            float flick = 0.7F + 0.3F * noise(i * 0.37F, t * 0.25F + phase, 3);
            float h = (float) ((2.0 + 4.2 * (1 - d / R)) * hScale * flick * life);
            if (h < 0.15F) {
                continue;
            }
            int tongues = 3;
            for (int k = 0; k < tongues; k++) {
                double ka = a * 3 + k * 2.1;
                double bx = x + Math.cos(ka) * 0.35, bz = z + Math.sin(ka) * 0.35;
                org.joml.Vector3f prev = null;
                float prevW = 0;
                int seg = 5;
                for (int s = 0; s <= seg; s++) {
                    float u = s / (float) seg;
                    double sway = Math.sin(t * 0.85F + phase + k * 1.3F + u * 4.0F) * 0.42 * u;
                    double sway2 = Math.cos(t * 0.73F + phase * 1.3F + k + u * 3.0F) * 0.42 * u;
                    org.joml.Vector3f p = new org.joml.Vector3f((float) (bx + sway), (float) (floor + u * h * (0.75F + 0.25F * k / 2.0F)),
                            (float) (bz + sway2));
                    float w = 0.45F * (1 - u * 0.9F) * (0.8F + 0.2F * flick);
                    if (prev != null) {
                        tris += ribbon(buf, cam, prev, p, prevW, w, (s - 1) / (float) seg, u);
                    }
                    prev = p;
                    prevW = w;
                }
            }
        }
        return tris;
    }

    // ------------------------------------------------------------------------------------------------ living fire

    /** How long the pillar of fire stands over the blast (ticks). */
    private static final float PILLAR = 150.0F;

    /** Tongues of flame streaming back off the arrow, fluttering, white at the root and red at the tips. */
    private static int trail(BufferBuilder buf, Shot s, Vec3 cam, float t, boolean flying) {
        Matrix4f M = s.M;
        org.joml.Vector3f o = M.transformPosition(0, 0, 0, new org.joml.Vector3f());
        org.joml.Vector3f fx = M.transformDirection(1, 0, 0, new org.joml.Vector3f());
        float scale = fx.length();
        org.joml.Vector3f f = new org.joml.Vector3f(fx).normalize();
        org.joml.Vector3f side = M.transformDirection(0, 1, 0, new org.joml.Vector3f()).normalize();
        org.joml.Vector3f up = M.transformDirection(0, 0, 1, new org.joml.Vector3f()).normalize();
        int tris = 0;
        int tongues = flying ? 14 : 9;
        float len = (flying ? 2.6F : 1.4F) * scale;
        for (int i = 0; i < tongues; i++) {
            float a = (float) (i * 2.399963);
            float rad = 0.12F * scale * (0.6F + 0.4F * noise(i * 3.1F, t * 0.2F, 0));
            float baseX = 0.9F - 0.13F * (i % 6);
            org.joml.Vector3f root = new org.joml.Vector3f(o).add(new org.joml.Vector3f(f).mul(baseX * scale))
                    .add(new org.joml.Vector3f(side).mul((float) Math.cos(a) * rad)).add(new org.joml.Vector3f(up).mul((float) Math.sin(a) * rad));
            int seg = 7;
            org.joml.Vector3f prev = null;
            float prevW = 0;
            for (int k = 0; k <= seg; k++) {
                float u = k / (float) seg;
                float flutter = (float) Math.sin(t * 0.9F + i * 1.7F + u * 6.0F) * 0.22F * u * scale;
                float lift = (float) Math.cos(t * 0.7F + i * 2.3F + u * 5.0F) * 0.16F * u * scale;
                org.joml.Vector3f pnt = new org.joml.Vector3f(root).sub(new org.joml.Vector3f(f).mul(u * len * (0.7F + 0.3F * noise(i, t * 0.15F, 3))))
                        .add(new org.joml.Vector3f(side).mul(flutter)).add(new org.joml.Vector3f(up).mul(lift + u * u * 0.25F * scale));
                float wdt = 0.16F * scale * (1 - u) * (0.7F + 0.3F * (float) Math.sin(t + i));
                if (prev != null) {
                    tris += ribbon(buf, cam, prev, pnt, prevW, wdt, (k - 1) / (float) seg, u);
                }
                prev = pnt;
                prevW = wdt;
            }
        }
        return tris;
    }

    /** One camera-facing segment of a flame ribbon between {@code a} and {@code b}, coloured by its age along the ribbon. */
    private static int ribbon(BufferBuilder buf, Vec3 cam, org.joml.Vector3f a, org.joml.Vector3f b, float wa, float wb, float ua, float ub) {
        float dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        float vx = (float) (a.x - cam.x), vy = (float) (a.y - cam.y), vz = (float) (a.z - cam.z);
        // perpendicular to both the segment and the view
        float px = dy * vz - dz * vy, py = dz * vx - dx * vz, pz = dx * vy - dy * vx;
        float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1.0E-6F) {
            return 0;
        }
        px /= pl;
        py /= pl;
        pz /= pl;
        int ca = flame(ua), cb = flame(ub);
        float ax = (float) (a.x - cam.x), ay = (float) (a.y - cam.y), az = (float) (a.z - cam.z);
        float bx = (float) (b.x - cam.x), by = (float) (b.y - cam.y), bz = (float) (b.z - cam.z);
        vtx(buf, ax - px * wa, ay - py * wa, az - pz * wa, ca);
        vtx(buf, ax + px * wa, ay + py * wa, az + pz * wa, ca);
        vtx(buf, bx + px * wb, by + py * wb, bz + pz * wb, cb);
        vtx(buf, ax - px * wa, ay - py * wa, az - pz * wa, ca);
        vtx(buf, bx + px * wb, by + py * wb, bz + pz * wb, cb);
        vtx(buf, bx - px * wb, by - py * wb, bz - pz * wb, cb);
        return 2;
    }

    /** Flame colour along a tongue: white-gold at the root, orange, deep red and thin at the tip. */
    private static int flame(float u) {
        float r = 1.0F, g = Mth.lerp(Mth.clamp(u * 1.4F, 0, 1), 0.95F, 0.18F), b = Mth.lerp(Mth.clamp(u * 3.0F, 0, 1), 0.7F, 0.0F);
        float a = Mth.clamp(1.0F - u * u, 0, 1) * 0.9F;
        return (int) (a * 255) << 24 | (int) (r * 255) << 16 | (int) (g * 255) << 8 | (int) (b * 255);
    }

    /**
     * The blast's pillar: a ring of towering flame tongues climbing out of the impact, a churning core, a shock ring
     * racing over the ground and embers thrown high, burning down over several seconds.
     */
    private static int pillar(BufferBuilder buf, S2CCast m, Vec3 cam, float t, Camera camera) {
        float R = Math.max(6.0F, m.size * 10.0F);
        Vec3 c = m.point;
        int tris = 0;
        float grow = 1 - (float) Math.pow(1 - Mth.clamp(t / 14.0F, 0, 1), 3);
        float fade = 1 - Mth.clamp((t - 70.0F) / 80.0F, 0, 1);
        float height = R * 1.35F * grow * (0.75F + 0.25F * fade);
        float ringR = R * 0.32F * grow;
        // the column: tall flame tongues all round, the tallest at the middle
        int tongues = 40;
        for (int i = 0; i < tongues; i++) {
            float a = (float) (i * 2.399963);
            float rr = ringR * (0.25F + 0.75F * ((i * 37 % 100) / 100.0F));
            Vec3 base = c.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            float h = height * (0.55F + 0.45F * (1 - rr / Math.max(0.1F, ringR))) * (0.8F + 0.4F * noise(i * 1.3F, t * 0.12F, 7));
            int seg = 9;
            org.joml.Vector3f prev = null;
            float prevW = 0;
            for (int k = 0; k <= seg; k++) {
                float u = k / (float) seg;
                double sway = Math.sin(t * 0.21F + i * 1.9F + u * 4.0F) * R * 0.05 * u;
                double twist = Math.cos(t * 0.17F + i * 2.7F + u * 3.0F) * R * 0.05 * u;
                org.joml.Vector3f pnt = new org.joml.Vector3f((float) (base.x + sway + Math.cos(a) * u * R * 0.12), (float) (base.y + u * h),
                        (float) (base.z + twist + Math.sin(a) * u * R * 0.12));
                float wdt = R * 0.075F * (1 - u * 0.85F) * (0.7F + 0.3F * noise(i, u * 3 + t * 0.3F, 1)) * Math.max(0.15F, fade);
                if (prev != null) {
                    tris += ribbon(buf, cam, prev, pnt, prevW, wdt, Mth.clamp((k - 1) / (float) seg + (1 - fade) * 0.4F, 0, 1),
                            Mth.clamp(u + (1 - fade) * 0.4F, 0, 1));
                }
                prev = pnt;
                prevW = wdt;
            }
        }
        // the core: a white-hot churning ball that swells and sinks away
        float core = R * 0.42F * grow * (0.6F + 0.4F * fade);
        if (core > 0.2F && fade > 0.05F) {
            tris += ball(buf, cam, c.add(0, core * 0.55F, 0), core, t, fade);
        }
        // the shock ring over the ground
        if (t < 30.0F) {
            float k = t / 30.0F;
            float rr = R * 1.7F * (1 - (float) Math.pow(1 - k, 2.2));
            int n = 96;
            float wdt = R * 0.12F * (1 - k);
            int col = (int) ((1 - k) * 230) << 24 | 0xFFD9A0;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                float y = (float) (c.y + 0.3 - cam.y);
                float x0i = (float) (c.x + Math.cos(a0) * (rr - wdt) - cam.x), z0i = (float) (c.z + Math.sin(a0) * (rr - wdt) - cam.z);
                float x0o = (float) (c.x + Math.cos(a0) * (rr + wdt) - cam.x), z0o = (float) (c.z + Math.sin(a0) * (rr + wdt) - cam.z);
                float x1i = (float) (c.x + Math.cos(a1) * (rr - wdt) - cam.x), z1i = (float) (c.z + Math.sin(a1) * (rr - wdt) - cam.z);
                float x1o = (float) (c.x + Math.cos(a1) * (rr + wdt) - cam.x), z1o = (float) (c.z + Math.sin(a1) * (rr + wdt) - cam.z);
                int edge = col & 0x00FFFFFF;
                vtx(buf, x0i, y, z0i, edge);
                vtx(buf, x0o, y, z0o, col);
                vtx(buf, x1o, y, z1o, col);
                vtx(buf, x0i, y, z0i, edge);
                vtx(buf, x1o, y, z1o, col);
                vtx(buf, x1i, y, z1i, edge);
                tris += 2;
            }
        }
        // embers thrown up and raining back
        for (int i = 0; i < 70; i++) {
            float life = (t * 0.9F + i * 7.3F) % 60.0F;
            float a = (float) (i * 2.399963);
            float sp = R * (0.02F + 0.02F * ((i * 53 % 100) / 100.0F));
            double x = c.x + Math.cos(a) * sp * life * 1.2, z = c.z + Math.sin(a) * sp * life * 1.2;
            double y = c.y + R * 0.08 * life - 0.012 * R * life * life * 0.25 + 1;
            float sz = R * 0.012F * (1 - life / 60.0F) * fade;
            if (sz > 0.02F) {
                org.joml.Vector3f p0 = new org.joml.Vector3f((float) x, (float) y, (float) z);
                org.joml.Vector3f p1 = new org.joml.Vector3f((float) x, (float) (y - sz * 4), (float) z);
                tris += ribbon(buf, cam, p0, p1, sz, sz * 0.4F, 0.1F, 0.6F);
            }
        }
        return tris;
    }

    /** A churning ball of fire: a sphere whose surface boils, white-gold inside, orange to red at its rim. */
    private static int ball(BufferBuilder buf, Vec3 cam, Vec3 c, float r, float t, float fade) {
        int lat = 14, lon = 24;
        int tris = 0;
        float[][] px = new float[lat + 1][lon + 1], py = new float[lat + 1][lon + 1], pz = new float[lat + 1][lon + 1];
        int[][] col = new int[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat;
            for (int j = 0; j <= lon; j++) {
                double ph = Math.PI * 2 * j / lon;
                float nx = (float) (Math.sin(th) * Math.cos(ph)), ny = (float) Math.cos(th), nz = (float) (Math.sin(th) * Math.sin(ph));
                float boil = noise(nx * 2.2F + t * 0.08F, ny * 2.2F - t * 0.11F, nz * 2.2F);
                float rr = r * (0.82F + 0.36F * boil);
                px[i][j] = (float) (c.x + nx * rr - cam.x);
                py[i][j] = (float) (c.y + ny * rr - cam.y);
                pz[i][j] = (float) (c.z + nz * rr - cam.z);
                // facing the camera: hotter
                float vx = -px[i][j], vy = -py[i][j], vz = -pz[i][j];
                float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4F;
                float facing = Math.abs((nx * vx + ny * vy + nz * vz) / vl);
                float heat = Mth.clamp(0.35F + 0.65F * facing * (0.6F + 0.4F * boil), 0, 1);
                col[i][j] = ramp(0, heat, (0.55F + 0.45F * facing) * fade);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                vtx(buf, px[i][j], py[i][j], pz[i][j], col[i][j]);
                vtx(buf, px[i + 1][j], py[i + 1][j], pz[i + 1][j], col[i + 1][j]);
                vtx(buf, px[i + 1][j + 1], py[i + 1][j + 1], pz[i + 1][j + 1], col[i + 1][j + 1]);
                vtx(buf, px[i][j], py[i][j], pz[i][j], col[i][j]);
                vtx(buf, px[i + 1][j + 1], py[i + 1][j + 1], pz[i + 1][j + 1], col[i + 1][j + 1]);
                vtx(buf, px[i][j + 1], py[i][j + 1], pz[i][j + 1], col[i][j + 1]);
                tris += 2;
            }
        }
        return tris;
    }

    private static Vec3 right(Vec3 fwd) {
        return new Vec3(-fwd.z, 0.0, fwd.x);
    }

    /**
     * Blender space (x right, y forward, z up) to the world: {@code fwd} is the film's +y, {@code up} its +z.
     */
    private static Matrix4f basis(Vec3 origin, Vec3 fwd, Vec3 up, float scale) {
        Vec3 f = fwd.normalize();
        Vec3 r = f.cross(up).normalize();
        Vec3 u = r.cross(f).normalize();
        // columns: x -> right, y -> forward, z -> up
        Matrix4f M = new Matrix4f(
                (float) r.x * scale, (float) r.y * scale, (float) r.z * scale, 0,
                (float) f.x * scale, (float) f.y * scale, (float) f.z * scale, 0,
                (float) u.x * scale, (float) u.y * scale, (float) u.z * scale, 0,
                (float) origin.x, (float) origin.y, (float) origin.z, 1);
        return M;
    }

    private static int emit(BufferBuilder buf, Shot s, Vec3 cam) {
        Seq q = s.seq;
        if (q == null) {
            return 0;
        }
        int f0 = Mth.clamp((int) Math.floor(s.frame), 0, q.frames - 1);
        int f1 = Math.min(f0 + 1, q.frames - 1);
        float a = s.frame - f0;
        // the film's own clock (60 fps) for its wobble and colour noise
        float sec = (q.start + s.frame * 3.0F) / 60.0F;
        int tris = 0;
        boolean flowing = q == DRAW || q == ARROW;
        float wob = flowing ? 2.6F : 1.0F;
        float[] w = new float[3];
        float[] nn = new float[3];
        for (int i = 0; i < q.parts.length; i++) {
            float[] p0 = q.place[f0][i];
            if (p0 == null) {
                continue;
            }
            float[] p1 = q.place[f1][i] != null ? q.place[f1][i] : p0;
            float[] L = new float[14];
            for (int k = 0; k < 14; k++) {
                L[k] = p0[k] + (p1[k] - p0[k]) * a;
            }
            Part p = q.parts[i];
            int nv = p.u.length;
            float[] wx = new float[nv], wy = new float[nv], wz = new float[nv];
            int[] col = new int[nv];
            float s0 = L[12], s1 = L[13];
            for (int v = 0; v < nv; v++) {
                float x = p.pos[v * 3], y = p.pos[v * 3 + 1], z = p.pos[v * 3 + 2];
                float nx = p.nrm[v * 3], ny = p.nrm[v * 3 + 1], nz = p.nrm[v * 3 + 2];
                // FIRE_dough_flow: a noise offset plus a swell along the normal, flowing over time
                if (p.amp != 0.0F || p.swell != 0.0F) {
                    float px = x * p.scale + p.fx * sec * p.rise, py = y * p.scale + p.fy * sec * p.rise, pz = z * p.scale + p.fz * sec * p.rise;
                    float tw = sec * p.speed;
                    float n0 = noise(px + tw * 0.31F, py + tw * 0.17F, pz + tw * 0.23F);
                    float n1 = noise(px + 17.3F + tw * 0.29F, py - 4.1F + tw * 0.21F, pz + 9.7F + tw * 0.19F);
                    float n2 = noise(px - 8.6F + tw * 0.27F, py + 13.2F + tw * 0.33F, pz - 2.9F + tw * 0.15F);
                    float sw = (n0 - 0.5F) * p.swell * wob;
                    x += (n0 - 0.5F) * p.amp * wob + nx * sw;
                    y += (n1 - 0.5F) * p.amp * wob + ny * sw;
                    z += (n2 - 0.5F) * p.amp * wob + nz * sw;
                }
                // part space -> anchor space (the baked 3x4) -> world (the shot)
                float ax = L[0] * x + L[1] * y + L[2] * z + L[3];
                float ay = L[4] * x + L[5] * y + L[6] * z + L[7];
                float az = L[8] * x + L[9] * y + L[10] * z + L[11];
                org.joml.Vector3f wv = s.M.transformPosition(ax, ay, az, new org.joml.Vector3f());
                wx[v] = (float) (wv.x - cam.x);
                wy[v] = (float) (wv.y - cam.y);
                wz[v] = (float) (wv.z - cam.z);
                // the material: scrolling noise through its colour ramp, transparent where the noise is thin
                // the arrow's fire runs back from its tip like a living flame; everything else simmers in place
                float heat = flowing ? noise(x * 2.6F + sec * 7.5F, y * 3.4F, z * 3.4F - sec * 2.0F) : noise(x * 3.1F, y * 3.1F, z * 3.1F - sec * 1.6F);
                float alpha = smooth(0.18F, 0.42F, heat);
                if (p.curve && (p.u[v] < s0 - 0.01F || p.u[v] > s1 + 0.01F)) {
                    alpha = 0.0F;
                }
                col[v] = ramp(p.mat, 0.25F + 0.75F * heat, alpha);
            }
            int[] tri = p.tri;
            for (int k = 0; k < tri.length; k += 3) {
                int A = tri[k], B = tri[k + 1], C = tri[k + 2];
                if ((col[A] >>> 24) == 0 && (col[B] >>> 24) == 0 && (col[C] >>> 24) == 0) {
                    continue;
                }
                vtx(buf, wx[A], wy[A], wz[A], col[A]);
                vtx(buf, wx[B], wy[B], wz[B], col[B]);
                vtx(buf, wx[C], wy[C], wz[C], col[C]);
                tris++;
            }
        }
        return tris;
    }

    private static void vtx(BufferBuilder b, float x, float y, float z, int argb) {
        b.vertex(x, y, z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24).endVertex();
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // the film's colour ramps (VFX_fire_hot / VFX_arrow_wing / VFX_arrowhead), sparks and the cube's glow
    private static final float[][] RAMP_HOT = {{0.0F, 1.0F, 0.3F, 0.0F}, {0.35F, 1.0F, 0.62F, 0.02F}, {0.52F, 1.0F, 0.9F, 0.15F}, {0.68F, 1.0F, 1.0F, 0.75F}};
    private static final float[][] RAMP_WING = {{0.0F, 0.55F, 0.03F, 0.0F}, {0.42F, 1.0F, 0.22F, 0.0F}, {0.55F, 1.0F, 0.55F, 0.02F}, {0.66F, 1.0F, 0.85F, 0.1F},
            {0.78F, 1.0F, 1.0F, 0.7F}};
    private static final float[][] RAMP_HEAD = {{0.0F, 0.85F, 0.12F, 0.0F}, {0.4F, 1.0F, 0.35F, 0.02F}, {0.55F, 1.0F, 0.62F, 0.05F}, {0.68F, 1.0F, 0.88F, 0.25F},
            {0.8F, 1.0F, 1.0F, 0.8F}};

    private static int ramp(int mat, float x, float alpha) {
        float r, g, b;
        if (mat == 3) {
            r = 1.0F; g = 0.55F; b = 0.08F;
        } else if (mat == 4) {
            r = 1.0F; g = 0.8F; b = 0.1F;
        } else {
            float[][] R = mat == 0 ? RAMP_HOT : (mat == 2 ? RAMP_HEAD : RAMP_WING);
            int i = 0;
            while (i < R.length - 1 && x > R[i + 1][0]) {
                i++;
            }
            if (i >= R.length - 1) {
                r = R[i][1]; g = R[i][2]; b = R[i][3];
            } else {
                float k = Mth.clamp((x - R[i][0]) / (R[i + 1][0] - R[i][0]), 0.0F, 1.0F);
                r = Mth.lerp(k, R[i][1], R[i + 1][1]);
                g = Mth.lerp(k, R[i][2], R[i + 1][2]);
                b = Mth.lerp(k, R[i][3], R[i + 1][3]);
            }
        }
        int A = (int) (Mth.clamp(alpha * 0.85F, 0.0F, 1.0F) * 255.0F);
        return A << 24 | (int) (r * 255.0F) << 16 | (int) (g * 255.0F) << 8 | (int) (b * 255.0F);
    }

    // ------------------------------------------------------------------------------------------------ noise

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Smooth value noise in 0..1. */
    static float noise(float x, float y, float z) {
        int ix = Mth.floor(x), iy = Mth.floor(y), iz = Mth.floor(z);
        float fx = x - ix, fy = y - iy, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float a = Mth.lerp(fx, hash(ix, iy, iz), hash(ix + 1, iy, iz));
        float b = Mth.lerp(fx, hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz));
        float c = Mth.lerp(fx, hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1));
        float d = Mth.lerp(fx, hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1));
        return Mth.lerp(fz, Mth.lerp(fy, a, b), Mth.lerp(fy, c, d));
    }
}
