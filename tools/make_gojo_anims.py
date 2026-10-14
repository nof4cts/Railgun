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
sway = P(thrust, right_arm=[-88, 8, 6], torso=[12, -2, 0])
anim('g_max_blue', 176, [(0, STAND, None), (8, hold_up, SETTLE), (30, hold_up_b, BREATH), (36, thrust, SNAP), (70, sway, BREATH), (100, thrust, BREATH),
                         (130, sway, BREATH), (158, thrust, BREATH), (160, clench, SNAP), (176, STAND, GLIDE)])

# ---- Maximum Output: Red - arm out, the left hand bracing the wrist; the recoil
brace = P(right_arm=[-90, 2, 0], left_arm=[-82, 34, 0], torso=[6, -16, 0], head=[0, 14, 0], right_leg=[-14, 0, 4], left_leg=[16, 0, -4], pos=[0, -2, 0])
brace_b = P(brace, right_arm=[-92, 2, 0], left_arm=[-84, 34, 0])
kick = P(right_arm=[-122, -6, 6], left_arm=[-60, 20, -10], torso=[-12, 6, 0], head=[-14, 0, 0], right_leg=[22, 0, 4], left_leg=[-18, 0, -4], pos=[0, -1, 0])
anim('g_max_red', 96, [(0, STAND, None), (8, brace, SETTLE), (38, brace_b, BREATH), (40, kick, SNAP), (52, P(kick, right_arm=[-110, -6, 6]), SETTLE),
                       (72, kick, None), (96, STAND, GLIDE)])

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
# he rises first (hanging in the air, legs loose), then the choreography plays out up there
hang = P(right_arm=[-8, 0, 16], left_arm=[-8, 0, -16], right_leg=[-6, 0, 3], left_leg=[14, 0, -3], head=[6, 0, 0])
air = lambda p: P(p, right_leg=[-6 + p['right_leg'][0] * 0.3, 0, 3], left_leg=[14 + p['left_leg'][0] * 0.3, 0, -3], pos=[0, 0, 0])
AIM_MAX = 100


