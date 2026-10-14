"""Generates assets/gojo/player_animation/g_*.json (Player Animator, bedrock format): Gojo's body for each reworked
technique, timed to com/gojo/rework/common/GBeats.java (ticks of 1/20 s).

Arms: x < 0 raises forward/up, z > 0 lifts the right arm outward (z < 0 the left), y turns it. Torso x > 0 bows;
head x < 0 looks up. 'pos' moves the body in pixels.
Usage: make_gojo_anims.py <assets/gojo/player_animation>"""
import json
import os
import sys

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
Z = [0, 0, 0]
SNAP, GLIDE, SETTLE, IN, BREATH = 'easeOutExpo', 'easeInOutSine', 'easeOutCubic', 'easeInCubic', 'easeInOutQuad'


def P(base=None, **bones):
    out = {b: list((base or {}).get(b, Z)) for b in BONES + ['pos']}
    for b, v in bones.items():
        out[b] = list(v)
    return out


def anim(name, length, keys):
    bones = {}
    for b in BONES:
        rot, pos = {}, {}
        for tick, pose, ease in keys:
            k = {'vector': pose[b]}
            if ease:
                k['easing'] = ease
            rot[f'{tick / 20.0:.3f}'] = k
            if b == 'torso':
                v = {'vector': pose['pos']}
                if ease:
                    v['easing'] = ease
                pos[f'{tick / 20.0:.3f}'] = v
        bones[b] = {'rotation': rot}
        if b == 'torso':
            bones[b]['position'] = pos
    data = {'format_version': '1.8.0', 'animations': {name: {'animation_length': length / 20.0, 'loop': False, 'bones': bones}}}
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(data, f, indent=1)
    print(name, length)


N = P()
# hands in pockets is his rest; the techniques start from a loose stand
STAND = P(right_arm=[4, 0, 6], left_arm=[4, 0, -6], head=[2, 0, 0])

# ---- Reversal: Red - the point at his fingertip, then the flick
point = P(right_arm=[-88, 4, 2], left_arm=[8, 0, -10], torso=[2, -12, 0], head=[0, 10, 0], right_leg=[-6, 0, 3], left_leg=[8, 0, -3])
point_b = P(point, right_arm=[-90, 6, 2])
flick = P(right_arm=[-104, -8, 4], left_arm=[18, 0, -16], torso=[-4, 14, 0], head=[-4, -8, 0], right_leg=[10, 0, 4], left_leg=[-12, 0, -4])
anim('g_red', 60, [(0, STAND, None), (4, point, SNAP), (12, point_b, BREATH), (14, flick, SNAP), (20, P(flick, right_arm=[-98, -6, 4]), SETTLE),
                   (40, flick, None), (58, STAND, GLIDE)])

# ---- Maximum Output: Blue - the orb raised over the hand, then thrust out and held as it ploughs on
hold_up = P(right_arm=[-160, -10, 18], left_arm=[-30, 20, -14], torso=[-6, -8, 0], head=[-22, 6, 0], right_leg=[-4, 0, 4], left_leg=[6, 0, -4])
hold_up_b = P(hold_up, right_arm=[-164, -10, 20], head=[-24, 6, 0])
thrust = P(right_arm=[-92, 0, 4], left_arm=[-40, 30, -6], torso=[14, -10, 0], head=[-8, 8, 0], right_leg=[18, 0, 4], left_leg=[-26, 0, -4], pos=[0, -2, 0])
clench = P(thrust, right_arm=[-70, 10, 0], torso=[6, 10, 0], head=[0, -6, 0])
anim('g_max_blue', 112, [(0, STAND, None), (8, hold_up, SETTLE), (22, hold_up_b, BREATH), (26, thrust, SNAP), (60, P(thrust, right_arm=[-95, 0, 4]), BREATH),
                         (96, thrust, BREATH), (98, clench, SNAP), (112, STAND, GLIDE)])

# ---- Maximum Output: Red - arm out, the left hand bracing the wrist; the recoil
brace = P(right_arm=[-90, 2, 0], left_arm=[-82, 34, 0], torso=[6, -16, 0], head=[0, 14, 0], right_leg=[-14, 0, 4], left_leg=[16, 0, -4], pos=[0, -2, 0])
brace_b = P(brace, right_arm=[-92, 2, 0], left_arm=[-84, 34, 0])
kick = P(right_arm=[-122, -6, 6], left_arm=[-60, 20, -10], torso=[-12, 6, 0], head=[-14, 0, 0], right_leg=[22, 0, 4], left_leg=[-18, 0, -4], pos=[0, -1, 0])
anim('g_max_red', 84, [(0, STAND, None), (8, brace, SETTLE), (26, brace_b, BREATH), (28, kick, SNAP), (40, P(kick, right_arm=[-110, -6, 6]), SETTLE),
                       (60, kick, None), (84, STAND, GLIDE)])

