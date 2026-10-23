#version 150

// Modulo Yuji: every effect of his moves, drawn over the finished frame using its depth buffer.
// Effect instances arrive in a float texture (FxData, 4 vec4 columns per row), one section per kind:
//
//   SLASH   Dismantle crescents sweeping out and the seams they leave; Cleave's straight cuts
//   FLASH   Black Flash: black lightning with crimson rims, a void flash, the burst of distorted space
//   RING    shockwaves racing over the ground
//   DUST    volumetric dust clouds thrown up by craters
//   BOLT    blood (and cursed-energy) projectiles: liquid capsules with a wet highlight and a glowing wake
//   ORB     blood spheres (Supernova, Crimson Execution) with a rippling surface
//   SHELL   expanding hemispheres of cursed energy (the Apocalypse wave, Overdrive's burst, the domain surge)
//   CHARGE  energy compressing into a fist: the world's light pulled into a point
//   AURA    cursed energy burning around a body (Overdrive)
//   MARK    targets marked by the domain
//   TRENCH  white-hot seams left in the ground by long cuts and directional blasts
//   SPHERE  the great blood formation in the sky (Apocalypse)
// plus the domain (a dusk sky over a far city skyline, a living energy floor, a shimmering boundary dome)
// and a global darkening for the moments the world goes quiet.

#include "common.glsl"
#include "world_common.glsl"

in vec2 uv;
out vec4 fragColor;

uniform sampler2D FxData;
uniform int NSlash, NFlash, NRing, NDust, NBolt, NOrb, NShell, NCharge, NAura, NMark, NTrench, NSphere;
uniform int Steps;
uniform vec4 DomC;     // centre (rel), radius
uniform vec4 DomP;     // age s, strength 0..1, seed, on
uniform vec4 Dark;     // amount, tint r, g, b
uniform float Overdrive;

const float PI = 3.14159265;
const int B_SLASH = 0, B_FLASH = 48, B_RING = 56, B_DUST = 72, B_BOLT = 80, B_ORB = 208, B_SHELL = 224,
          B_CHARGE = 228, B_AURA = 232, B_MARK = 240, B_TRENCH = 256, B_SPHERE = 272;

vec4 fx(int row, int col) {
    return texelFetch(FxData, ivec2(col, row), 0);
}

vec2 toScreen(vec3 rel) {
    vec4 c = Proj * vec4(ViewRot * rel, 1.0);
    return c.xy / max(c.w, 1.0e-4) * 0.5 + 0.5;
}

float inFront(vec3 rel) {
    return (ViewRot * rel).z < 0.0 ? 1.0 : 0.0;
}

float lineCore(float d, float t, float w) {
    float ww = max(w, t * 0.0012);
    return exp(-(d * d) / (ww * ww));
}

vec3 bloodColor(float k) {
    // deep, almost black red .. saturated crimson .. hot highlight
    k = clamp(k, 0.0, 1.0);
    vec3 c = mix(vec3(0.05, 0.03, 0.09), vec3(0.55, 0.33, 0.05), smoothstep(0.0, 0.55, k));
    return mix(c, vec3(1.0, 0.83, 0.42), smoothstep(0.75, 1.0, k));
}

vec3 cursedColor(float k) {
    // black core .. crimson .. hot white rim
    k = clamp(k, 0.0, 1.0);
    vec3 c = mix(vec3(0.0), vec3(0.80, 0.50, 0.07), smoothstep(0.2, 0.7, k));
    return mix(c, vec3(1.0, 0.95, 0.82), smoothstep(0.85, 1.0, k));
}

