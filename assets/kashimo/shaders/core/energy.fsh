#version 150

// Additive light drawn on quads and ribbons; colour comes from the vertex colour, alpha is the strength.
//   Mode 0 soft glow sprite           Mode 1 streak ribbon (UV0.x along, UV0.y across)
//   Mode 2 thin bright ring (UV quad)  Mode 3 crackling arc ribbon (jittery, white core)
//   Mode 4 lens flare star (four-point)

uniform float Mode;
uniform float Time;

in vec2 vUV;
in vec4 vColor;

out vec4 fragColor;

float hash(float x) {
    return fract(sin(x * 91.3458) * 47453.5453);
}

void main() {
    vec3 c = vColor.rgb;
    float a = vColor.a;
    float e;
    if (Mode < 0.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float r2 = dot(p, p);
        e = max(exp(-r2 * 4.0) - 0.018, 0.0) + exp(-r2 * 40.0) * 0.8;
    } else if (Mode < 1.5) {
        float y = abs(vUV.y * 2.0 - 1.0);
        float along = vUV.x;
        e = exp(-y * y * 9.0) * smoothstep(0.0, 0.15, along) * smoothstep(1.0, 0.6, along);
        e += exp(-y * y * 80.0) * 0.8 * smoothstep(1.0, 0.3, along);
    } else if (Mode < 2.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float r = length(p);
        e = exp(-pow((r - 0.9) / 0.035, 2.0)) + exp(-pow((r - 0.9) / 0.12, 2.0)) * 0.35;
    } else if (Mode < 3.5) {
        float y = abs(vUV.y * 2.0 - 1.0);
        float flick = step(0.35, hash(floor(Time * 1.7) + vUV.x * 13.0));
        e = (exp(-y * y * 30.0) * 1.6 + exp(-y * y * 4.0) * 0.4) * (0.4 + 0.6 * flick);
        c = mix(c, vec3(1.0), exp(-y * y * 60.0));
    } else {
        vec2 p = vUV * 2.0 - 1.0;
        float cross1 = exp(-abs(p.x) * 40.0) * exp(-abs(p.y) * 2.5) + exp(-abs(p.y) * 40.0) * exp(-abs(p.x) * 2.5);
        e = cross1 + exp(-dot(p, p) * 30.0);
    }
    fragColor = vec4(c * e * a, 0.0);
}
