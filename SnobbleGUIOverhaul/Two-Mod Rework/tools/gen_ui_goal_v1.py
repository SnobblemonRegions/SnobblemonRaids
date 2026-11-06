"""Generates the layout-v2 page for the Snobblemon Companion /goal page (goal.json)."""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
INK, DIM, CARD, CARD2, LINE, GOLD, PINK, MINT, SKY = '#EAF3FF', '#9DB4D4', '#13263F', '#1A3357', '#2C5080', '#FFD36B', '#FF9CCF', '#8CF5C4', '#8FD0FF'
RAIN = 'grad:#FF9AB8,#FFC58A,#A6F5B0,#C8AAFF'


def label(text, **kw):
    d = {'type': 'label', 'text': text}
    d.update(kw)
    return d


def sp(**kw):
    d = {'type': 'spacer'}
    d.update(kw)
    return d


def box(children, **kw):
    d = {'type': 'column', 'bg': CARD, 'border': LINE, 'radius': 8, 'pad': [8, 10, 8, 10], 'gap': 3, 'children': children}
    d.update(kw)
    return d


def tab(key, text):
    on = {'type': 'button', 'if': "$tab == '%s'" % key, 'h': 24, 'w': 'content', 'pad': [4, 12, 4, 12], 'cross': 'center', 'align': 'center', 'bg': 'grad:#FFB3D1,#FFD9A0,#C4F5B4,#A8DCFF',
          'border': '#FFFFFF', 'radius': 8, 'set': {'tab': "'%s'" % key}, 'children': [label(text, color='#0B1A30', shadow=False)]}
    on['if'] = "$tab == '%s'" % key
    off = {'type': 'button', 'if': "$tab != '%s'" % key, 'h': 24, 'w': 'content', 'pad': [4, 12, 4, 12], 'cross': 'center', 'align': 'center', 'bg': CARD, 'border': LINE, 'radius': 8,
           'hover': '#1E3B63', 'set': {'tab': "'%s'" % key}, 'children': [label(text, color=DIM)]}
    return [on, off]


def goal_tab():
    return {'type': 'column', 'if': "$tab == 'goal'", 'w': 'fill', 'gap': 10, 'children': [
        {'type': 'row', 'w': 'fill', 'cross': 'end', 'children': [
            {'type': 'column', 'w': 'fill', 'children': [
                label('Progress this month', color=DIM),
                label('{st.percent|floor}%', scale=3.2, color='#C9A8FF'),
            ]},
            {'type': 'column', 'w': 'content', 'cross': 'end', 'if': 'st.showAmounts', 'children': [
                label('{st.symbol}{st.raised} of {st.symbol}{st.target}', scale=1.4, color=INK),
                label('Goal reached! Thank you!', **{'if': 'st.reached', 'color': MINT}),
                label('Still needed: {st.symbol}{st.left}', **{'if': '!st.reached', 'color': DIM}),
            ]},
            {'type': 'column', 'w': 'content', 'cross': 'end', 'if': '!st.showAmounts', 'children': [
                label('Goal reached! Thank you!', **{'if': 'st.reached', 'scale': 1.4, 'color': MINT}),
                label('{vm.togo}% to go', **{'if': '!st.reached', 'scale': 1.4, 'color': INK}),
                label('Thank you for supporting the server', color=DIM),
            ]},
        ]},
        {'type': 'bar', 'w': 'fill', 'h': 24, 'value': 'st.percent', 'max': '100', 'fill': RAIN, 'fill_bg': '#0A162A', 'border': LINE, 'radius': 12},
    ]}


def points_tab():
    card = box([
        label('{r.name}', scale=1.5, color='{r.color|color}', text_align='center', w='fill'),
        label('{r.points}', scale=2.2, color=PINK, text_align='center', w='fill'),
        label('{st.pointsName}', color=DIM, text_align='center', w='fill'),
        label('{r.ws}', scale=1.5, color=INK, text_align='center', w='fill', pad=[8, 0, 0, 0]),
        label('Ws said', color=DIM, text_align='center', w='fill'),
        label('{r.wins}', scale=1.5, color=INK, text_align='center', w='fill', pad=[8, 0, 0, 0]),
        label('wave wins', color=DIM, text_align='center', w='fill'),
        label('{r.online} online', color=DIM, text_align='center', w='fill', pad=[8, 0, 0, 0]),
    ], repeat={'for': 'st.regions', 'as': 'r'}, w='fill', cross='center', pad=[14, 8, 14, 8])
    return {'type': 'column', 'if': "$tab == 'points'", 'w': 'fill', 'gap': 10, 'children': [
        label('Regional {st.pointsName} - every W and welcome earns points for your region.', color=DIM, w='fill', wrap=True),
        {'type': 'row', 'w': 'fill', 'gap': 14, 'children': [card]},
    ]}


