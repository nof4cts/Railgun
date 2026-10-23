package com.woh.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.woh.W;
import com.woh.common.Beat;
import com.woh.common.WMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The World Over Heaven's Stand: an original living statue, sculpted. Heroic white marble, cracked and mended with
 * seams of gold that glow; a calm faceless face with two narrow burning eyes; a gold laurel round the brow; a marble
 * sash across the chest; a gold belt with a burning gem and cloth panels; gold bracers and anklets. Built from smooth
 * ellipsoids, tapered tubes and tori with smooth normals, lit by the world and depth-tested like any solid thing.
 *
 * It does the fighting. It waits at his shoulder; when a move lands blows on someone it flies out, stands in front of
 * them and throws every blow on the exact frame the server deals it (it reads the same beats), a storm of afterimage
 * fists in a barrage, then returns. Ultimates raise its arms and bring them down; support moves have their gestures.
 */
public final class WStand {
    private static final ResourceLocation TEX = W.id("textures/entity/stand.png");
    private static final int BRIGHT = 0xF000F0;
    private static final double S = 1.25;                 // model units -> blocks
    private static final Map<Integer, double[]> SURGE = new HashMap<>();   // {startSeconds, strength}
    private static MultiBufferSource.BufferSource OWN;

    // model-space joints (x right, y up, z forward; origin at the pelvis)
    private static final Vec3 SHOULDER_L = new Vec3(-0.35, 0.72, 0), SHOULDER_R = new Vec3(0.35, 0.72, 0);
    private static final Vec3 HIP_L = new Vec3(-0.11, 0.0, 0), HIP_R = new Vec3(0.11, 0.0, 0);
    private static final Vec3 GUARD_L = new Vec3(-0.2, 0.55, 0.34), GUARD_R = new Vec3(0.2, 0.55, 0.34);
    private static final double UPPER = 0.34, FORE = 0.32;

    private WStand() {}

    /** A cast began: the eyes flare. */
    public static void surge(int entityId, WMove move) {
        double strength = move == null ? 0.6 : (move.cutscene ? 1.2 : 0.8);
        SURGE.put(entityId, new double[]{WFx.time(), strength});
    }

    public static void clear() {
        SURGE.clear();
    }

