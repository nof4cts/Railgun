#version 150
#include "common.glsl"
// 13-tap downsample (Call of Duty: Advanced Warfare style). The first pass also picks out the bright parts.
uniform sampler2D Src;
uniform vec2 SrcTexel;
uniform float Prefilter;
uniform float Threshold;
in vec2 uv;
out vec4 fragColor;

vec3 tap(vec2 o) { return texture(Src, uv + o * SrcTexel).rgb; }

vec3 bright(vec3 c) {
    float l = max(c.r, max(c.g, c.b));
    float knee = 0.25;
    float soft = clamp(l - Threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 1e-4);
    float w = max(soft, l - Threshold) / max(l, 1e-4);
    return c * w;
}

void main() {
    vec3 a = tap(vec2(-2, 2)), b = tap(vec2(0, 2)), c = tap(vec2(2, 2));
    vec3 d = tap(vec2(-2, 0)), e = tap(vec2(0, 0)), f = tap(vec2(2, 0));
    vec3 g = tap(vec2(-2, -2)), h = tap(vec2(0, -2)), i = tap(vec2(2, -2));
    vec3 j = tap(vec2(-1, 1)), k = tap(vec2(1, 1)), l = tap(vec2(-1, -1)), m = tap(vec2(1, -1));
    vec3 col = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
    if (Prefilter > 0.5) {
        col = bright(col);
    }
    fragColor = vec4(col, 1.0);
}
