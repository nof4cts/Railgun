package com.firestar.orbital.client;

import com.firestar.orbital.client.cinematic.CameraPose;
import com.firestar.orbital.client.space.Space;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * The geometry of a strike seen from orbit: where on the globe the mark is (always on the day side), where the sun is,
 * and the directions along the ground there. Everything in Earth radii.
 */
final class Orbit {
    /** The mark, as a unit vector from the Earth's centre. */
    final Vec3 mark;
    /** Two directions along the ground at the mark. */
    final Vec3 east, north;
    final Vec3 sun;

    Orbit(long seed) {
        Random r = new Random(seed);
        double lat = Math.toRadians(15 + r.nextDouble() * 35) * (r.nextBoolean() ? 1 : -1);
        double lon = r.nextDouble() * Math.PI * 2;
        mark = new Vec3(Math.cos(lat) * Math.cos(lon), Math.sin(lat), Math.cos(lat) * Math.sin(lon));
        Vec3 e = new Vec3(0, 1, 0).cross(mark);
        east = e.normalize();
        north = mark.cross(east).normalize();
        // morning light, low enough to throw long shadows across the clouds
        sun = mark.scale(0.55).add(east.scale(0.8)).add(north.scale(0.25)).normalize();
    }

    /** A point at altitude {@code alt} (Earth radii) over the mark, offset along the ground. */
    Vec3 over(double alt, double e, double n) {
        return mark.scale(1 + alt).add(east.scale(e)).add(north.scale(n));
    }

    Space.View view() {
        Space.View v = new Space.View();
        v.sun = sun;
        v.mark = mark;
        return v;
    }

    /** What the Director should do while a space shot is up: nothing visible, so hold the player's own eye. */
    static CameraPose hold() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        Vec3 look = mc.player != null ? mc.player.getViewVector(1.0F) : new Vec3(0, 0, 1);
        return new CameraPose(eye, eye.add(look.scale(10)), 70, 0);
    }
}
