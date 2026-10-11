"""Generates assets/silverinstinct/player_animation/*.json (Player Animator, bedrock format): the fighter's body during
every technique, from beat-timed key poses. Ticks are 1/20 s and line up with common/Beats.java.

Every move is animated the way a fight scene is drawn: a wind-up against the direction of the blow (anticipation), a
snap into the strike with a little overshoot, a held contact frame, follow-through past it, and a settle back into the
fighting stance. The whole body takes part: hips drop on effort (torso position), the head leads, the off arm counters.
"""
import json, os, sys
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
Z = [0, 0, 0]
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
# bone -> [x, y, z] degrees (x<0 raises a limb forward, z>0 lifts the right arm out sideways)
# 'pos' -> torso offset in pixels [x, y, z] (y<0 drops the body: knees bend)
POSE = {
    'rest': {},
    'stance': {'right_arm': [-48, -24, 10], 'left_arm': [-66, 26, -8], 'torso': [8, 18, 0], 'head': [6, -16, 0],
               'right_leg': [16, 0, 6], 'left_leg': [-20, 0, -6], 'pos': [0, -1, 0]},
    'stance_b': {'right_arm': [-44, -22, 12], 'left_arm': [-62, 24, -10], 'torso': [6, 20, 0], 'head': [4, -18, 0],
                 'right_leg': [16, 0, 6], 'left_leg': [-20, 0, -6], 'pos': [0, -1.5, 0]},
    # strikes
    'jab_r_wind': {'right_arm': [-34, 6, 14], 'left_arm': [-70, 20, -6], 'torso': [6, 28, 0], 'head': [6, -24, 0],
                   'right_leg': [18, 0, 4], 'left_leg': [-22, 0, -4], 'pos': [0, -1.5, 0]},
    'jab_r': {'right_arm': [-96, 10, 0], 'left_arm': [-52, -16, 0], 'torso': [12, -28, 0], 'head': [4, -6, 0],
              'right_leg': [-26, 0, 0], 'left_leg': [22, 0, 0], 'pos': [0, -1, -1]},
    'jab_l_wind': {'left_arm': [-34, -6, -14], 'right_arm': [-70, -20, 6], 'torso': [6, -28, 0], 'head': [6, 24, 0],
                   'right_leg': [-22, 0, 4], 'left_leg': [18, 0, -4], 'pos': [0, -1.5, 0]},
    'jab_l': {'left_arm': [-96, -10, 0], 'right_arm': [-52, 16, 0], 'torso': [12, 28, 0], 'head': [4, 6, 0],
              'right_leg': [22, 0, 0], 'left_leg': [-26, 0, 0], 'pos': [0, -1, -1]},
    'kick_chamber': {'right_leg': [-88, 0, 26], 'left_leg': [6, 0, -4], 'right_arm': [-40, 0, 34], 'left_arm': [-64, 0, -20],
                     'torso': [-6, -24, 0], 'head': [0, 18, 0], 'pos': [0, -0.5, 0]},
    'roundhouse': {'right_leg': [-98, 0, 74], 'left_leg': [8, 0, -6], 'right_arm': [14, 0, 44], 'left_arm': [-54, 0, -32],
                   'torso': [-12, -54, 10], 'head': [0, 36, 0]},
    'roundhouse_follow': {'right_leg': [-60, 0, 40], 'left_leg': [6, 0, -4], 'right_arm': [20, 0, 50], 'left_arm': [-30, 0, -40],
                          'torso': [-6, -74, 6], 'head': [0, 50, 0]},
    'crescent_wind': {'right_leg': [-40, 0, -30], 'left_leg': [10, 0, 0], 'right_arm': [-30, 0, 40], 'left_arm': [-70, 0, -20],
                      'torso': [8, 30, 0], 'head': [4, -30, 0], 'pos': [0, -2, 0]},
    'crescent': {'right_leg': [-155, -20, 34], 'right_arm': [24, 0, 54], 'left_arm': [-30, 0, -54], 'torso': [-20, -34, 0],
                 'head': [12, 20, 0], 'left_leg': [12, 0, 0], 'pos': [0, 0.5, 0]},
    'crescent_follow': {'right_leg': [-100, -40, 70], 'right_arm': [30, 0, 60], 'left_arm': [-20, 0, -60], 'torso': [-10, -60, 6],
                        'head': [10, 40, 0], 'left_leg': [10, 0, 0]},
    'land': {'right_arm': [-26, 0, 52], 'left_arm': [-26, 0, -52], 'torso': [32, 0, 0], 'head': [-26, 0, 0],
             'right_leg': [-72, 0, 22], 'left_leg': [-32, 0, -20], 'pos': [0, -4, 0]},
    'palm': {'right_arm': [-92, 4, 0], 'left_arm': [-92, -4, 0], 'torso': [14, 0, 0], 'head': [2, 0, 0],
             'right_leg': [-24, 0, 0], 'left_leg': [20, 0, 0], 'pos': [0, -1.5, -1]},
    'crouch': {'right_arm': [-34, 0, 34], 'left_arm': [-34, 0, -34], 'torso': [30, 0, 0], 'head': [-24, 0, 0],
               'right_leg': [-50, 0, 0], 'left_leg': [-50, 0, 0], 'pos': [0, -4, 0]},
    'dash': {'right_arm': [46, 0, 24], 'left_arm': [46, 0, -24], 'torso': [40, 0, 0], 'head': [-34, 0, 0],
             'right_leg': [-44, 0, 0], 'left_leg': [34, 0, 0], 'pos': [0, -2, -2]},
    # waves: cupped hands drawn back at the right hip
    'cup': {'right_arm': [22, 0, 28], 'left_arm': [12, 42, 0], 'torso': [14, 42, 0], 'head': [6, -32, 0],
            'right_leg': [-32, 0, 0], 'left_leg': [28, 0, 0], 'pos': [0, -2.5, 0]},
    'cup_deep': {'right_arm': [30, 0, 30], 'left_arm': [20, 46, 0], 'torso': [18, 52, 0], 'head': [8, -40, 0],
                 'right_leg': [-38, 0, 0], 'left_leg': [32, 0, 0], 'pos': [0, -3.5, 0.5]},
    'cup_shake': {'right_arm': [27, 0, 33], 'left_arm': [16, 43, 3], 'torso': [16, 49, -2], 'head': [6, -37, 2],
                  'right_leg': [-37, 0, 0], 'left_leg': [31, 0, 0], 'pos': [0, -3.2, 0.5]},
    'fire': {'right_arm': [-94, -10, 0], 'left_arm': [-94, 10, 0], 'torso': [10, -6, 0], 'head': [6, 0, 0],
             'right_leg': [-38, 0, 0], 'left_leg': [34, 0, 0], 'pos': [0, -2, -1.5]},
    'fire_push': {'right_arm': [-90, -6, 0], 'left_arm': [-90, 6, 0], 'torso': [16, -4, 0], 'head': [10, 0, 0],
                  'right_leg': [-44, 0, 0], 'left_leg': [40, 0, 0], 'pos': [0, -3, -2]},
    # air and hammer
    'uppercut': {'right_arm': [-172, -10, 0], 'left_arm': [-20, 0, -34], 'torso': [-16, -24, 0], 'head': [-22, 0, 0],
                 'right_leg': [-32, 0, 0], 'left_leg': [12, 0, 0], 'pos': [0, 1, 0]},
    'launch_kick': {'right_leg': [-168, 0, 0], 'left_leg': [14, 0, 0], 'right_arm': [34, 0, 44], 'left_arm': [34, 0, -44],
                    'torso': [-24, 0, 0], 'head': [-14, 0, 0], 'pos': [0, 1, 0]},
    'air_r': {'right_arm': [-102, 22, 0], 'left_arm': [-20, 0, -44], 'torso': [6, -24, 0], 'right_leg': [-64, 0, 0],
              'left_leg': [-22, 0, 0]},
    'air_l': {'left_arm': [-102, -22, 0], 'right_arm': [-20, 0, 44], 'torso': [6, 24, 0], 'right_leg': [-22, 0, 0],
              'left_leg': [-64, 0, 0]},
    'hammer_up': {'right_arm': [-178, 0, -12], 'left_arm': [-178, 0, 12], 'torso': [-24, 0, 0], 'head': [-12, 0, 0],
                  'right_leg': [-44, 0, 0], 'left_leg': [-32, 0, 0]},
    'hammer': {'right_arm': [-46, 0, -8], 'left_arm': [-46, 0, 8], 'torso': [38, 0, 0], 'head': [12, 0, 0],
               'right_leg': [-64, 0, 0], 'left_leg': [-42, 0, 0], 'pos': [0, -1, -1]},
    'slash_r': {'right_arm': [-82, -74, 0], 'left_arm': [-20, 0, -44], 'torso': [12, -44, 0], 'head': [4, 32, 0],
                'right_leg': [-32, 0, 0], 'left_leg': [28, 0, 0], 'pos': [0, -2, 0]},
    'slash_l': {'left_arm': [-82, 74, 0], 'right_arm': [-20, 0, 44], 'torso': [12, 44, 0], 'head': [4, -32, 0],
                'right_leg': [28, 0, 0], 'left_leg': [-32, 0, 0], 'pos': [0, -2, 0]},
    # the sphere
    'open': {'right_arm': [-70, 0, 74], 'left_arm': [-70, 0, -74], 'torso': [-12, 0, 0], 'head': [-30, 0, 0],
             'right_leg': [-8, 0, 6], 'left_leg': [8, 0, -6]},
    'raise': {'right_arm': [-178, 0, -16], 'left_arm': [-178, 0, 16], 'torso': [-10, 0, 0], 'head': [-32, 0, 0],
              'right_leg': [-6, 0, 6], 'left_leg': [6, 0, -6]},
    'raise_b': {'right_arm': [-174, 0, -22], 'left_arm': [-174, 0, 22], 'torso': [-12, 0, 0], 'head': [-36, 0, 0],
                'right_leg': [-6, 0, 6], 'left_leg': [6, 0, -6], 'pos': [0, -0.5, 0]},
    'raise_back': {'right_arm': [-200, 0, -12], 'left_arm': [-200, 0, 12], 'torso': [-22, 0, 0], 'head': [-30, 0, 0],
                   'right_leg': [-30, 0, 0], 'left_leg': [22, 0, 0], 'pos': [0, -1, 1]},
    'throw': {'right_arm': [-62, -10, -10], 'left_arm': [-62, 10, 10], 'torso': [28, 0, 0], 'head': [12, 0, 0],
              'right_leg': [-38, 0, 0], 'left_leg': [30, 0, 0], 'pos': [0, -2.5, -1.5]},
    # transformation
    'tense': {'right_arm': [4, 0, 16], 'left_arm': [4, 0, -16], 'torso': [6, 0, 0], 'head': [8, 0, 0],
              'right_leg': [-4, 0, 4], 'left_leg': [-4, 0, -4]},
    'clench': {'right_arm': [12, 0, 24], 'left_arm': [12, 0, -24], 'torso': [12, 0, 0], 'head': [20, 0, 0],
               'right_leg': [-10, 0, 8], 'left_leg': [-10, 0, -8], 'pos': [0, -1.5, 0]},
    'clench_deep': {'right_arm': [18, 0, 30], 'left_arm': [18, 0, -30], 'torso': [22, 0, 0], 'head': [30, 0, 0],
                    'right_leg': [-30, 0, 12], 'left_leg': [-30, 0, -12], 'pos': [0, -3.5, 0]},
    'burst': {'right_arm': [-24, 0, 76], 'left_arm': [-24, 0, -76], 'torso': [-14, 0, 0], 'head': [-22, 0, 0],
              'right_leg': [-12, 0, 10], 'left_leg': [12, 0, -10], 'pos': [0, 0.5, 0]},
    'burst_hold': {'right_arm': [-16, 0, 64], 'left_arm': [-16, 0, -64], 'torso': [-8, 0, 0], 'head': [-12, 0, 0],
                   'right_leg': [-10, 0, 8], 'left_leg': [10, 0, -8]},
    'calm': {'right_arm': [-8, 0, 12], 'left_arm': [-8, 0, -12], 'head': [4, 0, 0]},
    'calm_b': {'right_arm': [-6, 0, 14], 'left_arm': [-6, 0, -14], 'head': [10, 0, 0], 'torso': [2, 0, 0]},
    'slump': {'right_arm': [10, 0, 6], 'left_arm': [10, 0, -6], 'torso': [20, 0, 0], 'head': [26, 0, 0],
              'right_leg': [-16, 0, 0], 'left_leg': [-4, 0, 0], 'pos': [0, -1.5, 0]},
}


