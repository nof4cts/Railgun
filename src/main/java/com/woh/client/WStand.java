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
 * The World Over Heaven's Stand: an original living statue, built blocky like the world around it. White marble,
 * cracked and mended with seams of gold that glow; a plain face with two narrow burning eyes; a gold laurel; a marble
 * sash across the chest; a gold belt with a burning gem and cloth panels; gold bracers and anklets. Oriented boxes
 * textured at 16 texels a block, lit by the world and depth-tested like any solid thing.
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

    // ------------------------------------------------------------------ the build
    // Blocky, like everything else in the world: oriented boxes only, the texture laid on at a steady 16 pixels a
    // block so its marble and gold read as pixels, not smears. Drawn camera-relative; the per-statue context lives in
    // these fields while one statue is being drawn.
    private static final int MARBLE = 0, GOLD = 1, GLOW = 2;
    private static final double PX = 16.0;                // texels per block
    private static VertexConsumer VC;
    private static Matrix4f MAT;
    private static Matrix3f NMAT;
    private static Vec3[] BAS;
    private static Vec3 ROOT, CAM;
    private static int LIGHT, SEED;
    private static float ALPHA = 1f;

    /** Model point -> camera-relative world point. */
    private static Vec3 mw(double x, double y, double z) {
        return ROOT.add(BAS[0].scale(x * S)).add(BAS[1].scale(y * S)).add(BAS[2].scale(z * S)).subtract(CAM);
    }

    private static Vec3 mw(Vec3 p) {
        return mw(p.x, p.y, p.z);
    }

    private static void begin(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, Pose P, int light) {
        VC = vc;
        MAT = m;
        NMAT = nm;
        BAS = basis(P);
        ROOT = P.root;
        CAM = cam;
        LIGHT = light;
        SEED = 0;
        ALPHA = 1f;
    }

    private static void drawStatue(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, Pose P, int light, double time) {
        begin(vc, m, nm, cam, P, light);

        // ---- the body: a hard-cut marble statue, broad at the chest, narrow at the waist
        cube(0, 0.08, 0, 0.16, 0.1, 0.11, MARBLE);                 // hips
        cube(0, 0.31, 0, 0.14, 0.13, 0.095, MARBLE);               // waist
        cube(0, 0.6, 0, 0.22, 0.16, 0.12, MARBLE);                 // chest
        for (int sx = -1; sx <= 1; sx += 2) {
            cube(0.1 * sx, 0.67, 0.125, 0.09, 0.055, 0.012, MARBLE);           // chest plates
            cube(0.34 * sx, 0.72, 0, 0.1, 0.09, 0.1, MARBLE);                  // shoulders
            cube(0.34 * sx, 0.815, 0, 0.08, 0.008, 0.08, GOLD);                // gold caps on the shoulders
            for (int k = 0; k < 3; k++) cube(0.045 * sx, 0.22 + 0.075 * k, 0.1, 0.035, 0.028, 0.01, MARBLE);   // abs
        }

        // ---- the sash: flat marble cloth, shoulder to hip, front and back
        double[][] sash = {{-0.27, 0.8}, {-0.15, 0.66}, {-0.03, 0.52}, {0.07, 0.38}, {0.14, 0.25}, {0.18, 0.13}};
        for (int i = 0; i + 1 < sash.length; i++) {
            double[] a = sash[i], b = sash[i + 1];
            strip(mw(a[0], a[1], frontZ(a[1]) + 0.012), mw(b[0], b[1], frontZ(b[1]) + 0.012), BAS[2], 0.04, 0.012, MARBLE);
            strip(mw(a[0], a[1], -frontZ(a[1]) - 0.012), mw(b[0], b[1], -frontZ(b[1]) - 0.012), BAS[2], 0.04, 0.012, MARBLE);
        }

        // ---- the belt, its burning gem, cloth panels fore and aft with gold hems
        cube(0, 0.17, 0, 0.17, 0.025, 0.12, GOLD);
        cube(0, 0.17, 0.122, 0.04, 0.035, 0.012, GOLD);
        ALPHA = Mth.clamp(P.eyes, 0.6f, 1f);
        cube(0, 0.17, 0.136, 0.016, 0.016, 0.006, GLOW);
        ALPHA = 1f;
        for (int sz = -1; sz <= 1; sz += 2) {
            cube(0, -0.04, 0.118 * sz, 0.08, 0.15, 0.01, MARBLE);
            cube(0, -0.18, 0.118 * sz, 0.082, 0.014, 0.014, GOLD);
        }

        // ---- neck and head: a plain marble face, two burning slits, a gold laurel
        cube(0, 0.85, 0, 0.065, 0.06, 0.065, MARBLE);
        cube(0, 1.02, 0, 0.11, 0.115, 0.11, MARBLE);
        ALPHA = Mth.clamp(P.eyes, 0f, 1f);
        for (int sx = -1; sx <= 1; sx += 2) cube(0.045 * sx, 1.03, 0.112, 0.03, 0.009, 0.004, GLOW);
        ALPHA = 1f;
        laurel(1.1);

        // ---- kintsugi: cracks in the marble filled with glowing gold
        seam(new double[][]{{0.07, 0.75}, {0.02, 0.68}, {0.08, 0.6}, {0.03, 0.5}}, 0.12);
        seam(new double[][]{{0.18, 0.72}, {0.14, 0.64}, {0.19, 0.56}}, 0.12);
        seam(new double[][]{{-0.08, 0.38}, {-0.04, 0.3}, {-0.09, 0.22}}, 0.095);
        seam(new double[][]{{-0.1, 0.74}, {-0.03, 0.64}, {-0.09, 0.54}}, -0.12);
        seam(new double[][]{{0.04, 1.12}, {0.06, 1.07}, {0.045, 1.045}}, 0.11);
        seam(new double[][]{{0.06, 1.015}, {0.065, 0.97}, {0.05, 0.93}}, 0.11);

        // ---- arms (two-bone, solved to the fists) and legs (hanging as it floats)
        arm(SHOULDER_L, P.fistL, -1);
        arm(SHOULDER_R, P.fistR, 1);
        double sway = Math.sin(time * 1.3) * 0.03;
        leg(HIP_L, sway);
        leg(HIP_R, -sway);
    }

    /** The torso's front face at height y (the back is its mirror). */
    private static double frontZ(double y) {
        return y > 0.44 ? 0.12 : y > 0.2 ? 0.095 : 0.11;
    }

    private static void seam(double[][] pts, double z) {
        Vec3 out = z > 0 ? BAS[2] : BAS[2].scale(-1);
        double zz = z + Math.signum(z) * 0.003;
        for (int i = 0; i + 1 < pts.length; i++)
            strip(mw(pts[i][0], pts[i][1], zz), mw(pts[i + 1][0], pts[i + 1][1], zz), out, 0.009, 0.004, GLOW);
    }

    /** A gold laurel: a band round the brow, and stepped gold leaves rising along the sides and back. */
    private static void laurel(double y) {
        cube(0, y, -0.116, 0.115, 0.012, 0.006, GOLD);                        // back of the band
        for (int sx = -1; sx <= 1; sx += 2) {
            cube(0.116 * sx, y, -0.02, 0.006, 0.012, 0.1, GOLD);              // sides of the band
            for (int k = 0; k < 3; k++) {                                     // leaves, stepping up toward the back
                double z = 0.05 - 0.06 * k;
                cube(0.12 * sx, y + 0.03 + 0.012 * k, z, 0.008, 0.02, 0.012, GOLD);
                cube(0.12 * sx, y - 0.025, z - 0.02, 0.008, 0.012, 0.012, GOLD);
            }
        }
        for (int k = -1; k <= 1; k++) cube(0.045 * k, y + 0.03, -0.12, 0.014, 0.02, 0.008, GOLD);
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
        limb(Ws, We, 0.075, MARBLE);
        limb(We, Wf, 0.066, MARBLE);
        limb(We.lerp(Wf, 0.5), We.lerp(Wf, 0.92), 0.076, GOLD);              // gold bracer
        limb(We.lerp(Wf, 0.49), We.lerp(Wf, 0.53), 0.08, GLOW);              // its glowing rim
        Vec3 fd = Wf.subtract(We);
        Vec3 fe = fd.lengthSqr() < 1e-8 ? Wf : Wf.add(fd.normalize().scale(0.1 * S));
        limb(Wf, fe, 0.075, MARBLE);                                          // the fist
    }

    private static void leg(Vec3 hip, double sway) {
        Vec3 knee = hip.add(0, -0.4, 0.08 + sway);
        Vec3 foot = knee.add(0, -0.38, -0.12 + sway);
        Vec3 Wh = mw(hip), Wk = mw(knee), Wf = mw(foot);
        limb(Wh, Wk, 0.09, MARBLE);
        limb(Wk, Wf, 0.075, MARBLE);
        limb(Wk.lerp(Wf, 0.8), Wk.lerp(Wf, 0.92), 0.08, GOLD);               // anklet
        limb(Wf, mw(foot.add(0, -0.02, 0.14)), 0.065, MARBLE);                // foot
    }

    /** A barrage: afterimage arms, each frame in different places, all driving at the same point. */
    private static void drawAfterimages(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, Pose P, int light, double time) {
        begin(vc, m, nm, cam, P, light);
        Random rng = new Random((long) Math.floor(time * 30));
        for (int k = 0; k < 7; k++) {
            Vec3 from = new Vec3((rng.nextDouble() - 0.5) * 0.7, 0.45 + rng.nextDouble() * 0.4, 0.1 + rng.nextDouble() * 0.15);
            Vec3 to = P.aimModel.add((rng.nextDouble() - 0.5) * 0.45, (rng.nextDouble() - 0.5) * 0.45, (rng.nextDouble() - 0.5) * 0.2);
            to = reach(from, to, 0.62);
            Vec3 Wa = mw(from), Wb = mw(to);
            ALPHA = 0.25f + 0.35f * rng.nextFloat();
            limb(Wa, Wb, 0.062, MARBLE);
            Vec3 dd = Wb.subtract(Wa).normalize();
            limb(Wb, Wb.add(dd.scale(0.1 * S)), 0.075, MARBLE);
        }
        ALPHA = 1f;
    }

    // ------------------------------------------------------------------ boxes
    /** A box in model space, aligned to the statue (centre, half extents in model units). */
    private static void cube(double x, double y, double z, double hx, double hy, double hz, int mat) {
        box(mw(x, y, z), BAS[0], BAS[1], BAS[2], hx * S, hy * S, hz * S, mat);
    }

    /** A flat strip from a to b (camera-relative) lying on a surface facing `out`; half width and half thickness in model units. */
    private static void strip(Vec3 a, Vec3 b, Vec3 out, double hw, double ht, int mat) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-6) return;
        Vec3 ay = d.scale(1 / len);
        Vec3 ax = ay.cross(out).normalize(), az = ax.cross(ay);
        box(a.add(d.scale(0.5)), ax, ay, az, hw * S, len / 2 + hw * S * 0.5, ht * S, mat);
    }

    /** A square-section limb from a to b (camera-relative), half thickness in model units. */
    private static void limb(Vec3 a, Vec3 b, double thick, int mat) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-5) return;
        Vec3 ay = d.scale(1 / len);
        Vec3 ref = Math.abs(ay.dot(BAS[1])) > 0.9 ? BAS[2] : BAS[1];
        Vec3 ax = ay.cross(ref).normalize();
        Vec3 az = ax.cross(ay);
        double t = thick * S;
        box(a.add(d.scale(0.5)), ax, ay, az, t, len / 2 + t * 0.3, t, mat);
    }

    /**
     * An oriented box: centre c (camera-relative), unit axes, half extents in blocks. Each face takes a window of the
     * marble or gold half of the texture sized to the face (16 texels a block), offset per box so no two match.
     */
    private static void box(Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, double hx, double hy, double hz, int mat) {
        int light = mat == GLOW ? BRIGHT : LIGHT;
        float base = mat == MARBLE ? 0f : 0.5f;
        Vec3[] axes = {ax, ay, az};
        double[] h = {hx, hy, hz};
        int seed = SEED++;
        for (int i = 0; i < 3; i++) {
            Vec3 ua = axes[(i + 1) % 3];
            double ha = h[(i + 1) % 3], hb = h[(i + 2) % 3];
            float uw = (float) Math.min(0.5, 2 * ha * PX / 64.0), vh = (float) Math.min(1.0, 2 * hb * PX / 32.0);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int k = seed * 7 + i * 2 + (sgn + 1) / 2;
                float u0 = base + (float) (((k * 0.37) % 1.0) * (0.5 - uw)), v0 = (float) (((k * 0.61) % 1.0) * (1.0 - vh));
                Vec3 n = axes[i].scale(sgn);
                Vec3 fc = c.add(n.scale(h[i]));
                Vec3 A = ua.scale(ha);
                Vec3 Bv = n.cross(ua).scale(hb);   // A x B points along n: counter-clockwise seen from outside
                v(fc.subtract(A).subtract(Bv), n, u0, v0 + vh, light);
                v(fc.add(A).subtract(Bv), n, u0 + uw, v0 + vh, light);
                v(fc.add(A).add(Bv), n, u0 + uw, v0, light);
                v(fc.subtract(A).add(Bv), n, u0, v0, light);
            }
        }
    }

    private static void v(Vec3 p, Vec3 n, float u, float vv, int light) {
        VC.vertex(MAT, (float) p.x, (float) p.y, (float) p.z).color(1f, 1f, 1f, ALPHA).uv(u, vv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(NMAT, (float) n.x, (float) n.y, (float) n.z).endVertex();
    }
}
