package com.aegis.command.client.render;

import com.aegis.command.client.render.mesh.WMesh;
import com.aegis.command.mission.AircraftType;

import java.util.EnumMap;
import java.util.Map;

import static com.aegis.command.client.render.mesh.WMesh.*;

/**
 * Original, realistic-proportioned aircraft and ordnance, built with {@link WMesh}.
 * Units are 1/16 block (scaled again by {@link AircraftType#scale}); nose toward -Z, y up.
 * Materials for aircraft: PAINT hull, ACCENT darker camo panels, DARK intakes / undersides,
 * BLACK canopy glass, CHROME nozzles, TRIM unit stripe, GLOW engines, HOT burner cores,
 * LENS red/green nav lights (red on the left, green on the right via the MAG/SPIN groups).
 */
public final class AircraftModels {
    private static final Map<AircraftType, WMesh> CACHE = new EnumMap<>(AircraftType.class);
    private static WMesh bomb;

    /** Group used for the starboard (green) nav light so the renderer can colour it separately. */
    public static final int NAV_GREEN = SPIN;

    private AircraftModels() {}

    public static WMesh get(AircraftType t) {
        return CACHE.computeIfAbsent(t, k -> switch (k) {
            case KESTREL -> kestrel();
            case GOLIATH -> goliath();
            case WRAITH -> wraith();
        });
    }

    public static WMesh bomb() {
        if (bomb == null) bomb = buildBomb();
        return bomb;
    }

    // ------------------------------------------------------------------ KESTREL A-9: single-seat multirole