    private static boolean isStandUser(Entity ent) {
        if (!(ent instanceof Player)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (ent == mc.player) return ClientSorcerers.mine() == Sorcerer.WORLD_OVER_HEAVEN;
        return SURGE.containsKey(ent.getId()) || WCasts.overdrive(ent) || active(ent.getId()) != null;
    }

    /** The newest cast this player is in the middle of, or null. */
    static WCasts.Live active(int id) {
        WCasts.Live best = null;
        for (WCasts.Live l : WCasts.LIVE) {
            if (l.m.caster() != id || l.t < 0 || l.t > l.move.end + 14) continue;
            if (best == null || l.start > best.start) best = l;
        }
        return best;
    }

    // ------------------------------------------------------------------ choreography
    private static final class Pose {
        Vec3 root, face;
        double lean = 0.12, twist = 0;
        Vec3 fistL = GUARD_L, fistR = GUARD_R;
        double out;              // 0 at his shoulder .. 1 out at the target
        boolean barrage;
        Vec3 aimModel;           // where the barrage lands (model space)
        float eyes = 0.8f;
    }

    private static double smooth(double u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    /** World point -> this pose's (un-leaned) model space. */
    private static Vec3 toModel(Vec3 w, Vec3 root, Vec3 f, Vec3 r) {
        Vec3 d = w.subtract(root);
        return new Vec3(d.dot(r) / S, d.y / S, d.dot(f) / S);
    }

    private static Vec3 reach(Vec3 shoulder, Vec3 target, double max) {
        Vec3 d = target.subtract(shoulder);
        double len = d.length();
        return len > max ? shoulder.add(d.scale(max / len)) : target;
    }

    private static Pose choreograph(Player pl, float pt, double time) {
        Pose P = new Pose();
        float yaw = Mth.lerp(pt, pl.yBodyRotO, pl.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 r = new Vec3(-f.z, 0, f.x);
        Vec3 feet = pl.getPosition(pt);
        double bob = Math.sin(time * 1.7 + pl.getId()) * 0.05;
        Vec3 home = feet.add(f.scale(-0.4)).add(r.scale(0.95)).add(0, 0.95 + bob, 0);   // beside his right shoulder
        P.root = home;
        P.face = f;
        double[] sg = SURGE.get(pl.getId());
        if (sg != null) P.eyes = (float) (0.8 + 0.8 * sg[1] * Math.exp(-(time - sg[0]) * 3.0));

        WCasts.Live l = active(pl.getId());
        if (l == null) return P;
        double t = l.t;
        WMove m = l.move;

        // the blows this move lands: on someone (it flies to them), or in place (it slams where it stands)
        List<Beat> struck = new ArrayList<>(), slams = new ArrayList<>(), thrusts = new ArrayList<>();
        for (Beat b : l.beats) {
            if (b.kind() != Beat.BF && b.kind() != Beat.ERASE) continue;
            if (b.a0() == Beat.TARGET || b.a0() >= Beat.T0) struck.add(b);
            else if (b.a0() == Beat.POINT) thrusts.add(b);
            else slams.add(b);
        }

        // ---- gestures for the moves that are not blows
        double gIn = smooth(t / 5.0) * (1 - smooth((t - (m.end - 8)) / 8.0));
        Vec3 gl = null, gr = null;
        switch (m) {
            case WOUNDS_UNDONE, GRACE_OVER_HEAVEN -> { gl = new Vec3(-0.62, 0.78, 0.2); gr = new Vec3(0.62, 0.78, 0.2); }
            case NEGATION -> { gl = new Vec3(0.14, 0.74, 0.36); gr = new Vec3(-0.14, 0.8, 0.38); }
            case RETURN_OVER_HEAVEN -> { gr = new Vec3(0.04, 0.62, 0.2); gl = new Vec3(-0.2, 0.5, 0.3); }
            case HEAVEN_ASCENSION -> { gl = new Vec3(-0.45, 1.28, 0.05); gr = new Vec3(0.45, 1.28, 0.05); }
            case REALITY_COLLAPSE -> { gl = new Vec3(-0.66, 0.8, 0.12); gr = new Vec3(0.66, 0.8, 0.12); }
            case TIME_STOP, STILLNESS -> { if (t < 22) gr = new Vec3(0.16, 0.95, 0.46); }
            case KNIFE_RAIN -> {
                if (t < 70) { gl = new Vec3(-0.3, 1.3, 0.1); gr = new Vec3(0.3, 1.3, 0.1); }
                else { gl = new Vec3(-0.2, -0.1, 0.55); gr = new Vec3(0.2, -0.1, 0.55); P.lean += 0.5 * (1 - smooth((t - 76) / 20.0)); }
            }
            case HEAVENLY_LIGHTNING -> { gr = t < 15 ? new Vec3(0.2, 1.32, 0.15) : new Vec3(0.2, 0.55, 0.62); }
            case FOURTEEN_PHRASES -> { if (t < 70) { gl = new Vec3(-0.6, 0.7, 0.3); gr = new Vec3(0.6, 0.7, 0.3); } }
            case ROAD_TO_HEAVEN -> { if (t > 4) { gl = new Vec3(-0.25, 1.3, 0.3); gr = new Vec3(0.25, 1.3, 0.3); } }
            case REALITY_KNIVES -> {
                if (t > 1 && t < 12) {
                    double k = (t - 2) % 2.0;
                    gr = k < 1 ? new Vec3(0.25, 0.75, 0.15) : new Vec3(0.05, 0.6, 0.66);
                }
            }
            default -> {
            }
        }
        if (gl != null) P.fistL = GUARD_L.lerp(gl, gIn);
        if (gr != null) P.fistR = GUARD_R.lerp(gr, gIn);

        // ---- slams in place (the ultimates): arms raised as it builds, brought down on the beat
        for (Beat b : slams) {
            double ph = t - b.t();
            if (ph > -40 && ph < 0) {
                double u = smooth((ph + 40) / 30.0);
                P.fistL = P.fistL.lerp(new Vec3(-0.26, 1.36, 0.08), u);
                P.fistR = P.fistR.lerp(new Vec3(0.26, 1.36, 0.08), u);
                P.lean -= 0.15 * u;
            } else if (ph >= 0 && ph < 26) {
                double u = ph < 2 ? ph / 2.0 : 1 - smooth((ph - 10) / 16.0);
                P.fistL = P.fistL.lerp(new Vec3(-0.2, -0.25, 0.55), u);
                P.fistR = P.fistR.lerp(new Vec3(0.2, -0.25, 0.55), u);
                P.lean += 0.7 * u;
            }
        }

        // ---- thrusts at a distant point (judgement falling where he aims)
        for (Beat b : thrusts) {
            double ph = t - b.t();
            if (ph > -3 && ph < 10) {
                double e = ph < 0 ? 0 : (ph < 2 ? ph / 2.0 : 1 - smooth((ph - 3) / 7.0));
                Vec3 aim = reach(SHOULDER_R, toModel(l.at(b.a0(), b.o0()), P.root, f, r), UPPER + FORE - 0.02);
                P.fistR = P.fistR.lerp(aim, e);
                P.lean += 0.25 * e;
            }
        }

        if (struck.isEmpty()) return P;

        // ---- it flies out to whoever is being struck, and back
        double first = Double.MAX_VALUE, last = -1;
        for (Beat b : struck) {
            first = Math.min(first, b.t());
            last = Math.max(last, b.t());
        }
        double tOut = first - 7, tIn = first - 1, tLeave = last + 7, tHome = last + 14;
        double w = t < tOut ? 0 : t < tIn ? smooth((t - tOut) / (tIn - tOut)) : t < tLeave ? 1 : smooth(1 - (t - tLeave) / (tHome - tLeave));
        P.out = w;
        if (w <= 0) return P;

        // the strike it is on (the next one, or the last one if they are all thrown)
        int idx = 0;
        for (int i = 0; i < struck.size(); i++) if (struck.get(i).t() <= t + 3) idx = i;
        Beat cur = struck.get(idx);
        Vec3 T = l.at(cur.a0(), Vec3.ZERO);
        Vec3 toT = new Vec3(T.x - feet.x, 0, T.z - feet.z);
        Vec3 dir = toT.lengthSqr() < 1e-4 ? f : toT.normalize();
        if (m == WMove.REWRITE_RUSH) {
            // a reality-step: each blow from another side of them
            double[] around = {0, 90, -90, 180, 45};
            double a = Math.toRadians(around[idx % around.length]);
            dir = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), 0, dir.x * Math.sin(a) + dir.z * Math.cos(a));
        }
        Vec3 strikePos = T.subtract(dir.scale(1.35)).add(0, -0.55, 0);
        double ahead = new Vec3(strikePos.x - feet.x, 0, strikePos.z - feet.z).dot(dir);
        if (ahead < 1.8) strikePos = strikePos.add(dir.scale(1.8 - ahead));   // a point at his feet: it steps out in front
        P.root = home.lerp(strikePos, w);
        Vec3 fl = f.lerp(dir, smooth(w * 2));
        Vec3 fc = fl.lengthSqr() < 1e-4 ? dir : fl.normalize();
        P.face = fc;
        Vec3 rc = new Vec3(-fc.z, 0, fc.x);

        // ---- every blow thrown on its frame: wind back, snap out, hold, recover
        double eL = 0, eR = 0;
        Vec3 aimL = GUARD_L, aimR = GUARD_R;
        for (int i = 0; i < struck.size(); i++) {
            Beat b = struck.get(i);
            double ph = t - b.t();
            if (ph < -3 || ph > 10) continue;
            boolean heavy = b.kind() == Beat.ERASE || b.x() >= 2.5;
            double hold = heavy ? 7 : 3;
            double e = ph < 0 ? -0.3 * (ph + 3) / 3.0 : ph < 1.5 ? ph / 1.5 : ph < 1.5 + hold ? 1 : 1 - smooth((ph - 1.5 - hold) / 5.0);
            Vec3 tgt = toModel(l.at(b.a0(), b.o0()), P.root, fc, rc);
            boolean right = heavy || i % 2 == 0;
            if (right && Math.abs(e) > Math.abs(eR)) {
                eR = e;
                aimR = reach(SHOULDER_R, tgt, UPPER + FORE - 0.02);
            } else if (!right && Math.abs(e) > Math.abs(eL)) {
                eL = e;
                aimL = reach(SHOULDER_L, tgt, UPPER + FORE - 0.02);
            }
            if (i + 1 < struck.size() && struck.get(i + 1).t() - b.t() <= 4 && ph > -1 && ph < 5) {
                P.barrage = true;
                P.aimModel = tgt;
            }
        }
        P.fistR = eR >= 0 ? P.fistR.lerp(aimR, eR) : P.fistR.subtract(aimR.subtract(P.fistR).scale(-eR));
        P.fistL = eL >= 0 ? P.fistL.lerp(aimL, eL) : P.fistL.subtract(aimL.subtract(P.fistL).scale(-eL));
        P.lean += 0.3 * Math.max(Math.abs(eL), Math.abs(eR));
        P.twist += -0.35 * eR + 0.35 * eL;
        return P;
    }

    // ------------------------------------------------------------------ rendering
    public static void render(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float pt = e.getPartialTick();
        double time = WFx.time();
        Vec3 cam = e.getCamera().getPosition();
        if (OWN == null) OWN = MultiBufferSource.immediate(new BufferBuilder(1 << 16));
        Matrix4f m = e.getPoseStack().last().pose();
        Matrix3f nm = e.getPoseStack().last().normal();
        boolean any = false;
        for (Player pl : mc.level.players()) {
            if (!isStandUser(pl) || pl.isInvisible()) continue;
            Pose P = choreograph(pl, pt, time);
            boolean firstPerson = pl == mc.player && mc.options.getCameraType().isFirstPerson() && !WCam.running();
            if (firstPerson && P.out < 0.15) continue;   // at your own shoulder, behind the camera
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(P.root.x, P.root.y + 0.5, P.root.z));
            VertexConsumer solid = OWN.getBuffer(RenderType.entitySolid(TEX));
            drawStatue(solid, m, nm, cam, P, light, time);
            if (P.barrage && P.aimModel != null) {
                VertexConsumer ghost = OWN.getBuffer(RenderType.entityTranslucent(TEX));
                drawAfterimages(ghost, m, nm, cam, P, light, time);
            }
            any = true;
        }
        if (any) OWN.endBatch();
    }

