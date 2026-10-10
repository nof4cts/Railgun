package com.cataclysm.spells.client;

import com.cataclysm.spells.SpellsMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Loads the cinematic impact/post shader. If a driver rejects it, the mod falls back to overlay frames. */
@Mod.EventBusSubscriber(modid = SpellsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpellShaders {
    private static final Logger LOG = LogUtils.getLogger();
    public static ShaderInstance IMPACT, POST, POST_ADD, PLASMA, SPACE;

    @SubscribeEvent
    public static void onRegister(RegisterShadersEvent e) {
        load(e, "impact", DefaultVertexFormat.POSITION_TEX, s -> IMPACT = s);
        load(e, "post", DefaultVertexFormat.POSITION_TEX, s -> POST = s);
        load(e, "post_add", DefaultVertexFormat.POSITION_TEX, s -> POST_ADD = s);
        load(e, "plasma", DefaultVertexFormat.POSITION_TEX_COLOR, s -> PLASMA = s);
        load(e, "space", DefaultVertexFormat.POSITION_TEX, s -> SPACE = s);
    }

    /** Registers one shader; on any failure the feature using it quietly falls back. */
    private static void load(RegisterShadersEvent e, String name, com.mojang.blaze3d.vertex.VertexFormat fmt, java.util.function.Consumer<ShaderInstance> set) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(SpellsMod.MODID, name), fmt), set);
        } catch (Exception ex) {
            LOG.error("Cataclysm Spells: shader '" + name + "' failed to load; that effect will fall back", ex);
            set.accept(null);
        }
    }

    private SpellShaders() {}
}
