package com.woh.client;

import com.woh.W;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Everything that happens to the picture rather than in the world: camera shake, flashes, the impact frames,
 * hit-stop, smears, blackouts, the shockwave that ripples the screen. All of it runs on wall-clock time (not game
 * ticks), so it is as smooth as the frame rate allows and a frame sequence plays at the same speed at 30 or 240 fps.
 */
public final class WFx {
    private static long last = System.nanoTime();
    private static double now;           // seconds since start

    // shake (trauma model: offsets grow with trauma squared, trauma bleeds away)
    private static float trauma;
    // flash
    private static float flash, flashDecay = 4f;
    private static float fr = 1, fg = 1, fb = 1;
    // impact frames: a sequence of (until seconds, mode, amount)
    private static float[][] seq;
    private static double seqStart;
    private static Vec3 seqWorld;
    private static float seqDirX, seqDirY, seqLines, seqCracks, seqSeed;
    private static float ar = 1f, ag = 0.08f, ab = 0.12f;
    // punch (radial zoom blur), smear, aberration, blackout
    private static float punch, aberration, afterimage;
    private static float smearX, smearY, smear;
    private static double blackoutUntil;
    // the screen shockwave
    private static Vec3 shockAt;
    private static double shockStart;
    private static float shockStrength, shockSpeed = 1.2f;
    // field of view kick
    private static float fovKick;
    // hit-stop
    private static double stopUntil;

    private WFx() {}

    public static void update() {
        long t = System.nanoTime();
        float dt = Mth.clamp((t - last) / 1.0e9f, 0f, 0.1f);
        last = t;
        now += dt;
        trauma = Math.max(0, trauma - dt * 1.4f);
        flash = Math.max(0, flash - dt * flashDecay);
        punch = Math.max(0, punch - dt * 3.2f);
        aberration = Math.max(0, aberration - dt * 2.5f);
        afterimage = Math.max(0, afterimage - dt * 1.6f);
        smear = Math.max(0, smear - dt * 7f);
        fovKick *= (float) Math.exp(-dt * 6);
        if (seq != null && now - seqStart > seq[seq.length - 1][0]) seq = null;
        if (shockAt != null && now - shockStart > 1.6) shockAt = null;
    }

    public static double time() {
        return now;
    }

    public static boolean active() {
        return trauma > 0 || flash > 0.001f || seq != null || punch > 0.001f || smear > 0.001f || blackout() > 0 || shockAt != null
                || aberration > 0.001f || afterimage > 0.001f;
    }

    public static void reset() {
        trauma = flash = punch = aberration = afterimage = smear = fovKick = 0;
        seq = null;
        shockAt = null;
        blackoutUntil = stopUntil = 0;
    }

    // ------------------------------------------------------------------ triggers
    public static void shake(float amount) {
        trauma = Math.min(1.25f, trauma + amount * (float) W.shake);
    }

    public static void flash(float amount, float r, float g, float b, float decay) {
        if (amount * W.flash <= flash) return;
        flash = (float) Math.min(1.0, amount * W.flash);
        fr = r;
        fg = g;
        fb = b;
        flashDecay = decay;
    }

    public static void punch(float a) {
        punch = Math.max(punch, a * (float) W.motion);
    }

    public static void aberration(float a) {
        aberration = Math.max(aberration, a);
    }

    public static void afterimage(float a) {
        afterimage = Math.max(afterimage, a);
    }

