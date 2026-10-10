"""Generates assets/santoryu/player_animation/*.json (Player Animator, bedrock format) from beat-timed key poses.
Ticks are 1/20 s and line up with common/Beats.java."""
import json, os, sys
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

Z = [0, 0, 0]
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']

# named key poses: bone -> [x, y, z] degrees (x<0 raises an arm forward, z>0 lifts the right arm out sideways)
POSE = {
    'rest':     {},
    'draw':     {'right_arm': [-30, 40, 0], 'left_arm': [-20, -10, 0], 'torso': [5, 15, 0]},
    'cross':    {'right_arm': [-95, 38, 0], 'left_arm': [-95, -38, 0], 'torso': [18, 0, 0], 'head': [12, 0, 0],
                 'right_leg': [-28, 0, 0], 'left_leg': [22, 0, 0]},
    'spread':   {'right_arm': [-25, 0, 75], 'left_arm': [-25, 0, -75], 'torso': [-6, 0, 0], 'head': [-6, 0, 0],
                 'right_leg': [-15, 0, 0], 'left_leg': [12, 0, 0]},
    'iai':      {'right_arm': [-38, 52, 0], 'left_arm': [-24, 18, 0], 'torso': [16, -28, 0], 'head': [-12, 26, 0],
                 'right_leg': [-38, 0, 0], 'left_leg': [30, 0, 0]},
    'sheathe':  {'right_arm': [-22, 40, 0], 'left_arm': [-18, 15, 0], 'torso': [2, -6, 0], 'head': [5, 0, 0]},
    'drawback': {'right_arm': [-10, 0, 95], 'left_arm': [-10, 0, -95], 'torso': [-4, 30, 0], 'head': [0, -25, 0],
                 'right_leg': [-20, 0, 0], 'left_leg': [18, 0, 0]},
    'fling':    {'right_arm': [-95, -25, 0], 'left_arm': [-95, 25, 0], 'torso': [14, -18, 0], 'head': [4, 12, 0],
                 'right_leg': [-30, 0, 0], 'left_leg': [26, 0, 0]},
    'low':      {'right_arm': [-28, 0, 38], 'left_arm': [-28, 0, -38], 'torso': [12, 0, 0], 'head': [18, 0, 0],
                 'right_leg': [-18, 0, 0], 'left_leg': [14, 0, 0]},
    'rise':     {'right_arm': [-40, 0, 55], 'left_arm': [-40, 0, -55], 'torso': [-4, 0, 0], 'head': [-14, 0, 0]},
    'mill':     {'right_arm': [-90, 12, 0], 'left_arm': [-90, -12, 0], 'torso': [6, 0, 0], 'head': [0, 0, 0],
                 'right_leg': [-14, 0, 0], 'left_leg': [10, 0, 0]},
    'high':     {'right_arm': [-165, 25, 0], 'left_arm': [-165, -25, 0], 'torso': [-8, 0, 0], 'head': [-18, 0, 0],
                 'right_leg': [-10, 0, 0], 'left_leg': [8, 0, 0]},
    'enma':     {'right_arm': [-70, 10, 0], 'left_arm': [-35, -15, 0], 'torso': [6, -10, 0], 'head': [8, 5, 0]},
    'crouch':   {'right_arm': [-20, 0, 35], 'left_arm': [-20, 0, -35], 'torso': [30, 0, 0], 'head': [-20, 0, 0],
                 'right_leg': [-50, 0, 0], 'left_leg': [-50, 0, 0]},
    'leap':     {'right_arm': [-150, 0, 40], 'left_arm': [-150, 0, -40], 'torso': [-12, 0, 0], 'head': [-25, 0, 0],
                 'right_leg': [20, 0, 0], 'left_leg': [-35, 0, 0]},
}

def pose(name, extra=None):
    p = {b: list(POSE[name].get(b, Z)) for b in BONES}
    for b, v in (extra or {}).items():
        p[b] = v
    return p

def anim(name, length, keys):
    """keys: list of (tick, pose_dict, easing)"""
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

# Oni Giri: dash at 7, cut at 9, length 26
anim('oni_giri', 26, [(0, R(), None), (5, pose('cross'), 'easeOutBack'), (7, pose('cross'), None),
                      (9, pose('spread'), 'easeOutExpo'), (16, pose('spread'), None), (26, R(), 'easeInOutSine')])
# Tatsu Maki: a full body turn per pulse (every 6 ticks from 6)
tk = [(0, R(), None), (4, pose('spread'), 'easeOutCubic')]
for i, t in enumerate(range(6, 36, 3)):
    tk.append((t, pose('spread', {'torso': [0, (i + 1) * 180 % 360 - 180, 0]}), 'linear'))
tk += [(38, pose('spread'), 'easeOutCubic'), (46, R(), 'easeInOutSine')]
anim('tatsu_maki', 46, tk)
# Sanjuroku Pound Ho: draw back, fire at 9
anim('pound_ho', 34, [(0, R(), None), (6, pose('drawback'), 'easeOutCubic'), (8, pose('drawback'), None),
                      (10, pose('fling'), 'easeOutExpo'), (20, pose('fling'), None), (34, R(), 'easeInOutSine')])
