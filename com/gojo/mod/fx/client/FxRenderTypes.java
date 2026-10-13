package com.gojo.mod.fx.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;

public final class FxRenderTypes extends RenderType {
   public static final ResourceLocation GLOW_TEXTURE = new ResourceLocation("gojo", "textures/fx/glow.png");
   public static final ResourceLocation BEAM_TEXTURE = new ResourceLocation("gojo", "textures/fx/beam.png");
   public static final RenderType ADDITIVE_COLOR = create(
      "gojo:additive_color",
      DefaultVertexFormat.POSITION_COLOR,
      Mode.QUADS,
      8192,
      false,
      true,
      CompositeState.builder()
         .setShaderState(POSITION_COLOR_SHADER)
         .setTransparencyState(LIGHTNING_TRANSPARENCY)
         .setWriteMaskState(COLOR_WRITE)
         .setCullState(NO_CULL)
         .createCompositeState(false)
   );
   public static final RenderType GLOW = additiveTextured("glow", GLOW_TEXTURE, false);
   public static final RenderType BEAM = additiveTextured("beam", BEAM_TEXTURE, false);
   public static final RenderType DARK = dark("dark", false);
   public static final RenderType DARK_OVERLAY = dark("dark_overlay", true);
   public static final RenderType GLOW_OVERLAY = additiveTextured("glow_overlay", GLOW_TEXTURE, true);
   public static final RenderType BEAM_OVERLAY = additiveTextured("beam_overlay", BEAM_TEXTURE, true);

   private FxRenderTypes(
      String name, VertexFormat format, Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear
   ) {
      super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
   }

   private static RenderType additiveTextured(String name, ResourceLocation texture, boolean overlay) {
      return create(
         "gojo:" + name,
         DefaultVertexFormat.POSITION_COLOR_TEX,
         Mode.QUADS,
         16384,
         false,
         false,
         CompositeState.builder()
            .setShaderState(POSITION_COLOR_TEX_SHADER)
            .setTextureState(new TextureStateShard(texture, false, false))
            .setTransparencyState(LIGHTNING_TRANSPARENCY)
            .setDepthTestState(overlay ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .createCompositeState(false)
      );
   }

   private static RenderType dark(String name, boolean overlay) {
      return create(
         "gojo:" + name,
         DefaultVertexFormat.POSITION_COLOR,
         Mode.QUADS,
         8192,
         false,
         true,
         CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(overlay ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .setCullState(NO_CULL)
            .createCompositeState(false)
      );
   }
}
