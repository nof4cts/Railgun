#version 150

// The World Over Heaven: every effect of his moves, drawn over the finished frame using its depth buffer.
// His visual language is reality treated as glass and clockwork, in gold:
//   SEAL     each overwrite blow: a geometric seal stamped into the air while the panes of reality around it crack
//   DIAL     clock-dial rings racing over the ground, ticked like a watch face
//   DUST     glittering glass dust thrown up by impacts
//   CRYSTAL  faceted crystal shards, and pillars of light
//   DOME     gold geodesic lattice domes; the clock-face sphere of a time stop
//   GATHER   power drawn in: a dial closing on a point while its hands sweep shut
//   HALO     Heaven Ascension: a column of light and turning halos
//   MARK     those held by Reality Collapse
//   FISSURE  gold seams opened in the ground
// plus Reality Collapse itself (a lattice void overhead, the ground turned to a pulsing grid) and the world's drain.
// Effect instances arrive in a float texture (FxData), one section of rows per kind.

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
const int B_FLASH = 48, B_RING = 56, B_DUST = 72, B_BOLT = 80, B_SHELL = 224,
          B_CHARGE = 228, B_AURA = 232, B_MARK = 240, B_TRENCH = 256;

const vec3 GOLD = vec3(1.0, 0.76, 0.28);
const vec3 GOLD_HOT = vec3(1.0, 0.9, 0.62);
const vec3 GOLD_DEEP = vec3(0.55, 0.32, 0.05);
const vec3 IVORY = vec3(1.0, 0.97, 0.9);

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

float line(float d, float w) {
    return exp(-(d * d) / (w * w));
}

float notch(float x, float w) {          // 1 at x = 0, falling to 0 at |x| = w
    return 1.0 - smoothstep(0.0, w, abs(x));
}

vec2 hash22(vec2 p, float s) {
    return hash33(vec3(p, s)).xy;
}

/** Glass: distance to the nearest pane edge (F2 - F1) and an id for the pane. */
vec2 panes(vec2 q, float seed) {
    vec2 c = floor(q), f = fract(q);
    float d1 = 8.0, d2 = 8.0, id = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 r = g + hash22(c + g, seed) - f;
            float d = dot(r, r);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                id = hash12(c + g + vec2(seed));
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    return vec2(sqrt(d2) - sqrt(d1), id);
}

/** Signed distance (along the sector normal) to a regular n-gon of circumradius R, turned by rot. */
float ngon(float r, float th, float n, float R, float rot) {
    float a = mod(th + rot, 2.0 * PI / n) - PI / n;
    return r * cos(a) - R * cos(PI / n);
}

/** Three families of lines crossing into a triangulated geodesic skin, on unit direction n. */
float lattice(vec3 n, float k) {
    vec3 b = vec3(n.x, n.y * 0.5 + n.z * 0.866, -n.y * 0.866 + n.z * 0.5);
    float f1 = abs(fract(atan(n.z, n.x) / (2.0 * PI) * k * 2.0 + 0.5) - 0.5);
    float f2 = abs(fract(atan(b.z, b.x) / (2.0 * PI) * k * 2.0 + 0.5) - 0.5);
    float f3 = abs(fract(n.y * k * 0.5 + 0.5) - 0.5);
    return max(max(notch(f1, 0.06), notch(f2, 0.06)), notch(f3, 0.05));
}

