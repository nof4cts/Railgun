package com.naoya.ps.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class Views {
   private Views() {
   }

   public static PoseStack view(RenderLevelStageEvent event) {
      PoseStack ps = new PoseStack();
      ps.mulPose(Axis.XP.rotationDegrees(event.getCamera().getXRot()));
      ps.mulPose(Axis.YP.rotationDegrees(event.getCamera().getYRot() + 180.0F));
      return ps;
   }
}
