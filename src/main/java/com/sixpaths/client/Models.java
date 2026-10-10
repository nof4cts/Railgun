package com.sixpaths.client;

import com.sixpaths.entity.BirdEntity;
import com.sixpaths.entity.CentipedeEntity;
import com.sixpaths.entity.HoundEntity;
import com.sixpaths.entity.RhinoEntity;
import com.sixpaths.client.Mesh.Frame;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Procedural, cel-shaded models for every Animal Path summon and every physical projectile.
 * Coordinates are relative to the entity's feet; the caller sets up the pose and the Mesh pass.
 * All designs are original creatures.
 */
public final class Models {

    static float yaw(LivingEntity e, float partial) {
        return Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
    }

    static double secs() {
        return Vfx.now() / 1000.0;
    }

    /** A jointed leg from hip down to the ground with a knee bend and stride swing. */
    static void leg(Frame f, Vec3 hip, double length, double swing, double lift, double r, int col, int hoof) {
        Vec3 foot = hip.add(f.fwd.scale(swing)).add(0, -length + lift, 0);
        Vec3 knee = hip.lerp(foot, 0.5).add(f.fwd.scale(length * 0.18));
        Mesh.limb(new Vec3[]{hip, knee, foot}, r, r * 0.65, 8, col);
        Mesh.blob(foot.add(0, 0.05, 0), f, r * 0.9, r * 0.45, r * 1.05, hoof);
    }

    // ═════════════════════════ HORNED BEHEMOTH ═════════════════════════

    public static void rhino(RhinoEntity e, float partial) {
        Frame f = Frame.yaw(yaw(e, partial));
        float walk = e.walkAnimation.position(partial), sp = Math.min(1f, e.walkAnimation.speed(partial));
        float charge = BeastAnim.pulse(e.getId(), 1, 1300);
        float gore = BeastAnim.pulse(e.getId(), 2, 450);
        int hide = 0xFF7E6A54, plate = 0xFF4E3E32, bone = 0xFFE8DCC0;
        double bob = Math.abs(Math.sin(walk * 0.6)) * 0.08 * sp;

        Vec3 body = new Vec3(0, 1.45 + bob, 0);
        Mesh.blob(body, f, 1.25, 1.0, 1.75, hide);
        Mesh.blob(body.add(f.l(0, 0.25, 0.9)), f, 1.15, 0.95, 0.95, hide); // shoulder hump
        // armour plates and dorsal spikes
        for (int i = 0; i < 4; i++) {
            double z = -1.1 + i * 0.75;
            Mesh.blob(body.add(f.l(0, 0.82, z)), f, 0.95, 0.32, 0.42, plate);
            Mesh.spike(body.add(f.l(0, 1.0, z)), body.add(f.l(0, 1.55 - i * 0.05, z - 0.25)), 0.16, bone);
        }
        // legs: diagonal gait
        double[] ph = {0, Math.PI, Math.PI, 0};
        Vec3[] hips = {f.l(0.75, 1.25, 1.0), f.l(-0.75, 1.25, 1.0), f.l(0.75, 1.25, -1.05), f.l(-0.75, 1.25, -1.05)};
        for (int i = 0; i < 4; i++) {
            double s = Math.sin(walk * 0.6 + ph[i]) * 0.45 * sp;
            double lift = Math.max(0, Math.cos(walk * 0.6 + ph[i])) * 0.25 * sp;
            leg(f, hips[i].add(0, bob, 0), 1.25, s, lift, 0.3, hide, plate);
        }
        // head lowers into a charge
        double dip = charge * 0.45 - gore * 0.35;
        Frame hf = f.pitch(-0.25 - dip);
        Vec3 head = body.add(f.l(0, 0.05 - dip * 0.6, 2.05));
        Mesh.blob(head, hf, 0.72, 0.62, 0.85, hide);
        Mesh.blob(head.add(hf.l(0, -0.3, 0.55)), hf, 0.5, 0.35, 0.55, plate); // snout
        Mesh.blob(head.add(hf.l(0, 0.42, -0.1)), hf, 0.62, 0.22, 0.5, plate); // brow plate
        for (int s = -1; s <= 1; s += 2) {
            Mesh.ball(head.add(hf.l(s * 0.46, 0.2, 0.45)), 0.1, Mesh.emissive(0xFFFFB040));
            Mesh.spike(head.add(hf.l(s * 0.5, 0.35, -0.3)), head.add(hf.l(s * 0.95, 0.75, -0.75)), 0.12, bone); // ear horns
        }
        // the spiral drill horn — spins while charging
        Vec3 hb = head.add(hf.l(0, 0.25, 0.75));
        Vec3 ht = head.add(hf.l(0, 0.85, 2.3));
        Mesh.spike(hb, ht, 0.36, bone);
        double spin = secs() * (2 + charge * 30);
        Vec3 ax = ht.subtract(hb).normalize();
        Vec3 u = Draw.perp(ax), v = ax.cross(u);
        for (int k = 0; k < 6; k++) {
            double t = 0.08 + k * 0.14;
            Vec3 c = hb.lerp(ht, t);
            double rr = 0.34 * (1 - t) + 0.02;
            double a = spin + k * 1.1;
            Vec3 ru = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 rv = ax.cross(ru);
            Mesh.torus(c, ru, rv, rr, 0.045, 10, plate);
        }
        // tail
        Vec3 t0 = body.add(f.l(0, 0.3, -1.65));
        Mesh.limb(new Vec3[]{t0, t0.add(f.l(0, -0.3, -0.5)), t0.add(f.l(Math.sin(secs() * 3) * 0.2, -0.8, -0.7))}, 0.16, 0.05, 6, hide);
    }

