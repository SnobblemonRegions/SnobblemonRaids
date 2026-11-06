"""CobbleTowers region panoramas from the ORIGINAL lobby (the page paints them on a canvas): pano_<region>.png, the visible pbox
(selected row = no dim overlay) at 1280x720, plus the full 240x60 canvases as panofull_<region>.png (for the larger banners)."""
import base64, json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright

OUT = os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions', 'textures', 'gui', 'pages', 'cobbletowers'))
REGIONS = {'tideforge': 'cobbletowers:tideforge', 'rootvale': 'cobbletowers:rootvale', 'duskvale': 'cobbletowers:duskvale', 'neutral': 'cobbletowers:neutral', 'halloween': 'cobbletowers:halloween'}


def main():
    base = json.load(open(r'G:\Claude\cobbletowers-ui\fixtures\PlayState.json'))
    base['data']['towers'].append({'id': 'cobbletowers:halloween', 'displayName': 'Halloween Tower'})
    httpd = fid.serve()
    try:
        with sync_playwright() as p:
            br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
            for key, tid in REGIONS.items():
                fx = json.loads(json.dumps(base))
                fx['data']['lobby']['selected'] = tid
                ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=1)
                pg = ctx.new_page()
                pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
                time.sleep(1.0)
                pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", fx)
                time.sleep(1.2)
                pg.mouse.move(2, 2)
                el = pg.query_selector('.trow.sel .pbox')
                el.screenshot(path=os.path.join(OUT, 'pano_%s.png' % key))
                url = pg.evaluate("() => document.querySelector('.trow.sel .pbox canvas').toDataURL('image/png')")
                open(os.path.join(OUT, 'panofull_%s.png' % key), 'wb').write(base64.b64decode(url.split(',')[1]))
                print(key, 'ok')
                ctx.close()
            br.close()
    finally:
        httpd.shutdown()


main()
