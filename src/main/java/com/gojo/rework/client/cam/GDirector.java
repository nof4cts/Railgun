package com.gojo.rework.client.cam;

import com.gojo.rework.client.GCasts;
import com.gojo.rework.common.GBeats;
import com.gojo.rework.common.GMove;
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
        if (p.skipped) {
            return;
        }
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
            com.gojo.rework.client.GFilm.stop();
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
        if (skip >= 16) {
            play.skipped = true;
            com.gojo.rework.client.GFilm.stop();
            stop();
            return;
        }
        com.gojo.rework.client.GFilm.tick(play);
        if (play.time(0) >= end(play)) {
            stop();
        }
    }

    /**
     * Where the cutscene hands the view back: a Purple once it is ready (he aims himself; the cutscene picks up again
     * when he lets it go and follows the shot), otherwise at the technique's own end.
     */
    public static int end(GCasts.Play p) {
        GMove m = p.move();
        if (GBeats.aims(m)) {
            if (p.fireAt < 0) {
                return GBeats.ready(m);
            }
            return p.fireAt + (m == GMove.NUKE ? GBeats.NK_DROP + 90 : Math.min(GBeats.flight(m), 30) + 20);
        }
        return switch (m) {
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
            case PURPLE, PURPLE_200 -> purple(t, partial);
            case IMAGINARY -> toji(t, partial);
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

    /** Where a fired Purple is, {@code age} ticks after it left. */
    static Vec3 ball(GMove m, float age) {
        double speed = m == GMove.PURPLE_200 ? GBeats.P2_SPEED : m == GMove.IMAGINARY ? GBeats.IM_SPEED : GBeats.HP_SPEED;
        double range = m == GMove.PURPLE_200 ? GBeats.P2_RANGE : m == GMove.IMAGINARY ? GBeats.IM_RANGE : GBeats.HP_RANGE;
        return play.fireFrom.add(play.fireDir.scale(Math.min(range, Math.max(0, age) * speed)));
    }

    /** After the shot: behind him as it leaves, then from the side as it tears across the land. */
    static Shot follow(float t, float partial, double sc) {
        GMove m = play.move();
        float age = t - play.fireAt;
        Vec3 d = play.fireDir;
        Vec3 f = new Vec3(d.x, 0, d.z).lengthSqr() < 1e-4 ? play.flat() : new Vec3(d.x, 0, d.z).normalize();
        Vec3 r = new Vec3(-f.z, 0, f.x);
        Vec3 eyeP = play.eye(partial);
        if (age < 12) {
            float u = ease(age / 12F);
            Vec3 pos = eyeP.subtract(d.scale(3.5 + 2 * u)).add(r.scale(1.4)).add(0, 0.8, 0);
            return new Shot(pos.add(drift(t, 0.25 * (1 - u))), ball(m, age), 66 + 10 * u, 0);
        }
        Vec3 b = ball(m, age);
        Vec3 mid = play.fireFrom.lerp(b, 0.55);
        Vec3 pos = above(mid.add(r.scale(-26 * sc)).add(0, 8 * sc, 0), 3);
        return new Shot(pos, mid.lerp(b, 0.6), 72, 0);
    }

    // ------------------------------------------------------------------------------------------------ the shots

    /**
     * Hollow Purple (Hanami) and the 200%: rising from below; for the 200% the four lines of the incantation each
     * from its own angle; then as in the anime - "Reversal: Red" close on him, Red blooms at his side; "Lapse: Blue",
     * Blue at the other; the two drawn together, wide; "Hollow..." tight on the Purple. Then the view is his to aim.
     */
    static Shot purple(float t, float partial) {
        GMove m = play.move();
        boolean p200 = m == GMove.PURPLE_200;
        if (play.fireAt >= 0) {
            return follow(t, partial, p200 ? 1.5 : 1.0);
        }
        int[] b = GBeats.purpleBeats(m);
        if (t >= b[7]) {
            return null;
        }
        Vec3 f = play.flat(), r = play.right();
        Vec3 eyeP = play.eye(partial);
        Vec3 o = play.msg.origin();
        double sc = p200 ? 2.0 : 1.0;
        int risen = p200 ? 26 : GBeats.HP_RISEN;
        if (t < risen + 2) {
            float u = ease(span(t, 0, risen));
            Vec3 pos = o.add(f.scale(4.5)).add(r.scale(-1.6)).add(0, -1.2, 0);
            return new Shot(above(pos, 0.4), eyeP, 64 - 6 * u, -2);
        }
        if (p200 && t < b[0]) {
            int[] c = GBeats.CHANT;
            int shift = GBeats.P2_SHIFT;
            int i = 0;
            while (i < c.length - 1 && t >= c[i + 1] + shift) {
                i++;
            }
            float u = ease(span(t, c[i] + shift, (i + 1 < c.length ? c[i + 1] + shift : b[0])));
            return switch (i) {
                case 0 -> new Shot(eyeP.add(f.scale(2.0 - 0.3 * u)).add(r.scale(0.6)).add(0, -0.35, 0), eyeP.add(0, 0.05, 0), 42, 3);
                case 1 -> new Shot(eyeP.add(r.scale(3.2 + 0.4 * u)).add(f.scale(0.6)).add(0, 0.1, 0), eyeP.add(f.scale(1.0)), 50, 0);
                case 2 -> new Shot(eyeP.subtract(f.scale(2.6)).add(r.scale(0.9)).add(0, 0.5, 0), eyeP.add(f.scale(12 + 6 * u)), 56 - 4 * u, -2);
                default -> new Shot(eyeP.add(f.scale(4.5)).add(0, 2.2 - 0.6 * u, 0), eyeP, 58, 0);
            };
        }
        Vec3 side = eyeP.add(f.scale(0.9)).add(0, 0.3, 0);
        Vec3 redAt = side.add(r.scale(-2.6 * sc)), blueAt = side.add(r.scale(2.6 * sc));
        if (t < b[1] - 3) {
            // "Cursed technique reversal: Red" - close on him as he says it
            float u = ease(span(t, risen, b[1] - 3));
            Vec3 pos = eyeP.add(f.scale(2.2 - 0.4 * u)).add(r.scale(-0.7)).add(0, -0.2, 0);
            return new Shot(pos.add(drift(t, 0.03)), eyeP.add(0, -0.05, 0), 46 - 4 * u, 2);
        }
        if (t < b[2]) {
            // Red blooms at his side: over that shoulder, Red large in the foreground
            float u = ease(span(t, b[1] - 3, b[2]));
            Vec3 pos = redAt.add(f.scale(3.0 * sc - 0.4 * u)).add(r.scale(-1.6 * sc)).add(0, 0.4, 0);
            return new Shot(pos.add(drift(t, 0.04)), redAt.lerp(eyeP, 0.35), 58, -3);
        }
        if (t < b[3] - 3) {
            // "Lapse: Blue" - close on him from the other side
            float u = ease(span(t, b[2], b[3] - 3));
            Vec3 pos = eyeP.add(f.scale(2.2 - 0.4 * u)).add(r.scale(0.7)).add(0, -0.2, 0);
            return new Shot(pos.add(drift(t, 0.03)), eyeP.add(0, -0.05, 0), 46 - 4 * u, -2);
        }
        if (t < b[4]) {
            float u = ease(span(t, b[3] - 3, b[4]));
            Vec3 pos = blueAt.add(f.scale(3.0 * sc - 0.4 * u)).add(r.scale(1.6 * sc)).add(0, 0.4, 0);
            return new Shot(pos.add(drift(t, 0.04)), blueAt.lerp(eyeP, 0.35), 58, 3);
        }
        if (t < b[6] - 2) {
            // the two drawn in together: wide, in front, pushing in
            float u = ease(span(t, b[4], b[6] - 2));
            Vec3 pos = eyeP.add(f.scale(8.0 * sc - 2.5 * u * sc)).add(0, -0.5, 0);
            return new Shot(pos.add(drift(t, 0.05 + 0.08 * u)), eyeP.add(f.scale(1.5)), 68 - 6 * u, 0);
        }
        // "Hollow..." tight on the Purple in his hand, a slow arc round it
        float u = ease(span(t, b[6] - 2, b[7]));
        Vec3 centre = eyeP.add(f.scale(GBeats.purpleAhead(m))).add(0, -0.2, 0);
        double a = -0.6 + 0.5 * u;
        Vec3 pos = centre.add(f.scale(Math.cos(a) * 3.6 * sc)).add(r.scale(Math.sin(a) * 3.6 * sc)).add(0, 0.5, 0);
        return new Shot(pos.add(drift(t, 0.04)), centre.lerp(eyeP, 0.25), 54 - 6 * u, 0);
    }

    /**
     * Imaginary Technique: Purple, as against Toji: he ascends slowly into a golden sky while he speaks; between the
     * shots of him, the rendered cutaways (sky, leaves, the two drops) cover the screen; then the Purple is in his
     * hand, and the view is his to aim.
     */
    static Shot toji(float t, float partial) {
        if (play.fireAt >= 0) {
            return follow(t, partial, 1.3);
        }
        if (t >= GBeats.IM_READY) {
            return null;
        }
        Vec3 f = play.flat(), r = play.right();
        Vec3 eyeP = play.eye(partial);
        Vec3 o = play.msg.origin();
        Vec3 feet = play.feet(partial);
        if (t < 64) {
            // from the ground, looking up as he leaves it
            float u = ease(span(t, 0, 64));
            Vec3 pos = o.add(f.scale(5.0)).add(r.scale(-2.0)).add(0, -1.3, 0);
            return new Shot(above(pos, 0.3), eyeP.add(0, 0.6 * u, 0), 66 - 6 * u, -3);
        }
        if (t < GBeats.IM_FILM_SKY[1] + 2) {
            // his face, lit gold, a slow push in
            float u = ease(span(t, 64, GBeats.IM_FILM_SKY[0]));
            Vec3 pos = eyeP.add(f.scale(1.9 - 0.5 * u)).add(r.scale(0.45)).add(0, -0.25, 0);
            return new Shot(pos.add(drift(t, 0.02)), eyeP.add(0, 0.02, 0), 40, 2);
        }
        if (t < GBeats.IM_TENJO) {
            // straight down on him from above, arms spread to the sky, the camera turning slowly
            float u = ease(span(t, GBeats.IM_FILM_SKY[1], GBeats.IM_TENJO));
            double a = 0.6 * u;
            Vec3 ahead = f.scale(Math.cos(a) * 0.4).add(r.scale(Math.sin(a) * 0.4));
            return new Shot(feet.add(0, 7.5 - 1.5 * u, 0).add(ahead), feet.add(0, 0.8, 0), 60, 0);
        }
        if (t < GBeats.IM_FILM_LEAVES[0] + 2) {
            // "Throughout Heaven and Earth...": low and close, looking up at him, pushing in
            float u = ease(span(t, GBeats.IM_TENJO, GBeats.IM_TENJO_END));
            Vec3 pos = eyeP.add(f.scale(2.6 - 0.8 * u)).add(r.scale(-0.8)).add(0, -1.6 + 0.4 * u, 0);
            return new Shot(pos, eyeP.add(0, 0.1, 0), 58 - 8 * u, -4);
        }
        if (t < GBeats.IM_FILM_LEAVES[1] + 2) {
            return new Shot(eyeP.add(f.scale(3)).add(0, -1, 0), eyeP, 60, 0);
        }
        if (t < 494) {
            // high above everything: a long circle round him, the land far below
            float u = ease(span(t, GBeats.IM_FILM_LEAVES[1], 494));
            double a = Math.PI * (0.2 + 0.55 * u);
            Vec3 pos = eyeP.add(f.scale(Math.cos(a) * 11)).add(r.scale(Math.sin(a) * 11)).add(0, 1.5 - 2.5 * u, 0);
            return new Shot(pos, eyeP.add(0, -0.6, 0), 64, 0);
        }
        if (t < GBeats.IM_FILM_DROPS[0] + 2) {
            // "...only a handful of the Gojo clan know": side on, close, still
            float u = ease(span(t, 494, GBeats.IM_FILM_DROPS[0]));
            Vec3 pos = eyeP.add(r.scale(2.4 - 0.4 * u)).add(f.scale(0.5)).add(0, 0.05, 0);
            return new Shot(pos.add(drift(t, 0.02)), eyeP.add(f.scale(1.2)), 44, 0);
        }
        if (t < GBeats.IM_FILM_DROPS[1]) {
            return new Shot(eyeP.add(f.scale(3)).add(0, -0.5, 0), eyeP, 60, 0);
        }
        // the Purple is simply there in front of his hand: in front and to the side, it big in the frame, him behind it
        float u = ease(span(t, GBeats.IM_FILM_DROPS[1], GBeats.IM_READY));
        Vec3 centre = eyeP.add(f.scale(GBeats.purpleAhead(GMove.IMAGINARY))).add(0, -0.2, 0);
        Vec3 pos = centre.add(f.scale(4.2 - 1.0 * u)).add(r.scale(2.0)).add(0, 0.4, 0);
        return new Shot(pos.add(drift(t, 0.05)), centre.lerp(eyeP, 0.35), 64 - 4 * u, 0);
    }

    /**
     * The Hollow Nuke: rising from below him; Blue shot and circling him; Red fired up into the sky; the two meeting
     * over his head; then he aims; once let go the Purple is followed down and the blast seen from far off.
     */
    static Shot nuke(float t, float partial) {
        Vec3 f = play.flat(), r = play.right();
        Vec3 o = play.msg.origin(), d = play.msg.dir();
        Vec3 eyeP = play.eye(partial);
        Vec3 sky = GBeats.nukeRedPoint(o, d);
        if (play.fireAt >= 0) {
            float age = t - play.fireAt;
            Vec3 g = play.target;
            if (age < GBeats.NK_DROP + 2) {
                // over his shoulder, down the line it falls
                float u = ease(age / (float) GBeats.NK_DROP);
                Vec3 pos = eyeP.subtract(f.scale(4)).add(r.scale(1.4)).add(0, 2.0, 0);
                return new Shot(pos, GBeats.nukePurple(o, d, g, t, play.fireAt).lerp(g, 0.3 * u), 72 + 6 * u, 0);
            }
            // the blast from far off, the camera rocked back by it
            float u = ease(span(age, GBeats.NK_DROP, GBeats.NK_DROP + 90));
            Vec3 pos = above(g.add(f.scale(-110 - 30 * u)).add(r.scale(70)).add(0, 30 + 20 * u, 0), 4);
            float shake = (float) Math.max(0, 1 - (age - GBeats.NK_DROP) / 30.0);
            return new Shot(pos.add(drift(t * 9, 1.2 * shake)).add(drift(t, 1.0 * (1 - u))), g.add(0, 25 + 20 * u, 0), 68, 0);
        }
        if (t >= GBeats.NK_READY) {
            return null;
        }
        if (t < GBeats.NK_RISE) {
            float u = ease(span(t, 0, GBeats.NK_RISE));
            Vec3 pos = o.add(f.scale(4)).add(r.scale(1.5)).add(0, -1.0, 0);
            return new Shot(above(pos, 0.4), eyeP, 66 - 8 * u, 2);
        }
        if (t < GBeats.NK_BLUE) {
            // "Maximum output: Blue" close on him in the air
            float u = ease(span(t, GBeats.NK_RISE, GBeats.NK_BLUE));
            Vec3 pos = eyeP.add(f.scale(2.4 - 0.4 * u)).add(r.scale(0.8)).add(0, -0.2, 0);
            return new Shot(pos.add(drift(t, 0.03)), eyeP, 46, 2);
        }
        if (t < GBeats.NK_V_RED) {
            // Blue shot out and swinging round him: level with him, the camera sweeping with it
            float u = ease(span(t, GBeats.NK_BLUE, GBeats.NK_V_RED));
            double a = Math.PI * (0.15 + 0.5 * u);
            Vec3 pos = eyeP.add(f.scale(Math.cos(a) * 9)).add(r.scale(Math.sin(a) * 9)).add(0, 0.8, 0);
            return new Shot(pos, eyeP.add(0, -0.3, 0), 64, 0);
        }
        if (t < GBeats.NK_RED - 2) {
            // "Maximum output: Red"
            float u = ease(span(t, GBeats.NK_V_RED, GBeats.NK_RED));
            Vec3 pos = eyeP.add(f.scale(2.4 - 0.4 * u)).add(r.scale(-0.8)).add(0, -0.2, 0);
            return new Shot(pos.add(drift(t, 0.03)), eyeP, 46, -2);
        }
        if (t < GBeats.NK_JOIN) {
            // behind and below him, looking up as Red goes up into the sky
            float u = ease(span(t, GBeats.NK_RED - 2, GBeats.NK_JOIN));
            Vec3 pos = eyeP.subtract(f.scale(5)).add(r.scale(1.6)).add(0, -1.5, 0);
            return new Shot(pos, eyeP.lerp(sky, 0.4 + 0.5 * u), 66, -2);
        }
        // wide from the side: him small below, Blue crashing into Red, the Purple swelling over him
        float u = ease(span(t, GBeats.NK_JOIN, GBeats.NK_READY));
        Vec3 pos = eyeP.lerp(sky, 0.5).add(r.scale(-24 - 6 * u)).add(f.scale(-4));
        return new Shot(pos.add(drift(t, 0.3)), eyeP.lerp(sky, 0.6 + 0.3 * u), 66, 3 * u);
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
