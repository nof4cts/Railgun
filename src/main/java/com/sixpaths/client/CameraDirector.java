package com.sixpaths.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;

/**
 * Takes over the camera for cutscenes by swapping the camera entity to an invisible
 * client-only marker that we move every rendered frame (frame-rate-independent, so it
 * is as smooth as the monitor refresh). Also owns screen shake.
 */
public final class CameraDirector {
    private static Marker cam;
    private static Cutscene current;
    private static CameraType savedType;

    // Vanilla's Camera eases its eye height toward the camera entity's over a few ticks;
    // we mirror that decay so switching to the (zero-eye-height) marker never dips the shot.
    private static float eyeOld, eye;
    private static float lastPartial = 1f;

    private static float shakeAmp;
    private static long shakeStart, shakeLife;

    public static boolean active() {
        return current != null;
    }

    public static void play(Cutscene c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (cam == null || cam.level() != mc.level) cam = new Marker(EntityType.MARKER, mc.level);
        if (current == null) savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (current == null) {
            eyeOld = eye = mc.player.getEyeHeight();
        }
        current = c;
        c.begin();
        c.update(1f);
        place(c);
        mc.setCameraEntity(cam);
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (current == null) return;
        current = null;
        if (mc.player != null) mc.setCameraEntity(mc.player);
        if (savedType != null) mc.options.setCameraType(savedType);
        ScreenFx.letterbox(0);
        ScreenFx.vignette(0);
    }

    private static void place(Cutscene c) {
        double y = c.outPos.y - Mth.lerp(lastPartial, eyeOld, eye);
        cam.setPos(c.outPos.x, y, c.outPos.z);
        cam.xo = cam.xOld = c.outPos.x;
        cam.yo = cam.yOld = y;
        cam.zo = cam.zOld = c.outPos.z;
        cam.setYRot(c.outYaw);
        cam.yRotO = c.outYaw;
        cam.setXRot(c.outPitch);
        cam.xRotO = c.outPitch;
    }

    /** Called at the start of every rendered frame. */
    public static void frame(float partial) {
        Cutscene c = current;
        if (c == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || c.done()) {
            stop();
            return;
        }
        lastPartial = partial;
        c.update(partial);
        place(c);
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
    }

    /** Client tick: mirror vanilla Camera's eye-height easing. */
    public static void clientTick() {
        if (current == null) return;
        eyeOld = eye;
        eye += (0f - eye) * 0.5f;
    }

    public static double fov(double vanilla) {
        return current == null ? vanilla : current.outFov;
    }

    public static float roll() {
        return current == null ? 0 : current.outRoll;
    }

    public static void shake(float amp, long life) {
        long now = Vfx.now();
        float remaining = currentShake(now);
        if (amp >= remaining) {
            shakeAmp = amp;
            shakeStart = now;
            shakeLife = life;
        }
    }

    private static float currentShake(long now) {
        if (now - shakeStart > shakeLife) return 0;
        float p = (now - shakeStart) / (float) shakeLife;
        return shakeAmp * (1 - p) * (1 - p);
    }

    /** {yaw, pitch, roll} offsets in degrees. */
    public static float[] shakeOffsets() {
        long now = Vfx.now();
        float a = currentShake(now);
        if (a <= 0.001f) return new float[]{0, 0, 0};
        double t = now / 1000.0;
        float yaw = (float) (Math.sin(t * 61.3) + Math.sin(t * 97.1) * 0.5) * a;
        float pitch = (float) (Math.sin(t * 53.7 + 1.3) + Math.sin(t * 89.9) * 0.5) * a;
        float roll = (float) Math.sin(t * 41.1 + 2.1) * a * 0.8f;
        return new float[]{yaw, pitch, Mth.clamp(roll, -12, 12)};
    }

    public static void reset() {
        stop();
        cam = null;
        shakeAmp = 0;
    }

    private CameraDirector() {}
}
