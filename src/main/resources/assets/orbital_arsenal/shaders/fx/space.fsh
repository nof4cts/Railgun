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

float hexd(vec2 p) {
    p = abs(p);
    return max(dot(p, vec2(0.8660254, 0.5)), p.y);
}

vec4 helios(vec3 ro, vec3 rd, out vec3 glow) {
    glow = vec3(0.0);
    vec3 A = normalize(ArrayAxis);
    vec3 U = normalize(cross(A, abs(A.y) < 0.9 ? vec3(0, 1, 0) : vec3(1, 0, 0)));
    vec3 V = cross(A, U);
    float best = 1e9;
    vec3 col = vec3(0.0);
    float rr = mix(0.55, 2.3, Unfold);
    float pr = 0.66;
    for (int i = 0; i < 12; i++) {
        float ang = float(i) * TAU / 12.0;
        vec3 radial = cos(ang) * U + sin(ang) * V;
        vec3 c = radial * rr - A * (1.0 - Unfold) * 0.6;
        vec3 tangent = cross(A, radial);
        vec3 toLens = normalize(-c + A * 0.001);
        vec3 openN = normalize(SunDir + toLens);
        // each mirror opens a moment after the one before it
        float u = smoothstep(float(i) / 16.0, float(i) / 16.0 + 0.3, Unfold);
        vec3 n = normalize(mix(tangent, openN, u));
        float dn = dot(rd, n);
        if (abs(dn) < 1e-4) continue;
        float t = dot(c - ro, n) / dn;
        if (t <= 0.0 || t >= best) continue;
        vec3 h = ro + rd * t - c;
        vec3 e1 = normalize(cross(n, radial) + 1e-4);
        vec3 e2 = cross(n, e1);
        vec2 l = vec2(dot(h, e1), dot(h, e2));
        float d = hexd(l);
        if (d > pr) continue;
        best = t;
        bool front = dn < 0.0;
        // facets: each a slightly different angle, so the sun glitters across the face
        vec2 cellId = floor(l * 7.0 + 0.5);
        vec3 jit = (vec3(hash31(vec3(cellId, float(i))), hash31(vec3(cellId, float(i) + 3.0)), hash31(vec3(cellId, float(i) + 7.0))) - 0.5) * 0.02;
        vec3 nf = normalize((front ? n : -n) + jit);
        if (front) {
            vec3 r = reflect(rd, nf);
            vec3 env = stars(r) + sun(r);
            float fres = 0.6 + 0.4 * pow(1.0 - abs(dot(rd, nf)), 3.0);
            vec3 m = vec3(0.9, 0.76, 0.48) * 0.1 + env * vec3(1.0, 0.9, 0.7) * fres;
            vec2 g = abs(fract(l * 7.0 + 0.5) - 0.5);
            m *= 0.82 + 0.18 * smoothstep(0.0, 0.05, min(g.x, g.y));
            col = m;
        } else {
            vec2 g = abs(fract(l * 9.0) - 0.5);
            col = mix(vec3(0.55, 0.57, 0.62), vec3(0.025, 0.035, 0.07), smoothstep(0.02, 0.06, min(g.x, g.y)));
            col *= 0.25 + 0.75 * max(dot(-n, SunDir), 0.0);
        }
        if (d > pr * 0.92) {
            float diff = max(dot(front ? n : -n, SunDir), 0.0);
            float spec = pow(max(dot(reflect(rd, front ? n : -n), SunDir), 0.0), 30.0);
            col = vec3(0.85, 0.6, 0.22) * (0.12 + diff) + vec3(1.0, 0.9, 0.7) * spec;
        }
    }
    // the hub ring and the struts
    for (int i = 0; i < 12; i++) {
        float ang = float(i) * TAU / 12.0;
        vec3 c = (cos(ang) * U + sin(ang) * V) * rr - A * (1.0 - Unfold) * 0.6;
        vec2 s = raySeg(ro, rd, vec3(0.0), c);
        if (s.x < 0.022 && s.y < best) {
            best = s.y;
            col = vec3(0.4, 0.36, 0.3) * (0.25 + max(dot(normalize(cross(rd, c)), SunDir), 0.0));
        }
        float ang2 = float(i + 1) * TAU / 12.0;
        vec3 a0 = (cos(ang) * U + sin(ang) * V) * 0.55, a1 = (cos(ang2) * U + sin(ang2) * V) * 0.55;
        vec2 s2 = raySeg(ro, rd, a0, a1);
        if (s2.x < 0.035 && s2.y < best) {
            best = s2.y;
            col = vec3(0.7, 0.55, 0.3) * (0.2 + max(dot(A * -1.0, SunDir), 0.0));
        }
        if (Charge > 0.0) {
            float run = fract(Time * 1.6 - float(i) * 0.07);
            vec3 pulse = mix(c, vec3(0.0), run);
            float dp = length(ro + rd * dot(pulse - ro, rd) - pulse);
            glow += vec3(1.0, 0.85, 0.5) * (exp(-s.x * s.x / 0.003) * 0.7 + exp(-dp * dp / 0.01) * 1.5) * Charge * step(s.y, best + 0.001);
        }
    }
    float tl = sphere(ro, rd, vec3(0.0), 0.36);
    if (tl > 0.0 && tl < best) {
        best = tl;
        vec3 n = normalize(ro + rd * tl);
        float fres = pow(1.0 - max(dot(n, -rd), 0.0), 2.0);
        vec3 inner = stars(refract(rd, n, 0.7)) * 0.6;
        float rings = 0.5 + 0.5 * sin(length(cross(n, A)) * 60.0 - Time * 8.0 * Charge);
        col = mix(vec3(0.06, 0.08, 0.14) + inner, vec3(1.0, 0.95, 0.82) * (1.6 + 0.9 * rings), Charge) + vec3(0.6, 0.7, 1.0) * fres * 0.7;
    }
    vec2 lc = raySeg(ro, rd, vec3(0.0), vec3(0.0));
    glow += vec3(1.0, 0.9, 0.6) * exp(-lc.x * 7.0) * Charge * Charge * 0.9;
    if (Lance > 0.0) {
        vec2 s = raySeg(ro, rd, vec3(0.0), A * Lance * 4000.0);
        float w = 0.16 * (0.9 + 0.1 * sin(Time * 40.0));
        float core = exp(-sqr(s.x / w));
        float sheath = exp(-sqr(s.x / (w * 3.0))) * (0.6 + 0.4 * vnoise3(vec3(s.y * 0.5 - Time * 30.0, 0.0, 0.0)));
        float wide = exp(-s.x / 1.4);
        glow += (vec3(1.0, 0.98, 0.92) * core * 10.0 + vec3(1.0, 0.8, 0.4) * sheath * 2.5 + vec3(1.0, 0.7, 0.3) * wide * 1.0) * step(s.y, best + 0.001);
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
