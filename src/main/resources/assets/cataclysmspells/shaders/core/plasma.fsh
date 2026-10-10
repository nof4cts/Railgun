#version 150

// Procedural plasma (original): 0 star surface + corona, 1 accretion disk, 2 beam core.
// col.rgb = tint, col.a = intensity.

uniform float Mode;
uniform float Time;

in vec2 uv;
in vec4 col;
out vec4 fragColor;

float hash(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i), n100 = hash(i + vec3(1, 0, 0)), n010 = hash(i + vec3(0, 1, 0)), n110 = hash(i + vec3(1, 1, 0));
    float n001 = hash(i + vec3(0, 0, 1)), n101 = hash(i + vec3(1, 0, 1)), n011 = hash(i + vec3(0, 1, 1)), n111 = hash(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y), mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

vec3 fireRamp(float t) {
    vec3 c1 = vec3(0.35, 0.02, 0.0), c2 = vec3(1.0, 0.35, 0.05), c3 = vec3(1.0, 0.78, 0.35), c4 = vec3(1.0, 0.98, 0.9);
    if (t < 0.33) return mix(c1, c2, t / 0.33);
    if (t < 0.66) return mix(c2, c3, (t - 0.33) / 0.33);
    return mix(c3, c4, (t - 0.66) / 0.34);
}

void main() {
    float I = col.a;
    vec3 rgb;
    float a;
    if (Mode < 0.5) {
        float r = length(uv);
        if (r < 1.0) {
            float z = sqrt(1.0 - r * r);
            vec3 n = vec3(uv, z);
            float gran = fbm(n * 5.0 + vec3(0.0, 0.0, Time * 0.6));
            float spots = fbm(n * 14.0 - vec3(Time * 0.9));
            float limb = pow(z, 0.45);
            float t = clamp(0.45 + 0.45 * limb + 0.25 * (gran - 0.5) + 0.1 * spots, 0.0, 1.0);
            rgb = fireRamp(t) * mix(vec3(1.0), col.rgb, 0.35) * (1.2 + 0.6 * limb);
            a = 1.0;
        } else {
            float ang = atan(uv.y, uv.x);
            float stream = fbm(vec3(cos(ang) * 3.0, sin(ang) * 3.0, r * 1.5 - Time * 0.8));
            float fall = exp(-(r - 1.0) * (3.2 - 1.2 * stream));
            rgb = fireRamp(0.55 + 0.4 * fall) * col.rgb * 1.4;
            a = fall * (0.55 + 0.6 * stream);
        }
    } else if (Mode < 1.5) {
        float r = length(uv);
        float ang = atan(uv.y, uv.x);
        if (r < 1.0 || r > 4.2) discard;
        float swirl = fbm(vec3(r * 3.0, cos(ang - Time * 2.4 / r) * 2.0, sin(ang - Time * 2.4 / r) * 2.0));
        float bands = 0.6 + 0.4 * sin(r * 9.0 - Time * 1.5 + swirl * 4.0);
        float doppler = 0.55 + 0.45 * cos(ang - 0.6);
        float heat = clamp(1.25 - (r - 1.0) / 3.2, 0.0, 1.0);
        float edge = smoothstep(1.0, 1.15, r) * smoothstep(4.2, 3.2, r);
        rgb = fireRamp(heat * (0.7 + 0.3 * swirl)) * col.rgb * (0.6 + 1.4 * doppler) * bands * 1.6;
        a = edge * (0.5 + 0.5 * swirl);
    } else {
        float x = uv.x;
        float flow = fbm(vec3(x * 3.0, uv.y * 0.25 - Time * 7.0, Time));
        float core = exp(-x * x * 10.0);
        float sheath = exp(-x * x * 2.2) * (0.5 + 0.6 * flow);
        float rings = 0.75 + 0.25 * sin(uv.y * 0.9 + Time * 30.0);
        rgb = mix(col.rgb, vec3(1.0, 0.98, 0.92), core) * (core * 2.4 + sheath * 1.2) * rings;
        a = clamp(core + sheath, 0.0, 1.0);
    }
    fragColor = vec4(rgb, a * I);
}