# ---- Hollow Purple (Hanami) - Blue in the left, Red in the right, pressed together, flicked
# choreographed as the stick-figure animation does it: he squares up and spreads both hands out to his sides (Red
# blooms at the left, Blue at the right), draws them in, thrusts his right hand out at the target and holds it there
# while the Purple grows in front of it, then drives it forward with a lean.
squat = P(STAND, right_leg=[-14, 0, 8], left_leg=[14, 0, -8], torso=[6, 0, 0], pos=[0, -2, 0])
spread = P(squat, left_arm=[-24, 0, -78], right_arm=[-24, 0, 78], head=[-4, 0, 0])
spread_b = P(spread, left_arm=[-28, 0, -82], right_arm=[-28, 0, 82])
pull = P(squat, right_arm=[-70, -30, 10], left_arm=[-70, 30, -10], torso=[10, 0, 0], head=[4, 0, 0])
aim = P(squat, right_arm=[-90, 4, 0], left_arm=[-30, 30, -12], torso=[2, -14, 0], head=[0, 12, 0], right_leg=[-18, 0, 6], left_leg=[18, 0, -6])
aim_b = P(aim, right_arm=[-92, 4, 0])
drive = P(right_arm=[-96, 8, 0], left_arm=[20, 0, -22], torso=[16, -6, 0], head=[-8, 4, 0], right_leg=[22, 0, 4], left_leg=[-28, 0, -4], pos=[0, -3, 0])
anim('g_purple', 72, [(0, STAND, None), (3, spread, SETTLE), (14, spread_b, BREATH), (18, pull, IN), (21, aim, SNAP), (29, aim_b, BREATH),
                      (31, drive, SNAP), (38, P(drive, torso=[12, -6, 0]), SETTLE), (54, drive, None), (72, STAND, GLIDE)])


# ---- the Purples spoken in full (Toji's, and the 200%)
def chanted(name, length, shift, red, blue, merge, fire):
    floatp = P(right_arm=[-10, 0, 14], left_arm=[-10, 0, -14], right_leg=[-6, 0, 3], left_leg=[14, 0, -3], head=[4, 0, 0])
    pray = P(floatp, right_arm=[-72, -42, 0], left_arm=[-72, 42, 0], head=[10, 0, 0], torso=[4, 0, 0])
    keys = [(0, STAND, None), (10, floatp, GLIDE), (16 + shift, pray, SETTLE)]
    for i, c in enumerate([14, 36, 58, 80]):
        keys.append((c + shift + 2, P(pray, head=[6 + 4 * (i % 2), (-1) ** i * 6, 0], right_arm=[-74 - i * 2, -42, 0], left_arm=[-74 - i * 2, 42, 0]), BREATH))
    # Red forms at his left, Blue at his right: each hand opens toward its colour
    open_r = P(floatp, left_arm=[-36, 0, -72], right_arm=[-60, -30, 0], head=[0, 16, 0])
    open_b = P(open_r, right_arm=[-36, 0, 72], head=[0, -10, 0])
    press = P(floatp, right_arm=[-90, -30, 0], left_arm=[-90, 30, 0], torso=[8, 0, 0], head=[4, 0, 0])
    # the aim: his right arm straight out at the target, the left pulled back, held while the ring forms at his fingertip
    back = P(floatp, right_arm=[-90, 2, 0], left_arm=[-20, 20, -30], torso=[0, -16, 0], head=[0, 14, 0])
    shot = P(floatp, right_arm=[-96, 6, 0], left_arm=[10, 0, -30], torso=[10, -4, 0], head=[-6, 2, 0])
    keys += [(red, open_r, SETTLE), (blue, open_b, SETTLE), (merge, press, SNAP), (fire - 14, back, SNAP), (fire, shot, SNAP),
             (fire + 10, P(shot, right_arm=[-96, 8, 0]), SETTLE), (length - 24, shot, None), (length, STAND, GLIDE)]
    keys.sort(key=lambda k: k[0])
    anim(name, length, keys)


chanted('g_imaginary', 200, 0, 96, 102, 114, 134)
chanted('g_purple_200', 236, 8, 106, 112, 126, 150)

# ---- Hollow Nuke
fl = P(right_arm=[-14, 0, 22], left_arm=[-14, 0, -22], right_leg=[-8, 0, 4], left_leg=[16, 0, -4], head=[-8, 0, 0])
lup = P(fl, left_arm=[-150, 0, -38], head=[-20, -12, 0])
rup = P(lup, right_arm=[-150, 0, 38], head=[-24, 0, 0])
press = P(fl, right_arm=[-176, 0, 8], left_arm=[-176, 0, -8], head=[-26, 0, 0], torso=[-8, 0, 0])
wide = P(fl, right_arm=[-30, 0, 104], left_arm=[-30, 0, -104], head=[-6, 0, 0], torso=[-10, 0, 0], right_leg=[0, 0, 12], left_leg=[0, 0, -12])
anim('g_nuke', 250, [(0, STAND, None), (26, fl, GLIDE), (36, lup, SETTLE), (46, rup, SETTLE), (82, rup, None), (100, press, GLIDE), (120, P(press, right_arm=[-180, 0, 4], left_arm=[-180, 0, -4]), BREATH),
                     (134, wide, SNAP), (170, wide, None), (250, STAND, GLIDE)])

# ---- Unlimited Void: the hand sign at his face, index and middle crossed
sign = P(right_arm=[-126, -34, -4], left_arm=[4, 0, -8], head=[8, 6, 0], torso=[2, 0, 0])
sign_b = P(sign, right_arm=[-128, -36, -4], head=[6, 6, 0])
anim('g_domain', 156, [(0, STAND, None), (5, sign, SNAP), (40, sign_b, BREATH), (80, sign, BREATH), (118, sign_b, BREATH), (140, sign, None), (156, STAND, GLIDE)])
anim('g_domain_02', 44, [(0, STAND, None), (3, sign, SNAP), (14, sign_b, None), (30, sign, None), (44, STAND, GLIDE)])
