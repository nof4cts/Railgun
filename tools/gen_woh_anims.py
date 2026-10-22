"""Modulo Yuji's body (Player Animator, bedrock format): assets/yuji/player_animation/yuji_<move>.json.

Every move has its own choreography, keyed to the same ticks as its timeline (common/Script.java): anticipation
before each blow, a snap into contact, overshoot and follow-through after it, weight carried through the hips, and
a settle back into his stance. Keys are dense and eased so the motion reads as fluid rather than pose-to-pose.
Also writes the subtitle lang file.

    python3 tools/gen_anims.py
"""
import json
import math
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "woh")
OUT = os.path.join(ROOT, "player_animation")
os.makedirs(OUT, exist_ok=True)
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
KEYS = BONES + ['pos']
Z = [0, 0, 0]
# easings (into the key)
SNAP, OUT_, INOUT, IN, BACK, ELASTIC, QUAD = 'easeOutExpo', 'easeOutCubic', 'easeInOutSine', 'easeInCubic', 'easeOutBack', 'easeOutElastic', 'easeInOutQuad'


def P(base=None, **bones):
    out = {b: list((base or {}).get(b, Z)) for b in KEYS}
    for b, v in bones.items():
        out[b] = list(v)
    return out


def add(a, **delta):
    """a with some bones nudged by a delta."""
    out = P(a)
    for b, v in delta.items():
        out[b] = [x + y for x, y in zip(out[b], v)]
    return out


def mix(a, b, u):
    return {k: [x + (y - x) * u for x, y in zip(a[k], b[k])] for k in KEYS}


def push(rest, pose, k):
    """Exaggerate (k > 1) or soften (k < 1) a pose relative to a rest pose: overshoot and anticipation."""
    return {key: [r + (p - r) * k for r, p in zip(rest[key], pose[key])] for key in KEYS}


class Anim:
    def __init__(self, name, length, start):
        self.name, self.length = name, length
        self.keys = [(0, start, None)]

    def k(self, t, pose, ease=INOUT):
        self.keys.append((t, pose, ease))
        return self

    def hold(self, t0, t1, pose, amp=1.5, period=10):
        """Not frozen: a held pose that breathes."""
        t = t0
        i = 0
        while t < t1:
            s = math.sin(i * 1.7) * amp
            self.k(t, add(pose, torso=[s * 0.6, s * 0.3, 0], head=[s * 0.5, 0, 0], right_arm=[s, 0, 0], left_arm=[-s, 0, 0]), INOUT)
            t += period
            i += 1
        return self

    def tremble(self, t0, t1, pose, amp=2.5, step=2):
        i = 0
        for t in range(t0, t1, step):
            s = amp if i % 2 else -amp
            a = amp * (0.4 + 0.6 * (t - t0) / max(1, t1 - t0))   # builds up
            s = a if i % 2 else -a
            self.k(t, add(pose, right_arm=[s, s * 0.4, 0], left_arm=[-s * 0.6, 0, s * 0.3], torso=[0, s * 0.3, 0], head=[s * 0.3, 0, 0]), QUAD)
            i += 1
        return self

    def strike(self, t, pose, rest, wind=None, over=1.12, hold=6, recover=None):
        """Anticipation -> contact on tick t (snapped in one tick) -> overshoot -> follow-through -> hold."""
        if wind is not None:
            self.k(t - 3, wind, OUT_)
            self.k(t - 2, push(rest, wind, 1.08), INOUT)
        self.k(t - 1, push(rest, pose, 0.85), IN)
        self.k(t, pose, SNAP)
        self.k(t + 2, push(rest, pose, over), OUT_)
        self.k(t + 4, pose, INOUT)
        if hold:
            self.hold(t + 6, t + 4 + hold, pose, 1.0, 4)
        if recover is not None:
            self.k(t + 4 + hold + 4, recover, OUT_)
        return self

    def write(self):
        """
        The whole-body lean, twist and drop go on Player Animator's "body" bone (the entire model moves as one),
        never on "torso": in the player model the arms and legs are not attached to the torso, so bending or
        shifting the torso alone tears it away from the hips and shoulders (the "floating torso" look).
        """
        keys = {}
        for t, pose, ease in self.keys:
            keys[min(t, self.length)] = (pose, ease)   # later keys on the same tick win
        bones = {}

        def track(get):
            out = {}
            for t in sorted(keys):
                pose, ease = keys[t]
                k = {'vector': [round(v, 2) for v in get(pose)]}
                if ease:
                    k['easing'] = ease
                out[f'{t / 20.0:.3f}'] = k
            return out

        for b in ['head', 'right_arm', 'left_arm', 'right_leg', 'left_leg']:
            bones[b] = {'rotation': track(lambda pose, b=b: pose[b])}
        # lean and twist of the whole figure (pitch softened a little: it pivots at the feet), and its drop
        bones['body'] = {'rotation': track(lambda pose: [pose['torso'][0] * 0.7, pose['torso'][1], pose['torso'][2] * 0.7]),
                         'position': track(lambda pose: [pose['pos'][0], pose['pos'][1], pose['pos'][2]])}
        data = {'format_version': '1.8.0', 'animations': {'woh_' + self.name: {'animation_length': self.length / 20.0, 'loop': False, 'bones': bones}}}
        with open(os.path.join(OUT, 'woh_' + self.name + '.json'), 'w') as f:
            json.dump(data, f, indent=1)
        print(self.name, self.length, len(keys), 'keys')


