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
 * The World Over Heaven's Stand: a solid living statue. White marble, cracked and mended with seams of gold that
 * glow; a crown of gold rays and a halo ring behind its head; two narrow gold eyes in a smooth face; gold vambraces,
 * a belt with a clock medallion, gold anklets. An original figure, built here from oriented boxes, lit by the world
 * and depth-tested like any solid thing.
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
        Vec3 home = feet.add(f.scale(-0.55)).add(r.scale(0.3)).add(0, 0.95 + bob, 0);
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

    private static Vec3 wp(Vec3[] B, Vec3 root, Vec3 cam, double x, double y, double z) {
        return root.add(B[0].scale(x * S)).add(B[1].scale(y * S)).add(B[2].scale(z * S)).subtract(cam);
    }

    private static Vec3 wp(Vec3[] B, Vec3 root, Vec3 cam, Vec3 p) {
        return wp(B, root, cam, p.x, p.y, p.z);
    }

    private static void drawStatue(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, Pose P, int light, double time) {
        Vec3[] B = basis(P);
        Vec3 R = B[0], U = B[1], F = B[2], root = P.root;
        // torso: pelvis, abdomen, a broad chest; a gold belt with a clock medallion; a flat gold gorget
        box(vc, m, nm, wp(B, root, cam, 0, 0.08, 0), R, U, F, 0.19 * S, 0.09 * S, 0.12 * S, false, light, 1f);
        box(vc, m, nm, wp(B, root, cam, 0, 0.33, 0.01), R, U, F, 0.165 * S, 0.14 * S, 0.11 * S, false, light, 1f);
        box(vc, m, nm, wp(B, root, cam, 0, 0.64, 0), R, U, F, 0.27 * S, 0.17 * S, 0.15 * S, false, light, 1f);
        box(vc, m, nm, wp(B, root, cam, 0, 0.17, 0), R, U, F, 0.2 * S, 0.03 * S, 0.125 * S, true, light, 1f);
        box(vc, m, nm, wp(B, root, cam, 0, 0.17, 0.13), R, U, F, 0.055 * S, 0.055 * S, 0.012 * S, true, light, 1f);
        box(vc, m, nm, wp(B, root, cam, 0, 0.8, 0), R, U, F, 0.2 * S, 0.025 * S, 0.14 * S, true, light, 1f);
        // shoulders
        box(vc, m, nm, wp(B, root, cam, -0.35, 0.73, 0), R, U, F, 0.11 * S, 0.1 * S, 0.11 * S, false, light, 1f);
        box(vc, m, nm, wp(B, root, cam, 0.35, 0.73, 0), R, U, F, 0.11 * S, 0.1 * S, 0.11 * S, false, light, 1f);
        // neck and head: a smooth face with two narrow gold eyes
        box(vc, m, nm, wp(B, root, cam, 0, 0.86, 0), R, U, F, 0.07 * S, 0.06 * S, 0.07 * S, false, light, 1f);
        box(vc, m, nm, wp(B, root, cam, 0, 1.03, 0), R, U, F, 0.115 * S, 0.13 * S, 0.12 * S, false, light, 1f);
        float eyeA = Mth.clamp(P.eyes, 0f, 1f);
        box(vc, m, nm, wp(B, root, cam, -0.045, 1.05, 0.122), R, U, F, 0.03 * S, 0.009 * S, 0.004 * S, true, BRIGHT, eyeA);
        box(vc, m, nm, wp(B, root, cam, 0.045, 1.05, 0.122), R, U, F, 0.03 * S, 0.009 * S, 0.004 * S, true, BRIGHT, eyeA);
        // the crown: gold rays fanned behind the head, and a thin halo ring among them
        Vec3 crown = new Vec3(0, 1.05, -0.14);
        for (int i = 0; i < 7; i++) {
            double a = Math.toRadians(18 + i * 24);
            Vec3 d = new Vec3(Math.cos(a), Math.sin(a), 0);
            double len = 0.3 + 0.07 * (i % 2);
            limb(vc, m, nm, wp(B, root, cam, crown.add(d.scale(0.15))), wp(B, root, cam, crown.add(d.scale(len))), 0.016 * S, true, light, 1f);
        }
        for (int i = 0; i < 16; i++) {
            double a0 = i * Math.PI * 2 / 16, a1 = (i + 1) * Math.PI * 2 / 16;
            limb(vc, m, nm, wp(B, root, cam, crown.add(new Vec3(Math.cos(a0), Math.sin(a0), 0).scale(0.19))),
                    wp(B, root, cam, crown.add(new Vec3(Math.cos(a1), Math.sin(a1), 0).scale(0.19))), 0.01 * S, true, BRIGHT, 1f);
        }
        // kintsugi: the marble is cracked, and the cracks are filled with glowing gold
        seam(vc, m, nm, B, root, cam, new double[][]{{0.06, 0.79, 0.152}, {-0.05, 0.68, 0.152}, {0.05, 0.58, 0.152}, {-0.03, 0.49, 0.152}});
        seam(vc, m, nm, B, root, cam, new double[][]{{0.2, 0.75, 0.152}, {0.14, 0.66, 0.152}});
        seam(vc, m, nm, B, root, cam, new double[][]{{-0.08, 0.78, -0.152}, {0.04, 0.62, -0.152}, {-0.02, 0.5, -0.152}});
        seam(vc, m, nm, B, root, cam, new double[][]{{0.03, 1.12, 0.122}, {-0.02, 1.08, 0.122}});
        seam(vc, m, nm, B, root, cam, new double[][]{{-0.1, 0.4, 0.113}, {-0.04, 0.3, 0.113}, {-0.08, 0.22, 0.113}});
        // arms, solved to the fists
        arm(vc, m, nm, B, root, cam, SHOULDER_L, P.fistL, -1, light);
        arm(vc, m, nm, B, root, cam, SHOULDER_R, P.fistR, 1, light);
        // legs, hanging as it floats
        double sway = Math.sin(time * 1.3) * 0.03;
        leg(vc, m, nm, B, root, cam, HIP_L, sway, light);
        leg(vc, m, nm, B, root, cam, HIP_R, -sway, light);
    }

    private static void seam(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3[] B, Vec3 root, Vec3 cam, double[][] pts) {
        for (int i = 0; i + 1 < pts.length; i++) {
            limb(vc, m, nm, wp(B, root, cam, pts[i][0], pts[i][1], pts[i][2]), wp(B, root, cam, pts[i + 1][0], pts[i + 1][1], pts[i + 1][2]),
                    0.011 * S, true, BRIGHT, 1f);
        }
    }

    /** Two-bone arm from shoulder to fist (model space), the elbow bent out and down. */
    private static void arm(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3[] B, Vec3 root, Vec3 cam, Vec3 sh, Vec3 fist, int side, int light) {
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
        Vec3 Ws = wp(B, root, cam, sh), We = wp(B, root, cam, el), Wf = wp(B, root, cam, f);
        limb(vc, m, nm, Ws, We, 0.075 * S, false, light, 1f);
        limb(vc, m, nm, We, Wf, 0.065 * S, false, light, 1f);
        limb(vc, m, nm, We.lerp(Wf, 0.3), We.lerp(Wf, 0.85), 0.076 * S, true, light, 1f);   // gold vambrace
        Vec3 fd = Wf.subtract(We);
        Vec3 fe = fd.lengthSqr() < 1e-8 ? Wf : Wf.add(fd.normalize().scale(0.05 * S));
        limb(vc, m, nm, Wf, fe.add(fe.subtract(Wf)), 0.072 * S, false, light, 1f);           // the fist
    }

    private static void leg(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3[] B, Vec3 root, Vec3 cam, Vec3 hip, double sway, int light) {
        Vec3 knee = hip.add(0, -0.4, 0.08 + sway);
        Vec3 foot = knee.add(0, -0.38, -0.12 + sway);
        Vec3 Wh = wp(B, root, cam, hip), Wk = wp(B, root, cam, knee), Wf = wp(B, root, cam, foot);
        limb(vc, m, nm, Wh, Wk, 0.085 * S, false, light, 1f);
        limb(vc, m, nm, Wk, Wf, 0.07 * S, false, light, 1f);
        limb(vc, m, nm, Wk.lerp(Wf, 0.78), Wk.lerp(Wf, 0.92), 0.075 * S, true, light, 1f);  // gold anklet
        limb(vc, m, nm, Wf, wp(B, root, cam, foot.add(0, -0.03, 0.13)), 0.06 * S, false, light, 1f);
    }

    /** A barrage: afterimage fists, each frame in different places, all driving at the same point. */
    private static void drawAfterimages(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 cam, Pose P, int light, double time) {
        Vec3[] B = basis(P);
        Random rng = new Random((long) Math.floor(time * 30));
        for (int k = 0; k < 7; k++) {
            Vec3 from = new Vec3((rng.nextDouble() - 0.5) * 0.7, 0.45 + rng.nextDouble() * 0.4, 0.1 + rng.nextDouble() * 0.15);
            Vec3 to = P.aimModel.add((rng.nextDouble() - 0.5) * 0.45, (rng.nextDouble() - 0.5) * 0.45, (rng.nextDouble() - 0.5) * 0.2);
            to = reach(from, to, 0.62);
            Vec3 Wa = wp(B, P.root, cam, from), Wb = wp(B, P.root, cam, to);
            float a = 0.25f + 0.35f * rng.nextFloat();
            limb(vc, m, nm, Wa, Wb, 0.06 * S, false, light, a);
            Vec3 d = Wb.subtract(Wa).normalize();
            limb(vc, m, nm, Wb, Wb.add(d.scale(0.1 * S)), 0.07 * S, false, light, a);
        }
    }

    // ------------------------------------------------------------------ geometry
    private static void limb(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 a, Vec3 b, double thick, boolean gold, int light, float alpha) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-5) return;
        Vec3 ay = d.scale(1 / len);
        Vec3 ref = Math.abs(ay.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 ax = ay.cross(ref).normalize();
        Vec3 az = ax.cross(ay);
        box(vc, m, nm, a.add(d.scale(0.5)), ax, ay, az, thick, len / 2 + thick * 0.3, thick, gold, light, alpha);
    }

    /** An oriented box: centre c (camera-relative), unit axes, half extents; marble or gold half of the texture. */
    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 c, Vec3 ax, Vec3 ay, Vec3 az,
                            double hx, double hy, double hz, boolean gold, int light, float alpha) {
        float u0 = gold ? 0.5f : 0f, u1 = gold ? 1f : 0.5f;
        Vec3[] axes = {ax, ay, az};
        double[] h = {hx, hy, hz};
        for (int i = 0; i < 3; i++) {
            Vec3 ua = axes[(i + 1) % 3];
            double ha = h[(i + 1) % 3], hb = h[(i + 2) % 3];
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3 n = axes[i].scale(sgn);
                Vec3 fc = c.add(n.scale(h[i]));
                Vec3 A = ua.scale(ha);
                Vec3 Bv = n.cross(ua).scale(hb);   // a x b points along n: counter-clockwise seen from outside
                v(vc, m, nm, fc.subtract(A).subtract(Bv), n, u0, 1f, light, alpha);
                v(vc, m, nm, fc.add(A).subtract(Bv), n, u1, 1f, light, alpha);
                v(vc, m, nm, fc.add(A).add(Bv), n, u1, 0f, light, alpha);
                v(vc, m, nm, fc.subtract(A).add(Bv), n, u0, 0f, light, alpha);
            }
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 p, Vec3 n, float u, float vv, int light, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(1f, 1f, 1f, a).uv(u, vv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, (float) n.x, (float) n.y, (float) n.z).endVertex();
    }
}
