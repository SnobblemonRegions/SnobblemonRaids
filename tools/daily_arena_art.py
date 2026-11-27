"""Draws the Daily Boss arena art into src/main/resources (run from the repo root: python tools/daily_arena_art.py).

1. The Half Veil panel: assets/cobbleraids/textures/item/daily_veil.png, an animated amber heat-haze 32 x 176 px (8 px a block over a
   22-block panel), eight frames that interpolate. The haze is densest at the foot and fades out by the top; slow bright columns rise
   through it, and a bronze foot band with gold rivets and a raised bronze rail at 3 blocks hold it to the
   ground. Every pattern repeats exactly across the 32 px width, so neighbouring panels join without a seam. Its model
   (models/item/daily_veil.json) is the dome's two-sided quad: the veil reads the same from inside and outside the zone.
2. The renowned boss bar: vanilla's WHITE boss bar sprites (assets/minecraft/textures/gui/sprites/boss_bar/white_*.png), redrawn
   as a burnished gold bar with a renown crest. Only the Daily Boss uses WHITE (AscensionLib reskins PINK, SpookySurge YELLOW and BLUE; vanilla uses PINK, PURPLE and RED). The 20-notch overlay is
   AscensionLib's (dark ticks), drawn over this unchanged.

Deterministic: the same pixels on every run.
"""
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets"

# ---- palette (renown bronze and gold) ----
BRONZE_DEEP = (46, 34, 22)
BRONZE_DARK = (74, 56, 36)
BRONZE = (138, 104, 62)
BRONZE_LIGHT = (184, 142, 86)
GOLD = (232, 184, 90)
GOLD_HOT = (255, 226, 150)
HAZE_FOOT = (255, 150, 62)
HAZE_TOP = (255, 214, 150)

VEIL_W, BLOCK, BLOCKS, FRAMES = 32, 8, 22, 8
VEIL_H = BLOCK * BLOCKS


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(round(v))))


