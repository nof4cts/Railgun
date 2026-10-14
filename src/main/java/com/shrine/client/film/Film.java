package com.shrine.client.film;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.shrine.network.S2CCast;
import com.shrine.registry.ModSounds;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;

/**
 * Open's cutscene: the author's Blender film (rendered from fire_arrow_open.blend at 1280x720, 30 fps), decoded on a
 * worker thread with JCodec and shown full screen, kept in step with the game clock, over its own soundtrack. The blast
 * lands in the world the tick it ends.
 */
public final class Film {
    public static final ResourceLocation VIDEO = new ResourceLocation("shrine", "film/open.mp4");
    public static final int FPS = 30;
    public static final int W = 1280, H = 720;
    private static final ResourceLocation TEX = new ResourceLocation("shrine", "dynamic/open_film");

    @Nullable
    private static S2CCast cast;
    @Nullable
    private static Decoder decoder;
    @Nullable
    private static DynamicTexture texture;
    private static int shown = -1;
    @Nullable
    private static SoundInstance music;
    /** Frames: the length of the film, set once it is opened. */
    private static int frames = (int) Math.ceil(com.shrine.common.Timing.FUGA_FILM / 20.0 * FPS);

    private Film() {}

    public static boolean playing() {
        return cast != null;
    }

    public static void start(S2CCast msg) {
        stop();
        cast = msg;
        shown = -1;
        decoder = new Decoder();
        decoder.start();
        Minecraft mc = Minecraft.getInstance();
        mc.getMusicManager().stopPlaying();
        music = new SimpleSoundInstance(ModSounds.FUGA_FILM.get().getLocation(), SoundSource.MASTER, 1.0F, 1.0F, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        mc.getSoundManager().play(music);
    }

    public static void stop() {
        cast = null;
        if (decoder != null) {
            decoder.halt();
            decoder = null;
        }
        if (music != null) {
            Minecraft.getInstance().getSoundManager().stop(music);
            music = null;
        }
    }

    /** Seconds into the film. */
    public static float seconds(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (cast == null || mc.level == null) {
            return 0.0F;
        }
        return ((float) (mc.level.getGameTime() - cast.startTick) + partial) / 20.0F;
    }

    /** Draws the current frame over everything. Returns false when no film is playing. */
    public static boolean draw(GuiGraphics g, float partial) {
        if (cast == null) {
            return false;
        }
        float sec = seconds(partial);
        if (sec * FPS >= frames + 1) {
            // the film is over: the cutscene carries on into the blast in the world
            stop();
            return false;
        }
        int want = Math.max(0, (int) (sec * FPS));
        int[] next = null;
        Decoder d = decoder;
        if (d != null) {
            // drop frames that are already late; never wait on the decoder
            while (true) {
                Decoder.Frame f = d.out.peek();
                if (f == null || f.index > want) {
                    break;
                }
                d.out.poll();
                next = f.rgba;
                shown = f.index;
                if (f.index == want) {
                    break;
                }
            }
        }
        if (texture == null) {
            texture = new DynamicTexture(W, H, false);
            Minecraft.getInstance().getTextureManager().register(TEX, texture);
        }
        if (next != null) {
            NativeImage img = texture.getPixels();
            if (img != null) {
                int i = 0;
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        img.setPixelRGBA(x, y, next[i++]);
                    }
                }
                texture.upload();
            }
        }
        int sw = g.guiWidth(), sh = g.guiHeight();
        g.fill(0, 0, sw, sh, 0xFF000000);
        if (shown >= 0) {
            // fit 16:9 inside the screen
            float scale = Math.min(sw / (float) W, sh / (float) H);
            int dw = Math.round(W * scale), dh = Math.round(H * scale);
            int x = (sw - dw) / 2, y = (sh - dh) / 2;
            RenderSystem.enableBlend();
            g.blit(TEX, x, y, dw, dh, 0.0F, 0.0F, W, H, W, H);
        }
        // the film's dialogue, as subtitles under the picture
        String line = subtitle(sec);
        if (line != null) {
            var font = Minecraft.getInstance().font;
            int tw = font.width(line);
            int ty = sh - Math.max(26, (sh - Math.round(H * Math.min(sw / (float) W, sh / (float) H))) / 2 + 22);
            g.fill(sw / 2 - tw / 2 - 6, ty - 4, sw / 2 + tw / 2 + 6, ty + 12, 0xA0000000);
            g.drawString(font, line, sw / 2 - tw / 2, ty, 0xFFFFFFFF, false);
        }
        if (sec > 1.0F) {
            String hint = "HOLD SHIFT TO SKIP";
            g.drawString(Minecraft.getInstance().font, hint, sw - Minecraft.getInstance().font.width(hint) - 8, sh - 12, 0x66FFFFFF, false);
        }
        return true;
    }

    /** The film's lines, by shot (seconds into the film). */
    private static final Object[][] LINES = {
            {2.3F, 3.6F, "Fuga (Open)"},
            {5.3F, 7.1F, "Those are..."},
            {8.8F, 11.9F, "Fire aspect?!"},
            {12.2F, 16.1F, "I thought your enchantments were Dismantle and Cleave..."},
            {16.3F, 19.6F, "Arm yourself!"},
            {19.9F, 22.9F, "Let's have a contest of firepower."},
    };

    @Nullable
    private static String subtitle(float sec) {
        for (Object[] l : LINES) {
            if (sec >= (Float) l[0] && sec < (Float) l[1]) {
                return (String) l[2];
            }
        }
        return null;
    }

    /** Decodes the film ahead of the clock on its own thread. */
    private static final class Decoder extends Thread {
        record Frame(int index, int[] rgba) {}

        final BlockingQueue<Frame> out = new ArrayBlockingQueue<>(10);
        private volatile boolean running = true;

        Decoder() {
            super("Open film decoder");
            setDaemon(true);
        }

        void halt() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            try {
                byte[] bytes;
                var res = Minecraft.getInstance().getResourceManager().getResource(VIDEO);
                if (res.isEmpty()) {
                    com.shrine.ShrineMod.LOGGER.warn("[open] film missing: {}", VIDEO);
                    return;
                }
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
                    int[] rgba = toRgba(p);
                    Frame f = new Frame(index++, rgba);
                    while (running && !out.offer(f, 50, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        // wait for the screen to catch up
                    }
                }
                frames = Math.max(1, index);
            } catch (InterruptedException ignored) {
            } catch (Throwable t) {
                com.shrine.ShrineMod.LOGGER.error("[open] film decoding failed", t);
            }
        }

        /** YUV 4:2:0 (BT.601, limited range, as the film is encoded) to Minecraft's ABGR. */
        private static int[] toRgba(Picture p) {
            byte[] yp = p.getPlaneData(0), up = p.getPlaneData(1), vp = p.getPlaneData(2);
            int yw = p.getPlaneWidth(0), cw = p.getPlaneWidth(1);
            int[] out = new int[W * H];
            int i = 0;
            // JCodec hands H.264 out as full-range "J" YUV; anything else is treated as studio range
            boolean full = p.getColor() == org.jcodec.common.model.ColorSpace.YUV420J;
            int ys = full ? 256 : 298, yo = full ? 0 : 16;
            for (int y = 0; y < H; y++) {
                int yr = y * yw, cr = (y >> 1) * cw;
                for (int x = 0; x < W; x++) {
                    // JCodec keeps samples as signed bytes offset by -128
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
