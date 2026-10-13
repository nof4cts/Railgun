#version 150

// Yuki Tsukumo's effects. Output is premultiplied (rgb * cover, cover): alpha 0 = pure additive light,
// alpha > 0 = it covers / darkens what is behind (black hole, bones, blades). Vertex colour = tint, vertex alpha = strength.
//   Mode 0 soft glow                 Mode 1 streak ribbon (UV.x along)     Mode 2 thin ring
//   Mode 3 dark halo (lensing: darkens the world around the hole)
//   Mode 4 accretion disk segment (UV.x = angle 0..1, UV.y = radius 0..1)
//   Mode 5 bone (Garuda vertebra: shaded, ridged, outlined)               Mode 6 crystal blade (Garuda fin)
//   Mode 7 starfield dome (sphere mesh: the domain sky)                    Mode 8 event horizon (sphere mesh: pure black)
//   Mode 9 crackling arc ribbon (mass bending space)                       Mode 10 shock ring on the ground (UV quad)

uniform float Mode;
uniform float Time;
uniform float Seed;
uniform float Alpha;

in vec2 vUV;
in vec4 vColor;
in vec3 objPos;
in vec3 viewN;
in vec3 viewP;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7)) + Seed * 17.3) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * noise(p);
        p = p * 2.03 + 1.7;
        a *= 0.5;
    }
    return s;
}

