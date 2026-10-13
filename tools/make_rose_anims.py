"""Generates assets/requiem/player_animation/*.json (Player Animator, bedrock format): the reaper's body for every
Requiem Rose technique. Ticks (1/20 s) line up with common/RBeats.java.

Poses are written per bone in degrees: x < 0 swings a limb forward and up, y turns it, z > 0 lifts the right arm out
(z < 0 the left). 'pos' moves the torso in pixels (y < 0 sinks the body: knees bend). The scythe sits in the right
hand with its shaft across the fist, so the right arm's angle is the scythe's angle.

Every cut is drawn the way a key animator draws it: a deep wind-up against the cut, a snap through it on the beat
(eased out hard, so it arrives fast and lands), an overshoot that hangs a frame, then a slow settle.
Usage: make_rose_anims.py <assets/requiem/player_animation>"""
import json
import os
import sys

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
Z = [0, 0, 0]
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']

POSE = {
    # the reaper at rest: scythe low and forward across the body, weight back, chin down
    'stance': {'right_arm': [-38, 12, 14], 'left_arm': [-46, 36, -6], 'torso': [6, 22, 0], 'head': [10, -18, 0],
               'right_leg': [14, 0, 7], 'left_leg': [-18, 0, -6]},
    'stance_b': {'right_arm': [-34, 12, 16], 'left_arm': [-43, 34, -8], 'torso': [5, 24, 0], 'head': [12, -20, 0],
                 'right_leg': [14, 0, 7], 'left_leg': [-18, 0, -6]},
    'carry': {'right_arm': [-30, 6, 18], 'left_arm': [-20, 20, -6]},
    'carry_b': {'right_arm': [-26, 6, 20], 'left_arm': [-16, 18, -8]},
    'hover': {'right_arm': [-28, 8, 22], 'left_arm': [-8, 0, -24], 'torso': [4, 10, 0], 'head': [10, -8, 0],
              'right_leg': [-16, 0, 4], 'left_leg': [12, 0, -4]},
    'hover_b': {'right_arm': [-24, 8, 24], 'left_arm': [-6, 0, -28], 'torso': [6, 10, 0], 'head': [8, -8, 0],
                'right_leg': [-10, 0, 4], 'left_leg': [18, 0, -4]},
    'glide': {'right_arm': [30, 0, 28], 'left_arm': [34, 0, -22], 'torso': [26, 0, 0], 'head': [-30, 0, 0],
              'right_leg': [10, 0, 2], 'left_leg': [22, 0, -2]},
    'glide_b': {'right_arm': [34, 0, 30], 'left_arm': [38, 0, -24], 'torso': [26, 0, 0], 'head': [-30, 0, 0],
                'right_leg': [14, 0, 2], 'left_leg': [18, 0, -2]},

    # Reap 0: high right to low left
    'r0_wind': {'right_arm': [-165, -20, 38], 'left_arm': [-40, 30, -20], 'torso': [-8, -38, 0], 'head': [6, 28, 0],
                'right_leg': [20, 0, 8], 'left_leg': [-24, 0, -6], 'pos': [0, -1, 0]},
    'r0_cut': {'right_arm': [-34, 46, -26], 'left_arm': [-20, -30, -40], 'torso': [18, 42, 0], 'head': [10, -30, 0],
               'right_leg': [-30, 0, 4], 'left_leg': [26, 0, -4], 'pos': [0, -3, 0]},
    'r0_over': {'right_arm': [-26, 54, -30], 'left_arm': [-14, -30, -46], 'torso': [20, 48, 0], 'head': [12, -34, 0],
                'right_leg': [-32, 0, 4], 'left_leg': [28, 0, -4], 'pos': [0, -3, 0]},
    # Reap 1: low left rising to high right
    'r1_wind': {'right_arm': [-20, 40, -30], 'left_arm': [-50, -10, -20], 'torso': [14, 40, 0], 'head': [10, -30, 0],
                'right_leg': [-22, 0, 4], 'left_leg': [22, 0, -4], 'pos': [0, -2, 0]},
    'r1_cut': {'right_arm': [-160, -30, 50], 'left_arm': [-30, 20, -30], 'torso': [-10, -36, 0], 'head': [-4, 26, 0],
               'right_leg': [22, 0, 6], 'left_leg': [-26, 0, -6], 'pos': [0, 0, 0]},
    'r1_over': {'right_arm': [-172, -34, 56], 'left_arm': [-24, 22, -34], 'torso': [-12, -42, 0], 'head': [-6, 30, 0],
                'right_leg': [24, 0, 6], 'left_leg': [-28, 0, -6]},
    # Reap 2: the flat spin
    'r2_wind': {'right_arm': [-88, -70, 50], 'left_arm': [-60, -40, -10], 'torso': [6, -64, 0], 'head': [4, 40, 0],
                'right_leg': [18, 0, 10], 'left_leg': [-20, 0, -8], 'pos': [0, -3, 0]},
    'r2_cut': {'right_arm': [-92, 80, -30], 'left_arm': [-30, 20, -50], 'torso': [8, 70, 0], 'head': [4, -40, 0],
               'right_leg': [-20, 0, 10], 'left_leg': [20, 0, -8], 'pos': [0, -3, 0]},
    'r2_over': {'right_arm': [-80, 96, -36], 'left_arm': [-24, 24, -56], 'torso': [8, 82, 0], 'head': [4, -46, 0],
                'right_leg': [-22, 0, 10], 'left_leg': [22, 0, -8], 'pos': [0, -3, 0]},
    # Reap 3: the overhead cleave
    'r3_wind': {'right_arm': [-188, 0, 10], 'left_arm': [-180, 0, -10], 'torso': [-20, 0, 0], 'head': [-16, 0, 0],
                'right_leg': [-14, 0, 6], 'left_leg': [10, 0, -6], 'pos': [0, 1, 0]},
    'r3_cut': {'right_arm': [-40, 0, 6], 'left_arm': [-44, 0, -6], 'torso': [38, 0, 0], 'head': [-10, 0, 0],
               'right_leg': [-50, 0, 6], 'left_leg': [34, 0, -6], 'pos': [0, -5, 0]},
    'r3_over': {'right_arm': [-30, 0, 6], 'left_arm': [-34, 0, -6], 'torso': [44, 0, 0], 'head': [-14, 0, 0],
                'right_leg': [-56, 0, 6], 'left_leg': [38, 0, -6], 'pos': [0, -6, 0]},

    # Crescent Reaper: thrown from behind the shoulder
    'cr_wind': {'right_arm': [-120, -70, 70], 'left_arm': [-80, 30, -10], 'torso': [-4, -58, 0], 'head': [4, 50, 0],
                'right_leg': [16, 0, 10], 'left_leg': [-24, 0, -8], 'pos': [0, -1, 0]},
    'cr_throw': {'right_arm': [-96, 46, -10], 'left_arm': [-10, -20, -40], 'torso': [10, 46, 0], 'head': [4, -40, 0],
                 'right_leg': [-28, 0, 6], 'left_leg': [26, 0, -6], 'pos': [0, -2, 0]},
    'cr_wait': {'right_arm': [-84, 20, 6], 'left_arm': [-14, -10, -30], 'torso': [6, 24, 0], 'head': [4, -20, 0],
                'right_leg': [-14, 0, 6], 'left_leg': [14, 0, -6]},
    'cr_catch': {'right_arm': [-118, 6, 16], 'left_arm': [-20, -10, -30], 'torso': [-4, 6, 0], 'head': [-4, -4, 0],
                 'right_leg': [-10, 0, 6], 'left_leg': [10, 0, -6]},

    # Rose Tether: the left hand casts the lash, pulls, the right cuts upward
    'te_cast': {'left_arm': [-92, -4, 0], 'right_arm': [-26, 10, 20], 'torso': [12, -24, 0], 'head': [4, 22, 0],
                'right_leg': [24, 0, 4], 'left_leg': [-30, 0, -4], 'pos': [0, -2, 0]},
    'te_hold': {'left_arm': [-88, -8, 4], 'right_arm': [-24, 12, 22], 'torso': [10, -20, 0], 'head': [4, 18, 0],
                'right_leg': [24, 0, 4], 'left_leg': [-30, 0, -4], 'pos': [0, -2, 0]},
    'te_pull': {'left_arm': [-20, 10, -30], 'right_arm': [-10, 30, -20], 'torso': [-14, 20, 0], 'head': [-6, -10, 0],
                'right_leg': [-36, 0, 4], 'left_leg': [30, 0, -4], 'pos': [0, -2, 0]},
    'te_cut': {'left_arm': [-30, 20, -40], 'right_arm': [-176, -10, 30], 'torso': [-14, -26, 0], 'head': [-14, 14, 0],
               'right_leg': [20, 0, 6], 'left_leg': [-24, 0, -6], 'pos': [0, 1, 0]},

    # Dusk Step
    'st_low': {'right_arm': [10, 10, 30], 'left_arm': [10, -10, -30], 'torso': [34, 0, 0], 'head': [-24, 0, 0],
               'right_leg': [-60, 0, 8], 'left_leg': [-20, 0, -8], 'pos': [0, -5, 0]},

    # Calamity Ray: the scythe raised, then levelled at them like a lance
    'ra_raise': {'right_arm': [-172, 0, 14], 'left_arm': [-40, 0, -50], 'torso': [-6, 14, 0], 'head': [-12, -10, 0],
                 'right_leg': [16, 0, 8], 'left_leg': [-16, 0, -8]},
    'ra_level': {'right_arm': [-92, 8, 4], 'left_arm': [-86, -26, 0], 'torso': [14, 20, 0], 'head': [-2, -18, 0],
                 'right_leg': [-36, 0, 6], 'left_leg': [30, 0, -6], 'pos': [0, -3, 0]},
    'ra_brace': {'right_arm': [-96, 8, 2], 'left_arm': [-90, -24, 0], 'torso': [18, 20, 0], 'head': [0, -18, 0],
                 'right_leg': [-42, 0, 6], 'left_leg': [34, 0, -6], 'pos': [0, -4, 0]},
    'ra_recoil': {'right_arm': [-108, 6, 6], 'left_arm': [-100, -22, 2], 'torso': [-4, 18, 0], 'head': [-8, -16, 0],
                  'right_leg': [-44, 0, 6], 'left_leg': [36, 0, -6], 'pos': [0, -4, 1]},

    # Thousand Requiem
    'rq_low': {'right_arm': [-10, 30, 30], 'left_arm': [20, -20, -30], 'torso': [36, 0, 0], 'head': [-30, 0, 0],
               'right_leg': [-64, 0, 10], 'left_leg': [-10, 0, -10], 'pos': [0, -6, 0]},
    'rq_after': {'right_arm': [-14, 50, -20], 'left_arm': [-6, 0, -10], 'torso': [4, 40, 0], 'head': [26, -30, 0],
                 'right_leg': [-8, 0, 4], 'left_leg': [8, 0, -4]},

    # Severance Rift
    'ri_up': {'right_arm': [-186, 6, 8], 'left_arm': [-184, -6, -8], 'torso': [-18, 0, 0], 'head': [-26, 0, 0],
              'right_leg': [-10, 0, 8], 'left_leg': [8, 0, -8], 'pos': [0, 1, 0]},
    'ri_up_b': {'right_arm': [-192, 6, 8], 'left_arm': [-190, -6, -8], 'torso': [-22, 0, 0], 'head': [-28, 0, 0],
                'right_leg': [-10, 0, 8], 'left_leg': [8, 0, -8], 'pos': [0, 1, 0]},
    'ri_cut': {'right_arm': [-36, 0, 8], 'left_arm': [-40, 0, -8], 'torso': [40, 0, 0], 'head': [-14, 0, 0],
               'right_leg': [-56, 0, 8], 'left_leg': [36, 0, -8], 'pos': [0, -6, 0]},

    # Last Bloom
    'lb_rise': {'right_arm': [-20, 0, 64], 'left_arm': [-20, 0, -64], 'torso': [-10, 0, 0], 'head': [-30, 0, 0],
                'right_leg': [8, 0, 6], 'left_leg': [-4, 0, -6]},
    'lb_hold': {'right_arm': [-178, 0, 12], 'left_arm': [-20, 0, -40], 'torso': [-8, 10, 0], 'head': [-6, -6, 0],
                'right_leg': [-14, 0, 6], 'left_leg': [10, 0, -6]},
    'lb_draw': {'right_arm': [-204, -10, 20], 'left_arm': [-30, 20, -50], 'torso': [-24, -10, 0], 'head': [-10, 6, 0],
                'right_leg': [-20, 0, 8], 'left_leg': [14, 0, -8]},
    'lb_cut': {'right_arm': [-40, 10, 6], 'left_arm': [-10, -10, -40], 'torso': [42, 12, 0], 'head': [-20, -6, 0],
               'right_leg': [-44, 0, 6], 'left_leg': [30, 0, -6]},

    # Ascension
    'as_still': {'right_arm': [4, 0, 6], 'left_arm': [4, 0, -6], 'torso': [6, 0, 0], 'head': [30, 0, 0]},
    'as_rise': {'right_arm': [-10, 0, 40], 'left_arm': [-10, 0, -40], 'torso': [10, 0, 0], 'head': [34, 0, 0], 'pos': [0, -1, 0]},
    'as_in': {'right_arm': [-62, 46, -10], 'left_arm': [-62, -46, 10], 'torso': [26, 0, 0], 'head': [36, 0, 0],
              'right_leg': [-30, 0, 6], 'left_leg': [-30, 0, -6], 'pos': [0, -4, 0]},
    'as_out': {'right_arm': [-26, 0, 104], 'left_arm': [-26, 0, -104], 'torso': [-14, 0, 0], 'head': [-28, 0, 0],
               'right_leg': [6, 0, 14], 'left_leg': [-6, 0, -14], 'pos': [0, 0, 0]},
    'as_out_b': {'right_arm': [-30, 0, 96], 'left_arm': [-30, 0, -96], 'torso': [-12, 0, 0], 'head': [-24, 0, 0],
                 'right_leg': [6, 0, 14], 'left_leg': [-6, 0, -14]},

    'release': {'right_arm': [6, 0, 6], 'left_arm': [6, 0, -6], 'torso': [10, 0, 0], 'head': [26, 0, 0], 'pos': [0, -1, 0]},
}