// ------------------------------------------------------------------ Dismantle / Cleave
vec3 slashes(vec3 rd, float sceneT, vec3 wp, bool hasSurface, inout float darken) {
    vec3 add = vec3(0.0);
    for (int i = 0; i < 48; i++) {
        if (i >= NSlash) break;
        vec4 o = fx(B_SLASH + i, 0), d = fx(B_SLASH + i, 1), s = fx(B_SLASH + i, 2), p = fx(B_SLASH + i, 3);
        vec3 O = o.xyz;
        float age = o.w;
        vec3 D = d.xyz;
        float L = d.w;
        vec3 S = s.xyz;
        float W = s.w;
        float kind = p.x, speed = p.y, seed = p.z, vis = p.w;
        vec3 N = normalize(cross(D, S));
        if (kind < 0.5) {
            float front = min(age * speed, L);
            float bladeLife = 1.0 - smoothstep(L / speed, L / speed + 0.1, age);
            if (bladeLife > 0.0) {
                float best = 1.0e9, bt = 0.0, bs = 0.0;
                vec3 prev = O + D * (front - W * 0.3) - S * W * 0.5;
                for (int k = 1; k <= 6; k++) {
                    float sk = float(k) / 6.0 * 2.0 - 1.0;
                    vec3 cur = O + D * (front - W * 0.3 * (sk * sk)) + S * (sk * W * 0.5);
                    vec3 r = raySegment(vec3(0.0), rd, prev, cur);
                    if (r.x < best) { best = r.x; bt = r.z; bs = sk - (1.0 - r.y) * (2.0 / 6.0); }
                    prev = cur;
                }
                if (bt < sceneT) {
                    float taper = 1.0 - bs * bs;
                    float core = lineCore(best, bt, 0.05 * taper + 0.012);
                    float glow = exp(-best / (0.35 + bt * 0.004));
                    // visibility: 0 = a near-invisible distortion line, 1 = a blazing blade
                    add += (vec3(1.0) * core * (2.0 + 6.0 * vis) + vec3(1.0, 0.9, 0.7) * glow * (0.15 + 0.5 * vis)) * taper * bladeLife;
                }
                float den = dot(rd, N);
                if (abs(den) > 1.0e-4) {
                    float tp = dot(O, N) / den;
                    if (tp > 0.0 && tp < sceneT) {
                        vec3 q = rd * tp - O;
                        float a = dot(q, D), sv = dot(q, S);
                        if (a > 0.0 && a < front && abs(sv) < W * 0.5) {
                            float lane = hash12(vec2(floor(sv * 3.0), seed));
                            float streak = step(0.72, lane) * smoothstep(front - 10.0, front, a) * (1.0 - abs(sv) / (W * 0.5));
                            add += vec3(1.0, 0.95, 0.8) * streak * (0.15 + 0.45 * vis) * bladeLife;
                        }
                    }
                }
            }
            if (hasSurface) {
                vec3 q = wp - O;
                float a = dot(q, D), sv = dot(q, S), n = dot(q, N);
                if (a > 0.0 && a < front && abs(sv) < W * 0.5) {
                    float since = age - a / speed;
                    float hot = exp(-since * 3.0);
                    float life = 1.0 - smoothstep(1.4, 3.0, since);
                    float px = max(fwidth(n) * 1.2, 0.03);
                    float edge = exp(-(n * n) / (px * px)) * life;
                    vec3 c = mix(vec3(0.70, 0.43, 0.05), vec3(1.0, 0.95, 0.92), hot);
                    add += c * edge * (1.5 + 6.0 * hot);
                    float px2 = max(fwidth(n) * 0.6, 0.015);
                    darken *= 1.0 - 0.85 * exp(-(n * n) / (px2 * px2)) * smoothstep(0.05, 0.25, since) * life;
                }
            }
        } else {
            float life = age < 0.0 ? 0.0 : exp(-age * 5.0);
            if (life > 0.002) {
                vec3 r = raySegment(vec3(0.0), rd, O, O + D * L);
                if (r.z < sceneT + 0.6) {
                    float draw = smoothstep(0.0, 0.04, age);
                    float along = step(r.y, draw);
                    float core = lineCore(r.x, r.z, 0.03);
                    float glow = exp(-r.x / 0.25);
                    add += (vec3(1.0) * core * 8.0 + vec3(1.0, 0.72, 0.22) * glow * 1.4) * life * along;
                }
            }
        }
    }
    return add;
}

