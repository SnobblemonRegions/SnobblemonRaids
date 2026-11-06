"""Small CobbleTowers textures drawn from the page's CSS (server pack, textures/gui/pages/cobbletowers/):
   pattern.png          1280x640  the diamond lattice behind every body (svg 32x32, gold, 7% alpha)
   gem_off/0/1/2.png    risk diamonds
   plinth_<tier>.png    reward plinth: sun rays + ring + glow (common/fine/rare/epic), 12u square = 153.6px shown, drawn at 2x
   medal.png            the vendor item medallion (dgoodpic: radial lapis, gold border), 69px shown, drawn at 4x
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = r'G:\Claude\SnobbleGUIOverhaul\Two-Mod Rework\snobblemon-server\src\main\resources\assets\snobblemon_regions\textures\gui\pages\cobbletowers'
U = 12.8


def hexc(h, a=255):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), a)


def pattern():
    W, H = 1280, 640
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = hexc('#ddab3c', round(.07 * 255))
    for ty in range(0, H, 32):
        for tx in range(0, W, 32):
            d.polygon([(tx + 16, ty), (tx + 32, ty + 16), (tx + 16, ty + 32), (tx, ty + 16)], outline=c)
    im.save(os.path.join(OUT, 'pattern.png'))


def gems():
    S = 8
    for name, col in (('off', '#2a3a66'), ('0', '#5fd68e'), ('1', '#f2c75c'), ('2', '#ff7a94')):
        n = 15 * S
        im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
        ImageDraw.Draw(im).polygon([(n / 2, 0), (n, n / 2), (n / 2, n), (0, n / 2)], fill=hexc(col))
        im.resize((30, 30), Image.LANCZOS).save(os.path.join(OUT, 'gem_%s.png' % name))


def radial(size, cx, cy, c0, c1, stop=.75):
    yy, xx = np.mgrid[0:size, 0:size].astype(float)
    r = np.sqrt((xx - cx * size) ** 2 + (yy - cy * size) ** 2) / (stop * size / 2 * 1.0)
    # farthest-corner like CSS is approximated by the radius to the far edge
    t = np.clip(r / (math.sqrt(2) * 0.5 * 2 / 2 * 1.0) if False else r / 1.0, 0, 1)
    out = np.zeros((size, size, 4))
    for i in range(3):
        out[..., i] = c0[i] + (c1[i] - c0[i]) * t
    out[..., 3] = 255
    return out


def disc(size, fill_fn, radius_frac=0.5):
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse([size / 2 - radius_frac * size, size / 2 - radius_frac * size, size / 2 + radius_frac * size, size / 2 + radius_frac * size], fill=255)
    return mask


def lapis_disc(px, S=4, cy=.32):
    n = px * S
    yy, xx = np.mgrid[0:n, 0:n].astype(float)
    cx0, cy0 = n * .5, n * cy
    far = max(math.hypot(cx0, cy0), math.hypot(n - cx0, cy0), math.hypot(cx0, n - cy0), math.hypot(n - cx0, n - cy0))   # farthest-corner
    t = np.clip(np.sqrt((xx - cx0) ** 2 + (yy - cy0) ** 2) / (far * .75), 0, 1)
    a, b = hexc('#2a52c0'), hexc('#17307c')
    arr = np.zeros((n, n, 4), dtype=np.uint8)
    for i in range(3):
        arr[..., i] = (a[i] + (b[i] - a[i]) * t).astype(np.uint8)
    arr[..., 3] = 255
    im = Image.fromarray(arr, 'RGBA')
    m = Image.new('L', (n, n), 0)
    ImageDraw.Draw(m).ellipse([0, 0, n - 1, n - 1], fill=255)
    im.putalpha(m)
    return im


def ring_layers(tier):
    """gold border + box-shadow rings of .dring, per tier"""
    gold = {'common': ('#ddab3c', '#6b4512', '#a8741e', (246, 220, 122, 71)),
            'fine': ('#ddab3c', '#6b4512', '#a8741e', (95, 214, 142, 102)),
            'rare': ('#b78cff', '#3b2a66', '#7f5fc0', (183, 140, 255, 128)),
            'epic': ('#f6dc7a', '#a8741e', '#f6dc7a', (246, 220, 122, 140))}[tier]
    return gold


def plinth(tier):
    S = 2
    P = 12 * U                                   # plinth box (153.6)
    n = int(round(P * S))
    img = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    cx = cy = n / 2
    # rays (not for common): repeating conic, masked radially 28% -> 72%
    if tier != 'common':
        step, on, col = {'fine': (20, 7, (246, 220, 122, 66)), 'rare': (20, 7, (183, 140, 255, 77)), 'epic': (15, 6, (255, 236, 170, 107))}[tier]
        yy, xx = np.mgrid[0:n, 0:n].astype(float)
        ang = (np.degrees(np.arctan2(xx - cx, -(yy - cy))) + 360) % 360
        ray = ((ang % step) < on).astype(float)
        rr = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / (n / 2)       # 1 at the edge of the box (closest-side)
        mask = np.clip(1 - (rr - .28) / (.72 - .28), 0, 1) * (rr <= 1)
        a = (ray * mask * col[3]).astype(np.uint8)
        layer = np.zeros((n, n, 4), dtype=np.uint8)
        layer[..., 0], layer[..., 1], layer[..., 2], layer[..., 3] = col[0], col[1], col[2], a
        img = Image.alpha_composite(img, Image.fromarray(layer, 'RGBA'))
    border, inner, outer, glow = ring_layers(tier)
    R = 9 * U * S / 2                            # ring radius (outer edge of border box)
    gl = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gl)
    spread = {'common': (.2, 2.2), 'fine': (.5, 2.6), 'rare': (.6, 3), 'epic': (1.0, 4)}[tier]
    gr = R + spread[0] * U * S
    gd.ellipse([cx - gr, cy - gr, cx + gr, cy + gr], fill=glow)
    gl = gl.filter(ImageFilter.GaussianBlur(spread[1] * U * S / 2))
    img = Image.alpha_composite(img, gl)
    d = ImageDraw.Draw(img)
    r2 = R + .7 * U * S
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=hexc(outer))                 # outer shadow ring (.7u)
    r1 = R + .25 * U * S
    d.ellipse([cx - r1, cy - r1, cx + r1, cy + r1], fill=hexc(inner))                 # inner ring (.25u)
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=hexc(border))                    # border
    rin = R - .5 * U * S
    disc_im = lapis_disc(int(round(rin * 2 / S)), 4)
    disc_im = disc_im.resize((int(round(rin * 2)), int(round(rin * 2))), Image.LANCZOS)
    img.alpha_composite(disc_im, (int(round(cx - rin)), int(round(cy - rin))))
    img.save(os.path.join(OUT, 'plinth_%s.png' % tier))


def medal():
    S = 4
    px = 5.4 * U                                  # 69.1
    n = int(round(px * S))
    base = lapis_disc(int(round(px)), S)
    base = base.resize((n, n), Image.LANCZOS)
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([0, 0, n - 1, n - 1], fill=hexc('#a8741e'))
    b = .25 * U * S
    inner = lapis_disc(int(round((n - 2 * b) / S)), S).resize((int(round(n - 2 * b)), int(round(n - 2 * b))), Image.LANCZOS)
    im.alpha_composite(inner, (int(round(b)), int(round(b))))
    im.resize((int(px * 2), int(px * 2)), Image.LANCZOS).save(os.path.join(OUT, 'medal.png'))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    pattern()
    gems()
    for t in ('common', 'fine', 'rare', 'epic'):
        plinth(t)
    medal()
    print('ok')
