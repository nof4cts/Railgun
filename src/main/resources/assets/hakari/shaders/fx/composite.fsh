#version 150
#include "common.glsl"
// The last pass, in film order: zoom punch toward the hit -> chromatic aberration -> bloom -> haki grade ->
// desaturation and contrast -> coloured vignette -> flash -> grain -> anime impact frame.
//
// Impact frames are the anime "genga" hit frames: for a tick or two at a time the whole picture is redrawn as stark
// two-tone ink (shapes found with a LOCAL brightness threshold, so they survive a white-out), outlined where the
// brightness changes, toned with manga screentone in the midtones, and torn or slashed through the point of impact.

uniform sampler2D Scene;
uniform sampler2D Bloom;
uniform vec2 ScreenSize;
uniform float Time;
uniform vec4 Flash;        // rgb, strength (above 1 burns to white)
uniform vec4 Impact;       // mode, amount, centre uv
uniform float ImpactSeed;
uniform vec3 Accent;
uniform float Punch;       // radial zoom blur toward the impact centre
uniform float Aberration;
uniform float Desaturate;
uniform float Contrast;
uniform vec4 Vignette;     // rgb, strength
uniform float Grain;
uniform float Haki;        // cursed-energy grade: tinted shadows, hot highlights, crackling at the frame edges
uniform vec3 GradeShadow;
uniform vec3 GradeHot;
uniform float BloomStrength;
uniform vec2 Shake;

in vec2 uv;
out vec4 fragColor;

vec3 scene(vec2 p) {
    return texture(Scene, clamp(p, vec2(0.001), vec2(0.999))).rgb;
}

vec3 fetch(vec2 p, vec2 dir, float ca) {
    if (ca < 1e-5) {
        return scene(p);
    }
    return vec3(scene(p + dir * ca).r, scene(p).g, scene(p - dir * ca).b);
}

/** Focus lines: thin wedges converging on c, sparse near it, dense at the frame edge. */
float focusLines(vec2 p, vec2 c, float aspect, float seed, float density) {
    vec2 d = (p - c) * vec2(aspect, 1.0);
    float r = length(d);
    float a = atan(d.y, d.x);
    float n = 220.0;
    float cell = floor((a + PI) / TAU * n);
    float h = hash11(cell * 1.731 + seed * 7.13);
    float centre = fract((a + PI) / TAU * n);
    float width = 0.18 + 0.3 * hash11(cell + seed);
    float line = step(1.0 - density, h) * smoothstep(width, 0.0, abs(centre - 0.5));
    float start = 0.16 + 0.22 * hash11(cell * 3.1 + seed);
    return line * smoothstep(start, start + 0.18, r);
}

/** Black lightning: a few jagged bolts out of the hit, flickering per frame. 1 on the bolt core. */
float blackLightning(vec2 p, vec2 c, float aspect, float seed, out float glow) {
    vec2 d = (p - c) * vec2(aspect, 1.0);
    float r = length(d);
    float a = atan(d.y, d.x);
    float core = 0.0;
    glow = 0.0;
    for (int k = 0; k < 7; k++) {
        float base = hash11(seed * 3.7 + float(k) * 11.3) * TAU;
        float wobble = (fbm(vec2(r * 9.0, seed + float(k) * 4.0)) - 0.5) * 0.9;
        float off = abs(angDiff(a, base + wobble)) * r;
        float reach = 0.35 + 0.6 * hash11(seed + float(k) * 2.9);
        float on = 1.0 - smoothstep(reach * 0.8, reach, r);
        core = max(core, on * (1.0 - smoothstep(0.0015, 0.004, off)));
        glow = max(glow, on * exp(-off * 70.0));
    }
    return core;
}

