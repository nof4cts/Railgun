#version 150

// Final pass, written back over Minecraft's frame:
// smear -> punch-in zoom -> shockwave refraction -> chromatic aberration -> bloom -> exposure -> roll-off ->
// flash / afterimage -> grade -> vignette -> grain -> IMPACT FRAMES -> blackout.
//
// Impact frames, second generation. A hit is a stutter of stark graphic frames, and now:
//  - the linework is traced from the DEPTH buffer as well as brightness, so silhouettes get clean, continuous
//    manga outlines (bodies against the ground, the ground against the sky) instead of blotchy shapes;
//  - speed lines follow the direction of the blow (parallel streaks) or converge on the hit (radial), per frame;
//  - a smear frame stretches the image along the strike just before contact;
//  - Black Flash has its own frame: black, everything lit cut out in crimson, outlines in white, cracks of
//    distorted space radiating from the point of contact;
//  - a blackout frame (a true black frame with only the brightest light left) for the void before the hit.

#include "common.glsl"

in vec2 uv;
out vec4 fragColor;

uniform sampler2D Hdr;
uniform sampler2D Bloom;
uniform sampler2D Depth;
uniform mat4 InvProj;
uniform vec2 ScreenSize;
uniform float Time;
uniform float BloomStrength;
uniform float Exposure;
uniform float Aberration;
uniform vec4 Shockwave;       // centre uv (xy), radius in screen heights (z), strength (w)
uniform vec4 Flash;           // colour (rgb), amount (a)
uniform float Afterimage;
uniform float Desaturate;
uniform float Vignette;
uniform float Grain;
uniform vec4 Impact;          // mode (x), amount (y), centre uv (zw)
uniform vec4 ImpactDir;       // direction of the blow on screen (xy), directional lines 0..1 (z), cracks 0..1 (w)
uniform float ImpactSeed;
uniform vec3 Accent;
uniform float Punch;
uniform vec4 Smear;           // direction (xy, uv units), amount (z)
uniform float Blackout;       // 0..1

vec3 rolloff(vec3 c) {
    float m = max(c.r, max(c.g, c.b));
    if (m <= 0.8) return c;
    float mapped = 0.8 + 0.2 * (1.0 - exp(-(m - 0.8) / 0.2));
    vec3 scaled = c * (mapped / m);
    float bleach = clamp((m - 1.6) * 0.06, 0.0, 0.7);
    return mix(scaled, vec3(mapped), bleach);
}

float lumAt(vec2 p) {
    return luminance(rolloff(texture(Hdr, p).rgb + texture(Bloom, p).rgb * BloomStrength));
}

float linDepth(vec2 p) {
    // the world pass leaves the distance to the scene in the alpha of its output
    return clamp(texture(Depth, p).a, 0.05, 4000.0);
}

/** Outline strength from depth discontinuities (log depth so near and far both read) and from brightness. */
float inkEdges(vec2 p) {
    vec2 px = 1.25 / ScreenSize;
    float c = log(linDepth(p));
    float dx = abs(log(linDepth(p + vec2(px.x, 0.0))) - c) + abs(log(linDepth(p - vec2(px.x, 0.0))) - c);
    float dy = abs(log(linDepth(p + vec2(0.0, px.y))) - c) + abs(log(linDepth(p - vec2(0.0, px.y))) - c);
    float depthEdge = smoothstep(0.06, 0.18, dx + dy);
    float gx = lumAt(p + vec2(px.x, 0.0)) - lumAt(p - vec2(px.x, 0.0));
    float gy = lumAt(p + vec2(0.0, px.y)) - lumAt(p - vec2(0.0, px.y));
    float lumEdge = smoothstep(0.06, 0.16, length(vec2(gx, gy)));
    return max(depthEdge, lumEdge * 0.8);
}

float radialLines(vec2 p, vec2 c, float aspect, float seed) {
    vec2 d = (p - c) * vec2(aspect, 1.0);
    float r = length(d);
    float a = atan(d.y, d.x) / 6.2831853 + 0.5;
    float n = 240.0;
    float cell = floor(a * n);
    float h = hash12(vec2(cell, seed));
    float w = 0.15 + 0.6 * hash12(vec2(cell, seed + 7.0));
    float f = abs(fract(a * n) - 0.5) * 2.0;
    float line = step(0.55, h) * smoothstep(w, w * 0.6, f);
    float start = 0.16 + 0.35 * hash12(vec2(cell, seed + 3.0));
    return line * smoothstep(start, start + 0.12, r);
}

