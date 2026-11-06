"""Native /goal page (goal.json), a faithful copy of the original snobblemon/goal.html (Snobblemon Companion).

The original scales its fixed 1000x640 panel to the window: z = min(w * .94 / 1000, h * .9 / 640) - on a wide window that is the
height rule, so here "zoomfit": [711.1, 1, ...] and the panel is centred in window / z page pixels. Backdrop and panel gradient are
the page's own CSS rendered to textures (tex:snobblemon/goal_bg.png, goal_panel.png). Trebuchet MS -> "treb" (PT Sans, OFL, sized
to Trebuchet's metrics). The pastel rainbow text and bar use the page's colours (static; the original slides them slowly).
The previous native version is gen_ui_goal_v1.py.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'snobblemon-server', 'src', 'main', 'resources', 'data', 'snobblemon_server', 'ui')
INK, DIM, CARD, CARD2, LINE, GOLD, PINK, MINT, SKY = '#eaf3ff', '#9db4d4', '#13263f', '#1a3357', '#2c5080', '#ffd36b', '#ff9ccf', '#8cf5c4', '#8fd0ff'
RAIN = 'grad:#ff9ab8,#ffc58a,#fff39a,#a6f5b0,#9ad7ff,#c8aaff,#ff9ab8'
BAR = 'grad:#ff8fb4,#ffc58a,#fff08a,#9af0b0,#8fd0ff,#c3a2ff,#ff8fb4'
TABON = 'grad:#ffb3d1,#ffd9a0,#c4f5b4,#a8dcff,#d2b8ff'
F, FB = 'treb', 'treb_b'


def T(text, size=16, color=INK, font=F, **kw):
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


def card(children, **kw):
    """.box: card colour, 2px line border, radius 16, padding 14px 16px"""
    d = box(children, bg=CARD, border=LINE, bw=2, radius=16, pad=[16, 18, 16, 18])
    d.update(kw)
    return d


def tabs():
    def one(key, text, on, extra_if=''):
        cond = ("$tab == '%s'" if on else "$tab != '%s'") % key
        if extra_if:
            cond = extra_if + ' && ' + cond
        d = {'type': 'button', 'if': cond, 'id': 'tab_%s%s' % (key, '_on' if on else ''), 'text': text, 'font': FB, 'size': 15, 'shadow': False,
             'color': '#0b1a30' if on else DIM, 'hover_color': '#0b1a30' if on else '#ffffff', 'h': 40, 'pad': [0, 16, 0, 16], 'shrink': True, 'text_align': 'center',
             'bg': TABON if on else CARD, 'hover': TABON if on else CARD, 'border': '#ffffff' if on else LINE, 'hover_border': '#ffffff' if on else SKY, 'bw': 2, 'radius': 12,
             'set': {'tab': "'%s'" % key}}
        return d
    out = []
    for key, text in (('goal', 'Monthly Goal'), ('points', 'Points'), ('milestones', 'Milestones'), ('me', 'My Stats')):
        out += [one(key, text, True), one(key, text, False)]
    out += [one('players', 'Players \U0001F50E', True, 'st.admin'), one('players', 'Players \U0001F50E', False, 'st.admin')]
    return row(out, w='fill', gap=8, pad=[6, 24, 12, 24], bsides='b', border=LINE, bw=2)


def goal_tab():
    right_amounts = box([row([T('{st.symbol}{st.raised} ', 26, INK, FB, lh=31), T('of {st.symbol}{st.target}', 14, DIM, F, lh=31)], cross='end'),
                         T('✔ Goal reached! Thank you!', 14, MINT, F, lh=18, **{'if': 'st.reached'}),
                         T('Still needed: {st.symbol}{st.left}', 14, DIM, F, lh=18, **{'if': '!st.reached'})], cross='end', **{'if': 'st.showAmounts'})
    right_plain = box([T('✔ Goal reached! Thank you!', 26, MINT, FB, lh=31, **{'if': 'st.reached'}),
                       T('{vm.togo}% to go', 26, INK, FB, lh=31, **{'if': '!st.reached'}),
                       T('Thank you for supporting the server ❤', 14, DIM, F, lh=18)], cross='end', **{'if': '!st.showAmounts'})
    bar = box([box([], x=0, y=0, w=0, w_expr='vm.barW', h=40, bg=BAR, radius=20)], w='fill', h=46, bg='#0a162a', border=LINE, bw=3, radius=23, pad=[3, 3, 3, 3], clip=True)
    return box([row([box([T('Progress this month', 14, DIM, F, lh=18), T('{st.percent|floor}%', 64, RAIN, FB, lh=64, shrink=True)], w='fill'),
                     right_amounts, right_plain], w='fill', cross='end'),
                box([], h=14), bar], w='fill', **{'if': "$tab == 'goal'"})


def points_tab():
    rc = card([T('{r.name}', 24, '{r.color|color}', FB, lh=29, w='fill', text_align='center', pad=[0, 0, 8, 0]),
               T('{r.points}', 34, PINK, FB, lh=40, w='fill', text_align='center'), T('{st.pointsName}', 14, DIM, F, lh=18, w='fill', text_align='center'),
               T('{r.ws}', 26, INK, FB, lh=31, w='fill', text_align='center', pad=[10, 0, 0, 0]), T('Ws said', 14, DIM, F, lh=18, w='fill', text_align='center'),
               T('{r.wins}', 26, INK, FB, lh=31, w='fill', text_align='center', pad=[10, 0, 0, 0]), T('wave wins', 14, DIM, F, lh=18, w='fill', text_align='center'),
               T('{r.online} online', 14, DIM, F, lh=18, w='fill', text_align='center', pad=[10, 0, 0, 0])],
              w='fill', pad=[20, 14, 20, 14], repeat={'for': 'st.regions', 'as': 'r', 'limit': 6})
    return box([T('Regional {st.pointsName} · every W and welcome earns points for your region.', 14, DIM, F, lh=18, w='fill', pad=[0, 0, 12, 0]),
                row([rc], w='fill', gap=18)], w='fill', **{'if': "$tab == 'points'"})


def ms_grid(lst, title):
    cell = box([T('{m.title}', 16, INK, FB, lh=19, w='fill', text_align='center'), T('{m.sub}', 13, DIM, F, lh=16, w='fill', text_align='center')],
               w='fill', bg='{m.bg}', border='{m.border}', bw=2, radius=14, pad=[14, 8, 14, 8], repeat={'for': lst, 'as': 'm', 'limit': 40})
    return [T(title, 14, DIM, F, lh=18), box([], h=8), {'type': 'grid', 'w': 'fill', 'min_col': 220, 'gap': 10, 'children': [cell]}, box([], h=16)]


def milestones_tab():
    return box(ms_grid('vm.msList', 'Server milestones') + ms_grid('vm.dmList', 'Your supporter ranks'), w='fill', **{'if': "$tab == 'milestones'"})


def me_tab():
    def stat(title, value, color=INK):
        return card([T(title, 14, DIM, F, lh=18), T(value, 26, color, FB, lh=31)], w='fill')
    return box([
        card([row([box([T('{st.me.name|first}', 42, '#0b1a30', FB, lh=84, w=84, text_align='center')], w=84, h=84, radius=42, bg='{vm.meAv}'),
                   box([T('{st.me.name}', 42, RAIN, FB, lh=42, shrink=True), T('{st.me.region}', 14, DIM, F, lh=18, **{'if': 'st.me.region'}),
                        T('No region yet', 14, DIM, F, lh=18, **{'if': '!st.me.region'})])], gap=22, cross='center')], w='fill', pad=[24, 24, 24, 24]),
        box([], h=16),
        row([stat('Donations', '{vm.donations}'), stat('{st.pointsName}', '{st.me.points}', PINK), stat('Ws said', '{st.me.ws}'), stat('Players welcomed', '{st.me.greets}')],
            w='fill', gap=18)], w='fill', **{'if': "$tab == 'me'"})


def players_tab():
    msgargs = {'args': {'q': 'str:32'}, 'argv': {'q': '$q'}, 'msg': {'kind': 'lookup', 'q': '{q}'}}
    inp = {'type': 'input', 'id': 'lkq', 'bind': 'q', 'hint': 'Search a player name (online or offline)...', 'w': 'fill', 'h': 42, 'max_len': 32, 'font': FB, 'size': 16,
           'bg': '#0a162a', 'border': LINE, 'bw': 2, 'radius': 12, 'pad': [0, 16, 0, 16], 'color': '#ffffff', 'hint_color': '#757575', 'debounce_ms': 400}
    inp.update(msgargs)
    srch = {'type': 'button', 'id': 'lkgo', 'text': 'Search', 'font': FB, 'size': 16, 'color': '#ffffff', 'shadow': False, 'h': 42, 'pad': [0, 20, 0, 20], 'shrink': True,
            'bg': 'gradv:#4f8fe0,#2f63b0', 'hover': 'gradv:#5f9ff0,#3f73c0', 'border': '#9cc4ff', 'bw': 2, 'radius': 12}
    srch.update(msgargs)

    def pill(v, w):
        return T('**%s** %s' % (v, w), 14, INK, F, lh=19, rich=True, rich_plain=True, bcolor=GOLD, pad=[3, 14, 3, 14], bg=CARD2, border=LINE, bw=2, radius=14, shrink=True)
    line = box([
        row([box([T('{r.name|first}', 13, '#0b1a30', FB, lh=26, w=26, text_align='center')], w=26, h=26, radius=13, bg='{r.name|avatar}'),
             row([T('{r.name} ', 16, INK, FB, lh=20), T('· ', 13, DIM, F, lh=20), T('online', 13, MINT, F, lh=20, **{'if': 'r.online'}),
                  T('offline', 13, '#8a97ab', F, lh=20, **{'if': '!r.online'}), T(' · {r.region}', 13, DIM, F, lh=20, **{'if': 'r.region'})], w='fill', cross='end'),
             pill('{r.ws}', 'Ws'), pill('{r.greets}', 'welcomes'), pill('{r.quizWins}', 'quiz wins'), pill('{r.points}', 'pts')],
            w='fill', gap=10, cross='center', bg=CARD, border=LINE, bw=2, radius=14, pad=[10, 14, 10, 14]),
        T('Ws by kind: quiz {r.wQuiz} · shiny {r.wShiny} · legendary {r.wLegendary} · donation {r.wDonation}', 13, DIM, F, lh=16, pad=[2, 0, 4, 56])],
        w='fill', repeat={'for': 'lk.results', 'as': 'r', 'limit': 12})
    return box([
        {'type': 'spacer', 'id': 'lkopen', 'watch': '$tab', **msgargs},
        row([inp, srch], w='fill', gap=18, pad=[0, 0, 10, 0]),
        T('Loading players...', 18, DIM, F, w='fill', text_align='center', lh=22, pad=[50, 0, 50, 0], **{'if': '!lk'}),
        T('No player found for "{lk.q}".', 18, DIM, F, w='fill', text_align='center', lh=22, pad=[50, 0, 50, 0], **{'if': 'lk && !lk.results'}),
        T('Showing {vm.lkShown} of {lk.total} · type more of the name to narrow it down', 13, DIM, F, lh=16, pad=[0, 0, 6, 0], **{'if': 'lk && lk.total > vm.lkShown'}),
        T('{lk.total} found', 13, DIM, F, lh=16, pad=[0, 0, 6, 0], **{'if': 'lk && lk.results && lk.total <= vm.lkShown'}),
        {'type': 'scroll', 'id': 'plist', 'w': 'fill', 'h': 'fill', 'children': [box([line], w='fill', gap=4)]}], w='fill', h='fill', **{'if': "$tab == 'players' && st.admin"})


def page():
    header = row([box([T('{st.title}', 30, RAIN, FB, ls=0.5, lh=36, shrink=True),
                       T('{st.server} · goal reached, thank you!', 14, DIM, F, lh=18, pad=[2, 0, 0, 0], **{'if': 'st.reached'}),
                       T('{st.server} · every donation moves the bar', 14, DIM, F, lh=18, pad=[2, 0, 0, 0], **{'if': 'st && !st.reached'}),
                       T('Loading...', 14, DIM, F, lh=18, pad=[2, 0, 0, 0], **{'if': '!st'})], w='fill'),
                  {'type': 'button', 'id': 'close', 'text': '× Close', 'font': FB, 'size': 16, 'color': '#ffffff', 'shadow': False, 'h': 42, 'pad': [0, 20, 0, 20],
                   'shrink': True, 'bg': 'gradv:#e0526b,#b9334e', 'hover': 'gradv:#f0657d,#cf405b', 'border': '#ff93a5', 'bw': 2, 'radius': 12, 'close': True,
                   'args': {}, 'msg': {'kind': 'bye'}}],
                 w='fill', cross='center', pad=[16, 24, 10, 24])
    body = box([goal_tab(), points_tab(), milestones_tab(), me_tab(), players_tab()], w='fill', h='fill', pad=[20, 28, 20, 28], clip=True)
    foot = row([T('Say ', 13, DIM, F, lh=16), T('W', 13, RAIN, FB, lh=16, shrink=True), T(' in chat when a donation lands for +{st.pointsPerW} {st.pointsName}', 13, DIM, F, lh=16),
                T(' · Store: {st.storeUrl}', 13, DIM, F, lh=16, **{'if': 'st.storeUrl'})], w='fill', align='center', pad=[8, 24, 12, 24])
    panel = box([box([], x=-30, y=-20, w=1060, h=720, bg='tex:snobblemon/goal_panel.png'),
                 header, tabs(), body, foot, {'type': 'spacer', 'id': 'ping', 'every_ms': 4000, 'msg': {'kind': 'ping'}}],
                w=1000, h=640)
    return {
        'v': 2, 'id': 'goal', 'channel': 'snobblemoncompanion', 'title': 'Community Goal', 'width': 1264, 'height': 711,
        'zoomfit': [711.11, 1, 0.1, 99], 'nodim': True, 'state': {'tab': 'goal', 'q': ''},
        'root': box([panel], w='fill', h='fill', align='center', cross='center', bg='tex:snobblemon/goal_bg.png')}


if __name__ == '__main__':
    with open(os.path.join(OUT, 'goal.json'), 'w', encoding='utf8') as f:
        json.dump(page(), f, ensure_ascii=False, separators=(',', ':'))
    print('wrote goal', os.path.getsize(os.path.join(OUT, 'goal.json')))
