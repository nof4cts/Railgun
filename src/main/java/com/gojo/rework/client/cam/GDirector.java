package com.gojo.rework.client.cam;

import com.gojo.rework.client.GCasts;
import com.gojo.rework.common.GBeats;
import com.gojo.rework.common.GMove;
import com.gojo.rework.server.GRites;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Gojo's cutscenes: the view handed to a marker flown along each technique's shot list. Hold Shift to skip; the JJK
 * menu's "skip cutscenes" turns them off altogether.
 */
public final class GDirector {
    @Nullable
    private static GCasts.Play play;
    @Nullable
    private static Marker eye;
    @Nullable
    private static CameraType saved;
    private static int skip;
    private static boolean released;
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

    private GDirector() {}

    public static boolean active() {
        return play != null;
    }

    @Nullable
    public static GCasts.Play playing() {
        return play;
    }

    @Nullable
    public static Shot shot() {
        return play == null ? null : shot;
    }

    public static void play(GCasts.Play p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        stop();
        play = p;
        skip = 0;
        released = !mc.options.keyShift.isDown();
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
        // Shift skips, but only a press made during the cutscene (not one held from before, or toggled sneak)
        boolean down = mc.options.keyShift.isDown();
        if (!down) {
            released = true;
        }
        skip = down && released ? skip + 1 : 0;
        if (play.time(0) >= end(play.move()) || skip >= 16) {
            stop();
        }
    }

    public static int end(GMove m) {
        return switch (m) {
            case PURPLE -> GBeats.HP_FIRE + 34;
            case IMAGINARY -> GBeats.IM_FIRE + 46;
            case PURPLE_200 -> GBeats.P2_FIRE + 56;
            case NUKE -> GBeats.NK_BLAST + 90;
            case DOMAIN -> GBeats.DV_CUT;
            default -> m.length;
        };
    }

    /** Each frame before the world draws: place the eye. */
    public static void frame(float partial) {
        if (play == null || eye == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != eye) {
            mc.setCameraEntity(eye);
        }
        float t = play.time(partial);
        Shot s = switch (play.move()) {
            case PURPLE -> hanami(t, partial);
            case IMAGINARY -> chanted(t, partial, false);
            case PURPLE_200 -> chanted(t, partial, true);
            case NUKE -> nuke(t, partial);
            case DOMAIN -> domain(t, partial);
            default -> null;
        };
        if (s == null) {
            stop();
            return;
        }
        shot = s;
        eye.setPos(s.pos.x, s.pos.y - eye.getEyeHeight(), s.pos.z);
        eye.xo = eye.getX();
        eye.yo = eye.getY();
        eye.zo = eye.getZ();
        eye.setYRot(s.yaw());
        eye.setXRot(s.pitch());
        eye.yRotO = eye.getYRot();
        eye.xRotO = eye.getXRot();
    }

