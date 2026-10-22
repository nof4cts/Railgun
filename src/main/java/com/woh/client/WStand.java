package com.woh.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.client.ClientSorcerers;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.woh.common.WMove;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * The World Over Heaven's apparition: a towering crystalline figure of overwritten reality that stands at the
 * summoner's shoulder - a faceted icy-crystal body laced with gold filigree down its chest and spine, a bladed
 * crown, and a single red eye. Drawn as additive light built from our own geometry (no model, no texture, nothing
 * taken from any existing design), so it glows against anything. It surges forward when he acts.
 */
public final class WStand {
    /** Entity id -> the tick (client gametime, *100 for sub-tick) a cast surge began, and its strength. */
    private static final Map<Integer, double[]> SURGE = new HashMap<>();   // {startSeconds, strength}
    private static MultiBufferSource.BufferSource OWN;

    private WStand() {}

    /** Called when a WOH player begins a cast, so the apparition lunges and flares. */
    public static void surge(int entityId, WMove move) {
        double strength = move == null ? 0.6 : (move.cutscene ? 1.3 : 0.8) + move.tier * 0.15;
        SURGE.put(entityId, new double[]{WFx.time(), strength});
    }

    public static void clear() {
        SURGE.clear();
    }

    /** True for a living player the client knows wields The World Over Heaven right now. */
    private static boolean isStandUser(Entity ent) {
        if (!(ent instanceof Player)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (ent == mc.player) return ClientSorcerers.mine() == Sorcerer.WORLD_OVER_HEAVEN;
        // other players: shown while they are casting or ascended (the only times the client is told)
        return SURGE.containsKey(ent.getId()) || WCasts.overdrive(ent);
    }

    public static void render(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        long gt = mc.level.getGameTime();
        float pt = e.getPartialTick();
        double time = WFx.time();
        Vec3 cam = e.getCamera().getPosition();
        if (OWN == null) OWN = MultiBufferSource.immediate(new BufferBuilder(1 << 15));
        VertexConsumer vc = OWN.getBuffer(RenderType.lightning());
        Matrix4f m = e.getPoseStack().last().pose();
        boolean any = false;

        for (Player pl : mc.level.players()) {
            if (!isStandUser(pl) || pl.isInvisible()) continue;
            // first-person on your own stand: keep it behind you so it doesn't fill the screen
            boolean self = pl == mc.player;
            boolean firstPerson = self && mc.options.getCameraType().isFirstPerson();

            float yaw = Mth.lerp(pt, pl.yBodyRotO, pl.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 feet = pl.getPosition(pt).subtract(cam);
            Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));   // where the player faces (flat)
            Vec3 r = new Vec3(f.z, 0, -f.x);
            Vec3 up = new Vec3(0, 1, 0);

            double[] s = SURGE.get(pl.getId());
            double lunge = 0, flare = 0;
            if (s != null) {
                double age = time - s[0];
                if (age >= 0 && age < 1.1) {
                    double k = Math.exp(-age * 3.5);
                    lunge = k * 0.9 * s[1];
                    flare = k * s[1];
                } else if (age >= 1.1) {
                    SURGE.remove(pl.getId());
                }
            }

            double scale = (self && !firstPerson ? 1.22 : 1.3) * (1 + flare * 0.12);
            double bob = Math.sin(time * 1.7 + pl.getId()) * 0.06 + Math.sin(time * 3.1) * 0.02;
            // the apparition looms just behind him, its head rising a little above his, leaning in when he acts
            Vec3 base = feet
                    .add(f.scale(-0.62 + lunge * 1.3))
                    .add(0, 0.72 + bob, 0);
            if (firstPerson) base = base.add(f.scale(-0.55));   // further back out of view

            drawFigure(vc, m, base, f, r, up, scale, time, flare);
            any = true;
        }
        if (any) OWN.endBatch();
    }

    // palette (additive light): icy crystal, gold filigree, red eye
    private static final float[] CRYSTAL = {0.55f, 0.82f, 1.0f};
    private static final float[] CRYSTAL_DIM = {0.30f, 0.52f, 0.78f};
    private static final float[] GOLD = {1.0f, 0.80f, 0.34f};
    private static final float[] RED = {1.0f, 0.12f, 0.12f};

