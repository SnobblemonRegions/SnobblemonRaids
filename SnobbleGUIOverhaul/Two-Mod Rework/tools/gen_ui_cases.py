"""Native Regions Cases page (cases.json), a faithful copy of the original web page (cases/style.css + app.js).

Units are the original's CSS pixels: the window is 1500x837 and is scaled like the original's fit() (min(w/1700, h/960)).
Positions/sizes/colours/fonts are taken from style.css and the inline styles in app.js (comments name the CSS rule).
Art the CSS draws with gradients/patterns (wood window, lined desk, manila folders, pinned notes) is rendered from that same CSS
by Fidelity/tools/capture_art.py and shipped in the server's pack (tex:cases/...). The previous native version is gen_ui_cases_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')

# :root
WOOD2, PAPER, PAPER2, MANILA, EDGE = '#432a17', '#efe2bd', '#fffdf4', '#e7c97a', '#8a6a28'
INK, INK2, TAB, BTN, CREAM = '#2b2118', '#5b4310', '#c9b078', '#3a2a17', '#f3e6c8'
GREEN, AMBER, RED, GRAY, BLUE = '#2f7d3a', '#b87500', '#b3261e', '#6b6b6b', '#2b5fa8'
BROWN, DIM = '#7a5f2e', '#8a7a52'
TYPE, TYPE_B, HAND = 'courier', 'courier_b', 'hand'     # --type: "Courier New" (-> Courier Prime), --hand: "Segoe Print" (-> Kalam)

ARGS = {'a': 'str:16', 'id': 'int:0:2000000000', 'v': 'str:64', 't': 'str:500', 'n': 'str:64', 'd': 'str:16'}
MSG = {'channel': 'snobble_view', 'action': 'act', 'a': '{a}', 'id': '{id}', 'v': '{v}', 't': '{t}', 'n': '{n}', 'd': '{d}'}
_N = [0]


def nid(p='n'):
    _N[0] += 1
    return '%s%d' % (p, _N[0])


def T(text, size=16, color=INK, bold=False, font=None, **kw):
    """A text run in the page font (Courier New -> Courier Prime)."""
    d = {'type': 'label', 'text': text, 'font': font or (TYPE_B if bold else TYPE), 'size': size, 'color': color, 'shadow': False}
    d.update(kw)
    return d


def H(text, **kw):
    """.h { font-size: 13px; font-weight: 700; letter-spacing: 2px; color: #7a5f2e; margin-bottom: 8px }"""
    return T(text, 13, BROWN, True, ls=2, **kw)


def box(children=None, **kw):
    d = {'type': 'column', 'children': children or []}
    d.update(kw)
    return d


def row(children, **kw):
    d = {'type': 'row', 'children': children}
    d.update(kw)
    return d


def act(a, id='0', v="''", t="''", n="''", d="''"):
    aexpr = a[1:] if a.startswith('@') else "'%s'" % a
    return {'id': nid('b'), 'args': dict(ARGS), 'argv': {'a': aexpr, 'id': id, 'v': v, 't': t, 'n': n, 'd': d}, 'msg': dict(MSG)}


# .btn and its colour classes: (background, text, hover)
BTNS = {'': (BTN, CREAM, '#55391e'), 'red': (RED, CREAM, '#cf352c'), 'green': (GREEN, CREAM, '#3a9547'), 'alt': (TAB, BTN, '#d9c288'), 'blue': (BLUE, CREAM, '#3a74c4'),
        'on': ('#1f6b2b', CREAM, '#1f6b2b')}


def btn(text, a, kind='', sm=False, pad=None, size=None, **kw):
    """.btn { background: var(--btn); color: var(--cream); padding: 10px 22px; font-weight: 700; letter-spacing: 1px; border-radius: 4px;
       box-shadow: 3px 3px 0 rgba(0,0,0,.3); font-size: 15px } .btn.sm { padding: 6px 12px; font-size: 13px } .btn:hover { translateY(-1px) }"""
    bg, fg, hov = BTNS[kind]
    margs = {k: kw.pop(k) for k in ('id', 'v', 't', 'n', 'd') if k in kw}
    d = {'type': 'button', 'text': text, 'font': TYPE_B, 'size': size or (13 if sm else 15), 'ls': 1, 'color': fg, 'shadow': False,
         'bg': bg, 'hover': hov, 'disabled': bg, 'radius': 4, 'bshadow': '3,3,#0000004D', 'hover_dy': -1,
         'pad': pad or ([6, 12, 6, 12] if sm else [10, 22, 10, 22]), 'text_align': 'center'}
    d.update(act(a, **margs))
    d.update(kw)
    return d


def chip(src, var, a, small=False, idexpr='0', **kw):
    """.chip { padding: 6px 12px; border: 2px solid #7a5f2e; border-radius: 16px; font-size: 14px; font-weight: 700; color: var(--ink2) }
       .chip.on { background: var(--btn); color: var(--cream); border-color: var(--btn) }  .chip:hover { background: rgba(0,0,0,.07) }"""
    d = {'type': 'button', 'text': '{%s.label}' % var, 'font': TYPE_B, 'size': 13 if small else 14, 'color': '{%s.color}' % var, 'shadow': False,
         'bg': '{%s.bg}' % var, 'hover': '{%s.hover}' % var, 'border': '{%s.border}' % var, 'bw': 2, 'radius': 14 if small else 16,
         'pad': [3, 10, 3, 10] if small else [6, 12, 6, 12], 'repeat': {'for': src, 'as': var, 'limit': 12}}
    d.update(act(a, id=idexpr, v='%s.k' % var))
    d.update(kw)
    return d


def sep():
    """.sep { width: 1px; height: 26px; background: #b79c58; margin: 0 4px }"""
    return box([box([], w=1, h=26, bg='#b79c58')], w=9, h=26, pad=[0, 4, 0, 4])


def tag(text, color, **kw):
    """.tag { font-size: 12px; font-weight: 700; padding: 1px 7px; border: 2px solid; letter-spacing: 1px }"""
    return T(text, 12, color, True, ls=1, border=color, bw=2, pad=[3, 9, 3, 9], **kw)


def field(fid, bind, hint, w='fill', h=36, size=15, maxlen=40, area=False, **kw):
    """.fld { height: 36px; background: #fff; border: 2px solid #7a5f2e; padding: 0 10px; font-size: 15px }  textarea.fld { padding: 10px 12px; line-height: 1.4 }"""
    d = {'type': 'input', 'id': fid, 'bind': bind, 'hint': hint, 'w': w, 'h': h, 'max_len': maxlen, 'bg': '#ffffff', 'border': BROWN, 'bw': 2, 'color': INK,
         'font': TYPE, 'size': size}
    if area:
        d['wrap'] = True
    d.update(kw)
    return d


def paper(x, y, w, h, children, bg=PAPER2, rot=None, **kw):
    """.paper { position: absolute; background: var(--paper2); border: 1px solid #bcae86; box-shadow: 3px 4px 0 rgba(0,0,0,.2); padding: 20px 24px }"""
    d = {'type': 'column', 'x': x, 'y': y, 'w': w, 'h': h, 'bg': bg, 'border': '#bcae86', 'bshadow': '3,4,#00000033', 'pad': [21, 25, 21, 25],
         'children': children, 'anim': 'rise', 'anim_key': 'vm.v'}
    if rot:
        d['rot'] = rot
    d.update(kw)
    return d


# ---------------------------------------------------------------- shared: a correspondence paper (ticketPaper / sugPaper)
def message_nodes():
    """.msg { margin-bottom: 12px; font-size: 17px; line-height: 1.45; max-width: 640px }  .msg small { block; 13px; #8a7a52 }
       .msg.st { margin-left: 100px; padding-left: 12px; border-left: 4px solid var(--btn) }  .msg.pl .t { --hand }
       .msg.sys { text-align: center; max-width: none; font-size: 14px; color: #8a7a52; font-style: italic }"""
    sysm = T('{m.text}', 14, DIM, italic=True, wrap=True, w='fill', text_align='center', lh=20.3, **{'if': 'm.sys'})
    player = box([T('{m.head}', 13, DIM, w='fill'), T('{m.text}', 17, INK, font=HAND, lh=24.65, wrap=True, w='fill')], w=640, gap=0, **{'if': 'm.pl'})
    staff = row([box([], w=100, h=1),
                 box([T('{m.head}', 13, DIM, w='fill'), T('{m.text}', 17, INK, lh=24.65, wrap=True, w='fill')], w=640, gap=0,
                     pad=[0, 0, 0, 16], border=BTN, bw=4, bsides='l')], **{'if': 'm.staff'})
    return box([sysm, player, staff], w='fill', gap=0, repeat={'for': 'vm.th.msgs', 'as': 'm', 'limit': 60})


def thread(pfx, x, w):
    def msgs(top, cond):
        return {'type': 'scroll', 'id': pfx + '_msgs' + str(top), 'x': 25, 'y': top + 1, 'w': w - 50, 'h': 616 - top - 120, 'stick': True, 'value': 'vm.th.id',
                'pad': [0, 10, 0, 0], 'thumb': TAB, 'if': cond, 'children': [box([message_nodes()], w='fill', gap=12)]}
    sla = box([T('{vm.th.sla}', 15, INK, lh=22.5, wrap=True, w='fill', rich=True, bcolor=INK2)], x=25, y=45, w=w - 50,
              bg='#fff4c4', border='#d3bd6a', pad=[11, 15, 11, 15], **{'if': 'vm.th.hasSla'})
    reply = row([field(pfx + '_reply', 'reply', 'Write a message...', h=74, size=15, maxlen=500, area=True, pad=[11, 13, 11, 13]),
                 btn('SEND', '@vm.th.act', id='vm.th.id', t='$reply', h=74, w=98)],
                x=25, bottom=19, w=w - 50, gap=12, cross='end', **{'if': 'vm.th.canWrite'})
    closed = box([T('{vm.th.closedText}', 16, DIM, wrap=True, w='fill', text_align='center', rich=True, bcolor=INK2)], x=25, bottom=19, w=w - 50, h=74,
                 pad=[20, 20, 20, 20], **{'if': '!vm.th.canWrite'})
    head = row([H('{vm.th.head}', w='fill'), tag('{vm.th.tag}', '{vm.th.tagColor}', **{'if': 'vm.th.tag'})], w='fill')
    return paper(x, 24, w, 616, [head, sla, msgs(132, 'vm.th.hasSla'), msgs(46, '!vm.th.hasSla'), reply, closed])


def mini_list(title, newbtn, empty_text, sel_act, w=360):
    """The list on the left (#f7ecc8 paper) with .mini cards: padding 10px 14px; border 2px; radius 4px 12px 4px 4px."""
    item = {'type': 'button', 'w': 'fill', 'bg': '{r.bg}', 'hover': '{r.hover}', 'border': '{r.border}', 'bw': 2, 'radii': [4, 12, 4, 4],
            'bshadow': '{r.shadow}', 'pad': [12, 16, 12, 16], 'repeat': {'for': 'vm.list', 'as': 'r', 'limit': 30}, 'anim': 'rise', 'anim_key': 'vm.v',
            'children': [box([
                row([T('{r.no}', 16, INK, True, w='fill', ellipsis=True), tag('{r.tag}', '{r.tagColor}')], w='fill'),
                T('{r.sub}', 13, INK2, rich=True, bcolor=RED, w='fill', wrap=True)], w='fill', gap=0)]}
    item.update(act(sel_act, id='r.id'))
    return {'type': 'scroll', 'id': nid('list'), 'x': 24, 'y': 24, 'w': w, 'h': 616, 'bg': '#f7ecc8', 'border': '#bcae86', 'bshadow': '3,4,#00000033',
            'pad': [21, 25, 21, 25], 'thumb': TAB, 'anim': 'rise', 'anim_key': 'vm.v', 'children': [box([
                H(title), newbtn, T(empty_text, 15, INK, lh=24, wrap=True, w='fill', **{'if': 'vm.noList'}), box([item], w='fill', gap=8)], w='fill', gap=8)]}


# ---------------------------------------------------------------- views (inside the desk; positions are relative to the desk's padding edge)
def v_mine():
    left = mini_list('YOUR TICKETS', btn('+ NEW TICKET', 'newticket', 'green', w='fill', enabled='vm.canNew'),
                     'You have no tickets yet. Press NEW TICKET, or type /ticket your message in chat.', 'selmine')
    form = paper(408, 24, 1028, 616, [
        H('NEW CASE'),
        box([T('{vm.nf.sla}', 15, INK, lh=22.5, wrap=True, w='fill', rich=True, bcolor=INK2)], w='fill', bg='#fff4c4', border='#d3bd6a', pad=[11, 15, 11, 15]),
        field('newtext', 'newtext', 'Describe the problem...', h=250, size=18, maxlen=500, area=True, pad=[11, 13, 11, 13], debounce_ms=350,
              **act('preview', t='$newtext')),
        row([T('{vm.nf.detected}', 16, INK, w='fill', rich=True, bcolor=INK2), H('CATEGORY'), chip('vm.nf.cats', 'k', 'newcat')], w='fill', gap=10, cross='center',
            pad=[4, 0, 0, 0]),
        row([btn('SUBMIT TICKET', 'create', 'green', t='$newtext', pad=[14, 34, 14, 34], size=17),
             btn('CANCEL', 'cancelnew', 'alt', **{'if': 'vm.nf.canCancel'})], gap=14, pad=[14, 0, 0, 0]),
        T('Your position is saved with the ticket so staff can find the place you mean. Staff can change the category if it was guessed wrong.', 14, DIM,
          wrap=True, w='fill', pad=[8, 0, 0, 0]),
    ], gap=12, **{'if': 'vm.showNew'})
    th = box([thread('mine', 408, 1028)], x=0, y=0, w=1456, h=660, **{'if': '!vm.showNew'})
    close_btn = btn('CLOSE MY TICKET', 'pclose', 'alt', sm=True, id='vm.selId', right=40, y=30, **{'if': 'vm.canClose'})
    return box([left, form, th, close_btn], x=0, y=0, w=1456, h=660, **{'if': 'vm.vMine'})


def folder_card():
    """.folder 440x250 at (c*460, r*266+16) inside .grid (left 36, top 100), rotated by ROT[i % 6], dealt in with a 55ms stagger.
       The art (tab, clip, shadow) is the original CSS rendered to tex:cases/folder.png: 452x284 with the folder at (6, 24)."""
    f = {'type': 'button', 'x': 'c.x', 'y': 'c.y', 'w': 452, 'h': 284, 'bg': 'tex:cases/folder.png', 'hover': 'tex:cases/folder_hover.png', 'hover_dy': -4,
         'rot': 'c.rot', 'anim': 'deal', 'anim_delay': 'c.delay', 'anim_key': 'vm.v', 'repeat': {'for': 'vm.cards', 'as': 'c', 'limit': 18}, 'children': [
             T('{c.no}', 12, INK2, True, x=18, y=10),                                                 # .folder .no { left: 12px; top: -15px }
             box([T('{c.who}', 22, INK, True, w=270, ellipsis=True),                                   # .who 22px bold, max-width 270
                  box([T('{c.cat}', 12, '{c.catColor}', True, ls=1, border='{c.catColor}', bw=2, radius=3, pad=[2, 8, 2, 8])], pad=[4, 0, 0, 0]),
                  box([T('{c.preview}', 16, INK, font=HAND, lh=22.4, wrap=True, w='fill', clip=True, h=62)], w='fill', pad=[10, 0, 0, 0])],
                 x=28, y=99, w=400, gap=0),                                                            # padding 34px + <button> centres its content vertically
             box([row([T('{c.foot}', 14, INK2, w='fill', ellipsis=True, rich=True, bcolor=RED),
                       btn('OPEN FILE', 'openticket', sm=True, id='c.id', pad=[5, 12, 5, 12])], w='fill', cross='center')],
                 x=26, y=224, w=400, pad=[9, 0, 0, 0], border=EDGE, bw=1, dash=True, bsides='t'),
             # .stamp { right: 18px; top: 40px; border: 4px solid; padding: 2px 10px; 19px bold; ls 2px; rotate(-9deg); opacity: .85 }
             T('{c.stamp}', 19, '{c.stampColor}', True, ls=2, border='{c.stampColor}', bw=4, pad=[4, 12, 4, 12], bg='#fffdf459',
               right=24, y=66, rot=-9, opacity=0.85, anim='thunk', anim_delay='c.stampDelay', anim_key='vm.v')]}
    f.update(act('openticket', id='c.id'))
    return f


def v_queue():
    filters = row([chip('vm.flt', 'f', 'flt'), sep(), chip('vm.cats', 'k', 'cat'), sep(), chip('vm.flt2', 'f2', 'flt')],
                  x=24, y=14, w=1408, h=46, gap=8, cross='center')
    bar = row([field('qtext', 'qtext', 'Search player or text, press Enter', w=340, h=30, size=14, debounce_ms=700, **act('qtext', t='$qtext')),
               sep(), T('SORT', 13, BROWN, True, ls=1), chip('vm.sorts', 's', 'sort', small=True), box([], w='fill'), T('{vm.total}', 14, BROWN)],
              x=24, y=56, w=1408, h=30, gap=8, cross='center')
    grid = box([folder_card()], x=36, y=100, w=1384, h=544, **{'if': '!vm.empty'})
    empty = box([T('No cases match these filters.', 20, INK2), T('The pile is clear.', 20, INK2)], x=50, y=120, gap=6, **{'if': 'vm.empty'})
    return box([filters, bar, grid, empty], x=0, y=0, w=1456, h=660, **{'if': 'vm.vQueue'})


def v_ticket():
    client = paper(928, 24, 508, 228, [
        H('CLIENT'),
        row([T('{vm.cl.name}', 24, INK, True), T('{vm.cl.online}', 13, '{vm.cl.onlineColor}', True)], gap=8, cross='end'),
        T('{vm.cl.kv}', 15, INK, lh=27, wrap=True, w='fill', rich=True, bcolor=BROWN),
        T('{vm.cl.stamp}', 24, '{vm.cl.stampColor}', True, ls=2, border='{vm.cl.stampColor}', bw=4, pad=[4, 12, 4, 12], bg='#fffdf459',
          right=19, y=19, rot=-9, opacity=0.85, anim='thunk', anim_key='vm.v')], rot=0.5)
    cid = 'vm.cl.id'
    grid = box([
        row([btn('{vm.cl.claimLabel}', 'claim', 'green', id=cid, w='fill', **{'if': '!vm.cl.closed && !vm.cl.mine'}),
             btn('RELEASE', 'release', 'alt', id=cid, w='fill', **{'if': '!vm.cl.closed && vm.cl.mine'}),
             btn('{vm.cl.urgentLabel}', 'urgent', 'alt', id=cid, v='vm.cl.urgentVal', w='fill', **{'if': '!vm.cl.closed && !vm.cl.urgent'}),
             btn('{vm.cl.urgentLabel}', 'urgent', 'on', id=cid, v='vm.cl.urgentVal', w='fill', **{'if': '!vm.cl.closed && vm.cl.urgent'})], w='fill', gap=12),
        row([btn('{vm.cl.tpLabel}', 'tpto', 'alt', v='vm.cl.name', w='fill', **{'if': 'vm.cl.canTp'}),
             btn('INVENTORY', 'invsee', 'alt', v='vm.cl.name', w='fill', **{'if': 'vm.cl.canInv'})], w='fill', gap=12),
        row([btn('ENDER CHEST', 'endersee', 'alt', v='vm.cl.name', w='fill', **{'if': 'vm.cl.canEnder'}),
             btn('HISTORY', 'history', 'alt', v='vm.cl.name', w='fill')], w='fill', gap=12),
        row([btn('PUNISH', 'history', 'red', v='vm.cl.name', w='fill'), box([], w='fill')], w='fill', gap=12)],
        x=928, y=276, w=508, gap=12)
    cats = box([H('{vm.cl.holder}'), row([chip('vm.tcats', 'k', 'setcat', idexpr=cid, enabled='!vm.cl.closed')], gap=6)], x=928, y=510, w=508)
    close = btn('STAMP CLOSED', 'sclose', 'green', id=cid, x=928, y=578, w=508, pad=[22, 16, 22, 16], size=25, **{'if': '!vm.cl.closed'})
    return box([thread('tick', 24, 880), client, grid, cats, close], x=0, y=0, w=1456, h=660, **{'if': 'vm.vTicket'})


def v_reports():
    head = row([H('PLAYER REPORTS'), box([], w='fill'), chip('vm.flt', 'f', 'rep')], x=24, y=14, w=1408, gap=10, cross='center')
    r = {'type': 'row', 'w': 'fill', 'gap': 14, 'cross': 'center', 'pad': [8, 14, 8, 14], 'border': '#7a5a284D', 'bw': 1, 'bsides': 'b',
         'anim': 'rise', 'anim_key': 'vm.v', 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 40}, 'children': [
             box([tag('{r.tag}', '{r.tagColor}')], w=96),
             box([row([T('{r.target}', 17, INK, True), T('reported by {r.reporter}', 15, BROWN)], gap=8, cross='end'),
                  T('{r.reason}', 15, INK, font=HAND, wrap=True, w='fill'), T('{r.note}', 13, GREEN, wrap=True, w='fill', **{'if': 'r.note'})], w='fill', gap=2),
             box([T('{r.ago}', 15, BROWN, text_align='right', w='fill'), T('{r.where}', 13, BROWN, text_align='right', w='fill')], w=190),
             row([btn('CLAIM', 'rclaim', 'green', sm=True, id='r.id', **{'if': 'r.canClaim'}), btn('CLOSE', 'rclose', 'alt', sm=True, id='r.id', **{'if': 'r.canClose'}),
                  btn('TP', 'tpto', 'alt', sm=True, v='r.target', **{'if': 'r.canTp'}), btn('PUNISH', 'history', 'red', sm=True, v='r.target')], gap=8)]}
    body = {'type': 'scroll', 'id': 'rep_scroll', 'x': 24, 'y': 56, 'w': 1408, 'h': 590, 'thumb': TAB, 'children': [box([
        r, T('{vm.emptyText}', 20, INK2, pad=[40, 40, 40, 40], **{'if': 'vm.empty'})], w='fill')]}
    return box([head, body], x=0, y=0, w=1456, h=660, **{'if': 'vm.vReports'})


