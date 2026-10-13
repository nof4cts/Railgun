#version 150

// Choso's blood: real liquid, not glow. Output is premultiplied (rgb * cover, cover), so blood DARKENS the scene,
// with wet highlights on top. Vertex colour = tint, vertex alpha = opacity.
//   Mode 0 blob (shaded liquid sphere sprite, wobbling edge)     Mode 1 tube ribbon (beam / stream, UV.x along)
//   Mode 2 spinning crescent disc (cutting wheel)                Mode 3 ground splatter (flat quad)
//   Mode 4 red glow (additive light: convergence, heat)          Mode 5 steam puff (Flowing Red Scale heat)
//   Mode 6 chain / binding strand (tube with knotted beads)       Mode 7 falling blood curtain (domain streaks)
//   Mode 10 liquid sphere mesh (Convergence orb)                  Mode 11 crimson sky dome (domain, seen from inside)

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
    for (int i = 0; i < 4; i++) {
        s += a * noise(p);
        p *= 2.1;
        a *= 0.5;
    }
    return s;
}

// Wet shading: dark core, deep red body, sharp white specular, thin bright rim.
vec3 liquid(vec3 n, vec3 tint) {
    vec3 L = normalize(vec3(-0.45, 0.7, 0.55));
    float diff = clamp(dot(n, L), 0.0, 1.0);
    vec3 h = normalize(L + vec3(0.0, 0.0, 1.0));
    float spec = pow(clamp(dot(n, h), 0.0, 1.0), 60.0);
    float spec2 = pow(clamp(dot(n, normalize(vec3(0.5, -0.2, 1.0))), 0.0, 1.0), 18.0) * 0.25;
    float rim = pow(1.0 - clamp(n.z, 0.0, 1.0), 3.0);
    vec3 deep = tint * 0.18;
    vec3 body = mix(deep, tint, diff * 0.85 + 0.1);
    return body + vec3(1.0, 0.85, 0.85) * spec * 1.4 + vec3(1.0, 0.6, 0.6) * spec2 + tint * rim * 0.9;
}

