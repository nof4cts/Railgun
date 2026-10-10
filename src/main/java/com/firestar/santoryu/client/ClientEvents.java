package com.firestar.santoryu.client;

import com.firestar.santoryu.SantoryuMod;
import com.firestar.santoryu.client.anim.PlayerAnims;
import com.firestar.santoryu.client.cinematic.CameraPose;
import com.firestar.santoryu.client.cinematic.Director;
import com.firestar.santoryu.client.cinematic.Overlay;
import com.firestar.santoryu.client.fx.ScreenFx;
import com.firestar.santoryu.client.render.GhostRenderer;
import com.firestar.santoryu.client.render.PostFx;
import com.firestar.santoryu.client.render.RibbonRenderer;
import com.firestar.santoryu.client.render.SwordLayer;
import com.firestar.santoryu.config.ZConfig;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientEvents {
    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = SantoryuMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                Director.frame(event.renderTickTime);
            } else {
                SelfTest.afterFrame();
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Keys.handle();
                Casts.tick();
                Director.tick();
                SelfTest.tick();
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            RibbonRenderer.render(event);
            PostFx.render(event);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
            CameraPose pose = Director.pose();
            if (pose != null) {
                event.setRoll(pose.roll() * Director.weight());
            }
            ScreenFx fx = PostFx.current();
            float s = Math.min(1.0F, fx.shake) * ZConfig.CLIENT.shake.get().floatValue();
            if (s > 0.001F) {
                s = s * s;
                double t = System.nanoTime() / 1.0E9 * 26.0;
                event.setYaw(event.getYaw() + s * 3.2F * noise(t, 1));
                event.setPitch(event.getPitch() + s * 2.6F * noise(t, 2));
                event.setRoll(event.getRoll() + s * 4.0F * noise(t * 0.7, 3));
            }
        }

        private static float noise(double t, int seed) {
            return (float) (Math.sin(t + seed * 12.9898) * 0.55 + Math.sin(t * 2.31 + seed * 7.233) * 0.3 + Math.sin(t * 5.17 + seed * 3.719) * 0.15);
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov event) {
            CameraPose pose = Director.pose();
            if (pose != null && event.usedConfiguredFov()) {
                event.setFOV(pose.fov());
            }
        }

        @SubscribeEvent
        public static void onMovement(MovementInputUpdateEvent event) {
            if (Director.active()) {
                Input in = event.getInput();
                in.forwardImpulse = 0.0F;
                in.leftImpulse = 0.0F;
                in.up = in.down = in.left = in.right = false;
                in.jumping = false;
                in.shiftKeyDown = false;
            }
        }

        @SubscribeEvent
        public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
            if (Director.active()) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onHand(RenderHandEvent event) {
            if (Director.active()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
            if (Overlay.hideHud()) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onGui(RenderGuiEvent.Post event) {
            Overlay.draw(event.getGuiGraphics(), event.getPartialTick());
        }

        @SubscribeEvent
        public static void onPlayerRendered(RenderPlayerEvent.Post event) {
            GhostRenderer.after(event);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Director.stop();
            Casts.clear();
        }
    }

    @Mod.EventBusSubscriber(modid = SantoryuMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent event) {
            event.enqueueWork(PlayerAnims::init);
        }

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) PostFx::reload);
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "techniques", Hud.INSTANCE);
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            Keys.register(event);
        }

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void onLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer renderer = event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new SwordLayer((LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) renderer));
                }
            }
        }
    }
}
