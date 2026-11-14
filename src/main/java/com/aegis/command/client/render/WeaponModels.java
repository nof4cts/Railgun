package com.aegis.command.client.render;

import com.aegis.command.client.render.mesh.WMesh;
import com.aegis.command.item.WeaponType;

import java.util.EnumMap;
import java.util.Map;

import static com.aegis.command.client.render.mesh.WMesh.*;

/**
 * Hand-built hard-surface weapon models. Model space is in pixels: barrel toward -Z, the bore runs
 * along x = 8, the grip sits around z = +8, roughly y 3..14. Each model layers a dark chassis,
 * painted armour shells, accent plates, chrome internals, panel greebles and emissive energy parts
 * (glow strips, coils, cores, holo lenses), with MAG and SPIN groups that the animator can move.
 */
public final class WeaponModels {
    private static final Map<WeaponType, WMesh> CACHE = new EnumMap<>(WeaponType.class);

    private WeaponModels() {}

    public static WMesh get(WeaponType t) {
        return CACHE.computeIfAbsent(t, WeaponModels::build);
    }

    private static WMesh build(WeaponType t) {
        WMesh m = new WMesh();
        switch (t) {
            case PHANTOM_9 -> phantom(m);
            case TITAN_X -> titan(m);
            case SPECTRE_7 -> spectre(m);
            case NOVA_RAIL -> nova(m);
            case HELIX_4 -> helix(m);
            case OBLIVION -> oblivion(m);
        }
        return m;
    }

    // ------------------------------------------------------------------ shared details

    /** Picatinny-style top rail with notches. */
    private static void rail(WMesh m, float y, float z0, float z1, float w) {
        m.box(8 - w / 2, y, z0, 8 + w / 2, y + 0.35f, z1, BLACK);
        for (float z = z0 + 0.3f; z < z1 - 0.4f; z += 0.9f) m.box(8 - w / 2 - 0.08f, y + 0.35f, z, 8 + w / 2 + 0.08f, y + 0.6f, z + 0.5f, DARK);
    }

    /** Holographic sight: base, hooded frame, glass and a glowing reticle. */
    private static void holoSight(WMesh m, float y, float z, float scale) {
        float s = scale;
        m.box(8 - 1.1f * s, y, z - 2.2f * s, 8 + 1.1f * s, y + 0.7f * s, z + 1.8f * s, DARK);
        m.box(8 - 1.25f * s, y + 0.1f * s, z + 0.6f * s, 8 + 1.25f * s, y + 0.55f * s, z + 1.6f * s, BLACK); // mount knob strip
        // frame posts + hood (open rectangle facing the shooter)
        m.box(8 - 1.3f * s, y + 0.7f * s, z - 1.6f * s, 8 - 1.0f * s, y + 2.9f * s, z + 0.6f * s, PAINT);
        m.box(8 + 1.0f * s, y + 0.7f * s, z - 1.6f * s, 8 + 1.3f * s, y + 2.9f * s, z + 0.6f * s, PAINT);
        m.taperZ(8, y + 3.05f * s, z - 1.8f, 2.6f * s, 0.35f * s, z + 0.6f * s, 2.6f * s, 0.35f * s, 0, ACCENT);
        m.box(8 - 1.0f * s, y + 0.7f * s, z - 1.6f * s, 8 + 1.0f * s, y + 0.95f * s, z + 0.6f * s, BLACK);
        // emitter window on the rear frame + tinted glass
        m.box(8 - 0.25f * s, y + 0.95f * s, z + 0.35f * s, 8 + 0.25f * s, y + 1.25f * s, z + 0.55f * s, GLOW);
        m.box(8 - 0.98f * s, y + 0.97f * s, z - 1.2f * s, 8 + 0.98f * s, y + 2.85f * s, z - 1.08f * s, LENS);
        // reticle: centre dot + four ticks, floating just behind the glass
        float cy = y + 1.92f * s, rz = z - 1.02f * s;
        m.box(8 - 0.12f * s, cy - 0.12f * s, rz, 8 + 0.12f * s, cy + 0.12f * s, rz + 0.04f, HOT);
        m.box(8 - 0.06f * s, cy + 0.35f * s, rz, 8 + 0.06f * s, cy + 0.7f * s, rz + 0.04f, GLOW);
        m.box(8 - 0.06f * s, cy - 0.7f * s, rz, 8 + 0.06f * s, cy - 0.35f * s, rz + 0.04f, GLOW);
        m.box(8 - 0.7f * s, cy - 0.06f * s, rz, 8 - 0.35f * s, cy + 0.06f * s, rz + 0.04f, GLOW);
        m.box(8 + 0.35f * s, cy - 0.06f * s, rz, 8 + 0.7f * s, cy + 0.06f * s, rz + 0.04f, GLOW);
    }

