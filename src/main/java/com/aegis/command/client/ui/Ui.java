package com.aegis.command.client.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Float-precision immediate-mode drawing on top of GuiGraphics: rects, gradients, thick lines,
 * triangles, rings, arcs, tech brackets and scaled text, all respecting a global alpha so whole
 * screens can cross-fade.
 */
public final class Ui {
    public static GuiGraphics g;
    public static Font font;
    public static float alpha = 1f;

    private Ui() {}

    public static void begin(GuiGraphics graphics) {
        g = graphics;
        font = Minecraft.getInstance().font;
        alpha = 1f;
    }

    private static VertexConsumer vc() {
        return g.bufferSource().getBuffer(RenderType.gui());
    }

    private static float[] c(int argb) {
        return new float[]{((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f,
                ((argb >>> 24) & 255) / 255f * alpha};
    }

    public static void rect(float x0, float y0, float x1, float y1, int argb) {
        if (x1 < x0) { float t = x0; x0 = x1; x1 = t; }
        if (y1 < y0) { float t = y0; y0 = y1; y1 = t; }
        float[] k = c(argb);
        if (k[3] <= 0.003f) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer v = vc();
        v.vertex(m, x0, y0, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x0, y1, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x1, y1, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x1, y0, 0).color(k[0], k[1], k[2], k[3]).endVertex();
    }

    public static void gradV(float x0, float y0, float x1, float y1, int top, int bottom) {
        float[] a = c(top), b = c(bottom);
        Matrix4f m = g.pose().last().pose();
        VertexConsumer v = vc();
        v.vertex(m, x0, y0, 0).color(a[0], a[1], a[2], a[3]).endVertex();
        v.vertex(m, x0, y1, 0).color(b[0], b[1], b[2], b[3]).endVertex();
        v.vertex(m, x1, y1, 0).color(b[0], b[1], b[2], b[3]).endVertex();
        v.vertex(m, x1, y0, 0).color(a[0], a[1], a[2], a[3]).endVertex();
    }

    public static void gradH(float x0, float y0, float x1, float y1, int left, int right) {
        float[] a = c(left), b = c(right);
        Matrix4f m = g.pose().last().pose();
        VertexConsumer v = vc();
        v.vertex(m, x0, y0, 0).color(a[0], a[1], a[2], a[3]).endVertex();
        v.vertex(m, x0, y1, 0).color(a[0], a[1], a[2], a[3]).endVertex();
        v.vertex(m, x1, y1, 0).color(b[0], b[1], b[2], b[3]).endVertex();
        v.vertex(m, x1, y0, 0).color(b[0], b[1], b[2], b[3]).endVertex();
    }

    /** Filled triangle (sent as a degenerate quad). */
    public static void tri(float ax, float ay, float bx, float by, float cx, float cy, int argb) {
        tri(ax, ay, bx, by, cx, cy, argb, argb, argb);
    }

    public static void tri(float ax, float ay, float bx, float by, float cx, float cy, int ca, int cb, int cc) {
        float[] a = c(ca), b = c(cb), d = c(cc);
        Matrix4f m = g.pose().last().pose();
        VertexConsumer v = vc();
        // counter-clockwise on screen to survive culling either way: emit both windings
        v.vertex(m, ax, ay, 0).color(a[0], a[1], a[2], a[3]).endVertex();
        v.vertex(m, bx, by, 0).color(b[0], b[1], b[2], b[3]).endVertex();
        v.vertex(m, cx, cy, 0).color(d[0], d[1], d[2], d[3]).endVertex();
        v.vertex(m, cx, cy, 0).color(d[0], d[1], d[2], d[3]).endVertex();
        v.vertex(m, ax, ay, 0).color(a[0], a[1], a[2], a[3]).endVertex();
        v.vertex(m, cx, cy, 0).color(d[0], d[1], d[2], d[3]).endVertex();
        v.vertex(m, bx, by, 0).color(b[0], b[1], b[2], b[3]).endVertex();
        v.vertex(m, bx, by, 0).color(b[0], b[1], b[2], b[3]).endVertex();
    }

    public static void line(float x0, float y0, float x1, float y1, float w, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4) return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        float[] k = c(argb);
        if (k[3] <= 0.003f) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer v = vc();
        v.vertex(m, x0 + nx, y0 + ny, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x1 + nx, y1 + ny, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x1 - nx, y1 - ny, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x0 - nx, y0 - ny, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        // reverse winding too
        v.vertex(m, x0 - nx, y0 - ny, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x1 - nx, y1 - ny, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x1 + nx, y1 + ny, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, x0 + nx, y0 + ny, 0).color(k[0], k[1], k[2], k[3]).endVertex();
    }

    public static void dashed(float x0, float y0, float x1, float y1, float w, float dash, float offset, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3) return;
        float ux = dx / len, uy = dy / len;
        float start = -(offset % (dash * 2));
        for (float s = start; s < len; s += dash * 2) {
            float a = Math.max(0, s), b = Math.min(len, s + dash);
            if (b > a) line(x0 + ux * a, y0 + uy * a, x0 + ux * b, y0 + uy * b, w, argb);
        }
    }

    public static void frame(float x0, float y0, float x1, float y1, float w, int argb) {
        rect(x0, y0, x1, y0 + w, argb);
        rect(x0, y1 - w, x1, y1, argb);
        rect(x0, y0 + w, x0 + w, y1 - w, argb);
        rect(x1 - w, y0 + w, x1, y1 - w, argb);
    }

    /** Corner brackets — the signature "tech frame". */
    public static void brackets(float x0, float y0, float x1, float y1, float len, float w, int argb) {
        rect(x0, y0, x0 + len, y0 + w, argb); rect(x0, y0, x0 + w, y0 + len, argb);
        rect(x1 - len, y0, x1, y0 + w, argb); rect(x1 - w, y0, x1, y0 + len, argb);
        rect(x0, y1 - w, x0 + len, y1, argb); rect(x0, y1 - len, x0 + w, y1, argb);
        rect(x1 - len, y1 - w, x1, y1, argb); rect(x1 - w, y1 - len, x1, y1, argb);
    }

    /** Panel with dark glass fill, hairline border and corner accents. */
    public static void panel(float x0, float y0, float x1, float y1, int accentRgb, float strength) {
        rect(x0, y0, x1, y1, Theme.a(Theme.SURFACE, 0.82f));
        gradV(x0, y0, x1, y0 + 14, Theme.a(accentRgb, 0.07f * strength), Theme.a(accentRgb, 0f));
        frame(x0, y0, x1, y1, 0.5f, Theme.a(accentRgb, 0.22f * strength));
        brackets(x0 - 0.5f, y0 - 0.5f, x1 + 0.5f, y1 + 0.5f, 5, 1, Theme.a(accentRgb, 0.75f * strength));
    }

    public static void ring(float cx, float cy, float r, float w, int argb, int seg) {
        arc(cx, cy, r, w, 0, (float) (Math.PI * 2), argb, seg);
    }

    public static void arc(float cx, float cy, float r, float w, float a0, float a1, int argb, int seg) {
        float step = (a1 - a0) / seg;
        for (int i = 0; i < seg; i++) {
            float t0 = a0 + step * i, t1 = t0 + step;
            float r0 = r - w / 2, r1 = r + w / 2;
            quadPts(cx + (float) Math.cos(t0) * r0, cy + (float) Math.sin(t0) * r0,
                    cx + (float) Math.cos(t0) * r1, cy + (float) Math.sin(t0) * r1,
                    cx + (float) Math.cos(t1) * r1, cy + (float) Math.sin(t1) * r1,
                    cx + (float) Math.cos(t1) * r0, cy + (float) Math.sin(t1) * r0, argb);
        }
    }

    public static void disc(float cx, float cy, float r, int argb, int seg) {
        for (int i = 0; i < seg; i++) {
            float t0 = (float) (Math.PI * 2 * i / seg), t1 = (float) (Math.PI * 2 * (i + 1) / seg);
            tri(cx, cy, cx + (float) Math.cos(t0) * r, cy + (float) Math.sin(t0) * r,
                    cx + (float) Math.cos(t1) * r, cy + (float) Math.sin(t1) * r, argb);
        }
    }

    /** Soft radial glow: centre colour fading to transparent. */
    public static void glow(float cx, float cy, float r, int centerArgb, int seg) {
        int edge = centerArgb & 0x00FFFFFF;
        for (int i = 0; i < seg; i++) {
            float t0 = (float) (Math.PI * 2 * i / seg), t1 = (float) (Math.PI * 2 * (i + 1) / seg);
            tri(cx, cy, cx + (float) Math.cos(t0) * r, cy + (float) Math.sin(t0) * r,
                    cx + (float) Math.cos(t1) * r, cy + (float) Math.sin(t1) * r, centerArgb, edge, edge);
        }
    }

    public static void ellipseGlow(float cx, float cy, float rx, float ry, int centerArgb, int seg) {
        int edge = centerArgb & 0x00FFFFFF;
        for (int i = 0; i < seg; i++) {
            float t0 = (float) (Math.PI * 2 * i / seg), t1 = (float) (Math.PI * 2 * (i + 1) / seg);
            tri(cx, cy, cx + (float) Math.cos(t0) * rx, cy + (float) Math.sin(t0) * ry,
                    cx + (float) Math.cos(t1) * rx, cy + (float) Math.sin(t1) * ry, centerArgb, edge, edge);
        }
    }

    public static void ellipse(float cx, float cy, float rx, float ry, float w, int argb, int seg) {
        for (int i = 0; i < seg; i++) {
            float t0 = (float) (Math.PI * 2 * i / seg), t1 = (float) (Math.PI * 2 * (i + 1) / seg);
            line(cx + (float) Math.cos(t0) * rx, cy + (float) Math.sin(t0) * ry,
                    cx + (float) Math.cos(t1) * rx, cy + (float) Math.sin(t1) * ry, w, argb);
        }
    }

    private static void quadPts(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int argb) {
        float[] k = c(argb);
        if (k[3] <= 0.003f) return;
        Matrix4f m = g.pose().last().pose();
        VertexConsumer v = vc();
        v.vertex(m, ax, ay, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, bx, by, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, cx, cy, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, dx, dy, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, dx, dy, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, cx, cy, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, bx, by, 0).color(k[0], k[1], k[2], k[3]).endVertex();
        v.vertex(m, ax, ay, 0).color(k[0], k[1], k[2], k[3]).endVertex();
    }

    // ------------------------------------------------------------------ text

    public static int width(String s) { return font.width(s); }

    public static void text(String s, float x, float y, int argb) { text(s, x, y, argb, 1f); }

    public static void text(String s, float x, float y, int argb, float scale) {
        float al = ((argb >>> 24) & 255) / 255f * alpha;
        if (al < 0.03f || s.isEmpty()) return;
        int col = (Math.max(5, (int) (al * 255)) << 24) | (argb & 0xFFFFFF);
        g.flush();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, s, 0, 0, col, false);
        g.pose().popPose();
    }

