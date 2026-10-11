"""Generates assets/silverinstinct/player_animation/*.json (Player Animator, bedrock format): the fighter's body during
every technique, from beat-timed key poses. Ticks are 1/20 s and line up with common/Beats.java."""
import json, os, sys
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
Z = [0, 0, 0]
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
# bone -> [x, y, z] degrees (x<0 raises a limb forward, z>0 lifts the right arm out sideways)
POSE = {
    'rest': {},
    'guard': {'right_arm': [-60, -20, 0], 'left_arm': [-70, 20, 0], 'torso': [6, 10, 0], 'head': [2, -10, 0],
              'right_leg': [10, 0, 0], 'left_leg': [-14, 0, 0]},
    'jab_r': {'right_arm': [-92, 8, 0], 'left_arm': [-55, -14, 0], 'torso': [10, -22, 0], 'head': [4, -10, 0],
              'right_leg': [-22, 0, 0], 'left_leg': [18, 0, 0]},
    'jab_l': {'left_arm': [-92, -8, 0], 'right_arm': [-55, 14, 0], 'torso': [10, 22, 0], 'head': [4, 10, 0],
              'right_leg': [18, 0, 0], 'left_leg': [-22, 0, 0]},
    'kick_chamber': {'right_leg': [-80, 0, 20], 'right_arm': [-40, 0, 30], 'left_arm': [-60, 0, -20], 'torso': [-6, -20, 0]},
    'roundhouse': {'right_leg': [-95, 0, 70], 'right_arm': [10, 0, 40], 'left_arm': [-50, 0, -30], 'torso': [-10, -50, 8],
                   'head': [0, 30, 0]},
    'crescent': {'right_leg': [-150, -20, 30], 'right_arm': [20, 0, 50], 'left_arm': [-30, 0, -50], 'torso': [-18, -30, 0],
                 'head': [10, 20, 0], 'left_leg': [10, 0, 0]},
    'palm': {'right_arm': [-90, 0, 0], 'left_arm': [-90, 0, 0], 'torso': [10, 0, 0], 'head': [2, 0, 0],
             'right_leg': [-18, 0, 0], 'left_leg': [16, 0, 0]},
    'crouch': {'right_arm': [-30, 0, 30], 'left_arm': [-30, 0, -30], 'torso': [28, 0, 0], 'head': [-22, 0, 0],
               'right_leg': [-48, 0, 0], 'left_leg': [-48, 0, 0]},
    'dash': {'right_arm': [40, 0, 20], 'left_arm': [40, 0, -20], 'torso': [36, 0, 0], 'head': [-30, 0, 0],
             'right_leg': [-40, 0, 0], 'left_leg': [30, 0, 0]},
    # cupped hands drawn back at the right hip: the gather before a wave
    'cup': {'right_arm': [20, 0, 26], 'left_arm': [10, 40, 0], 'torso': [14, 40, 0], 'head': [6, -30, 0],
            'right_leg': [-30, 0, 0], 'left_leg': [26, 0, 0]},
    'fire': {'right_arm': [-92, -10, 0], 'left_arm': [-92, 10, 0], 'torso': [8, -6, 0], 'head': [6, 0, 0],
             'right_leg': [-34, 0, 0], 'left_leg': [30, 0, 0]},
    'uppercut': {'right_arm': [-170, -10, 0], 'left_arm': [-20, 0, -30], 'torso': [-14, -20, 0], 'head': [-20, 0, 0],
                 'right_leg': [-30, 0, 0], 'left_leg': [10, 0, 0]},
    'launch_kick': {'right_leg': [-165, 0, 0], 'left_leg': [12, 0, 0], 'right_arm': [30, 0, 40], 'left_arm': [30, 0, -40],
                    'torso': [-22, 0, 0], 'head': [-12, 0, 0]},
    'air_r': {'right_arm': [-100, 20, 0], 'left_arm': [-20, 0, -40], 'torso': [6, -20, 0], 'right_leg': [-60, 0, 0],
              'left_leg': [-20, 0, 0]},
    'air_l': {'left_arm': [-100, -20, 0], 'right_arm': [-20, 0, 40], 'torso': [6, 20, 0], 'right_leg': [-20, 0, 0],
              'left_leg': [-60, 0, 0]},
    'hammer_up': {'right_arm': [-175, 0, -10], 'left_arm': [-175, 0, 10], 'torso': [-20, 0, 0], 'head': [-10, 0, 0],
                  'right_leg': [-40, 0, 0], 'left_leg': [-30, 0, 0]},
    'hammer': {'right_arm': [-50, 0, -8], 'left_arm': [-50, 0, 8], 'torso': [34, 0, 0], 'head': [10, 0, 0],
               'right_leg': [-60, 0, 0], 'left_leg': [-40, 0, 0]},
    'land': {'right_arm': [-20, 0, 50], 'left_arm': [-20, 0, -50], 'torso': [34, 0, 0], 'head': [-24, 0, 0],
             'right_leg': [-70, 0, 20], 'left_leg': [-30, 0, -20]},
    'slash_r': {'right_arm': [-80, -70, 0], 'left_arm': [-20, 0, -40], 'torso': [10, -40, 0], 'head': [4, 30, 0],
                'right_leg': [-30, 0, 0], 'left_leg': [26, 0, 0]},
    'slash_l': {'left_arm': [-80, 70, 0], 'right_arm': [-20, 0, 40], 'torso': [10, 40, 0], 'head': [4, -30, 0],
                'right_leg': [26, 0, 0], 'left_leg': [-30, 0, 0]},
    'raise': {'right_arm': [-178, 0, -14], 'left_arm': [-178, 0, 14], 'torso': [-8, 0, 0], 'head': [-30, 0, 0]},
    'throw': {'right_arm': [-60, -10, -10], 'left_arm': [-60, 10, 10], 'torso': [24, 0, 0], 'head': [10, 0, 0],
              'right_leg': [-34, 0, 0], 'left_leg': [28, 0, 0]},
    # clenching: fists low, arms tense out from the body, shoulders forward
    'clench': {'right_arm': [10, 0, 22], 'left_arm': [10, 0, -22], 'torso': [10, 0, 0], 'head': [16, 0, 0],
               'right_leg': [-8, 0, 6], 'left_leg': [-8, 0, -6]},
    'burst': {'right_arm': [-20, 0, 70], 'left_arm': [-20, 0, -70], 'torso': [-12, 0, 0], 'head': [-18, 0, 0],
              'right_leg': [-10, 0, 8], 'left_leg': [10, 0, -8]},
    # the calm of the summit: arms loose, chin level
    'calm': {'right_arm': [-8, 0, 10], 'left_arm': [-8, 0, -10], 'head': [4, 0, 0]},
    'slump': {'right_arm': [10, 0, 6], 'left_arm': [10, 0, -6], 'torso': [18, 0, 0], 'head': [24, 0, 0],
              'right_leg': [-14, 0, 0], 'left_leg': [-4, 0, 0]},
}