C = 'easeInOutCubic'
S = 'easeInOutSine'
SNAP = 'easeOutExpo'
IN = 'easeInQuad'
OUTC = 'easeOutCubic'


def pose(name):
    return {b: list(POSE[name].get(b, Z)) for b in BONES}


def anim(name, length, keys, loop=False, bones=BONES):
    out = {}
    for b in bones:
        frames, pos = {}, {}
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
        out[b] = {'rotation': frames}
        if b == 'torso':
            out[b]['position'] = pos
    data = {'format_version': '1.8.0', 'animations': {name: {'animation_length': length / 20.0, 'loop': loop, 'bones': out}}}
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(data, f, indent=1)


# idle poses
anim('stance', 40, [(0, 'stance', C), (20, 'stance_b', S), (40, 'stance', S)], loop=True)
anim('carry', 20, [(0, 'carry', C), (10, 'carry_b', S), (20, 'carry', S)], loop=True, bones=['right_arm', 'left_arm'])
anim('hover', 40, [(0, 'hover', C), (20, 'hover_b', S), (40, 'hover', S)], loop=True)
anim('glide', 20, [(0, 'glide', C), (10, 'glide_b', S), (20, 'glide', S)], loop=True)

# the Reap chain (hit on tick 4)
anim('reap_0', 14, [(0, 'stance', None), (2, 'r0_wind', OUTC), (4, 'r0_cut', SNAP), (6, 'r0_over', OUTC), (14, 'stance', S)])
anim('reap_1', 14, [(0, 'r0_over', None), (2, 'r1_wind', OUTC), (4, 'r1_cut', SNAP), (6, 'r1_over', OUTC), (14, 'stance', S)])
anim('reap_2', 14, [(0, 'r1_over', None), (2, 'r2_wind', OUTC), (4, 'r2_cut', SNAP), (6, 'r2_over', OUTC), (14, 'stance', S)])
anim('reap_3', 14, [(0, 'r2_over', None), (2, 'r3_wind', OUTC), (4, 'r3_cut', SNAP), (8, 'r3_over', OUTC), (14, 'stance', S)])

