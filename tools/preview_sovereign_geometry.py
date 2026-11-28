"""Render actual prototype cuboids for review. This is not a Minecraft renderer or acceptance test."""
from pathlib import Path
import json
import sys
import math
from PIL import Image, ImageDraw, ImageFont, ImageStat

WORKSPACE = Path(__file__).resolve().parents[1]
ASSETS = WORKSPACE / 'src/main/resources/assets/cobbleraids'
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else WORKSPACE / 'build/sovereign-geometry-preview'
OUT.mkdir(parents=True, exist_ok=True)


def rotate(v, q):
    x, y, z, w = q
    tx, ty, tz = 2 * (y * v[2] - z * v[1]), 2 * (z * v[0] - x * v[2]), 2 * (x * v[1] - y * v[0])
    return [v[0] + w * tx + y * tz - z * ty, v[1] + w * ty + z * tx - x * tz, v[2] + w * tz + x * ty - y * tx]


def faces(name):
    model = json.loads((ASSETS / ('models/item/worldboss_sovereign_' + name + '.json')).read_text())
    colors = {}
    for key, resource in model['textures'].items():
        texture = ASSETS / ('textures/' + resource.split(':')[1] + '.png')
        colors[key] = [round(v) for v in ImageStat.Stat(Image.open(texture).convert('RGB')).mean]
    polygons = []
    indices = {'north': [0, 2, 3, 1], 'south': [4, 5, 7, 6], 'west': [0, 4, 6, 2],
               'east': [1, 3, 7, 5], 'up': [2, 6, 7, 3], 'down': [0, 1, 5, 4]}
    for e in model['elements']:
        points = [[e['to' if c & (1 << axis) else 'from'][axis] for axis in range(3)] for c in range(8)]
        if 'rotation' in e:
            r = e['rotation']; a = math.radians(r['angle']) / 2
            q = [0, 0, 0, math.cos(a)]; q['xyz'.index(r['axis'])] = math.sin(a)
            points = [[a + b for a, b in zip(rotate([v[i] - r['origin'][i] for i in range(3)], q), r['origin'])] for v in points]
        points = [[(v - 8) / 16 for v in p] for p in points]
        for face, spec in e['faces'].items():
            polygons.append({'points': [points[i] for i in indices[face]], 'color': colors[spec['texture'][1:]]})
    return polygons


def pose(name, tick=0):
    radius = 96
    if name == 'ground':
        half = tick % 7200 / 7200 * math.pi
        return [0, max(.15, radius*.001), 0], [0, math.sin(half), 0, math.cos(half)], radius*.58/.875*(1+.005*math.sin(tick%240/240*math.tau))
    if name == 'energy':
        t, q, scale = pose('band', ((1200-tick%1200)%1200)*3)
        return [t[0],t[1]+1,t[2]], q, scale*.97
    if name == 'band':
        tilt = math.radians(22.5); half = (tick % 3600) / 3600 * math.pi
        sx, cx, sy, cy = math.sin(tilt / 2), math.cos(tilt / 2), math.sin(half), math.cos(half)
        return [0, radius * (math.sin(tilt) + 0.04) + 6, 0], [sx * cy, cx * sy, sx * sy, cx * cy], radius / 0.875
    bob = math.sin((tick % 180) / 180 * math.tau); yaw = (math.atan2(-0.72, 0.52) + math.pi) / 2; roll = math.radians(3) * bob / 2
    sy, cy, sz, cz = math.sin(yaw), math.cos(yaw), math.sin(roll), math.cos(roll)
    t, q = [-radius * 0.72, radius * 0.25 + 6 + bob * 0.75, radius * 0.52], [sy * sz, sy * cz, cy * sz, cy * cz]
    if name == 'halo':
        half = -tick%2400/2400*math.pi if tick else 0
        s, c = math.sin(half), math.cos(half)
        q = [q[0]*c+q[1]*s,q[1]*c-q[0]*s,q[2]*c+q[3]*s,q[3]*c-q[2]*s]
        return [t[0],t[1]+.5,t[2]], q, 18*(1+.035*math.sin(tick%240/240*math.tau))
    return t, q, 14


