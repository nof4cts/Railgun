package com.firestar.mih.client;

import com.firestar.mih.MihMod;
import com.firestar.mih.client.anim.PlayerAnims;
import com.firestar.mih.client.cinematic.CameraPose;
import com.firestar.mih.client.cinematic.Director;
import com.firestar.mih.client.cinematic.Overlay;
import com.firestar.mih.client.fx.ScreenFx;
import com.firestar.mih.client.render.Afterimages;
import com.firestar.mih.client.render.Galaxy;
import com.firestar.mih.client.render.KnifeRenderer;
import com.firestar.mih.client.render.Menacing;
import com.firestar.mih.client.render.PostFx;
import com.firestar.mih.client.render.RibbonRenderer;
import com.firestar.mih.client.render.SkyFx;
import com.firestar.mih.client.stand.StandRenderer;
import com.firestar.mih.config.ZConfig;
import com.firestar.mih.registry.ModEntities;
import com.firestar.mih.server.StandUsers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderArmEvent;
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

    @Mod.EventBusSubscriber(modid = MihMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                SkyFx.frame(event.renderTickTime);
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
                Director.tick();
                Afterimages.tick();
                SkyFx.tick();
                SelfTest.tick();
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            SkyFx.render(event);
            StandRenderer.render(event);
            RibbonRenderer.render(event);
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                Choreo.frame(event.getPartialTick());
                com.firestar.mih.client.fx.Light.render(Galaxy.view(event), event.getCamera().getPosition());
            }
            Menacing.render(event);
            Galaxy.render(event);
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
            if (event.isAttack() && Keys.punch()) {
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

        /** A lost arm stays lost in first person too. */
        @SubscribeEvent
        public static void onArm(RenderArmEvent event) {
            ClientState.Info i = ClientState.get(event.getPlayer());
            Minecraft mc = Minecraft.getInstance();
            if (i != null && mc.level != null && i.auUntil > mc.level.getGameTime() && i.limb == StandUsers.LIMB_ARM && event.getArm() == HumanoidArm.RIGHT) {
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

        /** The Alternate Universe takes a limb: hide it on the model. */
        @SubscribeEvent
        public static void onPlayerPre(RenderPlayerEvent.Pre event) {
            ClientState.Info i = ClientState.get(event.getEntity());
            Minecraft mc = Minecraft.getInstance();
            if (i == null || mc.level == null || i.auUntil <= mc.level.getGameTime()) {
                return;
            }
            PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
            if (i.limb == StandUsers.LIMB_ARM) {
                m.rightArm.visible = false;
                m.rightSleeve.visible = false;
            } else if (i.limb == StandUsers.LIMB_LEG) {
                m.rightLeg.visible = false;
                m.rightPants.visible = false;
            }
        }

        @SubscribeEvent
        public static void onPlayerRendered(RenderPlayerEvent.Post event) {
            PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
            m.rightArm.visible = true;
            m.rightSleeve.visible = true;
            m.rightLeg.visible = true;
            m.rightPants.visible = true;
            Afterimages.after(event);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Director.stop();
            Casts.clear();
            ClientState.clear();
            com.firestar.mih.client.fx.Light.clear();
        }
    }

    @Mod.EventBusSubscriber(modid = MihMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent event) {
            event.enqueueWork(PlayerAnims::init);
        }

        @SubscribeEvent
        public static void onReload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) PostFx::reload);
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) Galaxy::reload);
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "stand", Hud.INSTANCE);
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            Keys.register(event);
        }

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.KNIFE.get(), KnifeRenderer::new);
        }
    }
}
