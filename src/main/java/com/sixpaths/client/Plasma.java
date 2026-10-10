package com.sixpaths.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Queued draws for the procedural plasma shader: star surfaces with coronas, accretion disks,
 * beam cores. Effects enqueue during the glow pass; the queue is flushed right after it.
 */
public final class Plasma {
    public static final int SUN = 0, DISK = 1, BEAM = 2;

    private record Draw3(int mode, Vec3 pos, Vec3 axis, double size, double length, int tint, float intensity) {}

    private static final List<Draw3> QUEUE = new ArrayList<>();

    public static void sun(Vec3 pos, double radius, int tint, float intensity) {
        QUEUE.add(new Draw3(SUN, pos, Vec3.ZERO, radius, 0, tint, intensity));
    }

    public static void disk(Vec3 pos, Vec3 axis, double innerRadius, int tint, float intensity) {
        QUEUE.add(new Draw3(DISK, pos, axis.normalize(), innerRadius, 0, tint, intensity));
    }

    public static void beam(Vec3 from, Vec3 axis, double length, double halfWidth, int tint, float intensity) {
        QUEUE.add(new Draw3(BEAM, from, axis.normalize(), halfWidth, length, tint, intensity));
    }

    private static void v(BufferBuilder b, Matrix4f m, Vec3 p, float u, float vv, int tint, float a) {
        b.vertex(m, (float) p.x, (float) p.y, (float) p.z).uv(u, vv)
                .color((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, (int) (Math.min(1f, a) * 255)).endVertex();
    }

    public static void flush(PoseStack ps, Camera cam, Vec3 camPos) {
        ShaderInstance sh = SpellShaders.PLASMA;
        if (QUEUE.isEmpty()) return;
        if (sh == null) {
            QUEUE.clear();
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        sh.safeGetUniform("Time").set((Vfx.now() % 1000000) / 1000f);
        Vec3 left = Vfx.v3(cam.getLeftVector()), up = Vfx.v3(cam.getUpVector());
        for (int mode = 0; mode <= 2; mode++) {
            boolean any = false;
            for (Draw3 d : QUEUE) if (d.mode == mode) { any = true; break; }
            if (!any) continue;
            sh.safeGetUniform("Mode").set((float) mode);
            RenderSystem.setShader(() -> sh);
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Draw3 d : QUEUE) {
                if (d.mode != mode) continue;
                ps.pushPose();
                ps.translate(d.pos.x - camPos.x, d.pos.y - camPos.y, d.pos.z - camPos.z);
                Matrix4f m = ps.last().pose();
                if (mode == SUN) {
                    double s = d.size * 3.0;
                    Vec3 l = left.scale(s), u = up.scale(s);
                    v(b, m, l.add(u), -3, 3, d.tint, d.intensity);
                    v(b, m, l.scale(-1).add(u), 3, 3, d.tint, d.intensity);
                    v(b, m, l.scale(-1).subtract(u), 3, -3, d.tint, d.intensity);
                    v(b, m, l.subtract(u), -3, -3, d.tint, d.intensity);
                } else if (mode == DISK) {
                    Vec3 a = Draw.perp(d.axis), c = d.axis.cross(a);
                    double s = d.size * 4.3;
                    Vec3 aa = a.scale(s), cc = c.scale(s);
                    v(b, m, aa.add(cc), 4.3f, 4.3f, d.tint, d.intensity);
                    v(b, m, aa.scale(-1).add(cc), -4.3f, 4.3f, d.tint, d.intensity);
                    v(b, m, aa.scale(-1).subtract(cc), -4.3f, -4.3f, d.tint, d.intensity);
                    v(b, m, aa.subtract(cc), 4.3f, -4.3f, d.tint, d.intensity);
                } else {
                    Vec3 mid = d.axis.scale(d.length * 0.5);
                    Vec3 toCam = camPos.subtract(d.pos.add(mid));
                    Vec3 side = d.axis.cross(toCam);
                    if (side.lengthSqr() < 1.0e-8) side = Draw.perp(d.axis);
                    side = side.normalize().scale(d.size);
                    Vec3 end = d.axis.scale(d.length);
                    float len = (float) d.length;
                    v(b, m, side.scale(-1), -1, 0, d.tint, d.intensity);
                    v(b, m, side, 1, 0, d.tint, d.intensity);
                    v(b, m, end.add(side), 1, len, d.tint, d.intensity);
                    v(b, m, end.subtract(side), -1, len, d.tint, d.intensity);
                }
                ps.popPose();
            }
            BufferUploader.drawWithShader(b.end());
        }
        QUEUE.clear();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private Plasma() {}
}
