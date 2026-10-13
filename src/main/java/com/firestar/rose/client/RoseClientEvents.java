package com.firestar.rose.client;

import com.firestar.rose.RoseConfig;
import com.firestar.rose.client.cam.Lens;
import com.firestar.rose.client.gfx.Canvas;
import com.firestar.rose.client.gfx.Embers;
import com.firestar.rose.client.gfx.Look;
import com.firestar.rose.common.RMove;
import com.firestar.styles.Style;
import com.firestar.styles.client.ClientStyles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class RoseClientEvents {
    private static float lastRoll;

    private RoseClientEvents() {}

    @Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase == TickEvent.Phase.START) {
                RoseKeys.handle();
            } else {
                RoseForms.tick();
                RoseCasts.tick();
                RoseAnims.tick();
                Lens.tick();
                RoseChoreo.ambientTick();
                RoseTest.tick();
            }
        }

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.END) {
                RoseTest.afterFrame();
            }
        }

        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent e) {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                PoseStack view = new PoseStack();
                view.mulPose(Axis.ZP.rotationDegrees(lastRoll));
                view.mulPose(Axis.XP.rotationDegrees(e.getCamera().getXRot()));
                view.mulPose(Axis.YP.rotationDegrees(e.getCamera().getYRot() + 180.0F));
                Canvas.world(view, e.getProjectionMatrix(), e.getCamera(), e.getPartialTick());
            } else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                Canvas.lens(e.getPartialTick());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
            float partial = (float) e.getPartialTick();
            if (Lens.place(e.getCamera(), partial)) {
                Lens.Pose p = Lens.pose();
                e.setYaw(p.yaw());
                e.setPitch(p.pitch());
                e.setRoll(p.roll());
            }
            Look l = Canvas.LOOK;
            float s = Math.min(1.2F, l.shake) * RoseConfig.CLIENT.shake.get().floatValue();
            if (s > 0.001F) {
                s = s * s;
                double t = System.nanoTime() / 1.0E9 * 24.0;
                e.setYaw(e.getYaw() + s * 3.0F * noise(t, 1));
                e.setPitch(e.getPitch() + s * 2.4F * noise(t, 2));
                e.setRoll(e.getRoll() + s * 3.6F * noise(t * 0.7, 3));
            }
            lastRoll = e.getRoll();
        }

        private static float noise(double t, int seed) {
            return (float) (Math.sin(t + seed * 12.9898) * 0.55 + Math.sin(t * 2.31 + seed * 7.233) * 0.3 + Math.sin(t * 5.17 + seed * 3.719) * 0.15);
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void onFov(ViewportEvent.ComputeFov e) {
            if (!e.usedConfiguredFov()) {
                return;
            }
            Lens.Pose p = Lens.pose();
            double fov = e.getFOV();
            if (p != null && (Lens.active() || RoseTest.observing())) {
                fov = p.fov();
            }
            float punch = Canvas.LOOK.punch;
            if (punch > 0.001F) {
                fov *= 1.0 - 0.14 * Math.min(1.2F, punch);
            }
            e.setFOV(fov);
        }

        @SubscribeEvent
        public static void onMovement(MovementInputUpdateEvent e) {
            Minecraft mc = Minecraft.getInstance();
            RoseCast c = mc.player == null ? null : RoseCasts.of(mc.player);
            boolean held = Lens.active() || (c != null && c.time(0) <= c.move.anchorUntil());
            if (held) {
                Input in = e.getInput();
                in.forwardImpulse = 0;
                in.leftImpulse = 0;
                in.up = in.down = in.left = in.right = false;
                in.jumping = false;
                in.shiftKeyDown = Lens.active() && in.shiftKeyDown;
            }
        }

        @SubscribeEvent
        public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
            if (Lens.active()) {
                e.setSwingHand(false);
                e.setCanceled(true);
                return;
            }
            if (e.isAttack() && RoseKeys.reap()) {
                e.setSwingHand(false);
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onHand(RenderHandEvent e) {
            if (Lens.active()) {
                e.setCanceled(true);
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            AbstractClientPlayer p = mc.player;
            if (p == null || e.getHand() != InteractionHand.MAIN_HAND || !ClientStyles.mine(Style.ROSE) || !p.getMainHandItem().isEmpty()) {
                return;
            }
            float k = RoseForms.scythe(p, e.getPartialTick());
            if (k < 0.02F) {
                return;
            }
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            float swing = 0, lift = 0;
            RoseCast c = RoseCasts.of(p);
            if (c != null && c.move == RMove.REAP) {
                float t = c.time(e.getPartialTick());
                float u = Look.window(t, 0, 2, 3.5F, 9);
                int step = c.seed & 3;
                swing = (float) Math.sin(Math.min(1, t / 5) * Math.PI) * (step % 2 == 0 ? 75 : -75) * u;
                lift = step == 3 ? -40 * u : 0;
            }
            ps.translate(0.62F, -0.62F, -0.85F);
            ps.mulPose(Axis.YP.rotationDegrees(swing));
            ps.mulPose(Axis.ZP.rotationDegrees(24));
            ps.mulPose(Axis.XP.rotationDegrees(70 + lift));
            ps.mulPose(Axis.ZP.rotationDegrees(90));
            ps.scale(0.8F, 0.8F, 0.8F);
            ScytheLayer.drawShaft(ps, e.getMultiBufferSource(), e.getPackedLight(), k);
            Canvas.handBlade(ps.last().pose(), k, RoseChoreo.bladeHeat(p, e.getPartialTick()));
            ps.popPose();
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
            if (Lens.active() && e.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onGui(RenderGuiEvent.Post e) {
            RoseHud.cinema(e.getGuiGraphics(), e.getPartialTick());
        }

        @SubscribeEvent
        public static void onNameTag(net.minecraftforge.client.event.RenderNameTagEvent e) {
            Minecraft mc = Minecraft.getInstance();
            if (Lens.active() && e.getEntity() == mc.player) {
                e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            Lens.stop();
            RoseCasts.clear();
            RoseForms.clear();
            Embers.clear();
        }
    }

    @Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent e) {
            e.enqueueWork(RoseAnims::init);
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "requiem_kit", RoseHud.INSTANCE);
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent e) {
            RoseKeys.register(e);
        }

        @SubscribeEvent
        public static void onLayers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                if (e.getSkin(skin) instanceof PlayerRenderer r) {
                    r.addLayer(new ScytheLayer(r));
                }
            }
        }
    }
}