// ------------------------------------------------------------------ the seal (every overwrite blow, and the erase)
vec3 seals(vec2 p, float aspect, vec3 rd, float sceneT, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= NFlash) break;
        vec4 a = fx(B_FLASH + i, 0), b = fx(B_FLASH + i, 1);
        vec3 P = a.xyz;
        float age = a.w;
        float scale = b.x, seed = b.y, variant = b.z;
        bool erase = variant > 40.0;
        float span = (erase ? 1.4 : 0.75) + 0.2 * scale;
        if (age < 0.0 || age > span || inFront(P) < 0.5) continue;
        float dist = length(P);
        vec2 c = toScreen(P);
        vec2 d = (p - c) * vec2(aspect, 1.0);
        float r = length(d), th = atan(d.y, d.x);
        float sc = clamp(6.0 / max(dist, 1.0), 0.12, 1.6) * (0.7 + 0.3 * scale);
        float open = smoothstep(0.0, 0.08, age);
        float life = 1.0 - smoothstep(span * 0.45, span, age);
        float px = 0.0018 + 0.0007 * sc;
        // an outer circle and its twin, a hexagram of two turning triangles, an inner hexagon, spokes and dial ticks
        float R = (0.10 + 0.05 * scale) * sc * (0.6 + 0.4 * open);
        float spin = age * (erase ? 1.2 : 2.5) + seed;
        float seal = line(r - R, px * 1.2) + 0.7 * line(r - R * 1.12, px * 0.8);
        seal += line(ngon(r, th, 3.0, R * 0.86, spin), px);
        seal += line(ngon(r, th, 3.0, R * 0.86, spin + PI / 3.0), px);
        seal += 0.8 * line(ngon(r, th, 6.0, R * 0.48, -spin * 1.5), px);
        float inRing = step(R * 0.48, r) * step(r, R);
        seal += 0.6 * line(mod(th + spin * 0.5, 2.0 * PI / 12.0) - PI / 12.0, 0.012) * inRing;
        float rim = step(R, r) * step(r, R * 1.12);
        seal += 0.8 * line(mod(th - spin * 0.3, 2.0 * PI / 36.0) - PI / 36.0, 0.01) * rim;
        if (erase) {
            float R2 = R * 1.55;
            seal += line(r - R2, px * 1.5);
            seal += 0.8 * line(ngon(r, th, 8.0, R2 * 0.95, -spin * 0.7), px);
            seal += line(mod(th + spin * 0.8, 2.0 * PI / 24.0) - PI / 24.0, 0.02) * step(R2 * 0.95, r) * step(r, R2);
        }
        col += GOLD_HOT * seal * life * open * 2.2;
        // around it, reality cracks like a pane of glass, out to the blow's reach
        float reach = (erase ? 0.55 : 0.32) * sc * smoothstep(0.0, 0.1, age);
        if (r < reach) {
            vec2 v = panes(d / (0.045 * sc), seed);
            float fall = 1.0 - smoothstep(reach * 0.5, reach, r);
            float crack = line(v.x, 0.06) * fall;
            float pane = (0.15 + 0.25 * v.y) * fall * (1.0 - smoothstep(0.0, span, age));
            col += (GOLD * crack * 2.4 + GOLD_HOT * pane * 0.25) * life;
            darken *= 1.0 - 0.25 * crack * life;
        }
        // the instant of contact: an ivory point of overload
        col += IVORY * exp(-r / (0.01 * sc)) * exp(-age * 16.0) * 6.0;
        col += GOLD * exp(-r / (0.12 * sc)) * exp(-age * 6.0) * 0.6;
        // an erase pulls the light out of everything around its seal for a moment
        if (erase) darken *= 1.0 - 0.45 * smoothstep(R * 1.6, R * 3.0, r) * (1.0 - smoothstep(0.0, 0.5, age)) * open;
    }
    return col;
}

// ------------------------------------------------------------------ dials racing over the ground
vec3 dials(vec3 wp, bool hasSurface) {
    if (!hasSurface) return vec3(0.0);
    vec3 col = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= NRing) break;
        vec4 a = fx(B_RING + i, 0), b = fx(B_RING + i, 1);
        float age = a.w;
        if (age < 0.0) continue;
        float speed = b.x, width = b.y, maxR = b.z, strength = b.w;
        float R = maxR * (1.0 - exp(-age * speed / max(maxR, 0.1)));
        vec3 q = wp - a.xyz;
        if (abs(q.y) > 3.0 + maxR * 0.12) continue;
        float r = length(q.xz);
        float fade = exp(-age * 1.3) * strength;
        float px = max(fwidth(r), 0.02);
        float w = max(0.12 * width + 0.04, px);
        float ring = exp(-pow((r - R) / w, 2.0));
        float inner = 0.5 * exp(-pow((r - R * 0.86) / (w * 0.7), 2.0));
        float ang = atan(q.z, q.x) / (2.0 * PI);
        float hours = notch(fract(ang * 12.0 + 0.5) - 0.5, 0.06);
        float minutes = notch(fract(ang * 60.0 + 0.5) - 0.5, 0.12);
        float face = smoothstep(R * 0.86, R * 0.88, r) * (1.0 - smoothstep(R * 0.97, R * 0.99, r));
        float ticks = (hours + 0.35 * minutes) * face;
        col += (mix(GOLD, IVORY, exp(-age * 4.0)) * ring * 1.8 + GOLD * (inner + ticks) * 1.2) * fade;
    }
    return col;
}