# ------------------------------------------------------------------ his vocabulary
STAND = P(right_arm=[4, 0, 6], left_arm=[4, 0, -6])
STANCE = P(right_arm=[-42, 18, 8], left_arm=[-58, -22, -8], torso=[6, 14, 0], head=[0, -12, 0], right_leg=[-10, 0, 5], left_leg=[12, 0, -5], pos=[0, -0.7, 0])
CROUCH = P(right_arm=[25, 0, 22], left_arm=[25, 0, -22], torso=[28, 0, 0], head=[-22, 0, 0], right_leg=[-48, 0, 5], left_leg=[-48, 0, -5], pos=[0, -3.8, 0])


def wind_r(depth=1.0):
    """Right fist drawn back past the hip, left palm out to aim, hips turned away."""
    return P(right_arm=[42 * depth, 14, 30], left_arm=[-78, -28, -6], torso=[10, 36 * depth, 0], head=[4, -30 * depth, 0],
             right_leg=[16, 0, 6], left_leg=[-22, 0, -6], pos=[0, -1.6 * depth, 0.5])


def punch_r(reach=1.0):
    """The straight right: shoulder through, rear leg driving, head behind the fist."""
    return P(right_arm=[-94, -6, 0], left_arm=[30, 6, -24], torso=[14 * reach, -34 * reach, 0], head=[2, 28 * reach, 0],
             right_leg=[-20, 0, 6], left_leg=[34 * reach, 0, -6], pos=[0, -1.4, -1.0 * reach])


def punch_l(reach=1.0):
    return P(left_arm=[-94, 6, 0], right_arm=[30, -6, 24], torso=[14 * reach, 34 * reach, 0], head=[2, -28 * reach, 0],
             left_leg=[-20, 0, -6], right_leg=[34 * reach, 0, 6], pos=[0, -1.4, -1.0 * reach])


UPPER = P(right_arm=[-168, -12, 10], left_arm=[24, 0, -18], torso=[-16, -20, 0], head=[-24, 12, 0], right_leg=[-4, 0, 4], left_leg=[14, 0, -4], pos=[0, 0.4, 0])
CLASP = P(right_arm=[-76, 40, 0], left_arm=[-76, -40, 0], torso=[6, 0, 0], head=[8, 0, 0], right_leg=[-6, 0, 4], left_leg=[8, 0, -4], pos=[0, -0.4, 0])
FINGER = P(right_arm=[-92, 2, 0], left_arm=[-30, -10, -12], torso=[0, -16, 0], head=[0, 14, 0], right_leg=[-10, 0, 4], left_leg=[12, 0, -4])
TWO_HAND = P(right_arm=[-92, 8, 0], left_arm=[-88, -30, 0], torso=[4, -10, 0], head=[2, 8, 0], right_leg=[-14, 0, 5], left_leg=[16, 0, -5], pos=[0, -0.8, 0])
SKY = P(right_arm=[-172, 0, 14], left_arm=[-172, 0, -14], torso=[-12, 0, 0], head=[-30, 0, 0], right_leg=[0, 0, 7], left_leg=[0, 0, -7])
SIGN = P(right_arm=[-102, 30, 0], left_arm=[-102, -30, 0], torso=[2, 0, 0], head=[14, 0, 0], right_leg=[0, 0, 5], left_leg=[0, 0, -5])
ROAR = P(right_arm=[-24, 0, 70], left_arm=[-24, 0, -70], torso=[-14, 0, 0], head=[-30, 0, 0], right_leg=[-8, 0, 12], left_leg=[8, 0, -12], pos=[0, -2.2, 0])
FLEX_IN = P(right_arm=[-60, 50, -10], left_arm=[-60, -50, 10], torso=[22, 0, 0], head=[16, 0, 0], right_leg=[-30, 0, 6], left_leg=[-30, 0, -6], pos=[0, -2.6, 0])