def ms_grid(rows, title):
    cell = {'type': 'column', 'repeat': {'for': 'row.items', 'as': 'm'}, 'w': 'fill', 'cross': 'center', 'bg': '{m.bg}', 'border': '{m.border}', 'radius': 8, 'pad': [9, 4, 9, 4], 'gap': 2, 'children': [
        label('{m.title}', color=INK, text_align='center', w='fill'),
        label('{m.sub}', color=DIM, text_align='center', w='fill'),
    ]}
    return [label(title, color=DIM),
            {'type': 'column', 'w': 'fill', 'gap': 8, 'children': [{'type': 'row', 'repeat': {'for': rows, 'as': 'row'}, 'w': 'fill', 'gap': 8, 'children': [cell]}]}]


def milestones_tab():
    return {'type': 'column', 'if': "$tab == 'milestones'", 'w': 'fill', 'gap': 8, 'children':
            ms_grid('vm.msRows', 'Server milestones') + ms_grid('vm.dmRows', 'Your supporter ranks')}


def me_tab():
    def stat(title, value, color=INK):
        return box([label(title, color=DIM), label(value, scale=1.6, color=color)], w='fill', pad=[8, 12, 8, 12])
    return {'type': 'column', 'if': "$tab == 'me'", 'w': 'fill', 'gap': 12, 'children': [
        {'type': 'row', 'w': 'fill', 'gap': 16, 'cross': 'center', 'bg': CARD, 'border': LINE, 'radius': 8, 'pad': [14, 16, 14, 16], 'children': [
            {'type': 'column', 'w': 56, 'h': 56, 'align': 'center', 'cross': 'center', 'bg': '{st.me.name|avatar}', 'radius': 28, 'children': [label('{st.me.name|first}', color='#0B1A30', scale=2.6, shadow=False)]},
            {'type': 'column', 'w': 'fill', 'children': [
                label('{st.me.name}', scale=2.2, color='#C9A8FF'),
                label('{st.me.region}', **{'if': 'st.me.region', 'color': DIM}),
                label('No region yet', **{'if': '!st.me.region', 'color': DIM}),
            ]},
        ]},
        {'type': 'row', 'w': 'fill', 'gap': 10, 'children': [
            stat('Donations', '{vm.donations}'),
            stat('{st.pointsName}', '{st.me.points}', PINK),
            stat('Ws said', '{st.me.ws}'),
            stat('Players welcomed', '{st.me.greets}'),
        ]},
    ]}