    /** The model's basis in the world after its twist and lean. */
    private static Vec3[] basis(Pose P) {
        Vec3 F = P.face, U = new Vec3(0, 1, 0);
        Vec3 R = new Vec3(-F.z, 0, F.x);
        double ct = Math.cos(P.twist), st = Math.sin(P.twist);
        Vec3 F1 = F.scale(ct).add(R.scale(st));
        Vec3 R1 = R.scale(ct).subtract(F.scale(st));
        double cl = Math.cos(P.lean), sl = Math.sin(P.lean);
        Vec3 U2 = U.scale(cl).add(F1.scale(sl));
        Vec3 F2 = F1.scale(cl).subtract(U.scale(sl));
        return new Vec3[]{R1, U2, F2};
    }

    // ------------------------------------------------------------------ the sculpt
    // Everything is smooth, lit surfaces: ellipsoids for muscle and bone, tapered tubes for limbs, tori for bands.
    // Drawn camera-relative; the per-statue context lives in these fields while one statue is being drawn.
    private static final int MARBLE = 0, GOLD = 1, GLOW = 2;
    private static VertexConsumer VC;
    private static Matrix4f MAT;
    private static Matrix3f NMAT;
    private static Vec3[] BAS;
    private static Vec3 ROOT, CAM;
    private static int LIGHT;
    private static float ALPHA = 1f;

