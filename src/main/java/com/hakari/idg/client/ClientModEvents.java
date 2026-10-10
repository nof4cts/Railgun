package com.hakari.idg.client;

import com.hakari.idg.HakariMod;
import com.hakari.idg.ModRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = HakariMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.RESERVE_BALL_ENTITY.get(), ReserveBallRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        // Register the custom arm poses early (extensible enum) instead of on first use.
        e.enqueueWork(() -> {
            Object warm = ArmPoses.SIGN;
        });
    }

    private ClientModEvents() {}
}