    /** Angled pistol grip with finger grooves and a trigger + guard. */
    private static void pistolGrip(WMesh m, float y, float z, float angle) {
        m.push().rotAt(0, angle, 8, y, z);
        m.taperZ(8, y - 2.0f, z, 1.7f, 4.0f, z + 2.2f, 1.6f, 4.0f, 0, GRIP);
        for (int i = 0; i < 3; i++) m.box(7.1f, y - 3.4f + i * 1.1f, z - 0.15f, 8.9f, y - 3.0f + i * 1.1f, z + 0.1f, BLACK);
        m.box(7.05f, y - 4.2f, z - 0.2f, 8.95f, y - 3.8f, z + 2.5f, DARK); // base plate
        m.pop();
        // trigger and guard
        m.push().rotAt(0, 14, 8, y, z - 1.2f);
        m.box(7.75f, y - 1.6f, z - 1.5f, 8.25f, y, z - 1.1f, CHROME);
        m.pop();
        m.box(7.6f, y - 2.3f, z - 3.0f, 8.4f, y - 2.0f, z + 0.1f, BLACK);
        m.box(7.6f, y - 2.3f, z - 3.0f, 8.4f, y, z - 2.7f, BLACK);
    }

    /** Row of vent slots on both flanks, with an energy glow visible through them. */
    private static void vents(WMesh m, float xHalf, float y0, float y1, float z0, float z1, int n) {
        float step = (z1 - z0) / n;
        for (int i = 0; i < n; i++) {
            float z = z0 + i * step + step * 0.2f;
            m.boxMirror(8, 8 + xHalf - 0.05f, y0, z, 8 + xHalf + 0.12f, y1, z + step * 0.55f, BLACK);
            m.boxMirror(8, 8 + xHalf - 0.12f, y0 + 0.15f, z + 0.08f, 8 + xHalf - 0.02f, y1 - 0.15f, z + step * 0.47f, GLOW);
        }
    }

    // ------------------------------------------------------------------ PHANTOM-9 precision rifle

    private static void phantom(WMesh m) {
        // chassis
        m.box(6.7f, 6.9f, -1.5f, 9.3f, 9.9f, 12f, DARK);
        m.taperZ(8, 10.25f, -3f, 3.2f, 0.9f, 11.5f, 3.0f, 0.9f, 0, PAINT);       // upper receiver shell
        m.taperZ(8, 10.75f, -2.6f, 2.2f, 0.3f, 11f, 2.0f, 0.3f, 0, BLACK);       // shell seam
        // white sculpted side armour (angled panels)
        m.push().rotAt(2, 9, 6.4f, 8.4f, 4);
        m.box(6.05f, 7.3f, -2f, 6.55f, 9.9f, 9f, ACCENT);
        m.box(5.95f, 8.3f, 0.5f, 6.15f, 9.1f, 7.5f, TRIM);
        m.pop();
        m.push().rotAt(2, -9, 9.6f, 8.4f, 4);
        m.box(9.45f, 7.3f, -2f, 9.95f, 9.9f, 9f, ACCENT);
        m.box(9.85f, 8.3f, 0.5f, 10.05f, 9.1f, 7.5f, TRIM);
        m.pop();
        // barrel shroud: tapered, faceted, with vents
        m.taperZ(8, 8.6f, -11.2f, 2.5f, 2.3f, -1.5f, 3.1f, 2.9f, 0, PAINT);
        m.taperZ(8, 9.9f, -11f, 1.6f, 0.4f, -1.6f, 2.2f, 0.5f, 0, ACCENT);
        vents(m, 1.42f, 7.9f, 9.2f, -9.8f, -3.2f, 5);
        m.box(7.2f, 7.15f, -10.8f, 8.8f, 7.45f, -2f, GLOW);                    // under-barrel light bar
        m.box(7.0f, 7.0f, -11f, 9.0f, 7.15f, -1.8f, BLACK);
        // exposed bore with coil rings and a muzzle brake
        m.cylZ(8, 8.6f, -11.2f, -14.3f, 0.6f, 0.6f, 10, CHROME, false);
        for (int i = 0; i < 3; i++) m.ringZ(8, 8.6f, -11.6f - i * 0.75f, -11.9f - i * 0.75f, 0.6f, 0.95f, 12, GLOW);
        m.box(6.9f, 7.6f, -14.6f, 9.1f, 9.6f, -13.3f, DARK);
        m.boxMirror(8, 9.05f, 8.1f, -14.4f, 9.2f, 9.1f, -13.5f, BLACK);
        m.box(7.5f, 9.5f, -14.5f, 8.5f, 9.75f, -13.4f, BLACK);
        m.cylZ(8, 8.6f, -14.61f, -14.62f, 0.45f, 0.45f, 10, HOT, true);        // glowing bore
        // top rail + holo sight
        rail(m, 10.7f, -2.8f, 9f, 1.5f);
        holoSight(m, 11.3f, 3.5f, 1f);
        // energy core: a glass cylinder through the receiver, caged
        m.cylX(6.2f, 9.8f, 8.6f, 6.8f, 0.95f, 0.95f, 12, GLOW, true);
        m.cylX(6.0f, 6.4f, 8.6f, 6.8f, 1.25f, 1.25f, 12, CHROME, true);
        m.cylX(9.6f, 10.0f, 8.6f, 6.8f, 1.25f, 1.25f, 12, CHROME, true);
        for (int i = 0; i < 3; i++) m.box(6.3f, 7.4f + i * 0.85f, 6.6f, 9.7f, 7.55f + i * 0.85f, 7.0f, DARK);
        // energy cell magazine (MAG group)
        m.group(MAG);
        m.push().rotAt(0, 10, 8, 7, 2.5f);
        m.box(7.05f, 3.7f, 1.0f, 8.95f, 7.0f, 4.0f, DARK);
        m.box(7.0f, 3.5f, 0.9f, 9.0f, 3.9f, 4.1f, PAINT);
        m.boxMirror(8, 8.95f, 4.2f, 1.6f, 9.05f, 6.6f, 2.0f, GLOW);
        m.boxMirror(8, 8.95f, 4.2f, 2.6f, 9.05f, 6.6f, 3.0f, GLOW);
        m.pop();
        m.group(MAIN);
        pistolGrip(m, 7.0f, 8.6f, -16);
        // skeleton stock
        m.taperZ(8, 9.2f, 12f, 1.8f, 1.6f, 20f, 1.5f, 1.2f, 0, PAINT);
        m.taperZ(8, 6.0f, 15f, 1.4f, 1.0f, 20f, 1.4f, 1.0f, 0, DARK);
        m.push().rotAt(0, 38, 8, 6.4f, 15.2f);
        m.box(7.35f, 6.0f, 15f, 8.65f, 6.8f, 19.2f, DARK);
        m.pop();
        m.box(6.8f, 5.0f, 19.8f, 9.2f, 10.2f, 21.4f, GRIP);
        m.box(6.9f, 9.6f, 18f, 9.1f, 10.3f, 20f, ACCENT);                        // cheek rest
        m.boxMirror(8, 8.9f, 9.0f, 13f, 9.0f, 9.4f, 19f, GLOW);                   // stock light lines
        // small details
        m.box(9.3f, 9.2f, 9.5f, 9.6f, 9.6f, 11.5f, CHROME);                        // charging handle
        m.cylX(9.3f, 9.9f, 9.4f, 11.3f, 0.3f, 0.3f, 8, CHROME, true);
        m.box(6.6f, 7.8f, 10.5f, 6.75f, 8.4f, 11.6f, GLOW);                        // status LEDs
    }

