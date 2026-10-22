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

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "yuji")
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
        data = {'format_version': '1.8.0', 'animations': {'yuji_' + self.name: {'animation_length': self.length / 20.0, 'loop': False, 'bones': bones}}}
        with open(os.path.join(OUT, 'yuji_' + self.name + '.json'), 'w') as f:
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


# ================================================================== World Breaker (the one Black Flash)
a = Anim('world_breaker', 142, STAND)
a.k(6, STANCE, OUT_)
FOCUS = P(right_arm=[-70, 20, 0], left_arm=[-62, -46, 0], torso=[18, 6, 0], head=[22, -4, 0], right_leg=[-24, 0, 6], left_leg=[-6, 0, -6], pos=[0, -2.0, 0])
a.k(12, FOCUS, OUT_).tremble(14, 28, FOCUS, 1.6)             # head down, left hand clamped on the right forearm, pouring it into the fist
a.k(30, mix(FOCUS, wind_r(), 0.5), INOUT)
a.k(34, wind_r(1.2), OUT_).tremble(36, 56, wind_r(1.25), 3.0)  # coiled, shaking with it
a.k(58, push(STAND, wind_r(1.25), 1.15), IN)                # the last inhale
a.k(61, P(punch_r(0.4), right_arm=[30, 10, 26], pos=[0, -1.8, -1.5]), SNAP)   # the step in
a.strike(66, punch_r(1.3), STAND, wind=None, over=1.18, hold=18)
a.k(92, push(STAND, punch_r(1.0), 0.8), INOUT)
a.k(104, STANCE, OUT_).hold(106, 128, STANCE, 1.2, 8)
a.k(142, STAND, INOUT)
a.write()

# ---- Black Flash: Ricochet - five blows from five directions, hands and feet
KICK_R = P(right_leg=[-30, 0, 70], left_leg=[0, 0, -10], torso=[0, -30, -24], head=[0, 30, 10], right_arm=[-30, 0, 30], left_arm=[-60, -20, -30], pos=[0, 0.6, 0])
AXE = P(right_leg=[-150, 0, 6], left_leg=[10, 0, -6], torso=[-20, 0, 0], head=[10, 0, 0], right_arm=[20, 0, 30], left_arm=[20, 0, -30], pos=[0, 1.0, 0])
AXE_DOWN = P(right_leg=[-40, 0, 6], left_leg=[20, 0, -6], torso=[30, 0, 0], head=[20, 0, 0], right_arm=[30, 0, 40], left_arm=[30, 0, -40], pos=[0, -1.0, 0])
a = Anim('ricochet', 80, STAND)
a.k(3, P(punch_r(0.5), right_arm=[40, 0, 26], torso=[26, 0, 0], pos=[0, -1.4, -1.2]), SNAP)      # the dash
a.k(6, wind_r(0.8), OUT_)
a.strike(8, punch_r(1.1), STAND, hold=0)                                       # 1: the straight right
a.k(12, add(KICK_R, right_leg=[0, 0, -30]), OUT_)                              # 2: the roundhouse
a.strike(16, KICK_R, STAND, over=1.1, hold=0)
a.k(21, CROUCH, OUT_)                                                          # 3: under it - the rising uppercut
a.strike(24, UPPER, STAND, over=1.1, hold=0)
a.k(30, AXE, OUT_)                                                             # 4: the axe kick from above
a.strike(34, AXE_DOWN, STAND, over=1.1, hold=0)
a.k(42, CROUCH, OUT_).tremble(43, 47, CROUCH, 2.0, 1)
a.strike(48, add(UPPER, right_arm=[-8, 0, 0], pos=[0, 1.2, 0]), STAND, over=1.15, hold=8)    # 5: the launcher
a.k(62, add(STANCE, head=[-30, 0, 0]), OUT_)                                   # watching it go up
a.hold(64, 74, add(STANCE, head=[-30, 0, 0]), 0.8, 5)
a.k(80, STAND, INOUT)
a.write()

