"""Generates assets/celestial/player_animation/*.json (Player Animator, bedrock format): the Celestial Sage's body for
every rite, timed to common/SBeats.java (ticks of 1/20 s).

The sage moves like still water: long, slow holds and breathing, then one decisive motion on the beat. The Nullstaff
crosses the right fist like any held item (it points along the hand's forward), so with the right arm raised forward
to about -65 degrees it stands upright, planted; lowered, it levels into a thrust; raised overhead, it lies back
over the shoulder ready to chop.

Angles are degrees per bone: arms x < 0 raise forward/up, z > 0 lifts the right arm outward (z < 0 the left);
torso x > 0 bows forward; head x < 0 looks up. 'pos' moves the body in pixels (y < 0 sinks, bending the knees).
Usage: make_sage_anims.py <assets/celestial/player_animation>"""
import json
import os
import sys

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
ZERO = [0, 0, 0]

SNAP, GLIDE, SETTLE, IN, BREATH = 'easeOutExpo', 'easeInOutSine', 'easeOutCubic', 'easeInCubic', 'easeInOutQuad'

# the sage at rest, staff planted upright before him, free hand loose at the side
CALM = {'right_arm': [-64, -10, 6], 'left_arm': [6, 0, -7], 'head': [4, 0, 0], 'right_leg': [-3, 0, 3], 'left_leg': [4, 0, -3]}


def P(base=None, **bones):
    out = {b: list((base or {}).get(b, ZERO)) for b in BONES + ['pos']}
    for b, v in bones.items():
        out[b] = list(v)
    return out


def anim(name, length, keys, loop=False):
    """keys: (tick, pose, easing)."""
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
    data = {'format_version': '1.8.0', 'animations': {name: {'animation_length': length / 20.0, 'loop': loop, 'bones': bones}}}
    with open(os.path.join(OUT, name + '.json'), 'w') as f:
        json.dump(data, f, indent=1)
    print(name, length)


calm = P(CALM)
calm_b = P(CALM, right_arm=[-61, -10, 6], left_arm=[3, 0, -9], head=[1, 0, 0])

# ------------------------------------------------------------------------------------------------ idles
anim('calm', 60, [(0, calm, None), (30, calm_b, BREATH), (60, calm, BREATH)], loop=True)

flt = P(CALM, right_arm=[-66, -8, 12], left_arm=[-8, 0, -20], head=[8, 0, 0], torso=[-2, 0, 0],
        right_leg=[-12, 0, 5], left_leg=[16, 0, -4])
flt_b = P(flt, right_arm=[-63, -8, 14], left_arm=[-4, 0, -24], right_leg=[-8, 0, 5], left_leg=[20, 0, -4], head=[5, 0, 0])
anim('float', 50, [(0, flt, None), (25, flt_b, BREATH), (50, flt, BREATH)], loop=True)

drift = P(right_arm=[-40, 0, 16], left_arm=[34, 0, -14], torso=[20, 0, 0], head=[-18, 0, 0], right_leg=[18, 0, 3], left_leg=[28, 0, -3])
drift_b = P(drift, left_arm=[40, 0, -16], right_leg=[22, 0, 3], left_leg=[24, 0, -3])
anim('drift', 30, [(0, drift, None), (15, drift_b, BREATH), (30, drift, BREATH)], loop=True)

# ------------------------------------------------------------------------------------------------ Heaven's Mantle (130)
mudra = P(right_arm=[-72, -38, 0], left_arm=[-72, 38, 0], head=[26, 0, 0], torso=[6, 0, 0], right_leg=[-2, 0, 2], left_leg=[2, 0, -2])
mudra_b = P(mudra, right_arm=[-76, -40, 0], left_arm=[-76, 40, 0], head=[22, 0, 0])
lift = P(mudra, head=[-16, 0, 0], torso=[-2, 0, 0])
coil = P(right_arm=[-40, -46, -6], left_arm=[-40, 46, 6], head=[18, 0, 0], torso=[16, 0, 0], pos=[0, -2, 0],
         right_leg=[-14, 0, 6], left_leg=[10, 0, -6])
burst = P(right_arm=[-24, 0, 116], left_arm=[-24, 0, -116], head=[-28, 0, 0], torso=[-12, 0, 0], pos=[0, 1, 0],
          right_leg=[4, 0, 12], left_leg=[-4, 0, -12])
burst_o = P(burst, right_arm=[-28, 0, 124], left_arm=[-28, 0, -124], head=[-32, 0, 0], torso=[-14, 0, 0])
anim('mantle', 130, [(0, P(), None), (10, mudra, GLIDE), (40, mudra_b, BREATH), (64, mudra, BREATH), (76, lift, GLIDE),
                     (90, coil, IN), (96, burst, SNAP), (100, burst_o, SETTLE), (112, burst, GLIDE), (128, calm, GLIDE)])

