"""Layout-v2 pages for the Snobble Rules mod (rules.json, verify.json), written into the server mod's resources.

rules.json is a faithful copy of the original snobblerules/rules.html: the book is fitted like the QuestDex (zoom = clamp(.7, 1.7,
height / 780 * .92), "zoomfit"), min(window - 24, 1240) wide and centred, with the same side list, page header, blocks, status line
and footer. Segoe UI -> "sans" (Open Sans, OFL, sized to Segoe UI's metrics); the page's colour emoji -> the server pack's emoji font
(Twemoji, CC-BY 4.0). The reading rules (minimum time + scrolled to the end, reported to the server) are unchanged from v1.
The previous version is gen_ui_rules_v1.py (verify.json is still built from it until its own rework).
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(__file__))
import gen_ui_rules_v1 as v1

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
BG1, PANEL, PANEL2, LINE = '#121a29', '#151e30', '#1b2740', '#2b3a58'
INK, MUTE, DIM, GOLD, OK, OKD, BAD = '#eef3fb', '#9fb0cb', '#6c7c99', '#ffd166', '#4ade80', '#1f9d55', '#ff6b6b'
RAINBOW = 'grad:#6fcf6a,#5bb8ff,#b48cff'
S, SB, SX = 'sans', 'sans_b', 'sans_xb'


def T(text, size=15, color=INK, font=S, **kw):
    d = {'type': 'label', 'text': text, 'font': font, 'size': size, 'color': color, 'shadow': False}
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


def btn(text, **kw):
    """.btn: 700 15px, radius 10, padding 10px 18px, 1px border"""
    d = {'type': 'button', 'text': text, 'font': SB, 'size': 15, 'color': INK, 'shadow': False, 'h': 38, 'pad': [0, 19, 0, 19], 'shrink': True,
         'text_align': 'center', 'bg': PANEL2, 'border': LINE, 'bw': 1, 'radius': 10, 'hover': '#26375a', 'disabled': '#20283a'}
    d.update(kw)
    return d


# ------------------------------------------------------------------ blocks
def blocks():
    v = []
    v.append(box([T('{b.icon}', 56, INK, S, lh=56, w='fill', text_align='center'),
                  T('{b.text}', 34, RAINBOW, SB, ls=4.08, lh=51, w='fill', text_align='center', pad=[8, 0, 0, 0]),
                  T('{b.sub}', 14, MUTE, SB, ls=3.92, upper=True, lh=21, w='fill', text_align='center', pad=[4, 0, 0, 0], **{'if': 'b.sub'})],
                 w='fill', pad=[22, 0, 12, 0], **{'if': "b.t == 'banner'"}))
    v.append(T('{b.icon} {b.text} {b.icon2}', 19, GOLD, SB, w='fill', wrap=True, text_align='center', lh=28.5, pad=[10, 0, 14, 0], **{'if': "b.t == 'tagline'"}))
    v.append(T('{b.text}', 16, '#dbe5f6', S, w='fill', wrap=True, lh=24, pad=[6, 0, 6, 0], **{'if': "b.t == 'p'"}))
    v.append(row([T('{b.icon}', 18, GOLD, S, lh=27, **{'if': 'b.icon'}), T('{b.text}', 18, GOLD, SX, w='fill', wrap=True, lh=27)],
                 w='fill', gap=9, cross='center', pad=[14, 0, 6, 0], **{'if': "b.t == 'h'"}))
    v.append(box([T('{b.text}', 16, '#b9f3cf', SB, w='fill', wrap=True, lh=24, text_align='center')],
                 w='fill', bg='#12301f', border='#2a6a45', bw=1, radius=10, pad=[11, 17, 11, 17], **{'if': "b.t == 'note'"}))
    v.append(box([], h=8, **{'if': "b.t == 'note'"}))
    v.append(box([
        row([T('{b.n}', 13, GOLD, SX, min_w=34, h=26, lh=24, pad=[0, 9, 0, 9], text_align='center', bg=PANEL2, border=LINE, bw=1, radius=13, shrink=True),
             T('{b.icon}', 20, INK, S, lh=30, **{'if': 'b.icon'}),
             T('{b.title}', 17, INK, SX, w='fill', wrap=True, lh=25.5)], w='fill', gap=10, cross='center', pad=[0, 0, 4, 0]),
        T('{para}', 15, '#c9d6ec', S, w='fill', wrap=True, lh=22.5, pad=[3, 0, 3, 0], repeat={'for': 'b.text', 'as': 'para', 'limit': 20}),
        box([T('{b.emph}', 15, '#ffc4cf', SX, w='fill', wrap=True, lh=22.5)], w='fill', bg='#3a1d28', border='#7a2e45', bw=1, radius=8, pad=[7, 13, 7, 13],
            **{'if': 'b.emph'}),
        box([], h=0)],
        w='fill', bg=PANEL, border=LINE, bw=1, radius=12, pad=[13, 17, 13, 17], gap=0, **{'if': "b.t == 'rule'"}))
    v.append(box([], h=12, **{'if': "b.t == 'rule'"}))
    v.append(box([row([T('{it.icon}', 18, INK, S, w=26, lh=27, text_align='center', **{'if': 'it.icon'}),
                       T('{it.text}', 15.5, '#dbe5f6', S, w='fill', wrap=True, lh=23.25)],
                      w='fill', gap=10, cross='center', bg=PANEL, border=LINE, bw=1, radius=10, pad=[7, 13, 7, 13], repeat={'for': 'b.items', 'as': 'it', 'limit': 30})],
                 w='fill', gap=10, pad=[7, 0, 11, 0], **{'if': "b.t == 'list'"}))
    v.append(row([box([T('{r.icon}', 28, '{r.color}', S, lh=33.6),
                       T('{r.name}', 16, '{r.color}', SX, ls=1.6, lh=24)],
                      min_w=170, w='content', cross='center', bg=PANEL, border='{r.color}', bw=2, radius=12, pad=[14, 20, 14, 20], repeat={'for': 'b.items', 'as': 'r', 'limit': 6})],
                 w='fill', gap=12, align='center', wrap=True, pad=[8, 0, 12, 0], **{'if': "b.t == 'regions'"}))
    return v


def howto():
    li = lambda n, txt: row([T('%d.' % n, 15, '#dbe5f6', S, w=22, lh=22.5, text_align='right', pad=[0, 6, 0, 0]),
                             T(txt, 15, '#dbe5f6', S, w='fill', wrap=True, lh=22.5, rich=True, rich_plain=False)], w='fill', pad=[2, 0, 2, 0])
    return box([row([box([], w=4, h='fill', bg=GOLD),
                     box([T('How this works', 15, GOLD, SB, ls=0.6, lh=19, pad=[0, 0, 4, 0]),
                          li(1, 'There are **{vm.total} pages**. Read each one to the very end (scroll down).'),
                          li(2, 'Press **Next page** to continue. A page counts once it has been open for a few seconds and you reached its end.'),
                          li(3, 'When all {vm.total} pages are ticked ✔ the grey **I have read the rules** button turns green. Press it to finish the Rules quest.')],
                         w='fill', pad=[12, 18, 12, 14])], w='fill')],
               w='fill', bg=PANEL2, border=LINE, bw=1, radius=10, pad=[14, 0, 6, 0], **{'if': '$cur == 0 && !st.accepted'})


# ------------------------------------------------------------------ side list
def steps():
    def one(cur):
        d = {'type': 'button', 'if': '$cur == p_i' if cur else '$cur != p_i', 'w': 'fill', 'min_h': 38, 'pad': [8, 11, 8, 11], 'gap': 10, 'cross': 'center',
             'radius': 10, 'bg': PANEL2 if cur else '', 'border': LINE if cur else '', 'bw': 1, 'hover': PANEL2, 'set': {'cur': 'p_i'}}
        d['children'] = [
            box([T('{p.mark}', 12, '{p.markColor}', SX, w=26, h=26, lh=26, text_align='center')], w=26, h=26, radius=13, bg='{p.markbg}', border='{p.markBorder}', bw=1),
            T('{p.title}', 14, INK if cur else MUTE, SB if cur else S, w='fill', wrap=True, lh=16.8),
            T('{p.icon}', 16, INK, S, lh=24, **{'if': 'p.icon'})]
        return d
    return [{'type': 'stack', 'repeat': {'for': 'vm.pages', 'as': 'p', 'limit': 64}, 'w': 'fill', 'children': [one(True), one(False)]}]


def rules_page():
    side = box([
        row([box([T('\U0001F4DC', 24, INK, S, lh=36, w=44, text_align='center')], w=44, h=44, radius=12, bg=PANEL2, border=LINE, bw=1, align='center', cross='center'),
             box([T('{st.book.title}', 15, INK, SX, ls=0.9, lh=17.25, w='fill', wrap=True), T('{st.book.subtitle}', 12, MUTE, S, ls=0.48, lh=18, w='fill', wrap=True)],
                 w='fill')],
            w='fill', gap=11, cross='center', pad=[2, 6, 16, 6], bsides='b', border=LINE, bw=1),
        box([], h=12),
        {'type': 'scroll', 'id': 'steps', 'w': 'fill', 'h': 'fill', 'track': '#101827', 'fill': '#3b4f78', 'children': [box(steps(), w='fill', gap=5)]},
        box([row([T('Pages read', 12.5, MUTE, S, lh=18.75, w='fill', **{'if': '!st.accepted'}), T('All pages read', 12.5, MUTE, S, lh=18.75, w='fill', **{'if': 'st.accepted'}),
                  T('{vm.readCount} / {vm.total}', 12.5, INK, SB, lh=18.75)], w='fill', pad=[0, 0, 6, 0]),
             {'type': 'bar', 'w': 'fill', 'h': 9, 'value': 'vm.readCount', 'max': 'vm.total', 'fill': RAINBOW, 'fill_bg': '#223048', 'radius': 5}],
            w='fill', pad=[13, 6, 2, 6], bsides='t', border=LINE, bw=1),
        box([], h=0)],
        w=270, h='fill', bg=PANEL, pad=[18, 14, 14, 14], bsides='r', border=LINE, bw=1)

    head = row([T('{st.book.pages[$cur].icon}', 28, INK, S, lh=42, **{'if': 'st.book.pages[$cur].icon'}),
                T('{st.book.pages[$cur].title}', 22, INK, SB, ls=0.44, lh=33, w='fill', ellipsis=True),
                T('Page {vm.pages[$cur].no} of {vm.total}', 13, MUTE, S, lh=19.5, pad=[3, 13, 3, 13], bg=PANEL, border=LINE, bw=1, radius=12, shrink=True)],
               w='fill', gap=12, cross='center', pad=[16, 26, 12, 26], bsides='b', border=LINE, bw=1)

    content = {'type': 'scroll', 'id': 'content', 'w': 'fill', 'h': 'fill', 'pad': [18, 28, 26, 28], 'value': '$cur', 'hint': '▼ scroll down to keep reading ▼',
               'track': '#101827', 'fill': '#3b4f78',
               'min_ms': 'vm.minMs', 'until': 'vm.pages[$cur].done', 'args': {'page': 'int:0:63'}, 'argv': {'page': '$cur'},
               'msg': {'kind': 'read', 'page': '{page}'},
               'children': [box([box(blocks(), w='fill', repeat={'for': 'st.book.pages[$cur].blocks', 'as': 'b', 'limit': 100}), howto()], w='fill')]}

    def st_line(text, cond, ok=False):
        return T(text, 13.5, OK if ok else MUTE, SB if ok else S, lh=20.25, **{'if': cond})
    status = row([
        st_line('Connecting to the server…', '!st'),
        st_line('✔ You have already completed the rules. This page is for reference.', 'st && vm.pages[$cur].done && st.accepted', True),
        st_line('✔ Page read. Go to the next page.', 'st && vm.pages[$cur].done && !st.accepted', True),
        st_line('\U0001F4D6 Scroll down to the end of the page to finish reading it.', 'st && !vm.pages[$cur].done && !$_atend'),
        st_line('\U0001F4D6 Keep reading… ({$_left}s)', 'st && !vm.pages[$cur].done && $_atend && $_left > 0'),
        st_line('\U0001F4D6 Checking with the server…', 'st && !vm.pages[$cur].done && $_atend && $_left <= 0')],
        w='fill', min_h=36, pad=[8, 26, 8, 26], gap=8, cross='center', bg=PANEL, bsides='t', border=LINE, bw=1)

    footer = row([
        btn('◀ Previous', id='prev', enabled='$cur > 0', set={'cur': '$cur - 1'}, key='left'),
        btn('Next page ▶', id='next', enabled='$cur < vm.last', set={'cur': '$cur + 1'}, bg='#1d4f88', border='#3a7bc0', hover='#2461a8', key='right'),
        box([], w='fill'),
        T('Accepted {st.acceptedAt|day}', 12, DIM, S, lh=18, **{'if': 'st.accepted && st.acceptedAt'}),
        T('Greyed out until you have read all {vm.total} pages ({vm.readCount}/{vm.total}).', 12, DIM, S, lh=18, **{'if': '!st.accepted && !vm.allRead'}),
        T('Press the button to finish the Rules quest.', 12, DIM, S, lh=18, **{'if': '!st.accepted && vm.allRead'}),
        btn('I have read the rules', **{'if': '!st.accepted', 'id': 'confirm', 'h': 43, 'pad': [0, 27, 0, 27], 'size': 16, 'ls': 0.32, 'enabled': 'vm.allRead',
                                         'bg': OKD, 'border': '{vm.confirmBorder}', 'color': '{vm.confirmColor}', 'hover': '#25b563', 'disabled': '#3a3f4c', 'msg': {'kind': 'accept'}}),
        btn('✔ Rules quest completed', **{'if': 'st.accepted', 'id': 'done_btn', 'h': 43, 'pad': [0, 27, 0, 27], 'size': 16, 'ls': 0.32, 'enabled': 'false',
                                                'bg': '#3a3f4c', 'border': '#4a4f5c', 'color': '#8a909e', 'disabled': '#3a3f4c'}),
        btn('Close', id='close', close=True, args={}, msg={'kind': 'bye'})],
        w='fill', gap=12, cross='center', pad=[13, 22, 14, 22], bg=PANEL, bsides='t', border=LINE, bw=1)

    main = box([head, content, status, footer], w='fill', h='fill')

    overlay = box([box([
        T('\U0001F389', 54, INK, S, lh=81, w='fill', text_align='center'),
        T('Rules quest complete!', 26, OK, SB, lh=39, w='fill', text_align='center', pad=[6, 0, 8, 0]),
        T('Thank you for reading the rules. Every other quest is now open to you.', 16, '#cfdcf2', S, w='fill', wrap=True, text_align='center', lh=24, pad=[4, 0, 4, 0]),
        box([], h=16),
        row([btn('Start playing', id='start', h=43, pad=[0, 27, 0, 27], size=16, ls=0.32, bg=OKD, border=OK, color='#ffffff', hover='#25b563', close=True)], w='fill', align='center')],
        w=540, bg=PANEL, border=OK, bw=2, radius=16, pad=[30, 32, 30, 32])],
        x=0, y=0, w='fill', h='fill', z=5, bg='#06090fe0', align='center', cross='center', anim='fade', anim_ms=200, **{'if': 'done'})

    book = row([side, main], w='fill', max_w=1240, h='fill', bg=BG1, border=LINE, bw=1, radius=14, clip=True)
    return {
        'v': 2, 'id': 'rules', 'channel': 'snobblerules', 'title': 'Snobblemon Regions Rules', 'width': 1507, 'height': 848,
        'zoomfit': [780, 0.92, 0.7, 1.7], 'nodim': True,
        'state': {'cur': {'from': 'vm.startPage'}},
        'toast': {'bg': '#3a1d28', 'border': '#7a2e45', 'border_bad': '#7a2e45', 'color': '#ffd0d8', 'color_bad': '#ffd0d8', 'bw': 1, 'radius': 10,
                  'pad_x': 18, 'pad_y': 9, 'y': 26, 'from': -20, 'font': SB, 'size': 15},
        'root': {'type': 'stack', 'w': 'fill', 'h': 'fill', 'children': [
            box([box([book], w='fill', h='fill', cross='center', pad=[12, 12, 12, 12])], w='fill', h='fill', bg='tex:snobblerules/bg.png'),
            # tells the server which page is on screen whenever it changes (the web page did this in show())
            {'type': 'spacer', 'id': 'view', 'watch': '$cur', 'args': {'page': 'int:0:63'}, 'argv': {'page': '$cur'}, 'msg': {'kind': 'view', 'page': '{page}'}},
            overlay,
        ]},
    }


# ------------------------------------------------------------------ rules roll (staff), a copy of snobblerules/verify.html
def verify_page():
    REQ = {'tab': 'enum:accepted|unread|all', 'q': 'str:32'}

    def tab(key, text):
        def one(on):
            cond = ("$tab == '%s'" if on else "$tab != '%s'") % key
            return {'type': 'button', 'if': cond, 'id': 'tab_%s%s' % (key, '_on' if on else ''),
                    'h': 37 if on else 36, 'pad': [0, 17, 0, 17], 'gap': 7, 'cross': 'center', 'shrink': True,
                    'bg': BG1 if on else '', 'hover': BG1 if on else PANEL2, 'border': LINE if on else '', 'bw': 1, 'bsides': 'tlr', 'radii': [9, 9, 0, 0],
                    'set': {'tab': "'%s'" % key}, 'args': REQ, 'argv': {'tab': "'%s'" % key, 'q': '$q'},
                    'msg': {'kind': 'admin_refresh', 'tab': '{tab}', 'q': '{q}', 'force': 'true'},
                    'children': [T(text, 14, INK if on else MUTE, SB, lh=21),
                                 T('{vs.counts.%s}' % key, 12.5, INK if on else MUTE, SB, lh=19, min_w=24, shrink=True, text_align='center', pad=[0, 7, 0, 7],
                                   bg='#243250', radius=10)]}
        return [one(True), one(False)]

    line = {'type': 'row', 'repeat': {'for': 'vs.rows', 'as': 'r', 'limit': 200}, 'w': 'fill', 'gap': 14, 'cross': 'center', 'bg': PANEL, 'border': LINE, 'bw': 1,
            'radius': 11, 'pad': [11, 15, 11, 15], 'children': [
        box([T('{r.name|first}', 17, '#0b1019', SX, lh=38, w=38, text_align='center')], w=38, h=38, bg='{r.name|namecolor}', radius=10),
        box([row([T('{r.name}', 16, INK, SX, lh=23, ellipsis=True, shrink=True),
                  box([], w=9, h=9, radius=5, bg=OK, **{'if': 'r.online'}), box([], w=9, h=9, radius=5, bg='#4a5568', **{'if': '!r.online'})], gap=8, cross='center'),
             T('Read and accepted the rules {r.acceptedAt|ago} · {r.acceptedAt|date}', 13, MUTE, S, lh=19, w='fill', ellipsis=True, **{'if': "r.status == 'accepted'"}),
             T('Has not finished the rules · first seen {r.firstSeen|ago}', 13, MUTE, S, lh=19, w='fill', ellipsis=True, **{'if': "r.status != 'accepted'"})], w='fill'),
        T('ACCEPTED', 12, '#b9f3cf', SX, ls=0.6, lh=17, pad=[3, 11, 3, 11], bg='#14452a', radius=12, shrink=True, **{'if': "r.status == 'accepted'"}),
        T('NOT FINISHED', 12, '#aebad2', SX, ls=0.6, lh=17, pad=[3, 11, 3, 11], bg='#2b344a', radius=12, shrink=True, **{'if': "r.status != 'accepted'"})]}

    def empty(text, cond):
        return T(text, 16, DIM, S, w='fill', text_align='center', lh=23, pad=[60, 0, 60, 0], **{'if': cond})

    head = row([box([T('\U0001F6E1', 22, INK, S, lh=33, w=42, text_align='center')], w=42, h=42, radius=11, bg=PANEL2, border=LINE, bw=1, align='center', cross='center'),
                box([T('Rules Roll', 20, INK, SB, ls=0.8, lh=29),
                     T('Everyone who has read and accepted the rules. Accepting verifies a player automatically.', 12.5, MUTE, S, lh=18)]),
                box([], w='fill'),
                {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search a player…', 'w': 230, 'h': 38, 'max_len': 32, 'font': S, 'size': 15, 'bg': '#0f1626',
                 'border': LINE, 'bw': 1, 'radius': 9, 'pad': [0, 13, 0, 13], 'color': INK, 'hint_color': '#757575', 'debounce_ms': 250,
                 'args': REQ, 'argv': {'tab': '$tab', 'q': '$q'}, 'msg': {'kind': 'admin_refresh', 'tab': '{tab}', 'q': '{q}', 'force': 'true'}},
                btn('Close', id='close', close=True, size=14, h=36, pad=[0, 17, 0, 17], radius=9, args={}, msg={'kind': 'bye'})],
               w='fill', gap=14, cross='center', pad=[16, 22, 12, 22], bg=PANEL, bsides='b', border=LINE, bw=1)
    tabs = row(tab('accepted', 'Accepted') + tab('unread', 'Not finished') + tab('all', 'Everyone'), w='fill', gap=6, cross='end', pad=[10, 22, 0, 22], bg=PANEL,
               bsides='b', border=LINE, bw=1)
    listing = {'type': 'scroll', 'id': 'list', 'w': 'fill', 'h': 'fill', 'pad': [12, 22, 12, 22], 'track': '#101827', 'fill': '#3b4f78', 'value': '$tab',
               'children': [box([
                   line,
                   box([T('No permission', 24, BAD, SB, lh=35, w='fill', text_align='center', pad=[0, 0, 8, 0]),
                        T('Only staff with the **snobblerules.verify** permission can use this page.', 15, MUTE, S, lh=22, w='fill', text_align='center', rich=True,
                          bcolor=MUTE)], w='fill', pad=[40, 40, 40, 40], **{'if': 'denied'}),
                   empty('Loading…', '!vs && !denied'),
                   empty('No players match “{$q}”.', 'vs && vs.matches == 0 && $q'),
                   empty('Nobody has accepted the rules yet.', "vs && vs.matches == 0 && !$q && $tab == 'accepted'"),
                   empty('Nothing here yet.', "vs && vs.matches == 0 && !$q && $tab != 'accepted'")], w='fill', gap=8)]}
    foot = row([T('Connecting…', 13, MUTE, S, lh=19, **{'if': '!vs && !denied'}),
                T('Access denied', 13, MUTE, S, lh=19, **{'if': 'denied'}),
                T('Showing {vs.shown} of {vs.matches}', 13, MUTE, S, lh=19, **{'if': 'vs && !vs.truncated'}),
                T('Showing {vs.shown} of {vs.matches} — type in the search box to find the rest', 13, MUTE, S, lh=19, **{'if': 'vs && vs.truncated'}),
                box([], w='fill'),
                T('●', 13, OK, S, lh=19, **{'if': 'live'}), T('●', 13, BAD, S, lh=19, **{'if': '!live'})],
               w='fill', min_h=38, gap=14, cross='center', pad=[8, 22, 8, 22], bg=PANEL, bsides='t', border=LINE, bw=1)
    poll = {'type': 'spacer', 'id': 'poll', 'every_ms': 4000, 'args': {'tab': 'enum:accepted|unread|all', 'q': 'str:32', 'rev': 'int:-1:9007199254740991'},
            'argv': {'tab': '$tab', 'q': '$q', 'rev': 'vs.rev'}, 'msg': {'kind': 'admin_refresh', 'tab': '{tab}', 'q': '{q}', 'rev': '{rev}', 'force': 'false'}}
    wrap = box([head, tabs, listing, foot, poll], w='fill', max_w=1100, h='fill', bg=BG1, border=LINE, bw=1, radius=14, clip=True)
    return {
        'v': 2, 'id': 'verify', 'channel': 'snobblerules', 'hello': 'admin_hello', 'title': 'Snobblemon Rules Roll', 'width': 1507, 'height': 848,
        'zoomfit': [780, 0.92, 0.7, 1.7], 'nodim': True, 'state': {'tab': 'accepted', 'q': ''},
        'toast': {'bg': '#3a1d28', 'border': '#2a6a45', 'border_bad': '#7a2e45', 'color': '#b9f3cf', 'color_bad': '#ffd0d8', 'bw': 1, 'radius': 10,
                  'pad_x': 18, 'pad_y': 9, 'y': 26, 'from': -20, 'font': SB, 'size': 15},
        'root': box([box([wrap], w='fill', h='fill', cross='center', pad=[12, 12, 12, 12])], w='fill', h='fill', bg='tex:snobblerules/bg.png')}


def strip_empty(o):
    """an empty border is not a colour: leave the key out"""
    if isinstance(o, dict):
        return {k: strip_empty(v) for k, v in o.items() if not (k == 'border' and v == '')}
    if isinstance(o, list):
        return [strip_empty(v) for v in o]
    return o


def check(d):
    seen, bad = set(), []
    def walk(n, rep):
        r = n.get('repeat')
        if r and not 1 <= r.get('limit', 1) <= 500:
            bad.append('limit')
        rep = rep or bool(r)
        if n.get('id') and not rep:
            if n['id'] in seen:
                bad.append('duplicate id ' + n['id'])
            seen.add(n['id'])
        for k in n.get('children', []):
            walk(k, rep)
    walk(d['root'], False)
    if bad:
        raise SystemExit('INVALID: ' + ', '.join(bad))


if __name__ == '__main__':
    for name, page in (('rules', rules_page()), ('verify', verify_page())):
        page = strip_empty(v1.fix(page))
        check(page)
        with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf8') as f:
            json.dump(page, f, ensure_ascii=False, separators=(',', ':'))
        print('wrote', name, os.path.getsize(os.path.join(OUT, name + '.json')))