# ---- Black Flash: Resonance - one short jab, two fingers following the lightning, a snap to set it all off
JAB = P(right_arm=[-90, -2, 0], left_arm=[-50, -20, -8], torso=[6, -18, 0], head=[0, 14, 0], right_leg=[-8, 0, 4], left_leg=[14, 0, -4], pos=[0, -0.8, -0.4])
TRACK = P(right_arm=[-96, 0, 0], left_arm=[-20, 0, -10], torso=[0, 0, 0], head=[0, 0, 0], right_leg=[0, 0, 5], left_leg=[0, 0, -5])
SNAPF = P(right_arm=[-120, 30, 0], left_arm=[0, 0, -8], torso=[-4, 10, 0], head=[-6, -8, 0], right_leg=[0, 0, 5], left_leg=[0, 0, -5])
a = Anim('resonance', 96, STAND)
a.k(4, add(STANCE, right_arm=[20, 0, 0]), OUT_).tremble(6, 12, add(STANCE, right_arm=[20, 0, 0]), 1.2, 2)
a.k(13, add(JAB, right_arm=[50, 0, 0]), IN)
a.strike(14, JAB, STAND, over=1.08, hold=0)                                     # a short, almost lazy jab
for i, t in enumerate(range(18, 34, 4)):                                       # two fingers follow the lightning target to target
    a.k(t, add(TRACK, right_arm=[0, (i % 2 * 2 - 1) * 28, 0], torso=[0, (i % 2 * 2 - 1) * 14, 0], head=[0, (i % 2 * 2 - 1) * 20, 0]), INOUT)
a.k(40, SNAPF, OUT_).hold(41, 45, SNAPF, 0.5, 2)
a.k(46, add(SNAPF, right_arm=[12, -20, 0]), SNAP)                               # the snap: resonance
a.k(50, add(SNAPF, right_arm=[20, -20, 0]), OUT_)
a.k(60, add(STANCE, head=[4, 0, 0]), INOUT).hold(62, 82, STANCE, 0.8, 8)
a.k(96, STAND, INOUT)
a.write()

# ---- Reverse Cursed Technique x100: palms pressed together at the chest, a deep breath, the body rebuilt
a = Anim('rct', 44, STAND)
PRAY = P(right_arm=[-70, 36, 0], left_arm=[-70, -36, 0], torso=[4, 0, 0], head=[18, 0, 0], right_leg=[0, 0, 4], left_leg=[0, 0, -4])
a.k(5, PRAY, OUT_).hold(6, 16, PRAY, 0.6, 3)
INHALE = P(right_arm=[12, 0, 10], left_arm=[12, 0, -10], torso=[-12, 0, 0], head=[-26, 0, 0], right_leg=[0, 0, 6], left_leg=[0, 0, -6], pos=[0, 0.4, 0])
a.k(18, INHALE, SNAP).k(24, add(INHALE, torso=[4, 0, 0], head=[10, 0, 0]), OUT_)   # chest up, a deep breath, then let out
a.k(32, add(STANCE, head=[-4, 0, 0]), INOUT)
a.k(44, STAND, INOUT)
a.write()

# ---- 4th Wall Breaker: a wide slash across the air in front of him, toward the screen
a = Anim('fourth_wall', 80, STAND)
a.k(6, add(STANCE, right_arm=[-120, 60, 0]), OUT_).tremble(8, 16, add(STANCE, right_arm=[-120, 60, 0]), 1.5)
a.k(17, P(right_arm=[-70, 70, 0], left_arm=[20, 0, -10], torso=[0, 40, 0], head=[0, -26, 0]), IN)
a.k(19, P(right_arm=[-80, -78, 0], left_arm=[24, 0, -18], torso=[6, -42, 0], head=[0, 30, 0], right_leg=[-12, 0, 6], left_leg=[14, 0, -6], pos=[0, -1.2, 0]), SNAP)   # the cut
a.k(24, push(STAND, P(right_arm=[-70, -70, 0], torso=[4, -30, 0]), 1.1), OUT_)
a.hold(28, 44, add(STANCE, head=[0, 6, 0]), 0.6, 7)
a.k(80, STAND, INOUT)
a.write()

# ---- As God As My Witness: the right hand raised as if swearing an oath, eyes to the sky
OATH = P(right_arm=[-168, -6, -10], left_arm=[-30, 30, -10], torso=[-6, 0, 0], head=[-22, 0, 0], right_leg=[0, 0, 5], left_leg=[0, 0, -5])
a = Anim('as_god', 40, STAND)
a.k(4, add(OATH, right_arm=[40, 0, 0]), OUT_)
a.k(10, OATH, OUT_).k(12, add(OATH, right_arm=[-6, 0, 0], head=[-6, 0, 0]), SNAP)
a.hold(14, 28, OATH, 0.6, 5)
a.k(40, STAND, INOUT)
a.write()

