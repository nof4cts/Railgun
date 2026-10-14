package com.firestar.sage.client.cam;

import com.firestar.sage.common.SBeats;
import com.firestar.sage.common.SMove;
import com.firestar.sage.client.SageCasts;
import com.firestar.sage.server.Rites;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Celestial Sage's cutscenes: a camera of its own (a marker the view is handed to) flown along each rite's shot
 * list. Slow, deliberate moves and long holds, then a sudden cut to the impact. Sneak (held) skips.
 */
public final class Director {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    @Nullable
    private static SageCasts.Play play;
    @Nullable
    private static Marker eye;
    @Nullable
    private static CameraType saved;
    private static int skip;
    @Nullable
    private static Shot shot;

    public record Shot(Vec3 pos, Vec3 look, float fov, float roll) {
        float yaw() {
            Vec3 d = look.subtract(pos);
            return (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        }

        float pitch() {
            Vec3 d = look.subtract(pos);
            return (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        }
    }

    private Director() {}

    public static boolean active() {
        return play != null;
    }

    @Nullable
    public static Shot shot() {
        return play == null ? null : shot;
    }

    public static float time(float partial) {
        return play == null ? 0 : play.time(partial);
    }

    public static void play(SageCasts.Play p) {
        if (!com.firestar.styles.client.ClientStyles.cutscenes()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        stop();
        play = p;
        skip = 0;
        eye = new Marker(EntityType.MARKER, mc.level);
        eye.setPos(mc.player.getEyePosition());
        saved = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(eye);
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (play != null) {
            play = null;
            shot = null;
            if (mc.player != null) {
                mc.setCameraEntity(mc.player);
            }
            if (saved != null) {
                mc.options.setCameraType(saved);
            }
            saved = null;
            eye = null;
        }
    }

    public static void tick() {
        if (play == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        skip = mc.options.keyShift.isDown() ? skip + 1 : 0;
        if (play.time(0) >= end(play.rite()) || skip >= 20) {
            stop();
        }
    }

    static int end(SMove m) {
        return switch (m) {
            case MANTLE -> 128;
            case STARFALL -> 150;
            case COLOSSUS -> 150;
            case ECLIPSE -> 220;
            default -> m.length;
        };
    }

    /** Each frame, before the world draws: place the eye. */
    public static void frame(float partial) {
        if (play == null || eye == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != eye) {
            mc.setCameraEntity(eye);
        }
        float t = play.time(partial);
        shot = switch (play.rite()) {
            case MANTLE -> mantle(t, partial);
            case STARFALL -> starfall(t, partial);
            case COLOSSUS -> colossus(t, partial);
            case ECLIPSE -> eclipse(t, partial);
            default -> null;
        };
        if (shot == null) {
            return;
        }
        Vec3 p = shot.pos();
        eye.setPos(p.x, p.y, p.z);
        eye.xo = eye.xOld = p.x;
        eye.yo = eye.yOld = p.y;
        eye.zo = eye.zOld = p.z;
        eye.setYRot(shot.yaw());
        eye.yRotO = shot.yaw();
        eye.setXRot(shot.pitch());
        eye.xRotO = shot.pitch();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0, 1);
    }

    static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    static Vec3 drift(float t, double amount) {
        return new Vec3(Math.sin(t * 0.061) * 0.5 + Math.sin(t * 0.19) * 0.2, Math.sin(t * 0.047 + 1.1) * 0.35, Math.sin(t * 0.053 + 2.0) * 0.5)
                .scale(amount);
    }

    /** Never put the eye inside the ground. */
    static Vec3 above(Vec3 p, double clearance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return p;
        }
        int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
        return p.y < h + clearance ? new Vec3(p.x, h + clearance, p.z) : p;
    }

    static Vec3 feet(float partial) {
        return play.feet(partial);
    }

    // ------------------------------------------------------------------------------------------------ the shots

    /** The Mantle: low and close on his face as the dark gathers, a slow orbit as the orbs come, wide on the ignition. */
    static Shot mantle(float t, float partial) {
        Vec3 f = play.flat(), r = play.right();
        Vec3 ft = feet(partial);
        Vec3 head = ft.add(0, 1.62, 0);
        if (t < SBeats.MANTLE_GATHER + 10) {
            // low in front, a slow push in on him as the dark gathers
            float u = ease(span(t, 0, SBeats.MANTLE_GATHER + 10));
            Vec3 pos = ft.add(f.scale(5.2 - 1.2 * u)).add(r.scale(1.6)).add(0, 0.7, 0);
            return new Shot(pos, ft.add(0, 1.3, 0), 50 - 4 * u, -3);
        }
        if (t < SBeats.MANTLE_HALO) {
            float u = ease(span(t, SBeats.MANTLE_GATHER + 10, SBeats.MANTLE_HALO));
            double a = Math.PI * (0.3 + 1.0 * u);
            Vec3 pos = ft.add(f.scale(Math.cos(a) * 6.5)).add(r.scale(Math.sin(a) * 6.5)).add(0, 2.2 + u * 1.5, 0);
            return new Shot(pos.add(drift(t, 0.3)), ft.add(0, 1.6, 0), 55, 0);
        }
        if (t < SBeats.MANTLE_IGNITE) {
            float u = ease(span(t, SBeats.MANTLE_HALO, SBeats.MANTLE_IGNITE));
            // in front and below, looking up at the face and the halo closing over it
            Vec3 pos = ft.add(f.scale(4.6 - 0.8 * u)).add(r.scale(-1.3)).add(0, 0.9, 0);
            return new Shot(pos, head.add(0, 0.2 + 0.2 * u, 0), 50 - 6 * u, 2);
        }
        float u = ease(span(t, SBeats.MANTLE_IGNITE, 128));
        Vec3 pos = ft.add(f.scale(9 + 4 * u)).add(r.scale(-3)).add(0, 1.0 + 2 * u, 0);
        return new Shot(above(pos, 1.0), ft.add(0, 1.4, 0), 62 + 6 * (1 - u), 0);
    }

    /** Starfall: his raised hand, up into the sky as it opens, the star's descent from below, the impact wide. */
    static Shot starfall(float t, float partial) {
        Vec3 f = play.flat(), r = play.right();
        Vec3 ft = feet(partial);
        Vec3 p0 = Rites.starPoint(play.msg.point(), f, 0);
        Vec3 from = Rites.starFrom(play.msg.point(), f, 0);
        if (t < SBeats.STAR_CALL + 10) {
            float u = ease(span(t, 0, SBeats.STAR_CALL + 10));
            Vec3 pos = ft.add(f.scale(2.4)).add(r.scale(-1.2)).add(0, 0.6, 0);
            return new Shot(pos, ft.add(0, 1.8 + u * 1.5, 0), 50, 0);
        }
        if (t < SBeats.STAR_APPEAR + 22) {
            float u = ease(span(t, SBeats.STAR_CALL + 10, SBeats.STAR_APPEAR + 22));
            Vec3 pos = ft.subtract(f.scale(3)).add(r.scale(1.4)).add(0, 1.2, 0);
            Vec3 look = ft.add(0, 3, 0).lerp(from, u * 0.85);
            return new Shot(pos.add(drift(t, 0.15)), look, 70 - 10 * u, 0);
        }
        if (t < SBeats.STAR_IMPACT - 2) {
            float u = ease(span(t, SBeats.STAR_APPEAR + 22, SBeats.STAR_IMPACT - 2));
            Vec3 pos = above(p0.subtract(f.scale(70)).add(r.scale(30)).add(0, 6, 0), 3);
            Vec3 star = from.lerp(p0, (float) Math.pow(span(t, SBeats.STAR_APPEAR, SBeats.STAR_IMPACT), 3));
            return new Shot(pos, star.lerp(p0, 0.25 + 0.5 * u), 58, -2 * u);
        }
        float u = ease(span(t, SBeats.STAR_IMPACT - 2, 150));
        Vec3 pos = above(p0.subtract(f.scale(95 + 20 * u)).add(r.scale(40)).add(0, 22 + 18 * u, 0), 4);
        return new Shot(pos.add(drift(t, 1.2 * (1 - u))), p0.add(0, 10, 0), 64, 0);
    }

    /** Colossus: from his feet up the giant as it rises, behind it as it lifts the blade, then down the cut. */
    static Shot colossus(float t, float partial) {
        Vec3 f = play.flat(), r = play.right();
        Vec3 ft = play.msg.point();
        if (t < SBeats.COL_FORMED) {
            float u = ease(span(t, SBeats.COL_RISE, SBeats.COL_FORMED));
            Vec3 pos = ft.add(f.scale(10)).add(r.scale(5)).add(0, 1.0 + 2 * u, 0);
            return new Shot(above(pos, 1), ft.subtract(f.scale(6)).add(0, 4 + 26 * u, 0), 74, -4 * u);
        }
        if (t < SBeats.COL_CUT - 4) {
            float u = ease(span(t, SBeats.COL_FORMED, SBeats.COL_CUT - 4));
            // side on, far enough to hold the whole giant and the blade it lifts
            Vec3 chest = ft.subtract(f.scale(6)).add(0, 22, 0);
            Vec3 pos = ft.add(r.scale(52 - 6 * u)).add(f.scale(10 + 6 * u)).add(0, 14 + 6 * u, 0);
            return new Shot(above(pos, 2), chest.add(f.scale(4 * u)).add(0, 4 - 6 * u, 0), 70, 3 * u);
        }
        float u = ease(span(t, SBeats.COL_CUT - 4, 150));
        Vec3 pos = ft.add(r.scale(-22)).add(f.scale(12 + 30 * u)).add(0, 12 + 10 * u, 0);
        return new Shot(above(pos.add(drift(t, 0.8 * (1 - u))), 3), ft.add(f.scale(40)).add(0, 0, 0), 70, 0);
    }

    /** Eternal Eclipse: him rising, the sun going black over his shoulder, the ring of light from far above. */
    static Shot eclipse(float t, float partial) {
        Vec3 f = play.flat(), r = play.right();
        Vec3 o = play.msg.point();
        Entity e = play.caster();
        Vec3 me = e != null ? e.getPosition(partial).add(0, 1, 0) : o.add(0, 1, 0);
        Vec3 sun = o.add(f.scale(30)).add(0, 95, 0);
        if (t < SBeats.ECL_COVER) {
            float u = ease(span(t, 0, SBeats.ECL_COVER));
            Vec3 pos = me.add(f.scale(5)).add(r.scale(2)).add(0, -2 + 1 * u, 0);
            return new Shot(pos, me.add(0, 0.6, 0), 52, 0);
        }
        if (t < SBeats.ECL_RING - 6) {
            float u = ease(span(t, SBeats.ECL_COVER, SBeats.ECL_RING - 6));
            Vec3 pos = me.subtract(f.scale(6)).add(r.scale(2.5)).add(0, -1.5, 0);
            return new Shot(pos.add(drift(t, 0.2)), me.lerp(sun, 0.25 + 0.55 * u), 60 + 8 * u, -2);
        }
        float u = ease(span(t, SBeats.ECL_RING - 6, 220));
        Vec3 pos = o.subtract(f.scale(40 + 30 * u)).add(r.scale(25)).add(0, 55 + 25 * u, 0);
        return new Shot(pos, o.add(f.scale(10)), 72, 0);
    }
}
