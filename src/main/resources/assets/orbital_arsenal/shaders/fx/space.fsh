#version 150
#include "common.glsl"
// Orbit, drawn whole by ray tracing for the space shots of the films:
//   - the sky: stars, a dust band of the galaxy, the sun with its glare;
//   - the Earth (radius 1, centre at the origin): continents, clouds that drift, night-side cities, sun glint on the
//     sea, and the thin blue skin of air round the limb;
//   - for Antipode, a second Earth of another reality (crimson seas, violet-black land split by glowing fissures), the
//     breach it comes through, and lightning where the two atmospheres touch;
//   - for Helios, in its own frame of kilometres near the camera: twelve hexagonal mirrors that unfold round a focusing
//     lens, the sunlight they gather, and the lance it sends down to the Earth.

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
uniform float Contact;      // lightning between the two
uniform vec4 Breach;        // centre, radius (0: none)
uniform vec3 BreachN;
uniform float BreachOpen;
uniform vec4 Mark;          // a point on the Earth's surface (unit vector) and how brightly it burns

uniform float Mode;         // 1: the Helios array is in view
uniform vec3 LocalEye;      // camera in the array's frame, kilometres
uniform vec3 ArrayAxis;     // from the array toward the Earth
uniform float Unfold;
uniform float Charge;
uniform float Lance;

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

float fbm3(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

// ------------------------------------------------------------------------------------------------ geometry

float sphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - dot(oc, oc) + r * r;
    if (h < 0.0) return -1.0;
    h = sqrt(h);
    float t = -b - h;
    return t > 0.0 ? t : (-b + h > 0.0 ? 0.0 : -1.0);
}

/** Distance between a ray and a segment, and how far along the ray the closest point is. */
vec2 raySeg(vec3 ro, vec3 rd, vec3 a, vec3 b) {
    vec3 ab = b - a;
    vec3 ao = ro - a;
    float abab = dot(ab, ab);
    float abrd = dot(ab, rd);
    float aoab = dot(ao, ab);
    float aord = dot(ao, rd);
    float den = abab - abrd * abrd;
    float s = den > 1e-6 ? clamp((aoab - aord * abrd) / den, 0.0, 1.0) : 0.0;
    float t = max(0.0, dot(a + ab * s - ro, rd));
    return vec2(length(ro + rd * t - (a + ab * s)), t);
}

vec3 rotY(vec3 v, float a) {
    float c = cos(a), s = sin(a);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

// ------------------------------------------------------------------------------------------------ sky

vec3 sky(vec3 d) {
    vec3 col = vec3(0.0);
    // stars: one chance per cell of a lattice round the camera, sizes and tints varied
    for (int k = 0; k < 2; k++) {
        float scale = k == 0 ? 140.0 : 320.0;
        vec3 p = d * scale;
        vec3 cell = floor(p);
        float h = hash31(cell + float(k) * 17.0);
        if (h > 0.985) {
            vec3 centre = cell + 0.5 + (vec3(hash31(cell + 3.1), hash31(cell + 7.7), hash31(cell + 1.3)) - 0.5) * 0.6;
            float dist = length(p - centre);
            float b = (h - 0.985) / 0.015;
            float s = exp(-dist * dist * (k == 0 ? 22.0 : 40.0)) * (0.4 + 1.6 * b * b);
            vec3 tint = mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.85, 0.65), hash31(cell + 5.5));
            col += tint * s;
        }
    }
    // the galaxy's band of dust and light
    vec3 g = normalize(vec3(0.32, 0.82, 0.47));
    float band = exp(-sqr(dot(d, g)) * 22.0);
    float dust = fbm3(d * 5.0);
    col += band * (vec3(0.16, 0.13, 0.22) * dust + vec3(0.05, 0.06, 0.1)) * 0.9;
    col += band * smoothstep(0.62, 0.8, fbm3(d * 11.0 + 4.0)) * vec3(0.18, 0.12, 0.2) * 0.6;
    // the sun
    float sd = dot(d, SunDir);
    col += vec3(1.0, 0.97, 0.9) * smoothstep(0.99975, 0.99988, sd) * 30.0;
    col += vec3(1.0, 0.85, 0.6) * (pow(max(sd, 0.0), 900.0) * 6.0 + pow(max(sd, 0.0), 40.0) * 0.25);
    return col;
}