    // ------------------------------------------------------------------ TITAN-X heavy arc cannon

    private static void titan(WMesh m) {
        // armoured body
        m.taperZ(8, 8.0f, -2.5f, 6.4f, 5.4f, 13.5f, 6.8f, 6.0f, 0, PAINT);
        m.taperZ(8, 11.3f, -2f, 5.2f, 1.2f, 12.5f, 5.8f, 1.4f, 0, PAINT);       // top deck
        m.taperZ(8, 4.9f, -1.5f, 5.0f, 1.0f, 12.5f, 5.6f, 1.2f, 0, DARK);       // belly
        // red armour plates (angled), with black seams and warning stripes
        m.push().rotAt(2, 12, 4.6f, 8, 5);
        m.box(4.2f, 6.2f, -1.8f, 4.85f, 10.8f, 7.5f, ACCENT);
        m.box(4.1f, 6.6f, 0.2f, 4.25f, 7.0f, 6f, BLACK);
        m.pop();
        m.push().rotAt(2, -12, 11.4f, 8, 5);
        m.box(11.15f, 6.2f, -1.8f, 11.8f, 10.8f, 7.5f, ACCENT);
        m.box(11.75f, 6.6f, 0.2f, 11.9f, 7.0f, 6f, BLACK);
        m.pop();
        for (int i = 0; i < 4; i++) m.box(5.2f + i * 1.6f, 11.95f, 9.5f, 5.9f + i * 1.6f, 12.05f, 12f, ACCENT);
        // heat-sink fins on the rear flanks, energy behind them
        for (int i = 0; i < 6; i++) {
            float z = 7.8f + i * 0.9f;
            m.boxMirror(8, 11.3f, 6.3f, z, 11.8f, 10.4f, z + 0.35f, DARK);
        }
        m.boxMirror(8, 11.05f, 6.6f, 7.6f, 11.2f, 10.1f, 13.0f, GLOW);
        // main barrel: thick armoured tube with three induction coils
        m.cylZ(8, 8.5f, -2.5f, -12f, 2.2f, 2.0f, 14, DARK, false);
        for (int i = 0; i < 3; i++) {
            float z = -4.2f - i * 2.5f;
            m.ringZ(8, 8.5f, z, z - 0.9f, 2.0f, 2.55f, 14, GLOW);
            m.ringZ(8, 8.5f, z + 0.35f, z + 0.05f, 2.05f, 2.75f, 14, ACCENT);
            m.ringZ(8, 8.5f, z - 0.95f, z - 1.25f, 2.05f, 2.75f, 14, ACCENT);
        }
        // armour spine over the barrel + barrel clamps
        m.taperZ(8, 10.9f, -11.5f, 1.4f, 0.8f, -2.5f, 2.2f, 1.3f, 0, PAINT);
        m.box(5.4f, 7.9f, -3.2f, 10.6f, 9.1f, -2.5f, BLACK);
        // muzzle: flared crown with a glowing throat
        m.cylZ(8, 8.5f, -12f, -14.4f, 2.4f, 2.9f, 14, CHROME, false);
        m.ringZ(8, 8.5f, -14.2f, -14.45f, 1.6f, 2.95f, 14, DARK);
        m.cylZ(8, 8.5f, -14.3f, -14.31f, 1.6f, 1.6f, 14, HOT, true);
        m.cylZ(8, 8.5f, -13.6f, -13.61f, 1.9f, 1.9f, 14, GLOW, true);
        for (int i = 0; i < 4; i++) {
            m.push().rotAt(2, 45 + i * 90, 8, 8.5f, 0);
            m.box(7.7f, 11.0f, -14.3f, 8.3f, 11.6f, -11.8f, DARK);              // crown prongs
            m.pop();
        }
        // three plasma chambers on the top deck (caged glass)
        for (int i = 0; i < 3; i++) {
            float z = 1.0f + i * 3.0f;
            m.cylY(11.9f, 14.6f, 8, z, 0.95f, 0.95f, 12, GLOW, true);
            m.cylY(11.9f, 12.4f, 8, z, 1.25f, 1.25f, 12, DARK, true);
            m.cylY(14.4f, 14.9f, 8, z, 1.25f, 1.25f, 12, CHROME, true);
            for (int k = 0; k < 4; k++) {
                m.push().rotAt(1, 45 + k * 90, 8, 13, z);
                m.box(9.1f, 12.4f, z - 0.15f, 9.35f, 14.4f, z + 0.15f, BLACK);
                m.pop();
            }
        }
        // carry handle
        m.box(7.5f, 15.0f, -1.5f, 8.5f, 15.6f, 9.5f, BLACK);
        m.box(7.6f, 12f, -1.4f, 8.4f, 15.2f, -0.6f, DARK);
        // grips: rear pistol grip + vertical fore-handle
        pistolGrip(m, 5.2f, 9.2f, -10);
        m.taperZ(8, 2.8f, -1.6f, 1.6f, 3.2f, 0.4f, 1.4f, 3.2f, 0, GRIP);
        // power cell (MAG group) slotted into the rear
        m.group(MAG);
        m.box(5.8f, 6.0f, 13.4f, 10.2f, 10.4f, 16.0f, DARK);
        m.box(6.2f, 6.4f, 15.9f, 9.8f, 10.0f, 16.3f, GLOW);
        m.box(5.6f, 5.8f, 14.2f, 10.4f, 6.3f, 15.2f, ACCENT);
        m.group(MAIN);
        // shoulder brace
        m.box(6.4f, 5.4f, 16.3f, 9.6f, 10.8f, 17.6f, GRIP);
        m.box(5.8f, 6.0f, 17.6f, 10.2f, 11.4f, 20f, PAINT);
        m.box(5.6f, 5.6f, 19.6f, 10.4f, 11.8f, 20.4f, GRIP);
        // vents on the rear deck
        for (int i = 0; i < 5; i++) m.box(6.0f + i * 0.85f, 11.98f, 3.4f, 6.4f + i * 0.85f, 12.08f, 5.2f, GLOW);
    }

