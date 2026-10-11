package com.firestar.silver.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** The camera's rotation as a pose stack, built from the camera itself (some stages hand over a different matrix). */
public final class Views {
    private Views() {}

    public static PoseStack view(RenderLevelStageEvent event) {
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.XP.rotationDegrees(event.getCamera().getXRot()));
        ps.mulPose(Axis.YP.rotationDegrees(event.getCamera().getYRot() + 180.0F));
        return ps;
    }
}