// ------------------------------------------------------------------------------------------------ planets

vec3 earth(vec3 n, vec3 rd) {
    vec3 q = rotY(n, Time * 0.004);
    float land = fbm3(q * 2.4 + 7.1);
    float cont = smoothstep(0.515, 0.535, land);
    float lat = abs(q.y);
    vec3 sea = mix(vec3(0.01, 0.05, 0.16), vec3(0.02, 0.12, 0.28), smoothstep(0.5, 0.42, land));
    vec3 ground = mix(vec3(0.10, 0.17, 0.06), vec3(0.32, 0.25, 0.14), smoothstep(0.55, 0.7, fbm3(q * 6.0)));
    ground = mix(ground, vec3(0.42, 0.36, 0.24), smoothstep(0.35, 0.05, lat) * 0.5);
    vec3 surf = mix(sea, ground, cont);
    surf = mix(surf, vec3(0.85, 0.9, 0.95), smoothstep(0.8, 0.88, lat));
    float cl = smoothstep(0.5, 0.72, fbm3(q * 4.6 + vec3(Time * 0.0025, 0.0, 0.0)));
    float diff = max(dot(n, SunDir), 0.0);
    float day = smoothstep(-0.12, 0.15, dot(n, SunDir));
    vec3 col = surf * (diff * 1.25 + 0.015);
    col += vec3(1.0, 0.9, 0.7) * pow(max(dot(reflect(rd, n), SunDir), 0.0), 70.0) * (1.0 - cont) * diff * 1.6;
    col = mix(col, vec3(1.0) * (diff * 1.15 + 0.02), cl * 0.9);
    // cities on the night side
    float city = step(0.93, hash31(floor(q * 260.0))) * smoothstep(0.55, 0.62, fbm3(q * 9.0)) * cont * (1.0 - cl);
    col += vec3(1.0, 0.7, 0.35) * city * (1.0 - day) * 0.9;
    // the air at the limb
    float fres = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
    col += vec3(0.25, 0.5, 1.0) * fres * (0.08 + day * 0.9);
    // the mark: a point of fire where a strike is landing
    if (Mark.w > 0.0) {
        float m = dot(n, Mark.xyz);
        col += vec3(1.0, 0.8, 0.45) * Mark.w * (exp(-(1.0 - m) * 9000.0) * 6.0 + exp(-(1.0 - m) * 600.0) * 0.6);
    }
    return col;
}

vec3 otherEarth(vec3 n, vec3 rd, vec3 lightDir) {
    vec3 q = rotY(n * vec3(-1.0, -1.0, 1.0), -Time * 0.004);
    float land = fbm3(q * 2.4 + 7.1);
    float cont = smoothstep(0.515, 0.535, land);
    vec3 sea = mix(vec3(0.16, 0.0, 0.03), vec3(0.32, 0.02, 0.06), smoothstep(0.5, 0.42, land));
    vec3 ground = mix(vec3(0.05, 0.015, 0.08), vec3(0.16, 0.05, 0.2), smoothstep(0.5, 0.75, fbm3(q * 6.0)));
    vec3 surf = mix(sea, ground, cont);
    float diff = max(dot(n, lightDir), 0.0);
    vec3 col = surf * (diff * 1.25 + 0.03);
    // fissures of light across its land
    float ridge = 1.0 - abs(2.0 * fbm3(q * 7.0 + 2.0) - 1.0);
    col += vec3(1.0, 0.18, 0.55) * pow(ridge, 14.0) * cont * 2.2;
    float cl = smoothstep(0.55, 0.75, fbm3(q * 4.6 - vec3(Time * 0.003, 0.0, 0.0)));
    col = mix(col, vec3(0.9, 0.45, 0.25) * (diff + 0.05), cl * 0.6);
    float fres = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
    col += vec3(1.0, 0.15, 0.35) * fres * 0.8;
    return col;
}