    // ═════════════════════════ GREAT CENTIPEDE ═════════════════════════

    public static void centipede(CentipedeEntity e, Vec3 origin, float partial) {
        float y = yaw(e, partial);
        Frame f = Frame.yaw(y);
        float rear = BeastAnim.pulse(e.getId(), 2, 3500);
        float bite = BeastAnim.pulse(e.getId(), 1, 300);
        Vec3 headW = origin.add(0, 0.55 + rear * 2.6, 0);
        Vec3[] spine = SpineTrail.sample(e.getId(), headW, f.fwd.scale(-1), 24, 0.62);
        int shell = 0xFF6E241C, shell2 = 0xFF3E1410, legC = 0xFFD89A3A;
        double t = secs();
        for (int i = spine.length - 1; i >= 0; i--) {
            Vec3 p = spine[i].subtract(origin);
            Vec3 next = (i > 0 ? spine[i - 1] : spine[0].add(f.fwd)).subtract(origin);
            Vec3 prev = (i < spine.length - 1 ? spine[i + 1] : spine[i].subtract(f.fwd)).subtract(origin);
            Vec3 dir = next.subtract(prev);
            Frame sf = Frame.of(dir.lengthSqr() < 1.0e-6 ? f.fwd : dir, Draw.Y);
            double taper = 1 - i / (double) spine.length * 0.45;
            if (i > 0) {
                // ground the body behind the rearing front
                if (rear > 0 && i > 8) p = new Vec3(p.x, Math.max(0.45, p.y - rear * 2.6 * Math.min(1, (i - 8) / 4.0)), p.z);
                Mesh.blob(p, sf, 0.6 * taper, 0.4 * taper, 0.38, i % 2 == 0 ? shell : shell2);
                Mesh.blob(p.add(0, 0.18 * taper, 0), sf, 0.45 * taper, 0.2 * taper, 0.3, shell2);
                for (int s = -1; s <= 1; s += 2) {
                    double ph = t * 9 + i * 0.7 + (s > 0 ? 0 : Math.PI);
                    Vec3 base = p.add(sf.side.scale(s * 0.5 * taper));
                    Vec3 knee = base.add(sf.side.scale(s * 0.45)).add(0, 0.2, 0).add(sf.fwd.scale(Math.sin(ph) * 0.18));
                    Vec3 tip = knee.add(sf.side.scale(s * 0.25)).add(0, -Math.max(0.2, p.y + 0.2), 0).add(sf.fwd.scale(Math.sin(ph) * 0.25));
                    Mesh.limb(new Vec3[]{base, knee, tip}, 0.07, 0.03, 5, legC);
                }
            } else {
                Frame hf = sf.pitch(-0.15 + rear * 0.4);
                Mesh.blob(p, hf, 0.62, 0.42, 0.55, shell2);
                Mesh.blob(p.add(hf.l(0, 0.2, -0.1)), hf, 0.5, 0.2, 0.45, shell);
                for (int s = -1; s <= 1; s += 2) {
                    double open = 0.25 + bite * 0.5;
                    Vec3 mb = p.add(hf.l(s * 0.3, -0.1, 0.45));
                    Mesh.curve(mb, mb.add(hf.l(s * (0.5 + open), 0, 0.5)), mb.add(hf.l(s * -0.05, 0, 1.0)), 6, 0.09, 0.02, 6, legC);
                    Vec3 ab = p.add(hf.l(s * 0.22, 0.3, 0.4));
                    Mesh.curve(ab, ab.add(hf.l(s * 0.6, 0.9, 0.6)), ab.add(hf.l(s * 1.1, 0.6 + Math.sin(t * 4 + s) * 0.2, 1.5)), 8, 0.04, 0.01, 4, legC);
                    Mesh.ball(p.add(hf.l(s * 0.32, 0.2, 0.35)), 0.08, Mesh.emissive(0xFFFFE070));
                }
            }
        }
    }