    /** Model point -> camera-relative world point. */
    private static Vec3 mw(double x, double y, double z) {
        return ROOT.add(BAS[0].scale(x * S)).add(BAS[1].scale(y * S)).add(BAS[2].scale(z * S)).subtract(CAM);
    }

    private static Vec3 mw(Vec3 p) {
        return mw(p.x, p.y, p.z);
    }

    /** Model direction -> world direction (unit if the input is). */
    private static Vec3 dirW(Vec3 d) {
        return BAS[0].scale(d.x).add(BAS[1].scale(d.y)).add(BAS[2].scale(d.z));
    }

    private static void drawStatue(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, Pose P, int light, double time) {
        VC = vc;
        MAT = m;
        NMAT = nm;
        BAS = basis(P);
        ROOT = P.root;
        CAM = cam;
        LIGHT = light;
        ALPHA = 1f;

        // ---- the torso: a heroic V, carved like a temple statue
        ell(0, 0.07, 0, 0.158, 0.105, 0.108, MARBLE);               // pelvis
        ell(0, 0.3, 0.005, 0.125, 0.17, 0.095, MARBLE);             // abdomen
        for (int k = 0; k < 3; k++) {                               // the abdominal wall
            double y = 0.2 + 0.08 * k;
            for (int sx = -1; sx <= 1; sx += 2) {
                double x = 0.045 * sx;
                ell(x, y, frontZ(x, y) - 0.012, 0.04, 0.032, 0.02, MARBLE);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) ell(0.11 * sx, 0.27, -0.01, 0.04, 0.12, 0.075, MARBLE);   // obliques
        ell(0, 0.6, 0, 0.21, 0.19, 0.12, MARBLE);                   // ribcage
        ell(0, 0.56, -0.02, 0.24, 0.13, 0.08, MARBLE);              // the lats
        for (int sx = -1; sx <= 1; sx += 2) {
            ell(0.095 * sx, 0.66, 0.085, 0.1, 0.065, 0.045, MARBLE);  // pectorals
            tube(mw(0.07 * sx, 0.84, -0.02), mw(0.3 * sx, 0.76, -0.01), 0.05 * S, 0.04 * S, MARBLE);   // trapezius
            ell(0.34 * sx, 0.73, 0, 0.09, 0.095, 0.09, MARBLE);      // deltoids
        }

        // ---- the drape: a marble sash from the left shoulder to the right hip, front and back
        double[][] sash = {{-0.27, 0.8}, {-0.15, 0.66}, {-0.03, 0.52}, {0.07, 0.38}, {0.14, 0.25}, {0.18, 0.13}};
        for (int i = 0; i + 1 < sash.length; i++) {
            double[] a = sash[i], b = sash[i + 1];
            band(mw(a[0], a[1], frontZ(a[0], a[1]) + 0.01), mw(b[0], b[1], frontZ(b[0], b[1]) + 0.01), BAS[2]);
            band(mw(a[0], a[1], backZ(a[0], a[1]) - 0.01), mw(b[0], b[1], backZ(b[0], b[1]) - 0.01), BAS[2].scale(-1));
        }

        // ---- the belt: a gold band, a gold clasp with a burning gem; cloth panels hanging fore and aft
        torus(mw(0, 0.16, 0), dirW(new Vec3(0, 1, 0)), dirW(new Vec3(1, 0, 0)), 0.172 * S, 0.122 * S, 0.022 * S, GOLD);
        ell(0, 0.16, 0.122, 0.045, 0.04, 0.018, GOLD);
        ALPHA = Mth.clamp(P.eyes, 0.6f, 1f);
        ell(0, 0.16, 0.138, 0.018, 0.018, 0.009, GLOW);
        ALPHA = 1f;
        for (int sz = -1; sz <= 1; sz += 2) {
            ell(0, -0.02, 0.108 * sz, 0.085, 0.16, 0.018, MARBLE);
            ell(0, -0.17, 0.108 * sz, 0.08, 0.014, 0.022, GOLD);
        }

        // ---- neck and head: a calm, faceless marble face; two narrow burning eyes; a gold laurel
        tube(mw(0, 0.76, 0), mw(0, 0.93, 0.012), 0.066 * S, 0.056 * S, MARBLE);
        ell(0, 1.03, 0.005, 0.092, 0.118, 0.102, MARBLE);           // cranium
        ell(0, 0.962, 0.032, 0.07, 0.055, 0.07, MARBLE);            // jaw
        ell(0, 1.06, 0.074, 0.084, 0.02, 0.03, MARBLE);             // brow
        ell(0, 1.0, 0.098, 0.014, 0.035, 0.014, MARBLE);            // the bridge of the nose
        ALPHA = Mth.clamp(P.eyes, 0f, 1f);
        for (int sx = -1; sx <= 1; sx += 2) ell(0.037 * sx, 1.035, 0.094, 0.026, 0.008, 0.012, GLOW);
        ALPHA = 1f;
        laurel(1.085);

        // ---- kintsugi: the marble is cracked and mended with gold that glows
        seamFront(new double[][]{{0.07, 0.79}, {0.02, 0.7}, {0.08, 0.62}, {0.03, 0.52}, {0.07, 0.43}});
        seamFront(new double[][]{{0.19, 0.74}, {0.15, 0.66}, {0.2, 0.58}});
        seamFront(new double[][]{{-0.08, 0.36}, {-0.04, 0.28}, {-0.09, 0.2}});
        seamBack(new double[][]{{-0.1, 0.8}, {-0.03, 0.66}, {-0.09, 0.55}, {-0.02, 0.42}});
        seamBack(new double[][]{{0.16, 0.7}, {0.1, 0.6}});
        seamFace(new double[][]{{0.03, 1.13}, {0.045, 1.08}, {0.03, 1.045}});
        seamFace(new double[][]{{0.044, 1.02}, {0.05, 0.98}, {0.035, 0.94}});

        // ---- arms (two-bone, solved to the fists) and legs (hanging as it floats)
        arm(SHOULDER_L, P.fistL, -1);
        arm(SHOULDER_R, P.fistR, 1);
        double sway = Math.sin(time * 1.3) * 0.03;
        leg(HIP_L, sway);
        leg(HIP_R, -sway);
    }

    /** A flat strip of cloth from a to b lying on a surface facing `out` (overlapping lozenges, so it reads as one band). */
    private static void band(Vec3 a, Vec3 b, Vec3 out) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-6) return;
        Vec3 ay = d.scale(1 / len);
        Vec3 ax = ay.cross(out).normalize(), az = ax.cross(ay);
        ellW(a.lerp(b, 0.5), ax, ay, az, 0.042 * S, len * 0.62, 0.012 * S, MARBLE, 12, 6);
    }

    /** Front (or back) surface of the torso at (x, y): the outermost of the carved forms there. */
    private static final double[][] TORSO = {
            {0, 0.07, 0, 0.158, 0.105, 0.108}, {0, 0.3, 0.005, 0.125, 0.17, 0.095}, {0, 0.6, 0, 0.21, 0.19, 0.12},
            {0.095, 0.66, 0.085, 0.1, 0.065, 0.045}, {-0.095, 0.66, 0.085, 0.1, 0.065, 0.045}, {0, 0.56, -0.02, 0.24, 0.13, 0.08}};

    private static double frontZ(double x, double y) {
        double z = 0.05;
        for (double[] e : TORSO) {
            double q = 1 - sq((x - e[0]) / e[3]) - sq((y - e[1]) / e[4]);
            if (q > 0) z = Math.max(z, e[2] + e[5] * Math.sqrt(q));
        }
        return z;
    }

    private static double backZ(double x, double y) {
        double z = -0.05;
        for (double[] e : TORSO) {
            double q = 1 - sq((x - e[0]) / e[3]) - sq((y - e[1]) / e[4]);
            if (q > 0) z = Math.min(z, e[2] - e[5] * Math.sqrt(q));
        }
        return z;
    }

    private static double faceZ(double x, double y) {
        double q = 1 - sq(x / 0.092) - sq((y - 1.03) / 0.118);
        return 0.005 + 0.102 * Math.sqrt(Math.max(0, q));
    }

    private static double sq(double v) {
        return v * v;
    }

    private static void seamFront(double[][] pts) {
        for (int i = 0; i + 1 < pts.length; i++)
            seg(mw(pts[i][0], pts[i][1], frontZ(pts[i][0], pts[i][1]) + 0.002), mw(pts[i + 1][0], pts[i + 1][1], frontZ(pts[i + 1][0], pts[i + 1][1]) + 0.002));
    }

    private static void seamBack(double[][] pts) {
        for (int i = 0; i + 1 < pts.length; i++)
            seg(mw(pts[i][0], pts[i][1], backZ(pts[i][0], pts[i][1]) - 0.002), mw(pts[i + 1][0], pts[i + 1][1], backZ(pts[i + 1][0], pts[i + 1][1]) - 0.002));
    }

    private static void seamFace(double[][] pts) {
        for (int i = 0; i + 1 < pts.length; i++)
            seg(mw(pts[i][0], pts[i][1], faceZ(pts[i][0], pts[i][1]) + 0.002), mw(pts[i + 1][0], pts[i + 1][1], faceZ(pts[i + 1][0], pts[i + 1][1]) + 0.002));
    }

    private static void seg(Vec3 a, Vec3 b) {
        tube(a, b, 0.008 * S, 0.008 * S, GLOW);
        ellW(b, BAS[0], BAS[1], BAS[2], 0.008 * S, 0.008 * S, 0.008 * S, GLOW, 6, 4);
    }

    /** A gold laurel round the brow, open at the front: paired leaves sweeping back along a thin band. */
    private static void laurel(double y) {
        double rx = 0.1, rz = 0.112, cz = 0.0;
        for (int i = 0; i < 16; i++) {
            double ang = Math.toRadians(150 + i * 240.0 / 15.0);   // left temple, round the back, right temple; open at the brow
            Vec3 p = new Vec3(rx * Math.cos(ang), y, cz + rz * Math.sin(ang));
            Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 tan = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
            if (tan.z > 0) tan = tan.scale(-1);                          // leaves sweep toward the back
            for (int k = -1; k <= 1; k += 2) {
                Vec3 along = tan.scale(0.8).add(0, 0.55 * k, 0).normalize();
                Vec3 across = out.cross(along).normalize();
                Vec3 c = p.add(along.scale(0.022)).add(out.scale(0.006));
                ellW(mw(c), dirW(across), dirW(along), dirW(out), 0.011 * S, 0.026 * S, 0.006 * S, GOLD, 8, 5);
            }
        }
        torus(mw(0, y, cz), dirW(new Vec3(0, 1, 0)), dirW(new Vec3(1, 0, 0)), rx * S, rz * S, 0.007 * S, GOLD);
    }

    /** Two-bone arm from shoulder to fist (model space), the elbow bent out and down. */
    private static void arm(Vec3 sh, Vec3 fist, int side) {
        Vec3 d = fist.subtract(sh);
        double len = Mth.clamp(d.length(), 0.12, UPPER + FORE - 0.005);
        Vec3 dir = d.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : d.normalize();
        Vec3 f = sh.add(dir.scale(len));
        double along = (UPPER * UPPER + len * len - FORE * FORE) / (2 * len);
        double h = Math.sqrt(Math.max(0, UPPER * UPPER - along * along));
        Vec3 pole = new Vec3(side * 0.6, -0.6, -0.3);
        Vec3 pp = pole.subtract(dir.scale(pole.dot(dir)));
        pp = pp.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : pp.normalize();
        Vec3 el = sh.add(dir.scale(along)).add(pp.scale(h));
        Vec3 Ws = mw(sh), We = mw(el), Wf = mw(f);
        tube(Ws, We, 0.07 * S, 0.056 * S, MARBLE);
        muscle(Ws, We, 0.42, 0.074 * S, 0.13 * S, MARBLE);             // biceps and triceps
        ellW(We, BAS[0], BAS[1], BAS[2], 0.056 * S, 0.056 * S, 0.056 * S, MARBLE);
        tube(We, Wf, 0.064 * S, 0.047 * S, MARBLE);
        muscle(We, Wf, 0.28, 0.068 * S, 0.12 * S, MARBLE);
        tube(We.lerp(Wf, 0.56), We.lerp(Wf, 0.92), 0.058 * S, 0.054 * S, GOLD);   // the gold bracer
        Vec3 fd = Wf.subtract(We);
        Vec3 ax = fd.lengthSqr() < 1e-10 ? BAS[1] : fd.normalize();
        Vec3 ref = Math.abs(ax.dot(BAS[1])) > 0.9 ? BAS[0] : BAS[1];
        Vec3 bx = ax.cross(ref).normalize(), bz = bx.cross(ax);
        torus(We.lerp(Wf, 0.56), ax, bx, 0.06 * S, 0.06 * S, 0.01 * S, GLOW);
        Vec3 fc = Wf.add(ax.scale(0.045 * S));
        ellW(fc, bx, ax, bz, 0.06 * S, 0.068 * S, 0.058 * S, MARBLE);  // the fist
        ellW(fc.add(ax.scale(0.045 * S)), bx, ax, bz, 0.052 * S, 0.026 * S, 0.05 * S, MARBLE);   // knuckles
    }

    private static void leg(Vec3 hip, double sway) {
        Vec3 knee = hip.add(0, -0.4, 0.08 + sway);
        Vec3 foot = knee.add(0, -0.38, -0.12 + sway);
        Vec3 Wh = mw(hip), Wk = mw(knee), Wf = mw(foot);
        tube(Wh, Wk, 0.1 * S, 0.065 * S, MARBLE);
        muscle(Wh, Wk, 0.42, 0.1 * S, 0.2 * S, MARBLE);                // quadriceps
        ellW(Wk, BAS[0], BAS[1], BAS[2], 0.066 * S, 0.066 * S, 0.066 * S, MARBLE);
        tube(Wk, Wf, 0.07 * S, 0.045 * S, MARBLE);
        Vec3 calf = Wk.lerp(Wf, 0.3).subtract(BAS[2].scale(0.022 * S));
        Vec3 ax = Wf.subtract(Wk).normalize();
        Vec3 bx = BAS[0], bz = bx.cross(ax).normalize();
        ellW(calf, bx, ax, bz, 0.07 * S, 0.13 * S, 0.072 * S, MARBLE);
        torus(Wk.lerp(Wf, 0.86), ax, bx, 0.054 * S, 0.054 * S, 0.016 * S, GOLD);   // anklet
        ellW(mw(foot.add(0, -0.02, 0.06)), BAS[0], BAS[1], BAS[2], 0.046 * S, 0.036 * S, 0.11 * S, MARBLE);
    }

    /** A barrage: afterimage arms, each frame in different places, all driving at the same point. */
    private static void drawAfterimages(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, Pose P, int light, double time) {
        VC = vc;
        MAT = m;
        NMAT = nm;
        BAS = basis(P);
        ROOT = P.root;
        CAM = cam;
        LIGHT = light;
        Random rng = new Random((long) Math.floor(time * 30));
        for (int k = 0; k < 7; k++) {
            Vec3 from = new Vec3((rng.nextDouble() - 0.5) * 0.7, 0.45 + rng.nextDouble() * 0.4, 0.1 + rng.nextDouble() * 0.15);
            Vec3 to = P.aimModel.add((rng.nextDouble() - 0.5) * 0.45, (rng.nextDouble() - 0.5) * 0.45, (rng.nextDouble() - 0.5) * 0.2);
            to = reach(from, to, 0.62);
            Vec3 Wa = mw(from), Wb = mw(to);
            ALPHA = 0.25f + 0.35f * rng.nextFloat();
            tube(Wa, Wb, 0.06 * S, 0.048 * S, MARBLE);
            Vec3 ax = Wb.subtract(Wa).normalize();
            Vec3 ref = Math.abs(ax.dot(BAS[1])) > 0.9 ? BAS[0] : BAS[1];
            Vec3 bx = ax.cross(ref).normalize(), bz = bx.cross(ax);
            ellW(Wb.add(ax.scale(0.05 * S)), bx, ax, bz, 0.06 * S, 0.07 * S, 0.058 * S, MARBLE);
        }
        ALPHA = 1f;
    }

    // ------------------------------------------------------------------ surfaces
    private static void ell(double x, double y, double z, double rx, double ry, double rz, int mat) {
        ellW(mw(x, y, z), BAS[0], BAS[1], BAS[2], rx * S, ry * S, rz * S, mat);
    }

    /** A muscle belly along a bone a->b (world), centred at fraction t. */
    private static void muscle(Vec3 a, Vec3 b, double t, double r, double half, int mat) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-10) return;
        Vec3 ay = d.normalize();
        Vec3 ref = Math.abs(ay.dot(BAS[1])) > 0.9 ? BAS[2] : BAS[1];
        Vec3 ax = ay.cross(ref).normalize(), az = ax.cross(ay);
        ellW(a.lerp(b, t), ax, ay, az, r, Math.min(half, d.length() * 0.5), r * 0.95, mat);
    }

    private static final int EU = 16, EV = 10;

    /** An ellipsoid: centre (camera-relative), unit axes, radii. */
    private static void ellW(Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, double rx, double ry, double rz, int mat) {
        ellW(c, ax, ay, az, rx, ry, rz, mat, EU, EV);
    }

    private static void ellW(Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, double rx, double ry, double rz, int mat, int EU, int EV) {
        double[][] P = new double[(EU + 1) * (EV + 1)][];
        for (int j = 0; j <= EV; j++) {
            double th = -Math.PI / 2 + Math.PI * j / EV, ct = Math.cos(th), st = Math.sin(th);
            for (int i = 0; i <= EU; i++) {
                double ph = 2 * Math.PI * i / EU, cp = Math.cos(ph), sp = Math.sin(ph);
                double lx = ct * cp, ly = st, lz = ct * sp;
                double px = c.x + ax.x * rx * lx + ay.x * ry * ly + az.x * rz * lz;
                double py = c.y + ax.y * rx * lx + ay.y * ry * ly + az.y * rz * lz;
                double pz = c.z + ax.z * rx * lx + ay.z * ry * ly + az.z * rz * lz;
                double nx = lx / rx, ny = ly / ry, nz = lz / rz;
                double wx = ax.x * nx + ay.x * ny + az.x * nz, wy = ax.y * nx + ay.y * ny + az.y * nz, wz = ax.z * nx + ay.z * ny + az.z * nz;
                double nl = Math.sqrt(wx * wx + wy * wy + wz * wz);
                if (nl < 1e-12) nl = 1;
                P[j * (EU + 1) + i] = new double[]{px, py, pz, wx / nl, wy / nl, wz / nl, (double) i / EU, (double) j / EV};
            }
        }
        grid(P, EU, EV, mat);
    }

    private static final int TU = 12, TV = 3;

    /** A tapered tube from a to b (camera-relative), radius r0 at a and r1 at b. */
    private static void tube(Vec3 a, Vec3 b, double r0, double r1, int mat) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-6) return;
        Vec3 ay = d.scale(1 / len);
        Vec3 ref = Math.abs(ay.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 ax = ay.cross(ref).normalize(), az = ax.cross(ay);
        double slope = (r0 - r1) / len;
        double[][] P = new double[(TU + 1) * (TV + 1)][];
        for (int j = 0; j <= TV; j++) {
            double v = (double) j / TV, r = r0 + (r1 - r0) * v;
            for (int i = 0; i <= TU; i++) {
                double ph = 2 * Math.PI * i / TU, cp = Math.cos(ph), sp = Math.sin(ph);
                double rxv = ax.x * cp + az.x * sp, ryv = ax.y * cp + az.y * sp, rzv = ax.z * cp + az.z * sp;
                double nx = rxv + ay.x * slope, ny = ryv + ay.y * slope, nz = rzv + ay.z * slope;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                P[j * (TU + 1) + i] = new double[]{a.x + d.x * v + rxv * r, a.y + d.y * v + ryv * r, a.z + d.z * v + rzv * r,
                        nx / nl, ny / nl, nz / nl, (double) i / TU, v};
            }
        }
        grid(P, TU, TV, mat);
    }

    private static final int RU = 24, RV = 6;

    /** A torus: centre, ring-plane normal n, in-plane axis x, major radii along x and n x x, tube radius r. */
    private static void torus(Vec3 c, Vec3 n, Vec3 x, double R1, double R2, double r, int mat) {
        Vec3 nn = n.normalize();
        Vec3 xx = x.subtract(nn.scale(x.dot(nn))).normalize();
        Vec3 zz = nn.cross(xx);
        double[][] P = new double[(RU + 1) * (RV + 1)][];
        for (int j = 0; j <= RV; j++) {
            double ps = 2 * Math.PI * j / RV, cs = Math.cos(ps), ss = Math.sin(ps);
            for (int i = 0; i <= RU; i++) {
                double ph = 2 * Math.PI * i / RU, cp = Math.cos(ph), sp = Math.sin(ph);
                Vec3 ring = xx.scale(R1 * cp).add(zz.scale(R2 * sp));
                Vec3 out = xx.scale(cp / R1).add(zz.scale(sp / R2)).normalize();
                Vec3 nrm = out.scale(cs).add(nn.scale(ss));
                Vec3 p = c.add(ring).add(nrm.scale(r));
                P[j * (RU + 1) + i] = new double[]{p.x, p.y, p.z, nrm.x, nrm.y, nrm.z, (double) i / RU, (double) j / RV};
            }
        }
        grid(P, RU, RV, mat);
    }

    /** Emit a (nu x nv) grid of quads, each wound counter-clockwise as seen from outside its normals. */
    private static void grid(double[][] P, int nu, int nv, int mat) {
        float u0 = mat == MARBLE ? 0f : 0.5f;
        int light = mat == GLOW ? BRIGHT : LIGHT;
        for (int j = 0; j < nv; j++) {
            for (int i = 0; i < nu; i++) {
                double[] a = P[j * (nu + 1) + i], b = P[j * (nu + 1) + i + 1], c = P[(j + 1) * (nu + 1) + i + 1], d = P[(j + 1) * (nu + 1) + i];
                // geometric normal from the diagonals vs. the smooth normals
                double e1x = c[0] - a[0], e1y = c[1] - a[1], e1z = c[2] - a[2];
                double e2x = d[0] - b[0], e2y = d[1] - b[1], e2z = d[2] - b[2];
                double gx = e1y * e2z - e1z * e2y, gy = e1z * e2x - e1x * e2z, gz = e1x * e2y - e1y * e2x;
                double sx = a[3] + b[3] + c[3] + d[3], sy = a[4] + b[4] + c[4] + d[4], sz = a[5] + b[5] + c[5] + d[5];
                if (gx * sx + gy * sy + gz * sz >= 0) {
                    vert(a, u0, light);
                    vert(b, u0, light);
                    vert(c, u0, light);
                    vert(d, u0, light);
                } else {
                    vert(a, u0, light);
                    vert(d, u0, light);
                    vert(c, u0, light);
                    vert(b, u0, light);
                }
            }
        }
    }

    private static void vert(double[] p, float u0, int light) {
        VC.vertex(MAT, (float) p[0], (float) p[1], (float) p[2]).color(1f, 1f, 1f, ALPHA)
                .uv(u0 + 0.5f * (float) p[6], (float) p[7]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(NMAT, (float) p[3], (float) p[4], (float) p[5]).endVertex();
    }
}
