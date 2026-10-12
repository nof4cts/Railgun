#version 150
#include "common.glsl"
// Orbit, drawn whole by ray tracing for the space shots of the films.
//   sky:     stars of many sizes and tints, the galaxy's band of dust, the sun and its glare, lens flares;
//   Earth:   relief (bump-mapped from the terrain itself), biomes by latitude and rainfall, mountains and snow, seas
//            with glint, two layers of cloud that cast shadows, city lights at night, air that scatters blue by day and
//            amber at the terminator, and detail that keeps coming as the camera dives into the atmosphere;
//   Antipode: the other Earth (crimson seas, violet-black land, teal moss, burning fissures), the breach it comes
//            through (a torn ring round a vortex that bends the starlight), and lightning where the two skies touch;
//   Helios:  in its own frame of kilometres, twelve hexagonal mirrors of glittering facets that unfold round a lens,
//            the sunlight they send running in along the struts, and the lance, traced on down to the ground.

uniform vec3 CamR;
uniform vec3 CamU;
uniform vec3 CamF;
uniform float TanHalf;
uniform float Aspect;
uniform float Time;
uniform float Fade;

uniform vec3 EyeP;          // camera, in Earth radii
uniform vec3 SunDir;
uniform vec4 Earth2;        // centre, radius (radius 0: no second Earth)
uniform float Contact;
uniform vec4 Breach;        // centre, radius (0: none)
uniform vec3 BreachN;
uniform float BreachOpen;
uniform vec4 Mark;          // a point on the Earth's surface (unit vector) and how brightly it burns

uniform float Mode;         // 1: the Helios array is in view
uniform vec3 LocalEye;      // camera in the array's frame, kilometres
uniform vec3 ArrayAxis;
uniform float Unfold;
uniform float Charge;
uniform float Lance;        // the lance in the array's frame, 0..1 of its reach
uniform vec4 LanceA;        // the lance in Earth radii: from (xyz) ...
uniform vec4 LanceB;        // ... to (xyz), with intensity in LanceA.w

in vec2 uv;
out vec4 fragColor;

// ------------------------------------------------------------------------------------------------ noise

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash31(i), hash31(i + vec3(1, 0, 0)), u.x), mix(hash31(i + vec3(0, 1, 0)), hash31(i + vec3(1, 1, 0)), u.x), u.y);
    float b = mix(mix(hash31(i + vec3(0, 0, 1)), hash31(i + vec3(1, 0, 1)), u.x), mix(hash31(i + vec3(0, 1, 1)), hash31(i + vec3(1, 1, 1)), u.x), u.y);
    return mix(a, b, u.z);
}

float fbmN(vec3 p, int oct) {
    float v = 0.0, a = 0.5, norm = 0.0;
    for (int i = 0; i < 12; i++) {
        if (i >= oct) break;
        v += a * vnoise3(p);
        norm += a;
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v / norm;
}

float fbm3(vec3 p) { return fbmN(p, 5); }

// ------------------------------------------------------------------------------------------------ geometry

float sphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - dot(oc, oc) + r * r;
    if (h < 0.0) return -1.0;
    h = sqrt(h);
    float t = -b - h;
    return t > 0.0 ? t : -1.0;
}

vec2 raySeg(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 ab = b - a;
    vec3 ao = ro - a;
    float abab = dot(ab, ab);
    float abrd = dot(ab, rd);
    float aoab = dot(ao, ab);
    float aord = dot(ao, rd);
    float den = abab - abrd * abrd;
    float s = den > 1e-9 ? clamp((aoab - aord * abrd) / den, 0.0, 1.0) : 0.0;
    float t = max(0.0, dot(a + ab * s - ro, rd));
    return vec2(length(ro + rd * t - (a + ab * s)), t);
}

