package com.sbr.client;

import com.jjk.mod.Sorcerer;
import com.sbr.Gyro;
import com.sbr.Johnny;
import com.sbr.SbrNet;
import com.teengojo.client.TeenFx;
import com.teengojo.client.TeenFx.Draw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Johnny's and Gyro's techniques as drawn: spinning nails, Tusk's holes, the golden spiral, the Steel Balls. */
@Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
public final class SbrFx {
    record Eff(SbrNet.Fx m, long start) {
        float age(float partial) {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? 0 : (mc.level.getGameTime() - start) + partial;
        }
    }

    static final List<Eff> EFFECTS = new ArrayList<>();
    /** Tusk's holes by id: [x, y, z, previous x, y, z, last update time]. */
    static final Map<Integer, double[]> HOLES = new HashMap<>();
    static final long[][] CD = new long[6][2];
    static int ammo = -1, ammoMax = 0;
    static long goldenUntil, neglectUntil, neglectStart;

    static final int GOLD = 0xFFC93C, GOLD_CORE = 0xFFF4C4, NAIL = 0xFFE2EC, STEEL = 0xB9C4CF, STEEL_DARK = 0x3B4450, WHITE = 0xFFFFFF,
            GREEN = 0x8DFFB0, VIOLET = 0xC08CFF;

    private SbrFx() {}

    public static void receive(SbrNet.Fx m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        switch (m.kind()) {
            case SbrNet.COOLDOWN -> {
                int s = (int) m.a();
                if (s >= 0 && s < CD.length) {
                    CD[s][0] = now + (long) m.b();
                    CD[s][1] = (long) m.b();
                }
                return;
            }
            case SbrNet.AMMO -> {
                ammo = (int) m.a();
                ammoMax = (int) m.b();
                return;
            }
            case SbrNet.NEGLECT -> {
                neglectStart = now;
                neglectUntil = now + (long) m.a();
                return;
            }
            case SbrNet.HOLE -> {
                double[] h = HOLES.get(m.entity());
                if (h == null) {
                    HOLES.put(m.entity(), new double[]{m.x(), m.y(), m.z(), m.x(), m.y(), m.z(), now});
                } else {
                    HOLES.put(m.entity(), new double[]{m.x(), m.y(), m.z(), h[0], h[1], h[2], now});
                }
                return;
            }
            case SbrNet.HOLE_END -> {
                HOLES.remove(m.entity());
                EFFECTS.add(new Eff(m, now));
                return;
            }
            case SbrNet.GOLDEN -> {
                if (m.b() == 1) {
                    if (mc.player != null && m.entity() == mc.player.getId()) {
                        goldenUntil = now + (long) m.a();
                    }
                    return;
                }
            }
            default -> {
            }
        }
        EFFECTS.add(new Eff(m, now));
        Entity who = m.entity() >= 0 ? mc.level.getEntity(m.entity()) : null;
        if (who instanceof AbstractClientPlayer pl) {
            String anim = switch (m.kind()) {
                case SbrNet.NAIL1, SbrNet.NAIL2, SbrNet.NAIL4 -> "sb_point";
                case SbrNet.CUTTER -> "sb_cutter";
                case SbrNet.BALL, SbrNet.BREAKER -> "sb_throw";
                case SbrNet.ORA -> "sb_ora";
                case SbrNet.HARDEN -> "sb_harden";
                case SbrNet.GOLDEN -> m.b() == 0 ? "sb_spin" : null;
                default -> null;
            };
            if (anim != null) {
                TeenFx.anim(pl, "sbr", anim);
            }
        }
    }