# ---- The End: the chokehold, held all the way down
CHOKE = P(right_arm=[-88, -62, 0], left_arm=[-96, 48, 0], torso=[-8, 6, 0], head=[14, -10, 0], right_leg=[-10, 0, 6], left_leg=[14, 0, -6], pos=[0, -0.6, 0])
a = Anim('the_end', 96, STAND)
a.k(3, P(punch_r(0.4), right_arm=[30, 0, 20], left_arm=[30, 0, -20], torso=[24, 0, 0], pos=[0, -1.2, -1.0]), SNAP)   # around behind them
a.k(6, push(STAND, CHOKE, 1.1), SNAP).k(9, CHOKE, OUT_)
a.tremble(10, 62, CHOKE, 1.4, 3)                              # holding on, the whole way down
a.k(66, STAND, SNAP)                                         # back on the surface
a.k(72, add(STANCE, head=[10, 0, 0]), OUT_).hold(74, 88, add(STANCE, head=[10, 0, 0]), 0.6, 7)
a.k(96, STAND, INOUT)
a.write()

# ================================================================== Blood
a = Anim('piercing_blood', 30, STAND)
a.k(2, add(CLASP, torso=[10, 0, 0], head=[12, 0, 0]), OUT_)        # palms meet, a small bow over them
a.k(6, add(CLASP, right_arm=[6, 4, 0], left_arm=[6, -4, 0]), INOUT)   # compressing
a.k(8, push(STAND, FINGER, 0.9), SNAP)
a.k(9, add(FINGER, right_arm=[-14, 0, 0], torso=[-6, 0, 0], pos=[0, 0, 0.6]), SNAP)   # recoil kicks the hand up
a.k(12, FINGER, BACK).hold(14, 20, FINGER, 0.8, 3)
a.k(30, STAND, INOUT)
a.write()

a = Anim('homing_blood', 40, STAND)
a.k(3, CLASP, OUT_)
a.k(7, add(CLASP, torso=[0, 20, 0]), INOUT)
for i, t in enumerate(range(8, 15)):                       # the beams fan out as the hand sweeps across
    u = i / 6
    a.k(t, P(FINGER, right_arm=[-96 + 10 * math.sin(u * 3.14), 50 - 100 * u, 0], torso=[0, 18 - 36 * u, 0], head=[0, -10 + 24 * u, 0]), INOUT)
a.k(18, add(FINGER, right_arm=[0, -50, 0], torso=[0, -18, 0]), OUT_).hold(20, 30, add(FINGER, right_arm=[0, -50, 0], torso=[0, -18, 0]), 0.8, 4)
a.k(40, STAND, INOUT)
a.write()

a = Anim('supernova', 62, STAND)
a.k(4, CLASP, OUT_)
HOLD_UP = P(right_arm=[-150, -20, 20], left_arm=[-86, -10, 0], torso=[-4, 18, 0], head=[-8, -14, 0], right_leg=[10, 0, 5], left_leg=[-12, 0, -5])
a.k(12, HOLD_UP, OUT_).hold(13, 17, HOLD_UP, 1.2, 2)        # palm up over his shoulder, the orbs circling it; the left hand marks the target
COCK = P(right_arm=[-170, 30, 10], left_arm=[-70, -20, 0], torso=[-14, 34, 0], head=[-4, -24, 0], right_leg=[18, 0, 5], left_leg=[-20, 0, -5], pos=[0, -0.6, 0.6])
a.k(19, COCK, IN)
PITCH = P(right_arm=[-60, -24, 0], left_arm=[20, 10, -20], torso=[26, -30, 0], head=[6, 24, 0], right_leg=[-26, 0, 5], left_leg=[32, 0, -5], pos=[0, -1.6, -1.2])
a.strike(22, PITCH, STAND, over=1.12, hold=0)                  # pitched overhand
a.k(30, add(PITCH, right_arm=[-30, 0, 0], torso=[-10, 0, 0]), INOUT)
a.k(32, P(PITCH, right_arm=[-120, -10, 0], torso=[10, -20, 0]), SNAP)    # the fingers spread: Supernova
a.hold(34, 46, P(PITCH, right_arm=[-120, -10, 0], torso=[10, -20, 0]), 1.0, 4)
a.k(62, STAND, INOUT)
a.write()

