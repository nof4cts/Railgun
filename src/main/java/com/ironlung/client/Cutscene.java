package com.ironlung.client;

import com.ironlung.IronLungMod;
import com.ironlung.feed.SurfaceScene;
import com.ironlung.registry.ModSounds;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The opening: rendered shots of the rig, the blood moon, the pilot boarding and the boat going under.
 * Shots are rendered in the background; playback waits for each one, so nothing pops in half-made.
 */
public final class Cutscene {
    private Cutscene() {}

    public static final int W = 560, H = 315;
    private static final int SHOT_TICKS = 84, FADE = 16, TAIL = 30;

    public static boolean active;
    private static int tick;
    private static int shown; // index of the shot currently on screen
    private static int shotStart;
    private static final int[][] BUFFERS = new int[SurfaceScene.SHOTS][];
    private static final ResourceLocation[] TEX = new ResourceLocation[SurfaceScene.SHOTS];
    private static SoundInstance ambience;
    private static boolean ending;
    private static int endTick;

    public static void start() {
        release();
        active = true;
        tick = 0;
        shown = -1;
        shotStart = 0;
        ending = false;
        final long seed = System.nanoTime();
        FeedSystem.submit(() -> {
            for (int i = 0; i < SurfaceScene.SHOTS; i++) {
                try {
                    int[] out = new int[W * H];
                    SurfaceScene.render(out, W, H, i, seed + i);
                    synchronized (BUFFERS) {
                        BUFFERS[i] = out;
                    }
                } catch (Throwable t) {
                    IronLungMod.LOGGER.error("Opening shot {} failed", i, t);
                }
            }
        });
        ambience = SimpleSoundInstance.forUI(ModSounds.OPENING_AMBIENCE.get(), 1.0f, 0.9f);
        Minecraft.getInstance().getSoundManager().play(ambience);
    }

    private static boolean ready(int i) {
        synchronized (BUFFERS) {
            return BUFFERS[i] != null || TEX[i] != null;
        }
    }

    private static ResourceLocation texture(int i) {
        if (TEX[i] != null) return TEX[i];
        int[] buf;
        synchronized (BUFFERS) {
            buf = BUFFERS[i];
            BUFFERS[i] = null;
        }
        if (buf == null) return null;
        DynamicTexture tex = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, W, H, true));
        NativeImage img = tex.getPixels();
        if (img != null) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int c = buf[y * W + x];
                    img.setPixelRGBA(x, y, 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF));
                }
            }
            tex.upload();
        }
        TEX[i] = Minecraft.getInstance().getTextureManager().register("ironlung_opening_" + i, tex);
        return TEX[i];
    }

    private static void sound(SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    public static void tick() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) && tick > 10) {
            finish();
            return;
        }
        tick++;
        if (ending) {
            if (tick - endTick > TAIL) finish();
            return;
        }
        int next = shown + 1;
        boolean due = shown < 0 || tick - shotStart >= SHOT_TICKS;
        if (due) {
            if (next >= SurfaceScene.SHOTS) {
                ending = true;
                endTick = tick;
                sound(ModSounds.HULL_GROAN.get(), 0.6f, 0.8f);
                return;
            }
            if (ready(next)) {
                shown = next;
                shotStart = tick;
                cue(shown);
            }
        }
        // mid-shot cues
        int age = tick - shotStart;
        if (shown == 3 && age == 58) sound(ModSounds.BREAKER_CLUNK.get(), 0.55f, 1.0f); // the lid slams
        if (shown == 4 && age == 30) sound(ModSounds.SUBMERGE.get(), 1.0f, 1.0f);
    }

    private static void cue(int shot) {
        switch (shot) {
            case 1 -> sound(ModSounds.CHAINS.get(), 1.0f, 0.8f);
            case 2 -> sound(ModSounds.HULL_CREAK.get(), 0.8f, 0.5f);
            case 3 -> sound(ModSounds.CHAINS.get(), 0.85f, 0.5f);
            case 4 -> {
                sound(ModSounds.CHAINS.get(), 0.7f, 1.0f);
                sound(ModSounds.HULL_GROAN.get(), 0.75f, 0.7f);
            }
            default -> {}
        }
    }

    private static void finish() {
        active = false;
        if (ambience != null) Minecraft.getInstance().getSoundManager().stop(ambience);
        ambience = null;
        ClientMissionState.introTicks = 160;
        release();
    }

    public static void stop() {
        if (active) {
            active = false;
            if (ambience != null) Minecraft.getInstance().getSoundManager().stop(ambience);
            ambience = null;
        }
        release();
    }

    private static void release() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < TEX.length; i++) {
            if (TEX[i] != null) mc.getTextureManager().release(TEX[i]);
            TEX[i] = null;
        }
        synchronized (BUFFERS) {
            for (int i = 0; i < BUFFERS.length; i++) BUFFERS[i] = null;
        }
    }

    public static void render(GuiGraphics g, int w, int h, float partialTick) {
        g.fill(0, 0, w, h, 0xFF000000);
        if (shown >= 0 && !ending) {
            float age = tick - shotStart + partialTick;
            drawShot(g, w, h, shown, age, fadeIn(shown, age));
        } else if (ending) {
            float a = 1f - Math.min(1f, (tick - endTick) / (float) FADE);
            if (a > 0 && shown >= 0) drawShot(g, w, h, shown, SHOT_TICKS + (tick - endTick), a);
        }
        // letterbox
        int bar = (int) (h * 0.11);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (tick > 40 && tick < 160) {
            String skip = "SPACE  skip";
            g.drawString(Minecraft.getInstance().font, skip, w - Minecraft.getInstance().font.width(skip) - 8, h - bar / 2 - 4, 0x60907060, false);
        }
    }

    private static float fadeIn(int shot, float age) {
        float in = Math.min(1f, age / FADE);
        return shot == 0 ? Math.min(1f, age / (FADE * 2f)) : in;
    }

    private static void drawShot(GuiGraphics g, int w, int h, int shot, float age, float alpha) {
        ResourceLocation tex = texture(shot);
        if (tex == null) return;
        // a slow push-in and drift, different per shot
        float t = Math.min(1f, age / SHOT_TICKS);
        double zoom = 1.04 + 0.07 * t;
        double driftX = (shot % 2 == 0 ? -1 : 1) * 0.025 * t;
        int dw = (int) (Math.max(w, h * W / (double) H) * zoom), dh = dw * H / W;
        int x = (w - dw) / 2 + (int) (driftX * w), y = (h - dh) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        g.blit(tex, x, y, dw, dh, 0, 0, W, H, W, H);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