vec3 halo(vec3 ro, vec3 rd, vec3 c, float r, vec3 tint, float tMax) {
    // the glow of air seen just past the limb
    vec3 oc = c - ro;
    float t = dot(oc, rd);
    if (t < 0.0 || t > tMax) return vec3(0.0);
    vec3 p = ro + rd * t;
    float b = length(p - c) / r;
    if (b < 1.0) return vec3(0.0);
    float lit = smoothstep(-0.3, 0.4, dot(normalize(p - c), SunDir));
    return tint * exp(-(b - 1.0) / 0.012) * (0.15 + lit);
}

// ------------------------------------------------------------------------------------------------ Helios

float hexd(vec2 p) {
    p = abs(p);
    return max(dot(p, vec2(0.8660254, 0.5)), p.y);
}

/** The array, in its own frame: returns colour, alpha (1 where a solid part covers the pixel) and additive light. */
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
        vec3 n = normalize(mix(tangent, openN, smoothstep(0.0, 1.0, Unfold)));
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
        if (front) {
            // the mirror: what it reflects (the sun, if it is aimed right), gilded
            vec3 r = reflect(rd, n);
            vec3 env = sky(r);
            vec3 m = vec3(0.9, 0.78, 0.5) * 0.12 + env * vec3(1.0, 0.92, 0.75);
            // seams between its facets
            vec2 g = abs(fract(l * 6.0) - 0.5);
            m *= 0.85 + 0.15 * smoothstep(0.0, 0.06, min(g.x, g.y));
            col = m;
        } else {
            // the back: dark cells and a silver lattice
            vec2 g = abs(fract(l * 9.0) - 0.5);
            col = mix(vec3(0.5, 0.52, 0.58), vec3(0.03, 0.04, 0.08), smoothstep(0.02, 0.06, min(g.x, g.y)));
            col *= 0.3 + 0.7 * max(dot(-n, SunDir), 0.0);
        }
        // a gold frame round every mirror
        if (d > pr * 0.92) {
            float diff = max(dot(front ? n : -n, SunDir), 0.0);
            col = vec3(0.85, 0.6, 0.22) * (0.15 + diff);
        }
    }
    // struts from the lens to each mirror
    for (int i = 0; i < 12; i++) {
        float ang = float(i) * TAU / 12.0;
        vec3 c = (cos(ang) * U + sin(ang) * V) * rr - A * (1.0 - Unfold) * 0.6;
        vec2 s = raySeg(ro, rd, vec3(0.0), c);
        if (s.x < 0.025 && s.y < best) {
            best = s.y;
            col = vec3(0.35, 0.3, 0.25) * (0.3 + max(dot(normalize(cross(rd, c)), SunDir), 0.0));
        }
        if (Charge > 0.0) {
            // the gathered light running in along it
            glow += vec3(1.0, 0.85, 0.5) * exp(-s.x * s.x / 0.0025) * Charge * 0.7 * step(s.y, best + 0.001);
        }
    }
    // the lens
    float tl = sphere(ro, rd, vec3(0.0), 0.36);
    if (tl > 0.0 && tl < best) {
        best = tl;
        vec3 n = normalize(ro + rd * tl);
        float fres = pow(1.0 - max(dot(n, -rd), 0.0), 2.0);
        col = mix(vec3(0.08, 0.1, 0.16), vec3(1.0, 0.95, 0.8) * 4.0, Charge) + vec3(0.6, 0.7, 1.0) * fres * 0.6;
    }
    vec2 lc = raySeg(ro, rd, vec3(0.0), vec3(0.0));
    glow += vec3(1.0, 0.9, 0.6) * exp(-lc.x * 2.2) * Charge * Charge * 1.4;
    // the lance
    if (Lance > 0.0) {
        vec2 s = raySeg(ro, rd, vec3(0.0), A * Lance * 4000.0);
        float w = 0.16;
        float core = exp(-s.x * s.x / (w * w));
        float wide = exp(-s.x / 1.2);
        glow += (vec3(1.0, 0.98, 0.9) * core * 9.0 + vec3(1.0, 0.75, 0.35) * wide * 1.2) * step(s.y, best + 0.001);
    }
    return vec4(col, best < 1e8 ? 1.0 : 0.0);
}