def players_tab():
    msgargs = {'args': {'q': 'str:32'}, 'argv': {'q': '$q'}, 'msg': {'kind': 'lookup', 'q': '{q}'}}
    inp = {'type': 'input', 'id': 'lkq', 'bind': 'q', 'hint': 'Search a player name (online or offline)...', 'w': 'fill', 'h': 24, 'max_len': 32, 'bg': '#0A162A', 'border': LINE, 'radius': 8, 'color': '#FFFFFF', 'debounce_ms': 400}
    inp.update(msgargs)
    srch = {'type': 'button', 'id': 'lkgo', 'text': 'Search', 'w': 70, 'h': 24, 'bg': '#2F63B0', 'border': '#9CC4FF', 'radius': 8, 'hover': '#3F78CC', 'color': '#FFFFFF'}
    srch.update(msgargs)
    row = {'type': 'column', 'repeat': {'for': 'lk.results', 'as': 'r', 'limit': 12}, 'w': 'fill', 'gap': 1, 'children': [
        {'type': 'row', 'w': 'fill', 'gap': 8, 'cross': 'center', 'bg': CARD, 'border': LINE, 'radius': 8, 'pad': [5, 8, 5, 8], 'children': [
            {'type': 'column', 'w': 22, 'h': 22, 'align': 'center', 'cross': 'center', 'bg': '{r.name|avatar}', 'radius': 11, 'children': [label('{r.name|first}', color='#0B1A30', shadow=False)]},
            label('{r.name}', color=INK),
            label('online', **{'if': 'r.online', 'color': MINT}),
            label('offline', **{'if': '!r.online', 'color': '#8A97AB'}),
            label('{r.region}', **{'if': 'r.region', 'color': DIM}),
            sp(w='fill'),
            label('{r.ws} Ws', color=GOLD), label('{r.greets} welcomes', color=GOLD), label('{r.quizWins} quiz wins', color=GOLD), label('{r.points} pts', color=GOLD),
        ]},
        label('Ws by kind: quiz {r.wQuiz} - shiny {r.wShiny} - legendary {r.wLegendary} - donation {r.wDonation}', color=DIM, pad=[0, 0, 2, 30]),
    ]}
    return {'type': 'column', 'if': "$tab == 'players' && st.admin", 'w': 'fill', 'h': 'fill', 'gap': 6, 'children': [
        sp(id='lkopen', watch='$tab', **msgargs),
        {'type': 'row', 'w': 'fill', 'gap': 8, 'children': [inp, srch]},
        label('Loading players...', **{'if': '!lk', 'color': DIM, 'w': 'fill', 'text_align': 'center', 'pad': [30, 0, 0, 0]}),
        label('No player found for "{lk.q}".', **{'if': 'lk && !lk.results', 'color': DIM, 'w': 'fill', 'text_align': 'center', 'pad': [30, 0, 0, 0]}),
        label('Showing {vm.lkShown} of {lk.total} - type more of the name to narrow it down', **{'if': 'lk && lk.total > vm.lkShown', 'color': DIM}),
        label('{lk.total} found', **{'if': 'lk && lk.results && lk.total <= vm.lkShown', 'color': DIM}),
        {'type': 'scroll', 'id': 'plist', 'w': 'fill', 'h': 'fill', 'children': [{'type': 'column', 'w': 'fill', 'gap': 4, 'children': [row]}]},
    ]}


def page():
    tabs = {'type': 'row', 'w': 'fill', 'gap': 6, 'pad': [4, 20, 10, 20], 'children':
            tab('goal', 'Monthly Goal') + tab('points', 'Points') + tab('milestones', 'Milestones') + tab('me', 'My Stats')
            + [dict(t, **{'if': ("st.admin && " + t['if'])}) for t in tab('players', 'Players')]}
    header = {'type': 'row', 'w': 'fill', 'pad': [14, 20, 8, 20], 'cross': 'center', 'children': [
        {'type': 'column', 'w': 'fill', 'children': [
            label('{st.title}', scale=2.2, color='#C9A8FF'),
            label('{st.server} - goal reached, thank you!', **{'if': 'st.reached', 'color': DIM}),
            label('{st.server} - every donation moves the bar', **{'if': '!st.reached', 'color': DIM}),
        ]},
        {'type': 'button', 'text': 'X Close', 'w': 70, 'h': 24, 'bg': '#B9334E', 'border': '#FF93A5', 'radius': 8, 'hover': '#CF405B', 'color': '#FFFFFF', 'close': True},
    ]}
    body = {'type': 'column', 'w': 'fill', 'h': 'fill', 'pad': [14, 24, 8, 24], 'children': [goal_tab(), points_tab(), milestones_tab(), me_tab(), players_tab()]}
    foot = label('Say W in chat when a donation lands for +{st.pointsPerW} {st.pointsName}', color=DIM, w='fill', text_align='center', pad=[6, 0, 8, 0])
    return {
        'v': 2, 'id': 'goal', 'channel': 'snobblemoncompanion', 'title': 'Community Goal', 'width': 860, 'height': 520,
        'state': {'tab': 'goal', 'q': ''},
        'root': {'type': 'column', 'w': 'fill', 'h': 'fill', 'bg': '#12243D', 'border': LINE, 'radius': 12, 'children': [
            header, {'type': 'spacer', 'h': 1, 'w': 'fill', 'bg': LINE}, tabs, {'type': 'spacer', 'h': 1, 'w': 'fill', 'bg': LINE}, body, foot,
            sp(id='ping', every_ms=4000, msg={'kind': 'ping'}),
        ]},
    }


if __name__ == '__main__':
    with open(os.path.join(OUT, 'goal.json'), 'w', encoding='utf8') as f:
        json.dump(page(), f, indent=1)
    print('wrote goal')
