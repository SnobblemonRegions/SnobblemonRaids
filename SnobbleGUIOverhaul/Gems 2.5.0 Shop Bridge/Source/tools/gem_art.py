"""Builds the Version 2 Gem artwork from the user's reference (gem_icon.png).

The reference is a smooth upscale, not a real pixel grid, so it is re-drawn as true pixel art:
crop -> square -> area-downsample to a 64x64 master -> hard alpha -> limited palette (median cut, no dithering) -> 32x32 / 16x16 derived the same way.
"""
import sys, os
from PIL import Image

REF = None
OUT = None

def load(ref=None):
    im = Image.open(ref or REF).convert('RGBA')
    bb = im.getchannel('A').point(lambda a: 255 if a > 40 else 0).getbbox()
    c = im.crop(bb)
    s = max(c.size)
    sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    sq.paste(c, ((s - c.width) // 2, (s - c.height) // 2))
    return sq

def pixelate(img, size, colors, alpha_cut=110):
    small = img.resize((size, size), Image.BOX)
    r, g, b, a = small.split()
    a = a.point(lambda v: 255 if v >= alpha_cut else 0)
    rgb = Image.merge('RGB', (r, g, b))
    # colours at semi-transparent edges are darkened by the premultiplied background: re-pull them from the opaque neighbour interior
    q = rgb.quantize(colors=colors, method=Image.MEDIANCUT, dither=Image.NONE).convert('RGB')
    out = Image.merge('RGBA', (*q.split(), a))
    return out

from PIL import ImageDraw

def hx(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

INK = hx('#1d0b4a'); PINK_L = hx('#ffd6ff'); PINK = hx('#f27ae6'); MAG = hx('#c243e0'); PURP = hx('#7b3bea'); DEEP = hx('#4b25b8')
CY_L = hx('#b4f7ff'); CY = hx('#3ddcff'); BLUE = hx('#3a7bff'); WHITE = hx('#ffffff')

def gem16():
    """Hand-drawn 16x16 isometric-cube gem: same silhouette, colours and white facet edges as the reference."""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    P = lambda pts, c: d.polygon(pts, fill=c)
    # outer hexagon in the darkest tone (becomes the outline)
    P([(8, 0), (15, 4), (15, 11), (8, 15), (0, 11), (0, 4)], INK)
    # top face: four triangles around its centre (8,4)
    P([(8, 1), (8, 4), (2, 4)], PINK_L)
    P([(8, 1), (14, 4), (8, 4)], CY_L)
    P([(2, 4), (8, 4), (8, 7)], PINK)
    P([(14, 4), (8, 4), (8, 7)], CY)
    # left face
    P([(1, 5), (7, 8), (7, 10), (1, 8)], MAG)
    P([(1, 8), (7, 10), (7, 13), (1, 10)], PURP)
    # right face
    P([(14, 5), (9, 8), (9, 10), (14, 8)], CY)
    P([(14, 8), (9, 10), (9, 13), (14, 10)], BLUE)
    # bottom tip
    P([(7, 13), (9, 13), (8, 14)], DEEP)
    # white facet edges
    for a, b in (((2, 4), (8, 7)), ((14, 4), (8, 7)), ((8, 7), (8, 13)), ((2, 4), (8, 1)), ((14, 4), (8, 1))):
        d.line([a, b], fill=WHITE)
    for x, y in ((11, 2), (10, 3), (12, 3), (11, 4), (11, 3)):
        im.putpixel((x, y), WHITE)
    im.putpixel((3, 7), WHITE); im.putpixel((4, 11), PINK_L)
    return im

ROWS16 = [
    ".......oo.......",
    ".....oAABBo.....",
    "...oAAAABBBBo...",
    ".oAAAAAABBBBBBo.",
    "oWppppppccccccWo",
    "ommWppppccccWyyo",
    "ommmmWppccWyyyyo",
    "ommmmmmmWyyyyyyo",
    "ommmmmmmWyyyyyyo",
    "ouuuuuuuWbbbbbbo",
    "ouuuuuuuWbbbbbbo",
    "ouuuuuuuWbbbbbbo",
    ".ouuuuuuWbbbbbo.",
    "...oddddWdddo...",
    ".....oddWdo.....",
    ".......oo.......",
]
PAL16 = {'o': INK, 'A': PINK_L, 'B': CY_L, 'p': PINK, 'c': CY, 'm': MAG, 'y': CY, 'u': PURP, 'b': BLUE, 'd': DEEP, 'W': WHITE}

def gem16():
    """Hand-placed 16x16 isometric-cube gem: the reference's hexagon, pink left / cyan right / purple base, white facet edges, sparkle."""
    assert all(len(r) == 16 for r in ROWS16) and len(ROWS16) == 16
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ROWS16):
        for x, ch in enumerate(row):
            if ch != '.':
                im.putpixel((x, y), PAL16[ch])
    for x, y in ((11, 2), (10, 3), (11, 3), (12, 3), (11, 4)):
        im.putpixel((x, y), WHITE)
    im.putpixel((3, 7), PINK_L); im.putpixel((4, 11), MAG)
    return im

def hex_clean(im):
    """Hard alpha only."""
    r, g, b, a = im.split()
    return Image.merge('RGBA', (r, g, b, a.point(lambda v: 255 if v >= 128 else 0)))

def sample_small(master, size, sat=1.3, cont=1.1):
    """Crisp small version: per block keep the most vivid pixel (so white facet edges and sparkles survive), then a dark 1px outline."""
    from PIL import ImageEnhance
    f = 64 // size
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    rgb = Image.merge('RGB', master.split()[:3])
    rgb = ImageEnhance.Contrast(ImageEnhance.Color(rgb).enhance(sat)).enhance(cont)
    for y in range(size):
        for x in range(size):
            best, bs, alpha = None, -1, 0
            for dy in range(f):
                for dx in range(f):
                    X, Y = x * f + dx, y * f + dy
                    alpha += master.getpixel((X, Y))[3]
                    r, g, b = rgb.getpixel((X, Y))
                    score = (max(r, g, b) - min(r, g, b)) + min(r, g, b) * 0.4
                    if score > bs:
                        bs, best = score, (r, g, b)
            if alpha / (f * f) >= 128:
                out.putpixel((x, y), best + (255,))
    src = out.copy()
    for y in range(size):
        for x in range(size):
            if src.getpixel((x, y))[3] == 0:
                continue
            if any(not (0 <= x + dx < size and 0 <= y + dy < size) or src.getpixel((x + dx, y + dy))[3] == 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                r, g, b, _ = src.getpixel((x, y))
                out.putpixel((x, y), (int(r * .3), int(g * .2), int(b * .5), 255))
    return out

if __name__ == '__main__':
    REF, OUT = sys.argv[1], sys.argv[2]
    os.makedirs(OUT, exist_ok=True)
    ref = load()
    for size, colors in ((64, 40), (32, 28)):
        pixelate(ref, size, colors).save(os.path.join(OUT, f'gem_{size}.png'))
    gem16().save(os.path.join(OUT, 'gem_16.png'))
    # preview sheet at 8x
    sheet = Image.new('RGBA', (64 * 8 + 32 * 8 + 16 * 8 + 40, 64 * 8 + 20), (14, 22, 40, 255))
    x = 10
    for size in (64, 32, 16):
        im = Image.open(os.path.join(OUT, f'gem_{size}.png')).resize((size * 8, size * 8), Image.NEAREST)
        sheet.alpha_composite(im, (x, 10)); x += size * 8 + 10
    sheet.save(os.path.join(OUT, 'gem_preview.png'))
