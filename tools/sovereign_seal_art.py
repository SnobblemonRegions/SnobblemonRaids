"""Native 1.21.1 cuboid models for the opt-in Sovereign Seal prototype.

Broken orbital band, crest, aureole, counter-orbiting energy and ritual floor seal.
Native geometry only; no shaders, custom model loader or world blocks.
"""
import json
import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/cobbleraids'
MATERIALS = ['gold', 'black', 'red', 'light', 'ember', 'gold_side', 'gold_shadow',
             'black_side', 'black_shadow', 'red_side', 'red_shadow']


def box(a, b, material, rotation=None):
    solid = material in {'gold', 'black', 'red'}
    def surface(face):
        if not solid or face in {'up','west'}: return material
        return material + ('_shadow' if face in {'down','east'} else '_side')
    element = {'from': [round(v, 6) for v in a], 'to': [round(v, 6) for v in b], 'shade': solid,
               'faces': {face: {'uv': [0, 0, 16, 16], 'texture': '#' + surface(face)}
                         for face in ['north', 'south', 'east', 'west', 'up', 'down']}}
    if rotation:
        element['rotation'] = rotation
    return element


def model(elements):
    textures = {name: 'cobbleraids:item/sovereign_' + name for name in MATERIALS}
    textures['particle'] = textures['gold']
    return {'ambientocclusion': any(e['shade'] for e in elements), 'gui_light': 'side', 'textures': textures, 'elements': elements}


def band():
    out = []
    radius = 14
    length = 2 * radius * math.tan(math.pi / 16)
    for part in range(16):
        if part in {2, 7, 12}:
            continue
        angle = part * math.pi / 8
        cx, cz = 8 + radius * math.cos(angle), 8 + radius * math.sin(angle)
        tangent = (part * 22.5 + 90 + 90) % 180 - 90
        along_x = abs(tangent) <= 45
        rotation = -tangent if along_x else (90 - tangent + 90) % 180 - 90
        for tile in range(4):
            start, end = -length / 2 + tile * length / 4 + 0.012, -length / 2 + (tile + 1) * length / 4 - 0.012
            for y0, y1, thickness in [(7.77, 8.23, 0.32), (8.23, 8.30, 0.24), (7.70, 7.77, 0.24)]:
                if along_x:
                    a, b = (cx + start, y0, cz - thickness / 2), (cx + end, y1, cz + thickness / 2)
                else:
                    a, b = (cx - thickness / 2, y0, cz + start), (cx + thickness / 2, y1, cz + end)
                out.append(box(a, b, 'gold', {'origin': [cx, 8, cz], 'axis': 'y', 'angle': rotation, 'rescale': False}))
    return model(out)


def crest():
    out = [box((5.5, 5, 6.5), (10.5, 12, 9.5), 'black')]
    for mirrored in [False, True]:
        def add(a, b, material, tilt=0):
            if mirrored:
                a, b = (16 - b[0], a[1], a[2]), (16 - a[0], b[1], b[2])
            rotation = {'origin': [8, 8, 8], 'axis': 'z', 'angle': -tilt if mirrored else tilt, 'rescale': False} if tilt else None
            out.append(box(a, b, material, rotation))
        add((2.5, 8, 6), (6.5, 13, 9), 'black', 22.5)
        # The stem meets the cap instead of occupying its front/back/top planes.
        add((2, 10.5, 5.7), (4, 13, 9.7), 'gold')
        add((1.5, 13, 5.7), (4.5, 15, 9.7), 'gold')
        add((4.5, 7.5, 5.3), (6, 12.5, 6.2), 'gold', 22.5)
        add((4, 10, 4.95), (6.5, 11.5, 5.9), 'gold', 22.5)
    out.extend([
        box((6.5, 8, 5.8), (9.5, 10.5, 6.5), 'gold'),
        box((6.4, 4, 6), (9.6, 7, 9.1), 'red'),
        box((7, 2, 6.4), (9, 4, 9), 'red'),
        box((7.5, 0, 6.8), (8.5, 2, 8.6), 'red'),
        box((7, 7, 5.9), (9, 8, 6.5), 'red'),
        box((7, 10.5, 5.3), (9, 12.5, 5.8), 'light', {'origin': [8, 11.5, 5.5], 'axis': 'z', 'angle': 45, 'rescale': False}),
        box((7.45, 11, 4.95), (8.55, 12, 5.4), 'ember', {'origin': [8, 11.5, 5.3], 'axis': 'z', 'angle': 45, 'rescale': False}),
        box((6.9, 5.2, 5.6), (7.2, 6.7, 6), 'light'),
        box((8.8, 5.2, 5.6), (9.1, 6.7, 6), 'light'),
    ])
    return model(out)


def stroke(out, cx, cy, cz, length, width, angle, material, vertical=False, depth=0.08):
    # Native model rotations permit only 0, +/-22.5 and +/-45 degrees.
    tangent = (angle + 90) % 180 - 90
    along_x = abs(tangent) <= 45
    rotation = -tangent if along_x else (90 - tangent + 90) % 180 - 90
    a = (cx-length/2, cy-width/2, cz-depth/2) if along_x else (cx-width/2, cy-length/2, cz-depth/2)
    b = (cx+length/2, cy+width/2, cz+depth/2) if along_x else (cx+width/2, cy+length/2, cz+depth/2)
    if vertical:
        # Positive Z model rotation rotates X toward positive Y.
        rotation = -rotation
        origin, axis = [cx, cy, cz], 'z'
    else:
        # Reuse the XY construction in the XZ plane.
        a, b = (a[0], cz-depth/2, a[1]), (b[0], cz+depth/2, b[1])
        origin, axis = [cx, cz, cy], 'y'
    out.append(box(a, b, material, {'origin': origin, 'axis': axis, 'angle': round(rotation, 6), 'rescale': False}))


