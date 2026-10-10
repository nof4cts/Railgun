package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The jackpot's green cursed energy wrapped round the body: Hakari's own model drawn again a little larger in an
 * additive green glow, breathing with the aura, so he reads as burning green from any distance.
 */
@Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
public final class JackpotGlow {
    private static boolean drawing;

    private JackpotGlow() {}

    @SubscribeEvent
    public static void after(RenderPlayerEvent.Post event) {
        if (drawing || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        StatePacket s = null;
        for (StatePacket p : ClientState.all()) {
            if (p.entityId == player.getId() && (p.jackpotTicks > 0 || p.jackpotCutTicks > 0)) {
                s = p;
            }
        }
        if (s == null) {
            return;
        }
        float build = s.jackpotCutTicks > 0 ? Mth.clamp(1.0F - s.jackpotCutTicks / 110.0F, 0.0F, 1.0F) : 1.0F;
        float time = (System.currentTimeMillis() % 100000L) / 1000.0F;
        float breathe = 0.75F + 0.25F * Mth.sin(time * 4.2F);
        float a = 0.42F * build * breathe;
        if (a <= 0.01F) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        MultiBufferSource base = event.getMultiBufferSource();
        MultiBufferSource glow = type -> new Tint(base.getBuffer(RenderType.lightning()), 0.22F, 1.0F, 0.48F, a);
        drawing = true;
        try {
            for (int layer = 0; layer < 2; layer++) {
                float scale = layer == 0 ? 1.06F : 1.14F;
                pose.pushPose();
                pose.translate(0.0F, -0.02F * layer, 0.0F);
                pose.scale(scale, scale, scale);
                MultiBufferSource src = layer == 0 ? glow
                        : (type -> new Tint(base.getBuffer(RenderType.lightning()), 0.12F, 0.85F, 0.35F, a * 0.45F));
                event.getRenderer().render(player, Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot()), event.getPartialTick(), pose, src, 0xF000F0);
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
