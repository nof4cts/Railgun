package com.firestar.orbital.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Render types of our own. TINT: alpha-blended colour that is depth-tested but writes no depth, so a translucent
 * cel fill can sit under the additive glow and still read against a bright sky without hiding anything behind it.
 */
public final class FxTypes extends RenderType {
    public static final RenderType TINT = create("orbital_arsenal_tint", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256,
            false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private FxTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup,
            Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
}
