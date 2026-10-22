package com.woh.client;

import com.woh.W;
import net.minecraft.client.player.Input;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Wires Woh's client into the game. */
public final class WClient {
    private WClient() {}

    private static long lastLog;

    /** Nothing of Woh's may ever take the game down: log it, drop the cutscene, carry on. */
    static void safe(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastLog > 5000) {
            lastLog = now;
            W.LOG.error("[woh] {} failed (ignored)", where, t);
        }
        try {
            WCam.stop();
        } catch (Throwable ignored) {
        }
    }

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
    public static final class Forge {
        private Forge() {}

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            try {
                WFx.update();
                WCasts.frame(e.renderTickTime);
                WAnims.frame();
                WCam.frame(e.renderTickTime);
            } catch (Throwable t) {
                safe("frame", t);
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent e) {
            try {
                if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) WPost.begin(e);
                else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                    WDebris.render(e);
                    WGod.render(e);
                    WStand.render(e);
                }
                else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) WPost.render(e);
            } catch (Throwable t) {
                safe("render", t);
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            try {
                WCam.tick();
                WDebris.tick();
            } catch (Throwable t) {
                safe("tick", t);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
            try {
                WCam.Pose p = WCam.pose();
                if (p != null) e.setRoll(p.roll());
                WFx.onCameraAngles(e);
                WJudge.angles(e);
            } catch (Throwable t) {
                safe("camera", t);
            }
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov e) {
            WCam.Pose p = WCam.pose();
            if (p != null && e.usedConfiguredFov()) e.setFOV(p.fov());
            float kick = WFx.fov();
            if (Math.abs(kick) > 0.01f) e.setFOV(e.getFOV() + kick);
        }

        @SubscribeEvent
        public static void onInput(MovementInputUpdateEvent e) {
            if (!WCam.running() && !WJudge.running() && !WWall.running()) return;
            Input in = e.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }

        @SubscribeEvent
        public static void onHand(RenderHandEvent e) {
            if (WCam.running() || WJudge.running() || WWall.running()) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
            if (WCam.running() || WJudge.running() || WWall.running()) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void onGui(RenderGuiEvent.Post e) {
            try {
                WHud.draw(e.getGuiGraphics());
                WHud.infinite(e.getGuiGraphics());
            } catch (Throwable t) {
                safe("hud", t);
            }
        }

        /** The letterbox goes on last, over everything (the kit's own HUD included). */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onGuiLast(RenderGuiEvent.Post e) {
            try {
                WCam.overlay(e.getGuiGraphics());
                WJudge.overlay(e.getGuiGraphics());
                WWall.overlay(e.getGuiGraphics());
            } catch (Throwable t) {
                safe("overlay", t);
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            WCam.stop();
            WCasts.clear();
            WDebris.clear();
            WGod.clear();
            WStand.clear();
            WFx.reset();
        }
    }

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                W.load();
                WAnims.init();
            });
        }

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) WPost::reload);
        }
    }
}