// ------------------------------------------------------------------ glass dust (volumetric, glinting)
vec4 dust(vec3 rd, float sceneT) {
    vec3 acc = vec3(0.0);
    float trans = 1.0;
    for (int i = 0; i < 8; i++) {
        if (i >= NDust) break;
        vec4 a = fx(B_DUST + i, 0), b = fx(B_DUST + i, 1);
        float age = a.w;
        if (age < 0.0) continue;
        float R = b.x * (0.6 + 0.6 * (1.0 - exp(-age * 1.1)));
        float H = b.y * (0.3 + 0.7 * (1.0 - exp(-age * 0.7)));
        float strength = b.z * (1.0 - smoothstep(4.0, 10.0, age)) * smoothstep(0.0, 0.2, age);
        if (strength <= 0.0) continue;
        vec3 c = a.xyz + vec3(0.0, H * 0.35, 0.0);
        vec2 h = sphereHit(vec3(0.0), rd, c, max(R, H) * 1.15);
        if (h.y < 0.0) continue;
        float t0 = max(h.x, 0.0), t1 = min(h.y, sceneT);
        if (t1 <= t0) continue;
        float dt = (t1 - t0) / 16.0;
        float jit = hash12(gl_FragCoord.xy + vec2(float(i) * 7.0));
        for (int k = 0; k < 16; k++) {
            vec3 p = rd * (t0 + dt * (float(k) + jit)) - a.xyz;
            float hy = p.y / max(H, 1.0);
            if (hy < -0.05 || hy > 1.1) continue;
            // a rising ring of dust, thick at its rim and thin at its heart
            float rr = length(p.xz) / max(R, 0.5);
            float torus = exp(-pow((rr - 0.7 - 0.2 * hy) / 0.35, 2.0));
            float nz = noise3(p / max(R, 1.0) * 4.0 + vec3(0.0, -age * 0.6, age * 0.2));
            float dens = clamp(torus * (0.6 + 0.8 * nz), 0.0, 1.0) * (1.0 - smoothstep(0.4, 1.1, hy)) * strength;
            if (dens <= 0.0) continue;
            vec3 c2 = vec3(0.5, 0.45, 0.38) * (0.5 + 0.5 * hy);
            // flecks of broken glass catching the light
            c2 += GOLD_HOT * step(0.985, hash13(floor(p * 9.0) + vec3(floor(Time * 8.0)))) * 3.0;
            float ab = dens * dt / max(R, 1.0) * 2.2;
            acc += c2 * ab * trans;
            trans *= exp(-ab * 1.2);
        }
    }
    return vec4(acc, trans);
}

// ------------------------------------------------------------------ crystal shards and pillars of light
vec3 crystals(vec3 rd, float sceneT, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 128; i++) {
        if (i >= NBolt) break;
        vec4 a = fx(B_BOLT + i, 0), b = fx(B_BOLT + i, 1), c = fx(B_BOLT + i, 2);
        vec3 head = a.xyz, tail = b.xyz;
        float rad = a.w, kind = c.x, life = c.y;
        if (life <= 0.0) continue;
        vec3 r = raySegment(vec3(0.0), rd, tail, head);
        if (r.z > sceneT + rad * 2.0) continue;
        float px = r.z * 0.0012;
        if (kind > 0.5 && kind < 1.5) {
            // a pillar of light: an ivory core in a gold sheath, flickering
            float w = max(rad * 0.35, px);
            float core = exp(-(r.x * r.x) / (w * w));
            float sheath = exp(-r.x / (rad * 1.5 + px));
            float flick = 0.8 + 0.2 * sin(Time * 60.0 + r.y * 20.0);
            col += (IVORY * core * 4.0 + GOLD * sheath * 1.2) * flick * life;
        } else {
            // a faceted crystal: a long bipyramid, widest two-thirds along, six facets catching the light
            float along = r.y;
            float prof = along < 0.66 ? along / 0.66 : (1.0 - along) / 0.34;
            float w = max(rad * prof, px);
            if (r.x > w * 2.5) continue;
            vec3 axis = normalize(head - tail + vec3(1.0e-5));
            vec3 ref = abs(axis.y) > 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
            vec3 side = normalize(cross(axis, ref));
            vec3 up2 = cross(axis, side);
            vec3 pt = rd * r.z - (tail + (head - tail) * along);
            float ang = atan(dot(pt, up2), dot(pt, side));
            float facet = floor((ang + PI) / (PI / 3.0));
            float shade = 0.55 + 0.45 * fract(sin(facet * 12.9898 + float(i)) * 43758.5453);
            float body = 1.0 - smoothstep(w * 0.6, w, r.x);
            float edge = exp(-pow((r.x - w * 0.85) / (w * 0.15 + px), 2.0));
            col += (mix(GOLD_DEEP, GOLD_HOT, shade) * body * 1.4 + IVORY * edge * 1.6) * life;
            col += GOLD * exp(-r.x / (w * 3.0 + px)) * 0.25 * life;
            darken *= 1.0 - 0.2 * body * life;
        }
    }
    return col;
}

