package com.ironlung.client;

import com.ironlung.IronLungMod;
import com.ironlung.config.IronLungConfig;
import com.ironlung.feed.FeedRenderer;
import com.ironlung.mission.PhotoRecord;
import com.ironlung.ocean.OceanMap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the camera renderer on a background thread and moves finished frames into GPU textures.
 * The live feed is deliberately low-resolution and low-framerate.
 */
public final class FeedSystem {
    private FeedSystem() {}

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "IronLung-Camera");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static FeedRenderer renderer;

    // live feed
    private static volatile boolean liveBusy;
    private static volatile int[] liveResult;
    private static volatile int liveResultW;
    private static long lastLiveNanos;
    private static long frameCounter;
    private static DynamicTexture liveTex;
    private static ResourceLocation liveLoc;
    private static int liveW, liveH;

    // photographs
    private static final Map<Integer, int[]> PHOTO_BUFFERS = new ConcurrentHashMap<>();
    private static final Set<Integer> PHOTO_PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, ResourceLocation> PHOTO_TEX = new HashMap<>();
    private static final Map<Integer, int[]> PHOTO_SIZE = new HashMap<>();

    public static void reset(OceanMap map) {
        release();
        renderer = new FeedRenderer(map);
    }

    public static void release() {
        Minecraft mc = Minecraft.getInstance();
        if (liveLoc != null) {
            mc.getTextureManager().release(liveLoc);
            liveLoc = null;
            liveTex = null;
        }
        for (ResourceLocation loc : PHOTO_TEX.values()) mc.getTextureManager().release(loc);
        PHOTO_TEX.clear();
        PHOTO_SIZE.clear();
        PHOTO_BUFFERS.clear();
        PHOTO_PENDING.clear();
        liveResult = null;
        renderer = null;
    }

    public static int liveWidth() {
        return liveW;
    }

    public static int liveHeight() {
        return liveH;
    }

    /** Call every frame while the camera screen is visible. Returns the texture to draw, or null. */
    public static ResourceLocation live(double glitch, double grain, double light) {
        int w = IronLungConfig.FEED_WIDTH.get();
        int h = w * 3 / 4;
        if (liveTex == null || liveW != w || liveH != h) {
            Minecraft mc = Minecraft.getInstance();
            if (liveLoc != null) mc.getTextureManager().release(liveLoc);
            liveTex = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, w, h, true));
            liveLoc = mc.getTextureManager().register("ironlung_feed", liveTex);
            liveW = w;
            liveH = h;
        }
        int[] res = liveResult;
        if (res != null && liveResultW == w && res.length == w * h) {
            upload(liveTex, res, w, h);
            liveResult = null;
        }
        final FeedRenderer r = renderer;
        long now = System.nanoTime();
        long interval = 1_000_000_000L / Math.max(1, IronLungConfig.FEED_FPS.get());
        if (r != null && !liveBusy && now - lastLiveNanos >= interval) {
            lastLiveNanos = now;
            liveBusy = true;
            final double[] pose = ClientMissionState.cameraPose();
            final FeedRenderer.Params p = new FeedRenderer.Params();
            p.lightRange = 26;
            p.grain = grain;
            p.glitch = glitch;
            p.light = light;
            p.frame = frameCounter++;
            p.time = ClientMissionState.now();
            p.transients.addAll(ClientMissionState.transients);
            WORKER.submit(() -> {
                try {
                    int[] out = new int[w * h];
                    r.render(out, w, h, pose[0], pose[1], pose[2], pose[3], pose[4], p);
                    liveResultW = w;
                    liveResult = out;
                } catch (Throwable t) {
                    IronLungMod.LOGGER.error("Camera feed render failed", t);
                } finally {
                    liveBusy = false;
                }
            });
        }
        return liveLoc;
    }

    /** Develop a photograph in the background. Current transients are captured so fleeting things stay in the picture. */
    public static void requestPhoto(PhotoRecord rec) {
        final FeedRenderer r = renderer;
        if (r == null || PHOTO_TEX.containsKey(rec.id) || !PHOTO_PENDING.add(rec.id)) return;
        final int w = IronLungConfig.PHOTO_WIDTH.get();
        final int h = w * 3 / 4;
        final FeedRenderer.Params p = new FeedRenderer.Params();
        p.photo = true;
        p.lightRange = 40;
        p.grain = 0.12;
        p.frame = rec.frameSeed;
        p.time = ClientMissionState.now();
        List<FeedRenderer.Transient> trans = new ArrayList<>(ClientMissionState.transients);
        if (rec.has(PhotoRecord.FLAG_RUINED)) {
            p.glitch = 1.0;
            p.light = 0.35;
            p.grain = 0.9;
        }
        final OceanMap map = ClientMissionState.map;
        WORKER.submit(() -> {
            try {
                if (rec.has(PhotoRecord.FLAG_ANOMALY) && map != null) {
                    Random rnd = new Random(rec.frameSeed);
                    double fx = Math.sin(rec.yaw), fz = -Math.cos(rec.yaw);
                    double rx = Math.cos(rec.yaw), rz = Math.sin(rec.yaw);
                    double dist = 17 + rnd.nextDouble() * 7;
                    double off = (rnd.nextDouble() - 0.5) * 8;
                    double px = rec.x + fx * dist + rx * off, pz = rec.z + fz * dist + rz * off;
                    trans.add(new FeedRenderer.Transient(FeedRenderer.Transient.FIGURE, px, map.floorY(px, pz) - 0.3, pz,
                            0, 0, 0, 0.95 + rnd.nextDouble() * 0.35, 0, p.time - 1, 1e9));
                }
                p.transients.addAll(trans);
                int[] out = new int[w * h];
                r.render(out, w, h, rec.x, rec.y, rec.z, rec.yaw, rec.pitch, p);
                PHOTO_BUFFERS.put(rec.id, out);
            } catch (Throwable t) {
                IronLungMod.LOGGER.error("Photo development failed", t);
                PHOTO_PENDING.remove(rec.id);
            }
        });
        PHOTO_SIZE.put(rec.id, new int[]{w, h});
    }

    /** Texture for a developed photo, or null while it is still developing. */
    public static ResourceLocation photo(PhotoRecord rec) {
        ResourceLocation loc = PHOTO_TEX.get(rec.id);
        if (loc != null) return loc;
        int[] buf = PHOTO_BUFFERS.remove(rec.id);
        if (buf == null) {
            requestPhoto(rec);
            return null;
        }
        int[] size = PHOTO_SIZE.get(rec.id);
        if (size == null || size[0] * size[1] != buf.length) {
            PHOTO_PENDING.remove(rec.id);
            return null;
        }
        DynamicTexture tex = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, size[0], size[1], true));
        upload(tex, buf, size[0], size[1]);
        loc = Minecraft.getInstance().getTextureManager().register("ironlung_photo_" + rec.id, tex);
        PHOTO_TEX.put(rec.id, loc);
        PHOTO_PENDING.remove(rec.id);
        return loc;
    }

    public static int[] photoSize(PhotoRecord rec) {
        int[] s = PHOTO_SIZE.get(rec.id);
        return s == null ? new int[]{320, 240} : s;
    }

    private static void upload(DynamicTexture tex, int[] argb, int w, int h) {
        NativeImage img = tex.getPixels();
        if (img == null) return;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                int a = (c >>> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        tex.upload();
    }
}
