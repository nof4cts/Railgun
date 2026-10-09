package com.railgun.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class FxRenderTypes extends RenderType {
    private FxRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean a, boolean s, Runnable st, Runnable cl) {
        super(n, f, m, b, a, s, st, cl);
        throw new IllegalStateException();
    }

    /** vanilla 1x1 white texture, used with vertex colours for the solid gun body */
    public static final ResourceLocation WHITE = new ResourceLocation("textures/misc/white.png");

    /** Additive, no cull, depth-tested, no depth write: all glow/energy effects. */
    public static final RenderType GLOW = create("railgun_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
}
