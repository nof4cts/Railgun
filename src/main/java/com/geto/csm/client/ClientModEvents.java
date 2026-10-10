package com.geto.csm.client;

import com.geto.csm.CsmMod;
import com.geto.csm.ModRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Curses are drawn by {@link CurseRenderer} in the world pass, so their entity renderers are no-ops. */
@Mod.EventBusSubscriber(modid = CsmMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.WYRM.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.WORM.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.RAY.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.CENTIPEDE.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.PYRE.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.KUCHISAKE.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.TAMAMO.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.NAMAZU.get(), NoopRenderer::new);
    }

    private ClientModEvents() {}
}
