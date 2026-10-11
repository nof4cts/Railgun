package com.firestar.worldbreaker.client.giant;

import com.firestar.worldbreaker.WorldbreakerMod;
import com.firestar.worldbreaker.client.Marching;
import com.firestar.worldbreaker.common.March;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Draws every giant of every march straight into the world each frame (they are not entities: nothing limits how far
 * away they can be seen, and they move as smoothly as the frame rate allows). Two passes per giant: the lit basalt, then
 * the molten seams and eyes at full brightness.
 */
public final class GiantRenderer {
    public static final ResourceLocation TEX = WorldbreakerMod.id("textures/entity/colossus.png");
    public static final ResourceLocation GLOW = WorldbreakerMod.id("textures/entity/colossus_glow.png");
    private static GiantModel model;

    private GiantRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || Marching.ACTIVE.isEmpty()) {
            return;
        }
        if (model == null) {
            model = new GiantModel();
        }
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        org.joml.Vector3f lk = event.getCamera().getLookVector();
        Vec3 look = new Vec3(lk.x, lk.y, lk.z);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
        RenderType solid = RenderType.entityCutoutNoCull(TEX);
        RenderType glow = RenderType.eyes(GLOW);
        for (Marching.Active a : Marching.ACTIVE) {
            double t = Marching.time(a, partial);
            if (t < 0 || t > a.life + 2) {
                continue;
            }
            for (March.Giant g : a.giants) {
                double h = March.HEIGHT * g.scale();
                Vec3 c = Marching.centre(a, g, partial);
                Vec3 rel = c.subtract(cam);
                double dist = rel.length();
                if (dist > 900 || (rel.add(0, h * 0.5, 0).dot(look) < -h && dist > h)) {
                    continue;
                }
                float presence = (float) March.presence(t, a.life);
                Vec3 heading = March.heading(a.dir, g);
                float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
                model.pose(March.gait(t, g), time, g.index(), Mth.clamp(presence * 1.4F - 0.2F, 0, 1));
                float s = (float) (h / (GiantModel.TALL * GiantModel.U / 16.0F));
                BlockPos lp = BlockPos.containing(c.x, c.y + h * 0.6, c.z);
                int light = mc.level.isLoaded(lp) ? LevelRenderer.getLightColor(mc.level, lp) : 0xF000F0;

                pose.pushPose();
                // rising out of the ground at the start, sinking back into it at the end
                pose.translate(rel.x, rel.y - (1 - presence) * h * 1.45, rel.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
                if (presence < 1) {
                    pose.mulPose(Axis.XP.rotationDegrees((1 - presence) * 14));
                }
                pose.scale(-s, -s, s);
                pose.translate(0, -128 * GiantModel.U / 16.0F, 0);
                // each giant its own stone: some ashier, some warmer, some near black
                java.util.Random tint = new java.util.Random(a.seed * 977 + g.index());
                float v = 0.78F + tint.nextFloat() * 0.34F;
                float warm = (tint.nextFloat() - 0.5F) * 0.12F;
                model.root.render(pose, buffers.getBuffer(solid), light, OverlayTexture.NO_OVERLAY, v * (1 + warm), v, v * (1 - warm), 1);
                float pulse = 0.82F + 0.18F * Mth.sin(time * 2.1F + g.index() * 1.3F);
                model.root.render(pose, buffers.getBuffer(glow), 0xF000F0, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse, 1);
                pose.popPose();
            }
        }
        buffers.endBatch(solid);
        buffers.endBatch(glow);
    }

    /** Roughly where a giant's eyes are, for the light that burns in them. */
    public static Vec3 eyes(Vec3 centre, Vec3 heading, double h) {
        return centre.add(0, h * 0.955, 0).add(heading.scale(h * 0.15));
    }
}