    static float life(SbrNet.Fx m) {
        return switch (m.kind()) {
            case SbrNet.NAIL1, SbrNet.NAIL2, SbrNet.NAIL4 -> m.b() / Math.max(0.1F, m.a()) + 3;
            case SbrNet.CUTTER -> 8;
            case SbrNet.HOLE_END -> 10;
            case SbrNet.WORM_IN -> m.a() + 4;
            case SbrNet.WORM_OUT -> 12;
            case SbrNet.ROTATION, SbrNet.ORA, SbrNet.HARDEN, SbrNet.HEAL, SbrNet.BREAKER_HIT -> m.a();
            case SbrNet.GOLDEN -> m.b() == 2 ? Math.min(m.a(), 200) : m.a() + 2;
            case SbrNet.BALL -> 2 * Math.abs(m.b()) / Math.max(0.1F, m.a()) + 6;
            case SbrNet.BREAKER -> 2 * m.b() / Math.max(0.1F, m.a()) + 6;
            case SbrNet.SPLIT -> 8;
            case SbrNet.HIT -> 8;
            case SbrNet.SPIN_UP -> 6;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------------------------------------ the world

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (EFFECTS.isEmpty() && HOLES.isEmpty())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            EFFECTS.clear();
            HOLES.clear();
            return;
        }
        float partial = e.getPartialTick();
        long now = mc.level.getGameTime();
        EFFECTS.removeIf(f -> f.age(partial) > life(f.m));
        HOLES.values().removeIf(h -> now - (long) h[6] > 12);
        Draw d = new Draw(e.getCamera(), e.getPoseStack().last().pose());
        // dark things: the holes, the Steel Balls' bodies
        d.begin(false);
        for (double[] h : HOLES.values()) {
            float u = Mth.clamp((now - (long) h[6] + partial) / 2F, 0, 1);
            Vec3 p = new Vec3(Mth.lerp(u, h[3], h[0]), Mth.lerp(u, h[4], h[1]), Mth.lerp(u, h[5], h[2]));
            d.disc(p, 0.36, TeenFx.argb(0x050208, 0.95F), TeenFx.argb(0x1A1024, 0.7F));
        }
        for (Eff f : EFFECTS) {
            dark(d, f, partial);
        }
        d.end();
        d.begin(true);
        for (double[] h : HOLES.values()) {
            float u = Mth.clamp((now - (long) h[6] + partial) / 2F, 0, 1);
            Vec3 p = new Vec3(Mth.lerp(u, h[3], h[0]), Mth.lerp(u, h[4], h[1]), Mth.lerp(u, h[5], h[2]));
            spiral(d, p, 0.5, now + partial, GOLD, 0.7F);
            d.ring(p, 0.38, 0.04, TeenFx.argb(GOLD_CORE, 0.6F));
        }
        for (Eff f : EFFECTS) {
            light(d, f, partial);
        }
        d.end();
    }

    static Vec3 along(SbrNet.Fx m, float age, float dist) {
        Vec3 from = new Vec3(m.x(), m.y(), m.z()), dir = new Vec3(m.dx(), m.dy(), m.dz());
        return from.add(dir.scale(Math.min(dist, age * m.a())));
    }

    /** Where a thrown Steel Ball is: out along its line, then back to his hand. */
    static Vec3 ball(SbrNet.Fx m, float age, float partial) {
        float dist = Math.abs(m.b());
        float out = dist / Math.max(0.1F, m.a());
        if (age <= out) {
            return along(m, age, dist);
        }
        Vec3 turn = along(m, out, dist);
        Minecraft mc = Minecraft.getInstance();
        Entity who = mc.level == null ? null : mc.level.getEntity(m.entity());
        Vec3 home = who != null ? TeenFx.hand(who, partial, true) : new Vec3(m.x(), m.y(), m.z());
        float k = Mth.clamp((age - out) / out, 0, 1);
        return turn.lerp(home, k * k * (3 - 2 * k)).add(0, Math.sin(k * Math.PI) * 0.6, 0);
    }

