"""Trials / Social tab art from the ORIGINAL page: tb_<region>.png (trial banners), soc_art_<n>.png, soc_ring_<n>.png, st_*.png, note_icon.png, mod_gem.png."""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright

OUT = os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions', 'textures', 'gui', 'pages', 'cobbletowers'))
REG = ['neutral', 'tideforge', 'rootvale', 'duskvale', 'halloween']


def shot(pg, el, name):
    el.screenshot(path=os.path.join(OUT, name + '.png'), omit_background=True)
    print(name)


def main():
    fx = json.load(open(r'G:\Claude\cobbletowers-ui\fixtures\TowerHallState.json'))
    httpd = fid.serve()
    try:
        with sync_playwright() as p:
            br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
            ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=2)
            ctx.add_init_script("localStorage.setItem('cobbletowers.guideSeen','1')")
            pg = ctx.new_page()
            pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
            time.sleep(1.0)
            for i, r in enumerate(REG):
                f = json.loads(json.dumps(fx))
                f['data']['trials'][0]['tower'] = 'cobbletowers:' + r
                pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", f)
                time.sleep(0.5)
                pg.get_by_text('Trials', exact=True).first.click()
                time.sleep(0.7)
                pg.mouse.move(2, 2)
                time.sleep(0.4)
                shot(pg, pg.query_selector_all('.dbanner .dpano')[0], 'tb_' + r)
                if i == 0:
                    ds = pg.query_selector_all('.dstat svg.dpix')[:3]
                    for el, n in zip(ds, ['st_floors', 'st_mode', 'st_enemy']):
                        shot(pg, el, n)
                    shot(pg, pg.query_selector('.dnote svg.dpix'), 'note_icon')
                pg.get_by_text('Play', exact=True).first.click()
                time.sleep(0.3)
            # weekly's gem
            f = json.loads(json.dumps(fx))
            pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", f)
            time.sleep(0.4)
            pg.get_by_text('Trials', exact=True).first.click()
            time.sleep(0.7)
            pg.mouse.move(2, 2)
            shot(pg, pg.query_selector('.dmodchip svg.dpix'), 'mod_gem')
            pg.get_by_text('Social', exact=True).first.click()
            time.sleep(0.8)
            pg.mouse.move(2, 2)
            time.sleep(0.4)
            for i, c in enumerate(pg.query_selector_all('.dsoc')):
                shot(pg, c.query_selector('.dsocart .dpano'), 'soc_art_%d' % i)
                shot(pg, c.query_selector('.dsocring'), 'soc_ring_%d' % i)
            br.close()
    finally:
        httpd.shutdown()


main()