def purple_anim(name, risen, b, pre=None):
    """b = GBeats.purpleBeats: named Red, Red, named Blue, Blue, join, Hollow, merged, ready. Red blooms at his left,
    Blue at his right: each arm goes out to its side as the colour is named; both drawn in; then the right arm out at
    the target, held while he aims (the fire is its own animation)."""
    v_red, red, v_blue, blue, join, v_hollow, merge, ready = b
    keys = [(0, STAND, None), (risen, hang, GLIDE)] + (pre or [])
    left_out = air(P(squat, left_arm=[-24, 0, -80], right_arm=[-14, 0, 18], head=[-2, -14, 0]))
    left_b = P(left_out, left_arm=[-30, 0, -84])
    right_out = air(P(left_b, right_arm=[-24, 0, 80], head=[-2, 14, 0]))
    right_b = P(right_out, right_arm=[-30, 0, 84], left_arm=[-28, 0, -82])
    keys += [(v_red + 2, left_out, SETTLE), (red, left_b, SNAP), (v_blue - 2, P(left_b, head=[-2, 0, 0]), BREATH), (v_blue + 4, right_out, SETTLE),
             (blue, right_b, SNAP), (join, right_b, None), (merge - 2, air(pull), IN), (merge + 2, air(aim), SNAP), (ready, air(aim_b), BREATH)]
    for k in range(ready + 20, ready + AIM_MAX + 1, 20):
        keys.append((k, air(aim if (k // 20) % 2 else aim_b), BREATH))
    length = ready + AIM_MAX + 10
    keys.sort(key=lambda k: k[0])
    anim(name, length, keys)


def fire_anim(name, pose, follow_pose, length):
    anim(name, length, [(0, pose, None), (2, follow_pose, SNAP), (12, follow_pose, SETTLE), (length - 16, follow_pose, None), (length, STAND, GLIDE)])


purple_anim('g_purple', 20, [22, 58, 70, 110, 122, 130, 140, 150])
fire_anim('g_purple_fire', air(aim_b), air(drive), 60)

floatp = P(right_arm=[-10, 0, 14], left_arm=[-10, 0, -14], right_leg=[-6, 0, 3], left_leg=[14, 0, -3], head=[4, 0, 0])
pray = P(floatp, right_arm=[-72, -42, 0], left_arm=[-72, 42, 0], head=[10, 0, 0], torso=[4, 0, 0])
chant = [(16 + 8, pray, SETTLE)] + [(c + 8 + 2, P(pray, head=[6 + 4 * (i % 2), (-1) ** i * 6, 0], right_arm=[-74 - i * 2, -42, 0],
                                                 left_arm=[-74 - i * 2, 42, 0]), BREATH) for i, c in enumerate([14, 36, 58, 80])]
purple_anim('g_purple_200', 26, [104, 140, 150, 186, 198, 206, 218, 230], chant)
fire_anim('g_purple_200_fire', air(aim_b), air(P(drive, torso=[20, -6, 0])), 70)

# ---- Imaginary Technique: Purple (Toji): he rises slowly into the light, arms opening wide to the sky; at "Throughout
# Heaven and Earth" a hand over his face as he laughs; calm again while he explains; then the right arm out, the
# Purple in front of it, held while he aims
ascend = P(floatp, right_arm=[-24, 0, 70], left_arm=[-24, 0, -70], head=[-22, 0, 0], torso=[-6, 0, 0], right_leg=[-2, 0, 2], left_leg=[8, 0, -2])
ascend_b = P(ascend, right_arm=[-30, 0, 78], left_arm=[-30, 0, -78], head=[-28, 0, 0])
laugh = P(floatp, right_arm=[-150, -48, -6], left_arm=[-16, 0, -46], head=[-30, 6, 0], torso=[-12, 4, 0])
laugh_b = P(laugh, head=[-36, -4, 0], torso=[-15, -3, 0], right_arm=[-152, -50, -6])
calm = P(floatp, right_arm=[-6, 0, 22], left_arm=[-6, 0, -22], head=[6, 0, 0])
calm_b = P(calm, head=[2, 8, 0], right_arm=[-10, 0, 26])
toji_aim = P(floatp, right_arm=[-90, 4, 0], left_arm=[-14, 20, -26], torso=[0, -14, 0], head=[0, 12, 0])
toji_aim_b = P(toji_aim, right_arm=[-92, 4, 0])
keys = [(0, STAND, None), (20, floatp, GLIDE), (60, ascend, GLIDE)]
for k in range(100, 231, 40):
    keys.append((k, ascend_b if (k // 40) % 2 else ascend, BREATH))
keys += [(238, P(ascend, head=[-10, 0, 0]), None), (243, laugh, SNAP)]
for k in range(250, 286, 6):
    keys.append((k, laugh_b if (k // 6) % 2 else laugh, BREATH))
keys += [(296, calm, GLIDE), (360, calm_b, BREATH), (420, calm, BREATH), (480, calm_b, BREATH), (540, calm, BREATH),
         (670, calm, None), (690, toji_aim, SNAP)]
for k in range(710, 728 + AIM_MAX + 1, 20):
    keys.append((k, toji_aim_b if (k // 20) % 2 else toji_aim, BREATH))
anim('g_imaginary', 728 + AIM_MAX + 10, keys)
fire_anim('g_imaginary_fire', toji_aim_b, P(floatp, right_arm=[-98, 8, 0], left_arm=[10, 0, -32], torso=[12, -4, 0], head=[-6, 2, 0]), 70)

# ---- Hollow Nuke: risen, he shoots Blue out of his right hand (it circles him), fires Red up out of his left, holds
# both hands up as they meet and while he aims at the land; then hurls the Purple down with both arms
fl = P(right_arm=[-14, 0, 22], left_arm=[-14, 0, -22], right_leg=[-8, 0, 4], left_leg=[16, 0, -4], head=[-4, 0, 0])
shoot_b = P(fl, right_arm=[-92, -10, 30], torso=[0, -16, 0], head=[0, 14, 0])
follow = P(fl, right_arm=[-70, 30, 50], head=[0, -20, 0])
shoot_r = P(fl, left_arm=[-168, 10, -16], right_arm=[-30, 0, 30], head=[-30, 0, 0], torso=[-8, 10, 0])
up2 = P(fl, right_arm=[-172, 0, 12], left_arm=[-172, 0, -12], head=[-34, 0, 0], torso=[-10, 0, 0])
up2b = P(up2, right_arm=[-178, 0, 8], left_arm=[-178, 0, -8])
hurl = P(fl, right_arm=[-46, 0, 8], left_arm=[-46, 0, -8], torso=[22, 0, 0], head=[14, 0, 0])
wide = P(fl, right_arm=[-30, 0, 100], left_arm=[-30, 0, -100], head=[10, 0, 0], torso=[-6, 0, 0])
keys = [(0, STAND, None), (26, fl, GLIDE), (40, P(fl, right_arm=[-60, 20, 10]), SETTLE), (62, P(fl, right_arm=[-64, 22, 12]), BREATH), (64, shoot_b, SNAP),
        (74, follow, GLIDE), (90, P(fl, left_arm=[-90, 0, -10]), SETTLE), (112, P(fl, left_arm=[-96, 0, -12]), BREATH), (114, shoot_r, SNAP),
        (128, shoot_r, None), (140, up2, GLIDE), (172, up2b, BREATH)]
for k in range(192, 172 + AIM_MAX + 1, 20):
    keys.append((k, up2 if (k // 20) % 2 else up2b, BREATH))
anim('g_nuke', 172 + AIM_MAX + 10, keys)
anim('g_nuke_fire', 130, [(0, up2b, None), (3, P(up2b, torso=[-14, 0, 0]), IN), (6, hurl, SNAP), (20, hurl, None), (28, wide, SETTLE), (80, wide, None),
                          (130, STAND, GLIDE)])

# ---- Unlimited Void: the hand sign at his face, index and middle crossed
sign = P(right_arm=[-126, -34, -4], left_arm=[4, 0, -8], head=[8, 6, 0], torso=[2, 0, 0])
sign_b = P(sign, right_arm=[-128, -36, -4], head=[6, 6, 0])
anim('g_domain', 156, [(0, STAND, None), (5, sign, SNAP), (40, sign_b, BREATH), (80, sign, BREATH), (118, sign_b, BREATH), (140, sign, None), (156, STAND, GLIDE)])
anim('g_domain_02', 44, [(0, STAND, None), (3, sign, SNAP), (14, sign_b, None), (30, sign, None), (44, STAND, GLIDE)])