// ------------------------------------------------------------------------------------------------

void main() {
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 rd = normalize(CamF + ndc.x * Aspect * TanHalf * CamR + ndc.y * TanHalf * CamU);
    vec3 col = sky(rd);

    float tBest = 1e9;
    float t1 = sphere(EyeP, rd, vec3(0.0), 1.0);
    float t2 = Earth2.w > 0.0 ? sphere(EyeP, rd, Earth2.xyz, Earth2.w) : -1.0;

    // the breach: a ring of torn light, and the dark beyond it
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
                float cracks = pow(vnoise3(vec3(a * 9.0, Time * 0.3, 1.0)), 6.0) * exp(-max(d - 1.0, 0.0) * 3.0) * step(1.0, d);
                float ring = exp(-sqr((d - 1.0) / 0.035));
                if (d < 1.0 && (t1 < 0.0 || tb < t1)) {
                    col = mix(col, vec3(0.08, 0.0, 0.03) + col * vec3(1.2, 0.3, 0.5), 0.75 * BreachOpen);
                }
                col += (vec3(1.0, 0.25, 0.4) * ring * 3.0 + vec3(1.0, 0.5, 0.7) * cracks * 2.5) * BreachOpen;
            }
        }
    }

    if (t1 > 0.0) {
        tBest = t1;
        vec3 n = normalize(EyeP + rd * t1);
        col = earth(n, rd);
    }
    if (t2 > 0.0 && t2 < tBest) {
        tBest = t2;
        vec3 n = normalize(EyeP + rd * t2 - Earth2.xyz);
        col = otherEarth(n, rd, SunDir);
    }
    col += halo(EyeP, rd, vec3(0.0), 1.0, vec3(0.3, 0.55, 1.0), tBest);
    if (Earth2.w > 0.0) {
        col += halo(EyeP, rd, Earth2.xyz, Earth2.w, vec3(1.0, 0.2, 0.4), tBest);
        if (Contact > 0.0) {
            // where the two skies touch: a seam of light, and lightning crawling across it
            vec3 dirTo = normalize(Earth2.xyz);
            vec3 a = dirTo * 1.0;
            vec3 b = Earth2.xyz - dirTo * Earth2.w;
            vec3 mid = (a + b) * 0.5;
            vec2 s = raySeg(EyeP, rd, a - (b - a) * 0.5, b + (b - a) * 0.5);
            float seam = exp(-s.x / 0.02);
            col += vec3(1.0, 0.6, 0.9) * seam * Contact * 1.2 * step(s.y, tBest + 0.01);
            float tm = max(0.0, dot(mid - EyeP, rd));
            vec3 p = EyeP + rd * tm;
            float r = length(p - mid);
            float bolt = pow(1.0 - abs(2.0 * vnoise3((p - mid) * 40.0 + vec3(0.0, Time * 6.0, 0.0)) - 1.0), 18.0);
            col += vec3(0.9, 0.7, 1.0) * bolt * exp(-r / 0.12) * Contact * 3.0 * step(tm, tBest + 0.01);
        }
    }

    if (Mode > 0.5) {
        vec3 g;
        vec4 h = helios(LocalEye, rd, g);
        if (h.a > 0.0) {
            col = h.rgb;
        }
        col += g;
    }

    col *= Fade;
    // tone: keep the highlights hot enough to bloom
    col = col / (1.0 + col * 0.35);
    col = pow(col, vec3(1.0 / 1.1));
    fragColor = vec4(col, 1.0);
}