def pose(name):
    return {b: list(POSE[name].get(b, Z)) for b in BONES}

def anim(name, length, keys):
    bones = {}
    for b in BONES:
        frames = {}
        for tick, p, ease in keys:
            k = {'vector': pose(p)[b]}
            if ease:
                k['easing'] = ease
            frames[f'{tick / 20.0:.2f}'] = k
        bones[b] = {'rotation': frames}
    data = {'format_version': '1.8.0', 'animations': {name: {'animation_length': length / 20.0, 'bones': bones}}}
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(data, f, indent=1)

E, X, S, C = 'easeOutCubic', 'easeOutExpo', 'easeInOutSine', None
anim('strike_0', 9, [(0, 'guard', C), (3, 'jab_r', X), (5, 'jab_r', C), (9, 'guard', S)])
anim('strike_1', 9, [(0, 'guard', C), (3, 'jab_l', X), (5, 'jab_l', C), (9, 'guard', S)])
anim('strike_2', 10, [(0, 'guard', C), (2, 'kick_chamber', E), (4, 'roundhouse', X), (6, 'roundhouse', C), (10, 'rest', S)])
anim('heavy', 20, [(0, 'rest', C), (5, 'kick_chamber', E), (9, 'crescent', X), (13, 'crescent', C), (20, 'rest', S)])
volley = [(0, 'rest', C), (4, 'guard', E)]
for i, t in enumerate(range(5, 33, 2)):
    volley.append((t, 'jab_r' if i % 2 == 0 else 'jab_l', X))
volley += [(34, 'palm', E), (40, 'rest', S)]
anim('volley', 40, volley)
anim('vanish', 10, [(0, 'rest', C), (3, 'crouch', E), (5, 'dash', X), (10, 'rest', S)])
anim('wave', 84, [(0, 'rest', C), (8, 'cup', E), (32, 'cup', C), (34, 'fire', X), (66, 'fire', C), (84, 'rest', S)])
flurry = [(0, 'rest', C), (6, 'crouch', E), (10, 'dash', X)]
for i, t in enumerate(range(13, 41, 3)):
    flurry.append((t, ['jab_r', 'jab_l', 'slash_r', 'jab_l', 'jab_r', 'slash_l'][i % 6], X))
flurry += [(42, 'crouch', E), (44, 'uppercut', X), (52, 'uppercut', C), (60, 'rest', S)]
anim('flurry', 60, flurry)
anim('meteor', 74, [(0, 'rest', C), (5, 'crouch', E), (8, 'launch_kick', X), (13, 'air_r', E), (15, 'air_r', X), (18, 'air_l', X),
                    (21, 'air_r', X), (24, 'air_l', X), (27, 'air_r', X), (31, 'hammer_up', E), (34, 'hammer', X), (40, 'hammer', C),
                    (42, 'land', X), (56, 'land', C), (74, 'rest', S)])
rush = [(0, 'rest', C), (8, 'crouch', E)]
for i, t in enumerate(range(12, 40, 3)):
    rush.append((t, 'slash_r' if i % 2 == 0 else 'slash_l', X))
rush += [(44, 'hammer_up', E), (48, 'hammer', X), (56, 'hammer', C), (66, 'rest', S)]
anim('rush', 66, rush)
anim('sphere', 150, [(0, 'rest', C), (10, 'raise', E), (88, 'raise', C), (92, 'throw', X), (110, 'throw', C), (150, 'rest', S)])
anim('final', 140, [(0, 'rest', C), (12, 'cup', E), (66, 'cup', C), (70, 'fire', X), (112, 'fire', C), (140, 'rest', S)])
anim('awaken_omen', 110, [(0, 'rest', C), (10, 'clench', E), (40, 'clench', C), (52, 'crouch', E), (62, 'crouch', C), (64, 'burst', X),
                          (86, 'burst', C), (110, 'rest', S)])
anim('awaken_zenith', 150, [(0, 'rest', C), (16, 'calm', E), (70, 'calm', C), (86, 'clench', E), (94, 'clench', C), (96, 'burst', X),
                            (120, 'burst', C), (150, 'calm', S)])
anim('revert', 16, [(0, 'rest', C), (5, 'slump', E), (10, 'slump', C), (16, 'rest', S)])
print('ok', len(os.listdir(OUT)))
