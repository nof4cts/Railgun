#version 150
// 9-tap tent upsample of the smaller level, added onto this level.
uniform sampler2D Low;
uniform sampler2D Cur;
uniform vec2 LowTexel;
uniform float Radius;
in vec2 uv;
out vec4 fragColor;

void main() {
    vec2 o = LowTexel * Radius;
    vec3 s = texture(Low, uv).rgb * 4.0;
    s += (texture(Low, uv + vec2(-o.x, 0)).rgb + texture(Low, uv + vec2(o.x, 0)).rgb
        + texture(Low, uv + vec2(0, -o.y)).rgb + texture(Low, uv + vec2(0, o.y)).rgb) * 2.0;
    s += texture(Low, uv + vec2(-o.x, -o.y)).rgb + texture(Low, uv + vec2(o.x, -o.y)).rgb
        + texture(Low, uv + vec2(-o.x, o.y)).rgb + texture(Low, uv + vec2(o.x, o.y)).rgb;
    fragColor = vec4(texture(Cur, uv).rgb + s / 16.0 * 0.85, 1.0);
}