void main() {
    vec3 tint = vColor.rgb;
    float a = vColor.a;
    if (Mode < 0.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float ang = atan(p.y, p.x);
        float edge = 0.86 + 0.09 * sin(ang * 3.0 + Time * 0.6 + Seed) + 0.05 * sin(ang * 7.0 - Time * 0.9);
        float r = length(p) / edge;
        float cover = smoothstep(1.0, 0.9, r) * a;
        if (cover < 0.003) discard;
        vec3 n = vec3(p / edge, sqrt(max(0.0, 1.0 - r * r)));
        fragColor = vec4(liquid(normalize(n), tint) * cover, cover);
    } else if (Mode < 1.5) {
        float y = vUV.y * 2.0 - 1.0;
        float along = vUV.x;
        float wob = 0.08 * sin(along * 40.0 - Time * 2.5 + Seed);
        float yy = y + wob;
        float cover = smoothstep(1.0, 0.8, abs(yy)) * smoothstep(0.0, 0.04, along) * smoothstep(1.0, 0.96, along) * a;
        if (cover < 0.003) discard;
        vec3 n = normalize(vec3(0.0, yy, sqrt(max(0.0, 1.0 - yy * yy))));
        vec3 c = liquid(n, tint);
        // Flowing pressure bands race along the stream.
        float band = smoothstep(0.6, 1.0, sin(along * 60.0 - Time * 6.0) * 0.5 + 0.5);
        c += tint * band * 0.35 * (1.0 - abs(yy));
        fragColor = vec4(c * cover, cover);
    } else if (Mode < 2.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float r = length(p);
        float ang = atan(p.y, p.x) + Time * 1.4;
        float w = 0.16 * (0.4 + 0.6 * fract(ang / 6.28318));      // crescent: thick head, thin tail
        float d = (r - 0.78) / w;
        float cover = smoothstep(1.0, 0.75, abs(d)) * a;
        // Motion-blurred spray inside the wheel.
        float smear = exp(-pow((r - 0.7) / 0.25, 2.0)) * 0.25 * fract(ang / 6.28318);
        cover = max(cover, smear * a);
        if (cover < 0.003) discard;
        vec3 n = normalize(vec3(p / max(r, 0.001) * clamp(d, -1.0, 1.0), sqrt(max(0.0, 1.0 - d * d))));
        fragColor = vec4(liquid(n, tint) * cover, cover);
    } else if (Mode < 3.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float n0 = fbm(p * 3.0 + Seed);
        float r = length(p) + (n0 - 0.5) * 0.7;
        // Satellite droplets around the splat.
        float drops = step(0.82, noise(p * 9.0 + Seed * 3.0)) * smoothstep(1.0, 0.6, length(p));
        float cover = max(smoothstep(0.62, 0.5, r), drops) * a;
        if (cover < 0.003) discard;
        float gloss = pow(clamp(1.0 - r * 1.3, 0.0, 1.0), 4.0) * 0.4;
        vec3 c = tint * (0.25 + 0.3 * n0) + vec3(1.0, 0.7, 0.7) * gloss * step(0.55, noise(p * 6.0));
        fragColor = vec4(c * cover, cover);
    } else if (Mode < 4.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float r2 = dot(p, p);
        float e = max(exp(-r2 * 4.0) - 0.018, 0.0) + exp(-r2 * 40.0) * 0.6;
        fragColor = vec4(tint * e * a, 0.0);
    } else if (Mode < 5.5) {
        vec2 p = vUV * 2.0 - 1.0;
        float n0 = fbm(p * 2.0 + vec2(Seed, -Time * 0.05));
        float cover = smoothstep(1.0, 0.2, length(p) + (n0 - 0.5) * 0.6) * a * 0.6;
        if (cover < 0.003) discard;
        fragColor = vec4(mix(tint, vec3(1.0, 0.9, 0.9), 0.5) * (0.6 + 0.4 * n0) * cover, cover);
    } else if (Mode < 6.5) {
        float y = vUV.y * 2.0 - 1.0;
        float along = vUV.x;
        float bead = 0.55 + 0.45 * abs(sin(along * 80.0 + Seed));
        float yy = y / bead;
        float cover = smoothstep(1.0, 0.8, abs(yy)) * a;
        if (cover < 0.003) discard;
        vec3 n = normalize(vec3(0.0, yy, sqrt(max(0.0, 1.0 - yy * yy))));
        fragColor = vec4(liquid(n, tint) * cover, cover);
    } else if (Mode < 7.5) {
        float x = vUV.x, y = vUV.y;
        float st = noise(vec2(x * 14.0 + Seed, y * 1.2 + Time * 0.22));
        float st2 = noise(vec2(x * 37.0 - Seed, y * 3.0 + Time * 0.35));
        float streak = smoothstep(0.5, 0.92, st) + smoothstep(0.7, 0.98, st2) * 0.6;
        float fade = smoothstep(0.0, 0.18, y) * smoothstep(1.0, 0.55, y) * sin(3.14159 * x);
        float cover = clamp(streak * fade, 0.0, 1.0) * a * 0.85;
        if (cover < 0.003) discard;
        vec3 c = mix(tint * 0.35, tint * 1.2 + vec3(0.15, 0.0, 0.0), st);
        fragColor = vec4(c * cover, cover);
    } else if (Mode > 10.5) {
        a = Alpha;
        vec3 d = normalize(objPos);
        float ang = atan(d.z, d.x);
        float h = d.y;
        float st = noise(vec2(ang * 60.0, h * 2.5 + Time * 0.12));
        float st2 = noise(vec2(ang * 140.0 + 3.0, h * 4.0 + Time * 0.2));
        float streak = (smoothstep(0.5, 0.95, st) + smoothstep(0.75, 0.98, st2) * 0.7) * (1.0 - smoothstep(0.25, 0.85, h));
        vec3 base = mix(vec3(0.20, 0.0, 0.02), vec3(0.015, 0.0, 0.004), smoothstep(-0.05, 0.75, h));
        if (h < 0.0) base = mix(vec3(0.20, 0.0, 0.02), vec3(0.09, 0.0, 0.01), clamp(-h * 3.0, 0.0, 1.0));
        vec3 c = base + vec3(0.85, 0.03, 0.05) * streak * 0.75;
        float speck = step(0.996, hash(floor(vec2(ang * 260.0, h * 260.0)))) * smoothstep(-0.1, 0.4, h);
        c += vec3(1.0, 0.85, 0.85) * speck * 0.7;
        fragColor = vec4(c * a, a);
    } else {
        vec3 n = normalize(viewN);
        if (n.z < 0.0) discard;
        a = Alpha;
        tint = vec3(0.62, 0.02, 0.05);
        vec3 d = normalize(objPos);
        // Compressed blood: swirling dark liquid with bright compression lines and a blazing rim.
        float sw = fbm(vec2(atan(d.z, d.x) * 2.0 + Time * 0.15, d.y * 3.0 - Time * 0.1));
        vec3 c = liquid(n, tint * (0.7 + 0.6 * sw));
        float lines = smoothstep(0.03, 0.0, abs(fract(sw * 6.0 + Time * 0.2) - 0.5) - 0.45);
        c += tint * lines * 0.8 + vec3(1.0, 0.25, 0.2) * pow(1.0 - n.z, 2.0) * 1.2;
        fragColor = vec4(c * a, a);
    }
}