a = Anim('blood_storm', 112, STAND)
a.k(6, CLASP, OUT_)
a.k(14, add(CLASP, right_arm=[-30, -20, 0], left_arm=[-30, 20, 0]), INOUT)
CONDUCT_UP = P(right_arm=[-170, -10, 20], left_arm=[-40, 0, -30], torso=[-8, -10, 0], head=[-24, 6, 0], right_leg=[0, 0, 6], left_leg=[0, 0, -6])
CONDUCT_DOWN = P(right_arm=[-70, -20, 20], left_arm=[-60, 0, -40], torso=[14, -6, 0], head=[6, 4, 0], right_leg=[-10, 0, 6], left_leg=[10, 0, -6], pos=[0, -0.8, 0])
a.k(20, CONDUCT_UP, BACK)
for w in range(4):                                         # each wave called down with a sweep of the arm
    t0 = 24 + w * 20
    side = 1 if w % 2 == 0 else -1
    a.k(t0 + 2, add(CONDUCT_UP, right_arm=[0, 30 * side, 0], torso=[0, 10 * side, 0]), INOUT)
    a.k(t0 + 9, add(CONDUCT_DOWN, right_arm=[0, 30 * side, 0], torso=[0, 10 * side, 0]), SNAP)
    a.k(t0 + 16, CONDUCT_UP, INOUT)
a.k(100, CONDUCT_DOWN, OUT_)
a.k(112, STAND, INOUT)
a.write()

a = Anim('crimson_execution', 64, STAND)
for i, t in enumerate(range(2, 20, 3)):                    # the blood circles the hand as it is spun up
    ang = i * 1.2
    a.k(t, P(CLASP, right_arm=[-80 + 8 * math.sin(ang), 40 + 8 * math.cos(ang), 0], torso=[6, 6 * math.sin(ang), 0]), INOUT)
a.k(21, CROUCH, IN)
a.k(24, P(punch_r(0.6), right_arm=[40, 0, 20], left_arm=[30, 0, -20], torso=[30, 0, 0], pos=[0, -1.5, -1.5]), SNAP)   # the dash
a.k(27, TWO_HAND, SNAP)
a.k(28, add(TWO_HAND, right_arm=[-22, 0, 0], left_arm=[-20, 0, 0], torso=[-10, 0, 0], pos=[0, 0, 1.4]), SNAP)  # the shot
a.k(31, add(TWO_HAND, right_arm=[-6, 0, 0], left_arm=[-6, 0, 0]), BACK)
a.hold(33, 46, TWO_HAND, 0.8, 4)
a.k(64, STAND, INOUT)
a.write()

a = Anim('blood_apocalypse', 172, STAND)
a.k(4, CLASP, OUT_)
LIFT = P(right_arm=[-176, -8, 6], left_arm=[-10, 0, -16], torso=[-8, 10, 0], head=[-34, -6, 0], right_leg=[-4, 0, 6], left_leg=[6, 0, -6])
for i, t in enumerate(range(8, 40, 4)):                    # one hand rises, slowly, the blood gathering in the sky above it
    u = (i + 1) / 8
    a.k(t, mix(CLASP, LIFT, u), INOUT)
a.tremble(42, 100, LIFT, 1.2, 4)
a.k(102, add(LIFT, right_arm=[8, 0, 0], torso=[-10, 0, 0], pos=[0, 0.4, 0]), IN)       # the hand closes on it
SLAM = P(right_arm=[-40, 10, 10], left_arm=[-40, -10, -10], torso=[40, 0, 0], head=[10, 0, 0], right_leg=[-30, 0, 6], left_leg=[20, 0, -6], pos=[0, -2.6, -0.6])
a.strike(106, SLAM, STAND, over=1.12, hold=20)
a.k(140, STANCE, OUT_).hold(142, 160, STANCE, 1.0, 8)
a.k(172, STAND, INOUT)
a.write()

# ================================================================== Shrine
a = Anim('dismantle', 30, STAND)
a.k(1, slash(1.05), OUT_)
a.k(3, slash(-1), SNAP)                                      # one flick, wrist snapped through
a.k(5, push(STAND, slash(-1), 1.12), OUT_)
a.k(8, slash(-0.9), INOUT).hold(10, 16, slash(-0.9), 0.8, 3)
a.k(30, STAND, INOUT)
a.write()

