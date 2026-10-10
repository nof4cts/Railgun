package com.geto.csm.client;

import com.geto.csm.entity.CurseEntity;
import com.geto.csm.entity.PyreEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Draws every curse in the world: ink outline pass, cel-shaded fill pass, then the glow layer. */
public final class CurseRenderer {
    private static final double MAX_DIST = 160;

    private static List<CurseEntity> visible(Vfx.Ctx c) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        List<CurseEntity> out = new ArrayList<>();
        if (level == null) return out;
        Set<Integer> alive = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof CurseEntity ce && ce.isAlive()) {
                alive.add(e.getId());
                if (e.position().distanceTo(c.camPos) < MAX_DIST && !e.isInvisible()) out.add(ce);
            }
        }
        if (mc.level.getGameTime() % 100 == 0) SpineTrail.prune(alive);
        return out;
    }

    private static float brightness(CurseEntity e) {
        Minecraft mc = Minecraft.getInstance();
        int l = mc.level.getMaxLocalRawBrightness(BlockPos.containing(e.getEyePosition()));
        return 0.42f + 0.58f * l / 15f;
    }

    public static void solid(Vfx.Ctx c) {
        List<CurseEntity> list = visible(c);
        if (list.isEmpty()) return;
        // 1) ink: inflated hulls, front faces culled → only the silhouette rim shows
        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_FRONT);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        BufferBuilder b = Draw.begin(VertexFormat.Mode.QUADS);
        for (CurseEntity e : list) pass(c, e, b, Mesh.OUTLINE);
        Draw.end(b);
        GL11.glCullFace(GL11.GL_BACK);
        // 2) fill: cel-shaded body
        RenderSystem.disableCull();
        b = Draw.begin(VertexFormat.Mode.QUADS);
        for (CurseEntity e : list) pass(c, e, b, Mesh.FILL);
        Draw.end(b);
        RenderSystem.enableCull();
    }

    private static void pass(Vfx.Ctx c, CurseEntity e, BufferBuilder b, int mode) {
        Vec3 origin = e.getPosition(c.partial);
        c.at(origin);
        Mesh.ink = CurseModels.inkWidth(e);
        float hurt = e.hurtTime > 0 ? e.hurtTime / 10f : 0f;
        Mesh.begin(mode, b, c.m(), c.camRel(origin), brightness(e), hurt);
        CurseModels.draw(e, origin, c.partial);
        c.pop();
    }

    public static void glow(Vfx.Ctx c) {
        for (CurseEntity e : visible(c)) {
            Vec3 origin = e.getPosition(c.partial);
            c.at(origin);
            CurseModels.glow(e, origin, c.partial, c.vc, c.m(), c.camRel(origin), c);
            c.pop();
        }
    }

    public static void text(Vfx.Ctx c, MultiBufferSource.BufferSource buffers) {
        Font font = Minecraft.getInstance().font;
        for (CurseEntity e : visible(c)) {
            if (e instanceof PyreEntity p) {
                float heat = PyreEntity.heat(p.tickCount);
                Vec3 at = p.getPosition(c.partial).add(0, 3.3, 0);
                int col = Draw.lerp(0xFFFF6A20, 0xFFFFF4D0, (heat - 120f) / 2880f);
                billboard(c, font, buffers, String.format("%,d°C", (int) heat), at, 0.028f, col);
            }
        }
        buffers.endBatch();
    }

    private static void billboard(Vfx.Ctx c, Font font, MultiBufferSource buffers, String s, Vec3 world, float scale, int color) {
        PoseStack ps = c.ps;
        ps.pushPose();
        ps.translate(world.x - c.camPos.x, world.y - c.camPos.y, world.z - c.camPos.z);
        ps.mulPose(c.cam.rotation());
        ps.scale(-scale, -scale, scale);
        font.drawInBatch(s, -font.width(s) / 2f, -4, color, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x60000000, LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private CurseRenderer() {}
}
