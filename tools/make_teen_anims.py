"""Teen Gojo's poses (Player Animator, bedrock format): assets/teengojo/player_animation/tg_*.json.
Usage: make_teen_anims.py <assets/teengojo/player_animation>"""
import json, os, sys
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


STAND = P(right_arm=[4, 0, 6], left_arm=[4, 0, -6], head=[2, 0, 0])
# Red: the point at the fingertip, then the flick
point = P(right_arm=[-88, 4, 2], left_arm=[8, 0, -10], torso=[2, -12, 0], head=[0, 10, 0], right_leg=[-6, 0, 3], left_leg=[8, 0, -3])
flick = P(right_arm=[-104, -8, 4], left_arm=[18, 0, -16], torso=[-4, 14, 0], head=[-4, -8, 0], right_leg=[10, 0, 4], left_leg=[-12, 0, -4])
anim('tg_red', 26, [(0, STAND, None), (3, point, SNAP), (7, P(point, right_arm=[-90, 6, 2]), BREATH), (9, flick, SNAP),
                    (16, P(flick, right_arm=[-98, -6, 4]), SETTLE), (26, STAND, GLIDE)])
# Blue: the palm held out at the point, fingers spread
palm = P(right_arm=[-96, -14, 10], left_arm=[-20, 10, -14], torso=[4, -10, 0], head=[0, 8, 0], right_leg=[-8, 0, 3], left_leg=[10, 0, -3])
anim('tg_blue', 46, [(0, STAND, None), (4, palm, SNAP), (24, P(palm, right_arm=[-100, -14, 12]), BREATH), (40, palm, BREATH), (46, STAND, GLIDE)])
# Infinity: a loose, easy stance, hands open at his sides
guard = P(right_arm=[-28, 0, 26], left_arm=[-28, 0, -26], head=[-4, 0, 0], torso=[-2, 0, 0])
anim('tg_guard', 22, [(0, STAND, None), (5, guard, SETTLE), (14, P(guard, right_arm=[-24, 0, 22], left_arm=[-24, 0, -22]), BREATH), (22, STAND, GLIDE)])
# Hollow Purple: Red out to one side, Blue to the other, pressed together, thrust
spread = P(STAND, left_arm=[-24, 0, -80], right_arm=[-24, 0, 80], right_leg=[-12, 0, 6], left_leg=[12, 0, -6], torso=[4, 0, 0], pos=[0, -1, 0])
press = P(spread, right_arm=[-80, -30, 8], left_arm=[-80, 30, -8], torso=[8, 0, 0])
thrust = P(right_arm=[-96, 6, 0], left_arm=[10, 0, -24], torso=[12, -8, 0], head=[-6, 6, 0], right_leg=[20, 0, 4], left_leg=[-24, 0, -4], pos=[0, -2, 0])
anim('tg_purple', 40, [(0, STAND, None), (5, spread, SETTLE), (12, P(spread, left_arm=[-28, 0, -84], right_arm=[-28, 0, 84]), BREATH),
                       (19, press, IN), (24, thrust, SNAP), (32, thrust, None), (40, STAND, GLIDE)])
# 0.2-Second Domain: the hand sign at his face, index and middle crossed
sign = P(right_arm=[-126, -34, -4], left_arm=[4, 0, -8], head=[8, 6, 0], torso=[2, 0, 0])
anim('tg_sign', 26, [(0, STAND, None), (3, sign, SNAP), (16, P(sign, right_arm=[-128, -36, -4]), None), (26, STAND, GLIDE)])
