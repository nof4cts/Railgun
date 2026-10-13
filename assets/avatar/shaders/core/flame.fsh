#version 150

// Fire, smoke and plasma drawn on camera-facing quads and ribbons. Shapes come from animated noise, so there is
// no texture and no pixel grid. Output is premultiplied alpha (blend ONE, ONE_MINUS_SRC_ALPHA): glowing parts add
// light (alpha 0), smoke darkens what is behind it (alpha > 0), both in one pass.
//
// Vertex colour carries parameters: r = random seed, g = heat (0 cold smoke .. 1 white-hot), b = density, a = opacity.
//   Mode 0 puff (fireball / smoke ball)      Mode 1 trail ribbon (UV0.x 0 at head .. 1 at tail)
//   Mode 2 beam                               Mode 3 ground shock ring (g = ring radius 0..1)
//   Mode 4 soft glow / flash                  Mode 5 ember streak
//   Mode 6 ground flame (UV0.y 0 bottom .. 1 top)
//   Mode 7 white vapor / steam   Mode 8 ash   Mode 9 spores
//   Mode 13 lightning ribbon. In modes 4, 5, 10-12 a blue (b > 0.75) vertex colour switches to Azula's blue fire.
//   Mode 10 anime cel puff   Mode 11 anime flame tongue ribbon   Mode 12 anime upright flame

uniform float Mode;
uniform float Time;
uniform float Length;
uniform float Bright;
uniform vec3 SmokeLight;

in vec2 vUV;
in vec4 vColor;
in vec3 vAux;

out vec4 fragColor;

vec3 hash33(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float gnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    return mix(mix(mix(dot(hash33(i), f),
                       dot(hash33(i + vec3(1, 0, 0)), f - vec3(1, 0, 0)), u.x),
                   mix(dot(hash33(i + vec3(0, 1, 0)), f - vec3(0, 1, 0)),
                       dot(hash33(i + vec3(1, 1, 0)), f - vec3(1, 1, 0)), u.x), u.y),
               mix(mix(dot(hash33(i + vec3(0, 0, 1)), f - vec3(0, 0, 1)),
                       dot(hash33(i + vec3(1, 0, 1)), f - vec3(1, 0, 1)), u.x),
                   mix(dot(hash33(i + vec3(0, 1, 1)), f - vec3(0, 1, 1)),
                       dot(hash33(i + vec3(1, 1, 1)), f - vec3(1, 1, 1)), u.x), u.y), u.z);
}

