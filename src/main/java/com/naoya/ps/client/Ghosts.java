package com.naoya.ps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.naoya.ps.client.render.FxTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * The frames Naoya leaves behind: his body drawn again, flat and pale like a cel on a light table, at each position he
 * planned. Each one is held for a beat and then gone; the earliest go first, so a dash reads as a run of stills.
 */
public final class Ghosts {
    private record Ghost(int id, Vec3 pos, float yaw, long start, float life, float alpha) {}

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    static boolean drawing;

    private Ghosts() {}

    public static void add(int id, Vec3 pos, float yaw, float life, float alpha) {
        if (GHOSTS.size() > 240) {
            GHOSTS.remove(0);
        }
        GHOSTS.add(new Ghost(id, pos, yaw, System.nanoTime(), life, alpha));
    }

    static void tick() {
        long now = System.nanoTime();
        GHOSTS.removeIf(g -> (now - g.start) / 1.0E9F > g.life);
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || GHOSTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partial = event.getPartialTick();
        long now = System.nanoTime();
        drawing = true;
        try {
            for (Ghost g : GHOSTS) {
                Entity e = mc.level.getEntity(g.id);
                if (!(e instanceof LivingEntity) || g.pos.distanceTo(e.position()) < 0.6) {
                    continue;
                }
                float k = (now - g.start) / 1.0E9F / g.life;
                // held at full strength, then cut out quickly: a frame, not a fade
                float a = g.alpha * (k < 0.7F ? 1.0F : (1 - k) / 0.3F);
                if (a <= 0.02F) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                EntityRenderer<Entity> r = (EntityRenderer<Entity>) mc.getEntityRenderDispatcher().getRenderer(e);
                float yaw0 = e.getYRot(), body0 = ((LivingEntity) e).yBodyRot, bodyO = ((LivingEntity) e).yBodyRotO, head0 = ((LivingEntity) e).yHeadRot,
                        headO = ((LivingEntity) e).yHeadRotO;
                LivingEntity le = (LivingEntity) e;
                le.yBodyRot = le.yBodyRotO = g.yaw;
                le.yHeadRot = le.yHeadRotO = g.yaw;
                pose.pushPose();
                pose.translate(g.pos.x - cam.x, g.pos.y - cam.y, g.pos.z - cam.z);
                MultiBufferSource fill = type -> new Tint(buffers.getBuffer(FxTypes.TINT), 0.93F, 0.91F, 0.85F, a * 0.55F, false);
                r.render(e, g.yaw, partial, pose, fill, 0xF000F0);
                MultiBufferSource rim = type -> new Tint(buffers.getBuffer(RenderType.lightning()), 0.75F, 0.85F, 1.0F, a * 0.25F, true);
                pose.scale(1.03F, 1.02F, 1.03F);
                r.render(e, g.yaw, partial, pose, rim, 0xF000F0);
                pose.popPose();
                le.yBodyRot = body0;
                le.yBodyRotO = bodyO;
                le.yHeadRot = head0;
                le.yHeadRotO = headO;
                e.setYRot(yaw0);
            }
        } finally {
            drawing = false;
        }
        buffers.endBatch(FxTypes.TINT);
        buffers.endBatch(RenderType.lightning());
    }

    /** Passes positions through and paints every vertex one colour. */
    record Tint(VertexConsumer to, float r, float g, float b, float a, boolean premultiply) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, y, z);
            float m = premultiply ? a : 1.0F;
            to.color(r * m, g * m, b * m, a);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            to.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
