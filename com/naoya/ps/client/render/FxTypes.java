package com.naoya.ps.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.CompositeState;

public final class FxTypes extends RenderType {
   public static final RenderType TINT = create(
      "naoya_tint",
      DefaultVertexFormat.POSITION_COLOR,
      Mode.QUADS,
      256,
      false,
      true,
      CompositeState.builder()
         .setShaderState(POSITION_COLOR_SHADER)
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );

   private FxTypes(String name, VertexFormat format, Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
      super(name, format, mode, size, crumbling, sort, setup, clear);
   }
}