float fbm(vec3 p, int oct) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < oct; i++) {
        s += a * gnoise(p);
        p = p * 2.07 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

vec3 fireRamp(float t) {
    t = clamp(t, 0.0, 1.5);
    vec3 c = mix(vec3(0.0), vec3(0.45, 0.03, 0.0), smoothstep(0.0, 0.25, t));
    c = mix(c, vec3(1.0, 0.28, 0.03), smoothstep(0.2, 0.5, t));
    c = mix(c, vec3(1.0, 0.62, 0.18), smoothstep(0.45, 0.75, t));
    c = mix(c, vec3(1.0, 0.93, 0.75), smoothstep(0.7, 1.0, t));
    return mix(c, vec3(1.35), smoothstep(1.0, 1.5, t));
}

vec3 blueRamp(float t) {
    t = clamp(t, 0.0, 1.5);
    vec3 c = mix(vec3(0.0), vec3(0.02, 0.08, 0.45), smoothstep(0.0, 0.25, t));
    c = mix(c, vec3(0.1, 0.4, 1.0), smoothstep(0.2, 0.5, t));
    c = mix(c, vec3(0.45, 0.8, 1.0), smoothstep(0.45, 0.75, t));
    c = mix(c, vec3(0.85, 0.96, 1.0), smoothstep(0.7, 1.0, t));
    return mix(c, vec3(1.35), smoothstep(1.0, 1.5, t));
}

vec3 smokeColor(float n, float heat) {
    vec3 c = mix(vec3(0.045, 0.04, 0.04), vec3(0.2, 0.175, 0.16), clamp(n + 0.5, 0.0, 1.0));
    return c + SmokeLight * (0.35 + 0.65 * clamp(n + 0.5, 0.0, 1.0)) + vec3(0.5, 0.14, 0.02) * heat * 0.6;
}


// ---------------- anime (cel-shaded) fire ----------------
// Hard-edged flat colour bands (dark red rim, orange body, yellow, white-hot core) with thin bright "lava" veins,
// like hand-drawn anime fire. g = heat (1 fresh .. 0 burnt out: the shape erodes away instead of turning to smoke).
bool BLUE = false;
int PAL = 0;   // 0 fire, 1 blue fire, 2 water, 3 ice, 4 air, 5 Yuta's cursed energy, 6 Rika (black-violet)

vec3 celFire(float depth, float heat, float vein, float aa) {
    vec3 dark = BLUE ? vec3(0.04, 0.12, 0.62) : vec3(0.80, 0.17, 0.04);
    vec3 body = BLUE ? vec3(0.10, 0.42, 1.00) : vec3(1.00, 0.47, 0.07);
    vec3 hot = BLUE ? vec3(0.42, 0.80, 1.00) : vec3(1.00, 0.76, 0.22);
    vec3 core = BLUE ? vec3(0.90, 0.98, 1.00) : vec3(1.00, 0.96, 0.78);
    if (PAL == 2) { dark = vec3(0.05, 0.30, 0.62); body = vec3(0.20, 0.58, 0.90); hot = vec3(0.55, 0.86, 1.0); core = vec3(0.95, 1.0, 1.0); }
    if (PAL == 3) { dark = vec3(0.35, 0.62, 0.80); body = vec3(0.62, 0.86, 0.97); hot = vec3(0.85, 0.96, 1.0); core = vec3(1.0); }
    if (PAL == 4) { dark = vec3(0.74, 0.77, 0.80); body = vec3(0.90, 0.92, 0.93); hot = vec3(0.97, 0.98, 0.98); core = vec3(1.0); }
    // JJK cursed energy: inked near-black rim, electric blue body, white-hot core (Yuta) / black-violet (Rika).
    if (PAL == 5) { dark = vec3(0.02, 0.02, 0.07); body = vec3(0.20, 0.34, 0.96); hot = vec3(0.62, 0.84, 1.0); core = vec3(1.0); }
    if (PAL == 6) { dark = vec3(0.0); body = vec3(0.035, 0.015, 0.06); hot = vec3(0.13, 0.05, 0.21); core = vec3(0.36, 0.22, 0.52); }
    vec3 col = dark;
    col = mix(col, body, smoothstep(0.08 - aa, 0.08 + aa, depth));
    col = mix(col, hot, smoothstep(0.30 - aa, 0.30 + aa, depth));
    col = mix(col, core, smoothstep(0.52 - aa, 0.52 + aa, depth * (0.55 + 0.45 * heat)));
    // Veins: thin bright lines through the body.
    col = mix(col, PAL == 0 ? vec3(1.0, 0.95, 0.62) : PAL == 4 ? vec3(0.55, 0.60, 0.66) : PAL == 6 ? vec3(0.28, 0.13, 0.42) : vec3(0.92, 0.99, 1.0),
              vein * smoothstep(0.06, 0.16, depth) * (PAL == 2 ? 0.7 : 1.0));
    // Cooling: dims (and reddens, for orange fire) as it burns out.
    if (PAL < 2) col = mix(col * (BLUE ? vec3(0.45, 0.55, 0.85) : vec3(0.95, 0.5, 0.32)), col, smoothstep(0.0, 0.5, heat));
    return col;
}

float veinLine(float n, float aa) {
    return 1.0 - smoothstep(0.012, 0.035 + aa, abs(n));
}

float palAlpha() {
    return PAL == 4 ? 0.62 : PAL == 2 ? 0.74 : PAL == 3 ? 0.8 : PAL == 5 ? 0.92 : PAL == 6 ? 0.95 : 1.0;
}

bool anime(out vec4 res, float seed, float heat, float op) {
    op *= palAlpha();
    if (Mode > 13.5) {
        // Mode 14: round cel blob (water drops and globs, air puffs, dust-free shapes).
        vec2 c = vUV * 2.0 - 1.0;
        float r = length(c);
        float n = fbm(vec3(c * 1.3, seed + Time * 0.05), 3);
        float field = 0.8 - r + n * 0.35 - (1.0 - heat) * 0.5;
        if (field <= 0.0) return false;
        float aa = max(fwidth(field), 1e-4);
        float cover = smoothstep(0.0, aa * 1.5, field);
        float vein = veinLine(fbm(vec3(c * 1.8 + 7.0, seed * 0.7 + Time * 0.04), 3), aa) * 0.6;
        // Light from the upper left: a crisp highlight crescent like hand-drawn water.
        float spec = smoothstep(0.62 - aa, 0.62 + aa, dot(normalize(c + vec2(0.0001)), vec2(-0.6, 0.8)) * (1.0 - r) * 2.2);
        vec3 col = celFire(field * 1.1 + 0.1, 1.0, vein, aa);
        col = mix(col, vec3(1.0), spec * 0.8);
        float a = cover * op;
        res = vec4(col * a * Bright, a * 0.92);
        return true;
    }
    if (Mode < 10.5) {
        // Mode 10: a flame (blast head, trail, explosion): round at the base, splitting into 2-3 pointed licking
        // tongues toward the top of the quad (the quad is kept upright, so the tips point up like real fire).
        vec2 c = vUV * 2.0 - 1.0;
        float y = c.y;
        float n = fbm(vec3(c.x * 1.6, c.y * 1.4 - Time * 0.16, seed), 3);
        float n2 = fbm(vec3(c * 3.2 + 3.0, seed * 1.3 - Time * 0.22), 2);
        // Body: wide round bottom, tapering to a point at the top.
        float body = y < 0.0 ? sqrt(max(0.0, 1.0 - y * y)) * 0.72 : 0.72 * pow(max(0.0, 1.0 - y), 1.15);
        // Sway: the upper part bends with the noise, like a flame in moving air.
        float x = c.x + n * 0.45 * max(0.0, y + 0.6);
        // Tongues: split the upper half into points.
        float k = clamp((y + 0.15) / 1.15, 0.0, 1.0);
        float split = 1.0 - abs(sin((x * 2.4 + n2 * 0.9) * 2.2 + seed * 6.0));
        float field = body - abs(x) - k * k * (0.55 - 0.55 * split) - abs(n2) * 0.18 - (1.0 - heat) * 0.5;
        if (field <= 0.0) return false;
        float aa = max(fwidth(field), 1e-4);
        float cover = smoothstep(0.0, aa * 1.5, field);
        float vein = veinLine(fbm(vec3(c.x * 2.0 + 7.0, c.y * 1.6 - Time * 0.2, seed * 0.7), 3), aa);
        // Hottest low and in the middle, like a real flame.
        vec3 col = celFire(field * 1.2 + (1.0 - k) * 0.22 + heat * 0.08, heat, vein, aa);
        float a = cover * op;
        res = vec4(col * a * 1.1 * Bright, a * 0.9);
        return true;
    }
    if (Mode < 11.5) {
        // Mode 11: licking flame tongue ribbon. UV0.x 0 at the root .. 1 at the tip, UV0.y across.
        float x = vUV.x;
        float y = vUV.y * 2.0 - 1.0;
        float n = fbm(vec3(x * 3.0 - Time * 0.3, y * 1.2, seed), 3);
        float width = (1.0 - x) * (0.8 + 0.35 * n);
        float yc = y + sin(x * 8.0 - Time * 0.7 + seed * 6.0) * 0.18 * x;
        float field = width - abs(yc) - abs(n) * 0.3 * x;
        if (field <= 0.0) return false;
        float aa = max(fwidth(field), 1e-4);
        float cover = smoothstep(0.0, aa * 1.5, field);
        float vein = veinLine(fbm(vec3(x * 4.5 - Time * 0.45, y * 1.8, seed + 4.0), 3), aa);
        float depth = field / max(width, 0.08) * 0.6 * (1.0 - x * 0.5) + (1.0 - x) * 0.2;
        vec3 col = celFire(depth, heat, vein, aa);
        float a = cover * op;
        res = vec4(col * a * 1.1 * Bright, a * 0.9);
        return true;
    }
    // Mode 12: upright cel flame (burning fists). UV0.y 0 at the base .. 1 at the top.
    vec2 q = vec2(vUV.x * 2.0 - 1.0, vUV.y);
    float n = fbm(vec3(q.x * 1.8, q.y * 2.2 - Time * 0.12, seed), 3);
    float n2 = fbm(vec3(q.x * 4.0, q.y * 4.5 - Time * 0.2, seed + 3.0), 2);
    float width = mix(0.9, 0.0, pow(q.y, 0.8));
    float field = width - abs(q.x + n * 0.45 * q.y) - n2 * 0.18 * q.y - smoothstep(0.0, 0.08, -q.y + 0.04);
    if (field <= 0.0) return false;
    float aa = max(fwidth(field), 1e-4);
    float cover = smoothstep(0.0, aa * 1.5, field);
    float vein = veinLine(fbm(vec3(q * 2.5 + 9.0, seed - Time * 0.08), 3), aa);
    vec3 col = celFire(field * 1.3 + (1.0 - q.y) * 0.15, heat, vein, aa);
    float a = cover * op;
    res = vec4(col * a * 1.1 * Bright, a * 0.9);
    return true;
}

void main() {
    float seed = vColor.r * 97.0;
    float heat = vColor.g;
    float dens = vColor.b;
    float op = vColor.a;
    BLUE = vColor.b > 0.96;
    PAL = BLUE ? 1 : vColor.b > 0.92 ? 3 : vColor.b > 0.85 ? 2 : vColor.b > 0.78 ? 4 : vColor.b > 0.70 ? 5 : vColor.b > 0.62 ? 6 : 0;
    if (Mode > 12.5 && Mode < 13.5) {
        // Mode 13: lightning (ribbon, UV0.x along, UV0.y across): white-hot core, electric blue halo, flickering.
        // g = intensity. Additive.
        float y = abs(vUV.y * 2.0 - 1.0);
        float fl = 0.75 + 0.25 * sin(Time * 9.0 + seed * 3.0 + vUV.x * 20.0);
        float core = exp(-y * y * 70.0);
        float halo = exp(-y * y * 5.0);
        vec3 e3 = vec3(1.0) * core * 1.6 + vec3(0.25, 0.55, 1.0) * halo * 0.9;
        fragColor = vec4(e3 * heat * op * fl * Bright, 0.0);
        return;
    }
    if (Mode > 9.5) {
        vec4 res;
        if (!anime(res, seed, heat, op)) discard;
        fragColor = res;
        return;
    }
    vec3 emit = vec3(0.0);
    float alpha = 0.0;

    if (Mode < 0.5) {
        // ---------------- puff ----------------
        vec2 c = vUV * 2.0 - 1.0;
        float r = length(c);
        if (r > 1.0) discard;
        float n = fbm(vec3(c * 1.25, seed + Time * 0.02), 3);
        float n2 = fbm(vec3(c * 3.2 + 7.0, seed * 1.7 - Time * 0.035), 2);
        float shape = smoothstep(1.0, 0.2, r + n * 0.9 + n2 * 0.3);
        if (shape <= 0.002) discard;
        float core = shape * (0.7 + 0.9 * n + 0.4 * n2);
        float temp = heat * clamp(core, 0.0, 1.3) * 1.2;
        emit = fireRamp(temp) * smoothstep(0.05, 0.4, temp) * 1.5 * shape;
        alpha = shape * dens * (1.0 - clamp(heat * 1.15, 0.0, 1.0) * 0.8);
        emit += smokeColor(n2, heat) * alpha;
    } else if (Mode < 1.5) {
        // ---------------- trail ribbon ----------------
        float x = vUV.x;
        float y = abs(vUV.y * 2.0 - 1.0);
        float n = fbm(vec3(x * Length * 0.045 - Time * 0.09, vUV.y * 2.2, seed), 4);
        float n2 = fbm(vec3(x * Length * 0.12 - Time * 0.2, vUV.y * 5.0 + 3.0, seed + 5.0), 3);
        float shape = smoothstep(1.0, 0.15, y + n * (0.5 + 0.6 * x) + n2 * 0.2) * smoothstep(1.0, 0.75, x);
        if (shape <= 0.002) discard;
        float temp = heat * pow(1.0 - x, 1.6) * (1.0 - y * 0.6) * (1.0 + n * 0.8);
        emit = fireRamp(temp * 1.05) * smoothstep(0.03, 0.3, temp) * 1.15 * shape;
        alpha = shape * dens * smoothstep(0.02, 0.35, x) * (1.0 - clamp(temp * 1.5, 0.0, 1.0));
        emit += smokeColor(n2, temp) * alpha;
    } else if (Mode < 2.5) {
        // ---------------- beam ----------------
        float y = abs(vUV.y * 2.0 - 1.0);
        float n = fbm(vec3(vUV.x * Length * 0.08 - Time * 1.2, vUV.y * 3.0, seed), 3);
        float core = exp(-y * y * 60.0);
        float halo = exp(-y * y * 5.0) * (0.55 + 0.45 * n);
        float edge = exp(-pow(abs(y - 0.45 - n * 0.2) * 9.0, 2.0)) * 0.35;
        emit = (vec3(1.3, 1.2, 1.0) * core + vec3(1.0, 0.36, 0.06) * halo + vec3(1.0, 0.15, 0.02) * edge) * op;
        alpha = 0.0;
        op = 1.0;
    } else if (Mode < 3.5) {
        // ---------------- ground shock ring ----------------
        vec2 c = vUV * 2.0 - 1.0;
        float r = length(c);
        float ang = atan(c.y, c.x);
        float n = fbm(vec3(cos(ang) * 3.0, sin(ang) * 3.0, seed + r * 4.0), 4);
        float R = heat;
        float w = 0.03 + 0.05 * dens;
        float ring = exp(-pow((r - R + n * 0.04) / w, 2.0));
        float inner = smoothstep(R, R * 0.4, r) * 0.35 * (0.6 + n);
        emit = fireRamp(0.65 + 0.4 * (1.0 - R)) * ring * (0.8 + n) * 1.4 + vec3(1.0, 0.35, 0.05) * inner * 0.3;
        alpha = (ring * 0.4 + inner * 0.5) * dens;
        emit += smokeColor(n, 0.2) * alpha * 0.6;
    } else if (Mode < 4.5) {
        // ---------------- soft glow ----------------
        vec2 c = vUV * 2.0 - 1.0;
        float r2 = dot(c, c);
        float g = max(exp(-r2 * 4.5) - 0.011, 0.0) + max(exp(-r2 * 30.0) - 0.0, 0.0) * 0.6;
        emit = (PAL == 0 ? fireRamp(heat * 1.3) : PAL == 4 ? vec3(0.9, 0.95, 1.0) * heat : PAL == 1 ? blueRamp(heat * 1.3)
                : mix(vec3(0.1, 0.5, 1.0), vec3(0.85, 1.0, 1.0), heat) * heat) * g;
        alpha = 0.0;
    } else if (Mode < 5.5) {
        // ---------------- ember streak ----------------
        float x = vUV.x;
        float y = abs(vUV.y * 2.0 - 1.0);
        float g = exp(-y * y * 6.0) * smoothstep(1.0, 0.0, x) * smoothstep(0.0, 0.08, x);
        emit = (PAL == 0 ? fireRamp(heat * (1.2 - x * 0.6)) : PAL == 1 ? blueRamp(heat * (1.2 - x * 0.6))
                : vec3(0.75, 0.92, 1.0) * heat * (1.0 - x * 0.5)) * g * 1.8;
        alpha = 0.0;
    } else if (Mode < 6.5) {
        // ---------------- ground flame ----------------
        vec2 q = vec2(vUV.x * 2.0 - 1.0, vUV.y);
        float n = fbm(vec3(q.x * 1.8, q.y * 2.2 - Time * 0.09, seed), 4);
        float n2 = fbm(vec3(q.x * 4.5, q.y * 5.0 - Time * 0.16, seed + 3.0), 3);
        float width = mix(0.95, 0.12, q.y);
        float flame = smoothstep(width, width * 0.3, abs(q.x + n * 0.35 * q.y) + n2 * 0.25)
                    * smoothstep(1.0, 0.35, q.y + n * 0.55 + n2 * 0.2) * smoothstep(0.0, 0.1, q.y);
        if (flame <= 0.002) discard;
        float temp = heat * flame * (1.3 - q.y * 0.8) * (0.8 + n2);
        emit = fireRamp(temp) * smoothstep(0.03, 0.3, temp) * 1.7 * flame;
        alpha = flame * dens * smoothstep(0.55, 1.0, q.y) * 0.5;
        emit += smokeColor(n, 0.3) * alpha;
    }
    if (Mode > 6.5 && Mode < 7.5) {
        // ---------------- white vapor (contrails, condensation) ----------------
        vec2 c = vUV * 2.0 - 1.0;
        float r = length(c);
        if (r > 1.0) discard;
        float n = fbm(vec3(c * 1.4, seed + Time * 0.015), 3);
        float n2 = fbm(vec3(c * 3.4 + 4.0, seed - Time * 0.02), 2);
        float shape = smoothstep(1.0, 0.25, r + n * 0.8 + n2 * 0.25);
        if (shape <= 0.002) discard;
        vec3 light = clamp(SmokeLight * 1.8 + vec3(0.42), 0.0, 1.0);
        vec3 c3 = mix(vec3(0.62, 0.63, 0.66), vec3(0.97, 0.97, 1.0), clamp(n2 + 0.65, 0.0, 1.0)) * light;
        alpha = shape * dens;
        emit = c3 * alpha + fireRamp(heat) * shape * heat * 0.8;
    } else if (Mode > 8.5) {
        // ---------------- toxic spores / acid ----------------
        vec2 c = vUV * 2.0 - 1.0;
        float r = length(c);
        if (r > 1.0) discard;
        float n = fbm(vec3(c * 2.2, seed + Time * 0.06), 2);
        float core = smoothstep(1.0, 0.05, r + n * 0.45);
        if (core <= 0.002) discard;
        vec3 green = mix(vec3(0.3, 0.6, 0.25), vec3(0.6, 0.85, 0.45), clamp(heat * core, 0.0, 1.0));
        alpha = core * dens * 0.55;
        emit = green * core * (0.3 + heat * 0.7) + vec3(0.04, 0.1, 0.03) * alpha;
    } else if (Mode > 7.5) {
        // ---------------- falling ash flake ----------------
        vec2 c = vUV * 2.0 - 1.0;
        float r = length(c * vec2(1.0, 1.0 + 0.6 * heat));
        float shape = smoothstep(1.0, 0.2, r);
        if (shape <= 0.002) discard;
        alpha = shape * dens;
        emit = mix(vec3(0.45, 0.44, 0.43), vec3(0.78, 0.77, 0.75), seed / 97.0) * alpha;
    }
    fragColor = vec4(emit * Bright * op, alpha * op);
}