def note_card():
    """.note 440x250 pinned notes, coloured per status; art = the original CSS (tex:cases/note_<status>.png, 452x264 with the note at (6, 6))."""
    f = {'type': 'button', 'x': 'c.x', 'y': 'c.y', 'w': 452, 'h': 264, 'bg': '{c.tex}', 'hover': '{c.tex}', 'hover_dy': -4,
         'rot': 'c.rot', 'anim': 'deal', 'anim_delay': 'c.delay', 'anim_key': 'vm.v', 'repeat': {'for': 'vm.cards', 'as': 'c', 'limit': 18}, 'children': [
             T('{c.no}', 12, INK2, True, x=21, y=15),
             box([T('{c.title}', 20, INK, True, w='fill', ellipsis=True),
                  box([T('{c.preview}', 16, INK, font=HAND, lh=22.4, wrap=True, w='fill', clip=True, h=92)], w='fill', pad=[8, 0, 0, 0])],
                 x=27, y=82, w=400, gap=0),
             box([row([T('{c.foot}', 14, INK2, w='fill', ellipsis=True), tag('{c.tag}', '{c.tagColor}')], w='fill', cross='center')],
                 x=26, y=211, w=400, pad=[9, 0, 0, 0], border=EDGE, bw=1, dash=True, bsides='t')]}
    f.update(act('opensug', id='c.id'))
    return f