def pose(name):
    return {b: list(POSE[name].get(b, Z)) for b in BONES}


def anim(name, length, keys, loop=False):
    bones = {}
    for b in BONES:
        frames = {}
        pos = {}
        for tick, p, ease in keys:
            k = {'vector': pose(p)[b]}
            if ease:
                k['easing'] = ease
            frames[f'{tick / 20.0:.3f}'] = k
            if b == 'torso':
                v = {'vector': list(POSE[p].get('pos', Z))}
                if ease:
                    v['easing'] = ease
                pos[f'{tick / 20.0:.3f}'] = v
        bones[b] = {'rotation': frames}
        if b == 'torso':
            bones[b]['position'] = pos
    data = {'format_version': '1.8.0', 'animations': {name: {'animation_length': length / 20.0, 'loop': loop, 'bones': bones}}}
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(data, f, indent=1)


C, IN, OUT_, X, BACK, S = None, 'easeInQuad', 'easeOutCubic', 'easeOutExpo', 'easeOutBack', 'easeInOutSine'

# the idle fighting stance while awakened: a slow breath, the weight shifting
anim('stance', 40, [(0, 'stance', C), (20, 'stance_b', S), (40, 'stance', S)], loop=True)

# strikes: wind, snap, hold the contact, recover
anim('strike_0', 9, [(0, 'stance', C), (1, 'jab_r_wind', IN), (3, 'jab_r', X), (5, 'jab_r', C), (9, 'stance', S)])
anim('strike_1', 9, [(0, 'stance', C), (1, 'jab_l_wind', IN), (3, 'jab_l', X), (5, 'jab_l', C), (9, 'stance', S)])
anim('strike_2', 11, [(0, 'stance', C), (2, 'kick_chamber', OUT_), (4, 'roundhouse', BACK), (6, 'roundhouse_follow', OUT_),
                      (11, 'stance', S)])