    private static WMesh kestrel() {
        WMesh m = new WMesh();
        // fuselage loft: radome, forebody, centre, aft, tail boom
        m.smooth();
        m.taperZ(0, 0.4f, -38f, 0.2f, 0.2f, -31f, 2.6f, 2.4f, -0.3f, PAINT);
        m.taperZ(0, 0.1f, -31f, 2.6f, 2.4f, -24f, 4.4f, 4.0f, 0.2f, PAINT);
        m.taperZ(0, 0.3f, -24f, 4.4f, 4.0f, -12f, 5.4f, 4.6f, 0f, PAINT);
        m.taperZ(0, 0.3f, -12f, 5.4f, 4.6f, 16f, 6.6f, 4.4f, 0f, PAINT);
        m.taperZ(0, 0.3f, 16f, 6.6f, 4.4f, 30f, 5.6f, 3.6f, 0.1f, PAINT);
        m.panel();
        // chines along the forebody
        m.wing(2.0f, -30f, -12f, 3.4f, -20f, -10f, -0.2f, 0.5f, 0.2f, 0, PAINT);
        m.wing(-2.0f, -30f, -12f, -3.4f, -20f, -10f, -0.2f, 0.5f, 0.2f, 0, PAINT);
        // dorsal spine and canopy
        m.taperZ(0, 2.7f, -12f, 2.4f, 0.8f, 22f, 1.6f, 0.6f, -0.6f, PAINT);
        m.taperZ(0, 2.7f, -26f, 0.6f, 0.4f, -21f, 2.6f, 1.8f, 0.4f, BLACK);
        m.taperZ(0, 3.1f, -21f, 2.6f, 1.8f, -14f, 2.2f, 1.6f, -0.2f, BLACK);
        m.taperZ(0, 2.7f, -14f, 2.2f, 1.2f, -11f, 2.0f, 0.8f, 0f, PAINT);           // canopy aft fairing
        m.box(-1.25f, 2.6f, -20.2f, 1.25f, 4.1f, -19.8f, DARK);                      // canopy frame bow
        // intakes (DSI bumps + caret inlets)
        for (int s = -1; s <= 1; s += 2) {
            m.taperZ(s * 3.6f, -0.2f, -19f, 0.8f, 2.4f, -12f, 2.4f, 3.2f, 0, PAINT);
            m.box(s * 3.6f - 1.2f, -1.8f, -18.9f, s * 3.6f + 1.2f, 1.4f, -18.6f, DARK);
            m.taperZ(s * 3.6f, -0.1f, -12f, 2.4f, 3.2f, 6f, 1.6f, 2.6f, 0, PAINT);
        }
        // wings: swept trapezoid with leading-edge extension, slight anhedral
        m.wing(3.2f, -10f, 14f, 26f, 9f, 16f, -0.4f, 1.2f, 0.35f, -0.9f, PAINT);
        m.wing(-3.2f, -10f, 14f, -26f, 9f, 16f, -0.4f, 1.2f, 0.35f, -0.9f, PAINT);
        m.wing(3.0f, -14f, -9f, 5.5f, -9f, -6f, -0.2f, 0.6f, 0.3f, 0, PAINT);          // LERX
        m.wing(-3.0f, -14f, -9f, -5.5f, -9f, -6f, -0.2f, 0.6f, 0.3f, 0, PAINT);
        // control surfaces (darker panels on the trailing edge)
        m.wing(6f, 12.6f, 14.2f, 22f, 14.2f, 15.6f, -0.62f, 0.5f, 0.3f, -0.6f, ACCENT);
        m.wing(-6f, 12.6f, 14.2f, -22f, 14.2f, 15.6f, -0.62f, 0.5f, 0.3f, -0.6f, ACCENT);
        // horizontal stabilators
        m.wing(3f, 21f, 30f, 13.5f, 27.5f, 31.5f, -0.2f, 0.7f, 0.3f, 0, PAINT);
        m.wing(-3f, 21f, 30f, -13.5f, 27.5f, 31.5f, -0.2f, 0.7f, 0.3f, 0, PAINT);
        // twin canted fins
        m.fin(2.6f, 2.0f, 17f, 28.5f, 11.5f, 25.5f, 30f, 0.6f, 2.6f, PAINT);
        m.fin(-2.6f, 2.0f, 17f, 28.5f, 11.5f, 25.5f, 30f, 0.6f, -2.6f, PAINT);
        m.fin(4.0f, 9.5f, 24.6f, 28.2f, 11.2f, 25.6f, 29.8f, 0.7f, 0.5f, ACCENT);     // fin tip caps
        m.fin(-4.0f, 9.5f, 24.6f, 28.2f, 11.2f, 25.6f, 29.8f, 0.7f, -0.5f, ACCENT);
        // engines: twin nozzles with petals
        for (int s = -1; s <= 1; s += 2) {
            float x = s * 1.8f;
            m.cylZ(x, 0.2f, 29f, 33.5f, 1.65f, 1.35f, 14, CHROME, false);
            m.ringZ(x, 0.2f, 33.3f, 33.7f, 1.15f, 1.45f, 14, DARK);
            m.cylZ(x, 0.2f, 32.6f, 32.61f, 1.2f, 1.2f, 14, GLOW, true);
            m.cylZ(x, 0.2f, 32.2f, 32.21f, 0.7f, 0.7f, 10, HOT, true);
        }
        // underbelly + weapons bay seam
        m.taperZ(0, -1.9f, -10f, 3.4f, 0.4f, 14f, 4.2f, 0.4f, 0, DARK);
        // stores: two guided bombs on wing pylons
        for (int s = -1; s <= 1; s += 2) {
            float x = s * 11f;
            m.box(x - 0.3f, -1.9f, 1f, x + 0.3f, -0.5f, 7f, DARK);
            m.cylZ(x, -2.8f, -1f, 8f, 0.9f, 0.9f, 10, ACCENT, true);
            m.cylZ(x, -2.8f, -3f, -1f, 0.1f, 0.9f, 10, ACCENT, false);
            m.box(x - 1.6f, -2.9f, 6.5f, x + 1.6f, -2.7f, 8.2f, DARK);
            m.box(x - 0.1f, -4.4f, 6.5f, x + 0.1f, -1.2f, 8.2f, DARK);
        }
        // unit stripe on the fins and a nose stripe
        m.fin(2.6f + 1.4f, 6.5f, 20.6f, 29.0f, 7.5f, 21.2f, 29.4f, 0.65f, 0.25f, TRIM);
        m.fin(-2.6f - 1.4f, 6.5f, 20.6f, 29.0f, 7.5f, 21.2f, 29.4f, 0.65f, -0.25f, TRIM);
        // navigation lights: red port (left, -x), green starboard (+x), white tail strobe
        m.box(-26.3f, -0.9f, 12.6f, -25.6f, -0.2f, 13.6f, LENS);
        m.group(NAV_GREEN).box(25.6f, -0.9f, 12.6f, 26.3f, -0.2f, 13.6f, LENS).group(MAIN);
        m.box(-0.3f, 0.6f, 30.2f, 0.3f, 1.0f, 30.8f, HOT);
        return m;
    }

    // ------------------------------------------------------------------ GOLIATH B-4: heavy strategic bomber