def v_sugs():
    bar = row([chip('vm.flt', 'f', 'sugfilter'), sep(),
               field('sugtext', 'sugsearch', 'Search idea or player, press Enter', w=340, h=30, size=14, debounce_ms=700, **act('sugtext', t='$sugsearch')),
               box([], w='fill'), T('{vm.total}', 14, BROWN)], x=24, y=14, w=1408, h=36, gap=8, cross='center')
    grid = box([note_card()], x=36, y=70, w=1384, h=574, **{'if': '!vm.empty'})
    empty = box([T('No suggestions here yet.', 20, INK2), T('Ideas players send will be pinned up on this board.', 20, INK2)], x=50, y=120, gap=6, **{'if': 'vm.empty'})
    return box([bar, grid, empty], x=0, y=0, w=1456, h=660, **{'if': 'vm.vSugs'})


def v_sug():
    card = paper(928, 24, 508, 170, [
        H('SUGGESTED BY'),
        row([T('{vm.cl.name}', 24, INK, True), T('{vm.cl.online}', 13, '{vm.cl.onlineColor}', True)], gap=8, cross='end'),
        T('{vm.cl.kv}', 15, INK, lh=27, wrap=True, w='fill', rich=True, bcolor=BROWN)], rot=0.5)
    side = box([H('SET A DECISION (THE PLAYER IS TOLD IN CHAT)'), row([chip('vm.scats', 'k', 'sugset', idexpr='vm.cl.id')], gap=6),
                box([T('Setting **Declined** or **Done** closes the idea for new player messages. Write a reply first so the player knows why.', 15, INK, lh=22.5,
                       wrap=True, w='fill', rich=True, bcolor=INK2)], w='fill', bg='#fff4c4', border='#d3bd6a', pad=[11, 15, 11, 15])],
               x=928, y=220, w=508, gap=8)
    return box([thread('sug', 24, 880), card, side], x=0, y=0, w=1456, h=660, **{'if': 'vm.vSug'})


