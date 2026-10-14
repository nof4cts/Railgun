package com.gojo.rework.client;

import com.gojo.rework.Gojo2;
import com.gojo.rework.common.GBeats;
import com.gojo.rework.common.GMove;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;

/**
 * The rendered cutaways of Imaginary Technique: Purple (golden sky, leaves with drops, the blue and red drops running
 * together), each a short film decoded on its own thread a second before it is due and shown full screen in step with
 * the cast's clock.
 */
public final class GFilm {
    public static final int FPS = 30;
    private static final String[] NAMES = {"sky", "leaves", "drops"};
    private static final int[][] WINDOWS = {GBeats.IM_FILM_SKY, GBeats.IM_FILM_LEAVES, GBeats.IM_FILM_DROPS};
    private static final ResourceLocation TEX = Gojo2.id("dynamic/toji_film");

    @Nullable
    private static Decoder[] decoders;
    private static long castStart = Long.MIN_VALUE;
    @Nullable
    private static DynamicTexture texture;
    private static int texW, texH;
    private static int shownShot = -1, shownFrame = -1;

    private GFilm() {}

    /** Every tick of a Toji cast watched in its cutscene: open each film shortly before it plays, close finished ones. */
    public static void tick(@Nullable GCasts.Play p) {
        if (p == null || p.move() != GMove.IMAGINARY) {
            stop();
            return;
        }
        if (castStart != p.msg.start()) {
            stop();
            castStart = p.msg.start();
            decoders = new Decoder[NAMES.length];
        }
        float t = p.time(0);
        for (int i = 0; i < NAMES.length; i++) {
            int[] w = WINDOWS[i];
            if (decoders[i] == null && t >= w[0] - 30 && t < w[1]) {
                decoders[i] = new Decoder(NAMES[i]);
                decoders[i].start();
            }
            if (decoders[i] != null && t > w[1] + 2) {
                decoders[i].halt();
            }
        }
    }

    public static void stop() {
        if (decoders != null) {
            for (Decoder d : decoders) {
                if (d != null) {
                    d.halt();
                }
            }
        }
        decoders = null;
        castStart = Long.MIN_VALUE;
        shownShot = -1;
    }

    /** Which film is up at tick t of the cast, or -1. */
    public static int showing(float t) {
        for (int i = 0; i < WINDOWS.length; i++) {
            if (t >= WINDOWS[i][0] && t < WINDOWS[i][1]) {
                return i;
            }
        }
        return -1;
    }

    /** Draws the film that is up over the whole screen; false if none is. */
    public static boolean draw(GuiGraphics g, GCasts.Play p, float partial) {
        if (decoders == null || p.move() != GMove.IMAGINARY) {
            return false;
        }
        float t = p.time(partial);
        int i = showing(t);
        if (i < 0 || decoders[i] == null) {
            return false;
        }
        Decoder d = decoders[i];
        int want = Math.max(0, (int) ((t - WINDOWS[i][0]) / 20F * FPS));
        int[] next = null;
        int nw = 0, nh = 0;
        while (true) {
            Decoder.Frame f = d.out.peek();
            if (f == null || f.index > want) {
                break;
            }
            d.out.poll();
            next = f.rgba;
            nw = f.w;
            nh = f.h;
            shownFrame = f.index;
            if (f.index == want) {
                break;
            }
        }
        if (next != null) {
            if (texture == null || texW != nw || texH != nh) {
                if (texture != null) {
                    texture.close();
                }
                texture = new DynamicTexture(nw, nh, false);
                texW = nw;
                texH = nh;
                Minecraft.getInstance().getTextureManager().register(TEX, texture);
            }
            NativeImage img = texture.getPixels();
            if (img != null) {
                int k = 0;
                for (int y = 0; y < nh; y++) {
                    for (int x = 0; x < nw; x++) {
                        img.setPixelRGBA(x, y, next[k++]);
                    }
                }
                texture.upload();
            }
            shownShot = i;
        }
        int sw = g.guiWidth(), sh = g.guiHeight();
        if (shownShot != i || texture == null) {
            // not decoded yet: hold on gold rather than show the world
            g.fill(0, 0, sw, sh, 0xFFE0A040);
            return true;
        }
        // fill the screen (cropping the sides of a wide screen's top and bottom if need be)
        float scale = Math.max(sw / (float) texW, sh / (float) texH);
        int dw = Math.round(texW * scale), dh = Math.round(texH * scale);
        int x = (sw - dw) / 2, y = (sh - dh) / 2;
        RenderSystem.enableBlend();
        g.blit(TEX, x, y, dw, dh, 0.0F, 0.0F, texW, texH, texW, texH);
        // a quick dip in and out of each cutaway
        float in = (t - WINDOWS[i][0]) / 3F, out = (WINDOWS[i][1] - t) / 3F;
        float fade = Math.max(0, 1 - Math.min(in, out));
        if (fade > 0.01F) {
            g.fill(0, 0, sw, sh, (int) (fade * 255) << 24 | 0xFFF2D0);
        }
        return true;
    }

