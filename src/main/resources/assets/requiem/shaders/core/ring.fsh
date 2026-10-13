#version 150
// A shockwave: a band with a sharp leading edge and a smoky trailing one, torn by noise round its length.
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
    float fade = vColor.r, heat = vColor.g, seed = vColor.b * 97.0, sharp = vColor.a;
    float x = vUV.x, y = vUV.y; // x round (0..1), y across 0 inner .. 1 outer
    float lead = 1.0 - smoothstep(0.78, 0.86 + 0.12 * (1.0 - sharp), y);
    float trail = smoothstep(0.0, 0.8, y);
    float band = lead * trail * trail;
    float tear = fbm(vec2(x * 40.0 + seed, y * 3.0 - Time * 2.0));
    float a = band * smoothstep(0.15, 0.55, tear + 0.25 * y) * fade;
    float h = (0.35 + 0.65 * y * y) * mix(0.7, 1.15, heat) + 0.25 * tear;
    fragColor = vec4(rose(h) * a, a);
}