def v_psug():
    left = mini_list('YOUR SUGGESTIONS', btn('+ NEW SUGGESTION', 'newsug', 'blue', w='fill'),
                     'You have not sent an idea yet. Press NEW SUGGESTION and tell us what would make the server better.', 'selsug')
    form = paper(408, 24, 1028, 616, [
        H('NEW SUGGESTION'),
        box([T('Share an idea for the server. Staff read every one and tell you what they decide. Please keep it to one idea per suggestion.', 15, INK, lh=22.5,
               wrap=True, w='fill')], w='fill', bg='#fff4c4', border='#d3bd6a', pad=[11, 15, 11, 15]),
        H('SHORT TITLE (OPTIONAL)'),
        field('sugtitle', 'sugtitle', 'Example: Sell Great Balls at the Pokecenter', maxlen=60),
        H('YOUR IDEA'),
        field('sugidea', 'sugidea', 'Describe your idea and why it would help...', h=230, size=18, maxlen=500, area=True, pad=[11, 13, 11, 13]),
        row([btn('SEND SUGGESTION', 'sugcreate', 'blue', n='$sugtitle', t='$sugidea', pad=[14, 34, 14, 34], size=17),
             btn('CANCEL', 'cancelsug', 'alt', **{'if': 'vm.canCancel'})], gap=14, pad=[12, 0, 0, 0])], gap=8, **{'if': 'vm.showNew'})
    th = box([thread('psug', 408, 1028)], x=0, y=0, w=1456, h=660, **{'if': '!vm.showNew'})
    return box([left, form, th], x=0, y=0, w=1456, h=660, **{'if': 'vm.vPsug'})


