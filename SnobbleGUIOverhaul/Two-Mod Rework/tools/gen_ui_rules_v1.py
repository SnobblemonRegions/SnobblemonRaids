"""Generates the layout-v2 pages for the Snobble Rules mod (rules.json, verify.json) into the server mod's resources."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
os.makedirs(OUT, exist_ok=True)

BG1, PANEL, PANEL2, LINE = '#121A29', '#151E30', '#1B2740', '#2B3A58'
INK, MUTE, DIM, GOLD, OK, OKD, BAD = '#EEF3FB', '#9FB0CB', '#6C7C99', '#FFD166', '#4ADE80', '#1F9D55', '#FF6B6B'
RAINBOW = 'grad:#6FCF6A,#5BB8FF,#B48CFF'


def label(text, **kw):
    d = {'type': 'label', 'text': text}
    d.update(kw)
    return d


def spacer(**kw):
    d = {'type': 'spacer'}
    d.update(kw)
    return d


def btn(text, **kw):
    d = {'type': 'button', 'text': text, 'h': 22, 'bg': PANEL2, 'border': LINE, 'radius': 5, 'hover': '#26375A', 'disabled': '#20283A', 'color': INK}
    d.update(kw)
    return d


# ------------------------------------------------------------------ rules book

def block_variants():
    v = []
    v.append({'type': 'column', 'if': "b.t == 'banner'", 'w': 'fill', 'cross': 'center', 'pad': [10, 0, 6, 0], 'gap': 2, 'children': [
        label('{b.text}', scale=2.2, color='#6FCF6A'),
        label('{b.sub}', color=MUTE, if_='b.sub'),
    ]})
    v.append(label('{b.text}', **{'if': "b.t == 'tagline'", 'w': 'fill', 'text_align': 'center', 'color': GOLD, 'scale': 1.25, 'pad': [4, 0, 6, 0]}))
    v.append(label('{b.text}', **{'if': "b.t == 'p'", 'w': 'fill', 'wrap': True, 'color': '#DBE5F6', 'pad': [2, 0, 2, 0]}))
    v.append(label('{b.text}', **{'if': "b.t == 'h'", 'w': 'fill', 'color': GOLD, 'scale': 1.2, 'pad': [8, 0, 2, 0]}))
    v.append(label('{b.text}', **{'if': "b.t == 'note'", 'w': 'fill', 'wrap': True, 'text_align': 'center', 'color': '#B9F3CF', 'bg': '#12301F', 'border': '#2A6A45', 'radius': 5, 'pad': [7, 10, 7, 10]}))
    v.append({'type': 'column', 'if': "b.t == 'rule'", 'w': 'fill', 'bg': PANEL, 'border': LINE, 'radius': 6, 'pad': [8, 12, 8, 12], 'gap': 4, 'children': [
        {'type': 'row', 'gap': 8, 'cross': 'center', 'children': [
            label('{b.n}', color=GOLD, bg=PANEL2, border=LINE, radius=6, pad=[3, 7, 3, 7]),
            label('{b.title}', scale=1.15, color=INK),
        ]},
        {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
            label('{para}', repeat={'for': 'b.text', 'as': 'para'}, w='fill', wrap=True, color='#C9D6EC'),
        ]},
        label('{b.emph}', **{'if': 'b.emph', 'w': 'fill', 'wrap': True, 'color': '#FFC4CF', 'bg': '#3A1D28', 'border': '#7A2E45', 'radius': 5, 'pad': [5, 9, 5, 9]}),
    ]})
    v.append({'type': 'column', 'if': "b.t == 'list'", 'w': 'fill', 'gap': 4, 'children': [
        {'type': 'row', 'repeat': {'for': 'b.items', 'as': 'it'}, 'w': 'fill', 'bg': PANEL, 'border': LINE, 'radius': 5, 'pad': [5, 10, 5, 10], 'children': [
            label('{it.text}', w='fill', wrap=True, color='#DBE5F6'),
        ]},
    ]})
    v.append({'type': 'row', 'if': "b.t == 'regions'", 'w': 'fill', 'gap': 8, 'align': 'center', 'pad': [4, 0, 6, 0], 'children': [
        {'type': 'column', 'repeat': {'for': 'b.items', 'as': 'r'}, 'w': 150, 'cross': 'center', 'bg': PANEL, 'border': '{r.color}', 'radius': 6, 'pad': [9, 8, 9, 8], 'children': [
            label('{r.name}', color='{r.color}'),
        ]},
    ]})
    return v


def fix(o):
    """label(..., if_=...) helper: rename if_ to if."""
    if isinstance(o, dict):
        return {('if' if k == 'if_' else k): fix(v) for k, v in o.items()}
    if isinstance(o, list):
        return [fix(x) for x in o]
    return o


def howto():
    return {'type': 'column', 'if': '$cur == 0 && !st.accepted', 'w': 'fill', 'bg': PANEL2, 'border': LINE, 'radius': 6, 'pad': [9, 14, 9, 14], 'gap': 3, 'children': [
        label('How this works', color=GOLD),
        label('1. There are {vm.total} pages. Read each one to the very end (scroll down).', w='fill', wrap=True, color='#DBE5F6'),
        label('2. Press Next page to continue. A page counts once it has been open for a few seconds and you reached its end.', w='fill', wrap=True, color='#DBE5F6'),
        label('3. When all {vm.total} pages are ticked the grey "I have read the rules" button turns green. Press it to finish the Rules quest.', w='fill', wrap=True, color='#DBE5F6'),
    ]}


def step_buttons():
    def row(cur):
        d = {'type': 'button', 'if': '$cur == p_i' if cur else '$cur != p_i', 'w': 'fill', 'h': 26, 'pad': [3, 6, 3, 6], 'gap': 7, 'cross': 'center', 'radius': 5,
             'set': {'cur': 'p_i'}, 'hover': '#222F4C'}
        if cur:
            d.update({'bg': PANEL2, 'border': LINE})
        d['children'] = [
            {'type': 'column', 'w': 18, 'h': 18, 'align': 'center', 'cross': 'center', 'bg': '{p.markbg}', 'radius': 9, 'children': [label('{p.mark}', color='#FFFFFF')]},
            label('{p.title}', w='fill', color=INK if cur else MUTE),
        ]
        return d
    # one wrapper per step keeps the steps in order; inside it, exactly one of the two looks shows
    return [{'type': 'stack', 'repeat': {'for': 'vm.pages', 'as': 'p'}, 'w': 'fill', 'h': 26, 'children': [row(True), row(False)]}]


def rules_page():
    side = {'type': 'column', 'w': 190, 'h': 'fill', 'bg': PANEL, 'pad': [12, 10, 10, 10], 'gap': 6, 'children': [
        label('{st.book.title}', scale=1.25, color=INK),
        label('{st.book.subtitle}', color=MUTE),
        spacer(h=1, w='fill', bg=LINE),
        {'type': 'scroll', 'id': 'steps', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': step_buttons()}]},
        spacer(h=1, w='fill', bg=LINE),
        {'type': 'row', 'w': 'fill', 'children': [
            label('Pages read', **{'if': '!st.accepted', 'color': MUTE, 'w': 'fill'}),
            label('All pages read', **{'if': 'st.accepted', 'color': MUTE, 'w': 'fill'}),
            label('{vm.readCount} / {vm.total}', color=INK),
        ]},
        {'type': 'bar', 'w': 'fill', 'h': 8, 'value': 'vm.readCount', 'max': 'vm.total', 'fill': RAINBOW, 'fill_bg': '#223048', 'radius': 4},
    ]}

    status = {'type': 'row', 'w': 'fill', 'h': 26, 'bg': PANEL, 'pad': [0, 18, 0, 18], 'cross': 'center', 'children': [
        label('Connecting to the server...', **{'if': '!st', 'color': MUTE}),
        label('You have already completed the rules. This page is for reference.', **{'if': 'st && vm.pages[$cur].done && st.accepted', 'color': OK}),
        label('Page read. Go to the next page.', **{'if': 'st && vm.pages[$cur].done && !st.accepted', 'color': OK}),
        label('Scroll down to the end of the page to finish reading it.', **{'if': 'st && !vm.pages[$cur].done && !$_atend', 'color': MUTE}),
        label('Keep reading... ({$_left}s)', **{'if': 'st && !vm.pages[$cur].done && $_atend && $_left > 0', 'color': MUTE}),
        label('Checking with the server...', **{'if': 'st && !vm.pages[$cur].done && $_atend && $_left <= 0', 'color': MUTE}),
    ]}

    footer = {'type': 'row', 'w': 'fill', 'bg': PANEL, 'pad': [8, 14, 8, 14], 'gap': 8, 'cross': 'center', 'children': [
        btn('< Previous', w=70, enabled='$cur > 0', set={'cur': '$cur - 1'}),
        btn('Next page >', w=76, enabled='$cur < vm.last', set={'cur': '$cur + 1'}, bg='#1D4F88', border='#3A7BC0', hover='#2461A8'),
        spacer(w='fill'),
        label('Accepted {st.acceptedAt|day}', **{'if': 'st.accepted', 'color': DIM}),
        label('Read all {vm.total} pages first ({vm.readCount}/{vm.total})', **{'if': '!st.accepted && !vm.allRead', 'color': DIM}),
        label('Press the button to finish.', **{'if': '!st.accepted && vm.allRead', 'color': DIM}),
        btn('I have read the rules', **{'if': '!st.accepted', 'id': 'confirm', 'w': 130, 'h': 24, 'enabled': 'vm.allRead', 'bg': OKD, 'border': OK, 'hover': '#25B563', 'disabled': '#3A3F4C',
                                         'msg': {'kind': 'accept'}}),
        btn('Rules quest completed', **{'if': 'st.accepted', 'w': 130, 'h': 24, 'bg': '#3A3F4C', 'border': '#4A4F5C', 'color': '#8A909E'}),
        btn('Close', w=60, close=True),
    ]}

    main = {'type': 'column', 'w': 'fill', 'h': 'fill', 'children': [
        {'type': 'row', 'w': 'fill', 'pad': [12, 18, 10, 18], 'gap': 10, 'cross': 'center', 'children': [
            label('{st.book.pages[$cur].title}', scale=1.45, color=INK, w='fill'),
            label('Page {vm.pages[$cur].no} of {vm.total}', color=MUTE, bg=PANEL, border=LINE, radius=8, pad=[3, 10, 3, 10]),
        ]},
        spacer(h=1, w='fill', bg=LINE),
        {'type': 'scroll', 'id': 'content', 'w': 'fill', 'h': 'fill', 'pad': [10, 16, 10, 16], 'value': '$cur', 'hint': '▼ scroll down to keep reading ▼',
         'min_ms': 'vm.minMs', 'until': 'vm.pages[$cur].done', 'args': {'page': 'int:0:63'}, 'argv': {'page': '$cur'},
         'msg': {'kind': 'read', 'page': '{page}'},
         'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
             {'type': 'column', 'repeat': {'for': 'st.book.pages[$cur].blocks', 'as': 'b'}, 'w': 'fill', 'children': block_variants()},
             howto(),
         ]}]},
        status,
        spacer(h=1, w='fill', bg=LINE),
        footer,
    ]}

    overlay = {'type': 'column', 'if': 'done', 'w': 'fill', 'h': 'fill', 'bg': '#06090FE0', 'align': 'center', 'cross': 'center', 'children': [
        {'type': 'column', 'w': 400, 'bg': PANEL, 'border': OK, 'radius': 8, 'pad': [22, 24, 22, 24], 'gap': 10, 'cross': 'center', 'children': [
            label('Rules quest complete!', color=OK, scale=1.7),
            label('Thank you for reading the rules. Every other quest is now open to you.', w='fill', wrap=True, text_align='center', color='#CFDCF2'),
            btn('Start playing', w=150, h=26, bg=OKD, border=OK, hover='#25B563', close=True),
        ]},
    ]}

    book = {'type': 'row', 'w': 'fill', 'h': 'fill', 'bg': BG1, 'border': LINE, 'radius': 8, 'children': [
        side,
        spacer(w=1, h='fill', bg=LINE),
        main,
    ]}
    return {
        'v': 2, 'id': 'rules', 'channel': 'snobblerules', 'title': 'Snobblemon Regions Rules', 'width': 820, 'height': 450,
        'state': {'cur': {'from': 'vm.startPage'}},
        'root': {'type': 'stack', 'w': 'fill', 'h': 'fill', 'children': [
            book,
            # tells the server which page is on screen whenever it changes (the web page did this in show())
            spacer(id='view', watch='$cur', args={'page': 'int:0:63'}, argv={'page': '$cur'}, msg={'kind': 'view', 'page': '{page}'}),
            overlay,
        ]},
    }


# ------------------------------------------------------------------ rules roll (staff)

def verify_page():
    def tab(key, text):
        on = {'type': 'button', 'if': '$tab == \'%s\'' % key, 'id': 'tab_%s_on' % key, 'h': 24, 'pad': [3, 12, 3, 12], 'gap': 7, 'cross': 'center', 'bg': BG1, 'border': LINE, 'radius': 5,
              'set': {'tab': "'%s'" % key}, 'args': {'tab': 'enum:accepted|unread|all', 'q': 'str:32'}, 'argv': {'tab': "'%s'" % key, 'q': '$q'},
              'msg': {'kind': 'admin_refresh', 'tab': '{tab}', 'q': '{q}', 'force': 'true'},
              'children': [label(text, color=INK), label('{vs.counts.%s}' % key, color=INK, bg='#243250', radius=7, pad=[1, 6, 1, 6])]}
        off = dict(on)
        off['if'] = '$tab != \'%s\'' % key
        off['id'] = 'tab_%s' % key
        off['bg'] = ''
        off.pop('border')
        off['hover'] = PANEL2
        off['children'] = [label(text, color=MUTE), label('{vs.counts.%s}' % key, color=MUTE, bg='#243250', radius=7, pad=[1, 6, 1, 6])]
        return [on, off]

    row = {'type': 'row', 'repeat': {'for': 'vs.rows', 'as': 'r', 'limit': 200}, 'w': 'fill', 'gap': 12, 'cross': 'center', 'bg': PANEL, 'border': LINE, 'radius': 7, 'pad': [8, 12, 8, 12], 'children': [
        {'type': 'column', 'w': 32, 'h': 32, 'align': 'center', 'cross': 'center', 'bg': '{r.name|avatar}', 'radius': 7, 'children': [label('{r.name|first}', color='#0B1019', scale=1.2)]},
        {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
            {'type': 'row', 'gap': 7, 'cross': 'center', 'children': [
                label('{r.name}', scale=1.1, color=INK),
                {'type': 'column', 'if': 'r.online', 'w': 8, 'h': 8, 'bg': OK, 'radius': 4},
                {'type': 'column', 'if': '!r.online', 'w': 8, 'h': 8, 'bg': '#4A5568', 'radius': 4},
            ]},
            label('Read and accepted the rules {r.acceptedAt|ago} - {r.acceptedAt|date}', **{'if': "r.status == 'accepted'", 'color': MUTE}),
            label('Has not finished the rules - first seen {r.firstSeen|ago}', **{'if': "r.status != 'accepted'", 'color': MUTE}),
        ]},
        label('ACCEPTED', **{'if': "r.status == 'accepted'", 'color': '#B9F3CF', 'bg': '#14452A', 'radius': 7, 'pad': [3, 9, 3, 9]}),
        label('NOT FINISHED', **{'if': "r.status != 'accepted'", 'color': '#AEBAD2', 'bg': '#2B344A', 'radius': 7, 'pad': [3, 9, 3, 9]}),
    ]}

    head = {'type': 'row', 'w': 'fill', 'bg': PANEL, 'pad': [12, 18, 10, 18], 'gap': 12, 'cross': 'center', 'children': [
        {'type': 'column', 'w': 'fill', 'gap': 2, 'children': [
            label('Rules Roll', scale=1.6, color=INK),
            label('Everyone who has read and accepted the rules. Accepting verifies a player automatically.', color=MUTE),
        ]},
        {'type': 'input', 'id': 'search', 'bind': 'q', 'hint': 'Search a player...', 'w': 190, 'h': 22, 'max_len': 32, 'bg': '#0F1626', 'border': LINE, 'radius': 5, 'color': INK, 'debounce_ms': 250,
         'args': {'tab': 'enum:accepted|unread|all', 'q': 'str:32'}, 'argv': {'tab': '$tab', 'q': '$q'},
         'msg': {'kind': 'admin_refresh', 'tab': '{tab}', 'q': '{q}', 'force': 'true'}},
        btn('Close', w=60, close=True),
    ]}
    tabs = {'type': 'row', 'w': 'fill', 'bg': PANEL, 'pad': [8, 18, 8, 18], 'gap': 6, 'children': tab('accepted', 'Accepted') + tab('unread', 'Not finished') + tab('all', 'Everyone')}

    listing = {'type': 'scroll', 'id': 'list', 'w': 'fill', 'h': 'fill', 'pad': [10, 18, 10, 18], 'children': [{'type': 'column', 'w': 'fill', 'gap': 6, 'children': [
        row,
        label('No permission', **{'if': 'denied', 'color': BAD, 'scale': 1.6, 'w': 'fill', 'text_align': 'center', 'pad': [40, 0, 4, 0]}),
        label('Only staff with the snobblerules.verify permission can use this page.', **{'if': 'denied', 'color': MUTE, 'w': 'fill', 'text_align': 'center'}),
        label('Loading...', **{'if': '!vs && !denied', 'color': DIM, 'w': 'fill', 'text_align': 'center', 'pad': [40, 0, 0, 0]}),
        label('No players match this search.', **{'if': 'vs && vs.matches == 0 && $q', 'color': DIM, 'w': 'fill', 'text_align': 'center', 'pad': [40, 0, 0, 0]}),
        label('Nobody has accepted the rules yet.', **{'if': "vs && vs.matches == 0 && !$q && $tab == 'accepted'", 'color': DIM, 'w': 'fill', 'text_align': 'center', 'pad': [40, 0, 0, 0]}),
        label('Nothing here yet.', **{'if': "vs && vs.matches == 0 && !$q && $tab != 'accepted'", 'color': DIM, 'w': 'fill', 'text_align': 'center', 'pad': [40, 0, 0, 0]}),
    ]}]}

    foot = {'type': 'row', 'w': 'fill', 'bg': PANEL, 'pad': [8, 18, 8, 18], 'gap': 14, 'cross': 'center', 'children': [
        label('Connecting...', **{'if': '!vs && !denied', 'color': MUTE}),
        label('Access denied', **{'if': 'denied', 'color': MUTE}),
        label('Showing {vs.shown} of {vs.matches}', **{'if': 'vs && !vs.truncated', 'color': MUTE}),
        label('Showing {vs.shown} of {vs.matches} - type in the search box to find the rest', **{'if': 'vs && vs.truncated', 'color': MUTE}),
        spacer(w='fill'),
        label('LIVE', **{'if': 'live', 'color': OK}),
        label('NO ANSWER', **{'if': '!live', 'color': BAD}),
    ]}

    return {
        'v': 2, 'id': 'verify', 'channel': 'snobblerules', 'hello': 'admin_hello', 'title': 'Snobblemon Rules Roll', 'width': 820, 'height': 450,
        'state': {'tab': 'accepted', 'q': ''},
        'root': {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': BG1, 'border': LINE, 'radius': 8, 'children': [
            head, spacer(h=1, w='fill', bg=LINE), tabs, spacer(h=1, w='fill', bg=LINE), listing, spacer(h=1, w='fill', bg=LINE), foot,
            # the page asked for news every 4 seconds; the server answers only when something changed since "rev"
            spacer(id='poll', every_ms=4000, args={'tab': 'enum:accepted|unread|all', 'q': 'str:32', 'rev': 'int:-1:9007199254740991'}, argv={'tab': '$tab', 'q': '$q', 'rev': 'vs.rev'},
                   msg={'kind': 'admin_refresh', 'tab': '{tab}', 'q': '{q}', 'rev': '{rev}', 'force': 'false'}),
        ]},
    }


for name, page in (('rules', rules_page()), ('verify', verify_page())):
    with open(os.path.join(OUT, name + '.json'), 'w', encoding='utf8') as f:
        json.dump(fix(page), f, indent=1)
    print('wrote', name)
