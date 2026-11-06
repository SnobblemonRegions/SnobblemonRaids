"""Tower medallion icons (the page's pixel SVG `be()`): tw_<key>.png from the ORIGINAL hall rows, rendered at 4x."""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright

OUT = os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions', 'textures', 'gui', 'pages', 'cobbletowers'))
KEYS = {'Neutral': 'neutral', 'Tideforge': 'tideforge', 'Rootvale': 'rootvale', 'Duskvale': 'duskvale', 'Halloween': 'halloween', 'Zeta': 'other'}


def main():
    fx = json.load(open(r'G:\Claude\cobbletowers-ui\fixtures\TowerHallState.json'))
    tw = fx['data']['towers']
    base = {t['id']: t for t in tw}
    for tid, nm in (('cobbletowers:tideforge', 'Tideforge Tower'), ('cobbletowers:halloween', 'Halloween Tower'), ('cobbletowers:zeta', 'Zeta Tower')):
        if tid not in base:
            tw.append({'id': tid, 'name': nm, 'floors': 10, 'milestones': 2, 'ascends': True})
    httpd = fid.serve()
    try:
        with sync_playwright() as p:
            br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
            ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=4)
            ctx.add_init_script("localStorage.setItem('cobbletowers.guideSeen','1')")
            pg = ctx.new_page()
            pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
            time.sleep(1.0)
            pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", fx)
            time.sleep(1.2)
            for el in pg.query_selector_all('.trow'):
                nm = el.query_selector('.dtname').inner_text().strip().split(' ')[0]
                key = KEYS.get(nm) or ('neutral' if nm == 'Test' else None)
                if not key or (nm == 'Test'):
                    continue
                svg = el.query_selector('.dtname svg')
                svg.screenshot(path=os.path.join(OUT, 'tw_%s.png' % key), omit_background=True)
                print(key, 'ok')
            br.close()
    finally:
        httpd.shutdown()


main()
