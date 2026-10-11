package com.firestar.worldbreaker.client;

import static com.firestar.worldbreaker.client.fx.ScreenFx.hit;
import static com.firestar.worldbreaker.client.fx.ScreenFx.window;

import com.firestar.worldbreaker.client.cinematic.CameraPose;
import com.firestar.worldbreaker.client.cinematic.Director;
import com.firestar.worldbreaker.client.cinematic.Film;
import com.firestar.worldbreaker.client.cinematic.Shot;
import com.firestar.worldbreaker.client.fx.Impact;
import com.firestar.worldbreaker.client.fx.ScreenFx;
import com.firestar.worldbreaker.client.giant.GiantRenderer;
import com.firestar.worldbreaker.common.March;
import com.firestar.worldbreaker.config.WConfig;
import com.firestar.worldbreaker.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The caller's cinematic, fifteen seconds cut to where the giants really are: a tremor at the caller's back, the dust
 * wall on the horizon, burning eyes through the haze, a foot coming down, a giant stepping over the lens, the whole line
 * from the air, a head passing, the caller standing still as they arrive behind, and the crane up as a foot lands.
 */
public final class Films {
    static final int[][] STOMP = {{Impact.WHITEOUT, 1}, {Impact.INK, 1}, {Impact.INVERT, 1}, {Impact.LINES, 1}};
    static final int[][] STEP = {{Impact.INK, 1}, {Impact.INVERT, 1}};
    static final int[][] FINALE = {{Impact.WHITEOUT, 1}, {Impact.INVERT, 1}, {Impact.HALFTONE, 1}, {Impact.LINES, 1}, {Impact.CRIMSON, 1},
            {Impact.INK, 1}};

    private record Cue(float at, int[][] seq, Vec3 where, float power) {}

    private static final List<Cue> CUES = new ArrayList<>();
    private static Film film;

    private Films() {}

    private static final Vec3 UP = new Vec3(0, 1, 0);

    static Film march(Marching.Active a) {
        CUES.clear();
        Vec3 P = a.origin;
        Vec3 D = a.dir;
        Vec3 S = March.side(D);
        int L = March.LEAD;
        March.Giant gc = centreGiant(a), gl = neighbour(a, gc, -1), gr = neighbour(a, gc, 1);
        double H = March.HEIGHT * gc.scale();
        List<Shot> s = new ArrayList<>();

        // 1. the tremor: over the caller's shoulder toward an empty horizon
        s.add(Shot.cut(0, 36, (k, t) -> pose(P.subtract(D.scale(2.2)).add(S.scale(0.9)).add(0, 1.45, 0), P.add(D.scale(60)).add(0, 9 + 3 * k, 0),
                50 - 6 * k, 0)));
        // 2. the dust wall: down at the dirt, a telephoto on the horizon as it boils up
        s.add(Shot.cut(36, 70, (k, t) -> pose(P.add(D.scale(4)).add(S.scale(-1)).add(0, 2.2, 0), P.add(D.scale(95)).add(0, 14 + 8 * k, 0),
                30 - 6 * k, 0)));
        // 3. eyes through the haze
        s.add(Shot.cut(70, 102, (k, t) -> pose(P.add(0, 1.8, 0), at(a, gc, t - L).add(0, H * (0.84 + 0.04 * k), 0), 15 - 4 * k, 0)));
        // 4. a foot coming down, from the ground beside it
        int t4 = nextFall(gl, 114 - L);
        Vec3 foot4 = footAt(a, gl, t4);
        t4 += L;
        Vec3 heading = March.heading(D, gl);
        s.add(Shot.cut(102, 134, (k, t) -> pose(foot4.add(March.side(heading).scale(16 - 3 * k)).add(heading.scale(10)).add(0, 0.7, 0),
                foot4.add(0, 4 + 10 * k, 0), 44, -4)));
        CUES.add(new Cue(t4, STOMP, foot4, 1.0F));
        // 5. it steps over the lens: worm's-eye from right in its path, looking up at it
        Vec3 path5 = at(a, gc, 182 - L);
        s.add(Shot.cut(134, 172, (k, t) -> pose(path5.add(S.scale(3)).add(0, 0.6, 0), at(a, gc, t - L).add(0, H * 0.9, 0), 78, 8 * k - 4)));
        for (int t5 = nextFall(gc, 134 - L); t5 + L < 172; t5 += March.HALF) {
            CUES.add(new Cue(t5 + L, t5 + L + March.HALF >= 172 ? STOMP : STEP, footAt(a, gc, t5), 0.8F));
        }
        // 6. the whole line from the air, the flattened land behind it
        s.add(Shot.cut(172, 208, (k, t) -> {
            Vec3 mid = line(a, t - L);
            return pose(mid.add(S.scale(95)).add(0, 72, 0).add(D.scale(-30 + 40 * k)), mid.add(0, 14, 0).add(D.scale(-12)), 54, 0);
        }));
        // 7. a head going by: the eyes, the steam, the jaw working
        s.add(Shot.cut(208, 238, (k, t) -> {
            Vec3 c = at(a, gr, t - L);
            Vec3 hd = March.heading(D, gr);
            Vec3 e = GiantRenderer.eyes(c, hd, March.HEIGHT * gr.scale());
            return pose(e.add(hd.scale(15 - 4 * k)).add(March.side(hd).scale(-11)).add(0, -3, 0), e.subtract(0, 1, 0), 42, 3);
        }));
        // 8. the caller, unmoved, as they arrive at his back
        s.add(Shot.cut(238, 272, (k, t) -> pose(P.subtract(D.scale(6.5 - 1.5 * k)).add(S.scale(1.6)).add(0, 1.0, 0), P.add(D.scale(2)).add(0, 3.5 + 3 * k, 0),
                62, 0)));
        // 9. a foot lands beside him: crane up into the sky as the dust rolls out
        int t9 = nextFall(gc, 268 - L);
        Vec3 foot9 = footAt(a, gc, t9);
        t9 += L;
        s.add(Shot.cut(272, March.FILM, (k, t) -> pose(P.add(0, 6 + 70 * k * k, 0).subtract(D.scale(5)).add(S.scale(4)), P.add(D.scale(4)), 58 + 12 * k, 0)));
        CUES.add(new Cue(Math.max(t9, 274), FINALE, foot9, 1.4F));

        String[][] lines = {{"4", "...Do you feel it? The ground is shaking."}, {"74", "They're coming."}, {"176", "Everything in their path..."},
                {"240", "Nothing will be left standing."}, {"276", "Walk."}};
        Film.Card card = new Film.Card("WORLDBREAKER", "終焉の行進", "The Endless March", 0xFF8A2A, 240, 58);
        film = new Film(partial -> (float) (Marching.time(a, partial) + L), s, March.FILM, card, lines, March.BLEND_IN, March.BLEND_OUT);
        return film;
    }