    // ═════════════════════════ THREE-HEADED HOUND ═════════════════════════

    public static void hound(HoundEntity e, float partial) {
        double sc = 1 - e.generation() * 0.12;
        Frame f = Frame.yaw(yaw(e, partial));
        float walk = e.walkAnimation.position(partial), sp = Math.min(1f, e.walkAnimation.speed(partial));
        float bite = BeastAnim.pulse(e.getId(), 1, 320);
        int fur = 0xFF4A3A5E, dark = 0xFF241A30, fang = 0xFFF2EAD8;
        double bob = Math.abs(Math.sin(walk * 0.8)) * 0.1 * sp;
        Vec3 body = new Vec3(0, 1.15 * sc + bob, 0);
        Mesh.blob(body, f, 0.55 * sc, 0.55 * sc, 1.05 * sc, fur);
        Mesh.blob(body.add(f.l(0, 0.15, 0.55).scale(sc)), f, 0.72 * sc, 0.68 * sc, 0.62 * sc, fur); // chest
        for (int i = 0; i < 7; i++) {
            double z = (0.75 - i * 0.22) * sc;
            Mesh.spike(body.add(f.l(0, 0.55 * sc, z)), body.add(f.l(0, (0.95 - i * 0.04) * sc, z - 0.35 * sc)), 0.09 * sc, dark);
        }
        double[] ph = {0, Math.PI, Math.PI * 0.5, Math.PI * 1.5};
        Vec3[] hips = {f.l(0.38, 0.95, 0.7), f.l(-0.38, 0.95, 0.7), f.l(0.36, 0.95, -0.75), f.l(-0.36, 0.95, -0.75)};
        for (int i = 0; i < 4; i++) {
            double s = Math.sin(walk * 0.8 + ph[i]) * 0.5 * sp;
            double lift = Math.max(0, Math.cos(walk * 0.8 + ph[i])) * 0.3 * sp;
            leg(f, hips[i].scale(sc).add(0, bob, 0), 0.95 * sc, s * sc, lift * sc, 0.17 * sc, fur, dark);
        }
        // three heads on three necks, the middle one biggest
        double t = secs();
        for (int h = -1; h <= 1; h++) {
            double hs = (h == 0 ? 1.0 : 0.85) * sc;
            Vec3 n0 = body.add(f.l(h * 0.35 * sc, 0.35 * sc, 0.8 * sc));
            double sway = Math.sin(t * 2.3 + h * 1.7) * 0.12;
            Vec3 hp = n0.add(f.l(h * 0.55 * sc + sway, 0.55 * sc, 0.55 * sc - bite * 0.0));
            Mesh.curve(n0, n0.add(f.l(h * 0.2 * sc, 0.45 * sc, 0.1)), hp, 5, 0.28 * hs, 0.22 * hs, 8, fur);
            Frame hf = f.yawBy(-h * 0.35).pitch(-0.1 - bite * 0.25 * (h == 0 ? 1 : 0.6));
            Mesh.blob(hp, hf, 0.3 * hs, 0.28 * hs, 0.34 * hs, fur);
            Mesh.blob(hp.add(hf.l(0, -0.06, 0.38).scale(hs)), hf, 0.17 * hs, 0.14 * hs, 0.3 * hs, dark); // muzzle
            double open = 0.12 + bite * 0.5 + Math.max(0, Math.sin(t * 1.4 + h)) * 0.08;
            Frame jf = hf.pitch(-open);
            Mesh.blob(hp.add(hf.l(0, -0.2, 0.25).scale(hs)), jf, 0.14 * hs, 0.06 * hs, 0.3 * hs, dark); // jaw
            for (int s = -1; s <= 1; s += 2) {
                Mesh.spike(hp.add(hf.l(s * 0.08, -0.12, 0.55).scale(hs)), hp.add(hf.l(s * 0.08, -0.26, 0.55).scale(hs)), 0.03 * hs, fang);
                Mesh.spike(hp.add(hf.l(s * 0.16, 0.2, -0.05).scale(hs)), hp.add(hf.l(s * 0.3, 0.55, -0.2).scale(hs)), 0.07 * hs, dark); // ears
                Mesh.ball(hp.add(hf.l(s * 0.15, 0.08, 0.26).scale(hs)), 0.05 * hs, Mesh.emissive(0xFFFF5A3A));
            }
        }
        Vec3 tb = body.add(f.l(0, 0.3 * sc, -1.0 * sc));
        double wag = Math.sin(t * 6) * 0.3;
        Mesh.curve(tb, tb.add(f.l(wag, 0.4 * sc, -0.5 * sc)), tb.add(f.l(wag * 1.5, 0.2 * sc, -1.1 * sc)), 6, 0.14 * sc, 0.03, 6, fur);
    }

