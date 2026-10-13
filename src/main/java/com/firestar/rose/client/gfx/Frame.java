package com.firestar.rose.client.gfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Everything Requiem Rose draws this frame, as shapes in the world: cuts of light (and the ink laid under them), rays,
 * shockwaves, flares, sparks, smoke, rifts in space, and the bending of space round them. The techniques describe what
 * is there from their own clock each frame; {@link Canvas} turns it into geometry and light.
 */
public final class Frame {
    public final Vec3 cam;
    public final Vec3 look, up, left;
    public final float time;
    final Matrix4f viewProj;

    final List<Arc> arcs = new ArrayList<>();
    final List<Arc> inks = new ArrayList<>();
    final List<Beam> beams = new ArrayList<>();
    final List<Ring> rings = new ArrayList<>();
    final List<Dot> dots = new ArrayList<>();
    final List<Dot> smokes = new ArrayList<>();
    final List<Rift> rifts = new ArrayList<>();
    final List<Arc> warpArcs = new ArrayList<>();
    final List<Ring> warpRings = new ArrayList<>();

    Frame(Vec3 cam, Vec3 look, Vec3 up, Vec3 left, float time, Matrix4f viewProj) {
        this.cam = cam;
        this.look = look;
        this.up = up;
        this.left = left;
        this.time = time;
        this.viewProj = viewProj;
    }

    record Arc(Vec3 c, Vec3 u, Vec3 v, double r, double a0, double a1, double w, float fade, float heat, float seed, boolean blade) {}

    record Beam(Vec3 a, Vec3 b, double radius, float fade, float heat, float seed) {}

    record Ring(Vec3 c, Vec3 n, double r, double w, float fade, float heat, float seed, float sharp) {}

    /** kind: 0 flare, 1 spark (dir set), 2 mote, 3 smoke. */
    record Dot(Vec3 p, Vec3 dir, double size, double length, float fade, float heat, float seed, int kind) {}

    record Rift(Vec3 c, Vec3 axis, Vec3 across, double length, double width, float open, float fade, float seed) {}

    public boolean empty() {
        return arcs.isEmpty() && inks.isEmpty() && beams.isEmpty() && rings.isEmpty() && dots.isEmpty() && smokes.isEmpty() && rifts.isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ the vocabulary

    /**
     * A cut of light in the plane (u, v) round {@code c}: from angle a0 (degrees, 0 along u) toward a1, drawn on as far as
     * {@code reveal}; its outer rim at radius r is the edge, {@code w} its greatest thickness. Fade 1..0 burns it away.
     */
    public void cut(Vec3 c, Vec3 u, Vec3 v, double r, double a0, double a1, double w, float reveal, float fade, float heat, float seed) {
        if (reveal <= 0.001F || fade <= 0.001F) {
            return;
        }
        double end = a0 + (a1 - a0) * Math.min(1.0F, reveal);
        arcs.add(new Arc(c, u.normalize(), v.normalize(), r, a0, end, w, fade, heat, seed, false));
        warpArcs.add(new Arc(c, u.normalize(), v.normalize(), r, a0, end, w * 1.6, fade, heat, seed, false));
    }

    /** The same cut with a black shadow laid beneath it: the rose-and-black look of every Requiem slash. */
    public void slash(Vec3 c, Vec3 u, Vec3 v, double r, double a0, double a1, double w, float reveal, float fade, float heat, float seed) {
        if (reveal <= 0.001F || fade <= 0.001F) {
            return;
        }
        double end = a0 + (a1 - a0) * Math.min(1.0F, reveal);
        // the shadow trails along the inside of the cut: rose light on the edge, black behind it
        inks.add(new Arc(c, u.normalize(), v.normalize(), r - w * 0.45, a0, end, w * 1.15, fade, heat, seed, false));
        cut(c, u, v, r, a0, a1, w, reveal, fade, heat, seed);
    }

    /** The scythe's blade: a steady crystal crescent of energy. */
    public void blade(Vec3 c, Vec3 u, Vec3 v, double r, double a0, double a1, double w, float fade, float heat) {
        arcs.add(new Arc(c, u, v, r, a0, a1, w, fade, heat, 0.3F, true));
    }

    public void bladeInk(Vec3 c, Vec3 u, Vec3 v, double r, double a0, double a1, double w, float fade) {
        inks.add(new Arc(c, u, v, r, a0, a1, w, fade, 0, 0.7F, true));
    }

    public void beam(Vec3 a, Vec3 b, double radius, float fade, float heat, float seed) {
        if (fade > 0.001F && a.distanceToSqr(b) > 1.0E-4) {
            beams.add(new Beam(a, b, radius, fade, heat, seed));
        }
    }

    public void ring(Vec3 c, Vec3 n, double r, double w, float fade, float heat, float seed, float sharp) {
        if (fade > 0.001F && r > 0.01) {
            rings.add(new Ring(c, n.normalize(), r, w, fade, heat, seed, sharp));
            warpRings.add(new Ring(c, n.normalize(), r, w * 1.2, fade * 0.8F, heat, seed, sharp));
        }
    }

    /** A ring that bends space but gives no light: the air thrown outward by a blow. */
    public void shock(Vec3 c, Vec3 n, double r, double w, float amount) {
        if (amount > 0.001F && r > 0.01) {
            warpRings.add(new Ring(c, n.normalize(), r, w, amount, 0, 0, 1));
        }
    }

    public void flare(Vec3 p, double size, float fade, float heat) {
        if (fade > 0.001F) {
            dots.add(new Dot(p, null, size, 0, fade, heat, 0, 0));
        }
    }

    public void spark(Vec3 p, Vec3 dir, double length, double width, float fade, float heat) {
        if (fade > 0.001F) {
            dots.add(new Dot(p, dir, width, length, fade, heat, 0, 1));
        }
    }

    public void mote(Vec3 p, double size, float fade, float heat) {
        if (fade > 0.001F) {
            dots.add(new Dot(p, null, size, 0, fade, heat, 0, 2));
        }
    }

    public void smoke(Vec3 p, double size, float fade, float seed) {
        if (fade > 0.001F) {
            smokes.add(new Dot(p, null, size, 0, fade, 0, seed, 3));
        }
    }

    /** A streak of light: a beam with no body to speak of, sharp at both ends. */
    public void line(Vec3 a, Vec3 b, double width, float fade, float heat) {
        beam(a, b, width, fade, heat, 0.5F);
    }

    /** A tear in space along {@code axis} (length), opened {@code open} (0..1) to at most {@code width} across. */
    public void rift(Vec3 c, Vec3 axis, Vec3 across, double length, double width, float open, float fade, float seed) {
        if (fade > 0.001F) {
            rifts.add(new Rift(c, axis.normalize(), across.normalize(), length, width, open, fade, seed));
        }
    }

    /** Where a point in the world lands on the screen (0..1, y up), or null if behind the camera. */
    public float[] screen(Vec3 world) {
        Vec3 p = world.subtract(cam);
        Vector4f v = new Vector4f((float) p.x, (float) p.y, (float) p.z, 1.0F);
        viewProj.transform(v);
        if (v.w <= 0.01F) {
            return null;
        }
        return new float[]{v.x / v.w * 0.5F + 0.5F, v.y / v.w * 0.5F + 0.5F};
    }
}
