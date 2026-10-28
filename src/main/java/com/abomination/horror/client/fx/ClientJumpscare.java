package com.abomination.horror.client.fx;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.config.AbomConfig;
import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.registry.ModSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * First-sighting jumpscare, animatronic style: it is suddenly right in your face, your view is wrenched onto it,
 * it lunges at the camera screaming while everything shakes, tears and glitches - then it lets you run.
 *
 * <pre>
 *  0-3   it snaps into existence: hard cut, white-hot static
 *  3-26  screaming lunge at the lens, violent shake, chromatic tearing, zoom punching in
 *  26-30 cut to black with static
 *  30-36 release: control returns, distortion lingers into the chase
 * </pre>
 */
public final class ClientJumpscare {
    private static final int LENGTH = AbominationEntity.JUMPSCARE_TICKS;
    private static final RandomSource RANDOM = RandomSource.create();
    private static int creatureId = -1;
    private static long start;
    private static Vec3 lastFace;
    private static CameraType savedCamera;
    private static boolean cameraTaken;

    private ClientJumpscare() {}

    public static boolean active() {
        return creatureId >= 0;
    }

    public static void start(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ClientFinisher.localActive()) return;
        creatureId = id;
        start = mc.level.getGameTime();
        lastFace = null;
        if (AbomConfig.CLIENT.cinematicCamera.get() && !cameraTaken) {
            savedCamera = mc.options.getCameraType();
            cameraTaken = true;
        }
        mc.getSoundManager().stop(null, SoundSource.MUSIC);
        mc.getSoundManager().stop(null, SoundSource.AMBIENT);
        ear(mc, ModSounds.JUMPSCARE_SCREAM.get(), 1.0F, 1.0F);
        ear(mc, ModSounds.JUMPSCARE_SCREAM.get(), 1.0F, 0.8F);
        ear(mc, ModSounds.SUB_BASS.get(), 1.0F, 0.6F);
        ClientFx.apply(1.6F, 1.0F, 1.0F, 0);
    }

    static void ear(Minecraft mc, SoundEvent sound, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(sound.getLocation(), SoundSource.MASTER, Math.min(1.0F, volume), pitch,
                RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true));
    }

    private static float time(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 999.0F : (mc.level.getGameTime() - start) + partial;
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (cameraTaken) {
            mc.options.setCameraType(savedCamera != null ? savedCamera : CameraType.FIRST_PERSON);
            cameraTaken = false;
        }
        if (creatureId >= 0) ClientFx.apply(0.7F, 0.9F, 0.0F, 30); // distortion bleeds into the chase
        creatureId = -1;
    }

    public static void tick() {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || ClientFinisher.localActive()) {
            stop();
            return;
        }
        if (mc.isPaused()) return;
        int t = (int) (mc.level.getGameTime() - start);
        if (t >= LENGTH) {
            stop();
            return;
        }
        if (t == 6) ear(mc, ModSounds.JUMPSCARE_SCREAM_LONG.get(), 1.0F, 1.0F);
        if (t == 12) ear(mc, ModSounds.JUMPSCARE_SCREAM.get(), 1.0F, 0.7F);
        if (t == 26) ear(mc, ModSounds.IMPACT.get(), 0.7F, 0.6F);
        if (t < 30) ClientFx.apply(t < 26 ? 1.2F + RANDOM.nextFloat() * 0.6F : 0.0F, 1.0F, 0.0F, 0);

        Entity c = mc.level.getEntity(creatureId);
        if (c != null) {
            float s = c instanceof AbominationEntity a && a.isAwakened() ? 1.18F : 1.0F;
            lastFace = c.position().add(AbominationEntity.forward(c.getYRot()).scale(1.1D * s)).add(0, 3.0D * s, 0);
        }
        if (cameraTaken && lastFace != null && t < 30) {
            LocalPlayer p = mc.player;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            Vec3 d = lastFace.subtract(p.getEyePosition());
            float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0F / (float) Math.PI)) - 90.0F;
            float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0F / (float) Math.PI));
            p.yRotO = p.getYRot();
            p.xRotO = p.getXRot();
            p.setYRot(yaw + (RANDOM.nextFloat() - 0.5F) * 3.0F);
            p.setXRot(pitch + (RANDOM.nextFloat() - 0.5F) * 3.0F);
            p.yHeadRotO = p.yHeadRot;
            p.yHeadRot = p.getYRot();
        }
    }

    public static void onFov(ViewportEvent.ComputeFov e) {
        if (!active() || !cameraTaken) return;
        float tf = time((float) e.getPartialTick());
        double mult;
        if (tf < 3) mult = 0.75D;
        else if (tf < 26) mult = 0.75D - 0.3D * Mth.clamp((tf - 3.0F) / 10.0F, 0.0F, 1.0F) + Mth.sin(tf * 2.7F) * 0.03D;
        else mult = 0.9D;
        e.setFOV(e.getFOV() * mult);
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!active() || !AbomConfig.CLIENT.cameraShake.get()) return;
        float tf = time((float) e.getPartialTick());
        if (tf >= 26) return;
        // jerky servo-like twitches on top of the regular shake
        int step = (int) (tf * 2.0F);
        float jx = (hash(step) - 0.5F) * 9.0F, jy = (hash(step + 77) - 0.5F) * 7.0F;
        e.setYaw(e.getYaw() + jx);
        e.setPitch(e.getPitch() + jy);
        e.setRoll(e.getRoll() + (hash(step + 31) - 0.5F) * 14.0F);
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 2147483647.0F;
    }

    public static void onInput(MovementInputUpdateEvent e) {
        if (!active() || time(0.0F) >= 30) return;
        Input in = e.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    public static void onOverlayPre(RenderGuiOverlayEvent.Pre e) {
        if (active() && cameraTaken && time(0.0F) < 30 && !AbominationMod.MODID.equals(e.getOverlay().id().getNamespace())) {
            e.setCanceled(true);
        }
    }

    /** Returns true while the jumpscare owns the screen. */
    public static boolean renderOverlay(GuiGraphics g, int w, int h, float partial, boolean fx) {
        if (!active()) return false;
        float tf = time(partial);
        if (tf >= 30) return false;
        if (tf < 2.0F) {
            g.fill(0, 0, w, h, 0xE0FFFFFF);
            if (fx) ClientFx.noise(g, w, h, 0.8F, 1.0F, 1.0F, 1.0F);
            return true;
        }
        if (tf >= 26) {
            g.fill(0, 0, w, h, 0xFF000000);
            if (fx) ClientFx.noise(g, w, h, 0.35F, 0.6F, 0.6F, 0.6F);
            return true;
        }
        if (fx) {
            float pulse = 0.5F + 0.5F * Mth.sin(tf * 3.1F);
            ClientFx.vignette(g, w, h, 0.45F, 0.0F, 0.0F, 0.7F + 0.3F * pulse, 0);
            ClientFx.chromatic(g, w, h, 0.9F + 0.4F * pulse);
            ClientFx.glitchBars(g, w, h, 1.6F);
            ClientFx.noise(g, w, h, 0.18F + 0.2F * RANDOM.nextFloat(), 1.0F, 0.85F, 0.85F);
            if (RANDOM.nextInt(5) == 0) g.fill(0, 0, w, h, 0x50FF0000);       // red strobe
            if (RANDOM.nextInt(9) == 0) g.fill(0, 0, w, h, 0x90000000);       // dropped frame
            int bar = (int) (h * 0.08F);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        return true;
    }
}