/** Streaks parallel to the blow: the whole frame rushing past in one direction. */
float directionalLines(vec2 p, vec2 dir, vec2 c, float aspect, float seed) {
    vec2 q = (p - c) * vec2(aspect, 1.0);
    vec2 d = normalize(dir * vec2(aspect, 1.0) + 1.0e-5);
    vec2 n = vec2(-d.y, d.x);
    float across = dot(q, n) * 260.0;
    float along = dot(q, d);
    float cell = floor(across);
    float h = hash12(vec2(cell, seed));
    float w = 0.2 + 0.5 * hash12(vec2(cell, seed + 5.0));
    float f = abs(fract(across) - 0.5) * 2.0;
    float len = 0.25 + 0.6 * hash12(vec2(cell, seed + 9.0));
    float start = (hash12(vec2(cell, seed + 11.0)) - 0.5) * 1.6;
    float seg = smoothstep(start, start + 0.05, along) * smoothstep(start + len, start + len - 0.08, along);
    float clear = smoothstep(0.08, 0.22, length(q));
    return step(0.6, h) * smoothstep(w, w * 0.5, f) * seg * clear;
}

/** Jagged cracks of distorted space radiating from c. */
float cracks(vec2 p, vec2 c, float aspect, float seed) {
    vec2 d = (p - c) * vec2(aspect, 1.0);
    float r = length(d);
    float th = atan(d.y, d.x);
    float best = 0.0;
    for (int k = 0; k < 9; k++) {
        float a0 = hash12(vec2(float(k), seed)) * 6.2831853;
        float jag = (fbm3(vec3(r * 14.0, float(k) * 3.0, seed)) - 0.5) * 0.5;
        float ang = mod(th - a0 - jag + 3.14159, 6.2831853) - 3.14159;
        float len = 0.25 + 0.45 * hash12(vec2(float(k), seed + 1.0));
        float w = 0.004 + 0.006 * (1.0 - r / len);
        best = max(best, exp(-pow(ang * r / w, 2.0)) * step(r, len));
    }
    return best;
}

