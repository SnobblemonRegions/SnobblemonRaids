"""AscensionLib's tablet bezel (ui.js A.setBezel): 8 nested octagon outlines on a 24x24 grid, one per colour, used as
border-image: <svg> 8 / 18px stretch with image-rendering: pixelated. Rendered here per rarity, scaled x2.25 nearest-neighbour
(8 source px -> 18 display px) and written as GUI nine-slice sprites (border 18) for the server pack:
  assets/snobblemon_regions/textures/gui/sprites/ascensionlib/bezel_<rarity>.png (+ .mcmeta)
"""
import json, os
from PIL import Image, ImageDraw

OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets',
                                   'snobblemon_regions', 'textures', 'gui', 'sprites', 'ascensionlib'))
SHELLS = {'common': ['#6c7585', '#aeb6c4', '#3f4755'], 'uncommon': ['#14877a', '#4fd3c1', '#0a4a42'], 'rare': ['#1d6db0', '#5bb7ee', '#0f3f6e'],
          'epic': ['#6a35b8', '#af69ff', '#341a66'], 'legendary': ['#b88a12', '#ffd437', '#6b4f08'], 'mythical': ['#8e1426', '#d0404f', '#620512']}


def bezel(c):
    cols = ['#1b120d', c[1], c[0], c[0], c[2], '#1b120d', '#cbd3df', '#eef2f7']
    im = Image.new('RGBA', (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i, col in enumerate(cols):
        k = max(0, 3 - i)
        lo, hi = i, 23 - i                       # the stroke runs along pixel row/column i (path at i + .5, 1px wide)
        pts = [(lo + k, lo), (hi - k, lo), (hi, lo + k), (hi, hi - k), (hi - k, hi), (lo + k, hi), (lo, hi - k), (lo, lo + k), (lo + k, lo)]
        d.line(pts, fill=col, width=1)
    return im.resize((54, 54), Image.NEAREST)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, c in list(SHELLS.items()) + [('none', SHELLS['mythical'])]:
        png = os.path.join(OUT, 'bezel_%s.png' % name)
        bezel(c).save(png)
        json.dump({'gui': {'scaling': {'type': 'nine_slice', 'width': 54, 'height': 54, 'border': 18}}}, open(png + '.mcmeta', 'w'))
    print('bezels written to', OUT)


if __name__ == '__main__':
    main()