void main() {
    vec2 p = uv + Shake;
    float aspect = ScreenSize.x / ScreenSize.y;
    vec2 ic = Impact.zw;
    float mode = Impact.x;
    bool impact = mode > 0.5 && Impact.y > 0.001;

    // impact tear: the frame splits into horizontal slices that jump sideways, a new pattern every frame
    if (impact && (mode > 7.5 || (mode > 1.5 && mode < 3.5))) {
        float band = floor(p.y * 14.0 + hash11(ImpactSeed) * 6.0);
        float jump = (hash12(vec2(band, ImpactSeed)) - 0.5) * step(0.62, hash12(vec2(band * 1.7, ImpactSeed + 3.0)));
        p.x += jump * 0.045;
    }

    // zoom punch toward the hit, with aberration that grows toward the edges
    vec2 pc = impact ? ic : vec2(0.5);
    vec2 c = p - 0.5;
    float ca = Aberration * 0.012 * dot(c * vec2(aspect, 1.0), c * vec2(aspect, 1.0)) * 2.0;
    vec3 col;
    vec3 bloom;
    if (Punch > 0.001) {
        vec3 acc = vec3(0.0);
        vec3 bacc = vec3(0.0);
        float total = 0.0;
        for (int i = 0; i < 12; i++) {
            float k = float(i) / 12.0;
            vec2 q = mix(p, pc, k * Punch * 0.18);
            float w = 1.0 - k * 0.65;
            acc += fetch(q, c, ca) * w;
            bacc += texture(Bloom, q).rgb * w;
            total += w;
        }
        col = acc / total;
        bloom = bacc / total;
    } else {
        col = fetch(p, c, ca);
        bloom = texture(Bloom, p).rgb;
    }

    // bloom as a soft screen blend
    vec3 b = bloom * BloomStrength;
    col = col + b - col * b * 0.4;

    // Enma's grade: the colour bleeds out, shadows sink to violet-black, the brights stay hot
    if (Haki > 0.001) {
        float l = lum(col);
        vec3 graded = mix(GradeShadow, GradeHot, smoothstep(0.08, 0.95, l));
        graded = mix(graded, col * (0.6 + 0.6 * GradeHot), smoothstep(0.7, 1.0, l) * 0.6);
        col = mix(col, graded, Haki * 0.75);
    }

    float grey = lum(col);
    col = mix(col, vec3(grey) * vec3(0.95, 1.0, 1.02), saturate(Desaturate));
    if (Contrast > 0.001) {
        col = clamp((col - 0.45) * (1.0 + Contrast) + 0.45, 0.0, 1.5);
    }

    float edge = smoothstep(0.3, 0.98, length(c * vec2(aspect * 0.8, 1.0)) * 1.25);
    col = mix(col, Vignette.rgb * (0.3 + 0.7 * grey), saturate(Vignette.a * edge));

    col += Flash.rgb * Flash.a;
    col = mix(col, vec3(1.0), saturate(Flash.a - 1.0));

    if (Grain > 0.001) {
        col += (hash12(uv * ScreenSize + fract(Time * 7.3) * 431.0) - 0.5) * Grain * 0.16;
    }

    if (impact) {
        vec2 px = 1.5 / ScreenSize;
        float gx = lum(scene(p + vec2(px.x, 0))) - lum(scene(p - vec2(px.x, 0)));
        float gy = lum(scene(p + vec2(0, px.y))) - lum(scene(p - vec2(0, px.y)));
        float ink = smoothstep(0.05, 0.16, length(vec2(gx, gy)));
        float l = lum(scene(p));
        float avg = 0.0;
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.785398;
            avg += lum(scene(p + vec2(cos(a) / aspect, sin(a)) * 0.04));
        }
        avg /= 8.0;
        // threshold half local, half absolute: shapes survive a white-out, yet a flat bright sky stays paper
        float thr = mix(avg, 0.45, 0.45);
        float two = step(thr + 0.004, l);
        float core = max(step(avg + 0.2, l), step(0.93, l));
        // manga screentone only where the light is shading softly, never on flat areas
        vec2 tp = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (uv * ScreenSize) / 5.0;
        float shade = abs(l - avg);
        float dots = step(length(fract(tp) - 0.5), sqrt(saturate(1.0 - l)) * 0.62);
        float mid = smoothstep(0.012, 0.035, shade) * (1.0 - smoothstep(0.09, 0.18, shade)) * (1.0 - ink);
        vec2 d = (uv - ic) * vec2(aspect, 1.0);
        vec3 frame;

        if (mode < 1.5) {
            // INK: black ink on paper
            frame = mix(vec3(two), vec3(1.0 - dots * 0.85), mid * 0.8) * (1.0 - ink);
        } else if (mode < 2.5) {
            // INVERT: white shapes on black
            frame = vec3(1.0) - mix(vec3(two), vec3(1.0 - dots), mid * 0.7) * (1.0 - ink);
        } else if (mode < 3.5) {
            // CRIMSON: black and blood red, white where it burns
            vec3 red = mix(vec3(0.85, 0.02, 0.05), Accent, 0.35);
            frame = mix(vec3(0.02, 0.0, 0.0), red, two);
            frame = mix(frame, vec3(1.0, 0.95, 0.9), core);
            frame = mix(frame, red * dots, mid * 0.6);
            frame *= 1.0 - ink;
        } else if (mode < 4.5) {
            // NEGATIVE: every hue flipped and pushed
            vec3 neg = 1.0 - scene(p);
            float g = lum(neg);
            frame = clamp((mix(vec3(g), neg, 1.9) - 0.5) * 1.7 + 0.5, 0.0, 1.0) * (1.0 - ink * 0.7);
        } else if (mode < 5.5) {
            // WHITEOUT: the frame burns white, only the hardest edges and darkest shapes survive as ink
            float hard = smoothstep(0.12, 0.3, length(vec2(gx, gy)));
            frame = vec3(1.0, 0.99, 0.96);
            frame = mix(frame, vec3(0.0), max(hard, step(l, avg - 0.12) * 0.85));
            frame = mix(frame, Accent, (1.0 - two) * 0.12);
        } else if (mode < 6.5) {
            // HALFTONE: big accent dots on black, white at the core
            vec2 hp = mat2(0.866, -0.5, 0.5, 0.866) * (uv * ScreenSize) / 12.0;
            float big = step(length(fract(hp) - 0.5), sqrt(saturate(l * 1.25 - 0.12)) * 0.66);
            frame = mix(vec3(0.01), Accent, big);
            frame = mix(frame, vec3(1.0), core);
            frame = mix(frame, vec3(0.0), ink * 0.85);
        } else if (mode < 7.5) {
            // HAKI: the world on black, its edges lit in the accent, black lightning out of the hit
            vec3 black = vec3(0.01, 0.0, 0.02);
            frame = mix(black, Accent * 0.9, ink);
            frame = mix(frame, Accent * 0.35, two * 0.35);
            frame = mix(frame, vec3(1.0, 0.97, 1.0), core);
            float glow;
            float bolt = blackLightning(uv, ic, aspect, ImpactSeed, glow);
            frame = mix(frame, Accent * 1.6, glow * 0.8);
            frame = mix(frame, vec3(0.0), bolt);
        } else if (mode < 8.5) {
            // SLASHED: cut through the hit at a new angle each frame; ink one side, inverted the other, a white tear
            float ang = hash11(ImpactSeed + 3.0) * PI;
            vec2 nrm = vec2(cos(ang), sin(ang));
            float along = dot(d, vec2(-nrm.y, nrm.x));
            float side = dot(d, nrm) + (vnoise(vec2(along * 40.0, ImpactSeed)) - 0.5) * 0.012;
            float s = step(0.0, side);
            float lit = mix(two, 1.0 - two, s);
            frame = vec3(lit) * (1.0 - ink * (1.0 - s)) + vec3(ink * s);
            frame = mix(frame, Accent, (1.0 - lit) * s * 0.25);
            frame = mix(frame, vec3(1.0), saturate(exp(-abs(side) / 0.004) * 1.8));
        } else if (mode < 9.5) {
            // LINES: ink, with heavy focus lines converging on the hit
            frame = mix(vec3(two), vec3(1.0 - dots * 0.85), mid * 0.8) * (1.0 - ink);
            float fl = focusLines(uv, ic, aspect, ImpactSeed, 0.45);
            frame = mix(frame, vec3(1.0 - two), fl);
        } else if (mode < 10.5) {
            // RAINBOW: the pachinko "confirmed" frame - the world posterised into the spectrum by light, rings of it
            // racing out of the centre, white where it burns, inked edges
            float band = floor(saturate((l - avg) * 2.6 + 0.5) * 6.999);
            float ring = floor(length(d) * 10.0 - Time * 8.0 + ImpactSeed);
            float hue = fract((band + ring) / 7.0);
            vec3 rgb = clamp(abs(mod(hue * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
            frame = mix(rgb, vec3(1.0), core);
            frame = mix(frame, vec3(0.0), ink * 0.85);
        } else {
            // JACKPOT: the world in cursed-energy green on black, gold where it burns hottest, focus lines in green
            vec3 black = vec3(0.0, 0.02, 0.01);
            frame = mix(black, Accent, two);
            frame = mix(frame, vec3(1.0, 0.86, 0.3), core);
            frame = mix(frame, Accent * dots, mid * 0.6);
            frame *= 1.0 - ink * 0.9;
            float fl = focusLines(uv, ic, aspect, ImpactSeed, 0.3);
            frame = mix(frame, Accent * 1.3, fl * 0.8);
        }

        // focus lines over the paper-and-ink modes
        if (mode < 2.5 || (mode > 7.5 && mode < 8.5)) {
            float fl = focusLines(uv, ic, aspect, ImpactSeed, 0.22);
            frame = mix(frame, vec3(1.0) - frame, fl * 0.9);
        }

        col = mix(col, frame, saturate(Impact.y));
    }

    fragColor = vec4(col, 1.0);
}