    static void dark(Draw d, Eff f, float partial) {
        SbrNet.Fx m = f.m;
        float age = f.age(partial);
        switch (m.kind()) {
            case SbrNet.BALL -> steelBody(d, ball(m, age, partial), 0.18);
            case SbrNet.BREAKER -> steelBody(d, ball(m, age, partial), 0.32);
            case SbrNet.SPLIT -> {
                float k = Mth.clamp(age / 6F, 0, 1);
                Vec3 p = new Vec3(m.x(), m.y(), m.z()).add(new Vec3(m.dx(), m.dy(), m.dz()).scale(k));
                steelBody(d, p, 0.09 * (1 - Math.max(0, age - 6) / 2F));
            }
            case SbrNet.WORM_IN -> {
                float len = m.a();
                float open = Mth.clamp(age / 3F, 0, 1) * Mth.clamp((len + 2 - age) / 3F, 0, 1);
                d.disc(new Vec3(m.x(), m.y() + 1.0, m.z()), 0.9 * open, TeenFx.argb(0x030106, 0.95F), TeenFx.argb(0x120A1E, 0.5F));
            }
            case SbrNet.WORM_OUT -> {
                float open = Mth.clamp(1 - age / 12F, 0, 1);
                d.disc(new Vec3(m.x(), m.y() + 1.0, m.z()), 0.9 * open, TeenFx.argb(0x030106, 0.95F), TeenFx.argb(0x120A1E, 0.5F));
            }
            default -> {
            }
        }
    }

    static void steelBody(Draw d, Vec3 p, double r) {
        if (r <= 0.01) {
            return;
        }
        d.disc(p, r, TeenFx.argb(STEEL, 1), TeenFx.argb(STEEL_DARK, 1));
    }