    // ------------------------------------------------------------------ SPECTRE-7 compact plasma SMG

    private static void spectre(WMesh m) {
        // sharp angular body
        m.taperZ(8, 8.3f, -4.5f, 2.9f, 3.4f, 9.5f, 2.9f, 3.8f, 0, PAINT);
        m.taperZ(8, 8.6f, -9.0f, 1.8f, 1.6f, -4.5f, 2.9f, 3.0f, 0, PAINT);       // raked nose
        m.taperZ(8, 10.2f, -4.2f, 2.4f, 0.4f, 9f, 2.6f, 0.5f, 0, BLACK);
        // black accent wedges cutting across the white body
        m.push().rotAt(0, -20, 8, 8, 0);
        m.boxMirror(8, 9.42f, 6.7f, -2.5f, 9.55f, 9.5f, 1.0f, ACCENT);
        m.pop();
        m.boxMirror(8, 9.42f, 6.8f, 3.5f, 9.55f, 9.6f, 8.6f, ACCENT);
        // digital circuitry: segmented glow traces on both flanks
        float[][] traces = {{-3.6f, -1.6f, 9.2f}, {-1.2f, 0.6f, 9.2f}, {1.0f, 2.0f, 9.2f}, {-2.6f, 1.6f, 7.5f}, {2.0f, 3.2f, 7.9f}};
        for (float[] tr : traces) m.boxMirror(8, 9.46f, tr[2], tr[0], 9.62f, tr[2] + 0.18f, tr[1], GLOW);
        m.boxMirror(8, 9.46f, 7.5f, 1.6f, 9.62f, 7.9f, 1.8f, GLOW);
        m.boxMirror(8, 9.6f, 9.0f, 4.0f, 9.7f, 9.3f, 8.0f, TRIM);
        // short bore and split-prong muzzle
        m.cylZ(8, 8.4f, -9f, -10.6f, 0.55f, 0.55f, 10, CHROME, false);
        m.box(7.4f, 9.6f, -10.8f, 8.6f, 10.0f, -8.5f, DARK);
        m.box(7.4f, 6.8f, -10.8f, 8.6f, 7.2f, -8.5f, DARK);
        m.box(7.6f, 9.75f, -11.0f, 8.4f, 9.9f, -10.6f, GLOW);
        m.box(7.6f, 6.9f, -11.0f, 8.4f, 7.05f, -10.6f, GLOW);
        m.cylZ(8, 8.4f, -10.61f, -10.62f, 0.4f, 0.4f, 10, HOT, true);
        // top-loaded plasma cell in a clear housing (SPIN group turns the cell)
        m.box(6.9f, 10.4f, -2.4f, 9.1f, 10.6f, 6.6f, DARK);
        m.box(7.0f, 10.6f, -2.2f, 9.0f, 11.95f, -1.8f, PAINT);
        m.box(7.0f, 10.6f, 6.0f, 9.0f, 11.95f, 6.4f, PAINT);
        m.box(7.05f, 10.6f, -1.8f, 8.95f, 11.9f, 6.0f, LENS);
        m.group(SPIN);
        m.cylZ(8, 10.9f, -1.6f, 5.8f, 0.5f, 0.5f, 6, GLOW, true);
        for (int i = 0; i < 4; i++) m.ringZ(8, 10.9f, -1.0f + i * 1.8f, -0.7f + i * 1.8f, 0.5f, 0.62f, 6, CHROME);
        m.group(MAIN);
        // micro red-dot
        m.box(7.4f, 12.0f, 3.0f, 8.6f, 12.3f, 5.6f, DARK);
        m.box(7.3f, 12.3f, 4.2f, 8.7f, 13.6f, 4.5f, PAINT);
        m.box(7.45f, 12.4f, 4.15f, 8.55f, 13.45f, 4.2f, LENS);
        m.box(7.93f, 12.9f, 4.22f, 8.07f, 13.04f, 4.26f, HOT);
        // vertical foregrip
        m.taperZ(8, 5.0f, -3.6f, 1.2f, 2.6f, -2.4f, 1.1f, 2.6f, 0, GRIP);
        m.box(7.3f, 3.6f, -3.8f, 8.7f, 3.9f, -2.2f, ACCENT);
        // angled magazine (MAG)
        m.group(MAG);
        m.push().rotAt(0, 14, 8, 6.4f, 3);
        m.box(7.25f, 2.6f, 2.0f, 8.75f, 6.6f, 3.8f, DARK);
        m.box(7.2f, 2.4f, 1.9f, 8.8f, 2.8f, 3.9f, ACCENT);
        m.boxMirror(8, 8.72f, 3.0f, 2.6f, 8.82f, 6.2f, 3.1f, GLOW);
        m.pop();
        m.group(MAIN);
        pistolGrip(m, 6.7f, 6.8f, -14);
        // folding wire stock
        m.boxMirror(8, 8.6f, 9.2f, 9.5f, 8.9f, 9.5f, 15.0f, PAINT);
        m.boxMirror(8, 8.6f, 6.4f, 9.5f, 8.9f, 6.7f, 15.0f, PAINT);
        m.box(7.1f, 6.2f, 14.8f, 8.9f, 9.7f, 15.6f, GRIP);
        m.box(6.9f, 8.0f, 9.0f, 9.1f, 8.4f, 9.8f, CHROME);                       // hinge
    }

