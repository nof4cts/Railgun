#version 150
// Writes how far the picture should bend here: the shape's across-direction, projected on the screen, times a profile.
// Signed offsets are split into four positive channels so they can be added together.
in vec4 vColor;
in vec2 vUV;
in float vDepth;
uniform float Time;
out vec4 fragColor;
void main() {
    float amount = vColor.r, kind = vColor.a;
    vec2 g = vec2(dFdx(vUV.y), dFdy(vUV.y));
    float gl = length(g);
    vec2 dir = gl > 1e-7 ? g / gl : vec2(0.0);
    float prof;
    if (kind > 0.5) {
        // rings and rifts: a lens bulge across the band
        prof = sin(vUV.y * 3.14159);
    } else {
        // cuts: strongest just inside the edge
        float d = 1.0 - vUV.y;
        prof = exp(-d * 4.0) * smoothstep(0.0, 0.2, vUV.x) * (1.0 - smoothstep(0.8, 1.0, vUV.x));
    }
    vec2 off = dir * prof * amount * 0.025;
    fragColor = vec4(max(off.x, 0.0), max(-off.x, 0.0), max(off.y, 0.0), max(-off.y, 0.0)) * 8.0;
}
