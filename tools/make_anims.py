"""The caller's body as the march is called: an arm raised to the sky, then down on one knee, a palm slammed flat
against the earth, held while the ground answers. Player Animator bedrock format; ticks are 1/20 s."""
import json, os, sys
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
Z = [0, 0, 0]
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
POSE = {
    'rest': {},
    'reach': {'right_arm': [-176, 0, -8], 'left_arm': [8, 0, -10], 'torso': [-8, 0, 0], 'head': [-34, 0, 0]},
    'reach_hold': {'right_arm': [-172, 0, -12], 'left_arm': [10, 0, -14], 'torso': [-10, 0, 0], 'head': [-38, 0, 0], 'pos': [0, 0.5, 0]},
    'wind': {'right_arm': [-190, 0, -4], 'left_arm': [-20, 0, -30], 'torso': [-16, 0, 0], 'head': [-20, 0, 0], 'right_leg': [-10, 0, 0],
             'left_leg': [6, 0, 0], 'pos': [0, 1, 0]},
    'slam': {'right_arm': [-50, 0, -6], 'left_arm': [-30, 0, -40], 'torso': [40, 0, 0], 'head': [8, 0, 0], 'right_leg': [-95, 0, 6],
             'left_leg': [-5, 0, -4], 'pos': [0, -9, -2]},
    'kneel': {'right_arm': [-46, 0, -6], 'left_arm': [-24, 0, -36], 'torso': [34, 0, 0], 'head': [-20, 0, 0], 'right_leg': [-92, 0, 6],
              'left_leg': [-4, 0, -4], 'pos': [0, -8.5, -2]},
    'kneel_look': {'right_arm': [-44, 0, -6], 'left_arm': [-22, 0, -34], 'torso': [26, 0, 0], 'head': [-42, 0, 0], 'right_leg': [-90, 0, 6],
                   'left_leg': [-4, 0, -4], 'pos': [0, -8.5, -2]},
}


def pose(n):
    return {b: list(POSE[n].get(b, Z)) for b in BONES}


def anim(name, length, keys):
    bones = {}
    for b in BONES:
        fr, pos = {}, {}
        for tick, p, ease in keys:
            k = {'vector': pose(p)[b]}
            if ease:
                k['easing'] = ease
            fr[f'{tick / 20.0:.3f}'] = k
            if b == 'torso':
                v = {'vector': list(POSE[p].get('pos', Z))}
                if ease:
                    v['easing'] = ease
                pos[f'{tick / 20.0:.3f}'] = v
        bones[b] = {'rotation': fr}
        if b == 'torso':
            bones[b]['position'] = pos
    data = {'format_version': '1.8.0', 'animations': {name: {'animation_length': length / 20.0, 'bones': bones}}}
    json.dump(data, open(os.path.join(OUT, name + '.json'), 'w'), indent=1)


anim('call', 130, [(0, 'rest', None), (12, 'reach', 'easeOutBack'), (30, 'reach_hold', 'easeInOutSine'), (36, 'wind', 'easeInQuad'),
                   (40, 'slam', 'easeOutExpo'), (52, 'kneel', 'easeOutCubic'), (80, 'kneel_look', 'easeInOutSine'), (112, 'kneel_look', None),
                   (130, 'rest', 'easeInOutSine')])
print('anims ok')