// ------------------------------------------------------------------ Black Flash
vec3 blackFlashes(vec2 p, float aspect, vec3 rd, float sceneT, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= NFlash) break;
        vec4 a = fx(B_FLASH + i, 0), b = fx(B_FLASH + i, 1);
        vec3 P = a.xyz;
        float age = a.w;
        float scale = b.x, seed = b.y, variant = b.z;
        if (age < 0.0 || age > 0.9 + 0.25 * scale) continue;
        float dist = length(P);
        // ---- the void flash: a sphere of black with a crimson skin, swelling then collapsing
        float vr = scale * 1.4 * (smoothstep(0.0, 0.05, age) - 0.8 * smoothstep(0.08, 0.35, age));
        if (vr > 0.01) {
            vec2 h = sphereHit(vec3(0.0), rd, P, vr);
            if (h.x > 0.0 && h.x < sceneT) {
                vec3 n = normalize(rd * h.x - P);
                float rim = pow(1.0 - max(dot(n, -rd), 0.0), 2.0);
                darken *= 0.55 + 0.45 * (1.0 - smoothstep(0.0, 0.15, rim));
                col += vec3(1.0, 0.72, 0.22) * rim * 6.0;
            }
            vec3 oc = -P;
            float closest = length(oc - rd * dot(oc, rd));
            col += vec3(1.0, 0.76, 0.30) * exp(-max(closest - vr, 0.0) / (0.4 * scale)) * 1.5 * inFront(P) * exp(-age * 4.0);
        }
        if (inFront(P) < 0.5) continue;
        // ---- black lightning in screen space, branching, strobing
        vec2 c = toScreen(P);
        vec2 d = (p - c) * vec2(aspect, 1.0);
        float r = length(d);
        float th = atan(d.y, d.x);
        float sc = clamp(6.0 / max(dist, 1.0), 0.12, 1.6) * (0.7 + 0.35 * scale);
        float reach = (0.08 + 0.75 * smoothstep(0.0, 0.07, age)) * sc * (0.8 + 0.4 * hash12(vec2(seed, 9.0)));
        float strobe = 0.35 + 0.65 * step(0.22, hash12(vec2(floor(age * 40.0), seed)));
        float bolts = 0.0, rim = 0.0;
        int nb = 7 + int(variant) % 5;
        for (int k = 0; k < 12; k++) {
            if (k >= nb) break;
            float a0 = hash12(vec2(float(k), seed)) * 2.0 * PI;
            float fl = floor(age * 30.0);
            float jag = (fbm3(vec3(r * 11.0 / sc, float(k) * 7.0, seed + fl)) - 0.5) * 0.16 * sc;
            jag += (noise3(vec3(r * 60.0 / sc, float(k) * 3.0, fl)) - 0.5) * 0.02 * sc;
            float ang = mod(th - a0 + PI, 2.0 * PI) - PI;
            float da = abs(ang * r - jag * smoothstep(0.0, 0.05 * sc, r)) + step(PI * 0.5, abs(ang)) * 10.0;
            // a branch splitting off halfway out
            float a1 = a0 + (hash12(vec2(float(k), seed + 4.0)) - 0.5) * 1.3;
            float ang1 = mod(th - a1 + PI, 2.0 * PI) - PI;
            float bstart = reach * (0.35 + 0.3 * hash12(vec2(float(k), seed + 2.0)));
            float jag1 = (fbm3(vec3(r * 14.0 / sc, float(k) * 5.0 + 50.0, seed + fl)) - 0.5) * 0.12 * sc;
            float db = abs(ang1 * r - jag1) + step(PI * 0.5, abs(ang1)) * 10.0 + step(r, bstart) * 10.0;
            float w = 0.0065 * sc * (1.0 - r / reach) + 0.0008;
            float lim = step(r, reach);
            bolts = max(bolts, max(exp(-(da * da) / (w * w)), exp(-(db * db) / (w * w * 0.5))) * lim);
            rim = max(rim, max(exp(-da / (w * 3.0)), 0.6 * exp(-db / (w * 2.5))) * lim);
        }
        float life = 1.0 - smoothstep(0.25 + 0.1 * scale, 0.6 + 0.2 * scale, age);
        darken *= 1.0 - 0.30 * bolts * strobe * life;
        float halo = exp(-r / (0.05 * sc)) * 0.8 + exp(-r / (0.2 * sc)) * 0.25;
        col += vec3(1.0, 0.80, 0.32) * (bolts * 5.5 * strobe + rim * 3.5 * strobe + halo * max(0.0, 1.0 - age * 1.5)) * life;
        // the first instant: a white pinprick of overload at the point of contact
        col += vec3(1.0) * exp(-r / (0.012 * sc)) * exp(-age * 18.0) * 6.0;
    }
    return col;
}

// ------------------------------------------------------------------ shockwave rings on the ground
vec3 rings(vec3 wp, bool hasSurface, inout float darken) {
    if (!hasSurface) return vec3(0.0);
    vec3 col = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= NRing) break;
        vec4 a = fx(B_RING + i, 0), b = fx(B_RING + i, 1);
        float age = a.w;
        if (age < 0.0) continue;
        float speed = b.x, width = b.y, maxR = b.z, strength = b.w;
        float R = maxR * (1.0 - exp(-age * speed / maxR));
        vec3 q = wp - a.xyz;
        if (abs(q.y) > 4.0 + maxR * 0.15) continue;
        float r = length(q.xz);
        float fade = exp(-age * 1.1) * strength;
        float w = width * (0.35 + age * 0.6);
        float px = max(fwidth(r), 0.02);
        float ring = exp(-pow((r - R) / max(w, px), 2.0));
        // the wave lifts dust: a dark band just behind the bright front
        float behind = exp(-pow((r - R + w * 3.0) / (w * 2.0), 2.0)) * step(r, R);
        darken *= 1.0 - 0.35 * behind * fade;
        col += mix(vec3(1.0, 0.83, 0.42), vec3(1.0, 0.95, 0.9), exp(-age * 3.0)) * ring * fade * 1.6;
    }
    return col;
}

