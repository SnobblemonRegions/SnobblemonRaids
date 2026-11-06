"""Bakes the static art of the AscensionLib capture reveal card (capture.css) into pack textures, at 2x:
   cap_portrait_<0..5>.png  the portrait window: radial bg, grid, plate, rarity aura + six facets + pixel dots, 2px line border, corner brackets
   cap_back_<1..3>.png      the card back (A NEW BOND AWAITS): diagonal lines over a radial gradient; 3 heights (1, 2, 3+ traits)
   cap_crest.png            the back's rotated crest with the ascension_core icon
   ascension_dex.png        the footer icon (copied)
The text, sprite, buttons and the flip/bloom/shockwave motion are native nodes, see gen_ui_asc.capreveal and UiScreen (capfx, capa/capb)."""
import math, os, shutil
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions', 'textures', 'gui', 'pages', 'ascensionlib')
ICONS = os.path.join(HERE, '..', '..', 'webgui_pages_src', 'extracted', 'webgui', 'web', 'ascensionlib', 'icons')
S = 2                                                   # texture pixels per design pixel
SCR, HEAD, LINE, ACC = (7, 19, 28), (10, 27, 39), (31, 107, 134), (74, 216, 246)
COLORS = ['#c1c5cf', '#1bd7e8', '#38ccef', '#af69ff', '#ffd437', '#ff3448']
SEC = ['#bdf1ff', '#9affd5', '#a9eaff', '#65ecff', '#fff3b3', '#bd8bff']
AURA = [.18, .32, .46, .62, .76, .88]
RAD = [3, 5, 8, 11, 13, 16]


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def over(dst, rgbv, alpha):
    """composite a colour with a per-pixel alpha (H,W) onto dst (H,W,3) float"""
    a = alpha[..., None]
    return dst * (1 - a) + np.array(rgbv, float) * a


def portrait(i):
    W, H = 384 * S, 164 * S
    y, x = (np.mgrid[0:H, 0:W] + .5) / S                # design coordinates
    # radial-gradient(circle at 50% 70%, #11405a, var(--scr) 72%)
    cx, cy = 192, .7 * 164
    r = np.hypot(x - cx, y - cy) / math.hypot(192, cy)
    t = np.clip(r / .72, 0, 1)[..., None]
    img = np.array((17, 64, 90), float) * (1 - t) + np.array(SCR, float) * t
    # the 24px grid (1px lines on the padding box origin 2,2)
    lx = ((x - 2) % 24) < 1
    ly = ((y - 2) % 24) < 1
    g = (lx | ly).astype(float) * .1
    img = over(img, (40, 99, 120), g)
    col, sec = rgb(COLORS[i]), rgb(SEC[i])
    # .capture-aura 224x164 centred; radial-gradient(ellipse at 50% 57%, rarity, soft 24%, transparent 67%), opacity = strength
    ax, ay = 192, 82
    ex, ey = (x - ax) / (112 * math.sqrt(2)), (y - .57 * 164) / (.57 * 164 * math.sqrt(2))
    rr = np.hypot(ex, ey)
    a = np.interp(rr, [0, .24, .67, 10], [1, .5, 0, 0])
    inside = (np.abs(x - ax) <= 112) & (np.abs(y - ay) <= 82)
    img = over(img, col, a * AURA[i] * inside)
    # .capture-facets: inset 28px 35px 22px (padding box), six 52x90 petals rotated about (50%, 85%) of themselves, opacity = strength
    fcx, fcy = 2 + 35 + (380 - 70) / 2, 2 + 28 + (160 - 50) / 2
    soft, soft2 = .5, .18
    for k in range(6):
        ang = math.radians(k * 60)
        # element local frame: element top-left at container centre, then translate(-50%,-91%), then rotate about origin (50%, 85%)
        ox, oy = fcx - 26, fcy - .91 * 90                # element top-left after the translate
        px, py = ox + 26, oy + .85 * 90                  # rotation origin in page coords
        dx, dy = x - px, y - py
        c, s = math.cos(-ang), math.sin(-ang)            # inverse rotation
        lxp = dx * c - dy * s + 26
        lyp = dx * s + dy * c + .85 * 90
        # polygon (50% 0, 100% 36%, 65% 100%, 0 70%) of 52x90
        poly = [(26, 0), (52, .36 * 90), (.65 * 52, 90), (0, .7 * 90)]
        mask = np.zeros_like(lxp, bool)
        inpoly = np.ones_like(lxp, bool)
        for (x1, y1), (x2, y2) in zip(poly, poly[1:] + poly[:1]):
            inpoly &= ((x2 - x1) * (lyp - y1) - (y2 - y1) * (lxp - x1)) >= 0
        mask = inpoly | (np.zeros_like(inpoly))
        # linear-gradient(125deg, transparent, soft 49%, secondary-soft 51%, transparent 87%) over 52x90
        th = math.radians(125)
        dxv, dyv = math.sin(th), -math.cos(th)
        L = abs(52 * dxv) + abs(90 * dyv)
        p = ((lxp - 26) * dxv + (lyp - 45) * dyv) / L + .5
        pa1 = np.interp(p, [0, .49, .51, .87, 1], [0, soft, 0, 0, 0])
        pa2 = np.interp(p, [0, .49, .51, .87, 1], [0, 0, soft2, 0, 0])
        for cc, pa in ((col, pa1), (sec, pa2)):
            img = over(img, cc, pa * mask * AURA[i])
    # .capture-pixels: 4x4 dots (left 25%, top 35% + shadows), opacity = strength
    bx, by = 2 + .25 * 380, 2 + .35 * 160
    for ddx, ddy, cc in ((0, 0, col), (15, -24, col), (-12, 69, col), (150, -8, sec), (140, 79, col), (98, -33, col), (32, 125, sec)):
        m = (x >= bx + ddx) & (x < bx + ddx + 4) & (y >= by + ddy) & (y < by + ddy + 4)
        img = over(img, cc, m * AURA[i])
    # ::after plate: bottom 29px, 50% wide, 8px high, rgba(74,216,246,.16) + 4px ring at .07
    pl, pr, pt, pb = 2 + 95, 2 + 285, 2 + 160 - 29 - 8, 2 + 160 - 29
    ring = (x >= pl - 4) & (x < pr + 4) & (y >= pt - 4) & (y < pb + 4)
    plate = (x >= pl) & (x < pr) & (y >= pt) & (y < pb)
    img = over(img, ACC, ring * .07)
    img = over(img, ACC, plate * .16)
    # corner brackets (12x12, 2px accent) 8px in from the padding box
    for (cx0, cy0, sx, sy) in ((2 + 8, 2 + 8, 1, 1), (2 + 380 - 8 - 12, 2 + 8, -1, 1), (2 + 8, 2 + 160 - 8 - 12, 1, -1), (2 + 380 - 8 - 12, 2 + 160 - 8 - 12, -1, -1)):
        hx = (x >= cx0) & (x < cx0 + 12)
        vy = (y >= cy0) & (y < cy0 + 12)
        ytop = (y >= cy0) & (y < cy0 + 2) if sy == 1 else (y >= cy0 + 10) & (y < cy0 + 12)
        xside = (x >= cx0) & (x < cx0 + 2) if sx == 1 else (x >= cx0 + 10) & (x < cx0 + 12)
        m = (hx & ytop) | (vy & xside)
        img = over(img, ACC, m.astype(float))
    # 2px line border
    edge = (x < 2) | (x >= 382) | (y < 2) | (y >= 162)
    img = over(img, LINE, edge.astype(float))
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')


