"""Bakes the Gem Shop page: shop_template.html + art + font (+ mock icons for the browser preview) -> shop.html

    python build_page.py <out shop.html> <art folder> [icons folder for the mock preview]
"""
import base64
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
out, art = sys.argv[1], sys.argv[2]
icons = sys.argv[3] if len(sys.argv) > 3 else None


def b64(path):
    return base64.b64encode(open(path, 'rb').read()).decode()


html = open(os.path.join(HERE, 'shop_template.html'), encoding='utf-8').read()
html = html.replace('/*__PXFONT__*/{}', open(os.path.join(HERE, 'pxfont.json'), encoding='utf-8').read())
html = html.replace('__GEM64__', b64(os.path.join(art, 'gem_64.png')))
html = html.replace('__TICKET64__', b64(os.path.join(art, 'ticket_64.png')))
html = html.replace('__SKIN64__', b64(os.path.join(art, 'skin_64.png')))
mock = {}
if icons and os.path.isdir(icons):
    for ns in os.listdir(icons):
        for root, _, files in os.walk(os.path.join(icons, ns)):
            for f in files:
                if f.endswith('.png'):
                    rel = os.path.relpath(os.path.join(root, f), os.path.join(icons, ns)).replace('\\', '/')[:-4]
                    mock[ns + ':' + rel] = 'data:image/png;base64,' + b64(os.path.join(root, f))
html = html.replace('/*__MOCKICONS__*/{}', json.dumps(mock))
os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
open(out, 'w', encoding='utf-8', newline='\n').write(html)
print('wrote', out, len(html), 'bytes;', len(mock), 'mock icons')