    private static WMesh goliath() {
        WMesh m = new WMesh();
        m.smooth();
        m.taperZ(0, -0.5f, -46f, 0.4f, 0.4f, -40f, 5.6f, 5.2f, 0.6f, PAINT);          // nose
        m.taperZ(0, 0.2f, -40f, 5.6f, 5.2f, -32f, 9.0f, 8.6f, 0.2f, PAINT);
        m.taperZ(0, 0.4f, -32f, 9.0f, 8.6f, 22f, 9.6f, 9.0f, 0f, PAINT);              // long fuselage
        m.taperZ(0, 0.4f, 22f, 9.6f, 9.0f, 38f, 4.0f, 5.2f, 2.4f, PAINT);           // tail cone rising
        m.panel();
        // cockpit windows: a dark glazed band
        m.taperZ(0, 3.4f, -40.5f, 3.6f, 1.2f, -35f, 6.8f, 1.6f, 1.2f, BLACK);
        m.box(-0.2f, 3.6f, -39.8f, 0.2f, 5.2f, -35.2f, PAINT);
        // dorsal hump with sensor fairing
        m.taperZ(0, 5.2f, -32f, 4.4f, 1.0f, 8f, 5.6f, 1.6f, 0, PAINT);
        m.cylY(5.8f, 6.8f, 0, -22f, 0.8f, 0.6f, 10, ACCENT, true);
        // high-mounted long-span swept wings
        m.wing(4.6f, -16f, 4f, 52f, 10f, 17f, 3.0f, 1.8f, 0.5f, -1.8f, PAINT);
        m.wing(-4.6f, -16f, 4f, -52f, 10f, 17f, 3.0f, 1.8f, 0.5f, -1.8f, PAINT);
        m.wing(10f, 2.4f, 4.5f, 46f, 15.0f, 16.6f, 2.0f, 0.6f, 0.4f, -1.6f, ACCENT);    // flaps
        m.wing(-10f, 2.4f, 4.5f, -46f, 15.0f, 16.6f, 2.0f, 0.6f, 0.4f, -1.6f, ACCENT);
        // four engines in underwing nacelles
        float[][] pods = {{-14, -9.0f}, {14, -9.0f}, {-27, -4.0f}, {27, -4.0f}};
        for (float[] p : pods) {
            float x = p[0], z = p[1];
            float y = 1.4f - Math.abs(x) * 0.02f;
            m.box(x - 0.4f, y - 0.8f, z + 3f, x + 0.4f, y + 0.8f, z + 9f, DARK);            // pylon
            m.cylZ(x, y - 2.4f, z - 1f, z + 10f, 1.9f, 1.6f, 14, PAINT, false);
            m.ringZ(x, y - 2.4f, z - 1.2f, z - 0.7f, 1.4f, 2.0f, 14, CHROME);
            m.cylZ(x, y - 2.4f, z - 0.8f, z - 0.79f, 1.4f, 1.4f, 14, DARK, true);
            m.cylZ(x, y - 2.4f, z - 0.5f, z - 0.49f, 0.5f, 0.5f, 10, CHROME, true);    // spinner
            m.cylZ(x, y - 2.4f, z + 10f, z + 11.4f, 1.6f, 1.1f, 14, CHROME, false);
            m.cylZ(x, y - 2.4f, z + 11f, z + 11.01f, 1.05f, 1.05f, 12, GLOW, true);
            m.cylZ(x, y - 2.4f, z + 10.8f, z + 10.81f, 0.6f, 0.6f, 10, HOT, true);
        }
        // T-tail
        m.fin(0, 4.0f, 22f, 37f, 19f, 32f, 39.5f, 1.0f, 0, PAINT);
        m.wing(0.5f, 31.5f, 39.6f, 17f, 37f, 41f, 18.8f, 0.9f, 0.4f, 0.4f, PAINT);
        m.wing(-0.5f, 31.5f, 39.6f, -17f, 37f, 41f, 18.8f, 0.9f, 0.4f, 0.4f, PAINT);
        m.fin(0, 12f, 30.2f, 36.6f, 17f, 32.6f, 38f, 1.05f, 0, TRIM);                    // tail flash
        // bomb bay doors outline + belly
        m.box(-3.4f, -4.15f, -14f, 3.4f, -4.0f, 8f, DARK);
        m.box(-0.08f, -4.25f, -14f, 0.08f, -4.1f, 8f, BLACK);
        // landing gear doors and antennae greebles
        m.box(-2.2f, -4.2f, -30f, 2.2f, -4.05f, -24f, ACCENT);
        m.fin(0, 4.8f, -14f, -12f, 7.2f, -13.2f, -12.4f, 0.3f, 0, DARK);
        // nav lights + tail beacon
        m.box(-52.4f, 1.0f, 15f, -51.6f, 1.8f, 16f, LENS);
        m.group(NAV_GREEN).box(51.6f, 1.0f, 15f, 52.4f, 1.8f, 16f, LENS).group(MAIN);
        m.box(-0.4f, 19f, 37.6f, 0.4f, 19.8f, 38.4f, HOT);
        return m;
    }

    // ------------------------------------------------------------------ WRAITH S-2: stealth flying wing