a = Anim('thousand_slashes', 92, STAND)
a.k(4, STANCE, OUT_)
heights = [0, 30, -20, 15, -30, 25, 5, -10, 35, -25, 10, 20, -15]
for i, t in enumerate(range(8, 60, 4)):                     # alternating hands, a different angle every cut
    h = heights[i % len(heights)]
    if i % 2 == 0:
        a.k(t, slash(1, h), OUT_).k(t + 2, slash(-1, h), SNAP)
    else:
        a.k(t, slash_l(1, h), OUT_).k(t + 2, slash_l(-1, h), SNAP)
FINISH = P(right_arm=[-120, -60, -20], left_arm=[-120, 60, 20], torso=[20, 0, 0], head=[10, 0, 0], right_leg=[-24, 0, 6], left_leg=[24, 0, -6], pos=[0, -1.6, 0])
a.k(62, push(STAND, FINISH, 1.1), SNAP).k(66, FINISH, OUT_).hold(68, 78, FINISH, 0.8, 4)
a.k(92, STAND, INOUT)
a.write()

a = Anim('cleave', 30, STAND)
a.k(2, P(punch_r(0.5), right_arm=[40, 0, 30], torso=[26, 0, 0], pos=[0, -1.4, -1.2]), SNAP)   # the dash in
CHOP0 = P(right_arm=[-96, 80, 0], left_arm=[-40, -10, -10], torso=[6, 44, 0], head=[0, -30, 0], right_leg=[12, 0, 6], left_leg=[-14, 0, -6], pos=[0, -1.2, 0])
CHOP1 = P(right_arm=[-86, -76, 0], left_arm=[24, 0, -24], torso=[10, -46, 0], head=[0, 30, 0], right_leg=[-16, 0, 6], left_leg=[18, 0, -6], pos=[0, -1.4, 0])
a.k(5, CHOP0, OUT_)
a.strike(6, CHOP1, STAND, over=1.15, hold=6)
a.k(30, STAND, INOUT)
a.write()

a = Anim('world_splitter', 102, STAND)
RAISED = P(right_arm=[-178, 10, 4], left_arm=[-30, -10, -20], torso=[-10, 10, 0], head=[-20, -6, 0], right_leg=[-6, 0, 6], left_leg=[10, 0, -6])
a.k(5, RAISED, OUT_).tremble(7, 20, RAISED, 2.0)
a.k(21, add(RAISED, torso=[-14, 0, 0], right_arm=[-6, 0, 0]), IN)
DOWNCUT = P(right_arm=[-20, -4, 0], left_arm=[30, 0, -30], torso=[44, 0, 0], head=[20, 0, 0], right_leg=[-36, 0, 6], left_leg=[20, 0, -6], pos=[0, -3.0, -0.8])
a.strike(22, DOWNCUT, STAND, over=1.1, hold=24)
a.k(60, STANCE, OUT_).hold(62, 86, STANCE, 1.0, 8)
a.k(102, STAND, INOUT)
a.write()

# ================================================================== power moves
a = Anim('meteor', 94, STAND)
a.k(5, CROUCH, OUT_)
a.k(7, push(STAND, CROUCH, 1.15), INOUT)
LAUNCH = P(right_arm=[-176, 0, 10], left_arm=[-176, 0, -10], torso=[-6, 0, 0], head=[-30, 0, 0], right_leg=[10, 0, 2], left_leg=[10, 0, -2], pos=[0, 1.0, 0])
a.k(9, LAUNCH, SNAP).k(18, add(LAUNCH, head=[-10, 0, 0]), OUT_)
TUCK = P(right_arm=[-40, 0, 20], left_arm=[-40, 0, -20], torso=[50, 0, 0], head=[-10, 0, 0], right_leg=[-110, 0, 6], left_leg=[-110, 0, -6], pos=[0, 2.0, 0])
a.k(26, TUCK, INOUT).k(31, add(TUCK, torso=[30, 0, 0]), INOUT)    # flipping over at the top
DIVE = P(right_arm=[-178, 0, 4], left_arm=[-178, 0, -4], torso=[78, 0, 0], head=[-56, 0, 0], right_leg=[16, 0, 4], left_leg=[22, 0, -4])
a.k(36, DIVE, SNAP).hold(38, 44, DIVE, 1.5, 2)
LAND = P(right_arm=[-36, 0, 4], left_arm=[30, 0, -50], torso=[56, 0, 0], head=[-32, 0, 0], right_leg=[-80, 0, 8], left_leg=[-6, 0, -8], pos=[0, -6.5, 0])
a.strike(46, LAND, STAND, over=1.06, hold=20)            # the superhero landing, fist in the crater
a.k(76, add(CROUCH, pos=[0, -2.0, 0]), INOUT)
a.k(94, STAND, INOUT)
a.write()

