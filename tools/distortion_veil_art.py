"""Deterministic, tileable soft VFX textures; regenerate with Python + Pillow."""
from pathlib import Path
import math
from PIL import Image
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'radar-client/src/main/resources/assets/cobbleraids_radar/textures/worldboss'
OUT.mkdir(parents=True, exist_ok=True)
for name in ('veil_silk', 'umbra_wisp', 'veil_mote'):
    size = 256 if name != 'veil_mote' else 64
    image = Image.new('RGBA', (size,size))
    for y in range(size):
        v = y / (size-1)
        for x in range(size):
            u = x / (size-1)
            if name == 'veil_mote':
                r = math.hypot((u-.5)*2,(v-.5)*2)
                alpha = max(0,1-r)**2.4
                alpha = min(1, alpha + .28 * math.exp(-((u-.5)*35)**2-((v-.5)*10)**2))
                shade = 255
            else:
                bend = .10*math.sin(v*math.tau)+.035*math.sin(v*math.tau*3+.6)
                center = (u-.5-bend)
                envelope = math.sin(math.pi*u)**2
                veins = .62 + .18*math.sin(u*9+math.sin(v*math.tau)*2) + .12*math.sin(u*17+v*math.tau*3+math.sin(u*7-v*math.tau))
                if name == 'veil_silk':
                    alpha = envelope * (math.exp(-(center*5)**2)*.48+math.exp(-((center-.12)*28)**2)*.38) * max(.1,veins)
                    shade = int(160+95*max(0,veins))
                else:
                    alpha = envelope * math.exp(-(center*4.8)**2) * (.40+.55*max(0,veins))
                    shade = int(100+155*max(0,veins))
            image.putpixel((x,y),(shade,shade,shade,int(max(0,min(1,alpha * (1 if name == 'veil_mote' else 1.8)))*255)))
    image.save(OUT/(name+'.png'))
    (OUT/(name+'.png.mcmeta')).write_text('{"texture":{"blur":true,"clamp":false}}\n')
    print(OUT/(name+'.png'))