    // ------------------------------------------------------------------ NOVA-RAIL magnetic lance

    private static void nova(WMesh m) {
        // receiver
        m.box(6.6f, 6.8f, -1f, 9.4f, 10.4f, 12.5f, DARK);
        m.taperZ(8, 10.8f, -1.5f, 3.0f, 0.8f, 12f, 3.2f, 0.9f, 0, PAINT);
        m.taperZ(8, 6.4f, -0.5f, 2.6f, 0.8f, 11f, 2.8f, 0.8f, 0, PAINT);
        m.push().rotAt(2, 8, 6.3f, 8.6f, 5);
        m.box(5.9f, 7.0f, 0f, 6.45f, 10.2f, 11f, ACCENT);
        m.pop();
        m.push().rotAt(2, -8, 9.7f, 8.6f, 5);
        m.box(9.55f, 7.0f, 0f, 10.1f, 10.2f, 11f, ACCENT);
        m.pop();
        // twin rails with a plasma channel between them
        m.box(6.9f, 9.7f, -15f, 9.1f, 10.5f, -1f, CHROME);
        m.box(6.9f, 6.7f, -15f, 9.1f, 7.5f, -1f, CHROME);
        m.box(7.3f, 9.55f, -14.8f, 8.7f, 9.7f, -1.2f, GLOW);
        m.box(7.3f, 7.5f, -14.8f, 8.7f, 7.65f, -1.2f, GLOW);
        m.box(7.9f, 7.65f, -14.6f, 8.1f, 9.55f, -1.4f, LENS);                   // arc sheet
        // rail yokes
        for (int i = 0; i < 5; i++) {
            float z = -13.4f + i * 2.6f;
            m.box(6.5f, 6.4f, z, 9.5f, 6.75f, z + 0.7f, PAINT);
            m.box(6.5f, 10.45f, z, 9.5f, 10.8f, z + 0.7f, PAINT);
            m.boxMirror(8, 9.1f, 6.4f, z, 9.5f, 10.8f, z + 0.7f, PAINT);
            m.boxMirror(8, 9.45f, 8.2f, z + 0.15f, 9.55f, 9.0f, z + 0.55f, GLOW);
        }
        // muzzle emitter tips
        m.box(6.7f, 10.5f, -15.4f, 9.3f, 10.9f, -14.6f, ACCENT);
        m.box(6.7f, 6.3f, -15.4f, 9.3f, 6.7f, -14.6f, ACCENT);
        // capacitor bank: four glowing cells, caged
        for (int i = 0; i < 4; i++) {
            float z = 1.4f + i * 2.0f;
            m.cylX(5.6f, 10.4f, 8.6f, z, 0.6f, 0.6f, 10, GLOW, true);
            m.cylX(5.4f, 5.8f, 8.6f, z, 0.85f, 0.85f, 10, CHROME, true);
            m.cylX(10.2f, 10.6f, 8.6f, z, 0.85f, 0.85f, 10, CHROME, true);
        }
        // long scope
        m.cylZ(8, 13.1f, -2.5f, 8.5f, 0.95f, 0.95f, 12, DARK, false);
        m.cylZ(8, 13.1f, -3.8f, -2.5f, 1.35f, 0.95f, 12, PAINT, false);
        m.cylZ(8, 13.1f, 8.5f, 9.8f, 0.95f, 1.2f, 12, PAINT, false);
        m.cylZ(8, 13.1f, -3.75f, -3.76f, 1.25f, 1.25f, 12, LENS, true);
        m.cylZ(8, 13.1f, 9.75f, 9.76f, 1.1f, 1.1f, 12, LENS, true);
        m.box(7.95f, 12.5f, 9.6f, 8.05f, 13.7f, 9.7f, GLOW);
        m.box(7.4f, 13.05f, 9.6f, 8.6f, 13.15f, 9.7f, GLOW);
        m.cylY(13.0f, 13.6f, 8, 3.0f, 0.5f, 0.5f, 8, CHROME, true);
        m.box(7.3f, 11.2f, 0.0f, 8.7f, 12.3f, 1.0f, BLACK);
        m.box(7.3f, 11.2f, 5.6f, 8.7f, 12.3f, 6.6f, BLACK);
        // slug magazine (MAG)
        m.group(MAG);
        m.box(7.0f, 3.4f, 2.0f, 9.0f, 6.4f, 5.0f, DARK);
        m.box(7.05f, 3.4f, 2.4f, 8.95f, 3.6f, 4.6f, ACCENT);
        m.boxMirror(8, 8.95f, 4.0f, 3.2f, 9.05f, 6.0f, 3.8f, GLOW);
        m.group(MAIN);
        pistolGrip(m, 6.9f, 9.0f, -16);
        // folded bipod
        m.boxMirror(8, 8.3f, 5.9f, -12.6f, 8.7f, 6.3f, -6.0f, BLACK);
        m.box(7.2f, 6.0f, -12.9f, 8.8f, 6.4f, -12.4f, DARK);
        // heavy stock with recoil buffer
        m.taperZ(8, 8.6f, 12.5f, 2.6f, 3.4f, 19.5f, 2.2f, 4.6f, -0.3f, PAINT);
        m.cylZ(8, 10.0f, 12.5f, 17.0f, 0.5f, 0.5f, 8, CHROME, true);
        m.box(6.7f, 5.4f, 19.4f, 9.3f, 11.0f, 21.2f, GRIP);
        m.boxMirror(8, 9.08f, 7.6f, 13.5f, 9.18f, 8.0f, 18.5f, GLOW);
    }