// ------------------------------------------------------------------ domes: gold lattice, and the clock-face sphere of a stop
vec3 domes(vec3 rd, float sceneT, vec3 wp, bool hasSurface, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        if (i >= NShell) break;
        vec4 a = fx(B_SHELL + i, 0), b = fx(B_SHELL + i, 1);
        float R = a.w, strength = b.y, kind = b.z;
        if (R <= 0.0 || strength <= 0.0) continue;
        vec2 h = sphereHit(vec3(0.0), rd, a.xyz, R);
        for (int s = 0; s < 2; s++) {
            float t = s == 0 ? h.x : h.y;
            if (t <= 0.0 || t > sceneT) continue;
            vec3 p = rd * t - a.xyz;
            if (p.y < -1.5 && kind < 1.5) continue;
            vec3 n = normalize(p);
            float fres = pow(1.0 - abs(dot(n, rd)), 2.0);
            float back = s == 1 ? 0.45 : 1.0;
            if (kind > 1.5) {
                // a time stop: meridians on the hours, a ticked equator, and one hand sweeping round
                float az = atan(n.z, n.x) / (2.0 * PI) + 0.5;
                float mer = notch(fract(az * 12.0 + 0.5) - 0.5, 0.03);
                float eq = notch(n.y, 0.02);
                float ticks = notch(fract(az * 60.0 + 0.5) - 0.5, 0.08) * notch(n.y, 0.08);
                float hand = notch(fract(az - Time * 0.08 + 0.5) - 0.5, 0.006);
                col += (GOLD * (mer * 0.8 + eq + ticks) * 1.4 + GOLD_HOT * hand * 2.0 + GOLD * fres * 0.6) * strength * back;
            } else {
                float lat = lattice(n, 8.0);
                col += (GOLD * lat * 1.6 + GOLD_HOT * lat * fres * 1.2 + GOLD * fres * 0.35) * strength * back;
            }
        }
        if (hasSurface) {
            vec3 q = wp - a.xyz;
            float rr = length(q.xz);
            float front = exp(-pow((rr - R) / 0.5, 2.0)) * step(abs(q.y), R * 0.4 + 3.0);
            col += GOLD * front * 1.6 * strength;
        }
    }
    return col;
}

// ------------------------------------------------------------------ gathering power: a dial closing on a point
vec3 gathers(vec2 p, float aspect, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        if (i >= NCharge) break;
        vec4 a = fx(B_CHARGE + i, 0);
        float amt = a.w;
        if (amt <= 0.0 || inFront(a.xyz) < 0.5) continue;
        vec2 c = toScreen(a.xyz);
        vec2 d = (p - c) * vec2(aspect, 1.0);
        float r = length(d), th = atan(d.y, d.x);
        float sc = clamp(4.0 / max(length(a.xyz), 1.0), 0.15, 1.5);
        float R = (0.22 - 0.16 * amt) * sc;
        float px = 0.0016 + 0.0006 * sc;
        float dial = exp(-pow((r - R) / px, 2.0));
        float ticks = notch(fract(th / (2.0 * PI) * 12.0 + 0.5) - 0.5, 0.03) * step(R * 0.85, r) * step(r, R);
        // two hands sweeping shut on each other
        float h1 = Time * 3.0 + amt * 4.0, h2 = -Time * 3.0 + PI - amt * 4.0;
        float d1 = abs(mod(th - h1 + PI, 2.0 * PI) - PI), d2 = abs(mod(th - h2 + PI, 2.0 * PI) - PI);
        float hands = (exp(-pow(d1 * r / px, 2.0)) + exp(-pow(d2 * r / px, 2.0))) * step(r, R);
        col += GOLD * (dial * 1.6 + ticks * 1.2 + hands * 1.8) * amt;
        float core = exp(-r / (0.006 + 0.02 * sc * (1.0 - 0.6 * amt)));
        col += GOLD_HOT * core * amt * 2.5;
        darken *= 1.0 - 0.3 * amt * smoothstep(R, R * 3.0, r);
    }
    return col;
}