void main() {
    vec3 tint = vColor.rgb;
    float a = vColor.a;
    if (Mode < 0.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float r2 = dot(p, p);
        float e = max(exp(-r2 * 4.0) - 0.018, 0.0) + exp(-r2 * 40.0) * 0.6;
        fragColor = vec4(tint * e * a, 0.0);
    } else if (Mode < 1.5) {
        float y = abs(vUV.y * 2.0 - 1.0);
        float along = vUV.x;
        float e = exp(-y * y * 9.0) * smoothstep(0.0, 0.12, along) * smoothstep(1.0, 0.7, along);
        e += exp(-y * y * 70.0) * 0.9 * smoothstep(1.0, 0.4, along);
        fragColor = vec4(mix(tint, vec3(1.0), exp(-y * y * 60.0) * 0.5) * e * a, 0.0);
    } else if (Mode < 2.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float r = length(p);
        float e = exp(-pow((r - 0.88) / 0.03, 2.0)) * 1.4 + exp(-pow((r - 0.88) / 0.14, 2.0)) * 0.45;
        fragColor = vec4(tint * e * a, 0.0);
    } else if (Mode < 3.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float r = length(p);
        float cover = smoothstep(1.0, 0.25, r) * a;
        fragColor = vec4(vec3(0.02, 0.0, 0.04) * cover, cover);
    } else if (Mode < 4.5) {
        // Accretion disk: hot violet-white inside, deep purple outside, swirling and Doppler-brightened on one side.
        float ang = vUV.x * 6.28318;
        float r = vUV.y;
        float swirl = fbm(vec2(ang * 3.0 / 6.28318 * 8.0 + r * 6.0 - Time * (0.35 / (0.25 + r)), r * 9.0));
        float bands = 0.55 + 0.45 * sin(r * 60.0 - Time * 0.8 + swirl * 6.0);
        float edge = smoothstep(0.0, 0.08, r) * smoothstep(1.0, 0.55, r);
        float doppler = 0.6 + 0.4 * cos(ang + Seed);
        vec3 hot = vec3(1.0, 0.9, 1.0), mid = vec3(0.78, 0.35, 1.0), cold = vec3(0.32, 0.05, 0.55);
        vec3 c = r < 0.3 ? mix(hot, mid, r / 0.3) : mix(mid, cold, (r - 0.3) / 0.7);
        float e = edge * (0.35 + 0.65 * swirl) * bands * doppler * 1.6;
        fragColor = vec4(c * e * a, 0.0);
    } else if (Mode < 5.5) {
        // Garuda's bone: rounded, bone-white, with vertebra ridges and a dark outline.
        vec2 p = vUV * 2.0 - 1.0;
        float r = length(p * vec2(1.0, 1.15));
        float cover = smoothstep(1.0, 0.9, r) * a;
        if (cover < 0.003) discard;
        vec3 n = normalize(vec3(p, sqrt(max(0.0, 1.0 - r * r))));
        float diff = clamp(dot(n, normalize(vec3(-0.4, 0.7, 0.6))), 0.0, 1.0);
        float ridge = smoothstep(0.75, 1.0, abs(sin(p.x * 6.0 + Seed)));
        vec3 c = tint * (0.45 + 0.6 * diff) - vec3(0.12) * ridge;
        c = mix(c, vec3(0.18, 0.17, 0.22), smoothstep(0.78, 0.95, r));
        c += vec3(1.0) * pow(clamp(dot(n, normalize(vec3(-0.3, 0.6, 1.0))), 0.0, 1.0), 30.0) * 0.4;
        fragColor = vec4(c * cover, cover);
    } else if (Mode < 6.5) {
        // Crystal blade: translucent blue-violet with bright facet edges.
        float y = abs(vUV.y * 2.0 - 1.0);
        float along = vUV.x;
        float width = 1.0 - along * 0.95;
        float inside = smoothstep(width, width - 0.15, y);
        float cover = inside * a * 0.82;
        if (cover < 0.003) discard;
        float facet = smoothstep(0.1, 0.0, abs(y - width * 0.5));
        vec3 c = tint * (0.55 + 0.45 * (1.0 - y)) + vec3(0.85, 0.9, 1.0) * (facet * 0.5 + smoothstep(width - 0.2, width, y) * 0.6);
        fragColor = vec4(c * cover, cover);
    } else if (Mode < 7.5) {
        // Domain sky: deep violet void, nebula clouds and stars (sphere mesh, seen from inside).
        vec3 d = normalize(objPos);
        vec2 uv = vec2(atan(d.z, d.x), d.y * 1.6);
        float neb = fbm(uv * 2.2 + vec2(Time * 0.01, 0.0));
        float neb2 = fbm(uv * 5.0 - vec2(0.0, Time * 0.015));
        vec3 c = mix(vec3(0.02, 0.0, 0.05), vec3(0.22, 0.05, 0.4), smoothstep(0.35, 0.85, neb));
        c += vec3(0.55, 0.2, 0.9) * smoothstep(0.6, 0.95, neb2) * 0.35;
        vec2 g = floor(uv * vec2(140.0, 90.0));
        float star = step(0.993, hash(g)) * (0.6 + 0.4 * sin(Time * 0.2 + hash(g + 3.0) * 30.0));
        c += vec3(1.0, 0.9, 1.0) * star;
        float hz = 1.0 - smoothstep(-0.05, 0.25, d.y);
        c += vec3(0.5, 0.15, 0.85) * hz * 0.35;
        fragColor = vec4(c * Alpha, Alpha);
    } else if (Mode < 8.5) {
        // Event horizon: perfectly black, with a thin violet photon rim.
        vec3 n = normalize(viewN);
        float facing = clamp(abs(n.z), 0.0, 1.0);
        float rim = pow(1.0 - facing, 5.0);
        vec3 c = vec3(0.75, 0.35, 1.0) * rim * 1.4;
        fragColor = vec4(c * Alpha, Alpha);
    } else if (Mode < 9.5) {
        float y = abs(vUV.y * 2.0 - 1.0);
        float flick = step(0.3, hash(vec2(floor(Time * 1.7), floor(vUV.x * 13.0))));
        float e = (exp(-y * y * 30.0) * 1.6 + exp(-y * y * 4.0) * 0.4) * (0.4 + 0.6 * flick);
        fragColor = vec4(mix(tint, vec3(1.0), exp(-y * y * 60.0)) * e * a, 0.0);
    } else {
        vec2 p = vUV * 2.0 - 1.0;
        float r = length(p);
        float n = fbm(vec2(atan(p.y, p.x) * 3.0, r * 4.0) + Seed);
        float e = exp(-pow((r - 0.85 + n * 0.08) / 0.06, 2.0)) * 1.3 + smoothstep(0.85, 0.0, r) * 0.15;
        fragColor = vec4(tint * e * a, 0.0);
    }
}
