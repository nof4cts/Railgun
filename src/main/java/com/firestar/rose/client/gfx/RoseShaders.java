package com.firestar.rose.client.gfx;

import com.firestar.rose.RoseMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Every Requiem Rose shader program, loaded with the resources. */
@Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RoseShaders {
    public static ShaderInstance AURA, ARC, ARC_INK, BEAM, RING, FLARE, SMOKE, RIFT, RIFT_GLOW, WARP;
    public static ShaderInstance BLOOM_DOWN, BLOOM_UP, COMPOSITE, GRADE;

    private RoseShaders() {}

    public static boolean ready() {
        return AURA != null && ARC != null && GRADE != null && COMPOSITE != null;
    }

    @SubscribeEvent
    public static void onShaders(RegisterShadersEvent e) throws IOException {
        VertexFormat fx = DefaultVertexFormat.POSITION_COLOR_TEX;
        reg(e, "aura", fx, s -> AURA = s);
        reg(e, "arc", fx, s -> ARC = s);
        reg(e, "arc_ink", fx, s -> ARC_INK = s);
        reg(e, "beam", fx, s -> BEAM = s);
        reg(e, "ring", fx, s -> RING = s);
        reg(e, "flare", fx, s -> FLARE = s);
        reg(e, "smoke", fx, s -> SMOKE = s);
        reg(e, "rift", fx, s -> RIFT = s);
        reg(e, "rift_glow", fx, s -> RIFT_GLOW = s);
        reg(e, "warp", fx, s -> WARP = s);
        VertexFormat post = DefaultVertexFormat.POSITION;
        reg(e, "bloom_down", post, s -> BLOOM_DOWN = s);
        reg(e, "bloom_up", post, s -> BLOOM_UP = s);
        reg(e, "composite", post, s -> COMPOSITE = s);
        reg(e, "grade", post, s -> GRADE = s);
    }

    private static void reg(RegisterShadersEvent e, String name, VertexFormat f, Consumer<ShaderInstance> set) throws IOException {
        e.registerShader(new ShaderInstance(e.getResourceProvider(), RoseMod.id(name), f), set);
    }
}