    static void light(Draw d, Eff f, float partial) {
        Minecraft mc = Minecraft.getInstance();
        SbrNet.Fx m = f.m;
        float age = f.age(partial);
        Vec3 at = new Vec3(m.x(), m.y(), m.z()), dir = new Vec3(m.dx(), m.dy(), m.dz());
        Entity who = m.entity() >= 0 && mc.level != null ? mc.level.getEntity(m.entity()) : null;
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
        switch (m.kind()) {
            case SbrNet.NAIL1, SbrNet.NAIL2, SbrNet.NAIL4 -> {
                float dist = m.b();
                if (age * m.a() > dist + 1) {
                    return;
                }
                Vec3 p = along(m, age, dist);
                Vec3 tail = at.add(dir.scale(Math.max(0, age * m.a() - 3)));
                int col = m.kind() == SbrNet.NAIL1 ? NAIL : GOLD;
                double w = m.kind() == SbrNet.NAIL1 ? 0.05 : 0.08;
                d.beam(tail, p, w * 1.6, TeenFx.argb(col, 0), TeenFx.argb(col, 0.6F));
                d.beam(p.subtract(dir.scale(0.35)), p, w, TeenFx.argb(WHITE, 0.9F), TeenFx.argb(WHITE, 1));
                d.ring(p, 0.12 + 0.03 * Math.sin(age * 3), 0.025, TeenFx.argb(col, 0.8F));
                if (m.kind() == SbrNet.NAIL4) {
                    TeenFx.orb(d, p, 0.12, GOLD, GOLD_CORE, 1, age);
                    spiral(d, p, 0.45, t, GOLD, 0.8F);
                }
            }
            case SbrNet.CUTTER -> {
                float k = Mth.clamp(age / 6F, 0, 1);
                Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
                Vec3 side = new Vec3(-flat.z, 0, flat.x);
                for (int i = 0; i < 5; i++) {
                    double sweep = -1.1 + 2.2 * k;
                    Vec3 a = at.add(flat.scale(1.0)).add(side.scale(sweep * 2.2)).add(0, -0.2 + i * 0.08, 0);
                    Vec3 b = at.add(flat.scale(3.6)).add(side.scale(sweep * 2.2 + 0.5)).add(0, -0.4 + i * 0.12, 0);
                    d.beam(a, b, 0.035, TeenFx.argb(NAIL, 0.7F * (1 - k)), TeenFx.argb(WHITE, 0.0F));
                }
            }
            case SbrNet.HOLE_END -> {
                float k = Mth.clamp(age / 10F, 0, 1);
                d.ring(at, 0.4 + 0.8 * k, 0.05, TeenFx.argb(GOLD, 0.6F * (1 - k)));
            }
            case SbrNet.WORM_IN, SbrNet.WORM_OUT -> {
                float k = m.kind() == SbrNet.WORM_IN ? Mth.clamp(age / 3F, 0, 1) : Mth.clamp(1 - age / 12F, 0, 1);
                Vec3 c = at.add(0, 1.0, 0);
                spiral(d, c, 1.1 * k, t, GOLD, 0.7F * k);
                d.ring(c, 0.95 * k, 0.05, TeenFx.argb(GOLD_CORE, 0.7F * k));
            }
            case SbrNet.ROTATION -> {
                if (who == null) {
                    return;
                }
                float len = m.a(), k = Mth.clamp(Math.min(age / 4F, (len - age) / 6F), 0, 1);
                Vec3 c = who.getPosition(partial).add(0, who.getBbHeight() * 0.55, 0);
                double r = Math.max(0.7, who.getBbWidth() * 1.1);
                spiral(d, c, r * 1.3, t * 2.2F, GOLD, 0.8F * k);
                spiral(d, c.add(0, who.getBbHeight() * 0.3, 0), r, -t * 2.6F, GOLD_CORE, 0.6F * k);
                d.ring(c, r, 0.05, TeenFx.argb(GOLD, 0.5F * k));
            }
            case SbrNet.ORA -> {
                if (who == null) {
                    return;
                }
                Vec3 look = who.getViewVector(partial);
                Vec3 base = who.getEyePosition(partial).add(look.scale(0.6));
                Random r = new Random((long) (age * 3) * 7919L + f.start);
                for (int i = 0; i < 6; i++) {
                    Vec3 off = new Vec3(r.nextGaussian() * 0.35, r.nextGaussian() * 0.3 - 0.1, r.nextGaussian() * 0.35);
                    Vec3 a = base.add(off), b = a.add(look.scale(1.4 + r.nextDouble() * 0.6));
                    d.beam(a, b, 0.07, TeenFx.argb(GOLD, 0.0F), TeenFx.argb(GOLD_CORE, 0.75F));
                    d.disc(b, 0.12, TeenFx.argb(WHITE, 0.7F), TeenFx.argb(GOLD, 0));
                }
            }
            case SbrNet.BALL -> {
                Vec3 p = ball(m, age, partial);
                boolean gold = m.b() < 0;
                int col = gold ? GOLD : STEEL;
                // the spin, made visible: lines running round the ball
                for (int i = 0; i < 3; i++) {
                    d.ring(p, 0.22 + i * 0.05, 0.015, TeenFx.argb(gold ? GOLD_CORE : WHITE, 0.55F - i * 0.12F));
                }
                d.disc(p.add(0.05, 0.05, 0), 0.07, TeenFx.argb(WHITE, 0.9F), TeenFx.argb(WHITE, 0));
                if (gold) {
                    spiral(d, p, 0.5, t * 3, GOLD, 0.7F);
                }
                d.disc(p, 0.45, TeenFx.argb(col, 0.18F), TeenFx.argb(col, 0));
            }
            case SbrNet.BREAKER -> {
                Vec3 p = ball(m, age, partial);
                float dist = m.b();
                boolean out = age * m.a() <= dist;
                TeenFx.orb(d, p, 0.35, GOLD, GOLD_CORE, out ? 0.6F : 0.2F, age);
                spiral(d, p, 1.1, t * 2, GOLD, out ? 0.8F : 0.2F);
                if (out) {
                    // the Spin's energy, given form: a pale presence riding the ball
                    d.disc(p.add(0, 0.6, 0), 1.0, TeenFx.argb(0xE8F2FF, 0.18F), TeenFx.argb(0xE8F2FF, 0));
                }
            }
            case SbrNet.BREAKER_HIT -> {
                if (who == null) {
                    return;
                }
                float len = m.a(), k = Mth.clamp(Math.min(age / 4F, (len - age) / 10F), 0, 1);
                Vec3 c = who.getPosition(partial).add(0, who.getBbHeight() * 0.5, 0);
                Random r = new Random(f.start);
                for (int i = 0; i < 10; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double y = ((r.nextDouble() + age * 0.01) % 1.0) * who.getBbHeight() - who.getBbHeight() * 0.4;
                    d.disc(c.add(Math.cos(a) * 0.5, y, Math.sin(a) * 0.5), 0.08, TeenFx.argb(0xC8C0B0, 0.5F * k), TeenFx.argb(0xC8C0B0, 0));
                }
            }
            case SbrNet.SPLIT -> {
                float k = Mth.clamp(age / 6F, 0, 1);
                Vec3 p = at.add(dir.scale(k));
                d.beam(at.add(dir.scale(Math.max(0, k - 0.3))), p, 0.03, TeenFx.argb(VIOLET, 0), TeenFx.argb(VIOLET, 0.6F));
                if (age > 5) {
                    d.ring(p, 0.2 + (age - 5) * 0.2, 0.04, TeenFx.argb(VIOLET, 0.6F * Mth.clamp(1 - (age - 5) / 3F, 0, 1)));
                }
            }
            case SbrNet.HARDEN -> {
                if (who == null) {
                    return;
                }
                float len = m.a(), k = Mth.clamp(Math.min(age / 4F, (len - age) / 8F), 0, 1);
                Vec3 c = who.getPosition(partial);
                for (int i = 0; i < 4; i++) {
                    double y = (who.getBbHeight() * (i + 0.5) / 4);
                    d.ring(c.add(0, y, 0), 0.55 + 0.04 * Math.sin(t * 0.8 + i), 0.02, TeenFx.argb(STEEL, 0.45F * k));
                }
            }
            case SbrNet.HEAL -> {
                if (who == null) {
                    return;
                }
                float len = m.a(), k = Mth.clamp(Math.min(age / 4F, (len - age) / 8F), 0, 1);
                Vec3 c = who.getPosition(partial).add(0, age / len * who.getBbHeight(), 0);
                spiral(d, c, 0.7, t * 2, GREEN, 0.7F * k);
                d.ring(c, 0.6, 0.03, TeenFx.argb(GREEN, 0.5F * k));
            }
            case SbrNet.GOLDEN -> {
                if (who == null) {
                    return;
                }
                float len = life(m), k = Mth.clamp(Math.min(age / 6F, (len - age) / 10F), 0, 1);
                if (m.b() == 2) {
                    k *= 0.55F;
                }
                Vec3 c = who.getPosition(partial).add(0, who.getBbHeight() * 0.5, 0);
                spiral(d, c, 1.3, t * 2.5F, GOLD, 0.7F * k);
                spiral(d, c.add(0, 0.5, 0), 0.9, -t * 3F, GOLD_CORE, 0.5F * k);
                if (m.b() == 2 && age < 12) {
                    d.ring(c, 0.5 + age * 0.4, 0.08, TeenFx.argb(GOLD, 0.8F * (1 - age / 12F)));
                }
            }
            case SbrNet.HIT -> {
                float k = Mth.clamp(age / 8F, 0, 1);
                int col = m.a() >= 3 ? STEEL : m.a() >= 1 ? GOLD : NAIL;
                d.ring(at, 0.2 + 0.8 * k, 0.05, TeenFx.argb(col, 0.8F * (1 - k)));
                TeenFx.rays(d, at, 0.5 + 0.8 * k, col, 0.6F * (1 - k), f.start);
            }
            case SbrNet.SPIN_UP -> {
                float k = Mth.clamp(age / 6F, 0, 1);
                d.ring(at, 0.1 + 0.4 * k, 0.03, TeenFx.argb(m.a() == 1 ? GOLD : WHITE, 0.7F * (1 - k)));
            }
            default -> {
            }
        }
    }