def ring_lines(out, radius, width, material, vertical=False, parts=None, depth=0.04, z=8):
    for part in range(16):
        if parts is not None and part not in parts:
            continue
        angle = part * math.pi / 8
        stroke(out, 8+radius*math.cos(angle), 8+radius*math.sin(angle), z,
               2*radius*math.tan(math.pi/16)*0.94, width, part*22.5+90, material, vertical, depth)


def halo():
    out = []
    ring_lines(out, 14, 0.28, 'light', True, depth=0.10, z=9.5)
    ring_lines(out, 11.7, 0.18, 'gold', True, depth=0.12, z=9.5)
    ring_lines(out, 9.8, 0.16, 'ember', True, parts={0,1,4,5,8,9,12,13}, depth=0.10, z=9.5)
    for part in range(8):
        angle = part*math.pi/4
        x, y = 8+12.7*math.cos(angle), 8+12.7*math.sin(angle)
        stroke(out, x, y, 9.5, 2.6, 0.25, part*45, 'gold', True, 0.15)
        stroke(out, x, y, 9.4, 1.2, 0.24, part*45+90, 'light', True, 0.15)
        # Four faceted keystones and shorter secondary notches give the aureole a readable hierarchy.
        if part % 2 == 0:
            out.append(box((x-.55,y-.55,9.1),(x+.55,y+.55,9.35),'ember',
                           {'origin':[x,y,9.2],'axis':'z','angle':45,'rescale':False}))
    for part in range(16):
        angle = (part+.5)*math.pi/8
        x,y=8+10.9*math.cos(angle),8+10.9*math.sin(angle)
        out.append(box((x-.12,y-.35,9.3),(x+.12,y+.35,9.5),'light'))
    return model(out)


def energy():
    out = []
    for radius, width, material in [(14,0.36,'ember'),(13.75,0.12,'light')]:
        ring_lines(out, radius, width, material, parts={0,1,5,6,10,11}, depth=0.10)
    return model(out)


def ground():
    out = []
    ring_lines(out, 14, 0.10, 'light', depth=0.02)
    ring_lines(out, 11.6, 0.12, 'gold', depth=0.02)
    ring_lines(out, 7.8, 0.11, 'ember', parts={0,1,4,5,8,9,12,13}, depth=0.02)
    for part in range(8):
        angle = part*math.pi/4
        x, z = 8+12.8*math.cos(angle), 8+12.8*math.sin(angle)
        stroke(out, x, z, 8, 1.7, 0.20, part*45, 'gold', depth=0.02)
        stroke(out, x, z, 8.02, 0.9, 0.15, part*45+90, 'light', depth=0.02)
        x, z = 8+9.7*math.cos(angle), 8+9.7*math.sin(angle)
        stroke(out, x, z, 8, 2.0, 0.08, part*45, 'ember', depth=0.02)
    return model(out)


def png(name, base):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    rows = []
    size = 128
    family = name.split('_')[0]
    tone = 0.55 if name.endswith('_shadow') else 0.80 if name.endswith('_side') else 1.0
    for y in range(size):
        row = []
        for x in range(size):
            edge = min(x, y, size-1-x, size-1-y)
            grain = ((x*73856093 ^ y*19349663) & 7) - 3
            light = grain * 0.6
            if family in {'light','ember'}:
                light += 34*math.exp(-((x-48)/23)**2) - 18*(abs(x-64)/64)
            else:
                # Broad highlights, shadowed bevels and inset frames survive distance mipmapping.
                light += 35 if (x < 4 or y < 4) else -48 if edge < 5 else 0
                light += 28*math.exp(-((x+y*.22-42)/12)**2) - 14*y/127
                if edge in (12,13,24): light -= 36
                if edge in (14,25): light += 15
                if family == 'gold':
                    # Machined chevrons, fine brushed grain and four recessed studs.
                    if 28<x<100 and 28<y<100 and (x+y)%40 in (0,1,2): light -= 28
                    if 28<x<100 and 28<y<100 and (x+y)%40 == 3: light += 9
                    light += ((y*17)%5)-2
                    for rx,ry in ((18,18),(109,18),(18,109),(109,109)):
                        d=math.hypot(x-rx,y-ry)
                        if 3<d<5: light -= 45
                        elif d<=3: light += 35-(x-rx)*6-(y-ry)*6
                    if (x*11+y*37)%251 == 0 and edge<9: light += 22
                elif family == 'black':
                    light += ((x//28+y//36)%3)*4
                    light += 13*math.exp(-((x-y*.7-20)/3)**2)
                    if (x+2*y)%79 == 0 and edge>7: light += 6
                else:
                    light += 14 if x<y else -10
                    light += 18*math.exp(-((x-34)/5)**2)
            row.extend((*[max(0, min(255, round((c + light)*tone))) for c in base], 255))
        rows.append(b'\0' + bytes(row))
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    data += chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b'')
    (ROOT / ('textures/item/sovereign_' + name + '.png')).write_bytes(data)


if __name__ == '__main__':
    for folder in ['models/item', 'textures/item']:
        (ROOT / folder).mkdir(parents=True, exist_ok=True)
    for name, geometry in [('band', band()), ('crest', crest()), ('halo', halo()), ('energy', energy()), ('ground', ground())]:
        (ROOT / ('models/item/worldboss_sovereign_' + name + '.json')).write_text(json.dumps(geometry, indent=2) + '\n')
    colors = {'gold': (183,138,60), 'black': (27,24,38), 'red': (145,19,43),
              'light': (255,239,174), 'ember': (250,61,80)}
    for name in MATERIALS:
        png(name, colors[name.split('_')[0]])