// ------------------------------------------------------------------ Heaven Ascension: a column of light and turning halos
vec3 halos(vec3 rd, float sceneT, inout float darken) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= NAura) break;
        vec4 a = fx(B_AURA + i, 0), b = fx(B_AURA + i, 1);
        float amt = b.x;
        if (amt <= 0.0) continue;
        vec3 base = a.xyz;
        float H = a.w;
        vec3 s = raySegment(vec3(0.0), rd, base - vec3(0.0, H * 0.2, 0.0), base + vec3(0.0, H * 1.6, 0.0));
        if (s.z < sceneT + 1.0) {
            float column = exp(-s.x / 0.35) * (0.4 + 0.6 * (1.0 - s.y));
            float motes = step(0.97, hash13(floor(rd * s.z * 6.0 + vec3(0.0, -Time * 6.0, 0.0))));
            col += (GOLD * column * 0.5 + GOLD_HOT * motes * exp(-s.x / 0.6) * 1.2) * amt;
        }
        for (int k = 0; k < 3; k++) {
            float y = k == 0 ? H * 1.1 : (k == 1 ? H * 1.22 : 0.05);
            float R = k == 0 ? 0.42 : (k == 1 ? 0.62 : 0.85);
            vec3 cen = base + vec3(0.0, y, 0.0);
            if (abs(rd.y) < 1.0e-4) continue;
            float t = cen.y / rd.y;
            if (t <= 0.0 || t > sceneT + 0.3) continue;
            vec3 q = rd * t - cen;
            float rr = length(q.xz);
            float w = max(0.025, t * 0.0015);
            float ring = exp(-pow((rr - R) / w, 2.0));
            float ang = atan(q.z, q.x) + Time * (k == 1 ? -0.8 : 0.6);
            float gaps = 0.6 + 0.4 * step(0.25, fract(ang / (2.0 * PI) * (k == 1 ? 8.0 : 6.0)));
            col += GOLD_HOT * ring * gaps * 1.8 * amt;
        }
    }
    return col;
}

// ------------------------------------------------------------------ those held by Reality Collapse
vec3 marks(vec3 rd, float sceneT, vec3 wp, bool hasSurface) {
    vec3 col = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= NMark) break;
        vec4 a = fx(B_MARK + i, 0);
        if (a.w <= 0.0) continue;
        if (hasSurface) {
            vec3 q = wp - a.xyz;
            if (abs(q.y) < 1.5) {
                float ang = Time * 0.5;
                vec2 u = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * q.xz;
                float dia = abs(u.x) + abs(u.y);
                float sq = max(abs(u.x), abs(u.y));
                float px = max(fwidth(dia), 0.02);
                float l = line(dia - 1.3, px) + 0.6 * line(dia - 0.95, px) + 0.5 * line(sq - 0.9, px);
                col += GOLD * l * 1.6 * a.w;
            }
        }
        vec3 s = raySegment(vec3(0.0), rd, a.xyz, a.xyz + vec3(0.0, 8.0, 0.0));
        if (s.z < sceneT) col += GOLD_HOT * exp(-s.x / 0.05) * (1.0 - s.y) * 0.9 * a.w;
    }
    return col;
}