# Shishi Sonson: stance and stillness, vanish at 26, slow sheathe, click at 48
anim('shishi_sonson', 74, [(0, R(), None), (8, pose('iai'), 'easeOutCubic'), (24, pose('iai', {'head': [-14, 22, 0]}), 'easeInOutSine'),
                           (27, pose('spread', {'right_arm': [-60, -40, 40], 'left_arm': [-20, 30, 0]}), 'easeOutExpo'),
                           (34, pose('spread', {'right_arm': [-55, -30, 30]}), None), (46, pose('sheathe'), 'easeInOutCubic'),
                           (48, pose('sheathe', {'right_arm': [-18, 46, 0]}), 'easeInExpo'), (62, pose('sheathe'), None), (74, R(), 'easeInOutSine')])
# Hyakuhachi Pound Ho: three blades wound back with the body turning, fire at 48
hk = [(0, R(), None), (10, pose('drawback'), 'easeOutCubic')]
for i, t in enumerate(range(14, 46, 4)):
    hk.append((t, pose('drawback', {'torso': [0, (i + 1) * 120 % 360 - 180, 0]}), 'linear'))
hk += [(46, pose('drawback'), 'easeInQuad'), (49, pose('fling'), 'easeOutExpo'), (64, pose('fling'), None), (84, R(), 'easeInOutSine')]
anim('hyakuhachi', 84, hk)
# Kokujo: haki stance, then the twist that throws the dragon tornado at 36, held as it rages
anim('kokujo', 104, [(0, R(), None), (10, pose('low'), 'easeOutCubic'), (30, pose('low', {'head': [24, 0, 0]}), 'easeInOutSine'),
                     (34, pose('drawback'), 'easeInQuad'), (37, pose('spread', {'torso': [0, -60, 0]}), 'easeOutExpo'),
                     (60, pose('spread', {'torso': [0, -40, 0]}), 'easeInOutSine'), (84, pose('spread'), None),
                     (88, pose('fling'), 'easeOutBack'), (104, R(), 'easeInOutSine')])
# Sanzen Sekai: three draws, the windmills, the charge at 92, the cut at 102
sk = [(0, R(), None), (6, pose('draw'), 'easeOutCubic'), (14, pose('draw', {'left_arm': [-40, -30, 0]}), 'easeOutCubic'),
      (22, pose('cross'), 'easeOutBack'), (38, pose('mill'), 'easeInOutSine')]
for i, t in enumerate(range(42, 90, 4)):
    w = 18 if i % 2 == 0 else -18
    sk.append((t, pose('mill', {'right_arm': [-90 + w, 12, 0], 'left_arm': [-90 - w, -12, 0]}), 'easeInOutSine'))
sk += [(90, pose('cross'), 'easeInQuad'), (92, pose('cross'), None), (102, pose('spread'), 'easeOutExpo'),
       (116, pose('spread'), None), (134, R(), 'easeInOutSine')]
anim('sanzen_sekai', 134, sk)
# Asura: head bowed, the aura, rising at the manifest (66), the stance, vanish at 152, cut at 164
anim('asura', 214, [(0, R(), None), (14, pose('low', {'head': [30, 0, 0]}), 'easeInOutSine'), (60, pose('low', {'head': [34, 0, 0]}), None),
                    (68, pose('rise'), 'easeOutBack'), (110, pose('rise', {'head': [-8, 0, 0]}), 'easeInOutSine'),
                    (140, pose('cross'), 'easeInOutCubic'), (152, pose('cross'), None), (164, pose('spread'), 'easeOutExpo'),
                    (190, pose('spread', {'head': [10, 0, 0]}), 'easeInOutSine'), (200, pose('sheathe'), 'easeInOutCubic'), (214, R(), 'easeInOutSine')])
# Rengoku Oni Giri: Enma raised, the drain, the conqueror's wave at 46, crossed high, vanish 118, cut 126
anim('rengoku', 174, [(0, R(), None), (8, pose('enma'), 'easeOutCubic'), (40, pose('enma', {'head': [16, 5, 0]}), 'easeInOutSine'),
                      (47, pose('spread', {'head': [-20, 0, 0]}), 'easeOutExpo'), (58, pose('spread'), None),
                      (80, pose('high'), 'easeInOutCubic'), (112, pose('high'), None), (118, pose('cross'), 'easeInExpo'),
                      (126, pose('spread'), 'easeOutExpo'), (150, pose('spread'), None), (174, R(), 'easeInOutSine')])
# Dragon Damnation: the gathering, the wave at 52, three blades, the crouch, the leap at 182, the cut at 202, the landing
anim('dragon', 274, [(0, R(), None), (20, pose('low', {'head': [28, 0, 0]}), 'easeInOutSine'), (50, pose('low'), None),
                     (53, pose('spread', {'head': [-22, 0, 0]}), 'easeOutExpo'), (66, pose('spread'), None),
                     (90, pose('cross'), 'easeInOutCubic'), (150, pose('cross', {'head': [8, 0, 0]}), 'easeInOutSine'),
                     (172, pose('crouch'), 'easeInOutCubic'), (182, pose('leap'), 'easeOutExpo'), (198, pose('high'), 'easeInOutSine'),
                     (202, pose('spread'), 'easeOutExpo'), (232, pose('spread'), None), (244, pose('sheathe'), 'easeInOutCubic'),
                     (274, R(), 'easeInOutSine')])
print('animations written')
