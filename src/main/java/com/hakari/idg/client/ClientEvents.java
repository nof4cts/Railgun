package com.hakari.idg.client;

import com.hakari.idg.HakariMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = HakariMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Sfx.run();
        CameraDirector.frame(e.renderTickTime);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        CameraDirector.clientTick();
        Vfx.prune();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        RenderLevelStageEvent.Stage stage = e.getStage();
        boolean solid = stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        boolean glow = stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (!solid && !glow) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack ps = e.getPoseStack();
        Vec3 camPos = e.getCamera().getPosition();
        Vfx.Ctx ctx = new Vfx.Ctx();
        ctx.ps = ps;
        ctx.cam = e.getCamera();
        ctx.camPos = camPos;
        ctx.t = Vfx.now();
        ctx.partial = e.getPartialTick();

        if (glow) ScreenFx.captureMatrices(new Matrix4f(ps.last().pose()), new Matrix4f(e.getProjectionMatrix()), camPos);

        Draw.worldPassBegin(glow);
        BufferBuilder b = Draw.begin(VertexFormat.Mode.QUADS);
        ctx.vc = b;
        if (solid) {
            DomainRenderer.solid(ctx);
            Vfx.renderSolid(ctx);
        } else {
            DomainRenderer.glow(ctx);
            Vfx.renderGlow(ctx);
        }
        Draw.end(b);
        Draw.worldPassEnd();

        if (glow) {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            DomainRenderer.text(ctx, buffers);
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        float[] s = CameraDirector.shakeOffsets();
        e.setYaw(e.getYaw() + s[0]);
        e.setPitch(e.getPitch() + s[1]);
        e.setRoll(e.getRoll() + s[2] + CameraDirector.roll());
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (!e.usedConfiguredFov() || !CameraDirector.active()) return;
        e.setFOV(CameraDirector.fov(e.getFOV()));
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent e) {
        if (CameraDirector.active()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        // Cutscenes are full-screen: hide the hotbar, crosshair, chat, everything vanilla.
        if (CameraDirector.active()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGuiPre(RenderGuiEvent.Pre e) {
        ScreenFx.preGui(e.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onGuiPost(RenderGuiEvent.Post e) {
        Hud.render(e.getGuiGraphics(), e.getPartialTick());
        RiichiOverlay.render(e.getGuiGraphics(), e.getPartialTick());
        ScreenFx.postGui(e.getGuiGraphics(), e.getPartialTick());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        CameraDirector.reset();
        Vfx.clear();
        ScreenFx.clear();
        ClientState.clear();
        ReelSet.clear();
        RiichiOverlay.clear();
        DomainRenderer.clear();
        Sfx.clear();
    }

    private ClientEvents() {}
}
