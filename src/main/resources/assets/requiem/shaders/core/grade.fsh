#version 150
// Requiem Rose's lens: the last look of every frame. Space bent round slashes and the rift, the screen torn along a
// cut, zoom blur, colour fringing, a sky turned rose-black, the rose grade, impact frames, speed lines, flash,
// vignette, grain and letterbox.
uniform sampler2D Scene;
uniform sampler2D Depth;
uniform sampler2D Warp;
uniform vec2 Res;
uniform float Time;
uniform vec4 Flash;
uniform float Chroma;
uniform vec3 Zoom;
uniform vec4 Vignette;
uniform float Bars;
uniform float Grain;
uniform float Desat;
uniform vec4 Tint;
uniform vec2 Sky;
uniform vec4 Impact;
uniform vec3 Accent;
uniform vec4 Split;
uniform vec3 Lines;
uniform float WarpOn;
uniform vec2 Near;
in vec2 uv;
out vec4 fragColor;

float h21(vec2 p) { p = fract(p * vec2(233.34, 851.73)); p += dot(p, p + 23.45); return fract(p.x * p.y); }
float vn(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), u.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) { float s = 0.0, a = 0.5; for (int i = 0; i < 5; i++) { s += a * vn(p); p = p * 2.03 + 1.7; a *= 0.5; } return s; }
vec3 rose(float h) {
    h = clamp(h, 0.0, 1.0);
    vec3 c0 = vec3(0.10, 0.0, 0.04), c1 = vec3(0.70, 0.0, 0.30), c2 = vec3(1.0, 0.30, 0.62), c3 = vec3(1.0, 0.82, 0.92), c4 = vec3(1.0);
    if (h < 0.25) return mix(c0, c1, h / 0.25);
    if (h < 0.55) return mix(c1, c2, (h - 0.25) / 0.30);
    if (h < 0.82) return mix(c2, c3, (h - 0.55) / 0.27);
    return mix(c3, c4, (h - 0.82) / 0.18);
}
float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
float lin(float d) { float z = d * 2.0 - 1.0; return 2.0 * Near.x * Near.y / (Near.y + Near.x - z * (Near.y - Near.x)); }

vec3 scene(vec2 p) {
    vec3 c;
    if (Chroma > 0.001) {
        vec2 dir = (p - 0.5) * Chroma * 0.012;
        c = vec3(texture(Scene, p + dir).r, texture(Scene, p).g, texture(Scene, p - dir).b);
    } else {
        c = texture(Scene, p).rgb;
    }
    return c;
}

float edges(vec2 p) {
    vec2 t = 1.0 / Res;
    float c = lin(texture(Depth, p).r);
    float dd = 0.0, dl = 0.0;
    float l0 = lum(texture(Scene, p).rgb);
    for (int i = 0; i < 4; i++) {
        vec2 o = (i == 0 ? vec2(1, 0) : i == 1 ? vec2(-1, 0) : i == 2 ? vec2(0, 1) : vec2(0, -1)) * t * 1.5;
        dd += abs(lin(texture(Depth, p + o).r) - c);
        dl += abs(lum(texture(Scene, p + o).rgb) - l0);
    }
    return clamp(dd / max(c, 0.5) * 3.5 + dl * 1.6, 0.0, 1.0);
}

vec3 impact(int mode, vec2 p, vec3 c, float side) {
    float e = edges(p);
    float l = lum(c);
    vec2 ctr = Impact.zw;
    float rd = length((p - ctr) * vec2(Res.x / Res.y, 1.0));
    if (mode == 5) mode = side > 0.0 ? 2 : 1;
    if (mode == 1) {
        // petal negative: the picture's light inverted onto the rose ramp, cut to four tones, inked edges
        float v = floor((1.0 - l) * 4.0) / 3.0;
        return mix(rose(0.15 + v * 0.8), vec3(0.0), e);
    }
    if (mode == 2) {
        // ink wash: paper and ink, a rose bloom of light spilling from the point of contact
        vec3 paper = vec3(0.98, 0.95, 0.96);
        float ink = max(step(l, 0.28), e);
        vec3 col = mix(paper, vec3(0.02, 0.0, 0.02), ink);
        float splash = exp(-rd * 5.0) * (1.0 - ink);
        return mix(col, Accent, splash * 0.85);
    }
    if (mode == 3) {
        // the void: black, every edge burning in the accent, light streaming out of the hit
        float ang = atan(p.y - ctr.y, (p.x - ctr.x) * Res.x / Res.y);
        float rays = pow(vn(vec2(ang * 18.0, 0.0) + floor(Time * 12.0)), 6.0) * smoothstep(0.05, 0.4, rd);
        return Accent * (e * 1.4 + rays * 0.8) + vec3(1.0) * e * e * 0.5;
    }
    if (mode == 4) {
        // blowout: everything but the darkest lines burnt to blush white
        vec3 col = mix(vec3(1.0, 0.93, 0.97), rose(0.3), smoothstep(0.35, 0.8, e));
        return mix(col, vec3(0.05, 0.0, 0.03), step(l, 0.08));
    }
    return c;
}