// ------------------------------------------------------------------ fissures: gold seams opened in the ground
vec3 fissures(vec3 wp, bool hasSurface) {
    if (!hasSurface) return vec3(0.0);
    vec3 col = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= NTrench) break;
        vec4 a = fx(B_TRENCH + i, 0), b = fx(B_TRENCH + i, 1), c = fx(B_TRENCH + i, 2);
        float age = a.w;
        if (age < 0.0) continue;
        vec3 D = b.xyz;
        float L = b.w, W = c.x, speed = max(c.y, 0.1);
        vec3 q = wp - a.xyz;
        if (abs(q.y) > 20.0) continue;
        vec2 dir = normalize(D.xz + vec2(1.0e-5));
        float al = dot(q.xz, dir);
        float front = min(L, age * speed);
        if (al < 0.0 || al > front) continue;
        vec2 nd = vec2(-dir.y, dir.x);
        float zig = (noise3(vec3(al * 0.9, 3.0, 1.0)) - 0.5) * (W * 0.6 + 0.6);
        float off = dot(q.xz, nd) - zig;
        float since = age - al / speed;
        float hot = exp(-since * 1.5);
        float life = 1.0 - smoothstep(3.0, 8.0, since);
        float px = max(fwidth(off), 0.02);
        float seam = exp(-pow(off / (px + 0.08), 2.0));
        float glow = exp(-abs(off) / (W * 0.4 + 0.6));
        col += (mix(GOLD, IVORY, hot) * seam * (2.0 + 5.0 * hot) + GOLD_DEEP * glow * 0.8) * life;
        col += IVORY * exp(-pow((al - front) / 1.5, 2.0)) * exp(-pow(off / 1.2, 2.0)) * 3.0 * step(age * speed, L + 4.0);
    }
    return col;
}

// ------------------------------------------------------------------ Reality Collapse
vec3 collapseSky(vec3 rd) {
    float up = clamp(rd.y, -0.2, 1.0);
    vec3 sky = mix(vec3(0.10, 0.06, 0.02), vec3(0.03, 0.02, 0.06), smoothstep(-0.05, 0.7, up));
    vec3 n = rd;
    float ang = Time * 0.03;
    n.xz = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * n.xz;
    sky += GOLD * lattice(n, 6.0) * (0.35 + 0.4 * smoothstep(0.0, 0.6, up));
    sky += GOLD * exp(-abs(rd.y) * 18.0) * 0.45;
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
        col = mix(col, collapseSky(rd), s);
    } else if (hasSurface) {
        float inside = 1.0 - smoothstep(R - 1.0, R + 1.0, length(wp - C));
        float l = luminance(col);
        vec3 graded = mix(vec3(l), col, 0.35) * vec3(1.1, 0.95, 0.7);
        col = mix(col, graded, inside * s);
        // the ground becomes a grid of overwritten reality, pulses running out from the centre
        vec3 q = wp - C;
        vec2 g = abs(fract(q.xz / 3.0) - 0.5);
        float gpx = max(fwidth(q.x / 3.0), 0.01) * 1.5;
        float grid = 1.0 - smoothstep(0.0, gpx, min(g.x, g.y));
        float rr = length(q.xz);
        float pulse = exp(-pow(fract(rr / 18.0 - Time * 0.3) - 0.5, 2.0) * 40.0);
        col += GOLD * grid * (0.35 + 1.2 * pulse) * inside * s * step(abs(q.y), 6.0);
        col += GOLD_HOT * exp(-pow((rr - R) / 1.0, 2.0)) * 2.0 * s * step(abs(q.y), 8.0);
    }
    for (int k = 0; k < 2; k++) {
        float t = k == 0 ? h.x : h.y;
        if (t <= 0.0 || t > sceneT) continue;
        vec3 p = rd * t - C;
        if (p.y < -1.0) continue;
        vec3 n = normalize(p);
        float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
        col += GOLD * (lattice(n, 10.0) * 1.2 + fres * 0.6) * s * (camIn > 0.5 ? 0.25 : 1.0);
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

    // the world drains toward gold while he gathers himself (and while time is held)
    if (Dark.x > 0.0) {
        float l = luminance(col);
        vec3 dk = mix(vec3(l), col, 0.3) * Dark.yzw * (hasSurface ? 0.5 : 0.3);
        col = mix(col, dk, Dark.x);
    }
    col = domain(rd, sceneT, wp, hasSurface, col, darken);

    vec3 add = vec3(0.0);
    add += dials(wp, hasSurface);
    add += fissures(wp, hasSurface);
    add += marks(rd, sceneT, wp, hasSurface);
    add += domes(rd, sceneT, wp, hasSurface, darken);
    add += halos(rd, sceneT, darken);
    add += crystals(rd, sceneT, darken);
    add += gathers(uv, aspect, darken);
    add += seals(uv, aspect, rd, sceneT, darken);

    col = col * darken + add;
    vec4 ds = dust(rd, sceneT);
    col = col * ds.a + ds.rgb;

    fragColor = vec4(max(col, vec3(0.0)), hasSurface ? min(sceneT, 4000.0) : 4000.0);
}
