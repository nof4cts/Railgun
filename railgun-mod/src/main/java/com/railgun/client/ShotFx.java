package com.railgun.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.railgun.ClientConfig;
import com.railgun.net.ShotPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/** One fired shot: volumetric beam, helix, lightning, shockwaves, flares. Pure additive geometry. */
public final class ShotFx {
    public static final int LIFE = 22;      // beam body
    public static final int ARC_LIFE = 48;  // lingering lightning

    private final Vec3 start, end, dir, normal;
    private final float len, power;
    private final boolean hitBlock;
    private final long startTick, seed;
    private final float[] beamBasis, impactBasis;

    public ShotFx(ShotPacket p, long gameTime) {
        start = new Vec3(p.mx, p.my, p.mz);
        end = new Vec3(p.ex, p.ey, p.ez);
        Vec3 d = end.subtract(start);
        len = (float) d.length();
        dir = len > 1e-3 ? d.scale(1.0 / len) : new Vec3(0, 0, -1);
        power = p.power;
        hitBlock = p.face >= 0;
        if (hitBlock) {
            Direction f = Direction.from3DDataValue(p.face);
            normal = new Vec3(f.getStepX(), f.getStepY(), f.getStepZ());
        } else normal = dir.scale(-1);
        startTick = gameTime;
        seed = Double.doubleToLongBits(p.mx * 31 + p.ey * 17 + p.ez) ^ gameTime;
        beamBasis = Draw.basis((float) dir.x, (float) dir.y, (float) dir.z);
        impactBasis = Draw.basis((float) normal.x, (float) normal.y, (float) normal.z);
    }

    public boolean dead(long gameTime) { return gameTime - startTick > ARC_LIFE; }

