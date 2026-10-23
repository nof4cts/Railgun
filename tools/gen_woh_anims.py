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
# An original pose vocabulary for a reality-overwriting Stand user: deliberate, imperious,
# hand-extended "decrees" and time-halts rather than a brawler's punches. Nothing carried
# over from Modulo Yuji's motion set. Keyed to each move's timeline in common/Script.java.

REST   = P(right_arm=[3, 0, 5], left_arm=[3, 0, -5])                                                   # calm stand
READY  = P(right_arm=[-16, 0, 9], left_arm=[-10, 0, -6], torso=[2, 7, 0], head=[0, -5, 0], right_leg=[-6, 0, 4], left_leg=[6, 0, -4], pos=[0, -0.3, 0])
DECREE = P(right_arm=[-92, -8, 0], left_arm=[-40, -18, -8], torso=[8, -22, 0], head=[2, 18, 0], right_leg=[-12, 0, 5], left_leg=[16, 0, -5], pos=[0, -0.6, -0.9])
DECR2  = P(right_arm=[-92, -10, 0], left_arm=[-92, 10, 0], torso=[6, 0, 0], head=[6, 0, 0], right_leg=[-14, 0, 5], left_leg=[16, 0, -5], pos=[0, -0.8, -1.0])
HALT   = P(right_arm=[-104, -22, 0], left_arm=[-24, -10, -8], torso=[2, -14, 0], head=[2, 12, 0], right_leg=[-8, 0, 5], left_leg=[10, 0, -5])
RAISE  = P(right_arm=[-164, 0, 14], left_arm=[-164, 0, -14], torso=[-12, 0, 0], head=[-26, 0, 0], right_leg=[0, 0, 6], left_leg=[0, 0, -6])
REND   = P(right_arm=[-68, 34, 42], left_arm=[-68, -34, -42], torso=[0, 0, 0], head=[-8, 0, 0], right_leg=[-6, 0, 6], left_leg=[6, 0, -6], pos=[0, -0.4, 0])
SLAMD  = P(right_arm=[-8, 0, 22], left_arm=[-8, 0, -22], torso=[26, 0, 0], head=[20, 0, 0], right_leg=[-30, 0, 6], left_leg=[-30, 0, -6], pos=[0, -2.2, 0])
GATHER = P(right_arm=[-62, 36, 10], left_arm=[-62, -36, -10], torso=[16, 0, 0], head=[12, 0, 0], right_leg=[-26, 0, 6], left_leg=[-26, 0, -6], pos=[0, -1.4, 0])
FLICK  = P(right_arm=[-84, -44, 22], left_arm=[-34, -16, -10], torso=[6, -26, 0], head=[0, 22, 0], right_leg=[-8, 0, 5], left_leg=[12, 0, -5])
ASCEND = P(right_arm=[-128, 0, 30], left_arm=[-128, 0, -30], torso=[-14, 0, 0], head=[-28, 0, 0], right_leg=[2, 0, 6], left_leg=[2, 0, -6], pos=[0, 0.9, 0])
WARD   = P(right_arm=[-106, 34, 0], left_arm=[-106, -34, 0], torso=[10, 0, 0], head=[8, 0, 0], right_leg=[-8, 0, 5], left_leg=[8, 0, -5], pos=[0, -0.5, 0])
OPENF  = P(right_arm=[-24, 0, 78], left_arm=[-24, 0, -78], torso=[-8, 0, 0], head=[-14, 0, 0], right_leg=[0, 0, 10], left_leg=[0, 0, -10], pos=[0, -0.5, 0])
VOW    = P(right_arm=[-116, 28, 0], left_arm=[-70, -28, 0], torso=[6, 0, 0], head=[8, 0, 0], right_leg=[-6, 0, 5], left_leg=[8, 0, -5], pos=[0, -0.3, 0])
GRACE  = P(right_arm=[-34, 0, 54], left_arm=[-34, 0, -54], torso=[-8, 0, 0], head=[-14, 0, 0], right_leg=[0, 0, 7], left_leg=[0, 0, -7], pos=[0, -0.3, 0])
POINTU = P(right_arm=[-150, -10, 10], left_arm=[16, 0, -14], torso=[-10, -14, 0], head=[-20, 10, 0], right_leg=[-6, 0, 5], left_leg=[8, 0, -5])

def decree_snap(a, t, pose, wind=GATHER, over=1.12, hold=8, rec=REST):
    """A WOH decree: gather -> palm thrust snaps on tick t -> overshoot -> hold -> settle."""
    a.k(t - 4, wind, OUT_).k(t - 2, push(READY, wind, 1.05), INOUT)
    a.k(t - 1, push(READY, pose, 0.82), IN).k(t, pose, SNAP).k(t + 2, push(READY, pose, over), OUT_).k(t + 4, pose, INOUT)
    if hold: a.hold(t + 6, t + 4 + hold, pose, 1.0, 5)
    if rec is not None: a.k(t + 6 + hold, rec, OUT_)
    return a