# ================================================================== the domain
a = Anim('domain', 122, STAND)
a.k(10, STANCE, OUT_).hold(12, 28, STANCE, 1.0, 8)
a.k(31, add(SIGN, right_arm=[20, 0, 0], left_arm=[20, 0, 0]), INOUT)
a.k(34, SIGN, SNAP).k(36, add(SIGN, head=[6, 0, 0]), OUT_)
a.hold(38, 58, SIGN, 0.6, 5)
a.k(62, add(SIGN, head=[-24, 0, 0], torso=[-6, 0, 0]), SNAP)      # eyes up as the domain opens
a.hold(64, 100, add(SIGN, head=[-24, 0, 0], torso=[-6, 0, 0]), 0.6, 6)
a.k(110, STANCE, INOUT)
a.k(122, STAND, INOUT)
a.write()

a = Anim('domain_finisher', 92, STAND)
a.k(4, wind_r(1.0), OUT_).tremble(6, 22, wind_r(1.15), 2.5)
a.k(25, P(punch_r(0.5), right_arm=[40, 0, 26], torso=[30, 20, 0], pos=[0, -1.8, -1.4]), SNAP)
a.k(34, wind_r(1.2), INOUT)
a.strike(38, punch_r(1.3), STAND, wind=None, over=1.16, hold=16)
a.k(70, STANCE, OUT_)
a.k(92, STAND, INOUT)
a.write()

