package com.gojo.rework.client;

import com.gojo.rework.client.cam.GDirector;
import com.gojo.rework.common.GBeats;
import com.gojo.rework.common.GMove;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * What Gojo's cutscenes paint over the picture: the DOMAIN / EXPANSION banner with him in the middle panel, the
 * flashes and the rush of speed lines into the void, the words of the incantation, and the title cards.
 */
public final class GBanner {
    private GBanner() {}

    public static void draw(GuiGraphics g, float partial) {
        GCasts.Play p = GDirector.playing();
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        if (p != null) {
            float t = p.time(partial);
            if (p.move() == GMove.IMAGINARY && p.fireAt < 0) {
                if (!GFilm.draw(g, p, partial)) {
                    heaven(g, t, w, h, p.msg.seed());
                }
            }
            bars(g, w, h);
            switch (p.move()) {
                case DOMAIN -> domain(g, p, t, w, h, partial);
                case IMAGINARY -> toji(g, p, t, w, h);
                case PURPLE_200 -> chant(g, p, t, w, h, true);
                case NUKE -> card(g, w, h, t, GBeats.NK_MERGE, "HOLLOW NUKE", "", 0xFFB46BFF);
                default -> {
                }
            }
            if (t > 20 && p.mine) {
                String hint = "HOLD SHIFT TO SKIP";
                g.drawString(mc.font, hint, w - mc.font.width(hint) - 8, h - 12, 0x66FFFFFF, false);
            }
        }
        aim(g, w, h, partial);
        // the 0.2-second domain, for everyone it catches: a fifth of a second of the void
        for (GCasts.Play c : GCasts.all()) {
            if (c.move() == GMove.DOMAIN_02 && mc.player != null) {
                float t = c.time(partial);
                double d = mc.player.position().distanceTo(c.msg.origin());
                if (d < GBeats.D2_RADIUS && t >= GBeats.D2_OPEN && t < GBeats.D2_CLOSE) {
                    speedLines(g, w, h, t, c.msg.seed(), 0.55F);
                }
            }
        }
    }

    static void bars(GuiGraphics g, int w, int h) {
        int bh = (int) (h * 0.09);
        g.fill(0, 0, w, bh, 0xFF000000);
        g.fill(0, h - bh, w, h, 0xFF000000);
    }

    // ------------------------------------------------------------------------------------------------ Unlimited Void

    static void domain(GuiGraphics g, GCasts.Play p, float t, int w, int h, float partial) {
        if (t >= GBeats.DV_BANNER && t < GBeats.DV_FLASH1 + 2) {
            banner(g, p, t - GBeats.DV_BANNER, GBeats.DV_FLASH1 - GBeats.DV_BANNER, w, h);
        }
        if (t >= GBeats.DV_FLASH1 && t < GBeats.DV_FLASH1 + 4) {
            g.fill(0, 0, w, h, (int) (255 * (1 - (t - GBeats.DV_FLASH1) / 4F)) << 24 | 0xFFFFFF);
        }
        if (t >= GBeats.DV_LINES && t < GBeats.DV_FLASH2) {
            speedLines(g, w, h, t, p.msg.seed(), Math.min(1, (t - GBeats.DV_LINES) / 4F));
        }
        if (t >= GBeats.DV_FLASH2 && t < GBeats.DV_FLASH2 + 6) {
            g.fill(0, 0, w, h, (int) (255 * (1 - (t - GBeats.DV_FLASH2) / 6F)) << 24 | 0xFFFFFF);
        }
        if (t >= GBeats.DV_VOID - 4) {
            card(g, w, h, t, GBeats.DV_VOID - 4, "UNLIMITED VOID", "無量空処", 0xFF9FD0FF);
        }
    }