// ------------------------------------------------------------------ dust clouds (volumetric)
vec4 dust(vec3 rd, float sceneT) {
    vec3 acc = vec3(0.0);
    float trans = 1.0;
    vec3 sun = normalize(vec3(0.4, 0.8, 0.3));
    for (int i = 0; i < 8; i++) {
        if (i >= NDust) break;
        vec4 a = fx(B_DUST + i, 0), b = fx(B_DUST + i, 1);
        float age = a.w;
        if (age < 0.0) continue;
        float R = b.x * (0.5 + 0.7 * (1.0 - exp(-age * 0.8)));
        float H = b.y * (0.4 + 0.8 * (1.0 - exp(-age * 0.5)));
        float strength = b.z * (1.0 - smoothstep(6.0, 14.0, age)) * smoothstep(0.0, 0.3, age);
        vec3 c = a.xyz + vec3(0.0, H * 0.4, 0.0);
        vec2 h = sphereHit(vec3(0.0), rd, c, max(R, H) * 1.2);
        if (h.y < 0.0 || strength <= 0.0) continue;
        float t0 = max(h.x, 0.0), t1 = min(h.y, sceneT);
        if (t1 <= t0) continue;
        float dt = (t1 - t0) / 20.0;
        float jit = hash12(gl_FragCoord.xy + float(i));
        for (int k = 0; k < 20; k++) {
            vec3 p = rd * (t0 + dt * (float(k) + jit)) - a.xyz;
            float hy = p.y / max(H, 1.0);
            if (hy < -0.1 || hy > 1.2) continue;
            float rr = length(p.xz) / (R * (0.6 + 0.6 * hy));
            float n = fbm3(p / max(R, 1.0) * 3.0 + vec3(0.0, -age * 0.4, age * 0.1));
            float dens = clamp((1.0 - rr) * 1.4 + (n - 0.5) * 1.6, 0.0, 1.0) * smoothstep(1.2, 0.6, hy) * strength;
            if (dens <= 0.0) continue;
            float lit = 0.45 + 0.55 * clamp(0.5 + hy * 0.6, 0.0, 1.0);
            vec3 c2 = vec3(0.42, 0.37, 0.32) * lit;
            float ab = dens * dt / max(R, 1.0) * 3.0;
            acc += c2 * ab * trans;
            trans *= exp(-ab * 1.4);
        }
    }
    return vec4(acc, trans);
}

// ------------------------------------------------------------------ blood bolts and cursed streaks
vec3 bolts(vec3 rd, float sceneT, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 128; i++) {
        if (i >= NBolt) break;
        vec4 a = fx(B_BOLT + i, 0), b = fx(B_BOLT + i, 1), c = fx(B_BOLT + i, 2);
        vec3 head = a.xyz, tail = b.xyz;
        float rad = a.w, glow = b.w, kind = c.x, life = c.y;
        if (life <= 0.0) continue;
        vec3 r = raySegment(vec3(0.0), rd, tail, head);
        if (r.z > sceneT + rad) continue;
        float w = rad * mix(0.25, 1.0, pow(r.y, 0.7));        // the wake tapers
        float px = max(w, r.z * 0.0015);
        if (kind < 0.5) {
            // blood: a dark liquid core, a wet highlight along one edge, a crimson glow
            float core = exp(-(r.x * r.x) / (px * px));
            float edge = smoothstep(px * 0.2, px, r.x) * core;
            vec3 c3 = bloodColor(0.55 + 0.45 * core) * core * 2.2 + vec3(1.0, 0.9, 0.7) * edge * 1.2 * r.y;
            darken *= 1.0 - 0.6 * core * life;
            col += (c3 + vec3(0.95, 0.66, 0.13) * exp(-r.x / (px * 4.0)) * glow * 0.8) * life;
        } else {
            // cursed energy: black core, crimson body, white-hot rim
            float core = exp(-(r.x * r.x) / (px * px * 0.3));
            float body = exp(-(r.x * r.x) / (px * px * 1.5));
            darken *= 1.0 - 0.85 * core * life;
            col += (vec3(1.0, 0.72, 0.22) * (body - core) * 3.0 + vec3(1.0, 0.95, 0.82) * exp(-r.x / (px * 0.6)) * (1.0 - core) * 0.6
                   + vec3(1.0, 0.70, 0.20) * exp(-r.x / (px * 5.0)) * glow * 0.6) * life;
        }
    }
    return col;
}

// ------------------------------------------------------------------ blood orbs
vec3 orbs(vec3 rd, float sceneT, inout float occl) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= NOrb) break;
        vec4 a = fx(B_ORB + i, 0), b = fx(B_ORB + i, 1);
        float R = a.w;
        if (R <= 0.0) continue;
        vec2 h = sphereHit(vec3(0.0), rd, a.xyz, R);
        vec3 oc = -a.xyz;
        float closest = length(oc - rd * dot(oc, rd));
        if (dot(a.xyz, rd) > 0.0) col += vec3(0.80, 0.50, 0.08) * exp(-max(closest - R, 0.0) / (R * 0.8)) * b.x * 0.6;
        if (h.x > 0.0 && h.x < sceneT) {
            vec3 p = rd * h.x - a.xyz;
            vec3 n = normalize(p + (vec3(noise3(p * 3.0 + Time * 2.0), noise3(p * 3.0 + 9.0 - Time * 2.0), noise3(p * 3.0 + 17.0)) - 0.5) * R * 0.6);
            float fres = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
            float spec = pow(max(dot(reflect(rd, n), normalize(vec3(0.3, 0.9, 0.2))), 0.0), 40.0);
            vec3 c3 = bloodColor(0.35 + 0.4 * fres) * 1.2 + vec3(1.0, 0.92, 0.78) * spec * 3.0 + vec3(1.0, 0.76, 0.30) * fres * 1.5;
            col = c3;
            occl = 0.0;
        }
    }
    return col;
}

