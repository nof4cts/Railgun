package com.sixpaths.client;

import com.sixpaths.ModRegistry;
import com.sixpaths.SixPathsMod;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Summons and projectiles are drawn by {@link ModelRenderer} in the world pass, so their entity renderers are no-ops. */
@Mod.EventBusSubscriber(modid = SixPathsMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.RHINO.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.CENTIPEDE.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.HOUND.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.BIRD.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.ROD.get(), NoopRenderer::new);
        e.registerEntityRenderer(ModRegistry.MISSILE.get(), NoopRenderer::new);
    }

    private ClientModEvents() {}
}
