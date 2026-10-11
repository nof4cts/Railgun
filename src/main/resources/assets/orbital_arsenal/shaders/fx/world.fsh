#version 150
#include "common.glsl"
// The strikes, drawn into the finished world with its depth: everything here is volumetric or lit, never a flat card.
//   Helios   - the lance as a column of light with a white core and turbulent gold sheath, scattering in the air; it
//              lights the land round it, melts the ground where it stands into flowing magma, and bends the air.
//   Shock    - an expanding dome that refracts what is behind it and runs a bright band across the ground.
//   Antipode - the sky stained crimson and split by a web of light; the other world's land hanging upside down above,
//              ray-marched as real relief (crimson seas, violet rock, teal moss, burning fissures); lightning between.
// All positions are relative to the camera, in blocks.

uniform sampler2D Scene;
uniform sampler2D Depth;
uniform mat4 InvProjView;
uniform mat4 ProjView;
uniform vec2 ScreenSize;
uniform float Time;

uniform vec4 Beam;      // ground point, radius (radius 0: no lance)
uniform vec4 BeamP;     // top y, lowest y reached so far, intensity, how molten the ground is
uniform vec4 Shock;     // centre, radius (0: none)
uniform vec4 ShockP;    // thickness, strength
uniform vec3 ShockC;
uniform vec4 Mirror;    // zone centre on the ground, zone radius (0: no Antipode)
uniform vec4 MirrorP;   // height of the hanging land's base above the centre, opacity, flash, span (in zone radii)
uniform vec4 Cracks;    // growth, opacity, height above the centre, seed
uniform float Crimson;  // how far the sky and light have turned
uniform float Lightning;

in vec2 uv;
out vec4 fragColor;

// ------------------------------------------------------------------------------------------------ noise

float h2(vec2 p) { return hash12(p); }

float n2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1, 0)), u.x), mix(h2(i + vec2(0, 1)), h2(i + vec2(1, 1)), u.x), u.y);
}

float f2(vec2 p, int oct) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 7; i++) {
        if (i >= oct) break;
        v += a * n2(p);
        p = p * 2.02 + vec2(5.3, 1.7);
        a *= 0.5;
    }
    return v;
}

/** Distance to the nearest edge between Voronoi cells: crack lines. */
float voronoiEdge(vec2 p, float seed) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 o = vec2(h2(i + g + seed), h2(i + g + seed + 17.0));
            float d = length(g + o - f);
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
        }
    }
    return d2 - d1;
}

// ------------------------------------------------------------------------------------------------ helpers

