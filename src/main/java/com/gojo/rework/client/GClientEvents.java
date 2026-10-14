package com.gojo.rework.client;

import com.gojo.rework.client.cam.GDirector;
import com.gojo.rework.client.fx.Ink;
import com.gojo.rework.client.fx.Look;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Hooks Gojo's rework into the client: ticks, the camera, the world pass, the screen, and the cutscene lock. */
public final class GClientEvents {
    private static final Ink INK = new Ink();
    static final Look LOOK = new Look();
    private static float lastRoll;
    private static CloudStatus heldClouds;

    private GClientEvents() {}

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent e) {
            e.enqueueWork(GAnims::init);
        }
    }

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            GCasts.tick();
            GDirector.tick();
            GojoTest.tick();
            clouds(Minecraft.getInstance());
        }

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.START) {
                GDirector.frame(e.renderTickTime);
            } else {
                GojoTest.afterFrame();
            }
        }

        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            var cam = e.getCamera();
            Vec3 pos = cam.getPosition();
            org.joml.Vector3f l = cam.getLeftVector(), u = cam.getUpVector(), f = cam.getLookVector();
            INK.begin(pos, new Vec3(-l.x(), -l.y(), -l.z()), new Vec3(u.x(), u.y(), u.z()), new Vec3(-f.x(), -f.y(), -f.z()));
            GFx.draw(INK, e.getPartialTick());
            PoseStack view = new PoseStack();
            view.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(lastRoll));
            view.mulPose(com.mojang.math.Axis.XP.rotationDegrees(cam.getXRot()));
            view.mulPose(com.mojang.math.Axis.YP.rotationDegrees(cam.getYRot() + 180.0F));
            INK.end(view);
            // the orbs, arcs and glows, in the original technique's shaders
            Minecraft mc = Minecraft.getInstance();
            float time = mc.level == null ? 0 : (mc.level.getGameTime() % 100000L) + e.getPartialTick();
            com.gojo.rework.client.fx.GShade.flush(e.getPoseStack().last().pose(), e.getProjectionMatrix(), pos, time);
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
            float partial = (float) e.getPartialTick();
            LOOK.reset();
            GFx.screen(LOOK, partial);
            if (GDirector.active()) {
                GDirector.Shot s = GDirector.shot();
                if (s != null) {
                    e.setRoll(s.roll());
                }
            }
            if (LOOK.shake > 0.01F) {
                float[] sh = LOOK.shakeAngles((System.nanoTime() % 100_000_000_000L) / 1.0E9F);
                e.setYaw(e.getYaw() + sh[0]);
                e.setPitch(e.getPitch() + sh[1]);
                e.setRoll(e.getRoll() + sh[2]);
            }
            lastRoll = e.getRoll();
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov e) {
            GDirector.Shot s = GDirector.shot();
            if (s != null && e.usedConfiguredFov()) {
                e.setFOV(s.fov());
            }
        }

        @SubscribeEvent
        public static void onInput(MovementInputUpdateEvent e) {
            if (GDirector.active()) {
                Input in = e.getInput();
                in.forwardImpulse = 0;
                in.leftImpulse = 0;
                in.up = in.down = in.left = in.right = false;
                in.jumping = false;
            }
        }

        /** Holding a finished Purple: attack or use lets it go where he is looking (instead of swinging or placing). */
        @SubscribeEvent
        public static void onClick(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            GCasts.Play p = GCasts.of(mc.player);
            if (p != null && p.mine && p.aiming(0) && !GDirector.active() && (e.isAttack() || e.isUseItem())) {
                e.setCanceled(true);
                e.setSwingHand(false);
                if (p.let) {
                    return;
                }
                p.let = true;
                com.gojo.rework.net.GNet.CHANNEL.sendToServer(new com.gojo.rework.net.GNet.Shoot(mc.player.getViewVector(1.0F)));
            } else if (GDirector.active()) {
                e.setCanceled(true);
                e.setSwingHand(false);
            }
        }

        @SubscribeEvent
        public static void onHand(RenderHandEvent e) {
            if (GDirector.active()) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
            if (GDirector.active()) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onGui(RenderGuiEvent.Post e) {
            LOOK.draw(e.getGuiGraphics());
            GBanner.draw(e.getGuiGraphics(), e.getPartialTick());
        }

        @SubscribeEvent
        public static void onNameTag(RenderNameTagEvent e) {
            if (GDirector.active() && e.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                e.setResult(Event.Result.DENY);
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            GDirector.stop();
            GCasts.clear();
            GInfinity.clear();
            if (heldClouds != null) {
                Minecraft.getInstance().options.cloudStatus().set(heldClouds);
                heldClouds = null;
            }
        }
    }

    /** No clouds over the void (they would draw bright across its dark). */
    static void clouds(Minecraft mc) {
        boolean dark = mc.level != null && (GVoid.insideVoid() > 0.05F || (GDirector.playing() != null
                && GDirector.playing().move() == com.gojo.rework.common.GMove.DOMAIN));
        if (dark && heldClouds == null) {
            heldClouds = mc.options.cloudStatus().get();
            mc.options.cloudStatus().set(CloudStatus.OFF);
        } else if (!dark && heldClouds != null) {
            mc.options.cloudStatus().set(heldClouds);
            heldClouds = null;
        }
    }
}