def slash(side, height=0):
    """An open-hand sweep: side +1 = start (hand at the far shoulder), -1 = finish (hand flung out)."""
    return P(right_arm=[-100 - height, 72 * side, -10 * side], left_arm=[14, 0, -14], torso=[2, 38 * side, 0], head=[0, -22 * side, 0],
             right_leg=[10 * side, 0, 4], left_leg=[-10 * side, 0, -4])


def slash_l(side, height=0):
    return P(left_arm=[-100 - height, -72 * side, 10 * side], right_arm=[14, 0, 14], torso=[2, -38 * side, 0], head=[0, 22 * side, 0],
             left_leg=[10 * side, 0, -4], right_leg=[-10 * side, 0, 4])



# ================================================================== The World Over Heaven
# Each move's choreography is keyed to the ticks of its timeline in common/Script.java.
# Shared extra poses (reality-overwrite vocabulary):
GRIP   = P(right_arm=[-60, 40, 10], left_arm=[-60, -40, -10], torso=[18, 0, 0], head=[14, 0, 0], right_leg=[-24, 0, 6], left_leg=[-24, 0, -6], pos=[0, -1.8, 0])
DECREE = P(right_arm=[-100, -8, 0], left_arm=[-30, -12, -10], torso=[4, -22, 0], head=[2, 18, 0], right_leg=[-12, 0, 5], left_leg=[16, 0, -5], pos=[0, -0.8, -0.8])
GUARD  = P(right_arm=[-96, 40, 0], left_arm=[-96, -40, 0], torso=[10, 0, 0], head=[8, 0, 0], right_leg=[-8, 0, 5], left_leg=[8, 0, -5], pos=[0, -0.6, 0])
GRACE  = P(right_arm=[-30, 0, 60], left_arm=[-30, 0, -60], torso=[-8, 0, 0], head=[-16, 0, 0], right_leg=[0, 0, 8], left_leg=[0, 0, -8], pos=[0, -0.4, 0])
HALT   = P(right_arm=[-100, -20, 0], left_arm=[-20, -10, -10], torso=[2, -16, 0], head=[2, 14, 0], right_leg=[-8, 0, 5], left_leg=[10, 0, -5], pos=[0, -0.4, 0])
OPEN   = P(right_arm=[-30, 0, 80], left_arm=[-30, 0, -80], torso=[-10, 0, 0], head=[-18, 0, 0], right_leg=[0, 0, 10], left_leg=[0, 0, -10], pos=[0, -0.6, 0])
SLAM   = P(right_arm=[-10, 0, 30], left_arm=[-10, 0, -30], torso=[26, 0, 0], head=[20, 0, 0], right_leg=[-30, 0, 6], left_leg=[-30, 0, -6], pos=[0, -2.4, 0])

# --- page 1: the fists
a = Anim('ascended_barrage', 90, STAND)
a.k(6, STANCE, OUT_)
for i, t in enumerate([8, 11, 14, 17, 20, 23, 26, 29, 33, 37]):
    pose = punch_r(1.0) if i % 2 == 0 else punch_l(1.0)
    a.k(t - 1, push(STANCE, pose, 0.8), IN).k(t, pose, SNAP).k(t + 1, push(STANCE, pose, 1.1), OUT_)
a.k(40, add(punch_r(1.3), pos=[0, -1.2, -1.4]), OUT_)
a.hold(46, 72, STANCE, 1.0, 5).k(88, STAND, OUT_)
a.write()

a = Anim('overwrite_blow', 110, STAND)
a.k(6, STANCE, OUT_).k(10, GRIP, OUT_).tremble(10, 24, GRIP, 2.0)
a.strike(28, add(punch_r(1.4), pos=[0, -1.6, -1.8]), STANCE, wind=wind_r(1.3), over=1.15, hold=10, recover=STAND)
a.write()

a = Anim('erasure_hook', 72, STAND)
a.k(5, STANCE, OUT_)
REACH = P(right_arm=[-96, -10, 0], left_arm=[-70, -30, 0], torso=[6, -18, 0], head=[0, 14, 0], right_leg=[-10, 0, 5], left_leg=[12, 0, -5], pos=[0, -0.6, 0])
a.k(6, REACH, OUT_).hold(8, 12, REACH, 1.0, 3)
a.strike(12, add(punch_r(1.1), right_arm=[-70, -40, 20]), STANCE, wind=wind_r(0.9), over=1.12, hold=8, recover=STAND)
a.write()

