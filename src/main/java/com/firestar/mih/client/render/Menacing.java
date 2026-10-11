package com.firestar.mih.client.render;

import com.firestar.mih.client.ClientCast;
import com.firestar.mih.client.ClientState;
import com.firestar.mih.client.Casts;
import com.firestar.mih.common.Move;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** ゴゴゴゴ: the menacing letters that rise round a Stand user about to do something terrible. */
public final class Menacing {
    private Menacing() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = event.getPoseStack();
        long now = mc.level.getGameTime();
        boolean any = false;
        for (Player p : mc.level.players()) {
            ClientCast c = Casts.of(p);
            float strength = 0.0F;
            if (c != null && (c.move == Move.TIME_ACCEL || c.move == Move.JUDGEMENT || c.move == Move.DOUBLE_ACCEL)) {
                float t = c.time(partial);
                strength = Math.min(1.0F, t / 8.0F) * Math.max(0.0F, Math.min(1.0F, (c.move.length - t) / 10.0F));
            }
            ClientState.Info i = ClientState.get(p);
            if (ClientState.accelerating(i, now)) {
                strength = Math.max(strength, 0.6F);
            }
            if (strength <= 0.02F) {
                continue;
            }
            any = true;
            Vec3 at = p.getPosition(partial);
            float time = now + partial;
            for (int k = 0; k < 6; k++) {
                // each letter rises and drifts on its own loop, on either side of him
                float phase = ((time * 0.025F + k * 0.37F) % 1.0F);
                double side = (k % 2 == 0 ? 1 : -1) * (1.3 + (k % 3) * 0.35);
                double rad = Math.toRadians(-mc.gameRenderer.getMainCamera().getYRot());
                Vec3 right = new Vec3(Math.cos(rad), 0, Math.sin(rad));
                Vec3 pos = at.add(right.scale(side)).add(0, 0.6 + phase * 2.4, 0);
                float a = strength * (float) Math.sin(Math.PI * phase);
                text(ps, buffers, mc.font, cam, pos, a, 0.045F + (k % 3) * 0.008F, k);
            }
        }
        if (any) {
            buffers.endBatch();
        }
    }

    private static void text(PoseStack ps, MultiBufferSource buffers, Font font, Vec3 cam, Vec3 pos, float a, float scale, int k) {
        if (a <= 0.02F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ps.pushPose();
        ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        ps.mulPose(mc.gameRenderer.getMainCamera().rotation());
        ps.mulPose(Axis.ZP.rotationDegrees(-8 + k * 5));
        ps.scale(-scale, -scale, scale);
        Matrix4f m = ps.last().pose();
        String s = "ゴ";
        int alpha = (int) (a * 255) << 24;
        float x = -font.width(s) / 2.0F;
        font.drawInBatch(s, x + 0.8F, 0.8F, alpha | 0x1A0024, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        font.drawInBatch(s, x, 0, alpha | 0xB04CFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        ps.popPose();
    }
}