def world(name):
    t, q, scale = pose(name)
    return [{'points': [[v[i] + t[i] for i in range(3)] for v in [rotate([-p[0] * scale, p[1] * scale, -p[2] * scale], q) for p in f['points']]], 'color': f['color']} for f in faces(name)]


def sub(a, b): return [x - y for x, y in zip(a, b)]
def dot(a, b): return sum(x * y for x, y in zip(a, b))
def norm(a): return [x / math.sqrt(dot(a, a)) for x in a]
def cross(a, b): return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def view(image, polygons, rect, camera, target, focal, grid=False):
    layer = Image.new('RGB', (rect[2], rect[3]), '#171c25'); draw = ImageDraw.Draw(layer)
    forward = norm(sub(target, camera)); right = norm(cross(forward, [0, 1, 0])); up = cross(right, forward)
    def project(p):
        v = sub(p, camera); depth = dot(v, forward)
        return (rect[2] / 2 + dot(v, right) / depth * focal, rect[3] / 2 - dot(v, up) / depth * focal, depth)
    if grid:
        for x in range(-128, 129, 16):
            for a, b in [([x, 0, -128], [x, 0, 128]), ([-128, 0, x], [128, 0, x])]:
                pa, pb = project(a), project(b)
                draw.line((pa[0], pa[1], pb[0], pb[1]), fill='#2a3341', width=1)
    for f in sorted(polygons, key=lambda f: sum(project(p)[2] for p in f['points']), reverse=True):
        vertices = [project(p) for p in f['points']]
        if min(p[2] for p in vertices) <= 0: continue
        normal = norm(cross(sub(f['points'][1], f['points'][0]), sub(f['points'][2], f['points'][0])))
        shade = 0.67 + 0.33 * abs(dot(normal, norm([0.4, 0.8, -0.5])))
        color = tuple(round(c * shade) for c in f['color'])
        draw.polygon([(p[0], p[1]) for p in vertices], fill=color)
        draw.line([(p[0], p[1]) for p in vertices] + [(vertices[0][0], vertices[0][1])], fill=tuple(round(c * 0.72) for c in color), width=1)
    image.paste(layer, (rect[0], rect[1]))


canvas = Image.new('RGB', (1600, 960), '#10151c'); draw = ImageDraw.Draw(canvas)
font = lambda size: ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', size)
draw.text((36, 22), 'SOVEREIGN SEAL  /  GEOMETRY PROTOTYPE', font=font(30), fill='#f3ddb0')
draw.text((36, 68), 'Actual model cuboids • Offline preview with illustrative lighting • NOT a Minecraft screenshot', font=font(19), fill='#b5c0cd')
names = ['band','crest','halo','energy','ground']
view(canvas, sum((world(name) for name in names), []), (32, 125, 1090, 690), [190, 140, 210], [0, 30, 0], 1040, True)
detail = [{'points': [[v * scale for v in p] for p in f['points']], 'color': f['color']}
          for name,scale in [('crest',14),('halo',18)] for f in faces(name)]
view(canvas, detail, (1144, 125, 424, 690), [8, 5, -42], [0, 0, 0], 550)
draw.text((48, 138), 'AUREOLE / ENERGY ARCS / RITUAL CIRCLE', font=font(20), fill='#dce2eb')
draw.text((1160, 138), 'CREST DETAIL', font=font(20), fill='#dce2eb')
draw.text((36, 838), 'Arena radius: 96 blocks. Grid: 16 blocks. Five bounded display models in full mode.', font=font(21), fill='#dce2eb')
draw.text((36, 884), 'Native appearance, interpolation, cleanup and 50-player performance remain unverified.', font=font(20), fill='#acbacb')
canvas.save(OUT / 'sovereign-prototype-geometry.png')
(OUT / 'sovereign-prototype-mesh.json').write_text(json.dumps({**{name:faces(name) for name in names}, 'notes': 'Actual local model geometry; not Minecraft rendering.'}, separators=(',', ':')))
print(OUT / 'sovereign-prototype-geometry.png')
