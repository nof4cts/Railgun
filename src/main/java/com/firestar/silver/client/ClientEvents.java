package com.firestar.silver.client;

import com.firestar.silver.SilverMod;
import com.firestar.silver.client.anim.PlayerAnims;
import com.firestar.silver.client.cinematic.CameraPose;
import com.firestar.silver.client.cinematic.Director;
import com.firestar.silver.client.cinematic.Overlay;
import com.firestar.silver.client.fx.ScreenFx;
import com.firestar.silver.client.render.Afterimages;
import com.firestar.silver.client.render.PostFx;
import com.firestar.silver.client.render.RibbonRenderer;
import com.firestar.silver.client.render.Views;
import com.firestar.silver.client.fx.Light;
import com.firestar.silver.config.ZConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
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

    @Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID, value = Dist.CLIENT)
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
            if (event.phase == TickEvent.Phase.START) {
                Keys.handle();
            } else {
                ClientState.tick();
                Casts.tick();
                PlayerAnims.tickStances();
                Director.tick();
                Afterimages.tick();
                Choreo.ambientTick();
                SelfTest.tick();
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            RibbonRenderer.render(event);
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                Choreo.frame(event.getPartialTick());
                Light.render(Views.view(event), event.getCamera().getPosition());
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
                return;
            }
            if (event.isAttack() && Keys.strike()) {
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

        private static final java.util.Set<Integer> LEANING = new java.util.HashSet<>();

        /** Flying fast, the whole body leans into the flight, head first. */
        @SubscribeEvent
        public static void onPlayerPre(RenderPlayerEvent.Pre event) {
            if (!(event.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer p) || !PlayerAnims.flying(p)) {
                return;
            }
            float partial = event.getPartialTick();
            double vx = p.getX() - p.xo, vz = p.getZ() - p.zo, vy = p.getY() - p.yo;
            double h = Math.sqrt(vx * vx + vz * vz);
            if (h < 0.15) {
                return;
            }
            float k = (float) Math.min(1.0, (h - 0.15) / 0.5);
            float pitch = (float) Math.toDegrees(Math.atan2(vy, h));
            float lean = k * (80 - Math.max(-30, Math.min(50, pitch)));
            com.mojang.blaze3d.vertex.PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(0, 0.9, 0);
            org.joml.Vector3f axis = new org.joml.Vector3f((float) (vz / h), 0, (float) (-vx / h));
            pose.mulPose(new org.joml.Quaternionf().fromAxisAngleDeg(axis, lean));
            pose.translate(0, -0.9, 0);
            LEANING.add(p.getId());
        }

        @SubscribeEvent
        public static void onPlayerRendered(RenderPlayerEvent.Post event) {
            if (LEANING.remove(event.getEntity().getId())) {
                event.getPoseStack().popPose();
            }
            Afterimages.after(event);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Director.stop();
            Casts.clear();
            ClientState.clear();
            Light.clear();
        }
    }

    @Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "kit", Hud.INSTANCE);
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            Keys.register(event);
        }

    }
}
