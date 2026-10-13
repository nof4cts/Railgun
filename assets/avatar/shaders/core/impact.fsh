#version 150

// Anime impact frame applied to the whole rendered picture.
// Turns the scene into stark two-tone ink with outlines, slams radial speed lines in from the edges, and splits the
// colour channels. Style: 0 white paper / black ink, 1 black / purple-white (Hollow Purple), 2 black / red,
// 3 black / electric blue, 4 the "world cut in half", 5 manga panels (slanted panel gutters + halftone dots),
// 6 inverted colours (negative film with blood-red speed lines), 7 warm freeze frame, 8 negative film with electric-blue lines.

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
    if (Style > 3.5 && Style < 4.5) {
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
        light = vec3(0.8, 0.97, 1.0); dark = vec3(0.0, 0.02, 0.06); lineCol = vec3(0.35, 0.9, 1.0);
        ink = 1.0 - ink;
    } else if (Style < 4.5) {
        light = vec3(1.0); dark = vec3(0.0); lineCol = vec3(0.0);
    } else {
        light = vec3(1.0); dark = vec3(0.03); lineCol = vec3(0.0);
    }
    vec3 styled = mix(dark, light, ink);
    styled = mix(styled, Style < 0.5 || Style > 3.5 ? vec3(0.0) : light, edge * 0.9);
    if (Style > 4.5 && Style < 5.5) {
        // Halftone dots on the mid-tones, and slanted white gutters splitting the frame into manga panels.
        vec2 px = uv / Texel;
        vec2 cell = fract(px / 7.0) - 0.5;
        float dotR = clamp((0.62 - l) * 0.9, 0.0, 0.5);
        float dots = step(length(cell), dotR);
        styled = mix(vec3(1.0), vec3(0.03), max(dots, 1.0 - ink));
        styled = mix(styled, vec3(0.0), edge);
        float g1 = abs(uv.x - 0.38 - (uv.y - 0.5) * 0.25);
        float g2 = abs(uv.y - 0.55 - (uv.x - 0.5) * 0.12);
        float gutter = step(g1, 0.008) + step(g2, 0.008) * step(0.38 + (uv.y - 0.5) * 0.25, uv.x);
        float border = step(min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y)), 0.012);
        styled = mix(styled, vec3(1.0), clamp(gutter + border, 0.0, 1.0));
        float frameLine = step(abs(g1 - 0.011), 0.002) + step(abs(g2 - 0.011), 0.002) * step(0.38 + (uv.y - 0.5) * 0.25, uv.x);
        styled = mix(styled, vec3(0.0), clamp(frameLine, 0.0, 1.0));
    }
    if (Style > 5.5 && Style < 8.5) {
        // Negative film: every colour flipped, outlines in deep red, speed lines blood red.
        styled = vec3(1.0) - scene;
        styled = mix(styled, vec3(dot(styled, vec3(0.33))) * vec3(1.0, 0.92, 0.92), 0.35);
        bool elec = Style > 7.5;   // 8: negative film with electric-blue lines (lightning)
        styled = mix(styled, elec ? vec3(0.0, 0.08, 0.35) : vec3(0.35, 0.0, 0.0), edge);
        lineCol = elec ? vec3(0.3, 0.65, 1.0) : vec3(0.75, 0.0, 0.02);
    }
    styled = mix(styled, lineCol, line * 0.9);
    vec3 outc = mix(scene, styled, Strength);
    if (Style > 3.5 && Style < 4.5) {
        // The seam blazes white-blue; past full strength the picture fades into the void beyond the cut.
        outc = mix(outc, vec3(0.85, 0.95, 1.0), seam);
    }
    if (Style > 6.5 && Style < 7.5) {
        // 7: freeze frame - the held picture goes warm and grainy with a dark vignette (no ink).
        vec2 vc = (uv - 0.5) * vec2(Texel.y / Texel.x, 1.0);
        float vig = smoothstep(0.85, 0.25, length(vc));
        vec3 warm = mix(vec3(lum(scene)), scene, 0.55) * vec3(1.12, 0.95, 0.78);
        float grain = (hash(floor(uv.x / Texel.x) * 0.13 + floor(uv.y / Texel.y) * 7.1 + floor(Time)) - 0.5) * 0.06;
        outc = mix(scene, warm * (0.35 + 0.65 * vig) + grain, Strength);
    }
    if (Style > 8.5) {
        vec2 rc = (uv - vec2(0.5, -0.05)) * vec2(Texel.y / Texel.x, 1.0);
        float rr = length(rc);
        float rings = smoothstep(0.08, 0.0, abs(fract(rr * 3.5 - Time * 0.12) - 0.5) - 0.42) * smoothstep(1.4, 0.2, rr);
        vec3 sense = vec3(0.015) + vec3(1.0) * edge * 1.2 + vec3(0.85, 0.9, 1.0) * rings * 0.55;
        outc = mix(scene, sense, Strength);
    }
    fragColor = vec4(outc, 1.0);
}
