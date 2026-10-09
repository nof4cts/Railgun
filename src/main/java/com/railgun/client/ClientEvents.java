package com.railgun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.railgun.ClientConfig;
import com.railgun.RailgunMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

public final class ClientEvents {

    @Mod.EventBusSubscriber(modid = RailgunMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("railgun_fx", ScreenFx::render);
        }
    }

    @Mod.EventBusSubscriber(modid = RailgunMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) { ClientShots.SHOTS.clear(); return; }
            long gt = mc.level.getGameTime();
            ClientShots.SHOTS.removeIf(s -> s.dead(gt));
        }

        @SubscribeEvent
        public static void renderLevel(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            if (ClientShots.SHOTS.isEmpty()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            PoseStack ps = e.getPoseStack();
            Vec3 cam = e.getCamera().getPosition();
            MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
            VertexConsumer vc = bs.getBuffer(FxRenderTypes.GLOW);
            Matrix4f m = ps.last().pose();
            long gt = mc.level.getGameTime();
            for (ShotFx s : ClientShots.SHOTS) s.render(vc, m, cam, e.getCamera(), e.getPartialTick(), gt);
            bs.endBatch(FxRenderTypes.GLOW);
        }

        @SubscribeEvent
        public static void cameraAngles(ViewportEvent.ComputeCameraAngles e) {
            if (!ClientConfig.SCREEN_SHAKE.get()) return;
            long now = Util.getMillis();
            float ch = ScreenFx.charge((float) e.getPartialTick());
            float amp = ScreenFx.shake(now) + 0.35f * ch * ch;
            if (amp < 0.001f) return;
            double t = now * 0.001;
            e.setYaw(e.getYaw() + (float) (amp * (Math.sin(t * 61) * 0.6 + Math.sin(t * 37 + 1.3) * 0.4)));
            e.setPitch(e.getPitch() + (float) (amp * (Math.sin(t * 53 + 0.7) * 0.6 + Math.sin(t * 29) * 0.4)));
            e.setRoll(e.getRoll() + (float) (amp * 1.2 * Math.sin(t * 47 + 2.1)));
        }

        @SubscribeEvent
        public static void fov(ViewportEvent.ComputeFov e) {
            float ch = ScreenFx.charge((float) e.getPartialTick());
            double f = 1.0 - 0.16 * ch * ch;
            f *= ScreenFx.fovPunch(Util.getMillis());
            e.setFOV(e.getFOV() * f);
        }
    }

    private ClientEvents() {}
}
