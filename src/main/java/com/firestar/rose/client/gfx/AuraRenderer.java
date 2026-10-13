package com.firestar.rose.client.gfx;

import com.firestar.rose.RoseMod;
import com.firestar.rose.client.RoseForms;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The Rosé aura, ported from Dragon Block Rebirth.
 *
 * <p>MIT License, Copyright (c) 2026 Medrado26 (Dragon Block Rebirth, https://www.curseforge.com/minecraft/mc-mods/dragon-block-rebirth).
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright notice
 * and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS
 * PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
 *
 * <p>The shader (assets/requiem/shaders/core/aura.*) and noise texture are the originals. This class rebuilds the same
 * shell (96 rings of 96 segments, radius 0.8 at the crown swelling to 1.3 at the base), the same placement (1.15 above
 * the feet, scaled 0.8 x 1.35 x 0.8), the same blend and depth state, and feeds the shader the SSRose form's values.
 */
public final class AuraRenderer {
    private static final ResourceLocation NOISE = RoseMod.id("textures/fx/aura_noise.png");
    private static VertexBuffer shell;
    private static final Map<UUID, float[]> PHASE = new HashMap<>();
    private static final List<Player> DRAW = new ArrayList<>();

    // SSRose, as Dragon Block Rebirth defines it
    static final float[] SIZES = {1.06F, 1.15F, 1.01F};
    static final float FREQ_Y = 2.1F, SPEED = 5.0F, AMP = 0.63F, FINE = 0.12F, UP_DIR = 0.52F;
    static final float ALP1 = 0.04F, ALP2 = 1.0F, POWER = 3.3F, DIVIS = 0.07F;
    static final float[] COLOR1 = {0.7F, 0.0F, 0.3F};
    static final float[] COLOR2 = {0.5F, 0.0F, 0.1F};
    static final float[] COLOR_NOISE = {1.05F, 0.0F, 0.45F};

    private AuraRenderer() {}

    /** The shell, exactly as Dragon Block Rebirth builds it: (radius 1, 96 rings, crown 1.3, base 0.8, no offset). */
    private static VertexBuffer shell() {
        if (shell != null) {
            return shell;
        }
        float r = 1.0F, top = 1.3F, bottom = 0.8F, yOff = 0.0F;
        int n = 96;
        float step = 360.0F / n;
        float segs = 360.0F / step;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_TEX);
        for (int i = 0; i < n; i++) {
            double u0 = i / (double) n, u1 = (i + 1) / (double) n;
            double y0 = u0 * 2.0 - 1.0 + yOff, y1 = u1 * 2.0 - 1.0 + yOff;
            double k0 = Math.pow(1.0 - u0, 1.5), k1 = Math.pow(1.0 - u1, 1.5);
            double r0 = r * (bottom + (top - bottom) * k0) * Math.sqrt(1.0 - y0 * y0);
            double r1 = r * (bottom + (top - bottom) * k1) * Math.sqrt(1.0 - y1 * y1);
            if (Double.isNaN(r0) || r0 < 0) {
                r0 = 0;
            }
            if (Double.isNaN(r1) || r1 < 0) {
                r1 = 0;
            }
            double py0 = y0 * r, py1 = y1 * r;
            float v0 = i / (float) n, v1 = (i + 1) / (float) n;
            for (float j = 0; j < segs; j++) {
                double a0 = Math.toRadians(j * step), a1 = Math.toRadians((j + 1) * step);
                float s0 = j / segs, s1 = (j + 1) / segs;
                float x00 = (float) (r0 * Math.cos(a0)), z00 = (float) (r0 * Math.sin(a0));
                float x01 = (float) (r0 * Math.cos(a1)), z01 = (float) (r0 * Math.sin(a1));
                float x11 = (float) (r1 * Math.cos(a1)), z11 = (float) (r1 * Math.sin(a1));
                float x10 = (float) (r1 * Math.cos(a0)), z10 = (float) (r1 * Math.sin(a0));
                b.vertex(x00, py0, z00).color(255, 255, 255, 255).uv(s0, v0).endVertex();
                b.vertex(x01, py0, z01).color(255, 255, 255, 255).uv(s1, v0).endVertex();
                b.vertex(x11, py1, z11).color(255, 255, 255, 255).uv(s1, v1).endVertex();
                b.vertex(x00, py0, z00).color(255, 255, 255, 255).uv(s0, v0).endVertex();
                b.vertex(x11, py1, z11).color(255, 255, 255, 255).uv(s1, v1).endVertex();
                b.vertex(x10, py1, z10).color(255, 255, 255, 255).uv(s0, v1).endVertex();
            }
        }
        shell = new VertexBuffer(VertexBuffer.Usage.STATIC);
        shell.bind();
        shell.upload(b.end());
        VertexBuffer.unbind();
        return shell;
    }

    /**
     * The aura on the scythe: the same shader and the same Rosé values, as two shells shaped to the weapon. One runs the
     * length of the shaft with its flames streaming toward the head; a thinner one wraps the blade, burning outward.
     * {@code hand} maps the hand's frame (the shaft along z, the blade toward +y) to view space.
     */
    static void scythe(ShaderInstance sh, VertexBuffer vb, int noise, Matrix4f hand, Matrix4f projection, float k, float strength) {
        if (k < 0.05F) {
            return;
        }
        float var = Mth.clamp((k - 0.2F) / 0.8F, 0, 1);
        // along the shaft: the shell's up is the shaft's way to the head
        Matrix4f shaft = new Matrix4f(hand).translate(0, 0, -0.62F).rotateX((float) Math.toRadians(-90)).scale(0.17F, 0.92F, 0.17F);
        shell(sh, vb, noise, shaft, projection, var, strength, 0.55F);
        // round the blade: its up is the blade's reach
        Matrix4f blade = new Matrix4f(hand).translate(0, 0.66F, -1.52F).scale(0.11F, 0.62F, 0.4F);
        shell(sh, vb, noise, blade, projection, var, strength, 0.75F);
    }

    private static void shell(ShaderInstance sh, VertexBuffer vb, int noise, Matrix4f mv, Matrix4f projection, float auravar, float strength,
                              float amp) {
        Matrix3f normal = new Matrix3f(mv).invert().transpose();
        sh.safeGetUniform("ModelViewMat").set(mv);
        sh.safeGetUniform("ProjMat").set(projection);
        sh.safeGetUniform("normalMatrix").set(normal);
        sh.safeGetUniform("time").set(scytheTime);
        sh.safeGetUniform("auraPhase").set(scythePhase);
        sh.safeGetUniform("flyboost").set(0.0F);
        sh.safeGetUniform("auravar").set(auravar);
        sh.safeGetUniform("sizes").set(SIZES[0], SIZES[1], SIZES[2]);
        sh.safeGetUniform("color1").set(COLOR1[0], COLOR1[1], COLOR1[2]);
        sh.safeGetUniform("color2").set(COLOR2[0], COLOR2[1], COLOR2[2]);
        sh.safeGetUniform("noiseFactor").set(1.0F);
        sh.safeGetUniform("colorNoise").set(COLOR_NOISE[0], COLOR_NOISE[1], COLOR_NOISE[2]);
        sh.safeGetUniform("freqY").set(FREQ_Y);
        sh.safeGetUniform("speed").set(SPEED);
        sh.safeGetUniform("amp").set(AMP * amp);
        sh.safeGetUniform("fineDetail").set(FINE);
        sh.safeGetUniform("upDir").set(UP_DIR);
        sh.safeGetUniform("alp1").set(ALP1 * strength);
        sh.safeGetUniform("alp2").set(ALP2 * strength);
        sh.safeGetUniform("power").set(POWER);
        sh.safeGetUniform("divis").set(DIVIS);
        sh.safeGetUniform("bloomColor").set(0.0F, 1.0F, 1.0F);
        sh.safeGetUniform("fakeBloom").set(1.0F);
        sh.safeGetUniform("realBloom").set(1.0F);
        sh.safeGetUniform("noiseBloom").set(1.0F);
        sh.setSampler("NoiseTex", noise);
        vb.bind();
        vb.drawWithShader(mv, projection, sh);
    }

    /** The first-person scythe's aura, drawn straight into the hand view. */
    public static void handScythe(Matrix4f hand, float k) {
        if (RoseShaders.AURA == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int noise = mc.getTextureManager().getTexture(NOISE).getId();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        scythe(RoseShaders.AURA, shell(), noise, hand, RenderSystem.getProjectionMatrix(), k, 1.0F);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    /** Called once a frame before drawing: who has an aura, and each one's wave phase advanced. */
    static boolean any(float partial) {
        DRAW.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RoseShaders.AURA == null) {
            return false;
        }
        boolean firstPerson = mc.options.getCameraType().isFirstPerson() && !com.firestar.rose.client.cam.Lens.active();
        for (Player p : mc.level.players()) {
            float power = RoseForms.aura(p, partial);
            if (power < 0.01F || (p == mc.player && firstPerson) || p.isInvisible()) {
                continue;
            }
            DRAW.add(p);
            float time = (p.tickCount + partial) / 20.0F;
            float[] ph = PHASE.computeIfAbsent(p.getUUID(), k -> new float[]{time, 0});
            float dt = Mth.clamp(time - ph[0], 0.0F, 0.1F);
            ph[0] = time;
            ph[1] = (ph[1] + dt * SPEED) % 1000.0F;
        }
        float now = (System.nanoTime() / 1.0E9F) % 3600.0F;
        scytheTime = now;
        scythePhase = (scythePhase + Mth.clamp(now - scytheLast, 0.0F, 0.1F) * SPEED) % 1000.0F;
        scytheLast = now;
        return !DRAW.isEmpty() || !com.firestar.rose.client.ScytheLayer.AURA_M.isEmpty();
    }

    private static float scytheTime, scytheLast, scythePhase;

    /**
     * Draws every aura gathered by {@link #any}, with the state Dragon Block Rebirth uses. {@code strength} 1 is the
     * original; the glow buffer takes a weaker copy for the bloom.
     */
    static void draw(PoseStack view, Matrix4f projection, float partial, float strength) {
        if (DRAW.isEmpty() && com.firestar.rose.client.ScytheLayer.AURA_M.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance sh = RoseShaders.AURA;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int noise = mc.getTextureManager().getTexture(NOISE).getId();
        VertexBuffer vb = shell();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        for (Player p : DRAW) {
            float auravar = RoseForms.aura(p, partial);
            Vec3 pos = p.getPosition(partial).subtract(cam);
            PoseStack ps = new PoseStack();
            ps.last().pose().set(view.last().pose());
            ps.last().normal().set(view.last().normal());
            ps.translate(pos.x, pos.y, pos.z);
            ps.translate(0.0F, 1.15F, 0.0F);
            ps.scale(0.8F, 1.35F, 0.8F);
            float time = (p.tickCount + partial) / 20.0F;
            float[] ph = PHASE.get(p.getUUID());
            sh.safeGetUniform("ModelViewMat").set(ps.last().pose());
            sh.safeGetUniform("ProjMat").set(projection);
            sh.safeGetUniform("normalMatrix").set(ps.last().normal());
            sh.safeGetUniform("time").set(time);
            sh.safeGetUniform("auraPhase").set(ph == null ? 0.0F : ph[1]);
            sh.safeGetUniform("flyboost").set(0.0F);
            sh.safeGetUniform("auravar").set(auravar);
            sh.safeGetUniform("sizes").set(SIZES[0], SIZES[1], SIZES[2]);
            sh.safeGetUniform("color1").set(COLOR1[0], COLOR1[1], COLOR1[2]);
            sh.safeGetUniform("color2").set(COLOR2[0], COLOR2[1], COLOR2[2]);
            sh.safeGetUniform("noiseFactor").set(1.0F);
            sh.safeGetUniform("colorNoise").set(COLOR_NOISE[0], COLOR_NOISE[1], COLOR_NOISE[2]);
            sh.safeGetUniform("freqY").set(FREQ_Y);
            sh.safeGetUniform("speed").set(SPEED);
            sh.safeGetUniform("amp").set(AMP);
            sh.safeGetUniform("fineDetail").set(FINE);
            sh.safeGetUniform("upDir").set(UP_DIR);
            sh.safeGetUniform("alp1").set(ALP1 * strength);
            sh.safeGetUniform("alp2").set(ALP2 * strength);
            sh.safeGetUniform("power").set(POWER);
            sh.safeGetUniform("divis").set(DIVIS);
            sh.safeGetUniform("bloomColor").set(0.0F, 1.0F, 1.0F);
            sh.safeGetUniform("fakeBloom").set(1.0F);
            sh.safeGetUniform("realBloom").set(1.0F);
            sh.safeGetUniform("noiseBloom").set(1.0F);
            sh.setSampler("NoiseTex", noise);
            RenderSystem.setShaderTexture(0, NOISE);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            vb.bind();
            vb.drawWithShader(ps.last().pose(), projection, sh);
        }
        List<Matrix4f> hands = com.firestar.rose.client.ScytheLayer.AURA_M;
        List<Float> ks = com.firestar.rose.client.ScytheLayer.AURA_K;
        for (int i = 0; i < hands.size(); i++) {
            scythe(sh, vb, noise, hands.get(i), projection, ks.get(i), strength);
        }
        VertexBuffer.unbind();
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
    }
}
