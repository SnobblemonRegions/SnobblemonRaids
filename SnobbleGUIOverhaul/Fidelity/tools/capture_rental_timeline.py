"""Real-time timeline of the ORIGINAL rental draft after clicking the pack: screenshots with ms-since-click stamps -> Fidelity/rental_anim/orig/t<ms>.png"""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright
OUT = os.path.join(fid.ROOT, 'rental_anim', 'orig_cdp2')
os.makedirs(OUT, exist_ok=True)
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
        pg.get_by_text('Continue with effects', exact=True).first.click()
        time.sleep(0.8)
        pg.mouse.move(4, 4)
        import base64
        cdp = ctx.new_cdp_session(pg)
        got = []
        def on(f):
            got.append((f['metadata']['timestamp'], f['data']))
            cdp.send('Page.screencastFrameAck', {'sessionId': f['sessionId']})
        cdp.on('Page.screencastFrame', on)
        bb = pg.query_selector('button.pack').bounding_box()
        cdp.send('Page.startScreencast', {'format': 'png', 'everyNthFrame': 1})
        pg.wait_for_timeout(300)
        tclick = time.time()
        pg.mouse.click(bb['x'] + bb['width'] / 2, bb['y'] + bb['height'] / 2)
        pg.mouse.move(4, 4)
        pg.wait_for_timeout(6500)
        cdp.send('Page.stopScreencast')
        for ts, d in got:
            ms = round((ts - tclick) * 1000)
            open(os.path.join(OUT, ('t%05d.png' % ms) if ms >= 0 else ('tm%05d.png' % -ms)), 'wb').write(base64.b64decode(d))
        print('frames', len(got))
        br.close()
finally:
    httpd.shutdown()
