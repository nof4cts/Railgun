package com.firestar.rose.client.gfx;

import com.firestar.rose.RoseConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Loose light and smoke: sparks thrown off a cut, embers drifting up out of the aura, soot rolling off black energy.
 * Simulated per rendered frame (not per tick) so nothing ever steps or stutters.
 */
public final class Embers {
    private static final List<P> LIVE = new ArrayList<>();
    private static final Random R = new Random();
    private static long last = System.nanoTime();

    private Embers() {}

    static final class P {
        Vec3 pos, vel;
        float life, age, size, heat, drag, gravity, seed;
        int kind; // 1 spark, 2 mote, 3 smoke, 0 flare
    }

    private static P add(int kind, Vec3 pos, Vec3 vel, float life, float size, float heat, float drag, float gravity) {
        if (LIVE.size() >= RoseConfig.CLIENT.embers.get()) {
            return null;
        }
        P p = new P();
        p.kind = kind;
        p.pos = pos;
        p.vel = vel;
        p.life = life;
        p.size = size;
        p.heat = heat;
        p.drag = drag;
        p.gravity = gravity;
        p.seed = R.nextFloat();
        LIVE.add(p);
        return p;
    }

    public static void spark(Vec3 pos, Vec3 vel, float life, float width, float heat) {
        add(1, pos, vel, life, width, heat, 3.0F, 6.0F);
    }

    public static void mote(Vec3 pos, Vec3 vel, float life, float size, float heat) {
        add(2, pos, vel, life, size, heat, 0.8F, -1.2F);
    }

    public static void smoke(Vec3 pos, Vec3 vel, float life, float size) {
        add(3, pos, vel, life, size, 0, 1.6F, -0.6F);
    }

    public static void flash(Vec3 pos, float size, float life, float heat) {
        add(0, pos, Vec3.ZERO, life, size, heat, 0, 0);
    }

    /** Sparks flung out in every direction. */
    public static void burst(Vec3 at, int n, float speed, float life, float width, float heat) {
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).normalize();
            spark(at, d.scale(speed * (0.3F + R.nextFloat())), life * (0.5F + R.nextFloat() * 0.7F), width * (0.6F + R.nextFloat() * 0.7F), heat);
        }
    }

    /** Sparks thrown one way, in a cone round {@code dir}. */
    public static void spray(Vec3 at, Vec3 dir, float cone, int n, float speed, float life, float width, float heat) {
        Vec3 d0 = dir.normalize();
        for (int i = 0; i < n; i++) {
            Vec3 d = d0.add(new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).scale(cone)).normalize();
            spark(at, d.scale(speed * (0.4F + R.nextFloat() * 0.8F)), life * (0.6F + R.nextFloat() * 0.6F), width, heat);
        }
    }

    /** Soot rolling outward from a point. */
    public static void soot(Vec3 at, int n, float speed, float life, float size) {
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(R.nextGaussian(), R.nextGaussian() * 0.5 + 0.3, R.nextGaussian()).normalize();
            smoke(at.add(d.scale(0.2)), d.scale(speed * (0.3F + R.nextFloat())), life * (0.6F + R.nextFloat() * 0.6F), size * (0.6F + R.nextFloat() * 0.8F));
        }
    }

    public static void clear() {
        LIVE.clear();
    }

    static void draw(Frame f, float partial) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - last) / 1.0E9F);
        last = now;
        if (Minecraft.getInstance().isPaused()) {
            dt = 0;
        }
        Iterator<P> it = LIVE.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.age += dt;
            if (p.age >= p.life) {
                it.remove();
                continue;
            }
            p.pos = p.pos.add(p.vel.scale(dt));
            p.vel = p.vel.scale(Math.exp(-p.drag * dt)).add(0, -p.gravity * dt, 0);
            float k = p.age / p.life;
            float fade = (1 - k);
            switch (p.kind) {
                case 1 -> {
                    double speed = p.vel.length();
                    f.spark(p.pos, p.vel, 0.08 + speed * 0.05, p.size * (0.4 + 0.6 * fade), fade * fade, p.heat * (0.6F + 0.4F * fade));
                }
                case 2 -> f.mote(p.pos, p.size * (0.6 + 0.4 * fade), (float) Math.sin(Math.PI * Math.min(1, k * 3)) * fade, p.heat);
                case 3 -> f.smoke(p.pos, p.size * (0.7 + 0.8 * k), fade * fade * 0.9F, p.seed);
                default -> f.flare(p.pos, p.size * (1 + 0.5 * k), fade * fade, p.heat);
            }
        }
    }
}
