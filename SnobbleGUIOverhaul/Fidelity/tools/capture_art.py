"""Renders decorative elements of the ORIGINAL pages (their own CSS, unchanged) to transparent PNGs, for the server's resource pack.

Used where the CSS draws something Minecraft cannot draw natively with equal fidelity (layered gradients, patterns, inset frames,
shaped folders). Each job is an HTML snippet that uses the page's stylesheet; the result is saved under
snobblemon-server/src/main/resources/assets/snobblemon_regions/textures/gui/pages/<page>/<name>.png

  python capture_art.py [page]
"""
import os, subprocess, sys, time, json
sys.path.insert(0, os.path.dirname(__file__))
import fid

OUT = os.path.abspath(os.path.join(fid.ROOT, '..', 'Two-Mod Rework', 'snobblemon-server', 'src', 'main', 'resources', 'assets',
                                   'snobblemon_regions', 'textures', 'gui', 'pages'))

# page -> list of (name, css hrefs relative to the page dir, body html, width, height)
Q = ['inline:index.html']
G = ['inline:shop.html']
TK = '<div class="tick1"></div><div class="tick2"></div>'
AUC_THEMES = {'gray': ('#2c3943', '#0b0e11', '#5d7280'), 'violet': ('#3a2a86', '#090618', '#6a4fd0'), 'emerald': ('#1f6a55', '#061210', '#2f9a7c'),
              'crimson': ('#6a1f2e', '#10050a', '#b0384c'), 'ocean': ('#1d5a9c', '#050c18', '#2e7fc4'), 'amber': ('#6a4d1a', '#100a03', '#b98a32')}
