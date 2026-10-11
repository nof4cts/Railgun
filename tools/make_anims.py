"""Generates assets/madeinheaven/player_animation/*.json (Player Animator, bedrock format) for Pucci's body during each
move, from beat-timed key poses. Ticks are 1/20 s and line up with common/Beats.java."""
import json, os, sys
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
Z = [0, 0, 0]
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
# bone -> [x, y, z] degrees (x<0 raises an arm forward, z>0 lifts the right arm out sideways)
POSE = {
    'rest': {},
    'point': {'right_arm': [-95, 8, 0], 'torso': [0, -12, 0], 'head': [0, -8, 0]},
    'command': {'right_arm': [-88, 10, 0], 'left_arm': [-10, 0, -10], 'torso': [4, -16, 0], 'head': [4, -10, 0],
                'right_leg': [-12, 0, 0], 'left_leg': [10, 0, 0]},
    'thrust': {'right_arm': [-92, -4, 0], 'left_arm': [-30, 0, -18], 'torso': [10, -24, 0], 'head': [6, -14, 0],
               'right_leg': [-26, 0, 0], 'left_leg': [22, 0, 0]},
    'windback': {'right_arm': [-20, 0, 70], 'left_arm': [-55, 0, -10], 'torso': [-4, 30, 0], 'head': [0, -18, 0]},
    'throw': {'right_arm': [-150, -10, 10], 'left_arm': [-40, 0, -20], 'torso': [-8, 20, 0], 'head': [-6, -6, 0],
              'right_leg': [-14, 0, 0], 'left_leg': [12, 0, 0]},
    'release': {'right_arm': [-70, -20, 0], 'left_arm': [-20, 0, -30], 'torso': [14, -26, 0], 'head': [8, -10, 0],
                'right_leg': [-28, 0, 0], 'left_leg': [24, 0, 0]},
    'face': {'right_arm': [-110, 40, 0], 'torso': [4, 0, 0], 'head': [8, 0, 0]},
    'slash': {'right_arm': [-80, -70, 0], 'left_arm': [-20, 0, -40], 'torso': [10, -40, 0], 'head': [4, 30, 0],
              'right_leg': [-30, 0, 0], 'left_leg': [26, 0, 0]},
    'crouch': {'right_arm': [-30, 0, 30], 'left_arm': [-30, 0, -30], 'torso': [28, 0, 0], 'head': [-22, 0, 0],
               'right_leg': [-48, 0, 0], 'left_leg': [-48, 0, 0]},
    'chop_up': {'right_arm': [-170, 0, 10], 'left_arm': [-30, 0, -20], 'torso': [-10, 10, 0], 'head': [-10, 0, 0]},
    'chop': {'right_arm': [-40, 0, 0], 'left_arm': [-20, 0, -20], 'torso': [26, -16, 0], 'head': [10, 0, 0],
             'right_leg': [-30, 0, 0], 'left_leg': [24, 0, 0]},
    'dash': {'right_arm': [40, 0, 20], 'left_arm': [40, 0, -20], 'torso': [36, 0, 0], 'head': [-30, 0, 0],
             'right_leg': [-40, 0, 0], 'left_leg': [30, 0, 0]},
    'jab_r': {'right_arm': [-90, 6, 0], 'left_arm': [-50, -10, 0], 'torso': [12, -20, 0], 'head': [4, -8, 0],
              'right_leg': [-24, 0, 0], 'left_leg': [20, 0, 0]},
    'jab_l': {'left_arm': [-90, -6, 0], 'right_arm': [-50, 10, 0], 'torso': [12, 20, 0], 'head': [4, 8, 0],
              'right_leg': [-24, 0, 0], 'left_leg': [20, 0, 0]},
    'cross': {'right_arm': [-80, 40, 0], 'left_arm': [-80, -40, 0], 'torso': [6, 0, 0], 'head': [12, 0, 0]},
    'burst': {'right_arm': [-30, 0, 80], 'left_arm': [-30, 0, -80], 'torso': [-10, 0, 0], 'head': [-16, 0, 0],
              'right_leg': [-10, 0, 0], 'left_leg': [10, 0, 0]},
    'heaven': {'right_arm': [-178, 10, 0], 'left_arm': [-10, 0, -12], 'torso': [-6, 0, 0], 'head': [-30, 0, 0]},
    'open': {'right_arm': [-60, 0, 70], 'left_arm': [-60, 0, -70], 'torso': [-12, 0, 0], 'head': [-34, 0, 0],
             'right_leg': [-8, 0, 0], 'left_leg': [8, 0, 0]},
    'float': {'right_arm': [-40, 0, 85], 'left_arm': [-40, 0, -85], 'torso': [-8, 0, 0], 'head': [-26, 0, 0],
              'right_leg': [-20, 0, 0], 'left_leg': [10, 0, 0]},
}

