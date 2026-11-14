package com.aegis.command.client.fx;

import com.aegis.command.client.Sfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Layered client-side explosion: flash, fireball, expanding shockwave ring, debris thrown from the
 * actual ground block, rolling dust, and a smoke column that keeps feeding for several seconds.
 */
public final class ImpactFx {
    private static final RandomSource R = RandomSource.create();

    private ImpactFx() {}

    private static void p(ClientLevel l, ParticleOptions o, double x, double y, double z, double vx, double vy, double vz) {
        l.addAlwaysVisibleParticle(o, true, x, y, z, vx, vy, vz);
    }

    public static void spawn(double x, double y, double z, float power, int kind) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(new Vec3(x, y, z));

        if (kind == 2) {
            for (int i = 0; i < 6; i++) p(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, R.nextGaussian() * 0.2, R.nextGaussian() * 0.2, R.nextGaussian() * 0.2);
            return;
        }

        boolean big = kind == 0;
        float scale = power / 4f;

        // ---- flash + shake + sound
        float near = (float) Math.max(0, 1 - dist / (big ? 140 : 40));
        ScreenFx.flash(near * (big ? 0.85f : 0.4f), big ? 0xFFF0D0 : 0xFFB070);
        ScreenFx.addTrauma(near * (big ? 0.95f : 0.35f));
        if (big) Sfx.impact(x, y, z, power, dist);
        else {
            Sfx.at(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, x, y, z, 1.3f, 2.5f);
            Sfx.at(net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, x, y, z, 1.6f, 1.2f);
        }

        BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
        if (ground.isAir()) ground = level.getBlockState(BlockPos.containing(x, y - 1.5, z));
        if (ground.isAir()) ground = Blocks.DIRT.defaultBlockState();
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);

        if (big) cinematicLayers(level, x, y, z, power, scale, near);

        // ---- core fireball
        p(level, ParticleTypes.FLASH, x, y + 1, z, 0, 0, 0);
        p(level, ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5, z, 1, 0, 0);
        int balls = big ? 16 : 5;
        for (int i = 0; i < balls; i++) {
            p(level, ParticleTypes.EXPLOSION, x + R.nextGaussian() * 2.2 * scale, y + 0.5 + R.nextDouble() * 3 * scale,
                    z + R.nextGaussian() * 2.2 * scale, 1, 0, 0);
        }
        int flames = big ? 90 : 30;
        for (int i = 0; i < flames; i++) {
            Vec3 v = randomDir().scale(0.15 + R.nextDouble() * 0.45 * scale);
            p(level, R.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, x, y + 0.8, z, v.x, Math.abs(v.y) + 0.05, v.z);
        }
        DustParticleOptions ember = new DustParticleOptions(new Vector3f(1f, 0.55f, 0.15f), 2.5f);
        for (int i = 0; i < (big ? 50 : 18); i++) {
            Vec3 v = randomDir().scale(1.5 * scale);
            p(level, ember, x + v.x, y + 1 + Math.abs(v.y), z + v.z, 0, 0.05, 0);
        }

        // ---- shockwave ring, hugging the ground
        int ring = big ? 96 : 32;
        for (int i = 0; i < ring; i++) {
            double a = i / (double) ring * Math.PI * 2;
            double sp = (big ? 0.95 : 0.55) * (0.85 + R.nextDouble() * 0.3);
            p(level, ParticleTypes.CLOUD, x, y + 0.3, z, Math.cos(a) * sp, 0.01, Math.sin(a) * sp);
            if (big && i % 2 == 0) p(level, ParticleTypes.POOF, x, y + 0.5, z, Math.cos(a) * sp * 0.6, 0.04, Math.sin(a) * sp * 0.6);
        }

        // ---- debris thrown from the real ground
        for (int i = 0; i < (big ? 140 : 40); i++) {
            Vec3 v = randomDir();
            p(level, debris, x + v.x, y + 0.5, z + v.z, v.x * 0.6 * scale, 0.4 + R.nextDouble() * 0.9 * scale, v.z * 0.6 * scale);
        }

        // ---- rolling dust and smoke column
        for (int i = 0; i < (big ? 70 : 20); i++) {
            Vec3 v = randomDir();
            p(level, ParticleTypes.LARGE_SMOKE, x + v.x * 2 * scale, y + 0.5, z + v.z * 2 * scale, v.x * 0.25, 0.05 + R.nextDouble() * 0.15, v.z * 0.25);
        }
        if (big) {
            for (int i = 0; i < 14; i++) {
                p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + R.nextGaussian() * 1.5, y + 0.5, z + R.nextGaussian() * 1.5,
                        R.nextGaussian() * 0.01, 0.07 + R.nextDouble() * 0.06, R.nextGaussian() * 0.01);
            }
            for (int i = 0; i < 26; i++) {
                p(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x + R.nextGaussian() * 3, y + 0.5 + R.nextDouble() * 2, z + R.nextGaussian() * 3,
                        R.nextGaussian() * 0.03, 0.03 + R.nextDouble() * 0.05, R.nextGaussian() * 0.03);
            }
            // keep the column alive and add secondary pops for a few seconds
            for (int k = 1; k <= 8; k++) {
                final int step = k;
                Sfx.later(k * 6, () -> lingering(x, y, z, scale, step));
            }
        }
    }

    private static void lingering(double x, double y, double z, float scale, int step) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int i = 0; i < 6; i++) {
            p(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x + R.nextGaussian() * 2.5, y + 0.5, z + R.nextGaussian() * 2.5,
                    R.nextGaussian() * 0.02, 0.05, R.nextGaussian() * 0.02);
        }
        for (int i = 0; i < 4; i++) {
            p(level, ParticleTypes.ASH, x + R.nextGaussian() * 4, y + 2 + R.nextDouble() * 6, z + R.nextGaussian() * 4, 0, -0.02, 0);
        }
        if (step <= 3 && R.nextInt(2) == 0) {
            double ox = x + R.nextGaussian() * 2.5 * scale, oz = z + R.nextGaussian() * 2.5 * scale;
            p(level, ParticleTypes.EXPLOSION, ox, y + 0.8, oz, 1, 0, 0);
            for (int i = 0; i < 8; i++) p(level, ParticleTypes.FLAME, ox, y + 0.8, oz, R.nextGaussian() * 0.08, 0.1, R.nextGaussian() * 0.08);
            Sfx.at(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, ox, y, oz, 1.4f, 1.5f);
        }
    }

    /** FxWorld layers for a bomb: star flash, shock dome, ground rings, spark storm and real block debris. */
    private static void cinematicLayers(ClientLevel level, double x, double y, double z, float power, float scale, float near) {
        Vec3 c = new Vec3(x, y, z);
        FxWorld.flash(c.add(0, 2, 0), 16 * scale, 0.65f, 0xFFC27A, true);
        FxWorld.flash(c.add(0, 1, 0), 7 * scale, 0.25f, 0xFFFFFF, false);
        FxWorld.dome(c, 10 * scale, 0.75f, 0xFFB15A);
        FxWorld.ring(c.add(0, 0.25, 0), 1, 24 * scale, 1.2f, 0xFFE2B8, true);
        FxWorld.ring(c.add(0, 0.45, 0), 1, 14 * scale, 0.75f, 0xFF7A2A, true);
        FxWorld.sparkBurst(c.add(0, 1, 0), 110, 24f * scale, 1.7f, 0xFFB050, 18f);
        // debris: the actual blocks around the impact, thrown outward and up
        int r = (int) Math.ceil(power * 1.15);
        BlockPos centre = BlockPos.containing(x, y, z);
        java.util.List<BlockPos> solid = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (p.distSqr(centre) > r * r) continue;
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty()) solid.add(p.immutable());
        }
        java.util.Collections.shuffle(solid, new java.util.Random(R.nextLong()));
        int n = Math.min(solid.size(), 85);
        for (int i = 0; i < n; i++) {
            BlockPos p = solid.get(i);
            Vec3 from = Vec3.atCenterOf(p);
            Vec3 out = from.subtract(c);
            if (out.lengthSqr() < 0.01) out = randomDir();
            out = new Vec3(out.x, 0, out.z).normalize();
            double sp = 5 + R.nextDouble() * 12 * scale;
            Vec3 v = out.scale(sp * 0.55).add(0, 7 + R.nextDouble() * 15 * scale, 0);
            FxWorld.debris(from.add(0, 0.6, 0), v, level.getBlockState(p), 0.3f + R.nextFloat() * 0.55f, 4f + R.nextFloat() * 4f);
        }
        if (near > 0.25f) ImpactFrames.trigger(near, 0xFFC070);
    }

    private static Vec3 randomDir() {
        double u = R.nextDouble() * 2 - 1, a = R.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(a), u, s * Math.sin(a));
    }
}
