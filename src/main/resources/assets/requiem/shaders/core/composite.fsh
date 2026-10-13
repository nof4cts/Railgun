#version 150
// The energy and its bloom laid onto the picture (added): a soft shoulder keeps the brightest light from clipping flat.
uniform sampler2D Glow;
uniform sampler2D Bloom;
uniform float Strength;
in vec2 uv;
out vec4 fragColor;
void main() {
    vec3 g = texture(Glow, uv).rgb;
    vec3 b = texture(Bloom, uv).rgb * Strength;
    vec3 c = g + b * 1.35;
    c = c / (1.0 + 0.18 * c);
    fragColor = vec4(c, 1.0);
}
