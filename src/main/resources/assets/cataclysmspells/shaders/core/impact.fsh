#version 150

// Cataclysm Spells — cinematic post / impact-frame shader.
// Mode 0: post (chromatic aberration, zoom blur, heat haze, grain, vignette)
// Mode 1: INK      — scene traced into white paper + black ink, radial speed lines
// Mode 2: NEGATIVE — inverse of INK
// Mode 3: DUOTONE  — posterised black + Tint with ink outlines
// Mode 4: BLEACH   — overexposed white-out with tinted contours
// Always: shockwave refraction ring (Radius) and gravitational lens (LensPos/LensR).

uniform sampler2D Sampler0;
uniform float Time;
uniform float Mode;
uniform vec2 Focus;
uniform float Strength;
uniform float Radius;
uniform vec3 Tint;
uniform float Seed;
uniform float Aspect;
uniform vec2 LensPos;
uniform float LensR;
uniform float Heat;

in vec2 texCoord;
out vec4 fragColor;

const float PI = 3.14159265;

float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
float hash1(float n) { return fract(sin(n) * 43758.5453); }
float hash2(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

vec3 scene(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

float sobel(vec2 uv) {
    vec2 px = 1.6 / vec2(textureSize(Sampler0, 0));
    float tl = lum(scene(uv + px * vec2(-1.0,  1.0)));
    float t  = lum(scene(uv + px * vec2( 0.0,  1.0)));
    float tr = lum(scene(uv + px * vec2( 1.0,  1.0)));
    float l  = lum(scene(uv + px * vec2(-1.0,  0.0)));
    float r  = lum(scene(uv + px * vec2( 1.0,  0.0)));
    float bl = lum(scene(uv + px * vec2(-1.0, -1.0)));
    float b  = lum(scene(uv + px * vec2( 0.0, -1.0)));
    float br = lum(scene(uv + px * vec2( 1.0, -1.0)));
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
    return sqrt(gx * gx + gy * gy);
}

float speedLines(vec2 uv) {
    vec2 d = (uv - Focus) * vec2(Aspect, 1.0);
    float ang = atan(d.y, d.x);
    float dist = length(d);
    float k = floor((ang + PI) / (2.0 * PI) * 260.0);
    float h = hash1(k + Seed * 17.13);
    float start = 0.10 + h * 0.38;
    return step(0.74, h) * smoothstep(start, start + 0.06, dist);
}

void main() {
    vec2 uv = texCoord;
    vec2 asp = vec2(Aspect, 1.0);

    // gravitational lens: bend the image around the hole, brighten the Einstein ring
    float einstein = 0.0;
    if (LensR > 0.0) {
        vec2 ld = (uv - LensPos) * asp;
        float lr = length(ld);
        float bend = (LensR * LensR) / max(lr, LensR * 0.35);
        uv -= normalize(ld + 1e-6) / asp * bend * 0.9;
        einstein = exp(-pow((lr - LensR * 1.55) / (LensR * 0.12 + 0.002), 2.0));
    }

    // shockwave refraction ring
    vec2 fd = (uv - Focus) * asp;
    float fdist = length(fd);
    if (Radius > 0.0) {
        float ring = exp(-pow((fdist - Radius) * 16.0, 2.0));
        uv -= normalize(fd + 1e-6) / asp * ring * 0.045 * max(Strength, 0.4);
    }

    // heat haze
    if (Heat > 0.0) {
        uv += vec2(sin(uv.y * 90.0 + Time * 9.0), cos(uv.x * 70.0 + Time * 7.0)) * 0.0025 * Heat;
    }

    vec3 col;
    if (Mode < 0.5) {
        vec2 dir = uv - Focus;
        float ca = 0.010 * Strength;
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 10; i++) {
            float s = 1.0 - float(i) * 0.010 * Strength;
            vec2 u2 = Focus + dir * s;
            acc += vec3(scene(u2 + dir * ca).r, scene(u2).g, scene(u2 - dir * ca).b);
        }
        col = acc / 10.0;
        float vig = smoothstep(1.15, 0.30, fdist);
        col *= mix(1.0, vig, 0.55 * clamp(Strength, 0.0, 1.0));
        col += (hash2(uv * 1000.0 + Time) - 0.5) * 0.05 * clamp(Strength, 0.0, 1.0);
        col += vec3(1.0, 0.85, 0.6) * einstein * 0.9;
    } else {
        vec3 sc = scene(uv);
        float L = lum(sc);
        float e = sobel(uv);
        float ink = smoothstep(0.22, 0.42, e);
        float lines = speedLines(uv);
        float core = smoothstep(0.15, 0.0, fdist);
        if (Mode < 1.5) {
            float v = step(0.40, L);
            v = max(v, core);
            v *= 1.0 - ink;
            v *= 1.0 - lines * (1.0 - core);
            col = vec3(v);
        } else if (Mode < 2.5) {
            float v = 1.0 - step(0.40, L);
            v = max(v, ink);
            v = max(v, lines);
            v = max(v, core);
            col = vec3(v);
        } else if (Mode < 3.5) {
            float p = floor(L * 3.0 + 0.5) / 3.0;
            p = max(p, core);
            col = mix(vec3(0.02, 0.0, 0.03), Tint, p) * (1.0 - ink);
            col = max(col, Tint * lines * 0.9);
        } else {
            col = vec3(1.0) - (1.0 - sc) * 0.15;
            col = mix(col, Tint * 0.6, ink * 0.85);
            col = mix(col, vec3(1.0), core);
        }
    }
    fragColor = vec4(col, 1.0);
}
