"""Rental draft art from the ORIGINAL page (rendered by its own CSS):
  rd_<type>_<rarity>.png   card face base (no sprite / name / level number / gems / medals), 280x400 design px at 2x
  rd_med_<type>.png        type hexagon medal;  rd_gem_<type>.png  type diamond
  rd_pack.png, rd_back.png sealed pack and card back
Also writes rental_types.json (set -> types/rarity/level) for the server."""
import json, os, shutil, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright

OUT = os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions', 'textures', 'gui', 'pages', 'cobbletowers'))
TABLE = json.load(open(os.path.join(os.path.dirname(__file__), 'rental_table.json')))
TYPES = {}
for k, v in TABLE.items():
    TYPES.setdefault(v['t'][0], k)
COL = {'normal': '#a8a77a', 'fire': '#ee8130', 'water': '#6390f0', 'electric': '#f7d02c', 'grass': '#7ac74c', 'ice': '#96d9d6', 'fighting': '#c22e28', 'poison': '#a33ea1',
       'ground': '#e2bf65', 'flying': '#a98ff3', 'psychic': '#f95587', 'bug': '#a6b91a', 'rock': '#b6a136', 'ghost': '#735797', 'dragon': '#6f35fc', 'dark': '#705746',
       'steel': '#b7b7ce', 'fairy': '#d685ad'}
RAR = {'common': ('Common', 1), 'uncommon': ('Uncommon', 2), 'rare': ('Rare', 3), 'epic': ('Epic', 4), 'legendary': ('Legendary', 5), 'mythic': ('Mythic', 6)}


def fixture(species):
    cards = [{'set': 'cobbletowers:' + s, 'species': s, 'name': s.capitalize(), 'rarity': TABLE[s]['r'], 'details': {'level': 50, 'moves': []}} for s in species]
    packs = [{'god': False, 'cards': cards[i * 2:i * 2 + 2] + cards[:3], 'kept': [0, 1]} for i in range(3)]
    return {'kind': 'RentalDraft', 'data': {'packs': packs, 'current': 3, 'complete': True, 'message': ''}}


def main():
    names = list(TYPES.values())
    httpd = fid.serve()
    try:
        with sync_playwright() as p:
            br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
            ctx = br.new_context(viewport={'width': 2400, 'height': 900}, device_scale_factor=2)
            ctx.add_init_script("localStorage.setItem('cobbletowers.guideSeen','1')")
            pg = ctx.new_page()
            pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
            time.sleep(1.0)
            pg.add_style_tag(content='.rcard{--cs:2.5 !important} .rcard *{animation:none !important;transition:none !important}')
            for b in range(3):
                sp = names[b * 6:b * 6 + 6]
                pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", fixture(sp))
                time.sleep(1.0)
                cards = pg.query_selector_all('.rcard')
                print(len(cards), 'cards for', sp)
                for el, s in zip(cards, sp):
                    t = TABLE[s]['t'][0]
                    pg.evaluate("e => e.scrollIntoView()", el)
                    med = el.query_selector('.rc-insp .rc-med')
                    med.screenshot(path=os.path.join(OUT, 'rd_med_%s.png' % t), omit_background=True)
                    gem = el.query_selector('.rc-tyb .rc-gem')
                    gem.screenshot(path=os.path.join(OUT, 'rd_gem_%s.png' % t), omit_background=True)
            # bases: mutate the first card
            pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", fixture(names[:6]))
            time.sleep(1.0)
            el = pg.query_selector_all('.rcard')[0]
            pg.evaluate("""e => { const q = s => e.querySelectorAll(s);
              q('.rc-sbox,.rc-nm,.rc-tyb,.rc-giant,.rc-rbanner,.rc-kmark,.rc-prism,.rc-holo,.rc-flash').forEach(n => n.style.visibility = 'hidden');
              q('.rc-med').forEach(n => n.style.visibility = 'hidden');
              const lv = e.querySelector('.rc-lvb'); [...lv.childNodes].forEach(n => { if (n.nodeType === 3) n.textContent = ''; }); }""", el)
            pg.evaluate("e => e.querySelectorAll('.rc-rframe,.rc-rbadge,.rc-rstars').forEach(n => n.style.visibility = 'hidden')", el)
            for t, c in COL.items():
                pg.evaluate("([e, c]) => e.querySelectorAll('.rc-front').forEach(f => f.style.setProperty('--acc', c))", [el, c])
                time.sleep(0.05)
                el.query_selector('.rc-front').screenshot(path=os.path.join(OUT, 'rd_%s.png' % t), omit_background=True)
                print(t)
            # rarity overlays: frame + badge + stars only
            pg.add_style_tag(content='html,body,body *:not([class*=rc-]){background:none !important;background-image:none !important;box-shadow:none !important;filter:none !important} .rc-face{box-shadow:none !important}')
            pg.evaluate("""e => { const f = e.querySelector('.rc-front'); f.style.background = 'transparent'; f.style.boxShadow = 'none';
              [...f.children].forEach(n => { n.style.visibility = 'hidden'; });
              e.querySelectorAll('.rc-rframe,.rc-rbadge,.rc-rstars').forEach(n => n.style.visibility = 'visible'); }""", el)
            for r, (n, st) in RAR.items():
                pg.evaluate("""([e, r, n, st]) => { e.querySelectorAll('[data-r]').forEach(x => x.setAttribute('data-r', r));
                  const b = e.querySelector('.rc-rbadge'); if (b) b.textContent = n; const s = e.querySelector('.rc-rstars'); if (s) s.textContent = '\u2605'.repeat(st); }""", [el, r, n, st])
                time.sleep(0.05)
                el.query_selector('.rc-front').screenshot(path=os.path.join(OUT, 'rd_r_%s.png' % r), omit_background=True)
            br.close()
    finally:
        httpd.shutdown()
    json.dump(TABLE, open(os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'rental_types.json')), 'w'))


main()
