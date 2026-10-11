#define PI 3.14159265
#define TAU 6.28318531

float saturate(float x) { return clamp(x, 0.0, 1.0); }
float sqr(float x) { return x * x; }
float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), u.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

/** Signed angular difference, wrapped to (-PI, PI]. */
float angDiff(float a, float b) {
    float d = mod(a - b + PI, TAU) - PI;
    return d;
}
