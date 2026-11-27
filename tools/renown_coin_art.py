"""Draws the Renown Coin icons into src/main/resources (run from the repo root: python tools/renown_coin_art.py).

A Renown Coin is struck from a Daily Boss: 16 x 16, a shaded coin lit from the top left with the boss's primary type struck into its face.
18 type emblems x 2 rims (gold for a legendary boss, platinum for a mythical one) = 36 icons, plus a plain coin (renown_coin.png) for a
coin with no species, which only an operator can make. Every model is item/generated over its texture. Deterministic.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "cobbleraids"

RIMS = {
    # highlight, body, mid, shade, rim, struck (the recessed emblem), edge glint
    "gold": ((255, 242, 184), (240, 198, 92), (214, 160, 60), (160, 108, 36), (104, 66, 20), (126, 82, 26), (255, 255, 236)),
    "platinum": ((248, 250, 255), (210, 218, 232), (176, 186, 206), (128, 138, 162), (76, 84, 104), (98, 106, 128), (255, 255, 255)),
}
OUTLINE = (32, 22, 12)

# 7 x 7 emblems, '#' struck in, '+' a struck highlight (the lip of the cut catching light)
TYPES = {
    "normal":   [".......", "..###..", ".#...#.", ".#.+.#.", ".#...#.", "..###..", "......."],
    "fire":     ["...#...", "..##...", "..###..", ".##+##.", ".#####.", "..###..", "......."],
    "water":    ["...#...", "..###..", "..###..", ".##+##.", ".#####.", "..###..", "......."],
    "grass":    ["....##.", "...###.", "..###..", ".###+..", ".##....", "#......", "......."],
    "electric": ["...##..", "..##...", ".####..", "...##..", "..##...", ".##....", "......."],
    "ice":      ["...#...", ".#.#.#.", "..###..", "###+###", "..###..", ".#.#.#.", "...#..."],
    "fighting": [".......", ".####..", "######.", "######.", ".#####.", "..###..", "......."],
    "poison":   ["..###..", ".#####.", ".#+#+#.", ".#####.", "..#.#..", "..###..", "......."],
    "ground":   [".......", ".......", "...#...", "..###..", ".##+##.", "#######", "......."],
    "flying":   [".......", "#......", "##...#.", ".##.##.", "..###..", "...#...", "......."],
    "psychic":  ["..###..", ".#...#.", "#..#..#", "#.#+#.#", "#..#..#", ".#...#.", "..###.."],
    "bug":      [".#...#.", "..#.#..", ".#####.", "##+#+##", ".#####.", ".#.#.#.", "......."],
    "rock":     [".......", "..###..", ".####..", "##+###.", "######.", ".####..", "......."],
    "ghost":    ["..###..", ".#####.", "#+#+###", "#######", "#######", "#.#.#.#", "......."],
    "dragon":   ["#.....#", "##...##", ".##.##.", "..###..", ".##+##.", "..#.#..", "......."],
    "dark":     ["..###..", ".##....", "##.....", "##..+..", "##.....", ".##....", "..###.."],
    "steel":    ["..#.#..", ".#####.", "##...##", ".#.+.#.", "##...##", ".#####.", "..#.#.."],
    "fairy":    ["...#...", "...#...", ".#####.", "..#+#..", ".#...#.", "#.....#", "......."],
}


def coin(rim, emblem):
    hi, body, mid, shade, edge, struck, glint = RIMS[rim]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d > 6.9:
                continue
            light = ((cx - x) + (cy - y)) / 13.0
            if d > 5.9:                          # the milled rim: darker, lit along the top left
                c = mid if light > 0.15 else edge
            elif d > 5.0:                        # the raised border ring
                c = hi if light > 0.25 else body if light > -0.2 else shade
            else:                                # the field
                c = body if light > 0.05 else mid if light > -0.35 else shade
            px[x, y] = (*c, 255)
    if emblem:
        for j, row in enumerate(emblem):
            for i, ch in enumerate(row):
                x, y = 4 + i, 4 + j
                if ch == '#':
                    px[x, y] = (*struck, 255)
                elif ch == '+':
                    px[x, y] = (*hi, 255)
    # milled edge notches and a glint on the upper left of the border
    for a in range(0, 360, 30):
        x = round(cx + 6.4 * math.cos(math.radians(a)))
        y = round(cy + 6.4 * math.sin(math.radians(a)))
        if px[x, y][3]:
            px[x, y] = (*edge, 255)
    for x, y in ((4, 3), (3, 4), (5, 2)):
        px[x, y] = (*glint, 255)
    # outline
    src = img.copy().load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src[x + dx, y + dy][3]
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (*OUTLINE, 255)
    return img


def write(name, image):
    (ROOT / "textures" / "item").mkdir(parents=True, exist_ok=True)
    (ROOT / "models" / "item").mkdir(parents=True, exist_ok=True)
    image.save(ROOT / "textures" / "item" / f"{name}.png")
    model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"cobbleraids:item/{name}"}}
    (ROOT / "models" / "item" / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    write("renown_coin", coin("gold", None))
    for rim in RIMS:
        for kind, emblem in TYPES.items():
            write(f"renown_coin_{kind}_{rim}", coin(rim, emblem))
    print("wrote", 1 + len(RIMS) * len(TYPES), "Renown Coin icons")
