"""Reproducible native 182x5 Distortion bar; no vanilla sprite overrides.

Gold beveled frame, obsidian recess and a slow crimson energy glint.
BossBarSkins maps this owner-scoped art to an available physical slot.
"""
import json
import math
from pathlib import Path
import struct
import zlib

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/cobbleraids/textures/boss_bar'


def png(path, rows):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    data = b''.join(b'\0' + bytes(v for rgb in row for v in (*rgb, 255)) for row in rows)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 182, len(rows), 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(data, 9)) + chunk(b'IEND', b''))


def frame(progress, phase=0):
    rows = []
    for y in range(5):
        row = []
        for x in range(182):
            if y in (0, 4):
                color = (188, 145, 67) if y == 0 else (85, 61, 36)
                if x % 30 in (0, 1): color = (240, 207, 130)
            elif x in (0, 181):
                color = (232, 194, 107)
            elif not progress:
                color = [(0, 0, 0), (30, 29, 39), (19, 18, 27), (12, 12, 19)][y]
            else:
                glint = max(0, math.cos(2 * math.pi * (x / 60 - phase / 24))) ** 12
                base = [(0, 0, 0), (196, 62, 72), (139, 27, 48), (82, 16, 35)][y]
                color = tuple(min(255, round(c + glint * a)) for c, a in zip(base, (40, 38, 32)))
                if x % 30 == 0: color = (212, 150, 76)
            row.append(color)
        rows.append(row)
    return rows


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    png(OUT / 'distortion_background.png', frame(False))
    png(OUT / 'distortion_progress.png', [row for phase in range(24) for row in frame(True, phase)])
    (OUT / 'distortion_progress.png.mcmeta').write_text(json.dumps({
        'animation': {'width': 182, 'height': 5, 'frametime': 3, 'interpolate': True}
    }, indent=2) + '\n')