anim('heavy', 22, [(0, 'stance', C), (3, 'kick_chamber', OUT_), (6, 'crescent_wind', IN), (9, 'crescent', X), (11, 'crescent', C),
                   (13, 'crescent_follow', OUT_), (16, 'land', OUT_), (22, 'stance', S)])

volley = [(0, 'rest', C), (3, 'stance', OUT_)]
for i, t in enumerate(range(5, 33, 2)):
    volley.append((t - 1, 'jab_r_wind' if i % 2 == 0 else 'jab_l_wind', IN))
    volley.append((t, 'jab_r' if i % 2 == 0 else 'jab_l', X))
volley += [(34, 'palm', BACK), (38, 'palm', C), (44, 'stance', S)]
anim('volley', 44, volley)

anim('vanish', 12, [(0, 'stance', C), (2, 'crouch', OUT_), (4, 'dash', X), (7, 'dash', C), (12, 'stance', S)])

anim('wave', 84, [(0, 'rest', C), (5, 'stance', OUT_), (9, 'cup', OUT_), (20, 'cup_deep', S), (24, 'cup_shake', C), (27, 'cup_deep', C),
                  (30, 'cup_shake', C), (32, 'cup_deep', IN), (34, 'fire', X), (38, 'fire_push', OUT_), (62, 'fire_push', C),
                  (66, 'fire', S), (76, 'stance', S), (84, 'stance', C)])