a = Anim('rewrite_rush', 84, STAND)
a.k(2, STANCE, OUT_)
t = 0
for i in range(5):
    pose = punch_r(1.0) if i % 2 == 0 else punch_l(1.0)
    a.k(t + 3, push(STANCE, pose, 0.8), IN).k(t + 4, pose, SNAP).k(t + 6, push(STANCE, pose, 1.1), OUT_)
    t += 7
a.hold(t + 2, min(82, t + 18), STANCE, 1.0, 4).k(82, STAND, OUT_)
a.write()

# --- page 2: reality overwrite
a = Anim('reality_overwrite', 96, STAND)
a.k(6, STANCE, OUT_).k(10, GRIP, OUT_).tremble(10, 18, GRIP, 2.2)
a.k(19, push(STANCE, DECREE, 0.8), IN).k(20, DECREE, SNAP).k(22, push(STANCE, DECREE, 1.12), OUT_)
a.hold(26, 60, DECREE, 1.0, 5).k(94, STAND, OUT_)
a.write()

a = Anim('wounds_undone', 56, STAND)
a.k(4, CLASP, OUT_).hold(8, 44, CLASP, 1.4, 6).k(54, STAND, OUT_)
a.write()

a = Anim('grace_over_heaven', 60, STAND)
a.k(4, GRACE, OUT_).hold(8, 48, GRACE, 1.6, 7).k(58, STAND, OUT_)
a.write()

a = Anim('negation', 40, STAND)
a.k(2, push(STANCE, GUARD, 0.9), IN).k(3, GUARD, SNAP).hold(6, 34, GUARD, 1.2, 5).k(38, STAND, OUT_)
a.write()

# --- page 3: heaven's judgment
a = Anim('heavenly_lightning', 70, STAND)
a.k(4, STANCE, OUT_)
POINT_UP = P(right_arm=[-150, -10, 10], left_arm=[20, 0, -16], torso=[-10, -14, 0], head=[-20, 10, 0], right_leg=[-6, 0, 5], left_leg=[8, 0, -5])
a.k(6, POINT_UP, OUT_).tremble(8, 12, POINT_UP, 1.4)
SMITE = P(right_arm=[-70, -20, 0], left_arm=[20, 0, -16], torso=[14, -20, 0], head=[6, 16, 0], right_leg=[-18, 0, 6], left_leg=[22, 0, -6], pos=[0, -1.0, -0.6])
a.k(15, push(STANCE, SMITE, 0.85), IN).k(16, SMITE, SNAP).k(18, push(STANCE, SMITE, 1.1), OUT_)
a.hold(20, 60, SMITE, 1.0, 5).k(68, STAND, OUT_)
a.write()

a = Anim('fourteen_phrases', 150, STAND)
a.k(6, SIGN, OUT_).hold(10, 66, SIGN, 1.8, 6)
THRUST = add(punch_r(1.0), left_arm=[-94, -30, 0], torso=[6, -6, 0], pos=[0, -1.0, -1.0])
a.k(70, push(SIGN, THRUST, 0.85), IN).k(72, THRUST, SNAP).k(74, push(SIGN, THRUST, 1.1), OUT_)
a.hold(80, 140, THRUST, 1.0, 6).k(148, STAND, OUT_)
a.write()

a = Anim('reality_knives', 48, STAND)
a.k(1, STANCE, OUT_)
THROW = P(right_arm=[-88, -40, 20], left_arm=[-40, -20, -10], torso=[6, -24, 0], head=[0, 20, 0], right_leg=[-8, 0, 5], left_leg=[12, 0, -5])
for i in range(7):
    tt = 2 + i
    a.k(tt, add(THROW, right_arm=[-88 + (i % 2) * 8, -40 + i * 4, 20], torso=[6, -24 + i * 2, 0]), SNAP if i % 2 == 0 else OUT_)
a.k(14, STANCE, OUT_).hold(18, 44, STANCE, 1.0, 5).k(46, STAND, OUT_)
a.write()

a = Anim('road_to_heaven', 72, STAND)
a.k(2, add(CROUCH, pos=[0, -2.0, 0]), IN)
LIFT = P(right_arm=[-175, 0, 12], left_arm=[-175, 0, -12], torso=[-16, 0, 0], head=[-30, 0, 0], right_leg=[-6, 0, 7], left_leg=[-6, 0, -7], pos=[0, 0.6, 0])
a.k(6, LIFT, SNAP).k(10, push(STAND, LIFT, 1.1), OUT_).hold(14, 60, LIFT, 1.0, 5).k(70, STAND, OUT_)
a.write()