# --- page 1: the fists (overwrite strikes)
a = Anim('ascended_barrage', 90, REST)
a.k(6, READY, OUT_)
for i, t in enumerate([8, 11, 14, 17, 20, 23, 26, 29, 33, 37]):
    pose = DECREE if i % 2 == 0 else DECR2
    a.k(t - 1, push(READY, pose, 0.82), IN).k(t, pose, SNAP).k(t + 1, push(READY, pose, 1.12), OUT_)
a.k(40, add(DECR2, pos=[0, -1.0, -1.4]), OUT_)
a.hold(46, 72, READY, 1.0, 5).k(88, REST, OUT_)
a.write()

a = Anim('overwrite_blow', 110, REST)
a.k(6, READY, OUT_).k(10, GATHER, OUT_).tremble(10, 24, GATHER, 2.0)
decree_snap(a, 28, add(DECREE, pos=[0, -1.4, -1.9]), wind=GATHER, over=1.15, hold=12, rec=REST)
a.write()

a = Anim('erasure_hook', 72, REST)
a.k(5, READY, OUT_).k(6, add(DECREE, right_arm=[-70, -40, 24]), OUT_).hold(8, 12, add(DECREE, right_arm=[-70, -40, 24]), 1.0, 3)
decree_snap(a, 14, add(DECREE, right_arm=[-96, -20, 10]), wind=READY, over=1.1, hold=8, rec=REST)
a.write()

a = Anim('rewrite_rush', 84, REST)
a.k(2, READY, OUT_)
t = 0
for i in range(5):
    pose = DECREE if i % 2 == 0 else DECR2
    a.k(t + 3, push(READY, pose, 0.82), IN).k(t + 4, pose, SNAP).k(t + 6, push(READY, pose, 1.12), OUT_)
    t += 7
a.hold(t + 2, min(82, t + 18), READY, 1.0, 4).k(82, REST, OUT_)
a.write()

# --- page 2: reality overwrite
a = Anim('reality_overwrite', 96, REST)
a.k(6, READY, OUT_).k(10, GATHER, OUT_).tremble(10, 18, GATHER, 2.2)
a.k(19, push(READY, DECR2, 0.8), IN).k(20, add(DECR2, pos=[0, -0.8, -1.1]), SNAP).k(22, push(READY, DECR2, 1.14), OUT_)
a.hold(26, 60, DECR2, 1.0, 5).k(94, REST, OUT_)
a.write()

a = Anim('wounds_undone', 56, REST)
a.k(4, VOW, OUT_).hold(8, 44, VOW, 1.3, 6).k(54, REST, OUT_)
a.write()

a = Anim('grace_over_heaven', 60, REST)
a.k(4, GRACE, OUT_).hold(8, 48, GRACE, 1.6, 7).k(58, REST, OUT_)
a.write()

a = Anim('negation', 40, REST)
a.k(2, push(READY, WARD, 0.9), IN).k(3, WARD, SNAP).hold(6, 34, WARD, 1.1, 5).k(38, REST, OUT_)
a.write()

# --- page 3: heaven's judgment
a = Anim('heavenly_lightning', 70, REST)
a.k(4, READY, OUT_).k(6, POINTU, OUT_).tremble(8, 12, POINTU, 1.4)
SMITE = P(right_arm=[-70, -20, 0], left_arm=[18, 0, -14], torso=[14, -20, 0], head=[6, 16, 0], right_leg=[-18, 0, 6], left_leg=[22, 0, -6], pos=[0, -1.0, -0.6])
a.k(15, push(READY, SMITE, 0.85), IN).k(16, SMITE, SNAP).k(18, push(READY, SMITE, 1.1), OUT_)
a.hold(20, 60, SMITE, 1.0, 5).k(68, REST, OUT_)
a.write()

a = Anim('fourteen_phrases', 150, REST)
a.k(6, REND, OUT_).hold(10, 66, REND, 1.6, 6)
a.k(70, push(REND, DECR2, 0.85), IN).k(72, add(DECR2, pos=[0, -1.0, -1.0]), SNAP).k(74, push(REND, DECR2, 1.1), OUT_)
a.hold(80, 140, DECR2, 1.0, 6).k(148, REST, OUT_)
a.write()

a = Anim('reality_knives', 48, REST)
a.k(1, READY, OUT_)
for i in range(7):
    tt = 2 + i
    a.k(tt, add(FLICK, right_arm=[-84 + (i % 2) * 8, -44 + i * 4, 22], torso=[6, -26 + i * 2, 0]), SNAP if i % 2 == 0 else OUT_)