anim('crescent', 40, [(0, 'stance', None), (6, 'cr_wind', OUTC), (8, 'cr_throw', SNAP), (12, 'cr_wait', OUTC), (30, 'cr_wait', S),
                      (34, 'cr_catch', SNAP), (40, 'stance', S)])
anim('tether', 36, [(0, 'stance', None), (4, 'te_cast', SNAP), (10, 'te_hold', OUTC), (15, 'te_hold', S), (19, 'te_pull', OUTC),
                    (22, 'te_pull', S), (24, 'te_cut', SNAP), (28, 'te_cut', S), (36, 'stance', S)])
anim('step', 16, [(0, 'stance', None), (2, 'st_low', OUTC), (3, 'st_low', None), (4, 'r2_wind', SNAP), (6, 'r2_cut', SNAP),
                  (8, 'r2_over', OUTC), (16, 'stance', S)])
anim('ray', 148, [(0, 'stance', None), (12, 'ra_raise', OUTC), (60, 'ra_raise', S), (80, 'ra_level', C), (92, 'ra_brace', S),
                  (94, 'ra_recoil', SNAP), (98, 'ra_brace', OUTC), (130, 'ra_brace', S), (136, 'ra_recoil', OUTC), (148, 'stance', S)])
cuts = [(0, 'stance', None), (6, 'rq_low', OUTC), (11, 'rq_low', None)]
names = ['r0_cut', 'r1_cut', 'r2_cut', 'r0_cut', 'r1_cut', 'r2_cut', 'r0_cut', 'r1_cut']
winds = ['r0_wind', 'r1_wind', 'r2_wind', 'r0_wind', 'r1_wind', 'r2_wind', 'r0_wind', 'r1_wind']
for i, t in enumerate([12, 16, 20, 24, 28, 32, 36, 40]):
    cuts.append((t - 1, winds[i], OUTC))
    cuts.append((t, names[i], SNAP))
