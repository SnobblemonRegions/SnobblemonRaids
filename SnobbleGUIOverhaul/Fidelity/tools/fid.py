"""Visual-fidelity tooling: render the ORIGINAL WebGUI pages with the same data and steps as a native real-client run, then compare.

  python fid.py site                      build Fidelity/refsite (copy of the original pages + the injector)
  python fid.py cases <tag>               turn a native run (tests/shots_ui/<tag>/timeline.jsonl + the rig's ui_events.jsonl) into replay cases
  python fid.py shoot <tag> [filter]      render each case's original page in headless Chrome at 1280x720
  python fid.py compare <tag>             side-by-side + difference images and a score per screen
  python fid.py all <tag>                 cases + shoot + compare

The original page sources are only copied and served; nothing in them is executed outside the sandboxed headless browser.
"""
import http.server, json, os, re, shutil, socketserver, subprocess, sys, threading, time
from functools import partial

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
WEB = os.path.abspath(os.path.join(ROOT, '..', 'webgui_pages_src', 'extracted', 'webgui', 'web'))
TESTS = os.path.abspath(os.path.join(ROOT, '..', 'Two-Mod Rework', 'tests'))
SITE = os.path.join(ROOT, 'refsite')
CHROME = r'C:\Program Files\Google\Chrome\Application\chrome.exe'
PORT = 8765
EVENTS = os.path.join(TESTS, 'rig_compat', 'server', 'logs', 'ui_events.jsonl')


def page_id(rel):
    """Same rule as the compat jar's UrlMap."""
    parts = []
    for seg in rel.replace('\\', '/').split('/'):
        if not seg or seg in ('.', '..'):
            continue
        if seg.endswith('.html'):
            seg = seg[:-5]
        seg = re.sub(r'[^a-z0-9_-]', '', seg.lower())
        if not seg:
            continue
        if parts and parts[-1] == seg:
            continue
        parts.append(seg)
    if len(parts) > 1 and parts[-1] == 'index':
        parts.pop()
    return '_'.join(parts)


def pages():
    out = {}
    for d, _, fs in os.walk(WEB):
        for f in fs:
            if f.endswith('.html'):
                rel = os.path.relpath(os.path.join(d, f), WEB).replace('\\', '/')
                out[page_id(rel)] = rel
    return out


def site():
    if os.path.isdir(SITE):
        shutil.rmtree(SITE)
    shutil.copytree(WEB, SITE)
    shutil.copy(os.path.join(ROOT, 'tools', '_inject.js'), os.path.join(SITE, '_inject.js'))
    bg = os.path.join(ROOT, 'tools', '_bg.png')
    if os.path.exists(bg):
        shutil.copy(bg, os.path.join(SITE, '_bg.png'))
    os.makedirs(os.path.join(SITE, '_cases'), exist_ok=True)
    n = 0
    for d, _, fs in os.walk(SITE):
        for f in fs:
            if not f.endswith('.html'):
                continue
            p = os.path.join(d, f)
            s = open(p, encoding='utf8', errors='replace').read()
            tag = '<script src="/_inject.js"></script>'
            m = re.search(r'<head[^>]*>', s, re.I)
            s = s[:m.end()] + tag + s[m.end():] if m else tag + s
            open(p, 'w', encoding='utf8').write(s)
            n += 1
    print('site ready:', n, 'pages,', len(pages()), 'ids')


def load_jsonl(p):
    out = []
    if not os.path.exists(p):
        return out
    for line in open(p, encoding='utf8', errors='replace'):
        line = line.strip()
        if line:
            try:
                out.append(json.loads(line))
            except ValueError:
                pass
    return out


def cases(tag):
    shots = os.path.join(TESTS, 'shots_ui', tag)
    tl = load_jsonl(os.path.join(shots, 'timeline.jsonl'))
    if not tl:
        sys.exit('no timeline for ' + tag)
    t0, t1 = tl[0]['t'] - 120000, tl[-1]['t'] + 1000
    ev = [e for e in load_jsonl(EVENTS) if t0 <= e['t'] <= t1]
    items = sorted([('e', e['t'], e) for e in ev] + [('a', a['t'], a) for a in tl], key=lambda x: x[1])
    ids = pages()
    # native page ids that differ from the original file's id
    for nat, orig in (('rules', 'snobblerules_rules'), ('verify', 'snobblerules_verify'), ('goal', 'snobblemon_goal')):
        if orig in ids and nat not in ids:
            ids[nat] = ids[orig]
    cur, since = None, []
    manifest = []
    for kind, t, x in items:
        if kind == 'e':
            if x['event'] == '_open':
                cur, since = x['page'], [(t, x)]
                continue
            if x['page'] == cur:
                since.append((t, x))
            continue
        if x['kind'] != 'shot':
            if cur:
                since.append((t, x))
            continue
        if not cur or cur not in ids:
            continue
        steps, last, unmappable = [], None, []
        for st, s in since:
            dt = 400 if last is None else max(30, min(2500, st - last))
            last = st
            if 'event' in s:
                if s['event'] == '_open':
                    continue
                p = s['payload']
                if isinstance(p, dict) and list(p.keys()) == ['list'] and isinstance(p['list'], list):
                    p = p['list']
                steps.append({'dt': dt, 'event': s['event'], 'detail': p})
            elif s['kind'] == 'click':
                steps.append({'dt': dt, 'click': s['a']})
            elif s['kind'] in ('typetext', 'type'):
                steps.append({'dt': dt, 'typetext': s['b'] if s['kind'] == 'type' else s['a']})
            elif s['kind'] == 'key':
                steps.append({'dt': dt, 'key': s['a']})
            elif s['kind'] == 'scroll':
                # native scroll box id -> the original's scrolling element; wheel steps (negative = down) -> pixels
                sel = {'content': '#scroll', 'list': '#list', 'read': '#read', 'smain': '#main .scroll', 'sdetail': '#detail .scroll'}.get(s['a'], '#' + s['a'])
                steps.append({'dt': dt, 'scroll': sel, 'by': -float(s['b'] or 0) * 40})
            elif s['kind'] == 'clickxy':
                steps.append({'dt': dt, 'clickxy': [float(v) for v in s['a'].split(',')]})
            else:
                unmappable.append(s['kind'] + ' ' + s['a'])
        wait = 1500 if last is None else max(2500, min(4000, t - last))
        name = x['a']
        cid = tag + '__' + name
        json.dump(steps, open(os.path.join(SITE, '_cases', cid + '.json'), 'w', encoding='utf8'))
        manifest.append({'case': cid, 'shot': name, 'page': cur, 'url': '/' + ids[cur] + '?refcase=' + cid,
                         'budget': sum(s['dt'] for s in steps) + wait + 300, 'native': os.path.join(shots, name + '.png'),
                         'unmappable': unmappable, 'steps': len(steps)})
    os.makedirs(os.path.join(ROOT, 'cases'), exist_ok=True)
    json.dump(manifest, open(os.path.join(ROOT, 'cases', tag + '.json'), 'w'), indent=1)
    print('cases:', len(manifest), 'for', tag, '| pages:', sorted({m['page'] for m in manifest}))


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


