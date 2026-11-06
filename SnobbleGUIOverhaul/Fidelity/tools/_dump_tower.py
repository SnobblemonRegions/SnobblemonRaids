import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import fid
from playwright.sync_api import sync_playwright
fx = json.load(open(sys.argv[1]))
out = sys.argv[2]
js = """() => { const r=[]; const walk=(e,d)=>{ const b=e.getBoundingClientRect(); if(b.width<1||b.height<1){for(const c of e.children) walk(c,d);return;} const cs=getComputedStyle(e);
 const own=[...e.childNodes].filter(n=>n.nodeType==3).map(n=>n.textContent.trim()).join(' ').slice(0,60);
 r.push({d,t:e.tagName.toLowerCase(),c:(e.getAttribute('class')||'').slice(0,50),x:Math.round(b.x*10)/10,y:Math.round(b.y*10)/10,w:Math.round(b.width*10)/10,h:Math.round(b.height*10)/10,
 bg:cs.backgroundColor,bgi:cs.backgroundImage.slice(0,60),col:cs.color,fs:cs.fontSize,ff:cs.fontFamily.slice(0,14),fw:cs.fontWeight,bd:cs.borderTopWidth+' '+cs.borderTopColor,rad:cs.borderTopLeftRadius,sh:cs.boxShadow.slice(0,70),pad:cs.padding,txt:own});
 for(const c of e.children) walk(c,d+1)}; walk(document.body,0); return r}"""
httpd = fid.serve()
with sync_playwright() as p:
    br = p.chromium.launch(executable_path=fid.CHROME, headless=True)
    ctx = br.new_context(viewport={'width': 1280, 'height': 720}); ctx.add_init_script("localStorage.setItem('cobbletowers.guideSeen','1')"); pg = ctx.new_page()
    pg.goto('http://127.0.0.1:%d/cobbletowers/index.html' % fid.PORT)
    time.sleep(1.2)
    pg.evaluate("d => window.dispatchEvent(new CustomEvent('webgui:tower', {detail: d}))", fx)
    time.sleep(1.5)
    json.dump(pg.evaluate(js), open(out, 'w'), indent=0)
    br.close()
httpd.shutdown()
