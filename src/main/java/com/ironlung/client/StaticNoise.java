package com.ironlung.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ThreadLocalRandom;

/** A small texture of television snow, refreshed a few times a second. */
public final class StaticNoise {
    private StaticNoise() {}

    private static final int W = 128, H = 72;
    private static DynamicTexture tex;
    private static ResourceLocation loc;
    private static long lastUpdate;

    private static void refresh() {
        if (tex == null) {
            tex = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, W, H, true));
            loc = Minecraft.getInstance().getTextureManager().register("ironlung_static", tex);
        }
        long now = System.nanoTime();
        if (now - lastUpdate < 45_000_000L) return;
        lastUpdate = now;
        NativeImage img = tex.getPixels();
        if (img == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int y = 0; y < H; y++) {
            int rowBias = r.nextInt(100) < 6 ? 60 : 0;
            for (int x = 0; x < W; x++) {
                int v = Math.min(255, r.nextInt(200) + rowBias);
                int red = Math.min(255, v + 20);
                img.setPixelRGBA(x, y, (255 << 24) | ((v * 8 / 10) << 16) | ((v * 85 / 100) << 8) | red);
            }
        }
        tex.upload();
    }

    /** Draw snow over a rectangle with the given opacity. */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        if (alpha <= 0.005f) return;
        refresh();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, Math.min(1f, alpha));
        g.blit(loc, x, y, w, h, 0, 0, W, H, W, H);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