vec3 rotY(vec3 v, float a) {
    float c = cos(a), s = sin(a);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

/** Tilt a normal by the screen-space slope of a height field (bump mapping without extra samples). */
vec3 bump(vec3 n, vec3 p, float h, float k) {
    vec3 dpx = dFdx(p), dpy = dFdy(p);
    float dhx = dFdx(h), dhy = dFdy(h);
    vec3 r1 = cross(dpy, n), r2 = cross(n, dpx);
    float det = dot(dpx, r1);
    if (abs(det) < 1e-12) return n;
    vec3 g = (r1 * dhx + r2 * dhy) / det;
    return normalize(n - g * k);
}

// ------------------------------------------------------------------------------------------------ sky

vec3 stars(vec3 d) {
    vec3 col = vec3(0.0);
    for (int k = 0; k < 3; k++) {
        float scale = k == 0 ? 90.0 : k == 1 ? 220.0 : 520.0;
        vec3 p = d * scale;
        vec3 cell = floor(p);
        float h = hash31(cell + float(k) * 17.0);
        float thresh = k == 2 ? 0.97 : 0.985;
        if (h > thresh) {
            vec3 centre = cell + 0.5 + (vec3(hash31(cell + 3.1), hash31(cell + 7.7), hash31(cell + 1.3)) - 0.5) * 0.6;
            float dist = length(p - centre);
            float b = (h - thresh) / (1.0 - thresh);
            float s = exp(-dist * dist * (k == 0 ? 18.0 : 40.0)) * (0.3 + 1.8 * b * b) * (k == 2 ? 0.5 : 1.0);
            vec3 tint = mix(vec3(0.65, 0.78, 1.0), vec3(1.0, 0.82, 0.6), hash31(cell + 5.5));
            float tw = 0.85 + 0.15 * sin(Time * (2.0 + h * 5.0) + h * 40.0);
            col += tint * s * tw;
        }
    }
    vec3 g = normalize(vec3(0.32, 0.82, 0.47));
    float band = exp(-sqr(dot(d, g)) * 16.0);
    float dust = fbm3(d * 5.0);
    float lanes = smoothstep(0.45, 0.62, fbm3(d * 9.0 + 2.0));
    col += band * (vec3(0.17, 0.14, 0.24) * dust * (1.0 - 0.7 * lanes) + vec3(0.05, 0.06, 0.1)) * 0.9;
    col += band * smoothstep(0.62, 0.82, fbm3(d * 14.0 + 4.0)) * vec3(0.3, 0.18, 0.28) * 0.5;
    col += vec3(0.04, 0.02, 0.06) * smoothstep(0.55, 0.8, fbm3(d * 2.2 + 9.0));
    return col;
}

vec3 sun(vec3 d) {
    float sd = dot(d, SunDir);
    vec3 col = vec3(1.0, 0.97, 0.9) * smoothstep(0.99975, 0.99988, sd) * 30.0;
    col += vec3(1.0, 0.85, 0.6) * (pow(max(sd, 0.0), 1200.0) * 8.0 + pow(max(sd, 0.0), 60.0) * 0.3 + pow(max(sd, 0.0), 6.0) * 0.04);
    return col;
}

/** Inside the top of the atmosphere the black thins to a deep blue toward the horizon. */
vec3 highSky(vec3 d, float inAir) {
    vec3 up = normalize(EyeP);
    float e = dot(d, up);
    vec3 blue = mix(vec3(0.12, 0.3, 0.7), vec3(0.01, 0.02, 0.06), smoothstep(-0.1, 0.5, e));
    return blue * inAir;
}

// ------------------------------------------------------------------------------------------------ planets

vec3 earth(vec3 p, vec3 n, vec3 rd, float dist) {
    vec3 q = rotY(n, Time * 0.002);
    int oct = int(clamp(6.0 + log2(0.35 / max(dist, 1e-5)), 6.0, 12.0));
    float h = fbmN(q * 2.4 + 7.1, oct);
    float cont = smoothstep(0.505, 0.525, h);
    float lat = abs(q.y);
    float wet = fbm3(q * 3.3 + 2.0);
    // land: desert where dry and near the tropics, forest where wet, grass between, tundra and ice toward the poles
    vec3 desert = vec3(0.62, 0.5, 0.33), forest = vec3(0.07, 0.17, 0.06), grass = vec3(0.22, 0.3, 0.11), tundra = vec3(0.42, 0.4, 0.34);
    vec3 ground = mix(desert, grass, smoothstep(0.42, 0.55, wet));
    ground = mix(ground, forest, smoothstep(0.55, 0.65, wet));
    ground = mix(ground, tundra, smoothstep(0.55, 0.72, lat));
    float mount = smoothstep(0.6, 0.75, h);
    ground = mix(ground, vec3(0.36, 0.32, 0.28), mount);
    ground = mix(ground, vec3(0.92, 0.94, 0.97), smoothstep(0.73, 0.8, h) + smoothstep(0.8, 0.88, lat));
    vec3 sea = mix(vec3(0.01, 0.04, 0.13), vec3(0.02, 0.13, 0.28), smoothstep(0.5, 0.42, h));
    sea = mix(sea, vec3(0.05, 0.3, 0.38), smoothstep(0.49, 0.505, h) * 0.6);
    vec3 surf = mix(sea, ground, cont);
    vec3 nb = bump(n, p, h * cont, 0.05);
    float diff = max(dot(nb, SunDir), 0.0);
    float day = smoothstep(-0.12, 0.18, dot(n, SunDir));
    // clouds, two layers, and their shadows on the ground
    vec3 drift = vec3(Time * 0.0018, 0.0, Time * 0.0006);
    float c1 = smoothstep(0.5, 0.72, fbmN(q * 4.6 + drift, oct - 1));
    float c2 = smoothstep(0.58, 0.8, fbmN(q * 11.0 - drift * 1.5, oct - 2)) * 0.6;
    float cl = clamp(c1 + c2, 0.0, 1.0);
    float shadow = smoothstep(0.5, 0.72, fbmN((q + SunDir * 0.006) * 4.6 + drift, 4));
    vec3 col = surf * (diff * 1.3 * (1.0 - 0.55 * shadow) + 0.012);
    float fres = pow(1.0 - max(dot(n, -rd), 0.0), 5.0);
    col += vec3(1.0, 0.88, 0.7) * pow(max(dot(reflect(rd, n), SunDir), 0.0), 90.0) * (1.0 - cont) * (1.0 - cl) * diff * 2.0;
    col += vec3(0.4, 0.55, 0.8) * fres * (1.0 - cont) * day * 0.3;
    float cdiff = max(dot(n, SunDir), 0.0);
    col = mix(col, vec3(1.0, 0.98, 0.96) * (cdiff * 1.2 + 0.02), cl * 0.92);
    // cities in the dark
    float city = step(0.9, hash31(floor(q * 380.0))) * smoothstep(0.5, 0.6, fbm3(q * 12.0)) * cont * (1.0 - cl);
    col += vec3(1.0, 0.7, 0.35) * city * (1.0 - day) * 0.8;
    // the air: blue haze on the day side, amber at the edge of night
    float edge = 1.0 - abs(dot(n, SunDir));
    vec3 scatter = mix(vec3(0.25, 0.5, 1.0), vec3(1.0, 0.5, 0.2), smoothstep(0.75, 1.0, edge) * smoothstep(-0.25, 0.1, dot(n, SunDir)));
    float haze = pow(1.0 - max(dot(n, -rd), 0.0), 2.5);
    col = mix(col, scatter * (0.05 + day), haze * 0.75);
    if (Mark.w > 0.0) {
        // the point of fire, a few kilometres across, with a glow round it a few tens
        float km = acos(clamp(dot(n, Mark.xyz), -1.0, 1.0)) * 6371.0;
        col += vec3(1.0, 0.82, 0.5) * Mark.w * (exp(-km / 1.5) * 8.0 + exp(-km / 12.0) * 1.0 + exp(-km / 90.0) * 0.12);
    }
    return col;
}

vec3 otherEarth(vec3 p, vec3 n, vec3 rd, float dist) {
    vec3 q = rotY(n * vec3(-1.0, -1.0, 1.0), -Time * 0.002);
    int oct = int(clamp(6.0 + log2(0.35 / max(dist, 1e-5)), 6.0, 11.0));
    float h = fbmN(q * 2.4 + 7.1, oct);
    float cont = smoothstep(0.505, 0.525, h);
    vec3 sea = mix(vec3(0.2, 0.0, 0.03), vec3(0.42, 0.03, 0.08), smoothstep(0.5, 0.42, h));
    float moss = smoothstep(0.5, 0.62, fbm3(q * 6.0 + 3.0));
    vec3 ground = mix(vec3(0.06, 0.02, 0.09), vec3(0.2, 0.07, 0.24), smoothstep(0.5, 0.75, fbmN(q * 8.0, oct - 2)));
    ground = mix(ground, vec3(0.03, 0.2, 0.2), moss * 0.7);
    vec3 surf = mix(sea, ground, cont);
    vec3 nb = bump(n, p, h * cont, 0.06);
    float diff = max(dot(nb, SunDir), 0.0);
    vec3 col = surf * (diff * 1.3 + 0.035);
    float ridge = 1.0 - abs(2.0 * fbmN(q * 7.0 + 2.0, oct - 1) - 1.0);
    col += vec3(1.0, 0.18, 0.55) * pow(ridge, 16.0) * cont * 2.6;
    float cl = smoothstep(0.55, 0.75, fbmN(q * 4.6 - vec3(Time * 0.003, 0.0, 0.0), oct - 1));
    col = mix(col, vec3(0.95, 0.5, 0.3) * (diff + 0.06), cl * 0.55);
    float haze = pow(1.0 - max(dot(n, -rd), 0.0), 2.5);
    col = mix(col, vec3(1.0, 0.18, 0.4) * (0.15 + diff), haze * 0.7);
    return col;
}

vec3 halo(vec3 ro, vec3 rd, vec3 c, float r, vec3 tint, float tMax) {
    vec3 oc = c - ro;
    float t = dot(oc, rd);
    if (t < 0.0 || t > tMax) return vec3(0.0);
    vec3 p = ro + rd * t;
    float b = length(p - c) / r;
    if (b < 1.0) return vec3(0.0);
    float lit = smoothstep(-0.35, 0.4, dot(normalize(p - c), SunDir));
    float term = smoothstep(0.35, -0.05, abs(dot(normalize(p - c), SunDir)));
    vec3 c2 = mix(tint, vec3(1.0, 0.5, 0.25), term * 0.8);
    return c2 * (exp(-(b - 1.0) / 0.01) * 1.1 + exp(-(b - 1.0) / 0.04) * 0.25) * (0.08 + lit);
}

// ------------------------------------------------------------------------------------------------ Helios

/** What a mirror sees: the Earth below it (traced from the array's place in orbit), else the sky and the sun. */
vec3 envAt(vec3 from, vec3 d) {
    float t = sphere(from, d, vec3(0.0), 1.0);
    if (t > 0.0) {
        vec3 p = from + d * t;
        vec3 n = normalize(p);
        vec3 q = rotY(n, Time * 0.002);
        float h = fbmN(q * 2.4 + 7.1, 6);
        float cont = smoothstep(0.505, 0.525, h);
        vec3 surf = mix(vec3(0.02, 0.08, 0.2), vec3(0.25, 0.3, 0.16), cont);
        float cl = smoothstep(0.5, 0.72, fbmN(q * 4.6 + vec3(Time * 0.0018, 0.0, 0.0), 5));
        surf = mix(surf, vec3(1.0), cl * 0.9);
        float diff = max(dot(n, SunDir), 0.0);
        float rim = pow(1.0 - max(dot(n, -d), 0.0), 3.0);
        return surf * (diff * 1.2 + 0.02) + vec3(0.3, 0.55, 1.0) * rim * 0.5;
    }
    return stars(d) + sun(d) + halo(from, d, vec3(0.0), 1.0, vec3(0.3, 0.55, 1.0), 1e9);
}

/** Signed distance to the hub: a machined body along the axis, a ring round it, the lens at its nose. */
float hubSdf(vec3 p, vec3 A, out int part) {
    float along = dot(p, A);
    vec3 radial = p - A * along;
    float r = length(radial);
    // body: a rounded cylinder from -0.55 to 0.35 along the axis
    float body = max(r - 0.2, abs(along + 0.1) - 0.45) - 0.03;
    // a collar where the struts meet
    float collar = max(r - 0.3, abs(along + 0.05) - 0.06) - 0.02;
    // a ring round it, out where the struts begin
    vec2 tq = vec2(r - 0.62, along + 0.05);
    float ring = length(tq) - 0.045;
    // the lens at the nose, facing the Earth
    float lens = length(p - A * 0.42) - 0.17;
    float d = min(min(body, collar), ring);
    part = d == ring ? 2 : 1;
    if (lens < d) {
        d = lens;
        part = 3;
    }
    return d;
}

vec4 helios(vec3 ro, vec3 rd, out vec3 glow) {
    glow = vec3(0.0);
    vec3 A = normalize(ArrayAxis);
    vec3 U = normalize(cross(A, abs(A.y) < 0.9 ? vec3(0, 1, 0) : vec3(1, 0, 0)));
    vec3 V = cross(A, U);
    vec3 arrayPos = EyeP - LocalEye / 6371.0;
    vec3 earthshine = vec3(0.12, 0.22, 0.4);
    float best = 1e9;
    vec3 col = vec3(0.0);
    float rr = mix(0.6, 2.35, Unfold);
    // petals: long leaves, wide in the middle, pointed at both ends
    float plen = 1.05, pwid = 0.5;
    for (int i = 0; i < 12; i++) {
        float ang = float(i) * TAU / 12.0;
        vec3 radial = cos(ang) * U + sin(ang) * V;
        vec3 c = radial * rr - A * (1.0 - Unfold) * 0.65;
        vec3 tangent = cross(A, radial);
        vec3 toLens = normalize(-c + A * 0.001);
        vec3 openN = normalize(SunDir + toLens);
        float u = smoothstep(float(i) / 16.0, float(i) / 16.0 + 0.3, Unfold);
        vec3 n = normalize(mix(tangent, openN, u));
        float dn = dot(rd, n);
        if (abs(dn) < 1e-5) continue;
        float t = dot(c - ro, n) / dn;
        if (t <= 0.0 || t >= best) continue;
        vec3 h = ro + rd * t - c;
        // the petal's own axes: along the radial (in its plane) and across
        vec3 e1 = normalize(radial - n * dot(radial, n));
        vec3 e2 = cross(n, e1);
        vec2 l = vec2(dot(h, e1) / plen, dot(h, e2) / pwid);
        // leaf outline: narrows toward both tips
        float halfw = sqrt(max(0.0, 1.0 - l.x * l.x)) * (1.0 - 0.18 * l.x * l.x);
        float edge = abs(l.y) / max(halfw, 1e-4);
        if (abs(l.x) > 1.0 || edge > 1.0) continue;
        best = t;
        bool front = dn < 0.0;
        // concave: the surface tips toward the lens away from the centre line, rippled very slightly
        vec3 nn = front ? n : -n;
        vec3 bend = (e1 * l.x * plen + e2 * l.y * pwid) * 0.09;
        vec3 ripple = (vec3(vnoise3(h * 26.0 + float(i)), vnoise3(h * 26.0 + 7.0 + float(i)), vnoise3(h * 26.0 + 13.0)) - 0.5) * 0.012;
        vec3 ns = normalize(nn - (front ? bend : -bend) + ripple);
        float rim = smoothstep(0.86, 0.97, edge) + smoothstep(0.9, 0.99, abs(l.x));
        if (rim > 0.5) {
            // the frame: a titanium tube round the petal, round in section
            float k = clamp((edge - 0.86) / 0.11, 0.0, 1.0);
            vec3 tubeN = normalize(nn * cos(k * 1.4) + e2 * sign(l.y) * sin(k * 1.4));
            float diff = max(dot(tubeN, SunDir), 0.0);
            float spec = pow(max(dot(reflect(rd, tubeN), SunDir), 0.0), 60.0);
            col = vec3(0.62, 0.6, 0.58) * (diff * 0.9 + 0.05) + earthshine * max(dot(tubeN, -A), 0.0) * 0.4 + vec3(1.0, 0.95, 0.9) * spec * 2.0;
            continue;
        }
        if (front) {
            vec3 r = reflect(rd, ns);
            vec3 env = envAt(arrayPos + ro / 6371.0, r);
            float fres = 0.75 + 0.25 * pow(1.0 - abs(dot(rd, ns)), 4.0);
            vec3 m = env * vec3(0.98, 0.95, 0.88) * fres;
            // seams of the membrane: fine radial lines and a few rings
            float seam = smoothstep(0.012, 0.0, abs(fract(l.y * halfw * 5.0 + 0.5) - 0.5) * 0.2) * 0.35
                    + smoothstep(0.02, 0.0, abs(fract((l.x + 1.0) * 3.0) - 0.5) * 0.12) * 0.25;
            m *= 1.0 - seam;
            col = m + vec3(0.04, 0.035, 0.03);
        } else {
            // the back: carbon panels and silver ribs, lit by the sun and the Earth
            float rib = smoothstep(0.03, 0.0, abs(l.y)) + smoothstep(0.015, 0.0, abs(fract((l.x + 1.0) * 4.0) - 0.5) * 0.25);
            vec3 base = mix(vec3(0.03, 0.035, 0.05), vec3(0.6, 0.62, 0.66), clamp(rib, 0.0, 1.0));
            float diff = max(dot(ns, SunDir), 0.0);
            col = base * (diff + 0.08) + earthshine * max(dot(ns, -A), 0.0) * 0.5;
        }
    }
    // struts, as round tubes, from the hub to each petal
    for (int i = 0; i < 12; i++) {
        float ang = float(i) * TAU / 12.0;
        vec3 c = (cos(ang) * U + sin(ang) * V) * rr - A * (1.0 - Unfold) * 0.65;
        vec3 a0 = (cos(ang) * U + sin(ang) * V) * 0.62;
        vec2 s = raySeg(ro, rd, a0, c * 0.6);
        float rad = 0.018;
        if (s.x < rad && s.y < best) {
            best = s.y;
            vec3 p = ro + rd * s.y;
            vec3 axis = normalize(c - a0);
            vec3 onAxis = a0 + axis * dot(p - a0, axis);
            vec3 tn = normalize(p - onAxis + rd * -0.001);
            float diff = max(dot(tn, SunDir), 0.0);
            float spec = pow(max(dot(reflect(rd, tn), SunDir), 0.0), 40.0);
            col = vec3(0.55, 0.55, 0.55) * (diff + 0.06) + earthshine * max(dot(tn, -A), 0.0) * 0.4 + vec3(1.0) * spec;
        }
        if (Charge > 0.0) {
            vec2 sl = raySeg(ro, rd, c * 0.6, vec3(0.0));
            float run = fract(Time * 1.6 - float(i) * 0.07);
            vec3 pulse = mix(c, vec3(0.0), run);
            float dp = length(ro + rd * dot(pulse - ro, rd) - pulse);
            glow += vec3(1.0, 0.86, 0.55) * (exp(-sl.x * sl.x / 0.0012) * 0.6 + exp(-dp * dp / 0.006) * 1.2) * Charge * step(sl.y, best + 0.001);
        }
    }
    // the hub, ray-marched inside its bounding sphere
    float tb = sphere(ro, rd, vec3(0.0), 0.8);
    if (tb >= 0.0 && tb < best) {
        float t = max(tb, 0.0);
        int part = 0;
        for (int k = 0; k < 48; k++) {
            vec3 p = ro + rd * t;
            float d = hubSdf(p, A, part);
            if (d < 0.002) {
                vec2 e = vec2(0.002, 0.0);
                int pp;
                vec3 n = normalize(vec3(hubSdf(p + e.xyy, A, pp) - hubSdf(p - e.xyy, A, pp), hubSdf(p + e.yxy, A, pp) - hubSdf(p - e.yxy, A, pp),
                        hubSdf(p + e.yyx, A, pp) - hubSdf(p - e.yyx, A, pp)));
                float diff = max(dot(n, SunDir), 0.0);
                float spec = pow(max(dot(reflect(rd, n), SunDir), 0.0), 50.0);
                if (part == 3) {
                    float fres = pow(1.0 - max(dot(n, -rd), 0.0), 2.0);
                    vec3 inner = stars(refract(rd, n, 0.7)) * 0.5;
                    float rings = 0.5 + 0.5 * sin(length(cross(p, A)) * 90.0 - Time * 8.0 * Charge);
                    col = mix(vec3(0.05, 0.07, 0.12) + inner, vec3(1.0, 0.95, 0.82) * (1.4 + 0.8 * rings), Charge) + vec3(0.6, 0.7, 1.0) * fres * 0.6;
                } else if (part == 2) {
                    col = vec3(0.85, 0.62, 0.25) * (diff + 0.08) + vec3(1.0, 0.9, 0.7) * spec * 1.5 + earthshine * max(dot(n, -A), 0.0) * 0.3;
                } else {
                    float panel = smoothstep(0.004, 0.0, abs(fract(dot(p, A) * 14.0) - 0.5) * 0.07);
                    col = vec3(0.7, 0.72, 0.75) * (diff + 0.07) * (1.0 - panel * 0.5) + vec3(1.0) * spec + earthshine * max(dot(n, -A), 0.0) * 0.35;
                }
                best = t;
                break;
            }
            t += d;
            if (t > tb + 1.7 || t > best) break;
        }
    }
    vec2 lc = raySeg(ro, rd, A * 0.42, A * 0.42);
    glow += vec3(1.0, 0.9, 0.6) * exp(-lc.x * 9.0) * Charge * Charge * 0.8;
    if (Lance > 0.0) {
        vec2 s = raySeg(ro, rd, A * 0.45, A * (0.45 + Lance * 4000.0));
        float w = 0.13 * (0.92 + 0.08 * sin(Time * 40.0));
        float core = exp(-sqr(s.x / w));
        float sheath = exp(-sqr(s.x / (w * 3.0))) * (0.6 + 0.4 * vnoise3(vec3(s.y * 0.5 - Time * 30.0, 0.0, 0.0)));
        float wide = exp(-s.x / 1.4);
        glow += (vec3(1.0, 0.98, 0.92) * core * 9.0 + vec3(1.0, 0.8, 0.4) * sheath * 2.2 + vec3(1.0, 0.7, 0.3) * wide * 0.8) * step(s.y, best + 0.001);
    }
    return vec4(col, best < 1e8 ? 1.0 : 0.0);
}

// ------------------------------------------------------------------------------------------------ flares

vec2 screenOf(vec3 d) {
    float f = dot(d, CamF);
    return vec2(dot(d, CamR) / (f * TanHalf * Aspect), dot(d, CamU) / (f * TanHalf));
}

vec3 flare(vec2 ndc, vec3 d, vec3 tint, float strength) {
    if (strength <= 0.0 || dot(d, CamF) <= 0.05) return vec3(0.0);
    vec2 s = screenOf(d);
    if (abs(s.x) > 1.6 || abs(s.y) > 1.6) return vec3(0.0);
    vec3 c = vec3(0.0);
    vec2 a = vec2(ndc.x * Aspect, ndc.y), sa = vec2(s.x * Aspect, s.y);
    // anamorphic streak
    c += tint * exp(-abs(a.y - sa.y) * 90.0) * exp(-abs(a.x - sa.x) * 1.4) * 0.35;
    // ghosts along the line through the centre
    for (int i = 0; i < 5; i++) {
        float k = -0.35 - float(i) * 0.38;
        vec2 g = sa * k;
        float r = 0.03 + 0.05 * float(i % 3);
        float dist = length(a - g);
        float ring = exp(-sqr((dist - r) / 0.006)) * 0.3 + smoothstep(r, r * 0.6, dist) * 0.06;
        vec3 hue = mix(vec3(0.4, 0.7, 1.0), vec3(1.0, 0.6, 0.3), float(i) / 4.0);
        c += hue * ring;
    }
    return c * strength;
}

// ------------------------------------------------------------------------------------------------

void main() {
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 rd = normalize(CamF + ndc.x * Aspect * TanHalf * CamR + ndc.y * TanHalf * CamU);
    float alt = length(EyeP);
    float inAir = smoothstep(1.016, 1.003, alt);
    vec3 col = stars(rd) * (1.0 - inAir * 0.9) + sun(rd) + highSky(rd, inAir);

    float tBest = 1e9;
    float t1 = sphere(EyeP, rd, vec3(0.0), 1.0);
    float t2 = Earth2.w > 0.0 ? sphere(EyeP, rd, Earth2.xyz, Earth2.w) : -1.0;

    if (Breach.w > 0.0 && BreachOpen > 0.0) {
        float dn = dot(rd, BreachN);
        if (abs(dn) > 1e-4) {
            float tb = dot(Breach.xyz - EyeP, BreachN) / dn;
            if (tb > 0.0) {
                vec3 x = EyeP + rd * tb - Breach.xyz;
                float d = length(x) / Breach.w;
                vec3 e1 = normalize(cross(BreachN, vec3(0.0, 1.0, 0.0)));
                vec3 e2 = cross(BreachN, e1);
                float a = atan(dot(x, e2), dot(x, e1));
                if (d < 1.0 && (t1 < 0.0 || tb < t1)) {
                    // the vortex: the stars beyond, swirled, and bands of crimson light winding in
                    float swirl = a + log(max(d, 0.02)) * 2.2 - Time * 0.8;
                    vec3 bent = normalize(rd + (e1 * cos(swirl) + e2 * sin(swirl)) * 0.25 * (1.0 - d));
                    float bands = 0.5 + 0.5 * sin(swirl * 5.0 + fbm3(vec3(d * 4.0, a, Time * 0.2)) * 4.0);
                    vec3 v = stars(bent) * 1.6 + mix(vec3(0.12, 0.0, 0.06), vec3(0.8, 0.12, 0.4), bands * (1.0 - d)) * 0.6;
                    v += vec3(1.0, 0.7, 0.9) * exp(-d * 6.0) * 0.6;
                    col = mix(col, v, BreachOpen);
                }
                float ring = exp(-sqr((d - 1.0) / 0.03));
                float corona = exp(-abs(d - 1.0) / 0.12) * (0.6 + 0.4 * fbm3(vec3(a * 6.0, Time * 0.6, 0.0)));
                float cracks = pow(vnoise3(vec3(a * 11.0, Time * 0.25, 1.0)), 7.0) * exp(-max(d - 1.0, 0.0) * 2.2) * step(1.0, d);
                float shards = pow(vnoise3(vec3(a * 37.0, 3.0, 2.0)), 12.0) * exp(-max(d - 1.0, 0.0) * 4.0) * step(1.0, d);
                col += (vec3(1.0, 0.95, 0.98) * ring * 4.0 + vec3(1.0, 0.25, 0.45) * corona * 1.2 + vec3(1.0, 0.5, 0.75) * (cracks * 3.0 + shards * 5.0))
                        * BreachOpen;
            }
        }
    }

    if (t1 > 0.0) {
        tBest = t1;
        vec3 p = EyeP + rd * t1;
        col = earth(p, normalize(p), rd, t1);
    }
    if (t2 > 0.0 && t2 < tBest) {
        tBest = t2;
        vec3 p = EyeP + rd * t2;
        col = otherEarth(p, normalize(p - Earth2.xyz), rd, t2);
    }
    col += halo(EyeP, rd, vec3(0.0), 1.0, vec3(0.3, 0.55, 1.0), tBest);
    if (Earth2.w > 0.0) {
        col += halo(EyeP, rd, Earth2.xyz, Earth2.w, vec3(1.0, 0.2, 0.42), tBest);
        if (Contact > 0.0) {
            vec3 dirTo = normalize(Earth2.xyz);
            vec3 a = dirTo * 1.0;
            vec3 b = Earth2.xyz - dirTo * Earth2.w;
            vec3 mid = (a + b) * 0.5;
            vec3 side = normalize(cross(dirTo, vec3(0.0, 1.0, 0.0)));
            vec2 s = raySeg(EyeP, rd, mid - side * 0.6, mid + side * 0.6);
            col += vec3(1.0, 0.65, 0.9) * exp(-s.x / 0.012) * Contact * 1.5 * step(s.y, tBest + 0.02);
            float tm = max(0.0, dot(mid - EyeP, rd));
            vec3 p = EyeP + rd * tm;
            float r = length(p - mid);
            for (int i = 0; i < 2; i++) {
                float bolt = pow(1.0 - abs(2.0 * vnoise3((p - mid) * (30.0 + float(i) * 25.0) + vec3(0.0, Time * 7.0, float(i) * 5.0)) - 1.0), 22.0);
                col += vec3(0.95, 0.75, 1.0) * bolt * exp(-r / 0.15) * Contact * 3.0 * step(tm, tBest + 0.02);
            }
            col += vec3(1.0, 0.8, 0.95) * exp(-r / 0.05) * Contact * Contact * 1.2;
        }
    }
    if (LanceA.w > 0.0) {
        // the lance traced in Earth radii, from the array to the ground
        vec2 s = raySeg(EyeP, rd, LanceA.xyz, LanceB.xyz);
        float px = max(s.y * TanHalf * 0.004, 0.00008);
        float core = exp(-sqr(s.x / px));
        float glow = exp(-s.x / (px * 6.0));
        float vis = step(s.y, tBest + 0.0005);
        col += (vec3(1.0, 0.98, 0.9) * core * 6.0 + vec3(1.0, 0.75, 0.35) * glow * 0.8) * LanceA.w * vis;
        // where it enters the air, a ring of plasma
        vec3 dir = normalize(LanceB.xyz - LanceA.xyz);
        float tIn = sphere(LanceA.xyz, dir, vec3(0.0), 1.013);
        if (tIn > 0.0) {
            vec3 entry = LanceA.xyz + dir * tIn;
            float te = max(0.0, dot(entry - EyeP, rd));
            float de = length(EyeP + rd * te - entry);
            float pe = max(te * TanHalf * 0.02, 0.0006);
            col += vec3(1.0, 0.85, 0.6) * exp(-de / pe) * LanceA.w * 0.5 * step(te, tBest + 0.001);
        }
    }

    if (Mode > 0.5) {
        vec3 g;
        vec4 h = helios(LocalEye, rd, g);
        if (h.a > 0.0) {
            col = h.rgb;
        }
        col += g;
        col += flare(ndc, normalize(-LocalEye), vec3(1.0, 0.85, 0.55), Charge * 0.9);
    }
    // the sun's flare, unless a planet is in front of it
    bool sunHidden = sphere(EyeP, SunDir, vec3(0.0), 1.0) > 0.0 || (Earth2.w > 0.0 && sphere(EyeP, SunDir, Earth2.xyz, Earth2.w) > 0.0);
    if (!sunHidden) {
        col += flare(ndc, SunDir, vec3(1.0, 0.9, 0.75), 0.8);
    }

    col *= Fade;
    col = col / (1.0 + col * 0.3);
    col = pow(col, vec3(1.0 / 1.1));
    // a little grain against banding in the dark
    col += (hash12(uv * 1000.0 + fract(Time)) - 0.5) / 255.0 * 2.0;
    fragColor = vec4(col, 1.0);
}
