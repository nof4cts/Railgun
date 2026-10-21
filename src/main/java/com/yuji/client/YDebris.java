package com.yuji.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yuji.net.YNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The world coming apart: real blocks (the ones the server just destroyed) flung out of every crater, tumbling,
 * falling back and breaking up. Purely visual; they are drawn with the block renderer, not particles.
 */
public final class YDebris {
    private static final int MAX = 1600;
    private static final List<Piece> PIECES = new ArrayList<>();
    private static final Random R = new Random();

    private YDebris() {}

    private static final class Piece {
        final BlockState state;
        double x, y, z, px, py, pz, vx, vy, vz;
        float rx, ry, prx, pry, wx, wy, scale;
        int age, life;

        Piece(BlockState s) {
            state = s;
        }
    }

    public static void add(YNet.Debris m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 c = m.centre();
        double power = m.power();
        for (int i = 0; i < m.pos().size(); i++) {
            BlockState s = m.states().get(i);
            if (s.getRenderShape() != RenderShape.MODEL) continue;
            BlockPos p = m.pos().get(i);
            int copies = power > 1.5 ? 2 : 1;
            for (int k = 0; k < copies; k++) {
                if (PIECES.size() >= MAX) PIECES.remove(0);
                Piece d = new Piece(s);
                d.x = d.px = p.getX() + 0.5;
                d.y = d.py = p.getY() + 0.5;
                d.z = d.pz = p.getZ() + 0.5;
                Vec3 away = new Vec3(d.x - c.x, 0, d.z - c.z);
                double dist = away.length();
                away = dist < 1e-3 ? new Vec3(R.nextGaussian(), 0, R.nextGaussian()).normalize() : away.scale(1 / dist);
                double sp = (0.25 + R.nextDouble() * 0.6) * power * (0.6 + 0.4 * Math.exp(-dist / 20));
                d.vx = away.x * sp + R.nextGaussian() * 0.08;
                d.vz = away.z * sp + R.nextGaussian() * 0.08;
                d.vy = (0.45 + R.nextDouble() * 0.9) * Math.min(2.4, power) * (0.7 + 0.3 * Math.exp(-dist / 30));
                d.wx = (float) (R.nextGaussian() * 25);
                d.wy = (float) (R.nextGaussian() * 25);
                d.scale = (float) (0.35 + R.nextDouble() * 0.55);
                d.life = 50 + R.nextInt(50);
                PIECES.add(d);
            }
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PIECES.clear();
            return;
        }
        PIECES.removeIf(d -> {
            d.px = d.x;
            d.py = d.y;
            d.pz = d.z;
            d.prx = d.rx;
            d.pry = d.ry;
            d.vy -= 0.07;
            d.vx *= 0.985;
            d.vy *= 0.985;
            d.vz *= 0.985;
            double nx = d.x + d.vx, ny = d.y + d.vy, nz = d.z + d.vz;
            BlockPos below = BlockPos.containing(nx, ny - 0.3 * d.scale, nz);
            if (d.vy < 0 && !mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty()) {
                ny = below.getY() + 1 + 0.3 * d.scale;
                d.vy = -d.vy * 0.25;
                d.vx *= 0.6;
                d.vz *= 0.6;
                d.wx *= 0.5;
                d.wy *= 0.5;
            }
            d.x = nx;
            d.y = ny;
            d.z = nz;
            d.rx += d.wx;
            d.ry += d.wy;
            return ++d.age > d.life;
        });
    }

    public static void clear() {
        PIECES.clear();
    }

    public static void render(RenderLevelStageEvent e) {
        if (PIECES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (Piece d : PIECES) {
            double x = Mth.lerp(pt, d.px, d.x), y = Mth.lerp(pt, d.py, d.y), z = Mth.lerp(pt, d.pz, d.z);
            if ((x - cam.x) * (x - cam.x) + (z - cam.z) * (z - cam.z) > 300 * 300) continue;
            float shrink = Mth.clamp((d.life - d.age - pt) / 12f, 0, 1);
            float s = d.scale * shrink;
            if (s <= 0.01f) continue;
            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, d.prx, d.rx)));
            ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, d.pry, d.ry)));
            ps.scale(s, s, s);
            ps.translate(-0.5, -0.5, -0.5);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y, z));
            mc.getBlockRenderer().renderSingleBlock(d.state, ps, buf, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        buf.endBatch();
    }
}