C = ['inline:crate.html']
JOBS = {
    'snobblecrates': [
        ('lid', C, '<div class="lid" style="width:656px;height:184px"><div class="strap a"></div><div class="strap b"></div></div>', 656, 184),
        ('body', C, '<div class="body" style="width:656px;height:286px;display:block"></div>', 656, 286),
        ('slot', C, '<div class="slot" style="width:99px;height:82px"></div>', 99, 82),
        ('well_info', C, '<div class="well" style="width:374px;height:74px"></div>', 374, 74),
        ('well_pager', C, '<div class="well" style="width:120px;height:74px"></div>', 120, 74),
        ('well_side', C, '<div class="well" style="width:150px;height:74px"></div>', 150, 74),
        ('close', C, '<button class="close" style="position:absolute;left:0;top:0;right:auto">&times;</button>', 30, 30),
        ('arrow', C, '<button class="arrow" style="position:absolute;left:0;top:0"></button>', 26, 34),
        ('arrow_off', C, '<button class="arrow" disabled style="position:absolute;left:0;top:0"></button>', 26, 34),
        ('sz', C, '<button class="sz" style="position:absolute;left:0;top:0"></button>', 22, 22),
        ('latch_l', C, '<div class="latch l" style="left:0;top:0"></div>', 22, 62),
        ('latch_r', C, '<div class="latch r" style="left:0;top:0;right:auto"></div>', 22, 62),
        ('bar', C, '<div class="bar" style="width:508px;box-sizing:border-box"></div>', 508, 16),
    ],
    'auction': [('art_' + t, ['style.css'], '<style>:root{--spot:%s;--input:%s;--edge:%s}</style><div class="card" style="all:unset"><div class="art" '
                 'style="position:absolute;left:0;top:0;width:204px;height:126px;display:block"></div></div>' % v, 204, 126) for t, v in AUC_THEMES.items()],
    'snobblegems': [
        ('bg', G, '<div id="bg"></div><div id="frame" class="abs"></div><div id="subline" class="abs"></div>', 2000, 1125),
        ('left', G, '<div id="left" class="cut" style="left:0;top:0">' + TK + '</div>', 450, 827),
        ('right', G, '<div id="right" class="cut" style="left:0;top:0">' + TK + '<div id="beam"></div><div id="floor"></div></div>', 1260, 666),
        ('chip', G, '<div id="chip" class="cut" style="left:0;top:0">' + TK + '</div>', 395, 62),
        ('card', G, '<div class="cut card"><div class="tk"></div></div>', 198, 198),
        ('card_hover', G, '<div class="cut card hover"><div class="tk"></div></div>', 198, 198),
        ('card_sel', G, '<div class="cut card sel"><div class="tk"></div></div>', 198, 198),
        ('buy', G, '<div class="btn" style="left:0;top:0;width:250px;height:72px"></div>', 250, 72),
        ('buy_off', G, '<div class="btn off" style="left:0;top:0;width:250px;height:72px"></div>', 250, 72),
        ('close', G, '<div class="btn" style="left:0;top:0;width:564px;height:120px"></div>', 564, 120),
        ('mok', G, '<div class="btn" style="left:0;top:0;width:320px;height:70px"></div>', 320, 70),
        ('mcancel', G, '<div class="btn ghost" style="left:0;top:0;width:320px;height:70px"></div>', 320, 70),
        ('modal', G, '<div id="modal" class="cut" style="left:0;top:0">' + TK + '</div>', 760, 560),
        ('toast_ok', G, '<div id="toast" class="cut on ok" style="left:0;top:0"></div>', 800, 56),
        ('toast_bad', G, '<div id="toast" class="cut on bad" style="left:0;top:0"></div>', 800, 56),
        ('banner', G, '<div id="banner" class="abs" style="display:block;left:0;top:0"></div>', 1200, 260),
    ],
    'questdex': [
        ('dex', Q, '<div class="dex" style="zoom:1;width:1507px;height:848px"></div>', 1507, 848),
        ('screen', Q, '<div class="screen" style="width:240px;height:240px"></div>', 240, 240, 60),
        ('lens', Q, '<div class="lens" style="position:absolute;left:10px;top:10px"></div>', 64, 64),
        ('led_r', Q, '<span class="led r" style="position:absolute;left:6px;top:6px"></span>', 24, 24),
        ('led_y', Q, '<span class="led y" style="position:absolute;left:6px;top:6px"></span>', 24, 24),
        ('led_g', Q, '<span class="led g" style="position:absolute;left:6px;top:6px"></span>', 24, 24),
        ('dpad', Q, '<div class="dpad"></div>', 30, 30),
    ],
    'cases': [
        ('win', ['style.css'], '<div id="win" style="left:0;top:0;transform:none"></div>', 1500, 837),
        ('desk', ['style.css'], '<div id="desk" style="left:0;top:0"></div>', 1460, 664),
        # folders: 440x250 body + 19px tab above + 4/5 shadow; drawn at (6, 24) inside a 452x284 canvas
        ('folder', ['style.css'], '<button class="folder" style="left:6px;top:24px;--rot:0deg"><div class="clip"></div></button>', 452, 284),
        ('folder_hover', ['style.css'], '<button class="folder" style="left:6px;top:24px;--rot:0deg;background:#f0d589;box-shadow:6px 10px 0 rgba(0,0,0,.26)"><div class="clip"></div></button>', 452, 284),
        # pinned notes 440x250, shadow 4/5, at (6, 6) in 452x264
    ] + [('note_' + k, ['style.css'], '<button class="note %s" style="left:6px;top:6px;--rot:0deg"><span class="pin"></span></button>' % c, 452, 264)
         for k, c in (('new', ''), ('con', 'n-con'), ('plan', 'n-plan'), ('done', 'n-done'), ('dec', 'n-dec'))],
}

# ---- wardrobe (style.css): chamfered panels, cards per rank/state, glows, button sprites
W = ['style.css']
WHV = ('<style>.card.hv{background:rgba(54,37,79,.95);box-shadow:inset 0 0 26px rgba(var(--rgb),.18)}'
       '.card.hv:not(.worn)::before{background:linear-gradient(var(--rc),var(--rc)) 10px 0/24px 2px no-repeat,linear-gradient(var(--rc),var(--rc)) 0 10px/2px 16px no-repeat}</style>')
WRGB = ['230,207,250', '77,255,176', '95,168,255', '179,107,255', '255,210,74', '255,79,224']
WRC = ['#e6cffa', '#4dffb0', '#5fa8ff', '#b36bff', '#ffd24a', '#ff4fe0']
NOPS = '<style>.x::before,.x::after{display:none!important}</style>'


def _wcard(r, worn, hv):
    cls = 'card panel' + (' rare' if r >= 2 else '') + (' worn' if worn else '') + (' hv' if hv else '')
    return WHV + '<div class="%s" data-r="%d" style="position:absolute;left:0;top:0"><div class="art"></div><div class="foot"></div></div>' % (cls, r)


def _wbtn(cls, w, h, extra=''):
    return '<button class="%s" style="position:absolute;left:0;top:0;width:%dpx;height:%dpx;%s"></button>' % (cls, w, h, extra)


