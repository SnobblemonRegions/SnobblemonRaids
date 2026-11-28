"""Owned Distortion membrane: flowing violet nebula, crimson rifts and broken gold seals.

No ghost faces, green wisps or shared seasonal texture paths. Standard-library only.
"""
import json
import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/cobbleraids'
SIZE = 64
FRAMES = 24


def pixel(variant, frame, x, y):
    half = (y - 1) / 61 * 30.5
    edge = min((half - abs(x - 31.5)) / 1.118, 62 - y)
    if y < 1 or y > 62 or edge < 0:
        return (0, 0, 0, 0)
    phase = frame / FRAMES * math.tau + variant * math.tau / 3
    rim = math.exp(-edge / 0.65)
    # The currents loop seamlessly and differ in phase across the three panel variants.
    flow = math.sin(x * 0.14 + y * 0.09 + 1.7 * math.sin(y * 0.13 - phase) + phase)
    cloud = (flow + math.cos(y * 0.15 - x * 0.07 - phase)) * 0.25 + 0.5
    stream = max(0, flow) ** 7
    # A jagged tear, with a broad violet halo and a narrow scarlet core.
    scar_x = 30 + (y - 32) * 0.24 + 3 * math.sin(y * 0.44 + variant) + 1.5 * math.sin(phase)
    scar = math.exp(-((x - scar_x) / 0.8) ** 2) * math.sin(math.pi * max(0, min(1, (y - 8) / 50))) ** 2
    halo = math.exp(-((x - scar_x) / 3.0) ** 2)
    # A broken orbital seal around the panel's centre, with moving gold segments.
    dx, dy = x - 31.5, (y - 41) * 0.9
    radius = math.hypot(dx, dy)
    angle = math.atan2(dy, dx)
    orbit = math.exp(-((radius - 9) / 0.6) ** 2)
    orbit *= max(0, math.sin(angle * 3 - phase)) ** 2
    sparks = max(0, math.cos(x * 1.73 + y * 2.37 + phase)) ** 48 * stream
    pulse = 0.5 + 0.5 * math.sin(phase)
    flash = variant == 3
    color = [round(62 + 43 * cloud + 38 * stream + 48 * halo),
             round(16 + 20 * cloud + 20 * stream),
             round(143 + 75 * cloud + 32 * stream)]
    for strength, target in [(scar * 0.98, (255, 24, 57)),
                             (orbit * (0.85 + 0.13 * pulse), (255, 218, 93)),
                             (rim * 0.95, (247, 199, 99)), (sparks * 0.9, (255, 241, 193))]:
        color = [round(a + (b - a) * strength) for a, b in zip(color, target)]
    # Interior detail is visible without turning the sphere into an opaque wall.
    alpha = 18 + 40 * cloud + 15 * stream + 180 * scar + 155 * orbit + 65 * sparks + 165 * rim
    if flash:
        color = [round(c + (245 - c) * 0.55) for c in color]
        alpha += 25 + 30 * pulse
    return (*[min(255, c) for c in color], min(230, round(alpha)))


def write_png(path, variant):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(c for x in range(SIZE) for c in pixel(variant, frame, x, y))
                   for frame in range(FRAMES) for y in range(SIZE))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE * FRAMES, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    for folder in ['models/item', 'textures/item']:
        (ROOT / folder).mkdir(parents=True, exist_ok=True)
    for variant in range(4):
        name = 'worldboss_distortion_dome_' + str(variant)
        model = {'textures': {'panel': 'cobbleraids:item/' + name, 'particle': 'cobbleraids:item/' + name},
                 'elements': [{'from': [0, 0, 8], 'to': [16, 16, 8], 'shade': False,
                               'faces': {face: {'uv': [0, 0, 16, 16], 'texture': '#panel'} for face in ['north', 'south']}}]}
        (ROOT / ('models/item/' + name + '.json')).write_text(json.dumps(model, indent=2) + '\n')
        write_png(ROOT / ('textures/item/' + name + '.png'), variant)
        (ROOT / ('textures/item/' + name + '.png.mcmeta')).write_text(json.dumps({
            'animation': {'frametime': 4, 'interpolate': True}
        }, indent=2) + '\n')
