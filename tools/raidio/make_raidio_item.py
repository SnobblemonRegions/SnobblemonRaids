"""The Raidio item's model and texture, the Pocket Radio design: a chunky handheld set with a speaker grille, an orange tuning dial and a leaning antenna.

Writes, under src/main/resources/assets/cobbleraids:
  textures/item/raidio.png   a 32x32 atlas: the six faces of the case, then a strip of flat colours the other pieces take their colour from
  models/item/raidio.json    the case, a dark outline shell round it, a knob, and the antenna with its glowing tip

The model is a real 3D object for the hand; its front view is the 16x16 sprite from the design board, so the inventory icon matches it. The art is original.
Run from the repository root:  python tools/raidio/make_raidio_item.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/cobbleraids'
SIZE = 32

PALETTE = {
    'K': (20, 24, 31), 'T': (47, 182, 196), 't': (28, 135, 148), 'w': (200, 247, 255), 'D': (27, 42, 51), 'd': (52, 73, 90),
    'O': (255, 154, 60), 'o': (196, 102, 28), 'Y': (255, 217, 138), 'S': (195, 204, 214), 's': (123, 135, 148), 'R': (238, 243, 246),
}
# The strip of flat colours along the bottom of the atlas: colour letter -> its x, at y = STRIP_Y.
STRIP_Y = 20
STRIP = {letter: i for i, letter in enumerate('KTtwDdOoYSsR')}

# Face regions of the case on the atlas: (x, y, width, height).
FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM = (0, 0, 12, 9), (12, 0, 12, 9), (24, 0, 4, 9), (28, 0, 4, 9), (0, 10, 12, 4), (12, 10, 12, 4)
# Which side of the case faces the player. The back is the plain side. If the radio shows its back in the hand, flip this.
FRONT_FACE, BACK_FACE = 'south', 'north'


def paint():
    image = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = image.load()

    def put(x, y, letter): px[x, y] = PALETTE[letter] + (255,)

    def fill(region, letter):
        x0, y0, w, h = region
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w): put(x, y, letter)

    # the front: the speaker grille on the left, the dial on the right, a slot under it
    fill(FRONT, 'T')
    fx, fy, fw, fh = FRONT
    for x in range(fw): put(fx + x, fy, 'w')                      # light along the top edge
    for y in range(fh): put(fx, fy + y, 'w')                      # and down the left
    for x in range(1, fw): put(fx + x, fy + fh - 1, 't')          # shade along the bottom
    for y in range(1, fh): put(fx + fw - 1, fy + y, 't')          # and down the right
    for y in range(2, 7):
        for x in range(2, 7): put(fx + x, fy + y, 'D')            # the speaker
    for y in (3, 5):
        for x in (3, 5): put(fx + x, fy + y, 'd')                 # its grille
    for y in range(2, 5):
        for x in range(8, 11): put(fx + x, fy + y, 'O')           # the tuning dial
    put(fx + 8, fy + 2, 'Y'); put(fx + 10, fy + 4, 'o'); put(fx + 10, fy + 3, 'o')
    for x in range(8, 11): put(fx + x, fy + 6, 'D')               # a slot

    # the back: plain, with a battery cover
    fill(BACK, 't')
    bx, by, bw, bh = BACK
    for y in range(2, 7):
        for x in range(2, 10): put(bx + x, by + y, 'T')
    for x in range(2, 10): put(bx + x, by + 2, 'w')
    put(bx + 9, by + 6, 't')

    fill(LEFT, 'T'); fill(RIGHT, 't'); fill(TOP, 'w'); fill(BOTTOM, 'D')
    for y in range(LEFT[3]): put(LEFT[0], LEFT[1] + y, 'w')

    for letter, x in STRIP.items(): put(x, STRIP_Y, letter)
    return image


def uv(x0, y0, x1, y1):
    """Pixel coordinates on the atlas as the model wants them. Minecraft ignores a model's texture_size and always reads uv in a 0 to 16 space whatever the
    texture's resolution, so pixels are scaled by 16 / SIZE."""
    k = 16 / SIZE
    return [x0 * k, y0 * k, x1 * k, y1 * k]