void main() {
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 p = uv;
    float mode = Impact.x;
    float amt = Impact.y;
    vec2 ic = Impact.zw;

    // impact tear: slices of the image jump sideways
    if (mode > 0.5 && amt > 0.0) {
        float band = floor(p.y * 18.0 + hash12(vec2(ImpactSeed, 1.0)) * 4.0);
        float jump = hash12(vec2(band, ImpactSeed)) - 0.5;
        p.x += jump * step(0.62, abs(jump) * 2.0) * 0.045 * amt;
    }
    if (Shockwave.w > 0.0) {
        vec2 d = (p - Shockwave.xy) * vec2(aspect, 1.0);
        float len = length(d);
        float ring = exp(-pow((len - Shockwave.z) / 0.035, 2.0));
        p -= normalize(d + 1.0e-5) / vec2(aspect, 1.0) * ring * Shockwave.w * 0.05;
    }

    // smear along the blow + punch-in toward the hit, with chromatic split
    vec2 off = (p - 0.5) * Aberration * 0.012;
    vec3 col = vec3(0.0), bloom = vec3(0.0);
    int taps = (Punch > 0.001 || Smear.z > 0.001) ? 12 : 1;
    for (int i = 0; i < 12; i++) {
        if (i >= taps) break;
        float k = float(i) / 11.0;
        vec2 q = mix(p, ic, k * Punch * 0.22) - Smear.xy * Smear.z * (k - 0.2);
        vec3 c;
        c.r = texture(Hdr, q + off).r;
        c.g = texture(Hdr, q).g;
        c.b = texture(Hdr, q - off).b;
        col += c;
        bloom += texture(Bloom, q).rgb;
    }
    col /= float(taps);
    bloom /= float(taps);
    col += bloom * BloomStrength;
    col *= Exposure;
    col = rolloff(col);

    if (Flash.a > 0.0) col = mix(col, Flash.rgb, clamp(Flash.a, 0.0, 1.0));
    if (Afterimage > 0.0) {
        vec3 burned = vec3(1.0) - col * vec3(0.55, 0.75, 1.0);
        col = mix(col, col * 0.55 + burned * 0.25, Afterimage * 0.6);
    }
    float l = luminance(col);
    col = mix(col, vec3(l) * vec3(1.02, 0.98, 0.95), Desaturate);
    vec2 vq = (uv - 0.5) * vec2(aspect, 1.0);
    col *= 1.0 - Vignette * smoothstep(0.35, 1.1, length(vq));
    if (Grain > 0.0) {
        float g = hash12(gl_FragCoord.xy + fract(Time * 13.37) * 371.0) - 0.5;
        col += g * 0.06 * Grain * (0.4 + 0.6 * (1.0 - l));
    }

    // ---------------------------------------------------------------- impact frames
    if (mode > 0.5 && amt > 0.0) {
        float lc = lumAt(p);
        float edge = inkEdges(p);
        float r = length((p - ic) * vec2(aspect, 1.0));
        // adaptive threshold from the frame's own brightness
        float sum = 0.0, sum2 = 0.0;
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 7; i++) {
                float s = lumAt(vec2((float(i) + 0.5) / 7.0, (float(j) + 0.5) / 5.0));
                sum += s;
                sum2 += s * s;
            }
        }
        float mean = sum / 35.0;
        float sd = sqrt(max(sum2 / 35.0 - mean * mean, 0.0));
        float th = clamp(mean + 0.5 * sd, 0.08, 0.92) - 0.05 * exp(-r * 3.0);
        float lit = smoothstep(th - 0.012, th + 0.012, lc);
        float lines = ImpactDir.z > 0.5 ? directionalLines(p, ImpactDir.xy, ic, aspect, ImpactSeed)
                                        : radialLines(p, ic, aspect, ImpactSeed);
        float crk = ImpactDir.w > 0.0 ? cracks(p, ic, aspect, ImpactSeed * 0.37) * ImpactDir.w : 0.0;
        float core = exp(-r * 6.0);
        vec3 f;
        if (mode < 1.5) {          // INK: paper and ink
            float ink = max(1.0 - lit, edge);
            f = vec3(1.0 - ink);
            f = mix(f, vec3(0.0), max(lines * (1.0 - core), crk));
        } else if (mode < 2.5) {   // NEGATIVE
            f = vec3(lit) + Accent * edge * 1.2;
            f = mix(f, vec3(1.0), lines * 0.9);
            f += vec3(1.0) * core * 0.25;
            f = mix(f, Accent, crk);
        } else if (mode < 3.5) {   // TWO-TONE
            f = mix(vec3(0.03, 0.02, 0.0), Accent, lit);
            f = mix(f, vec3(0.0), max(edge, lines) * 0.9);
            f = mix(f, vec3(1.0, 0.97, 0.9), smoothstep(0.75, 0.95, lc) + core * 0.5);
            f = mix(f, vec3(1.0), crk);
        } else if (mode < 4.5) {   // HALFTONE
            vec2 g = gl_FragCoord.xy / 6.0;
            mat2 rot = mat2(0.7071, -0.7071, 0.7071, 0.7071);
            vec2 cell = fract(rot * g) - 0.5;
            float dotR = sqrt(clamp(lc, 0.0, 1.0)) * 0.62;
            float dotM = smoothstep(dotR + 0.06, dotR - 0.06, length(cell));
            f = mix(vec3(0.0), Accent * 1.2, dotM);
            f = mix(f, vec3(1.0), edge * 0.8 + lines * 0.6 + crk);
        } else if (mode < 5.5) {   // WHITE-OUT
            f = vec3(smoothstep(0.03, 0.12, lc));
            f = mix(f, vec3(0.0), max(edge, crk));
            f = mix(f, Accent, lines * 0.7);
        } else {                   // BLACK FLASH: black world, crimson light, white outlines, cracked space
            f = vec3(0.0);
            f = mix(f, vec3(0.95, 0.66, 0.12), lit);
            f = mix(f, vec3(1.0), edge);
            f = mix(f, vec3(1.0, 0.78, 0.30), lines * 0.85);
            f = mix(f, vec3(1.0), crk);
            f += vec3(1.0, 0.82, 0.42) * core * 0.6;
        }
        col = mix(col, clamp(f, 0.0, 1.0), clamp(amt, 0.0, 1.0));
    }

    // blackout: the instant before the hit, nothing but the brightest light survives
    if (Blackout > 0.0) {
        float keep = smoothstep(0.85, 0.98, luminance(col));
        col = mix(col, col * keep, Blackout);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