JOBS['wardrobe'] = [
    ('bg', W, '<style>#stage::before{display:none!important}</style>', 1600, 900),
    ('grid', W, '<style>#stage{background:none!important;width:1680px!important;height:980px!important}#stage::before{inset:0!important}</style>', 1680, 980),
    ('rail', W, '<aside id="rail" class="panel" style="left:0;top:0"></aside>', 300, 660),
    ('meter', W, '<div class="meter panel" style="position:absolute;left:0;top:0"></div>', 150, 56),
    ('detail', W, '<section id="detail" class="panel" style="position:absolute;left:0;top:0"></section>', 1000, 620),
    ('dbar', W, '<div id="dBar" class="panel" style="left:0;top:0;right:auto;bottom:auto;width:930px"></div>', 930, 110),
    ('shine', W, '<div class="card" style="position:absolute;left:0;top:0;width:160px;height:320px;background:none;overflow:visible"><i class="shine" style="left:57px"></i></div>', 160, 320),
] + [('card_r%d%s%s' % (r, '_worn' if wn else '', '_hv' if hv else ''), W, _wcard(r, wn, hv), 270, 320)
     for r in range(6) for wn in (False, True) for hv in (False, True)] + [
    ('pulse_r%d' % r, W, NOPS + '<div class="card panel x" data-r="%d" style="position:absolute;left:0;top:0;background:none;border-color:transparent;box-shadow:inset 0 0 28px rgba(%s,.3)"></div>' % (r, WRGB[r]), 270, 320)
    for r in (4, 5)] + [
    ('glow_r%d' % r, W, '<style>#dArt .glow::after{display:none}</style><div id="detail" data-r="%d" style="position:absolute;left:0;top:0;width:320px;height:320px;background:none">'
     '<div id="dArt" style="left:0;top:0;width:320px;height:320px"><div class="glow" style="left:0;top:0"></div></div></div>' % r, 320, 320) for r in range(6)] + [
    ('ring_r%d' % r, W, '<div style="position:absolute;left:0;top:0;width:320px;height:320px;border-radius:50%%;border:2px solid %s;box-sizing:border-box"></div>' % WRC[r], 320, 320) for r in range(6)] + [
    ('tab', W, '<nav id="tabs" style="left:0;top:0"><button class="tab" style="width:120px"></button></nav>', 120, 44, 12),
    ('tab_hover', W, '<nav id="tabs" style="left:0;top:0"><button class="tab" style="width:120px;background:var(--fill2)"></button></nav>', 120, 44, 12),
    ('tab_on', W, '<nav id="tabs" style="left:0;top:0"><button class="tab on" style="width:120px"></button></nav>', 120, 44, 12),
    ('ghost', W, _wbtn('btn ghost', 120, 38), 120, 38, 14),
    ('ghost_hover', W, _wbtn('btn ghost', 120, 38, 'background:var(--fill2)'), 120, 38, 14),
    ('danger', W, _wbtn('btn ghost danger', 120, 38), 120, 38, 14),
    ('danger_hover', W, _wbtn('btn ghost danger', 120, 38, 'background:var(--fill2)'), 120, 38, 14),
    ('solid', W, _wbtn('btn solid', 120, 46), 120, 46, 14),
    ('solid_hover', W, _wbtn('btn solid', 120, 46, 'background:#fff'), 120, 46, 14),
    ('green', W, _wbtn('btn green', 120, 46), 120, 46, 14),
    ('go', W, '<div class="card" style="position:absolute;left:0;top:0;width:60px;height:30px;background:none"><button class="go" style="width:60px"></button></div>', 60, 30, 4),
    ('go_hover', W, '<div class="card" style="position:absolute;left:0;top:0;width:60px;height:30px;background:none"><button class="go" style="width:60px;background:var(--acc);box-shadow:none"></button></div>', 60, 30, 4),
    ('go_take', W, '<div class="card" style="position:absolute;left:0;top:0;width:60px;height:30px;background:none"><button class="go take" style="width:60px"></button></div>', 60, 30, 4),
    ('go_take_hover', W, '<div class="card" style="position:absolute;left:0;top:0;width:60px;height:30px;background:none"><button class="go take" style="width:60px;background:var(--green);box-shadow:none"></button></div>', 60, 30, 4),
]

JOBS['snobblerules'] = [
    ('bg', ['inline:rules.html'], '<div style="position:absolute;inset:0;background:linear-gradient(115deg, #0a1a18 0%, #0b1019 34%, #0d1224 66%, #17102b 100%)"></div>', 1507, 848),
]

