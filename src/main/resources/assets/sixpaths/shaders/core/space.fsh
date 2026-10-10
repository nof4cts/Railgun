#version 150

// Procedural deep-space sky (original): three star layers, twinkle, nebula clouds, a galactic band.

uniform float Alpha;
uniform float Time;
uniform vec3 Tint;

in vec2 sph;
out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash(i), n100 = hash(i + vec3(1, 0, 0)), n010 = hash(i + vec3(0, 1, 0)), n110 = hash(i + vec3(1, 1, 0));
    float n001 = hash(i + vec3(0, 0, 1)), n101 = hash(i + vec3(1, 0, 1)), n011 = hash(i + vec3(0, 1, 1)), n111 = hash(i + vec3(1, 1, 1));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y), mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 6; i++) {
        v += a * noise(p);
        p = p * 2.02 + vec3(3.1, 1.7, 5.3);
        a *= 0.5;
    }
    return v;
}

float stars(vec3 d, float scale, float density) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    float h = hash(cell);
    if (h < density) return 0.0;
    vec3 center = cell + vec3(hash(cell + 1.3), hash(cell + 2.7), hash(cell + 4.1));
    float dist = length(p - center);
    float tw = 0.65 + 0.35 * sin(Time * (2.0 + h * 6.0) + h * 40.0);
    return smoothstep(0.12, 0.0, dist) * tw * (h - density) / (1.0 - density) * 3.0;
}

void main() {
    float az = sph.x * 2.0 * PI, el = (sph.y - 0.5) * PI;
    vec3 d = vec3(cos(el) * cos(az), sin(el), cos(el) * sin(az));

    vec3 col = vec3(0.004, 0.003, 0.012);
    float band = exp(-pow(dot(d, normalize(vec3(0.3, 0.8, -0.5))) * 3.2, 2.0));
    float neb = fbm(d * 2.4 + vec3(0.0, Time * 0.01, 0.0));
    float neb2 = fbm(d * 5.0 - vec3(Time * 0.013));
    col += vec3(0.32, 0.10, 0.42) * pow(neb, 3.0) * 1.6;
    col += vec3(0.05, 0.18, 0.40) * pow(neb2, 4.0) * 2.2;
    col += vec3(0.9, 0.7, 0.55) * band * pow(neb, 2.0) * 0.55;
    col *= Tint;

    float s = stars(d, 160.0, 0.985) + stars(d, 320.0, 0.990) * 0.7 + stars(d, 60.0, 0.996) * 1.6;
    s *= 1.0 + band * 2.0;
    col += vec3(s) * mix(vec3(1.0), vec3(0.75, 0.85, 1.0), hash(floor(d * 160.0)));

    fragColor = vec4(col, Alpha);
}
