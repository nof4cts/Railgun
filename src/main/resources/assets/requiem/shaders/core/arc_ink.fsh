#version 150
// The black shadow laid under every cut of light: smoke-dark, frayed by the same noise, burning away with it.
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
        // smoke-black under the light, frayed by the same noise
        float a = (1.0 - smoothstep(0.55, 1.0, d)) * alive * (0.55 + 0.45 * flow) * min(fade * 1.6, 1.0) * 0.85;
        vec3 c = mix(vec3(0.015, 0.0, 0.02), vec3(0.13, 0.0, 0.07), flow * (1.0 - d));
        fragColor = vec4(c, a);
}