JOBS['snobblemon'] = [
    # goal.html: the window backdrop (at the 16:9 page size, 711px tall) and the 1000x640 panel
    ('goal_bg', ['inline:goal.html'], '<div style="position:absolute;inset:0;background:radial-gradient(900px 520px at 18% 12%, #2d5aa6 0%, rgba(45,90,166,0) 60%), radial-gradient(800px 500px at 85% 90%, #6b3fa8 0%, rgba(107,63,168,0) 60%), linear-gradient(180deg, #0c1d38 0%, #0a1628 100%)"></div>', 1264, 711),
    ('goal_panel', ['inline:goal.html'], '<div class="panel" style="position:absolute;left:30px;top:20px;box-shadow:0 10px 40px rgba(0,0,0,.55)"></div>', 1060, 720),
]

# ---- cobbleraids (shared/theme.css): the 16:9 stage, the header rule, the hub's line icons. A 1600px wide stage makes 1cqw = 16px.
RT = ['shared/theme.css']
RSTAGE = '<div class="stage" style="position:absolute;left:0;top:0;transform:none;width:1600px;height:900px;box-shadow:none">%s</div>'
HUB_SVG = {
    'shop': '<path d="M4 8h16l-1.4 12H5.4L4 8zm4-1a4 4 0 018 0" fill="none" stroke="currentColor" stroke-width="1.8"/>',
    'trophy': '<path d="M7 4h10v5a5 5 0 01-10 0V4zM7 6H3.5v1.5A3.5 3.5 0 007 11M17 6h3.5v1.5A3.5 3.5 0 0117 11M12 14v4M8 20h8" fill="none" stroke="currentColor" stroke-width="1.8"/>',
    'legends': '<path d="M3 18L2 7l5 4 5-7 5 7 5-4-1 11H3z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>',
    'leaders': '<path d="M4 20V12h4v8M10 20V4h4v16M16 20V9h4v11" fill="none" stroke="currentColor" stroke-width="1.8"/>',
}
JOBS['cobbleraids'] = [
    ('stage', RT, RSTAGE % '', 1600, 900),
    ('rule', RT, RSTAGE % '<i class="sp" style="position:absolute;left:0;top:0;width:800px;display:block"></i>', 800, 1),
] + [('icon_' + k, RT + ['hub/hub.css'], (RSTAGE % ('<div class="tile" style="position:absolute;left:0;top:0;width:200px;height:200px"><svg viewBox="0 0 24 24" '
      'style="left:0;top:0">%s</svg></div>' % v)).replace('style="position:absolute;left:0;top:0;transform:none;width:1600px;height:900px;box-shadow:none"',
      'style="position:absolute;left:0;top:0;transform:none;width:1600px;height:900px;box-shadow:none;background:none;border:0"'), 71, 71) for k, v in HUB_SVG.items()]
JOBS['cobbleraids'] = [(j[0], j[1], '<style>.stage::before{display:none}</style>' + j[2] if j[0].startswith('icon_') or j[0] == 'rule' else j[2]) + tuple(j[3:])
                       for j in JOBS['cobbleraids']]

BALL = ('<svg viewBox="0 0 16 16" width="144" height="144"><circle cx="8" cy="8" r="7" fill="#eafbff" stroke="#001a30" stroke-width="1"/>'
        '<path d="M1 8a7 7 0 0114 0z" fill="#ff5a6a" stroke="#001a30" stroke-width="1"/><path d="M1 8h14" stroke="#001a30" stroke-width="1.4"/>'
        '<circle cx="8" cy="8" r="2.2" fill="#eafbff" stroke="#001a30" stroke-width="1.2"/></svg>')
SKULL = ('<svg viewBox="0 0 16 16" width="29" height="29" fill="#ff5555"><path d="M8 1C4.4 1 2 3.4 2 6.6c0 1.8.8 3 1.9 3.8V13h2v-1.5h1V13h2v-1.5h1V13h2v-2.6c1.1-.8 '
         '1.9-2 1.9-3.8C14 3.4 11.6 1 8 1zM5.5 8.6a1.4 1.4 0 110-2.8 1.4 1.4 0 010 2.8zm5 0a1.4 1.4 0 110-2.8 1.4 1.4 0 010 2.8z"/></svg>')
