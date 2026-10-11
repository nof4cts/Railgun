package com.naoya.ps.client;

import static com.naoya.ps.client.fx.ScreenFx.hit;
import static com.naoya.ps.client.fx.ScreenFx.window;

import com.naoya.ps.Net.Fx;
import com.naoya.ps.client.cine.CameraPose;
import com.naoya.ps.client.cine.Director;
import com.naoya.ps.client.cine.Film;
import com.naoya.ps.client.cine.Shot;
import com.naoya.ps.client.fx.Impact;
import com.naoya.ps.client.fx.ScreenFx;
import com.naoya.ps.server.Projection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Mach 3 Strike, from the caster's side: his eyes as the second splits into twenty-four frames (a strip of film fills
 * across the top of the screen, frame by frame), the target stuck flat in its panel, the run in three sonic booms seen
 * side on, the hit in impact frames, and the body sent flying.
 */
public final class Cine {
    public static final int[][] HIT = {{Impact.WHITEOUT, 1}, {Impact.INK, 1}, {Impact.INVERT, 1}, {Impact.LINES, 1}, {Impact.HALFTONE, 1},
            {Impact.INK, 1}, {Impact.INVERT, 1}};
    private static Film film;
    private static long start;

    private Cine() {}

    static Film ult(Fx m, long now) {
        start = now;
        Vec3 P = m.a();
        Vec3 T = m.b();
        Vec3 D = T.subtract(P).multiply(1, 0, 1);
        D = D.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : D.normalize();
        Vec3 S = new Vec3(-D.z, 0, D.x);
        Vec3 dd = D;
        int target = m.other();
        int hitAt = Projection.ULT_HIT;
        List<Shot> s = new ArrayList<>();
        // his eyes as the plan is made
        s.add(Shot.cut(0, 22, (k, t) -> pose(P.add(dd.scale(1.9 - 0.3 * k)).add(S.scale(0.25)).add(0, 1.62, 0), P.add(0, 1.55, 0), 38 - 4 * k, 0)));
        // the target, stuck flat in its frame
        s.add(Shot.cut(22, 44, (k, t) -> pose(T.subtract(dd.scale(3.6 - 0.8 * k)).add(S.scale(1.4)).add(0, 1.3, 0), T.add(0, 1.0, 0), 44, -3)));
        // the run, side on, three booms
        s.add(Shot.cut(44, hitAt, (k, t) -> {
            Vec3 mid = P.lerp(T, 0.25 + 0.6 * k);
            return pose(mid.add(S.scale(10 - 2 * k)).add(0, 1.8, 0), mid.add(0, 1.1, 0), 50, 0);
        }));
        // the hit, close
        s.add(Shot.cut(hitAt, hitAt + 12, (k, t) -> pose(T.add(S.scale(2.8)).subtract(dd.scale(1.0)).add(0, 1.2, 0), T.add(0, 1.0, 0), 56 + 8 * k, 4)));
        // and it is gone
        s.add(Shot.cut(hitAt + 12, Projection.ULT_LENGTH, (k, t) -> {
            Entity e = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(target);
            Vec3 at = e != null ? e.position().add(0, 1, 0) : T.add(dd.scale(12 * k)).add(0, 1, 0);
            return pose(T.subtract(dd.scale(3)).add(S.scale(2)).add(0, 2.4 + 2 * k, 0), at, 62, 0);
        }));
        String[][] lines = {{"6", "One second. Twenty-four frames."}, {"26", "Too slow. You're stuck in one."}, {"70", "Keep up, or stay still."}};
        Film.Card card = new Film.Card("ZEN'IN CLAN", "投射呪法", "Projection Sorcery", 0xF2D27A, 30, 44);
        film = new Film(partial -> (float) (NFx.now(partial) - start), s, Projection.ULT_LENGTH, card, lines, 6, 12);
        return film;
    }

    private static CameraPose pose(Vec3 pos, Vec3 look, double fov, double roll) {
        return new CameraPose(pos, look, (float) fov, (float) roll);
    }

    static boolean playing() {
        return film != null && Director.film() == film;
    }

    static void screen(ScreenFx fx, float partial) {
        if (!playing()) {
            return;
        }
        float t = film.clock.time(partial);
        // the plan is made in a still world
        float still = window(t, 0, 4, 40, 46);
        fx.desaturate(0.55F * still);
        fx.vignette(0x000000, 0.45F * still);
        fx.grain(0.4F);
        for (int b : Projection.ULT_BLINKS) {
            fx.punch(0.6F * hit(t, b, 3));
            fx.aberration(0.7F * hit(t, b, 3));
            fx.shake(0.5F * hit(t, b, 5));
            if (Prefs.impactFrames) {
                fx.impact(new int[][]{{Impact.INK, 1}}, t - b, null, 0xF2D27A);
            }
        }
        int h = Projection.ULT_HIT;
        if (Prefs.impactFrames) {
            fx.impact(HIT, t - h, null, 0xF2D27A);
        }
        fx.flash(0xFFFFFF, 0.9F * hit(t, h, 4));
        fx.shake(1.3F * hit(t, h, 10));
        fx.aberration(1.0F * hit(t, h, 6));
    }

    /** The plan: a strip of twenty-four frames across the top, filling one frame at a time. */
    public static void filmstrip(GuiGraphics g, float partial) {
        if (!playing()) {
            return;
        }
        float t = film.clock.time(partial);
        float a = Mth.clamp(t / 4, 0, 1) * Mth.clamp((Projection.ULT_HIT + 4 - t) / 6, 0, 1);
        if (a <= 0.02F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth();
        int bar = Math.max(0, (int) ((g.guiHeight() - w / 2.39F) / 2.0F));
        int cell = Math.max(10, Math.min(18, (w - 40) / 24));
        int x0 = w / 2 - cell * 12;
        int y0 = bar + 8;
        int lit = (int) Mth.clamp(t / 0.9F, 0, 24);
        int al = (int) (a * 255) << 24;
        g.fill(x0 - 2, y0 - 4, x0 + cell * 24 + 2, y0 + cell + 4, al | 0x0B0B0E);
        for (int i = 0; i < 24; i++) {
            int x = x0 + i * cell;
            boolean on = i < lit;
            g.fill(x + 1, y0, x + cell - 1, y0 + cell, al | (on ? 0xF3EFE4 : 0x2A2A2E));
            g.fill(x + cell / 2 - 1, y0 - 3, x + cell / 2 + 1, y0 - 1, al | 0xF3EFE4);
            g.fill(x + cell / 2 - 1, y0 + cell + 1, x + cell / 2 + 1, y0 + cell + 3, al | 0xF3EFE4);
            if (on && cell >= 12) {
                String n = Integer.toString(i + 1);
                g.drawString(font, n, x + cell / 2 - font.width(n) / 2, y0 + cell / 2 - 4, al | 0x0B0B0E, false);
            }
        }
    }
}