bow = P(right_arm=[-20, 0, 8], left_arm=[-10, 0, -8], head=[24, 0, 0], torso=[8, 0, 0])
anim('unmantle', 20, [(0, calm, None), (8, bow, GLIDE), (20, P(), GLIDE)])

# ------------------------------------------------------------------------------------------------ Staff Rite (16 each)
# 0: the thrust - the staff levels from the hip and drives forward with a lunge
w0 = P(right_arm=[34, 8, 10], left_arm=[-30, 30, -10], torso=[-6, 26, 0], head=[0, -22, 0], right_leg=[12, 0, 4], left_leg=[-16, 0, -4],
       pos=[0, -1, 0])
h0 = P(right_arm=[-16, 10, 2], left_arm=[30, -10, -16], torso=[18, -20, 0], head=[-10, 18, 0], right_leg=[26, 0, 4], left_leg=[-38, 0, -4],
       pos=[0, -3, 0])
o0 = P(h0, right_arm=[-22, 12, 2], torso=[20, -24, 0])
anim('staff_0', 16, [(0, calm, None), (1, w0, SETTLE), (3, w0, None), (4, h0, SNAP), (8, o0, SETTLE), (16, calm, GLIDE)])

# 1: the sweep - the upright staff swings across the body right to left like a closing gate
w1 = P(right_arm=[-62, 74, 44], left_arm=[-20, 20, -20], torso=[0, 44, 0], head=[0, -30, 0], right_leg=[-6, 0, 8], left_leg=[8, 0, -6],
       pos=[0, -1, 0])
h1 = P(right_arm=[-64, -66, -14], left_arm=[-10, -20, -30], torso=[6, -40, 0], head=[0, 26, 0], right_leg=[-22, 0, 6], left_leg=[18, 0, -6],
       pos=[0, -2, 0])
o1 = P(h1, right_arm=[-60, -76, -18], torso=[6, -46, 0])
anim('staff_1', 16, [(0, calm, None), (1, w1, SETTLE), (3, w1, None), (4, h1, SNAP), (8, o1, SETTLE), (16, calm, GLIDE)])

# 2: the overhead chop - both hands, staff laid back over the shoulder, brought down through the target
w2 = P(right_arm=[-172, 0, 10], left_arm=[-166, 0, -12], torso=[-14, 0, 0], head=[-12, 0, 0], right_leg=[-10, 0, 4], left_leg=[8, 0, -4],
       pos=[0, 1, 0])
h2 = P(right_arm=[-22, 0, 4], left_arm=[-30, 0, -6], torso=[26, 0, 0], head=[-14, 0, 0], right_leg=[22, 0, 4], left_leg=[-30, 0, -4],
       pos=[0, -3, 0])
o2 = P(h2, right_arm=[-16, 0, 4], left_arm=[-24, 0, -6], torso=[30, 0, 0])
anim('staff_2', 16, [(0, calm, None), (1, w2, SETTLE), (3, w2, None), (4, h2, SNAP), (8, o2, SETTLE), (16, calm, GLIDE)])

# ------------------------------------------------------------------------------------------------ Null Orbs (70)
point = P(CALM, left_arm=[-92, 4, -4], head=[2, 8, 0], torso=[2, 8, 0])
kick = P(point, left_arm=[-82, 4, -4], torso=[0, 10, 0])
keys = [(0, calm, None), (6, point, SETTLE)]
for i in range(9):
    t = 8 + i * 4
    keys += [(t, kick, SNAP), (t + 2, point, SETTLE)]
keys += [(56, point, BREATH), (70, calm, GLIDE)]
anim('orbs', 70, keys)

# ------------------------------------------------------------------------------------------------ Heaven's Pull (40)
reach = P(CALM, left_arm=[-96, 0, 2], torso=[8, 10, 0], head=[0, 8, 0], left_leg=[-12, 0, -3])
yank = P(CALM, left_arm=[-38, 34, 4], torso=[-8, -22, 0], head=[-4, -14, 0], right_leg=[10, 0, 3], left_leg=[-8, 0, -3])
drive = P(right_arm=[-14, 8, 0], left_arm=[20, -10, -14], torso=[20, -14, 0], head=[-12, 10, 0], right_leg=[24, 0, 4], left_leg=[-34, 0, -4],
          pos=[0, -3, 0])
anim('pull', 40, [(0, calm, None), (4, reach, SNAP), (6, reach, None), (15, yank, IN), (18, P(drive, right_arm=[30, 8, 6]), SETTLE),
                  (20, drive, SNAP), (24, drive, None), (40, calm, GLIDE)])

