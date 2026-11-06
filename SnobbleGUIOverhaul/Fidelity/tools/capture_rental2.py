"""rd_pack.png (sealed pack) and rd_back.png (card back) from the ORIGINAL page."""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright
OUT = os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets', 'snobblemon_regions', 'textures', 'gui', 'pages', 'cobbletowers'))
S = r'C:\Users\allie\AppData\Local\Temp\claude\G--Claude\65e21b71-5195-4c68-b389-e8e880ace018\scratchpad'
httpd = fid.serve()
try:
    with sync_playwright() as p:
        br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
        ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=1)
        ctx.add_init_script("localStorage.setItem('cobbletowers.guideSeen','1')")
        pg = ctx.new_page()
        pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
        time.sleep(1)
        pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", json.load(open(S + r'\rd0.json')))
        time.sleep(0.6)
        pg.get_by_text('Turn on calm effects and continue', exact=True).first.click()
        time.sleep(0.8)
        pg.mouse.move(2, 2)
        b = pg.query_selector('button.pack')
        print(b.bounding_box())
        b.screenshot(path=os.path.join(OUT, 'rd_pack.png'), omit_background=True)
        pg.add_style_tag(content='.dstage2 > *{visibility:hidden !important}')
        pg.query_selector('.dtable').screenshot(path=os.path.join(OUT, 'rd_table.png'), omit_background=True)
        pg.add_style_tag(content='.dstage2 > *{visibility:visible !important}')
        pg.click('button.pack')
        time.sleep(9)
        pg.add_style_tag(content='.rcard{--cs:2.5 !important} .rcard *{animation:none !important;transition:none !important}')
        pg.evaluate("() => document.querySelectorAll('.rc-inner').forEach(i => i.classList.remove('rc-flipped'))")
        time.sleep(0.5)
        pg.set_viewport_size({'width': 2400, 'height': 1000})
        time.sleep(0.5)
        e = pg.query_selector('.rcard .rc-back')
        pg.evaluate('e => e.scrollIntoView()', e)
        print(e.bounding_box())
        e.screenshot(path=os.path.join(OUT, 'rd_back.png'), omit_background=True)
        br.close()
finally:
    httpd.shutdown()