def v_prep():
    """.slip { background: var(--paper2); border: 1px solid #bcae86; border-left: 12px solid var(--red); box-shadow: 3px 4px 0 rgba(0,0,0,.2); padding: 10px 16px }"""
    slip = {'type': 'column', 'w': 'fill', 'bg': PAPER2, 'border': '#bcae86', 'bshadow': '3,4,#00000033', 'pad': [11, 17, 11, 29], 'gap': 2, 'anim': 'rise', 'anim_key': 'vm.v',
            'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 30}, 'children': [
                box([], x=0, y=0, w=12, bottom=0, bg=RED),
                row([T('{r.no}', 12, BROWN, True, ls=1, w='fill'), tag('{r.tag}', '{r.tagColor}')], w='fill'),
                T('{r.target}', 20, INK, True), T('{r.reason}', 15, INK, font=HAND, lh=20.25, wrap=True, w='fill')]}
    left = {'type': 'scroll', 'id': 'rp_list', 'x': 24, 'y': 24, 'w': 560, 'h': 616, 'bg': '#f7ecc8', 'border': '#bcae86', 'bshadow': '3,4,#00000033',
            'pad': [21, 25, 21, 25], 'thumb': TAB, 'anim': 'rise', 'anim_key': 'vm.v', 'children': [box([
                H('YOUR REPORTS'), T('You have not reported anyone. Use the form to tell staff about a player who broke a rule.', 15, INK, lh=24, wrap=True, w='fill',
                                     **{'if': 'vm.empty'}), box([slip], w='fill', gap=12)], w='fill', gap=8)]}
    pick = {'type': 'button', 'text': '{o.k}', 'font': TYPE_B, 'size': 14, 'color': INK2, 'shadow': False, 'bg': '#00000000', 'hover': '#00000012', 'border': BROWN, 'bw': 2,
            'radius': 16, 'pad': [6, 12, 6, 12], 'repeat': {'for': 'vm.online', 'as': 'o', 'limit': 10}}
    pick.update(act('pickname', v='o.k'))
    form = paper(608, 24, 828, 616, [
        H('REPORT A PLAYER'),
        box([T('Only report rule-breaking such as griefing, cheating, harassment or spam. Staff also see where you are standing when you send it. '
               'For problems that are not about a player, use **Help Tickets**.', 15, INK, lh=22.5, wrap=True, w='fill', rich=True, bcolor=INK2)],
            w='fill', bg='#fff4c4', border='#d3bd6a', pad=[11, 15, 11, 15]),
        H('PLAYER NAME'),
        field('repname', 'repname', 'Type the name, or pick one below', maxlen=32),
        H('ONLINE NOW', **{'if': 'vm.hasOnline'}),
        row([pick], gap=6, **{'if': 'vm.hasOnline'}),
        H('WHAT HAPPENED?'),
        field('reptext', 'reptext', 'What did they do, and when?', h=130, size=17, maxlen=500, area=True, pad=[11, 13, 11, 13]),
        box([btn('SEND REPORT', 'repcreate', 'red', n='$repname', t='$reptext', pad=[14, 34, 14, 34], size=17)], pad=[10, 0, 0, 0])], gap=8)
    return box([left, form], x=0, y=0, w=1456, h=660, **{'if': 'vm.vPrep'})