def serve():
    httpd = socketserver.ThreadingTCPServer(('127.0.0.1', PORT), partial(Quiet, directory=SITE))
    httpd.daemon_threads = True
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return httpd


def shoot(tag, flt=''):
    """Real-time Playwright capture (headless Chrome's --screenshot with a virtual time budget caught windows mid-animation)."""
    from playwright.sync_api import sync_playwright
    man = json.load(open(os.path.join(ROOT, 'cases', tag + '.json')))
    out = os.path.join(ROOT, 'ref', tag)
    os.makedirs(out, exist_ok=True)
    httpd = serve()
    try:
        with sync_playwright() as p:
            br = p.chromium.launch(executable_path=CHROME, headless=True)
            for m in man:
                if flt and flt not in m['shot']:
                    continue
                png = os.path.join(out, m['shot'] + '.png')
                if os.path.exists(png):
                    os.remove(png)
                ctx = br.new_context(viewport={'width': 1280, 'height': 720}, device_scale_factor=1)   # fresh profile: no stale cache
                pg = ctx.new_page()
                pg.goto('http://127.0.0.1:%d%s' % (PORT, m['url']))
                time.sleep(m['budget'] / 1000 + 0.5)
                pg.screenshot(path=png)
                ctx.close()
                print('ref', m['shot'], 'ok' if os.path.exists(png) else 'MISSING')
            br.close()
    finally:
        httpd.shutdown()


def compare(tag):
    from PIL import Image, ImageChops, ImageDraw, ImageStat
    man = json.load(open(os.path.join(ROOT, 'cases', tag + '.json')))
    out = os.path.join(ROOT, 'compare', tag)
    os.makedirs(out, exist_ok=True)
    rows = []
    for m in man:
        rp = os.path.join(ROOT, 'ref', tag, m['shot'] + '.png')
        if not (os.path.exists(rp) and os.path.exists(m['native'])):
            continue
        a = Image.open(rp).convert('RGB').resize((1280, 720))
        b = Image.open(m['native']).convert('RGB').resize((1280, 720))
        d = ImageChops.difference(a, b)
        score = 100 - sum(ImageStat.Stat(d.convert('L')).mean) / 2.55
        canvas = Image.new('RGB', (1280 * 3, 760), (24, 24, 24))
        canvas.paste(a, (0, 40)); canvas.paste(b, (1280, 40)); canvas.paste(d.point(lambda v: min(255, v * 3)), (2560, 40))
        g = ImageDraw.Draw(canvas)
        g.text((10, 12), 'ORIGINAL WebGUI  - ' + m['page'] + ' / ' + m['shot'], fill=(255, 255, 255))
        g.text((1290, 12), 'NATIVE (real client)', fill=(255, 255, 255))
        g.text((2570, 12), 'DIFFERENCE x3   pixel similarity %.1f%%' % score, fill=(255, 255, 255))
        for attempt in range(8):
            try:
                canvas.save(os.path.join(out, m['shot'] + '.png'))
                break
            except OSError:
                time.sleep(1.0)
        rows.append((m['shot'], m['page'], round(score, 1), m['unmappable']))
    json.dump(rows, open(os.path.join(out, '_scores.json'), 'w'), indent=1)
    for r in rows:
        print('%-34s %-26s %5.1f%% %s' % (r[0], r[1], r[2], ('UNREPLAYED: ' + '; '.join(r[3])) if r[3] else ''))


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'site':
        site()
    elif cmd == 'cases':
        cases(sys.argv[2])
    elif cmd == 'shoot':
        shoot(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else '')
    elif cmd == 'compare':
        compare(sys.argv[2])
    elif cmd == 'all':
        cases(sys.argv[2]); shoot(sys.argv[2]); compare(sys.argv[2])
