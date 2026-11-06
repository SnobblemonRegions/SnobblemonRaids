"""Native QuestDex (questdex.json), a faithful copy of the original questdex/index.html (inline CSS + render functions).

The original fills the window: zoom = clamp(.7, 1.7, height / 780 * .92) and the dex is laid out at window / zoom CSS pixels ("zoomfit").
Grid: 54px top, flexible body (236px nav | cards | 370px detail), 40px bottom; the cards are repeat(auto-fill, minmax(230px, 1fr)).
Font: Consolas (-> Inconsolata, scaled to Consolas' widths). Art from the page's own CSS: tex:questdex/*, sprite questdex/screen (nine-slice).
The previous native version is gen_ui_questdex_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
SCREEN, SCREEN_L, SCREEN_D, INK, DIM = '#0d2a2b', '#164244', '#07191a', '#bff5d8', '#5a9a86'
BLUE, YELLOW, GREEN, RED = '#3ab4ff', '#ffd23a', '#3aff8c', '#ff5a5a'
F, FB = 'mono', 'mono_b'
_N = [0]


def nid(p='n'):
    _N[0] += 1
    return '%s%d' % (p, _N[0])


def T(text, size=16, color=INK, bold=False, **kw):
    d = {'type': 'label', 'text': text, 'font': FB if bold else F, 'size': size, 'color': color, 'shadow': False}
    d.update(kw)
    return d


def box(children=None, **kw):
    d = {'type': 'column', 'children': children or []}
    d.update(kw)
    return d


def row(children, **kw):
    d = {'type': 'row', 'children': children}
    d.update(kw)
    return d


def vmsg(action, **extra):
    d = {'channel': 'snobble_view', 'action': action}
    d.update(extra)
    return d


def btn(text, kind='', **kw):
    """.btn { linear-gradient(#f4f4f4, #bdbdbd); border: 2px solid #4a0a0f; border-radius: 8px; color: #222; font-weight: 700; padding: 6px 12px;
       box-shadow: 0 3px 0 #4a0a0f }  .go green  .sub yellow  .trk blue  :disabled { opacity: .45 }"""
    top = {'': '#f4f4f4', 'go': '#b9ffd4', 'sub': '#ffe9a0', 'trk': '#c8e9ff'}[kind]
    bot = {'': '#bdbdbd', 'go': GREEN, 'sub': YELLOW, 'trk': BLUE}[kind]
    d = {'type': 'button', 'text': text, 'font': FB, 'size': 16, 'color': '#222222', 'shadow': False, 'bg': 'gradv:%s,%s' % (top, bot), 'hover': 'gradv:%s,%s' % (top, bot),
         'disabled': 'gradv:%s73,%s73' % (top, bot), 'border': '#4a0a0f', 'bw': 2, 'radius': 8, 'bshadow': '0,3,#4a0a0f', 'pad': [8, 14, 8, 14], 'text_align': 'center',
         'id': nid('b')}
    d.update(kw)
    return d


def bar(value, **kw):
    """.bar { height: 7px; background: #031211; border: 1px solid var(--dim); border-radius: 5px }  > i { linear-gradient(90deg, blue, green) }"""
    d = box([{'type': 'bar', 'w': 'fill', 'h': 5, 'value': value, 'max': '100', 'fill': 'grad:#3ab4ff,#3aff8c', 'fill_bg': '#031211', 'radius': 4}],
            h=7, bg='#031211', border=DIM, bw=1, radius=5, pad=[1, 1, 1, 1])
    d.update(kw)
    return d


def glyph(item, g, size, img):
    """.glyph { 40x40; border-radius: 9px; background: var(--g); border: 2px solid rgba(255,255,255,.18) }  img 32x32 pixelated"""
    pad = (size - 4 - img) // 2
    return box([{'type': 'item', 'item': item, 'w': img, 'h': img}], w=size, h=size, bg=g, border='#FFFFFF2E', bw=2, radius=9, pad=[pad + 2, pad + 2, pad + 2, pad + 2])


def chip(text, bg, color):
    """.chip { padding: 1px 7px; border-radius: 999px; font-size: 11px; font-weight: 700; color: #03201a; letter-spacing: .04em }"""
    return T(text, 11, color, True, ls=0.44, bg=bg, radius=8, pad=[1, 7, 1, 7])


def nav():
    """.nav { padding: 8px; gap: 5px }  h4 { margin: 8px 4px 2px; 11px; ls .25em; dim }  .tab { padding: 6px 8px; gap 8px; radius 8px; border 1px }
       .tab.on { background: var(--screen-l); border-color: var(--c); box-shadow: inset 3px 0 0 var(--c) }  img 32x32  .nm 14px  .ct 12px dim, b yellow"""
    head = T('{t.label}', 11, DIM, ls=2.75, pad=[8, 4, 2, 4], **{'if': 't.isHead'})
    tab = {'type': 'button', 'w': 'fill', 'bg': '{t.bg}', 'hover': SCREEN_L, 'border': '{t.border}', 'bw': 1, 'radius': 8, 'pad': [7, 9, 7, 9], 'if': '!t.isHead',
           'id': nid('tab'), 'args': {'k': 'str:12'}, 'argv': {'k': 't.k'}, 'msg': vmsg('tab', k='{k}'), 'children': [
               box([], x=1, y=1, w=3, bottom=1, bg='{t.c}', **{'if': 't.on'}),
               row([{'type': 'image', 'pokemon': '{t.icon}', 'w': 32, 'h': 32}, T('{t.label}', 14, INK, w='fill', ellipsis=True),
                    T('{t.count}', 12, DIM, rich=True, bcolor=YELLOW)], w='fill', gap=8, cross='center')]}
    return {'type': 'scroll', 'id': 'navscroll', 'w': 236, 'h': 'fill', 'bg': 'sprite:questdex/screen', 'pad': [12, 12, 12, 12], 'thumb': DIM, 'children': [
        box([box([head, tab], w='fill', gap=0, repeat={'for': 'vm.nav', 'as': 't', 'limit': 16})], w='fill', gap=5)]}


def card():
    """.qc { background: var(--screen-d); border: 2px solid var(--screen-l); border-radius: 10px; padding: 9px 10px; gap: 5px; min-height: 92px }
       .qc.sel { border-color: #fff; box-shadow: 0 0 0 2px rgba(255,255,255,.25) }  .s-locked { opacity .55 }  .s-completed { opacity .8 }"""
    c = {'type': 'button', 'w': 'fill', 'h': 'fill', 'bg': SCREEN_D, 'hover': SCREEN_D, 'border': '{c.border}', 'bw': 2, 'radius': 10, 'ring': '{c.ring}',
         'pad': [11, 12, 11, 12], 'gap': 5, 'opacity': 'c.op', 'repeat': {'for': 'vm.cards', 'as': 'c', 'limit': 120},
         'id': nid('card'), 'args': {'k': 'str:64'}, 'argv': {'k': 'c.id'}, 'msg': vmsg('sel', k='{k}'), 'children': [box([
             row([glyph('{c.item}', '{c.g}', 40, 32), T('{c.name}', 14, '#ffffff', True, lh=16.8, wrap=True, lines=2, w='fill'),
                  T('◉', 12, RED, **{'if': 'c.pin'})], w='fill', gap=8, cross='center'),
             T('{c.sum}', 12, DIM, lh=15.6, wrap=True, lines=2, w='fill'),
             row([chip('{c.chip}', '{c.chipBg}', '{c.chipColor}'), bar('c.pct', w='fill', **{'if': 'c.showBar'}), T('{c.cat}', 10, DIM, **{'if': 'c.cat'})],
                 w='fill', gap=6, cross='center')], w='fill', h='fill', gap=5, min_h=0),
             T('NEW', 9, '#ffffff', True, ls=0.72, bg=RED, radius=6, pad=[1, 6, 1, 6], right=8, y=-7, **{'if': 'c.isNew'})]}
    c['hover'] = SCREEN_D
    return c


def center():
    """.listhead { padding: 10px 14px 8px; border-bottom: 1px solid var(--screen-l) }  h2 17px #fff (colour per tab) ls .08em  p 12px dim lh 1.35"""
    head = box([T('{vm.listName}', 17, '{vm.listColor}', True, ls=1.36),
                T('{vm.listDesc}', 12, DIM, lh=16.2, wrap=True, w='fill', pad=[2, 0, 0, 0]),
                T('{vm.slots}', 12, DIM, lh=16.2, rich=True, bcolor='#ffffff', pad=[2, 0, 0, 0], **{'if': 'vm.slots'})],
               w='fill', pad=[10, 14, 8, 14], border=SCREEN_L, bw=1, bsides='b', gap=0)
    banner = T('{vm.banner}', 11, '#ffb4b4', wrap=True, w='fill', rich=True, bcolor='#ffb4b4', pad=[10, 12, 0, 12], **{'if': 'vm.banner'})
    grid = {'type': 'grid', 'w': 'fill', 'min_col': 230, 'gap': 9, 'pad': [10, 12, 14, 12], 'children': [card()], 'if': '!vm.empty'}
    empty = T('{vm.emptyText}', 16, DIM, w='fill', text_align='center', pad=[40, 20, 40, 20], **{'if': 'vm.empty'})
    body = {'type': 'scroll', 'id': 'listscroll', 'w': 'fill', 'h': 'fill', 'thumb': DIM, 'children': [box([banner, grid, empty], w='fill', gap=0)]}
    return box([head, body], w='fill', h='fill', bg='sprite:questdex/screen', pad=[4, 4, 4, 4], gap=0)


def obj_rows(src, var):
    """.obj { margin: 5px 0 }  .t { flex; space-between }  last span yellow (green when done)  bar 100%"""
    return box([row([T('{%s.text}' % var, 13.5, INK, lh=19.6, wrap=True, w='fill'), T('{%s.amt}' % var, 13.5, '{%s.amtColor}' % var, lh=19.6)], w='fill', gap=8),
                bar('%s.pct' % var, w='fill')], w='fill', gap=2, pad=[5, 0, 5, 0], repeat={'for': src, 'as': var, 'limit': 8})


def sec(text, **kw):
    """.sec { margin: 12px 0 4px; font-size: 11px; letter-spacing: .25em; color: var(--dim) }"""
    return T(text, 11, DIM, ls=2.75, pad=[12, 0, 4, 0], **kw)


def detail():
    """.detail { padding: 14px; font-size: 13.5px; line-height: 1.45 }"""
    hd = row([glyph('{vm.d.item}', '{vm.d.g}', 56, 48),
              box([T('{vm.d.name}', 18, '#ffffff', True, lh=20.7, wrap=True, w='fill'), T('{vm.d.meta}', 12, '{vm.d.metaColor}', pad=[2, 0, 0, 0])], w='fill', gap=0)],
             w='fill', gap=10, cross='center', pad=[0, 0, 8, 0])
    chips = row([chip('{vm.d.chip}', '{vm.d.chipBg}', '{vm.d.chipColor}'), T('{vm.d.repeat}', 12, DIM)], gap=7, cross='center')
    step = {'type': 'column', 'w': 'fill', 'gap': 0, 'repeat': {'for': 'vm.d.steps', 'as': 's', 'limit': 12}, 'children': [
        row([T('{s.mk}', 13.5, '{s.mkColor}', w=16, text_align='center'),
             box([T('{s.name}', 13.5, INK, wrap=True, w='fill'), T('{s.meta}', 12, DIM, wrap=True, w='fill')], w='fill', gap=0),
             btn('Take', 'go', size=11, pad=[2, 8, 2, 8], bshadow='0,2,#4a0a0f', args={'k': 'str:64'}, argv={'k': 's.id'}, msg=vmsg('act', k="'accept'", id='{k}') if False else
                 {'channel': 'snobble_view', 'action': 'act', 'k': '{a}', 'id': '{k}'}, **{'if': 's.take'})],
            w='fill', gap=8, cross='center', pad=[5, 9, 5, 9], border='{s.border}', bw=1, radius=7, bg=SCREEN_D),
        box([obj_rows('s.objs', 'so')], w='fill', pad=[2, 0, 6, 24], **{'if': 's.cur'})]}
    step['children'][0]['children'][2]['args'] = {'a': 'str:12', 'k': 'str:64'}
    step['children'][0]['children'][2]['argv'] = {'a': "'accept'", 'k': 's.id'}
    req = {'type': 'button', 'w': 'fill', 'bg': SCREEN_D, 'hover': SCREEN_D, 'border': SCREEN_L, 'bw': 1, 'radius': 7, 'pad': [5, 9, 5, 9],
           'repeat': {'for': 'vm.d.reqs', 'as': 'r', 'limit': 8}, 'id': nid('req'), 'args': {'k': 'str:64'}, 'argv': {'k': 'r.id'}, 'msg': vmsg('goto', k='{k}'),
           'children': [row([T('{r.mk}', 13.5, '{r.mkColor}', w=16, text_align='center'), T('{r.name}', 13.5, INK, w='fill', ellipsis=True), T('{r.cat}', 12, DIM)],
                            w='fill', gap=8, cross='center')]}
    rew = T('{w.text}', 13.5, INK, bg=SCREEN_L, border=DIM, bw=1, radius=6, pad=[3, 9, 3, 9], repeat={'for': 'vm.d.rewards', 'as': 'w', 'limit': 12})

    def act_btn(text, kind, k, cond, enabled=None):
        b = btn(text, kind, w='fill', args={'a': 'str:12', 'id': 'str:64'}, argv={'a': "'%s'" % k, 'id': "''"}, msg={'channel': 'snobble_view', 'action': 'act', 'k': '{a}', 'id': '{id}'})
        b['if'] = cond
        if enabled:
            b['enabled'] = enabled
        return b
    actions = row([act_btn('Open the rules', 'go', 'rules', 'vm.d.showRules'),
                   act_btn('Accept', '{vm.d.acceptKind}' if False else '', 'accept', 'vm.d.showAcceptPlain', 'vm.d.acceptOk'),
                   act_btn('Accept', 'go', 'accept', 'vm.d.showAcceptGo', 'vm.d.acceptOk'),
                   act_btn('Turn in', 'sub', 'submit', 'vm.d.showSubmit', 'vm.d.submitOk'),
                   act_btn('{vm.d.trackText}', 'trk', 'track', 'vm.d.showTrack')], w='fill', gap=6, pad=[14, 0, 0, 0])
    body = box([hd, chips,
                sec('BRIEFING', **{'if': 'vm.d.hasBrief'}), T('{vm.d.brief}', 13.5, INK, lh=19.6, wrap=True, w='fill', **{'if': 'vm.d.hasBrief'}),
                sec('OBJECTIVES', **{'if': 'vm.d.single'}), box([obj_rows('vm.d.objs', 'o')], w='fill', **{'if': 'vm.d.single'}),
                sec('{vm.d.stepsHead}', **{'if': '!vm.d.single'}), box([step], w='fill', gap=3, **{'if': '!vm.d.single'}),
                sec('REQUIRES', **{'if': 'vm.d.hasReqs'}), box([req], w='fill', gap=3, **{'if': 'vm.d.hasReqs'}),
                sec('REWARDS'), row([rew], wrap=True, w='fill', gap=5),
                actions,
                T('{vm.d.note}', 11, '{vm.d.noteColor}', wrap=True, w='fill', pad=[8, 0, 0, 0], **{'if': 'vm.d.note'})], w='fill', gap=0, **{'if': 'vm.hasDetail'})
    empty = box([T('◉', 13.5, DIM, w='fill', text_align='center'), T('', 13.5, DIM), T('Select a quest to read its entry.', 13.5, DIM, w='fill', text_align='center')],
                w='fill', pad=[40, 20, 40, 20], gap=0, **{'if': '!vm.hasDetail'})
    return {'type': 'scroll', 'id': 'detscroll', 'w': 370, 'h': 'fill', 'bg': 'sprite:questdex/screen', 'pad': [18, 18, 18, 18], 'thumb': DIM,
            'children': [box([body, empty], w='fill')]}


def page():
    lens = box([{'type': 'image', 'texture': 'textures/gui/pages/questdex/lens.png', 'x': -10, 'y': -10, 'w': 64, 'h': 64}], w=44, h=44)

    def led(c, op=None):
        d = box([{'type': 'image', 'texture': 'textures/gui/pages/questdex/led_%s.png' % c, 'x': -6, 'y': -6, 'w': 24, 'h': 24}], w=12, h=12)
        if op:
            d['opacity'] = op
        return d
    trainer = box([T('{vm.trainer}', 13, INK, lh=16.9, rich=True, bcolor='#ffffff', wrap=True)], bg=SCREEN, border='#2a0508', bw=3, radius=8, pad=[7, 15, 7, 15])
    close = btn('✕ Close', close=True)
    top = row([lens, led('r'), led('y'), led('g', 'vm.ledOp'), T('Snobblemon Regions Quests', 19, '#ffffff', True, ls=4.18, upper=True, tshadow='0,2,#8c1018'),
               box([], w='fill'), trainer, close], w='fill', h=54, gap=12, cross='center')
    body = row([nav(), center(), detail()], w='fill', h='fill', gap=12)
    search = {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search all quests…  (name, objective, reward)', 'w': 'fill', 'h': 40, 'max_len': 40,
              'bg': '#0b1f20', 'border': '#4a0a0f', 'bw': 2, 'radius': 8, 'color': INK, 'font': F, 'size': 16, 'pad': [0, 12, 0, 12], 'debounce_ms': 250,
              'args': {'q': 'str:40'}, 'argv': {'q': '$q'}, 'msg': vmsg('q', q='{q}')}
    tracker = row([T('◉', 12, RED, **{'if': 'vm.tracking'}), T('{vm.trackName}', 13, '#ffffff', ellipsis=True, **{'if': 'vm.tracking'}),
                   bar('vm.trackPct', w=90, **{'if': 'vm.tracking'}), T('{vm.trackText}', 13, INK, **{'if': 'vm.tracking'}),
                   T('No quest tracked', 13, DIM, **{'if': '!vm.tracking'})],
                  h=40, bg=SCREEN, border='#2a0508', bw=3, radius=8, pad=[0, 15, 0, 15], gap=8, cross='center')
    bottom = row([{'type': 'image', 'texture': 'textures/gui/pages/questdex/dpad.png', 'w': 30, 'h': 30}, search, tracker], w='fill', h=40, gap=8, cross='center')
    tick = {'type': 'spacer', 'id': 'tick', 'x': 0, 'y': 0, 'w': 0, 'h': 0, 'every_ms': 1000, 'args': {}, 'msg': vmsg('tick')}
    ping = {'type': 'spacer', 'id': 'ping', 'x': 0, 'y': 0, 'w': 0, 'h': 0, 'every_ms': 4000, 'args': {}, 'msg': {'channel': 'questdex', 'kind': 'ping'}}
    root = {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': 'tex:questdex/dex.png', 'pad': [14, 14, 14, 14], 'gap': 10, 'children': [top, body, bottom, tick, ping]}
    return {'v': 2, 'id': 'questdex', 'channel': 'questdex', 'hello': 'hello', 'bye': True, 'title': 'QuestDex', 'width': 1507, 'height': 848,
            'zoomfit': [780, 0.92, 0.7, 1.7],
            'toast': {'bg': SCREEN, 'border': GREEN, 'color': '#ffffff', 'font': FB, 'size': 16, 'y': 12, 'from': -80, 'radius': 10, 'pad_x': 18, 'pad_y': 8},
            'root': root}


d = page()
with open(os.path.join(OUT, 'questdex.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote questdex (%d bytes)' % os.path.getsize(os.path.join(OUT, 'questdex.json')))