    public static void textC(String s, float cx, float y, int argb, float scale) {
        text(s, cx - width(s) * scale / 2f, y, argb, scale);
    }

    public static void textR(String s, float rx, float y, int argb, float scale) {
        text(s, rx - width(s) * scale, y, argb, scale);
    }

    /** Text with a soft glow halo. */
    public static void textGlow(String s, float x, float y, int rgb, float scale, float glow) {
        if (glow > 0.01f) {
            int h = Theme.a(rgb, 0.18f * glow);
            float o = 0.6f * scale;
            text(s, x - o, y, h, scale); text(s, x + o, y, h, scale);
            text(s, x, y - o, h, scale); text(s, x, y + o, h, scale);
        }
        text(s, x, y, Theme.a(rgb, 1f), scale);
    }

    /** Reveal a string character by character. */
    public static String typed(String s, float progress) {
        int n = Math.max(0, Math.min(s.length(), (int) (s.length() * progress)));
        return s.substring(0, n);
    }

    /** Scissor in the current (scaled) pose coordinates. */
    public static void scissor(float x0, float y0, float x1, float y1) {
        Matrix4f m = g.pose().last().pose();
        Vector4f a = m.transform(new Vector4f(x0, y0, 0, 1));
        Vector4f b = m.transform(new Vector4f(x1, y1, 0, 1));
        g.flush();
        g.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
    }

    public static void endScissor() {
        g.flush();
        g.disableScissor();
    }
}