def v_punish():
    search = row([H('FIND PLAYER'), field('pname', 'pname', 'Player name, press Enter', w=300, maxlen=32), btn('SEARCH', 'lookup', sm=True, t='$pname')],
                 x=24, y=14, w=1408, gap=10, cross='center')

    def stat(v, label, col):
        return box([T(v, 32, col, True, text_align='center', w='fill'), T(label, 13, INK, text_align='center', w='fill')], cross='center', gap=0)
    stats = paper(24, 64, 1412, 140, [row([
        box([T('{vm.pl.initial}', 34, INK, True, text_align='center', w='fill')], w=70, h=70, bg=TAB, border=EDGE, bw=3, pad=[13, 0, 0, 0]),
        box([T('{vm.pl.name}', 28, INK, True), T('{vm.pl.line}', 15, BROWN, rich=True, bcolor=GREEN),
             row([tag('{vm.pl.ban}', RED, **{'if': 'vm.pl.ban'}), tag('{vm.pl.mute}', AMBER, **{'if': 'vm.pl.mute'}), tag('FROZEN', AMBER, **{'if': 'vm.pl.frozen'})],
                 gap=6, pad=[8, 0, 0, 0])], w='fill', gap=0),
        row([stat('{vm.pl.warnings}', 'WARNINGS', AMBER), stat('{vm.pl.mutes}', 'MUTES', RED), stat('{vm.pl.bans}', 'BANS', INK), stat('{vm.pl.kicks}', 'KICKS', INK)], gap=34)],
        w='fill', gap=30, cross='center')], pad=[17, 25, 17, 25])
    hrow = {'type': 'row', 'w': 'fill', 'gap': 14, 'cross': 'center', 'pad': [8, 14, 8, 14], 'border': '#7a5a284D', 'bw': 1, 'bsides': 'b',
            'repeat': {'for': 'vm.hist', 'as': 'h', 'limit': 60}, 'children': [
                box([tag('{h.type}', '{h.color}', text_align='center', w=74)], w=74),
                box([T('{h.reason}', 15, INK, font=HAND, wrap=True, w='fill'), T('{h.meta}', 13, BROWN, wrap=True, w='fill')], w='fill', gap=2),
                btn('LIFT', 'lift', 'alt', sm=True, v='h.liftK', n='vm.pl.name', **{'if': 'h.canLift'})]}
    hist = paper(24, 224, 860, 414, [H('RECORD'), {'type': 'scroll', 'id': 'hist_scroll', 'x': 21, 'y': 43, 'w': 818, 'h': 360, 'thumb': TAB, 'children': [
        box([hrow, T('Clean record.', 16, DIM, pad=[30, 30, 30, 30], **{'if': 'vm.clean'})], w='fill')]}], pad=[17, 21, 17, 21])
    ptype = {'type': 'button', 'text': '{k.label}', 'font': TYPE_B, 'size': 13, 'color': '{k.color}', 'shadow': False, 'bg': '{k.bg}', 'hover': '{k.hover}',
             'border': '{k.border}', 'bw': 2, 'radius': 14, 'pad': [4, 10, 4, 10], 'repeat': {'for': 'vm.ptypes', 'as': 'k', 'limit': 8}}
    ptype.update(act('ptype', v='k.k'))
    form = paper(908, 224, 528, 414, [
        H('NEW ENTRY - PICK A TYPE'),
        row([ptype], gap=6),
        field('pdur', 'pdur', 'Length, e.g. 30m, 2h, 7d, 1w', maxlen=12, **{'if': 'vm.temp'}),
        field('preason', 'preason', 'Reason (the player sees it)', h=140, maxlen=240, area=True, pad=[11, 13, 11, 13], **{'if': 'vm.temp'}),
        field('preason2', 'preason', 'Reason (the player sees it)', h=190, maxlen=240, area=True, pad=[11, 13, 11, 13], **{'if': '!vm.temp'}),
        btn('{vm.applyLabel}', 'punish', 'red', w='fill', pad=[14, 14, 14, 14], v='vm.ptype', n='vm.pl.name', t='$preason', d='$pdur', **{'if': 'vm.hasType'}),
        T('Staff protected by the exempt permission cannot be punished.', 13, DIM, wrap=True, w='fill')], gap=12)
    player = box([stats, hist, form], x=0, y=0, w=1456, h=660, **{'if': 'vm.hasPlayer'})
    mrow = {'type': 'button', 'w': 'fill', 'bg': MANILA, 'hover': '#f0d589', 'border': EDGE, 'bw': 2, 'radii': [4, 12, 4, 4], 'pad': [12, 16, 12, 16],
            'repeat': {'for': 'vm.matches', 'as': 'm', 'limit': 10}, 'children': [T('{m.k}', 16, INK, True)]}
    mrow.update(act('history', v='m.k'))
    matches = paper(24, 64, 700, 300, [H('DID YOU MEAN'), box([mrow], w='fill', gap=8)], **{'if': 'vm.hasMatches'})
    empty = T('{vm.emptyText}', 20, INK2, x=50, y=90, w=1300, wrap=True, **{'if': 'vm.showEmpty'})
    return box([search, player, matches, empty], x=0, y=0, w=1456, h=660, **{'if': 'vm.vPunish'})