    /** Build the whole figure from facets and edges, bottom (waist) to the crown. */
    private static void drawFigure(VertexConsumer vc, Matrix4f m, Vec3 o, Vec3 f, Vec3 r, Vec3 u,
                                   double scale, double time, double flare) {
        float a = (float) (0.42 + 0.10 * Math.sin(time * 5.0) + flare * 0.35);
        float ga = (float) (0.65 + 0.15 * Math.sin(time * 7.0 + 1.0) + flare * 0.35);
        double sw = 0.42 * scale, torsoH = 1.15 * scale;

        // ---- torso: a tapered crystalline column of facets (wider shoulders, narrow waist)
        Vec3 waistL = o.add(r.scale(-sw * 0.6)), waistR = o.add(r.scale(sw * 0.6));
        Vec3 chest = o.add(u.scale(torsoH * 0.62));
        Vec3 shL = chest.add(r.scale(-sw)).add(f.scale(-0.04 * scale));
        Vec3 shR = chest.add(r.scale(sw)).add(f.scale(-0.04 * scale));
        Vec3 neck = o.add(u.scale(torsoH));
        // torso as a solid tapered crystal prism (front, back and two sides), so it reads as a volume from any angle
        Vec3 dd = f.scale(0.16 * scale);
        facet(vc, m, waistL.add(dd), waistR.add(dd), shR.add(dd), shL.add(dd), CRYSTAL, a, Vec3.ZERO);        // front
        facet(vc, m, waistL.subtract(dd), waistR.subtract(dd), shR.subtract(dd), shL.subtract(dd), CRYSTAL_DIM, a * 0.8f, Vec3.ZERO);  // back
        facet(vc, m, waistL.subtract(dd), waistL.add(dd), shL.add(dd), shL.subtract(dd), CRYSTAL_DIM, a * 0.7f, Vec3.ZERO);           // left
        facet(vc, m, waistR.add(dd), waistR.subtract(dd), shR.subtract(dd), shR.add(dd), CRYSTAL_DIM, a * 0.7f, Vec3.ZERO);           // right
        // the gold filigree running down the chest and the spine
        edge(vc, m, neck.add(f.scale(0.15 * scale)), o.add(f.scale(0.15 * scale)), GOLD, ga, 0.05 * scale, r);
        edge(vc, m, neck.add(f.scale(-0.15 * scale)), o.add(f.scale(-0.15 * scale)), GOLD, ga * 0.85f, 0.05 * scale, r);
        // collar between the shoulders
        edge(vc, m, shL, shR, GOLD, ga, 0.05 * scale, u);

        // ---- shoulders and arms: tapered facets angled outward and down
        Vec3 elbowL = shL.add(r.scale(-0.34 * scale)).add(u.scale(-0.46 * scale)).add(f.scale(0.1 * scale));
        Vec3 elbowR = shR.add(r.scale(0.34 * scale)).add(u.scale(-0.46 * scale)).add(f.scale(0.1 * scale));
        Vec3 fistL = elbowL.add(r.scale(-0.12 * scale)).add(u.scale(-0.5 * scale)).add(f.scale(0.18 * scale));
        Vec3 fistR = elbowR.add(r.scale(0.12 * scale)).add(u.scale(-0.5 * scale)).add(f.scale(0.18 * scale));
        limb(vc, m, shL, elbowL, 0.17 * scale, 0.12 * scale, CRYSTAL, a, r, u);
        limb(vc, m, elbowL, fistL, 0.12 * scale, 0.09 * scale, CRYSTAL, a, r, u);
        limb(vc, m, shR, elbowR, 0.17 * scale, 0.12 * scale, CRYSTAL, a, r, u);
        limb(vc, m, elbowR, fistR, 0.12 * scale, 0.09 * scale, CRYSTAL, a, r, u);
        // gold knuckle glints
        edge(vc, m, fistL.add(r.scale(-0.08 * scale)), fistL.add(r.scale(0.08 * scale)), GOLD, ga, 0.06 * scale, u);
        edge(vc, m, fistR.add(r.scale(-0.08 * scale)), fistR.add(r.scale(0.08 * scale)), GOLD, ga, 0.06 * scale, u);

        // ---- head: a crystal volume sitting just on the shoulders (short neck), a bladed crown, a single red eye
        Vec3 neckTop = neck.add(u.scale(0.06 * scale));
        // a short crystal neck connecting torso to head
        facet(vc, m, neck.add(r.scale(-0.08 * scale)).add(dd.scale(0.5)), neck.add(r.scale(0.08 * scale)).add(dd.scale(0.5)),
                neckTop.add(r.scale(0.08 * scale)).add(dd.scale(0.5)), neckTop.add(r.scale(-0.08 * scale)).add(dd.scale(0.5)), CRYSTAL, a, Vec3.ZERO);
        Vec3 headC = neckTop.add(u.scale(0.18 * scale));
        double hw = 0.19 * scale, hh = 0.2 * scale, hd = 0.17 * scale;
        Vec3 hf = f.scale(hd), hu = u.scale(hh), hr = r.scale(hw);
        Vec3 ftl = headC.add(hf).add(hu).add(hr.scale(-1)), ftr = headC.add(hf).add(hu).add(hr);
        Vec3 fbl = headC.add(hf).add(hu.scale(-1)).add(hr.scale(-1)), fbr = headC.add(hf).add(hu.scale(-1)).add(hr);
        Vec3 btl = headC.add(hf.scale(-1)).add(hu).add(hr.scale(-1)), btr = headC.add(hf.scale(-1)).add(hu).add(hr);
        Vec3 bbl = headC.add(hf.scale(-1)).add(hu.scale(-1)).add(hr.scale(-1)), bbr = headC.add(hf.scale(-1)).add(hu.scale(-1)).add(hr);
        facet(vc, m, fbl, fbr, ftr, ftl, CRYSTAL, a + 0.12f, Vec3.ZERO);        // front
        facet(vc, m, bbl, bbr, btr, btl, CRYSTAL_DIM, a, Vec3.ZERO);            // back
        facet(vc, m, bbl, fbl, ftl, btl, CRYSTAL_DIM, a * 0.85f, Vec3.ZERO);    // left
        facet(vc, m, fbr, bbr, btr, ftr, CRYSTAL_DIM, a * 0.85f, Vec3.ZERO);    // right
        // the bladed crown: three angled spikes
        spike(vc, m, headC.add(u.scale(hh)), u.add(f.scale(-0.2)).normalize(), 0.42 * scale, 0.10 * scale, GOLD, ga, r);
        spike(vc, m, headC.add(u.scale(hh)).add(r.scale(-0.14 * scale)), u.add(r.scale(-0.5)).normalize(), 0.3 * scale, 0.08 * scale, CRYSTAL, a + 0.15f, f);
        spike(vc, m, headC.add(u.scale(hh)).add(r.scale(0.14 * scale)), u.add(r.scale(0.5)).normalize(), 0.3 * scale, 0.08 * scale, CRYSTAL, a + 0.15f, f);
        // the red eye
        float eye = (float) (0.9 + 0.1 * Math.sin(time * 9.0));
        Vec3 eyeC = headC.add(f.scale(0.14 * scale)).add(u.scale(0.02 * scale));
        quadAt(vc, m, eyeC, r.scale(0.05 * scale), u.scale(0.03 * scale), RED, eye);
        quadAt(vc, m, eyeC, r.scale(0.09 * scale), u.scale(0.055 * scale), RED, eye * 0.4f);
    }