// ------------------------------------------------------------------ energy shells (hemispheres)
vec3 shells(vec3 rd, float sceneT, vec3 wp, bool hasSurface, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        if (i >= NShell) break;
        vec4 a = fx(B_SHELL + i, 0), b = fx(B_SHELL + i, 1);
        float R = a.w, thick = b.x, strength = b.y, kind = b.z;
        if (R <= 0.0 || strength <= 0.0) continue;
        vec2 h = sphereHit(vec3(0.0), rd, a.xyz, R);
        for (int s = 0; s < 2; s++) {
            float t = s == 0 ? h.x : h.y;
            if (t <= 0.0 || t > sceneT) continue;
            vec3 p = rd * t - a.xyz;
            if (p.y < -2.0) continue;
            vec3 n = normalize(p);
            float fres = pow(1.0 - abs(dot(n, rd)), 2.0);
            float pat = fbm3(n * 6.0 + vec3(0.0, -Time * 1.5, 0.0));
            float vein = exp(-pow((pat - 0.5) / 0.04, 2.0));
            vec3 c3 = kind < 0.5 ? cursedColor(0.6 + 0.4 * fres) : vec3(1.0, 0.78, 0.45);
            col += (c3 * (fres * 1.6 + vein * 1.5) + vec3(1.0) * vein * fres * 0.8) * strength;
            darken *= 1.0 - 0.25 * fres * strength * (kind < 0.5 ? 1.0 : 0.0);
        }
        // the ground the wave has passed over smoulders
        if (hasSurface) {
            vec3 q = wp - a.xyz;
            float rr = length(q.xz);
            float front = exp(-pow((rr - R) / (thick + 1.0), 2.0)) * step(abs(q.y), R * 0.4 + 3.0);
            col += vec3(1.0, 0.74, 0.26) * front * 2.0 * strength;
        }
    }
    return col;
}

// ------------------------------------------------------------------ charge: the world pulled into a fist
vec3 charges(vec2 p, float aspect, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        if (i >= NCharge) break;
        vec4 a = fx(B_CHARGE + i, 0);
        float amt = a.w;
        if (amt <= 0.0 || inFront(a.xyz) < 0.5) continue;
        vec2 c = toScreen(a.xyz);
        vec2 d = (p - c) * vec2(aspect, 1.0);
        float r = length(d);
        float sc = clamp(4.0 / max(length(a.xyz), 1.0), 0.15, 1.5);
        // streaks converging on the point, moving inward
        float ang = atan(d.y, d.x) / (2.0 * PI) + 0.5;
        float cell = floor(ang * 90.0);
        float hsh = hash12(vec2(cell, 3.0));
        float lane = step(0.6, hsh) * smoothstep(0.35, 0.0, abs(fract(ang * 90.0) - 0.5));
        float flow = fract(r * 6.0 / sc + Time * 4.0 * (0.7 + hsh));
        float streak = lane * smoothstep(0.0, 0.25, flow) * smoothstep(1.0, 0.6, flow) * smoothstep(0.6 * sc, 0.04 * sc, r);
        col += vec3(1.0, 0.76, 0.3) * streak * amt * 1.2;
        // a black point with a crimson corona, tightening as it fills
        float core = exp(-r / (0.006 + 0.02 * sc * (1.0 - 0.6 * amt)));
        darken *= 1.0 - 0.9 * core * amt;
        col += vec3(1.0, 0.72, 0.22) * exp(-r / (0.03 * sc + 0.03 * sc * amt)) * amt * 2.0;
        // the rest of the frame dims, as if the light were being drawn in
        darken *= 1.0 - 0.25 * amt * smoothstep(0.1, 0.8, r);
    }
    return col;
}

