"""towers_ref2.py <out.png> <steps...> : drive the ORIGINAL cobbletowers page.
Step forms:  fx:<fixture path>   dispatch webgui:tower with that {kind,data} json
             click:<text>        click the first visible element whose own text equals <text>  (a[role=tab], button, ...)
             sel:<css>           click css selector
             wait:<ms>
Writes <out>.png and <out>.json (DOM dump like _dump_tower)."""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright

JS = """() => { const r=[]; const walk=(e,d)=>{ const b=e.getBoundingClientRect(); if(b.width<1||b.height<1){for(const c of e.children) walk(c,d);return;} const cs=getComputedStyle(e);
 const own=[...e.childNodes].filter(n=>n.nodeType==3).map(n=>n.textContent.trim()).join(' ').slice(0,80);
 r.push({d,t:e.tagName.toLowerCase(),c:(e.getAttribute('class')||'').slice(0,50),x:Math.round(b.x*10)/10,y:Math.round(b.y*10)/10,w:Math.round(b.width*10)/10,h:Math.round(b.height*10)/10,
 bg:cs.backgroundColor,bgi:cs.backgroundImage.slice(0,70),col:cs.color,fs:cs.fontSize,fw:cs.fontWeight,bd:cs.borderTopWidth+' '+cs.borderTopColor,rad:cs.borderTopLeftRadius,sh:cs.boxShadow.slice(0,70),pad:cs.padding,txt:own});
 for(const c of e.children) walk(c,d+1)}; walk(document.body,0); return r}"""


def main():
    out = sys.argv[1]
    httpd = fid.serve()
    try:
        with sync_playwright() as p:
            br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
            ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=1)
            ctx.add_init_script("localStorage.setItem('cobbletowers.guideSeen','1')")
            pg = ctx.new_page()
            pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
            time.sleep(1.0)
            for st in sys.argv[2:]:
                k, _, v = st.partition(':')
                if k == 'fx':
                    fx = json.load(open(v))
                    pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", fx)
                    time.sleep(0.6)
                elif k == 'click':
                    pg.get_by_text(v, exact=(not v.endswith("*"))).first.click() if not v.endswith("*") else pg.get_by_text(v[:-1]).first.click()
                    time.sleep(0.6)
                elif k == 'sel':
                    pg.click(v)
                    time.sleep(0.6)
                elif k == 'wait':
                    time.sleep(int(v) / 1000)
            pg.mouse.move(2, 2)
            time.sleep(1.0)
            pg.screenshot(path=out + '.png')
            json.dump(pg.evaluate(JS), open(out + '.json', 'w'), indent=0)
            br.close()
    finally:
        httpd.shutdown()
    print('ok', out)


main()
