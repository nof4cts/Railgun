#version 150
#include "common.glsl"
// The space the universe falls into: a spiral galaxy seen in true 3D (a disc through the galaxy's centre, ray-cast per
// pixel from the camera), its bulge glowing through everything, nebulae and three layers of stars behind it. As the
// universe collapses, the galaxy winds tighter and the core burns until it is all there is.

uniform vec3 CamFwd;        // the camera's basis in world space
uniform vec3 CamRight;
uniform vec3 CamUp;
uniform vec2 TanHalf;       // tan of half the field of view, horizontally and vertically
uniform vec3 CamPos;        // camera position relative to the galaxy's centre, in blocks
uniform float Time;
uniform float Spin;         // how far the galaxy has turned (radians)
uniform float Collapse;     // 0 calm .. 1 everything at the core
uniform float Scale;        // galaxy radius in blocks
uniform float Fade;         // how much of the old world is already gone
uniform float Core;         // core brightness

in vec2 uv;
out vec4 fragColor;

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 stars(vec3 d) {
    vec3 col = vec3(0.0);
    for (int layer = 0; layer < 3; layer++) {
        float sc = 70.0 + float(layer) * 110.0;
        vec3 p = d * sc;
        vec3 cell = floor(p);
        vec3 f = fract(p) - 0.5;
        float h = hash13(cell + float(layer) * 17.0);
        float h2 = hash13(cell.zxy + float(layer) * 5.0 + 3.0);
        vec3 off = vec3(h2, fract(h * 13.7), fract(h2 * 7.3)) - 0.5;
        float r = length(f - off * 0.6);
        float on = step(0.93 + float(layer) * 0.02, h);
        float twinkle = 0.75 + 0.25 * sin(Time * (2.0 + h2 * 5.0) + h * 40.0);
        vec3 tint = mix(vec3(0.65, 0.78, 1.0), vec3(1.0, 0.86, 0.68), h2);
        col += on * tint * smoothstep(0.09 - float(layer) * 0.02, 0.0, r) * (0.8 + 1.4 * h2) * twinkle;
    }
    return col;
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), u.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), u.x), u.y);
    float b = mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), u.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), u.x), u.y);
    return mix(a, b, u.z);
}

float fbm3(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise3(p);
        p = p * 2.07 + vec3(17.1, 9.2, 4.7);
        a *= 0.5;
    }
    return v;
}

vec3 nebula(vec3 d) {
    float n = fbm3(d * 2.2 + vec3(Time * 0.004, 0.0, 0.0));
    float m = fbm3(d * 4.3 - vec3(0.0, Time * 0.006, 0.0) + 7.0);
    vec3 a = vec3(0.16, 0.05, 0.30) * smoothstep(0.35, 0.85, n);
    vec3 b = vec3(0.02, 0.12, 0.28) * smoothstep(0.4, 0.9, m);
    vec3 c = vec3(0.35, 0.06, 0.22) * smoothstep(0.62, 0.95, n * m * 1.6);
    return a + b + c;
}

/** The disc at a point on the galaxy plane: arms, dust lanes, star-forming knots, the core's warm wash. */
vec3 disc(vec2 xz) {
    float shrink = 1.0 - 0.9 * Collapse;
    float r = length(xz) / (Scale * shrink);
    if (r > 1.6) {
        return vec3(0.0);
    }
    float th = atan(xz.y, xz.x);
    float twist = 3.4 + Collapse * 6.0;
    float a = th - Spin + log(r + 0.03) * twist;
    float arm = pow(0.5 + 0.5 * cos(2.0 * a), 2.6);
    float arm2 = pow(0.5 + 0.5 * cos(2.0 * a + 0.55), 7.0);
    vec2 rot = mat2(cos(Spin), -sin(Spin), sin(Spin), cos(Spin)) * xz / (Scale * shrink);
    float cloud = fbm(rot * 7.0 + 3.0);
    float fine = fbm(rot * 22.0 - 5.0);
    float falloff = exp(-r * 2.3) * smoothstep(1.35, 0.25, r);
    float body = falloff * (0.25 + 1.6 * arm * (0.55 + 0.9 * cloud));
    float dust = smoothstep(0.45, 0.8, arm2) * smoothstep(0.08, 0.35, r) * (0.6 + 0.4 * fine);
    vec3 armCol = mix(vec3(0.38, 0.52, 1.0), vec3(0.75, 0.82, 1.0), fine);
    vec3 col = armCol * body;
    // pink star-forming knots along the arms
    float knots = smoothstep(0.72, 0.95, fine * cloud * 1.7) * arm * falloff;
    col += vec3(1.0, 0.35, 0.65) * knots * 1.6;
    // young stars sparkling in the arms
    vec2 g = rot * 140.0;
    vec2 cell = floor(g);
    float h = hash12(cell);
    float sp = step(0.965, h) * smoothstep(0.35, 0.0, length(fract(g) - 0.5)) * arm * (r < 1.2 ? 1.0 : 0.0);
    col += vec3(0.9, 0.95, 1.0) * sp * 2.0;
    col *= 1.0 - dust * 0.75;
    // the bulge
    col += vec3(1.0, 0.82, 0.55) * exp(-r * 9.0) * 1.3;
    return col;
}

void main() {
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 d = normalize(CamFwd + CamRight * ndc.x * TanHalf.x + CamUp * ndc.y * TanHalf.y);

    vec3 col = vec3(0.004, 0.003, 0.012);
    col += nebula(d) * (1.0 - Collapse * 0.7);
    col += stars(d) * (1.0 - Collapse * 0.5);

    // the disc, where the ray crosses the galaxy's plane
    if (abs(d.y) > 1e-4) {
        float t = -CamPos.y / d.y;
        if (t > 0.0) {
            vec3 p = CamPos + d * t;
            // thin discs read brighter edge-on
            float edge = 1.0 / max(0.25, abs(d.y) * 1.6);
            col += disc(p.xz) * min(edge, 2.2) * 0.9;
        }
    }

    // the core's glow seen through everything: by how close the ray passes the centre
    float tc = max(-dot(CamPos, d), 0.0);
    float dmin = length(CamPos + d * tc);
    float sc = Scale * (1.0 - 0.9 * Collapse);
    float glow = exp(-dmin / (sc * 0.04)) * 0.9 + exp(-dmin / (sc * 0.2)) * 0.22 + exp(-dmin / (sc * 0.7)) * 0.08;
    col += vec3(1.0, 0.88, 0.7) * glow * Core;

    // collapse: the light pulled inward in streaks toward the core
    if (Collapse > 0.001) {
        vec3 toC = normalize(-CamPos);
        float ang = acos(clamp(dot(d, toC), -1.0, 1.0));
        vec3 side = normalize(cross(toC, vec3(0.0, 1.0, 0.0)) + 1e-4);
        float around = atan(dot(d, cross(side, toC)), dot(d, side));
        float streak = pow(hash11(floor(around * 90.0)), 8.0) * smoothstep(0.9, 0.05, ang);
        col += vec3(0.85, 0.9, 1.0) * streak * Collapse * 1.6;
        col = mix(col, vec3(1.0, 0.97, 0.92), smoothstep(0.7, 1.0, Collapse) * smoothstep(1.2, 0.0, ang));
    }

    // film-like shoulder: the core stays the brightest thing without burning the frame out
    col = vec3(1.0) - exp(-col * 1.25);
    fragColor = vec4(col, Fade);
}
