package com.firestar.orbital.client;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.client.cinematic.CameraPose;
import com.firestar.orbital.client.cinematic.Director;
import com.firestar.orbital.client.cinematic.Overlay;
import com.firestar.orbital.client.fx.Light;
import com.firestar.orbital.client.fx.ScreenFx;
import com.firestar.orbital.client.fx.Smoke;
import com.firestar.orbital.client.space.Space;
import com.firestar.orbital.client.render.PostFx;
import com.firestar.orbital.client.render.RibbonRenderer;
import com.firestar.orbital.client.render.Views;
import com.firestar.orbital.config.OConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
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

public final class ClientEvents {
    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = OrbitalMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                Space.newFrame();
                Director.frame(event.renderTickTime);
            } else {
                SelfTest.afterFrame();
            }
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Strikes.tick();
                Director.tick();
                SelfTest.tick();
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                Strikes.world(event.getPartialTick());
            }
            RibbonRenderer.render(event);
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                Minecraft mc = Minecraft.getInstance();
                Strikes.frame(event.getPartialTick());
                float bright = mc.level == null ? 1 : Mth.clamp(mc.level.getSkyDarken(event.getPartialTick()) * 1.1F, 0.3F, 1.0F);
                Smoke.render(Views.view(event), event.getCamera().getPosition(), bright);
                Light.render(Views.view(event), event.getCamera().getPosition());
            }
            Space.render(event);
            if (!Space.showing()) {
                com.firestar.orbital.client.render.WorldFx.render(event);
            }
            PostFx.render(event);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onAngles(ViewportEvent.ComputeCameraAngles event) {
            CameraPose pose = Director.pose();
            if (pose != null) {
                event.setRoll(pose.roll() * Director.weight());
            }
            ScreenFx fx = PostFx.current();
            float s = Math.min(1.2F, fx.shake) * OConfig.CLIENT.shake.get().floatValue();
            if (s > 0.001F) {
                s = s * s;
                double t = System.nanoTime() / 1.0E9 * 22.0;
                event.setYaw(event.getYaw() + s * 2.6F * noise(t, 1));
                event.setPitch(event.getPitch() + s * 3.4F * noise(t, 2));
                event.setRoll(event.getRoll() + s * 3.0F * noise(t * 0.7, 3));
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

        /** A crimson sky over an Antipode strike. */
        @SubscribeEvent
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            float[] c = Strikes.fog(event.getRed(), event.getGreen(), event.getBlue(), (float) event.getPartialTick());
            if (c != null) {
                event.setRed(c[0]);
                event.setGreen(c[1]);
                event.setBlue(c[2]);
            }
        }

        @SubscribeEvent
        public static void onFog(ViewportEvent.RenderFog event) {
            float k = Strikes.atmosphere((float) event.getPartialTick());
            if (k > 0.01F && event.getMode() == net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN) {
                float far = event.getFarPlaneDistance();
                event.setFarPlaneDistance(Mth.lerp(k * 0.45F, far, Math.max(160, far * 0.7F)));
                event.setNearPlaneDistance(Mth.lerp(k * 0.6F, event.getNearPlaneDistance(), 8));
                event.setCanceled(true);
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
        public static void onNameTag(net.minecraftforge.client.event.RenderNameTagEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (Director.active() && event.getEntity() == mc.player) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Director.stop();
            Strikes.clear();
            Light.clear();
            Smoke.clear();
        }
    }

    @Mod.EventBusSubscriber(modid = OrbitalMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) PostFx::reload);
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) Space::reload);
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) com.firestar.orbital.client.render.WorldFx::reload);
        }
    }
}