// ------------------------------------------------------------------ aura (Overdrive)
vec3 auras(vec3 rd, float sceneT, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= NAura) break;
        vec4 a = fx(B_AURA + i, 0), b = fx(B_AURA + i, 1);
        float amt = b.x;
        if (amt <= 0.0) continue;
        vec3 base = a.xyz;
        float H = a.w;
        vec3 mid = base + vec3(0.0, H * 0.5, 0.0);
        vec2 h = sphereHit(vec3(0.0), rd, mid, H * 0.85);
        if (h.y < 0.0) continue;
        float t0 = max(h.x, 0.0), t1 = min(h.y, sceneT + 0.4);
        if (t1 <= t0) continue;
        float dt = (t1 - t0) / 16.0;
        vec3 acc = vec3(0.0);
        float dk = 0.0;
        for (int k = 0; k < 16; k++) {
            vec3 p = rd * (t0 + dt * (float(k) + 0.5)) - base;
            float y = p.y / H;
            if (y < -0.05 || y > 1.35) continue;
            float rr = length(p.xz);
            float shell = 0.55 + 0.25 * y;
            float n = fbm3(vec3(p.xz * 3.0, p.y * 1.8 - Time * 4.5));
            float n2 = noise3(vec3(p.xz * 7.0, p.y * 4.0 - Time * 7.0));
            float flame = smoothstep(shell, shell * 0.6, rr - (n - 0.5) * 0.7) * smoothstep(1.35, 0.6, y + (n - 0.5) * 0.6);
            float tongues = smoothstep(0.45, 0.8, n * 0.7 + n2 * 0.5);
            float inner = smoothstep(shell * 0.8, shell * 0.45, rr);
            float e = flame * tongues * (1.0 - inner * 0.9);
            acc += (vec3(1.0, 0.72, 0.22) * e * 1.6 + vec3(1.0, 0.93, 0.62) * e * smoothstep(0.85, 1.0, n2) * 2.0) * dt;
            dk += flame * tongues * dt * 0.5;
        }
        darken *= 1.0 - clamp(dk, 0.0, 0.5) * amt;
        col += acc * amt * 2.2;
    }
    return col;
}

// ------------------------------------------------------------------ marked targets
vec3 marks(vec3 rd, float sceneT, vec3 wp, bool hasSurface) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= NMark) break;
        vec4 a = fx(B_MARK + i, 0);
        if (a.w <= 0.0) continue;
        if (hasSurface) {
            vec3 q = wp - a.xyz;
            float r = length(q.xz);
            if (abs(q.y) < 1.5) {
                float ring = exp(-pow((r - 1.3) / 0.08, 2.0)) + 0.5 * exp(-pow((r - 0.8 - 0.3 * fract(Time)) / 0.06, 2.0));
                float ang = atan(q.z, q.x);
                float ticks = step(0.8, fract(ang / (2.0 * PI) * 12.0 + Time * 0.3)) * exp(-pow((r - 1.6) / 0.12, 2.0));
                col += vec3(1.0, 0.72, 0.24) * (ring + ticks) * 2.0 * a.w;
            }
        }
        vec3 s = raySegment(vec3(0.0), rd, a.xyz, a.xyz + vec3(0.0, 6.0, 0.0));
        if (s.z < sceneT) col += vec3(1.0, 0.76, 0.30) * exp(-s.x / 0.08) * (1.0 - s.y) * 0.8 * a.w;
    }
    return col;
}

// ------------------------------------------------------------------ trenches: glowing seams on the ground
vec3 trenches(vec3 wp, bool hasSurface) {
    if (!hasSurface) return vec3(0.0);
    vec3 col = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= NTrench) break;
        vec4 a = fx(B_TRENCH + i, 0), b = fx(B_TRENCH + i, 1), c = fx(B_TRENCH + i, 2);
        float age = a.w;
        if (age < 0.0) continue;
        vec3 D = b.xyz;
        float L = b.w;
        float W = c.x, speed = c.y;
        vec3 q = wp - a.xyz;
        float al = dot(q.xz, normalize(D.xz));
        float front = min(L, age * speed);
        if (al < 0.0 || al > front) continue;
        vec2 nd = normalize(vec2(-D.z, D.x));
        float off = abs(dot(q.xz, nd));
        float since = age - al / speed;
        float hot = exp(-since * 1.2);
        float life = 1.0 - smoothstep(3.0, 8.0, since);
        float w = W * 0.5 + 0.3;
        float seam = exp(-pow(max(off - W * 0.5, 0.0) / 0.6, 2.0)) * smoothstep(W * 0.5 + 2.5, W * 0.5, off);
        float crack = 0.6 + 0.4 * noise3(vec3(al * 0.4, off * 0.8, 1.0));
        col += mix(vec3(0.7, 0.45, 0.06), vec3(1.0, 0.85, 0.75), hot) * seam * crack * (1.0 + 5.0 * hot) * life * step(abs(q.y), 20.0);
        // the wave racing ahead of the cut
        col += vec3(1.0, 0.9, 0.85) * exp(-pow((al - front) / 2.5, 2.0)) * exp(-pow(off / (w + 2.0), 2.0)) * 3.0 * step(age * speed, L + 4.0);
    }
    return col;
}