    private static CameraPose pose(Vec3 pos, Vec3 look, double fov, double roll) {
        return new CameraPose(pos, look, (float) fov, (float) roll);
    }

    /** The giant of the front rank that walks straight over the caller. */
    static March.Giant centreGiant(Marching.Active a) {
        March.Giant best = a.giants[0];
        for (March.Giant g : a.giants) {
            if (g.back() < 10 && Math.abs(g.lateral()) < Math.abs(best.lateral())) {
                best = g;
            }
        }
        return best;
    }

    private static March.Giant neighbour(Marching.Active a, March.Giant c, int side) {
        March.Giant best = c;
        double want = c.lateral() + side * 17;
        for (March.Giant g : a.giants) {
            if (g.back() < 10 && Math.abs(g.lateral() - want) < Math.abs(best.lateral() - want)) {
                best = g;
            }
        }
        return best;
    }

    private static Vec3 at(Marching.Active a, March.Giant g, double t) {
        Vec3 p = March.position(a.origin, a.dir, g, t);
        return new Vec3(p.x, a.ground[g.index()], p.z);
    }

    private static Vec3 line(Marching.Active a, double t) {
        Vec3 p = a.origin.add(a.dir.scale(March.START_DIST - March.walked(t, 0)));
        return new Vec3(p.x, a.origin.y, p.z);
    }

    private static int nextFall(March.Giant g, int from) {
        int r = (from + g.phase()) % March.HALF;
        return r == 0 ? from : from + March.HALF - r;
    }

    private static Vec3 footAt(Marching.Active a, March.Giant g, int t) {
        return March.foot(at(a, g, t), March.heading(a.dir, g), g, March.leftFoot(t, g));
    }

    /** Sounds on the film's beats. */
    static void tick() {
        if (film == null || Director.film() != film) {
            return;
        }
        int t = (int) Math.floor(film.clock.time(0));
        for (int b : new int[]{6, 30, 50, 66, 80, 92, 102}) {
            if (t == b) {
                Marching.ui(ModSounds.HEARTBEAT.get(), 1.0F, 1.0F + (b - 6) / 400.0F);
            }
        }
        for (Cue c : CUES) {
            if (t == (int) c.at) {
                Marching.ui(ModSounds.IMPACT.get(), Math.min(1.0F, 0.6F + 0.3F * c.power), 0.9F);
            }
        }
        if (t == 172) {
            Marching.ui(ModSounds.WIND.get(), 1.0F, 1.0F);
        }
    }

    /** The film's look: a hush before, grain, and the impact frames on the cues. */
    static void screen(ScreenFx fx, float partial) {
        if (film == null || Director.film() != film) {
            return;
        }
        float t = film.clock.time(partial);
        float hush = window(t, 0, 6, 64, 72);
        fx.desaturate(0.5F * hush);
        fx.vignette(0x000000, 0.5F * hush);
        fx.grain(0.35F);
        for (int b : new int[]{6, 30, 50, 66, 80, 92, 102}) {
            fx.punch(0.25F * hit(t, b, 3));
        }
        for (Cue c : CUES) {
            if (WConfig.CLIENT.impactFrames.get()) {
                fx.impact(c.seq, t - c.at, c.where, 0xFF8A2A);
            }
            fx.flash(0xFFE2B0, 0.7F * c.power * hit(t, c.at, 4));
            fx.shake(c.power * hit(t, c.at, 10));
            fx.aberration(0.8F * c.power * hit(t, c.at, 5));
        }
    }

    static boolean playing() {
        return film != null && Director.film() == film;
    }

}
