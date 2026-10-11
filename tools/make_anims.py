"""Naoya's body for each Projection Sorcery move (Player Animator, bedrock format, rotations only). Ticks 1/20 s."""
import json, os, sys
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
Z = [0, 0, 0]
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
POSE = {
    'rest': {},
    'ready': {'right_arm': [-20, 0, 12], 'left_arm': [-20, 0, -12], 'torso': [10, 0, 0], 'head': [-8, 0, 0], 'right_leg': [-14, 0, 0],
              'left_leg': [12, 0, 0]},
    'lean': {'right_arm': [55, 0, 18], 'left_arm': [55, 0, -18], 'torso': [42, 0, 0], 'head': [-36, 0, 0], 'right_leg': [-48, 0, 0],
             'left_leg': [36, 0, 0]},
    'palm': {'right_arm': [-92, -8, 0], 'left_arm': [20, 0, -20], 'torso': [14, -24, 0], 'head': [4, -10, 0], 'right_leg': [-28, 0, 0],
             'left_leg': [24, 0, 0]},
    'jab_r': {'right_arm': [-95, 8, 0], 'left_arm': [-50, -14, 0], 'torso': [12, -24, 0], 'head': [4, -8, 0], 'right_leg': [-22, 0, 0],
              'left_leg': [18, 0, 0]},
    'jab_l': {'left_arm': [-95, -8, 0], 'right_arm': [-50, 14, 0], 'torso': [12, 24, 0], 'head': [4, 8, 0], 'right_leg': [18, 0, 0],
              'left_leg': [-22, 0, 0]},
    'kick': {'right_leg': [-100, 0, 60], 'right_arm': [14, 0, 40], 'left_arm': [-50, 0, -30], 'torso': [-12, -50, 8], 'head': [0, 30, 0]},
    'turn': {'right_arm': [-30, 0, 30], 'left_arm': [-30, 0, -30], 'torso': [20, 40, 0], 'head': [0, -30, 0], 'right_leg': [-30, 0, 10],
             'left_leg': [-10, 0, -10]},
    'wide': {'right_arm': [-80, 0, 70], 'left_arm': [-80, 0, -70], 'torso': [-6, 0, 0], 'head': [-10, 0, 0]},
    'snap': {'right_arm': [-150, -20, -10], 'left_arm': [6, 0, -10], 'torso': [-4, -10, 0], 'head': [-6, 10, 0]},
    'crouch': {'right_arm': [-30, 0, 30], 'left_arm': [-30, 0, -30], 'torso': [30, 0, 0], 'head': [-24, 0, 0], 'right_leg': [-50, 0, 0],
               'left_leg': [-50, 0, 0]},
    'punch': {'right_arm': [-92, -4, 0], 'left_arm': [40, 0, -20], 'torso': [22, -30, 0], 'head': [8, -10, 0], 'right_leg': [-40, 0, 0],
              'left_leg': [34, 0, 0]},
}


def pose(n):
    return {b: list(POSE[n].get(b, Z)) for b in BONES}


def anim(name, length, keys):
    bones = {b: {'rotation': {}} for b in BONES}
    for tick, p, ease in keys:
        for b in BONES:
            k = {'vector': pose(p)[b]}
            if ease:
                k['easing'] = ease
            bones[b]['rotation'][f'{tick / 20.0:.3f}'] = k
    json.dump({'format_version': '1.8.0', 'animations': {name: {'animation_length': length / 20.0, 'bones': bones}}},
              open(os.path.join(OUT, name + '.json'), 'w'), indent=1)


X, O, S, C, B = 'easeOutExpo', 'easeOutCubic', 'easeInOutSine', None, 'easeOutBack'
anim('dash', 12, [(0, 'ready', C), (1, 'lean', X), (6, 'lean', C), (12, 'rest', S)])
anim('step', 12, [(0, 'rest', C), (2, 'turn', X), (7, 'ready', O), (12, 'rest', S)])
anim('palm', 16, [(0, 'ready', C), (2, 'lean', X), (4, 'palm', B), (10, 'palm', C), (16, 'rest', S)])
bar = [(0, 'ready', C)]
for i, t in enumerate(range(1, 24)):
    bar.append((t, ['jab_r', 'jab_l', 'jab_r', 'kick', 'jab_l', 'jab_r'][i % 6], X))
bar += [(24, 'punch', B), (30, 'punch', C), (36, 'rest', S)]
anim('barrage', 36, bar)
anim('boom', 18, [(0, 'crouch', C), (2, 'lean', X), (10, 'lean', C), (18, 'rest', S)])
anim('trap', 22, [(0, 'ready', C), (3, 'wide', B), (14, 'wide', C), (22, 'rest', S)])
anim('snap', 16, [(0, 'rest', C), (3, 'snap', B), (10, 'snap', C), (16, 'rest', S)])
anim('ult', 104, [(0, 'rest', C), (6, 'ready', O), (30, 'ready', C), (40, 'crouch', S), (45, 'crouch', C), (46, 'lean', X), (58, 'lean', C),
                  (60, 'punch', B), (72, 'punch', C), (90, 'ready', S), (104, 'rest', S)])
print('anims ok')
