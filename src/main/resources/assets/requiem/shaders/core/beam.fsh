#version 150
// A ray of rose light: a white core, a body twisting with currents, rings of pressure riding down it.
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
    float fade = vColor.r, heat = vColor.g, seed = vColor.b * 97.0, len = vColor.a * 64.0;
    float x = vUV.x * len, y = vUV.y;   // x in blocks along, y -1..1 across
    float r = abs(y);
    float core = exp(-r * r * 22.0);
    float body = exp(-r * r * 3.2);
    float twist = fbm(vec2(x * 0.7 - Time * 11.0, y * 2.6 + seed + sin(x * 0.4 - Time * 6.0)));
    float pulse = 0.5 + 0.5 * sin(x * 1.3 - Time * 30.0);
    float bands = smoothstep(0.75, 1.0, fract(x * 0.18 - Time * 2.4)) * body;
    float ends = smoothstep(0.0, 0.6, x) * smoothstep(0.0, 1.2, len - x);
    float h = core * 1.1 + body * (0.35 + 0.45 * twist) + bands * 0.35 + core * pulse * 0.1;
    h *= mix(0.8, 1.15, heat);
    float a = clamp((core + body * (0.35 + 0.65 * twist) + bands * 0.4) * fade * ends, 0.0, 1.0);
    vec3 col = rose(h) * a + vec3(1.0, 0.95, 0.98) * core * core * fade * ends * heat;
    fragColor = vec4(col, a);
}