# ------------------------------------------------------------------------------------------------ Mirror Step (22)
sink = P(CALM, torso=[22, 0, 0], head=[-14, 0, 0], pos=[0, -4, 0], right_leg=[-20, 0, 4], left_leg=[10, 0, -4])
back = P(right_arm=[-120, -64, 66], left_arm=[-30, 20, -20], torso=[4, -46, 0], head=[0, 34, 0], pos=[0, -1, 0])
cut = P(right_arm=[-46, 62, -14], left_arm=[10, -20, -30], torso=[12, 44, 0], head=[0, -26, 0], right_leg=[-20, 0, 6], left_leg=[22, 0, -6],
        pos=[0, -2, 0])
anim('mirror', 22, [(0, calm, None), (3, sink, SNAP), (9, back, SETTLE), (11, cut, SNAP), (14, P(cut, torso=[14, 50, 0]), SETTLE),
                    (22, calm, GLIDE)])

# ------------------------------------------------------------------------------------------------ Starfall (150)
up = P(CALM, left_arm=[-178, 0, -6], head=[-42, 0, 0], torso=[-6, 0, 0])
up_b = P(up, left_arm=[-176, 2, -8], head=[-40, 0, 0])
down = P(CALM, left_arm=[-72, 6, -4], head=[6, 4, 0], torso=[12, 4, 0], pos=[0, -1, 0], left_leg=[-10, 0, -3])
anim('starfall', 150, [(0, calm, None), (16, up, GLIDE), (40, up_b, BREATH), (64, up, BREATH), (88, up_b, BREATH),
                       (93, P(up, left_arm=[-184, 0, -6]), IN), (96, down, SNAP), (110, down, None), (114, P(down, left_arm=[-86, 6, -4]), GLIDE),
                       (116, down, SNAP), (130, down, None), (150, calm, GLIDE)])

# ------------------------------------------------------------------------------------------------ Thornwood (70)
raise_ = P(right_arm=[-170, 0, 10], left_arm=[-162, 0, -10], head=[-10, 0, 0], torso=[-8, 0, 0], pos=[0, 1, 0])
plant = P(right_arm=[-40, 0, 4], left_arm=[-48, 0, -4], head=[-8, 0, 0], torso=[32, 0, 0], pos=[0, -5, 0], right_leg=[-34, 0, 4],
          left_leg=[30, 0, -4])
anim('thornwood', 70, [(0, calm, None), (6, raise_, SETTLE), (8, plant, SNAP), (22, plant, None), (36, P(plant, torso=[20, 0, 0], pos=[0, -3, 0]), GLIDE),
                       (70, calm, GLIDE)])

# ------------------------------------------------------------------------------------------------ Colossus (150)
open_ = P(right_arm=[-30, 0, 58], left_arm=[-30, 0, -58], head=[-8, 0, 0])
wide = P(right_arm=[-60, 0, 92], left_arm=[-60, 0, -92], head=[-24, 0, 0], torso=[-6, 0, 0])
over = P(right_arm=[-176, 0, 8], left_arm=[-176, 0, -8], head=[-26, 0, 0], torso=[-14, 0, 0], pos=[0, 1, 0])
chop = P(right_arm=[-30, 0, 4], left_arm=[-34, 0, -4], head=[-10, 0, 0], torso=[30, 0, 0], pos=[0, -4, 0], right_leg=[22, 0, 4],
         left_leg=[-32, 0, -4])
anim('colossus', 150, [(0, calm, None), (12, open_, GLIDE), (58, wide, GLIDE), (70, over, GLIDE), (95, P(over, right_arm=[-184, 0, 8], left_arm=[-184, 0, -8]), IN),
                       (98, chop, SNAP), (104, P(chop, torso=[34, 0, 0]), SETTLE), (128, chop, None), (150, calm, GLIDE)])

# ------------------------------------------------------------------------------------------------ Eternal Eclipse (240)
offer = P(right_arm=[-150, 0, 30], left_arm=[-150, 0, -30], head=[-32, 0, 0], torso=[-8, 0, 0])
offer_b = P(offer, right_arm=[-154, 0, 28], left_arm=[-154, 0, -28], head=[-34, 0, 0])
draw_in = P(right_arm=[-82, -34, 0], left_arm=[-82, 34, 0], head=[14, 0, 0], torso=[12, 0, 0], pos=[0, -2, 0])
spread = P(right_arm=[-6, 0, 96], left_arm=[-6, 0, -96], head=[-12, 0, 0], torso=[-8, 0, 0], pos=[0, 1, 0], right_leg=[2, 0, 10],
           left_leg=[-2, 0, -10])
anim('eclipse', 240, [(0, calm, None), (10, P(offer, right_arm=[-100, 0, 30], left_arm=[-100, 0, -30]), GLIDE), (40, offer, GLIDE),
                      (70, offer_b, BREATH), (104, offer, BREATH), (124, draw_in, GLIDE), (137, P(draw_in, pos=[0, -3, 0], torso=[16, 0, 0]), IN),
                      (140, spread, SNAP), (176, spread, None), (200, P(spread, right_arm=[-10, 0, 70], left_arm=[-10, 0, -70]), GLIDE),
                      (240, calm, GLIDE)])
