package com.yuji.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yuji.net.YNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * As God As My Witness, seen from outside: his eyes burn red and a red halo floats over his head, both following
 * where his head turns. Drawn as additive light (no textures, no particles), so they glow against anything.
 */
public final class YGod {
    private static final Map<Integer, Long> ON = new HashMap<>();
    private static MultiBufferSource.BufferSource OWN;

    private YGod() {}

    public static void set(YNet.God m) {
        if (m.until() <= 0) ON.remove(m.entity());
        else ON.put(m.entity(), m.until());
    }

    public static void clear() {
        ON.clear();
    }

    public static void render(RenderLevelStageEvent e) {
        if (ON.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long gt = mc.level.getGameTime();
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        if (OWN == null) OWN = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1 << 14));
        VertexConsumer vc = OWN.getBuffer(RenderType.lightning());
        Matrix4f m = e.getPoseStack().last().pose();
        float time = (gt + pt) / 20f;
        boolean any = false;
        for (Map.Entry<Integer, Long> en : ON.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || en.getValue() <= gt || le.isInvisible()) continue;
            // in first person his own eyes would sit in the camera: show only the halo then
            boolean self = le == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
            float fade = (float) Mth.clamp((en.getValue() - gt - pt) / 20.0, 0, 1);
            Vec3 feet = le.getPosition(pt).subtract(cam);
            float yaw = Mth.lerp(pt, le.yHeadRotO, le.yHeadRot) * Mth.DEG_TO_RAD;
            float pitch = Mth.lerp(pt, le.xRotO, le.getXRot()) * Mth.DEG_TO_RAD;
            float s = le.getScale();
            double neckY = (le.isCrouching() ? 1.2 : 1.5) * s;
            Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 r = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            Vec3 up = new Vec3(0, 1, 0);
            // the head, tilted by where he looks
            Vec3 hf = f.scale(Mth.cos(pitch)).add(0, -Mth.sin(pitch), 0);
            Vec3 hu = up.scale(Mth.cos(pitch)).add(f.scale(Mth.sin(pitch)));
            Vec3 neck = feet.add(0, neckY, 0);
            float flicker = 0.85f + 0.15f * Mth.sin(time * 9f) * Mth.sin(time * 5.3f + 1.1f);
            if (!self) {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3 eye = neck.add(hu.scale(0.235 * s)).add(hf.scale(0.252 * s)).add(r.scale(side * 0.094 * s));
                    quad(vc, m, eye, r, hu, 0.055 * s, 0.03 * s, 1f, 0.12f, 0.1f, 1f * fade);                    // the burning iris
                    quad(vc, m, eye.add(hf.scale(0.005)), r, hu, 0.14 * s, 0.07 * s, 1f, 0.05f, 0.05f, 0.45f * fade * flicker);  // glow
                    quad(vc, m, eye.add(hf.scale(0.01)), r, hu, 0.3 * s, 0.12 * s, 0.9f, 0f, 0.05f, 0.18f * fade * flicker);   // bleed
                }
            }
            // the halo: a ring of red light over his head
            Vec3 centre = neck.add(hu.scale((0.62 + 0.03 * Mth.sin(time * 2f)) * s)).add(hf.scale(-0.06 * s));
            Vec3 a1 = r, a2 = hf.cross(hu).lengthSqr() > 0 ? hf : f;   // the ring's plane: across the head
            ring(vc, m, centre, a1, a2, 0.3 * s, 0.035 * s, 1f, 0.15f, 0.12f, 0.95f * fade);
            ring(vc, m, centre, a1, a2, 0.3 * s, 0.11 * s, 1f, 0.02f, 0.05f, 0.3f * fade * flicker);
            any = true;
        }
        if (any) OWN.endBatch();
        ON.values().removeIf(u -> u < gt - 40);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 r, Vec3 u, double w, double h,
                             float red, float green, float blue, float alpha) {
        Vec3 a = c.add(r.scale(-w / 2)).add(u.scale(-h / 2)), b = c.add(r.scale(w / 2)).add(u.scale(-h / 2));
        Vec3 d = c.add(r.scale(w / 2)).add(u.scale(h / 2)), e = c.add(r.scale(-w / 2)).add(u.scale(h / 2));
        v(vc, m, a, red, green, blue, alpha);
        v(vc, m, b, red, green, blue, alpha);
        v(vc, m, d, red, green, blue, alpha);
        v(vc, m, e, red, green, blue, alpha);
        // and from behind
        v(vc, m, e, red, green, blue, alpha);
        v(vc, m, d, red, green, blue, alpha);
        v(vc, m, b, red, green, blue, alpha);
        v(vc, m, a, red, green, blue, alpha);
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 x, Vec3 z, double radius, double width,
                             float red, float green, float blue, float alpha) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            Vec3 d0 = x.scale(Math.cos(a0)).add(z.scale(Math.sin(a0)));
            Vec3 d1 = x.scale(Math.cos(a1)).add(z.scale(Math.sin(a1)));
            Vec3 i0 = c.add(d0.scale(radius - width / 2)), o0 = c.add(d0.scale(radius + width / 2));
            Vec3 i1 = c.add(d1.scale(radius - width / 2)), o1 = c.add(d1.scale(radius + width / 2));
            v(vc, m, i0, red, green, blue, alpha);
            v(vc, m, o0, red, green, blue, alpha);
            v(vc, m, o1, red, green, blue, alpha);
            v(vc, m, i1, red, green, blue, alpha);
            v(vc, m, i1, red, green, blue, alpha);
            v(vc, m, o1, red, green, blue, alpha);
            v(vc, m, o0, red, green, blue, alpha);
            v(vc, m, i0, red, green, blue, alpha);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    static boolean unused(PoseStack ps) {
        return ps == null;
    }
}