    /** A flat quad (four corners) nudged along a normal, as additive light. */
    private static void facet(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                              float[] c, float alpha, Vec3 push) {
        Vec3 q0 = p0.add(push), q1 = p1.add(push), q2 = p2.add(push), q3 = p3.add(push);
        v(vc, m, q0, c, alpha);
        v(vc, m, q1, c, alpha);
        v(vc, m, q2, c, alpha);
        v(vc, m, q3, c, alpha);
    }

    /** A tapered limb segment from a to b, as a thin crossed billboard pair. */
    private static void limb(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double w0, double w1,
                             float[] c, float alpha, Vec3 r, Vec3 u) {
        for (Vec3 side : new Vec3[]{r, u}) {
            Vec3 a0 = a.add(side.scale(-w0)), a1 = a.add(side.scale(w0));
            Vec3 b1 = b.add(side.scale(w1)), b0 = b.add(side.scale(-w1));
            v(vc, m, a0, c, alpha);
            v(vc, m, a1, c, alpha);
            v(vc, m, b1, c, alpha);
            v(vc, m, b0, c, alpha);
        }
    }

    /** A line of light of given half-width across axis s. */
    private static void edge(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float[] c, float alpha, double w, Vec3 s) {
        Vec3 a0 = a.add(s.scale(-w)), a1 = a.add(s.scale(w));
        Vec3 b1 = b.add(s.scale(w)), b0 = b.add(s.scale(-w));
        v(vc, m, a0, c, alpha);
        v(vc, m, a1, c, alpha);
        v(vc, m, b1, c, alpha);
        v(vc, m, b0, c, alpha);
    }

    /** A spike (a thin pyramid as two crossed triangles-as-quads) from base along dir. */
    private static void spike(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 dir, double len, double w,
                              float[] c, float alpha, Vec3 side) {
        Vec3 tip = base.add(dir.scale(len));
        Vec3 l = base.add(side.scale(-w)), rr = base.add(side.scale(w));
        v(vc, m, l, c, alpha);
        v(vc, m, rr, c, alpha);
        v(vc, m, tip, c, alpha * 0.6f);
        v(vc, m, tip, c, alpha * 0.6f);
    }

    /** A small quad centred at c spanning axes ex, ey. */
    private static void quadAt(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 ex, Vec3 ey, float[] col, float alpha) {
        v(vc, m, c.add(ex.scale(-1)).add(ey.scale(-1)), col, alpha);
        v(vc, m, c.add(ex).add(ey.scale(-1)), col, alpha);
        v(vc, m, c.add(ex).add(ey), col, alpha);
        v(vc, m, c.add(ex.scale(-1)).add(ey), col, alpha);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float[] c, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(c[0], c[1], c[2], Mth.clamp(a, 0f, 1f)).endVertex();
    }
}
