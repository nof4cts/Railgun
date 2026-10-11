package com.firestar.orbital.client;

import com.firestar.orbital.client.cinematic.Director;
import com.firestar.orbital.client.fx.Ribbon;
import com.firestar.orbital.client.fx.ScreenFx;
import com.firestar.orbital.client.space.Space;
import com.firestar.orbital.common.Cannon;
import com.firestar.orbital.config.OConfig;
import com.firestar.orbital.network.S2CBeam;
import com.firestar.orbital.network.S2CStrike;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Every strike this client knows of, on the shared timeline: the caller's film, and for everyone near enough the world
 * effects, the sounds (the boom arrives late, at the speed of sound) and the screen.
 */
public final class Strikes {
    private Strikes() {}

    public static final class Active {
        public final int id;
        public final Cannon cannon;
        public final int caller;
        public final Vec3 target;
        public final long start;
        public final long seed;
        public final float radius;
        public final boolean mine;
        /** The Helios lance's ground point now and a tick ago. */
        public Vec3 beam, beamO;

        Active(S2CStrike m, boolean mine) {
            this.id = m.id();
            this.cannon = Cannon.of(m.cannon());
            this.caller = m.caller();
            this.target = m.target();
            this.start = m.start();
            this.seed = m.seed();
            this.radius = m.radius();
            this.mine = mine;
            this.beam = this.beamO = m.target();
        }

        public Vec3 beam(float partial) {
            return beamO.lerp(beam, partial);
        }
    }

    public static final List<Active> ACTIVE = new ArrayList<>();

    public static void begin(S2CStrike m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (Active a : ACTIVE) {
            if (a.id == m.id()) {
                return;
            }
        }
        boolean mine = m.caller() == mc.player.getId();
        Active a = new Active(m, mine);
        ACTIVE.add(a);
        if (mine && OConfig.CLIENT.cutscenes.get() && time(a, 0) < 10) {
            Director.play(a.cannon == Cannon.HELIOS ? HeliosFx.film(a) : AntipodeFx.film(a));
        }
    }

    public static void beam(S2CBeam m) {
        for (Active a : ACTIVE) {
            if (a.id == m.id()) {
                a.beamO = a.beam;
                a.beam = m.at();
            }
        }
    }

    public static float time(Active a, float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (float) (mc.level.getGameTime() - a.start) + partial;
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            int t = (int) time(a, 0);
            if (t > a.cannon.end + 60) {
                it.remove();
                continue;
            }
            if (a.cannon != Cannon.HELIOS) {
                a.beamO = a.beam;
            }
            if (a.cannon == Cannon.HELIOS) {
                HeliosFx.tick(a, t);
            } else {
                AntipodeFx.tick(a, t);
            }
        }
    }

    /** Effects drawn every frame (sparks, smoke) after the world. */
    public static void frame(float partial) {
        for (Active a : ACTIVE) {
            float t = time(a, partial);
            if (a.cannon == Cannon.HELIOS) {
                HeliosFx.frame(a, t, partial);
            } else {
                AntipodeFx.frame(a, t, partial);
            }
        }
    }

    public static void ribbons(List<Ribbon> out, float partial) {
        if (Space.showing()) {
            return;
        }
        for (Active a : ACTIVE) {
            float t = time(a, partial);
            if (a.cannon == Cannon.HELIOS) {
                HeliosFx.ribbons(out, a, t, partial);
            } else {
                AntipodeFx.ribbons(out, a, t, partial);
            }
        }
    }

    /** Fill the world shader's state for this frame. */
    public static void world(float partial) {
        com.firestar.orbital.client.render.WorldFx.begin();
        if (Space.showing()) {
            return;
        }
        for (Active a : ACTIVE) {
            float t = time(a, partial);
            if (a.cannon == Cannon.HELIOS) {
                HeliosFx.world(com.firestar.orbital.client.render.WorldFx.STATE, a, t, partial);
            } else {
                AntipodeFx.world(com.firestar.orbital.client.render.WorldFx.STATE, a, t, partial);
            }
        }
    }

    /** The picture: the film's own grading for the caller, and the strike felt from wherever else you stand. */
    public static void screen(ScreenFx fx, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (Active a : ACTIVE) {
            float t = time(a, partial);
            double dist = mc.player.position().distanceTo(a.target);
            boolean film = a.mine && Director.active();
            if (a.cannon == Cannon.HELIOS) {
                HeliosFx.screen(fx, a, t, dist, film);
            } else {
                AntipodeFx.screen(fx, a, t, dist, film);
            }
        }
    }

    /** 0..1: how strongly a strike near you colours the sky and air. */
    public static float atmosphere(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !OConfig.CLIENT.atmosphere.get()) {
            return 0;
        }
        float k = 0;
        for (Active a : ACTIVE) {
            if (a.cannon != Cannon.ANTIPODE) {
                continue;
            }
            float t = time(a, partial);
            double dist = mc.player.position().distanceTo(a.target);
            float near = (float) Mth.clamp(1.0 - (dist - a.radius * 2) / (a.radius * 8), 0, 1);
            float on = ScreenFx.window(t, 10, 120, a.cannon.hold, a.cannon.end + 30);
            k = Math.max(k, near * on);
        }
        return k;
    }

    public static float[] fog(float r, float g, float b, float partial) {
        float k = atmosphere(partial);
        if (k <= 0.01F) {
            return null;
        }
        return new float[]{Mth.lerp(k * 0.75F, r, 0.32F), Mth.lerp(k * 0.75F, g, 0.04F), Mth.lerp(k * 0.75F, b, 0.12F)};
    }

    /** A sound for this strike: at the camera for its caller (the film moves the camera far away), at the spot for others. */
    static void sound(Active a, Vec3 at, SoundEvent s, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (a.mine) {
            Vec3 p = mc.player.getEyePosition();
            mc.level.playLocalSound(p.x, p.y, p.z, s, SoundSource.PLAYERS, volume, pitch, false);
        } else {
            mc.level.playLocalSound(at.x, at.y, at.z, s, SoundSource.AMBIENT, volume * 6.0F, pitch, false);
        }
    }

    /** Ticks for the boom to reach you from {@code at}: sound covers about 17 blocks a tick. */
    static int soundDelay(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : (int) (mc.player.position().distanceTo(at) / 17.0);
    }
}