    // ------------------------------------------------------------------ HELIX-4 scatter shotgun

    private static void helix(WMesh m) {
        // receiver
        m.taperZ(8, 8.3f, -1.5f, 3.2f, 4.0f, 10.5f, 3.2f, 4.2f, 0, PAINT);
        m.taperZ(8, 10.6f, -1.2f, 2.6f, 0.6f, 10f, 2.6f, 0.6f, 0, ACCENT);
        m.boxMirror(8, 9.55f, 7.0f, 0.5f, 9.7f, 9.8f, 8.5f, DARK);
        m.boxMirror(8, 9.65f, 7.4f, 1.0f, 9.75f, 7.7f, 8.0f, TRIM);
        // fat barrel
        m.cylZ(8, 8.6f, -1.5f, -12.6f, 1.25f, 1.25f, 12, DARK, false);
        // the helix: an energy coil wound around the barrel (SPIN group)
        m.group(SPIN);
        int turns = 4, segs = 12;
        for (int i = 0; i < turns * segs; i++) {
            float z = -2.4f - i * (8.8f / (turns * segs));
            m.push().rotAt(2, i * (360f / segs), 8, 8.6f, z);
            m.box(7.75f, 10.0f, z - 0.24f, 8.25f, 10.35f, z, GLOW);
            m.pop();
        }
        m.group(MAIN);
        // coil guard cage
        for (int k = 0; k < 6; k++) {
            m.push().rotAt(2, k * 60 + 30, 8, 8.6f, 0);
            m.box(7.85f, 10.4f, -11.6f, 8.15f, 10.65f, -2.0f, BLACK);
            m.pop();
        }
        m.ringZ(8, 8.6f, -1.6f, -2.2f, 1.2f, 2.15f, 12, PAINT);
        m.ringZ(8, 8.6f, -11.4f, -12.0f, 1.2f, 2.15f, 12, PAINT);
        // flared scatter muzzle
        m.cylZ(8, 8.6f, -12.0f, -13.8f, 1.6f, 2.2f, 12, CHROME, false);
        m.ringZ(8, 8.6f, -13.6f, -13.85f, 1.3f, 2.25f, 12, ACCENT);
        m.cylZ(8, 8.6f, -13.7f, -13.71f, 1.35f, 1.35f, 12, HOT, true);
        // pump fore-end (MAG group: it racks back on reload)
        m.group(MAG);
        m.taperZ(8, 6.4f, -9.0f, 2.6f, 1.6f, -4.0f, 2.6f, 1.8f, 0, GRIP);
        for (int i = 0; i < 4; i++) m.box(6.65f, 5.5f, -8.4f + i * 1.1f, 9.35f, 5.7f, -7.9f + i * 1.1f, BLACK);
        m.group(MAIN);
        m.box(7.6f, 6.8f, -10f, 8.4f, 7.4f, -1.5f, CHROME);                       // pump rod
        // revolving shell drum under the receiver: six glowing plasma shells
        m.cylZ(8, 5.6f, 0.4f, 5.6f, 1.9f, 1.9f, 12, DARK, true);
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2;
            float sx = 8 + (float) Math.cos(a) * 1.2f, sy = 5.6f + (float) Math.sin(a) * 1.2f;
            m.cylZ(sx, sy, 0.3f, 5.7f, 0.42f, 0.42f, 8, GLOW, true);
        }
        m.ringZ(8, 5.6f, 0.2f, 0.6f, 1.8f, 2.1f, 12, CHROME);
        m.ringZ(8, 5.6f, 5.4f, 5.8f, 1.8f, 2.1f, 12, CHROME);
        // ghost-ring sight and rail
        rail(m, 10.9f, 0f, 8f, 1.3f);
        m.box(7.2f, 11.4f, 6.4f, 8.8f, 12.6f, 6.8f, DARK);
        m.ringZ(8, 12.3f, 6.45f, 6.75f, 0.35f, 0.6f, 10, ACCENT);
        m.box(7.9f, 10.9f, -12.2f, 8.1f, 11.6f, -11.8f, GLOW);                   // front post
        pistolGrip(m, 6.4f, 8.4f, -18);
        // stock
        m.taperZ(8, 8.4f, 10.5f, 2.6f, 3.2f, 19.5f, 2.2f, 4.8f, -0.6f, PAINT);
        m.boxMirror(8, 8.95f, 7.0f, 12f, 9.15f, 9.0f, 17f, ACCENT);
        m.box(6.8f, 5.2f, 19.4f, 9.2f, 10.6f, 21.0f, GRIP);
        for (int i = 0; i < 4; i++) m.boxMirror(8, 9.12f, 7.3f, 12.4f + i * 1.2f, 9.2f, 7.6f, 12.9f + i * 1.2f, GLOW);
    }

    // ------------------------------------------------------------------ OBLIVION singularity projector

    private static void oblivion(WMesh m) {
        // lower frame
        m.taperZ(8, 7.2f, -4f, 3.0f, 2.6f, 13f, 3.4f, 3.0f, 0, PAINT);
        m.taperZ(8, 5.9f, -2f, 2.4f, 0.6f, 12f, 2.8f, 0.6f, 0, ACCENT);
        // containment chamber: glass orb housing with the singularity inside (SPIN group swirls)
        m.cylZ(8, 10.0f, 1.6f, 8.4f, 3.0f, 3.0f, 16, LENS, false);
        m.ringZ(8, 10.0f, 1.2f, 2.0f, 2.8f, 3.4f, 16, PAINT);
        m.ringZ(8, 10.0f, 8.0f, 8.8f, 2.8f, 3.4f, 16, PAINT);
        m.ringZ(8, 10.0f, 4.7f, 5.3f, 2.95f, 3.25f, 16, ACCENT);
        for (int k = 0; k < 6; k++) {
            m.push().rotAt(2, k * 60, 8, 10, 0);
            m.box(7.8f, 12.95f, 1.6f, 8.2f, 13.25f, 8.4f, DARK);
            m.pop();
        }
        m.cylZ(8, 10.0f, 0.4f, 1.3f, 2.2f, 2.8f, 16, DARK, true);
        m.cylZ(8, 10.0f, 8.7f, 9.6f, 2.8f, 2.2f, 16, DARK, true);
        m.group(SPIN);
        m.sphere(8, 10.0f, 5.0f, 1.0f, 6, HOT);
        m.ringZ(8, 10.0f, 4.85f, 5.15f, 1.45f, 1.75f, 14, GLOW);
        m.push().rotAt(1, 90, 8, 10, 5);
        m.ringZ(8, 10.0f, 4.88f, 5.12f, 1.9f, 2.1f, 14, GLOW);
        m.pop();
        m.push().rotAt(0, 60, 8, 10, 5);
        m.ringZ(8, 10.0f, 4.88f, 5.12f, 2.2f, 2.4f, 14, GLOW);
        m.pop();
        m.group(MAIN);
        // feed throat from chamber to the emitter
        m.taperZ(8, 9.5f, -4.0f, 1.6f, 1.6f, 0.6f, 2.6f, 2.6f, 0, DARK);
        m.cylZ(8, 9.0f, -4.5f, 0.5f, 0.55f, 0.55f, 8, GLOW, false);
        // tri-prong emitter around the bore + halo ring (AUX group spins the other way)
        for (int k = 0; k < 3; k++) {
            m.push().rotAt(2, k * 120, 8, 9.0f, 0);
            m.wing(7.6f, -3.8f, -2.5f, 7.6f, -13.6f, -12.4f, 11.6f, 0.6f, 0.4f, -1.4f, PAINT);
            m.box(7.55f, 11.7f, -6.5f, 8.45f, 12.4f, -3.8f, ACCENT);
            m.box(7.7f, 9.9f, -13.8f, 8.3f, 10.5f, -12.6f, GLOW);
            m.pop();
        }
        m.group(AUX);
        m.ringZ(8, 9.0f, -8.2f, -8.6f, 2.8f, 3.2f, 18, GLOW);
        for (int k = 0; k < 6; k++) {
            m.push().rotAt(2, k * 60, 8, 9.0f, 0);
            m.box(7.7f, 12.2f, -8.7f, 8.3f, 12.7f, -8.1f, CHROME);
            m.pop();
        }
        m.group(MAIN);
        m.cylZ(8, 9.0f, -13.4f, -13.6f, 0.7f, 0.7f, 10, HOT, true);
        // grips: pistol grip at the rear, angled fore-handle
        pistolGrip(m, 6.0f, 10.5f, -14);
        m.push().rotAt(0, 20, 8, 5.8f, -1.5f);
        m.taperZ(8, 4.2f, -2.4f, 1.4f, 3.0f, -0.8f, 1.3f, 3.0f, 0, GRIP);
        m.pop();
        // fuel canister (MAG): a dense cell under the chamber
        m.group(MAG);
        m.cylZ(8, 4.8f, 2.0f, 7.4f, 1.3f, 1.3f, 12, DARK, true);
        m.ringZ(8, 4.8f, 3.0f, 3.4f, 1.25f, 1.45f, 12, GLOW);
        m.ringZ(8, 4.8f, 5.6f, 6.0f, 1.25f, 1.45f, 12, GLOW);
        m.group(MAIN);
        // stock with stabiliser fins
        m.taperZ(8, 8.2f, 12.5f, 2.6f, 3.0f, 19.5f, 2.4f, 4.4f, -0.4f, PAINT);
        m.fin(8, 10.2f, 13f, 18f, 12.6f, 15.5f, 18.4f, 0.4f, 0, ACCENT);
        m.box(6.8f, 5.6f, 19.4f, 9.2f, 10.6f, 21.0f, GRIP);
        m.boxMirror(8, 9.18f, 8.0f, 13.5f, 9.28f, 8.3f, 18.0f, GLOW);
    }
}
