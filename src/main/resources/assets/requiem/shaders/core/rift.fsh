#version 150
// A tear in space: a lens of the void (black, a nebula of crimson drifting deep inside, far stars) held open by a rim of
// burning rose. Mode 0 draws it blended into the scene; Mode 1 adds only the light of the rim (for the glow).
in vec4 vColor;
in vec2 vUV;
in float vDepth;
uniform float Time;
uniform float Mode;
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
    float open = vColor.r, fade = vColor.g, seed = vColor.b * 97.0;
    vec2 p = vUV * 2.0 - 1.0;            // x along the cut, y across
    float jag = (fbm(vec2(p.x * 6.0 + seed, Time * 1.5)) - 0.5) * 0.18 * open;
    float half_ = open * pow(max(0.0, 1.0 - p.x * p.x), 0.8) * 0.3 + 0.002;
    float m = abs(p.y + jag * (1.0 - abs(p.x))) / half_;
    if (m > 3.0) discard;
    float rim = exp(-abs(m - 1.0) * (m < 1.0 ? 9.0 : 4.0));
    float crackle = fbm(vec2(p.x * 22.0 + Time * 3.0, m * 3.0 + seed));
    rim *= 0.6 + 0.8 * crackle;
    float glowOut = exp(-(m - 1.0) * 2.4) * step(1.0, m) * 0.35;
    if (Mode > 0.5) {
        float a = clamp((rim * 1.2 + glowOut) * fade, 0.0, 1.0);
        float h = 0.45 + rim * 0.6;
        fragColor = vec4(rose(h) * a, a);
        return;
    }
    if (m > 1.05) discard;
    vec2 q = p * vec2(1.0, 1.0 / max(open, 0.05));
    float swirl = fbm(vec2(q.x * 2.0 + Time * 0.15, q.y * 1.6 - Time * 0.25) + fbm(q * 3.0 + Time * 0.1));
    vec3 deep = mix(vec3(0.004, 0.0, 0.01), vec3(0.20, 0.0, 0.09), pow(swirl, 2.6));
    float star = step(0.985, h21(floor(gl_FragCoord.xy * 0.5) + seed)) * (0.5 + 0.5 * sin(Time * 6.0 + seed + gl_FragCoord.x));
    deep += vec3(1.0, 0.8, 0.95) * star * 0.8;
    vec3 c = mix(deep, rose(0.7), smoothstep(0.75, 1.0, m) * 0.6);
    float a = (1.0 - smoothstep(0.96, 1.05, m)) * fade;
    fragColor = vec4(c, a);
}
