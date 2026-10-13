#version 150

// Anime impact frame applied to the whole rendered picture.
// Turns the scene into stark two-tone ink with outlines, slams radial speed lines in from the edges, and splits the
// colour channels. Style: 0 white paper / black ink, 1 black / purple-white (Hollow Purple), 2 black / red,
// 3 black / blue, 4 the "world cut in half" (the screen splits along a blazing seam and slides apart).

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Strength;
uniform float Style;
uniform vec2 Center;
uniform float Seed;
uniform vec2 Texel;

in vec2 uv;

out vec4 fragColor;

float hash(float x) {
    return fract(sin(x * 127.1 + Seed * 31.7) * 43758.5453);
}

float lum(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 p = uv;
    float seam = 0.0;
    if (Style > 3.5) {
        // Cut the world in half: diagonal seam through the centre, halves slide apart.
        vec2 n = normalize(vec2(0.25, 1.0));
        float side = dot(uv - Center, n);
        float slide = 0.06 * Strength;
        p = uv + vec2(n.y, -n.x) * (side > 0.0 ? slide : -slide) + n * sign(side) * slide * 0.3;
        seam = exp(-pow(side / (0.004 + 0.01 * Strength), 2.0));
    }
    vec2 dirv = p - Center;
    float ca = 0.006 * Strength;
    vec3 scene = vec3(texture(DiffuseSampler, p + dirv * ca).r,
                      texture(DiffuseSampler, p).g,
                      texture(DiffuseSampler, p - dirv * ca).b);
    float l = lum(scene);

    // Ink outlines from a quick edge detect.
    float lx = lum(texture(DiffuseSampler, p + vec2(Texel.x * 1.5, 0.0)).rgb) - lum(texture(DiffuseSampler, p - vec2(Texel.x * 1.5, 0.0)).rgb);
    float ly = lum(texture(DiffuseSampler, p + vec2(0.0, Texel.y * 1.5)).rgb) - lum(texture(DiffuseSampler, p - vec2(0.0, Texel.y * 1.5)).rgb);
    float edge = smoothstep(0.06, 0.2, length(vec2(lx, ly)));

    // Hard threshold with a little jitter so it flickers between frames like hand-drawn impact frames.
    float th = 0.42 + 0.08 * (hash(floor(Time * 2.0)) - 0.5);
    float ink = step(th, l);

    // Radial speed lines from the edges toward the centre.
    vec2 rel = (uv - Center) * vec2(Texel.y / Texel.x, 1.0);
    float ang = atan(rel.y, rel.x);
    float rad = length(rel);
    float id = floor(ang * 90.0 / 3.14159);
    float line = step(0.72, hash(id + floor(Time * 3.0) * 7.0)) * smoothstep(0.18, 0.75, rad)
               * smoothstep(0.5, 0.0, abs(fract(ang * 90.0 / 3.14159) - 0.5) * 2.0 - 0.25);

    vec3 light, dark, lineCol;
    if (Style < 0.5) {
        light = vec3(1.0); dark = vec3(0.02); lineCol = vec3(0.0);
    } else if (Style < 1.5) {
        light = vec3(0.93, 0.8, 1.0); dark = vec3(0.04, 0.0, 0.08); lineCol = vec3(0.75, 0.45, 1.0);
        ink = 1.0 - ink;       // inverted: bright things go dark, darkness blazes violet-white
    } else if (Style < 2.5) {
        light = vec3(1.0, 0.85, 0.8); dark = vec3(0.06, 0.0, 0.0); lineCol = vec3(1.0, 0.15, 0.1);
        ink = 1.0 - ink;
    } else if (Style < 3.5) {
        light = vec3(0.85, 0.95, 1.0); dark = vec3(0.0, 0.01, 0.05); lineCol = vec3(0.3, 0.6, 1.0);
        ink = 1.0 - ink;
    } else {
        light = vec3(1.0); dark = vec3(0.0); lineCol = vec3(0.0);
    }
    vec3 styled = mix(dark, light, ink);
    styled = mix(styled, Style < 0.5 || Style > 3.5 ? vec3(0.0) : light, edge * 0.9);
    styled = mix(styled, lineCol, line * 0.9);
    vec3 outc = mix(scene, styled, Strength);
    if (Style > 3.5) {
        // The seam blazes white-blue; past full strength the picture fades into the void beyond the cut.
        outc = mix(outc, vec3(0.85, 0.95, 1.0), seam);
    }
    fragColor = vec4(outc, 1.0);
}
