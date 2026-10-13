#version 150

// The inside of Unlimited Void, computed per pixel from the view direction: an endless deep-space void packed with
// stars at every depth, drifting nebula veils, countless streams of light rushing toward a colossal glowing iris
// (rings of light around a blinding white core), and a slow spin of everything around it.
// Fade blends it in over whatever is behind it (0 = invisible, 1 = fully the void).

uniform float Time;
uniform float Fade;
uniform vec3 IrisDir;

in vec3 dir;

out vec4 fragColor;

vec3 hash33(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float gnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(dot(hash33(i), f), dot(hash33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), u.x),
                   mix(dot(hash33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)), dot(hash33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0)), u.x), u.y),
               mix(mix(dot(hash33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)), dot(hash33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), u.x),
                   mix(dot(hash33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)), dot(hash33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1)), u.x), u.y), u.z);
}

float fbm(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        s += a * gnoise(p);
        p = p * 2.07 + 3.1;
        a *= 0.5;
    }
    return s;
}

float stars(vec3 d, float scale, float density) {
    vec3 p = d * scale;
    vec3 i = floor(p);
    vec3 f = fract(p);
    float h = hash13(i);
    if (h > density) return 0.0;
    vec3 c = hash33(i + 17.0) * 0.3 + 0.5;
    float tw = 0.75 + 0.25 * sin(Time * (0.2 + h * 3.0) + h * 50.0);
    return smoothstep(0.13, 0.0, length(f - c)) * (pow(hash13(i + 5.0), 5.0) * 2.0 + 0.25) * tw;
}

mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}

void main() {
    vec3 d0 = normalize(dir);
    vec3 d = rotY(Time * 0.0015) * d0;
    vec3 iris = normalize(IrisDir);
    float toIris = dot(d0, iris);

    // Deep space base: blue-black, brighter toward the iris.
    vec3 col = mix(vec3(0.004, 0.006, 0.02), vec3(0.03, 0.05, 0.12), smoothstep(-0.2, 1.0, toIris));

    // Nebula veils in violet and cyan.
    float neb = fbm(d * 2.2 + vec3(0.0, Time * 0.002, 0.0));
    float neb2 = fbm(d * 4.0 - vec3(Time * 0.003));
    col += vec3(0.16, 0.05, 0.3) * smoothstep(0.0, 0.6, neb) + vec3(0.02, 0.16, 0.26) * smoothstep(0.1, 0.7, neb2);

    // Stars at every depth.
    float s = stars(d, 420.0, 0.22) + stars(d, 200.0, 0.14) * 1.3 + stars(d, 80.0, 0.06) * 2.2 + stars(d, 30.0, 0.03) * 3.0;
    float tint = hash13(floor(d * 200.0));
    col += s * mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.95), tint);

    // Streams of light rushing toward the iris: thin radial lines whose dashes flow inward.
    vec3 axisA = normalize(cross(iris, vec3(0.0, 1.0, 0.0) + vec3(0.001)));
    vec3 axisB = cross(iris, axisA);
    vec2 q = vec2(dot(d0, axisA), dot(d0, axisB));
    float ang = atan(q.y, q.x);
    float radial = acos(clamp(toIris, -1.0, 1.0));
    float lane = floor(ang * 70.0);
    float laneOn = step(0.62, hash13(vec3(lane, 3.0, 9.0)));
    float laneW = smoothstep(0.5, 0.0, abs(fract(ang * 70.0) - 0.5) * 2.0 - 0.2);
    float dash = smoothstep(0.55, 1.0, fract(radial * 3.0 + Time * 0.02 * (1.0 + hash13(vec3(lane)))));
    col += vec3(0.7, 0.85, 1.0) * laneOn * laneW * dash * smoothstep(2.6, 0.3, radial) * 0.7;

    // The iris: a blinding core, a dark pupil ring, then bright concentric rings fading out.
    float r = radial * 9.0;
    float core = exp(-r * r * 6.0) * 4.0;
    float pupil = smoothstep(0.25, 0.32, r) * smoothstep(0.62, 0.42, r);
    float rings = 0.0;
    for (int i = 1; i <= 6; i++) {
        float fi = float(i);
        float rr = 0.55 + fi * 0.42 + sin(Time * 0.03 + fi) * 0.03;
        rings += exp(-pow((r - rr) / (0.035 + fi * 0.008), 2.0)) * (1.2 - fi * 0.15);
    }
    float halo = exp(-r * 0.6) * 0.5;
    vec3 irisCol = vec3(1.0, 1.0, 1.0) * core + vec3(0.65, 0.85, 1.0) * rings + vec3(0.45, 0.6, 1.0) * halo;
    col = col * (1.0 - pupil * 0.9) + irisCol;

    fragColor = vec4(col, Fade);
}