a = Anim('domain_barrage', 102, STAND)
a.k(3, STANCE, OUT_)
for i, t in enumerate(range(6, 72, 3)):
    if i % 2 == 0:                                         # Dismantle: open-hand cuts, alternating
        h = [20, -10, 30, 0, -20][i // 2 % 5]
        a.k(t, slash(-1, h) if (i // 2) % 2 else slash_l(-1, h), SNAP)
    else:                                                  # Cleave: short hooks
        a.k(t, punch_r(0.7) if (i // 2) % 2 else punch_l(0.7), SNAP)
a.k(72, P(right_arm=[-160, 50, 0], left_arm=[-160, -50, 0], torso=[-10, 0, 0], head=[-14, 0, 0], right_leg=[0, 0, 5], left_leg=[0, 0, -5]), IN)   # wrists crossed overhead
a.strike(74, P(SLAM := P(right_arm=[-40, 10, 10], left_arm=[-40, -10, -10], torso=[40, 0, 0], head=[10, 0, 0], right_leg=[-30, 0, 6],
                         left_leg=[20, 0, -6], pos=[0, -2.6, -0.6])), STAND, over=1.12, hold=12)
a.k(102, STAND, INOUT)
a.write()

a = Anim('overdrive', 62, STAND)
a.k(6, CROUCH, OUT_)
CLENCH = P(CROUCH, right_arm=[10, 0, 34], left_arm=[10, 0, -34], torso=[34, 0, 0], head=[-6, 0, 0])
a.k(10, CLENCH, INOUT).tremble(12, 40, CLENCH, 3.5, 2)
a.k(41, push(STAND, CLENCH, 1.15), IN)
PALM = P(right_arm=[-84, 34, 0], left_arm=[-80, -30, 0], torso=[-4, 4, 0], head=[-6, 0, 0], right_leg=[-4, 0, 8], left_leg=[4, 0, -8])
a.strike(42, PALM, STAND, over=1.1, hold=0)                 # stands up straight, fist driven into his palm
a.k(48, add(PALM, head=[0, 0, 18]), SNAP).k(52, add(PALM, head=[0, 0, -16]), SNAP)   # neck cracked, one side, then the other
a.k(56, add(STANCE, head=[-8, 0, 0]), OUT_)
a.k(62, STANCE, INOUT)
a.write()

# ================================================================== ultimates
a = Anim('final_judgment', 234, STAND)
a.k(14, STANCE, OUT_).hold(16, 34, STANCE, 1.0, 6)
FIST_UP = P(right_arm=[-176, 0, 6], left_arm=[-50, -20, -10], torso=[-10, 8, 0], head=[-24, -4, 0], right_leg=[-6, 0, 7], left_leg=[10, 0, -7])
a.k(38, FIST_UP, OUT_).tremble(40, 70, FIST_UP, 1.5, 3)         # the fist raised to the darkening sky
a.k(76, mix(FIST_UP, wind_r(1.3), 0.5), INOUT)
a.k(82, wind_r(1.35), OUT_).tremble(84, 110, wind_r(1.4), 3.5, 2)   # dragged down to the hip, shaking with it
a.k(112, push(STAND, wind_r(1.4), 1.12), IN)
a.k(116, P(punch_r(0.4), right_arm=[30, 10, 26], torso=[24, 30, 0], pos=[0, -2.0, -1.6]), SNAP)
a.strike(120, punch_r(1.4), STAND, over=1.2, hold=34)        # and held, while the world ends
a.k(166, push(STAND, punch_r(1.0), 0.7), INOUT)
a.k(186, STANCE, OUT_).hold(188, 220, STANCE, 1.2, 8)
a.k(234, STAND, INOUT)
a.write()

a = Anim('domain_apocalypse', 286, STAND)
a.k(10, STANCE, OUT_)
a.k(25, add(SIGN, right_arm=[20, 0, 0], left_arm=[20, 0, 0]), INOUT).k(28, SIGN, SNAP).hold(30, 40, SIGN, 0.6, 5)
a.k(42, add(SIGN, head=[-24, 0, 0]), SNAP)
a.k(56, CONDUCT_UP, OUT_)
for w in range(3):                                         # the storm, called down
    t0 = 60 + w * 16
    side = 1 if w % 2 == 0 else -1
    a.k(t0 + 7, add(CONDUCT_DOWN, right_arm=[0, 30 * side, 0]), SNAP).k(t0 + 14, CONDUCT_UP, INOUT)
for i, t in enumerate(range(108, 146, 3)):                  # Dismantle across the domain
    a.k(t, slash(1 if i % 2 == 0 else -1, (i % 4) * 12 - 18), SNAP)
a.k(147, P(punch_r(0.5), right_arm=[40, 0, 26], torso=[26, 0, 0], pos=[0, -1.4, -1.2]), SNAP)
a.k(151, CHOP0, OUT_)
a.strike(152, CHOP1, STAND, over=1.12, hold=0)
a.k(158, wind_r(1.2), OUT_).tremble(160, 174, wind_r(1.3), 3.0, 2)
a.strike(176, punch_r(1.35), STAND, over=1.18, hold=10)
AWAY = P(right_arm=[-6, 0, 10], left_arm=[2, 0, -6], torso=[4, 70, 0], head=[14, 30, 0], right_leg=[0, 30, 4], left_leg=[0, 30, -4], pos=[0, 0, 0])
a.k(190, push(STAND, punch_r(1.0), 1.1), SNAP)
a.k(198, P(punch_r(0.6), right_arm=[-30, 0, 10]), OUT_)
a.k(208, AWAY, INOUT).hold(210, 236, AWAY, 0.8, 8)           # turns his back on it
a.k(250, STANCE, INOUT)
a.k(286, STAND, INOUT)
a.write()

# subtitles
names = ["charge", "black_flash", "black_flash_big", "dash", "blood_form", "blood_fire", "blood_hit", "blood_rain", "dismantle", "cleave",
         "world_splitter", "crater", "eruption", "domain", "domain_hum", "overdrive", "wave", "world_ender", "rumble", "impact", "meteor", "card"]
pretty = {"black_flash": "Black Flash", "black_flash_big": "Black Flash erupts", "world_ender": "The world ends", "card": "Technique"}
lang = {"subtitles.yuji." + n: pretty.get(n, n.replace("_", " ").capitalize()) for n in names}
os.makedirs(os.path.join(ROOT, "lang"), exist_ok=True)
with open(os.path.join(ROOT, "lang", "en_us.json"), "w") as f:
    json.dump(lang, f, indent=1)
