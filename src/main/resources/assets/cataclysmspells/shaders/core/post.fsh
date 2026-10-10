#version 150

// Cataclysm Spells post chain (original implementation):
// 0 bright-pass (soft knee) · 1 13-tap downsample · 2 tent upsample (added)
// 3 radial god rays from LightPos · 4 composite scene + bloom + rays with a soft filmic shoulder

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform float Mode;
uniform vec2 TexelSize;
uniform float Threshold;
uniform float Intensity;
uniform vec2 LightPos;
uniform float RayStrength;
uniform vec3 RayTint;

in vec2 texCoord;
out vec4 fragColor;

vec3 tap(vec2 o) { return texture(Sampler0, texCoord + o * TexelSize).rgb; }

void main() {
    if (Mode < 0.5) {
        vec3 c = texture(Sampler0, texCoord).rgb;
        float l = max(c.r, max(c.g, c.b));
        float knee = 0.25;
        float soft = clamp(l - Threshold + knee, 0.0, 2.0 * knee);
        soft = soft * soft / (4.0 * knee + 1e-4);
        float contrib = max(soft, l - Threshold) / max(l, 1e-4);
        fragColor = vec4(c * contrib, 1.0);
    } else if (Mode < 1.5) {
        vec3 a = tap(vec2(-2.0, 2.0)), b = tap(vec2(0.0, 2.0)), c = tap(vec2(2.0, 2.0));
        vec3 d = tap(vec2(-2.0, 0.0)), e = tap(vec2(0.0, 0.0)), f = tap(vec2(2.0, 0.0));
        vec3 g = tap(vec2(-2.0, -2.0)), h = tap(vec2(0.0, -2.0)), i = tap(vec2(2.0, -2.0));
        vec3 j = tap(vec2(-1.0, 1.0)), k = tap(vec2(1.0, 1.0)), l = tap(vec2(-1.0, -1.0)), m = tap(vec2(1.0, -1.0));
        vec3 res = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
        fragColor = vec4(res, 1.0);
    } else if (Mode < 2.5) {
        vec3 res = tap(vec2(0.0)) * 4.0;
        res += (tap(vec2(-1.0, 0.0)) + tap(vec2(1.0, 0.0)) + tap(vec2(0.0, -1.0)) + tap(vec2(0.0, 1.0))) * 2.0;
        res += tap(vec2(-1.0, -1.0)) + tap(vec2(1.0, -1.0)) + tap(vec2(-1.0, 1.0)) + tap(vec2(1.0, 1.0));
        fragColor = vec4(res / 16.0 * Intensity, 1.0);
    } else if (Mode < 3.5) {
        vec2 uv = texCoord;
        vec2 delta = (uv - LightPos) / 56.0;
        float decay = 1.0;
        vec3 acc = vec3(0.0);
        for (int n = 0; n < 56; n++) {
            uv -= delta;
            acc += texture(Sampler0, uv).rgb * decay;
            decay *= 0.962;
        }
        fragColor = vec4(acc / 18.0 * RayStrength * RayTint, 1.0);
    } else {
        vec3 s = texture(Sampler0, texCoord).rgb;
        vec3 b = texture(Sampler1, texCoord).rgb;
        vec3 r = texture(Sampler2, texCoord).rgb;
        vec3 c = s + b * Intensity + r;
        c = c / (1.0 + max(c - 1.0, 0.0) * 0.6);
        fragColor = vec4(c, 1.0);
    }
}
