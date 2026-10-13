#version 150
// A cut of light: a crescent whose outer rim is the white-hot edge, thick at the head and tapering to a point at the
// tail, its body streaming with energy; it dies by burning away from the inside.
in vec4 vColor;
in vec2 vUV;
in float vDepth;
uniform float Time;
out vec4 fragColor;
float h21(vec2 p) { p = fract(p * vec2(233.34, 851.73)); p += dot(p, p + 23.45); return fract(p.x * p.y); }
float vn(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), u.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), u.x), u.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { s += a * vn(p); p = p * 2.02 + vec2(3.1, 1.7); a *= 0.5; }
    return s;
}
// heat 0..1: abyssal crimson, the aura magenta, hot rose, blush, white
vec3 rose(float h) {
    h = clamp(h, 0.0, 1.0);
    vec3 c0 = vec3(0.16, 0.0, 0.05), c1 = vec3(0.70, 0.0, 0.30), c2 = vec3(1.0, 0.30, 0.62), c3 = vec3(1.0, 0.78, 0.90), c4 = vec3(1.0, 0.97, 0.99);
    if (h < 0.25) return mix(c0, c1, h / 0.25);
    if (h < 0.55) return mix(c1, c2, (h - 0.25) / 0.30);
    if (h < 0.82) return mix(c2, c3, (h - 0.55) / 0.27);
    return mix(c3, c4, (h - 0.82) / 0.18);
}
void main() {
    float fade = vColor.r, heat = vColor.g, seed = vColor.b * 97.0, kind = vColor.a;
    float x = vUV.x, y = vUV.y;
    bool blade = kind > 0.5;
    // the crescent: outer edge at y = 1, thickness swelling toward the head (x = 1) and pinched at both ends
    float body = pow(clamp(sin(3.14159 * clamp(x, 0.0, 1.0)), 0.0, 1.0), blade ? 0.35 : 0.6) * (blade ? 1.0 : mix(0.45, 1.0, x));
    float d = (1.0 - y) / max(body, 1e-3);
    if (d > 1.0) discard;
    float flow = fbm(vec2(x * 7.0 - Time * 5.0 + seed, y * 2.0 + seed));
    float streak = fbm(vec2(x * 26.0 - Time * 13.0, y * 6.0 + seed * 1.3));
    float erode = fbm(vec2(x * 5.0 + seed, y * 3.0 - Time * 0.7));
    float alive = blade ? 1.0 : smoothstep(0.0, 0.12, erode + 0.25 * (1.0 - d) - (1.0 - fade) * 1.15 + 0.12);
    float edge = exp(-d * (blade ? 14.0 : 8.0));
    float inner = 1.0 - smoothstep(0.45, 1.0, d);
    // the body: hot rose, streaming; brightest toward the edge
    float h = edge * 0.9 + inner * (0.48 + 0.3 * streak) * (1.0 - 0.35 * d) + 0.1 * flow;
    if (blade) {
        // the scythe's blade is pinkish white: blush through its body, white at the edge, a pale shimmer of facets
        float facet = smoothstep(0.9, 1.0, fract(x * 9.0 + y * 2.5 + Time * 0.4));
        h = 0.68 + 0.22 * inner * (0.6 + 0.4 * streak) + edge * 0.35 + facet * 0.12 * inner;
        h *= 0.95 + 0.05 * sin(Time * 6.0 + x * 12.0);
    }
    h *= mix(0.8, 1.15, heat);
    float a = clamp((edge + inner * (0.85 + 0.35 * flow)) * alive * min(fade * 1.4, 1.0), 0.0, 1.0);
    vec3 col = rose(h) * a * 1.25;
    col += vec3(1.0, 0.92, 0.96) * edge * edge * heat * alive * fade;
    fragColor = vec4(col, a);
}