    static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0, 1);
    }

    static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    static Vec3 drift(float t, double amount) {
        return new Vec3(Math.sin(t * 0.07) * amount, Math.sin(t * 0.05 + 1.3) * amount * 0.6, Math.cos(t * 0.06) * amount);
    }

    static Vec3 above(Vec3 p, double clearance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return p;
        }
        int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
        return p.y < h + clearance ? new Vec3(p.x, h + clearance, p.z) : p;
    }

    // ------------------------------------------------------------------------------------------------ the shots

    /** The Purples spoken in full: rising, the four lines each from its own angle, the two colours, the shot. */
    static Shot chanted(float t, float partial, boolean p200) {
        Vec3 f = play.flat(), r = play.right(), d = play.dir();
        Vec3 eyeP = play.eye(partial);
        Vec3 feet = play.feet(partial);
        int shift = p200 ? GBeats.P2_SHIFT : 0;
        int red = p200 ? GBeats.P2_RED : GBeats.IM_RED, merge = p200 ? GBeats.P2_MERGE : GBeats.IM_MERGE;
        int fire = p200 ? GBeats.P2_FIRE : GBeats.IM_FIRE;
        double sc = p200 ? 1.5 : 1.0;
        int[] c = GBeats.CHANT;
        if (t < c[0] + shift) {
            float u = ease(span(t, 0, c[0] + shift));
            Vec3 pos = feet.add(f.scale(5.5)).add(r.scale(-2.5)).add(0, 0.3, 0);
            return new Shot(above(pos, 0.3), eyeP.add(0, -0.2 + 0.4 * u, 0), 60, -2);
        }
        if (t < c[1] + shift) {
            // "Nine Ropes": close on his face, low
            float u = ease(span(t, c[0] + shift, c[1] + shift));
            Vec3 pos = eyeP.add(f.scale(2.0 - 0.3 * u)).add(r.scale(0.6)).add(0, -0.35, 0);
            return new Shot(pos, eyeP.add(0, 0.05, 0), 42, 3);
        }
        if (t < c[2] + shift) {
            // "Polarized Light": side on
            float u = ease(span(t, c[1] + shift, c[2] + shift));
            Vec3 pos = eyeP.add(r.scale(3.2 + 0.4 * u)).add(f.scale(0.6)).add(0, 0.1, 0);
            return new Shot(pos, eyeP.add(f.scale(1.0)), 50, 0);
        }
        if (t < c[3] + shift) {
            // "Crow and Declaration": over his shoulder, down his line of aim
            float u = ease(span(t, c[2] + shift, c[3] + shift));
            Vec3 pos = eyeP.subtract(f.scale(2.6)).add(r.scale(0.9)).add(0, 0.5, 0);
            return new Shot(pos, eyeP.add(d.scale(12 + 6 * u)), 56 - 4 * u, -2);
        }
        if (t < red - 2) {
            // "Between Front and Back": from high in front, looking down on him
            float u = ease(span(t, c[3] + shift, red - 2));
            Vec3 pos = eyeP.add(f.scale(4.5)).add(0, 2.2 - 0.6 * u, 0);
            return new Shot(pos, eyeP, 58, 0);
        }
        if (t < merge - 4) {
            // the two colours, wide enough to hold both
            float u = ease(span(t, red - 2, merge - 4));
            Vec3 pos = eyeP.add(f.scale(7.5 * sc - 1.5 * u)).add(0, -0.4, 0);
            return new Shot(pos.add(drift(t, 0.08)), eyeP.add(f.scale(1.5)), 66, 0);
        }
        if (t < fire) {
            // pressed into one: tight on the purple
            float u = ease(span(t, merge - 4, fire));
            Vec3 centre = eyeP.add(d.scale(2.2 * sc));
            Vec3 pos = centre.add(f.scale(3.8 * sc - 1.2 * u)).add(r.scale(-1.4)).add(0, 0.6, 0);
            return new Shot(pos.add(drift(t, 0.05 + 0.1 * u)), centre, 52 - 8 * u, 0);
        }
        float age = t - fire;
        double speed = p200 ? GBeats.P2_SPEED : GBeats.IM_SPEED;
        Vec3 from = GBeats.purpleStart(play.move(), play.msg.origin(), play.msg.dir());
        if (age < 12) {
            // behind him as it leaves
            Vec3 pos = eyeP.subtract(d.scale(4.5)).add(r.scale(1.6)).add(0, 1.0, 0);
            Vec3 ball = from.add(d.scale(age * speed));
            return new Shot(pos, ball, 70 + age, 0);
        }
        // from the side, tracking the erasure across the land
        Vec3 ball = from.add(d.scale(Math.min(p200 ? GBeats.P2_RANGE : GBeats.IM_RANGE, age * speed)));
        Vec3 mid = from.lerp(ball, 0.55);
        Vec3 pos = above(mid.add(r.scale(-34 * sc)).add(0, 10 * sc, 0), 3);
        return new Shot(pos, mid.lerp(ball, 0.6), 74, 0);
    }

    /**
     * The Hollow Nuke: rising from below him; Blue shot and circling him; Red fired up past the lens; the two meeting
     * in the sky over his head; the Purple hurled down, followed; the blast from far off.
     */
    static Shot nuke(float t, float partial) {
        Vec3 f = play.flat(), r = play.right();
        Vec3 o = play.msg.origin(), d = play.msg.dir();
        Vec3 eyeP = play.eye(partial);
        Vec3 g = play.msg.point();
        Vec3 sky = GBeats.nukeRedPoint(o, d);
        if (t < GBeats.NK_RISE) {
            float u = ease(span(t, 0, GBeats.NK_RISE));
            Vec3 pos = o.add(f.scale(4)).add(r.scale(1.5)).add(0, -1.0, 0);
            return new Shot(above(pos, 0.4), eyeP, 66 - 8 * u, 2);
        }
        if (t < GBeats.NK_RED - 2) {
            // close and level with him in the air, Blue sweeping round
            float u = ease(span(t, GBeats.NK_RISE, GBeats.NK_RED));
            double a = Math.PI * (0.15 + 0.4 * u);
            Vec3 pos = eyeP.add(f.scale(Math.cos(a) * 8)).add(r.scale(Math.sin(a) * 8)).add(0, 0.6, 0);
            return new Shot(pos, eyeP.add(0, -0.3, 0), 62, 0);
        }
        if (t < GBeats.NK_JOIN) {
            // behind him, looking up as Red goes up into the sky
            float u = ease(span(t, GBeats.NK_RED - 2, GBeats.NK_JOIN));
            Vec3 pos = eyeP.subtract(f.scale(5)).add(r.scale(1.6)).add(0, -1.5, 0);
            return new Shot(pos, eyeP.lerp(sky, 0.4 + 0.5 * u), 66, -2);
        }
        if (t < GBeats.NK_LAUNCH) {
            // from the side, wide: him small below the two meeting, then the Purple swelling
            float u = ease(span(t, GBeats.NK_JOIN, GBeats.NK_LAUNCH));
            Vec3 pos = eyeP.lerp(sky, 0.5).add(r.scale(-24 - 6 * u)).add(f.scale(-4));
            return new Shot(pos.add(drift(t, 0.3)), eyeP.lerp(sky, 0.6 + 0.3 * u), 66, 3 * u);
        }
        if (t < GBeats.NK_BLAST) {
            // over his shoulder, down the line it falls
            float u = ease(span(t, GBeats.NK_LAUNCH, GBeats.NK_BLAST));
            Vec3 pos = eyeP.subtract(f.scale(4)).add(r.scale(1.4)).add(0, 2.0, 0);
            return new Shot(pos, GBeats.nukePurple(o, d, g, t).lerp(g, 0.3 * u), 72 + 6 * u, 0);
        }
        // the blast from far off, the camera rocked back by it
        float u = ease(span(t, GBeats.NK_BLAST, GBeats.NK_BLAST + 90));
        Vec3 pos = above(g.add(f.scale(-110 - 30 * u)).add(r.scale(70)).add(0, 30 + 20 * u, 0), 4);
        return new Shot(pos.add(drift(t, 1.5 * (1 - u))), g.add(0, 25 + 20 * u, 0), 68, 0);
    }

    /** Hollow Purple (Hanami): rising from below, Red and Blue blooming either side of him, the shot down the line. */
    static Shot hanami(float t, float partial) {
        Vec3 f = play.flat(), r = play.right(), d = play.dir();
        Vec3 eyeP = play.eye(partial);
        Vec3 o = play.msg.origin();
        if (t < GBeats.HP_RISEN + 2) {
            float u = ease(span(t, 0, GBeats.HP_RISEN));
            Vec3 pos = o.add(f.scale(4.5)).add(r.scale(-1.6)).add(0, -1.2, 0);
            return new Shot(above(pos, 0.4), eyeP, 64 - 6 * u, -2);
        }
        if (t < GBeats.HP_MERGE) {
            // in front, wide enough to hold him and both colours
            float u = ease(span(t, GBeats.HP_RISEN, GBeats.HP_MERGE));
            Vec3 pos = eyeP.add(f.scale(8.5 - 1.5 * u)).add(0, -0.6, 0);
            return new Shot(pos.add(drift(t, 0.06)), eyeP.add(0, -0.3, 0), 68, 0);
        }
        if (t < GBeats.HP_FIRE) {
            // tight on the Purple in his hand
            float u = ease(span(t, GBeats.HP_MERGE, GBeats.HP_FIRE));
            Vec3 centre = eyeP.add(d.scale(2.4));
            Vec3 pos = centre.add(f.scale(3.2 - 0.8 * u)).add(r.scale(-1.6)).add(0, 0.5, 0);
            return new Shot(pos, centre, 54 - 8 * u, 0);
        }
        // behind him as it goes down the line
        float age = t - GBeats.HP_FIRE;
        Vec3 from = GBeats.purpleStart(play.move(), o, play.msg.dir());
        Vec3 ball = from.add(d.scale(Math.min(GBeats.HP_RANGE, age * GBeats.HP_SPEED)));
        Vec3 pos = eyeP.subtract(f.scale(4.0)).add(r.scale(1.5)).add(0, 1.2, 0);
        return new Shot(pos, ball, 72, 0);
    }

    /** Unlimited Void: the hand sign, held through the banner, then the black hole and the void. */
    static Shot domain(float t, float partial) {
        Vec3 f = play.flat(), r = play.right();
        Vec3 eyeP = play.eye(partial);
        Vec3 feet = play.feet(partial);
        if (t < GBeats.DV_BANNER) {
            float u = ease(span(t, 0, GBeats.DV_BANNER));
            return new Shot(eyeP.add(f.scale(1.8 - 0.3 * u)).add(r.scale(0.3)).add(0, -0.15, 0), eyeP.add(0, -0.1, 0), 46, 0);
        }
        if (t < GBeats.DV_FLASH2) {
            float u = ease(span(t, GBeats.DV_BANNER, GBeats.DV_FLASH2));
            return new Shot(eyeP.add(f.scale(3.0 - 1.0 * u)).add(r.scale(-0.6)).add(0, 0.1, 0), eyeP, 54 - 6 * u, 0);
        }
        Vec3 hole = play.msg.origin().add(f.scale(GBeats.DV_RADIUS * 0.82)).add(0, GBeats.DV_RADIUS * 0.28, 0);
        if (t < GBeats.DV_VOID) {
            // behind him, looking past him at the black hole opening
            float u = ease(span(t, GBeats.DV_FLASH2, GBeats.DV_VOID));
            Vec3 pos = eyeP.subtract(f.scale(3.5 + 2.0 * u)).add(r.scale(1.2)).add(0, -0.3, 0);
            return new Shot(pos, eyeP.lerp(hole, 0.6), 68, -2 * u);
        }
        // inside the void: a slow circle round him and whoever stands frozen in it
        float u = ease(span(t, GBeats.DV_VOID, GBeats.DV_CUT));
        double ang = Math.PI * (1.15 + 0.5 * u);
        Vec3 pos = feet.add(f.scale(Math.cos(ang) * 9)).add(r.scale(Math.sin(ang) * 9)).add(0, 2.4, 0);
        return new Shot(pos, feet.add(0, 1.4, 0).add(f.scale(3)), 70, 0);
    }
}
