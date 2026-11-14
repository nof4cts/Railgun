package com.aegis.command.client.fx;

import com.aegis.command.client.Sfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Weapon visuals built on {@link FxWorld}: beams, muzzle sparks, casings and impacts. */
public final class WeaponFx {
    private static final RandomSource R = RandomSource.create();

    private WeaponFx() {}

    private static Vec3 camPos() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    /**
     * kind 0 = PHANTOM beam, 1 = NOVA-RAIL lance, 2 = small plasma impact at b,
     * 3 = TITAN-X shell impact at b, 4 = OBLIVION singularity detonation at b.
     */
    public static void tracer(double ax, double ay, double az, double bx, double by, double bz, int color, byte kind) {
        Vec3 a = new Vec3(ax, ay, az), b = new Vec3(bx, by, bz);
        if (kind == 2) { smallImpact(b, color); return; }
        if (kind == 3) { impactBurst(b, color, 1.15f, true); return; }
        if (kind == 4) {
            impactBurst(b, color, 1.6f, true);
            FxWorld.dome(b, 9f, 0.7f, 0xFFFFFF);
            FxWorld.ring(b, 0.5f, 14f, 0.8f, color, true);
            FxWorld.flash(b, 9f, 0.5f, color, true);
            Sfx.impact(b.x, b.y, b.z, 4f, camPos().distanceTo(b));
            return;
        }
        if (kind == 1) {
            FxWorld.beam(a, b, 0.32f, 0.9f, color, true);
            FxWorld.beam(a, b, 0.9f, 0.18f, 0xFFFFFF, false);
            // shockwave rings stacked along the lance, like a sonic tunnel
            Vec3 d = b.subtract(a);
            double len = d.length();
            for (double t = 2; t < Math.min(len, 40); t += 4.5) {
                FxWorld.ring(a.add(d.scale(t / len)), 0.2f, 1.6f, 0.45f + (float) t * 0.01f, color, false);
            }
            impactBurst(b, color, 1.1f, true);
        } else {
            FxWorld.beam(a, b, 0.07f, 0.22f, color, false);
            FxWorld.flash(b, 0.6f, 0.15f, color, true);
            FxWorld.sparkBurst(b, 14, 7f, 0.4f, color, 14f);
            chips(b, 5, 0.25f);
        }
    }

    /** Small energy impact: sparks, a flash, a ring and chips of the struck block. */
    public static void smallImpact(Vec3 p, int color) {
        FxWorld.flash(p, 0.5f, 0.14f, color, true);
        FxWorld.sparkBurst(p, 10, 6f, 0.35f, color, 14f);
        FxWorld.ring(p, 0.1f, 0.9f, 0.25f, color, false);
        chips(p, 3, 0.2f);
    }

    /**
     * Heavy cannon impact: flash, ground shock ring + dome, spark storm, block debris thrown from the
     * surface, and an impact frame if the camera is close enough to feel it.
     */
    public static void impactBurst(Vec3 p, int color, float power, boolean frames) {
        double dist = camPos().distanceTo(p);
        FxWorld.flash(p.add(0, 0.5, 0), 3.5f * power, 0.35f, color, true);
        FxWorld.flash(p.add(0, 0.5, 0), 2.0f * power, 0.18f, 0xFFFFFF, false);
        FxWorld.ring(p.add(0, 0.15, 0), 0.4f, 7f * power, 0.55f, color, true);
        FxWorld.ring(p.add(0, 0.2, 0), 0.4f, 4.5f * power, 0.4f, 0xFFFFFF, true);
        FxWorld.dome(p, 4.2f * power, 0.5f, color);
        FxWorld.sparkBurst(p.add(0, 0.3, 0), (int) (60 * power), 14f * power, 0.8f, color, 16f);
        chips(p, (int) (22 * power), 0.32f);
        if (frames && dist < 48) ImpactFrames.trigger((float) (1.0 - dist / 60.0) * Math.min(1.3f, power), color);
    }

    /** Throw chunks of whatever block is at/under p. */
    public static void chips(Vec3 p, int n, float scale) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlockState s = level.getBlockState(BlockPos.containing(p));
        if (s.isAir()) s = level.getBlockState(BlockPos.containing(p.x, p.y - 0.6, p.z));
        if (s.isAir()) return;
        for (int i = 0; i < n; i++) {
            Vec3 v = FxWorld.randomDir();
            FxWorld.debris(p.add(v.scale(0.3)), new Vec3(v.x * 5, Math.abs(v.y) * 7 + 2, v.z * 5), s,
                    scale * (0.5f + R.nextFloat() * 0.7f), 2.5f + R.nextFloat() * 2f);
        }
    }

    /** Glowing spent cell ejected from the right side of the weapon (first person). */
    public static void casing(Vec3 muzzle, Vec3 look, int color) {
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 p = muzzle.subtract(look.scale(0.55)).add(0, 0.05, 0);
        Vec3 v = right.scale(2.2 + R.nextDouble()).add(0, 2.4 + R.nextDouble(), 0).add(look.scale(-0.6));
        FxWorld.spark(p, v, 1.4f, 0.03f, 0.035f, 12f, color, true);
    }

    /** Singularity collapse: inward-rushing sparks and a shrinking ring. */
    public static void implosion(Vec3 c, int color, float k) {
        for (int i = 0; i < 6; i++) {
            Vec3 d = FxWorld.randomDir().scale(3 + R.nextDouble() * 3);
            FxWorld.spark(c.add(d), d.scale(-2.6), 0.38f, 0.08f, 0.06f, 0f, color, false);
        }
        if (R.nextInt(3) == 0) FxWorld.ring(c, 5.5f, 0.3f, 0.4f, color, false);
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && R.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL, c.x + R.nextGaussian() * 2, c.y + R.nextGaussian() * 2, c.z + R.nextGaussian() * 2, 0, 0, 0);
        }
        if (R.nextInt(5) == 0) Sfx.at(SoundEvents.RESPAWN_ANCHOR_CHARGE, c.x, c.y, c.z, 0.4f + k, 0.5f);
    }
}