    public static void smear(float dx, float dy, float a) {
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-4) return;
        smearX = dx / l * 0.06f;
        smearY = dy / l * 0.06f;
        smear = Math.max(smear, a * (float) W.motion);
    }

    public static void blackout(double seconds) {
        blackoutUntil = Math.max(blackoutUntil, now + seconds);
    }

    public static void shockwave(Vec3 at, float strength, float speed) {
        shockAt = at;
        shockStart = now;
        shockStrength = strength * (float) W.motion;
        shockSpeed = speed;
    }

    public static void fovKick(float degrees) {
        fovKick += degrees * (float) W.motion;
    }

    public static void hitStop(double seconds) {
        if (W.hitStop) stopUntil = Math.max(stopUntil, now + seconds);
    }

    public static boolean stopped() {
        return now < stopUntil;
    }

    public static void accent(float r, float g, float b) {
        ar = r;
        ag = g;
        ab = b;
    }

    /**
     * An impact-frame sequence: {seconds, mode, amount} triples played back to back. Modes (ycomposite.fsh):
     * 1 ink, 2 negative, 3 two-tone, 4 halftone, 5 white-out, 6 BLACK FLASH.
     */
    public static void impact(Vec3 world, float dirX, float dirY, boolean lines, boolean cracks, float[][] frames) {
        if (!W.impactFrames || W.flash <= 0) return;
        float[][] s = new float[frames.length][];
        double acc = 0;
        for (int i = 0; i < frames.length; i++) {
            acc += frames[i][0];
            s[i] = new float[]{(float) acc, frames[i][1], frames[i][2]};
        }
        seq = s;
        seqStart = now;
        seqWorld = world;
        float l = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        seqDirX = l < 1e-4 ? 1 : dirX / l;
        seqDirY = l < 1e-4 ? 0 : dirY / l;
        seqLines = lines ? 1 : 0;
        seqCracks = cracks ? 1 : 0;
        seqSeed = (float) (Math.random() * 100);
    }

    /** The Black Flash itself: sparks of black, the world turned inside out, then the two-tone afterglow. */
    /**
     * An overwrite blow landing: the seal frame, the glass breaking around it, a gilt afterimage; a short
     * stillness as reality is rewritten, then the shock rolls out.
     */
    public static void overwrite(Vec3 at, float dirX, float dirY, double scale, double distance) {
        float near = (float) Mth.clamp(1.0 - (distance - 6) / 60.0, 0.25, 1.0);
        float s = (float) Math.min(1.0, 0.5 + scale * 0.2) * near;
        accent(1f, 0.8f, 0.34f);
        if (scale >= 1.8) {
            impact(at, dirX, dirY, false, true, new float[][]{
                    {0.05f, 6, 1f}, {0.04f, 1, 1f}, {0.05f, 3, 1f}, {0.05f, 1, 0.9f}, {0.1f + 0.02f * (float) scale, 3, 0.6f}, {0.14f, 1, 0.25f}});
        } else if (scale >= 1.0) {
            impact(at, dirX, dirY, false, true, new float[][]{{0.035f, 1, 1f}, {0.04f, 3, 0.8f}, {0.06f, 1, 0.35f}});
        }   // the light blows of a barrage land without breaking the frame: only the seal, the shake and the shock
        shake(0.35f + 0.15f * (float) scale * near);
        punch(0.6f * s);
        aberration(0.5f * s);
        afterimage(0.5f * s);
        fovKick(-4f * s);
        shockwave(at, 0.9f * s, 1.1f + 0.2f * (float) scale);
        flash(0.3f * s, 1f, 0.93f, 0.72f, 5f);
        hitStop(Math.min(0.18, 0.05 + 0.03 * scale));
    }

    // ------------------------------------------------------------------ read by the pipeline
    public static float flashAmount() {
        return flash;
    }

    public static float[] flashColor() {
        return new float[]{fr, fg, fb};
    }

    public static float punchAmount() {
        return punch;
    }

    public static float aberrationAmount() {
        return aberration;
    }

    public static float afterimageAmount() {
        return afterimage;
    }

    public static float[] smearVec() {
        return new float[]{smearX, smearY, smear};
    }

    public static float blackout() {
        double left = blackoutUntil - now;
        return left <= 0 ? 0 : 1;
    }

    public static Vec3 shockAt() {
        return shockAt;
    }

    public static float shockRadius() {
        return (float) ((now - shockStart) * shockSpeed);
    }

    public static float shockStrength() {
        if (shockAt == null) return 0;
        double t = now - shockStart;
        return (float) (shockStrength * Math.exp(-t * 2.2));
    }

    /** {mode, amount} of the current impact frame, or null. */
    public static float[] frame() {
        if (seq == null) return null;
        double t = now - seqStart;
        for (float[] f : seq) if (t < f[0]) return new float[]{f[1], f[2]};
        return null;
    }

    public static Vec3 impactWorld() {
        return seqWorld;
    }

    public static float[] impactDir() {
        return new float[]{seqDirX, seqDirY, seqLines, seqCracks};
    }

    public static float impactSeed() {
        return seqSeed;
    }

    public static float[] accentColor() {
        return new float[]{ar, ag, ab};
    }

    public static float fov() {
        return fovKick;
    }

    // ------------------------------------------------------------------ the camera
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        float t2 = trauma * trauma;
        if (t2 <= 0) return;
        double s = now * 1.0;
        float k = 7.0f * t2;
        float yaw = (float) (Math.sin(s * 41.3) * 0.6 + Math.sin(s * 77.9 + 1.7) * 0.4) * k;
        float pitch = (float) (Math.sin(s * 37.1 + 4.1) * 0.6 + Math.sin(s * 83.3 + 0.3) * 0.4) * k;
        float roll = (float) (Math.sin(s * 29.7 + 2.2) * 0.7 + Math.sin(s * 61.1) * 0.3) * k * 0.8f;
        e.setYaw(e.getYaw() + yaw);
        e.setPitch(e.getPitch() + pitch);
        e.setRoll(e.getRoll() + roll);
    }
}
