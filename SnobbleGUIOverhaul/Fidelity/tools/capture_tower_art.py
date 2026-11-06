"""Captures the CobbleTowers modifier-card art (the page paints it itself from a theme string) from the ORIGINAL page, one PNG per theme:
   art_<key>.png (card, 186x101 at 1280x720) and wide_<key>.png (detail panel, 313x104). Into the SERVER pack (never the client jar).
   key = theme with ':' -> '_'.   Also writes themes.json (the list, for the server)."""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright

OUT = os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions', 'textures', 'gui', 'pages', 'cobbletowers'))
THEMES = ['weather:sunny', 'weather:sandstorm', 'weather:hail', 'weather:rain',
          'terrain:electricterrain', 'terrain:psychicterrain', 'terrain:mistyterrain', 'terrain:grassyterrain',
          'enemy:fragile', 'enemy:champion', 'enemy:veteran', 'enemy:novice', 'enemy:tough', 'enemy:other',
          'encounter:crowd', 'encounter:other',
          'constraint:no_heal', 'constraint:no_setup', 'constraint:no_switch', 'constraint:no_items', 'constraint:other',
          'scouting', 'reward_up', 'reward_down',
          'custom:glass_cannon', 'custom:fortunes_wheel', 'custom:field_hospital', 'custom:black_market', 'custom:swift_start', 'custom:iron_hide', 'custom:war_banner',
          'unknown']


def key(t):
    return t.replace(':', '_')


def main():
    os.makedirs(OUT, exist_ok=True)
    base = json.load(open(r'G:\Claude\cobbletowers-ui\fixtures\IntermissionState.json'))
    httpd = fid.serve()
    try:
        with sync_playwright() as p:
            br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
            for i in range(0, len(THEMES), 3):
                grp = THEMES[i:i + 3]
                fx = json.loads(json.dumps(base))
                fx['data']['draft']['cards'] = [{'id': 'cobbletowers:t%d' % k, 'displayName': 'T', 'votes': 0, 'risk': 0, 'theme': t, 'lines': ['x']} for k, t in enumerate(grp)]
                ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=1)
                pg = ctx.new_page()
                pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
                time.sleep(1.0)
                pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", fx)
                time.sleep(1.2)
                cards = pg.query_selector_all('.dmc')
                for k, t in enumerate(grp):
                    art = cards[k].query_selector('.dart')
                    art.screenshot(path=os.path.join(OUT, 'art_%s.png' % key(t)))
                    cards[k].click()
                    time.sleep(0.5)
                    pg.mouse.move(5, 5)
                    time.sleep(0.2)
                    w = pg.query_selector('.dside .dart')
                    if w:
                        w.screenshot(path=os.path.join(OUT, 'wide_%s.png' % key(t)))
                    print(t, 'ok')
                ctx.close()
            br.close()
    finally:
        httpd.shutdown()
    json.dump([key(t) for t in THEMES], open(os.path.join(OUT, 'themes.json'), 'w'))


main()
