package com.firestar.silver.client.cinematic;

import com.firestar.silver.common.Beats;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the cinematic camera. The view is handed to an invisible marker entity that is moved every rendered frame (not
 * every tick), so camera moves are as smooth as the frame rate allows. The film eases in out of the player's own view
 * and back into it at the end, the camera is kept out of walls, and holding sneak skips.
 */
public final class Director {
    private static Film film;
    private static Marker camera;
    private static CameraType savedType;
    private static CameraPose pose;
    private static float weight;
    private static int skipHeld;
    private static boolean skipped;
    private static float skipAt;

    private Director() {}

    public static void play(Film f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        stop();
        film = f;
        skipHeld = 0;
        skipped = false;
        camera = new Marker(EntityType.MARKER, mc.level);
        Vec3 eye = mc.player.getEyePosition();
        camera.setPos(eye.x, eye.y, eye.z);
        savedType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (film == null) {
            return;
        }
        film = null;
        pose = null;
        weight = 0.0F;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (savedType != null) {
            mc.options.setCameraType(savedType);
        }
        savedType = null;
        camera = null;
    }

    public static boolean active() {
        return film != null;
    }

    public static float weight() {
        return film == null ? 0.0F : weight;
    }

    @Nullable
    public static Film film() {
        return film;
    }

    @Nullable
    public static CameraPose pose() {
        return film == null ? null : pose;
    }

    public static float time(float partial) {
        return film == null ? 0.0F : film.cast.time(partial);
    }

    public static boolean skipped() {
        return skipped;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (film == null) {
            return;
        }
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) {
            stop();
            return;
        }
        float t = film.cast.time(0.0F);
        skipHeld = mc.options.keyShift.isDown() ? skipHeld + 1 : 0;
        if (!skipped && skipHeld >= 12 && t > 6) {
            skipped = true;
            skipAt = t;
        }
        if (t > film.end || (skipped && t - skipAt > 8)) {
            stop();
        }
    }

    /** Called at the start of every rendered frame: place the camera. */
    public static void frame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (film == null || camera == null || mc.player == null) {
            return;
        }
        if (mc.getCameraEntity() != camera) {
            mc.setCameraEntity(camera);
        }
        float t = film.cast.time(partial);
        float in = Film.smooth(t / Beats.BLEND_IN);
        float out = 1.0F - Film.smooth((t - (film.end - Beats.BLEND_OUT)) / Beats.BLEND_OUT);
        if (skipped) {
            out = Math.min(out, 1.0F - Film.smooth((t - skipAt) / 8.0F));
        }
        weight = Math.max(0.0F, Math.min(in, out));

        CameraPose raw = film.pose(t);
        CameraPose shot = clip(raw, raw.look());
        Vec3 eye = mc.player.getEyePosition(partial);
        Vec3 view = mc.player.getViewVector(partial);
        double fov = mc.options.fov().get();
        CameraPose player = new CameraPose(eye, eye.add(view.scale(10.0)), (float) fov, 0.0F);
        pose = player.lerp(shot, weight);

        Vec3 p = pose.pos();
        camera.setPos(p.x, p.y, p.z);
        camera.xo = camera.xOld = p.x;
        camera.yo = camera.yOld = p.y;
        camera.zo = camera.zOld = p.z;
        float yaw = pose.yaw();
        float pitch = Mth.clamp(pose.pitch(), -90.0F, 90.0F);
        camera.setYRot(yaw);
        camera.yRotO = yaw;
        camera.setXRot(pitch);
        camera.xRotO = pitch;
    }

    /** Keep the lens out of walls: walk out from the subject and stop just short of the first solid block. */
    private static CameraPose clip(CameraPose pose, Vec3 subject) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return pose;
        }
        BlockHitResult hit = mc.level.clip(new ClipContext(subject, pose.pos(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return pose;
        }
        Vec3 dir = pose.pos().subtract(subject);
        if (dir.lengthSqr() < 1.0E-6) {
            return pose;
        }
        return new CameraPose(hit.getLocation().subtract(dir.normalize().scale(0.35)), pose.look(), pose.fov(), pose.roll());
    }
}