flurry = [(0, 'stance', C), (6, 'crouch', OUT_), (10, 'dash', X)]
seq = ['jab_r', 'jab_l', 'slash_r', 'jab_l', 'jab_r', 'slash_l']
for i, t in enumerate(range(13, 41, 3)):
    flurry.append((t - 1, 'jab_r_wind' if seq[i % 6].endswith('_r') else 'jab_l_wind', IN))
    flurry.append((t, seq[i % 6], X))
flurry += [(42, 'crouch', OUT_), (44, 'uppercut', BACK), (50, 'uppercut', C), (60, 'stance', S)]
anim('flurry', 60, flurry)

anim('meteor', 74, [(0, 'stance', C), (5, 'crouch', OUT_), (8, 'launch_kick', X), (12, 'air_l', OUT_),
                    (14, 'air_l', C), (15, 'air_r', X), (17, 'air_r', C), (18, 'air_l', X), (20, 'air_l', C), (21, 'air_r', X),
                    (23, 'air_r', C), (24, 'air_l', X), (26, 'air_l', C), (27, 'air_r', X), (31, 'hammer_up', OUT_), (34, 'hammer', X),
                    (40, 'hammer', C), (42, 'land', X), (54, 'land', C), (66, 'stance', S), (74, 'stance', C)])

