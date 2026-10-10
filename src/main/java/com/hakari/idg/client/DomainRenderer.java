package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Idle Death Gamble, built out of raw geometry: the barrier forming as a black sphere with a
 * fresnel rim, then the interior — a pink-lit train station under a dome, a rushing commuter
 * train, a three-storey slot machine in the sky with real spinning reels, raining pachinko
 * balls, light pillars and sweeping spotlights. Plus the golden jackpot aura.
 */
public final class DomainRenderer {

    private static final class Visual {
        float yaw = Float.NaN;
        long trainStart = -1;
        long lastAutoTrain;
        long openedAt = -1;
    }

    private static final Map<Integer, Visual> VISUALS = new HashMap<>();

    private static Visual visual(int caster) {
        return VISUALS.computeIfAbsent(caster, k -> new Visual());
    }

    public static void train(int caster) {
        Visual v = visual(caster);
        v.trainStart = Vfx.now();
        v.lastAutoTrain = v.trainStart;
    }

    public static void clear() {
        VISUALS.clear();
    }

    private static final double TRACK_Z = 8.0;
    private static final long TRAIN_MS = 2200;

    // ═══════════════════════════════ entry points ═══════════════════════════════

    public static void solid(Vfx.Ctx c) {
        for (StatePacket s : ClientState.all()) {
            if (!(s.domainActive || s.castTicks > 0)) continue;
            Visual v = visual(s.entityId);
            captureYaw(s, v);
            float e = ClientState.castElapsedSeconds(s);
            Vec3 center = ClientState.center(s);
            double R = s.radius;
            c.at(center);
            Matrix4f m = c.m();
            Vec3 camRel = c.camRel(center);
            if (e >= 2.45f && e < 3.95f) {
                float f = Mth.clamp((e - 2.45f) / 1.45f, 0, 1);
                double r = R * Ease.outExpo(f);
                fresnelSphere(c.vc, m, r, camRel, 0xFF050005, 0xFFFF4FB8);
            } else if (e >= 3.95f) {
                if (v.openedAt < 0) v.openedAt = c.t;
                autoTrain(s, v, c.t, center);
                interiorSolid(c, s, v, R, camRel);
            }
            c.pop();
        }
    }

    public static void glow(Vfx.Ctx c) {
        for (StatePacket s : ClientState.all()) {
            if (s.domainActive || s.castTicks > 0) {
                Visual v = visual(s.entityId);
                float e = ClientState.castElapsedSeconds(s);
                Vec3 center = ClientState.center(s);
                double R = s.radius;
                c.at(center);
                if (e >= 2.45f && e < 3.95f) {
                    float f = Mth.clamp((e - 2.45f) / 1.45f, 0, 1);
                    double r = R * Ease.outExpo(f);
                    Draw.sphere(c.vc, c.m(), 0, 0, 0, r * 1.03, 16, 24, Draw.alpha(0xFFFF2D7A, 0.18f * (1 - f * 0.5f)));
                    Draw.ringXZ(c.vc, c.m(), new Vec3(0, 0.1, 0), r * 0.98, r * 1.15, Draw.alpha(0xFFFF4FB8, 0.9f), 0, 64);
                } else if (e >= 3.95f) {
                    interiorGlow(c, s, v, R);
                }
                c.pop();
            }
            if (s.jackpotTicks > 0 || s.jackpotCutTicks > 0) aura(c, s);
        }
    }

