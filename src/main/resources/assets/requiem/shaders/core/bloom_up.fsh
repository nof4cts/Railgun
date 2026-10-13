#version 150
// 3x3 tent upsample, added onto the next larger level.
uniform sampler2D Src;
uniform vec2 Texel;
uniform float Weight;
in vec2 uv;
out vec4 fragColor;
vec3 s(vec2 o) { return texture(Src, uv + o * Texel).rgb; }
void main() {
    vec3 col = s(vec2(0, 0)) * 4.0 + (s(vec2(-1, 0)) + s(vec2(1, 0)) + s(vec2(0, -1)) + s(vec2(0, 1))) * 2.0
             + s(vec2(-1, -1)) + s(vec2(1, -1)) + s(vec2(-1, 1)) + s(vec2(1, 1));
    fragColor = vec4(col / 16.0 * Weight, 1.0);
}