def flat(letter):
    """The uv of a one-pixel swatch of a colour, so a whole face takes that colour."""
    x = STRIP[letter]
    return uv(x, STRIP_Y, x + 1, STRIP_Y + 1)


def region(r):
    x, y, w, h = r
    return uv(x, y, x + w, y + h)


def solid_faces(letter):
    return {face: {'uv': flat(letter), 'texture': '#all'} for face in ('north', 'south', 'east', 'west', 'up', 'down')}


def model():
    # Coordinates are in sixteenths of a block. The case is 12 wide, 9 tall and 4 deep, standing on y = 1.
    body_faces = {
        FRONT_FACE: {'uv': region(FRONT), 'texture': '#all'},
        BACK_FACE: {'uv': region(BACK), 'texture': '#all'},
        'west': {'uv': region(LEFT), 'texture': '#all'},
        'east': {'uv': region(RIGHT), 'texture': '#all'},
        'up': {'uv': region(TOP), 'texture': '#all'},
        'down': {'uv': region(BOTTOM), 'texture': '#all'},
    }
    lean = {'origin': [12, 10, 8], 'axis': 'z', 'angle': -22.5}
    elements = [
        # A dark shell, half a unit proud all round but tucked in at the front and back, so it shows only as the outline Minecraft items have.
        {'name': 'outline', 'from': [1.5, 0.5, 6.25], 'to': [14.5, 10.5, 9.75], 'faces': solid_faces('K')},
        {'name': 'case', 'from': [2, 1, 6], 'to': [14, 10, 10], 'faces': body_faces},
        {'name': 'knob', 'from': [5, 10, 7], 'to': [8, 11.5, 9], 'faces': solid_faces('s')},
        {'name': 'antenna outline', 'from': [11.25, 10, 7.6], 'to': [12.75, 14.75, 8.4], 'rotation': lean, 'faces': solid_faces('K')},
        {'name': 'antenna', 'from': [11.5, 10, 7.5], 'to': [12.5, 14.25, 8.5], 'rotation': lean, 'faces': solid_faces('S')},
        {'name': 'antenna tip', 'from': [11.1, 14.25, 7.1], 'to': [12.9, 16.05, 8.9], 'rotation': lean, 'faces': solid_faces('O')},
    ]
    for e in elements: e['shade'] = False
    return {
        'credit': 'Generated by tools/raidio/make_raidio_item.py',
        'textures': {'all': 'cobbleraids:item/raidio', 'particle': 'cobbleraids:item/raidio'},
        'elements': elements,
        'display': {
            'gui': {'rotation': [0, 0, 0], 'translation': [0, -0.5, 0], 'scale': [0.95, 0.95, 0.95]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
            'fixed': {'rotation': [0, 180, 0], 'translation': [0, -1, 0], 'scale': [0.85, 0.85, 0.85]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.5, 0.5, 0.5]},
            'thirdperson_lefthand': {'rotation': [75, 225, 0], 'translation': [0, 2.5, 0], 'scale': [0.5, 0.5, 0.5]},
            'firstperson_righthand': {'rotation': [6, -22, 0], 'translation': [-1.5, 3, 0], 'scale': [0.27, 0.27, 0.27]},
            'firstperson_lefthand': {'rotation': [6, 22, 0], 'translation': [1.5, 3, 0], 'scale': [0.27, 0.27, 0.27]},
        },
    }


def main():
    (ASSETS / 'textures/item').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'models/item').mkdir(parents=True, exist_ok=True)
    paint().save(ASSETS / 'textures/item/raidio.png')
    (ASSETS / 'models/item/raidio.json').write_text(json.dumps(model(), indent=1) + '\n', encoding='utf-8')
    print('wrote raidio.png and raidio.json')


if __name__ == '__main__':
    main()