    public static void text(Vfx.Ctx c, MultiBufferSource.BufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        for (StatePacket s : ClientState.all()) {
            if (!(s.domainActive || s.castTicks > 0)) continue;
            if (ClientState.castElapsedSeconds(s) < 3.95f) continue;
            Vec3 center = ClientState.center(s);
            double R = s.radius;
            double spin = c.t / 9000.0;
            String[] glyphs = {"坐", "殺", "博", "徒"};
            for (int i = 0; i < 4; i++) {
                double a = spin + i * Math.PI / 2;
                Vec3 p = center.add(Math.cos(a) * R * 0.86, R * 0.42, Math.sin(a) * R * 0.86);
                billboardText(c, font, buffers, glyphs[i], p, 0.55f, 0xFFFF4FB8);
            }
            Visual v = visual(s.entityId);
            Vec3 mpos = machinePos(center, v);
            billboardText(c, font, buffers, "IDLE DEATH GAMBLE", mpos.add(0, 4.4, 0), 0.09f, 0xFFFFD34D);
            billboardText(c, font, buffers, "CR 私鉄純愛列車", mpos.add(0, 3.7, 0), 0.07f, 0xFFFFFFFF);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 sign = center.add(side * 9, 3.9, TRACK_Z - 3.2);
                billboardText(c, font, buffers, "純愛駅", sign, 0.06f, 0xFF1A0010);
                billboardText(c, font, buffers, "PURE LOVE STA.", sign.add(0, -0.45, 0), 0.025f, 0xFF1A0010);
            }
        }
        buffers.endBatch();
    }

    // ═══════════════════════════════ pieces ═══════════════════════════════

    private static void captureYaw(StatePacket s, Visual v) {
        if (!Float.isNaN(v.yaw)) return;
        Entity e = Vfx.entity(s.entityId);
        v.yaw = e != null ? e.getYRot() : 0f;
    }

    private static Vec3 machineLocal(Visual v) {
        float y = (Float.isNaN(v.yaw) ? 0 : v.yaw) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * 9, 14.6, Mth.cos(y) * 9);
    }

    private static Vec3 machinePos(Vec3 center, Visual v) {
        return center.add(machineLocal(v));
    }

    private static void autoTrain(StatePacket s, Visual v, long t, Vec3 center) {
        if (!s.domainActive) return;
        if (t - v.lastAutoTrain > 14000) {
            v.trainStart = t;
            v.lastAutoTrain = t;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.position().distanceTo(center) < s.radius + 4) {
                Sfx.play(SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, 0.6f);
                Sfx.later(160, SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 0.6f);
                Sfx.later(900, SoundEvents.ENDER_DRAGON_FLAP, 0.5f, 0.8f);
            }
        }
    }

    private static void fresnelSphere(VertexConsumer vc, Matrix4f m, double r, Vec3 camRel, int core, int rim) {
        Draw.sphere(vc, m, 0, 0, 0, r, 24, 36, (nx, ny, nz) -> {
            Vec3 p = new Vec3(nx * r, ny * r, nz * r);
            Vec3 view = p.subtract(camRel).normalize();
            double facing = Math.abs(nx * view.x + ny * view.y + nz * view.z);
            float edge = (float) Math.pow(1 - facing, 3);
            return Draw.lerp(core, rim, edge);
        });
    }

    private static void interiorSolid(Vfx.Ctx c, StatePacket s, Visual v, double R, Vec3 camRel) {
        VertexConsumer vc = c.vc;
        Matrix4f m = c.m();
        boolean inside = camRel.length() < R;
        if (inside) {
            // dome: deep magenta gradient
            Draw.sphere(vc, m, 0, 0, 0, R, 24, 40, (nx, ny, nz) -> {
                if (ny > 0) return Draw.lerp(0xFF5A0838, 0xFF0E000A, (float) Math.pow(ny, 0.6));
                return Draw.lerp(0xFF5A0838, 0xFF080004, (float) -ny * 3);
            });
        } else {
            fresnelSphere(vc, m, R, camRel, 0xFF050005, 0xFFFF4FB8);
            return;
        }
        // floor
        Draw.ringXZ(vc, m, new Vec3(0, 0.02, 0), 0, R, 0xFF1A0A16, 0xFF0C040A, 64);

        // ── station: platforms, rails, sleepers, pillars, signs ──
        platform(vc, m, R, TRACK_Z - 5.0, TRACK_Z - 1.8, true);
        platform(vc, m, R, TRACK_Z + 1.8, TRACK_Z + 5.0, false);
        double half = Math.sqrt(Math.max(0, R * R - TRACK_Z * TRACK_Z));
        for (double x = -half; x < half; x += 0.8) {
            Draw.box(vc, m, x, 0.02, TRACK_Z - 1.3, x + 0.3, 0.14, TRACK_Z + 1.3, 0xFF3A2620);
        }
        Draw.box(vc, m, -half, 0.14, TRACK_Z - 0.8, half, 0.3, TRACK_Z - 0.7, 0xFFB8BCC6);
        Draw.box(vc, m, -half, 0.14, TRACK_Z + 0.7, half, 0.3, TRACK_Z + 0.8, 0xFFB8BCC6);
        for (int side = -1; side <= 1; side += 2) {
            double sx = side * 9;
            Draw.box(vc, m, sx - 0.12, 0.5, TRACK_Z - 3.3, sx + 0.12, 3.5, TRACK_Z - 3.1, 0xFF3C3440);
            Draw.box(vc, m, sx - 1.6, 3.5, TRACK_Z - 3.25, sx + 1.6, 4.3, TRACK_Z - 3.15, 0xFFF2F0F4);
            Draw.box(vc, m, sx - 1.6, 3.5, TRACK_Z - 3.27, sx + 1.6, 3.6, TRACK_Z - 3.13, 0xFFFF4FB8);
        }
        for (double x = -half + 3; x < half - 2; x += 6) {
            Draw.box(vc, m, x - 0.25, 0.5, TRACK_Z + 3.5, x + 0.25, 6.0, TRACK_Z + 4.0, 0xFF2A2230);
            Draw.box(vc, m, x - 0.25, 0.5, TRACK_Z - 4.0, x + 0.25, 6.0, TRACK_Z - 3.5, 0xFF2A2230);
        }
        // canopy
        Draw.box(vc, m, -half + 1, 6.0, TRACK_Z - 4.4, half - 1, 6.3, TRACK_Z + 4.4, 0xFF1C1420);

        // ── the train ──
        if (v.trainStart >= 0) train(vc, m, c.t - v.trainStart, R);

        // ── slot machine ──
        Vec3 ml = machineLocal(v);
        c.ps.pushPose();
        c.ps.translate(ml.x, ml.y, ml.z);
        c.ps.mulPose(Axis.YP.rotationDegrees(-v.yaw));
        machine(c, ReelSet.of(s.entityId));
        c.ps.popPose();

        // ── pachinko balls (solid silver cores) ──
        balls(c, R, true);
    }

    private static void platform(VertexConsumer vc, Matrix4f m, double R, double z0, double z1, boolean edgeHigh) {
        double zz = Math.max(Math.abs(z0), Math.abs(z1));
        double half = Math.sqrt(Math.max(0, R * R - zz * zz));
        Draw.box(vc, m, -half, 0.0, z0, half, 0.5, z1, 0xFFB9B4BE);
        double ez0 = edgeHigh ? z1 - 0.5 : z0 + 0.1, ez1 = edgeHigh ? z1 - 0.1 : z0 + 0.5;
        Draw.box(vc, m, -half, 0.5, ez0, half, 0.52, ez1, 0xFFF2C230);
    }

    private static void train(VertexConsumer vc, Matrix4f m, long age, double R) {
        if (age > TRAIN_MS) return;
        double span = 2 * R + 80;
        double head = -R - 40 + span * (age / (double) TRAIN_MS);
        for (int car = 0; car < 4; car++) {
            double x1 = head - car * 8.4, x0 = x1 - 8.0;
            double z0 = TRACK_Z - 1.45, z1 = TRACK_Z + 1.45;
            Draw.box(vc, m, x0, 0.4, z0, x1, 3.4, z1, 0xFFF4F0F2);
            Draw.box(vc, m, x0, 1.0, z0 - 0.02, x1, 1.3, z1 + 0.02, 0xFFFF4FB8);
            Draw.box(vc, m, x0, 3.4, z0 + 0.2, x1, 3.6, z1 - 0.2, 0xFFB0A8B4);
            for (double wx = x0 + 0.6; wx < x1 - 1.0; wx += 1.6) {
                Draw.box(vc, m, wx, 1.6, z0 - 0.03, wx + 1.0, 2.7, z1 + 0.03, 0xFF2A1E26);
            }
            Draw.box(vc, m, x0, 0.3, z0 + 0.3, x1, 0.45, z1 - 0.3, 0xFF202024);
        }
        Draw.box(vc, m, head, 0.4, TRACK_Z - 1.3, head + 0.6, 3.1, TRACK_Z + 1.3, 0xFFE8E2E6);
    }

    private static void machine(Vfx.Ctx c, ReelSet reels) {
        VertexConsumer vc = c.vc;
        Matrix4f m = c.m();
        // cabinet (front faces -Z, toward the caster)
        Draw.box(vc, m, -5.6, -3.4, 0, 5.6, 3.4, 1.4, 0xFF2A0820);
        Draw.box(vc, m, -4.6, -2.0, -0.25, 4.6, -1.8, 0.05, 0xFFFFD34D);
        Draw.box(vc, m, -4.6, 1.8, -0.25, 4.6, 2.0, 0.05, 0xFFFFD34D);
        Draw.box(vc, m, -4.6, -2.0, -0.25, -4.4, 2.0, 0.05, 0xFFFFD34D);
        Draw.box(vc, m, 4.4, -2.0, -0.25, 4.6, 2.0, 0.05, 0xFFFFD34D);
        Draw.box(vc, m, -5.6, 3.4, -0.1, 5.6, 4.0, 1.2, 0xFFFF4FB8);
        Draw.box(vc, m, -2.0, -3.4, -0.6, 2.0, -2.6, 0.2, 0xFFC9A030);
        long t = c.t;
        for (int r = 0; r < 3; r++) {
            double rx = (r - 1) * 2.9;
            double pos = reels.position(r, t);
            int segs = 21;
            double rad = 1.55;
            for (int i = 0; i < segs; i++) {
                double a0 = (i / (double) segs) * Math.PI * 2 - pos * Math.PI * 2 / 7;
                double a1 = ((i + 1) / (double) segs) * Math.PI * 2 - pos * Math.PI * 2 / 7;
                float shade = (float) Math.max(0.25, -Math.cos((a0 + a1) / 2) * -1);
                int col = Draw.scaleRgb((i / 3) % 2 == 0 ? 0xFFF6F0E4 : 0xFFE6DCCB, 0.35f + 0.65f * (float) Math.max(0, Math.cos((a0 + a1) / 2)));
                Vec3 p0 = new Vec3(rx - 1.1, Math.sin(a0) * rad, 0.4 - Math.cos(a0) * rad);
                Vec3 p1 = new Vec3(rx + 1.1, Math.sin(a0) * rad, 0.4 - Math.cos(a0) * rad);
                Vec3 p2 = new Vec3(rx + 1.1, Math.sin(a1) * rad, 0.4 - Math.cos(a1) * rad);
                Vec3 p3 = new Vec3(rx - 1.1, Math.sin(a1) * rad, 0.4 - Math.cos(a1) * rad);
                Draw.quad(vc, m, p0, p1, p2, p3, col);
            }
        }
    }

    /** Seven-segment digit in the local XY plane (facing -Z). */
    private static void seg7(VertexConsumer vc, Matrix4f m, int digit, double size, int col) {
        int[] masks = {0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F};
        int mask = masks[Mth.clamp(digit, 0, 9)];
        double w = size * 0.5, h = size, t = size * 0.13;
        double[][] seg = {
                {-w, h - t, w, h},          // a top
                {w - t, 0, w, h},           // b upper right
                {w - t, -h, w, 0},          // c lower right
                {-w, -h, w, -h + t},        // d bottom
                {-w, -h, -w + t, 0},        // e lower left
                {-w, 0, -w + t, h},         // f upper left
                {-w, -t / 2, w, t / 2},     // g middle
        };
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) == 0) continue;
            double[] s = seg[i];
            // x is mirrored because the face is viewed from -Z
            Draw.quad(vc, m, new Vec3(-s[0], s[1], 0), new Vec3(-s[2], s[1], 0), new Vec3(-s[2], s[3], 0), new Vec3(-s[0], s[3], 0), col);
        }
    }

    private static void interiorGlow(Vfx.Ctx c, StatePacket s, Visual v, double R) {
        VertexConsumer vc = c.vc;
        Matrix4f m = c.m();
        long t = c.t;
        float pulse = 0.65f + 0.35f * Mth.sin(t / 300f);

        // dome lattice
        for (double ny : new double[]{0.15, 0.4, 0.65, 0.85}) {
            double rr = R * Math.sqrt(1 - ny * ny) * 0.995;
            Draw.ring(vc, m, new Vec3(0, R * ny * 0.995, 0), Draw.X, Draw.Z, rr - 0.12, rr, 0, Draw.alpha(0xFFFF4FB8, 0.22f * pulse), 64);
        }
        // floor grid + travelling pulse rings
        for (double x = -R + 2; x < R; x += 2) {
            double h = Math.sqrt(Math.max(0, R * R - x * x));
            Draw.quad(vc, m, new Vec3(x - 0.03, 0.05, -h), new Vec3(x + 0.03, 0.05, -h), new Vec3(x + 0.03, 0.05, h), new Vec3(x - 0.03, 0.05, h), Draw.alpha(0xFFFF4FB8, 0.16f));
            Draw.quad(vc, m, new Vec3(-h, 0.05, x - 0.03), new Vec3(-h, 0.05, x + 0.03), new Vec3(h, 0.05, x + 0.03), new Vec3(h, 0.05, x - 0.03), Draw.alpha(0xFFFF4FB8, 0.16f));
        }
        float wave = (t % 2400) / 2400f;
        Draw.ringXZ(vc, m, new Vec3(0, 0.06, 0), R * wave - 0.6, R * wave, 0, Draw.alpha(0xFFFF8AD8, 0.5f * (1 - wave)), 72);

        // light pillars
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + t / 20000.0;
            Vec3 b = new Vec3(Math.cos(a) * R * 0.8, 0, Math.sin(a) * R * 0.8);
            int col = i % 2 == 0 ? 0xFFFF4FB8 : 0xFFFFD34D;
            float k = 0.5f + 0.5f * Mth.sin(t / 250f + i);
            Draw.beam(vc, m, b, R * 1.1, 0.35, Draw.alpha(0xFFFFFFFF, 0.5f * k), Draw.alpha(col, 0), 12);
            Draw.beam(vc, m, b, R * 0.9, 0.9, Draw.alpha(col, 0.22f * k), Draw.alpha(col, 0), 12);
        }
        // sweeping spotlights
        Vec3 apex = new Vec3(0, R * 0.9, 0);
        for (int i = 0; i < 4; i++) {
            double a = t / 1400.0 + i * Math.PI / 2;
            Vec3 floor = new Vec3(Math.cos(a) * R * 0.5, 0, Math.sin(a * 1.3) * R * 0.5);
            Vec3 dir = floor.subtract(apex);
            Draw.cone(vc, m, apex, dir, dir.length(), 3.2, Draw.alpha(0xFFFFFFFF, 0.25f), Draw.alpha(i % 2 == 0 ? 0xFFFF4FB8 : 0xFF5CF2FF, 0.0f), 24);
            Draw.ringXZ(vc, m, floor.add(0, 0.07, 0), 0, 3.2, Draw.alpha(0xFFFFFFFF, 0.18f), 0, 24);
        }
        // train lights + motion streaks
        if (v.trainStart >= 0 && t - v.trainStart < TRAIN_MS) {
            double span = 2 * R + 80;
            double head = -R - 40 + span * ((t - v.trainStart) / (double) TRAIN_MS);
            Vec3 cam = c.camRel(ClientState.center(s));
            for (int car = 0; car < 4; car++) {
                double x1 = head - car * 8.4, x0 = x1 - 8.0;
                for (double wx = x0 + 0.6; wx < x1 - 1.0; wx += 1.6) {
                    for (int side = -1; side <= 1; side += 2) {
                        double z = TRACK_Z + side * 1.5;
                        Draw.quad(vc, m, new Vec3(wx, 1.65, z), new Vec3(wx + 1.0, 1.65, z), new Vec3(wx + 1.0, 2.65, z), new Vec3(wx, 2.65, z), Draw.alpha(0xFFFFE7A8, 0.85f));
                    }
                }
            }
            for (int k = 0; k < 10; k++) {
                double y = 0.6 + Draw.hash(k * 7) * 2.6;
                double z = TRACK_Z + (Draw.hash(k * 13) - 0.5) * 3.2;
                Draw.ribbon(vc, m, new Vec3(head - 34 - Draw.hash(k) * 10, y, z), new Vec3(head, y, z), 0.0, 0.05, 0, Draw.alpha(0xFFFFFFFF, 0.6f), cam);
            }
            Draw.glowOrb(vc, m, head + 0.7, 2.6, TRACK_Z - 0.8, 0.35, 0xFFFFF4C0, 1f);
            Draw.glowOrb(vc, m, head + 0.7, 2.6, TRACK_Z + 0.8, 0.35, 0xFFFFF4C0, 1f);
        }

        // slot machine glow: 7-segment numbers on the reels + chasing marquee bulbs
        Vec3 ml = machineLocal(v);
        c.ps.pushPose();
        c.ps.translate(ml.x, ml.y, ml.z);
        c.ps.mulPose(Axis.YP.rotationDegrees(-v.yaw));
        ReelSet reels = ReelSet.of(s.entityId);
        for (int r = 0; r < 3; r++) {
            double rx = (r - 1) * 2.9;
            double pos = reels.position(r, t);
            int base = (int) Math.floor(pos);
            for (int k = -1; k <= 1; k++) {
                double faceAng = (base + k - pos) * Math.PI * 2 / 7;
                double cs = Math.cos(faceAng);
                if (cs <= 0.15) continue;
                int n = ReelSet.numberAt(base + k);
                c.ps.pushPose();
                c.ps.translate(rx, 0, 0.4);
                c.ps.mulPose(Axis.XP.rotation((float) faceAng));
                c.ps.translate(0, 0, -1.58);
                seg7(vc, c.m(), n, 0.62, Draw.alpha(ReelSet.numberColor(n), (float) cs));
                c.ps.popPose();
            }
            if (reels.stopped(r, t) && reels.spinning(t)) {
                Draw.glowOrb(vc, c.m(), rx, 0, -1.3, 0.8, 0xFFFFD34D, 0.35f + 0.25f * Mth.sin(t / 60f));
            }
        }
        Matrix4f mm = c.m();
        int bulbs = 44;
        for (int i = 0; i < bulbs; i++) {
            double u = i / (double) bulbs;
            double x, y;
            if (u < 0.25) { x = -5.4 + u / 0.25 * 10.8; y = 3.25; }
            else if (u < 0.5) { x = 5.4; y = 3.25 - (u - 0.25) / 0.25 * 6.5; }
            else if (u < 0.75) { x = 5.4 - (u - 0.5) / 0.25 * 10.8; y = -3.25; }
            else { x = -5.4; y = -3.25 + (u - 0.75) / 0.25 * 6.5; }
            boolean lit = ((i + t / 70) % 4) == 0;
            Draw.glowOrb(vc, mm, x, y, -0.15, lit ? 0.18 : 0.1, 0xFFFFD34D, lit ? 1f : 0.35f);
        }
        c.ps.popPose();

        balls(c, R, false);
    }

    private static void balls(Vfx.Ctx c, double R, boolean solid) {
        VertexConsumer vc = c.vc;
        Matrix4f m = c.m();
        double time = c.t / 1000.0;
        double top = R * 0.72;
        double g = 9.8;
        double tf = Math.sqrt(2 * top / g);
        for (int i = 0; i < 72; i++) {
            double period = tf + 1.2 + Draw.hash(i * 3) * 1.4;
            double ph = (time + Draw.hash(i * 5) * period) % period;
            long cycle = (long) ((time + Draw.hash(i * 5) * period) / period);
            double ang = Draw.hash(i * 11 + cycle * 101) * Math.PI * 2;
            double rad = Math.sqrt(Draw.hash(i * 13 + cycle * 37)) * R * 0.82;
            double x = Math.cos(ang) * rad, z = Math.sin(ang) * rad;
            double y;
            if (ph < tf) y = top - 0.5 * g * ph * ph;
            else {
                double b = ph - tf;
                y = 0.14 + Math.abs(Math.sin(b * 7)) * 1.1 * Math.exp(-b * 2.2);
                x += Math.cos(ang + 1) * b * 0.9;
                z += Math.sin(ang + 1) * b * 0.9;
            }
            if (solid) {
                Draw.sphere(vc, m, x, y, z, 0.13, 5, 8, (nx, ny, nz) -> Draw.lerp(0xFF6C6E78, 0xFFF4F6FF, (float) (ny * 0.5 + 0.5)));
            } else {
                Draw.sphere(vc, m, x, y, z, 0.22, 4, 6, Draw.alpha(0xFFFF8AD8, 0.25f));
            }
        }
    }

    // ═══════════════════════════════ jackpot aura ═══════════════════════════════

    private static void aura(Vfx.Ctx c, StatePacket s) {
        Entity e = Vfx.entity(s.entityId);
        if (e == null) return;
        Vec3 base = e.getPosition(c.partial);
        long t = c.t;
        c.at(base);
        VertexConsumer vc = c.vc;
        Matrix4f m = c.m();
        Vec3 cam = c.camRel(base);
        double time = t / 1000.0;
        for (int h = 0; h < 3; h++) {
            Vec3 prev = null;
            for (int k = 0; k <= 26; k++) {
                double y = k * 0.085;
                double a = time * 4 + h * 2.094 + k * 0.33;
                double r = 0.72 + 0.12 * Math.sin(time * 3 + k * 0.4);
                Vec3 p = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
                if (prev != null) {
                    float al = (float) Math.sin(Math.PI * k / 26.0);
                    Draw.ribbon(vc, m, prev, p, 0.05, 0.05, Draw.alpha(0xFFFFD34D, al), Draw.alpha(0xFFFFF4C0, al), cam);
                }
                prev = p;
            }
        }
        float beat = (t % 700) / 700f;
        Draw.ringXZ(vc, m, new Vec3(0, 0.05, 0), 0.6 + beat * 1.6, 0.8 + beat * 1.6, Draw.alpha(0xFFFFD34D, 1 - beat), 0, 40);
        Draw.ringXZ(vc, m, new Vec3(0, 0.05, 0), 0.0, 1.0, Draw.alpha(0xFFFFD34D, 0.25f), 0, 32);
        for (int i = 0; i < 16; i++) {
            double ph = (time * 0.8 + Draw.hash(i)) % 1.0;
            double a = Draw.hash(i * 7) * Math.PI * 2;
            Vec3 p = new Vec3(Math.cos(a) * 0.6, ph * 2.6, Math.sin(a) * 0.6);
            Draw.billboard(vc, m, p, 0.06 + 0.06 * (1 - ph), Draw.alpha(0xFFFFF4C0, (float) (1 - ph)), c.cam.getLeftVector(), c.cam.getUpVector());
        }
        // steam: the body boiling with limitless cursed energy
        for (int i = 0; i < 8; i++) {
            double ph = (time * 0.5 + Draw.hash(i * 19)) % 1.0;
            Vec3 p = new Vec3((Draw.hash(i * 3) - 0.5) * 0.7, 1.2 + ph * 1.6, (Draw.hash(i * 5) - 0.5) * 0.7);
            Draw.billboard(vc, m, p, 0.3 + ph * 0.5, Draw.alpha(0xFFFFFFFF, (float) (0.12 * (1 - ph))), c.cam.getLeftVector(), c.cam.getUpVector());
        }
        if ((t / 90) % 5 == 0) {
            Vec3 a = Draw.randDir(t / 90).scale(0.5).add(0, 1.1, 0);
            Vec3 b = a.add(Draw.randDir(t / 90 + 3).scale(0.6));
            Draw.ribbon(vc, m, a, b, 0.03, 0.01, 0xFFFFFFFF, 0xFFFF4FB8, cam);
        }
        c.pop();
    }

    // ═══════════════════════════════ text ═══════════════════════════════

    private static void billboardText(Vfx.Ctx c, Font font, MultiBufferSource buffers, String s, Vec3 world, float scale, int color) {
        PoseStack ps = c.ps;
        ps.pushPose();
        ps.translate(world.x - c.camPos.x, world.y - c.camPos.y, world.z - c.camPos.z);
        ps.mulPose(c.cam.rotation());
        ps.scale(-scale, -scale, scale);
        float x = -font.width(s) / 2f;
        font.drawInBatch(s, x, -4, color, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private DomainRenderer() {}
}