    // ═════════════════════════ SKY ROC ═════════════════════════

    public static void bird(BirdEntity e, float partial) {
        Frame f0 = Frame.yaw(yaw(e, partial));
        Vec3 v = e.getDeltaMovement();
        float dive = BeastAnim.pulse(e.getId(), 1, 1500);
        Frame f = f0.pitch(Mth.clamp(v.y * 0.8, -0.6, 0.4) - dive * 0.5);
        int body = 0xFF4E6E9E, wing = 0xFF34507E, tip = 0xFFE6EEFF, beak = 0xFFE8B040;
        double t = secs();
        double flap = Math.sin(t * (e.isVehicle() ? 3.2 : 4.0)) * (dive > 0.3 ? 0.15 : 1);
        Vec3 c = new Vec3(0, 0.95, 0);
        Mesh.blob(c, f, 0.75, 0.62, 1.45, body);
        Mesh.blob(c.add(f.l(0, -0.2, 0.3)), f, 0.62, 0.45, 0.9, tip); // pale breast
        // head and beak
        Vec3 nk = c.add(f.l(0, 0.35, 1.25));
        Vec3 hd = nk.add(f.l(0, 0.35, 0.45));
        Mesh.curve(nk, nk.add(f.l(0, 0.3, 0.1)), hd, 4, 0.32, 0.28, 8, body);
        Mesh.blob(hd, f, 0.34, 0.32, 0.4, body);
        Mesh.spike(hd.add(f.l(0, -0.02, 0.3)), hd.add(f.l(0, -0.22, 1.05)), 0.15, beak);
        Mesh.spike(hd.add(f.l(0, 0.2, -0.15)), hd.add(f.l(0, 0.55, -0.75)), 0.08, tip); // crest
        Mesh.spike(hd.add(f.l(0.1, 0.25, -0.1)), hd.add(f.l(0.25, 0.5, -0.65)), 0.06, tip);
        Mesh.spike(hd.add(f.l(-0.1, 0.25, -0.1)), hd.add(f.l(-0.25, 0.5, -0.65)), 0.06, tip);
        for (int s = -1; s <= 1; s += 2) Mesh.ball(hd.add(f.l(s * 0.27, 0.08, 0.15)), 0.06, Mesh.emissive(0xFF8AE0FF));
        // four wings: two great pairs, the rear pair beating out of phase
        for (int pair = 0; pair < 2; pair++) {
            double span = pair == 0 ? 3.6 : 2.6;
            double fl = pair == 0 ? flap : Math.sin(t * 4.0 + 1.2) * (dive > 0.3 ? 0.1 : 0.9);
            double z = pair == 0 ? 0.45 : -0.55;
            for (int s = -1; s <= 1; s += 2) {
                Vec3 root = c.add(f.l(s * 0.55, 0.2, z));
                double ang = fl * 0.6 + (dive > 0.3 ? -0.7 : 0.05);
                Vec3 out = f.side.scale(s * Math.cos(ang)).add(f.up.scale(Math.sin(ang)));
                Vec3 elbow = root.add(out.scale(span * 0.45)).add(f.fwd.scale(0.15));
                double ang2 = ang + fl * 0.35;
                Vec3 out2 = f.side.scale(s * Math.cos(ang2)).add(f.up.scale(Math.sin(ang2)));
                Vec3 end = elbow.add(out2.scale(span * 0.55)).add(f.fwd.scale(-0.35));
                Mesh.limb(new Vec3[]{root, elbow, end}, 0.16, 0.06, 6, wing);
                // primary feathers as flattened blades trailing the arm
                for (int k = 0; k < 7; k++) {
                    double u = k / 6.0;
                    Vec3 a = u < 0.45 ? root.lerp(elbow, u / 0.45) : elbow.lerp(end, (u - 0.45) / 0.55);
                    double len = 0.8 + u * 0.9;
                    Vec3 fd = f.fwd.scale(-1).add(out2.scale(u * 0.6)).normalize();
                    Vec3 fc = a.add(fd.scale(len * 0.5));
                    Vec3 flat = fd.cross(out2).normalize();
                    Mesh.ellipsoid(fc, fd.scale(len * 0.55), flat.scale(0.05), out2.scale(0.22), 4, 8, (uu, n) -> uu > 0.5 ? tip : wing);
                }
            }
        }
        // tail fan
        for (int k = -3; k <= 3; k++) {
            Vec3 tb = c.add(f.l(k * 0.08, 0.05, -1.3));
            Vec3 td = f.fwd.scale(-1).add(f.side.scale(k * 0.18)).normalize();
            Mesh.ellipsoid(tb.add(td.scale(0.7)), td.scale(0.8), f.up.scale(0.04), td.cross(f.up).scale(0.16), 4, 8, (uu, n) -> Math.abs(n.dot(td)) > 0.5 ? tip : wing);
        }
        // talons tucked
        for (int s = -1; s <= 1; s += 2) {
            Vec3 lb = c.add(f.l(s * 0.3, -0.45, -0.2));
            Mesh.limb(new Vec3[]{lb, lb.add(f.l(0, -0.3, -0.25)), lb.add(f.l(0, -0.38, -0.55))}, 0.08, 0.04, 5, beak);
        }
    }

