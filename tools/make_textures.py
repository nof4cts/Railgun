"""Paints the three katana sprites (64x64, diagonal like vanilla swords: pommel bottom-left, tip top-right)."""
import math, os, sys
from PIL import Image
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
N = 64
A = (7.0, 57.0)    # pommel end
B = (58.5, 5.5)    # tip
L = math.dist(A, B)
ux, uy = (B[0] - A[0]) / L, (B[1] - A[1]) / L
px, py = -uy, ux   # perpendicular (towards top-left is negative)

def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)

def paint(name, wrap, wrap2, guard, guard2, blade, edge, hamon, pommel, scabbard_glint=False, guard_shape='round', hamon_style='straight'):
    img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    pix = img.load()
    # lengths along the sword (in pixels)
    p_end = 2.0
    handle_end = 16.5
    guard_end = 19.0
    habaki_end = 21.0
    for y in range(N):
        for x in range(N):
            cx, cy = x + 0.5 - A[0], y + 0.5 - A[1]
            s = cx * ux + cy * uy          # along
            d = cx * px + cy * py          # across (signed)
            col = None
            if -0.5 <= s < p_end and abs(d) <= 2.3:
                col = rgb(pommel) if abs(d) < 1.8 else rgb(0x1a1410)
            elif p_end <= s < handle_end and abs(d) <= 2.2:
                # tsuka-ito: diamond wrap over same (white ray-skin) showing through
                k = (s - p_end) / 3.0
                diamond = abs((k % 1.0) - 0.5) * 4.4 - abs(d)
                if abs(d) > 1.6:
                    col = rgb(0x0e0b0a)
                elif diamond > 0.2:
                    col = rgb(wrap2)
                else:
                    col = rgb(wrap)
            elif handle_end <= s < guard_end:
                r = 5.6 if guard_shape == 'round' else 5.0
                if abs(d) <= r:
                    edge_px = abs(d) > r - 1.0 or s < handle_end + 0.6 or s > guard_end - 0.7
                    col = rgb(guard2) if edge_px else rgb(guard)
            elif guard_end <= s < habaki_end and abs(d) <= 1.9:
                col = rgb(0xE8C35A)
            elif habaki_end <= s <= L:
                tipk = (L - s) / 7.0
                half = 1.95 if tipk >= 1 else 1.95 * max(tipk, 0.0) ** 0.8
                # the edge side curves into the kissaki
                if -half - 0.25 <= d <= half:
                    if d < -half + 0.85:
                        col = rgb(edge)                         # the cutting edge, bright
                    else:
                        wave = 0.0
                        if hamon_style == 'wave':
                            wave = 0.45 * math.sin(s * 0.9)
                        elif hamon_style == 'flame':
                            wave = 0.5 * abs(math.sin(s * 0.55)) - 0.2
                        if d < -half + 1.75 + wave:
                            col = rgb(hamon)                    # the temper line
                        elif d > half - 0.6:
                            col = rgb(0x3a3f48)                 # the spine, dark
                        else:
                            k = (d + half) / (2 * half)
                            b = blade
                            col = tuple(int(c * (1.05 - 0.25 * k)) if i < 3 else 255 for i, c in enumerate(rgb(b)))
                            col = tuple(min(255, c) for c in col)
            if col:
                pix[x, y] = col
    img.save(os.path.join(OUT, name + '.png'))
    return img

paint('wado_ichimonji', wrap=0xF4F1EA, wrap2=0x2A2A2E, guard=0xE2B84A, guard2=0x8A6A1C, blade=0xA9B6C6, edge=0xFFFFFF,
      hamon=0xF7FBFF, pommel=0xD9B24A, hamon_style='straight')
paint('sandai_kitetsu', wrap=0x7A0E16, wrap2=0x120406, guard=0x2B2022, guard2=0x0A0607, blade=0x9AA4B4, edge=0xF3F7FF,
      hamon=0xE6DCE0, pommel=0x3A2A2C, guard_shape='square', hamon_style='flame')
paint('enma', wrap=0x5B2A9C, wrap2=0x150A24, guard=0xD8A63A, guard2=0x6E4E12, blade=0x9098B2, edge=0xF0F2FF,
      hamon=0xD6D0F0, pommel=0x6A3BB2, hamon_style='wave')
# a 4x preview sheet for the sneak peek
sheet = Image.new('RGBA', (N * 4 * 3 + 40, N * 4 + 20), (24, 22, 26, 255))
for i, n in enumerate(['wado_ichimonji', 'sandai_kitetsu', 'enma']):
    im = Image.open(os.path.join(OUT, n + '.png')).resize((N * 4, N * 4), Image.NEAREST)
    sheet.alpha_composite(im, (10 + i * (N * 4 + 10), 10))
sheet.save(sys.argv[2])
print('textures written')