    // ------------------------------------------------------------------ particles
    public void burst(ClientLevel lv) {
        Random r = new Random(seed);
        boolean heavy = ClientConfig.HEAVY_PARTICLES.get();
        int mult = heavy ? 1 : 0;
        lv.addParticle(ParticleTypes.FLASH, true, start.x, start.y, start.z, 0, 0, 0);
        for (int i = 0; i < 18 + 22 * mult; i++) {
            lv.addParticle(ParticleTypes.ELECTRIC_SPARK, true, start.x, start.y, start.z,
                    dir.x * 0.9 + (r.nextDouble() - .5) * .9, dir.y * 0.9 + (r.nextDouble() - .5) * .9, dir.z * 0.9 + (r.nextDouble() - .5) * .9);
        }
        float step = Math.max(heavy ? 1.6f : 4f, len / (heavy ? 90f : 30f));
        for (float s = 1.5f; s < len; s += step) {
            double x = start.x + dir.x * s, y = start.y + dir.y * s, z = start.z + dir.z * s;
            lv.addParticle(ParticleTypes.END_ROD, true, x, y, z, (r.nextDouble() - .5) * .08, (r.nextDouble() - .5) * .08, (r.nextDouble() - .5) * .08);
            if (r.nextInt(3) == 0) {
                lv.addParticle(ParticleTypes.ELECTRIC_SPARK, true, x, y, z, (r.nextDouble() - .5) * .5, (r.nextDouble() - .5) * .5, (r.nextDouble() - .5) * .5);
            }
        }
        // impact
        lv.addParticle(ParticleTypes.FLASH, true, end.x, end.y, end.z, 0, 0, 0);
        lv.addParticle(ParticleTypes.EXPLOSION, true, end.x, end.y, end.z, 0, 0, 0);
        if (power > 0.9f) lv.addParticle(ParticleTypes.SONIC_BOOM, true, end.x, end.y, end.z, 0, 0, 0);
        int sparks = heavy ? 90 : 25;
        for (int i = 0; i < sparks; i++) {
            double vx = r.nextGaussian(), vy = r.nextGaussian(), vz = r.nextGaussian();
            if (hitBlock && vx * normal.x + vy * normal.y + vz * normal.z < 0) { vx = -vx; vy = -vy; vz = -vz; }
            double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6, sp = 0.4 + r.nextDouble() * 1.4;
            lv.addParticle(ParticleTypes.ELECTRIC_SPARK, true, end.x, end.y, end.z, vx / l * sp, vy / l * sp, vz / l * sp);
            if (i % 3 == 0) lv.addParticle(ParticleTypes.FIREWORK, true, end.x, end.y, end.z, vx / l * sp * .6, vy / l * sp * .6, vz / l * sp * .6);
            if (i % 5 == 0) lv.addParticle(ParticleTypes.LARGE_SMOKE, true, end.x, end.y, end.z, vx / l * .15, vy / l * .15 + .05, vz / l * .15);
        }
        if (hitBlock) {
            BlockState st = lv.getBlockState(BlockPos.containing(end.subtract(normal.scale(0.1))));
            if (!st.isAir()) {
                for (int i = 0; i < (heavy ? 40 : 12); i++) {
                    lv.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), true,
                            end.x, end.y, end.z,
                            normal.x * .5 + r.nextGaussian() * .35, normal.y * .5 + r.nextGaussian() * .35 + .1, normal.z * .5 + r.nextGaussian() * .35);
                }
            }
        }
    }

    // ------------------------------------------------------------------ rendering
    public void render(VertexConsumer vc, Matrix4f m, Vec3 cam, Camera camera, float pt, long gameTime) {
        float age = Math.max(0f, (gameTime - startTick) + pt);
        float sx = (float) (start.x - cam.x), sy = (float) (start.y - cam.y), sz = (float) (start.z - cam.z);
        float ex = (float) (end.x - cam.x), ey = (float) (end.y - cam.y), ez = (float) (end.z - cam.z);
        float dx = (float) dir.x, dy = (float) dir.y, dz = (float) dir.z;
        float t = age / LIFE;
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        float rx = -left.x, ry = -left.y, rz = -left.z;
        float[] bb = beamBasis;

        // ---------------- the beam: white-hot core, cyan sheath, blue bloom, violet halo
        if (t < 1f) {
            float k = (1f - t) * (1f - t);
            float punch = 1f + 2.6f * Math.max(0f, 1f - age / 1.6f);
            float base = (0.05f + 0.07f * power) * punch;
            float flick = 0.92f + 0.08f * Mth.sin(age * 11f);
            Draw.cylinder(vc, m, sx, sy, sz, dx, dy, dz, len, bb, base * (0.25f + 0.75f * k) * flick, base * (0.25f + 0.75f * k) * flick, 18, 1f, 1f, 1f, 1f, 0.9f);
            Draw.cylinder(vc, m, sx, sy, sz, dx, dy, dz, len, bb, base * 2.4f * (0.3f + 0.7f * k), base * 2.4f * (0.3f + 0.7f * k), 18, 0.55f, 0.95f, 1f, 0.75f * k, 0.5f * k);
            Draw.cylinder(vc, m, sx, sy, sz, dx, dy, dz, len, bb, base * (5f + 14f * t), base * (5f + 14f * t), 20, 0.25f, 0.55f, 1f, 0.30f * k, 0.18f * k);
            Draw.cylinder(vc, m, sx, sy, sz, dx, dy, dz, len, bb, base * (10f + 34f * t), base * (10f + 34f * t), 20, 0.55f, 0.30f, 1f, 0.13f * k, 0.06f * k);

            // double helix of plasma around the beam
            int n = Mth.clamp((int) (len / 0.6f), 8, 300);
            float[] pts = new float[n * 3];
            for (int h = 0; h < 2; h++) {
                float ph = h * 3.14159f;
                for (int i = 0; i < n; i++) {
                    float s = len * i / (n - 1f);
                    float ang = s * 0.55f + age * 0.9f + ph;
                    float rad = (0.35f + 1.1f * t) * (0.6f + 0.4f * Mth.sin(s * 0.13f + age * 0.3f));
                    float ca = Mth.cos(ang) * rad, sa = Mth.sin(ang) * rad;
                    pts[i * 3] = sx + dx * s + bb[0] * ca + bb[3] * sa;
                    pts[i * 3 + 1] = sy + dy * s + bb[1] * ca + bb[4] * sa;
                    pts[i * 3 + 2] = sz + dz * s + bb[2] * ca + bb[5] * sa;
                }
                Draw.ribbon(vc, m, pts, n, 0.2f * k + 0.03f, 0.2f * k + 0.03f, 0.4f, 0.9f, 1f, 0.9f * k, false);
            }

            // shock rings sprinting down the beam
            if (age < 8f) {
                for (int j = 0; j < 3; j++) {
                    float s = age * len * 0.22f - j * len * 0.07f;
                    if (s < 0 || s > len) continue;
                    Draw.softRing(vc, m, sx + dx * s, sy + dy * s, sz + dz * s, bb, 0.7f + 0.15f * age + j * 0.25f, 0.18f + 0.04f * age, 40,
                            0.6f, 0.95f, 1f, 0.7f * (1f - age / 8f));
                }
            }
        }

        // ---------------- lightning arcs, re-rolled every 2 ticks for crackle
        if (age < ARC_LIFE) {
            float arcFade = age < LIFE ? 1f : Math.max(0f, 1f - (age - LIFE) / (ARC_LIFE - LIFE));
            Random r = new Random(seed + (long) (age / 2f) * 7919L);
            int arcs = age < 16f ? 9 : 4;
            float[] pts = new float[3 * 9];
            for (int a = 0; a < arcs; a++) {
                float s = r.nextFloat() * len;
                float ph = r.nextFloat() * 6.2832f;
                float wx = bb[0] * Mth.cos(ph) + bb[3] * Mth.sin(ph), wy = bb[1] * Mth.cos(ph) + bb[4] * Mth.sin(ph), wz = bb[2] * Mth.cos(ph) + bb[5] * Mth.sin(ph);
                float reach = (1.5f + r.nextFloat() * 3.5f) * (0.5f + power);
                for (int j = 0; j < 9; j++) {
                    float f = j / 8f;
                    float jag = j == 0 ? 0f : 0.45f;
                    float j1 = (r.nextFloat() - .5f) * jag, j2 = (r.nextFloat() - .5f) * jag;
                    pts[j * 3] = sx + dx * (s + (r.nextFloat() - .5f) * .6f) + wx * reach * f + bb[0] * j1 + bb[3] * j2;
                    pts[j * 3 + 1] = sy + dy * s + wy * reach * f + bb[1] * j1 + bb[4] * j2;
                    pts[j * 3 + 2] = sz + dz * s + wz * reach * f + bb[2] * j1 + bb[5] * j2;
                }
                Draw.ribbon(vc, m, pts, 9, 0.28f, 0.04f, 0.3f, 0.7f, 1f, 0.45f * arcFade, false);
                Draw.ribbon(vc, m, pts, 9, 0.08f, 0.01f, 1f, 1f, 1f, 0.95f * arcFade, false);
            }
        }

        // ---------------- muzzle flare
        if (age < 9f) {
            float f = 1f - age / 9f; f *= f;
            float rad = (0.7f + 1.2f * power) * f;
            Draw.glowDisc(vc, m, sx, sy, sz, rx, ry, rz, up.x, up.y, up.z, rad * 1.6f, 22, 0.35f, 0.8f, 1f, 0.6f * f);
            Draw.glowDisc(vc, m, sx, sy, sz, rx, ry, rz, up.x, up.y, up.z, rad * 0.55f, 18, 1f, 1f, 1f, 1f);
            Draw.star(vc, m, sx, sy, sz, rx, ry, rz, up.x, up.y, up.z, 3, age * 0.35f, (3f + 4f * power) * f, 0.12f * (0.3f + f), 0.7f, 0.97f, 1f, 1f);
        }

        // ---------------- impact: flare, spikes, expanding shock rings
        if (age < 20f) {
            float f = 1f - age / 20f;
            float cx = ex + (float) normal.x * 0.15f, cy = ey + (float) normal.y * 0.15f, cz = ez + (float) normal.z * 0.15f;
            if (age < 11f) {
                float g = 1f - age / 11f; g *= g;
                float rad = (1.5f + 2.5f * power) * g;
                Draw.glowDisc(vc, m, cx, cy, cz, rx, ry, rz, up.x, up.y, up.z, rad * 1.7f, 24, 0.4f, 0.8f, 1f, 0.55f * g);
                Draw.glowDisc(vc, m, cx, cy, cz, rx, ry, rz, up.x, up.y, up.z, rad * 0.6f, 20, 1f, 1f, 1f, 1f);
                Draw.star(vc, m, cx, cy, cz, rx, ry, rz, up.x, up.y, up.z, 4, age * -0.25f, (4f + 6f * power) * g, 0.18f * (0.3f + g), 0.75f, 0.97f, 1f, 1f);
            }
            float sp = 0.9f + 0.8f * power;
            Draw.softRing(vc, m, cx, cy, cz, impactBasis, age * sp + 0.3f, 0.10f + 0.07f * age, 48, 0.5f, 0.9f, 1f, 0.95f * f * f);
            Draw.softRing(vc, m, cx, cy, cz, impactBasis, age * sp * 0.55f + 0.2f, 0.07f + 0.05f * age, 40, 1f, 1f, 1f, 0.8f * f * f);
            Draw.softRing(vc, m, cx, cy, cz, beamBasis, age * sp * 0.8f + 0.2f, 0.08f + 0.05f * age, 40, 0.6f, 0.4f, 1f, 0.6f * f * f);
        }
    }
}