a.k(14, READY, OUT_).hold(18, 44, READY, 1.0, 5).k(46, REST, OUT_)
a.write()

a = Anim('road_to_heaven', 72, REST)
a.k(2, add(GATHER, pos=[0, -1.8, 0]), IN)
LIFT = P(right_arm=[-172, 0, 12], left_arm=[-172, 0, -12], torso=[-16, 0, 0], head=[-30, 0, 0], right_leg=[-6, 0, 7], left_leg=[-6, 0, -7], pos=[0, 0.6, 0])
a.k(6, LIFT, SNAP).k(10, push(REST, LIFT, 1.1), OUT_).hold(14, 60, LIFT, 1.0, 5).k(70, REST, OUT_)
a.write()

# --- page 4: the world (time)
a = Anim('time_stop', 120, REST)
a.k(2, push(READY, HALT, 0.9), IN).k(3, HALT, SNAP)
for i, t in enumerate([8, 18, 28, 38]):
    pose = DECREE if i % 2 == 0 else DECR2
    a.k(t - 1, push(HALT, pose, 0.82), IN).k(t, pose, SNAP).k(t + 2, push(HALT, pose, 1.05), OUT_)
a.k(60, add(DECR2, pos=[0, -1.0, -1.2]), OUT_)
a.hold(66, 110, READY, 1.0, 5).k(118, REST, OUT_)
a.write()

a = Anim('knife_rain', 150, REST)
a.k(2, RAISE, SNAP).hold(6, 66, RAISE, 1.3, 6)
a.k(70, push(RAISE, SLAMD, 0.85), IN).k(72, SLAMD, SNAP).k(75, push(RAISE, SLAMD, 1.1), OUT_)
a.hold(80, 140, SLAMD, 1.0, 6).k(148, REST, OUT_)
a.write()

a = Anim('stillness', 56, REST)
a.k(1, push(READY, HALT, 0.9), IN).k(2, HALT, SNAP).hold(5, 50, HALT, 0.8, 5).k(54, REST, OUT_)
a.write()

# --- page 5: over heaven (ultimates)
a = Anim('worlds_truth', 260, REST)
a.k(4, RAISE, OUT_).hold(10, 40, RAISE, 1.2, 6).tremble(40, 84, RAISE, 2.4)
a.k(84, push(RAISE, SLAMD, 0.85), IN).k(86, SLAMD, SNAP).k(89, push(RAISE, SLAMD, 1.12), OUT_)
a.hold(94, 250, SLAMD, 1.0, 7).k(258, REST, OUT_)
a.write()

a = Anim('return_over_heaven', 56, REST)
a.k(2, VOW, OUT_).hold(6, 44, VOW, 1.2, 6).k(54, REST, OUT_)
a.write()

a = Anim('heaven_ascension', 64, REST)
a.k(2, add(GATHER, pos=[0, -1.6, 0]), IN).k(6, ASCEND, SNAP).k(10, push(REST, ASCEND, 1.1), OUT_)
a.hold(14, 56, ASCEND, 1.4, 6).k(62, REST, OUT_)
a.write()

a = Anim('reality_collapse', 130, REST)
a.k(2, GATHER, IN).k(6, add(REND, torso=[-6, 0, 0]), OUT_)
a.k(8, push(REND, OPENF, 0.9), IN).k(10, OPENF, SNAP).hold(14, 120, OPENF, 1.4, 7).k(128, REST, OUT_)
a.write()

a = Anim('collapse_finisher', 110, REST)
a.k(2, RAISE, OUT_)
a.k(5, push(RAISE, SLAMD, 0.85), IN).k(6, SLAMD, SNAP).k(9, push(RAISE, SLAMD, 1.12), OUT_)
a.hold(12, 100, SLAMD, 1.0, 6).k(108, REST, OUT_)
a.write()

# subtitles
names = ["charge", "black_flash", "black_flash_big", "dash", "blood_form", "blood_fire", "blood_hit", "blood_rain", "dismantle", "cleave",
         "world_splitter", "crater", "eruption", "domain", "domain_hum", "overdrive", "wave", "world_ender", "rumble", "impact", "meteor", "card", "time_stop_voice"]
pretty = {"black_flash": "Reality overwrite", "world_ender": "The world is overwritten", "wave": "Reality cracks", "rumble": "Time halts", "card": "The World Over Heaven", "time_stop_voice": "The world stops"}
lang = {"subtitles.woh." + n: pretty.get(n, n.replace("_", " ").capitalize()) for n in names}
os.makedirs(os.path.join(ROOT, "lang"), exist_ok=True)
with open(os.path.join(ROOT, "lang", "en_us.json"), "w") as f:
    json.dump(lang, f, indent=1)
print("wrote lang", len(lang))
