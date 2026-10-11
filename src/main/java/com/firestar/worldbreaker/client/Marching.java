package com.firestar.worldbreaker.client;

import static com.firestar.worldbreaker.client.fx.ScreenFx.hit;

import com.firestar.worldbreaker.client.anim.PlayerAnims;
import com.firestar.worldbreaker.client.cinematic.Director;
import com.firestar.worldbreaker.client.fx.Impact;
import com.firestar.worldbreaker.client.fx.Light;
import com.firestar.worldbreaker.client.fx.Ribbon;
import com.firestar.worldbreaker.client.fx.ScreenFx;
import com.firestar.worldbreaker.client.fx.Shapes;
import com.firestar.worldbreaker.client.fx.Smoke;
import com.firestar.worldbreaker.client.giant.GiantRenderer;
import com.firestar.worldbreaker.common.March;
import com.firestar.worldbreaker.config.WConfig;
import com.firestar.worldbreaker.network.S2CMarch;
import com.firestar.worldbreaker.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The client side of every march: it replays the giants from the shared arithmetic, and dresses each step in what a
 * fifty-block giant walking through a world would do to it: a boom felt through the floor, a crater flash and molten
 * cracks, dust thrown out in a ring, steam pouring off hot stone, embers, the sky gone the colour of rust, the camera
 * shaking with every footfall near it.
 */
public final class Marching {
    public static final List<Active> ACTIVE = new ArrayList<>();
    static final int EMBER = 0xFF7A1A, MOLTEN = 0xFFB040, HOT = 0xFFE2A0, RUST = 0x2A0C02;
    static final int STEAM = 0xC9C2BA, ASH = 0x8A7A6A, DUST = 0x9A8064, DARK = 0x4A3E34;

    private Marching() {}

    public static final class Active {
        public final int id;
        public final int caller;
        public final Vec3 origin, dir;
        public final long start;
        public final long seed;
        public final March.Giant[] giants;
        public final int life;
        final double[] ground, groundO;

        Active(S2CMarch m) {
            id = m.id();
            caller = m.caller();
            origin = m.origin();
            dir = m.dir().normalize();
            start = m.start();
            seed = m.seed();
            giants = March.formation(m.count(), m.seed());
            life = m.life();
            ground = new double[giants.length];
            groundO = new double[giants.length];
            java.util.Arrays.fill(ground, origin.y);
            java.util.Arrays.fill(groundO, origin.y);
        }
    }

    /** A footfall, remembered for a few seconds for its cracks and the shake it sends through the ground. */
    record Shock(float time, Vec3 at, float power, long seed) {}

    static final List<Shock> SHOCKS = new ArrayList<>();
    private static final Random R = new Random();

    public static void accept(S2CMarch m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ACTIVE.removeIf(a -> a.id == m.id());
        Active a = new Active(m);
        ACTIVE.add(a);
        boolean mine = mc.player != null && m.caller() == mc.player.getId();
        if (mine && time(a, 0) + March.LEAD < 10) {
            if (WConfig.CLIENT.cutscenes.get()) {
                Director.play(Films.march(a));
            }
            PlayerAnims.play(mc.player, "call");
        }
        if (time(a, 0) + March.LEAD < 20) {
            ui(ModSounds.HORN.get(), 1.0F, 0.9F);
        }
    }

    public static double time(Active a, float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? -1 : mc.level.getGameTime() - a.start + partial;
    }

    /** A giant's body centre (on the ground) at this frame. */
    public static Vec3 centre(Active a, March.Giant g, float partial) {
        Vec3 p = March.position(a.origin, a.dir, g, time(a, partial));
        return new Vec3(p.x, Mth.lerp(partial, a.groundO[g.index()], a.ground[g.index()]), p.z);
    }

    public static boolean any() {
        return !ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
        SHOCKS.clear();
    }

    // ------------------------------------------------------------------------------------------------ per tick

