package com.abomination.horror.client;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.client.fx.ClientFx;
import com.abomination.horror.client.particle.BloodParticle;
import com.abomination.horror.client.particle.IchorParticle;
import com.abomination.horror.client.render.AbominationModel;
import com.abomination.horror.client.render.AbominationRenderer;
import com.abomination.horror.registry.ModEntities;
import com.abomination.horror.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AbominationMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AbominationModel.LAYER, AbominationModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ABOMINATION.get(), AbominationRenderer::new);
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLOOD.get(), BloodParticle.Provider::new);
        event.registerSpriteSet(ModParticles.ICHOR.get(), IchorParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(ClientFx.OVERLAY_ID, ClientFx::renderOverlay);
    }
}
