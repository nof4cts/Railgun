#version 150

// A ball of cursed energy drawn on a sphere mesh (additive). Three styles:
//   Mode 0 Blue: an implosion. Bright core, bands spiralling INTO the centre, cool rim.
//   Mode 1 Red: an explosion. White-hot core, turbulent bands pushing OUTWARD, hot rim.
//   Mode 2 Purple: imaginary mass. Inky violet body with white veins and crackling seams; an almost-black
//          heart behind a white-hot shell, so it reads as "something that erases".

uniform float Mode;
uniform float Time;
uniform float Intensity;
uniform float Seed;
uniform vec3 ColorA;
uniform vec3 ColorB;

in vec3 objPos;
in vec3 viewN;
in vec3 viewP;

out vec4 fragColor;

vec3 hash33(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float gnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(mix(dot(hash33(i), f), dot(hash33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), u.x),
                   mix(dot(hash33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), dot(hash33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0)), u.x), u.y),
               mix(mix(dot(hash33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), dot(hash33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), u.x),
                   mix(dot(hash33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), dot(hash33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1)), u.x), u.y), u.z);
}

float fbm(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * gnoise(p);
        p = p * 2.03 + 1.7;
        a *= 0.5;
    }
    return s;
}

void main() {
    vec3 n = normalize(viewN);
    vec3 v = normalize(-viewP);
    float facing = clamp(dot(n, v), 0.0, 1.0);
    float rim = pow(1.0 - facing, 2.2);
    vec3 d = normalize(objPos);
    float t = Time * 0.05;
    vec3 col;
    float cover;
    if (Mode < 0.5) {
        // Spiral bands flowing inward.
        float ang = atan(d.z, d.x);
        float sw = sin(ang * 3.0 + d.y * 6.0 + t * 9.0 + fbm(d * 3.0 + Seed) * 4.0);
        float bands = smoothstep(0.2, 1.0, sw);
        float core = pow(facing, 3.0);
        col = ColorA * (0.35 + bands * 0.9) * (0.5 + facing) + ColorB * core * 1.3 + ColorA * rim * 1.2;
        cover = 0.25 + 0.55 * sqrt(facing);
    } else if (Mode < 1.5) {
        float turb = fbm(d * 3.5 - vec3(0.0, t * 3.0, 0.0) + Seed);
        float hot = smoothstep(-0.1, 0.5, turb);
        float core = pow(facing, 2.0);
        col = mix(ColorA, ColorB, hot * core) * (0.6 + hot) + ColorB * core * 1.1 + ColorA * rim * 1.4;
        cover = 0.3 + 0.55 * sqrt(facing);
    } else {
        float veins = smoothstep(0.045, 0.0, abs(gnoise(d * 5.0 + vec3(t * 2.0) + Seed)));
        float seams = smoothstep(0.02, 0.0, abs(gnoise(d * 11.0 - vec3(t * 4.0))));
        float swirl = fbm(d * 2.5 + vec3(sin(t), t * 1.5, cos(t)));
        vec3 body = ColorA * (0.25 + 0.5 * smoothstep(-0.3, 0.6, swirl));
        float heart = pow(facing, 6.0);
        col = body * (1.0 - heart * 0.85)                       // dark heart
            + vec3(1.0, 0.95, 1.0) * (veins * 1.4 + seams * 1.8)  // white veins and cracks
            + ColorB * rim * 1.6 + ColorA * rim * 1.0;            // blazing shell
        cover = 0.92;
    }
    // Back faces (seen through the sphere) add only a little, so the ball doesn't double up to white.
    // Premultiplied: the orb partly blocks what's behind it so its colour survives against a bright sky.
    float ff = gl_FrontFacing ? 1.0 : 0.0;
    fragColor = vec4(col * Intensity * ff, cover * ff);
}