def veil_frame(f):
    img = Image.new("RGBA", (VEIL_W, VEIL_H), (0, 0, 0, 0))
    px = img.load()
    phase = f / FRAMES
    for y in range(VEIL_H):
        b = (VEIL_H - 1 - y + 0.5) / BLOCK          # height above the foot, in blocks
        h = b / BLOCKS                                # 0 at the foot, 1 at the top
        for x in range(VEIL_W):
            u = x / VEIL_W
            # two rising column waves, both periodic in x over the panel width, moving up one cycle per loop
            w1 = 0.5 + 0.5 * math.sin(2 * math.pi * (2 * u + b / 7.0 - phase))
            w2 = 0.5 + 0.5 * math.sin(2 * math.pi * (3 * u - b / 11.0 - phase + 0.37))
            shimmer = 0.62 * w1 + 0.38 * w2
            # fine heat ripple: a faint horizontal wobble band
            ripple = 0.5 + 0.5 * math.sin(2 * math.pi * (b / 1.6 - 2 * phase + 0.5 * math.sin(2 * math.pi * u)))
            base = 210 * (1 - h) ** 1.5
            alpha = base * (0.86 + 0.16 * shimmer) * (0.93 + 0.07 * ripple)
            colour = lerp(HAZE_FOOT, HAZE_TOP, min(1.0, h * 1.3))
            colour = lerp(colour, GOLD_HOT, 0.18 * shimmer * (1 - h))
            px[x, y] = (*colour, clamp(alpha))
    # the foot band: 6 px of dark bronze, lit along its top edge, shaded along the bottom, with gold rivets every 8 px
    foot_top = VEIL_H - 6
    for y in range(foot_top, VEIL_H):
        row = y - foot_top
        tone = [BRONZE_LIGHT, BRONZE, BRONZE_DARK, BRONZE_DARK, BRONZE_DEEP, BRONZE_DEEP][row]
        for x in range(VEIL_W):
            grain = 10 if (x * 7 + row * 3) % 9 == 0 else 0
            px[x, y] = (clamp(tone[0] + grain), clamp(tone[1] + grain), clamp(tone[2] + grain // 2), 240)
    for x0 in range(3, VEIL_W, 8):
        for (dx, dy, c) in ((0, 0, GOLD_HOT), (1, 0, GOLD), (0, 1, GOLD), (1, 1, BRONZE_LIGHT)):
            px[x0 + dx, foot_top + 2 + dy] = (*c, 250)
    px_line = foot_top - 1                              # a gold hairline where the haze meets the band
    for x in range(VEIL_W):
        r, g, bb, aa = px[x, px_line]
        px[x, px_line] = (*lerp((r, g, bb), GOLD, 0.8), max(aa, 220))
    # the rail at 3 blocks: a 2 px bronze strip with a gold top edge, half see-through so the haze still reads behind it
    rail = VEIL_H - 3 * BLOCK
    for x in range(VEIL_W):
        px[x, rail - 1] = (*GOLD, 200)
        px[x, rail] = (*BRONZE_LIGHT, 190)
        px[x, rail + 1] = (*BRONZE_DARK, 170)
    return img


def write_veil():
    sheet = Image.new("RGBA", (VEIL_W, VEIL_H * FRAMES))
    for f in range(FRAMES):
        sheet.paste(veil_frame(f), (0, f * VEIL_H))
    tex = ROOT / "cobbleraids" / "textures" / "item" / "daily_veil.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(tex)
    meta = {"animation": {"frametime": 4, "interpolate": True, "width": VEIL_W, "height": VEIL_H}}
    (tex.parent / "daily_veil.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    face = {"uv": [0, 0, 16, 16], "texture": "#panel"}
    model = {
        "textures": {"panel": "cobbleraids:item/daily_veil", "particle": "cobbleraids:item/daily_veil"},
        "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "faces": {"north": face, "south": face}}],
    }
    mpath = ROOT / "cobbleraids" / "models" / "item" / "daily_veil.json"
    mpath.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")


# ---- the boss bar ----

BAR_W, BAR_H = 182, 5
FILL = [(255, 240, 190), (240, 198, 100), (214, 160, 62), (168, 116, 42), (102, 68, 24)]
EMPTY = [(14, 10, 6, 245), (50, 37, 22, 238), (30, 22, 13, 238), (24, 17, 10, 238), (14, 10, 6, 245)]
CREST = ["..#..",
         ".#o#.",
         "#ooo#",
         ".#o#.",
         "..#.."]


def crest(img, light, dark, x0=2):
    px = img.load()
    for y, row in enumerate(CREST):
        for x, ch in enumerate(row):
            if ch == '#':
                px[x0 + x, y] = light
            elif ch == 'o':
                px[x0 + x, y] = dark


def bar_background():
    img = Image.new("RGBA", (BAR_W, BAR_H))
    px = img.load()
    for y in range(BAR_H):
        for x in range(BAR_W):
            r, g, b, a = EMPTY[y]
            if 1 <= y <= 3 and (x * 5 + y * 11) % 41 == 0:       # faint embers asleep in the empty part
                r, g, b = r + 30, g + 20, b + 8
            px[x, y] = (clamp(r), clamp(g), clamp(b), a)
    crest(img, (96, 72, 40, 255), (14, 10, 6, 255))
    return img


def bar_progress():
    img = Image.new("RGBA", (BAR_W, BAR_H))
    px = img.load()
    for y in range(BAR_H):
        for x in range(BAR_W):
            px[x, y] = (*FILL[y], 255)
    for start in range(12, BAR_W, 29):                          # burnished glints: short bright slashes leaning right
        for j in range(4):
            x, y = start + j, 3 - j
            if 0 <= x < BAR_W and 1 <= y <= 3:
                r, g, b, _ = px[x, y]
                px[x, y] = (*lerp((r, g, b), (255, 250, 225), 0.7), 255)
    for y in range(BAR_H):                                       # rounded tip so a nearly empty bar still ends cleanly
        r, g, b, _ = px[BAR_W - 1, y]
        px[BAR_W - 1, y] = (r // 2, g // 2, b // 2, 255)
    crest(img, (255, 246, 214, 255), (128, 84, 28, 255))
    return img


def write_bar():
    out = ROOT / "minecraft" / "textures" / "gui" / "sprites" / "boss_bar"
    out.mkdir(parents=True, exist_ok=True)
    bar_background().save(out / "white_background.png")
    bar_progress().save(out / "white_progress.png")


if __name__ == "__main__":
    write_veil()
    write_bar()
    print("wrote daily_veil (texture, mcmeta, model) and the white boss bar sprites")
