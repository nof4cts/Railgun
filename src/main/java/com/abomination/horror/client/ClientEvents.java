package com.abomination.horror.client;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.client.fx.ClientFinisher;
import com.abomination.horror.client.fx.ClientFx;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AbominationMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientFinisher.tick();
            ClientFx.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        ClientFinisher.render(event);
    }

    /** Hide the victim's real head once it has been torn off; the severed copy is rendered separately. */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (ClientFinisher.isHeadless(event.getEntity())) {
            PlayerModel<?> model = event.getRenderer().getModel();
            model.head.visible = false;
            model.hat.visible = false;
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        PlayerModel<?> model = event.getRenderer().getModel();
        model.head.visible = true;
        model.hat.visible = true;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientFx.onCameraAngles(event);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        ClientFinisher.onFov(event);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        ClientFinisher.onInput(event);
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        ClientFinisher.onOverlayPre(event);
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof DeathScreen) ClientFinisher.releaseCamera();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFinisher.reset();
        ClientFx.reset();
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        ClientFinisher.onLocalRespawn();
    }
}
