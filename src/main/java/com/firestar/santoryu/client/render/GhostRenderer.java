package com.firestar.santoryu.client.render;

import com.firestar.santoryu.client.Casts;
import com.firestar.santoryu.client.ClientCast;
import com.firestar.santoryu.client.fx.ScreenFx;
import com.firestar.santoryu.common.Beats;
import com.firestar.santoryu.common.Move;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Asura: two more of him, standing at his shoulders, glowing like embers through smoke. They are the player's own
 * model drawn twice more, turned and offset, in an additive glow (nine swords, three faces, six arms).
 */
public final class GhostRenderer {
    private static boolean drawing;

    private GhostRenderer() {}

    public static void after(RenderPlayerEvent.Post event) {
        if (drawing || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        ClientCast c = Casts.of(player);
        if (c == null || c.move != Move.ASURA) {
            return;
        }
        float t = c.time(event.getPartialTick());
        float a = ScreenFx.window(t, Beats.ASURA_MANIFEST - 2, Beats.ASURA_MANIFEST + 6, Beats.ASURA_HIT + 14, Beats.ASURA_HIT + 34);
        if (a <= 0.01F) {
            return;
        }
        PlayerRenderer renderer = event.getRenderer();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource base = event.getMultiBufferSource();
        float yaw = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        float flicker = 0.85F + 0.15F * Mth.sin(t * 1.7F);
        MultiBufferSource ghost = type -> new Tint(base.getBuffer(RenderType.lightning()), 1.0F, 0.22F, 0.1F, Math.min(1.0F, a * flicker * 0.65F));
        drawing = true;
        try {
            for (int side = -1; side <= 1; side += 2) {
                // his right is (-cos yaw, 0, -sin yaw) and his forward (-sin yaw, 0, cos yaw) in world space
                double rad = Math.toRadians(yaw);
                float rx = (float) -Math.cos(rad), rz = (float) -Math.sin(rad);
                float fx = (float) -Math.sin(rad), fz = (float) Math.cos(rad);
                pose.pushPose();
                pose.translate(rx * side * 0.95F - fx * 0.3F, 0.04F * Mth.sin(t * 0.2F + side), rz * side * 0.95F - fz * 0.3F);
                pose.mulPose(Axis.YP.rotationDegrees(side * 28.0F));
                pose.scale(1.08F, 1.08F, 1.08F);
                renderer.render(player, Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot()), event.getPartialTick(), pose, ghost, 0xF000F0);
                pose.popPose();
            }
        } finally {
            drawing = false;
        }
    }

    /** Passes positions through and paints every vertex one glowing colour. */
    private record Tint(VertexConsumer to, float r, float g, float b, float a) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, y, z);
            to.color(r, g, b, a);
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
