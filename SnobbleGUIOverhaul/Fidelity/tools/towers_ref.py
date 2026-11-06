"""Render the ORIGINAL cobbletowers page (refsite copy) in headless Chrome with captured server payloads.
  python towers_ref.py <outdir> [fixture ...]
Each fixture is G:/Claude/cobbletowers-ui/fixtures/<Kind>.json ({kind, data}); the page receives it as the webgui 'tower' event.
"""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright

FIX = r'G:\Claude\cobbletowers-ui\fixtures'


def main():
    out = sys.argv[1]
    names = sys.argv[2:] or [f[:-5] for f in os.listdir(FIX) if f.endswith('.json')]
    os.makedirs(out, exist_ok=True)
    httpd = fid.serve()
    try:
        with sync_playwright() as p:
            br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
            for n in names:
                fx = json.load(open(os.path.join(FIX, n + '.json')))
                ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=1)
                ctx.add_init_script("localStorage.setItem('cobbletowers.guideSeen','1')"); pg = ctx.new_page()
                pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
                time.sleep(1.2)
                pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", fx)
                time.sleep(1.5)
                png = os.path.join(out, n + '.png')
                pg.screenshot(path=png)
                print(n, 'ok')
                ctx.close()
            br.close()
    finally:
        httpd.shutdown()


main()
