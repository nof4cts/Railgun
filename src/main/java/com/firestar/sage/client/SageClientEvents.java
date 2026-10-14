package com.firestar.sage.client;

import com.firestar.sage.client.cam.Director;
import com.firestar.sage.client.fx.Ink;
import com.firestar.sage.client.fx.Look;
import com.firestar.sage.common.SMove;
import com.firestar.sage.net.SageNet;
import com.firestar.styles.Style;
import com.firestar.styles.client.ClientStyles;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
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

public final class SageClientEvents {
    private static final Ink INK = new Ink();
    static final Look LOOK = new Look();
    private static float lastRoll;

    private SageClientEvents() {}

    @Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent e) {
            e.enqueueWork(SageAnims::init);
        }

        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent e) {
            SageKeys.register(e);
        }

        @SubscribeEvent
        public static void onLayers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                if (e.getSkin(skin) instanceof PlayerRenderer r) {
                    r.addLayer(new StaffLayer(r));
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        private static boolean mine() {
            return ClientStyles.mine(Style.SAGE);
        }

        private static void ask(SMove m) {
            SageNet.CHANNEL.sendToServer(new SageNet.Ask(m.ordinal()));
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            SageForms.tick();
            SageCasts.tick();
            SageAnims.tick();
            Director.tick();
            SageTest.tick();
            if (mc.player == null || mc.screen != null || !mine() || Director.active()) {
                return;
            }
            while (SageKeys.MANTLE.consumeClick()) {
                ask(SMove.MANTLE);
            }
            for (Map.Entry<SMove, KeyMapping> en : SageKeys.all().entrySet()) {
                while (en.getValue().consumeClick()) {
                    ask(en.getKey());
                }
            }
        }

        /** The staff strikes on the attack button while the Mantle is on (nothing else is swung). */
        @SubscribeEvent
        public static void onAttack(InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc = Minecraft.getInstance();
            if (e.isAttack() && mine() && mc.player != null && mc.player.getMainHandItem().isEmpty() && SageForms.ascended(mc.player)) {
                e.setCanceled(true);
                e.setSwingHand(false);
                ask(SMove.STAFF);
            }
        }

        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent e) {
            if (e.phase == TickEvent.Phase.START) {
                Director.frame(e.renderTickTime);
            } else {
                SageTest.afterFrame();
            }
        }

        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            var cam = e.getCamera();
            Vec3 pos = cam.getPosition();
            org.joml.Vector3f l = cam.getLeftVector(), u = cam.getUpVector(), f = cam.getLookVector();
            INK.begin(pos, new Vec3(-l.x(), -l.y(), -l.z()), new Vec3(u.x(), u.y(), u.z()), new Vec3(-f.x(), -f.y(), -f.z()));
            SageFx.draw(INK, e.getPartialTick());
            PoseStack view = new PoseStack();
            view.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(lastRoll));
            view.mulPose(com.mojang.math.Axis.XP.rotationDegrees(cam.getXRot()));
            view.mulPose(com.mojang.math.Axis.YP.rotationDegrees(cam.getYRot() + 180.0F));
            INK.end(view);
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
            float partial = (float) e.getPartialTick();
            LOOK.reset();
            SageFx.screen(LOOK, partial);
            if (Director.active()) {
                LOOK.bars(1);
                Director.Shot s = Director.shot();
                if (s != null) {
                    e.setRoll(s.roll());
                }
            }
            float[] sh = LOOK.shakeAngles((System.nanoTime() % 100_000_000_000L) / 1.0E9F);
            e.setYaw(e.getYaw() + sh[0]);
            e.setPitch(e.getPitch() + sh[1]);
            e.setRoll(e.getRoll() + sh[2]);
            lastRoll = e.getRoll();
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov e) {
            Director.Shot s = Director.shot();
            if (s != null && e.usedConfiguredFov()) {
                e.setFOV(s.fov());
            }
        }

        @SubscribeEvent
        public static void onInput(MovementInputUpdateEvent e) {
            if (Director.active()) {
                Input in = e.getInput();
                in.forwardImpulse = 0;
                in.leftImpulse = 0;
                in.up = in.down = in.left = in.right = false;
                in.jumping = false;
                in.shiftKeyDown = false;
            }
        }

        @SubscribeEvent
        public static void onHand(RenderHandEvent e) {
            if (Director.active()) {
                e.setCanceled(true);
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !mc.player.getMainHandItem().isEmpty() || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
                return;
            }
            float m = SageForms.mantle(mc.player, e.getPartialTick());
            if (m < 0.05F) {
                return;
            }
            // first person: the staff upright in the right hand, its head reaching up and forward
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(0.56F, -0.55F, -0.8F);
            ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-62));
            ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-12));
            StaffLayer.draw(ps.last().pose(), e.getMultiBufferSource(), m);
            ps.popPose();
        }

        @SubscribeEvent
        public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
            if (Director.active()) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onGui(RenderGuiEvent.Post e) {
            LOOK.draw(e.getGuiGraphics());
            SageHud.draw(e.getGuiGraphics(), e.getPartialTick());
            SageCards.draw(e.getGuiGraphics(), e.getPartialTick());
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            Director.stop();
            SageCasts.clear();
            SageForms.forget();
        }
    }
}