    private static final class Decoder extends Thread {
        record Frame(int index, int w, int h, int[] rgba) {}

        final BlockingQueue<Frame> out = new ArrayBlockingQueue<>(8);
        private final String name;
        private volatile boolean running = true;

        Decoder(String name) {
            super("Gojo film " + name);
            this.name = name;
            setDaemon(true);
        }

        void halt() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            ResourceLocation loc = Gojo2.id("film/toji_" + name + ".mp4");
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(loc);
                if (res.isEmpty()) {
                    Gojo2.LOGGER.warn("[gojo] film missing: {}", loc);
                    return;
                }
                byte[] bytes;
                try (InputStream in = res.get().open()) {
                    bytes = in.readAllBytes();
                }
                FrameGrab grab = FrameGrab.createFrameGrab(ByteBufferSeekableByteChannel.readFromByteBuffer(ByteBuffer.wrap(bytes)));
                int index = 0;
                while (running) {
                    Picture p = grab.getNativeFrame();
                    if (p == null) {
                        break;
                    }
                    Frame f = new Frame(index++, p.getWidth(), p.getHeight(), toRgba(p));
                    while (running && !out.offer(f, 50, TimeUnit.MILLISECONDS)) {
                        // wait for the screen
                    }
                }
            } catch (InterruptedException ignored) {
            } catch (Throwable t) {
                Gojo2.LOGGER.error("[gojo] film {} failed", name, t);
            }
        }

        /** YUV 4:2:0 to Minecraft's ABGR. */
        private static int[] toRgba(Picture p) {
            int w = p.getWidth(), h = p.getHeight();
            byte[] yp = p.getPlaneData(0), up = p.getPlaneData(1), vp = p.getPlaneData(2);
            int yw = p.getPlaneWidth(0), cw = p.getPlaneWidth(1);
            int[] out = new int[w * h];
            int i = 0;
            boolean full = p.getColor() == org.jcodec.common.model.ColorSpace.YUV420J;
            int ys = full ? 256 : 298, yo = full ? 0 : 16;
            for (int y = 0; y < h; y++) {
                int yr = y * yw, cr = (y >> 1) * cw;
                for (int x = 0; x < w; x++) {
                    int Y = yp[yr + x] + 128, U = up[cr + (x >> 1)], V = vp[cr + (x >> 1)];
                    int c = (Y - yo) * ys;
                    int r = full ? (c + 359 * V + 128) >> 8 : (c + 409 * V + 128) >> 8;
                    int gg = full ? (c - 88 * U - 183 * V + 128) >> 8 : (c - 100 * U - 208 * V + 128) >> 8;
                    int b = full ? (c + 454 * U + 128) >> 8 : (c + 516 * U + 128) >> 8;
                    r = r < 0 ? 0 : (r > 255 ? 255 : r);
                    gg = gg < 0 ? 0 : (gg > 255 ? 255 : gg);
                    b = b < 0 ? 0 : (b > 255 ? 255 : b);
                    out[i++] = 0xFF000000 | (b << 16) | (gg << 8) | r;
                }
            }
            return out;
        }
    }
}