def back(traits_h):
    inner_h = {1: 545, 2: 596, 3: 615}[traits_h]
    W, H = 408 * S, inner_h * S
    y, x = (np.mgrid[0:H, 0:W] + .5) / S
    # radial-gradient(ellipse, #123346, scr 75%): farthest-corner ellipse at the centre
    r = np.hypot((x - 204) / (204 * math.sqrt(2)), (y - inner_h / 2) / (inner_h / 2 * math.sqrt(2)))
    t = np.clip(r / .75, 0, 1)[..., None]
    img = np.array((18, 51, 70), float) * (1 - t) + np.array(SCR, float) * t
    # repeating-linear-gradient(45deg, transparent 0 20px, rgba(31,107,134,.1) 20px 21px)
    s = x * math.sqrt(.5) + (inner_h - y) * math.sqrt(.5)
    edge = (x < 2) | (x >= 406) | (y < 2) | (y >= inner_h - 2)
    img = over(img, LINE, edge.astype(float))
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGB')


def crest():
    N = 4                                                # supersample
    size = 168
    im = Image.new('RGBA', (size * S * N, size * S * N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = size * S * N / 2

    def sq(half, color):
        h = half * S * N
        pts = [(c + dx * h, c + dy * h) for dx, dy in ((0, -1.41421356), (1.41421356, 0), (0, 1.41421356), (-1.41421356, 0))]
        d.polygon(pts, fill=color)
    sq(59, LINE + (255,))          # box-shadow 0 0 0 9px line   (100/2 + 9)
    sq(57, SCR + (255,))           # box-shadow 0 0 0 7px scr    (100/2 + 7)
    sq(50, ACC + (255,))           # the 3px accent border
    sq(47, HEAD + (255,))          # background: var(--head)
    im = im.resize((size * S, size * S), Image.LANCZOS)
    icon = Image.open(os.path.join(ICONS, 'ascension_core.png')).convert('RGBA').resize((70 * S, 70 * S), Image.NEAREST)
    im.alpha_composite(icon, (size * S // 2 - 35 * S, size * S // 2 - 35 * S))
    return im


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for i in range(6):
        portrait(i).save(os.path.join(OUT, 'cap_portrait_%d.png' % i))
    for n in (1, 2, 3):
        back(n).save(os.path.join(OUT, 'cap_back_%d.png' % n))
    crest().save(os.path.join(OUT, 'cap_crest.png'))
    shutil.copy(os.path.join(ICONS, 'ascension_dex.png'), os.path.join(OUT, 'ascension_dex.png'))
    print('wrote capture art to', os.path.abspath(OUT))
