#version 150

// Heat haze: re-draws the scene behind a camera-facing quad with its texture coordinates wobbled by rising noise,
// so the air above fire shimmers. Scene is a copy of the picture taken just before this pass.
// Vertex colour: r = seed, g = warm tint, a = strength. Output is premultiplied (blend ONE, ONE_MINUS_SRC_ALPHA).

uniform sampler2D Scene;
uniform vec2 ScreenSize;
uniform float Time;

in vec2 vUV;
in vec4 vColor;

out vec4 fragColor;

float h2(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1.0, 0.0)), f.x), mix(h2(i + vec2(0.0, 1.0)), h2(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec2 c = vUV * 2.0 - 1.0;
    float r = length(c);
    if (r > 1.0) discard;
    float fall = smoothstep(1.0, 0.25, r);
    float seed = vColor.r * 61.0;
    vec2 q = vUV * 4.0 + vec2(seed, 0.0);
    vec2 off = vec2(vnoise(q + vec2(0.0, -Time * 0.22)) - 0.5,
                    vnoise(q * 1.3 + vec2(5.2, 1.3 - Time * 0.3)) - 0.5);
    off += 0.5 * vec2(vnoise(q * 2.7 + vec2(2.0, -Time * 0.5)) - 0.5, vnoise(q * 2.3 + vec2(7.0, -Time * 0.45)) - 0.5);
    vec2 uv = gl_FragCoord.xy / ScreenSize + off * vColor.a * 0.03 * fall;
    vec3 col = texture(Scene, uv).rgb;
    col *= mix(vec3(1.0), vec3(1.06, 1.0, 0.93), fall * vColor.g);
    fragColor = vec4(col * fall, fall);
}
