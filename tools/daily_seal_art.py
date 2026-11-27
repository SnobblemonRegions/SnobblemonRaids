"""Draws the Daily Boss seal panels into src/main/resources (run from the repo root: python tools/daily_seal_art.py).

The seal lies flat on the ground under the boss as two full-bright display panels (see WorldBossSeal): an OUTER panel 62 blocks across with
the two rim rings, twelve rune marks and six spokes, which turns with the fight, and an INNER panel 28 blocks across with the inner ring, a
ring of six small marks and the core, which turns the other way. Both are drawn as rows of soft glowing dots, so the seal keeps the look of
a particle sigil but stays crisp and bright at any distance and by night. Three colourings: bronze while players gather (unlit), gold once the
fight opens, ember red when the boss burns low.

Layout numbers match DailySeal (radius 30, rims at 30 and 27.6, runes at 28.8, inner ring 12, core 4, spokes 12.6 to 26.8).
Deterministic: the same pixels on every run.
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "cobbleraids"
SS = 3   # supersampling for clean round dots

PALETTES = {
    "bronze": {"dot": (196, 150, 88), "hot": (226, 188, 124), "halo": (140, 100, 54), "line": (150, 110, 62)},
    "gold": {"dot": (255, 222, 140), "hot": (255, 249, 224), "halo": (240, 170, 64), "line": (232, 178, 82)},
    "ember": {"dot": (255, 160, 96), "hot": (255, 236, 196), "halo": (226, 78, 40), "line": (236, 104, 52)},
}

OUTER_BLOCKS, OUTER_PX = 62.0, 512
INNER_BLOCKS, INNER_PX = 28.0, 256


class Canvas:
    def __init__(self, px, blocks, pal):
        self.px, self.blocks, self.pal = px, blocks, pal
        self.scale = px * SS / blocks
        self.c = px * SS / 2
        self.dots = Image.new("RGBA", (px * SS, px * SS), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (px * SS, px * SS), (0, 0, 0, 0))
        self.lines = Image.new("RGBA", (px * SS, px * SS), (0, 0, 0, 0))
        self.d, self.g, self.l = ImageDraw.Draw(self.dots), ImageDraw.Draw(self.glow), ImageDraw.Draw(self.lines)

    def at(self, x, z):
        return self.c + x * self.scale, self.c - z * self.scale

    def dot(self, x, z, r_blocks, hot=False):
        cx, cy = self.at(x, z)
        r = r_blocks * self.scale
        col = self.pal["hot"] if hot else self.pal["dot"]
        self.d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(*col, 255))
        rr = r * 2.6
        self.g.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(*self.pal["halo"], 150))

    def ring_line(self, r_blocks, width_blocks, alpha):
        cx, cy = self.at(0, 0)
        r = r_blocks * self.scale
        self.l.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(*self.pal["line"], alpha), width=max(1, int(width_blocks * self.scale)))

    def dotted_ring(self, r_blocks, spacing, dot_r, hot_every=0):
        n = max(12, round(2 * math.pi * r_blocks / spacing))
        for i in range(n):
            a = 2 * math.pi * i / n
            self.dot(r_blocks * math.sin(a), r_blocks * math.cos(a), dot_r, hot=bool(hot_every) and i % hot_every == 0)

    def diamond(self, cx, cz, a, size, dot_r):
        """A rune mark: a diamond of dots set crosswise to the radius at angle a, with a bright centre."""
        tx, tz = math.cos(a), -math.sin(a)
        rx, rz = math.sin(a), math.cos(a)
        for t in range(-3, 4):
            w = 3 - abs(t)
            for s in ((-w, w) if w else (0,)):
                self.dot(cx + tx * t * 0.32 * size + rx * s * 0.25 * size, cz + tz * t * 0.32 * size + rz * s * 0.25 * size, dot_r)
        self.dot(cx, cz, dot_r * 1.5, hot=True)

    def save(self, path):
        glow = self.glow.filter(ImageFilter.GaussianBlur(self.scale * 0.35))
        out = Image.alpha_composite(Image.alpha_composite(self.lines.filter(ImageFilter.GaussianBlur(self.scale * 0.08)), glow), self.dots)
        out = out.resize((self.px, self.px), Image.LANCZOS)
        out.save(path)


def outer(pal):
    c = Canvas(OUTER_PX, OUTER_BLOCKS, pal)
    c.ring_line(30.0, 0.18, 120)
    c.ring_line(27.6, 0.12, 90)
    c.dotted_ring(30.0, 0.72, 0.26, hot_every=6)
    c.dotted_ring(27.6, 0.95, 0.19)
    for k in range(12):
        a = 2 * math.pi * k / 12
        c.diamond(28.8 * math.sin(a), 28.8 * math.cos(a), a, 1.0, 0.17)
    for k in range(6):
        a = 2 * math.pi * k / 6 + math.pi / 6
        r = 12.6
        while r <= 26.8:
            c.dot(r * math.sin(a), r * math.cos(a), 0.14 + 0.06 * ((r - 12.6) / 14.2))
            r += 1.05
    return c


def inner(pal):
    c = Canvas(INNER_PX, INNER_BLOCKS, pal)
    c.ring_line(12.0, 0.16, 120)
    c.dotted_ring(12.0, 0.7, 0.22, hot_every=5)
    for k in range(6):
        a = 2 * math.pi * k / 6
        c.diamond(8.0 * math.sin(a), 8.0 * math.cos(a), a, 0.7, 0.13)
    c.ring_line(4.0, 0.14, 140)
    c.dotted_ring(4.0, 0.55, 0.2, hot_every=3)
    c.dot(0, 0, 0.45, hot=True)
    return c


def model(texture):
    face = {"uv": [0, 0, 16, 16], "texture": "#panel"}
    return {"textures": {"panel": texture, "particle": texture},
            "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "faces": {"north": face, "south": face}}]}


if __name__ == "__main__":
    tex, mod = ROOT / "textures" / "item", ROOT / "models" / "item"
    tex.mkdir(parents=True, exist_ok=True)
    mod.mkdir(parents=True, exist_ok=True)
    for name, pal in PALETTES.items():
        for part, make in (("outer", outer), ("inner", inner)):
            base = f"daily_seal_{part}_{name}"
            make(pal).save(tex / f"{base}.png")
            (mod / f"{base}.json").write_text(json.dumps(model(f"cobbleraids:item/{base}"), indent=2) + "\n", encoding="utf-8")
    print("wrote the daily seal panels: outer and inner, bronze, gold and ember")
