package com.firestar.worldbreaker.client.giant;

import com.firestar.worldbreaker.WorldbreakerMod;
import com.firestar.worldbreaker.client.Marching;
import com.firestar.worldbreaker.common.March;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Draws every giant of every march straight into the world each frame (they are not entities: nothing limits how far
 * away they can be seen, and they move as smoothly as the frame rate allows). Each giant is sculpted afresh for its pose
 * at a tessellation chosen by its distance: round and outlined in ink up close, a handful of quads on the horizon.
 */
public final class GiantRenderer {
    public static final ResourceLocation BRONZE = WorldbreakerMod.id("textures/entity/bronze.png");
    public static final ResourceLocation MARBLE = WorldbreakerMod.id("textures/entity/marble.png");
    public static final ResourceLocation GOLD = WorldbreakerMod.id("textures/entity/gold.png");
    public static final ResourceLocation CLOTH = WorldbreakerMod.id("textures/entity/cloth.png");
    public static final ResourceLocation GLOW = WorldbreakerMod.id("textures/entity/glow.png");
    private static final ResourceLocation WHITE = WorldbreakerMod.id("textures/misc/white.png");

    private static RenderType bronze, marble, gold, cloth, ink, glow;
    /** The giants' own buffers, one per material, so every material can be written at once without flushing the others. */
    private static MultiBufferSource.BufferSource buffers;

    private GiantRenderer() {}

    private static void init() {
        if (buffers != null) {
            return;
        }
        bronze = RenderType.entitySolid(BRONZE);
        marble = RenderType.entitySolid(MARBLE);
        gold = RenderType.entitySolid(GOLD);
        cloth = RenderType.entityCutoutNoCull(CLOTH);
        ink = RenderType.entitySolid(WHITE);
        glow = RenderType.eyes(GLOW);
        java.util.Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> map = new java.util.LinkedHashMap<>();
        for (RenderType rt : new RenderType[]{ink, bronze, marble, gold, cloth, glow}) {
            map.put(rt, new com.mojang.blaze3d.vertex.BufferBuilder(1 << 18));
        }
        buffers = MultiBufferSource.immediateWithBuffers(map, new com.mojang.blaze3d.vertex.BufferBuilder(1 << 12));
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || Marching.ACTIVE.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        org.joml.Vector3f lk = event.getCamera().getLookVector();
        Vec3 look = new Vec3(lk.x, lk.y, lk.z);
        PoseStack pose = event.getPoseStack();
        init();
        float time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9F;
        GiantBody.Materials mats = new GiantBody.Materials(buffers.getBuffer(bronze), buffers.getBuffer(marble), buffers.getBuffer(gold),
                buffers.getBuffer(cloth), buffers.getBuffer(glow));
        VertexConsumer inkBuf = buffers.getBuffer(ink);
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
                BlockPos lp = BlockPos.containing(c.x, c.y + h * 0.6, c.z);
                int light = mc.level.isLoaded(lp) ? LevelRenderer.getLightColor(mc.level, lp) : 0xF000F0;
                // detail by how big the giant looks: distance against its height
                double apparent = dist / h;
                Sculpt s;
                if (apparent < 2.2) {
                    s = new Sculpt(mats.bronze(), null, light, 7, 12, true);
                } else if (apparent < 4.5) {
                    s = new Sculpt(mats.bronze(), null, light, 5, 9, false);
                } else if (apparent < 9) {
                    s = new Sculpt(mats.bronze(), null, light, 4, 7, false);
                } else {
                    s = new Sculpt(mats.bronze(), null, light, 3, 5, false);
                }

                pose.pushPose();
                // rising out of the ground at the start, sinking back into it at the end
                pose.translate(rel.x, rel.y - (1 - presence) * h * 1.15, rel.z);
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                if (presence < 1) {
                    pose.mulPose(Axis.XP.rotationDegrees((1 - presence) * 14));
                }
                pose.scale((float) h, (float) h, (float) h);
                float rising = Mth.clamp(presence * 1.4F - 0.2F, 0, 1);
                if (g.sovereign()) {
                    GiantBody.sovereign(pose, s, mats, March.gait(t, g), time, g.index(), rising);
                } else {
                    // each its own casting: some darker, some brighter, some warmer
                    java.util.Random tint = new java.util.Random(a.seed * 977 + g.index());
                    float v = 0.82F + tint.nextFloat() * 0.3F;
                    float warm = (tint.nextFloat() - 0.5F) * 0.14F;
                    GiantBody.colossus(pose, s, mats, March.gait(t, g), time, g.index(), rising, v, warm);
                }
                pose.popPose();
            }
        }
        buffers.endBatch(ink);
        buffers.endBatch(bronze);
        buffers.endBatch(marble);
        buffers.endBatch(gold);
        buffers.endBatch(cloth);
        buffers.endBatch(glow);
    }

    /** Roughly where a giant's eyes are, for the light that burns in them. */
    public static Vec3 eyes(Vec3 centre, Vec3 heading, double h) {
        return centre.add(0, h * 0.93, 0).add(heading.scale(h * 0.06));
    }
}