rush = [(0, 'stance', C), (8, 'crouch', OUT_)]
for i, t in enumerate(range(12, 40, 3)):
    rush.append((t, 'slash_r' if i % 2 == 0 else 'slash_l', X))
    rush.append((t + 2, 'dash', OUT_))
rush += [(44, 'hammer_up', OUT_), (48, 'hammer', X), (56, 'hammer', C), (66, 'stance', S)]
anim('rush', 66, rush)

anim('sphere', 150, [(0, 'rest', C), (6, 'open', OUT_), (14, 'raise', S), (34, 'raise_b', S), (54, 'raise', S), (74, 'raise_b', S),
                     (86, 'raise_back', IN), (92, 'throw', X), (100, 'throw', C), (120, 'stance', S), (150, 'stance', C)])

anim('final', 140, [(0, 'rest', C), (6, 'stance', OUT_), (14, 'cup', OUT_), (30, 'cup_deep', S), (44, 'cup_shake', C),
                    (48, 'cup_deep', C), (52, 'cup_shake', C), (56, 'cup_deep', C), (60, 'cup_shake', C), (64, 'cup_deep', C),
                    (68, 'cup_deep', IN), (70, 'fire', X), (74, 'fire_push', OUT_), (108, 'fire_push', C), (114, 'fire', S),
                    (128, 'stance', S), (140, 'stance', C)])

anim('awaken_omen', 110, [(0, 'rest', C), (8, 'tense', OUT_), (20, 'clench', S), (32, 'tense', S), (42, 'clench', S),
                          (52, 'clench_deep', OUT_), (60, 'clench_deep', C), (62, 'crouch', IN), (64, 'burst', BACK),
                          (72, 'burst_hold', OUT_), (90, 'burst_hold', C), (104, 'stance', S), (110, 'stance', C)])

anim('awaken_zenith', 150, [(0, 'stance', C), (12, 'calm', S), (34, 'calm_b', S), (56, 'calm', S), (76, 'calm_b', S),
                            (86, 'clench', OUT_), (92, 'clench_deep', S), (95, 'clench_deep', C), (96, 'burst', BACK),
                            (104, 'burst_hold', OUT_), (122, 'calm', S), (140, 'stance', S), (150, 'stance', C)])

anim('revert', 18, [(0, 'stance', C), (5, 'slump', OUT_), (12, 'slump', C), (18, 'rest', S)])
print('ok', len(os.listdir(OUT)))