    private record Heard(double dist, Vec3 at, float power) {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float now = mc.level.getGameTime();
        SHOCKS.removeIf(s -> now - s.time > 80);
        List<Heard> heard = new ArrayList<>();
        double nearest = 1e9;
        ACTIVE.removeIf(a -> time(a, 0) > a.life + 40);
        for (Active a : ACTIVE) {
            int t = (int) time(a, 0);
            if (t < 0) {
                continue;
            }
            double presence = March.presence(t, a.life);
            for (March.Giant g : a.giants) {
                int i = g.index();
                double h = March.HEIGHT * g.scale();
                Vec3 flat = March.position(a.origin, a.dir, g, t);
                a.groundO[i] = a.ground[i];
                var bp = net.minecraft.core.BlockPos.containing(flat);
                if (mc.level.isLoaded(bp)) {
                    double surface = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bp.getX(), bp.getZ());
                    double target = Math.max(a.origin.y - 24, Math.min(a.origin.y + 30, surface));
                    a.ground[i] += (target - a.ground[i]) * 0.06;
                }
                Vec3 c = new Vec3(flat.x, a.ground[i], flat.z);
                double d = c.distanceTo(cam);
                nearest = Math.min(nearest, d);
                Vec3 heading = March.heading(a.dir, g);
                if (d < 400) {
                    emit(a, g, t, c, heading, h, presence, d);
                }
                if (March.footfall(t, g) && presence > 0.8) {
                    Vec3 foot = March.foot(c, heading, g, March.leftFoot(t, g));
                    footfall(a, g, foot, h, d, now);
                    heard.add(new Heard(foot.distanceTo(cam), foot, (float) g.scale()));
                }
                if (t == 2) {
                    heard.add(new Heard(d, c, 0.6F));
                }
            }
            if (t == 1) {
                ui(ModSounds.RISE.get(), 1.0F, 0.8F);
            }
            if (t % 40 == 0 && t < a.life - 20) {
                double k = Mth.clamp(1 - nearestTo(a, cam) / 420.0, 0.08, 1);
                ui(ModSounds.RUMBLE.get(), (float) k, 0.85F + R.nextFloat() * 0.1F);
            }
        }
        // the nearest few footfalls are heard, the rest are felt
        heard.sort(Comparator.comparingDouble(Heard::dist));
        for (int i = 0; i < Math.min(3, heard.size()); i++) {
            Heard hd = heard.get(i);
            float vol = (float) Mth.clamp(Math.pow(1 - hd.dist / 520.0, 1.6), 0.05, 1);
            ui(ModSounds.FOOTFALL.get(), vol, 0.85F + R.nextFloat() * 0.15F + (i == 0 ? 0 : 0.05F));
            if (hd.dist < 60) {
                ui(ModSounds.CRUMBLE.get(), vol, 0.9F + R.nextFloat() * 0.2F);
            }
        }
        if (nearest < 90 && mc.level.getGameTime() % 47 == 0) {
            ui(ModSounds.STEAM.get(), (float) Mth.clamp(1 - nearest / 90, 0.1, 0.7), 0.9F);
        }
    }

    private static double nearestTo(Active a, Vec3 p) {
        double best = 1e9;
        for (March.Giant g : a.giants) {
            best = Math.min(best, centre(a, g, 0).distanceTo(p));
        }
        return best;
    }

    static void ui(SoundEvent s, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** What a walking giant gives off every tick: steam off its shoulders and back, embers from its seams, dust at its shins. */
    private static void emit(Active a, March.Giant g, int t, Vec3 c, Vec3 heading, double h, double presence, double dist) {
        Vec3 side = March.side(heading);
        float detail = dist < 120 ? 0.7F : dist < 240 ? 0.3F : 0.1F;
        double sink = (1 - presence) * h * 1.45;
        if (presence > 0.05) {
            // steam: hot stone in cold air
            for (int i = 0; i < 2; i++) {
                if (R.nextFloat() > detail) {
                    continue;
                }
                double sx = (R.nextBoolean() ? 1 : -1) * h * (0.12 + R.nextDouble() * 0.12);
                Vec3 from = c.add(side.scale(sx)).add(heading.scale(-h * 0.04)).add(0, h * (0.7 + R.nextDouble() * 0.2) - sink, 0);
                Vec3 vel = new Vec3((R.nextDouble() - 0.5) * 1.5, 2.5 + R.nextDouble() * 2.5, (R.nextDouble() - 0.5) * 1.5).add(heading.scale(-1.5));
                Smoke.puff(from, vel, (float) (h * 0.05), (float) (h * 0.17), 3.5F + R.nextFloat() * 2, STEAM, 0.26F, 0.6F, 0.6F);
            }
            if (R.nextFloat() < 0.25F * detail) {
                Vec3 mouth = GiantRenderer.eyes(c, heading, h).subtract(0, h * 0.06 + sink, 0);
                Smoke.puff(mouth, heading.scale(3).add(0, 1.2, 0), (float) (h * 0.03), (float) (h * 0.12), 2.5F, STEAM, 0.35F, 0.8F, 0.8F);
            }
            // embers coming off the seams
            if (R.nextFloat() < 0.8F * detail) {
                Vec3 at = c.add(side.scale((R.nextDouble() - 0.5) * h * 0.35)).add(0, R.nextDouble() * h * 0.9 - sink, 0);
                Light.mote(at, new Vec3((R.nextDouble() - 0.5) * 2, 2 + R.nextDouble() * 3, (R.nextDouble() - 0.5) * 2), 1.6F,
                        R.nextBoolean() ? EMBER : MOLTEN, 0.12F);
            }
        }
        if (presence < 1 && t < March.RISE + 4 && R.nextFloat() < detail) {
            // clawing up out of the earth: the ground boils up round it
            for (int i = 0; i < 3; i++) {
                double ang = R.nextDouble() * Math.PI * 2;
                Vec3 at = c.add(Math.cos(ang) * h * 0.25, 0.5, Math.sin(ang) * h * 0.25);
                Smoke.puff(at, new Vec3(Math.cos(ang) * 4, 3 + R.nextDouble() * 5, Math.sin(ang) * 4), (float) (h * 0.08), (float) (h * 0.32),
                        5 + R.nextFloat() * 2, R.nextBoolean() ? DUST : DARK, 0.6F, 0.8F, -0.4F);
            }
        }
        if (presence > 0.6 && R.nextFloat() < 0.6F * detail) {
            // what it walks through comes apart at its shins
            Vec3 at = c.add(heading.scale(h * 0.1)).add(side.scale((R.nextDouble() - 0.5) * h * 0.3)).add(0, R.nextDouble() * h * 0.2, 0);
            Smoke.puff(at, heading.scale(3).add(0, 1.5, 0), (float) (h * 0.04), (float) (h * 0.14), 3F, DUST, 0.35F, 1.0F, -0.2F);
        }
    }

    /** A foot comes down. */
    private static void footfall(Active a, March.Giant g, Vec3 foot, double h, double dist, float now) {
        SHOCKS.add(new Shock(now, foot, (float) g.scale(), a.seed * 31 + g.index() * 7L + (long) now));
        if (dist > 700) {
            return;
        }
        float detail = dist < 120 ? 0.75F : dist < 260 ? 0.35F : 0.15F;
        // dust thrown out in a ring along the ground, then a slower column rising
        int n = (int) (16 * detail);
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n + R.nextDouble() * 0.3;
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            double sp = 10 + R.nextDouble() * 10;
            Smoke.puff(foot.add(dir.scale(h * 0.06)).add(0, 0.8, 0), dir.scale(sp).add(0, 0.8 + R.nextDouble() * 1.5, 0), (float) (h * 0.05),
                    (float) (h * (0.18 + R.nextDouble() * 0.1)), 4.5F + R.nextFloat() * 2.5F, R.nextInt(3) == 0 ? DARK : DUST, 0.55F, 1.4F, -0.1F);
        }
        for (int i = 0; i < (int) (4 * detail) + 1; i++) {
            Smoke.puff(foot.add((R.nextDouble() - 0.5) * h * 0.1, 1 + R.nextDouble() * 3, (R.nextDouble() - 0.5) * h * 0.1),
                    new Vec3((R.nextDouble() - 0.5) * 2, 4 + R.nextDouble() * 6, (R.nextDouble() - 0.5) * 2), (float) (h * 0.08), (float) (h * 0.3),
                    6, DUST, 0.45F, 0.9F, 0.3F);
        }
        // the molten foot: a flash, a shock ring racing out, sparks and rocks of light
        Light.flare(foot.add(0, 1.5, 0), (float) (h * 0.22), EMBER, 0.6F);
        Light.flare(foot.add(0, 1.0, 0), (float) (h * 0.08), HOT, 0.3F);
        if (detail > 0.5F) {
            Light.ring(foot.add(0, 0.3, 0), new Vec3(0, 1, 0), (float) (h * 0.05), (float) (h * 0.4), (float) (h * 0.015), MOLTEN, 0.6F);
        }
        Light.spray(foot.add(0, 1, 0), new Vec3(0, 1, 0), 1.2F, (int) (24 * detail), 22.0F, EMBER, 1.3F, 0.16F);
    }

    // ------------------------------------------------------------------------------------------------ per frame

    /** Light that lives on: the eyes burning in every head. */
    public static void frame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        for (Active a : ACTIVE) {
            double t = time(a, partial);
            if (t < 0) {
                continue;
            }
            double presence = March.presence(t, a.life);
            for (March.Giant g : a.giants) {
                double h = March.HEIGHT * g.scale();
                Vec3 heading = March.heading(a.dir, g);
                Vec3 e = GiantRenderer.eyes(centre(a, g, partial), heading, h).subtract(0, (1 - presence) * h * 1.45, 0);
                Vec3 side = March.side(heading);
                float flick = 0.85F + 0.15F * (float) Math.sin(t * 0.7 + g.index());
                boolean near = e.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) < 250 * 250;
                for (int s = -1; s <= 1; s += 2) {
                    Light.flare(e.add(side.scale(s * h * 0.055)), (float) (h * 0.05) * flick, EMBER, 0.05F);
                    if (near) {
                        Light.flare(e.add(side.scale(s * h * 0.055)), (float) (h * 0.018), HOT, 0.05F);
                    }
                }
            }
        }
    }

    /** Strokes on the ground: molten cracks racing out from each footfall and cooling. */
    public static void ribbons(List<Ribbon> out, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float now = mc.level.getGameTime() + partial;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Shock s : SHOCKS) {
            float age = now - s.time;
            if (age < 0 || age > 50 || s.at.distanceToSqr(cam) > 160 * 160) {
                continue;
            }
            Random r = new Random(s.seed);
            float grow = Mth.clamp(age / 5.0F, 0, 1);
            float cool = 1 - Mth.clamp((age - 8) / 60.0F, 0, 1);
            double reach = 12 * s.power;
            int cracks = s.at.distanceToSqr(cam) < 60 * 60 ? 8 : 4;
            for (int i = 0; i < cracks; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double len = reach * (0.5 + r.nextDouble() * 0.8) * grow;
                Vec3 a = s.at.add(Math.cos(ang) * 2, 0.15, Math.sin(ang) * 2);
                Vec3 b = s.at.add(Math.cos(ang) * (2 + len), 0.15, Math.sin(ang) * (2 + len));
                int col = cool > 0.6F ? HOT : cool > 0.3F ? MOLTEN : EMBER;
                Shapes.bolt(out, a, b, s.seed + i, 0.6, 0.35F * cool + 0.1F, 0.9F * cool, col, Ribbon.Style.GLOW);
                Shapes.bolt(out, a, b, s.seed + i, 0.6, 0.6F, 0.35F * cool, RUST, Ribbon.Style.TINT);
            }
            float sh = 1 - Mth.clamp(age / 14.0F, 0, 1);
            if (sh > 0) {
                Shapes.ring(out, s.at.add(0, 0.25, 0), new Vec3(0, 1, 0), 3 + age * 2.2 * s.power, 1.2F * sh, 0.55F * sh, DUST, Ribbon.Style.TINT);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the picture

    /** How strongly the march colours the world for the viewer: by distance to the nearest giant and the march's age. */
    public static float atmosphere(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty() || !WConfig.CLIENT.atmosphere.get()) {
            return 0;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float best = 0;
        for (Active a : ACTIVE) {
            double t = time(a, partial);
            float fade = (float) Math.min(Mth.clamp(t / 60.0, 0, 1), Mth.clamp((a.life + 30 - t) / 80.0, 0, 1));
            float near = (float) Mth.clamp(1 - (nearestTo(a, cam) - 180) / 320.0, 0, 1);
            best = Math.max(best, fade * near);
        }
        return best;
    }

    public static void screen(ScreenFx fx, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float k = atmosphere(partial);
        if (k > 0.001F) {
            fx.vignette(0x1C0600, 0.42F * k);
            fx.desaturate(0.28F * k);
            fx.contrast(0.22F * k);
            fx.bloom(0.25F * k);
            fx.grain(0.18F * k);
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float now = mc.level.getGameTime() + partial;
        float shake = 0;
        for (Shock s : SHOCKS) {
            double d = s.at.distanceTo(cam);
            float p = (float) (s.power * 1.4 / (1 + Math.pow(d / 45.0, 1.5)));
            shake = Math.max(shake, p * hit(now, s.time, 9));
            if (d < 28 && now - s.time < 3 && WConfig.CLIENT.impactFrames.get() && !Director.active()) {
                fx.impact(new int[][]{{Impact.INK, 1}}, now - s.time, s.at, MOLTEN);
                fx.punch(0.5F * hit(now, s.time, 3));
            }
        }
        // the ground never stops trembling while they walk
        shake = Math.max(shake, 0.08F * k);
        fx.shake(shake);
        Films.screen(fx, partial);
    }

    /** The march colours the fog: rust and ash, drawn in closer as the dust thickens. */
    public static float[] fog(float r, float g, float b, float partial) {
        float k = atmosphere(partial);
        if (k <= 0) {
            return null;
        }
        return new float[]{Mth.lerp(k * 0.75F, r, 0.42F), Mth.lerp(k * 0.75F, g, 0.2F), Mth.lerp(k * 0.75F, b, 0.1F)};
    }

}
