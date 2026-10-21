package com.yuji.client;

import com.yuji.Y;
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

/** Wires Yuji's client into the game. */
public final class YClient {
    private YClient() {}

    private static long lastLog;

    /** Nothing of Yuji's may ever take the game down: log it, drop the cutscene, carry on. */
    static void safe(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - lastLog > 5000) {
            lastLog = now;
            Y.LOG.error("[yuji] {} failed (ignored)", where, t);
        }
        try {
            YCam.stop();
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
                YFx.update();
                YCasts.frame(e.renderTickTime);
                YAnims.frame();
                YCam.frame(e.renderTickTime);
            } catch (Throwable t) {
                safe("frame", t);
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent e) {
            try {
                if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) YPost.begin(e);
                else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                    YDebris.render(e);
                    YGod.render(e);
                }
                else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) YPost.render(e);
            } catch (Throwable t) {
                safe("render", t);
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            try {
                YCam.tick();
                YDebris.tick();
            } catch (Throwable t) {
                safe("tick", t);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
            try {
                YCam.Pose p = YCam.pose();
                if (p != null) e.setRoll(p.roll());
                YFx.onCameraAngles(e);
            } catch (Throwable t) {
                safe("camera", t);
            }
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov e) {
            YCam.Pose p = YCam.pose();
            if (p != null && e.usedConfiguredFov()) e.setFOV(p.fov());
            float kick = YFx.fov();
            if (Math.abs(kick) > 0.01f) e.setFOV(e.getFOV() + kick);
        }

        @SubscribeEvent
        public static void onInput(MovementInputUpdateEvent e) {
            if (!YCam.running()) return;
            Input in = e.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }

        @SubscribeEvent
        public static void onHand(RenderHandEvent e) {
            if (YCam.running()) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
            if (YCam.running()) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void onGui(RenderGuiEvent.Post e) {
            try {
                YHud.draw(e.getGuiGraphics());
                YHud.infinite(e.getGuiGraphics());
            } catch (Throwable t) {
                safe("hud", t);
            }
        }

        /** The letterbox goes on last, over everything (the kit's own HUD included). */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onGuiLast(RenderGuiEvent.Post e) {
            try {
                YCam.overlay(e.getGuiGraphics());
            } catch (Throwable t) {
                safe("overlay", t);
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            YCam.stop();
            YCasts.clear();
            YDebris.clear();
            YGod.clear();
            YFx.reset();
        }
    }

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                Y.load();
                YAnims.init();
            });
        }

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((ResourceManagerReloadListener) YPost::reload);
        }
    }
}
