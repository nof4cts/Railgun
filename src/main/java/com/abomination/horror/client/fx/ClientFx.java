package com.abomination.horror.client.fx;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.config.AbomConfig;
import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.registry.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Proximity dread and screen effects: sub-bass pulses that quicken as it closes in, your heartbeat,
 * escalating music, camera shake, chromatic fringing, light flicker, and flashes.
 */
public final class ClientFx {
    public static final String OVERLAY_ID = "horror_fx";
    private static final ResourceLocation VIGNETTE = AbominationMod.id("textures/misc/vignette.png");
    private static final ResourceLocation NOISE = AbominationMod.id("textures/misc/noise.png");
    private static final RandomSource RANDOM = RandomSource.create();

    private static float shake, aberration, flash, ambientAberration, dread;
    private static int flicker;
    private static boolean flickerOn;
    private static int bassTimer, heartTimer, musicRetry;
    private static SoundInstance music;
    private static SoundEvent musicEvent;

    private ClientFx() {}

    public static void apply(float s, float ab, float fl, int flick) {
        shake = Math.max(shake, s);
        aberration = Math.max(aberration, ab);
        flash = Math.max(flash, fl);
        flicker = Math.max(flicker, flick);
    }

    public static void reset() {
        shake = aberration = flash = ambientAberration = dread = 0.0F;
        flicker = 0;
        stopMusic();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        shake *= 0.85F;
        aberration *= 0.9F;
        flash *= 0.7F;
        if (flicker > 0) flicker--;
        flickerOn = flicker > 0 && RANDOM.nextFloat() < 0.35F;

        AbominationEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (AbominationEntity a : mc.level.getEntitiesOfClass(AbominationEntity.class, mc.player.getBoundingBox().inflate(48.0D))) {
            if (!a.isAlive() || a.deathTime > 0) continue;
            double d = a.distanceTo(mc.player);
            if (d < best) {
                best = d;
                nearest = a;
            }
        }
        dread = 0.0F;
        ambientAberration = 0.0F;
        if (nearest != null) {
            int phase = nearest.getPhase();
            if (phase >= AbominationEntity.PHASE_STALK || nearest.isAwakened()) {
                if (--bassTimer <= 0) {
                    bassTimer = (int) Mth.clamp(best * 3.0D, 14.0D, 80.0D);
                    mc.level.playLocalSound(nearest.getX(), nearest.getY() + 1.5D, nearest.getZ(), ModSounds.SUB_BASS.get(), SoundSource.HOSTILE,
                            (float) Mth.clamp(1.6D - best / 20.0D, 0.2D, 1.6D), 1.0F, false);
                }
                if (best < 12.0D && --heartTimer <= 0) {
                    heartTimer = (int) Mth.clamp(best * 2.2D, 9.0D, 30.0D);
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 1.0F, 0.45F));
                }
            }
            if (phase == AbominationEntity.PHASE_NIGHTMARE && best < 24.0D) {
                ambientAberration = 0.12F + 0.08F * Mth.sin(nearest.tickCount * 0.1F);
                if (RANDOM.nextInt(40) == 0) flicker = Math.max(flicker, 6 + RANDOM.nextInt(10));
            }
            dread = (float) Mth.clamp(1.0D - best / 24.0D, 0.0D, 1.0D) * (phase >= AbominationEntity.PHASE_HUNT ? 0.5F : 0.3F);
        }
        tickMusic(mc, nearest, best);
    }

    private static void tickMusic(Minecraft mc, AbominationEntity nearest, double d) {
        SoundEvent want = null;
        if (AbomConfig.CLIENT.dynamicMusic.get() && nearest != null && !ClientFinisher.localActive()) {
            int phase = nearest.getPhase();
            if (phase == AbominationEntity.PHASE_NIGHTMARE || (nearest.isAwakened() && nearest.getBossStage() >= 2)) want = ModSounds.MUSIC_NIGHTMARE.get();
            else if (phase == AbominationEntity.PHASE_HUNT || nearest.isAwakened()) want = ModSounds.MUSIC_HUNT.get();
            else if (phase == AbominationEntity.PHASE_STALK && d < 40.0D) want = ModSounds.MUSIC_STALK.get();
        }
        if (musicRetry > 0) musicRetry--;
        boolean ended = music != null && !mc.getSoundManager().isActive(music);
        if (want != musicEvent || (ended && musicRetry == 0)) {
            stopMusic();
            musicEvent = want;
            if (want != null) {
                music = SimpleSoundInstance.forMusic(want);
                mc.getSoundManager().play(music);
                musicRetry = 40;
            }
        }
        if (music != null) mc.getMusicManager().stopPlaying();
    }

    private static void stopMusic() {
        if (music != null) Minecraft.getInstance().getSoundManager().stop(music);
        music = null;
        musicEvent = null;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!AbomConfig.CLIENT.cameraShake.get()) return;
        float s = Math.max(shake, ClientFinisher.shakeAmount((float) e.getPartialTick()));
        if (s < 0.001F) return;
        e.setYaw(e.getYaw() + (RANDOM.nextFloat() - 0.5F) * s * 3.0F);
        e.setPitch(e.getPitch() + (RANDOM.nextFloat() - 0.5F) * s * 3.0F);
        e.setRoll(e.getRoll() + (RANDOM.nextFloat() - 0.5F) * s * 5.0F);
    }

    // =============================================================================== overlay
    public static void renderOverlay(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean fx = AbomConfig.CLIENT.screenEffects.get();
        if (ClientFinisher.renderOverlay(g, w, h, partial, fx)) return;
        if (!fx) return;
        if (dread > 0.01F) vignette(g, w, h, 0.05F, 0.0F, 0.0F, dread, 0);
        float ab = Math.max(aberration, ambientAberration);
        if (ab > 0.01F) {
            chromatic(g, w, h, ab);
            if (ab > 0.3F) glitchBars(g, w, h, ab);
        }
        if (flickerOn) g.fill(0, 0, w, h, ((int) (110 + RANDOM.nextInt(100)) << 24));
        if (flash > 0.02F) g.fill(0, 0, w, h, ((int) (Math.min(1.0F, flash) * 200) << 24) | 0x00FFE0E0);
    }

    static void vignette(GuiGraphics g, int w, int h, float r, float gr, float b, float a, int dx) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, Mth.clamp(a, 0.0F, 1.0F));
        g.blit(VIGNETTE, dx, 0, 0.0F, 0.0F, w, h, w, h);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /** Fake chromatic aberration: red and cyan fringes pulled apart at the screen edges. */
    static void chromatic(GuiGraphics g, int w, int h, float amount) {
        int off = (int) (2 + amount * 10);
        vignette(g, w, h, 1.0F, 0.0F, 0.0F, amount * 0.55F, -off);
        vignette(g, w, h, 0.0F, 1.0F, 1.0F, amount * 0.4F, off);
    }

    static void glitchBars(GuiGraphics g, int w, int h, float amount) {
        int n = RANDOM.nextInt(1 + (int) (amount * 6));
        for (int i = 0; i < n; i++) {
            int y = RANDOM.nextInt(Math.max(1, h));
            int bh = 1 + RANDOM.nextInt(6);
            int col = RANDOM.nextBoolean() ? 0x40FF0010 : 0x3000FFFF;
            int shift = RANDOM.nextInt(30) - 15;
            g.fill(shift, y, w + shift, y + bh, col);
        }
    }

    static void noise(GuiGraphics g, int w, int h, float a, float r, float gr, float b) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, a);
        g.blit(NOISE, 0, 0, RANDOM.nextInt(128), RANDOM.nextInt(128), w, h, 128, 128);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