def v_logs():
    head = row([H('AUDIT LOG'), box([], w=6), chip('vm.kinds', 'k', 'logk')], x=24, y=14, w=1408, gap=8, cross='center')
    r = {'type': 'row', 'w': 'fill', 'gap': 14, 'cross': 'center', 'pad': [4, 14, 4, 14], 'border': '#7a5a284D', 'bw': 1, 'bsides': 'b',
         'anim': 'rise', 'anim_key': 'vm.v', 'repeat': {'for': 'vm.rows', 'as': 'r', 'limit': 100}, 'children': [
             T('{r.time}', 15, BROWN, w=100), tag('{r.kind}', '{r.color}', w=76, text_align='center'), T('{r.actor}', 15, INK, True, w=160, ellipsis=True),
             T('{r.text}', 15, INK, w='fill', ellipsis=True)]}
    body = {'type': 'scroll', 'id': 'log_scroll', 'x': 24, 'y': 56, 'w': 1408, 'h': 590, 'thumb': TAB, 'children': [box([
        r, T('Nothing logged yet.', 20, INK2, pad=[40, 40, 40, 40], **{'if': 'vm.empty'})], w='fill')]}
    return box([head, body], x=0, y=0, w=1456, h=660, **{'if': 'vm.vLogs'})


# ---------------------------------------------------------------- the window
def page():
    logo = box([T('R', 26, WOOD2, True, text_align='center', w='fill')], x=24, y=14, w=46, h=46, bg=MANILA, border=EDGE, bw=3, pad=[8, 0, 0, 0])
    title = T('{vm.title}', 32, CREAM, True, ls=4, tshadow='2,2,#2a180b', x=86, y=10)
    sub = T('{vm.sub}', 14, '#d9c49a', ls=2, x=88, y=48)
    meter = box([T('{mt.label}', 10, '#d9c49a', ls=2), T('{mt.val}', 20, '{mt.color}', True)], bg='#00000047', border=BROWN, pad=[5, 13, 5, 13], min_w=104,
                gap=0, repeat={'for': 'vm.meters', 'as': 'mt', 'limit': 4})
    meters = row([meter], right=250, y=12, gap=8)

    def zb(text, delta, red=False):
        b = {'type': 'button', 'text': text, 'font': TYPE_B, 'size': 20, 'color': CREAM, 'shadow': False, 'w': 36, 'h': 36, 'radius': 3, 'bw': 2,
             'bg': RED if red else BTN, 'hover': '#cf352c' if red else '#55391e', 'border': '#7a1812' if red else BROWN, 'id': nid('z'), 'text_align': 'center'}
        if delta is None:
            b['close'] = True
        else:
            b['set'] = {'zoompct': '$zoompct %s %d' % ('+' if delta > 0 else '-', abs(delta))}
        return b
    zoom = row([zb('-', -10), T('{$zoompct}%', 15, CREAM, True, w=58, text_align='center'), zb('+', 10), zb('x', None, True)], right=22, y=16, gap=6, cross='center')

    def tab(on):
        """.tab { padding: 9px 22px 7px; background: #c9b078; color: #4a3a1c; 15px bold; ls 1px; radius 10px 10px 0 0; border: 2px solid #7a5f2e; no bottom;
           top: 2px }  .tab.on { background: var(--paper); color: var(--ink); top: 0; padding-top: 11px }  .tab.k-help.on { border-top: 5px solid green; padding-top: 8px }"""
        t = {'type': 'button', 'h': 37, 'bg': PAPER if on else TAB, 'border': BROWN, 'bw': 2, 'bsides': 'trl', 'radii': [10, 10, 0, 0],
             'pad': [13, 24, 7, 24], 'anim': 'tabin', 'anim_key': 'vm.v', 'children': [row([
                 T('{tb.label}', 15, INK if on else '#4a3a1c', True, ls=1),
                 T('{tb.n}', 12, '#ffffff', True, bg=RED, radius=9, pad=[1, 7, 1, 7], **{'if': 'tb.hasN'})], gap=8, cross='center')]}
        if on:
            t['btop'] = '{tb.btop}'
        else:
            t['hover'] = '#d9c288'
            t['hover_dy'] = -2
        t.update(act('tab', v='tb.k'))
        return t
    tabs = row([box([box([tab(True)], **{'if': 'tb.on'}), box([tab(False)], pad=[2, 0, 0, 0], **{'if': '!tb.on'})],
                    repeat={'for': 'vm.tabs', 'as': 'tb', 'limit': 8})], x=22, y=76, gap=4)
    desk = box([box([v_mine(), v_queue(), v_ticket(), v_reports(), v_sugs(), v_sug(), v_psug(), v_prep(), v_punish(), v_logs(),
                     T('Connecting to staff services...', 20, INK2, x=50, y=120, **{'if': 'vm.connecting'})], x=2, y=2, w=1456, h=660)],
               x=20, y=119, w=1460, h=664, bg='tex:cases/desk.png')

    def pgb(text, d, en):
        b = {'type': 'button', 'text': text, 'font': TYPE, 'size': 15, 'ls': 1, 'color': CREAM, 'shadow': False, 'pad': [6, 10, 6, 10], 'enabled': en,
             'bg': '#00000000', 'hover': '#00000000', 'disabled': '#00000000'}
        b.update(act('@vm.pgAct', v="'%s'" % d))
        return b
    pager = row([pgb('< PREV PAGE', '-1', 'vm.pgPrev'), T('{vm.pgText}', 15, '#d9c49a'), pgb('NEXT PAGE >', '1', 'vm.pgNext')], gap=22, cross='center',
                **{'if': 'vm.pager'})
    back = {'type': 'button', 'text': '{vm.backLabel}', 'font': TYPE, 'size': 15, 'ls': 1, 'color': CREAM, 'shadow': False, 'pad': [6, 10, 6, 10],
            'bg': '#00000000', 'hover': '#00000000', 'if': 'vm.back'}
    back.update(act('tab', v='vm.backTab'))
    foot = row([pager, back, box([], w='fill'), btn('REFRESH', 'refresh', sm=True), box([], w=10),
                {'type': 'button', 'id': 'close2', 'text': 'CLOSE', 'font': TYPE_B, 'size': 13, 'ls': 1, 'color': CREAM, 'shadow': False, 'bg': BTN, 'hover': '#55391e',
                 'radius': 4, 'bshadow': '3,3,#0000004D', 'hover_dy': -1, 'pad': [6, 12, 6, 12], 'close': True}],
               x=24, y=837 - 12 - 34, w=1452, h=34, cross='center')
    tick = {'type': 'spacer', 'w': 0, 'h': 0, 'every_ms': 25000}
    tick.update(act('refresh'))
    tock = {'type': 'spacer', 'w': 0, 'h': 0, 'every_ms': 1000}
    tock.update(act('tick'))
    # after a case is closed: a big rubber stamp, in the page's own stamp style (.stamp + thunk)
    stamp = box([T('{vm.stampText}', 80, RED, True, ls=7, border=RED, bw=9, pad=[12, 40, 12, 40], bg='#fffdf4E6', rot=-9, anim='thunk', anim_ms=420)],
                x=0, y=330, w=1500, cross='center', **{'if': 'vm.stamp'})
    root = {'type': 'column', 'w': 1500, 'h': 837, 'bg': 'tex:cases/win.png', 'children': [logo, title, sub, meters, zoom, tabs, desk, foot, tick, tock, stamp]}
    return {'v': 2, 'id': 'cases', 'channel': 'snobblestaff', 'hello': 'ready', 'bye': True, 'title': 'Regions Cases', 'width': 1500, 'height': 837, 'fit': [1700, 960], 'nodim': True,
            'state': {'zoompct': 100},
            'toast': {'bg': BTN, 'color': CREAM, 'ok': GREEN, 'bad': RED, 'y': 132, 'font': TYPE_B, 'size': 16},
            'root': root}


d = page()
with open(os.path.join(OUT, 'cases.json'), 'w', encoding='utf8') as f:
    json.dump(d, f, separators=(',', ':'))
print('wrote cases (%d bytes)' % os.path.getsize(os.path.join(OUT, 'cases.json')))
