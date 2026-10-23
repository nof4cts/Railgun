#version 150

// Final pass, written back over Minecraft's frame:
// smear -> punch-in zoom -> shockwave refraction -> chromatic aberration -> bloom -> exposure -> roll-off ->
// flash / afterimage -> grade -> vignette -> grain -> IMPACT FRAMES -> blackout.
//
// The World Over Heaven's impact frames. A blow does not smear the picture, it breaks it: the frame shatters
// like a mirror and every pane slips a little out of place, then the world is redrawn in his colours for a
// stutter of stark frames. Styles (Impact.x):
//   1 SHATTERED GLASS  the picture in gilt two-tone, panes offset, seams of light between them
//   2 GOLD NEGATIVE    light and dark swapped onto a black-to-gold-to-ivory ramp
//   3 GILT             umber and gold split at the frame's own threshold, ivory highlights, black ink
//   4 ENGRAVING        black, the picture cut in fine gold lines whose weight follows the light
//   5 WHITE FLARE      ivory, outlines in dark gold, a seal burning at the point of the blow
//   6 THE SEAL         black, lit things in deep gold, a great geometric seal stamped over the hit
// Silhouettes are traced from the depth buffer as well as brightness, so outlines stay clean and continuous.

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

vec2 hash22(vec2 p, float s) {
    return hash33(vec3(p, s)).xy;
}

/** The frame as glass: x = distance to the nearest pane edge, y = pane id, zw = the pane's own random vector. */
vec4 panes(vec2 q, float seed) {
    vec2 c = floor(q), f = fract(q);
    float d1 = 8.0, d2 = 8.0;
    vec2 best = vec2(0.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 r = g + hash22(c + g, seed) - f;
            float d = dot(r, r);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                best = c + g;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    vec2 rv = hash22(best, seed + 17.0) - 0.5;
    return vec4(sqrt(d2) - sqrt(d1), hash12(best + vec2(seed)), rv);
}

float ngon(float r, float th, float n, float R, float rot) {
    float a = mod(th + rot, 6.2831853 / n) - 3.14159265 / n;
    return r * cos(a) - R * cos(3.14159265 / n);
}

/** A geometric seal centred on c (screen uv): circles, a hexagram, a hexagon, dial ticks. */
float seal(vec2 p, vec2 c, float aspect, float R, float spin) {
    vec2 d = (p - c) * vec2(aspect, 1.0);
    float r = length(d), th = atan(d.y, d.x);
    float w = 0.0025;
    float s = exp(-pow((r - R) / w, 2.0)) + 0.7 * exp(-pow((r - R * 1.1) / w, 2.0));
    s += exp(-pow(ngon(r, th, 3.0, R * 0.86, spin) / w, 2.0));
    s += exp(-pow(ngon(r, th, 3.0, R * 0.86, spin + 1.0471976) / w, 2.0));
    s += 0.8 * exp(-pow(ngon(r, th, 6.0, R * 0.46, -spin * 1.5) / w, 2.0));
    float tick = 1.0 - smoothstep(0.0, 0.012, abs(mod(th - spin * 0.3, 6.2831853 / 36.0) - 3.14159265 / 36.0));
    s += 0.8 * tick * step(R, r) * step(r, R * 1.1);
    return clamp(s, 0.0, 1.0);
}

/** Engraving: fine parallel lines whose weight follows the light, cut along direction dir. */
float engrave(vec2 p, vec2 dir, float aspect, float lum) {
    vec2 d = normalize(dir * vec2(aspect, 1.0) + vec2(1.0e-5));
    vec2 n = vec2(-d.y, d.x);
    float across = dot(p * vec2(aspect, 1.0), n) * ScreenSize.y / 4.0;
    float f = abs(fract(across) - 0.5) * 2.0;
    float weight = clamp(lum, 0.0, 1.0) * 0.9;
    return 1.0 - smoothstep(weight, weight + 0.15, f);
}

void main() {
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 p = uv;
    float mode = Impact.x;
    float amt = Impact.y;
    vec2 ic = Impact.zw;

    // the frame shatters: every pane of the picture slips a little out of place
    vec4 pn = vec4(1.0, 0.0, 0.0, 0.0);
    if (mode > 0.5 && amt > 0.0) {
        pn = panes((p - ic) * vec2(aspect, 1.0) * 7.0, ImpactSeed);
        float near = 1.0 - smoothstep(0.0, 0.9, length((p - ic) * vec2(aspect, 1.0)));
        p += pn.zw * 0.03 * amt * (0.4 + 0.6 * near) * (ImpactDir.w > 0.0 ? 1.0 : 0.5);
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
    col = mix(col, vec3(l) * vec3(1.1, 0.98, 0.72), Desaturate);   // desaturate toward gold, not grey
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
        float seam = exp(-pow(pn.x / 0.035, 2.0)) * (ImpactDir.w > 0.0 ? 1.0 : 0.6);
        float core = exp(-r * 6.0);
        vec3 ink = vec3(0.02, 0.015, 0.0);
        vec3 umber = vec3(0.16, 0.09, 0.02);
        vec3 ivory = vec3(1.0, 0.97, 0.9);
        float spin = ImpactSeed + Time * 1.5;
        vec3 f;
        if (mode < 1.5) {          // SHATTERED GLASS
            f = mix(umber, Accent, lit) * (0.85 + 0.3 * pn.y);
            f = mix(f, ink, edge * 0.9);
            f = mix(f, ivory, seam);
        } else if (mode < 2.5) {   // GOLD NEGATIVE
            float inv = 1.0 - clamp(lc, 0.0, 1.0);
            f = mix(ink, Accent, smoothstep(0.1, 0.6, inv));
            f = mix(f, ivory, smoothstep(0.7, 0.95, inv));
            f = mix(f, ivory, edge * 0.8);
            f = mix(f, Accent, seam);
        } else if (mode < 3.5) {   // GILT
            f = mix(umber, Accent, lit);
            f = mix(f, ink, edge * 0.9);
            f = mix(f, ivory, smoothstep(0.75, 0.95, lc) + core * 0.5);
            f = mix(f, ivory, seam * 0.7);
        } else if (mode < 4.5) {   // ENGRAVING
            vec2 dir = ImpactDir.z > 0.5 ? ImpactDir.xy : vec2(0.7071, 0.7071);
            f = mix(ink, Accent, engrave(p, dir, aspect, lc));
            f = mix(f, ivory, edge * 0.85);
            f = mix(f, ivory, seam * 0.5);
        } else if (mode < 5.5) {   // WHITE FLARE
            f = ivory;
            f = mix(f, Accent * 0.45, max(edge, seam));
            f = mix(f, Accent, seal(p, ic, aspect, 0.16, spin));
        } else {                   // THE SEAL
            f = mix(ink, Accent * 0.75, lit);
            f = mix(f, ivory, edge * 0.7);
            f = mix(f, Accent * 1.2, seal(p, ic, aspect, 0.3, spin));
            f = mix(f, ivory, seal(p, ic, aspect, 0.12, -spin * 1.3));
            f += Accent * core * 0.5;
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