    private static WMesh wraith() {
        WMesh m = new WMesh();
        // centre body: a flattened lens, raised cockpit
        m.taperZ(0, 0.2f, -34f, 0.6f, 0.4f, -24f, 10f, 3.2f, 0.4f, PAINT);
        m.taperZ(0, 0.6f, -24f, 10f, 3.2f, 6f, 14f, 3.6f, 0f, PAINT);
        m.taperZ(0, 0.6f, 6f, 14f, 3.6f, 16f, 10f, 1.6f, -0.6f, PAINT);
        m.taperZ(0, 2.4f, -26f, 1.0f, 0.4f, -18f, 5.2f, 1.6f, 0.6f, BLACK);         // cockpit glazing
        m.taperZ(0, 2.8f, -18f, 5.2f, 1.0f, -12f, 5.6f, 0.6f, -0.4f, PAINT);
        // swept main wing with a sawtooth trailing edge
        m.wing(6f, -24f, 14f, 46f, 10f, 15f, 0.4f, 2.6f, 0.3f, 0, PAINT);
        m.wing(-6f, -24f, 14f, -46f, 10f, 15f, 0.4f, 2.6f, 0.3f, 0, PAINT);
        for (int s = -1; s <= 1; s += 2) {
            m.wing(s * 7f, 14f, 21f, s * 16f, 14f, 17f, 0.3f, 1.4f, 0.4f, 0, PAINT);
            m.wing(s * 16f, 14f, 17f, s * 24f, 14f, 21f, 0.3f, 0.4f, 1.0f, 0, PAINT);
            m.wing(s * 24f, 14f, 21f, s * 33f, 14f, 16f, 0.3f, 1.0f, 0.4f, 0, PAINT);
        }
        // darker radar-absorbent panels along the leading edge
        m.wing(7f, -21.5f, -18f, 44f, 9.8f, 11.5f, 1.55f, 0.3f, 0.12f, 0, ACCENT);
        m.wing(-7f, -21.5f, -18f, -44f, 9.8f, 11.5f, 1.55f, 0.3f, 0.12f, 0, ACCENT);
        // buried engine humps and slit exhausts over the wing
        for (int s = -1; s <= 1; s += 2) {
            float x = s * 8.5f;
            m.taperZ(x, 2.2f, -14f, 1.2f, 0.4f, -6f, 4.4f, 1.6f, 0, PAINT);
            m.box(x - 2.0f, 1.6f, -14.2f, x + 2.0f, 2.6f, -13.8f, DARK);              // inlet
            m.taperZ(x, 2.2f, -6f, 4.4f, 1.6f, 12f, 5.4f, 0.6f, -1.2f, PAINT);
            m.box(x - 2.6f, 0.95f, 12f, x + 2.6f, 1.25f, 13.2f, GLOW);                   // slit exhaust
            m.box(x - 2.0f, 1.0f, 11.8f, x + 2.0f, 1.2f, 12.6f, HOT);
        }
        // gold-tinted canopy rim and edge lights
        m.box(-46.4f, 0.1f, 10.4f, -45.6f, 0.7f, 11.4f, LENS);
        m.group(NAV_GREEN).box(45.6f, 0.1f, 10.4f, 46.4f, 0.7f, 11.4f, LENS).group(MAIN);
        m.box(-12f, 1.95f, -10f, -11.6f, 2.05f, 8f, TRIM);
        m.box(11.6f, 1.95f, -10f, 12f, 2.05f, 8f, TRIM);
        return m;
    }

    // ------------------------------------------------------------------ ordnance

    private static WMesh buildBomb() {
        WMesh m = new WMesh();
        m.cylZ(0, 0, -5.5f, 5.5f, 1.6f, 1.45f, 14, PAINT, false);
        m.cylZ(0, 0, -9.0f, -5.5f, 0.25f, 1.6f, 14, PAINT, true);                  // ogive nose
        m.cylZ(0, 0, 5.5f, 7.5f, 1.45f, 0.8f, 14, DARK, true);                       // boat tail
        m.ringZ(0, 0, -4.2f, -3.6f, 1.55f, 1.7f, 14, GLOW);                           // arming band
        m.ringZ(0, 0, 2.0f, 2.5f, 1.5f, 1.62f, 14, TRIM);
        m.box(-0.12f, -3.2f, 4.5f, 0.12f, 3.2f, 7.6f, DARK);                          // tail fins
        m.box(-3.2f, -0.12f, 4.5f, 3.2f, 0.12f, 7.6f, DARK);
        m.box(-0.8f, 1.5f, -2f, 0.8f, 1.9f, 1f, ACCENT);                              // lug / strake
        m.cylZ(0, 0, -9.05f, -9.06f, 0.3f, 0.3f, 8, HOT, true);                       // seeker
        return m;
    }
}
