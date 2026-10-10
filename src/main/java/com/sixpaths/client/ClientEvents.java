package com.sixpaths.client;

import com.sixpaths.SixPathsMod;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import com.sixpaths.Ability;
import com.sixpaths.ModRegistry;
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

@Mod.EventBusSubscriber(modid = SixPathsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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
        ModelRenderer.tick();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        RenderLevelStageEvent.Stage stage = e.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            SpaceSky.render(e.getPoseStack());
            return;
        }
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

        if (solid) {
            ModelRenderer.solid(ctx);
            Draw.worldPassBegin(false);
            BufferBuilder b = Draw.begin(VertexFormat.Mode.QUADS);
            ctx.vc = b;
            Vfx.renderSolid(ctx);
            Draw.end(b);
            Draw.worldPassEnd();
        } else {
            ScreenFx.captureMatrices(new Matrix4f(ps.last().pose()), new Matrix4f(e.getProjectionMatrix()), camPos);
            ScreenFx.captureCamLeft(Vfx.v3(e.getCamera().getLeftVector()));
            Draw.worldPassBegin(true);
            BufferBuilder b = Draw.begin(VertexFormat.Mode.QUADS);
            ctx.vc = b;
            ModelRenderer.glow(ctx);
            Vfx.renderGlow(ctx);
            Draw.end(b);
            Draw.worldPassEnd();
            Plasma.flush(ps, e.getCamera(), camPos);
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
        if (CameraDirector.active()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGuiPre(RenderGuiEvent.Pre e) {
        PostPipeline.run();
        ScreenFx.preGui(e.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onGuiPost(RenderGuiEvent.Post e) {
        Hud.render(e.getGuiGraphics());
        ScreenFx.postGui(e.getGuiGraphics(), e.getPartialTick());
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isShiftKeyDown()) return;
        if (!mc.player.getMainHandItem().is(ModRegistry.FOCUS.get())) return;
        int n = Ability.values().length;
        SelectorScreen.pick(Math.floorMod(ClientState.selected + (e.getScrollDelta() < 0 ? 1 : -1), n));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        CameraDirector.reset();
        Vfx.clear();
        ScreenFx.clear();
        Sfx.clear();
        SpaceSky.clear();
        BeastAnim.clear();
        SpineTrail.clear();
        ClientState.clear();
    }

    private ClientEvents() {}
}