    // ═════════════════════════ PROJECTILES ═════════════════════════

    /** Black receiver rod: a ridged spike, oriented along its flight. */
    public static void rod(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1.0e-6 ? Draw.Y : dir.normalize();
        Vec3 tip = d.scale(0.9), tail = d.scale(-0.8);
        Mesh.spike(tail, tip, 0.06, 0xFF141418);
        Vec3 u = Draw.perp(d), v = d.cross(u);
        for (int k = 0; k < 5; k++) Mesh.torus(tail.lerp(tip, 0.1 + k * 0.12), u, v, 0.07, 0.022, 8, 0xFF2C2C36);
        Mesh.ball(tail, 0.075, 0xFF22222A);
    }

    /** Asura missile: finned grey body with a dark nose. */
    public static void missile(Vec3 dir) {
        Vec3 d = dir.lengthSqr() < 1.0e-6 ? Draw.Z : dir.normalize();
        Vec3 u = Draw.perp(d), v = d.cross(u);
        Mesh.limb(new Vec3[]{d.scale(-0.45), Vec3.ZERO, d.scale(0.3)}, 0.13, 0.13, 10, 0xFF9A9CA6);
        Mesh.spike(d.scale(0.3), d.scale(0.62), 0.13, 0xFF3A3C46);
        Mesh.torus(d.scale(0.05), u, v, 0.135, 0.02, 10, 0xFFC8501E);
        for (int k = 0; k < 4; k++) {
            Vec3 o = (k % 2 == 0 ? u : v).scale(k < 2 ? 1 : -1);
            Mesh.ellipsoid(d.scale(-0.35).add(o.scale(0.18)), d.scale(0.14), o.scale(0.1), o.cross(d).scale(0.012), 3, 6, (uu, nn) -> 0xFF6E707A);
        }
    }

    // ═════════════════════════ NARAKA: THE KING ═════════════════════════