// ------------------------------------------------------------------ the great blood formation
vec3 bloodSphere(vec3 rd, float sceneT, inout float occl, inout float darken) {
    if (NSphere < 1) return vec3(0.0);
    vec4 a = fx(B_SPHERE, 0), b = fx(B_SPHERE, 1);
    float R = a.w, age = b.x, strength = b.y;
    if (R <= 0.0 || strength <= 0.0) return vec3(0.0);
    vec3 col = vec3(0.0);
    // orbiting rings of blood
    for (int k = 0; k < 3; k++) {
        float tilt = 0.4 + float(k) * 0.55;
        vec3 n = normalize(vec3(sin(tilt + Time * 0.1), 1.0, cos(tilt * 1.7)));
        float den = dot(rd, n);
        if (abs(den) < 1.0e-4) continue;
        float t = dot(a.xyz, n) / den;
        if (t <= 0.0 || t > sceneT) continue;
        vec3 q = rd * t - a.xyz;
        float rr = length(q);
        float ringR = R * (1.5 + 0.35 * float(k));
        float band = exp(-pow((rr - ringR) / (R * 0.05), 2.0));
        float ang = atan(dot(q, normalize(cross(n, vec3(1.0, 0.0, 0.0)))), dot(q, normalize(cross(n, vec3(0.0, 0.0, 1.0)))));
        float beads = 0.5 + 0.5 * sin(ang * 40.0 + Time * (2.0 + float(k)));
        col += bloodColor(0.7) * band * (0.6 + beads) * 2.0 * strength;
    }
    vec2 h = sphereHit(vec3(0.0), rd, a.xyz, R);
    vec3 oc = -a.xyz;
    float closest = length(oc - rd * dot(oc, rd));
    if (dot(a.xyz, rd) > 0.0) col += vec3(0.72, 0.46, 0.06) * exp(-max(closest - R, 0.0) / (R * 0.6)) * strength * 1.2;
    if (h.x > 0.0 && h.x < sceneT) {
        vec3 p = rd * h.x - a.xyz;
        vec3 pn = p / R;
        float n1 = fbm3(pn * 2.5 + vec3(Time * 0.3, -Time * 0.5, 0.0));
        vec3 n = normalize(pn + (vec3(n1, fbm3(pn * 2.5 + 7.0), fbm3(pn * 2.5 + 13.0)) - 0.5) * 0.8);
        float fres = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
        float spec = pow(max(dot(reflect(rd, n), normalize(vec3(0.3, 0.9, 0.2))), 0.0), 30.0);
        float veins = exp(-pow((n1 - 0.5) / 0.03, 2.0));
        col = bloodColor(0.25 + 0.5 * fres) * 1.3 + vec3(1.0, 0.92, 0.76) * spec * 2.5 + vec3(1.0, 0.74, 0.26) * (fres * 1.5 + veins * 1.2);
        occl = 0.0;
    }
    darken *= 1.0 - 0.35 * strength;
    return col;
}

// ------------------------------------------------------------------ the domain
vec3 domainSky(vec3 rd, float strength) {
    float up = clamp(rd.y, -0.1, 1.0);
    // dusk: a burning horizon, violet overhead
    vec3 sky = mix(vec3(0.95, 0.68, 0.22), vec3(0.32, 0.12, 0.35), smoothstep(0.0, 0.45, up));
    sky = mix(sky, vec3(0.06, 0.03, 0.12), smoothstep(0.4, 1.0, up));
    // a low sun, swollen and red, and long bands of cloud
    vec3 sunDir = normalize(vec3(0.7, 0.06, 0.3));
    float sd = max(dot(rd, sunDir), 0.0);
    sky += vec3(1.0, 0.82, 0.42) * (pow(sd, 400.0) * 6.0 + pow(sd, 12.0) * 0.6);
    float bands = fbm3(vec3(rd.xz / max(rd.y + 0.12, 0.03) * vec2(0.5, 2.0), Time * 0.02));
    sky = mix(sky, sky * vec3(0.55, 0.35, 0.45), smoothstep(0.5, 0.75, bands) * smoothstep(0.03, 0.25, up));
    // a far city skyline along the horizon, towers silhouetted against the dusk, windows lit
    float az = atan(rd.z, rd.x);
    float col = floor(az * 160.0 / PI);
    float hgt = 0.015 + 0.05 * pow(hash12(vec2(col, 7.0)), 3.0) + 0.012 * hash12(vec2(floor(az * 40.0 / PI), 3.0));
    if (rd.y < hgt && rd.y > -0.05) {
        vec3 bld = vec3(0.05, 0.03, 0.08);
        vec2 win = vec2(fract(az * 160.0 / PI * 4.0), fract(rd.y * 600.0));
        float lit = step(0.55, hash12(vec2(floor(az * 640.0 / PI), floor(rd.y * 600.0)))) * step(0.3, win.x) * step(0.4, win.y);
        bld += vec3(1.0, 0.86, 0.46) * lit * 0.6;
        sky = mix(sky, bld, strength);
    }
    return sky;
}