cuts += [(44, 'r2_over', OUTC), (50, 'rq_after', C), (58, 'rq_after', S), (70, 'rq_after', S), (80, 'stance', S)]
anim('requiem', 80, cuts)
anim('rift', 112, [(0, 'stance', None), (10, 'ri_up', OUTC), (18, 'ri_up_b', S), (24, 'ri_up_b', S), (26, 'ri_cut', SNAP),
                   (50, 'ri_cut', S), (70, 'stance', S), (112, 'stance', S)])
anim('last_bloom', 210, [(0, 'stance', None), (14, 'lb_rise', OUTC), (60, 'lb_rise', S), (76, 'lb_hold', C), (118, 'lb_hold', S),
                         (126, 'lb_draw', OUTC), (128, 'lb_cut', SNAP), (170, 'lb_cut', S), (196, 'stance', S), (210, 'stance', S)])
anim('ascend', 150, [(0, 'stance', None), (8, 'as_still', OUTC), (40, 'as_still', S), (80, 'as_rise', S), (92, 'as_in', IN),
                     (97, 'as_in', S), (98, 'as_out', SNAP), (108, 'as_out_b', OUTC), (128, 'as_out_b', S), (150, 'stance', S)])
anim('release', 20, [(0, 'stance', None), (6, 'release', OUTC), (20, 'release', S)])
print('rose anims ok')