def pose(name, extra=None):
    p = {b: list(POSE[name].get(b, Z)) for b in BONES}
    for b, v in (extra or {}).items():
        p[b] = v
    return p

def anim(name, length, keys):
    bones = {}
    for b in BONES:
        frames = {}
        for tick, p, ease in keys:
            k = {'vector': p[b]}
            if ease:
                k['easing'] = ease
            frames[f'{tick / 20.0:.2f}'] = k
        bones[b] = {'rotation': frames}
    data = {'format_version': '1.8.0', 'animations': {name: {'animation_length': length / 20.0, 'bones': bones}}}
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(data, f, indent=1)

R = lambda: pose('rest')
anim('summon', 22, [(0, R(), None), (4, pose('point'), 'easeOutBack'), (14, pose('point'), None), (22, R(), 'easeInOutSine')])
anim('barrage', 52, [(0, R(), None), (4, pose('command'), 'easeOutCubic'), (46, pose('command'), None), (52, R(), 'easeInOutSine')])
anim('finisher', 24, [(0, R(), None), (7, pose('windback'), 'easeOutCubic'), (10, pose('thrust'), 'easeOutExpo'), (16, pose('thrust'), None),
                      (24, R(), 'easeInOutSine')])
anim('knives', 18, [(0, R(), None), (5, pose('throw'), 'easeOutCubic'), (8, pose('release'), 'easeOutExpo'), (12, pose('release'), None),
                    (18, R(), 'easeInOutSine')])
anim('speed_slice', 58, [(0, R(), None), (8, pose('face'), 'easeOutCubic'), (17, pose('face'), None), (19, pose('dash'), 'easeOutExpo'),
                         (24, pose('slash'), 'easeOutExpo'), (40, pose('slash'), None), (58, R(), 'easeInOutSine')])
anim('infinite_speed', 48, [(0, R(), None), (9, pose('crouch'), 'easeOutCubic'), (13, pose('dash'), 'easeOutExpo'), (15, pose('chop_up'), 'easeOutExpo'),
                            (17, pose('chop'), 'easeOutExpo'), (32, pose('chop'), None), (48, R(), 'easeInOutSine')])
jk = [(0, R(), None), (12, pose('crouch'), 'easeOutCubic'), (16, pose('crouch'), None), (18, pose('dash'), 'easeOutExpo')]
for i, t in enumerate(range(22, 54, 4)):
    jk.append((t, pose('jab_r' if i % 2 == 0 else 'jab_l'), 'easeOutExpo'))
jk += [(56, pose('windback'), 'easeOutCubic'), (60, pose('thrust'), 'easeOutExpo'), (78, pose('thrust'), None), (100, R(), 'easeInOutSine')]
anim('judgement', 100, jk)
anim('double_accel', 50, [(0, R(), None), (10, pose('cross'), 'easeOutCubic'), (26, pose('cross'), None), (29, pose('burst'), 'easeOutExpo'),
                          (40, pose('burst'), None), (50, R(), 'easeInOutSine')])
anim('time_accel', 92, [(0, R(), None), (12, pose('heaven'), 'easeOutCubic'), (46, pose('heaven'), None), (52, pose('open'), 'easeOutExpo'),
                        (80, pose('open'), None), (92, R(), 'easeInOutSine')])
anim('universe_reset', 300, [(0, pose('open'), None), (40, pose('open'), None), (60, pose('float'), 'easeInOutSine'), (210, pose('float'), None),
                             (230, pose('open'), 'easeInOutSine'), (280, pose('open'), None), (300, R(), 'easeInOutSine')])
print('animations written:', len(os.listdir(OUT)))