vec3 domain(vec3 rd, float sceneT, vec3 wp, bool hasSurface, vec3 col, inout float darken) {
    if (DomP.w < 0.5 || DomP.y <= 0.0) return col;
    float s = DomP.y;
    vec3 C = DomC.xyz;
    float R = DomC.w;
    float camIn = 1.0 - smoothstep(R - 2.0, R + 2.0, length(C));
    vec2 h = sphereHit(vec3(0.0), rd, C, R);
    if (!hasSurface && camIn > 0.5) {
        col = mix(col, domainSky(rd, 1.0), s);
    } else if (hasSurface) {
        float inside = 1.0 - smoothstep(R - 1.0, R + 1.0, length(wp - C));
        // the domain's light: warm dusk on everything inside it
        float l = luminance(col);
        vec3 graded = mix(vec3(l), col, 0.55) * vec3(1.1, 0.96, 0.62);
        col = mix(col, graded, inside * s);
        // the floor is alive: lines of crimson energy flowing outward through the ground
        vec3 q = wp - C;
        float rr = length(q.xz);
        float ang = atan(q.z, q.x);
        float flow = fbm3(vec3(ang * 4.0, rr * 0.06 - Time * 0.4, DomP.z));
        float fw = max(fwidth(flow) * 1.2, 0.002);
        float vein = exp(-pow((flow - 0.5) / min(fw, 0.018), 2.0)) * step(abs(q.y), 6.0);
        float pulse = exp(-pow(fract(rr / 24.0 - Time * 0.25) - 0.5, 2.0) * 60.0);
        col += vec3(1.0, 0.72, 0.24) * (vein * (0.8 + 1.5 * pulse)) * inside * s;
        // the boundary where it meets the ground
        col += vec3(1.0, 0.78, 0.34) * exp(-pow((rr - R) / 1.2, 2.0)) * 2.5 * s * step(abs(q.y), 8.0);
    }
    // the boundary dome: a thin shimmering skin, brightest at the edges
    for (int k = 0; k < 2; k++) {
        float t = k == 0 ? h.x : h.y;
        if (t <= 0.0 || t > sceneT) continue;
        vec3 p = rd * t - C;
        if (p.y < -1.0) continue;
        vec3 n = normalize(p);
        float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
        float pat = fbm3(n * 9.0 + vec3(0.0, Time * 0.3, 0.0));
        float lines = exp(-pow((fract(pat * 6.0) - 0.5) / 0.05, 2.0));
        vec3 c = vec3(1.0, 0.78, 0.36) * (fres * 1.4 + lines * 0.25) + vec3(1.0, 0.72, 0.24) * fres * fres;
        col += c * s * (camIn > 0.5 ? 0.12 * fres : 1.0);
        if (camIn < 0.5 && k == 0) {
            // seen from outside, the dusk shows through the skin
            col = mix(col, domainSky(reflect(rd, n), 0.0), 0.35 * s * (1.0 - fres));
        }
    }
    return col;
}

void main() {
    vec2 res = vec2(textureSize(SceneTex, 0));
    float aspect = res.x / res.y;
    vec3 rd = viewRay(uv);
    vec3 col = texture(SceneTex, uv).rgb;
    float sceneT = sceneDistance(uv);
    bool hasSurface = sceneT < 1.0e8;
    vec3 wp = rd * sceneT;
    float darken = 1.0;

    // the world goes quiet: everything darkens and loses colour (big moves charging)
    if (Dark.x > 0.0) {
        float l = luminance(col);
        vec3 dk = mix(vec3(l), col, 0.4) * Dark.yzw * (hasSurface ? 0.45 : 0.25);
        col = mix(col, dk, Dark.x);
    }
    col = domain(rd, sceneT, wp, hasSurface, col, darken);

    vec3 add = vec3(0.0);
    add += slashes(rd, sceneT, wp, hasSurface, darken);
    add += rings(wp, hasSurface, darken);
    add += trenches(wp, hasSurface);
    add += marks(rd, sceneT, wp, hasSurface);
    add += shells(rd, sceneT, wp, hasSurface, darken);
    add += auras(rd, sceneT, darken);
    add += bolts(rd, sceneT, darken);
    add += charges(uv, aspect, darken);
    add += blackFlashes(uv, aspect, rd, sceneT, darken);

    float occl = 1.0;
    vec3 sph = bloodSphere(rd, sceneT, occl, darken);
    vec3 orb = orbs(rd, sceneT, occl);
    col = col * darken;
    if (occl < 0.5) col = vec3(0.0);
    col += sph + orb + add;

    vec4 ds = dust(rd, sceneT);
    col = col * ds.a + ds.rgb;

    fragColor = vec4(max(col, vec3(0.0)), hasSurface ? min(sceneT, 4000.0) : 4000.0);
}