# --- page 4: the world (time)
a = Anim('time_stop', 120, STAND)
a.k(2, push(STANCE, HALT, 0.9), IN).k(3, HALT, SNAP)
for i, t in enumerate([8, 18, 28, 38]):
    pose = punch_r(1.0) if i % 2 == 0 else punch_l(1.0)
    a.k(t - 1, push(HALT, pose, 0.8), IN).k(t, pose, SNAP).k(t + 2, push(HALT, pose, 1.05), OUT_)
a.k(60, add(punch_r(1.2), pos=[0, -1.0, -1.2]), OUT_)
a.hold(66, 110, STANCE, 1.0, 5).k(118, STAND, OUT_)
a.write()

a = Anim('knife_rain', 150, STAND)
a.k(2, SKY, SNAP).hold(6, 66, SKY, 1.4, 6)
DOWN = P(right_arm=[-20, 0, 40], left_arm=[-20, 0, -40], torso=[20, 0, 0], head=[16, 0, 0], right_leg=[-20, 0, 6], left_leg=[-20, 0, -6], pos=[0, -1.2, 0])
a.k(70, push(SKY, DOWN, 0.85), IN).k(72, DOWN, SNAP).k(75, push(SKY, DOWN, 1.1), OUT_)
a.hold(80, 140, DOWN, 1.0, 6).k(148, STAND, OUT_)
a.write()

a = Anim('stillness', 56, STAND)
a.k(1, push(STANCE, GUARD, 0.9), IN).k(2, GUARD, SNAP).hold(5, 50, GUARD, 0.8, 5).k(54, STAND, OUT_)
a.write()

# --- page 5: over heaven (ultimates)
a = Anim('worlds_truth', 260, STAND)
a.k(4, SKY, OUT_).hold(10, 40, SKY, 1.2, 6).tremble(40, 84, SKY, 2.5)
a.k(84, push(SKY, SLAM, 0.85), IN).k(86, SLAM, SNAP).k(89, push(SKY, SLAM, 1.12), OUT_)
a.hold(94, 250, SLAM, 1.0, 7).k(258, STAND, OUT_)
a.write()

a = Anim('return_over_heaven', 56, STAND)
VOW = P(right_arm=[-120, 30, 0], left_arm=[-70, -30, 0], torso=[6, 0, 0], head=[10, 0, 0], right_leg=[-6, 0, 5], left_leg=[8, 0, -5], pos=[0, -0.4, 0])
a.k(2, VOW, OUT_).hold(6, 44, VOW, 1.2, 6).k(54, STAND, OUT_)
a.write()

a = Anim('heaven_ascension', 64, STAND)
a.k(2, add(FLEX_IN, pos=[0, -2.0, 0]), IN).k(6, ROAR, SNAP).k(10, push(STAND, ROAR, 1.1), OUT_)
a.hold(14, 56, ROAR, 1.6, 6).k(62, STAND, OUT_)
a.write()

a = Anim('reality_collapse', 130, STAND)
a.k(2, CLASP, IN).k(6, add(SIGN, torso=[-6, 0, 0]), OUT_)
a.k(8, push(SIGN, OPEN, 0.9), IN).k(10, OPEN, SNAP).hold(14, 120, OPEN, 1.4, 7).k(128, STAND, OUT_)
a.write()

a = Anim('collapse_finisher', 110, STAND)
a.k(2, SKY, OUT_)
a.k(5, push(SKY, SLAM, 0.85), IN).k(6, SLAM, SNAP).k(9, push(SKY, SLAM, 1.12), OUT_)
a.hold(12, 100, SLAM, 1.0, 6).k(108, STAND, OUT_)
a.write()

# subtitles (the sound set is reused; keys live under subtitles.woh.*)
names = ["charge", "black_flash", "black_flash_big", "dash", "blood_form", "blood_fire", "blood_hit", "blood_rain", "dismantle", "cleave",
         "world_splitter", "crater", "eruption", "domain", "domain_hum", "overdrive", "wave", "world_ender", "rumble", "impact", "meteor", "card"]
pretty = {"black_flash": "Overwrite strike", "world_ender": "Reality is overwritten", "wave": "Reality cracks", "rumble": "Time halts", "card": "The World Over Heaven"}
lang = {"subtitles.woh." + n: pretty.get(n, n.replace("_", " ").capitalize()) for n in names}
os.makedirs(os.path.join(ROOT, "lang"), exist_ok=True)
with open(os.path.join(ROOT, "lang", "en_us.json"), "w") as f:
    json.dump(lang, f, indent=1)
print("wrote lang", len(lang))
