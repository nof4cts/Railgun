package com.firestar.rose.client.cam;

import com.firestar.rose.client.RoseCast;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Requiem Rose's cutscene camera. It takes the game's own camera each rendered frame (not each tick) and places it on
 * the reel's path: spline moves, rack zooms, dutch roll, a handheld drift. The reaper stays the player: the game simply
 * looks at him from elsewhere. Eases out of the player's view and back into it; never goes inside a wall; hold sneak
 * for a second to skip.
 */
public final class Lens {
    private static Reel reel;
    private static RoseCast cast;
    private static CameraType saved;
    private static Pose pose;
    private static float weight;
    private static int sneakTicks;
    private static Method setPosition;

    private Lens() {}

    public record Pose(Vec3 pos, Vec3 look, float fov, float roll) {
        Pose lerp(Pose o, float k) {
            return new Pose(pos.lerp(o.pos, k), look.lerp(o.look, k), Mth.lerp(k, fov, o.fov), Mth.lerp(k, roll, o.roll));
        }

        float yaw() {
            Vec3 d = look.subtract(pos);
            return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        }

        float pitch() {
            Vec3 d = look.subtract(pos);
            return (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        }
    }

    public static boolean active() {
        return reel != null;
    }

    public static float weight() {
        return reel == null ? 0 : weight;
    }

    public static RoseCast cast() {
        return cast;
    }

    public static void play(RoseCast c, Reel r) {
        Minecraft mc = Minecraft.getInstance();
        if (reel == null) {
            saved = mc.options.getCameraType();
        }
        reel = r;
        cast = c;
        pose = null;
        sneakTicks = 0;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    public static void stop() {
        if (reel == null) {
            return;
        }
        reel = null;
        cast = null;
        pose = null;
        Minecraft mc = Minecraft.getInstance();
        if (saved != null) {
            mc.options.setCameraType(saved);
        }
        saved = null;
    }

    /** Every client tick: end the reel when its time is up, or when the player holds sneak to skip. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (reel == null) {
            return;
        }
        if (mc.player == null || mc.level == null || cast.time(0) > reel.end + 1) {
            stop();
            return;
        }
        sneakTicks = mc.options.keyShift.isDown() ? sneakTicks + 1 : 0;
        if (sneakTicks > 20) {
            stop();
        }
    }

    /** Where the camera is this frame (called while the game sets up the camera). Returns false when idle. */
    public static boolean place(Camera camera, float partial) {
        if (reel == null || cast == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        float t = cast.time(partial);
        Pose shot = reel.pose(t);
        shot = clip(shot);
        // the player's own view, to ease out of and back into
        Vec3 eye = mc.player.getEyePosition(partial);
        Vec3 view = mc.player.getViewVector(partial);
        Pose own = new Pose(eye, eye.add(view.scale(10)), (float) mc.options.fov().get().intValue(), 0);
        float in = smooth(t / 7.0F), out = smooth((reel.end - t) / 9.0F);
        weight = Math.min(in, out);
        pose = own.lerp(shot, weight);
        try {
            if (setPosition == null) {
                for (Method m : Camera.class.getDeclaredMethods()) {
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length == 1 && ps[0] == Vec3.class && m.getReturnType() == void.class) {
                        m.setAccessible(true);
                        setPosition = m;
                        break;
                    }
                }
            }
            if (setPosition != null) {
                setPosition.invoke(camera, pose.pos);
            }
        } catch (ReflectiveOperationException e) {
            stop();
            return false;
        }
        return true;
    }

    public static Pose pose() {
        return pose;
    }

    private static Pose clip(Pose p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return p;
        }
        Vec3 from = p.look.distanceTo(p.pos) > 40 ? p.pos : p.look;
        Vec3 d = p.pos.subtract(from);
        if (d.lengthSqr() < 1.0E-4) {
            return p;
        }
        BlockHitResult hit = mc.level.clip(new ClipContext(from, p.pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return p;
        }
        return new Pose(hit.getLocation().subtract(d.normalize().scale(0.3)), p.look, p.fov, p.roll);
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
