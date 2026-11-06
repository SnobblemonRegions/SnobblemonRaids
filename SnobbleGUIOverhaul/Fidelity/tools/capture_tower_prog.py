"""Progress tab art: rk_<rank>.png (rank medals, tile size), dgap.png, from the ORIGINAL page."""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright
OUT = os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions', 'textures', 'gui', 'pages', 'cobbletowers'))
RANKS = ['Bronze', 'Silver', 'Gold', 'Platinum', 'Diamond', 'Master', 'Champion']
hall = json.load(open(r'G:\Claude\cobbletowers-ui\fixtures\TowerHallState.json'))
tr = json.load(open(r'G:\Claude\cobbletowers-ui\fixtures\TrackState.json'))
nodes = [{'number': i + 1, 'state': 1, 'reward': '', 'icon': '', 'note': 'Rank: ' + r} for i, r in enumerate(RANKS)] + [{'number': 8, 'state': 0, 'reward': '', 'icon': '', 'note': ''}]
tr['data']['mastery']['nodes'] = nodes
httpd = fid.serve()
try:
    with sync_playwright() as p:
        br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
        ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=4)
        ctx.add_init_script("localStorage.setItem('cobbletowers.guideSeen','1')")
        pg = ctx.new_page()
        pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
        time.sleep(1)
        for f in (hall, tr):
            pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", f)
            time.sleep(0.5)
        pg.get_by_text('Progress', exact=True).first.click()
        time.sleep(1)
        pg.mouse.move(2, 2)
        tiles = pg.query_selector_all('.dtile')
        for t, r in zip(tiles, RANKS):
            pg.evaluate("e => e.scrollIntoView()", t)
            t.query_selector('.dpic svg').screenshot(path=os.path.join(OUT, 'rk_%s.png' % r.lower()), omit_background=True)
            print(r)
        g = tiles[7].query_selector('.dgap')
        g.screenshot(path=os.path.join(OUT, 'dgap.png'), omit_background=True)
        br.close()
finally:
    httpd.shutdown()