    /**
     * The King of Hell — an original design: a colossal horned head of crimson stone banded in
     * bronze, three burning eyes, a crown of five horns and a jaw that opens to judge.
     */
    public static void king(Frame f, double scale, double jawOpen, int skin, int metal, int eye) {
        double s = scale;
        Vec3 c = new Vec3(0, 2.4 * s, 0);
        Mesh.blob(c, f, 2.1 * s, 2.0 * s, 1.8 * s, skin);
        Mesh.blob(c.add(f.l(0, 0.9, 0.55).scale(s)), f, 1.95 * s, 0.55 * s, 1.35 * s, metal); // brow band
        Mesh.blob(c.add(f.l(0, -0.2, 1.35).scale(s)), f, 0.55 * s, 0.75 * s, 0.6 * s, skin); // nose ridge
        for (int k = -1; k <= 1; k += 2) {
            Mesh.blob(c.add(f.l(k * 1.6, -0.6, 0.9).scale(s)), f, 0.75 * s, 0.85 * s, 0.75 * s, skin); // cheeks
            Mesh.blob(c.add(f.l(k * 0.85, 0.35, 1.45).scale(s)), f, 0.42 * s, 0.26 * s, 0.2 * s, Mesh.emissive(eye));
            Mesh.blob(c.add(f.l(k * 0.85, 0.58, 1.48).scale(s)), f.pitch(0.25 * k * 0), 0.55 * s, 0.12 * s, 0.25 * s, metal); // scowl
            Mesh.spike(c.add(f.l(k * 2.0, 0.2, 0).scale(s)), c.add(f.l(k * 3.0, 0.6, -0.6).scale(s)), 0.25 * s, metal); // ear blades
        }
        Mesh.blob(c.add(f.l(0, 1.2, 1.55).scale(s)), f, 0.18 * s, 0.3 * s, 0.12 * s, Mesh.emissive(eye)); // third eye
        // crown of five horns
        for (int h = -2; h <= 2; h++) {
            Vec3 base = c.add(f.l(h * 0.7, 1.7 - Math.abs(h) * 0.2, 0.2 - Math.abs(h) * 0.15).scale(s));
            Vec3 tip = base.add(f.l(h * 0.55, 2.0 - Math.abs(h) * 0.35, -0.6).scale(s));
            Mesh.spike(base, tip, (0.3 - Math.abs(h) * 0.03) * s, h == 0 ? metal : 0xFF2A1014);
        }
        // upper teeth
        for (int k = -3; k <= 3; k++) {
            Vec3 tb = c.add(f.l(k * 0.32, -1.05, 1.5 - Math.abs(k) * 0.08).scale(s));
            Mesh.spike(tb, tb.add(f.l(0, -0.5, 0.05).scale(s)), 0.11 * s, 0xFFEFE6CF);
        }
        // the jaw swings open around a hinge below the ears
        Frame jf = f.pitch(-jawOpen);
        Vec3 hinge = c.add(f.l(0, -0.9, -0.3).scale(s));
        Vec3 jc = hinge.add(jf.l(0, -0.6, 1.3).scale(s));
        Mesh.blob(jc, jf, 1.6 * s, 0.55 * s, 1.35 * s, skin);
        Mesh.blob(jc.add(jf.l(0, -0.35, 0.6).scale(s)), jf, 1.1 * s, 0.35 * s, 0.6 * s, metal); // chin plate
        for (int k = -3; k <= 3; k++) {
            Vec3 tb = jc.add(jf.l(k * 0.32, 0.4, 0.9 - Math.abs(k) * 0.1).scale(s));
            Mesh.spike(tb, tb.add(jf.l(0, 0.45, 0).scale(s)), 0.1 * s, 0xFFEFE6CF);
        }
        if (jawOpen > 0.15) Mesh.blob(c.add(f.l(0, -1.3, 0.6).scale(s)), f, 1.2 * s, 0.6 * s * jawOpen, 0.8 * s, 0xFF120408); // throat
    }

    /** Rocky moon of Planetary Devastation: lumpy shell studded with boulders. */
    public static void moon(double r, long seed, int rock, int rock2) {
        Mesh.ellipsoid(Vec3.ZERO, Draw.X.scale(r), Draw.Y.scale(r * 0.97), Draw.Z.scale(r), 14, 22, (u, n) -> {
            double k = Math.sin(n.x * 9 + seed) * Math.sin(n.y * 7) * Math.sin(n.z * 8 - seed);
            return k > 0.15 ? rock2 : rock;
        });
        for (int i = 0; i < 46; i++) {
            Vec3 d = Draw.randDir(seed + i * 17L);
            double b = r * (0.12 + Draw.hash(seed + i) * 0.16);
            Frame bf = Frame.of(d, Draw.Y);
            Mesh.blob(d.scale(r * 0.98), bf, b, b * 0.8, b * 0.6, i % 3 == 0 ? rock2 : rock);
        }
    }

    private Models() {}
}