void main() {
    vec2 p = uv;
    float aspect = Res.x / Res.y;
    // the screen torn along a cut: two halves sliding apart, a seam of white light
    float side = 0.0, seam = 0.0;
    if (Split.w > 0.001) {
        vec2 n = normalize(Split.xy);
        vec2 t = vec2(-n.y, n.x);
        float sd = dot((p - 0.5) * vec2(aspect, 1.0), n) - Split.z;
        side = sign(sd);
        p += t * side * Split.w * 0.035 * vec2(1.0 / aspect, 1.0);
        seam = exp(-abs(sd) * 140.0) * Split.w;
    }
    // space bending round slashes and the rift
    if (WarpOn > 0.5) {
        vec4 w = texture(Warp, p);
        p += vec2(w.r - w.g, w.b - w.a) / 8.0;
    }
    vec3 c;
    if (Zoom.z > 0.001) {
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 12; i++) {
            float k = float(i) / 11.0;
            acc += scene(mix(p, Zoom.xy, k * Zoom.z * 0.18));
        }
        c = acc / 12.0;
    } else {
        c = scene(p);
    }
    float depth = texture(Depth, p).r;
    // the sky of the Last Bloom: rose and black, slow storm light
    if (Sky.x > 0.001) {
        float isSky = step(0.99999, depth);
        vec2 q = vec2(p.x * aspect, p.y) * 2.2;
        float cloud = fbm(q + vec2(Time * 0.03, -Time * 0.02) + fbm(q * 1.7 - Time * 0.05));
        vec3 sky = mix(vec3(0.01, 0.0, 0.015), vec3(0.42, 0.02, 0.18), pow(cloud, 2.2) * (0.4 + 0.6 * (1.0 - p.y)));
        sky += rose(0.6) * pow(max(0.0, cloud - 0.55), 3.0) * 4.0;
        c = mix(c, sky, isSky * Sky.x);
        // the land beneath darkens and takes the sky's colour
        vec3 dim = c * vec3(0.55, 0.32, 0.45);
        c = mix(c, dim, (1.0 - isSky) * Sky.y);
    }
    float l = lum(c);
    c = mix(c, vec3(l), Desat);
    c = mix(c, rose(l * 0.9 + 0.08) * (0.4 + l), Tint.a * 0.6);
    // impact frames
    int mode = int(Impact.x + 0.5);
    if (mode > 0 && Impact.y > 0.001) {
        c = mix(c, impact(mode, p, c, side), Impact.y);
    }
    c += vec3(1.0, 0.92, 0.96) * seam;
    // speed lines converging on a point
    if (Lines.x > 0.001) {
        vec2 d = (p - Lines.yz) * vec2(aspect, 1.0);
        float ang = atan(d.y, d.x);
        float bin = floor(ang * 70.0 / 6.28318);
        float rnd = h21(vec2(bin, floor(Time * 14.0)));
        float lineMask = step(0.62, rnd) * smoothstep(0.28 + 0.2 * h21(vec2(bin, 3.0)), 0.75, length(d));
        float thin = 1.0 - smoothstep(0.0, 0.5, abs(fract(ang * 70.0 / 6.28318) - 0.5) * 2.0 - 0.3);
        c = mix(c, vec3(1.0, 0.96, 0.98), lineMask * thin * Lines.x * 0.8);
    }
    c = mix(c, Flash.rgb, clamp(Flash.a, 0.0, 1.0));
    float r = length((uv - 0.5) * vec2(aspect, 1.0));
    c = mix(c, Vignette.rgb, Vignette.a * smoothstep(0.35, 1.05, r));
    c += (h21(uv * Res + fract(Time * 13.7) * 91.0) - 0.5) * Grain * 0.12;
    float bar = Bars * 0.115;
    if (uv.y < bar || uv.y > 1.0 - bar) c = vec3(0.0);
    fragColor = vec4(c, 1.0);
}