    /** The golden spiral: points winding outward from the centre, turning. */
    static void spiral(Draw d, Vec3 c, double r, float t, int col, float k) {
        if (k <= 0.01F || r <= 0.01) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 n = c.subtract(cam).normalize();
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-6) {
            u = new Vec3(1, 0, 0);
        }
        u = u.normalize();
        Vec3 v = u.cross(n).normalize();
        int pts = 26;
        Vec3 prev = null;
        for (int i = 0; i < pts; i++) {
            double s = i / (double) (pts - 1);
            double a = s * Math.PI * 3.2 + t * 0.15;
            double rr = r * Math.pow(s, 1.4);
            Vec3 p = c.add(u.scale(Math.cos(a) * rr)).add(v.scale(Math.sin(a) * rr));
            if (prev != null) {
                d.beam(prev, p, 0.02 + 0.03 * s, TeenFx.argb(col, k * (float) s), TeenFx.argb(col, k * (float) s));
            }
            prev = p;
        }
    }

    // ------------------------------------------------------------------------------------------------ the screen

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        long now = mc.level.getGameTime();
        float partial = e.getPartialTick();
        // hemispatial neglect: the left half of the world, gone
        if (neglectUntil > now) {
            float left = (neglectUntil - now - partial);
            float k = Mth.clamp(Math.min((now - neglectStart + partial) / 4F, left / 10F), 0, 1);
            int a = (int) (250 * k);
            g.fill(0, 0, w / 2 - 20, h, a << 24);
            g.fillGradient(w / 2 - 20, 0, w / 2 + 20, h, a << 24, a << 24);
            // soft edge
            for (int i = 0; i < 20; i++) {
                int x = w / 2 - 20 + i * 2;
                g.fill(x, 0, x + 2, h, (int) (a * (1 - i / 20F)) << 24);
            }
        }
        Sorcerer mine;
        try {
            mine = com.jjk.mod.client.ClientSorcerers.of(mc.player);
        } catch (Throwable t) {
            return;
        }
        if (mine == null || mc.options.hideGui) {
            return;
        }
        boolean johnny = mine.name().equals(Johnny.NAME), gyro = mine.name().equals(Gyro.NAME);
        if (!johnny && !gyro) {
            return;
        }
        String[] moves = johnny ? Johnny.MOVES : Gyro.MOVES;
        int x = w / 2 + 96, y = h - 18 - moves.length * 10;
        // nails or balls, and the Golden Spin / ACT4 timer
        if (ammo >= 0) {
            StringBuilder sb = new StringBuilder(johnny ? "NAILS " : "STEEL BALLS ");
            for (int i = 0; i < ammoMax; i++) {
                sb.append(i < ammo ? '●' : '○');
            }
            g.drawString(mc.font, sb.toString(), x, y - 2, johnny ? 0xFFFFE2EC : 0xFFB9C4CF, true);
        }
        if (goldenUntil > now) {
            String s = (johnny ? "TUSK ACT4 " : "GOLDEN SPIN ") + String.format("%.0fs", Math.ceil((goldenUntil - now) / 20F));
            g.drawString(mc.font, s, x + 90, y - 2, 0xFFFFC93C, true);
        }
        y += 10;
        for (int i = 0; i < moves.length; i++) {
            long end = CD[i][0], len = Math.max(1, CD[i][1]);
            float left = Math.max(0, end - now - partial);
            boolean ready = left <= 0;
            String label = (i + 1) + "  " + moves[i];
            g.fill(x - 2, y - 1, x + 150, y + 9, 0x66000000);
            if (!ready) {
                int bar = (int) (152 * Mth.clamp(left / len, 0, 1));
                g.fill(x - 2, y + 8, x - 2 + bar, y + 9, 0xFFFFC93C);
                label += String.format("  %.0fs", Math.ceil(left / 20F));
            }
            g.drawString(mc.font, label, x, y, ready ? 0xFFFFF4C4 : 0xFF8A8070, false);
            y += 10;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        EFFECTS.clear();
        HOLES.clear();
        for (long[] c : CD) {
            c[0] = 0;
        }
        ammo = -1;
        goldenUntil = 0;
        neglectUntil = 0;
    }

    static Iterator<Eff> unused() {
        return EFFECTS.iterator();
    }
}