NOGRID = '<style>.stage::before{display:none}.stage{background:none!important;border:0!important}</style>'
JOBS['cobbleraids'] += [
    ('beam', RT + ['reveal/reveal.css'], NOGRID + RSTAGE % '<div id="chamber" style="left:0;top:0;width:1072px;height:567px;right:auto;bottom:auto;background:none;border:0;clip-path:none"><div id="beam"></div></div>', 1072, 567),
    ('cap_chamber', RT + ['capture/capture.css'], NOGRID + RSTAGE % '<section id="chamber" style="left:0;top:0;width:1008px;height:482px;right:auto;bottom:auto;clip-path:none;border:0"></section>', 1008, 482),
    ('ball', RT, NOGRID + RSTAGE % BALL, 144, 144),
    ('skull', RT, NOGRID + RSTAGE % SKULL, 29, 29),
]

# ---- ascensionlib (ascension.css): the scanlines over the tablet's screen and the header's ball + LEDs
AC = ['ascension.css']
JOBS['ascensionlib'] = [
    ('scan', AC, '<div class="frame" style="position:absolute;left:0;top:0;transform:none;border:0;width:1004px;height:564px;background:none"></div>', 1004, 564),
    ('headdeco', AC, '<div class="head" style="position:absolute;left:0;top:0;width:200px;height:40px;background:none;border:0;padding:12px 14px">'
                     '<span style="display:none"></span></div><style>.head{display:block}.head::before{display:block}</style>', 120, 40),
]

CLR = '<style>#stage{background:none!important}#stage::before{display:none!important}</style>'
JOBS['wardrobe'] = [j if j[0] in ('bg', 'grid') else (j[0], j[1], CLR + j[2]) + tuple(j[3:]) for j in JOBS['wardrobe']]


def html(page, css, body, w, h):
    links = ''
    for c in css:
        if c.startswith('inline:'):
            # the page keeps its CSS inline: reuse that <style> block as is
            src = open(os.path.join(fid.WEB, page, c[7:]), encoding='utf8', errors='replace').read()
            a, b = src.find('<style>'), src.find('</style>')
            links += src[a:b + 8]
        else:
            links += '<link rel="stylesheet" href="/%s/%s">' % (page, c)
    return ('<!doctype html><html><head><meta charset="utf-8">%s<style>html,body{margin:0;background:transparent!important;overflow:hidden;width:%dpx;height:%dpx}'
            '#stage{position:absolute;inset:0}*{animation:none!important;transition:none!important}</style></head><body><div id="stage">%s</div></body></html>') % (links, w, h, body)


def run(only=''):
    httpd = fid.serve()
    prof = os.path.join(fid.ROOT, '_chrome_profile', 'art')
    try:
        for page, jobs in JOBS.items():
            if only and only != page:
                continue
            od = os.path.join(OUT, page)
            os.makedirs(od, exist_ok=True)
            for job in jobs:
                name, css, body, w, h = job[:5]
                nine = job[5] if len(job) > 5 else None
                tmp = os.path.join(fid.SITE, page, '_art_%s.html' % name)
                open(tmp, 'w', encoding='utf8').write(html(page, css, body, w, h))
                png = os.path.join(od, name + '.png')
                if nine is not None:
                    # a GUI-atlas sprite drawn nine-sliced (edges and centre tile, so patterns stay crisp at any size)
                    sd = os.path.join(OUT, '..', 'sprites', page)
                    os.makedirs(sd, exist_ok=True)
                    png = os.path.join(sd, name + '.png')
                    json.dump({'gui': {'scaling': {'type': 'nine_slice', 'width': w, 'height': h, 'border': nine}}}, open(png + '.mcmeta', 'w'))
                if os.path.exists(png):
                    os.remove(png)
                cmd = [fid.CHROME, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1', '--window-size=%d,%d' % (w, h),
                       '--default-background-color=00000000', '--user-data-dir=' + os.path.join(prof, page + '_' + name), '--no-first-run',
                       '--virtual-time-budget=800', '--screenshot=' + png, 'http://127.0.0.1:%d/%s/_art_%s.html' % (fid.PORT, page, name)]
                subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
                end = time.time() + 40
                while not os.path.exists(png) and time.time() < end:
                    time.sleep(0.3)
                time.sleep(0.3)
                print(page, name, '%dx%d' % (w, h), 'ok' if os.path.exists(png) else 'MISSING')
    finally:
        httpd.shutdown()


if __name__ == '__main__':
    run(sys.argv[1] if len(sys.argv) > 1 else '')