vec3 worldAt(vec2 st, float depth) {
    vec4 p = InvProjView * vec4(st * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

vec2 toScreen(vec3 p) {
    vec4 c = ProjView * vec4(p, 1.0);
    return c.xy / max(c.w, 1e-4) * 0.5 + 0.5;
}

/** Closest approach of the ray (from the camera, length tMax) to a vertical line through c: distance, ray t, y there. */
vec3 toVertical(vec3 rd, vec2 c, float tMax) {
    vec2 d = rd.xz;
    float dd = max(dot(d, d), 1e-6);
    float t = clamp(dot(c, d) / dd, 0.0, tMax);
    vec2 q = d * t - c;
    return vec3(length(q), t, rd.y * t);
}

// ------------------------------------------------------------------------------------------------ the hanging land

float hang(vec2 xz) {
    vec2 u = (xz - Mirror.xz) / (Mirror.w * MirrorP.w);
    float r2 = dot(u, u);
    if (r2 > 1.0) return 0.0;
    float fall = 1.0 - r2;
    float peak = max(0.0, (f2(u * 7.0 + Cracks.w, 5) - 0.4) / 0.6);
    float ridge = pow(1.0 - abs(2.0 * f2(u * 16.0 + 3.0 + Cracks.w, 5) - 1.0), 6.0);
    float centre = exp(-r2 * MirrorP.w * MirrorP.w * 1.6);
    return Mirror.w * 0.9 * min(1.0, pow(peak, 1.6) * fall * 0.55 + ridge * 0.12 * fall + centre * (0.9 + 0.1 * peak));
}

/** March the ray into the hanging land: hit t (or -1) and where. */
float marchLand(vec3 rd, float tMax, out vec3 hit, out bool top) {
    top = false;
    float span = Mirror.w * MirrorP.w;
    float base = Mirror.y + MirrorP.x;
    float bottom = base - Mirror.w * 0.95;
    float thick = base + Mirror.w * 0.35;
    // the slab the land can occupy, clipped to its disc (as a box: cheap and good enough to bound the march)
    vec3 lo = vec3(Mirror.x - span, bottom, Mirror.z - span);
    vec3 hi = vec3(Mirror.x + span, thick, Mirror.z + span);
    vec3 inv = 1.0 / (rd + vec3(1e-6));
    vec3 t0 = lo * inv, t1 = hi * inv;
    vec3 tn = min(t0, t1), tf = max(t0, t1);
    float a = max(max(tn.x, tn.y), max(tn.z, 0.0));
    float b = min(min(tf.x, tf.y), min(tf.z, tMax));
    if (a >= b) return -1.0;
    const int STEPS = 80;
    float dt = (b - a) / float(STEPS);
    float t = a;
    float prev = -1.0;
    for (int i = 0; i <= STEPS; i++) {
        vec3 p = rd * t;
        float ys = base - hang(p.xz);
        bool inDisc = dot(p.xz - Mirror.xz, p.xz - Mirror.xz) < span * span;
        if (inDisc && p.y >= ys && p.y <= thick) {
            // refine between the last step and this one
            float lo2 = max(a, t - dt), hi2 = t;
            for (int k = 0; k < 6; k++) {
                float m = 0.5 * (lo2 + hi2);
                vec3 q = rd * m;
                if (q.y >= base - hang(q.xz)) hi2 = m; else lo2 = m;
            }
            hit = rd * hi2;
            top = hit.y > thick - 0.5;
            return hi2;
        }
        t += dt;
    }
    return -1.0;
}

vec3 shadeLand(vec3 p, vec3 rd, bool top, float dist) {
    vec2 u = (p.xz - Mirror.xz) / (Mirror.w * MirrorP.w);
    float e = 1.5;
    float hx = hang(p.xz + vec2(e, 0.0)) - hang(p.xz - vec2(e, 0.0));
    float hz = hang(p.xz + vec2(0.0, e)) - hang(p.xz - vec2(0.0, e));
    vec3 n = top ? vec3(0.0, 1.0, 0.0) : normalize(vec3(-hx / (2.0 * e), -1.0, -hz / (2.0 * e)));
    float h = hang(p.xz) / (Mirror.w * 0.9);
    float sea = smoothstep(0.06, 0.01, h);
    float moss = smoothstep(0.52, 0.62, f2(u * 22.0 + 9.0, 4)) * (1.0 - sea);
    vec3 rock = mix(vec3(0.12, 0.05, 0.16), vec3(0.28, 0.14, 0.3), f2(u * 60.0, 4));
    vec3 col = mix(rock, vec3(0.04, 0.24, 0.24), moss);
    vec3 seaCol = vec3(0.36, 0.03, 0.08) + vec3(0.25, 0.05, 0.1) * pow(1.0 - abs(dot(n, rd)), 3.0);
    col = mix(col, seaCol, sea);
    // its light: a dim crimson glow from all round, brighter on the faces turned toward us, and the lightning
    float rim = pow(1.0 - abs(dot(n, -rd)), 2.0);
    float diff = 0.35 + 0.65 * max(0.0, dot(n, normalize(vec3(0.4, -0.7, 0.3))));
    col *= diff * (0.7 + 1.4 * Lightning);
    col += vec3(0.5, 0.08, 0.2) * rim * 0.6;
    // fissures of light
    float fis = pow(1.0 - abs(2.0 * f2(u * 30.0 + 2.0, 5) - 1.0), 14.0) * (1.0 - sea);
    float pulse = 0.75 + 0.25 * sin(Time * 2.0 + u.x * 20.0);
    col += vec3(1.0, 0.25, 0.65) * fis * 2.2 * pulse;
    if (top) {
        col = vec3(0.05, 0.02, 0.06) + vec3(0.6, 0.1, 0.3) * fis;
    }
    // air between us and it
    float fog = 1.0 - exp(-dist / 900.0);
    col = mix(col, vec3(0.4, 0.08, 0.16), fog * 0.7);
    return col;
}

// ------------------------------------------------------------------------------------------------

void main() {
    vec2 st = uv;
    float depth = texture(Depth, st).r;
    vec3 farP = worldAt(st, 1.0);
    vec3 rd = normalize(farP);
    bool sky = depth >= 0.99999;
    vec3 P = worldAt(st, depth);
    float tScene = sky ? 1e6 : length(P);

    // --- bending the air: heat over the lance, the shock dome
    vec2 off = vec2(0.0);
    if (Beam.w > 0.0) {
        vec3 c = toVertical(rd, Beam.xz, tScene);
        float near = exp(-c.x / (Beam.w * 3.0)) * step(BeamP.y - 2.0, c.z) * BeamP.z;
        vec2 wob = vec2(n2(vec2(c.z * 0.3, Time * 6.0)) - 0.5, n2(vec2(c.z * 0.3 + 9.0, Time * 6.0)) - 0.5);
        off += wob * near * 0.012;
    }
    if (Shock.w > 0.0) {
        float tc = dot(Shock.xyz, rd);
        if (tc > 0.0 && tc < tScene + Shock.w) {
            float dc = length(rd * tc - Shock.xyz);
            float band = exp(-sqr((dc - Shock.w) / max(ShockP.x, 0.5)));
            vec2 cs = toScreen(Shock.xyz);
            off += normalize(st - cs + 1e-5) * band * ShockP.y * 0.02;
        }
    }
    vec3 col = texture(Scene, clamp(st + off, 0.001, 0.999)).rgb;

    // --- Antipode: the sky and the light turn
    if (Crimson > 0.0) {
        if (sky) {
            float up = clamp(rd.y, 0.0, 1.0);
            vec3 skyCol = mix(vec3(0.55, 0.12, 0.22), vec3(0.18, 0.02, 0.08), pow(up, 0.6));
            col = mix(col, skyCol, Crimson * 0.72);
        } else {
            col *= mix(vec3(1.0), vec3(1.0, 0.68, 0.78), Crimson * 0.55);
        }
    }
    if (Mirror.w > 0.0 && Cracks.y > 0.0 && rd.y > 0.0) {
        // the web of cracks across the sky over the mark
        float h = Mirror.y + Cracks.z;
        float t = h / rd.y;
        if (t > 0.0 && t < tScene) {
            vec3 p = rd * t;
            vec2 l = (p.xz - Mirror.xz);
            float lr = length(l);
            float reach = Mirror.w * 7.0 * Cracks.x;
            float m = smoothstep(reach, reach * 0.6, lr);
            // warp the plane so the cracks run in jagged, uneven lines, and let only some of the edges split
            vec2 w = l * 0.022 + (vec2(f2(l * 0.01 + Cracks.w, 4), f2(l * 0.01 + Cracks.w + 7.0, 4)) - 0.5) * 2.2;
            float e = voronoiEdge(w, Cracks.w);
            float e2 = voronoiEdge(w * 3.1 + 3.0, Cracks.w + 5.0);
            float keep = smoothstep(0.35, 0.6, f2(l * 0.015 + Cracks.w * 3.0, 3) + 0.35 * exp(-lr / (Mirror.w * 3.0)));
            float width = 0.012 + 0.03 * f2(l * 0.03, 3);
            float crack = exp(-sqr(e / width)) * keep + 0.45 * exp(-sqr(e2 / 0.025)) * keep * smoothstep(reach * 0.8, reach * 0.3, lr);
            float centre = exp(-lr / (Mirror.w * 1.2));
            vec3 g = vec3(1.0, 0.35, 0.7) * crack * 1.6 + vec3(1.0, 0.85, 0.95) * pow(crack, 4.0) * 1.5 + vec3(0.8, 0.1, 0.4) * centre * 0.8;
            col += g * m * Cracks.y;
        }
    }
    float landT = -1.0;
    if (Mirror.w > 0.0 && MirrorP.y > 0.0) {
        vec3 hit;
        bool top;
        landT = marchLand(rd, tScene, hit, top);
        if (landT > 0.0) {
            float u2 = dot(hit.xz - Mirror.xz, hit.xz - Mirror.xz) / sqr(Mirror.w * MirrorP.w);
            float edge = smoothstep(1.0, 0.75, u2);
            col = mix(col, shadeLand(hit, rd, top, landT), MirrorP.y * edge);
        }
    }
    if (Mirror.w > 0.0 && Lightning > 0.0) {
        // bolts between the hanging land and ours, a few at a time, each living a fraction of a second
        float bucket = floor(Time * 7.0);
        for (int i = 0; i < 4; i++) {
            float s = bucket * 13.0 + float(i) * 7.0 + Cracks.w;
            if (h2(vec2(s, 1.0)) < 0.45) continue;
            float ang = h2(vec2(s, 2.0)) * TAU, rr = sqrt(h2(vec2(s, 3.0))) * Mirror.w * 0.9;
            vec2 bxz = Mirror.xz + vec2(cos(ang), sin(ang)) * rr;
            float topY = Mirror.y + MirrorP.x - hang(bxz);
            vec3 c = toVertical(rd, bxz, tScene);
            float y = c.z;
            if (y < Mirror.y - 2.0 || y > topY + 2.0) continue;
            // jag the line sideways as it falls
            vec2 jag = (vec2(f2(vec2(y * 0.08, s), 3), f2(vec2(y * 0.08, s + 4.0), 3)) - 0.5) * Mirror.w * 0.35;
            vec3 c2 = toVertical(rd, bxz + jag, tScene);
            float d = c2.x;
            float flick = h2(vec2(floor(Time * 30.0), s));
            col += (vec3(1.0, 0.92, 1.0) * exp(-sqr(d / 0.5)) * 2.5 + vec3(0.8, 0.3, 1.0) * exp(-d / 4.0) * 0.5) * Lightning * (0.6 + 0.4 * flick);
        }
    }
    if (Mirror.w > 0.0 && MirrorP.z > 0.0) {
        col = mix(col, vec3(1.0, 0.9, 0.97), MirrorP.z);
    }

    // --- Helios
    if (Beam.w > 0.0 && BeamP.z > 0.0) {
        float r = Beam.w;
        float I = BeamP.z;
        // the land lit by the lance
        if (!sky) {
            vec3 n = normalize(cross(dFdx(P), dFdy(P)));
            if (dot(n, -rd) < 0.0) n = -n;
            vec3 axis = vec3(Beam.x, clamp(P.y, BeamP.y, BeamP.x), Beam.z);
            vec3 L = axis - P;
            float d = length(L);
            float lit = max(0.0, dot(n, L / max(d, 1e-3))) * 0.8 + 0.2;
            float fall = min(1.6, (r * r * 5.0) / (d * d + r * r * 2.0));
            col += col * vec3(1.3, 0.95, 0.5) * fall * lit * I + vec3(1.0, 0.7, 0.3) * fall * 0.03 * I;
            // the ground it stands on runs molten
            float dh = length(P.xz - Beam.xz);
            if (BeamP.w > 0.0 && abs(P.y - Beam.y) < r * 2.5) {
                float m = smoothstep(r * 2.2, r * 0.7, dh) * BeamP.w;
                float flow = f2(P.xz * 0.35 + vec2(Time * 0.6, -Time * 0.4), 5);
                float veins = pow(1.0 - abs(2.0 * f2(P.xz * 0.6 - Time * 0.3, 4) - 1.0), 6.0);
                vec3 magma = mix(vec3(0.9, 0.18, 0.02), vec3(1.0, 0.75, 0.3), flow) + vec3(1.0, 0.95, 0.8) * veins * 0.8;
                col = mix(col, magma * 1.4, m * (0.55 + 0.45 * flow));
            }
        }
        // the lance itself: the air glowing round a white core, its sheath streaming down
        vec3 c = toVertical(rd, Beam.xz, tScene);
        float y = c.z;
        float inside = smoothstep(BeamP.y - r, BeamP.y + r * 0.5, y) * smoothstep(BeamP.x, BeamP.x - 60.0, y);
        float ang = atan(rd.x * c.y - Beam.x, rd.z * c.y - Beam.z);
        float stream = f2(vec2(ang * 2.0, y * 0.04 + Time * 9.0), 4);
        float spiral = 0.5 + 0.5 * sin(ang * 3.0 + y * 0.12 + Time * 24.0);
        float core = exp(-sqr(c.x / (r * 0.42)));
        float sheath = exp(-sqr(c.x / (r * 1.05))) * (0.55 + 0.45 * stream) * (0.8 + 0.2 * spiral);
        float halo = exp(-c.x / (r * 3.0));
        float scatter = exp(-c.x / (r * 16.0));
        vec3 beam = vec3(1.0, 0.98, 0.92) * core * 3.0 + vec3(1.0, 0.78, 0.38) * sheath * 1.2 + vec3(1.0, 0.62, 0.25) * halo * 0.25
                + vec3(1.0, 0.7, 0.4) * scatter * 0.05;
        // where it meets the ground: a sphere of white fire
        vec3 g = vec3(Beam.x, Beam.y, Beam.z);
        float tg = clamp(dot(g, rd), 0.0, tScene);
        float dg = length(rd * tg - g);
        float ground = step(BeamP.y, Beam.y + 1.0);
        beam += (vec3(1.0, 0.95, 0.85) * exp(-sqr(dg / (r * 0.9))) * 1.2 + vec3(1.0, 0.6, 0.2) * exp(-dg / (r * 2.0)) * 0.2) * ground;
        col += beam * inside * I;
    }

    // --- the shockwave's band across the ground
    if (Shock.w > 0.0 && !sky) {
        float dh = length(P.xz - Shock.xz);
        float band = exp(-sqr((dh - Shock.w) / max(ShockP.x, 0.5)));
        float lowEnough = smoothstep(Shock.w * 0.6, 0.0, abs(P.y - Shock.y));
        col += ShockC * band * lowEnough * ShockP.y * 1.5;
    }

    fragColor = vec4(col, 1.0);
}
