#version 150
// Points of light. Kind (alpha): 0 a flare with a long anamorphic streak, 0.33 a spark stretched along its flight,
// 0.66 an ember mote, 1 a puff of dark smoke (drawn with the smoke program).
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
    vec2 p = vUV * 2.0 - 1.0;
    float r = length(p);
    if (kind > 0.83) {
        float n = fbm(p * 2.5 + vec2(seed, -Time * 0.6));
        float a = smoothstep(1.0, 0.2, r + (n - 0.5) * 0.6) * fade * 0.75;
        vec3 c = mix(vec3(0.02, 0.0, 0.025), vec3(0.16, 0.01, 0.09), n * (1.0 - r));
        fragColor = vec4(c, a);
        return;
    }
    float a;
    float h;
    if (kind < 0.16) {
        // the quad is three times as wide as tall: room for the anamorphic streak
        vec2 q = vec2(p.x * 3.0, p.y);
        float rq = length(q);
        float core = exp(-rq * rq * 40.0);
        float halo = exp(-rq * 3.6) * (1.0 - smoothstep(0.7, 1.0, rq));
        float streak = exp(-abs(q.y) * 55.0) * exp(-abs(q.x) * 0.9) * (1.0 - smoothstep(2.3, 3.0, abs(q.x)));
        float rays = pow(max(0.0, 1.0 - abs(q.x * q.y) * 30.0), 6.0) * exp(-rq * 2.5) * (1.0 - smoothstep(0.8, 1.0, rq));
        a = clamp(core + halo * 0.6 + streak * 0.7 + rays * 0.4, 0.0, 1.0) * fade;
        h = core * 1.0 + halo * 0.55 + streak * 0.45 + rays * 0.3;
    } else if (kind < 0.5) {
        float across = exp(-p.y * p.y * 14.0);
        float along = smoothstep(-1.0, -0.2, p.x) * (1.0 - smoothstep(0.75, 1.0, p.x));
        a = across * along * fade;
        h = across * (0.5 + 0.5 * smoothstep(-0.4, 0.9, p.x));
    } else {
        a = exp(-r * r * 6.0) * fade;
        h = exp(-r * r * 14.0);
    }
    h *= mix(0.65, 1.1, heat);
    fragColor = vec4(rose(h) * a, a);
}
