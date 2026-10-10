package com.geto.csm.client;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.SelectPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Radial wheel for choosing which cursed spirit to call. */
public class SelectorScreen extends Screen {
    private int hovered = -1;
    private final long opened = Vfx.now();

    public SelectorScreen() {
        super(Component.literal("Cursed Spirits"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, int c) {
        b.vertex(m, x, y, 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        CurseKind[] kinds = CurseKind.values();
        int n = kinds.length;
        float cx = width / 2f, cy = height / 2f;
        float open = Ease.outBack(Math.min(1f, (Vfx.now() - opened) / 220f));
        float R = Math.min(width, height) * 0.4f * open, r0 = R * 0.38f;
        g.fill(0, 0, width, height, 0x99050008);

        double ang = Math.atan2(my - cy, mx - cx);
        double dist = Math.hypot(mx - cx, my - cy);
        double step = Math.PI * 2 / n;
        hovered = dist > r0 * 0.5 ? Math.floorMod((int) Math.floor((ang + Math.PI / 2 + step / 2) / step), n) : -1;

        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            double a0 = -Math.PI / 2 + i * step - step / 2 + 0.02, a1 = a0 + step - 0.04;
            boolean hov = i == hovered, sel = i == ClientState.selected;
            int inner = hov ? Draw.alpha(kinds[i].color, 0.55f) : 0xC0140A1C;
            int outer = hov ? Draw.alpha(kinds[i].color, 0.85f) : sel ? Draw.alpha(kinds[i].color, 0.45f) : 0xD01E1028;
            float rr = hov ? R * 1.06f : R;
            for (int k = 0; k < 8; k++) {
                double s0 = a0 + (a1 - a0) * k / 8, s1 = a0 + (a1 - a0) * (k + 1) / 8;
                float ix0 = cx + (float) Math.cos(s0) * r0, iy0 = cy + (float) Math.sin(s0) * r0;
                float ix1 = cx + (float) Math.cos(s1) * r0, iy1 = cy + (float) Math.sin(s1) * r0;
                float ox0 = cx + (float) Math.cos(s0) * rr, oy0 = cy + (float) Math.sin(s0) * rr;
                float ox1 = cx + (float) Math.cos(s1) * rr, oy1 = cy + (float) Math.sin(s1) * rr;
                v(b, m, ix0, iy0, inner); v(b, m, ox0, oy0, outer); v(b, m, ox1, oy1, outer);
                v(b, m, ix0, iy0, inner); v(b, m, ox1, oy1, outer); v(b, m, ix1, iy1, inner);
            }
        }
        BufferUploader.drawWithShader(b.end());

        for (int i = 0; i < n; i++) {
            double mid = -Math.PI / 2 + i * step;
            float lr = (r0 + R) / 2f;
            float lx = cx + (float) Math.cos(mid) * lr, ly = cy + (float) Math.sin(mid) * lr;
            CurseKind k = kinds[i];
            boolean hov = i == hovered;
            ScreenFx.bigText(g, font, k.jp, lx, ly - 9, hov ? 2.0f : 1.6f, hov ? 0xFFFFFFFF : k.color, hov, 1f);
            ScreenFx.bigText(g, font, k.title, lx, ly + 8, 0.85f, 0xFFE8E0F0, false, 1f);
            float cd = ClientState.cooldownSeconds(i);
            if (cd > 0) ScreenFx.bigText(g, font, String.format("%.0fs", cd), lx, ly + 18, 0.8f, 0xFF9A8AA8, false, 1f);
        }
        CurseKind focus = hovered >= 0 ? kinds[hovered] : CurseKind.byId(ClientState.selected);
        ScreenFx.bigText(g, font, focus.jp, cx, cy - 10, 2.4f, focus.color, true, 1f);
        ScreenFx.bigText(g, font, focus.grade, cx, cy + 10, 0.8f, 0xFFFFFFFF, false, 1f);
        ScreenFx.bigText(g, font, "◉ " + ClientState.absorbed + " stored", cx, cy + 22, 0.8f, 0xFFB98CFF, false, 1f);
        RenderSystem.enableDepthTest();
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hovered >= 0) {
            ClientState.selected = hovered;
            CsmNetwork.sendToServer(new SelectPacket(hovered));
            Sfx.play(net.minecraft.sounds.SoundEvents.SCULK_CLICKING, 1.2f, 1f);
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int next = Mth.positiveModulo(ClientState.selected + (delta < 0 ? 1 : -1), CurseKind.values().length);
        ClientState.selected = next;
        CsmNetwork.sendToServer(new SelectPacket(next));
        return true;
    }
}
