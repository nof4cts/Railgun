package com.geto.csm.client;

import com.geto.csm.client.Mesh.Frame;
import com.geto.csm.entity.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The eight cursed spirits, sculpted in code. Every coordinate is relative to the entity's
 * interpolated position. {@link #draw} runs for both the ink-outline and the fill pass;
 * {@link #glow} adds the additive light layer (eyes, embers, foxfire, bioluminescence).
 */
public final class CurseModels {
    static final Vec3 UP = Draw.Y;

    static double time() {
        return Vfx.now() / 1000.0;
    }

    static float yaw(LivingEntity e, float partial) {
        return Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
    }

    static Vec3 tangent(Vec3[] p, int i) {
        Vec3 a = p[Math.max(0, i - 1)], b = p[Math.min(p.length - 1, i + 1)];
        Vec3 t = b.subtract(a);
        return t.lengthSqr() < 1.0e-10 ? Draw.Z : t.normalize();
    }

    static Vec3 upOf(Vec3 tan) {
        Vec3 u = UP.subtract(tan.scale(UP.dot(tan)));
        return u.lengthSqr() < 1.0e-6 ? Draw.perp(tan) : u.normalize();
    }

    public static double inkWidth(CurseEntity e) {
        return switch (e.kind()) {
            case WYRM, WORM, NAMAZU -> 0.06;
            case RAY, TAMAMO -> 0.05;
            case CENTIPEDE -> 0.018;
            case PYRE -> 0.035;
            case KUCHISAKE -> 0.022;
        };
    }

    public static void draw(CurseEntity e, Vec3 origin, float partial) {
        switch (e.kind()) {
            case WYRM -> wyrm((WyrmEntity) e, origin, partial);
            case WORM -> worm((WormEntity) e, partial);
            case RAY -> ray((RayEntity) e, partial);
            case CENTIPEDE -> centipede((CentipedeEntity) e, origin, partial);
            case PYRE -> pyre((PyreEntity) e, partial);
            case KUCHISAKE -> kuchisake((KuchisakeEntity) e, partial);
            case TAMAMO -> tamamo((TamamoEntity) e, partial);
            case NAMAZU -> namazu((NamazuEntity) e, partial);
        }
    }

    // ══════════════════════════════ GLOOMSCALE WYRM ══════════════════════════════

    static double wyrmRadius(double u) {
        if (u < 0.22) return Mth.lerp(u / 0.22, 0.62, 0.95);
        return 0.06 + 0.89 * Math.pow(1 - (u - 0.22) / 0.78, 0.85);
    }

    static Vec3[] wyrmSpine(WyrmEntity e, Vec3 origin, float partial) {
        Frame f = Frame.yaw(yaw(e, partial));
        Vec3 headW = origin.add(0, 1.0, 0);
        int n = 44;
        Vec3[] raw = SpineTrail.sample(e.getId(), headW.subtract(f.fwd.scale(1.1)), f.fwd.scale(-1), n, 0.55);
        double t = time();
        float sweep = CurseAnim.pulse(e.getId(), 2, 800);
        Vec3[] out = new Vec3[n + 1];
        out[0] = new Vec3(0, 1.0, 0);
        for (int i = 0; i < n; i++) {
            Vec3 tan = raw[Math.min(n - 1, i + 1)].subtract(raw[Math.max(0, i - 1)]);
            Vec3 side = UP.cross(tan);
            side = side.lengthSqr() < 1.0e-8 ? f.side : side.normalize();
            double amp = 0.12 + i * 0.016;
            double w = Math.sin(t * 2.4 - i * 0.42) * amp + sweep * Math.sin(i * 0.22) * 3.0 * (i / (double) n);
            double v = Math.cos(t * 1.9 - i * 0.37) * amp * 0.5;
            out[i + 1] = raw[i].subtract(origin).add(side.scale(w)).add(0, v, 0);
        }
        return out;
    }

    static void wyrm(WyrmEntity e, Vec3 origin, float partial) {
        Vec3[] p = wyrmSpine(e, origin, partial);
        int n = p.length;
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = wyrmRadius(i / (double) (n - 1));
        Mesh.tube(p, r, 14, (u, nn) -> {
            int band = ((int) (u * 90)) % 2;
            if (nn.y > 0.12) return band == 0 ? 0xFF1C1733 : 0xFF28204A;
            if (nn.y < -0.3) return band == 0 ? 0xFFD8CCAE : 0xFFBCAE8E;
            return 0xFF2F2749;
        });
        // dorsal blades, swept toward the tail
        for (int i = 3; i < n - 6; i += 2) {
            double u = i / (double) (n - 1);
            Vec3 tan = tangent(p, i), up = upOf(tan);
            Vec3 base = p[i].add(up.scale(r[i] * 0.82));
            Vec3 tip = base.add(up.scale(0.12 + 0.6 * (1 - u))).add(tan.scale(0.32));
            Mesh.spike(base, tip, 0.13 * (1.1 - u), 0xFFE6DCC6);
        }
        // four short clawed legs
        int[] legAt = {8, 25};
        double t = time();
        for (int li = 0; li < legAt.length; li++) {
            int i = legAt[li];
            Vec3 tan = tangent(p, i), up = upOf(tan), side = up.cross(tan).normalize();
            for (int s = -1; s <= 1; s += 2) {
                double paddle = Math.sin(t * 2.2 + li * 1.7 + s) * 0.15;
                Vec3 sh = p[i].add(side.scale(s * r[i] * 0.75)).add(up.scale(-r[i] * 0.2));
                Vec3 el = sh.add(side.scale(s * 0.65)).add(up.scale(-0.35)).add(tan.scale(-0.25 + paddle));
                Vec3 ft = el.add(up.scale(-0.55)).add(tan.scale(-0.35 + paddle));
                Mesh.limb(new Vec3[]{sh, el, ft}, 0.24, 0.11, 9, 0xFF2A2240);
                for (int c = -1; c <= 1; c++) {
                    Vec3 cb = ft.add(side.scale(s * c * 0.08));
                    Mesh.spike(cb, cb.add(tan.scale(-0.3)).add(up.scale(-0.12)).add(side.scale(s * c * 0.1)), 0.045, 0xFFE6DCC6);
                }
            }
        }
        wyrmHead(e, p);
    }

    static void wyrmHead(WyrmEntity e, Vec3[] p) {
        Vec3 H = p[0];
        Vec3 fwdH = H.subtract(p[2]);
        Frame hf = Frame.of(fwdH.lengthSqr() < 1.0e-6 ? Draw.Z : fwdH, UP);
        double t = time();
        double jaw = 0.1 + 0.05 * Math.sin(t * 2) + 0.7 * Math.max(CurseAnim.pulse(e.getId(), 1, 420), CurseAnim.pulse(e.getId(), 3, 1100));
        Mesh.blob(H.add(hf.l(0, 0.12, 0.2)), hf, 0.62, 0.48, 0.95, (u, n) -> n.y > 0 ? 0xFF221B38 : 0xFF3A2F50);
        Mesh.blob(H.add(hf.l(0, 0.02, 1.12)), hf, 0.42, 0.3, 0.78, (u, n) -> n.y > 0 ? 0xFF241D3C : 0xFF3C3052);
        // nostrils
        for (int s = -1; s <= 1; s += 2) Mesh.ball(H.add(hf.l(s * 0.18, 0.22, 1.75)), 0.06, 0xFF0C0814);
        // lower jaw on a hinge
        Vec3 pivot = H.add(hf.l(0, -0.22, 0.1));
        Vec3 jd = hf.fwd.scale(Math.cos(jaw)).subtract(hf.up.scale(Math.sin(jaw)));
        Frame jf = Frame.of(jd, hf.up);
        Mesh.blob(pivot.add(jd.scale(0.85)), jf, 0.36, 0.17, 0.85, (u, n) -> n.dot(jf.up) > 0 ? 0xFF5A1426 : 0xFFCFC2A4);
        // teeth
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 6; k++) {
                double z = 0.55 + k * 0.22;
                Vec3 ub = H.add(hf.l(s * (0.34 - k * 0.03), -0.18, z));
                Mesh.spike(ub, ub.add(hf.up.scale(-0.22 + k * 0.015)), 0.045, 0xFFF4EEDC);
                Vec3 lb = pivot.add(jd.scale(0.35 + k * 0.2)).add(jf.side.scale(s * (0.28 - k * 0.025))).add(jf.up.scale(0.12));
                Mesh.spike(lb, lb.add(jf.up.scale(0.18)), 0.04, 0xFFF4EEDC);
            }
        }
        // eyes (emissive amber), brows, horns, whiskers
        for (int s = -1; s <= 1; s += 2) {
            Mesh.blob(H.add(hf.l(s * 0.42, 0.3, 0.6)), hf, 0.1, 0.07, 0.16, Mesh.emissive(0xFFB13B));
            Mesh.spike(H.add(hf.l(s * 0.4, 0.42, 0.55)), H.add(hf.l(s * 0.62, 0.62, 0.1)), 0.08, 0xFFE6DCC6);
            Mesh.curve(H.add(hf.l(s * 0.3, 0.38, -0.1)), H.add(hf.l(s * 0.45, 0.95, -1.0)), H.add(hf.l(s * 0.75, 0.45, -2.3)),
                    10, 0.17, 0.015, 8, (u, n) -> u > 0.75 ? 0xFFFFF4E0 : 0xFFE0D4BC);
            double sway = Math.sin(t * 2.3 + s) * 0.35;
            Mesh.curve(H.add(hf.l(s * 0.38, -0.02, 1.55)), H.add(hf.l(s * 1.3, 0.2 + sway, 0.9)), H.add(hf.l(s * 1.2, -0.3 - sway, -1.6)),
                    14, 0.06, 0.012, 6, Mesh.emissive(0xFF7A2A));
        }
    }

    // ══════════════════════════════ MAW BURROWER ══════════════════════════════

    static Vec3[] wormSpine(WormEntity e, float partial, Frame f) {
        double age = (e.tickCount + partial) / 20.0;
        double emerge = Ease.outBack((float) Mth.clamp(age / 1.1, 0, 1));
        double H = 6.2 * emerge;
        double t = time();
        double sway = Math.sin(t * 1.3) * 0.35;
        float lunge = CurseAnim.pulse(e.getId(), 1, 420);
        float spit = CurseAnim.pulse(e.getId(), 3, 500);
        Vec3 base = new Vec3(0, -3.5, 0);
        Vec3 head = f.l(sway, H + spit * 0.8, 1.6 + lunge * 1.6);
        return Mesh.bezier3(base, f.l(-sway * 0.5, H * 0.35, -0.4), f.l(sway, H * 0.8, 0.6), head, 22);
    }

    static void worm(WormEntity e, float partial) {
        Frame f = Frame.yaw(yaw(e, partial));
        Vec3[] p = wormSpine(e, partial, f);
        int n = p.length;
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = Mth.lerp(i / (double) (n - 1), 1.42, 1.15) * (1 + 0.06 * Math.sin(i * 2.6));
        Mesh.tube(p, r, 16, (u, nn) -> {
            if (nn.dot(f.fwd) > 0.4) return 0xFFB79BB0;
            return ((int) (u * 44)) % 2 == 0 ? 0xFF5B3A62 : 0xFF48304F;
        });
        Vec3 H = p[n - 1];
        Vec3 tn = p[n - 1].subtract(p[n - 2]).normalize();
        Frame mf = Frame.of(tn, f.fwd.scale(-1));
        float open = 0.62f + 0.38f * Math.max(CurseAnim.pulse(e.getId(), 2, 700), Math.max(CurseAnim.pulse(e.getId(), 3, 500), CurseAnim.pulse(e.getId(), 1, 420)));
        double R = 1.15;
        Mesh.torus(H, mf.side, mf.up, R, 0.25, 26, 0xFF7A4E80);
        Mesh.ellipsoid(H.subtract(tn.scale(0.25)), mf.side.scale(R * 0.96), mf.up.scale(R * 0.96), tn.scale(0.12), 6, 18, (u, nn) -> Mesh.emissive(0x2A0610));
        for (int k = 0; k < 3; k++) {
            int count = 16 - k * 3;
            double rr = R * (0.95 - k * 0.2) * (0.55 + 0.45 * open);
            for (int i = 0; i < count; i++) {
                double a = 2 * Math.PI * (i + k * 0.5) / count;
                Vec3 radial = mf.side.scale(Math.cos(a)).add(mf.up.scale(Math.sin(a)));
                Vec3 b = H.add(tn.scale(-k * 0.28)).add(radial.scale(rr));
                Mesh.spike(b, b.subtract(radial.scale(0.36 - k * 0.06)).add(tn.scale(0.2)), 0.085 - k * 0.012, 0xFFEDE6D2);
            }
        }
        int ground = Vfx.groundColor(e.position());
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + 0.3;
            double rr = 1.8 + Draw.hash(i * 7L) * 0.5;
            Mesh.ball(new Vec3(Math.cos(a) * rr, -0.05 + Draw.hash(i * 3L) * 0.15, Math.sin(a) * rr), 0.28 + Draw.hash(i) * 0.25, Draw.scaleRgb(ground, 0.8f));
        }
    }

    // ══════════════════════════════ VEIL RAY ══════════════════════════════

    static void ray(RayEntity e, float partial) {
        Frame f = Frame.yaw(yaw(e, partial)).pitch(-Math.toRadians(e.getViewXRot(partial)) * 0.5);
        double t = time();
        Vec3 c = new Vec3(0, 0.45 + Math.sin(t * 1.5) * 0.12, 0);
        double w = Math.sin(t * 2.2) * 0.32 + CurseAnim.pulse(e.getId(), 1, 600) * 0.6 * Math.sin(t * 20);
        Mesh.Paint skin = (u, n) -> n.dot(f.up) > 0 ? (((int) ((n.x + n.z) * 6)) % 2 == 0 ? 0xFF0F3A44 : 0xFF13495A) : 0xFFE6E0C8;
        Mesh.blob(c, f, 1.25, 0.42, 1.65, skin);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 dir = f.side.scale(s * Math.cos(w)).add(f.up.scale(Math.sin(w)));
            Vec3 wc = c.add(dir.scale(2.0)).subtract(f.fwd.scale(0.35));
            Vec3 nrm = f.fwd.cross(dir).normalize();
            if (nrm.dot(f.up) < 0) nrm = nrm.scale(-1);
            Vec3 fn = nrm;
            Mesh.ellipsoid(wc, dir.scale(2.15), nrm.scale(0.16), f.fwd.scale(1.15), 8, 16,
                    (u, n) -> n.dot(fn) > 0 ? 0xFF0F3A44 : 0xFFE6E0C8);
            Mesh.spike(wc.add(dir.scale(1.85)), wc.add(dir.scale(2.75)).subtract(f.fwd.scale(0.55)), 0.13, 0xFF0F3A44);
            Mesh.curve(c.add(f.l(s * 0.55, 0, 1.45)), c.add(f.l(s * 0.8, -0.1, 2.25)), c.add(f.l(s * 0.42, -0.4, 2.35)), 8, 0.15, 0.04, 8, 0xFF0F3A44);
            Mesh.ball(c.add(f.l(s * 0.92, 0.12, 1.1)), 0.09, 0xFF0A1418);
            Mesh.ball(c.add(f.l(s * 0.96, 0.16, 1.14)), 0.028, Mesh.emissive(0x9FFFF6));
            for (int k = 0; k < 5; k++) {
                Vec3 spot = wc.add(dir.scale(-0.9 + k * 0.55)).add(f.fwd.scale(Math.sin(k * 2.1) * 0.4)).add(nrm.scale(0.155));
                Mesh.ball(spot, 0.055, Mesh.emissive(0x6FFFF0));
            }
        }
        for (int k = 0; k < 8; k++) {
            double x = Math.sin(k * 1.7) * 0.8, z = Math.cos(k * 2.3) * 1.1;
            double y = 0.42 * Math.sqrt(Math.max(0, 1 - (x / 1.25) * (x / 1.25) - (z / 1.65) * (z / 1.65)));
            Mesh.ball(c.add(f.l(x, y, z)), 0.05, Mesh.emissive(0x6FFFF0));
        }
        Vec3[] tail = new Vec3[14];
        for (int i = 0; i < 14; i++) {
            tail[i] = c.subtract(f.fwd.scale(1.5 + i * 0.33)).add(f.side.scale(Math.sin(t * 3 - i * 0.5) * 0.18 * i / 13.0));
        }
        Mesh.limb(tail, 0.14, 0.012, 7, 0xFF0C2E36);
    }

    // ══════════════════════════════ CINDER CENTIPEDES ══════════════════════════════

    static void centipede(CentipedeEntity e, Vec3 origin, float partial) {
        Frame f = Frame.yaw(yaw(e, partial));
        float rear = CurseAnim.pulse(e.getId(), 1, 320);
        int n = 14;
        Vec3[] raw = SpineTrail.sample(e.getId(), origin.add(0, 0.2, 0).add(f.fwd.scale(0.3)), f.fwd.scale(-1), n, 0.17);
        Vec3[] p = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double lift = i < 4 ? rear * 0.28 * (4 - i) / 4.0 : 0;
            p[i] = raw[i].subtract(origin).add(0, lift, 0);
        }
        double t = time();
        for (int i = 0; i < n; i++) {
            double r = 0.16 * (1 - i / 22.0);
            Frame sf = Frame.of(tangent(p, i).scale(-1), UP);
            Mesh.blob(p[i], sf, r, r * 0.66, r * 0.9, (u, nn) -> nn.y > 0.55 ? 0xFF243224 : 0xFF141216);
            if (i == 0 || i >= n - 1) continue;
            for (int s = -1; s <= 1; s += 2) {
                double ph = t * 16 + i * 0.95 + (s > 0 ? Math.PI : 0);
                double lift = Math.max(0, Math.sin(ph)) * 0.07;
                Vec3 hip = p[i].add(sf.side.scale(s * r * 0.9));
                Vec3 knee = hip.add(sf.side.scale(s * 0.18)).add(0, 0.12 + lift, 0).add(sf.fwd.scale(0.05 * Math.cos(ph)));
                Vec3 foot = hip.add(sf.side.scale(s * 0.34)).add(sf.fwd.scale(0.09 * Math.cos(ph)));
                foot = new Vec3(foot.x, Math.min(foot.y, 0.02 + lift * 0.5), foot.z);
                Mesh.limb(new Vec3[]{hip, knee, foot}, 0.034, 0.016, 5, 0xFF201C1E);
                Mesh.ball(foot, 0.024, Mesh.emissive(0x9CFF3A));
            }
        }
        Frame hf = Frame.of(tangent(p, 0).scale(-1), UP);
        Vec3 H = p[0];
        for (int s = -1; s <= 1; s += 2) {
            double snap = rear * 0.06;
            Mesh.curve(H.add(hf.l(s * 0.08, -0.02, 0.12)), H.add(hf.l(s * (0.2 + snap), -0.03, 0.3)), H.add(hf.l(s * (0.04 + snap), -0.04, 0.37)),
                    6, 0.035, 0.005, 5, (u, nn) -> u > 0.7 ? Mesh.emissive(0x9CFF3A) : 0xFF3A1C10);
            double sw = Math.sin(t * 5 + s) * 0.06;
            Mesh.curve(H.add(hf.l(s * 0.06, 0.08, 0.1)), H.add(hf.l(s * 0.25, 0.42 + sw, 0.3)), H.add(hf.l(s * 0.42, 0.3 - sw, 0.62)),
                    8, 0.02, 0.004, 4, 0xFF201C1E);
            Mesh.ball(H.add(hf.l(s * 0.08, 0.07, 0.12)), 0.022, Mesh.emissive(0x9CFF3A));
        }
    }

    // ══════════════════════════════ PYRE WRAITH ══════════════════════════════

    static float heatK(PyreEntity e) {
        return (PyreEntity.heat(e.tickCount) - 120f) / 2880f;
    }

    static int molten(float k) {
        return Draw.lerp(0xFFFF4A10, 0xFFFFF0C0, k);
    }

    static void pyre(PyreEntity e, float partial) {
        Frame f = Frame.yaw(yaw(e, partial));
        float k = heatK(e);
        int mc = molten(k);
        double pos = e.walkAnimation.position(partial);
        double spd = Math.min(1f, e.walkAnimation.speed(partial));
        double swing = Math.sin(pos * 0.66) * spd * 0.6;
        float flare = CurseAnim.pulse(e.getId(), 1, 650);
        Mesh.Paint ash = (u, n) -> {
            double v = Math.sin(n.x * 11 + n.y * 7.3 + u * 9) * Math.sin(n.z * 9.1 - n.y * 5.7 + u * 4);
            if (Math.abs(v) < 0.06 + k * 0.1) return Mesh.emissive(mc);
            return n.y > 0.4 ? 0xFF2A2420 : 0xFF1E1A18;
        };
        for (int s = -1; s <= 1; s += 2) {
            double ph = s * swing;
            Vec3 hip = f.l(s * 0.24, 1.15, -0.05);
            Vec3 knee = f.l(s * 0.3, 0.68, 0.28 + ph * 0.4);
            Vec3 ankle = f.l(s * 0.28, 0.24, -0.12 + ph * 0.2);
            Vec3 toe = f.l(s * 0.28, 0.03, 0.28 + ph * 0.35);
            Mesh.limb(new Vec3[]{hip, knee, ankle, toe}, 0.16, 0.07, 9, ash);
        }
        Mesh.blob(f.l(0, 1.15, 0), f, 0.32, 0.2, 0.26, ash);
        Frame tf = f.pitch(-0.45 - flare * 0.25);
        Vec3 torso = f.l(0, 1.62, 0.18);
        Mesh.blob(torso, tf, 0.46, 0.62, 0.36, ash);
        for (int j = 0; j < 4; j++) Mesh.blob(torso.add(tf.l(0, 0.3 - j * 0.18, 0.3)), tf, 0.36 - j * 0.03, 0.045, 0.08, 0xFF34302C);
        Vec3 neckTop = f.l(0, 2.22, 0.58 + flare * 0.1);
        Mesh.limb(new Vec3[]{torso.add(tf.l(0, 0.5, 0.05)), neckTop}, 0.15, 0.12, 8, ash);
        Frame hf = f.pitch(-0.2 + flare * 0.4);
        Vec3 H = neckTop.add(hf.l(0, 0.12, 0.06));
        Mesh.blob(H, hf, 0.26, 0.3, 0.3, ash);
        Mesh.blob(H.add(hf.l(0, 0.02, 0.28)), hf, 0.035, 0.17, 0.025, Mesh.emissive(mc));
        for (int s = -1; s <= 1; s += 2) {
            double sw = -swing * s;
            Vec3 sh = f.l(s * 0.5, 1.98, 0.22);
            Vec3 el = f.l(s * 0.66, 1.4 + flare * 0.6, 0.42 + sw * 0.3 + flare * 0.5);
            Vec3 wr = f.l(s * 0.56, 0.86 + flare * 1.0, 0.52 + sw * 0.2 + flare * 0.9);
            Mesh.limb(new Vec3[]{sh, el, wr}, 0.14, 0.07, 9, ash);
            for (int c = -1; c <= 1; c++) {
                Vec3 cb = wr.add(f.side.scale(s * c * 0.06));
                Mesh.spike(cb, cb.add(f.l(s * c * 0.07, -0.32 + flare * 0.3, 0.16 + flare * 0.2)), 0.035, Mesh.emissive(mc));
            }
        }
    }

    // ══════════════════════════════ KUCHISAKE-ONNA ══════════════════════════════

    static void kuchisake(KuchisakeEntity e, float partial) {
        Frame f = Frame.yaw(yaw(e, partial));
        double t = time();
        double pos = e.walkAnimation.position(partial);
        double spd = Math.min(1f, e.walkAnimation.speed(partial));
        double swing = Math.sin(pos * 0.66) * spd * 0.5;
        float snip = CurseAnim.pulse(e.getId(), 1, 520);
        double confused = CurseAnim.pulse(e.getId(), 4, 3000) * 0.45;
        int skin = 0xFFEFE4DA, coat = 0xFFB0122A;
        for (int s = -1; s <= 1; s += 2) {
            double ph = s * swing;
            Mesh.blob(f.l(s * 0.13, 0.06, 0.08 + ph * 0.25), f, 0.08, 0.06, 0.15, 0xFF121014);
            Mesh.limb(new Vec3[]{f.l(s * 0.12, 0.08, 0.02 + ph * 0.2), f.l(s * 0.12, 0.48, 0)}, 0.06, 0.065, 7, skin);
        }
        double[] ys = {0.35, 0.75, 1.2, 1.6, 1.95, 2.25, 2.42};
        double[] rs = {0.66, 0.52, 0.38, 0.31, 0.33, 0.37, 0.2};
        Vec3[] coatPts = new Vec3[ys.length];
        for (int j = 0; j < ys.length; j++) {
            double hem = (1 - j / 6.0);
            coatPts[j] = f.l(Math.sin(t * 1.7) * 0.06 * hem, ys[j], -swing * 0.12 * hem);
        }
        Mesh.tube(coatPts, rs, 18, (u, n) -> Draw.scaleRgb(coat, 0.84f + 0.16f * (float) Math.sin(Math.atan2(n.z, n.x) * 9 + u * 2)));
        Mesh.torus(f.l(0, 1.22, 0), f.side, f.fwd, 0.385, 0.045, 22, 0xFF4A0612);
        for (int j = 0; j < 3; j++) Mesh.ball(f.l(0, 1.5 + j * 0.2, 0.33 - j * 0.005), 0.03, 0xFF141012);
        Mesh.blob(f.l(0, 2.37, 0), f, 0.3, 0.08, 0.26, 0xFF8A0C20);
        Mesh.limb(new Vec3[]{f.l(0, 2.38, 0), f.l(0, 2.53, 0.02)}, 0.07, 0.065, 7, skin);

        Frame hf = Frame.of(f.fwd, f.up.scale(Math.cos(confused)).add(f.side.scale(Math.sin(confused))));
        Vec3 H = f.l(0, 2.7, 0.03);
        Mesh.blob(H, hf, 0.2, 0.26, 0.22, skin);
        for (int s = -1; s <= 1; s += 2) {
            Mesh.blob(H.add(hf.l(s * 0.075, 0.04, 0.19)), hf, 0.045, 0.022, 0.02, 0xFF140A0E);
            Mesh.ball(H.add(hf.l(s * 0.075, 0.04, 0.208)), 0.012, Mesh.emissive(0xFF2A3A));
        }
        if (!e.unmasked()) {
            Mesh.blob(H.add(hf.l(0, -0.08, 0.13)), hf, 0.17, 0.11, 0.1, 0xFFF5F5F2);
            for (int s = -1; s <= 1; s += 2) {
                Mesh.curve(H.add(hf.l(s * 0.16, -0.06, 0.1)), H.add(hf.l(s * 0.23, 0, 0.02)), H.add(hf.l(s * 0.2, 0.03, -0.04)), 6, 0.01, 0.01, 4, 0xFFF5F5F2);
            }
        } else {
            Vec3[] slit = Mesh.bezier(H.add(hf.l(-0.19, -0.05, 0.08)), H.add(hf.l(0, -0.13, 0.33)), H.add(hf.l(0.19, -0.05, 0.08)), 12);
            Mesh.limb(slit, 0.022, 0.022, 6, Mesh.emissive(0x6A0018));
            for (int k = 2; k <= 10; k++) Mesh.ball(slit[k].add(hf.up.scale(0.018)), 0.012, 0xFFF4EEE4);
        }
        for (int k = 0; k < 28; k++) {
            double a = -2.3 + 4.6 * k / 27.0;
            double sway = Math.sin(t * 1.4 + k) * 0.05;
            Vec3 root = H.add(hf.l(Math.sin(a) * 0.19, 0.17 - Math.abs(a) * 0.02, -Math.cos(a) * 0.17 + 0.02));
            Vec3 ctrl = H.add(hf.l(Math.sin(a) * 0.3, -0.2, -Math.cos(a) * 0.32));
            Vec3 end = H.add(hf.l(Math.sin(a) * 0.36 + sway, -1.15 - Draw.hash(k) * 0.3, -Math.cos(a) * 0.32 - 0.1 - swing * 0.08));
            Mesh.curve(root, ctrl, end, 8, 0.045, 0.008, 5, 0xFF0B0A12);
        }
        for (int k = 0; k < 7; k++) {
            double x = -0.15 + k * 0.05;
            Mesh.curve(H.add(hf.l(x, 0.22, 0.1)), H.add(hf.l(x * 1.05, 0.18, 0.22)), H.add(hf.l(x * 1.1, 0.05, 0.215)), 5, 0.03, 0.008, 4, 0xFF0B0A12);
        }
        // left arm (creature's left = +side)
        Mesh.limb(new Vec3[]{f.l(0.36, 2.22, 0), f.l(0.44, 1.7, 0.04 - swing * 0.2), f.l(0.38, 1.26, 0.12)}, 0.1, 0.08, 8, coat);
        Mesh.ball(f.l(0.38, 1.18, 0.14), 0.06, skin);
        // right arm with the shears
        Vec3 wr = f.l(-0.4, 1.55 + snip * 0.4, 0.62 + snip * 0.25);
        Mesh.limb(new Vec3[]{f.l(-0.36, 2.22, 0), f.l(-0.46, 1.82 + snip * 0.25, 0.3), wr}, 0.1, 0.08, 8, coat);
        Mesh.ball(wr, 0.06, skin);
        double oa = 0.14 + 0.5 * snip + 0.04 * Math.sin(t * 3);
        shears(wr, f.fwd.scale(0.85).subtract(f.up.scale(0.3 - snip * 0.4)).normalize(), f.side, oa, 1.0);
    }

    /** A pair of giant shears at a hand. Also used by the SHEARS effect. */
    public static void shears(Vec3 hand, Vec3 dir, Vec3 perpHint, double open, double scale) {
        Vec3 sd = dir.normalize();
        Vec3 perp = perpHint.subtract(sd.scale(perpHint.dot(sd))).normalize();
        Vec3 pivot = hand.add(sd.scale(0.25 * scale));
        for (int s = -1; s <= 1; s += 2) {
            Vec3 b = sd.scale(Math.cos(open)).add(perp.scale(s * Math.sin(open))).normalize();
            Vec3 thick = b.cross(perp).normalize();
            Vec3 wdir = thick.cross(b).normalize();
            Mesh.ellipsoid(pivot.add(b.scale(0.78 * scale)), b.scale(0.8 * scale), wdir.scale(0.085 * scale), thick.scale(0.022 * scale), 6, 12,
                    (u, n) -> Math.abs(n.dot(wdir)) > 0.6 ? 0xFFF2F4F8 : 0xFFAEB3BE);
            Vec3 hc = pivot.subtract(b.scale(0.32 * scale));
            Mesh.torus(hc, b, wdir, 0.12 * scale, 0.032 * scale, 14, 0xFF16141A);
        }
        Mesh.ball(pivot, 0.045 * scale, 0xFF2A2830);
    }

    // ══════════════════════════════ TAMAMO-NO-MAE ══════════════════════════════

    static void tamamo(TamamoEntity e, float partial) {
        Frame f = Frame.yaw(yaw(e, partial));
        double t = time();
        float sweep = CurseAnim.pulse(e.getId(), 2, 700);
        float stone = CurseAnim.pulse(e.getId(), 3, 1100);
        float cast = CurseAnim.pulse(e.getId(), 1, 500);
        Vec3 o = new Vec3(0, Math.sin(t * 1.3) * 0.25, 0);
        int gold = 0xFFF2C14E, ivory = 0xFFF3EFE6, shadow = 0xFFDAD2E8, verm = 0xFFE0402A;
        Mesh.Paint fur = (u, n) -> n.y < -0.35 ? shadow : ivory;

        Mesh.blob(o.add(f.l(0, 1.4, 0)), f, 0.66, 0.62, 1.35, fur);
        Mesh.blob(o.add(f.l(0, 1.55, 1.0)), f, 0.6, 0.72, 0.7, fur);
        Mesh.blob(o.add(f.l(0, 1.45, -1.0)), f, 0.62, 0.66, 0.72, fur);
        for (int k = 0; k < 14; k++) {
            double a = k / 14.0 * Math.PI * 2;
            Vec3 dir = f.side.scale(Math.cos(a)).add(f.up.scale(Math.sin(a)));
            Vec3 base = o.add(f.l(0, 1.75, 1.25)).add(dir.scale(0.55));
            Mesh.spike(base, base.add(dir.scale(0.45)).subtract(f.fwd.scale(0.35)), 0.13, ivory);
        }
        Vec3 H = o.add(f.l(0, 2.6 + stone * 0.25, 1.92));
        Mesh.limb(new Vec3[]{o.add(f.l(0, 1.85, 1.25)), o.add(f.l(0, 2.2, 1.55)), H.add(f.l(0, -0.15, -0.14))}, 0.38, 0.3, 12, fur);
        Frame hf = f.pitch(stone * 0.35 + cast * 0.15);
        Mesh.blob(H, hf, 0.42, 0.38, 0.48, fur);
        Mesh.blob(H.add(hf.l(0, -0.1, 0.5)), hf, 0.2, 0.17, 0.4, fur);
        Mesh.ball(H.add(hf.l(0, -0.06, 0.9)), 0.07, 0xFF1A1418);
        Mesh.blob(H.add(hf.l(0, -0.2 - cast * 0.05, 0.55)), hf, 0.12, 0.015 + cast * 0.04, 0.25, 0xFF2A1A20);
        for (int s = -1; s <= 1; s += 2) {
            Mesh.blob(H.add(hf.l(s * 0.2, 0.08, 0.38)), hf, 0.09, 0.028, 0.05, Mesh.emissive(gold));
            Mesh.blob(H.add(hf.l(s * 0.22, 0.17, 0.33)), hf, 0.13, 0.02, 0.035, verm);
            Mesh.blob(H.add(hf.l(s * 0.3, -0.05, 0.25)), hf, 0.1, 0.02, 0.045, verm);
            Mesh.limb(new Vec3[]{H.add(hf.l(s * 0.24, 0.28, 0.05)), H.add(hf.l(s * 0.31, 0.6, 0.0)), H.add(hf.l(s * 0.35, 0.86, -0.08))},
                    0.17, 0.012, 8, (u, n) -> u > 0.7 ? gold : ivory);
        }
        Mesh.ball(H.add(hf.l(0, 0.3, 0.36)), 0.06, Mesh.emissive(gold));
        int idx = 0;
        for (int s = -1; s <= 1; s += 2) {
            for (int fr = -1; fr <= 1; fr += 2) {
                double ph = t * 2 + idx++;
                Vec3 hip = o.add(f.l(s * 0.4, 1.22, fr * 1.05));
                Vec3 knee = hip.add(f.l(s * 0.05, -0.55, 0.15 * Math.sin(ph)));
                Vec3 paw = knee.add(f.l(0, -0.55, 0.25 + 0.15 * Math.sin(ph + 1)));
                Mesh.limb(new Vec3[]{hip, knee, paw}, 0.2, 0.11, 10, fur);
                Mesh.ball(paw, 0.13, ivory);
            }
        }
        Vec3 R = o.add(f.l(0, 1.65, -1.75));
        for (int i = 0; i < 9; i++) {
            double a = (-1 + 2 * i / 8.0) * 1.25;
            Vec3 sdir = f.side.scale(Math.sin(a)).add(f.up.scale(Math.cos(a)));
            double sway = Math.sin(t * 1.4 + i * 0.7) * 0.4 + sweep * 2.0 * Math.sin(i * 1.3 + t * 8);
            Vec3 p1 = R.add(f.fwd.scale(-1.3)).add(sdir.scale(0.9)).add(f.side.scale(sway * 0.3));
            Vec3 p2 = R.add(f.fwd.scale(-2.2)).add(sdir.scale(2.0)).add(f.side.scale(sway * 0.8)).add(f.up.scale(0.3));
            Vec3 tip = R.add(f.fwd.scale(-2.6)).add(sdir.scale(3.3)).add(f.side.scale(sway * 1.3)).add(f.up.scale(0.6));
            Vec3[] pts = Mesh.bezier3(R, p1, p2, tip, 16);
            double[] rr = new double[pts.length];
            for (int k = 0; k < pts.length; k++) {
                double u = k / (double) (pts.length - 1);
                double body = 0.2 + 0.3 * Math.sin(Math.PI * Math.min(0.999, u * 0.95 + 0.05));
                rr[k] = body * (u > 0.8 ? (1 - u) / 0.2 : 1) + 0.02;
            }
            Mesh.tube(pts, rr, 12, (u, n) -> u > 0.88 ? Mesh.emissive(gold) : u > 0.74 ? gold : (n.y < -0.35 ? shadow : ivory));
        }
    }

    // ══════════════════════════════ ŌNAMAZU ══════════════════════════════

    static void namazu(NamazuEntity e, float partial) {
        Frame f = Frame.yaw(yaw(e, partial));
        double t = time();
        double dive = CurseAnim.pulse(e.getId(), 2, 1500) * 0.7;
        float bite = CurseAnim.pulse(e.getId(), 1, 420);
        double spd = Math.min(1f, e.walkAnimation.speed(partial));
        int n = 20;
        Vec3[] p = new Vec3[n];
        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            double u = i / (double) (n - 1);
            double wig = Math.sin(t * (2.4 + spd * 2) - i * 0.5) * (0.25 + spd * 0.2) * Math.pow(u, 1.2);
            p[i] = f.l(wig, 0.55 - dive, 2.6 - i * 0.36);
            r[i] = u < 0.35 ? 1.15 - 0.15 * (u / 0.35) : Mth.lerp((u - 0.35) / 0.65, 1.0, 0.2);
        }
        Mesh.Paint hide = (u, nn) -> {
            if (nn.y < -0.25) return 0xFFCFC6A8;
            double mot = Math.sin(nn.x * 6 + u * 20) * Math.sin(nn.z * 5 + u * 13);
            return mot > 0.35 ? 0xFF3A3624 : 0xFF4E4A33;
        };
        Mesh.tube(p, r, 16, hide);
        Mesh.blob(f.l(0, 0.55 - dive, 2.85), f, 1.35, 0.72, 1.05, hide);
        Mesh.blob(f.l(0, 0.28 - dive - bite * 0.15, 3.72), f, 0.95, 0.07 + bite * 0.2, 0.18, 0xFF140E08);
        Mesh.blob(f.l(0, 0.16 - dive - bite * 0.3, 3.6), f, 0.86, 0.1, 0.2, 0xFFCFC6A8);
        for (int s = -1; s <= 1; s += 2) {
            Mesh.ball(f.l(s * 0.95, 0.85 - dive, 3.2), 0.17, Mesh.emissive(0xE8B640));
            Mesh.ball(f.l(s * 1.07, 0.87 - dive, 3.26), 0.08, 0xFF0A0806);
            double sway = Math.sin(t * 2 + s) * 0.25;
            Mesh.curve(f.l(s * 0.75, 0.35 - dive, 3.65), f.l(s * 2.0, 0.6 + sway, 3.4), f.l(s * 3.0, 0.1 + sway, 1.6), 14, 0.085, 0.012, 6, 0xFF2E2A1C);
            Mesh.curve(f.l(s * 0.35, 0.1 - dive, 3.6), f.l(s * 0.6, -0.2, 3.95), f.l(s * 0.85, -0.3, 3.4), 8, 0.05, 0.01, 5, 0xFF2E2A1C);
            double flap = Math.sin(t * 3 + s) * 0.25;
            Mesh.ellipsoid(f.l(s * 1.25, 0.15 - dive, 1.8), f.side.scale(s * 0.85).add(f.up.scale(flap)).add(f.fwd.scale(-0.3)), f.up.scale(0.06), f.fwd.scale(0.5), 6, 12, (u, nn) -> 0xFF3A3624);
        }
        for (int i = 4; i <= 14; i++) {
            Vec3 up = upOf(tangent(p, i));
            double h = 0.38 * (1 - Math.abs(i - 9) / 6.0) + 0.05;
            Mesh.blob(p[i].add(up.scale(r[i] * 0.9 + h * 0.5)), Frame.of(tangent(p, i), UP), 0.05, h, 0.22, 0xFF2E2A1C);
        }
        Vec3 tailEnd = p[n - 1];
        Vec3 tt = tangent(p, n - 1);
        Mesh.ellipsoid(tailEnd.add(tt.scale(0.5)), f.up.scale(0.95), f.side.scale(0.05).add(tt.cross(UP).scale(Math.sin(t * 4) * 0.2)), tt.scale(0.6), 6, 12, (u, nn) -> 0xFF3A3624);
    }

    // ══════════════════════════════ GLOW LAYER ══════════════════════════════

    public static void glow(CurseEntity e, Vec3 origin, float partial, VertexConsumer vc, Matrix4f m, Vec3 camRel, Vfx.Ctx c) {
        double t = time();
        switch (e.kind()) {
            case WYRM -> {
                Vec3[] p = wyrmSpine((WyrmEntity) e, origin, partial);
                for (int i = 4; i < p.length - 4; i += 3) {
                    Vec3 tan = tangent(p, i), up = upOf(tan), side = up.cross(tan).normalize();
                    double r = wyrmRadius(i / (double) (p.length - 1)) + 0.03;
                    float a = 0.45f + 0.25f * (float) Math.sin(t * 3 + i);
                    Draw.ring(vc, m, p[i], side, up, r - 0.04, r + 0.03, Draw.alpha(0xFFFF7A2A, a), Draw.alpha(0xFFFF7A2A, 0), 20);
                }
                Vec3 H = p[0];
                Frame hf = Frame.of(H.subtract(p[2]), UP);
                for (int s = -1; s <= 1; s += 2) Draw.glowOrb(vc, m, H.add(hf.l(s * 0.45, 0.3, 0.65)).x, H.add(hf.l(s * 0.45, 0.3, 0.65)).y, H.add(hf.l(s * 0.45, 0.3, 0.65)).z, 0.22, 0xFFFFB13B, 0.7f);
                float roar = CurseAnim.pulse(e.getId(), 3, 1100);
                if (roar > 0) {
                    Vec3 mouth = H.add(hf.l(0, -0.2, 1.3));
                    Draw.glowOrb(vc, m, mouth.x, mouth.y, mouth.z, 0.5 * roar, 0xFF9B5CFF, roar);
                }
            }
            case WORM -> {
                Frame f = Frame.yaw(yaw(e, partial));
                Vec3[] p = wormSpine((WormEntity) e, partial, f);
                Vec3 H = p[p.length - 1];
                float a = 0.25f + 0.2f * (float) Math.sin(t * 4);
                Draw.glowOrb(vc, m, H.x, H.y, H.z, 0.9, 0xFFFF2A50, a);
            }
            case RAY -> {
                Vec3 cc = new Vec3(0, 0.45, 0);
                for (int k = 0; k < 6; k++) {
                    double ang = t * 0.8 + k;
                    Vec3 sp = cc.add(Math.cos(ang) * 2.2, -0.3, Math.sin(ang) * 2.2);
                    Draw.billboard(vc, m, sp, 0.12, Draw.alpha(0xFF6FFFF0, 0.4f), c.cam.getLeftVector(), c.cam.getUpVector());
                }
            }
            case CENTIPEDE -> {
            }
            case PYRE -> pyreFlames((PyreEntity) e, partial, vc, m, camRel, c);
            case KUCHISAKE -> {
                KuchisakeEntity k = (KuchisakeEntity) e;
                Frame f = Frame.yaw(yaw(e, partial));
                Vec3 H = f.l(0, 2.72, 0.2);
                float a = k.unmasked() ? 0.6f : 0.3f;
                for (int s = -1; s <= 1; s += 2) Draw.glowOrb(vc, m, H.x + f.side.x * s * 0.075, H.y + 0.02, H.z + f.side.z * s * 0.075, 0.05, 0xFFFF2A3A, a);
            }
            case TAMAMO -> {
                Frame f = Frame.yaw(yaw(e, partial));
                for (int k = 0; k < 4; k++) {
                    double ang = t * 1.6 + k * Math.PI / 2;
                    Vec3 fp = f.l(Math.cos(ang) * 2.8, 2.0 + Math.sin(t * 2 + k) * 0.4, Math.sin(ang) * 2.8);
                    Vec3 trailEnd = f.l(Math.cos(ang - 0.5) * 2.8, 2.0 + Math.sin(t * 2 + k - 0.3) * 0.4, Math.sin(ang - 0.5) * 2.8);
                    Draw.glowOrb(vc, m, fp.x, fp.y, fp.z, 0.22, 0xFF9FE6FF, 0.8f);
                    Draw.ribbon(vc, m, trailEnd, fp, 0.0, 0.15, 0, Draw.alpha(0xFF9FE6FF, 0.6f), camRel);
                }
                Vec3 H = f.l(0, 2.6, 1.92).add(0, Math.sin(t * 1.3) * 0.25, 0);
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 eye = H.add(f.l(s * 0.2, 0.08, 0.38));
                    Draw.glowOrb(vc, m, eye.x, eye.y, eye.z, 0.12, 0xFFF2C14E, 0.6f);
                }
            }
            case NAMAZU -> {
                Frame f = Frame.yaw(yaw(e, partial));
                double dive = CurseAnim.pulse(e.getId(), 2, 1500) * 0.7;
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 eye = f.l(s * 0.95, 0.85 - dive, 3.2);
                    Draw.glowOrb(vc, m, eye.x, eye.y, eye.z, 0.25, 0xFFE8B640, 0.5f);
                }
            }
        }
    }

    static void pyreFlames(PyreEntity e, float partial, VertexConsumer vc, Matrix4f m, Vec3 camRel, Vfx.Ctx c) {
        float k = heatK(e);
        int mc = molten(k);
        Frame f = Frame.yaw(yaw(e, partial));
        double t = time();
        int tongues = 10 + (int) (k * 16);
        long frame = Vfx.now() / 45;
        for (int i = 0; i < tongues; i++) {
            double a = i * 2.399;
            double rr = 0.25 + Draw.hash(i) * 0.35;
            Vec3 base = f.l(Math.cos(a) * rr, 1.5 + Draw.hash(i * 3) * 0.7, Math.sin(a) * rr * 0.7 + 0.1);
            double h = (0.45 + 2.6 * k) * (0.6 + 0.4 * Draw.hash(frame + i * 13));
            Vec3 tip = base.add(Math.sin(t * 5 + i) * 0.15, h, Math.cos(t * 4 + i) * 0.15);
            Draw.ribbon(vc, m, base, tip, 0.12 + 0.12 * k, 0.0, Draw.alpha(0xFFFFFFFF, 0.55f), Draw.alpha(mc, 0f), camRel);
            Draw.ribbon(vc, m, base, tip.add(0, h * 0.2, 0), 0.25 + 0.2 * k, 0.0, Draw.alpha(mc, 0.35f), Draw.alpha(0xFFFF2A00, 0f), camRel);
        }
        Draw.glowOrb(vc, m, 0, 1.7, 0.2, 0.6 + k * 0.9, mc, 0.25f + 0.25f * k);
        double aura = 2.0 + PyreEntity.heat(e.tickCount) / 600.0;
        Draw.ringXZ(vc, m, new Vec3(0, 0.06, 0), aura - 0.3, aura, 0, Draw.alpha(mc, 0.35f), 48);
        Draw.ringXZ(vc, m, new Vec3(0, 0.05, 0), 0, aura, Draw.alpha(mc, 0.12f), 0, 48);
    }

    private CurseModels() {}
}