    /**
     * The banner: a slanted panel across the screen, edged in white, a rush of violet light behind him, and the words
     * DOMAIN above and EXPANSION below, punched in from the sides.
     */
    static void banner(GuiGraphics g, GCasts.Play p, float t, float len, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float in = Mth.clamp(t / 4F, 0, 1);
        float out = Mth.clamp((len - t) / 3F, 0, 1);
        float k = Math.min(in, out);
        float slide = (1 - easeOut(in)) * w * 0.6F;
        float zoom = 1 + t * 0.0025F;
        int bandH = (int) (h * 0.52);
        int cx = w / 2, cy = h / 2;
        // the panel, tilted
        g.pose().pushPose();
        g.pose().translate(cx + slide, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-5.5F));
        g.pose().scale(zoom, zoom, 1);
        int W = (int) (w * 0.75);
        int a = (int) (255 * k);
        g.fill(-W, -bandH / 2, W, bandH / 2, a << 24 | 0x0A0420);
        // behind him: light rushing in from a point at the right
        Random r = new Random(p.msg.seed());
        float fx = W * 0.35F, fy = -bandH * 0.05F;
        for (int i = 0; i < 70; i++) {
            float ang = (float) (r.nextDouble() * 360);
            float l0 = (float) (r.nextDouble() * 40) + ((t * 18 + i * 37) % 200);
            float l1 = l0 + 60 + (float) r.nextDouble() * 240;
            int col = i % 5 == 0 ? 0xFFFFFF : i % 2 == 0 ? 0xFF3DD8 : 0xA24BFF;
            float th = 0.6F + (float) r.nextDouble() * 1.8F;
            g.pose().pushPose();
            g.pose().translate(fx, fy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
            g.fill((int) l0, (int) -th, (int) l1, (int) th, (int) (a * (0.35 + 0.5 * r.nextDouble())) << 24 | col);
            g.pose().popPose();
        }
        for (int i = 0; i < 60; i++) {
            int sx = (int) ((r.nextDouble() * 2 - 1) * W), sy = (int) ((r.nextDouble() - 0.5) * bandH);
            g.fill(sx, sy, sx + 1, sy + 1, (int) (a * 0.8) << 24 | 0xFFFFFF);
        }
        g.pose().popPose();
        // him, large in the panel (clipped to its inner band)
        Entity e = p.caster();
        if (e instanceof LivingEntity le && k > 0.05F) {
            // the whole of him, standing in the panel. Drawn as the third-person body (the cutscene camera counts as
            // first person, which would leave only his arms)
            net.minecraft.client.CameraType was = mc.options.getCameraType();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            int scale = (int) (bandH * 0.43);
            int px = (int) (cx - w * 0.04 + slide), py = cy + (int) (bandH * 0.41);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py, scale, px - w * 0.12F, py - scale * 2.2F, le);
            mc.options.setCameraType(was);
        }
        // the white edges of the panel, drawn over him
        g.pose().pushPose();
        g.pose().translate(cx + slide, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-5.5F));
        g.pose().scale(zoom, zoom, 1);
        int edge = Math.max(3, h / 90);
        g.fill(-W, -bandH / 2 - edge, W, -bandH / 2, a << 24 | 0xFFFFFF);
        g.fill(-W, bandH / 2, W, bandH / 2 + edge, a << 24 | 0xFFFFFF);
        g.pose().popPose();
        // the words, punched in from either side
        float ts = Math.max(3F, h / 60F);
        int dx = (int) ((1 - easeOut(in)) * -w * 0.4F);
        bigText(g, font, "DOMAIN", (int) (w * 0.07F) + dx, (int) (cy - bandH * 0.5F - ts * 9 - edge * 2), ts, a);
        int ex = (int) (w * 0.93F - font.width("EXPANSION") * ts) - dx;
        bigText(g, font, "EXPANSION", ex, (int) (cy + bandH * 0.5F + edge * 2 + ts), ts, a);
    }

    /** Thick white letters with a black outline. */
    static void bigText(GuiGraphics g, Font font, String s, int x, int y, float scale, int alpha) {
        if (alpha < 8) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        int black = alpha << 24;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (ox != 0 || oy != 0) {
                    g.drawString(font, s, ox, oy, black, false);
                }
            }
        }
        g.drawString(font, s, 0, 0, alpha << 24 | 0xFFFFFF, false);
        g.pose().popPose();
    }

    /** The rush into the void: the whole picture a storm of violet and pink light streaking past. */
    static void speedLines(GuiGraphics g, int w, int h, float t, long seed, float k) {
        int a = (int) (235 * k);
        g.fill(0, 0, w, h, a << 24 | 0x12041E);
        Random r = new Random(seed ^ 77);
        int cx = w / 2, cy = h / 2;
        for (int i = 0; i < 140; i++) {
            float ang = (float) (r.nextDouble() * 360);
            float speed = 40 + (float) r.nextDouble() * 90;
            float l0 = ((float) r.nextDouble() * w + t * speed) % (w * 0.9F);
            float l1 = l0 + 30 + (float) r.nextDouble() * w * 0.4F;
            float th = 0.5F + (float) r.nextDouble() * 2.2F;
            int col = i % 6 == 0 ? 0xFFFFFF : i % 3 == 0 ? 0xFF7AE6 : i % 2 == 0 ? 0xFF2FCB : 0xB45CFF;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
            g.fill((int) l0, (int) -th, (int) l1, (int) th, (int) (a * (0.4 + 0.6 * r.nextDouble())) << 24 | col);
            g.pose().popPose();
        }
        for (int i = 0; i < 120; i++) {
            int sx = r.nextInt(Math.max(1, w)), sy = r.nextInt(Math.max(1, h));
            g.fill(sx, sy, sx + 1 + r.nextInt(2), sy + 1, (int) (a * r.nextDouble()) << 24 | 0xFFFFFF);
        }
        // a hot white centre the lines pour out of
        for (int i = 6; i > 0; i--) {
            int rr = (int) (i * h * 0.03);
            g.fill(cx - rr, cy - rr / 3, cx + rr, cy + rr / 3, (int) (a * 0.12) << 24 | 0xFFE8FF);
        }
    }

    // ------------------------------------------------------------------------------------------------ aiming

    /** While he holds a finished Purple: a reticle where it will go, and how long he can hold it. */
    static void aim(GuiGraphics g, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        GCasts.Play p = GCasts.of(mc.player);
        if (p == null || !p.mine || p.let || !p.aiming(partial) || GDirector.active()) {
            return;
        }
        float held = p.time(partial) - GBeats.ready(p.move());
        float left = 1 - Mth.clamp(held / GBeats.AIM_MAX, 0, 1);
        int cx = w / 2, cy = h / 2;
        float pulse = 0.5F + 0.5F * (float) Math.sin(p.time(partial) * 0.4);
        int col = p.move() == GMove.NUKE ? 0xD27BFF : 0xC890FF;
        int rr = 14 + (int) (3 * pulse);
        // a ring of ticks round the centre, those still lit showing the time left
        int n = 32;
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2 - Math.PI / 2;
            boolean lit = i / (float) n < left;
            int x = cx + (int) Math.round(Math.cos(a) * rr), y = cy + (int) Math.round(Math.sin(a) * rr);
            g.fill(x - 1, y - 1, x + 1, y + 1, (lit ? 0xE0 : 0x40) << 24 | col);
        }
        g.fill(cx - 1, cy - 6, cx + 1, cy - 2, 0xD0FFFFFF);
        g.fill(cx - 1, cy + 2, cx + 1, cy + 6, 0xD0FFFFFF);
        g.fill(cx - 6, cy - 1, cx - 2, cy + 1, 0xD0FFFFFF);
        g.fill(cx + 2, cy - 1, cx + 6, cy + 1, 0xD0FFFFFF);
        String s = p.move() == GMove.NUKE ? "AIM AT THE GROUND  -  CLICK TO DROP" : "AIM  -  CLICK TO FIRE";
        Font font = mc.font;
        int a = (int) (170 + 85 * pulse);
        g.drawString(font, s, cx - font.width(s) / 2, cy + rr + 10, a << 24 | 0xFFFFFF, true);
    }

    // ------------------------------------------------------------------------------------------------ Toji

    /** The golden heaven he rises into: the picture washed with warm light, rays falling slantwise from above. */
    static void heaven(GuiGraphics g, float t, int w, int h, long seed) {
        float k = Mth.clamp(t / 30F, 0, 1) * (1 - Mth.clamp((t - GBeats.IM_FILM_DROPS[1]) / 8F, 0, 1));
        if (k <= 0.01F) {
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.fillGradient(0, 0, w, h, (int) (k * 120) << 24 | 0xFFC850, (int) (k * 70) << 24 | 0xFF8A1E);
        // rays
        com.mojang.blaze3d.systems.RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        Random r = new Random(seed ^ 0x51);
        for (int i = 0; i < 14; i++) {
            float x0 = (float) (r.nextDouble() * w * 1.4 - w * 0.2);
            float width = (float) (w * (0.03 + r.nextDouble() * 0.08));
            float sway = (float) Math.sin(t * 0.02 + i) * w * 0.02F;
            float a = k * (float) (0.10 + 0.12 * r.nextDouble()) * (0.75F + 0.25F * (float) Math.sin(t * 0.05 + i * 1.7));
            g.pose().pushPose();
            g.pose().translate(x0 + sway, -h * 0.1F, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-18));
            g.fillGradient(0, 0, (int) width, (int) (h * 1.5F), (int) (a * 255) << 24 | 0xFFF0C0, 0x00FFD080);
            g.pose().popPose();
        }
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    }

    /** His words (the player's recording), as subtitles: [start, end) ticks of the cast, line. */
    static final Object[][] TOJI_LINES = {
            {0.0F, 1.95F, "Sorry."},
            {2.2F, 5.45F, "Right now, I'm not even angry at you."},
            {5.85F, 7.4F, "I don't hate anyone."},
            {7.6F, 10.7F, "Right now... this world just feels so good."},
            {10.9F, 13.2F, "Throughout Heaven and Earth, I alone am the honoured one."},
            {13.2F, 16.35F, "The downside: my technique's secrets get out."},
            {16.5F, 20.1F, "You're a Zen'in, aren't you?"},
            {20.3F, 23.6F, "Then you know Limitless well enough."},
            {23.7F, 28.95F, "But only a handful in the Gojo clan know this."},
            {29.05F, 33.5F, "Lapse and reversal - collide each infinity..."},
            {33.7F, 35.45F, "...and push out the imaginary mass it makes."},
    };

    static void toji(GuiGraphics g, GCasts.Play p, float t, int w, int h) {
        Font font = Minecraft.getInstance().font;
        float sec = (t - GBeats.IM_V0) / 20F;
        for (Object[] l : TOJI_LINES) {
            float a0 = (Float) l[0], a1 = (Float) l[1];
            if (sec >= a0 && sec < a1) {
                float a = Math.min(1, (sec - a0) / 0.2F) * Math.min(1, (a1 - sec) / 0.2F);
                String line = (String) l[2];
                int tw = font.width(line);
                int y = h - (int) (h * 0.09) - 22;
                g.fill(w / 2 - tw / 2 - 6, y - 4, w / 2 + tw / 2 + 6, y + 12, (int) (a * 150) << 24);
                g.drawString(font, line, w / 2 - tw / 2, y, (int) (a * 255) << 24 | 0xFFFFFF, false);
            }
        }
        // 天上天下唯我独尊, large, down the left
        if (t >= GBeats.IM_TENJO && t < GBeats.IM_TENJO_END) {
            float a = Math.min(1, (t - GBeats.IM_TENJO) / 5F) * Math.min(1, (GBeats.IM_TENJO_END - t) / 6F);
            String kj = "天上天下唯我独尊";
            float ks = h / 110F;
            g.pose().pushPose();
            g.pose().translate(w * 0.1F, h * 0.5F - kj.length() * ks * 5, 0);
            for (int c = 0; c < kj.length(); c++) {
                g.pose().pushPose();
                g.pose().translate(0, c * ks * 10, 0);
                g.pose().scale(ks, ks, 1);
                String ch = kj.substring(c, c + 1);
                g.drawString(font, ch, 1, 1, (int) (a * 150) << 24, false);
                g.drawString(font, ch, 0, 0, (int) (a * 255) << 24 | 0xFFF4D8, false);
                g.pose().popPose();
            }
            g.pose().popPose();
        }
        card(g, w, h, t, GBeats.IM_MERGE + 4, "IMAGINARY TECHNIQUE: PURPLE", "虚式茈", 0xFFC890FF);
    }

    // ------------------------------------------------------------------------------------------------ the incantation

    static void chant(GuiGraphics g, GCasts.Play p, float t, int w, int h, boolean p200) {
        Font font = Minecraft.getInstance().font;
        int shift = p200 ? GBeats.P2_SHIFT : 0;
        for (int i = 0; i < GBeats.CHANT.length; i++) {
            float start = GBeats.CHANT[i] + shift;
            float end = i + 1 < GBeats.CHANT.length ? GBeats.CHANT[i + 1] + shift : GBeats.P2_V_RED;
            if (t >= start && t < end) {
                float u = t - start;
                float a = Math.min(1, u / 4F) * Math.min(1, (end - t) / 4F);
                // the word, large, in kanji, to the left; the English line as a subtitle
                String kj = GBeats.CHANT_KANJI[i];
                float ks = h / 85F;
                g.pose().pushPose();
                g.pose().translate(w * 0.12F, h * 0.5F - kj.length() * ks * 5, 0);
                for (int c = 0; c < kj.length(); c++) {
                    g.pose().pushPose();
                    g.pose().translate(0, c * ks * 10, 0);
                    g.pose().scale(ks, ks, 1);
                    String ch = kj.substring(c, c + 1);
                    g.drawString(font, ch, 1, 1, (int) (a * 150) << 24, false);
                    g.drawString(font, ch, 0, 0, (int) (a * 255) << 24 | 0xE8F2FF, false);
                    g.pose().popPose();
                }
                g.pose().popPose();
                String line = GBeats.CHANT_LINES[i];
                int tw = font.width(line);
                int y = h - (int) (h * 0.09) - 22;
                g.fill(w / 2 - tw / 2 - 6, y - 4, w / 2 + tw / 2 + 6, y + 12, (int) (a * 160) << 24);
                g.drawString(font, line, w / 2 - tw / 2, y, (int) (a * 255) << 24 | 0xFFFFFF, false);
            }
        }
        int merge = GBeats.P2_MERGE;
        card(g, w, h, t, merge + 2, "200%  HOLLOW PURPLE", "虚式茈", 0xFFC890FF);
        if (p200 && t >= merge + 8 && t < merge + 30) {
            float a = Math.min(1, (t - merge - 8) / 3F) * Math.min(1, (merge + 30 - t) / 4F);
            bigText(g, font, "200%", (int) (w * 0.62F), (int) (h * 0.28F), h / 60F, (int) (a * 255));
        }
    }

    /** A title: kanji large down the right, the name spaced out beneath. */
    static void card(GuiGraphics g, int w, int h, float t, int at, String name, String kanji, int col) {
        float u = t - at;
        if (u < 0 || u > 50) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        float a = Math.min(1, u / 5F) * Math.min(1, (50 - u) / 10F);
        int alpha = (int) (a * 255);
        if (alpha < 8) {
            return;
        }
        float ks = h / 85F;
        StringBuilder sb0 = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb0.append(c).append(' ');
        }
        float x = Math.min(w * 0.84F, w - font.width(sb0.toString().trim()) / 2F - 10);
        float top = h * 0.5F - kanji.length() * ks * 5;
        for (int i = 0; i < kanji.length(); i++) {
            String ch = kanji.substring(i, i + 1);
            g.pose().pushPose();
            g.pose().translate(x - font.width(ch) * ks / 2, top + i * ks * 10, 0);
            g.pose().scale(ks, ks, 1);
            g.drawString(font, ch, 1, 1, (alpha * 3 / 5) << 24, false);
            g.drawString(font, ch, 0, 0, alpha << 24 | (col & 0xFFFFFF), false);
            g.pose().popPose();
        }
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            sb.append(c).append(' ');
        }
        String spaced = sb.toString().trim();
        int y = (int) (top + kanji.length() * ks * 10 + 8);
        if (kanji.isEmpty()) {
            y = (int) (h * 0.3F);
        }
        g.drawString(font, spaced, (int) x - font.width(spaced) / 2, y, alpha << 24 | 0xFFFFFF, true);
    }

    static float easeOut(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }
}
