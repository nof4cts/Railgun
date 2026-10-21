"""Modulo Yuji's body (Player Animator, bedrock format): assets/yuji/player_animation/yuji_<move>.json, one per move,
keyed to the same ticks as the move's timeline (common/Script.java), plus the subtitle lang file.

    python3 tools/gen_anims.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "yuji")
OUT = os.path.join(ROOT, "player_animation")
os.makedirs(OUT, exist_ok=True)
BONES = ['head', 'torso', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
Z = [0, 0, 0]
SNAP, GLIDE, SETTLE, IN, BREATH = 'easeOutExpo', 'easeInOutSine', 'easeOutCubic', 'easeInCubic', 'easeInOutQuad'


def P(base=None, **bones):
    out = {b: list((base or {}).get(b, Z)) for b in BONES + ['pos']}
    for b, v in bones.items():
        out[b] = list(v)
    return out


def anim(move, length, keys):
    name = 'yuji_' + move
    keys = sorted(keys, key=lambda k: k[0])
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


STAND = P(right_arm=[4, 0, 6], left_arm=[4, 0, -6])
GUARD = P(right_arm=[-50, 20, 10], left_arm=[-60, -25, -10], torso=[6, 10, 0], head=[0, -8, 0], right_leg=[-8, 0, 4], left_leg=[10, 0, -4], pos=[0, -0.6, 0])
WIND = P(right_arm=[40, 10, 28], left_arm=[-70, -25, -6], torso=[8, 32, 0], head=[4, -26, 0], right_leg=[14, 0, 5], left_leg=[-16, 0, -5], pos=[0, -1.4, 0])
PUNCH = P(right_arm=[-92, -8, 0], left_arm=[25, 0, -18], torso=[12, -30, 0], head=[0, 24, 0], right_leg=[-14, 0, 5], left_leg=[26, 0, -5], pos=[0, -1.2, 0])
PUNCH_L = P(left_arm=[-92, 8, 0], right_arm=[25, 0, 18], torso=[12, 30, 0], head=[0, -24, 0], left_leg=[-14, 0, -5], right_leg=[26, 0, 5], pos=[0, -1.2, 0])
DASH = P(right_arm=[45, 0, 18], left_arm=[45, 0, -18], torso=[28, 0, 0], head=[-20, 0, 0], right_leg=[-30, 0, 4], left_leg=[30, 0, -4], pos=[0, -1.2, 0])
UPPER = P(right_arm=[-165, -10, 8], left_arm=[20, 0, -14], torso=[-14, -18, 0], head=[-20, 10, 0], right_leg=[-6, 0, 4], left_leg=[12, 0, -4])
HAMMER_UP = P(right_arm=[-175, 10, 12], left_arm=[-175, -10, -12], torso=[-12, 0, 0], head=[-14, 0, 0])
HAMMER = P(right_arm=[-50, 10, 0], left_arm=[-50, -10, 0], torso=[36, 0, 0], head=[16, 0, 0], right_leg=[-20, 0, 4], left_leg=[20, 0, -4], pos=[0, -2, 0])
CLASP = P(right_arm=[-78, 38, 0], left_arm=[-78, -38, 0], torso=[4, 0, 0], head=[6, 0, 0], right_leg=[-6, 0, 4], left_leg=[8, 0, -4])
AIM = P(right_arm=[-90, 4, 0], left_arm=[-86, -24, 0], torso=[0, -12, 0], head=[0, 10, 0], right_leg=[-10, 0, 4], left_leg=[12, 0, -4])
RAISE = P(right_arm=[-170, 0, 18], left_arm=[-170, 0, -18], torso=[-8, 0, 0], head=[-24, 0, 0], right_leg=[0, 0, 6], left_leg=[0, 0, -6])
SLASH_A = P(right_arm=[-115, 70, 0], left_arm=[10, 0, -10], torso=[0, 34, 0], head=[0, -20, 0], right_leg=[10, 0, 4], left_leg=[-10, 0, -4])
SLASH_B = P(right_arm=[-60, -70, 0], left_arm=[20, 0, -16], torso=[6, -36, 0], head=[0, 22, 0], right_leg=[-12, 0, 4], left_leg=[14, 0, -4])
SIGN = P(right_arm=[-104, 28, 0], left_arm=[-104, -28, 0], torso=[2, 0, 0], head=[10, 0, 0], right_leg=[0, 0, 5], left_leg=[0, 0, -5])
POWER = P(right_arm=[-20, 0, 64], left_arm=[-20, 0, -64], torso=[-12, 0, 0], head=[-22, 0, 0], right_leg=[-6, 0, 10], left_leg=[6, 0, -10], pos=[0, -2, 0])
CROUCH = P(right_arm=[30, 0, 20], left_arm=[30, 0, -20], torso=[30, 0, 0], head=[-24, 0, 0], right_leg=[-50, 0, 4], left_leg=[-50, 0, -4], pos=[0, -4, 0])
DIVE = P(right_arm=[-175, 0, 6], left_arm=[-175, 0, -6], torso=[70, 0, 0], head=[-50, 0, 0], right_leg=[20, 0, 4], left_leg=[20, 0, -4])
GROUND_PUNCH = P(right_arm=[-30, 0, 4], left_arm=[30, 0, -40], torso=[60, 0, 0], head=[-30, 0, 0], right_leg=[-60, 0, 6], left_leg=[-10, 0, -6], pos=[0, -6, 0])


def tremble(pose, t0, t1, step=2, amt=3):
    out = []
    for k, t in enumerate(range(t0, t1, step)):
        s = amt if k % 2 else -amt
        q = P(pose, right_arm=[pose['right_arm'][0] + s, pose['right_arm'][1], pose['right_arm'][2]],
              torso=[pose['torso'][0], pose['torso'][1] + s * 0.5, pose['torso'][2]])
        out.append((t, q, BREATH))
    return out


def strike(t, pose=PUNCH, hold=6):
    """Snap into the blow one tick before contact, hold it through the hit-stop."""
    return [(t - 1, pose, SNAP), (t + hold, pose, None)]


def windup(t0, t1):
    return [(t0, WIND, SETTLE)] + tremble(WIND, t0 + 2, t1)


# ---- Black Flash
anim('perfect_impact', 52, [(0, STAND, None), (4, DASH, SNAP), (12, WIND, SETTLE)] + tremble(WIND, 14, 22) + strike(24, hold=8) + [(52, STAND, GLIDE)])
anim('zero_distance', 34, [(0, STAND, None), (3, WIND, SNAP)] + tremble(WIND, 5, 12, 1, 4) + strike(13, hold=8) + [(34, STAND, GLIDE)])
anim('relentless_chain', 112, [(0, STAND, None), (4, DASH, SNAP), (12, WIND, SETTLE)] + strike(14, PUNCH, 4)
     + [(22, DASH, SNAP), (28, WIND, SETTLE)] + strike(30, PUNCH_L, 4)
     + [(38, CROUCH, SNAP), (44, CROUCH, None)] + strike(46, UPPER, 4)
     + [(54, HAMMER_UP, SNAP), (58, HAMMER_UP, None)] + strike(60, HAMMER, 4)
     + [(68, DASH, SNAP)] + windup(72, 78) + strike(80, PUNCH, 10) + [(112, STAND, GLIDE)])
ex = [(0, STAND, None)]
for k, t in enumerate([10, 16, 22, 28, 34, 40]):
    ex += [(t - 3, WIND if k % 2 == 0 else P(WIND, torso=[8, -32, 0]), SETTLE)] + strike(t, PUNCH if k % 2 == 0 else PUNCH_L, 1)
ex += windup(44, 58) + [(58, DASH, SNAP)] + strike(62, PUNCH, 10) + [(94, STAND, GLIDE)]
anim('relentless_execution', 94, ex)
anim('void_shatter', 86, [(0, STAND, None), (6, GUARD, SETTLE)] + windup(14, 30) + [(32, DASH, SNAP)] + strike(41, PUNCH, 12) + [(60, GUARD, GLIDE), (86, STAND, GLIDE)])
anim('world_breaker', 142, [(0, STAND, None), (10, POWER, SETTLE), (26, POWER, None)] + windup(30, 58) + [(60, DASH, SNAP)] + strike(66, PUNCH, 14)
     + [(90, GUARD, GLIDE), (142, STAND, GLIDE)])
# ---- Blood
anim('piercing_blood', 30, [(0, STAND, None), (3, CLASP, SNAP), (7, CLASP, None), (9, AIM, SNAP), (18, AIM, None), (30, STAND, GLIDE)])
anim('homing_blood', 40, [(0, STAND, None), (3, CLASP, SNAP), (7, CLASP, None), (9, P(AIM, right_arm=[-110, 20, 0]), SNAP), (12, AIM, SETTLE), (26, AIM, None), (40, STAND, GLIDE)])
anim('supernova', 62, [(0, STAND, None), (4, CLASP, SETTLE), (16, P(CLASP, right_arm=[-120, 40, 0], left_arm=[-120, -40, 0]), BREATH), (24, AIM, SNAP), (40, AIM, None), (62, STAND, GLIDE)])
anim('blood_storm', 112, [(0, STAND, None), (6, CLASP, SETTLE), (20, RAISE, SNAP), (90, RAISE, None), (112, STAND, GLIDE)])
anim('crimson_execution', 64, [(0, STAND, None), (4, CLASP, SETTLE), (20, CLASP, None), (23, DASH, SNAP), (27, AIM, SNAP), (40, AIM, None), (64, STAND, GLIDE)])
anim('blood_apocalypse', 172, [(0, STAND, None), (8, RAISE, SETTLE)] + tremble(RAISE, 10, 100, 4, 2) + [(104, P(RAISE, torso=[-14, 0, 0]), SNAP), (110, AIM, SNAP), (130, AIM, None), (172, STAND, GLIDE)])
# ---- Shrine
anim('dismantle', 30, [(0, STAND, None), (2, SLASH_A, SNAP), (4, SLASH_B, SNAP), (12, SLASH_B, None), (30, STAND, GLIDE)])
ts = [(0, STAND, None)]
for k, t in enumerate(range(8, 60, 4)):
    ts.append((t, SLASH_A if k % 2 == 0 else SLASH_B, SNAP))
ts += [(70, STAND, GLIDE), (92, STAND, None)]
anim('thousand_slashes', 92, ts)
anim('cleave', 30, [(0, STAND, None), (2, DASH, SNAP), (5, P(SLASH_A, right_arm=[-90, 50, 0]), SNAP), (6, SLASH_B, SNAP), (14, SLASH_B, None), (30, STAND, GLIDE)])
anim('world_splitter', 102, [(0, STAND, None), (4, P(SLASH_A, right_arm=[-175, 20, 0]), SETTLE)] + tremble(P(SLASH_A, right_arm=[-175, 20, 0]), 6, 20)
     + [(22, P(SLASH_B, right_arm=[-20, -10, 0], torso=[30, -10, 0]), SNAP), (40, P(SLASH_B, right_arm=[-20, -10, 0], torso=[30, -10, 0]), None), (102, STAND, GLIDE)])
# ---- power
anim('meteor', 94, [(0, STAND, None), (4, CROUCH, SETTLE), (8, RAISE, SNAP), (26, RAISE, None), (30, P(CROUCH, pos=[0, 0, 0]), SETTLE), (36, DIVE, SNAP),
                    (45, DIVE, None), (46, GROUND_PUNCH, SNAP), (64, GROUND_PUNCH, None), (94, STAND, GLIDE)])
anim('ce_apocalypse', 112, [(0, STAND, None), (8, POWER, SETTLE)] + tremble(POWER, 10, 46, 3, 3) + [(48, P(POWER, right_arm=[-60, 0, 90], left_arm=[-60, 0, -90], head=[-30, 0, 0]), SNAP),
                                                                     (80, POWER, None), (112, STAND, GLIDE)])
# ---- domain
anim('domain', 122, [(0, STAND, None), (10, GUARD, SETTLE), (34, SIGN, SNAP), (90, SIGN, None), (122, STAND, GLIDE)])
anim('domain_finisher', 92, [(0, STAND, None)] + windup(2, 22) + [(24, DASH, SNAP)] + strike(38, PUNCH, 14) + [(92, STAND, GLIDE)])
db = [(0, STAND, None)]
for k, t in enumerate(range(6, 72, 3)):
    db.append((t, SLASH_A if k % 2 == 0 else SLASH_B, SNAP))
db += [(73, RAISE, SNAP), (74, P(HAMMER), SNAP), (90, HAMMER, None), (102, STAND, GLIDE)]
anim('domain_barrage', 102, db)
anim('overdrive', 62, [(0, STAND, None), (6, CROUCH, SETTLE)] + tremble(CROUCH, 8, 40, 2, 3) + [(42, POWER, SNAP), (54, POWER, None), (62, STAND, GLIDE)])
# ---- ultimates
anim('final_judgment', 234, [(0, STAND, None), (20, GUARD, SETTLE), (36, POWER, SETTLE), (40, WIND, SETTLE)] + tremble(WIND, 42, 112, 3, 2)
     + [(113, DASH, SNAP)] + strike(120, PUNCH, 20) + [(170, GUARD, GLIDE), (234, STAND, GLIDE)])
ap = [(0, STAND, None), (10, GUARD, SETTLE), (28, SIGN, SNAP), (44, SIGN, None), (56, CLASP, SETTLE), (60, RAISE, SNAP), (104, RAISE, None)]
for k, t in enumerate(range(110, 146, 3)):
    ap.append((t, SLASH_A if k % 2 == 0 else SLASH_B, SNAP))
ap += [(147, DASH, SNAP), (151, P(SLASH_A, right_arm=[-90, 50, 0]), SNAP), (152, SLASH_B, SNAP)] + windup(156, 174) + strike(176, PUNCH, 14) + [(190, RAISE, SNAP), (210, RAISE, None), (286, STAND, GLIDE)]
anim('domain_apocalypse', 286, ap)

# subtitles
names = ["charge", "black_flash", "black_flash_big", "dash", "blood_form", "blood_fire", "blood_hit", "blood_rain", "dismantle", "cleave",
         "world_splitter", "crater", "eruption", "domain", "domain_hum", "overdrive", "wave", "world_ender", "rumble", "impact", "meteor", "card"]
pretty = {"black_flash": "Black Flash", "black_flash_big": "Black Flash erupts", "world_ender": "The world ends", "card": "Technique"}
lang = {"subtitles.yuji." + n: pretty.get(n, n.replace("_", " ").capitalize()) for n in names}
os.makedirs(os.path.join(ROOT, "lang"), exist_ok=True)
with open(os.path.join(ROOT, "lang", "en_us.json"), "w") as f:
    json.dump(lang, f, indent=1)
