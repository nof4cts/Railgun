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
 * away they can be seen, and they move as smoothly as the frame rate allows). One blocky body, one skin, a different
 * skin tone and build for each giant. The king who leads them walks behind: far taller, twice as broad, bone grown
 * over its skin and a crown of antlers.
 */
public final class GiantRenderer {
    public static final ResourceLocation TEX = WorldbreakerMod.id("textures/entity/giant.png");
    private static GiantModel model, king;

    /** Skin tones the giants are tinted with (the skin is painted light and neutral). */
    private static final float[][] TONES = {{1.0F, 0.97F, 0.95F}, {1.0F, 0.9F, 0.84F}, {0.93F, 0.8F, 0.7F}, {0.82F, 0.66F, 0.55F},
            {0.66F, 0.5F, 0.4F}, {0.5F, 0.37F, 0.3F}, {0.98F, 0.88F, 0.86F}};

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
            king = new GiantModel(true);
        }
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        org.joml.Vector3f lk = event.getCamera().getLookVector();
        Vec3 look = new Vec3(lk.x, lk.y, lk.z);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
        RenderType solid = RenderType.entityCutoutNoCull(TEX);
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
                if (presence <= 0.001F) {
                    continue;
                }
                Vec3 heading = March.heading(a.dir, g);
                float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
                GiantModel body = g.sovereign() ? king : model;
                body.pose(March.gait(t, g), time, g.index(), Mth.clamp(presence * 1.4F - 0.2F, 0, 1));
                float s = (float) (h / (GiantModel.TALL * GiantModel.U / 16.0F));
                BlockPos lp = BlockPos.containing(c.x, c.y + h * 0.6, c.z);
                int light = mc.level.isLoaded(lp) ? LevelRenderer.getLightColor(mc.level, lp) : 0xF000F0;

                // each its own build: some gaunt, some heavy, a little lopsided
                java.util.Random r = new java.util.Random(a.seed * 977 + g.index());
                float[] tone = TONES[r.nextInt(TONES.length)];
                float girth = g.sovereign() ? 1.0F : 0.86F + r.nextFloat() * 0.32F;
                if (g.sovereign()) {
                    tone = new float[]{0.86F, 0.72F, 0.62F};
                }

                pose.pushPose();
                // rising out of the ground at the start, sinking back into it at the end
                pose.translate(rel.x, rel.y - (1 - presence) * h * 1.15, rel.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
                if (presence < 1) {
                    pose.mulPose(Axis.XP.rotationDegrees((1 - presence) * 14));
                }
                pose.scale(-s * girth, -s, s * girth);
                pose.translate(0, -128 * GiantModel.U / 16.0F, 0);
                body.root.render(pose, buffers.getBuffer(solid), light, OverlayTexture.NO_OVERLAY, tone[0], tone[1], tone[2], 1);
                pose.popPose();
            }
        }
        buffers.endBatch(solid);
    }

    /** Roughly where a giant's eyes are, for the camera to find its face. */
    public static Vec3 eyes(Vec3 centre, Vec3 heading, double h) {
        return centre.add(0, h * 0.94, 0).add(heading.scale(h * 0.06));
    }
}
